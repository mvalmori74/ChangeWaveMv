package com.changewave.scorch.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.changewave.scorch.game.ShopItem
import com.changewave.scorch.game.Tank
import com.changewave.scorch.game.Weapon
import com.changewave.scorch.game.WeaponType
import com.changewave.scorch.game.Weapons

/** Negozio fra un round e l'altro, costruito a codice per restare indipendente dai layout. */
object ShopDialog {

    fun show(activity: Activity, tank: Tank, onClose: () -> Unit) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
            setBackgroundColor(Color.rgb(16, 22, 40))
        }

        val money = TextView(activity).apply {
            textSize = 18f
            setTextColor(Color.rgb(255, 200, 120))
        }
        root.addView(money)

        val hint = TextView(activity).apply {
            textSize = 12f
            setTextColor(Color.rgb(150, 165, 195))
            text = "Ogni acquisto aggiunge un pacchetto di munizioni all'arsenale."
        }
        root.addView(hint)

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(activity).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(320)
            )
        }
        root.addView(scroll)

        val rows = ArrayList<() -> Unit>()

        fun refresh() {
            money.text = "${tank.name} · disponibili $${tank.money}  |  HP ${tank.health.toInt()}  Scudo ${tank.shield.toInt()}"
            rows.forEach { it() }
        }

        fun sectionTitle(title: String) {
            list.addView(TextView(activity).apply {
                text = title
                textSize = 14f
                setTextColor(Color.rgb(255, 179, 71))
                setPadding(0, dp(10), 0, dp(4))
            })
        }

        fun addRow(name: String, detail: () -> String, cost: Int, canBuy: () -> Boolean, buy: () -> Unit) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setGravity(Gravity.CENTER_VERTICAL)
                setPadding(0, dp(4), 0, dp(4))
            }
            val label = TextView(activity).apply {
                textSize = 14f
                setTextColor(Color.rgb(230, 238, 255))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val button = Button(activity).apply {
                text = "$$cost"
                setAllCaps(false)
            }
            button.setOnClickListener {
                if (canBuy()) {
                    buy()
                    refresh()
                }
            }
            row.addView(label)
            row.addView(button)
            list.addView(row)

            rows.add {
                label.text = "$name — ${detail()}"
                val ok = canBuy()
                button.isEnabled = ok
                button.alpha = if (ok) 1f else 0.45f
            }
        }

        sectionTitle("ARMI")
        for (w: Weapon in Weapons.PURCHASABLE) {
            addRow(
                name = w.name,
                detail = {
                    val extra = when (w.type) {
                        WeaponType.DIRT -> "riempie il terreno"
                        WeaponType.MIRV -> "${w.childCount} testate"
                        WeaponType.CLUSTER -> "${w.childCount} frammenti"
                        WeaponType.ROLLER -> "rotola in discesa"
                        WeaponType.DIGGER -> "scava nel terreno"
                        else -> "danno ${w.damage.toInt()}"
                    }
                    "x${w.packSize} · raggio ${w.radius.toInt()} · $extra · possiedi ${tank.ammo(w)}"
                },
                cost = w.cost,
                canBuy = { tank.money >= w.cost },
                buy = {
                    tank.money -= w.cost
                    tank.addAmmo(w, w.packSize)
                }
            )
        }

        sectionTitle("EQUIPAGGIAMENTO")
        for (item in ShopItem.values()) {
            addRow(
                name = item.label,
                detail = {
                    when (item) {
                        ShopItem.SHIELD -> "scudo attuale ${tank.shield.toInt()}"
                        ShopItem.REPAIR -> "struttura ${tank.health.toInt()}/100"
                        ShopItem.FUEL -> "carburante ${tank.fuel.toInt()}/100"
                    }
                },
                cost = item.cost,
                canBuy = { tank.money >= item.cost },
                buy = {
                    tank.money -= item.cost
                    when (item) {
                        ShopItem.SHIELD -> tank.shield = (tank.shield + item.amount).coerceAtMost(200f)
                        ShopItem.REPAIR -> tank.health = (tank.health + item.amount).coerceAtMost(Tank.MAX_HEALTH)
                        ShopItem.FUEL -> tank.fuel = (tank.fuel + item.amount).coerceAtMost(Tank.MAX_FUEL)
                    }
                }
            )
        }

        refresh()

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Negozio — ${tank.name}")
            .setView(root as View)
            .setCancelable(false)
            .setPositiveButton("Pronto") { dlg, _ -> dlg.dismiss() }
            .create()

        dialog.setOnDismissListener { onClose() }
        dialog.show()
    }
}
