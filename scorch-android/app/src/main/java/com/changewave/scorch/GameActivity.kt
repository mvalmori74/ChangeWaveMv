package com.changewave.scorch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.changewave.scorch.game.AiShopper
import com.changewave.scorch.game.GameSettings
import com.changewave.scorch.game.GameWorld
import com.changewave.scorch.game.Tank
import com.changewave.scorch.net.BluetoothLink
import com.changewave.scorch.net.NetSession
import com.changewave.scorch.net.Protocol
import com.changewave.scorch.ui.GameView
import com.changewave.scorch.ui.ShopDialog
import kotlin.random.Random

class GameActivity : AppCompatActivity(), GameWorld.Listener, BluetoothLink.Listener {

    companion object {
        const val EXTRA_SETTINGS = "settings"
    }

    private lateinit var view: GameView
    private lateinit var settings: GameSettings
    private val rnd = Random(System.nanoTime())
    private var dialogOpen = false

    // --- partita Bluetooth
    private val net: BluetoothLink? get() = if (settings.isNetworkGame) NetSession.link else null
    private var localShopDone = false
    private var remoteShopDone = false
    private var waitingDialog: AlertDialog? = null
    private var disconnectShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = intentSettings()
        view = GameView(this, settings, this)
        view.setMenuAction { runOnUiThread { showPauseDialog() } }
        setContentView(view)

        if (settings.isNetworkGame) attachNetwork()

