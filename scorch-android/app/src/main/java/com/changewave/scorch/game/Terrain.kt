package com.changewave.scorch.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Terreno distruttibile rappresentato come height-map (una quota di superficie per colonna).
 * Le esplosioni scavano crateri e la terra sovrastante frana verso il basso, come in Scorched Earth.
 */
class Terrain(val columns: Int, var worldHeight: Float) {

    /** Quota (coordinata Y, cresce verso il basso) della superficie di ogni colonna. */
    val surface = FloatArray(columns) { worldHeight * 0.7f }

    private val path = Path()
    private val grassPath = Path()
    private var pathDirty = true

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
    }

    private var topColor = Color.rgb(96, 74, 52)
    private var bottomColor = Color.rgb(38, 27, 20)
    private var grassColor = Color.rgb(122, 176, 92)

    val minSurface: Float get() = worldHeight * 0.20f
    val maxSurface: Float get() = worldHeight * 0.94f

    fun palette(top: Int, bottom: Int, grass: Int) {
        topColor = top
        bottomColor = bottom
        grassColor = grass
        fillPaint.shader = LinearGradient(
            0f, worldHeight * 0.15f, 0f, worldHeight,
            top, bottom, Shader.TileMode.CLAMP
        )
        grassPaint.color = grass
    }

    fun generate(rnd: Random) {
        val n = 256 // punti di controllo (potenza di due)
        val pts = FloatArray(n + 1)
        pts[0] = 0.45f + rnd.nextFloat() * 0.25f
        pts[n] = 0.45f + rnd.nextFloat() * 0.25f

        var step = n
        var scale = 0.30f
        while (step > 1) {
            val half = step / 2
            var i = half
            while (i <= n) {
                val mid = (pts[i - half] + pts[i + half]) * 0.5f
                pts[i] = mid + (rnd.nextFloat() * 2f - 1f) * scale
                i += step
            }
            step = half
            scale *= 0.52f
        }

        for (x in 0 until columns) {
            val t = x.toFloat() * n / columns
            val i = t.toInt().coerceIn(0, n - 1)
            val f = t - i
            val v = pts[i] * (1f - f) + pts[i + 1] * f
            surface[x] = (v * worldHeight).coerceIn(minSurface, maxSurface)
        }

        smooth(2)
        pathDirty = true
    }

    private fun smooth(passes: Int) {
        val tmp = FloatArray(columns)
        repeat(passes) {
            for (x in 0 until columns) {
                val a = surface[max(0, x - 1)]
                val b = surface[x]
                val c = surface[min(columns - 1, x + 1)]
                tmp[x] = (a + b * 2f + c) * 0.25f
            }
            System.arraycopy(tmp, 0, surface, 0, columns)
        }
    }

    fun heightAt(x: Float): Float {
        val i = x.toInt()
        if (i < 0) return surface[0]
        if (i >= columns - 1) return surface[columns - 1]
        val f = x - i
        return surface[i] * (1f - f) + surface[i + 1] * f
    }

    fun isSolid(x: Float, y: Float): Boolean = y >= heightAt(x)

    /** Pendenza locale (positiva = il terreno scende verso destra). */
    fun slopeAt(x: Float): Float {
        val d = 6f
        return (heightAt(x + d) - heightAt(x - d)) / (2f * d)
    }

    /** Scava un cratere circolare; la terra sopra il cratere frana. */
    fun crater(cx: Float, cy: Float, r: Float) {
        val from = max(0, (cx - r).toInt())
        val to = min(columns - 1, (cx + r).toInt())
        for (x in from..to) {
            val dx = x - cx
            val chord = r * r - dx * dx
            if (chord <= 0f) continue
            val half = sqrt(chord)
            val top = cy - half
            val bottom = cy + half
            val s = surface[x]
            when {
                bottom < s -> Unit                        // cratere sospeso in aria
                top <= s -> surface[x] = bottom           // apre la superficie: scende al fondo
                else -> surface[x] = s + (bottom - top)   // cavita' interna: la terra sopra frana
            }
            surface[x] = surface[x].coerceIn(minSurface * 0.4f, worldHeight + 40f)
        }
        pathDirty = true
    }

    /** Aggiunge una collinetta di terra (palla di terra). */
    fun addDirt(cx: Float, cy: Float, r: Float) {
        val from = max(0, (cx - r).toInt())
        val to = min(columns - 1, (cx + r).toInt())
        for (x in from..to) {
            val dx = x - cx
            val chord = r * r - dx * dx
            if (chord <= 0f) continue
            val half = sqrt(chord)
            val top = cy - half * 0.85f
            if (top < surface[x]) surface[x] = max(minSurface * 0.5f, top)
        }
        pathDirty = true
    }

    /** Livella una piazzola per posizionare un carro. */
    fun flatten(cx: Float, halfWidth: Float) {
        val from = max(0, (cx - halfWidth).toInt())
        val to = min(columns - 1, (cx + halfWidth).toInt())
        if (from >= to) return
        var sum = 0f
        for (x in from..to) sum += surface[x]
        val avg = sum / (to - from + 1)
        for (x in from..to) {
            val dx = abs(x - cx) / halfWidth
            val w = (1f - dx).coerceIn(0f, 1f)
            surface[x] = surface[x] * (1f - w) + avg * w
        }
        pathDirty = true
    }

    fun draw(canvas: Canvas, worldWidth: Float) {
        if (pathDirty) rebuildPath(worldWidth)
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(grassPath, grassPaint)
    }

    private fun rebuildPath(worldWidth: Float) {
        val stepPx = 3
        path.reset()
        grassPath.reset()
        val scaleX = worldWidth / columns

        path.moveTo(0f, surface[0])
        grassPath.moveTo(0f, surface[0])
        var x = stepPx
        while (x < columns) {
            val px = x * scaleX
            path.lineTo(px, surface[x])
            grassPath.lineTo(px, surface[x])
            x += stepPx
        }
        val lastX = (columns - 1) * scaleX
        path.lineTo(lastX, surface[columns - 1])
        grassPath.lineTo(lastX, surface[columns - 1])
        path.lineTo(worldWidth, worldHeight + 60f)
        path.lineTo(0f, worldHeight + 60f)
        path.close()
        pathDirty = false
    }

    fun markDirty() {
        pathDirty = true
    }

    fun resize(newHeight: Float) {
        val ratio = if (worldHeight > 0f) newHeight / worldHeight else 1f
        for (x in 0 until columns) surface[x] *= ratio
        worldHeight = newHeight
        palette(topColor, bottomColor, grassColor)
        pathDirty = true
    }
}
