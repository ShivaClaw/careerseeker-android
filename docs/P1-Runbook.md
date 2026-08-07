# Android Program — P1 Runbook (Blind relay + pairing, end to end)

**Phase:** P1 of the Android Dashboard / CareerSeeker Pro program (spec §8).
**Written:** 2026-07-23 by the executing model, before labor, per the program rule: each
phase opens with a runbook reviewed as a draft PR. **No P1 labor has started.**
**Prerequisite state:** P0 exit criteria met — protocol v1 + vectors merged-pending-audit
([careerseeker#5](https://github.com/ShivaClaw/careerseeker/pull/5)), app scaffold green
([#1](https://github.com/ShivaClaw/careerseeker-android/pull/1)), local Kotlin builds now
work (wrapper committed, checksum-verified; JDK 17 auto-provisioned via foojay).

**Spec's P1 exit:** phone pairs with desktop through the real relay; both sides prove
replay rejection and signature verification; relay storage inspected to confirm
ciphertext-only. Codex audit before merge.

---

## 1. The finding that shapes this phase: the spec's pairing crypto cannot be built as written

Spec §3.1 prescribes **X25519** ECDH for pairing and **Ed25519 in Android Keystore** for
the device signing key. Verified 2026-07-23, both halves fail:

- **Engine (.NET 8):** `System.Security.Cryptography` has neither X25519 nor Ed25519.
  X25519 arrives as a first-class type only in **.NET 11 previews**
  ([dotnet/runtime#126206](https://github.com/dotnet/runtime/issues/126206)); Ed25519 has
  been an open API proposal since 2021
  ([dotnet/runtime#63174](https://github.com/dotnet/runtime/issues/63174)). Building the
  spec as written means a third-party crypto library (BouncyCastle or NSec/libsodium) on
  the engine's security-critical path — the exact posture P0-CIPHER rejected.
- **Android (minSdk 26):** Android Keystore only supports Curve25519 keys from **API 33**
  (Android 13). At minSdk 26, "Ed25519 in Keystore (StrongBox where available)" is
  impossible — the device key would have to live in software (a Tink keyset on disk),
  which surrenders the hardware-backed non-exportability that is the entire point of a
  device key.

### Gate P1-CURVE — ANSWERED 2026-07-23: **P-256 both sides**

Gated alongside a standing directive to build post-quantum going forward, Q-day assumed
2029. Both hold without conflict; the full reasoning is
[Post-Quantum-Posture.md](Post-Quantum-Posture.md). In short: the spec's alternative
(X25519) was *equally* quantum-broken, so nothing was surrendered by choosing P-256;
.NET's `MLKem` requires Windows 11 25H2+/OpenSSL 3.5 and the engine is still on .NET 8, so
a post-quantum handshake would hard-fail on real users' machines today; and NIST/IETF
prescribe **hybrid**, not PQ-only, during transition — shipping ML-KEM alone would be
worse practice than P-256 alone.

**P1's post-quantum obligation is therefore to make the migration cheap, not to perform it
early and badly.** Four constraints, folded into §2.1 at near-zero cost. Skipping any of
them is what makes the eventual migration expensive:

1. **The pairing payload carries its own `suite` identifier**, versioned separately from
   the envelope's `v`: `p256-hkdf-sha256` for v1, `p256+mlkem768-hkdf-sha256` reserved.
   Negotiated once at pairing, never mid-session.
2. **Session keys derive via HKDF from a *concatenated* secret**, not from the raw ECDH
   output — `ikm = ecdh_shared` today, `ikm = ecdh_shared || mlkem_shared` under hybrid.
   Deriving straight from the ECDH result makes adding ML-KEM a breaking change for every
   paired device; deriving through a concatenation of one element makes it a suite bump.
   **Highest-leverage item in this runbook, and it costs one function today.**
3. **No fixed-width key fields on the wire, and the QR payload budget checked against
   ML-KEM-768 sizes now** (1184-byte encapsulation key vs P-256's 32). A QR that
   physically cannot carry a hybrid key would force a pairing-UX redesign at migration
   time — far cheaper to discover in P1 than in PQ-1.
4. **`suite` recorded in the engine's audit chain at pairing**, so "which cryptography was
   this pairing built on" is answerable later without surveying users.

The original gate framing is retained below: the platform facts in it are what the answer
rests on, and they will need re-checking at PQ-1.

#### Original framing (retained for the platform facts)

| | **A: P-256 everywhere (recommended)** | **B: X25519/Ed25519 per spec** |
| --- | --- | --- |
| Engine ECDH | `ECDiffieHellman` — native .NET, all OSes | BouncyCastle or NSec dependency |
| Engine sig verify | `ECDsa` — native | Same new dependency |
| Phone key storage | Keystore ECDSA P-256: hardware-backed from API 23, StrongBox from 28 — covers effectively every minSdk-26 device | Software keyset below API 33, or raise minSdk to 33 |
| Deps added | **None, either side** | One crypto lib (engine) + weaker key storage (phone) |
| Security delta | Curve25519's margins (twist security, simpler constant-time impls) are real but library-internal; hardware non-exportability of the device key matters more at our threat model | Preserves the curve name at the cost of the hardware guarantee |

**Recommendation: A.** It is the same reasoning that settled P0-CIPHER — native
primitives on both sides beat a spec-named algorithm that forces new dependencies — plus
one stronger argument: option B *weakens actual security* (software device keys on every
device below Android 13) to honor a curve choice made before anyone checked platform
support. If A is gated in, `docs/Sync-Protocol.md` §5.2/§5.4 and `:core`'s constants are
amended **in the same commit** as the implementation, drift-trap style. HKDF structure,
info strings, AAD format, and envelope shape are unchanged — only the curve names move.

### Gate P1-DEPLOY — ANSWERED 2026-07-23: **deploy to workers.dev for P1**

The custom domain (`relay.careerseeker.app`) stays open and must be decided **before**
P2's certificate-pinning work, since pinning wants the final hostname. Original framing
retained below.

#### Original framing

The relay has never been deployed; P0 proved config validity with `--dry-run` only.
End-to-end pairing needs it live. Facts for the gate:

- Durable Objects run on the **Workers Free plan** (Cloudflare changelog 2025-04-07), and
  SQLite-backed DOs are the recommended default — expected cost of a single-user test
  relay is ~$0. But this is a live internet surface under our account, so it is a
  Brandon-only gate per the working rules, not a cost question.
- Decision inside the gate: `careerseeker-relay.<subdomain>.workers.dev` (zero DNS work,
  fine for P1) vs `relay.careerseeker.app` (custom domain; the eventual production name —
  cert pinning in P2+ wants the final hostname early). Recommendation: **workers.dev for
  P1**, custom domain decided before P2's pinning work.

### Already-open gates that touch P1

- **P0-SYNC-COPY** ([Sync-Consent-Copy.md](Sync-Consent-Copy.md), awaiting review) gates
  the *wording* on the desktop "Pair phone" page and the phone onboarding screen. P1
  builds those screens using the draft copy; shipping wording is whatever the gate
  approves. Not build-blocking, review-blocking.
- **P0-WORKER** does not touch P1 (entitlement is P4).

---

## 2. Work items

Ordered so that each item's output is the next item's test fixture. Engine-side items
land on a branch off `claude/alpha-finish` in the main repo (per P0-BASE); app items on a
branch here.

### 2.1 Protocol amendments + pairing vectors (main repo)

`docs/Sync-Protocol.md` gains: the P1-CURVE outcome in §5.2/§5.4; **relay token
derivation** (currently unspecified — the bearer token the relay sees must be derived, not
invented): `relay_token = HKDF(ikm, salt=one_time_secret, info="careerseeker/v1/relay-token", 32)`,
base64url — so both endpoints derive it and the relay cannot link it to anything;
the 6-digit confirmation code derivation (`HKDF(..., info="careerseeker/v1/confirm", 4)`
mod 10^6, displayed both screens).

`docs/sync-vectors/generate.mjs` gains a `pairing/` vector set: ECDH shared-secret →
HKDF → both directional keys + relay token + confirm code, from fixed test keys (Node's
`crypto` has both P-256 and X25519, so the generator is ready for either gate outcome),
plus `device_sig` vectors over the §5.4 signing string — replacing the placeholder
signature in `doc-edit-signed`. Invalid cases: wrong confirm code, reused one-time
secret, bad signature, signature by a revoked key.

`SyncHarness` consumes all of it; `$ExpectedOfflineTotal` and every count-bearing doc move
in the same commit. **Acceptance:** `Verify-Alpha.ps1` green at the new pin;
`generate.mjs --check` clean; the Kotlin `:core` tests (2.4) read the same files.

### 2.2 Relay implementation (main repo, `relay/`)

Replace the 501s: `push` (validate header shape + size vs `MAX_ENVELOPE_BYTES`, store
`{dir, seq, ts, key_id, nonce, ciphertext, size, expires_at}`, reject duplicate seq),
`pull?since=` (ordered page, metadata + ciphertext verbatim), `live` (WebSocket
**hibernation API** so idle pairings cost nothing), `DELETE` (purge DO). TTL purge via DO
**alarms**, not opportunistic sweeps. Auth: constant-time compare of the bearer against
the pairing's registered token (registered at first `push` with a pairing-created flag —
exact bootstrap documented in the protocol doc).

Tests to add beyond the P0 suite: seq-duplicate rejection, TTL expiry via simulated
alarm, hibernation wake, oversized push → 413, **a storage-inspection test that dumps DO
rows and asserts every row matches the schema and no column contains valid UTF-8 JSON**
(ciphertext-only, proven not asserted). The existing no-decryption-path CI grep stays.
**Acceptance:** `npm test` green; `wrangler deploy --dry-run` clean; still **no deploy**
without Gate P1-DEPLOY.

### 2.3 Engine `src/Sync/` + Pair-phone page (main repo)

`PairingManager` (keypair, one-time secret with 60s TTL + single-use burn, QR payload
`{pairing_id, engine_pub, relay_url, one_time_secret}`, confirm-code derivation, key
storage in the existing DPAPI vault pattern — **never plaintext on disk**),
`RelayClient` (push/pull over HTTPS; WSS deferred to P2 — pull is enough for P1's exit),
`SyncPublisher` skeleton behind a config flag **default OFF** (sync is opt-in; the flag
gates even the pairing page). Dashboard "Pair phone" page: QR rendered via **QRCoder
1.8.0** (MIT, managed, no native deps — dependency decision recorded here rather than
made silently; alternative is a vendored JS encoder if Codex objects), token-protected
like every other mutating dashboard control, copy from the P0-SYNC-COPY draft. Doctor
check: relay reachable, pairing state.

**Acceptance:** new `SyncHarness` sections (pairing round-trip from vectors, secret
single-use, confirm-code mismatch, DPAPI round-trip); `DispatcherNoSendHarness`
unchanged and green; dashboard page passes the same Host/Origin/token checks the
existing EngineHarness dashboard tests enforce (extend them).

### 2.4 Android pairing flow (this repo)

New deps (verified against artifact repos 2026-07-23): Tink **1.23.0** (AEAD + ECDH),
CameraX **1.6.1** + ML Kit barcode-scanning **17.3.0** (bundled model — keeps Google
Play services optional, spec §3.2), OkHttp **5.4.0**, kotlinx-serialization **1.11.0**
(R8 keep rules arrive with it, per the P0 rule: rules land with the code that needs
them). `CAMERA` permission enters the manifest **in this phase**, with its feature.

`:core` gains the real codec: AES-256-GCM via Tink, envelope encode/decode, AAD builder,
`SequenceTracker` wired to it — **passing every shared vector file**, which is the moment
cross-repo drift protection becomes real. `:app` gains Onboarding/Pairing (spec §4.1
screen 1): explain → scan → confirm code → paired; device key generated in Keystore
(curve per P1-CURVE); replica-free — nothing to store yet beyond pairing state in
EncryptedSharedPreferences/DataStore.

**Acceptance:** `:core` vector conformance green in CI; `checkCoreIsAndroidFree` still
green (Tink is a `:app`-side dependency for key management; `:core` keeps zero Android
imports — the codec's Tink use goes through an interface implemented in `:app`, or Tink's
pure-JVM artifact if it proves Android-free in practice; resolve at implementation and
record which); lint green; pairing flow demo on a real device.

### 2.5 End-to-end proof (needs P1-DEPLOY)

Live relay + engine on the PC + app on a phone: pair in under two minutes (spec success
criterion 1), then prove the negative paths — replayed envelope rejected by both
receivers, tampered signature rejected by the engine, reused QR rejected. **Inspect relay
storage** (`wrangler` DO storage dump) and attach the ciphertext-only evidence to the PR.
Unpair; verify DO purged and phone state wiped.

---

## 3. Explicitly NOT in P1

Room replica and dashboard screens (P2); WSS live feed on the engine side (P2);
doc/doc_edit payloads (P3); FCM (v1.1); entitlement (P4); certificate pinning (P2+, wants
the final hostname); any relay deploy before its gate; any change to the Dispatcher or
Gate surfaces.

---

## 4. Verification ritual

Every engine-side commit: `scripts\Verify-Alpha.ps1` offline green at the pinned total,
counts and docs moved together. Every app-side commit: `./gradlew checkCoreIsAndroidFree
:core:test :app:assembleDebug :app:lintDebug` — now runnable locally. Never assert a pass
without running it. Draft PRs, never self-merge, Codex audits before merge.

## 5. Gates opened by this runbook

| Gate | Decision | Status |
| --- | --- | --- |
| **P1-CURVE** | P-256 both sides, with the four PQ-migration constraints in §1 | **Answered 2026-07-23** |
| **P1-DEPLOY** | Deploy the relay | **Answered 2026-07-23 — deployed** |
| **P1-DOMAIN** | **`relay.careerseeker.app`**, provisioned now and used from P1; `workers_dev` disabled so exactly one hostname is live | **Answered 2026-07-23 — live and verified** |
| **P0-WORKER** | **Engine verifies Google's signed purchase payload** — neither option the spec offered | **Answered 2026-07-23** — [Entitlement-Architecture.md](Entitlement-Architecture.md) |
| **P-MONEY** | Dashboard $4.99, Pro $2.99 one-time, Cloud $1.99/mo; Windows app stays **"CareerSeeker"** | **Answered 2026-07-23** — [Monetization-Decision.md](Monetization-Decision.md) |
| **PQ-1** | Hybrid `p256+mlkem768` migration | Open — trigger conditions in [Post-Quantum-Posture.md](Post-Quantum-Posture.md) §5; engine `.NET 8 → 10` is the current blocker, not the phone |

**The relay is live**, deployed 2026-07-23 as part of answering P1-DOMAIN. It runs the P0
scaffold, so every `/v1/{pairing}/*` route returns 501 by design; §2.2 replaces those.
Verified live: health 200, unauthenticated 401, authenticated 501, malformed pairing 404,
and the `workers.dev` hostname does not resolve.

Estimated agent labor (spec §8): 30–40 h across 2.1–2.5.
