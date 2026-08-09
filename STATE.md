# STATE — android tree

Single-glance state for the unattended window (2026-08-07 → 2026-08-18). Full derivation lives in
[`docs/S-Ladder.md`](docs/S-Ladder.md); evidence in [`LOG.md`](LOG.md); re-verification commands in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md); blockers in [`BLOCKED.md`](BLOCKED.md).

| | |
| --- | --- |
| **Heartbeat** | 2026-08-09 (S4 pull decision — cloud iteration, Linux sandbox; **fourth** run of the day) |
| **Android branch** | `claude/android-a0-probe` — pushed at `3e1e51a`, draft [PR #6](https://github.com/ShivaClaw/careerseeker-android/pull/6) with self-audit. **10 behind `main`** (docs-only commits, no overlap with this branch's files); left as found — merging `main` in would reshape PR #6's diff and is not this slice |
| **Merge topology** | **measured, not predicted** — [`docs/Merge-Topology.md`](docs/Merge-Topology.md). The whole stack merges into `main` **clean**; exactly **one** conflicting file repo-wide (`docs/Monetization-Decision.md`, add/add, a naming *decision*). `p4-pro` == `p2-replica` (`d9f95fd`) — no separate P4 branch exists. Re-verify: `AUDIT-REQUEST.md` C-MT-1…7 |
| **`:core` health** | **93 tests / 0 failures / 0 skipped — measured here**, up from a measured 76 baseline (+17, `PullPolicyTest`). Counts come from a reduced probe (`:core` alone, JDK 21); the **gate** is CI run [`31315093971`](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31315093971) on `3e1e51a` — see the CI row. CI proves green, not the number |
| **CI on this push** | run [`31315093971`](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31315093971), job *Build and test* — **result recorded in `LOG.md` S4.A-7**. The previous slice's green was run [`31305289509`](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31305289509) on `a37c185` |
| **Android health** | **green on CI at `53710a6`** — [run 31292342258](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31292342258), success: vendored-vector step, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug` all `BUILD SUCCESSFUL`, plus *"OK: no analytics or tracking SDKs on the release classpath."* **Not run by me** — no Android SDK/JBR/Gradle on this machine. The **102 / 0 / 0 / 3** test *counts* remain carried from the S8 local run: Gradle does not print counts, so CI proves green, not the number |
| **Main-repo base of record** | `origin/main` = `00b3705` (gate `P0-BASE` superseded — S-Ladder §2.3) |
| **Main-repo PRs merged** | #27 `7f3e61e` · #28 `f0b9bd5` · #29 `160b317` · #30 `a8ef552` · #31 `00b3705` |
| **Main-repo PR open** | **#32 draft** — `claude/s5-entitlement-ack-spec`, S5 spec + vectors. **Not merged** (merging needs a full local gate this machine cannot run) |
| **Offline pin** | **598, unchanged** — verified by CI run [`31292158471`](https://github.com/ShivaClaw/careerseeker/actions/runs/31292158471) on `windows-latest`: SyncHarness `130 passed`, `Offline total: 598 passed, 0 failed` |
| **Shared vectors** | **28** (was 26) — two added, **25 pre-existing files byte-identical**, `index.json` appended-only |
| **Coordination bus** | `autonomy/claude-state` — updated this iteration; files claimed named there |
| **Terra (Codex)** | R6(b) BLOCKED, PR #26 draft, files claimed: **none** — read at iteration start, no collision |

## Ladder

| Rung | Status | Evidence / reason |
| --- | --- | --- |
| **S0** re-entry + derivation | **DONE** | `docs/S-Ladder.md`; `LOG.md` §S0; `AUDIT-REQUEST.md` C-S0-1…9 |
| **S1** land the engine sync track | **DONE** | PRs #27–#30 merged; sync-track paths on main **0 → 54**; vector drift **0** in every check; C-S1-1…6 |
| **S2** engine publishes for real | **PARTIAL** | PR #31; engine ↔ **local** relay **30/30**, no deploy. **B-2 open:** no `/pair` page |
| **S3** pairing screen | **BLOCKED — B-4** | `sdkmanager`/`avdmanager` are not installed anywhere on this machine; Keystore cannot be honestly verified without an AVD |
| **S4** transport loop | **PARTIAL** (was BLOCKED — a mislabel, corrected 2026-08-09) | **The pull decision is DONE** — `PullPolicy`, 17 tests, run here (C-S4A-1/-2): pull-on-open, `AwaitingSnapshot` → ask, §6.2 large-gap → ask, latched, `since_seq` pinned to 0. **`pull_request` is not state-changing (§5.4), so this half never needed S3's device key** — verified on both sides (C-S4A-4). **Not** E2E: no production caller (C-S4A-7), `:app` wiring + Ktor engine + WSS still unwritten, and the E2E proof needs B-4 + B-7 |
| **S5** entitlement ack | **PARTIAL** | **spec + vectors DONE** (PR #32 draft): §4.3.3 body, PQ-A2-1 + PQ-A2-2 closed, 2 vectors. **Phone applier DONE 2026-08-09** — `EntitlementAckApplier`, 9 tests, run here (C-S5B-2/-3). **C# applier NOT written** — no .NET here; unblocked, merely unwritten. PQ-A2-3 → **B-6** |
| **S6** outcome marking (phone) | **BLOCKED — B-4** | needs S3 + S4 |
| **S7** Play-readiness pack | **PARTIAL** | upload keystore generated (§3b); Play floor **re-verified live**; `versionCode` scheme recorded → `docs/S7-Release-Signing.md`. Listing copy, data-safety dossier, privacy delta, account-day checklist and assets **already exist on `claude/p5-store`**; pricing rewrite on `claude/todos-pq1-pricing` — *not* duplicated here. No `.aab`, no Console action; screenshots need B-4 |
| **S8** hardening | **PARTIAL / BLOCKED — B-5** | migration test written; Room 2.8.4 cannot open a file-backed DB under Robolectric. Lint hold, full gate, bundle refresh **done** |

**S3, S4 and S6 are blocked on one checkbox** — Android Studio → SDK Tools → *Android SDK
Command-line Tools (latest)*. Mission §3a authorized *using* `sdkmanager`; it does not exist here,
and installing the toolchain that provides it was not authorized. B-5 is downstream of the same
thing (the migration test runs fine as an instrumented test).

**S5 was the one rung that was neither done nor blocked, and 2026-08-09 took its first half.** What
landed is the part that needed no compiler: the §4.3.3 body, two closed protocol questions, and two
generated vectors. What remains is the part that needs one — the engine and phone appliers — and it
is **still not blocked**, only unstarted. Do not let the `PARTIAL` label send the next session
hunting for an obstacle: the obstacle was the missing spec, and the spec now exists.

## Open blockers

| ID | Status |
| --- | --- |
| **B-1** pairing UI | gate answered; device half **still blocked** — see B-4 (the earlier "scheduled at S3" note was written before anyone checked `sdkmanager` existed) |
| **B-2** live E2E | **most of the way closed** — engine ↔ local relay proven 30/30; remaining gap is exactly the `/pair` page |
| ~~**B-3** vector drift~~ | **CLOSED** — 26/26 byte-identical to pin `679a317`, confirmed by CI's own step (run `31278769047`) |
| **B-4** emulator lane | `sdkmanager`/`avdmanager` absent; blocks S3/S6, S4's **E2E proof** (not its decision layer — see the S4 row) and B-1's device half |
| **B-5** migration test | Room 2.8.4 + Robolectric cannot open a file-backed DB; test kept under `@Ignore` with the diagnosis |
| **B-6** unknown-field vector | PQ-A2-3 cannot be closed by adding a vector: the engine has no inbound wire-JSON parser, so it would *accept* the envelope and turn the gate red. **Re-verified 2026-08-09** to two lines — `EnvelopeReceiver.cs:33` takes a parsed record, `SyncHarness/Program.cs:696` cherry-picks keys (C-S5B-5). Parser first, vector second |
| **B-7** cloud sandbox egress | **new 2026-08-09.** `dl.google.com` and `api.foojay.io` are **403 policy denials**, so AGP/`androidx`/JDK-17 are unfetchable in a cloud session — the android gate is unrunnable here for a reason *independent* of B-4. CI is the unblock, not a checkbox |

## Next intent (in order)

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

0. **S4's remaining half, and it is the first thing a machine with an SDK should do.** The pull
   *decision* landed 2026-08-09 (`PullPolicy`, 17 tests). What is left is `:app` work that needs
   the Android toolchain, in this order: map `ApplyResult` → `ApplyDisposition` (one `when`), call
   `PullPolicy.onOpen` when the transport opens and `onEnvelope` per envelope, push the resulting
   `OutboundEnvelopeFactory.pullRequest(0, ts)` through `RelayClient`, and call `onRequestFailed()`
   when that push fails. Then the `:app` Ktor engine dependency (**3.1.3**, never 3.2.0 — see the
   standing pins) and the WSS route. Only the final E2E claim needs an emulator; the wiring itself
   just needs a machine that can compile `:app`. Verify with C-S4A-7 — it is written to fail while
   the policy has no caller.
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
2. **Finish S2 — the `/pair` route.** All that stands between B-2 and closed. The vault and publisher
   wiring landed in PR #31 and the handshake is vector-proven. Needed: create a `PairingManager`,
   render the invite (`PairingInvite.ToQrJson()` is the exact payload — **a QR encoder is the only
   genuinely new dependency**), poll `RelayClient.TakeCompletionAsync`, show the confirm code for the
   human to compare, write `SyncPairing` to the vault.
3. **Tick the SDK Command-line Tools checkbox**, then the whole emulator lane is unattended and
   S3 → S4 → S6 unblock in order, along with B-5.
4. **B-6**, whenever the engine is being touched anyway: the inbound wire-JSON parser, then the
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
