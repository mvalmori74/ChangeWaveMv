import OmbraCore
import SwiftUI

// MARK: - Formattazione

enum Format {

    static func clock(minuteOfDay: Int) -> String {
        String(format: "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
    }

    static func clock(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return clock(minuteOfDay: (parts.hour ?? 0) * 60 + (parts.minute ?? 0))
    }

    /// "2 h 30 min", "45 min".
    static func duration(minutes: Int) -> String {
        if minutes < 60 { return "\(minutes) min" }
        let hours = minutes / 60
        let rest = minutes % 60
        return rest == 0 ? "\(hours) h" : "\(hours) h \(rest) min"
    }

    static func distance(meters: Double) -> String {
        meters < 1000
            ? "\(Int(meters)) m"
            : String(format: "%.1f km", meters / 1000)
    }

    static func cardinal(azimuthDegrees: Double) -> String {
        let names = ["nord", "nord-est", "est", "sud-est", "sud", "sud-ovest", "ovest", "nord-ovest"]
        let index = Int(((azimuthDegrees.truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360) / 45).rounded()) % 8
        return names[index]
    }
}

extension ShadeQuality {
    var label: String {
        switch self {
        case .sun: return "Pieno sole"
        case .dappled: return "Ombra degli alberi"
        case .shade: return "All'ombra"
        case .night: return "Notte"
        default: return "—"
        }
    }

    var emoji: String {
        switch self {
        case .sun: return "☀️"
        case .dappled: return "🌤"
        case .shade: return "🌑"
        case .night: return "🌙"
        default: return "•"
        }
    }

    var color: Color {
        switch self {
        case .sun: return Color(red: 1.0, green: 0.79, blue: 0.30)
        case .dappled: return Color(red: 0.61, green: 0.82, blue: 0.49)
        case .shade: return Color(red: 0.42, green: 0.55, blue: 0.84)
        case .night: return Color(red: 0.24, green: 0.28, blue: 0.39)
        default: return .gray
        }
    }
}

// MARK: - Barra della giornata

/// Mostra a colpo d'occhio quando quel punto è al sole e quando è in ombra, e permette di
/// scegliere l'ora trascinando il dito.
struct ShadeTimelineStrip: View {

    let forecast: ShadeForecast?
    let dayStart: Date
    @Binding var minuteOfDay: Int

    private let minutesInDay = 24 * 60

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Color.black.opacity(0.25)

                if let forecast {
                    ForEach(Array(forecast.slots.enumerated()), id: \.offset) { _, slot in
                        let start = minutes(from: slot.start.date)
                        let end = minutes(from: slot.end.date)
                        let width = max(0, CGFloat(end - start) / CGFloat(minutesInDay) * geometry.size.width)
                        slot.quality.color
                            .frame(width: width)
                            .offset(x: CGFloat(start) / CGFloat(minutesInDay) * geometry.size.width)
                    }
                }

                // Cursore dell'ora scelta
                Rectangle()
                    .fill(.white)
                    .frame(width: 2)
                    .offset(x: CGFloat(minuteOfDay) / CGFloat(minutesInDay) * geometry.size.width)
            }
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0).onChanged { value in
                    let fraction = min(max(value.location.x / geometry.size.width, 0), 1)
                    minuteOfDay = Int(fraction * CGFloat(minutesInDay - 1))
                }
            )
        }
        .frame(height: 44)
    }

    private func minutes(from date: Date) -> Int {
        Int(date.timeIntervalSince(dayStart) / 60)
    }
}

// MARK: - Riepilogo

struct ShadeSummary: View {

