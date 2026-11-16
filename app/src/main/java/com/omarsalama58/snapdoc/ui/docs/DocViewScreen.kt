package com.omarsalama58.snapdoc.ui.docs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.ocr.Ocr
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.ConfirmDialog
import com.omarsalama58.snapdoc.ui.Exporter
import com.omarsalama58.snapdoc.ui.PrimaryButton
import com.omarsalama58.snapdoc.ui.SecondaryButton
import com.omarsalama58.snapdoc.ui.TextResultDialog
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.rememberThumb
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import com.omarsalama58.snapdoc.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DocViewScreen(
    store: DocStore,
    prefs: Prefs,
    docId: String,
    storeRev: Int,
    onBack: () -> Unit,
    onEditPage: (Int) -> Unit,
    onAddCamera: () -> Unit,
    onAddGallery: () -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val doc = remember(docId, storeRev, tick) { store.load(docId) }
    if (doc == null) {
        onBack()
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteDocOpen by remember { mutableStateOf(false) }
    var deletePage by remember { mutableStateOf(-1) }
    var busyText by remember { mutableStateOf<String?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        tick++
        onChanged()
    }

    fun exportPdf(action: (java.io.File) -> Unit) {
        if (doc.pages.isEmpty()) {
            toast(ctx, "This document has no pages")
            return
        }
        busyText = "Creating PDF..."
        scope.launch {
            val pdf = withContext(Dispatchers.IO) { Exporter.buildPdf(ctx, store, doc, prefs) }
            busyText = null
            action(pdf)
        }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(doc.name, onBack = onBack) {
                IconButton(onClick = { exportPdf { Exporter.sharePdf(ctx, it) } }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share PDF", tint = CyanAccent)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextWhite)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameOpen = true })
                        DropdownMenuItem(text = { Text("Save PDF to Downloads") }, onClick = {
                            menuOpen = false
                            exportPdf { pdf ->
                                if (Exporter.saveToDownloads(ctx, pdf)) toast(ctx, "Saved to Downloads/SnapDoc")
                                else Exporter.sharePdf(ctx, pdf)
                            }
                        })
                        DropdownMenuItem(text = { Text("Share as images") }, onClick = {
                            menuOpen = false
                            if (doc.pages.isNotEmpty())
                                Exporter.shareImages(ctx, doc.pages.indices.map { store.pageFile(doc, it) })
                        })
                        DropdownMenuItem(text = { Text("Extract text (OCR)") }, onClick = {
                            menuOpen = false
                            if (doc.pages.isEmpty()) return@DropdownMenuItem
                            busyText = "Reading text..."
                            scope.launch {
                                val files = doc.pages.indices.map { store.pageFile(doc, it) }
                                val text = withContext(Dispatchers.IO) {
                                    try { Ocr.recognize(ctx, files) } catch (e: Throwable) { "" }
                                }
                                busyText = null
                                ocrText = text
                            }
                        })
                        DropdownMenuItem(text = { Text("Delete document") }, onClick = { menuOpen = false; deleteDocOpen = true })
                    }
                }
            }

            if (doc.pages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No pages yet. Add one below.", color = TextMuted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(doc.pages, key = { _, p -> p.file }) { i, _ ->
                        val path = store.pageFile(doc, i).absolutePath
                        val thumb by rememberThumb(path, path, 500)
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(NavyCard)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .background(Color.White)
                                    .clickable { onEditPage(i) },
                                contentAlignment = Alignment.Center
                            ) {
                                thumb?.let {
                                    Image(it, contentDescription = "Page ${i + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { store.movePage(doc, i, i - 1); refresh() },
                                    enabled = i > 0,
                                    modifier = Modifier.size(38.dp)
                                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move earlier", tint = if (i > 0) TextWhite else TextMuted) }
                                Text("${i + 1}", color = TextWhite, fontSize = 14.sp)
                                IconButton(
                                    onClick = { store.movePage(doc, i, i + 1); refresh() },
                                    enabled = i < doc.pages.lastIndex,
                                    modifier = Modifier.size(38.dp)
                                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move later", tint = if (i < doc.pages.lastIndex) TextWhite else TextMuted) }
                                IconButton(onClick = { deletePage = i }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete page", tint = TextWhite)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryButton("Add page (camera)", modifier = Modifier.weight(1f), onClick = onAddCamera)
                SecondaryButton("From gallery", modifier = Modifier.weight(1f), onClick = onAddGallery)
            }
        }

        if (renameOpen) {
            var name by remember { mutableStateOf(doc.name) }
            AlertDialog(
                onDismissRequest = { renameOpen = false },
                containerColor = NavyCard,
                title = { Text("Rename", color = TextWhite) },
                text = {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                        textStyle = TextStyle(color = TextWhite))
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) { store.rename(doc, name.trim()); refresh() }
                        renameOpen = false
                    }) { Text("OK", color = CyanAccent) }
                },
                dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel", color = TextMuted) } }
            )
        }
        if (deleteDocOpen) {
            ConfirmDialog("Delete document?", "\"${doc.name}\" will be deleted.", "Delete",
                onConfirm = { store.delete(doc.id); deleteDocOpen = false; onDeleted() },
                onDismiss = { deleteDocOpen = false })
        }
        if (deletePage >= 0) {
            ConfirmDialog("Delete page ${deletePage + 1}?", "This can't be undone.", "Delete",
                onConfirm = { store.deletePage(doc, deletePage); deletePage = -1; refresh() },
                onDismiss = { deletePage = -1 })
        }
        ocrText?.let { TextResultDialog(it) { ocrText = null } }
        busyText?.let { BusyOverlay(it) }
    }
}
