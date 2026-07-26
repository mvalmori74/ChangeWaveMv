package com.changewave.scorch.game

class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val weapon: Weapon,
    val ownerId: Int
) {
    enum class Mode { FLY, ROLL, DIG }

    var mode = Mode.FLY
    var alive = true
    var age = 0f
    var splitDone = false
    var digDepth = 0f
    var rollDir = if (vx >= 0f) 1 else -1
    var rollTime = 0f

    /** Coppie (x, y) della scia, usata per il disegno e come traiettoria di riferimento. */
    val trail = ArrayList<Float>(256)

    fun pushTrail() {
        if (trail.size >= 700) return
        val n = trail.size
        if (n >= 2) {
            val lx = trail[n - 2]
            val ly = trail[n - 1]
            val dx = x - lx
            val dy = y - ly
            if (dx * dx + dy * dy < 25f) return
        }
        trail.add(x)
        trail.add(y)
    }
}

class Explosion(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Int
) {
    var t = 0f
    val duration = 0.45f + radius / 900f
    val done: Boolean get() = t >= duration
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Int,
    val life: Float,
    val size: Float,
    val gravityScale: Float = 1f
) {
    var t = 0f
    val done: Boolean get() = t >= life
}

class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int
) {
    var t = 0f
    val life = 1.4f
    val done: Boolean get() = t >= life
}
