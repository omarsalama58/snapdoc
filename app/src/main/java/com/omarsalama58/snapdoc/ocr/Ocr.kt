package com.omarsalama58.snapdoc.ocr

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import com.omarsalama58.snapdoc.imaging.Imaging
import java.io.File

/** Offline text recognition (Arabic + English) using Tesseract. */
object Ocr {

    private fun ensureData(ctx: Context): File {
        val base = File(ctx.filesDir, "tess")
        val td = File(base, "tessdata").also { it.mkdirs() }
        for (lang in listOf("ara", "eng")) {
            val f = File(td, "$lang.traineddata")
            if (!f.exists() || f.length() == 0L) {
                ctx.assets.open("tessdata/$lang.traineddata").use { i ->
                    f.outputStream().use { o -> i.copyTo(o) }
                }
            }
        }
        return base
    }

    fun recognize(ctx: Context, files: List<File>, onProgress: (Int) -> Unit = {}): String {
        val base = ensureData(ctx)
        val tess = TessBaseAPI()
        try {
            if (!tess.init(base.absolutePath, "ara+eng")) return ""
            val sb = StringBuilder()
            files.forEachIndexed { i, f ->
                val bmp = Imaging.decodeFile(f.absolutePath, 2200)
                if (bmp != null) {
                    tess.setImage(bmp)
                    val t = tess.utf8Text ?: ""
                    if (t.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(t.trim())
                    }
                }
                onProgress(i + 1)
            }
            return sb.toString()
        } finally {
            tess.recycle()
        }
    }
}
