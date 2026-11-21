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

    var cam: Int
        get() = sp.getInt("cam", 1)
        set(v) { sp.edit().putInt("cam", v).apply() }

    var lens: Int
        get() = sp.getInt("lens", 2)
        set(v) { sp.edit().putInt("lens", v).apply() }

    var film: Int
        get() = sp.getInt("film", 4)
        set(v) { sp.edit().putInt("film", v).apply() }

    var date: Boolean
        get() = sp.getBoolean("date", true)
        set(v) { sp.edit().putBoolean("date", v).apply() }

    /** Half Frame: true = due scatti in una foto, false = foto verticale singola. */
    var pair: Boolean
        get() = sp.getBoolean("pair", true)
        set(v) { sp.edit().putBoolean("pair", v).apply() }

    fun newRoll() {
        roll = roll + 1
        left = 36
    }
}
