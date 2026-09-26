package com.changewave.dungeon.android.data

import android.content.Context

/** Preferenze audio dell'utente, modificabili dalla schermata di configurazione. */
data class AudioSettings(
    val musicEnabled: Boolean = true,
    /** Volume lineare 0..1 scelto dall'utente. */
    val musicVolume: Float = 0.6f,
    /**
     * Brano scelto dall'utente fra i file del telefono, come URI del content
     * provider. Null = si usa la musica generata dal gioco.
     *
     * Il file non viene copiato nell'app: si legge dove sta, con il permesso
     * persistente concesso dal selettore di sistema. Cosi' l'utente puo' usare
     * la musica di cui dispone senza che nulla di tutto cio' entri nell'APK.
     */
    val customTrackUri: String? = null,
    /** Nome del file scelto, solo per mostrarlo nell'interfaccia. */
    val customTrackName: String? = null,
    /** Livello del dungeon in cui il brano personalizzato sostituisce la musica generata. */
    val customTrackDepth: Int = 1,
)

/**
 * Persistenza delle impostazioni in SharedPreferences: poche chiavi, lettura
 * sincrona all'avvio, scrittura asincrona ad ogni modifica.
 */
class SettingsStore(context: Context) {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): AudioSettings = AudioSettings(
        musicEnabled = preferences.getBoolean(KEY_MUSIC_ENABLED, true),
        musicVolume = preferences.getFloat(KEY_MUSIC_VOLUME, 0.6f).coerceIn(0f, 1f),
        customTrackUri = preferences.getString(KEY_TRACK_URI, null),
        customTrackName = preferences.getString(KEY_TRACK_NAME, null),
        customTrackDepth = preferences.getInt(KEY_TRACK_DEPTH, 1).coerceIn(1, 10),
    )

    fun save(settings: AudioSettings) {
        preferences.edit()
            .putBoolean(KEY_MUSIC_ENABLED, settings.musicEnabled)
            .putFloat(KEY_MUSIC_VOLUME, settings.musicVolume.coerceIn(0f, 1f))
            .putString(KEY_TRACK_URI, settings.customTrackUri)
            .putString(KEY_TRACK_NAME, settings.customTrackName)
            .putInt(KEY_TRACK_DEPTH, settings.customTrackDepth.coerceIn(1, 10))
            .apply()
    }

    companion object {
        const val FILE_NAME = "impostazioni"
        private const val KEY_MUSIC_ENABLED = "musica_attiva"
        private const val KEY_MUSIC_VOLUME = "musica_volume"
        private const val KEY_TRACK_URI = "brano_uri"
        private const val KEY_TRACK_NAME = "brano_nome"
        private const val KEY_TRACK_DEPTH = "brano_livello"
    }
}
