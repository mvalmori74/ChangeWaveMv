package com.changewave.ombraparking.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.changewave.ombraparking.core.shadow.ShadeQuality
import com.changewave.ombraparking.ui.ShadowUiState
import com.changewave.ombraparking.ui.asClockTime
import com.changewave.ombraparking.ui.cardinalName
import com.changewave.ombraparking.ui.color
import com.changewave.ombraparking.ui.emoji
import com.changewave.ombraparking.ui.formatDuration
import com.changewave.ombraparking.ui.label
import java.time.Duration

/** Il verdetto sul punto scelto: è la risposta alla domanda "qui ci sarà ombra?". */
@Composable
fun ShadeSummaryCard(state: ShadowUiState, modifier: Modifier = Modifier) {
    val shade = state.shade
    val quality = shade?.quality

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quality?.emoji ?: "⏳",
                    fontSize = 34.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column {
                    Text(
                        text = quality?.label ?: "Calcolo in corso…",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = quality?.color ?: MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = transitionText(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            shade?.obstacle?.name?.let { name ->
                Text(
                    text = "Ombra di $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.sun?.let { sun ->
                val position = if (sun.isAboveHorizon) {
                    "Sole a ${sun.elevationDegrees.toInt()}° sull'orizzonte, " +
                        "verso ${cardinalName(sun.azimuthDegrees).lowercase()} " +
                        "(${sun.azimuthDegrees.toInt()}°)"
                } else {
                    "Sole sotto l'orizzonte"
                }
                Text(
                    text = position,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.dayLight?.let { light ->
                val sunrise = light.sunrise?.asClockTime(state.zone) ?: "—"
                val sunset = light.sunset?.asClockTime(state.zone) ?: "—"
                Text(
                    text = "Alba $sunrise · tramonto $sunset",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Frase che dice quanto dura la situazione attuale e cosa succede dopo. */
private fun transitionText(state: ShadowUiState): String {
    val forecast = state.forecast ?: return "In attesa dei dati sugli edifici"
    val instant = state.instant
    val zone = state.zone

    return when (state.shade?.quality) {
        ShadeQuality.SHADE, ShadeQuality.DAPPLED -> {
            val end = forecast.shadeEndsAfter(instant)
            if (end == null) {
                "Resta all'ombra per tutto il giorno"
            } else {
                val minutes = Duration.between(instant, end).toMinutes().toInt()
                "Ombra fino alle ${end.asClockTime(zone)} (ancora ${formatDuration(minutes)})"
            }
        }

        ShadeQuality.SUN -> {
            val next = forecast.nextShadeStart(instant)
            if (next == null) {
                "Nessuna ombra prevista qui per il resto della giornata"
            } else {
                val minutes = Duration.between(instant, next).toMinutes().toInt()
                "Ombra dalle ${next.asClockTime(zone)} (fra ${formatDuration(minutes)})"
            }
        }

        ShadeQuality.NIGHT -> {
            val sunrise = state.dayLight?.sunrise?.asClockTime(zone)
            if (sunrise == null) "Il sole non sorge in questa data" else "Il sole sorge alle $sunrise"
        }

        null -> "Calcolo in corso…"
    }
}
