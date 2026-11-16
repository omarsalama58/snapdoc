package com.omarsalama58.snapdoc.ui.tools

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.omarsalama58.snapdoc.data.Signatures
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.ColorSwatch
import com.omarsalama58.snapdoc.ui.PrimaryButton
import com.omarsalama58.snapdoc.ui.SecondaryButton
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.toast
import kotlin.math.max
import kotlin.math.min

@Composable
fun SignatureScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var ink by remember { mutableStateOf(Color(0xFF000000)) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Create signature", onBack = onBack)
            Text("Sign inside the white box", color = TextMuted, modifier = Modifier.padding(horizontal = 16.dp))

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { live = listOf(it) },
                            onDrag = { change, _ -> change.consume(); live = live + change.position },
                            onDragEnd = { if (live.isNotEmpty()) strokes.add(live); live = emptyList() },
                            onDragCancel = { live = emptyList() }
                        )
                    }
            ) {
                fun drawStroke(pts: List<Offset>) {
                    if (pts.size < 2) return
                    val p = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                    }
                    drawPath(p, ink, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                strokes.forEach { drawStroke(it) }
                drawStroke(live)
            }

            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorSwatch(Color(0xFF000000), ink == Color(0xFF000000)) { ink = Color(0xFF000000) }
                ColorSwatch(Color(0xFF1565C0), ink == Color(0xFF1565C0)) { ink = Color(0xFF1565C0) }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton("Clear", Modifier.weight(1f)) { strokes.clear(); live = emptyList() }
                PrimaryButton("Save", Modifier.weight(1f)) {
                    val all = strokes.flatten()
                    if (all.isEmpty()) {
                        toast(ctx, "Draw your signature first")
                    } else {
                        val pad = 20f
                        val minX = all.minOf { it.x } - pad
                        val minY = all.minOf { it.y } - pad
                        val maxX = all.maxOf { it.x } + pad
                        val maxY = all.maxOf { it.y } + pad
                        val scale = max(1f, 700f / max(1f, maxX - minX))
                        val w = max(1, ((maxX - minX) * scale).toInt())
                        val h = max(1, ((maxY - minY) * scale).toInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = AndroidCanvas(bmp)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            color = ink.toArgb()
                            strokeWidth = 5f * ctx.resources.displayMetrics.density * scale
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        strokes.forEach { s ->
                            if (s.size >= 2) {
                                val path = AndroidPath()
                                path.moveTo((s[0].x - minX) * scale, (s[0].y - minY) * scale)
                                for (i in 1 until s.size) path.lineTo((s[i].x - minX) * scale, (s[i].y - minY) * scale)
                                c.drawPath(path, paint)
                            }
                        }
                        Signatures.save(ctx, bmp)
                        toast(ctx, "Signature saved")
                        onSaved()
                    }
                }
            }
        }
    }
}
