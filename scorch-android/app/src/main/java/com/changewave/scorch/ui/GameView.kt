package com.changewave.scorch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.changewave.scorch.game.GameSettings
import com.changewave.scorch.game.GameWorld
import kotlin.math.min
import kotlin.random.Random

/**
 * SurfaceView con game loop su thread dedicato. Il mondo e' largo 1600 unita' e viene
 * scalato alla larghezza dello schermo; l'altezza in unita' dipende dal formato del display.
 */
class GameView(
    context: Context,
    settings: GameSettings,
    listener: GameWorld.Listener
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val world = GameWorld(settings, listener)
    val hud = Hud(resources.displayMetrics.density)

    private var thread: Thread? = null
    @Volatile private var running = false
    private var scale = 1f
    private var aimPointer = -1
    private val rnd = Random(1234)

    init {
        holder.addCallback(this)
        hud.world = world
        isFocusable = true
    }

    fun setMenuAction(action: () -> Unit) {
        hud.onMenu = action
    }

    // ------------------------------------------------------------ surface

    override fun surfaceCreated(holder: SurfaceHolder) {
        start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        scale = w / GameWorld.WORLD_WIDTH
        world.onSizeChanged(h / scale)
        hud.layout(w, h)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stop()
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread(this, "scorch-loop").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.let {
            var retry = true
            while (retry) {
                try {
                    it.join(500)
                    retry = false
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    retry = false
                }
            }
        }
        thread = null
    }

    // ------------------------------------------------------------ loop

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            last = now

            if (!world.paused) {
                hud.update(dt)
                world.update(dt)
            }

            val canvas: Canvas? = try {
                holder.lockCanvas()
            } catch (e: IllegalStateException) {
                null
            }
            if (canvas != null) {
                try {
                    render(canvas)
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: IllegalStateException) {
                        // superficie gia' rilasciata
                    }
                }
            }

            val elapsed = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - elapsed
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    running = false
                }
            }
        }
    }

    private fun render(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        canvas.save()
        val s = world.shake
        if (s > 0.2f) {
            canvas.translate(
                (rnd.nextFloat() - 0.5f) * s * scale,
                (rnd.nextFloat() - 0.5f) * s * scale
            )
        }
        canvas.scale(scale, scale)
        world.draw(canvas)
        canvas.restore()
        hud.draw(canvas)

        if (world.paused) {
            canvas.drawColor(Color.argb(120, 0, 0, 0))
        }
    }

    // ------------------------------------------------------------ input

    private fun toWorldX(x: Float) = x / scale
    private fun toWorldY(y: Float) = y / scale

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (world.paused) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)
                val consumed = hud.onPointerDown(id, x, y)
                if (!consumed && hud.isPlayArea(x, y)) {
                    aimPointer = id
                    world.aimAt(toWorldX(x), toWorldY(y))
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (aimPointer >= 0) {
                    val idx = event.findPointerIndex(aimPointer)
                    if (idx >= 0) {
                        world.aimAt(toWorldX(event.getX(idx)), toWorldY(event.getY(idx)))
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                hud.onPointerUp(id)
                if (id == aimPointer) aimPointer = -1
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hud.clearPointers()
                aimPointer = -1
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Dimensione ragionevole del testo per i dialog costruiti a codice. */
    fun dialogTextSize(): Float = min(18f, resources.displayMetrics.density * 7f)
}
