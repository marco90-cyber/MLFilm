package com.marco.mlfilm

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLUtils
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Disegna l'anteprima della camera passando dallo shader "pellicola".
 * [onGlReady] viene chiamato (sul thread GL) ogni volta che il contesto OpenGL viene (ri)creato.
 */
class FilmRenderer(private val onGlReady: () -> Unit) : GLSurfaceView.Renderer {

    @Volatile var look: Look = Look()
    @Volatile var rotation: Int = 90
    @Volatile var srcAspect: Float = 0.75f

    private var surfaceW = 1
    private var surfaceH = 1
    private var oesTex = 0
    private var st: SurfaceTexture? = null
    private var progOes: GlProgram? = null
    private var progTex: GlProgram? = null
    private val frameAvail = AtomicBoolean(false)
    private val stm = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    private val rnd = java.util.Random()

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f,
            ))
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        progOes = GlProgram(FilmShader.FRAGMENT_OES)
        progTex = null
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        oesTex = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        st = SurfaceTexture(oesTex).also { s ->
            s.setOnFrameAvailableListener { frameAvail.set(true) }
        }
        frameAvail.set(false)
        onGlReady()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = width
        surfaceH = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val tex = st ?: return
        val prog = progOes ?: return
        if (frameAvail.getAndSet(false)) {
            try {
                tex.updateTexImage()
                tex.getTransformMatrix(stm)
            } catch (_: Exception) {
                return
            }
        }
        val rad = rotation * PI.toFloat() / 180f
        GLES20.glUseProgram(prog.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(prog.loc("uTex"), 0)
        GLES20.glUniformMatrix4fv(prog.loc("uStM"), 1, false, stm, 0)
        applyLook(prog, look, srcAspect, surfaceW.toFloat() / surfaceH.toFloat(), cos(rad), sin(rad), 1f, rnd.nextFloat() * 100f)
        drawQuad(prog)
    }

    /** La camera chiede una superficie su cui scrivere i fotogrammi. */
    fun onSurfaceRequest(request: SurfaceRequest, executor: Executor) {
        val tex = st
        if (tex == null) {
            request.willNotProvideSurface()
            return
        }
        val res = request.resolution
        tex.setDefaultBufferSize(res.width, res.height)
        val surface = Surface(tex)
        request.provideSurface(surface, executor) { surface.release() }
        request.setTransformationInfoListener(executor) { info ->
            val rot = info.rotationDegrees
            rotation = rot
            srcAspect = if (rot % 180 == 0) res.width.toFloat() / res.height else res.height.toFloat() / res.width
        }
    }

    /**
     * Applica lo stesso effetto dell'anteprima a una foto già scattata (bitmap in piedi).
     * Va chiamata sul thread GL (glView.queueEvent). Restituisce la foto finita, ritagliata nel formato della camera.
     */
    fun processBitmap(input: Bitmap, l: Look): Bitmap? {
        val maxTex = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        val limit = minOf(maxTex[0], 4096)
        var src = input
        if (maxOf(src.width, src.height) > limit) {
            val k = limit.toFloat() / maxOf(src.width, src.height)
            src = Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true)
        }
        val sa = src.width.toFloat() / src.height
        val oa = l.aspect
        var ow: Int
        var oh: Int
        if (oa < sa) {
            oh = src.height
            ow = Math.round(oh * oa)
        } else {
            ow = src.width
            oh = Math.round(ow / oa)
        }
        val k = limit.toFloat() / maxOf(ow, oh)
        if (k < 1f) {
            ow = (ow * k).toInt()
            oh = (oh * k).toInt()
        }
        ow = maxOf(ow, 2)
        oh = maxOf(oh, 2)

        val prog = progTex ?: GlProgram(FilmShader.FRAGMENT_2D).also { progTex = it }
        val ids = IntArray(2) // 0 = texture foto, 1 = texture destinazione
        GLES20.glGenTextures(2, ids, 0)
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        try {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            setupTex()
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, src, 0)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[1])
            setupTex()
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, ow, oh, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ids[1], 0)
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) return null

            GLES20.glViewport(0, 0, ow, oh)
            GLES20.glUseProgram(prog.id)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glUniform1i(prog.loc("uTex"), 0)
            // la bitmap ha la riga 0 in alto: si inverte la coordinata verticale
            val m = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
            GLES20.glUniformMatrix4fv(prog.loc("uStM"), 1, false, m, 0)
            applyLook(prog, l, sa, ow.toFloat() / oh, 1f, 0f, -1f, rnd.nextFloat() * 100f)
            drawQuad(prog)

            val buf = ByteBuffer.allocateDirect(ow * oh * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, ow, oh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            buf.position(0)
            out.copyPixelsFromBuffer(buf)
            return out
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, fbo, 0)
            GLES20.glDeleteTextures(2, ids, 0)
            GLES20.glViewport(0, 0, surfaceW, surfaceH)
            if (src !== input) src.recycle()
        }
    }

    private fun setupTex() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    companion object {
        fun applyLook(p: GlProgram, l: Look, srcAspect: Float, outAspect: Float, rotCos: Float, rotSin: Float, flip: Float, seed: Float) {
            p.f1("uFlip", flip)
            p.f2("uRot", rotCos, rotSin)
            p.f1("uSrcAspect", srcAspect)
            p.f1("uOutAspect", outAspect)
            p.f1("uZoom", l.zoom)
            p.f1("uDist", l.dist)
            p.f1("uCA", l.ca)
            p.f1("uSoft", l.soft)
            p.f1("uTilt", l.tilt)
            p.f3("uGain", l.gain)
            p.f3("uShadow", l.shadow)
            p.f3("uHigh", l.high)
            p.f1("uSat", l.sat)
            p.f1("uCon", l.con)
            p.f1("uBri", l.bri)
            p.f1("uFade", l.fade)
            p.f1("uVig", l.vig)
            p.f1("uGrain", l.grain)
            p.f1("uHal", l.halation)
            p.f1("uSeed", seed)
            p.f1("uLeak", l.leak)
            p.f2("uLeakPos", 0.92f, 0.08f)
        }
    }

    private fun drawQuad(p: GlProgram) {
        val aPos = GLES20.glGetAttribLocation(p.id, "aPos")
        val aUv = GLES20.glGetAttribLocation(p.id, "aUv")
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }
}
