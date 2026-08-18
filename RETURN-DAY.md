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

### Do this in the same sitting: re-pin the phone's vectors (added run 51, 2026-08-17)

**Step 4 (#51) changes the shared vector corpus, and the phone will not follow on its own.**
Measured against a `main` fetched this morning, by running all six merges for real (**C-POST-1**):

| | payload vectors | `index.json` | total files |
| --- | --- | --- | --- |
| phone today, pin `7328a0b` | 28 | 1 | **29** |
| `main` after §3's six merges | **29** | 1 | **30** |

The extra one is **`pairing-high-bit-confirm.json`** (`b95e83d`), and it arrives with **#51 — a merge
this plan calls *clean*, because it is.** **No file under `docs/sync-vectors/` conflicts in any of the
six merges**, and the post-landing corpus is **byte-identical whether you resolve the two stops
`--ours` or `--theirs`** (**C-POST-2**) — so this outcome is fixed by the merge set and none of your
choices at the stops can change it.

**Nothing will tell you.** `.github/workflows/ci.yml:127-133` has a check written for exactly this
case — *"upstream has vector(s) that were never vendored"* — but it queries `?ref=$PIN`, and the pin
also lacks the vector, so **android CI stays green straight through it** (**B-16 status 2026-08-17**).
Nothing breaks at runtime either: `ProtocolVectorsTest` enumerates from the phone's own `index.json`,
so the vector is simply **never asserted** — which is what **B-14** has been blocked on since 08-15.

**So, right after the last merge:**

```bash
cd <android>
scripts/repin-vectors.sh --check --engine <engine> origin/main   # what would change, writes nothing
scripts/repin-vectors.sh --engine <engine> origin/main           # do it
```

*Expect from `--check`:* `OK: 30 vector files match the generator.`, then
`vendored: 29 files    at pin: 30 files` with **`+ pairing-high-bit-confirm.json`** and
**`~ index.json`** — and `exit=1`, because `--check` reports drift as a failure. The write run ends
`OK: re-pinned 7328a0b… -> <merge commit>` and `30 vector files vendored`. Then run
`scripts/core-probe.sh`, and commit the corpus and `VECTORS.lock` **together**.

> **Added run 55, 2026-08-18 — this step used to be prose.** Until this morning the block above
> said *"# re-vendor docs/sync-vectors/v1 → core/src/test/resources/…"* and *"# bump the SHA"*, which
> is a description, not a command, for the one step in this plan that nothing checks. `scripts/repin-vectors.sh`
> is that command. It refuses to write unless the rev resolves, the commit carries
> `docs/sync-vectors/v1`, the corpus there passes its own generator, and `VECTORS.lock` holds exactly
> one 40-hex string — `ci.yml:75` reads the pin with `grep -oE '[0-9a-f]{40}' | head -1`, so a second
> one silently repoints CI. It replaces the corpus wholesale, so an upstream **deletion** is honoured
> too. It **decides nothing**: which upstream ref the repos should track is **H3**, still yours.
> Verified against a replay of these exact six merges — **C-REPIN-1**.
>
> **Point it at the post-merge `main`, not at #51's branch.** Measured (**C-REPIN-2**):
> `--check … origin/claude/s3-pairing-confirm-consumer` reports **+1 / −3 / ~1** — that branch
> gains `pairing-high-bit-confirm.json` but has never carried the three S5 vectors, so re-pinning
> there would *delete* them from the phone. The script shows you the removals before it writes;
> read them.

**This closes B-14.** `VECTORS.lock`'s *"ACTION WHEN PR #38's STACK MERGES"* note and B-14's unblock
both already say *re-pin afterwards* — **neither says to do it in the same sitting, and neither gave a
number to check it against.** Both now do. Deferring it is safe for the build and costs you the one
thing the re-pin buys: the phone testing what the engine ships.

> **CORRECTED run 56, 2026-08-18 — "this closes B-14" was false until this morning, and the re-pin
> is what would have looked like closing it.** Step 2 above (*run `:core:test`*) had never been
> executed by any cloud run, because `core-probe.sh` needs JDK 17 and the image ships 21. It needed a
> JDK, not the Windows box. Installed, the six merges replayed, a **copy** of the phone tree re-pinned
> at the result: `:core:test` **288 tests, 0 failed** (**C-ENUM-1**) — so **the re-pin is test-green**.
>
> **But the count was 288 before the re-pin and 288 after, with a vector added.** Corrupting
> `pairing-high-bit-confirm`'s expected confirm code to `999999` left the suite **green** (**C-ENUM-2**):
> `ProtocolVectorsTest`'s *"…every vector value"* **hardcoded `pairing-basic`**, and its only
> enumerator filters `type == "envelope"` while the new vector is `type: "pairing"`. **The re-pin would
> have vendored the vector, listed it in `index.json`, and asserted nothing about it** — and the vector
> is the only one that separates a signed-int32 reduction (`-936782`) and a dropped zero-pad (`30514`)
> from the conforming `030514`, which `pairing-basic` cannot do.
>
> **`4ddad07` fixes it** by enumerating valid `type: pairing` vectors from the manifest; the same
> mutation now fails (`expected: <999999> but was: <030514>`, **C-ENUM-3**), which incidentally proves
> this phone's reduction is the conforming one (**C-ENUM-4**). **Do the re-pin as written — it now buys
> what this box says it buys.** Nothing about the merge order, the counts, or `--check`'s expected
> output changes. **No pin was moved and no vector byte written by that run** (**C-ENUM-5**); H7 is
> still yours.

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

### REVALIDATED 2026-08-17 (run 52) — and it needed it more than §3 did

§3 was re-measured at runs 49 and 51. **This section had not been re-measured since
`docs/Merge-Topology.md` §§4–7 was written on 2026-08-09** — while `claude/android-a0-probe`, one
side of the §6 hazard, grew to **183 commits past the fork, 156 of them after that date** (this
window's own records commits). Every other android branch is byte-unmoved. Re-run as **real merges**,
not `merge-tree` (**C-AND-1**, **C-AND-2**):

| Claim, as written 2026-08-09 | Status 2026-08-17 |
| --- | --- |
| §4: 7 branches merge clean, `p1-runbook` conflicts on **one** file | **HOLDS**, row for row |
| §6: the two siblings overlap in exactly **3** files, all auto-fused | **HOLDS** — still those 3 |
| §7: recommended merge order | **unchanged** |

**The plan holds. Nobody knew that until it was run**, and it is `git`-only — no gate, no SDK.

### One thing §5 defers, now checked — read this before you resolve that conflict

§5 tells you to resolve the `docs/Monetization-Decision.md` add/add conflict in favour of
`p1-runbook`, then *"re-read `docs/store/Play-Listing.md` for the word 'Basic' before submission."*
**That downstream check has now been run (C-AND-3), and it turns the recommendation into a much
stronger one.** `Play-Listing.md` — the copy staged for the Play Console, living on **`p5-store`,
which is neither side of the conflict** — opens with:

> **Naming canon (enforced):** the Windows app is **"CareerSeeker"** — never "Basic". … Do not let
> "Basic" appear in any user-facing string **(Monetization-Decision §3)**.

**It cites the disputed section as its authority.** Take the `p1-runbook` side and §3 reads *"Naming
— decided"* with a price row saying *"CareerSeeker (.exe) — the product, not a tier"*: coherent.
Take the other side — **the tempting one, because it is the lineage carrying all 183 commits** — and
`main` carries a price table printing **"CareerSeeker Basic"** next to a store listing that forbids
the string and points at that very table as the rule's source.

**Nothing will stop you.** `p5-store` merges clean and `a0-probe` merges clean; the only conflict git
raises is on `Monetization-Decision.md` itself. **The contradiction is never a merge conflict** — it
surfaces in copy pasted into the Console. §5's recommendation is unchanged; it now has a second,
independent reason. **`Basic` appears in the whole `docs/store/` dossier exactly twice, and both are
that guard clause.**

---

## 5. Human queue — what only you can do

| | Item | Why it needs you |
| --- | --- | --- |
| **H1** | **Decide PR #53** | design choice between two push-result shapes (§3 step 0) |
| **H2** | **Run the gate and land §3's six merges** | `Verify-Alpha.ps1 -IncludePublish -IncludePackage` needs Windows + .NET; no cloud session can run it |
| **H3** | **Decide B-16** — should anything watch the vendored pin for staleness? | naming a draft branch in CI couples the android build to a ref someone may rebase; three options are written out in `BLOCKED.md` B-16. **Landing §3 satisfies option 2's precondition** — the sync track reaches `main`, so "compare against `main` and fail" stops being gated on the restack, and would fire correctly on H7's vector |
| **H7** | **Re-pin the phone's vectors, in the same sitting as H2** | §3's step 4 puts `pairing-high-bit-confirm.json` on `main`; the phone stays at 28 payloads and **no check in either repo reports it** (**C-POST-3**, **B-14**, **B-16**). Mechanical, ~5 min, expected `OK: 30` / `30` files — but only you can decide the pin moves. **Run 56:** the re-pin is now proved **test-green** (`:core:test` 288/0 on a re-pinned copy, **C-ENUM-1**), and `4ddad07` fixes the reason it would otherwise have vendored that vector **inert** (**C-ENUM-2/-3**) |
| **H8** | **Resolve §5's naming conflict toward `p1-runbook`** — take *"CareerSeeker"*, never *"Basic"* | it is a product-naming decision, so it is yours; but it is no longer 50/50. `docs/store/Play-Listing.md`, on a **third** branch, already enforces that side and cites the disputed section as its authority (**C-AND-3**). The other resolution puts a self-contradicting tree on `main` **through clean merges that raise no conflict**. §4's second box has the detail |
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
   blurs that and report it. **Run 56 narrows this and is the place to press:** `:core:test` **was**
   executed locally (**C-JDK-1**, 288/0) after installing JDK 17. That is **one** of the gate's five
   tasks. Attack any sentence that lets it stand in for the other four — `:app:assembleDebug` and
   `:app:lintDebug` still have no SDK here, and **the fused android tree has still never been built**.
8. **Run 56's own finding, by the method that produced it** (added run 56). It rests on a negative
   control, not on reading code: green with a corrupted vector, red after the fix, same mutation. **The
   test count is 288 in every run**, so a reviewer checking coverage by count will conclude nothing
   changed. Re-run **C-ENUM-2** *before* applying `4ddad07` and confirm the green — if it is red, the
   finding is wrong and the fix is unnecessary. **A first draft of this line claimed the `entitlement`
   and `entitlement_ack` families had the same hole; they do not** — `EntitlementVectorsTest` filters
   `type == "entitlement"` and `ProtocolVectorsTest` filters `type == "entitlement_ack"`, both
   enumerated from the manifest. The claim was withdrawn before push, by grepping instead of assuming.
   The corrected finding is **narrower and worse**: all four vector families enumerate, and **`pairing`
   was the single hardcoded exception** — the one family where a vector could be added upstream and
   land inert.
4. **B-16.** Every drift check in both repos compares the phone against **the pin**, never upstream.
   The guarantee is "the phone matches the pin", not "the phone matches the engine" — and
   `VECTORS.lock`'s wording is close to implying the latter.
5. **The eleven-times-restated slice.** Twelve runs were assigned S5's spec half; it has been built
   since 2026-08-09 (`8575539`, `22b028e`, `7328a0b`) on draft branches. If the records made that hard
   to see, that is a defect in the records, and it is the one that cost the most iterations.
6. **"§3 leaves the phone one vector behind"** (added run 51, §3's re-pin box). Two ways in: the
   **28 vs 29 payload** counts are easy to misread, because `generate.mjs --check` counts
   `index.json` among its "vector files" and so reports **29/30** where the payload counts are
   **28/29** — a reader comparing the generator's number against `index.vectors[]` will think the
   manifest is stale, and it is not (**C-POST-3** records that false alarm and why it is false). The
   harder attack is the **resolution-independence** claim: re-run **C-POST-1** with `--ours` instead
   of `--theirs` and byte-compare the corpora. If any vector byte differs, the re-pin box's numbers
   are a forecast rather than a determination, and it should be re-read as one.
7. **"§4's android plan still holds"** (added run 52). It rests on `a0-probe` having grown *only* by
   records commits. Attack it by re-running **C-AND-2**'s intersection: if any **code** file entered
   the overlap set since 08-09, §6's auto-fusion hazard is wider than three files and §7's *"order
   between them does not matter"* needs re-deriving. Attack **C-AND-3** by scoping — it is a claim
   about the **price-table row**, not about the string `Basic` anywhere in the file; the whole-file
   grep reports *both* resolutions inconsistent and is wrong, for the reason the check records.
   And note what neither check claims: **the fused android tree has still never been built** (§6).

---

## 8. Boundary — what this window never touched

No merge in either repo. No force-push, no history rewrite, no branch deleted. No deploy of any kind
— Cloudflare, Workers, relay or site. The production relay was contacted at most on `GET /v1/health`
and, in this run, **not at all**. No Play, Google or OAuth console; no accounts, no purchases, no
Gmail, no keystore, no emulator. No secret was read, printed or echoed — existence checks only. No
`.appdata` original was touched. Terra's territory (`autonomy/codex-state`, `Documents\CareerSeeker`,
the beta-track worktrees) was **read, never written**. No vector byte was changed in either repo: the
vendored corpus still matches its pin `7328a0b`, confirmed by a real CI runner.
