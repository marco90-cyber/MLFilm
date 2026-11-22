package com.marco.mlfilm

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Profondità da una sola foto con Depth Anything V2 Small (TFLite, in assets/depth/dav2s.tflite, scaricato dalla CI).
 * Ingresso 518x518x3 in [0,1], uscita 518x518 con valori alti = vicino. Il risultato è normalizzato 0..1 e
 * restituito come bitmap in grigi (1 = vicino). Se il modello manca o fallisce restituisce null e lo shader
 * usa una profondità stimata a gradiente.
 */
object DepthEstimator {
    private var interp: Interpreter? = null
    private var failed = false
    @Volatile var aiWorked: Boolean? = null
        private set

    private fun get(): Interpreter? {
        if (failed) return null
        interp?.let { return it }
        return try {
            val afd = AppCtx.app.assets.openFd("depth/dav2s.tflite")
            val buf = FileInputStream(afd.fileDescriptor).channel
                .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            Interpreter(buf, Interpreter.Options().apply { setNumThreads(4) }).also { interp = it }
        } catch (e: Throwable) {
            failed = true
            aiWorked = false
            null
        }
    }

    /** Passa un'immagine al modello e restituisce la mappa grezza s*s (valori alti = vicino). */
    private fun runModel(it: Interpreter, src: Bitmap, s: Int): FloatArray {
        val sc = Bitmap.createScaledBitmap(src, s, s, true)
        val px = IntArray(s * s)
        sc.getPixels(px, 0, s, 0, 0, s, s)
        if (sc !== src) sc.recycle()
        val inp = ByteBuffer.allocateDirect(s * s * 3 * 4).order(ByteOrder.nativeOrder())
        val fb = inp.asFloatBuffer()
        for (p in px) {
            fb.put(((p shr 16) and 255) / 255f)
            fb.put(((p shr 8) and 255) / 255f)
            fb.put((p and 255) / 255f)
        }
        inp.rewind()
        val out = ByteBuffer.allocateDirect(s * s * 4).order(ByteOrder.nativeOrder())
        it.run(inp, out)
        out.rewind()
        val d = FloatArray(s * s)
        out.asFloatBuffer().get(d)
        return d
    }

    private fun bil(a: FloatArray, s: Int, fx: Float, fy: Float): Float {
        val x = fx.coerceIn(0f, s - 1f); val y = fy.coerceIn(0f, s - 1f)
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = minOf(x0 + 1, s - 1); val y1 = minOf(y0 + 1, s - 1)
        val tx = x - x0; val ty = y - y0
        val top = a[y0 * s + x0] * (1 - tx) + a[y0 * s + x1] * tx
        val bot = a[y1 * s + x0] * (1 - tx) + a[y1 * s + x1] * tx
        return top * (1 - ty) + bot * ty
    }

