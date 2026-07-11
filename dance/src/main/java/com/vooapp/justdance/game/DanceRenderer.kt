package com.vooapp.justdance.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Renderiza o modo dança no estilo Just Dance: um pictograma neon rosa (a pose
 * a imitar) com contorno brilhante e setas de movimento, palco colorido com
 * holofotes e grade em perspectiva, barra de jogadores no topo, nota grande
 * com brilho e HUD. Medidas escaladas por u = largura/540.
 */
class DanceRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val color: Int)
    private val particles = ArrayList<Particle>()
    private var prevRatingCount = 0
    private var lastNanos = 0L
    private val rng = Random()

    private val pinkTop = Color.rgb(255, 150, 220)
    private val pinkMid = Color.rgb(255, 80, 190)
    private val pinkLow = Color.rgb(232, 30, 150)
    private val glowPink = Color.rgb(255, 60, 180)
    private val cyan = Color.rgb(57, 224, 255)

    // Painéis coloridos de fundo: x, y, w, h, ângulo, cor
    private val panels = arrayOf(
        floatArrayOf(0.11f, 0.14f, 0.22f, 0.33f, -0.2f, 0f), floatArrayOf(0.70f, 0.12f, 0.20f, 0.39f, 0.15f, 1f),
        floatArrayOf(0.28f, 0.48f, 0.17f, 0.22f, 0.1f, 2f), floatArrayOf(0.78f, 0.52f, 0.18f, 0.28f, -0.15f, 3f))
    private val panelColors = intArrayOf(Color.rgb(23, 195, 214), Color.rgb(255, 210, 63), Color.rgb(140, 255, 90), Color.rgb(255, 47, 182))
    private val npcNames = arrayOf("Ana", "Léo")
    private val npcPool = arrayOf("PERFEITO", "BOM", "SUPER", "OK")

    fun render(canvas: Canvas, e: DanceEngine) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
        val time = now / 1e9f
        val w = canvas.width; val h = canvas.height; val u = w / 540f
        val floorY = h * 0.68f

        // Fundo vibrante
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(58, 10, 160), Color.rgb(184, 27, 142), Color.rgb(255, 90, 47)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill); fill.shader = null
        for (i in panels.indices) {
            val p = panels[i]
            canvas.save(); canvas.translate(p[0] * w, p[1] * h); canvas.rotate(p[4] * 57.3f)
            fill.color = withAlpha(panelColors[i], 72); canvas.drawRect(0f, 0f, p[2] * w, p[3] * h, fill); canvas.restore()
        }
        drawSpotlights(canvas, w, floorY, time, e.beatPulse)
        drawFloor(canvas, w, h, floorY)

        if (e.state == DanceState.READY) { drawReady(canvas, w, h, u, e, time); return }

        val cx = w * 0.5f; val cy = h * 0.30f; val s = h * 0.17f * (1f + e.beatPulse * 0.03f)
        pictogram(canvas, cx, cy, s, e.currentMove, u, true)
        // Setas de movimento nas mãos
        arrow(canvas, cx + e.currentMove.handR[0] * s, cy + e.currentMove.handR[1] * s - 10f * u, 26f * u, -2.4f, -0.6f)
        arrow(canvas, cx + e.currentMove.handL[0] * s, cy + e.currentMove.handL[1] * s - 10f * u, 26f * u, 3.7f, 5.5f)

        drawPlayerBar(canvas, w, u, e)
        stepParticles(dt); handleRatingFx(e, cx, cy + s * 0.6f, u); drawParticles(canvas)

        // Próximo (painel canto inferior direito)
        fill.color = Color.argb(102, 0, 0, 0); canvas.drawRoundRect(w - 150f * u, h * 0.60f, w - 18f * u, h * 0.60f + 150f * u, 18f, 18f, fill)
        txt(canvas, "PRÓXIMO", w - 84f * u, h * 0.625f, 15f * u, true, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        pictogram(canvas, w - 84f * u, h * 0.66f, h * 0.05f, e.nextMove, u, false)

        drawName(canvas, w, h, u, e)
        drawRating(canvas, w, h, u, e)
        drawHud(canvas, w, h, u, e)
    }

    // ------------------------------------------------------------ pictograma

    private fun pictogram(c: Canvas, cx: Float, cy: Float, s: Float, m: Move, u: Float, glow: Boolean) {
        fun j(x: Float, y: Float) = floatArrayOf(cx + x * s, cy + y * s)
        fun mid(a: FloatArray, b: FloatArray) = floatArrayOf((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        val neck = j(0f, 0f); val hipC = j(0f, 1.02f); val lSh = j(-0.34f, 0.06f); val rSh = j(0.34f, 0.06f)
        val lHip = j(-0.2f, 1.02f); val rHip = j(0.2f, 1.02f)
        val hL = j(m.handL[0], m.handL[1]); val hR = j(m.handR[0], m.handR[1]); val fL = j(m.footL[0], m.footL[1]); val fR = j(m.footR[0], m.footR[1])
        val eL = mid(lSh, hL); val eR = mid(rSh, hR); val kL = mid(lHip, fL); val kR = mid(rHip, fR)
        val bones = arrayOf(
            Triple(neck, hipC, 0.19f), Triple(lSh, rSh, 0.14f), Triple(lSh, eL, 0.12f), Triple(eL, hL, 0.10f),
            Triple(rSh, eR, 0.12f), Triple(eR, hR, 0.10f), Triple(lHip, kL, 0.15f), Triple(kL, fL, 0.12f),
            Triple(rHip, kR, 0.15f), Triple(kR, fR, 0.12f))
        val head = j(0f, -0.34f); val hr = 0.26f * s

        // 1) contorno branco com brilho
        if (glow) stroke.setShadowLayer(30f, 0f, 0f, glowPink)
        stroke.color = Color.WHITE
        for (b in bones) { stroke.strokeWidth = 2f * b.third * s + 9f * u; c.drawLine(b.first[0], b.first[1], b.second[0], b.second[1], stroke) }
        if (glow) fill.setShadowLayer(30f, 0f, 0f, glowPink)
        fill.color = Color.WHITE; c.drawCircle(head[0], head[1], hr + 5f * u, fill)
        stroke.clearShadowLayer(); fill.clearShadowLayer()

        // 2) preenchimento neon
        val grad = LinearGradient(cx, cy - 0.5f * s, cx, cy + 2f * s, intArrayOf(pinkTop, pinkMid, pinkLow), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        stroke.shader = grad
        for (b in bones) { stroke.strokeWidth = 2f * b.third * s; c.drawLine(b.first[0], b.first[1], b.second[0], b.second[1], stroke) }
        stroke.shader = null
        fill.shader = grad; c.drawCircle(head[0], head[1], hr, fill); fill.shader = null

        // 3) destaque interno
        stroke.color = Color.argb(120, 255, 255, 255)
        for (i in 0 until 7) { val b = bones[i]; stroke.strokeWidth = max(2f, b.third * s * 0.5f); c.drawLine(b.first[0] - b.third * s * 0.3f, b.first[1], b.second[0] - b.third * s * 0.3f, b.second[1], stroke) }
    }

    private fun arrow(c: Canvas, x: Float, y: Float, r: Float, a0: Float, a1: Float) {
        stroke.setShadowLayer(12f, 0f, 0f, cyan); stroke.color = cyan; stroke.strokeWidth = 6f
        val rect = android.graphics.RectF(x - r, y - r, x + r, y + r)
        c.drawArc(rect, Math.toDegrees(a0.toDouble()).toFloat(), Math.toDegrees((a1 - a0).toDouble()).toFloat(), false, stroke)
        val ex = x + cos(a1) * r; val ey = y + sin(a1) * r; val ta = a1 + Math.PI.toFloat() / 2f
        c.drawLine(ex, ey, ex + cos(ta - 0.5f) * 14f, ey + sin(ta - 0.5f) * 14f, stroke)
        c.drawLine(ex, ey, ex + cos(ta + 0.4f) * 14f, ey + sin(ta + 0.4f) * 14f, stroke)
        stroke.clearShadowLayer()
    }

    // ------------------------------------------------------------ palco

    private fun drawSpotlights(canvas: Canvas, w: Int, floorY: Float, time: Float, pulse: Float) {
        val specs = arrayOf(Triple(-1f, cyan, 0f), Triple(1f, Color.rgb(255, 80, 200), 1.2f))
        for (sp in specs) {
            val ang = sp.third + sin(time * 1.3f + sp.first) * 0.18f
            val ox = w * (0.5f + sp.first * 0.42f); val tx = w * 0.5f + sin(ang) * w * 0.3f
            fill.color = withAlpha(sp.second, ((0.14f + pulse * 0.12f) * 255).toInt())
            canvas.drawPath(android.graphics.Path().apply { moveTo(ox - 30f, -30f); lineTo(tx - 130f, floorY); lineTo(tx + 130f, floorY); lineTo(ox + 30f, -30f); close() }, fill)
        }
    }

    private fun drawFloor(canvas: Canvas, w: Int, h: Int, floorY: Float) {
        fill.shader = LinearGradient(0f, floorY, 0f, h.toFloat(), intArrayOf(Color.rgb(42, 14, 90), Color.rgb(10, 4, 22)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, floorY, w.toFloat(), h.toFloat(), fill); fill.shader = null
        stroke.color = Color.argb(46, 120, 220, 255); stroke.strokeWidth = 1.5f
        for (i in -6..6) canvas.drawLine(w * 0.5f + i * 44f, h.toFloat(), w * 0.5f, floorY, stroke)
        for (i in 1..7) { val t = i / 7f; val y = floorY + (h - floorY) * (t * t); canvas.drawLine(0f, y, w.toFloat(), y, stroke) }
    }

    // ------------------------------------------------------------ HUD / telas

    private fun drawPlayerBar(canvas: Canvas, w: Int, u: Float, e: DanceEngine) {
        val entries = ArrayList<Triple<String, String, Int>>()
        val (vl, vc) = ratingLabel(e.lastRating)
        entries.add(Triple("Você", vl, vc))
        val move = e.beatCount / 4
        for (i in npcNames.indices) { val lbl = npcPool[(move + i * 2) % npcPool.size]; entries.add(Triple(npcNames[i], lbl, labelColor(lbl))) }
        val cw = w.toFloat() / entries.size
        for (i in entries.indices) {
            val cx = cw * i + cw / 2; val e2 = entries[i]
            fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(cx - cw * 0.44f, 34f * u, cx + cw * 0.44f, 82f * u, 22f, 22f, fill)
            txt(canvas, e2.first, cx - cw * 0.36f, 66f * u, 17f * u, true, Color.WHITE, Paint.Align.LEFT)
            text.setShadowLayer(10f, 0f, 0f, e2.third)
            txt(canvas, e2.second, cx + cw * 0.40f, 66f * u, 15f * u, true, e2.third, Paint.Align.RIGHT)
            text.clearShadowLayer()
        }
    }

    private fun drawName(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        txt(canvas, e.currentMove.name, w * 0.5f, h * 0.115f, 30f * u, true, Color.WHITE, Paint.Align.CENTER)
        val beatInMove = (e.moveProgress * 4f).toInt().coerceIn(0, 3)
        for (i in 0 until 4) {
            val bx = w * 0.5f - 3 * 26f * u / 2 + i * 26f * u
            fill.color = if (i <= beatInMove) Color.rgb(255, 47, 182) else Color.argb(90, 255, 255, 255)
            canvas.drawCircle(bx, h * 0.14f, (if (i == beatInMove) 8f else 5f) * u, fill)
        }
    }

    private fun drawRating(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        if (e.ratingTimer <= 0f || e.lastRating == Rating.NONE) return
        val p = 1f - (e.ratingTimer / 1.1f).coerceIn(0f, 1f)
        val alpha = (if (p > 0.7f) (1f - (p - 0.7f) / 0.3f) else 1f).coerceIn(0f, 1f)
        val (label, color) = ratingLabel(e.lastRating)
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = true; text.textSize = 70f * u * (1f + (1f - p) * 0.12f)
        text.setShadowLayer(34f, 0f, 0f, withAlpha(color, (alpha * 255).toInt()))
        text.color = withAlpha(Color.WHITE, (alpha * 255).toInt()); canvas.drawText(label, w * 0.5f, h * 0.52f, text)
        text.clearShadowLayer()
        text.color = withAlpha(color, (alpha * 255).toInt()); canvas.drawText(label, w * 0.5f, h * 0.52f, text)
    }

    private fun drawHud(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(18f * u, h - 92f * u, 228f * u, h - 18f * u, 16f, 16f, fill)
        txt(canvas, thousands(e.score), 32f * u, h - 52f * u, 34f * u, true, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "PONTOS", 34f * u, h - 30f * u, 15f * u, true, Color.argb(230, 180, 240, 255), Paint.Align.LEFT)
        if (e.combo >= 2) {
            text.setShadowLayer(14f, 0f, 0f, Color.rgb(255, 47, 182))
            txt(canvas, "x${e.combo}", 218f * u, h - 46f * u, 28f * u, true, Color.rgb(255, 159, 224), Paint.Align.RIGHT)
            text.clearShadowLayer()
        }
        val sw = w * 0.5f; val sxx = w * 0.5f - sw / 2; val syy = h - 40f * u; val shh = 16f * u
        fill.color = Color.argb(102, 0, 0, 0); canvas.drawRoundRect(sxx - 3f, syy - 3f, sxx + sw + 3f, syy + shh + 3f, 11f, 11f, fill)
        fill.shader = LinearGradient(sxx, 0f, sxx + sw, 0f, intArrayOf(cyan, Color.rgb(255, 47, 182)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(sxx, syy, sxx + sw * e.liveMatch.coerceIn(0f, 1f), syy + shh, 8f, 8f, fill); fill.shader = null
    }

    private fun drawReady(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine, time: Float) {
        fill.color = Color.argb(110, 20, 5, 40); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        txt(canvas, "JustVoo Dance", w * 0.5f, h * 0.15f, 52f * u, true, Color.WHITE, Paint.Align.CENTER)
        txt(canvas, "Imite o pictograma no ritmo!", w * 0.5f, h * 0.21f, 22f * u, false, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        pictogram(canvas, w * 0.5f, h * 0.34f, h * 0.15f, Move.ALL[3], u, true)
        txt(canvas, "Fique de corpo inteiro na câmera", w * 0.5f, h * 0.76f, 22f * u, true, Color.WHITE, Paint.Align.CENTER)
        if (e.startProgress > 0.01f) {
            val bw = w * 0.5f; val bx = w * 0.5f - bw / 2; val by = h * 0.81f; val bh = 14f * u
            fill.color = Color.argb(130, 0, 0, 0); canvas.drawRoundRect(bx - 3f, by - 3f, bx + bw + 3f, by + bh + 3f, 10f, 10f, fill)
            fill.color = Color.rgb(120, 230, 140); canvas.drawRoundRect(bx, by, bx + bw * e.startProgress.coerceIn(0f, 1f), by + bh, 8f, 8f, fill)
            txt(canvas, "Começando…", w * 0.5f, by - 10f * u, 16f * u, true, Color.rgb(150, 240, 170), Paint.Align.CENTER)
        }
    }

    // ------------------------------------------------------------ efeitos

    private fun handleRatingFx(e: DanceEngine, x: Float, y: Float, u: Float) {
        if (e.ratingCount != prevRatingCount) {
            prevRatingCount = e.ratingCount
            if (e.lastRating == Rating.PERFEITO || e.lastRating == Rating.BOM) {
                val colors = intArrayOf(Color.rgb(255, 47, 182), cyan, Color.WHITE)
                val n = if (e.lastRating == Rating.PERFEITO) 26 else 14
                for (i in 0 until n) { val a = rng.nextFloat() * 6.283f; val sp = (80f + rng.nextFloat() * 170f) * u
                    particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 0.7f + rng.nextFloat() * 0.4f, 1.1f, (3f + rng.nextFloat() * 4f) * u, colors[i % 3])) }
            }
        }
    }

    private fun stepParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) { val p = it.next(); p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 50f * dt; p.life -= dt; if (p.life <= 0f) it.remove() }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) { fill.color = withAlpha(p.color, ((p.life / p.maxLife).coerceIn(0f, 1f) * 255).toInt()); canvas.drawCircle(p.x, p.y, p.size, fill) }
    }

    // ------------------------------------------------------------ util

    private fun ratingLabel(r: Rating): Pair<String, Int> = when (r) {
        Rating.PERFEITO -> "PERFEITO" to cyan
        Rating.BOM -> "BOM" to Color.rgb(140, 255, 90)
        Rating.OK -> "OK" to Color.rgb(255, 210, 63)
        Rating.ERROU -> "OPS" to Color.rgb(255, 110, 110)
        Rating.NONE -> "—" to Color.WHITE
    }

    private fun labelColor(l: String) = when (l) {
        "PERFEITO" -> cyan; "BOM" -> Color.rgb(140, 255, 90); "SUPER" -> Color.rgb(255, 210, 63); else -> Color.rgb(255, 210, 63)
    }

    private fun thousands(n: Int): String {
        val s = n.toString(); val sb = StringBuilder()
        for (i in s.indices) { if (i > 0 && (s.length - i) % 3 == 0) sb.append('.'); sb.append(s[i]) }
        return sb.toString()
    }

    private fun txt(canvas: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int, align: Paint.Align) {
        text.textSize = size; text.isFakeBoldText = bold; text.textAlign = align
        shadowText.textSize = size; shadowText.isFakeBoldText = bold; shadowText.textAlign = align; shadowText.alpha = 120
        canvas.drawText(s, x + 2f, y + 2f, shadowText); text.color = color; canvas.drawText(s, x, y, text)
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
}
