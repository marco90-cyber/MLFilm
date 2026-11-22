package com.marco.mlfilm

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lettura delle foto scelte, salvataggio in galleria e miniature. */
object PhotoIO {
    private val counter = AtomicInteger(0)

    private fun inDir(ctx: Context): File = File(ctx.cacheDir, "in").apply { mkdirs() }

    /**
     * Copia la foto scelta (o condivisa) in una cartella temporanea dell'app: i permessi di lettura
     * di "Condividi" e "Apri con" durano poco, e ogni copia ha un nome unico.
     */
    fun importToCache(ctx: Context, uri: Uri): Uri? = try {
        val name = "IN_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "_" + counter.incrementAndGet()
        val f = File(inDir(ctx), "$name.jpg")
        val ok = ctx.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(f).use { o -> i.copyTo(o) }; true } ?: false
        if (ok) Uri.fromFile(f) else null
    } catch (_: Throwable) {
        null
    }

    /** Svuota le copie temporanee più vecchie di un giorno (e le mappe di profondità collegate). */
    fun cleanCache(ctx: Context) {
        val limit = System.currentTimeMillis() - 24L * 3600L * 1000L
        runCatching { inDir(ctx).listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() } }
        runCatching { DepthStore.cleanOld(ctx, limit) }
    }

    /** Decodifica la foto già in piedi (rispetta l'orientamento EXIF), con il lato lungo al massimo [maxSide]. */
    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2
        val d = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, d) }
        if (bmp == null) null else {
            val orient = try {
                ctx.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val m = Matrix()
            when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (m.isIdentity) bmp else {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r !== bmp) bmp.recycle()
                r
            }
        }
    } catch (_: Throwable) {
        null
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

    /** Salva in Pictures/MLFilm e restituisce l'indirizzo della foto salvata, oppure null se fallisce. */
    fun saveToGallery(ctx: Context, bmp: Bitmap): Uri? {
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
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MLFilm")
                if (!dir.exists() && !dir.mkdirs()) return null
                val f = File(dir, "$name.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                Uri.fromFile(f)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Ultime foto salvate da MLFilm nella galleria, dalla più recente. */
    fun loadRecent(ctx: Context, limit: Int = 30): List<Uri> {
        val out = ArrayList<Uri>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sel: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?"
            args = arrayOf("Pictures/MLFilm%")
        } else {
            @Suppress("DEPRECATION")
            sel = MediaStore.Images.Media.DATA + " LIKE ?"
            args = arrayOf("%/Pictures/MLFilm/%")
        }
        try {
            ctx.contentResolver.query(
                base, arrayOf(MediaStore.Images.Media._ID), sel, args, MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { c ->
                val idx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && out.size < limit) out += Uri.withAppendedPath(base, c.getLong(idx).toString())
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Miniatura quadrata di una foto. */
    suspend fun thumb(ctx: Context, uri: Uri, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= 29 && uri.scheme != "file") {
                ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
            } else {
                decode(ctx, uri, px)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
