# P4 Runbook — CareerSeeker Pro (billing, entitlement, outcome tracking)

**Written:** 2026-07-24 (Fable, planning role), for the **Opus session executing P4**.
**Read first, in order:** this file → `HANDOFF.md` (this repo, main) →
`docs/Checkpoint-2026-07-24.md` → `docs/Entitlement-Architecture.md` (branch
`claude/p1-runbook`) → `docs/Monetization-Decision.md` (same branch) →
`docs/Sync-Protocol.md` §4.3/§5.4 (public repo). Auto-memory corroborates.
**Spec basis:** `Android-Dashboard-Pro-Spec-2026-07-22.md` §6 (Pro), §8 (P4). Decisions
P-MONEY and P0-WORKER are **closed** — build to them, do not reopen them.

---

## 0. The one constraint that shapes this phase

**There is no Google Play account yet.** Brandon's D-U-N-S number and LLC registration are
2–3 weeks out. Until then there is no Play Console, no merchant profile, no uploaded app, no
`pro_unlock` product, no license testers, and — critically — **no production Play "License
Key" RSA public key** (it only exists in Play Console after the app is created).

**The design rule that makes this a non-problem:** every account-dependent value is
**injectable configuration**, never a hardcoded assumption:

| Account-dependent value | Where it lives until account day |
| --- | --- |
| Play license RSA public key | Injectable into the verifier; harness/vectors use a fixed **test** RSA keypair; production key slots in via config |
| `pro_unlock` product id, price | Already decided ($2.99 INAPP, P-MONEY); constants, referenced from one place |
| `applicationId` / packageName check | `app.careerseeker.dashboard` (see gate P4-APPID below) |
| Real purchase flow | `BillingProvider` seam: `FakePlayBilling` for tests; real client compiles and degrades gracefully |

Everything else in P4 — the protocol amendment, vectors, the engine verifier, the inbound
receive path, outcome tracking, both funnel boards, the Pro screen — is **fully buildable and
provable now**. §6 is the account-day checklist that turns the remainder into one afternoon.

## 1. Session setup (parallel-session discipline — read before touching anything)

A **P5 session runs in parallel** with you, staging store readiness. Collision rules:

- **You (P4) own:** the engine repo entirely; android branch **`claude/p4-pro`** (create off
  `claude/p2-replica`); new files under `:app` (billing, Pro screen, outcome UI) and targeted
  edits to replica/applier/protocol-adjacent code.
- **P5 owns:** android branch `claude/p5-store`; **additive-only** accessibility edits
  (semantics/contentDescription) to the five existing screens; `docs/store/**`. P5 never
  touches the engine repo.
- **Shared-clone hazard:** you both work on the same PC. **P5 uses a separate git worktree**;
  you use the main clones. If you find the android checkout on a branch you didn't create,
  stop and check — never reset someone else's branch.
- If you need to edit one of the five existing screen files beyond adding your new
  outcome/Pro UI entry points, note it in your commit message so the P5 merge is easy.

**Repos/paths:** engine worktree
`C:\Users\bkirk\Documents\CareerSeeker\.claude\worktrees\android-apk-build-setup-90d9d5`
(branch off **`claude/p2-publisher`** → create `claude/p4-entitlement`); android repo
`C:\Users\bkirk\Documents\careerseeker-android` (branch off **`claude/p2-replica`** →
create `claude/p4-pro`).

