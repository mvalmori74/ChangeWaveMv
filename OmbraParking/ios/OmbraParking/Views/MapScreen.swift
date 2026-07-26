import MapKit
import OmbraCore
import SwiftUI

/// Vista dall'alto: mappa con le ombre proiettate all'ora scelta.
/// Toccando un punto si sposta lì l'analisi.
struct MapScreen: View {

    @ObservedObject var model: ShadowModel
    @State private var camera: MapCameraPosition = .automatic
    @State private var query = ""

    var body: some View {
        MapReader { proxy in
            Map(position: $camera) {
                /*
                 * Ogni sagoma è un poligono a sé: dove si sovrappongono il colore si somma e
                 * l'ombra appare più scura. Sull'app Android l'unione è risolta con un
                 * riempimento unico; qui l'opacità è tenuta bassa perché la sovrapposizione
                 * si noti il meno possibile.
                 */
                ForEach(model.shadowPolygons) { polygon in
                    MapPolygon(coordinates: polygon.coordinates)
                        .foregroundStyle(Color(red: 0.17, green: 0.24, blue: 0.43).opacity(0.30))
                }

                if let target = model.target {
                    Annotation("Punto scelto", coordinate: target.coordinate) {
                        Circle()
                            .strokeBorder(.white, lineWidth: 2)
                            .background(Circle().fill(.orange))
                            .frame(width: 16, height: 16)
                    }
                }

                if let car = model.parkedCarStore.parkedCar {
                    Annotation("Auto", coordinate: car.position.coordinate) {
                        Text("P")
                            .font(.caption.bold())
                            .foregroundStyle(.white)
                            .frame(width: 26, height: 26)
                            .background(Circle().fill(Color(red: 0.18, green: 0.49, blue: 0.31)))
                            .overlay(Circle().strokeBorder(.white, lineWidth: 2))
                    }
                }

                UserAnnotation()
            }
            .mapStyle(.standard)
            .onTapGesture { screenPoint in
                if let coordinate = proxy.convert(screenPoint, from: .local) {
                    model.selectTarget(coordinate.latLng)
                }
            }
        }
        .overlay(alignment: .top) { topControls }
        .overlay(alignment: .bottom) { bottomPanel }
        .onChange(of: model.explorationCenter?.latitude) { _, _ in
            guard let center = model.explorationCenter else { return }
            camera = .region(
                MKCoordinateRegion(
                    center: center.coordinate,
                    latitudinalMeters: 500,
                    longitudinalMeters: 500
                )
            )
        }
    }

    private var topControls: some View {
        VStack(spacing: 8) {
            HStack {
                TextField("Cerca una via o una piazza", text: $query)
                    .textFieldStyle(.roundedBorder)
                    .submitLabel(.search)
                    .onSubmit { model.searchPlaces(query) }
                if model.isSearchingPlaces { ProgressView() }
                if !query.isEmpty {
                    Button {
                        query = ""
                        model.clearPlaceResults()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                    }
                    .buttonStyle(.plain)
                }
            }

            if !model.placeResults.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(model.placeResults.enumerated()), id: \.offset) { _, place in
                        Button {
                            query = place.shortName
                            model.explore(place: place)
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(place.shortName).font(.subheadline).lineLimit(1)
                                Text(place.fullName).font(.caption)
                                    .foregroundStyle(.secondary).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                        Divider()
                    }
                }
                .padding(.horizontal, 10)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
            }

            if model.isExploring {
                HStack {
                    Text("🔎  " + (model.explorationName ?? "Zona scelta sulla mappa"))
                        .font(.subheadline).lineLimit(1)
                    Spacer()
                    Button {
                        model.backToMyLocation()
                        camera = .userLocation(fallback: .automatic)
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Torna alla mia posizione")
                }
                .padding(8)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
            }

            if let message = model.errorMessage ?? loadingMessage {
                Text(message)
                    .font(.footnote)
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }

    private var loadingMessage: String? {
        model.isLoading ? "Sto scaricando edifici e alberi…" : nil
    }

    private var bottomPanel: some View {
        VStack(spacing: 10) {
            ShadeSummary(
                shade: model.shade,
                forecast: model.forecast,
                instant: model.instant,
                minuteOfDay: model.minuteOfDay
            )
            ParkedCarBar(
                parkedCar: model.parkedCarStore.parkedCar,
                status: model.parkedCarStatus,
                userLocation: model.userLocation,
                onPark: model.parkHere,
                onClear: model.clearParkedCar,
                onShowCar: {
                    guard let car = model.parkedCarStore.parkedCar else { return }
                    camera = .region(
                        MKCoordinateRegion(
                            center: car.position.coordinate,
                            latitudinalMeters: 300,
                            longitudinalMeters: 300
                        )
                    )
                }
            )
            ShadeTimelineStrip(
                forecast: model.forecast,
                dayStart: model.date.startOfDay(zone: .current).date,
                minuteOfDay: Binding(
                    get: { model.minuteOfDay },
                    set: { model.setTime(minuteOfDay: $0) }
                )
            )
            QuickTimeChips(model: model)
        }
        .padding(12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        .padding(12)
    }
}
