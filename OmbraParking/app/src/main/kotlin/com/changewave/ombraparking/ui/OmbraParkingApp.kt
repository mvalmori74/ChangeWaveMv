package com.changewave.ombraparking.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.changewave.ombraparking.ui.ar.ArScreen
import com.changewave.ombraparking.ui.map.MapScreen

private enum class Destination(val title: String, val emoji: String) {
    MAP("Mappa", "🗺"),
    AR("Realtà aumentata", "📷"),
}

@Composable
fun OmbraParkingApp(viewModel: ShadowViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var destination by rememberSaveable { mutableStateOf(Destination.MAP) }
    var hasLocationPermission by remember { mutableStateOf(context.hasAnyLocationPermission()) }
    var hasCameraPermission by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.CAMERA))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.keys.any { it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION }) {
            hasLocationPermission = context.hasAnyLocationPermission()
        }
        if (result.containsKey(Manifest.permission.CAMERA)) {
            hasCameraPermission = context.hasPermission(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Il permesso delle notifiche si chiede solo quando c'è un'auto da tenere d'occhio.
    NotificationPermissionEffect(enabled = state.parkedCar != null)

    // La posizione serve solo mentre l'app è in primo piano.
    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (hasLocationPermission) viewModel.startLocationUpdates()
                Lifecycle.Event.ON_STOP -> viewModel.stopLocationUpdates()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationUpdates()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = {
                            destination = item
                            if (item == Destination.AR && !hasCameraPermission) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        icon = { Text(item.emoji) },
                        label = { Text(item.title) },
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !hasLocationPermission -> PermissionRequest(
                    title = "Serve la posizione",
                    body = "Ombra Parking calcola le ombre degli edifici intorno a te: " +
                        "senza posizione non sa quali edifici guardare.",
                    onRequest = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                )

                destination == Destination.MAP -> MapScreen(
                    state = state,
                    onSelectTarget = viewModel::selectTarget,
                    onFollowUser = viewModel::followUserLocation,
                    onMinuteSelected = viewModel::setTime,
                    onDateSelected = viewModel::setDate,
                    onNow = viewModel::useCurrentTime,
                    onRefresh = viewModel::refresh,
                    onPark = viewModel::parkHere,
                    onClearParkedCar = viewModel::clearParkedCar,
                    onSearchPlace = viewModel::searchPlaces,
                    onPlaceSelected = viewModel::explorePlace,
                    onDismissPlaceResults = viewModel::clearPlaceResults,
                )

                !hasCameraPermission -> PermissionRequest(
                    title = "Serve la fotocamera",
                    body = "La vista in realtà aumentata sovrappone le ombre a quello che inquadri.",
                    onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
                )

                else -> ArScreen(
                    state = state,
                    onMinuteSelected = viewModel::setTime,
                    onDateSelected = viewModel::setDate,
                    onNow = viewModel::useCurrentTime,
                    onPark = viewModel::parkHere,
                    onClearParkedCar = viewModel::clearParkedCar,
                    onBackToMyPosition = viewModel::followUserLocation,
                )
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    title: String,
    body: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onRequest) { Text("Concedi il permesso") }
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun android.content.Context.hasAnyLocationPermission(): Boolean =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
