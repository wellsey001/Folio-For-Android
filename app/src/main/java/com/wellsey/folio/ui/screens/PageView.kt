package com.wellsey.folio.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wellsey.folio.data.PageGeom
import com.wellsey.folio.data.Stroke
import com.wellsey.folio.ui.ReaderViewModel
import com.wellsey.folio.ui.Tool
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A signature being positioned on a page. Position and size are in page points. */
data class Placement(val page: Int, val center: Offset, val width: Float, val bitmap: Bitmap) {
    val height: Float get() = width * bitmap.height / bitmap.width.coerceAtLeast(1)
    val box: Rect get() = Rect(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2)
}

/** Inverts page colours for reading in the dark. */
val NightFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.87f, 0f, 0f, 0f, 235f,
            0f, -0.87f, 0f, 0f, 235f,
            0f, 0f, -0.87f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

@Composable
fun PageView(
    vm: ReaderViewModel,
    index: Int,
    geom: PageGeom,
    version: Int,
    night: Boolean,
    tool: Tool,
    penColor: Int,
    penWidth: Float,
    pendingStrokes: List<Stroke>,
    hitRects: List<Rect>,
    currentHitRects: List<Rect>,
    placement: Placement?,
    onTap: (page: Int, at: Offset) -> Unit,
    onMovePlacement: (Offset) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(geom.width / geom.height)
            .background(Color.White)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = widthPx / geom.width // pixels per PDF point

        // Draw the page at a resolution that matches its size on screen (up to a limit).
        val target = (widthPx.coerceAtMost(2200f) / 100f).roundToInt() * 100
        var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(index, version, target) {
            if (bitmap != null) delay(150) // wait for pinch-zoom to settle
            bitmap = vm.render(index, target.coerceAtLeast(100))
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = if (night) NightFilter else null,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Text(
            "${index + 1}",
            color = Color(0x55000000),
            modifier = Modifier.align(Alignment.Center),
        )

        // Search highlights and drawing in progress.
        val live = remember { mutableStateListOf<Offset>() }
        Canvas(Modifier.fillMaxSize()) {
            for (r in hitRects) {
                drawRect(Color(0x55FFD54F), Offset(r.left * scale, r.top * scale), Size(r.width * scale, r.height * scale))
            }
            for (r in currentHitRects) {
                drawRect(Color(0x88FF8F00), Offset(r.left * scale, r.top * scale), Size(r.width * scale, r.height * scale))
            }
            fun drawPoints(points: List<Offset>, color: Int, width: Float, highlighter: Boolean) {
                if (points.isEmpty()) return
                val path = Path().apply {
                    moveTo(points[0].x * scale, points[0].y * scale)
                    if (points.size == 1) lineTo(points[0].x * scale + 0.5f, points[0].y * scale)
                    points.drop(1).forEach { lineTo(it.x * scale, it.y * scale) }
                }
                drawPath(
                    path,
                    Color(color).copy(alpha = if (highlighter) 0.35f else 1f),
                    style = DrawStroke(
                        width = width * scale,
                        cap = if (highlighter) StrokeCap.Square else StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            for (s in pendingStrokes) drawPoints(s.points, s.color, s.width, s.highlighter)
            drawPoints(live.toList(), penColor, penWidth, tool == Tool.Highlighter)
        }

        // Touch handling depends on the tool.
        val currentTool by rememberUpdatedState(tool)
        val color by rememberUpdatedState(penColor)
        val width by rememberUpdatedState(penWidth)
        val tap by rememberUpdatedState(onTap)
        val drawing = tool == Tool.Pen || tool == Tool.Highlighter
        val tapping = tool == Tool.Text || (tool == Tool.Sign && placement == null)
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (drawing) Modifier.pointerInput(index, scale) {
                        detectDragGestures(
                            onDragStart = { p -> live.clear(); live.add(p / scale) },
                            onDrag = { change, _ -> change.consume(); live.add(change.position / scale) },
                            onDragEnd = {
                                if (live.isNotEmpty()) {
                                    vm.addStroke(
                                        Stroke(index, color, width, currentTool == Tool.Highlighter, live.toList())
                                    )
                                }
                                live.clear()
                            },
                            onDragCancel = { live.clear() },
                        )
                    } else Modifier
                )
                .then(
                    if (tapping) Modifier.pointerInput(index, scale) {
                        detectTapGestures(onTap = { p -> tap(index, p / scale) })
                    } else Modifier
                )
        )

        // A signature being positioned: drag it into place.
        if (placement != null && placement.page == index) {
            val box = placement.box
            val move by rememberUpdatedState(onMovePlacement)
            val current by rememberUpdatedState(placement)
            Image(
                bitmap = placement.bitmap.asImageBitmap(),
                contentDescription = "Signature",
                modifier = Modifier
                    .offset { IntOffset((box.left * scale).roundToInt(), (box.top * scale).roundToInt()) }
                    .size(with(density) { (box.width * scale).toDp() }, with(density) { (box.height * scale).toDp() })
                    .border(1.5.dp, Color(0xFF1E88E5))
                    .pointerInput(scale) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            move(current.center + drag / scale)
                        }
                    },
            )
        }
    }
}