    private fun toBitmap(d: FloatArray, w: Int): Bitmap {
        val sorted = d.copyOf().also { a -> a.sort() }
        val lo = sorted[(sorted.size * 0.02f).toInt()]
        val hi = sorted[(sorted.size * 0.98f).toInt()]
        val k = 1f / maxOf(hi - lo, 1e-6f)
        val outPx = IntArray(d.size)
        for (i in d.indices) {
            val g = (((d[i] - lo) * k).coerceIn(0f, 1f) * 255f).toInt()
            outPx[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(outPx, w, d.size / w, Bitmap.Config.ARGB_8888)
    }

    /**
     * Un solo calcolo alla volta (l'Interpreter non è thread-safe). Chiamare fuori dal thread principale e da quello GL.
     * Pro: una passata (518x518). Extreme: media con la foto specchiata più 4 riquadri sovrapposti (60% della foto, ognuno
     * rianalizzato a 518x518), riallineati alla mappa globale e fusi in una mappa 1036x1036 con molto più dettaglio sui bordi.
     * Costa circa 6 passate del modello.
     */
    @Synchronized
    fun estimate(src: Bitmap, extreme: Boolean = false): Bitmap? {
        val it = get() ?: return null
        return try {
            val s = it.getInputTensor(0).shape()[1] // 1, S, S, 3
            val g = runModel(it, src, s)
            if (!extreme) {
                aiWorked = true
                return toBitmap(g, s)
            }
            val m = android.graphics.Matrix().apply { preScale(-1f, 1f) }
            val mir = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            val g2 = runModel(it, mir, s)
            if (mir !== src) mir.recycle()
            for (y in 0 until s) for (x in 0 until s) g[y * s + x] = (g[y * s + x] + g2[y * s + (s - 1 - x)]) * 0.5f

            val big = 2 * s
            val acc = FloatArray(big * big)
            val wsum = FloatArray(big * big)
            for (Y in 0 until big) for (X in 0 until big) {
                val i = Y * big + X
                acc[i] = 0.25f * bil(g, s, (X + 0.5f) / big * s - 0.5f, (Y + 0.5f) / big * s - 0.5f)
                wsum[i] = 0.25f
            }
            val cw = (src.width * 0.6f).toInt().coerceAtLeast(16)
            val ch = (src.height * 0.6f).toInt().coerceAtLeast(16)
            for (ty in 0..1) for (tx in 0..1) {
                val cx = if (tx == 0) 0 else src.width - cw
                val cy = if (ty == 0) 0 else src.height - ch
                val crop = Bitmap.createBitmap(src, cx, cy, cw, ch)
                val t = runModel(it, crop, s)
                crop.recycle()
                val x0 = cx.toFloat() / src.width; val tw = cw.toFloat() / src.width
                val y0 = cy.toFloat() / src.height; val th = ch.toFloat() / src.height
                // riallineamento lineare del riquadro alla mappa globale: g ≈ a*t + b
                var st = 0.0; var sg = 0.0; var stt = 0.0; var stg = 0.0
                val n = s * s
                for (j in 0 until s) for (i in 0 until s) {
                    val tv = t[j * s + i].toDouble()
                    val gv = bil(g, s, (x0 + (i + 0.5f) / s * tw) * s - 0.5f, (y0 + (j + 0.5f) / s * th) * s - 0.5f).toDouble()
                    st += tv; sg += gv; stt += tv * tv; stg += tv * gv
                }
                val vt = stt - st * st / n
                if (vt < 1e-9) continue
                val a = ((stg - st * sg / n) / vt).toFloat()
                val b = ((sg - a * st) / n).toFloat()
                val X0 = (x0 * big).toInt().coerceAtLeast(0); val X1 = ((x0 + tw) * big).toInt().coerceAtMost(big - 1)
                val Y0 = (y0 * big).toInt().coerceAtLeast(0); val Y1 = ((y0 + th) * big).toInt().coerceAtMost(big - 1)
                for (Y in Y0..Y1) for (X in X0..X1) {
                    val u = ((X + 0.5f) / big - x0) / tw
                    val v = ((Y + 0.5f) / big - y0) / th
                    val edge = minOf(minOf(u, 1f - u), minOf(v, 1f - v))
                    var w = (edge / 0.15f).coerceIn(0f, 1f)
                    w = w * w * (3f - 2f * w)
                    if (w <= 0f) continue
                    val value = a * bil(t, s, u * s - 0.5f, v * s - 0.5f) + b
                    val i = Y * big + X
                    acc[i] += w * value
                    wsum[i] += w
                }
            }
            for (i in acc.indices) acc[i] /= wsum[i]
            aiWorked = true
            toBitmap(acc, big)
        } catch (e: Throwable) {
            failed = true
            aiWorked = false
            null
        }
    }
}

/** Mappe di profondità delle foto aperte, tenute nella cache dell'app. */
object DepthStore {
    private fun dir(ctx: Context): File = File(ctx.cacheDir, "depth").apply { mkdirs() }

    fun toPng(b: Bitmap): ByteArray = java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    fun save(ctx: Context, name: String, png: ByteArray) {
        try {
            File(dir(ctx), "$name.png").writeBytes(png)
        } catch (_: Exception) {
        }
    }

    fun cleanOld(ctx: Context, olderThan: Long) {
        dir(ctx).listFiles()?.filter { it.lastModified() < olderThan }?.forEach { it.delete() }
    }

    fun file(ctx: Context, name: String): File? = File(dir(ctx), "$name.png").takeIf { it.exists() }

    fun load(ctx: Context, name: String): Bitmap? = file(ctx, name)?.let {
        android.graphics.BitmapFactory.decodeFile(it.absolutePath)?.copy(Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Punto (u, v) del fotogramma finale (origine in alto a sinistra) -> punto della sorgente 0..1 (origine in alto a sinistra),
     * con gli stessi passaggi dello shader (ritaglio, specchio, zoom).
     */
    fun toSource(u: Float, v: Float, look: Look, srcAspect: Float): Pair<Float, Float> {
        var cx = u - 0.5f
        var cy = 0.5f - v
        if (look.mirror) cx = -cx
        if (look.aspect < srcAspect) cx *= look.aspect / srcAspect else cy *= srcAspect / look.aspect
        cx /= look.zoom
        cy /= look.zoom
        return Pair((cx + 0.5f).coerceIn(0f, 1f), (0.5f - cy).coerceIn(0f, 1f))
    }

    /** Profondità (0..1) nel punto a fuoco, mediata su un piccolo intorno. */
    fun depthAtSource(depth: Bitmap, sx: Float, sy: Float): Float {
        val cx = (sx * (depth.width - 1)).toInt()
        val cy = (sy * (depth.height - 1)).toInt()
        var sum = 0f
        var n = 0
        for (dy in -3..3) for (dx in -3..3) {
            val x = (cx + dx).coerceIn(0, depth.width - 1)
            val y = (cy + dy).coerceIn(0, depth.height - 1)
            sum += ((depth.getPixel(x, y) shr 16) and 255) / 255f
            n++
        }
        return sum / n
    }

    fun focusDepth(depth: Bitmap, look: Look, srcAspect: Float, u: Float, v: Float): Float {
        val (sx, sy) = toSource(u, v, look, srcAspect)
        return depthAtSource(depth, sx, sy)
    }
}
