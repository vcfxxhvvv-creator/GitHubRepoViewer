package com.novadrift.runner.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Top level game state for Nova Drift - Space Runner. */
enum class GamePhase { MENU, PLAYING, PAUSED, GAME_OVER }

/**
 * An asteroid. Vertices are pre-rolled once from a seed so every rock keeps its
 * own jagged silhouette while spinning.
 */
class Asteroid(seed: Int) {
    private val rnd = Random(seed)

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var rot = rnd.nextFloat() * 2f * PI.toFloat()
    var rotSpeed = (rnd.nextFloat() - 0.5f) * 3.4f
    var r = 22f
    var big = false

    /** Radial offsets (0.72..1.0) for each vertex, clockwise starting at top. */
    val offsets: FloatArray = FloatArray(9)
    val color: Long

    init {
        for (i in offsets.indices) {
            offsets[i] = 0.72f + rnd.nextFloat() * 0.28f
        }
        color = when (rnd.nextInt(4)) {
            0 -> 0xFF9AA3B5
            1 -> 0xFFB09072
            2 -> 0xFF8E97AD
            else -> 0xFF7C8796
        }
    }
}

class Orb {
    var x = 0f
    var y = 0f
    var baseX = 0f
    var vx = 0f
    var vy = 0f
    var r = 9f
    var phase = 0f
    var alive = true
}

class Particle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var size = 3f
    var color: Long = 0xFFFFFFFF
    var gravity = 0f
}

class Shockwave {
    var x = 0f
    var y = 0f
    var radius = 0f
    var maxRadius = 60f
    var age = 0f
    var life = 0.45f
}

class Flash {
    var x = 0f
    var y = 0f
    var radius = 60f
    var age = 0f
    var life = 0.22f
}

class Star {
    var x = 0f
    var y = 0f
    var size = 1.5f
    var speed = 30f
    var twPhase = 0f
    var twSpeed = 2f
    var bright = 0.8f
}

class Nebula {
    var cx = 0f
    var cy = 0f
    // radius as a fraction of screen height
    var radius = 0.2f
    var color: Long = 0xFF7C5CFF
    var alpha = 0.10f
    var phase = 0f
    var drift = 0f
}

/** Simulates a star system drifting toward a dust belt — the enemy field. */
class NovaGame {

    // --- world ---
    var w = 0f
    var h = 0f
    private var s = 1f
    val unit: Float get() = s

    var time = 0f
    var phase: GamePhase = GamePhase.MENU

    private val rand = Random(1337)

    // --- ship ---
    var shipX = 0f
    var shipVx = 0f
    var shipY = 0f
    var targetX: Float? = null
    var boostHeld = false
    var energy = 0f
    var bank = 0f
    private var bob = 0f
    var flameL = 18f
    private var exhaustAcc = 0f

    // --- run stats ---
    private var scoreF = 0f
    var timeSurvived = 0f
    var rocksDestroyed = 0
    var orbsCollected = 0
    var nearMisses = 0

    val score: Int get() = scoreF.toInt()
    val boosting: Boolean
        get() = phase == GamePhase.PLAYING && boostHeld && energy > 0f

    // --- entities ---
    val asteroids = ArrayList<Asteroid>()
    val orbs = ArrayList<Orb>()
    val particles = ArrayList<Particle>()
    val shocks = ArrayList<Shockwave>()
    val flashes = ArrayList<Flash>()
    val stars = ArrayList<Star>()
    val nebulas = ArrayList<Nebula>()

    // --- fx state ---
    var shake = 0f
    var shakeX = 0f
    var shakeY = 0f
    var screenFlash = 0f

    // --- one-frame events (consumed by the UI for haptics) ---
    var eventDeath = false
    var eventSlam = false
    var eventCollect = false

    private var spawnTimer = 1.2f
    private var orbTimer = 2.0f
    private var ambientTimer = 0.6f

    init {
        buildBackdrop()
    }

