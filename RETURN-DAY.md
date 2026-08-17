# RETURN DAY — the unattended window's closing handoff

**Written:** 2026-08-16, forty-seventh cloud iteration (Linux sandbox).
**For:** Brandon, on return 2026-08-18.
**Why this file exists:** mission §7 sets the stop condition as *"all rungs DONE/BLOCKED, **or 45
iterations logged** → final handoff entry"*. **46 runs were logged before this one and no handoff was
written.** This is that handoff, two runs late. It is the only document you need to read first; every
number in it has a re-verification command in [`AUDIT-REQUEST.md`](AUDIT-REQUEST.md).

---

## 1. The one-paragraph version

The engine sync track works and is **not on `main`**. Seventeen draft PRs are open in
`ShivaClaw/careerseeker` and none is merged, because the merge condition is a full local
`Verify-Alpha.ps1` that no cloud session can run. Those 17 PRs are **7 merges**, of which **3 stop on
a conflict** — all of them the same `$ExpectedOfflineTotal` pin family, and all of them mechanical
once you decide one design question. The single highest-value hour on return day is: **decide PR #53,
then land six merges in the order in §3.** Everything else on the ladder is either done, partial in a
way that is written down, or blocked on hardware this program does not have.

---

## 2. Ladder — status at close

Derived from `STATE.md`'s table; unchanged by this run except where noted.

| Rung | Status | What actually remains |
| --- | --- | --- |
| **S0** re-entry + derivation | **DONE** | — |
| **S1** land the engine sync track | **DONE**; *successor* stack costed, not landed | §3 of this file |
| **S2** engine publishes for real | **PARTIAL** | B-2's last half: the phone-facing pair flow |
| **S3** pairing screen | **PARTIAL** | device-key behaviour needs an emulator (**B-4**) |
| **S4** transport loop | **PARTIAL** | E2E rig needs engine + relay + emulator on one machine |
| **S5** entitlement ack | **PARTIAL** — emitter landed | host wiring; **not blocked** |
| **S6** outcome marking (phone) | **PARTIAL** | needs S3's key; engine half is in #52/#53 |
| **S7** Play-readiness pack | **PARTIAL** | Console steps are human-only |
| **S8** hardening | **PARTIAL / BLOCKED** | **B-5** (Room 2.8.4 under Robolectric) |

**No rung's status was changed by this run.** This run measured the landing cost and wrote the
handoff; it wrote no engine code and no Kotlin.

---

## 3. The landing plan — 17 PRs, 7 merges, 3 stops

Full derivation and the commands: [`docs/Merge-Topology.md` §12](docs/Merge-Topology.md).
**Decide step 0 before doing any of it.**

> **REVALIDATED 2026-08-17 (run 49), the day before this plan is acted on.** Every number in this
> section was re-measured against a `main` fetched that morning — `origin/main` is still
> `aac05f3`, unmoved since 2026-08-12 — and against the **live PR heads**, not just local refs: all
> seven landing branches match their PR's head SHA exactly, 0 mismatches (**C-RD-3**). All 17 PRs
> are still open and still draft; nothing was merged or closed. **The four stop counts below (3 / 2
> / 4 / 3) all reproduce** (**C-RD-1**, **C-RD-2**). One row was missing and is added below.

### Step 0 — decide PR #53 (`claude/s6-resume-reconciliation`)

#53 and the #45/#46 stack **implement the same defect fix twice, incompatibly** — a typed push
result, as `PushOutcome` and as `RelayPushResult`. This is §11.2, and §11.4 recommends #53 be
**closed or reduced to whatever #45/#46 lack**, on the evidence that the stack's shape is more
developed on every measurable axis. This run quantified the recommendation:

| | stops | source conflicts in the final merge |
| --- | --- | --- |
| land all 7 leaves | **3** | `RelayClient.cs`, `SyncPublisher.cs`, `Program.cs`, `SyncLiveSmoke` |
| **#53 closed** | **2** | **none** — only the pin family + `SyncHarness` |

