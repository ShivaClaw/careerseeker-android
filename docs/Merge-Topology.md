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

There is nothing to merge for P4 separately, and nothing missing. **First measured at S0**
(`LOG.md:954`, `S-Ladder.md:36`), re-confirmed here and repeated because this is the document
someone will read *while merging*, which is the moment "where did P4 go?" costs an hour.

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

S0 computed this set correctly as an intersection of both diffs, and also corrected the mission's
prediction that `ApplicationDetailScreen.kt` would be in it — it is not. What S0 did not do is run
the merge. The earlier record (LOG.md, HUMAN-QUEUE item 1) called these three files a *collision*,
which is accurate as **file overlap** and imprecise as **conflict**: git resolves all three without
asking, because the two sides edited different regions.

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

## 8. Cross-repo pin — verified

> **Corrected 2026-08-16, forty-fifth run.** Everything below the rule described the tree as it
> stood *before* `056a1dd` (2026-08-12 21:11) re-vendored the three post-pin vectors. This section
> was edited two days after that commit and still reported the pre-re-vendor state, so it named the
> wrong pin, the wrong vendored count, and asked for a re-vendor that had already happened.
> `VECTORS.lock` — which is what CI actually reads — was correct the whole time. **The lock is
> authoritative; this section is commentary.** Measured state as of the forty-fifth run:

`core/src/test/resources/sync-vectors/` is vendored from `ShivaClaw/careerseeker` at pin
**`7328a0bc043335491cd96a67d634e8eea2a13af9`** (moved from `679a317` on 2026-08-12; both are
off-`main`, see §8.1). Blob-by-blob against that commit:

**29 identical, 0 differing, 0 missing.** Upstream `docs/sync-vectors/v1/` holds **29** files at
that pin. The 26/29 gap this section used to report is **closed** — re-vendoring did not wait for
the Kotlin applier. Re-verify: **C-PIN-1**.

**The restack in §10 still cannot cause drift**, and that half was measured correctly: `origin/main`
has touched no vector file at all since the stack forked, and the stack's whole effect on
`docs/sync-vectors/` is **three added payloads plus the `index.json` manifest** — **zero existing
payloads modified**. Re-verify: **C-RST-7**.

### 8.1 One correction the lock itself needs

`VECTORS.lock` says the 26 previously-vendored files "are byte-identical across `679a317`,
`origin/main`, and `7328a0b`". Measured this run, that is true of **25** of them: `index.json` is a
**manifest**, and it necessarily changed when the three vectors were added. No existing *payload*
changed — which is the claim that actually carries the safety guarantee, and is how this section
words it above. The lock's wording is one word too strong; the guarantee is intact. Re-verify:
**C-PIN-2**.

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

## 10. The engine repo's `claude/s2-*` stack — costed, 2026-08-14 (thirty-sixth run)

Sections 1–9 measure **this** repo. This section measures the **engine** repo
(`ShivaClaw/careerseeker`), because `STATE.md`'s ordered intent carried "the restack is real work
that is growing, and **no run has yet costed it**" for three revisions. It is costed here. As
everywhere in this document: **measured, not merged.** Refs taken after `git fetch --all --prune`;
`origin/main` = `aac05f3`.

### 10.1 It is not sixteen deep, and it is not a line

