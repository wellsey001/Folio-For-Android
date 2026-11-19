package com.wellsey.folio.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wellsey.folio.data.FormField
import com.wellsey.folio.data.PageGeom
import com.wellsey.folio.data.PageText
import com.wellsey.folio.data.PasswordProtectedException
import com.wellsey.folio.data.PdfSession
import com.wellsey.folio.data.Recents
import com.wellsey.folio.data.SearchHit
import com.wellsey.folio.data.Stroke
import com.wellsey.folio.data.TextBoxStyle
import com.wellsey.folio.data.applyImage
import com.wellsey.folio.data.applyStrokes
import com.wellsey.folio.data.applyTextBox
import com.wellsey.folio.data.deletePage
import com.wellsey.folio.data.extractPageTexts
import com.wellsey.folio.data.mergePdfs
import com.wellsey.folio.data.movePage
import com.wellsey.folio.data.readForm
import com.wellsey.folio.data.rotatePage
import com.wellsey.folio.data.search as searchText
import com.wellsey.folio.data.writeForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything the reader screen shows about the open document. */
data class DocState(
    val name: String,
    val uri: Uri,
    val pages: List<PageGeom>,
    val version: Int,
    val canUndo: Boolean,
    val dirty: Boolean,
    val hasForm: Boolean,
    val startPage: Int,
)

