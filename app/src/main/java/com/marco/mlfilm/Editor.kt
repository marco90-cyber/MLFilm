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
    var date by remember { mutableStateOf(prefs.date) }
    var open by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    val look = remember(ci, li, fi) { buildLook(CAMERAS[ci], LENSES[li], FILMS[fi]) }
    val src by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadSource(ctx, uri, 1280) }
    }

    LaunchedEffect(src, look) {
        val s = src ?: return@LaunchedEffect
        delay(120)
        val out = suspendCancellableCoroutine<Bitmap?> { cont ->
            proc.process(s, look, 1280) { cont.resume(it) }
        }
        if (out != null) preview = out.asImageBitmap()
    }

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
                            val out = suspendCancellableCoroutine<Bitmap?> { cont ->
                                proc.process(full, look, 4096) { cont.resume(it) }
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
            preview?.let { Image(it, null, Modifier.fillMaxSize().padding(horizontal = 12.dp), contentScale = ContentScale.Fit) }
                ?: Mono("CARICO...", size = 12.sp)
        }
        ClapperStripe(Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(C.Panel)) {
            Slot("CAMERA", CAMERAS[ci].name, open == 0, false, { open = if (open == 0) -1 else 0 }) { CameraGlyph(CAMERAS[ci].id, it) }
            Slot("LENS", LENSES[li].name, open == 1, false, { open = if (open == 1) -1 else 1 }) { LensGlyph(LENSES[li], it) }
            Slot("FILM", FILMS[fi].name, open == 2, true, { open = if (open == 2) -1 else 2 }) { FilmGlyph(FILMS[fi].family, it) }
        }
        if (open >= 0) {
            val panel = Modifier.fillMaxWidth().height(280.dp)
            when (open) {
                0 -> GearGrid("CAMERA", CAMERAS.size, ci, 4, { ci = it }, { CAMERAS[it].name }, panel) { i, m -> CameraGlyph(CAMERAS[i].id, m) }
                1 -> GearGrid("LENS", LENSES.size, li, 4, { li = it }, { LENSES[it].name }, panel) { i, m -> LensGlyph(LENSES[i], m) }
                else -> GearGrid("FILM", FILMS.size, fi, 4, { fi = it }, { FILMS[it].name }, panel) { i, m -> FilmGlyph(FILMS[i].family, m) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Chip("DATE", date) { date = !date }
            }
        }
    }
}
