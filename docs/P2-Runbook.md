# Android Program — P2 Runbook (Read-only dashboard)

**Phase:** P2 of the Android Dashboard / CareerSeeker Pro program (spec §8).
**Written:** 2026-07-23 by the executing model, before labor, per the program rule.
**No P2 labor has started.**

**Prerequisite state (P1, done and proven):** the wire protocol, the live blind relay at
`relay.careerseeker.app`, the engine `src/Sync` library (codec, pairing, receiver), and
the Android `:core` codec — all cross-verified against shared vectors, with a live
end-to-end pairing round-trip at 17/17 ([P1-Evidence.md](P1-Evidence.md) in the main repo).

**Spec's P2 exit:** a demo cycle on the PC visibly ticks on the phone in near-real time;
airplane-mode replica reads correctly. Codex audit before merge.

---

## 1. The dependency P1 deliberately left, and how P2 handles it

P1 proved the crypto and the transport but deferred **two UI pieces** that P2's exit test
needs, because they require a physical device to verify honestly:

- the **phone pairing flow** (CameraX + ML Kit QR scan, ECDSA P-256 device key in the
  Android Keystore, the pairing Compose screens), and
- the desktop **"Pair phone" page** on the engine dashboard.

**P2 absorbs both as §2.1 — its first work item.** Everything the pairing UI *drives* is
already built and vector-tested (`PairingManager`, `:core` derivation, the QR invite); the
missing part is camera/Keystore/Compose wiring, which is exactly what a device session
should do.

**The screens do not wait on pairing, though.** Per the spec, a **demo-mode fixture**
populates the Room replica so every screen is built and tested without a live engine or a
paired phone (§2.4). So the phases run in parallel: fixtures unblock UI work in CI; the
pairing UI and the live tick are the device-bound finale.

## 2. Work items

### 2.1 Carryover: phone pairing UI + desktop Pair-phone page (device-bound)

- **Android `:app`:** onboarding/pairing screen (spec §4.1 screen 1) — explain the model,
  CameraX preview, ML Kit barcode scan of the QR, then the `:core` handshake (P-256 ECDH →
  derive keys → 6-digit confirm), and generate the **ECDSA P-256 device key in the Android
  Keystore** (StrongBox where available; software fallback only with an explicit, logged
  downgrade — gate P2-KEYSTORE-FALLBACK). Submit the completion via the relay. `CAMERA`
  permission enters the manifest here **with its feature**, per the standing rule.
- **Engine (`src/Engine` dashboard):** a token-protected `/pair` page that calls
  `PairingManager.CreateInvite()`, renders the QR (**QRCoder 1.8.0**, MIT, managed — decision
  recorded in the P1 runbook), bootstraps the relay channel, polls for the completion, and
  displays the matching confirm code. Same Host/Origin/token checks as every other mutating
  dashboard control (extend the EngineHarness dashboard tests).
- **Acceptance:** a real phone pairs with a real desktop through the live relay in under two
  minutes (spec success-criterion 1). This is the one test that needs a handset.

### 2.2 Engine snapshot/delta publisher (`src/Sync` + `src/Engine`, main repo)

Wire a `SyncPublisher` into the engine's existing lifecycle. The surface already exists:
`EngineCore` counters (`Discovered/Acted/Drafted/Blocked/Rejected/Errors/Cycles`), the
SQLite recent-application/job summaries (see `StoreParityHarness`), and the `/evidence`
audit metadata.

- **`snapshot`** on pairing and engine start: full dashboard state — counters, recent
  applications (state/company/title/score), recent jobs, recent audit-event metadata.
- **`delta`** on each cycle / state transition: what changed since a sequence point.
- **`heartbeat`** on a timer: `{ts, cycle, counters}` for the phone's "last seen".
- Each envelope is sealed with `k_e2p` and pushed via `RelayClient`. Publishing is behind
  the `sync.enabled` flag (**default off**; opt-in, privacy-load-bearing — the consent copy
  is [Sync-Consent-Copy.md](Sync-Consent-Copy.md)).
- **Untrusted-text rule:** job descriptions and recruiter text ride as display-only strings.
  They are never interpolated, never rendered with active content (CLAUDE.md invariant).
- **Acceptance:** `SyncHarness` gains snapshot/delta/heartbeat codec cases (bump the pinned
  total + docs together); a `SyncLiveSmoke` extension pushes a snapshot+delta through the
  live relay and a simulated phone reconstructs the counters.

### 2.3 Android Room replica (`:app`)

