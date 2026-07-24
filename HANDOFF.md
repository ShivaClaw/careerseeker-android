# HANDOFF — CareerSeeker Android Program (for a fresh session)

**Written:** 2026-07-23, end of the **Opus 4.8** session that completed the entire **P2
offline** half (engine snapshot/delta/heartbeat publisher + Android Room replica, applier,
demo fixture, and the five read-only screens). The prior Fable 5 session (P0 → P1 → P2 start)
is recorded in §2a.
**Read next, in order:** this file → `docs/P2-Runbook.md` (branch `claude/p2-runbook`) →
`docs/P2-Evidence.md` (main repo, branch `claude/p2-publisher`) → `docs/P1-Evidence.md`.
Auto-memory (`careerseeker-android-program.md`, `careerseeker-working-rules.md`) has the same
facts in denser form.

**Android Studio answer:** *Open local folder* — `C:\Users\bkirk\Documents\careerseeker-android`.
The repo is cloned with a checksum-verified Gradle wrapper committed. Do NOT re-clone. The
`:app` module (Room replica + screens) lives on branch **`claude/p2-replica`**; `main` has
only docs/scaffold, so check that branch out to see the app code.

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
| **P2** | **Read-only dashboard (Room replica, screens, snapshot/delta publisher)** | **OFFLINE DONE** — engine publisher + Android replica/screens green in CI; device-bound finale remains (§4) |
| P3 | Document view/edit (touches the sacred Dispatcher surface — Fable/max effort) | not started |
| P4 | Pro (Play Billing, entitlement via Google-signed payload) | not started |
| P5 | Store readiness (data-safety, listing, closed test) | not started; D-U-N-S verification in flight |
| P6 | Launch + pricing-page rewrite (rewrite is a LAUNCH BLOCKER) | not started |

