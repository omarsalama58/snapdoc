package com.omarsalama58.snapdoc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omarsalama58.snapdoc.data.Doc
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.CyanGlow
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite

/** A panel that slides up from the bottom of the screen. */
@Composable
fun BottomSheetDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(NavyCard)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                content = content
            )
        }
    }
}

@Composable
fun SheetRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(NavyDeep),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(24.dp)) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = TextWhite, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
    }
}

/** Lets the user choose one of their documents. */
@Composable
fun DocPickerDialog(store: DocStore, title: String, onPick: (Doc) -> Unit, onDismiss: () -> Unit) {
    val docs = remember { store.list() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = { Text(title, color = TextWhite) },
        text = {
            if (docs.isEmpty()) {
                Text("You don't have any documents yet.", color = TextMuted)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(docs, key = { it.id }) { d ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(d) }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(d.name, color = TextWhite, maxLines = 1)
                            Text("${d.pages.size} pages  ·  ${formatDate(d.updated)}", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = CyanAccent) } }
    )
}
