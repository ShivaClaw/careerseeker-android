# Android Program — P0 Runbook (Decisions + Skeletons)

**Phase:** P0 of the Android Dashboard / CareerSeeker Pro program.
**Source spec:** `C:\Users\bkirk\Desktop\Career Seeker\Android-Dashboard-Pro-Spec-2026-07-22.md`
(Fable 5, planning role) — §8 phase plan, §9 gates.
**Written:** 2026-07-22 by Opus 4.8 (executing role), before labor, per the spec's rule
that each phase opens with a runbook reviewed as a draft PR.
**Status:** gates P0-BASE, P0-CIPHER and P0-ACCOUNT answered 2026-07-22 (§7); P0 labor
underway. P0-WORKER and P0-SYNC-COPY remain open but do not block P0 code.

This runbook is the plan of record for P0. It is deliberately concrete: every work item
has the command that performs it and the observation that proves it worked.

Throughout, **main repo** means `ShivaClaw/careerseeker` (public) and **this repo** means
`ShivaClaw/careerseeker-android` (private).

---

## 1. Already done (prerequisites, not phase labor)

| Item | Evidence |
| --- | --- |
| This repo created, **private** | `gh api repos/ShivaClaw/careerseeker-android` → `"private": true`, `default_branch: main`, `size: 0` |
| Main-repo working branch re-based onto the true tip | §2 |
| Offline verifier baseline measured on the corrected base | §6 |
| Repo split decided (strategy here, relay + protocol public) | README, and §3 |

The `gh` CLI is authenticated as `ShivaClaw` with scopes `gist, read:org, repo, workflow`
— sufficient to create this repo and push Actions workflows. It has **no `delete_repo`
scope**, so repo deletion is a manual UI action by Brandon. Private visibility here is
independent of the main repo's visibility; exposing `ShivaClaw/careerseeker` to serve the
alpha ZIP does not affect this repo.

---

## 2. Finding that changed the plan: the branch base was wrong

The main-repo working branch `claude/android-apk-build-setup-90d9d5` was cut from `main`
@ `3fa65f5`. Derived state (`git rev-list --left-right --count`, run 2026-07-22 — derived,
not read from any handoff doc):

| Ref | SHA | Behind `claude/alpha-finish` |
| --- | --- | --- |
| `main` / `origin/main` | `3fa65f5` | 169 |
| `agent/repo-cleanup` | `81d232c` | 156 |
| `agent/audit-cleanup-h1h2h3` | `f3021ec` | 157 |
| `claude/hardening-batch` | `8ba127c` | 163 |
| `origin/claude/codex-audit-pr2-triage-mjdur6` | `1d1a5a4` | 3 |
| **`claude/alpha-finish`** | **`dca6eb5`** | **— (tip)** |

`claude/alpha-finish` contains the triage branch (`git merge-base --is-ancestor` → true)
plus three doc commits, so it is the single true tip.

**Why this mattered.** On the `main` base, none of the files this program depends on
exist: `CLAUDE.md`, `scripts/Verify-Alpha.ps1` (and its `$ExpectedOfflineTotal` pin),
`.github/workflows/ci.yml`, and `tests/DispatcherNoSendHarness` — the harness the spec
names as the guard for the invariant-sensitive P3 commit. Building P0 on `main` would
have produced a relay scaffold that could not be wired into the verifier at all, and the
drift-trap discipline would have been silently unenforceable.

**Action taken:** the branch was hard-reset onto `claude/alpha-finish` @ `dca6eb5`. It had
zero unique commits and had never been pushed, so nothing was lost.

**Consequence:** engine-side PRs for this program target `claude/alpha-finish`, not
`main`, until the alpha merge train lands. See Gate P0-BASE in §7.

---

## 3. Scope of P0

Per spec §8, P0 is **decisions + skeletons**. Exit condition: *"empty-but-green CI in both
worlds; protocol doc reviewed."* Three deliverables, split across the two repos.

### 3.1 `docs/Sync-Protocol.md` v1 + versioned test vectors — **main repo, public**

The protocol is specified once and consumed by both sides, so cross-repo drift dies in CI
the same way doc/verifier drift does today.

**Content:** envelope shape, payload kinds, pairing handshake, replay rules, error
envelopes, version negotiation.

**Reconciliation required — two envelope shapes exist in the docs today:**

| Source | Shape |
| --- | --- |
| Main repo `docs/CareerSeeker-Spec.md` §7.2 (line 388) | `{v, device, seq, ts, key_id, nonce, ciphertext}`, XChaCha20-Poly1305 |
| Android spec §3.3 | `{v, pairing, dir, seq, ts, cipher(payload)}` |

