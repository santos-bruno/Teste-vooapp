package com.vooapp.birdflight.game

import com.vooapp.birdflight.input.FlightInput
import kotlin.math.abs
import kotlin.random.Random

enum class GameState { READY, PLAYING, CRASHED }
enum class PowerKind { TURBO, SHIELD }

/** Argola flutuante que o jogador atravessa para pontuar. */
class Ring(var z: Float, val laneX: Float, val altitude: Float) {
    var scored = false
    var missed = false
}

/** Obstáculo (balão) que faz o pássaro cair, a menos que tenha escudo/turbo. */
class Obstacle(var z: Float, val laneX: Float, val altitude: Float) {
    var hit = false
    var popped = false // atravessado com turbo/escudo
}

/** Item coletável que concede turbo ou escudo. */
class PowerUp(var z: Float, val laneX: Float, val altitude: Float, val kind: PowerKind) {
    var taken = false
    var missed = false
}

/**
 * Estado e física do voo. Independente de renderização: só atualiza números.
 * Coordenadas: lateral em -1..1; altitude em 0 (chão) .. [CEILING].
 */
class GameEngine {

    var state = GameState.READY; private set

    // Seleção de pássaro (na tela inicial)
    var selectedIndex = 0; private set
    val bird: BirdType get() = BirdType.ALL[selectedIndex]

    fun selectBird(index: Int) {
        if (state == GameState.READY || state == GameState.CRASHED) {
            selectedIndex = index.coerceIn(0, BirdType.ALL.size - 1)
        }
    }

    // Pássaro
    var lateral = 0f; private set
    private var lateralVel = 0f
    var altitude = START_ALTITUDE; private set
    private var vSpeed = 0f
    var bank = 0f; private set
    var flapPhase = 0f; private set
    var forwardSpeed = BASE_SPEED; private set

    // Progresso
    var distance = 0f; private set
    var score = 0; private set
    var combo = 0; private set
    var best = 0; private set

    // Power-ups
    var hasShield = false; private set
    var turboTime = 0f; private set
    val turboMax get() = TURBO_DURATION
    private var invuln = 0f

    // Ambiente
    var timeOfDay = 0.12f; private set

    // Progresso de decolagem (0..1): quanto tempo a pose de voo foi mantida.
    var takeoffProgress = 0f; private set

    val rings = ArrayList<Ring>()
    val obstacles = ArrayList<Obstacle>()
    val powerups = ArrayList<PowerUp>()
    private var spawnCooldown = 0f
    private var obstacleCooldown = 0f
    private var powerupCooldown = 0f
    private var crashTimer = 0f
    private var rng = Random(System.nanoTime())

    private fun difficulty() = (distance / 2500f).coerceIn(0f, 1f)

    /** Define o recorde carregado do armazenamento persistente. */
    fun setBest(value: Int) { if (value > best) best = value }

    fun startIfReady() {
        if (state != GameState.PLAYING) { reset(); state = GameState.PLAYING }
    }

    private fun reset() {
        lateral = 0f; lateralVel = 0f
        altitude = START_ALTITUDE; vSpeed = 0f
        bank = 0f; flapPhase = 0f
        forwardSpeed = BASE_SPEED
        distance = 0f; score = 0; combo = 0
        hasShield = false; turboTime = 0f; invuln = 0f
        rings.clear(); obstacles.clear(); powerups.clear()
        spawnCooldown = 0.5f; obstacleCooldown = 3f; powerupCooldown = 8f
        crashTimer = 0f; takeoffProgress = 0f
    }

    /** A pose de voo está correta: as duas asas bem rastreadas e abertas/erguidas. */
    private fun takeoffPoseOk(input: FlightInput) =
        input.confident && (input.spread > TAKEOFF_SPREAD || input.lift > TAKEOFF_LIFT)

    /** Acumula/decai o progresso de decolagem; retorna true quando pronto. */
    private fun advanceTakeoff(dt: Float, input: FlightInput): Boolean {
        if (takeoffPoseOk(input)) {
            takeoffProgress += dt / TAKEOFF_HOLD
            if (takeoffProgress >= 1f) { takeoffProgress = 0f; return true }
        } else {
            takeoffProgress = (takeoffProgress - dt / (TAKEOFF_HOLD * 0.5f)).coerceAtLeast(0f)
        }
        return false
    }

