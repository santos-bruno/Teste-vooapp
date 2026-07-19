package com.vooapp.justdance.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Esqueleto completo do dançarino em coordenadas locais
 * (pescoço=(0,0), quadril=(0,1); y cresce para baixo).
 */
class DancerPose {
    val head = FloatArray(2); val neck = FloatArray(2); val hip = FloatArray(2)
    val shL = FloatArray(2); val shR = FloatArray(2)
    val elL = FloatArray(2); val elR = FloatArray(2)
    val haL = FloatArray(2); val haR = FloatArray(2)
    val hipL = FloatArray(2); val hipR = FloatArray(2)
    val kneL = FloatArray(2); val kneR = FloatArray(2)
    val ftL = FloatArray(2); val ftR = FloatArray(2)
}

/**
 * Anima o dançarino em tempo real, como um coach de jogo de dança:
 *  - interpola os passos com easing de "punch" (overshoot) para acertar a pose
 *    exatamente no ritmo;
 *  - adiciona ginga contínua — quique do corpo, balanço de quadril, ombros em
 *    contra-balanço, cabeça com atraso e passinho alternado dos pés;
 *  - resolve cotovelos e joelhos com IK de dois ossos (dobram para fora).
 *
 * [mirror] espelha a coreografia e [beatShift] desloca a fase da ginga —
 * usados pelos dançarinos de apoio para o palco parecer um ballet de verdade.
 */
class DancerAnimator(private val mirror: Boolean = false, private val beatShift: Float = 0f) {

    private val pose = DancerPose()
    private var lastMove: Move? = null
    private val fromT = FloatArray(8)    // mãoL, mãoR, péL, péR (x,y) no início do blend
    private val curT = FloatArray(8)     // posição corrente (exibida) dos 4 pontos
    private val targetT = FloatArray(8)  // alvo do passo atual
    private var blend = 1f

    fun update(dt: Float, move: Move, beatsIn: Float, beatLen: Float, energy: Float): DancerPose {
        val beats = beatsIn + beatShift
        moveTargets(move)
        if (move !== lastMove) {
            if (lastMove == null) System.arraycopy(targetT, 0, curT, 0, 8)
            else { System.arraycopy(curT, 0, fromT, 0, 8); blend = 0f }
            lastMove = move
        }
        if (blend < 1f) {
            blend = (blend + dt / (0.42f * max(0.2f, beatLen))).coerceAtMost(1f)
            val e = easeOutBack(blend)
            for (i in 0 until 8) curT[i] = fromT[i] + (targetT[i] - fromT[i]) * e
        } else {
            System.arraycopy(targetT, 0, curT, 0, 8)
        }

        val ph = beats * PI.toFloat()                    // meio ciclo por batida
        val phase = beats - floor(beats)                 // 0..1 dentro da batida
        val sway = sin(ph) * 0.09f * energy              // quadril: lado a lado a cada 2 batidas
        val dip = abs(sin(ph)) * 0.06f * energy          // quique: desce entre as batidas

        val hipX = sway; val hipY = 1.02f + dip
        val neckX = sway * 0.4f; val neckY = dip * 0.75f
        pose.hip[0] = hipX; pose.hip[1] = hipY
        pose.neck[0] = neckX; pose.neck[1] = neckY
        pose.hipL[0] = hipX - 0.20f; pose.hipL[1] = hipY
        pose.hipR[0] = hipX + 0.20f; pose.hipR[1] = hipY

        // Ombros em leve contra-balanço (rotação oposta ao quadril).
        val rot = -sway * 0.5f
        val cR = cos(rot); val sR = sin(rot)
        pose.shL[0] = neckX + (-0.34f) * cR - 0.06f * sR; pose.shL[1] = neckY + (-0.34f) * sR + 0.06f * cR
        pose.shR[0] = neckX + 0.34f * cR - 0.06f * sR; pose.shR[1] = neckY + 0.34f * sR + 0.06f * cR

        // Cabeça balança com um pequeno atraso em relação ao corpo.
        pose.head[0] = neckX + sin(ph - 0.6f) * 0.045f * energy
        pose.head[1] = neckY - 0.36f + dip * 0.35f

        // Mãos "bombeiam" no ritmo por cima do alvo do passo.
        val twoPi = 2f * PI.toFloat()
        val hlX = curT[0] + neckX * 0.5f
        val hlY = curT[1] + sin(beats * twoPi) * 0.035f * energy + dip * 0.5f
        val hrX = curT[2] + neckX * 0.5f
        val hrY = curT[3] + sin(beats * twoPi + 0.9f) * 0.035f * energy + dip * 0.5f

        // Pés plantados, com passinho alternado (levanta um pé por batida).
        val step = sin(PI.toFloat() * phase) * 0.05f * energy
        val leftSteps = ((floor(beats).toInt() % 2) + 2) % 2 == 0
        val flX = curT[4] + hipX * 0.25f
        val flY = curT[5] - if (leftSteps) step else 0f
        val frX = curT[6] + hipX * 0.25f
        val frY = curT[7] - if (leftSteps) 0f else step

        ik(pose.shL, hlX, hlY, ARM1, ARM2, -1f, pose.elL, pose.haL)
        ik(pose.shR, hrX, hrY, ARM1, ARM2, 1f, pose.elR, pose.haR)
        ik(pose.hipL, flX, flY, LEG1, LEG2, -1f, pose.kneL, pose.ftL)
        ik(pose.hipR, frX, frY, LEG1, LEG2, 1f, pose.kneR, pose.ftR)
        return pose
    }

