package com.marco.mlfilm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Foto arrivate da "Condividi" o "Apri con"; la schermata le prende e le svuota. */
    private var incoming by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCtx.app = applicationContext
        enableEdgeToEdge()
        if (savedInstanceState == null) incoming = urisFrom(intent)
        setContent { Root(incoming) { incoming = emptyList() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val u = urisFrom(intent)
        if (u.isNotEmpty()) incoming = u
    }

    private fun urisFrom(i: Intent?): List<Uri> {
        i ?: return emptyList()
        return when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            Intent.ACTION_VIEW -> listOfNotNull(i.data)
            else -> emptyList()
        }
    }
}

@Composable
private fun Root(incoming: List<Uri>, onIncomingTaken: () -> Unit) {
    val ctx = LocalContext.current

    // Fino ad Android 9 serve il permesso per salvare nella galleria
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { PhotoIO.cleanCache(ctx) }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    // Coda di foto da modificare: si apre la prima, poi la successiva dopo ogni salvataggio
    var queue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    suspend fun enqueue(uris: List<Uri>) {
        val copies = withContext(Dispatchers.IO) { uris.mapNotNull { PhotoIO.importToCache(ctx, it) } }
        if (copies.isNotEmpty()) queue = copies
    }

    LaunchedEffect(incoming) {
        if (incoming.isNotEmpty()) {
            val taken = incoming
            onIncomingTaken()
            enqueue(taken)
        }
    }

    var pending by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            val p = pending
            pending = emptyList()
            enqueue(p)
        }
    }

    BackHandler(enabled = queue.isNotEmpty()) { queue = emptyList() }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        val current = queue.firstOrNull()
        if (current != null) {
            val total = queue.size
            key(current) {
                EditorScreen(
                    current,
                    if (total > 1) "$total DA MODIFICARE" else null,
                    { queue = emptyList() },
                    { queue = queue.drop(1); refresh++ },
                )
            }
        } else {
            HomeScreen(refresh, { pending = it }, { pending = listOf(it) })
        }
    }
}
