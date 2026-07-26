package com.changewave.ombraparking.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.changewave.ombraparking.OmbraParkingApplication
import com.changewave.ombraparking.core.geo.DataCoverage
import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.osm.Place
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
import com.changewave.ombraparking.data.PlaceRepository
import com.changewave.ombraparking.data.SunAlarmScheduler
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
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
    val zone: TimeZone = TimeZone.currentSystemDefault(),
    val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    /** Ora scelta con lo slider, in minuti dalla mezzanotte. */
    val minuteOfDay: Int = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .let { it.hour * 60 + it.minute },
    val userLocation: LatLng? = null,
    /** Punto di cui si vuole sapere l'ombra: di default la posizione dell'utente. */
    val target: LatLng? = null,
    /** Finché è vero il bersaglio segue l'utente; toccando la mappa si sgancia. */
    val targetFollowsUser: Boolean = true,
    /**
     * Zona su cui si sta ragionando quando non è quella in cui ci si trova.
     * Serve a rispondere a "domani parcheggio in centro: dove sarà ombra alle 15?".
     */
    val explorationCenter: LatLng? = null,
    /** Nome della zona esplorata, quando è stata scelta cercandola. */
    val explorationName: String? = null,
    val placeResults: List<Place> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val plane: LocalPlane? = null,
    val obstacles: List<Obstacle> = emptyList(),
    /** Raggio entro cui sono stati scaricati gli ostacoli intorno a [plane]. */
    val dataRadiusMeters: Int = ObstacleRepository.DEFAULT_RADIUS_M,
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
        get() = date.atStartOfDayIn(zone) + minuteOfDay.minutes

    val hasData: Boolean get() = plane != null

    /** Vero quando si stanno guardando le ombre di un posto diverso da dove ci si trova. */
    val isExploring: Boolean get() = explorationCenter != null

    /** Punto intorno a cui vengono scaricati gli edifici. */
    val analysisCenter: LatLng? get() = explorationCenter ?: userLocation

    /**
     * Vero se la posizione dell'utente è dentro l'area di cui conosciamo gli edifici:
     * è la condizione perché la vista in realtà aumentata abbia qualcosa da mostrare.
     */
    val coversUserLocation: Boolean
        get() {
            val origin = plane?.origin ?: return false
            val user = userLocation ?: return false
            return DataCoverage.isReliable(origin, user, dataRadiusMeters)
        }

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
    private val placeRepository: PlaceRepository =
        (application as OmbraParkingApplication).placeRepository
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
    private var searchJob: Job? = null

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

        // Mentre si esplora un'altra zona il GPS aggiorna solo il puntino blu: spostare i
        // dati dietro a chi cammina vanificherebbe la scelta appena fatta.
        if (previous.isExploring) return

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
        val zone = _state.value.zone
        val now = Clock.System.now().toLocalDateTime(zone)
        _state.value = _state.value.copy(date = now.date)
        setTime(now.hour * 60 + now.minute)
    }

    /**
     * Punto scelto toccando la mappa.
     *
     * Se cade fuori dall'area di cui abbiamo gli edifici, quella zona diventa la nuova zona
     * analizzata e i dati vengono riscaricati lì: toccare un punto lontano vuol dire volere
     * la risposta *in quel punto*, non un verdetto basato su edifici che non conosciamo.
     */
    fun selectTarget(position: LatLng) {
        val snapshot = _state.value
        val origin = snapshot.plane?.origin
        val needsData = origin == null ||
            !DataCoverage.isReliable(origin, position, snapshot.dataRadiusMeters)

        _state.value = snapshot.copy(
            target = position,
            targetFollowsUser = false,
            explorationCenter = if (needsData) position else snapshot.explorationCenter,
            explorationName = if (needsData) null else snapshot.explorationName,
        )

        if (needsData) loadObstacles(position) else recompute()
    }

    /** Cerca un luogo per nome; i risultati finiscono in [ShadowUiState.placeResults]. */
    fun searchPlaces(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(placeResults = emptyList(), isSearchingPlaces = false)
            return
        }
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isSearchingPlaces = true, errorMessage = null)
            try {
                val places = placeRepository.search(query)
                _state.value = _state.value.copy(
                    placeResults = places,
                    isSearchingPlaces = false,
                    errorMessage = if (places.isEmpty()) "Nessun luogo trovato per \"$query\"" else null,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isSearchingPlaces = false,
                    errorMessage = "Ricerca non riuscita: ${error.message ?: "rete non raggiungibile"}",
                )
            }
        }
    }

    fun clearPlaceResults() {
        searchJob?.cancel()
        _state.value = _state.value.copy(placeResults = emptyList(), isSearchingPlaces = false)
    }

    /** Sposta l'analisi sul luogo scelto fra i risultati della ricerca. */
    fun explorePlace(place: Place) {
        _state.value = _state.value.copy(
            explorationCenter = place.position,
            explorationName = place.shortName,
            target = place.position,
            targetFollowsUser = false,
            placeResults = emptyList(),
            isSearchingPlaces = false,
            errorMessage = null,
        )
        loadObstacles(place.position)
    }

    /** Riporta analisi e dati sulla propria posizione, uscendo dall'esplorazione. */
    fun followUserLocation() {
        val snapshot = _state.value
        val user = snapshot.userLocation ?: return
        val wasExploring = snapshot.isExploring
        _state.value = snapshot.copy(
            target = user,
            targetFollowsUser = true,
            explorationCenter = null,
            explorationName = null,
        )
        if (wasExploring) loadObstacles(user) else recompute()
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
                    dataRadiusMeters = set.radiusMeters,
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
                        from = snapshot.date.atStartOfDayIn(snapshot.zone),
                        to = snapshot.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(snapshot.zone),
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
        if (!DataCoverage.isReliable(carLocal, snapshot.dataRadiusMeters)) return null

        val now = Clock.System.now()
        val info = ShadowEngine.shadeAt(carLocal, snapshot.obstacles, SolarPosition.at(now, car.position))
        val endOfDay = Clock.System.todayIn(snapshot.zone)
            .plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(snapshot.zone)
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
    }
}
