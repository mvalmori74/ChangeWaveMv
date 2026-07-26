package com.changewave.scorch.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

class Tank(
    val id: Int,
    val name: String,
    val color: Int,
    val isHuman: Boolean
) {
    companion object {
        const val HALF_W = 17f
        const val BODY_H = 11f
        const val TURRET_LEN = 26f
        const val HIT_RADIUS = 19f
        const val MAX_HEALTH = 100f
        const val MAX_POWER = 1000f
        const val MAX_FUEL = 100f
    }

    var x = 0f
    var y = 0f
    var vy = 0f
    var falling = false
    var fallStartY = 0f

    /** Gradi: 0 = destra, 90 = su, 180 = sinistra. */
    var angle = 45f
    var power = 550f
    var health = MAX_HEALTH
    var shield = 0f
    var fuel = MAX_FUEL
    var alive = true

    var money = 0
    var score = 0
    var roundsWon = 0

    var selectedWeaponId = Weapons.BABY_MISSILE.id
    val inventory = HashMap<Int, Int>()

    /** Traiettoria dell'ultimo colpo, disegnata in trasparenza come riferimento. */
    val lastShotTrail = ArrayList<Float>()

    /** Memoria dell'IA: (bersaglioId -> errore orizzontale dell'ultimo tiro). */
    val aiMemory = HashMap<Int, Float>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPath = Path()
    private val rect = RectF()

    val selectedWeapon: Weapon
        get() = Weapons.byId(selectedWeaponId)

    fun ammo(w: Weapon): Int = if (w.unlimited) Int.MAX_VALUE else (inventory[w.id] ?: 0)

    fun hasAmmo(w: Weapon): Boolean = w.unlimited || ammo(w) > 0

    fun consumeAmmo(w: Weapon) {
        if (w.unlimited) return
        val left = (inventory[w.id] ?: 0) - 1
        if (left <= 0) inventory.remove(w.id) else inventory[w.id] = left
    }

    fun addAmmo(w: Weapon, count: Int) {
        if (w.unlimited) return
        inventory[w.id] = (inventory[w.id] ?: 0) + count
    }

    /** Armi effettivamente selezionabili in questo turno. */
    fun availableWeapons(): List<Weapon> = Weapons.ALL.filter { hasAmmo(it) }

    fun ensureValidWeapon() {
        if (!hasAmmo(selectedWeapon)) selectedWeaponId = Weapons.BABY_MISSILE.id
    }

    fun cycleWeapon(dir: Int) {
        val list = availableWeapons()
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == selectedWeaponId }
        val next = ((if (idx < 0) 0 else idx) + dir + list.size) % list.size
        selectedWeaponId = list[next].id
    }

    fun resetForRound() {
        health = MAX_HEALTH
        shield = 0f
        fuel = MAX_FUEL
        alive = true
        vy = 0f
        falling = false
        angle = if (x < 800f) 55f else 125f
        power = 550f
        lastShotTrail.clear()
        aiMemory.clear()
        ensureValidWeapon()
    }

    /** Danno: prima consuma lo scudo, poi la struttura. Ritorna il danno inflitto allo scafo. */
    fun applyDamage(amount: Float): Float {
        if (!alive || amount <= 0f) return 0f
        var remaining = amount
        if (shield > 0f) {
            val absorbed = minOf(shield, remaining)
            shield -= absorbed
            remaining -= absorbed
        }
        if (remaining <= 0f) return 0f
        val before = health
        health -= remaining
        if (health <= 0f) {
            health = 0f
            alive = false
        }
        return before - health
    }

    fun muzzleX(): Float = x + cos(Math.toRadians(angle.toDouble())).toFloat() * TURRET_LEN
    fun muzzleY(): Float = y - BODY_H - sin(Math.toRadians(angle.toDouble())).toFloat() * TURRET_LEN

    fun draw(canvas: Canvas, active: Boolean) {
        if (!alive) {
            // relitto annerito
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(40, 36, 34)
            rect.set(x - HALF_W, y - BODY_H * 0.6f, x + HALF_W, y)
            canvas.drawRoundRect(rect, 4f, 4f, paint)
            return
        }

        // scudo
        if (shield > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.argb((70 + shield.coerceAtMost(100f)).toInt().coerceIn(0, 200), 120, 220, 255)
            canvas.drawCircle(x, y - BODY_H * 0.8f, HIT_RADIUS + 9f, paint)
        }

        // cingoli
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(38, 38, 42)
        rect.set(x - HALF_W, y - 5f, x + HALF_W, y)
        canvas.drawRoundRect(rect, 3f, 3f, paint)

        // scafo
        paint.color = color
        bodyPath.reset()
        bodyPath.moveTo(x - HALF_W + 2f, y - 5f)
        bodyPath.lineTo(x - HALF_W + 6f, y - BODY_H)
        bodyPath.lineTo(x + HALF_W - 6f, y - BODY_H)
        bodyPath.lineTo(x + HALF_W - 2f, y - 5f)
        bodyPath.close()
        canvas.drawPath(bodyPath, paint)

        // torretta
        paint.color = lighten(color, 0.25f)
        canvas.drawCircle(x, y - BODY_H, 7.5f, paint)

        // cannone
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = lighten(color, 0.45f)
        canvas.drawLine(x, y - BODY_H, muzzleX(), muzzleY(), paint)

        // barra vita
        val w = 34f
        val hx = x - w / 2f
        val hy = y - BODY_H - 20f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 10, 12, 20)
        rect.set(hx - 1f, hy - 1f, hx + w + 1f, hy + 5f)
        canvas.drawRect(rect, paint)
        paint.color = healthColor()
        rect.set(hx, hy, hx + w * (health / MAX_HEALTH), hy + 4f)
        canvas.drawRect(rect, paint)

        if (active) {
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 214, 120)
            val ty = y - BODY_H - 32f
            bodyPath.reset()
            bodyPath.moveTo(x, ty + 9f)
            bodyPath.lineTo(x - 6f, ty)
            bodyPath.lineTo(x + 6f, ty)
            bodyPath.close()
            canvas.drawPath(bodyPath, paint)
        }
    }

    private fun healthColor(): Int {
        val t = health / MAX_HEALTH
        return when {
            t > 0.6f -> Color.rgb(110, 220, 120)
            t > 0.3f -> Color.rgb(240, 200, 90)
            else -> Color.rgb(235, 90, 80)
        }
    }

    private fun lighten(c: Int, f: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt().coerceIn(0, 255),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt().coerceIn(0, 255)
    )
}
