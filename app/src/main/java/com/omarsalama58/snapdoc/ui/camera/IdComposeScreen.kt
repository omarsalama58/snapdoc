package com.omarsalama58.snapdoc.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.omarsalama58.snapdoc.imaging.ImageOps
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.PrimaryButton
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows the scanned front and back of an ID card; each can be turned, then both go on one A4 page. */
@Composable
fun IdComposeScreen(
    frontPath: String,
    backPath: String?,
    onCancel: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf<Bitmap?>(null) }
    var back by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(frontPath, backPath) {
        withContext(Dispatchers.IO) {
            front = Imaging.decodeFile(frontPath)
            back = backPath?.let { Imaging.decodeFile(it) }
        }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("ID card", onBack = onCancel)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CardPreview("Front", front) { front = front?.let { Imaging.rotate(it, 90) } }
                if (backPath != null) CardPreview("Back", back) { back = back?.let { Imaging.rotate(it, 90) } }
            }
            Text(
                "Turn each side until the text reads upright.",
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            PrimaryButton(
                "Save to one page",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = front != null
            ) {
                val f = front ?: return@PrimaryButton
                busy = true
                scope.launch {
                    val page = withContext(Dispatchers.Default) { ImageOps.composeIdPage(f, back) }
                    busy = false
                    onDone(page)
                }
            }
        }
        if (busy) BusyOverlay("Creating page...")
    }
}

@Composable
private fun CardPreview(label: String, bmp: Bitmap?, onRotate: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NavyCard).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextWhite, modifier = Modifier.weight(1f))
            IconButton(onClick = onRotate) {
                Icon(Icons.Filled.RotateRight, contentDescription = "Rotate", tint = CyanAccent)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