enum class Tool { Read, Pen, Highlighter, Text, Sign }

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    val recents = Recents(app)

    private var session: PdfSession? = null

    private val _doc = MutableStateFlow<DocState?>(null)
    val doc: StateFlow<DocState?> = _doc.asStateFlow()

    private val _opening = MutableStateFlow(false)
    val opening: StateFlow<Boolean> = _opening.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    // Drawing in progress (not yet written to the file)
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()

    // Search
    private var pageTexts: List<PageText>? = null
    private val _hits = MutableStateFlow<List<SearchHit>>(emptyList())
    val hits: StateFlow<List<SearchHit>> = _hits.asStateFlow()
    private val _searching = MutableStateFlow<String?>(null)
    val searching: StateFlow<String?> = _searching.asStateFlow()
    private var searchJob: Job? = null

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    // ------------------------------------------------------------ open / close

    fun open(uri: Uri, onOpened: () -> Unit = {}) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _opening.value = true
            // Keep access to files picked from the file picker, so Recent files still open later.
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.recoverCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = withContext(Dispatchers.IO) { displayName(uri) }
            close()
            val s = PdfSession(ctx, uri, name)
            try {
                s.open()
                session = s
                recents.touch(uri, name, s.pages.size)
                val hasForm = runCatching { s.read { readForm(it).isNotEmpty() } }.getOrDefault(false)
                _doc.value = DocState(name, uri, s.pages, s.version, false, false, hasForm, recents.pageFor(uri))
                onOpened()
            } catch (e: PasswordProtectedException) {
                s.close(); message("This PDF is password-protected, which Folio can't open yet")
            } catch (e: SecurityException) {
                s.close(); recents.remove(uri.toString()); message("Folio no longer has permission to open that file. Open it again from Files.")
            } catch (e: Exception) {
                s.close(); message("Couldn't open this file: it may not be a valid PDF")
            } finally {
                _opening.value = false
            }
        }
    }

    fun close() {
        session?.close()
        session = null
        _doc.value = null
        _strokes.value = emptyList()
        pageTexts = null
        _hits.value = emptyList()
        searchJob?.cancel()
    }

    override fun onCleared() {
        session?.close()
    }

    private fun displayName(uri: Uri): String {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = session?.render(index, widthPx)

    fun rememberPage(page: Int) {
        val d = _doc.value ?: return
        recents.setPage(d.uri, page)
    }

    // ------------------------------------------------------------ editing

    private fun publish(dirty: Boolean = true) {
        val s = session ?: return
        val d = _doc.value ?: return
        _doc.value = d.copy(pages = s.pages, version = s.version, canUndo = s.canUndo, dirty = dirty || d.dirty)
        pageTexts = null
        _hits.value = emptyList()
    }

    private fun runEdit(label: String, done: String, change: (com.tom_roush.pdfbox.pdmodel.PDDocument) -> Unit) {
        val s = session ?: return
        viewModelScope.launch {
            _busy.value = label
            val ok = s.edit(change)
            _busy.value = null
            if (ok) {
                publish(); message(done)
            } else message("Sorry, that change didn't work on this PDF")
        }
    }

    fun addStroke(stroke: Stroke) {
        _strokes.value = _strokes.value + stroke
    }

    fun undoStroke() {
        _strokes.value = _strokes.value.dropLast(1)
    }

    fun discardStrokes() {
        _strokes.value = emptyList()
    }

    fun commitStrokes() {
        val list = _strokes.value
        val pages = session?.pages ?: return
        if (list.isEmpty()) return
        _strokes.value = emptyList()
        runEdit("Saving drawing…", "Drawing added") { applyStrokes(it, pages, list) }
    }

    fun addTextBox(page: Int, at: Offset, text: String, style: TextBoxStyle) {
        val geom = session?.pages?.getOrNull(page) ?: return
        runEdit("Adding text…", "Text added") { applyTextBox(it, geom, page, at, text, style) }
    }

    fun addSignature(page: Int, box: Rect, bitmap: Bitmap) {
        val geom = session?.pages?.getOrNull(page) ?: return
        runEdit("Adding signature…", "Signature added") { applyImage(it, geom, page, box, bitmap) }
    }

    fun rotate(page: Int, degrees: Int) = runEdit("Rotating…", "Page ${page + 1} rotated") { rotatePage(it, page, degrees) }

    fun deletePageAt(page: Int) {
        if ((session?.pages?.size ?: 0) <= 1) {
            message("A PDF needs at least one page"); return
        }
        runEdit("Deleting page…", "Page ${page + 1} deleted") { deletePage(it, page) }
    }

    fun move(from: Int, to: Int) = runEdit("Moving page…", "Page moved") { movePage(it, from, to) }

    suspend fun formFields(): List<FormField> = session?.read { readForm(it) }.orEmpty()

    fun fillForm(values: Map<String, String>) =
        runEdit("Filling in form…", "Form updated") { writeForm(it, values) }

    fun undo() {
        val s = session ?: return
        viewModelScope.launch {
            if (s.undo()) {
                publish(dirty = true); message("Undone")
            }
        }
    }

    // ------------------------------------------------------------ saving & sharing

    /** Saves over the original file. Returns false if the original can't be written to. */
    fun save(onNeedsCopy: () -> Unit) {
        val s = session ?: return
        val d = _doc.value ?: return
        viewModelScope.launch {
            _busy.value = "Saving…"
            val ok = runCatching { s.saveTo(d.uri) }.isSuccess
            _busy.value = null
            if (ok) {
                _doc.value = _doc.value?.copy(dirty = false)
                message("Saved")
            } else {
                message("This file can't be changed in place. Choose where to save a copy.")
                onNeedsCopy()
            }
        }
    }

    fun saveCopy(target: Uri) {
        val s = session ?: return
        viewModelScope.launch {
            _busy.value = "Saving…"
            val ok = runCatching { s.saveTo(target) }.isSuccess
            _busy.value = null
            if (ok) {
                _doc.value = _doc.value?.copy(dirty = false)
                message("Copy saved")
            } else message("Couldn't save there")
        }
    }

    suspend fun shareFile(): File? = session?.shareCopy()

    // ------------------------------------------------------------ search

    fun search(query: String) {
        val s = session ?: return
        searchJob?.cancel()
        if (query.isBlank()) {
            _hits.value = emptyList(); return
        }
        searchJob = viewModelScope.launch {
            val texts = pageTexts ?: run {
                val total = s.pages.size
                _searching.value = "Reading text…"
                val t = s.read { doc -> extractPageTexts(doc) { n -> _searching.value = "Reading page $n of $total…" } }
                pageTexts = t
                t
            }
            _searching.value = null
            _hits.value = withContext(Dispatchers.Default) { searchText(texts, query) }
            if (_hits.value.isEmpty()) {
                val noText = texts.all { it.text.isBlank() }
                message(if (noText) "This PDF has no searchable text (it may be a scan)" else "No matches")
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searching.value = null
        _hits.value = emptyList()
    }

    // ------------------------------------------------------------ merge

    fun merge(sources: List<Uri>, target: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _busy.value = "Merging ${sources.size} PDFs…"
            val ok = runCatching { mergePdfs(ctx, sources, target) }.isSuccess
            _busy.value = null
            if (ok) {
                message("Merged PDF saved")
                open(target)
            } else message("Couldn't merge those files")
        }
    }
}
