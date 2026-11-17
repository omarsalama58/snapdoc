package com.omarsalama58.snapdoc.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omarsalama58.snapdoc.data.DocStore
import com.omarsalama58.snapdoc.data.Prefs
import com.omarsalama58.snapdoc.data.Signatures
import com.omarsalama58.snapdoc.imaging.EditParams
import com.omarsalama58.snapdoc.imaging.Filter
import com.omarsalama58.snapdoc.imaging.Filters
import com.omarsalama58.snapdoc.imaging.ImageOps
import com.omarsalama58.snapdoc.imaging.Imaging
import com.omarsalama58.snapdoc.imaging.Overlay
import com.omarsalama58.snapdoc.imaging.SigOv
import com.omarsalama58.snapdoc.imaging.StrokeOv
import com.omarsalama58.snapdoc.imaging.TextOv
import com.omarsalama58.snapdoc.ui.AppBackground
import com.omarsalama58.snapdoc.ui.BusyOverlay
import com.omarsalama58.snapdoc.ui.ColorSwatch
import com.omarsalama58.snapdoc.ui.PrimaryButton
import com.omarsalama58.snapdoc.ui.SecondaryButton
import com.omarsalama58.snapdoc.ui.TopBar
import com.omarsalama58.snapdoc.ui.theme.CyanAccent
import com.omarsalama58.snapdoc.ui.theme.CyanGlow
import com.omarsalama58.snapdoc.ui.theme.NavyCard
import com.omarsalama58.snapdoc.ui.theme.NavyDeep
import com.omarsalama58.snapdoc.ui.theme.TextMuted
import com.omarsalama58.snapdoc.ui.theme.TextWhite
import com.omarsalama58.snapdoc.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class EditMode { FILTERS, ADJUST, DRAW, TEXT, SIGN, ERASE }

private val inkColors = listOf(
    Color(0xFF000000), Color(0xFFD32F2F), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFFFFFFF)
)
private val highlightColor = Color(0x66FFEB3B)

