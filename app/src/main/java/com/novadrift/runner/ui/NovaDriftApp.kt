package com.novadrift.runner.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novadrift.runner.game.GamePhase
import com.novadrift.runner.game.NovaGame
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private const val PREFS_NAME = "nova_drift"
private const val KEY_BEST = "best_score"

@Composable
fun NovaDriftApp() {
    val context = LocalContext.current.applicationContext
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val game = remember { NovaGame() }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    val buzz: (Long, Int) -> Unit = { ms, amp ->
        val v = vibrator
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    var best by remember { mutableIntStateOf(prefs.getInt(KEY_BEST, 0)) }
    var newRecord by remember { mutableStateOf(false) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var energyUi by remember { mutableFloatStateOf(0f) }
    var timeUi by remember { mutableIntStateOf(0) }
    var phaseUi by remember { mutableStateOf(GamePhase.MENU) }
    var frameTick by remember { mutableLongStateOf(0L) }

    // One continuous frame loop: physics + fx + haptics + HUD state sync.
    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last == 0L) last = now
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now

            game.update(dt)

            if (game.eventDeath) {
                game.eventDeath = false
                buzz(260, 255)
            }
            if (game.eventSlam) {
                game.eventSlam = false
                buzz(20, 130)
            }
            if (game.eventCollect) {
                game.eventCollect = false
                buzz(10, 50)
            }

            if (game.score != scoreUi) scoreUi = game.score
            val e = game.energy
            if (abs(e - energyUi) > 0.4f) energyUi = e
            val t = game.timeSurvived.toInt()
            if (t != timeUi) timeUi = t
            if (phaseUi != game.phase) phaseUi = game.phase
            frameTick++
        }
    }

    // Persist the high score exactly once when a run ends.
    LaunchedEffect(phaseUi) {
        if (phaseUi == GamePhase.GAME_OVER) {
            val sc = game.score
            if (sc > best) {
                best = sc
                newRecord = true
                prefs.edit().putInt(KEY_BEST, sc).apply()
            } else {
                newRecord = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaPalette.DeepSpace)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(game) {
                    val zoneW = 150.dp.toPx()
                    val zoneH = 150.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val id = down.id
                        fun apply(pos: Offset) {
                            game.targetX = pos.x
                            game.boostHeld =
                                pos.x > game.w - zoneW && pos.y > game.h - zoneH
                        }
                        apply(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release ||
                                event.type == PointerEventType.Cancel
                            ) {
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == id } ?: break
                            if (change.positionChanged()) apply(change.position)
                        }
                        game.targetX = null
                        game.boostHeld = false
                    }
                }
        ) {
            // Keep the sim in sync with the real canvas size (pixels).
            if (game.w != size.width) game.w = size.width
            if (game.h != size.height) game.h = size.height

            frameTick // state read -> this draw pass repaints every frame
            drawScene(game)
        }

        // ===== HUD while flying =====
        if (phaseUi == GamePhase.PLAYING) {
            HUD(
                score = scoreUi,
                best = best,
                seconds = timeUi,
                energy = energyUi / 100f,
                onPause = { game.pause() }
            )
        }

        // ===== Screens =====
        when (phaseUi) {
            GamePhase.MENU -> MenuScreen(best = best, onStart = { game.startRun() })
            GamePhase.PAUSED -> PausedScreen(
                onResume = { game.resume() },
                onMenu = { game.toMenu() }
            )
            GamePhase.GAME_OVER -> GameOverScreen(
                score = scoreUi,
                best = best,
                seconds = timeUi,
                rocks = game.rocksDestroyed,
                orbs = game.orbsCollected,
                newRecord = newRecord,
                onRetry = { game.startRun() },
                onMenu = { game.toMenu() }
            )
            GamePhase.PLAYING -> Unit
        }
    }
}

// --- HUD ---

