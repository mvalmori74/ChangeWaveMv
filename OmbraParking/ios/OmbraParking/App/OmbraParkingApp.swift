import SwiftUI

@main
struct OmbraParkingApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
        }
    }
}

struct ContentView: View {

    @StateObject private var model = ShadowModel()
    @StateObject private var locationProvider = LocationProvider()

    var body: some View {
        TabView {
            MapScreen(model: model)
                .tabItem { Label("Mappa", systemImage: "map") }

            ARScreen(model: model)
                .tabItem { Label("Realtà aumentata", systemImage: "camera.viewfinder") }
        }
        .task {
            locationProvider.start()
        }
        .onReceive(locationProvider.$location.compactMap { $0 }) { position in
            model.onLocationUpdate(position)
        }
        .overlay(alignment: .top) {
            if !locationProvider.isAuthorized {
                Text("Senza posizione l'app non sa quali edifici guardare")
                    .font(.footnote)
                    .padding(10)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 8))
                    .padding(.top, 8)
            }
        }
    }
}
