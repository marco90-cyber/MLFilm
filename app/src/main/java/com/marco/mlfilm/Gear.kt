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

/** Regolazioni stile Snapseed/Lightroom, tutte da -1 a +1 (0 = spento); nitidezza da 0 a 1. */
data class Adj(
    val exp: Float = 0f, val con: Float = 0f, val hi: Float = 0f, val sh: Float = 0f, val wh: Float = 0f, val bl: Float = 0f,
    val temp: Float = 0f, val tint: Float = 0f, val sat: Float = 0f, val vib: Float = 0f,
    val sharp: Float = 0f, val clar: Float = 0f, val haze: Float = 0f,
) {
    val on: Boolean get() = this != Adj()
}

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
    val focal: Int = 0,         // lunghezza focale in mm (0 = non indicata)
    val group: String = "BASE", // categoria nell'elenco
    val real: Boolean = false,  // distorsione/CA/vignettatura da profili Lensfun
)

val SHAPES = listOf("CERCHIO", "ESAGONO", "PENTAGONO", "CUORE", "STELLA", "OTTAGONO", "STELLA 6", "ANELLO", "GOCCIA", "SOFT", "FIORE", "TRIFOGLIO", "ROMBO", "QUADRATO")

/** Ordine delle forme nel selettore (indici di SHAPES). */
val SHAPE_ORDER = listOf(0, 9, 10, 11, 8, 12, 13, 2, 1, 5, 3, 4, 6, 7)

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
    /** "Nessuna": foto com'è, senza LUT, grana né alone. */
    val none: Boolean get() = id == "none"
    val lut: String? get() = if (none) null else "luts/$id.png"
    val grain: Float get() {
        if (none) return 0f
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
    val halation: Float get() = if (none) 0f else if (family == "Vintage Neg" || family == "Instant") 0.12f else 0.05f
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
    val focusW: Float = 0f,      // ampiezza della zona a fuoco (0 = solo il piano scelto .. 0.5 = molto profonda)
    val focusDepth: Float = 1f,  // profondità a fuoco (0 lontano .. 1 vicino)
    val fpU: Float = 0.5f,       // punto a fuoco nel fotogramma (origine in alto a sinistra)
    val fpV: Float = 0.5f,
    val flare: Float = 0f,       // riflessi (ghost) dalle luci forti
    val star: Float = 0f,        // raggi attorno alle luci forti
    val hi: Float = 0f,          // luminosità delle luci sfocate (bokeh), 0..1
    val viv: Float = 0f,         // colore delle luci sfocate (bokeh), 0..1
    val apod: Float = 0f,        // bokeh morbido e passaggio di fuoco graduale ("fuoco sfuggente")
    val adj: Adj = Adj(),        // regolazioni manuali (REGOLA)
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
    zoom = 1f, // l'obiettivo non ritaglia la foto: contano solo effetti e bokeh
    dist = cam.dist + lens.dist,
    ca = cam.ca + lens.ca,
    tilt = lens.tilt,
    leak = cam.leak,
    half = cam.half,
)

// ---------------------------------------------------------------- camere (ognuna con i suoi difetti tipici)

val CAMERAS: List<CameraSpec> = listOf(
    CameraSpec("phone", "Smartphone", "Nessun effetto", 0.75f),
    CameraSpec("rf", "Rangefinder", "Telemetro: angoli morbidi, piccola luce dal dorso", 2f / 3f,
        gain = Rgb(1.02f, 1.0f, 0.97f), con = 1.06f, vig = 0.12f, soft = 0.12f, leak = 0.06f),
    CameraSpec("tlr", "Twin Lens", "Biottica 6x6: bordi molli, vignettatura", 1f,
        gain = Rgb(1.0f, 1.01f, 0.99f), con = 1.08f, vig = 0.24f, soft = 0.18f, dist = 0.03f),
    CameraSpec("instant", "Instant", "Istantanea: velata, calda, luce laterale", 0.82f,
        gain = Rgb(1.05f, 1.0f, 0.93f), sat = 0.9f, con = 0.92f, fade = 0.06f,
        soft = 0.55f, vig = 0.3f, grainMul = 0.8f, leak = 0.25f),
    CameraSpec("toy", "Toy", "Plastica: vignetta forte, colori sbavati, luce", 0.75f,
        gain = Rgb(1.08f, 0.98f, 0.9f), sat = 1.25f, con = 1.12f, soft = 0.6f,
        vig = 0.6f, ca = 0.006f, dist = 0.1f, leak = 0.45f, grainMul = 1.2f),
    CameraSpec("compact", "Compact", "Compatta anni '90: flash piatto, angoli scuri", 0.667f,
        gain = Rgb(1.04f, 1.02f, 0.96f), sat = 1.1f, con = 1.1f, vig = 0.22f, leak = 0.08f, dist = 0.04f),
    CameraSpec("dispo", "Disposable", "Usa e getta: flash duro, grana, barilotto", 0.75f,
        gain = Rgb(1.06f, 1.03f, 0.93f), sat = 1.15f, con = 1.18f, bri = 0.08f,
        vig = 0.42f, soft = 0.25f, dist = 0.08f, grainMul = 1.4f, leak = 0.15f),
    CameraSpec("pinhole", "Pinhole", "Foro stenopeico: tutto morbido", 1f,
        con = 0.88f, fade = 0.05f, soft = 1.0f, vig = 0.5f, grainMul = 1.1f, dist = 0.12f),
    CameraSpec("box", "Box", "Scatola di cartone: velata, bordi sfocati", 1f,
        gain = Rgb(0.98f, 0.99f, 1.0f), sat = 0.85f, con = 0.9f, fade = 0.08f,
        soft = 0.8f, vig = 0.45f, ca = 0.004f, grainMul = 1.3f, leak = 0.1f),
    CameraSpec("pano", "Panoramic", "Formato lungo: prospettiva distorta", 0.5f,
        con = 1.06f, vig = 0.16f, ca = 0.002f, dist = 0.06f),
    CameraSpec("mf", "Medium Format", "Medio formato 4:5: pulita, grana fine", 0.8f,
        con = 1.03f, sat = 1.02f, vig = 0.08f, grainMul = 0.6f),
)

// ---------------------------------------------------------------- obiettivi: vedi LensCatalog.kt (generato)

private val FILM_SUFFIX = Regex(" (\\+\\+|\\+|\u2212)$")

/** Una pellicola "madre" per nome, con le sue varianti (+, ++, −, altre categorie con lo stesso nome). */
class FilmGroup(val title: String, val members: List<Int>, val rep: Int)

val FILM_GROUPS: List<FilmGroup> by lazy {
    val map = LinkedHashMap<String, MutableList<Int>>()
    FILMS.forEachIndexed { i, f -> map.getOrPut(f.name.replace(FILM_SUFFIX, "").lowercase()) { mutableListOf() }.add(i) }
    map.values.map { m ->
        val rep = m.firstOrNull { FILMS[it].name == FILMS[it].name.replace(FILM_SUFFIX, "") } ?: m[0]
        FilmGroup(FILMS[rep].name.replace(FILM_SUFFIX, ""), m, rep)
    }
}
