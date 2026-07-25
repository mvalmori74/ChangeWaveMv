package com.changewave.ombraparking.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.changewave.ombraparking.core.shadow.ShadeForecast
import com.changewave.ombraparking.ui.color
import com.changewave.ombraparking.ui.minuteOfDayAsClock
import com.changewave.ombraparking.ui.theme.ShadeColors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val MINUTES_IN_DAY = 24 * 60

/**
 * Barra della giornata: mostra a colpo d'occhio quando quel punto è al sole e quando
 * è in ombra, e permette di scegliere l'ora trascinando il dito.
 */
@Composable
fun ShadeTimelineStrip(
    forecast: ShadeForecast?,
    dayStart: Instant,
    selectedMinute: Int,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val markerColor = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ShadeColors.night.copy(alpha = 0.35f))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onMinuteSelected(minuteAt(offset.x, size.width.toFloat()))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        onMinuteSelected(minuteAt(change.position.x, size.width.toFloat()))
                    }
                },
        ) {
            val width = size.width
            val height = size.height

            forecast?.slots?.forEach { slot ->
                val startMinute = Duration.between(dayStart, slot.start).toMinutes().toFloat()
                val endMinute = Duration.between(dayStart, slot.end).toMinutes().toFloat()
                val left = (startMinute / MINUTES_IN_DAY) * width
                val right = (endMinute / MINUTES_IN_DAY) * width
                drawRect(
                    color = slot.quality.color.copy(alpha = 0.85f),
                    topLeft = Offset(left, 0f),
                    size = Size((right - left).coerceAtLeast(1f), height),
                )
            }

            // Tacche ogni tre ore per orientarsi.
            for (hour in 3 until 24 step 3) {
                val x = (hour / 24f) * width
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(x, height * 0.6f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                )
            }

            val markerX = (selectedMinute.toFloat() / MINUTES_IN_DAY) * width
            drawLine(
                color = markerColor,
                start = Offset(markerX, 0f),
                end = Offset(markerX, height),
                strokeWidth = 3f,
            )
            drawCircle(color = markerColor, radius = 6f, center = Offset(markerX, height / 2))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "24").forEach { hour ->
                Text(hour, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun minuteAt(x: Float, width: Float): Int {
    if (width <= 0f) return 0
    val ratio = (x / width).coerceIn(0f, 0.9999f)
    return (ratio * MINUTES_IN_DAY).toInt()
}

/** Scelta rapida del giorno e dell'ora: i casi d'uso veri sono "adesso" e "fra qualche ora". */
@Composable
fun QuickTimeChips(
    date: LocalDate,
    zone: ZoneId,
    selectedMinute: Int,
    onDateSelected: (LocalDate) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    onNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now(zone)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = date == today,
            onClick = { onDateSelected(today) },
            label = { Text("Oggi") },
        )
        FilterChip(
            selected = date == today.plusDays(1),
            onClick = { onDateSelected(today.plusDays(1)) },
            label = { Text("Domani") },
        )
        FilterChip(
            selected = false,
            onClick = onNow,
            label = { Text("Adesso") },
        )
        FilterChip(
            selected = false,
            onClick = { onMinuteSelected((selectedMinute + 60) % MINUTES_IN_DAY) },
            label = { Text("+1 h") },
        )
        Text(
            text = minuteOfDayAsClock(selectedMinute),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
