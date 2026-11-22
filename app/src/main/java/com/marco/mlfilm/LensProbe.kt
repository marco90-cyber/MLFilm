package com.marco.mlfilm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Una lente reale del telefono. [cam2] = va aperta direttamente con Camera2 (id diverso dalla camera principale);
 * altrimenti si usa CameraX sulla camera principale, eventualmente con [zoom] (es. 0.5x = grandangolo logico).
 */
data class RealLens(val id: String, val mm: Int, val kind: Int, val cam2: Boolean, val zoom: Float = 1f) {
    val key: String get() = "$id/$kind"
    val short: String get() = if (kind == LensProbe.MACRO) "MACRO" else "${mm}mm"
    val name: String get() = when (kind) {
        LensProbe.UW -> "${mm}mm GRANDANGOLO"
        LensProbe.TELE -> "${mm}mm TELE"
        LensProbe.MACRO -> "MACRO"
        else -> "${mm}mm"
    }
}

/** Cerca le camere posteriori, anche quelle che Android non elenca ma che si aprono (come fanno GCam e Open Camera). */
object LensProbe {
    const val UW = 0
    const val MAIN = 1
    const val TELE = 2
    const val MACRO = 3

    fun kindShort(k: Int) = when (k) { UW -> "GRAND."; TELE -> "TELE"; MACRO -> "MACRO"; else -> "PRINC." }

    private const val KEY = "lenses_v2"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("lenses", Context.MODE_PRIVATE)

    fun cached(ctx: Context): List<RealLens>? {
        val s = sp(ctx).getString(KEY, null) ?: return null
        return s.split("\n").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split("|")
            if (p.size < 5) null else RealLens(p[0], p[1].toInt(), p[2].toInt(), p[3] == "1", p[4].toFloat())
        }
    }

    fun clear(ctx: Context) { sp(ctx).edit().remove(KEY).apply() }

    private fun save(ctx: Context, l: List<RealLens>) {
        sp(ctx).edit().putString(KEY, l.joinToString("\n") { "${it.id}|${it.mm}|${it.kind}|${if (it.cam2) 1 else 0}|${it.zoom}" } + "\n").apply()
    }

    fun equivMm(c: CameraCharacteristics): Double? {
        val sz = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val fl = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
        return fl * 43.27 / Math.sqrt((sz.width * sz.width + sz.height * sz.height).toDouble())
    }

    private class Cand(val id: String, val mm: Double, val minFocusCm: Double, val listed: Boolean, val zoomMin: Float)

    /** Prova ad aprire una camera: true se si apre davvero. */
    fun tryOpen(ctx: Context, id: String, done: (Boolean) -> Unit) {
        val h = Handler(Looper.getMainLooper())
        var finished = false
        fun end(ok: Boolean) { if (!finished) { finished = true; done(ok) } }
        h.postDelayed({ end(false) }, 4000)
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) { d.close(); end(true) }
                override fun onDisconnected(d: CameraDevice) { d.close(); end(false) }
                override fun onError(d: CameraDevice, e: Int) { d.close(); end(false) }
            }, h)
        } catch (e: Exception) {
            end(false)
        }
    }

    /** Scansione completa (da lanciare sul thread principale, prima di aprire il mirino). Salva e restituisce l'elenco. */
    fun scan(ctx: Context, done: (List<RealLens>) -> Unit) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val listed = try { cm.cameraIdList.toSet() } catch (e: Exception) { emptySet() }
        val cands = ArrayList<Cand>()
        for (n in 0..12) {
            val id = n.toString()
            try {
                val c = cm.getCameraCharacteristics(id)
                if (c.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
                val mm = equivMm(c) ?: continue
                val mf = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
                val cm1 = if (mf > 0.5f) 100.0 / mf else 1000.0
                val zmin = if (Build.VERSION.SDK_INT >= 30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.lower ?: 1f else 1f
                cands += Cand(id, mm, cm1, id in listed, zmin)
            } catch (_: Exception) {
            }
        }
        val queue = ArrayList(cands.filter { !it.listed })
        val ok = HashSet<String>()
        fun finish() {
            val usable = cands.filter { it.listed || it.id in ok }
            val out = build(usable)
            save(ctx, out)
            done(out)
        }
        fun next() {
            if (queue.isEmpty()) { finish(); return }
            val c = queue.removeAt(0)
            tryOpen(ctx, c.id) { good ->
                if (good) ok += c.id
                next()
            }
        }
        next()
    }

    private fun build(c: List<Cand>): List<RealLens> {
        val main = c.firstOrNull { it.listed } ?: return emptyList()
        val out = ArrayList<RealLens>()
        out += RealLens(main.id, main.mm.toInt(), MAIN, false)
        // grandangolo logico (zoom sotto 1x sulla camera principale)
        if (main.zoomMin < 0.95f) out += RealLens(main.id, (main.mm * main.zoomMin).toInt(), UW, false, main.zoomMin)
        for (x in c) {
            if (x.id == main.id) continue
            val ratio = x.mm / main.mm
            val kind = when {
                ratio < 0.75 -> UW
                ratio > 1.6 -> TELE
                else -> continue // doppione della principale (profondità, mono...)
            }
            if (out.none { it.kind == kind }) out += RealLens(x.id, x.mm.toInt(), kind, true)
        }
        // macro: una camera che mette a fuoco sotto i 5 cm (non la principale)
        val m = c.filter { it.id != main.id && it.minFocusCm <= 5.0 }.minByOrNull { it.minFocusCm }
        if (m != null) out += RealLens(m.id, m.mm.toInt(), MACRO, true)
        return out
    }
}
