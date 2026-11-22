package com.marco.mlfilm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colori dell'interfaccia: nero, grigi caldi e ambra. */
object C {
    val Bg = Color(0xFF0B0B0C)
    val Panel = Color(0xFF151515)
    val Line = Color(0xFF2B2B2B)
    val Amber = Color(0xFFF5A623)
    val Text = Color(0xFFEDEDED)
    val Dim = Color(0xFF8A8A8A)
}

/** Etichetta in maiuscolo, monospazio, con le lettere distanziate. */
@Composable
fun Mono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Dim,
    size: TextUnit = 11.sp,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = size,
        fontFamily = FontFamily.Monospace,
        fontWeight = weight,
        letterSpacing = 1.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Striscia diagonale bianca e nera, come sul ciak. */
@Composable
fun ClapperStripe(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        drawRect(Color(0xFFEDEDED))
        val h = size.height
        val w = 26.dp.toPx()
        var x = -h
        while (x < size.width) {
            val p = Path().apply {
                moveTo(x, h)
                lineTo(x + h, 0f)
                lineTo(x + h + w, 0f)
                lineTo(x + w, h)
                close()
            }
            drawPath(p, Color(0xFF111111))
            x += w * 2f
        }
    }
}

// ------------------------------------------------------------------ disegni

private class CamStyle(
    val body: Color,
    val trim: Color,
    val w: Float,
    val h: Float,
    val lenses: Int = 1,
    val hump: Boolean = true,
    val flash: Boolean = false,
)

private fun styleOf(id: String) = when (id) {
    "rf" -> CamStyle(Color(0xFFD9D6CE), Color(0xFF1A1A1A), .9f, .55f)
    "slr" -> CamStyle(Color(0xFF232323), Color(0xFF3F3F3F), .86f, .6f)
    "tlr" -> CamStyle(Color(0xFF2B2B2B), Color(0xFF8A8A8A), .56f, .92f, lenses = 2, hump = false)
    "instant" -> CamStyle(Color(0xFFE8E4DA), Color(0xFFF5A623), .8f, .78f, hump = false, flash = true)
    "toy" -> CamStyle(Color(0xFF2E7DB5), Color(0xFFF2D14B), .8f, .6f, flash = true)
    "half" -> CamStyle(Color(0xFF8C8C8C), Color(0xFF1A1A1A), .8f, .58f)
    "compact" -> CamStyle(Color(0xFFC9C9C9), Color(0xFF444444), .9f, .5f, hump = false, flash = true)
    "dispo" -> CamStyle(Color(0xFFE8B730), Color(0xFF2A2A2A), .8f, .62f, hump = false, flash = true)
    "pinhole" -> CamStyle(Color(0xFF6B4A2E), Color(0xFF1A1A1A), .72f, .7f, hump = false)
    "box" -> CamStyle(Color(0xFF3A3A3A), Color(0xFF8A6A3A), .7f, .8f, hump = false)
    "pano" -> CamStyle(Color(0xFF1F1F1F), Color(0xFFB0B0B0), .96f, .46f)
    else -> CamStyle(Color(0xFF3A3F45), Color(0xFFB0B0B0), .88f, .6f)
}

@Composable
fun CameraGlyph(id: String, modifier: Modifier = Modifier) {
    val st = styleOf(id)
    Canvas(modifier) {
        val bw = size.width * st.w
        val bh = size.height * st.h
        val x = (size.width - bw) / 2f
        val y = (size.height - bh) / 2f + if (st.hump) size.height * 0.05f else 0f
        if (st.hump) {
            drawRoundRect(st.body, Offset(x + bw * .3f, y - size.height * .1f), Size(bw * .28f, size.height * .14f), CornerRadius(4f))
        }
        drawRoundRect(st.body, Offset(x, y), Size(bw, bh), CornerRadius(bh * .12f))
        drawRoundRect(st.trim, Offset(x, y), Size(bw, bh * .2f), CornerRadius(bh * .1f))
        val r = minOf(bw, bh) * (if (st.lenses == 2) .17f else .3f)
        val centers = if (st.lenses == 2) {
            listOf(Offset(x + bw / 2f, y + bh * .38f), Offset(x + bw / 2f, y + bh * .74f))
        } else {
            listOf(Offset(x + bw / 2f, y + bh * .6f))
        }
        for (c in centers) {
            drawCircle(Color(0xFF0E0E0E), r * 1.12f, c)
            drawCircle(Color(0xFF2A2A2A), r * .95f, c)
            drawCircle(Color(0xFF10131A), r * .7f, c)
            drawCircle(Color(0xCC3B4A6B), r * .38f, c)
            drawCircle(Color(0x80FFFFFF), r * .1f, Offset(c.x - r * .2f, c.y - r * .2f))
        }
        if (st.flash) {
            drawRoundRect(Color(0xFFF2F2F2), Offset(x + bw * .08f, y + bh * .26f), Size(bw * .2f, bh * .14f), CornerRadius(3f))
        }
    }
}

