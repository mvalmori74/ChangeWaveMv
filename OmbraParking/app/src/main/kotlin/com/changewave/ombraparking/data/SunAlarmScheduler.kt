package com.changewave.ombraparking.data

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.changewave.ombraparking.core.alarm.SunWarning
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.TimeUnit

/**
 * Programma (e riprogramma) l'avviso di sole in arrivo sull'auto.
 *
 * Un solo lavoro alla volta, identificato per nome: riprogrammarlo sostituisce il
 * precedente, così non si accumulano avvisi vecchi quando la previsione viene ricalcolata.
 *
 * WorkManager non garantisce l'orario al secondo — in Doze l'avviso può arrivare qualche
 * minuto tardi. È il compromesso accettato per non chiedere il permesso delle sveglie
 * esatte, che Android concede col contagocce; il preavviso di un quarto d'ora assorbe lo
 * scarto.
 */
class SunAlarmScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Ultimo avviso programmato: evita di riscrivere il lavoro a ogni ricalcolo. */
    private var scheduled: Scheduled? = null

    /**
     * Falso finché non abbiamo annullato almeno una volta in questa esecuzione: serve a
     * ripulire un avviso rimasto in coda da una sessione precedente, di cui in memoria non
     * resta traccia.
     */
    private var cancelledOnce = false

    /**
     * Fissa l'avviso per [sunArrivesAt]. Se non c'è nessun arrivo previsto, annulla
     * quello eventualmente programmato.
     */
    fun schedule(car: ParkedCar, sunArrivesAt: Instant?, now: Instant = Clock.System.now()) {
        val trigger = SunWarning.triggerTime(sunArrivesAt, now) ?: run {
            cancel()
            return
        }
        val arrival = sunArrivesAt ?: return

        val request = Scheduled(car.position.latitude, car.position.longitude, arrival)
        if (scheduled == request) return
        scheduled = request
        cancelledOnce = false

        val data = Data.Builder()
            .putDouble(SunArrivalWorker.KEY_LATITUDE, car.position.latitude)
            .putDouble(SunArrivalWorker.KEY_LONGITUDE, car.position.longitude)
            .putLong(SunArrivalWorker.KEY_ARRIVAL_MILLIS, arrival.toEpochMilliseconds())
            .build()

        val delayMillis = (trigger.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)
        val work = OneTimeWorkRequestBuilder<SunArrivalWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    fun cancel() {
        if (scheduled == null && cancelledOnce) return
        scheduled = null
        cancelledOnce = true
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /**
     * Chiave di confronto: stessa auto e stesso orario di arrivo significa avviso già
     * programmato. L'orario viene arrotondato al minuto perché il ricalcolo può restituire
     * istanti leggermente diversi senza che l'avviso debba cambiare.
     */
    private data class Scheduled(
        val latitude: Double,
        val longitude: Double,
        val arrivalMinute: Long,
    ) {
        constructor(latitude: Double, longitude: Double, arrival: Instant) :
            this(latitude, longitude, arrival.toEpochMilliseconds() / 60_000L)
    }

    private companion object {
        const val WORK_NAME = "sun_arrival_warning"
    }
}
