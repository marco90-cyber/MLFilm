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
uniform float uMirror;
uniform vec2 uLeakPos;
uniform sampler2D uLut;
uniform float uLutOn;
uniform sampler2D uDepth;
uniform float uDepthOn;
uniform float uBokeh;
uniform float uFocusD;
uniform float uFocusW;
uniform vec2 uFocusPt;
uniform float uShape;
uniform float uSwirl;
uniform float uCat;
uniform float uBoost;
uniform float uTaps;
uniform float uFlare;
uniform float uStar;
uniform float uApod;
uniform float uBokHi;
uniform float uBokViv;

float hash12(vec2 p);

vec2 srcUv(vec2 c) {
    vec2 b = vec2(c.x * uRot.x - c.y * uRot.y, c.x * uRot.y + c.y * uRot.x) + 0.5;
    return (uStM * vec4(b, 0.0, 1.0)).xy;
}

vec3 fetchC(vec2 c) {
    return texture2D(uTex, srcUv(c)).rgb;
}

// da coordinate del fotogramma finale a coordinate centrate della sorgente (ritaglio, specchio, zoom, distorsione)
vec2 toC(vec2 uv) {
    vec2 c = uv - 0.5;
    if (uOutAspect < uSrcAspect) {
        c.x *= uOutAspect / uSrcAspect;
    } else {
        c.y *= uSrcAspect / uOutAspect;
    }
    if (uMirror > 0.5) c.x = -c.x;
    c /= uZoom;
    vec2 d = vec2(c.x * uSrcAspect, c.y);
    return c * (1.0 + uDist * dot(d, d));
}

// profondità: 1 = vicino, 0 = lontano. Senza mappa si usa un gradiente attorno al punto a fuoco.
float depthAt(vec2 c, vec2 uv) {
    if (uDepthOn > 0.5) return texture2D(uDepth, srcUv(c)).r;
    return 1.0 - clamp(length((uv - uFocusPt) * vec2(uOutAspect, 1.0)) * 1.8, 0.0, 1.0);
}

float shapeR(float a) {
    if (uShape < 0.5) return 1.0;
    if (uShape < 1.5) {
        float k = 3.14159265 / 6.0;
        return cos(k) / cos(mod(a, 2.0 * k) - k);
    }
    if (uShape < 2.5) {
        float k = 3.14159265 / 5.0;
        return cos(k) / cos(mod(a, 2.0 * k) - k);
    }
    if (uShape < 3.5) {
        float t = a - 1.5707963;
        float s = sin(t);
        float r = 2.0 - 2.0 * s + s * sqrt(abs(cos(t))) / (s + 1.4);
        return clamp(r / 3.0, 0.2, 1.15);
    }
    if (uShape < 4.5) return 0.5 + 0.5 * cos(5.0 * a);
    if (uShape < 5.5) {
        float k = 3.14159265 / 8.0;
        return cos(k) / cos(mod(a, 2.0 * k) - k);
    }
    if (uShape < 6.5) return 0.55 + 0.45 * cos(6.0 * a);
    if (uShape < 7.5) return 1.0;
    if (uShape > 8.5) {
        if (uShape < 9.5) return 1.0;
        if (uShape < 10.5) return 0.55 + 0.45 * abs(cos(2.5 * a));
        if (uShape < 11.5) return 0.6 + 0.4 * abs(cos(2.0 * a));
        float k4 = 3.14159265 / 4.0;
        float a4 = (uShape < 12.5) ? a : a + k4;
        return cos(k4) / cos(mod(a4, 2.0 * k4) - k4);
    }
    float t = a - 1.5707963;
    return 0.62 + 0.5 * exp(-4.0 * (1.0 - cos(t)));
}

