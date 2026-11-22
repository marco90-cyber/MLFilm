package com.marco.mlfilm

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay

private val FLASH_MODES = listOf(
    "OFF" to ImageCapture.FLASH_MODE_OFF,
    "AUTO" to ImageCapture.FLASH_MODE_AUTO,
    "ON" to ImageCapture.FLASH_MODE_ON,
)

@Composable
fun CameraScreen(onAlbum: () -> Unit, onCams: () -> Unit = {}, lenses: List<RealLens> = emptyList()) {
    val ctx = LocalContext.current
    val prefs = remember { RollPrefs(ctx) }
    val controller = remember { CameraController() }
    val main = remember { Handler(Looper.getMainLooper()) }

    val ci = 0 // allo scatto: camera e obiettivo neutri, restano solo le pellicole; il resto si sceglie nell'editor
    val li = 0
    var fi by remember { mutableIntStateOf(prefs.film.coerceIn(0, FILMS.size - 1)) }
    var date by remember { mutableStateOf(prefs.date) }
    var pair by remember { mutableStateOf(prefs.pair) }
    var grid by remember { mutableStateOf(prefs.grid) }
    var left by remember { mutableIntStateOf(prefs.left) }
    var roll by remember { mutableIntStateOf(prefs.roll) }
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableIntStateOf(-1) } // -1 chiuso, 0 camera, 1 obiettivo, 2 pellicola
    var pendingShown by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    var rl by remember {
        mutableStateOf(lenses.firstOrNull { it.key == prefs.realLens } ?: lenses.firstOrNull { it.kind == LensProbe.MAIN } ?: lenses.firstOrNull())
    }
    var flashIdx by remember { mutableIntStateOf(0) }
    var ev by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var focusPt by remember { mutableStateOf<Offset?>(null) }
    var focusUV by remember { mutableStateOf(0.5f to 0.5f) }
    val pending = remember { AtomicReference<Bitmap?>(null) }

    val look = remember(ci, li, fi, front, ev, focusUV) {
        val l = buildLook(CAMERAS[ci], LENSES[li], FILMS[fi])
        l.copy(bri = l.bri + ev, mirror = front, fNum = 0f, fpU = focusUV.first, fpV = focusUV.second)
    }
    val cam = CAMERAS[ci]
    val halfPair = false // lo scatto salva solo il negativo: l'half frame si decide dopo
    val stampText = remember { SimpleDateFormat("''yy  MM  dd", Locale.US).format(Date()) }

    LaunchedEffect(ci, pair) {
        pending.getAndSet(null)?.recycle()
        pendingShown = false
    }
    var flashOk by remember { mutableStateOf(false) }
    LaunchedEffect(front) {
        zoom = 1f
        flashOk = false
        repeat(20) {
            delay(250)
            if (controller.hasFlash()) {
                flashOk = true
                return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(focusPt) {
        if (focusPt != null) {
            delay(900)
            focusPt = null
        }
    }

    fun fail() {
        main.post {
            Toast.makeText(ctx, "Foto non salvata", Toast.LENGTH_SHORT).show()
            busy = false
        }
    }

    fun finish(out: Bitmap?, neg: ByteArray?, dep: ByteArray?, consumePose: Boolean) {
        val name = if (out != null) CameraController.saveToGallery(ctx, out) else null
        out?.recycle()
        if (name != null && neg != null) CameraController.saveNegative(ctx, name, neg)
        if (name != null && dep != null) DepthStore.save(ctx, name, dep)
        if (name != null && neg != null) controller.depthFor(ctx, name, neg)
        if (name == null) {
            fail()
            return
        }
        main.post {
            if (consumePose) {
                prefs.left = prefs.left - 1
                left = prefs.left
            }
            pendingShown = false
            busy = false
        }
    }

    fun shoot() {
        if (busy) return
        if (left == 0) {
            prefs.newRoll()
            left = prefs.left
            roll = prefs.roll
            return
        }
        busy = true
        val pairNow = halfPair
        controller.takePhoto(look, date && !pairNow) { bmp, neg, dep ->
            if (bmp == null) {
                fail()
                return@takePhoto
            }
            if (!pairNow) {
                finish(bmp, neg, dep, true)
                return@takePhoto
            }
            val first = pending.getAndSet(null)
            if (first == null) {
                pending.set(bmp)
                main.post {
                    pendingShown = true
                    busy = false
                }
                return@takePhoto
            }
            val combo = try {
                CameraController.composePair(first, bmp)
            } catch (e: Throwable) {
                null
            }
            first.recycle()
            bmp.recycle()
            if (combo != null && date) CameraController.stampDate(combo)
            finish(combo, null, null, true)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !busy) {
            busy = true
            controller.importPhoto(ctx, uri, look, date) { bmp, neg, dep -> finish(bmp, neg, dep, false) }
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        // ---- barra in alto: contatore pose, rullo, opzioni
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.background(Color(0xFFCFC8B4), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text("%02d".format(left), color = Color(0xFF2A2A2A), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Mono("ROLL %02d".format(roll), modifier = Modifier.weight(1f), size = 10.sp)
            if (false) {
                Chip(if (pair) "2-UP" else "1-UP", true) { pair = !pair; prefs.pair = pair }
            }
            Chip("CAM", false) { onCams() }
            Chip("GRID", grid) { grid = !grid; prefs.grid = grid }
            Chip("DATE", date) { date = !date; prefs.date = date }
        }

        // ---- mirino dentro il corpo macchina
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val availW = maxWidth - 16.dp
            val availH = maxHeight - 30.dp
            val byHeight = availH * look.aspect
            val w = if (byHeight < availW) byHeight else availW
            CameraBody(cam.id) {
                Box(Modifier.width(w)) {
                    Viewfinder(look, controller, front, FLASH_MODES[flashIdx].second, rl, {
                        Toast.makeText(ctx, "Lente non disponibile", Toast.LENGTH_SHORT).show()
                        rl = lenses.firstOrNull { it.kind == LensProbe.MAIN } ?: lenses.firstOrNull { !it.cam2 }
                    }, Modifier.fillMaxWidth())

                    // gesti: pizzico = zoom, tocco = messa a fuoco
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, z, _ ->
                                    if (z != 1f) {
                                        controller.zoomBy(z)
                                        zoom = controller.zoomRatio()
                                    }
                                }
                            }
                            .pointerInput(look) {
                                detectTapGestures { off ->
                                    focusPt = off
                                    focusUV = (off.x / size.width).coerceIn(0f, 1f) to (off.y / size.height).coerceIn(0f, 1f)
                                    controller.focusAt(off.x / size.width, off.y / size.height, look)
                                }
                            }
                    )
                    ViewfinderOverlay(grid, focusPt, Modifier.matchParentSize())

                    if (date) {
                        Text(
                            stampText, color = Color(0xFFFF8A1F), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        )
                    }
                    if (zoom > 1.05f) {
                        Mono("%.1fX".format(zoom), color = C.Amber, size = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                    }
                    if (pendingShown) {
                        Mono("1 / 2", color = C.Amber, size = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(10.dp))
                    }
                    Row(
                        Modifier.align(Alignment.TopStart).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Chip("FLIP", front) {
                            front = !front
                            if (front) flashIdx = 0
                        }
                        if (!front && flashOk) {
                            Chip("FLASH " + FLASH_MODES[flashIdx].first, flashIdx != 0) {
                                flashIdx = (flashIdx + 1) % FLASH_MODES.size
                            }
                        }
                    }
                    if (!front && lenses.isNotEmpty()) {
                        LensBar(
                            lenses, rl?.key,
                            { rl = it; prefs.realLens = it.key },
                            { Toast.makeText(ctx, "Lente non disponibile", Toast.LENGTH_SHORT).show() },
                            Modifier.align(Alignment.TopEnd).padding(8.dp),
                        )
                    }
                    ExposureRuler(
                        ev, { ev = it },
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.42f).padding(bottom = 6.dp)
                    )
                }
            }
        }

        // ---- ciak con i tre selettori
        ClapperStripe(Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(C.Panel)) {
            Slot("FILM", FILMS[fi].name, open == 2, true, { open = if (open == 2) -1 else 2 }) { FilmGlyph(FILMS[fi].family, it) }
        }

        if (open < 0) {
            // ---- scatto, album, importa
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clickable { onAlbum() }, contentAlignment = Alignment.Center) {
                    AlbumGlyph(Modifier.size(34.dp))
                }
                val ring = if (busy) Color(0xFF555555) else Color(0xFFEDEDED)
                Box(
                    Modifier
                        .size(72.dp)
                        .border(3.dp, ring, CircleShape)
                        .clickable(enabled = !busy) { shoot() }
                        .padding(7.dp)
                        .background(if (left == 0) C.Amber else ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (left == 0) Mono("NEW", color = Color.Black, size = 11.sp, weight = FontWeight.Bold)
                }
                Box(
                    Modifier.size(52.dp).clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    ImportGlyph(Modifier.size(34.dp))
                }
            }
        } else {
            val panel = Modifier.fillMaxWidth().height(300.dp)
            when (open) {
                else -> FilmPicker(fi, { fi = it; prefs.film = it }, panel)
            }
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Mono(
        text,
        color = if (on) C.Amber else C.Dim,
        size = 10.sp,
        modifier = Modifier
            .background(Color(0x66000000), shape)
            .border(1.dp, if (on) C.Amber else C.Line, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun RowScope.Slot(
    label: String,
    name: String,
    active: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    glyph: @Composable (Modifier) -> Unit,
) {
    Column(
        Modifier.weight(1f).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Mono(label, size = 10.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            color = if (active || accent) C.Amber else C.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        glyph(Modifier.size(44.dp))
    }
}
