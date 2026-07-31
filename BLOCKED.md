# BLOCKED

Things this session could not finish, why, and the **smallest** action that unblocks each.

Recorded per the operating protocol ("blocked → record symptom, attempts, smallest human
unblock → advance"). Nothing here is a guess about what *might* be wrong: each entry names a
check that was actually run.

---

## B-1 — Pairing UI cannot be finished: an open gate plus no device

**Milestone:** A4.

**Symptom.** The pairing *logic* is complete and vector-proven (`PairingSession` in `:core`,
7 tests). The pairing *screen* is not built, and building it now would mean pre-empting a
decision that is explicitly Brandon's.

**Why.** Two independent blockers:

1. **Gate `P2-KEYSTORE-FALLBACK` is open.** `docs/P2-Runbook.md` §4 lists three P2 gates as
   Brandon-only, and this one decides what happens when a device has no StrongBox: pair anyway
   with a *logged software-key downgrade*, or refuse. That choice determines what the pairing
   screen shows and what the app promises about key storage — a security-posture claim, not a
   layout detail. Guessing it would mean shipping a promise nobody approved.
   The other two (`P2-PIN-ROTATION`, `P2-REPLICA-CRYPTO`) also touch this surface.
2. **No device and no emulator.** Verified this session, not assumed:
   ```
   $ emulator -list-avds          → (empty)
   $ ls $ANDROID_HOME/system-images → none installed
   $ adb devices                  → List of devices attached   (none)
   ```
   The device signing key is an **Android Keystore** key (§5.4, hardware-backed, StrongBox
   where available). Keystore behaviour is precisely what Robolectric does not model, so a
   pairing screen written now could not be honestly tested here — only compiled.

**Deliberately not worked around.** `PairingSession` takes the device signing key as a
*public point plus a signing function*, so it never touches private key material and needs no
Keystore to be tested. That is why A4's logic could be completed and proven while the screen
could not.

**Smallest unblock:** Brandon answers `P2-KEYSTORE-FALLBACK` (one line: fall back with a
visible, logged downgrade, or refuse to pair). A handset — or `sdkmanager` installing a
system image and an AVD — is then needed to verify, but the gate answer is the part only he
can give.

---

## B-2 — Full live end-to-end could not be reached

**Milestone:** A5.

**Symptom.** "Pair phone/emulator ↔ engine through the relay" was not achieved. What *was*
achieved is recorded honestly in `LOG.md` A5 rather than described as more than it is.

**Attempts / findings:**

1. **Device or emulator** — none available (see B-1's probe output). Installing a system image
   is a multi-GB download plus an AVD create, and would still leave B-1's gate unanswered, so
   the emulator path buys a screen that cannot be honestly labelled anyway.
2. **The spec's own fallback** — "drive `:core`'s client against the engine from a JVM test
   harness" — is blocked *engine-side*, not phone-side. `HANDOFF.md` §4 records that the
   engine's `--sync` flag is honored but **no-ops with an explicit note**, because publishing
   requires a completed pairing, and the desktop `/pair` page that would create one is listed
   as still-to-build. `Program.cs::BuildSyncBridge` is a documented seam with no
   `RelayClient`-backed sink behind it yet. So there is currently no way for an engine on this
   machine to publish a real envelope to the relay for the phone to read.
3. **The reference repo is owned by a parallel session this week.** Building the engine is
   permitted (build/run only, no commits), but its working tree currently sits on
   `codex/beta-M0-preflight`, and creating worktrees or running builds in it risks colliding
   with work in flight. The cost/benefit did not justify it given (2) already blocks the
   outcome.

**What was done instead:** the production relay was probed as a *client* on the one route that
carries no pairing information — `GET /v1/health` — which proves TLS reachability and that this
client speaks to the real service. Result in `LOG.md` A5. No pairing was created, no envelope
pushed, nothing deployed or configured.

**Smallest unblock:** the engine-side desktop `/pair` page plus `BuildSyncBridge` wired to a
real `RelayClient` sink (engine work, and the parallel session's territory), **then** a handset.
Until the engine can publish, the phone has nothing to receive, and no amount of phone-side work
changes that.

---

## B-3 — CI's vendored-vector drift check is unverifiable locally

**Milestone:** A1/A2 (minor).

**Symptom.** The workflow step that re-fetches the pinned upstream vectors and diffs them uses
the GitHub contents API with `${{ github.token }}`. It cannot run on this machine.

**Mitigation, not a workaround:** the same comparison was performed locally against the local
clone of the reference repo, **blob-to-blob** (which is what CI's Linux checkout compares) —
all 26 vendored vectors are byte-identical to pin `679a317`. The command is in
`AUDIT-REQUEST.md` C-A2-1.

**Smallest unblock:** push the branch; CI runs the step. This is expected to pass, and is
recorded as *expected*, not as *verified*.
