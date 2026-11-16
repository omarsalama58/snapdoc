package com.omarsalama58.snapdoc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// SnapDoc uses the dark navy look from the logo in both themes for a consistent brand.
private val SnapDocDarkColors = darkColorScheme(
    primary = CyanAccent,
    onPrimary = NavyDeep,
    secondary = CyanGlow,
    background = NavyDeep,
    onBackground = TextWhite,
    surface = NavyCard,
    onSurface = TextWhite,
    surfaceVariant = NavyMid,
    onSurfaceVariant = TextMuted
)

private val SnapDocLightColors = lightColorScheme(
    primary = CyanAccent,
    onPrimary = NavyDeep,
    secondary = CyanGlow,
    background = NavyDeep,
    onBackground = TextWhite,
    surface = NavyCard,
    onSurface = TextWhite,
    surfaceVariant = NavyMid,
    onSurfaceVariant = TextMuted
)

@Composable
fun SnapDocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SnapDocDarkColors else SnapDocLightColors,
        content = content
    )
}
