package com.wellsey.folio.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wellsey.folio.ui.ReaderViewModel

private data class Picked(val uri: Uri, val name: String)

/** Pick several PDFs, put them in order, and combine them into one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val files = remember { mutableStateListOf<Picked>() }

    fun nameOf(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "Document.pdf"

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u -> if (files.none { it.uri == u }) files += Picked(u, nameOf(u)) }
    }
    val target = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.merge(files.map { it.uri }, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Merge PDFs") },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Filled.Add, null); Text(" Add PDFs")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(enabled = files.size >= 2, onClick = { target.launch("Merged.pdf") }) {
                        Text("Merge ${files.size} files")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            item {
                Text(
                    if (files.isEmpty()) "Add two or more PDFs. They'll be joined in the order shown; use the arrows to reorder."
                    else "Pages will be joined in this order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            itemsIndexed(files, key = { _, f -> f.uri.toString() }) { i, f ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                ) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(enabled = i > 0, onClick = { files.add(i - 1, files.removeAt(i)) }) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                    }
                    IconButton(enabled = i < files.lastIndex, onClick = { files.add(i + 1, files.removeAt(i)) }) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                    }
                    IconButton(onClick = { files.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
