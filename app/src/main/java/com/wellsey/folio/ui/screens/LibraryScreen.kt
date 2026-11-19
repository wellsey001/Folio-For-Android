package com.wellsey.folio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.folio.data.RecentFile
import com.wellsey.folio.ui.ReaderViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: ReaderViewModel, onOpen: (Uri) -> Unit, onMerge: () -> Unit) {
    val recents by vm.recents.items.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<RecentFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onOpen(uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Folio") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Button(
                        onClick = { picker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open PDF")
                    }
                    FilledTonalButton(onClick = onMerge, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Filled.CallMerge, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Merge PDFs")
                    }
                }
            }
            item {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (recents.isEmpty()) {
                item {
                    Text(
                        "PDFs you open will appear here. You can also set Folio as your default PDF app: " +
                            "open any PDF, choose Folio and tap \"Always\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(recents, key = { it.uri }) { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpen(Uri.parse(r.uri)) },
                            onLongClick = { removing = r },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 50.dp)
                            .background(Color(0x1FB2452E), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFB2452E))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val pages = if (r.pageCount > 0) " · page ${r.page + 1} of ${r.pageCount}" else ""
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(r.openedAt)) + pages,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    removing?.let { r ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from Recent?") },
            text = { Text("\"${r.name}\" stays on your phone; it's just removed from this list.") },
            confirmButton = { TextButton(onClick = { vm.recents.remove(r.uri); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
