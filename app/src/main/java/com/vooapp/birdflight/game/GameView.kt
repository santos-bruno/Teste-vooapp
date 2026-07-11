package com.vooapp.birdflight.game

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.vooapp.birdflight.input.FlightInput

/**
 * SurfaceView com um loop de renderização em thread própria.
 * Recebe os comandos de voo por [updateInput] (chamado da thread da câmera).
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val engine = GameEngine()
    private val renderer = GameRenderer()

    @Volatile private var currentInput: FlightInput = FlightInput.IDLE
    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
    }

    /** Atualiza o comando de voo mais recente (thread-safe). */
    fun updateInput(input: FlightInput) {
        currentInput = input
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread({ loop() }, "GameLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    private fun loop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            val input = currentInput
            engine.update(dt, input)

            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                try {
                    renderer.render(canvas, engine, input)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            // ~60 FPS
            val frameMs = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - frameMs
            if (sleep > 0) {
                try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
            }
        }
    }
}
