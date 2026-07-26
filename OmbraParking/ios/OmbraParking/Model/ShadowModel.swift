import Combine
import CoreLocation
import Foundation
import OmbraCore

/// Situazione dell'auto parcheggiata **adesso**, non all'ora dello slider.
struct ParkedCarStatus {
    let quality: ShadeQuality
    let obstacleName: String?
    let sunArrivesAt: Date?
    let shadeArrivesAt: Date?
    let computedAt: Date
}

/// Sagoma d'ombra pronta per la mappa.
struct ShadowPolygon: Identifiable {
    let id: Int
    let coordinates: [CLLocationCoordinate2D]
}

/**
 Stato condiviso da mappa e realtà aumentata, come nell'app Android: le due viste mostrano
 gli stessi dati con occhi diversi, e tenerli separati porterebbe solo a divergenze.

 Tutti i calcoli passano dal core Kotlin: qui non c'è una riga di matematica solare, e non
 può quindi discostarsi da quella verificata dai test.
 */
@MainActor
final class ShadowModel: ObservableObject {

    // Dati della zona
    @Published private(set) var plane: LocalPlane?
    @Published private(set) var obstacles: [Obstacle] = []
    @Published private(set) var dataRadiusMeters: Int32 = ObstacleService.defaultRadius
    @Published private(set) var shadowPolygons: [ShadowPolygon] = []

    // Posizione e bersaglio
    @Published private(set) var userLocation: LatLng?
    @Published private(set) var target: LatLng?
    @Published private(set) var targetFollowsUser = true
    @Published private(set) var explorationCenter: LatLng?
    @Published private(set) var explorationName: String?

