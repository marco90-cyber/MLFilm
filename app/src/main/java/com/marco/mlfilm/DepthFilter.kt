package com.marco.mlfilm

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Pulisce la mappa di profondità: toglie i valori isolati (mediana 3x3) e poi la leviga con un filtro bilaterale guidato
 * dalla foto, così le foglie e i dettagli fini hanno una profondità uniforme e i bordi restano allineati all'immagine.
 */
object DepthFilter {
    fun smooth(depth: Bitmap, guide: Bitmap, strength: Float): Bitmap {
        val w = depth.width
        val h = depth.height
        val dp = IntArray(w * h)
        depth.getPixels(dp, 0, w, 0, 0, w, h)
        val d = FloatArray(w * h) { (dp[it] and 0xFF) / 255f }

        val g = Bitmap.createScaledBitmap(guide, w, h, true)
        val gp = IntArray(w * h)
        g.getPixels(gp, 0, w, 0, 0, w, h)
        if (g !== guide) g.recycle()
        val lum = FloatArray(w * h) {
            val p = gp[it]
            (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) / 255f
        }

        // 1) mediana 3x3
        val med = FloatArray(w * h)
        val t = FloatArray(9)
        for (y in 0 until h) for (x in 0 until w) {
            var k = 0
            for (dy in -1..1) for (dx in -1..1) {
                t[k++] = d[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            }
            for (i in 1 until 9) { // ordinamento a inserimento
                val v = t[i]
                var j = i - 1
                while (j >= 0 && t[j] > v) { t[j + 1] = t[j]; j-- }
                t[j + 1] = v
            }
            med[y * w + x] = t[4]
        }

        // 2) bilaterale congiunto guidato dalla luminosità della foto
        // su mappe più grandi (Extreme, 1036) il raggio cresce solo con la radice, per non cancellare il dettaglio in più
        val r = ((1f + strength * 5f) * Math.sqrt((w / 518f).toDouble()).toFloat()).roundToInt().coerceAtLeast(1)
        val stride = maxOf(1, r / 4)
        val sSpace = r * 0.55f + 0.5f
        val inv2s = 1f / (2f * sSpace * sSpace)
        val inv2l = 1f / (2f * 0.10f * 0.10f)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val l0 = lum[i]
            var acc = 0f
            var ws = 0f
            var dy = -r
            while (dy <= r) {
                val yy = (y + dy).coerceIn(0, h - 1)
                var dx = -r
                while (dx <= r) {
                    val j = yy * w + (x + dx).coerceIn(0, w - 1)
                    val dl = lum[j] - l0
                    val wt = exp(-(dx * dx + dy * dy) * inv2s - dl * dl * inv2l)
                    acc += med[j] * wt
                    ws += wt
                    dx += stride
                }
                dy += stride
            }
            val v = ((acc / ws) * 255f + 0.5f).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
