package com.changewave.dungeon.game

import kotlinx.serialization.Serializable

enum class MessageKind { INFO, COMBAT, GOOD, BAD, CRITICAL, SYSTEM }

@Serializable
data class LogMessage(val turn: Int, val text: String, val kind: MessageKind = MessageKind.INFO)

/** Registro messaggi a finestra scorrevole: l'interfaccia ne mostra le ultime righe. */
@Serializable
class MessageLog(private val capacity: Int = 200) {

    private val entries: MutableList<LogMessage> = mutableListOf()

    fun add(turn: Int, text: String, kind: MessageKind = MessageKind.INFO) {
        entries.add(LogMessage(turn, text, kind))
        while (entries.size > capacity) entries.removeAt(0)
    }

    fun last(count: Int): List<LogMessage> = entries.takeLast(count)

    fun all(): List<LogMessage> = entries.toList()

    fun clear() = entries.clear()

    val size: Int get() = entries.size
}
