package com.omarsalama58.snapdoc.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---- Overlays drawn on top of a page. Coordinates are normalized (0..1) relative to the page. ----
sealed interface Overlay
data class TextOv(val text: String, val x: Float, val y: Float, val size: Float, val color: Int) : Overlay
data class StrokeOv(val pts: List<Offset>, val color: Int, val width: Float) : Overlay
data class SigOv(val path: String, val x: Float, val y: Float, val width: Float) : Overlay

object SigCache {
    private val map = HashMap<String, Bitmap>()
    fun get(path: String): Bitmap? {
        map[path]?.let { return it }
        val b = BitmapFactory.decodeFile(path) ?: return null
        map[path] = b
        return b
    }
}

data class FitRect(val l: Float, val t: Float, val w: Float, val h: Float)

object Imaging {
    const val MAX_SIDE = 2600

    // ---------------------------------------------------------------- loading

    fun copyUriToCache(ctx: Context, uri: Uri): File? {
        return try {
            val f = File(ctx.cacheDir, "imp_${System.nanoTime()}.jpg")
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            f
        } catch (e: Exception) {
            null
        }
    }

    private fun exifRotation(path: String): Int {
        return try {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun decodeFile(path: String, maxSide: Int = MAX_SIDE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        val rot = exifRotation(path)
        if (rot != 0) bmp = rotate(bmp, rot)
        return scaleDown(bmp, maxSide)
    }

    fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val m = max(bmp.width, bmp.height)
        if (m <= maxSide) return bmp
        val s = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(
            bmp,
            max(1, (bmp.width * s).roundToInt()),
            max(1, (bmp.height * s).roundToInt()),
            true
        )
    }

    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun fitRect(cw: Float, ch: Float, bw: Int, bh: Int): FitRect {
        val s = min(cw / bw, ch / bh)
        val w = bw * s
        val h = bh * s
        return FitRect((cw - w) / 2f, (ch - h) / 2f, w, h)
    }

    // ---------------------------------------------------------------- edge detection

    /** Returns 8 normalized values: TL, TR, BR, BL (x,y each). */
    fun detectCorners(src: Bitmap): FloatArray {
        EdgeDetector.detect(src)?.let { return it }
        return detectCornersLegacy(src)
    }

    private fun detectCornersLegacy(src: Bitmap): FloatArray {
        val fallback = floatArrayOf(0.08f, 0.08f, 0.92f, 0.08f, 0.92f, 0.92f, 0.08f, 0.92f)
        try {
            val sc = 220f / max(src.width, src.height)
            val w = max(8, (src.width * sc).roundToInt())
            val h = max(8, (src.height * sc).roundToInt())
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            var g = IntArray(w * h) {
                val c = px[it]
                (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
            }
            g = boxBlur(g, w, h, 2)
            val t = otsu(g)
            var best: FloatArray? = null
            var bestScore = 0f
            for (bright in listOf(true, false)) {
                val mask = BooleanArray(w * h) { if (bright) g[it] > t else g[it] <= t }
                val comp = largestComponent(mask, w, h) ?: continue
                val frac = comp.area.toFloat() / (w * h)
                val borderFrac = comp.border.toFloat() / (2f * (w + h))
                if (frac in 0.12f..0.97f && borderFrac < 0.5f) {
                    val score = frac * (1f - borderFrac)
                    if (score > bestScore) {
                        bestScore = score
                        best = comp.corners
                    }
                }
            }
            val c = best ?: return fallback
            // reject degenerate quads
            val area = quadArea(c)
            return if (area < 0.1f) fallback else c
        } catch (e: Exception) {
            return fallback
        }
    }

    private fun quadArea(c: FloatArray): Float {
        var a = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            a += c[2 * i] * c[2 * j + 1] - c[2 * j] * c[2 * i + 1]
        }
        return kotlin.math.abs(a) / 2f
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            var n = 0
            for (dy in -r..r) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -r..r) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    s += src[yy * w + xx]
                    n++
                }
            }
            out[y * w + x] = s / n
        }
        return out
    }

    private fun otsu(g: IntArray): Int {
        val hist = IntArray(256)
        for (v in g) hist[v.coerceIn(0, 255)]++
        val total = g.size
        var sum = 0L
        for (i in 0 until 256) sum += i.toLong() * hist[i]
        var sumB = 0L
        var wB = 0
        var maxVar = -1.0
        var thr = 128
        for (i in 0 until 256) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i.toLong() * hist[i]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val v = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (v > maxVar) {
                maxVar = v
                thr = i
            }
        }
        return thr
    }

    private class Comp(val area: Int, val border: Int, val corners: FloatArray)

    private fun largestComponent(mask: BooleanArray, w: Int, h: Int): Comp? {
        val seen = BooleanArray(w * h)
        val q = IntArray(w * h)
        var best: Comp? = null
        for (s in mask.indices) {
            if (!mask[s] || seen[s]) continue
            var head = 0
            var tail = 0
            q[tail++] = s
            seen[s] = true
            var area = 0
            var border = 0
            var minS = Int.MAX_VALUE
            var maxS = Int.MIN_VALUE
            var minD = Int.MAX_VALUE
            var maxD = Int.MIN_VALUE
            var tlx = 0; var tly = 0; var brx = 0; var bry = 0
            var trx = 0; var tr_y = 0; var blx = 0; var bly = 0
            while (head < tail) {
                val p = q[head++]
                val x = p % w
                val y = p / w
                area++
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border++
                val sum = x + y
                val d = x - y
                if (sum < minS) { minS = sum; tlx = x; tly = y }
                if (sum > maxS) { maxS = sum; brx = x; bry = y }
                if (d > maxD) { maxD = d; trx = x; tr_y = y }
                if (d < minD) { minD = d; blx = x; bly = y }
                if (x > 0 && mask[p - 1] && !seen[p - 1]) { seen[p - 1] = true; q[tail++] = p - 1 }
                if (x < w - 1 && mask[p + 1] && !seen[p + 1]) { seen[p + 1] = true; q[tail++] = p + 1 }
                if (y > 0 && mask[p - w] && !seen[p - w]) { seen[p - w] = true; q[tail++] = p - w }
                if (y < h - 1 && mask[p + w] && !seen[p + w]) { seen[p + w] = true; q[tail++] = p + w }
            }
            if (best == null || area > best.area) {
                best = Comp(
                    area, border,
                    floatArrayOf(
                        (tlx + 0.5f) / w, (tly + 0.5f) / h,
                        (trx + 0.5f) / w, (tr_y + 0.5f) / h,
                        (brx + 0.5f) / w, (bry + 0.5f) / h,
                        (blx + 0.5f) / w, (bly + 0.5f) / h
                    )
                )
            }
        }
        return best
    }

    // ---------------------------------------------------------------- perspective crop

    fun warp(src: Bitmap, c: FloatArray): Bitmap {
        val pts = FloatArray(8) { if (it % 2 == 0) c[it] * src.width else c[it] * src.height }
        fun d(i: Int, j: Int) = hypot(pts[2 * i] - pts[2 * j], pts[2 * i + 1] - pts[2 * j + 1])
        var w = max(d(0, 1), d(3, 2))
        var h = max(d(0, 3), d(1, 2))
        if (w < 16f || h < 16f) return src
        val big = max(w, h)
        if (big > MAX_SIDE) {
            val s = MAX_SIDE / big
            w *= s
            h *= s
        }
        val out = Bitmap.createBitmap(max(1, w.roundToInt()), max(1, h.roundToInt()), Bitmap.Config.ARGB_8888)
        val m = Matrix()
        m.setPolyToPoly(pts, 0, floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, 4)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    // ---------------------------------------------------------------- filters (see Filters.kt)

    fun applyParams(src: Bitmap, p: EditParams): Bitmap = Filters.apply(src, p)

    // ---------------------------------------------------------------- overlays

    private fun textPaint(o: TextOv, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = o.size * w
        color = o.color
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun drawOverlays(c: Canvas, w: Float, h: Float, list: List<Overlay>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (o in list) {
            when (o) {
                is StrokeOv -> {
                    if (o.pts.isEmpty()) continue
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = o.width * w
                    paint.color = o.color
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    if (o.pts.size == 1) {
                        val p = o.pts[0]
                        c.drawPoint(p.x * w, p.y * h, paint)
                    } else {
                        val path = Path()
                        path.moveTo(o.pts[0].x * w, o.pts[0].y * h)
                        for (i in 1 until o.pts.size) path.lineTo(o.pts[i].x * w, o.pts[i].y * h)
                        c.drawPath(path, paint)
                    }
                }
                is TextOv -> {
                    val tp = textPaint(o, w)
                    val lines = o.text.split("\n")
                    val lh = tp.descent() - tp.ascent()
                    val top = o.y * h - lh * lines.size / 2f
                    lines.forEachIndexed { i, line ->
                        c.drawText(line, o.x * w, top + lh * i - tp.ascent(), tp)
                    }
                }
                is SigOv -> {
                    val b = SigCache.get(o.path) ?: continue
                    paint.style = Paint.Style.FILL
                    val dw = o.width * w
                    val dh = dw * b.height / b.width
                    val cx = o.x * w
                    val cy = o.y * h
                    c.drawBitmap(b, null, RectF(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2), paint)
                }
            }
        }
    }

    fun overlayBounds(o: Overlay, w: Float, h: Float): RectF? = when (o) {
        is TextOv -> {
            val tp = textPaint(o, w)
            val lines = o.text.split("\n")
            val tw = lines.maxOf { tp.measureText(it) }
            val th = (tp.descent() - tp.ascent()) * lines.size
            RectF(o.x * w - tw / 2, o.y * h - th / 2, o.x * w + tw / 2, o.y * h + th / 2)
        }
        is SigOv -> {
            val b = SigCache.get(o.path)
            if (b == null) null else {
                val dw = o.width * w
                val dh = dw * b.height / b.width
                RectF(o.x * w - dw / 2, o.y * h - dh / 2, o.x * w + dw / 2, o.y * h + dh / 2)
            }
        }
        is StrokeOv -> null
    }
}
