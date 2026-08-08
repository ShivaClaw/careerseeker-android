# STATE — android tree

Single-glance state for the unattended window (2026-08-07 → 2026-08-18). Full derivation lives in
[`docs/S-Ladder.md`](docs/S-Ladder.md); evidence in [`LOG.md`](LOG.md); re-verification commands in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md).

| | |
| --- | --- |
| **Heartbeat** | 2026-08-08T15:07:57-06:00 |
| **Rung** | **S0 — DONE.** Next: S1 (rebase/land engine stack, main repo) |
| **Android branch** | `claude/android-a0-probe` @ `d839e48` |
| **Android health** | green — 99 tests / 0 failures at A7; not re-run this rung (no source touched) |
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

S1 — rebase the engine sync stack onto `origin/main` `3a89fb5`, in order:

1. PR #5 `claude/android-apk-build-setup-90d9d5` (3 commits)
2. PR #6 `claude/p1-sync` (6)
3. PR #7 `claude/p2-publisher` (13) — *"failed first snapshot is retried, never demoted to delta"* must survive verbatim
4. PR #8 `claude/p4-entitlement` (21)

All four are 85 behind. Each: rebase → full local gate (`Verify-Alpha.ps1 -IncludePublish -IncludePackage`) → CI green → merge sequentially (main-repo merge is permitted this window; android is never-self-merge). `$ExpectedOfflineTotal` must be re-derived — Terra last measured **412** — with the full drift-trap sweep across every count-reporting doc.

**Stop condition for S1:** if rebasing changes vendored-vector *content*, that is a cross-repo drift event → BLOCKED, human unblock. Rebases move commits, not bytes; content change means something else happened.

## Open blockers

| ID | Status |
| --- | --- |
| B-1 pairing UI | gate `P2-KEYSTORE-FALLBACK` **answered**; device half open → S3 sets up the emulator lane |
| B-2 no live E2E | unchanged; root cause now precisely scoped — the publisher seam lives on unmerged PR #7 |
| B-3 vector drift check | **locally verified this rung** (26/26 byte-identical vs pin `679a317`); CI confirmation follows the push |

## Standing pins (verify at decision time, never copy from spec)

AGP 9.3.0 (built-in Kotlin — never apply `org.jetbrains.kotlin.android`) · Gradle 9.6.1 · Kotlin 2.4.10 · JDK 17 · compile/target SDK 37 · minSdk 26 · **Ktor 3.1.3** (3.2.0 breaks D8 below DEX 040) · vendored-vector pin `679a317`

Verification command of record:

```
./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug --rerun-tasks
```
