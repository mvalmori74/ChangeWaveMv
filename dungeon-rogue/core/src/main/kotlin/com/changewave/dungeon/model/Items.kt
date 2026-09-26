package com.changewave.dungeon.model

import com.changewave.dungeon.rules.DamageType
import com.changewave.dungeon.rules.DiceExpr
import kotlinx.serialization.Serializable

enum class WeaponCategory { SIMPLE, MARTIAL }

enum class ArmorCategory(val italian: String) {
    LIGHT("leggera"), MEDIUM("media"), HEAVY("pesante")
}

/** Oggetti trasportabili. Gerarchia sigillata: la serializzazione polimorfa e' automatica. */
@Serializable
sealed class Item {
    abstract val id: String
    abstract val name: String
    /** Valore in monete d'oro, usato per il punteggio finale. */
    abstract val value: Int
    /** Glifo usato dal renderer (ASCII-compatibile, come da tradizione roguelike). */
    abstract val glyph: Char
}

@Serializable
data class Weapon(
    override val id: String,
    override val name: String,
    val category: WeaponCategory,
    val damage: DiceExpr,
    val damageType: DamageType,
    val finesse: Boolean = false,
    val twoHanded: Boolean = false,
    val ranged: Boolean = false,
    /** Gittata in caselle (1 casella = 5 piedi). 1 = mischia. */
    val range: Int = 1,
    val magicBonus: Int = 0,
    override val value: Int = 10,
) : Item() {
    override val glyph: Char get() = if (ranged) '}' else ')'
    val displayName: String get() = if (magicBonus > 0) "$name +$magicBonus" else name
}

@Serializable
data class Armor(
    override val id: String,
    override val name: String,
    val category: ArmorCategory,
    val baseArmorClass: Int,
    /** Limite al modificatore di Destrezza applicabile (null = nessun limite). */
    val dexterityCap: Int?,
    val minimumStrength: Int = 0,
    val stealthDisadvantage: Boolean = false,
    val magicBonus: Int = 0,
    override val value: Int = 20,
) : Item() {
    override val glyph: Char get() = '['
    val displayName: String get() = if (magicBonus > 0) "$name +$magicBonus" else name
}

@Serializable
data class Shield(
    override val id: String = "shield",
    override val name: String = "Scudo",
    val armorClassBonus: Int = 2,
    val magicBonus: Int = 0,
    override val value: Int = 10,
) : Item() {
    override val glyph: Char get() = '('
}

enum class PotionEffect { HEALING, GREATER_HEALING, STRENGTH, ANTIDOTE }

@Serializable
data class Potion(
    override val id: String,
    override val name: String,
    val effect: PotionEffect,
    val amount: DiceExpr = DiceExpr.NONE,
    override val value: Int = 50,
) : Item() {
    override val glyph: Char get() = '!'
}

@Serializable
data class Scroll(
    override val id: String,
    override val name: String,
    val spell: SpellId,
    override val value: Int = 75,
) : Item() {
    override val glyph: Char get() = '?'
}

@Serializable
data class Treasure(
    override val id: String,
    override val name: String,
    override val value: Int,
) : Item() {
    override val glyph: Char get() = '$'
}

/** Catalogo SRD 5.1 (sottoinsieme). I valori sono quelli della tabella equipaggiamento PHB. */
object ItemCatalog {

    val DAGGER = Weapon("dagger", "Pugnale", WeaponCategory.SIMPLE, DiceExpr(1, 4), DamageType.PIERCING, finesse = true, value = 2)
    val QUARTERSTAFF = Weapon("quarterstaff", "Bastone ferrato", WeaponCategory.SIMPLE, DiceExpr(1, 6), DamageType.BLUDGEONING, value = 1)
    val MACE = Weapon("mace", "Mazza", WeaponCategory.SIMPLE, DiceExpr(1, 6), DamageType.BLUDGEONING, value = 5)
    val SPEAR = Weapon("spear", "Lancia", WeaponCategory.SIMPLE, DiceExpr(1, 6), DamageType.PIERCING, value = 1)
    val SHORTSWORD = Weapon("shortsword", "Spada corta", WeaponCategory.MARTIAL, DiceExpr(1, 6), DamageType.PIERCING, finesse = true, value = 10)
    val LONGSWORD = Weapon("longsword", "Spada lunga", WeaponCategory.MARTIAL, DiceExpr(1, 8), DamageType.SLASHING, value = 15)
    val BATTLEAXE = Weapon("battleaxe", "Ascia da battaglia", WeaponCategory.MARTIAL, DiceExpr(1, 8), DamageType.SLASHING, value = 10)
    val GREATAXE = Weapon("greataxe", "Ascia bipenne", WeaponCategory.MARTIAL, DiceExpr(1, 12), DamageType.SLASHING, twoHanded = true, value = 30)
    val GREATSWORD = Weapon("greatsword", "Spadone", WeaponCategory.MARTIAL, DiceExpr(2, 6), DamageType.SLASHING, twoHanded = true, value = 50)
    val RAPIER = Weapon("rapier", "Stocco", WeaponCategory.MARTIAL, DiceExpr(1, 8), DamageType.PIERCING, finesse = true, value = 25)
    val SHORTBOW = Weapon("shortbow", "Arco corto", WeaponCategory.SIMPLE, DiceExpr(1, 6), DamageType.PIERCING, twoHanded = true, ranged = true, range = 12, value = 25)
    val LIGHT_CROSSBOW = Weapon("light_crossbow", "Balestra leggera", WeaponCategory.SIMPLE, DiceExpr(1, 8), DamageType.PIERCING, twoHanded = true, ranged = true, range = 16, value = 25)
    val UNARMED = Weapon("unarmed", "Pugni", WeaponCategory.SIMPLE, DiceExpr(0, 1, 1), DamageType.BLUDGEONING, value = 0)