vec3 bokehBlur(vec2 uv, vec2 c0, vec3 col0) {
    float d0 = depthAt(c0, uv);
    float fk = 1.7 - 1.0 * uApod;
    float coc0 = uBokeh * clamp(max(abs(d0 - uFocusD) - uFocusW, 0.0) * fk, 0.0, 1.0);
    vec3 acc = col0 * col0;   // somma in luce lineare (gamma ~2): le luci sfocate brillano invece di ingrigire
    float wsum = 1.0;
    float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy + vec2(uSeed * 0.37), vec2(0.06711056, 0.00583715))));
    vec2 toCen = uv - 0.5;
    float cl = length(toCen);
    vec2 dir = cl > 0.0001 ? toCen / cl : vec2(1.0, 0.0);
    // raggio utile locale (come il "tile max CoC" dei DoF dei motori): quanto lontano arriva davvero la sfocatura
    // di un oggetto vicino fino a questo pixel. I campioni si concentrano li' invece che su tutto il raggio massimo,
    // cosi' il bordo della sfocatura in primo piano e' liscio e senza puntinatura.
    float rMax = coc0;
    for (int k = 0; k < 20; k++) {
        float pa = float(k) * 2.39996323 + jit * 6.2831853;
        float prr = sqrt((float(k) + 0.5) / 20.0);
        vec2 puv = uv + prr * vec2(cos(pa), sin(pa)) * uBokeh * vec2(1.0 / uOutAspect, 1.0);
        float dp = depthAt(toC(puv), puv);
        float cp = uBokeh * clamp(max(abs(dp - uFocusD) - uFocusW, 0.0) * fk, 0.0, 1.0);
        float dr = prr * uBokeh;
        if (cp >= dr * 0.8 && dp >= d0 - 0.03) rMax = max(rMax, dr + uBokeh * 0.12);
    }
    rMax = min(rMax, uBokeh);
    if (rMax < 0.0006) return col0;
    float edgeW = 0.0025 + 0.05 * rMax;
    for (int i = 0; i < 128; i++) {
        if (float(i) >= uTaps) break;
        float fi = float(i) + jit;
        float r = sqrt((fi + 0.5) / uTaps);
        float a = fi * 2.39996323;
        float sr = shapeR(a);
        vec2 p = r * sr * vec2(cos(a), sin(a));
        if (uSwirl > 0.0) {
            float ang = uSwirl * cl * 2.2;
            p = vec2(p.x * cos(ang) - p.y * sin(ang), p.x * sin(ang) + p.y * cos(ang));
            p -= dir * dot(p, dir) * (0.35 * clamp(cl * 1.6, 0.0, 1.0));
        }
        if (uCat > 0.0) {
            p -= dir * dot(p, dir) * (uCat * clamp(cl * 1.5, 0.0, 0.75));
        }
        float dist = r * rMax;
        vec2 suv = uv + p * rMax * vec2(1.0 / uOutAspect, 1.0);
        vec2 sc = toC(suv);
        float ds = depthAt(sc, suv);
        float cocS = uBokeh * clamp(max(abs(ds - uFocusD) - uFocusW, 0.0) * fk, 0.0, 1.0);
        float w;
        if (ds >= d0 - 0.03) {
            w = smoothstep(dist - edgeW, dist + edgeW, max(cocS, coc0));
        } else {
            w = smoothstep(dist - edgeW, dist + edgeW, coc0);
            // il pixel e' in primo piano e il campione sta dietro: lo sfondo nitido non deve trasparire (fantasmi)
            if (d0 > uFocusD + uFocusW) w *= 0.55;
        }
        if (w <= 0.0) continue;
        vec3 sc3 = fetchC(sc);
        float lum = dot(sc3, vec3(0.299, 0.587, 0.114));
        sc3 = clamp(mix(vec3(lum), sc3, 1.0 + uBokViv * 1.5), 0.0, 1.0);
        w *= 1.0 + uBoost * smoothstep(0.72, 1.0, lum) * 4.0;
        w *= 1.0 + uBokHi * smoothstep(0.55, 1.0, lum) * 7.0;
        if (uShape > 6.5 && uShape < 7.5) w *= 0.15 + smoothstep(0.45, 0.72, r);
        if (uShape > 8.5 && uShape < 9.5) w *= exp(-2.5 * r * r);
        w *= 1.0 - uApod * 0.9 * r * r;
        acc += sc3 * sc3 * w;
        wsum += w;
    }
    return sqrt(acc / wsum);
}

