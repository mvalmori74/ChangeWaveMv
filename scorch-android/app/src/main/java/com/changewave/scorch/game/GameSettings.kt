package com.changewave.scorch.game

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class Difficulty(val label: String, val angleError: Float, val powerError: Float, val adapt: Float) {
    /** Mira grossolana, corregge poco fra un tiro e l'altro. */
    ROOKIE("Recluta", 7.0f, 0.16f, 0.35f),

    /** Mira discreta, impara dagli errori. */
    VETERAN("Veterano", 2.5f, 0.06f, 0.75f),

    /** Praticamente infallibile. */
    CYBORG("Cyborg", 0.6f, 0.012f, 1.0f)
}

@Parcelize
data class GameSettings(
    val playerCount: Int = 3,
    val humanCount: Int = 1,
    val rounds: Int = 3,
    val difficultyOrdinal: Int = Difficulty.VETERAN.ordinal,
    val windEnabled: Boolean = true,
    val startMoney: Int = 10_000,

    /** Seme della partita: due dispositivi con lo stesso seme generano lo stesso mondo. */
    val seed: Long = 0L,

    /** Indice del carro comandato da questo dispositivo; -1 = tutti locali (hot-seat). */
    val localPlayerIndex: Int = -1,

    /** Questo dispositivo ospita la partita Bluetooth ed e' quindi autorevole sullo stato. */
    val isHost: Boolean = false,

    /** Nome mostrato per il giocatore remoto. */
    val remoteName: String = ""
) : Parcelable {

    val difficulty: Difficulty
        get() {
            val all = Difficulty.values()
            return all[difficultyOrdinal.coerceIn(0, all.size - 1)]
        }

    val isNetworkGame: Boolean get() = localPlayerIndex >= 0
}
