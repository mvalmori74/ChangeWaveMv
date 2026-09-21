package com.changewave.dungeon.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.changewave.dungeon.android.vm.GameUiState
import com.changewave.dungeon.android.vm.HudState
import com.changewave.dungeon.android.vm.SpellEntry
import com.changewave.dungeon.android.vm.TargetEntry
import com.changewave.dungeon.game.Command
import com.changewave.dungeon.game.MessageKind
import com.changewave.dungeon.model.SpellDelivery
import com.changewave.dungeon.model.SpellId

/** Dialoghi modali sovrapposti alla schermata di gioco. */
private sealed interface Sheet {
    data object None : Sheet
    data object Inventory : Sheet
    data object Spells : Sheet
    data class Targeting(val spell: SpellId?, val ranged: Boolean) : Sheet
    data object Character : Sheet
}

@Composable
fun GameScreen(
    state: GameUiState,
    onCommand: (Command) -> Unit,
    onAbandon: () -> Unit,
) {
    var sheet by remember { mutableStateOf<Sheet>(Sheet.None) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DungeonColors.Background)
            .padding(8.dp),
    ) {
        HudBar(state.hud, onCharacterClick = { sheet = Sheet.Character })

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
        ) {
            DungeonCanvas(
                map = state.map,
                modifier = Modifier.fillMaxSize(),
                onDirection = { dx, dy -> onCommand(Command.Move(dx, dy)) },
            )
        }

        MessageLogPanel(state, modifier = Modifier.fillMaxWidth().height(96.dp))

        Spacer(Modifier.height(6.dp))

        ActionBar(
            state = state,
            onCommand = onCommand,
            onOpenInventory = { sheet = Sheet.Inventory },
            onOpenSpells = { sheet = Sheet.Spells },
            onOpenRanged = { sheet = Sheet.Targeting(spell = null, ranged = true) },
        )

        Spacer(Modifier.height(6.dp))

        DirectionPad(
            onDirection = { dx, dy -> onCommand(Command.Move(dx, dy)) },
            onWait = { onCommand(Command.Wait) },
        )
    }

    when (val current = sheet) {
        Sheet.None -> Unit
        Sheet.Inventory -> InventoryDialog(
            state = state,
            onUse = { index -> onCommand(Command.UseItem(index)); sheet = Sheet.None },
            onDrop = { index -> onCommand(Command.DropItem(index)); sheet = Sheet.None },
            onDismiss = { sheet = Sheet.None },
        )
        Sheet.Spells -> SpellDialog(
            spells = state.spells,
            onCast = { entry ->
                sheet = if (entry.spell.delivery == SpellDelivery.SELF) {
                    onCommand(Command.Cast(entry.spell.id, slotLevel = maxOf(1, entry.spell.level)))
                    Sheet.None
                } else {
                    Sheet.Targeting(spell = entry.spell.id, ranged = false)
                }
            },
            onDismiss = { sheet = Sheet.None },
        )
        is Sheet.Targeting -> TargetDialog(
            targets = state.targets,
            onSelect = { target ->
                if (current.ranged) {
                    onCommand(Command.RangedAttack(target.id))
                } else if (current.spell != null) {
                    onCommand(Command.Cast(current.spell, target.id))
                }
                sheet = Sheet.None
            },
            onDismiss = { sheet = Sheet.None },
        )
        Sheet.Character -> CharacterSheetDialog(
            hud = state.hud,
            onAbandon = onAbandon,
            onDismiss = { sheet = Sheet.None },
        )
    }
}

