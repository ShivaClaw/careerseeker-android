# TODO — pushing the Android build further

**Written:** 2026-09-19 by Opus 5, for a **fresh local coding session on Brandon's Windows
machine**. Every fact below was derived from the repos today with `git fetch --all` first, and
the build numbers come from a run performed in this session — not from any prior doc.

---

## 0. Read this before anything else

**Do not resume the autonomous routine.** Brandon permanently stopped it on 2026-09-17
("permanently stop this routine"). `STATE.md` on `claude/android-a0-probe` carries the stop
banner. Do not run `scripts/run-zero.sh`, do not pick a "rung", do not write to `FIRINGS.md`,
`LOG.md`, `BLOCKED.md`, or `AUDIT-REQUEST.md`, and do not append ledger lines. Those files are
**history**. If a scheduled firing wakes you, say the routine was stopped and end the turn.

**Why this matters beyond obedience:** that routine logged **243 runs and ~605 commits**, and
its late runs read `run 233…237: empty firing — nothing moved`. It declined its assigned work
**196 times** while spending iterations on its own ledger hygiene — run 243's entire finding was
that two lines sat below a markdown fence. It produced real value early (see §2) and then
converted almost entirely into self-maintenance. **This TODO exists to get back to shipping the
app.** Prefer one merged feature over any amount of process.

