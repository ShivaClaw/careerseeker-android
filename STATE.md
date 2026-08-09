# STATE — android tree

Single-glance state for the unattended window (2026-08-07 → 2026-08-18). Full derivation lives in
[`docs/S-Ladder.md`](docs/S-Ladder.md); evidence in [`LOG.md`](LOG.md); re-verification commands in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md).

| | |
| --- | --- |
| **Heartbeat** | 2026-08-09 (session handoff) |
| **Rung** | **S0 DONE · S1 DONE · S2 PARTIAL.** S3–S8 **not started** (capacity, not a blocker) |
| **S1 result** | PRs #27–#30 merged; pin **591**; sync-track paths on main **0 → 54**; vector drift **0** every check |
| **S2 result** | PR #31 merged; `main` = `00b3705`; pin **598**; engine ↔ **local** relay **30/30**, no deploy |

**Ladder:** S0 ✅ · S1 ✅ · S2 ◐ (B-2 narrowed to the `/pair` page) · S3–S8 ✗ not started.
Full handoff at the end of [`LOG.md`](LOG.md).
| **Android branch** | `claude/android-a0-probe` @ `d839e48` |
| **Android health** | green — **CI run `31278769047` success** (vectors, `:core`, `:app`, APK, lint, no-analytics). Not re-run locally this rung; no source touched. |
| **Draft PR** | [#6](https://github.com/ShivaClaw/careerseeker-android/pull/6) — `a0-probe` → base `claude/p2-replica`, with self-audit |
| **Coordination bus** | `autonomy/claude-state` created @ `01ade62` |
| **Main-repo base of record** | `origin/main` = `3a89fb5` (gate `P0-BASE` superseded — see S-Ladder §2.3) |
| **Terra (Codex)** | R6(b) BLOCKED, PR #26 draft, **files claimed: none** — no collision |

## Files claimed this iteration

Android tree only, documentation surface only:

- `STATE.md` (this file)
- `docs/S-Ladder.md`
- `docs/CLAUDE-ANDROID-MISSION.md`
- `LOG.md`, `AUDIT-REQUEST.md`, `BLOCKED.md`

**No source file claimed.** Nothing claimed in the main repo this iteration.

## Next intent (in order)

1. **Finish S2 — the `/pair` route.** This is all that stands between B-2 and closed. The vault
   (`SyncPairingVault`) and the publisher wiring landed in PR #31; the handshake is vector-proven.
   Needed: create a `PairingManager`, render the invite (`PairingInvite.ToQrJson()` is the exact
   payload, so a **QR encoder is the only genuinely new dependency**), poll
   `RelayClient.TakeCompletionAsync`, show the confirm code for the human to compare, write
   `SyncPairing` to the vault.
2. **S3 — set up the emulator lane first** (`sdkmanager` + AVD, explicitly permitted §3a). Keystore
   cannot be modelled by Robolectric and compile-only claims are forbidden, so the lane is a
   prerequisite, not an optimisation.
3. **S4** then has a real rig: engine ↔ local relay ↔ emulator on `10.0.2.2`. The local-relay half
   is already proven (30/30).

`--sync` stays default OFF (opt-in, privacy-load-bearing per `docs/Sync-Consent-Copy.md`).

**Deliberately not done:** `SyncLiveSmoke` against the **production** relay — embargoed all window.
Note the live Worker's `phase:"p1"` is **not** evidence it is stale: that string is hard-coded at
`relay/src/index.ts:47`.

## Open blockers

| ID | Status |
| --- | --- |
| B-1 pairing UI | gate `P2-KEYSTORE-FALLBACK` **answered**; device half open → S3 sets up the emulator lane |
| B-2 no live E2E | unchanged; root cause now precisely scoped — the publisher seam lives on unmerged PR #7 |
| ~~B-3 vector drift check~~ | **CLOSED** — local 26/26 byte-identical vs pin `679a317`, then CI's own step confirmed it (run `31278769047`) |

## Standing pins (verify at decision time, never copy from spec)

AGP 9.3.0 (built-in Kotlin — never apply `org.jetbrains.kotlin.android`) · Gradle 9.6.1 · Kotlin 2.4.10 · JDK 17 · compile/target SDK 37 · minSdk 26 · **Ktor 3.1.3** (3.2.0 breaks D8 below DEX 040) · vendored-vector pin `679a317`

Verification command of record:

```
./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug --rerun-tasks
```
