package com.marco.mlfilm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Elenco delle lenti reali del telefono (grandangolo, principale, tele, macro): quelle assenti restano grigie. */
@Composable
fun LensList(lenses: List<RealLens>, selKey: String?, onPick: (RealLens) -> Unit, onMissing: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(C.Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mono("OBIETTIVI DEL TELEFONO", size = 11.sp)
        for (k in listOf(LensProbe.UW, LensProbe.MAIN, LensProbe.TELE, LensProbe.MACRO)) {
            val l = lenses.firstOrNull { it.kind == k }
            val on = l != null && l.key == selKey
            Row(
                Modifier.fillMaxWidth().background(if (on) Color(0x33FFB04A) else Color(0x22FFFFFF))
                    .clickable { if (l != null) onPick(l) else onMissing() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Mono(
                    l?.name ?: "LENTE NON DISPONIBILE",
                    color = if (on) C.Amber else if (l != null) C.Text else C.Text.copy(alpha = 0.35f),
                    size = 13.sp,
                )
                Mono(LensProbe.kindShort(k), color = C.Dim, size = 10.sp)
            }
        }
    }
}
