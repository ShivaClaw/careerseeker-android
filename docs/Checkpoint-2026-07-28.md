# CareerSeeker Android Program — Checkpoint, 2026-07-28

**Written:** 2026-07-28 by Opus 5, at the **P2-complete / P4-engine-complete / P5-staged**
boundary. Supersedes nothing; it indexes what changed since
[Checkpoint-2026-07-24.md](Checkpoint-2026-07-24.md) and states what is at risk.

**Method:** derived from the repos, not recalled. Branch topology, commit histories, origin
refs, CI conclusions, and push access were each checked directly this session. Where a number
comes from a prior session's commit message rather than a run I performed, it is labeled
**(claimed)**.

---

> **Resolved same day (2026-07-28).** Both at-risk branches are **pushed** and origin
> confirms the SHAs (`claude/p4-entitlement` `d5bdb9d`, `fix/engine-actually-runs` `40bc9a7`).
> Four draft PRs are open, stacked: engine **#7** (P2 → P1) and **#8** (P4 → P2); android
> **#4** (P2 → P1) and **#5** (P5 → P2). **F-A and F-B are closed.**
>
> **F-C is now partly closed:** pushing P4 triggered its first-ever CI run, which came back
> **success** — so the pin-500 / EngineHarness-129 claim is no longer unreproduced; the
> verifier that enforces the pinned total ran green in CI. What remains of F-C is the missing
> `docs/P4-Evidence.md` narrative (P4 §2.7).
>
> **Correction to F-E — `fix/engine-actually-runs` was never at risk.** This checkpoint
> listed it as unbacked-up work. That was wrong, and the cause was a **stale local
> `origin/main`**: after `git fetch`, its tip `40bc9a7` proves to be an **ancestor of
> `origin/main`** — already merged upstream via **PR #10 `codex/beta-M1-engine-runs`**. The
> local branch was a leftover ref. No PR was opened for it; one would have been empty.
> Pushing it was harmless but redundant, and the remote ref is worth deleting so it does not
> read as unmerged work.
>
> **The larger miss that correction exposed:** `origin/main` is **33 commits ahead** of the
> local `main` this checkpoint was written against. An entire **beta hardening track** landed
> via Codex PRs #10–#18 — engine-runs, crash recovery, onboarding v2, single-executable
> **MSIX packaging**, evidence, and a hardening pass. The Android program's publisher rides on
> that engine, so **§1's picture of the alpha track is stale**. Re-derive against `origin/main`
> before the next engine-side phase, and check how the P2/P4 branches (cut from
> `claude/alpha-finish`) now relate to it — that base may have been overtaken.
>
> **Method note, worth keeping:** this checkpoint claimed to derive state from the repos, and
> it did — but from **unfetched** remote refs. "Derive, don't recall" is only as good as the
> freshness of what you derive from. `git fetch` belongs at the *start* of a state survey, not
> after a contradiction surfaces.
>
> The findings below are preserved as originally written.

## 0. The one thing to act on first

**Two branches carrying real work exist only on this machine.** `git ls-remote` confirms
origin does not have them:

| Branch | Repo | Tip | Contents | On origin? |
| --- | --- | --- | --- | --- |
| `claude/p4-entitlement` | careerseeker (public) | `d5bdb9d` | **8 commits — the entire P4 engine** | **NO** |
| `fix/engine-actually-runs` | careerseeker (public) | `40bc9a7` | alpha engine fix (all-zeros bug) | **NO** |

