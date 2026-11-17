package com.omarsalama58.snapdoc.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.DialogProperties
import com.omarsalama58.snapdoc.data.Doc
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.export.Exporter
import com.omarsalama58.snapdoc.ui.BottomSheetDialog
import com.omarsalama58.snapdoc.ui.SheetRow
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import com.omarsalama58.snapdoc.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Share / Save" panel: PDF, Word, one long picture, pictures, separate PDFs, download, print. */
@Composable
fun ShareSheet(store: DocStore, prefs: Prefs, doc: Doc, pages: List<Int>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }

    fun run(label: String, work: suspend () -> Unit) {
        busy = label
        scope.launch {
            try {
                work()
            } catch (e: Throwable) {
                toast(ctx, "Something went wrong")
            }
            busy = null
        }
    }

    BottomSheetDialog(onDismiss = { if (busy == null) onDismiss() }) {
        Text(
            if (pages.size == doc.pages.size) "Share  ·  ${doc.name}" else "Share  ·  ${pages.size} selected pages",
            color = TextWhite,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PDF size", color = TextMuted)
            FilterChip(selected = prefs.pdfQuality == 60, onClick = { prefs.pdfQuality = 60 }, label = { Text("Small") })
            FilterChip(selected = prefs.pdfQuality == 85, onClick = { prefs.pdfQuality = 85 }, label = { Text("Balanced") })
            FilterChip(selected = prefs.pdfQuality == 95, onClick = { prefs.pdfQuality = 95 }, label = { Text("Best") })
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetRow(Icons.Filled.PictureAsPdf, "Share as PDF") {
                run("Creating PDF...") {
                    val f = withContext(Dispatchers.IO) { Exporter.pdf(ctx, store, doc, pages, prefs) }
                    Exporter.share(ctx, listOf(f), "application/pdf", "Share PDF")
                    onDismiss()
                }
            }
            SheetRow(Icons.Filled.Description, "Share as Word", "Picture and editable text (Arabic + English)") {
                run("Reading text...") {
                    val f = withContext(Dispatchers.IO) {
                        Exporter.docx(ctx, store, doc, pages) { d, t -> busy = "Reading text $d / $t..." }
                    }
                    Exporter.share(
                        ctx, listOf(f),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Share Word file"
                    )
                    onDismiss()
                }
            }
            SheetRow(Icons.Filled.ViewDay, "Share as Long Image", "All pages in one tall picture") {
                run("Creating image...") {
                    val f = withContext(Dispatchers.IO) { Exporter.longImage(ctx, store, doc, pages) }
                    if (f == null) toast(ctx, "Nothing to share") else Exporter.share(ctx, listOf(f), "image/jpeg", "Share image")
                    onDismiss()
                }
            }
            SheetRow(Icons.Filled.Collections, "Share as Images", "One picture per page") {
                val files = pages.filter { it in doc.pages.indices }.map { store.pageFile(doc, it) }
                Exporter.share(ctx, files, "image/jpeg", "Share images")
                onDismiss()
            }
            SheetRow(Icons.Filled.FileCopy, "Export Pages as Separate PDFs") {
                run("Creating PDFs...") {
                    val files = withContext(Dispatchers.IO) { Exporter.separatePdfs(ctx, store, doc, pages, prefs) }
                    Exporter.share(ctx, files, "application/pdf", "Share PDFs")
                    onDismiss()
                }
            }
            SheetRow(Icons.Filled.Download, "Save PDF to Downloads") {
                run("Saving...") {
                    val f = withContext(Dispatchers.IO) { Exporter.pdf(ctx, store, doc, pages, prefs) }
                    val ok = withContext(Dispatchers.IO) { Exporter.saveToDownloads(ctx, f, "application/pdf") }
                    if (ok) toast(ctx, "Saved in Downloads/SnapDoc") else Exporter.share(ctx, listOf(f), "application/pdf", "Save PDF")
                    onDismiss()
                }
            }
            SheetRow(Icons.Filled.Print, "Print") {
                run("Preparing...") {
                    val f = withContext(Dispatchers.IO) { Exporter.pdf(ctx, store, doc, pages, prefs) }
                    Exporter.print(ctx, f)
                    onDismiss()
                }
            }
        }
    }

    busy?.let { text ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(NavyCard)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = CyanAccent)
                Text(text, color = TextWhite, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}
