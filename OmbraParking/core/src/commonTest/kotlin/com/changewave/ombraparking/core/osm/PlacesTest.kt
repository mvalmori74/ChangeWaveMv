package com.changewave.ombraparking.core.osm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacesTest {

    private val response = """
        [
          {
            "place_id": 1,
            "lat": "44.4949",
            "lon": "11.3426",
            "display_name": "Piazza Maggiore, Centro Storico, Bologna, Emilia-Romagna, 40121, Italia",
            "type": "square"
          },
          {
            "place_id": 2,
            "lat": "45.4642",
            "lon": "9.1900",
            "display_name": "Piazza del Duomo, Milano, Lombardia, Italia"
          }
        ]
    """.trimIndent()

    @Test
    fun `legge i luoghi restituiti dal geocoder`() {
        val places = NominatimParser.parsePlaces(response)

        assertEquals(2, places.size)
        assertEquals(44.4949, places[0].position.latitude, 1e-9)
        assertEquals(11.3426, places[0].position.longitude, 1e-9)
        assertEquals("Piazza del Duomo, Milano, Lombardia", places[1].shortName)
    }

    @Test
    fun `il nome breve tiene solo le prime parti dell'indirizzo`() {
        val place = NominatimParser.parsePlaces(response).first()

        assertEquals("Piazza Maggiore, Centro Storico, Bologna", place.shortName)
        assertTrue(place.fullName.contains("Italia"), "il nome completo resta disponibile")
    }

    @Test
    fun `scarta i risultati senza coordinate valide`() {
        val broken = """
            [
              { "display_name": "Senza coordinate", "lat": "boh", "lon": "9.0" },
              { "display_name": "", "lat": "45.0", "lon": "9.0" },
              { "display_name": "Buono", "lat": "45.0", "lon": "9.0" }
            ]
        """.trimIndent()

        val places = NominatimParser.parsePlaces(broken)

        assertEquals(1, places.size)
        assertEquals("Buono", places.first().shortName)
    }

    @Test
    fun `una ricerca senza risultati non è un errore`() {
        assertTrue(NominatimParser.parsePlaces("[]").isEmpty())
    }

    @Test
    fun `la query codifica spazi e accenti`() {
        val url = NominatimQuery.searchUrl("Città Studi, Milano")

        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?q="))
        assertTrue(url.contains("Citt%C3%A0+Studi"), "url costruito: $url")
        assertTrue(url.contains("format=jsonv2"))
        assertTrue(url.contains("limit=6"))
    }
}
