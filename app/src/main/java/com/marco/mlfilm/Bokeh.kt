package com.marco.mlfilm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Raggio della forma del bokeh a un certo angolo: stesse formule dello shader (indici di SHAPES). */
fun shapeRadius(shape: Int, a: Float): Float {
    val pi = PI.toFloat()
    fun poly(n: Int, ang: Float): Float {
        val k = pi / n
        val m = ((ang % (2f * k)) + 2f * k) % (2f * k)
        return cos(k) / cos(m - k)
    }
    return when (shape) {
        1 -> poly(6, a)
        2 -> poly(5, a)
        3 -> {
            val t = a - pi / 2f
            val s = sin(t)
            val r = 2f - 2f * s + s * sqrt(abs(cos(t))) / (s + 1.4f)
            (r / 3f).coerceIn(0.2f, 1.15f)
        }
        4 -> 0.5f + 0.5f * cos(5f * a)
        5 -> poly(8, a)
        6 -> 0.55f + 0.45f * cos(6f * a)
        8 -> 0.62f + 0.5f * exp(-4f * (1f - cos(a - pi / 2f)))
        10 -> 0.55f + 0.45f * abs(cos(2.5f * a))
        11 -> 0.6f + 0.4f * abs(cos(2f * a))
        12 -> poly(4, a)
        13 -> poly(4, a + pi / 4f)
        else -> 1f
    }
}

/** Icona di una forma di bokeh, disegnata dalla stessa formula usata per sfocare. */
@Composable
fun ShapeGlyph(shape: Int, on: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val m = minOf(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val col = if (on) C.Amber else Color(0xFFE8E8E8)
        val r0 = m * 0.4f
        when (shape) {
            9 -> drawCircle(
                Brush.radialGradient(listOf(col, col.copy(alpha = 0.55f), Color.Transparent), c, r0),
                r0, c,
            )
            7 -> drawCircle(col, r0 * 0.88f, c, style = Stroke(width = m * 0.1f))
            else -> {
                val p = Path()
                val n = 72
                for (i in 0..n) {
                    val a = i * 2f * PI.toFloat() / n
                    // il disegno ha l'angolo 0 a destra e y verso il basso: come nello shader, ma specchiato in verticale
                    val r = r0 * shapeRadius(shape, a)
                    val x = c.x + r * cos(a)
                    val y = c.y - r * sin(a)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, col)
            }
        }
    }
}

private const val F_MAX = 16f
private const val F_MIN = 1.4f
private val F_SPAN = ln(F_MAX / F_MIN)

private fun fToT(f: Float): Float = (ln(F_MAX / f.coerceIn(F_MIN, F_MAX)) / F_SPAN).coerceIn(0f, 1f)
private fun tToF(t: Float): Float = (F_MAX * exp(-t.coerceIn(0f, 1f) * F_SPAN)).coerceIn(F_MIN, F_MAX)

/**
 * Righello dell'apertura da f/16 (sinistra) a f/1.4 (destra), a tacche di 1/3 di stop.
 * [f] = 0 vuol dire spento. Si trascina o si tocca; [onDrag] dice se il dito è giù (per mostrare il fumetto).
 */
