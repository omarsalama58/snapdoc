package com.omarsalama58.snapdoc.imaging

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds the four corners of a document (or ID card) in a photo.
 *
 * Several different segmentations each propose a quadrilateral (Canny edges, colour distance from the
 * photo border, Otsu on brightness). Every proposal is snapped onto the nearest real edges and scored by
 * how strongly the colour changes across its four sides. The outermost well-supported one wins.
 * Tuned and checked offline against a real ID-card photo and synthetic scans.
 */
object EdgeDetector {

    private val ready: Boolean by lazy {
        try {
            OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------------ small helpers

    private class LabImg(val w: Int, val h: Int, val d: FloatArray) {
        fun sample(x: Double, y: Double, out: FloatArray) {
            val xx = x.coerceIn(0.0, (w - 1).toDouble())
            val yy = y.coerceIn(0.0, (h - 1).toDouble())
            val x0 = xx.toInt()
            val y0 = yy.toInt()
            val x1 = min(x0 + 1, w - 1)
            val y1 = min(y0 + 1, h - 1)
            val fx = (xx - x0).toFloat()
            val fy = (yy - y0).toFloat()
            for (c in 0..2) {
                val a = d[(y0 * w + x0) * 3 + c]
                val b = d[(y0 * w + x1) * 3 + c]
                val cc = d[(y1 * w + x0) * 3 + c]
                val dd = d[(y1 * w + x1) * 3 + c]
                out[c] = (a * (1 - fx) + b * fx) * (1 - fy) + (cc * (1 - fx) + dd * fx) * fy
            }
        }
    }

    /** Clockwise TL, TR, BR, BL as [x0,y0,x1,y1,...]. */
    private fun order(q: DoubleArray): DoubleArray {
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4
        val idx = (0..3).sortedBy { atan2(q[2 * it + 1] - cy, q[2 * it] - cx) }
        var start = 0
        var best = Double.MAX_VALUE
        for (k in 0..3) {
            val s = q[2 * idx[k]] + q[2 * idx[k] + 1]
            if (s < best) { best = s; start = k }
        }
        val out = DoubleArray(8)
        for (k in 0..3) {
            val i = idx[(start + k) % 4]
            out[2 * k] = q[2 * i]
            out[2 * k + 1] = q[2 * i + 1]
        }
        return out
    }

    private fun area(q: DoubleArray): Double {
        var a = 0.0
        for (i in 0..3) {
            val j = (i + 1) % 4
            a += q[2 * i] * q[2 * j + 1] - q[2 * j] * q[2 * i + 1]
        }
        return abs(a) / 2
    }

    private fun rectness(q: DoubleArray): Double {
        var m = 0.0
        for (i in 0..3) {
            val p = (i + 3) % 4
            val n = (i + 1) % 4
            val ax = q[2 * i] - q[2 * p]
            val ay = q[2 * i + 1] - q[2 * p + 1]
            val bx = q[2 * n] - q[2 * i]
            val by = q[2 * n + 1] - q[2 * i + 1]
            val c = abs(ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by) + 1e-6)
            if (c > m) m = c
        }
        return max(0.0, 1 - m * 1.2)
    }

    private fun percentile(v: DoubleArray, p: Double): Double {
        if (v.isEmpty()) return 0.0
        val s = v.sortedArray()
        return s[((s.size - 1) * p).toInt()]
    }

    // ------------------------------------------------------------------ edge scoring

    /** Average colour change across a side placed at offset [o] (outward) and rotated by [ang] degrees. */
    private fun sideDiff(
        img: LabImg, ax: Double, ay: Double, bx: Double, by: Double,
        nx: Double, ny: Double, o: Double, ang: Double
    ): Double {
        val len = hypot(bx - ax, by - ay)
        if (len < 4) return 0.0
        val mx = (ax + bx) / 2
        val my = (ay + by) / 2
        val dx = (bx - ax) / len
        val dy = (by - ay) / len
        val th = Math.toRadians(ang)
        val c = cos(th)
        val s = sin(th)
        val d2x = c * dx - s * dy
        val d2y = s * dx + c * dy
        var n2x = -d2y
        var n2y = d2x
        if (n2x * nx + n2y * ny < 0) { n2x = -n2x; n2y = -n2y }
        val count = max(24, (len / 3).toInt())
        val diffs = DoubleArray(count)
        val pin = FloatArray(3)
        val pout = FloatArray(3)
        val baseX = mx + n2x * o
        val baseY = my + n2y * o
        for (i in 0 until count) {
            val t = (-0.45 + 0.9 * i / (count - 1)) * len
            val px = baseX + d2x * t
            val py = baseY + d2y * t
            img.sample(px - n2x * 3, py - n2y * 3, pin)
            img.sample(px + n2x * 3, py + n2y * 3, pout)
            val dl = (pout[0] - pin[0]).toDouble()
            val da = (pout[1] - pin[1]).toDouble()
            val db = (pout[2] - pin[2]).toDouble()
            diffs[i] = min(sqrt(dl * dl + da * da + db * db), 40.0)
        }
        return 0.5 * diffs.average() + 0.5 * percentile(diffs, 0.25)
    }

    private fun outwardNormal(q: DoubleArray, i: Int): DoubleArray {
        val n = (i + 1) % 4
        val dx = q[2 * n] - q[2 * i]
        val dy = q[2 * n + 1] - q[2 * i + 1]
        var nx = -dy
        var ny = dx
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4
        val mx = (q[2 * i] + q[2 * n]) / 2
        val my = (q[2 * i + 1] + q[2 * n + 1]) / 2
        if (nx * (mx - cx) + ny * (my - cy) < 0) { nx = -nx; ny = -ny }
        return doubleArrayOf(nx, ny)
    }

    private fun sideScores(q: DoubleArray, img: LabImg): DoubleArray = DoubleArray(4) { i ->
        val n = (i + 1) % 4
        val nv = outwardNormal(q, i)
        val v = sideDiff(img, q[2 * i], q[2 * i + 1], q[2 * n], q[2 * n + 1], nv[0], nv[1], 0.0, 0.0)
        val ax = q[2 * i]; val ay = q[2 * i + 1]; val bx = q[2 * n]; val by = q[2 * n + 1]
        val onBorder = (ax < 4 && bx < 4) || (ay < 4 && by < 4) ||
            (ax > img.w - 5 && bx > img.w - 5) || (ay > img.h - 5 && by > img.h - 5)
        // a side lying on the picture edge has no outside to compare with: give it a fair credit
        if (onBorder) max(v, 12.0) else v
    }

    /** If a strip between a side and the picture edge is part of the document, stretch the side to the edge. */
    private fun extendToBorder(q0: DoubleArray, img: LabImg): DoubleArray {
        var q = q0.copyOf()
        val w = img.w
        val h = img.h
        fun band(x0: Double, x1: Double, y0: Double, y1: Double): DoubleArray? {
            val a = max(0, x0.toInt()); val b = min(w, x1.toInt())
            val c = max(0, y0.toInt()); val d = min(h, y1.toInt())
            if (b - a < 2 || d - c < 2) return null
            val sum = DoubleArray(3)
            var n = 0
            for (y in c until d) for (x in a until b) {
                for (k in 0..2) sum[k] += img.d[(y * w + x) * 3 + k].toDouble()
                n++
            }
            return DoubleArray(3) { sum[it] / n }
        }
        fun dist(a: DoubleArray, b: DoubleArray) =
            Math.sqrt((0..2).sumOf { (a[it] - b[it]) * (a[it] - b[it]) })
        val xs = doubleArrayOf(q[0], q[2], q[4], q[6])
        val ys = doubleArrayOf(q[1], q[3], q[5], q[7])
        val l = min(xs[0], xs[3]); val r = max(xs[1], xs[2])
        val t = min(ys[0], ys[1]); val b = max(ys[2], ys[3])
        val mx0 = l + 0.1 * (r - l); val mx1 = r - 0.1 * (r - l)
        val my0 = t + 0.1 * (b - t); val my1 = b - 0.1 * (b - t)
        val inner = band(l + 0.2 * (r - l), r - 0.2 * (r - l), t + 0.2 * (b - t), b - 0.2 * (b - t)) ?: return q
        val refs = HashMap<Char, DoubleArray>()
        if (l > 0.08 * w) band(l - 12, l - 3, my0, my1)?.let { refs['l'] = it }
        if (w - r > 0.08 * w) band(r + 3, r + 12, my0, my1)?.let { refs['r'] = it }
        if (t > 0.08 * h) band(mx0, mx1, t - 12, t - 3)?.let { refs['t'] = it }
        if (h - b > 0.08 * h) band(mx0, mx1, b + 3, b + 12)?.let { refs['b'] = it }
        fun decide(side: Char, g: DoubleArray?): Boolean {
            if (g == null) return false
            val others = refs.filter { it.key != side }.values.toList()
            if (others.isEmpty()) return false
            val bg = DoubleArray(3) { k ->
                val v = others.map { it[k] }.sorted()
                if (v.size % 2 == 1) v[v.size / 2] else 0.5 * (v[v.size / 2 - 1] + v[v.size / 2])
            }
            val db = dist(g, bg)
            val di = dist(g, inner)
            return db > 9 && di < db
        }
        val maxGap = 0.2
        if (5 < l && max(xs[0], xs[3]) < maxGap * w && decide('l', band(0.0, l - 1, my0, my1))) { q[0] = 0.0; q[6] = 0.0 }
        if (5 < w - 1 - r && w - 1 - min(xs[1], xs[2]) < maxGap * w && decide('r', band(r + 1, w.toDouble(), my0, my1))) { q[2] = (w - 1).toDouble(); q[4] = (w - 1).toDouble() }
        if (3 < t && max(ys[0], ys[1]) < maxGap * h && decide('t', band(mx0, mx1, 0.0, t - 1))) { q[1] = 0.0; q[3] = 0.0 }
        if (5 < h - 1 - b && h - 1 - min(ys[2], ys[3]) < maxGap * h && decide('b', band(mx0, mx1, b + 1, h.toDouble()))) { q[5] = (h - 1).toDouble(); q[7] = (h - 1).toDouble() }

        // Cards and A4 pages have well known proportions: stretch a side to the edge when that makes it match.
        fun std(a: Double, c: Double): Double {
            val ratio = max(a, c) / max(1e-6, min(a, c))
            return min(abs(ratio / 1.585 - 1), abs(ratio / 1.414 - 1))
        }
        fun aspectOk(n: DoubleArray): Boolean {
            val a = hypot(n[2] - n[0], n[3] - n[1]); val c = hypot(n[6] - n[0], n[7] - n[1])
            val o = hypot(q[2] - q[0], q[3] - q[1]); val p = hypot(q[6] - q[0], q[7] - q[1])
            return std(a, c) < 0.06 && std(o, p) >= 0.06
        }
        for (side in "lrtb") {
            val nq = q.copyOf()
            when {
                side == 'l' && 5 < min(q[0], q[6]) && min(q[0], q[6]) < maxGap * w -> { nq[0] = 0.0; nq[6] = 0.0 }
                side == 'r' && 5 < w - 1 - max(q[2], q[4]) && w - 1 - max(q[2], q[4]) < maxGap * w -> { nq[2] = (w - 1).toDouble(); nq[4] = (w - 1).toDouble() }
                side == 't' && 3 < min(q[1], q[3]) && min(q[1], q[3]) < maxGap * h -> { nq[1] = 0.0; nq[3] = 0.0 }
                side == 'b' && 5 < h - 1 - max(q[5], q[7]) && h - 1 - max(q[5], q[7]) < maxGap * h -> { nq[5] = (h - 1).toDouble(); nq[7] = (h - 1).toDouble() }
                else -> continue
            }
            if (aspectOk(nq)) q = nq
        }
        return q
    }

    private fun support(q: DoubleArray, img: LabImg): Double {
        val s = sideScores(q, img)
        return 0.5 * s.average() + 0.5 * s.min()
    }

    private fun weak2(q: DoubleArray, img: LabImg): Double {
        val s = sideScores(q, img).sorted()
        return 0.5 * (s[0] + s[1])
    }

    // ------------------------------------------------------------------ snapping to real edges

    private fun refine(
        q0: DoubleArray, img: LabImg, w: Int, h: Int,
        lo: Double, hi: Double, angs: DoubleArray
    ): DoubleArray {
        val q = order(q0)
        val lineP = Array(4) { DoubleArray(2) }
        val lineD = Array(4) { DoubleArray(2) }
        for (i in 0..3) {
            val n = (i + 1) % 4
            val ax = q[2 * i]; val ay = q[2 * i + 1]
            val bx = q[2 * n]; val by = q[2 * n + 1]
            val len = hypot(bx - ax, by - ay)
            if (len < 4) return q
            val dx = (bx - ax) / len
            val dy = (by - ay) / len
            val nv = outwardNormal(q, i)
            val nl = hypot(nv[0], nv[1])
            val nx = nv[0] / nl
            val ny = nv[1] / nl
            val opp = (i + 2) % 4
            val span = hypot(q[2 * opp] - q[2 * n], q[2 * opp + 1] - q[2 * n + 1])
            var bestScore = -1.0
            var bestO = 0.0
            var bestA = 0.0
            var o = lo * span
            while (o < hi * span) {
                for (ang in angs) {
                    var sc = sideDiff(img, ax, ay, bx, by, nx, ny, o, ang)
                    if (o < 0) sc *= 1 - 0.15 * min(1.0, -o / (abs(lo) * span + 1e-6))
                    if (sc > bestScore) { bestScore = sc; bestO = o; bestA = ang }
                }
                o += 1.0
            }
            val th = Math.toRadians(bestA)
            val c = cos(th)
            val s = sin(th)
            val d2x = c * dx - s * dy
            val d2y = s * dx + c * dy
            var n2x = -d2y
            var n2y = d2x
            if (n2x * nx + n2y * ny < 0) { n2x = -n2x; n2y = -n2y }
            lineP[i][0] = (ax + bx) / 2 + n2x * bestO
            lineP[i][1] = (ay + by) / 2 + n2y * bestO
            lineD[i][0] = d2x
            lineD[i][1] = d2y
        }
        val out = DoubleArray(8)
        for (i in 0..3) {
            val p = (i + 3) % 4
            // intersection of line p and line i
            val a11 = lineD[p][0]; val a12 = -lineD[i][0]
            val a21 = lineD[p][1]; val a22 = -lineD[i][1]
            val det = a11 * a22 - a12 * a21
            if (abs(det) < 1e-6) return q
            val rx = lineP[i][0] - lineP[p][0]
            val ry = lineP[i][1] - lineP[p][1]
            val t = (rx * a22 - a12 * ry) / det
            out[2 * i] = (lineP[p][0] + lineD[p][0] * t).coerceIn(0.0, (w - 1).toDouble())
            out[2 * i + 1] = (lineP[p][1] + lineD[p][1] * t).coerceIn(0.0, (h - 1).toDouble())
        }
        val a0 = area(q)
        val a1 = area(out)
        if (a0 < 1 || a1 < 0.8 * a0 || a1 > 1.8 * a0) return q
        return order(out)
    }

    // ------------------------------------------------------------------ candidate generators

    private fun quadOfContour(c: MatOfPoint): DoubleArray {
        val hullIdx = MatOfInt()
        Imgproc.convexHull(c, hullIdx)
        val pts = c.toArray()
        val hp = hullIdx.toArray().map { pts[it] }
        hullIdx.release()
        val hull = MatOfPoint2f(*hp.toTypedArray())
        val peri = Imgproc.arcLength(hull, true)
        var result: DoubleArray? = null
        for (eps in doubleArrayOf(0.015, 0.02, 0.03, 0.04, 0.06, 0.08, 0.1)) {
            val ap = MatOfPoint2f()
            Imgproc.approxPolyDP(hull, ap, eps * peri, true)
            val n = ap.rows()
            val arr = ap.toArray()
            ap.release()
            if (n == 4) {
                result = DoubleArray(8) { if (it % 2 == 0) arr[it / 2].x else arr[it / 2].y }
                break
            }
            if (n < 4) break
        }
        if (result == null) {
            val rect = Imgproc.minAreaRect(hull)
            val corners = Array(4) { Point() }
            rect.points(corners)
            result = DoubleArray(8) { if (it % 2 == 0) corners[it / 2].x else corners[it / 2].y }
        }
        hull.release()
        return result
    }

    private fun fillHoles(m: Mat) {
        if (m.get(0, 0)[0] != 0.0) return
        val ff = m.clone()
        val mask = Mat.zeros(m.rows() + 2, m.cols() + 2, CvType.CV_8UC1)
        Imgproc.floodFill(ff, mask, Point(0.0, 0.0), Scalar(255.0))
        Core.bitwise_not(ff, ff)
        Core.bitwise_or(m, ff, m)
        ff.release()
        mask.release()
    }

    private fun largestQuad(mask: Mat, minArea: Double): DoubleArray? {
        val contours = ArrayList<MatOfPoint>()
        val hier = Mat()
        Imgproc.findContours(mask, contours, hier, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hier.release()
        var best: MatOfPoint? = null
        var bestA = 0.0
        for (c in contours) {
            val a = Imgproc.contourArea(c)
            if (a > bestA) { bestA = a; best = c }
        }
        val out = if (best != null && bestA >= minArea) quadOfContour(best) else null
        contours.forEach { it.release() }
        return out
    }

    private fun cannyCandidates(blurBgr: Mat, w: Int, h: Int, track: (Mat) -> Mat): List<DoubleArray> {
        val chans = ArrayList<Mat>()
        val parts = ArrayList<Mat>()
        Core.split(blurBgr, parts)
        parts.forEach { chans.add(track(it)) }
        val gray = track(Mat())
        Imgproc.cvtColor(blurBgr, gray, Imgproc.COLOR_BGR2GRAY)
        chans.add(gray)
        val lab = track(Mat())
        Imgproc.cvtColor(blurBgr, lab, Imgproc.COLOR_BGR2Lab)
        val labParts = ArrayList<Mat>()
        Core.split(lab, labParts)
        labParts.forEach { track(it) }
        chans.add(labParts[1])
        chans.add(labParts[2])

        val out = ArrayList<DoubleArray>()
        val k3 = track(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        val k7 = track(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0)))
        for ((lo, hi) in listOf(14.0 to 42.0, 30.0 to 90.0)) {
            val edges = track(Mat.zeros(h, w, CvType.CV_8UC1))
            val tmp = track(Mat())
            for (c in chans) {
                Imgproc.Canny(c, tmp, lo, hi)
                Core.bitwise_or(edges, tmp, edges)
            }
            Imgproc.dilate(edges, edges, k3, Point(-1.0, -1.0), 2)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, k7)
            val contours = ArrayList<MatOfPoint>()
            val hier = track(Mat())
            Imgproc.findContours(edges, contours, hier, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val top = contours.sortedByDescending { Imgproc.contourArea(it) }.take(6)
            for (c in top) {
                if (Imgproc.contourArea(c) >= 0.12 * w * h) out.add(quadOfContour(c))
            }
            contours.forEach { it.release() }
        }
        return out
    }

    /** Colour distance of every pixel from the colour of the photo's border ring. */
    private fun distanceMap(labImg: LabImg, sides: IntArray): FloatArray {
        val w = labImg.w
        val h = labImg.h
        val b = max(3, (0.05 * min(w, h)).toInt())
        val ring = ArrayList<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            val inRing = (0 in sides && y < b) || (2 in sides && y >= h - b) || (3 in sides && x < b) || (1 in sides && x >= w - b)
            if (inRing) ring.add(y * w + x)
        }
        var sample = ring.toIntArray()
        var mu = DoubleArray(3)
        var inv = Array(3) { DoubleArray(3) }
        fun stats(idx: IntArray) {
            mu = DoubleArray(3)
            for (p in idx) for (c in 0..2) mu[c] += labImg.d[p * 3 + c].toDouble()
            for (c in 0..2) mu[c] /= idx.size
            val cov = Array(3) { DoubleArray(3) }
            for (p in idx) {
                for (i in 0..2) for (j in 0..2) {
                    cov[i][j] += (labImg.d[p * 3 + i] - mu[i]) * (labImg.d[p * 3 + j] - mu[j])
                }
            }
            for (i in 0..2) for (j in 0..2) cov[i][j] = cov[i][j] / (idx.size - 1) + if (i == j) 4.0 else 0.0
            inv = invert3(cov)
        }
        fun dist2(p: Int): Double {
            val v = DoubleArray(3) { labImg.d[p * 3 + it] - mu[it] }
            var s = 0.0
            for (i in 0..2) for (j in 0..2) s += v[i] * inv[i][j] * v[j]
            return s
        }
        for (iter in 0 until 3) {
            stats(sample)
            val dd = DoubleArray(sample.size) { dist2(sample[it]) }
            val cut = percentile(dd, 0.85)
            sample = sample.filterIndexed { i, _ -> dd[i] < cut }.toIntArray()
            if (sample.size < 20) break
        }
        stats(sample)
        return FloatArray(w * h) { sqrt(max(0.0, dist2(it))).toFloat() }
    }

