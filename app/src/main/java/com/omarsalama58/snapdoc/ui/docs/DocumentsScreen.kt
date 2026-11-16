package com.omarsalama58.snapdoc.ui.docs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omarsalama58.snapdoc.data.Doc
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.ConfirmDialog
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.formatDate
import com.omarsalama58.snapdoc.ui.rememberThumb
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite

@Composable
fun DocumentsScreen(store: DocStore, storeRev: Int, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Doc?>(null) }
    val docs = remember(storeRev, tick) { store.list() }
    val shown = docs.filter { it.name.contains(query, ignoreCase = true) }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("My Documents", onBack = onBack)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search", color = TextMuted) },
                textStyle = TextStyle(color = TextWhite),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (docs.isEmpty()) "No documents yet. Scan your first one!" else "No results.",
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(shown, key = { it.id }) { doc ->
                        DocRow(store, doc, storeRev, onClick = { onOpen(doc.id) }, onDelete = { toDelete = doc })
                    }
                }
            }
        }

        toDelete?.let { d ->
            ConfirmDialog(
                title = "Delete document?",
                message = "\"${d.name}\" and all its pages will be deleted.",
                confirmText = "Delete",
                onConfirm = { store.delete(d.id); toDelete = null; tick++ },
                onDismiss = { toDelete = null }
            )
        }
    }
}

@Composable
private fun DocRow(store: DocStore, doc: Doc, rev: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 60.dp, height = 78.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (doc.pages.isNotEmpty()) {
                val thumb by rememberThumb(store.pageFile(doc, 0).absolutePath, rev, 300)
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(doc.name, color = TextWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${doc.pages.size} pages", color = CyanAccent, fontSize = 13.sp)
            Text(formatDate(doc.updated), color = TextMuted, fontSize = 12.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = TextMuted)
        }
    }
}
