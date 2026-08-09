# STATE — android tree

Single-glance state for the unattended window (2026-08-07 → 2026-08-18). Full derivation lives in
[`docs/S-Ladder.md`](docs/S-Ladder.md); evidence in [`LOG.md`](LOG.md); re-verification commands in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md).

| | |
| --- | --- |
| **Heartbeat** | 2026-08-09 (S1 close-out) |
| **Rung** | **S0 DONE · S1 DONE.** Next: S2 (engine publishes for real — closes B-2) |
| **S1 result** | PRs #27/#28/#29/#30 merged; `main` = `a8ef552`; pin **591**; sync-track paths on main **0 → 54**; vector drift **0** in every check |
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

## Next intent

**S2 — close B-2: make the engine publish for real.** `Program.cs::BuildSyncBridge` now specifies
exactly what is needed, and S1 put the code it extends into `main`:

1. A **DPAPI pairing vault** persisting `last_e2p_seq` **and** `last_p2e_seq` — §6.1 applies in both
   directions. An engine resuming e2p at 1 would have every envelope rejected as a replay; one
   resuming p2e at 0 would re-accept an already-applied entitlement.
2. Construct the publisher at the seam (`SyncPublisher` with `startSeq = max(vault, relay latest)`)
   and the inbound pull loop feeding `InboundDispatcher`.
3. The desktop **`/pair` page** (pairing code + QR) feeding `PairingManager`.
4. **E2E against a LOCAL relay** under miniflare/vitest. **Never a deploy.**

`--sync` stays default OFF (opt-in, privacy-load-bearing per `docs/Sync-Consent-Copy.md`).

**Not doing:** `SyncLiveSmoke` against the production relay — embargoed, and the live Worker still
predates P2/P4. Its redeploy is a return-day human item.

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
