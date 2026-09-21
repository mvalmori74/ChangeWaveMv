package com.changewave.dungeon.android.data

import android.content.Context
import com.changewave.dungeon.game.GameState
import com.changewave.dungeon.game.SaveGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistenza su file privato dell'app. Il salvataggio e' unico (permadeath:
 * niente "save scumming" ricaricando prima di una morte) e viene sovrascritto
 * ad ogni turno significativo e quando l'app va in background.
 */
class SaveStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val temp = File(context.filesDir, "$FILE_NAME.tmp")

    fun hasSave(): Boolean = file.exists() && file.length() > 0

    suspend fun save(state: GameState) = withContext(Dispatchers.IO) {
        val text = SaveGame.encode(state)
        // Scrittura atomica: prima sul temporaneo, poi rename. Evita salvataggi
        // troncati se il processo viene ucciso durante la scrittura.
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    suspend fun load(): GameState? = withContext(Dispatchers.IO) {
        if (!hasSave()) return@withContext null
        val state = runCatching { SaveGame.decode(file.readText()) }.getOrNull()
        if (state == null) delete() // salvataggio corrotto o di versione incompatibile
        state
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        temp.delete()
        Unit
    }

    companion object {
        const val FILE_NAME = "partita.json"
    }
}
