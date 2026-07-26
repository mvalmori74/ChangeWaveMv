import Foundation
import OmbraCore
import UserNotifications

/// Dove e quando è stata lasciata l'auto.
struct ParkedCar: Equatable {
    let position: LatLng
    let parkedAt: Date

    static func == (lhs: ParkedCar, rhs: ParkedCar) -> Bool {
        lhs.position.latitude == rhs.position.latitude
            && lhs.position.longitude == rhs.position.longitude
            && lhs.parkedAt == rhs.parkedAt
    }
}

/// Ricorda il posto auto fra un avvio e l'altro. Sono due coordinate e un orario: gli
/// UserDefaults bastano e non aggiungono dipendenze.
@MainActor
final class ParkedCarStore: ObservableObject {

    @Published private(set) var parkedCar: ParkedCar?

    private let defaults = UserDefaults.standard
    private enum Key {
        static let latitude = "parkedCar.latitude"
        static let longitude = "parkedCar.longitude"
        static let parkedAt = "parkedCar.parkedAt"
    }

    init() {
        parkedCar = read()
    }

    func save(position: LatLng, parkedAt: Date = Date()) {
        defaults.set(position.latitude, forKey: Key.latitude)
        defaults.set(position.longitude, forKey: Key.longitude)
        defaults.set(parkedAt.timeIntervalSince1970, forKey: Key.parkedAt)
        parkedCar = ParkedCar(position: position, parkedAt: parkedAt)
    }

    func clear() {
        defaults.removeObject(forKey: Key.latitude)
        defaults.removeObject(forKey: Key.longitude)
        defaults.removeObject(forKey: Key.parkedAt)
        parkedCar = nil
    }

    private func read() -> ParkedCar? {
        guard defaults.object(forKey: Key.parkedAt) != nil else { return nil }
        let latitude = defaults.double(forKey: Key.latitude)
        let longitude = defaults.double(forKey: Key.longitude)
        let parkedAt = defaults.double(forKey: Key.parkedAt)
        guard latitude.isFinite, longitude.isFinite, parkedAt > 0 else { return nil }
        return ParkedCar(
            position: LatLng(latitude: latitude, longitude: longitude),
            parkedAt: Date(timeIntervalSince1970: parkedAt)
        )
    }
}

/// Avviso "il sole sta per arrivare sull'auto".
///
/// Su iOS non serve un worker in background: si consegna al sistema una notifica locale
/// programmata, che scatta all'orario previsto anche ad app chiusa. Quando l'auto cambia o
/// sparisce, l'avviso in coda viene sostituito o rimosso.
@MainActor
enum SunAlarm {

    private static let identifier = "sun-arrival"

    static func requestPermissionIfNeeded() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound])
    }

    /// Programma l'avviso per [sunArrivesAt], o lo cancella se non c'è nessun arrivo previsto.
    static func schedule(sunArrivesAt: Date?, now: Date = Date()) async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [identifier])

        guard let arrival = sunArrivesAt, arrival > now else { return }
        let lead = TimeInterval(SunWarning.shared.DEFAULT_LEAD_MINUTES * 60)
        let triggerAt = max(arrival.addingTimeInterval(-lead), now.addingTimeInterval(1))
        let delay = triggerAt.timeIntervalSince(now)
        guard delay > 0 else { return }

        let content = UNMutableNotificationContent()
        content.title = "Il sole sta per arrivare sull'auto"
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "it_IT")
        formatter.dateFormat = "HH:mm"
        let minutes = Int(arrival.timeIntervalSince(now) / 60)
        content.body = minutes < 60
            ? "Il sole ci arriva alle \(formatter.string(from: arrival)), fra \(minutes) minuti. Se puoi, spostala all'ombra."
            : "Il sole ci arriva alle \(formatter.string(from: arrival))."
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: identifier,
            content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false)
        )
        try? await center.add(request)
    }

    static func cancel() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [identifier])
    }
}
