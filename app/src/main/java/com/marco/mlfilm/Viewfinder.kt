package com.marco.mlfilm

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
fun Viewfinder(look: Look, controller: CameraController, front: Boolean, flashMode: Int, lens: RealLens? = null, onLensFail: () -> Unit = {}, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val main = remember { Handler(Looper.getMainLooper()) }
    var epoch by remember { mutableIntStateOf(0) }
    val renderer = remember { FilmRenderer { main.post { epoch++ } } }
    renderer.look = look
    controller.renderer = renderer
    val capture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(95)
            .build()
    }
    controller.imageCapture = capture
    SideEffect { capture.flashMode = flashMode }

    val glView = remember {
        GLSurfaceView(ctx).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }.also { controller.glView = it }
    }

    DisposableEffect(glView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    glView.onPause()
                    controller.hidden?.close()
                    controller.hidden = null
                }
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

    DisposableEffect(epoch, front, lens?.key) {
        if (epoch == 0) return@DisposableEffect onDispose { }
        val exec = ContextCompat.getMainExecutor(ctx)
        val future = ProcessCameraProvider.getInstance(ctx)
        var hc: HiddenCamera? = null
        val useCam2 = !front && lens != null && lens.cam2
        future.addListener({
            val provider = future.get()
            if (useCam2) {
                provider.unbindAll()
                controller.camera = null
                val h = HiddenCamera(activity, lens!!.id, lens.kind == LensProbe.MACRO)
                hc = h
                controller.hidden = h
                h.open(renderer) { main.post { onLensFail() } }
                return@addListener
            }
            controller.hidden = null
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
            preview.setSurfaceProvider(exec) { req -> renderer.onSurfaceRequest(req, exec) }
            provider.unbindAll()
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val cam = provider.bindToLifecycle(activity, selector, preview, capture)
            controller.camera = cam
            if (!front) runCatching { cam.cameraControl.setZoomRatio(lens?.zoom ?: 1f) }
        }, exec)
        onDispose {
            hc?.close()
            if (hc != null && controller.hidden === hc) controller.hidden = null
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier.aspectRatio(look.aspect),
    )
}
