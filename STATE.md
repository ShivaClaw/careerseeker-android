# STATE — android tree

Single-glance state for the unattended window (2026-08-07 → 2026-08-18). Full derivation lives in
[`docs/S-Ladder.md`](docs/S-Ladder.md); evidence in [`LOG.md`](LOG.md); re-verification commands in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md); blockers in [`BLOCKED.md`](BLOCKED.md).

| | |
| --- | --- |
| **Heartbeat** | 2026-08-09 (session handoff) |
| **Android branch** | `claude/android-a0-probe` — pushed, draft [PR #6](https://github.com/ShivaClaw/careerseeker-android/pull/6) with self-audit |
| **Android health** | **green** — 102 tests, 0 failures, 0 errors, 3 skipped (B-5); debug APK builds; lint clean under `warningsAsErrors`; 62/62 tasks executed |
| **Main-repo base of record** | `origin/main` = `00b3705` (gate `P0-BASE` superseded — S-Ladder §2.3) |
| **Main-repo PRs merged** | #27 `7f3e61e` · #28 `f0b9bd5` · #29 `160b317` · #30 `a8ef552` · #31 `00b3705` |
| **Offline pin** | **598** (was 418 at session start; measured at every step, never carried) |
| **Coordination bus** | `autonomy/claude-state` @ `e59ad6b` — pinch point released, nothing in flight |
| **Terra (Codex)** | R6(b) BLOCKED, PR #26 draft, files claimed: none — no collision all session |

## Ladder

| Rung | Status | Evidence / reason |
| --- | --- | --- |
| **S0** re-entry + derivation | **DONE** | `docs/S-Ladder.md`; `LOG.md` §S0; `AUDIT-REQUEST.md` C-S0-1…9 |
| **S1** land the engine sync track | **DONE** | PRs #27–#30 merged; sync-track paths on main **0 → 54**; vector drift **0** in every check; C-S1-1…6 |
| **S2** engine publishes for real | **PARTIAL** | PR #31; engine ↔ **local** relay **30/30**, no deploy. **B-2 open:** no `/pair` page |
| **S3** pairing screen | **BLOCKED — B-4** | `sdkmanager`/`avdmanager` are not installed anywhere on this machine; Keystore cannot be honestly verified without an AVD |
| **S4** transport loop | **BLOCKED — B-4** | needs S3's device key + an emulator for the claim to be E2E |
| **S5** entitlement ack | **NOT STARTED** | capacity. **Genuinely not blocked** — PQ-A6-1 is answered and the engine half needs no device |
| **S6** outcome marking (phone) | **BLOCKED — B-4** | needs S3 + S4 |
| **S7** Play-readiness pack | **PARTIAL** | upload keystore generated (§3b); Play floor **re-verified live**; `versionCode` scheme recorded → `docs/S7-Release-Signing.md`. No `.aab`, no Console action; screenshots need B-4 |
| **S8** hardening | **PARTIAL / BLOCKED — B-5** | migration test written; Room 2.8.4 cannot open a file-backed DB under Robolectric. Lint hold, full gate, bundle refresh **done** |

**S3, S4 and S6 are blocked on one checkbox** — Android Studio → SDK Tools → *Android SDK
Command-line Tools (latest)*. Mission §3a authorized *using* `sdkmanager`; it does not exist here,
and installing the toolchain that provides it was not authorized. B-5 is downstream of the same
thing (the migration test runs fine as an instrumented test).

**S5 is the one rung that is neither done nor blocked**, and it is labelled that way deliberately:
calling it BLOCKED would send the next session hunting for an obstacle that does not exist.

## Open blockers

| ID | Status |
| --- | --- |
| **B-1** pairing UI | gate answered; device half **still blocked** — see B-4 (the earlier "scheduled at S3" note was written before anyone checked `sdkmanager` existed) |
| **B-2** live E2E | **most of the way closed** — engine ↔ local relay proven 30/30; remaining gap is exactly the `/pair` page |
| ~~**B-3** vector drift~~ | **CLOSED** — 26/26 byte-identical to pin `679a317`, confirmed by CI's own step (run `31278769047`) |
| **B-4** emulator lane | `sdkmanager`/`avdmanager` absent; blocks S3/S4/S6 and B-1's device half |
| **B-5** migration test | Room 2.8.4 + Robolectric cannot open a file-backed DB; test kept under `@Ignore` with the diagnosis |

## Next intent (in order)

1. **Finish S2 — the `/pair` route.** All that stands between B-2 and closed. The vault and publisher
   wiring landed in PR #31 and the handshake is vector-proven. Needed: create a `PairingManager`,
   render the invite (`PairingInvite.ToQrJson()` is the exact payload — **a QR encoder is the only
   genuinely new dependency**), poll `RelayClient.TakeCompletionAsync`, show the confirm code for the
   human to compare, write `SyncPairing` to the vault.
2. **Tick the SDK Command-line Tools checkbox**, then the whole emulator lane is unattended and
   S3 → S4 → S6 unblock in order, along with B-5.
3. **S5 needs neither** and can proceed immediately — the engine half is device-free.

`--sync` stays default OFF (opt-in, privacy-load-bearing per `docs/Sync-Consent-Copy.md`).

**Deliberately not done:** `SyncLiveSmoke` against the **production** relay — embargoed all window.
The live Worker's `phase:"p1"` is **not** evidence it is stale: that string is hard-coded at
`relay/src/index.ts:47`.

## Standing pins (verify at decision time, never copy from spec)

AGP 9.3.0 (built-in Kotlin — never apply `org.jetbrains.kotlin.android`) · Gradle 9.6.1 · Kotlin
2.4.10 · JDK 17 · compile/target SDK 37 (live Play floor is 36 from 2026-08-31 — **verified
2026-08-09**) · minSdk 26 · **Ktor 3.1.3** (3.2.0 breaks D8 below DEX 040) · Room 2.8.4 ·
vendored-vector pin `679a317`

Verification command of record:

```
./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug --rerun-tasks
```
