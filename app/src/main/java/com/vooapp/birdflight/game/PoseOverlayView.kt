package com.vooapp.birdflight.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.vooapp.birdflight.camera.Joint
import com.vooapp.birdflight.camera.PoseFrame

/**
 * Desenha o esqueleto detectado sobre a prévia da câmera (canto da tela),
 * para o jogador ver que está sendo rastreado. Espelha horizontalmente para
 * combinar com a prévia frontal (efeito de espelho).
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    @Volatile private var frame: PoseFrame? = null

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 120, 230, 140)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    fun updateFrame(frame: PoseFrame?) {
        this.frame = frame
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val f = frame ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        fun px(j: Joint) = (1f - j.x) * w // espelhado horizontalmente
        fun py(j: Joint) = j.y * h

        fun bone(a: Joint?, b: Joint?) {
            if (a == null || b == null) return
            canvas.drawLine(px(a), py(a), px(b), py(b), bonePaint)
        }
        fun dot(j: Joint?) {
            if (j == null) return
            canvas.drawCircle(px(j), py(j), 7f, jointPaint)
        }

        bone(f.leftShoulder, f.rightShoulder)
        bone(f.leftShoulder, f.leftElbow)
        bone(f.leftElbow, f.leftWrist)
        bone(f.rightShoulder, f.rightElbow)
        bone(f.rightElbow, f.rightWrist)
        bone(f.leftShoulder, f.leftHip)
        bone(f.rightShoulder, f.rightHip)
        bone(f.leftHip, f.rightHip)

        listOf(
            f.leftShoulder, f.rightShoulder, f.leftElbow, f.rightElbow,
            f.leftWrist, f.rightWrist, f.leftHip, f.rightHip
        ).forEach { dot(it) }
    }
}
