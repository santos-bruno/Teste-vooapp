package com.vooapp.justdance.game

import kotlin.math.abs

/**
 * Um passo de dança. É definido de duas formas:
 *  - [targets]/[weights]: alvo das características de pose (para pontuar).
 *  - [handL]/[handR]/[footL]/[footR]: posições no "boneco" do dançarino
 *    (coordenadas locais: pescoço=(0,0), quadril=(0,1)) para desenhar o coach.
 *
 * Ordem das características: [armsUp, armsWide, armAsym, legsWide, legStretch].
 */
class Move(
    val name: String,
    val targets: FloatArray,
    val weights: FloatArray,
    val handL: FloatArray, val handR: FloatArray,
    val footL: FloatArray, val footR: FloatArray,
) {
    companion object {
        /** Tolerância por característica (em larguras de ombro). */
        private val TOL = floatArrayOf(0.9f, 1.1f, 0.9f, 1.1f, 1.3f)

        val ALL = listOf(
            Move("Braços abertos",
                floatArrayOf(0.0f, 2.6f, 0.0f, 0.9f, 3.0f), floatArrayOf(1f, 1.2f, 0.5f, 0.4f, 0.3f),
                floatArrayOf(-1.05f, 0.08f), floatArrayOf(1.05f, 0.08f), floatArrayOf(-0.3f, 2.0f), floatArrayOf(0.3f, 2.0f)),
            Move("Braços pra cima",
                floatArrayOf(1.6f, 1.2f, 0.0f, 0.9f, 3.0f), floatArrayOf(1.3f, 0.7f, 0.6f, 0.3f, 0.3f),
                floatArrayOf(-0.4f, -0.95f), floatArrayOf(0.4f, -0.95f), floatArrayOf(-0.3f, 2.0f), floatArrayOf(0.3f, 2.0f)),
            Move("Uma mão pra cima",
                floatArrayOf(0.4f, 1.2f, 1.7f, 0.9f, 3.0f), floatArrayOf(0.5f, 0.4f, 1.3f, 0.3f, 0.3f),
                floatArrayOf(-0.4f, -0.95f), floatArrayOf(0.75f, 0.55f), floatArrayOf(-0.3f, 2.0f), floatArrayOf(0.3f, 2.0f)),
            Move("Estrela",
                floatArrayOf(1.3f, 2.2f, 0.0f, 2.0f, 2.6f), floatArrayOf(1f, 1f, 0.4f, 1f, 0.4f),
                floatArrayOf(-0.95f, -0.7f), floatArrayOf(0.95f, -0.7f), floatArrayOf(-0.95f, 1.9f), floatArrayOf(0.95f, 1.9f)),
            Move("Agachar",
                floatArrayOf(0.1f, 1.3f, 0.0f, 1.2f, 1.6f), floatArrayOf(0.5f, 0.6f, 0.4f, 0.5f, 1.3f),
                floatArrayOf(-0.9f, 0.35f), floatArrayOf(0.9f, 0.35f), floatArrayOf(-0.55f, 1.45f), floatArrayOf(0.55f, 1.45f)),
            Move("Descansar",
                floatArrayOf(-0.9f, 0.6f, 0.0f, 0.9f, 3.0f), floatArrayOf(1f, 0.5f, 0.4f, 0.3f, 0.3f),
                floatArrayOf(-0.35f, 0.9f), floatArrayOf(0.35f, 0.9f), floatArrayOf(-0.3f, 2.0f), floatArrayOf(0.3f, 2.0f)),
        )

        /** Similaridade 0..1 entre as características do jogador e o passo. */
        fun score(f: FloatArray, m: Move): Float {
            var sumW = 0f; var acc = 0f
            for (i in 0 until 5) {
                val w = m.weights[i]
                if (w <= 0f) continue
                val match = (1f - abs(f[i] - m.targets[i]) / TOL[i]).coerceIn(0f, 1f)
                acc += w * match; sumW += w
            }
            return if (sumW <= 0f) 0f else acc / sumW
        }
    }
}
