package com.changewave.dungeon.model

import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.Condition
import com.changewave.dungeon.rules.DamageType
import com.changewave.dungeon.rules.DiceExpr

/**
 * Bestiario derivato dallo SRD 5.1 (Open Game License), con le statistiche
 * originali dei mostri: CA, dadi vita, velocita', caratteristiche, attacchi.
 */
object Bestiary {

    val GIANT_RAT = MonsterSpecies(
        id = "giant_rat", name = "Ratto gigante", glyph = 'r', challengeRating = 0.125,
        armorClass = 12, hitDice = DiceExpr(2, 6), speed = 30,
        abilities = AbilityScores(7, 15, 11, 2, 10, 4),
        attacks = listOf(MonsterAttack("morso", 4, DiceExpr(1, 4, 2), DamageType.PIERCING)),
        ai = AiProfile.MELEE, minDepth = 1, maxDepth = 4, spawnWeight = 14,
    )

    val KOBOLD = MonsterSpecies(
        id = "kobold", name = "Coboldo", glyph = 'k', challengeRating = 0.125,
        armorClass = 12, hitDice = DiceExpr(2, 6), speed = 30,
        abilities = AbilityScores(7, 15, 9, 8, 7, 8),
        attacks = listOf(
            MonsterAttack("pugnale", 4, DiceExpr(1, 4, 2), DamageType.PIERCING),
            MonsterAttack("fionda", 4, DiceExpr(1, 4, 2), DamageType.BLUDGEONING, range = 6),
        ),
        ai = AiProfile.COWARD, minDepth = 1, maxDepth = 5, spawnWeight = 12,
    )

    val GOBLIN = MonsterSpecies(
        id = "goblin", name = "Goblin", glyph = 'g', challengeRating = 0.25,
        armorClass = 15, hitDice = DiceExpr(2, 6), speed = 30,
        abilities = AbilityScores(8, 14, 10, 10, 8, 8),
        attacks = listOf(
            MonsterAttack("scimitarra", 4, DiceExpr(1, 6, 2), DamageType.SLASHING),
            MonsterAttack("arco corto", 4, DiceExpr(1, 6, 2), DamageType.PIERCING, range = 12),
        ),
        ai = AiProfile.COWARD, minDepth = 1, maxDepth = 6, spawnWeight = 16,
    )

    val SKELETON = MonsterSpecies(
        id = "skeleton", name = "Scheletro", glyph = 's', challengeRating = 0.25,
        armorClass = 13, hitDice = DiceExpr(2, 8, 4), speed = 30,
        abilities = AbilityScores(10, 14, 15, 6, 8, 5),
        attacks = listOf(MonsterAttack("spada corta", 4, DiceExpr(1, 6, 2), DamageType.PIERCING)),
        ai = AiProfile.MINDLESS,
        immunities = setOf(DamageType.POISON),
        resistances = setOf(DamageType.PIERCING),
        minDepth = 2, maxDepth = 8, spawnWeight = 14,
    )

    val ZOMBIE = MonsterSpecies(
        id = "zombie", name = "Zombi", glyph = 'z', challengeRating = 0.25,
        armorClass = 8, hitDice = DiceExpr(3, 8, 9), speed = 20,
        abilities = AbilityScores(13, 6, 16, 3, 6, 5),
        attacks = listOf(MonsterAttack("schianto", 3, DiceExpr(1, 6, 1), DamageType.BLUDGEONING)),
        ai = AiProfile.MINDLESS,
        saveProficiencies = setOf(Ability.WISDOM),
        immunities = setOf(DamageType.POISON),
        minDepth = 2, maxDepth = 8, spawnWeight = 12,
    )

    val ORC = MonsterSpecies(
        id = "orc", name = "Orco", glyph = 'o', challengeRating = 0.5,
        armorClass = 13, hitDice = DiceExpr(2, 8, 6), speed = 30,
        abilities = AbilityScores(16, 12, 16, 7, 11, 10),
        attacks = listOf(MonsterAttack("ascia bipenne", 5, DiceExpr(1, 12, 3), DamageType.SLASHING)),
        ai = AiProfile.MELEE, minDepth = 3, maxDepth = 9, spawnWeight = 14,
    )

