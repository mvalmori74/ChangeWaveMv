package com.changewave.ombraparking.data

import android.content.Context
import com.changewave.ombraparking.core.geo.LatLng
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dove e quando è stata lasciata l'auto. */
data class ParkedCar(
    val position: LatLng,
    val parkedAt: Instant,
)

/**
 * Ricorda il posto auto fra un avvio e l'altro.
 *
 * È un dato solo: due coordinate e un orario. Le SharedPreferences bastano e non aggiungono
 * dipendenze; il valore corrente viene esposto come flusso così mappa e AR si aggiornano
 * insieme senza doverlo rileggere.
 */
class ParkedCarStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _parkedCar = MutableStateFlow(read())
    val parkedCar: StateFlow<ParkedCar?> = _parkedCar.asStateFlow()

    fun save(position: LatLng, parkedAt: Instant = Instant.now()) {
        preferences.edit()
            .putLong(KEY_LATITUDE, java.lang.Double.doubleToRawLongBits(position.latitude))
            .putLong(KEY_LONGITUDE, java.lang.Double.doubleToRawLongBits(position.longitude))
            .putLong(KEY_PARKED_AT, parkedAt.toEpochMilli())
            .apply()
        _parkedCar.value = ParkedCar(position, parkedAt)
    }

    fun clear() {
        preferences.edit().clear().apply()
        _parkedCar.value = null
    }

    private fun read(): ParkedCar? {
        if (!preferences.contains(KEY_PARKED_AT)) return null
        val latitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_LATITUDE, 0L))
        val longitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_LONGITUDE, 0L))
        val parkedAt = preferences.getLong(KEY_PARKED_AT, 0L)
        if (!latitude.isFinite() || !longitude.isFinite() || parkedAt <= 0L) return null
        return ParkedCar(LatLng(latitude, longitude), Instant.ofEpochMilli(parkedAt))
    }

    private companion object {
        const val PREFERENCES_NAME = "parked_car"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_PARKED_AT = "parked_at"
    }
}
