package com.marco.mlfilm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private fun displayName(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0)?.substringBeforeLast('.') else null
    }
} catch (_: Exception) {
    null
}

/** Carica la sorgente da sviluppare: il negativo se esiste, altrimenti la foto stessa. */
private fun loadSource(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
    val name = displayName(ctx, uri)
    val neg = name?.let { CameraController.negativeFile(ctx, it) }
    fun decode(open: () -> java.io.InputStream?): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / s > maxSide) s *= 2
        val d = BitmapFactory.Options().apply { inSampleSize = s }
        return open()?.use { BitmapFactory.decodeStream(it, null, d) }
    }
    return if (neg != null) decode { neg.inputStream() } else decode { ctx.contentResolver.openInputStream(uri) }
}

/** Sviluppa di nuovo una foto dell'album con un'altra camera, obiettivo e pellicola. */
@Composable
fun EditorScreen(uri: Uri, onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { RollPrefs(ctx) }
    val proc = remember { GlProcessor() }
    DisposableEffect(Unit) { onDispose { proc.release() } }
    val scope = rememberCoroutineScope()

    var ci by remember { mutableIntStateOf(prefs.cam.coerceIn(0, CAMERAS.size - 1)) }
    var li by remember { mutableIntStateOf(prefs.lens.coerceIn(0, LENSES.size - 1)) }
    var fi by remember { mutableIntStateOf(prefs.film.coerceIn(0, FILMS.size - 1)) }
    val hasNeg = remember(uri) {
        displayName(ctx, uri)?.let { CameraController.negativeFile(ctx, it) } != null
    }
    var date by remember { mutableStateOf(hasNeg && prefs.date) }
    var open by remember { mutableIntStateOf(-1) } // 0 camera, 1 lente, 2 pellicola, 3 apertura
    var compare by remember { mutableStateOf(false) }
    val showBokeh = open == 3
    var saving by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var ap by remember { mutableIntStateOf(nearestAperture(LENSES[li].f)) }
    var sh by remember { mutableIntStateOf(LENSES[li].shape) }
    LaunchedEffect(li) {
        ap = nearestAperture(LENSES[li].f)
        sh = LENSES[li].shape
    }
    var fd by remember { mutableStateOf(1f) }
    var fp by remember { mutableStateOf(0.5f to 0.5f) }
    var depth by remember { mutableStateOf<Bitmap?>(null) }
    var depthVer by remember { mutableIntStateOf(0) }
    var depthStatus by remember { mutableStateOf("CALCOLO...") }
    var mode by remember { mutableIntStateOf(0) } // 0 fuoco, 1 pennello nitido, 2 pennello sfocato

    val src by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadSource(ctx, uri, 1280) }
    }
    // Senza negativo la foto è già sviluppata: si tiene il suo formato, senza ritagliarla di nuovo.
    val look = run {
        val s = src
        val l = buildLook(CAMERAS[ci], LENSES[li], FILMS[fi]).copy(
            fNum = APERTURES[ap], shape = sh, focusDepth = fd, fpU = fp.first, fpV = fp.second,
        )
        if (hasNeg || s == null) l else l.copy(aspect = s.width.toFloat() / s.height)
    }
    val srcAspect = src?.let { it.width.toFloat() / it.height } ?: 1f

    // mappa di profondità: salvata col negativo, altrimenti calcolata ora
    LaunchedEffect(src) {
        val s = src ?: return@LaunchedEffect
        val name = displayName(ctx, uri)
        val d = withContext(Dispatchers.Default) {
            name?.let { DepthStore.load(ctx, it) }
                ?: DepthEstimator.estimate(s)?.also { b -> name?.let { n -> DepthStore.save(ctx, n, DepthStore.toPng(b)) } }
        }
        depthStatus = if (d != null) "AI" else "STIMATA"
        depth = d?.copy(Bitmap.Config.ARGB_8888, true)
        if (d != null) fd = DepthStore.focusDepth(d, look, srcAspect, 0.5f, 0.5f)
        depthVer++
    }

    fun paint(u: Float, v: Float) {
        val d = depth ?: return
        val (sx, sy) = DepthStore.toSource(u, v, look, srcAspect)
        val cx = sx * d.width
        val cy = sy * d.height
        val r = 0.07f * d.width / look.zoom
        val g = if (mode == 1) (fd * 255).toInt().coerceIn(0, 255) else if (fd > 0.5f) 0 else 255
        val c1 = android.graphics.Color.argb(255, g, g, g)
        val c0 = android.graphics.Color.argb(0, g, g, g)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(cx, cy, r, intArrayOf(c1, c1, c0), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        android.graphics.Canvas(d).drawCircle(cx, cy, r, p)
        depthVer++
    }

    LaunchedEffect(src, look, depthVer) {
        val s = src ?: return@LaunchedEffect
        delay(120)
        val out = suspendCancellableCoroutine<Bitmap?> { cont ->
            proc.process(s, look, 1280, if (look.fNum > 0f) depth else null) { cont.resume(it) }
        }
        if (out != null) preview = out.asImageBitmap()
    }

    val srcImg = remember(src) { src?.asImageBitmap() }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono("< INDIETRO", color = C.Text, size = 13.sp, modifier = Modifier.clickable { onBack() })
            Mono("MODIFICA", color = C.Dim, size = 13.sp)
            Mono(
                if (saving) "..." else "SALVA", color = C.Amber, size = 13.sp,
                modifier = Modifier.clickable(enabled = !saving) {
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val full = loadSource(ctx, uri, 4096) ?: return@withContext false
                            val fullLook = if (hasNeg) look else look.copy(aspect = full.width.toFloat() / full.height)
                            val dp = depth
                            val out = suspendCancellableCoroutine<Bitmap?> { cont ->
                                proc.process(full, fullLook, 4096, if (fullLook.fNum > 0f) dp else null) { cont.resume(it) }
                            }
                            full.recycle()
                            if (out == null) return@withContext false
                            if (date) CameraController.stampDate(out)
                            val name = CameraController.saveToGallery(ctx, out)
                            out.recycle()
                            if (name != null) {
                                val oldName = displayName(ctx, uri)
                                val neg = oldName?.let { CameraController.negativeFile(ctx, it) }
                                if (neg != null) CameraController.saveNegative(ctx, name, neg.readBytes())
                                dp?.let { DepthStore.save(ctx, name, DepthStore.toPng(it)) }
                            }
                            name != null
                        }
                        saving = false
                        if (ok) onSaved() else Toast.makeText(ctx, "Foto non salvata", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            preview?.let { pv ->
                Image(
                    if (compare && srcImg != null) srcImg else pv, null,
                    Modifier.fillMaxSize().padding(horizontal = 12.dp).pointerInput(showBokeh, mode, depth != null, look) {
                        if (!showBokeh) {
                            detectTapGestures(onPress = {
                                compare = true
                                tryAwaitRelease()
                                compare = false
                            })
                            return@pointerInput
                        }
                        fun toUv(o: Offset): Pair<Float, Float>? {
                            val sc = minOf(size.width / pv.width.toFloat(), size.height / pv.height.toFloat())
                            val dw = pv.width * sc
                            val dh = pv.height * sc
                            val u = (o.x - (size.width - dw) / 2f) / dw
                            val v = (o.y - (size.height - dh) / 2f) / dh
                            return if (u in 0f..1f && v in 0f..1f) u to v else null
                        }
                        if (mode == 0) {
                            detectTapGestures { o ->
                                val uv = toUv(o) ?: return@detectTapGestures
                                fp = uv
                                depth?.let { fd = DepthStore.focusDepth(it, look, srcAspect, uv.first, uv.second) }
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { o -> toUv(o)?.let { paint(it.first, it.second) } },
                                onDrag = { ch, _ -> toUv(ch.position)?.let { paint(it.first, it.second) } },
                            )
                        }
                    },
                    contentScale = ContentScale.Fit,
                )
            } ?: Mono("CARICO...", size = 12.sp)
        }
        ClapperStripe(Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(C.Panel)) {
            Slot("CAMERA", CAMERAS[ci].name, open == 0, false, { open = if (open == 0) -1 else 0 }) { CameraGlyph(CAMERAS[ci].id, it) }
            Slot("LENS", LENSES[li].name, open == 1, false, { open = if (open == 1) -1 else 1 }) { LensGlyph(LENSES[li], it) }
            Slot("FILM", FILMS[fi].name, open == 2, true, { open = if (open == 2) -1 else 2 }) { FilmGlyph(FILMS[fi].family, it) }
            Slot("APERTURA", apertureLabel(APERTURES[ap]), open == 3, false, { open = if (open == 3) -1 else 3 }) { ApertureGlyph(APERTURES[ap], it) }
        }
        if (open >= 0) {
            val panel = Modifier.fillMaxWidth().height(280.dp)
            when (open) {
                0 -> GearGrid("CAMERA", CAMERAS.size, ci, 4, { ci = it }, { CAMERAS[it].name }, panel) { i, m -> CameraGlyph(CAMERAS[i].id, m) }
                1 -> GearGrid("LENS", LENSES.size, li, 4, { li = it }, { LENSES[it].name }, panel) { i, m -> LensGlyph(LENSES[i], m) }
                2 -> FilmPicker(fi, { fi = it }, panel)
                else -> Column(panel.background(C.Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mono("APERTURA · FORMA BOKEH · FUOCO", size = 11.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Mono(apertureLabel(APERTURES[ap]), color = C.Amber, size = 12.sp, modifier = Modifier.padding(end = 8.dp))
                        Slider(
                            value = ap.toFloat(), onValueChange = { ap = Math.round(it).coerceIn(0, APERTURES.size - 1) },
                            valueRange = 0f..(APERTURES.size - 1).toFloat(), steps = APERTURES.size - 2,
                            colors = SliderDefaults.colors(thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.Line, activeTickColor = C.Amber, inactiveTickColor = C.Line),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SHAPES.forEachIndexed { i, n -> Chip(n, sh == i) { sh = i } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("TOCCA = FUOCO", mode == 0) { mode = 0 }
                        Chip("NITIDO", mode == 1) { mode = 1 }
                        Chip("SFOCA", mode == 2) { mode = 2 }
                    }
                    Mono("PROFONDITÀ: $depthStatus", color = C.Dim, size = 9.sp)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!hasNeg) Mono("SENZA NEGATIVO · L'EFFETTO SI SOMMA", color = C.Dim, size = 9.sp)
                Mono("TIENI PREMUTO SULLA FOTO = NEGATIVO", color = C.Dim, size = 9.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("DATE", date) { date = !date }
                }
            }
        }
    }
}
