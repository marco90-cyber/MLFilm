package com.marco.mlfilm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** Tipo di maschera per le regolazioni locali. */
object MaskType {
    const val NONE = 0
    const val SUBJECT = 1
    const val BACKGROUND = 2
    const val LINEAR = 3
    const val RADIAL = 4
    const val BRUSH = 5
}

/**
 * Parametri di una maschera. Le coordinate sono nella foto sorgente, 0..1 con origine in alto a sinistra.
 * Soggetto/sfondo: [thr] = soglia di profondità (alto = vicino), [feather] = morbidezza.
 * Lineare: dal punto 1 (maschera piena) al punto 2 (vuota). Radiale: centro [x1],[y1], raggio [rad] (frazione dell'altezza).
 */
data class MaskSpec(
    val type: Int = MaskType.NONE,
    val invert: Boolean = false,
    val thr: Float = 0.5f,
    val feather: Float = 0.15f,
    val x1: Float = 0.5f, val y1: Float = 0.3f, val x2: Float = 0.5f, val y2: Float = 0.7f,
    val rad: Float = 0.25f,
)

/** Costruisce la bitmap della maschera (grigi: bianco = regolazione piena), a bassa risoluzione e stirata come la mappa di profondità. */
object LocalMask {
    const val SIZE = 256

    private fun smooth(a: Float, b: Float, x: Float): Float {
        if (b <= a) return if (x >= a) 1f else 0f
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun newBrush(): Bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF000000.toInt()) }

    /** Una pennellata morbida sulla maschera a mano: [erase] la toglie. [r] è il raggio in frazione della larghezza. */
    fun stamp(brush: Bitmap, sx: Float, sy: Float, r: Float, erase: Boolean) {
        val cx = sx * brush.width
        val cy = sy * brush.height
        val rr = (r * brush.width).coerceAtLeast(2f)
        val g = if (erase) 0 else 255
        val c1 = android.graphics.Color.argb(255, g, g, g)
        val c0 = android.graphics.Color.argb(0, g, g, g)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, rr, intArrayOf(c1, c1, c0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(brush).drawCircle(cx, cy, rr, p)
    }

    fun build(spec: MaskSpec, depth: Bitmap?, brush: Bitmap?, srcAspect: Float): Bitmap? {
        if (spec.type == MaskType.NONE) return null
        val n = SIZE
        val out = IntArray(n * n)
        val pix = IntArray(n * n)
        when (spec.type) {
            MaskType.SUBJECT, MaskType.BACKGROUND -> {
                val d = depth ?: return null
                val sm = Bitmap.createScaledBitmap(d, n, n, true)
                sm.getPixels(pix, 0, n, 0, 0, n, n)
                if (sm !== d) sm.recycle()
            }
            MaskType.BRUSH -> {
                val b = brush ?: return null
                val sm = if (b.width == n && b.height == n) b else Bitmap.createScaledBitmap(b, n, n, true)
                sm.getPixels(pix, 0, n, 0, 0, n, n)
                if (sm !== b) sm.recycle()
            }
        }
        val dx = (spec.x2 - spec.x1) * srcAspect
        val dy = spec.y2 - spec.y1
        val len2 = (dx * dx + dy * dy).coerceAtLeast(1e-6f)
        for (j in 0 until n) for (i in 0 until n) {
            val u = (i + 0.5f) / n
            val v = (j + 0.5f) / n
            var m = when (spec.type) {
                MaskType.SUBJECT, MaskType.BACKGROUND -> {
                    val g = (pix[j * n + i] and 0xFF) / 255f
                    val s = smooth(spec.thr - spec.feather, spec.thr + spec.feather, g)
                    if (spec.type == MaskType.SUBJECT) s else 1f - s
                }
                MaskType.LINEAR -> {
                    val t = (((u - spec.x1) * srcAspect) * dx + (v - spec.y1) * dy) / len2
                    1f - smooth(0f, 1f, t)
                }
                MaskType.RADIAL -> {
                    val ex = (u - spec.x1) * srcAspect
                    val ey = v - spec.y1
                    val dist = Math.sqrt((ex * ex + ey * ey).toDouble()).toFloat()
                    1f - smooth(spec.rad * (1f - spec.feather * 2f).coerceIn(0.05f, 1f), spec.rad, dist)
                }
                MaskType.BRUSH -> (pix[j * n + i] and 0xFF) / 255f
                else -> 0f
            }
            if (spec.invert) m = 1f - m
            val g = (m.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            out[j * n + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888)
    }
}
