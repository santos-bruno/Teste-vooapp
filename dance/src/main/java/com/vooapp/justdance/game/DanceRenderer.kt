package com.vooapp.justdance.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Renderiza o GINGA: menu, seleção, contagem, dança, resultado e ranking.
 * A cena de dança tem um coach animado em tempo real (estilo Just Dance),
 * dois dançarinos de apoio espelhados, reflexo no chão, rastros de luz nas
 * mãos, ondas de batida, equalizador e holofotes. Medidas em u = largura/540.
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

    // Dançarinos animados: coach principal, apoios espelhados e dupla do menu.
    private val coach = DancerAnimator()
    private val backL = DancerAnimator(mirror = true, beatShift = 0.14f)
    private val backR = DancerAnimator(mirror = true, beatShift = -0.14f)
    private val menuA = DancerAnimator()
    private val menuB = DancerAnimator(mirror = true, beatShift = 0.5f)

    // Estado por quadro (calculado em render e usado pelas telas).
    private var frameDt = 0.016f
    private var frameTime = 0f
    private var frameBeats = 0f
    private var framePulse = 0f

    private val trail = ArrayList<FloatArray>()   // [x, y, vida] — rastro das mãos do coach
    private val rings = ArrayList<FloatArray>()   // [idade] — ondas de batida no chão
    private var lastRingBeat = -1

    private val cyan = Color.rgb(57, 224, 255)
    private val magenta = Color.rgb(255, 47, 182)
    private val gold = Color.rgb(255, 210, 63)
    private val lime = Color.rgb(140, 255, 90)

    private val panels = arrayOf(
        floatArrayOf(0.11f, 0.14f, 0.22f, 0.33f, -0.2f, 0f), floatArrayOf(0.70f, 0.12f, 0.20f, 0.39f, 0.15f, 1f),
        floatArrayOf(0.28f, 0.50f, 0.17f, 0.22f, 0.1f, 2f), floatArrayOf(0.80f, 0.54f, 0.18f, 0.28f, -0.15f, 3f))
    private val panelColors = intArrayOf(Color.rgb(23, 195, 214), gold, lime, magenta)

    fun render(canvas: Canvas, e: DanceEngine) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
        val time = now / 1e9f
        val w = canvas.width; val h = canvas.height; val u = w / 540f
        val floorY = h * 0.68f
        val playing = e.screen == Screen.PLAYING
        val pulse = if (playing) e.beatPulse else (0.5f + 0.5f * sin(time * 2.2f))
        val beats = if (playing) e.beatCount + (1f - e.beatPulse) else time * (e.song.bpm / 60f)
        frameDt = dt; frameTime = time; frameBeats = beats; framePulse = pulse

        if (playing) {
            val bi = floor(beats).toInt()
            if (bi != lastRingBeat) { lastRingBeat = bi; rings.add(floatArrayOf(0f)) }
        }

        // "Camera pump": a cena inteira pulsa sutilmente com a batida.
        if (playing) { canvas.save(); val z = 1f + 0.014f * pulse; canvas.scale(z, z, w * 0.5f, h * 0.60f) }

        drawStage(canvas, w, h, floorY, time, pulse, beats)
        stepParticles(dt); drawParticles(canvas)

        when (e.screen) {
            Screen.MENU -> drawMenu(canvas, w, h, u, time)
            Screen.SETUP -> drawSetup(canvas, w, h, u, e)
            Screen.COUNTDOWN -> { drawScene(canvas, w, h, u, e, true); drawCountdown(canvas, w, h, u, e) }
            Screen.PLAYING -> { drawScene(canvas, w, h, u, e, false); handleRatingFx(e, w * 0.5f, h * 0.42f, u) }
            Screen.TURN_END -> { drawScene(canvas, w, h, u, e, true); drawTurnEnd(canvas, w, h, u, e) }
            Screen.RESULTS -> drawResults(canvas, w, h, u, e)
            Screen.RANKING -> drawRanking(canvas, w, h, u, e)
        }

        if (playing) canvas.restore()
    }

    // ------------------------------------------------------------ palco/base

    private fun drawStage(canvas: Canvas, w: Int, h: Int, floorY: Float, time: Float, pulse: Float, beats: Float) {
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(58, 10, 160), Color.rgb(184, 27, 142), Color.rgb(255, 90, 47)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill); fill.shader = null
        for (i in panels.indices) {
            val p = panels[i]; canvas.save(); canvas.translate(p[0] * w, p[1] * h); canvas.rotate(p[4] * 57.3f)
            fill.color = withAlpha(panelColors[i], 66); canvas.drawRect(0f, 0f, p[2] * w, p[3] * h, fill); canvas.restore()
        }

        // Equalizador dançante no fundo do palco.
        val n = 26; val bw = w / n.toFloat()
        for (i in 0 until n) {
            val v = (0.25f + 0.75f * abs(sin(i * 1.37f + beats * 3.1f))) * (0.35f + 0.65f * pulse)
            val bh = h * 0.16f * v
            fill.color = withAlpha(panelColors[i % panelColors.size], 70)
            canvas.drawRoundRect(i * bw + bw * 0.18f, floorY - bh, (i + 1) * bw - bw * 0.18f, floorY, 3f, 3f, fill)
        }

        // Holofotes varrendo o palco.
        for (sp in arrayOf(Triple(-1f, cyan, 0f), Triple(1f, magenta, 1.2f))) {
            val ang = sp.third + sin(time * 1.3f + sp.first) * 0.18f
            val ox = w * (0.5f + sp.first * 0.42f); val tx = w * 0.5f + sin(ang) * w * 0.3f
            fill.color = withAlpha(sp.second, ((0.13f + pulse * 0.10f) * 255).toInt())
            canvas.drawPath(Path().apply { moveTo(ox - 30f, -30f); lineTo(tx - 130f, floorY); lineTo(tx + 130f, floorY); lineTo(ox + 30f, -30f); close() }, fill)
        }

        fill.shader = LinearGradient(0f, floorY, 0f, h.toFloat(), intArrayOf(Color.rgb(42, 14, 90), Color.rgb(10, 4, 22)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, floorY, w.toFloat(), h.toFloat(), fill); fill.shader = null
        stroke.color = Color.argb(46, 120, 220, 255); stroke.strokeWidth = 1.5f
        for (i in -6..6) canvas.drawLine(w * 0.5f + i * 44f, h.toFloat(), w * 0.5f, floorY, stroke)
        for (i in 1..7) { val t = i / 7f; val y = floorY + (h - floorY) * (t * t); canvas.drawLine(0f, y, w.toFloat(), y, stroke) }
    }

    /** Ondas circulares que se expandem no chão a cada batida. */
    private fun drawRings(canvas: Canvas, cx: Float, floorY: Float, w: Int, hue: Int) {
        val it = rings.iterator()
        while (it.hasNext()) {
            val r = it.next(); r[0] += frameDt
            val k = r[0] / 0.9f
            if (k >= 1f) { it.remove(); continue }
            val rad = w * (0.06f + 0.38f * k)
            stroke.color = withAlpha(hsl(hue, 1f, 0.65f), ((1f - k) * 150).toInt())
            stroke.strokeWidth = 5f * (1f - k) + 1.5f
            canvas.drawOval(RectF(cx - rad, floorY - rad * 0.24f, cx + rad, floorY + rad * 0.24f), stroke)
        }
    }

    // ------------------------------------------------------------ cena de dança

    private fun drawScene(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine, dim: Boolean) {
        val floorY = h * 0.68f
        val hue = e.song.hue
        val beatLen = 60f / e.song.bpm
        val energy = if (e.screen == Screen.PLAYING) 1f else 0.75f

        drawRings(canvas, w * 0.5f, floorY, w, hue)

        // Brilho radial pulsando atrás do coach.
        val glowR = h * 0.36f
        fill.shader = RadialGradient(w * 0.5f, h * 0.40f, glowR,
            withAlpha(hsl(hue, 1f, 0.6f), (50 + 70 * framePulse).toInt()), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(w * 0.5f, h * 0.40f, glowR, fill); fill.shader = null

        // Dançarinos de apoio (silhuetas espelhadas, um pouco atrás).
        val sB = h * 0.115f
        val cyB = (floorY - 0.045f * h) - 2.05f * sB
        val pl = backL.update(frameDt, e.currentMove, frameBeats, beatLen, energy * 0.9f)
        drawDancer(canvas, pl, w * 0.20f, cyB, sB, hue, u, 210, silhouette = true, glow = false)
        val pr = backR.update(frameDt, e.currentMove, frameBeats, beatLen, energy * 0.9f)
        drawDancer(canvas, pr, w * 0.80f, cyB, sB, hue, u, 210, silhouette = true, glow = false)

        // Coach principal: sombra, reflexo no chão, rastros e o boneco.
        val s = h * 0.155f
        val cy = floorY - 2.05f * s
        val cx = w * 0.5f
        val pc = coach.update(frameDt, e.currentMove, frameBeats, beatLen, energy)
        fill.color = Color.argb(70, 0, 0, 0)
        canvas.drawOval(RectF(cx - s * 0.9f, floorY - s * 0.10f, cx + s * 0.9f, floorY + s * 0.10f), fill)
        drawDancer(canvas, pc, cx, 2f * floorY - cy, -s, hue, u, 56, silhouette = false, glow = false)
        updateTrails(pc, cx, cy, s, e.screen == Screen.PLAYING)
        drawTrails(canvas, hue, u)
        drawDancer(canvas, pc, cx, cy, s, hue, u, 255, silhouette = false, glow = true)

        // Alvos das mãos: aparecem e pulsam quando o julgamento se aproxima.
        if (e.screen == Screen.PLAYING) {
            val k = ((e.moveProgress - 0.55f) / 0.45f).coerceIn(0f, 1f)
            if (k > 0f) {
                val mr = s * 0.17f * (1f + 0.18f * sin(frameTime * 11f))
                stroke.color = withAlpha(gold, (k * 235).toInt()); stroke.strokeWidth = 3.5f * u
                stroke.setShadowLayer(10f, 0f, 0f, gold)
                canvas.drawCircle(cx + e.currentMove.handL[0] * s, cy + e.currentMove.handL[1] * s, mr, stroke)
                canvas.drawCircle(cx + e.currentMove.handR[0] * s, cy + e.currentMove.handR[1] * s, mr, stroke)
                stroke.clearShadowLayer()
            }
        }

        drawPlayerBar(canvas, w, u, e)
        txt(canvas, e.currentMove.name, w * 0.5f, h * 0.115f, 28f * u, true, Color.WHITE, Paint.Align.CENTER)

        // progresso da música
        val pb = w * 0.7f; val px = w * 0.5f - pb / 2; val py = h * 0.155f
        fill.color = Color.argb(80, 0, 0, 0); canvas.drawRoundRect(px, py, px + pb, py + 6f * u, 4f, 4f, fill)
        fill.color = gold; canvas.drawRoundRect(px, py, px + pb * e.songProgress, py + 6f * u, 4f, 4f, fill)

        // próximo passo (pictograma estático, estilo fila do Just Dance)
        fill.color = Color.argb(102, 0, 0, 0); canvas.drawRoundRect(w - 150f * u, h * 0.60f, w - 18f * u, h * 0.60f + 150f * u, 18f, 18f, fill)
        txt(canvas, "PRÓXIMO", w - 84f * u, h * 0.625f, 15f * u, true, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        pictogram(canvas, w - 84f * u, h * 0.66f, h * 0.05f, e.nextMove, e.song.hue, u, false)

        drawRating(canvas, w, h, u, e)
        drawHud(canvas, w, h, u, e)
        if (dim) { fill.color = Color.argb(120, 8, 2, 20); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }
    }

    // ------------------------------------------------------------ dançarino

    /**
     * Desenha um dançarino a partir da pose animada. [s] negativo espelha
     * verticalmente em torno de [cy] (usado para o reflexo no chão).
     * [silhouette] desenha a versão escura com luz de contorno (apoios).
     */
    private fun drawDancer(c: Canvas, p: DancerPose, cx: Float, cy: Float, s: Float, hue: Int, u: Float, alpha: Int, silhouette: Boolean, glow: Boolean) {
        val a = abs(s)
        fun x(v: FloatArray) = cx + v[0] * a
        fun y(v: FloatArray) = cy + v[1] * s

        val bones = arrayOf(
            Triple(p.hipL, p.kneL, 0.17f), Triple(p.kneL, p.ftL, 0.14f),
            Triple(p.hipR, p.kneR, 0.17f), Triple(p.kneR, p.ftR, 0.14f),
            Triple(p.neck, p.hip, 0.21f), Triple(p.shL, p.shR, 0.15f),
            Triple(p.shL, p.elL, 0.13f), Triple(p.elL, p.haL, 0.11f),
            Triple(p.shR, p.elR, 0.13f), Triple(p.elR, p.haR, 0.11f))
        val hx = x(p.head); val hy = y(p.head); val hr = 0.24f * a

        if (silhouette) {
            stroke.color = withAlpha(Color.rgb(24, 12, 54), alpha)
            for (b in bones) { stroke.strokeWidth = b.third * a; c.drawLine(x(b.first), y(b.first), x(b.second), y(b.second), stroke) }
            fill.color = withAlpha(Color.rgb(24, 12, 54), alpha)
            c.drawCircle(hx, hy, hr, fill)
            stroke.color = withAlpha(hsl(hue, 0.9f, 0.62f), alpha / 3)
            for (b in bones) { stroke.strokeWidth = max(1.5f, b.third * a * 0.30f); c.drawLine(x(b.first) - 0.05f * a, y(b.first), x(b.second) - 0.05f * a, y(b.second), stroke) }
            return
        }

        val glowC = hsl(hue, 1f, 0.62f)
        if (glow) { stroke.setShadowLayer(24f, 0f, 0f, glowC); fill.setShadowLayer(24f, 0f, 0f, glowC) }
        stroke.color = withAlpha(Color.WHITE, alpha)
        for (b in bones) { stroke.strokeWidth = b.third * a + 6f * u; c.drawLine(x(b.first), y(b.first), x(b.second), y(b.second), stroke) }
        fill.color = withAlpha(Color.WHITE, alpha)
        c.drawCircle(hx, hy, hr + 3f * u, fill)
        stroke.clearShadowLayer(); fill.clearShadowLayer()

        val grad = LinearGradient(cx, cy - 0.6f * s, cx, cy + 2.1f * s,
            intArrayOf(hsl(hue, 1f, 0.72f), hsl(hue, 1f, 0.58f), hsl((hue + 40) % 360, 0.95f, 0.46f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        stroke.shader = grad; stroke.alpha = alpha
        for (b in bones) { stroke.strokeWidth = b.third * a; c.drawLine(x(b.first), y(b.first), x(b.second), y(b.second), stroke) }
        stroke.shader = null; stroke.alpha = 255
        fill.shader = grad; fill.alpha = alpha
        c.drawCircle(hx, hy, hr, fill)
        fill.shader = null; fill.alpha = 255

        // Luz lateral de palco (realce fino nos membros superiores).
        stroke.color = withAlpha(Color.WHITE, alpha / 3)
        for (i in 4 until bones.size) {
            val b = bones[i]; stroke.strokeWidth = max(1.5f, b.third * a * 0.30f)
            c.drawLine(x(b.first) - b.third * a * 0.28f, y(b.first), x(b.second) - b.third * a * 0.28f, y(b.second), stroke)
        }

        // Luvas e tênis brancos + viseira (a assinatura visual do coach).
        fill.color = withAlpha(Color.WHITE, alpha)
        c.drawCircle(x(p.haL), y(p.haL), 0.115f * a, fill)
        c.drawCircle(x(p.haR), y(p.haR), 0.115f * a, fill)
        c.drawCircle(x(p.ftL), y(p.ftL), 0.10f * a, fill)
        c.drawCircle(x(p.ftR), y(p.ftR), 0.10f * a, fill)
        stroke.color = withAlpha(Color.rgb(20, 12, 40), alpha)
        stroke.strokeWidth = 0.10f * a
        c.drawLine(hx - 0.13f * a, hy - 0.02f * s, hx + 0.13f * a, hy - 0.02f * s, stroke)
    }

    private fun updateTrails(p: DancerPose, cx: Float, cy: Float, s: Float, active: Boolean) {
        val it = trail.iterator()
        while (it.hasNext()) { val t = it.next(); t[2] -= frameDt; if (t[2] <= 0f) it.remove() }
        if (!active) return
        trail.add(floatArrayOf(cx + p.haL[0] * s, cy + p.haL[1] * s, TRAIL_LIFE))
        trail.add(floatArrayOf(cx + p.haR[0] * s, cy + p.haR[1] * s, TRAIL_LIFE))
        while (trail.size > 44) trail.removeAt(0)
    }

    private fun drawTrails(canvas: Canvas, hue: Int, u: Float) {
        for (t in trail) {
            val k = (t[2] / TRAIL_LIFE).coerceIn(0f, 1f)
            fill.color = withAlpha(hsl(hue, 1f, 0.75f), (k * k * 130).toInt())
            canvas.drawCircle(t[0], t[1], (3f + 9f * k) * u, fill)
        }
    }

    /** Pictograma estático (usado na prévia do próximo passo). */
    private fun pictogram(c: Canvas, cx: Float, cy: Float, s: Float, m: Move, hue: Int, u: Float, glow: Boolean) {
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
        val glowC = hsl(hue, 1f, 0.62f)

        if (glow) stroke.setShadowLayer(30f, 0f, 0f, glowC)
        stroke.color = Color.WHITE
        for (b in bones) { stroke.strokeWidth = 2f * b.third * s + 9f * u; c.drawLine(b.first[0], b.first[1], b.second[0], b.second[1], stroke) }
        if (glow) fill.setShadowLayer(30f, 0f, 0f, glowC)
        fill.color = Color.WHITE; c.drawCircle(head[0], head[1], hr + 5f * u, fill)
        stroke.clearShadowLayer(); fill.clearShadowLayer()

        val grad = LinearGradient(cx, cy - 0.5f * s, cx, cy + 2f * s, intArrayOf(hsl(hue, 1f, 0.72f), hsl(hue, 1f, 0.6f), hsl(hue, 0.95f, 0.48f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        stroke.shader = grad
        for (b in bones) { stroke.strokeWidth = 2f * b.third * s; c.drawLine(b.first[0], b.first[1], b.second[0], b.second[1], stroke) }
        stroke.shader = null
        fill.shader = grad; c.drawCircle(head[0], head[1], hr, fill); fill.shader = null
    }

    private fun drawPlayerBar(canvas: Canvas, w: Int, u: Float, e: DanceEngine) {
        if (e.players.isEmpty()) return
        val n = e.players.size; val cw = w.toFloat() / n
        for (i in 0 until n) {
            val cx = cw * i + cw / 2; val p = e.players[i]; val active = i == e.currentPlayerIndex
            fill.color = if (active) Color.argb(150, 255, 47, 182) else Color.argb(90, 0, 0, 0)
            canvas.drawRoundRect(cx - cw * 0.44f, 30f * u, cx + cw * 0.44f, 74f * u, 20f, 20f, fill)
            txt(canvas, p.name, cx - cw * 0.34f, 60f * u, 17f * u, true, Color.WHITE, Paint.Align.LEFT)
            val sc = if (active) e.score else p.score
            txt(canvas, thousands(sc), cx + cw * 0.38f, 60f * u, 16f * u, true, if (active) Color.WHITE else Color.argb(210, 200, 230, 255), Paint.Align.RIGHT)
        }
    }

    private fun drawRating(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        if (e.ratingTimer <= 0f || e.lastRating == Rating.NONE) return
        val p = 1f - (e.ratingTimer / 1.1f).coerceIn(0f, 1f)
        val alpha = (if (p > 0.7f) (1f - (p - 0.7f) / 0.3f) else 1f).coerceIn(0f, 1f)
        val (label, color) = ratingLabel(e.lastRating)
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = true; text.textSize = 70f * u * (1f + (1f - p) * 0.12f)
        text.setShadowLayer(34f, 0f, 0f, withAlpha(color, (alpha * 255).toInt()))
        text.color = withAlpha(Color.WHITE, (alpha * 255).toInt()); canvas.drawText(label, w * 0.5f, h * 0.50f, text)
        text.clearShadowLayer()
        text.color = withAlpha(color, (alpha * 255).toInt()); canvas.drawText(label, w * 0.5f, h * 0.50f, text)
    }

    private fun drawHud(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(18f * u, h - 92f * u, 228f * u, h - 18f * u, 16f, 16f, fill)
        txt(canvas, thousands(e.score), 32f * u, h - 52f * u, 34f * u, true, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "PONTOS", 34f * u, h - 30f * u, 15f * u, true, Color.argb(230, 180, 240, 255), Paint.Align.LEFT)
        if (e.combo >= 2) {
            text.setShadowLayer(14f, 0f, 0f, magenta); txt(canvas, "x${e.combo}", 218f * u, h - 46f * u, 28f * u, true, Color.rgb(255, 159, 224), Paint.Align.RIGHT); text.clearShadowLayer()
        }
        val sw = w * 0.5f; val sxx = w * 0.5f - sw / 2; val syy = h - 40f * u; val shh = 16f * u
        fill.color = Color.argb(102, 0, 0, 0); canvas.drawRoundRect(sxx - 3f, syy - 3f, sxx + sw + 3f, syy + shh + 3f, 11f, 11f, fill)
        fill.shader = LinearGradient(sxx, 0f, sxx + sw, 0f, intArrayOf(cyan, magenta), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(sxx, syy, sxx + sw * e.liveMatch.coerceIn(0f, 1f), syy + shh, 8f, 8f, fill); fill.shader = null
    }

    private fun drawCountdown(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        fill.color = Color.argb(120, 8, 2, 20); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        txt(canvas, "Vez de ${e.currentPlayer?.name ?: ""}", w * 0.5f, h * 0.40f, 30f * u, true, gold, Paint.Align.CENTER)
        val label = when { e.countdown > 2.2f -> "3"; e.countdown > 1.2f -> "2"; e.countdown > 0.4f -> "1"; else -> "JÁ!" }
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = true; text.textSize = 130f * u
        text.setShadowLayer(40f, 0f, 0f, magenta); text.color = Color.WHITE
        canvas.drawText(label, w * 0.5f, h * 0.56f, text); text.clearShadowLayer()
    }

    // ------------------------------------------------------------ menus

    private fun logo(canvas: Canvas, w: Int, h: Int, u: Float, time: Float) {
        val cx = w * 0.5f; val cy = h * 0.24f
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = true; text.textSize = 96f * u
        // brilho
        text.setShadowLayer(46f, 0f, 0f, magenta); text.color = Color.WHITE; canvas.drawText("GINGA", cx, cy, text)
        text.clearShadowLayer()
        // preenchimento gradiente
        text.shader = LinearGradient(cx - 150f * u, cy - 60f * u, cx + 150f * u, cy, intArrayOf(cyan, magenta, gold), null, Shader.TileMode.CLAMP)
        canvas.drawText("GINGA", cx, cy, text); text.shader = null
        txt(canvas, "DANCE • SINTA A BATIDA", cx, cy + 34f * u, 18f * u, true, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
    }

    private fun button(canvas: Canvas, w: Int, h: Int, u: Float, l: Float, t: Float, r: Float, b: Float, label: String, color: Int) {
        val rl = l * w; val rt = t * h; val rr = r * w; val rb = b * h
        fill.color = Color.argb(120, 0, 0, 0); canvas.drawRoundRect(rl, rt, rr, rb, 22f, 22f, fill)
        stroke.setShadowLayer(18f, 0f, 0f, color); stroke.color = color; stroke.strokeWidth = 3f * u
        canvas.drawRoundRect(rl, rt, rr, rb, 22f, 22f, stroke); stroke.clearShadowLayer()
        txt(canvas, label, (rl + rr) / 2, (rt + rb) / 2 + 10f * u, 30f * u, true, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawMenu(canvas: Canvas, w: Int, h: Int, u: Float, time: Float) {
        logo(canvas, w, h, u, time)

        // Dupla de dançarinos animados dando as boas-vindas, trocando de passo.
        val menuBpm = 112f
        val beats = time * (menuBpm / 60f)
        val mv = Move.ALL[((beats / 2f).toInt()) % Move.ALL.size]
        val hueM = ((time * 24f) % 360f).toInt()
        val sM = h * 0.075f
        val floorY = h * 0.68f
        val cyM = floorY - 2.05f * sM
        val pa = menuA.update(frameDt, mv, beats, 60f / menuBpm, 0.9f)
        drawDancer(canvas, pa, w * 0.16f, cyM, sM, hueM, u, 255, silhouette = false, glow = true)
        val pb = menuB.update(frameDt, mv, beats, 60f / menuBpm, 0.9f)
        drawDancer(canvas, pb, w * 0.84f, cyM, sM, (hueM + 140) % 360, u, 255, silhouette = false, glow = true)

        button(canvas, w, h, u, DanceLayout.PLAY_L, DanceLayout.PLAY_T, DanceLayout.PLAY_R, DanceLayout.PLAY_B, "JOGAR", magenta)
        button(canvas, w, h, u, DanceLayout.RANK_L, DanceLayout.RANK_T, DanceLayout.RANK_R, DanceLayout.RANK_B, "RANKING", cyan)
        txt(canvas, "câmera de corpo inteiro • 1 a 4 jogadores", w * 0.5f, h * 0.74f, 16f * u, false, Color.argb(210, 255, 255, 255), Paint.Align.CENTER)
    }

    private fun drawSetup(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        drawBack(canvas, w, h, u)
        txt(canvas, "JOGADORES", w * 0.5f, h * 0.24f, 30f * u, true, Color.WHITE, Paint.Align.CENTER)
        val seg = (DanceLayout.PL_R - DanceLayout.PL_L) / 4f
        for (i in 0 until 4) {
            val l = (DanceLayout.PL_L + i * seg) * w + 8f; val r = (DanceLayout.PL_L + (i + 1) * seg) * w - 8f
            val t = DanceLayout.PL_T * h; val b = DanceLayout.PL_B * h; val sel = e.setupPlayerCount == i + 1
            fill.color = if (sel) withAlpha(magenta, 150) else Color.argb(90, 0, 0, 0); canvas.drawRoundRect(l, t, r, b, 18f, 18f, fill)
            if (sel) { stroke.color = Color.WHITE; stroke.strokeWidth = 3f * u; canvas.drawRoundRect(l, t, r, b, 18f, 18f, stroke) }
            txt(canvas, "${i + 1}", (l + r) / 2, (t + b) / 2 + 12f * u, 34f * u, true, Color.WHITE, Paint.Align.CENTER)
        }
        txt(canvas, "MÚSICA", w * 0.5f, DanceLayout.SONG_T * h - 12f * u, 24f * u, true, gold, Paint.Align.CENTER)
        for (i in Song.ALL.indices) {
            val song = Song.ALL[i]; val t = (DanceLayout.SONG_T + i * DanceLayout.SONG_H) * h; val b = t + (DanceLayout.SONG_H - 0.012f) * h
            val sel = e.songIndex == i
            fill.color = if (sel) withAlpha(hsl(song.hue, 1f, 0.5f), 150) else Color.argb(90, 0, 0, 0)
            canvas.drawRoundRect(0.14f * w, t, 0.86f * w, b, 16f, 16f, fill)
            if (sel) { stroke.color = Color.WHITE; stroke.strokeWidth = 3f * u; canvas.drawRoundRect(0.14f * w, t, 0.86f * w, b, 16f, 16f, stroke) }
            txt(canvas, song.name, 0.18f * w, (t + b) / 2 + 8f * u, 22f * u, true, Color.WHITE, Paint.Align.LEFT)
            txt(canvas, "${song.bpm.toInt()} BPM", 0.82f * w, (t + b) / 2 + 8f * u, 17f * u, false, Color.argb(220, 200, 230, 255), Paint.Align.RIGHT)
        }
        button(canvas, w, h, u, DanceLayout.START_L, DanceLayout.START_T, DanceLayout.START_R, DanceLayout.START_B, "COMEÇAR", lime)
    }

    private fun drawBack(canvas: Canvas, w: Int, h: Int, u: Float) {
        val l = DanceLayout.BACK_L * w; val t = DanceLayout.BACK_T * h; val r = DanceLayout.BACK_R * w; val b = DanceLayout.BACK_B * h
        fill.color = Color.argb(110, 0, 0, 0); canvas.drawRoundRect(l, t, r, b, 16f, 16f, fill)
        txt(canvas, "‹ Voltar", (l + r) / 2, (t + b) / 2 + 6f * u, 16f * u, true, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawTurnEnd(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        val p = e.currentPlayer ?: return
        txt(canvas, "${p.name} mandou bem!", w * 0.5f, h * 0.34f, 34f * u, true, Color.WHITE, Paint.Align.CENTER)
        txt(canvas, thousands(p.score), w * 0.5f, h * 0.44f, 56f * u, true, gold, Paint.Align.CENTER)
        drawStars(canvas, w * 0.5f, h * 0.52f, 26f * u, p.stars)
        val more = e.currentPlayerIndex < e.players.size - 1
        txt(canvas, if (more) "Toque • passe para ${e.players[e.currentPlayerIndex + 1].name}" else "Toque para ver o resultado", w * 0.5f, h * 0.64f, 20f * u, true, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawResults(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        fill.color = Color.argb(150, 8, 2, 22); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        spawnConfetti(w)
        txt(canvas, "RESULTADO", w * 0.5f, h * 0.14f, 34f * u, true, gold, Paint.Align.CENTER)
        val ranked = e.ranked()
        val medals = intArrayOf(gold, Color.rgb(200, 200, 210), Color.rgb(205, 127, 50))
        var y = h * 0.24f
        for (i in ranked.indices) {
            val p = ranked[i]
            fill.color = if (i == 0) withAlpha(gold, 40) else Color.argb(80, 0, 0, 0)
            canvas.drawRoundRect(0.1f * w, y, 0.9f * w, y + 74f * u, 16f, 16f, fill)
            fill.color = if (i < 3) medals[i] else Color.argb(120, 255, 255, 255)
            canvas.drawCircle(0.16f * w, y + 37f * u, 20f * u, fill)
            txt(canvas, "${i + 1}", 0.16f * w, y + 44f * u, 22f * u, true, Color.rgb(20, 10, 30), Paint.Align.CENTER)
            txt(canvas, p.name, 0.24f * w, y + 36f * u, 22f * u, true, Color.WHITE, Paint.Align.LEFT)
            drawStars(canvas, 0.30f * w, y + 58f * u, 11f * u, p.stars)
            txt(canvas, thousands(p.score), 0.86f * w, y + 46f * u, 26f * u, true, Color.WHITE, Paint.Align.RIGHT)
            y += 86f * u
        }
        txt(canvas, "🎉 ${ranked.firstOrNull()?.name ?: ""} venceu!", w * 0.5f, y + 24f * u, 22f * u, true, gold, Paint.Align.CENTER)
        txt(canvas, "Toque para voltar ao menu", w * 0.5f, h * 0.93f, 18f * u, true, Color.WHITE, Paint.Align.CENTER)
        drawParticles(canvas)
    }

    private fun drawRanking(canvas: Canvas, w: Int, h: Int, u: Float, e: DanceEngine) {
        fill.color = Color.argb(150, 8, 2, 22); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        drawBack(canvas, w, h, u)
        txt(canvas, "RANKING", w * 0.5f, h * 0.13f, 36f * u, true, cyan, Paint.Align.CENTER)
        if (e.rankingEntries.isEmpty()) { txt(canvas, "Ainda sem recordes — dance para pontuar!", w * 0.5f, h * 0.5f, 20f * u, true, Color.argb(220, 255, 255, 255), Paint.Align.CENTER); return }
        var y = h * 0.20f
        for (i in e.rankingEntries.indices) {
            val en = e.rankingEntries[i]
            fill.color = if (i == 0) withAlpha(gold, 40) else Color.argb(70, 0, 0, 0)
            canvas.drawRoundRect(0.08f * w, y, 0.92f * w, y + 52f * u, 12f, 12f, fill)
            txt(canvas, "${i + 1}.", 0.12f * w, y + 34f * u, 20f * u, true, if (i == 0) gold else Color.WHITE, Paint.Align.LEFT)
            txt(canvas, en.name, 0.22f * w, y + 34f * u, 20f * u, true, Color.WHITE, Paint.Align.LEFT)
            txt(canvas, en.song, 0.60f * w, y + 34f * u, 15f * u, false, Color.argb(200, 200, 230, 255), Paint.Align.LEFT)
            txt(canvas, thousands(en.score), 0.90f * w, y + 34f * u, 22f * u, true, gold, Paint.Align.RIGHT)
            y += 60f * u
        }
    }

    private fun drawStars(canvas: Canvas, cx: Float, cy: Float, r: Float, filled: Int) {
        val gap = r * 2.4f; val startX = cx - gap * 2
        for (i in 0 until 5) star(canvas, startX + i * gap, cy, r, i < filled)
    }

    private fun star(canvas: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
        val path = Path()
        for (k in 0 until 10) {
            val rr = if (k % 2 == 0) r else r * 0.45f
            val a = (-90f + k * 36f) * Math.PI.toFloat() / 180f
            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (on) { fill.color = gold; canvas.drawPath(path, fill) } else { stroke.color = Color.argb(120, 255, 255, 255); stroke.strokeWidth = 2f; canvas.drawPath(path, stroke) }
    }

    // ------------------------------------------------------------ efeitos

    private fun handleRatingFx(e: DanceEngine, x: Float, y: Float, u: Float) {
        if (e.ratingCount != prevRatingCount) {
            prevRatingCount = e.ratingCount
            if (e.lastRating == Rating.PERFEITO || e.lastRating == Rating.BOM) {
                val colors = intArrayOf(magenta, cyan, Color.WHITE)
                val n = if (e.lastRating == Rating.PERFEITO) 26 else 14
                for (i in 0 until n) { val a = rng.nextFloat() * 6.283f; val sp = (80f + rng.nextFloat() * 170f) * u
                    particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 0.7f + rng.nextFloat() * 0.4f, 1.1f, (3f + rng.nextFloat() * 4f) * u, colors[i % 3])) }
            }
        } else if (e.ratingCount == 0) prevRatingCount = 0
    }

    /** Confete caindo na tela de resultado. */
    private fun spawnConfetti(w: Int) {
        if (particles.size >= 150) return
        val colors = intArrayOf(gold, magenta, cyan, lime, Color.WHITE)
        for (i in 0 until 2) {
            particles.add(Particle(rng.nextFloat() * w, -10f, (rng.nextFloat() - 0.5f) * 80f,
                90f + rng.nextFloat() * 160f, 2.4f + rng.nextFloat() * 1.2f, 3.6f,
                3f + rng.nextFloat() * 5f, colors[rng.nextInt(colors.size)]))
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
        Rating.BOM -> "BOM" to lime
        Rating.OK -> "OK" to gold
        Rating.ERROU -> "OPS" to Color.rgb(255, 110, 110)
        Rating.NONE -> "—" to Color.WHITE
    }

    private fun thousands(n: Int): String {
        val s = n.toString(); val sb = StringBuilder()
        for (i in s.indices) { if (i > 0 && (s.length - i) % 3 == 0) sb.append('.'); sb.append(s[i]) }
        return sb.toString()
    }

    private fun hsl(h: Int, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val hp = (((h % 360) + 360) % 360) / 60f
        val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f); hp < 2f -> Triple(x, c, 0f); hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c); hp < 5f -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color.rgb(((r1 + m) * 255).toInt().coerceIn(0, 255), ((g1 + m) * 255).toInt().coerceIn(0, 255), ((b1 + m) * 255).toInt().coerceIn(0, 255))
    }

    private fun txt(canvas: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int, align: Paint.Align) {
        text.textSize = size; text.isFakeBoldText = bold; text.textAlign = align
        shadowText.textSize = size; shadowText.isFakeBoldText = bold; shadowText.textAlign = align; shadowText.alpha = 120
        canvas.drawText(s, x + 2f, y + 2f, shadowText); text.color = color; canvas.drawText(s, x, y, text)
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    companion object { private const val TRAIL_LIFE = 0.38f }
}
