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
        val bars = 2
        val total = (SR * beat * 4 * bars).toInt()
        val o = ShortArray(total)

        // Progressões de notas (Hz) por padrão.
        val bassSets = arrayOf(
            floatArrayOf(110f, 110f, 146.83f, 130.81f), // A A D C
            floatArrayOf(98f, 130.81f, 110f, 146.83f),  // G C A D
            floatArrayOf(87.31f, 116.54f, 98f, 130.81f) // F A#? -> F Bb G C
        )
        val bass = bassSets[pattern % bassSets.size]
        val arpSet = floatArrayOf(440f, 554.37f, 659.25f, 880f)

        val beatSamples = (SR * beat).toInt()
        val totalBeats = 4 * bars
        for (b in 0 until totalBeats) {
            val start = b * beatSamples
            // Bumbo em toda batida.
            addTone(o, 62f, start, 0.14f, 0.55f, 22f)
            // Baixo (uma nota por batida).
            addSaw(o, bass[b % bass.size], start, beat * 0.9f, 0.22f, 3.5f)
            // Arpejo em colcheias.
            addTone(o, arpSet[b % arpSet.size], start, beat * 0.45f, 0.10f, 6f)
            addTone(o, arpSet[(b + 2) % arpSet.size], start + beatSamples / 2, beat * 0.45f, 0.10f, 6f)
            // Chimbal em contratempo.
            if (b % 2 == 1) addNoise(o, start, 0.05f, 0.10f, 60f)
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
