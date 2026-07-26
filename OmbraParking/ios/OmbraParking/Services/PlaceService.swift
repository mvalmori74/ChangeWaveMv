import Foundation
import OmbraCore

/// Cerca un luogo per nome sul geocoder di OpenStreetMap.
///
/// Nominatim è gratuito ma con regole d'uso strette: la ricerca parte solo alla conferma
/// del testo, mai a ogni lettera, e i risultati recenti restano in cache.
actor PlaceService {

    private var cache: [String: [Place]] = [:]

    func search(_ query: String) async throws -> [Place] {
        let normalized = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard normalized.count >= 3 else { return [] }
        if let cached = cache[normalized.lowercased()] { return cached }

        let urlString = NominatimQuery.shared.searchUrl(query: normalized, limit: 6, language: "it")
        guard let url = URL(string: urlString) else { return [] }

        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue(AppInfo.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw ObstacleServiceError.network("La ricerca ha risposto \(code)")
        }
        guard let body = String(data: data, encoding: .utf8) else { return [] }

        let places = NominatimParser.shared.parsePlaces(rawJson: body)
        cache[normalized.lowercased()] = places
        return places
    }
}
