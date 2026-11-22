package com.marco.mlfilm

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Risultato di uno scatto: foto sviluppata, negativo (JPEG) e mappa di profondità (PNG). */
typealias ShotCb = (Bitmap?, ByteArray?, ByteArray?) -> Unit

/** Collega mirino, scatto e salvataggio. */
class CameraController {
    internal var renderer: FilmRenderer? = null
    internal var glView: GLSurfaceView? = null
    internal var imageCapture: ImageCapture? = null
    @Volatile internal var camera: Camera? = null
    @Volatile internal var hidden: HiddenCamera? = null
    @Volatile var lastError: String = ""

    fun hasFlash(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    fun zoomRatio(): Float = hidden?.zoom ?: camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    /** Pizzico con le dita: moltiplica lo zoom attuale. */
    fun zoomBy(factor: Float) {
        hidden?.let { it.zoomBy(factor); return }
        val c = camera ?: return
        val st = c.cameraInfo.zoomState.value ?: return
        val r = (st.zoomRatio * factor).coerceIn(st.minZoomRatio, st.maxZoomRatio)
        c.cameraControl.setZoomRatio(r)
    }

    /**
     * Tocco sul mirino (u, v da 0 a 1, origine in alto a sinistra): converte il punto nello spazio del sensore
     * seguendo gli stessi passaggi dello shader (ritaglio, zoom, rotazione) e chiede messa a fuoco ed esposizione.
     */
    fun focusAt(u: Float, v: Float, look: Look) {
        val c = camera
        if (c == null && hidden == null) return
        val r = renderer ?: return
        val srcA = r.srcAspect
        var cx = u - 0.5f
        var cy = 0.5f - v
        if (look.mirror) cx = -cx
        if (look.aspect < srcA) cx *= look.aspect / srcA else cy *= srcA / look.aspect
        cx /= look.zoom
        cy /= look.zoom
        val th = Math.toRadians(r.rotation.toDouble())
        val bx = (cx * Math.cos(th) - cy * Math.sin(th)).toFloat()
        val by = (cx * Math.sin(th) + cy * Math.cos(th)).toFloat()
        val px = (bx + 0.5f).coerceIn(0f, 1f)
        val py = (0.5f - by).coerceIn(0f, 1f)
        if (c == null) {
            hidden?.focusAt(px, py)
            return
        }
        try {
            val pt = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(px, py)
            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
        } catch (_: Exception) {
        }
    }
    private val bg = Executors.newSingleThreadExecutor()
    private val depthExec = Executors.newSingleThreadExecutor()

    /** Scatta, applica l'effetto e restituisce la foto finita (su un thread in background) oppure null. */
    fun takePhoto(look: Look, dateStamp: Boolean, onResult: ShotCb) {
        val hc = hidden
        if (hc != null) {
            lastError = ""
            hc.capture { bytes, rot ->
                bg.execute {
                    if (bytes == null) lastError = "la lente (camera2) non ha restituito l'immagine"
                    val bmp = try { if (bytes == null) null else decodeUpright(bytes, rot, 4096) } catch (e: Throwable) { lastError = "decodifica: ${e.message}"; null }
                    if (bmp == null) onResult(null, null, null) else develop(bmp, look, dateStamp, onResult)
                }
            }
            return
        }
        val ic = imageCapture
        val gl = glView
        val r = renderer
        lastError = ""
        if (ic == null || gl == null || r == null) {
            lastError = "anteprima non pronta (camera non ancora aperta)"
            onResult(null, null, null)
            return
        }
        ic.takePicture(bg, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = try {
                    val rot = image.imageInfo.rotationDegrees
                    val buf = image.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    decodeUpright(bytes, rot, 4096)
                } catch (e: Throwable) {
                    lastError = "decodifica: ${e.message}"
                    null
                } finally {
                    image.close()
                }
                if (bmp == null) {
                    onResult(null, null, null)
                    return
                }
                develop(bmp, look, dateStamp, onResult)
            }

            override fun onError(exception: ImageCaptureException) {
                lastError = "CameraX errore ${exception.imageCaptureError}: ${exception.message}"
                onResult(null, null, null)
            }
        })
    }

    /**
     * Applica l'effetto a una bitmap già in piedi (la bitmap passata viene riciclata).
     * Restituisce anche il "negativo": la foto senza effetto, per poterla sviluppare di nuovo dall'album.
     */
    private fun develop(bmp0: Bitmap, look: Look, dateStamp: Boolean, onResult: ShotCb) {
        try {
            val bmp = if (look.mirror) {
                val m = Matrix().apply { preScale(-1f, 1f) }
                Bitmap.createBitmap(bmp0, 0, 0, bmp0.width, bmp0.height, m, true).also { if (it !== bmp0) bmp0.recycle() }
            } else bmp0
            val o = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 94, o)
            val shown = negativeLook(bmp)
            bmp.recycle()
            onResult(shown, o.toByteArray(), null)
        } catch (e: Throwable) {
            lastError = "sviluppo: ${e.javaClass.simpleName} ${e.message}"
            onResult(null, null, null)
        }
    }

    /** Calcola la profondità del negativo in background e la salva accanto ad esso. */
    fun depthFor(ctx: Context, name: String, jpg: ByteArray) {
        depthExec.execute {
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                val b = BitmapFactory.decodeByteArray(jpg, 0, jpg.size, o) ?: return@execute
                val d = DepthEstimator.estimate(b)
                b.recycle()
                if (d != null) {
                    DepthStore.save(ctx, name, DepthStore.toPng(d))
                    d.recycle()
                }
            } catch (_: Throwable) {
            }
        }
    }

    /** Importa una foto dalla galleria e le applica la combinazione scelta. */
    fun importPhoto(ctx: Context, uri: android.net.Uri, look: Look, dateStamp: Boolean, onResult: ShotCb) {
        bg.execute {
            val bmp = try {
                decodeUri(ctx, uri, 4096)
            } catch (e: Throwable) {
                null
            }
            if (bmp == null) onResult(null, null, null) else develop(bmp, look, dateStamp, onResult)
        }
    }

    private fun decodeUri(ctx: Context, uri: android.net.Uri, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2
        val d = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, d) } ?: return null
        val orient = ctx.contentResolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
            )
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        val rot = when (orient) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (rot == 0) return bmp
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (r !== bmp) bmp.recycle()
        return r
    }

    private fun decodeUpright(bytes: ByteArray, rot: Int, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2
        val d = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, d) ?: return null
        if (rot == 0) return bmp
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (r !== bmp) bmp.recycle()
        return r
    }

    companion object {
        /** L'aspetto di un negativo a colori ancora da sviluppare: colori invertiti su base arancione. */
        fun negativeLook(src: Bitmap): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val cm = android.graphics.ColorMatrix(floatArrayOf(
                -0.62f, 0f, 0f, 0f, 232f,
                0f, -0.55f, 0f, 0f, 150f,
                0f, 0f, -0.45f, 0f, 86f,
                0f, 0f, 0f, 1f, 0f,
            ))
            val p = Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(cm) }
            Canvas(out).drawBitmap(src, 0f, 0f, p)
            return out
        }

        /** Due fotogrammi verticali affiancati, come una stampa half frame. */
        fun composePair(a: Bitmap, b: Bitmap): Bitmap {
            val gap = (a.width * 0.02f).toInt()
            val out = Bitmap.createBitmap(a.width + gap + b.width, maxOf(a.height, b.height), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(0xFF0A0A0A.toInt())
            c.drawBitmap(a, 0f, 0f, null)
            c.drawBitmap(b, (a.width + gap).toFloat(), 0f, null)
            return out
        }

        /** Data arancione in basso a destra, come sulle vecchie compatte. */
        fun stampDate(bmp: Bitmap) {
            val c = Canvas(bmp)
            val text = SimpleDateFormat("''yy  MM  dd", Locale.US).format(Date())
            val size = minOf(bmp.width, bmp.height) * 0.034f
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = size
                color = 0x66FF6A00
                maskFilter = android.graphics.BlurMaskFilter(size * 0.25f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = size
                color = 0xEEFF8A1F.toInt()
                textAlign = Paint.Align.RIGHT
            }
            val x = bmp.width - size * 1.1f
            val y = bmp.height - size * 1.2f
            c.drawText(text, x, y, glow)
            c.drawText(text, x, y, p)
        }

        /** Salva in Pictures/MLFilm e restituisce il nome del file (senza estensione), oppure null se fallisce. */
        fun saveToGallery(ctx: Context, bmp: Bitmap): String? {
            val name = "MLFilm_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            return try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MLFilm")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = ctx.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                    try {
                        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                            ?: throw java.io.IOException("stream nullo")
                        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                        resolver.update(uri, done, null, null)
                        name
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        null
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MLFilm")
                    if (!dir.exists() && !dir.mkdirs()) return null
                    val f = File(dir, "$name.jpg")
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                    name
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun shotDir(ctx: Context): File = File(ctx.filesDir, "shots").apply { mkdirs() }

        /** Il negativo da sviluppare resta dentro l'app (non va nella galleria del telefono). Restituisce il nome. */
        fun saveShot(ctx: Context, bmp: Bitmap): String? {
            val name = "MLFilm_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            return try {
                FileOutputStream(File(shotDir(ctx), "$name.jpg")).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                name
            } catch (e: Exception) {
                null
            }
        }

        /** Negativi dentro l'app, dal più recente: (file, data). */
        fun listShots(ctx: Context): List<File> =
            (shotDir(ctx).listFiles { f -> f.extension == "jpg" } ?: emptyArray()).sortedByDescending { it.lastModified() }

        /** Elimina un negativo e i suoi file collegati. */
        fun deleteShot(ctx: Context, name: String) {
            runCatching { File(shotDir(ctx), "$name.jpg").delete() }
            runCatching { File(negDir(ctx), "$name.jpg").delete() }
            runCatching { DepthStore.file(ctx, name)?.delete() }
        }

        private fun negDir(ctx: Context): File = File(ctx.filesDir, "negatives").apply { mkdirs() }

        /** Conserva il negativo (foto senza effetto) accanto alla foto salvata. */
        fun saveNegative(ctx: Context, name: String, bytes: ByteArray) {
            try {
                File(negDir(ctx), "$name.jpg").writeBytes(bytes)
            } catch (_: Exception) {
            }
        }

        fun negativeFile(ctx: Context, name: String): File? =
            File(negDir(ctx), "$name.jpg").takeIf { it.exists() }
    }
}
