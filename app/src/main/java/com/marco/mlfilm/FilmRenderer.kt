package com.marco.mlfilm

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
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
        proc.reset()
        lutTex.reset()
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        oesTex = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        runCatching { st?.release() }
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
        // Se la matrice della SurfaceTexture scambia già gli assi, contiene la rotazione: non va applicata due volte.
        val effRot = if (Math.abs(stm[0]) < 0.5f) 0 else rotation
        val rad = effRot * PI.toFloat() / 180f
        GLES20.glUseProgram(prog.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(prog.loc("uTex"), 0)
        GLES20.glUniformMatrix4fv(prog.loc("uStM"), 1, false, stm, 0)
        applyLook(prog, lutTex, look, 16f, false, srcAspect, surfaceW.toFloat() / surfaceH.toFloat(), cos(rad), sin(rad), 1f, rnd.nextFloat() * 100f)
        drawQuad(prog)
    }

    /** Superficie per una camera aperta con Camera2: stesso schermo di CameraX, rotazione data dal chiamante. */
    fun cameraSurface(w: Int, h: Int, rot: Int): Surface? {
        val tex = st ?: return null
        tex.setDefaultBufferSize(w, h)
        rotation = rot
        srcAspect = if (rot % 180 == 0) w.toFloat() / h else h.toFloat() / w
        return Surface(tex)
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

    private val lutTex = LutTexture()
    private val proc = FilmProcess()

    /** Applica lo stesso effetto dell'anteprima a una foto già scattata. Va chiamata sul thread GL. */
    fun processBitmap(input: Bitmap, l: Look, depth: Bitmap? = null): Bitmap? {
        try {
            return proc.process(input, l, 4096, depth)
        } finally {
            GLES20.glViewport(0, 0, surfaceW, surfaceH)
        }
    }

    companion object {
        fun applyLook(p: GlProgram, lut: LutTexture, l: Look, taps: Float, depthOn: Boolean, srcAspect: Float, outAspect: Float, rotCos: Float, rotSin: Float, flip: Float, seed: Float) {
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
            p.f1("uMirror", if (l.mirror) 1f else 0f)
            p.f2("uLeakPos", 0.92f, 0.08f)
            lut.bind(p, l.lut)
            p.f1("uBokeh", blurRadius(l.fNum))
            p.f1("uFocusD", l.focusDepth)
            p.f2("uFocusPt", l.fpU, 1f - l.fpV)
            p.f1("uShape", l.shape.toFloat())
            p.f1("uSwirl", l.swirl)
            p.f1("uCat", l.cat)
            p.f1("uBoost", 0.25f + l.halation)
            p.f1("uTaps", taps)
            p.f1("uFlare", l.flare)
            p.f1("uStar", l.star)
            p.f1("uApod", l.apod)
            p.f1("uDepthOn", if (depthOn) 1f else 0f)
            p.i1("uDepth", 2)
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
