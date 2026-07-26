package com.changewave.scorch.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.changewave.scorch.game.GameWorld
import com.changewave.scorch.game.Tank
import com.changewave.scorch.game.Weapon
import com.changewave.scorch.game.Weapons
import kotlin.math.abs

/**
 * Controlli touch e pannelli informativi. Tutto viene disegnato in pixel schermo,
 * sopra al mondo di gioco che invece e' in unita' di mondo.
 */
class Hud(private val density: Float) {

    object Id {
        const val NONE = -1
        const val ANGLE_DEC = 1
        const val ANGLE_INC = 2
        const val POWER_DEC = 3
        const val POWER_INC = 4
        const val MOVE_LEFT = 5
        const val MOVE_RIGHT = 6
        const val FIRE = 7
        const val WEAPON = 8
        const val MENU = 9
        const val WEAPON_ROW = 100 // + indice
    }

    private class Button(val id: Int, val label: String) {
        val rect = RectF()
        var enabled = true
    }

    var world: GameWorld? = null
    var onMenu: (() -> Unit)? = null

    private val buttons = ArrayList<Button>()
    private val held = HashMap<Int, Int>() // pointerId -> button id
    private val holdTime = HashMap<Int, Float>() // button id -> tempo di pressione

    private var weaponPanelOpen = false
    private val weaponRows = ArrayList<RectF>()
    private var weaponList: List<Weapon> = emptyList()
    private val panelRect = RectF()

    private var width = 0
    private var height = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val rect = RectF()

    private fun dp(v: Float) = v * density

    init {
        buttons.add(Button(Id.ANGLE_DEC, "◀"))
        buttons.add(Button(Id.ANGLE_INC, "▶"))
        buttons.add(Button(Id.POWER_DEC, "–"))
        buttons.add(Button(Id.POWER_INC, "+"))
        buttons.add(Button(Id.MOVE_LEFT, "◀◀"))
        buttons.add(Button(Id.MOVE_RIGHT, "▶▶"))
        buttons.add(Button(Id.FIRE, "FUOCO"))
        buttons.add(Button(Id.WEAPON, "ARMA"))
        buttons.add(Button(Id.MENU, "☰"))
    }

    fun layout(w: Int, h: Int) {
        width = w
        height = h

        val bs = dp(52f)      // lato pulsanti quadrati
        val gap = dp(8f)
        val bottom = h - dp(14f)

        // colonna sinistra: angolo e potenza
        val leftX = dp(14f)
        find(Id.ANGLE_DEC).rect.set(leftX, bottom - bs, leftX + bs, bottom)
        find(Id.ANGLE_INC).rect.set(leftX + bs + gap, bottom - bs, leftX + bs * 2 + gap, bottom)
        find(Id.POWER_DEC).rect.set(leftX, bottom - bs * 2 - gap, leftX + bs, bottom - bs - gap)
        find(Id.POWER_INC).rect.set(leftX + bs + gap, bottom - bs * 2 - gap, leftX + bs * 2 + gap, bottom - bs - gap)

        // movimento (accanto)
        val moveX = leftX + bs * 2 + gap * 3
        find(Id.MOVE_LEFT).rect.set(moveX, bottom - bs, moveX + bs, bottom)
        find(Id.MOVE_RIGHT).rect.set(moveX + bs + gap, bottom - bs, moveX + bs * 2 + gap, bottom)

        // destra: fuoco e selezione arma
        val fireR = dp(46f)
        val fireCx = w - dp(20f) - fireR
        val fireCy = bottom - fireR
        find(Id.FIRE).rect.set(fireCx - fireR, fireCy - fireR, fireCx + fireR, fireCy + fireR)

        val wBtnW = dp(150f)
        val wBtnH = dp(44f)
        find(Id.WEAPON).rect.set(w - dp(20f) - wBtnW, fireCy - fireR - gap - wBtnH, w - dp(20f), fireCy - fireR - gap)

        find(Id.MENU).rect.set(w - dp(56f), dp(10f), w - dp(12f), dp(54f))

        panelRect.set(w * 0.5f - dp(170f), dp(60f), w * 0.5f + dp(170f), h - dp(60f))
        rebuildWeaponRows()
    }

    private fun find(id: Int): Button = buttons.first { it.id == id }

