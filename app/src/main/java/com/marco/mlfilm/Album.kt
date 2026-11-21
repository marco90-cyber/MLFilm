package com.marco.mlfilm

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Legge dalla galleria le foto scattate con MLFilm, dalla più recente. */
fun loadShots(ctx: Context): List<Uri> {
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
            base, arrayOf(MediaStore.Images.Media._ID), sel, args,
            MediaStore.Images.Media.DATE_ADDED + " DESC"
        )?.use { c ->
            val idx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) out += Uri.withAppendedPath(base, c.getLong(idx).toString())
        }
    } catch (_: Exception) {
    }
    return out
}

private suspend fun loadBitmap(ctx: Context, uri: Uri, px: Int): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= 29 && px <= 600) {
            ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > px) s *= 2
            val d = BitmapFactory.Options().apply { inSampleSize = s }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, d) }
        }
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun AlbumScreen(onOpen: (Uri) -> Unit) {
    val ctx = LocalContext.current
    var shots by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shots = withContext(Dispatchers.IO) { loadShots(ctx) }
        loaded = true
    }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono("ALBUM", color = C.Text, size = 14.sp)
            Mono("${shots.size}", color = C.Amber, size = 14.sp)
        }
        if (loaded && shots.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Mono("NESSUNA FOTO ANCORA", size = 12.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shots) { uri ->
                    val bmp by produceState<ImageBitmap?>(null, uri) {
                        value = loadBitmap(ctx, uri, 320)?.asImageBitmap()
                    }
                    Box(Modifier.aspectRatio(1f).background(C.Panel).clickable { onOpen(uri) }) {
                        bmp?.let {
                            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViewerScreen(uri: Uri, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = loadBitmap(ctx, uri, 2048)?.asImageBitmap()
    }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono("< INDIETRO", color = C.Text, size = 13.sp, modifier = Modifier.clickable { onBack() })
            Mono(
                "CONDIVIDI", color = C.Amber, size = 13.sp,
                modifier = Modifier.clickable {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(i, null))
                }
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }
}
