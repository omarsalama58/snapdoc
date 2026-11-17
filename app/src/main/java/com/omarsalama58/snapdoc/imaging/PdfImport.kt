package com.omarsalama58.snapdoc.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlin.math.max
import kotlin.math.min

/** Turns the pages of an existing PDF into pictures so they can be edited like scans. */
object PdfImport {
    fun pages(ctx: Context, uri: Uri, maxPages: Int = 80): List<Bitmap> {
        val file = Imaging.copyUriToCache(ctx, uri) ?: return emptyList()
        val out = ArrayList<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                for (i in 0 until min(renderer.pageCount, maxPages)) {
                    val page = renderer.openPage(i)
                    try {
                        val scale = 1800f / max(page.width, page.height)
                        val w = max(1, (page.width * scale).toInt())
                        val h = max(1, (page.height * scale).toInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(bmp)
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            // password protected or damaged PDF: return what was read so far
        }
        file.delete()
        return out
    }
}
