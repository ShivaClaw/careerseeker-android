# STATE — android tree

Single-glance state for the unattended window (2026-08-07 → 2026-08-18). Full derivation lives in
[`docs/S-Ladder.md`](docs/S-Ladder.md); evidence in [`LOG.md`](LOG.md); re-verification commands in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md); blockers in [`BLOCKED.md`](BLOCKED.md).

| | |
| --- | --- |
| **Heartbeat** | 2026-08-11 (**S2 transport vocabulary — PQ-S2-3 closed via option (a), and the code that names the condition is never emitted for it**. Cloud iteration, Linux sandbox, **seventeenth** run.) **First, a correction to the iteration prompt, derived after the mandatory fetch:** it assigned **S5** on the basis that S5 was "NOT STARTED and genuinely NOT blocked" — **false.** `origin/claude/s5-entitlement-ack-spec` has carried **four commits since 2026-08-09** as draft **PR #32**; §4.3.3, both `entitlement_ack` vectors, PQ-A6-1/PQ-A2-1/PQ-A2-2 are all closed, and the only open piece is **PQ-A2-3, which B-6 blocks** (engine has no inbound wire-JSON parser; the vector would turn the offline gate red). So this iteration took the topmost rung actually verifiable here, as the prompt's own escape clause directs. **Main repo gained §2.3** (`claude/s2-transport-vocabulary`, draft PR **#36**, stacked **#33 → #32**) pinning `create`, `pair`, `pull`, `live`, `DELETE` and `health` — statuses **and** bodies. **`relay/src/` is byte-identical on the branch**: every line was read off the running Worker and written down second, so the section is **descriptive and refuses nothing new**, which is §3.1's size-cap lesson applied rather than restated. **The finding: `pairing_unknown` never means the pairing is unknown.** Measured after `DELETE /v1/{pairing}` — the exact condition §7.2 names — `pull`, `push`, `pair` and `DELETE` **all answer `401 unauthorized`**, identical to a wrong token, and `POST /create` then answers **201** because the id re-bootstraps. The transport `pairing_unknown` fires **only** on a pairing-id *shape* failure, checked before authentication. **So §7.2's condition has no transport code at all.** **The cost, and it is read not executed:** the phone maps any 404 → `PairingUnknown` → `SendHalt.PAIRING_GONE` (**terminal**), and 401 → `UNAUTHORISED` (**recoverable**, cleared "when a fresh bearer is in hand"). A genuinely unpaired phone therefore halts on the **recoverable** state waiting for a bearer that cannot exist, while the terminal state built for exactly this is never entered — and appears **unreachable outright**, since no route the phone calls can 404 (`GET /pair`, the one transiently-404ing route, is never called by the phone). **That half is a hypothesis with file:line support, unverified by execution — no Android SDK (B-7).** **v1 pins the 401 rather than adding a code**, because a purged pairing is indistinguishable from one that never existed; whether that privacy property outweighs the phone knowing it was unpaired is **Brandon's call** → **PQ-S2-4**, deliberately not a blocker. **Two documents were wrong and are corrected:** PQ-S2-3's table said **eight** transport codes — its own command on its own cited commit returns **nine** (`exists` dropped in transcription) — and `AUDIT-REQUEST.md`'s **C-S6C-5 was self-contradicting**, its command returning nine against an *Expected* of eight. Also measured: the two vocabularies share **three** names, not two; `pairing_unknown` is the one that means something **different** on each side. Relay suite **36 → 47**, and because no relay code changed **all eleven are pins by construction**, so each was checked against a **deliberately mutated relay** — four mutations, **ten of eleven caught something**; the eleventh is labelled a pin, not a regression catcher. **An audit command that did not reproduce its own expected output was caught and fixed before shipping** (the first `sed` matched three sites, not one). **Zero Kotlin, zero C#, zero relay source, zero vectors: one Markdown file and one test file.** CI **green both jobs on the branch tip** (run `31516194482` on `4db3543`), **`Offline total: 598 passed, 0 failed`** read from the job log. **S2 stays PARTIAL — B-2 is still exactly the missing `/pair` page; this is the *fourth* hardening of S2's transport half, which is not the same as advancing the rung** |
| **Heartbeat, sixteenth run** | 2026-08-11 (**S2 `seq` bound — PQ-S2-2 closed *in part*, spec first, and the deferral that held it was inherited from a different question**. `seq` had **no stated maximum anywhere**, and the relay's guard was `Number.isInteger(seq) && seq >= 1` — **not a range check**: it rejects a fractional value but **cannot reject a large one**, since every double at or above 2⁵³ is necessarily integral, so the accepted range ran to **~1.8e308** and only `Infinity` was refused (and that only because `Number.isInteger(Infinity)` is `false`). Main repo gained **§3.2** (`claude/s2-seq-bound`, draft PR **#35**, stacked #34 → #32) capping `seq` at **`2^53 - 1`** — the largest integer the two 64-bit receivers and the relay's double all represent **exactly**, so it is a property of the wire and not a number chosen for one party. `MAX_SEQ` = `Number.MAX_SAFE_INTEGER`, the derivation not a literal, per §3.1's round-number lesson. Cloud iteration, Linux sandbox, **sixteenth** run.) **The finding: the wedge reaches the READ path, which the question never costed.** `latest` is emitted from the same double, so measured under miniflare — 2⁶² returns `4611686018427388000` (**silently rounded, off by 96**), 1e19 exceeds `Long.MaxValue`, 1e300 renders `1e+300` — and **both receivers parse `latest` strictly** (`RelayClient.cs:74` `GetInt64()`, no catch on that path; the phone's `strictLong` → `toLongOrNull()`). **So one garbage counter disables the `GET /pull` reconciliation §6.1 prescribes for exactly that situation** — it takes out the instrument used to diagnose it. Also: 2⁵³ then 2⁵³+1 answered `201` then **`409 replay_rejected`** — a strictly *larger* integer refused as a **replay**, so the question's "unreachable in practice" precision note was wrong (reaching 2⁵³ needs one bad counter, not 2⁵³ envelopes). Relay suite **42 → 51**, **7 of 9 new tests proven to fail** against the pre-change guard by reverting and re-running; the other 2 are labelled **pins, not regression catchers**. **Receiver rule is SHOULD not MUST** — relay is the only ingress, neither receiver implements it, and §3.2 says so in a measured conformance note rather than tightening quietly. **Zero Kotlin, zero C#, zero vectors: one Markdown file and three TypeScript.** CI **green both jobs on the branch tip** (run `31495565325` on `2be00fc`; `31494720248` on the code commit before the wording fix), **`Offline total: 598 passed, 0 failed`** read from the job log. **S2 stays PARTIAL — B-2 is still exactly the missing `/pair` page; this is the *third* hardening of S2's transport, which is not the same as advancing the rung.** **STILL OPEN:** an *in-range* wedge still bricks a direction until TTL/unpair, and the reset endpoint is **Brandon's product decision**, deliberately not filed as a blocker |
| **Heartbeat, fifteenth run** | 2026-08-11 (**S6 counter symmetry — PQ-S6-2 closed, spec first, and the finding inverts the section**. §6.1's first sentence bound **both** senders to persist, then spelled out the *recovery* rule for the engine only — while `POST /push` refuses `seq <= last` per direction whichever end pushed it. Closing it needed **two** sections, because PQ-S6-2's own "to close" note contained the second gap as an aside: the rule points at the 409 body's `latest`, and **that body was defined nowhere**, which is PQ-S4-2's defect one level down. So main repo gained **§2.2** (`claude/s4-pull-request-semantics`, PR #33) pinning all four push responses — **measured under miniflare, not read off `channel.ts`** — and only then §6.1's generalisation. Cloud iteration, Linux sandbox, **fifteenth** run.) **The finding: the engine implements half of §6.1 and its own comment states the other half.** `Program.cs:288` passes `startSeq: paired.LastE2pSeq` — the persisted term only, no `max(…)`, and `grep -n PullAsync src/Engine/Program.cs` prints **nothing** — while the comment at `:239-243` states the `max(vault.last_e2p_seq, relay latest e2p)` rule verbatim. Compounding it, `PushAsync` returns `bool`, so the 409's `latest` is **discarded unread**. **So §6.1 asked the engine to reconcile and the engine is the one that cannot, while the phone — never asked — does.** Stated narrowly: `SyncPublisher` increments before the sink, so a stale vault self-heals by **burning one seq per refused push**; the cost is one dropped envelope each, *including the recovery `snapshot`* if it falls in the run. §6.1's catastrophe is **mitigated into a window, not prevented**, and nothing reports the window → **PQ-S6-3**. **Zero Kotlin, zero C#, zero relay source, zero vectors: two Markdown files.** **S6 stays PARTIAL — this closed a question against the send path, not the path** |
| **Heartbeat, fourteenth run** | 2026-08-11 (**S4 cursor bound — PQ-S4-3 closed, spec first**. §6.2 governs `highest_accepted`; the **transport cursor** — the `since` the next pull sends — was named nowhere in the protocol, and that hole is where the bug lived. An element failing the §3 parse has no authenticated `seq`, so `SyncPump` fell back to the one it *claims*, read leniently and authenticated by nothing: one unparseable element carrying `"seq": 1000000` walked the cursor past every envelope below it, permanently, **without decrypting a byte**. Main repo gained **§6.4** (`claude/s4-pull-request-semantics`, PR #33): the cursor never moves backwards, advances only to a `seq` recovered from the sealed bytes, and an unparsed element MAY advance it by its claim but **MUST NOT** pass the page's own `latest`. `SyncPump` then implemented exactly that — **in that order, deliberately**. Cloud iteration, Linux sandbox, **fourteenth** run). `:core` 187 → **190 / 0 / 0** across 14, both ends measured here; **2 of 3 new cases fail against the pre-change source** while all 19 pre-existing `SyncPumpTest` cases pass. **Bounded, not refused:** refusing stalls the direction forever on one corrupt byte (§6.2 forbids it), and the failure modes are **not symmetric** — a stall is recoverable and loud, truncation is silent, permanent, and looks like a healthy caught-up sync. **A correction against my own finding:** PQ-S4-3 claimed the bound capped the attack; it does not — it does **not** protect envelopes the relay already holds (it could withhold those anyway) and removes only the **forward-going** half, where an unbounded claim parks the cursor past seqs *not yet issued*. The spec states the smaller claim. **S4 stays PARTIAL: its remainder is the `:app` wiring and this did not touch it.** **Engine half unwritten, not blocked** |
| **Heartbeat, thirteenth run** | 2026-08-11 (**S4 pull-page semantics — PQ-S4-2 closed, spec first**. §2's route table defined the pull *request* and stopped, so three implementations each invented a response body and §6.1 reconciled against a `latest` **the document never defined**. Main repo gained **§2.1** (`claude/s4-pull-request-semantics`, PR #33): both fields REQUIRED, `latest` a bare integer, elements **bare §3 envelopes**, the page explicitly truncatable, and the `{"seq":N,"envelope":…}` wrapper refused. Android then removed the wrapper from `parsePullPage` — **in that order, deliberately**. Cloud iteration, Linux sandbox, **thirteenth** run). `:core` 185 → **187 / 0 / 0** across 14, both ends measured here; the new `RelayClientTest` case **fails against the pre-change parser** while all 25 pre-existing cases pass. **The result is stronger than a fix:** with the wrapper gone both parsers read `seq` off the same field, so the disagreement `SyncPump`'s rule 4 defends against is **structurally unreachable rather than defended** — rule 4 is now defence in depth and its test says so. **Two corrections in the same slice:** (a) §2.1's first draft required an error *type* the engine does not use — a spec tightening ahead of its implementations, softened to MUST/SHOULD (S4S-4); (b) three existing assertions rested on the wrapper, not the one predicted, and one **kept passing while testing something other than its title** (S4S-6). **S4 stays PARTIAL: its remainder is the `:app` wiring and this did not touch it.** Findings → **PQ-S4-3** |
| **Heartbeat, twelfth run** | 2026-08-10 (**S4 pull-page hardening** — `RelayClient.parsePullPage` was partial in four places *and* invoked outside `request`'s try/catch, so a malformed 200 body threw out of `pull` past the whole `RelayResult` contract. Measured on the shipped parser before any edit: **9 of 12** malformed bodies escaped as exceptions, and the 3 that did **not** were the worse half — an absent `latest` silently read `0`, which is what drives `moreAvailable`, so **deleting one field convinced the phone it was fully caught up**. Cloud iteration, Linux sandbox, **twelfth** run). Android repo only: **two code files, both `:core` Kotlin, zero `:app`, zero main-repo files, zero vectors.** `:core` 177 → **185 / 0 / 0**, both ends measured here, and **all 8 new tests fail against the pre-fix parser** while all 17 pre-existing `RelayClientTest` cases still pass — run deliberately. Now the engine-compatible reading: both keys required and strictly typed, matching `GetProperty`/`GetInt64`. **S4 stays PARTIAL: its remainder is the `:app` wiring and this did not touch it** |
| **Heartbeat, eleventh run** | 2026-08-10 (**S2 relay conformance** — the relay's read path served envelopes it had already promised were purged; `GET /pull` had no expiry predicate, so §2's retention MUST held only as fast as the TTL alarm happened to run; **eleventh** run). Main repo only: two files, both under `relay/`. Relay suite 36 → **42 / 0**. **S2 stayed PARTIAL: B-2's gap is the `/pair` page and it did not touch it** |
| **CI, thirteenth-run push (main repo)** | **GREEN, and this time the offline total was read directly rather than inferred** — run [31448717897](https://github.com/ShivaClaw/careerseeker/actions/runs/31448717897) on `claude/s4-pull-request-semantics` head `10696d2`. **Both jobs `success`:** *Blind relay (Worker)* and *Build and offline harnesses*. **From the job log itself:** `SyncHarness … === 130 passed, 0 failed ===` and **`=== Offline total: 598 passed, 0 failed ===`**, then `CareerSeeker alpha verification complete.` So `Verify-Alpha.ps1` **ran and the 598 pin is confirmed by measurement** — not by the doc-only argument, and not by "the script throws on drift so exit 0 implies it". The standing caveat from the tenth run ("I did not read the `Offline total:` line myself") is **discharged**. Re-verify: C-S4S-3, C-S4S-7 |
| **CI, thirteenth-run push (android)** | **GREEN, and it reported before this iteration ended** — run [31448716435](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31448716435) (run #75, event `push`), job *Build and test* (`93648385242`), conclusion **`success`**, 01:15:02 → 01:22:28 UTC. **`head_sha` `782f9bb` read from the run's own field and matched against `git rev-parse HEAD`** — not inferred from the PR's check list, which follows the head. Single-job workflow, so green covers `checkCoreIsAndroidFree`, the vendored-vector diff against `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug` and the release-classpath tracker check — **including the only check that the wrapper removal compiles under the real toolchain** rather than the probe's substituted JDK 21. **It does not corroborate the number: I did not count the per-case `PASSED` lines**, so **187** stays the probe's figure (the standing C-S4P-11 gap, unchanged) |
| **CI, twelfth-run push (main repo)** | **GREEN** — run [31412922819](https://github.com/ShivaClaw/careerseeker/actions/runs/31412922819), event `push`, **`head_sha` `310406a`** read from the run's own field and matched against the branch tip. **Both jobs `success`:** *Blind relay (Worker)* (`ubuntu-latest`) and *Build and offline harnesses* (`windows-latest`). **CI ran the two things this session could not:** `Run offline alpha verification` = `Verify-Alpha.ps1`, which *throws* on offline-total drift — so **the 598 pin is confirmed intact by measurement, not by the no-files-written argument** — and `Validate config (no deploy)` = the `wrangler deploy --dry-run` skipped here under the embargo. **What it does not prove:** no engine↔relay smoke ran, so the `latest` semantics change is still unverified against the C# resume path (PR #34 self-audit item 1). Re-verify: C-S2R-15 |
| **CI on this push (main repo), seventeenth run** | **GREEN, both jobs, and the offline total was read from the job log rather than inferred** — run [31516194482](https://github.com/ShivaClaw/careerseeker/actions/runs/31516194482) (run #427, event `push`), **`head_sha` `4db3543` read from the run's own field** and equal to the branch tip of `claude/s2-transport-vocabulary`. *Build and offline harnesses* (`windows-latest`, job `93861817135`) and *Blind relay (Worker)* (`ubuntu-latest`, job `93861817039`), 17:10:15 → 17:12:51 UTC, both `success`. From the log: `SyncHarness … === 130 passed, 0 failed ===`, **`=== Offline total: 598 passed, 0 failed ===`**, `CareerSeeker alpha verification complete.` **The relay job's steps were checked individually rather than trusting the overall green** — *Generate runtime types* ✓ · *Typecheck* ✓ · *Test* ✓ · *Validate config (no deploy)* ✓ · *Assert the relay has no decryption path* ✓ · *Assert sync vectors match their generator* ✓ — so CI ran the two things this sandbox could not: `Verify-Alpha.ps1` and the `wrangler` typecheck/dry-run. **What it does not prove:** no engine↔relay smoke ran, and **no Kotlin ran anywhere**, so PQ-S2-4's phone-side half is still unverified by execution. Re-verify: C-S2T-8 |
| **CI on this push (main repo), fifteenth run** | **GREEN, and the offline total was read from the job log rather than inferred** — run [31476875538](https://github.com/ShivaClaw/careerseeker/actions/runs/31476875538) (run #424, event `push`), **`head_sha` `b114d11` read from the run's own field** and equal to `git rev-parse HEAD`. **Both jobs `success`:** *Blind relay (Worker)* (`93732493713`) and *Build and offline harnesses* (`93732493711`), 09:15:16 → 09:16:38 UTC. From the log itself: `SyncHarness … === 130 passed, 0 failed ===` and **`=== Offline total: 598 passed, 0 failed ===`**, then `CareerSeeker alpha verification complete.` So `Verify-Alpha.ps1` **ran in full and the 598 pin is confirmed by measurement** — which upgrades this iteration's "unchangeable by construction" claim from an argument to an observation. *(The 82-second job duration is not a skipped run: every harness section prints in the log, `SyncHarness` included.)* Re-verify: C-S6C-6 |
| **CI, fourteenth-run push (main repo)** | **GREEN, and the offline total was read from the job log, not inferred** — run [31460767322](https://github.com/ShivaClaw/careerseeker/actions/runs/31460767322), event `push`, **`head_sha` `69b94fd` read from the run's own field** and equal to the branch tip. **Both jobs `success`:** *Blind relay (Worker)* and *Build and offline harnesses*. From the log itself: `=== 130 passed, 0 failed ===` (SyncHarness) and **`=== Offline total: 598 passed, 0 failed ===`**, then `CareerSeeker alpha verification complete.` So `Verify-Alpha.ps1` **ran and the 598 pin is confirmed by measurement** — not by the doc-only argument. Re-verify: C-S4C-6 |
| **CI on this push (android)** | **GREEN, and it reported before this iteration ended** — run [31460952903](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31460952903), job *Build and test* (`93684235208`), conclusion **`success`**, 05:14:06 → 05:21:40 UTC. **`head_sha` `ff25406` read from the job's own field and matched against `git rev-parse HEAD`** — not inferred from the PR check list, which follows the head. **Every step checked individually rather than trusting the overall green** (a skipped step also lets a run go green): `checkCoreIsAndroidFree` ✓ · vendored-vector diff against `679a317` ✓ · `:core:test` ✓ · `:app:test` ✓ · `assembleDebug` ✓ · `lintDebug` ✓ · analytics/tracker check ✓. **This is the only check that the bound compiles and passes under the real JDK 17 toolchain** rather than the probe's substituted JDK 21. **It does not corroborate the number: I did not count the per-case `PASSED` lines**, so **190** stays the probe's figure (the standing C-S4P-11 gap, unchanged). *Note: the first run on the code head (`629eb30`, run 31460738524) was **cancelled** by the records push — but it had already reported `checkCoreIsAndroidFree`, the vector diff and `:core:test` all `success` before being superseded.* |
| **Android branch** | `claude/android-a0-probe` — draft [PR #6](https://github.com/ShivaClaw/careerseeker-android/pull/6) with self-audit. **This iteration's update is a PR *comment*, not a body section** ([#6 comment](https://github.com/ShivaClaw/careerseeker-android/pull/6#issuecomment-5249237377)): the body is ~43 KB against GitHub's 65,536 limit and its own trim note asks the next appender to delete the oldest remaining section first. Deleting a records section is a decision, so this iteration left the trim budget alone and commented instead. **The main-repo half is likewise [a comment on #33](https://github.com/ShivaClaw/careerseeker/pull/33#issuecomment-5249241148).** Full evidence is in `LOG.md` §S4C-1…8 either way. **CI GREEN on the S6 *marking* push** (not the send push — see the CI rows): run [31325873134](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31325873134), job *Build and test*, `success`, on head `9f73226` — **C-S6A-9 is closed green** and S6's marking decision is gate-verified, not probe-verified. **10 behind `main`** (docs-only commits, no overlap with this branch's files); left as found |
| **Merge topology** | **measured, not predicted** — [`docs/Merge-Topology.md`](docs/Merge-Topology.md). The whole stack merges into `main` **clean**; exactly **one** conflicting file repo-wide (`docs/Monetization-Decision.md`, add/add, a naming *decision*). `p4-pro` == `p2-replica` (`d9f95fd`) — no separate P4 branch exists. Re-verify: `AUDIT-REQUEST.md` C-MT-1…7 |
| **`:core` health** | **190 tests / 0 failures / 0 skipped across 14 classes — measured here 2026-08-11, fourteenth run**, up from a **187 / 0 / 0 across 14** baseline re-measured on the same probe in the same session before any edit. The +3 is all `SyncPumpTest` (19 → **22**). **No class was added, deleted or renamed**, and **no existing assertion changed** — the two pre-existing cursor assertions on unparseable elements are untouched by the diff, because on each page the claimed `seq` equals `latest` so the new ceiling does not bind (C-S4C-5). **Two of the three new cases were run against the pre-change `SyncPump.kt` and failed** while all 19 pre-existing `SyncPumpTest` cases passed; **the third passes on both sides by design** — it is a regression guard forbidding the clamp-everything "simplification", and is labelled as such rather than counted as evidence (C-S4C-4). Counts come from a reduced probe (`:core` alone, separate root, JDK 21 substituted for 17 — `api.foojay.io` egress-denied, B-7; recipe in C-S6A-1). The **gate** is CI. *Previous figure, thirteenth run:* **187 tests / 0 failures / 0 skipped across 14 classes**, up from a **185 / 0 / 0 across 14** baseline re-measured on the same probe in the same session before any edit. The +2 is `RelayClientTest` 25 → **26** and `SyncPumpTest` 18 → **19**. **No class was added, deleted or renamed**, and the one net-new case was run against the pre-change `RelayClient.kt` and **failed** while all 25 pre-existing `RelayClientTest` cases passed (C-S4S-4). **Three existing assertions were rewritten, not merely added to** — all three rested on the `{"seq":N,"envelope":…}` wrapper §2.1 now forbids, and one of them was **passing for the wrong reason** post-change (C-S4S-6). Counts come from a reduced probe (`:core` alone, separate root, JDK 21 substituted for 17 — `api.foojay.io` egress-denied, B-7; recipe in C-S6A-1). The **gate** is CI. *Previous figure, twelfth run:* **185 / 0 / 0 across 14 classes**, up from a **177 / 0 / 0 across 14** baseline re-measured on the same probe in the same session (the tenth run's figure, now re-derived rather than carried). The +8 is all `RelayClientTest` (17 → **25**). **No class was added, deleted or renamed, and no existing assertion moved** — verified the hard way: the 8 new cases were run against the pre-fix `RelayClient.kt` and **all 8 failed while all 17 pre-existing cases passed** (C-S4P-3). Counts come from a reduced probe (`:core` alone, separate root, JDK 21 substituted for 17 — `api.foojay.io` is egress-denied, B-7; recipe in C-S6A-1). The **gate** is CI; count its per-case `PASSED` lines and expect **185** (method in C-S3A-9) |
| **CI, eleventh-run push (android)** | **GREEN, and it reported before this iteration ended** — check run `93600690593`, job *Build and test*, `completed` / **`success`**, 21:15:55 → 21:23:37 UTC, run [31433025825](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31433025825). **`head_sha` `1867d0c` confirmed equal to `git rev-parse HEAD`** — read from the PR's own `head.sha`, not inferred from the check list, which follows the head. Single job, so green covers `checkCoreIsAndroidFree`, the vendored-vector diff against `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug` and the tracker check — none of which the reduced probe can run. **I did not count the per-case `PASSED` lines**, so **185** stays the probe's number, gate-corroborated as *green* and not as a count (C-S4P-11). **Note for the next session: the Actions REST API is 403 to `curl` here — poll with the MCP `get_check_runs` method. C-S6A-1 already said so and I hit it anyway (C-S4P-12)** |
| **CI, tenth-run push** | **GREEN, and it reported before this iteration ended** — run [31392794765](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31392794765) (run #69, event `push`), job *Build and test* (`93468326913`), conclusion **`success`**, 13:25:17 → 13:32:38 UTC. **`head_sha` `88b1d19`** read from the run's own field and matched against this branch's tip — not inferred from the PR's check list, which follows the head. The workflow is a **single job**, so `success` means every step passed: `checkCoreIsAndroidFree`, the vendored-vector diff against `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug` and the release-classpath tracker check — **all of which the reduced probe structurally cannot run**. **What I did *not* do this time: count the per-case `PASSED` lines.** The 177 figure therefore remains the probe's, gate-corroborated only as "green", not as a number (C-S6S-12). |
| **CI, previous push** | **GREEN, checked rather than predicted** — run [31374085226](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31374085226), job *Build and test* (`93409378480`), `success`, **`head_sha` `d361fa3`** (read from the run's own field, not from the PR's check list, which follows the head). From the job log: `:checkCoreIsAndroidFree` ✓ · `OK: all vendored vectors match 679a317…` · `:core:test` ✓ · `:app:test` ✓ · `:app:assembleDebug` ✓ · `:app:lintDebug` ✓ · `OK: no analytics or tracking SDKs on the release classpath.` **All 21 `PairingFlowTest` cases appear individually as `PASSED`, zero `FAILED` in the whole log** (C-S3A-9). **And the standing caveat "CI prints no totals" is wrong** — it prints one line per case, and they count to **154**, matching the probe, so the count is gate-corroborated for the first time. Still true: **a green gate on an uncalled class is not a pairing screen**. The android gate cannot run on this machine (no SDK/JBR, B-7 re-measured — `dl.google.com` `CONNECT tunnel failed, response 403`). The main-repo gate last ran on `9399d11`: run [`31346147785`](https://github.com/ShivaClaw/careerseeker/actions/runs/31346147785), **both jobs `success`** |
| **Android health** | **green on CI at `53710a6`** — [run 31292342258](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31292342258), success: vendored-vector step, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug` all `BUILD SUCCESSFUL`, plus *"OK: no analytics or tracking SDKs on the release classpath."* **Not run by me** — no Android SDK/JBR/Gradle on this machine. The **102 / 0 / 0 / 3** test *counts* remain carried from the S8 local run: Gradle does not print counts, so CI proves green, not the number |
| **Main-repo base of record** | `origin/main` = `00b3705` (gate `P0-BASE` superseded — S-Ladder §2.3) |
| **Main-repo PRs merged** | #27 `7f3e61e` · #28 `f0b9bd5` · #29 `160b317` · #30 `a8ef552` · #31 `00b3705` |
| **Main-repo PR open** | **#32 draft** — `claude/s5-entitlement-ack-spec`, S5 spec + vectors + the relay size-cap fix (head `9c05ef7`, CI green). **Not merged** (merging needs a full local gate this machine cannot run) · **#33 draft** — `claude/s4-pull-request-semantics`, **stacked on #32**, and now carrying **four** spec sections rather than S4's alone: §4.3.4, §2.1's pull response body, §6.4's transport cursor, and — as of 2026-08-11, fifteenth run — **§2.2's push response body plus §6.1's generalisation** (PQ-S6-2). **The branch name understates its contents**; renaming or retargeting it is on the return-day list beside the #32 question · **`claude/s2-relay-retention`**, also **stacked on #32** (head `310406a`), the retention fix + 6 tests. Whether #33 and the new branch should be retargeted at `main` once #32 lands is on the return-day list · **#35 draft** — `claude/s2-seq-bound`, stacked **#34 → #32**, §3.2's `seq` cap · **#36 draft** — `claude/s2-transport-vocabulary`, stacked **#33 → #32**, §2.3's transport vocabulary (head `4db3543`, CI green both jobs). **Topology hazard, measured and recorded here because no PR in the stack says it:** §2.1/§2.2 exist **only on #33**; #34 → #35 branch off **#32 as siblings**, so the `seq`-bound line does not contain the §2.2 that #36 extends. #36 was **re-based onto #33 after starting on #35** for exactly that reason. `git merge-tree` says the two lines **merge cleanly, before and after #36** (exit 0, no conflict list) — #33's additions sit in §2, the other line's in §3, and #36's tests were placed away from #35's hunks. **Merge order is still a human decision** |
| **Offline pin** | **598, unchanged — and confirmed by observation, not argued** (2026-08-11, **seventeenth** run): CI run `31516194482` on the branch tip `4db3543`, job *Build and offline harnesses* (`93861817135`), printed `SyncHarness … === 130 passed, 0 failed ===` then **`=== Offline total: 598 passed, 0 failed ===`** and `CareerSeeker alpha verification complete.` — so `Verify-Alpha.ps1` ran in full on a machine that is not mine. The prediction that the total could not move (no `.cs`, no harness, no `$ExpectedOfflineTotal`, no count-reporting doc, no vector byte) is therefore **upgraded from reasoning to measurement**. Verify with C-S2T-8. *Previously:* **598, unchanged** — and unchangeable by that iteration too (2026-08-11, **fifteenth** run): the only main-repo file written is `docs/Sync-Protocol.md`, so no `.cs`, no `.ts`, no harness, no vector byte and no count-reporting doc moved. `grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1` → **0**, **run here before the edit**, so the verifier makes no assertion against this doc and the drift trap is not armed against it. `$ExpectedOfflineTotal = 598` read directly. Verify with C-S6C-6. *Previously (2026-08-11, **fourteenth** run):* the only main-repo file written is `docs/Sync-Protocol.md`, so no `.cs`, no harness, no vector byte and no count-reporting doc moved. Stronger than last time: `grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1` → **0**, **run here before the edit**, so the verifier makes no assertion against this doc and the drift trap is not armed against it at all. Verify with C-S4C-6. *Previously (2026-08-10, **tenth** run):* **no file in the main repo was written at all**, so no `.cs`, no harness, no vector byte and no count-reporting doc. Previously (eighth run): unchangeable for the same reasons. CI's `Verify-Alpha.ps1` run on `9c05ef7` exited 0, and the script *throws* on drift, so success is the confirmation. **I did not read the `Offline total:` line myself** — the earlier direct sighting is run [`31292158471`](https://github.com/ShivaClaw/careerseeker/actions/runs/31292158471): SyncHarness `130 passed`, `Offline total: 598 passed, 0 failed` |
| **Relay suite** | **47 / 0 on `claude/s2-transport-vocabulary`, measured here 2026-08-11 (seventeenth run)** — up from **36 / 0** re-measured on its base `claude/s4-pull-request-semantics` in the same session before any edit. The +11 is one new `describe` block (§2.3). **Because no relay source changed, all eleven are pins by construction — none of them CAN fail against the current source** — so each was checked against a **deliberately mutated relay** instead of being asserted useful: four mutations, each reverted, **ten of eleven caught something**. The eleventh (`unpair is not a tombstone`) is **labelled a pin, not a regression catcher**. **`git diff … -- relay/src/` is empty**, which is the slice's central property. **Note the branch-dependent counts, which are the count-drift trap one branch over: 47 is `claude/s2-transport-vocabulary`, 36 is #33, 42 is `claude/s2-relay-retention`, 51 is `claude/s2-seq-bound`.** `npx tsc --noEmit` prints **55** errors here and **55 on the base** — unresolved `Env`/`Response`, because the project typecheck is `wrangler types && tsc --noEmit` and no `wrangler` ran; the only claim that supports is *unchanged by this diff*, and CI's *Typecheck* step passes because it generates types first. *Previously:* **36 / 0 on `claude/s4-pull-request-semantics`, measured twice 2026-08-11** (fifteenth run) — before and after the throwaway push probes, which were **deleted before committing**, leaving `git status --porcelain` empty. **This is not a regression from 42**: 42 is `claude/s2-relay-retention`'s figure and 36 is this branch's, and reading one as the other is the count-drift trap one branch over. **No relay source file was touched this iteration.** *The 42 figure, eleventh run:* **42 passed / 0 failed** (was 36) — measured with `npx vitest run`. `npx tsc --noEmit` exit 0 after `npx wrangler types`. The +6 is **2 regression tests** (they fail on the parent's `channel.ts` — C-S2R-10) and **4 pins** of behaviour that already existed: the 409's `latest` (a *cross-repo* contract the android `RelayClient` now parses, tested on neither side until now), unknown-field passthrough, `push` still counting expired rows, and the relay guard **not** being a durable replay authority. `npx wrangler deploy --dry-run` — a CI step — was **deliberately not run** (deploy embargo); CI settles it |
| **Shared vectors** | **unchanged again 2026-08-11 (fifteenth run)** — none added, none edited, and none could be: a push *response* is not an envelope, so no §3 vector can express §2.2's rules at all, exactly as no §3 vector could express §6.4's. `node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the generator.`, exit 0 — **28 is the branch figure**, `main` is 26. Vendored pin stays `679a317`; no vendored byte touched. *Previously, fourteenth run:* unchanged — none added, none edited, and none could be: a pull *page* is not an envelope, so no §3 vector can express §6.4's rule at all. `node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the generator.`, exit 0 — **28 is the branch figure**, `main` is 26, and reading it as a `main` figure is the doc-drift trap one repo over. The vendored pin stays `679a317` and no vendored byte was touched. *Previously, 2026-08-10 (tenth run):* — none added, none edited, and this time verified rather than asserted: all **26** vendored files diffed byte-for-byte against pin `679a317` from the main-repo checkout, **drift=0**. Previously (ninth run) also unchanged. **The count depends on the ref, and both were measured here:** `OK: **26** vector files match the generator.` on `origin/main` (`00b3705`), `OK: **28** …` on `claude/s5-entitlement-ack-spec` (`9c05ef7`), both exit 0. The standing "28" in these records is the **branch** figure — PR #32's two ack vectors are not on `main` until it merges — and reading it as a `main` figure is the doc-drift trap one repo over. A `pull_request` vector was **deliberately not added** (LOG §S4S-3): it would pin a body nobody disputes, test none of §4.3.4's three behavioural MUSTs, and — being `type: "envelope"` — would enter `SyncHarness`'s enumeration and move `$ExpectedOfflineTotal`, a number no .NET-less machine can measure |
| **Coordination bus** | `autonomy/claude-state` — updated this iteration (**seventeenth** run); files claimed named there. **This iteration wrote two main-repo files**, `docs/Sync-Protocol.md` and `relay/test/relay.test.ts`, on the new branch `claude/s2-transport-vocabulary` — already-claimed territory via #32/#33, so no new claim was taken; `relay/src/` is **held but not modified**. Terra read at iteration start **and again before writing the bus entry**: R6(b) BLOCKED, heartbeat unchanged at 2026-08-07T21:18, **claims no files** — no collision. *Previously (fifteenth run):* files claimed named there. **This iteration wrote one main-repo file**, `docs/Sync-Protocol.md` on `claude/s4-pull-request-semantics` — already claimed territory via #32/#33, so no new claim was taken. Terra read at iteration start: R6(b) BLOCKED, heartbeat unchanged at 2026-08-07T21:18, **claims no files** — no collision |
| **Relay client** | `RelayResult.Conflict` now carries `latest` — the relay answers a refused push with `{"error":"replay_rejected","latest":N}` (`relay/src/channel.ts:167`) and `RelayClient` was returning before reading the body, so §6.1's reconciliation input was unreachable. Null on the pairing 409s (`{"error":"exists"}`), so `PairingFlow`'s ambiguity reading is unchanged. **The relay itself needs no change and was not touched**; its own suite does not assert this field, and the relay suite did not run here |
| **Terra (Codex)** | R6(b) BLOCKED, PR #26 draft, files claimed: **none** — read at iteration start, no collision |

## Ladder

| Rung | Status | Evidence / reason |
| --- | --- | --- |
| **S0** re-entry + derivation | **DONE** | `docs/S-Ladder.md`; `LOG.md` §S0; `AUDIT-REQUEST.md` C-S0-1…9 |
| **S1** land the engine sync track | **DONE** | PRs #27–#30 merged; sync-track paths on main **0 → 54**; vector drift **0** in every check; C-S1-1…6 |
| **S2** engine publishes for real | **PARTIAL** | PR #31; engine ↔ **local** relay **30/30**, no deploy. **B-2 open:** no `/pair` page — **and this rung has now been worked twice without moving that**, which is the thing to read carefully before picking it again. **Transport half hardened twice.** (a) 2026-08-09, PR #32: the relay was 413ing envelopes §3.1 declares legal — a base64url *character* count tested against a *byte* budget left a **256 KiB** band untransmittable. (b) **2026-08-10, eleventh run** (`claude/s2-relay-retention`): **`GET /pull` had no expiry predicate**, so between a row's expiry and the TTL alarm collecting it the relay served ciphertext §2 says MUST be purged — retention enforced by a background job and by nothing else. `latest` took the same predicate, and that half prevents a **hang**: it is the client's loop bound, so a `latest` counting a row the page will not return is a bound the client can never reach. Suite 36 → **42**. Re-verify: C-S2R-1…15 **Transport hardened a THIRD time 2026-08-11, sixteenth run — PQ-S2-2 closed in part** (`claude/s2-seq-bound`, draft PR **#35**): **`seq` had no stated maximum anywhere**, and the relay's guard `Number.isInteger(seq) && seq >= 1` is **not a range check** — it rejects a fractional value but cannot reject a large one, since every double at or above 2⁵³ is necessarily integral, so the accepted range ran to **~1.8e308** and `1e300` pushed fine. New **§3.2** caps it at **`2^53 - 1`**, the largest integer the two 64-bit receivers and the relay's double all represent **exactly**; relay refuses above it with `400 bad_request`, **above** the `MAX(seq)` block so nothing is appended. **The finding is the read path, which PQ-S2-2 never costed:** `latest` comes from the same double, so 2⁶² returns `4611686018427388000` (**silently rounded**), 1e19 exceeds `Long.MaxValue` and 1e300 renders `1e+300` — and **both receivers parse `latest` strictly** (`RelayClient.cs:74` `GetInt64()` with no catch; the phone's `strictLong`). **One garbage counter therefore disables the `GET /pull` reconciliation §6.1 prescribes for that exact situation.** The precision note was wrong too: 2⁵³ then 2⁵³+1 gave `201` then **`409 replay_rejected`** — a strictly larger integer refused as a replay, reachable with one bad counter rather than 2⁵³ envelopes. Suite **42 → 51**; **7 of 9 new tests proven to fail** against the pre-change guard, the other 2 labelled **pins**. **Receiver rule is SHOULD, not MUST** — relay is the only ingress and neither receiver implements it, stated in a measured conformance note. **STILL OPEN: an in-range wedge** (a legitimate `9007199254740991`) still bricks the direction until TTL/unpair, and the reset endpoint is a **product decision for Brandon** — deliberately not a blocker. Re-verify: C-S2Q-1…7 **Transport hardened a FOURTH time 2026-08-11, seventeenth run — PQ-S2-3 closed, option (a)** (`claude/s2-transport-vocabulary`, draft PR **#36**, stacked #33 → #32): §2.2 pinned `push` and pinned "no other route's", so five routes' bodies were observed-but-not-normative. New **§2.3** pins them all — measured under miniflare and written down second, so **`relay/src/` is byte-identical** and the section **refuses nothing new**. **The finding: `pairing_unknown` never means the pairing is unknown.** After `DELETE /v1/{pairing}` — §7.2's exact condition — `pull`/`push`/`pair`/`DELETE` **all answer 401 `unauthorized`**, and `create` then answers **201** (the id re-bootstraps, so there is no tombstone). The transport code fires **only** on a pairing-id *shape* failure, checked pre-auth. **§7.2's condition has no transport code at all**, and v1 **pins the 401** rather than adding one, because a purged pairing being indistinguishable from one that never existed is what stops the relay answering "did this pairing ever exist?" to a wrong credential. **Cost → PQ-S2-4:** the phone maps any 404 → terminal `PAIRING_GONE` and 401 → *recoverable* `UNAUTHORISED`, so an unpaired phone waits for a bearer that cannot exist while the terminal state built for this is never entered — **read, not executed (B-7)**. **Two documents corrected:** PQ-S2-3's table said eight codes and its own command returns **nine** (`exists` dropped in transcription); C-S6C-5 was **self-contradicting** for the same reason. The vocabularies share **three** names, not two. Suite **36 → 47**; all eleven are **pins by construction** (no relay code changed) so each was checked against a **mutated relay** — **ten of eleven caught something**, the eleventh labelled a pin. Re-verify: C-S2T-1…8 |
| **S3** pairing screen | **PARTIAL** (was BLOCKED — a mislabel, corrected 2026-08-10, ninth run) | **The attempt's decisions are DONE** — `PairingFlow` + `RelayTokenLadder`, 21 tests, run here (C-S3A-1…7): the completion is built **once** per invite and retried verbatim; a **409 is ambiguous by construction** and goes to the human flagged rather than being read as either success or hijack (C-S3A-3 — `RelayClient`'s own transport retry can turn this phone's success into the relay's conflict); a code mismatch is terminal and is **not** a cancel; and **the phone never rotates the relay token** (§5.2.3 gives that to the engine — one `create(rotate_to)` call locks the engine out of `GET /pair` with the completion already stored, one-shot and unreadable). Built with **no Keystore and no camera**, which is the assertion that this half needed neither. **Still B-4's, in full:** the Keystore key and gate P2-KEYSTORE-FALLBACK's StrongBox → TEE → software chain, CameraX + ML Kit, every screen, and any hardware-backed claim. **No production caller** — `grep -rn PairingFlow app/src` prints nothing |
| **S4** transport loop | **PARTIAL** (was BLOCKED — a mislabel, corrected 2026-08-09) | **The pull decision is DONE** — `PullPolicy`, 17 tests, run here (C-S4A-1/-2). **Spec half DONE 2026-08-10 — PQ-S4-1 closed** (PR #33 draft): §4.3.4 pins the body, `since_seq` reserved. **Transport half DONE 2026-08-10** — `SyncPump`, 18 tests, run here (C-S4T-1…7): the cursor advances on envelopes *seen* not *applied*; the replica position is read per envelope, before the apply; a refused push releases the latch; **the seq that drives the cursor is the envelope's authenticated one, never the relay's page wrapper** (C-S4T-4 — a blind relay could otherwise truncate the stream without decrypting anything). Built with **no `DeviceSigner` at all**, which is the assertion that this half never needed S3's key. **Transport hardened 2026-08-10, twelfth run** — `parsePullPage` was partial in four places *and* called outside `request`'s try/catch, so a malformed 200 body threw out of `pull` past the `RelayResult` contract entirely (**9 of 12** malformed bodies measured escaping as exceptions before the fix). The 3 that did *not* throw were worse: an absent `latest` read `0`, and `latest` is what drives `moreAvailable`, so **omitting one field made the phone report a healthy, permanently-caught-up sync**. Now total and engine-compatible — both keys required and strictly typed, matching `src/Sync/RelayClient.cs`'s `GetProperty`/`GetInt64`; **one unusable element rejects the whole page**, because skip-and-continue advances the cursor past what it skipped, which is C-S4T-4's truncation attack in a different costume. `RelayClientTest` 17 → **25**. Re-verify: C-S4P-1…10. **Spec+wrapper half DONE 2026-08-11, thirteenth run — PQ-S4-2 closed** (PR #33): §2's route table defined the pull *request* and stopped, so the response body was pinned nowhere and §6.1 reconciled against a `latest` the document never defined. New **§2.1** pins the engine's reading — both fields required, `latest` a bare integer, elements **bare §3 envelopes**, the page truncatable so `latest` is the only "caught up" signal — and **refuses the `{"seq":N,"envelope":…}` wrapper**, which `parsePullPage` then stopped accepting. **Spec first, deliberately.** The wrapper was the only shape carrying a *second*, unauthenticated `seq` beside the envelope's own, so removing it makes that disagreement **structurally unreachable** and demotes `SyncPump`'s rule 4 to defence in depth. `RelayClientTest` 25 → **26**, `SyncPumpTest` 18 → **19**. Re-verify: C-S4S-1…7. **Still not E2E, and `SyncPump` has no production caller** — `grep -rn SyncPump app/src` prints nothing, so the crash this prevents is prospective. What is left really is wiring now (Ktor engine, `ApplyResult`→`ReplicaApplier` adapter, Room position source); only the E2E claim needs B-4. **Cursor half DONE 2026-08-11, fourteenth run — PQ-S4-3 closed** (PR #33): §6.2 governs `highest_accepted` and the **transport cursor was named nowhere in the protocol**, so an element failing the §3 parse — which has no authenticated `seq` — advanced the cursor by the number it *claims*, read leniently and authenticated by nothing. One unparseable element carrying `"seq": 1000000` walked the cursor past every envelope below it, and the cursor never moves backwards, so they were never re-requested: **history truncation without decrypting anything**. New **§6.4** caps it at the page's own `latest`. **Bounded, not refused** — refusing stalls the direction forever on one corrupt byte, which §6.2 forbids by name — and the tie breaks on asymmetry: a stall is recoverable and loud, truncation is silent, permanent, and reads as a healthy caught-up sync. **The closing note corrects the finding that opened it:** the bound does **not** protect envelopes the relay already holds (it could withhold those regardless); it removes the **forward-going** half, where an unbounded claim parks the cursor past seqs not yet issued and every later envelope arrives at a receiver that thinks it is past them. `SyncPumpTest` 19 → **22**. **Engine half (`src/Sync/RelayClient.cs`) unwritten, not blocked** — no .NET here. Re-verify: C-S4C-1…7. Findings → **none new** |
| **S5** entitlement ack | **PARTIAL** | **spec + vectors DONE** (PR #32 draft): §4.3.3 body, PQ-A2-1 (**re-opened and re-closed 2026-08-09** — its first close checked one direction of a two-directional claim; see the S2 row) + PQ-A2-2 closed, 2 vectors. **Phone applier DONE 2026-08-09** — `EntitlementAckApplier`, 9 tests, run here (C-S5B-2/-3). **C# applier NOT written** — no .NET here; unblocked, merely unwritten. PQ-A2-3 → **B-6**. **Re-derived 2026-08-11, seventeenth run, because that iteration's prompt described S5 as "NOT STARTED and genuinely NOT blocked" — both halves of which are wrong.** PR #32 has carried four commits since 2026-08-09; §4.3.3 and both `entitlement_ack` vectors exist and PQ-A6-1/A2-1/A2-2 are closed, so it is **not** not-started. What remains is the **two appliers** (C# engine, Kotlin phone) — the phone's is in fact already done — and **PQ-A2-3 is genuinely blocked by B-6**. Nothing here is startable in a cloud sandbox; it is a local session's slice |
| **S6** outcome marking (phone) | **PARTIAL** (was BLOCKED — a mislabel, corrected 2026-08-09; its *stated reason* corrected again 2026-08-10, tenth run) | **The marking decision is DONE** — `OutcomeMarkPolicy`, 22 tests (C-S6A-2/-6): Pro-gated, `no_reply` renderable but never offerable, a pending mark shadows the engine's value, retired by **value convergence** and bounded by disagreeing reports. **The send *decisions* are DONE 2026-08-10** — `OutboundQueue`, 20 tests, run here (C-S6S-4…-7): bytes built **once** and retried verbatim; a re-mark collapses onto an unbuilt entry and never a built one; **a 409 is read as neither success nor failure** (C-S6S-5 — `RelayClient` retries transport *inside* one push call, so a lost response arrives as a conflict on what the queue thinks is its first attempt, and attempt count cannot disambiguate); a 413 drops just that envelope while every other failure keeps the user's data; and **exactly one envelope is in flight**, which is what makes the 409 rule checkable at all. Built with a **stub signer and no Keystore** — the assertion that this half needed neither. **What is actually left:** the Keystore key and any hardware-backed claim (**B-4**), and the `:app` wiring — controls, transport loop, persisted p2e counter (**B-7**). No production caller (C-S6S-10). Findings → **PQ-S6-1** (nothing ever acks an `outcome`) and **PQ-S6-2** (§6.1 states counter reconciliation for the engine only). **Spec half of PQ-S6-2 DONE 2026-08-11, fifteenth run** (PR #33): §6.1 now binds **whichever side is sending** — `max(persisted_seq, relay_latest_for_that_direction)` — with the engine's e2p case kept as the worked example and the phone's p2e obligation stated, milder consequence and all. Closing it required **§2.2** first, because the rule points at the 409 body's `latest` and **no section defined that body**: PQ-S4-2's defect one level down. §2.2 pins all four push responses, **measured under miniflare** (probe deleted; relay suite **36/0 before and after** — 36 is *this* branch, the 42 in these records is `claude/s2-relay-retention`'s). Load-bearing measurements: the 409's `latest` is **per direction** (`e2p` at 90, replayed `p2e` 4 → `latest: 4`), 400/413 carry **no** `latest`, 201 means *appended* and nothing more, and a direction holding nothing answers **201 to seq 1**. §2.2 also names the trap underneath: §7.2's `{code}` payload vocabulary and the relay's `{error}` transport vocabulary **share two names with identical meanings**, and `bad_request` + four others appeared **zero times** in the document — → **PQ-S2-3**. **The MUST is written against implementations that do not meet it, deliberately and out loud** — a measured conformance note names both gaps by ID. That is not the thirteenth run's §2.1 defect, for three stated reasons: the rule was **already normative for one of the two senders**, persistence was **already** required of both by §6.1's first sentence, and this is a **safety property rather than error-reporting style**. **S6 stays PARTIAL: this closed a question against the send path, not the path** — the Keystore key (B-4) and the `:app` wiring (B-7) are untouched. Re-verify: C-S6C-1…6. Findings → **PQ-S6-3**, **PQ-S2-3** |
| **S7** Play-readiness pack | **PARTIAL** | upload keystore generated (§3b); Play floor **re-verified live**; `versionCode` scheme recorded → `docs/S7-Release-Signing.md`. Listing copy, data-safety dossier, privacy delta, account-day checklist and assets **already exist on `claude/p5-store`**; pricing rewrite on `claude/todos-pq1-pricing` — *not* duplicated here. No `.aab`, no Console action; screenshots need B-4 |
| **S8** hardening | **PARTIAL / BLOCKED — B-5** | migration test written; Room 2.8.4 cannot open a file-backed DB under Robolectric. Lint hold, full gate, bundle refresh **done** |

**S3, S6's send path, and S4's E2E proof are blocked on one checkbox** — Android Studio → SDK Tools →
*Android SDK Command-line Tools (latest)*. Mission §3a authorized *using* `sdkmanager`; it does not
exist here, and installing the toolchain that provides it was not authorized. B-5 is downstream of
the same thing (the migration test runs fine as an instrumented test).

**Read that sentence precisely: it blocks the E2E *claims*, not every line of code in those rungs.**
2026-08-09 found S4 carrying a blanket `BLOCKED` label over a decision layer that needed neither an
emulator nor a device key, and building it cost one iteration. Before believing any rung label here,
check which half of the rung the blocker actually touches. **S6 is the next candidate for the same
re-read** — its `OutcomeBadge` display half is already done, and only the device-signed send needs
S3's key.

**S3, S4, S5 and S6 now all sit at PARTIAL, and the reason is the same four times over: each has a
decision half that runs anywhere and an execution half that needs a machine this program's cloud
sessions are not.** S3 has its attempt ordering; the Keystore key, the camera and the screens are
still B-4's, and unlike S4's and S5's remainders that one cannot be unblocked by adding a toolchain. S5 has its spec, vectors and *phone* applier; the **C# applier** is unwritten.
S4 has its pull *decision*; the `:app` wiring is unwritten. S6 has its *marking decision*; the
signed send is unwritten.

Read the difference carefully, because it is not the same in all three. **S4's and S5's remaining
halves are unblocked and merely unwritten** — give them a machine with an Android SDK and .NET
respectively and they can be done today.

> **Corrected 2026-08-10 (tenth run).** This paragraph used to continue: *"S6's remaining half is
> genuinely blocked: it needs a device-signed envelope (§5.4), which needs S3's Android Keystore
> key, which needs an AVD that does not exist (B-4). A machine with an SDK is necessary but not
> sufficient there."* **That was too strong**, and the codebase already showed why:
> `OutboundEnvelopeFactory` takes the signer as an injected `fun interface DeviceSigner`, and
> `OutboundEnvelopesTest` has been building and asserting *signed* envelopes in this sandbox since
> A6. What needs a Keystore is the claim that a key is **hardware-backed**. What does not is
> everything deciding *when* an envelope is built, *which* bytes go back on a retry, and *what* each
> relay answer means — now `OutboundQueue`, 20 tests, run here. **S6's remainder is B-4's for the
> key and B-7's for the `:app` wiring, and a machine with an SDK gets most of the way.**

## Open blockers

| ID | Status |
| --- | --- |
| **B-1** pairing UI | gate answered; device half **still blocked** — see B-4 (the earlier "scheduled at S3" note was written before anyone checked `sdkmanager` existed) |
| **B-2** live E2E | **most of the way closed** — engine ↔ local relay proven 30/30; remaining gap is exactly the `/pair` page. **Unmoved 2026-08-11 (sixteenth run)**, and worth stating: S2's *transport* has now been hardened three times (size cap, retention predicate, `seq` bound) while B-2 sat still — the transport was never what B-2 was about |
| ~~**B-3** vector drift~~ | **CLOSED** — 26/26 byte-identical to pin `679a317`, confirmed by CI's own step (run `31278769047`) |
| **B-4** emulator lane | `sdkmanager`/`avdmanager` absent; blocks **S3's device and screen halves** (not its attempt ordering — see the S3 row), **S6's device *key*** (not its marking decision and not its send decisions — corrected 2026-08-10, tenth run), S4's **E2E proof** (not its decision layer) and B-1's device half. **Cost shrunk a fourth time 2026-08-10** (tenth run) |
| **B-5** migration test | Room 2.8.4 + Robolectric cannot open a file-backed DB; test kept under `@Ignore` with the diagnosis |
| **B-6** unknown-field vector | PQ-A2-3 cannot be closed by adding a vector: the engine has no inbound wire-JSON parser, so it would *accept* the envelope and turn the gate red. **Re-verified 2026-08-09** to two lines — `EnvelopeReceiver.cs:33` takes a parsed record, `SyncHarness/Program.cs:696` cherry-picks keys (C-S5B-5). Parser first, vector second |
| **B-8** persisted p2e counter | **new 2026-08-10** (tenth run), **sharpened + spec half closed 2026-08-11** (fifteenth run). §6.1 requires the sender's counter to survive a restart; nothing in the repo implements one that does. **The earlier wording — "every `SeqSource` is in-memory" — undersold it**: measured, `SeqSource`'s **only implementation in the tree is a test double** (`OutboundQueueTest.kt:30`), with **zero `:app` references**, so there is no production counter to persist rather than an in-memory one to replace. `OutboundQueue.reconciled()` assumes a caller that owns it. Downstream of **B-7** (it belongs in Room). The failure is *reported* rather than silent: a refused push halts on `COUNTER_BEHIND` with the relay's `latest`. **The rule it must satisfy is now written down for this sender** (§6.1, PQ-S6-2 closed), and §2.2 pins a second, cheaper source of the same number — the 409 body's `latest`, which `conflictLatest` already reads. **The reconciliation logic exists; the persisted counter is the whole remaining hole** |
| **B-7** cloud sandbox egress | **new 2026-08-09, re-measured 2026-08-10** (`dl.google.com`, `api.foojay.io` → `CONNECT tunnel failed, response 403`; `repo.maven.apache.org` → `200`). AGP/`androidx`/JDK-17 are unfetchable in a cloud session — the android gate is unrunnable here for a reason *independent* of B-4. CI is the unblock, not a checkbox. **Its cost shrank 2026-08-10**: S4's ordering decisions no longer sit behind it |

## Next intent (in order)

**Eleventh correction 2026-08-11 (seventeenth iteration), and it is about trusting a task description
over the tree.** The iteration prompt stated the ladder as "S5 is NOT STARTED and genuinely NOT
blocked", named S5 as the strongest candidate, and listed four sub-tasks. **Three of the four had
been done since 2026-08-09** (PR #32: §4.3.3, the two `entitlement_ack` vectors, PQ-A2-1 and
PQ-A2-2), and **the fourth is blocked by B-6**, which is recorded in `BLOCKED.md` with an explicit
note that a session handed this task should read the entry *before* starting. The prompt's summary
was accurate as of roughly the fourth iteration; **thirteen have run since**. Nothing was lost,
because the mandatory fetch is what surfaced it — but the near-miss is that a fetch which is *run*
and not *read against the task* buys nothing. **Rule one is `git fetch`; the rule underneath it is
that the tree outranks the prompt, including on the question of what is already done.**

**And a second one from the same slice, about a correction that was itself uncorrected.** PQ-S2-3
recorded the relay's transport vocabulary as **eight** codes and printed the command that produced
it. Running that command, on the commit the question itself cites, returns **nine** — `exists` was
dropped between running and writing. The number then propagated: into §2.2's prose in the main repo,
and into `AUDIT-REQUEST.md`'s **C-S6C-5**, whose *Expected* line said eight while its command said
nine — **a re-verification entry that fails against itself**, which is the one failure mode that
document exists to prevent. Both are corrected in place, with the correction stated rather than
overwritten. **A transcribed measurement is not a measurement. Re-run the command when you cite it,
especially when you are citing your own.** This iteration then nearly shipped the same defect: the
first `sed` recipe written into C-S2T-6 matched three call sites instead of one and produced
`5 failed` against a documented `4 failed` — caught only because it was **run before being written
down**, which is the habit that separates the two outcomes.

**Tenth correction 2026-08-11 (sixteenth iteration), and it is about how a deferral spreads rather
than about any one rung.** PQ-S2-2 sat open for two iterations behind the note that PQ-S2-1 and
PQ-S2-2 both carry "an explicit *do not close either from a sandbox*". **Only PQ-S2-1 carries one.**
Its "to close" begins *"On a machine with .NET"* — correctly, because closing it means changing two
engine test fixtures. PQ-S2-2's says *"Spec first, relay second"* and names no machine at all; both
halves are Markdown and TypeScript, and it closed here in one slice. The two questions were opened in
the same iteration, got summarised together once, and **the summary is what every later iteration
read**. This is the fifteenth run's ninth correction in a new costume — that one was a deferral whose
reason was about *cost*; this one is a deferral whose reason **belonged to a different question**.
**Before inheriting a deferral, open the question it was written against and check the reason is
about the question you are holding.** The cheapest possible check, two iterations unpaid.

**And a second one from the same slice, about how a question's own measurements can mislead.**
PQ-S2-2 measured the wedge at `Number.MAX_SAFE_INTEGER` and dismissed the precision divergence as
"unreachable in practice — 2⁵³ envelopes is not a number this product produces". Both readings were
too narrow, in the same direction: the accepted range actually ran to ~1.8e308, and reaching 2⁵³
needs **one** bad counter rather than 2⁵³ envelopes. A question's *worked example* is not its
*bound*, and "unreachable by counting" is not "unreachable". **Re-measure the boundary before
building on where a previous run stopped measuring.**


**Correction 2026-08-09 (third iteration).** The line that stood here — "the Linux cloud sandbox has
Node and git and nothing else" — was **wrong**, and acting on it would have skipped work that is
genuinely doable. A cloud session **can** run: `:core` (pure Kotlin/JVM, every dependency on Maven
Central), `relay/` (Node + vitest + miniflare), `generate.mjs`, and every doc. It **cannot** run
`:app`, the android gate, or `Verify-Alpha.ps1` — no SDK, no .NET, and `dl.google.com` is an egress
**policy denial** (B-7), which is a firmer wall than "not installed". Judge a rung by which module
it lands in, not by the machine's label.

**Second correction 2026-08-09 (fourth iteration), and it generalises the first.** S4 was labelled
`BLOCKED — B-4` on a reason that covered only part of the rung: "needs S3's device key + an
emulator". The E2E *proof* needs both. The *decision layer* needed neither — `pull_request` is not
a state-changing kind, so §5.4 asks for no signature and no Keystore key, and the whole thing lives
in `:core`. A rung's blocker applies to the claims that actually depend on it; check which half you
are looking at before believing a one-line label. The same question is worth asking of S6, whose
display half is already done and whose blocked half is the device-signed send.

**Fourth correction 2026-08-10 (seventh iteration), and it is about the standing prompt rather than
this file.** The scheduled prompt that drives these iterations carries a ladder summary saying **"S5
(entitlement ack) is NOT STARTED and is genuinely NOT blocked"**, and nominates S5's spec half —
§4.3 amendment, ack vector, PQ-A2-1/-2/-3 — as the slice. **All of that except PQ-A2-3 landed on
2026-08-09** (PR #32 draft), and the phone applier landed with it. The prompt is a stored snapshot
and does not re-read itself; **this file and `LOG.md` are the state, the prompt is not.** An
iteration that trusts the summary redoes finished work or writes a duplicate spec section. Its own
instruction is the right one — *"verify it; do not trust this summary"* — and the verification is
the mandatory fetch plus these records, in that order. The same is likely to be true of the S2/S7/S8
lines in that summary; check before acting on any of them.

**Fifth correction 2026-08-10 (eighth iteration), and it is about a word rather than a fact.** The
line above and this file's own S4 row both described S4's remainder as "`:app` wiring". *Wiring*
sounds mechanical, and it was hiding **four ordering decisions**, each of which has a wrong version
that compiles, renders correctly, and reports nothing: which mark drives the transport cursor, when
the replica position is read, whether a failed push releases the latch, and **which `seq` is
authenticated**. Written in `:app` they would have been checkable only on a machine with an Android
SDK — which no session in this window has had. They are now `SyncPump` in `:core`, 18 tests, run
here. The general lesson: **when a rung's remainder is described with a word that sounds mechanical,
enumerate it before believing the word.** The same question is worth asking of S2's "`/pair` page"
and S6's "signed send" — how much of each is a decision rather than an I/O call?

**Eighth correction 2026-08-11 (thirteenth iteration), and it is about a defect class the previous
twelve did not name.** Every earlier correction here found something *too broad* — a rung label
reaching past its blocker, a closure argument checked in one direction. This one is the mirror:
**§2.1's first draft made a rule too strong.** It required a receiver to report an unreadable pull
body "as an unavailability"; the phone does, and the engine's `PullAsync` lets the parse throw
instead. One clause, written in the same hour, would have made shipping engine code non-conformant
on a question of **error-type style rather than safety** — and the only reason it was caught is that
the draft was checked against `src/Sync/RelayClient.cs` instead of against the assumption that the
engine matched the phone. **A spec tightening ahead of its implementations is the same defect as an
implementation tightening ahead of its spec**; the relay's size cap is the precedent for the second
direction and this is the first recorded instance of the first. The rule now splits: MUST for the
property both receivers hold (never a silent empty page), SHOULD for the mechanism they differ on.

**And a second one from the same slice, about tests rather than specs.** Removing the wrapper broke
**three** existing assertions across two files, where the queued note had predicted one. Two failed
loudly. The third — `an envelope that does not parse is discarded and does not stall the cursor` —
**kept passing while testing something other than its title**: it wrapped a malformed envelope to
exercise §3's unknown-field rule, and after the change the *wrapper* was what failed to parse, with
the numbers lining up so every assertion still held. Nothing flags this; it was found by reading
every `wrappedPage` caller after the one failure rather than stopping at the failure. **When a
change removes a shape, grep for every test that constructs it — the ones that still pass are the
ones to read.**

**Ninth correction 2026-08-11 (fifteenth iteration), and it is about the records' own deferrals
rather than about a rung label.** Every previous correction found something *too broad* (a label
reaching past its blocker) or, once, *too strong* (a spec rule ahead of its implementations). This
one is about a note that was **too settled**. PQ-S6-2 was deferred on 2026-08-10 with a reason that
reads like a conclusion: *"a third stacked spec edit, made from a sandbox that cannot run
`Verify-Alpha.ps1`, is a poor trade for a paragraph that changes no behaviour."* Every clause of
that is true, and it is still the wrong call — because it is an argument about **cost**, made before
anyone checked the engine against the rule being deferred. That check was one `sed` over
`src/Engine/Program.cs` and found the engine implementing half of §6.1 with the comment ten lines
above stating the other half. **A deferral reason about cost is not a finding about substance, and
it does not expire on its own.** Before inheriting one, re-run the cheapest check that could
contradict it.

**And a second one from the same slice, about where a question's real content hides.** PQ-S6-2's
"to close" paragraph ended with an aside — the 409 body carries `latest`, and *"that field is
currently documented nowhere in `Sync-Protocol.md` despite being implemented and relied upon"*. That
aside was **the larger half of the question**: a §6.1 rule pointing at an undefined response body is
PQ-S4-2's defect one level down, and closing PQ-S6-2 without §2.2 would have produced a rule whose
key term no section defines. **When a question's "to close" section contains an "optionally, note
that…", read it as a second question rather than as a footnote** — the author who wrote it had
already seen the problem and had not yet counted it.

**A finding this slice turned up, in `:core`, and it belongs on the return-day list.**
`RelayClient.parsePullPage` accepts **two** page shapes, and in the `{"seq":N,"envelope":…}` shape
the relay's reported sequence number and the envelope's own can **disagree**. The envelope's `seq`
is in the AAD and the AEAD tag covers it; the relay's is authenticated by nothing. `SyncPump` now
uses only the authenticated one, so a relay reporting `seq: 999` on an envelope carrying `5` cannot
make the phone skip `6..999` — **a blind relay could otherwise truncate history without decrypting
a byte it is unable to read.** The deployed relay splices envelopes back verbatim and does not do
this; the change is that the phone no longer *depends* on that.

> **CLOSED 2026-08-11 (thirteenth iteration).** The paragraph above ended *"Left open deliberately:
> whether `parsePullPage` should accept the wrapper shape at all… a slice, not a drive-by."* It got
> that slice. §2.1 forbids the wrapper and `parsePullPage` no longer accepts it, so
> `parsePullPage` accepts **one** page shape and the two sequence numbers of the paragraph above
> **cannot exist in the same element any more**. The prediction that it would cost one
> `RelayClientTest` assertion was an undercount — it cost **three, across two files** (S4S-6) — but
> the "not a drive-by" judgement was right, and doing it spec-first is what kept it from being the
> size-cap mistake in reverse.

**Third correction 2026-08-09 (sixth iteration), and it is about the records rather than the code.**
The previous two corrections were about *rung labels* being broader than their blockers. This one is
about a **closed question that was not closed**. PQ-A2-1's close reasoned: the relay's cap is
stricter than the receivers' ⟹ nothing the relay carries can be rejected on size ⟹ *"so there is no
gap"*. The first implication is true and the conclusion does not follow — it never checked the other
direction, where an envelope both receivers accept is refused by the relay. Running that direction
took one command and found a 256 KiB band. **When a closure argument is an implication, check both
directions before writing "closed".** The relay had been green on CI the whole time, because its own
test suite asserted the buggy number.

**A fourth thing worth carrying forward: `relay/` is a first-class verifiable lane here.** Node +
vitest + miniflare, no egress denial, and CI runs the same suite. Alongside `:core`, that is the
second module a cloud iteration can actually gate. Iterations 3–5 all landed in `:core`; nobody had
re-read `relay/` since P1.

**Sixth correction 2026-08-10 (ninth iteration), and it is the third instance of one pattern, so
it is written here as a rule rather than as another anecdote.** S3 carried a blanket `BLOCKED — B-4`
label. The label was right about the camera, the Keystore key and the screens, and wrong about the
rung: the pairing *attempt* — which body is sent, once or twice, what a 409 means, when the human's
answer is asked for, who rotates the relay token — needed neither a device nor an emulator, and it
was sitting unwritten in `:app` where no session in this window can compile it. That is now
`PairingFlow`, 21 test cases, run here.

Three rungs have now been found this way (S4 and S6 on 2026-08-09, S3 today), so:
**a rung's blocker applies to the claims that depend on it, and "needs a device" almost never covers
a rung's ordering rules.** Before believing any one-line label in this file, ask which half it
touches. **S2's `/pair` page is the remaining candidate** — but note the asymmetry that makes it
different from the three above: its decision layer is C#, so moving it somewhere testable needs
.NET, not merely a different module. That is a real constraint, not a mislabel.

**A finding this slice turned up, and it is about a status code.** `POST /v1/{pairing}/pair`
answering **409 cannot be read as "somebody else beat us"**, because `RelayClient` retries transport
failures internally (`RelayClient.kt:186`): an attempt that stores the completion and loses its
response is followed by one that sees the relay's own conflict. **This phone's success can therefore
arrive as `RelayResult.Conflict`**, and nothing available to the phone separates that from a
stranger's completion. Both obvious readings are wrong — abort, and a network hiccup kills a good
pairing; accept, and a real race hides behind a screen identical to the happy path. `PairingFlow`
resolves it the way §5.2 already intended: the confirm code matches the desktop **iff** the stored
completion is ours, so the flow flags the race and lets the human arbitrate. **The effect is that
the confirm code is load-bearing rather than decorative, and a UI that auto-confirms would delete
the only thing that distinguishes the two cases.** Neither the relay nor the client is wrong here;
the phone simply no longer needs 409 to mean one thing.

**Seventh correction 2026-08-10 (twelfth iteration), and it is the counterpart to all the "which
half does the blocker touch?" corrections above.** Those found *decision* work hiding behind a
device-shaped label. This one found something smaller and easier to miss: **a hardening that had
already been reasoned out, written down, and applied to only one of the two places it belonged.**
`conflictLatest` carries a KDoc explaining that it is "deliberately total" because "the one thing
this client must never do is convert a relay decision into an unavailability" — and three functions
above it, `parsePullPage` did exactly that, in four places, for the *other* body the same untrusted
relay controls. The argument was already correct and already committed; nobody had asked where else
it applied. **When a file states an invariant in prose, grep for the other places that invariant
governs** — the sibling that was never hardened does not announce itself, and the tests will be
green either way.

The related habit this slice is evidence for: **the silent wrong answer outranks the loud one.**
Nine malformed bodies threw, which is ugly and obvious. Three returned a plausible value, and one of
those turned "the relay omitted a field" into "you are fully caught up" — the failure nobody sees.
The loud ones drew attention; the quiet ones were the reason to do the slice.

0. **S4's remaining half, and it is now genuinely mechanical — which it was not before 2026-08-10.**
   The four ordering *decisions* that used to hide inside the phrase "`:app` wiring" landed as
   `SyncPump` in `:core` (18 tests, C-S4T-1…7). What is left needs the Android toolchain and is
   exactly three things: (a) an adapter mapping `ApplyResult` → `ApplyDisposition` — the body is
   written out verbatim in `ReplicaApplier`'s KDoc, and **the `snapshot` branch is the one that
   matters**, because `APPLIED_SNAPSHOT` is the only disposition that clears the latch; (b) a
   Room-backed `ReplicaPositionSource` reading the *persisted* mark and `snapshotSeen`; (c) the
   `:app` Ktor engine dependency (**3.1.3**, never 3.2.0 — see the standing pins) and a coroutine
   calling `open()` once and `pump()` on a tick, then the WSS route as a nudge to pump sooner.
   Only the final E2E claim needs an emulator. Verify with C-S4T-8's `grep -rn SyncPump app/src` —
   written to print nothing until the adapter exists.
1. **S5's remaining half — the C# applier.** The **phone applier landed 2026-08-09**
   (`EntitlementAckApplier`, 9 tests, measured here): parse §4.3.3's body, refuse every way it can
   fail, hand two fields to `ProState.afterEngineAck`. What is left is engine-side and needs .NET:
   answer `entitlement_ack` after `GoogleSignedPayloadVerifier` accepts. Still **unblocked, merely
   unwritten**.
   Then, once PR #32 merges, a **re-vendor slice**: bump the pin off `679a317`, pull the two ack
   vectors in, and move `EntitlementAckTest`'s transcribed bodies onto a `type`-filtered section in
   `ProtocolVectorsTest` beside the others. That moves assertion counts, so expect the full
   drift-trap sweep. Until then §10.2 holds — no consumer asserts against those vectors — and the
   app stays honestly Free, because `:app` has no caller for the applier yet.
2. **S6's remaining half — the signed send — and it is the one item here that is truly blocked.**
   The marking decision landed 2026-08-09 (`OutcomeMarkPolicy`, 22 tests). What is left: a screen
   that renders `offerFor` as controls and `DisplayedOutcome.pending` as a visibly-unconfirmed
   badge, a `DeviceSigner` backed by a real Android Keystore key, and a transport that pushes
   `OutboundEnvelopeFactory.outcome(...)` and reports back through `onSent`/`onSendFailed`, feeding
   every applied payload's carried outcome to `onEngineOutcome`. The key is S3's and needs an AVD
   (B-4), so unlike S4/S5 this cannot be done by adding a toolchain alone. Verify with C-S6A-8 —
   written to fail while the policy has no caller.

3. **Finish S2 — the `/pair` route.** All that stands between B-2 and closed. The relay half of S2
   is now hardened (the size-cap fix above); this is the C# half and needs .NET. The vault and publisher
   wiring landed in PR #31 and the handshake is vector-proven. Needed: create a `PairingManager`,
   render the invite (`PairingInvite.ToQrJson()` is the exact payload — **a QR encoder is the only
   genuinely new dependency**), poll `RelayClient.TakeCompletionAsync`, show the confirm code for the
   human to compare, write `SyncPairing` to the vault.
3b. **S3's remaining half — the device and the screens, and it is genuinely blocked.** The attempt
   ordering landed 2026-08-10 (`PairingFlow` + `RelayTokenLadder`, 21 tests). What is left: a
   CameraX preview with an ML Kit QR decode feeding `PairingFlow.begin`, an Android Keystore ECDSA
   P-256 key supplying `deviceSigPublic` with gate P2-KEYSTORE-FALLBACK's StrongBox → TEE →
   software chain (**persistent indicator + audit-trail entry** on a downgrade), a confirm screen
   rendering `AwaitingConfirmation.confirmCode` — which **must not auto-confirm**, per the 409
   finding above — and persistence of `PairedPairing` on `confirm(true)` only. Needs B-4: whether a
   key is hardware-backed is a claim only a device or an emulator can settle. Verify with
   C-S3A-8's `grep -rn PairingFlow app/src`, written to print nothing until the screen exists.

4. **Tick the SDK Command-line Tools checkbox**, then the whole emulator lane is unattended and
   S3 → S4 → S6 unblock in order, along with B-5.
5. **B-6**, whenever the engine is being touched anyway: the inbound wire-JSON parser, then the
   `invalid-unknown-field` vector. In that order — the reverse turns CI red.

**Do not re-vendor the shared vectors casually.** Upstream is now 28 files, this repo is pinned at
26 (`679a317`) and **verified 26/26 byte-identical 2026-08-09** (C-MT-6). That is not drift — the
pin is a deliberate contract — and a re-vendor should happen in the same slice as the Kotlin applier
that consumes the new files, not on its own.

## For return day — decisions, not blockers

Queued here rather than in `BLOCKED.md`: nothing below is obstructed, each is Brandon's call by
policy. Full derivation in [`docs/Merge-Topology.md`](docs/Merge-Topology.md).

1. **The product name, and it is on the critical path for the store listing.** The one merge
   conflict in the repo is `docs/Monetization-Decision.md`: `p1-runbook` records "the Windows app is
   **CareerSeeker**, not *Basic*" as **decided 2026-07-23**; the lineage carrying all recent work
   (this branch) still calls it an open suggestion and still prints "CareerSeeker **Basic**" in the
   price table. `docs/store/Play-Listing.md` derives from that table. Recommended: take the
   `p1-runbook` side, then grep the store copy for "Basic" before submission.
2. **Merge order** — `docs/Merge-Topology.md` §7. Nothing needs a rebase or a force-push; every
   branch is 10 behind `main` and merging *into* `main` absorbs it.
3. **Run the full gate on the merged tree, not on the branches.** #5 and #6 auto-fuse three screen
   files with no conflict and **no gate has ever run on the combination** (§6). This is the
   `Host.cs` failure mode, and P4's hard-coded-port bug is the precedent: a clean merge is not a
   passing gate.
4. **Whether #3–#6 should be retargeted at `main`** as the stack lands, rather than at sibling
   branches.

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

**Sixth correction 2026-08-10 (tenth iteration), and it is about how a blocker gets written down.**
The previous three corrections each found a rung whose one-line `BLOCKED` label was broader than the
thing actually blocking it. This one found the same shape in a sentence that had *already been
corrected once*: the fifth correction re-read S4 and S6, moved both to `PARTIAL`, and then wrote
that S6's remainder was "genuinely blocked" on the Keystore — narrowing the label while restating
the same over-reach one level down. The tell was available in the repo the whole time:
`OutboundEnvelopesTest` builds and asserts **signed** envelopes in this sandbox, so "needs a device
signature" was never the same statement as "needs a device". **When you narrow a blocker, check the
narrowed version against the code too — a correction is exactly where the next over-reach hides.**

**And the standing prompt's ladder summary has now been wrong in the same way twice.** It still says
S5 is "NOT STARTED", which the fourth correction already answered, and it nominates S5's spec half
every iteration. The instruction to *verify, do not trust the summary* is the right one; the
verification is the mandatory fetch plus these records, in that order.

**Seventh correction 2026-08-10 (eleventh iteration), and it is about where a defect hides rather
than about a label.** The four previous corrections all found the same shape — a rung's `BLOCKED`
label reaching further than the thing blocking it. This one is different, and it generalises the
*size-cap* precedent instead. The relay had a live defect (`GET /pull` serving envelopes §2 says
MUST be purged) sitting under a **green suite of 36 tests**, for the same structural reason the
size-cap bug did: **the suite tested the path the author was thinking about.** `purgeExpired removes
only expired rows` tested the collector directly and never once asked what a *reader* sees before
the collector runs. So: **when a rule is enforced in one place, ask what the other paths do about
it** — the collector and the reader are different code, and only one of them had the predicate.

The method that found it is worth reusing verbatim, because reading the code would not have. Five
probes were written against the running Worker under miniflare — deliberately asserting *wrong*
expected values so the runner prints the measured one in its diff (`console.log` does not escape the
Workers pool). Four of the five answers were things the suite did not know. That trick is written up
at the top of `AUDIT-REQUEST.md`'s C-S2R-8…15 block.

**Two things this iteration deliberately did not do, and the reason is the same both times.**
PQ-S2-1 (the relay never checks the `pairing` field it declares) and PQ-S2-2 (one out-of-range `seq`
wedges a direction forever) are both real and both were left alone, because **tightening what the
relay refuses is exactly the size-cap bug's shape** — a relay refusing what the spec declares legal
— and the harnesses that would catch an over-tightening need .NET. PQ-S2-1 also has direct evidence
against a blind fix: `tests/EngineHarness/Program.cs:2268` and the relay suite's own helper already
emit pairing ids that would fail a shape check. **Spec first, relay second, on a machine with a
gate.** Do not close either from a sandbox.

**And a caution about this rung specifically.** S2 has now been worked twice — the size cap, then
retention — and **B-2 has not moved either time**, because both slices were the transport half and
B-2 is the `/pair` page. Both were worth doing; neither advanced the rung. If a future iteration
picks S2 again expecting progress on B-2, it will find the same C# wall. A rung does not advance
because work happened in its neighbourhood.

**What is genuinely available to the next cloud iteration, in order** (rewritten 2026-08-11,
**fifteenth** run). S2's `/pair` page and S5's C# applier need .NET; S4's `:app` wiring, S6's
controls and every screen need an Android SDK (B-7); S3's key and any hardware claim need B-4.
**PQ-S4-3 closed (fourteenth run) and PQ-S6-2 closed (this run), so both of the previous list's top
two entries are spent.** What is left that a sandbox can still reach:

1. **PQ-S6-1 — nothing ever acknowledges an `outcome`, and the engine reports it applied either
   way.** Now the strongest remaining candidate, and it is the same shape as the last three closures:
   the decision is a spec sentence in §4.3 and the consequence lands in `:core`. Its own write-up
   says the cheap option is **not** clearly right — (a) add `outcome_ack` and derive
   `InboundDispatcher`'s result from the applier, or (b) declare marks fire-and-forget and specify
   convergence-with-a-bound. **(a) is recommended there**, but note the asymmetry that makes this
   harder than PQ-S6-2 was: (a)'s implementation half is **C#** (`InboundDispatcher`), so a sandbox
   can write the spec and the phone's reading of it but cannot close the loop. Say so rather than
   implying the rung moved. The `pull_request` extension to the same question (an `InboundOutcome`
   that reports *reaching a `case`* rather than completing an action) is the same three lines of
   reasoning twice and belongs in one commit with it.
2. **PQ-S2-3 — the relay's transport error vocabulary**, opened this run. Option (a) is to extend
   §2.2 into a full transport-response section covering `create`, `pair`, `pull`, `live` and
   `DELETE`, measured the same way push's was — and the measuring is **cheap and proven**: the probe
   recipe is written out in `AUDIT-REQUEST.md` C-S6C-2, including the vitest-truncation trap that
   cost this run two passes. This is the lowest-risk item on the list, because it documents
   behaviour rather than changing any.
3. `relay/` (Node + vitest + miniflare, no egress denial). PQ-S2-1 and PQ-S2-2 are still open there
   and still say **do not close either from a sandbox** (spec first, relay second, on a machine with
   a gate).
4. Whatever `:core` decision layers remain unenumerated.

**A note on picking, because this run nearly skipped its own slice.** PQ-S6-2 was deferred on
2026-08-10 with a reason that read as settled: *"a third stacked spec edit, made from a sandbox that
cannot run `Verify-Alpha.ps1`, is a poor trade for a paragraph that changes no behaviour."* That
judgement was about **cost**, and it was made before anyone checked the engine against the rule. The
check took one `sed` and found `Program.cs:288` implementing half of §6.1 with the comment above it
stating the other half. **A deferral reason about cost is not a finding about substance** — re-run
the cheap check before inheriting one.

S2's `/pair` page is the last remainder still described with a mechanical-sounding word — ask the
fifth correction's question of it before believing the word, but note the answer is likely "C#",
which this sandbox cannot compile.