    private fun invert3(m: Array<DoubleArray>): Array<DoubleArray> {
        val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
        val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
        val g = m[2][0]; val hh = m[2][1]; val i = m[2][2]
        val det = a * (e * i - f * hh) - b * (d * i - f * g) + c * (d * hh - e * g)
        val id = if (abs(det) < 1e-9) 1.0 else 1.0 / det
        return arrayOf(
            doubleArrayOf((e * i - f * hh) * id, (c * hh - b * i) * id, (b * f - c * e) * id),
            doubleArrayOf((f * g - d * i) * id, (a * i - c * g) * id, (c * d - a * f) * id),
            doubleArrayOf((d * hh - e * g) * id, (b * g - a * hh) * id, (a * e - b * d) * id)
        )
    }

    private val sideSets = arrayOf(
        intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3), intArrayOf(0, 2, 3), intArrayOf(0, 1, 3), intArrayOf(0, 1, 2)
    )

    private fun colorCandidates(labImg: LabImg, track: (Mat) -> Mat): List<DoubleArray> {
        val w = labImg.w
        val h = labImg.h
        val k = track(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0)))
        val out = ArrayList<DoubleArray>()
        for ((si, ss) in sideSets.withIndex()) {
            val dist = distanceMap(labImg, ss)
            val distMat = track(Mat(h, w, CvType.CV_32FC1))
            distMat.put(0, 0, dist)
            val ths = if (si == 0) doubleArrayOf(3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 14.0) else doubleArrayOf(4.0, 6.0, 8.0, 12.0)
            for (th in ths) {
                val m = track(Mat())
                Core.compare(distMat, Scalar(th), m, Core.CMP_GT)
                Imgproc.morphologyEx(m, m, Imgproc.MORPH_CLOSE, k)
                Imgproc.morphologyEx(m, m, Imgproc.MORPH_OPEN, k)
                fillHoles(m)
                largestQuad(m, 0.10 * w * h)?.let { out.add(it) }
            }
        }
        return out
    }

    /** Axis-aligned seeds: scan inward from every picture side and take the OUTERMOST clear edge. */
    private fun sweepCandidates(img: LabImg): List<DoubleArray> {
        val w = img.w.toDouble()
        val h = img.h.toDouble()
        fun prof(side: Int, o: Double): Double = when (side) {
            0 -> sideDiff(img, 0.1 * w, o, 0.9 * w, o, 0.0, -1.0, 0.0, 0.0)
            2 -> sideDiff(img, 0.9 * w, h - 1 - o, 0.1 * w, h - 1 - o, 0.0, 1.0, 0.0, 0.0)
            1 -> sideDiff(img, w - 1 - o, 0.1 * h, w - 1 - o, 0.9 * h, 1.0, 0.0, 0.0, 0.0)
            else -> sideDiff(img, o, 0.9 * h, o, 0.1 * h, -1.0, 0.0, 0.0, 0.0)
        }
        val ratios = doubleArrayOf(0.35, 0.55)
        val pos = Array(4) { IntArray(2) }
        for (side in 0..3) {
            val span = if (side == 0 || side == 2) h else w
            val offs = ArrayList<Int>()
            var o = 3
            while (o < (0.55 * span).toInt()) { offs.add(o); o += 2 }
            if (offs.size < 3) return emptyList()
            val p = DoubleArray(offs.size) { prof(side, offs[it].toDouble()) }
            val mx = p.max()
            for (r in 0..1) {
                val thr = max(7.0, ratios[r] * mx)
                var pick = 0
                for (k in 1 until offs.size - 1) {
                    if (p[k] >= thr && p[k] >= p[k - 1] && p[k] >= p[k + 1]) { pick = offs[k]; break }
                }
                pos[side][r] = pick
            }
        }
        val out = ArrayList<DoubleArray>()
        val seen = HashSet<List<Int>>()
        for (r in 0..1) {
            val t = pos[0][r].toDouble(); val rt = pos[1][r].toDouble()
            val b = pos[2][r].toDouble(); val l = pos[3][r].toDouble()
            if (!seen.add(listOf(pos[0][r], pos[1][r], pos[2][r], pos[3][r]))) continue
            out.add(doubleArrayOf(l, t, w - 1 - rt, t, w - 1 - rt, h - 1 - b, l, h - 1 - b))
        }
        return out
    }

    private fun otsuCandidates(blurBgr: Mat, w: Int, h: Int, track: (Mat) -> Mat): List<DoubleArray> {
        val gray = track(Mat())
        Imgproc.cvtColor(blurBgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.blur(gray, gray, Size(5.0, 5.0))
        val k = track(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0)))
        val out = ArrayList<DoubleArray>()
        for (type in intArrayOf(Imgproc.THRESH_BINARY, Imgproc.THRESH_BINARY_INV)) {
            val m = track(Mat())
            Imgproc.threshold(gray, m, 0.0, 255.0, type + Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(m, m, Imgproc.MORPH_CLOSE, k)
            Imgproc.morphologyEx(m, m, Imgproc.MORPH_OPEN, k)
            fillHoles(m)
            largestQuad(m, 0.10 * w * h)?.let { out.add(it) }
        }
        return out
    }

    // ------------------------------------------------------------------ main

    private fun insideConvex(q: DoubleArray, px: Double, py: Double): Boolean {
        var pos = 0; var neg = 0
        for (i in 0..3) {
            val j = (i + 1) % 4
            val cr = (q[2 * j] - q[2 * i]) * (py - q[2 * i + 1]) - (q[2 * j + 1] - q[2 * i + 1]) * (px - q[2 * i])
            if (cr > 0) pos++ else if (cr < 0) neg++
        }
        return pos == 0 || neg == 0
    }

    private class Eval(
        val q: DoubleArray, val area: Double, val sup: Double, val rect: Double,
        val touch: Int, val score: Double, val weak2: Double
    )

    private fun contains(outer: DoubleArray, inner: DoubleArray, tol: Double): Boolean {
        for (k in 0..3) {
            val px = inner[2 * k]
            val py = inner[2 * k + 1]
            var minD = Double.MAX_VALUE
            for (i in 0..3) {
                val n = (i + 1) % 4
                val ax = outer[2 * i]; val ay = outer[2 * i + 1]
                val bx = outer[2 * n]; val by = outer[2 * n + 1]
                val len = hypot(bx - ax, by - ay) + 1e-9
                val d = ((bx - ax) * (py - ay) - (by - ay) * (px - ax)) / len
                if (d < minD) minD = d
            }
            if (minD < -tol) return false
        }
        return true
    }

    /** Returns normalized TL, TR, BR, BL (8 values) or null when nothing reliable was found. */
    fun detect(src: Bitmap): FloatArray? {
        if (!ready) return null
        val toRelease = ArrayList<Mat>()
        fun track(m: Mat): Mat { toRelease.add(m); return m }
        try {
            val sc = 400f / max(src.width, src.height)
            val w = max(16, (src.width * sc).roundToInt())
            val h = max(16, (src.height * sc).roundToInt())
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            val rgba = track(Mat())
            Utils.bitmapToMat(small, rgba)
            val bgr = track(Mat())
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)

            val blurBgr = track(Mat())
            Imgproc.GaussianBlur(bgr, blurBgr, Size(5.0, 5.0), 0.0)
            val labMat = track(Mat())
            Imgproc.cvtColor(blurBgr, labMat, Imgproc.COLOR_BGR2Lab)
            val lab32 = track(Mat())
            labMat.convertTo(lab32, CvType.CV_32F)
            val arr = FloatArray(w * h * 3)
            lab32.get(0, 0, arr)
            val labImg = LabImg(w, h, arr)

            val raw = ArrayList<DoubleArray>()
            raw.addAll(cannyCandidates(blurBgr, w, h, { track(it) }))
            raw.addAll(colorCandidates(labImg, { track(it) }))
            raw.addAll(otsuCandidates(blurBgr, w, h, { track(it) }))
            raw.addAll(sweepCandidates(labImg))

            val imgArea = w.toDouble() * h
            val tol = 0.04 * max(w, h)
            val seen = HashSet<List<Int>>()
            val uniq = ArrayList<DoubleArray>()
            for (r in raw) {
                val q = order(r)
                val qa = area(q)
                if (qa < 0.10 * imgArea || qa > 0.985 * imgArea) continue
                val key = q.map { (it / tol).roundToInt() }
                if (seen.add(key)) uniq.add(q)
            }
            if (uniq.isEmpty()) return null

            val pre = uniq.map { q -> Pair(support(q, labImg) * (0.5 + 0.5 * rectness(q)), q) }
                .sortedByDescending { it.first }
            val shortlist = ArrayList<DoubleArray>()
            pre.take(5).forEach { shortlist.add(it.second) }
            pre.drop(5).map { it.second }.filter { rectness(it) >= 0.6 }
                .sortedByDescending { area(it) }.take(3).forEach { shortlist.add(it) }

            val evals = ArrayList<Eval>()
            for (q in shortlist) {
                val r1 = refine(q, labImg, w, h, -0.05, 0.18, doubleArrayOf(-9.0, -6.0, -3.0, 0.0, 3.0, 6.0, 9.0))
                val r2 = refine(r1, labImg, w, h, -0.04, 0.06, doubleArrayOf(-3.0, -1.5, 0.0, 1.5, 3.0))
                for (qq in listOf(q, r2)) {
                    val qa = area(qq)
                    if (qa < 0.12 * imgArea || qa > 0.96 * imgArea) continue
                    var touch = 0
                    for (k in 0..3) {
                        val x = qq[2 * k]
                        val y = qq[2 * k + 1]
                        if (x < 3 || y < 3 || x > w - 4 || y > h - 4) touch++
                    }
                    val sup = support(qq, labImg)
                    val rc = rectness(qq)
                    val score = sup * (0.5 + 0.5 * rc) * Math.pow(qa / imgArea, 0.5) * (if (touch >= 2) 0.6 else 1.0)
                    evals.add(Eval(qq, qa, sup, rc, touch, score, weak2(qq, labImg)))
                }
            }
            if (evals.isEmpty()) return null
            var best = evals.maxByOrNull { it.score }!!
            for (iter in 0 until 3) {
                // prefer the outermost real boundary (e.g. a page around a block of text)
                val enc = evals.filter {
                    it.area > best.area * 1.12 && it.touch < 2 && it.rect >= 0.6 &&
                        it.weak2 >= 0.7 * best.weak2 && contains(it.q, best.q, tol * 0.75)
                }
                if (enc.isEmpty()) break
                best = enc.maxByOrNull { it.score }!!
            }
            // band check: if what lies outside a strong inner quad is not paper-like (cloth, table), keep the inner quad
            for (iter in 0 until 3) {
                val cur = best
                val cands = evals.filter {
                    it !== cur && it.area < cur.area * 0.92 && it.area >= 0.5 * cur.area &&
                        it.rect >= 0.85 && it.sup >= 0.85 * cur.sup && contains(cur.q, it.q, 0.03 * max(w, h))
                }.sortedByDescending { it.area }
                var sw: Eval? = null
                for (e in cands) {
                    val sb = DoubleArray(3); val si = DoubleArray(3)
                    var nb = 0; var ni = 0; var no = 0
                    for (y in 0 until h) for (x in 0 until w) {
                        if (!insideConvex(cur.q, x + 0.5, y + 0.5)) continue
                        no++
                        val k = (y * w + x) * 3
                        if (insideConvex(e.q, x + 0.5, y + 0.5)) {
                            ni++; for (c in 0..2) si[c] += labImg.d[k + c].toDouble()
                        } else {
                            nb++; for (c in 0..2) sb[c] += labImg.d[k + c].toDouble()
                        }
                    }
                    if (nb < 0.10 * no || ni == 0) continue
                    var d2 = 0.0
                    for (c in 0..2) { val dd = sb[c] / nb - si[c] / ni; d2 += dd * dd }
                    if (sqrt(d2) > 40.0) { sw = e; break }
                }
                if (sw == null) break
                best = sw
            }
            val q = extendToBorder(best.q, labImg)
            return FloatArray(8) {
                val v = if (it % 2 == 0) q[it] / w else q[it] / h
                v.toFloat().coerceIn(0f, 1f)
            }
        } catch (e: Throwable) {
            return null
        } finally {
            toRelease.forEach { try { it.release() } catch (_: Throwable) {} }
        }
    }
}