@Composable
fun ApertureRuler(f: Float, onChange: (Float) -> Unit, onDrag: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val change by rememberUpdatedState(onChange)
    val drag by rememberUpdatedState(onDrag)
    val pad = 16.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(70.dp)) {
        val w = maxWidth - pad * 2
        for (v in listOf(16, 8, 4, 2)) {
            val t = fToT(v.toFloat())
            Mono("f/$v", color = C.Dim, size = 10.sp, modifier = Modifier.offset(x = pad + w * t - 12.dp, y = 4.dp))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .pointerInput(Unit) {
                    val p = 16.dp.toPx()
                    fun at(x: Float) = tToF((x - p) / (size.width - 2f * p))
                    detectDragGestures(
                        onDragStart = { o -> drag(true); change(at(o.x)) },
                        onDragEnd = { drag(false) },
                        onDragCancel = { drag(false) },
                    ) { ch, _ ->
                        ch.consume()
                        change(at(ch.position.x))
                    }
                }
                .pointerInput(Unit) {
                    val p = 16.dp.toPx()
                    detectTapGestures { o -> change(tToF((o.x - p) / (size.width - 2f * p))) }
                }
        ) {
            val p = pad.toPx()
            val span = size.width - 2f * p
            val top = size.height * 0.45f
            // tacche ogni 1/3 di stop da f/16 verso f/1.4
            val total = (ln(F_MAX / F_MIN) / ln(2f) * 3f)
            var k = 0
            while (k <= total.toInt()) {
                val t = (k / 3f) * ln(2f) / F_SPAN
                if (t <= 1.0001f) {
                    val x = p + span * t
                    val major = k % 3 == 0
                    drawLine(
                        if (major) Color(0xFFEDEDED) else Color(0xFF6A6A6A),
                        Offset(x, top + if (major) 0f else size.height * 0.08f),
                        Offset(x, size.height * 0.85f),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
                k++
            }
            if (f > 0f) {
                val x = p + span * fToT(f)
                drawLine(C.Amber, Offset(x, top - size.height * 0.12f), Offset(x, size.height * 0.92f), strokeWidth = 3.dp.toPx())
            }
        }
    }
}

/** Barra sfumata nero→bianco (lontano→vicino) per scegliere il piano a fuoco. */
@Composable
fun FocusBar(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val change by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                val p = 22.dp.toPx()
                fun at(x: Float) = ((x - p) / (size.width - 2f * p)).coerceIn(0f, 1f)
                detectDragGestures(onDragStart = { o -> change(at(o.x)) }) { ch, _ ->
                    ch.consume()
                    change(at(ch.position.x))
                }
            }
            .pointerInput(Unit) {
                val p = 22.dp.toPx()
                detectTapGestures { o -> change(((o.x - p) / (size.width - 2f * p)).coerceIn(0f, 1f)) }
            }
    ) {
        val p = 22.dp.toPx()
        val barH = 14.dp.toPx()
        val y = size.height / 2f - barH / 2f
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.White), p, size.width - p),
            Offset(p, y), Size(size.width - 2f * p, barH), CornerRadius(barH / 2f),
        )
        val x = p + (size.width - 2f * p) * value.coerceIn(0f, 1f)
        val h = 40.dp.toPx()
        val l = 10.dp.toPx()
        val s = Stroke(width = 2.dp.toPx())
        val tl = Offset(x - h / 2f, size.height / 2f - h / 2f)
        // quattro angoli del mirino attorno al cursore
        for ((sx, sy) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
            val cx = tl.x + h * sx
            val cy = tl.y + h * sy
            val dx = if (sx == 0f) l else -l
            val dy = if (sy == 0f) l else -l
            drawLine(Color.White, Offset(cx, cy), Offset(cx + dx, cy), strokeWidth = s.width)
            drawLine(Color.White, Offset(cx, cy), Offset(cx, cy + dy), strokeWidth = s.width)
        }
        drawRoundRect(Color.White, Offset(x - 5.dp.toPx(), size.height / 2f - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

/** Freccia circolare per annulla (a sinistra) o ripeti (a destra). */
@Composable
fun UndoGlyph(redo: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val col = if (enabled) Color(0xFFEDEDED) else Color(0xFF4A4A4A)
        val m = minOf(size.width, size.height)
        val sw = m * 0.09f
        val fx = if (redo) -1f else 1f
        fun px(x: Float) = size.width / 2f + (x - 0.5f) * m * fx
        val c = Offset(size.width / 2f, size.height / 2f + m * 0.05f)
        drawArc(
            col, startAngle = if (redo) -180f else 0f, sweepAngle = if (redo) -230f else 230f, useCenter = false,
            topLeft = Offset(c.x - m * 0.3f, c.y - m * 0.3f), size = Size(m * 0.6f, m * 0.6f),
            style = Stroke(width = sw),
        )
        // punta della freccia in alto a sinistra (annulla) o a destra (ripeti)
        val tip = Offset(px(0.18f), size.height / 2f - m * 0.28f)
        drawLine(col, tip, Offset(tip.x + fx * m * 0.14f, tip.y - m * 0.02f), strokeWidth = sw)
        drawLine(col, tip, Offset(tip.x + fx * m * 0.02f, tip.y + m * 0.14f), strokeWidth = sw)
    }
}

/** Icona confronto prima/dopo: rettangolo diviso a metà. */
@Composable
fun CompareGlyph(on: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val col = if (on) C.Amber else Color(0xFFEDEDED)
        val m = minOf(size.width, size.height)
        val sw = m * 0.07f
        drawRoundRect(col, Offset(m * 0.1f, m * 0.18f), Size(m * 0.8f, m * 0.64f), CornerRadius(m * 0.08f), style = Stroke(width = sw))
        drawRect(col, Offset(m * 0.5f, m * 0.22f), Size(m * 0.36f, m * 0.56f))
        drawLine(col, Offset(m * 0.5f, m * 0.05f), Offset(m * 0.5f, m * 0.95f), strokeWidth = sw)
    }
}