        goFullscreen()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showPauseDialog()
            }
        })
    }

    private fun intentSettings(): GameSettings {
        val fromIntent: GameSettings? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_SETTINGS, GameSettings::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SETTINGS)
        }
        return fromIntent ?: GameSettings()
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    override fun onResume() {
        super.onResume()
        if (!dialogOpen) view.world.paused = false
        view.start()
    }

    override fun onPause() {
        super.onPause()
        view.world.paused = true
    }

    override fun onDestroy() {
        super.onDestroy()
        view.stop()
        if (settings.isNetworkGame && isFinishing) NetSession.close()
    }

    // ------------------------------------------------------------ rete

    private fun attachNetwork() {
        val world = view.world
        world.onLocalTurn = { action -> net?.send(Protocol.TURN, Protocol.encodeTurn(action)) }
        world.onStateSync = { snapshot -> net?.send(Protocol.SNAPSHOT, Protocol.encodeSnapshot(snapshot)) }
        NetSession.link?.listener = this
    }

    override fun onConnected(peerName: String) = Unit

    override fun onPacket(packet: Protocol.Packet) {
        val world = view.world
        when (packet.type) {
            Protocol.TURN -> world.submitRemoteTurn(Protocol.decodeTurn(packet.payload))

            // lo stato autorevole arriva dall'host: chi ospita ignora eventuali rimbalzi
            Protocol.SNAPSHOT -> if (!settings.isHost) {
                world.submitSnapshot(Protocol.decodeSnapshot(packet.payload))
            }

            Protocol.SHOP -> {
                world.submitRemoteShop(Protocol.decodeTankState(packet.payload))
                runOnUiThread {
                    remoteShopDone = true
                    maybeAdvanceRound()
                }
            }
        }
    }

    override fun onDisconnected(reason: String) {
        runOnUiThread { showDisconnected(reason) }
    }

    private fun showDisconnected(reason: String) {
        if (disconnectShown || isFinishing) return
        disconnectShown = true
        dialogOpen = true
        view.world.paused = true
        waitingDialog?.dismiss()
        waitingDialog = null
        AlertDialog.Builder(this, R.style.Theme_Scorch_Dialog)
            .setTitle("Collegamento perso")
            .setMessage(if (reason.isEmpty()) "L'avversario ha lasciato la partita." else reason)
            .setCancelable(false)
            .setPositiveButton("Esci") { d, _ ->
                d.dismiss()
                finish()
            }
            .show()
    }

    /** Il round successivo parte solo quando entrambi hanno chiuso il negozio. */
    private fun maybeAdvanceRound() {
        if (!localShopDone || !remoteShopDone) return
        waitingDialog?.dismiss()
        waitingDialog = null
        localShopDone = false
        remoteShopDone = false
        val world = view.world
        world.startNextRound()
        dialogOpen = false
        world.paused = false
    }

    private fun showWaitingDialog() {
        if (isFinishing || waitingDialog != null) return
        waitingDialog = AlertDialog.Builder(this, R.style.Theme_Scorch_Dialog)
            .setTitle("Negozio")
            .setMessage("In attesa che l'avversario finisca gli acquisti…")
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    // ------------------------------------------------------------ listener

    override fun onRoundFinished(world: GameWorld, lastRound: Boolean) {
        runOnUiThread {
            world.paused = true
            dialogOpen = true
            showScoreboard(world, "Fine round ${world.round}") {
                if (settings.isNetworkGame) {
                    localShopDone = false
                    val mine = world.tanks.getOrNull(settings.localPlayerIndex)
                    if (mine == null) {
                        localShopDone = true
                        maybeAdvanceRound()
                    } else {
                        ShopDialog.show(this, mine) {
                            world.localTankState()?.let {
                                net?.send(Protocol.SHOP, Protocol.encodeTankState(it))
                            }
                            localShopDone = true
                            if (!remoteShopDone) showWaitingDialog()
                            maybeAdvanceRound()
                        }
                    }
                } else {
                    runAiShopping(world)
                    shopForHumans(world, 0) {
                        world.startNextRound()
                        dialogOpen = false
                        world.paused = false
                    }
                }
            }
        }
    }

    override fun onGameFinished(world: GameWorld) {
        runOnUiThread {
            world.paused = true
            dialogOpen = true
            showFinalDialog(world)
        }
    }

    // ------------------------------------------------------------ dialogs

    private fun runAiShopping(world: GameWorld) {
        for (t in world.tanks) {
            if (!t.isHuman) AiShopper.spend(t, settings.difficulty, rnd)
        }
    }

    private fun shopForHumans(world: GameWorld, index: Int, done: () -> Unit) {
        val humans = world.tanks.filter { it.isHuman }
        if (index >= humans.size) {
            done()
            return
        }
        ShopDialog.show(this, humans[index]) {
            shopForHumans(world, index + 1, done)
        }
    }

    private fun showScoreboard(world: GameWorld, title: String, onClose: () -> Unit) {
        AlertDialog.Builder(this, R.style.Theme_Scorch_Dialog)
            .setTitle(title)
            .setMessage(scoreText(world))
            .setCancelable(false)
            .setPositiveButton("Al negozio") { d, _ -> d.dismiss() }
            .create()
            .also { dlg ->
                dlg.setOnDismissListener { onClose() }
                dlg.show()
            }
    }

    private fun showFinalDialog(world: GameWorld) {
        val standings = world.standings()
        val champion = standings.firstOrNull()
        val builder = AlertDialog.Builder(this, R.style.Theme_Scorch_Dialog)
            .setTitle(if (champion != null) "Vince ${champion.name}!" else "Partita conclusa")
            .setMessage(scoreText(world))
            .setCancelable(false)
        // in rete la rivincita richiederebbe un nuovo accordo fra i due telefoni
        if (!settings.isNetworkGame) {
            builder.setPositiveButton("Rivincita") { d, _ ->
                d.dismiss()
                restart()
            }
        }
        builder.setNegativeButton("Menu") { d, _ ->
            d.dismiss()
            finish()
        }
        builder.show()
    }

    private fun showPauseDialog() {
        if (dialogOpen) return
        dialogOpen = true
        view.world.paused = true
        view.hud.closePanels()
        view.hud.clearPointers()

        AlertDialog.Builder(this, R.style.Theme_Scorch_Dialog)
            .setTitle("Pausa")
            .setMessage(scoreText(view.world))
            .setCancelable(false)
            .setPositiveButton("Riprendi") { d, _ ->
                d.dismiss()
                dialogOpen = false
                view.world.paused = false
                goFullscreen()
            }
            .setNegativeButton("Esci al menu") { d, _ ->
                d.dismiss()
                finish()
            }
            .show()
    }

    private fun restart() {
        val i = Intent(this, GameActivity::class.java)
        i.putExtra(EXTRA_SETTINGS, settings)
        finish()
        startActivity(i)
    }

    private fun scoreText(world: GameWorld): String {
        val sb = StringBuilder()
        sb.append("Vento: ")
            .append(if (world.settings.windEnabled) "${(world.wind * 100).toInt()}" else "assente")
            .append("\n\n")
        for (t: Tank in world.standings()) {
            sb.append(t.name)
                .append("  ·  round vinti ").append(t.roundsWon)
                .append("  ·  punti ").append(t.score)
                .append("  ·  $").append(t.money)
                .append(if (t.alive) "" else "  (distrutto)")
                .append('\n')
        }
        return sb.toString()
    }
}
