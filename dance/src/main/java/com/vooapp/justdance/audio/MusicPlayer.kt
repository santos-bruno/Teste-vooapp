package com.vooapp.justdance.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Música de fundo sintetizada em runtime (sem arquivos): monta um compasso de
 * baixo + bumbo + arpejo no BPM da faixa e o repete em loop com [AudioTrack].
 * Original e offline. Protegido por try/catch para nunca derrubar o jogo.
 */
class MusicPlayer {

    private var track: AudioTrack? = null

    fun play(song: com.vooapp.justdance.game.Song) {
        stop()
        try {
            val data = buildLoop(song.bpm, song.pattern)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val t = AudioTrack(attrs, fmt, data.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
            t.write(data, 0, data.size)
            t.setLoopPoints(0, data.size, -1) // loop infinito
            t.play()
            track = t
        } catch (_: Exception) { track = null }
    }

    fun stop() {
        val t = track ?: return
        try { t.pause(); t.flush(); t.stop() } catch (_: Exception) {}
        try { t.release() } catch (_: Exception) {}
        track = null
    }

    // --------------------------------------------------------- síntese

    private fun buildLoop(bpm: Float, pattern: Int): ShortArray {
        val beat = 60f / bpm
        val bars = 4
        val totalBeats = bars * 4
        val o = ShortArray((SR * beat * totalBeats).toInt())
        val beatSamples = (SR * beat).toInt()
        val half = beatSamples / 2

        // Raiz do baixo por compasso (progressões de 4 acordes por padrão).
        val progressions = arrayOf(
            floatArrayOf(110f, 87.31f, 130.81f, 98f),     // Am F C G
            floatArrayOf(73.42f, 58.27f, 87.31f, 65.41f), // Dm Bb F C
            floatArrayOf(65.41f, 98f, 110f, 87.31f),      // C G Am F
            floatArrayOf(82.41f, 65.41f, 98f, 73.42f),    // Em C G D
        )
        // Melodia: índices no arpejo [1, 3ª/5ª, oitava, 12ª]; -1 = pausa.
        val ratios = floatArrayOf(1f, 1.5f, 2f, 3f)
        val melodies = arrayOf(
            intArrayOf(0, 2, 1, 3, 2, -1, 1, 2),
            intArrayOf(0, 1, 2, 3, 2, 1, 0, -1),
            intArrayOf(2, -1, 1, -1, 3, 2, 1, 0),
            intArrayOf(0, 2, 3, 2, 0, 2, 3, -1),
        )
        val prog = progressions[pattern % progressions.size]
        val mel = melodies[pattern % melodies.size]

        for (b in 0 until totalBeats) {
            val start = b * beatSamples
            val root = prog[(b / 4) % prog.size]

            // Bumbo em toda batida (+ colcheia extra no fim do compasso, pra ginga).
            addTone(o, 58f, start, 0.15f, 0.60f, 20f)
            if (b % 4 == 3) addTone(o, 58f, start + half, 0.12f, 0.42f, 24f)
            // Caixa nos tempos 2 e 4.
            if (b % 2 == 1) { addNoise(o, start, 0.10f, 0.20f, 34f); addTone(o, 190f, start, 0.08f, 0.16f, 30f) }
            // Chimbal em colcheias, mais aberto no contratempo.
            addNoise(o, start, 0.03f, 0.05f, 90f)
            addNoise(o, start + half, 0.045f, 0.09f, 70f)
            // Baixo em colcheias: raiz e quinta alternadas.
            addSaw(o, root, start, beat * 0.42f, 0.20f, 4f)
            addSaw(o, if (b % 2 == 0) root else root * 1.5f, start + half, beat * 0.38f, 0.16f, 5f)
            // "Power chord" sustentado no tempo 1 de cada compasso.
            if (b % 4 == 0) {
                addSaw(o, root * 2f, start, beat * 1.6f, 0.06f, 1.6f)
                addSaw(o, root * 3f, start, beat * 1.6f, 0.05f, 1.6f)
                addSaw(o, root * 4f, start, beat * 1.6f, 0.04f, 1.6f)
            }
            // Melodia em arpejo (colcheias), duas oitavas acima da raiz.
            val m1 = mel[(b * 2) % mel.size]; val m2 = mel[(b * 2 + 1) % mel.size]
            if (m1 >= 0) addTone(o, root * 4f * ratios[m1], start, beat * 0.40f, 0.10f, 5f)
            if (m2 >= 0) addTone(o, root * 4f * ratios[m2], start + half, beat * 0.40f, 0.10f, 5f)
        }
        return o
    }

    private fun addTone(o: ShortArray, freq: Float, start: Int, dur: Float, vol: Float, decay: Float) {
        val n = (dur * SR).toInt()
        for (i in 0 until n) {
            val idx = start + i; if (idx >= o.size) break
            val t = i / SR.toFloat()
            val v = sin(2.0 * PI * freq * t).toFloat() * exp(-decay * t) * vol
            o[idx] = (o[idx] + (v * Short.MAX_VALUE).toInt()).coerceIn(-32768, 32767).toShort()
        }
    }

    private fun addSaw(o: ShortArray, freq: Float, start: Int, dur: Float, vol: Float, decay: Float) {
        val n = (dur * SR).toInt()
        for (i in 0 until n) {
            val idx = start + i; if (idx >= o.size) break
            val t = i / SR.toFloat()
            val phase = (freq * t) % 1f
            val saw = (2f * phase - 1f)
            val v = saw * exp(-decay * t) * vol
            o[idx] = (o[idx] + (v * Short.MAX_VALUE).toInt()).coerceIn(-32768, 32767).toShort()
        }
    }

    private fun addNoise(o: ShortArray, start: Int, dur: Float, vol: Float, decay: Float) {
        val n = (dur * SR).toInt()
        for (i in 0 until n) {
            val idx = start + i; if (idx >= o.size) break
            val t = i / SR.toFloat()
            val v = (Math.random().toFloat() * 2f - 1f) * exp(-decay * t) * vol
            o[idx] = (o[idx] + (v * Short.MAX_VALUE).toInt()).coerceIn(-32768, 32767).toShort()
        }
    }

    companion object { private const val SR = 22050 }
}
