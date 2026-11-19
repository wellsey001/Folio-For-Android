package com.wellsey.folio.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------ drawing

/** A pen or highlighter stroke. Points are in page points, origin at the displayed page's top-left. */
data class Stroke(
    val page: Int,
    val color: Int,
    val width: Float,
    val highlighter: Boolean,
    val points: List<Offset>,
)

private fun PDPageContentStream.strokeColor(argb: Int) =
    setStrokingColor(PDColor(rgb(argb), PDDeviceRGB.INSTANCE))

private fun PDPageContentStream.fillColor(argb: Int) =
    setNonStrokingColor(PDColor(rgb(argb), PDDeviceRGB.INSTANCE))

private fun rgb(argb: Int) = floatArrayOf(
    ((argb shr 16) and 0xFF) / 255f,
    ((argb shr 8) and 0xFF) / 255f,
    (argb and 0xFF) / 255f,
)

private fun appendStream(doc: PDDocument, page: PDPage) =
    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)

/** Burns pen and highlighter strokes into the pages. */
fun applyStrokes(doc: PDDocument, pages: List<PageGeom>, strokes: List<Stroke>) {
    for ((pageIndex, list) in strokes.groupBy { it.page }) {
        val geom = pages.getOrNull(pageIndex) ?: continue
        val page = doc.getPage(pageIndex)
        appendStream(doc, page).use { cs ->
            for (s in list) {
                if (s.points.isEmpty()) continue
                cs.saveGraphicsState()
                if (s.highlighter) {
                    val gs = PDExtendedGraphicsState()
                    gs.strokingAlphaConstant = 0.35f
                    cs.setGraphicsStateParameters(gs)
                }
                cs.strokeColor(s.color)
                cs.setLineWidth(s.width)
                cs.setLineCapStyle(if (s.highlighter) 2 else 1)
                cs.setLineJoinStyle(1)
                val first = geom.toPdf(s.points[0].x, s.points[0].y)
                cs.moveTo(first.x, first.y)
                if (s.points.size == 1) {
                    cs.lineTo(first.x + 0.1f, first.y)
                } else {
                    for (p in s.points.drop(1)) {
                        val q = geom.toPdf(p.x, p.y)
                        cs.lineTo(q.x, q.y)
                    }
                }
                cs.stroke()
                cs.restoreGraphicsState()
            }
        }
    }
}

/**
 * Sets up a drawing frame for a box on the displayed page ([box] in page points, top-left origin),
 * so that inside it x goes right and y goes up as you see the page, whatever the page rotation.
 */
private fun PDPageContentStream.enterBox(geom: PageGeom, box: Rect) {
    val o = geom.toPdf(box.left, box.bottom)
    val x1 = geom.toPdf(box.left + 1f, box.bottom)
    val y1 = geom.toPdf(box.left, box.bottom - 1f)
    transform(Matrix(x1.x - o.x, x1.y - o.y, y1.x - o.x, y1.y - o.y, o.x, o.y))
}

// ------------------------------------------------------------------ text boxes

private val textFont = PDType1Font.HELVETICA

/** The built-in PDF font only covers Western European characters; swap anything else for "?". */
fun pdfSafe(text: String): String = buildString {
    for (ch in text) {
        append(if (ch.code in 32..126 || ch.code in 160..255) ch else if (ch == '\n') ch else '?')
    }
}

data class TextBoxStyle(val fontSize: Float, val textColor: Int, val background: Int?)

/** Measures a text box: width and height in points for [text] at [style]. */
fun measureTextBox(text: String, style: TextBoxStyle): Pair<Float, Float> {
    val lines = pdfSafe(text).split('\n')
    val pad = if (style.background != null) style.fontSize * 0.5f else 0f
    val widest = lines.maxOf { textFont.getStringWidth(it) / 1000f * style.fontSize }
    val height = lines.size * style.fontSize * 1.25f
    return (widest + pad * 2) to (height + pad * 2)
}

