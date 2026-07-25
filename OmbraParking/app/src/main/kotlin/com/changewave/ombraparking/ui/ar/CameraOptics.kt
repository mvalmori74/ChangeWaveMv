package com.changewave.ombraparking.ui.ar

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ResolutionInfo
import kotlin.math.atan
import kotlin.math.tan

/**
 * Campo visivo dell'anteprima, in gradi, misurato sull'area effettivamente mostrata a schermo.
 * È il parametro che decide se le ombre disegnate coincidono con quelle vere.
 */
data class FieldOfView(val horizontalDegrees: Double, val verticalDegrees: Double) {
    companion object {
        /** Valori tipici di una fotocamera posteriore, usati quando l'ottica non è leggibile. */
        val FALLBACK = FieldOfView(horizontalDegrees = 52.0, verticalDegrees = 67.0)
    }
}

/**
 * Ricava il campo visivo dalla fotocamera e lo adatta a come l'anteprima riempie lo schermo.
 *
 * Con `PreviewView.ScaleType.FILL_CENTER` l'immagine viene ingrandita fino a coprire la vista
 * e uno dei due lati viene tagliato: usare il campo visivo nominale del sensore porterebbe a
 * disegnare le ombre più larghe di quanto appaiano.
 */
object CameraOptics {

    @OptIn(ExperimentalCamera2Interop::class)
    fun sensorFieldOfView(cameraInfo: CameraInfo): FieldOfView? {
        return try {
            val camera2Info = Camera2CameraInfo.from(cameraInfo)
            val focalLength = camera2Info
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: return null
            val sensorSize = camera2Info
                .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            if (focalLength <= 0f) return null

            FieldOfView(
                horizontalDegrees = fov(sensorSize.width.toDouble(), focalLength.toDouble()),
                verticalDegrees = fov(sensorSize.height.toDouble(), focalLength.toDouble()),
            )
        } catch (error: IllegalArgumentException) {
            // Alcuni dispositivi non espongono le caratteristiche Camera2: meglio una stima
            // che una schermata vuota.
            null
        }
    }

    /**
     * Adatta il campo visivo del sensore alla porzione davvero visibile.
     *
     * @param sensorFov campo visivo nominale, lungo i lati del sensore.
     * @param bufferRotationDegrees rotazione dell'immagine rispetto al display
     *   (`ResolutionInfo.getRotationDegrees`): a 90° o 270° i due assi si scambiano.
     * @param sourceAspectRatio larghezza/altezza dell'anteprima già orientata come lo schermo.
     * @param viewAspectRatio larghezza/altezza della vista su cui viene disegnata.
     */
    fun visibleFieldOfView(
        sensorFov: FieldOfView,
        bufferRotationDegrees: Int,
        sourceAspectRatio: Double,
        viewAspectRatio: Double,
    ): FieldOfView {
        val rotated = bufferRotationDegrees % 180 != 0
        val screenFov = if (rotated) {
            FieldOfView(sensorFov.verticalDegrees, sensorFov.horizontalDegrees)
        } else {
            sensorFov
        }
        if (sourceAspectRatio <= 0.0 || viewAspectRatio <= 0.0) return screenFov

        return if (viewAspectRatio > sourceAspectRatio) {
            // La vista è più larga della sorgente: si vede tutta la larghezza, l'altezza è tagliata.
            FieldOfView(
                horizontalDegrees = screenFov.horizontalDegrees,
                verticalDegrees = scale(screenFov.horizontalDegrees, 1.0 / viewAspectRatio),
            )
        } else {
            FieldOfView(
                horizontalDegrees = scale(screenFov.verticalDegrees, viewAspectRatio),
                verticalDegrees = screenFov.verticalDegrees,
            )
        }
    }

    /** Aspetto dell'anteprima in coordinate schermo (larghezza/altezza). */
    fun sourceAspectRatio(resolutionInfo: ResolutionInfo?): Double {
        val resolution = resolutionInfo?.resolution ?: return 0.0
        val rotated = resolutionInfo.rotationDegrees % 180 != 0
        val width = if (rotated) resolution.height else resolution.width
        val height = if (rotated) resolution.width else resolution.height
        return if (height == 0) 0.0 else width.toDouble() / height.toDouble()
    }

    private fun fov(sensorDimension: Double, focalLength: Double): Double =
        Math.toDegrees(2 * atan(sensorDimension / (2 * focalLength)))

    /** Campo visivo dell'altro asse mantenendo la stessa distanza focale. */
    private fun scale(referenceFovDegrees: Double, ratio: Double): Double =
        Math.toDegrees(2 * atan(tan(Math.toRadians(referenceFovDegrees / 2)) * ratio))
}
