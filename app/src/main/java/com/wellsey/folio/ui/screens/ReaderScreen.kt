package com.wellsey.folio.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.folio.ui.DocState
import com.wellsey.folio.ui.ReaderViewModel
import com.wellsey.folio.ui.Tool
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReaderScreen(vm: ReaderViewModel, doc: DocState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strokes by vm.strokes.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var tool by remember { mutableStateOf(Tool.Read) }
    var night by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var penColor by remember { mutableIntStateOf(InkColors[0]) }
    var highlightColor by remember { mutableIntStateOf(HighlightColors[0]) }
    var thick by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hitIndex by remember { mutableIntStateOf(0) }

    var menu by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var textAt by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var signing by remember { mutableStateOf(false) }
    var signature by remember { mutableStateOf<Bitmap?>(null) }
    var placement by remember { mutableStateOf<Placement?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveCopy(uri)
    }
    val copyName = doc.name.removeSuffix(".pdf").removeSuffix(".PDF") + " (edited).pdf"

    // Open at the page you were last reading.
    LaunchedEffect(doc.uri) {
        if (doc.startPage in doc.pages.indices) listState.scrollToItem(doc.startPage)
    }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(Unit) {
        snapshotFlow { currentPage }.distinctUntilChanged().debounce(800).collect { vm.rememberPage(it) }
    }

    // Jump to the selected search result.
    val density = LocalDensity.current
    LaunchedEffect(hits, hitIndex) {
        val hit = hits.getOrNull(hitIndex) ?: return@LaunchedEffect
        val geom = doc.pages.getOrNull(hit.page) ?: return@LaunchedEffect
        val top = hit.rects.firstOrNull()?.top ?: 0f
        val pageWidthPx = listState.layoutInfo.viewportSize.width * zoom
        val offsetPx = (top * pageWidthPx / geom.width - with(density) { 120.dp.toPx() }).toInt().coerceAtLeast(0)
        listState.animateScrollToItem(hit.page, offsetPx)
    }

    val drawing = tool == Tool.Pen || tool == Tool.Highlighter
    val leave: () -> Unit = {
        if (doc.dirty) {
            confirmLeave = true
        } else {
            onClose()
        }
    }
    BackHandler {
        when {
            placement != null -> placement = null
            drawing && strokes.isNotEmpty() -> vm.commitStrokes()
            tool != Tool.Read -> tool = Tool.Read
            searchOpen -> { searchOpen = false; vm.clearSearch() }
            zoom > 1f -> zoom = 1f
            else -> leave()
        }
    }

    Scaffold(
        topBar = {
            if (searchOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(4.dp),
                ) {
                    IconButton(onClick = { searchOpen = false; vm.clearSearch() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Find in document") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { hitIndex = 0; vm.search(query) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (hits.isNotEmpty()) {
                        Text("${hitIndex + 1}/${hits.size}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { hitIndex = (hitIndex - 1 + hits.size) % hits.size }) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Previous")
                        }
                        IconButton(onClick = { hitIndex = (hitIndex + 1) % hits.size }) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Next")
                        }
                    } else {
                        IconButton(onClick = { hitIndex = 0; vm.search(query) }) { Icon(Icons.Filled.Search, "Search") }
                    }
                }
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") } },
                    title = {
                        Column {
                            Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Page ${(currentPage + 1).coerceAtMost(doc.pages.size)} of ${doc.pages.size}" +
                                    if (doc.dirty) " · unsaved changes" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (doc.canUndo) IconButton(onClick = { vm.undo() }) { Icon(Icons.Filled.Undo, "Undo") }
                        if (doc.dirty) IconButton(onClick = { vm.save { saveCopy.launch(copyName) } }) { Icon(Icons.Filled.Save, "Save") }
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Go to page…") }, onClick = { menu = false; showGoTo = true })
                            DropdownMenuItem(text = { Text("Pages: rotate, move, delete") }, onClick = { menu = false; showPages = true })
                            if (doc.hasForm) {
                                DropdownMenuItem(text = { Text("Fill in form") }, onClick = { menu = false; showForm = true })
                            }
                            DropdownMenuItem(
                                text = { Text(if (night) "Normal colours" else "Night mode") },
                                onClick = { menu = false; night = !night },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Save") }, onClick = { menu = false; vm.save { saveCopy.launch(copyName) } })
                            DropdownMenuItem(text = { Text("Save a copy…") }, onClick = { menu = false; saveCopy.launch(copyName) })
                            DropdownMenuItem(text = { Text("Share") }, onClick = {
                                menu = false
                                scope.launch {
                                    val f = vm.shareFile() ?: return@launch
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(Intent.createChooser(send, null))
                                }
                            })
                        }
                    },
                )
            }
        },
        bottomBar = {
            Column {
                busy?.let { label ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    }
                }
                searching?.let { label ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(label, modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp))
                    }
                }
                ToolOptions(
                    tool = tool,
                    penColor = penColor, onPenColor = { penColor = it },
                    highlightColor = highlightColor, onHighlightColor = { highlightColor = it },
                    thick = thick, onThick = { thick = it },
                    strokeCount = strokes.size,
                    placement = placement,
                    onPlacementWidth = { w -> placement = placement?.copy(width = w) },
                    onUndoStroke = { vm.undoStroke() },
                    onDone = {
                        if (placement != null) {
                            val p = placement!!
                            vm.addSignature(p.page, p.box, p.bitmap)
                            placement = null
                        } else vm.commitStrokes()
                        tool = Tool.Read
                    },
                    onCancel = {
                        vm.discardStrokes(); placement = null; tool = Tool.Read
                    },
                )
                NavigationBar {
                    ToolItem(Tool.Read, tool, Icons.Filled.PanTool, "Read") { tool = it }
                    ToolItem(Tool.Pen, tool, Icons.Filled.Edit, "Pen") { tool = it }
                    ToolItem(Tool.Highlighter, tool, Icons.Outlined.BorderColor, "Highlight") { tool = it }
                    ToolItem(Tool.Text, tool, Icons.Filled.TextFields, "Text") { tool = it }
                    ToolItem(Tool.Sign, tool, Icons.Filled.Draw, "Sign") {
                        tool = it
                        if (signature == null) signing = true
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (night) Color(0xFF101010) else Color(0xFFE8E6E1))
        ) {
            val viewport = maxWidth
            val hScroll = rememberScrollState()
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(drawing) {
                        // Two-finger pinch to zoom (one finger still scrolls or draws).
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val factor = event.calculateZoom()
                                    val centroidX = event.calculateCentroid().x
                                    val old = zoom
                                    val next = (old * factor).coerceIn(1f, 4f)
                                    if (next != old) {
                                        zoom = next
                                        val target = ((hScroll.value + centroidX) * next / old - centroidX).toInt()
                                        scope.launch { delay(16); hScroll.scrollTo(target.coerceAtLeast(0)) }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .horizontalScroll(hScroll, enabled = zoom > 1f && !drawing)
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = !drawing && placement == null,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
                    modifier = Modifier
                        .width(viewport * zoom)
                        .fillMaxHeight(),
                ) {
                    items(doc.pages.size) { i ->
                        val pageHits = hits.filter { it.page == i }
                        val current = hits.getOrNull(hitIndex)?.takeIf { it.page == i }
                        PageView(
                            vm = vm,
                            index = i,
                            geom = doc.pages[i],
                            version = doc.version,
                            night = night,
                            tool = tool,
                            penColor = if (tool == Tool.Highlighter) highlightColor else penColor,
                            penWidth = if (tool == Tool.Highlighter) (if (thick) 18f else 11f) else (if (thick) 3.5f else 1.5f),
                            pendingStrokes = strokes.filter { it.page == i },
                            hitRects = pageHits.flatMap { it.rects },
                            currentHitRects = current?.rects.orEmpty(),
                            placement = placement,
                            onTap = { page, at ->
                                when (tool) {
                                    Tool.Text -> textAt = page to at
                                    Tool.Sign -> signature?.let { placement = Placement(page, at, 150f, it) }
                                    else -> Unit
                                }
                            },
                            onMovePlacement = { c -> placement = placement?.copy(center = c) },
                        )
                    }
                }
            }

            ToolHint(tool, placement != null, signature != null, Modifier.align(Alignment.TopCenter))
        }
    }

    // ------------------------------------------------------------ dialogs
    textAt?.let { (page, at) ->
        TextBoxDialog(
            onDismiss = { textAt = null },
            onAdd = { text, style -> textAt = null; vm.addTextBox(page, at, text, style) },
        )
    }
    if (signing) {
        SignatureDialog(
            onDismiss = { signing = false; if (signature == null) tool = Tool.Read },
            onUse = { signature = it; signing = false },
        )
    }
    if (showPages) {
        PagesSheet(vm, doc.pages, doc.version, onDismiss = { showPages = false }) { page ->
            showPages = false
            scope.launch { listState.scrollToItem(page) }
        }
    }
    if (showForm) FormSheet(vm, onDismiss = { showForm = false })
    if (showGoTo) {
        GoToPageDialog(doc.pages.size, onDismiss = { showGoTo = false }) { page ->
            showGoTo = false
            scope.launch { listState.scrollToItem(page) }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save changes?") },
            text = { Text("You've changed \"${doc.name}\". Save before closing?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.save { saveCopy.launch(copyName) }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
                    TextButton(onClick = { confirmLeave = false; onClose() }) { Text("Discard") }
                }
            },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ToolItem(
    t: Tool,
    current: Tool,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onSelect: (Tool) -> Unit,
) {
    NavigationBarItem(
        selected = current == t,
        onClick = { onSelect(t) },
        icon = { Icon(icon, null) },
        label = { Text(label) },
    )
}

@Composable
private fun ToolOptions(
    tool: Tool,
    penColor: Int, onPenColor: (Int) -> Unit,
    highlightColor: Int, onHighlightColor: (Int) -> Unit,
    thick: Boolean, onThick: (Boolean) -> Unit,
    strokeCount: Int,
    placement: Placement?,
    onPlacementWidth: (Float) -> Unit,
    onUndoStroke: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val drawing = tool == Tool.Pen || tool == Tool.Highlighter
    if (!drawing && placement == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (drawing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val colors = if (tool == Tool.Highlighter) HighlightColors else InkColors
                    val selected = if (tool == Tool.Highlighter) highlightColor else penColor
                    colors.forEach { c ->
                        ColorDot(c, c == selected) { if (tool == Tool.Highlighter) onHighlightColor(c) else onPenColor(c) }
                    }
                    Spacer(Modifier.weight(1f))
                    FilterChip(selected = thick, onClick = { onThick(!thick) }, label = { Text("Thick") })
                    IconButton(enabled = strokeCount > 0, onClick = onUndoStroke) {
                        Icon(Icons.Filled.Undo, "Undo last stroke")
                    }
                }
            } else if (placement != null) {
                Text("Drag the signature into place, then set its size", style = MaterialTheme.typography.labelLarge)
                Slider(value = placement.width, onValueChange = onPlacementWidth, valueRange = 50f..320f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, null); Text(" Cancel")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDone, enabled = placement != null || strokeCount > 0) {
                    Icon(Icons.Filled.Done, null); Text(if (placement != null) " Place" else " Done")
                }
            }
        }
    }
}

@Composable
private fun ToolHint(tool: Tool, placing: Boolean, hasSignature: Boolean, modifier: Modifier = Modifier) {
    val text = when {
        tool == Tool.Pen || tool == Tool.Highlighter -> "Draw with one finger · tap Done to keep it"
        tool == Tool.Text -> "Tap where the text should go"
        tool == Tool.Sign && hasSignature && !placing -> "Tap where your signature should go"
        else -> null
    } ?: return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = modifier.padding(top = 10.dp),
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
