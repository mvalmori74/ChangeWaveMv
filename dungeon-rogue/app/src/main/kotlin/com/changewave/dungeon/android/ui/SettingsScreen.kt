package com.changewave.dungeon.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.changewave.dungeon.android.data.AudioSettings
import com.changewave.dungeon.audio.MusicDirector

/**
 * Schermata di configurazione. Oggi contiene l'audio; e' strutturata a schede
 * ("Audio", e in futuro comandi e accessibilita') per non dover essere rifatta.
 */
@Composable
fun SettingsScreen(
    settings: AudioSettings,
    currentDepth: Int,
    message: String?,
    onMusicEnabledChange: (Boolean) -> Unit,
    onMusicVolumeChange: (Float) -> Unit,
    onCustomTrackPicked: (Uri) -> Unit,
    onCustomTrackCleared: () -> Unit,
    onCustomTrackDepthChange: (Int) -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    // Selettore di sistema: restituisce un URI di sola lettura sul file scelto,
    // senza copiarlo dentro l'app.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onCustomTrackPicked(uri)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DungeonColors.Background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Impostazioni", color = DungeonColors.Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Audio", color = DungeonColors.Bone, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Musica di sottofondo", color = DungeonColors.Bone, fontSize = 14.sp)
                        Text(
                            "Generata dal gioco, diventa piu' cupa scendendo",
                            color = DungeonColors.Stone,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(checked = settings.musicEnabled, onCheckedChange = onMusicEnabledChange)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Volume", color = DungeonColors.Bone, fontSize = 14.sp)
                    Text(
                        "${(settings.musicVolume * 100).toInt()}%",
                        color = DungeonColors.Amber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                }
                Slider(
                    value = settings.musicVolume,
                    onValueChange = onMusicVolumeChange,
                    valueRange = 0f..1f,
                    enabled = settings.musicEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "A 0% la musica tace del tutto. Il valore e' salvato e ripristinato al riavvio.",
                    color = DungeonColors.Stone,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Brano personalizzato", color = DungeonColors.Bone, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Puoi usare un file audio del telefono al posto della musica generata, " +
                        "per un livello a tua scelta. Il file resta dove sta: l'app lo legge, " +
                        "non lo copia al proprio interno.",
                    color = DungeonColors.Stone,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    settings.customTrackName?.let { "In uso: $it" } ?: "Nessun brano scelto",
                    color = if (settings.customTrackName != null) DungeonColors.Poison else DungeonColors.Stone,
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Scegli un file") }
                    if (settings.customTrackUri != null) {
                        TextButton(onClick = onCustomTrackCleared) { Text("Rimuovi") }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Livello in cui suona", color = DungeonColors.Bone, fontSize = 13.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (level in 1..10) {
                        FilterChip(
                            selected = settings.customTrackDepth == level,
                            onClick = { onCustomTrackDepthChange(level) },
                            label = { Text("$level", fontSize = 12.sp) },
                        )
                    }
                }

                if (message != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(message, color = DungeonColors.Blood, fontSize = 11.sp)
                    TextButton(onClick = onMessageShown) { Text("Ho capito") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DungeonColors.Surface),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Cosa stai ascoltando", color = DungeonColors.Bone, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    MusicDirector.describe(currentDepth),
                    color = DungeonColors.Arcane,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nessun brano registrato: la musica e' sintetizzata in tempo reale. Il primo " +
                        "livello ha un tema d'avventura in modo misolidio, con arpeggio ritmico e " +
                        "basso camminante. Scendendo, la scala passa a eoliana, poi frigia, poi " +
                        "locria, infine si assesta sul tritono; entrano battito cardiaco, rintocchi " +
                        "di campana e un letto di rumore, mentre il filtro si chiude e l'eco si allunga.",
                    color = DungeonColors.Stone,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text("Indietro")
        }
    }
}
