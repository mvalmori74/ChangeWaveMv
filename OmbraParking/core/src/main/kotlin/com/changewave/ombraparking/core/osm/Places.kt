package com.changewave.ombraparking.core.osm

import com.changewave.ombraparking.core.geo.LatLng
import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Un luogo trovato cercandolo per nome.
 *
 * @param shortName le prime parti dell'indirizzo, quelle che servono a riconoscerlo.
 * @param fullName l'indirizzo completo restituito da OpenStreetMap.
 */
data class Place(
    val shortName: String,
    val fullName: String,
    val position: LatLng,
)

/** Ricerca per nome sul geocoder di OpenStreetMap. */
object NominatimQuery {

    private const val BASE_URL = "https://nominatim.openstreetmap.org/search"

    /**
     * Le regole d'uso di Nominatim chiedono richieste sporadiche e identificabili:
     * l'app cerca solo quando l'utente conferma il testo, mai a ogni lettera digitata.
     */
    fun searchUrl(query: String, limit: Int = 6, language: String = "it"): String {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return "$BASE_URL?q=$encoded&format=jsonv2&limit=$limit&accept-language=$language"
    }
}

@Serializable
private data class NominatimPlace(
    @SerialName("display_name") val displayName: String = "",
    val lat: String = "",
    val lon: String = "",
)

object NominatimParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** Quante parti dell'indirizzo tenere nel nome breve. */
    private const val SHORT_NAME_PARTS = 3

    fun parsePlaces(rawJson: String): List<Place> {
        val places = json.decodeFromString(ListSerializer(NominatimPlace.serializer()), rawJson)
        return places.mapNotNull { place ->
            val latitude = place.lat.toDoubleOrNull() ?: return@mapNotNull null
            val longitude = place.lon.toDoubleOrNull() ?: return@mapNotNull null
            val fullName = place.displayName.trim()
            if (fullName.isEmpty()) return@mapNotNull null
            Place(
                shortName = shortName(fullName),
                fullName = fullName,
                position = LatLng(latitude, longitude),
            )
        }
    }

    /**
     * "Piazza Garibaldi, Centro Storico, Bologna, Emilia-Romagna, 40121, Italia" diventa
     * "Piazza Garibaldi, Centro Storico, Bologna": il resto non aiuta a distinguere.
     */
    private fun shortName(fullName: String): String =
        fullName.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(SHORT_NAME_PARTS)
            .joinToString(", ")
            .ifEmpty { fullName }
}
