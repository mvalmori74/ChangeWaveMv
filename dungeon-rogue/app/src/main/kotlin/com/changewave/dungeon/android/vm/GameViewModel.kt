package com.changewave.dungeon.android.vm

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.changewave.dungeon.android.audio.MusicPlayer
import com.changewave.dungeon.android.data.AudioSettings
import com.changewave.dungeon.android.data.SaveStore
import com.changewave.dungeon.android.data.SettingsStore
import com.changewave.dungeon.dungeon.TileType
import com.changewave.dungeon.game.Command
import com.changewave.dungeon.game.GameEngine
import com.changewave.dungeon.game.GameStatus
import com.changewave.dungeon.game.LogMessage
import com.changewave.dungeon.model.Armor
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.model.Item
import com.changewave.dungeon.model.Potion
import com.changewave.dungeon.model.Progression
import com.changewave.dungeon.model.Scroll
import com.changewave.dungeon.model.Shield
import com.changewave.dungeon.model.Spell
import com.changewave.dungeon.model.Weapon
import com.changewave.dungeon.rules.AbilityScores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Schermata corrente dell'app. */
sealed interface AppState {
    data class Menu(val hasSave: Boolean, val loading: Boolean = false) : AppState
    data object Creation : AppState
    data object Settings : AppState
    data class Playing(val game: GameUiState) : AppState
    data class Finished(val game: GameUiState, val victory: Boolean) : AppState
}

/** Snapshot immutabile dello stato di gioco per la UI (nessun accesso diretto al motore). */
data class GameUiState(
    val map: MapSnapshot,
    val hud: HudState,
    val log: List<LogMessage>,
    val inventory: List<InventoryEntry>,
    val spells: List<SpellEntry>,
    val targets: List<TargetEntry>,
    val onStairsDown: Boolean,
    val onItem: Boolean,
    val rejection: String? = null,
)

data class MapSnapshot(
    val width: Int,
    val height: Int,
    val glyphs: CharArray,
    val kinds: ByteArray,
    val visible: BooleanArray,
    val explored: BooleanArray,
    val playerX: Int,
    val playerY: Int,
) {
    fun glyphAt(x: Int, y: Int): Char = glyphs[y * width + x]
    fun kindAt(x: Int, y: Int): Byte = kinds[y * width + x]
    fun isVisible(x: Int, y: Int): Boolean = visible[y * width + x]
    fun isExplored(x: Int, y: Int): Boolean = explored[y * width + x]

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
        // Categorie usate dal renderer per scegliere il colore.
        const val KIND_WALL: Byte = 0
        const val KIND_FLOOR: Byte = 1
        const val KIND_DOOR: Byte = 2
        const val KIND_STAIRS: Byte = 3
        const val KIND_ITEM: Byte = 4
        const val KIND_MONSTER: Byte = 5
        const val KIND_PLAYER: Byte = 6
        const val KIND_TRAP: Byte = 7
        const val KIND_BOSS: Byte = 8
    }
}

data class HudState(
    val name: String,
    val heroClass: String,
    val level: Int,
    val hitPoints: Int,
    val maxHitPoints: Int,
    val armorClass: Int,
    val depth: Int,
    val experience: Int,
    val experienceToNext: Int?,
    val gold: Int,
    val turn: Int,
    val weapon: String,
    val conditions: List<String>,
    val slotsLevel1: Int,
    val maxSlotsLevel1: Int,
    val slotsLevel2: Int,
    val maxSlotsLevel2: Int,
    val secondWindAvailable: Boolean,
    val isFighter: Boolean,
    val hasRangedWeapon: Boolean,
    val score: Int,
    val monstersKilled: Int,
)

data class InventoryEntry(val index: Int, val label: String, val detail: String, val actionLabel: String)

data class SpellEntry(val spell: Spell, val castable: Boolean, val detail: String)

