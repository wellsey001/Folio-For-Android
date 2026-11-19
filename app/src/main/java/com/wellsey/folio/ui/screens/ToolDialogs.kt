package com.wellsey.folio.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wellsey.folio.data.FieldKind
import com.wellsey.folio.data.FormField
import com.wellsey.folio.data.PageGeom
import com.wellsey.folio.data.TextBoxStyle
import com.wellsey.folio.ui.ReaderViewModel
import java.io.File
import kotlin.math.max
import kotlin.math.min

val InkColors = listOf(0xFF1B1B1F, 0xFFD32F2F, 0xFF1565C0, 0xFF2E7D32).map { it.toInt() }
val HighlightColors = listOf(0xFFFFEB3B, 0xFF69F0AE, 0xFF40C4FF, 0xFFFF80AB).map { it.toInt() }

@Composable
fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
            .padding(4.dp)
            .background(Color(color), CircleShape)
            .clickable(onClick = onClick)
    )
}

// ------------------------------------------------------------------ text box

private data class TextPreset(val label: String, val style: TextBoxStyle)

@Composable
fun TextBoxDialog(onDismiss: () -> Unit, onAdd: (String, TextBoxStyle) -> Unit) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(12f) }
    var kind by remember { mutableIntStateOf(0) }
    var color by remember { mutableIntStateOf(InkColors[0]) }
    val style = when (kind) {
        1 -> TextBoxStyle(size, 0xFF3E2C00.toInt(), 0xFFFFF59D.toInt()) // sticky note
        else -> TextBoxStyle(size, color, null)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type here…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == 0, onClick = { kind = 0 }, label = { Text("Plain text") })
                    FilterChip(selected = kind == 1, onClick = { kind = 1 }, label = { Text("Sticky note") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(9f to "S", 12f to "M", 16f to "L", 22f to "XL").forEach { (s, l) ->
                        FilterChip(selected = size == s, onClick = { size = s }, label = { Text(l) })
                    }
                }
                if (kind == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InkColors.forEach { c -> ColorDot(c, color == c) { color = c } }
                    }
                }
                Text(
                    "Uses a standard PDF font, so letters outside Western European alphabets and emoji will show as \"?\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text.trimEnd(), style) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ signature

private fun savedSignatureFile(context: Context) = File(context.filesDir, "signature.png")

fun loadSavedSignature(context: Context): Bitmap? =
    savedSignatureFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

/** Draw your signature with a finger. Returns a transparent image cropped to the ink. */
@Composable
fun SignatureDialog(onDismiss: () -> Unit, onUse: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val saved = remember { loadSavedSignature(context) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val live = remember { mutableStateListOf<Offset>() }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val ink = Color(0xFF0D1B4C)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.6f)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x33000000), RoundedCornerShape(12.dp))
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { live.clear(); live.add(it) },
                                onDrag = { c, _ -> c.consume(); live.add(c.position) },
                                onDragEnd = { if (live.size > 1) strokes.add(live.toList()); live.clear() },
                                onDragCancel = { live.clear() },
                            )
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val all = strokes.toList() + listOf(live.toList())
                        for (s in all) {
                            if (s.size < 2) continue
                            val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                            drawPath(path, ink, style = DrawStroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    if (strokes.isEmpty() && live.isEmpty()) {
                        Text("Sign here", color = Color(0x55000000), modifier = Modifier.align(Alignment.Center))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { strokes.clear() }) { Text("Clear") }
                    if (saved != null) {
                        OutlinedButton(onClick = { onUse(saved) }) { Text("Use saved") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = strokes.isNotEmpty(), onClick = {
                val bmp = renderSignature(strokes.toList(), padSize, ink.toArgb())
                if (bmp != null) {
                    runCatching { savedSignatureFile(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    onUse(bmp)
                }
            }) { Text("Use") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun renderSignature(strokes: List<List<Offset>>, pad: IntSize, color: Int): Bitmap? {
    val pts = strokes.flatten()
    if (pts.isEmpty() || pad.width == 0) return null
    val margin = 12f
    val minX = max(0f, pts.minOf { it.x } - margin)
    val minY = max(0f, pts.minOf { it.y } - margin)
    val maxX = min(pad.width.toFloat(), pts.maxOf { it.x } + margin)
    val maxY = min(pad.height.toFloat(), pts.maxOf { it.y } + margin)
    val scale = 2f // extra resolution so it stays sharp when printed
    val w = ((maxX - minX) * scale).toInt().coerceAtLeast(1)
    val h = ((maxY - minY) * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 7f * scale / 2f * 1.4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (s in strokes) {
        val path = android.graphics.Path()
        s.forEachIndexed { i, p ->
            val x = (p.x - minX) * scale
            val y = (p.y - minY) * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}

// ------------------------------------------------------------------ pages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesSheet(
    vm: ReaderViewModel,
    pages: List<PageGeom>,
    version: Int,
    onDismiss: () -> Unit,
    onGoTo: (Int) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sel = selected.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Pages",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "Tap a page to select it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(pages.size) { i ->
                    val g = pages[i]
                    val thumb by produceState<Bitmap?>(null, i, version) { value = vm.render(i, 240) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(6.dp)
                            .clickable { selected = i },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(g.width / g.height)
                                .background(Color.White)
                                .border(
                                    if (i == sel) 3.dp else 1.dp,
                                    if (i == sel) MaterialTheme.colorScheme.primary else Color(0x22000000),
                                ),
                        ) {
                            thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                        }
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = if (i == sel) FontWeight.Bold else null)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                SheetAction(Icons.Filled.RotateLeft, "Rotate") { vm.rotate(sel, -90) }
                SheetAction(Icons.Filled.RotateRight, "Rotate") { vm.rotate(sel, 90) }
                SheetAction(Icons.Filled.ArrowBack, "Earlier") {
                    if (sel > 0) { vm.move(sel, sel - 1); selected = sel - 1 }
                }
                SheetAction(Icons.Filled.ArrowForward, "Later") {
                    if (sel < pages.size - 1) { vm.move(sel, sel + 1); selected = sel + 1 }
                }
                SheetAction(Icons.Filled.Delete, "Delete") { confirmDelete = true }
                SheetAction(Icons.Filled.MenuBook, "Go to") { onGoTo(sel) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete page ${sel + 1}?") },
            text = { Text("You can bring it back with Undo until you close the file.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deletePageAt(sel) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ------------------------------------------------------------------ forms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(vm: ReaderViewModel, onDismiss: () -> Unit) {
    val fields by produceState<List<FormField>?>(null) { value = vm.formFields() }
    val values = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(fields) { fields?.forEach { if (it.name !in values) values[it.name] = it.value } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("Fill in form", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            val list = fields
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                list.isEmpty() -> Text("This PDF has no fillable fields.")
                else -> LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list.filterNot { it.readOnly }, key = { it.name }) { f ->
                        val v = values[f.name] ?: f.value
                        when (f.kind) {
                            FieldKind.Text -> OutlinedTextField(
                                value = v,
                                onValueChange = { values[f.name] = it },
                                label = { Text(f.label) },
                                singleLine = !f.multiline,
                                minLines = if (f.multiline) 3 else 1,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FieldKind.Check -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { values[f.name] = if (v == "true") "false" else "true" },
                            ) {
                                Checkbox(checked = v == "true", onCheckedChange = { values[f.name] = if (it) "true" else "false" })
                                Text(f.label)
                            }
                            FieldKind.Choice -> {
                                var open by remember { mutableStateOf(false) }
                                Column {
                                    Text(f.label, style = MaterialTheme.typography.labelLarge)
                                    Box {
                                        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text(v.ifBlank { "Choose…" })
                                        }
                                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                            f.options.forEach { o ->
                                                DropdownMenuItem(text = { Text(o) }, onClick = { values[f.name] = o; open = false })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = !list.isNullOrEmpty(),
                    onClick = {
                        val changed = list.orEmpty().filter { values[it.name] != null && values[it.name] != it.value }
                            .associate { it.name to values[it.name].orEmpty() }
                        if (changed.isNotEmpty()) vm.fillForm(changed)
                        onDismiss()
                    },
                ) { Text("Apply") }
            }
        }
    }
}

// ------------------------------------------------------------------ go to page

@Composable
fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val n = text.toIntOrNull()
    val valid = n != null && n in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                placeholder = { Text("1 – $pageCount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onGo(n!! - 1) }) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