    private fun buildBackdrop() {
        val sr = Random(77)
        for (i in 0 until 110) {
            val layer = sr.nextFloat()
            stars.add(
                Star().apply {
                    x = sr.nextFloat() * 2000f
                    y = sr.nextFloat() * 2000f
                    twPhase = sr.nextFloat() * 6.28f
                    twSpeed = 1.5f + sr.nextFloat() * 3f
                    when {
                        layer < 0.55f -> {
                            size = 0.8f + sr.nextFloat() * 0.9f
                            speed = 34f + sr.nextFloat() * 26f
                            bright = 0.45f + sr.nextFloat() * 0.3f
                        }
                        layer < 0.85f -> {
                            size = 1.6f + sr.nextFloat() * 0.8f
                            speed = 78f + sr.nextFloat() * 40f
                            bright = 0.7f + sr.nextFloat() * 0.25f
                        }
                        else -> {
                            size = 2.2f + sr.nextFloat() * 1.2f
                            speed = 150f + sr.nextFloat() * 70f
                            bright = 0.85f + sr.nextFloat() * 0.15f
                        }
                    }
                }
            )
        }
        val nebRnd = Random(9)
        val palette = longArrayOf(0xFF3E3A8F, 0xFF1E4E6F, 0xFF6B2E86, 0xFF14475C)
        for (i in 0 until 3) {
            nebulas.add(
                Nebula().apply {
                    cx = nebRnd.nextFloat()
                    cy = nebRnd.nextFloat()
                    radius = 0.14f + nebRnd.nextFloat() * 0.16f
                    color = palette[nebRnd.nextInt(palette.size)]
                    phase = nebRnd.nextFloat() * 6.28f
                    drift = (nebRnd.nextFloat() - 0.5f) * 0.012f
                }
            )
        }
    }

    // ================= lifecycle =================

    fun startRun() {
        phase = GamePhase.PLAYING
        asteroids.clear()
        orbs.clear()
        particles.clear()
        shocks.clear()
        flashes.clear()
        scoreF = 0f
        timeSurvived = 0f
        rocksDestroyed = 0
        orbsCollected = 0
        nearMisses = 0
        energy = 35f
        boostHeld = false
        targetX = null
        shipVx = 0f
        bank = 0f
        shake = 0f
        screenFlash = 0f
        spawnTimer = 1.35f
        orbTimer = 2.4f
        shipX = w / 2f
    }

    fun toMenu() {
        phase = GamePhase.MENU
        asteroids.clear()
        orbs.clear()
        particles.clear()
        shocks.clear()
        flashes.clear()
        boostHeld = false
        targetX = null
        shipVx = 0f
        shake = 0f
    }

    fun pause() {
        if (phase == GamePhase.PLAYING) phase = GamePhase.PAUSED
    }

    fun resume() {
        if (phase == GamePhase.PAUSED) phase = GamePhase.PLAYING
    }

    // ================= update =================

    fun update(dt: Float) {
        if (w <= 0f || h <= 0f) return
        time += dt
        // px-per-dp-ish unit derived from screen width (canvas coords are pixels)
        s = (w / 400f).coerceIn(0.6f, 3.2f)

        updateBackdrop(dt)

        when (phase) {
            GamePhase.PLAYING -> updateGameplay(dt)
            GamePhase.MENU, GamePhase.GAME_OVER -> updateAmbient(dt)
            GamePhase.PAUSED -> Unit
        }

        updateParticles(dt)

        // screen shake decay + jitter
        shake = max(0f, shake - shake * 5.5f * dt)
        val sh = shake
        shakeX = if (sh > 0.05f) (rand.nextFloat() - 0.5f) * 2f * sh else 0f
        shakeY = if (sh > 0.05f) (rand.nextFloat() - 0.5f) * 2f * sh else 0f
        screenFlash = max(0f, screenFlash - screenFlash * 9f * dt)
    }

    private fun updateBackdrop(dt: Float) {
        val z = h / 760f
        for (st in stars) {
            st.y += st.speed * z * dt
            if (st.y > h + 4f) {
                st.y = -4f
                st.x = rand.nextFloat() * w
            }
        }
        for (n in nebulas) {
            n.cx += n.drift * dt
            if (n.cx < -0.25f) n.cx = 1.25f
            if (n.cx > 1.25f) n.cx = -0.25f
        }
        if (phase == GamePhase.PLAYING) {
            // background speeds up slightly with difficulty for depth
            for (st in stars) st.speed *= 1f + 0.006f * dt
        }
    }

