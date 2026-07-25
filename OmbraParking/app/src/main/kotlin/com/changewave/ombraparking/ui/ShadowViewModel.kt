package com.changewave.ombraparking.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.changewave.ombraparking.OmbraParkingApplication
import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.shadow.Obstacle
import com.changewave.ombraparking.core.shadow.ShadeForecast
import com.changewave.ombraparking.core.shadow.ShadeInfo
import com.changewave.ombraparking.core.shadow.ShadeTimeline
import com.changewave.ombraparking.core.shadow.ShadowEngine
import com.changewave.ombraparking.core.sun.DayLight
import com.changewave.ombraparking.core.sun.SolarPosition
import com.changewave.ombraparking.core.sun.SunPosition
import com.changewave.ombraparking.core.sun.SunTimes
import com.changewave.ombraparking.data.LocationTracker
import com.changewave.ombraparking.data.ObstacleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stato unico condiviso da mappa e realtà aumentata: le due viste mostrano gli stessi
 * dati con occhi diversi, quindi tenere due stati separati porterebbe solo a divergenze.
 */
data class ShadowUiState(
    val zone: ZoneId = ZoneId.systemDefault(),
    val date: LocalDate = LocalDate.now(),
    /** Ora scelta con lo slider, in minuti dalla mezzanotte. */
    val minuteOfDay: Int = LocalTime.now().hour * 60 + LocalTime.now().minute,
    val userLocation: LatLng? = null,
    /** Punto di cui si vuole sapere l'ombra: di default la posizione dell'utente. */
    val target: LatLng? = null,
    /** Finché è vero il bersaglio segue l'utente; toccando la mappa si sgancia. */
    val targetFollowsUser: Boolean = true,
    val plane: LocalPlane? = null,
    val obstacles: List<Obstacle> = emptyList(),
    /** Sagome d'ombra all'ora scelta, in metri locali rispetto a [plane]. */
    val shadowShapes: List<List<Vec2>> = emptyList(),
    val sun: SunPosition? = null,
    val dayLight: DayLight? = null,
    val shade: ShadeInfo? = null,
    val forecast: ShadeForecast? = null,
    val isLoadingObstacles: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Istante corrispondente a data e ora scelte. */
    val instant: Instant
        get() = date.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant()

    val hasData: Boolean get() = plane != null

    /** Posizione del bersaglio nel piano locale, se entrambi sono noti. */
    val targetLocal: Vec2?
        get() {
            val currentPlane = plane ?: return null
            val currentTarget = target ?: return null
            return currentPlane.toLocal(currentTarget)
        }
}

class ShadowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ObstacleRepository =
        (application as OmbraParkingApplication).obstacleRepository
    private val locationTracker = LocationTracker(application)

    private val _state = MutableStateFlow(ShadowUiState())
    val state: StateFlow<ShadowUiState> = _state.asStateFlow()

    private var locationJob: Job? = null
    private var loadJob: Job? = null
    private var computeJob: Job? = null

    /** Centro dell'ultima richiesta: serve a capire quando l'utente si è spostato davvero. */
    private var lastLoadedCenter: LatLng? = null

    fun startLocationUpdates() {
        if (locationJob?.isActive == true || !locationTracker.hasPermission()) return
        locationJob = viewModelScope.launch {
            locationTracker.updates().collect { update ->
                onLocationUpdate(update.position)
            }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun onLocationUpdate(position: LatLng) {
        val previous = _state.value
        val isFirstFix = previous.userLocation == null
        _state.value = previous.copy(
            userLocation = position,
            // Finché l'utente non sceglie un punto sulla mappa, il bersaglio lo segue.
            target = if (previous.target == null || previous.targetFollowsUser) position else previous.target,
        )

        val center = lastLoadedCenter
        val movedFar = center == null || LocalPlane(center).distanceMeters(center, position) > RELOAD_DISTANCE_M
        if (isFirstFix || movedFar) {
            loadObstacles(position)
        } else {
            recompute()
        }
    }

    fun setTime(minuteOfDay: Int) {
        _state.value = _state.value.copy(minuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1))
        recompute(recomputeForecast = false)
    }

    fun setDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
        recompute()
    }

    /** Riporta lo slider all'ora attuale. */
    fun useCurrentTime() {
        val now = LocalTime.now(_state.value.zone)
        _state.value = _state.value.copy(date = LocalDate.now(_state.value.zone))
        setTime(now.hour * 60 + now.minute)
    }

    /** Punto scelto toccando la mappa. */
    fun selectTarget(position: LatLng) {
        _state.value = _state.value.copy(target = position, targetFollowsUser = false)
        recompute()
    }

    /** Riporta l'analisi sulla propria posizione. */
    fun followUserLocation() {
        val user = _state.value.userLocation ?: return
        _state.value = _state.value.copy(target = user, targetFollowsUser = true)
        recompute()
    }

    fun refresh() {
        val center = _state.value.userLocation ?: _state.value.target ?: return
        loadObstacles(center, forceRefresh = true)
    }

    private fun loadObstacles(center: LatLng, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingObstacles = true, errorMessage = null)
            try {
                val set = repository.obstaclesAround(center, forceRefresh = forceRefresh)
                lastLoadedCenter = set.center
                _state.value = _state.value.copy(
                    plane = set.plane,
                    obstacles = set.obstacles,
                    isLoadingObstacles = false,
                )
                recompute()
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoadingObstacles = false,
                    errorMessage = "Non riesco a scaricare gli edifici: ${error.message ?: "rete non raggiungibile"}",
                )
            }
        }
    }

    /**
     * Ricalcola sole, sagome d'ombra e previsione per lo stato corrente.
     * Gira fuori dal thread principale: con qualche centinaio di edifici la previsione
     * giornaliera fa decine di migliaia di test di intersezione.
     */
    private fun recompute(recomputeForecast: Boolean = true) {
        val snapshot = _state.value
        val plane = snapshot.plane ?: return

        computeJob?.cancel()
        computeJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val instant = snapshot.instant
                val reference = snapshot.target ?: plane.origin
                val sun = SolarPosition.at(instant, reference)
                val shapes = ShadowEngine.shadowShapes(snapshot.obstacles, sun)
                val targetLocal = snapshot.targetLocal
                val shade = targetLocal?.let { ShadowEngine.shadeAt(it, snapshot.obstacles, sun) }
                val dayLight = SunTimes.forDay(snapshot.date, snapshot.zone, reference)
                val forecast = if (recomputeForecast && targetLocal != null) {
                    ShadeTimeline.compute(
                        point = targetLocal,
                        obstacles = snapshot.obstacles,
                        location = reference,
                        from = snapshot.date.atStartOfDay(snapshot.zone).toInstant(),
                        to = snapshot.date.plusDays(1).atStartOfDay(snapshot.zone).toInstant(),
                        stepMinutes = FORECAST_STEP_MINUTES,
                    )
                } else {
                    snapshot.forecast
                }
                Computed(sun, shapes, shade, dayLight, forecast)
            }

            _state.value = _state.value.copy(
                sun = result.sun,
                shadowShapes = result.shapes,
                shade = result.shade,
                dayLight = result.dayLight,
                forecast = result.forecast,
            )
        }
    }

    private class Computed(
        val sun: SunPosition,
        val shapes: List<List<Vec2>>,
        val shade: ShadeInfo?,
        val dayLight: DayLight,
        val forecast: ShadeForecast?,
    )

    private companion object {
        /** Oltre questo spostamento vale la pena riscaricare gli edifici intorno. */
        const val RELOAD_DISTANCE_M = 150.0
        const val FORECAST_STEP_MINUTES = 10
    }
}
