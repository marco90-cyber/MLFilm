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
    private val rnd = java.util.Random()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
            position(0)
        }

    /** Da chiamare quando il contesto OpenGL viene ricreato. */
    fun reset() {
        prog = null
    }

    fun process(input: Bitmap, l: Look, maxSide: Int): Bitmap? {
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
            GLES20.glUseProgram(p.id)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glUniform1i(p.loc("uTex"), 0)
            // la bitmap ha la riga 0 in alto: si inverte la coordinata verticale
            val m = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
            GLES20.glUniformMatrix4fv(p.loc("uStM"), 1, false, m, 0)
            FilmRenderer.applyLook(p, l, sa, ow.toFloat() / oh, 1f, 0f, -1f, rnd.nextFloat() * 100f)
            drawQuad(p)

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
}
