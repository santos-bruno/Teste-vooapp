package com.vooapp.birdflight.input

/**
 * Comandos de voo derivados da pose do jogador em um instante.
 * Todos os valores já vêm normalizados e prontos para o motor de física.
 */
data class FlightInput(
    /** true quando uma pose confiável foi detectada neste frame. */
    val detected: Boolean = false,
    /** Altura relativa dos braços: -1 (braços baixos, desce) .. +1 (braços no alto, sobe). */
    val lift: Float = 0f,
    /** Impulso instantâneo ao bater as asas (movimento rápido dos braços para cima): 0..1. */
    val flap: Float = 0f,
    /** Inclinação lateral / rolagem: -1 (vira à esquerda) .. +1 (vira à direita). */
    val roll: Float = 0f,
    /** Abertura das asas (braços esticados): 0 (encolhido) .. 1 (planando esticado). */
    val spread: Float = 0f,
    /**
     * true quando os dois ombros e os dois pulsos estão bem rastreados (alta
     * confiança) — ou seja, as "asas" foram identificadas corretamente.
     * Usado como pré-requisito para a decolagem.
     */
    val confident: Boolean = false,
) {
    companion object {
        val IDLE = FlightInput()
    }
}