    // Tempo scelto
    @Published private(set) var date: KotlinLocalDate = .from(Date())
    @Published var minuteOfDay: Int = {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: Date())
        return (parts.hour ?? 12) * 60 + (parts.minute ?? 0)
    }()

    // Esiti
    @Published private(set) var sun: SunPosition?
    @Published private(set) var dayLight: DayLight?
    @Published private(set) var shade: ShadeInfo?
    @Published private(set) var forecast: ShadeForecast?
    @Published private(set) var parkedCarStatus: ParkedCarStatus?

    // Ricerca luoghi
    @Published private(set) var placeResults: [Place] = []
    @Published private(set) var isSearchingPlaces = false

    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    let parkedCarStore = ParkedCarStore()

    private let obstacleService = ObstacleService()
    private let placeService = PlaceService()
    private let zone = KotlinTimeZone.current

    private var loadTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?
    private var lastLoadedCenter: LatLng?
    private var cancellables = Set<AnyCancellable>()

    /// Massimo di sagome disegnate sulla mappa: oltre, il disegno costa più di quanto renda.
    private static let maxDrawnPolygons = 500

    init() {
        parkedCarStore.$parkedCar
            .sink { [weak self] _ in
                Task { @MainActor in self?.recompute() }
            }
            .store(in: &cancellables)
    }

    var isExploring: Bool { explorationCenter != nil }

    var instant: Date {
        date.startOfDay(zone: zone).date.addingTimeInterval(Double(minuteOfDay) * 60)
    }

    /// Vero se l'utente si trova dentro l'area di cui conosciamo gli edifici.
    var coversUserLocation: Bool {
        guard let origin = plane?.origin, let user = userLocation else { return false }
        return DataCoverage.shared.isReliable(
            center: origin,
            point: user,
            radiusMeters: dataRadiusMeters
        )
    }

    // MARK: - Posizione

    func onLocationUpdate(_ position: LatLng) {
        let isFirstFix = userLocation == nil
        userLocation = position
        if targetFollowsUser || target == nil { target = position }

        // Mentre si esplora un'altra zona il GPS aggiorna solo il puntino: spostare i dati
        // dietro a chi cammina vanificherebbe la scelta appena fatta.
        guard !isExploring else { return }

        let movedFar: Bool
        if let center = lastLoadedCenter {
            movedFar = LocalPlane(origin: center).distanceMeters(a: center, b: position) > 150
        } else {
            movedFar = true
        }
        if isFirstFix || movedFar {
            load(around: position)
        } else {
            recompute()
        }
    }

    // MARK: - Scelte dell'utente

    func setTime(minuteOfDay newValue: Int) {
        minuteOfDay = min(max(newValue, 0), 24 * 60 - 1)
        recompute(recomputeForecast: false)
    }

    func setDate(_ newDate: KotlinLocalDate) {
        date = newDate
        recompute()
    }

    func useCurrentTime() {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: Date())
        date = .from(Date())
        setTime(minuteOfDay: (parts.hour ?? 12) * 60 + (parts.minute ?? 0))
    }

    /// Punto scelto toccando la mappa: se cade fuori dall'area scaricata, i dati si spostano lì.
    func selectTarget(_ position: LatLng) {
        let needsData: Bool
        if let origin = plane?.origin {
            needsData = !DataCoverage.shared.isReliable(
                center: origin,
                point: position,
                radiusMeters: dataRadiusMeters
            )
        } else {
            needsData = true
        }

        target = position
        targetFollowsUser = false
        if needsData {
            explorationCenter = position
            explorationName = nil
            load(around: position)
        } else {
            recompute()
        }
    }

    func explore(place: Place) {
        explorationCenter = place.position
        explorationName = place.shortName
        target = place.position
        targetFollowsUser = false
        placeResults = []
        errorMessage = nil
        load(around: place.position)
    }

    func backToMyLocation() {
        guard let user = userLocation else { return }
        let wasExploring = isExploring
        target = user
        targetFollowsUser = true
        explorationCenter = nil
        explorationName = nil
        if wasExploring { load(around: user) } else { recompute() }
    }

    func refresh() {
        guard let center = explorationCenter ?? userLocation ?? target else { return }
        load(around: center, forceRefresh: true)
    }

    // MARK: - Ricerca

    func searchPlaces(_ query: String) {
        searchTask?.cancel()
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            placeResults = []
            return
        }
        isSearchingPlaces = true
        searchTask = Task {
            do {
                let places = try await placeService.search(query)
                guard !Task.isCancelled else { return }
                placeResults = places
                isSearchingPlaces = false
                errorMessage = places.isEmpty ? "Nessun luogo trovato per \"\(query)\"" : nil
            } catch {
                guard !Task.isCancelled else { return }
                isSearchingPlaces = false
                errorMessage = "Ricerca non riuscita: \(error.localizedDescription)"
            }
        }
    }

    func clearPlaceResults() {
        searchTask?.cancel()
        placeResults = []
        isSearchingPlaces = false
    }

    // MARK: - Posto auto

    func parkHere() {
        let position = targetFollowsUser ? (userLocation ?? target) : target
        guard let position else {
            errorMessage = "Aspetto la posizione per salvare il posto auto"
            return
        }
        errorMessage = nil
        parkedCarStore.save(position: position)
        Task { await SunAlarm.requestPermissionIfNeeded() }
    }

    func clearParkedCar() {
        parkedCarStore.clear()
        SunAlarm.cancel()
    }

    // MARK: - Caricamento e calcolo

    private func load(around center: LatLng, forceRefresh: Bool = false) {
        loadTask?.cancel()
        isLoading = true
        errorMessage = nil
        loadTask = Task {
            do {
                let set = try await obstacleService.obstacles(
                    around: center,
                    radiusMeters: ObstacleService.defaultRadius,
                    forceRefresh: forceRefresh
                )
                guard !Task.isCancelled else { return }
                lastLoadedCenter = set.center
                plane = set.plane
                obstacles = set.obstacles
                dataRadiusMeters = set.radiusMeters
                isLoading = false
                recompute()
            } catch {
                guard !Task.isCancelled else { return }
                isLoading = false
                errorMessage = "Non riesco a scaricare gli edifici: \(error.localizedDescription)"
            }
        }
    }

    private func recompute(recomputeForecast: Bool = true) {
        guard let plane else { return }
        let reference = target ?? plane.origin
        let now = instant.kotlinInstant

        let currentSun = SolarPosition.shared.at(instant: now, location: reference)
        sun = currentSun
        dayLight = SunTimes.shared.forDay(date: date, zone: zone, location: reference)

        let shapes = ShadowEngine.shared.shadowShapes(obstacles: obstacles, sun: currentSun)
        shadowPolygons = shapes.prefix(Self.maxDrawnPolygons).enumerated().map { index, shape in
            ShadowPolygon(
                id: index,
                coordinates: shape.map { plane.toLatLng(point: $0).coordinate }
            )
        }

        if let target {
            let local = plane.toLocal(point: target)
            shade = ShadowEngine.shared.shadeAt(point: local, obstacles: obstacles, sun: currentSun)
            if recomputeForecast {
                forecast = ShadeTimeline.shared.compute(
                    point: local,
                    obstacles: obstacles,
                    location: reference,
                    from: date.startOfDay(zone: zone),
                    to: date.plusDays(1).startOfDay(zone: zone),
                    stepMinutes: 10
                )
            }
        }

        if recomputeForecast { updateParkedCarStatus(plane: plane) }
    }

    /// Stato dell'auto all'ora vera, e avviso di sole in arrivo.
    private func updateParkedCarStatus(plane: LocalPlane) {
        guard let car = parkedCarStore.parkedCar else {
            parkedCarStatus = nil
            SunAlarm.cancel()
            return
        }
        let local = plane.toLocal(point: car.position)
        guard DataCoverage.shared.isReliable(pointFromCenter: local, radiusMeters: dataRadiusMeters) else {
            // Fuori dalla zona analizzata: meglio non dire niente che indovinare.
            parkedCarStatus = nil
            return
        }

        let now = Date()
        let sunNow = SolarPosition.shared.at(instant: now.kotlinInstant, location: car.position)
        let info = ShadowEngine.shared.shadeAt(point: local, obstacles: obstacles, sun: sunNow)
        let endOfDay = KotlinLocalDate.from(now).plusDays(1).startOfDay(zone: zone)
        let carForecast = ShadeTimeline.shared.compute(
            point: local,
            obstacles: obstacles,
            location: car.position,
            from: now.kotlinInstant,
            to: endOfDay,
            stepMinutes: 10
        )

        let sunArrival = carForecast.nextSunStart(instant: now.kotlinInstant)?.date
        parkedCarStatus = ParkedCarStatus(
            quality: info.quality,
            obstacleName: info.obstacle?.name,
            sunArrivesAt: sunArrival,
            shadeArrivesAt: carForecast.nextShadeStart(instant: now.kotlinInstant)?.date,
            computedAt: now
        )

        Task { await SunAlarm.schedule(sunArrivesAt: sunArrival) }
    }
}
