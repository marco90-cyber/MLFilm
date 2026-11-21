package com.marco.mlfilm

/** Terna di colori usata da camere e pellicole. */
data class Rgb(val r: Float, val g: Float, val b: Float) {
    operator fun times(o: Rgb) = Rgb(r * o.r, g * o.g, b * o.b)
}

private val ONE = Rgb(1f, 1f, 1f)
private val ZERO = Rgb(0f, 0f, 0f)

/** Corpo macchina: formato, colore di base, vignettatura, morbidezza. */
data class CameraSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val aspect: Float,          // larghezza / altezza (foto verticale)
    val gain: Rgb = ONE,
    val sat: Float = 1f,
    val con: Float = 1f,
    val bri: Float = 0f,
    val fade: Float = 0f,
    val grainMul: Float = 1f,
    val soft: Float = 0f,
    val vig: Float = 0f,
    val ca: Float = 0f,
    val dist: Float = 0f,
    val leak: Float = 0f,
    val half: Boolean = false,
)

/** Obiettivo: lunghezza focale, apertura di default e forma del bokeh (la sfocatura usa la profondità). */
data class LensSpec(
    val id: String,
    val name: String,
    val zoom: Float,
    val f: Float = 2f,          // apertura di partenza; 0 = senza sfocatura di profondità
    val shape: Int = 0,         // forma del bokeh: indice in SHAPES
    val swirl: Float = 0f,      // bokeh "a vortice" ai bordi
    val cat: Float = 0f,        // bokeh a occhio di gatto ai bordi
    val soft: Float = 0f,
    val vig: Float = 0f,
    val ca: Float = 0f,
    val dist: Float = 0f,
    val bloom: Float = 0f,
    val tilt: Float = 0f,
)

val SHAPES = listOf("CERCHIO", "ESAGONO", "PENTAGONO", "CUORE", "STELLA")

/** Stop di diaframma offerti dall'editor; l'ultimo (0) spegne la sfocatura. */
val APERTURES = floatArrayOf(1.4f, 1.8f, 2f, 2.8f, 4f, 5.6f, 8f, 11f, 16f, 0f)

fun apertureLabel(f: Float) = if (f <= 0f) "OFF" else "F" + (if (f % 1f == 0f) f.toInt().toString() else f.toString())

fun nearestAperture(f: Float): Int {
    if (f <= 0f) return APERTURES.size - 1
    var best = 0
    for (i in 0 until APERTURES.size - 1) if (Math.abs(APERTURES[i] - f) < Math.abs(APERTURES[best] - f)) best = i
    return best
}

/** Raggio massimo della sfocatura (frazione dell'altezza) per un'apertura. */
fun blurRadius(f: Float) = if (f <= 0f) 0f else (0.05f * 2.8f / f).coerceIn(0f, 0.07f)

/** Pellicola: un look colore (LUT 3D negli asset) più grana e alone propri della famiglia. */
data class FilmSpec(
    val id: String,          // percorso asset senza estensione: "bw/ilford_delta_400"
    val name: String,
    val family: String,
    val iso: Int,
) {
    val lut: String get() = "luts/$id.png"
    val grain: Float get() {
        val base = when (family) {
            "B&W" -> 0.075f
            "Slide" -> 0.03f
            "Instant", "Instant Pro" -> 0.045f
            "Vintage Neg" -> 0.06f
            "Print" -> 0.035f
            else -> 0.05f
        }
        val k = if (iso > 0) Math.sqrt(iso / 100.0).toFloat().coerceIn(0.6f, 2.4f) else 1f
        return base * k
    }
    val halation: Float get() = if (family == "Vintage Neg" || family == "Instant") 0.12f else 0.05f
}

/** Tutti i parametri già combinati, pronti per lo shader. */
data class Look(
    val aspect: Float = 0.75f,
    val gain: Rgb = ONE,
    val sat: Float = 1f,
    val con: Float = 1f,
    val bri: Float = 0f,
    val fade: Float = 0f,
    val shadow: Rgb = ZERO,
    val high: Rgb = ZERO,
    val grain: Float = 0f,
    val halation: Float = 0f,
    val soft: Float = 0f,
    val vig: Float = 0f,
    val zoom: Float = 1f,
    val dist: Float = 0f,
    val ca: Float = 0f,
    val tilt: Float = 0f,
    val leak: Float = 0f,
    val half: Boolean = false,
    val mirror: Boolean = false,
    val lut: String? = null,
    val fNum: Float = 0f,        // 0 = niente sfocatura di profondità
    val shape: Int = 0,
    val swirl: Float = 0f,
    val cat: Float = 0f,
    val focusDepth: Float = 1f,  // profondità a fuoco (0 lontano .. 1 vicino)
    val fpU: Float = 0.5f,       // punto a fuoco nel fotogramma (origine in alto a sinistra)
    val fpV: Float = 0.5f,
)

fun buildLook(cam: CameraSpec, lens: LensSpec, film: FilmSpec) = Look(
    aspect = cam.aspect,
    gain = cam.gain,
    sat = cam.sat,
    con = cam.con,
    bri = cam.bri,
    fade = cam.fade,
    grain = film.grain * cam.grainMul,
    lut = film.lut,
    fNum = lens.f,
    shape = lens.shape,
    swirl = lens.swirl,
    cat = lens.cat,
    halation = film.halation + lens.bloom,
    soft = cam.soft + lens.soft,
    vig = cam.vig + lens.vig,
    zoom = lens.zoom,
    dist = cam.dist + lens.dist,
    ca = cam.ca + lens.ca,
    tilt = lens.tilt,
    leak = cam.leak,
    half = cam.half,
)

