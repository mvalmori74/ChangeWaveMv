package com.changewave.ombraparking.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.changewave.ombraparking.MainActivity
import com.changewave.ombraparking.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Costruisce e mostra l'avviso "il sole sta per arrivare sull'auto". */
class SunArrivalNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Mostra la notifica. Non fa nulla se l'utente ha negato il permesso o ha spento le
     * notifiche: è un avviso di cortesia, non deve mai diventare un errore.
     *
     * @return true se la notifica è stata effettivamente mostrata.
     */
    fun notifySunArriving(
        arrival: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        createChannel()

        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALY).format(arrival.atZone(zone))
        val minutes = Duration.between(now, arrival).toMinutes()
        val text = when {
            minutes <= 1 -> "Il sole ci arriva adesso (le $time)."
            minutes < 60 -> "Il sole ci arriva alle $time, fra $minutes minuti."
            else -> "Il sole ci arriva alle $time."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sun)
            .setContentTitle("Il sole sta per arrivare sull'auto")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text Se puoi, spostala all'ombra."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (securityException: SecurityException) {
            // Il permesso può essere revocato fra il controllo e l'invio.
            false
        }
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sole sull'auto",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avvisa poco prima che il sole raggiunga l'auto parcheggiata"
            setShowBadge(false)
        }
        val systemManager = context.getSystemService(NotificationManager::class.java)
        systemManager?.createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "sun_on_car"
        const val NOTIFICATION_ID = 1001
    }
}