    private fun rebuildWeaponRows() {
        weaponRows.clear()
        val w = world ?: return
        weaponList = w.currentTank.availableWeapons()
        val rowH = dp(46f)
        var y = panelRect.top + dp(46f)
        for (i in weaponList.indices) {
            if (y + rowH > panelRect.bottom - dp(10f)) break
            weaponRows.add(RectF(panelRect.left + dp(10f), y, panelRect.right - dp(10f), y + rowH - dp(6f)))
            y += rowH
        }
    }

    // ------------------------------------------------------------- input

    fun hitTest(x: Float, y: Float): Int {
        if (weaponPanelOpen) {
            for (i in weaponRows.indices) {
                if (weaponRows[i].contains(x, y)) return Id.WEAPON_ROW + i
            }
            return if (panelRect.contains(x, y)) Id.NONE else Id.WEAPON // tap fuori = chiudi
        }
        for (b in buttons) {
            if (b.rect.contains(x, y)) return b.id
        }
        return Id.NONE
    }

    /** @return true se il tocco e' stato consumato dalla HUD. */
    fun onPointerDown(pointerId: Int, x: Float, y: Float): Boolean {
        val id = hitTest(x, y)
        if (id == Id.NONE) return weaponPanelOpen // pannello aperto: assorbe i tocchi
        held[pointerId] = id
        holdTime[id] = 0f
        triggerOnce(id)
        return true
    }

    fun onPointerUp(pointerId: Int) {
        val id = held.remove(pointerId) ?: return
        holdTime.remove(id)
    }

    fun clearPointers() {
        held.clear()
        holdTime.clear()
    }

    fun isHeld(id: Int): Boolean = held.containsValue(id)

    private fun triggerOnce(id: Int) {
        val w = world ?: return
        when (id) {
            Id.FIRE -> if (!weaponPanelOpen) w.fire()
            Id.MENU -> onMenu?.invoke()
            Id.WEAPON -> {
                weaponPanelOpen = !weaponPanelOpen
                if (weaponPanelOpen) rebuildWeaponRows()
            }
            else -> {
                if (id >= Id.WEAPON_ROW) {
                    val idx = id - Id.WEAPON_ROW
                    weaponList.getOrNull(idx)?.let { w.selectWeapon(it.id) }
                    weaponPanelOpen = false
                }
            }
        }
    }

    /** Ripetizione automatica dei pulsanti tenuti premuti. */
    fun update(dt: Float) {
        val w = world ?: return
        for (id in held.values.toList()) {
            val t = (holdTime[id] ?: 0f) + dt
            holdTime[id] = t
            val boost = 1f + (t * 2.2f).coerceAtMost(6f)
            when (id) {
                Id.ANGLE_DEC -> w.adjustAngle(26f * dt * boost)
                Id.ANGLE_INC -> w.adjustAngle(-26f * dt * boost)
                Id.POWER_DEC -> w.adjustPower(-140f * dt * boost)
                Id.POWER_INC -> w.adjustPower(140f * dt * boost)
                Id.MOVE_LEFT -> w.moveTank(-1, dt)
                Id.MOVE_RIGHT -> w.moveTank(1, dt)
            }
        }
    }

    fun closePanels() {
        weaponPanelOpen = false
    }

    val panelOpen: Boolean get() = weaponPanelOpen

    // ------------------------------------------------------------- draw

    fun draw(canvas: Canvas) {
        val w = world ?: return
        val t = w.currentTank

        drawTopBar(canvas, w, t)
        drawControls(canvas, w, t)
        if (weaponPanelOpen) drawWeaponPanel(canvas, t)
        drawBanner(canvas, w)
    }

