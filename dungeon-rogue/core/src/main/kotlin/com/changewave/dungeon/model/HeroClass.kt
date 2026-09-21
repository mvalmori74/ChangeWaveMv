package com.changewave.dungeon.model

import com.changewave.dungeon.rules.Ability

/**
 * Le quattro classi giocabili (SRD 5.1), con le sole capacita' che hanno senso
 * in un roguelike a turni: dado vita, competenze, capacita' di classe, incantesimi.
 */
enum class HeroClass(
    val italian: String,
    val hitDie: Int,
    val primaryAbility: Ability,
    val savingThrowProficiencies: Set<Ability>,
    val spellcastingAbility: Ability? = null,
    val knownSpells: List<SpellId> = emptyList(),
    val description: String,
) {
    FIGHTER(
        italian = "Guerriero",
        hitDie = 10,
        primaryAbility = Ability.STRENGTH,
        savingThrowProficiencies = setOf(Ability.STRENGTH, Ability.CONSTITUTION),
        description = "PF alti, tutte le armi e armature, Recuperare Energie per curarsi in combattimento.",
    ),
    ROGUE(
        italian = "Ladro",
        hitDie = 8,
        primaryAbility = Ability.DEXTERITY,
        savingThrowProficiencies = setOf(Ability.DEXTERITY, Ability.INTELLIGENCE),
        description = "Attacco Furtivo devastante contro bersagli colti di sorpresa, armi agili e armature leggere.",
    ),
    CLERIC(
        italian = "Chierico",
        hitDie = 8,
        primaryAbility = Ability.WISDOM,
        savingThrowProficiencies = setOf(Ability.WISDOM, Ability.CHARISMA),
        spellcastingAbility = Ability.WISDOM,
        knownSpells = listOf(SpellId.SACRED_FLAME, SpellId.CURE_WOUNDS, SpellId.BLESS),
        description = "Cura, benedizioni e danni radiosi; armature medie e scudo.",
    ),
    WIZARD(
        italian = "Mago",
        hitDie = 6,
        primaryAbility = Ability.INTELLIGENCE,
        savingThrowProficiencies = setOf(Ability.INTELLIGENCE, Ability.WISDOM),
        spellcastingAbility = Ability.INTELLIGENCE,
        knownSpells = listOf(SpellId.FIRE_BOLT, SpellId.MAGIC_MISSILE, SpellId.BURNING_HANDS),
        description = "PF fragili ma il miglior danno a distanza; nessuna armatura.",
    );

    val isSpellcaster: Boolean get() = spellcastingAbility != null

    fun isProficientWithWeapon(weapon: Weapon): Boolean = when (this) {
        FIGHTER -> true
        ROGUE -> weapon.category == WeaponCategory.SIMPLE || weapon.finesse || weapon.ranged
        CLERIC -> weapon.category == WeaponCategory.SIMPLE
        WIZARD -> weapon.id in setOf("dagger", "quarterstaff", "light_crossbow", "unarmed")
    }

    fun isProficientWithArmor(armor: Armor): Boolean = when (this) {
        FIGHTER -> true
        ROGUE -> armor.category == ArmorCategory.LIGHT
        CLERIC -> armor.category != ArmorCategory.HEAVY
        WIZARD -> false
    }

    val usesShield: Boolean get() = this == FIGHTER || this == CLERIC

    /** Equipaggiamento iniziale, come i pacchetti del PHB. */
    fun startingEquipment(): StartingEquipment = when (this) {
        FIGHTER -> StartingEquipment(
            weapon = ItemCatalog.LONGSWORD,
            armor = ItemCatalog.CHAIN_MAIL,
            shield = Shield(),
            pack = listOf(ItemCatalog.POTION_HEALING, ItemCatalog.POTION_HEALING),
        )
        ROGUE -> StartingEquipment(
            weapon = ItemCatalog.SHORTSWORD,
            armor = ItemCatalog.LEATHER,
            shield = null,
            pack = listOf(ItemCatalog.DAGGER, ItemCatalog.POTION_HEALING, ItemCatalog.SHORTBOW),
        )
        CLERIC -> StartingEquipment(
            weapon = ItemCatalog.MACE,
            armor = ItemCatalog.SCALE_MAIL,
            shield = Shield(),
            pack = listOf(ItemCatalog.POTION_HEALING),
        )
        WIZARD -> StartingEquipment(
            weapon = ItemCatalog.QUARTERSTAFF,
            armor = null,
            shield = null,
            pack = listOf(ItemCatalog.DAGGER, ItemCatalog.POTION_HEALING, ItemCatalog.SCROLL_MAGIC_MISSILE),
        )
    }
}

data class StartingEquipment(
    val weapon: Weapon,
    val armor: Armor?,
    val shield: Shield?,
    val pack: List<Item>,
)
