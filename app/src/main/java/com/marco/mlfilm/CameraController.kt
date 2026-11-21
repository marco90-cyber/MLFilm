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
import androidx.camera.core.ImageCapture
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
    private val bg = Executors.newSingleThreadExecutor()

    /** Scatta, applica l'effetto e restituisce la foto finita (su un thread in background) oppure null. */
    fun takePhoto(look: Look, dateStamp: Boolean, onResult: (Bitmap?) -> Unit) {
        val ic = imageCapture
        val gl = glView
        val r = renderer
        if (ic == null || gl == null || r == null) {
            onResult(null)
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
                    onResult(null)
                    return
                }
                develop(bmp, look, dateStamp, onResult)
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(null)
            }
        })
    }

    /** Applica l'effetto a una bitmap già in piedi (la bitmap passata viene riciclata). */
    private fun develop(bmp: Bitmap, look: Look, dateStamp: Boolean, onResult: (Bitmap?) -> Unit) {
        val gl = glView
        val r = renderer
        if (gl == null || r == null) {
            bmp.recycle()
            onResult(null)
            return
        }
        gl.queueEvent {
            val out = try {
                r.processBitmap(bmp, look)
            } catch (e: Throwable) {
                null
            }
            bmp.recycle()
            bg.execute {
                if (out != null && dateStamp) stampDate(out)
                onResult(out)
            }
        }
    }

    /** Importa una foto dalla galleria e le applica la combinazione scelta. */
    fun importPhoto(ctx: Context, uri: android.net.Uri, look: Look, dateStamp: Boolean, onResult: (Bitmap?) -> Unit) {
        bg.execute {
            val bmp = try {
                decodeUri(ctx, uri, 4096)
            } catch (e: Throwable) {
                null
            }
            if (bmp == null) onResult(null) else develop(bmp, look, dateStamp, onResult)
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

        /** Salva in Pictures/MLFilm e restituisce true se è andato a buon fine. */
        fun saveToGallery(ctx: Context, bmp: Bitmap): Boolean {
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
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                    try {
                        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                            ?: throw java.io.IOException("stream nullo")
                        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                        resolver.update(uri, done, null, null)
                        true
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        false
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MLFilm")
                    if (!dir.exists() && !dir.mkdirs()) return false
                    val f = File(dir, "$name.jpg")
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}
