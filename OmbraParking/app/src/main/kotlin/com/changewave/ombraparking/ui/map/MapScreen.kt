package com.changewave.ombraparking.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.osm.Place
import com.changewave.ombraparking.core.shadow.ObstacleKind
import com.changewave.ombraparking.ui.ShadowUiState
import com.changewave.ombraparking.ui.components.ExplorationBanner
import com.changewave.ombraparking.ui.components.ParkedCarBar
import com.changewave.ombraparking.ui.components.PlaceSearchBar
import com.changewave.ombraparking.ui.components.QuickTimeChips
import com.changewave.ombraparking.ui.components.ShadeSummaryCard
import com.changewave.ombraparking.ui.components.ShadeTimelineStrip
import java.time.LocalDate
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

/**
 * Vista dall'alto: mappa OpenStreetMap con le ombre proiettate all'ora scelta.
 * Toccando un punto si sposta il bersaglio dell'analisi.
 */
@Composable
fun MapScreen(
    state: ShadowUiState,
    onSelectTarget: (LatLng) -> Unit,
    onFollowUser: () -> Unit,
    onMinuteSelected: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onNow: () -> Unit,
    onRefresh: () -> Unit,
    onPark: () -> Unit,
    onClearParkedCar: () -> Unit,
    onSearchPlace: (String) -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onDismissPlaceResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSelectTarget by rememberUpdatedState(onSelectTarget)

    val shadowOverlay = remember { ShadowMapOverlay() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(18.0)
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let { currentOnSelectTarget(LatLng(it.latitude, it.longitude)) }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
            )
            overlays.add(shadowOverlay)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    var hasCentered by remember { mutableStateOf(false) }

    // Scegliere un'altra zona porta la mappa lì: altrimenti si vedrebbero le ombre
    // ricalcolate ma la vista resterebbe dove si è.
    LaunchedEffect(state.explorationCenter) {
        state.explorationCenter?.let { center ->
            mapView.controller.animateTo(GeoPoint(center.latitude, center.longitude))
        }
    }

    val shadowGeometry = remember(state.shadowShapes, state.plane) {
        toGeoPoints(state.plane, state.shadowShapes)
    }
    val buildingGeometry = remember(state.obstacles, state.plane) {
        toGeoPoints(
            state.plane,
            state.obstacles.filter { it.kind == ObstacleKind.BUILDING }.map { it.footprint },
        )
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                shadowOverlay.shadowPolygons = shadowGeometry
                shadowOverlay.buildingFootprints = buildingGeometry
                shadowOverlay.target = state.target?.let { GeoPoint(it.latitude, it.longitude) }
                shadowOverlay.userPosition = state.userLocation?.let { GeoPoint(it.latitude, it.longitude) }
                shadowOverlay.parkedCar = state.parkedCar?.let {
                    GeoPoint(it.position.latitude, it.position.longitude)
                }

                val center = state.userLocation ?: state.target
                if (!hasCentered && center != null) {
                    view.controller.setCenter(GeoPoint(center.latitude, center.longitude))
                    hasCentered = true
                }
                view.invalidate()
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallFloatingActionButton(onClick = {
                onFollowUser()
                state.userLocation?.let {
                    mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                }
            }) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Centra sulla mia posizione")
            }
            SmallFloatingActionButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Ricarica gli edifici")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.78f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaceSearchBar(
                results = state.placeResults,
                isSearching = state.isSearchingPlaces,
                onSearch = onSearchPlace,
                onPlaceSelected = onPlaceSelected,
                onDismissResults = onDismissPlaceResults,
            )
            if (state.isExploring) {
                ExplorationBanner(
                    name = state.explorationName,
                    onBackToMyPosition = {
                        onFollowUser()
                        state.userLocation?.let {
                            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                        }
                    },
                )
            }
            StatusBanner(state = state)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShadeSummaryCard(state)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParkedCarBar(
                        parkedCar = state.parkedCar,
                        status = state.parkedCarStatus,
                        userLocation = state.userLocation,
                        zone = state.zone,
                        onPark = onPark,
                        onClear = onClearParkedCar,
                        onShowCar = {
                            state.parkedCar?.let { car ->
                                mapView.controller.animateTo(
                                    GeoPoint(car.position.latitude, car.position.longitude)
                                )
                            }
                        },
                    )
                    ShadeTimelineStrip(
                        forecast = state.forecast,
                        dayStart = state.date.atStartOfDay(state.zone).toInstant(),
                        selectedMinute = state.minuteOfDay,
                        onMinuteSelected = onMinuteSelected,
                    )
                    QuickTimeChips(
                        date = state.date,
                        zone = state.zone,
                        selectedMinute = state.minuteOfDay,
                        onDateSelected = onDateSelected,
                        onMinuteSelected = onMinuteSelected,
                        onNow = onNow,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(state: ShadowUiState, modifier: Modifier = Modifier) {
    val message = when {
        state.errorMessage != null -> state.errorMessage
        state.isLoadingObstacles -> "Sto scaricando edifici e alberi…"
        state.plane != null && state.obstacles.isEmpty() -> "Nessun edificio mappato qui intorno"
        else -> null
    } ?: return

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (state.isLoadingObstacles) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(bottom = 2.dp),
                )
            }
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Le sagome sono in metri locali: la mappa vuole latitudine e longitudine. */
private fun toGeoPoints(plane: LocalPlane?, polygons: List<List<Vec2>>): List<List<GeoPoint>> {
    if (plane == null) return emptyList()
    return polygons.map { polygon ->
        polygon.map { point ->
            val latLng = plane.toLatLng(point)
            GeoPoint(latLng.latitude, latLng.longitude)
        }
    }
}
