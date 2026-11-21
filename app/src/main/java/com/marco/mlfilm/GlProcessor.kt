package com.marco.mlfilm

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread

/** Contesto OpenGL privato (senza schermo) per sviluppare foto già esistenti, ad esempio nell'editor. */
class GlProcessor {
    private val thread = HandlerThread("mlfilm-gl").apply { start() }
    private val handler = Handler(thread.looper)
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var ready = false
    private val proc = FilmProcess()

    /** [cb] viene chiamata sul thread del processore; la bitmap sorgente non viene riciclata. */
    fun process(src: Bitmap, look: Look, maxSide: Int, cb: (Bitmap?) -> Unit) {
        handler.post {
            val out = try {
                if (!ready) init()
                if (ready) proc.process(src, look, maxSide) else null
            } catch (e: Throwable) {
                null
            }
            cb(out)
        }
    }

    private fun init() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        if (!EGL14.eglInitialize(display, v, 0, v, 1)) return
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) || n[0] == 0) return
        context = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        surface = EGL14.eglCreatePbufferSurface(
            display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return
        proc.reset()
        ready = true
    }

    fun release() {
        handler.post {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            display = EGL14.EGL_NO_DISPLAY
            ready = false
            thread.quitSafely()
        }
    }
}
