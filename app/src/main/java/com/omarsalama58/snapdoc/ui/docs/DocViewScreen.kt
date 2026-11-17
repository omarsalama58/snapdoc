package com.omarsalama58.snapdoc.ui.docs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.imaging.ImageOps
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ocr.Ocr
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.ConfirmDialog
import com.omarsalama58.snapdoc.ui.PrimaryButton
import com.omarsalama58.snapdoc.ui.SecondaryButton
import com.omarsalama58.snapdoc.ui.TextResultDialog
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.defaultDocName
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
    startSelect: Boolean,
    onBack: () -> Unit,
    onEditPage: (Int) -> Unit,
    onAddCamera: () -> Unit,
    onAddGallery: () -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit,
    onOpenDoc: (String) -> Unit
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
    var watermarkOpen by remember { mutableStateOf(false) }
    var deleteDocOpen by remember { mutableStateOf(false) }
    var deletePagesOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf<String?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }

    var selectMode by remember { mutableStateOf(startSelect) }
    val selected = remember { mutableStateListOf<Int>() }

    fun refresh() {
        tick++
        onChanged()
    }

    fun targetPages(): List<Int> =
        if (selectMode && selected.isNotEmpty()) selected.sorted() else doc.pages.indices.toList()

    fun exitSelect() {
        selectMode = false
        selected.clear()
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            if (selectMode) {
                TopBar("${selected.size} selected", onBack = { exitSelect() }) {
                    IconButton(onClick = {
                        if (selected.size == doc.pages.size) selected.clear()
                        else { selected.clear(); selected.addAll(doc.pages.indices) }
                    }) { Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = TextWhite) }
                    IconButton(onClick = { if (selected.isNotEmpty()) shareOpen = true else toast(ctx, "Select pages first") }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = CyanAccent)
                    }
                    IconButton(onClick = {
                        if (selected.isEmpty()) {
                            toast(ctx, "Select pages first")
                        } else {
                            val pages = selected.sorted()
                            busyText = "Extracting pages..."
                            scope.launch {
                                val newId = withContext(Dispatchers.IO) {
                                    val target = store.create(defaultDocName())
                                    pages.forEach { i ->
                                        val b = Imaging.decodeFile(store.pageFile(doc, i).absolutePath, Imaging.MAX_SIDE)
                                        if (b != null) store.addPage(target, b)
                                    }
                                    target.id
                                }
                                busyText = null
                                exitSelect()
                                onChanged()
                                onOpenDoc(newId)
                            }
                        }
                    }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Extract to new document", tint = TextWhite) }
                    IconButton(onClick = { if (selected.isNotEmpty()) deletePagesOpen = true else toast(ctx, "Select pages first") }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = TextWhite)
                    }
                }
            } else {
                TopBar(doc.name, onBack = onBack) {
                    IconButton(onClick = { selectMode = true }) {
                        Icon(Icons.Filled.TaskAlt, contentDescription = "Select pages", tint = TextWhite)
                    }
                    IconButton(onClick = { shareOpen = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = CyanAccent)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextWhite)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameOpen = true })
                            DropdownMenuItem(text = { Text("Add watermark") }, onClick = { menuOpen = false; watermarkOpen = true })
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
                        val isSel = selected.contains(i)
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(NavyCard)
                                .border(if (isSel) 3.dp else 0.dp, if (isSel) CyanAccent else Color.Transparent, RoundedCornerShape(16.dp))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .background(Color.White)
                                    .pointerInput(selectMode, isSel, i) {
                                        detectTapGestures(
                                            onTap = {
                                                if (selectMode) {
                                                    if (isSel) selected.remove(i) else selected.add(i)
                                                } else onEditPage(i)
                                            },
                                            onLongPress = {
                                                if (!selectMode) selectMode = true
                                                if (!selected.contains(i)) selected.add(i)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                thumb?.let {
                                    Image(it, contentDescription = "Page ${i + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                }
                                if (selectMode) {
                                    Icon(
                                        if (isSel) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSel) CyanAccent else Color(0xFF607D8B),
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp)
                                            .background(Color.White.copy(alpha = 0.8f), CircleShape)
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { store.movePage(doc, i, i - 1); selected.clear(); refresh() },
                                    enabled = i > 0,
                                    modifier = Modifier.size(38.dp)
                                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move earlier", tint = if (i > 0) TextWhite else TextMuted) }
                                Text("${i + 1}", color = TextWhite, fontSize = 14.sp)
                                IconButton(
                                    onClick = { store.movePage(doc, i, i + 1); selected.clear(); refresh() },
                                    enabled = i < doc.pages.lastIndex,
                                    modifier = Modifier.size(38.dp)
                                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move later", tint = if (i < doc.pages.lastIndex) TextWhite else TextMuted) }
                            }
                        }
                    }
                }
            }

            if (!selectMode) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryButton("Camera", modifier = Modifier.weight(1f), onClick = onAddCamera)
                    SecondaryButton("Gallery", modifier = Modifier.weight(1f), onClick = onAddGallery)
                    PrimaryButton("Share / Save", modifier = Modifier.weight(1.3f)) { shareOpen = true }
                }
            }
        }

        if (shareOpen) {
            ShareSheet(store, prefs, doc, targetPages(), onDismiss = { shareOpen = false })
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
        if (watermarkOpen) {
            var text by remember { mutableStateOf("SnapDoc") }
            AlertDialog(
                onDismissRequest = { watermarkOpen = false },
                containerColor = NavyCard,
                title = { Text("Add watermark", color = TextWhite) },
                text = {
                    Column {
                        Text(
                            if (selectMode && selected.isNotEmpty()) "Applied to the ${selected.size} selected pages."
                            else "Applied to every page.",
                            color = TextMuted, fontSize = 13.sp
                        )
                        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                            textStyle = TextStyle(color = TextWhite), modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val t = text.trim()
                        val pages = targetPages()
                        watermarkOpen = false
                        if (t.isNotEmpty()) {
                            busyText = "Adding watermark..."
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    pages.forEach { i ->
                                        val b = Imaging.decodeFile(store.pageFile(doc, i).absolutePath, Imaging.MAX_SIDE)
                                        if (b != null) store.replacePage(doc, i, ImageOps.watermark(b, t), alsoOrig = false)
                                    }
                                }
                                busyText = null
                                refresh()
                            }
                        }
                    }) { Text("Apply", color = CyanAccent) }
                },
                dismissButton = { TextButton(onClick = { watermarkOpen = false }) { Text("Cancel", color = TextMuted) } }
            )
        }
        if (deleteDocOpen) {
            ConfirmDialog("Delete document?", "\"${doc.name}\" will be deleted.", "Delete",
                onConfirm = { store.delete(doc.id); deleteDocOpen = false; onDeleted() },
                onDismiss = { deleteDocOpen = false })
        }
        if (deletePagesOpen) {
            ConfirmDialog("Delete ${selected.size} pages?", "This can't be undone.", "Delete",
                onConfirm = {
                    selected.sortedDescending().forEach { store.deletePage(doc, it) }
                    deletePagesOpen = false
                    exitSelect()
                    refresh()
                },
                onDismiss = { deletePagesOpen = false })
        }
        ocrText?.let { TextResultDialog(it) { ocrText = null } }
        busyText?.let { BusyOverlay(it) }
    }
}
