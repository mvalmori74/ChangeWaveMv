package com.changewave.dungeon.game

import com.changewave.dungeon.dungeon.DungeonConfig
import com.changewave.dungeon.dungeon.DungeonGenerator
import com.changewave.dungeon.dungeon.DungeonLevel
import com.changewave.dungeon.dungeon.FieldOfView
import com.changewave.dungeon.dungeon.Pos
import com.changewave.dungeon.dungeon.TileType
import com.changewave.dungeon.dungeon.Trap
import com.changewave.dungeon.dungeon.TrapKind
import com.changewave.dungeon.dungeon.placePlayerAtEntrance
import com.changewave.dungeon.dungeon.placePlayerAtExit
import com.changewave.dungeon.model.Actor
import com.changewave.dungeon.model.Armor
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.model.Item
import com.changewave.dungeon.model.Monster
import com.changewave.dungeon.model.PlayerCharacter
import com.changewave.dungeon.model.Potion
import com.changewave.dungeon.model.PotionEffect
import com.changewave.dungeon.model.Scroll
import com.changewave.dungeon.model.Shield
import com.changewave.dungeon.model.Spell
import com.changewave.dungeon.model.SpellBook
import com.changewave.dungeon.model.SpellDelivery
import com.changewave.dungeon.model.SpellId
import com.changewave.dungeon.model.Treasure
import com.changewave.dungeon.model.Weapon
import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.Combat
import com.changewave.dungeon.rules.Condition
import com.changewave.dungeon.rules.DamageType
import com.changewave.dungeon.rules.DiceExpr
import com.changewave.dungeon.rules.GameRandom
import com.changewave.dungeon.rules.proficiencyBonus
import com.changewave.dungeon.rules.rollD20
import kotlin.math.max

/**
 * Motore di gioco: applica i comandi del giocatore, fa agire i mostri e mantiene
 * la coerenza dello stato. E' completamente headless (nessuna dipendenza da
 * Android): la UI si limita a inviare [Command] e a leggere [GameState].
 *
 * Modello del tempo: punti azione. Ogni tick tutte le creature guadagnano
 * energia pari alla loro velocita'; agire costa [Actor.ENERGY_PER_ACTION].
 * Un lupo crudele (50 ft) agisce quindi ~1,67 volte per ogni azione del
 * personaggio (30 ft), uno zombi (20 ft) ~0,67.
 */
