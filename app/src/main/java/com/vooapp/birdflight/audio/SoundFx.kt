package com.vooapp.birdflight.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin

/**
 * Efeitos sonoros sintetizados em tempo de execução (sem arquivos de áudio).
 * Cada efeito é um pequeno buffer PCM 16-bit tocado por um [AudioTrack] em
 * modo estático, reaproveitado a cada disparo. Tudo é protegido por try/catch
 * para que problemas de áudio nunca derrubem o jogo.
 */
class SoundFx {

    private val ding = track(buildDing())
    private val whoosh = track(buildWhoosh())
    private val thud = track(buildThud())
    private val chime = track(buildChime())

    fun ding() = play(ding)
    fun whoosh() = play(whoosh)
    fun thud() = play(thud)
    fun chime() = play(chime)

    fun release() {
        for (t in listOf(ding, whoosh, thud, chime)) {
            try { t?.release() } catch (_: Exception) {}
        }
    }

    private fun play(t: AudioTrack?) {
        t ?: return
        try {
            try { t.stop() } catch (_: Exception) {}
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) {}
    }

    private fun track(data: ShortArray): AudioTrack? = try {
        val bytes = data.size * 2
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        AudioTrack(attrs, fmt, bytes, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE).also {
            it.write(data, 0, data.size)
        }
    } catch (_: Exception) { null }

    // ------------------------------------------------------------- síntese

    private fun buf(seconds: Float) = ShortArray((SR * seconds).toInt())

    private fun addTone(out: ShortArray, freq: Float, start: Float, dur: Float, vol: Float, decay: Float) {
        val s0 = (start * SR).toInt()
        val n = (dur * SR).toInt()
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val t = i / SR.toFloat()
            val env = exp(-decay * t)
            val v = sin(2.0 * Math.PI * freq * t).toFloat() * env * vol
            val cur = out[idx] + (v * Short.MAX_VALUE).toInt()
            out[idx] = cur.coerceIn(-32768, 32767).toShort()
        }
    }

    private fun addNoise(out: ShortArray, start: Float, dur: Float, vol: Float, decay: Float) {
        val s0 = (start * SR).toInt()
        val n = (dur * SR).toInt()
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val t = i / SR.toFloat()
            val env = exp(-decay * t) * (1f - exp(-40f * t)) // ataque rápido + decaimento
            val v = (Math.random().toFloat() * 2f - 1f) * env * vol
            val cur = out[idx] + (v * Short.MAX_VALUE).toInt()
            out[idx] = cur.coerceIn(-32768, 32767).toShort()
        }
    }

    private fun buildDing(): ShortArray {
        val o = buf(0.22f)
        addTone(o, 1200f, 0f, 0.22f, 0.34f, 14f)
        addTone(o, 1800f, 0f, 0.18f, 0.16f, 18f)
        return o
    }

    private fun buildWhoosh(): ShortArray {
        val o = buf(0.16f)
        addNoise(o, 0f, 0.16f, 0.22f, 22f)
        addTone(o, 320f, 0f, 0.12f, 0.10f, 20f)
        return o
    }

    private fun buildThud(): ShortArray {
        val o = buf(0.32f)
        addTone(o, 130f, 0f, 0.3f, 0.5f, 11f)
        addNoise(o, 0f, 0.08f, 0.35f, 30f)
        return o
    }

    private fun buildChime(): ShortArray {
        val o = buf(0.4f)
        addTone(o, 660f, 0.00f, 0.14f, 0.28f, 12f)
        addTone(o, 880f, 0.10f, 0.14f, 0.28f, 12f)
        addTone(o, 1174f, 0.20f, 0.18f, 0.30f, 10f)
        return o
    }

    companion object { private const val SR = 22050 }
}
