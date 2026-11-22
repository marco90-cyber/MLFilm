package com.marco.mlfilm

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
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

/** Le foto arrivano sempre come copie nella cache dell'app (nome unico): il nome serve da chiave per la profondità. */
private fun displayName(uri: Uri): String? = uri.lastPathSegment?.substringBeforeLast('.')

/** Modifica una foto: camera, obiettivo, pellicola, apertura ed effetti, con anteprima live. */
@Composable
fun EditorScreen(uri: Uri, counter: String?, onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val proc = remember { GlProcessor() }
    DisposableEffect(Unit) { onDispose { proc.release() } }
    val scope = rememberCoroutineScope()

    var ci by remember { mutableIntStateOf(0) } // si parte da Smartphone (nessun effetto)
    var li by remember { mutableIntStateOf(0) }
    var fi by remember { mutableIntStateOf(prefs.film.coerceIn(0, FILMS.size - 1)) }
    var date by remember { mutableStateOf(prefs.date) }
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
    var flare by remember { mutableStateOf(0f) }
    var star by remember { mutableStateOf(0f) }
    var sf by remember { mutableStateOf(0f) }
    var cs by remember { mutableStateOf(0f) }
    var apod by remember { mutableStateOf(0f) }
    var fd by remember { mutableStateOf(1f) }
    var fp by remember { mutableStateOf(0.5f to 0.5f) }
    var depth by remember { mutableStateOf<Bitmap?>(null) }
    var depthVer by remember { mutableIntStateOf(0) }
    var depthStatus by remember { mutableStateOf("CALCOLO...") }
    var mode by remember { mutableIntStateOf(0) } // 0 fuoco, 1 pennello nitido, 2 pennello sfocato

    val src by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { PhotoIO.decode(ctx, uri, 1280) }
    }
    // La foto tiene il suo formato, senza ritagli.
    val look = run {
        val s = src
        val base = buildLook(CAMERAS[ci], LENSES[li], FILMS[fi])
        val l = base.copy(
            fNum = APERTURES[ap], shape = sh, focusDepth = fd, fpU = fp.first, fpV = fp.second,
            flare = flare, star = star, apod = apod,
            soft = base.soft + sf * 0.9f, halation = base.halation + sf * 0.35f,
            shadow = Rgb(base.shadow.r - cs * 0.02f, base.shadow.g + cs * 0.015f, base.shadow.b + cs * 0.05f),
            high = Rgb(base.high.r + cs * 0.05f, base.high.g + cs * 0.02f, base.high.b - cs * 0.04f),
        )
        if (s == null) l else l.copy(aspect = s.width.toFloat() / s.height)
    }
    val srcAspect = src?.let { it.width.toFloat() / it.height } ?: 1f

    // mappa di profondità: salvata col negativo, altrimenti calcolata ora
    LaunchedEffect(src) {
        val s = src ?: return@LaunchedEffect
        val name = displayName(uri)
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

    /** Sviluppa la foto alla massima risoluzione e la salva in Pictures/MLFilm; con [share] apre anche la condivisione. */
    fun export(share: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val full = PhotoIO.decode(ctx, uri, 4096) ?: return@withContext null
                val fullLook = look.copy(aspect = full.width.toFloat() / full.height)
                val out = suspendCancellableCoroutine<Bitmap?> { cont ->
                    proc.process(full, fullLook, 4096, if (fullLook.fNum > 0f) depth else null) { cont.resume(it) }
                }
                full.recycle()
                if (out == null) return@withContext null
                if (date) PhotoIO.stampDate(out)
                val u = PhotoIO.saveToGallery(ctx, out)
                out.recycle()
                u
            }
            saving = false
            if (saved == null) {
                Toast.makeText(ctx, "Foto non salvata", Toast.LENGTH_SHORT).show()
            } else if (share && saved.scheme == "content") {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, saved)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(i, null))
                onSaved()
            } else {
                Toast.makeText(ctx, "Salvata in Pictures/MLFilm", Toast.LENGTH_SHORT).show()
                onSaved()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono("< INDIETRO", color = C.Text, size = 13.sp, modifier = Modifier.clickable { onBack() })
            Mono(counter ?: "MODIFICA", color = C.Dim, size = 13.sp)
            Mono(
                if (saving) "..." else "SALVA", color = C.Amber, size = 13.sp,
                modifier = Modifier.clickable(enabled = !saving) { export(false) }
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
            Slot("EFFETTI", if (flare + star + sf + apod > 0f || cs != 0f) "ON" else "OFF", open == 4, false, { open = if (open == 4) -1 else 4 }) { EffectGlyph(it) }
        }
        if (open >= 0) {
            val panel = Modifier.fillMaxWidth().height(280.dp)
            when (open) {
                0 -> GearGrid("CAMERA", CAMERAS.size, ci, 4, { ci = it }, { CAMERAS[it].name }, panel) { i, m -> CameraGlyph(CAMERAS[i].id, m) }
                1 -> GearGrid("LENS", LENSES.size, li, 4, { li = it }, { LENSES[it].name }, panel) { i, m -> LensGlyph(LENSES[i], m) }
                2 -> FilmPicker(fi, { fi = it; prefs.film = it }, panel)
                4 -> Column(
                    panel.background(C.Panel).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Mono("EFFETTI DELL'OBIETTIVO", size = 11.sp)
                    FxSlider("FLARE", flare, 0f..1f) { flare = it }
                    FxSlider("STARBURST", star, 0f..1f) { star = it }
                    FxSlider("SOFT FOCUS", sf, 0f..1f) { sf = it }
                    FxSlider("COLORE", cs, -1f..1f) { cs = it }
                    FxSlider("FUOCO SFUGGENTE", apod, 0f..1f) { apod = it }
                    Mono("FLARE E STARBURST COMPAIONO SULLE LUCI FORTI", color = C.Dim, size = 9.sp)
                }
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
                Mono("TIENI PREMUTO SULLA FOTO = ORIGINALE", color = C.Dim, size = 9.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("DATE", date) { date = !date; prefs.date = date }
                    Chip("CONDIVIDI", false) { export(true) }
                }
            }
        }
    }
}

@Composable
private fun FxSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Mono(label, color = if (value != 0f) C.Amber else C.Text, size = 10.sp, modifier = Modifier.width(110.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.Line),
            modifier = Modifier.weight(1f),
        )
    }
}
