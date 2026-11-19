package com.wellsey.folio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Size, rotation and crop box of one page. Sizes are in PDF points (1/72 inch).
 * [width]/[height] are as displayed (after rotation); the crop box is in the page's own space.
 */
data class PageGeom(
    val width: Float,
    val height: Float,
    val rotation: Int,
    val cropX: Float,
    val cropY: Float,
    val cropW: Float,
    val cropH: Float,
) {
    /** Converts a point on the displayed page (origin top-left) into PDF user space. */
    fun toPdf(u: Float, v: Float): PointF {
        val (x, y) = when (rotation) {
            90 -> v to u
            180 -> (cropW - u) to v
            270 -> (cropW - v) to (cropH - u)
            else -> u to (cropH - v)
        }
        return PointF(cropX + x, cropY + y)
    }
}

/**
 * One open PDF. Edits go to a working copy in the app's cache; "Save" writes it back.
 * Pages are drawn with Android's PdfRenderer; edits are made with PDFBox.
 * Everything touching the file runs on one background thread, because PdfRenderer
 * can only work on one page at a time.
 */
class PdfSession(context: Context, val sourceUri: Uri, val name: String) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.cacheDir, "session").apply { deleteRecursively(); mkdirs() }
    val work = File(dir, "work.pdf")
    private val history = ArrayDeque<File>()
    private var historyCounter = 0

    private val executor = Executors.newSingleThreadExecutor()
    private val thread = executor.asCoroutineDispatcher()

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    var pages: List<PageGeom> = emptyList()
        private set
    /** Goes up by one after every edit, so pages are re-drawn. */
    var version = 0
        private set
    val canUndo: Boolean get() = history.isNotEmpty()

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 5).toInt().coerceAtMost(160 * 1024 * 1024)

    /** Copies the file in and opens it. Throws [PasswordProtectedException] for locked PDFs. */
    suspend fun open() = withContext(thread) {
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            work.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("Couldn't read the file")
        reopen()
    }

    private fun reopen() {
        renderer?.close()
        pfd?.close()
        val fd = ParcelFileDescriptor.open(work, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = try {
            PdfRenderer(fd)
        } catch (e: SecurityException) {
            fd.close(); throw PasswordProtectedException()
        }
        pfd = fd
        renderer = r
        pages = readGeometry(r)
        cache.evictAll()
    }

    private fun readGeometry(r: PdfRenderer): List<PageGeom> {
        val boxes = try {
            PDDocument.load(work).use { doc ->
                (0 until doc.numberOfPages).map { i ->
                    val p = doc.getPage(i)
                    val box = p.cropBox
                    Triple(((p.rotation % 360) + 360) % 360, RectF(box.lowerLeftX, box.lowerLeftY, box.width, box.height), Unit)
                }
            }
        } catch (e: Exception) {
            null
        }
        return (0 until r.pageCount).map { i ->
            val page = r.openPage(i)
            val w = page.width.toFloat()
            val h = page.height.toFloat()
            page.close()
            val b = boxes?.getOrNull(i)
            val rot = b?.first ?: 0
            val crop = b?.second
            PageGeom(
                width = w, height = h, rotation = rot,
                cropX = crop?.left ?: 0f, cropY = crop?.top ?: 0f,
                cropW = crop?.right ?: if (rot % 180 == 0) w else h,
                cropH = crop?.bottom ?: if (rot % 180 == 0) h else w,
            )
        }
    }

    /** Draws a page [widthPx] wide. Recently drawn pages come from a cache. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(thread) {
        val key = "$version:$index:$widthPx"
        cache.get(key)?.let { return@withContext it }
        val r = renderer ?: return@withContext null
        if (index !in 0 until r.pageCount) return@withContext null
        val page = r.openPage(index)
        try {
            val w = widthPx.coerceAtLeast(1)
            val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache.put(key, bmp)
            bmp
        } finally {
            page.close()
        }
    }

    /**
     * Changes the document with PDFBox. The previous version is kept for Undo.
     * Returns false (and leaves the file untouched) if the change fails.
     */
    suspend fun edit(change: (PDDocument) -> Unit): Boolean = withContext(thread) {
        val backup = File(dir, "undo_${historyCounter++}.pdf")
        work.copyTo(backup, overwrite = true)
        val temp = File(dir, "edit.pdf")
        try {
            PDDocument.load(work).use { doc ->
                change(doc)
                doc.save(temp)
            }
            renderer?.close(); renderer = null
            pfd?.close(); pfd = null
            temp.copyTo(work, overwrite = true)
            temp.delete()
            history.addLast(backup)
            while (history.size > 15) history.removeFirst().delete()
            version++
            reopen()
            true
        } catch (e: Exception) {
            backup.delete()
            temp.delete()
            if (renderer == null) runCatching { reopen() }
            false
        }
    }

    suspend fun undo(): Boolean = withContext(thread) {
        val last = history.removeLastOrNull() ?: return@withContext false
        renderer?.close(); renderer = null
        pfd?.close(); pfd = null
        last.copyTo(work, overwrite = true)
        last.delete()
        version++
        reopen()
        true
    }

    /** Reads the document with PDFBox without changing it. */
    suspend fun <T> read(block: (PDDocument) -> T): T = withContext(thread) {
        PDDocument.load(work).use(block)
    }

    /** Writes the working copy to [target] (the original file or a new one). */
    suspend fun saveTo(target: Uri) = withContext(thread) {
        val out = appContext.contentResolver.openOutputStream(target, "wt")
            ?: throw IllegalStateException("Can't write there")
        out.use { o -> work.inputStream().use { it.copyTo(o) } }
    }

    /** A copy of the current document for sharing. */
    suspend fun shareCopy(): File = withContext(thread) {
        val shareDir = File(appContext.cacheDir, "share").apply { mkdirs() }
        val f = File(shareDir, name.ifBlank { "document.pdf" }.let { if (it.endsWith(".pdf", true)) it else "$it.pdf" })
        work.copyTo(f, overwrite = true)
        f
    }

    fun close() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        executor.shutdown()
        cache.evictAll()
    }
}

class PasswordProtectedException : Exception("This PDF is password-protected")
