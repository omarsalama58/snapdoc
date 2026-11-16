package com.omarsalama58.snapdoc.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.imaging.EditParams
import com.omarsalama58.snapdoc.imaging.Filter
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ui.PrimaryButton
import com.omarsalama58.snapdoc.ui.defaultDocName
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import com.omarsalama58.snapdoc.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CameraScreen(
    store: DocStore,
    prefs: Prefs,
    initialDocId: String?,
    initialBatch: Boolean,
    onBack: () -> Unit,
    onCaptured: (path: String, docId: String?) -> Unit,
    onDone: (docId: String) -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val scope = rememberCoroutineScope()

    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(Manifest.permission.CAMERA) }

    var docId by remember { mutableStateOf(initialDocId) }
    var batch by remember { mutableStateOf(initialBatch) }
    var count by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    LaunchedEffect(flash) { imageCapture.flashMode = flash }

    DisposableEffect(hasPerm) {
        var provider: ProcessCameraProvider? = null
        if (hasPerm) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                try {
                    p.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                } catch (e: Exception) {
                    toast(ctx, "Camera error")
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { provider?.unbindAll() }
    }

    fun capture() {
        if (busy || !hasPerm) return
        busy = true
        val file = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (batch) {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val bmp = Imaging.decodeFile(file.absolutePath)
                                if (bmp == null) null else {
                                    val corners = Imaging.detectCorners(bmp)
                                    val cropped = Imaging.warp(bmp, corners)
                                    val filter = Filter.values()[prefs.defaultFilter.coerceIn(0, Filter.values().size - 1)]
                                    val out = Imaging.applyParams(cropped, EditParams(filter = filter))
                                    val doc = docId?.let { store.load(it) } ?: store.create(defaultDocName())
                                    store.addPage(doc, out)
                                    file.delete()
                                    Pair(doc.id, doc.pages.size)
                                }
                            }
                            if (result != null) {
                                docId = result.first
                                count = result.second
                            }
                            busy = false
                        }
                    } else {
                        busy = false
                        onCaptured(file.absolutePath, docId)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    toast(ctx, "Capture failed")
                }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(NavyDeep)) {
        if (hasPerm) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is needed to scan documents.", color = TextWhite)
                Spacer(Modifier.size(16.dp))
                PrimaryButton("Allow camera") { permLauncher.launch(Manifest.permission.CAMERA) }
            }
        }

        // top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = batch,
                onClick = { batch = !batch },
                label = { Text("Batch") }
            )
            IconButton(onClick = {
                flash = when (flash) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            }) {
                Icon(
                    when (flash) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        else -> Icons.Filled.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = CyanAccent
                )
            }
        }

        // bottom controls
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (batch && count > 0) {
                    Text("$count pages", color = TextWhite)
                }
            }
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(4.dp, CyanAccent, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (busy) TextMuted else TextWhite)
                    .clickable { capture() }
            )
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                val d = docId
                if (d != null && (batch || count > 0)) {
                    IconButton(
                        onClick = { onDone(d) },
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(CyanAccent)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Done", tint = NavyDeep)
                    }
                }
            }
        }
    }
}
