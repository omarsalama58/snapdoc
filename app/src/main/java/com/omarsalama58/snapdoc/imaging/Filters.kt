package com.omarsalama58.snapdoc.imaging

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/** The scan filters (same family as CamScanner's) implemented with OpenCV. */
enum class Filter(val label: String) {
    ORIGINAL("Original"),
    LIGHTEN("Lighten"),
    ENHANCE("Enhance"),
    MAGIC("Magic Pro"),
    NO_SHADOW("No Shadow"),
    NO_HANDWRITING("No Handwriting"),
    BW("B&W"),
    ECO("Eco"),
    GRAY("Grayscale"),
    INVERT("Invert")
}

data class EditParams(
    val filter: Filter = Filter.ORIGINAL,
    val brightness: Float = 0f,   // -100..100
    val contrast: Float = 1f,     // 0.5..2
    val sharpness: Float = 0f     // 0..1
)

object Filters {

    val ready: Boolean by lazy {
        try {
            OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            false
        }
    }

    fun isDefault(p: EditParams) =
        p.filter == Filter.ORIGINAL && p.brightness == 0f && p.contrast == 1f && p.sharpness == 0f

    fun apply(src: Bitmap, p: EditParams): Bitmap {
        if (!ready) return src.copy(Bitmap.Config.ARGB_8888, true)
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(src, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            var out = runFilter(bgr, p.filter)
            if (p.contrast != 1f || p.brightness != 0f) {
                val c = p.contrast.toDouble()
                val adjusted = Mat()
                out.convertTo(adjusted, -1, c, 128.0 * (1.0 - c) + p.brightness)
                out.release()
                out = adjusted
            }
            if (p.sharpness > 0f) {
                val sharp = unsharp(out, 0.2 + 1.3 * p.sharpness, 1.4)
                out.release()
                out = sharp
            }
            val outRgba = Mat()
            Imgproc.cvtColor(out, outRgba, Imgproc.COLOR_BGR2RGBA)
            val bmp = Bitmap.createBitmap(outRgba.cols(), outRgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outRgba, bmp)
            out.release()
            outRgba.release()
            return bmp
        } catch (e: Throwable) {
            return src.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            rgba.release()
            bgr.release()
        }
    }

    // ------------------------------------------------------------------ filters (all return a new BGR Mat)

    private fun runFilter(bgr: Mat, f: Filter): Mat = when (f) {
        Filter.ORIGINAL -> bgr.clone()
        Filter.LIGHTEN -> lighten(bgr)
        Filter.ENHANCE -> enhance(bgr)
        Filter.MAGIC -> magic(bgr)
        Filter.NO_SHADOW -> noShadow(bgr)
        Filter.NO_HANDWRITING -> noHandwriting(bgr)
        Filter.BW -> blackWhite(bgr, 12.0)
        Filter.ECO -> blackWhite(bgr, 24.0)
        Filter.GRAY -> grayscale(bgr)
        Filter.INVERT -> Mat().also { Core.bitwise_not(bgr, it) }
    }

    private fun odd(n: Int) = if (n % 2 == 1) n else n + 1

