package com.marco.mlfilm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    "Amber" -> Color(0xFFF5A623)
    "Emerald" -> Color(0xFF2FBF71)
    "Vivid" -> Color(0xFFE5392B)
    "Clarity" -> Color(0xFF3FA7F5)
    "Portrait" -> Color(0xFFF2B6A0)
    "Dusk" -> Color(0xFF9B5DE5)
    "Harbor" -> Color(0xFF2C6FBB)
    "Meadow" -> Color(0xFF8BC34A)
    "Ember" -> Color(0xFFD9441F)
    "Velvet" -> Color(0xFF7B4B94)
    "Sunday" -> Color(0xFFE8C27A)
    "Cinema" -> Color(0xFF1E8A8A)
    "Pastel" -> Color(0xFFF4A6C0)
    "Citrus" -> Color(0xFFD6E02A)
    "Polar" -> Color(0xFF9FD3F0)
    "Rust" -> Color(0xFFA8532B)
    "Mono" -> Color(0xFFCCCCCC)
    "Ink" -> Color(0xFFEEEEEE)
    "Silver" -> Color(0xFFA0A0A0)
    else -> Color(0xFF8B6A45)
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
