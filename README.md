# DrumTuner

An offline, native Android drum-tuning app (Kotlin + Jetpack Compose). It listens to a tap near each lug, measures the **f11 overtone** to sub-0.2 Hz, and walks you around the head in a true star pattern until every lug matches.

* 100 % offline. The only permission is `RECORD_AUDIO`. No `INTERNET`, no accounts.
* No third-party audio libraries. The DSP is pure Kotlin: Hann window, 8192-point radix-2 FFT, parabolic peak interpolation, RMS decay envelope.
* The screen stays awake while tuning.

## Get the APK (no PC needed)

Every push to `main`/`master` builds the app in GitHub Actions:

1. Open the repo on GitHub, then tap **Actions** (in the mobile browser, use the ☰ / "…" menu if the tab is hidden).
2. Tap the latest **Build DrumTuner APK** run with a green ✓.
3. Scroll to **Artifacts** and tap **DrumTuner-debug-apk**. It downloads a `.zip`.
4. Open the zip and tap `app-debug.apk`. Allow "Install unknown apps" for your browser or file manager when Android asks.

To rebuild without pushing, go to **Actions → Build DrumTuner APK → Run workflow**.

## Features

| Tab | What it does |
|---|---|
| **Tuner** | Pick a drum (Snare, Tom 1, Tom 2, Floor Tom, Kick), choose Batter or Resonant, and set the lug count (4–12). The lug radar shows each lug's ±Hz. The centre readout shows Hz, delta, TIGHTEN ↑ / IN TUNE ✓ / LOOSEN ↓, note + cents, sustain, and hit dots. The tuner auto-advances along the star pattern. Tap any lug to re-measure it. Also here: Reset Head, a Dead-Head warning, and the trigger threshold slider with a live meter. |
| **Kit & Key** | Shell sizes, 1-ply/2-ply heads, Punchy (reso +12 %) or Max Sustain, and band key. From these it calculates tom notes spaced by M3/P4 inside each shell's safe range, plus batter/reso lug targets. Shows a sympathetic-buzz warning with −6/+6 Hz nudges. |
| **Soundcheck** | Snare Wire Tension Meter (Rattly → Crisp Sweet Spot → Choked) and a 60-second line check that compares each drum's pitch and sustain with the saved reference. |
| **Presets** | Multiple kit profiles saved on the device. Export/import them as `.json` through the Android file picker. |

### Tuning modes
* **A · Relative:** Lug 1 sets the 0.0 Hz baseline, and every other lug is compared with it.
* **B · Kit & Key Target:** every lug is compared with the calculated target for that drum and head.

## How the measurement works

1. **Capture:** `AudioRecord` at 44.1 kHz mono PCM16, using `UNPROCESSED` when the device reports support and `VOICE_RECOGNITION` otherwise (both bypass AGC and noise suppression). USB-C interfaces and wired/piezo inputs are preferred automatically when plugged in.
2. **Transient gate:** a hit crossing the threshold triggers a capture. The first 25 ms (1,102 samples) of stick impact are discarded, then 8,192 samples (≈185.8 ms) of resonance are captured. After that comes a 400 ms cooldown. A peak-hold envelope ensures a ringing drum can't retrigger itself.
3. **Pitch:** Hann window → FFT → strongest peak in the drum's f11 band (Kick 45–160, Floor 80–260, Rack 110–420, Snare batter 150–500, Snare reso 250–650 Hz). Once a baseline or target exists, the band narrows to ±30 % around it. Parabolic interpolation on log-magnitude refines the peak (worst case < 0.09 Hz on synthetic decaying tones).
4. **Smart 2-to-3 hit filter:** if two hits agree within ±0.5 Hz, their average locks. Otherwise a third hit is requested and the median of the three locks.
5. **Sustain:** the time for the 10 ms RMS envelope to fall 20 dB. If the envelope is interrupted or reaches the noise floor first, the time is extrapolated from a linear fit.
6. **Wire sizzle:** energy at 3–8 kHz ÷ energy at 150–450 Hz.

**Acoustic model (approximation):** an ideal membrane has f11/f01 ≈ 1.593. A two-headed drum's fundamental follows the mean of both heads, so batter = 2L/(1+r) and reso = r·batter. Here L is the mean lug target and r is the style ratio. 2-ply heads target about 3 % lower.

## Project layout

```
app/src/main/java/com/drumtuner/app/
  dsp/        Fft, SpectrumAnalyzer (Hann + peak), TransientGate, DecayTracker, SnareWire, PitchMath
  tuning/     DrumModel (bands), HitMedianFilter, StarPattern, DrumAcoustics (targets, buzz)
  session/    TunerSession: all app state logic in pure Kotlin (unit-tested)
  data/       KitProfile, KitJson + MiniJson (backup format), KitRepository (SharedPreferences)
  audio/      AudioTunerEngine (AudioRecord thread), EngineEvent
  ui/         Compose screens, LugRadar canvas, stage theme
app/src/test/ JUnit 4: FFT vs naive DFT, peak accuracy, gate timing/cooldown, decay,
              2-of-3 median, star patterns, acoustics, session flow, JSON round-trip
```

## Build stack

Gradle 8.7 (installed by the workflow, no wrapper jar), AGP 8.5.2, Kotlin 2.0.0 + Compose Compiler plugin, JDK 17, compileSdk/targetSdk 34, minSdk 26, Compose BOM 2024.06.00 (Material 3).

Local build: `gradle testDebugUnitTest assembleDebug`
