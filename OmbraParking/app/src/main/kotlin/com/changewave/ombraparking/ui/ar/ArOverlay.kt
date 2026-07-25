package com.changewave.ombraparking.ui.ar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.changewave.ombraparking.core.ar.CameraProjector
import com.changewave.ombraparking.core.ar.ScreenPoint
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.geo.Vec3
import com.changewave.ombraparking.core.sun.SunPosition
import com.changewave.ombraparking.ui.theme.ShadeColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Disegno della scena aumentata: le ombre appoggiate al suolo, l'orizzonte con i punti
 * cardinali e il sole con il suo arco nel cielo.
 *
 * Tutte le coordinate sono in metri rispetto all'utente (est, nord, quota dalla fotocamera).
 */

/** Ombre oltre questa distanza non aiutano a scegliere un parcheggio. */
const val MAX_DRAW_DISTANCE_M = 150.0

/** Distanza a cui vengono disegnati gli elementi "all'infinito" (orizzonte, sole). */
private const val FAR_DISTANCE_M = 300.0

/** Verde del segnaposto dell'auto: lo stesso usato sulla mappa. */
private val PARKED_COLOR = Color(0xFF2E7D4E)

fun DrawScope.drawShadows(projector: CameraProjector, shapes: List<List<Vec3>>) {
    if (shapes.isEmpty()) return
    val path = Path()
    path.fillType = PathFillType.NonZero
    var hasShape = false

    for (shape in shapes) {
        val projected = projector.projectPolygon(shape)
        if (projected.size < 3) continue
        path.moveTo(projected[0].x, projected[0].y)
        for (index in 1 until projected.size) {
            path.lineTo(projected[index].x, projected[index].y)
        }
        path.close()
        hasShape = true
    }

    // Un unico riempimento: le parti sovrapposte non si scuriscono a vicenda.
    if (hasShape) drawPath(path, color = ShadeColors.shadowFillAr)
}

/** Linea dell'orizzonte con le lettere dei punti cardinali. */
fun DrawScope.drawHorizon(projector: CameraProjector, textMeasurer: TextMeasurer) {
    val horizonColor = Color.White.copy(alpha = 0.35f)
    var previous: ScreenPoint? = null

    for (bearing in 0..360 step 5) {
        val point = projector.project(bearingPoint(bearing.toDouble(), heightMeters = 0.0))
        val last = previous
        if (point != null && last != null) {
            drawLine(
                color = horizonColor,
                start = Offset(last.x, last.y),
                end = Offset(point.x, point.y),
                strokeWidth = 2f,
            )
        }
        previous = point
    }

    val cardinals = listOf(0.0 to "N", 90.0 to "E", 180.0 to "S", 270.0 to "O")
    for ((bearing, letter) in cardinals) {
        val point = projector.project(bearingPoint(bearing, heightMeters = 0.0)) ?: continue
        val layout = textMeasurer.measure(
            text = letter,
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp),
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(point.x - layout.size.width / 2f, point.y - layout.size.height - 6f),
        )
    }
}

/**
 * Sole all'ora scelta e traiettoria della giornata: rende evidente da dove arriva la luce
 * e, di conseguenza, da che parte si sposterà l'ombra.
 */
fun DrawScope.drawSun(projector: CameraProjector, sun: SunPosition, dayPath: List<SunPosition>) {
    for (position in dayPath) {
        if (!position.isAboveHorizon) continue
        val point = projector.project(position.skyPoint()) ?: continue
        drawCircle(
            color = ShadeColors.sun.copy(alpha = 0.35f),
            radius = 5f,
            center = Offset(point.x, point.y),
        )
    }

    if (!sun.isAboveHorizon) return
    val point = projector.project(sun.skyPoint()) ?: return
    drawCircle(color = ShadeColors.sun.copy(alpha = 0.28f), radius = 46f, center = Offset(point.x, point.y))
    drawCircle(color = ShadeColors.sun, radius = 20f, center = Offset(point.x, point.y))
}

/**
 * Segnaposto dell'auto parcheggiata, appoggiato al suolo con l'etichetta della distanza.
 *
 * @param offset posizione dell'auto rispetto all'utente, in metri (est, nord).
 * @param eyeHeightMeters altezza della fotocamera da terra.
 */
fun DrawScope.drawParkedCar(
    projector: CameraProjector,
    offset: Vec2,
    eyeHeightMeters: Double,
    textMeasurer: TextMeasurer,
) {
    val ground = projector.project(Vec3(offset.x, offset.y, -eyeHeightMeters)) ?: return
    val center = Offset(ground.x, ground.y)

    drawCircle(color = PARKED_COLOR.copy(alpha = 0.35f), radius = 34f, center = center)
    drawCircle(color = PARKED_COLOR, radius = 16f, center = center)
    drawCircle(color = Color.White, radius = 16f, center = center, style = Stroke(width = 3f))

    val layout = textMeasurer.measure(
        text = "🚗 ${ground.distanceMeters.toInt()} m",
        style = TextStyle(color = Color.White, fontSize = 15.sp),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height - 28f),
    )
}

/** Mirino centrale: il punto al suolo di cui l'app dà il verdetto. */
fun DrawScope.drawReticle(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = color, radius = 26f, center = center, style = Stroke(width = 4f))
    drawLine(color, Offset(center.x - 40f, center.y), Offset(center.x - 30f, center.y), strokeWidth = 4f)
    drawLine(color, Offset(center.x + 30f, center.y), Offset(center.x + 40f, center.y), strokeWidth = 4f)
    drawLine(color, Offset(center.x, center.y - 40f), Offset(center.x, center.y - 30f), strokeWidth = 4f)
    drawLine(color, Offset(center.x, center.y + 30f), Offset(center.x, center.y + 40f), strokeWidth = 4f)
}

private fun bearingPoint(bearingDegrees: Double, heightMeters: Double): Vec3 {
    val radians = Math.toRadians(bearingDegrees)
    return Vec3(sin(radians) * FAR_DISTANCE_M, cos(radians) * FAR_DISTANCE_M, heightMeters)
}

/** Punto sulla sfera celeste in cui disegnare il sole. */
private fun SunPosition.skyPoint(): Vec3 {
    val direction = direction3D()
    return Vec3(
        direction.east * FAR_DISTANCE_M,
        direction.north * FAR_DISTANCE_M,
        direction.up * FAR_DISTANCE_M,
    )
}

/**
 * Porta le sagome d'ombra dal piano locale a coordinate centrate sull'utente, scartando
 * quelle troppo lontane per essere utili.
 *
 * @param userLocal posizione dell'utente nello stesso piano locale delle sagome.
 * @param eyeHeightMeters altezza della fotocamera da terra: il suolo sta sotto di noi.
 */
fun toUserCentredShapes(
    shapes: List<List<Vec2>>,
    userLocal: Vec2,
    eyeHeightMeters: Double,
    maxDistanceMeters: Double = MAX_DRAW_DISTANCE_M,
): List<List<Vec3>> = shapes.mapNotNull { shape ->
    var visible = false
    val translated = ArrayList<Vec3>(shape.size)
    for (point in shape) {
        val relative = point - userLocal
        if (relative.length <= maxDistanceMeters) visible = true
        translated += Vec3(relative.x, relative.y, -eyeHeightMeters)
    }
    if (visible) translated else null
}
