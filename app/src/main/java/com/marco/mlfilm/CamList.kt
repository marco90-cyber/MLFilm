package com.marco.mlfilm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Descrive le camere che Android mette a disposizione di questa app (per capire quali lenti reali si possono usare). */
fun describeCameras(ctx: Context): List<String> {
    val out = ArrayList<String>()
    out += "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
    try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cm.cameraIdList
        out += "Camere visibili all'app: ${ids.size}"
        for (id in ids) {
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "POSTERIORE"
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONTALE"
                else -> "ESTERNA"
            }
            val sz = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val fl = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val equiv = if (sz != null && fl != null) {
                val diag = Math.sqrt((sz.width * sz.width + sz.height * sz.height).toDouble())
                " (~%.0f mm equiv.)".format(fl * 43.27 / diag)
            } else ""
            val minFocus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            val macro = if (minFocus != null && minFocus > 8f) " · fuoco vicino fino a %.0f cm".format(100f / minFocus) else ""
            val zoom = if (Build.VERSION.SDK_INT >= 30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { " · zoom %.1fx-%.1fx".format(it.lower, it.upper) } ?: "" else ""
            val logical = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
            val phys = if (Build.VERSION.SDK_INT >= 28) c.physicalCameraIds.toList() else emptyList()
            out += ""
            out += "ID $id · $facing" + if (logical) " · LOGICA" else ""
            out += "  focale ${fl ?: "?"} mm$equiv$zoom$macro"
            if (phys.isNotEmpty()) out += "  camere fisiche: ${phys.joinToString(", ")}"
            for (p in phys) {
                try {
                    val pc = cm.getCameraCharacteristics(p)
                    val pf = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                    val ps = pc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val pe = if (ps != null && pf != null) " (~%.0f mm equiv.)".format(pf * 43.27 / Math.sqrt((ps.width * ps.width + ps.height * ps.height).toDouble())) else ""
                    out += "    - $p: focale ${pf ?: "?"} mm$pe"
                } catch (_: Exception) {
                    out += "    - $p: non leggibile"
                }
            }
        }
    } catch (e: Exception) {
        out += "Errore: ${e.message}"
    }
    return out
}

@Composable
fun CameraListScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lines = remember { describeCameras(ctx) }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Mono("< INDIETRO", color = C.Text, size = 13.sp, modifier = Modifier.padding(16.dp).clickable { onBack() })
        Mono("CAMERE VISIBILI ALL'APP", color = C.Amber, size = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            for (l in lines) Mono(if (l.isEmpty()) " " else l, color = C.Text, size = 11.sp)
        }
    }
}
