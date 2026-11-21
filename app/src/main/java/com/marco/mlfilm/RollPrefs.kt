package com.marco.mlfilm

import android.content.Context

/** Rullo da 36 pose: pose rimaste e numero del rullo, salvati sul telefono. */
class RollPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("roll", Context.MODE_PRIVATE)

    var left: Int
        get() = sp.getInt("left", 36)
        set(v) { sp.edit().putInt("left", v.coerceIn(0, 36)).apply() }

    var roll: Int
        get() = sp.getInt("roll", 1)
        set(v) { sp.edit().putInt("roll", v).apply() }

    fun newRoll() {
        roll = roll + 1
        left = 36
    }
}
