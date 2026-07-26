import Foundation
import OmbraCore

/// Ostacoli di una zona, già proiettati nel piano locale in metri.
struct ObstacleSet {
    let center: LatLng
    let radiusMeters: Int32
    let plane: LocalPlane
    let obstacles: [Obstacle]
}

enum ObstacleServiceError: LocalizedError {
    case network(String)

    var errorDescription: String? {
        switch self {
        case .network(let detail): return detail
        }
    }
}

/// Scarica edifici, alberi e muri da OpenStreetMap.
///
/// Query e interpretazione della risposta vivono nel core condiviso con Android: qui resta
/// solo la chiamata di rete, che è l'unica parte davvero specifica della piattaforma.
actor ObstacleService {

    static let defaultRadius: Int32 = 300

    /// Spostamenti sotto questa soglia riusano i dati già scaricati.
    private static let cacheToleranceMeters = 80.0

    private static let endpoints = [
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    ]

    private var cached: ObstacleSet?

    func obstacles(
        around center: LatLng,
        radiusMeters: Int32 = defaultRadius,
        forceRefresh: Bool = false
    ) async throws -> ObstacleSet {
        if !forceRefresh, let previous = cached, previous.radiusMeters == radiusMeters {
            let moved = previous.plane.distanceMeters(a: previous.center, b: center)
            if moved < Self.cacheToleranceMeters { return previous }
        }

        let query = OverpassQuery.shared.obstaclesAround(
            center: center,
            radiusMeters: radiusMeters,
            timeoutSeconds: 25
        )
        let body = try await post(query: query)

        let plane = LocalPlane(origin: center)
        let obstacles = OverpassParser.shared.parseObstacles(rawJson: body, plane: plane)
        let set = ObstacleSet(
            center: center,
            radiusMeters: radiusMeters,
            plane: plane,
            obstacles: obstacles
        )
        cached = set
        return set
    }

    /// Se un mirror Overpass è sovraccarico — succede spesso — si passa al successivo.
    private func post(query: String) async throws -> String {
        var lastError: Error?
        for endpoint in Self.endpoints {
            do {
                return try await send(query: query, to: endpoint)
            } catch {
                lastError = error
            }
        }
        throw ObstacleServiceError.network(
            lastError?.localizedDescription ?? "rete non raggiungibile"
        )
    }

    private func send(query: String, to endpoint: String) async throws -> String {
        guard let url = URL(string: endpoint) else {
            throw ObstacleServiceError.network("indirizzo non valido: \(endpoint)")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 45 // Overpass può metterci parecchio sotto carico
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(AppInfo.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpBody = "data=\(query.formEncoded)".data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw ObstacleServiceError.network("Overpass ha risposto \(code)")
        }
        guard let body = String(data: data, encoding: .utf8) else {
            throw ObstacleServiceError.network("risposta illeggibile")
        }
        return body
    }
}

extension String {
    /// Codifica per un corpo `application/x-www-form-urlencoded`.
    var formEncoded: String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return addingPercentEncoding(withAllowedCharacters: allowed) ?? self
    }
}

enum AppInfo {
    /// Overpass e i tile server di OpenStreetMap chiedono uno user agent identificabile.
    static let userAgent: String = {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        return "OmbraParking/\(version) (iOS; com.changewave.ombraparking)"
    }()
}