The P4 session knew: its final commit says *"Remaining: §2.6 android (**push-blocked**) +
§2.7 evidence/PRs."* **That block has cleared** — `git push --dry-run origin
claude/p4-entitlement` succeeded this session (`* [new branch]`), and `gh auth status` shows a
valid token with `repo` scope.

Nothing is backed up until these are pushed. The main repo is **public**, so this is Brandon's
call, not an automatic action:

```bash
git -C "C:/Users/bkirk/Documents/CareerSeeker/.claude/worktrees/android-apk-build-setup-90d9d5" push -u origin claude/p4-entitlement
```

`claude/p4-pro` (android) is **not** at risk — it sits at `d9f95fd`, the same tip as the pushed
`claude/p2-replica`, i.e. it is a branch pointer with no unique commits. P4's Android half
(§2.6) has not started.

---

## 1. Status by phase

| Phase | State | Where | Verified |
| --- | --- | --- | --- |
| **P0** Decisions + skeletons | **Done** | PRs #5 (engine), #1 (android) | CI green |
| **P1** Relay + pairing | **Done** minus device UI | PRs #6 (engine), #3 (android) | CI green; live smoke 17/17 |
| **P2** Read-only dashboard | **Offline half complete, both planes** | `claude/p2-publisher`, `claude/p2-replica` | CI **success** both, pushed |
| **P4** Pro | **Engine half complete; Android half not started** | `claude/p4-entitlement` (**unpushed**) | pin 500 **(claimed)**, no CI |
| **P5** Store readiness | **Staged; device- and account-gated remainder** | `claude/p5-store` | CI **success**, pushed |
| **P3** Document view/edit | **Not started** | — | — |
| **P6** Launch | Not started; pricing rewrite is a **blocker** | — | — |

P3 being unstarted while P4 and P5 progressed is deliberate, not drift: P4's outcome tracking
and P5's store artifacts need neither the document pipeline nor a handset, so they were
parallelized while P3 (the Dispatcher-adjacent phase) waits for a high-effort session.

## 2. What landed since the 2026-07-24 checkpoint

### P2 — closed out (both planes, CI green, pushed)

Engine `claude/p2-publisher` (7 commits past P1): payload builders → `SyncPublisher`
(seals/sequences/pushes e2p) → `EngineSyncBridge` wired into the host behind `--sync`
(**default OFF**) → live snapshot+delta round-trip → `evidence` payload (audit verdict +
event metadata) → checkpoint fixes F1/F2/F4/F6 → **Codex audit fix** (`7158202`: a failed first
snapshot is retried, never demoted to a delta).

Android `claude/p2-replica` (6 commits past P1): Room replica + envelope applier proven
against real SQLite → demo fixture → the five read-only screens rendered in CI → evidence
projection → F3 `doc_kind` reconciliation → **Codex audit defense** (`d9f95fd`: the first real
payload wipes demo data rather than merging into it).

Offline pin moved **395 → 437** across P2 **(claimed** in commit bodies; branch CI is green,
which exercises the pinned total, so this one is corroborated by the CI conclusion).

### P4 — engine half complete, unpushed

Eight commits on `claude/p4-entitlement`, stacked on P2:

- §2.2 entitlement body `{voucher}` → `{original_json, signature}` + five Play-signed vectors
- §2.3 `GoogleSignedPayloadVerifier` (option C — no second server) + `EntitlementService`
- §2.4 inbound p2e dispatcher + store-backed entitlement state; live round-trip
  (`SyncLiveSmoke` 22 → 30 **(claimed)**)
- §2.5a–c outcome-tracking store migration → wire field + `StoreOutcomeApplier` → funnel
  computation → **desktop funnel board + outcome-marking controls**

New engine sources: `EntitlementService.cs`, `GoogleSignedPayloadVerifier.cs`,
`InboundDispatcher.cs`, `SyncPublisher.cs`. Pin **437 → 500 (claimed)**; `EngineHarness`
123 → 129 **(claimed)**. **No CI has run on this branch** because it has never been pushed, and
there is **no `docs/P4-Evidence.md`** — a departure from the P1/P2/P5 convention.

The strategic result is real: *"the honest core of Pro ships complete on the desktop"* — no
phone, no Play account, no pairing required. That was the point of sequencing outcome tracking
before billing.

### P5 — staged, gated on a handset and on account day

Five commits on `claude/p5-store` (stacked on P2's Android tip), all CI green:

- `docs/store/` dossier: `Play-Data-Safety.md`, `Play-Listing.md`, `Privacy-Policy-Delta.md`,
  `Account-Day-Checklist.md`, `Accessibility-Pass.md`
- Accessibility: additive semantics across the five screens + Robolectric assertions
- Assets: icon + feature graphic rebranded to the official logo (PNG + SVG)
- Site reskin staged under `docs/store/site-reskin/`, plus
  `docs/todo/Design-Language-Implementation.md` for post-P4
- `docs/P5-Evidence.md` — every Play-policy claim carries a dated citation and quoted text

P5's evidence file is candid about its limits: the Pixel 10 was not connected
(`adb devices` empty), so **real screenshots and on-device TalkBack are device-gated**, and no
screenshot was fabricated. It also records that the engine repo was read-only for that session,
so the drift trap was never contended.

## 3. Findings

**F-A (high) — unpushed work.** §0. Two branches, one of them the whole P4 engine. Acting on
this is cheap now that push works.

**F-B (medium) — no draft PRs for P2, P4, or P5.** Open PRs are still only #5/#6 (engine
P0/P1) and #1/#2/#3 (android P0/P1). Three phases of completed, CI-green work have no review
surface. The 2026-07-24 checkpoint recorded Codex's verdict as *"go to open draft PRs"* after
the demo-boundary fix — that fix landed (`7158202`, `d9f95fd`), so **the condition for opening
them has been met and they were simply never opened.**

**F-C (medium) — P4 has no evidence doc.** P1, P2, and P5 each have one; P4's verification
lives only in commit bodies. Its own final commit lists `§2.7 evidence/PRs` as remaining, so
this is known-incomplete rather than forgotten — but it means P4's pin-500 claim is currently
unreproduced by CI or by an independent run.

**F-D (low) — branch/worktree hygiene.** The engine worktree sits on
`claude/android-apk-build-setup-90d9d5` (the P0 branch), which is why P1/P2/P4 files read as
"reverted" to a P0 shape in a stale editor buffer. Nothing is lost; it is a checkout state.
P5 used a separate worktree (`careerseeker-android-p5`) and its evidence says to remove it at
session end — worth confirming it is gone.

**F-E (context) — a fourth track exists.** `fix/engine-actually-runs` (2026-07-28, newest
commit in either repo) is **alpha-track**, not Android-program: it fixes the engine's
all-zeros bug (the bridge launched an engine-less `dashboard` mode with a hard-coded
"running"). It matters here because the Android program's publisher rides on that engine, and
because it is the second unpushed branch.

## 4. Immediate next steps

1. **Push the two branches** (§0). Highest value per second spent.
2. **Open draft PRs** for `claude/p2-publisher`, `claude/p2-replica`, `claude/p5-store`, and
   `claude/p4-entitlement` once pushed — stacked on their parents, never self-merged.
3. **Write `docs/P4-Evidence.md`** and let CI reproduce the pin-500 claim (P4 §2.7).
4. **P4 §2.6** — Android `EntitlementService` + Pro screen + outcome UI. Offline-buildable;
   does not need the Play account.
5. **P3** — document view/edit. The largest untouched phase and the invariant-sensitive one
   (Dispatcher-adjacent). Wants a high-effort session and its own runbook first.

**Gated, not forgotten:**

- *Device-gated:* P2 device finale (camera/Keystore pairing UI, desktop `/pair` page), P5 real
  screenshots + TalkBack pass.
- *Account-gated:* Play Console setup, D-U-N-S verification, real Play license key, live
  billing — `docs/store/Account-Day-Checklist.md` is the script for that day.
- *Decision-gated:* the three P2 gates (P2-KEYSTORE-FALLBACK, P2-PIN-ROTATION,
  P2-REPLICA-CRYPTO) and P4-APPID.
- *Launch-blocking:* the pricing-page rewrite — Pro at $2.99 falsifies "our only revenue" and
  "the only money we ever ask you for" **independent of Cloud**. TODO already written at
  `docs/todo/Pricing-Page-Rewrite.md`.

## 5. What has held up well

The machinery-over-memory bet is paying: the pinned offline total caught every count drift
across three phases; the shared vectors kept three independent implementations agreeing; the
structural CI checks (resolved classpath, storage schema, no-decryption grep) have not produced
a false pass. Both Codex audit findings this cycle were *behavioral* — a first-snapshot failure
demoting later publishes, and demo data surviving a real payload — the class of defect that
tests-in-code can miss and an adversarial reader catches. That division of labor is working;
keep sending completed phases to Codex before merge, which is exactly what F-B is currently
blocking.
