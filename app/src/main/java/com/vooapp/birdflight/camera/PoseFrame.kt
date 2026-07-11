package com.vooapp.birdflight.camera

/** Ponto de articulação normalizado (0..1) no espaço da imagem já na vertical. */
data class Joint(val x: Float, val y: Float, val inFrame: Boolean)

/**
 * Snapshot leve de uma pose, com apenas as articulações que o jogo usa.
 * Coordenadas normalizadas: x e y em 0..1, y crescendo para baixo.
 */
data class PoseFrame(
    val leftShoulder: Joint?,
    val rightShoulder: Joint?,
    val leftElbow: Joint?,
    val rightElbow: Joint?,
    val leftWrist: Joint?,
    val rightWrist: Joint?,
    val leftHip: Joint?,
    val rightHip: Joint?,
    val timestampMs: Long,
) {
    val hasCore: Boolean
        get() = leftShoulder != null && rightShoulder != null &&
                leftWrist != null && rightWrist != null

    /** Ombros e pulsos presentes E com alta confiança (dentro do quadro). */
    val wellTracked: Boolean
        get() = leftShoulder?.inFrame == true && rightShoulder?.inFrame == true &&
                leftWrist?.inFrame == true && rightWrist?.inFrame == true
}
