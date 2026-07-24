# CareerSeeker Android Program — Checkpoint & Meta-Analysis

**Written:** 2026-07-24, by Fable 5 at the **P2-offline boundary** — the moment the entire
offline half of P2 is built, green, and pushed on both repos, and everything remaining needs
either a handset or a Brandon decision.
**Method:** full re-read of the design spec
(`Android-Dashboard-Pro-Spec-2026-07-22.md`) and every decision document
(`Sync-Protocol.md`, `Entitlement-Architecture.md`, `Monetization-Decision.md`,
`Sync-Consent-Copy.md`, `Post-Quantum-Posture.md`, both runbooks, both evidence files),
checked line-by-line against the as-built code on `claude/p2-publisher` (engine) and
`claude/p2-replica` (Android). Findings below are verified against source, not recalled.
**Audience:** Brandon, Codex (audit seed), and every future session. This supersedes nothing;
it *indexes* everything.

> **Update 2026-07-24 (same session): findings F1–F4 are CLOSED.** F1 (wire score unit → 0–100
> int, `ScoreToWire = round(total×20)`), F2 (snapshot/delta bodies pinned in protocol §4.3.1),
> F6 (delta = recent-window latest-wins, stated), and F4 (seq-resume proof + §6.1 rule that the
> engine resumes above `max(persisted, relay latest)` + the vault requirement in the
> `BuildSyncBridge` seam) landed engine-side in `dbad69e` on `claude/p2-publisher`. F3
> (doc_kind reconciled to `draft_email|cover_letter|resume_text` across all three planes) is
> protocol-side in `dbad69e` and Android-side in `bd3f71d` on `claude/p2-replica`. Offline pin
> **433 → 435** (SyncHarness 94, EngineHarness 103); both CI green. Deliberately still deferred:
> the **doc-pipeline** half of F3 (persist tailored text + rev on the engine — P3's opening
> commit) and F5/F7 (heartbeat timer, `event_count` on the phone — fold into the device
> session). The findings text below is preserved as originally written.

> **Codex audit result (2026-07-24, brief: [Codex-Android-Audit-2026-07-24.md](Codex-Android-Audit-2026-07-24.md)).**
> Codex independently reproduced both verifications and confirmed the §3 invariant clean bills
> (no posting body on the wire, `InjectionSignals` stays engine-side, evidence is metadata-only,
> no send path, sync default-off, score 0–100, vectors untouched, synced strings inert). It found
> **one high defect the checkpoint had missed**: the bridge marked its first snapshot sent
> *before* the push succeeded, so a failed/thrown first push demoted every later publish to a
> delta — which a fresh phone merged into demo fixture rows, presenting demo data and the
> fixture's `auditOk=true` as real. **Fixed both sides:** engine `7158202` (flag flips only after
> a successful snapshot push; retried until it lands; EngineHarness 105, pin **437**) and android
> `d9f95fd` (defense-in-depth: the first applied real payload of any kind wipes all
> fixture-populated tables and the fixture's audit claim, with parse-before-write so a malformed
> payload still changes nothing; tests 25). The android fix also closes the wider class Codex's
> trigger implied — a real snapshot previously left demo evidence/documents standing, and a
> heartbeat cleared the demo label while demo rows stayed visible. Codex's low finding
> (P2-Evidence.md stale transcript) is addressed with a historical-note banner. Codex's verdict
> was "go to open draft PRs, no-go to merge until the demo-boundary bug is fixed" — the fix is
> now in, so both branches stand ready for draft PRs.

---

## 1. Design goals, restated as testable invariants

These are the load-bearing commitments extracted from the spec §2/§7 and `CLAUDE.md`. Each is
stated so a violation is detectable, with its current enforcement mechanism.