- Room schema mirroring dashboard state: applications, jobs, counters, evidence metadata,
  and a `sync_state` row (highest seq per direction, last-seen). Encrypted at rest via
  Android platform encryption; erasable on unpair.
- An **applier** that takes decrypted envelopes from `:core`'s receiver and projects them
  into Room. The UI is a **pure projection of Room** — offline-readable (spec Part 6). The
  receiver already lives in `:core` and is vector-tested; the applier is new and belongs in
  `:app` (it touches Room).
- **Acceptance:** Robolectric/instrumented tests that apply a snapshot then a delta and
  assert the projected state; an airplane-mode test that reads the replica with no network.

### 2.4 Demo-mode fixture (`:app`)

A fixture that populates Room with representative data **without a live engine or pairing**,
so screens are developable and CI-testable on their own. This is what keeps §2.5 off the
critical path behind §2.1's device work.

- **Acceptance:** `assembleDebug` renders every screen from fixtures; screen tests run in CI
  with no relay and no device.

### 2.5 Screens (`:app`, spec §4.1 screens 2–6)

Home/Live, Applications (list + filters), Application detail (score, evidence, the three
documents **read-only in P2** — editing is P3), Jobs, Evidence (audit-chain view + hash
verify badge). Compose, phone-first. Honest offline/last-seen states.

- **Acceptance:** each screen renders from fixtures in a screen test; lint green with
  `warningsAsErrors`; no analytics dependency (the resolved-classpath CI check stays).

### 2.6 Live transport: WSS + pull-on-open (`:app` + `src/Sync`)

P1 used pull only. P2 adds the live feed:

- **Engine:** push continues over HTTPS; the phone's live view uses the relay's **WebSocket
  hibernation** channel (already implemented relay-side) plus **pull-on-open** to catch up
  gaps, plus an optional WorkManager periodic pull. Keeps Google Play Services optional (no
  FCM in v1 — that's v1.1).
- **Certificate pinning** to `relay.careerseeker.app` becomes possible now that the hostname
  is settled (gate P1-DOMAIN, answered). Pin with a documented rotation story (spec §7.2).
- **Acceptance:** the live tick test in §3; a pinned-cert connection test; a
  wrong-cert-rejected test.

## 3. The exit proof (device-bound)

With a paired phone (§2.1) and the publisher live (§2.2): run a **demo cycle on the PC**
and watch the phone's Home screen tick the counters in near-real time over WSS; then put
the phone in **airplane mode** and confirm every screen still reads from the Room replica.
Capture both as evidence, the way P1-Evidence.md did.

## 4. Gates opened by this runbook

| Gate | Decision | Recommendation |
| --- | --- | --- |
| **P2-KEYSTORE-FALLBACK** | If a device lacks StrongBox (or hardware-backed keys), pair with a software key, or refuse? | **Pair with an explicit, logged downgrade.** Refusing locks out older/cheaper handsets in the tester pool; the software key is still non-exportable-ish via Keystore and the threat model (§7.1) already treats a rooted phone as lost. Surface the downgrade honestly in Settings. |
| **P2-PIN-ROTATION** | Certificate pinning rotation story before shipping a pin | Pin the relay's leaf **and** a backup key; document the rotation runbook. A pin with no rotation plan bricks the app on cert renewal — worse than no pin. Decide the specifics when §2.6 lands. |
| **P2-REPLICA-CRYPTO** | Room-at-rest: rely on Android platform encryption, or add app-layer (SQLCipher)? | **Platform encryption in P2**, revisit if a threat review asks for more. SQLCipher adds a native `.so` — the 16 KB-page-size compliance work the spec's Tink choice deliberately avoided. The replica is erasable on unpair and re-syncable, lowering the stakes. |

Nothing here blocks starting §2.2–§2.5 (offline, CI-verifiable). §2.1 and §3 want a device.

## 5. Verification ritual

Engine commits: `Verify-Alpha.ps1` offline green at the pinned total, counts + docs moved
together, `DispatcherNoSendHarness` untouched. Android commits:
`./gradlew checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug` +
screen tests. Live pieces: the extended `SyncLiveSmoke` against `relay.careerseeker.app`,
kept out of the hermetic offline suite. Draft PRs, never self-merged, Codex audits.

## 6. Non-goals for P2 (carried from spec §10)

Document **editing** (P3 — read-only here); Pro / entitlement (P4); FCM push (v1.1);
multi-device; L2 gate approvals from the phone. The `doc` payloads exist in the protocol
but P2 only *renders* documents; the `doc_edit` path is P3's invariant-sensitive work.

Estimated agent labor (spec §8): 40–50 h across §2.1–§2.6.
