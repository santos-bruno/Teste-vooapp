package com.vooapp.justdance.game

import android.content.Context

/** Entrada do ranking. */
class ScoreEntry(val name: String, val score: Int, val song: String)

/**
 * Ranking persistente (top scores) usando SharedPreferences. Serializa as
 * entradas como linhas "nome|pontos|musica" separadas por ';'.
 */
class Leaderboard(context: Context) {

    private val prefs = context.getSharedPreferences("ginga", Context.MODE_PRIVATE)

    fun top(limit: Int = 10): List<ScoreEntry> {
        val raw = prefs.getString("ranking", "") ?: ""
        return raw.split(";").mapNotNull { parse(it) }.sortedByDescending { it.score }.take(limit)
    }

    fun add(name: String, score: Int, song: String) {
        val list = (prefs.getString("ranking", "") ?: "").split(";").mapNotNull { parse(it) }.toMutableList()
        list.add(ScoreEntry(sanitize(name), score, sanitize(song)))
        val serialized = list.sortedByDescending { it.score }.take(20)
            .joinToString(";") { "${it.name}|${it.score}|${it.song}" }
        prefs.edit().putString("ranking", serialized).apply()
    }

    private fun parse(s: String): ScoreEntry? {
        val p = s.split("|")
        if (p.size != 3) return null
        val sc = p[1].toIntOrNull() ?: return null
        return ScoreEntry(p[0], sc, p[2])
    }

    private fun sanitize(s: String) = s.replace("|", " ").replace(";", " ").take(14)
}
