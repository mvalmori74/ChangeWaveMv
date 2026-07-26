package com.changewave.scorch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.changewave.scorch.game.Difficulty
import com.changewave.scorch.game.GameSettings

class MenuActivity : AppCompatActivity() {

    private lateinit var seekPlayers: SeekBar
    private lateinit var seekHumans: SeekBar
    private lateinit var seekRounds: SeekBar
    private lateinit var seekMoney: SeekBar
    private lateinit var labelPlayers: TextView
    private lateinit var labelHumans: TextView
    private lateinit var labelRounds: TextView
    private lateinit var labelMoney: TextView
    private lateinit var groupDifficulty: RadioGroup
    private lateinit var checkWind: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        seekPlayers = findViewById(R.id.seekPlayers)
        seekHumans = findViewById(R.id.seekHumans)
        seekRounds = findViewById(R.id.seekRounds)
        seekMoney = findViewById(R.id.seekMoney)
        labelPlayers = findViewById(R.id.labelPlayers)
        labelHumans = findViewById(R.id.labelHumans)
        labelRounds = findViewById(R.id.labelRounds)
        labelMoney = findViewById(R.id.labelMoney)
        groupDifficulty = findViewById(R.id.groupDifficulty)
        checkWind = findViewById(R.id.checkWind)

        val watcher = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar === seekPlayers && humanCount() > playerCount()) {
                    seekHumans.progress = playerCount()
                }
                updateLabels()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        seekPlayers.setOnSeekBarChangeListener(watcher)
        seekHumans.setOnSeekBarChangeListener(watcher)
        seekRounds.setOnSeekBarChangeListener(watcher)
        seekMoney.setOnSeekBarChangeListener(watcher)

        updateLabels()

        findViewById<Button>(R.id.buttonStart).setOnClickListener { startGame() }
    }

    private fun playerCount() = seekPlayers.progress + 2       // 2..4
    private fun humanCount() = seekHumans.progress.coerceAtMost(playerCount()) // 0..4
    private fun roundCount() = seekRounds.progress + 1         // 1..10
    private fun startMoney() = 5_000 + seekMoney.progress * 5_000 // 5k..55k

    private fun updateLabels() {
        labelPlayers.text = getString(R.string.players) + ": " + playerCount()
        labelHumans.text = getString(R.string.humans) + ": " + humanCount() +
            " (IA: " + (playerCount() - humanCount()) + ")"
        labelRounds.text = getString(R.string.rounds) + ": " + roundCount()
        labelMoney.text = getString(R.string.start_money) + ": $" + startMoney()
    }

    private fun difficulty(): Difficulty = when (groupDifficulty.checkedRadioButtonId) {
        R.id.diffRookie -> Difficulty.ROOKIE
        R.id.diffCyborg -> Difficulty.CYBORG
        else -> Difficulty.VETERAN
    }

    private fun startGame() {
        val settings = GameSettings(
            playerCount = playerCount(),
            humanCount = humanCount(),
            rounds = roundCount(),
            difficultyOrdinal = difficulty().ordinal,
            windEnabled = checkWind.isChecked,
            startMoney = startMoney()
        )
        val i = Intent(this, GameActivity::class.java)
        i.putExtra(GameActivity.EXTRA_SETTINGS, settings)
        startActivity(i)
    }
}
