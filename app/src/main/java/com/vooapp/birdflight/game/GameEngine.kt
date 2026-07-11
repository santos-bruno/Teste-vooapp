package com.vooapp.birdflight.game

import com.vooapp.birdflight.input.FlightInput
import kotlin.math.abs
import kotlin.random.Random

enum class GameState { READY, PLAYING, CRASHED }

/** Argola flutuante que o jogador pode atravessar para pontuar. */
class Ring(
    var z: Float,        // distância à frente (unidades de mundo); 0 = no plano do pássaro
    val laneX: Float,    // -1..1 posição lateral
    val altitude: Float, // altura no mundo
) {
    var scored = false
    var missed = false
}

/**
 * Estado e física do voo. Independente de renderização: só atualiza números.
 * Coordenadas:
 *  - lateral em -1..1 (esquerda..direita da tela)
 *  - altitude em 0 (chão) .. [CEILING]
 */
class GameEngine {

    var state = GameState.READY
        private set

    // Pássaro
    var lateral = 0f; private set
    private var lateralVel = 0f
    var altitude = START_ALTITUDE; private set
    private var vSpeed = 0f
    var bank = 0f; private set          // inclinação visual -1..1
    var flapPhase = 0f; private set     // 0..1 animação de bater asas
    var forwardSpeed = BASE_SPEED; private set

    // Progresso
    var distance = 0f; private set
    var score = 0; private set
    var combo = 0; private set
    var best = 0; private set

    val rings = ArrayList<Ring>()
    private var spawnCooldown = 0f
    private var crashTimer = 0f
    private var rng = Random(System.nanoTime())

    fun startIfReady() {
        if (state != GameState.PLAYING) {
            reset()
            state = GameState.PLAYING
        }
    }

    private fun reset() {
        lateral = 0f; lateralVel = 0f
        altitude = START_ALTITUDE; vSpeed = 0f
        bank = 0f; flapPhase = 0f
        forwardSpeed = BASE_SPEED
        distance = 0f; score = 0; combo = 0
        rings.clear()
        spawnCooldown = 0.5f
        crashTimer = 0f
    }

    fun update(dt: Float, input: FlightInput) {
        val clampedDt = dt.coerceIn(0f, 0.05f)
        when (state) {
            GameState.READY -> {
                // Começa quando o jogador levanta os braços (ou bate as asas).
                if (input.detected && (input.lift > 0.2f || input.flap > 0.3f)) {
                    startIfReady()
                }
                animateFlap(clampedDt, input)
            }
            GameState.PLAYING -> updatePlaying(clampedDt, input)
            GameState.CRASHED -> {
                crashTimer -= clampedDt
                animateFlap(clampedDt, input)
                if (crashTimer <= 0f && input.detected && input.lift > 0.1f) {
                    reset()
                    state = GameState.PLAYING
                }
            }
        }
    }

    private fun updatePlaying(dt: Float, input: FlightInput) {
        animateFlap(dt, input)

        // ---- Vertical ----
        val glide = 1f - 0.55f * input.spread
        val gravity = GRAVITY * glide
        val liftAccel = input.lift * LIFT_ACCEL + input.flap * FLAP_ACCEL
        vSpeed += (liftAccel - gravity) * dt
        vSpeed *= VERTICAL_DAMPING
        vSpeed = vSpeed.coerceIn(-MAX_VSPEED, MAX_VSPEED)
        altitude += vSpeed * dt
        if (altitude > CEILING) { altitude = CEILING; if (vSpeed > 0) vSpeed = 0f }

        // ---- Lateral (banking) ----
        lateralVel += input.roll * ROLL_ACCEL * dt
        lateralVel *= LATERAL_DAMPING
        lateral += lateralVel * dt
        if (lateral > 1f) { lateral = 1f; lateralVel = 0f }
        if (lateral < -1f) { lateral = -1f; lateralVel = 0f }
        bank = (input.roll * 0.7f + lateralVel * 3f).coerceIn(-1f, 1f)

        // ---- Avanço ----
        forwardSpeed = BASE_SPEED + input.spread * SPREAD_SPEED_BONUS
        distance += forwardSpeed * dt

        // ---- Chão ----
        if (altitude <= 0f) {
            altitude = 0f
            if (vSpeed < -CRASH_SPEED) {
                crash()
                return
            } else {
                vSpeed = 0f // pouso suave / rasante
            }
        }

        updateRings(dt)
    }

    private fun updateRings(dt: Float) {
        spawnCooldown -= dt
        if (spawnCooldown <= 0f) {
            spawnRing()
            spawnCooldown = rng.nextFloat() * 0.8f + SPAWN_INTERVAL
        }
        val it = rings.iterator()
        while (it.hasNext()) {
            val r = it.next()
            r.z -= forwardSpeed * dt
            if (!r.scored && !r.missed && r.z <= 0f) {
                val hitLateral = abs(r.laneX - lateral) < RING_LATERAL_TOL
                val hitAlt = abs(r.altitude - altitude) < RING_ALT_TOL
                if (hitLateral && hitAlt) {
                    r.scored = true
                    combo += 1
                    score += 1 + combo / 5
                    if (score > best) best = score
                } else {
                    r.missed = true
                    combo = 0
                }
            }
            if (r.z < -6f) it.remove()
        }
    }

    private fun spawnRing() {
        val lane = (rng.nextFloat() * 2f - 1f) * 0.85f
        val alt = (altitude + (rng.nextFloat() * 2f - 1f) * 22f)
            .coerceIn(8f, CEILING - 8f)
        rings.add(Ring(z = SPAWN_DISTANCE, laneX = lane, altitude = alt))
    }

    private fun crash() {
        state = GameState.CRASHED
        crashTimer = CRASH_RECOVER_TIME
        combo = 0
        vSpeed = 0f
        lateralVel = 0f
    }

    private fun animateFlap(dt: Float, input: FlightInput) {
        // Frequência do bater de asas aumenta com o esforço.
        val speed = 4f + input.flap * 10f + (if (state == GameState.PLAYING) 2f else 0f)
        flapPhase = (flapPhase + speed * dt) % 1f
    }

    companion object {
        const val CEILING = 120f
        private const val START_ALTITUDE = 55f

        private const val GRAVITY = 26f
        private const val LIFT_ACCEL = 34f
        private const val FLAP_ACCEL = 90f
        private const val VERTICAL_DAMPING = 0.985f
        private const val MAX_VSPEED = 60f

        private const val ROLL_ACCEL = 6.5f
        private const val LATERAL_DAMPING = 0.92f

        private const val BASE_SPEED = 34f
        private const val SPREAD_SPEED_BONUS = 22f

        private const val CRASH_SPEED = 22f
        private const val CRASH_RECOVER_TIME = 2.2f

        private const val SPAWN_DISTANCE = 90f
        private const val SPAWN_INTERVAL = 1.4f
        private const val RING_LATERAL_TOL = 0.28f
        private const val RING_ALT_TOL = 12f
    }
}
