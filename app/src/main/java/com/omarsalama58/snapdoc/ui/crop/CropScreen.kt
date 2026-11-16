package com.omarsalama58.snapdoc.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun CropScreen(path: String, onCancel: () -> Unit, onConfirm: (Bitmap) -> Unit) {
    val scope = rememberCoroutineScope()
    var full by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember { mutableStateOf(floatArrayOf(0.08f, 0.08f, 0.92f, 0.08f, 0.92f, 0.92f, 0.08f, 0.92f)) }
    var busy by remember { mutableStateOf(false) }
    var cSize by remember { mutableStateOf(IntSize(1, 1)) }
    var active by remember { mutableStateOf(-1) }

    LaunchedEffect(path) {
        val result = withContext(Dispatchers.IO) {
            val b = Imaging.decodeFile(path)
            if (b == null) null else Pair(b, Imaging.detectCorners(b))
        }
        if (result == null) {
            onCancel()
        } else {
            full = result.first
            corners = result.second
        }
    }

    // A smaller copy used only for drawing
    val preview = remember(full) { full?.let { Imaging.scaleDown(it, 1400) } }

    fun rotateCw() {
        val b = full ?: return
        val c = corners
        fun f(i: Int) = floatArrayOf(1f - c[2 * i + 1], c[2 * i])
        val bl = f(3); val tl = f(0); val tr = f(1); val br = f(2)
        corners = floatArrayOf(bl[0], bl[1], tl[0], tl[1], tr[0], tr[1], br[0], br[1])
        full = Imaging.rotate(b, 90)
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Crop", onBack = onCancel)

            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                val pv = preview
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { cSize = it }
                        .pointerInput(pv) {
                            if (pv == null) return@pointerInput
                            val hit = 52.dp.toPx()
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val r = Imaging.fitRect(size.width.toFloat(), size.height.toFloat(), pv.width, pv.height)
                                    var best = -1
                                    var bestD = hit
                                    for (i in 0 until 4) {
                                        val px = r.l + corners[2 * i] * r.w
                                        val py = r.t + corners[2 * i + 1] * r.h
                                        val d = (Offset(px, py) - pos).getDistance()
                                        if (d < bestD) { bestD = d; best = i }
                                    }
                                    active = best
                                },
                                onDrag = { change, amount ->
                                    if (active >= 0) {
                                        change.consume()
                                        val r = Imaging.fitRect(size.width.toFloat(), size.height.toFloat(), pv.width, pv.height)
                                        val c = corners.copyOf()
                                        c[2 * active] = (c[2 * active] + amount.x / r.w).coerceIn(0f, 1f)
                                        c[2 * active + 1] = (c[2 * active + 1] + amount.y / r.h).coerceIn(0f, 1f)
                                        corners = c
                                    }
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 }
                            )
                        }
                ) {
                    if (pv != null) {
                        val r = Imaging.fitRect(size.width, size.height, pv.width, pv.height)
                        drawImage(
                            pv.asImageBitmap(),
                            dstOffset = IntOffset(r.l.roundToInt(), r.t.roundToInt()),
                            dstSize = IntSize(r.w.roundToInt(), r.h.roundToInt())
                        )
                        val pts = (0 until 4).map { Offset(r.l + corners[2 * it] * r.w, r.t + corners[2 * it + 1] * r.h) }
                        val quad = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            lineTo(pts[1].x, pts[1].y)
                            lineTo(pts[2].x, pts[2].y)
                            lineTo(pts[3].x, pts[3].y)
                            close()
                        }
                        val whole = Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(Offset(r.l, r.t), Size(r.w, r.h)))
                        }
                        drawPath(Path.combine(PathOperation.Difference, whole, quad), Color.Black.copy(alpha = 0.55f))
                        drawPath(quad, CyanAccent, style = Stroke(width = 3.dp.toPx()))
                        pts.forEach {
                            drawCircle(Color.White, radius = 13.dp.toPx(), center = it)
                            drawCircle(CyanAccent, radius = 13.dp.toPx(), center = it, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                }
                if (preview == null) {
                    Text("Loading...", color = TextWhite, modifier = Modifier.align(Alignment.Center))
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { rotateCw() }) {
                    Icon(Icons.Filled.RotateRight, contentDescription = "Rotate", tint = TextWhite)
                }
                IconButton(onClick = {
                    val b = full ?: return@IconButton
                    scope.launch {
                        corners = withContext(Dispatchers.Default) { Imaging.detectCorners(b) }
                    }
                }) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = "Auto detect", tint = TextWhite)
                }
                IconButton(onClick = {
                    corners = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
                }) {
                    Icon(Icons.Filled.Crop, contentDescription = "Full page", tint = TextWhite)
                }
                IconButton(
                    onClick = {
                        val b = full ?: return@IconButton
                        busy = true
                        scope.launch {
                            val out = withContext(Dispatchers.Default) { Imaging.warp(b, corners) }
                            busy = false
                            onConfirm(out)
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = NavyDeep,
                        modifier = Modifier.size(52.dp).background(CyanAccent, androidx.compose.foundation.shape.CircleShape).padding(10.dp))
                }
            }
        }
        if (busy) BusyOverlay("Cropping...")
    }
}
