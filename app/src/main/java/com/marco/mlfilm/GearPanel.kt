package com.marco.mlfilm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Griglia di scelta (camere, obiettivi o pellicole) con la voce attiva cerchiata d'ambra. */
@Composable
fun GearGrid(
    title: String,
    count: Int,
    selected: Int,
    columns: Int,
    onPick: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    glyph: @Composable (Int, Modifier) -> Unit,
) {
    val state = rememberLazyGridState()
    LaunchedEffect(title) { state.scrollToItem(maxOf(0, selected - columns)) }
    Column(modifier.background(C.Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mono(title, size = 12.sp)
            Mono("%02d / %d".format(selected + 1, count), color = C.Amber, size = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            items((0 until count).toList()) { i ->
                val sel = i == selected
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier
                        .padding(3.dp)
                        .clip(shape)
                        .then(if (sel) Modifier.border(2.dp, C.Amber, shape) else Modifier)
                        .clickable { onPick(i) }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    glyph(i, Modifier.size(54.dp))
                    Mono(label(i), color = if (sel) C.Amber else C.Dim, size = 9.sp)
                }
            }
        }
    }
}
