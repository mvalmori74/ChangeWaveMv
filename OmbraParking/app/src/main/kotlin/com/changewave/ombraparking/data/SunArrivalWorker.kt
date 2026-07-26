package com.changewave.ombraparking.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.changewave.ombraparking.OmbraParkingApplication
import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import kotlinx.datetime.Instant

/**
 * Mostra l'avviso all'orario programmato.
 *
 * Il lavoro pesante (dove sarà l'ombra e quando) è già stato fatto quando la notifica è
 * stata programmata: qui non serve né rete né GPS, quindi il worker non ha vincoli e parte
 * anche in aereo. Prima di avvisare controlla però che l'auto sia ancora quella: se nel
 * frattempo è stata spostata o dimenticata, l'avviso non ha più senso.
 */
class SunArrivalWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val latitude = inputData.getDouble(KEY_LATITUDE, Double.NaN)
        val longitude = inputData.getDouble(KEY_LONGITUDE, Double.NaN)
        val arrivalMillis = inputData.getLong(KEY_ARRIVAL_MILLIS, 0L)
        if (latitude.isNaN() || longitude.isNaN() || arrivalMillis <= 0L) return Result.failure()

        val application = applicationContext as? OmbraParkingApplication ?: return Result.failure()
        val car = application.parkedCarStore.parkedCar.value ?: return Result.success()

        val scheduledFor = LatLng(latitude, longitude)
        val moved = LocalPlane(scheduledFor).distanceMeters(scheduledFor, car.position)
        if (moved > MAX_DRIFT_M) return Result.success()

        SunArrivalNotifier(applicationContext).notifySunArriving(Instant.fromEpochMilliseconds(arrivalMillis))
        return Result.success()
    }

    companion object {
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ARRIVAL_MILLIS = "arrival_millis"

        /** Oltre questo scarto l'auto salvata non è più quella per cui era stato fissato l'avviso. */
        private const val MAX_DRIFT_M = 25.0
    }
}
