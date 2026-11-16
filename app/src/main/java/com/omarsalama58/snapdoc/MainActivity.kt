package com.omarsalama58.snapdoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.omarsalama58.snapdoc.ui.home.HomeScreen
import com.omarsalama58.snapdoc.ui.theme.SnapDocTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnapDocTheme {
                HomeScreen(
                    onScanDocument = { /* camera screen: next milestone */ },
                    onImportFromGallery = { /* gallery import: next milestone */ },
                    onMyDocuments = { /* document list: next milestone */ },
                    onTools = { /* editing tools: next milestone */ },
                    onRecent = { /* recent files: next milestone */ },
                    onBatchScan = { /* batch scan: next milestone */ },
                    onSettings = { /* settings: next milestone */ }
                )
            }
        }
    }
}
