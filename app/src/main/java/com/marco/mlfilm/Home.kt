package com.marco.mlfilm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Schermata iniziale: scegli le foto da modificare e rivedi le ultime salvate. */
@Composable
fun HomeScreen(refresh: Int, onPicked: (List<Uri>) -> Unit, onOpenRecent: (Uri) -> Unit) {
    val ctx = LocalContext.current
    var recent by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) {
        recent = withContext(Dispatchers.IO) { PhotoIO.loadRecent(ctx) }
        loaded = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }

    Column(
        Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 14.dp)) {
            Mono("MLFILM", color = C.Amber, size = 26.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Mono("EDITOR DI FOTO A PELLICOLA", size = 11.sp)
        }
        ClapperStripe(Modifier.padding(horizontal = 12.dp))

        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            FilmStripArt(Modifier.fillMaxWidth().height(110.dp).rotate(-4f))
        }

        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(C.Amber, RoundedCornerShape(14.dp))
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Mono("SCEGLI FOTO", color = Color.Black, size = 16.sp, weight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Mono("O CONDIVIDI UNA FOTO VERSO MLFILM", size = 9.sp)
        }

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat((FILMS.size - 1).toString(), "PELLICOLE")
            Stat(CAMERAS.size.toString(), "CAMERE")
            Stat(LENSES.size.toString(), "OBIETTIVI")
            Stat("F1.4–F16", "BOKEH")
        }

        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(C.Line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Mono("ULTIME MODIFICHE", color = C.Text, size = 11.sp)
            if (recent.isNotEmpty()) Mono("${recent.size}", color = C.Amber, size = 11.sp)
        }
        if (loaded && recent.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Mono("NESSUNA FOTO MODIFICATA ANCORA", size = 10.sp)
            }
        } else {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recent) { uri ->
                    val bmp by produceState<ImageBitmap?>(null, uri) {
                        value = PhotoIO.thumb(ctx, uri, 320)?.asImageBitmap()
                    }
                    Box(
                        Modifier
                            .size(96.dp)
                            .background(C.Panel, RoundedCornerShape(8.dp))
                            .border(1.dp, C.Line, RoundedCornerShape(8.dp))
                            .clickable { onOpenRecent(uri) },
                    ) {
                        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Mono(value, color = C.Amber, size = 15.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Mono(label, size = 8.sp)
    }
}

/** Pezzo di pellicola con perforazioni e tre fotogrammi color ambra. */
@Composable
private fun FilmStripArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF050505), Offset.Zero, size)
        drawRect(Color(0xFF2A1A0A), Offset(0f, 0f), Size(w, h * 0.04f))
        val hole = Size(h * 0.07f, h * 0.09f)
        val step = h * 0.16f
        var x = step * 0.4f
        while (x < w) {
            drawRoundRect(Color(0xFF1C1C1C), Offset(x, h * 0.06f), hole, CornerRadius(2f))
            drawRoundRect(Color(0xFF1C1C1C), Offset(x, h * 0.85f), hole, CornerRadius(2f))
            x += step
        }
        val gap = w * 0.02f
        val fw = (w - gap * 4f) / 3f
        val tones = listOf(
            listOf(Color(0xFFF5A623), Color(0xFF7A3B0C)),
            listOf(Color(0xFFFFC46B), Color(0xFF8A4A12)),
            listOf(Color(0xFFD9791E), Color(0xFF4A2308)),
        )
        for (i in 0 until 3) {
            val left = gap + i * (fw + gap)
            drawRoundRect(
                Brush.linearGradient(tones[i], Offset(left, h * 0.2f), Offset(left + fw, h * 0.8f)),
                Offset(left, h * 0.2f), Size(fw, h * 0.6f), CornerRadius(5f),
            )
            drawCircle(Color(0x55FFFFFF), h * 0.07f, Offset(left + fw * 0.7f, h * 0.38f))
        }
    }
}
