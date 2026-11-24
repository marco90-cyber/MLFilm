package com.marco.mlfilm

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Applica l'effetto pellicola a una bitmap già in piedi, fuori schermo (texture + FBO).
 * Va usata sul thread che ha il contesto OpenGL corrente.
 */
class FilmProcess {
    private var prog: GlProgram? = null
    private val lutTex = LutTexture()
    private val rnd = java.util.Random()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
            position(0)
        }

    /** Da chiamare quando il contesto OpenGL viene ricreato. */
    fun reset() {
        prog = null
        lutTex.reset()
    }

    fun process(input: Bitmap, l: Look, maxSide: Int, depth: Bitmap? = null, mask: Bitmap? = null): Bitmap? {
        val maxTex = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        val limit = minOf(maxTex[0], maxSide)
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

        val p = prog ?: GlProgram(FilmShader.FRAGMENT_2D).also { prog = it }
        val ids = IntArray(4) // 0 = texture foto, 1 = texture destinazione, 2 = profondità, 3 = maschera
        GLES20.glGenTextures(4, ids, 0)
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
            GLES20.glUseProgram(p.id)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glUniform1i(p.loc("uTex"), 0)
            // la bitmap ha la riga 0 in alto: si inverte la coordinata verticale
            val m = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
            GLES20.glUniformMatrix4fv(p.loc("uStM"), 1, false, m, 0)
            val useDepth = depth != null && l.fNum > 0f
            if (useDepth) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[2])
                setupTex()
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, depth, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            }
            val useMask = mask != null && (l.local.on || l.maskShow)
            if (useMask) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[3])
                setupTex()
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mask, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            }
            val heavy = l.fNum > 0f
            applyLook(p, lutTex, l, if (heavy) (if (ow.toLong() * oh > 1_500_000L) 128f else 80f) else 0f, useDepth, sa, ow.toFloat() / oh, 1f, 0f, -1f, rnd.nextFloat() * 100f)
            p.f1("uMaskOn", if (useMask) 1f else 0f)
            p.f1("uMaskShow", if (l.maskShow) 1f else 0f)
            p.i1("uMask", 3)
            if (heavy && ow.toLong() * oh > 3_000_000L) {
                // a fasce, per non far scattare il watchdog della GPU
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                val band = 384
                var y = 0
                while (y < oh) {
                    GLES20.glScissor(0, y, ow, minOf(band, oh - y))
                    drawQuad(p)
                    GLES20.glFinish()
                    y += band
                }
                GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            } else {
                drawQuad(p)
            }

            val buf = ByteBuffer.allocateDirect(ow * oh * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, ow, oh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            buf.position(0)
            out.copyPixelsFromBuffer(buf)
            return out
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, fbo, 0)
            GLES20.glDeleteTextures(4, ids, 0)
            if (src !== input) src.recycle()
        }
    }

    private fun setupTex() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
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
            p.f1("uFocusW", l.focusW)
            p.f2("uFocusPt", l.fpU, 1f - l.fpV)
            p.f1("uShape", l.shape.toFloat())
            p.f1("uSwirl", l.swirl)
            p.f1("uCat", l.cat)
            p.f1("uBoost", 0.25f + l.halation)
            p.f1("uTaps", taps)
            p.f1("uFlare", l.flare)
            p.f1("uStar", l.star)
            p.f1("uApod", l.apod)
            p.f1("uBokHi", l.hi)
            p.f1("uBokViv", l.viv)
            val a = l.adj
            p.f1("uAdjOn", if (a.on) 1f else 0f)
            p.f1("uAExp", a.exp); p.f1("uACon", a.con); p.f1("uAHi", a.hi); p.f1("uASh", a.sh)
            p.f1("uAWh", a.wh); p.f1("uABl", a.bl); p.f1("uATemp", a.temp); p.f1("uATint", a.tint)
            p.f1("uASat", a.sat); p.f1("uAVib", a.vib); p.f1("uASharp", a.sharp); p.f1("uAClar", a.clar); p.f1("uAHaze", a.haze)
            p.f1("uDepthOn", if (depthOn) 1f else 0f)
            p.i1("uDepth", 2)
            val lc = l.local
            p.f1("uLExp", lc.exp); p.f1("uLCon", lc.con); p.f1("uLHi", lc.hi); p.f1("uLSh", lc.sh); p.f1("uLTemp", lc.temp); p.f1("uLSat", lc.sat)
        }
    }
}