    private fun drawTopBar(canvas: Canvas, w: GameWorld, t: Tank) {
        val h = dp(64f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 8, 12, 24)
        canvas.drawRect(0f, 0f, width.toFloat(), h, paint)

        // giocatore corrente
        text.textAlign = Paint.Align.LEFT
        text.textSize = dp(16f)
        text.color = t.color
        canvas.drawText(t.name, dp(14f), dp(24f), text)

        text.textSize = dp(12f)
        text.color = Color.rgb(200, 212, 236)
        canvas.drawText(
            "HP ${t.health.toInt()}   Scudo ${t.shield.toInt()}   Carb. ${t.fuel.toInt()}   $${t.money}",
            dp(14f), dp(44f), text
        )

        // round
        text.textAlign = Paint.Align.CENTER
        text.textSize = dp(14f)
        text.color = Color.rgb(255, 200, 120)
        canvas.drawText("ROUND ${w.round}/${w.settings.rounds}", width * 0.5f, dp(22f), text)

        // vento
        val windX = width * 0.5f
        val windY = dp(42f)
        val windPx = dp(60f) * w.wind
        paint.color = Color.argb(90, 255, 255, 255)
        paint.strokeWidth = dp(2f)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(windX - dp(60f), windY, windX + dp(60f), windY, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (abs(w.wind) < 0.05f) Color.rgb(150, 160, 180) else Color.rgb(120, 220, 255)
        canvas.drawCircle(windX + windPx, windY, dp(5f), paint)
        text.textSize = dp(10f)
        text.color = Color.rgb(170, 185, 210)
        canvas.drawText(
            if (abs(w.wind) < 0.05f) "vento assente"
            else "vento " + (abs(w.wind) * 100).toInt() + (if (w.wind > 0) " →" else " ←"),
            windX, dp(58f), text
        )

        // angolo / potenza
        text.textAlign = Paint.Align.RIGHT
        text.textSize = dp(15f)
        text.color = Color.rgb(235, 240, 255)
        canvas.drawText("ANG ${t.angle.toInt()}°", width - dp(70f), dp(26f), text)
        canvas.drawText("POT ${t.power.toInt()}", width - dp(70f), dp(48f), text)

        // pulsante menu
        drawButton(canvas, find(Id.MENU), true, Color.argb(120, 40, 52, 84))
    }

    private fun drawControls(canvas: Canvas, w: GameWorld, t: Tank) {
        val active = w.waitingForHumanInput
        val accent = Color.rgb(255, 179, 71)

        for (b in buttons) {
            if (b.id == Id.MENU) continue
            when (b.id) {
                Id.FIRE -> {
                    val enabled = active
                    paint.style = Paint.Style.FILL
                    paint.color = if (enabled) Color.argb(235, 200, 60, 45) else Color.argb(110, 70, 70, 78)
                    canvas.drawCircle(b.rect.centerX(), b.rect.centerY(), b.rect.width() / 2f, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(3f)
                    paint.color = if (enabled) Color.argb(255, 255, 190, 140) else Color.argb(120, 120, 120, 130)
                    canvas.drawCircle(b.rect.centerX(), b.rect.centerY(), b.rect.width() / 2f - dp(4f), paint)
                    text.textAlign = Paint.Align.CENTER
                    text.textSize = dp(17f)
                    text.color = if (enabled) Color.WHITE else Color.argb(150, 220, 220, 220)
                    canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + dp(6f), text)
                }

                Id.WEAPON -> {
                    drawButton(canvas, b, active, Color.argb(190, 26, 34, 58))
                    val weapon = t.selectedWeapon
                    text.textAlign = Paint.Align.CENTER
                    text.textSize = dp(13f)
                    text.color = if (active) accent else Color.argb(150, 200, 200, 210)
                    canvas.drawText(weapon.name, b.rect.centerX(), b.rect.centerY() - dp(1f), text)
                    text.textSize = dp(11f)
                    text.color = Color.rgb(180, 195, 220)
                    val ammo = if (weapon.unlimited) "∞" else t.ammo(weapon).toString()
                    canvas.drawText("munizioni: $ammo", b.rect.centerX(), b.rect.centerY() + dp(14f), text)
                }

                else -> {
                    val isMove = b.id == Id.MOVE_LEFT || b.id == Id.MOVE_RIGHT
                    val enabled = active && (!isMove || t.fuel > 0f)
                    drawButton(canvas, b, enabled, Color.argb(170, 22, 30, 52))
                    text.textAlign = Paint.Align.CENTER
                    text.textSize = dp(18f)
                    text.color = if (enabled) Color.rgb(235, 242, 255) else Color.argb(120, 200, 200, 210)
                    canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + dp(6f), text)
                }
            }
        }

        // etichette sotto ai gruppi
        text.textAlign = Paint.Align.LEFT
        text.textSize = dp(10f)
        text.color = Color.argb(180, 180, 195, 220)
        val ang = find(Id.ANGLE_DEC).rect
        canvas.drawText("ANGOLO", ang.left, ang.top - dp(4f), text)
        val pow = find(Id.POWER_DEC).rect
        canvas.drawText("POTENZA", pow.left, pow.top - dp(4f), text)
        val mv = find(Id.MOVE_LEFT).rect
        canvas.drawText("MOVIMENTO (carb. ${t.fuel.toInt()})", mv.left, mv.top - dp(4f), text)

        // barra potenza
        val bar = RectF(
            find(Id.POWER_DEC).rect.left,
            find(Id.POWER_DEC).rect.top - dp(20f),
            find(Id.POWER_INC).rect.right,
            find(Id.POWER_DEC).rect.top - dp(12f)
        )
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(120, 10, 14, 26)
        canvas.drawRect(bar, paint)
        paint.color = Color.rgb(255, 179, 71)
        rect.set(bar.left, bar.top, bar.left + bar.width() * (t.power / Tank.MAX_POWER), bar.bottom)
        canvas.drawRect(rect, paint)
    }

