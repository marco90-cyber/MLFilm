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

    /** Un solo calcolo alla volta (l'Interpreter non è thread-safe). Chiamare fuori dal thread principale e da quello GL. */
    @Synchronized
    fun estimate(src: Bitmap): Bitmap? {
        val it = get() ?: return null
        return try {
            val shape = it.getInputTensor(0).shape() // 1, S, S, 3
            val s = shape[1]
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
            val sorted = d.copyOf().also { a -> a.sort() }
            val lo = sorted[(sorted.size * 0.02f).toInt()]
            val hi = sorted[(sorted.size * 0.98f).toInt()]
            val k = 1f / maxOf(hi - lo, 1e-6f)
            val outPx = IntArray(s * s)
            for (i in d.indices) {
                val g = (((d[i] - lo) * k).coerceIn(0f, 1f) * 255f).toInt()
                outPx[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
            aiWorked = true
            Bitmap.createBitmap(outPx, s, s, Bitmap.Config.ARGB_8888)
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
