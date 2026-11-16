package com.omarsalama58.snapdoc.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.omarsalama58.snapdoc.R
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.CyanGlow
import com.omarsalama58.snapdoc.ui.theme.GoldCrown
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.NavyMid
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite

@Composable
fun HomeScreen(
    onScanDocument: () -> Unit,
    onImportFromGallery: () -> Unit,
    onMyDocuments: () -> Unit,
    onTools: () -> Unit,
    onRecent: () -> Unit,
    onBatchScan: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(NavyDeep, NavyMid))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Header(onSettings = onSettings)

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeatureCard(
                    title = "Scan Document",
                    icon = Icons.Outlined.DocumentScanner,
                    highlighted = true,
                    modifier = Modifier.weight(1f),
                    onClick = onScanDocument
                )
                FeatureCard(
                    title = "Import from Gallery",
                    icon = Icons.Outlined.PhotoLibrary,
                    modifier = Modifier.weight(1f),
                    onClick = onImportFromGallery
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeatureCard(
                    title = "My Documents",
                    icon = Icons.Outlined.Collections,
                    modifier = Modifier.weight(1f),
                    onClick = onMyDocuments
                )
                FeatureCard(
                    title = "Tools",
                    icon = Icons.Outlined.Build,
                    modifier = Modifier.weight(1f),
                    onClick = onTools
                )
            }

            Spacer(Modifier.weight(1f))

            BottomBar(
                onRecent = onRecent,
                onScan = onScanDocument,
                onBatchScan = onBatchScan
            )
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.snapdoc_logo),
                contentDescription = "SnapDoc logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Snap",
                color = TextWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Doc",
                color = CyanAccent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Premium",
                tint = GoldCrown,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.size(4.dp))
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = CyanAccent
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val background = if (highlighted) TextWhite else NavyCard
    val contentColor = if (highlighted) NavyDeep else TextWhite
    val iconTint = if (highlighted) CyanAccent else CyanGlow

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 26.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BottomBar(
    onRecent: () -> Unit,
    onScan: () -> Unit,
    onBatchScan: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BottomItem(label = "Recent", icon = Icons.Outlined.DateRange, onClick = onRecent)

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CyanAccent)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = "Scan",
                tint = NavyDeep,
                modifier = Modifier.size(36.dp)
            )
        }

        BottomItem(label = "Batch Scan", icon = Icons.Outlined.Description, onClick = onBatchScan)
    }
}

@Composable
private fun BottomItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = CyanGlow, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = TextMuted, fontSize = 12.sp)
    }
}