class GameEngine(
    val state: GameState,
    private val config: DungeonConfig = DungeonConfig(),
) {

    private val generator = DungeonGenerator(config)
    private val pendingMessages = mutableListOf<LogMessage>()

    val player: PlayerCharacter get() = state.player
    val level: DungeonLevel get() = state.currentLevel

    // --- API pubblica ---------------------------------------------------------

    fun execute(command: Command): TurnResult {
        pendingMessages.clear()
        if (state.isOver) {
            return TurnResult(accepted = false, timeAdvanced = false, messages = emptyList(), rejection = "La partita e' finita.")
        }
        if (!playerCanAct()) {
            return TurnResult(accepted = false, timeAdvanced = false, messages = emptyList(), rejection = "Non puoi agire.")
        }

        val outcome: ActionOutcome = when (command) {
            is Command.Move -> moveOrAttack(command.dx, command.dy)
            Command.Wait -> { searchForTraps(); ActionOutcome.spent() }
            Command.Descend -> descend()
            Command.Ascend -> ascend()
            Command.PickUp -> pickUp()
            is Command.UseItem -> useItem(command.inventoryIndex)
            is Command.DropItem -> dropItem(command.inventoryIndex)
            is Command.RangedAttack -> rangedAttack(command.targetId)
            is Command.Cast -> cast(command.spell, command.targetId, command.slotLevel)
            Command.SecondWind -> secondWind()
        }

        if (!outcome.spentTurn) {
            return TurnResult(accepted = false, timeAdvanced = false, messages = pendingMessages.toList(), rejection = outcome.rejection)
        }

        player.energy -= Actor.ENERGY_PER_ACTION
        endPlayerTurn()
        advanceWorld()
        recomputeVisibility()
        return TurnResult(accepted = true, timeAdvanced = true, messages = pendingMessages.toList())
    }

    /** Mostri attualmente visibili, ordinati per distanza: comodo per la UI e il bersagliamento. */
    fun visibleMonsters(): List<Monster> = level.monsters
        .filter { it.hitPoints > 0 && level.isVisible(it.x, it.y) }
        .sortedBy { it.distanceTo(player) }

    fun monsterById(id: Int): Monster? = level.monsters.firstOrNull { it.id == id && it.hitPoints > 0 }

    fun nearestVisibleMonster(): Monster? = visibleMonsters().firstOrNull()

    fun recomputeVisibility() {
        FieldOfView.compute(level, player.x, player.y, state.sightRadius)
    }

    // --- Azioni del giocatore -------------------------------------------------

    private data class ActionOutcome(val spentTurn: Boolean, val rejection: String? = null) {
        companion object {
            fun spent() = ActionOutcome(true)
            fun refused(reason: String) = ActionOutcome(false, reason)
        }
    }

    private fun moveOrAttack(dx: Int, dy: Int): ActionOutcome {
        if (dx == 0 && dy == 0) return ActionOutcome.refused("Direzione nulla.")
        val nx = player.x + dx
        val ny = player.y + dy
        val target = level.monsterAt(nx, ny)
        if (target != null) {
            meleeAttack(target)
            return ActionOutcome.spent()
        }
        val tile = level.tileAt(nx, ny)
        if (tile == TileType.DOOR_CLOSED) {
            level.setTile(nx, ny, TileType.DOOR_OPEN)
            log("Apri la porta.", MessageKind.INFO)
            return ActionOutcome.spent()
        }
        if (!tile.walkable) return ActionOutcome.refused("C'e' un muro.")
        // Niente diagonali che tagliano gli angoli.
        if (dx != 0 && dy != 0 && !level.isWalkable(player.x + dx, player.y) && !level.isWalkable(player.x, player.y + dy)) {
            return ActionOutcome.refused("Passaggio troppo stretto.")
        }
        player.x = nx
        player.y = ny
        onPlayerEnteredTile()
        return ActionOutcome.spent()
    }

    private fun meleeAttack(target: Monster) {
        val surprised = !target.alerted
        val outcome = Combat.playerAttack(player, target, state.rng, surprised = surprised)
        target.alerted = true
        log(outcome.describe(), if (outcome.critical) MessageKind.CRITICAL else MessageKind.COMBAT)
        if (target.hitPoints <= 0) onMonsterKilled(target)
    }

    private fun rangedAttack(targetId: Int): ActionOutcome {
        val weapon = player.weapon
        if (!weapon.ranged) return ActionOutcome.refused("${weapon.displayName} non e' un'arma a distanza.")
        val target = monsterById(targetId) ?: return ActionOutcome.refused("Bersaglio non valido.")
        val distance = player.distanceTo(target)
        if (distance > weapon.range) return ActionOutcome.refused("Bersaglio fuori gittata.")
        if (!FieldOfView.hasLineOfSight(level, player.x, player.y, target.x, target.y)) {
            return ActionOutcome.refused("Nessuna linea di tiro.")
        }
        val surprised = !target.alerted
        val outcome = Combat.playerAttack(player, target, state.rng, weapon, surprised)
        target.alerted = true
        log(outcome.describe(), if (outcome.critical) MessageKind.CRITICAL else MessageKind.COMBAT)
        if (target.hitPoints <= 0) onMonsterKilled(target)
        return ActionOutcome.spent()
    }

    private fun onMonsterKilled(monster: Monster) {
        state.monstersKilled++
        val xp = monster.species.experience
        val levels = player.gainExperience(xp, state.rng)
        log("${monster.name} e' sconfitto! +$xp PX", MessageKind.GOOD)
        if (levels > 0) {
            log("Sali al livello ${player.level}! PF massimi: ${player.hitPointMaximum}, competenza +${proficiencyBonus(player.level)}.", MessageKind.GOOD)
        }
        if (monster.species.boss) {
            state.status = GameStatus.VICTORY
            log("Malgrim si dissolve in cenere. Il dungeon e' libero: hai vinto!", MessageKind.CRITICAL)
        }
        // Il bottino del mostro cade a terra (probabilita' crescente con il GS).
        if (state.rng.chance(0.15 + monster.species.challengeRating * 0.08)) {
            val loot = generator.rollLoot(state.depth, state.rng)
            level.dropItem(monster.x, monster.y, loot)
            log("${monster.name} lascia cadere: ${itemName(loot)}.", MessageKind.INFO)
        }
    }

    private fun onPlayerEnteredTile() {
        val trap = level.trapAt(player.x, player.y)
        if (trap != null && !trap.triggered) triggerTrap(trap)
        val ground = level.itemsAt(player.x, player.y)
        if (ground.isNotEmpty()) {
            log("Qui c'e': ${ground.joinToString { itemName(it) }}.", MessageKind.INFO)
        }
        when (level.tileAt(player.x, player.y)) {
            TileType.STAIRS_DOWN -> log("Una scala scende nel buio.", MessageKind.INFO)
            TileType.STAIRS_UP -> log("Una scala risale.", MessageKind.INFO)
            else -> {}
        }
    }

    private fun triggerTrap(trap: Trap) {
        trap.triggered = true
        trap.discovered = true
        val save = when (trap.kind) {
            TrapKind.POISON_GAS -> Combat.savingThrow(player, Ability.CONSTITUTION, trap.saveDc, state.rng)
            else -> Combat.savingThrow(player, Ability.DEXTERITY, trap.saveDc, state.rng)
        }
        val depth = state.depth
        when (trap.kind) {
            TrapKind.DART -> {
                if (save.success) {
                    log("Dardi dal muro: li schivi (${save.roll.total} vs CD ${trap.saveDc}).", MessageKind.GOOD)
                } else {
                    val dmg = DiceExpr(1 + depth / 3, 4, 1).roll(state.rng)
                    player.applyDamage(dmg)
                    log("Trappola a dardi! Subisci $dmg danni perforanti.", MessageKind.BAD)
                }
            }
            TrapKind.PIT -> {
                if (save.success) {
                    log("Una fossa si apre: ti aggrappi al bordo (${save.roll.total} vs CD ${trap.saveDc}).", MessageKind.GOOD)
                } else {
                    val dmg = DiceExpr(1 + depth / 2, 6).roll(state.rng)
                    player.applyDamage(dmg)
                    player.conditions = player.conditions.add(Condition.PRONE, 2)
                    log("Precipiti in una fossa! $dmg danni contundenti, sei prono.", MessageKind.BAD)
                }
            }
            TrapKind.POISON_GAS -> {
                if (save.success) {
                    log("Una nube verde ti investe, ma trattieni il respiro.", MessageKind.GOOD)
                } else {
                    val dmg = DiceExpr(1, 6 + depth / 2).roll(state.rng)
                    player.applyDamage(dmg)
                    player.conditions = player.conditions.add(Condition.POISONED, 8)
                    log("Gas velenoso! $dmg danni e sei avvelenato.", MessageKind.BAD)
                }
            }
            TrapKind.ALARM -> {
                level.monsters.forEach { it.alerted = true }
                log("Una campana risuona nel dungeon: tutti i mostri sanno dove sei.", MessageKind.BAD)
            }
        }
        checkPlayerDown()
    }

    /** Percezione passiva sulle caselle adiacenti: individua le trappole prima di calpestarle. */
    private fun searchForTraps() {
        val passive = 10 + player.abilityModifier(Ability.WISDOM)
        for (dy in -1..1) for (dx in -1..1) {
            val trap = level.trapAt(player.x + dx, player.y + dy) ?: continue
            if (trap.discovered || trap.triggered) continue
            if (passive >= trap.saveDc) {
                trap.discovered = true
                log("Individui una trappola: ${trap.kind.italian}.", MessageKind.GOOD)
            }
        }
    }

    private fun descend(): ActionOutcome {
        if (level.tileAt(player.x, player.y) != TileType.STAIRS_DOWN) {
            return ActionOutcome.refused("Non ci sono scale in discesa qui.")
        }
        state.depth++
        val firstVisit = state.depth > state.deepestDepth
        state.deepestDepth = max(state.deepestDepth, state.depth)
        val next = state.levels.getOrPut(state.depth) { generator.generate(state.depth, state.rng) }
        assignIds()
        next.placePlayerAtEntrance(player)
        player.shortRest() // riposo breve: si recupera Recuperare Energie
        log("Scendi al livello ${state.depth} del dungeon.", MessageKind.SYSTEM)
        if (firstVisit) {
            // PX per "sfida superata" (DMG): scendere e' un obiettivo, non solo uccidere.
            // Senza questo bonus il personaggio resta cronicamente sotto livello
            // rispetto ai mostri, perche' la tabella PX del PHB cresce molto in fretta.
            val bonus = 40 * state.depth * state.depth
            val levels = player.gainExperience(bonus, state.rng)
            log("Nuova profondita' raggiunta: +$bonus PX.", MessageKind.GOOD)
            if (levels > 0) log("Sali al livello ${player.level}!", MessageKind.GOOD)
        }
        if (state.depth >= config.maxDepth) {
            log("L'aria e' gelida. Qualcosa di antico ti attende qui sotto.", MessageKind.CRITICAL)
        }
        return ActionOutcome.spent()
    }

    private fun ascend(): ActionOutcome {
        if (level.tileAt(player.x, player.y) != TileType.STAIRS_UP) {
            return ActionOutcome.refused("Non ci sono scale in salita qui.")
        }
        if (state.depth <= 1) return ActionOutcome.refused("Fuggire non e' un'opzione: l'uscita e' sbarrata.")
        state.depth--
        val previous = state.levels.getValue(state.depth)
        previous.placePlayerAtExit(player)
        log("Risali al livello ${state.depth}.", MessageKind.SYSTEM)
        return ActionOutcome.spent()
    }

    private fun pickUp(): ActionOutcome {
        val item = level.removeItemAt(player.x, player.y) ?: return ActionOutcome.refused("Non c'e' nulla da raccogliere.")
        if (item is Treasure) {
            player.gold += item.value
            log("Raccogli ${item.name}. Oro totale: ${player.gold}.", MessageKind.GOOD)
        } else {
            if (player.inventory.size >= MAX_INVENTORY) {
                level.dropItem(player.x, player.y, item)
                return ActionOutcome.refused("Zaino pieno ($MAX_INVENTORY oggetti).")
            }
            player.inventory.add(item)
            log("Raccogli ${itemName(item)}.", MessageKind.GOOD)
        }
        return ActionOutcome.spent()
    }

    private fun dropItem(index: Int): ActionOutcome {
        val item = player.inventory.getOrNull(index) ?: return ActionOutcome.refused("Oggetto inesistente.")
        player.inventory.removeAt(index)
        level.dropItem(player.x, player.y, item)
        log("Lasci cadere ${itemName(item)}.", MessageKind.INFO)
        return ActionOutcome.spent()
    }

    private fun useItem(index: Int): ActionOutcome {
        val item = player.inventory.getOrNull(index) ?: return ActionOutcome.refused("Oggetto inesistente.")
        return when (item) {
            is Potion -> { player.inventory.removeAt(index); quaff(item); ActionOutcome.spent() }
            is Scroll -> {
                val spell = SpellBook[item.spell]
                val target = if (needsTarget(spell)) nearestVisibleMonster() else null
                if (needsTarget(spell) && target == null) return ActionOutcome.refused("Nessun bersaglio visibile.")
                player.inventory.removeAt(index)
                log("Leggi ${item.name}.", MessageKind.INFO)
                resolveSpell(spell, target, slotLevel = spell.level.coerceAtLeast(1), fromScroll = true)
                ActionOutcome.spent()
            }
            is Weapon -> {
                val previous = player.weapon
                player.weapon = item
                player.inventory[index] = previous
                log("Impugni ${item.displayName}.", MessageKind.INFO)
                ActionOutcome.spent()
            }
            is Armor -> {
                if (!player.heroClass.isProficientWithArmor(item)) {
                    log("Non sei competente con ${item.displayName}: svantaggio e malus.", MessageKind.BAD)
                }
                if (item.minimumStrength > player.abilities[Ability.STRENGTH]) {
                    log("${item.displayName} richiede Forza ${item.minimumStrength}: ti rallenta.", MessageKind.BAD)
                }
                val previous = player.armor
                player.armor = item
                if (previous != null) player.inventory[index] = previous else player.inventory.removeAt(index)
                log("Indossi ${item.displayName}. CA ${player.armorClass}.", MessageKind.INFO)
                ActionOutcome.spent()
            }
            is Shield -> {
                if (player.weapon.twoHanded) return ActionOutcome.refused("Non puoi usare lo scudo con un'arma a due mani.")
                val previous = player.shield
                player.shield = item
                if (previous != null) player.inventory[index] = previous else player.inventory.removeAt(index)
                log("Imbracci lo scudo. CA ${player.armorClass}.", MessageKind.INFO)
                ActionOutcome.spent()
            }
            is Treasure -> {
                player.gold += item.value
                player.inventory.removeAt(index)
                log("Intaschi ${item.name}.", MessageKind.GOOD)
                ActionOutcome.spent()
            }
        }
    }

    private fun quaff(potion: Potion) {
        when (potion.effect) {
            PotionEffect.HEALING, PotionEffect.GREATER_HEALING -> {
                val healed = player.heal(potion.amount.roll(state.rng))
                log("Bevi ${potion.name}: +$healed PF (${player.hitPoints}/${player.maxHitPoints}).", MessageKind.GOOD)
            }
            PotionEffect.STRENGTH -> {
                player.abilities = player.abilities.plus(Ability.STRENGTH, 2)
                player.strengthElixirTurns = 60
                log("Bevi ${potion.name}: Forza +2 per 60 turni.", MessageKind.GOOD)
            }
            PotionEffect.ANTIDOTE -> {
                player.conditions = player.conditions.remove(Condition.POISONED)
                log("Bevi ${potion.name}: il veleno svanisce.", MessageKind.GOOD)
            }
        }
    }

    private fun secondWind(): ActionOutcome {
        if (player.heroClass != HeroClass.FIGHTER) return ActionOutcome.refused("Solo il guerriero puo' usare Recuperare Energie.")
        if (!player.secondWindAvailable) return ActionOutcome.refused("Recuperare Energie e' gia' stato usato: scendi di livello per recuperarlo.")
        player.secondWindAvailable = false
        val healed = player.heal(DiceExpr(1, 10, player.level).roll(state.rng))
        log("Recuperare Energie: +$healed PF (${player.hitPoints}/${player.maxHitPoints}).", MessageKind.GOOD)
        return ActionOutcome.spent()
    }

    // --- Incantesimi ----------------------------------------------------------

    private fun needsTarget(spell: Spell): Boolean =
        spell.delivery == SpellDelivery.SPELL_ATTACK ||
            spell.delivery == SpellDelivery.SAVING_THROW && spell.area == com.changewave.dungeon.model.SpellArea.SINGLE ||
            spell.delivery == SpellDelivery.AUTOMATIC

    private fun cast(spellId: SpellId, targetId: Int?, slotLevel: Int): ActionOutcome {
        val spell = SpellBook[spellId]
        if (spellId !in player.heroClass.knownSpells) return ActionOutcome.refused("Non conosci ${spell.name}.")
        val effectiveSlot = if (spell.level == 0) 0 else slotLevel.coerceAtLeast(spell.level)
        if (spell.level > 0) {
            if (player.availableSlots(effectiveSlot) <= 0) return ActionOutcome.refused("Nessuno slot di livello $effectiveSlot disponibile.")
        }
        val target = if (needsTarget(spell)) {
            val t = targetId?.let { monsterById(it) } ?: nearestVisibleMonster()
            if (t == null) return ActionOutcome.refused("Nessun bersaglio visibile.")
            if (player.distanceTo(t) > spell.range) return ActionOutcome.refused("Bersaglio fuori gittata.")
            if (!FieldOfView.hasLineOfSight(level, player.x, player.y, t.x, t.y)) return ActionOutcome.refused("Nessuna linea di vista.")
            t
        } else null
        if (spell.level > 0) player.consumeSlot(effectiveSlot)
        resolveSpell(spell, target, effectiveSlot, fromScroll = false)
        return ActionOutcome.spent()
    }

    private fun resolveSpell(spell: Spell, target: Monster?, slotLevel: Int, fromScroll: Boolean) {
        val upcast = (slotLevel - spell.level).coerceAtLeast(0)
        when (spell.delivery) {
            SpellDelivery.SPELL_ATTACK -> {
                val t = target ?: return
                val mode = Combat.attackMode(player, t)
                val roll = state.rng.rollD20(player.spellAttackBonus, mode)
                if (roll.isCriticalMiss || (!roll.isCriticalHit && roll.total < t.armorClass)) {
                    log("${spell.name}: $roll vs CA ${t.armorClass}. Mancato.", MessageKind.COMBAT)
                    return
                }
                // I trucchetti scalano con il livello personaggio (PHB): 2 dadi dal 5o.
                val dice = if (spell.level == 0 && player.level >= 5) spell.damage.copy(count = spell.damage.count * 2) else spell.damage
                val raw = dice.roll(state.rng, roll.isCriticalHit)
                val dealt = Combat.applyTypedDamage(t, t.damageAfterDefenses(raw, spell.damageType), spell.damageType)
                log("${spell.name} colpisce ${t.name}: $dealt danni ${spell.damageType.italian}.", if (roll.isCriticalHit) MessageKind.CRITICAL else MessageKind.COMBAT)
                if (t.hitPoints <= 0) onMonsterKilled(t)
            }
            SpellDelivery.AUTOMATIC -> {
                val t = target ?: return
                val missiles = spell.missiles + upcast
                var total = 0
                repeat(missiles) { total += spell.damage.roll(state.rng) }
                val dealt = Combat.applyTypedDamage(t, t.damageAfterDefenses(total, spell.damageType), spell.damageType)
                log("${spell.name}: $missiles dardi colpiscono ${t.name} per $dealt danni.", MessageKind.COMBAT)
                if (t.hitPoints <= 0) onMonsterKilled(t)
            }
            SpellDelivery.SAVING_THROW -> {
                val targets: List<Monster> = if (spell.area == com.changewave.dungeon.model.SpellArea.BURST) {
                    level.monsters.filter { it.hitPoints > 0 && player.distanceTo(it) <= spell.radius + 1 }
                } else {
                    listOfNotNull(target)
                }
                if (targets.isEmpty()) {
                    log("${spell.name} non colpisce nessuno.", MessageKind.INFO)
                    return
                }
                val dice = when {
                    spell.level == 0 && player.level >= 5 -> spell.damage.copy(count = spell.damage.count * 2)
                    upcast > 0 -> spell.damage.copy(count = spell.damage.count + upcast)
                    else -> spell.damage
                }
                val dc = player.spellSaveDifficulty
                for (t in targets) {
                    val save = Combat.savingThrow(t, spell.save ?: Ability.DEXTERITY, dc, state.rng)
                    val rolled = dice.roll(state.rng)
                    val amount = when {
                        !save.success -> rolled
                        spell.halfOnSave -> rolled / 2
                        else -> 0
                    }
                    val dealt = Combat.applyTypedDamage(t, t.damageAfterDefenses(amount, spell.damageType), spell.damageType)
                    log("${spell.name} su ${t.name}: TS ${save.roll.total} vs CD $dc -> $dealt danni.", MessageKind.COMBAT)
                    if (t.hitPoints <= 0) onMonsterKilled(t)
                }
            }
            SpellDelivery.SELF -> {
                if (spell.healing.count > 0) {
                    val dice = spell.healing.copy(count = spell.healing.count + upcast)
                    val healed = player.heal(dice.roll(state.rng) + player.spellcastingModifier)
                    log("${spell.name}: +$healed PF (${player.hitPoints}/${player.maxHitPoints}).", MessageKind.GOOD)
                }
                spell.selfCondition?.let {
                    player.conditions = player.conditions.add(it, spell.conditionTurns)
                    log("${spell.name}: ${it.italian} per ${spell.conditionTurns} turni.", MessageKind.GOOD)
                }
            }
        }
    }

    // --- Ciclo del tempo ------------------------------------------------------

    private fun playerCanAct(): Boolean = !player.conditions.preventsAction() && player.hitPoints > 0

    private fun endPlayerTurn() {
        tickConditions(player)
        if (player.strengthElixirTurns > 0) {
            player.strengthElixirTurns--
            if (player.strengthElixirTurns == 0) {
                player.abilities = player.abilities.plus(Ability.STRENGTH, -2)
                log("L'effetto dell'elisir di forza svanisce.", MessageKind.INFO)
            }
        }
    }

    /**
     * Fa scorrere il tempo finche' il giocatore non ha di nuovo energia per agire.
     * Se il giocatore e' incapacitato (paralizzato, privo di sensi) il suo turno
     * viene consumato automaticamente, inclusi i tiri salvezza contro morte.
     */
    private fun advanceWorld() {
        var guard = 0
        while (guard++ < MAX_TICKS_PER_ACTION) {
            if (state.isOver) return
            if (player.energy >= Actor.ENERGY_PER_ACTION) {
                if (playerCanAct()) return
                player.energy -= Actor.ENERGY_PER_ACTION
                if (player.hitPoints <= 0) rollDeathSave() else tickConditions(player)
                continue
            }
            tick()
        }
    }

    private fun tick() {
        state.turn++
        player.energy += player.speed
        val monsters = level.monsters.toList()
        for (monster in monsters) {
            if (monster.hitPoints <= 0) continue
            monster.energy += monster.speed
            var actions = 0
            while (monster.energy >= Actor.ENERGY_PER_ACTION && monster.hitPoints > 0 && !state.isOver && actions++ < 3) {
                monster.energy -= Actor.ENERGY_PER_ACTION
                if (monster.conditions.preventsAction()) {
                    tickConditions(monster)
                    continue
                }
                MonsterAi.takeTurn(monster, player, level, state.rng, aiActions)
                tickConditions(monster)
            }
        }
        level.monsters.removeAll { it.hitPoints <= 0 }
    }

    private val aiActions = object : MonsterAi.Actions {
        override fun attackPlayer(monster: Monster, melee: Boolean) {
            val attack = (if (melee) monster.meleeAttack() else monster.rangedAttack())
                ?: monster.species.attacks.firstOrNull()
                ?: return
            val wasUnconscious = Condition.UNCONSCIOUS in player.conditions
            val outcome = Combat.monsterAttack(monster, attack, player, state.rng)
            if (wasUnconscious && outcome.hit) {
                // PHB: colpire una creatura priva di sensi in mischia e' un critico
                // automatico e ogni critico vale due fallimenti al tiro contro morte.
                val failures = if (outcome.critical) 2 else 1
                player.deathSaveFailures += failures
                log(
                    "${monster.name} infierisce sul tuo corpo privo di sensi: $failures fallimenti ai tiri contro morte (${player.deathSaveFailures}/3).",
                    MessageKind.CRITICAL,
                )
            } else {
                log(outcome.describe(), if (outcome.critical) MessageKind.CRITICAL else MessageKind.COMBAT)
            }
            if (player.hitPoints <= 0) checkPlayerDown()
        }

        override fun move(monster: Monster, to: Pos) {
            if (!level.isWalkable(to.x, to.y)) return
            if (level.monsterAt(to.x, to.y) != null) return
            if (to.x == player.x && to.y == player.y) return
            monster.x = to.x
            monster.y = to.y
        }

        override fun openDoor(monster: Monster, at: Pos) {
            level.setTile(at.x, at.y, TileType.DOOR_OPEN)
            if (level.isVisible(at.x, at.y)) log("${monster.name} apre una porta.", MessageKind.INFO)
        }

        override fun message(text: String, kind: MessageKind) = log(text, kind)
    }

    private fun tickConditions(actor: Actor) {
        val (next, expired) = actor.conditions.tick()
        actor.conditions = next
        for (condition in expired) {
            if (actor === player) log("Non sei piu': ${condition.italian}.", MessageKind.GOOD)
        }
    }

    /** Il giocatore e' a 0 PF: entra in stato di incoscienza e tira contro morte. */
    private fun checkPlayerDown() {
        if (player.hitPoints > 0 || state.isOver) return
        if (Condition.UNCONSCIOUS !in player.conditions) {
            player.conditions = player.conditions.add(Condition.UNCONSCIOUS, 99)
            player.deathSaveSuccesses = 0
            player.deathSaveFailures = 0
            log("Cadi privo di sensi! Tiri salvezza contro morte in corso.", MessageKind.CRITICAL)
        }
        if (player.deathSaveFailures >= 3) killPlayer()
    }

    private fun rollDeathSave() {
        val roll = state.rng.rollD20()
        when {
            roll.natural == 20 -> {
                player.conditions = player.conditions.remove(Condition.UNCONSCIOUS)
                player.hitPoints = 1
                player.deathSaveSuccesses = 0
                player.deathSaveFailures = 0
                log("Tiro contro morte: 20 naturale! Ti rialzi con 1 PF.", MessageKind.CRITICAL)
            }
            roll.natural == 1 -> {
                player.deathSaveFailures += 2
                log("Tiro contro morte: 1 naturale! Due fallimenti (${player.deathSaveFailures}/3).", MessageKind.BAD)
            }
            roll.total >= 10 -> {
                player.deathSaveSuccesses++
                log("Tiro contro morte riuscito (${player.deathSaveSuccesses}/3).", MessageKind.GOOD)
            }
            else -> {
                player.deathSaveFailures++
                log("Tiro contro morte fallito (${player.deathSaveFailures}/3).", MessageKind.BAD)
            }
        }
        if (player.deathSaveFailures >= 3) {
            killPlayer()
        } else if (player.deathSaveSuccesses >= 3) {
            player.conditions = player.conditions.remove(Condition.UNCONSCIOUS)
            player.hitPoints = 1
            player.deathSaveSuccesses = 0
            player.deathSaveFailures = 0
            log("Ti stabilizzi e riprendi conoscenza con 1 PF.", MessageKind.GOOD)
        }
    }

    private fun killPlayer() {
        player.isDead = true
        player.hitPoints = 0
        state.status = GameStatus.DEAD
        log("${player.name} muore nel livello ${state.depth} del dungeon. Punteggio: ${state.score()}.", MessageKind.CRITICAL)
    }

    // --- Utilita' -------------------------------------------------------------

    private fun log(text: String, kind: MessageKind = MessageKind.INFO) {
        state.log.add(state.turn, text, kind)
        pendingMessages.add(LogMessage(state.turn, text, kind))
    }

    private fun itemName(item: Item): String = when (item) {
        is Weapon -> item.displayName
        is Armor -> item.displayName
        else -> item.name
    }

    companion object {
        const val MAX_INVENTORY = 16
        private const val MAX_TICKS_PER_ACTION = 500

        /** Crea una nuova partita: personaggio, primo livello, visibilita' iniziale. */
        fun newGame(
            name: String,
            heroClass: HeroClass,
            abilities: AbilityScores = PlayerCharacter.recommendedScores(heroClass),
            seed: Long = System.nanoTime(),
            config: DungeonConfig = DungeonConfig(),
        ): GameEngine {
            val rng = GameRandom.fromSeed(seed)
            val player = PlayerCharacter.create(name, heroClass, abilities)
            player.energy = Actor.ENERGY_PER_ACTION
            val state = GameState(seed = seed, player = player, rng = rng)
            val generator = DungeonGenerator(config)
            val first = generator.generate(1, rng)
            state.levels[1] = first
            first.placePlayerAtEntrance(player)
            val engine = GameEngine(state, config)
            engine.assignIds()
            engine.recomputeVisibility()
            state.log.add(0, "${player.name}, ${heroClass.italian} di livello 1, entra nel dungeon.", MessageKind.SYSTEM)
            return engine
        }
    }

    /** Assegna identificatori stabili ai mostri (usati dalla UI per bersagliare). */
    fun assignIds() {
        for (lvl in state.levels.values) {
            for (monster in lvl.monsters) {
                if (monster.id == 0) monster.id = state.nextActorId++
            }
        }
    }
}
