# Merge topology — measured, 2026-08-09

Return-day decision aid. Brandon merges; this document only measures. Every number below was
produced by a command in [`AUDIT-REQUEST.md`](../AUDIT-REQUEST.md) §C-MT, run in this session
against refs taken **after** `git fetch --all --prune`.

Written because the "two-lineage merge hazard" has been carried as a *prediction* since S0 and has
been shaping decisions ever since — S7 skipped creating `docs/store/` specifically to avoid
"manufacturing a merge conflict" on top of it. A prediction that steers work for three weeks is
worth replacing with a measurement.

**Headline: the hazard is not a conflict hazard.** Eight of the nine branches merge into `main`
without a single textual conflict. There is exactly **one** conflicting file in the whole
repository, and it is a documentation file carrying a *product decision*, not code.

---

## 1. The branches

`origin/main` = `ebfaf81` (2026-08-06, docs-only). Every branch is **10 behind** it; those 10 are
docs-only commits that no feature branch has ever contained.

| Branch | Tip | Ahead | Last commit | PR |
| --- | --- | --- | --- | --- |
| `claude/p0-scaffold` | `59051a4` | 15 | 2026-07-23 | [#1](https://github.com/ShivaClaw/careerseeker-android/pull/1) → `main` |
| `claude/p1-runbook` | `ec0f73e` | 3 | 2026-07-23 | [#2](https://github.com/ShivaClaw/careerseeker-android/pull/2) → `main` |
| `claude/p1-pairing` | `3477b41` | 17 | 2026-07-23 | [#3](https://github.com/ShivaClaw/careerseeker-android/pull/3) → `p0-scaffold` |
| `claude/p2-replica` | `d9f95fd` | 23 | 2026-07-24 | [#4](https://github.com/ShivaClaw/careerseeker-android/pull/4) → `p1-pairing` |
| `claude/p4-pro` | `d9f95fd` | 23 | 2026-07-24 | *(none — see §2)* |
| `claude/p5-store` | `bb7f4d0` | 28 | 2026-07-25 | [#5](https://github.com/ShivaClaw/careerseeker-android/pull/5) → `p2-replica` |
| `claude/android-a0-probe` | `5714733` | 40 | 2026-08-09 | [#6](https://github.com/ShivaClaw/careerseeker-android/pull/6) → `p2-replica` |
| `claude/p2-runbook` | `2f4cf87` | 1 | 2026-07-23 | *(none)* |
| `claude/todos-pq1-pricing` | `e9ce010` | 1 | 2026-07-23 | *(none)* |

## 2. `claude/p4-pro` is not a separate branch

`p4-pro` and `p2-replica` are **the same commit** — both `d9f95fd`. The P4 Pro work is already
inside the `p2-replica` tip, which is why PR #4 is titled for P2 and no PR exists for P4.

There is nothing to merge for P4 separately, and nothing missing. Recorded because "where did P4
go?" is otherwise a question someone will spend an hour on.

## 3. The stack is clean, and it is a real stack

Each PR's base **is** an ancestor of its head, so #1 → #3 → #4 → {#5, #6} is a genuine stack, not a
set of branches that merely look stacked:

```
main ebfaf81 ─────────────────────────────────────────── (docs-only, 10 commits nobody has)

p0-scaffold 59051a4  ⊂  p1-pairing 3477b41  ⊂  p2-replica d9f95fd  ┬─ p5-store  bb7f4d0  (#5)
     (#1)                    (#3)                   (#4)           └─ a0-probe  5714733  (#6)
                                                 == p4-pro

p1-runbook ec0f73e (#2) ─┐
p2-runbook 2f4cf87       ├── independent of the stack, 1–3 commits each
todos-pq1-pricing e9ce010┘
```

`main` is **not** an ancestor of any of them, and none of them is an ancestor of `main`: the
divergence is real, but it is additive on both sides.

## 4. Simulated integration — what actually happens

Merging the stack into `main` in order, carrying each result forward (`git merge-tree
--write-tree`, the same ort strategy a real `git merge` uses):

| Step | Result |
| --- | --- |
| `main` ← `p0-scaffold` | **clean** |
| ← `p1-pairing` | **clean** |
| ← `p2-replica` | **clean** |
| ← `p5-store` | **clean** |
| ← `android-a0-probe` | **clean** |
| ← `p2-runbook` | **clean** |
| ← `todos-pq1-pricing` | **clean** |
| ← `p1-runbook` | **CONFLICT** — one file (§5) |

The integrated tree contains everything expected: `app/`, `core/`, the full `docs/store/` dossier
from #5, `docs/todo/Pricing-Page-Rewrite.md`, and this branch's records
(`STATE.md`, `LOG.md`, `AUDIT-REQUEST.md`, `BLOCKED.md`, `docs/S-Ladder.md`).

No branch was created, moved, or pushed to produce this. The simulation writes dangling objects
only.

## 5. The one conflict: `docs/Monetization-Decision.md`

An **add/add** conflict — `p1-runbook` and `p0-scaffold` each created this path independently:

- `p0-scaffold` `59051a4` "Record P-MONEY: Basic free / Dashboard $4.99 / Pro $2.99 / Cloud $1.99mo"
  (4,706 bytes, blob `973a1dc` — this is the version on `a0-probe` today)
- `p1-runbook` `ec0f73e` "Record P0-WORKER (option C) and the CareerSeeker naming decision"
  (4,755 bytes, blob `2322ce8`)

They differ in **two places, 9 insertions / 12 deletions**, and both are the same question:

| | `a0-probe` lineage (older) | `p1-runbook` (newer) |
| --- | --- | --- |
| Price table row | "**CareerSeeker Basic** (.exe)" | "**CareerSeeker** (.exe) — *the product*, not a tier" |
| §3 heading | "Naming note (worth a decision, not urgent)" | "Naming — **decided** 2026-07-23" |

**This is not a textual accident; it is an unresolved product decision.** `p1-runbook` records the
naming as settled — the Windows app is "CareerSeeker", never "CareerSeeker Basic", because a tier
name implies withheld features and contradicts the page's strongest promise. The lineage carrying
all the recent work still says the question is open, and still prints "Basic" in the price table.

**Why it matters beyond one file.** The store copy derives from this table.
`docs/store/Play-Listing.md` and the pricing rewrite are downstream of a name that one branch
considers rejected. Resolving the conflict by taking either side silently would either re-open a
closed decision or quietly close an open one.

**Recommendation (Brandon's call):** take the `p1-runbook` side. It is the later, more specific
record, and it states a decision rather than a suggestion. Then re-read `docs/store/Play-Listing.md`
for the word "Basic" before submission.

## 6. The real hazard is a clean merge, not a dirty one

`p5-store` (#5) and `a0-probe` (#6) fork at `d9f95fd`. Both modify the **same three files**:

| File | `p5-store` | `a0-probe` | Merge |
| --- | --- | --- | --- |
| `ui/HomeScreen.kt` | +11 / −2, 3 hunks | 0 / −1, 1 hunk | auto-fused, no conflict |
| `ui/ApplicationsScreen.kt` | +19 / −2, 4 hunks | +27 / −0, 2 hunks | auto-fused, no conflict |
| `test/…/ScreensFromFixtureTest.kt` | +53 / −0, 3 hunks | +32 / −2, 4 hunks | auto-fused, no conflict |

The earlier record (LOG.md, HUMAN-QUEUE item 1) called these three files a *collision*. That is
accurate as **file overlap** and imprecise as **conflict**: git resolves all three without asking,
because the two sides edited different regions.

That is the risk, not the reassurance. Two independently-evolved sets of screen edits and two
independently-written test sets get fused by a strategy with no opinion about whether the result is
coherent — and **no gate has ever run on the fused tree.** CI runs per-branch; the combination in
§4 has never been built, never been tested, and never been linted anywhere.

This is the same failure mode already recorded as the first of LOG.md's "three things most worth
distrusting" (`Host.cs`, merged by hand twice, compiled fine, intent unverified) — and the second
one is the precedent that matters: P4's Pro assertions compiled perfectly and then killed the
harness on a hard-coded port. **A clean merge is not a passing gate.**

**Therefore:** whoever performs the integration must run the verification command of record on the
merged tree, not on the branches:

```
./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug --rerun-tasks
```

## 7. Recommended merge order

Not performed. The android repo is **never-self-merge** and every PR here is a draft.

1. **#1 `p0-scaffold` → `main`** — clean, and it is the base of everything.
2. **#2 `p1-runbook` → `main`** — resolve the §5 conflict; decide the name.
3. **#3 `p1-pairing` → `p0-scaffold`** (or retarget to `main` once #1 lands).
4. **#4 `p2-replica`** — carries P4 (§2).
5. **#5 `p5-store`** and **#6 `a0-probe`** — order between them does not matter for conflicts;
   whichever lands second inherits the §6 auto-fusion. **Run the full gate after the second one.**
6. `p2-runbook` and `todos-pq1-pricing` — 1 commit each, clean, no PR open. Decide whether they want
   PRs or should be folded in.

Each branch is 10 behind `main`; merging **into** `main` absorbs that automatically. Nothing here
needs a rebase, and nothing here needs a force-push.

## 8. Cross-repo pin — verified, unchanged

`core/src/test/resources/sync-vectors/` is vendored from `ShivaClaw/careerseeker` at pin
`679a3175590dcd021b21c85af9daf12114e131fd`. Blob-by-blob against that commit:

**26 identical, 0 differing, 0 missing.**

Upstream `docs/sync-vectors/v1/` now holds **28** files — the two `entitlement-ack` vectors added by
[careerseeker#32](https://github.com/ShivaClaw/careerseeker/pull/32) (still a draft, unmerged). The
26/28 gap is the pin doing its job, not drift. Re-vendoring belongs in the same slice as the Kotlin
applier that consumes the new files.

## 9. What this document does not establish

- **Not that the integrated tree builds.** It has never been compiled. No Android SDK, JBR, Gradle
  or emulator exists in the environment that produced this file; §6 exists precisely because that
  claim is unavailable here.
- **Not semantic correctness of any merge.** `git merge-tree` detects textual conflicts. It cannot
  see two branches editing *different* files that must agree — the exact class of bug the
  doc/verifier drift trap exists for.
- **Not that PR bases are what they should be.** #3–#6 target sibling branches. Whether to retarget
  them at `main` as the stack lands is a decision, not a measurement.
- **Not a merge.** Nothing was merged, rebased, retargeted, force-pushed, or deleted.
