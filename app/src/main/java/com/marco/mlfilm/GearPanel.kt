package com.marco.mlfilm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Color
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

/** Elenco delle pellicole: una riga per nome; tenendo premuto si vedono le varianti. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilmPicker(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val groups = FILM_GROUPS
    val selGroup = groups.indexOfFirst { selected in it.members }.coerceAtLeast(0)
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem(maxOf(0, selGroup - 2)) }
    var variants by remember { mutableStateOf<FilmGroup?>(null) }
    Column(modifier.background(C.Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mono("PELLICOLA · TIENI PREMUTO = VARIANTI", size = 10.sp)
            Mono("%d / %d".format(selGroup + 1, groups.size), color = C.Amber, size = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(groups) { i, gr ->
                val sel = i == selGroup
                val f = FILMS[if (sel) selected else gr.rep]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (sel) Modifier.background(Color(0x22F5A623)) else Modifier)
                        .combinedClickable(onClick = { onPick(gr.rep) }, onLongClick = { variants = gr })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilmGlyph(f.family, Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(f.name, color = if (sel) C.Amber else C.Text, fontSize = 15.sp, maxLines = 1)
                        Mono(f.family + if (f.iso > 0) " · ISO ${f.iso}" else "", color = C.Dim, size = 9.sp)
                    }
                    if (gr.members.size > 1) Mono("+${gr.members.size - 1}", color = C.Dim, size = 10.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
            }
        }
    }
    variants?.let { gr ->
        AlertDialog(
            onDismissRequest = { variants = null },
            containerColor = C.Panel,
            confirmButton = {},
            title = { Mono(gr.title.uppercase(), size = 12.sp) },
            text = {
                LazyColumn {
                    itemsIndexed(gr.members) { _, idx ->
                        val f = FILMS[idx]
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(idx); variants = null }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilmGlyph(f.family, Modifier.size(28.dp))
                            androidx.compose.material3.Text(f.name, color = if (idx == selected) C.Amber else C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Mono(f.family, color = C.Dim, size = 9.sp)
                        }
                    }
                }
            },
        )
    }
}
