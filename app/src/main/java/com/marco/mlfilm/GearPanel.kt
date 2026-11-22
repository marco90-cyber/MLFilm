package com.marco.mlfilm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Elenco a categorie (camere o obiettivi): intestazione di gruppo, poi una riga per voce. */
@Composable
fun GearList(
    title: String,
    groups: List<Pair<String, List<Int>>>,
    selected: Int,
    onPick: (Int) -> Unit,
    name: (Int) -> String,
    sub: (Int) -> String,
    modifier: Modifier = Modifier,
    glyph: @Composable (Int, Modifier) -> Unit,
) {
    val state = rememberLazyListState()
    val total = groups.sumOf { it.second.size }
    LaunchedEffect(title) {
        var pos = 0
        for ((_, ids) in groups) {
            pos++
            val k = ids.indexOf(selected)
            if (k >= 0) { state.scrollToItem(maxOf(0, pos + k - 2)); return@LaunchedEffect }
            pos += ids.size
        }
    }
    Column(modifier.background(C.Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mono(title, size = 12.sp)
            Mono("$total", color = C.Amber, size = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            groups.forEach { (head, ids) ->
                item(key = "h$head") {
                    Mono(head, color = C.Dim, size = 10.sp, modifier = Modifier.fillMaxWidth().background(C.Line.copy(alpha = 0.35f)).padding(horizontal = 16.dp, vertical = 5.dp))
                }
                items(ids, key = { "$title$it" }) { i ->
                    val sel = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (sel) Modifier.background(Color(0x22F5A623)) else Modifier)
                            .clickable { onPick(i) }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        glyph(i, Modifier.size(38.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(name(i), color = if (sel) C.Amber else C.Text, fontSize = 15.sp, maxLines = 1)
                            Mono(sub(i), color = C.Dim, size = 9.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                }
            }
        }
    }
}

/** Carosello delle pellicole: scorri e quella al centro si applica subito alla foto. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilmPicker(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemW = 88.dp
    var ready by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(selected) }
    LaunchedEffect(Unit) { state.scrollToItem(selected); ready = true }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { Math.abs(it.offset + it.size / 2f - mid) }?.index ?: -1
        }.collect { i ->
            if (i in FILMS.indices && i != shown) { shown = i; onPick(i) }
        }
    }
    val f = FILMS[shown.coerceIn(0, FILMS.size - 1)]
    Column(modifier.background(C.Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mono("PELLICOLA \u00b7 " + f.family.uppercase() + if (f.iso > 0) " \u00b7 ISO ${f.iso}" else "", size = 10.sp)
            Mono("%d / %d".format(shown + 1, FILMS.size), color = C.Amber, size = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val pad = (maxWidth - itemW) / 2
            LazyRow(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                contentPadding = PaddingValues(horizontal = pad),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(FILMS) { i, fs ->
                    val sel = i == shown
                    Column(
                        Modifier
                            .width(itemW)
                            .clickable { scope.launch { state.animateScrollToItem(i) } }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilmGlyph(fs.family, Modifier.size(if (sel) 60.dp else 46.dp))
                        androidx.compose.material3.Text(
                            fs.name, color = if (sel) C.Amber else C.Dim, fontSize = 11.sp,
                            maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                        )
                    }
                }
            }
            val frame = RoundedCornerShape(12.dp)
            Box(Modifier.align(Alignment.Center).width(itemW).fillMaxHeight(0.92f).border(2.dp, C.Amber, frame))
        }
    }
}
