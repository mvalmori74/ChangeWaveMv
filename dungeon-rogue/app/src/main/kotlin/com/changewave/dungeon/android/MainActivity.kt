package com.changewave.dungeon.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.changewave.dungeon.android.ui.CharacterCreationScreen
import com.changewave.dungeon.android.ui.DungeonColors
import com.changewave.dungeon.android.ui.DungeonTheme
import com.changewave.dungeon.android.ui.GameOverScreen
import com.changewave.dungeon.android.ui.GameScreen
import com.changewave.dungeon.android.ui.MainMenuScreen
import com.changewave.dungeon.android.ui.SettingsScreen
import com.changewave.dungeon.android.vm.AppState
import com.changewave.dungeon.android.vm.GameViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DungeonTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DungeonColors.Background) {
                    DungeonApp(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppResumed()
    }

    /** Il salvataggio avviene ad ogni turno; qui si copre la chiusura improvvisa. */
    override fun onStop() {
        super.onStop()
        viewModel.onAppPaused()
    }
}

@Composable
private fun DungeonApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val audio by viewModel.audio.collectAsState()
    when (val current = state) {
        is AppState.Menu -> MainMenuScreen(
            hasSave = current.hasSave,
            loading = current.loading,
            onContinue = viewModel::continueGame,
            onNewGame = viewModel::startCreation,
            onSettings = viewModel::openSettings,
        )
        AppState.Settings -> SettingsScreen(
            settings = audio,
            currentDepth = viewModel.currentDepth(),
            onMusicEnabledChange = viewModel::setMusicEnabled,
            onMusicVolumeChange = viewModel::setMusicVolume,
            onBack = viewModel::closeSettings,
        )
        AppState.Creation -> CharacterCreationScreen(
            onStart = viewModel::newGame,
            onBack = viewModel::backToMenu,
        )
        is AppState.Playing -> GameScreen(
            state = current.game,
            onCommand = viewModel::execute,
            onAbandon = viewModel::abandonGame,
            onOpenSettings = viewModel::openSettings,
        )
        is AppState.Finished -> GameOverScreen(
            state = current.game,
            victory = current.victory,
            onRestart = viewModel::startCreation,
        )
    }
}
