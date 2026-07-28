# P5 Evidence — Store readiness (STAGED)

**Session:** Opus 4.8, executing P5 per `docs/P5-Runbook.md` (read from `main`).
**Started:** 2026-07-24. **Branch:** `claude/p5-store` (worktree, off `claude/p2-replica`).
**Evidence standard:** "ran it and saw it." Every Play-policy fact below was verified against
the live page on the date shown, with the load-bearing text quoted. Policy drifts quarterly —
**re-verify each row on account day** (§2 carries the URLs to re-check).

This file is the running log; it grows as artifacts land. It is the audit seed for `docs/store/**`.

---

## 1. Session setup (parallel-session discipline)

**Worktree created (first command of the session):**

```
$ git -C C:/Users/bkirk/Documents/careerseeker-android worktree add \
    C:/Users/bkirk/Documents/careerseeker-android-p5 -b claude/p5-store claude/p2-replica
Preparing worktree (new branch 'claude/p5-store')
HEAD is now at d9f95fd Codex audit defense: the first real payload wipes demo data, never merges it

$ git -C C:/Users/bkirk/Documents/careerseeker-android worktree list
C:/Users/bkirk/Documents/careerseeker-android    d9f95fd [claude/p2-replica]   <- main clone, P4's, untouched
C:/Users/bkirk/Documents/careerseeker-android-p5 d9f95fd [claude/p5-store]     <- P5 works here
```

The main clone stayed on `claude/p2-replica` throughout (verified after `worktree add`). All P5
android work happens in `careerseeker-android-p5`. **Remove the worktree at session end.**

**Engine repo — NOT touched.** `C:\Users\bkirk\Documents\CareerSeeker` (the .NET engine, on
`main`) was **read** for the live-site source (`docs-site/privacy.md`) as reference for §2.2, and
never written. The drift trap is P4's this cycle; this session never triggers it.

**Device status (2026-07-24):** Pixel 10 **not connected** —
`adb devices` (adb `37.0.0-14910828` at `%LOCALAPPDATA%\Android\Sdk\platform-tools`) lists no
devices. Consequence: §2.4 real-screenshot capture and the on-device TalkBack half of §2.5 are
**device-gated** (see §5). Everything that does not require the handset was completed this session;
no screenshot was fabricated.

**Manifest state today (grounds the §2.1 permissions truth-matching):**

```
$ grep -nE 'uses-permission|uses-feature' app/src/main/AndroidManifest.xml
12:  <uses-permission android:name="android.permission.INTERNET" />
```

Only `INTERNET`. No `CAMERA`, no `POST_NOTIFICATIONS` yet — those arrive with the P2 device-finale
pairing UI. `app/build.gradle.kts`: `applicationId = "app.careerseeker.dashboard"`,
`compileSdk = 37`, `targetSdk = 37`, `minSdk = 26`.

---

## 2. Live Play-policy verification (verified 2026-07-24)

Each row: what I needed, the authoritative Google URL, and the load-bearing text quoted from it on
2026-07-24. **These are the dated citations the runbook requires; re-verify on account day.**

### 2.1 Target API level — app is compliant (ahead)

- **Source:** Play Console Help, *Target API level requirements for Google Play apps*
  — https://support.google.com/googleplay/android-developer/answer/11926878 (2026-07-24)
- **Verbatim:** "Android 16 (API level 36) or higher" for new apps and updates, **effective
  August 31, 2026**. Apps targeting API 34 or lower "will only be available on devices running
  Android OS that are the same or lower than your apps' target API level." An extension "to
  November 1, 2026" can be requested via the Play Console Policy Status page.
- **Our state:** `targetSdk = 37` → **already exceeds** the API 36 floor. No action; confirm the
  uploaded AAB still targets ≥36 on account day.

### 2.2 Packaging & signing

- **Source:** established Play requirement (cross-checked against the target-API and testing pages,
  2026-07-24); re-confirm the current wording on account day.
- **Fact:** upload format is **AAB** with **Play App Signing**; the `.apk` is what Play *delivers*,
  not what we upload. Keep an APK build target for sideload testing (we use it for §2.4/§2.5).

### 2.3 Play Billing Library minimum version (P4/Pro — recorded so account day isn't surprised)

- **Source:** Android Developers, *Google Play Billing Library version deprecation*
  — https://developer.android.com/google/play/billing/deprecation-faq (2026-07-24, via search)
