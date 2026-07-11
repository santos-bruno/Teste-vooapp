package com.vooapp.justdance.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.vooapp.justdance.camera.Joint
import com.vooapp.justdance.camera.PoseSnapshot

/** Desenha o esqueleto de corpo inteiro sobre a prévia da câmera (espelhado). */
class PoseOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    @Volatile private var snap: PoseSnapshot? = null

    private val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 90, 200); strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    fun updateSnapshot(s: PoseSnapshot?) { snap = s; postInvalidateOnAnimation() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = snap ?: return
        val w = width.toFloat(); val h = height.toFloat()
        fun px(j: Joint) = (1f - j.x) * w
        fun py(j: Joint) = j.y * h
        fun line(a: Joint?, b: Joint?) { if (a != null && b != null) canvas.drawLine(px(a), py(a), px(b), py(b), bone) }
        line(s.leftShoulder, s.rightShoulder)
        line(s.leftShoulder, s.leftElbow); line(s.leftElbow, s.leftWrist)
        line(s.rightShoulder, s.rightElbow); line(s.rightElbow, s.rightWrist)
        line(s.leftShoulder, s.leftHip); line(s.rightShoulder, s.rightHip); line(s.leftHip, s.rightHip)
        line(s.leftHip, s.leftKnee); line(s.leftKnee, s.leftAnkle)
        line(s.rightHip, s.rightKnee); line(s.rightKnee, s.rightAnkle)
        for (j in listOf(s.leftShoulder, s.rightShoulder, s.leftElbow, s.rightElbow, s.leftWrist, s.rightWrist,
                s.leftHip, s.rightHip, s.leftKnee, s.rightKnee, s.leftAnkle, s.rightAnkle)) {
            if (j != null) canvas.drawCircle(px(j), py(j), 6f, dot)
        }
    }
}
