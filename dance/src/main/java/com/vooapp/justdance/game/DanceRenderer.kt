package com.vooapp.justdance.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * Desenha o modo dança: fundo pulsante no ritmo, o "coach" (boneco) mostrando
 * o passo atual, prévia do próximo passo, medidores e a nota (Perfeito/Bom/…).
 * Medidas escaladas por u = largura/540.
 */
class DanceRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }

    fun render(canvas: Canvas, e: DanceEngine) {
        val w = canvas.width; val h = canvas.height; val u = w / 540f

        // Fundo pulsante
        val topBase = Color.rgb(58, 12, 90); val botBase = Color.rgb(200, 30, 110)
        fill.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(topBase, botBase), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill); fill.shader = null
        if (e.state == DanceState.PLAYING && e.beatPulse > 0f) {
            fill.color = Color.argb((e.beatPulse * 40f).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        }

        if (e.state == DanceState.READY) { drawReady(canvas, w, h, u, e); return }

        // Coach (passo atual)
        val cx = w * 0.5f; val cy = h * 0.20f; val scale = h * 0.15f
        val matchGreen = (e.liveMatch).coerceIn(0f, 1f)
        val limb = lerpColor(Color.rgb(255, 255, 255), Color.rgb(120, 240, 150), matchGreen)
        val pop = 1f + e.beatPulse * 0.05f
        drawFigure(canvas, e.currentMove, cx, cy, scale * pop, limb, Color.rgb(255, 220, 120), 12f * u, true)

        // Nome do passo
        txt(canvas, e.currentMove.name, cx, h * 0.10f, 40f * u, true, Color.WHITE, Paint.Align.CENTER)

        // Batidas do compasso (4 pontos)
        val beatInMove = (e.moveProgress * 4f).toInt().coerceIn(0, 3)
        for (i in 0 until 4) {
            val bx = cx - 3f * 26f * u / 2 + i * 26f * u
            fill.color = if (i <= beatInMove) Color.rgb(255, 220, 120) else Color.argb(90, 255, 255, 255)
            canvas.drawCircle(bx, h * 0.135f, (if (i == beatInMove) 8f else 5f) * u, fill)
        }

        // Próximo passo (prévia pequena, canto inferior esquerdo)
        txt(canvas, "Próximo", 70f * u, h * 0.60f, 18f * u, true, Color.argb(220, 255, 255, 255), Paint.Align.CENTER)
        drawFigure(canvas, e.nextMove, 70f * u, h * 0.66f, h * 0.055f, Color.argb(210, 255, 255, 255), Color.argb(210, 255, 220, 120), 5f * u, false)

        drawHud(canvas, w, h, u, e)
        drawRating(canvas, w, h, u, e)
    }

    private fun drawFigure(canvas: Canvas, m: Move, cx: Float, cy: Float, s: Float, limb: Int, head: Int, sw: Float, targets: Boolean) {
        fun sx(p: FloatArray) = cx + p[0] * s
        fun sy(p: FloatArray) = cy + p[1] * s
        val neck = floatArrayOf(0f, 0f); val hipC = floatArrayOf(0f, 1.0f)
        val lSh = floatArrayOf(-0.35f, 0.08f); val rSh = floatArrayOf(0.35f, 0.08f)
        val lHip = floatArrayOf(-0.22f, 1.0f); val rHip = floatArrayOf(0.22f, 1.0f)
        fun mid(a: FloatArray, b: FloatArray) = floatArrayOf((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        val elbowL = mid(lSh, m.handL); val elbowR = mid(rSh, m.handR)
        val kneeL = mid(lHip, m.footL); val kneeR = mid(rHip, m.footR)

        // alvos das mãos/pés (guia)
        if (targets) {
            fill.color = Color.argb(60, 255, 255, 255)
            for (p in listOf(m.handL, m.handR, m.footL, m.footR)) canvas.drawCircle(sx(p), sy(p), 0.14f * s, fill)
        }

        stroke.color = limb; stroke.strokeWidth = sw
        fun bone(a: FloatArray, b: FloatArray) = canvas.drawLine(sx(a), sy(a), sx(b), sy(b), stroke)
        bone(neck, hipC); bone(lSh, rSh); bone(lHip, rHip)
        bone(lSh, elbowL); bone(elbowL, m.handL); bone(rSh, elbowR); bone(elbowR, m.handR)
        bone(lHip, kneeL); bone(kneeL, m.footL); bone(rHip, kneeR); bone(kneeR, m.footR)
        // articulações
        fill.color = limb
        for (p in listOf(m.handL, m.handR, m.footL, m.footR, elbowL, elbowR, kneeL, kneeR)) canvas.drawCircle(sx(p), sy(p), sw * 0.45f, fill)
        // cabeça
        fill.color = head; canvas.drawCircle(cx, cy - 0.35f * s, 0.22f * s, fill)
    }

    private fun drawHud(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        txt(canvas, "${e.score}", 28f * u, 62f * u, 40f * u, true, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "pontos", 30f * u, 88f * u, 16f * u, false, Color.argb(220, 255, 255, 255), Paint.Align.LEFT)
        if (e.combo >= 2) txt(canvas, "Combo x${e.combo}", 28f * u, 120f * u, 22f * u, true, Color.rgb(255, 220, 120), Paint.Align.LEFT)

        // Medidor de gingado (barra vertical à esquerda)
        val bx = 16f * u; val bTop = h * 0.32f; val bBot = h * 0.72f
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(bx - 2f, bTop - 2f, bx + 16f * u + 2f, bBot + 2f, 10f, 10f, fill)
        val fillTop = bBot - (bBot - bTop) * e.groove.coerceIn(0f, 1f)
        fill.color = lerpColor(Color.rgb(255, 90, 120), Color.rgb(120, 240, 150), e.groove)
        canvas.drawRoundRect(bx, fillTop, bx + 16f * u, bBot, 8f, 8f, fill)

        // Sincronia ao vivo (barra inferior)
        val sw = w * 0.5f; val sxx = w * 0.5f - sw / 2; val syy = h * 0.9f; val sh = 16f * u
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(sxx - 3f, syy - 3f, sxx + sw + 3f, syy + sh + 3f, 10f, 10f, fill)
        fill.color = lerpColor(Color.rgb(255, 120, 120), Color.rgb(120, 240, 150), e.liveMatch)
        canvas.drawRoundRect(sxx, syy, sxx + sw * e.liveMatch.coerceIn(0f, 1f), syy + sh, 8f, 8f, fill)
        txt(canvas, "Sincronia", w * 0.5f, syy - 8f * u, 16f * u, true, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawRating(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        if (e.ratingTimer <= 0f || e.lastRating == Rating.NONE) return
        val p = 1f - (e.ratingTimer / 1.1f).coerceIn(0f, 1f)
        val alpha = (if (p > 0.7f) (1f - (p - 0.7f) / 0.3f) else 1f).coerceIn(0f, 1f)
        val scale = 1f + (1f - p) * 0.4f
        val (label, color) = when (e.lastRating) {
            Rating.PERFEITO -> "PERFEITO!" to Color.rgb(255, 215, 0)
            Rating.BOM -> "BOM!" to Color.rgb(120, 230, 140)
            Rating.OK -> "OK" to Color.rgb(90, 200, 255)
            else -> "ERROU" to Color.rgb(230, 110, 110)
        }
        text.color = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = true; text.textSize = 64f * u * scale
        shadow.textAlign = Paint.Align.CENTER; shadow.isFakeBoldText = true; shadow.textSize = 64f * u * scale
        shadow.alpha = (alpha * 120).toInt()
        canvas.drawText(label, w * 0.5f + 3f, h * 0.46f + 3f, shadow)
        canvas.drawText(label, w * 0.5f, h * 0.46f, text)
    }

    private fun drawReady(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        fill.color = Color.argb(120, 20, 5, 40); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        txt(canvas, "JustVoo Dance", w * 0.5f, h * 0.20f, 52f * u, true, Color.WHITE, Paint.Align.CENTER)
        txt(canvas, "Siga o dançarino no ritmo!", w * 0.5f, h * 0.26f, 22f * u, false, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        // boneco de exemplo
        drawFigure(canvas, Move.ALL[3], w * 0.5f, h * 0.36f, h * 0.14f, Color.WHITE, Color.rgb(255, 220, 120), 12f * u, false)
        txt(canvas, "Fique de corpo inteiro na câmera", w * 0.5f, h * 0.74f, 22f * u, true, Color.WHITE, Paint.Align.CENTER)
        if (e.startProgress > 0.01f) {
            val bw = w * 0.5f; val bx = w * 0.5f - bw / 2; val by = h * 0.80f; val bh = 14f * u
            fill.color = Color.argb(130, 0, 0, 0); canvas.drawRoundRect(bx - 3f, by - 3f, bx + bw + 3f, by + bh + 3f, 10f, 10f, fill)
            fill.color = Color.rgb(120, 230, 140); canvas.drawRoundRect(bx, by, bx + bw * e.startProgress.coerceIn(0f, 1f), by + bh, 8f, 8f, fill)
            txt(canvas, "Começando…", w * 0.5f, by - 10f * u, 16f * u, true, Color.rgb(150, 240, 170), Paint.Align.CENTER)
        } else {
            txt(canvas, "(fique visível de corpo inteiro para começar)", w * 0.5f, h * 0.80f, 16f * u, false, Color.argb(200, 255, 210, 160), Paint.Align.CENTER)
        }
    }

    private fun txt(canvas: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int, align: Paint.Align) {
        text.textSize = size; text.isFakeBoldText = bold; text.textAlign = align
        shadow.textSize = size; shadow.isFakeBoldText = bold; shadow.textAlign = align; shadow.alpha = 120
        canvas.drawText(s, x + 2f, y + 2f, shadow); text.color = color; canvas.drawText(s, x, y, text)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt().coerceIn(0, 255))
    }
}
