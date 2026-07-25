package com.changewave.ombraparking.core.shadow

import com.changewave.ombraparking.core.geo.Polygons
import com.changewave.ombraparking.core.geo.Vec2

enum class ObstacleKind {
    /** Edificio: ombra piena, dal suolo alla gronda. */
    BUILDING,

    /** Albero: chioma sospesa che filtra la luce invece di bloccarla del tutto. */
    TREE,

    /** Muro, recinzione, cartellone: ombra piena ma su una sagoma sottile. */
    WALL,
}

/**
 * Un volume che proietta ombra, già proiettato nel piano locale in metri.
 *
 * @param footprint impronta al suolo (vertici in senso antiorario, primo non ripetuto).
 * @param heightMeters quota della sommità rispetto al suolo.
 * @param baseHeightMeters quota da cui inizia il volume: 0 per un edificio, l'inizio
 *   della chioma per un albero (sotto la chioma la luce passa).
 */
data class Obstacle(
    val id: String,
    val kind: ObstacleKind,
    val footprint: List<Vec2>,
    val heightMeters: Double,
    val baseHeightMeters: Double = 0.0,
    val name: String? = null,
) {
    /** Quanta luce blocca: 1 = ombra piena, valori minori = ombra screziata. */
    val opacity: Double
        get() = when (kind) {
            ObstacleKind.BUILDING, ObstacleKind.WALL -> 1.0
            ObstacleKind.TREE -> 0.65
        }

    val center: Vec2 by lazy { Polygons.centroid(footprint) }

    /** Raggio di ingombro dal centro: usato per scartare in fretta gli ostacoli lontani. */
    val boundingRadius: Double by lazy { Polygons.boundingRadius(footprint, center) }
}