    private fun moveTargets(m: Move) {
        if (!mirror) {
            targetT[0] = m.handL[0]; targetT[1] = m.handL[1]
            targetT[2] = m.handR[0]; targetT[3] = m.handR[1]
            targetT[4] = m.footL[0]; targetT[5] = m.footL[1]
            targetT[6] = m.footR[0]; targetT[7] = m.footR[1]
        } else {
            targetT[0] = -m.handR[0]; targetT[1] = m.handR[1]
            targetT[2] = -m.handL[0]; targetT[3] = m.handL[1]
            targetT[4] = -m.footR[0]; targetT[5] = m.footR[1]
            targetT[6] = -m.footL[0]; targetT[7] = m.footL[1]
        }
    }

    /**
     * IK de dois ossos: dado o ponto fixo [a], o alvo (ex,ey) e os comprimentos
     * [l1]/[l2], calcula a articulação do meio ([joint]) e o ponto final
     * alcançável ([end]). [outSign] escolhe o lado da dobra (-1 esquerda/fora,
     * +1 direita/fora).
     */
    private fun ik(a: FloatArray, ex: Float, ey: Float, l1: Float, l2: Float, outSign: Float, joint: FloatArray, end: FloatArray) {
        var dx = ex - a[0]; var dy = ey - a[1]
        var d = sqrt(dx * dx + dy * dy)
        val reach = l1 + l2 - 0.001f
        if (d > reach) { val k = reach / d; dx *= k; dy *= k; d = reach }
        if (d < 0.001f) { dx = 0f; dy = 0.001f; d = 0.001f }
        end[0] = a[0] + dx; end[1] = a[1] + dy
        val t = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
        val h = sqrt(max(0f, l1 * l1 - t * t))
        val ux = dx / d; val uy = dy / d
        val j1x = a[0] + ux * t - uy * h; val j1y = a[1] + uy * t + ux * h
        val j2x = a[0] + ux * t + uy * h; val j2y = a[1] + uy * t - ux * h
        if ((j1x - a[0]) * outSign >= (j2x - a[0]) * outSign) { joint[0] = j1x; joint[1] = j1y }
        else { joint[0] = j2x; joint[1] = j2y }
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f; val c3 = c1 + 1f
        val x = t - 1f
        return 1f + c3 * x * x * x + c1 * x * x
    }

    companion object {
        private const val ARM1 = 0.56f; private const val ARM2 = 0.56f
        private const val LEG1 = 0.62f; private const val LEG2 = 0.62f
    }
}