**Environment facts (verified, don't rediscover):** engine verify via `powershell.exe`
(`pwsh` is NOT installed): `powershell -NoProfile -ExecutionPolicy Bypass -File
scripts/Verify-Alpha.ps1` — current pin **437**; port 7777 must be free (a running alpha
dashboard collides). Android: `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`,
`ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`, ritual `./gradlew checkCoreIsAndroidFree
:core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — currently 25 unit
tests green. AGP 9 has built-in Kotlin (never apply `org.jetbrains.kotlin.android`);
interrupted `--no-daemon` runs corrupt `*/build/kotlin` caches → delete them. Room schema
changes need a version bump + exported schema + migration (schemas under `app/schemas`).
A **Pixel 10 with USB debugging** is available for sideload testing (`adb install`).

**Non-negotiable rules (violations were caught in prior sessions):** drift trap — the
Verify-Alpha pin + its `Assert-Contains` strings + README + src/Engine/README +
Project-Summary + External-Audit-Handoff + repo-audit-2026-07-13 move as ONE commit with any
count change; shared vectors are generated (`node docs/sync-vectors/generate.mjs`,
`--check` proves no drift), NEVER hand-edited; evidence standard "ran it and saw it";
draft PRs only, never self-merge, never push code to main; engine PRs target
`claude/alpha-finish`; secrets by name only; no send path ever; untrusted text stays inert.
Commit only green states.

## 2. Work items, in order

### 2.1 Gate P4-APPID (ask Brandon first — 30 seconds, then unblocked)

`applicationId` is currently **`app.careerseeker.dashboard`**. On the first Play upload it
becomes **permanent forever** — it can never change without becoming a different app.
Confirm with Brandon that this is the forever id before building the verifier's packageName
check around it. (Recommendation: keep it — it matches the namespace and the naming canon.)

### 2.2 Protocol amendment + vectors (engine repo, the invariant-bearing commit)

Per `Entitlement-Architecture.md` §"Protocol impact", amend `docs/Sync-Protocol.md` §4.3:
`entitlement` body changes from `{voucher}` (the dead option-A shape) to
**`{original_json, signature}`** — Google's `Purchase.getOriginalJson()` string verbatim and
its `Purchase.getSignature()` (RSASSA-PKCS1-v1_5 over the exact JSON bytes, **SHA-1**,
base64 **standard** encoding as Play emits it — it is payload content, not envelope framing,
so base64url rules don't apply to it). Record the SHA-1 caveat and its assessment by
reference to Entitlement-Architecture §weakness-1; add the body schema next to §4.3.1 the
way snapshot/delta were pinned. Add a §9 amendment row.

**Vectors:** extend `docs/sync-vectors/generate.mjs` with a **fixed, embedded test RSA
keypair** (PEM in the generator — RSASSA-PKCS1-v1_5 signing is deterministic, so
byte-identical regeneration holds; note the published-test-key warning like the existing
one). Add entitlement vectors: valid; tampered `original_json`; wrong `productId`; wrong
`packageName`; `purchaseState != PURCHASED`. `entitlement` is a **state-changing p2e kind**
(§5.4), so each vector envelope also carries the device ECDSA `sig` — the generator's
embed-and-verify machinery for `doc_edit` already does this; reuse it. Regenerate, run
`--check`, update the vector-count assertions, bump the pin + docs in the SAME commit.
Android side: re-vendor vectors + bump `core/src/test/resources/sync-vectors/VECTORS.lock`
(CI drift-checks via the GitHub **contents API**); `:core` `ProtocolVectorsTest` must pass
against them.

### 2.3 Engine: verifier + entitlement application (`claude/p4-entitlement`)

- `GoogleSignedPayloadVerifier` (new, `src/Sync` or `src/Engine` — prefer `src/Sync`, it is
  pure): verify RSA signature over the exact `original_json` bytes
  (`RSA.VerifyData(..., HashAlgorithmName.SHA1, RSASignaturePadding.Pkcs1)`, native .NET),
  then `packageName == configured`, `productId ∈ {pro_unlock}`, `purchaseState == PURCHASED
  (1)`, `acknowledged` noted. **The RSA public key is a constructor argument** (X.509
  SubjectPublicKeyInfo base64, as Play Console publishes it) — harness passes the test key;
  production key arrives on account day via config. Every rejection reason distinct + tested.
- `EntitlementService` seam per spec §6.3: strategy interface, `GoogleSignedPayloadVerifier`
  is the one shipping strategy; `DeveloperApiVerifier` remains an unbuilt named seam.
- Engine state: entitlement enable/disable is a stored flag (config table) + **audit event**
  recording product, orderId, and the delivering device-key fingerprint. Revocation: on each
  applied entitlement report the flag refreshes; absence past the grace window (30 days,
  constant) clears it. Keep grace logic pure/testable with an injected clock.
- SyncHarness: verifier against all five vectors + the service's apply/refresh/grace logic.

### 2.4 Engine: minimal inbound p2e path (structural, entitlement-only)

The host currently has **no inbound loop** (checkpoint F8, deliberate). P4 builds the
*structure* without touching P3's sacred surface:

- A host-side receive step (behind the same `--sync` seam, still inert until the pairing
  vault exists): `RelayClient.PullAsync("p2e", since)` → `:core`-mirrored `EnvelopeReceiver`
  (already built, verifies device sig on state-changing kinds) → dispatch by kind:
  **`entitlement`** → EntitlementService; **`pull_request`** → re-publish a snapshot via the
  existing bridge (this also banks device-session work); **`outcome`** → apply per §2.5;
  **`doc_edit`** → reply `error{code: unknown_kind-style "unimplemented"}` — P3 owns it; do
  NOT stub any part of the doc-edit apply path. `DispatcherNoSendHarness` must be untouched
  and green.
- The p2e high-water mark persists next to the e2p one (protocol §6.1 applies both ways —
  same vault requirement; note it in the `BuildSyncBridge` seam comment).
- Prove it live the P2 way: extend `tests/SyncLiveSmoke` — a **simulated phone** (same
  `src/Sync` primitives, software ECDSA key) pushes a signed `entitlement` (test-RSA-signed
  payload) and a signed `outcome` through `relay.careerseeker.app`; the engine-side receive
  step pulls, verifies both layers, applies, and a `pull_request` round-trips a snapshot.
  Live smoke stays out of the hermetic pin.

### 2.5 Outcome tracking (Pro's actual feature) — desktop first, it's fully provable now

- **Store:** outcome state per application (`sent | no_reply | replied | interview | offer |
  rejected` + timestamp), joined to the audit chain. SQLite migration + parity in
  `StoreParityHarness` (count moves → drift trap).
- **Desktop:** outcome marking controls on the dashboard applications view (token/Host/
  Origin-protected POST like every mutating control) + a **funnel board** panel (sent →
  reply rate → interviews → results over 7/30/90d) gated by the entitlement flag.
  EngineHarness HTTP coverage (port 7777 note applies). This slice needs no phone, no
  account, no pairing — it is the honest core of Pro and it ships complete.
- **Wire:** outcomes ride to the phone by extending the application summary in
  snapshot/delta with a nullable `outcome` field — amend §4.3.1 + the C# builders + harness
  pins + the Kotlin applier in the same coordinated pair of commits (the shapes are pinned
  on both sides; keep them in lockstep). Phone→engine `outcome` envelopes are already §4.3;
  the engine applies them through §2.4's dispatch.

### 2.6 Android: EntitlementService + Pro screen + outcome UI (`claude/p4-pro`)

- **Billing:** add the Play Billing Library (verify the current minimum supported major
  version against Play's docs at execution time — assume PBL 7/8). `BillingProvider`
  interface: `RealPlayBilling` (connects, queries, purchases; must degrade **gracefully and
  honestly** when the store says unavailable — which it will, all the way until account day)
  + `FakePlayBilling` for unit tests (purchase success / cancel / already-owned / store-
  unavailable). `EntitlementService` on top; state exposed as a Flow the UI projects. No
  Firebase, no analytics — the resolved-classpath CI check must stay green.
- **Entitlement forwarding:** on entitled state, build the `entitlement` envelope
  (`{original_json, signature}`) via `:core` builders + device signature — with a software
  test key until the Keystore key exists (P2 finale). Unit-test the envelope shape against
  the vendored vectors' expectations.
- **Pro screen** (spec §4.1 screen 8): locked state = honest explanation + real feature
  description + purchase/restore buttons that surface the store-unavailable truth plainly
  (no dark patterns, no fake urgency); unlocked state = the **funnel board** computed
  on-device from replica outcomes. **Replica migration:** add nullable `outcome` to
  `ApplicationRow` (Room v1→v2, exported schema, migration test) + applier parses the new
  optional field (absent ⇒ null, never malformed).
- **Outcome marking UI:** two-tap outcome set on Application detail (Pro-gated). It writes
  locally to the replica AND queues the signed `outcome` envelope (send path is inert until
  pairing exists — structure + tests now, device proof later).
- **Pixel 10 sideload check (do it, it's real evidence):** `adb install` the debug build —
  Pro screen renders locked state, billing reports store-unavailable gracefully, demo data
  labeled, nothing crashes. Capture output/screens in the evidence doc.

### 2.7 Evidence + PR prep

`docs/P4-Evidence.md` (this repo) in the P2-Evidence style: what landed, harness/CI
transcripts, the live simulated-phone smoke, the Pixel sideload check, and an explicit
"what remains for account day" list. Push both branches; **draft PRs only when Brandon says
open them** (engine → `claude/alpha-finish` stack; android → its P2 base).

## 3. Explicitly deferred to account day (§6 of the spec dossier still applies)

Real `pro_unlock` product creation; license testers; a real sandbox purchase/refund cycle;
the production RSA license key (slot into config); AAB upload; PBL version re-verification
against live Play docs; merchant profile. **Exit criteria now:** everything in §2 green
offline + live-simulated; **exit criteria then:** sandbox purchase unlocks phone + desktop,
restore works, refund revokes within grace — per spec §8-P4.

## 4. Standing cautions

- The Fabrication Gate, Dispatcher, and doc-edit surfaces are **out of bounds** (P3, Fable).
- `outcome` and `entitlement` are state-changing kinds: device signature verification is
  NEVER optional, including in tests.
- Play's IAB signature is SHA-1/RSA — that is Google's format, not a choice; the assessment
  lives in Entitlement-Architecture and does not need re-litigating in code comments.
- If a work item seems to want generative text (follow-up drafting), STOP — that is Cloud,
  permanently out of Pro's scope (spec §6.1).
