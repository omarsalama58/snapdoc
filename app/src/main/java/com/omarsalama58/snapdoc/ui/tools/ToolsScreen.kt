package com.omarsalama58.snapdoc.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omarsalama58.snapdoc.data.Doc
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.data.ScanMode
import com.omarsalama58.snapdoc.export.Exporter
import com.omarsalama58.snapdoc.imaging.ImageOps
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ocr.Ocr
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.DocPickerDialog
import com.omarsalama58.snapdoc.ui.TextResultDialog
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.defaultDocName
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.CyanGlow
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import com.omarsalama58.snapdoc.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ToolAction { WORD, IMAGES, LONG, PRINT, WATERMARK, COMPRESS, EXTRACT, REORDER }

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun ToolsScreen(
    store: DocStore,
    prefs: Prefs,
    onBack: () -> Unit,
    onScanMode: (ScanMode) -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onSignature: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onOpenDocSelect: (String) -> Unit,
    onMerged: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var mergeOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ToolAction?>(null) }
    var watermarkDoc by remember { mutableStateOf<Doc?>(null) }
    var compressDoc by remember { mutableStateOf<Doc?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = "Reading text..."
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    val f = Imaging.copyUriToCache(ctx, uri)
                    if (f == null) "" else try {
                        Ocr.recognize(ctx, listOf(f)).also { f.delete() }
                    } catch (e: Throwable) {
                        ""
                    }
                }
                busy = null
                ocrText = text
            }
        }
    }

    fun allPages(doc: Doc) = doc.pages.indices.toList()

    fun run(action: ToolAction, doc: Doc) {
        if (doc.pages.isEmpty() && action != ToolAction.EXTRACT && action != ToolAction.REORDER) {
            toast(ctx, "This document has no pages")
            return
        }
        when (action) {
            ToolAction.WORD -> {
                busy = "Creating Word file..."
                scope.launch {
                    val f = withContext(Dispatchers.IO) {
                        try { Exporter.docx(ctx, store, doc, allPages(doc)) } catch (e: Throwable) { null }
                    }
                    busy = null
                    if (f == null) toast(ctx, "Couldn't create the Word file") else Exporter.share(ctx, listOf(f), DOCX_MIME, "Share Word")
                }
            }
            ToolAction.IMAGES -> {
                Exporter.share(ctx, doc.pages.indices.map { store.pageFile(doc, it) }, "image/jpeg", "Share images")
            }
            ToolAction.LONG -> {
                busy = "Creating long image..."
                scope.launch {
                    val f = withContext(Dispatchers.IO) { Exporter.longImage(ctx, store, doc, allPages(doc)) }
                    busy = null
                    if (f == null) toast(ctx, "Couldn't create the image") else Exporter.share(ctx, listOf(f), "image/jpeg", "Share long image")
                }
            }
            ToolAction.PRINT -> {
                busy = "Preparing..."
                scope.launch {
                    val f = withContext(Dispatchers.IO) { Exporter.pdf(ctx, store, doc, allPages(doc), prefs) }
                    busy = null
                    Exporter.print(ctx, f)
                }
            }
            ToolAction.WATERMARK -> watermarkDoc = doc
            ToolAction.COMPRESS -> compressDoc = doc
            ToolAction.EXTRACT -> onOpenDocSelect(doc.id)
            ToolAction.REORDER -> onOpenDoc(doc.id)
        }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Tools", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Section("Scan") {
                    ToolTile("ID Cards", Icons.Filled.Badge) { onScanMode(ScanMode.ID) }
                    ToolTile("Extract Text", Icons.Filled.TextFields) { onScanMode(ScanMode.TEXT) }
                    ToolTile("Book", Icons.Filled.MenuBook) { onScanMode(ScanMode.BOOK) }
                    ToolTile("Whiteboard", Icons.Filled.Dashboard) { onScanMode(ScanMode.BOARD) }
                    ToolTile("Timestamp", Icons.Filled.Schedule) { onScanMode(ScanMode.STAMP) }
                    ToolTile("Scan Code", Icons.Filled.QrCodeScanner) { onScanMode(ScanMode.CODE) }
                }
                Section("Import") {
                    ToolTile("Import Images", Icons.Filled.PhotoLibrary, onImportImages)
                    ToolTile("Import PDF", Icons.Filled.PictureAsPdf, onImportPdf)
                    ToolTile("Text from Image", Icons.Filled.Image) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Section("Convert") {
                    ToolTile("To Word", Icons.Filled.Description) { pending = ToolAction.WORD }
                    ToolTile("PDF to Images", Icons.Filled.Collections) { pending = ToolAction.IMAGES }
                    ToolTile("Long Image", Icons.Filled.ViewDay) { pending = ToolAction.LONG }
                }
                Section("Edit") {
                    ToolTile("Sign", Icons.Filled.Edit, onSignature)
                    ToolTile("Add Watermark", Icons.Filled.BrandingWatermark) { pending = ToolAction.WATERMARK }
                    ToolTile("Merge Files", Icons.Filled.MergeType) { mergeOpen = true }
                    ToolTile("Extract Pages", Icons.Filled.ContentCopy) { pending = ToolAction.EXTRACT }
                    ToolTile("Reorder Pages", Icons.Filled.SwapVert) { pending = ToolAction.REORDER }
                    ToolTile("Compress", Icons.Filled.Compress) { pending = ToolAction.COMPRESS }
                }
                Section("Utilities") {
                    ToolTile("Print", Icons.Filled.Print) { pending = ToolAction.PRINT }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        pending?.let { action ->
            DocPickerDialog(
                store = store,
                title = "Choose a document",
                onPick = { doc -> pending = null; run(action, doc) },
                onDismiss = { pending = null }
            )
        }

        watermarkDoc?.let { doc ->
            var text by remember { mutableStateOf("SnapDoc") }
            AlertDialog(
                onDismissRequest = { watermarkDoc = null },
                containerColor = NavyCard,
                title = { Text("Add watermark", color = TextWhite) },
                text = {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = TextStyle(color = TextWhite))
                },
                confirmButton = {
                    TextButton(onClick = {
                        val t = text.trim()
                        watermarkDoc = null
                        if (t.isNotEmpty()) {
                            busy = "Adding watermark..."
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    doc.pages.indices.forEach { i ->
                                        val b = Imaging.decodeFile(store.pageFile(doc, i).absolutePath, Imaging.MAX_SIDE)
                                        if (b != null) store.replacePage(doc, i, ImageOps.watermark(b, t), alsoOrig = false)
                                    }
                                }
                                busy = null
                                onOpenDoc(doc.id)
                            }
                        }
                    }) { Text("Apply", color = CyanAccent) }
                },
                dismissButton = { TextButton(onClick = { watermarkDoc = null }) { Text("Cancel", color = TextMuted) } }
            )
        }

        compressDoc?.let { doc ->
            AlertDialog(
                onDismissRequest = { compressDoc = null },
                containerColor = NavyCard,
                title = { Text("Compress \"${doc.name}\"", color = TextWhite) },
                text = {
                    Column {
                        Text("Makes the pages smaller so the PDF is lighter. This can't be undone.", color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        listOf("Medium (1600 px)" to 1600, "High (1100 px)" to 1100, "Maximum (800 px)" to 800).forEach { (label, side) ->
                            TextButton(onClick = {
                                compressDoc = null
                                busy = "Compressing..."
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        doc.pages.indices.forEach { i ->
                                            val b = Imaging.decodeFile(store.pageFile(doc, i).absolutePath, side)
                                            if (b != null) store.replacePage(doc, i, b, alsoOrig = false)
                                        }
                                    }
                                    busy = null
                                    toast(ctx, "Done")
                                    onOpenDoc(doc.id)
                                }
                            }) { Text(label, color = CyanAccent) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { compressDoc = null }) { Text("Cancel", color = TextMuted) } }
            )
        }

        if (mergeOpen) {
            val docs = remember { store.list() }
            val picked = remember { mutableListOf<String>().toMutableStateList() }
            AlertDialog(
                onDismissRequest = { mergeOpen = false },
                containerColor = NavyCard,
                title = { Text("Select documents (in order)", color = TextWhite) },
                text = {
                    if (docs.isEmpty()) Text("No documents yet.", color = TextMuted)
                    else Column(Modifier.verticalScroll(rememberScrollState())) {
                        docs.forEach { d ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (picked.contains(d.id)) picked.remove(d.id) else picked.add(d.id)
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = picked.contains(d.id), onCheckedChange = {
                                    if (it) picked.add(d.id) else picked.remove(d.id)
                                })
                                Text(
                                    "${d.name} (${d.pages.size})" + if (picked.contains(d.id)) "  #${picked.indexOf(d.id) + 1}" else "",
                                    color = TextWhite, fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = picked.size >= 2, onClick = {
                        mergeOpen = false
                        busy = "Merging..."
                        val ids = picked.toList()
                        scope.launch {
                            val newId = withContext(Dispatchers.IO) {
                                val target = store.create("Merged " + defaultDocName())
                                ids.forEach { id -> store.load(id)?.let { store.appendPagesFrom(target, it) } }
                                target.id
                            }
                            busy = null
                            onMerged(newId)
                        }
                    }) { Text("Merge", color = if (picked.size >= 2) CyanAccent else TextMuted) }
                },
                dismissButton = { TextButton(onClick = { mergeOpen = false }) { Text("Cancel", color = TextMuted) } }
            )
        }
        ocrText?.let { TextResultDialog(it) { ocrText = null } }
        busy?.let { BusyOverlay(it) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavyCard)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Tiles(content)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Tiles(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        maxItemsInEachRow = 4
    ) { content() }
}

@Composable
private fun ToolTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1B4278)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = CyanGlow, modifier = Modifier.size(24.dp))
        }
        Text(label, color = TextWhite, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
    }
}
