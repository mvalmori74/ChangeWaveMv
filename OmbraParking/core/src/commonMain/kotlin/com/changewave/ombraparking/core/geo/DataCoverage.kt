package com.changewave.ombraparking.core.geo

/**
 * Fin dove ci si può fidare degli ostacoli scaricati.
 *
 * Gli edifici vengono presi entro un raggio da un centro: un punto vicino al bordo di
 * quell'area è circondato solo a metà, perché i palazzi appena fuori — che potrebbero
 * fargli ombra — non sono stati scaricati. Rispondere "pieno sole" per un punto del genere
 * significherebbe scambiare l'assenza di dati per assenza di ombra.
 *
 * Questa regola sta in un posto solo perché decide tre cose che devono restare coerenti:
 * quando riscaricare i dati, quando dire che l'auto parcheggiata è fuori zona e quando la
 * vista in realtà aumentata non ha niente di affidabile da mostrare.
 */
object DataCoverage {

    /**
     * Frazione del raggio entro cui un punto è considerato ben circondato dai dati.
     * A 0,6 su un raggio di 300 m restano sempre almeno 120 m di edifici mappati intorno
     * al punto, abbastanza per l'ombra di qualsiasi palazzo con il sole sopra i 10°.
     */
    const val RELIABLE_FRACTION = 0.6

    fun reliableRadiusMeters(radiusMeters: Int): Double = radiusMeters * RELIABLE_FRACTION

    /** Vero se [point] è abbastanza interno all'area scaricata intorno a [center]. */
    fun isReliable(center: LatLng, point: LatLng, radiusMeters: Int): Boolean =
        LocalPlane(center).distanceMeters(center, point) <= reliableRadiusMeters(radiusMeters)

    /** Come [isReliable] ma con il punto già in coordinate locali rispetto al centro. */
    fun isReliable(pointFromCenter: Vec2, radiusMeters: Int): Boolean =
        pointFromCenter.length <= reliableRadiusMeters(radiusMeters)
}