These are the same idea with different field names, and §7.2's is the older, already-
published one. P0 writes the union and marks it v1 explicitly: `pairing` is required
(§7.2 predates the pairing-id concept), `device`/`dir` collapse to one direction field,
and `key_id` + `nonce` stay as separate top-level fields because the receiver needs both
before it can decrypt.

**Cipher choice is settled in the protocol doc, not left to each implementation.** §7.2
says XChaCha20-Poly1305; the Android spec offers either; .NET's
`System.Security.Cryptography` implements **AES-GCM natively but not XChaCha20**.
**Decided (P0-CIPHER, 2026-07-22): AES-256-GCM.** Tink supports it on the Android side, so
neither side takes a new dependency. Because this contradicts already-written spec text,
`docs/CareerSeeker-Spec.md` §7.2 is amended in the same commit as `docs/Sync-Protocol.md`
— two docs disagreeing about the wire format is exactly the failure `CLAUDE.md` was
written to prevent.

**Test vectors:** `docs/sync-vectors/v1/*.json` — each vector is
`{name, key_hex, nonce_hex, aad, plaintext_json, ciphertext_hex, envelope_json}`, with
deliberately-invalid cases (seq regression, truncated tag, wrong key id, unknown payload
kind, version mismatch). Both the C# `SyncHarness` and this repo's `:core` unit tests read
the same files. Vectors are generated by a committed script so they are reproducible, not
hand-typed.

**Acceptance:** vectors parse in both languages and produce byte-identical results;
invalid vectors are rejected with the specified error kind. In P0 only the C# side reads
them — the Kotlin reader lands in P1, and that is the moment the cross-repo check becomes
real.

### 3.2 `relay/` scaffold + CI — **main repo, public**

Cloudflare Worker + Durable Object per pairing. P0 ships the skeleton only: routes
declared and returning `501 Not Implemented` except a health route, DO class defined with
storage schema and TTL constants, Vitest + `@cloudflare/vitest-pool-workers` (miniflare)
wired, `wrangler.jsonc` with no secrets committed.

```bash
npm --prefix relay ci
npm --prefix relay test
npx wrangler deploy --dry-run --config relay/wrangler.jsonc
```

**Acceptance:** `npm test` green with at least one real assertion (health route returns
200, unimplemented routes return 501, DO instantiates); `--dry-run` succeeds so we know
the config is valid **without deploying anything**. No deploy in P0 — the relay goes live
in P1, which is a gated, spend-adjacent action.

**CI:** the relay job is added to the main repo's existing `.github/workflows/ci.yml` as a
second job (`relay`, `runs-on: ubuntu-latest`) rather than a new workflow file, so one red
X means one place to look. The existing `build-and-test` job is untouched.

### 3.3 App scaffold + CI — **this repo, private**

Gradle/Kotlin/Compose skeleton, minSdk 26, plus the pure-Kotlin `:core` module with **no
Android imports** (the KMP-ready protocol/crypto/domain layer, spec §4.2). P0 content is a
skeleton that builds — no screens, no crypto implementation.

```bash
./gradlew :core:test :app:assembleDebug lint
```

**Acceptance:** GitHub Actions green here; `:core` has zero Android dependencies, asserted
by a Gradle check rather than by inspection so it cannot rot; debug APK artifact produced.

**Constraint — CI is the only verifier for this deliverable.** The development machine has
**no JDK and no Android SDK installed** (checked 2026-07-22: `java` not on PATH,
`JAVA_HOME`, `ANDROID_HOME` and `ANDROID_SDK_ROOT` all unset, no SDK at
`%LOCALAPPDATA%\Android\Sdk`). Node 24.18.0 and .NET 8.0.422 are present, so §3.1 and §3.2
*are* locally verifiable. Nothing in this repo can be built locally until a JDK and the
Android SDK (or Android Studio) are installed. Per the evidence standard, no agent may
claim a local Gradle run until that changes — Actions output is the evidence.

`targetSdk` is set to the current Play requirement **verified against live Play
documentation at build time**, not copied from the spec — spec §5.3's "assume 35+" is
explicitly flagged there as needing re-verification, and store policy shifts quarterly.

---

## 4. Explicitly NOT in P0

Stated so the phase cannot quietly widen: no relay deploy; no pairing implementation; no
crypto beyond the vector generator; no Room, no screens; no Play Console registration
(P4 at the latest — but see the pacing trap in §7); no Play Billing; no `src/Sync/` C#
code beyond what `SyncHarness` needs to read vectors; **no changes to any file named in
the main repo's `CLAUDE.md` invariant list.**

---

## 5. What P0 must not break (the invariants, restated)

From the main repo's `CLAUDE.md`. P0 touches none of these, and this list is here so that
a later phase's drift is visible against it:

