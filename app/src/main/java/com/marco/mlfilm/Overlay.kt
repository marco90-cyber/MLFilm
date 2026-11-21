package com.marco.mlfilm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Griglia dei terzi, livella con l'accelerometro e cerchio di messa a fuoco. */
@Composable
fun ViewfinderOverlay(grid: Boolean, focus: Offset?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(grid) {
        if (!grid) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val deg = Math.toDegrees(Math.atan2(-e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
                roll += (deg - roll) * 0.25f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    Canvas(modifier) {
        if (grid) {
            val c = Color(0x66FFFFFF)
            val sw = 1.dp.toPx()
            for (i in 1..2) {
                drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), strokeWidth = sw)
                drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), strokeWidth = sw)
            }
            val level = Math.abs(roll) < 1.5f
            val col = if (level) C.Amber else Color(0xAAFFFFFF)
            rotate(-roll, Offset(size.width / 2f, size.height / 2f)) {
                drawLine(col, Offset(size.width * 0.3f, size.height / 2f), Offset(size.width * 0.7f, size.height / 2f), strokeWidth = 2.dp.toPx())
            }
        }
        if (focus != null) {
            drawCircle(C.Amber, 28.dp.toPx(), focus, style = Stroke(2.dp.toPx()))
        }
    }
}