**Two repos, deliberate split:** `ShivaClaw/careerseeker` (public: engine, relay,
`docs/Sync-Protocol.md` + shared vectors — a blind relay's claim is worth more when auditable)
and `ShivaClaw/careerseeker-android` (private always: app code, strategy, runbooks, gates).

## 2. What the latest (Opus) session accomplished — P2 offline, both halves

**Engine publisher (public repo, branch `claude/p2-publisher`, 3 commits, pushed, NO PR):**
- `src/Sync/SyncPublisher.cs` — seals `SyncPayloads` (snapshot/delta/heartbeat) with `k_e2p`
  into v1 wire envelopes, assigns the monotonic e2p `seq` the relay enforces, pushes through
  an injected sink. Transport/clock/nonce injectable → offline-testable. A failed push
  **burns** the seq (a legitimate gap) rather than reusing it; e2p envelopes carry no `sig`.
- `src/Engine/EngineSyncBridge.cs` — projects live `EngineCounters` + the recent
  application/job rows the local dashboard already renders into the sync record types;
  snapshot first, deltas thereafter, plus a counters-only heartbeat. Holds no key material;
  carries only structured fields → a raw posting body **structurally cannot** reach the phone.
- Host wiring behind **`--sync` (default OFF)**. With sync off the tick is exactly
  `cycle.TickAsync` (byte-identical behavior). Publishing needs a completed pairing, which is
  device-bound, so `--sync` is honored today but **no-ops with an explicit note** — a clean
  seam in `Program.cs::BuildSyncBridge` where the `RelayClient`-backed sink gets built once the
  pairing vault exists.
- Proof: `Verify-Alpha.ps1` offline **425 passed / 0 failed** (SyncHarness 74→88,
  EngineHarness 89→99; pin + every count-bearing doc moved in-commit per the drift trap).
  `tests/SyncLiveSmoke` **22/22 LIVE** — the publisher pushes a snapshot+delta through
  `relay.careerseeker.app` and a simulated phone reconstructs the counters. Recorded in
  `docs/P2-Evidence.md`.

**Android offline (private repo, branch `claude/p2-replica` off `claude/p1-pairing`, 3
commits, pushed, NO PR; CI green):**
- **Room replica** (`44d0abe`): tables applications / jobs / counters / evidence_events /
  documents / sync_state; schema exported under `app/schemas`; proven against real SQLite via
  Robolectric.
- **EnvelopeApplier** (same commit): takes decrypted envelopes from the `:core` receiver and
  projects them into Room — snapshot = wholesale replace, delta = upsert, heartbeat = counters
  + last-seen. The seq high-water mark is **persisted** in `sync_state`, so a post-restart
  replay no-ops. Field names pinned to the engine's `SyncPayloads`.
- **DemoFixture** (`d0d3a30`): populates the replica with `demoMode=true` at **seq 0** so real
  synced data always wins. Screens are developable/CI-testable with no engine and no pairing.
- **Five read-only Compose screens** (`316fc95`): Home/Live, Applications, Application detail,
  Jobs, Evidence — honest demo / last-seen / audit-unknown states. Pure projection of Room.
- Proof: **17/17 Robolectric** (8 replica incl. a sealed-envelope seam test `:core` receiver →
  applier, 3 fixture, 6 screens); `:app:lintDebug` green with `warningsAsErrors`; the
  no-analytics resolved-classpath CI check stays green.

### 2a. Prior Fable 5 session (P0 + P1 + P2 start) — condensed
Protocol v1 (`docs/Sync-Protocol.md`, NORMATIVE: AES-256-GCM, `p256-hkdf-sha256`, ECDSA P-256
`sig`, fixed-string AAD, provisional→final token rotation). 21 shared vectors, three-way
byte-for-byte agreement (Node ↔ C# `SyncHarness` ↔ Kotlin `:core`). Relay LIVE (Cloudflare
Worker+DO, 32 vitest). Engine `src/Sync` + `:core` JCA-only crypto (Tink dropped). Business
decisions closed: P-MONEY, P0-WORKER (`docs/Entitlement-Architecture.md`), consent copy,
PQ posture.

## 3. Branch/PR topology (all draft when opened, NEVER self-merge; Codex audits, Brandon merges)

**Main/public repo** (`C:\Users\bkirk\Documents\CareerSeeker\.claude\worktrees\android-apk-build-setup-90d9d5`):
- `#5` P0: `claude/android-apk-build-setup-90d9d5` → `claude/alpha-finish` (base per P0-BASE — NOT main)
- `#6` P1: `claude/p1-sync` → the P0 branch (stacked)
- `claude/p2-publisher` (off p1-sync): P2 engine publisher, **3 commits, green+pushed, NO PR yet**

**Private repo** (`C:\Users\bkirk\Documents\careerseeker-android`):
- `#1` P0 scaffold: `claude/p0-scaffold` → main; `#2` P1 runbook: `claude/p1-runbook`
- `#3` P1 :core: `claude/p1-pairing` → p0-scaffold (stacked)
- `claude/p2-replica` (off p1-pairing): P2 `:app` replica + screens, **3 commits, green+pushed,
  CI green, NO PR yet**
- `claude/p2-runbook` (P2 plan), `claude/todos-pq1-pricing` (standalone TODOs for a Sonnet
  session: `docs/todo/PQ1-Hybrid-Migration.md`, `docs/todo/Pricing-Page-Rewrite.md`)

**PRs deliberately not opened** — both P2 branches are stacked and green; Brandon decides when
to open the draft PRs for Codex audit (publisher → `claude/alpha-finish`; replica → its P1 base).

## 4. Immediate next steps (what's actually left in P2)

The offline half is done. What remains is **device-bound** and needs Brandon + a handset:

1. **Answer the three open P2 gates** (recs in `docs/P2-Runbook.md` §4 — Brandon decides):
   P2-KEYSTORE-FALLBACK (rec: pair with a logged software-key downgrade), P2-PIN-ROTATION
   (rec: pin leaf+backup with a rotation runbook), P2-REPLICA-CRYPTO (rec: Android platform
   encryption; avoid SQLCipher's native `.so`).
2. **Phone pairing UI** (`:app`): CameraX + ML Kit QR scan, the pairing screens, and the ECDSA
   P-256 device key in the Android **Keystore** (StrongBox where available; software fallback
   only per gate 1). `CAMERA` permission enters the manifest **with its feature** here.
3. **Desktop `/pair` page** (engine dashboard): renders the QR (`PairingManager.CreateInvite()`
   is done), token/Host/Origin-protected like every other mutating dashboard control. This is
   also where `BuildSyncBridge` stops no-op'ing: load the persisted pairing, back the sink with
   `RelayClient.PushAsync`, construct the publisher.
4. **Live transport** (`:app` + `src/Sync`): WSS + pull-on-open (relay hibernation already
   exists), cert-pinning to `relay.careerseeker.app` (per gate P2-PIN-ROTATION).
5. **P2 exit proof:** a demo cycle on the PC ticks the phone's Home in near-real time over WSS;
   then airplane-mode and every screen still reads from the Room replica. Capture it as
   evidence the way P1/P2-Evidence did.

**A pure-offline task available now if you don't have a handset:** define the engine `doc` and
`evidence` **wire payloads** in `docs/Sync-Protocol.md` §4.3 + `SyncPayloads`, then extend the
applier. Today the replica's `documents` table and `auditOk` are **fixture-fed only** — the
applier deliberately has **no branch** for those shapes because the engine doesn't emit them
yet. `doc` bodies are P3's editing surface, but read-only `doc`/`evidence` *rendering* is P2.

## 5. Environment facts (verified, don't rediscover)

- Local Kotlin builds WORK: `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`,
  `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`. Run:
  `./gradlew checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug`
- **`:app` toolchain (pinned this session):** Room **2.8.4** + KSP **2.3.10** (KSP versions
  independently of Kotlin since 2.3 — do not expect them to match); Robolectric **4.16.1** at
  `@Config(sdk=35)` (the SDK 36 image wants a Java 21 test JVM, module pins 17);
  `material-icons-core` pinned **1.7.8** (its terminal release — the m3 lib / Compose BOM no
  longer ship icons); `kotlinx-serialization-json` used via the **runtime tree API only** (no
  codegen plugin).
- Base toolchain: AGP 9.3.0 / Gradle 9.6.1 (wrapper committed, sha-pinned) / Kotlin 2.4.10 /
  compileSdk=targetSdk **37** / minSdk 26. AGP 9 has built-in Kotlin — never apply
  `org.jetbrains.kotlin.android`. Interrupted `--no-daemon` runs corrupt `*/build/kotlin`
  caches → `rm -rf app/build/kotlin core/build/kotlin`.
- Engine verify uses **`powershell.exe`** (`pwsh` / PowerShell 7 is NOT installed on this box):
  `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Alpha.ps1`.
- Relay: wrangler OAuth as brandongkirksey@gmail.com, acct `1219051ffe69babd0286f747d8ac33bb`.
  Edge lags fresh deploys ~1 min on the custom domain — re-probe, don't redeploy.
- Vendored vectors in android CI are drift-checked via the GitHub **contents API** (raw
  .githubusercontent lags fresh SHAs → flaky 404). Pin: `core/src/test/resources/sync-vectors/VECTORS.lock`.
- GitHub blob links with `claude/...` branch names 404 (slash ambiguity) — link by commit SHA.

## 6. Non-negotiable working rules (inherited; violations were corrected in prior sessions)

- **Drift trap:** `$ExpectedOfflineTotal` (Verify-Alpha.ps1) + the script's own `Assert-Contains`
  count strings + README + src/Engine/README + Project-Summary + External-Audit-Handoff +
  repo-audit-2026-07-13 move as ONE commit. Same for shared vectors (regenerate via
  `node docs/sync-vectors/generate.mjs`, `--check` proves no drift; never hand-edit).
- **Evidence standard:** "ran it and saw it" or it didn't happen. Cite command output.
- Draft PRs only; never self-merge; never push code to main (docs-only exceptions — like this
  handoff — are fine).
- Secrets by name only. Sending email is not this program's to build — no send path, ever.
- Job descriptions/recruiter text = untrusted data; never ships to the phone as raw body in P2.
- Engine-side PRs target `claude/alpha-finish`, not `main`, until the alpha train merges.
- Substring-grepping source for forbidden tokens caused false positives — make checks structural
  (parsed columns, resolved classpath, parsed plugins block).

## 7. Model guidance (recorded for Brandon)

P2 device-bound UI/wiring: **Opus 4.8 normal effort**. P3 (Dispatcher-adjacent doc-edit):
Fable 5 high effort. PQ-1 + pricing rewrite: Sonnet with the standalone TODOs in `docs/todo/`.
