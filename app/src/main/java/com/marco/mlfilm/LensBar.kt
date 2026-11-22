package com.marco.mlfilm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lenti reali del telefono (grandangolo, principale, tele, macro): quelle assenti restano grigie. */
@Composable
fun LensBar(lenses: List<RealLens>, selKey: String?, onPick: (RealLens) -> Unit, onMissing: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
        for (k in listOf(LensProbe.UW, LensProbe.MAIN, LensProbe.TELE, LensProbe.MACRO)) {
            val l = lenses.firstOrNull { it.kind == k }
            val on = l != null && l.key == selKey
            Box(
                Modifier
                    .background(if (on) C.Amber else Color(0x99000000))
                    .clickable { if (l != null) onPick(l) else onMissing() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Mono(
                    l?.short ?: LensProbe.kindShort(k),
                    color = if (on) Color.Black else if (l != null) C.Text else C.Text.copy(alpha = 0.35f),
                    size = 10.sp,
                )
            }
        }
    }
}