// LUT 3D 13x13x13 impaginata in una striscia 169x13 (fetta = blu, x = rosso, y = verde).
vec3 lutFetch(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float b = c.b * 12.0;
    float b0 = floor(b);
    float b1 = min(b0 + 1.0, 12.0);
    float f = b - b0;
    float y = (0.5 + c.g * 12.0) / 13.0;
    float rx = 0.5 + c.r * 12.0;
    vec3 a = texture2D(uLut, vec2((b0 * 13.0 + rx) / 169.0, y)).rgb;
    vec3 d = texture2D(uLut, vec2((b1 * 13.0 + rx) / 169.0, y)).rgb;
    return mix(a, d, f);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

uniform sampler2D uMask;
uniform float uMaskOn;
uniform float uMaskShow;
uniform float uLExp, uLCon, uLHi, uLSh, uLTemp, uLSat;

// regolazioni locali: stessa logica di REGOLA ma ridotta alle voci principali
vec3 localCol(vec3 col) {
    col *= exp2(uLExp * 1.5);
    col.r *= 1.0 + uLTemp * 0.25;
    col.b *= 1.0 - uLTemp * 0.25;
    col = max(col, vec3(0.0));
    float l = clamp(dot(col, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    col *= 1.0 + uLSh * 0.6 * (1.0 - l) * (1.0 - l);
    col *= 1.0 + uLHi * 0.4 * l * l;
    col = (col - 0.5) * (1.0 + uLCon * 0.8) + 0.5;
    float l2 = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(l2), col, max(0.0, 1.0 + uLSat));
    return max(col, vec3(0.0));
}

uniform float uAdjOn;
uniform float uAExp, uACon, uAHi, uASh, uAWh, uABl, uATemp, uATint, uASat, uAVib, uASharp, uAClar, uAHaze;

// regolazioni manuali (luce, colore, dettaglio) applicate prima del look pellicola
vec3 adjustCol(vec3 col, vec2 c) {
    if (uASharp > 0.001 || abs(uAClar) > 0.001) {
        vec3 bs = vec3(0.0);
        vec3 bb = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            vec2 o = vec2(cos(a), sin(a));
            bs += fetchC(c + o * 0.0025);
            bb += fetchC(c + o * 0.012);
        }
        bs /= 8.0;
        bb /= 8.0;
        float l0 = dot(col, vec3(0.299, 0.587, 0.114));
        col += (col - bs) * uASharp * 2.0;
        col += (col - bb) * uAClar * 1.2 * (1.0 - abs(l0 * 2.0 - 1.0));
    }
    col *= exp2(uAExp * 1.5);
    col.r *= 1.0 + uATemp * 0.25;
    col.b *= 1.0 - uATemp * 0.25;
    col.g *= 1.0 - uATint * 0.15;
    col = max(col, vec3(0.0));
    float l = clamp(dot(col, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    float sw = (1.0 - l) * (1.0 - l);
    float hw = l * l;
    col *= 1.0 + uASh * 0.6 * sw;
    col *= 1.0 + uAHi * 0.4 * hw;
    col *= 1.0 + uAWh * 0.3 * smoothstep(0.5, 1.0, l);
    col += uABl * 0.12 * (1.0 - smoothstep(0.0, 0.5, l));
    col = (col - 0.5) * (1.0 + uACon * 0.8) + 0.5;
    if (uAHaze > 0.0) {
        float t = min(col.r, min(col.g, col.b)) * uAHaze * 0.85;
        col = (col - t) / max(1.0 - t, 0.05);
    } else if (uAHaze < 0.0) {
        col = mix(col, vec3(0.7), -uAHaze * 0.35);
    }
    float l2 = dot(col, vec3(0.299, 0.587, 0.114));
    float mx = max(col.r, max(col.g, col.b));
    float mn = min(col.r, min(col.g, col.b));
    float sat0 = (mx - mn) / max(mx, 0.0001);
    col = mix(vec3(l2), col, max(0.0, 1.0 + uASat + uAVib * (1.0 - sat0)));
    return max(col, vec3(0.0));
}

void main() {
    vec2 c = toC(vUv);

    vec3 col;
    if (uCA > 0.0) {
        col = vec3(fetchC(c * (1.0 + uCA)).r, fetchC(c).g, fetchC(c * (1.0 - uCA)).b);
    } else {
        col = fetchC(c);
    }
    if (uBokeh > 0.0004) col = bokehBlur(vUv, c, col);

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

    if (uFlare > 0.0) {
        vec3 gs = vec3(0.0);
        for (int i = 0; i < 3; i++) {
            float k = 0.45 + float(i) * 0.5;
            vec3 sm = max(fetchC(-c * k) - vec3(0.7), vec3(0.0));
            vec3 tint = i == 0 ? vec3(1.0, 0.45, 0.2) : (i == 1 ? vec3(0.3, 0.8, 0.6) : vec3(0.5, 0.4, 1.0));
            gs += sm * tint;
        }
        col += gs * uFlare * 1.6;
    }
    if (uStar > 0.0) {
        vec3 sp = vec3(0.0);
        for (int d = 0; d < 4; d++) {
            float a = float(d) * 0.785398 + 0.2;
            vec2 dir = vec2(cos(a), sin(a));
            for (int j = 1; j <= 6; j++) {
                float t = float(j) / 6.0;
                vec3 s1 = max(fetchC(c + dir * t * 0.14) - vec3(0.82), vec3(0.0));
                vec3 s2 = max(fetchC(c - dir * t * 0.14) - vec3(0.82), vec3(0.0));
                sp += (s1 + s2) * (1.0 - t) * (1.0 - t);
            }
        }
        col += sp * uStar * 0.5;
    }

    col *= uGain * exp2(uBri);
    if (uAdjOn > 0.5) col = adjustCol(col, c);
    float mk = 0.0;
    if (uMaskOn > 0.5) {
        mk = texture2D(uMask, srcUv(c)).r;
        if (mk > 0.002) col = mix(col, localCol(col), mk);
    }
    if (uLutOn > 0.5) col = lutFetch(col);
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

    if (uMaskShow > 0.5) col = mix(col, vec3(0.95, 0.15, 0.15), mk * 0.55);
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
    fun i1(name: String, v: Int) = GLES20.glUniform1i(loc(name), v)
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

/** Contesto dell'app, per leggere gli asset dai thread OpenGL. */
object AppCtx {
    lateinit var app: android.content.Context
}

/** Texture della LUT corrente per un contesto OpenGL: si ricarica solo quando cambia pellicola. */
class LutTexture {
    private var tex = 0
    private var cur: String? = null

    /** Da chiamare quando il contesto OpenGL viene ricreato. */
    fun reset() {
        tex = 0
        cur = null
    }

    /** Lega la LUT sull'unità 1 e imposta uLut/uLutOn. */
    fun bind(p: GlProgram, name: String?) {
        var on = false
        if (name != null) {
            if (tex == 0) {
                val t = IntArray(1)
                GLES20.glGenTextures(1, t, 0)
                tex = t[0]
                cur = null
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            if (cur != name) {
                val bmp = runCatching {
                    AppCtx.app.assets.open(name).use {
                        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inScaled = false })
                    }
                }.getOrNull()
                if (bmp != null) {
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                    bmp.recycle()
                    cur = name
                } else {
                    cur = null
                }
            }
            on = cur == name
            p.i1("uLut", 1)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
        p.f1("uLutOn", if (on) 1f else 0f)
    }
}
