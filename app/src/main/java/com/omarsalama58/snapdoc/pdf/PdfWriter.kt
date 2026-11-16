package com.omarsalama58.snapdoc.pdf

import android.graphics.Bitmap
import com.omarsalama58.snapdoc.imaging.Imaging
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.min

/** Tiny PDF writer: one JPEG image per page, so files stay small. */
object PdfWriter {

    private class Counting(private val os: OutputStream) {
        var pos = 0L
        fun w(s: String) = w(s.toByteArray(Charsets.ISO_8859_1))
        fun w(b: ByteArray) {
            os.write(b)
            pos += b.size
        }
    }

    fun write(out: File, pages: List<File>, a4: Boolean, quality: Int) {
        out.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(out)).use { os ->
            val c = Counting(os)
            val n = pages.size
            val offsets = ArrayList<Long>()
            c.w("%PDF-1.4\n")
            c.w(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

            offsets.add(c.pos)
            c.w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

            offsets.add(c.pos)
            val kids = (0 until n).joinToString(" ") { "${3 + 3 * it} 0 R" }
            c.w("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $n >>\nendobj\n")

            pages.forEachIndexed { i, file ->
                val bmp = Imaging.decodeFile(file.absolutePath, 2480) ?: return@forEachIndexed
                val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                val iw = bmp.width
                val ih = bmp.height

                val pw: Double
                val ph: Double
                val dw: Double
                val dh: Double
                if (a4) {
                    val landscape = iw > ih
                    pw = if (landscape) 841.89 else 595.28
                    ph = if (landscape) 595.28 else 841.89
                    val s = min(pw / iw, ph / ih)
                    dw = iw * s
                    dh = ih * s
                } else {
                    pw = iw * 0.48
                    ph = ih * 0.48
                    dw = pw
                    dh = ph
                }
                val dx = (pw - dw) / 2
                val dy = (ph - dh) / 2

                val pageId = 3 + 3 * i
                val contentId = pageId + 1
                val imageId = pageId + 2

                offsets.add(c.pos)
                c.w(
                    "$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(pw)} ${f(ph)}] " +
                        "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
                )

                val content = "q ${f(dw)} 0 0 ${f(dh)} ${f(dx)} ${f(dy)} cm /Im0 Do Q\n"
                offsets.add(c.pos)
                c.w("$contentId 0 obj\n<< /Length ${content.length} >>\nstream\n$content" + "endstream\nendobj\n")

                offsets.add(c.pos)
                c.w(
                    "$imageId 0 obj\n<< /Type /XObject /Subtype /Image /Width $iw /Height $ih " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n"
                )
                c.w(jpeg)
                c.w("\nendstream\nendobj\n")
                bmp.recycle()
            }

            val xrefPos = c.pos
            c.w("xref\n0 ${offsets.size + 1}\n")
            c.w("0000000000 65535 f \n")
            for (o in offsets) c.w(String.format(Locale.US, "%010d 00000 n \n", o))
            c.w("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        }
    }

    private fun f(d: Double) = String.format(Locale.US, "%.2f", d)
}
