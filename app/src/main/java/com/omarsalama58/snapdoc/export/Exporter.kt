package com.omarsalama58.snapdoc.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.omarsalama58.snapdoc.data.Doc
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.imaging.ImageOps
import com.omarsalama58.snapdoc.ocr.Ocr
import com.omarsalama58.snapdoc.pdf.PdfWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Everything that leaves the app: PDF, Word, long picture, pictures, printing. */
object Exporter {

    private fun exportDir(ctx: Context) = File(ctx.cacheDir, "export").also { it.mkdirs() }

    fun safeName(n: String) =
        n.replace(Regex("[^A-Za-z0-9؀-ۿ._ -]"), "_").trim().ifEmpty { "SnapDoc" }

    private fun pageFiles(store: DocStore, doc: Doc, pages: List<Int>) =
        pages.filter { it in doc.pages.indices }.map { store.pageFile(doc, it) }

    fun pdf(ctx: Context, store: DocStore, doc: Doc, pages: List<Int>, prefs: Prefs): File {
        val out = File(exportDir(ctx), safeName(doc.name) + ".pdf")
        PdfWriter.write(out, pageFiles(store, doc, pages), prefs.a4, prefs.pdfQuality)
        return out
    }

    fun separatePdfs(ctx: Context, store: DocStore, doc: Doc, pages: List<Int>, prefs: Prefs): List<File> {
        val dir = File(exportDir(ctx), "separate_" + System.currentTimeMillis()).also { it.mkdirs() }
        return pages.filter { it in doc.pages.indices }.mapIndexed { i, p ->
            val out = File(dir, "${safeName(doc.name)}_${i + 1}.pdf")
            PdfWriter.write(out, listOf(store.pageFile(doc, p)), prefs.a4, prefs.pdfQuality)
            out
        }
    }

    fun longImage(ctx: Context, store: DocStore, doc: Doc, pages: List<Int>): File? {
        val bmp = ImageOps.longImage(pageFiles(store, doc, pages)) ?: return null
        val out = File(exportDir(ctx), safeName(doc.name) + "_long.jpg")
        FileOutputStream(out).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        return out
    }

    /** Word file with the page pictures and the recognised (Arabic + English) text. */
    fun docx(
        ctx: Context, store: DocStore, doc: Doc, pages: List<Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): File {
        val files = pageFiles(store, doc, pages)
        val content = files.mapIndexed { i, f ->
            val text = try { Ocr.recognize(ctx, listOf(f)) } catch (e: Throwable) { "" }
            onProgress(i + 1, files.size)
            DocxWriter.PageContent(f, text)
        }
        val out = File(exportDir(ctx), safeName(doc.name) + ".docx")
        DocxWriter.write(out, content)
        return out
    }

    fun uriFor(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun share(ctx: Context, files: List<File>, mime: String, title: String) {
        if (files.isEmpty()) return
        val i = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uriFor(ctx, files[0])) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uriFor(ctx, it) }))
            }
        }
        i.type = mime
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, title))
    }

    /** Saves into Downloads/SnapDoc. Returns false when this Android version needs the share sheet instead. */
    fun saveToDownloads(ctx: Context, file: File, mime: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SnapDoc")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun print(ctx: Context, pdf: File) {
        val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(pdf.nameWithoutExtension, PdfPrintAdapter(pdf), null)
    }

    private class PdfPrintAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?, callback: WriteResultCallback
        ) {
            try {
                FileInputStream(file).use { i ->
                    FileOutputStream(destination.fileDescriptor).use { o -> i.copyTo(o) }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
