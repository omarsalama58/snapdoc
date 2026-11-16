package com.omarsalama58.snapdoc.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.imaging.Filter
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite

@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    AppBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar("Settings", onBack = onBack)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                Section("PDF page size") {
                    FilterChip(selected = prefs.a4, onClick = { prefs.a4 = true }, label = { Text("A4") })
                    FilterChip(selected = !prefs.a4, onClick = { prefs.a4 = false }, label = { Text("Fit to image") })
                }

                Section("PDF quality") {
                    FilterChip(selected = prefs.pdfQuality == 60, onClick = { prefs.pdfQuality = 60 }, label = { Text("Small") })
                    FilterChip(selected = prefs.pdfQuality == 85, onClick = { prefs.pdfQuality = 85 }, label = { Text("Balanced") })
                    FilterChip(selected = prefs.pdfQuality == 95, onClick = { prefs.pdfQuality = 95 }, label = { Text("Best") })
                }

                Section("Filter for new scans") {
                    Filter.values().forEachIndexed { i, f ->
                        FilterChip(selected = prefs.defaultFilter == i, onClick = { prefs.defaultFilter = i }, label = { Text(f.label) })
                    }
                }

                Text("SnapDoc 1.0.0", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = CyanAccent, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
