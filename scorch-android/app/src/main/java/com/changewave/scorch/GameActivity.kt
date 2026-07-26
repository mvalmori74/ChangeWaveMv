package com.changewave.scorch

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.changewave.scorch.game.AiShopper
import com.changewave.scorch.game.GameSettings
import com.changewave.scorch.game.GameWorld
import com.changewave.scorch.game.Tank
import com.changewave.scorch.ui.GameView
import com.changewave.scorch.ui.ShopDialog
import kotlin.random.Random

class GameActivity : AppCompatActivity(), GameWorld.Listener {

    companion object {
        const val EXTRA_SETTINGS = "settings"
    }

    private lateinit var view: GameView
    private lateinit var settings: GameSettings
    private val rnd = Random(System.nanoTime())
    private var dialogOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = intentSettings()
        view = GameView(this, settings, this)
        view.setMenuAction { runOnUiThread { showPauseDialog() } }
        setContentView(view)

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
    }

    // ------------------------------------------------------------ listener

    override fun onRoundFinished(world: GameWorld, lastRound: Boolean) {
        runOnUiThread {
            world.paused = true
            dialogOpen = true
            showScoreboard(world, "Fine round ${world.round}") {
                runAiShopping(world)
                shopForHumans(world, 0) {
                    world.startNextRound()
                    dialogOpen = false
                    world.paused = false
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
            .setTitle(if (champion != null) "Vince ${champion.name}!" else "Partita conclusa")
            .setMessage(scoreText(world))
            .setCancelable(false)
            .setPositiveButton("Rivincita") { d, _ ->
                d.dismiss()
                restart()
            }
            .setNegativeButton("Menu") { d, _ ->
                d.dismiss()
                finish()
            }
            .show()
    }

    private fun showPauseDialog() {
        if (dialogOpen) return
        dialogOpen = true
        view.world.paused = true
        view.hud.closePanels()
        view.hud.clearPointers()

        AlertDialog.Builder(this)
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
