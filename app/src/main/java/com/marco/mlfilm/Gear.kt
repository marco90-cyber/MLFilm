package com.marco.mlfilm

import kotlin.math.pow

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

/** Obiettivo: ingrandimento (lunghezza focale), nitidezza, distorsione, bagliore. */
data class LensSpec(
    val id: String,
    val name: String,
    val zoom: Float,
    val soft: Float = 0f,
    val vig: Float = 0f,
    val ca: Float = 0f,
    val dist: Float = 0f,
    val bloom: Float = 0f,
    val tilt: Float = 0f,
)

/** Pellicola: colore, contrasto, grana. */
data class FilmSpec(
    val id: String,
    val name: String,
    val family: String,
    val iso: Int,
    val gain: Rgb,
    val sat: Float,
    val con: Float,
    val bri: Float,
    val fade: Float,
    val shadow: Rgb,
    val high: Rgb,
    val grain: Float,
    val halation: Float,
)

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
)

fun buildLook(cam: CameraSpec, lens: LensSpec, film: FilmSpec) = Look(
    aspect = cam.aspect,
    gain = cam.gain * film.gain,
    sat = cam.sat * film.sat,
    con = cam.con * film.con,
    bri = cam.bri + film.bri,
    fade = maxOf(cam.fade, film.fade),
    shadow = film.shadow,
    high = film.high,
    grain = film.grain * cam.grainMul,
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
    LensSpec("24", "24mm f2.8", 1.0f, ca = 0.002f, vig = 0.08f),
    LensSpec("28", "28mm f2.8", 1.06f, vig = 0.05f),
    LensSpec("35", "35mm f2", 1.22f, vig = 0.06f),
    LensSpec("40", "40mm f2.8", 1.38f),
    LensSpec("50a", "50mm f1.4", 1.7f, soft = 0.1f, vig = 0.12f, bloom = 0.1f),
    LensSpec("50b", "50mm f1.8", 1.7f, vig = 0.07f),
    LensSpec("85", "85mm f1.8", 2.4f, soft = 0.08f, vig = 0.1f, bloom = 0.08f),
    LensSpec("135", "135mm f2.8", 3.2f, vig = 0.06f),
    LensSpec("zoom", "35-70mm", 1.5f, ca = 0.003f, vig = 0.1f),
    LensSpec("fish", "Fisheye 16mm", 1.28f, dist = 0.95f, ca = 0.004f, vig = 0.15f),
    LensSpec("tilt", "Tilt-Shift", 1.35f, tilt = 1f),
    LensSpec("soft", "Soft Focus", 1.3f, soft = 0.75f, bloom = 0.45f),
    LensSpec("vintage", "Vintage Glass", 1.4f, soft = 0.3f, vig = 0.2f, ca = 0.005f, bloom = 0.25f),
    LensSpec("cine", "Cine 40mm", 1.4f, vig = 0.15f, ca = 0.004f, bloom = 0.3f),
    LensSpec("lomo", "Lomo 32mm", 1.15f, vig = 0.45f, ca = 0.003f, dist = 0.08f),
)

// ---------------------------------------------------------------- 120 pellicole

private class Fam(
    val name: String,
    val gain: Rgb = ONE,
    val sat: Float = 1f,
    val con: Float = 1f,
    val bri: Float = 0f,
    val fade: Float = 0f,
    val shadow: Rgb = ZERO,
    val high: Rgb = ZERO,
    val grain: Float = 0.05f,
    val halation: Float = 0f,
)

