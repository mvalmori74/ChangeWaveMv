package com.changewave.dungeon.game

import kotlinx.serialization.json.Json

/**
 * Persistenza della partita: l'intero [GameState] (compreso lo stato del RNG)
 * viene serializzato in JSON. Ricaricando si riprende esattamente dal turno
 * salvato, con la stessa sequenza di dadi futura.
 *
 * Formato versionato: [CURRENT_VERSION] cambia quando lo schema non e' piu'
 * compatibile, cosi' l'app puo' scartare i salvataggi vecchi senza crashare.
 */
object SaveGame {

    const val CURRENT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    fun encode(state: GameState): String {
        val payload = json.encodeToString(GameState.serializer(), state)
        return "$CURRENT_VERSION\n$payload"
    }

    /** Restituisce null se il salvataggio e' di una versione incompatibile o corrotto. */
    fun decode(text: String): GameState? {
        val newline = text.indexOf('\n')
        if (newline <= 0) return null
        val version = text.substring(0, newline).trim().toIntOrNull() ?: return null
        if (version != CURRENT_VERSION) return null
        return try {
            val state = json.decodeFromString(GameState.serializer(), text.substring(newline + 1))
            // La visibilita' e' transiente: va ricalcolata dopo il caricamento.
            state.currentLevel.clearVisibility()
            state
        } catch (e: Exception) {
            null
        }
    }

    /** Ricrea un motore pronto all'uso a partire dal testo di un salvataggio. */
    fun loadEngine(text: String): GameEngine? {
        val state = decode(text) ?: return null
        val engine = GameEngine(state)
        engine.recomputeVisibility()
        return engine
    }
}
