package com.changewave.scorch.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * IA di tiro: risolve la balistica (gravita' + vento), verifica la traiettoria contro il
 * terreno simulandola e infine sporca la soluzione in base alla difficolta'.
 */
class AiBrain(private val difficulty: Difficulty, private val rnd: Random) {

    data class Shot(val angle: Float, val power: Float, val weaponId: Int)

    fun chooseTarget(world: GameWorld, me: Tank): Tank? {
        val enemies = world.tanks.filter { it.alive && it.id != me.id }
        if (enemies.isEmpty()) return null
        return enemies.minByOrNull { t ->
            val d = abs(t.x - me.x)
            // preferisce i bersagli deboli e vicini
            d * 0.6f + t.health * 6f + (t.shield * 4f)
        }
    }

    fun plan(world: GameWorld, me: Tank): Shot {
        val target = chooseTarget(world, me) ?: return Shot(me.angle, me.power, me.selectedWeaponId)
        val weapon = chooseWeapon(me, target)

        val originX = me.muzzleX()
        val originY = me.muzzleY()
        val dx = target.x - originX
        val dy = (target.y - Tank.BODY_H * 0.5f) - originY

        var best: Shot? = null
        var bestScore = Float.MAX_VALUE

        var flight = 0.45f
        while (flight <= 6.5f) {
            val vx = (dx - 0.5f * world.windAccel * flight * flight) / flight
            val vy = (dy - 0.5f * GameWorld.GRAVITY * flight * flight) / flight
            val speed = hypot(vx, vy)
            if (speed <= GameWorld.MAX_SPEED * 0.995f && speed > 40f) {
                val angle = Math.toDegrees(atan2(-vy.toDouble(), vx.toDouble())).toFloat()
                if (angle in 3f..177f) {
                    val miss = simulateMiss(world, me, originX, originY, vx, vy, target)
                    // penalizza le parabole troppo tese (rischio di colpire una collina)
                    val score = miss + abs(flight - 2.2f) * 4f
                    if (score < bestScore) {
                        bestScore = score
                        best = Shot(angle, speed / GameWorld.MAX_SPEED * Tank.MAX_POWER, weapon.id)
                    }
                }
            }
            flight += 0.12f
        }

        val raw = best ?: fallbackShot(me, target, weapon)
        return blur(raw, target, me)
    }

    /** Simula il tiro e restituisce la distanza dal bersaglio del punto di impatto. */
    private fun simulateMiss(
        world: GameWorld,
        me: Tank,
        startX: Float,
        startY: Float,
        vx0: Float,
        vy0: Float,
        target: Tank
    ): Float {
        var x = startX
        var y = startY
        var vx = vx0
        var vy = vy0
        val h = 1f / 60f
        var t = 0f
        while (t < 12f) {
            vx += world.windAccel * h
            vy += GameWorld.GRAVITY * h
            x += vx * h
            y += vy * h
            t += h

            if (x < -80f || x > world.worldWidth + 80f) return 4000f
            if (y > world.worldHeight + 60f) return 3000f

            for (other in world.tanks) {
                if (!other.alive) continue
                if (other.id == me.id && t < 0.15f) continue
                if (hypot(other.x - x, (other.y - Tank.BODY_H) - y) < Tank.HIT_RADIUS) {
                    return if (other.id == target.id) 0f
                    else if (other.id == me.id) 2500f // autogol
                    else hypot(target.x - x, target.y - y) + 120f
                }
            }

            if (world.terrain.isSolid(x, y)) {
                return hypot(target.x - x, (target.y - Tank.BODY_H) - y)
            }
        }
        return 2000f
    }

    private fun fallbackShot(me: Tank, target: Tank, weapon: Weapon): Shot {
        val toRight = target.x > me.x
        val angle = if (toRight) 50f else 130f
        val dist = abs(target.x - me.x)
        val power = (250f + dist * 0.45f).coerceIn(200f, Tank.MAX_POWER)
        return Shot(angle, power, weapon.id)
    }

    /** Introduce l'errore umano/robotico previsto dalla difficolta'. */
    private fun blur(shot: Shot, target: Tank, me: Tank): Shot {
        val prev = me.aiMemory[target.id] ?: 0f
        // "adapt" riduce l'errore quando l'IA ha gia' tirato su questo bersaglio
        val learn = if (prev != 0f) (1f - difficulty.adapt * 0.6f) else 1f
        val angle = shot.angle + gauss() * difficulty.angleError * learn
        val power = shot.power * (1f + gauss() * difficulty.powerError * learn)
        return Shot(
            angle.coerceIn(2f, 178f),
            power.coerceIn(120f, Tank.MAX_POWER),
            shot.weaponId
        )
    }

    private fun gauss(): Float {
        // Somma di uniformi: distribuzione a campana sufficiente per il gioco.
        var s = 0f
        repeat(3) { s += rnd.nextFloat() * 2f - 1f }
        return s / 1.7f
    }

    private fun chooseWeapon(me: Tank, target: Tank): Weapon {
        val owned = me.availableWeapons().filter { it.type != WeaponType.DIRT }
        if (owned.isEmpty()) return Weapons.BABY_MISSILE

        val strongest = owned.maxByOrNull { Weapons.punch(it) } ?: Weapons.BABY_MISSILE
        val effective = target.health + target.shield

        val aggression = when (difficulty) {
            Difficulty.ROOKIE -> 0.20f
            Difficulty.VETERAN -> 0.45f
            Difficulty.CYBORG -> 0.75f
        }

        // usa l'arma pesante per finire un bersaglio o quando decide di forzare
        val finisher = Weapons.punch(strongest) >= effective
        return if (finisher || rnd.nextFloat() < aggression) strongest else Weapons.BABY_MISSILE
    }

    companion object {
        fun velocityFrom(angleDeg: Float, power: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angleDeg.toDouble())
            val speed = power / Tank.MAX_POWER * GameWorld.MAX_SPEED
            return Pair(
                (cos(rad) * speed).toFloat(),
                (-sin(rad) * speed).toFloat()
            )
        }
    }
}