// ---------------------------------------------------------------- 12 camere

val CAMERAS: List<CameraSpec> = listOf(
    CameraSpec("rf", "Rangefinder", "Telemetro 35mm", 2f / 3f,
        gain = Rgb(1.02f, 1.0f, 0.97f), con = 1.06f, vig = 0.12f),
    CameraSpec("slr", "Pro SLR", "Reflex, nitida", 2f / 3f,
        con = 1.04f, sat = 1.04f, vig = 0.05f),
    CameraSpec("tlr", "Twin Lens", "Biottica 6x6", 1f,
        gain = Rgb(1.0f, 1.01f, 0.99f), con = 1.08f, vig = 0.22f, soft = 0.1f),
    CameraSpec("instant", "Instant", "Istantanea morbida", 0.82f,
        gain = Rgb(1.05f, 1.0f, 0.93f), sat = 0.9f, con = 0.92f, fade = 0.06f,
        soft = 0.55f, vig = 0.3f, grainMul = 0.8f, leak = 0.25f),
    CameraSpec("toy", "Toy", "Giocattolo plasticoso", 0.75f,
        gain = Rgb(1.08f, 0.98f, 0.9f), sat = 1.25f, con = 1.12f, soft = 0.6f,
        vig = 0.55f, ca = 0.006f, leak = 0.4f, grainMul = 1.2f),
    CameraSpec("half", "Half Frame", "Mezzo formato", 0.75f,
        gain = Rgb(1.03f, 1.0f, 0.96f), con = 1.05f, vig = 0.15f, grainMul = 1.25f, half = true),
    CameraSpec("compact", "Compact", "Compatta anni '90", 0.667f,
        gain = Rgb(1.04f, 1.02f, 0.96f), sat = 1.1f, con = 1.1f, vig = 0.2f, leak = 0.08f),
    CameraSpec("dispo", "Disposable", "Usa e getta con flash", 0.75f,
        gain = Rgb(1.06f, 1.03f, 0.93f), sat = 1.15f, con = 1.18f, bri = 0.08f,
        vig = 0.4f, soft = 0.25f, grainMul = 1.4f, leak = 0.15f),
    CameraSpec("pinhole", "Pinhole", "Foro stenopeico", 1f,
        con = 0.88f, fade = 0.05f, soft = 1.0f, vig = 0.5f, grainMul = 1.1f, dist = 0.12f),
    CameraSpec("box", "Box", "Scatola di cartone", 1f,
        gain = Rgb(0.98f, 0.99f, 1.0f), sat = 0.85f, con = 0.9f, fade = 0.08f,
        soft = 0.8f, vig = 0.45f, ca = 0.004f, grainMul = 1.3f),
    CameraSpec("pano", "Panoramic", "Formato lungo", 0.5f,
        con = 1.06f, vig = 0.14f, ca = 0.002f),
    CameraSpec("mf", "Medium Format", "Medio formato 4:5", 0.8f,
        gain = Rgb(1.0f, 1.0f, 1.0f), con = 1.03f, sat = 1.02f, vig = 0.08f, grainMul = 0.6f),
)

// ---------------------------------------------------------------- 15 obiettivi

val LENSES: List<LensSpec> = listOf(
    LensSpec("24", "24mm f4", 1.0f, f = 4f, ca = 0.002f, vig = 0.08f),
    LensSpec("35", "35mm f2", 1.22f, f = 2f, vig = 0.06f),
    LensSpec("50a", "50mm f1.4", 1.7f, f = 1.4f, vig = 0.12f, bloom = 0.1f),
    LensSpec("50b", "50mm f1.8 esagono", 1.7f, f = 1.8f, shape = 1, vig = 0.07f),
    LensSpec("85a", "85mm f1.4", 2.4f, f = 1.4f, vig = 0.1f, bloom = 0.08f),
    LensSpec("85b", "85mm f1.8 penta", 2.4f, f = 1.8f, shape = 2, vig = 0.08f),
    LensSpec("135", "135mm f2", 3.2f, f = 2f, vig = 0.06f),
    LensSpec("swirl", "Swirl 58 f1.4", 1.8f, f = 1.4f, swirl = 1f, vig = 0.2f, ca = 0.003f),
    LensSpec("heart", "Cuore 50 f2", 1.7f, f = 2f, shape = 3, vig = 0.08f),
    LensSpec("star", "Stella 50 f2", 1.7f, f = 2f, shape = 4, vig = 0.08f),
    LensSpec("cat", "Occhio di gatto f1.2", 1.7f, f = 1.2f, cat = 1f, vig = 0.25f, bloom = 0.1f),
    LensSpec("soft", "Soft 90 f2.8", 2.5f, f = 2.8f, soft = 0.7f, bloom = 0.45f),
    LensSpec("cine", "Cine 40 f2", 1.4f, f = 2f, vig = 0.15f, ca = 0.004f, bloom = 0.3f),
    LensSpec("fish", "Fisheye 16 f8", 1.28f, f = 8f, dist = 0.95f, ca = 0.004f, vig = 0.15f),
    LensSpec("tilt", "Tilt-Shift f5.6", 1.35f, f = 5.6f, tilt = 1f),
)
