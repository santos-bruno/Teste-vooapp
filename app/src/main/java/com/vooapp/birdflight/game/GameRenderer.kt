package com.vooapp.birdflight.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.vooapp.birdflight.input.FlightInput
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Renderiza o [GameEngine] com aparência 3D (perspectiva forte): prédios como
 * caixas extrudadas, chão em grade, argolas como anéis 3D com furo, pássaro
 * volumétrico com rolagem, além do ciclo dia/noite, efeitos, obstáculos
 * (balões) e power-ups (turbo/escudo). Layout de referência 540x1080; tudo é
 * escalado por [u] = largura/540.
 */
class GameRenderer {

    private class Building(val row: Int, val x: Float, val w: Float, val h: Float, val lit: Int)
    private class Tree(val side: Float, val t: Float, val off: Float)
    private class Star(val x: Float, val y: Float, val r: Float, val tw: Float)
    private class BgBird(val x: Float, val y: Float, val s: Float, val ph: Float)
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                           var life: Float, val maxLife: Float, val size: Float, val color: Int, val grav: Float)
    private class Popup(var x: Float, var y: Float, var age: Float, val maxAge: Float, val text: String)
    private class Halo(var x: Float, var y: Float, var age: Float, val maxAge: Float)

    private val city: List<Building>
    private val trees: List<Tree>
    private val stars: List<Star>
    private val bgBirds: List<BgBird>
    private val clouds = arrayOf(floatArrayOf(-0.6f, 26f, 1.0f), floatArrayOf(0.15f, 44f, 0.8f), floatArrayOf(-0.15f, 64f, 1.2f))

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
        val list = ArrayList<Building>()
        for (row in 0..1) for (i in 0 until 11) {
            list.add(Building(row, (i / 10f * 2f - 1f) * 1.05f, 0.05f + r.nextFloat() * 0.055f,
                (if (row == 1) 0.10f else 0.07f) + r.nextFloat() * (if (row == 1) 0.18f else 0.12f), (r.nextFloat() * 10).toInt()))
        }
        city = list
        trees = (0 until 12).map { Tree(if (it % 2 == 0) -1f else 1f, 0.12f + r.nextFloat() * 0.85f, 0.02f + r.nextFloat() * 0.06f) }
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
        drawCity(canvas, w, horizonY, vpX, night, engine)
        drawFog(canvas, w, horizonY, night)
        drawGround(canvas, w, h, horizonY, vpX, night, engine)
        drawHighway(canvas, w, h, horizonY, vpX, u, night, engine)
        drawTrees(canvas, w, h, horizonY, vpX, u, night, engine)
        drawRings(canvas, w, h, horizonY, vpX, u, night, engine)
        drawObstacles(canvas, w, h, horizonY, vpX, u, engine)
        drawPowerups(canvas, w, h, horizonY, vpX, u, engine)
        drawHalos(canvas, u)
        drawBird(canvas, w, h, u, engine, input)
        drawParticles(canvas)
        drawPopups(canvas, u)

        canvas.restore()

        if (night > 0.05f) { fill.color = Color.argb((night * 0.28f * 255).toInt(), 10, 20, 45); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }
        if (engine.turboTime > 0f) { fill.color = Color.argb(30, 255, 150, 40); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }

        if (engine.state == GameState.PLAYING) drawHud(canvas, w, h, u, engine, input)
        drawMessages(canvas, w, h, u, engine, input)
    }

    // -------------------------------------------------------------- efeitos

    private fun handleEvents(engine: GameEngine, w: Int, h: Int, u: Float) {
        val by = altToScreenY(engine.altitude, h); val bx = w * 0.5f
        if (engine.score > prevScore) {
            burst(bx, by, u)
            popups.add(Popup(bx, by - 30f * u, 0f, 0.9f, "+${engine.score - prevScore}"))
            halos.add(Halo(bx, by, 0f, 0.5f))
        }
        if (engine.state == GameState.CRASHED && prevState != GameState.CRASHED) { feathers(bx, by, engine.bird, u); shake = 16f * u }
        prevScore = engine.score; prevState = engine.state
    }

    private fun burst(x: Float, y: Float, u: Float) {
        val colors = intArrayOf(Color.rgb(140, 240, 170), Color.rgb(255, 224, 102), Color.WHITE)
        for (i in 0 until 18) { val a = rnd.nextFloat() * 6.283f; val sp = (60f + rnd.nextFloat() * 120f) * u
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 0.6f + rnd.nextFloat() * 0.3f, 0.9f, (2.5f + rnd.nextFloat() * 3f) * u, colors[i % 3], 40f * u)) }
    }

    private fun feathers(x: Float, y: Float, bird: BirdType, u: Float) {
        val colors = intArrayOf(bird.wing, bird.body, bird.edge)
        for (i in 0 until 16) { val a = -1.6f + (rnd.nextFloat() - 0.5f) * 2.4f; val sp = (80f + rnd.nextFloat() * 160f) * u
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 1.0f + rnd.nextFloat() * 0.6f, 1.6f, (4f + rnd.nextFloat() * 4f) * u, colors[i % 3], 260f * u)) }
    }

    private fun stepEffects(dt: Float) {
        if (shake > 0f) shake = (shake - 60f * dt).coerceAtLeast(0f)
        particles.iterator().let { it -> while (it.hasNext()) { val p = it.next(); p.x += p.vx * dt; p.y += p.vy * dt; p.vy += p.grav * dt; p.life -= dt; if (p.life <= 0f) it.remove() } }
        popups.iterator().let { it -> while (it.hasNext()) { val p = it.next(); p.age += dt; if (p.age >= p.maxAge) it.remove() } }
        halos.iterator().let { it -> while (it.hasNext()) { val hh = it.next(); hh.age += dt; if (hh.age >= hh.maxAge) it.remove() } }
    }

    // -------------------------------------------------------------- mundo

    private fun drawSky(canvas: Canvas, w: Int, horizonY: Float, night: Float, tod: Float) {
        fill.shader = LinearGradient(0f, 0f, 0f, horizonY, intArrayOf(interpColor(skyTop, tod), interpColor(skyMid, tod), interpColor(skyBot, tod)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), horizonY, fill); fill.shader = null
        if (night > 0.05f) for (s in stars) { fill.color = Color.argb((night * (0.4f + 0.6f * s.tw) * 255).toInt().coerceIn(0, 255), 255, 255, 255); canvas.drawCircle(s.x * w, s.y * horizonY, s.r, fill) }
        val cross = (tod * 2f) % 1f; val bx = w * (0.1f + 0.8f * cross); val by = horizonY - sin(cross * Math.PI).toFloat() * horizonY * 0.72f; val moon = tod >= 0.5f
        val glow = if (moon) intArrayOf(Color.argb(230, 220, 230, 245), Color.argb(102, 200, 215, 240), Color.argb(0, 200, 215, 240)) else intArrayOf(Color.argb(242, 255, 244, 214), Color.argb(140, 255, 224, 150), Color.argb(0, 255, 224, 150))
        fill.shader = RadialGradient(bx, by, 110f * (w / 540f), glow, floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, 110f * (w / 540f), fill); fill.shader = null
        fill.color = if (moon) Color.rgb(232, 238, 248) else Color.rgb(255, 246, 220); canvas.drawCircle(bx, by, (if (moon) 26f else 30f) * (w / 540f), fill)
        if (moon) { fill.color = interpColor(skyTop, tod); canvas.drawCircle(bx + 11f * (w / 540f), by - 6f * (w / 540f), 24f * (w / 540f), fill) }
    }

    private fun drawBgBirds(canvas: Canvas, w: Int, horizonY: Float, engine: GameEngine) {
        stroke.color = Color.argb(200, 20, 20, 30); stroke.strokeWidth = 2f
        for (b in bgBirds) {
            var fx = (b.x - engine.distance * 0.0008f - engine.lateral * 0.1f) % 1f; if (fx < 0f) fx += 1f
            val cx = fx * w; val cy = b.y * horizonY; val s = b.s
            val wy = sin(((b.ph + engine.distance * 0.02f) % 1f) * 6.28f) * 4f * s
            canvas.drawPath(Path().apply { moveTo(cx - 9f * s, cy + wy); quadTo(cx, cy - 6f * s, cx, cy); quadTo(cx, cy - 6f * s, cx + 9f * s, cy + wy) }, stroke)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Int, horizonY: Float, u: Float, night: Float, engine: GameEngine) {
        for (c in clouds) {
            c[1] -= engine.forwardSpeed * 0.0022f
            if (c[1] < 8f) { c[0] = rand(-1f, 1f); c[1] = rand(60f, 90f); c[2] = rand(0.6f, 1.3f) }
            val s = 40f / (40f + c[1]); val cx = w * 0.5f + (c[0] - engine.lateral) * w * 0.9f * s; val cy = horizonY * (0.12f + 0.5f * (1f - s)) + 18f; val rr = 58f * s * c[2] * u
            val a = (255 * min(0.9f, s + 0.2f)).toInt()
            fill.color = if (night > 0.5f) Color.argb(a, 70, 80, 110) else Color.argb(a, 255, 255, 255)
            canvas.drawCircle(cx, cy, rr, fill); canvas.drawCircle(cx - rr * 0.8f, cy + rr * 0.2f, rr * 0.7f, fill); canvas.drawCircle(cx + rr * 0.9f, cy + rr * 0.15f, rr * 0.75f, fill)
        }
    }

    private fun poly(canvas: Canvas, pts: Array<FloatArray>, color: Int) {
        fill.color = color
        canvas.drawPath(Path().apply { moveTo(pts[0][0], pts[0][1]); for (i in 1 until pts.size) lineTo(pts[i][0], pts[i][1]); close() }, fill)
    }

    private fun box3d(canvas: Canvas, px: Float, pyTop: Float, bw: Float, bh: Float, horizonY: Float, vpX: Float, night: Float, front: Boolean) {
        val x = px - bw / 2; val y = pyTop; val baseY = horizonY
        val depth = 0.12f * (if (front) 1f else 0.6f)
        val vx = (vpX - px) * depth; val vy = -bh * 0.16f - 6f
        val base = if (night > 0.5f) Color.rgb(42, 63, 87) else Color.rgb(91, 118, 144)
        val side = shade(base, 0.62f); val roof = shade(base, 1.12f)
        poly(canvas, arrayOf(floatArrayOf(x, y), floatArrayOf(x + bw, y), floatArrayOf(x + bw + vx, y + vy), floatArrayOf(x + vx, y + vy)), roof)
        if (px < vpX) poly(canvas, arrayOf(floatArrayOf(x + bw, y), floatArrayOf(x + bw + vx, y + vy), floatArrayOf(x + bw + vx, baseY + vy), floatArrayOf(x + bw, baseY)), side)
        else poly(canvas, arrayOf(floatArrayOf(x, y), floatArrayOf(x + vx, y + vy), floatArrayOf(x + vx, baseY + vy), floatArrayOf(x, baseY)), side)
        fill.shader = LinearGradient(x, y, x, baseY, intArrayOf(base, shade(base, 0.8f)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(Path().apply { moveTo(x, y); lineTo(x + bw, y); lineTo(x + bw, baseY); lineTo(x, baseY); close() }, fill); fill.shader = null
        val cols = max(2, (bw / 9f).toInt()); val rows = max(3, (bh / 13f).toInt()); val cwd = (bw - 6f) / cols; val rhd = (bh - 6f) / rows
        val winA = (0.5f + 0.5f * max(0.4f, night))
        for (rr in 0 until rows) for (cc in 0 until cols) {
            val on = (rr * 7 + cc * 3 + ((px * 13).toInt())) % 4 == 0 || (night > 0.4f && (rr * 5 + cc * 2) % 3 == 0)
            fill.color = if (on) Color.argb((winA * 255).toInt().coerceIn(0, 255), 255, 224, 150) else if (night > 0.5f) Color.argb(178, 15, 25, 40) else Color.argb(150, 35, 52, 70)
            canvas.drawRect(x + 3f + cc * cwd, y + 4f + rr * rhd, x + 3f + cc * cwd + cwd - 2f, y + 4f + rr * rhd + rhd - 3f, fill)
        }
    }

    private fun drawCity(canvas: Canvas, w: Int, horizonY: Float, vpX: Float, night: Float, engine: GameEngine) {
        for (b in city) if (b.row == 0) { val px = w * 0.5f + (b.x - engine.lateral * 0.22f) * w * 0.72f; box3d(canvas, px, horizonY - b.h * (horizonY * 2f), b.w * w * 0.85f, b.h * (horizonY * 2f), horizonY, vpX, night, false) }
        for (b in city) if (b.row == 1) { val px = w * 0.5f + (b.x - engine.lateral * 0.32f) * w * 0.86f; box3d(canvas, px, horizonY - b.h * (horizonY * 2f), b.w * w, b.h * (horizonY * 2f), horizonY, vpX, night, true) }
    }

    private fun drawFog(canvas: Canvas, w: Int, horizonY: Float, night: Float) {
        val c = if (night > 0.5f) Color.rgb(18, 39, 76) else Color.rgb(207, 232, 245)
        fill.shader = LinearGradient(0f, horizonY - 70f, 0f, horizonY + 10f, intArrayOf(withAlpha(c, 0), withAlpha(c, 140)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, horizonY - 70f, w.toFloat(), horizonY + 10f, fill); fill.shader = null
    }

    private fun drawGround(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, night: Float, engine: GameEngine) {
        val gt = (night * 0.55f).coerceIn(0f, 0.55f)
        fill.shader = LinearGradient(0f, horizonY, 0f, h.toFloat(), intArrayOf(lerpColor(Color.rgb(111, 176, 96), Color.rgb(36, 56, 38), gt), lerpColor(Color.rgb(60, 122, 68), Color.rgb(22, 36, 26), gt)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, horizonY, w.toFloat(), h.toFloat(), fill); fill.shader = null
        val scroll = (engine.distance % 10f) / 10f
        fill.color = Color.argb(13, 255, 255, 255)
        for (i in 0 until 12) { if (i % 2 != 0) continue; val t0 = (i + scroll) / 12f; val t1 = (i + 1 + scroll) / 12f; val y0 = horizonY + (h - horizonY) * (t0 * t0); val y1 = horizonY + (h - horizonY) * (t1 * t1); canvas.drawRect(0f, y0, w.toFloat(), y1, fill) }
        stroke.color = Color.argb(30, 255, 255, 255); stroke.strokeWidth = 1.5f
        for (gx in -4..4) { val bx = w * 0.5f + gx * w * 0.16f; canvas.drawLine(bx, h.toFloat(), vpX, horizonY, stroke) }
    }

    private fun drawHighway(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, night: Float, engine: GameEngine) {
        val baseY = h.toFloat(); val baseHalf = w * 0.30f; val topHalf = 6f * u
        fill.shader = LinearGradient(0f, horizonY, 0f, baseY, intArrayOf(if (night > 0.5f) Color.rgb(32, 35, 42) else Color.rgb(51, 55, 62), if (night > 0.5f) Color.rgb(51, 55, 62) else Color.rgb(74, 78, 87)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(Path().apply { moveTo(w * 0.5f - baseHalf, baseY); lineTo(vpX - topHalf, horizonY + 1f); lineTo(vpX + topHalf, horizonY + 1f); lineTo(w * 0.5f + baseHalf, baseY); close() }, fill); fill.shader = null
        stroke.color = Color.argb(230, 240, 240, 240); stroke.strokeWidth = 3f * u
        canvas.drawLine(w * 0.5f - baseHalf + 7f * u, baseY, vpX - topHalf, horizonY + 1f, stroke)
        canvas.drawLine(w * 0.5f + baseHalf - 7f * u, baseY, vpX + topHalf, horizonY + 1f, stroke)
        val scroll = (engine.distance % 9f) / 9f
        fill.color = Color.rgb(244, 209, 58)
        for (i in 0 until 9) { val t = ((i + scroll) % 9f) / 9f; val tt = t * t; val y = horizonY + (baseY - horizonY) * tt; val cx = vpX + (w * 0.5f - vpX) * tt; canvas.drawRect(cx - max(1.2f, 5f * tt * u) / 2, y, cx + max(1.2f, 5f * tt * u) / 2, y + max(2f, 20f * tt * u), fill) }
        val carPhase = (engine.distance * 0.03f) % 1f
        val cars = arrayOf(floatArrayOf(0f, -0.28f, 0f), floatArrayOf(0.45f, 0.30f, 1f), floatArrayOf(0.8f, 0.05f, 2f))
        val cc = intArrayOf(Color.rgb(224, 49, 49), Color.rgb(28, 126, 214), Color.rgb(245, 159, 0))
        for (car in cars) {
            val t = (car[0] + carPhase) % 1f; val tt = t * t; val y = horizonY + (baseY - horizonY) * tt; val half = topHalf + (baseHalf - topHalf) * tt
            val cx = vpX + (w * 0.5f - vpX) * tt + car[1] * half; val cw = max(3f, half * 0.5f); val ch = cw * 0.8f; val col = cc[car[2].toInt()]
            poly(canvas, arrayOf(floatArrayOf(cx - cw / 2, y + ch * 0.2f), floatArrayOf(cx - cw * 0.35f, y - ch * 0.3f), floatArrayOf(cx + cw * 0.35f, y - ch * 0.3f), floatArrayOf(cx + cw / 2, y + ch * 0.2f)), shade(col, 1.15f))
            fill.color = col; canvas.drawRoundRect(cx - cw / 2, y - ch * 0.1f, cx + cw / 2, y - ch * 0.1f + ch * 0.5f, min(3f, cw * 0.2f), min(3f, cw * 0.2f), fill)
            if (night > 0.5f) { fill.color = Color.argb(230, 255, 240, 180); canvas.drawRect(cx - cw * 0.42f, y + ch * 0.1f, cx - cw * 0.2f, y + ch * 0.28f, fill); canvas.drawRect(cx + cw * 0.2f, y + ch * 0.1f, cx + cw * 0.42f, y + ch * 0.28f, fill) }
        }
    }

    private fun drawTrees(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, night: Float, engine: GameEngine) {
        val gt = (night * 0.55f).coerceIn(0f, 0.55f)
        for (tr in trees) {
            val tt = tr.t * tr.t; val y = horizonY + (h - horizonY) * tt; val roadHalf = 6f + (w * 0.30f - 6f) * tt
            val cx = vpX + (w * 0.5f - vpX) * tt + tr.side * (roadHalf + tr.off * w * (0.4f + tt)); val sc = (0.25f + tt * 1.1f) * u
            fill.color = Color.argb(46, 0, 0, 0); canvas.drawOval(cx - 12f * sc, y - 4f * sc, cx + 12f * sc, y + 4f * sc, fill)
            fill.color = if (night > 0.5f) Color.rgb(58, 42, 24) else Color.rgb(107, 74, 43); canvas.drawRect(cx - 3f * sc, y - 16f * sc, cx + 3f * sc, y, fill)
            fill.shader = RadialGradient(cx - 4f * sc, y - 24f * sc, 20f * sc, lerpColor(Color.rgb(74, 168, 90), Color.rgb(32, 80, 46), gt), lerpColor(Color.rgb(42, 109, 56), Color.rgb(18, 53, 31), gt), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, y - 22f * sc, 14f * sc, fill); canvas.drawCircle(cx - 10f * sc, y - 16f * sc, 10f * sc, fill); canvas.drawCircle(cx + 10f * sc, y - 16f * sc, 10f * sc, fill); fill.shader = null
        }
    }

    private fun altToScreenY(alt: Float, h: Int): Float { val g = h * 0.9f; val s = h * 0.14f; return g + (s - g) * (alt / GameEngine.CEILING).coerceIn(0f, 1f) }

    /** Projeta um item do mundo (z, laneX, altitude) para a tela. */
    private fun project(z: Float, laneX: Float, altitude: Float, w: Int, h: Int, horizonY: Float, vpX: Float, lateral: Float): FloatArray {
        val scale = 46f / (46f + z.coerceAtLeast(-1f)); val f = (1f - scale).coerceIn(0f, 1f)
        val baseX = w * 0.5f + (laneX - lateral) * (w * 0.42f); val baseY = altToScreenY(altitude, h)
        return floatArrayOf(baseX + (vpX - baseX) * f, baseY + (horizonY - baseY) * f, scale)
    }

    private fun ring3d(canvas: Canvas, sx: Float, sy: Float, ro: Float, base: Int, glow: Boolean) {
        val ry = ro * 0.5f; val ri = ro * 0.60f; val riy = ry * 0.60f
        if (glow) fill.setShadowLayer(ro * 0.25f, 0f, 0f, base)
        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addOval(RectF(sx - ro, sy - ry, sx + ro, sy + ry), Path.Direction.CW)
            addOval(RectF(sx - ri, sy - riy, sx + ri, sy + riy), Path.Direction.CCW)
        }
        fill.shader = LinearGradient(sx, sy - ry, sx, sy + ry, intArrayOf(shade(base, 1.25f), base, shade(base, 0.6f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill); fill.shader = null; fill.clearShadowLayer()
        stroke.color = shade(base, 0.5f); stroke.strokeWidth = max(1f, ro * 0.03f); canvas.drawOval(sx - ri, sy - riy, sx + ri, sy + riy, stroke)
        stroke.color = Color.argb(165, 255, 255, 255); stroke.strokeWidth = max(1f, ro * 0.04f)
        canvas.drawArc(sx - (ro + ri) / 2, sy - (ry + riy) / 2, sx + (ro + ri) / 2, sy + (ry + riy) / 2, 25f, 130f, false, stroke)
    }

    private fun drawRings(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, night: Float, engine: GameEngine) {
        for (r in engine.rings.sortedByDescending { it.z }) {
            if (r.z <= -2f) continue
            val p = project(r.z, r.laneX, r.altitude, w, h, horizonY, vpX, engine.lateral)
            val base = when { r.scored -> Color.rgb(79, 208, 122); r.missed -> Color.argb(150, 184, 90, 90); else -> Color.rgb(255, 204, 40) }
            ring3d(canvas, p[0], p[1], 130f * p[2] * u, base, !r.scored && !r.missed && night > 0.4f)
        }
    }

    private fun drawObstacles(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, engine: GameEngine) {
        for (o in engine.obstacles.sortedByDescending { it.z }) {
            if (o.z <= -2f || o.popped) continue
            val p = project(o.z, o.laneX, o.altitude, w, h, horizonY, vpX, engine.lateral)
            val sx = p[0]; val sy = p[1] + sin((engine.distance * 0.08f + o.laneX * 6f)) * 4f * u; val rr = 46f * p[2] * u
            // Cordinha
            stroke.color = Color.argb(160, 60, 60, 60); stroke.strokeWidth = max(1f, 1.5f * p[2] * u); canvas.drawLine(sx, sy + rr, sx, sy + rr * 2.4f, stroke)
            // Balão (esfera vermelha com brilho)
            fill.shader = RadialGradient(sx - rr * 0.3f, sy - rr * 0.35f, rr * 1.25f, intArrayOf(Color.rgb(255, 140, 130), Color.rgb(214, 40, 40), Color.rgb(150, 20, 20)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(sx, sy, rr, fill); fill.shader = null
            fill.color = Color.argb(150, 255, 255, 255); canvas.drawOval(sx - rr * 0.5f, sy - rr * 0.6f, sx - rr * 0.1f, sy - rr * 0.2f, fill)
            // Bico do balão
            poly(canvas, arrayOf(floatArrayOf(sx - rr * 0.16f, sy + rr * 0.95f), floatArrayOf(sx + rr * 0.16f, sy + rr * 0.95f), floatArrayOf(sx, sy + rr * 1.2f)), Color.rgb(150, 20, 20))
        }
    }

    private fun drawPowerups(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, engine: GameEngine) {
        for (pu in engine.powerups.sortedByDescending { it.z }) {
            if (pu.z <= -2f || pu.taken) continue
            val p = project(pu.z, pu.laneX, pu.altitude, w, h, horizonY, vpX, engine.lateral)
            val sx = p[0]; val sy = p[1]; val rr = 40f * p[2] * u
            val pulse = 0.85f + 0.15f * sin(engine.distance * 0.15f)
            val turbo = pu.kind == PowerKind.TURBO
            val core = if (turbo) Color.rgb(255, 170, 40) else Color.rgb(70, 200, 255)
            fill.shader = RadialGradient(sx, sy, rr * pulse, intArrayOf(withAlpha(core, 235), withAlpha(core, 90), withAlpha(core, 0)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(sx, sy, rr * 1.4f * pulse, fill); fill.shader = null
            fill.color = Color.argb(230, 255, 255, 255)
            if (turbo) {
                canvas.drawPath(Path().apply { moveTo(sx + rr * 0.15f, sy - rr * 0.6f); lineTo(sx - rr * 0.3f, sy + rr * 0.1f); lineTo(sx, sy + rr * 0.1f); lineTo(sx - rr * 0.15f, sy + rr * 0.6f); lineTo(sx + rr * 0.3f, sy - rr * 0.15f); lineTo(sx, sy - rr * 0.15f); close() }, fill)
            } else {
                canvas.drawPath(Path().apply { moveTo(sx, sy - rr * 0.55f); lineTo(sx + rr * 0.45f, sy - rr * 0.3f); lineTo(sx + rr * 0.45f, sy + rr * 0.15f); lineTo(sx, sy + rr * 0.6f); lineTo(sx - rr * 0.45f, sy + rr * 0.15f); lineTo(sx - rr * 0.45f, sy - rr * 0.3f); close() }, fill)
                fill.color = core; canvas.drawPath(Path().apply { moveTo(sx, sy - rr * 0.4f); lineTo(sx + rr * 0.3f, sy - rr * 0.2f); lineTo(sx + rr * 0.3f, sy + rr * 0.12f); lineTo(sx, sy + rr * 0.42f); lineTo(sx - rr * 0.3f, sy + rr * 0.12f); lineTo(sx - rr * 0.3f, sy - rr * 0.2f); close() }, fill)
            }
        }
    }

    private fun drawHalos(canvas: Canvas, u: Float) {
        for (hl in halos) { val p = hl.age / hl.maxAge; stroke.color = Color.argb(((1f - p) * 255).toInt().coerceIn(0, 255), 140, 240, 170); stroke.strokeWidth = 6f * (1f - p) + 1f; canvas.drawCircle(hl.x, hl.y, (20f + p * 130f) * u, stroke) }
    }

    private fun drawParticles(canvas: Canvas) { for (p in particles) { fill.color = withAlpha(p.color, ((p.life / p.maxLife).coerceIn(0f, 1f) * 255).toInt()); canvas.drawCircle(p.x, p.y, p.size, fill) } }

    private fun drawPopups(canvas: Canvas, u: Float) {
        textPaint.textAlign = Paint.Align.CENTER; textPaint.isFakeBoldText = true; textPaint.textSize = 30f * u
        for (p in popups) { val a = (1f - p.age / p.maxAge).coerceIn(0f, 1f); textPaint.color = Color.argb((a * 255).toInt(), 255, 224, 102); canvas.drawText(p.text, p.x, p.y - (p.age / p.maxAge) * 50f * u, textPaint) }
    }

    private fun drawBirdShape(canvas: Canvas, bird: BirdType, flapPhase: Float, spread: Float, scale: Float, bank: Float) {
        canvas.save(); canvas.scale(scale, scale)
        if (bank != 0f) { canvas.rotate(bank * 20f); canvas.skew(-bank * 0.35f, 0f); canvas.scale(1f - kotlin.math.abs(bank) * 0.18f, 1f) }
        val flap = sin(flapPhase * 2f * Math.PI).toFloat(); val up = flap * 30f; val sp = 1f + spread * 0.4f
        fill.color = bird.edge; canvas.drawPath(Path().apply { moveTo(0f, 10f); lineTo(-18f, 52f); lineTo(0f, 42f); lineTo(18f, 52f); close() }, fill)
        for (s in intArrayOf(-1, 1)) {
            fill.shader = LinearGradient(0f, -20f, 0f, 20f, intArrayOf(shade(bird.wing, 1.25f), bird.wing, bird.edge), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawPath(Path().apply { moveTo(s * 8f, -6f); quadTo(s * 72f * sp, -24f - up, s * 140f * sp, -4f - up * 0.5f); quadTo(s * 120f * sp, 16f - up * 0.3f, s * 70f * sp, 22f); quadTo(s * 40f, 16f, s * 8f, 10f); close() }, fill); fill.shader = null
            stroke.color = bird.edge; stroke.strokeWidth = 2f
            for (i in 1..4) { val t = i / 5f; canvas.drawLine(s * (24f + 60f * t * sp), 6f - up * 0.4f * t, s * (140f * sp * t + 18f), -4f - up * 0.5f, stroke) }
        }
        fill.shader = RadialGradient(-7f, -4f, 42f, intArrayOf(shade(bird.wing, 1.4f), bird.body, shade(bird.body, 0.7f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawOval(-20f, -26f, 20f, 38f, fill); fill.shader = null
        fill.shader = RadialGradient(-5f, -32f, 17f, shade(bird.head, 1.2f), shade(bird.head, 0.82f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, -28f, 15f, fill); fill.shader = null
        fill.color = bird.beak; canvas.drawPath(Path().apply { moveTo(-6f, -40f); lineTo(0f, -56f); lineTo(6f, -40f); close() }, fill)
        fill.color = Color.rgb(22, 22, 22); canvas.drawCircle(-7f, -30f, 2.6f, fill); canvas.drawCircle(7f, -30f, 2.6f, fill)
        fill.color = Color.WHITE; canvas.drawCircle(-6.2f, -30.8f, 0.9f, fill); canvas.drawCircle(7.8f, -30.8f, 0.9f, fill)
        canvas.restore()
    }

    private fun drawBird(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        val bx = w * 0.5f; val by = altToScreenY(engine.altitude, h); val groundY = h * 0.9f
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f); val ss = 1f - altT * 0.7f
        canvas.save(); canvas.translate(bx, groundY); canvas.skew(-0.3f, 0f); canvas.scale(1f, 0.5f)
        fill.color = Color.argb(56, 0, 0, 0); canvas.drawOval(-72f * ss * u, -40f * ss * u, 72f * ss * u, 40f * ss * u, fill); canvas.restore()
        for (i in 1..5) { fill.color = Color.argb(((0.10f - i * 0.015f) * 255).toInt().coerceAtLeast(0), 255, 255, 255); canvas.drawOval(bx - (20f - i * 2) * u, by + i * 10f * u - 8f * u, bx + (20f - i * 2) * u, by + i * 10f * u + 8f * u, fill) }
        val turbo = engine.turboTime > 0f
        if (input.spread > 0.45f || turbo) {
            stroke.color = if (turbo) Color.argb(150, 255, 180, 60) else Color.argb(90, 255, 255, 255); stroke.strokeWidth = (if (turbo) 3f else 2f) * u
            val n = if (turbo) 12 else 7
            for (i in 0 until n) { val a = (i / n.toFloat()) * 6.28f; val r0 = (60f + ((i * 53 + engine.distance * 4) % 60)) * u; canvas.drawLine(bx + cos(a) * r0, by + sin(a) * r0, bx + cos(a) * (r0 + (if (turbo) 40f else 28f) * u), by + sin(a) * (r0 + (if (turbo) 40f else 28f) * u), stroke) }
        }
        canvas.save(); canvas.translate(bx, by); drawBirdShape(canvas, engine.bird, engine.flapPhase, input.spread, u, engine.bank); canvas.restore()
        // Bolha de escudo
        if (engine.hasShield) {
            val pulse = 0.9f + 0.1f * sin(engine.distance * 0.2f)
            fill.shader = RadialGradient(bx, by, 66f * u * pulse, intArrayOf(Color.argb(0, 90, 200, 255), Color.argb(60, 90, 200, 255), Color.argb(120, 140, 220, 255)), floatArrayOf(0.6f, 0.85f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(bx, by, 66f * u * pulse, fill); fill.shader = null
            stroke.color = Color.argb(200, 150, 225, 255); stroke.strokeWidth = 2.5f * u; canvas.drawCircle(bx, by, 66f * u * pulse, stroke)
        }
    }

    // -------------------------------------------------------------- HUD / telas

    private fun drawHud(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        txt(canvas, "Pontos: ${engine.score}", 28f * u, 66f * u, 26f * u, true, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "Recorde: ${engine.best}", 28f * u, 110f * u, 18f * u, false, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "Distância: ${engine.distance.toInt()} m", 28f * u, 148f * u, 18f * u, false, Color.WHITE, Paint.Align.LEFT)
        if (engine.combo >= 2) txt(canvas, "Combo x${engine.combo}", 28f * u, 186f * u, 18f * u, false, Color.rgb(255, 220, 90), Paint.Align.LEFT)
        // Indicadores de power-up
        var iy = 222f * u
        if (engine.hasShield) { fill.color = Color.rgb(70, 200, 255); canvas.drawCircle(40f * u, iy, 11f * u, fill); txt(canvas, "Escudo", 60f * u, iy + 7f * u, 18f * u, true, Color.WHITE, Paint.Align.LEFT); iy += 36f * u }
        if (engine.turboTime > 0f) {
            fill.color = Color.argb(60, 0, 0, 0); canvas.drawRoundRect(28f * u, iy - 10f * u, 158f * u, iy + 4f * u, 7f, 7f, fill)
            fill.color = Color.rgb(255, 170, 40); canvas.drawRoundRect(28f * u, iy - 10f * u, (28f + 130f * (engine.turboTime / engine.turboMax)) * u, iy + 4f * u, 7f, 7f, fill)
            txt(canvas, "Turbo", 166f * u, iy + 3f * u, 16f * u, true, Color.rgb(255, 190, 90), Paint.Align.LEFT)
        }
        txt(canvas, engine.bird.name, 28f * u, h - 34f * u, 20f * u, true, Color.WHITE, Paint.Align.LEFT)
        val barX = w - 44f * u; val barTop = 66f * u; val barBot = h * 0.58f
        fill.color = Color.argb(90, 0, 0, 0); canvas.drawRoundRect(barX - 4f, barTop - 4f, barX + 22f, barBot + 4f, 12f, 12f, fill)
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f); val fillTop = barBot - (barBot - barTop) * altT
        fill.color = if (engine.altitude < 12f) Color.rgb(230, 90, 90) else Color.rgb(120, 200, 255); canvas.drawRoundRect(barX, fillTop, barX + 18f, barBot, 9f, 9f, fill)
        fill.color = if (input.detected) Color.rgb(120, 230, 140) else Color.rgb(230, 120, 120); canvas.drawCircle(w - 30f * u, h - 36f * u, 11f * u, fill)
        txt(canvas, if (input.detected) "rastreando" else "sem pose", w - 48f * u, h - 30f * u, 16f * u, false, Color.WHITE, Paint.Align.RIGHT)
    }

    private fun drawMessages(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        when (engine.state) {
            GameState.READY -> drawSelectScreen(canvas, w, h, u, engine, input)
            GameState.CRASHED -> {
                dim(canvas, w, h, 120)
                txt(canvas, "Você caiu!", w / 2f, h * 0.36f, 56f * u, true, Color.WHITE, Paint.Align.CENTER)
                txt(canvas, "Pontos: ${engine.score}   Recorde: ${engine.best}", w / 2f, h * 0.44f, 24f * u, false, Color.WHITE, Paint.Align.CENTER)
                val hint = when {
                    !input.detected -> "Fique visível para a câmera"
                    !input.confident -> "Mostre os dois braços abertos como asas"
                    else -> "Segure os braços abertos para voar de novo"
                }
                txt(canvas, hint, w / 2f, h * 0.53f, 22f * u, true, if (input.detected && input.confident) Color.WHITE else Color.rgb(255, 180, 120), Paint.Align.CENTER)
                if (engine.takeoffProgress > 0.01f) drawTakeoffBar(canvas, w, h, u, engine.takeoffProgress)
            }
            GameState.PLAYING -> if (!input.detected) txt(canvas, "Reposicione-se na câmera", w / 2f, h * 0.14f, 22f * u, true, Color.rgb(255, 180, 120), Paint.Align.CENTER)
        }
    }

    private fun drawSelectScreen(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        dim(canvas, w, h, 128)
        txt(canvas, "VooApp", w / 2f, h * 0.16f, 58f * u, true, Color.WHITE, Paint.Align.CENTER)
        txt(canvas, "Simulador de voo com o corpo", w / 2f, h * 0.205f, 22f * u, false, Color.argb(217, 255, 255, 255), Paint.Align.CENTER)
        txt(canvas, "Escolha seu pássaro", w / 2f, h * 0.30f, 26f * u, true, Color.rgb(255, 224, 138), Paint.Align.CENTER)
        canvas.save(); canvas.translate(w / 2f, h * 0.44f); drawBirdShape(canvas, engine.bird, 0.12f, 0.5f, 1.5f * u, 0f); canvas.restore()
        val n = BirdType.ALL.size; val cw = w.toFloat() / n; val cardY = h * 0.60f; val cardH = h * 0.14f
        for (i in 0 until n) {
            val cx = cw * i + cw / 2; val on = i == engine.selectedIndex
            fill.color = if (on) Color.argb(56, 255, 224, 138) else Color.argb(20, 255, 255, 255)
            canvas.drawRoundRect(cx - cw * 0.42f, cardY, cx + cw * 0.42f, cardY + cardH, 14f, 14f, fill)
            if (on) { stroke.color = Color.rgb(255, 224, 138); stroke.strokeWidth = 3f * u; canvas.drawRoundRect(cx - cw * 0.42f, cardY, cx + cw * 0.42f, cardY + cardH, 14f, 14f, stroke) }
            canvas.save(); canvas.translate(cx, cardY + cardH * 0.40f); drawBirdShape(canvas, BirdType.ALL[i], 0.1f, 0.4f, 0.42f * u, 0f); canvas.restore()
            txt(canvas, BirdType.ALL[i].name, cx, cardY + cardH * 0.88f, 17f * u, on, if (on) Color.rgb(255, 224, 138) else Color.WHITE, Paint.Align.CENTER)
        }
        txt(canvas, "Toque para escolher", w / 2f, h * 0.80f, 20f * u, false, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        val hint = when {
            !input.detected -> "Fique visível para a câmera"
            !input.confident -> "Mostre os dois braços abertos como asas"
            else -> "Segure os braços abertos para decolar"
        }
        txt(canvas, hint, w / 2f, h * 0.835f, 22f * u, true, if (input.detected && input.confident) Color.WHITE else Color.rgb(255, 180, 120), Paint.Align.CENTER)
        if (engine.takeoffProgress > 0.01f) drawTakeoffBar(canvas, w, h, u, engine.takeoffProgress)
    }

    private fun drawTakeoffBar(canvas: Canvas, w: Int, h: Int, u: Float, progress: Float) {
        val bw = w * 0.5f; val bx = w * 0.5f - bw / 2; val by = h * 0.88f; val bh = 14f * u
        fill.color = Color.argb(130, 0, 0, 0)
        canvas.drawRoundRect(bx - 3f, by - 3f, bx + bw + 3f, by + bh + 3f, 10f, 10f, fill)
        fill.color = Color.rgb(120, 230, 140)
        canvas.drawRoundRect(bx, by, bx + bw * progress.coerceIn(0f, 1f), by + bh, 8f, 8f, fill)
        txt(canvas, "Decolando…", w / 2f, by - 10f * u, 16f * u, true, Color.rgb(150, 240, 170), Paint.Align.CENTER)
    }

    private fun dim(canvas: Canvas, w: Int, h: Int, alpha: Int) { fill.color = Color.argb(alpha, 6, 20, 38); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }

    private fun txt(canvas: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int, align: Paint.Align) {
        textPaint.textSize = size; textPaint.isFakeBoldText = bold; textPaint.textAlign = align
        shadowText.textSize = size; shadowText.isFakeBoldText = bold; shadowText.textAlign = align
        canvas.drawText(s, x + 2f, y + 2f, shadowText); textPaint.color = color; canvas.drawText(s, x, y, textPaint)
    }

    // -------------------------------------------------------------- util

    private fun ints(vararg hex: String) = IntArray(hex.size) { Color.parseColor(hex[it]) }

    private fun interp(vals: FloatArray, t: Float): Float {
        for (i in 0 until todKeys.size - 1) if (t >= todKeys[i] && t <= todKeys[i + 1]) { val tt = (t - todKeys[i]) / (todKeys[i + 1] - todKeys[i]); return vals[i] + (vals[i + 1] - vals[i]) * tt }
        return vals.last()
    }

    private fun interpColor(cols: IntArray, t: Float): Int {
        for (i in 0 until todKeys.size - 1) if (t >= todKeys[i] && t <= todKeys[i + 1]) { val tt = (t - todKeys[i]) / (todKeys[i + 1] - todKeys[i]); return lerpColor(cols[i], cols[i + 1], tt) }
        return cols.last()
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        return Color.rgb((Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255))
    }

    private fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))
    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
    private fun rand(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    private fun min(a: Float, b: Float) = if (a < b) a else b
}
