# HANDOFF — CareerSeeker Android Program (for a fresh Opus session)

**Written:** 2026-07-23, end of the Fable 5 session that executed P0 → P1 → start of P2.
**Read next, in order:** this file → `docs/P2-Runbook.md` (branch `claude/p2-runbook`) →
`docs/P1-Evidence.md` (main repo, branch `claude/p1-sync`). Auto-memory
(`careerseeker-android-program.md`) has the same facts in denser form.

**Android Studio answer:** *Open local folder* — `C:\Users\bkirk\Documents\careerseeker-android`.
The repo is already cloned with a checksum-verified Gradle wrapper committed. Do NOT create a
new project or re-clone.

---

## 1. The forest (overall roadmap)

Two products: **CareerSeeker Dashboard** (paid Android companion, $4.99 one-time) and
**CareerSeeker Pro** (in-app purchase, $2.99 one-time). Spec:
`C:\Users\bkirk\Desktop\Career Seeker\Android-Dashboard-Pro-Spec-2026-07-22.md`.
Phased, gate-driven; each phase opens with a runbook as a draft PR before labor.

| Phase | Scope | Status |
| --- | --- | --- |
| P0 | Decisions + skeletons (protocol doc, vectors, relay scaffold, app scaffold) | **DONE**, CI green both repos |
| P1 | Blind relay + pairing end to end | **DONE** minus device-bound UI (see §4) |
| **P2** | **Read-only dashboard (Room replica, screens, snapshot/delta publisher)** | **STARTED** — runbook written, first increment green |
| P3 | Document view/edit (touches the sacred Dispatcher surface — Fable/max effort) | not started |
| P4 | Pro (Play Billing, entitlement via Google-signed payload) | not started |
| P5 | Store readiness (data-safety, listing, closed test) | not started; D-U-N-S verification in flight |
| P6 | Launch + pricing-page rewrite (rewrite is a LAUNCH BLOCKER) | not started |

