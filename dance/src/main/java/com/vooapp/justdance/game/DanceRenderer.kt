package com.vooapp.justdance.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Renderiza o modo dança com um dançarino 3D (membros = cápsulas sombreadas,
 * rotação/perspectiva), palco neon com holofotes, chão espelhado com grade,
 * reflexo, partículas e HUD. Medidas escaladas por u = largura/540.
 */
class DanceRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val color: Int)
    private val particles = ArrayList<Particle>()
    private var prevRatingCount = 0
    private var lastNanos = 0L
    private val rng = Random()

    private val suit = Color.rgb(23, 195, 214)
    private val suit2 = Color.rgb(15, 122, 153)
    private val acc = Color.rgb(255, 47, 182)
    private val skin = Color.rgb(42, 47, 69)

    fun render(canvas: Canvas, e: DanceEngine) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
        val time = now / 1e9f
        val w = canvas.width; val h = canvas.height; val u = w / 540f
        val floorY = h * 0.66f

        // Fundo
        fill.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(42, 10, 74), Color.rgb(90, 15, 110), Color.rgb(18, 6, 31)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill); fill.shader = null

        drawSpotlights(canvas, w, floorY, time, e.beatPulse)
        drawFloor(canvas, w, h, floorY, e.beatPulse)

        if (e.state == DanceState.READY) { drawReady(canvas, w, h, u, e, time); return }

        val cx = w * 0.5f; val cy = h * 0.20f; val s = h * 0.15f * (1f + e.beatPulse * 0.04f)
        val rotA = 0.32f + sin(time * 1.6f) * 0.06f
        val reflColor = if (e.liveMatch > 0.6f) Color.rgb(63, 224, 160) else suit

        // Reflexo no chão
        val sc = canvas.save()
        canvas.saveLayerAlpha(0f, floorY, w.toFloat(), h.toFloat(), 48)
        canvas.translate(0f, (cy + s * 2.0f) * 2f); canvas.scale(1f, -1f)
        dancer(canvas, cx, cy, s, rotA, e.currentMove, reflColor)
        canvas.restoreToCount(sc)

        // Dançarino
        dancer(canvas, cx, cy, s, rotA, e.currentMove, reflColor)

        txt(canvas, e.currentMove.name, cx, h * 0.095f, 40f * u, true, Color.WHITE, Paint.Align.CENTER)
        val beatInMove = (e.moveProgress * 4f).toInt().coerceIn(0, 3)
        for (i in 0 until 4) {
            val bx = cx - 3 * 26f * u / 2 + i * 26f * u
            fill.color = if (i <= beatInMove) acc else Color.argb(90, 255, 255, 255)
            canvas.drawCircle(bx, h * 0.125f, (if (i == beatInMove) 8f else 5f) * u, fill)
        }

        stepParticles(dt); handleRatingFx(e, cx, h * 0.30f, u); drawParticles(canvas)

        // Próximo (painel)
        fill.color = Color.argb(72, 0, 0, 0); canvas.drawRoundRect(20f * u, h * 0.55f, 140f * u, h * 0.55f + 120f * u, 16f, 16f, fill)
        txt(canvas, "Próximo", 80f * u, h * 0.575f, 16f * u, true, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        dancer(canvas, 80f * u, h * 0.60f, h * 0.04f, 0.32f, e.nextMove, suit)

        drawHud(canvas, w, h, u, e)
        drawRating(canvas, w, h, u, e)
    }

    // ------------------------------------------------------------ dançarino 3D

    private fun rotProj(px: Float, py: Float, pz: Float, a: Float, cx: Float, cy: Float, s: Float): FloatArray {
        val c = cos(a); val si = sin(a); val xr = px * c - pz * si; val zr = px * si + pz * c; val pe = 3.4f / (3.4f + zr)
        return floatArrayOf(cx + xr * pe * s, cy + py * pe * s, zr, pe)
    }

    private fun capsule(c: Canvas, a: FloatArray, b: FloatArray, rA: Float, rB: Float, base: Int) {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        val l = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f); val nx = -dy / l; val ny = dx / l
        fill.shader = LinearGradient(a[0] + nx * rA, a[1] + ny * rA, a[0] - nx * rA, a[1] - ny * rA,
            intArrayOf(shade(base, 1.5f), base, shade(base, 0.55f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(a[0], a[1], rA, fill); c.drawCircle(b[0], b[1], rB, fill)
        val p = Path().apply { moveTo(a[0] + nx * rA, a[1] + ny * rA); lineTo(b[0] + nx * rB, b[1] + ny * rB); lineTo(b[0] - nx * rB, b[1] - ny * rB); lineTo(a[0] - nx * rA, a[1] - ny * rA); close() }
        c.drawPath(p, fill); fill.shader = null
        stroke.color = Color.argb(130, 255, 255, 255); stroke.strokeWidth = max(1f, rA * 0.28f)
        c.drawLine(a[0] + nx * rA * 0.9f, a[1] + ny * rA * 0.9f, b[0] + nx * rB * 0.9f, b[1] + ny * rB * 0.9f, stroke)
    }

    private fun dancer(c: Canvas, cx: Float, cy: Float, s: Float, rotA: Float, m: Move, suitColor: Int) {
        fun local(x: Float, y: Float, z: Float) = rotProj(x, y, z, rotA, cx, cy, s)
        fun mid(x0: Float, y0: Float, x1: Float, y1: Float, z: Float) = local((x0 + x1) / 2, (y0 + y1) / 2, z)

        val zHand = -0.14f; val zElbow = -0.2f; val zKnee = -0.14f; val zFoot = 0f; val zHead = -0.06f
        val head = local(0f, -0.35f, zHead); val neck = local(0f, 0f, 0f); val hipC = local(0f, 1f, 0f)
        val lSh = local(-0.35f, 0.08f, 0f); val rSh = local(0.35f, 0.08f, 0f)
        val lHip = local(-0.22f, 1f, 0f); val rHip = local(0.22f, 1f, 0f)
        val hL = local(m.handL[0], m.handL[1], zHand); val hR = local(m.handR[0], m.handR[1], zHand)
        val fL = local(m.footL[0], m.footL[1], zFoot); val fR = local(m.footR[0], m.footR[1], zFoot)
        val eL = mid(-0.35f, 0.08f, m.handL[0], m.handL[1], zElbow); val eR = mid(0.35f, 0.08f, m.handR[0], m.handR[1], zElbow)
        val kL = mid(-0.22f, 1f, m.footL[0], m.footL[1], zKnee); val kR = mid(0.22f, 1f, m.footR[0], m.footR[1], zKnee)

        val items = ArrayList<Pair<Float, () -> Unit>>()
        items.add(0.02f to {
            fill.shader = LinearGradient(neck[0], neck[1], hipC[0], hipC[1], intArrayOf(shade(suitColor, 1.25f), suit2), null, Shader.TileMode.CLAMP)
            c.drawPath(Path().apply { moveTo(lSh[0], lSh[1]); lineTo(rSh[0], rSh[1]); lineTo(rHip[0], rHip[1]); lineTo(lHip[0], lHip[1]); close() }, fill); fill.shader = null
            stroke.color = acc; stroke.strokeWidth = max(2f, 3f * neck[3] * s / 9f); c.drawLine(neck[0], neck[1], hipC[0], hipC[1], stroke)
        })
        items.add((lSh[2] + eL[2]) / 2 to { capsule(c, lSh, eL, 0.11f * lSh[3] * s, 0.09f * eL[3] * s, suitColor) })
        items.add((eL[2] + hL[2]) / 2 to { capsule(c, eL, hL, 0.09f * eL[3] * s, 0.07f * hL[3] * s, suitColor) })
        items.add((rSh[2] + eR[2]) / 2 to { capsule(c, rSh, eR, 0.11f * rSh[3] * s, 0.09f * eR[3] * s, suitColor) })
        items.add((eR[2] + hR[2]) / 2 to { capsule(c, eR, hR, 0.09f * eR[3] * s, 0.07f * hR[3] * s, suitColor) })
        items.add((lHip[2] + kL[2]) / 2 to { capsule(c, lHip, kL, 0.14f * lHip[3] * s, 0.11f * kL[3] * s, suit2) })
        items.add((kL[2] + fL[2]) / 2 to { capsule(c, kL, fL, 0.11f * kL[3] * s, 0.08f * fL[3] * s, suit2) })
        items.add((rHip[2] + kR[2]) / 2 to { capsule(c, rHip, kR, 0.14f * rHip[3] * s, 0.11f * kR[3] * s, suit2) })
        items.add((kR[2] + fR[2]) / 2 to { capsule(c, kR, fR, 0.11f * kR[3] * s, 0.08f * fR[3] * s, suit2) })
        items.add(head[2] to {
            val r = 0.24f * head[3] * s
            fill.shader = RadialGradient(head[0] - r * 0.3f, head[1] - r * 0.4f, r * 1.2f, shade(skin, 1.7f), shade(skin, 0.7f), Shader.TileMode.CLAMP)
            c.drawCircle(head[0], head[1], r, fill); fill.shader = null
            fill.color = acc; c.drawOval(head[0] - r * 0.7f, head[1] - r * 0.33f, head[0] + r * 0.7f, head[1] + r * 0.23f, fill)
            fill.color = Color.argb(217, 255, 255, 255); c.drawOval(head[0] - r * 0.48f, head[1] - r * 0.18f, head[0] - r * 0.12f, head[1] + r * 0.02f, fill)
        })
        items.sortByDescending { it.first }
        for (it in items) it.second()
    }

    // ------------------------------------------------------------ palco

    private fun drawSpotlights(canvas: Canvas, w: Int, floorY: Float, time: Float, pulse: Float) {
        val specs = arrayOf(Triple(-1f, Color.rgb(23, 195, 214), 0f), Triple(1f, Color.rgb(255, 47, 182), 1.2f))
        for (sp in specs) {
            val ang = sp.third + sin(time * 1.3f + sp.first) * 0.18f
            val ox = w * (0.5f + sp.first * 0.42f); val tx = w * 0.5f + sin(ang) * w * 0.3f
            val a = (0.12f + pulse * 0.10f)
            fill.color = withAlpha(sp.second, (a * 255).toInt())
            canvas.drawPath(Path().apply { moveTo(ox - 30f, -30f); lineTo(tx - 120f, floorY); lineTo(tx + 120f, floorY); lineTo(ox + 30f, -30f); close() }, fill)
        }
    }

    private fun drawFloor(canvas: Canvas, w: Int, h: Int, floorY: Float, pulse: Float) {
        fill.shader = LinearGradient(0f, floorY, 0f, h.toFloat(), intArrayOf(Color.rgb(58, 16, 96), Color.rgb(10, 4, 22)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, floorY, w.toFloat(), h.toFloat(), fill); fill.shader = null
        stroke.color = Color.argb(40, 120, 220, 255); stroke.strokeWidth = 1.5f
        val vpx = w * 0.5f
        for (i in -6..6) canvas.drawLine(w * 0.5f + i * 40f, h.toFloat(), vpx, floorY, stroke)
        for (i in 1..7) { val t = i / 7f; val y = floorY + (h - floorY) * (t * t); canvas.drawLine(0f, y, w.toFloat(), y, stroke) }
        stroke.color = Color.argb((pulse * 128).toInt().coerceIn(0, 255), 255, 255, 255); stroke.strokeWidth = 4f
        val rr = (0.6f + pulse * 0.6f)
        canvas.drawOval(w * 0.5f - 120f * rr, floorY + 40f - 34f * rr, w * 0.5f + 120f * rr, floorY + 40f + 34f * rr, stroke)
    }

    // ------------------------------------------------------------ efeitos

    private fun handleRatingFx(e: DanceEngine, x: Float, y: Float, u: Float) {
        if (e.ratingCount != prevRatingCount) {
            prevRatingCount = e.ratingCount
            if (e.lastRating == Rating.PERFEITO || e.lastRating == Rating.BOM) {
                val colors = intArrayOf(acc, Color.rgb(23, 195, 214), Color.WHITE)
                val n = if (e.lastRating == Rating.PERFEITO) 26 else 14
                for (i in 0 until n) { val a = rng.nextFloat() * 6.283f; val sp = (80f + rng.nextFloat() * 160f) * u
                    particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 0.7f + rng.nextFloat() * 0.4f, 1.1f, (3f + rng.nextFloat() * 4f) * u, colors[i % 3])) }
            }
        }
    }

    private fun stepParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) { val p = it.next(); p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 60f * dt; p.life -= dt; if (p.life <= 0f) it.remove() }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) { fill.color = withAlpha(p.color, ((p.life / p.maxLife).coerceIn(0f, 1f) * 255).toInt()); canvas.drawCircle(p.x, p.y, p.size, fill) }
    }

    // ------------------------------------------------------------ HUD / telas

    private fun drawHud(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        fill.color = Color.argb(72, 0, 0, 0); canvas.drawRoundRect(18f * u, 30f * u, 208f * u, 116f * u, 16f, 16f, fill)
        txt(canvas, "${e.score}", 32f * u, 70f * u, 38f * u, true, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "PONTOS", 34f * u, 94f * u, 15f * u, true, Color.argb(230, 180, 240, 255), Paint.Align.LEFT)
        if (e.combo >= 2) txt(canvas, "COMBO x${e.combo}", 34f * u, 116f * u, 18f * u, true, Color.rgb(255, 143, 214), Paint.Align.LEFT)

        val bx = 16f * u; val bTop = h * 0.34f; val bBot = h * 0.60f
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(bx - 2f, bTop - 2f, bx + 16f * u + 2f, bBot + 2f, 10f, 10f, fill)
        val ft = bBot - (bBot - bTop) * e.groove.coerceIn(0f, 1f)
        fill.shader = LinearGradient(0f, bBot, 0f, bTop, intArrayOf(acc, suit), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bx, ft, bx + 16f * u, bBot, 8f, 8f, fill); fill.shader = null

        val sw = w * 0.56f; val sxx = w * 0.5f - sw / 2; val syy = h * 0.9f; val shh = 18f * u
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(sxx - 3f, syy - 3f, sxx + sw + 3f, syy + shh + 3f, 11f, 11f, fill)
        fill.shader = LinearGradient(sxx, 0f, sxx + sw, 0f, intArrayOf(suit, Color.rgb(63, 224, 160)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(sxx, syy, sxx + sw * e.liveMatch.coerceIn(0f, 1f), syy + shh, 9f, 9f, fill); fill.shader = null
        txt(canvas, "SINCRONIA", w * 0.5f, syy - 8f * u, 15f * u, true, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawRating(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        if (e.ratingTimer <= 0f || e.lastRating == Rating.NONE) return
        val p = 1f - (e.ratingTimer / 1.1f).coerceIn(0f, 1f)
        val alpha = (if (p > 0.7f) (1f - (p - 0.7f) / 0.3f) else 1f).coerceIn(0f, 1f)
        val (label, color) = when (e.lastRating) {
            Rating.PERFEITO -> "PERFEITO!" to Color.rgb(255, 215, 0)
            Rating.BOM -> "BOM!" to Color.rgb(120, 230, 140)
            Rating.OK -> "OK" to Color.rgb(90, 200, 255)
            else -> "ERROU" to Color.rgb(230, 110, 110)
        }
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = true; text.textSize = 66f * u * (1f + (1f - p) * 0.15f)
        text.color = withAlpha(color, (alpha * 255).toInt())
        text.setShadowLayer(30f, 0f, 0f, withAlpha(color, (alpha * 220).toInt()))
        canvas.drawText(label, w * 0.5f, h * 0.44f, text)
        text.clearShadowLayer()
    }

    private fun drawReady(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine, time: Float) {
        fill.color = Color.argb(120, 20, 5, 40); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        txt(canvas, "JustVoo Dance", w * 0.5f, h * 0.16f, 52f * u, true, Color.WHITE, Paint.Align.CENTER)
        txt(canvas, "Siga o dançarino no ritmo!", w * 0.5f, h * 0.22f, 22f * u, false, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        dancer(canvas, w * 0.5f, h * 0.34f, h * 0.13f, 0.32f + sin(time * 1.6f) * 0.06f, Move.ALL[3], suit)
        txt(canvas, "Fique de corpo inteiro na câmera", w * 0.5f, h * 0.74f, 22f * u, true, Color.WHITE, Paint.Align.CENTER)
        if (e.startProgress > 0.01f) {
            val bw = w * 0.5f; val bx = w * 0.5f - bw / 2; val by = h * 0.80f; val bh = 14f * u
            fill.color = Color.argb(130, 0, 0, 0); canvas.drawRoundRect(bx - 3f, by - 3f, bx + bw + 3f, by + bh + 3f, 10f, 10f, fill)
            fill.color = Color.rgb(120, 230, 140); canvas.drawRoundRect(bx, by, bx + bw * e.startProgress.coerceIn(0f, 1f), by + bh, 8f, 8f, fill)
            txt(canvas, "Começando…", w * 0.5f, by - 10f * u, 16f * u, true, Color.rgb(150, 240, 170), Paint.Align.CENTER)
        } else {
            txt(canvas, "(apareça inteiro para começar)", w * 0.5f, h * 0.80f, 16f * u, false, Color.argb(200, 255, 210, 160), Paint.Align.CENTER)
        }
    }

    private fun txt(canvas: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int, align: Paint.Align) {
        text.textSize = size; text.isFakeBoldText = bold; text.textAlign = align
        shadow.textSize = size; shadow.isFakeBoldText = bold; shadow.textAlign = align; shadow.alpha = 120
        canvas.drawText(s, x + 2f, y + 2f, shadow); text.color = color; canvas.drawText(s, x, y, text)
    }

    private fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))
    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
}