    private fun drawButton(canvas: Canvas, b: Button, enabled: Boolean, bg: Int) {
        paint.style = Paint.Style.FILL
        paint.color = if (enabled) bg else Color.argb(90, 40, 44, 56)
        canvas.drawRoundRect(b.rect, dp(10f), dp(10f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = if (isHeld(b.id)) Color.rgb(255, 200, 120) else Color.argb(110, 160, 180, 220)
        canvas.drawRoundRect(b.rect, dp(10f), dp(10f), paint)
        paint.style = Paint.Style.FILL
        if (b.id == Id.MENU) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = dp(18f)
            text.color = Color.rgb(230, 238, 255)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + dp(6f), text)
        }
    }

    private fun drawWeaponPanel(canvas: Canvas, t: Tank) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(200, 6, 10, 20)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = Color.argb(245, 20, 27, 46)
        canvas.drawRoundRect(panelRect, dp(14f), dp(14f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.rgb(255, 179, 71)
        canvas.drawRoundRect(panelRect, dp(14f), dp(14f), paint)
        paint.style = Paint.Style.FILL

        text.textAlign = Paint.Align.CENTER
        text.textSize = dp(16f)
        text.color = Color.rgb(255, 200, 120)
        canvas.drawText("ARSENALE", panelRect.centerX(), panelRect.top + dp(28f), text)

        for (i in weaponRows.indices) {
            val w = weaponList.getOrNull(i) ?: continue
            val r = weaponRows[i]
            paint.color = if (w.id == t.selectedWeaponId) Color.argb(255, 52, 66, 104) else Color.argb(255, 30, 38, 62)
            canvas.drawRoundRect(r, dp(8f), dp(8f), paint)

            paint.color = w.color
            canvas.drawCircle(r.left + dp(20f), r.centerY(), dp(8f), paint)

            text.textAlign = Paint.Align.LEFT
            text.textSize = dp(14f)
            text.color = Color.rgb(238, 244, 255)
            canvas.drawText(w.name, r.left + dp(38f), r.centerY() + dp(5f), text)

            text.textAlign = Paint.Align.RIGHT
            text.textSize = dp(13f)
            text.color = Color.rgb(180, 200, 235)
            val ammo = if (w.unlimited) "∞" else "x" + t.ammo(w)
            canvas.drawText(ammo, r.right - dp(14f), r.centerY() + dp(5f), text)
        }

        text.textAlign = Paint.Align.CENTER
        text.textSize = dp(11f)
        text.color = Color.argb(190, 180, 195, 220)
        canvas.drawText("tocca fuori dal pannello per chiudere", panelRect.centerX(), panelRect.bottom - dp(14f), text)
    }

    private fun drawBanner(canvas: Canvas, w: GameWorld) {
        if (w.bannerTimer <= 0f || w.banner.isEmpty()) return
        val alpha = (w.bannerTimer.coerceAtMost(0.6f) / 0.6f * 235f).toInt().coerceIn(0, 255)
        text.textAlign = Paint.Align.CENTER
        text.textSize = dp(30f)
        text.color = Color.argb(alpha, 255, 214, 140)
        canvas.drawText(w.banner, width * 0.5f, height * 0.34f, text)
    }

    /** L'area di gioco utile per la mira col dito (esclude le zone dei comandi). */
    fun isPlayArea(x: Float, y: Float): Boolean {
        if (weaponPanelOpen) return false
        if (y < dp(64f)) return false
        for (b in buttons) {
            if (b.rect.contains(x, y)) return false
        }
        // margine attorno ai gruppi di pulsanti
        val bottomZone = height - dp(96f)
        if (y > bottomZone && (x < dp(260f) || x > width - dp(200f))) return false
        return true
    }

    fun weaponsChanged() {
        if (weaponPanelOpen) rebuildWeaponRows()
    }

    @Suppress("unused")
    fun purchasableCount(): Int = Weapons.PURCHASABLE.size
}
