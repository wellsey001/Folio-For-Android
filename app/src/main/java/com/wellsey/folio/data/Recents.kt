package com.wellsey.folio.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(val uri: String, val name: String, val openedAt: Long, val page: Int, val pageCount: Int)

/** Recently opened PDFs, newest first, remembered with the page you were on. */
class Recents(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<RecentFile>> = _items.asStateFlow()

    private fun load(): List<RecentFile> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RecentFile(o.getString("uri"), o.getString("name"), o.getLong("openedAt"), o.optInt("page"), o.optInt("pageCount"))
        }
    }.getOrDefault(emptyList())

    private fun persist(list: List<RecentFile>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("openedAt", it.openedAt)
                .put("page", it.page).put("pageCount", it.pageCount))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun touch(uri: Uri, name: String, pageCount: Int) {
        val old = _items.value.firstOrNull { it.uri == uri.toString() }
        val entry = RecentFile(uri.toString(), name, System.currentTimeMillis(), old?.page ?: 0, pageCount)
        persist((listOf(entry) + _items.value.filterNot { it.uri == entry.uri }).take(50))
    }

    fun setPage(uri: Uri, page: Int) {
        persist(_items.value.map { if (it.uri == uri.toString()) it.copy(page = page) else it })
    }

    fun pageFor(uri: Uri): Int = _items.value.firstOrNull { it.uri == uri.toString() }?.page ?: 0

    fun remove(uri: String) = persist(_items.value.filterNot { it.uri == uri })
}
