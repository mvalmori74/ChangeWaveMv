import CoreLocation
import Foundation
import OmbraCore

/// Posizione dell'utente, pubblicata man mano che il GPS aggiorna.
@MainActor
final class LocationProvider: NSObject, ObservableObject {

    @Published private(set) var location: LatLng?
    @Published private(set) var isAuthorized = false

    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        // Sotto i tre metri non cambia nulla per l'ombra: filtrare qui evita di ricalcolare
        // la scena a ogni oscillazione del segnale.
        manager.distanceFilter = 3
    }

    func start() {
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    func stop() {
        manager.stopUpdatingLocation()
    }
}

extension LocationProvider: CLLocationManagerDelegate {

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        guard let last = locations.last else { return }
        let position = LatLng(
            latitude: last.coordinate.latitude,
            longitude: last.coordinate.longitude
        )
        Task { @MainActor in
            self.location = position
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        let authorized = status == .authorizedWhenInUse || status == .authorizedAlways
        Task { @MainActor in
            self.isAuthorized = authorized
            if authorized { manager.startUpdatingLocation() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Un errore momentaneo del GPS non deve svuotare la posizione già nota.
    }
}
