package com.changewave.ombraparking.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.changewave.ombraparking.core.alarm.SunWarning
import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.shadow.ShadeQuality
import com.changewave.ombraparking.data.ParkedCar
import com.changewave.ombraparking.ui.ParkedCarStatus
import com.changewave.ombraparking.ui.asClockTime
import com.changewave.ombraparking.ui.cardinalName
import com.changewave.ombraparking.ui.emoji
import com.changewave.ombraparking.ui.formatDistance
import com.changewave.ombraparking.ui.formatDuration
import com.changewave.ombraparking.ui.relativePosition
import kotlinx.datetime.TimeZone

/**
 * Riga del posto auto: se non è salvato offre il pulsante per salvarlo, altrimenti dice
 * dov'è l'auto e cosa le sta succedendo adesso.
 */
@Composable
fun ParkedCarBar(
    parkedCar: ParkedCar?,
    status: ParkedCarStatus?,
    userLocation: LatLng?,
    zone: TimeZone,
    onPark: () -> Unit,
    onClear: () -> Unit,
    /** Se presente, toccando la riga si va a vedere l'auto (ha senso solo sulla mappa). */
    onShowCar: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (parkedCar == null) {
        OutlinedButton(onClick = onPark, modifier = modifier.fillMaxWidth()) {
            Text("🚗  Ho parcheggiato qui")
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = (if (onShowCar != null) Modifier.clickable(onClick = onShowCar) else Modifier)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🚗", fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = whereText(parkedCar, userLocation, zone),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText(status, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Dimentica il posto auto")
            }
        }
    }
}

private fun whereText(car: ParkedCar, userLocation: LatLng?, zone: TimeZone): String {
    val parkedAt = "parcheggiata alle ${car.parkedAt.asClockTime(zone)}"
    val user = userLocation ?: return "Auto $parkedAt"

    val relative = relativePosition(user, car.position)
    val where = if (relative.distanceMeters < 15) {
        "Sei all'auto"
    } else {
        "Auto a ${formatDistance(relative.distanceMeters)} verso " +
            cardinalName(relative.azimuthDegrees).lowercase()
    }
    return "$where · $parkedAt"
}

private fun statusText(status: ParkedCarStatus?, zone: TimeZone): String {
    if (status == null) return "Troppo lontana per sapere se è al sole"

    val prefix = "${status.quality.emoji} "
    return when (status.quality) {
        ShadeQuality.SHADE, ShadeQuality.DAPPLED -> {
            val arrival = status.sunArrivesAt
            if (arrival == null) {
                prefix + "All'ombra per il resto della giornata"
            } else {
                val minutes = (arrival - status.computedAt).inWholeMinutes.toInt()
                prefix + "All'ombra, il sole arriva alle ${arrival.asClockTime(zone)} " +
                    "(fra ${formatDuration(minutes)}) · ti avviso " +
                    "${SunWarning.DEFAULT_LEAD_MINUTES} min prima"
            }
        }

        ShadeQuality.SUN -> {
            val shade = status.shadeArrivesAt
            if (shade == null) {
                prefix + "Al sole fino al tramonto"
            } else {
                val minutes = (shade - status.computedAt).inWholeMinutes.toInt()
                prefix + "Al sole, ombra dalle ${shade.asClockTime(zone)} " +
                    "(fra ${formatDuration(minutes)})"
            }
        }

        ShadeQuality.NIGHT -> prefix + "È notte, nessun sole sull'auto"
    }
}
