package com.changewave.ombraparking.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.changewave.ombraparking.core.geo.LatLng
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Posizione dell'utente con la sua incertezza. */
data class UserLocation(val position: LatLng, val accuracyMeters: Float)

/** Espone la posizione dei servizi Google Play come flusso Compose-friendly. */
class LocationTracker(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Aggiornamenti di posizione finché il flusso resta raccolto.
     * Il permesso viene verificato all'avvio: senza, il flusso si chiude subito.
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMillis: Long = 5_000L): Flow<UserLocation> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateDistanceMeters(3f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    UserLocation(
                        position = LatLng(location.latitude, location.longitude),
                        accuracyMeters = location.accuracy,
                    )
                )
            }
        }

        // L'ultima posizione nota evita la schermata vuota mentre arriva il primo fix.
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                trySend(UserLocation(LatLng(location.latitude, location.longitude), location.accuracy))
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
