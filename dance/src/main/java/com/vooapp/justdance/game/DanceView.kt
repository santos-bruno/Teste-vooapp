package com.vooapp.justdance.game

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.vooapp.justdance.audio.DanceSound
import com.vooapp.justdance.camera.PoseSnapshot

/** SurfaceView com loop próprio para o modo dança. */
class DanceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val engine = DanceEngine()
    private val renderer = DanceRenderer()
    private val sound = DanceSound()
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val prefs = context.getSharedPreferences("justvoo", Context.MODE_PRIVATE)

    @Volatile private var features: FloatArray? = null
    @Volatile private var fullBody = false
    @Volatile private var running = false
    private var thread: Thread? = null

    private var lastBeat = 0
    private var lastRatingCount = 0
    private var savedBest = 0

    init {
        holder.addCallback(this)
        savedBest = prefs.getInt("best", 0)
    }

    /** Recebe a pose detectada (thread da câmera). */
    fun updatePose(snapshot: PoseSnapshot?) {
        features = snapshot?.features()
        fullBody = snapshot?.fullBody ?: false
    }

    fun releaseAudio() = sound.release()

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread({ loop() }, "DanceLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    private fun vibrate(ms: Long, amp: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, amp))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    private fun loop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            engine.update(dt, features, fullBody)

            if (engine.beatCount != lastBeat) { lastBeat = engine.beatCount; sound.beat() }
            if (engine.ratingCount != lastRatingCount) {
                lastRatingCount = engine.ratingCount
                when (engine.lastRating) {
                    Rating.PERFEITO -> { sound.perfect(); vibrate(40, 200) }
                    Rating.BOM -> sound.good()
                    Rating.OK -> sound.good()
                    Rating.ERROU -> { sound.miss(); vibrate(120, 180) }
                    Rating.NONE -> {}
                }
                if (engine.score > savedBest) { savedBest = engine.score; prefs.edit().putInt("best", savedBest).apply() }
            }

            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                try { renderer.render(canvas, engine) } finally { holder.unlockCanvasAndPost(canvas) }
            }

            val frameMs = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - frameMs
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }
}