**Closing #53 removes a stop and the entire `src/Sync/` conflict class.** It is a design decision,
not a merge conflict, and it is yours.

### Then land in this order (the measured minimum)

| # | PR | Branch | Lands | Cost |
| --- | --- | --- | --- | --- |
| 1 | **#48** | `s8-harness-linux-reach` | itself | clean |
| 2 | **#35** | `s2-seq-bound` | #34, #32 | clean |
| 3 | **#36** | `s2-transport-vocabulary` | #33, #32 | clean — but read §10.5 first, its declared base ≠ its actual base |
| 4 | **#51** | `s3-pairing-confirm-consumer` | #50 | clean — **takes the free pin slot** |
| 5 | **#52** | `s6-outcome-disposition` | itself | **STOP** — pin family, 5 files |
| 6 | **#49** | `s6-composition-root-decision` | #47, #46, #45, #39, #38, #37, #32 | **STOP** — pin family + `SyncHarness` |

**Order is load-bearing.** Landing #49 first costs **4** stops instead of 3: it forked at pin `598`,
`main` is now `611`, so it conflicts even as the first merge and forfeits the free slot.

**The row that was missing, added at run 49 (C-RD-2).** The `4 instead of 3` above is measured on
the **all-7-leaves** configuration — but this section *recommends closing #53*, and nobody had
measured order-dependence in the configuration it recommends. Both are now measured:

| configuration | recommended order | #49 first | penalty |
| --- | --- | --- | --- |
| all 7 leaves | **3** stops | **4** stops | **+1** |
| **#53 closed** (what §3 recommends) | **2** stops | **3** stops | **+1** |

**This is a clarification, not a correction — the `4 instead of 3` figure is right as written.** The
point is that a reader who takes step 0's advice, closes #53, and then sanity-checks the order claim
will measure **3 vs 2** and see neither printed number. The penalty is what transfers between
configurations; the absolute counts are not. **Order is load-bearing in both**, which is the claim
that actually matters, and it now has evidence in the configuration you will be standing in.

### At each STOP

The conflict is always the same five files — `README.md`, `docs/CareerSeeker-Project-Summary.md`,
`docs/External-Audit-Handoff.md`, `scripts/Verify-Alpha.ps1`, `src/Engine/README.md` — and the rule
from `CLAUDE.md`'s drift trap is:

> **Keep both sides' prose. Write the number the gate measures. Never "take theirs" or "take mine" —
> both are wrong.**

**Do not pre-fill the pin.** §10.3 predicted `806` for the chain alone and arithmetic would suggest
`832` for the fleet, but §11.3 showed the deltas are not disjoint once #53 is involved. The landed
value is whatever `Verify-Alpha.ps1` reports *after* step 0. It throws on a mismatch, so a wrong
guess is a hard failure, not silent drift — the drift trap is protective here.

---

## 4. Android repo — 6 open drafts, unchanged

| PR | Branch | Note |
| --- | --- | --- |
| #6 | `claude/android-a0-probe` | this window's work + all house records. CI green on the runner |
| #5 | `claude/p5-store` | sibling of #6, not a chain — 3-file overlap, `docs/S-Ladder.md` §1.1 |
| #4 | `claude/p2-replica` | |
| #3 | `claude/p1-pairing` | |
| #2 | `claude/p1-runbook` | |
| #1 | `claude/p0-scaffold` | |

**The android repo is never-self-merge.** Nothing here was merged, closed, or taken out of draft in
any run of this window. The two-lineage merge hazard (`main` has diverged from the code lineage, it
is not merely behind) is flagged in `docs/S-Ladder.md` §1.1 and deliberately unresolved — merge
policy is yours.

---

## 5. Human queue — what only you can do