The record said "sixteen PRs deep". Measured: **eleven** open chained PRs (#32–#39, #45–#47) plus
**#48** standalone, and they form a **tree of depth 7**, not a chain of sixteen:

```
main (aac05f3)
└── #32 s5-entitlement-ack-spec        (4)
    ├── #33 s4-pull-request-semantics  (12)
    │   └── #36 s2-transport-vocabulary (15)   ← see §10.5
    ├── #34 s2-relay-retention         (6)
    │   └── #35 s2-seq-bound           (9)
    └── #37 s5-engine-wire-parser      (8)
        └── #38 s5-entitlement-ack-emitter (12)
            └── #39 s5-inbound-pump    (17)
                └── #45 s2-relay-pull-result (26)
                    └── #46 s6-counter-reconciliation (37)
                        └── #47 s2-push-disposition   (43)
```

Every one of the eleven forks from the **same** commit `00b3705`, and `origin/main` is **16 ahead**
of it. #48 is off **fresh** main: 1 commit, **0 behind**, merge-probe **clean** — it is unaffected by
everything below. Re-verify: **C-RST-1**, **C-RST-2**, **C-RST-9**.

### 10.2 The entire cost is the pin, and nothing else

`git merge-tree` against `origin/main`, per branch — and the correlation is exact:

| PR | Ahead | Pin sweeps | Merge probe |
| --- | --- | --- | --- |
| #32 | 4 | 0 | **CLEAN** |
| #33 | 12 | 0 | **CLEAN** |
| #34 | 6 | 0 | **CLEAN** |
| #35 | 9 | 0 | **CLEAN** |
| #36 | 15 | 0 | **CLEAN** |
| #37 | 8 | 1 | conflicts |
| #38 | 12 | 2 | conflicts |
| #39 | 17 | 3 | conflicts |
| #45 | 26 | 6 | conflicts |
| #46 | 37 | 9 | conflicts |
| #47 | 43 | 11 | conflicts |

**Conflicts appear exactly where a pin sweep does, and nowhere else.** The conflicting set is the
*same five files* for every branch from #37 on, and it is the drift trap's own file family:
`README.md`, `docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`,
`scripts/Verify-Alpha.ps1`, `src/Engine/README.md`.

**Five of the eleven PRs have zero restack cost.** Re-verify: **C-RST-3**, **C-RST-4**.

**The code half is free.** `src/Engine/Host.cs` (+134 on main) and `src/Engine/Program.cs` (+95)
**auto-merge** — they appear in the probe as `Auto-merging`, not `CONFLICT`, despite main having
rewritten both. No `src/Sync/`, no `relay/`, no test file conflicts anywhere. Re-verify: **C-RST-6**.

### 10.3 The conflict is additive, and it resolves to one derivable number

This is the part worth reading before touching it. Both sides moved the **same** counter from the
**same** base, by editing **different** harnesses:

| | EngineHarness | SyncHarness | `$ExpectedOfflineTotal` |
| --- | --- | --- | --- |
| fork point `00b3705` | 217 | 130 | **598** |
| `origin/main` `aac05f3` | **230** | 130 | **611** (+13, the `/pair` page, PR #42) |
| stack tip #47 `1951313` | 217 | **325** | **793** (+195) |
| **restacked** | **230** | **325** | **806** ← derived |

The two deltas are **disjoint** — main moved `EngineHarness` only, the stack moved `SyncHarness`
only — so **neither side's number is the answer and "take theirs"/"take mine" are both wrong.**
The resolution is arithmetic: `598 + 13 + 195 = 806`.

**806 is DERIVED, NOT MEASURED.** `Verify-Alpha.ps1` cannot run in a Linux sandbox and did not run.
It **throws** on a pin mismatch, so a wrong value is a hard CI failure rather than silent drift —
here the drift trap is protective, and it is the thing that will confirm or refute 806 on
`windows-latest`. Re-verify: **C-RST-5**, and settle with **C-RST-11** on Windows.

The conflicting hunks also carry **prose**: each sweep's comment block explains what it added. Both
sides' prose must be **kept**, not chosen between. This is not a pure number merge.

### 10.4 Merging costs 5 resolutions; rebasing costs 55 — an 11× difference

The two integration strategies do **not** cost the same, and the record never separated them:

- **Merge into `main`** (as §7 recommends for this repo): the cumulative tree conflicts **once**,
  on **5 files**. Intermediate pin values never surface.
- **Rebase** (as S1 used for PRs #27–#30): each of the **11 pin-sweep commits** conflicts in turn —
  every sweep states a *from* value that the previous resolution has just invalidated, so the
  arithmetic is re-derived at each step. **11 sequential resolutions × 5 files = 55 hunks**, to
  reach the same tree.

The 11 intermediate values are **bookkeeping**: CI runs on PR heads, never on a stack's interior
commits, so no intermediate pin was ever independently gated. **A local session may therefore
collapse the 11 sweeps into one sweep at the tip** and pay the 5-hunk cost instead of 55 — but that
rewrites draft branch history, which this session is forbidden from doing and did not do.

**Growth rate, now quantified:** the stack gained **one conflict per assertion-adding run** — 11
sweeps across 11 such runs. The cost grows linearly in *runs*, not in PRs, which is why "before it
is twenty deep" measured the wrong axis.

### 10.5 One anomaly: #36's declared base is not its actual base

GitHub declares #36's base as `claude/s4-pull-request-semantics` (#33), but **#36 does not contain
#33's tip.** #36 forked at `b114d11`; #33 has since gained **one** commit, `3a8dfdd`
("S4/S5: 6.4's carve-out was drawn at the parse, and a failed tag fell through it — PQ-CUR-1").

Restacking #36 onto a rebased #33 **includes** `3a8dfdd`; restacking it onto its actual fork point
silently **drops** that commit from #36's line. The two are not the same tree, and the PR page shows
no sign of the difference. Re-verify: **C-RST-8**.

### 10.6 What this section recommends (it does not decide)

1. **#48 first** — off fresh main, clean, independent, and it unblocks `EngineHarness` on Linux
   (B-10). Nothing below affects it.
2. **#32 → #34 → #35, and #32 → #33 → #36**, in that order: **zero conflicts**, subject only to the
   local gate. Fixing #36's base (§10.5) is a prerequisite for #36, not for the others.
3. **#37 → #47 last**, as one costed unit, resolving the pin **once** to a value the gate measures.
   **Prefer merge over rebase** — §10.4 is an 11× difference for an identical tree.
4. The gate is Brandon's and the merge condition is a **full local** `Verify-Alpha.ps1
   -IncludePublish -IncludePackage`, which no cloud session can run. **Nothing here is a
   go-ahead.**

---

## 11. The thirteenth leaf: leaf-vs-leaf cost, measured 2026-08-16 (forty-fourth run)

§10 costed the fleet by probing **each branch against `origin/main`**. That is the right probe for
"what does this branch cost to land on main", and its numbers still reproduce. It is not the probe for
"what do two unmerged branches cost each other", and **nothing in §10 took that measurement.**

PR **#53** (`claude/s6-resume-reconciliation`) was opened **2026-08-16**, two days after §10 was
written, cut **depth 1 off `origin/main`** and deliberately not stacked. §10.2's audit could not have
included it. This section adds the missing dimension and records what it found.

### 11.1 The measurement

`scripts/fleet-probe.sh matrix ../careerseeker claude/s6-resume-reconciliation` — every remote branch
probed against #53 with `git merge-tree`, source conflicts counted apart from the pin/doc family:

| Branch | PR | SRC | DOC | Conflicting source/test files |
| --- | --- | --- | --- | --- |
| `claude/s2-relay-pull-result` | #45 | **4** | 5 | `Program.cs`, `RelayClient.cs`, `SyncHarness`, `SyncLiveSmoke` |
| `claude/s6-counter-reconciliation` | #46 | **5** | 5 | + `SyncPublisher.cs` |
| `claude/s2-push-disposition` | #47 | **5** | 5 | + `SyncPublisher.cs` |
| `claude/s6-composition-root-decision` | #49 | **5** | 5 | + `SyncPublisher.cs` |
| `claude/s5-inbound-pump` | #39 | 2 | 5 | `Program.cs`, `SyncHarness` |
| `claude/s5-entitlement-ack-emitter` | #38 | 1 | 5 | `SyncHarness` |
| `claude/s3-pairing-confirm-consumer` | #51 | 1 | 5 | `SyncHarness` |
| `claude/s5-engine-wire-parser` | #37 | 0 | 5 | — |
| `claude/s6-outcome-disposition` | #52 | 0 | 5 | — |
| #32, #33, #34, #35, #36, #48, #50 | | **0** | **0** | — |

**§10.2's sentence "No `src/Sync/`, no `relay/`, no test file conflicts anywhere" is true of the probe
it describes and false of the fleet as it now stands.** Four branches conflict with #53 inside
`src/Sync/`, and four more conflict in a test file. The seven branches §10.2 called zero-cost are
still zero-cost, against #53 as well as against main — that half of §10 is reconfirmed, not overturned.

### 11.2 Why: #53 re-implemented work the fleet already held

`scripts/fleet-probe.sh symbol ../careerseeker ReconcileTo RelayPushResult PushOutcome`:

```
=== 'ReconcileTo' ===        s2-push-disposition, s6-composition-root-decision, s6-counter-reconciliation
=== 'RelayPushResult' ===    s2-push-disposition, s2-relay-pull-result, s6-composition-root-decision,
                             s6-counter-reconciliation
=== 'PushOutcome' ===        s6-resume-reconciliation          <- one branch, and it is #53
```

Both lines describe the same defect — *the 409's `latest` was discarded unread* — solved twice, in
shapes that cannot both exist:

| §6.1 piece | #45/#46 stack | #53 |
| --- | --- | --- |
| typed push result | `RelayPushResult`, abstract record, 7 cases (#45, `e083f86`) | `PushOutcome(PushStatus, long? Latest)` |
| startup resume rule | `ResumeSeq(long, RelayPullResult)` | `ResumeFrom(long, long?)` |
| runtime reconcile | `ReconcileTo(long)` + `RelaySink`, call site mutation-tested (#46, `6c3f8bb`) | **absent** |
| sink composition | extracted `RelaySink.cs` + `SyncPushPath.cs` | inline lambda in `Program.cs` |

**#53 holds two of the three pieces in its own shape and lacks the third** — and the third is exactly
what `STATE.md`'s ordered ITEM 1 asked the forty-fourth run to write. Writing it would have completed
a second parallel implementation of PR #46, which has been open since **2026-08-14**.

The cause is not carelessness, and naming it as such would hide it. Thirteen draft PRs are open and
none is merged, so **`origin/main` is not the state of the program.** A session that derives "what is
still missing" by reading main — the honest, obvious move, and the one the house rules push toward —
sees every solved-but-unmerged problem as open. Cutting #53 depth-1 off main *to keep the pin conflict
additive* is what made the duplication invisible.

### 11.3 The pin arithmetic §10.3 promises does not survive this

§10.3's additive resolution holds when both sides add **distinct** assertions to a counter from a
common base. Pins measured this run: main **611**, #53 **627**, #45 **704**, #46 **762**, #47/#49 **793**.

#53's `+16` and the stack's `+182` are **not** disjoint: both include assertions covering the same
push-answer behaviour through incompatible APIs (#46's line `acf9ebe` reads *"31 offline assertions for
the push result"*). Resolving `RelayClient.cs` means choosing one representation and **deleting the
other's assertions**, so the merged total is not `611 + 16 + 182`. **Derived, not measured** — the
number is whatever the gate reports after the design choice, and it cannot be derived before it.

### 11.4 What this section recommends (it does not decide)

§10.6's order is unaffected for #48, #32→#34→#35, and #32→#33→#36 — all still zero-cost.

1. **#53 is not in §10.6's plan and should not be merged as a thirteenth leaf.** Two typed push
   results in one tree is not a merge conflict to resolve; it is a design choice to make.
2. **The stack's shape is the more developed one** on every axis measurable here: `RelayPushResult`
   carries 7 cases against `PushStatus`'s 6, `ReconcileTo` exists at all, its call site is
   mutation-tested, and its composition is extracted to `RelaySink`/`SyncPushPath` rather than inline
   in `Program.cs`. **On the evidence, #53 should be closed or reduced to whatever #45/#46 lack, not
   landed alongside them.** That is a recommendation; the choice is Brandon's, and §10's own
   distinction between a recommendation and a decision is load-bearing here.
3. **Before any future session writes engine sync code, run `scripts/fleet-probe.sh symbol` first.**
   The duplication cost two iterations and would have been caught by one command.

Re-verify: **C-FL-1 … C-FL-6**.

---

## 12. The landing sequence, measured 2026-08-16 (forty-seventh run)

§10 probed each branch against **pristine `origin/main`**. §11 probed one leaf against every other
leaf. **Neither probed the sequence a human actually performs**, and that is the number §10.4 states:

> **Merge into `main`** … the cumulative tree conflicts **once**, on **5 files**.

**That sentence is true of the single chain §10 costed and false of the fleet as it now stands.**
Measured this run, and the difference is not a rounding error — it is 1 versus 3.

### 12.1 Seventeen open PRs are seven merges

A **leaf** is a fleet branch contained in no other. Merging a leaf lands every PR beneath it, so the
leaf count — not the PR count — is the number of merges performed. `merge-base --is-ancestor`,
every check exit 0:

| Leaf (PR) | Subsumes |
| --- | --- |
| `s6-composition-root-decision` (#49) | #47, #46, #45, #39, #38, #37, #32 |
| `s2-transport-vocabulary` (#36) | #33, #32 |
| `s2-seq-bound` (#35) | #34, #32 |
| `s3-pairing-confirm-consumer` (#51) | #50 |
| `s8-harness-linux-reach` (#48) | — |
| `s6-outcome-disposition` (#52) | — |
| `s6-resume-reconciliation` (#53) | — |

**17 open PRs → 7 merges.** Re-verify: **C-LAND-1**.

`scripts/fleet-probe.sh leaves` derives this, and prints one extra name: `claude/p4-entitlement`,
a leaf with **no open PR** — the pre-S1 branch whose successors landed as #27–#30. **A leaf is not
an open PR**; cross-check the two lists before reading the output as a plan. Re-verify: **C-LAND-2**.

### 12.2 Isolated cost says six of seven are free. The sequence says otherwise.

Each leaf merged into **pristine** `origin/main`, working-tree merge, aborted after each:

| Leaf | Isolated |
| --- | --- |
| #48, #35, #36, #51, #52, #53 | **clean** |
| #49 | conflicts, **5 files** — exactly §10.2's pin family |

That reproduces §10.2 and is where §10.4's "conflicts once" comes from. Now the same seven merged
**cumulatively**, each onto the result of the last (`scripts/fleet-probe.sh land`):

```
  s8-harness-linux-reach                   clean
  s2-seq-bound                             clean
  s2-transport-vocabulary                  clean
  s3-pairing-confirm-consumer              clean
  s6-outcome-disposition                   STOP  README.md … scripts/Verify-Alpha.ps1 …
  s6-resume-reconciliation                 STOP  … tests/SyncHarness/Program.cs
  s6-composition-root-decision             STOP  … src/Sync/RelayClient.cs src/Sync/SyncPublisher.cs …

conflicted merges (human stops): 3
```

**Three stops, not one.** Re-verify: **C-LAND-3**.

### 12.3 Why: the pin is an absolute number, so pin-touchers collide pairwise

Four of the seven leaves move `$ExpectedOfflineTotal`, each to a **different absolute value**:

| Leaf | merge-base | tip | delta |
| --- | --- | --- | --- |
| `origin/main` | — | **611** | — |
| #51 `s3-pairing-confirm-consumer` | 611 | 617 | +6 |
| #52 `s6-outcome-disposition` | 611 | 615 | +4 |
| #53 `s6-resume-reconciliation` | 611 | 627 | +16 |
| #49 `s6-composition-root-decision` | **598** | 793 | +195 |

The other three (#48, #35, #36) do not touch the file at all — #35/#36 show `598` only because they
inherit it from the merge base; `main` moved it, they did not, so the merge is clean. Re-verify:
**C-LAND-4**.

Because the pin is an absolute value rather than a delta, **the first pin-toucher to land is free and
every subsequent one conflicts.** N pin-touchers cost **N−1** stops. That is structural, not a
property of any one branch, and it is why §10.2's per-branch probe could not see it: against pristine
`main`, each of these four *is* the first.

**Order changes the count.** Landing #49 first costs **4** stops, not 3 — #49 forked at `598` and
`main` has since moved to `611`, so it conflicts even as the first merge and forfeits the free slot.
**Land a fresh-off-`main` pin-toucher first.** Re-verify: **C-LAND-5**.

### 12.4 Closing #53 removes a stop *and* the whole `src/Sync/` conflict class

§11.4 recommends #53 be closed or reduced rather than landed. The landing cost now quantifies that
recommendation. Same order, #53 omitted:

```
conflicted merges (human stops): 2
```

and the final merge's conflict set loses `src/Sync/RelayClient.cs`, `src/Sync/SyncPublisher.cs`,
`src/Engine/Program.cs` and `tests/SyncLiveSmoke/Program.cs` — **the source-level collisions
disappear entirely, leaving only the pin family plus `tests/SyncHarness/Program.cs`.** That is
§11.2's duplication finding, reproduced from the other direction: those files conflict *because* two
branches implement the same push-result design twice. Re-verify: **C-LAND-6**.

### 12.5 What this section does NOT establish

- **The file lists after the first STOP are probe artifacts, not forecasts.** To continue past a
  conflict the probe keeps `merge-tree`'s conflicted tree — markers and all — as the next merge's
  base. A human resolving properly would produce a different, smaller downstream conflict set. **The
  count of stops is the robust number; the growing file lists are not.** This is stated because a
  reader comparing 12.2's 10-file last line against §10.2's 5-file row would otherwise conclude the
  cost had doubled. It has not; the two lines answer different questions.
- **No gate ran.** `scripts/Verify-Alpha.ps1` needs PowerShell and .NET; this sandbox measured
  **neither `pwsh` nor `dotnet` on `PATH`**. Re-verify: **C-LAND-7**.
- **The landed pin value is still not derivable.** §10.3 predicted `806` for the chain alone;
  adding #51/#52/#53's disjoint deltas would arithmetically give `832` — but §11.3 already showed the
  deltas are **not** disjoint once #53 is in, and resolving `RelayClient.cs` means deleting one
  side's assertions. **The landed total is whatever the Windows gate measures after the design choice
  in §11.4 is made. Do not pre-fill it.**
- **Nothing here is a go-ahead.** No merge was performed. Every probe ran on a throwaway ref or in
  the object store; the scratch branch was deleted and nothing was pushed to either repo.

### 12.6 What this section recommends (it does not decide)

1. **Decide #53 first** (§11.4). It is worth one fewer stop and the entire `src/Sync/` conflict class.
2. **Then land in this order**, which is the measured minimum:
   `#48 → #35 → #36 → #51 → #52 → #49`, with a full local gate between merges.
   The three zero-cost merges come first; #51 takes the free pin slot; #49 — the largest — lands last,
   when the pin must be re-measured once regardless.
3. **At each STOP: keep both sides' prose, and write the number the gate measures.** Never
   "take theirs" / "take mine" — both are wrong (§10.3), and the probe's own continuation is not a
   model to copy.
