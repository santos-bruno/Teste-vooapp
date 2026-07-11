package com.vooapp.justdance.camera

import kotlin.math.abs
import kotlin.math.max

/** Articulação normalizada (0..1); y cresce para baixo. */
data class Joint(val x: Float, val y: Float, val inFrame: Boolean)

/**
 * Pose de corpo inteiro (normalizada) mais um resumo de "características de
 * dança" invariantes a espelhamento, usadas para pontuar contra os passos.
 */
class PoseSnapshot(
    val leftShoulder: Joint?, val rightShoulder: Joint?,
    val leftElbow: Joint?, val rightElbow: Joint?,
    val leftWrist: Joint?, val rightWrist: Joint?,
    val leftHip: Joint?, val rightHip: Joint?,
    val leftKnee: Joint?, val rightKnee: Joint?,
    val leftAnkle: Joint?, val rightAnkle: Joint?,
) {
    /** Ombros, quadris, pulsos e tornozelos presentes e bem rastreados. */
    val fullBody: Boolean
        get() = ok(leftShoulder) && ok(rightShoulder) && ok(leftHip) && ok(rightHip) &&
                ok(leftWrist) && ok(rightWrist) && ok(leftAnkle) && ok(rightAnkle)

    val hasArms: Boolean
        get() = leftShoulder != null && rightShoulder != null && leftWrist != null && rightWrist != null

    /**
     * Vetor de características (todas em "larguras de ombro"):
     * [0] armsUp, [1] armsWide, [2] armAsym, [3] legsWide, [4] legStretch.
     * Retorna null se nem os braços estão disponíveis.
     */
    fun features(): FloatArray? {
        val ls = leftShoulder ?: return null
        val rs = rightShoulder ?: return null
        val lw = leftWrist ?: return null
        val rw = rightWrist ?: return null
        val scale = max(0.05f, abs(ls.x - rs.x))
        val shoulderY = (ls.y + rs.y) * 0.5f

        val armsUp = (shoulderY - (lw.y + rw.y) * 0.5f) / scale
        val armsWide = abs(lw.x - rw.x) / scale
        val armAsym = abs((ls.y - lw.y) - (rs.y - rw.y)) / scale

        // Pernas: usa defaults de "em pé" quando não visíveis.
        var legsWide = 0.9f
        var legStretch = 3.0f
        val la = leftAnkle; val ra = rightAnkle; val lh = leftHip; val rh = rightHip
        if (la != null && ra != null && lh != null && rh != null) {
            legsWide = abs(la.x - ra.x) / scale
            val hipY = (lh.y + rh.y) * 0.5f
            legStretch = ((la.y + ra.y) * 0.5f - hipY) / scale
        }
        return floatArrayOf(armsUp, armsWide, armAsym, legsWide, legStretch)
    }

    private fun ok(j: Joint?) = j != null && j.inFrame
}
