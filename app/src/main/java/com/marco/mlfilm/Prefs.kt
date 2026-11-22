package com.marco.mlfilm

import android.content.Context

/** Ultime scelte dell'editor, salvate sul telefono. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("roll", Context.MODE_PRIVATE)

    var film: Int
        get() = sp.getInt("film2", 0)
        set(v) { sp.edit().putInt("film2", v).apply() }

    /** Data arancione sulla foto salvata. */
    var date: Boolean
        get() = sp.getBoolean("stamp", false)
        set(v) { sp.edit().putBoolean("stamp", v).apply() }
}
