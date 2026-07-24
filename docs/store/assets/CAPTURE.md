# Screenshot capture plan — Pixel 10, demo mode (device-gated)

**Status:** ready to run; **awaits the Pixel 10 over USB.** As of 2026-07-24 `adb devices` shows no
device (P5-Evidence §1/§5). No screenshot is fabricated — when the phone is connected, this is a
mechanical run producing the five real captures the listing needs.

**Why demo mode is the right source:** the app self-seeds `DemoFixture` (seq 0, `demoMode = true`) on
first launch — every screen renders labeled sample data with no engine, no pairing, no account. That
is exactly what a Play reviewer sees, so the screenshots match the reviewed experience. **The
"Demo data — not a live engine" banner appearing in the shots is fine and honest** — it's noted in
`Play-Listing.md` §6 and the reviewer notes. Do **not** try to hide or crop it.

## Verified Play screenshot specs (2026-07-24, answer/9866151 — re-verify on account day)

- Phone screenshots: **min 2, max 8**; **JPEG or 24-bit PNG (no alpha)**; each side **320–3840px**,
  max side ≤ **2×** min side. High-visibility placement wants **≥1080px**, portrait **9:16 (min
  1080×1920)**. The Pixel 10 panel is comfortably within these bounds (a raw `screencap` is native
  resolution, portrait, no alpha) — capture native and do not upscale.

## Procedure (run when `adb devices` lists the Pixel 10)

```bash
# 0. env (same as the build ritual)
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
ADB="$ANDROID_HOME/platform-tools/adb.exe"

# 1. build + install the debug APK (demo mode self-seeds; no pairing needed)
cd C:/Users/bkirk/Documents/careerseeker-android-p5
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew --no-daemon :app:assembleDebug
"$ADB" devices                    # confirm the Pixel 10 is listed
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk

# 2. launch
"$ADB" shell am start -n app.careerseeker.dashboard/.MainActivity

# 3. capture each screen. Navigate with the bottom nav (Home/Applications/Jobs/Evidence);
#    for Application detail, tap a row on Applications first. Then, per screen:
OUT=docs/store/assets
"$ADB" exec-out screencap -p > "$OUT/01-home.png"          # Home/Live (metric grid + demo banner)
"$ADB" exec-out screencap -p > "$OUT/02-applications.png"  # Applications list + state filters
"$ADB" exec-out screencap -p > "$OUT/03-detail.png"        # Application detail (3 read-only docs)
"$ADB" exec-out screencap -p > "$OUT/04-jobs.png"          # Jobs + repost/injection badges
"$ADB" exec-out screencap -p > "$OUT/05-evidence.png"      # Evidence trail + audit badge
```

> On Windows/Git Bash, `screencap -p` piped through `>` is byte-safe (no CRLF translation) because
> the redirect is binary. If a viewer reports a corrupt PNG, re-pull with
> `"$ADB" shell screencap -p /sdcard/s.png && "$ADB" pull /sdcard/s.png` instead.

## After capture

1. Confirm each is portrait, ≥1080px wide, **no alpha channel** (a raw `screencap` PNG has no alpha;
   if any tool re-encodes with alpha, flatten it — Play rejects alpha on screenshots).
2. Listing order (Play + `/dashboard/` page): **01-home → 02-applications → 03-detail → 04-jobs →
   05-evidence** (`Play-Listing.md` §6).
3. Record device model, build SHA, and `adb` output in `docs/P5-Evidence.md` §5 (turn this from
   "device-gated" to "ran it and saw it").
4. Optionally add short caption frames later; the plain screens satisfy the minimum now.

## Accessibility retest happens in the same device session

While the Pixel is connected, run the TalkBack sweep in `docs/store/Accessibility-Pass.md` §4 — it
needs the same handset and the same demo build.
