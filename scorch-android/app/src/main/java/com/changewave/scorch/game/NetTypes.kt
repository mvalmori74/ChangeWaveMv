package com.changewave.scorch.game

/** Mossa completa di un giocatore: posizione finale, mira e arma scelta. */
data class TurnAction(
    val playerIndex: Int,
    val x: Float,
    val fuel: Float,
    val angle: Float,
    val power: Float,
    val weaponId: Int
)

/** Stato di un carro scambiato dopo il negozio o dentro uno snapshot. */
data class TankState(
    val index: Int,
    val x: Float,
    val y: Float,
    val angle: Float,
    val power: Float,
    val health: Float,
    val shield: Float,
    val fuel: Float,
    val alive: Boolean,
    val money: Int,
    val score: Int,
    val roundsWon: Int,
    val selectedWeaponId: Int,
    val inventory: Map<Int, Int>
) {
    companion object {
        fun of(t: Tank): TankState = TankState(
            index = t.id,
            x = t.x,
            y = t.y,
            angle = t.angle,
            power = t.power,
            health = t.health,
            shield = t.shield,
            fuel = t.fuel,
            alive = t.alive,
            money = t.money,
            score = t.score,
            roundsWon = t.roundsWon,
            selectedWeaponId = t.selectedWeaponId,
            inventory = HashMap(t.inventory)
        )
    }

    fun applyTo(t: Tank) {
        t.x = x
        t.y = y
        t.angle = angle
        t.power = power
        t.health = health
        t.shield = shield
        t.fuel = fuel
        t.alive = alive
        t.money = money
        t.score = score
        t.roundsWon = roundsWon
        t.selectedWeaponId = selectedWeaponId
        t.inventory.clear()
        t.inventory.putAll(inventory)
        t.ensureValidWeapon()
    }

    /** Solo la parte che cambia nel negozio: inventario, denaro e riparazioni. */
    fun applyPurchasesTo(t: Tank) {
        t.money = money
        t.health = health
        t.shield = shield
        t.fuel = fuel
        t.selectedWeaponId = selectedWeaponId
        t.inventory.clear()
        t.inventory.putAll(inventory)
        t.ensureValidWeapon()
    }
}

/** Fotografia completa della partita, usata per riallineare i due dispositivi. */
data class WorldSnapshot(
    val round: Int,
    val currentIndex: Int,
    val turnCounter: Int,
    val firstPlayerOfRound: Int,
    val wind: Float,
    val surface: FloatArray,
    val tanks: List<TankState>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorldSnapshot) return false
        return round == other.round &&
            currentIndex == other.currentIndex &&
            turnCounter == other.turnCounter &&
            firstPlayerOfRound == other.firstPlayerOfRound &&
            wind == other.wind &&
            surface.contentEquals(other.surface) &&
            tanks == other.tanks
    }

    override fun hashCode(): Int {
        var result = round
        result = 31 * result + currentIndex
        result = 31 * result + turnCounter
        result = 31 * result + firstPlayerOfRound
        result = 31 * result + wind.hashCode()
        result = 31 * result + surface.contentHashCode()
        result = 31 * result + tanks.hashCode()
        return result
    }
}
