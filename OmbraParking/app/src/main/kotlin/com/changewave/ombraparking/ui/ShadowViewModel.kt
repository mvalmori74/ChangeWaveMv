package com.changewave.ombraparking.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.changewave.ombraparking.OmbraParkingApplication
import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.shadow.Obstacle
import com.changewave.ombraparking.core.shadow.ShadeForecast
import com.changewave.ombraparking.core.shadow.ShadeInfo
import com.changewave.ombraparking.core.shadow.ShadeQuality
import com.changewave.ombraparking.core.shadow.ShadeTimeline
import com.changewave.ombraparking.core.shadow.ShadowEngine
import com.changewave.ombraparking.core.sun.DayLight
import com.changewave.ombraparking.core.sun.SolarPosition
import com.changewave.ombraparking.core.sun.SunPosition
import com.changewave.ombraparking.core.sun.SunTimes
import com.changewave.ombraparking.data.LocationTracker
import com.changewave.ombraparking.data.ObstacleRepository
import com.changewave.ombraparking.data.ParkedCar
import com.changewave.ombraparking.data.ParkedCarStore
import com.changewave.ombraparking.data.SunAlarmScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Situazione dell'auto parcheggiata **adesso**, non all'ora dello slider: qui interessa la
 * realtà, non la simulazione.
 */
