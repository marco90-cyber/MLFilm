package com.marco.mlfilm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }
}

private val Amber = Color(0xFFF5A623)

@Composable
private fun Root() {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0C))) {
        if (!granted) {
            Text("SERVE IL PERMESSO CAMERA", color = Amber, modifier = Modifier.align(Alignment.Center))
        } else {
            Interim()
        }
    }
}

/** Schermata provvisoria: mirino + tre liste di selezione. L'interfaccia definitiva arriva dopo. */
@Composable
private fun Interim() {
    var ci by remember { mutableIntStateOf(1) }
    var li by remember { mutableIntStateOf(2) }
    var fi by remember { mutableIntStateOf(4) }
    val look = remember(ci, li, fi) { buildLook(CAMERAS[ci], LENSES[li], FILMS[fi]) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Viewfinder(look, Modifier.fillMaxWidth())
        Picker("CAMERA", CAMERAS.map { it.name }, ci) { ci = it }
        Picker("LENS", LENSES.map { it.name }, li) { li = it }
        Picker("FILM", FILMS.map { it.name }, fi) { fi = it }
    }
}

@Composable
private fun Picker(title: String, names: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Text(title, color = Color(0xFF8A8A8A))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(names.size) { i ->
            Text(
                names[i],
                color = if (i == selected) Amber else Color(0xFFBBBBBB),
                modifier = Modifier.clickable { onPick(i) },
            )
        }
    }
}