    val HOBGOBLIN = MonsterSpecies(
        id = "hobgoblin", name = "Hobgoblin", glyph = 'h', challengeRating = 0.5,
        armorClass = 18, hitDice = DiceExpr(2, 8, 2), speed = 30,
        abilities = AbilityScores(13, 12, 12, 10, 10, 9),
        attacks = listOf(
            MonsterAttack("spada lunga", 3, DiceExpr(1, 8, 1), DamageType.SLASHING),
            MonsterAttack("arco lungo", 3, DiceExpr(1, 8, 1), DamageType.PIERCING, range = 15),
        ),
        ai = AiProfile.RANGED, minDepth = 4, maxDepth = 10, spawnWeight = 10,
    )

    val GIANT_SPIDER = MonsterSpecies(
        id = "giant_spider", name = "Ragno gigante", glyph = 'x', challengeRating = 1.0,
        armorClass = 14, hitDice = DiceExpr(4, 10, 4), speed = 30,
        abilities = AbilityScores(14, 16, 12, 2, 11, 4),
        attacks = listOf(
            MonsterAttack(
                "morso", 5, DiceExpr(1, 8, 3), DamageType.PIERCING,
                onHit = OnHitEffect(Ability.CONSTITUTION, 11, Condition.POISONED, 4, DiceExpr(2, 8), DamageType.POISON),
            ),
        ),
        ai = AiProfile.AMBUSHER, minDepth = 4, maxDepth = 9, spawnWeight = 9,
    )

    val GHOUL = MonsterSpecies(
        id = "ghoul", name = "Ghoul", glyph = 'G', challengeRating = 1.0,
        armorClass = 12, hitDice = DiceExpr(5, 8), speed = 30,
        abilities = AbilityScores(13, 15, 10, 7, 10, 6),
        attacks = listOf(
            MonsterAttack(
                "artigli", 4, DiceExpr(2, 4, 2), DamageType.SLASHING,
                onHit = OnHitEffect(Ability.CONSTITUTION, 10, Condition.PARALYZED, 2),
            ),
        ),
        ai = AiProfile.MINDLESS,
        immunities = setOf(DamageType.POISON),
        minDepth = 5, maxDepth = 10, spawnWeight = 10,
    )

    val DIRE_WOLF = MonsterSpecies(
        id = "dire_wolf", name = "Lupo crudele", glyph = 'w', challengeRating = 1.0,
        armorClass = 14, hitDice = DiceExpr(5, 10, 10), speed = 50,
        abilities = AbilityScores(17, 15, 15, 3, 12, 7),
        attacks = listOf(
            MonsterAttack(
                "morso", 5, DiceExpr(2, 6, 3), DamageType.PIERCING,
                onHit = OnHitEffect(Ability.STRENGTH, 13, Condition.PRONE, 2),
            ),
        ),
        ai = AiProfile.MELEE, minDepth = 4, maxDepth = 9, spawnWeight = 10,
    )

    val BUGBEAR = MonsterSpecies(
        id = "bugbear", name = "Bugbear", glyph = 'b', challengeRating = 1.0,
        armorClass = 16, hitDice = DiceExpr(5, 8, 5), speed = 30,
        abilities = AbilityScores(15, 14, 13, 8, 11, 9),
        attacks = listOf(MonsterAttack("mazzafrusto", 4, DiceExpr(2, 8, 2), DamageType.PIERCING)),
        ai = AiProfile.AMBUSHER, minDepth = 5, maxDepth = 10, spawnWeight = 9,
    )

    val OGRE = MonsterSpecies(
        id = "ogre", name = "Ogre", glyph = 'O', challengeRating = 2.0,
        armorClass = 11, hitDice = DiceExpr(7, 10, 21), speed = 40,
        abilities = AbilityScores(19, 8, 16, 5, 7, 7),
        attacks = listOf(MonsterAttack("randello", 6, DiceExpr(2, 8, 4), DamageType.BLUDGEONING)),
        ai = AiProfile.MELEE, minDepth = 6, maxDepth = 10, spawnWeight = 8,
    )

    val OWLBEAR = MonsterSpecies(
        id = "owlbear", name = "Orsogufo", glyph = 'B', challengeRating = 3.0,
        armorClass = 13, hitDice = DiceExpr(7, 10, 21), speed = 40,
        abilities = AbilityScores(20, 12, 17, 3, 12, 7),
        attacks = listOf(MonsterAttack("artigli", 7, DiceExpr(2, 8, 5), DamageType.SLASHING)),
        ai = AiProfile.MELEE, minDepth = 7, maxDepth = 10, spawnWeight = 7,
    )

