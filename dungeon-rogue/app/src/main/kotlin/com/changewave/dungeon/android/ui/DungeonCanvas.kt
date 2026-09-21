package com.changewave.dungeon.android.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.changewave.dungeon.android.vm.MapSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** Numero di colonne mostrate: il resto della mappa scorre con il personaggio. */
private const val VIEWPORT_COLUMNS = 19

/**
 * Renderer della mappa. Disegna solo la finestra attorno al personaggio, in stile
 * roguelike classico (glifi ASCII), con tre livelli di luminosita': visibile,
 * esplorato (memoria) e ignoto.
 *
 * Il tocco su una casella invia la direzione corrispondente: un passo alla volta,
 * cosi' il giocatore non "scivola" dentro un'imboscata.
 */
@Composable
fun DungeonCanvas(
    map: MapSnapshot,
    modifier: Modifier = Modifier,
    onDirection: (dx: Int, dy: Int) -> Unit,
) {
    val glyphPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier.pointerInput(map) {
            detectTapGestures { offset ->
                val geometry = viewportGeometry(size.width.toFloat(), size.height.toFloat(), map)
                val column = ((offset.x - geometry.offsetX) / geometry.tileSize).toInt() + geometry.firstColumn
                val row = ((offset.y - geometry.offsetY) / geometry.tileSize).toInt() + geometry.firstRow
                val dx = (column - map.playerX).sign
                val dy = (row - map.playerY).sign
                if (dx != 0 || dy != 0) onDirection(dx, dy)
            }
        },
    ) {
        val geometry = viewportGeometry(size.width, size.height, map)
        glyphPaint.textSize = geometry.tileSize * 0.78f

        drawRect(color = DungeonColors.Background, size = Size(size.width, size.height))

        for (row in geometry.firstRow until geometry.firstRow + geometry.rows) {
            for (column in geometry.firstColumn until geometry.firstColumn + geometry.columns) {
                if (column < 0 || row < 0 || column >= map.width || row >= map.height) continue
                val visible = map.isVisible(column, row)
                val explored = map.isExplored(column, row)
                if (!visible && !explored) continue

                val left = geometry.offsetX + (column - geometry.firstColumn) * geometry.tileSize
                val top = geometry.offsetY + (row - geometry.firstRow) * geometry.tileSize
                val glyph = map.glyphAt(column, row)
                val kind = map.kindAt(column, row)

                drawTile(
                    left = left,
                    top = top,
                    tileSize = geometry.tileSize,
                    glyph = glyph,
                    kind = kind,
                    visible = visible,
                    paint = glyphPaint,
                )
            }
        }
    }
}

private class Geometry(
    val tileSize: Float,
    val columns: Int,
    val rows: Int,
    val firstColumn: Int,
    val firstRow: Int,
    val offsetX: Float,
    val offsetY: Float,
)

private fun viewportGeometry(width: Float, height: Float, map: MapSnapshot): Geometry {
    val tileSize = width / VIEWPORT_COLUMNS
    val columns = VIEWPORT_COLUMNS
    val rows = max(1, (height / tileSize).toInt())
    // Il personaggio resta al centro finche' possibile, poi la vista si ferma ai bordi.
    val firstColumn = min(max(0, map.playerX - columns / 2), max(0, map.width - columns))
    val firstRow = min(max(0, map.playerY - rows / 2), max(0, map.height - rows))
    val offsetX = 0f
    val offsetY = 0f
    return Geometry(tileSize, columns, rows, firstColumn, firstRow, offsetX, offsetY)
}

private fun DrawScope.drawTile(
    left: Float,
    top: Float,
    tileSize: Float,
    glyph: Char,
    kind: Byte,
    visible: Boolean,
    paint: Paint,
) {
    val background = when {
        !visible -> DungeonColors.Background
        kind == MapSnapshot.KIND_WALL -> DungeonColors.StoneDim
        else -> Color(0xFF16161F)
    }
    drawRect(color = background, topLeft = Offset(left, top), size = Size(tileSize, tileSize))

    val color = glyphColor(kind, glyph, visible)
    paint.color = color.toArgb()
    drawContext.canvas.nativeCanvas.drawText(
        glyph.toString(),
        left + tileSize / 2f,
        top + tileSize * 0.78f,
        paint,
    )
}

private fun glyphColor(kind: Byte, glyph: Char, visible: Boolean): Color {
    val base = when (kind) {
        MapSnapshot.KIND_PLAYER -> DungeonColors.Amber
        MapSnapshot.KIND_BOSS -> DungeonColors.Arcane
        MapSnapshot.KIND_MONSTER -> DungeonColors.Blood
        MapSnapshot.KIND_ITEM -> if (glyph == '$') DungeonColors.Gold else DungeonColors.Poison
        MapSnapshot.KIND_TRAP -> DungeonColors.Blood
        MapSnapshot.KIND_STAIRS -> DungeonColors.Gold
        MapSnapshot.KIND_DOOR -> Color(0xFFB07B3E)
        MapSnapshot.KIND_WALL -> DungeonColors.Stone
        else -> DungeonColors.FloorLit
    }
    // Cio' che si ricorda ma non si vede e' disegnato spento, virato al blu.
    return if (visible) base else base.dimmed()
}

/** Versione "memoria della mappa" di un colore: scura e fredda, ma leggibile. */
private fun Color.dimmed(): Color = Color(
    red = red * 0.45f,
    green = green * 0.45f,
    blue = blue * 0.70f,
    alpha = 1f,
)