@Composable
private fun HUD(
    score: Int,
    best: Int,
    seconds: Int,
    energy: Float,
    onPause: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // score block, top center
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = fmt(score),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = NovaPalette.White,
                style = TextStyle(shadow = Shadow(Color(0xFF3FE0FF), blurRadius = 26f))
            )
            Text(
                text = "BEST ${fmt(best)}",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = NovaPalette.White.copy(alpha = 0.55f),
                fontWeight = FontWeight.SemiBold
            )
        }

        // clock chip, top left
        Text(
            text = "T+ ${mmss(seconds)}",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 30.dp, start = 22.dp),
            fontSize = 13.sp,
            letterSpacing = 1.5.sp,
            color = NovaPalette.CyanSoft.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium
        )

        // pause chip, top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 22.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(NovaPalette.White.copy(alpha = 0.10f))
                .clickable(onClick = onPause),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(NovaPalette.White, RoundedCornerShape(2.dp))
                )
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(NovaPalette.White, RoundedCornerShape(2.dp))
                )
            }
        }

        // energy bar, bottom left
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 22.dp, bottom = 22.dp)
        ) {
            Text(
                text = "ENERGY",
                fontSize = 10.sp,
                letterSpacing = 2.4.sp,
                color = NovaPalette.CyanSoft.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(NovaPalette.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(energy.coerceIn(0f, 1f))
                        .height(9.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1FA7C8), Color(0xFF6EF6FF))
                            ),
                            RoundedCornerShape(5.dp)
                        )
                )
            }
        }

        // boost pad hint, bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(width = 122.dp, height = 96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(NovaPalette.Cyan.copy(alpha = if (energy > 0.01f) 0.13f else 0.05f))
                .border(
                    width = 1.5.dp,
                    color = NovaPalette.Cyan.copy(alpha = if (energy > 0.01f) 0.5f else 0.18f),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BOOST",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = if (energy > 0.01f) NovaPalette.CyanSoft
                    else NovaPalette.White.copy(alpha = 0.35f)
                )
                Text(
                    text = "hold",
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = NovaPalette.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// --- Menu ---

@Composable
private fun MenuScreen(best: Int, onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x4D03040CL))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "— SECTOR 7 · ASTEROID BELT —",
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                color = NovaPalette.Orange.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "NOVA DRIFT",
                modifier = Modifier.padding(bottom = 2.dp),
                style = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp,
                    brush = Brush.horizontalGradient(
                        listOf(NovaPalette.Cyan, NovaPalette.White, NovaPalette.CyanSoft)
                    ),
                    shadow = Shadow(Color(0xFF0B5A70), blurRadius = 30f)
                )
            )
            Text(
                text = "SPACE RUNNER",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 9.sp,
                color = NovaPalette.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Dodge the rocks · Catch the energy · Boost to smash",
                fontSize = 13.sp,
                color = NovaPalette.White.copy(alpha = 0.55f)
            )

            Spacer(Modifier.height(46.dp))

            PillButton(text = "START RUN", onClick = onStart)

            Spacer(Modifier.height(20.dp))

            if (best > 0) {
                Text(
                    text = "★ HIGH SCORE  ${fmt(best)}",
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    color = NovaPalette.Orange.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "←  drag anywhere to steer  →",
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = NovaPalette.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "hold the bottom-right pad to boost — smashing rocks scores",
                fontSize = 12.sp,
                color = NovaPalette.White.copy(alpha = 0.38f)
            )
        }
    }
}

// --- Pause ---

@Composable
private fun PausedScreen(onResume: () -> Unit, onMenu: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB0000000L)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PAUSED",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                color = NovaPalette.White
            )
            Spacer(Modifier.height(34.dp))
            PillButton(text = "RESUME", onClick = onResume)
            Spacer(Modifier.height(12.dp))
            TextButtonLabel(text = "MAIN MENU", onClick = onMenu)
        }
    }
}

// --- Game over ---

@Composable
private fun GameOverScreen(
    score: Int,
    best: Int,
    seconds: Int,
    rocks: Int,
    orbs: Int,
    newRecord: Boolean,
    onRetry: () -> Unit,
    onMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x7305040EL))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SHIP DOWN",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp,
                color = NovaPalette.Orange.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = fmt(score),
                fontSize = 66.sp,
                fontWeight = FontWeight.Black,
                color = NovaPalette.White,
                style = TextStyle(shadow = Shadow(NovaPalette.Orange, blurRadius = 30f))
            )
            Text(
                text = "BEST ${fmt(best)}",
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = NovaPalette.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(12.dp))

            if (newRecord) {
                Text(
                    text = "★ NEW RECORD ★",
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFFE082), NovaPalette.Orange, Color(0xFFFF7043))
                        )
                    )
                )
                Spacer(Modifier.height(20.dp))
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(NovaPalette.White.copy(alpha = 0.07f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                StatCell(label = "TIME", value = mmss(seconds))
                StatCell(label = "ROCKS", value = fmt(rocks))
                StatCell(label = "ORBS", value = fmt(orbs))
            }

            Spacer(Modifier.height(38.dp))
            PillButton(text = "FLY AGAIN", onClick = onRetry)
            Spacer(Modifier.height(12.dp))
            TextButtonLabel(text = "MAIN MENU", onClick = onMenu)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = NovaPalette.CyanSoft
        )
        Text(
            text = label,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = NovaPalette.White.copy(alpha = 0.45f),
            fontWeight = FontWeight.Bold
        )
    }
}

// --- shared controls ---

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF3FE0FF), Color(0xFF1F7ACB))),
                RoundedCornerShape(34.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 44.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.5.sp,
            color = Color(0xFF02131A)
        )
    }
}

@Composable
private fun TextButtonLabel(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.5.sp,
        color = NovaPalette.White.copy(alpha = 0.6f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

// --- formatting helpers ---

private fun fmt(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

private fun mmss(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