- **Verbatim (summarized from the deprecation page):** by **August 31, 2026**, all new apps and
  updates "must use Billing Library version 8 or later." Current major is **9** (released May 2026);
  no direct 7→9 jump. Extension available to November 1, 2026.
- **Our state:** Pro billing is **P4's** to implement. Recorded here only so the `pro_unlock` INAPP
  work targets **PBL 8+** and account day carries no surprise.

### 2.4 Store-listing graphic assets — exact specs

- **Source:** Play Console Help, *Add preview assets to showcase your app*
  — https://support.google.com/googleplay/android-developer/answer/9866151 (2026-07-24)
- **Verbatim specs:**
  - **App icon:** "512px by 512px", "32-bit PNG (with alpha)", max "1024KB".
  - **Feature graphic:** "1024px by 500px", "JPEG or 24-bit PNG (no alpha)".
  - **Phone screenshots:** minimum **two** (across device types) to publish, maximum **eight** per
    device type; "JPEG or 24-bit PNG (no alpha)"; each side **min 320px, max 3840px**, and the max
    dimension **must not exceed twice** the min dimension. For high-visibility placement: min
    1080px, "9:16 for portrait (minimum 1080x1920px)" or "16:9 for landscape (minimum 1920x1080px)".
- **Our plan:** capture all five demo-mode screens on the Pixel 10 (its native panel is well within
  bounds); feature graphic authored at exactly 1024×500; icon decision is **Gate P5-ICON** (§2.4
  doc) — no placeholder art shipped.

### 2.5 Data-safety form — the E2EE exception is the crux

- **Source:** Play Console Help, *Provide information for Google Play's Data safety section*
  — https://support.google.com/googleplay/android-developer/answer/10787469 (2026-07-24)
- **Verbatim (the load-bearing exception):** "Data that is sent off device, but that is unreadable
  by you or anyone other than the sender and recipient as a result of end-to-end encryption does
  **not** need to be disclosed." A parallel ephemeral-processing carve-out exists (data "only stored
  in memory and retained for no longer than necessary to service the specific request in real-time").
- **Form structure quoted:** "Does your app collect or share certain types of user data?"; a
  security question on "whether or not all of the user data collected by your app is encrypted in
  transit"; a deletion question on "whether or not you provide a way for users to request that their
  data is deleted." Google's closing note: "You alone are responsible for making complete and
  accurate declarations."
- **Our mapping:** the relay carries only E2EE ciphertext the developer cannot read → falls under
  the exception → the honest answer is **"No data collected / No data shared."** Cloudflare is a
  **processor hosting ciphertext**, not a recipient of user data. Full walk-through in
  `docs/store/Play-Data-Safety.md`.

### 2.6 Closed testing / production access — org account avoids the 12/14 rule (verify on the day)

- **Source:** Play Console Help, *App testing requirements for new personal developer accounts*
  — https://support.google.com/googleplay/android-developer/answer/14151465 (2026-07-24)
- **Verbatim:** the requirement applies to "developers with personal accounts created after
  November 13, 2023," who must "run a closed test for your app with a minimum of 12 testers who have
  been opted-in for at least the last 14 days continuously." The 14 days must be continuous ("we
  won't count testers who opted in, tested for less than 14 days, and then opted out").
- **Organization accounts:** the page is **silent** on them — it neither imposes nor explicitly
  waives the rule for org accounts. Community threads report org accounts publishing straight to
  production, and separately report occasional closed-testing gates. **Honest posture for account
  day:** proceed on the org-account (D-U-N-S) path expecting **no** 12/14 requirement, but on the
  day read the Console's own Publishing-overview / production-access status and be ready to run a
  closed test from the Windows alpha testers if the Console asks. If it applies, the 14-day clock
  gates production — start it immediately (see `docs/store/Account-Day-Checklist.md`).

---

## 3. Live careerseeker.app snapshot (verified 2026-07-24) — the promises P5 copy must keep true

