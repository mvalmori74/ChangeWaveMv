package com.changewave.scorch.game

import kotlin.random.Random

/** Acquisti automatici dei carri controllati dall'IA fra un round e l'altro. */
object AiShopper {

    fun spend(tank: Tank, difficulty: Difficulty, rnd: Random) {
        // scudo se e' stato preso di mira nel round precedente
        if (tank.shield < 40f && tank.money >= ShopItem.SHIELD.cost && rnd.nextFloat() < 0.6f) {
            tank.money -= ShopItem.SHIELD.cost
            tank.shield = (tank.shield + ShopItem.SHIELD.amount).coerceAtMost(200f)
        }

        val taste = when (difficulty) {
            Difficulty.ROOKIE -> 0.45f
            Difficulty.VETERAN -> 0.7f
            Difficulty.CYBORG -> 0.95f
        }

        var guard = 0
        while (guard < 12) {
            guard++
            val affordable = Weapons.PURCHASABLE.filter { it.cost <= tank.money && it.type != WeaponType.DIRT }
            if (affordable.isEmpty()) break

            val pick = if (rnd.nextFloat() < taste) {
                affordable.maxByOrNull { Weapons.punch(it) / it.cost.coerceAtLeast(1).toFloat() * Weapons.punch(it) }
            } else {
                affordable[rnd.nextInt(affordable.size)]
            } ?: break

            // tiene sempre un fondo cassa
            if (tank.money - pick.cost < 500) break
            tank.money -= pick.cost
            tank.addAmmo(pick, pick.packSize)
        }

        // qualche palla di terra come difesa passiva
        if (tank.money >= Weapons.DIRT_BALL.cost && rnd.nextFloat() < 0.35f) {
            tank.money -= Weapons.DIRT_BALL.cost
            tank.addAmmo(Weapons.DIRT_BALL, Weapons.DIRT_BALL.packSize)
        }
    }
}
