package com.changewave.ombraparking.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Disegna le ombre sopra la mappa.
 *
 * Le sagome arrivano già calcolate: qui si tratta solo di proiettarle in pixel. Tutte le
 * parti finiscono in un unico [Path] con riempimento *non-zero*, così le sovrapposizioni
 * vengono riempite una volta sola invece di scurirsi a strati.
 */
class ShadowMapOverlay : Overlay() {

    var shadowPolygons: List<List<GeoPoint>> = emptyList()
    var buildingFootprints: List<List<GeoPoint>> = emptyList()
    var target: GeoPoint? = null
    var userPosition: GeoPoint? = null

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(105, 18, 32, 67)
    }
    private val buildingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 138, 151, 184)
    }
    private val buildingStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(160, 138, 151, 184)
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.argb(255, 255, 201, 77)
    }
    private val userFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 66, 133, 244)
    }
    private val userStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val reusablePoint = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection

        buildPath(buildingFootprints, projection)?.let { path ->
            canvas.drawPath(path, buildingFillPaint)
            canvas.drawPath(path, buildingStrokePaint)
        }
        buildPath(shadowPolygons, projection)?.let { path ->
            canvas.drawPath(path, shadowPaint)
        }

        target?.let { point ->
            projection.toPixels(point, reusablePoint)
            val x = reusablePoint.x.toFloat()
            val y = reusablePoint.y.toFloat()
            canvas.drawCircle(x, y, 14f, targetPaint)
            canvas.drawLine(x - 22f, y, x - 16f, y, targetPaint)
            canvas.drawLine(x + 16f, y, x + 22f, y, targetPaint)
            canvas.drawLine(x, y - 22f, x, y - 16f, targetPaint)
            canvas.drawLine(x, y + 16f, x, y + 22f, targetPaint)
        }

        userPosition?.let { point ->
            projection.toPixels(point, reusablePoint)
            val x = reusablePoint.x.toFloat()
            val y = reusablePoint.y.toFloat()
            canvas.drawCircle(x, y, 9f, userFillPaint)
            canvas.drawCircle(x, y, 9f, userStrokePaint)
        }
    }

    private fun buildPath(polygons: List<List<GeoPoint>>, projection: org.osmdroid.views.Projection): Path? {
        if (polygons.isEmpty()) return null
        val path = Path()
        path.fillType = Path.FillType.WINDING
        var added = false
        for (polygon in polygons) {
            if (polygon.size < 3) continue
            projection.toPixels(polygon[0], reusablePoint)
            path.moveTo(reusablePoint.x.toFloat(), reusablePoint.y.toFloat())
            for (index in 1 until polygon.size) {
                projection.toPixels(polygon[index], reusablePoint)
                path.lineTo(reusablePoint.x.toFloat(), reusablePoint.y.toFloat())
            }
            path.close()
            added = true
        }
        return if (added) path else null
    }
}