| | Item | Why it needs you |
| --- | --- | --- |
| **H1** | **Decide PR #53** | design choice between two push-result shapes (§3 step 0) |
| **H2** | **Run the gate and land §3's six merges** | `Verify-Alpha.ps1 -IncludePublish -IncludePackage` needs Windows + .NET; no cloud session can run it |
| **H3** | **Decide B-16** — should anything watch the vendored pin for staleness? | naming a draft branch in CI couples the android build to a ref someone may rebase; three options are written out in `BLOCKED.md` B-16 |
| **H4** | **Install `sdkmanager`/`avdmanager`** (B-4) | unblocks S3, S4, S6 — the emulator lane is the single biggest unblock on the board |
| **H5** | **`npx wrangler deploy --config relay/wrangler.jsonc`**, then re-run SyncLiveSmoke live | the production relay still self-reports `phase: p1`; it predates P2/P4. Deploys are embargoed for agents |
| **H6** | **Room 2.8.4 / Robolectric** (B-5) | blocks S8's migration coverage |

---

## 6. Evidence index — where to check the work

| Document | Holds |
| --- | --- |
| [`AUDIT-REQUEST.md`](AUDIT-REQUEST.md) | **every claim in these records, with the exact command that re-verifies it.** Start here to audit |
| [`LOG.md`](LOG.md) | 47 run entries, evidence milestone by milestone, each ending in a prohibition paragraph |
| [`BLOCKED.md`](BLOCKED.md) | B-1 … B-17, each as symptom / attempts / smallest human unblock |
| [`docs/Merge-Topology.md`](docs/Merge-Topology.md) | the branch topology and all three costings (§10 isolated, §11 leaf-vs-leaf, §12 sequential) |
| [`docs/S-Ladder.md`](docs/S-Ladder.md) | the re-entry derivation the window was planned from |
| [`docs/protocol-questions.md`](docs/protocol-questions.md) | PQ-* questions and which are answered |
| `scripts/fleet-probe.sh` | `symbol` / `matrix` / `leaves` / `land` — the probes behind §10–§12 |

---

## 7. What an external auditor should attack first

Ranked by how much would be wrong if the claim is wrong.

1. **"17 PRs are 7 merges."** The whole plan rests on subsumption. If one `--is-ancestor` check is
   wrong, a PR silently does not land. Re-run **C-LAND-1** and diff the leaf set against the open-PR
   list — they are *not* the same set, and `claude/p4-entitlement` is the proof (**C-LAND-2**).
2. **"3 stops, and order matters."** Measured by a probe that continues past conflicts using
   `merge-tree`'s conflicted tree. **The stop count is robust; the file lists after the first stop are
   artifacts** (§12.5). An auditor should confirm the count is order-dependent (**C-LAND-5**) and not
   read the growing file lists as forecasts.
3. **Every gate claim in this window.** No cloud run ever executed `Verify-Alpha.ps1` or the android
   gate — no `pwsh`, no `dotnet`, no Android SDK (**C-LAND-7**, **C-ENV-1**). Android gate results in
   these records are *read out of CI runner logs*, never produced locally. Grep for any sentence that
   blurs that and report it.
4. **B-16.** Every drift check in both repos compares the phone against **the pin**, never upstream.
   The guarantee is "the phone matches the pin", not "the phone matches the engine" — and
   `VECTORS.lock`'s wording is close to implying the latter.
5. **The eleven-times-restated slice.** Twelve runs were assigned S5's spec half; it has been built
   since 2026-08-09 (`8575539`, `22b028e`, `7328a0b`) on draft branches. If the records made that hard
   to see, that is a defect in the records, and it is the one that cost the most iterations.

---

## 8. Boundary — what this window never touched

No merge in either repo. No force-push, no history rewrite, no branch deleted. No deploy of any kind
— Cloudflare, Workers, relay or site. The production relay was contacted at most on `GET /v1/health`
and, in this run, **not at all**. No Play, Google or OAuth console; no accounts, no purchases, no
Gmail, no keystore, no emulator. No secret was read, printed or echoed — existence checks only. No
`.appdata` original was touched. Terra's territory (`autonomy/codex-state`, `Documents\CareerSeeker`,
the beta-track worktrees) was **read, never written**. No vector byte was changed in either repo: the
vendored corpus still matches its pin `7328a0b`, confirmed by a real CI runner.