    /** Per-channel estimate of the paper colour / lighting (ink and text disappear). */
    private fun background(bgr: Mat): Mat {
        val w = bgr.cols()
        val h = bgr.rows()
        val scale = if (max(w, h) > 900) 0.25 else 1.0
        val small = Mat()
        if (scale < 1.0) Imgproc.resize(bgr, small, Size(), scale, scale, Imgproc.INTER_AREA) else bgr.copyTo(small)
        val k = odd(max(9, min(small.cols(), small.rows()) / 14))
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble()))
        val closed = Mat()
        Imgproc.morphologyEx(small, closed, Imgproc.MORPH_CLOSE, kernel)
        val med = Mat()
        Imgproc.medianBlur(closed, med, odd(max(5, k / 2)))
        val blur = Mat()
        Imgproc.GaussianBlur(med, blur, Size(0.0, 0.0), max(3.0, k / 3.0))
        val bg = Mat()
        if (scale < 1.0) Imgproc.resize(blur, bg, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        else blur.copyTo(bg)
        small.release(); closed.release(); med.release(); blur.release(); kernel.release()
        return bg
    }

    private fun divideBy(bgr: Mat, bg: Mat, scale: Double): Mat {
        val a = Mat()
        val b = Mat()
        bgr.convertTo(a, CvType.CV_32F)
        bg.convertTo(b, CvType.CV_32F)
        Core.max(b, Scalar(25.0, 25.0, 25.0), b)
        val q = Mat()
        Core.divide(a, b, q, scale)
        val out = Mat()
        q.convertTo(out, CvType.CV_8U)
        a.release(); b.release(); q.release()
        return out
    }

    private fun noShadow(bgr: Mat): Mat {
        val bg = background(bgr)
        val out = divideBy(bgr, bg, 245.0)
        bg.release()
        return out
    }

    private fun lighten(bgr: Mat): Mat {
        val lut = Mat(1, 256, CvType.CV_8U)
        val data = ByteArray(256) { i ->
            val v = Math.pow(i / 255.0, 0.72) * 1.06 * 255.0
            min(255.0, v).toInt().toByte()
        }
        lut.put(0, 0, data)
        val out = Mat()
        Core.LUT(bgr, lut, out)
        lut.release()
        return out
    }

    private fun levels(src: Mat, lowPct: Double, highPct: Double): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        val small = Mat()
        Imgproc.resize(gray, small, Size(160.0, 160.0), 0.0, 0.0, Imgproc.INTER_AREA)
        val buf = ByteArray(160 * 160)
        small.get(0, 0, buf)
        val hist = IntArray(256)
        for (b in buf) hist[b.toInt() and 255]++
        fun pct(p: Double): Int {
            val target = p / 100.0 * buf.size
            var acc = 0
            for (i in 0..255) { acc += hist[i]; if (acc >= target) return i }
            return 255
        }
        val a = pct(lowPct).toDouble()
        val b = max(a + 1, pct(highPct).toDouble())
        val out = Mat()
        src.convertTo(out, CvType.CV_8U, 255.0 / (b - a), -a * 255.0 / (b - a))
        gray.release(); small.release()
        return out
    }

    private fun saturate(src: Mat, k: Double): Mat {
        val hsv = Mat()
        Imgproc.cvtColor(src, hsv, Imgproc.COLOR_BGR2HSV)
        val ch = ArrayList<Mat>()
        Core.split(hsv, ch)
        val s = Mat()
        ch[1].convertTo(s, CvType.CV_8U, k)
        ch[1].release()
        ch[1] = s
        Core.merge(ch, hsv)
        val out = Mat()
        Imgproc.cvtColor(hsv, out, Imgproc.COLOR_HSV2BGR)
        ch.forEach { it.release() }
        hsv.release()
        return out
    }

    private fun unsharp(src: Mat, amount: Double, sigma: Double): Mat {
        val blur = Mat()
        Imgproc.GaussianBlur(src, blur, Size(0.0, 0.0), sigma)
        val out = Mat()
        Core.addWeighted(src, 1 + amount, blur, -amount, 0.0, out)
        blur.release()
        return out
    }

    private fun enhance(bgr: Mat): Mat {
        val bg = background(bgr)
        // half-strength flattening: blend the estimated paper colour with plain white
        val white = Mat(bg.size(), bg.type(), Scalar(255.0, 255.0, 255.0))
        val mixed = Mat()
        Core.addWeighted(bg, 0.5, white, 0.5, 0.0, mixed)
        val flat = divideBy(bgr, mixed, 240.0)
        val lv = levels(flat, 1.0, 99.0)
        val out = saturate(lv, 1.1)
        bg.release(); white.release(); mixed.release(); flat.release(); lv.release()
        return out
    }

    private fun magic(bgr: Mat): Mat {
        val flat = noShadow(bgr)
        val lab = Mat()
        Imgproc.cvtColor(flat, lab, Imgproc.COLOR_BGR2Lab)
        val ch = ArrayList<Mat>()
        Core.split(lab, ch)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val l2 = Mat()
        clahe.apply(ch[0], l2)
        ch[0].release()
        ch[0] = l2
        Core.merge(ch, lab)
        val back = Mat()
        Imgproc.cvtColor(lab, back, Imgproc.COLOR_Lab2BGR)
        val lv = levels(back, 1.0, 99.0)
        val sat = saturate(lv, 1.2)
        val out = unsharp(sat, 0.7, 1.6)
        flat.release(); lab.release(); ch.forEach { it.release() }; back.release(); lv.release(); sat.release()
        return out
    }

    private fun noHandwriting(bgr: Mat): Mat {
        val flat = noShadow(bgr)
        val hsv = Mat()
        Imgproc.cvtColor(flat, hsv, Imgproc.COLOR_BGR2HSV)
        val ink = Mat()
        Core.inRange(hsv, Scalar(0.0, 70.0, 0.0), Scalar(180.0, 255.0, 234.0), ink)
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.dilate(ink, ink, k)
        val gray = Mat()
        Imgproc.cvtColor(flat, gray, Imgproc.COLOR_BGR2GRAY)
        gray.setTo(Scalar(250.0), ink)
        val g3 = Mat()
        Imgproc.cvtColor(gray, g3, Imgproc.COLOR_GRAY2BGR)
        val out = levels(g3, 2.0, 98.0)
        flat.release(); hsv.release(); ink.release(); k.release(); gray.release(); g3.release()
        return out
    }

    private fun blackWhite(bgr: Mat, c: Double): Mat {
        val flat = noShadow(bgr)
        val gray = Mat()
        Imgproc.cvtColor(flat, gray, Imgproc.COLOR_BGR2GRAY)
        val bs = odd(max(21, min(gray.cols(), gray.rows()) / 18))
        val t = Mat()
        Imgproc.adaptiveThreshold(gray, t, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, bs, c)
        val m = Mat()
        Imgproc.medianBlur(t, m, 3)
        val out = Mat()
        Imgproc.cvtColor(m, out, Imgproc.COLOR_GRAY2BGR)
        flat.release(); gray.release(); t.release(); m.release()
        return out
    }

    private fun grayscale(bgr: Mat): Mat {
        val g = Mat()
        Imgproc.cvtColor(bgr, g, Imgproc.COLOR_BGR2GRAY)
        val out = Mat()
        Imgproc.cvtColor(g, out, Imgproc.COLOR_GRAY2BGR)
        g.release()
        return out
    }
}
