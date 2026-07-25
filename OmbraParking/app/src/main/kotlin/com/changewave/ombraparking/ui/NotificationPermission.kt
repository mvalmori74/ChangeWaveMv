package com.changewave.ombraparking.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Chiede il permesso delle notifiche la prima volta che [enabled] diventa vero, cioè
 * quando l'utente salva il posto auto.
 *
 * Viene chiesto lì e non all'avvio perché è l'unico momento in cui la richiesta si spiega
 * da sola: prima di avere un'auto parcheggiata non ci sarebbe niente da notificare. Se il
 * permesso viene negato l'app continua a funzionare, semplicemente senza avviso.
 */
@Composable
fun NotificationPermissionEffect(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* concesso o no, non cambia nient'altro nell'app */ },
    )

    LaunchedEffect(enabled, alreadyAsked) {
        if (!enabled || alreadyAsked) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        alreadyAsked = true
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
