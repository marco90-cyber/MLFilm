package com.marco.mlfilm

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Mirino: anteprima live della camera con l'effetto della combinazione scelta. */
@Composable
fun Viewfinder(look: Look, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val main = remember { Handler(Looper.getMainLooper()) }
    var epoch by remember { mutableIntStateOf(0) }
    val renderer = remember { FilmRenderer { main.post { epoch++ } } }
    renderer.look = look

    val glView = remember {
        GLSurfaceView(ctx).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(glView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> {}
            }
        }
        activity.lifecycle.addObserver(obs)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) glView.onResume()
        onDispose {
            activity.lifecycle.removeObserver(obs)
            glView.onPause()
        }
    }

    DisposableEffect(epoch) {
        if (epoch == 0) return@DisposableEffect onDispose { }
        val exec = ContextCompat.getMainExecutor(ctx)
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
            preview.setSurfaceProvider(exec) { req -> renderer.onSurfaceRequest(req, exec) }
            provider.unbindAll()
            provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }, exec)
        onDispose {
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier.aspectRatio(look.aspect),
    )
}