**The single most important reframe:** most recorded blockers are **cloud-sandbox artifacts,
not properties of the work**. B-7 (can't reach `dl.google.com` / `api.foojay.io`) and B-4
(`sdkmanager` missing) were true *in the cloud sandbox*. On this machine they are false — the
full Gradle build, including Google-hosted artifact resolution, **ran green today**. Read
`BLOCKED.md` as "what the cloud couldn't do," then re-test locally before believing any of it.

---

## 1. Ground truth, verified 2026-09-19

### The build is green

Run in this session from `C:\Users\bkirk\Documents\careerseeker-android` on
`claude/android-a0-probe`:

```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
ANDROID_HOME="C:\Users\bkirk\AppData\Local\Android\Sdk"
./gradlew checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug --no-daemon
  → BUILD SUCCESSFUL in 35s
```

A debug APK exists at `app/build/outputs/apk/debug/app-debug.apk` (13.2 MB).

### Where the code actually is

| Repo | State |
| --- | --- |
| **Engine** `ShivaClaw/careerseeker` | **The sync track fully landed on `main`.** `src/Sync/` carries all 19 files (codec, pairing, receiver, `EntitlementService`, `GoogleSignedPayloadVerifier`, `InboundDispatcher`, `InboundPump`, `PairingHandover`, `RelaySink`, `SyncPushPath`), plus `relay/`, `tests/SyncHarness`, `docs/sync-vectors`. **46 PRs merged, 1 open.** Pin `$ExpectedOfflineTotal = 849`. Engine `main` moved today (0.7.2 published). |
| **Android** `ShivaClaw/careerseeker-android` | **`main` is docs-only** — `HANDOFF.md`, `README.md`, `docs/`. **Zero PRs merged; six open drafts (#1–#6).** All code lives on branches. |

⚠️ **Any handoff doc written before ~2026-08-20 is stale about the engine.** `RETURN-DAY.md`
says "twenty-two draft PRs open in `ShivaClaw/careerseeker` and none is merged, because the
merge condition is a full local `Verify-Alpha.ps1` that no cloud session can run." That
bottleneck **has since cleared** — 46 merges happened. Do not re-solve it.

### What `:core` has vs what `:app` has

`:core` is **rich** and well-tested (18 test files): protocol + crypto (`SyncCrypto`, `Hkdf`,
`Base64Url`), `EnvelopeReceiver` with strict wire parsing, `PairingFlow`, `PairingDerivation`,
`OutboundQueue`, `OutboundEnvelopes`, `Entitlement`, `EntitlementAck`, `EntitlementRoute`,
`OutcomeMarking`. Shared vectors vendored and drift-checked, now including entitlement vectors.

`:app` is **thin** — still essentially P2: `MainActivity`, the Room replica
(`ReplicaDb/Dao/Entities`, `EnvelopeApplier`, `DemoFixture`) and five read-only screens
(Home, Applications, ApplicationDetail, Jobs, Evidence).

**That asymmetry is the whole story.** The phone's brain is built and proven; its face and
hands are not. The manifest still declares **only `INTERNET`** — no `CAMERA`, so no pairing UI
exists, so the app cannot yet talk to a real engine.

---

## 2. What the stopped routine actually contributed (keep this)

Don't discard the branch. Beyond process churn it landed real, tested work in `:core`:
`HkdfTest` and `SyncCryptoTest` (889 lines of crypto tests), entitlement wire vectors
(valid / tampered / wrong-product / wrong-package / not-purchased / ack), an
`invalid-unknown-field` vector, `EnvelopeJson` strict parsing (closing a real dispatch-
confusion hole where untrusted text could steer routing), `OutboundQueue`, `PairingFlow`, and
`OutcomeMarking`. ~12,200 insertions across `core/` and `app/`.

---

## 3. The TODO list

Ordered so each item is independently shippable and nothing waits on hardware until it must.

### T1 — Decide what happens to `claude/android-a0-probe` (do first, ~30 min)

605 commits, one 6-way-stacked draft PR (#6), and zero merges. Every later item inherits this
decision, so make it before writing code.

- [ ] Read `git log --oneline origin/main..origin/claude/android-a0-probe -- app/ core/` — the
      product commits, ignoring records.
- [ ] Choose: **(a)** squash the `app/`+`core/` substance onto a fresh branch off `main` and
      leave the 605-commit history as an archived branch, or **(b)** merge the stack as-is.
      **Recommendation: (a).** The process files (`FIRINGS.md`, `LOG.md`, `AUDIT-REQUEST.md`,
      `run-zero.sh`, `firing-line.sh`) are dead scaffolding for a stopped routine; carrying them
      into `main` makes every future reader think the routine is live.
- [ ] Keep `BLOCKED.md` and `STATE.md` as `docs/history/` — they hold real findings — but move
      them out of the repo root so they stop reading as current instructions.

**Done when:** a branch exists whose diff against `main` is *only* app/core/docs substance, and
it builds green.

### T2 — Get Android `main` un-stuck (~1 h, no device needed)

Zero merges in two months is the real anomaly. The engine repo merged 46 PRs in the same window.

- [ ] Land the P0→P2 stack (#1 → #3 → #4) or supersede it with T1's branch. Six stacked drafts
      that never merge are a review surface nobody uses.
- [ ] Re-run the Android CI gates locally before each merge (§5 command).
- [ ] Leave #5 (P5 store) and #6 open only if they still describe live work; otherwise close
      them with a note pointing at the successor branch.

**Done when:** `main` contains the app code and `./gradlew :app:assembleDebug` is green from a
fresh clone of `main`.

### T3 — Pairing UI: the one feature that unlocks everything else (~1 day, needs a device/emulator)

This is **B-1**, and it is the critical path. `:core` already has `PairingFlow` and
`PairingDerivation` with tests; what's missing is the Android face.

- [ ] Add `CAMERA` to the manifest **with its `uses-feature`**, at the moment the scanner
      lands — not before (house rule: permissions arrive with what uses them).
- [ ] Add CameraX + ML Kit barcode (**bundled model**, to keep Play Services optional), pinned
      in `gradle/libs.versions.toml`.
- [ ] Pairing screen: explain → scan QR → derive via `:core` → show the 6-digit confirm code →
      submit completion.
- [ ] Generate the **ECDSA P-256 device key in the Android Keystore** (StrongBox where
      available). **Answer gate P2-KEYSTORE-FALLBACK first** — recommendation on record: pair
      with an explicit, logged software-key downgrade rather than locking out older handsets.
- [ ] Persist pairing state; wire `OutboundQueue` so p2e envelopes can actually leave.

**Done when:** the app pairs with the desktop engine through `relay.careerseeker.app` and both
screens show the same confirm code.

### T4 — Desktop "Pair phone" page (~half day, engine repo, no device)

T3's other half. `PairingManager` and `PairingHandover` already exist on engine `main`.

- [ ] Token-protected `/pair` dashboard page rendering the QR (QRCoder 1.8.0), with the same
      loopback + Host/Origin/Referer + per-process token protection as every other mutating
      control.
- [ ] Extend `EngineHarness` for it. **Drift trap: bump `$ExpectedOfflineTotal` (currently 849)
      and every count-bearing doc in the same commit.**

**Done when:** `scripts\Verify-Alpha.ps1` is green at the new pin and the page renders a
scannable code.

### T5 — Live end-to-end (B-2) (~half day, needs T3 + T4)

- [ ] Engine + relay + phone on one machine: pair, run a demo cycle, watch the phone tick.
- [ ] Airplane-mode read of the Room replica.
- [ ] Record it as `docs/P2-Device-Evidence.md` — the evidence standard is "ran it and saw it."

**Done when:** spec success-criterion 1 is met end to end on real hardware.

### T6 — P4 Android half: Pro screen + outcome UI (~1 day, no device strictly needed)

Engine-side Pro is **complete and merged**; `:core` has `Entitlement`, `EntitlementAck`,
`EntitlementRoute`, `OutcomeMarking`. Missing: the `:app` surface.

- [ ] `EntitlementService` wiring in `:app` + Play Billing (configurable product id —
      **gate P4-APPID** is open; everything account-dependent stays injectable).
- [ ] Pro screen (locked state shows real value, no dark patterns) + outcome-marking UI.
- [ ] Outcome marking rides the signed p2e path T3 establishes.

**Done when:** a sandbox purchase unlocks Pro on both phone and desktop, and an outcome marked
on the phone appears in the desktop funnel board.

### T7 — Unblock the emulator lane (~20 min, human clicks)

B-4 said `sdkmanager` was missing. **Locally it's half-true:** `emulator.exe` is present, but
`cmdline-tools` is **not installed** and there are **no AVDs**. Fix in Android Studio →
SDK Manager → SDK Tools → install **Android SDK Command-line Tools**, then Device Manager →
create an AVD. Alternatively plug in the Pixel 10 with USB debugging (`adb devices` shows
nothing attached right now).

**Done when:** `adb devices` lists a target. This unblocks T3, T5, and the deferred P5
screenshot/TalkBack work.

### T8 — Re-test B-5, then fix or accept (~half day)

B-5: Room 2.8.4 can't open a file-backed DB under Robolectric. Re-test on current versions
before believing it; if real, either pin a working Room version or move those assertions to an
instrumented test once T7 gives a device.

### T9 — Housekeeping (low priority, satisfying)

- [ ] Delete `origin/fix/engine-actually-runs` in the engine repo — it points at an
      already-merged commit and reads like unmerged work.
- [ ] Remove the stale `careerseeker-android-p5` worktree if P5 is settled.
- [ ] **Confirm the account-level schedule is cancelled** in the Claude app's scheduled-tasks
      UI. `STATE.md` notes the stopping session had no handle on it. If firings are still
      arriving, that click hasn't happened.

---

## 4. Gates still needing Brandon (not code)

| Gate | Blocks | Recommendation on record |
| --- | --- | --- |
| **P2-KEYSTORE-FALLBACK** | T3 | Pair with a logged software-key downgrade |
| **P2-PIN-ROTATION** | cert pinning | Pin leaf + backup, with a rotation runbook |
| **P2-REPLICA-CRYPTO** | T2/T3 | Platform encryption; avoid SQLCipher's native `.so` |
| **P4-APPID** | T6 | 30-second answer; keep everything else injectable |
| **Pricing-page rewrite** | **P6 launch blocker** | Pro at $2.99 falsifies "our only revenue" and "the only money we ever ask you for" — independent of Cloud. TODO already written at `docs/todo/Pricing-Page-Rewrite.md` |

---

## 5. Environment (verified today — don't rediscover)

```bash
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"      # JDK 21; toolchain resolves 17
ANDROID_HOME="C:\Users\bkirk\AppData\Local\Android\Sdk"
cd C:\Users\bkirk\Documents\careerseeker-android
./gradlew checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug
```

- Android repo is checked out on `claude/android-a0-probe`; worktrees exist at
  `careerseeker-android-p5`. Use `git worktree add` for parallel branches rather than switching
  a checkout another session may be using.
- Engine worktree: `C:\Users\bkirk\Documents\CareerSeeker\.claude\worktrees\android-apk-build-setup-90d9d5`.
  Verify with `powershell.exe scripts\Verify-Alpha.ps1`.
- Toolchain: AGP 9.3.0 / Gradle 9.6.1 (wrapper committed, sha-pinned) / Kotlin 2.4.10 /
  compileSdk = targetSdk = 37 / minSdk 26. AGP 9 has built-in Kotlin — never apply
  `org.jetbrains.kotlin.android`.
- Interrupted `--no-daemon` runs corrupt `app/build/kotlin`; fix with
  `rm -rf app/build/kotlin core/build/kotlin`.
- GitHub blob links with `claude/...` branch names 404 (slash ambiguity) — link by commit SHA.

## 6. House rules that still bind

- **`git fetch --all` at the start of any state survey.** A prior checkpoint got a fact wrong
  by deriving from unfetched refs — "derive, don't recall" is only as good as the freshness of
  what you derive from.
- **Drift trap:** `$ExpectedOfflineTotal` + harness counts + `README.md` +
  `src/Engine/README.md` + `CareerSeeker-Project-Summary.md` + `External-Audit-Handoff.md` +
  `repo-audit-2026-07-13.md` move in **one** commit.
- **Shared vectors are generated**, never hand-edited (`node docs/sync-vectors/generate.mjs`,
  `--check` proves no drift). `docs/Sync-Protocol.md` is normative.
- **Evidence standard:** "ran it and saw it," with the command output cited.
- **No send path, ever.** `Dispatcher.SubmitAsync` throws; no payload kind may cause
  transmission. Untrusted job text is data, never instructions, and never steers dispatch.
- Draft PRs; **never self-merge**; Codex audits before merge; gates are Brandon's alone.