**Two repos, deliberate split:** `ShivaClaw/careerseeker` (public: engine, relay,
`docs/Sync-Protocol.md` + shared vectors — a blind relay's claim is worth more when auditable)
and `ShivaClaw/careerseeker-android` (private always: app code, strategy, runbooks, gates).

## 2. What this session accomplished (P0 + P1 + P2 start)

- **Protocol v1** (`docs/Sync-Protocol.md`, main repo — NORMATIVE): AES-256-GCM envelopes,
  P-256 ECDH pairing suite `p256-hkdf-sha256` (ikm=concat → PQ hybrid is a suite bump),
  ECDSA P-256 device signature as top-level envelope `sig` over AAD+nonce+ct-hash (no JSON
  canonicalization anywhere), derived relay tokens + 6-digit confirm code, provisional→final
  token rotation, one-shot pairing completion with phone_pub bound into the AAD.
- **Shared vectors** (21 files, `docs/sync-vectors/v1/`, Node generator, deterministic,
  embed-and-verify for ECDSA): **three-way byte-for-byte agreement proven** — Node generator ↔
  C# `src/Sync`+`SyncHarness` ↔ Kotlin `:core` `ProtocolVectorsTest`.
- **Relay LIVE at `https://relay.careerseeker.app`** (Cloudflare Worker + DO, `workers_dev`
  false): bootstrap/rotate, pair completion, push/pull (relay-side seq monotonicity), TTL via
  alarms, WS hibernation, token stored as SHA-256 constant-time. 32 vitest green.
- **Engine `src/Sync`**: codec, PairingCrypto, DeviceSignature, EnvelopeReceiver (check-order
  state machine), PairingManager (single-use secret burn), RelayClient.
- **`tests/SyncLiveSmoke` 17/17 LIVE**: full pairing + signed doc_edit + wrong-key rejection +
  replay 409 + unpair, against the real relay. P1 exit condition minus a physical phone.
- **Android `:core`**: JCA-only crypto (Tink DROPPED — 20-line HKDF; :core stays zero-dep,
  Android-free, enforced by `checkCoreIsAndroidFree`), receiver mirroring the C# one exactly.
- **P2 started**: `docs/P2-Runbook.md` + first increment `src/Sync/SyncPayloads.cs`
  (snapshot/delta/heartbeat builders; untrusted-text invariant asserted — no posting body ever
  ships to the phone). Offline pin now **401** (`$ExpectedOfflineTotal` in `Verify-Alpha.ps1`).
- **Business decisions closed**: P-MONEY (Dashboard $4.99 / Pro $2.99 / Cloud $1.99mo; Windows
  app is "CareerSeeker", never "Basic"); P0-WORKER (engine verifies Google's signed purchase
  payload — no second server, `docs/Entitlement-Architecture.md`); Cloudflare named in consent
  copy (`docs/Sync-Consent-Copy.md`); PQ posture (`docs/Post-Quantum-Posture.md`).

## 3. Branch/PR topology (all draft, NEVER self-merge; Codex audits, Brandon merges)

**Main repo** (`C:\Users\bkirk\Documents\CareerSeeker\.claude\worktrees\android-apk-build-setup-90d9d5`):
- `#5` P0: `claude/android-apk-build-setup-90d9d5` → `claude/alpha-finish` (base per P0-BASE — NOT main)
- `#6` P1: `claude/p1-sync` → the P0 branch (stacked)
- `claude/p2-publisher` (off p1-sync): P2 increment, green+pushed, **no PR yet** — stack more P2 first

**Private repo** (`C:\Users\bkirk\Documents\careerseeker-android`):
- `#1` P0 scaffold: `claude/p0-scaffold` → main; `#2` P1 runbook: `claude/p1-runbook`
- `#3` P1 :core: `claude/p1-pairing` → p0-scaffold (stacked)
- `claude/p2-runbook` (P2 plan), `claude/todos-pq1-pricing` (two standalone TODOs for a Sonnet
  session: `docs/todo/PQ1-Hybrid-Migration.md`, `docs/todo/Pricing-Page-Rewrite.md`)

## 4. Immediate next steps (P2, in order)

1. **Offline, no gate needed (do first):** wire `SyncPublisher` into the engine host — seal
   `SyncPayloads` output with `k_e2p`, push via `RelayClient` on cycle/state-transition, behind
   `sync.enabled` config **default OFF**. Extend `SyncHarness` (bump pin + all count-bearing
   docs in the SAME commit — see drift trap, §6).
2. **Android offline:** Room replica schema + envelope applier in `:app` (receiver already in
   `:core`); demo-mode fixture; then screens (Home/Applications/Detail/Jobs/Evidence) from
   fixtures. Compose, read-only, no editing (that's P3).
3. **Device-bound (needs the physical phone + Brandon present):** phone pairing UI (CameraX +
   ML Kit QR, ECDSA P-256 key in Keystore) + desktop `/pair` page (QRCoder 1.8.0, token-protected
   like other dashboard controls). Then the P2 exit proof: live tick + airplane-mode replica.
4. **Three P2 gates open** (recs in P2-Runbook §4, Brandon decides): P2-KEYSTORE-FALLBACK
   (rec: pair with logged software-key downgrade), P2-PIN-ROTATION (rec: pin leaf+backup with
   rotation runbook), P2-REPLICA-CRYPTO (rec: platform encryption; avoid SQLCipher's native .so).

## 5. Environment facts (verified, don't rediscover)

- Local Kotlin builds WORK: `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`,
  `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`. Run:
  `./gradlew checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug`
- Toolchain: AGP 9.3.0 / Gradle 9.6.1 (wrapper committed, sha-pinned) / Kotlin 2.4.10 /
  compileSdk=targetSdk **37** / minSdk 26. AGP 9 has built-in Kotlin — never apply
  `org.jetbrains.kotlin.android`. Interrupted `--no-daemon` runs corrupt
  `app/build/kotlin` caches → `rm -rf app/build/kotlin core/build/kotlin`.
- Relay: wrangler OAuth as brandongkirksey@gmail.com, acct `1219051ffe69babd0286f747d8ac33bb`.
  Edge lags fresh deploys ~1 min on the custom domain — re-probe, don't redeploy.
- Vendored vectors in android CI are drift-checked via the GitHub **contents API** (raw
  .githubusercontent lags fresh SHAs → flaky 404). Pin: `core/src/test/resources/sync-vectors/VECTORS.lock`.
- GitHub blob links with `claude/...` branch names 404 (slash ambiguity) — link by commit SHA.

## 6. Non-negotiable working rules (inherited; violations were corrected this session)

- **Drift trap:** `$ExpectedOfflineTotal` (Verify-Alpha.ps1) + harness counts + README +
  src/Engine/README + Project-Summary + External-Audit-Handoff + repo-audit-2026-07-13 move as
  ONE commit. Same for shared vectors (regenerate via `node docs/sync-vectors/generate.mjs`,
  `--check` proves no drift; never hand-edit).
- **Evidence standard:** "ran it and saw it" or it didn't happen. Cite command output.
- Draft PRs only; never self-merge; never push code to main (docs-only exceptions existed in P0).
- Secrets by name only. Sending email is not this program's to build — no send path, ever.
- Job descriptions/recruiter text = untrusted data; never ships to the phone as raw body in P2.
- Engine-side PRs target `claude/alpha-finish`, not `main`, until the alpha train merges.
- Substring-grepping source for forbidden tokens caused 3 false positives — make checks
  structural (parsed columns, resolved classpath, parsed plugins block).

## 7. Model guidance (recorded for Brandon)

P2 UI/wiring: **Opus 4.8 normal effort**. P3 (Dispatcher-adjacent doc-edit): Fable 5 high
effort. PQ-1 + pricing rewrite: Sonnet with the standalone TODOs in `docs/todo/`.
