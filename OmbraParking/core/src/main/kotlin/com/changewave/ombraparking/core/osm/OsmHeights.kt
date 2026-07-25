package com.changewave.ombraparking.core.osm

/**
 * Stima dell'altezza degli ostacoli a partire dai tag OpenStreetMap.
 *
 * In OSM l'altezza esplicita (`height`) è presente su una minoranza di edifici; molto più
 * spesso c'è il numero di piani, e spesso non c'è nulla. Le stime di ripiego sono
 * volutamente prudenti: sottostimare l'altezza significa promettere sole dove ci sarà
 * ombra, che è l'errore meno fastidioso per chi cerca parcheggio all'ombra.
 */
object OsmHeights {

    const val METERS_PER_LEVEL = 3.1
    private const val DEFAULT_BUILDING_HEIGHT = 9.0
    private const val DEFAULT_TREE_HEIGHT = 8.0
    private const val DEFAULT_CROWN_RADIUS = 3.5

    /** Altezze tipiche per tipo di edificio, usate quando mancano `height` e `building:levels`. */
    private val heightByBuildingType = mapOf(
        "garage" to 2.6,
        "garages" to 2.6,
        "carport" to 2.4,
        "shed" to 2.5,
        "hut" to 2.5,
        "roof" to 3.0,
        "kiosk" to 3.0,
        "bungalow" to 3.5,
        "house" to 7.0,
        "detached" to 7.0,
        "semidetached_house" to 7.0,
        "terrace" to 9.0,
        "farm" to 7.0,
        "chapel" to 10.0,
        "school" to 11.0,
        "kindergarten" to 7.0,
        "retail" to 8.0,
        "supermarket" to 8.0,
        "commercial" to 12.0,
        "industrial" to 10.0,
        "warehouse" to 10.0,
        "apartments" to 15.0,
        "residential" to 12.0,
        "dormitory" to 15.0,
        "hotel" to 18.0,
        "office" to 18.0,
        "hospital" to 20.0,
        "church" to 18.0,
        "cathedral" to 30.0,
        "tower" to 25.0,
    )

    /**
     * Interpreta un valore di lunghezza OSM: `"12"`, `"12 m"`, `"12,5"`, `"40'"`, `"40 ft"`.
     * Restituisce metri, o null se non interpretabile.
     */
    fun parseLength(raw: String?): Double? {
        val text = raw?.trim()?.lowercase() ?: return null
        if (text.isEmpty()) return null

        // Piedi e pollici: 40' oppure 40'6"
        Regex("""^(\d+(?:[.,]\d+)?)\s*'\s*(?:(\d+(?:[.,]\d+)?)\s*")?$""").find(text)?.let { match ->
            val feet = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val inches = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 0.0
            return feet * 0.3048 + inches * 0.0254
        }

        val match = Regex("""^(-?\d+(?:[.,]\d+)?)\s*([a-z]*)$""").find(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val meters = when (match.groupValues[2]) {
            "", "m", "meter", "meters", "metri" -> number
            "ft", "feet", "foot" -> number * 0.3048
            "km" -> number * 1000
            "cm" -> number / 100
            else -> return null
        }
        return if (meters.isFinite() && meters > 0) meters else null
    }

    /** Altezza stimata di un edificio in metri. */
    fun buildingHeight(tags: Map<String, String>): Double {
        parseLength(tags["height"])?.let { return it }

        val levels = tags["building:levels"]?.replace(',', '.')?.toDoubleOrNull()
        if (levels != null && levels > 0) {
            val roof = parseLength(tags["roof:height"]) ?: 0.0
            return levels * METERS_PER_LEVEL + roof
        }

        val type = tags["building"]?.lowercase()
        if (type != null && type != "yes") {
            heightByBuildingType[type]?.let { return it }
        }
        tags["building:part"]?.lowercase()?.let { part -> heightByBuildingType[part]?.let { return it } }

        return DEFAULT_BUILDING_HEIGHT
    }

    /** Altezza stimata di un albero in metri. */
    fun treeHeight(tags: Map<String, String>): Double =
        parseLength(tags["height"])
            ?: parseLength(tags["est_height"])
            ?: DEFAULT_TREE_HEIGHT

    /** Raggio della chioma in metri. */
    fun treeCrownRadius(tags: Map<String, String>): Double {
        parseLength(tags["diameter_crown"])?.let { return it / 2 }
        parseLength(tags["canopy:diameter"])?.let { return it / 2 }
        // Chioma proporzionata all'altezza quando non è mappata.
        val height = treeHeight(tags)
        return (height * 0.4).coerceIn(1.5, 8.0)
    }

    /**
     * Quota da cui parte la chioma: sotto quella altezza la luce passa.
     * Serve perché l'ombra di un albero non tocca il tronco ma è staccata.
     */
    fun treeCrownBase(tags: Map<String, String>): Double {
        val height = treeHeight(tags)
        return (height * 0.35).coerceIn(1.5, height - 1.0)
    }

    /** Altezza stimata di un muro o di una recinzione piena. */
    fun barrierHeight(tags: Map<String, String>): Double =
        parseLength(tags["height"]) ?: when (tags["barrier"]?.lowercase()) {
            "city_wall" -> 6.0
            "retaining_wall" -> 3.0
            "wall" -> 2.2
            "hedge" -> 1.8
            "fence" -> 1.8
            else -> 2.0
        }
}
