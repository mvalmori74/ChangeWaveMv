package com.changewave.dungeon.android.data

import android.content.Context

/** Preferenze audio dell'utente, modificabili dalla schermata di configurazione. */
data class AudioSettings(
    val musicEnabled: Boolean = true,
    /** Volume lineare 0..1 scelto dall'utente. */
    val musicVolume: Float = 0.6f,
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
    )

    fun save(settings: AudioSettings) {
        preferences.edit()
            .putBoolean(KEY_MUSIC_ENABLED, settings.musicEnabled)
            .putFloat(KEY_MUSIC_VOLUME, settings.musicVolume.coerceIn(0f, 1f))
            .apply()
    }

    companion object {
        const val FILE_NAME = "impostazioni"
        private const val KEY_MUSIC_ENABLED = "musica_attiva"
        private const val KEY_MUSIC_VOLUME = "musica_volume"
    }
}
