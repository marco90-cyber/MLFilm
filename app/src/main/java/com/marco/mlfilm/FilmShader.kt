package com.marco.mlfilm

import android.opengl.GLES20

/** Shader unico: lo stesso codice disegna l'anteprima (texture della camera) e la foto finale (bitmap). */
object FilmShader {

    const val VERTEX = """
attribute vec4 aPos;
attribute vec2 aUv;
uniform float uFlip;
varying vec2 vUv;
void main() {
    gl_Position = vec4(aPos.x, aPos.y * uFlip, 0.0, 1.0);
    vUv = aUv;
}
"""

    private const val FRAGMENT_BODY = """
precision highp float;
varying vec2 vUv;
uniform SAMPLER uTex;
uniform mat4 uStM;
uniform vec2 uRot;
uniform float uSrcAspect;
uniform float uOutAspect;
uniform float uZoom;
uniform float uDist;
uniform float uCA;
uniform float uSoft;
uniform float uTilt;
uniform vec3 uGain;
uniform vec3 uShadow;
uniform vec3 uHigh;
uniform float uSat;
uniform float uCon;
uniform float uBri;
uniform float uFade;
uniform float uVig;
uniform float uGrain;
uniform float uHal;
uniform float uSeed;
uniform float uLeak;
uniform vec2 uLeakPos;

vec3 fetchC(vec2 c) {
    vec2 b = vec2(c.x * uRot.x - c.y * uRot.y, c.x * uRot.y + c.y * uRot.x) + 0.5;
    vec4 t = uStM * vec4(b, 0.0, 1.0);
    return texture2D(uTex, t.xy).rgb;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 c = vUv - 0.5;
    if (uOutAspect < uSrcAspect) {
        c.x *= uOutAspect / uSrcAspect;
    } else {
        c.y *= uSrcAspect / uOutAspect;
    }
    c /= uZoom;
    vec2 d = vec2(c.x * uSrcAspect, c.y);
    c *= 1.0 + uDist * dot(d, d);

    vec3 col;
    if (uCA > 0.0) {
        col = vec3(fetchC(c * (1.0 + uCA)).r, fetchC(c).g, fetchC(c * (1.0 - uCA)).b);
    } else {
        col = fetchC(c);
    }

    float bt = smoothstep(0.12, 0.45, abs(vUv.y - 0.5)) * uTilt;
    float rad = uSoft * 0.010 + bt * 0.014;
    if (rad > 0.0005 || uHal > 0.0) {
        vec3 acc = vec3(0.0);
        vec3 bl = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            vec2 o = vec2(cos(a), sin(a));
            acc += fetchC(c + o * rad);
            bl += fetchC(c + o * 0.035);
        }
        acc /= 8.0;
        bl /= 8.0;
        col = mix(col, acc, clamp(rad * 70.0, 0.0, 0.85));
        vec3 glow = max(bl - vec3(0.62), vec3(0.0));
        col += glow * uHal * vec3(1.6, 0.9, 0.6);
    }

    col *= uGain * exp2(uBri);
    col = (col - 0.5) * uCon + 0.5;
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(l), col, uSat);
    col += uShadow * (1.0 - l) + uHigh * l;
    col = uFade + col * (1.0 - uFade);

    if (uGrain > 0.0) {
        vec2 gp = floor(vUv * vec2(uOutAspect, 1.0) * 1000.0);
        vec2 sd = vec2(uSeed * 1.7, uSeed * 9.2);
        float n = hash12(gp + sd);
        float amp = uGrain * (1.25 - abs(l - 0.5) * 1.2);
        col += vec3((n - 0.5) * amp);
        col += vec3(hash12(gp + sd + 11.0) - 0.5,
                    hash12(gp + sd + 23.0) - 0.5,
                    hash12(gp + sd + 37.0) - 0.5) * uGrain * 0.18;
    }

    float vr = length(vUv - 0.5) * 1.4142;
    col *= 1.0 - uVig * smoothstep(0.35, 1.0, vr);

    if (uLeak > 0.0) {
        float k = pow(max(0.0, 1.0 - distance(vUv, uLeakPos) * 1.7), 2.4) * uLeak;
        col += k * vec3(1.0, 0.42, 0.12);
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

    /** Versione per la texture della camera (OES). */
    val FRAGMENT_OES = "#extension GL_OES_EGL_image_external : require\n" +
        FRAGMENT_BODY.replace("SAMPLER", "samplerExternalOES")

    /** Versione per le bitmap (foto già scattate). */
    val FRAGMENT_2D = FRAGMENT_BODY.replace("SAMPLER", "sampler2D")
}

/** Programma OpenGL compilato, con cache delle posizioni delle variabili. */
class GlProgram(fragment: String) {
    val id: Int
    private val locs = HashMap<String, Int>()

    init {
        val vs = compile(GLES20.GL_VERTEX_SHADER, FilmShader.VERTEX)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            throw RuntimeException("Link shader fallito: $log")
        }
    }

    fun loc(name: String): Int = locs.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

    fun f1(name: String, v: Float) = GLES20.glUniform1f(loc(name), v)
    fun f2(name: String, a: Float, b: Float) = GLES20.glUniform2f(loc(name), a, b)
    fun f3(name: String, c: Rgb) = GLES20.glUniform3f(loc(name), c.r, c.g, c.b)

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val status = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException("Compilazione shader fallita: $log")
        }
        return s
    }
}