    fun update(dt: Float, input: FlightInput) {
        val clampedDt = dt.coerceIn(0f, 0.05f)
        timeOfDay = (timeOfDay + clampedDt / DAY_LENGTH) % 1f
        when (state) {
            GameState.READY -> {
                animateFlap(clampedDt, input)
                // Só decola quando as asas são identificadas corretamente e a
                // pose de voo é mantida por um instante (evita começar quebrado).
                if (advanceTakeoff(clampedDt, input)) startIfReady()
            }
            GameState.PLAYING -> updatePlaying(clampedDt, input)
            GameState.CRASHED -> {
                crashTimer -= clampedDt
                animateFlap(clampedDt, input)
                if (crashTimer <= 0f) {
                    if (advanceTakeoff(clampedDt, input)) { reset(); state = GameState.PLAYING }
                } else takeoffProgress = 0f
            }
        }
    }

    private fun updatePlaying(dt: Float, input: FlightInput) {
        animateFlap(dt, input)
        if (turboTime > 0f) turboTime -= dt
        if (invuln > 0f) invuln -= dt
        val invincible = turboTime > 0f || invuln > 0f

        // Vertical
        val glide = 1f - 0.55f * input.spread
        val gravity = GRAVITY * glide
        val liftAccel = (input.lift * LIFT_ACCEL + input.flap * FLAP_ACCEL) * bird.liftMul
        vSpeed += (liftAccel - gravity) * dt
        vSpeed *= VERTICAL_DAMPING
        vSpeed = vSpeed.coerceIn(-MAX_VSPEED, MAX_VSPEED)
        altitude += vSpeed * dt
        if (altitude > CEILING) { altitude = CEILING; if (vSpeed > 0) vSpeed = 0f }

        // Lateral
        lateralVel += input.roll * ROLL_ACCEL * dt
        lateralVel *= LATERAL_DAMPING
        lateral += lateralVel * dt
        if (lateral > 1f) { lateral = 1f; lateralVel = 0f }
        if (lateral < -1f) { lateral = -1f; lateralVel = 0f }
        bank = (input.roll * 0.7f + lateralVel * 3f).coerceIn(-1f, 1f)

        // Avanço (dificuldade + turbo)
        val diff = difficulty()
        val turboMul = if (turboTime > 0f) TURBO_MUL else 1f
        forwardSpeed = (BASE_SPEED + input.spread * SPREAD_SPEED_BONUS) * bird.speedMul * (1f + 0.6f * diff) * turboMul

        distance += forwardSpeed * dt

        // Chão
        if (altitude <= 0f) {
            altitude = 0f
            if (vSpeed < -CRASH_SPEED && !invincible) {
                if (hasShield) { hasShield = false; invuln = INVULN_TIME; vSpeed = MAX_VSPEED * 0.4f }
                else { crash(); return }
            } else {
                vSpeed = if (invincible && vSpeed < 0f) MAX_VSPEED * 0.3f else 0f
            }
        }

        updateSpawns(dt)
        if (updateObstacles(dt)) return
        updatePowerups(dt)
        updateRings(dt)
    }

    private fun updateSpawns(dt: Float) {
        spawnCooldown -= dt
        if (spawnCooldown <= 0f) {
            spawnRing()
            spawnCooldown = (rng.nextFloat() * 0.8f + SPAWN_INTERVAL) * (1f - 0.4f * difficulty())
        }
        obstacleCooldown -= dt
        if (obstacleCooldown <= 0f) {
            spawnObstacle()
            obstacleCooldown = (rng.nextFloat() * 1.5f + OBSTACLE_INTERVAL) * (1f - 0.5f * difficulty())
        }
        powerupCooldown -= dt
        if (powerupCooldown <= 0f) {
            spawnPowerup()
            powerupCooldown = rng.nextFloat() * 4f + POWERUP_INTERVAL
        }
    }

