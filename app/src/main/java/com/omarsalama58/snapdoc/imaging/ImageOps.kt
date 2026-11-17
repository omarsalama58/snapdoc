package com.omarsalama58.snapdoc.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Extra page operations: smart erase, watermark, date stamp, ID card page, book split, long image. */
object ImageOps {

    private fun mutableCopy(src: Bitmap): Bitmap = src.copy(Bitmap.Config.ARGB_8888, true)

    /** Removes whatever the user brushed over, filling it from the surroundings. */
    fun erase(src: Bitmap, strokes: List<StrokeOv>): Bitmap {
        if (strokes.isEmpty() || !Filters.ready) return src
        val maskBmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(maskBmp)
        c.drawColor(Color.BLACK)
        Imaging.drawOverlays(c, src.width.toFloat(), src.height.toFloat(), strokes.map { it.copy(color = Color.WHITE) })
        val maskRgba = Mat()
        val mask = Mat()
        val rgba = Mat()
        val bgr = Mat()
        val out = Mat()
        val outRgba = Mat()
        try {
            Utils.bitmapToMat(maskBmp, maskRgba)
            Imgproc.cvtColor(maskRgba, mask, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.threshold(mask, mask, 127.0, 255.0, Imgproc.THRESH_BINARY)
            Utils.bitmapToMat(src, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Photo.inpaint(bgr, mask, out, 5.0, Photo.INPAINT_TELEA)
            Imgproc.cvtColor(out, outRgba, Imgproc.COLOR_BGR2RGBA)
            val res = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outRgba, res)
            return res
        } catch (e: Throwable) {
            return src
        } finally {
            maskRgba.release(); mask.release(); rgba.release(); bgr.release(); out.release(); outRgba.release()
            maskBmp.recycle()
        }
    }

    /** Diagonal semi-transparent text across the page. */
    fun watermark(src: Bitmap, text: String): Bitmap {
        val out = mutableCopy(src)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 120, 120, 120)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = out.width * 0.11f
        }
        // shrink long texts so they fit along the diagonal
        val maxW = kotlin.math.hypot(out.width.toDouble(), out.height.toDouble()).toFloat() * 0.8f
        val tw = paint.measureText(text)
        if (tw > maxW) paint.textSize = paint.textSize * maxW / tw
        c.save()
        c.rotate(-30f, out.width / 2f, out.height / 2f)
        c.drawText(text, out.width / 2f, out.height / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        c.restore()
        return out
    }

    /** Date and time written in the bottom-right corner. */
    fun stamp(src: Bitmap, text: String): Bitmap {
        val out = mutableCopy(src)
        val c = Canvas(out)
        val size = max(24f, out.width * 0.035f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.DEFAULT_BOLD
        }
        val paint = Paint(shadow).apply { color = Color.rgb(255, 193, 7) }
        val x = out.width - size
        val y = out.height - size
        c.drawText(text, x + 2f, y + 2f, shadow)
        c.drawText(text, x, y, paint)
        return out
    }

    fun ensureLandscape(src: Bitmap): Bitmap = if (src.height > src.width) Imaging.rotate(src, 90) else src

    /** Puts the front and (optionally) back of an ID card on one A4 page. */
    fun composeIdPage(front: Bitmap, back: Bitmap?): Bitmap {
        val pw = 2480
        val ph = 3508
        val page = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val c = Canvas(page)
        c.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.rgb(200, 200, 200)
        }
        val cardW = 1750
        fun place(b: Bitmap, top: Int) {
            val s = cardW.toFloat() / b.width
            val h = (b.height * s).roundToInt()
            val left = (pw - cardW) / 2
            val dst = Rect(left, top, left + cardW, top + h)
            c.drawBitmap(b, null, dst, paint)
            c.drawRect(dst, border)
        }
        if (back == null) {
            val s = cardW.toFloat() / front.width
            place(front, ((ph - front.height * s) / 2).roundToInt())
        } else {
            val h1 = front.height * cardW.toFloat() / front.width
            val h2 = back.height * cardW.toFloat() / back.width
            val gap = 220f
            val total = h1 + h2 + gap
            val top = ((ph - total) / 2).coerceAtLeast(120f)
            place(front, top.roundToInt())
            place(back, (top + h1 + gap).roundToInt())
        }
        return page
    }

    /** Splits a photo of an open book into its left and right pages. */
    fun splitBook(src: Bitmap): List<Bitmap> {
        return if (src.width >= src.height) {
            val half = src.width / 2
            listOf(
                Bitmap.createBitmap(src, 0, 0, half, src.height),
                Bitmap.createBitmap(src, half, 0, src.width - half, src.height)
            )
        } else {
            val half = src.height / 2
            listOf(
                Bitmap.createBitmap(src, 0, 0, src.width, half),
                Bitmap.createBitmap(src, 0, half, src.width, src.height - half)
            )
        }
    }

    /** All pages stacked into one tall picture. */
    fun longImage(files: List<File>): Bitmap? {
        val width = 1080
        val parts = ArrayList<Bitmap>()
        for (f in files) {
            val b = Imaging.decodeFile(f.absolutePath, 2000) ?: continue
            val h = max(1, (b.height * width.toFloat() / b.width).roundToInt())
            parts.add(if (b.width == width) b else Bitmap.createScaledBitmap(b, width, h, true))
        }
        if (parts.isEmpty()) return null
        val gap = 24
        var total = parts.sumOf { it.height } + gap * (parts.size - 1)
        val maxH = 12000
        val scale = if (total > maxH) maxH.toFloat() / total else 1f
        val outW = max(1, (width * scale).roundToInt())
        total = (total * scale).roundToInt()
        val out = Bitmap.createBitmap(outW, max(1, total), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var y = 0f
        for (b in parts) {
            val dh = b.height * scale
            c.drawBitmap(b, null, Rect(0, y.roundToInt(), outW, min(total, (y + dh).roundToInt())), paint)
            y += dh + gap * scale
        }
        return out
    }
}