private val FAMILIES = listOf(
    Fam("Amber", Rgb(1.07f, 1.0f, 0.9f), 1.05f, 1.05f, high = Rgb(0.05f, 0.03f, -0.03f), grain = 0.06f),
    Fam("Emerald", Rgb(0.96f, 1.05f, 0.98f), 1.1f, 1.08f, shadow = Rgb(-0.02f, 0.03f, 0.02f), grain = 0.055f),
    Fam("Vivid", ONE, 1.4f, 1.15f, grain = 0.04f),
    Fam("Clarity", Rgb(1.0f, 1.0f, 1.01f), 1.0f, 1.1f, grain = 0.035f),
    Fam("Portrait", Rgb(1.05f, 1.0f, 0.96f), 0.95f, 0.95f, fade = 0.02f, high = Rgb(0.04f, 0.02f, 0f), grain = 0.045f),
    Fam("Dusk", Rgb(0.98f, 0.96f, 1.04f), 1.05f, 1.1f, shadow = Rgb(0.04f, -0.02f, 0.05f), high = Rgb(0.03f, 0.01f, -0.02f), grain = 0.06f),
    Fam("Harbor", Rgb(0.93f, 0.99f, 1.08f), 1.0f, 1.08f, shadow = Rgb(-0.02f, 0.01f, 0.04f), grain = 0.05f),
    Fam("Meadow", Rgb(0.98f, 1.06f, 0.92f), 1.12f, 1.04f, high = Rgb(0.02f, 0.04f, -0.03f), grain = 0.05f),
    Fam("Ember", Rgb(1.1f, 0.97f, 0.88f), 1.2f, 1.12f, shadow = Rgb(0.04f, -0.01f, -0.02f), grain = 0.06f, halation = 0.25f),
    Fam("Velvet", Rgb(1.0f, 0.97f, 1.02f), 0.92f, 0.88f, fade = 0.05f, shadow = Rgb(0.04f, 0.0f, 0.05f), grain = 0.05f),
    Fam("Sunday", Rgb(1.05f, 1.01f, 0.92f), 0.88f, 0.9f, bri = 0.06f, fade = 0.07f, high = Rgb(0.04f, 0.03f, 0f), grain = 0.055f),
    Fam("Cinema", Rgb(1.03f, 1.0f, 0.97f), 1.05f, 1.15f, shadow = Rgb(-0.04f, 0.02f, 0.05f), high = Rgb(0.06f, 0.025f, -0.04f), grain = 0.05f, halation = 0.15f),
    Fam("Pastel", Rgb(1.02f, 1.01f, 1.02f), 0.75f, 0.82f, bri = 0.08f, fade = 0.08f, grain = 0.04f),
    Fam("Citrus", Rgb(1.03f, 1.05f, 0.9f), 1.18f, 1.06f, high = Rgb(0.03f, 0.04f, -0.04f), grain = 0.05f),
    Fam("Polar", Rgb(0.95f, 1.0f, 1.07f), 0.9f, 1.05f, bri = 0.03f, high = Rgb(-0.01f, 0.01f, 0.03f), grain = 0.045f),
    Fam("Rust", Rgb(1.08f, 0.96f, 0.86f), 1.0f, 1.1f, fade = 0.04f, shadow = Rgb(0.03f, 0.0f, -0.02f), grain = 0.065f, halation = 0.2f),
    Fam("Mono", Rgb(1f, 1f, 1f), 0f, 1.12f, grain = 0.06f, high = Rgb(0.01f, 0.01f, 0.01f)),
    Fam("Ink", Rgb(1f, 1f, 1f), 0f, 1.4f, bri = -0.05f, grain = 0.09f),
    Fam("Silver", Rgb(1f, 1f, 1f), 0f, 0.95f, fade = 0.04f, grain = 0.1f, high = Rgb(0.0f, 0.0f, 0.02f)),
    Fam("Sepia", Rgb(1f, 1f, 1f), 0f, 1.0f, fade = 0.05f, shadow = Rgb(0.06f, 0.03f, -0.02f), high = Rgb(0.1f, 0.06f, -0.03f), grain = 0.07f),
)

private val ISOS = listOf(50, 100, 200, 400, 800, 1600)

val FILMS: List<FilmSpec> = FAMILIES.flatMap { f ->
    ISOS.map { iso ->
        val s = Math.log((iso / 100.0)).div(Math.log(2.0)).toFloat() // -1 .. 4
        FilmSpec(
            id = "${f.name.lowercase()}$iso",
            name = "${f.name} $iso",
            family = f.name,
            iso = iso,
            gain = f.gain,
            sat = f.sat * (1f - 0.035f * s),
            con = f.con * (1f + 0.025f * s),
            bri = f.bri + 0.03f * s,
            fade = f.fade,
            shadow = f.shadow,
            high = f.high,
            grain = f.grain * (iso / 100f).pow(0.5f),
            halation = f.halation,
        )
    }
}