    private fun updateRings(dt: Float) {
        val it = rings.iterator()
        while (it.hasNext()) {
            val r = it.next()
            r.z -= forwardSpeed * dt
            if (!r.scored && !r.missed && r.z <= 0f) {
                if (abs(r.laneX - lateral) < RING_LATERAL_TOL && abs(r.altitude - altitude) < RING_ALT_TOL) {
                    r.scored = true; combo += 1; score += 1 + combo / 5
                    if (score > best) best = score
                } else { r.missed = true; combo = 0 }
            }
            if (r.z < -6f) it.remove()
        }
    }

    /** @return true se houve batida fatal (interrompe o frame). */
    private fun updateObstacles(dt: Float): Boolean {
        val invincible = turboTime > 0f || invuln > 0f
        val it = obstacles.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.z -= forwardSpeed * dt
            if (!o.hit && o.z <= 0f) {
                if (abs(o.laneX - lateral) < OBST_LATERAL_TOL && abs(o.altitude - altitude) < OBST_ALT_TOL) {
                    o.hit = true
                    when {
                        invincible -> o.popped = true
                        hasShield -> { hasShield = false; invuln = INVULN_TIME; o.popped = true }
                        else -> { crash(); return true }
                    }
                }
            }
            if (o.z < -6f) it.remove()
        }
        return false
    }

    private fun updatePowerups(dt: Float) {
        val it = powerups.iterator()
        while (it.hasNext()) {
            val pu = it.next()
            pu.z -= forwardSpeed * dt
            if (!pu.taken && !pu.missed && pu.z <= 0f) {
                if (abs(pu.laneX - lateral) < POW_LATERAL_TOL && abs(pu.altitude - altitude) < POW_ALT_TOL) {
                    pu.taken = true
                    when (pu.kind) {
                        PowerKind.TURBO -> turboTime = TURBO_DURATION
                        PowerKind.SHIELD -> hasShield = true
                    }
                } else pu.missed = true
            }
            if (pu.z < -6f) it.remove()
        }
    }

    private fun spawnRing() {
        val lane = (rng.nextFloat() * 2f - 1f) * 0.85f
        val alt = (altitude + (rng.nextFloat() * 2f - 1f) * 22f).coerceIn(8f, CEILING - 8f)
        rings.add(Ring(SPAWN_DISTANCE, lane, alt))
    }

    private fun spawnObstacle() {
        val lane = (rng.nextFloat() * 2f - 1f) * 0.85f
        val alt = (altitude + (rng.nextFloat() * 2f - 1f) * 26f).coerceIn(10f, CEILING - 6f)
        obstacles.add(Obstacle(SPAWN_DISTANCE, lane, alt))
    }

    private fun spawnPowerup() {
        val lane = (rng.nextFloat() * 2f - 1f) * 0.8f
        val alt = (altitude + (rng.nextFloat() * 2f - 1f) * 18f).coerceIn(10f, CEILING - 8f)
        val kind = if (rng.nextFloat() < 0.5f) PowerKind.TURBO else PowerKind.SHIELD
        powerups.add(PowerUp(SPAWN_DISTANCE, lane, alt, kind))
    }

    private fun crash() {
        state = GameState.CRASHED
        crashTimer = CRASH_RECOVER_TIME
        combo = 0; vSpeed = 0f; lateralVel = 0f
        turboTime = 0f; hasShield = false
    }

    private fun animateFlap(dt: Float, input: FlightInput) {
        val speed = 4f + input.flap * 10f + (if (state == GameState.PLAYING) 2f else 0f)
        flapPhase = (flapPhase + speed * dt) % 1f
    }

    companion object {
        const val CEILING = 120f
        private const val DAY_LENGTH = 80f
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

        private const val OBSTACLE_INTERVAL = 3.6f
        private const val OBST_LATERAL_TOL = 0.24f
        private const val OBST_ALT_TOL = 11f

        private const val POWERUP_INTERVAL = 12f
        private const val POW_LATERAL_TOL = 0.30f
        private const val POW_ALT_TOL = 14f

        private const val TURBO_DURATION = 4.5f
        private const val TURBO_MUL = 1.8f
        private const val INVULN_TIME = 1.2f

        // Decolagem: quanto a pose precisa ser mantida e os limiares da "pose de asas".
        private const val TAKEOFF_HOLD = 0.7f
        private const val TAKEOFF_SPREAD = 0.35f
        private const val TAKEOFF_LIFT = 0.25f
    }
}