fun applyTextBox(doc: PDDocument, geom: PageGeom, pageIndex: Int, at: Offset, text: String, style: TextBoxStyle) {
    val (w, h) = measureTextBox(text, style)
    val box = Rect(at.x, at.y, at.x + w, at.y + h)
    val pad = if (style.background != null) style.fontSize * 0.5f else 0f
    appendStream(doc, doc.getPage(pageIndex)).use { cs ->
        cs.saveGraphicsState()
        cs.enterBox(geom, box)
        style.background?.let { bg ->
            cs.fillColor(bg)
            cs.addRect(0f, 0f, w, h)
            cs.fill()
        }
        cs.fillColor(style.textColor)
        cs.beginText()
        cs.setFont(textFont, style.fontSize)
        val lines = pdfSafe(text).split('\n')
        val lineHeight = style.fontSize * 1.25f
        cs.newLineAtOffset(pad, h - pad - style.fontSize)
        lines.forEachIndexed { i, line ->
            if (i > 0) cs.newLineAtOffset(0f, -lineHeight)
            cs.showText(line)
        }
        cs.endText()
        cs.restoreGraphicsState()
    }
}

// ------------------------------------------------------------------ signatures & pages

fun applyImage(doc: PDDocument, geom: PageGeom, pageIndex: Int, box: Rect, bitmap: Bitmap) {
    val image = LosslessFactory.createFromImage(doc, bitmap)
    appendStream(doc, doc.getPage(pageIndex)).use { cs ->
        cs.saveGraphicsState()
        cs.enterBox(geom, box)
        cs.drawImage(image, 0f, 0f, box.width, box.height)
        cs.restoreGraphicsState()
    }
}

fun rotatePage(doc: PDDocument, index: Int, degrees: Int) {
    val page = doc.getPage(index)
    page.rotation = (((page.rotation + degrees) % 360) + 360) % 360
}

fun deletePage(doc: PDDocument, index: Int) {
    if (doc.numberOfPages > 1) doc.removePage(index)
}

fun movePage(doc: PDDocument, from: Int, to: Int) {
    if (from == to) return
    val pages = doc.pages
    val page = pages.get(from)
    pages.remove(from)
    if (to >= pages.count) pages.add(page) else pages.insertBefore(page, pages.get(to))
}

// ------------------------------------------------------------------ forms

enum class FieldKind { Text, Check, Choice }

data class FormField(
    val name: String,
    val label: String,
    val kind: FieldKind,
    val value: String,
    val options: List<String> = emptyList(),
    val multiline: Boolean = false,
    val readOnly: Boolean = false,
)

fun readForm(doc: PDDocument): List<FormField> {
    val form = doc.documentCatalog.acroForm ?: return emptyList()
    val result = mutableListOf<FormField>()
    for (f: PDField in form.fieldTree) {
        val name = f.fullyQualifiedName ?: continue
        val label = (f.alternateFieldName ?: f.partialName ?: name).ifBlank { name }
        when (f) {
            is PDTextField -> result += FormField(name, label, FieldKind.Text, f.valueAsString ?: "", multiline = f.isMultiline, readOnly = f.isReadOnly)
            is PDCheckBox -> result += FormField(name, label, FieldKind.Check, if (f.isChecked) "true" else "false", readOnly = f.isReadOnly)
            is PDRadioButton -> result += FormField(name, label, FieldKind.Choice, f.valueAsString ?: "", options = f.exportValues.orEmpty().distinct(), readOnly = f.isReadOnly)
            is PDChoice -> result += FormField(name, label, FieldKind.Choice, f.valueAsString ?: "", options = f.optionsDisplayValues.orEmpty(), readOnly = f.isReadOnly)
            is PDPushButton, is PDSignatureField -> Unit
            else -> Unit
        }
    }
    return result
}