@Composable
fun LensGlyph(spec: LensSpec, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val m = minOf(size.width, size.height)
        val r = m * (0.28f + 0.05f * minOf(spec.zoom, 3.2f))
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF0E0E0E), r, c)
        drawCircle(Color(0xFF3A3A3A), r * .92f, c)
        drawCircle(Color(0xFF151515), r * .86f, c)
        drawCircle(C.Amber, r * .66f, c)
        drawCircle(Color(0xFF0B0B0B), r * .6f, c)
        val tint = if (spec.id == "vintage" || spec.id == "soft") Color(0xFF6B5B2A) else Color(0xFF2B3E66)
        drawCircle(
            Brush.radialGradient(listOf(tint, Color(0xFF0A0C12)), c, r * .55f),
            r * .55f, c
        )
        if (spec.id == "fish") {
            drawCircle(Color(0x33FFFFFF), r * .3f, Offset(c.x - r * .15f, c.y - r * .18f))
        }
        drawCircle(Color(0x80FFFFFF), r * .09f, Offset(c.x - r * .22f, c.y - r * .22f))
    }
}

fun familyColor(f: String): Color = when (f) {
    "B&W" -> Color(0xFFB8B8B8)
    "Slide" -> Color(0xFF3FA7F5)
    "Fuji X" -> Color(0xFF2FBF71)
    "Instant" -> Color(0xFFF4A6C0)
    "Instant Pro" -> Color(0xFFE8C27A)
    "Color Neg" -> Color(0xFFF5A623)
    "Negative" -> Color(0xFFD9441F)
    "Vintage Neg" -> Color(0xFF9B5DE5)
    "Print" -> Color(0xFF1E8A8A)
    else -> Color(0xFF888888)
}

@Composable
fun FilmGlyph(family: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width * .42f
        val h = size.height * .82f
        val x = (size.width - w) / 2f
        val y = (size.height - h) / 2f
        drawRoundRect(Color(0xFF1C1C1C), Offset(x, y + h * .08f), Size(w, h * .84f), CornerRadius(w * .15f))
        drawRect(familyColor(family), Offset(x, y + h * .3f), Size(w, h * .34f))
        drawRoundRect(Color(0xFFB8B8B8), Offset(x + w * .12f, y), Size(w * .76f, h * .12f), CornerRadius(4f))
        drawRoundRect(Color(0xFFB8B8B8), Offset(x + w * .12f, y + h * .88f), Size(w * .76f, h * .12f), CornerRadius(4f))
        drawRect(Color(0xFF7A5A3A), Offset(x + w, y + h * .45f), Size(w * .18f, h * .14f))
    }
}

/** Icona "album": due quadrati sovrapposti. */
@Composable
fun AlbumGlyph(modifier: Modifier = Modifier, color: Color = C.Text) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = s * .07f)
        drawRoundRect(color, Offset(s * .28f, s * .14f), Size(s * .56f, s * .56f), CornerRadius(s * .08f), style = st)
        drawRoundRect(color, Offset(s * .16f, s * .28f), Size(s * .56f, s * .56f), CornerRadius(s * .08f), style = st)
    }
}

