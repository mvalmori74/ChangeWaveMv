import CoreLocation
import Foundation
import OmbraCore

/*
 * Conversioni fra i tipi di Foundation e quelli del core Kotlin.
 *
 * Il core lavora con kotlinx-datetime, che in Swift arriva con i nomi generati dal
 * compilatore (`Kotlinx_datetimeInstant` e simili). Concentrare qui le conversioni evita
 * di spargere quei nomi in tutta l'app.
 */

typealias KotlinInstant = Kotlinx_datetimeInstant
typealias KotlinLocalDate = Kotlinx_datetimeLocalDate
typealias KotlinTimeZone = Kotlinx_datetimeTimeZone

extension Date {
    var kotlinInstant: KotlinInstant {
        KotlinInstant.companion.fromEpochMilliseconds(
            epochMilliseconds: Int64((timeIntervalSince1970 * 1000).rounded())
        )
    }
}

extension KotlinInstant {
    var date: Date {
        Date(timeIntervalSince1970: Double(toEpochMilliseconds()) / 1000)
    }
}

extension KotlinTimeZone {
    static var current: KotlinTimeZone { KotlinTimeZone.companion.currentSystemDefault() }
}

extension KotlinLocalDate {
    static func from(_ date: Date, zone: TimeZone = .current) -> KotlinLocalDate {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return KotlinLocalDate(
            year: Int32(parts.year ?? 2024),
            monthNumber: Int32(parts.month ?? 1),
            dayOfMonth: Int32(parts.day ?? 1)
        )
    }

    /// Mezzanotte locale di questa data, come istante assoluto.
    func startOfDay(zone: KotlinTimeZone) -> KotlinInstant {
        Kotlinx_datetimeLocalDateTime(
            date: self,
            time: Kotlinx_datetimeLocalTime(hour: 0, minute: 0, second: 0, nanosecond: 0)
        ).toInstant(timeZone: zone)
    }

    func plusDays(_ days: Int) -> KotlinLocalDate {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        let base = DateComponents(
            year: Int(year),
            month: Int(monthNumber),
            day: Int(dayOfMonth)
        )
        guard let start = calendar.date(from: base),
              let shifted = calendar.date(byAdding: .day, value: days, to: start)
        else { return self }
        return KotlinLocalDate.from(shifted)
    }
}

extension LatLng {
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

extension CLLocationCoordinate2D {
    var latLng: LatLng { LatLng(latitude: latitude, longitude: longitude) }
}
