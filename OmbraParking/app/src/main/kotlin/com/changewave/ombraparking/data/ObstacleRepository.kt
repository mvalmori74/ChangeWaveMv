package com.changewave.ombraparking.data

import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.osm.OverpassParser
import com.changewave.ombraparking.core.osm.OverpassQuery
import com.changewave.ombraparking.core.shadow.Obstacle
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** Ostacoli scaricati per una zona, già proiettati nel piano locale in metri. */
data class ObstacleSet(
    val center: LatLng,
    val radiusMeters: Int,
    val plane: LocalPlane,
    val obstacles: List<Obstacle>,
)

/**
 * Scarica edifici, alberi e muri da OpenStreetMap tramite Overpass API.
 *
 * Overpass è un servizio pubblico e volontario: l'app fa una richiesta sola per zona,
 * la tiene in cache e la rifà solo quando ci si sposta davvero.
 */
class ObstacleRepository(
    private val userAgent: String,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
    private val client: OkHttpClient = defaultClient(),
) {

    private var cached: ObstacleSet? = null

    /**
     * Ostacoli intorno a [center]. Riusa la cache se ci si è spostati di poco,
     * a meno che [forceRefresh] non chieda esplicitamente di riscaricare.
     */
    suspend fun obstaclesAround(
        center: LatLng,
        radiusMeters: Int = DEFAULT_RADIUS_M,
        forceRefresh: Boolean = false,
    ): ObstacleSet = withContext(Dispatchers.IO) {
        val previous = cached
        if (!forceRefresh && previous != null && previous.radiusMeters == radiusMeters) {
            val moved = previous.plane.distanceMeters(previous.center, center)
            if (moved < CACHE_TOLERANCE_M) return@withContext previous
        }

        val query = OverpassQuery.obstaclesAround(center, radiusMeters)
        val body = requestWithFallback(query)
        val plane = LocalPlane(center)
        val obstacles = OverpassParser.parseObstacles(body, plane)
        ObstacleSet(center, radiusMeters, plane, obstacles).also { cached = it }
    }

    /** Se un mirror Overpass è sovraccarico (succede spesso) si passa al successivo. */
    private fun requestWithFallback(query: String): String {
        var lastError: IOException? = null
        for (endpoint in endpoints) {
            try {
                return execute(endpoint, query)
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("Nessun endpoint Overpass configurato")
    }

    private fun execute(endpoint: String, query: String): String {
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", userAgent)
            .post(FormBody.Builder().add("data", query).build())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                throw IOException("Overpass ha risposto ${response.code} da $endpoint")
            }
            return body
        }
    }

    companion object {
        const val DEFAULT_RADIUS_M = 300

        /** Spostamenti sotto questa soglia riusano i dati già scaricati. */
        private const val CACHE_TOLERANCE_M = 80.0

        private val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS) // Overpass può metterci parecchio sotto carico
            .build()
    }
}