    private fun updateAmbient(dt: Float) {
        ambientTimer -= dt
        if (ambientTimer <= 0f && asteroids.size < 6) {
            ambientTimer = 0.5f + rand.nextFloat() * 0.9f
            spawnRock(decorative = true)
        }
        moveRocks(dt)
        shipY = h * 0.84f
        // gentle menu idle sway
        val target = w / 2f + sin(time * 0.55f) * w * 0.12f
        shipX += (target - shipX) * min(1f, 1.2f * dt)
        shipX = shipX.coerceIn(26f * s, w - 26f * s)
        bank = cos(time * 0.55f) * 0.16f
        shipVx = 0f
    }

    private fun updateGameplay(dt: Float) {
        timeSurvived += dt
        scoreF += dt * (if (boosting) 26f else 11f)

        val difficulty = min(1f, timeSurvived / 110f)

        // spawn rocks on a shrinking interval
        spawnTimer -= dt * (1f + difficulty * 0.55f)
        if (spawnTimer <= 0f) {
            spawnRock(decorative = false)
            spawnTimer = lerp(1.35f, 0.5f, difficulty) * (0.75f + rand.nextFloat() * 0.5f)
        }

        orbTimer -= dt
        if (orbTimer <= 0f) {
            spawnOrb()
            orbTimer = lerp(3.2f, 1.7f, difficulty) * (0.8f + rand.nextFloat() * 0.4f)
        }

        // energy management
        if (boostHeld && energy > 0f) {
            energy = max(0f, energy - 34f * dt)
        } else {
            energy = min(100f, energy + 4.5f * dt)
        }

        updateShip(dt)
        moveRocks(dt)
        moveOrbs(dt)

        // collisions
        val shipR = 13f * s
        val shipCx = shipX
        val shipCy = shipY
        val i = asteroids.iterator()
        while (i.hasNext()) {
            val rock = i.next()
            val d = dist(shipCx, shipCy, rock.x, rock.y)
            val hitDist = shipR + rock.r * 0.92f
            if (d < hitDist) {
                if (boosting) {
                    // smash it
                    explodeRock(rock, atShip = false)
                    i.remove()
                } else {
                    explodeRock(rock, atShip = true)
                    i.remove()
                    die()
                    return
                }
            }
        }

        // ship crash against rocks already handled above; now draw orbs pickup
        val j = orbs.iterator()
        while (j.hasNext()) {
            val orb = j.next()
            val d = dist(shipCx, shipCy, orb.x, orb.y)
            if (d < shipR + orb.r + 8f * s) {
                collectOrb(orb)
                j.remove()
            }
        }
    }

    private fun updateShip(dt: Float) {
        shipY = h * 0.84f
        bob = sin(time * 2.3f) * 2.2f * s
        val margin = 26f * s

        val tx = targetX
        if (tx != null) {
            val desired = tx.coerceIn(margin, w - margin)
            val diff = desired - shipX
            shipVx += diff * 18f * dt
            shipVx = (shipVx * exp(-9f * dt)).coerceIn(-700f * s, 700f * s)
            shipX += shipVx * dt
            bank = (shipVx / (700f * s)).coerceIn(-0.5f, 0.5f) * 1.15f
        } else {
            shipVx *= exp(-8f * dt)
            shipX += shipVx * dt
            bank *= exp(-8f * dt)
        }
        shipX = shipX.coerceIn(margin, w - margin)

        // engine flame flicker + exhaust
        val intense = boosting
        flameL += ((if (intense) 52f else 22f) + rand.nextFloat() * 14f - flameL) * min(1f, 16f * dt)
        exhaustAcc += (if (intense) 150f else 48f) * dt
        while (exhaustAcc >= 1f) {
            exhaustAcc -= 1f
            spawnExhaust(intense)
        }
    }

    private fun moveRocks(dt: Float) {
        val i = asteroids.iterator()
        while (i.hasNext()) {
            val rock = i.next()
            rock.x += rock.vx * dt
            rock.y += rock.vy * dt
            rock.rot += rock.rotSpeed * dt
            if (rock.y - rock.r > h + 60f || rock.x < -rock.r * 3f || rock.x > w + rock.r * 3f) {
                i.remove()
            }
        }
    }

    private fun moveOrbs(dt: Float) {
        val i = orbs.iterator()
        while (i.hasNext()) {
            val orb = i.next()
            orb.phase += 2.2f * dt
            orb.x = orb.baseX + sin(orb.phase) * 10f * s

            // gentle magnet pull when the ship is close
            val d = dist(shipX, shipY, orb.x, orb.y)
            if (d < 150f * s && d > 1f) {
                val pull = (1f - d / (150f * s)) * 260f * s
                orb.vx += ((shipX - orb.x) / d) * pull * dt
                orb.vy += ((shipY - orb.y) / d) * pull * dt
            }
            orb.x += orb.vx * dt
            orb.y += orb.vy * dt
            if (orb.y > h + 30f) i.remove()
        }
    }