data class ParkedCarStatus(
    val quality: ShadeQuality,
    val obstacleName: String?,
    /** Quando il sole tornerà a colpire l'auto, se ora è in ombra. */
    val sunArrivesAt: Instant?,
    /** Quando l'auto tornerà in ombra, se ora è al sole. */
    val shadeArrivesAt: Instant?,
    val computedAt: Instant,
)

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
    /** Posto auto salvato, se c'è. */
    val parkedCar: ParkedCar? = null,
    /** Null se l'auto è troppo lontana dagli edifici scaricati per dire qualcosa di sensato. */
    val parkedCarStatus: ParkedCarStatus? = null,
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
    private val parkedCarStore: ParkedCarStore =
        (application as OmbraParkingApplication).parkedCarStore
    private val sunAlarmScheduler: SunAlarmScheduler =
        (application as OmbraParkingApplication).sunAlarmScheduler
    private val locationTracker = LocationTracker(application)

    private val _state = MutableStateFlow(ShadowUiState())
    val state: StateFlow<ShadowUiState> = _state.asStateFlow()

    private var locationJob: Job? = null
    private var loadJob: Job? = null
    private var computeJob: Job? = null

    /** Centro dell'ultima richiesta andata a buon fine: dice quando ci si è spostati davvero. */
    private var lastLoadedCenter: LatLng? = null

    /** Centro della richiesta in volo, valido solo mentre [loadJob] è attivo. */
    private var loadingCenter: LatLng? = null

    /** Quando è fallito l'ultimo tentativo: senza dati il GPS ne chiederebbe uno a ogni fix. */
    private var lastFailureAtMillis = 0L

    init {
        viewModelScope.launch {
            parkedCarStore.parkedCar.collect { car ->
                _state.value = _state.value.copy(parkedCar = car, parkedCarStatus = null)
                recompute()
            }
        }
    }

    /**
     * Salva il posto auto sul punto attualmente puntato: quello scelto sulla mappa se
     * l'utente ne ha indicato uno, altrimenti la sua posizione.
     */
    fun parkHere() {
        val snapshot = _state.value
        val position = if (snapshot.targetFollowsUser) {
            snapshot.userLocation ?: snapshot.target
        } else {
            snapshot.target
        }
        if (position == null) {
            _state.value = snapshot.copy(errorMessage = "Aspetto la posizione per salvare il posto auto")
            return
        }
        _state.value = snapshot.copy(errorMessage = null)
        parkedCarStore.save(position)
    }

    fun clearParkedCar() {
        parkedCarStore.clear()
    }

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

    /**
     * Scarica gli ostacoli intorno a [center].
     *
     * Overpass può metterci diversi secondi, e nel frattempo il GPS continua a mandare
     * aggiornamenti: se ognuno facesse ripartire il download annullerebbe quello in corso,
     * e il primo caricamento non arriverebbe mai in fondo. Finché una richiesta per questa
     * stessa zona è in volo, quindi, si lascia lavorare quella.
     */
    private fun loadObstacles(center: LatLng, forceRefresh: Boolean = false) {
        val inFlight = loadingCenter
        if (!forceRefresh && loadJob?.isActive == true && inFlight != null &&
            LocalPlane(inFlight).distanceMeters(inFlight, center) <= RELOAD_DISTANCE_M
        ) {
            return
        }

        // Dopo un errore si riprova da soli al prossimo fix, ma non prima del tempo di attesa:
        // se Overpass è in affanno, tempestarlo di richieste non aiuta nessuno.
        val sinceFailure = SystemClock.elapsedRealtime() - lastFailureAtMillis
        if (!forceRefresh && lastFailureAtMillis > 0L && sinceFailure < RETRY_COOLDOWN_MS) return

        loadJob?.cancel()
        loadingCenter = center
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingObstacles = true, errorMessage = null)
            try {
                val set = repository.obstaclesAround(center, forceRefresh = forceRefresh)
                lastLoadedCenter = set.center
                lastFailureAtMillis = 0L
                _state.value = _state.value.copy(
                    plane = set.plane,
                    obstacles = set.obstacles,
                    isLoadingObstacles = false,
                )
                recompute()
            } catch (cancellation: CancellationException) {
                // Download sostituito da uno più recente: non è un errore da mostrare.
                throw cancellation
            } catch (error: Exception) {
                lastFailureAtMillis = SystemClock.elapsedRealtime()
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
                // Lo stato dell'auto guarda l'ora vera, quindi non cambia muovendo lo slider.
                val parkedStatus = if (recomputeForecast) {
                    parkedCarStatus(snapshot, plane)
                } else {
                    snapshot.parkedCarStatus
                }
                Computed(sun, shapes, shade, dayLight, forecast, parkedStatus)
            }

            _state.value = _state.value.copy(
                sun = result.sun,
                shadowShapes = result.shapes,
                shade = result.shade,
                dayLight = result.dayLight,
                forecast = result.forecast,
                parkedCarStatus = result.parkedCarStatus,
            )
            updateSunAlarm(snapshot.parkedCar, result.parkedCarStatus)
        }
    }

    /**
     * Tiene allineato l'avviso a quello che sappiamo adesso.
     *
     * Se l'auto è fuori dalla zona analizzata non tocchiamo l'avviso già fissato: era stato
     * calcolato quando i dati c'erano e resta valido, perché l'auto non si è mossa.
     */
    private fun updateSunAlarm(car: ParkedCar?, status: ParkedCarStatus?) {
        val current = _state.value.parkedCar
        if (current == null) {
            sunAlarmScheduler.cancel()
            return
        }
        // L'auto è cambiata mentre calcolavamo: il ricalcolo appena partito sistemerà l'avviso.
        if (current != car) return
        if (status == null) return
        sunAlarmScheduler.schedule(current, status.sunArrivesAt)
    }

    /**
     * Ombra sull'auto adesso e prossimo cambio.
     * Restituisce null se l'auto è fuori dalla zona di cui conosciamo gli edifici: meglio
     * non dire niente che dire "pieno sole" solo perché lì non abbiamo dati.
     */
    private fun parkedCarStatus(snapshot: ShadowUiState, plane: LocalPlane): ParkedCarStatus? {
        val car = snapshot.parkedCar ?: return null
        val carLocal = plane.toLocal(car.position)
        if (carLocal.length > PARKED_ANALYSIS_RADIUS_M) return null

        val now = Instant.now()
        val info = ShadowEngine.shadeAt(carLocal, snapshot.obstacles, SolarPosition.at(now, car.position))
        val endOfDay = LocalDate.now(snapshot.zone).plusDays(1).atStartOfDay(snapshot.zone).toInstant()
        val forecast = ShadeTimeline.compute(
            point = carLocal,
            obstacles = snapshot.obstacles,
            location = car.position,
            from = now,
            to = endOfDay,
            stepMinutes = FORECAST_STEP_MINUTES,
        )

        return ParkedCarStatus(
            quality = info.quality,
            obstacleName = info.obstacle?.name,
            sunArrivesAt = forecast.nextSunStart(now),
            shadeArrivesAt = forecast.nextShadeStart(now),
            computedAt = now,
        )
    }

    private class Computed(
        val sun: SunPosition,
        val shapes: List<List<Vec2>>,
        val shade: ShadeInfo?,
        val dayLight: DayLight,
        val forecast: ShadeForecast?,
        val parkedCarStatus: ParkedCarStatus?,
    )

    private companion object {
        /** Oltre questo spostamento vale la pena riscaricare gli edifici intorno. */
        const val RELOAD_DISTANCE_M = 150.0

        /** Attesa minima fra due tentativi automatici dopo un errore di rete. */
        const val RETRY_COOLDOWN_MS = 20_000L
        const val FORECAST_STEP_MINUTES = 10

        /** Distanza dal centro dei dati oltre la quale non si azzarda un verdetto sull'auto. */
        const val PARKED_ANALYSIS_RADIUS_M = 250.0
    }
}
