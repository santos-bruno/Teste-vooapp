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
import kotlin.math.max
import kotlin.math.sin

/**
 * Desenha o estado do [GameEngine] num [Canvas] com um visual de mundo aberto:
 * céu com sol, cidade no horizonte, rodovia com carros, árvores, argolas e um
 * pássaro detalhado (colorido conforme o tipo escolhido). Todas as medidas são
 * escaladas por [u] = largura/540 para manter as proporções em qualquer tela.
 *
 * Layout de referência: 540 x 1080. Portado de scene2 (protótipo em canvas).
 */
class GameRenderer {

    private class Building(val x: Float, val w: Float, val h: Float, val lit: Int)
    private class Tree(val side: Float, val t: Float, val off: Float)

    private val cityMain: List<Building>
    private val cityBack: List<Building>
    private val trees: List<Tree>
    private val clouds = arrayOf(
        floatArrayOf(-0.6f, 26f, 1.0f),
        floatArrayOf(0.15f, 44f, 0.8f),
        floatArrayOf(-0.15f, 64f, 1.2f),
    )

    init {
        val r = Random(7)
        cityMain = (0 until 16).map {
            Building(it / 15f * 2f - 1f, 0.045f + r.nextFloat() * 0.05f,
                0.06f + r.nextFloat() * 0.17f, (r.nextFloat() * 10).toInt())
        }
        cityBack = (0 until 14).map {
            Building(it / 13f * 2f - 1f + 0.03f, 0.05f + r.nextFloat() * 0.05f,
                0.04f + r.nextFloat() * 0.10f, 0)
        }
        trees = (0 until 10).map {
            Tree(if (it % 2 == 0) -1f else 1f, 0.15f + r.nextFloat() * 0.8f, 0.02f + r.nextFloat() * 0.06f)
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(128, 0, 0, 0) }

    private var lastW = -1
    private var lastH = -1
    private var skyShader: Shader? = null
    private var groundShader: Shader? = null
    private var sunShader: Shader? = null

    fun render(canvas: Canvas, engine: GameEngine, input: FlightInput) {
        val w = canvas.width
        val h = canvas.height
        val u = w / 540f
        val horizonY = h * 0.5f
        val vpX = w * 0.5f - engine.lateral * w * 0.16f

        if (w != lastW || h != lastH) {
            buildShaders(w, h)
            lastW = w; lastH = h
        }

        drawSky(canvas, w, horizonY, u)
        drawClouds(canvas, w, horizonY, u, engine)
        drawCity(canvas, w, horizonY, engine)
        drawGround(canvas, w, h, horizonY, engine)
        drawHighway(canvas, w, h, horizonY, vpX, u, engine)
        drawTrees(canvas, w, h, horizonY, vpX, u, engine)
        drawRings(canvas, w, h, horizonY, vpX, u, engine)
        drawBird(canvas, w, h, u, engine, input)
        if (engine.state == GameState.PLAYING) drawHud(canvas, w, h, u, engine, input)
        drawMessages(canvas, w, h, u, engine, input)
    }

    private fun buildShaders(w: Int, h: Int) {
        val horizonY = h * 0.5f
        skyShader = LinearGradient(0f, 0f, 0f, horizonY,
            intArrayOf(Color.rgb(30, 95, 166), Color.rgb(90, 160, 210), Color.rgb(207, 232, 245)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        groundShader = LinearGradient(0f, horizonY, 0f, h.toFloat(),
            intArrayOf(Color.rgb(127, 191, 106), Color.rgb(60, 122, 68)), null, Shader.TileMode.CLAMP)
        val sunx = w * 0.72f; val suny = horizonY * 0.42f
        sunShader = RadialGradient(sunx, suny, 120f * (w / 540f),
            intArrayOf(Color.argb(242, 255, 244, 214), Color.argb(140, 255, 224, 150), Color.argb(0, 255, 224, 150)),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    }

    private fun drawSky(canvas: Canvas, w: Int, horizonY: Float, u: Float) {
        fill.shader = skyShader
        canvas.drawRect(0f, 0f, w.toFloat(), horizonY, fill)
        fill.shader = null
        // Sol
        val sunx = w * 0.72f; val suny = horizonY * 0.42f
        fill.shader = sunShader
        canvas.drawCircle(sunx, suny, 120f * u, fill)
        fill.shader = null
        fill.color = Color.rgb(255, 246, 220)
        canvas.drawCircle(sunx, suny, 30f * u, fill)
    }

    private fun drawClouds(canvas: Canvas, w: Int, horizonY: Float, u: Float, engine: GameEngine) {
        for (c in clouds) {
            c[1] -= engine.forwardSpeed * 0.0022f
            if (c[1] < 8f) { c[0] = rand(-1f, 1f); c[1] = rand(60f, 90f); c[2] = rand(0.6f, 1.3f) }
            val s = 40f / (40f + c[1])
            val cx = w * 0.5f + (c[0] - engine.lateral) * w * 0.9f * s
            val cy = horizonY * (0.12f + 0.5f * (1f - s)) + 18f
            val rr = 58f * s * c[2] * u
            fill.color = Color.argb((255 * min(0.9f, s + 0.2f)).toInt(), 255, 255, 255)
            canvas.drawCircle(cx, cy, rr, fill)
            canvas.drawCircle(cx - rr * 0.8f, cy + rr * 0.2f, rr * 0.7f, fill)
            canvas.drawCircle(cx + rr * 0.9f, cy + rr * 0.15f, rr * 0.75f, fill)
        }
    }

    private fun drawCity(canvas: Canvas, w: Int, horizonY: Float, engine: GameEngine) {
        // Fundo (haze)
        fill.color = Color.argb(140, 120, 150, 180)
        for (b in cityBack) {
            val px = w * 0.5f + (b.x - engine.lateral * 0.2f) * w * 0.85f
            val bw = b.w * w; val bh = b.h * (horizonY * 2f)
            canvas.drawRect(px - bw / 2, horizonY - bh, px + bw / 2, horizonY, fill)
        }
        // Cidade principal
        for (b in cityMain) {
            val px = w * 0.5f + (b.x - engine.lateral * 0.3f) * w * 0.8f
            val bw = b.w * w; val bh = b.h * (horizonY * 2f)
            val x = px - bw / 2; val y = horizonY - bh
            fill.color = Color.rgb(50, 74, 99)
            canvas.drawRect(x, y, x + bw, horizonY, fill)
            fill.color = Color.rgb(38, 56, 74)
            canvas.drawRect(x, y, x + bw, y + 3f, fill)
            val cols = max(2, (bw / 9f).toInt())
            val rows = max(3, (bh / 12f).toInt())
            val cwd = (bw - 6f) / cols; val rhd = (bh - 6f) / rows
            for (rr in 0 until rows) for (cc in 0 until cols) {
                val on = (rr * 7 + cc * 3 + b.lit) % 4 == 0
                fill.color = if (on) Color.argb(242, 255, 224, 150) else Color.argb(178, 30, 45, 60)
                canvas.drawRect(x + 3f + cc * cwd, y + 4f + rr * rhd, x + 3f + cc * cwd + cwd - 2f, y + 4f + rr * rhd + rhd - 3f, fill)
            }
        }
    }

    private fun drawGround(canvas: Canvas, w: Int, h: Int, horizonY: Float, engine: GameEngine) {
        fill.shader = groundShader
        canvas.drawRect(0f, horizonY, w.toFloat(), h.toFloat(), fill)
        fill.shader = null
        val scroll = (engine.distance % 10f) / 10f
        stroke.color = Color.argb(26, 255, 255, 255)
        stroke.strokeWidth = 2f
        for (i in 1..9) {
            val t = (i - scroll) / 9f
            if (t <= 0f) continue
            val y = horizonY + (h - horizonY) * (t * t)
            canvas.drawLine(0f, y, w.toFloat(), y, stroke)
        }
    }

    private fun drawHighway(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, engine: GameEngine) {
        val baseY = h.toFloat(); val baseHalf = w * 0.30f; val topHalf = 6f * u
        val road = Path().apply {
            moveTo(w * 0.5f - baseHalf, baseY)
            lineTo(vpX - topHalf, horizonY + 1f)
            lineTo(vpX + topHalf, horizonY + 1f)
            lineTo(w * 0.5f + baseHalf, baseY)
            close()
        }
        fill.color = Color.rgb(64, 68, 75)
        canvas.drawPath(road, fill)
        stroke.color = Color.argb(230, 240, 240, 240); stroke.strokeWidth = 3f * u
        canvas.drawLine(w * 0.5f - baseHalf + 7f * u, baseY, vpX - topHalf, horizonY + 1f, stroke)
        canvas.drawLine(w * 0.5f + baseHalf - 7f * u, baseY, vpX + topHalf, horizonY + 1f, stroke)
        // Faixa central tracejada
        val scroll = (engine.distance % 9f) / 9f
        fill.color = Color.rgb(244, 209, 58)
        for (i in 0 until 9) {
            val t = ((i + scroll) % 9f) / 9f; val tt = t * t
            val y = horizonY + (baseY - horizonY) * tt
            val cx = vpX + (w * 0.5f - vpX) * tt
            val dl = max(2f, 20f * tt * u); val dw = max(1.2f, 5f * tt * u)
            canvas.drawRect(cx - dw / 2, y, cx + dw / 2, y + dl, fill)
        }
        // Carros
        val cars = arrayOf(
            floatArrayOf(0.35f, -0.28f, 0f), floatArrayOf(0.62f, 0.30f, 1f), floatArrayOf(0.5f, 0.05f, 2f)
        )
        val carColors = intArrayOf(Color.rgb(224, 49, 49), Color.rgb(28, 126, 214), Color.rgb(245, 159, 0))
        for (car in cars) {
            val tt = car[0] * car[0]
            val y = horizonY + (baseY - horizonY) * tt
            val half = topHalf + (baseHalf - topHalf) * tt
            val cx = vpX + (w * 0.5f - vpX) * tt + car[1] * half
            val cw = max(3f, half * 0.5f); val ch = cw * 0.7f
            fill.color = carColors[car[2].toInt()]
            canvas.drawRoundRect(cx - cw / 2, y - ch / 2, cx + cw / 2, y + ch / 2, min(3f, cw * 0.2f), min(3f, cw * 0.2f), fill)
            fill.color = Color.argb(178, 180, 220, 255)
            canvas.drawRect(cx - cw / 2 + cw * 0.15f, y - ch / 2 + ch * 0.15f, cx - cw / 2 + cw * 0.15f + cw * 0.7f, y - ch / 2 + ch * 0.15f + ch * 0.3f, fill)
        }
    }

    private fun drawTrees(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, engine: GameEngine) {
        for (tr in trees) {
            val tt = tr.t * tr.t
            val y = horizonY + (h - horizonY) * tt
            val roadHalf = 6f + (w * 0.30f - 6f) * tt
            val cx = vpX + (w * 0.5f - vpX) * tt + tr.side * (roadHalf + tr.off * w * (0.4f + tt))
            val sc = (0.25f + tt * 1.1f) * u
            fill.color = Color.rgb(107, 74, 43)
            canvas.drawRect(cx - 3f * sc, y - 16f * sc, cx + 3f * sc, y, fill)
            fill.color = Color.rgb(47, 125, 58)
            canvas.drawCircle(cx, y - 22f * sc, 14f * sc, fill)
            canvas.drawCircle(cx - 10f * sc, y - 16f * sc, 10f * sc, fill)
            canvas.drawCircle(cx + 10f * sc, y - 16f * sc, 10f * sc, fill)
            fill.color = Color.rgb(58, 145, 71)
            canvas.drawCircle(cx - 4f * sc, y - 26f * sc, 8f * sc, fill)
        }
    }

    private fun altToScreenY(alt: Float, h: Int): Float {
        val g = h * 0.9f; val s = h * 0.14f
        val t = (alt / GameEngine.CEILING).coerceIn(0f, 1f)
        return g + (s - g) * t
    }

    private fun drawRings(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, u: Float, engine: GameEngine) {
        val halfW = w * 0.42f
        for (r in engine.rings.sortedByDescending { it.z }) {
            if (r.z <= -2f) continue
            val scale = 46f / (46f + r.z.coerceAtLeast(-1f))
            val relLane = r.laneX - engine.lateral
            val baseX = w * 0.5f + relLane * halfW
            val baseY = altToScreenY(r.altitude, h)
            val f = (1f - scale).coerceIn(0f, 1f)
            val sx = baseX + (vpX - baseX) * f
            val sy = baseY + (horizonY - baseY) * f
            val radius = 120f * scale * u
            stroke.strokeWidth = max(2f, 16f * scale * u)
            stroke.color = when {
                r.scored -> Color.rgb(120, 230, 140)
                r.missed -> Color.argb(150, 200, 90, 90)
                else -> Color.rgb(255, 204, 40)
            }
            canvas.drawOval(sx - radius, sy - radius * 0.72f, sx + radius, sy + radius * 0.72f, stroke)
            if (!r.scored && !r.missed) {
                stroke.color = Color.argb(128, 255, 255, 255)
                stroke.strokeWidth = max(1f, 4f * scale * u)
                canvas.drawOval(sx - radius * 0.86f, sy - radius * 0.62f, sx + radius * 0.86f, sy + radius * 0.62f, stroke)
            }
        }
    }

    /** Desenha o pássaro em torno da origem atual do canvas (vista de trás/cima). */
    private fun drawBirdShape(canvas: Canvas, bird: BirdType, flapPhase: Float, spread: Float, scale: Float) {
        canvas.save()
        canvas.scale(scale, scale)
        val flap = sin(flapPhase * 2f * Math.PI).toFloat()
        val up = flap * 30f
        val sp = 1f + spread * 0.4f

        // Cauda
        fill.color = bird.edge
        val tail = Path().apply {
            moveTo(0f, 10f); lineTo(-18f, 52f); lineTo(0f, 42f); lineTo(18f, 52f); close()
        }
        canvas.drawPath(tail, fill)

        // Asas
        for (s in intArrayOf(-1, 1)) {
            fill.shader = LinearGradient(0f, 0f, s * 135f * sp, 0f,
                intArrayOf(bird.wing, bird.edge), null, Shader.TileMode.CLAMP)
            val wing = Path().apply {
                moveTo(s * 8f, -6f)
                quadTo(s * 72f * sp, -24f - up, s * 140f * sp, -4f - up * 0.5f)
                quadTo(s * 120f * sp, 16f - up * 0.3f, s * 70f * sp, 22f)
                quadTo(s * 40f, 16f, s * 8f, 10f)
                close()
            }
            canvas.drawPath(wing, fill)
            fill.shader = null
            stroke.color = bird.edge; stroke.strokeWidth = 2f
            for (i in 1..4) {
                val t = i / 5f
                canvas.drawLine(s * (24f + 60f * t * sp), 6f - up * 0.4f * t, s * (140f * sp * t + 18f), -4f - up * 0.5f, stroke)
            }
        }

        // Corpo
        fill.shader = LinearGradient(0f, -24f, 0f, 34f, intArrayOf(bird.wing, bird.body), null, Shader.TileMode.CLAMP)
        canvas.drawOval(-20f, 6f - 32f, 20f, 6f + 32f, fill)
        fill.shader = null
        // Cabeça
        fill.color = bird.head
        canvas.drawCircle(0f, -28f, 15f, fill)
        // Bico
        fill.color = bird.beak
        val beak = Path().apply { moveTo(-6f, -40f); lineTo(0f, -56f); lineTo(6f, -40f); close() }
        canvas.drawPath(beak, fill)
        // Olhos
        fill.color = Color.rgb(22, 22, 22)
        canvas.drawCircle(-7f, -30f, 2.6f, fill)
        canvas.drawCircle(7f, -30f, 2.6f, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(-6.2f, -30.8f, 0.9f, fill)
        canvas.drawCircle(7.8f, -30.8f, 0.9f, fill)

        canvas.restore()
    }

    private fun drawBird(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        val bx = w * 0.5f
        val by = altToScreenY(engine.altitude, h)
        val groundY = h * 0.9f
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f)
        val ss = 1f - altT * 0.7f
        fill.color = Color.argb(56, 0, 0, 0)
        canvas.drawOval(bx - 72f * ss * u, groundY - 15f * ss * u, bx + 72f * ss * u, groundY + 15f * ss * u, fill)

        canvas.save()
        canvas.translate(bx, by)
        canvas.rotate(engine.bank * 22f)
        drawBirdShape(canvas, engine.bird, engine.flapPhase, input.spread, u)
        canvas.restore()
    }

    private fun drawHud(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        txt(canvas, "Pontos: ${engine.score}", 28f * u, 66f * u, 26f * u, true, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "Recorde: ${engine.best}", 28f * u, 110f * u, 18f * u, false, Color.WHITE, Paint.Align.LEFT)
        txt(canvas, "Distância: ${engine.distance.toInt()} m", 28f * u, 148f * u, 18f * u, false, Color.WHITE, Paint.Align.LEFT)
        if (engine.combo >= 2) {
            txt(canvas, "Combo x${engine.combo}", 28f * u, 186f * u, 18f * u, false, Color.rgb(255, 220, 90), Paint.Align.LEFT)
        }
        txt(canvas, engine.bird.name, 28f * u, h - 34f * u, 20f * u, true, Color.WHITE, Paint.Align.LEFT)

        // Barra de altitude
        val barX = w - 44f * u; val barTop = 66f * u; val barBot = h * 0.58f
        fill.color = Color.argb(90, 0, 0, 0)
        canvas.drawRoundRect(barX - 4f, barTop - 4f, barX + 22f, barBot + 4f, 12f, 12f, fill)
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f)
        val fillTop = barBot - (barBot - barTop) * altT
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
            GameState.PLAYING -> if (!input.detected) {
                txt(canvas, "Reposicione-se na câmera", w / 2f, h * 0.14f, 22f * u, true, Color.rgb(255, 180, 120), Paint.Align.CENTER)
            }
        }
    }

    private fun drawSelectScreen(canvas: Canvas, w: Int, h: Int, u: Float, engine: GameEngine, input: FlightInput) {
        dim(canvas, w, h, 128)
        txt(canvas, "VooApp", w / 2f, h * 0.16f, 58f * u, true, Color.WHITE, Paint.Align.CENTER)
        txt(canvas, "Simulador de voo com o corpo", w / 2f, h * 0.205f, 22f * u, false, Color.argb(217, 255, 255, 255), Paint.Align.CENTER)
        txt(canvas, "Escolha seu pássaro", w / 2f, h * 0.30f, 26f * u, true, Color.rgb(255, 224, 138), Paint.Align.CENTER)

        canvas.save(); canvas.translate(w / 2f, h * 0.44f)
        drawBirdShape(canvas, engine.bird, 0.12f, 0.5f, 1.5f * u)
        canvas.restore()

        val n = BirdType.ALL.size
        val cw = w.toFloat() / n
        val cardY = h * 0.60f
        val cardH = h * 0.14f
        for (i in 0 until n) {
            val cx = cw * i + cw / 2
            val on = i == engine.selectedIndex
            fill.color = if (on) Color.argb(56, 255, 224, 138) else Color.argb(20, 255, 255, 255)
            canvas.drawRoundRect(cx - cw * 0.42f, cardY, cx + cw * 0.42f, cardY + cardH, 14f, 14f, fill)
            if (on) {
                stroke.color = Color.rgb(255, 224, 138); stroke.strokeWidth = 3f * u
                canvas.drawRoundRect(cx - cw * 0.42f, cardY, cx + cw * 0.42f, cardY + cardH, 14f, 14f, stroke)
            }
            canvas.save(); canvas.translate(cx, cardY + cardH * 0.40f)
            drawBirdShape(canvas, BirdType.ALL[i], 0.1f, 0.4f, 0.42f * u)
            canvas.restore()
            txt(canvas, BirdType.ALL[i].name, cx, cardY + cardH * 0.88f, 17f * u, on, if (on) Color.rgb(255, 224, 138) else Color.WHITE, Paint.Align.CENTER)
        }

        txt(canvas, "Toque para escolher", w / 2f, h * 0.80f, 20f * u, false, Color.argb(230, 255, 255, 255), Paint.Align.CENTER)
        val hint = if (input.detected) "Abra e levante os braços para decolar" else "Fique visível para a câmera"
        txt(canvas, hint, w / 2f, h * 0.835f, 22f * u, true, if (input.detected) Color.WHITE else Color.rgb(255, 180, 120), Paint.Align.CENTER)
    }

    private fun dim(canvas: Canvas, w: Int, h: Int, alpha: Int) {
        fill.color = Color.argb(alpha, 6, 20, 38)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
    }

    private fun txt(canvas: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int, align: Paint.Align) {
        textPaint.textSize = size; textPaint.isFakeBoldText = bold; textPaint.textAlign = align
        shadowText.textSize = size; shadowText.isFakeBoldText = bold; shadowText.textAlign = align
        canvas.drawText(s, x + 2f, y + 2f, shadowText)
        textPaint.color = color
        canvas.drawText(s, x, y, textPaint)
    }

    private fun rand(a: Float, b: Float) = a + Math.random().toFloat() * (b - a)
    private fun min(a: Float, b: Float) = if (a < b) a else b
}