    private fun spawnRock(decorative: Boolean) {
        val roll = rand.nextFloat()
        val big = !decorative && roll > 1f - 0.16f * min(1f, timeSurvived / 25f)
        val r = if (big) 34f + rand.nextFloat() * 13f else 15f + rand.nextFloat() * 17f
        val rock = Asteroid(rand.nextInt())
        rock.r = r * s
        rock.big = big
        val m = 8f * s
        rock.x = m + rand.nextFloat() * (w - 2f * m)
        rock.y = -rock.r - 10f
        val diff = min(1f, timeSurvived / 90f)
        val speedMul = if (decorative) 0.45f else 0.72f + diff * 0.65f
        // smaller rocks fall faster
        val sizeFactor = (1f - r / 48f).coerceIn(0.55f, 1f)
        rock.vy = (h * 0.16f + rand.nextFloat() * h * 0.13f) * speedMul * sizeFactor
        rock.vx = (rand.nextFloat() - 0.5f) * h * 0.03f
        rock.rotSpeed *= 1f + diff
        asteroids.add(rock)
    }

    private fun spawnOrb() {
        val orb = Orb()
        orb.r = 8.5f * s
        val m = 40f * s
        orb.baseX = m + rand.nextFloat() * (w - 2f * m)
        orb.x = orb.baseX
        orb.y = -20f
        orb.vy = h * (0.06f + rand.nextFloat() * 0.04f)
        orb.phase = rand.nextFloat() * 6.28f
        orbs.add(orb)
    }

    private fun spawnExhaust(intense: Boolean) {
        val tailY = shipY + 16f * s + bob
        val p = Particle()
        p.x = shipX + (rand.nextFloat() - 0.5f) * 5f * s
        p.y = tailY
        p.vx = (rand.nextFloat() - 0.5f) * 46f * s
        p.vy = 60f * s + rand.nextFloat() * (if (intense) 180f else 90f) * s
        p.size = (if (intense) 3.6f else 2.2f) * s + rand.nextFloat() * 1.6f * s
        p.maxLife = if (intense) 0.4f else 0.55f
        p.life = p.maxLife
        p.color = if (intense && rand.nextFloat() < 0.5f) 0xFFFFFFFF else 0xFFFFA24D
        particles.add(p)
    }

    private fun explodeRock(rock: Asteroid, atShip: Boolean) {
        rocksDestroyed++
        scoreF += if (rock.big) 70f else 45f
        shake = (shake + 5f + rock.r * 0.22f).coerceAtMost(24f)
        eventSlam = true
        val cx = if (atShip) shipX else rock.x
        val cy = if (atShip) shipY else rock.y

        if (!atShip) {
            screenFlash = max(screenFlash, 0.10f + rock.r / 600f)
        }
        val n = if (rock.big) 42 else 28
        val sparkN = n / 2
        for (i in 0 until n) {
            val p = Particle()
            p.x = cx
            p.y = cy
            val ang = rand.nextFloat() * 2f * PI.toFloat()
            val sp = (60f + rand.nextFloat() * (if (rock.big) 320f else 240f)) * s
            p.vx = cos(ang) * sp
            p.vy = sin(ang) * sp
            p.maxLife = 0.35f + rand.nextFloat() * 0.5f
            p.life = p.maxLife
            p.size = (1.5f + rand.nextFloat() * 3.4f) * s
            p.gravity = 60f
            p.color = when (rand.nextInt(4)) {
                0 -> rock.color
                1 -> 0xFFFFD9A0
                2 -> 0xFFFF7A3C
                else -> 0xFFFFFFFF
            }
            particles.add(p)
        }
        for (i in 0 until sparkN) {
            val p = Particle()
            p.x = cx
            p.y = cy
            val ang = rand.nextFloat() * 2f * PI.toFloat()
            val sp = (220f + rand.nextFloat() * 420f) * s
            p.vx = cos(ang) * sp
            p.vy = sin(ang) * sp
            p.maxLife = 0.18f + rand.nextFloat() * 0.2f
            p.life = p.maxLife
            p.size = 1.4f * s
            p.color = 0xFFFFFFFF
            particles.add(p)
        }
        addFlash(cx, cy, if (rock.big) 70f else 40f, 0.9f)
        addShock(cx, cy, if (rock.big) 120f else 70f)

        // big rocks split into shards
        if (rock.big && !atShip) {
            for (k in 0 until 2) {
                val shard = Asteroid(rand.nextInt())
                shard.r = rock.r * (0.42f + rand.nextFloat() * 0.12f)
                shard.x = rock.x + (rand.nextFloat() - 0.5f) * rock.r
                shard.y = rock.y + (rand.nextFloat() - 0.5f) * rock.r
                shard.big = false
                val ang = rand.nextFloat() * 6.28f
                shard.vx = cos(ang) * 60f * s + rock.vx * 0.4f
                shard.vy = sin(ang) * 40f * s + rock.vy * 0.6f
                shard.rotSpeed = (rand.nextFloat() - 0.5f) * 6f
                asteroids.add(shard)
            }
        }
    }

