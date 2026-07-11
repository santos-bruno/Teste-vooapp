package com.vooapp.justdance.game

/**
 * Posições (em frações de largura/altura) dos elementos tocáveis, usadas tanto
 * pelo [DanceRenderer] (para desenhar) quanto pela DanceView (para o toque).
 */
object DanceLayout {
    // MENU
    const val PLAY_L = 0.22f; const val PLAY_R = 0.78f; const val PLAY_T = 0.44f; const val PLAY_B = 0.53f
    const val RANK_L = 0.30f; const val RANK_R = 0.70f; const val RANK_T = 0.58f; const val RANK_B = 0.66f

    // SETUP — botões de nº de jogadores (linha) e faixas (colunas verticais)
    const val PL_T = 0.30f; const val PL_B = 0.39f; const val PL_L = 0.10f; const val PL_R = 0.90f
    const val SONG_T = 0.46f; const val SONG_H = 0.095f   // cada faixa ocupa SONG_H, começando em SONG_T
    const val START_L = 0.26f; const val START_R = 0.74f; const val START_T = 0.85f; const val START_B = 0.93f
    const val BACK_L = 0.04f; const val BACK_R = 0.20f; const val BACK_T = 0.03f; const val BACK_B = 0.08f

    fun inRect(nx: Float, ny: Float, l: Float, t: Float, r: Float, b: Float) = nx in l..r && ny in t..b

    /** Índice do botão de jogadores (0..3) ou -1. */
    fun playersButton(nx: Float, ny: Float): Int {
        if (ny < PL_T || ny > PL_B) return -1
        if (nx < PL_L || nx > PL_R) return -1
        val seg = (PL_R - PL_L) / 4f
        return ((nx - PL_L) / seg).toInt().coerceIn(0, 3)
    }

    /** Índice da faixa (0..count-1) ou -1. */
    fun songRow(nx: Float, ny: Float, count: Int): Int {
        for (i in 0 until count) {
            val t = SONG_T + i * SONG_H
            if (ny in t..(t + SONG_H - 0.012f) && nx in 0.14f..0.86f) return i
        }
        return -1
    }
}