fun writeForm(doc: PDDocument, values: Map<String, String>) {
    val form = doc.documentCatalog.acroForm ?: return
    for ((name, value) in values) {
        val f = form.getField(name) ?: continue
        if (f.isReadOnly) continue
        try {
            when (f) {
                is PDCheckBox -> if (value == "true") f.check() else f.unCheck()
                is PDRadioButton -> if (value.isNotEmpty()) f.setValue(value)
                is PDChoice -> if (value.isNotEmpty()) f.setValue(value)
                is PDTextField -> f.setValue(value)
                else -> Unit
            }
        } catch (e: Exception) {
            // Couldn't build the field's appearance (missing font); let viewers redraw it.
            form.needAppearances = true
            runCatching { if (f is PDTextField) f.setValue(value) }
        }
    }
}

// ------------------------------------------------------------------ search

data class SearchHit(val page: Int, val rects: List<Rect>, val snippet: String)

/** The text on one page, with the box of each character in page points. */
class PageText(val text: String, val boxes: List<Rect?>)

private class PositionStripper : PDFTextStripper() {
    val sb = StringBuilder()
    val boxes = ArrayList<Rect?>()

    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
        for (tp in textPositions.orEmpty()) {
            val u = tp.unicode ?: continue
            val r = Rect(tp.xDirAdj, tp.yDirAdj - tp.heightDir, tp.xDirAdj + tp.widthDirAdj, tp.yDirAdj)
            for (c in u) {
                sb.append(c); boxes.add(r)
            }
        }
    }

    override fun writeWordSeparator() {
        sb.append(' '); boxes.add(null)
    }

    override fun writeLineSeparator() {
        sb.append(' '); boxes.add(null)
    }
}

fun extractPageTexts(doc: PDDocument, onPage: (Int) -> Unit): List<PageText> =
    (1..doc.numberOfPages).map { n ->
        onPage(n)
        val s = PositionStripper()
        s.sortByPosition = true
        s.startPage = n
        s.endPage = n
        runCatching { s.getText(doc) }
        PageText(s.sb.toString(), s.boxes.toList())
    }

fun search(texts: List<PageText>, query: String, limit: Int = 1000): List<SearchHit> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<SearchHit>()
    texts.forEachIndexed { page, pt ->
        val lower = pt.text.lowercase()
        var i = lower.indexOf(q)
        while (i >= 0 && hits.size < limit) {
            val rects = pt.boxes.subList(i, (i + q.length).coerceAtMost(pt.boxes.size)).filterNotNull()
            // Join the character boxes on each line into one rectangle.
            val merged = rects.groupBy { (it.bottom / 2).toInt() }.values.map { line ->
                Rect(line.minOf { it.left }, line.minOf { it.top }, line.maxOf { it.right }, line.maxOf { it.bottom })
            }
            val from = (i - 30).coerceAtLeast(0)
            val to = (i + q.length + 40).coerceAtMost(pt.text.length)
            val snippet = (if (from > 0) "…" else "") + pt.text.substring(from, to).trim() + if (to < pt.text.length) "…" else ""
            hits += SearchHit(page, merged, snippet)
            i = lower.indexOf(q, i + q.length)
        }
    }
    return hits
}

// ------------------------------------------------------------------ merging

suspend fun mergePdfs(context: Context, sources: List<Uri>, target: Uri) = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "merge").apply { deleteRecursively(); mkdirs() }
    val files = sources.mapIndexed { i, uri ->
        val f = File(dir, "$i.pdf")
        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("Couldn't read a file")
        f
    }
    val out = File(dir, "merged.pdf")
    val merger = PDFMergerUtility()
    files.forEach { merger.addSource(it) }
    merger.destinationFileName = out.absolutePath
    merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    context.contentResolver.openOutputStream(target, "wt")?.use { o -> out.inputStream().use { it.copyTo(o) } }
        ?: throw IllegalStateException("Couldn't write the merged file")
    dir.deleteRecursively()
}