@Composable
fun EditorScreen(
    store: DocStore,
    prefs: Prefs,
    docId: String,
    index: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onCrop: (pagePath: String) -> Unit,
    onNewSignature: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = remember(docId) { store.load(docId) }
    if (doc == null || index !in doc.pages.indices) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var useOrig by remember { mutableStateOf(false) }
    val sourceFile = if (useOrig) store.origFile(doc, index) else store.pageFile(doc, index)
    val pageFile = store.pageFile(doc, index)

    var rotation by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(Filter.ORIGINAL) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var sharpness by remember { mutableFloatStateOf(0f) }
    var applyAll by remember { mutableStateOf(false) }
    val params = EditParams(filter, brightness, contrast, sharpness)

    var mode by remember { mutableStateOf(EditMode.FILTERS) }
    val overlays = remember { mutableStateListOf<Overlay>() }
    val erases = remember { mutableStateListOf<StrokeOv>() }
    var selected by remember { mutableIntStateOf(-1) }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var comparing by remember { mutableStateOf(false) }

    var drawColor by remember { mutableStateOf(inkColors[2]) }
    var highlighter by remember { mutableStateOf(false) }
    var drawWidth by remember { mutableFloatStateOf(0.008f) }
    var eraseWidth by remember { mutableFloatStateOf(0.03f) }
    var textColor by remember { mutableStateOf(inkColors[0]) }
    var textSize by remember { mutableFloatStateOf(0.06f) }
    var textDialog by remember { mutableStateOf<TextDialogState?>(null) }
    val signatures = remember { Signatures.list(ctx) }

    var saving by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    // the picture the user sees (downscaled for speed)
    val previewBase by produceState<Bitmap?>(null, sourceFile.path) {
        value = withContext(Dispatchers.IO) { Imaging.decodeFile(sourceFile.absolutePath, 1400) }
    }
    val rotated = remember(previewBase, rotation) { previewBase?.let { Imaging.rotate(it, rotation) } }
    var processed by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(rotated, filter, brightness, contrast, sharpness, erases.size) {
        val src = rotated ?: return@LaunchedEffect
        val p = EditParams(filter, brightness, contrast, sharpness)
        val er = erases.toList()
        processed = withContext(Dispatchers.Default) { Filters.apply(ImageOps.erase(src, er), p) }
    }

    // small previews of every filter, like CamScanner's filter strip
    val thumbs by produceState<Map<Filter, ImageBitmap>>(emptyMap(), rotated) {
        val src = rotated ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val small = Imaging.scaleDown(src, 220)
            Filter.values().associateWith { f -> Filters.apply(small, EditParams(filter = f)).asImageBitmap() }
        }
    }

    fun normalize(pos: Offset): Offset {
        val bmp = processed ?: return Offset.Zero
        val r = Imaging.fitRect(canvasSize.width.toFloat(), canvasSize.height.toFloat(), bmp.width, bmp.height)
        return Offset(((pos.x - r.l) / r.w).coerceIn(0f, 1f), ((pos.y - r.t) / r.h).coerceIn(0f, 1f))
    }

    fun nearestMovable(pos: Offset): Int {
        val bmp = processed ?: return -1
        val r = Imaging.fitRect(canvasSize.width.toFloat(), canvasSize.height.toFloat(), bmp.width, bmp.height)
        val n = normalize(pos)
        var best = -1
        var bestD = Float.MAX_VALUE
        overlays.forEachIndexed { i, o ->
            val b = Imaging.overlayBounds(o, r.w, r.h) ?: return@forEachIndexed
            val px = n.x * r.w
            val py = n.y * r.h
            val inside = px >= b.left - 30 && px <= b.right + 30 && py >= b.top - 30 && py <= b.bottom + 30
            if (inside) {
                val d = hypot(px - b.centerX(), py - b.centerY())
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    fun save() {
        if (saving) return
        saving = true
        val snapshot = overlays.toList()
        val erased = erases.toList()
        val rot = rotation
        val p = params
        val src = sourceFile
        val all = applyAll
        scope.launch {
            withContext(Dispatchers.Default) {
                val loaded = Imaging.decodeFile(src.absolutePath, Imaging.MAX_SIDE) ?: return@withContext
                val full = Imaging.rotate(loaded, rot)
                val out = Filters.apply(ImageOps.erase(full, erased), p)
                AndroidCanvas(out).also { c ->
                    Imaging.drawOverlays(c, out.width.toFloat(), out.height.toFloat(), snapshot)
                }
                store.replacePage(doc, index, out, alsoOrig = false)
                if (all && !Filters.isDefault(p)) {
                    for (j in doc.pages.indices) {
                        if (j == index) continue
                        val b = Imaging.decodeFile(store.origFile(doc, j).absolutePath, Imaging.MAX_SIDE) ?: continue
                        store.replacePage(doc, j, Filters.apply(b, p), alsoOrig = false)
                    }
                }
            }
            saving = false
            onSaved()
        }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Page ${index + 1} of ${doc.pages.size}", onBack = onBack) {
                IconButton(onClick = { save() }) {
                    Icon(Icons.Filled.Check, contentDescription = "Save", tint = NavyDeep,
                        modifier = Modifier.size(38.dp).background(CyanAccent, RoundedCornerShape(19.dp)).padding(7.dp))
                }
            }

            // ------------------------------------------------ canvas
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        // drawing and erasing
                        .pointerInput(mode) {
                            if (mode == EditMode.DRAW || mode == EditMode.ERASE) {
                                detectDragGestures(
                                    onDragStart = { live = listOf(normalize(it)) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        live = live + normalize(change.position)
                                    },
                                    onDragEnd = {
                                        if (live.isNotEmpty()) {
                                            if (mode == EditMode.ERASE) {
                                                erases.add(StrokeOv(live, Color.White.toArgb(), eraseWidth))
                                            } else {
                                                val col = if (highlighter) highlightColor else drawColor
                                                val w = if (highlighter) drawWidth * 3.5f else drawWidth
                                                overlays.add(StrokeOv(live, col.toArgb(), w))
                                            }
                                        }
                                        live = emptyList()
                                    },
                                    onDragCancel = { live = emptyList() }
                                )
                            }
                        }
                        // moving text / signatures
                        .pointerInput(mode) {
                            if (mode == EditMode.TEXT || mode == EditMode.SIGN) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val i = nearestMovable(pos)
                                        if (i >= 0) selected = i
                                    },
                                    onDrag = { change, amount ->
                                        val bmp = processed
                                        val i = selected
                                        if (bmp != null && i in overlays.indices) {
                                            change.consume()
                                            val r = Imaging.fitRect(size.width.toFloat(), size.height.toFloat(), bmp.width, bmp.height)
                                            val dx = amount.x / r.w
                                            val dy = amount.y / r.h
                                            overlays[i] = when (val o = overlays[i]) {
                                                is TextOv -> o.copy(x = (o.x + dx).coerceIn(0f, 1f), y = (o.y + dy).coerceIn(0f, 1f))
                                                is SigOv -> o.copy(x = (o.x + dx).coerceIn(0f, 1f), y = (o.y + dy).coerceIn(0f, 1f))
                                                else -> o
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        .pointerInput(mode) {
                            if (mode == EditMode.TEXT || mode == EditMode.SIGN) {
                                detectTapGestures(onTap = { pos ->
                                    val i = nearestMovable(pos)
                                    if (i >= 0) {
                                        selected = i
                                    } else {
                                        selected = -1
                                        if (mode == EditMode.TEXT) {
                                            val n = normalize(pos)
                                            textDialog = TextDialogState("", n.x, n.y, -1)
                                        }
                                    }
                                })
                            }
                        }
                ) {
                    val bmp = if (comparing) rotated else processed
                    if (bmp != null) {
                        val r = Imaging.fitRect(size.width, size.height, bmp.width, bmp.height)
                        drawImage(
                            bmp.asImageBitmap(),
                            dstOffset = IntOffset(r.l.roundToInt(), r.t.roundToInt()),
                            dstSize = IntSize(r.w.roundToInt(), r.h.roundToInt())
                        )
                        if (!comparing) {
                            drawIntoCanvas { c ->
                                val nc = c.nativeCanvas
                                nc.save()
                                nc.translate(r.l, r.t)
                                var all: List<Overlay> = overlays.toList()
                                if (live.isNotEmpty()) {
                                    all = if (mode == EditMode.ERASE) {
                                        all + StrokeOv(live, Color(0x88FF1744).toArgb(), eraseWidth)
                                    } else {
                                        val col = if (highlighter) highlightColor else drawColor
                                        val w = if (highlighter) drawWidth * 3.5f else drawWidth
                                        all + StrokeOv(live, col.toArgb(), w)
                                    }
                                }
                                Imaging.drawOverlays(nc, r.w, r.h, all)
                                val sel = overlays.getOrNull(selected)
                                if (sel != null && (mode == EditMode.TEXT || mode == EditMode.SIGN)) {
                                    val b = Imaging.overlayBounds(sel, r.w, r.h)
                                    if (b != null) {
                                        val p = Paint().apply {
                                            style = Paint.Style.STROKE
                                            color = CyanAccent.toArgb()
                                            strokeWidth = 4f
                                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                                        }
                                        b.inset(-10f, -10f)
                                        nc.drawRect(b, p)
                                    }
                                }
                                nc.restore()
                            }
                        }
                    }
                }
                if (processed == null) {
                    Text("Loading...", color = TextWhite, modifier = Modifier.align(Alignment.Center))
                }

                // hold to see the picture before the filter
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC0A1B3A))
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                comparing = true
                                tryAwaitRelease()
                                comparing = false
                            })
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CompareArrows, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                    Text("  Hold to compare", color = TextWhite, fontSize = 12.sp)
                }
            }

            // ------------------------------------------------ context panel
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(NavyCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (mode) {
                    EditMode.FILTERS -> {
                        if (doc.pages.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                                Text("Pages to apply", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                FilterChip(selected = !applyAll, onClick = { applyAll = false }, label = { Text("This page") })
                                Spacer(Modifier.width(6.dp))
                                FilterChip(selected = applyAll, onClick = { applyAll = true }, label = { Text("All pages") })
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Filter.values().forEach { f ->
                                FilterThumb(f, thumbs[f], selected = filter == f) { filter = f }
                            }
                        }
                    }
                    EditMode.ADJUST -> {
                        LabeledSlider("Brightness", brightness, -100f, 100f) { brightness = it }
                        LabeledSlider("Contrast", contrast, 0.5f, 2f) { contrast = it }
                        LabeledSlider("Sharpness", sharpness, 0f, 1f) { sharpness = it }
                    }
                    EditMode.DRAW -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            inkColors.forEach { c ->
                                ColorSwatch(c, selected = !highlighter && drawColor == c) {
                                    drawColor = c; highlighter = false
                                }
                            }
                            ColorSwatch(Color(0xFFFFEB3B), selected = highlighter) { highlighter = true }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                val i = overlays.indexOfLast { it is StrokeOv }
                                if (i >= 0) overlays.removeAt(i)
                            }) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = TextWhite) }
                        }
                        LabeledSlider("Thickness", drawWidth, 0.002f, 0.03f) { drawWidth = it }
                    }
                    EditMode.ERASE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Paint over anything you want removed.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { if (erases.isNotEmpty()) erases.removeAt(erases.lastIndex) }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = TextWhite)
                            }
                        }
                        LabeledSlider("Brush size", eraseWidth, 0.01f, 0.1f) { eraseWidth = it }
                    }
                    EditMode.TEXT -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            inkColors.forEach { c ->
                                ColorSwatch(c, selected = textColor == c) {
                                    textColor = c
                                    val i = selected
                                    val o = overlays.getOrNull(i)
                                    if (o is TextOv) overlays[i] = o.copy(color = c.toArgb())
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            val sel = overlays.getOrNull(selected)
                            if (sel is TextOv) {
                                IconButton(onClick = { textDialog = TextDialogState(sel.text, sel.x, sel.y, selected) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit text", tint = TextWhite)
                                }
                            }
                            if (sel != null) {
                                IconButton(onClick = { overlays.removeAt(selected); selected = -1 }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = TextWhite)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                LabeledSlider("Size", textSize, 0.02f, 0.2f) {
                                    textSize = it
                                    val o = overlays.getOrNull(selected)
                                    if (o is TextOv) overlays[selected] = o.copy(size = it)
                                }
                            }
                            PrimaryButton("Add text", modifier = Modifier.padding(start = 8.dp)) {
                                textDialog = TextDialogState("", 0.5f, 0.5f, -1)
                            }
                        }
                    }
                    EditMode.SIGN -> {
                        if (signatures.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("No signatures yet.", color = TextMuted, modifier = Modifier.weight(1f))
                                PrimaryButton("Create signature") { onNewSignature() }
                            }
                        } else {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                signatures.forEach { f ->
                                    SignatureThumb(f) {
                                        overlays.add(SigOv(f.absolutePath, 0.5f, 0.75f, 0.35f))
                                        selected = overlays.lastIndex
                                    }
                                }
                                SecondaryButton("New") { onNewSignature() }
                            }
                            val sel = overlays.getOrNull(selected)
                            if (sel is SigOv) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        LabeledSlider("Size", sel.width, 0.1f, 0.9f) {
                                            overlays[selected] = sel.copy(width = it)
                                        }
                                    }
                                    IconButton(onClick = { overlays.removeAt(selected); selected = -1 }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = TextWhite)
                                    }
                                }
                            } else {
                                Text("Tap a signature to add it, then drag to place it.", color = TextMuted, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ tool bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(NavyDeep)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ToolItem("Filter", Icons.Filled.AutoFixHigh, mode == EditMode.FILTERS) { mode = EditMode.FILTERS }
                ToolItem("Adjust", Icons.Filled.Tune, mode == EditMode.ADJUST) { mode = EditMode.ADJUST }
                ToolItem("Crop", Icons.Filled.Crop, false) { onCrop(pageFile.absolutePath) }
                ToolItem("Rotate", Icons.Filled.RotateRight, false) {
                    if (overlays.isNotEmpty() || erases.isNotEmpty()) toast(ctx, "Rotate before adding text, drawings or erasing")
                    else rotation = (rotation + 90) % 360
                }
                ToolItem("Erase", Icons.Filled.Brush, mode == EditMode.ERASE) { mode = EditMode.ERASE; selected = -1 }
                ToolItem("Draw", Icons.Filled.Edit, mode == EditMode.DRAW) { mode = EditMode.DRAW; selected = -1 }
                ToolItem("Text", Icons.Filled.TextFields, mode == EditMode.TEXT) { mode = EditMode.TEXT; selected = -1 }
                ToolItem("Sign", Icons.Filled.Edit, mode == EditMode.SIGN) { mode = EditMode.SIGN; selected = -1 }
                ToolItem("Original", Icons.Filled.Restore, useOrig) {
                    if (overlays.isNotEmpty() || erases.isNotEmpty()) toast(ctx, "Remove your edits first")
                    else {
                        useOrig = !useOrig
                        rotation = 0
                        filter = Filter.ORIGINAL; brightness = 0f; contrast = 1f; sharpness = 0f
                    }
                }
            }
        }

        textDialog?.let { st ->
            var text by remember(st) { mutableStateOf(st.text) }
            AlertDialog(
                onDismissRequest = { textDialog = null },
                containerColor = NavyCard,
                title = { Text("Text", color = TextWhite) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = false,
                        textStyle = TextStyle(color = TextWhite)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (text.isNotBlank()) {
                            if (st.editIndex >= 0 && overlays.getOrNull(st.editIndex) is TextOv) {
                                overlays[st.editIndex] = (overlays[st.editIndex] as TextOv).copy(text = text)
                            } else {
                                overlays.add(TextOv(text, st.x, st.y, textSize, textColor.toArgb()))
                                selected = overlays.lastIndex
                            }
                        }
                        textDialog = null
                    }) { Text("OK", color = CyanAccent) }
                },
                dismissButton = { TextButton(onClick = { textDialog = null }) { Text("Cancel", color = TextMuted) } }
            )
        }

        if (saving) BusyOverlay("Saving...")
    }
}

private data class TextDialogState(val text: String, val x: Float, val y: Float, val editIndex: Int)

@Composable
private fun FilterThumb(f: Filter, thumb: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 78.dp, height = 104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(if (selected) 3.dp else 1.dp, if (selected) CyanAccent else Color(0xFF3A5A8C), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (thumb != null) {
            Image(thumb, contentDescription = f.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            f.label,
            color = if (selected) NavyDeep else TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(if (selected) CyanAccent else Color(0xAA0A1B3A))
                .padding(vertical = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextWhite, fontSize = 13.sp, modifier = Modifier.width(84.dp))
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ToolItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(70.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (active) CyanAccent else CyanGlow, modifier = Modifier.size(26.dp))
        Text(label, color = if (active) CyanAccent else TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SignatureThumb(file: File, onClick: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) { Imaging.decodeFile(file.absolutePath, 300)?.asImageBitmap() }
    }
    Box(
        Modifier
            .height(48.dp)
            .width(96.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, CyanAccent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        img?.let { Image(it, contentDescription = "Signature", contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp)) }
    }
}