    let shade: ShadeInfo?
    let forecast: ShadeForecast?
    let instant: Date
    let minuteOfDay: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(Format.clock(minuteOfDay)).font(.title3.bold())
                if let shade {
                    Text("\(shade.quality.emoji) \(shade.quality.label)")
                        .foregroundStyle(shade.quality.color)
                }
                Spacer()
            }
            if let detail { Text(detail).font(.footnote).foregroundStyle(.secondary) }
        }
    }

    private var detail: String? {
        guard let forecast, let shade else { return nil }
        let now = instant.kotlinInstant
        if shade.quality.isShaded {
            guard let end = forecast.shadeEndsAfter(instant: now)?.date else {
                return "All'ombra per il resto della giornata"
            }
            let minutes = Int(end.timeIntervalSince(instant) / 60)
            return "Ombra fino alle \(Format.clock(end)) (ancora \(Format.duration(minutes: minutes)))"
        }
        guard let next = forecast.nextShadeStart(instant: now)?.date else { return nil }
        let minutes = Int(next.timeIntervalSince(instant) / 60)
        return "Ombra dalle \(Format.clock(next)) (fra \(Format.duration(minutes: minutes)))"
    }
}

// MARK: - Posto auto

struct ParkedCarBar: View {

    let parkedCar: ParkedCar?
    let status: ParkedCarStatus?
    let userLocation: LatLng?
    let onPark: () -> Void
    let onClear: () -> Void
    var onShowCar: (() -> Void)?

    var body: some View {
        if parkedCar == nil {
            Button(action: onPark) {
                Text("🚗  Ho parcheggiato qui").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        } else {
            HStack(spacing: 10) {
                Text("🚗").font(.title3)
                VStack(alignment: .leading, spacing: 2) {
                    Text(whereText).font(.subheadline).lineLimit(1)
                    Text(statusText).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer()
                Button(action: onClear) {
                    Image(systemName: "xmark").foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Dimentica il posto auto")
            }
            .contentShape(Rectangle())
            .onTapGesture { onShowCar?() }
        }
    }

    private var whereText: String {
        guard let car = parkedCar else { return "" }
        let parkedAt = "parcheggiata alle \(Format.clock(car.parkedAt))"
        guard let user = userLocation else { return "Auto \(parkedAt)" }

        let plane = LocalPlane(origin: user)
        let offset = plane.toLocal(point: car.position)
        if offset.length < 15 { return "Sei all'auto · \(parkedAt)" }
        let azimuth = atan2(offset.x, offset.y) * 180 / .pi
        return "Auto a \(Format.distance(meters: offset.length)) verso "
            + "\(Format.cardinal(azimuthDegrees: azimuth)) · \(parkedAt)"
    }

    private var statusText: String {
        guard let status else { return "Troppo lontana per sapere se è al sole" }
        let prefix = "\(status.quality.emoji) "
        switch status.quality {
        case .sun:
            guard let shade = status.shadeArrivesAt else { return prefix + "Al sole fino al tramonto" }
            let minutes = Int(shade.timeIntervalSince(status.computedAt) / 60)
            return prefix + "Al sole, ombra dalle \(Format.clock(shade)) (fra \(Format.duration(minutes: minutes)))"
        case .night:
            return prefix + "È notte, nessun sole sull'auto"
        default:
            guard let arrival = status.sunArrivesAt else {
                return prefix + "All'ombra per il resto della giornata"
            }
            let minutes = Int(arrival.timeIntervalSince(status.computedAt) / 60)
            let lead = Int(SunWarning.shared.DEFAULT_LEAD_MINUTES)
            return prefix + "All'ombra, il sole arriva alle \(Format.clock(arrival)) "
                + "(fra \(Format.duration(minutes: minutes))) · ti avviso \(lead) min prima"
        }
    }
}

// MARK: - Scelte rapide di ora e giorno

struct QuickTimeChips: View {

    @ObservedObject var model: ShadowModel

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Button("Adesso") { model.useCurrentTime() }.buttonStyle(.bordered)
                Button("+1 h") { model.setTime(minuteOfDay: model.minuteOfDay + 60) }
                    .buttonStyle(.bordered)
                Button("+2 h") { model.setTime(minuteOfDay: model.minuteOfDay + 120) }
                    .buttonStyle(.bordered)
                Button("Oggi") { model.setDate(.from(Date())) }.buttonStyle(.bordered)
                Button("Domani") { model.setDate(KotlinLocalDate.from(Date()).plusDays(1)) }
                    .buttonStyle(.bordered)
            }
        }
    }
}
