package com.novadrift.runner.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.novadrift.runner.game.Asteroid
import com.novadrift.runner.game.GamePhase
import com.novadrift.runner.game.NovaGame
import com.novadrift.runner.game.Orb
import com.novadrift.runner.game.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object NovaPalette {
    val DeepSpace = Color(0xFF05060F)
    val Cyan = Color(0xFF3FE0FF)
    val CyanSoft = Color(0xFFBFF9FF)
    val Orange = Color(0xFFFF9F43)
    val White = Color(0xFFFFFFFF)
    val Flame = Color(0xFFFFA24D)
    val Hull = Color(0xFFDCE8FF)
    val Fin = Color(0xFF3BB4DE)
    val Glass = Color(0xFF0B3140)
}

private fun withAlpha(argb: Long, alpha: Float): Color =
    Color(argb).copy(alpha = alpha.coerceIn(0f, 1f))

/** Paints the full star system: backdrop + world + lights. Called every frame. */
fun DrawScope.drawScene(game: NovaGame) {
    if (game.w <= 0f) return
    val w = game.w
    val h = game.h
    val u = game.unit

    // deep space vertical gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF03040C),
                Color(0xFF070A1F),
                Color(0xFF120C2B)
            )
        )
    )

    // drifting nebulas
    for (n in game.nebulas) {
        val pulse = n.alpha * (0.72f + 0.28f * sin(game.time * 0.8f + n.phase))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(withAlpha(n.color, pulse), withAlpha(n.color, 0f)),
                center = Offset(n.cx * w, n.cy * h)
            ),
            radius = n.radius * h,
            center = Offset(n.cx * w, n.cy * h)
        )
    }

    // parallax stars
    for (st in game.stars) {
        val tw = 0.55f + 0.45f * sin(game.time * st.twSpeed + st.twPhase)
        drawCircle(
            color = NovaPalette.White.copy(alpha = st.bright * tw),
            radius = st.size * u,
            center = Offset(st.x, st.y)
        )
    }

    // ===== world (shaken) =====
    translate(game.shakeX, game.shakeY) {
        for (orb in game.orbs) drawOrb(orb, game.time)
        for (rock in game.asteroids) drawAsteroid(rock)
        for (p in game.particles) drawParticle(p)
        for (sh in game.shocks) {
            val t = sh.age / sh.life
            drawCircle(
                color = NovaPalette.Cyan.copy(alpha = (1f - t) * 0.85f),
                radius = sh.radius,
                center = Offset(sh.x, sh.y),
                style = Stroke(width = (6f * (1f - t) + 1.5f) * u)
            )
        }
        if (game.phase != GamePhase.GAME_OVER) {
            drawShip(game, u)
        }
        for (f in game.flashes) {
            val t = f.age / f.life
            val a = (1f - t) * 0.8f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NovaPalette.White.copy(alpha = a), NovaPalette.White.copy(alpha = 0f)),
                    center = Offset(f.x, f.y)
                ),
                radius = f.radius * (0.4f + t),
                center = Offset(f.x, f.y)
            )
        }
    }

    // screen-wide explosion flash
    if (game.screenFlash > 0f) {
        drawRect(color = NovaPalette.White.copy(alpha = game.screenFlash * 0.7f))
    }

    // cinematic vignette
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x00000000L), Color(0x66000000L)),
            center = Offset(w / 2f, h * 0.42f),
            radius = max(w, h) * 0.95f
        )
    )
}

private fun DrawScope.drawAsteroid(rock: Asteroid) {
    val r = rock.r
    if (r <= 0f) return
    val n = rock.offsets.size
    val path = Path()
    for (i in 0 until n) {
        val ang = (i.toFloat() / n) * 2f * PI.toFloat() + rock.rot
        val rad = r * rock.offsets[i]
        val px = rock.x + cos(ang) * rad
        val py = rock.y + sin(ang) * rad
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = Color(rock.color))
    drawPath(path, color = NovaPalette.White.copy(alpha = 0.10f), style = Stroke(width = 1.4f))

    val c1a = rock.rot * 0.6f + 1.2f
    val c2a = rock.rot * 0.6f + 4.3f
    drawCircle(
        color = Color(0x00000000L).copy(alpha = 0.18f),
        radius = r * 0.2f,
        center = Offset(rock.x + cos(c1a) * r * 0.35f, rock.y + sin(c1a) * r * 0.35f)
    )
    drawCircle(
        color = Color(0x00000000L).copy(alpha = 0.14f),
        radius = r * 0.14f,
        center = Offset(rock.x + cos(c2a) * r * 0.5f, rock.y + sin(c2a) * r * 0.5f)
    )
}