    val PADDED = Armor("padded", "Armatura imbottita", ArmorCategory.LIGHT, 11, null, stealthDisadvantage = true, value = 5)
    val LEATHER = Armor("leather", "Armatura di cuoio", ArmorCategory.LIGHT, 11, null, value = 10)
    val STUDDED_LEATHER = Armor("studded_leather", "Cuoio borchiato", ArmorCategory.LIGHT, 12, null, value = 45)
    val HIDE = Armor("hide", "Armatura di pelle", ArmorCategory.MEDIUM, 12, 2, value = 10)
    val CHAIN_SHIRT = Armor("chain_shirt", "Camicia di maglia", ArmorCategory.MEDIUM, 13, 2, value = 50)
    val SCALE_MAIL = Armor("scale_mail", "Corazza a scaglie", ArmorCategory.MEDIUM, 14, 2, stealthDisadvantage = true, value = 50)
    val CHAIN_MAIL = Armor("chain_mail", "Cotta di maglia", ArmorCategory.HEAVY, 16, 0, minimumStrength = 13, stealthDisadvantage = true, value = 75)
    val PLATE = Armor("plate", "Armatura a piastre", ArmorCategory.HEAVY, 18, 0, minimumStrength = 15, stealthDisadvantage = true, value = 1500)

    val POTION_HEALING = Potion("potion_healing", "Pozione di guarigione", PotionEffect.HEALING, DiceExpr(2, 4, 2), value = 50)
    val POTION_GREATER_HEALING = Potion("potion_greater_healing", "Pozione di guarigione superiore", PotionEffect.GREATER_HEALING, DiceExpr(4, 4, 4), value = 150)
    val POTION_STRENGTH = Potion("potion_strength", "Elisir di forza", PotionEffect.STRENGTH, value = 120)
    val POTION_ANTIDOTE = Potion("potion_antidote", "Antidoto", PotionEffect.ANTIDOTE, value = 40)

    val SCROLL_MAGIC_MISSILE = Scroll("scroll_magic_missile", "Pergamena di dardo incantato", SpellId.MAGIC_MISSILE)
    val SCROLL_BURNING_HANDS = Scroll("scroll_burning_hands", "Pergamena di mani brucianti", SpellId.BURNING_HANDS)
    val SCROLL_CURE_WOUNDS = Scroll("scroll_cure_wounds", "Pergamena di cura ferite", SpellId.CURE_WOUNDS)
    val SCROLL_BLESS = Scroll("scroll_bless", "Pergamena di benedizione", SpellId.BLESS)

    val weapons: List<Weapon> = listOf(
        DAGGER, QUARTERSTAFF, MACE, SPEAR, SHORTSWORD, LONGSWORD, BATTLEAXE,
        GREATAXE, GREATSWORD, RAPIER, SHORTBOW, LIGHT_CROSSBOW,
    )
    val armors: List<Armor> = listOf(PADDED, LEATHER, STUDDED_LEATHER, HIDE, CHAIN_SHIRT, SCALE_MAIL, CHAIN_MAIL, PLATE)
    val potions: List<Potion> = listOf(POTION_HEALING, POTION_GREATER_HEALING, POTION_STRENGTH, POTION_ANTIDOTE)
    val scrolls: List<Scroll> = listOf(SCROLL_MAGIC_MISSILE, SCROLL_BURNING_HANDS, SCROLL_CURE_WOUNDS, SCROLL_BLESS)

    fun byId(id: String): Item? =
        (weapons + armors + potions + scrolls + listOf(Shield())).firstOrNull { it.id == id }
}
