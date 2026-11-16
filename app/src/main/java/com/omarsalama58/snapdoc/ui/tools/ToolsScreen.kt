package com.omarsalama58.snapdoc.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ocr.Ocr
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.TextResultDialog
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.CyanGlow
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import com.omarsalama58.snapdoc.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen(
    store: DocStore,
    onBack: () -> Unit,
    onSignature: () -> Unit,
    onMerged: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var mergeOpen by remember { mutableStateOf(false) }

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

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Tools", onBack = onBack)
            Column(Modifier.padding(16.dp)) {
                ToolCard("Create signature", "Draw your signature once, then add it to any page.", Icons.Filled.Edit, onSignature)
                ToolCard("Extract text from image", "Read Arabic and English text from a photo.", Icons.Filled.TextFields) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ToolCard("Merge documents", "Combine several documents into one.", Icons.Filled.MergeType) { mergeOpen = true }
                ToolCard("PDF export", "Open any document, then use the share button to get a PDF.", Icons.Filled.PictureAsPdf) {
                    toast(ctx, "Open a document, then tap the share icon")
                }
            }
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
                    else Column {
                        docs.take(12).forEach { d ->
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
                                val target = store.create("Merged " + com.omarsalama58.snapdoc.ui.defaultDocName())
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
private fun ToolCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NavyCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(34.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = TextWhite, fontSize = 16.sp)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
    }
}
