package com.changewave.scorch.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Stato e logica del gioco: turni, balistica, distruzione del terreno, IA, punteggi.
 * Tutte le coordinate sono in unita' di mondo (larghezza fissa 1600) e vengono scalate
 * a schermo dalla GameView, cosi' la fisica e' identica su ogni dispositivo.
 */
class GameWorld(val settings: GameSettings, private val listener: Listener) {

    companion object {
        const val COLUMNS = 1600
        const val WORLD_WIDTH = 1600f
        const val GRAVITY = 700f
        const val MAX_SPEED = 1500f
        const val WIND_ACCEL = 140f
        const val MOVE_SPEED = 70f
        const val KILL_BONUS = 6_000
        const val SURVIVOR_BONUS = 8_000
        const val MONEY_PER_DAMAGE = 60
    }

    interface Listener {
        /** Fine round: la partita e' in pausa finche' non viene chiamato [startNextRound]. */
        fun onRoundFinished(world: GameWorld, lastRound: Boolean)

        /** Fine partita. */
        fun onGameFinished(world: GameWorld)
    }

    enum class State { TURN_START, AIMING, FLYING, SETTLING, ROUND_END, GAME_OVER }

    private data class Palette(
        val skyTop: Int, val skyBottom: Int,
        val hillFar: Int, val hillNear: Int,
        val dirtTop: Int, val dirtBottom: Int, val grass: Int,
        val sun: Int
    )

    private val palettes = listOf(
        Palette(
            Color.rgb(14, 20, 48), Color.rgb(86, 66, 110),
            Color.rgb(38, 40, 78), Color.rgb(26, 28, 58),
            Color.rgb(104, 78, 54), Color.rgb(38, 27, 20), Color.rgb(126, 178, 96),
            Color.rgb(255, 214, 150)
        ),
        Palette(
            Color.rgb(28, 12, 30), Color.rgb(200, 92, 60),
            Color.rgb(80, 40, 58), Color.rgb(52, 26, 40),
            Color.rgb(122, 84, 52), Color.rgb(44, 26, 18), Color.rgb(168, 140, 70),
            Color.rgb(255, 170, 90)
        ),
        Palette(
            Color.rgb(10, 34, 52), Color.rgb(120, 178, 200),
            Color.rgb(58, 92, 108), Color.rgb(38, 66, 82),
            Color.rgb(198, 202, 210), Color.rgb(96, 106, 122), Color.rgb(228, 240, 248),
            Color.rgb(255, 250, 220)
        ),
        Palette(
            Color.rgb(8, 26, 20), Color.rgb(70, 132, 96),
            Color.rgb(30, 66, 52), Color.rgb(20, 46, 38),
            Color.rgb(92, 96, 52), Color.rgb(32, 34, 20), Color.rgb(150, 196, 88),
            Color.rgb(240, 255, 200)
        )
    )

    val worldWidth = WORLD_WIDTH
    var worldHeight = 900f
        private set

    val terrain = Terrain(COLUMNS, worldHeight)
    val tanks = ArrayList<Tank>()
    val projectiles = ArrayList<Projectile>()
    private val explosions = ArrayList<Explosion>()
    private val particles = ArrayList<Particle>()
    private val texts = ArrayList<FloatingText>()

    private val rnd = Random(System.nanoTime())
    private val ai = AiBrain(settings.difficulty, rnd)

    var state = State.TURN_START
        private set
    var round = 1
        private set
    var wind = 0f
        private set
    var currentIndex = 0
        private set
    var banner: String = ""
        private set
    var bannerTimer = 0f
        private set
    /** Impostato dal thread UI (dialog, pausa) e letto dal thread di gioco. */
    @Volatile
    var paused = false

    var shake = 0f
        private set

    private var firstPlayerOfRound = 0
    private var aiThinkTimer = 0f
    private var roundReported = false
    private var palette = palettes[0]

    private val starsX = FloatArray(90)
    private val starsY = FloatArray(90)
    private val starsR = FloatArray(90)
    private val hillFarPath = Path()
    private val hillNearPath = Path()