data class TargetEntry(val id: Int, val label: String, val distance: Int, val hitPoints: Int, val maxHitPoints: Int)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SaveStore(application)
    private val settingsStore = SettingsStore(application)
    private val music = MusicPlayer(application)
    private var engine: GameEngine? = null

    private val _state = MutableStateFlow<AppState>(AppState.Menu(store.hasSave()))
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _audio = MutableStateFlow(settingsStore.load())
    val audio: StateFlow<AudioSettings> = _audio.asStateFlow()

    /** Schermata da cui si e' aperta la configurazione, per tornarci uscendo. */
    private var stateBeforeSettings: AppState? = null

    /** Avvisi sull'audio da mostrare nelle impostazioni (es. brano non leggibile). */
    private val _audioMessage = MutableStateFlow<String?>(null)
    val audioMessage: StateFlow<String?> = _audioMessage.asStateFlow()

    init {
        music.onCustomTrackFailed = { reason ->
            // Il brano scelto non e' riproducibile: si torna alla musica generata
            // e si dimentica il riferimento, che resterebbe rotto.
            _audio.value = _audio.value.copy(customTrackUri = null, customTrackName = null)
            settingsStore.save(_audio.value)
            _audioMessage.value = "Brano personalizzato non riproducibile ($reason): torno alla musica del gioco."
        }
        applyAudioSettings()
    }

    fun clearAudioMessage() {
        _audioMessage.value = null
    }

    // --- audio ----------------------------------------------------------------

    fun setMusicEnabled(enabled: Boolean) {
        updateAudio(_audio.value.copy(musicEnabled = enabled))
    }

    fun setMusicVolume(volume: Float) {
        updateAudio(_audio.value.copy(musicVolume = volume.coerceIn(0f, 1f)))
    }

    private fun updateAudio(settings: AudioSettings) {
        _audio.value = settings
        settingsStore.save(settings)
        applyAudioSettings()
    }

    /**
     * Registra il brano scelto dal selettore di sistema. Il permesso di lettura
     * viene reso persistente, altrimenti al riavvio dell'app l'URI non sarebbe
     * piu' accessibile.
     */
    fun pickCustomTrack(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = resolveDisplayName(uri) ?: "brano scelto"
        updateAudio(_audio.value.copy(customTrackUri = uri.toString(), customTrackName = name))
        _audioMessage.value = null
    }

    fun clearCustomTrack() {
        updateAudio(_audio.value.copy(customTrackUri = null, customTrackName = null))
    }

    fun setCustomTrackDepth(depth: Int) {
        updateAudio(_audio.value.copy(customTrackDepth = depth.coerceIn(1, 10)))
    }

    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    private fun applyAudioSettings() {
        val settings = _audio.value
        music.setVolume(settings.musicVolume)
        music.setCustomTrack(settings.customTrackUri?.let { Uri.parse(it) }, settings.customTrackDepth)
        music.setEnabled(settings.musicEnabled)
    }

    /** Profondita' corrente, mostrata nella schermata delle impostazioni. */
    fun currentDepth(): Int = engine?.state?.depth ?: 1

    // --- navigazione ----------------------------------------------------------

    fun openSettings() {
        stateBeforeSettings = _state.value
        _state.value = AppState.Settings
    }

    fun closeSettings() {
        _state.value = stateBeforeSettings ?: AppState.Menu(store.hasSave())
        stateBeforeSettings = null
    }

    fun startCreation() {
        _state.value = AppState.Creation
    }

    fun backToMenu() {
        _state.value = AppState.Menu(store.hasSave())
    }

    fun continueGame() {
        _state.value = AppState.Menu(hasSave = true, loading = true)
        viewModelScope.launch {
            val loaded = store.load()
            if (loaded == null) {
                _state.value = AppState.Menu(hasSave = false)
                return@launch
            }
            val restored = withContext(Dispatchers.Default) {
                GameEngine(loaded).also { it.assignIds(); it.recomputeVisibility() }
            }
            engine = restored
            publish(restored)
        }
    }

    fun newGame(name: String, heroClass: HeroClass, abilities: AbilityScores) {
        viewModelScope.launch {
            val created = withContext(Dispatchers.Default) {
                GameEngine.newGame(name.ifBlank { "Avventuriero" }, heroClass, abilities)
            }
            engine = created
            store.delete()
            publish(created)
            persist()
        }
    }

    fun execute(command: Command) {
        val current = engine ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { current.execute(command) }
            publish(current, result.rejection)
            if (result.timeAdvanced) persist()
        }
    }

    fun abandonGame() {
        viewModelScope.launch {
            engine = null
            store.delete()
            _state.value = AppState.Menu(hasSave = false)
        }
    }

    private fun persist() {
        val current = engine ?: return
        viewModelScope.launch {
            if (current.state.status == GameStatus.PLAYING) store.save(current.state) else store.delete()
        }
    }

    /** Da chiamare in onStop: salva la partita e libera il dispositivo audio. */
    fun onAppPaused() {
        persist()
        music.pause()
    }

    /** Da chiamare in onStart: riprende la musica se l'utente la vuole. */
    fun onAppResumed() {
        music.resume()
    }

    override fun onCleared() {
        music.release()
        super.onCleared()
    }

    private fun publish(current: GameEngine, rejection: String? = null) {
        val ui = withEngine(current, rejection)
        // La colonna sonora segue la profondita': la transizione la gestisce il
        // sintetizzatore con una dissolvenza di alcuni secondi.
        music.setDepth(current.state.depth)
        _state.value = when (current.state.status) {
            GameStatus.PLAYING -> AppState.Playing(ui)
            GameStatus.VICTORY -> AppState.Finished(ui, victory = true)
            GameStatus.DEAD -> AppState.Finished(ui, victory = false)
        }
    }

    private fun withEngine(engine: GameEngine, rejection: String?): GameUiState {
        val level = engine.level
        val player = engine.player
        val size = level.width * level.height
        val glyphs = CharArray(size)
        val kinds = ByteArray(size)
        val visible = BooleanArray(size)
        val explored = BooleanArray(size)

        for (y in 0 until level.height) {
            for (x in 0 until level.width) {
                val i = y * level.width + x
                val tile = level.tileAt(x, y)
                glyphs[i] = tile.glyph
                kinds[i] = when (tile) {
                    TileType.WALL -> MapSnapshot.KIND_WALL
                    TileType.DOOR_CLOSED, TileType.DOOR_OPEN -> MapSnapshot.KIND_DOOR
                    TileType.STAIRS_DOWN, TileType.STAIRS_UP -> MapSnapshot.KIND_STAIRS
                    else -> MapSnapshot.KIND_FLOOR
                }
                visible[i] = level.isVisible(x, y)
                explored[i] = level.isExplored(x, y)
            }
        }

        // Trappole individuate, oggetti e mostri sovrascrivono il glifo della casella.
        for (trap in level.traps) {
            if (!trap.discovered) continue
            val i = trap.pos.y * level.width + trap.pos.x
            glyphs[i] = '^'
            kinds[i] = MapSnapshot.KIND_TRAP
        }
        for (ground in level.items) {
            val i = ground.pos.y * level.width + ground.pos.x
            glyphs[i] = ground.item.glyph
            kinds[i] = MapSnapshot.KIND_ITEM
        }
        for (monster in level.monsters) {
            if (monster.hitPoints <= 0) continue
            val i = monster.y * level.width + monster.x
            glyphs[i] = monster.glyph
            kinds[i] = if (monster.species.boss) MapSnapshot.KIND_BOSS else MapSnapshot.KIND_MONSTER
        }
        val playerIndex = player.y * level.width + player.x
        glyphs[playerIndex] = player.glyph
        kinds[playerIndex] = MapSnapshot.KIND_PLAYER

        val hud = HudState(
            name = player.name,
            heroClass = player.heroClass.italian,
            level = player.level,
            hitPoints = player.hitPoints,
            maxHitPoints = player.maxHitPoints,
            armorClass = player.armorClass,
            depth = engine.state.depth,
            experience = player.experience,
            experienceToNext = Progression.experienceToNextLevel(player.experience),
            gold = player.gold,
            turn = engine.state.turn,
            weapon = player.weapon.displayName,
            conditions = player.conditions.active.map { "${it.condition.italian} (${it.turnsLeft})" },
            slotsLevel1 = player.availableSlots(1),
            maxSlotsLevel1 = player.maxSlots(1),
            slotsLevel2 = player.availableSlots(2),
            maxSlotsLevel2 = player.maxSlots(2),
            secondWindAvailable = player.secondWindAvailable,
            isFighter = player.heroClass == HeroClass.FIGHTER,
            hasRangedWeapon = player.weapon.ranged,
            score = engine.state.score(),
            monstersKilled = engine.state.monstersKilled,
        )

        val inventory = player.inventory.mapIndexed { index, item -> item.toEntry(index) }

        val spells = player.knownSpells().map { spell ->
            val slots = if (spell.level == 0) Int.MAX_VALUE else player.availableSlots(spell.level)
            SpellEntry(
                spell = spell,
                castable = spell.level == 0 || slots > 0,
                detail = if (spell.level == 0) "Trucchetto" else "Livello ${spell.level} - slot $slots",
            )
        }

        val targets = engine.visibleMonsters().map { monster ->
            TargetEntry(
                id = monster.id,
                label = monster.name,
                distance = player.distanceTo(monster),
                hitPoints = monster.hitPoints,
                maxHitPoints = monster.maxHitPoints,
            )
        }

        return GameUiState(
            map = MapSnapshot(level.width, level.height, glyphs, kinds, visible, explored, player.x, player.y),
            hud = hud,
            log = engine.state.log.last(30),
            inventory = inventory,
            spells = spells,
            targets = targets,
            onStairsDown = level.tileAt(player.x, player.y) == TileType.STAIRS_DOWN,
            onItem = level.itemsAt(player.x, player.y).isNotEmpty(),
            rejection = rejection,
        )
    }

    private fun Item.toEntry(index: Int): InventoryEntry = when (this) {
        is Weapon -> InventoryEntry(index, displayName, "Arma ${damage}${if (ranged) ", gittata $range" else ""}", "Impugna")
        is Armor -> InventoryEntry(index, displayName, "Armatura CA base $baseArmorClass", "Indossa")
        is Shield -> InventoryEntry(index, name, "Scudo +$armorClassBonus CA", "Imbraccia")
        is Potion -> InventoryEntry(index, name, "Pozione", "Bevi")
        is Scroll -> InventoryEntry(index, name, "Pergamena", "Leggi")
        else -> InventoryEntry(index, name, "Valore $value mo", "Usa")
    }
}
