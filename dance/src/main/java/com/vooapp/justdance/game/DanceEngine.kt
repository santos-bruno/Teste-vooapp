package com.vooapp.justdance.game

import kotlin.math.floor
import kotlin.random.Random

enum class Screen { MENU, SETUP, COUNTDOWN, PLAYING, TURN_END, RESULTS, RANKING }
enum class Rating { NONE, ERROU, OK, BOM, PERFEITO }

class Player(val name: String) {
    var score = 0
    var stars = 0
    var matchSum = 0f
    var matchCount = 0
}

/**
 * Motor do GINGA: fluxo completo (menu → seleção → contagem → dança por turnos
 * → resultado → ranking) e a mecânica de dança (ritmo, passos, nota por acerto).
 * Sem renderização.
 */
class DanceEngine {

    var screen = Screen.MENU; private set

    // Seleção
    var setupPlayerCount = 1; private set
    var songIndex = 0; private set
    val song: Song get() = Song.ALL[songIndex]

    // Jogadores / turnos
    val players = ArrayList<Player>()
    var currentPlayerIndex = 0; private set
    val currentPlayer: Player? get() = players.getOrNull(currentPlayerIndex)

    // Ranking (preenchido pela View a partir do Leaderboard)
    var rankingEntries: List<ScoreEntry> = emptyList()

    // Passo atual da dança
    var currentMove: Move = Move.ALL[0]; private set
    var nextMove: Move = Move.ALL[1]; private set

    // Estado da corrida atual
    var score = 0; private set
    var combo = 0; private set
    var groove = 0.5f; private set
    var liveMatch = 0f; private set
    var lastRating = Rating.NONE; private set
    var ratingTimer = 0f; private set
    var ratingCount = 0; private set
    var moveProgress = 0f; private set
    var beatPulse = 0f; private set
    var beatCount = 0; private set
    var movesDone = 0; private set
    var countdown = 0f; private set

    private var songTime = 0f
    private var lastBeat = -1
    private var moveNumber = 0
    private var bestMatch = 0f
    private val rng = Random(System.nanoTime())

    val songProgress: Float get() = (movesDone.toFloat() / song.moves).coerceIn(0f, 1f)

    // -------------------------------------------------------- navegação (toque)

    fun tapPlay() { if (screen == Screen.MENU) screen = Screen.SETUP }
    fun tapRanking() { if (screen == Screen.MENU) screen = Screen.RANKING }
    fun tapBackToMenu() { screen = Screen.MENU }
    fun setPlayers(n: Int) { if (screen == Screen.SETUP) setupPlayerCount = n.coerceIn(1, 4) }
    fun selectSong(i: Int) { if (screen == Screen.SETUP) songIndex = i.coerceIn(0, Song.ALL.size - 1) }

    fun startGame() {
        if (screen != Screen.SETUP) return
        players.clear()
        for (i in 0 until setupPlayerCount) players.add(Player("P${i + 1}"))
        currentPlayerIndex = 0
        beginTurn()
    }

    /** Avança do TURN_END para o próximo jogador ou para os resultados. */
    fun tapContinue() {
        if (screen != Screen.TURN_END) return
        if (currentPlayerIndex < players.size - 1) { currentPlayerIndex++; beginTurn() }
        else screen = Screen.RESULTS
    }

    private fun beginTurn() {
        screen = Screen.COUNTDOWN
        countdown = 3.2f
        score = 0; combo = 0; groove = 0.5f; movesDone = 0
        songTime = 0f; lastBeat = -1; moveNumber = 0; bestMatch = 0f
        lastRating = Rating.NONE; ratingTimer = 0f
        currentMove = Move.ALL[rng.nextInt(Move.ALL.size)]
        nextMove = pickDifferent(currentMove)
    }

    // -------------------------------------------------------- update

    fun update(dt: Float, features: FloatArray?, fullBody: Boolean) {
        val d = dt.coerceIn(0f, 0.05f)
        if (ratingTimer > 0f) ratingTimer -= d
        when (screen) {
            Screen.COUNTDOWN -> { countdown -= d; if (countdown <= 0f) screen = Screen.PLAYING }
            Screen.PLAYING -> updatePlaying(d, features)
            else -> {}
        }
    }

    private fun updatePlaying(dt: Float, features: FloatArray?) {
        val beatLen = 60f / song.bpm
        songTime += dt
        val beatPos = songTime / beatLen
        val beat = floor(beatPos).toInt()
        val phase = (beatPos - beat).toFloat()
        beatPulse = (1f - phase).coerceIn(0f, 1f)
        if (beat != lastBeat) { lastBeat = beat; beatCount++ }

        liveMatch = if (features != null) Move.score(features, currentMove) else liveMatch * 0.9f
        if (liveMatch > bestMatch) bestMatch = liveMatch

        val bpm = song.beatsPerMove
        moveProgress = ((beat % bpm) + phase) / bpm
        val thisMoveNumber = beat / bpm
        if (thisMoveNumber != moveNumber) {
            commitRating(bestMatch)
            moveNumber = thisMoveNumber
            bestMatch = 0f
            movesDone++
            if (movesDone >= song.moves) { finishTurn(); return }
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
        lastRating = rating; ratingTimer = RATING_SHOW; ratingCount++
        currentPlayer?.let { it.matchSum += best; it.matchCount++ }
    }

    private fun finishTurn() {
        val p = currentPlayer ?: return
        p.score = score
        val avg = if (p.matchCount > 0) p.matchSum / p.matchCount else 0f
        p.stars = when { avg >= 0.85f -> 5; avg >= 0.72f -> 4; avg >= 0.58f -> 3; avg >= 0.42f -> 2; else -> 1 }
        screen = Screen.TURN_END
    }

    private fun pickDifferent(m: Move): Move {
        var c = m
        while (c === m) c = Move.ALL[rng.nextInt(Move.ALL.size)]
        return c
    }

    /** Jogadores ordenados por pontuação (para a tela de resultados). */
    fun ranked(): List<Player> = players.sortedByDescending { it.score }

    companion object { private const val RATING_SHOW = 1.1f }
}
