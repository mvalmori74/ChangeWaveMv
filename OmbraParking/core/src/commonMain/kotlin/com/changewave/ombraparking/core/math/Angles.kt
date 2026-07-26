package com.changewave.ombraparking.core.math

import kotlin.math.PI

/**
 * Conversioni fra gradi e radianti.
 *
 * `Math.toRadians` è una funzione della libreria Java e nel codice condiviso con iOS non
 * esiste: queste due righe la sostituiscono ovunque.
 */
internal fun Double.toRadians(): Double = this * PI / 180.0

internal fun Double.toDegrees(): Double = this * 180.0 / PI
