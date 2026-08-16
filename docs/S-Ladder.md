# S-Ladder — derived truth at re-entry

**Derived:** 2026-08-08T15:07:57-06:00 · **Rung:** S0 · **Agent:** Claude (android + engine-sync track)

This document records what was *measured* at re-entry, not what was expected. Where the mission's
stated expectation and the repository disagree, the measurement wins and the difference is called
out explicitly. Every claim here has a re-verification command in [`AUDIT-REQUEST.md`](../AUDIT-REQUEST.md).

---

## 0. Mandatory fetch (rule one)

Both trees fetched before anything else was read.

| Tree | Result |
| --- | --- |
| `careerseeker-android` | no ref changes |
| `careerseeker-sync` | `main` moved `e95b1b3..3a89fb5` (**27 commits**); 7 new `codex/r0..r6` branches; `autonomy/codex-state` appeared |

The stale-refs rule earned its keep: the sync clone's local `main` was **27 behind** at session
start. Nothing was branched or compared until after the fetch.

---

## 1. Android repo (`ShivaClaw/careerseeker-android`)

### 1.1 Branch topology — the two-lineage hazard, measured

The mission expected a chain `p2-replica → p4-pro → p5-store`. The measurement says otherwise:

```
origin/main            ebfaf81   docs-only (HANDOFF.md, README.md, docs/) — NO code
                                 NOT an ancestor of the code lineage
                                 main…p2-replica = 10 ahead / 23 ahead (diverged)

d9f95fd  claude/p2-replica  ==  claude/p4-pro     <-- byte-identical, same SHA
   |
   +-- claude/android-a0-probe   d839e48   (+9 commits)   A0→A7 alpha ladder
   +-- claude/p5-store           bb7f4d0   (+5 commits)   P5 store readiness
```

Three corrections to the expected picture:

1. **`p4-pro` is not a distinct lineage.** `claude/p4-pro` and `claude/p2-replica` are the *same
   commit* (`d9f95fd`). There is **no android-side P4 work** on any branch. P4 exists only
   engine-side, as PR #8 in the main repo. This is consistent with P4's android half (§2.6) never
   having been started.
2. **`a0-probe` and `p5-store` are siblings, not a chain.** They both branch from `d9f95fd`.
   `p5-store` is *not* an ancestor of `a0-probe` (verified: `merge-base --is-ancestor` exit 1).
3. **`main` has diverged from the code lineage**, it is not merely behind it. `main` carries 10
   docs-only commits the code branches have never seen. Any eventual merge is a real merge, not a
   fast-forward.

