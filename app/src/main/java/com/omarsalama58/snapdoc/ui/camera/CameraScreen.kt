package com.omarsalama58.snapdoc.ui.camera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.data.ScanMode
import com.omarsalama58.snapdoc.imaging.EditParams
import com.omarsalama58.snapdoc.imaging.Filter
import com.omarsalama58.snapdoc.imaging.ImageOps
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.ui.PrimaryButton
import com.omarsalama58.snapdoc.ui.defaultDocName
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import com.omarsalama58.snapdoc.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val codeReader = MultiFormatReader().apply {
    setHints(mapOf(DecodeHintType.TRY_HARDER to true))
}

private fun decodeCode(src: LuminanceSource): String? {
    for (s in listOf(src, src.rotateCounterClockwise())) {
        try {
            return codeReader.decodeWithState(BinaryBitmap(HybridBinarizer(s))).text
        } catch (e: NotFoundException) {
            // try the next orientation
        } finally {
            codeReader.reset()
        }
    }
    return null
}

@Composable
fun CameraScreen(
    store: DocStore,
    prefs: Prefs,
    initialDocId: String?,
    initialMode: ScanMode,
    initialBatch: Boolean,
    onBack: () -> Unit,
    onCaptured: (path: String, docId: String?, mode: ScanMode) -> Unit,
    onIdCaptured: (front: String, back: String?) -> Unit,
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
    var mode by remember { mutableStateOf(initialMode) }
    var batch by remember { mutableStateOf(initialBatch) }
    var count by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var idFront by remember { mutableStateOf<String?>(null) }
    var codeText by remember { mutableStateOf<String?>(null) }

    val modeState = rememberUpdatedState(mode)
    val codeState = rememberUpdatedState(codeText)

    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysis = remember {
        ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    }
    LaunchedEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        analysis.setAnalyzer(analysisExecutor) { image ->
            try {
                if (modeState.value == ScanMode.CODE && codeState.value == null) {
                    val plane = image.planes[0]
                    val buf = plane.buffer
                    val data = ByteArray(buf.remaining())
                    buf.get(data)
                    val src = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                    val text = decodeCode(src)
                    if (text != null) main.post { if (codeText == null) codeText = text }
                }
            } catch (e: Throwable) {
                // ignore a bad frame
            } finally {
                image.close()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            analysis.clearAnalyzer()
            analysisExecutor.shutdown()
        }
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
                    p.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
                } catch (e: Exception) {
                    try {
                        p.unbindAll()
                        p.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    } catch (e2: Exception) {
                        toast(ctx, "Camera error")
                    }
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { provider?.unbindAll() }
    }

    fun addToDoc(bmp: android.graphics.Bitmap): Pair<String, Int> {
        val doc = docId?.let { store.load(it) } ?: store.create(defaultDocName())
        store.addPage(doc, bmp)
        return Pair(doc.id, doc.pages.size)
    }

    fun capture() {
        if (busy || !hasPerm || mode == ScanMode.CODE) return
        busy = true
        val file = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        val currentMode = mode
        val currentBatch = batch
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    when {
                        currentMode == ScanMode.ID -> scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                val bmp = Imaging.decodeFile(file.absolutePath)
                                if (bmp == null) null else {
                                    val card = ImageOps.ensureLandscape(Imaging.warp(bmp, Imaging.detectCorners(bmp)))
                                    val out = File(ctx.cacheDir, "id_${System.nanoTime()}.jpg")
                                    FileOutputStream(out).use { card.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                                    file.delete()
                                    out.absolutePath
                                }
                            }
                            busy = false
                            if (path == null) {
                                toast(ctx, "Capture failed")
                            } else {
                                val front = idFront
                                if (front == null) idFront = path else {
                                    idFront = null
                                    onIdCaptured(front, path)
                                }
                            }
                        }

                        currentMode == ScanMode.STAMP || (currentMode == ScanMode.DOC && currentBatch) -> scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val bmp = Imaging.decodeFile(file.absolutePath)
                                if (bmp == null) null else {
                                    val out = if (currentMode == ScanMode.STAMP) {
                                        val t = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()).format(Date())
                                        ImageOps.stamp(bmp, t)
                                    } else {
                                        val cropped = Imaging.warp(bmp, Imaging.detectCorners(bmp))
                                        val f = Filter.values()[prefs.defaultFilter.coerceIn(0, Filter.values().size - 1)]
                                        Imaging.applyParams(cropped, EditParams(filter = f))
                                    }
                                    file.delete()
                                    addToDoc(out)
                                }
                            }
                            if (result != null) {
                                docId = result.first
                                count = result.second
                            }
                            busy = false
                        }

                        else -> {
                            busy = false
                            onCaptured(file.absolutePath, if (currentMode == ScanMode.TEXT) null else docId, currentMode)
                        }
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
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC0A1B3A), Color.Transparent)))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
            }
            Spacer(Modifier.weight(1f))
            if (mode == ScanMode.DOC) {
                FilterChip(selected = batch, onClick = { batch = !batch }, label = { Text("Batch") })
            }
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

        // hint banner
        val hint = when (mode) {
            ScanMode.ID -> if (idFront == null) "Place the FRONT of the card and capture" else "Now capture the BACK of the card"
            ScanMode.BOOK -> "Open the book flat. It will be split into two pages."
            ScanMode.TEXT -> "Capture text to read it out loud as copyable text"
            ScanMode.STAMP -> "Photos get the date and time written on them"
            ScanMode.CODE -> "Point the camera at a QR code or barcode"
            ScanMode.BOARD -> "Capture a whiteboard or slide"
            ScanMode.DOC -> null
        }
        if (hint != null) {
            Text(
                hint,
                color = TextWhite,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xAA0A1B3A))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // bottom panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0A1B3A))))
                .padding(bottom = 16.dp, top = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScanMode.values().forEach { m ->
                    Text(
                        m.label,
                        color = if (m == mode) NavyDeep else TextWhite,
                        fontSize = 13.sp,
                        fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (m == mode) CyanAccent else Color(0x66000000))
                            .clickable {
                                mode = m
                                idFront = null
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    if (count > 0 && (batch || mode == ScanMode.STAMP)) {
                        Text("$count pages", color = TextWhite, fontSize = 13.sp)
                    }
                    if (mode == ScanMode.ID && idFront != null) {
                        TextButton(onClick = {
                            val f = idFront
                            idFront = null
                            if (f != null) onIdCaptured(f, null)
                        }) { Text("Skip back", color = CyanAccent, fontSize = 12.sp) }
                    }
                }
                if (mode != ScanMode.CODE) {
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
                } else {
                    Box(Modifier.size(78.dp))
                }
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    val d = docId
                    if (d != null && (count > 0 || batch)) {
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

        codeText?.let { text ->
            AlertDialog(
                onDismissRequest = { codeText = null },
                containerColor = NavyCard,
                title = { Text("Code found", color = TextWhite) },
                text = { Text(text, color = TextWhite) },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("SnapDoc", text))
                            toast(ctx, "Copied")
                        }) { Text("Copy", color = CyanAccent) }
                        if (text.startsWith("http://") || text.startsWith("https://")) {
                            TextButton(onClick = {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
                                } catch (e: Exception) {
                                    toast(ctx, "Can't open this link")
                                }
                            }) { Text("Open", color = CyanAccent) }
                        }
                        TextButton(onClick = {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            ctx.startActivity(Intent.createChooser(i, "Share"))
                        }) { Text("Share", color = CyanAccent) }
                    }
                },
                dismissButton = { TextButton(onClick = { codeText = null }) { Text("Scan again", color = TextMuted) } }
            )
        }
    }
}
