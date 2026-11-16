package com.omarsalama58.snapdoc

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.imaging.EditParams
import com.omarsalama58.snapdoc.imaging.Filter
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.camera.CameraScreen
import com.omarsalama58.snapdoc.ui.crop.CropScreen
import com.omarsalama58.snapdoc.ui.defaultDocName
import com.omarsalama58.snapdoc.ui.docs.DocViewScreen
import com.omarsalama58.snapdoc.ui.docs.DocumentsScreen
import com.omarsalama58.snapdoc.ui.editor.EditorScreen
import com.omarsalama58.snapdoc.ui.home.HomeScreen
import com.omarsalama58.snapdoc.ui.settings.SettingsScreen
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.SnapDocTheme
import com.omarsalama58.snapdoc.ui.toast
import com.omarsalama58.snapdoc.ui.tools.SignatureScreen
import com.omarsalama58.snapdoc.ui.tools.ToolsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class Camera(val docId: String?, val batch: Boolean) : Screen
    data class Crop(val path: String, val docId: String?, val replaceIndex: Int?) : Screen
    data class Editor(val docId: String, val index: Int, val rev: Int = 0) : Screen
    data object Documents : Screen
    data class DocView(val docId: String) : Screen
    data object Tools : Screen
    data object Signature : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapDocTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DocStore(ctx) }
    val prefs = remember { Prefs(ctx) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var storeRev by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var importTarget by remember { mutableStateOf<String?>(null) }

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun goDoc(id: String) {
        storeRev++
        stack.clear()
        stack.add(Screen.Home)
        stack.add(Screen.DocView(id))
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    val defaultFilter = { Filter.values()[prefs.defaultFilter.coerceIn(0, Filter.values().size - 1)] }

    // gallery import: one image -> manual crop, several -> automatic crop into one document
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val target = importTarget
        if (uris.size == 1) {
            busy = "Opening image..."
            scope.launch {
                val f = withContext(Dispatchers.IO) { Imaging.copyUriToCache(ctx, uris[0]) }
                busy = null
                if (f == null) toast(ctx, "Couldn't open the image") else push(Screen.Crop(f.absolutePath, target, null))
            }
        } else {
            busy = "Importing ${uris.size} images..."
            scope.launch {
                val id = withContext(Dispatchers.IO) {
                    val doc = target?.let { store.load(it) } ?: store.create(defaultDocName())
                    for (u in uris) {
                        val f = Imaging.copyUriToCache(ctx, u) ?: continue
                        val bmp = Imaging.decodeFile(f.absolutePath) ?: continue
                        val cropped = Imaging.warp(bmp, Imaging.detectCorners(bmp))
                        val out = Imaging.applyParams(cropped, EditParams(filter = defaultFilter()))
                        store.addPage(doc, out)
                        f.delete()
                    }
                    doc.id
                }
                busy = null
                goDoc(id)
            }
        }
    }
    fun pickImages(target: String?) {
        importTarget = target
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .systemBarsPadding()
    ) {
        val current = stack.last()
        key(current) {
            when (val s = current) {
                Screen.Home -> HomeScreen(
                    onScanDocument = { push(Screen.Camera(null, false)) },
                    onImportFromGallery = { pickImages(null) },
                    onMyDocuments = { push(Screen.Documents) },
                    onTools = { push(Screen.Tools) },
                    onRecent = { push(Screen.Documents) },
                    onBatchScan = { push(Screen.Camera(null, true)) },
                    onSettings = { push(Screen.Settings) }
                )

                is Screen.Camera -> CameraScreen(
                    store = store,
                    prefs = prefs,
                    initialDocId = s.docId,
                    initialBatch = s.batch,
                    onBack = { pop() },
                    onCaptured = { path, docId -> push(Screen.Crop(path, docId, null)) },
                    onDone = { id -> goDoc(id) }
                )

                is Screen.Crop -> CropScreen(
                    path = s.path,
                    onCancel = { pop() },
                    onConfirm = { bmp ->
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val replace = s.replaceIndex
                                if (replace != null && s.docId != null) {
                                    val d = store.load(s.docId)
                                    if (d != null) {
                                        store.replacePage(d, replace, bmp, alsoOrig = true)
                                        Pair(d.id, replace)
                                    } else null
                                } else {
                                    val d = s.docId?.let { store.load(it) } ?: store.create(defaultDocName())
                                    val out = Imaging.applyParams(bmp, EditParams(filter = defaultFilter()))
                                    store.addPage(d, out)
                                    Pair(d.id, d.pages.lastIndex)
                                }
                            }
                            if (result == null) {
                                pop()
                            } else {
                                storeRev++
                                pop() // remove crop
                                if (s.replaceIndex != null) {
                                    val top = stack.lastOrNull()
                                    if (top is Screen.Editor) stack[stack.lastIndex] = top.copy(rev = top.rev + 1)
                                    else push(Screen.Editor(result.first, result.second))
                                } else {
                                    push(Screen.Editor(result.first, result.second))
                                }
                            }
                        }
                    }
                )

                is Screen.Editor -> EditorScreen(
                    store = store,
                    prefs = prefs,
                    docId = s.docId,
                    index = s.index,
                    onBack = {
                        val below = stack.getOrNull(stack.lastIndex - 1)
                        if (below is Screen.DocView) pop() else goDoc(s.docId)
                    },
                    onSaved = {
                        val below = stack.getOrNull(stack.lastIndex - 1)
                        if (below is Screen.DocView) { storeRev++; pop() } else goDoc(s.docId)
                    },
                    onCrop = { path -> push(Screen.Crop(path, s.docId, s.index)) },
                    onNewSignature = { push(Screen.Signature) }
                )

                Screen.Documents -> DocumentsScreen(
                    store = store,
                    storeRev = storeRev,
                    onBack = { pop() },
                    onOpen = { id -> push(Screen.DocView(id)) }
                )

                is Screen.DocView -> DocViewScreen(
                    store = store,
                    prefs = prefs,
                    docId = s.docId,
                    storeRev = storeRev,
                    onBack = { pop() },
                    onEditPage = { i -> push(Screen.Editor(s.docId, i)) },
                    onAddCamera = { push(Screen.Camera(s.docId, false)) },
                    onAddGallery = { pickImages(s.docId) },
                    onChanged = { storeRev++ },
                    onDeleted = { storeRev++; pop() }
                )

                Screen.Tools -> ToolsScreen(
                    store = store,
                    onBack = { pop() },
                    onSignature = { push(Screen.Signature) },
                    onMerged = { id -> goDoc(id) }
                )

                Screen.Signature -> SignatureScreen(onBack = { pop() }, onSaved = { pop() })

                Screen.Settings -> SettingsScreen(prefs = prefs, onBack = { pop() })
            }
        }
        busy?.let { BusyOverlay(it) }
    }
}
