package com.marco.mlfilm

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) {
        val wanted = mutableListOf<String>()
        if (!granted) wanted += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
    }

    var album by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Uri?>(null) }
    var editing by remember { mutableStateOf<Uri?>(null) }
    BackHandler(enabled = album || viewer != null || editing != null) {
        if (editing != null) editing = null else if (viewer != null) viewer = null else album = false
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (!granted) {
            Mono("SERVE IL PERMESSO CAMERA", color = C.Amber, size = 13.sp, modifier = Modifier.align(Alignment.Center))
        } else {
            val v = viewer
            val e = editing
            if (e != null) {
                EditorScreen(e, { editing = null }, { editing = null; viewer = null })
            } else if (v != null) {
                ViewerScreen(v, { viewer = null }, { editing = v })
            } else if (album) {
                AlbumScreen { viewer = it }
            } else {
                CameraScreen { album = true }
            }
        }
    }
}
