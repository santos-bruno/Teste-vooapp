package com.vooapp.birdflight.game

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
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

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var lastScore = 0
    private var lastState = GameState.READY

    init {
        holder.addCallback(this)
    }

    private fun vibrate(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }

    /** Atualiza o comando de voo mais recente (thread-safe). */
    fun updateInput(input: FlightInput) {
        currentInput = input
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Na tela inicial, tocar embaixo escolhe o pássaro (cartões em colunas).
        if (event.action == MotionEvent.ACTION_DOWN && engine.state == GameState.READY) {
            if (event.y > height * 0.5f) {
                val n = BirdType.ALL.size
                val index = (event.x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)
                engine.selectBird(index)
            }
            performClick()
            return true
        }
        return super.onTouchEvent(event)
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

            // Feedback tátil: leve ao pontuar, forte ao cair.
            if (engine.score > lastScore) vibrate(28, 90)
            if (engine.state == GameState.CRASHED && lastState != GameState.CRASHED) vibrate(220, 255)
            lastScore = engine.score
            lastState = engine.state

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