    private fun die() {
        phase = GamePhase.GAME_OVER
        eventDeath = true
        boostHeld = false
        targetX = null
        shake = 30f
        screenFlash = 0.55f
        val n = 90
        for (i in 0 until n) {
            val p = Particle()
            p.x = shipX
            p.y = shipY
            val ang = rand.nextFloat() * 2f * PI.toFloat()
            val sp = (80f + rand.nextFloat() * 420f) * s
            p.vx = cos(ang) * sp
            p.vy = sin(ang) * sp
            p.maxLife = 0.4f + rand.nextFloat() * 0.8f
            p.life = p.maxLife
            p.size = (1.6f + rand.nextFloat() * 3.6f) * s
            p.gravity = 40f
            p.color = when (rand.nextInt(5)) {
                0 -> 0xFFEAF2FF
                1 -> 0xFFFFB454
                2 -> 0xFFFF7A3C
                3 -> 0xFF7CC7FF
                else -> 0xFFFFFFFF
            }
            particles.add(p)
        }
        addFlash(shipX, shipY, 90f, 1f)
        addShock(shipX, shipY, 170f)
    }

    private fun collectOrb(orb: Orb) {
        orbsCollected++
        scoreF += 15f
        energy = min(100f, energy + 26f)
        eventCollect = true
        val n = 10
        for (i in 0 until n) {
            val p = Particle()
            p.x = orb.x
            p.y = orb.y
            val ang = rand.nextFloat() * 2f * PI.toFloat()
            val sp = (50f + rand.nextFloat() * 130f) * s
            p.vx = cos(ang) * sp
            p.vy = sin(ang) * sp
            p.maxLife = 0.3f + rand.nextFloat() * 0.25f
            p.life = p.maxLife
            p.size = (1.4f + rand.nextFloat() * 2f) * s
            p.color = if (rand.nextBoolean()) 0xFF6EF6FF else 0xFFBFF9FF
            particles.add(p)
        }
        addShock(orb.x, orb.y, 30f)
    }

    private fun addFlash(x: Float, y: Float, radius: Float, strength: Float) {
        val f = Flash()
        f.x = x
        f.y = y
        f.radius = radius * s
        f.life = 0.22f * strength
        flashes.add(f)
    }

    private fun addShock(x: Float, y: Float, maxRadius: Float) {
        val sh = Shockwave()
        sh.x = x
        sh.y = y
        sh.maxRadius = maxRadius * s
        shocks.add(sh)
    }

    private fun updateParticles(dt: Float) {
        val i = particles.iterator()
        while (i.hasNext()) {
            val p = i.next()
            p.life -= dt
            if (p.life <= 0f) {
                i.remove()
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            // drift up slightly for flame so it trails behind (below) the ship
            p.vx *= exp(-1.4f * dt)
        }
        val si = shocks.iterator()
        while (si.hasNext()) {
            val sh = si.next()
            sh.age += dt
            if (sh.age >= sh.life) {
                si.remove()
                continue
            }
            sh.radius = sh.maxRadius * (1f - exp(-9f * sh.age))
        }
        val fi = flashes.iterator()
        while (fi.hasNext()) {
            val f = fi.next()
            f.age += dt
            if (f.age >= f.life) fi.remove()
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
