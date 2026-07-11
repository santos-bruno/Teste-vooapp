package com.vooapp.birdflight.input

import com.vooapp.birdflight.camera.PoseFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converte uma sequência de [PoseFrame] em [FlightInput].
 *
 * Metáfora de controle (braços = asas):
 *  - Braços no alto  -> sobe.  Braços baixos -> desce.
 *  - Bater os braços para cima rapidamente -> impulso de altitude (flap).
 *  - Inclinar as asas (um pulso mais alto que o outro) -> vira para aquele lado.
 *  - Braços bem esticados para os lados -> plana (mais velocidade, menos queda).
 *
 * Mantém estado entre frames para medir a velocidade do bater de asas.
 */
class PoseInterpreter {

    // Estado para detecção de flap (velocidade vertical dos pulsos).
    private var lastWristY = Float.NaN
    private var lastTimestamp = 0L
    private var smoothedFlap = 0f

    // Suavização para evitar tremores nos controles.
    private var smoothedLift = 0f
    private var smoothedRoll = 0f
    private var smoothedSpread = 0f

    fun reset() {
        lastWristY = Float.NaN
        lastTimestamp = 0L
        smoothedFlap = 0f
        smoothedLift = 0f
        smoothedRoll = 0f
        smoothedSpread = 0f
    }

    fun interpret(frame: PoseFrame): FlightInput {
        if (!frame.hasCore) {
            // Decai suavemente para o repouso quando perde a pose.
            smoothedFlap *= 0.85f
            smoothedLift *= 0.9f
            smoothedRoll *= 0.9f
            return FlightInput(
                detected = false,
                lift = smoothedLift,
                flap = smoothedFlap,
                roll = smoothedRoll,
                spread = smoothedSpread,
                confident = false,
            )
        }

        val ls = frame.leftShoulder!!
        val rs = frame.rightShoulder!!
        val lw = frame.leftWrist!!
        val rw = frame.rightWrist!!

        val shoulderY = (ls.y + rs.y) * 0.5f
        val shoulderWidth = max(0.05f, abs(ls.x - rs.x))
        val wristY = (lw.y + rw.y) * 0.5f

        // ---- Lift: quão acima dos ombros estão os pulsos ----
        // (shoulderY - wristY) positivo = pulsos acima dos ombros = subir.
        // Escala pela largura dos ombros para ficar independente da distância à câmera.
        val rawLift = ((shoulderY - wristY) / shoulderWidth).coerceIn(-1.2f, 1.2f)
        smoothedLift = lerp(smoothedLift, rawLift.coerceIn(-1f, 1f), 0.25f)

        // ---- Flap: velocidade com que os pulsos sobem ----
        val now = frame.timestampMs
        var flapImpulse = 0f
        if (!lastWristY.isNaN() && lastTimestamp != 0L) {
            val dt = max(1L, now - lastTimestamp) / 1000f
            // velocidade em "larguras de ombro por segundo"; negativa = subindo.
            val vel = ((wristY - lastWristY) / shoulderWidth) / dt
            if (vel < 0f) {
                flapImpulse = min(1f, (-vel) / FLAP_VELOCITY_FULL)
            }
        }
        lastWristY = wristY
        lastTimestamp = now
        // Ataque rápido, decaimento lento -> sensação de "batida".
        smoothedFlap = if (flapImpulse > smoothedFlap) {
            flapImpulse
        } else {
            smoothedFlap * 0.80f
        }

        // ---- Roll: diferença de altura entre os pulsos (banking) ----
        // rightWrist mais baixo que leftWrist -> asa direita baixa -> vira à direita.
        val rawRoll = ((rw.y - lw.y) / shoulderWidth).coerceIn(-1.5f, 1.5f)
        val roll = (rawRoll * ROLL_GAIN).coerceIn(-1f, 1f) * if (INVERT_ROLL) -1f else 1f
        smoothedRoll = lerp(smoothedRoll, roll, 0.3f)

        // ---- Spread: distância horizontal entre os pulsos vs largura dos ombros ----
        val wristSpan = abs(lw.x - rw.x)
        val rawSpread = ((wristSpan / shoulderWidth) - 0.6f) / 1.4f
        smoothedSpread = lerp(smoothedSpread, rawSpread.coerceIn(0f, 1f), 0.2f)

        return FlightInput(
            detected = true,
            lift = smoothedLift,
            flap = smoothedFlap,
            roll = smoothedRoll,
            spread = smoothedSpread,
            confident = frame.wellTracked,
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    companion object {
        /** Velocidade dos pulsos (larguras de ombro/s) que gera flap máximo. */
        private const val FLAP_VELOCITY_FULL = 3.0f
        private const val ROLL_GAIN = 1.1f

        /**
         * A prévia da câmera frontal é espelhada. Se a direção do giro parecer
         * invertida no seu aparelho, troque este valor.
         */
        const val INVERT_ROLL = false
    }
}