@Composable
private fun HudBar(hud: HudState, onCharacterClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${hud.name} - ${hud.heroClass} liv. ${hud.level}",
                    color = DungeonColors.Amber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCharacterClick) { Text("Scheda") }
            }

            val fraction = if (hud.maxHitPoints == 0) 0f else hud.hitPoints.toFloat() / hud.maxHitPoints
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (fraction < 0.34f) DungeonColors.Blood else DungeonColors.Poison,
                trackColor = DungeonColors.SurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "PF ${hud.hitPoints}/${hud.maxHitPoints}   CA ${hud.armorClass}   " +
                    "Prof. ${hud.depth}   PX ${hud.experience}" +
                    (hud.experienceToNext?.let { " (-$it)" } ?: " (max)") +
                    "   Oro ${hud.gold}",
                color = DungeonColors.Bone,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Arma: ${hud.weapon}" +
                    if (hud.maxSlotsLevel1 > 0) {
                        "   Slot I ${hud.slotsLevel1}/${hud.maxSlotsLevel1}" +
                            if (hud.maxSlotsLevel2 > 0) "  II ${hud.slotsLevel2}/${hud.maxSlotsLevel2}" else ""
                    } else "",
                color = DungeonColors.Stone,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            if (hud.conditions.isNotEmpty()) {
                Text(
                    text = hud.conditions.joinToString(" - "),
                    color = DungeonColors.Blood,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun MessageLogPanel(state: GameUiState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .verticalScroll(scroll),
        ) {
            state.rejection?.let {
                Text(text = "! $it", color = DungeonColors.Amber, fontSize = 12.sp)
            }
            for (message in state.log.takeLast(12)) {
                Text(
                    text = message.text,
                    color = when (message.kind) {
                        MessageKind.COMBAT -> DungeonColors.Bone
                        MessageKind.GOOD -> DungeonColors.Poison
                        MessageKind.BAD -> DungeonColors.Blood
                        MessageKind.CRITICAL -> DungeonColors.Amber
                        MessageKind.SYSTEM -> DungeonColors.Arcane
                        MessageKind.INFO -> DungeonColors.Stone
                    },
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionBar(
    state: GameUiState,
    onCommand: (Command) -> Unit,
    onOpenInventory: () -> Unit,
    onOpenSpells: () -> Unit,
    onOpenRanged: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactButton("Zaino", Modifier.weight(1f), onClick = onOpenInventory)
        if (state.spells.isNotEmpty()) {
            CompactButton("Magie", Modifier.weight(1f), onClick = onOpenSpells)
        }
        if (state.hud.hasRangedWeapon && state.targets.isNotEmpty()) {
            CompactButton("Tira", Modifier.weight(1f), onClick = onOpenRanged)
        }
        if (state.onItem) {
            CompactButton("Raccogli", Modifier.weight(1f)) { onCommand(Command.PickUp) }
        }
        if (state.onStairsDown) {
            CompactButton("Scendi", Modifier.weight(1f)) { onCommand(Command.Descend) }
        }
        if (state.hud.isFighter && state.hud.secondWindAvailable) {
            CompactButton("Energie", Modifier.weight(1f)) { onCommand(Command.SecondWind) }
        }
    }
}

@Composable
private fun CompactButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * Croce direzionale a 8 vie: su schermo tattile e' piu' affidabile del solo
 * tocco sulla mappa, soprattutto in combattimento ravvicinato.
 */
@Composable
private fun DirectionPad(onDirection: (Int, Int) -> Unit, onWait: () -> Unit) {
    val directions = listOf(
        listOf(-1 to -1, 0 to -1, 1 to -1),
        listOf(-1 to 0, 0 to 0, 1 to 0),
        listOf(-1 to 1, 0 to 1, 1 to 1),
    )
    val labels = listOf(
        listOf("↖", "↑", "↗"),
        listOf("←", "·", "→"),
        listOf("↙", "↓", "↘"),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        directions.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { columnIndex, (dx, dy) ->
                    Button(
                        onClick = { if (dx == 0 && dy == 0) onWait() else onDirection(dx, dy) },
                        modifier = Modifier.size(width = 64.dp, height = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(labels[rowIndex][columnIndex], fontSize = 16.sp)
                    }
                }
            }
        }
        Text("Il tasto centrale attende un turno e cerca trappole", color = DungeonColors.Stone, fontSize = 10.sp)
    }
}

@Composable
private fun InventoryDialog(
    state: GameUiState,
    onUse: (Int) -> Unit,
    onDrop: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("Zaino (${state.inventory.size}/16)") },
        text = {
            if (state.inventory.isEmpty()) {
                Text("Lo zaino e' vuoto.")
            } else {
                LazyColumn {
                    items(state.inventory) { entry ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(entry.label, fontWeight = FontWeight.SemiBold)
                            Text(entry.detail, fontSize = 11.sp, color = DungeonColors.Stone)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onUse(entry.index) }) { Text(entry.actionLabel) }
                                TextButton(onClick = { onDrop(entry.index) }) { Text("Lascia") }
                            }
                            HorizontalDivider(color = DungeonColors.SurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SpellDialog(spells: List<SpellEntry>, onCast: (SpellEntry) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("Incantesimi") },
        text = {
            LazyColumn {
                items(spells) { entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            entry.spell.name,
                            fontWeight = FontWeight.SemiBold,
                            color = if (entry.castable) DungeonColors.Bone else DungeonColors.Stone,
                        )
                        Text(entry.spell.description, fontSize = 11.sp, color = DungeonColors.Stone)
                        Text(entry.detail, fontSize = 11.sp, color = DungeonColors.Arcane)
                        TextButton(enabled = entry.castable, onClick = { onCast(entry) }) { Text("Lancia") }
                        HorizontalDivider(color = DungeonColors.SurfaceVariant)
                    }
                }
            }
        },
    )
}

@Composable
private fun TargetDialog(targets: List<TargetEntry>, onSelect: (TargetEntry) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        title = { Text("Scegli il bersaglio") },
        text = {
            if (targets.isEmpty()) {
                Text("Nessun nemico in vista.")
            } else {
                LazyColumn {
                    items(targets) { target ->
                        TextButton(onClick = { onSelect(target) }) {
                            Text("${target.label} - ${target.distance} caselle - PF ${target.hitPoints}/${target.maxHitPoints}")
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun CharacterSheetDialog(hud: HudState, onAbandon: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        dismissButton = {
            TextButton(onClick = onAbandon) { Text("Abbandona partita", color = DungeonColors.Blood) }
        },
        title = { Text("${hud.name}, ${hud.heroClass}") },
        text = {
            Column {
                SheetRow("Livello", hud.level.toString())
                SheetRow("Punti ferita", "${hud.hitPoints}/${hud.maxHitPoints}")
                SheetRow("Classe armatura", hud.armorClass.toString())
                SheetRow("Arma", hud.weapon)
                SheetRow("Profondita'", hud.depth.toString())
                SheetRow("Turni", hud.turn.toString())
                SheetRow("Mostri uccisi", hud.monstersKilled.toString())
                SheetRow("Oro", hud.gold.toString())
                SheetRow("Punteggio", hud.score.toString())
            }
        },
    )
}

@Composable
private fun SheetRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DungeonColors.Stone, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
