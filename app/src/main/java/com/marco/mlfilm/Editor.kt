package com.marco.mlfilm

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/** Punto della sorgente (0..1, origine in alto a sinistra) -> punto nel fotogramma finale, inverso di DepthStore.toSource. */
private fun sourceToOut(sx: Float, sy: Float, look: Look, srcAspect: Float): Pair<Float, Float> {
    var cx = (sx - 0.5f) * look.zoom
    var cy = (0.5f - sy) * look.zoom
    if (look.aspect < srcAspect) cx /= look.aspect / srcAspect else cy /= srcAspect / look.aspect
    if (look.mirror) cx = -cx
    return (cx + 0.5f) to (0.5f - cy)
}

private fun fmtF(f: Float): String = if (f <= 0f) "OFF" else "F" + String.format(java.util.Locale.US, "%.1f", f)

private fun cameraGroups(): List<Pair<String, List<Int>>> =
    listOf("BASE", "DIGITALI", "PELLICOLA: REFLEX E COMPATTE", "FORMATI SPECIALI", "ISTANTANEE", "ECONOMICHE E LOMO")
        .map { gr -> gr to CAMERAS.indices.filter { CAMERAS[it].group == gr } }
        .filter { it.second.isNotEmpty() }

private fun lensGroups(): List<Pair<String, List<Int>>> =
    listOf("BASE", "RITRATTO", "STANDARD", "GRANDANGOLO", "ZOOM", "MACRO", "VINTAGE", "SPECIALI")
        .map { gr -> gr to LENSES.indices.filter { LENSES[it].group == gr } }
        .filter { it.second.isNotEmpty() }

private fun lensSub(l: LensSpec): String =
    if (l.focal == 0) "Nessuna lente" else "${l.focal}mm \u00b7 " + apertureLabel(l.f).replace("F", "f/") + if (l.real) " \u00b7 profilo Lensfun" else " \u00b7 stima"

private fun lensAp(f: Float): Float = if (f <= 0f) 0f else f.coerceIn(1.4f, 16f)

/** Fotografia di tutte le scelte, per annulla e ripeti. */
private data class Edit(
    val ci: Int, val li: Int, val fi: Int, val ap: Float, val sh: Int,
    val flare: Float, val star: Float, val sf: Float, val cs: Float, val apod: Float,
    val fd: Float, val fpU: Float, val fpV: Float, val hi: Float, val viv: Float, val snap: Int,
    val adj: Adj = Adj(), val fw: Float = 0f,
)