**Merge hazard — flagged, deliberately not resolved (merge policy is Brandon's).** The exact
overlap between the two sibling branches, computed as a file-set intersection of
`d9f95fd..a0-probe` and `d9f95fd..p5-store`:

| Overlapping file | Touched by |
| --- | --- |
| `app/src/main/kotlin/app/careerseeker/dashboard/ui/HomeScreen.kt` | both |
| `app/src/main/kotlin/app/careerseeker/dashboard/ui/ApplicationsScreen.kt` | both |
| `app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt` | both |

The mission predicted `ApplicationDetailScreen.kt` would also collide. It does **not**:
`a0-probe` never touches that file; only `p5-store` does. The measured collision set is three
files, and the third is a test.

### 1.2 Pull requests

All five are **DRAFT**; none merged. Left draft, untouched, per house rule.

| PR | Branch | State |
| --- | --- | --- |
| #5 | `claude/p5-store` | DRAFT |
| #4 | `claude/p2-replica` | DRAFT |
| #3 | `claude/p1-pairing` | DRAFT |
| #2 | `claude/p1-runbook` | DRAFT |
| #1 | `claude/p0-scaffold` | DRAFT |

`claude/android-a0-probe` (tip `d839e48`, matching the mission's expectation) had **no remote and
no PR** at re-entry. HANDOFF recorded the A6 artifact at `26b9aee`, also confirmed.

### 1.3 Superseded house rule — pushing

`LOG.md` A7.2 records: *"Pushed: **no** — house rule is that Brandon decides when draft PRs
open."* Mission §3(c) (2026-08-07) explicitly reverses this: pushing branches and opening draft
PRs is now allowed in both repos. **Recorded as superseded**, and acted on this rung.

### 1.4 Documents referenced by the mission that do not exist here

Searched across `main`, `p2-replica`, `p5-store`, and `a0-probe` — zero hits:

- `docs/P2-Runbook.md` — mission §2.1 says to update its §4 gate record. **Not in this repo on any
  branch.** The P2 runbook content lives in the main repo. The gate answer is recorded in §4 below
  instead, and the runbook edit is deferred to whichever tree actually holds the file.
- `docs/Sync-Protocol.md` — lives in the **main** repo, and only on the unmerged PR stack (§2.2).
- `HUMAN-QUEUE.md` — the real path is `docs/autonomy/HUMAN-QUEUE.md` in the **main** repo.

---

## 2. Main repo (`ShivaClaw/careerseeker`) — engine sync track

### 2.1 The stack is intact

Stacked ancestry **verified**, not assumed (`merge-base --is-ancestor`, all exit 0):

| PR | Branch | Commits | `5⊂6⊂7⊂8` | Behind `main` |
| --- | --- | --- | --- | --- |
| #5 | `claude/android-apk-build-setup-90d9d5` | 3 | — | **85** |
| #6 | `claude/p1-sync` | 6 | 5 ⊂ 6 ✓ | **85** |
| #7 | `claude/p2-publisher` | 13 | 6 ⊂ 7 ✓ | **85** |
| #8 | `claude/p4-entitlement` | 21 | 7 ⊂ 8 ✓ | **85** |

Commit counts match the mission exactly (3/6/13/21). **Behind-count is 85, not the expected ~58** —
`main` advanced 27 commits while the stack sat still, which is precisely the 27 this session's
fetch pulled down.

PR #8 (`claude/p4-entitlement`) is **pushed and open as a draft** — correcting a stale memory note
that recorded those commits as local-only.

### 2.2 The finding that reorders the ladder

**The entire engine sync track is absent from `main`.** Path-existence check on `origin/main` for
`relay/`, `src/Sync/`, `docs/Sync-Protocol.md`, `docs/sync-vectors/`, `tests/SyncHarness`:

```
matches on origin/main:                        0
matches on origin/claude/p4-entitlement:      45+   (relay/, src/Sync/, protocol, 26 vectors)
```

So the protocol spec, the shared vectors, the blind relay, and the C# sync sources exist **only on
the unmerged stack**. Consequences:

- **S1 is not housekeeping — it is the gate.** S2 (publisher + `/pair`), S4 (transport), S5
  (entitlement ack) and S6 (outcomes) all edit files that do not exist in `main` yet.
- The android repo's vendored vectors are pinned to `679a317`, a commit that is reachable in the
  main repo but **not an ancestor of `main`**. Cross-repo drift is therefore currently measured
  against an unmerged branch — worth stating plainly in the S1 PR.
  > **Superseded 2026-08-16, forty-fifth run.** The pin moved to `7328a0b` on 2026-08-12 and the
  > three post-pin vectors were re-vendored by `056a1dd`. The off-`main` posture this bullet
  > describes is unchanged — `7328a0b` is not an ancestor of `main` either — so the *finding*
  > stands; only the SHA and the count are stale. `VECTORS.lock` is authoritative; see
  > `docs/Merge-Topology.md` §8.

### 2.3 Gate `P0-BASE` — superseded, recorded

`P0-BASE` targeted `claude/alpha-finish`. PR #4 (`claude/alpha-finish`) is **MERGED** (2026-07-21);
the alpha train has long since landed. **New base of record: `origin/main` =
`3a89fb58673712ac46aff82b35d7d269cb15793c`.** Supersession recorded here per mission S0.

### 2.4 Coordination — Terra (Codex)

Read from `autonomy/codex-state:STATE.md`, heartbeat `2026-08-07T21:18:24-06:00`:

- Current rung **R6(b) dependency/SBOM — BLOCKED** after the bounded two CI attempts; PR #26 draft.
- **Files claimed: none** for the next iteration. → **No collision with this iteration's slice.**
- Terra holds worktree `C:\Users\bkirk\Documents\CareerSeeker-r6-sbom` — untouched, as is
  `Documents\CareerSeeker`.
- Terra's measured `$ExpectedOfflineTotal` is **412** (was 407 at R0). S1 must re-derive this and
  sweep every count-reporting doc, per the drift trap.
- Terra noted `autonomy/claude-state` was **absent**. Created this rung.

---

## 3. BLOCKED-item movement

- **B-3 (vendored-vector drift unverifiable locally) — now locally VERIFIED.** With a genuine
  independent clone of the main repo available this window, the vendored vectors were compared
  **blob-to-blob** against pin `679a3175590dcd021b21c85af9daf12114e131fd`:
  **26 files compared, 0 mismatches.** This is stronger than A2's original check (which compared
  against a same-machine reference tree). CI's authoritative run follows from this rung's push.
- **B-1 (pairing UI) — gate half cleared.** `P2-KEYSTORE-FALLBACK` is answered (§4). The device
  half remains: emulator creation is now explicitly allowed (mission §3a) and is S3's first task.
- **B-2 (no live end-to-end) — unchanged, and now precisely scoped.** §2.2 explains why: the
  publisher seam it needs is on PR #7, which is not in `main`. S1 → S2 is the path.

---

## 4. Gates now law (Brandon, 2026-08-07)

| Gate | Answer |
| --- | --- |
| `P2-KEYSTORE-FALLBACK` | **Fall back, visibly.** Software key permitted; persistent "software-backed key" indicator; downgrade written to the audit trail. |
| `PQ-A6-1` | **Default-proceed.** `entitlement_ack` body `{product_id, acknowledged_at, order_id?}`. Spec + vector + engine + phone move as one change (S5). |
| `PQ-A2-1` | Option (a) — 1 MiB cap measured on the **ciphertext**; both implementations stand. |
| `PQ-A2-2` | Structural rejection reports `decrypt_failed`; state it in §3. |
| `PQ-A2-3` | Add the `invalid-unknown-field` vector via `generate.mjs`. |
| Misc | Room stays. `applicationId` stays PROVISIONAL. Play floor targetSdk 36 from 2026-08-31 — **re-verify live at S7**, never from spec. |

`P2-KEYSTORE-FALLBACK` was to be recorded in `docs/P2-Runbook.md` §4; that file is not in this
repo (§1.4), so it is recorded here and carried to S3.

---

## 5. Ladder status

| Rung | Status | Note |
| --- | --- | --- |
| **S0** — re-entry + derivation | **DONE** | this document |
| **S1** — rebase/land engine stack | **NEXT** | gates everything downstream (§2.2); 85 behind, 4 PRs |
| **S2** — engine publishes for real | blocked by S1 | `/pair` page + publisher sink + local-relay E2E |
| **S3** — pairing screen | ready (needs emulator lane) | gate answered; B-1 device half open |
| **S4** — transport loop | blocked by S1, S3 | |
| **S5** — entitlement ack | blocked by S1 | no `HOLD S5` received → proceed |
| **S6** — outcome marking | blocked by S3, S4 | |
| **S7** — Play-readiness pack | ready in part | re-verify Play floor live |
| **S8** — hardening | ready in part | |

**Next intent:** S1, starting with PR #5 (`claude/android-apk-build-setup-90d9d5`, 3 commits)
rebased onto `origin/main` `3a89fb5`.

---

## 6. Boundary — what this rung did not touch

No deploys of any kind. The production relay was not contacted at all this rung (not even
`/v1/health`). `Documents\CareerSeeker` and Terra's `CareerSeeker-r6-sbom` worktree were never
read from or written to. No Play/Google/OAuth console, no accounts, no purchases, no Play Billing
code, no email, no secrets, no `.appdata`, no `Desktop\site-v2`. No force-push, no history
rewrite, no merges in either repo. Android PRs #1–#5 were left draft and unmodified. No android
source file was edited — this rung is documentation and derivation only.