    val WIGHT = MonsterSpecies(
        id = "wight", name = "Wight", glyph = 'W', challengeRating = 3.0,
        armorClass = 14, hitDice = DiceExpr(6, 8, 18), speed = 30,
        abilities = AbilityScores(15, 14, 16, 10, 13, 15),
        attacks = listOf(
            MonsterAttack(
                "spada lunga", 4, DiceExpr(1, 8, 2), DamageType.SLASHING,
                onHit = OnHitEffect(Ability.CONSTITUTION, 13, Condition.FRIGHTENED, 3, DiceExpr(1, 6), DamageType.NECROTIC),
            ),
            MonsterAttack("arco lungo", 4, DiceExpr(1, 8, 2), DamageType.PIERCING, range = 15),
        ),
        ai = AiProfile.RANGED,
        resistances = setOf(DamageType.NECROTIC, DamageType.SLASHING),
        immunities = setOf(DamageType.POISON),
        minDepth = 8, maxDepth = 10, spawnWeight = 7,
    )

    val TROLL = MonsterSpecies(
        id = "troll", name = "Troll", glyph = 'T', challengeRating = 5.0,
        armorClass = 15, hitDice = DiceExpr(8, 10, 40), speed = 30,
        abilities = AbilityScores(18, 13, 20, 7, 9, 7),
        attacks = listOf(MonsterAttack("artigli", 7, DiceExpr(2, 6, 4), DamageType.SLASHING)),
        ai = AiProfile.MELEE, minDepth = 9, maxDepth = 10, spawnWeight = 5,
    )

    /** Boss finale del livello 10: statistiche da "morte" SRD, alleggerite. */
    val DEATH_LORD = MonsterSpecies(
        id = "death_lord", name = "Malgrim, Signore dei Sepolcri", glyph = 'M', challengeRating = 6.0,
        armorClass = 17, hitDice = DiceExpr(12, 10, 36), speed = 30,
        abilities = AbilityScores(18, 16, 18, 16, 16, 18),
        attacks = listOf(
            MonsterAttack(
                "lama tombale", 8, DiceExpr(2, 8, 4), DamageType.NECROTIC,
                onHit = OnHitEffect(Ability.CONSTITUTION, 15, Condition.FRIGHTENED, 4, DiceExpr(2, 6), DamageType.NECROTIC),
            ),
            MonsterAttack(
                "raggio necrotico", 8, DiceExpr(3, 6), DamageType.NECROTIC, range = 10,
            ),
        ),
        ai = AiProfile.MELEE,
        saveProficiencies = setOf(Ability.CONSTITUTION, Ability.WISDOM),
        resistances = setOf(DamageType.NECROTIC, DamageType.COLD, DamageType.SLASHING),
        immunities = setOf(DamageType.POISON),
        minDepth = 10, maxDepth = 10, spawnWeight = 0, boss = true,
    )

    val all: List<MonsterSpecies> = listOf(
        GIANT_RAT, KOBOLD, GOBLIN, SKELETON, ZOMBIE, ORC, HOBGOBLIN, GIANT_SPIDER,
        GHOUL, DIRE_WOLF, BUGBEAR, OGRE, OWLBEAR, WIGHT, TROLL, DEATH_LORD,
    )

    fun byId(id: String): MonsterSpecies? = all.firstOrNull { it.id == id }

    /** Specie selezionabili a una data profondita' (boss esclusi). */
    fun spawnTable(depth: Int): List<MonsterSpecies> =
        all.filter { !it.boss && it.spawnWeight > 0 && depth >= it.minDepth && depth <= it.maxDepth }

    /** Tabella PX per GS del DMG. */
    fun xpForChallengeRating(cr: Double): Int = when {
        cr <= 0.0 -> 10
        cr <= 0.125 -> 25
        cr <= 0.25 -> 50
        cr <= 0.5 -> 100
        cr <= 1.0 -> 200
        cr <= 2.0 -> 450
        cr <= 3.0 -> 700
        cr <= 4.0 -> 1100
        cr <= 5.0 -> 1800
        cr <= 6.0 -> 2300
        cr <= 7.0 -> 2900
        cr <= 8.0 -> 3900
        else -> 5000
    }
}
