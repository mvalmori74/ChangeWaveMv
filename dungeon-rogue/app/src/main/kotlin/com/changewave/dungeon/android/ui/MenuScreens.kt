package com.changewave.dungeon.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.changewave.dungeon.android.vm.GameUiState
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.model.PlayerCharacter
import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.GameRandom

@Composable
fun MainMenuScreen(
    hasSave: Boolean,
    loading: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DungeonColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DUNGEON d20", color = DungeonColors.Amber, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Roguelike a turni con regole D&D 5e (SRD 5.1)",
            color = DungeonColors.Stone,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(32.dp))

        if (hasSave) {
            Button(
                onClick = onContinue,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) { Text(if (loading) "Caricamento..." else "Riprendi la discesa") }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) { Text(if (hasSave) "Nuovo personaggio (abbandona la partita)" else "Nuova partita") }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) { Text("Impostazioni (musica e volume)") }

        Spacer(Modifier.height(28.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Regole essenziali", color = DungeonColors.Amber, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "- Attacchi, tiri salvezza e critici seguono il d20 del regolamento 5e\n" +
                        "- 10 livelli di dungeon generati proceduralmente, morte permanente\n" +
                        "- A 0 PF si cade privi di sensi e si tirano i tiri contro morte\n" +
                        "- Scendere di livello vale come riposo breve\n" +
                        "- In fondo attende Malgrim, Signore dei Sepolcri",
                    color = DungeonColors.Bone,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun CharacterCreationScreen(
    onStart: (String, HeroClass, AbilityScores) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var heroClass by remember { mutableStateOf(HeroClass.FIGHTER) }
    var scores by remember { mutableStateOf(PlayerCharacter.recommendedScores(HeroClass.FIGHTER)) }
    var rolled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DungeonColors.Background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Creazione del personaggio", color = DungeonColors.Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(16) },
            label = { Text("Nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Text("Classe", color = DungeonColors.Bone, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HeroClass.entries.forEach { option ->
                FilterChip(
                    selected = heroClass == option,
                    onClick = {
                        heroClass = option
                        if (!rolled) scores = PlayerCharacter.recommendedScores(option)
                    },
                    label = { Text(option.italian, fontSize = 12.sp) },
                )
            }
        }
        Text(heroClass.description, color = DungeonColors.Stone, fontSize = 12.sp)

        Spacer(Modifier.height(16.dp))
        Text("Caratteristiche", color = DungeonColors.Bone, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Ability.entries.forEach { ability ->
                    val value = scores[ability]
                    val modifier = AbilityScores.modifierOf(value)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ability.italian, color = DungeonColors.Bone, fontSize = 13.sp)
                        Text(
                            "$value (${if (modifier >= 0) "+" else ""}$modifier)",
                            color = if (ability == heroClass.primaryAbility) DungeonColors.Amber else DungeonColors.Stone,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val hitPoints = heroClass.hitDie + AbilityScores.modifierOf(scores[Ability.CONSTITUTION])
                Text(
                    "PF iniziali: ${maxOf(1, hitPoints)}  -  Dado vita d${heroClass.hitDie}",
                    color = DungeonColors.Poison,
                    fontSize = 12.sp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scores = PlayerCharacter.recommendedScores(heroClass)
                rolled = false
            }) { Text("Array standard") }
            OutlinedButton(onClick = {
                scores = AbilityScores.rolled(GameRandom.fromSeed(System.nanoTime()))
                rolled = true
            }) { Text("Tira 4d6") }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onStart(name, heroClass, scores) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) { Text("Entra nel dungeon") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Indietro") }
    }
}

@Composable
fun GameOverScreen(state: GameUiState, victory: Boolean, onRestart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DungeonColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (victory) "VITTORIA" else "MORTO",
            color = if (victory) DungeonColors.Gold else DungeonColors.Blood,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (victory) {
                "${state.hud.name} ha distrutto Malgrim e risalito le scale con il bottino."
            } else {
                "${state.hud.name} riposa per sempre al livello ${state.hud.depth} del dungeon."
            },
            color = DungeonColors.Bone,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                ResultRow("Punteggio", state.hud.score.toString())
                ResultRow("Livello personaggio", state.hud.level.toString())
                ResultRow("Profondita' raggiunta", state.hud.depth.toString())
                ResultRow("Mostri uccisi", state.hud.monstersKilled.toString())
                ResultRow("Oro raccolto", state.hud.gold.toString())
                ResultRow("Turni giocati", state.hud.turn.toString())
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            state.log.takeLast(4).joinToString("\n") { it.text },
            color = DungeonColors.Stone,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text("Nuova partita")
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DungeonColors.Stone, fontSize = 13.sp)
        Spacer(Modifier.height(0.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}