- Fabrication Gate is never bypassed; `READY` only from `VERIFIED`.
- `Stage.VerifierEntailment` stays pinned to `StrongCloud` and fails closed.
- **The L1 Dispatcher has no send path.** `Dispatcher.SubmitAsync` throws. The phone must
  never acquire one — this is the invariant P3 is closest to, and why the spec routes
  phone edits through the engine's existing compose-only path.
- Local-first: SQLite store, OAuth tokens, provider keys, artifacts stay on the PC. The
  blind relay is the *only* sanctioned server component.
- Gmail scope stays `gmail.compose`. (Pro v1.1's `gmail.readonly` is a separate program
  behind its own gate — not this one.)
- External text is untrusted data, never instruction context. Job descriptions rendered in
  the app are display-only strings.

---

## 6. Verification and the drift trap

**Baseline measured this session** on `claude/alpha-finish` @ `dca6eb5`, before any
change: `scripts\Verify-Alpha.ps1` offline → **327 passed / 0 failed**, exit 0; build
0 warnings / 0 errors. This matches `$ExpectedOfflineTotal` as pinned at
`scripts/Verify-Alpha.ps1:117`.

P0 adds the `SyncHarness` project only if it carries real assertions (vector round-trip).
If it does, then in the **same commit**:

1. bump `$ExpectedOfflineTotal` in `scripts/Verify-Alpha.ps1`,
2. add `SyncHarness` to the offline-harness list in `CLAUDE.md` (Practical notes),
3. update every doc that reports the total — `README.md`, `src/Engine/README.md`,
   `docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`, and any
   `Assert-Contains` expectation in the verifier that names a count.

Per `CLAUDE.md`, doc content and verifier expectations are one unit that changes together.
A P0 PR that bumps the pin without touching the docs is a defect, not a nit.

---

## 7. Gates — Brandon only, blocking

### Answered 2026-07-22 by Brandon

| Gate | Decision | Consequence |
| --- | --- | --- |
| **P0-BASE** *(new — not in the spec)* | **Target `claude/alpha-finish`** | Engine-side PRs for this program base on `dca6eb5`, not `main`. `relay/` and `docs/Sync-Protocol.md` are new files that cannot conflict, so the eventual rebase onto `main` is cheap. P0 proceeds without waiting on the merge train. |
| **P0-CIPHER** *(new — see §3.1)* | **AES-256-GCM** | Deviates from `docs/CareerSeeker-Spec.md` §7.2's stated XChaCha20-Poly1305. Rationale recorded in `docs/Sync-Protocol.md`: native in .NET and supported by Tink, so zero new dependencies on either side, and 96-bit nonces are safe given per-envelope random nonces plus monotonic sequence numbers and our message volume. **§7.2 must be amended in the same change** or the specs contradict each other — that is the drift trap in doc form. |
| **P0-ACCOUNT** | **Organization (D-U-N-S)** | Avoids the 12-testers × 14-days rule entirely, which removes the program's longest lead time from the critical path. **Introduces a dependency the code cannot satisfy:** a registered legal entity with a D-U-N-S number, and Play's business verification. That lead time is unknown until started, and Brandon must start it — registration involves payment details and identity documents. Worth beginning well before P4. Re-verify current requirements against live Play documentation at the time of registration. |

### Still open (do not block P0 code)

| Gate | Decision | Blocks | Recommendation |
| --- | --- | --- | --- |
| **P0-WORKER** | Entitlement Worker vs phone-only license verification | P4 architecture; P0 only records it | Worker (spec §6.3). The relay stays blind either way; the phone-only fallback is weaker against piracy but ships with zero new infra. |
| **P0-SYNC-COPY** | Opt-in sync consent wording | P1 UI, privacy policy | Draft in P0 alongside the protocol doc so app copy, privacy-policy delta, and data-safety answers stay one artifact (spec §7.3). |

**P-MONEY** ($4.99 one-time vs $1.99/mo) stays open per spec §6.4 — the build is identical
either way until store-listing time. Recommendation remains **$4.99 one-time**, because it
is the only option under which every sentence already published on
careerseeker.app/pricing/ stays literally true: "one-time purchase," "the only money we
ever ask you for," and "no subscription exists, so there's nothing to cancel."

---

## 8. Exit criteria for P0

1. This runbook reviewed; the gates in §7 answered (P-MONEY may stay open).
2. `docs/Sync-Protocol.md` v1 merged in the main repo with vectors, envelope
   reconciliation and cipher choice recorded.
3. `relay/` scaffold green in CI; `wrangler deploy --dry-run` clean; **nothing deployed**.
4. This repo's CI green; `:core` proven Android-free by an automated check.
5. `scripts\Verify-Alpha.ps1` green with the pin and the docs consistent (§6).
6. Draft PRs open in both repos. **Never self-merged** — external audit, then Brandon
   merges.