| # | Invariant | Enforced by (today) |
| --- | --- | --- |
| I1 | **No send path, anywhere.** No protocol kind causes email transmission; `Dispatcher.SubmitAsync` throws. | `DispatcherNoSendHarness` (35); SyncHarness "no v1 payload kind implies a send path"; protocol §8.1 |
| I2 | **The relay is blind or it doesn't ship.** Ciphertext, pairing ids, sizes, timing — nothing else. | Relay storage-schema test (exact column list); CI grep proving `relay/src` holds no decryption primitive; live pull inspection (P1-Evidence §3) |
| I3 | **Phone never holds Gmail credentials; no kind transports tokens/keys.** | Protocol §8.2; no such field in any payload builder |
| I4 | **Untrusted text stays data.** No raw posting body ships to the phone in P2; short structured fields only, rendered inert. | SyncHarness + EngineHarness "NO raw posting body" checks; applier parses only pinned fields; build is *stricter* than protocol §8.6 (descriptions don't ride at all) |
| I5 | **Local-first stays true.** SQLite, artifacts, OAuth, provider keys stay on the PC; phone holds an erasable replica. | Architecture (no code path moves them); replica erasure lands with unpair (device session) |
| I6 | **Sync is opt-in, default OFF.** | `--sync` flag default off; with it off the tick is byte-identical `cycle.TickAsync` |
| I7 | **No analytics/trackers in the app.** | Resolved-classpath CI check (structural, not grep) |
| I8 | **Every remote action lands in the hash-chained audit log** with device-key fingerprint. | Receiver requires ECDSA sig on state-changing kinds; engine-side audit append lands with the P3/P4 inbound applier |
| I9 | **Crypto-agility over algorithm choice.** Suite-versioned pairing, HKDF-over-concat ikm, variable-width key fields, suite recorded at pairing. | Protocol §5.2; PQ-Posture §4; `concat(ss)` implemented in both `PairingCrypto` implementations |
| I10 | **Every public promise stays literally true on launch day.** | Monetization-Decision §1 tracks the three broken pricing sentences; rewrite is a P6 **blocker** |
| I11 | **Exactly one server component** (the blind relay). | P0-WORKER option C chosen *because* option A violated this; no entitlement Worker exists |
| I12 | **Evidence standard:** "ran it and saw it" or it didn't happen. | Verify-Alpha pinned total (433); android CI; P1/P2-Evidence transcripts |

The strongest structural property of this program: **most invariants are enforced by
machinery, not memory** — pinned counts, structural CI checks, shared vectors, storage-schema
assertions. The findings in §4 are almost all places where a *shape* is currently pinned only
by tests-in-code rather than by the normative doc, which is the one enforcement gap this
checkpoint closes where it can and flags where it can't.

---

## 2. State of the build (what exists, with proof)

### 2.1 The three planes

**Engine (public repo, branch `claude/p2-publisher`, 5 commits over P1 base):**
`src/Sync` — codec (AES-256-GCM, fixed-string AAD), `PairingCrypto` (P-256 ECDH → HKDF over
`concat(ss)`, provisional/final relay tokens, 6-digit confirm), `DeviceSignature` (ECDSA
P-256, r||s), `EnvelopeReceiver` (normative check order, revocation-before-decrypt),
`PairingManager` (single-use secret burn), `RelayClient` (push/pull/pair/rotate/unpair),
`SyncPayloads` (snapshot/delta/heartbeat/evidence builders), `SyncPublisher` (seals with
k_e2p, monotonic e2p seq, gap-on-failed-push, injectable sink/clock/nonce).
`src/Engine` — `EngineSyncBridge` (projects counters + dashboard rows → snapshot-first /
delta-after / heartbeat / evidence; holds no key material), host wiring post-tick behind
`--sync` (default OFF, no-ops until a pairing vault exists — the deliberate device-session
seam at `Program.cs::BuildSyncBridge`).
**Proof:** Verify-Alpha offline **433/0** (SyncHarness 93, EngineHarness 102, +8 more
harnesses); `SyncLiveSmoke` **22/22 live** against `relay.careerseeker.app` (pairing,
snapshot+delta through the shipping publisher, simulated-phone counter reconstruction, signed
doc_edit accept + wrong-key reject, relay replay 409, unpair purge).

**Relay (public repo, deployed):** Cloudflare Worker + one Durable Object per pairing,
`workers_dev` false, custom domain live. Bootstrap/rotate, one-shot pair completion,
push/pull with relay-side seq monotonicity, TTL purge via alarms, WS hibernation, token
stored as SHA-256 compared constant-time. 32 vitest green. Unchanged this session.

**Android (private repo, branch `claude/p2-replica`, 4 commits over P1 base):**
`:core` — JCA-only crypto (no Tink; 20-line HKDF), receiver mirroring the C# one, kept
Android-free by `checkCoreIsAndroidFree` and proven against the same vendored vectors
(drift-checked in CI via the GitHub contents API, pinned by `VECTORS.lock`).
`:app` — Room replica (applications/jobs/counters/evidence_events/documents/sync_state,
schema exported), `EnvelopeApplier` (snapshot wholesale-replace / delta upsert / heartbeat /
evidence, **persisted** seq high-water mark so post-restart replays no-op, malformed-payload
no-op, audit-verdict lifecycle: evidence sets → delta/heartbeat preserve → snapshot resets to
unknown), `DemoFixture` (seq 0, `demoMode=true`, honest labeling), five read-only Compose
screens (Home/Applications/Detail/Jobs/Evidence) as pure projections of Room.
**Proof:** **21/21** Robolectric (12 applier incl. the sealed-envelope seam test
`:core` receiver → applier, 3 fixture, 6 screens); lint green with `warningsAsErrors`; CI
green end-to-end.

### 2.2 Branch / PR topology (unchanged policy: draft PRs, never self-merge)

| Repo | Branch | Contents | PR |
| --- | --- | --- | --- |
| public | `#5` P0 → `claude/alpha-finish`, `#6` P1 (stacked) | protocol, vectors, relay, src/Sync P1 | open drafts |
| public | `claude/p2-publisher` (off p1-sync) | P2 engine: payloads, publisher, bridge, evidence kind | **none yet** |
| private | `#1` P0, `#2` P1 runbook, `#3` P1 :core (stacked) | scaffold, runbooks, :core | open drafts |
| private | `claude/p2-replica` (off p1-pairing) | replica, applier, fixture, screens, evidence branch | **none yet** |
| private | `claude/p2-runbook`, `claude/todos-pq1-pricing` | P2 plan; PQ-1 + pricing TODOs for Sonnet | — |

### 2.3 What the phases claimed vs. what is real

P2's spec exit ("demo cycle ticks on the phone; airplane-mode replica reads") is **not yet
claimable** — it needs the device-bound finale. What *is* claimable, with evidence: every
offline item in P2-Runbook §2.2–§2.5 plus the `evidence` wire kind, proven at both ends and
across the live relay. The honest program status: **P2 offline complete; P2 device-bound not
started; P3+ untouched.**

---

## 3. Spec ↔ build conformance matrix

Spec section by section. **Conforms** = built as specified (or spec was formally amended).
**Amended** = deliberate, documented divergence (protocol §9 / decision docs). **Deferred** =
in-scope, not yet built, tracked. **Drifted** = unintended divergence → §4 findings.

| Spec § | Item | Status |
| --- | --- | --- |
| §1.3/§6.4 | Pricing promises, P-MONEY | **Decided** ($4.99/$2.99/$1.99mo); pricing rewrite = P6 blocker (I10) |
| §2.1–2.5 | Five constraints | **Conforms** (I1–I7; I4 stricter than spec) |
| §3.1 | X25519 · Tink · Ed25519 | **Amended** → P-256 · JCA/no-Tink · ECDSA P-256 (protocol §9, P1-CURVE; PQ-posture §3 confirms the lens) |
| §3.2 | Relay design | **Conforms**; FCM correctly deferred to v1.1 |
| §3.3 | Snapshot/delta/doc/evidence publishing | snapshot/delta/heartbeat/evidence **conform**; `doc` **deferred** (F3); "on artifact change" trigger deferred with it; delta semantics **drifted** (F6) |
| §3.4 | Doc editing, rev/conflict, verified-badge | **Deferred** (P3, by design); prerequisites identified (F3) |
| §3.5 | Engine sync work + harness + drift trap | **Conforms** (SyncHarness in the pin since P1; counts moved with docs every time) |
| §4.1 | Screens 2–6 read-only | **Conforms** (from fixture and, for evidence, from wire); screens 1/7/8 deferred to device session / P4 |
| §4.2 | Stack (Kotlin/Compose, minSdk 26, pure-Kotlin core, Room, kotlinx) | **Conforms**; Tink→JCA amended; WSS client **deferred** (P2 finale) |
| §4.3 | Two repos, shared vectors, CI both worlds | **Conforms** — the three-way vector agreement is the program's best artifact |
| §5 | Play compliance dossier | **Deferred to P4/P5 by design**; re-verify against current policy at execution (spec's own instruction) |
| §6.1–6.3 | Pro scope fence, phased Pro, entitlement | **Decided better than spec**: P0-WORKER option C (engine verifies Google's signature; no second server, I11 preserved); §4.3 `entitlement` body amendment scheduled with P4 (tracked, F10) |
| §7.1 | Threat model | **Conforms**; entitlement-Worker row obsolete under option C (harmless leftover in spec) |
| §7.2 | App hardening checklist | **Deferred** to device session/P5 (pinning, biometric, clipboard hint, R8 config review, codec fuzzing) |
| §7.3 | Privacy/site one-artifact rule | Consent copy drafted + gated (Cloudflare named); ships with the app (P5/P6) |
| §8/§9 | Phase plan, gates | On plan; three P2 gates open; P0-ACCOUNT effectively decided by action (D-U-N-S in flight → org account) |
| §10 | Non-goals | **Holding** — nothing leaked in (no FCM, no multi-device, no L2 kinds shipped, `kill` still reserved-and-rejected) |
| §11 | Success criteria | 1: partially provable (pairing live minus handset); 2–3: P5/P6; 4: green so far; 5: this document is part of it |

---

## 4. Findings ledger (the meta-analysis core)

Ordered by severity × cost-if-ignored. **None are launch-day surprises if fixed in their
listed phase** — the point of finding them now.

### F1 — Score scale mismatch across all three planes · **HIGH, cheap now, expensive later**
The engine scores on a **0–5 scale** (`Scorer.Clamp05`; dashboard renders "8.2 total"-style
single-decimal). `EngineSyncBridge.MapApplication` projects `(int)Math.Round(Total)` →
**integers 0–5** on the wire. The Android demo fixture and screens speak **0–100** ("82").
First real pairing will render "4" where the UI's visual language promises "82" — real data
will look broken next to demo data.
**Resolution:** decide the wire unit, pin it in protocol §4.3 (with F2), and align: recommend
**0–100 int** (`round(Total × 20)`) since the phone UI and fixture already speak it; fix
`MapApplication`; add a harness check asserting the range. One commit each side, before the
device session (it changes what the exit-proof screens display).

### F2 — `snapshot`/`delta` bodies are not specified in the normative doc · **HIGH**
Protocol §4.3 gives field-level bodies for `doc`, `evidence`, `heartbeat`, `conflict`,
`doc_edit`, `outcome` — but `snapshot` and `delta` are prose only ("Full dashboard state").
The actual shape (`counters{discovered…cycles}`, `applications[{id,state,company,title,
score}]`, `jobs[{id,company,title,repost,injection_flag}]`, `since_seq`) is pinned only by
SyncHarness and `EnvelopeApplierTest` mirroring each other. Tests pin *implementations*;
the doc is what a third implementation (iOS, §10) would read.
**Resolution:** add the body schemas to §4.3 in the same commit as F1's unit decision.
Doc-only on the engine repo; cite in the next protocol amendment row (§9).

### F3 — `doc` is blocked on engine-side facts, and its vocabulary is already split three ways · **HIGH (P3 gate item)**
(a) The engine renders resume/cover to PDF and persists **paths only** (`ApplicationRow.
ResumePath/CoverPath`; tailored text is discarded after render; only `AnswersJson` persists
as text). The protocol's `doc` (`{app_id, doc_kind, rev, text, verified}`) has nothing to
ship until a store migration persists tailored text + a per-doc **rev** counter + the
verified flag. (b) Vocabulary: protocol says `doc_kind ∈ draft_email|cover_letter|
resume_text`; the Android `DocumentRow`/fixture use `resume|cover_letter|answers`; the
engine's persisted artifacts are resume-PDF/cover-PDF/answers-JSON. Three vocabularies, no
two identical. (c) Spec §3.4's editable units include the **draft email body**, which today
lives in Gmail (draft ref), not in the store at all.
**Resolution:** this is the *first work item of P3*, not a P2 patch: one reconciliation
commit (protocol + store migration design + Android rename) before any editor UI. The
deliberate absence of a `doc` applier branch on the phone ("no parser for unshipped shapes")
was the right call and held.

### F4 — Publisher seq persistence across engine restart · **HIGH (device-session design input)**
Protocol §6.1: seq is "**persisted by the sender across restarts**." `SyncPublisher` accepts
`startSeq` but nothing persists it (no pairing vault yet). The failure mode is sharp: the
phone's high-water mark is **persisted** (by design, F-none — it's correct), so an engine
that restarts at seq 1 gets *everything* rejected as replay — including the recovery
snapshot. Silent, total, one-sided sync death.
**Resolution:** the pairing vault (device session) must persist `{pairing, suite, key_id,
k_e2p, k_p2e, device_sig_pub, relay_token, last_e2p_seq}`; belt-and-suspenders: on start,
also read the relay's `latest` for e2p and start above `max(vault, relay)`. Write this into
the vault design *before* it's built; add a SyncHarness case (publisher resumed from
persisted seq → accepted).

### F5 — Heartbeat exists but nothing drives it · **MEDIUM**
Runbook §2.2: heartbeat "on a timer." Built and tested (`PublishHeartbeatAsync`), but the
host only publishes per-cycle (delta + evidence). At demo intervals (30 s) that's fine; at a
production cycle interval (hours), "last seen" goes stale exactly when the user most wants
liveness.
**Resolution:** a second `PeriodicScheduler` (e.g., 60 s) driving heartbeat when sync is on —
one constructor line in the host, one EngineHarness case. Fold into the device session (it
only matters with a real phone watching).

### F6 — `delta` is a recent-window upsert, not "changed since seq" · **LOW-MEDIUM, document rather than fix**
Protocol: "Applications/jobs/counters **changed since a given seq**." Build: the bridge
sends the full recent window (25+25) every cycle with `since_seq = last published seq`.
Correct under latest-wins (applier upserts), but imprecise: bandwidth scales with window
size not change rate, and `since_seq` is decorative.
**Resolution:** cheapest honest fix is amending the protocol's description to match
("recent-window state; receiver applies latest-wins"), deferring true change-tracking until
scale demands it. Fold the wording into F2's doc commit.

### F7 — `evidence.event_count` is carried but dropped by the phone · **LOW**
The wire carries the engine's total event count; the applier persists the trail + verdict
but not the count, so the Evidence screen can't say "showing 12 of 7,214."
**Resolution:** one column on `sync_state` (or ignore deliberately and note in the doc).
Bundle with any next replica migration; not worth one alone.

### F8 — No inbound p2e path in the host · **expected, but state it**
`EnvelopeReceiver` is built/vector-proven and the live smoke exercises a signed `doc_edit`
engine-side — but no host loop pulls p2e, so `doc_edit`/`outcome`/`entitlement`/
`pull_request` have no runtime handler. This is the P3/P4 boundary, correctly not crossed.
`pull_request` handling (re-publish from seq) should land with the *device session* though,
not P3 — it's the phone's gap-recovery mechanism (§6.2: "large gap → request fresh
snapshot") and the WSS/pull-on-open client will want it.

### F9 — `entitlement` body in §4.3 still reads `{voucher}` · **tracked, correctly deferred**
Superseded by P0-WORKER option C (`{original_json, signature}`); `Entitlement-Architecture.md`
already schedules the amendment + vectors (valid/tampered/wrong-product/wrong-package/
non-PURCHASED) for the same commit that implements P4. Keep it there; noting here so it
can't be lost.

### F10 — Residue in the spec itself · **LOW, informational**
The spec (frozen 2026-07-22) still describes X25519/Tink/Ed25519, the entitlement Worker as
recommended, and the threat-model row for that Worker. All formally amended in protocol §9 /
decision docs. No action — the spec is the historical intent; the protocol doc is normative.
Any future reader starts from the protocol doc; the paper trail explains why.

### F11 — Minor consistency items · **LOW**
`--sync` exists only in demo mode (intentional until the vault; add to `dashboard` mode with
the device session). `key_id` format (`k-<date>`) is specified but unenforced — the vault
mints it; add a format assertion then. Robolectric pinned at SDK 35 vs compileSdk 37 —
re-check when Robolectric ships 37 support or the module moves to a Java 21 test JVM.
Chunking (§4.4) untested — becomes real with `doc` (P3); add chunk vectors then.

---

## 5. Risk register (beyond the findings)

| Risk | Exposure | Mitigation / trigger |
| --- | --- | --- |
| **Three open P2 gates** stall the device session | Device work can't finish without KEYSTORE-FALLBACK, PIN-ROTATION, REPLICA-CRYPTO answers | Recommendations already written (P2-Runbook §4); decide before the handset session |
| **Pricing-page rewrite** slips to "after launch" | Breaks I10 — success criterion 3 fails on day one | Already a P6 **blocker** with draft copy in Monetization-Decision §2; Sonnet TODO exists |
| **Cert-pin rotation** ships pin-only | A cert rotation bricks every installed app | Gate rec: pin leaf+backup + documented rotation runbook; decide at §2.6 |
| **SHA-1 IAB signature** (Play's `SHA1withRSA`) | Assessed not-practically-exploitable (Entitlement-Arch §weakness 1) | Revisit if Pro revenue justifies Developer-API verification; seam exists |
| **HNDL on P-256 pairing** | Recorded traffic decryptable at Q-day | 7-day relay TTL bounds the window; PQ-1 triggers pinned (`MLKem.IsSupported` on user Windows / BC on Android / NIST 2030 outer bound) |
| **Play policy drift** (billing lib version, target API, data-safety form shape) | Dossier written 2026-07-22 goes stale | Spec's own rule: re-verify every §5 item at P4/P5 execution time |
| **12-tester/14-day closed test** (if account ends up personal) | Calendar time on the critical path | D-U-N-S in flight → org account path; if it falls through, start the clock at P4 open |
| **Engine restart seq death** (F4) | Total silent sync failure post-restart | Vault design requirement written; harness case scheduled |
| **Single-maintainer bus factor** | All crypto decisions concentrated | The paper trail (spec → runbooks → decision docs → evidence → this checkpoint) is the mitigation; keep it current |

---

## 6. Strategy forward

### 6.1 Immediate (no handset, no new gates — one short session)
1. **F1+F2+F6 in one commit pair:** decide score unit (rec: 0–100), pin snapshot/delta
   bodies + delta wording in protocol §4.3, fix `MapApplication`, align harness + applier
   tests. Engine + android, drift-trap discipline.
2. **Open the four draft PRs** (publisher → alpha-finish base; replica → p1-pairing base)
   so Codex audits the complete offline P2 in one pass. Attach P2-Evidence and this
   checkpoint as the audit seed.

### 6.2 Device session (handset + Brandon; the P2 finale)
Prerequisites: three gate answers; F4's vault design (write it into the session runbook
*first*); F1 landed (exit-proof screens show real scores).
Order: pairing vault (with seq persistence) → desktop `/pair` page (QRCoder, same
token/Host/Origin discipline; `BuildSyncBridge` stops no-op'ing here) → phone pairing UI
(CameraX/ML Kit, Keystore ECDSA per KEYSTORE-FALLBACK) → WSS + pull-on-open + `pull_request`
handling (F8's device-session slice) + heartbeat timer (F5) → cert pinning per PIN-ROTATION →
**exit proof** (live tick + airplane mode), captured in P2-Evidence style.

### 6.3 P3 (doc view/edit — Fable, high effort, the sacred-surface phase)
Opens with the **F3 reconciliation commit** (doc_kind vocabulary + store migration for
tailored text/rev/verified + draft-email-body sourcing decision) before any UI. Then: `doc`
publisher + chunking + vectors → phone editor with rev chips → engine inbound applier
(verify sig → base_rev check → apply → regen artifact → update Gmail draft via compose-only
path → audit append → re-publish `doc`) → conflict path → verified-badge downgrade. Every
commit re-runs `DispatcherNoSendHarness`. Codex audit is non-negotiable here.

### 6.4 P4–P6 (unchanged from spec, with decided gates folded in)
P4: Play Console (org account), Billing + `GoogleSignedPayloadVerifier` (option C),
`entitlement` body amendment + vectors (F9), outcome tracking + funnel boards.
P5: data-safety form from the consent-copy mapping (processor-hosting-ciphertext framing in
review notes), listing assets, closed test, accessibility pass.
P6: staged rollout + **pricing rewrite ships with launch** (I10).
Parallel Sonnet lane (already packaged): `docs/todo/PQ1-Hybrid-Migration.md`,
`docs/todo/Pricing-Page-Rewrite.md`.

### 6.5 Standing model allocation (re-affirmed)
Opus 4.8 normal: P2 finale, P4 wiring. Fable 5 high: P3, protocol amendments, audits like
this one. Sonnet: the two packaged TODOs. Every phase opens with its runbook as a draft PR
before labor — the pattern has now caught enough (Tink, X25519, Ed25519-below-33, the
entitlement Worker) to be beyond argument.

---

## 7. Verification map (what "green" means today)

| Surface | Gate | Current |
| --- | --- | --- |
| Engine offline | `Verify-Alpha.ps1`, pinned `$ExpectedOfflineTotal` | **433 / 0** (Slice 28, Engine 102, Researcher 55, Hook 14, StoreParity 22, GatewayGate 34, DispatcherNoSend 35, Lifecycle 44, Renderer 6, Sync 93) |
| Engine live | `SyncLiveSmoke` vs `relay.careerseeker.app` (explicitly out of the hermetic pin) | **22 / 22** (2026-07-23) |
| Relay | vitest (incl. ciphertext-only storage schema) | **32 / 32** |
| Vectors | `generate.mjs --check` (Node ↔ C# ↔ Kotlin, byte-for-byte) | clean; android vendored copy drift-checked in CI (`VECTORS.lock`) |
| Android | `checkCoreIsAndroidFree` + `:core:test` + `:app:test` + `assembleDebug` + `lintDebug` (warningsAsErrors) + no-tracker classpath check | **CI green** (run 30066114299); 21/21 Robolectric |
| Drift traps | pinned total + doc-count asserts move as one commit; vectors never hand-edited | held through three pin bumps this session (401→415→425→433) |

**Ritual reminders:** engine verify via `powershell.exe` (pwsh not installed); Android env
`JAVA_HOME=<AS jbr>`, `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`; interrupted `--no-daemon`
Gradle runs → delete `*/build/kotlin`; port 7777 must be free for EngineHarness (a running
alpha dashboard collides — cost one flaked verify this session).

---

## 8. Decisions: closed and open

**Closed:** P0-CIPHER (AES-256-GCM) · P0-BASE (engine PRs → alpha-finish) · P1-CURVE (P-256
both sides, suite-versioned) · P1-DOMAIN (relay.careerseeker.app) · P0-WORKER (**option C:
engine verifies Google's signed payload; no second server**) · P-MONEY (4.99 / 2.99 one-time
/ Cloud 1.99mo parked; Windows app is "CareerSeeker", never "Basic") · Sync consent copy
(Cloudflare named) · PQ posture (hybrid at trigger, not before; SHA-256/AES stay) ·
P0-ACCOUNT (org account, by action — D-U-N-S in flight).

**Open (all Brandon's, all with written recommendations):**

| Gate | Question | Recommendation on file | Needed by |
| --- | --- | --- | --- |
| P2-KEYSTORE-FALLBACK | Pair without StrongBox/hardware keys? | Yes, with explicit logged downgrade surfaced in Settings | device session |
| P2-PIN-ROTATION | Cert-pin rotation story | Pin leaf + backup; rotation runbook; no pin without a plan | §2.6 |
| P2-REPLICA-CRYPTO | Room-at-rest | Platform encryption; avoid SQLCipher's native `.so` | device session |
| F1 (new, this doc) | Wire score unit | 0–100 int | before device session |
| PRO-1.1 (future) | gmail.readonly + CASA | Own spec, own gate; not this program | — |

---

## 9. Paper-trail index

| Artifact | Where |
| --- | --- |
| Design spec (historical intent) | `Desktop\Career Seeker\Android-Dashboard-Pro-Spec-2026-07-22.md` |
| **Normative protocol** | main repo `docs/Sync-Protocol.md` (+ §9 amendment log) |
| Decision docs | android repo: `Entitlement-Architecture.md`, `Monetization-Decision.md` (p1-runbook branch), `Sync-Consent-Copy.md` (p0-scaffold), `Post-Quantum-Posture.md` (p1-runbook) |
| Phase plans | `docs/P0-Runbook.md` (main), `docs/P1-Runbook.md`, `docs/P2-Runbook.md` (their branches) |
| Evidence | main repo `docs/P1-Evidence.md`, `docs/P2-Evidence.md` (p2-publisher branch) |
| Session handoff | android repo main `HANDOFF.md` (updated 2026-07-24) |
| Standalone TODOs | android repo `claude/todos-pq1-pricing` |
| **This checkpoint** | android repo main `docs/Checkpoint-2026-07-24.md` |

---

*The build is ahead of its spec in the ways that matter (stricter untrusted-text handling,
a better entitlement answer, machinery-enforced invariants) and behind it only where a
handset or a decision is genuinely required. The findings above are the entire known gap
between "green in CI" and "true on a phone." Close F1–F4 before the device session and the
exit proof should be a demonstration, not a debugging session.*