Fetched the live pages so staged copy keeps success-criterion 3 ("every public promise stays
literally true on launch day") intact.

**`/privacy/` (live)** already anticipates the relay: "mobile sync relay for the Android dashboard"
using "end-to-end encrypted payloads that we cannot read"; "We operate no servers that store user
data"; footer "no trackers, no cookies, no analytics." The §2.2 delta finalizes this into the
shipping "The phone dashboard and the relay" section (Cloudflare named).

**`/dashboard/` (live)** headline "The dashboard"; already carries the exact slot I fill:
"Screenshots and the Google Play link will appear here when the app enters review"; "One-time
purchase, and the only money we ever ask you for"; "no trackers, no cookies, no analytics."
**Finding (P5-FIND-1):** the live page also says the Android dashboard does "application drafting,
reply management, and interview scheduling — all accessible live from a mobile device." That
**over-promises the read-only P2 v1**: the phone does not edit/draft in v1 (that is P3), and "reply
management" brushes the permanently-out-of-scope send/reply line (spec §6.1). The §2.2 staged
`/dashboard/` copy corrects this to describe read-only v1 honestly; going live is P6's one-artifact
change. Noted for the site owner; not fixed cross-scope from this session.

**`/pricing/` (live)** verbatim promises (the pricing rewrite is **P6 + a Sonnet TODO**, not P5 —
recorded here only so P5 copy contradicts none of them): survives — "The Windows application is free
to download and free forever. No subscription, no trial clock, no locked features." Becomes false
when Pro/Cloud ship (P6 rewrite target) — "Our only revenue"; "It's a one-time purchase, and it's
the only money we ever ask you for"; "No subscription exists, so there's nothing to cancel."

**Site-source discrepancy (noted, not mine to fix):** the engine repo's `docs-site/privacy.md`
(labeled "L1 Drafts alpha v0.1", effective 2026-07-18) is a *different, older* draft than what is
live at `careerseeker.app/privacy/`. Whoever owns the site deploy (P6/engine) must reconcile source
↔ live before the one-artifact launch. P5 anchors to the **live** text as ground truth.

---

## 4. Artifact production log

All under `docs/store/` (+ this evidence file), on `claude/p5-store`.

| Artifact | § | State |
| --- | --- | --- |
| `Play-Data-Safety.md` | 2.1 | Done — answers grounded in the verified E2EE exception; permissions truth-matching (both manifest states) + red-box AAB-match rule; IARC/ads/18+/not-financial staged. |
| `Privacy-Policy-Delta.md` | 2.2 | Done — publish-ready `/privacy/` relay section (Cloudflare named) + corrected `/dashboard/` copy (fixes P5-FIND-1); one-artifact / do-not-deploy rules; site-source discrepancy noted for P6. |
| `Play-Listing.md` | 2.3 | Done — title 22/30, short 72/80, full **2,522/4000** (all counted); reviewer notes; read-only v1 truth fence; conditional editing block gated on P3. |
| `assets/feature-graphic.{svg,png}` | 2.4 | Done — rendered **1024×500**, viewed. **Rebranded 2026-07-24** to the official Claude Design brand (logo + wordmark). |
| `assets/icon.{svg,png}` + `Gate-P5-ICON.md` | 2.4 | **Gate P5-ICON RESOLVED** — Brandon supplied the official radar logo in Claude Design; **512×512** icon adapted from it, rendered + viewed. Earlier 3 slate/blue drafts removed. In-app launcher swap folded into the Design-Language decision. |
| `assets/CAPTURE.md` + `assets/README.md` | 2.4 | Done — capture procedure + specs staged; screenshots device-gated (§5). |
| `Accessibility-Pass.md` + 6 additive code fixes + 5 a11y tests | 2.5 | Done (code) — full ritual green; on-device TalkBack sweep device-gated (§5). |
| `Account-Day-Checklist.md` | 2.6 | Done — 16-step ordered afternoon, owner column, each step → its staged artifact; policy re-verify reminders. |

## 5. Device-gated items (await Pixel 10 over USB)

The Pixel 10 was not connected this session (`adb devices` empty, §1). These two items are staged to
be a mechanical run when the handset is plugged in — **nothing was fabricated**:

- **§2.4 screenshots** — five demo-mode captures per `docs/store/assets/CAPTURE.md`. Run
  `adb devices` → `assembleDebug` → `install -r` → `exec-out screencap` for each screen; drop into
  `assets/01..05-*.png`; record device/build/adb output here.
- **§2.5 on-device TalkBack** — the code audit + additive fixes + 5 assertions landed and are
  CI-green; the TalkBack sweep + Accessibility Scanner run are the checklist in
  `Accessibility-Pass.md` §4. Do both in the **same** device session.

## 6. CI / build evidence

**Baseline (pre-change), 2026-07-24:** `:app:testDebugUnitTest` → **BUILD SUCCESSFUL in 25s**;
DemoFixtureTest 3 / EnvelopeApplierTest 16 / ScreensFromFixtureTest 6 = **25**, 0 failures (matches
the recorded pin).

**A11y fixes — full ritual:** `./gradlew --no-daemon checkCoreIsAndroidFree :core:test
:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- First run **FAILED** — `:app:compileDebugUnitTestKotlin`: `Unresolved reference 'assert'` (test
  import only; the screen changes and `:app:assembleDebug` both succeeded). Fixed by adding
  `import androidx.compose.ui.test.assert`.
- Re-run **BUILD SUCCESSFUL in 51s, `RITUAL_EXIT_CODE=0`.** App unit tests **25 → 30**
  (ScreensFromFixtureTest **6 → 11**, +5 a11y assertions), 0 failures; `:core` 17 (ProtocolTest 11,
  ProtocolVectorsTest 6); `:app:lintDebug` clean with `warningsAsErrors`. Debug APK built.
- Code diff vs `claude/p2-replica`: 3 screen files + 1 test, **+94/−6**, additive semantics only.

**Assets rendered + verified, 2026-07-24:** headless Chrome (`/c/Program Files/Google/Chrome`) →
PNGs; pixel sizes confirmed via PNG IHDR; each PNG opened and visually confirmed. **Rebrand
2026-07-24:** after Brandon supplied the official brand in Claude Design (radar logo; near-black
`#0A0A0B` / lime `#A3FF12` / yellow `#FFE500`), the feature graphic + a 512² icon were regenerated
from the official `logo.svg` and re-verified; the three earlier slate/blue icon drafts were removed.
Recipe in `assets/README.md`.

**GitHub Actions CI (branch push):** two commits pushed to `origin/claude/p5-store` (no PR). CI run
[30131023641](https://github.com/ShivaClaw/careerseeker-android/actions/runs/30131023641) —
**conclusion `success`** ("Build and test", 6m12s) on head `5fffdd0`, matching the local full ritual.

## 7. Claude Design import — brand, site reskin, app design language (2026-07-24)

Brandon supplied two Claude Design projects (read via the DesignSync tool; nothing written back):
`d57594ba…` **CareerSeeker Design Language** (product-UI cockpit system + Android mockups) and
`e03c8d14…` **CareerSeeker Site Reskin** (homepage redesign + brand logo + full `site-v2` backup).

**Two palettes, by design:** brand/marketing = `#0A0A0B` + lime `#A3FF12` + yellow `#FFE500`
radar logo (Space Grotesk/Inter/Plex Mono); product UI = "cockpit" `#07090D`/`#0C1017` +
trust-blue `#45C4FF` + status lamps (IBM Plex Sans/Mono).

**Decisions (Brandon, in-session):** app Design Language → **plan now, build after P4 merges**
(full reskin isn't additive; P4 owns the screens) — plan staged at
`docs/todo/Design-Language-Implementation.md` with tokens, screen mapping, the do-NOT-ship list
(kill switch — protocol `kill` reserved-and-rejected; OPEN DRAFT actions — P3), gates G-DL-1/2.
Site Reskin → **staged on `claude/p5-store`, no deploy** (P6 one-artifact) at
`docs/store/site-reskin/` — faithful implementation of the mock as a self-contained static page
(vanilla-JS port of the mock's React radar-clock animation) + assets.

**Render proof:** headless-Chrome screenshot of the staged homepage (1280w) viewed — nav, animated
hero (needle mid-sweep), schematic panel, trust cards, divider, pricing cards, footer all render
per the mock with Space Grotesk loaded.

**P5-FIND-2 (copy flags carried in HTML comments for the P6 pass):** (a) the reskin pricing card
says "Our only revenue" — one of the three sentences Monetization-Decision §1 marks as breaking
when Pro ships; P6's pricing rewrite must reconcile it. (b) hero eyebrow "Free during beta" sits
against the live "free to download and free forever" promise — P6 copy call. (c) Google-Fonts
requests vs the footer's "loads nothing that watches you" — recommend self-hosting WOFF2 at deploy.

**Gate P5-ICON:** brand resolved (radar logo); store icon + feature graphic regenerated earlier in
§4; the in-app launcher swap is scheduled inside the Design-Language TODO (step 5).
