package com.vooapp.justdance.game

/**
 * Uma "faixa" jogável: define ritmo (BPM), quantas batidas por passo, a cor do
 * pictograma, o padrão de música (sintetizada) e a duração em passos.
 */
class Song(
    val name: String,
    val bpm: Float,
    val beatsPerMove: Int,
    val hue: Int,
    val pattern: Int,
    val moves: Int,
) {
    companion object {
        val ALL = listOf(
            Song("Neon Nights", 100f, 4, 315, 0, 16),
            Song("Cyber Samba", 118f, 4, 190, 1, 18),
            Song("Sunset Drive", 92f, 4, 30, 2, 14),
        )
    }
}
