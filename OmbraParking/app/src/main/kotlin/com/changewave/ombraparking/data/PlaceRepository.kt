package com.changewave.ombraparking.data

import com.changewave.ombraparking.core.osm.NominatimParser
import com.changewave.ombraparking.core.osm.NominatimQuery
import com.changewave.ombraparking.core.osm.Place
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cerca un luogo per nome sul geocoder di OpenStreetMap.
 *
 * Nominatim è gratuito ma con regole d'uso strette: richieste sporadiche, uno user agent
 * riconoscibile e nessuna raffica. Per questo la ricerca parte solo quando l'utente
 * conferma il testo, e i risultati recenti restano in cache.
 */
class PlaceRepository(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient(),
) {

    private val cache = LinkedHashMap<String, List<Place>>()

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        cache[normalized.lowercase()]?.let { return@withContext it }

        val request = Request.Builder()
            .url(NominatimQuery.searchUrl(normalized))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                throw IOException("La ricerca ha risposto ${response.code}")
            }
            val places = NominatimParser.parsePlaces(body)
            if (cache.size >= CACHE_SIZE) {
                cache.remove(cache.keys.first())
            }
            cache[normalized.lowercase()] = places
            places
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val CACHE_SIZE = 20

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