/** Icona "importa": freccia verso un vassoio. */
@Composable
fun ImportGlyph(modifier: Modifier = Modifier, color: Color = C.Text) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        val sw = s * .07f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = sw)
        drawLine(color, Offset(s * .5f, s * .16f), Offset(s * .5f, s * .6f), strokeWidth = sw)
        drawLine(color, Offset(s * .34f, s * .46f), Offset(s * .5f, s * .62f), strokeWidth = sw)
        drawLine(color, Offset(s * .66f, s * .46f), Offset(s * .5f, s * .62f), strokeWidth = sw)
        drawRoundRect(color, Offset(s * .2f, s * .7f), Size(s * .6f, s * .14f), CornerRadius(s * .04f), style = st)
    }
}

// ------------------------------------------------------------------ corpo macchina

private class BodyStyle(val base: Color, val edge: Color, val dot: Color, val accent: Color, val kind: Int)

private fun bodyOf(id: String) = when (id) {
    "rf" -> BodyStyle(Color(0xFF161616), Color(0xFFB8B8B8), Color(0xFF2C2C2C), Color(0xFFD9D6CE), 0)
    "slr" -> BodyStyle(Color(0xFF1B1B1B), Color(0xFF3A3A3A), Color(0xFF2F2F2F), Color(0xFF8A8A8A), 1)
    "tlr" -> BodyStyle(Color(0xFF121212), Color(0xFF8A8A8A), Color(0xFF262626), Color(0xFFB8B8B8), 2)
    "instant" -> BodyStyle(Color(0xFFE6E1D5), Color(0xFFBDB6A4), Color(0xFFD6D0C0), Color(0xFFF5A623), 3)
    "toy" -> BodyStyle(Color(0xFF2E6FA3), Color(0xFF1F4F78), Color(0xFF3A82BB), Color(0xFFF2D14B), 4)
    "half" -> BodyStyle(Color(0xFF7D7D7D), Color(0xFF4A4A4A), Color(0xFF8E8E8E), Color(0xFF1A1A1A), 0)
    "compact" -> BodyStyle(Color(0xFFB9B9B9), Color(0xFF6A6A6A), Color(0xFFC8C8C8), Color(0xFF3A3A3A), 4)
    "dispo" -> BodyStyle(Color(0xFFD9A726), Color(0xFF8C6A10), Color(0xFFE6B93A), Color(0xFF2A2A2A), 4)
    "pinhole" -> BodyStyle(Color(0xFF5A3D25), Color(0xFF2E1E10), Color(0xFF6B4A2E), Color(0xFFB08A5A), 5)
    "box" -> BodyStyle(Color(0xFF2B2B2B), Color(0xFF6B5030), Color(0xFF383838), Color(0xFF8A6A3A), 5)
    "pano" -> BodyStyle(Color(0xFF141414), Color(0xFFB0B0B0), Color(0xFF282828), Color(0xFFB0B0B0), 0)
    else -> BodyStyle(Color(0xFF181818), Color(0xFF3A3A3A), Color(0xFF2C2C2C), Color(0xFFC8C8C8), 1)
}