private fun DrawScope.drawOrb(orb: Orb, time: Float) {
    val pulse = 1f + 0.22f * sin(time * 4f + orb.phase)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(withAlpha(0xFF6EF6FF, 0.55f), withAlpha(0xFF6EF6FF, 0f)),
            center = Offset(orb.x, orb.y)
        ),
        radius = orb.r * 3.1f * pulse,
        center = Offset(orb.x, orb.y)
    )
    drawCircle(
        color = withAlpha(0xFFBFF9FF, 0.95f),
        radius = orb.r * pulse,
        center = Offset(orb.x, orb.y)
    )
    drawCircle(
        color = NovaPalette.White,
        radius = orb.r * 0.45f,
        center = Offset(orb.x, orb.y)
    )
}

private fun DrawScope.drawParticle(p: Particle) {
    val t = (p.life / p.maxLife).coerceIn(0f, 1f)
    drawCircle(
        color = withAlpha(p.color, t),
        radius = p.size * (0.4f + 0.6f * t),
        center = Offset(p.x, p.y)
    )
}

private fun DrawScope.drawShip(game: NovaGame, u: Float) {
    val boosting = game.boosting
    val x = game.shipX
    val y = game.shipY

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(withAlpha(0xFFFFA24D, if (boosting) 0.5f else 0.22f), withAlpha(0xFFFFA24D, 0f)),
            center = Offset(x, y + 14f * u)
        ),
        radius = if (boosting) 66f * u else 40f * u,
        center = Offset(x, y + 14f * u)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(withAlpha(0xFF3FE0FF, if (boosting) 0.20f else 0.09f), withAlpha(0xFF3FE0FF, 0f)),
            center = Offset(x, y)
        ),
        radius = 54f * u,
        center = Offset(x, y)
    )

    translate(x, y) {
        scale(u, u, pivot = Offset.Zero) {
        rotate(game.bank * 14f, pivot = Offset.Zero) {
            val flick = 0.8f + 0.2f * sin(game.time * 41f)
            val len = game.flameL * flick
            val flame = Path().apply {
                moveTo(-3.2f, 12f)
                lineTo(0f, 18f + len)
                lineTo(3.2f, 12f)
                close()
            }
            drawPath(
                flame,
                color = (if (boosting) NovaPalette.White else NovaPalette.Flame).copy(alpha = 0.95f)
            )
            val flameInner = Path().apply {
                moveTo(-1.6f, 14f)
                lineTo(0f, 17f + len * 0.5f)
                lineTo(1.6f, 14f)
                close()
            }
            drawPath(flameInner, color = NovaPalette.Flame.copy(alpha = if (boosting) 0.9f else 0.75f))

            val hull = Path().apply {
                moveTo(0f, -22f)
                lineTo(9f, -4f)
                lineTo(6.5f, 15f)
                lineTo(0f, 18f)
                lineTo(-6.5f, 15f)
                lineTo(-9f, -4f)
                close()
            }
            drawPath(hull, color = NovaPalette.Hull)

            val finR = Path().apply {
                moveTo(6f, 0f)
                lineTo(17f, 12f)
                lineTo(8f, 11f)
                close()
            }
            val finL = Path().apply {
                moveTo(-6f, 0f)
                lineTo(-17f, 12f)
                lineTo(-8f, 11f)
                close()
            }
            drawPath(finR, color = NovaPalette.Fin)
            drawPath(finL, color = NovaPalette.Fin)

            drawOval(
                color = NovaPalette.Glass,
                topLeft = Offset(-3.6f, -12f),
                size = Size(7.2f, 11f)
            )
            drawOval(
                color = withAlpha(0xFF3FE0FF, 0.9f),
                topLeft = Offset(-2.2f, -11f),
                size = Size(3.2f, 5f)
            )

            val spine = Path().apply {
                moveTo(0f, -22f)
                lineTo(0f, 16f)
            }
            drawPath(
                spine,
                color = NovaPalette.White.copy(alpha = 0.5f),
                style = Stroke(width = 1.4f)
            )
            } // rotate
        } // scale
    } // translate
}
