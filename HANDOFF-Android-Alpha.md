# HANDOFF — P3 Android Alpha (ladder A0→A7)

**Session:** 2026-07-30, unattended, executing `Android_Alpha_roadmap_spec.md` while Brandon
was away. **Branch:** `claude/android-a0-probe`, off `claude/p4-pro` (`d9f95fd`).
**Not pushed. No PR opened.**

> Named `HANDOFF-Android-Alpha.md`, not `HANDOFF.md`, deliberately: `HANDOFF.md` exists on
> `main` with the program-level handoff and is **absent from every code branch** (see
> "Merge hazard" below). Using a distinct name avoids a conflict that would otherwise force a
> merge resolution between two unrelated documents.

**Read in this order:** this file → [`LOG.md`](LOG.md) (executed evidence, milestone by
milestone) → [`AUDIT-REQUEST.md`](AUDIT-REQUEST.md) (every claim with the command that
re-verifies it) → [`BLOCKED.md`](BLOCKED.md) → [`docs/protocol-questions.md`](docs/protocol-questions.md).

---

## 1. Headline

**The spec's central premise was stale.** It records `careerseeker-android` as 404 and
JULY-SUMMARY §S7 says "P3 = the Kotlin app = DOES NOT EXIST YET". The repo **exists**, with 10
branches, 5 PRs, and a CI-green app. Verified by `git ls-remote` before any code was written.

Following the spec's 404 contingency — init a fresh project at `%USERPROFILE%` — would have
orphaned working, tested code. So the ladder ran **against the existing repo**, and each
milestone became "verify what is there, then close what is missing" rather than "build from
scratch".

**Lane A** (full APK) selected and demonstrated.

| | |
| --- | --- |
| Tests | **42 → 99**, 0 failures, all forced to re-execute (`--rerun-tasks`) |
| APK | 13,180,026 bytes, `assembleDebug` + `lintDebug` green under `warningsAsErrors` |
| Vector conformance | **25/25**, including all five entitlement vectors and every invalid one rejected *with its stated reason* |
| Commits | 8, on `claude/android-a0-probe` |
| Bundle | `C:\Users\bkirk\Desktop\careerseeker-android-2026-07-30.bundle` (refreshed every milestone, `git bundle verify` clean) |

## 2. Milestone status

| | Milestone | Status |
| --- | --- | --- |
| A0 | Environment probe + lane selection | **complete** |
| A1 | Scaffold + CI | **complete** (reconciliation, not rebuild) |
| A2 | Envelope crypto + vector conformance | **complete** — 25/25 |
| A3 | Protocol client + persistence | **complete** |
| A4 | Pairing + honest replay | **partial** — logic complete & vector-proven; screen blocked (B-1) |
| A5 | Live end-to-end | **not reached** — blocked engine-side (B-2); level achieved stated honestly |
| A6 | Outcomes + entitlement | **partial** — display + courier complete; controls blocked (B-1), unlock blocked (PQ-A6-1) |
| A7 | Package + handoff | **complete** |

## 3. The five findings that mattered

Each was a case where the app could have shown something untrue.

1. **A delta could be applied before any snapshot** (A3). A delta is the recent *window*; the
   phone pulls from seq 0 and the relay purges on a TTL, so the snapshot can legitimately be
   gone. Two applications would have rendered as the user's entire pipeline — correct counters
   beside a silently wrong list. Now refused and awaited (`snapshotSeen`, Room v2).
2. **Four of five screens showed fixture data with no label** (A4). The provenance banner was
   drawn by `HomeScreen` alone. Opening the app on the Jobs tab showed six fabricated postings
   with nothing saying so. The banner now lives in the shell, so no screen can forget it.
3. **Untrusted text could choose the envelope route** (A2). The receiver found the payload
   `kind` by scanning decrypted bytes for the first `"kind"` substring — and the decrypted body
   is exactly where untrusted job text lives. Now parsed properly.
4. **§3's unknown-top-level-field MUST was unenforced** (A2). No shared vector covers it, so the
   suite passed while the rule was ignored. Now enforced by `EnvelopeJson`, wired into the
   receive path so transport code cannot bypass it.
5. **CI never ran `:app:test`** (A1). 25 Robolectric tests — including the applier's
   audit-derived demo/real boundary — were ungated on every push.

## 4. Deviations from the spec — all deliberate, none silent