private class History(val items: List<Edit>, val idx: Int)

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
    var open by remember { mutableIntStateOf(-1) } // 0 camera, 1 lente, 2 pellicola, 3 apertura, 4 effetti
    var tab by remember { mutableIntStateOf(1) } // apertura: 0 profondità, 1 dimensione, 2 flare, 3 forma, 4 fuoco
    var compare by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var ap by remember { mutableFloatStateOf(lensAp(LENSES[0].f)) }
    var apDrag by remember { mutableStateOf(false) }
    var sh by remember { mutableIntStateOf(LENSES[0].shape) }
    var fw by remember { mutableFloatStateOf(0f) } // ampiezza zona a fuoco
    var adj by remember { mutableStateOf(Adj()) }
    var adjTab by remember { mutableIntStateOf(0) }
    var flare by remember { mutableFloatStateOf(0f) }
    var star by remember { mutableFloatStateOf(0f) }
    var sf by remember { mutableFloatStateOf(0f) }
    var cs by remember { mutableFloatStateOf(0f) }
    var apod by remember { mutableFloatStateOf(0f) }
    var hi by remember { mutableFloatStateOf(0f) }
    var viv by remember { mutableFloatStateOf(0f) }
    var fd by remember { mutableFloatStateOf(1f) }
    var fp by remember { mutableStateOf(0.5f to 0.5f) }
    var depth by remember { mutableStateOf<Bitmap?>(null) }
    var depthVer by remember { mutableIntStateOf(0) }
    var smooth by remember { mutableFloatStateOf(0.5f) } // levigatura della mappa di profondità
    var depthStatus by remember { mutableStateOf("CALCOLO...") }
    var dq by remember(uri) { mutableIntStateOf(1) } // 0 Pro, 1 Extreme (parte da solo Extreme)
    var showMap by remember { mutableStateOf(false) }
    var mSpec by remember(uri) { mutableStateOf(MaskSpec()) }
    var loc by remember(uri) { mutableStateOf(LocalAdj()) }
    var maskShow by remember { mutableStateOf(false) }
    var brushBmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var brushVer by remember(uri) { mutableIntStateOf(0) }
    var mBrushSize by remember { mutableFloatStateOf(0.05f) }
    var mErase by remember { mutableStateOf(false) }
    var brush by remember { mutableIntStateOf(0) } // 0 spento, 1 nitido, 2 sfocato
    var reticle by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var snapId by remember { mutableIntStateOf(0) }
    var nextSnap by remember { mutableIntStateOf(1) }
    val snaps = remember { HashMap<Int, Bitmap>() }

    fun editNow() = Edit(ci, li, fi, ap, sh, flare, star, sf, cs, apod, fd, fp.first, fp.second, hi, viv, snapId, adj, fw)
    var hist by remember { mutableStateOf(History(listOf(editNow()), 0)) }
    val cur = editNow()
    LaunchedEffect(cur) {
        delay(450)
        val h = hist
        if (h.items[h.idx] != cur) {
            val items = (h.items.take(h.idx + 1) + cur).takeLast(40)
            hist = History(items, items.lastIndex)
        }
    }

    val src by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { PhotoIO.decode(ctx, uri, 1280) }
    }
    // La foto tiene il suo formato, senza ritagli.
    val look = run {
        val s = src
        val base = buildLook(CAMERAS[ci], LENSES[li], FILMS[fi])
        val l = base.copy(
            fNum = ap, shape = sh, focusDepth = fd, focusW = fw, fpU = fp.first, fpV = fp.second,
            flare = flare, star = star, apod = apod, hi = hi, viv = viv, adj = adj,
            local = loc, maskShow = maskShow && open == 6 && mSpec.type != MaskType.NONE,
            soft = base.soft + sf * 0.9f, halation = base.halation + sf * 0.35f,
            shadow = Rgb(base.shadow.r - cs * 0.02f, base.shadow.g + cs * 0.015f, base.shadow.b + cs * 0.05f),
            high = Rgb(base.high.r + cs * 0.05f, base.high.g + cs * 0.02f, base.high.b - cs * 0.04f),
        )
        if (s == null) l else l.copy(aspect = s.width.toFloat() / s.height)
    }
    val srcAspect = src?.let { it.width.toFloat() / it.height } ?: 1f

    // mappa di profondità: calcolata ora (Pro = una passata, Extreme = media con la foto specchiata)
    LaunchedEffect(src, dq) {
        val s = src ?: return@LaunchedEffect
        depthStatus = "CALCOLO..."
        val name = displayName(uri)?.let { if (dq == 1) it + "_x3" else it }
        val d = withContext(Dispatchers.Default) {
            name?.let { DepthStore.load(ctx, it) }
                ?: DepthEstimator.estimate(s, dq == 1)?.also { b -> name?.let { n -> DepthStore.save(ctx, n, DepthStore.toPng(b)) } }
        }
        depthStatus = if (d != null) (if (dq == 1) "AI · EXTREME" else "AI · PRO") else "STIMATA"
        depth = d?.copy(Bitmap.Config.ARGB_8888, true)
        if (d != null) fd = DepthStore.focusDepth(d, look, srcAspect, fp.first, fp.second)
        snaps.values.forEach { it.recycle() }
        snaps.clear()
        depth?.let { snaps[0] = it.copy(Bitmap.Config.ARGB_8888, false) }
        snapId = 0
        nextSnap = 1
        depthVer++
        hist = History(listOf(editNow()), 0)
    }

    fun paint(u: Float, v: Float) {
        val d = depth ?: return
        val (sx, sy) = DepthStore.toSource(u, v, look, srcAspect)
        val cx = sx * d.width
        val cy = sy * d.height
        val r = 0.07f * d.width / look.zoom
        val g = if (brush == 1) (fd * 255).toInt().coerceIn(0, 255) else if (fd > 0.5f) 0 else 255
        val c1 = android.graphics.Color.argb(255, g, g, g)
        val c0 = android.graphics.Color.argb(0, g, g, g)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(cx, cy, r, intArrayOf(c1, c1, c0), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        android.graphics.Canvas(d).drawCircle(cx, cy, r, p)
        depthVer++
    }

    /** Fine di una pennellata: si tiene una copia della mappa per annulla e ripeti. */
    fun commitStroke() {
        val d = depth ?: return
        snaps[nextSnap] = d.copy(Bitmap.Config.ARGB_8888, false)
        snapId = nextSnap
        nextSnap++
        if (snaps.size > 12) snaps.keys.minOrNull()?.let { k -> if (k != snapId) snaps.remove(k)?.recycle() }
    }

    fun focusAt(u: Float, v: Float) {
        fp = u to v
        depth?.let { fd = DepthStore.focusDepth(it, look, srcAspect, u, v) }
        reticle = true
        tapCount++
    }
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(1400)
            reticle = false
        }
    }

    fun applyEdit(e: Edit) {
        ci = e.ci; li = e.li; fi = e.fi; ap = e.ap; sh = e.sh
        flare = e.flare; star = e.star; sf = e.sf; cs = e.cs; apod = e.apod
        fd = e.fd; fp = e.fpU to e.fpV; hi = e.hi; viv = e.viv; adj = e.adj; fw = e.fw
        if (e.snap != snapId) snaps[e.snap]?.let { depth = it.copy(Bitmap.Config.ARGB_8888, true); depthVer++ }
        snapId = e.snap
    }

    fun undo() {
        val h = hist
        if (h.idx > 0) {
            hist = History(h.items, h.idx - 1)
            applyEdit(h.items[h.idx - 1])
        }
    }

    fun redo() {
        val h = hist
        if (h.idx < h.items.lastIndex) {
            hist = History(h.items, h.idx + 1)
            applyEdit(h.items[h.idx + 1])
        }
    }

    /** Regola da sola apertura, fuoco e luci sfocate in base alla mappa di profondità. */
    fun magic() {
        val d = depth
        if (d == null) {
            Toast.makeText(ctx, "Profondità non pronta", Toast.LENGTH_SHORT).show()
            return
        }
        val w = d.width
        val h = d.height
        val centre = ArrayList<Triple<Int, Int, Int>>()
        var y = h / 4
        while (y < h * 3 / 4) {
            var x = w / 4
            while (x < w * 3 / 4) {
                centre += Triple(x, y, (d.getPixel(x, y) shr 16) and 255)
                x += 4
            }
            y += 4
        }
        if (centre.isEmpty()) return
        val sorted = centre.map { it.third }.sorted()
        val target = sorted[(sorted.size * 0.75f).toInt().coerceAtMost(sorted.size - 1)]
        val best = centre.filter { Math.abs(it.third - target) <= 6 }
            .minByOrNull { (it.first - w / 2f) * (it.first - w / 2f) + (it.second - h / 2f) * (it.second - h / 2f) } ?: centre[0]
        val all = ArrayList<Int>()
        y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                all += (d.getPixel(x, y) shr 16) and 255
                x += 8
            }
            y += 8
        }
        all.sort()
        val spread = (all[(all.size * 0.9f).toInt().coerceAtMost(all.size - 1)] - all[(all.size * 0.1f).toInt()]) / 255f
        ap = if (spread > 0.45f) 2.0f else 2.8f
        sh = 0
        apod = 0f
        hi = 0.3f
        viv = 0.3f
        fd = target / 255f
        fp = (best.first.toFloat() / w) to (best.second.toFloat() / h)
        reticle = true
        tapCount++
        Toast.makeText(ctx, "Apertura e fuoco regolati", Toast.LENGTH_SHORT).show()
    }

    // mappa di profondità pulita (mediana + filtro bilaterale guidato dalla foto) usata per il rendering
    val depthR by produceState<Bitmap?>(null, depth, depthVer, smooth, src) {
        val d = depth
        val s = src
        if (d == null || s == null || smooth < 0.02f) { value = d; return@produceState }
        delay(150)
        value = withContext(Dispatchers.Default) { DepthFilter.smooth(d, s, smooth) }
    }
    // maschera locale (soggetto, sfondo, gradienti, pennello) in bassa risoluzione
    val maskBmp by produceState<Bitmap?>(null, mSpec, depthR, depth, brushVer, srcAspect) {
        val spec = mSpec
        if (spec.type == MaskType.NONE) { value = null; return@produceState }
        val d = depthR ?: depth
        val b = brushBmp
        val a = srcAspect
        value = withContext(Dispatchers.Default) { LocalMask.build(spec, d, b, a) }
    }
    LaunchedEffect(src, look, depthVer, depthR, maskBmp) {
        val s = src ?: return@LaunchedEffect
        val out = suspendCancellableCoroutine<Bitmap?> { cont ->
            proc.processLatest(s, look, 1280, if (look.fNum > 0f) (depthR ?: depth) else null, maskBmp) { if (cont.isActive) cont.resume(it) }
        }
        if (out != null) preview = out.asImageBitmap()
    }

    val srcImg = remember(src) { src?.asImageBitmap() }
    // mirino "focus peaking": le zone a fuoco (profondità vicina a fd) si tingono d'ambra mentre si trascina la barra
    val peakOn = ap > 0f && !compare && open == 3 && tab == 4 && depth != null
    val peak by produceState<ImageBitmap?>(null, depthVer, fd, fw, peakOn) {
        val d = depth
        if (!peakOn || d == null) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            val w = 160
            val h = (w * d.height / d.width.toFloat()).toInt().coerceAtLeast(1)
            val sm = Bitmap.createScaledBitmap(d, w, h, true)
            val px = IntArray(w * h)
            sm.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) {
                val g = (px[i] and 0xFF) / 255f
                val a = (1f - Math.max(Math.abs(g - fd) - fw, 0f) / 0.1f).coerceIn(0f, 1f)
                px[i] = ((a * 170f).toInt() shl 24) or 0xF5A623
            }
            val o = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            if (sm !== d) sm.recycle()
            o.asImageBitmap()
        }
    }
    val mapImg = remember(depthVer, showMap, depth) { if (showMap) depth?.asImageBitmap() else null }

    /** Sviluppa la foto alla massima risoluzione e la salva in Pictures/MLFilm; con [share] apre anche la condivisione. */
    fun export(share: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val full = PhotoIO.decode(ctx, uri, 4096) ?: return@withContext null
                val dExp = depth?.let { dd -> if (smooth >= 0.02f) DepthFilter.smooth(dd, full, smooth) else dd }
                val fullLook = look.copy(aspect = full.width.toFloat() / full.height)
                val out = suspendCancellableCoroutine<Bitmap?> { cont ->
                    proc.process(full, fullLook, 4096, if (fullLook.fNum > 0f) dExp else null, maskBmp) { cont.resume(it) }
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

    val canUndo = hist.idx > 0
    val canRedo = hist.idx < hist.items.lastIndex
    val brushOn = brush != 0 && open == 3 && tab == 0
    val maskDrag = open == 6 && (mSpec.type == MaskType.LINEAR || mSpec.type == MaskType.RADIAL || mSpec.type == MaskType.BRUSH)

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono("< INDIETRO", color = C.Text, size = 13.sp, modifier = Modifier.clickable { onBack() })
            Mono(counter ?: "MODIFICA", color = C.Dim, size = 13.sp)
            Mono(
                if (saving) "..." else "SALVA", color = C.Amber, size = 13.sp,
                modifier = Modifier.clickable(enabled = !saving) { export(false) }
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            preview?.let { pv ->
                Box(
                    Modifier.fillMaxSize().pointerInput(brushOn, look, depth, ap > 0f, maskDrag, mSpec.type) {
                        fun toUv(o: Offset): Pair<Float, Float>? {
                            val sc = minOf(size.width / pv.width.toFloat(), size.height / pv.height.toFloat())
                            val dw = pv.width * sc
                            val dh = pv.height * sc
                            val u = (o.x - (size.width - dw) / 2f) / dw
                            val v = (o.y - (size.height - dh) / 2f) / dh
                            return if (u in 0f..1f && v in 0f..1f) u to v else null
                        }
                        if (maskDrag) {
                            var last: Pair<Float, Float>? = null
                            fun maskPt(o: Offset): Pair<Float, Float>? =
                                toUv(o)?.let { DepthStore.toSource(it.first, it.second, look, srcAspect) }
                            fun stampTo(pt: Pair<Float, Float>) {
                                val b = brushBmp ?: LocalMask.newBrush().also { brushBmp = it }
                                val l0 = last
                                if (l0 == null) {
                                    LocalMask.stamp(b, pt.first, pt.second, mBrushSize, mErase)
                                } else {
                                    val dx = pt.first - l0.first
                                    val dy = pt.second - l0.second
                                    val n = (Math.sqrt((dx * dx + dy * dy).toDouble()) / (mBrushSize * 0.4f)).toInt().coerceIn(1, 40)
                                    for (k in 1..n) LocalMask.stamp(b, l0.first + dx * k / n, l0.second + dy * k / n, mBrushSize, mErase)
                                }
                                last = pt
                                brushVer++
                            }
                            detectDragGestures(
                                onDragStart = { o ->
                                    maskPt(o)?.let { pt ->
                                        when (mSpec.type) {
                                            MaskType.LINEAR -> mSpec = mSpec.copy(x1 = pt.first, y1 = pt.second, x2 = pt.first, y2 = pt.second)
                                            MaskType.RADIAL -> mSpec = mSpec.copy(x1 = pt.first, y1 = pt.second)
                                            else -> { last = null; stampTo(pt) }
                                        }
                                        Unit
                                    }
                                },
                                onDragEnd = { last = null },
                                onDragCancel = { last = null },
                                onDrag = { ch, _ ->
                                    maskPt(ch.position)?.let { pt ->
                                        when (mSpec.type) {
                                            MaskType.LINEAR -> mSpec = mSpec.copy(x2 = pt.first, y2 = pt.second)
                                            MaskType.RADIAL -> {
                                                val ex = (pt.first - mSpec.x1) * srcAspect
                                                val ey = pt.second - mSpec.y1
                                                mSpec = mSpec.copy(rad = Math.sqrt((ex * ex + ey * ey).toDouble()).toFloat().coerceIn(0.03f, 1.2f))
                                            }
                                            else -> stampTo(pt)
                                        }
                                        Unit
                                    }
                                },
                            )
                        } else if (brushOn) {
                            detectDragGestures(
                                onDragStart = { o -> toUv(o)?.let { paint(it.first, it.second) } },
                                onDragEnd = { commitStroke() },
                                onDragCancel = { commitStroke() },
                                onDrag = { ch, _ -> toUv(ch.position)?.let { paint(it.first, it.second) } },
                            )
                        } else {
                            detectTapGestures(
                                onPress = {
                                    val j = scope.launch { delay(350); compare = true }
                                    tryAwaitRelease()
                                    j.cancel()
                                    compare = false
                                },
                                onLongPress = {},
                                onTap = { o ->
                                    if (ap > 0f) toUv(o)?.let { focusAt(it.first, it.second) }
                                },
                            )
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val m = mapImg
                    if (m != null) {
                        Image(m, null, Modifier.aspectRatio(srcAspect), contentScale = ContentScale.FillBounds)
                    } else {
                        Image(
                            if (compare && srcImg != null) srcImg else pv, null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        )
                    }
                    peak?.let { Image(it, null, Modifier.aspectRatio(srcAspect), contentScale = ContentScale.FillBounds) }
                    if (open == 6 && !compare && (mSpec.type == MaskType.LINEAR || mSpec.type == MaskType.RADIAL)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sc = minOf(size.width / pv.width.toFloat(), size.height / pv.height.toFloat())
                            val dw = pv.width * sc
                            val dh = pv.height * sc
                            val ox = (size.width - dw) / 2f
                            val oy = (size.height - dh) / 2f
                            fun pt(sx: Float, sy: Float): Offset {
                                val o = sourceToOut(sx, sy, look, srcAspect)
                                return Offset(ox + o.first * dw, oy + o.second * dh)
                            }
                            val a = pt(mSpec.x1, mSpec.y1)
                            val sw = 2.dp.toPx()
                            if (mSpec.type == MaskType.LINEAR) {
                                val b = pt(mSpec.x2, mSpec.y2)
                                drawLine(C.Amber, a, b, strokeWidth = sw)
                                drawCircle(C.Amber, 8.dp.toPx(), a)
                                drawCircle(C.Amber, 8.dp.toPx(), b, style = Stroke(width = sw))
                            } else {
                                drawCircle(C.Amber, mSpec.rad * dh, a, style = Stroke(width = sw))
                                drawCircle(C.Amber, 6.dp.toPx(), a)
                            }
                        }
                    }
                    if (ap > 0f && !compare && (reticle || (open == 3 && tab == 4))) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sc = minOf(size.width / pv.width.toFloat(), size.height / pv.height.toFloat())
                            val dw = pv.width * sc
                            val dh = pv.height * sc
                            val cx = (size.width - dw) / 2f + fp.first * dw
                            val cy = (size.height - dh) / 2f + fp.second * dh
                            val half = minOf(dw, dh) * 0.11f
                            val sw = 2.dp.toPx()
                            val tick = 7.dp.toPx()
                            drawRect(C.Amber, Offset(cx - half, cy - half), Size(half * 2f, half * 2f), style = Stroke(width = sw))
                            drawLine(C.Amber, Offset(cx, cy - half), Offset(cx, cy - half + tick), strokeWidth = sw)
                            drawLine(C.Amber, Offset(cx, cy + half), Offset(cx, cy + half - tick), strokeWidth = sw)
                            drawLine(C.Amber, Offset(cx - half, cy), Offset(cx - half + tick, cy), strokeWidth = sw)
                            drawLine(C.Amber, Offset(cx + half, cy), Offset(cx + half - tick, cy), strokeWidth = sw)
                        }
                    }
                    if (apDrag) {
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .background(Color(0xE61C1C1C), RoundedCornerShape(10.dp))
                                .padding(horizontal = 28.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            androidx.compose.material3.Text(
                                "F / " + String.format(java.util.Locale.US, "%.1f", ap),
                                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            )
                            ApertureGlyph(ap, Modifier.size(56.dp))
                        }
                    }
                }
            } ?: Mono("CARICO...", size = 12.sp)
        }

        // ---- annulla / ripeti, Magic Enhance, confronto
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clickable(enabled = canUndo) { undo() }, contentAlignment = Alignment.Center) {
                    UndoGlyph(false, canUndo, Modifier.size(32.dp))
                }
                Box(Modifier.size(52.dp).clickable(enabled = canRedo) { redo() }, contentAlignment = Alignment.Center) {
                    UndoGlyph(true, canRedo, Modifier.size(32.dp))
                }
            }
            Box(
                Modifier
                    .background(C.Amber, RoundedCornerShape(8.dp))
                    .clickable { magic() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Mono("MAGIC ENHANCE", color = Color.Black, size = 11.sp, weight = FontWeight.Bold)
            }
            Box(
                Modifier.size(52.dp).pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        compare = true
                        tryAwaitRelease()
                        compare = false
                    })
                },
                contentAlignment = Alignment.Center,
            ) {
                CompareGlyph(compare, Modifier.size(32.dp))
            }
        }

        ClapperStripe(Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(C.Panel).horizontalScroll(rememberScrollState())) {
            Slot("CAMERA", CAMERAS[ci].name, open == 0, false, { open = if (open == 0) -1 else 0 }) { CameraGlyph(CAMERAS[ci].id, it) }
            Slot("LENS", LENSES[li].name, open == 1, false, { open = if (open == 1) -1 else 1 }) { LensGlyph(LENSES[li], it) }
            Slot("FILM", FILMS[fi].name, open == 2, true, { open = if (open == 2) -1 else 2 }) { FilmGlyph(FILMS[fi].family, it) }
            Slot("APERTURA", fmtF(ap), open == 3, false, { open = if (open == 3) -1 else 3 }) { ApertureGlyph(ap, it) }
            Slot("EFFETTI", if (flare + star + sf + apod > 0.01f || Math.abs(cs) > 0.01f) "ON" else "OFF", open == 4, false, { open = if (open == 4) -1 else 4 }) { EffectGlyph(it) }
            Slot("REGOLA", if (adj.on) "ON" else "OFF", open == 5, false, { open = if (open == 5) -1 else 5 }) { AdjustGlyph(adj.on, it) }
            Slot("MASCHERE", if (mSpec.type != MaskType.NONE && loc.on) "ON" else "OFF", open == 6, false, { open = if (open == 6) -1 else 6 }) { AdjustGlyph(mSpec.type != MaskType.NONE && loc.on, it) }
        }
        if (open >= 0) {
            val panel = Modifier.fillMaxWidth().height(300.dp)
            when (open) {
                0 -> GearList(
                    "CAMERA", cameraGroups(), ci, { ci = it }, { CAMERAS[it].name }, { CAMERAS[it].tagline }, panel,
                ) { i, m -> CameraGlyph(CAMERAS[i].id, m) }
                1 -> GearList(
                    "LENS", lensGroups(), li, { li = it; ap = lensAp(LENSES[it].f); sh = LENSES[it].shape },
                    { LENSES[it].name }, { lensSub(LENSES[it]) }, panel,
                ) { i, m -> LensGlyph(LENSES[i], m) }
                2 -> FilmPicker(fi, { fi = it; prefs.film = it }, Modifier.fillMaxWidth().height(190.dp))
                5 -> Column(panel.background(C.Panel)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("LUCE", "COLORE", "DETTAGLIO").forEachIndexed { i, n -> Chip(n, adjTab == i) { adjTab = i } }
                        Chip("AZZERA", false) { adj = Adj() }
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        when (adjTab) {
                            0 -> {
                                FxSlider("ESPOSIZIONE", adj.exp, -1f..1f) { adj = adj.copy(exp = it) }
                                FxSlider("CONTRASTO", adj.con, -1f..1f) { adj = adj.copy(con = it) }
                                FxSlider("ALTE LUCI", adj.hi, -1f..1f) { adj = adj.copy(hi = it) }
                                FxSlider("OMBRE", adj.sh, -1f..1f) { adj = adj.copy(sh = it) }
                                FxSlider("BIANCHI", adj.wh, -1f..1f) { adj = adj.copy(wh = it) }
                                FxSlider("NERI", adj.bl, -1f..1f) { adj = adj.copy(bl = it) }
                            }
                            1 -> {
                                FxSlider("TEMPERATURA", adj.temp, -1f..1f) { adj = adj.copy(temp = it) }
                                FxSlider("TINTA", adj.tint, -1f..1f) { adj = adj.copy(tint = it) }
                                FxSlider("SATURAZIONE", adj.sat, -1f..1f) { adj = adj.copy(sat = it) }
                                FxSlider("VIBRANZA", adj.vib, -1f..1f) { adj = adj.copy(vib = it) }
                            }
                            else -> {
                                FxSlider("NITIDEZZA", adj.sharp, 0f..1f) { adj = adj.copy(sharp = it) }
                                FxSlider("CHIAREZZA", adj.clar, -1f..1f) { adj = adj.copy(clar = it) }
                                FxSlider("DEHAZE", adj.haze, -1f..1f) { adj = adj.copy(haze = it) }
                            }
                        }
                        Mono("TOCCA UN NOME PER AZZERARLO", color = C.Dim, size = 9.sp)
                    }
                }
                6 -> Column(
                    panel.background(C.Panel).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Mono("MASCHERA LOCALE", size = 11.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "OFF" to MaskType.NONE, "SOGGETTO" to MaskType.SUBJECT, "SFONDO" to MaskType.BACKGROUND,
                            "LINEARE" to MaskType.LINEAR, "RADIALE" to MaskType.RADIAL, "PENNELLO" to MaskType.BRUSH,
                        ).forEach { (n, t) ->
                            Chip(n, mSpec.type == t) {
                                mSpec = mSpec.copy(type = t)
                                if (t == MaskType.BRUSH && brushBmp == null) brushBmp = LocalMask.newBrush()
                                maskShow = t != MaskType.NONE
                            }
                        }
                    }
                    if (mSpec.type != MaskType.NONE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("INVERTI", mSpec.invert) { mSpec = mSpec.copy(invert = !mSpec.invert) }
                            Chip(if (maskShow) "MASCHERA ON" else "VEDI MASCHERA", maskShow) { maskShow = !maskShow }
                        }
                        when (mSpec.type) {
                            MaskType.SUBJECT, MaskType.BACKGROUND -> {
                                PctSlider("SOGLIA", mSpec.thr) { mSpec = mSpec.copy(thr = it) }
                                PctSlider("SFUMATURA", (mSpec.feather * 2f).coerceIn(0f, 1f)) { mSpec = mSpec.copy(feather = it * 0.5f) }
                                Mono("USA LA PROFONDITÀ: SOGLIA ALTA = SOLO CIÒ CHE È VICINO", color = C.Dim, size = 9.sp)
                            }
                            MaskType.LINEAR -> Mono("TRASCINA SULLA FOTO: DAL PUNTO PIENO AL PUNTO VUOTO", color = C.Dim, size = 9.sp)
                            MaskType.RADIAL -> {
                                PctSlider("SFUMATURA", (mSpec.feather * 2f).coerceIn(0f, 1f)) { mSpec = mSpec.copy(feather = it * 0.5f) }
                                Mono("TRASCINA SULLA FOTO: PARTI DAL CENTRO, LA DISTANZA È IL RAGGIO", color = C.Dim, size = 9.sp)
                            }
                            else -> {
                                PctSlider("DIMENSIONE", ((mBrushSize - 0.02f) / 0.18f).coerceIn(0f, 1f)) { mBrushSize = 0.02f + it * 0.18f }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip(if (mErase) "GOMMA" else "PENNELLO", mErase) { mErase = !mErase }
                                    Chip("AZZERA", false) { brushBmp = LocalMask.newBrush(); brushVer++ }
                                }
                                Mono("DIPINGI SULLA FOTO LA ZONA DA RITOCCARE", color = C.Dim, size = 9.sp)
                            }
                        }
                        Mono("REGOLAZIONI NELLA MASCHERA", size = 11.sp)
                        FxSlider("LUCE", loc.exp, -1f..1f) { loc = loc.copy(exp = it) }
                        FxSlider("CONTRASTO", loc.con, -1f..1f) { loc = loc.copy(con = it) }
                        FxSlider("LUCI", loc.hi, -1f..1f) { loc = loc.copy(hi = it) }
                        FxSlider("OMBRE", loc.sh, -1f..1f) { loc = loc.copy(sh = it) }
                        FxSlider("TEMPERATURA", loc.temp, -1f..1f) { loc = loc.copy(temp = it) }
                        FxSlider("SATURAZIONE", loc.sat, -1f..1f) { loc = loc.copy(sat = it) }
                        Mono("TOCCA UN NOME PER AZZERARLO", color = C.Dim, size = 9.sp)
                    }
                }
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
                    Mono("TOCCA UN NOME PER AZZERARLO \u00b7 FLARE E STARBURST SULLE LUCI FORTI", color = C.Dim, size = 9.sp)
                }
                else -> Column(panel.background(C.Panel)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("PROFONDITÀ", "DIMENSIONE", "FLARE", "FORMA", "FUOCO").forEachIndexed { i, n ->
                            Chip(n, tab == i) { tab = i }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
                        when (tab) {
                            0 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    DepthButton("PRO", dq == 0, Modifier.weight(1f)) { dq = 0 }
                                    DepthButton("EXTREME", dq == 1, Modifier.weight(1f)) { dq = 1 }
                                    Chip(if (showMap) "MAPPA ON" else "MAPPA", showMap) { showMap = !showMap }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("PENNELLO NITIDO", brush == 1) { brush = if (brush == 1) 0 else 1 }
                                    Chip("PENNELLO SFOCA", brush == 2) { brush = if (brush == 2) 0 else 2 }
                                }
                                FxSlider("LEVIGATURA", smooth, 0f..1f) { smooth = it }
                                Mono("PROFONDITÀ: $depthStatus", color = C.Dim, size = 9.sp)
                                Mono("COL PENNELLO DISEGNI SULLA FOTO PER CORREGGERE LA MAPPA", color = C.Dim, size = 9.sp)
                            }
                            1 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Mono("APERTURA", size = 10.sp)
                                    Mono(fmtF(ap), color = C.Amber, size = 15.sp, weight = FontWeight.Bold)
                                    Chip(if (ap <= 0f) "ACCENDI" else "SPEGNI", ap <= 0f) { ap = if (ap > 0f) 0f else 2.8f }
                                }
                                ApertureRuler(ap, { ap = it }, { apDrag = it })
                                Mono("TRASCINA PER SCEGLIERE DA F/16 A F/1.4", color = C.Dim, size = 9.sp)
                            }
                            2 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                PctSlider("HIGHLIGHT", hi) { hi = it }
                                PctSlider("VIVID", viv) { viv = it }
                                Mono("HIGHLIGHT: LUCI SFOCATE PIÙ BRILLANTI · VIVID: PIÙ COLORE", color = C.Dim, size = 9.sp)
                            }
                            3 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SHAPE_ORDER.forEach { idx ->
                                        Box(
                                            Modifier
                                                .size(56.dp)
                                                .background(Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                                                .border(1.dp, if (sh == idx) C.Amber else C.Line, RoundedCornerShape(10.dp))
                                                .clickable { sh = idx },
                                            contentAlignment = Alignment.Center,
                                        ) { ShapeGlyph(idx, sh == idx, Modifier.size(38.dp)) }
                                    }
                                }
                                Mono("FORMA: " + SHAPES[sh.coerceIn(0, SHAPES.size - 1)], color = C.Dim, size = 9.sp)
                            }
                            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FocusBar(fd, { fd = it })
                                FxSlider("ZONA DI FUOCO", fw * 2f, 0f..1f) { fw = it * 0.5f }
                                Mono("ZONA: STRETTA = SOLO UN PIANO NITIDO, LARGA = TANTA PROFONDITÀ A FUOCO", color = C.Dim, size = 9.sp)
                                Mono("TRASCINA: LE ZONE AMBRA SULLA FOTO SONO A FUOCO", color = C.Dim, size = 9.sp)
                                Mono("O TOCCA LA FOTO PER METTERE A FUOCO COME SU UNA REFLEX", color = C.Dim, size = 9.sp)
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Mono("TOCCA LA FOTO = FUOCO · TIENI PREMUTO = ORIGINALE", color = C.Dim, size = 9.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("DATE", date) { date = !date; prefs.date = date }
                    Chip("CONDIVIDI", false) { export(true) }
                }
            }
        }
    }
}

@Composable
private fun DepthButton(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (on) C.Amber else Color(0xFF262626), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Mono(text, color = if (on) Color.Black else C.Dim, size = 13.sp, weight = FontWeight.Bold)
    }
}

@Composable
private fun PctSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Mono(label, color = if (value > 0f) C.Amber else C.Text, size = 10.sp, modifier = Modifier.width(92.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.Line),
            modifier = Modifier.weight(1f),
        )
        Mono("${Math.round(value * 100f)}", color = C.Text, size = 11.sp, modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun FxSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Mono(
            if (value != 0f) "$label \u00d7" else label, color = if (value != 0f) C.Amber else C.Text, size = 10.sp,
            modifier = Modifier.width(110.dp).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).clickable { onChange(0f) },
        )
        Slider(
            value = value,
            onValueChange = { v -> onChange(if (Math.abs(v) < 0.04f) 0f else v) },   // vicino allo zero si aggancia a 0 (= spento)
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.Line),
            modifier = Modifier.weight(1f),
        )
        Mono(Math.round(value * 100f).toString(), color = C.Text, size = 10.sp, modifier = Modifier.width(34.dp))
    }
}
