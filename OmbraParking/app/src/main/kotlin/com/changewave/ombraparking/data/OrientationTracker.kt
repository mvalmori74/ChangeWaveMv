package com.changewave.ombraparking.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Orientamento del telefono pronto per la proiezione AR.
 *
 * @param deviceToWorld matrice 3x3 row-major che porta dai assi dello schermo al mondo
 *   est/nord/alto, già corretta per la rotazione del display e per la declinazione magnetica.
 * @param magneticAccuracy ultimo valore riportato dal sensore
 *   (`SensorManager.SENSOR_STATUS_ACCURACY_*`): sotto MEDIUM la bussola va calibrata.
 */
class DeviceOrientation(
    val deviceToWorld: DoubleArray,
    val magneticAccuracy: Int,
)

/**
 * Legge il vettore di rotazione del dispositivo e lo trasforma nel riferimento che serve
 * alla vista AR.
 *
 * Due correzioni sono indispensabili perché le ombre finiscano nel punto giusto:
 * - la **rotazione del display**, altrimenti su un tablet (il cui orientamento naturale è
 *   orizzontale) tutto risulta ruotato di 90°;
 * - la **declinazione magnetica**, perché i sensori puntano al nord magnetico mentre la
 *   posizione del sole è calcolata rispetto al nord geografico. In Italia sono 3-4 gradi,
 *   altrove anche quindici: a 50 metri di distanza è la larghezza di una strada.
 */
class OrientationTracker(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val isAvailable: Boolean get() = sensor != null

    private val sensor: Sensor?
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    /**
     * @param displayRotation una delle costanti `Surface.ROTATION_*` dello schermo corrente.
     * @param declinationDegrees differenza fra nord geografico e nord magnetico nel punto in
     *   cui ci si trova (`android.hardware.GeomagneticField.getDeclination()`).
     */
    fun orientation(
        displayRotation: () -> Int,
        declinationDegrees: () -> Float,
    ): Flow<DeviceOrientation> = callbackFlow {
        val rotationSensor = sensor
        if (rotationSensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val remapped = FloatArray(9)
            private var lastEmittedNanos = 0L
            private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

            override fun onSensorChanged(event: SensorEvent) {
                if (event.timestamp - lastEmittedNanos < MIN_INTERVAL_NANOS) return
                lastEmittedNanos = event.timestamp

                // Alcuni dispositivi riportano 5 componenti: getRotationMatrixFromVector
                // accetta solo le prime quattro.
                val vector = if (event.values.size > 4) event.values.copyOf(4) else event.values
                SensorManager.getRotationMatrixFromVector(rotationMatrix, vector)

                val (axisX, axisY) = screenAxes(displayRotation())
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)

                val corrected = applyDeclination(remapped, declinationDegrees())
                trySend(DeviceOrientation(corrected, accuracy))
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate() // se il disegno rallenta si salta ai dati più recenti invece di accodarli

    private companion object {

        /** ~30 aggiornamenti al secondo: più che sufficienti e molto meno lavoro di disegno. */
        const val MIN_INTERVAL_NANOS = 33_000_000L

        /** Assi del mondo su cui cadono l'asse X e Y dello schermo per ogni rotazione. */
        fun screenAxes(displayRotation: Int): Pair<Int, Int> = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }

        /**
         * Ruota il riferimento dal nord magnetico a quello geografico:
         * una direzione di rilevamento magnetico θ corrisponde a θ + declinazione veri.
         */
        fun applyDeclination(matrix: FloatArray, declinationDegrees: Float): DoubleArray {
            val result = DoubleArray(9)
            if (declinationDegrees == 0f) {
                for (i in 0 until 9) result[i] = matrix[i].toDouble()
                return result
            }
            val angle = Math.toRadians(declinationDegrees.toDouble())
            val cosine = cos(angle)
            val sine = sin(angle)
            // rotazione = [[cos, sin, 0], [-sin, cos, 0], [0, 0, 1]] applicata a sinistra
            for (column in 0 until 3) {
                val east = matrix[column].toDouble()
                val north = matrix[3 + column].toDouble()
                result[column] = cosine * east + sine * north
                result[3 + column] = -sine * east + cosine * north
                result[6 + column] = matrix[6 + column].toDouble()
            }
            return result
        }
    }
}