| Spec says | Built | Why |
| --- | --- | --- |
| Repo is 404; init fresh at `%USERPROFILE%` | worked in the existing repo | it exists with CI-green code; a fresh scaffold would orphan it |
| SQLDelight | **Room** | already in use with exported schema + audit-derived tests; re-platforming buys nothing observable. Cost: `:app` persistence is a rewrite for iOS. `:core` stays portable |
| Label replay mode "REPLAY" | "Demo data — not a live engine" | same meaning in the consumer register the copy rules require; "REPLAY" is nearer the banned jargon. The rule enforced is *provenance always visible* |
| `entitlement-valid` "unlocks" | maps to `AwaitingEngine` | §4.3.2 makes the engine the verifier. A device-local verdict must not unlock a paid feature (PQ-A2-4) |
| Branches `claude/android-a<N>-<slug>` | followed | the repo's own convention is phase-named; the explicit instruction won. Rename freely |

**One audit-derived test was amended** (A3): `firstRealDeltaWipesDemoDataInsteadOfMergingIntoIt`
→ `firstRealDeltaIsRefusedOutrightRatherThanMergedIntoDemoData`. The Codex invariant — fixture
data must never mix with or masquerade as engine data — is held **more** strictly, since the
delta never lands. The wipe defense is untouched for snapshot and heartbeat, and both those
tests still pass unmodified. **Please review this one specifically** — `AUDIT-REQUEST.md`
C-A3-2.

## 5. Merge hazard Brandon should know about

`main` and the code branches have **diverged into two lineages**:

- `main` — docs only (`HANDOFF.md`, checkpoints, P4/P5 runbooks). **No `app/`, no `core/`.**
- `claude/p2-replica` / `p4-pro` / `p5-store` — the app. **No `HANDOFF.md`.**

Any eventual merge has to reconcile both. Flagged, not touched: merge policy is Brandon's.
Also note `claude/p5-store` modifies three UI files this branch also touches
(`HomeScreen`, `ApplicationsScreen`, `ApplicationDetailScreen`) — expect small conflicts if
P5 lands first. This branch was based on `p4-pro` precisely so it does **not** force P5 to
merge first.

## 6. Decisions waiting on Brandon

1. **Gate `P2-KEYSTORE-FALLBACK`** — blocks the pairing screen *and* outcome marking, since
   both need the Keystore device key. One line: fall back to a software key with a visible,
   logged downgrade, or refuse to pair. (`P2-PIN-ROTATION` and `P2-REPLICA-CRYPTO` touch the
   same surface.)
2. **`entitlement_ack`'s body** (PQ-A6-1) — undefined in §4.3, and it is the only thing that
   may unlock Pro. Suggested minimum `{product_id, acknowledged_at, order_id?}`. Needs the
   engine, a shared vector, and the phone to move in one commit.
3. **PQ-A2-1** — §3.1 caps the *envelope* at 1 MiB; both implementations measure the
   *ciphertext*. They agree with each other and not with the document. Amend the prose or change
   both sides; do not change one.
4. **`applicationId`** — `app.careerseeker.dashboard`, PROVISIONAL, permanent once published.
5. **Whether to keep Room or move to SQLDelight** before iOS is attempted.

## 7. The next three tasks for the Beta phone phase

1. **Answer `P2-KEYSTORE-FALLBACK`, then build the pairing screen.** `PairingSession` is done
   and vector-proven; what remains is the Keystore-backed ECDSA key, CameraX + ML Kit QR scan,
   the confirm-code screen, and `CAMERA` in the manifest with its `<uses-feature>`. This
   unblocks A5 on the phone side.
2. **Wire the transport loop.** `RelayClient` and the applier exist but nothing connects them:
   pull-on-open, apply through `EnvelopeReceiver.receiveWire`, and — importantly — react to
   `ApplyResult.AwaitingSnapshot` by sending `pull_request`, which is currently returned and
   ignored. `:app` needs a Ktor engine dependency (deliberately not added while unused). Then
   the WSS live route, which today is only a URL.
3. **Specify `entitlement_ack` and close the Pro loop.** With the body defined, add the applier
   branch, call `ProState.afterEngineAck`, and surface free-vs-Pro on Overview. Until then the
   app is honestly Free.

Also queued, smaller: a `MigrationTestHelper` test for the v1→v2→v3 migrations (defined,
**not** covered by a test — the code comment says so rather than implying coverage); and the
hardening backlog in the spec §6, which was **not** started because the ladder did not finish
early.

## 8. What I did not touch

The reference repo `ShivaClaw/careerseeker` — read-only throughout, via `git show` only. No
worktree created, no build run there, no commit. A parallel session owns it this week and its
working tree sits on `codex/beta-M0-preflight`.

No push, no PR, no Cloudflare action, no Play Console, no Google/OAuth console, no email, no
purchase, no account creation, no release signing, no secrets read or committed. The production
relay was contacted **once**, as a client, on `/v1/health` — the route §2 defines as returning
no pairing information. It answered `{"ok":true,"protocol":1,"phase":"p1"}`.

Worth a glance: the deployed relay self-reports `"phase":"p1"`. If that tracks the deployment
rather than the protocol, the live Worker predates the P2/P4 work.
