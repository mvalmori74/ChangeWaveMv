package com.changewave.scorch.game

import android.graphics.Color

enum class WeaponType {
    /** Esplode all'impatto. */
    NORMAL,

    /** Deposita terra invece di scavare. */
    DIRT,

    /** Tocca terra e rotola in discesa fino al primo avvallamento. */
    ROLLER,

    /** Si conficca nel terreno scavando un tunnel, poi esplode. */
    DIGGER,

    /** All'impatto sparge N sub-munizioni. */
    CLUSTER,

    /** Si divide in N testate al culmine della parabola. */
    MIRV
}

data class Weapon(
    val id: Int,
    val name: String,
    val type: WeaponType,
    val radius: Float,
    val damage: Float,
    val cost: Int,
    val packSize: Int,
    val color: Int,
    val shellRadius: Float = 4f,
    val childCount: Int = 0,
    val child: Weapon? = null,
    val unlimited: Boolean = false,
    val buyable: Boolean = true
) {
    val displayCost: String
        get() = if (cost == 0) "-" else "$" + cost.toString()
}

object Weapons {

    // Sub-munizioni (non acquistabili singolarmente).
    private val FUNKY_CHILD = Weapon(
        id = 100, name = "Frammento", type = WeaponType.NORMAL,
        radius = 52f, damage = 22f, cost = 0, packSize = 0,
        color = Color.rgb(255, 150, 60), shellRadius = 3f, buyable = false
    )

    private val MIRV_CHILD = Weapon(
        id = 101, name = "Testata", type = WeaponType.NORMAL,
        radius = 62f, damage = 32f, cost = 0, packSize = 0,
        color = Color.rgb(180, 220, 255), shellRadius = 3.5f, buyable = false
    )

    val BABY_MISSILE = Weapon(
        id = 0, name = "Missile Baby", type = WeaponType.NORMAL,
        radius = 46f, damage = 25f, cost = 0, packSize = 0,
        color = Color.rgb(255, 235, 170), unlimited = true, buyable = false
    )

    val MISSILE = Weapon(
        id = 1, name = "Missile", type = WeaponType.NORMAL,
        radius = 72f, damage = 45f, cost = 1_000, packSize = 5,
        color = Color.rgb(255, 200, 120), shellRadius = 4.5f
    )

    val BABY_NUKE = Weapon(
        id = 2, name = "Baby Nuke", type = WeaponType.NORMAL,
        radius = 112f, damage = 70f, cost = 3_500, packSize = 3,
        color = Color.rgb(190, 255, 190), shellRadius = 5f
    )

    val NUKE = Weapon(
        id = 3, name = "Nuke", type = WeaponType.NORMAL,
        radius = 185f, damage = 115f, cost = 9_000, packSize = 2,
        color = Color.rgb(150, 255, 150), shellRadius = 6f
    )

    val FUNKY_BOMB = Weapon(
        id = 4, name = "Funky Bomb", type = WeaponType.CLUSTER,
        radius = 55f, damage = 20f, cost = 4_000, packSize = 3,
        color = Color.rgb(255, 120, 220), shellRadius = 5f,
        childCount = 7, child = FUNKY_CHILD
    )

    val MIRV = Weapon(
        id = 5, name = "MIRV", type = WeaponType.MIRV,
        radius = 40f, damage = 0f, cost = 6_000, packSize = 2,
        color = Color.rgb(200, 230, 255), shellRadius = 5f,
        childCount = 5, child = MIRV_CHILD
    )

    val ROLLER = Weapon(
        id = 6, name = "Roller", type = WeaponType.ROLLER,
        radius = 82f, damage = 50f, cost = 3_000, packSize = 5,
        color = Color.rgb(160, 220, 255), shellRadius = 5f
    )

    val DIGGER = Weapon(
        id = 7, name = "Digger", type = WeaponType.DIGGER,
        radius = 92f, damage = 58f, cost = 2_500, packSize = 5,
        color = Color.rgb(200, 160, 110), shellRadius = 5f
    )

    val DIRT_BALL = Weapon(
        id = 8, name = "Palla di Terra", type = WeaponType.DIRT,
        radius = 95f, damage = 0f, cost = 2_000, packSize = 5,
        color = Color.rgb(150, 110, 70), shellRadius = 5f
    )

    /** Ordine di comparsa nell'inventario e nel negozio. */
    val ALL: List<Weapon> = listOf(
        BABY_MISSILE, MISSILE, BABY_NUKE, NUKE,
        FUNKY_BOMB, MIRV, ROLLER, DIGGER, DIRT_BALL
    )

    val PURCHASABLE: List<Weapon> = ALL.filter { it.buyable }

    fun byId(id: Int): Weapon = ALL.firstOrNull { it.id == id } ?: BABY_MISSILE

    /** Potenza "percepita" di un'arma: usata dall'IA per scegliere cosa sparare. */
    fun punch(w: Weapon): Float = when (w.type) {
        WeaponType.MIRV -> (w.child?.damage ?: 0f) * w.childCount * 0.6f
        WeaponType.CLUSTER -> (w.child?.damage ?: 0f) * w.childCount * 0.5f
        WeaponType.DIRT -> 0f
        else -> w.damage
    }
}

/** Oggetti non-arma acquistabili nel negozio. */
enum class ShopItem(val label: String, val cost: Int, val amount: Int) {
    SHIELD("Scudo (+60)", 5_000, 60),
    REPAIR("Kit riparazione (+50 HP)", 4_000, 50),
    FUEL("Carburante (+40 movimento)", 1_500, 40)
}
