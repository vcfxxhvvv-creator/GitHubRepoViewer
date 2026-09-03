# 🚀 Nova Drift — Space Runner

لعبة أندرويد خفيفة وسريعة الإيقاع: أنت طيّار سفينة فضائية تخترق حقل كويكبات. راوغ يميناً ويساراً، اجمع كرات الطاقة، واستخدم الدفعة (Boost) لتحطيم الصخور وتجميع النقاط. الهدف: أعلى سكور!

A lightweight, fast-paced Android space runner built 100% with **Kotlin + Jetpack Compose** (custom `Canvas` engine — no game engine dependency). Pilot a ship through a scrolling asteroid field: dodge, catch energy orbs, then **hold the BOOST pad to smash through rocks** for big points. Survive as long as you can and chase the high score.

## Gameplay
- **Steer** — drag your finger anywhere (ship glides toward your finger)
- **Boost** — hold the bottom-right pad while the ENERGY bar is above zero; boosting lets you *destroy* asteroids (+45/70 pts) instead of dying
- **Energy** — regenerates slowly; orbs give a big refill (+26)
- **Scoring** — survival time + orbs + smashed rocks; big rocks split into shards
- **Feel** — parallax starfield, drifting nebulas, engine flame, particle explosions, shockwave rings, screen shake, flashes, haptics, and a persisted high score

## Screens
- Menu (with high score + instructions)
- In-game HUD (score, best, run timer, energy bar, boost pad, pause)
- Pause overlay (resume / main menu)
- Game over (score, new-record badge, TIME / ROCKS / ORBS stats, fly again)

## Build the APK
Option A — GitHub Actions (no local SDK needed):
1. Push this repo to GitHub
2. Open **Actions** → **Build APK** → **Run workflow**
3. Download `NovaDrift-APK` from the run artifacts (or the release)

Option B — locally:
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Project layout
```
app/src/main/java/com/novadrift/runner/
├── MainActivity.kt            # immersive fullscreen activity
├── game/NovaGame.kt           # engine: physics, spawning, collisions, particles, scoring
└── ui/
    ├── NovaDriftApp.kt        # game loop, touch input, screens & HUD
    ├── GameRender.kt          # Canvas renderer (space scene, ship, fx)
    └── theme/                 # Material 3 dark theme
```