/** Corpo della macchina attorno al mirino: colore, finitura e dettagli cambiano con la camera scelta. */
@Composable
fun CameraBody(id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val st = bodyOf(id)
    androidx.compose.foundation.layout.Box(
        modifier
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = 18.dp.toPx()
                drawRoundRect(st.base, Offset.Zero, size, CornerRadius(r))
                val rnd = kotlin.random.Random(7)
                repeat(220) {
                    drawCircle(st.dot, 1.1.dp.toPx(), Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
                }
                drawRoundRect(st.edge, Offset.Zero, size, CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                // cornice nera attorno al mirino
                drawRoundRect(Color(0xFF050505), Offset(8.dp.toPx(), 22.dp.toPx()), Size(w - 16.dp.toPx(), h - 30.dp.toPx()), CornerRadius(6.dp.toPx()))
                // dettagli sulla fascia in alto
                val cy = 11.dp.toPx()
                when (st.kind) {
                    0 -> {
                        drawRoundRect(Color(0xFF0A0A0A), Offset(w * .12f, 5.dp.toPx()), Size(w * .13f, 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                        drawRoundRect(Color(0xFF0A0A0A), Offset(w * .29f, 5.dp.toPx()), Size(w * .09f, 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                        drawCircle(st.accent, 6.dp.toPx(), Offset(w * .88f, cy))
                    }
                    1 -> {
                        val p = Path().apply {
                            moveTo(w * .36f, 20.dp.toPx()); lineTo(w * .42f, 3.dp.toPx())
                            lineTo(w * .58f, 3.dp.toPx()); lineTo(w * .64f, 20.dp.toPx()); close()
                        }
                        drawPath(p, st.edge)
                        drawCircle(st.accent, 5.dp.toPx(), Offset(w * .88f, cy))
                    }
                    2 -> {
                        drawCircle(st.accent, 5.dp.toPx(), Offset(w * .4f, cy))
                        drawCircle(st.accent, 5.dp.toPx(), Offset(w * .6f, cy))
                    }
                    3 -> {
                        drawRect(st.accent, Offset(w * .1f, 6.dp.toPx()), Size(w * .8f, 3.dp.toPx()))
                        drawRect(Color(0xFFE5392B), Offset(w * .1f, 10.dp.toPx()), Size(w * .8f, 2.dp.toPx()))
                    }
                    4 -> {
                        drawRoundRect(Color(0xFFF2F2F2), Offset(w * .1f, 5.dp.toPx()), Size(w * .14f, 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                        drawCircle(st.accent, 5.dp.toPx(), Offset(w * .86f, cy))
                    }
                    else -> {
                        drawRect(st.accent, Offset(w * .3f, 8.dp.toPx()), Size(w * .4f, 3.dp.toPx()))
                    }
                }
            }
            .padding(top = 22.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
    ) { content() }
}

// ------------------------------------------------------------------ ghiera esposizione

/** Righello dell'esposizione da -2 a +2 (passi da 1/3). Si trascina con il dito, doppio tocco per azzerare. */
@Composable
fun ExposureRuler(ev: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val evNow by androidx.compose.runtime.rememberUpdatedState(ev)
    val changeNow by androidx.compose.runtime.rememberUpdatedState(onChange)
    androidx.compose.foundation.layout.Column(
        modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Mono(
            (if (ev > 0.01f) "+" else "") + "%.1f".format(ev),
            color = if (Math.abs(ev) < 0.01f) C.Text else C.Amber,
            size = 10.sp,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        val v = (evNow + dx / size.width * 4f).coerceIn(-2f, 2f)
                        changeNow(Math.round(v * 3f) / 3f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { changeNow(0f) }) }
        ) {
            val steps = 12
            for (i in 0..steps) {
                val x = size.width * i / steps
                val major = i % 3 == 0
                drawLine(
                    Color(0xAAFFFFFF), Offset(x, size.height * (if (major) 0.35f else 0.55f)),
                    Offset(x, size.height * 0.9f), strokeWidth = 1.2.dp.toPx()
                )
            }
            val mx = size.width * (ev + 2f) / 4f
            drawLine(C.Amber, Offset(mx, 0f), Offset(mx, size.height), strokeWidth = 2.5.dp.toPx())
        }
    }
}

/** Diaframma a lamelle: più aperto = foro più grande. */
@Composable
fun ApertureGlyph(f: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val m = minOf(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = m * 0.46f
        drawCircle(Color(0xFF3A3A3A), r, c)
        drawCircle(Color(0xFF151515), r * 0.92f, c)
        val hole = if (f <= 0f) 0.15f else (0.62f - 0.045f * minOf(f, 11f)).coerceIn(0.14f, 0.6f)
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 + 15.0)
            val e = Offset(c.x + (r * 0.92f * Math.cos(a)).toFloat(), c.y + (r * 0.92f * Math.sin(a)).toFloat())
            val s = Offset(c.x + (r * hole * Math.cos(a + 1.0)).toFloat(), c.y + (r * hole * Math.sin(a + 1.0)).toFloat())
            drawLine(Color(0xFF5A5A5A), s, e, strokeWidth = m * 0.035f)
        }
        drawCircle(C.Amber, r * hole, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = m * 0.03f))
    }
}
