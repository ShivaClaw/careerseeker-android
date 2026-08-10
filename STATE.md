# STATE — android tree

Single-glance state for the unattended window (2026-08-07 → 2026-08-18). Full derivation lives in
[`docs/S-Ladder.md`](docs/S-Ladder.md); evidence in [`LOG.md`](LOG.md); re-verification commands in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md); blockers in [`BLOCKED.md`](BLOCKED.md).

| | |
| --- | --- |
| **Heartbeat** | 2026-08-10 (S4 **transport half** — `SyncPump`, the loop's four ordering decisions moved into `:core` where they can be tested; cloud iteration, Linux sandbox, **eighth** run). `:core` only: **two new files + one comment-only fix, zero `:app` files, zero main-repo files.** `:core` 115 → **133 / 0 / 0**, measured here |
| **Android branch** | `claude/android-a0-probe` — draft [PR #6](https://github.com/ShivaClaw/careerseeker-android/pull/6) with self-audit. **CI GREEN on the S6 push**: run [31325873134](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31325873134), job *Build and test*, `success`, on head `9f73226` — **C-S6A-9 is closed green** and S6's marking decision is gate-verified, not probe-verified. **10 behind `main`** (docs-only commits, no overlap with this branch's files); left as found |
| **Merge topology** | **measured, not predicted** — [`docs/Merge-Topology.md`](docs/Merge-Topology.md). The whole stack merges into `main` **clean**; exactly **one** conflicting file repo-wide (`docs/Monetization-Decision.md`, add/add, a naming *decision*). `p4-pro` == `p2-replica` (`d9f95fd`) — no separate P4 branch exists. Re-verify: `AUDIT-REQUEST.md` C-MT-1…7 |
| **`:core` health** | **133 tests / 0 failures / 0 skipped across 12 classes — measured here 2026-08-10** (`BUILD SUCCESSFUL in 28s`), up from a **115 / 11** baseline re-measured on the same probe in the same session. The +18 is `SyncPumpTest` and nothing else: no existing test was edited, renamed or deleted. Counts come from a reduced probe (`:core` alone, JDK 21 — Gradle 9 removed `-c`, so the probe is a separate root; recipe in C-S6A-1). The **gate** is CI. CI proves green, not the number |
| **CI on this push** | **this iteration wrote Kotlin**, so the android gate on this push is the result that matters — and it is the one thing here that is *not yet* verified: at the time of writing CI had not reported. **C-S4T-8 is written to be checked, not assumed.** The android gate cannot run on this machine (no SDK/JBR, B-7 re-measured — `dl.google.com` and `api.foojay.io` both `CONNECT tunnel failed, response 403`, while `repo.maven.apache.org` answers `200`, which is exactly why `:core` builds here and `:app` does not). Last **known-green** android run stays [31325873134](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31325873134) on `9f73226`. The main-repo gate last ran on `9399d11`: run [`31346147785`](https://github.com/ShivaClaw/careerseeker/actions/runs/31346147785), **both jobs `success`** |
| **Android health** | **green on CI at `53710a6`** — [run 31292342258](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31292342258), success: vendored-vector step, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug` all `BUILD SUCCESSFUL`, plus *"OK: no analytics or tracking SDKs on the release classpath."* **Not run by me** — no Android SDK/JBR/Gradle on this machine. The **102 / 0 / 0 / 3** test *counts* remain carried from the S8 local run: Gradle does not print counts, so CI proves green, not the number |
| **Main-repo base of record** | `origin/main` = `00b3705` (gate `P0-BASE` superseded — S-Ladder §2.3) |
| **Main-repo PRs merged** | #27 `7f3e61e` · #28 `f0b9bd5` · #29 `160b317` · #30 `a8ef552` · #31 `00b3705` |
| **Main-repo PR open** | **#32 draft** — `claude/s5-entitlement-ack-spec`, S5 spec + vectors **+ the relay size-cap fix** (head `9c05ef7`, CI green). **Not merged** (merging needs a full local gate this machine cannot run) · **#33 draft** — `claude/s4-pull-request-semantics`, **stacked on #32**, S4's spec half (head `9399d11`). Docs-only, one file. Whether it should be retargeted at `main` once #32 lands is on the return-day list |
| **Offline pin** | **598, unchanged** — and unchangeable by this iteration too (2026-08-10, eighth run): no `.cs`, no harness, no vector byte, no count-reporting doc, and no main-repo file at all. Previously (seventh run): unchangeable for the same reasons. CI's `Verify-Alpha.ps1` run on `9c05ef7` exited 0, and the script *throws* on drift, so success is the confirmation. **I did not read the `Offline total:` line myself** — the earlier direct sighting is run [`31292158471`](https://github.com/ShivaClaw/careerseeker/actions/runs/31292158471): SyncHarness `130 passed`, `Offline total: 598 passed, 0 failed` |
| **Relay suite** | **36 passed / 0 failed** (was 32) — measured here with `npx vitest run`, and green again on CI's *Blind relay (Worker)* job. `npx tsc --noEmit` clean after `wrangler types` |
| **Shared vectors** | **28, unchanged again 2026-08-10 (eighth run)** — none added, none edited; `generate.mjs --check` re-measured here: `OK: 28 vector files match the generator.` (exit 0). A `pull_request` vector was **deliberately not added** (LOG §S4S-3): it would pin a body nobody disputes, test none of §4.3.4's three behavioural MUSTs, and — being `type: "envelope"` — would enter `SyncHarness`'s enumeration and move `$ExpectedOfflineTotal`, a number no .NET-less machine can measure |
| **Coordination bus** | `autonomy/claude-state` — updated this iteration; files claimed named there |
| **Terra (Codex)** | R6(b) BLOCKED, PR #26 draft, files claimed: **none** — read at iteration start, no collision |

## Ladder

| Rung | Status | Evidence / reason |
| --- | --- | --- |
| **S0** re-entry + derivation | **DONE** | `docs/S-Ladder.md`; `LOG.md` §S0; `AUDIT-REQUEST.md` C-S0-1…9 |
| **S1** land the engine sync track | **DONE** | PRs #27–#30 merged; sync-track paths on main **0 → 54**; vector drift **0** in every check; C-S1-1…6 |
| **S2** engine publishes for real | **PARTIAL** | PR #31; engine ↔ **local** relay **30/30**, no deploy. **B-2 open:** no `/pair` page. **Transport half hardened 2026-08-09** (PR #32, CI green): the relay was 413ing envelopes §3.1 declares legal — a base64url **character** count tested against a **byte** budget capped the decoded payload at 786,432 and left a **256 KiB** band untransmittable. Latent, not live (§4.4 chunking is unimplemented in both codebases), but §4.4 tells a future chunker to size against exactly the number that did not fit. Cap now derived; suite 32 → **36**. Re-verify: C-S2R-1…7 |
| **S3** pairing screen | **BLOCKED — B-4** | `sdkmanager`/`avdmanager` are not installed anywhere on this machine; Keystore cannot be honestly verified without an AVD |
| **S4** transport loop | **PARTIAL** (was BLOCKED — a mislabel, corrected 2026-08-09) | **The pull decision is DONE** — `PullPolicy`, 17 tests, run here (C-S4A-1/-2). **Spec half DONE 2026-08-10 — PQ-S4-1 closed** (PR #33 draft): §4.3.4 pins the body, `since_seq` reserved. **Transport half DONE 2026-08-10** — `SyncPump`, 18 tests, run here (C-S4T-1…7): the cursor advances on envelopes *seen* not *applied*; the replica position is read per envelope, before the apply; a refused push releases the latch; **the seq that drives the cursor is the envelope's authenticated one, never the relay's page wrapper** (C-S4T-4 — a blind relay could otherwise truncate the stream without decrypting anything). Built with **no `DeviceSigner` at all**, which is the assertion that this half never needed S3's key. **Still not E2E, and `SyncPump` has no production caller** — `grep -rn SyncPump app/src` prints nothing. What is left really is wiring now (Ktor engine, `ApplyResult`→`ReplicaApplier` adapter, Room position source); only the E2E claim needs B-4 |
| **S5** entitlement ack | **PARTIAL** | **spec + vectors DONE** (PR #32 draft): §4.3.3 body, PQ-A2-1 (**re-opened and re-closed 2026-08-09** — its first close checked one direction of a two-directional claim; see the S2 row) + PQ-A2-2 closed, 2 vectors. **Phone applier DONE 2026-08-09** — `EntitlementAckApplier`, 9 tests, run here (C-S5B-2/-3). **C# applier NOT written** — no .NET here; unblocked, merely unwritten. PQ-A2-3 → **B-6** |
| **S6** outcome marking (phone) | **PARTIAL** (was BLOCKED — a mislabel, corrected 2026-08-09) | **The marking decision is DONE** — `OutcomeMarkPolicy`, 22 tests, run here (C-S6A-2/-6): Pro-gated (and `AwaitingEngine` is not Pro enough), `no_reply` renderable but never offerable, a pending mark shadows the engine's value, retired by **value convergence** and bounded by disagreeing reports. **The blocked half is the send path**: `outcome` is state-changing, so §5.4 needs S3's Keystore key (B-4), and the `:app` wiring needs a toolchain this sandbox cannot fetch (B-7). No production caller (C-S6A-8). Finding → **PQ-S6-1**: `outcome` is the one state-changing kind nothing ever acks |
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

**S4, S5 and S6 now all sit at PARTIAL, and the reason is the same three times over: each has a
decision half that runs anywhere and an execution half that needs a machine this program's cloud
sessions are not.** S5 has its spec, vectors and *phone* applier; the **C# applier** is unwritten.
S4 has its pull *decision*; the `:app` wiring is unwritten. S6 has its *marking decision*; the
signed send is unwritten.

Read the difference carefully, because it is not the same in all three. **S4's and S5's remaining
halves are unblocked and merely unwritten** — give them a machine with an Android SDK and .NET
respectively and they can be done today. **S6's remaining half is genuinely blocked**: it needs a
device-signed envelope (§5.4), which needs S3's Android Keystore key, which needs an AVD that does
not exist (B-4). A machine with an SDK is necessary but not sufficient there.

## Open blockers

| ID | Status |
| --- | --- |
| **B-1** pairing UI | gate answered; device half **still blocked** — see B-4 (the earlier "scheduled at S3" note was written before anyone checked `sdkmanager` existed) |
| **B-2** live E2E | **most of the way closed** — engine ↔ local relay proven 30/30; remaining gap is exactly the `/pair` page |
| ~~**B-3** vector drift~~ | **CLOSED** — 26/26 byte-identical to pin `679a317`, confirmed by CI's own step (run `31278769047`) |
| **B-4** emulator lane | `sdkmanager`/`avdmanager` absent; blocks S3, **S6's send path** (not its marking decision — see the S6 row), S4's **E2E proof** (not its decision layer) and B-1's device half |
| **B-5** migration test | Room 2.8.4 + Robolectric cannot open a file-backed DB; test kept under `@Ignore` with the diagnosis |
| **B-6** unknown-field vector | PQ-A2-3 cannot be closed by adding a vector: the engine has no inbound wire-JSON parser, so it would *accept* the envelope and turn the gate red. **Re-verified 2026-08-09** to two lines — `EnvelopeReceiver.cs:33` takes a parsed record, `SyncHarness/Program.cs:696` cherry-picks keys (C-S5B-5). Parser first, vector second |
| **B-7** cloud sandbox egress | **new 2026-08-09, re-measured 2026-08-10** (`dl.google.com`, `api.foojay.io` → `CONNECT tunnel failed, response 403`; `repo.maven.apache.org` → `200`). AGP/`androidx`/JDK-17 are unfetchable in a cloud session — the android gate is unrunnable here for a reason *independent* of B-4. CI is the unblock, not a checkbox. **Its cost shrank 2026-08-10**: S4's ordering decisions no longer sit behind it |

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

**A finding this slice turned up, in `:core`, and it belongs on the return-day list.**
`RelayClient.parsePullPage` accepts **two** page shapes, and in the `{"seq":N,"envelope":…}` shape
the relay's reported sequence number and the envelope's own can **disagree**. The envelope's `seq`
is in the AAD and the AEAD tag covers it; the relay's is authenticated by nothing. `SyncPump` now
uses only the authenticated one, so a relay reporting `seq: 999` on an envelope carrying `5` cannot
make the phone skip `6..999` — **a blind relay could otherwise truncate history without decrypting
a byte it is unable to read.** The deployed relay splices envelopes back verbatim and does not do
this; the change is that the phone no longer *depends* on that. **Left open deliberately:** whether
`parsePullPage` should accept the wrapper shape at all. Removing it is a `:core` change with a
`RelayClientTest` assertion behind it and possible engine-side expectations — a slice, not a
drive-by.

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
