package com.vooapp.justdance.game

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.vooapp.justdance.audio.DanceSound
import com.vooapp.justdance.audio.MusicPlayer
import com.vooapp.justdance.camera.PoseSnapshot

/** SurfaceView com loop próprio, toque nos menus, música e ranking. */
class DanceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val engine = DanceEngine()
    private val renderer = DanceRenderer()
    private val sound = DanceSound()
    private val music = MusicPlayer()
    private val leaderboard = Leaderboard(context)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @Volatile private var features: FloatArray? = null
    @Volatile private var fullBody = false
    @Volatile private var running = false
    private var thread: Thread? = null

    private var lastBeat = 0
    private var lastRatingCount = 0
    private var prevScreen = Screen.MENU

    init { holder.addCallback(this) }

    fun updatePose(snapshot: PoseSnapshot?) {
        features = snapshot?.features()
        fullBody = snapshot?.fullBody ?: false
    }

    fun releaseAudio() { sound.release(); music.stop() }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        val nx = event.x / width.coerceAtLeast(1); val ny = event.y / height.coerceAtLeast(1)
        when (engine.screen) {
            Screen.MENU -> {
                if (DanceLayout.inRect(nx, ny, DanceLayout.PLAY_L, DanceLayout.PLAY_T, DanceLayout.PLAY_R, DanceLayout.PLAY_B)) engine.tapPlay()
                else if (DanceLayout.inRect(nx, ny, DanceLayout.RANK_L, DanceLayout.RANK_T, DanceLayout.RANK_R, DanceLayout.RANK_B)) engine.tapRanking()
            }
            Screen.SETUP -> {
                if (DanceLayout.inRect(nx, ny, DanceLayout.BACK_L, DanceLayout.BACK_T, DanceLayout.BACK_R, DanceLayout.BACK_B)) engine.tapBackToMenu()
                else if (DanceLayout.inRect(nx, ny, DanceLayout.START_L, DanceLayout.START_T, DanceLayout.START_R, DanceLayout.START_B)) engine.startGame()
                else {
                    val pb = DanceLayout.playersButton(nx, ny)
                    if (pb >= 0) engine.setPlayers(pb + 1)
                    else { val sr = DanceLayout.songRow(nx, ny, Song.ALL.size); if (sr >= 0) engine.selectSong(sr) }
                }
            }
            Screen.TURN_END -> engine.tapContinue()
            Screen.RESULTS -> engine.tapBackToMenu()
            Screen.RANKING -> engine.tapBackToMenu()
            else -> {}
        }
        performClick(); return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) { running = true; thread = Thread({ loop() }, "GingaLoop").also { it.start() } }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
        music.stop()
    }

    private fun vibrate(ms: Long, amp: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, amp))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    private fun onScreenChange(from: Screen, to: Screen) {
        if (to == Screen.PLAYING) music.play(engine.song)
        else if (from == Screen.PLAYING) music.stop()
        if (to == Screen.RESULTS && from != Screen.RESULTS) {
            for (p in engine.players) leaderboard.add(p.name, p.score, engine.song.name)
        }
        if (to == Screen.RANKING) engine.rankingEntries = leaderboard.top()
    }

    private fun loop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            engine.update(dt, features, fullBody)

            if (engine.screen != prevScreen) { onScreenChange(prevScreen, engine.screen); prevScreen = engine.screen }

            if (engine.screen == Screen.PLAYING) {
                if (engine.beatCount != lastBeat) { lastBeat = engine.beatCount; sound.beat() }
                if (engine.ratingCount != lastRatingCount) {
                    lastRatingCount = engine.ratingCount
                    when (engine.lastRating) {
                        Rating.PERFEITO -> { sound.perfect(); vibrate(40, 200) }
                        Rating.BOM, Rating.OK -> sound.good()
                        Rating.ERROU -> { sound.miss(); vibrate(110, 170) }
                        Rating.NONE -> {}
                    }
                }
            }

            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) { try { renderer.render(canvas, engine) } finally { holder.unlockCanvasAndPost(canvas) } }

            val frameMs = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - frameMs
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }
}
