package com.vooapp.birdflight.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.vooapp.birdflight.input.FlightInput
import kotlin.math.sin

/**
 * Desenha o estado do [GameEngine] num [Canvas], criando a sensação de
 * mundo aberto com projeção pseudo-3D (ponto de fuga no horizonte).
 */
class GameRenderer {

    private val skyPaint = Paint()
    private val groundPaint = Paint()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val birdBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 55) }
    private val birdWing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 70, 95) }
    private val birdBeak = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 179, 0) }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        isFakeBoldText = true
    }
    private val hudSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
    }
    private val hudShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
    }
    private val centerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val centerSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private var lastW = -1
    private var lastH = -1

    // Nuvens decorativas (world x em -1..1, z profundidade).
    private val clouds = Array(6) { floatArrayOf(rand(-1f, 1f), rand(10f, 90f), rand(0.6f, 1.4f)) }

    fun render(canvas: Canvas, engine: GameEngine, input: FlightInput) {
        val w = canvas.width
        val h = canvas.height
        if (w != lastW || h != lastH) {
            buildGradients(w, h)
            lastW = w; lastH = h
        }

        val horizonY = h * 0.5f
        val vpX = w * 0.5f - engine.lateral * w * 0.16f

        drawSky(canvas, w, horizonY)
        drawClouds(canvas, w, horizonY, engine)
        drawGround(canvas, w, h, horizonY, vpX, engine)
        drawRings(canvas, w, h, horizonY, vpX, engine)
        drawBird(canvas, w, h, engine, input)
        drawHud(canvas, w, h, engine, input)
        drawMessages(canvas, w, h, engine, input)
    }

    private fun buildGradients(w: Int, h: Int) {
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.5f,
            intArrayOf(Color.rgb(30, 90, 160), Color.rgb(150, 205, 235)),
            null, Shader.TileMode.CLAMP
        )
        groundPaint.shader = LinearGradient(
            0f, h * 0.5f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(96, 168, 96), Color.rgb(54, 110, 60)),
            null, Shader.TileMode.CLAMP
        )
    }

    private fun drawSky(canvas: Canvas, w: Int, horizonY: Float) {
        canvas.drawRect(0f, 0f, w.toFloat(), horizonY, skyPaint)
    }

    private fun drawGround(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, engine: GameEngine) {
        canvas.drawRect(0f, horizonY, w.toFloat(), h.toFloat(), groundPaint)

        // Linhas convergindo ao ponto de fuga (raios).
        val spacing = w / 8f
        var x = -w.toFloat()
        while (x < w * 2f) {
            canvas.drawLine(x, h.toFloat(), vpX, horizonY, gridPaint)
            x += spacing
        }
        // Linhas horizontais que "vêm" em direção ao jogador (perspectiva).
        val scroll = (engine.distance % 10f) / 10f
        for (i in 1..8) {
            val t = (i - scroll) / 8f
            if (t <= 0f) continue
            val y = horizonY + (h - horizonY) * (t * t)
            gridPaint.alpha = (90 * (1f - t) + 20).toInt().coerceIn(15, 110)
            canvas.drawLine(0f, y, w.toFloat(), y, gridPaint)
        }
        gridPaint.alpha = 70
    }

    private fun drawClouds(canvas: Canvas, w: Int, horizonY: Float, engine: GameEngine) {
        for (c in clouds) {
            c[1] -= engine.forwardSpeed * 0.0025f
            if (c[1] < 6f) {
                c[0] = rand(-1f, 1f); c[1] = rand(70f, 95f); c[2] = rand(0.6f, 1.4f)
            }
            val scale = 40f / (40f + c[1])
            val cx = w * 0.5f + (c[0] - engine.lateral) * w * 0.9f * scale
            val cy = horizonY * (0.15f + 0.55f * (1f - scale)) + 20f
            val r = 60f * scale * c[2]
            cloudPaint.alpha = (200 * scale).toInt().coerceIn(40, 220)
            canvas.drawCircle(cx, cy, r, cloudPaint)
            canvas.drawCircle(cx - r * 0.8f, cy + r * 0.2f, r * 0.7f, cloudPaint)
            canvas.drawCircle(cx + r * 0.9f, cy + r * 0.15f, r * 0.75f, cloudPaint)
        }
    }

    private fun altToScreenY(alt: Float, h: Int): Float {
        val groundY = h * 0.9f
        val skyY = h * 0.14f
        val t = (alt / GameEngine.CEILING).coerceIn(0f, 1f)
        return groundY + (skyY - groundY) * t
    }

    private fun drawRings(canvas: Canvas, w: Int, h: Int, horizonY: Float, vpX: Float, engine: GameEngine) {
        val sorted = engine.rings.sortedByDescending { it.z }
        val halfW = w * 0.42f
        for (r in sorted) {
            if (r.z <= -2f) continue
            val scale = 46f / (46f + r.z.coerceAtLeast(-1f))
            val relLane = r.laneX - engine.lateral
            val baseX = w * 0.5f + relLane * halfW
            val baseY = altToScreenY(r.altitude, h)
            val f = (1f - scale).coerceIn(0f, 1f)
            val screenX = baseX + (vpX - baseX) * f
            val screenY = baseY + (horizonY - baseY) * f
            val radius = 120f * scale
            ringPaint.strokeWidth = (18f * scale).coerceAtLeast(2f)
            ringPaint.color = when {
                r.scored -> Color.rgb(120, 230, 140)
                r.missed -> Color.argb(150, 200, 90, 90)
                else -> Color.rgb(255, 200, 40)
            }
            // Elipse (achatada) para dar a impressão de estar "de frente".
            canvas.drawOval(
                RectF(screenX - radius, screenY - radius * 0.72f, screenX + radius, screenY + radius * 0.72f),
                ringPaint
            )
        }
    }

    private fun drawBird(canvas: Canvas, w: Int, h: Int, engine: GameEngine, input: FlightInput) {
        val bx = w * 0.5f
        val by = altToScreenY(engine.altitude, h)

        // Sombra no chão, projetada mais para longe quanto mais alto o pássaro.
        val groundY = h * 0.9f
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f)
        val shadowScale = (1f - altT * 0.7f)
        canvas.drawOval(
            RectF(bx - 70f * shadowScale, groundY - 14f * shadowScale,
                bx + 70f * shadowScale, groundY + 14f * shadowScale),
            shadowPaint
        )

        canvas.save()
        canvas.translate(bx, by)
        canvas.rotate(engine.bank * 22f)

        // Envergadura das asas com o bater (flapPhase).
        val flap = sin(engine.flapPhase * 2f * Math.PI).toFloat() // -1..1
        val wingTipY = -flap * 34f
        val spread = 1f + input.spread * 0.35f

        // Asa esquerda
        val leftWing = Path().apply {
            moveTo(-6f, 0f)
            quadTo(-70f * spread, -10f + wingTipY, -110f * spread, 8f + wingTipY * 0.6f)
            quadTo(-60f * spread, 18f, -6f, 12f)
            close()
        }
        // Asa direita
        val rightWing = Path().apply {
            moveTo(6f, 0f)
            quadTo(70f * spread, -10f + wingTipY, 110f * spread, 8f + wingTipY * 0.6f)
            quadTo(60f * spread, 18f, 6f, 12f)
            close()
        }
        canvas.drawPath(leftWing, birdWing)
        canvas.drawPath(rightWing, birdWing)

        // Corpo
        canvas.drawOval(RectF(-18f, -16f, 22f, 20f), birdBody)
        // Cabeça
        canvas.drawCircle(26f, -6f, 13f, birdBody)
        // Bico
        val beak = Path().apply {
            moveTo(38f, -8f); lineTo(52f, -4f); lineTo(38f, 1f); close()
        }
        canvas.drawPath(beak, birdBeak)
        // Olho
        canvas.drawCircle(30f, -9f, 2.6f, birdBeak)

        canvas.restore()
    }

    private fun drawHud(canvas: Canvas, w: Int, h: Int, engine: GameEngine, input: FlightInput) {
        // Placar
        text(canvas, "Pontos: ${engine.score}", 32f, 70f, hudPaint)
        text(canvas, "Recorde: ${engine.best}", 32f, 118f, hudSmall)
        text(canvas, "Distância: ${engine.distance.toInt()} m", 32f, 158f, hudSmall)
        if (engine.combo >= 2) {
            hudSmall.color = Color.rgb(255, 220, 90)
            text(canvas, "Combo x${engine.combo}", 32f, 198f, hudSmall)
            hudSmall.color = Color.WHITE
        }

        // Barra de altitude (direita).
        val barX = w - 46f
        val barTop = 70f
        val barBot = h * 0.6f
        hudShadow.color = Color.argb(90, 0, 0, 0)
        canvas.drawRoundRect(barX - 4f, barTop - 4f, barX + 22f, barBot + 4f, 12f, 12f, hudShadow)
        val altT = (engine.altitude / GameEngine.CEILING).coerceIn(0f, 1f)
        val fillTop = barBot - (barBot - barTop) * altT
        val altPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (engine.altitude < 12f) Color.rgb(230, 90, 90) else Color.rgb(120, 200, 255)
        }
        canvas.drawRoundRect(barX, fillTop, barX + 18f, barBot, 9f, 9f, altPaint)

        // Indicador de detecção da câmera.
        val dotColor = if (input.detected) Color.rgb(120, 230, 140) else Color.rgb(230, 120, 120)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
        canvas.drawCircle(w - 34f, h - 40f, 12f, dotPaint)
        hudSmall.textAlign = Paint.Align.RIGHT
        text(canvas, if (input.detected) "rastreando" else "sem pose", w - 54f, h - 30f, hudSmall)
        hudSmall.textAlign = Paint.Align.LEFT
    }

    private fun drawMessages(canvas: Canvas, w: Int, h: Int, engine: GameEngine, input: FlightInput) {
        when (engine.state) {
            GameState.READY -> {
                dimOverlay(canvas, w, h)
                canvas.drawText("VooApp", w / 2f, h * 0.34f, centerText)
                canvas.drawText("Abra os braços como asas", w / 2f, h * 0.44f, centerSub)
                canvas.drawText("e levante-os para decolar", w / 2f, h * 0.50f, centerSub)
                if (!input.detected) {
                    centerSub.color = Color.rgb(255, 180, 120)
                    canvas.drawText("Fique visível para a câmera", w / 2f, h * 0.60f, centerSub)
                    centerSub.color = Color.argb(230, 255, 255, 255)
                }
            }
            GameState.CRASHED -> {
                dimOverlay(canvas, w, h)
                canvas.drawText("Você caiu!", w / 2f, h * 0.36f, centerText)
                canvas.drawText("Pontos: ${engine.score}   Recorde: ${engine.best}", w / 2f, h * 0.46f, centerSub)
                canvas.drawText("Levante os braços para voar de novo", w / 2f, h * 0.56f, centerSub)
            }
            GameState.PLAYING -> {
                if (!input.detected) {
                    centerSub.color = Color.rgb(255, 180, 120)
                    canvas.drawText("Reposicione-se na câmera", w / 2f, h * 0.16f, centerSub)
                    centerSub.color = Color.argb(230, 255, 255, 255)
                }
            }
        }
    }

    private fun dimOverlay(canvas: Canvas, w: Int, h: Int) {
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            color = Color.argb(110, 0, 0, 0)
        })
    }

    private fun text(canvas: Canvas, s: String, x: Float, y: Float, paint: Paint) {
        val prev = paint.color
        hudShadow.textSize = paint.textSize
        hudShadow.textAlign = paint.textAlign
        hudShadow.isFakeBoldText = paint.isFakeBoldText
        canvas.drawText(s, x + 2f, y + 2f, hudShadow)
        paint.color = prev
        canvas.drawText(s, x, y, paint)
    }

    private fun rand(a: Float, b: Float) = a + Math.random().toFloat() * (b - a)
}
