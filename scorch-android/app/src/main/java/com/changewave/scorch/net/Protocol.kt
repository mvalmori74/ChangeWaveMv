package com.changewave.scorch.net

import com.changewave.scorch.game.GameSettings
import com.changewave.scorch.game.TankState
import com.changewave.scorch.game.TurnAction
import com.changewave.scorch.game.WorldSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Messaggi scambiati fra i due dispositivi. Formato binario compatto:
 * ogni pacchetto e' [tipo:1][lunghezza:4][payload], cosi' la lettura resta allineata
 * anche se una versione futura aggiunge campi.
 */
object Protocol {

    const val VERSION = 1

    const val HELLO = 1
    const val START = 2
    const val TURN = 3
    const val SNAPSHOT = 4
    const val SHOP = 5
    const val BYE = 6

    const val MAX_PAYLOAD = 1 shl 20 // 1 MiB: uno snapshot sta in ~7 KB

    class Packet(val type: Int, val payload: ByteArray)

    // ------------------------------------------------------------ framing

    fun write(out: DataOutputStream, type: Int, payload: ByteArray) {
        synchronized(out) {
            out.writeByte(type)
            out.writeInt(payload.size)
            out.write(payload)
            out.flush()
        }
    }

    fun read(input: DataInputStream): Packet {
        val type = input.readByte().toInt()
        val size = input.readInt()
        require(size in 0..MAX_PAYLOAD) { "pacchetto di dimensione non valida: $size" }
        val payload = ByteArray(size)
        input.readFully(payload)
        return Packet(type, payload)
    }

    private fun encode(block: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(block)
        return bytes.toByteArray()
    }

    private fun <T> decode(payload: ByteArray, block: (DataInputStream) -> T): T =
        DataInputStream(ByteArrayInputStream(payload)).use(block)

    // ------------------------------------------------------------ HELLO

    fun encodeHello(playerName: String): ByteArray = encode {
        it.writeInt(VERSION)
        it.writeUTF(playerName)
    }

    /** @return versione di protocollo e nome del giocatore. */
    fun decodeHello(payload: ByteArray): Pair<Int, String> = decode(payload) {
        Pair(it.readInt(), it.readUTF())
    }

    // ------------------------------------------------------------ START

    fun encodeStart(settings: GameSettings): ByteArray = encode {
        it.writeInt(settings.rounds)
        it.writeInt(settings.difficultyOrdinal)
        it.writeBoolean(settings.windEnabled)
        it.writeInt(settings.startMoney)
        it.writeLong(settings.seed)
    }

    /**
     * Ricostruisce le impostazioni sul dispositivo che si e' unito: due giocatori umani,
     * carro locale il numero 1 (l'host comanda il numero 0).
     */
    fun decodeStart(payload: ByteArray, localPlayerIndex: Int, remoteName: String): GameSettings =
        decode(payload) {
            val rounds = it.readInt()
            val difficulty = it.readInt()
            val wind = it.readBoolean()
            val money = it.readInt()
            val seed = it.readLong()
            GameSettings(
                playerCount = 2,
                humanCount = 2,
                rounds = rounds,
                difficultyOrdinal = difficulty,
                windEnabled = wind,
                startMoney = money,
                seed = seed,
                localPlayerIndex = localPlayerIndex,
                isHost = localPlayerIndex == 0,
                remoteName = remoteName
            )
        }

    // ------------------------------------------------------------ TURN

    fun encodeTurn(action: TurnAction): ByteArray = encode {
        it.writeInt(action.playerIndex)
        it.writeFloat(action.x)
        it.writeFloat(action.fuel)
        it.writeFloat(action.angle)
        it.writeFloat(action.power)
        it.writeInt(action.weaponId)
    }

    fun decodeTurn(payload: ByteArray): TurnAction = decode(payload) {
        TurnAction(
            playerIndex = it.readInt(),
            x = it.readFloat(),
            fuel = it.readFloat(),
            angle = it.readFloat(),
            power = it.readFloat(),
            weaponId = it.readInt()
        )
    }

    // ------------------------------------------------------------ SHOP

    fun encodeTankState(state: TankState): ByteArray = encode { writeTank(it, state) }

    fun decodeTankState(payload: ByteArray): TankState = decode(payload) { readTank(it) }

    private fun writeTank(out: DataOutputStream, t: TankState) {
        out.writeInt(t.index)
        out.writeFloat(t.x)
        out.writeFloat(t.y)
        out.writeFloat(t.angle)
        out.writeFloat(t.power)
        out.writeFloat(t.health)
        out.writeFloat(t.shield)
        out.writeFloat(t.fuel)
        out.writeBoolean(t.alive)
        out.writeInt(t.money)
        out.writeInt(t.score)
        out.writeInt(t.roundsWon)
        out.writeInt(t.selectedWeaponId)
        out.writeInt(t.inventory.size)
        for ((weaponId, count) in t.inventory.entries.sortedBy { it.key }) {
            out.writeInt(weaponId)
            out.writeInt(count)
        }
    }

    private fun readTank(input: DataInputStream): TankState {
        val index = input.readInt()
        val x = input.readFloat()
        val y = input.readFloat()
        val angle = input.readFloat()
        val power = input.readFloat()
        val health = input.readFloat()
        val shield = input.readFloat()
        val fuel = input.readFloat()
        val alive = input.readBoolean()
        val money = input.readInt()
        val score = input.readInt()
        val roundsWon = input.readInt()
        val weaponId = input.readInt()
        val size = input.readInt()
        require(size in 0..64) { "inventario non valido: $size" }
        val inventory = HashMap<Int, Int>(size)
        repeat(size) { inventory[input.readInt()] = input.readInt() }
        return TankState(
            index, x, y, angle, power, health, shield, fuel, alive,
            money, score, roundsWon, weaponId, inventory
        )
    }

    // ------------------------------------------------------------ SNAPSHOT

    fun encodeSnapshot(snapshot: WorldSnapshot): ByteArray = encode { out ->
        out.writeInt(snapshot.round)
        out.writeInt(snapshot.currentIndex)
        out.writeInt(snapshot.turnCounter)
        out.writeInt(snapshot.firstPlayerOfRound)
        out.writeFloat(snapshot.wind)
        out.writeInt(snapshot.surface.size)
        for (v in snapshot.surface) out.writeFloat(v)
        out.writeInt(snapshot.tanks.size)
        for (t in snapshot.tanks) writeTank(out, t)
    }

    fun decodeSnapshot(payload: ByteArray): WorldSnapshot = decode(payload) { input ->
        val round = input.readInt()
        val currentIndex = input.readInt()
        val turnCounter = input.readInt()
        val firstPlayer = input.readInt()
        val wind = input.readFloat()
        val columns = input.readInt()
        require(columns in 0..8192) { "terreno non valido: $columns" }
        val surface = FloatArray(columns) { input.readFloat() }
        val tankCount = input.readInt()
        require(tankCount in 0..8) { "numero di carri non valido: $tankCount" }
        val tanks = ArrayList<TankState>(tankCount)
        repeat(tankCount) { tanks.add(readTank(input)) }
        WorldSnapshot(round, currentIndex, turnCounter, firstPlayer, wind, surface, tanks)
    }
}
