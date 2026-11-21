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

/** Collega mirino, scatto e salvataggio. */
class CameraController {
    internal var renderer: FilmRenderer? = null
    internal var glView: GLSurfaceView? = null
    internal var imageCapture: ImageCapture? = null
    @Volatile internal var camera: Camera? = null

    fun hasFlash(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    fun zoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    /** Pizzico con le dita: moltiplica lo zoom attuale. */
    fun zoomBy(factor: Float) {
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
        val c = camera ?: return
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
        try {
            val pt = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(px, py)
            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
        } catch (_: Exception) {
        }
    }
    private val bg = Executors.newSingleThreadExecutor()

    /** Scatta, applica l'effetto e restituisce la foto finita (su un thread in background) oppure null. */
    fun takePhoto(look: Look, dateStamp: Boolean, onResult: (Bitmap?, ByteArray?) -> Unit) {
        val ic = imageCapture
        val gl = glView
        val r = renderer
        if (ic == null || gl == null || r == null) {
            onResult(null, null)
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
                    null
                } finally {
                    image.close()
                }
                if (bmp == null) {
                    onResult(null, null)
                    return
                }
                develop(bmp, look, dateStamp, onResult)
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(null, null)
            }
        })
    }

    /**
     * Applica l'effetto a una bitmap già in piedi (la bitmap passata viene riciclata).
     * Restituisce anche il "negativo": la foto senza effetto, per poterla sviluppare di nuovo dall'album.
     */
    private fun develop(bmp: Bitmap, look: Look, dateStamp: Boolean, onResult: (Bitmap?, ByteArray?) -> Unit) {
        val gl = glView
        val r = renderer
        if (gl == null || r == null) {
            bmp.recycle()
            onResult(null, null)
            return
        }
        gl.queueEvent {
            val out = try {
                r.processBitmap(bmp, look)
            } catch (e: Throwable) {
                null
            }
            bg.execute {
                val neg = try {
                    val o = java.io.ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, o)
                    o.toByteArray()
                } catch (e: Throwable) {
                    null
                }
                bmp.recycle()
                if (out != null && dateStamp) stampDate(out)
                onResult(out, neg)
            }
        }
    }

    /** Importa una foto dalla galleria e le applica la combinazione scelta. */
    fun importPhoto(ctx: Context, uri: android.net.Uri, look: Look, dateStamp: Boolean, onResult: (Bitmap?, ByteArray?) -> Unit) {
        bg.execute {
            val bmp = try {
                decodeUri(ctx, uri, 4096)
            } catch (e: Throwable) {
                null
            }
            if (bmp == null) onResult(null, null) else develop(bmp, look, dateStamp, onResult)
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
