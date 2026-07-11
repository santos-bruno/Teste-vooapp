package com.vooapp.justdance.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sons do modo dança, sintetizados em runtime (sem arquivos): batida do ritmo
 * e retornos de nota. Protegido por try/catch para nunca derrubar o jogo.
 */
class DanceSound {

    private val beat = track(buildBeat())
    private val perfect = track(buildChord(floatArrayOf(660f, 990f, 1320f)))
    private val good = track(buildTone(880f, 0.18f, 0.3f, 12f))
    private val miss = track(buildTone(160f, 0.22f, 0.35f, 9f))

    fun beat() = play(beat)
    fun perfect() = play(perfect)
    fun good() = play(good)
    fun miss() = play(miss)

    fun release() { for (t in listOf(beat, perfect, good, miss)) try { t?.release() } catch (_: Exception) {} }

    private fun play(t: AudioTrack?) {
        t ?: return
        try { try { t.stop() } catch (_: Exception) {}; t.reloadStaticData(); t.play() } catch (_: Exception) {}
    }

    private fun track(data: ShortArray): AudioTrack? = try {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        AudioTrack(attrs, fmt, data.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
            .also { it.write(data, 0, data.size) }
    } catch (_: Exception) { null }

    private fun buf(sec: Float) = ShortArray((SR * sec).toInt())

    private fun addTone(o: ShortArray, freq: Float, start: Float, dur: Float, vol: Float, decay: Float) {
        val s0 = (start * SR).toInt(); val n = (dur * SR).toInt()
        for (i in 0 until n) {
            val idx = s0 + i; if (idx >= o.size) break
            val t = i / SR.toFloat()
            val v = sin(2.0 * Math.PI * freq * t).toFloat() * exp(-decay * t) * vol
            o[idx] = (o[idx] + (v * Short.MAX_VALUE).toInt()).coerceIn(-32768, 32767).toShort()
        }
    }

    private fun buildTone(freq: Float, dur: Float, vol: Float, decay: Float): ShortArray {
        val o = buf(dur + 0.02f); addTone(o, freq, 0f, dur, vol, decay); return o
    }

    private fun buildChord(freqs: FloatArray): ShortArray {
        val o = buf(0.4f)
        freqs.forEachIndexed { i, f -> addTone(o, f, i * 0.05f, 0.3f, 0.26f, 10f) }
        return o
    }

    private fun buildBeat(): ShortArray {
        // "Tick" curto e seco.
        val o = buf(0.09f)
        addTone(o, 1400f, 0f, 0.06f, 0.28f, 45f)
        addTone(o, 700f, 0f, 0.05f, 0.18f, 55f)
        return o
    }

    companion object { private const val SR = 22050 }
}