    private val skyPaint = Paint()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        isFakeBoldText = true
    }
    private val tmpPath = Path()

    val windAccel: Float get() = if (settings.windEnabled) wind * WIND_ACCEL else 0f

    val currentTank: Tank get() = tanks[currentIndex.coerceIn(0, tanks.size - 1)]

    val aliveTanks: List<Tank> get() = tanks.filter { it.alive }

    val waitingForHumanInput: Boolean
        get() = state == State.AIMING && currentTank.isHuman && !paused

    init {
        createTanks()
        startRound(reset = true)
    }

    // ---------------------------------------------------------------- setup

    private fun createTanks() {
        val colors = listOf(
            Color.rgb(240, 90, 80),
            Color.rgb(90, 170, 250),
            Color.rgb(120, 220, 130),
            Color.rgb(245, 200, 90)
        )
        val names = listOf("Rosso", "Blu", "Verde", "Giallo")
        val count = settings.playerCount.coerceIn(2, 4)
        val humans = settings.humanCount.coerceIn(0, count)
        for (i in 0 until count) {
            val human = i < humans
            val label = if (human) names[i] else names[i] + " (IA)"
            val t = Tank(i, label, colors[i], human)
            t.money = settings.startMoney
            tanks.add(t)
        }
    }

    fun onSizeChanged(heightInWorldUnits: Float) {
        val h = heightInWorldUnits.coerceIn(500f, 1400f)
        if (abs(h - worldHeight) < 1f) return
        worldHeight = h
        terrain.resize(h)
        terrain.palette(palette.dirtTop, palette.dirtBottom, palette.grass)
        buildBackground()
        for (t in tanks) t.y = terrain.heightAt(t.x)
    }

    private fun buildBackground() {
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, worldHeight,
            palette.skyTop, palette.skyBottom, Shader.TileMode.CLAMP
        )
        for (i in starsX.indices) {
            starsX[i] = rnd.nextFloat() * worldWidth
            starsY[i] = rnd.nextFloat() * worldHeight * 0.55f
            starsR[i] = 0.8f + rnd.nextFloat() * 1.6f
        }
        buildHills(hillFarPath, worldHeight * 0.62f, 90f, 5, 0.9f)
        buildHills(hillNearPath, worldHeight * 0.72f, 70f, 3, 1.6f)
    }

    private fun buildHills(path: Path, baseY: Float, amp: Float, waves: Int, phase: Float) {
        path.reset()
        path.moveTo(0f, worldHeight)
        var x = 0f
        while (x <= worldWidth) {
            var y = baseY
            for (k in 1..waves) {
                y += sin((x / worldWidth) * (k * 2.4f) * Math.PI.toFloat() + phase * k) * (amp / k)
            }
            path.lineTo(x, y)
            x += 24f
        }
        path.lineTo(worldWidth, worldHeight)
        path.close()
    }

    fun startRound(reset: Boolean) {
        palette = palettes[(round - 1) % palettes.size]
        terrain.palette(palette.dirtTop, palette.dirtBottom, palette.grass)
        terrain.generate(rnd)
        buildBackground()

        wind = if (settings.windEnabled) (rnd.nextFloat() * 2f - 1f) else 0f

        projectiles.clear()
        explosions.clear()
        particles.clear()
        texts.clear()

        for (t in tanks) t.resetForRound()
        placeTanks()

        if (!reset) firstPlayerOfRound = (firstPlayerOfRound + 1) % tanks.size
        currentIndex = firstPlayerOfRound
        roundReported = false
        beginTurn(announce = true)
    }

    fun startNextRound() {
        if (round >= settings.rounds) {
            state = State.GAME_OVER
            return
        }
        round++
        startRound(reset = false)
    }

    private fun placeTanks() {
        val count = tanks.size
        val margin = 140f
        val usable = worldWidth - margin * 2f
        val slot = usable / count
        val order = tanks.indices.shuffled(rnd)
        for ((i, tankIdx) in order.withIndex()) {
            val t = tanks[tankIdx]
            val center = margin + slot * i + slot * 0.5f
            val jitter = (rnd.nextFloat() * 2f - 1f) * (slot * 0.28f)
            t.x = (center + jitter).coerceIn(60f, worldWidth - 60f)
            terrain.flatten(t.x, Tank.HALF_W + 10f)
            t.y = terrain.heightAt(t.x)
            t.angle = if (t.x < worldWidth / 2f) 55f else 125f
            t.falling = false
            t.vy = 0f
        }
    }

    private fun beginTurn(announce: Boolean) {
        // salta i carri distrutti
        var guard = 0
        while (!tanks[currentIndex].alive && guard < tanks.size) {
            currentIndex = (currentIndex + 1) % tanks.size
            guard++
        }
        val t = currentTank
        t.ensureValidWeapon()
        t.fuel = min(Tank.MAX_FUEL, t.fuel + 12f) // piccola ricarica a inizio turno
        aiThinkTimer = 0.8f + rnd.nextFloat() * 0.5f
        state = State.TURN_START
        if (announce) showBanner("Turno di ${t.name}", 0.9f) else bannerTimer = 0.35f
    }

    private fun showBanner(text: String, seconds: Float) {
        banner = text
        bannerTimer = seconds
    }

    // ---------------------------------------------------------------- input

    fun adjustAngle(delta: Float) {
        if (!waitingForHumanInput) return
        val t = currentTank
        t.angle = (t.angle + delta).coerceIn(0f, 180f)
    }

    fun adjustPower(delta: Float) {
        if (!waitingForHumanInput) return
        val t = currentTank
        t.power = (t.power + delta).coerceIn(50f, Tank.MAX_POWER)
    }

    fun aimAt(worldX: Float, worldY: Float) {
        if (!waitingForHumanInput) return
        val t = currentTank
        val dx = worldX - t.x
        val dy = (t.y - Tank.BODY_H) - worldY
        if (abs(dx) < 0.001f && abs(dy) < 0.001f) return
        val ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        t.angle = ang.coerceIn(0f, 180f)
        val len = hypot(dx, dy)
        t.power = (len / 330f * Tank.MAX_POWER).coerceIn(50f, Tank.MAX_POWER)
    }

    fun moveTank(dir: Int, dt: Float) {
        if (!waitingForHumanInput) return
        val t = currentTank
        if (t.fuel <= 0f) return
        val step = dir * MOVE_SPEED * dt
        val newX = (t.x + step).coerceIn(40f, worldWidth - 40f)
        if (abs(newX - t.x) < 0.0001f) return
        val rise = terrain.heightAt(t.x) - terrain.heightAt(newX) // >0 = in salita
        if (rise / abs(newX - t.x) > 1.1f) return // pendenza troppo ripida
        t.x = newX
        t.y = terrain.heightAt(newX)
        t.fuel = max(0f, t.fuel - abs(step) * 0.5f)
    }

    fun selectWeapon(id: Int) {
        if (!waitingForHumanInput) return
        val w = Weapons.byId(id)
        if (currentTank.hasAmmo(w)) currentTank.selectedWeaponId = id
    }

    fun cycleWeapon(dir: Int) {
        if (!waitingForHumanInput) return
        currentTank.cycleWeapon(dir)
    }

    fun fire() {
        if (state != State.AIMING || paused) return
        val t = currentTank
        val w = t.selectedWeapon
        if (!t.hasAmmo(w)) {
            t.selectedWeaponId = Weapons.BABY_MISSILE.id
            return
        }
        t.consumeAmmo(w)
        t.lastShotTrail.clear()

        val (vx, vy) = AiBrain.velocityFrom(t.angle, t.power)
        val p = Projectile(t.muzzleX(), t.muzzleY(), vx, vy, w, t.id)
        p.pushTrail()
        projectiles.add(p)

        spawnMuzzleFlash(t)
        state = State.FLYING
    }

    // ---------------------------------------------------------------- update

    fun update(dtRaw: Float) {
        if (paused) return
        val dt = dtRaw.coerceIn(0f, 0.05f)

        if (shake > 0f) shake = max(0f, shake - dt * 26f)
        updateEffects(dt)

        when (state) {
            State.TURN_START -> {
                bannerTimer -= dt
                if (bannerTimer <= 0f) state = State.AIMING
            }

            State.AIMING -> {
                val t = currentTank
                if (!t.isHuman) {
                    aiThinkTimer -= dt
                    if (aiThinkTimer <= 0f) {
                        val shot = ai.plan(this, t)
                        t.angle = shot.angle
                        t.power = shot.power
                        if (t.hasAmmo(Weapons.byId(shot.weaponId))) t.selectedWeaponId = shot.weaponId
                        ai.chooseTarget(this, t)?.let { target -> t.aiMemory[target.id] = 1f }
                        fire()
                    }
                }
            }

            State.FLYING -> {
                updateProjectiles(dt)
                if (projectiles.isEmpty() && explosions.isEmpty()) state = State.SETTLING
            }

            State.SETTLING -> {
                val settled = settleTanks(dt)
                if (settled && explosions.isEmpty() && projectiles.isEmpty()) endTurn()
            }

            State.ROUND_END -> {
                bannerTimer -= dt
                if (bannerTimer <= 0f && !roundReported) {
                    roundReported = true
                    val last = round >= settings.rounds
                    if (last) listener.onGameFinished(this) else listener.onRoundFinished(this, false)
                }
            }

            State.GAME_OVER -> Unit
        }
    }

    private fun updateEffects(dt: Float) {
        var i = explosions.size - 1
        while (i >= 0) {
            val e = explosions[i]
            e.t += dt
            if (e.done) explosions.removeAt(i)
            i--
        }
        i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.t += dt
            p.vy += GRAVITY * p.gravityScale * dt
            p.vx += windAccel * 0.4f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.done) particles.removeAt(i)
            i--
        }
        i = texts.size - 1
        while (i >= 0) {
            val f = texts[i]
            f.t += dt
            f.y -= 34f * dt
            if (f.done) texts.removeAt(i)
            i--
        }
    }

    private fun updateProjectiles(dt: Float) {
        val spawned = ArrayList<Projectile>()
        var i = projectiles.size - 1
        while (i >= 0) {
            val p = projectiles[i]
            stepProjectile(p, dt, spawned)
            if (!p.alive) {
                if (p.ownerId == currentTank.id && p.trail.size > 4 && currentTank.lastShotTrail.isEmpty()) {
                    currentTank.lastShotTrail.addAll(p.trail)
                }
                projectiles.removeAt(i)
            }
            i--
        }
        projectiles.addAll(spawned)
    }

    private fun stepProjectile(p: Projectile, dt: Float, spawned: ArrayList<Projectile>) {
        val speed = hypot(p.vx, p.vy)
        val steps = ceil((speed * dt) / 8f).toInt().coerceIn(1, 24)
        val h = dt / steps

        repeat(steps) {
            if (!p.alive) return@repeat
            p.age += h
            when (p.mode) {
                Projectile.Mode.FLY -> {
                    p.vx += windAccel * h
                    p.vy += GRAVITY * h
                    p.x += p.vx * h
                    p.y += p.vy * h

                    if (p.weapon.type == WeaponType.MIRV && !p.splitDone && p.vy >= 0f && p.age > 0.35f) {
                        p.splitDone = true
                        p.alive = false
                        splitMirv(p, spawned)
                        return@repeat
                    }

                    if (p.x < -120f || p.x > worldWidth + 120f || p.y > worldHeight + 120f) {
                        p.alive = false
                        return@repeat
                    }

                    val hitTank = tankAt(p.x, p.y, p)
                    if (hitTank != null) {
                        p.alive = false
                        impact(p, p.x, p.y, spawned)
                        return@repeat
                    }

                    if (p.y > 0f && terrain.isSolid(p.x, p.y)) {
                        p.alive = false
                        impact(p, p.x, terrain.heightAt(p.x), spawned)
                        return@repeat
                    }
                }

                Projectile.Mode.ROLL -> {
                    p.rollTime += h
                    val leftH = terrain.heightAt(p.x - 5f)
                    val rightH = terrain.heightAt(p.x + 5f)
                    val downhill = when {
                        rightH > leftH + 0.4f -> 1
                        leftH > rightH + 0.4f -> -1
                        else -> 0
                    }
                    if (downhill == 0 || p.rollTime > 6f) {
                        p.alive = false
                        detonate(p.x, p.y, p.weapon, p.ownerId, spawned)
                        return@repeat
                    }
                    p.rollDir = downhill
                    p.x += p.rollDir * 340f * h
                    p.y = terrain.heightAt(p.x)
                    if (p.x < 6f || p.x > worldWidth - 6f) {
                        p.alive = false
                        detonate(p.x.coerceIn(6f, worldWidth - 6f), p.y, p.weapon, p.ownerId, spawned)
                        return@repeat
                    }
                    val t = tankAt(p.x, p.y - 6f, p)
                    if (t != null) {
                        p.alive = false
                        detonate(p.x, p.y, p.weapon, p.ownerId, spawned)
                        return@repeat
                    }
                    if (rnd.nextFloat() < 0.35f) {
                        particles.add(
                            Particle(
                                p.x, p.y, (rnd.nextFloat() - 0.5f) * 40f, -30f * rnd.nextFloat(),
                                palette.dirtTop, 0.4f, 2f, 0.6f
                            )
                        )
                    }
                }

                Projectile.Mode.DIG -> {
                    val nx = p.x + p.vx * h
                    val ny = p.y + p.vy * h
                    p.digDepth += hypot(nx - p.x, ny - p.y)
                    p.x = nx
                    p.y = ny
                    terrain.crater(p.x, p.y, 13f)
                    particles.add(
                        Particle(
                            p.x, p.y, (rnd.nextFloat() - 0.5f) * 90f, -rnd.nextFloat() * 120f,
                            palette.dirtTop, 0.5f, 2.5f, 0.9f
                        )
                    )
                    if (p.digDepth > 120f || p.y > worldHeight - 8f) {
                        p.alive = false
                        detonate(p.x, p.y, p.weapon, p.ownerId, spawned)
                        return@repeat
                    }
                }
            }
        }
        if (p.alive) p.pushTrail()
    }

    private fun tankAt(x: Float, y: Float, p: Projectile): Tank? {
        for (t in tanks) {
            if (!t.alive) continue
            if (t.id == p.ownerId && p.age < 0.12f) continue
            val d = hypot(t.x - x, (t.y - Tank.BODY_H) - y)
            if (d < Tank.HIT_RADIUS) return t
        }
        return null
    }

    private fun splitMirv(p: Projectile, spawned: ArrayList<Projectile>) {
        val child = p.weapon.child ?: Weapons.BABY_MISSILE
        val n = max(2, p.weapon.childCount)
        for (k in 0 until n) {
            val spread = (k - (n - 1) / 2f) * 46f
            val c = Projectile(
                p.x, p.y,
                p.vx + spread + (rnd.nextFloat() - 0.5f) * 12f,
                p.vy - 40f - rnd.nextFloat() * 40f,
                child, p.ownerId
            )
            c.pushTrail()
            spawned.add(c)
        }
        for (k in 0 until 14) {
            particles.add(
                Particle(
                    p.x, p.y, (rnd.nextFloat() - 0.5f) * 220f, (rnd.nextFloat() - 0.5f) * 220f,
                    Color.rgb(200, 230, 255), 0.5f, 2.5f, 0.5f
                )
            )
        }
    }

    private fun impact(p: Projectile, x: Float, y: Float, spawned: ArrayList<Projectile>) {
        when (p.weapon.type) {
            WeaponType.ROLLER -> {
                val roller = Projectile(x, terrain.heightAt(x), p.vx, 0f, p.weapon, p.ownerId)
                roller.mode = Projectile.Mode.ROLL
                roller.rollDir = if (p.vx >= 0f) 1 else -1
                roller.trail.addAll(p.trail)
                spawned.add(roller)
            }

            WeaponType.DIGGER -> {
                val speed = hypot(p.vx, p.vy).coerceAtLeast(120f)
                val digger = Projectile(x, y + 2f, p.vx / speed * 260f, abs(p.vy / speed) * 260f + 120f, p.weapon, p.ownerId)
                digger.mode = Projectile.Mode.DIG
                digger.trail.addAll(p.trail)
                spawned.add(digger)
            }

            WeaponType.CLUSTER -> {
                detonate(x, y, p.weapon, p.ownerId, spawned)
                val child = p.weapon.child ?: Weapons.BABY_MISSILE
                for (k in 0 until p.weapon.childCount) {
                    val ang = Math.toRadians((35.0 + rnd.nextDouble() * 110.0))
                    val sp = 260f + rnd.nextFloat() * 220f
                    val c = Projectile(
                        x, y - 12f,
                        (cos(ang) * sp).toFloat() + p.vx * 0.15f,
                        (-sin(ang) * sp).toFloat(),
                        child, p.ownerId
                    )
                    c.pushTrail()
                    spawned.add(c)
                }
            }

            else -> detonate(x, y, p.weapon, p.ownerId, spawned)
        }
    }

    private fun detonate(x: Float, y: Float, weapon: Weapon, ownerId: Int, spawned: ArrayList<Projectile>) {
        if (weapon.type == WeaponType.DIRT) {
            terrain.addDirt(x, y, weapon.radius)
            for (k in 0 until 26) {
                particles.add(
                    Particle(
                        x, y, (rnd.nextFloat() - 0.5f) * 220f, -rnd.nextFloat() * 160f,
                        palette.dirtTop, 0.7f, 3f, 1f
                    )
                )
            }
            shake = max(shake, 3f)
            return
        }

        terrain.crater(x, y, weapon.radius)
        explosions.add(Explosion(x, y, weapon.radius, weapon.color))
        shake = max(shake, min(16f, weapon.radius * 0.09f))

        val count = (12 + weapon.radius * 0.35f).toInt()
        for (k in 0 until count) {
            val ang = rnd.nextFloat() * Math.PI.toFloat() * 2f
            val sp = (60f + rnd.nextFloat() * weapon.radius * 3.2f)
            particles.add(
                Particle(
                    x, y, cos(ang) * sp, sin(ang) * sp - 40f,
                    if (rnd.nextFloat() < 0.5f) weapon.color else Color.rgb(255, 180, 70),
                    0.45f + rnd.nextFloat() * 0.6f,
                    2f + rnd.nextFloat() * 3f,
                    0.8f
                )
            )
        }

        applyBlastDamage(x, y, weapon.radius, weapon.damage, ownerId, spawned)
    }

    private fun applyBlastDamage(
        x: Float,
        y: Float,
        radius: Float,
        damage: Float,
        ownerId: Int,
        spawned: ArrayList<Projectile>
    ) {
        if (damage <= 0f) return
        val killed = ArrayList<Tank>()
        for (t in tanks) {
            if (!t.alive) continue
            val d = hypot(t.x - x, (t.y - Tank.BODY_H) - y)
            if (d > radius + Tank.HIT_RADIUS) continue
            val factor = (1f - (d - Tank.HIT_RADIUS).coerceAtLeast(0f) / radius).coerceIn(0f, 1f)
            val dmg = damage * factor
            if (dmg < 0.5f) continue
            val applied = t.applyDamage(dmg)
            if (applied > 0f) {
                texts.add(
                    FloatingText(
                        t.x, t.y - Tank.BODY_H - 26f,
                        "-" + applied.toInt(),
                        if (t.id == ownerId) Color.rgb(255, 140, 140) else Color.rgb(255, 220, 140)
                    )
                )
            }
            val owner = tanks.firstOrNull { it.id == ownerId }
            if (owner != null && owner.id != t.id) {
                owner.money += (applied * MONEY_PER_DAMAGE).toInt()
                owner.score += applied.toInt()
            } else if (owner != null && owner.id == t.id) {
                owner.score -= (applied * 0.5f).toInt()
            }
            if (!t.alive) killed.add(t)
        }

        for (dead in killed) {
            val owner = tanks.firstOrNull { it.id == ownerId }
            if (owner != null && owner.id != dead.id) {
                owner.money += KILL_BONUS
                owner.score += 100
                texts.add(FloatingText(dead.x, dead.y - 50f, "DISTRUTTO!", Color.rgb(255, 120, 90)))
            }
            // esplosione secondaria del carro distrutto (puo' innescare reazioni a catena)
            terrain.crater(dead.x, dead.y - 4f, 62f)
            explosions.add(Explosion(dead.x, dead.y - 8f, 70f, Color.rgb(255, 170, 60)))
            shake = max(shake, 10f)
            for (k in 0 until 30) {
                val ang = rnd.nextFloat() * Math.PI.toFloat() * 2f
                val sp = 80f + rnd.nextFloat() * 340f
                particles.add(
                    Particle(
                        dead.x, dead.y - 8f, cos(ang) * sp, sin(ang) * sp - 60f,
                        Color.rgb(255, 150, 60), 0.6f + rnd.nextFloat() * 0.7f, 3f, 0.9f
                    )
                )
            }
            applyBlastDamage(dead.x, dead.y - 8f, 70f, 35f, dead.id, spawned)
        }
    }

    private fun spawnMuzzleFlash(t: Tank) {
        val mx = t.muzzleX()
        val my = t.muzzleY()
        for (k in 0 until 12) {
            val ang = Math.toRadians(t.angle.toDouble() + (rnd.nextDouble() - 0.5) * 50.0)
            val sp = 120f + rnd.nextFloat() * 200f
            particles.add(
                Particle(
                    mx, my, (cos(ang) * sp).toFloat(), (-sin(ang) * sp).toFloat(),
                    Color.rgb(255, 220, 150), 0.25f, 2.5f, 0.4f
                )
            )
        }
    }

    /** Fa cadere i carri sospesi nel vuoto. Ritorna true quando sono tutti fermi. */
    private fun settleTanks(dt: Float): Boolean {
        var stable = true
        for (t in tanks) {
            val ground = terrain.heightAt(t.x)
            if (!t.alive) {
                t.y = ground
                continue
            }
            if (t.y < ground - 0.6f) {
                if (!t.falling) {
                    t.falling = true
                    t.fallStartY = t.y
                    t.vy = 0f
                }
                t.vy += GRAVITY * dt
                t.y += t.vy * dt
                if (t.y >= ground) {
                    t.y = ground
                    val drop = t.y - t.fallStartY
                    t.falling = false
                    t.vy = 0f
                    if (drop > 70f) {
                        val dmg = (drop - 70f) * 0.28f
                        val applied = t.applyDamage(dmg)
                        if (applied > 1f) {
                            texts.add(
                                FloatingText(t.x, t.y - 40f, "caduta -" + applied.toInt(), Color.rgb(255, 200, 200))
                            )
                        }
                    }
                } else {
                    stable = false
                }
            } else if (t.y > ground) {
                // il terreno e' salito sotto al carro (palla di terra)
                t.y = ground
            }
        }
        return stable
    }

    private fun endTurn() {
        val alive = aliveTanks
        if (alive.size <= 1) {
            finishRound(alive.firstOrNull())
            return
        }
        currentIndex = (currentIndex + 1) % tanks.size
        beginTurn(announce = true)
    }

    private fun finishRound(winner: Tank?) {
        winner?.let {
            it.roundsWon++
            it.money += SURVIVOR_BONUS
            it.score += 250
        }
        showBanner(
            if (winner != null) "${winner.name} vince il round $round!" else "Round $round: nessun superstite",
            2.0f
        )
        state = State.ROUND_END
    }

    /** Classifica finale ordinata. */
    fun standings(): List<Tank> = tanks.sortedWith(
        compareByDescending<Tank> { it.roundsWon }.thenByDescending { it.score }.thenByDescending { it.money }
    )

    // ---------------------------------------------------------------- draw

    fun draw(canvas: Canvas) {
        drawBackground(canvas)
        terrain.draw(canvas, worldWidth)

        // traiettoria di riferimento del giocatore corrente
        val ghost = currentTank.lastShotTrail
        if (ghost.size >= 4 && state == State.AIMING) {
            trailPaint.color = Color.argb(60, 255, 255, 255)
            trailPaint.strokeWidth = 1.6f
            drawPolyline(canvas, ghost)
        }

        for (t in tanks) t.draw(canvas, t.id == currentTank.id && state != State.GAME_OVER)

        // mira corrente
        if (waitingForHumanInput) drawAimGuide(canvas, currentTank)

        for (p in projectiles) {
            trailPaint.color = Color.argb(190, Color.red(p.weapon.color), Color.green(p.weapon.color), Color.blue(p.weapon.color))
            trailPaint.strokeWidth = 2.4f
            drawPolyline(canvas, p.trail, p.x, p.y)
            paint.style = Paint.Style.FILL
            paint.color = p.weapon.color
            canvas.drawCircle(p.x, p.y, p.weapon.shellRadius, paint)
        }

        for (p in particles) {
            val a = (1f - p.t / p.life).coerceIn(0f, 1f)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((a * 255).toInt(), Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            canvas.drawCircle(p.x, p.y, p.size * (0.5f + a * 0.8f), paint)
        }

        for (e in explosions) drawExplosion(canvas, e)

        for (f in texts) {
            val a = (1f - f.t / f.life).coerceIn(0f, 1f)
            textPaint.color = Color.argb((a * 255).toInt(), Color.red(f.color), Color.green(f.color), Color.blue(f.color))
            canvas.drawText(f.text, f.x, f.y, textPaint)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawRect(0f, 0f, worldWidth, worldHeight, skyPaint)

        paint.style = Paint.Style.FILL
        paint.shader = null
        for (i in starsX.indices) {
            paint.color = Color.argb(110 + (i * 7) % 120, 255, 255, 255)
            canvas.drawCircle(starsX[i], starsY[i], starsR[i], paint)
        }

        // sole / luna con alone
        val sunX = worldWidth * 0.78f
        val sunY = worldHeight * 0.18f
        paint.shader = RadialGradient(
            sunX, sunY, 130f,
            Color.argb(120, Color.red(palette.sun), Color.green(palette.sun), Color.blue(palette.sun)),
            Color.argb(0, Color.red(palette.sun), Color.green(palette.sun), Color.blue(palette.sun)),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, 130f, paint)
        paint.shader = null
        paint.color = palette.sun
        canvas.drawCircle(sunX, sunY, 34f, paint)

        paint.color = palette.hillFar
        canvas.drawPath(hillFarPath, paint)
        paint.color = palette.hillNear
        canvas.drawPath(hillNearPath, paint)
    }

    private fun drawExplosion(canvas: Canvas, e: Explosion) {
        val k = (e.t / e.duration).coerceIn(0f, 1f)
        val r = e.radius * (0.35f + 0.85f * k)
        val alpha = ((1f - k) * 235f).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            e.x, e.y, r.coerceAtLeast(1f),
            Color.argb(alpha, 255, 250, 210),
            Color.argb(0, Color.red(e.color), Color.green(e.color), Color.blue(e.color)),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(e.x, e.y, r, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb((alpha * 0.7f).toInt(), 255, 200, 120)
        canvas.drawCircle(e.x, e.y, r * 0.92f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawAimGuide(canvas: Canvas, t: Tank) {
        val (vx, vy) = AiBrain.velocityFrom(t.angle, t.power)
        tmpPath.reset()
        var x = t.muzzleX()
        var y = t.muzzleY()
        var cvx = vx
        var cvy = vy
        tmpPath.moveTo(x, y)
        val h = 1f / 40f
        var steps = 0
        val maxSteps = 26
        while (steps < maxSteps) {
            cvx += windAccel * h
            cvy += GRAVITY * h
            x += cvx * h
            y += cvy * h
            tmpPath.lineTo(x, y)
            if (terrain.isSolid(x, y) || x < 0f || x > worldWidth || y > worldHeight) break
            steps++
        }
        trailPaint.color = Color.argb(120, 255, 235, 180)
        trailPaint.strokeWidth = 2f
        canvas.drawPath(tmpPath, trailPaint)
    }

    private fun drawPolyline(canvas: Canvas, pts: List<Float>, tipX: Float? = null, tipY: Float? = null) {
        if (pts.size < 4) {
            if (tipX != null && tipY != null && pts.size == 2) {
                canvas.drawLine(pts[0], pts[1], tipX, tipY, trailPaint)
            }
            return
        }
        tmpPath.reset()
        tmpPath.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            tmpPath.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        if (tipX != null && tipY != null) tmpPath.lineTo(tipX, tipY)
        canvas.drawPath(tmpPath, trailPaint)
    }
}
