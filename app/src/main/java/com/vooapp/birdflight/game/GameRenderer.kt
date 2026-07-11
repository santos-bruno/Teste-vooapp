package com.vooapp.birdflight.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.vooapp.birdflight.input.FlightInput
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Renderiza o [GameEngine] com um mundo aberto vivo: ciclo dia/noite (céu,
 * sol/lua, estrelas, luzes da cidade), rodovia com carros em movimento,
 * árvores, pássaros ao fundo, e efeitos — partículas, halos, "+pontos",
 * linhas de vento, rastro e tremor de tela na batida.
 *
 * Layout de referência 540x1080; tudo é escalado por [u] = largura/540.
 */
class GameRenderer {

    private class Building(val x: Float, val w: Float, val h: Float, val lit: Int)
    private class Tree(val side: Float, val t: Float, val off: Float)
    private class Star(val x: Float, val y: Float, val r: Float, val tw: Float)
    private class BgBird(val x: Float, val y: Float, val s: Float, val ph: Float)
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                           var life: Float, val maxLife: Float, val size: Float, val color: Int, val grav: Float)
    private class Popup(var x: Float, var y: Float, var age: Float, val maxAge: Float, val text: String)
    private class Halo(var x: Float, var y: Float, var age: Float, val maxAge: Float)

    private val cityMain: List<Building>
    private val cityBack: List<Building>
    private val trees: List<Tree>
    private val stars: List<Star>
    private val bgBirds: List<BgBird>
    private val clouds = arrayOf(
        floatArrayOf(-0.6f, 26f, 1.0f), floatArrayOf(0.15f, 44f, 0.8f), floatArrayOf(-0.15f, 64f, 1.2f))

    // Chaves do ciclo dia/noite
    private val todKeys = floatArrayOf(0f, 0.13f, 0.40f, 0.52f, 0.64f, 0.90f, 1f)
    private val skyTop = ints("#e8894a", "#1e5fa6", "#1e5fa6", "#4a3a72", "#0a1730", "#0a1730", "#e8894a")
    private val skyMid = ints("#f4b978", "#5aa0d2", "#5aa0d2", "#c0607a", "#12274c", "#12274c", "#f4b978")
    private val skyBot = ints("#ffe0b0", "#cfe8f5", "#cfe8f5", "#f0a868", "#2c4877", "#2c4877", "#ffe0b0")
    private val nightVals = floatArrayOf(0.25f, 0f, 0f, 0.35f, 1f, 1f, 0.25f)

    private val particles = ArrayList<Particle>()
    private val popups = ArrayList<Popup>()
    private val halos = ArrayList<Halo>()
    private var prevScore = 0
    private var prevState = GameState.READY
    private var shake = 0f
    private var lastNanos = 0L
    private val rnd = Random()

    init {
        val r = Random(7)
        cityMain = (0 until 16).map { Building(it / 15f * 2f - 1f, 0.045f + r.nextFloat() * 0.05f, 0.06f + r.nextFloat() * 0.17f, (r.nextFloat() * 10).toInt()) }
        cityBack = (0 until 14).map { Building(it / 13f * 2f - 1f + 0.03f, 0.05f + r.nextFloat() * 0.05f, 0.04f + r.nextFloat() * 0.10f, 0) }
        trees = (0 until 10).map { Tree(if (it % 2 == 0) -1f else 1f, 0.15f + r.nextFloat() * 0.8f, 0.02f + r.nextFloat() * 0.06f) }
        stars = (0 until 60).map { Star(r.nextFloat(), r.nextFloat() * 0.46f, r.nextFloat() * 1.4f + 0.4f, r.nextFloat()) }
        bgBirds = (0 until 5).map { BgBird(r.nextFloat(), 0.1f + r.nextFloat() * 0.28f, 0.5f + r.nextFloat() * 0.7f, r.nextFloat()) }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(128, 0, 0, 0) }

    fun render(canvas: Canvas, engine: GameEngine, input: FlightInput) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now

        val w = canvas.width; val h = canvas.height; val u = w / 540f
        val horizonY = h * 0.5f
        val vpX = w * 0.5f - engine.lateral * w * 0.16f
        val tod = engine.timeOfDay
        val night = interp(nightVals, tod)

        handleEvents(engine, w, h, u)
        stepEffects(dt)

        canvas.save()
        if (shake > 0f) canvas.translate((rnd.nextFloat() - 0.5f) * shake, (rnd.nextFloat() - 0.5f) * shake)

        drawSky(canvas, w, horizonY, night, tod)
        drawBgBirds(canvas, w, horizonY, engine)
        drawClouds(canvas, w, horizonY, u, night, engine)
        drawCity(canvas, w, horizonY, night, engine)
        drawGround(canvas, w, h, horizonY, night, engine)
        drawHighway(canvas, w, h, horizonY, vpX, u, night, engine)
        drawTrees(canvas, w, h, horizonY, vpX, u, night, engine)
        drawRings(canvas, w, h, horizonY, vpX, u, night, engine)
        drawHalos(canvas, u)
        drawBird(canvas, w, h, u, engine, input)
        drawParticles(canvas)
        drawPopups(canvas, u)

        canvas.restore() // tremor

        if (night > 0.05f) { fill.color = Color.argb((night * 0.28f * 255).toInt(), 10, 20, 45); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }

        if (engine.state == GameState.PLAYING) drawHud(canvas, w, h, u, engine, input)
        drawMessages(canvas, w, h, u, engine, input)
    }

    // ---------------------------------------------------------------- efeitos

    private fun handleEvents(engine: GameEngine, w: Int, h: Int, u: Float) {
        val by = altToScreenY(engine.altitude, h); val bx = w * 0.5f
        if (engine.score > prevScore) {
            val gained = engine.score - prevScore
            burst(bx, by, u)
            popups.add(Popup(bx, by - 30f * u, 0f, 0.9f, "+$gained"))
            halos.add(Halo(bx, by, 0f, 0.5f))
        }
        if (engine.state == GameState.CRASHED && prevState != GameState.CRASHED) {
            feathers(bx, by, engine.bird, u); shake = 16f * u
        }
        prevScore = engine.score
        prevState = engine.state
    }

    private fun burst(x: Float, y: Float, u: Float) {
        val colors = intArrayOf(Color.rgb(140, 240, 170), Color.rgb(255, 224, 102), Color.WHITE)
        for (i in 0 until 18) {
            val a = rnd.nextFloat() * 6.283f; val sp = (60f + rnd.nextFloat() * 120f) * u
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 0.6f + rnd.nextFloat() * 0.3f, 0.9f,
                (2.5f + rnd.nextFloat() * 3f) * u, colors[i % 3], 40f * u))
        }
    }

    private fun feathers(x: Float, y: Float, bird: BirdType, u: Float) {
        val colors = intArrayOf(bird.wing, bird.body, bird.edge)
        for (i in 0 until 16) {
            val a = -1.6f + (rnd.nextFloat() - 0.5f) * 2.4f; val sp = (80f + rnd.nextFloat() * 160f) * u
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 1.0f + rnd.nextFloat() * 0.6f, 1.6f,
                (4f + rnd.nextFloat() * 4f) * u, colors[i % 3], 260f * u))
        }
    }

    private fun stepEffects(dt: Float) {
        if (shake > 0f) shake = (shake - 60f * dt).coerceAtLeast(0f)
        val pit = particles.iterator()
        while (pit.hasNext()) { val p = pit.next(); p.x += p.vx * dt; p.y += p.vy * dt; p.vy += p.grav * dt; p.life -= dt; if (p.life <= 0f) pit.remove() }
        val poit = popups.iterator()
        while (poit.hasNext()) { val p = poit.next(); p.age += dt; if (p.age >= p.maxAge) poit.remove() }
        val hit = halos.iterator()
        while (hit.hasNext()) { val hh = hit.next(); hh.age += dt; if (hh.age >= hh.maxAge) hit.remove() }
    }

    // ---------------------------------------------------------------- mundo

    private fun drawSky(canvas: Canvas, w: Int, horizonY: Float, night: Float, tod: Float) {
        fill.shader = LinearGradient(0f, 0f, 0f, horizonY,
            intArrayOf(interpColor(skyTop, tod), interpColor(skyMid, tod), interpColor(skyBot, tod)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), horizonY, fill)
        fill.shader = null
        // Estrelas
        if (night > 0.05f) {
            for (s in stars) {
                fill.color = Color.argb((night * (0.4f + 0.6f * s.tw) * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                canvas.drawCircle(s.x * w, s.y * horizonY, s.r, fill)
            }
        }
        // Sol / Lua num arco
        val cross = (tod * 2f) % 1f
        val bx = w * (0.1f + 0.8f * cross)
        val by = horizonY - sin(cross * Math.PI).toFloat() * horizonY * 0.72f
        val moon = tod >= 0.5f
        val glow = if (moon) intArrayOf(Color.argb(230, 220, 230, 245), Color.argb(102, 200, 215, 240), Color.argb(0, 200, 215, 240))
                   else intArrayOf(Color.argb(242, 255, 244, 214), Color.argb(140, 255, 224, 150), Color.argb(0, 255, 224, 150))
        fill.shader = RadialGradient(bx, by, 110f * (w / 540f), glow, floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, 110f * (w / 540f), fill); fill.shader = null
        fill.color = if (moon) Color.rgb(232, 238, 248) else Color.rgb(255, 246, 220)
        canvas.drawCircle(bx, by, (if (moon) 26f else 30f) * (w / 540f), fill)
        if (moon) { fill.color = interpColor(skyTop, tod); canvas.drawCircle(bx + 11f * (w / 540f), by - 6f * (w / 540f), 24f * (w / 540f), fill) }
    }

    private fun drawBgBirds(canvas: Canvas, w: Int, horizonY: Float, engine: GameEngine) {
        stroke.color = Color.argb(200, 20, 20, 30); stroke.strokeWidth = 2f
        for (b in bgBirds) {
            var fx = (b.x - engine.distance * 0.0008f - engine.lateral * 0.1f) % 1f
            if (fx < 0f) fx += 1f
            val cx = fx * w; val cy = b.y * horizonY; val s = b.s
            val wy = sin(((b.ph + engine.distance * 0.02f) % 1f) * 6.28f) * 4f * s
            val path = Path().apply {
                moveTo(cx - 9f * s, cy + wy); quadTo(cx, cy - 6f * s, cx, cy); quadTo(cx, cy - 6f * s, cx + 9f * s, cy + wy)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Int, horizonY: Float, u: Float, night: Float, engine: GameEngine) {
        for (c in clouds) {
            c[1] -= engine.forwardSpeed * 0.0022f
            if (c[1] < 8f) { c[0] = rand(-1f, 1f); c[1] = rand(60f, 90f); c[2] = rand(0.6f, 1.3f) }
            val s = 40f / (40f + c[1])
            val cx = w * 0.5f + (c[0] - engine.lateral) * w * 0.9f * s
            val cy = horizonY * (0.12f + 0.5f * (1f - s)) + 18f
            val rr = 58f * s * c[2] * u
            val a = (255 * min(0.9f, s + 0.2f)).toInt()
            fill.color = if (night > 0.5f) Color.argb(a, 70, 80, 110) else Color.argb(a, 255, 255, 255)
            canvas.drawCircle(cx, cy, rr, fill)
            canvas.drawCircle(cx - rr * 0.8f, cy + rr * 0.2f, rr * 0.7f, fill)
            canvas.drawCircle(cx + rr * 0.9f, cy + rr * 0.15f, rr * 0.75f, fill)
        }
    }

    private fun drawCity(canvas: Canvas, w: Int, horizonY: Float, night: Float, engine: GameEngine) {
        val dark = night > 0.45f
        fill.color = if (dark) Color.argb(178, 20, 30, 55) else Color.argb(140, 120, 150, 180)
        for (b in cityBack) {
            val px = w * 0.5f + (b.x - engine.lateral * 0.2f) * w * 0.85f
            val bw = b.w * w; val bh = b.h * (horizonY * 2f)
            canvas.drawRect(px - bw / 2, horizonY - bh, px + bw / 2, horizonY, fill)
        }
        for (b in cityMain) {
            val px = w * 0.5f + (b.x - engine.lateral * 0.3f) * w * 0.8f
            val bw = b.w * w; val bh = b.h * (horizonY * 2f)
            val x = px - bw / 2; val y = horizonY - bh
            fill.shader = LinearGradient(0f, y, 0f, horizonY,
                intArrayOf(if (dark) Color.rgb(36, 52, 74) else Color.rgb(91, 118, 144), if (dark) Color.rgb(20, 31, 48) else Color.rgb(50, 74, 99)),
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(x, y, x + bw, horizonY, fill); fill.shader = null
            val cols = max(2, (bw / 9f).toInt()); val rows = max(3, (bh / 12f).toInt())
            val cwd = (bw - 6f) / cols; val rhd = (bh - 6f) / rows
            val winA = (0.5f + 0.5f * max(0.4f, night))
            for (rr in 0 until rows) for (cc in 0 until cols) {
                val on = (rr * 7 + cc * 3 + b.lit) % 4 == 0 || (night > 0.4f && (rr * 5 + cc * 2 + b.lit) % 3 == 0)
                fill.color = if (on) Color.argb((winA * 255).toInt().coerceIn(0, 255), 255, 224, 150)
                             else if (dark) Color.argb(204, 15, 25, 40) else Color.argb(178, 30, 45, 60)
                canvas.drawRect(x + 3f + cc * cwd, y + 4f + rr * rhd, x + 3f + cc * cwd + cwd - 2f, y + 4f + rr * rhd + rhd - 3f, fill)
            }
        }
    }

    private fun drawGround(canvas: Canvas, w: Int, h: Int, horizonY: Float, night: Float, engine: GameEngine) {
        val gt = (night * 0.55f).coerceIn(0f, 0.55f)
        fill.shader = LinearGradient(0f, horizonY, 0f, h.toFloat(),
            intArrayOf(lerpColor(Color.rgb(127, 191, 106), Color.rgb(42, 64, 48), gt), lerpColor(Color.rgb(60, 122, 68), Color.rgb(22, 36, 26), gt)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, horizonY, w.toFloat(), h.toFloat(), fill); fill.shader = null
        val scroll = (engine.distance % 10f) / 10f
        stroke.color = Color.argb(26, 255, 255, 255); stroke.strokeWidth = 2f
        for (i in 1..9) { val t = (i - scroll) / 9f; if (t <= 0f) continue; val y = horizonY + (h - horizonY) * (t * t); canvas.drawLine(0f, y, w.toFloat(), y, stroke) }
    }

    private fun drawHighway(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, night: Float, engine: GameEngine) {
        val baseY = h.toFloat(); val baseHalf = w * 0.30f; val topHalf = 6f * u
        val road = Path().apply { moveTo(w * 0.5f - baseHalf, baseY); lineTo(vpX - topHalf, horizonY + 1f); lineTo(vpX + topHalf, horizonY + 1f); lineTo(w * 0.5f + baseHalf, baseY); close() }
        fill.color = if (night > 0.5f) Color.rgb(43, 46, 52) else Color.rgb(64, 68, 75)
        canvas.drawPath(road, fill)
        stroke.color = Color.argb(230, 240, 240, 240); stroke.strokeWidth = 3f * u
        canvas.drawLine(w * 0.5f - baseHalf + 7f * u, baseY, vpX - topHalf, horizonY + 1f, stroke)
        canvas.drawLine(w * 0.5f + baseHalf - 7f * u, baseY, vpX + topHalf, horizonY + 1f, stroke)
        val scroll = (engine.distance % 9f) / 9f
        fill.color = Color.rgb(244, 209, 58)
        for (i in 0 until 9) { val t = ((i + scroll) % 9f) / 9f; val tt = t * t; val y = horizonY + (baseY - horizonY) * tt; val cx = vpX + (w * 0.5f - vpX) * tt; canvas.drawRect(cx - max(1.2f, 5f * tt * u) / 2, y, cx + max(1.2f, 5f * tt * u) / 2, y + max(2f, 20f * tt * u), fill) }
        // Carros em movimento
        val carPhase = (engine.distance * 0.03f) % 1f
        val cars = arrayOf(floatArrayOf(0f, -0.28f, 0f), floatArrayOf(0.45f, 0.30f, 1f), floatArrayOf(0.8f, 0.05f, 2f))
        val carColors = intArrayOf(Color.rgb(224, 49, 49), Color.rgb(28, 126, 214), Color.rgb(245, 159, 0))
        for (car in cars) {
            val t = (car[0] + carPhase) % 1f; val tt = t * t
            val y = horizonY + (baseY - horizonY) * tt; val half = topHalf + (baseHalf - topHalf) * tt
            val cx = vpX + (w * 0.5f - vpX) * tt + car[1] * half; val cw = max(3f, half * 0.5f); val ch = cw * 0.7f
            fill.color = carColors[car[2].toInt()]
            canvas.drawRoundRect(cx - cw / 2, y - ch / 2, cx + cw / 2, y + ch / 2, min(3f, cw * 0.2f), min(3f, cw * 0.2f), fill)
            fill.color = Color.argb(178, 180, 220, 255)
            canvas.drawRect(cx - cw / 2 + cw * 0.15f, y - ch / 2 + ch * 0.15f, cx - cw / 2 + cw * 0.85f, y - ch / 2 + ch * 0.45f, fill)
            if (night > 0.5f) { fill.color = Color.argb(230, 255, 240, 180); canvas.drawRect(cx - cw * 0.4f, y + ch * 0.2f, cx - cw * 0.15f, y + ch * 0.4f, fill); canvas.drawRect(cx + cw * 0.15f, y + ch * 0.2f, cx + cw * 0.4f, y + ch * 0.4f, fill) }
        }
    }

    private fun drawTrees(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, night: Float, engine: GameEngine) {
        val gt = (night * 0.55f).coerceIn(0f, 0.55f)
        for (tr in trees) {
            val tt = tr.t * tr.t; val y = horizonY + (h - horizonY) * tt
            val roadHalf = 6f + (w * 0.30f - 6f) * tt
            val cx = vpX + (w * 0.5f - vpX) * tt + tr.side * (roadHalf + tr.off * w * (0.4f + tt)); val sc = (0.25f + tt * 1.1f) * u
            fill.color = if (night > 0.5f) Color.rgb(58, 42, 24) else Color.rgb(107, 74, 43)
            canvas.drawRect(cx - 3f * sc, y - 16f * sc, cx + 3f * sc, y, fill)
            fill.color = lerpColor(Color.rgb(47, 125, 58), Color.rgb(24, 61, 34), gt)
            canvas.drawCircle(cx, y - 22f * sc, 14f * sc, fill)
            canvas.drawCircle(cx - 10f * sc, y - 16f * sc, 10f * sc, fill)
            canvas.drawCircle(cx + 10f * sc, y - 16f * sc, 10f * sc, fill)
        }
    }

    private fun altToScreenY(alt: Float, h: Int): Float {
        val g = h * 0.9f; val s = h * 0.14f; val t = (alt / GameEngine.CEILING).coerceIn(0f, 1f); return g + (s - g) * t
    }

    private fun drawRings(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, night: Float, engine: GameEngine) {
        val halfW = w * 0.42f
        for (r in engine.rings.sortedByDescending { it.z }) {
            if (r.z <= -2f) continue
            val scale = 46f / (46f + r.z.coerceAtLeast(-1f))
            val relLane = r.laneX - engine.lateral
            val baseX = w * 0.5f + relLane * halfW; val baseY = altToScreenY(r.altitude, h)
            val f = (1f - scale).coerceIn(0f, 1f)
            val sx = baseX + (vpX - baseX) * f; val sy = baseY + (horizonY - baseY) * f
            val radius = 120f * scale * u
            stroke.strokeWidth = max(2f, 16f * scale * u)
            val active = !r.scored && !r.missed
            stroke.color = when { r.scored -> Color.rgb(120, 230, 140); r.missed -> Color.argb(150, 200, 90, 90); else -> Color.rgb(255, 204, 40) }
            if (active && night > 0.4f) stroke.setShadowLayer(16f * scale, 0f, 0f, Color.rgb(255, 204, 40))
            canvas.drawOval(sx - radius, sy - radius * 0.72f, sx + radius, sy + radius * 0.72f, stroke)
            stroke.clearShadowLayer()
            if (active) {
                stroke.color = Color.argb(128, 255, 255, 255); stroke.strokeWidth = max(1f, 4f * scale * u)
                canvas.drawOval(sx - radius * 0.86f, sy - radius * 0.62f, sx + radius * 0.86f, sy + radius * 0.62f, stroke)
            }
        }
    }

    private fun drawHalos(canvas: Canvas, u: Float) {
        for (hl in halos) {
            val p = hl.age / hl.maxAge
            stroke.color = Color.argb(((1f - p) * 255).toInt().coerceIn(0, 255), 140, 240, 170)
            stroke.strokeWidth = 6f * (1f - p) + 1f
            canvas.drawCircle(hl.x, hl.y, (20f + p * 130f) * u, stroke)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = withAlpha(p.color, (a * 255).toInt())
            canvas.drawCircle(p.x, p.y, p.size, fill)
        }
    }

    private fun drawPopups(canvas: Canvas, u: Float) {
        textPaint.textAlign = Paint.Align.CENTER; textPaint.isFakeBoldText = true; textPaint.textSize = 30f * u
        for (p in popups) {
            val a = (1f - p.age / p.maxAge).coerceIn(0f, 1f)
            textPaint.color = Color.argb((a * 255).toInt(), 255, 224, 102)
            canvas.drawText(p.text, p.x, p.y - (p.age / p.maxAge) * 50f * u, textPaint)
        }
    }

    private fun drawBirdShape(canvas: Canvas, bird: BirdType, flapPhase: Float, spread: Float, scale: Float) {
        canvas.save(); canvas.scale(scale, scale)
        val flap = sin(flapPhase * 2f * Math.PI).toFloat(); val up = flap * 30f; val sp = 1f + spread * 0.4f
        fill.color = bird.edge
        canvas.drawPath(Path().apply { moveTo(0f, 10f); lineTo(-18f, 52f); lineTo(0f, 42f); lineTo(18f, 52f); close() }, fill)
        for (s in intArrayOf(-1, 1)) {
            fill.shader = LinearGradient(0f, 0f, s * 135f * sp, 0f, intArrayOf(bird.wing, bird.edge), null, Shader.TileMode.CLAMP)
            canvas.drawPath(Path().apply {
                moveTo(s * 8f, -6f); quadTo(s * 72f * sp, -24f - up, s * 140f * sp, -4f - up * 0.5f)
                quadTo(s * 120f * sp, 16f - up * 0.3f, s * 70f * sp, 22f); quadTo(s * 40f, 16f, s * 8f, 10f); close()
            }, fill); fill.shader = null
            stroke.color = bird.edge; stroke.strokeWidth = 2f
            for (i in 1..4) { val t = i / 5f; canvas.drawLine(s * (24f + 60f * t * sp), 6f - up * 0.4f * t, s * (140f * sp * t + 18f), -4f - up * 0.5f, stroke) }
        }
        fill.shader = LinearGradient(0f, -24f, 0f, 34f, intArrayOf(bird.wing, bird.body), null, Shader.TileMode.CLAMP)
        canvas.drawOval(-20f, -26f, 20f, 38f, fill); fill.shader = null
        fill.color = bird.head; canvas.drawCircle(0f, -28f, 15f, fill)
        fill.color = bird.beak; canvas.drawPath(Path().apply { moveTo(-6f, -40f); lineTo(0f, -56f); lineTo(6f, -40f); close() }, fill)
        fill.color = Color.rgb(22, 22, 22); canvas.drawCircle(-7f, -30f, 2.6f, fill); canvas.drawCircle(7f, -30f, 2.6f, fill)
        fill.color = Color.WHITE; canvas.drawCircle(-6.2f, -30.8f, 0.9f, fill); canvas.drawCircle(7.8f, -30.8f, 0.9f, fill)
        canvas.restore()
    }

    private fun drawBird(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        val bx = w * 0.5f; val by = altToScreenY(engine.altitude, h); val groundY = h * 0.9f
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f); val ss = 1f - altT * 0.7f
        fill.color = Color.argb(56, 0, 0, 0)
        canvas.drawOval(bx - 72f * ss * u, groundY - 15f * ss * u, bx + 72f * ss * u, groundY + 15f * ss * u, fill)
        // Rastro
        for (i in 1..5) { fill.color = Color.argb(((0.10f - i * 0.015f) * 255).toInt().coerceAtLeast(0), 255, 255, 255); canvas.drawOval(bx - (20f - i * 2) * u, by + i * 10f * u - 8f * u, bx + (20f - i * 2) * u, by + i * 10f * u + 8f * u, fill) }
        // Linhas de vento ao planar
        if (input.spread > 0.45f) {
            stroke.color = Color.argb(90, 255, 255, 255); stroke.strokeWidth = 2f * u
            for (i in 0 until 7) { val a = (i / 7f) * 6.28f; val r0 = (60f + ((i * 53 + engine.distance * 4) % 60)) * u; canvas.drawLine(bx + cos(a) * r0, by + sin(a) * r0, bx + cos(a) * (r0 + 28f * u), by + sin(a) * (r0 + 28f * u), stroke) }
        }
        canvas.save(); canvas.translate(bx, by); canvas.rotate(engine.bank * 22f)
        drawBirdShape(canvas, engine.bird, engine.flapPhase, input.spread, u); canvas.restore()
    }

    // ---------------------------------------------------------------- HUD / telas

    private fun drawHud(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        txt(canvas, "Pontos: ${engine.score}", 28f * u, 66f * u, 26f * u, true, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "Recorde: ${engine.best}", 28f * u, 110f * u, 18f * u, false, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "Distância: ${engine.distance.toInt()} m", 28f * u, 148f * u, 18f * u, false, Color.WHITE, Paint.Align.LEFT)
        if (engine.combo >= 2) txt(canvas, "Combo x${engine.combo}", 28f * u, 186f * u, 18f * u, false, Color.rgb(255, 220, 90), Paint.Align.LEFT)
        txt(canvas, engine.bird.name, 28f * u, h - 34f * u, 20f * u, true, Color.WHITE, Paint.Align.LEFT)
        val barX = w - 44f * u; val barTop = 66f * u; val barBot = h * 0.58f
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(barX - 4f, barTop - 4f, barX + 22f, barBot + 4f, 12f, 12f, fill)
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f); val fillTop = barBot - (barBot - barTop) * altT
        fill.color = if (engine.altitude < 12f) Color.rgb(230, 90, 90) else Color.rgb(120, 200, 255)
        canvas.drawRoundRect(barX, fillTop, barX + 18f, barBot, 9f, 9f, fill)
        fill.color = if (input.detected) Color.rgb(120, 230, 140) else Color.rgb(230, 120, 120)
        canvas.drawCircle(w - 30f * u, h - 36f * u, 11f * u, fill)
        txt(canvas, if (input.detected) "rastreando" else "sem pose", w - 48f * u, h - 30f * u, 16f * u, false, Color.WHITE, Paint.Align.RIGHT)
    }

    private fun drawMessages(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        when (engine.state) {
            GameState.READY -> drawSelectScreen(canvas, w, h, u, engine, input)
            GameState.CRASHED -> {
                dim(canvas, w, h, 120)
                txt(canvas, "Você caiu!", w / 2f, h * 0.36f, 56f * u, true, Color.WHITE, Paint.Align.CENTER)
                txt(canvas, "Pontos: ${engine.score}   Recorde: ${engine.best}", w / 2f, h * 0.44f, 24f * u, false, Color.WHITE, Paint.Align.CENTER)
                txt(canvas, "Levante os braços para voar de novo", w / 2f, h * 0.53f, 22f * u, true, Color.WHITE, Paint.Align.CENTER)
            }
            GameState.PLAYING -> if (!input.detected) txt(canvas, "Reposicione-se na câmera", w / 2f, h * 0.14f, 22f * u, true, Color.rgb(255, 180, 120), Paint.Align.CENTER)
        }
    }

    private fun drawSelectScreen(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        dim(canvas, w, h, 128)
        txt(canvas, "VooApp", w / 2f, h * 0.16f, 58f * u, true, Color.WHITE, Paint.Align.CENTER)
        txt(canvas, "Simulador de voo com o corpo", w / 2f, h * 0.205f, 22f * u, false, Color.argb(217, 255, 255, 255), Paint.Align.CENTER)
        txt(canvas, "Escolha seu pássaro", w / 2f, h * 0.30f, 26f * u, true, Color.rgb(255, 224, 138), Paint.Align.CENTER)
        canvas.save(); canvas.translate(w / 2f, h * 0.44f); drawBirdShape(canvas, engine.bird, 0.12f, 0.5f, 1.5f * u); canvas.restore()
        val n = BirdType.ALL.size; val cw = w.toFloat() / n; val cardY = h * 0.60f; val cardH = h * 0.14f
        for (i in 0 until n) {
            val cx = cw * i + cw / 2; val on = i == engine.selectedIndex
            fill.color = if (on) Color.argb(56, 255, 224, 138) else Color.argb(20, 255, 255, 255)
            canvas.drawRoundRect(cx - cw * 0.42f, cardY, cx + cw * 0.42f, cardY + cardH, 14f, 14f, fill)
            if (on) { stroke.color = Color.rgb(255, 224, 138); stroke.strokeWidth = 3f * u; canvas.drawRoundRect(cx - cw * 0.42f, cardY, cx + cw * 0.42f, cardY + cardH, 14f, 14f, stroke) }
            canvas.save(); canvas.translate(cx, cardY + cardH * 0.40f); drawBirdShape(canvas, BirdType.ALL[i], 0.1f, 0.4f, 0.42f * u); canvas.restore()
            txt(canvas, BirdType.ALL[i].name, cx, cardY + cardH * 0.88f, 17f * u, on, if (on) Color.rgb(255, 224, 138) else Color.WHITE, Paint.Align.CENTER)
        }
        txt(canvas, "Toque para escolher", w / 2f, h * 0.80f, 20f * u, false, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        val hint = if (input.detected) "Abra e levante os braços para decolar" else "Fique visível para a câmera"
        txt(canvas, hint, w / 2f, h * 0.835f, 22f * u, true, if (input.detected) Color.WHITE else Color.rgb(255, 180, 120), Paint.Align.CENTER)
    }

    private fun dim(canvas: Canvas, w: Int, h: Int, alpha: Int) { fill.color = Color.argb(alpha, 6, 20, 38); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }

    private fun txt(canvas: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int, align: Paint.Align) {
        textPaint.textSize = size; textPaint.isFakeBoldText = bold; textPaint.textAlign = align
        shadowText.textSize = size; shadowText.isFakeBoldText = bold; shadowText.textAlign = align
        canvas.drawText(s, x + 2f, y + 2f, shadowText)
        textPaint.color = color; canvas.drawText(s, x, y, textPaint)
    }

    // ---------------------------------------------------------------- util

    private fun ints(vararg hex: String) = IntArray(hex.size) { Color.parseColor(hex[it]) }

    private fun interp(vals: FloatArray, t: Float): Float {
        for (i in 0 until todKeys.size - 1) if (t >= todKeys[i] && t <= todKeys[i + 1]) {
            val tt = (t - todKeys[i]) / (todKeys[i + 1] - todKeys[i]); return vals[i] + (vals[i + 1] - vals[i]) * tt
        }
        return vals.last()
    }

    private fun interpColor(cols: IntArray, t: Float): Int {
        for (i in 0 until todKeys.size - 1) if (t >= todKeys[i] && t <= todKeys[i + 1]) {
            val tt = (t - todKeys[i]) / (todKeys[i + 1] - todKeys[i]); return lerpColor(cols[i], cols[i + 1], tt)
        }
        return cols.last()
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), bl.coerceIn(0, 255))
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
    private fun rand(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    private fun min(a: Float, b: Float) = if (a < b) a else b
}
