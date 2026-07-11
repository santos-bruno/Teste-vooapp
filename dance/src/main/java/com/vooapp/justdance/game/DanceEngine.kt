package com.vooapp.justdance.game

import kotlin.math.floor
import kotlin.random.Random

enum class DanceState { READY, PLAYING }
enum class Rating { NONE, ERROU, OK, BOM, PERFEITO }

/**
 * Motor do modo dança: toca uma sequência de passos no ritmo (BPM) e pontua
 * o quanto a pose do jogador combina com o passo atual. Sem renderização.
 */
class DanceEngine {

    var state = DanceState.READY; private set

    var currentMove: Move = Move.ALL[0]; private set
    var nextMove: Move = Move.ALL[1]; private set

    var score = 0; private set
    var combo = 0; private set
    var groove = 0.5f; private set          // "medidor de gingado" 0..1
    var liveMatch = 0f; private set          // acerto ao vivo 0..1
    var lastRating = Rating.NONE; private set
    var ratingTimer = 0f; private set        // tempo restante do popup de nota
    var ratingCount = 0; private set         // incrementa a cada nota dada
    var moveProgress = 0f; private set       // 0..1 dentro do passo atual
    var beatPulse = 0f; private set          // 1 na batida, decai até 0
    var beatCount = 0; private set           // incrementa a cada batida
    var startProgress = 0f; private set      // prontidão para começar

    private var songTime = 0f
    private var lastBeat = -1
    private var moveNumber = 0
    private var bestMatch = 0f
    private val rng = Random(System.nanoTime())

    fun update(dt: Float, features: FloatArray?, fullBody: Boolean) {
        val d = dt.coerceIn(0f, 0.05f)
        if (ratingTimer > 0f) ratingTimer -= d
        when (state) {
            DanceState.READY -> {
                if (fullBody) { startProgress += d / START_HOLD; if (startProgress >= 1f) start() }
                else startProgress = (startProgress - d / (START_HOLD * 0.5f)).coerceAtLeast(0f)
            }
            DanceState.PLAYING -> updatePlaying(d, features)
        }
    }

    private fun start() {
        state = DanceState.PLAYING
        songTime = 0f; lastBeat = -1; moveNumber = 0; bestMatch = 0f
        score = 0; combo = 0; groove = 0.5f
        currentMove = Move.ALL[rng.nextInt(Move.ALL.size)]
        nextMove = pickDifferent(currentMove)
    }

    private fun updatePlaying(dt: Float, features: FloatArray?) {
        songTime += dt
        val beatPos = songTime / BEAT_LEN
        val beat = floor(beatPos).toInt()
        val phase = beatPos - beat
        beatPulse = (1f - phase).toFloat().coerceIn(0f, 1f)
        if (beat != lastBeat) { lastBeat = beat; beatCount++ }

        // Acerto ao vivo com o passo atual.
        liveMatch = if (features != null) Move.score(features, currentMove) else liveMatch * 0.9f
        if (liveMatch > bestMatch) bestMatch = liveMatch

        val thisMoveNumber = beat / BEATS_PER_MOVE
        moveProgress = ((beat % BEATS_PER_MOVE) + phase).toFloat() / BEATS_PER_MOVE
        if (thisMoveNumber != moveNumber) {
            commitRating(bestMatch)
            moveNumber = thisMoveNumber
            bestMatch = 0f
            currentMove = nextMove
            nextMove = pickDifferent(currentMove)
        }
    }

    private fun commitRating(best: Float) {
        val (rating, points) = when {
            best >= 0.80f -> Rating.PERFEITO to 100
            best >= 0.62f -> Rating.BOM to 60
            best >= 0.45f -> Rating.OK to 30
            else -> Rating.ERROU to 0
        }
        if (rating == Rating.ERROU) combo = 0 else combo += 1
        score += (points * (1f + combo * 0.1f)).toInt()
        groove = (groove + (best - 0.5f) * 0.4f).coerceIn(0f, 1f)
        lastRating = rating
        ratingTimer = RATING_SHOW
        ratingCount++
    }

    private fun pickDifferent(m: Move): Move {
        var c = m
        while (c === m) c = Move.ALL[rng.nextInt(Move.ALL.size)]
        return c
    }

    companion object {
        private const val BPM = 100f
        private const val BEAT_LEN = 60f / BPM
        private const val BEATS_PER_MOVE = 4
        private const val START_HOLD = 1.0f
        private const val RATING_SHOW = 1.1f
    }
}
