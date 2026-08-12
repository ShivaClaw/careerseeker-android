# LOG — Android Alpha (P3) ladder A0→A7

Execution log for the unattended session that runs the `Android_Alpha_roadmap_spec.md`
milestone ladder while Brandon is away (2026-07-30 → ~2026-08-02).

**Evidence standard (inherited):** "ran it and saw it" or it did not happen. Every claim
below is followed by output produced *in this session*. Where a result was inherited from a
previous session's build cache, it is labelled as such and **not** counted as evidence.

Re-verification commands for everything here live in [`AUDIT-REQUEST.md`](AUDIT-REQUEST.md).

---

## A0 — Environment probe + lane selection · 2026-07-30 · **COMPLETE**

### A0.1 Ground truth: the spec's central premise was stale

The roadmap spec states `target_repo: github.com/ShivaClaw/careerseeker-android # 404 as of
2026-07-30`, and JULY-SUMMARY §S7 states "**P3 = the Kotlin app itself = DOES NOT EXIST YET**
(repo 404 as of 2026-07-30)". Both are **wrong as of this session**. Verified first, before
any code, per the spec's own instruction to verify ground truth:

```
$ git ls-remote https://github.com/ShivaClaw/careerseeker-android.git
71fc7e2aaa5088b5f50731f9e5cf4ec8d480f13a        HEAD
59051a4f3f9d74d64fe5988cef33aa9fd94787de        refs/heads/claude/p0-scaffold
3477b41a1a1e28a4abf371ece29a7aecee31de72        refs/heads/claude/p1-pairing
ec0f73e3d7ed13fb359e16fa1e6479255f6b9f20        refs/heads/claude/p1-runbook
d9f95fd76d39a1ba8fdfe582486172c0e53ab9c0        refs/heads/claude/p2-replica
2f4cf87dba293c39077ac00da93a947f14b3dbe4        refs/heads/claude/p2-runbook
d9f95fd76d39a1ba8fdfe582486172c0e53ab9c0        refs/heads/claude/p4-pro
bb7f4d0a405f1f52d5f951a2150b4ba85fa7215c        refs/heads/claude/p5-store
e9ce010e39400394edf29acd116a43ef3515fe73        refs/heads/claude/todos-pq1-pricing
71fc7e2aaa5088b5f50731f9e5cf4ec8d480f13a        refs/heads/main
(+ refs/pull/1..5)
```

The repo exists, is private, has **5 PRs** and a substantial app already built by prior
sessions. The session's working directory `C:\Users\bkirk\Documents\careerseeker-android`
*is* a clone of it.

**Consequence for the mission.** The ladder is not greenfield. A1 (scaffold + CI), A2
(vector conformance), and much of A3/A4 already exist as real, tested code. Re-scaffolding a
fresh project at `%USERPROFILE%\careerseeker-android` — which the spec's contingency
describes for the 404 case — would have **orphaned working, CI-green code**. Decision
recorded in A0.6.

The reference repo matched the summary exactly, so S3 is trustworthy for `careerseeker`:

```
$ git ls-remote https://github.com/ShivaClaw/careerseeker.git
14a7dfec374cda410aa28b13c456d695f38e3507        refs/heads/main
d5bdb9dff43655b9ab0208c3032076c73013d33a        refs/heads/claude/p4-entitlement
7158202...  refs/heads/claude/p2-publisher     6c46545...  refs/heads/claude/p1-sync
940c4e1...  refs/heads/claude/android-apk-build-setup-90d9d5
40bc9a7...  refs/heads/fix/engine-actually-runs
```

### A0.2 Toolchain probe

| Probe | Result |
| --- | --- |
| JDK | `openjdk 21.0.10` at `C:\Program Files\Android\Android Studio\jbr` (runs Gradle; modules pin toolchain 17) |
| `ANDROID_HOME` | unset in env; SDK present at `%LOCALAPPDATA%\Android\Sdk` — exported per-command |
| SDK platforms | `android-36.1`, `android-37.0` (project needs `compileSdk`/`targetSdk` 37 — **satisfied**) |
| Build-tools | `36.0.0` |
| Gradle | 9.6.1 via committed, sha256-pinned wrapper |
| Disk free | 826,742,239,232 bytes (~770 GiB) |
| Network | reachable: both GitHub remotes cloned/queried this session |

### A0.3 Normative contract read in full

`docs/Sync-Protocol.md` @ `claude/p4-entitlement` (`d5bdb9d`), 474 lines, read end-to-end via
`git show` against the **local** reference clone — zero writes to that repo, no worktree
created, because a parallel session owns its working tree this week (it currently sits on
`codex/beta-M0-preflight`).

Contract points that drive the remaining ladder: §3 unknown top-level fields MUST be
rejected; §3.1 the **envelope** (not the ciphertext) is capped at 1 MiB; §4.3.1 the
application summary now carries a nullable `outcome`; §4.3.2 entitlement is
`{original_json, signature}` with standard-base64 signature and `purchaseState == 0` in the
**raw** JSON; §5.3 `key_id` mismatch rejected **before** decryption; §6.1 the receiver
persists its high-water seq across restarts; §10 rejecting for the *wrong reason* is a
failure.

### A0.4 Vector drift quantified

Upstream `claude/p4-entitlement` ships **25** vectors. The phone has vendored **20**, pinned
at `fff4bce9790788217d72be882f776b882993d640` (a P1-era commit) in
`core/src/test/resources/sync-vectors/VECTORS.lock`.

Missing on the phone — exactly the P4 acceptance material:
`entitlement-valid`, `entitlement-not-purchased`, `entitlement-tampered-json`,
`entitlement-wrong-package`, `entitlement-wrong-product`.

This is the single largest gap between "what the phone proves" and "what the contract now
says". It is A2 + A6 work.

### A0.5 Baseline: what actually passes, executed in this session

First invocation reported `BUILD SUCCESSFUL` with `55 up-to-date` — i.e. it **reused a prior
session's cache**. Under the evidence standard that is *not* a result I may cite, so every
test was forced to re-execute:

```
$ ./gradlew --no-daemon :core:test :app:test --rerun-tasks
BUILD SUCCESSFUL in 1m 36s
35 actionable tasks: 35 executed
```

Parsed from the JUnit XML written at 20:44–20:45 on 2026-07-30:

| Suite | Tests | Failures | Errors |
| --- | --- | --- | --- |
| `core…ProtocolTest` | 11 | 0 | 0 |
| `core…ProtocolVectorsTest` | 6 | 0 | 0 |
| `dashboard.replica.DemoFixtureTest` | 3 | 0 | 0 |
| `dashboard.replica.EnvelopeApplierTest` | 16 | 0 | 0 |
| `dashboard.ui.ScreensFromFixtureTest` | 6 | 0 | 0 |
| **TOTAL** | **42** | **0** | **0** |

`checkCoreIsAndroidFree` also ran and printed `:core is Android-free.`

Note the prior handoff cites "17/17 Robolectric" for `:app`; the actual current count is 25
(3 + 16 + 6) because later commits added evidence-applier tests. I report what I ran.

### A0.6 Decisions taken (reversible ones preferred, per the mission brief)

1. **Work inside the existing repo, not a fresh scaffold.** Re-scaffolding would duplicate
   and strand CI-green code. Reversible: nothing is deleted; new work is additive on a new
   branch.
2. **Base branch = `claude/p4-pro` (`d9f95fd`, identical to `claude/p2-replica`).** It is the
   branch the program already designated for the P4 phone half, and it is the common ancestor
   of the `p5-store` line — so this work does not force P5 to merge first. Rejected
   alternatives: basing on `p5-store` (would invert merge order and entangle un-audited store
   staging), basing on `main` (has **no app code** — see A0.7).
3. **Branch naming follows the spec** (`claude/android-a<N>-<slug>`) even though the repo's
   own convention is phase-named branches. Explicit instruction wins; branch names are
   trivially renameable if Brandon prefers the house style. First branch:
   `claude/android-a0-probe`.
4. **Lane A (full).** SDK, platform 37, and build-tools are present and an APK assembles
   locally. Evidence in A0.8.
5. **No pushes yet.** The repo is private and prior sessions pushed branches without opening
   PRs, by house rule ("Brandon decides when to open draft PRs"). Bundles go to the Desktop at
   every milestone so nothing is trapped in a working tree.
6. **`docs/Sync-Protocol.md` is never edited**, and no worktree was created in the reference
   repo — reads only, via `git show`.

### A0.7 Findings recorded for later milestones (not fixed in A0)

| # | Finding | Where it lands |
| --- | --- | --- |
| F-1 | **CI never runs `:app:test`.** `.github/workflows/ci.yml` runs `checkCoreIsAndroidFree`, the vector-drift check, `:core:test`, `assembleDebug`, `lintDebug`, and the analytics-classpath check — but not the 25 Robolectric tests. They are ungated. | A1 |
| F-2 | Vendored vectors are 5 behind upstream (A0.4). | A2 |
| F-3 | **`main` has diverged from the code branches.** `main` carries docs-only commits (HANDOFF, checkpoints, P4/P5 runbooks) that are absent from every code branch; the code branches carry the app, absent from `main`. Any future merge must reconcile both lineages. Flagged, not touched — merge policy is Brandon's. | Handoff |
| F-4 | README still says "**P0 — scaffold.** No product features yet"; the repo has a Room replica and five screens. Also missing the spec-mandated PROVISIONAL note on `applicationId` (the id itself is already correct: `app.careerseeker.dashboard`). | A1 |
| F-5 | `EnvelopeApplier.appOf()` does not parse the nullable `outcome` field that protocol §4.3.1 now defines; the replica has no outcome projection. | A6 |
| F-6 | `:core` has **no strict envelope parser**. §3 requires rejecting unknown top-level fields; `ReceivedEnvelope` is constructed field-by-field by callers, so an unknown field is silently dropped rather than rejected. No vector covers this, but it is a documented MUST. | A2 |
| F-7 | `EnvelopeReceiver` checks `ciphertext.size > MAX_ENVELOPE_BYTES`, but §3.1 caps the **whole envelope**. A ciphertext just under 1 MiB plus headers exceeds the limit and is accepted. | A2 |
| F-8 | `EnvelopeReceiver.kindOf()` hand-scans plaintext for the first `"kind"` substring instead of parsing JSON. Untrusted carried text containing `"kind":"…"` ahead of the real field would misroute. Untrusted job text is exactly what this envelope carries. | A2 |
| F-9 | `DemoFixture` seeds `auditOk = true` — a fabricated audit verdict. It is labelled demo by `StatusBanner` on every screen and is wiped by the first real payload, so it is honest today; worth re-checking when the Evidence screen gains prominence. | A4 (review) |

F-6/F-7/F-8 are hardening, not vector failures: all 25 upstream vectors' stated behaviours
are unaffected. They are recorded because §10 is explicit that passing the vectors is the
floor, not the ceiling.

### A0.8 Lane A confirmation — APK built in this session

The first assemble was also cache-inherited, so it too was forced:

```
$ ./gradlew --no-daemon :app:assembleDebug :app:lintDebug --rerun-tasks
BUILD SUCCESSFUL in 1m 32s
49 actionable tasks: 49 executed
```

`:app:lintDebug` passes with `warningsAsErrors = true` and `abortOnError = true`.

Artifact produced at `app/build/outputs/apk/debug/app-debug.apk`:

| | |
| --- | --- |
| Size | 12,147,666 bytes |
| Built | 2026-07-30 20:47:03 local |
| sha256 | `7CF785A95511DBFBFA08E72567FEB9588BFA3C6C0CF78EDFFB9BC0EC4B542861` |

The only build noise is `Unable to strip … libandroidx.graphics.path.so`, a benign
debug-packaging notice from AGP, not a project defect.

**Lane A is selected and demonstrated.** No `BLOCKED.md` is needed for A0.

### A0.9 Prohibitions honoured in A0 (see also A1 below)

No pushes anywhere; no PRs; no edit to the reference repo (reads via `git show` only, no
worktree created); no Cloudflare/Play/Google-console/email/purchase/account actions; the
relay was not contacted at all in A0; no secrets read, printed, or committed.

Milestone artifacts: commit `dd64160`; bundle
`C:\Users\bkirk\Desktop\careerseeker-android-2026-07-30.bundle` (604,773 bytes,
`git bundle verify` → "The bundle records a complete history").

---

## A1 — Scaffold + CI reconciliation · 2026-07-30 · **COMPLETE**

The spec's A1 ("KMP scaffold, version catalogs, `.gitignore`, GitHub Actions") was already
satisfied by prior sessions, so A1 became a *reconciliation*: verify what exists, and close
the gaps A0 found rather than rebuild.

### A1.1 Verified as already present — no change needed

| A1 requirement | State |
| --- | --- |
| `:core` module with zero Android deps | present; enforced by the `checkCoreIsAndroidFree` Gradle task, which ran and printed `:core is Android-free.` |
| Gradle version catalog | `gradle/libs.versions.toml`, all versions dated and justified in comments |
| `.gitignore` covers keystores / `local.properties` / captures | present (`*.keystore`, `*.jks`, `local.properties`, `captures/`) — added `.kotlin/`, which was showing up untracked |
| GitHub Actions workflow | `.github/workflows/ci.yml`, ubuntu-latest, JDK 17, wrapper validation |
| `applicationId` = `app.careerseeker.dashboard` | already exactly the spec's value (`app/build.gradle.kts:18`) |

Two CI checks in this repo are better than the spec asked for and are called out so they are
not lost: the vendored vectors are re-fetched from the pinned upstream commit and diffed (so
cross-repo protocol drift fails CI), and the "no analytics" promise is checked against the
**resolved release classpath** rather than by grepping build files.

### A1.2 F-1 closed — CI now runs `:app:test`

The workflow gated `:core:test`, `assembleDebug`, and `lintDebug`, but never `:app:test`.
The 25 Robolectric tests — including the applier's demo/real boundary, which exists because
of a Codex audit finding — were ungated on every push. Added as a step between `:core:test`
and the assemble (`.github/workflows/ci.yml:95-96`).

Locally executed proof that the newly-gated step passes (from A0.5, forced re-run):
`:app:test` = 25 tests, 0 failures.

CI's analytics check was also run locally to confirm the step I did not touch still holds:

```
$ ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath > deps.txt
$ grep -niE 'firebase|crashlytics|gms:play-services-ads|appsflyer|com\.adjust|amplitude|mixpanel|segment\.analytics' deps.txt
OK: no analytics or tracking SDKs on the release classpath.   (710 resolved lines scanned)
```

The vendored-vector drift step was **not** run locally — it needs the GitHub contents API and
is CI-only. It will run on push. Recorded here rather than implied.

### A1.3 F-4 closed — README no longer under-claims

The README said "**P0 — scaffold.** No product features yet" while the repo holds a Room
replica, an envelope applier, and five screens. Under this project's own rule that a
document and the thing it describes move together, an under-claiming README is the same
class of defect as an over-claiming one — it just fails safe. Rewritten to state what is
built *and what is not* (pairing UI, live transport, outcomes, entitlement).

Added the spec-mandated **PROVISIONAL `applicationId`** note: the id is permanent once
published on Play, so Brandon confirms it before any upload. Also added `--rerun-tasks`
guidance to the build section, for the same reason A0.5 needed it.

### A1.4 Exit criterion

The spec's A1 exit is "`:core` tests green on a hello-world test". Substantially exceeded and
already evidenced in A0.5: `:core` = 17 tests, 0 failures, forced execution.

### A1.5 Prohibitions

Same as A0.9. No push, no PR, no reference-repo write, no network action beyond Gradle
dependency resolution.

Milestone artifacts: commits `46064b8`, `0aa4c16`; bundle refreshed (609,293 bytes).

---

## A2 — Vector conformance · 2026-07-30 · **COMPLETE**

The milestone the mission calls the crown jewel: *"a dashboard that can be fooled is worse
than no dashboard"*, and "any mostly-passing state is failing".

### A2.1 Re-vendored to the current contract

The phone was pinned at `fff4bce` (P1-era, 20 vectors) while the contract had moved to 25.
Exactly one upstream commit touched vectors in between:

```
$ git log --oneline fff4bce..claude/p4-entitlement -- docs/sync-vectors
679a317 P4 §2.2: entitlement body {voucher}->{original_json,signature} + five Play-signed vectors

$ git diff --name-status fff4bce claude/p4-entitlement -- docs/sync-vectors
M  docs/sync-vectors/generate.mjs
A  docs/sync-vectors/v1/entitlement-not-purchased.json
A  docs/sync-vectors/v1/entitlement-tampered-json.json
A  docs/sync-vectors/v1/entitlement-valid.json
A  docs/sync-vectors/v1/entitlement-wrong-package.json
A  docs/sync-vectors/v1/entitlement-wrong-product.json
M  docs/sync-vectors/v1/index.json
```

No pre-existing vector changed, so re-vendoring was purely additive. `VECTORS.lock` now pins
`679a3175590dcd021b21c85af9daf12114e131fd` — the precise commit that last modified the
vectors, and an ancestor of `p4-entitlement`, so the pin survives that branch being merged or
deleted.

**A false alarm worth recording**, because it will bite the next person: comparing the
vendored files byte-for-byte against `git show` output reported all 20 pre-existing vectors as
drifted. They had not drifted. `core.autocrlf=true` on this machine means the working tree
holds CRLF while git blobs (and upstream, and CI's Linux checkout) hold LF. The correct
comparison is blob-to-blob:

```
OK: all 26 vendored vector blobs are byte-identical to upstream 679a317
```

### A2.2 Conformance suite extended — 30 `:core` tests, 0 failures

```
$ ./gradlew --no-daemon :core:test --rerun-tasks
BUILD SUCCESSFUL in 25s
5 actionable tasks: 5 executed
```

| Suite | Tests | Fail | Err |
| --- | --- | --- | --- |
| `ProtocolTest` | 11 | 0 | 0 |
| `ProtocolVectorsTest` | 6 | 0 | 0 |
| `EntitlementVectorsTest` *(new)* | 5 | 0 | 0 |
| `EnvelopeJsonTest` *(new)* | 8 | 0 | 0 |
| **TOTAL** | **30** | **0** | **0** |

The entitlement vectors are asserted at **two independent layers**, because they fail for
different reasons:

- **Envelope layer** — all five must be *accepted*. A bad purchase is still a well-formed,
  device-signed `p2e` envelope; rejecting it would mean the phone could not deliver a receipt
  it is supposed to forward, and would hide the real verdict behind a transport error.
- **Payload layer** — each must classify with the **exact** reason its own vector names
  (`accepted`, `signature_invalid`, `wrong_product`, `wrong_package`, `not_purchased`). The
  expectation is read out of the vector rather than hardcoded, so a renamed verdict cannot
  quietly pass.

Two assertions go beyond the vectors: the verifier reads its package/product **configuration**
(the real values only exist once the Play app is created), and a semantically-identical
re-encoding of `original_json` must **fail** — pinning §4.3.2's "verify over the exact bytes,
never re-serialise" as a test rather than a comment.

### A2.3 F-8 closed — untrusted text can no longer choose the route

`EnvelopeReceiver.kindOf` scanned the decrypted bytes for the first `"kind"` substring. The
decrypted body is exactly where untrusted job and recruiter text lives (§8.6), so carried text
containing `"kind":"snapshot"` ahead of the real field would have selected the route. It now
parses the JSON and returns null on malformed input, which the caller maps to `unknown_kind` —
matching the engine's `JsonDocument.Parse` behaviour so both sides classify garbage identically.

This required promoting `kotlinx-serialization-json` from a test-only to an implementation
dependency of `:core`, which retires that module's "zero dependencies" line. The trade is
recorded in `core/build.gradle.kts`: the library is **already** in the shipped APK via `:app`,
so this adds no artifact and no attack surface; it is pure Kotlin and multiplatform, so the
Android-free rule and a future iOS target are unaffected; and the posture that actually
matters — no third-party **crypto** — is untouched, since crypto remains JCA-only.

### A2.4 F-6 closed — §3's unknown-field rule is enforced

> "Other unknown top-level fields MUST be rejected, not ignored. A permissive parser here is
> how a future version's field silently becomes an injection point."

Nothing in `:core` enforced this: envelopes were assembled field-by-field by callers, so an
extra field was simply dropped. New `EnvelopeJson.parse` rejects unknown top-level fields,
missing required fields, wrong-typed fields (notably `"seq":"1"`, which a lenient parser
coerces into an attacker-chosen sequence number), a non-string `sig`, and malformed JSON —
all before any crypto runs.

It is wired into the actual receive path as `EnvelopeReceiver.receiveWire(...)` rather than
left as an unused utility, so transport code in A3 cannot accidentally bypass it.

### A2.5 F-7 deliberately **not** fixed — and why that is the correct call

§3.1 caps the *envelope* at 1 MiB; the phone checks the decoded *ciphertext*. That is a real
divergence from the prose — but reading the engine first showed it does the identical thing:

```csharp
// src/Sync/EnvelopeReceiver.cs
if (ciphertext.Length > Protocol.MaxEnvelopeBytes) return Reject(SyncError.TooLarge);
```

Changing only the phone would create a window where the engine accepts an envelope the phone
rejects — breaking exactly the cross-implementation agreement the shared vectors exist to
guarantee. Being unilaterally "more correct" than the engine is a field bug, not a fix.
Recorded as **PQ-A2-1** in `docs/protocol-questions.md` for a both-sides decision.

New file `docs/protocol-questions.md` records four items: PQ-A2-1 (size cap), PQ-A2-2 (§7.2
has no error code for a structurally malformed envelope; both sides fold it into
`decrypt_failed`), PQ-A2-3 (no shared vector covers the unknown-field MUST), and PQ-A2-4 (the
courier/verifier boundary — see below).

### A2.6 The entitlement boundary, stated before A6 can erode it

§4.3.2 makes the phone a **courier**: it forwards `{original_json, signature}`; the *engine*
verifies and answers `entitlement_ack`. The roadmap spec's A6 wording — "`entitlement-valid`
unlocks" — read literally would put the unlock decision on the device that benefits from
getting it wrong.

`EntitlementVerifier` therefore classifies but never grants: a local `ACCEPTED` verdict means
only *"worth sending to the engine"*, and the KDoc says so at length. Pro is unlocked by an
`entitlement_ack` envelope and by nothing else. This is the phone-side expression of the
mission's one rule — the engine cannot fabricate a skill; the phone must not be able to
fabricate a status.

### A2.7 Whole-project verification after the dependency change

Adding a dependency to `:core` could have broken the Android-free rule or the app build, so
the full set was re-executed rather than assumed:

```
$ ./gradlew --no-daemon checkCoreIsAndroidFree :app:test :app:assembleDebug :app:lintDebug --rerun-tasks
:core is Android-free.
BUILD SUCCESSFUL in 1m 47s
59 actionable tasks: 59 executed
```

| Suite | Tests | Fail | Err |
| --- | --- | --- | --- |
| `core…ProtocolTest` | 11 | 0 | 0 |
| `core…ProtocolVectorsTest` | 6 | 0 | 0 |
| `core…EntitlementVectorsTest` | 5 | 0 | 0 |
| `core…EnvelopeJsonTest` | 8 | 0 | 0 |
| `dashboard.replica.DemoFixtureTest` | 3 | 0 | 0 |
| `dashboard.replica.EnvelopeApplierTest` | 16 | 0 | 0 |
| `dashboard.ui.ScreensFromFixtureTest` | 6 | 0 | 0 |
| **TOTAL** | **55** | **0** | **0** |

APK rebuilt: 12,164,050 bytes (up 16,384 from A0's 12,147,666 — one dex page, consistent with
`:core` gaining code that was already on the app classpath). `lintDebug` green under
`warningsAsErrors`.

Test count across the ladder so far: **42 → 55** (+13, all in `:core`).

### A2.8 Prohibitions (A3 entry continues below)

No push, no PR, no reference-repo write (the engine's `EnvelopeReceiver.cs` and the upstream
vectors were read via `git show`), no relay contact, no secrets. The vector files carry
published test keys and are marked as such upstream; they are test resources and reach no
build — `core/src/test/resources` is not on the app's runtime classpath.

Milestone artifact: commit `7bc5667`; bundle refreshed (643,537 bytes).

---

## A3 — Protocol client + persistence · 2026-07-30 · **COMPLETE**

### A3.1 The honesty bug this milestone was really about

A3's spec text asks for a reducer covering "the P2 rule's client side (a missing first
snapshot is awaited, never faked from deltas)". The applier had **no such rule**, and the
16-test applier suite did not cover it.

Why it matters concretely: `delta` carries the *recent window*, not the pipeline (§4.3.1).
The phone pulls from `since=0` on pairing, and the relay purges on a TTL (≤30 days, §2) — so
the snapshot can legitimately be **gone** by the time the phone arrives. The applier would
then have upserted the delta's two applications into an empty replica, cleared `demoMode`, and
the Applications screen would have rendered *two* applications as the user's entire pipeline
when the engine had forty. Counters would have been right and the list silently wrong.

That is the phone-side twin of the engine's own postmortem — "running" over counters nothing
could increment (JULY-SUMMARY §S5) — and exactly what the mission means by *make the phone
unable to fabricate a status*.

**Fix.** `SyncStateRow.snapshotSeen` (persisted, latching), Room schema **v1 → v2** with a real
migration. The applier refuses a `delta` until a snapshot has been applied and returns a new
`ApplyResult.AwaitingSnapshot`, whose contract is "ask the engine to re-publish
(`pull_request`)", not "error".

It cannot be inferred from `highestAppliedE2pSeq`, because a *heartbeat* advances that mark
while carrying no rows at all — `aHeartbeatDoesNotCountAsASnapshot` pins precisely that.

The migration defaults existing replicas to `0` (no snapshot seen), which is the safe
direction: deltas are refused until the engine sends a snapshot, which it does on start and on
pairing. Defaulting to `1` would assert a snapshot the replica may never have received — the
fabrication the column exists to prevent.

### A3.2 An audit-derived test was amended — stated plainly, not buried

Two existing tests failed against the new rule. One needed a genuine amendment:
`firstRealDeltaWipesDemoDataInsteadOfMergingIntoIt`, which came from the **Codex audit
finding of 2026-07-24** and is the subject of this branch's tip commit ("the first real payload
wipes demo data, never merges it"). Changing an audit-derived test deserves scrutiny, so:

- **The invariant it protects** — *fixture data must never mix with, or masquerade as, engine
  data* — is now held **more** strictly. The delta is refused outright, so it cannot mix with
  anything.
- **What changed is the mechanism.** Demo rows survive a refused delta carrying their honest
  "Demo data — not a live engine" label, instead of being replaced by a partial window
  presented as real.
- **The wipe defense itself is untouched** for the kinds that legitimately arrive first —
  `firstRealSnapshotAlsoClearsDemoEvidenceAndDocuments` and
  `firstRealHeartbeatWipesDemoRowsRatherThanRelabelingThem` still pass unmodified.
- A new test, `aSnapshotAfterARefusedDeltaStillWipesTheDemoFixture`, proves refusing the delta
  does not strand the replica in demo mode.

The test was renamed to describe what it now proves, and carries an in-file note explaining the
amendment so a reader does not have to reconstruct this from git history.

The second failure, `malformedPayloadInDemoModeChangesNothing`, was **not** a real conflict —
it was my ordering mistake. I had put the snapshot gate before payload validation, so a
malformed delta reported `AwaitingSnapshot` instead of `Malformed`. Rejecting for the wrong
reason is the §10 failure mode, so the gate moved to *after* validation and *before* the demo
wipe. That test now passes unmodified, as it should have all along.

### A3.3 `RelayClient` — Ktor, engine-agnostic, never dials production in tests

New `core/.../RelayClient.kt` covering §2's transport table: `create`, `pair`, `push`,
`pull`, `unpair`, `health`, and the `wss://` live URL.

Design points that are load-bearing rather than stylistic:

- **The relay stays a dumb pipe.** `pull` returns envelope wire text **unparsed**;
  `EnvelopeReceiver` owns every trust decision. A transport that helpfully parsed payloads
  would become a second place where trust decisions live, and the second place is the one that
  gets them wrong.
- **TLS is enforced at construction**, not per request — §2 says clients MUST reject cleartext,
  and a per-request check is one a retry path can skip. `liveUrl()` derives `wss://` from the
  same validated base, so one check covers both schemes.
- **4xx is a decision and is never retried**; only 5xx and transport exceptions back off
  (250 ms × 3, capped at 8 s). Retrying "not ever" burns battery and loads a relay that already
  answered.
- **The bearer never reaches `toString()`** — a token in a crash report is the cheap failure to
  prevent.

`:core` takes `ktor-client-core` only (no engine), so it stays Android-free and the whole
protocol is testable against a `MockEngine`. **No test opens a socket**: the relay is
production infrastructure and this session is a client of it, not a load generator. Ktor
**3.2.0** was read off Maven Central this session rather than assumed, per the repo's rule that
versions are verified against the artifact repositories.

### A3.4 Deviation from the spec: Room, not SQLDelight

The roadmap spec names **SQLDelight** for persistence (§2). This repo already uses **Room**,
with an exported schema, a KSP pipeline, and 16 applier tests running against real SQLite under
Robolectric.

Kept Room. Re-platforming working, tested persistence to satisfy a spec written on the
assumption that the repo did not exist would burn the milestone's budget and throw away the
audit-derived demo/real boundary tests, in exchange for nothing a user could observe. This is
the same call as A0.6's: the ladder is not greenfield.

The cost is real and worth stating: SQLDelight is multiplatform and Room is not, so the day an
iOS target is attempted, `:app`'s persistence is a rewrite. `:core` — which holds the protocol,
the crypto, the receiver, and now the relay client — stays fully portable, so the rewrite is
confined to the layer that was always going to be platform-specific. Recorded for Brandon as a
deviation rather than decided permanently.

### A3.5 Verification

```
$ ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test --rerun-tasks
:core is Android-free.
BUILD SUCCESSFUL in 2m 19s
36 actionable tasks: 36 executed
```

| Suite | Tests | Fail | Err |
| --- | --- | --- | --- |
| `core…ProtocolTest` | 11 | 0 | 0 |
| `core…ProtocolVectorsTest` | 6 | 0 | 0 |
| `core…EntitlementVectorsTest` | 5 | 0 | 0 |
| `core…EnvelopeJsonTest` | 8 | 0 | 0 |
| `core…RelayClientTest` *(new)* | 14 | 0 | 0 |
| `dashboard.replica.DemoFixtureTest` | 3 | 0 | 0 |
| `dashboard.replica.EnvelopeApplierTest` | 21 (was 16) | 0 | 0 |
| `dashboard.ui.ScreensFromFixtureTest` | 6 | 0 | 0 |
| **TOTAL** | **74** | **0** | **0** |

Ladder progression: **42 → 55 → 74**. Room schema `2.json` exported alongside `1.json`.

### A3.6 Known gaps in A3, stated rather than left to be discovered

1. **The v1→v2 migration is not covered by a test.** No `MigrationTestHelper` case opens a v1
   database and migrates it. It is a one-column `ALTER TABLE` and Room validates the result
   against the exported `2.json` on open, so the risk is low — but low risk is not verified,
   and this is the schema's first migration. Adding `androidx.room:room-testing` and one test
   is the fix; it is on the hardening backlog rather than done, and the code comment says so
   too rather than implying coverage that does not exist.
2. **`RelayClient` is not yet wired into `:app`.** `:core` deliberately takes no HTTP engine, so
   `:app` must supply one (Ktor's OkHttp or CIO engine) when pairing is wired in A4/A5. The
   dependency is not added yet, because an unused engine on the release classpath is exactly
   the kind of thing the "what actually ships" CI check exists to catch.
3. **The WSS live route is a URL, not a socket.** `liveUrl()` returns the `wss://` endpoint;
   nothing opens or manages that connection yet. Pull-on-open plus the reducer is the working
   path; live fan-out is A5's if a device materialises.
4. **`AwaitingSnapshot` has no caller yet.** The applier returns it correctly and the tests pin
   the behaviour, but nothing currently reacts by sending `pull_request`. That wiring belongs
   with the transport, in A4/A5.

### A3.7 Prohibitions

No push, no PR, no reference-repo write. **The production relay was not contacted**: every
`RelayClient` test runs against a Ktor `MockEngine`, and no test in the repo opens a socket.
Ktor 3.2.0 was confirmed against Maven Central (a package-repository query, which the
live-network policy permits) rather than assumed.

Milestone artifact: commit `9992718`; bundle refreshed (661,778 bytes).

---

## A4 — Pairing logic + honest replay labelling · 2026-07-30 · **PARTIAL** (screen blocked, see B-1)

### A4.1 The honesty bug: four screens showed fixture data with no label

The honest-UI rule is that replay/demo data is labelled **on every screen**. It was labelled on
**one**. `StatusBanner` was drawn by `HomeScreen` alone, so Applications, Jobs, Evidence, and
the Application-detail overlay rendered demo rows with nothing anywhere on screen saying they
were demo. A user who opened the app on the Jobs tab saw six fabricated postings presented as
their pipeline.

Verified before fixing, not assumed:

```
ApplicationDetailScreen.kt   StatusBanner refs=0
ApplicationsScreen.kt        StatusBanner refs=0
EvidenceScreen.kt            StatusBanner refs=0
JobsScreen.kt                StatusBanner refs=0
HomeScreen.kt                StatusBanner refs=2
```

**Fix.** The banner moved out of `HomeScreen` and into the `Scaffold`'s `topBar` in
`DashboardApp`. That is the structural version of the rule rather than the polite version: no
screen draws the banner, so no screen can forget it, including screens added later.

Two new tests walk the actual navigation surface — `theProvenanceBannerIsShownOnEveryTab`
clicks through all four tabs and `theBannerFollowsIntoTheApplicationDetailOverlay` opens the
overlay, which is exactly the kind of screen a per-screen banner gets forgotten on.

On wording: the spec asks for the label "REPLAY". The existing string is
**"Demo data — not a live engine"**, which is kept — it says the same thing in the consumer
register the copy rules require, and "REPLAY" is closer to the internal jargon those rules ban.
The rule being enforced is *provenance is always visible*, and it now is.

### A4.2 Pairing logic — complete and vector-proven

New `core/.../PairingSession.kt` implements the phone's half of §5.2.2 as pure logic:

- **Invite parsing** with distinct, honest rejections: `SUITE_UNSUPPORTED`,
  `VERSION_UNSUPPORTED`, `INSECURE_RELAY`, `MALFORMED`. The suite check is the one that matters
  — §5.2 requires a phone that does not recognise `suite` to refuse and show the mismatch,
  **never** silently fall back. The realistic trigger is the reserved post-quantum suite on a
  newer desktop meeting an older phone, and a downgrade the user cannot see is worse than a
  pairing that fails loudly. Pinned by `an unrecognised suite refuses to pair instead of
  falling back`.
- **Completion building**, proven end-to-end against `pairing-basic`: the test builds the
  completion, then *plays the engine* — deriving from the `phone_pub` on the wire and opening
  the ciphertext — and asserts both sides compute the same six-digit confirm code. Derived
  values (`k_p2e`, relay token, provisional token, confirm) all equal the vector's.
- `the device signing key never appears outside the ciphertext` asserts §5.2.2's privacy
  property directly on the body the relay would see: the relay must never learn which signing
  key belongs to a pairing.
- `a swapped phone_pub breaks the handshake rather than hijacking it` reproduces the MITM the
  AAD binding exists to stop, using the `pairing-mitm-keyswap` vector's attacker key.

**No Android types, and no private key material.** The device signing key is taken as a public
point plus a signing function, so this class never sees a private key — the Keystore key is
non-exportable by construction and this API does not tempt anyone to change that. That design
choice is also *why* this half could be completed and tested while the screen could not.

### A4.3 What is NOT built, and why that is a gate rather than a shortfall

The pairing **screen** is not built. Two independent blockers, both recorded in `BLOCKED.md`
B-1:

1. Gate **`P2-KEYSTORE-FALLBACK` is open and Brandon-only** — it decides whether a device
   without StrongBox pairs anyway with a logged software-key downgrade, or refuses. That is a
   security-posture promise the screen would have to state; guessing it means shipping a claim
   nobody approved.
2. **No device and no emulator** (probe output in B-1). The device key is an Android Keystore
   key, which is exactly what Robolectric does not model — a screen written now could be
   compiled but not honestly tested.

### A4.4 Verification

```
$ ./gradlew --no-daemon :core:test :app:test --rerun-tasks
BUILD SUCCESSFUL in 58s
35 actionable tasks: 35 executed
```

`PairingSessionTest` 8 · `ScreensFromFixtureTest` 6 → **8** · **TOTAL 84 tests, 0 failures**
(74 → 84).

---

## A5 — Live end-to-end · 2026-07-30 · **NOT REACHED — honest statement of the level achieved**

The spec asks for "an honest statement of exactly how far e2e got, with evidence". Here it is.

### A5.1 What was achieved: the client works against the real relay

One probe, on the single route that carries no pairing information (§2: "Liveness. Returns no
pairing information."):

```
$ GET https://relay.careerseeker.app/v1/health
status : 200 OK
elapsed: 243 ms
body   : {"ok":true,"protocol":1,"phase":"p1"}
server : cloudflare
```

This proves the relay is live, TLS-reachable from here, and speaking protocol 1. It is a
client GET and nothing more — **no pairing was created, no envelope pushed, nothing deployed
or configured.**

Worth flagging for Brandon: the deployed relay self-reports **`"phase":"p1"`**. If that string
tracks the deployment rather than the protocol, the live Worker predates the P2/P4 work. Not
investigated further — the relay is production and belongs to the engine program.

### A5.2 What was not achieved, and the real reason

**Not** "no phone". The binding constraint is engine-side:

- No device, no emulator, no system image (probe in `BLOCKED.md` B-1).
- The spec's fallback — drive `:core`'s client against a locally-run engine — is blocked
  because, per `HANDOFF.md` §4, the engine's `--sync` flag is honored but **no-ops with an
  explicit note**: publishing needs a completed pairing, and the desktop `/pair` page that
  would create one is listed as still-to-build. `BuildSyncBridge` is a documented seam with no
  `RelayClient`-backed sink behind it.

So there is currently no way for an engine on this machine to publish a real envelope for a
phone to read. **Until the engine can publish, the phone has nothing to receive** — no amount
of phone-side work changes that, which is why this is recorded rather than worked around.

The reference repo was also left alone for a second reason: a parallel session owns it this
week and its working tree sits on `codex/beta-M0-preflight`.

### A5.3 The e2e level actually achieved

| Layer | Status |
| --- | --- |
| Envelope codec ↔ engine, byte-for-byte | **proven** — 25 shared vectors, 100% |
| Pairing derivation ↔ engine | **proven** — `pairing-basic`, both sides compute the same confirm code |
| Entitlement classification ↔ engine | **proven** — 5 signed vectors, exact reasons |
| Relay client protocol behaviour | **proven offline** — 14 MockEngine tests |
| Relay reachable over TLS from this machine | **proven live** — `/v1/health` 200 |
| Engine → relay → phone with a real envelope | **NOT reached** (engine-side, B-2) |
| Phone → relay → engine with a real command | **NOT reached** (same) |

Milestone artifact: commit `54b8937`; bundle refreshed (676,583 bytes).

---

## A6 — Outcomes + entitlement surface · 2026-07-30 · **PARTIAL** (display done; controls gated)

### A6.1 F-5 closed — the replica was dropping the `outcome` field

§4.3.1 was amended in P4 to carry a nullable `outcome` on each application summary.
`EnvelopeApplier.appOf()` did not parse it, so the field arrived from the engine and was
discarded: the phone could not have shown outcome tracking even against a Pro engine.

Now projected onto `ApplicationRow.outcome` (Room **v2 → v3**, `ALTER TABLE applications ADD
COLUMN outcome TEXT`, nullable with no default — NULL *is* the protocol's "unset", and a
default would turn "the engine never said" into a claim about the application).

Two details that are contract, not preference:

- **Absent is not malformed.** §4.3.1 says a receiver treats an absent field as "no outcome",
  never as a malformed value. A non-Pro engine simply omits it, and its snapshots must still
  parse — so the field is read leniently while every other field stays strict.
- **It is stored as an opaque string, not the phone's enum.** The wire vocabulary is the
  *store's superset* and includes `no_reply`, a desktop-set observation. Validating against the
  phone's narrower enum would silently drop legitimate engine values.

`ApplicationRow.outcome` is declared last with a default so the existing positional
constructions in the demo fixture and applier tests keep meaning what they meant.

### A6.2 Outbound envelopes — two rules made structural

New `core/.../OutboundEnvelopes.kt`. Rather than documenting the p2e rules, it enforces them:

1. **A state-changing kind is signed, or it is not built.** `doc_edit`, `outcome`, and
   `entitlement` require the envelope `sig` (§5.4). `build()` throws `UnsignableEnvelope` when
   no signer is configured instead of emitting an envelope the engine must reject. The audit
   chain's ability to prove *which paired device* asked for a change depends on that signature
   existing.
2. **The sequence number is owned, not passed in.** The factory takes a `SeqSource`, so a
   caller cannot supply — or reuse — a number. A reused p2e seq is silently dropped by the
   engine as a replay, which to a user looks exactly like "the outcome didn't save".

`Outcome` models the **five values a phone may set**, deliberately not the six the wire carries
back. `no_reply` is in `Outcome.ENGINE_ONLY` and `Outcome.fromWire("no_reply")` is null — the
phone renders it, and cannot send it. That distinction is what stops a future screen offering a
button the engine would reject.

Correctness is proven by round-tripping through `EnvelopeReceiver` — the same state machine the
engine mirrors — rather than by inspecting strings:

- an outcome envelope this factory builds is **accepted**, signature and all;
- flipping one ciphertext character makes it **rejected**, so the signature covers the real
  thing;
- three queued offline outcomes replay in order and all three are accepted;
- the entitlement courier forwards `original_json` **byte-for-byte** (tested with a record
  containing quotes and a backslash, since re-serialising would destroy the RSA signature);
- the sealed wire contains neither the application id nor the outcome nor the key — and
  decrypts to the truth under the right key, so that check is not vacuous;
- `send_email` and every reserved L2 kind are **unbuildable** (§8.1 as a test).

### A6.3 `ProState` — the phone structurally cannot claim Pro

`ProState.Unlocked` has exactly one producer: `ProState.afterEngineAck(...)`. There is no path
from a locally computed `EntitlementVerdict` to `Unlocked` — a local `ACCEPTED` maps to
`AwaitingEngine` ("the desktop is checking"), never to an optimistic unlock that might revoke
itself later. `ProStateTest` asserts exhaustively that **no** verdict yields `Unlocked`.

This is the phone-side expression of the project's one rule. The engine cannot fabricate a
skill; the phone cannot fabricate a status.

### A6.4 What A6 did not deliver, and the two different reasons

**The outcome-marking control is not built** — same blocker as the pairing screen (B-1). Marking
an outcome *sends a device-signed envelope*, so it needs the Android Keystore key, which needs
gate `P2-KEYSTORE-FALLBACK` answered and a device to test on. The display half has no such
dependency and is done: `OutcomeBadge` renders the engine's outcome on each application card,
including `no_reply`.

**The unlock path cannot be completed at all**, and this one is a contract gap rather than a
gate — recorded as **PQ-A6-1**. §4.3 lists `entitlement_ack` with a one-line description and
**no body definition**, while every other shipping kind has its fields specified. Since
`entitlement_ack` is the only thing that may unlock Pro, and its fields are unknown, the applier
has **no `entitlement_ack` branch** — guessing field names would be inventing wire format, and
writing a parser for an unshipped shape is the drift generator this repo already forbids for the
`doc` kind.

Consequence, stated plainly: **`ProState.afterEngineAck` has no caller.** The app is honestly
Free with no way to become anything else, which is the correct behaviour for a build that cannot
receive an ack. Suggested body in PQ-A6-1.

### A6.5 A build failure worth recording: newest ≠ compatible

A3 picked Ktor **3.2.0** after checking Maven Central — following the repo's rule that versions
are verified against the artifact repository rather than copied from a spec. That rule was
necessary and insufficient. The APK build failed:

```
D8: Space characters in SimpleName 'use streaming syntax' are not allowed prior to
    DEX version 040 (field name `use streaming syntax` on class io.ktor.client.plugins.Messages)
> Task :app:mergeExtDexDebug FAILED
```

Ktor 3.2.0 ships a field whose name contains spaces — legal JVM bytecode, rejected by D8 below
DEX 040, which needs **minSdk 30**. This project is minSdk **26**.

Two things this teaches, both recorded rather than quietly fixed:

1. **Every JVM test passed on 3.2.0**, because unit tests are never dexed. Only `assembleDebug`
   caught it. That is precisely why the ladder builds the APK at every milestone instead of
   saving packaging for A7 — this would otherwise have surfaced at the end, attributed to
   whatever was touched last.
2. **The tempting fix is a product decision in disguise.** Raising `minSdk` to 30 would make the
   error vanish and silently drop Android 8, 9, and 10 devices. That is Brandon's call about who
   can install the app, not a build workaround. The dependency moved instead: **Ktor 3.1.3**,
   the previous release, which dexes cleanly at 26.

### A6.6 Verification

```
$ ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug --rerun-tasks
:core is Android-free.
BUILD SUCCESSFUL in 1m 47s
62 actionable tasks: 62 executed
```

| Suite | Tests | Fail |
| --- | --- | --- |
| `core…ProtocolTest` | 11 | 0 |
| `core…ProtocolVectorsTest` | 6 | 0 |
| `core…EntitlementVectorsTest` | 5 | 0 |
| `core…EnvelopeJsonTest` | 8 | 0 |
| `core…RelayClientTest` | 14 | 0 |
| `core…PairingSessionTest` | 8 | 0 |
| `core…OutboundEnvelopesTest` *(new)* | 10 | 0 |
| `core…ProStateTest` *(new)* | 5 | 0 |
| `dashboard.replica.DemoFixtureTest` | 3 | 0 |
| `dashboard.replica.EnvelopeApplierTest` | 21 | 0 |
| `dashboard.ui.ScreensFromFixtureTest` | 8 | 0 |
| **TOTAL** | **99** | **0** |

Ladder progression: **42 → 55 → 74 → 84 → 99**.

APK: `app/build/outputs/apk/debug/app-debug.apk`, **13,180,026 bytes**, sha256
`CD7B8A26A9B0FEE1E1C756159B5BFAC0D256607F6265D4220A4310E2487AB9CE`, built 2026-07-30 21:27:49.
The ~1 MB growth over A2's 12,164,050 is Ktor entering the APK.

### A6.7 Prohibitions

No Play Billing code exists anywhere in the repo — entitlement is exercised **only** with the
signed test vectors. No push, no PR, no reference-repo write, no purchase, no account action.
The relay was contacted once in this session (A5.1, `/v1/health`) and not at all in A6.

Milestone artifact: commit `26b9aee`; bundle refreshed (696,755 bytes).

---

## A7 — Package + handoff · 2026-07-30 · **COMPLETE**

Deliverables:

- **APK** — `app/build/outputs/apk/debug/app-debug.apk`, 13,180,026 bytes, sha256
  `CD7B8A26A9B0FEE1E1C756159B5BFAC0D256607F6265D4220A4310E2487AB9CE`, built from a forced
  full run (62/62 tasks executed), lint green under `warningsAsErrors`.
- **[`SIDELOAD.md`](SIDELOAD.md)** — build/install steps, a review tour that says which parts
  of each screen are real and which are fixture, five things worth deliberately checking
  (they are the claims the app makes), and an explicit list of what is *not* in the build.
- **[`HANDOFF-Android-Alpha.md`](HANDOFF-Android-Alpha.md)** — lane achieved, e2e level, every
  deviation, the merge hazard, decisions waiting on Brandon, and the next three Beta tasks.
- **`LOG.md`** and **`AUDIT-REQUEST.md`** — this file, and a re-verification command for every
  claim in it.
- **Bundle** — `C:\Users\bkirk\Desktop\careerseeker-android-2026-07-30.bundle`, refreshed at
  every milestone, `git bundle verify` clean.

Named `HANDOFF-Android-Alpha.md` rather than `HANDOFF.md` deliberately: `HANDOFF.md` exists on
`main` with the program-level handoff and is absent from every code branch, so a same-named
file here would force a merge resolution between two unrelated documents.

### A7.1 The hardening backlog was NOT started

Spec §6 lists it for the case where the ladder completes early. The ladder did not complete
early — A4, A5, and A6 are partial for reasons recorded in `BLOCKED.md` and `PQ-A6-1`, and the
remaining budget went into stating those honestly rather than into reducer fuzzing. Listed in
the handoff as queued work.

### A7.2 Final state

| | |
| --- | --- |
| Tests | **99**, 0 failures, 0 errors |
| Vector conformance | 25/25 — every valid vector accepted, every invalid one rejected with its stated reason |
| Commits | 8 on `claude/android-a0-probe`, off `claude/p4-pro` |
| Pushed | **no** — house rule is that Brandon decides when draft PRs open |


---

## S0 — Re-entry + derivation · 2026-08-08 · **COMPLETE**

First rung of the unattended window (2026-08-07 → 2026-08-18). No source was touched: this rung
exists to replace assumption with measurement before anything is built on top of it. Full
derivation in [`docs/S-Ladder.md`](docs/S-Ladder.md); one re-verification command per claim in
[`AUDIT-REQUEST.md`](AUDIT-REQUEST.md) §S0.

### S0.1 The mandatory fetch immediately earned its place

Both trees were fetched before any ref was read. The android tree had no ref changes. The main-repo
clone's `main` moved **`e95b1b3..3a89fb5` — 27 commits** — and seven `codex/r0..r6` branches plus
`autonomy/codex-state` appeared for the first time. Every count in this entry was taken *after*
that fetch. Had it been skipped, the behind-counts in S0.4 would have been wrong by 27.

### S0.2 The two-lineage hazard is not the shape it was described as

Expected: a chain `p2-replica → p4-pro → p5-store`. Measured:

- **`claude/p4-pro` and `claude/p2-replica` are the same commit** (`d9f95fd`, identical SHA).
  There is **no android-side P4 work on any branch** — P4 exists only engine-side as PR #8.
- **`a0-probe` and `p5-store` are siblings**, both branching from `d9f95fd`. `p5-store` is not an
  ancestor of `a0-probe` (`merge-base --is-ancestor` → exit 1).
- **`main` has diverged**, not merely fallen behind: `main…p2-replica` = **10 ahead / 23 ahead**,
  and `main` is not an ancestor of the code lineage. `main`'s tree is exactly `HANDOFF.md`,
  `README.md`, `docs` — docs-only, as expected.

The collision set between the two siblings was computed rather than predicted — a set intersection
of the two diffs from `d9f95fd`:

| Overlapping file | |
| --- | --- |
| `app/.../ui/HomeScreen.kt` | both branches |
| `app/.../ui/ApplicationsScreen.kt` | both branches |
| `app/src/test/.../ScreensFromFixtureTest.kt` | both branches |

`ApplicationDetailScreen.kt` was expected to collide and **does not** — only `p5-store` touches it.
Flagged, deliberately unresolved: the merge policy is Brandon's alone.

### S0.3 B-3 is now locally verified, not merely expected

A7 recorded the vendored-vector drift check as *expected to pass*, because CI's step could not run
here and the only local comparison available was against a same-machine reference tree owned by a
parallel session. This window supplies a dedicated independent clone, so the check was re-run
properly — blob-to-blob, comparing git object hashes, which is the identity CI actually asserts:

```
pin 679a3175590dcd021b21c85af9daf12114e131fd present in clone: exit=0
vendored vectors compared = 26    mismatches = 0
```

All 26 files under `core/src/test/resources/sync-vectors/v1/` are byte-identical to the pin.

This also surfaced a caveat worth stating before it bites: **the pin is not an ancestor of
`origin/main`.** It is reachable only through the unmerged sync stack. Nothing is wrong today, but
cross-repo vector identity currently hangs off an unmerged branch, and S1 must confirm the content
survives the rebase byte-for-byte.

### S0.4 The engine stack is intact — and missing from `main` entirely

Stacked ancestry verified, all three checks exit 0: **5 ⊂ 6 ⊂ 7 ⊂ 8**, with ahead-counts
**3 / 6 / 13 / 21** exactly as expected. Behind-count is **85 for all four**, not the expected ~58
— the difference is precisely S0.1's 27 commits.

The finding that reorders the ladder came from a path check on `origin/main`:

```
matches for relay/ | src/Sync/ | Sync-Protocol | sync-vectors/ | SyncHarness
  on origin/main                      :  0
  on origin/claude/p4-entitlement     :  45+
```

The protocol spec, the 26 shared vectors, the blind relay and the C# sync sources exist **only on
the unmerged PR stack**. S2, S4, S5 and S6 all edit files that are not on the branch anyone would
build from. **S1 is therefore not housekeeping — it is the gate for everything downstream**, and
B-2 cannot be closed by writing publisher code first. `BLOCKED.md` B-2 was updated to say so with
the measurement rather than the inference.

### S0.5 Two superseded rules, both recorded rather than silently dropped

- **Gate `P0-BASE`** targeted `claude/alpha-finish`. That PR (#4) is **MERGED**; the alpha train
  landed long ago. New base of record: `origin/main` = `3a89fb5`.
- **A7.2's "Pushed: no"** house rule — that Brandon decides when draft PRs open — is reversed by
  mission §3(c), which explicitly permits pushing branches and opening draft PRs in both repos.
  Acted on this rung; the android repo remains **never-self-merge**.

Also corrected: A7.2 records 8 commits on `a0-probe`. A ninth (`d839e48`) landed after that table
was written, so `d9f95fd..HEAD` is **9**. The table was accurate when written; this is the update.

### S0.6 Documents the mission points at that are not in this repo

Searched across `main`, `p2-replica`, `p5-store` and `a0-probe` — zero hits for `docs/P2-Runbook.md`
and `docs/Sync-Protocol.md`. The former was where §2.1 asked for the `P2-KEYSTORE-FALLBACK` gate
record; it is recorded in `docs/S-Ladder.md` §4 instead and carried to S3. `Sync-Protocol.md` lives
in the main repo on the unmerged stack. `HUMAN-QUEUE.md` is really `docs/autonomy/HUMAN-QUEUE.md`,
in the main repo.

### S0.7 Housekeeping

The stale `careerseeker-android-p5` worktree was removed. `git worktree remove` de-registered it
and then **failed** with `Filename too long`, leaving orphaned Gradle `build/` output on disk; the
residue was cleared with a robocopy mirror-empty, the standard Windows long-path removal. Verified
before and after: the tree was clean, and `claude/p5-store` still resolves to `bb7f4d0` locally and
on the remote, with PR #5 an untouched draft. One worktree remains.

Terra's `autonomy/codex-state:STATE.md` was read: **R6(b) BLOCKED**, PR #26 draft, **files claimed:
none** — no collision with this slice. Terra's measured `$ExpectedOfflineTotal` is now **412** (was
407); S1 must re-derive rather than copy it.

### S0.8 State

| | |
| --- | --- |
| Tests | not re-run — **no source file was touched this rung** |
| Vector conformance | 26/26 byte-identical to pin `679a317` (blob-to-blob, independent clone) |
| Android PRs | #1–#5 left **draft and unmodified** |
| Rung | S0 **DONE**; next is S1 |

### S0.9 What was not touched

No deploys of any kind, and the production relay was not contacted at all this rung — not even
`GET /v1/health`. `Documents\CareerSeeker` and Terra's `CareerSeeker-r6-sbom` worktree were never
read from or written to. No Google, Play, OAuth or Console action; no accounts, no purchases, no
Play Billing code; no email or Gmail anything; no cert-store or MSIX action; no reboot; no
force-push and no history rewrite; no secrets read or written; no `.appdata` originals; no edits to
`Desktop\site-v2`. Nothing was merged in either repository, and no android PR was taken out of
draft. No android source file was edited — this rung is documentation and derivation only.

### S0.10 CI confirmed B-3 after the push — the blocker is closed

The push in S0.7 triggered CI run
[`31278769047`](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31278769047)
(6m01s, **success**). Per-step, not just per-workflow:

```
Assert :core has no Android dependency                            success
Assert vendored sync vectors match the pinned main-repo commit    success   <-- B-3
Unit tests (:core)                                                success
Unit tests (:app, Robolectric)                                    success
Assemble debug APK                                                success
Lint                                                              success
Assert no analytics or tracking SDKs ship                         success
```

The step list was inspected deliberately rather than reading the overall green: a *skipped* step
also produces a green run, so "CI passed" would not have closed a blocker whose whole content was
"this specific step has never executed." It executed, and it agrees with S0.3's local 26/26.

**B-3 is closed** — the first of the three A-ladder blockers to be retired. CI also ran the full
gate on this branch, so the alpha code is independently green on a clean Linux checkout; that is
CI's result, not a local re-run, and S0.8's "tests not re-run" stands as written for this machine.

`autonomy/claude-state` was created in the main repo (`01ade62`) as the counterpart to
`autonomy/codex-state`, answering Terra's note that it "remained absent." One config change to
record: my sync clone had **no git identity**, so commits there are made with a per-invocation
`git -c user.name/user.email` matching the android repo's existing identity. Nothing was written
to global git config, and no persistent repo config was altered.

---

## S1 — The engine sync track lands in `main` · 2026-08-09 · **COMPLETE**

S1 ran in the main repo (`careerseeker`), not here. It is logged in this file because S0 measured
that it gates this repo's entire remaining roadmap: **S2, S4, S5 and S6 all edit files that did not
exist on any branch anyone would build from.** Re-verification commands are in `AUDIT-REQUEST.md`
§S1.

### S1.1 Four PRs, re-cut rather than force-pushed

The stack (#5 ⊂ #6 ⊂ #7 ⊂ #8) was **85 commits behind** `main`. Updating a live PR branch by rebase
requires a force-push, which is embargoed this window — so each was **re-cut** onto fresh `main` as
a new branch and PR, and the original closed with a comment naming its successor. No branch was
deleted, which also keeps the android's vendored pin `679a317` reachable.

| Was | Now | Merged as | Pin after |
| --- | --- | --- | --- |
| #5 P0 protocol + relay | **#27** | `7f3e61e` | 457 |
| #6 P1 pairing | **#28** | `f0b9bd5` | 486 |
| #7 P2 publisher | **#29** | `160b317` | 528 |
| #8 P4 entitlement + outcomes | **#30** | (below) | 591 |

Each: rebase → full local gate (`-IncludePublish -IncludePackage`, exit code read from a file, never
off a pipe) → CI green → merge. `origin/main` was re-checked immediately before every merge to
confirm it had not moved underneath the gate.

### S1.2 Every pin measured, and why that was not pedantry

The offline total moved **418 → 457 → 486 → 528 → 591**. Not one of those was computed on paper.
The first run of each rung was left deliberately mismatched so the drift trap would report the real
figure.

That paid for itself at P1. The commit subject reads *"harness 39->60"*, which implies 457 + 21 =
**478**. The measured answer was **486** — later commits on that branch carried SyncHarness to 68.
A number derived from the commit message would have been wrong, and would have looked entirely
reasonable in review.

### S1.3 The conflicts were almost all one thing, until they weren't

Nearly every conflict was a count-reporting doc — the drift trap working as designed. Those were
resolved by taking `main`'s current text and re-applying only each branch's genuine contribution;
the branches' stale snapshots of whole doc sections were discarded, because they described a repo
that no longer exists (EngineHarness 89 vs 210, the pre-MSIX README).

To keep that mechanical resolution honest, the loop that applied it **refused to touch any file
outside a known doc set** and stopped for manual review otherwise. It stopped three times, and each
was a real merge:

- **`src/Engine/Host.cs`** (twice — a declared pinch point). `main` had grown
  `pauseRequested`/`maximumBackoff` and live scheduler-state reporting; the branch wrapped the tick
  to publish after each cycle; P4 added the Pro seam. All three survive: the sync publish is
  composed into the tick handed to the backoff-aware scheduler, so a flaky relay still cannot stall
  the engine, and `main`'s dashboard accessibility work (`role=region`, `scope=col`, `sr-only`
  caption) now coexists with P4's funnel panel and Outcome column instead of one overwriting the other.
- **`tests/EngineHarness/Program.cs`** — `main` had fixed a bug where a hard-coded port silently
  skipped 19 assertions. That fix survives *alongside* P4's Pro seam.

### S1.4 Three breakages the gate caught that reading the diff would not have

1. **`CS1503`, twice.** A constructor argument passed positionally that is no longer that
   parameter, because `main` grew parameters ahead of it. After the second occurrence all **11**
   `LocalDashboard`/`EngineHost` construction sites were audited rather than fixing only what the
   compiler named; every other site already used named arguments.
2. **An unhandled `TaskCanceledException` that killed a whole harness.** P4's Pro assertions request
   `http://localhost:7777`; `main` had moved that section to a free port because HTTP.sys keeps 7777
   reserved after a real dashboard run. **The compiler cannot see this.** The symptom was not a
   failed assertion but the harness dying on a 3-second timeout. `main`'s own fix exists because the
   same hazard had previously left *"19 assertions quietly not running"* — P4 predates the fix and
   reintroduced the assumption.
3. **A stale instruction in `docs/Scoring-Calibration.md`**, which told readers EngineHarness "must
   report 170 passed". The verifier only asserts the doc *contains* that string, so it would have
   kept passing while misleading every reader who followed it. Moved with the measurements.

(2) is the one worth remembering: **a green build proves nothing about it.** Only running the
harnesses does.

### S1.5 The hard stop, and the cross-repo check that matters to this repo

S1's standing rule is that a rebase moves commits, not bytes; changed vector *content* is a
cross-repo drift event and a full stop. Measured per rung — 16, 22, 22, 27 vector files compared
against each pre-rebase branch — **0 drift, every time.**

The check that matters here is the other one:

```
this repo's pin 679a317  vs  the landing branch
  26 vector files compared, 0 differences
```

`main` now carries vectors **byte-identical** to what `:core` already vendors. C-S0-1 flagged that
the pin was reachable only through an unmerged branch; that is no longer true of the *content*. The
pin still names a non-`main` commit, so re-pinning is queued as tidy-up — to be done by comparing
content, never by assuming.

### S1.6 What is now unblocked, and what is not

`relay/`, `src/Sync/`, `docs/Sync-Protocol.md`, `docs/sync-vectors/` and `tests/SyncHarness` are in
`main` for the first time. **B-2 is not closed** — `--sync` is still honored-but-no-op without a
pairing vault, exactly as `Program.cs::BuildSyncBridge` documents in place. That seam now specifies
precisely what S2 must build: a DPAPI pairing vault persisting **both** `last_e2p_seq` and
`last_p2e_seq` (§6.1 applies in both directions), publisher construction, the `/pair` page, and the
inbound pull loop.

### S1.7 What was not touched

No deploys. `SyncLiveSmoke` was **not run** — it is a live smoke against the relay, and contacting
the relay beyond `GET /v1/health` is embargoed; the live Worker still predates P2/P4 regardless.
The relay was not contacted at all during S1. `docs/P1-Evidence.md` and `docs/P2-Evidence.md`
describe July live runs: those were carried across the rebase as historical evidence and are **not
re-asserted** here. `Documents\CareerSeeker` and Terra's `CareerSeeker-r6-sbom` worktree were never
read or written. No Google/Play/OAuth console, no accounts, no purchases, no Play Billing code, no
Gmail, no secrets, no `.appdata` originals, no `Desktop\site-v2`. No force-push, no history rewrite,
no branch deletion. Nothing in the android repo was merged, and no android PR left draft.

### S1.8 Final state, measured against merged `main`

All four merged. Final `main` = **`a8ef552`**. Re-run against the merged result, not the branches:

```
sync-track paths on origin/main        0  (at S0)  ->  54
$ExpectedOfflineTotal on origin/main                    591
android pin 679a317 vs origin/main     26 vector files compared, 0 differences
```

The S0 finding is fully reversed. The protocol spec, the 26 shared vectors, the blind relay and the
C# sync sources are in `main`, and the phone's vendored vectors are byte-identical to them.

| Rung | Status |
| --- | --- |
| S0 re-entry + derivation | **DONE** |
| S1 land the engine sync track | **DONE** — 4 PRs merged, 0 vector drift |
| S2 engine publishes for real | **NEXT** — B-2 still open, seam now specified |

---

## S2 — The engine can publish for real · 2026-08-09 · **PARTIAL** (B-2 still open)

Engine-side rung, logged here because B-2 lives in this repo's `BLOCKED.md`. PR
[#31](https://github.com/ShivaClaw/careerseeker/pull/31) in `careerseeker`.

### S2.1 End-to-end against a local relay — the proof B-2 has been waiting for

The relay was run **locally** under miniflare (Durable Object in local mode), never deployed:

```
cd relay && npm ci && npx wrangler dev --port 8787 --local
dotnet run --project tests/SyncLiveSmoke -c Release -- http://127.0.0.1:8787

=== 30 passed, 0 failed ===
```

`SyncLiveSmoke` takes the relay URL as `args[0]`, so the *same* proof that was written for the
production relay runs against a local one with no deploy and no embargo problem. The run covers
pairing, snapshot + delta round-trip, a signed p2e `doc_edit` **and its rejection under the wrong
device key**, entitlement → outcome → `pull_request` dispatch in order, a republished snapshot, the
relay refusing a duplicate seq (409), and unpair.

This is the first time in the program that engine ↔ relay has been demonstrated end to end on this
machine.

### S2.2 The vault, and why its two counters are correctness

`src/Engine/SyncPairingVault.cs` — DPAPI-backed, current-Windows-user scoped, holding the pairing,
both directional keys, the device signing key, the relay token, the `key_id`, and both sequence
high-water marks. `BuildSyncBridge` now loads it and constructs a `RelayClient`-backed publisher
with `startSeq` = the persisted mark.

§6.1 applies in both directions, and getting it wrong fails **silently**:

- resuming **e2p** at 1 → every envelope the engine sends is rejected as a replay, *including the
  recovery snapshot*. The phone stops updating while the engine logs success.
- resuming **p2e** at 0 → an already-applied entitlement or outcome is applied again.

So the record methods are monotonic by construction: a lower seq is ignored, never written. Seven
EngineHarness assertions cover round-trip, advance, ignore-lower, equal-value, no-key-leak in
`Describe`, partial-vault-loads-as-nothing, and delete.

### S2.3 A standing assumption, corrected

The mission (and `BLOCKED.md` B-2) treat the live Worker's
`{"ok":true,"protocol":1,"phase":"p1"}` as evidence that it predates P2/P4. **It is not.**
`phase: 'p1'` is hard-coded at `relay/src/index.ts:47` — the *current* source reports exactly the
same string, as the local instance did. Whether the deployed Worker is stale must be established
some other way (deployed script hash, or a build stamp added to the response). The redeploy may
still be wanted; the health string is simply not the evidence for it.

### S2.4 Why B-2 is not closed

**The desktop `/pair` page does not exist.** Until it does, the vault has no product path to being
populated, so `--sync` still publishes nothing for a real user. The handshake itself is complete
and vector-proven, and S2.1 exercises it end to end — but a harness creating a pairing is not a
person pairing a phone.

I stopped at the page rather than rush a UI I could only partially verify. The remaining work is
specified precisely in `BLOCKED.md` B-2.

### S2.5 Verification and boundary

```
scripts\Verify-Alpha.ps1 -IncludePublish -IncludePackage  ->  PS_EXIT=0
Offline total: 598 passed, 0 failed   (EngineHarness 210 -> 217)
dotnet build -c Release  ->  0 Warning(s), 0 Error(s)
```

No deploy of any kind. The **production** relay was not contacted at all this rung — every request
went to `127.0.0.1:8787`, and the local process was stopped afterwards (port confirmed free). The
one machine change was `npm ci` in `relay/` (dependencies installed locally; `node_modules` is
gitignored). No Google/Play/OAuth console, no accounts, no purchases, no Gmail, no secrets read or
printed, no `.appdata` originals, no `Desktop\site-v2`, no force-push, no history rewrite. Nothing
in the android repo changed except these records.

---

# HANDOFF — unattended window, 2026-08-08/09

Written at the end of this session's capacity, not at the end of the ladder. **S0–S2 ran; S3–S8 did
not start.** This entry says so plainly rather than reporting a ladder that was not climbed.

## Ladder status — honest

| Rung | Status | Evidence |
| --- | --- | --- |
| **S0** re-entry + derivation | **DONE** | `LOG.md` §S0, `docs/S-Ladder.md`, `AUDIT-REQUEST.md` C-S0-1…9 |
| **S1** land the engine sync track | **DONE** | §S1, C-S1-1…6; PRs #27–#30 merged |
| **S2** engine publishes for real | **PARTIAL** — B-2 narrowed to one screen | §S2; PR #31 merged |
| **S3** pairing screen | **BLOCKED — B-4** | `BLOCKED.md` B-4 (probe output) |
| **S4** transport loop | **BLOCKED — B-4** | needs S3's device key + an emulator |
| **S6** outcome marking (phone) | **BLOCKED — B-4** | needs S3 + S4 |
| **S5** entitlement ack | **NOT STARTED** | capacity; engine half needs no device |
| **S7** Play-readiness pack | **NOT STARTED** | capacity; screenshots also need B-4 |
| **S8** hardening | **NOT STARTED** | capacity; mechanical, no device needed |

**The distinction is deliberate.** S3/S4/S6 are genuinely **BLOCKED**, on a blocker found by probing
rather than assumed: `sdkmanager` and `avdmanager` are **not installed anywhere on this machine** —
no `cmdline-tools` directory in the SDK, none bundled with Android Studio. Mission §3a authorized
*using* `sdkmanager` to install a system image; it does not exist, and installing the toolchain that
provides it is a machine change nobody authorized. Without an AVD, Keystore behaviour cannot be
honestly verified (Robolectric does not model it) and compile-only screen claims are forbidden — so
building S3 now would produce exactly the unverifiable artifact B-1 already refused once.

S5/S7/S8 are **NOT STARTED**, which is different: nothing external stopped them and the session's
capacity simply ran out. Calling those BLOCKED would send the next session hunting for an obstacle
that does not exist.

## What actually changed

**The roadmap was unblocked.** S0 measured that the entire engine sync track was absent from
`careerseeker`'s `main` — 0 path matches for `relay/`, `src/Sync/`, `Sync-Protocol`,
`sync-vectors/`, `SyncHarness`. It is now **54**. S2, S4, S5 and S6 had all been specified against
files that were not on any branch anyone would build from.

**Engine ↔ relay works end to end on this machine** — 30/30 against a local miniflare relay, no
deploy. That is what B-2 said had never been reached.

**Two A-ladder blockers moved.** B-3 is **closed** (CI ran the vendored-vector step; 26/26
byte-identical to pin `679a317`). B-2 is narrowed from "the engine has no publisher" to "there is no
`/pair` page". B-1's gate is answered and it is scheduled at S3.

## PR stack

**`careerseeker` (merged by me, per this window's policy):** #27 `7f3e61e` → #28 `f0b9bd5` →
#29 `160b317` → #30 `a8ef552` → #31 `00b3705`. Originals #5–#8 **closed as superseded** (force-push
embargoed, so each was re-cut rather than rewritten; **no branch deleted**, which keeps pin
`679a317` reachable). Each PR carries its own self-audit section.

**`careerseeker-android` (never self-merge):** PR
[#6](https://github.com/ShivaClaw/careerseeker-android/pull/6) opened as a **draft** with a
self-audit; #1–#5 left untouched drafts. **Nothing in this repo was merged.**

## The three things most worth distrusting

1. **`Host.cs` was merged by hand, twice.** Two independently-evolved designs were fused: main's
   backoff/pause/runtime-status wiring, the sync publish-after-cycle tick, and P4's Pro seam. The
   gate proves it compiles and 598 assertions pass. It does not prove I preserved each side's intent
   exactly. Read the `EngineHost` constructor and `LocalDashboard`'s signature first.
2. **A green build proved nothing about the worst bug found.** P4's Pro assertions hit a hard-coded
   `localhost:7777` while main had moved to a free port; it compiled perfectly and then killed the
   whole harness with an unhandled `TaskCanceledException`. Only *running* the harnesses surfaced it.
3. **Doc resolutions dropped whatever only the stale branch knew.** Five docs were resolved to
   main's text wholesale, four times over. The code and vectors merged cleanly; the judgement calls
   are all in prose.

## HUMAN-QUEUE — for return day

1. **Android two-lineage merge decision — Brandon only.** `main` is docs-only and has *diverged*
   (10/23, not an ancestor). `claude/android-a0-probe` and `claude/p5-store` are **siblings** off
   `d9f95fd`, colliding on exactly three files (`HomeScreen.kt`, `ApplicationsScreen.kt`,
   `ScreensFromFixtureTest.kt` — *not* `ApplicationDetailScreen.kt`). Flagged, never resolved.
2. **Relay redeploy — but not for the stated reason.** `phase: "p1"` is hard-coded at
   `relay/src/index.ts:47`, so the live Worker reporting it is **not** evidence of staleness. If a
   redeploy is wanted, establish staleness from the deployed script hash, and consider adding a build
   stamp so this question is answerable next time. Deploys were embargoed all window.
3. **Confirm `purchaseState == 0` against a real purchase.** It means *purchased* in the raw
   `original_json`; the runbook's "(1)" is the `getPurchaseState()` API enum, a different layer. It
   is a named constant. If it is wrong, entitlement is wrong.
4. **Re-pin the android's vendored vectors to a `main` commit.** Pin `679a317` is now
   content-identical to `main` (26/26) but still names a non-`main` commit. Tidiness, not
   correctness — and it must be done by comparing content, never by assuming.

## Next actions, in the order I would take them

- **Finish S2:** the `/pair` route. `PairingInvite.ToQrJson()` is the exact payload, so a QR encoder
  is the only genuinely new dependency; poll `TakeCompletionAsync`, show the confirm code, write
  `SyncPairing` to the vault. Everything else exists and is vector-proven. **This closes B-2.**
- **Unblock S3/S4/S6 first — it is one checkbox.** Android Studio → Settings → Android SDK → SDK
  Tools → tick **"Android SDK Command-line Tools (latest)"**. That creates `sdkmanager.bat`, after
  which §3a applies as written and the rest is unattended:
  `sdkmanager "system-images;android-36;google_apis;x86_64"` → `avdmanager create avd` →
  `emulator -no-window`. (Or authorize an agent to install that package itself.)
- **S4** then has a rig: engine ↔ local relay ↔ emulator on `10.0.2.2`. The local-relay half is
  already proven (30/30).
- **S5 and S8 need none of that** and could proceed immediately — S5's engine half and S8's
  migration coverage/lint hold are device-free.

## Boundary — what was never touched

No deploys of any kind (Cloudflare, Workers, relay, site, Pages). The **production relay was
contacted zero times** this window — the only relay traffic went to `127.0.0.1:8787`, and that
process was stopped (port confirmed free). `Documents\CareerSeeker` and Terra's
`CareerSeeker-r6-sbom` worktree were never read from or written to. No Google, Play, OAuth or
Console action; no accounts, no purchases, no Play Billing code beyond the signed test vectors; no
email or Gmail anything; no cert-store or MSIX signing; no reboot; no force-push; no history
rewrite; no branch deletion; no secrets read, written or printed; no `.appdata` originals; no edits
to `Desktop\site-v2`. No android PR was merged or taken out of draft. `SyncLiveSmoke` was never run
against the production relay. One machine change: `npm ci` in `relay/` (gitignored). One config
note: my sync clone has no git identity, so its commits use a per-invocation `git -c` — `git config`
writes are blocked by the permission classifier, and hand-editing `.git/config` would defeat that.

---

## S7 / S8 — partial, and one blocker each · 2026-08-09

Taken after the handoff above was written, because both rungs have device-free halves that the
emulator blocker does not touch. The handoff's ladder table is superseded by `STATE.md`.

### S8.1 The migration gap is now covered by a test that cannot run here

`ReplicaDb` has carried its own indictment since A3: *"NOT YET COVERED BY A TEST: there is no
`MigrationTestHelper` case opening a v1 database and migrating it."* All three schema versions are
exported (`1.json`, `2.json`, `3.json`), so the test is writable — and it is written, asserting what
the migrations must guarantee rather than merely that they run:

- `snapshotSeen` arrives as **0**. A 1 would claim a snapshot this replica may never have received,
  and deltas would then be applied over demo fixture rows — the exact fabrication the column exists
  to prevent.
- `outcome` arrives **NULL**, because "not recorded" and "known" are different claims.
- The full v1→v3 chain in one pass, which is the path a real device on an old build takes and is not
  the same code path as either step alone.

**It cannot execute on this machine.** Room 2.8.4 routes every open through `SupportSQLiteDriver`,
which compares the requested path against the configured database *name* and throws on Robolectric's
absolute temp path. In-memory databases are unaffected — which is why the existing 16 replica tests
pass and why nobody hit this before. A migration test cannot use in-memory: it must persist a v1 file
and reopen it.

Four attempts, same failure: `runMigrationsAndValidate`; fixing the asset path (that part now works —
`createDatabase` succeeds and builds the v1 DB from the committed schema); opening via
`Room.databaseBuilder`; and forcing the legacy path with `openHelperFactory`. Recorded as **B-5**.

The test is kept under `@Ignore` carrying that diagnosis rather than deleted — the assertions are the
valuable part, and reviving it is one annotation away once the class can move to `androidTest`.

### S8.2 The rest of S8, done

Full gate, forced re-run: **`BUILD SUCCESSFUL`, 62 actionable tasks, 62 executed.**

```
tests=102 failures=0 errors=0 skipped=3
```

99 running (unchanged from A7) + 3 skipped (B-5). Lint stayed clean under `warningsAsErrors`. Bundle
refreshed: `C:\Users\bkirk\Desktop\careerseeker-android-2026-08-09.bundle`, 750,192 bytes,
`git bundle verify` → *okay*.

### S7.1 The Play floor, verified live rather than copied

House rule is that policy facts are checked against the live source at decision time. Checked
2026-08-09 against `developer.android.com/google/play/requirements/target-sdk`:

| | Requirement | Deadline |
| --- | --- | --- |
| New apps and updates | **API 36** | **2026-08-31** (extension to 11-01 on request) |
| Existing apps | API 35 | 2026-08-31 |

**This app targets 37** — it clears the floor by one level, and the mission's gate record (§2.4) is
confirmed correct. Worth noting the deadline is **23 days** out: nothing that would tempt a targetSdk
downgrade before then is a build preference, it is a launch blocker.

### S7.2 Upload keystore, generated under §3(b)

`%USERPROFILE%\.careerseeker-signing\upload-keystore.jks` — **outside every git repository**, not
merely gitignored. RSA 4096, alias `careerseeker-upload`, 10,000-day validity (well past Play's
"valid beyond 2033"). The password was generated in-process and written straight to a sibling file
whose ACL had inheritance removed and is granted to the current user only. **It was never printed** —
not to a terminal, not to a log, not into a commit message. A README alongside explains what the
files are, how to print the certificate fingerprint without echoing a secret, and why loss is
recoverable (it is the *upload* key; Play App Signing holds the irreplaceable one).

A `versionCode` scheme is recorded in `docs/S7-Release-Signing.md` — a plain monotonic integer, not
a date-derived code, with the reasoning for rejecting the latter.

**Not done:** no `.aab` was built or signed, no Gradle signing config was added (wiring one without
producing and verifying an artifact would leave a config claiming a capability nobody exercised), no
R8 pass, **no Play Console action of any kind**, and no screenshots (they need B-4). Listing copy,
data-safety and privacy work were deliberately **not** duplicated — they exist on `claude/p5-store`,
a sibling branch, and re-creating them here would manufacture a conflict on top of the two-lineage
hazard.

### S7.3 / S8.3 Boundary

No deploys. The production relay was not contacted. No Console, account, purchase, Gmail, or
cert-store action; no secrets printed; no force-push or history rewrite; nothing merged in the
android repo. The keystore and its password file live outside every repository and are not tracked.

---

## S5 (first half) — the entitlement_ack body exists, and three questions are answered · 2026-08-09

Taken because S5 was the one rung on the ladder that was **neither done nor blocked**, and because
its first half — spec plus generated vectors — is the only S5 work that can be honestly verified
from a Linux sandbox with no Android SDK, no .NET, no emulator and no Windows. S3/S4/S6 are still
B-4; S2's remainder is a `/pair` page in a C# dashboard I cannot build. The appliers, which are the
second half of S5, are deliberately not here.

Landed as draft [careerseeker#32](https://github.com/ShivaClaw/careerseeker/pull/32) on
`claude/s5-entitlement-ack-spec`, two commits. Nothing in this repo's code changed; this repo's
changes are records and `docs/protocol-questions.md`.

### S5.1 PQ-A6-1 — `entitlement_ack` had a name and no body

Gate answered default-proceed (Brandon, 2026-08-07) with exactly the body PQ-A6-1 proposed.
`docs/Sync-Protocol.md` §4.3.3 now defines `{product_id, acknowledged_at, order_id?}` and pins three
things an implementer would otherwise guess:

- **`acknowledged_at` is advisory** (§6.3) and MUST NOT expire or re-lock an entitlement. An unlock
  that lapsed because two clocks disagreed would be indistinguishable from a revocation nobody
  performed.
- **`order_id` is optional and carries no authorisation weight.** An ack without it is complete.
- **There is no negative form.** A rejected receipt produces an `error`, never an ack with a failure
  flag inside it. This is the one payload that turns a paid feature on, and a kind whose meaning
  depends on reading a field inside the body is the parser mistake §4.2 exists to avoid.

One confirmation worth having: the phone's existing contract already takes exactly this shape.
`ProState.afterEngineAck(productId, acknowledgedAt)` (`core/.../ProState.kt:52`) was written at A6
against PQ-A6-1's *suggested* body, and the gate answer landed on the same two required fields — so
§4.3.3 and the phone's unlock contract agree without either side moving. That is luck confirming a
guess, not evidence the applier works; there is still no caller.

### S5.2 Two vectors, because optionality belongs in an artifact and not in prose

`entitlement-ack` carries `order_id`; `entitlement-ack-no-order-id` does not; both are valid. An
implementation that requires the field now fails on a vector rather than in a support ticket about
an unlock that never happened. `entitlement-ack`'s `order_id` matches `entitlement-valid`'s, so the
two read as one story: that receipt, acknowledged.

```
$ node docs/sync-vectors/generate.mjs
Wrote 28 files to docs/sync-vectors/v1/ (9 valid, 18 invalid).
$ node docs/sync-vectors/generate.mjs --check
OK: 28 vector files match the generator.
```

Verified **independently of the generator**, because a generator agreeing with itself proves
nothing: a standalone script reading only the published files re-opened both ciphertexts from
`key_hex` + `nonce_b64u` + `aad`, matched `plaintext_json` exactly, confirmed the AAD reconstructs
from the envelope header, confirmed neither `e2p` envelope carries `sig`, and confirmed that
flipping one AAD field breaks the tag. `ALL OK`, exit 0.

### S5.3 The finding: a new *valid* `e2p` envelope vector cannot be added at all

This was not anticipated by the mission and is the most useful thing this iteration produced.

The `envelope` vectors are fed through **one receiver in sequence order** — valid first, then
invalid. The valid `e2p` vectors occupy seq 1–4, and every invalid `e2p` vector sits above them
depending on the high-water mark staying at **4**. A new valid vector needs `seq > 4` to be accepted
at all, and `seq < 5` to leave `invalid-truncated-tag` (seq 5) and `invalid-unknown-kind` (seq 8)
alone — the replay check runs before both decryption and the kind check
(`src/Sync/EnvelopeReceiver.cs:53`). **No integer satisfies both.** Their expected `decrypt_failed`
and `unknown_kind` would silently become `replay_rejected`, which is precisely the "rejected for the
wrong reason" failure the suite exists to catch.

Renumbering the existing vectors is not available: their bytes are a published wire artifact that
this repo vendors at pin `679a317`.

The resolution is the one the suite already used for `entitlement`: a new kind gets its **own
`type`**, consumed by a dedicated section. Both consumers filter on the same string
(`tests/SyncHarness/Program.cs:62`, `core/.../ProtocolVectorsTest.kt:55`). Recorded as
`Sync-Protocol.md` §10.1 so the next person does not rediscover it by turning the gate red.

### S5.4 PQ-A2-1 and PQ-A2-2, closed

**PQ-A2-1** — §3.1 now says the 1 MiB cap is on the **decoded ciphertext**, so both implementations
stand and no wire-visible change was made to satisfy a sentence. Checking the claim turned up a
correction to my own entry in `docs/protocol-questions.md`: there are **three** measurements, not
two. The relay tests the `ciphertext` *string* length against 1 MiB and the raw body against
1 MiB + 4 KiB (`relay/src/channel.ts:160`, `:139`). Base64url expands by 4/3, so the relay's test is
the **stricter** one — the original worry ("a receiver accepting something the relay would not have
carried") is inverted from the real relationship. There is no gap. §3.1 now states all three.

**PQ-A2-2** — §3 and §7.2 now say structural rejection reports `decrypt_failed`, and that v1 adds no
`malformed` code, because a distinct code is a new observable and §7.2 already forbids letting an
observer separate `decrypt_failed` from `bad_signature`.

### S5.5 What did NOT get done, and why it is a blocker rather than laziness

**PQ-A2-3's `invalid-unknown-field` vector was not added.** The engine has nowhere to reject the
field: `ReceivedEnvelope` (`src/Sync/EnvelopeReceiver.cs:7`) is constructed *by callers* from
already-parsed JSON, and `SyncHarness`'s `ToReceived` (`tests/SyncHarness/Program.cs:200`) reads nine
fields and drops the rest silently. A vector added today would be **accepted** and would turn the
offline gate red. The parser comes first, the vector second. Recorded as **B-6**.

**Neither applier was written.** No .NET and no Android SDK on this machine, so a `entitlement_ack`
branch in either language would be a parser against a compiler nobody ran — the exact drift
`docs/protocol-questions.md` exists to prevent. `ProState.afterEngineAck(...)` still has no caller,
and the app is still honestly Free with no way to become anything else. `Sync-Protocol.md` §10.2
says out loud that **no consumer asserts against the new vectors yet**, so nobody can mistake their
presence for implemented behaviour.

### S5.6 Cross-repo: additive by construction, and re-vendoring is a separate step

All **25** pre-existing vector files are byte-identical to `origin/main` (blob-to-blob: 25 unchanged,
0 changed). `index.json` is the only existing file that moved, and only by two appended entries.
This repo's vendored copies at pin `679a317` are **untouched** and its CI drift step compares against
that pin, so it is unaffected. Seeing the new vectors here requires a deliberate re-vendor, which is
not part of this slice.

### S5.7 The engine gate — run by CI, not by me

I cannot run `Verify-Alpha.ps1`: there is no .NET on this machine (`which dotnet` → nothing). So the
claim that the type partition is unchanged and the offline pin holds was *reasoned* from measured
inputs (18 envelope / 2 pairing / 5 entitlement, before and after), and then **actually tested by CI
on `windows-latest`**, which is the gate of record:

```
=== 130 passed, 0 failed ===              <- SyncHarness, unchanged
=== Offline total: 598 passed, 0 failed ===
CareerSeeker alpha verification complete.
```

Run [`31292158471`](https://github.com/ShivaClaw/careerseeker/actions/runs/31292158471),
**success**; the relay job passed too. `$ExpectedOfflineTotal` did not need to move, so no
count-reporting doc needed the drift-trap sweep. Had the reasoning been wrong, this is where it
would have failed — which is the point of pushing before claiming.

### S5.8 One record-keeping gap noticed in passing, not fixed

`AUDIT-REQUEST.md` stops at **C-S1-6**. The S2, S3, S7 and S8 slices recorded evidence in `LOG.md`
but never appended their re-verification commands here, which is a drift from the house rule that
every claim carries its command. Noted rather than backfilled — reconstructing commands for
iterations I did not run would be inventing evidence, which is worse than the gap. This iteration's
claims are appended as C-S5-1…5.

### Boundary — what was not touched

No deploys of any kind (Cloudflare, Workers, relay, site, Pages). **The production relay was
contacted zero times** — not even `/v1/health`. No emulator, no `sdkmanager`, no machine change of
any kind. No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code;
no email or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its
password file were neither read nor referenced beyond their paths. No secrets read, written or
printed. **Nothing was merged in either repo**, and PR #32 was opened as a draft; the android repo's
PR #6 was not touched, taken out of draft, or merged. No force-push, no history rewrite, no branch
deleted. No existing vector's bytes were changed. No C# or Kotlin source file was modified in either
repo — this slice is spec, generated vectors, and records. `Documents\CareerSeeker` and Terra's
`CareerSeeker-r6-sbom` worktree are on a machine this session cannot reach at all.

### S5.9 The android gate did run — on CI, after the records were pushed

Added after the fact, because the evidence arrived after the entry above was written and the honest
thing is to record it rather than leave a weaker claim standing.

Pushing the records triggered this repo's own CI at `53710a6`
([run 31292342258](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31292342258)),
conclusion **success**. Checking the steps individually rather than trusting the overall green — the
B-3 lesson, that a skipped step also lets a run go green — the vendored-vector step has no `if:`
condition and therefore cannot skip, and the gate steps each reported:

```
> Task :app:test            BUILD SUCCESSFUL in 1m 42s
> Task :app:assembleDebug   BUILD SUCCESSFUL in 1m 53s
> Task :app:lintDebug       BUILD SUCCESSFUL in 51s
OK: no analytics or tracking SDKs on the release classpath.
```

So `STATE.md`'s "android health" is now **green at this commit**, not carried. Two honest limits on
that: I did not run it — no Android SDK, no JBR, no Gradle here — and Gradle does not print test
*counts*, so **102 / 0 / 0 / 3 remains carried from the S8 local run**. CI proves the build and the
suite are green; it does not re-prove the number, and the number is not upgraded here.

Nothing in this iteration touched Kotlin, so a green result was expected. It is recorded because
"expected" is not evidence, which is the whole point of the house rule.

---

## Merge topology — the S0 derivation finished with measurements · 2026-08-09 · **COMPLETE**

### Why this slice, and not a rung

The scheduled prompt for this iteration assigned S5's first half: amend §4.3 with the
`entitlement_ack` body, add the vector via `generate.mjs`, close PQ-A2-1/-2/-3. **That work was
already done** — by the previous iteration, earlier the same day, on
`claude/s5-entitlement-ack-spec` (careerseeker draft [#32](https://github.com/ShivaClaw/careerseeker/pull/32),
two commits). The prompt was stored before that session ran. Re-doing it would have duplicated a
draft PR and put two branches on the same three files.

So the rung had to be re-picked, and every candidate above it needs a toolchain this environment
does not have. Measured, not assumed:

```
node v22.22.2 · npm 10.9.7 · java present · python3 present
dotnet ABSENT · pwsh ABSENT · sdkmanager ABSENT · ANDROID_HOME unset
```

- **S5's second half** (both appliers) — C# and Kotlin. No compiler. Explicitly out of scope per the
  prompt, and per `docs/protocol-questions.md`'s own rule.
- **S2's remainder** (the `/pair` route) — a C# dashboard page plus a new QR-encoder dependency.
- **S3/S4/S6** — B-4, unchanged.
- **B-6** (the `invalid-unknown-field` vector) — I checked whether it could be added here under its
  own `type` so no consumer picks it up, since that is exactly how the `entitlement-ack` vectors
  landed. **`BLOCKED.md` B-6 had already considered and rejected that**, and is right: it would make
  the suite *look* like it covers a §3 MUST while enforcing nothing, which is the failure PQ-A2-3
  was raised about. Not attempted.
- **S7's store pack** — I got as far as reading the source material before finding
  `docs/S7-Release-Signing.md` §4: the listing copy, data-safety dossier, privacy delta and account-
  day checklist already exist on `claude/p5-store`, and the pricing rewrite exists on
  `claude/todos-pq1-pricing` as `docs/todo/Pricing-Page-Rewrite.md`. Creating them here would have
  duplicated five files onto a second branch — the exact conflict S7 deliberately avoided
  manufacturing. **Not created.**

What was left is a thing this environment can do completely and honestly: **replace the
merge-hazard prediction with a measurement.** git needs no SDK.

### MT.1 The prediction that has been steering decisions since S0

S0 recorded a "two-lineage merge hazard" and predicted that `p5-store` would collide with the
records lineage on `HomeScreen`/`ApplicationsScreen`/`ApplicationDetailScreen`. That prediction has
been load-bearing ever since — S7 declined to create `docs/store/` *because* of it. Three weeks of
decisions rested on something nobody had run.

It is now run. `docs/Merge-Topology.md` is the result; `AUDIT-REQUEST.md` C-MT-1…7 re-verifies every
number in it.

### MT.2 The whole stack merges clean, and that is the finding

Simulating the real integration — `git merge-tree --write-tree`, the same ort strategy `git merge`
uses, each result carried forward as the base for the next:

```
main ebfaf81 ← p0-scaffold : clean   ← p1-pairing : clean   ← p2-replica : clean
             ← p5-store    : clean   ← a0-probe   : clean
             ← p2-runbook  : clean   ← todos-pq1-pricing : clean
             ← p1-runbook  : CONFLICT (1 file)
```

Seven of eight clean. The integrated tree was inspected and contains `app/`, `core/`, the full
`docs/store/` dossier, the pricing rewrite, and this branch's records — nothing was silently
dropped. **No ref was created, moved or pushed:** the simulation writes dangling objects only.

Two structural facts carried forward. The first is **not new** — S0 measured it already
(`LOG.md:954`, `docs/S-Ladder.md:36`); it is re-confirmed here and folded into the merge order,
where it had never been applied:

- **`claude/p4-pro` and `claude/p2-replica` are the same commit** (`d9f95fd`). There is no separate
  P4 branch and no P4 PR because the P4 work is already in the `p2-replica` tip. Still true; still
  worth stating in a document someone reads before merging.
- **Every branch is exactly 10 behind `main`**, and those 10 are docs-only. Merging *into* `main`
  absorbs that. Nothing needs a rebase; nothing needs a force-push. This one is new.

### MT.3 The one conflict is a product decision wearing a diff

`docs/Monetization-Decision.md`, add/add: `p0-scaffold` and `p1-runbook` each created the path
independently. Nine insertions, twelve deletions, two hunks, one question:

| | `a0-probe` lineage | `p1-runbook` |
| --- | --- | --- |
| Price table | "**CareerSeeker Basic** (.exe)" | "**CareerSeeker** — *the product*, not a tier" |
| §3 heading | "Naming note (worth a decision, not urgent)" | "Naming — **decided** 2026-07-23" |

The branch carrying all the recent work — this one — still says the naming question is open and
still prints "Basic" in the price table, while `p1-runbook` records it as closed and rejected,
because a tier name implies withheld features and contradicts the pricing page's strongest promise.
The store listing derives from this table. Resolving by taking either side silently would re-open a
closed decision or quietly close an open one, which is why this is in the human queue rather than
fixed here.

### MT.4 The real hazard is the clean merge, not the dirty one

`p5-store` (#5) and `a0-probe` (#6) fork at `d9f95fd` and both modify the same three files —
`HomeScreen.kt` (+11/−2 vs 0/−1), `ApplicationsScreen.kt` (+19/−2 vs +27/−0),
`ScreensFromFixtureTest.kt` (+53/−0 vs +32/−2). Git fuses all three **without asking**.

So the earlier HUMAN-QUEUE entry was right about *overlap* and imprecise about *conflict*, and the
correction is not reassuring. Two independently-evolved sets of screen edits and two independently-
written test sets get merged by a strategy with no opinion about whether the result is coherent, and
**no gate has ever run on the fused tree** — CI runs per-branch. This repo already has the precedent
in its own "three things most worth distrusting": P4's Pro assertions compiled perfectly and then
killed the harness on a hard-coded port. A clean merge is not a passing gate, and whoever integrates
must run the verification command of record on the *merged* tree.

One correction *not* to claim as mine: `ApplicationDetailScreen.kt` is not among the overlapping
files, and **S0 already established that** (`LOG.md:971` — "expected to collide and does not"). The
mission's original three-file prediction was corrected three weeks ago; what is new here is only
that the three files which *do* overlap merge without conflict.

### MT.5 Two integrity checks, run because they were cheap and load-bearing

**The vendored vector pin holds.** Blob-by-blob against `679a317`: **26 identical, 0 differing, 0
missing.** Upstream is now 28 files — the two `entitlement-ack` vectors from the unmerged #32. The
26/28 gap is the pin working, not drift.

**The relay suite is the one gate this environment can run, and it is green.** `npm ci` +
`npx vitest run` in `careerseeker/relay`: **32 passed, 1 file, 2.58s**, on Node v22.22.2. Recorded
because it is the only executable evidence available from a Linux sandbox, and because the relay's
storage-schema test is what backs the data-safety dossier's "the relay stores ciphertext it cannot
read" claim — a compliance declaration that should rest on a run, not a reading. Also re-ran
`node docs/sync-vectors/generate.mjs --check` on the S5 branch: `OK: 28 vector files match the
generator.`

### MT.6 What this iteration did not establish

The android gate and `Verify-Alpha.ps1` were **not run** — there is no Android SDK, JBR, Gradle,
.NET or PowerShell here, and no emulator. Nothing in this entry claims otherwise. The integrated
tree of MT.2 has never been compiled by anyone; `git merge-tree` reports textual conflicts and is
blind to two branches editing different files that must agree, which is the entire class the
doc/verifier drift trap exists for. `STATE.md`'s 102/0/0/3 test counts remain carried, not
re-measured.

No new blocker was opened: nothing here is blocked. The merge decisions are Brandon's by policy,
not by obstacle, and they are queued below rather than filed in `BLOCKED.md` — calling a decision a
blocker sends the next session hunting for a phantom.

### Boundary — what was not touched

**Nothing was merged, rebased, retargeted, force-pushed or deleted, in either repo.** No branch was
created or moved; the merge simulation produced dangling objects only and left every ref where it
found it. PR #6 stays a draft and #1–#5 were not touched; careerseeker #32 stays a draft and was not
merged. No `.cs`, `.kt`, `.ts` or Gradle file was modified anywhere — this slice is one new document
and records. No existing vector's bytes were changed and nothing was re-vendored. `docs/store/` was
deliberately **not** created here. No deploys of any kind (Cloudflare, Workers, relay, site, Pages);
**the production relay was contacted zero times** — not even `/v1/health`; the only relay code that
ran was `vitest` against miniflare in-process. No emulator, no `sdkmanager`, no machine change
beyond `npm ci` in `relay/` (gitignored). No Google, Play, OAuth or Console action; no accounts, no
purchases, no Play Billing code; no email or Gmail anything; no cert-store, MSIX or keystore action —
the upload keystore and its password file were neither read nor referenced beyond their paths. No
secrets read, written or printed. Terra's worktree and `Documents\CareerSeeker` are on a machine
this session cannot reach at all.

## S5 (second half, phone side) — the ack has a consumer, and `:core` turns out to be runnable here · 2026-08-09 · **PARTIAL**

Third cloud iteration of the day, Linux sandbox. The assigned slice was S5's first half — §4.3.3,
PQ-A2-1/-2/-3, the vectors. **That was already done** by this morning's iteration and sits in the
main repo's draft PR #32; re-verified here rather than assumed:
`node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the generator.` So this
iteration took the rung's *second* half instead, on the side that turned out to be verifiable.

### S5.B-0 The environment finding that decided the slice

`STATE.md` said the cloud sandbox has "Node and git and nothing else". That is **wrong in one
direction and worse in another**, and both halves matter.

**Wrong:** there is a JDK (21) and a Gradle (8.14.3 system, plus the repo's pinned 9.6.1 once the
wrapper fetched it). `:core` is a pure Kotlin/JVM module whose every dependency —
kotlinx-serialization, Ktor, kotlin-test, coroutines-test — resolves from **Maven Central**, which
is reachable. So `:core` compiles and tests **here**, which no previous session had established.

**Worse:** `dl.google.com` is **egress-denied by policy**, not merely absent —
`curl` → `CONNECT tunnel failed, response 403`, and the proxy's own status endpoint names it:
`{"kind":"connect_rejected","detail":"gateway answered 403 to CONNECT","host":"dl.google.com:443"}`.
So the android gate is unrunnable here for a **second, independent reason** on top of the missing
SDK: AGP 9.3.0 and every `androidx` artifact are unreachable. `api.foojay.io` is denied too, which
is why the JDK-17 toolchain cannot be provisioned. Recorded as **B-7**.

The probe harness is therefore a **reduced** one and is labelled as such everywhere it is cited: a
throwaway `settings.gradle.kts` in the scratchpad including only `:core`, with the toolchain
overridden 17 → 21 because 17 cannot be obtained here. It is **not** the verification command of
record, it did not run `checkCoreIsAndroidFree`, `:app:test`, `assembleDebug` or `lintDebug`, and
nothing below claims otherwise. CI on `ubuntu-latest` — JDK 17, real SDK — remains the gate.

### S5.B-1 Baseline first, so the delta is a measurement and not a story

Before writing a line: `:core:test` on the untouched branch → **67 tests, 0 failures, 0 skipped**,
`BUILD SUCCESSFUL`. Worth stating plainly: `STATE.md`'s carried **102 / 0 / 0 / 3** is `:core` +
`:app` together, and `:app` cannot run here at all. 67 is the `:core` share, now measured rather
than carried.

### S5.B-2 The applier

`core/.../EntitlementAck.kt` — `EntitlementAck` plus `EntitlementAckApplier(knownProductIds)`.
`ProState.afterEngineAck(productId, acknowledgedAt)` already existed, already took exactly §4.3.3's
two fields, and **had no caller**. That was the whole gap: the ack had a spec and a destination and
nothing in between.

Five spec rules are enforced, and each is a test rather than a comment:

| §4.3.3 rule | What the applier does |
| --- | --- |
| unknown `product_id` → ignore, don't unlock | returns `current` unchanged — **not** `Rejected` |
| `acknowledged_at` advisory (§6.3) | a 1999 timestamp still grants; nothing expires or re-locks |
| `order_id` optional | both vector bodies apply to an identical `Unlocked` |
| no negative form | `"revoked":true` in the body is inert |
| ack means granted, full stop | `apply()` has no downward path at all |

The last two are the ones worth the reviewer's time. §4.3.3 says a kind whose meaning depends on a
field inside its body is the parser hazard §4.2 exists to avoid — and here that hazard would sit on
the single path that turns a paid feature on. So the test asserts a decoy `"granted":false,
"revoked":true` body **still unlocks**, which reads wrong until you notice that the alternative is a
wire format where an attacker who can add a body field can un-grant a purchase.

One deliberate asymmetry, called out in the source: unknown **body** fields are *ignored* while §3
requires unknown top-level **envelope** fields to be *rejected*. The envelope is framing an attacker
reshapes; the body's guarantee is that nothing inside it can change what the kind means. Ignoring is
what makes that guarantee real.

The kind is also **re-checked** inside `parse()` rather than trusted from the receiver's return
value — a caller that dispatched on the wrong branch must not be able to unlock Pro with a
`heartbeat` that happens to carry a `product_id`. That is a test too.

### S5.B-3 What ran

Reduced `:core` harness, `--rerun-tasks`: **76 tests, 0 failures, 0 skipped**, `BUILD SUCCESSFUL`
— 67 + the 9 new. All nine `EntitlementAckTest` cases named `PASSED` in the output. The first run
did **not** pass: `e: EntitlementAckTest.kt:128 Argument type mismatch: actual type is
'Serializable', but 'ByteArray' was expected` — `"a" + "b".toByteArray()` binds as string
concatenation. Recorded because a green suite whose first run was red is a different artifact from
one that was green immediately.

### S5.B-4 The vectors are transcribed, not vendored, and that was the point

`entitlement-ack.json` and `entitlement-ack-no-order-id.json` postdate the vendored pin `679a317`
and live on the **unmerged** PR #32. Consuming them from `core/src/test/resources/` would mean
moving the pin to an unmerged branch commit — precisely the cross-repo drift `VECTORS.lock` exists
to prevent. So the two grant bodies are transcribed **verbatim** from `generate.mjs`'s
`plaintext_json`, with the reason written at the top of the test file.

`git status --porcelain` after the code commit: exactly two `??` lines, both new. **No vendored
vector's bytes were touched**, the pin is still `679a317`, and the repo still holds 26 of upstream's
28 — the gap being the two unmerged files, which is the pin working.

The formal vector-driven assertion — the `type`-filtered section in `ProtocolVectorsTest` beside
the others — is deferred to the re-vendor slice that follows #32 merging. `Sync-Protocol.md` §10.2
already states no consumer asserts against these vectors yet; **this entry does not change that and
does not claim to.**

### S5.B-5 B-6 re-verified, and it holds

The prompt asked for PQ-A2-3's `invalid-unknown-field` vector. It is still blocked, and the
diagnosis is now backed by two specific lines rather than a summary: `EnvelopeReceiver.Receive`
takes an already-parsed `ReceivedEnvelope` **record** (`src/Sync/EnvelopeReceiver.cs:33`), and the
harness builds that record by **cherry-picking named keys** out of the vector JSON
(`tests/SyncHarness/Program.cs:696`, `ToReceived`). An extra top-level field is therefore dropped on
the floor before any check runs — the envelope would be **accepted**, and a vector expecting
`decrypt_failed` would turn the gate red. Parser first, vector second. B-6 stands, unchanged.

### S5.B-6 What this iteration did not establish

The android verification command of record was **not run** — no SDK, and Google's Maven is
egress-denied. `Verify-Alpha.ps1` was **not run** — no .NET, no PowerShell. The **C# applier does
not exist**; S5 stays `PARTIAL` on the engine side and is still *unblocked, merely unwritten*. `:app`
was not touched, so nothing in the UI surfaces Pro yet — the applier has tests and no production
caller, which is honest for a `:core` unit and is not a shipped feature. The 17→21 toolchain
substitution means even the `:core` result is one JDK away from CI's; treat CI's run on this push as
the authoritative one.

### Boundary — what was not touched

**Nothing was merged, in either repo** — the android repo is never-self-merge and the main-repo
merge policy is conditional on a full local gate this machine cannot run. PR #32 stays a **draft**
and was not merged, retargeted or rebased. No force-push, no history rewrite, no branch deleted or
created beyond pushing this one. No existing vector's bytes changed and **nothing was re-vendored**;
`VECTORS.lock` was not edited. No `.cs`, `.ts`, Gradle, manifest or version-catalog file was
modified — this slice is two new Kotlin files plus records. No `:app` source, no screens, no Room
schema. No deploys of any kind (Cloudflare, Workers, relay, site, Pages); **the production relay was
contacted zero times, not even `GET /v1/health`**. No emulator, no `sdkmanager`, no AVD; no attempt
to route around the `dl.google.com` denial, which the proxy's own README says to report rather than
work around. No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing
code beyond the signed test vectors already in the repo; no email or Gmail anything; no cert-store,
MSIX or keystore action — the upload keystore and its password file were neither read nor referenced
beyond their paths. No secrets read, written or printed. Terra's worktree is on a machine this
session cannot reach; Terra's state was read at start and claims **no files**, so there was no
collision.

### S5.B-7 The gate result, which arrived after the records were written

CI run [31305289509](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31305289509) on
`a37c185` — job *Build and test*, **`success`**, 09:10:54 → 09:18:25 UTC. That is the
**authoritative** result for this slice and it supersedes the reduced probe as evidence: it ran on
`ubuntu-latest` with **JDK 17** (not the probe's 21) and a real Android SDK, so it executed the
things the probe structurally could not — `checkCoreIsAndroidFree`, the vendored-vector drift step
against pin `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug`, and the
release-classpath tracker check. A green job means every one of those steps passed.

Two things it settles, and one it does not:

- **The applier compiles and passes on the pinned toolchain**, not just on the substituted one. The
  17 → 21 gap named in S5.B-0 is closed by this run, and the probe's role drops back to what it was
  worth: a fast local signal.
- **The vendored pin is intact under CI's own check**, which is a different check from mine — it
  re-fetches each vector from the main repo at `679a317` through the contents API and diffs. My
  `git status` evidence and CI's fetch-and-diff agree.
- **It does not produce test counts.** Gradle does not print them and this workflow does not collect
  them, so the measured **76 / 0 / 0** for `:core` remains the probe's number, and `STATE.md`'s
  combined `:core` + `:app` figure stays **carried**. CI proves green; it does not prove the count.

An honest note on process: the two poll loops I first armed to watch this run were querying the
Actions API with a token that lacks `actions:read` and were returning `403 Resource not accessible
by integration` on every iteration — silently, because the loop only tested for `completed`. They
would have run to timeout reporting nothing. The result above was read through the GitHub MCP
check-runs endpoint instead. A watcher whose failure mode is silence is indistinguishable from a
watcher seeing nothing happen, which is the same class of error as a grep that only matches success.

## S4 (first half, phone side) — the pull decision, and a spec/engine mismatch it surfaced · 2026-08-09 · **PARTIAL**

Fourth cloud iteration of the day, Linux sandbox. The assigned slice was S5's first half
(§4.3.3, PQ-A2-1/-2/-3, the vectors). **That was already done** — twice over: the spec half landed
this morning in the main repo's draft PR #32, and the phone applier landed at midday on this
branch. Re-verified rather than assumed (C-S5B-6), and the prompt's own instruction to pick a
different rung if a better one fits was taken.

### S4.A-0 Why S4 and not the next line of the "next intent" list

`STATE.md`'s next-intent list had four items and **three of them cannot be started here**: the C#
`entitlement_ack` applier and the `/pair` route need .NET; B-6's inbound parser needs .NET; the
SDK checkbox is Brandon's. The re-vendor slice is gated on PR #32 merging, which this session may
not do.

S4 was labelled `BLOCKED — B-4` on the strength of "needs S3's device key + an emulator for the
claim to be E2E". That is true of the *E2E claim* and false of the rung's first two items, and the
difference is checkable rather than arguable:

- `pull_request` is **not** in `Protocol.STATE_CHANGING_KINDS` — on either side
  (`Protocol.kt:52`, `src/Sync/Protocol.cs:52-55`). §5.4 therefore requires no envelope signature,
  `OutboundEnvelopeFactory.build` emits it with `signer = null`, and the pull loop is **not**
  downstream of S3's Keystore key at all.
- Both halves it needs already exist in `:core`: `EnvelopeReceiver.receiveWire` and
  `OutboundEnvelopeFactory.pullRequest`.

So the decision layer between them is pure Kotlin/JVM, and `:core` is the module a cloud session
*can* run (B-7's positive half). The E2E proof stays blocked; the decision does not.

### S4.A-1 Baseline first

`:core:test` on the untouched branch `db4ec49`, reduced probe: **76 tests, 0 failures, 0 skipped**,
`BUILD SUCCESSFUL`. Same probe shape as S5.B and labelled the same way — `:core` only, toolchain
substituted 17 → 21 because `api.foojay.io` is egress-denied (B-7). It is **not** the verification
command of record: no `checkCoreIsAndroidFree`, no `:app:test`, no `assembleDebug`, no `lintDebug`.

### S4.A-2 What was actually missing

Two rules, both normative, both with **no implementation and no test in either codebase**:

1. `ApplyResult.AwaitingSnapshot` was produced by `EnvelopeApplier` (`:app`) and **dropped by every
   caller**. Its own doc comment says "the caller should ask the engine to re-publish"; no caller
   did. S4's mission text names this exactly: "*currently returned and ignored*".
2. §6.2's "treat a large gap as a signal to request a fresh `snapshot`, not as an error" had
   nothing behind it anywhere — not in `:core`, not in `:app`, not in the engine, not in a vector.

`PullPolicy` is the missing decision: pull-on-open, ask when a delta is refused for want of a
snapshot, ask on a large gap, stay quiet otherwise.

### S4.A-3 The finding: `since_seq` is specified as resumable and implemented as "send everything"

This is the part worth an auditor's time, and it changed the design.

§4.3 describes `pull_request` as "ask the engine to re-publish **from a sequence point**". The
engine parses the field and then discards its meaning: `InboundDispatcher.cs:105-111` reads
`since_seq` and passes it to `ISnapshotRepublisher.RepublishSnapshotAsync(since, ct)` — and
**every implementation of that interface ignores the argument**. `LiveRepublisher`
(`tests/SyncLiveSmoke/Program.cs:311-312`) calls `PublishSnapshotAsync(...)` unconditionally;
`RecordingRepublisher` (`tests/SyncHarness/Program.cs:756-759`) only records the value. There is
no shipping code path in which `since_seq` changes what is sent (C-S4A-3).

So in v1 `pull_request` means exactly one thing — *send me a snapshot*.

The phone therefore sends `since_seq: 0`, always, for every reason. The alternative — report the
real high-water mark, which **looks** more honest — is the trap: it encodes a request the current
engine ignores but a future one might honour, and if honoured the §6.2 gap case would come back as
*deltas resuming after N* when §6.2 explicitly wants a snapshot. Zero is the only value that means
"I hold nothing usable" to both the engine that exists and the engine that might. This is the
mission's interpretation rule applied literally: match the engine, record the question. Recorded as
**PQ-S4-1**, with both ways to close it and a recommendation.

A test pins the zero, so a later "improvement" has to argue with a failing test rather than a
comment.

### S4.A-4 The second thing two fields prevent

`ReplicaPosition` carries `snapshotSeen` **separately** from `highestAppliedSeq` because the two
genuinely disagree: `EnvelopeApplier` gates only `delta` on `snapshotSeen`, so a `heartbeat` or
`evidence` payload applies on a phone whose dashboard is empty and advances the mark. A policy
keyed off the sequence number alone would reason "I am at seq 5, I must have state", stay silent
forever, and leave the phone showing demo data indefinitely — nothing forces a delta to arrive from
an idle engine. It is the same shape as the `snapshotSeen`-arrives-as-0 assertion B-5's migration
test was written for: a number that implies state the replica never received.

Two other decisions carry tests rather than comments. The policy **latches** — one gap produces one
ask, not one per envelope, because a stalled sync answered with a burst of traffic is a worse
failure than a slow one — and `MALFORMED` deliberately asks **nothing**, because a re-publish would
reproduce the same bytes and convert a parse defect into an unbounded request loop.

### S4.A-5 What ran

Reduced `:core` probe, `--rerun-tasks`: **93 tests, 0 failures, 0 skipped**, `BUILD SUCCESSFUL` —
76 + 17 new, all 17 `PullPolicyTest` cases named `PASSED`. Unlike S5.B's applier, this one **passed
on its first run**; recorded because "green immediately" and "green after a fix" are different
artifacts and the previous entry set the precedent of saying which.

`git status --porcelain` after the code commit: exactly two `??` lines, both new. **No vendored
vector's bytes were touched**, `VECTORS.lock` was not edited, the pin is still `679a317`, and the
repo still holds 26 of upstream's 28 (C-S4A-5).

### S4.A-6 What this does NOT establish, stated before anyone infers it

`PullPolicy` has **no production caller**. `grep -rn "PullPolicy\|ApplyDisposition" app/src/`
returns nothing, and that is the honest status: the mapping from `:app`'s `ApplyResult` onto
`ApplyDisposition`, the relay push of the resulting envelope, the `:app` Ktor engine dependency
(3.1.3) and the WSS route are all unwritten, and the E2E proof needs an emulator (**B-4**) plus a
toolchain this sandbox cannot fetch (**B-7**). C-S4A-7 is written as the command that must start
returning hits before anyone may call S4 `DONE`.

S4 therefore moves **BLOCKED → PARTIAL**, which is a correction to a label rather than progress
against the blocker: B-4 and B-7 are untouched and still block everything S4 needs to *prove*. The
label was wrong in the direction that costs work — it told the next session there was an obstacle
in front of a decision layer that had none.

### Boundary — what was not touched

**Nothing was merged, in either repo** — the android repo is never-self-merge and the main-repo
merge policy is conditional on a full local gate this machine cannot run. PR #6 stays a **draft**;
careerseeker PR #32 stays a draft and was not merged, retargeted, rebased or even checked out for
writing. No force-push, no history rewrite, no branch created, moved or deleted beyond pushing this
one. No existing vector's bytes changed, **nothing was re-vendored**, `VECTORS.lock` untouched. No
`.cs`, `.ts`, Gradle, manifest or version-catalog file was modified — this slice is two new Kotlin
files plus records. **No `:app` source, no screens, no Room schema, no `docs/Sync-Protocol.md`**
(normative; this repo never edits it). No deploys of any kind (Cloudflare, Workers, relay, site,
Pages); **the production relay was contacted zero times, not even `GET /v1/health`**; no relay code
ran at all this iteration. No emulator, no `sdkmanager`, no AVD, and no attempt to route around the
`dl.google.com` denial, which the proxy's own README says to report rather than work around. No
Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email or
Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password file
were neither read nor referenced beyond their paths. No secrets read, written or printed. Terra's
state was read at iteration start and claims **no files**, so there was no collision; Terra's
worktree is on a machine this session cannot reach.

### S4.A-7 The gate result, which arrived after the records were written

CI run [31315292165](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31315292165) on
`044d829` — job *Build and test*, **`success`**, 13:14:40 → 13:21:05 UTC. That is the
**authoritative** result for this slice and it supersedes the reduced probe: it ran on
`ubuntu-latest` with **JDK 17** (not the probe's 21) and a real Android SDK, so it executed the
steps the probe structurally could not — `checkCoreIsAndroidFree`, the vendored-vector drift check
against pin `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug`, and the
release-classpath tracker check. A green job means every one of those passed.

Three things it settles, and one it does not:

- **`PullPolicy` compiles and passes on the pinned toolchain**, not just the substituted one. The
  17 → 21 gap named in S4.A-1 is closed by this run; the probe drops back to being a fast local
  signal and nothing more.
- **`:core` is still Android-free under the real check**, which is a different check from my grep —
  it walks every `.kt` file and also scans `core/build.gradle.kts`'s `plugins {}` block.
- **`:app` still builds and lints clean** even though this slice did not touch it. Worth stating
  because adding a public type to `:core` is exactly the kind of change that can break a downstream
  module without the author noticing.
- **It does not produce test counts.** Gradle does not print them and this workflow does not
  collect them, so the measured **93 / 0 / 0** for `:core` remains the probe's number and
  `STATE.md`'s combined `:core` + `:app` figure stays **carried**. CI proves green; it does not
  prove the count.

An honest note on process, continuing the one the previous entry started. The Actions REST API is
still unusable from here — it needs `actions:read` and returns `403 Resource not accessible by
integration` without it — so this result was read through the GitHub MCP check-runs endpoint again.
The first poll after pushing the records commit returned `total_count: 0` rather than the run I was
watching: the endpoint reports check runs **for the PR's current head**, and pushing had moved the
head from `3e1e51a` to `044d829`, retiring run `31315093971` mid-flight and queuing
`31315292165` in its place. A poll loop keyed on "is it complete yet" would have read that empty
result as "no answer yet" and, if the push had been the last one, waited forever. Same failure
shape as the previous iteration's silent 403: the loop's idea of *nothing to report* and the API's
idea of *nothing here* are indistinguishable unless you check which commit you are asking about.

---

## S6 (phone half) — a mark that nothing ever acknowledges · 2026-08-09 · **PARTIAL**

Fifth cloud iteration of the day, Linux sandbox. **The assigned slice was already done**, so this
one took a different rung; the justification is S6.A-0 and it is the first thing to check if you
think this iteration went off-mission.

### S6.A-0 Why this is not the assigned slice

The iteration prompt assigned S5's first half: §4.3.3's `entitlement_ack` body, PQ-A2-1, PQ-A2-2,
PQ-A2-3's `invalid-unknown-field` vector, and `generate.mjs --check`. Read after the mandatory
fetch, **all of it is already landed or already known-impossible**:

- §4.3.3 + PQ-A2-1 + PQ-A2-2 + two generated vectors are in the main repo's **draft PR #32**
  (`claude/s5-entitlement-ack-spec`, commits `8575539` and `22b028e`, `origin/main..` measured this
  session). The prompt's own ladder summary says "S5 … NOT STARTED"; the repo disagrees, and the
  repo wins.
- PQ-A2-3's vector is **B-6**, and adding it is the documented trap: the engine has no inbound
  wire-JSON parser — `EnvelopeReceiver.Receive` takes an already-parsed record and the harness's
  `ToReceived` cherry-picks named keys — so an unknown top-level field is dropped *before* any
  check runs. The engine would **accept** the envelope and the vector would turn the offline gate
  red. Parser first, vector second; the parser is C# and there is no .NET here.

So the prompt's slice was zero real work plus one action that would break CI. The rung actually
picked is the one `STATE.md` had already nominated in writing — "**S6 is the next candidate for the
same re-read**" — after S4 turned out to be mislabelled `BLOCKED` over a decision layer that needed
no emulator. S6 is the same shape, and it is the topmost rung with a half that is genuinely
verifiable here.

### S6.A-1 Baseline first

Reduced `:core` probe on the untouched branch at `66bf167`: **93 tests, 0 failures, 0 skipped**,
`BUILD SUCCESSFUL`. That matches the number `STATE.md` already carried, which is the point of
measuring it — a baseline that agrees with the record is evidence the record is live.

One mechanical note for whoever rebuilds the probe: **Gradle 9 removed `-c` / `--settings-file`**,
so the previous iteration's recipe ("a throwaway `settings.gradle.kts` in the scratchpad") no
longer works as a flag. The probe now has to be a separate Gradle root that points `:core`'s
`projectDir` at the real module and declares the version catalog explicitly. The toolchain override
also has to run in `afterEvaluate` — a `plugins.withId` block fires *before* `core/build.gradle.kts`
applies its own `jvmToolchain(17)`, which then overwrites the override and the build fails looking
for a JDK 17 it cannot download (`api.foojay.io` is denied). Full recipe in C-S6A-1.

### S6.A-2 The finding, and it is the reason the rung is worth doing at all

**`outcome` is the only state-changing phone→engine kind with no acknowledgement of any sort.**

§4.3's engine→phone table acks exactly two things: `conflict` rejects a `doc_edit`,
`entitlement_ack` confirms an `entitlement`. There is no `outcome_ack` and no rejection kind for
`outcome`; `grep -n "outcome_ack" docs/Sync-Protocol.md` returns nothing (C-S6A-3).

The engine side is worse than silence. `InboundDispatcher.cs:98-103`:

```csharp
case "outcome":
{
    if (_outcomeApplier is not null)
        await _outcomeApplier.ApplyAsync(BodyJson(received.Plaintext!), _deviceFingerprint, ct)...;
    return new InboundResult(InboundOutcome.OutcomeApplied, null, received.Kind);
}
```

The `return` is **outside** the guard, and `IOutcomeApplier` is nullable by design — its own
doc-comment says "a null applier means outcome dispatch is a no-op seam for now". So the engine can
accept a signed `outcome`, do nothing, and report `OutcomeApplied` (C-S6A-4). Nothing goes back to
the phone claiming that, so it is not a wire-level lie — but not even an engine-side caller can
tell applied from dropped.

**And the phone cannot fall back on ordering.** §6.1: "Each direction has an independent counter
starting at 1." The e2p seq carrying a `snapshot` and the p2e seq carrying the mark are different
counters, and §4.3.1's application summary — `{id, state, company, title, score, outcome?}` — has no
per-application timestamp. A payload that arrives after a mark may have been generated before it,
and **there is nothing in v1 that says which** (C-S6A-5).

Recorded as **PQ-S6-1** with both closure options. Option (a), a real `outcome_ack`, is
recommended; unlike PQ-S4-1 the cheap option is not clearly the right one, and the write-up says so
rather than picking the convenient answer.

### S6.A-3 What the finding forces

`OutcomeMarkPolicy` (`core/.../OutcomeMarking.kt`). A pending mark **shadows** the engine's carried
value; the shadow retires on **value convergence** — the engine reporting that value in a later
§4.3.1 payload — and is **bounded** by a count of disagreeing payloads (`disagreementLimit`,
default **3**, chosen and labelled as chosen).

Both halves are forced, and it is worth stating why neither simpler rule survives:

- **Engine wins on arrival** → the badge reverts under the user's finger for a mark that is merely
  in flight. To the user that is indistinguishable from "it didn't save", and they will tap again.
- **Mark wins forever** → with no ack, a mark the engine silently dropped displays as the user's
  truth permanently. That is the project's own fabrication shape, turned around to point at the
  user instead of at the engine, and it is the worse of the two.

The bound counts **reports, not seconds**, because §6.3 makes clocks untrustworthy and a disagreeing
report is the only monotone evidence the phone actually holds. `DisplayedOutcome.pending` is what
keeps the compromise honest — the UI must render an unconfirmed mark differently from a confirmed
one, and the flag exists so that is a compile-time obligation rather than a note.

### S6.A-4 Three smaller decisions, each a test rather than a comment

1. **Pro gating reaches the mark, not just the screen.** `ProState.Unlocked` alone may mark;
   `AwaitingEngine` may not. The state after forwarding a receipt is deliberately not an optimistic
   unlock, and letting a user mark during the round trip would spend signed, sequence-burning
   envelopes on an unproven entitlement.
2. **`no_reply` is renderable but never offerable.** §4.3.1's carried superset has six values;
   §4.3's phone-settable subset has five. `Outcome` cannot represent `no_reply` at all — the type is
   the guard — and `offerFor` is where that guarantee becomes visible to a screen that might
   otherwise build its buttons from whatever the engine last reported.
3. **A re-mark collapses.** §4.3.1 makes the carried outcome latest-wins *state*, not an event log,
   so two taps on one application before either leaves the phone are one intention and must cost
   one §6.1 sequence number, not two. The entry moves to the end of the queue: newest decision,
   newest thing to send.

A failed push is explicitly **not** a disagreement — that bound measures the engine's opinion, and
an envelope that never arrived is not the engine's opinion about anything. `onSent` and
`onSendFailed` are deliberately empty and say so in their bodies: reaching the blind relay is not
the engine applying anything, and the decision *not* to change state there is worth a name so it can
be tested and cited rather than "fixed" later.

### S6.A-5 What ran

Reduced `:core` probe, `--rerun-tasks`: **115 tests, 0 failures, 0 skipped**, `BUILD SUCCESSFUL` —
93 + 22 new, all 22 `OutcomeMarkPolicyTest` cases named `PASSED`. **Passed on the first run**; no
test was adjusted to make it green. Recorded because the precedent in this log is to say whether a
slice was green immediately or green after a fix.

`git status --porcelain` after the code commit: exactly two `??` lines, both new. The vendored
vectors were checked against the pin the hard way rather than trusted — `git archive 679a317
docs/sync-vectors/v1` out of the main repo, `diff -r` against the vendored copy: **26 files, 0
differences** (C-S6A-7). `VECTORS.lock` untouched, pin still `679a317`, nothing re-vendored.

### S6.A-6 What this does NOT establish

`OutcomeMarkPolicy` has **no production caller**. `grep -rn "OutcomeMarkPolicy\|MarkDecision\|
DisplayedOutcome" app/src/` returns nothing, and that is the honest status: no screen offers the
control, no transport pushes the envelope, and the send path needs a §5.4 device signature from an
Android Keystore key that does not exist until S3 — which needs an emulator (**B-4**) — on a
toolchain this sandbox cannot fetch (**B-7**). C-S6A-8 is written as the command that must start
returning hits before anyone may call S6 `DONE`.

S6 therefore moves **BLOCKED → PARTIAL**, and that is a **label correction, not progress against
the blocker**: B-4 and B-7 are untouched and still block everything S6 needs to *prove*. This is
now the second rung in two iterations found carrying a blanket `BLOCKED` over a half that had no
blocker. The pattern is worth naming: a rung's blocker applies to the claims that depend on it, not
to the rung's name.

**The android gate did not run and could not.** No Android SDK; `dl.google.com` is an egress policy
denial. The 115/0/0 is a reduced JDK-21 probe, not the gate. CI on `ubuntu-latest` with JDK 17 is
the gate, and at the time of writing it has not reported — C-S6A-9 is recorded as an **open**
claim rather than a passing one.

### Boundary — what was not touched

**Nothing was merged, in either repo** — the android repo is never-self-merge and the main-repo
merge policy is conditional on a full local gate this machine cannot run. PR #6 stays a **draft**;
careerseeker PR #32 stays a draft and was not merged, retargeted, rebased, or checked out for
writing. No force-push, no history rewrite, no branch created, moved or deleted beyond pushing this
one and the docs-only coordination branch. **No existing vector's bytes changed, nothing was
re-vendored, `VECTORS.lock` untouched** — and no *new* vector was added either, because the only one
outstanding is B-6's and it would turn the gate red. No `.cs`, `.ts`, Gradle, manifest or
version-catalog file was modified; **`docs/Sync-Protocol.md` was read but never edited** — it is
normative and this repo does not write it. No `:app` source, no screens, no Room schema. No
`$ExpectedOfflineTotal`, no `Verify-Alpha.ps1`, no harness, no count-reporting doc in the main repo.
This slice is two new Kotlin files plus records.

No deploys of any kind (Cloudflare, Workers, relay, site, Pages); **the production relay was
contacted zero times, not even `GET /v1/health`**; no relay code ran. No emulator, no `sdkmanager`,
no AVD, and no attempt to route around the `dl.google.com` denial — the proxy's own README says to
report it rather than work around it, and the two toolchain workarounds used (a probe root, a
toolchain override) are local substitutions that are labelled as such everywhere they are cited,
not egress evasion. No Google, Play, OAuth or Console action; no accounts, no purchases, no Play
Billing code; no email or Gmail anything; no cert-store, MSIX or keystore action — the upload
keystore and its password file were neither read nor referenced beyond their paths. No secrets read,
written or printed. Terra's state was read at iteration start and claims **no files**, so there was
no collision; Terra remains R6(b) BLOCKED on draft PR #26.

---

## S2/S5 (relay half) — the transport refused envelopes the protocol declares legal · 2026-08-09 · **B-2 narrowed**

Sixth cloud iteration of the day, Linux sandbox. The slice landed in `careerseeker`, on the
existing draft [PR #32](https://github.com/ShivaClaw/careerseeker/pull/32) rather than a new branch,
because it finishes something #32 started and touches the same normative file. Nothing in this repo
changed but records.

### S2R-0 Why this rung, and why not the assigned one

The scheduled prompt assigned S5's first half — §4.3's `entitlement_ack` body, the vectors,
PQ-A2-1/-2/-3. **That is the fourth consecutive prompt to assign work already finished**: the spec
half landed in #32 this morning, PQ-A2-1 and PQ-A2-2 closed with it, and PQ-A2-3 is **B-6**, where
adding the vector the prompt asks for turns the offline gate red because the engine has no inbound
wire-JSON parser. Verified before disregarding, not assumed:
`node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the generator.`

So the standing instruction applied again: take the topmost rung genuinely verifiable here. Three of
`STATE.md`'s four next-intent items need .NET or an Android SDK (`dotnet` is not on this machine —
`which dotnet` → not found) and the fourth is Brandon's SDK checkbox. That leaves the module this
sandbox *can* run and that no iteration had revisited: **`relay/`**, Node + vitest + miniflare,
32/32 green at baseline.

**S2 is the topmost rung that is not DONE**, and B-2's remaining gap is the `/pair` page — C#, and
unreachable here. But B-2 is not all of S2, and the pattern these records have now named twice
applies a third time: *a rung's blocker applies to the claims that depend on it.* The relay is S2's
transport, it is in my declared territory, and it had never been read against the amendment #32 made
to §3.1 this morning.

### S2R-1 The finding, and it is a correction to my own work

§3.1 as amended says the 1 MiB cap is on the **decoded ciphertext**. The relay cannot decode — it
holds no key, by design — so its guard counted base64url characters. Against a constant named
`MAX_ENVELOPE_BYTES`. **A character count tested against a byte budget.**

Base64url expands by 4/3, so the guard capped the *decoded* payload at 786,432 bytes and left the
top **256 KiB** of §3.1's declared range untransmittable. Measured, not inferred — a throwaway
vitest probe against the local Worker under miniflare:

```
MAX_ENVELOPE_BYTES        = 1048576
b64u chars for 1 MiB      = 1398102
relay accepts b64u chars <= 1048576
=> max decoded bytes relay will carry = 786432

PROBE A status = 413 {"error":"too_large"}      <- ciphertext of exactly 1 MiB decoded: LEGAL by §3.1
PROBE B status = 201                            <- 786,432 decoded: the real ceiling
```

The probe file was deleted after it did its job; the same assertions live in the suite now, pointed
the right way round.

**What makes this worth the entry is where the error came from.** PQ-A2-1's close — written by an
earlier iteration of me, this morning — noticed the relay's test was *stricter* and concluded "so
there is no gap". The implication is true. The conclusion does not follow: it checks one direction
of a two-directional relationship and reports the question closed. The gap runs the other way, and
running the other way took one command.

### S2R-2 Latent, not live, and the record says which

Nothing sends envelopes near either number. §4.4 chunking is unimplemented in **both** codebases
(`grep -rn "chunk" src/Sync/*.cs` → nothing), and snapshots are orders of magnitude smaller. **No
field incident was ever possible from this**, and calling it a live bug would be the same
overclaiming this log corrected itself for in the merge-topology entry.

It is worth fixing before it is reachable because §4.4 tells a future chunker to split against "the
envelope limit" — §3.1's number — which is exactly the value that does not fit. The first
correctly-implemented chunker meets a 413, with the relay, the spec and both receivers each
individually defensible.

### S2R-3 What landed

`MAX_CIPHERTEXT_B64U_CHARS = ceil(4/3 × MAX_ENVELOPE_BYTES)` in `relay/src/protocol.ts`, derived and
documented as never-to-be-re-spelled-as-a-round-number; `MAX_PUSH_BODY_CHARS` follows it; both
applied in `relay/src/channel.ts`. §3.1 now states the conversion as **normative** — the relay MUST
carry every envelope §3.1 declares legal — and says why a conservative-looking round number in the
relay is still a bug: a sender obeying §3.1 and §4.4 cannot discover it except as a 413.

**Both guards moved strictly looser.** Nothing the relay accepted before is rejected now, which is
why PR #31's engine↔relay 30/30 proof cannot regress on this change — worth stating because a
size-guard edit is exactly the shape of change that silently breaks a proven path.

The suite's `1 MiB + 1 chars → 413` case was **pinning the bug in place** and is gone. Four replace
it: the derivation itself, the maximum legal envelope surviving a push/pull round trip read as
*text* (the assertion that actually proves storage did not truncate a 1.4 MiB row), the first
character past the cap, and the body guard firing before any parse.

### S2R-4 What ran

```
npx vitest run     ->  Test Files 1 passed (1)   Tests 36 passed (36)      (32 before)
npx tsc --noEmit   ->  clean, exit 0             (after `wrangler types`)
node docs/sync-vectors/generate.mjs --check  ->  OK: 28 vector files match the generator.
```

Green on the first run of the final code; the one intermediate failure was **my test being wrong,
not the fix** — I asserted `pull`'s `envelopes[0]` was a string when it is spliced in as parsed
JSON. Recorded because the precedent here is to say whether a slice was green immediately.

**`Verify-Alpha.ps1` did not run and cannot** — no .NET on this machine. It also cannot be affected:
this slice touches no `.cs`, no harness, no vector byte and no count-reporting doc, so
`$ExpectedOfflineTotal` (598) is untouched by construction. CI on `windows-latest` is the gate.

### S2R-5 An open claim closed for free

`AUDIT-REQUEST.md` C-S6A-9 was left **open** by the previous iteration: the android gate had not
reported on the S6 push. It has now. Run
[31325873134](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31325873134), job
*Build and test*, **conclusion `success`**, on head `9f73226` — which is the commit that carried
`OutcomeMarkPolicy`. C-S6A-9 is therefore **closed green**, and S6's marking decision is now
gate-verified rather than probe-verified.

### Boundary — what was not touched

**Nothing was merged, in either repo.** PR #32 stays a **draft** and was not merged, retargeted or
force-pushed; PR #6 stays a draft. No branch was created, deleted or rewritten — two commits were
appended to an existing branch of mine and pushed forward-only. No force-push, no history rewrite.

**No `.cs` file, no harness, no `$ExpectedOfflineTotal`, no `Verify-Alpha.ps1`, no count-reporting
doc.** **No vector's bytes changed** — `generate.mjs --check` proves 28/28 still match, and no
vector was added, because the only one outstanding is B-6's and it would turn the gate red. Nothing
was re-vendored; the android pin stays `679a317` and this repo's `core/src/test/resources/` was not
opened for writing. No `:core`, no `:app`, no Kotlin, no Gradle, manifest or version-catalog file —
**this iteration wrote no Kotlin at all.**

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages). `wrangler` was invoked exactly
once, as `wrangler types` — local type codegen from `wrangler.jsonc`, with `WRANGLER_SEND_METRICS=false`,
no account touched and nothing published. **The production relay was contacted zero times, not even
`GET /v1/health`.** Every relay run was miniflare, in-process, on localhost.

No emulator, no `sdkmanager`, no AVD, no attempt to route around the `dl.google.com` denial. No
Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email or
Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password file
were neither read nor referenced beyond their paths. No secrets read, written or printed. Terra's
state was read at iteration start: still R6(b) BLOCKED on draft PR #26, heartbeat unchanged at
2026-08-07T21:18, **claims no files** — no collision, and `relay/` was never Terra's territory.

---

## S4 spec half — `pull_request` is a snapshot request · 2026-08-10 (seventh cloud iteration, Linux sandbox)

**The slice the schedule proposed was already done.** The standing prompt nominated S5's spec half —
amend §4.3 for `entitlement_ack`, add the vector, close PQ-A2-1/-2/-3 — and named S5 as "NOT
STARTED". Fetching first and reading the records showed otherwise: §4.3.3 and the two ack vectors
landed on 2026-08-09 (PR #32, still an unmerged draft), PQ-A2-1 was closed, re-opened and re-closed
the same day, PQ-A2-2 was closed, and the *phone* applier landed with 9 tests. Redoing it would have
produced either a no-op or a duplicate section. **PQ-A2-3 remains open on purpose** — it is B-6, and
the reason is engine-side (below).

So the rung actually available was the topmost one with a half that runs on this machine, and the
records already named it: **PQ-S4-1**, S4's open spec question, whose recommended answer is pure
documentation of behaviour that both codebases already ship.

### S4S-1 What the question was, and why option (a) is not a compromise

§4.3 promised `pull_request` would "ask the engine to re-publish **from a sequence point**." No
implementation has ever done that. The engine parses `since_seq` and hands it to
`ISnapshotRepublisher.RepublishSnapshotAsync(sinceSeq)`; **both** implementations of that interface
ignore the argument — `LiveRepublisher` republishes unconditionally, `RecordingRepublisher` only
records the value so the harness can assert it round-tripped. The phone always sends `0`.

The tempting reading is that the spec describes a feature and the code has not caught up. It does
not. **Resumption contradicts §6.2**, which defines a large gap as a signal to request *a fresh
`snapshot`* — and a resumable pull has no way to express "start over". The two features want the
same kind to mean opposite things, so implementing the spec as written would have made §6.2
unsatisfiable, not completed it. Documenting what ships is the correct direction here, and the fact
that **neither side needed a line changed** is the evidence.

### S4S-2 What landed

Main repo, `claude/s4-pull-request-semantics` (stacked on PR #32), commit `9399d11`, **74 insertions
/ 2 deletions in one file**:

- §4.3's row: a whole-snapshot request; `since_seq` **reserved**, MUST be `0`, MUST be ignored.
- New **§4.3.4** with the body, the three MUSTs, the measured engine/phone behaviour at file:line,
  why a v2 wanting resumption needs a new shape rather than a widened field, and the field-name
  collision with §4.3.1's `delta.since_seq` — same name, different field, and *that* one is live.
- §6.2: the "large gap" threshold is **receiver policy**, v1 pins no number, and a receiver SHOULD
  say whether its value was measured or chosen.
- §9's amendments table: two rows, so the change is auditable rather than silent.

**One rule was added beyond what PQ-S4-1's option (a) asked for**: a receiver MUST NOT *reject* a
non-zero `since_seq`. "Reserved" invites validation, and this is a reserved **field**, not one of
§4.3's reserved **kinds**, which MUST be rejected as `unknown_kind`. Rejecting an unknown kind
refuses traffic v1 cannot understand; rejecting this field would refuse a request v1 understands
perfectly and stall the stream on a forward-compatible sender. §4.3.4 states the asymmetry rather
than leaving a reader to derive it, because deriving it wrong is silent.

### S4S-3 The vector that was deliberately not added

A `pull_request` vector was considered and rejected, and the reasoning is worth keeping because it
is the second time this iteration series has had to reason about the drift trap.

§4.3.4's content is three **behavioural** MUSTs. A static vector cannot observe that a receiver
answered with a snapshot, nor that it declined to reject — so the vector would have pinned a body
shape (`{since_seq: 0}`) that nothing disputes while testing none of the rules that matter.

And it would not have been free. `SyncHarness` **enumerates** every `type: "envelope"` vector on
disk (`tests/SyncHarness/Program.cs:59-62`, then the "classifies every vector correctly" loop), so
an envelope-typed addition adds assertions and moves `$ExpectedOfflineTotal` — **a number this
sandbox cannot measure, having no .NET.** That is exactly the CLAUDE.md trap: the next session's
gate goes red on a count nobody could compute. The S5 ack vectors escaped it only by carrying a new
`type` the enumeration skips, which is a mechanism, not a general licence.

Zero cost, zero value, non-zero risk. Not added — and recorded as a decision rather than an
omission, so the next session does not "finish" it.

### S4S-4 A finding, on a path being verified rather than written

Checking that the engine really ignores `since_seq` surfaced something else at the same lines.
`InboundDispatcher.cs:105-111` returns `SnapshotRepublished` **outside** its `if (_republisher is
not null)` guard — the same shape PQ-S6-1 records for `case "outcome"`, now on a second kind. An
engine with the documented-inert seam accepts a `pull_request`, republishes nothing, and reports a
snapshot it never sent.

**It is milder than the `outcome` case, and a future fix should not flatten the two.** A dropped
`outcome` loses a user's mark and the phone shows it as truth — which is why `OutcomeMarkPolicy`
bounds its shadow. A dropped `pull_request` loses only a request the phone re-issues on the next
open. What they share is the defect: an `InboundOutcome` that reports *reaching a case* rather than
*completing an action*. Recorded as an extension to PQ-S6-1, not as a new question, because it is
the same fix twice. **Not fixed — C#, no .NET here.** Unblocked, merely unwritten.

### S4S-5 What ran

```
node docs/sync-vectors/generate.mjs --check   ->  OK: 28 vector files match the generator.   (exit 0)
:core reduced probe (C-S6A-1 recipe)          ->  BUILD SUCCESSFUL in 1m 32s
JUnit XML totals                              ->  115 tests, 0 failures, 0 skipped
git diff --stat (vs the S5 branch)            ->  docs/Sync-Protocol.md | 74 ++++--, 1 file changed
```

`:core` is **unchanged at 115** — correct for a documentation slice, and run anyway because the
amendment's central claim is that the phone already conforms; a claim about code deserves the code
being executed, even when the prediction is "nothing moved".

**`Verify-Alpha.ps1` did not run and cannot** — no .NET on this machine. It also cannot be affected:
no `.cs`, no harness, no vector byte, no count-reporting doc, so `$ExpectedOfflineTotal` (598) is
untouched by construction. **The android gate did not run and cannot** — no SDK, no JBR, and
`dl.google.com` is a policy denial (B-7). CI is the gate for both; at the time of writing it has not
yet reported on this branch, and C-S4S-7 is written to be checked rather than assumed.

### S4S-6 Ladder effect, stated narrowly

**S4 does not become DONE.** Its remaining half is `:app` transport wiring that needs a toolchain
this sandbox cannot fetch, and the E2E proof additionally needs B-4. What closed is **PQ-S4-1**, an
open cross-implementation ambiguity attached to S4 — the risk that a third implementation reads
§4.3, implements resumption, and finds the phone's `0` asking for full history on every reconnect
while the engine never depended on the field. That risk is now gone from the document. The rung
stays **PARTIAL**.

### Boundary — what was not touched

**Nothing was merged, in either repo.** PR #32 stays a draft and was neither merged, retargeted nor
force-pushed; PR #6 stays a draft. The new branch was created and pushed **forward-only** — no
force-push, no history rewrite, no branch deleted.

**No `.cs` file, no harness, no `$ExpectedOfflineTotal`, no `Verify-Alpha.ps1`, no count-reporting
doc.** **No vector's bytes changed and no vector was added** — `generate.mjs --check` proves 28/28
still match. Nothing was re-vendored; the android pin stays `679a317` and
`core/src/test/resources/` was not opened for writing. **No Kotlin, no `:app`, no Gradle, manifest
or version-catalog file** — `:core` was compiled and tested, never edited. No `relay/` file: the
relay is blind to payload kinds, so `pull_request` semantics do not reach it, and `npm`, `vitest`,
`wrangler` and miniflare were not invoked at all this iteration.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages). **The production relay was
contacted zero times, not even `GET /v1/health`.** No emulator, no `sdkmanager`, no AVD, no attempt
to route around the `dl.google.com` denial. No Google, Play, OAuth or Console action; no accounts,
no purchases, no Play Billing code; no email or Gmail anything; no cert-store, MSIX or keystore
action — the upload keystore and its password file were neither read nor referenced beyond their
paths. No secrets read, written or printed. Terra's state was read at iteration start: still R6(b)
BLOCKED on draft PR #26, heartbeat unchanged at 2026-08-07T21:18, **claims no files** — no
collision, and `docs/Sync-Protocol.md` has been my territory since S1.

---

## S4 transport half — the loop's decisions, moved where they can be tested · 2026-08-10 (eighth cloud iteration, Linux sandbox)

**The environment was probed before the rung was picked, because the records' own next-intent list
is entirely out of reach here.** Every item on it needs .NET (S5's C# applier, S2's `/pair` page,
B-6's parser), an Android SDK (S4's `:app` wiring), or an AVD (S6's signed send). Measured this
session rather than carried:

```
dotnet, adb, sdkmanager, pwsh                 ->  absent
java 21, node 22.22.2, gradle 9.6.1           ->  present
https://repo.maven.apache.org/... (Kotlin)    ->  200
https://dl.google.com/... (AGP 9.3.0)         ->  curl: (56) CONNECT tunnel failed, response 403
https://api.foojay.io/...  (JDK provisioning) ->  curl: (56) CONNECT tunnel failed, response 403
```

So B-7 holds, re-measured, and the lanes that work here are the ones the records already name:
`:core`, `relay/`, `generate.mjs`, docs.

### S4T-1 The slice, and why it is not the one the schedule proposed

The standing prompt again nominates S5's spec half. That landed 2026-08-09 (PR #32) and the
seventh iteration already recorded the correction — the prompt is a stored snapshot and does not
re-read itself. Nothing was redone.

The rung actually available was **S4's remaining half**, and the records described it as "`:app`
wiring". Reading the four steps they list makes clear that only one of them is wiring:

> map `ApplyResult` → `ApplyDisposition`; call `PullPolicy.onOpen` when the transport opens and
> `onEnvelope` per envelope; push the resulting `pullRequest(0, ts)` through `RelayClient`; call
> `onRequestFailed()` when that push fails.

Those are **ordering rules**. Each one has a wrong version that compiles, renders correctly, and
reports nothing — and written in `:app` they can only be exercised by a machine with an Android
SDK, which no session in this window has had. `SyncPump` moves them into `:core`, which leaves
`:app` holding a Ktor engine, a Room-backed applier, and a coroutine.

### S4T-2 What landed

`core/src/main/kotlin/app/careerseeker/core/SyncPump.kt` (new) and its test (new), plus a
comment-only fix to `OutboundEnvelopes.kt`. **Zero `:app` files touched** — deliberately: nothing
here can be compiled against `:app` on this machine, so nothing here was allowed to depend on it.
`SyncPump.kt` has **no `import` lines at all**, which is what makes the `checkCoreIsAndroidFree`
claim structural rather than a promise (C-S4T-7).

Four rules, and the failure each one prevents. All four are silent failures; that is the whole
reason they are worth a test rather than a comment.

1. **The cursor advances on every envelope *seen*, not every envelope *applied*.** A `delta` before
   any snapshot is *accepted* by `EnvelopeReceiver` and *refused* by the replica, so the persisted
   applied mark does not move. A cursor read from that mark re-fetches the same envelope next
   cycle — where the receiver's in-process replay window now rejects it. The phone pulls the same
   page forever, applies nothing, and reports no error at all. Re-seeding from the persisted mark
   on restart is the other half of the rule and is correct for the same reason in reverse: after a
   restart the replay window is empty, so anything accepted-but-not-applied gets a clean retry.
2. **The position is read once per envelope, before that envelope is applied.** `PullPolicy`
   measures `envelopeSeq - positionBefore.highestAppliedSeq`. Reading after the apply hides every
   gap; reading once per *page* invents one — 39 contiguous envelopes measured against the position
   at the top of the page look 39 apart, so a page longer than the threshold asks for a snapshot on
   every sync, forever, for a stream with nothing wrong with it.
3. **A `pull_request` the relay refused releases the latch.** Otherwise one dropped push silences
   the policy for the life of the process: it believes a request is outstanding that the engine
   never received.
4. **Sequence numbers come from inside the envelope, never from the relay's page wrapper.** This
   one was found while writing the tests rather than planned, and it is the finding of the slice —
   see S4T-3.

### S4T-3 A finding, in `:core`, on a path that was being wired rather than reviewed

`RelayClient.parsePullPage` accepts **two** page shapes. In the second — `{"seq":N,"envelope":…}` —
the relay's reported sequence number and the envelope's own are separate values that **can
disagree**. The envelope's `seq` is in the AAD, so the AEAD tag covers it. The relay's is covered
by nothing.

A transport cursor driven by the relay's number would let a blind relay **truncate the stream
without decrypting anything**: report `seq: 999` on an envelope carrying `5`, and the phone never
asks for `6..999` again. The relay cannot read a byte of what it carries — and would not need to.

**This is not a report that the deployed relay lies.** It splices envelopes back verbatim, so the
two numbers agree today, and no test here asserts otherwise. What changed is that the phone no
longer *depends* on that. The rule is cheap, and the property it protects — that a compromised or
substituted relay cannot silently drop history — is one of the two the whole blind-relay design
exists for.

Left open, deliberately: whether `parsePullPage` should accept the wrapper shape at all. Removing
it is a `:core` change with a `RelayClientTest` assertion behind it and possible engine-side
expectations; that is a separate slice, not a drive-by.

### S4T-4 What ran

```
:core reduced probe (C-S6A-1 recipe)   ->  BUILD SUCCESSFUL in 28s
JUnit XML totals                       ->  133 tests, 0 failures, 0 skipped, 12 classes
baseline, same probe, before the slice ->  115 tests, 0 failures, 0 skipped, 11 classes
node docs/sync-vectors/generate.mjs --check  ->  OK: 28 vector files match the generator.  (exit 0)
```

**Not green on the first run**, and the failure was mine rather than the code's: the contiguous-page
test asserted 39 position reads where the correct number is 40 — `pump()` seeds the cursor lazily
when `open()` was never called, which is one extra read. The assertion was wrong, the behaviour was
right, and the test now says so explicitly. Recorded because the precedent in this file is to state
whether a slice was green immediately.

**`Verify-Alpha.ps1` did not run and cannot** — no .NET. It also cannot be affected: no `.cs`, no
harness, no vector byte, no count-reporting doc, so `$ExpectedOfflineTotal` (598) is untouched by
construction. **The android gate did not run and cannot** — no SDK, no JBR, B-7 re-measured above.
CI is the gate for both, and C-S4T-8 is written to be checked rather than assumed.

### S4T-5 Ladder effect, stated narrowly

**S4 does not become DONE, and the honest description of what is left changed shape.** Before this
slice, S4's remainder was "`:app` wiring" — a phrase that hid four correctness decisions inside a
word that sounds mechanical. After it, the remainder really is wiring: construct a `SyncPump` with
a Ktor engine, an `EnvelopeApplier` adapter (the one `when`, whose exact body is in
`ReplicaApplier`'s KDoc), and a Room-backed position source; then call `open()` and `pump()` from a
coroutine. **Only the E2E claim still needs B-4.**

**`SyncPump` has no production caller and this file will not pretend otherwise.** `grep -rn SyncPump
app/src` prints nothing. The rung stays **PARTIAL**.

### Boundary — what was not touched

**Nothing was merged, in either repo.** PRs #32 and #33 stay drafts in the main repo and were
neither merged, retargeted nor force-pushed; android PR #6 stays a draft. Two commits were appended
to an existing branch of mine and pushed **forward-only** — no force-push, no history rewrite, no
branch created or deleted.

**No `:app` file, of any kind** — not a screen, not the applier, not the manifest, not a Gradle or
version-catalog file. `core/build.gradle.kts` was not touched either: the slice adds no dependency,
and `SyncPump.kt` adds no `import`. **No `.cs` file, no harness, no `$ExpectedOfflineTotal`, no
`Verify-Alpha.ps1`, no count-reporting doc.** **No vector's bytes changed and no vector was added** —
`generate.mjs --check` proves 28/28 still match. Nothing was re-vendored; the android pin stays
`679a317` and `core/src/test/resources/` was not opened for writing. **No `docs/Sync-Protocol.md`
change** — this slice implements the amendment PR #33 already made; it does not extend it, and no
protocol question was closed or opened in the spec.

**No `relay/` file.** `npm`, `vitest`, `wrangler` and miniflare were not invoked at all this
iteration. **No deploy of any kind** (Cloudflare, Workers, relay, site, Pages). **The production
relay was contacted zero times, not even `GET /v1/health`** — every relay in this slice is a Ktor
`MockEngine` inside the test JVM.

No emulator, no `sdkmanager`, no AVD, and no attempt to route around the `dl.google.com` denial —
it was probed once, as a client, to confirm B-7 still holds, and then respected. No Google, Play,
OAuth or Console action; no accounts, no purchases, no Play Billing code; no email or Gmail
anything; no cert-store, MSIX or keystore action — the upload keystore and its password file were
neither read nor referenced beyond their paths. No secrets read, written or printed. Terra's state
was read at iteration start: still R6(b) BLOCKED on draft PR #26, heartbeat unchanged at
2026-08-07T21:18, **claims no files** — no collision, and `:core` has never been Terra's territory.

---

## S3 decision half — the pairing attempt, moved where it can be tested · 2026-08-10 (ninth cloud iteration, Linux sandbox)

**Environment probed before the rung was picked**, as the eighth iteration's entry recommends.
Unchanged, and re-measured rather than carried:

```
dotnet, adb, sdkmanager, pwsh                 ->  absent
java 21, node 22.22.2, gradle 9.6.1           ->  present
https://dl.google.com/  (AGP, androidx)       ->  curl: (56) CONNECT tunnel failed, response 403
```

So B-7 holds and the lanes are the ones the records name: `:core`, `relay/`, `generate.mjs`, docs.

### S3A-1 The slice, and why it is not the one the schedule proposed — nor the one the ladder's own next-intent list proposed

The standing prompt again nominates S5's spec half (§4.3.3, the ack vector, PQ-A2-1/-2/-3). That
landed 2026-08-09 as PR #32, and the seventh iteration already recorded the correction. **Nothing
was redone**, and PQ-A2-3 remains what B-6 says it is: not closable by adding a vector, because the
engine has no inbound wire-JSON parser and the vector would turn CI red. Parser first — and the
parser is C#.

The ladder's own next-intent list was then read in order, and **every item on it is out of reach in
this sandbox**: S4's remaining `:app` wiring needs an Android SDK; S5's C# applier and S2's `/pair`
page need .NET; S6's signed send needs an AVD. Taking any of them would have meant writing code
that cannot be compiled here, which this program's records forbid claiming and which the eighth
iteration's boundary paragraph explicitly avoided.

What was available is the question `STATE.md`'s **fifth correction** asks out loud:

> when a rung's remainder is described with a word that sounds mechanical, enumerate it before
> believing the word. The same question is worth asking of S2's `/pair` page and S6's `signed send`
> — how much of each is a decision rather than an I/O call?

Asked of **S3**, whose one-line label is `BLOCKED — B-4`, the answer is that the label covers part
of the rung. S3 is "pairing screen": a CameraX preview, an ML Kit QR decode, a Keystore-backed
ECDSA key with the StrongBox → TEE → software fallback chain, and three screens. Those need the
emulator, and they stay blocked. **The pairing *attempt* needs none of them.** `PairingSession` is
stateless — parse a QR, derive, seal one body — and everything that orders those steps was
unwritten, in `:app`, where no session in this window can compile it.

This is the third time the same shape has appeared (S4 2026-08-09, S6 2026-08-09, S3 today), and it
is worth stating as a rule rather than a coincidence: **a rung's blocker applies to the claims that
depend on it, and "needs a device" almost never covers a rung's ordering rules.**

### S3A-2 What landed

`core/src/main/kotlin/app/careerseeker/core/PairingFlow.kt` (new) and its test (new). **Two files,
both new. No existing file was edited** — not a source file, not a test, not a Gradle or
version-catalog file. `PairingFlow.kt` has **no `import` lines at all**, which is what makes the
`checkCoreIsAndroidFree` claim structural rather than a promise (C-S3A-7).

Four rules, and the failure each prevents. All four are silent; that is why they are assertions
rather than comments.

1. **The completion is derived once per invite, and a retry re-sends it verbatim.** The naive retry
   regenerates the ephemeral keypair and the nonce, and it breaks pairing in two different ways
   depending on what the relay did with the first body. If body #1 landed and is still stored, the
   retry gets 409 and the engine eventually collects **body #1** — deriving `k_p2e` against a
   `phone_pub` this device has already discarded. Both screens then show six digits that cannot
   match, and neither screen can say why. If body #1 landed and was already collected, the engine
   burned the one-time secret against it, so body #2 is refused (`pairing_unknown`) and the phone
   waits for a confirmation that will never be displayed.
2. **A 409 on submit is ambiguous *by construction*, and the class refuses to guess.** This is the
   finding of the slice — see S3A-3.
3. **Nothing leaves the class until the human confirms, and a mismatch is terminal and is not a
   cancel.** Key material is reachable only through `PairingStep.Paired`, which only
   `confirm(true)` produces. A mismatch cannot be retried: the engine burned the one-time secret on
   whichever completion it accepted (§5.2.2), so the honest next step is a fresh invite on the
   desktop, not a second attempt against a dead secret. And `CODE_MISMATCH` is reported separately
   from `CANCELLED` because the two mean opposite things about whether an attacker is present —
   folding them together would erase the only MITM signal the protocol gives a user.
4. **The phone never rotates the relay token.** §5.2.3 assigns rotation to the engine. `RelayClient`
   exposes `create(rotateToSha256Hex)` to the phone (`RelayClient.kt:94`) — one call, and while the
   engine still holds the provisional bearer to collect the completion, it is locked out of
   `GET /pair` by a 401 it has no way to read as "the phone jumped the gun". The completion is
   stored, one-shot and unreadable; the secret is spent; nothing on either screen says so. The test
   asserts the whole attempt is **one** relay call and that none of them is `/create`.

Alongside it, `RelayTokenLadder` — the handover the phone *does* take part in. Open on the
provisional token (§5.2.1 bootstrapped the channel with it), switch on a 401, and **once a call
carrying the final token has been accepted, never fall back**. Rotation is one-way and idempotent,
so after it there is no state in which the provisional token is right again; a ladder that kept
falling back would turn a revoked pairing — a 401 the user needs to see — into an auth blip
retrying forever against a token derived from a burned secret. Losing a pairing quietly is the
failure that rule exists to prevent.

Secret hygiene, stated because it is easy to get backwards: the invite's one-time secret and the
ephemeral private scalar are zeroised **as soon as the completion is built**, before any network
call, since `PairingKeys` and the provisional token are already in hand by then. The test asserts
both that the scalar comes back blank *and* that the derived keys still equal the vector's — the
second half is what proves the zeroisation happened after the derivation rather than before it.

### S3A-3 A finding, on the path being written rather than reviewed

**A 409 from `POST /pair` cannot be read as "somebody else beat us", and the obvious code that does
so is wrong.**

`RelayClient.request` retries transport failures internally — four attempts (`RelayClient.kt:186`).
An attempt that reaches the relay, stores the completion, and then loses its response is followed
by an attempt that sees the relay's own 409 (`channel.ts:118`, one completion per pairing). So
**this phone's own success can arrive as `RelayResult.Conflict`**, and nothing available to the
phone separates that from a stranger's completion sitting on the channel.

Both plausible readings are wrong. Treating 409 as failure aborts a perfectly good pairing every
time the network hiccups mid-post. Treating it as success hides a genuine race behind a screen that
looks identical to the happy path.

The resolution already exists in the protocol and did not need inventing: `confirm` is derived from
`ikm`, so it matches the desktop **iff** the stored completion is this phone's. `PairingFlow`
therefore returns `AwaitingConfirmation(raced = true)` and lets the human arbitrate — which is
exactly the job §5.2 assigns the confirmation step ("catches a raced completion"). The effect is
that the confirm code becomes **load-bearing rather than decorative**: it is the only thing that
distinguishes the two cases, and a UI that auto-confirms would delete the distinction.

**This is not a report that the relay or the client is broken.** Both behave as specified. What
changed is that the phone no longer needs 409 to mean one thing.

### S3A-4 What ran

```
:core reduced probe (C-S6A-1 recipe)          ->  BUILD SUCCESSFUL in 13s
JUnit XML totals                              ->  154 tests, 0 failures, 0 skipped, 13 classes
baseline, same probe, before the slice        ->  133 tests, 0 failures, 0 skipped, 12 classes
grep -c '^import' PairingFlow.kt              ->  0
grep -rn 'PairingFlow|RelayTokenLadder' app/src  ->  (nothing), exit=1
node generate.mjs --check   (careerseeker main)  ->  OK: 26 vector files match the generator. (exit 0)
node generate.mjs --check   (branch 9c05ef7)     ->  OK: 28 vector files match the generator. (exit 0)
curl https://dl.google.com/                   ->  curl: (56) CONNECT tunnel failed, response 403
```

**All 21 new cases passed on the first run**, and no existing test was touched — the precedent in
this file is to say whether a slice was green immediately, and this one was.

**One number in `STATE.md` needed qualifying rather than correcting.** The standing "Shared vectors:
**28**" is the figure on `claude/s5-entitlement-ack-spec` (PR #32, unmerged). On `origin/main` it is
**26** — the two ack vectors are not there yet. Both were measured here and C-S3A-6 now names the
ref alongside each count, because a count with no ref is the doc-drift trap the main repo's
`CLAUDE.md` warns about, one repo over.

**`Verify-Alpha.ps1` did not run and cannot** — no .NET. It also cannot be affected: **no file in
the main repo was written at all**, so `$ExpectedOfflineTotal` (598) is untouched by construction.
**The android gate did not run and cannot** — no SDK, no JBR, B-7 re-measured above. CI is the gate,
and C-S3A-8 is written to be checked rather than assumed.

### S3A-5 Ladder effect, stated narrowly

**S3 does not become DONE, and its label changes from `BLOCKED` to `PARTIAL`** — the same correction
S4 and S6 received on 2026-08-09, for the same reason and with the same care: the blocker was never
wrong about the half it covers.

What is still B-4's, in full: the Android Keystore key and therefore gate P2-KEYSTORE-FALLBACK's
StrongBox → TEE → software chain with its persistent indicator and audit entry; CameraX and the ML
Kit QR decode; the three screens; and any claim that a key is hardware-backed. **None of that is
weakened by this slice and none of it is claimed.** What is no longer behind B-4 is the ordering,
which is now 21 executed test cases instead of prose in a screen nobody can compile here.

**`PairingFlow` has no production caller, and this entry will not pretend otherwise.**
`grep -rn PairingFlow app/src` prints nothing. A green suite on an uncalled class is not a pairing
screen.

### Boundary — what was not touched

**Nothing was merged, in either repo.** Main-repo PRs #32 and #33 stay drafts and were neither
merged, retargeted nor force-pushed; android PR #6 stays a draft. Commits were appended to an
existing branch of mine and pushed **forward-only** — no force-push, no history rewrite, no branch
created or deleted.

**No file in the main repo was written at all** — no `.cs`, no harness, no `$ExpectedOfflineTotal`,
no `Verify-Alpha.ps1`, no count-reporting doc, no `docs/Sync-Protocol.md` change, and **no protocol
question was opened or closed in the spec**. `generate.mjs --check` was run twice, read-only, on two
refs. **No vector's bytes changed and no vector was added**; nothing was re-vendored, the android
pin stays `679a317`, and `core/src/test/resources/` was not opened for writing.

**No `:app` file of any kind** — not a screen, not the manifest, not a Gradle or version-catalog
file. `core/build.gradle.kts` was not touched: the slice adds no dependency, and `PairingFlow.kt`
adds no import. **No `relay/` file**, and `npm`, `vitest`, `wrangler` and miniflare were not invoked
this iteration — the finding in S3A-3 is about how a *client* reads a 409 the relay is right to
send, so the relay needs no change.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages). **The production relay was
contacted zero times, not even `GET /v1/health`** — every relay in this slice is a Ktor `MockEngine`
inside the test JVM. No emulator, no `sdkmanager`, no AVD, and no attempt to route around the
`dl.google.com` denial: it was probed once, as a client, to confirm B-7 still holds, and then
respected. No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing
code; no email or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and
its password file were neither read nor referenced beyond their paths. **No secrets read, written or
printed**, and no Android Keystore was created, faked or simulated: the device signing key enters
this code only as a public point supplied by the caller.

Terra's state was read at iteration start: still R6(b) BLOCKED on draft PR #26, heartbeat unchanged
at 2026-08-07T21:18, **claims no files** — no collision, and `:core` has never been Terra's
territory.

---

## S6S — the send path, 2026-08-10 (tenth cloud iteration, Linux sandbox)

### S6S-1 The slice, and why it is not the one the schedule proposed

The standing prompt again nominates S5's spec half — §4.3 `entitlement_ack`, the ack vector,
PQ-A2-1/-2/-3 — and again describes S5 as "NOT STARTED and genuinely NOT blocked". **That landed
2026-08-09 as PR #32**, and `STATE.md`'s fourth correction already records the drift. Verified again
this iteration rather than taken on trust: `origin/claude/s5-entitlement-ack-spec` (`9c05ef7`)
carries §4.3.3, the two ack vectors, `generate.mjs`'s entries and the relay cap fix; `origin/main`
(`00b3705`) does not yet. **Nothing was redone.** PQ-A2-3 remains what B-6 says it is — not closable
by adding a vector, because the engine has no inbound wire-JSON parser and the vector would turn CI
red. Parser first, and the parser is C#.

So the ladder's own next-intent list was read in order, and the question `STATE.md`'s **fifth
correction** asks was put to the one rung whose remainder is described with a mechanical-sounding
word:

> when a rung's remainder is described with a word that sounds mechanical, enumerate it before
> believing the word. The same question is worth asking of S2's `/pair` page and S6's `signed send`.

**Asked of S6, the answer is that "the signed send" is not an I/O call.** `STATE.md` called S6's
remaining half *genuinely* blocked — "it needs a device-signed envelope (§5.4), which needs S3's
Android Keystore key, which needs an AVD that does not exist" — and singled it out as the one
remainder a toolchain could not unblock. **That was too strong, and the codebase already showed
why:** `OutboundEnvelopeFactory` takes the signer as an injected `fun interface DeviceSigner`, and
`OutboundEnvelopesTest` has been building and asserting *signed* envelopes in this sandbox since A6.
What needs a Keystore is the claim that a key is hardware-backed. What does not is everything that
decides **when** an envelope is built, **which** bytes go back on a retry, and **what** each relay
answer means for the mark in flight.

That is the fourth time this shape has appeared (S4 2026-08-09, S6's marking half 2026-08-09, S3
2026-08-10, S6's send half today), and it is now worth stating as a rule with a caveat attached:
**a rung's blocker applies to the claims that depend on it — and a blocker that is real for one half
of a rung tends to get written down as if it covered the rung.** The label was not wrong about the
Keystore. It was wrong about the reach.

### S6S-2 What landed

Two commits, six files, of which **two are new** and four are edits confined to one type change.

`OutboundQueue.kt` (new) + `OutboundQueueTest.kt` (new): the layer between `OutcomeMarkPolicy`
(which decides *what* to mark) and `RelayClient.push` (which is the call). Six rules, each with a
version that compiles, renders correctly and reports nothing wrong.

1. **The bytes are built once; every retry re-sends them verbatim.** `build()` consumes a sequence
   number on every call, so *retry* and *rebuild* are different operations that look identical at
   the call site. A rebuild burns a second p2e seq, and if the first attempt landed and merely lost
   its response, the engine receives one intention twice under two seqs with two audit rows. The
   test counts the `SeqSource`'s calls, so this is observed rather than argued (C-S6S-4).
2. **A re-mark collapses onto an unbuilt entry and never onto a built one.** Two taps before
   anything leaves the phone are one intention — the same reasoning `OutcomeMarkPolicy.mark` gives
   for collapsing in its own map, and collapse moves the entry to the end for the same stated
   reason. Bytes already on the wire cannot be un-sent, so a re-mark against a built entry
   legitimately becomes a second envelope, which §4.3.1 resolves by latest-wins.
3. **A 409 is read as neither success nor failure.** See S6S-3 — this is the finding.
4. **Poison is dropped, not retried forever.** A 413 on a body of a few dozen bytes is a defect in
   whatever built it; retrying would wedge every later mark behind an envelope that can never fit.
   Dropping is safe twice over: the relay never stored it, so its `last` is unmoved, and §6.2 makes
   the resulting p2e gap legal for the receiver ("a gap MUST NOT stall the stream").
5. **Nothing is destroyed to report a condition.** `Unavailable` and `Unauthorised` keep the bytes;
   a missing device key **halts** rather than dropping the user's marks. Deleting data to describe a
   state the UI should be surfacing is the failure that rule exists to prevent.
6. **Exactly one envelope is in flight.** §6.2 would permit pipelining, but a queue with several
   unresolved sequence numbers cannot attribute a 409 to any of them, and rule 3's rebuild would
   then have to reason about which frozen envelopes are dead. Single-flight is what makes rule 3
   checkable at all — it is load-bearing, not a style choice.

Built with a **stub** signer and **no Keystore**, which is the assertion that this half needed
neither.

Alongside it, the four edits: `RelayResult.Conflict` becomes a data class carrying `latest`
(`RelayClient.kt`), with the two `when` branches that matched it as an object updated to type checks
(`SyncPump.kt`, `PairingFlow.kt`) and two existing assertions updated to construct it. **No existing
test was deleted or renamed, and no behaviour of `SyncPump` or `PairingFlow` changed** — both map a
conflict exactly as before.

### S6S-3 The finding: a 409 cannot be disambiguated by attempt count, and the relay was already sending the fix

The obvious code reads a push conflict by position: *first attempt → our counter is behind; retry →
our earlier attempt landed.* **That is wrong, and it is wrong for a reason that is invisible at the
call site.** `RelayClient.request` retries transport failures *inside* one `push` call (four
attempts). An attempt that reaches the relay, stores the envelope and loses its response is followed
by an attempt that sees the relay's own 409 — so **the queue sees a conflict on what it believes is
its first attempt.** It is the same ambiguity `PairingFlow` recorded on `POST /pair` (S3A-3),
arriving on the push path, and the attempt counter cannot separate the cases.

The resolution is that the queue does not need to. **Whether the mark landed is already answered
elsewhere and only elsewhere:** `OutcomeMarkPolicy` holds a mark pending until the engine's value
converges, because with no `outcome_ack` that is the only evidence v1 offers (PQ-S6-1). What the
queue must do is get *unstuck* — and for that the relay has been sending the necessary number all
along:

```ts
// relay/src/channel.ts:167  (careerseeker @ origin/main)
if (last !== null && seq <= last) return this.json({ error: 'replay_rejected', latest: last }, 409);
```

`RelayClient` mapped every 409 to a bare `Conflict` and returned **before reading the body**, so
`latest` was unreachable to any caller. It is now parsed. That value is precisely the input §6.1's
counter reconciliation asks for, and without it a sender whose persisted counter has fallen behind
can only retry an envelope the relay will refuse forever.

So a 409 retires the frozen bytes, halts on `COUNTER_BEHIND`, and hands `latest` to the caller that
owns the persisted counter. **The cost is a possible duplicate** — if the original did land, the
rebuild re-states the same mark — **and that is the right way to be wrong**: §4.3.1's carried
outcome is latest-wins state rather than an event log, so a duplicate is idempotent in effect,
whereas guessing "delivered" loses the user's mark silently.

**`PairingFlow` is untouched by this.** The pairing 409s answer `{"error":"exists"}` and carry no
number, so `latest` is null on exactly that path — there was never a number there for it to start
trusting, and the human's confirm-code comparison remains the tiebreak.

### S6S-4 A second finding, smaller, and it is a spec asymmetry — PQ-S6-2

§6.1 states the counter rule for both directions in one sentence ("persisted by the sender across
restarts") and then spells out the *reconciliation* obligation for **one** side only: the engine
MUST resume its e2p counter above `max(persisted_seq, relay_latest_e2p_seq)`. **The phone owes the
identical obligation on p2e and the spec never says so** — while the relay enforces it
symmetrically, refusing `seq <= last` per direction with no regard for who is sending.

Recorded as **PQ-S6-2** in `docs/protocol-questions.md` rather than fixed. The spec lives in the
main repo, `docs/Sync-Protocol.md` is already claimed by draft PRs #32 and #33, and a third stacked
spec edit made from a sandbox that cannot run `Verify-Alpha.ps1` is a poor trade for a paragraph.
The phone-side behaviour needed no amendment to be correct — `OutboundQueue` implements the
symmetric rule today.

### S6S-5 What ran

```
:core reduced probe (C-S6A-1 recipe)              ->  BUILD SUCCESSFUL
JUnit XML totals                                  ->  177 tests, 0 failures, 0 skipped, 14 classes
baseline, same probe, before the slice            ->  154 tests, 0 failures, 0 skipped, 13 classes
  OutboundQueueTest                               ->  20 (new)
  RelayClientTest                                 ->  17 (was 14)
grep -c '^import' OutboundQueue.kt                ->  0
grep -rnE '^\s*import\s+(android|androidx)\.' core/src  ->  (nothing), exit=1
grep -rn 'OutboundQueue' app/src                  ->  (nothing), exit=1
vendored vectors vs pin 679a317 (26 files)        ->  drift=0
node generate.mjs --check  (careerseeker main)    ->  OK: 26 vector files match the generator. (exit 0)
```

**The first full run was not green, and the precedent in this file is to say so.**
`177 tests completed, 1 failed` — `reconciling rebuilds above the reported mark` failed with
`expected: <0> but was: <1>`. Retiring the dead bytes after a 409 cleared the wire but not the
per-wire attempt counter, so a freshly rebuilt envelope reported itself as already-tried: a lie
about bytes the relay has never seen, and the only signal a future backoff caller would have. Fixed
in the same commit; re-run green (C-S6S-8).

One earlier run also failed for a reason worth recording because it is a **house convention, not a
bug**: an em-dash in a test *name* made Gradle's HTML report writer fail on the filename
(`Malformed input or input contains unmappable characters`). Every existing test name in this repo
is ASCII. Ours are now too.

**`Verify-Alpha.ps1` did not run and cannot** — no .NET. It also cannot be affected: **no file in
the main repo was written at all**, so `$ExpectedOfflineTotal` (598) is untouched by construction.
**The android gate did not run and cannot** — no SDK, no JBR (B-7). CI is the gate, and C-S6S-11 is
written to be checked rather than assumed.

### S6S-6 Ladder effect, stated narrowly

**S6 stays PARTIAL. It does not become DONE**, and the correction is to the *reason*, not the label:
S6's remaining half was recorded as genuinely blocked by B-4, and the send *decisions* were never
behind B-4 at all. What is still B-4's, in full: the Android Keystore key itself and therefore gate
P2-KEYSTORE-FALLBACK's StrongBox → TEE → software chain with its indicator and audit entry, and any
claim that a signature came from a hardware-backed key. What is still B-7's: the `:app` wiring — the
detail screen's controls, the transport loop that would drive this queue, and the persisted p2e
counter that `reconciled()` assumes a caller owns.

**`OutboundQueue` has no production caller, and this entry will not pretend otherwise.**
`grep -rn OutboundQueue app/src` prints nothing. A green suite on an uncalled class is not an
outcome-marking feature.

### Boundary — what was not touched

**Nothing was merged, in either repo.** Main-repo PRs #32 and #33 stay drafts and were neither
merged, retargeted nor force-pushed; android PR #6 stays a draft. Commits were appended to an
existing branch of mine and pushed **forward-only** — no force-push, no history rewrite, no branch
created or deleted, in either repo.

**No file in the main repo was written at all** — no `.cs`, no harness, no `$ExpectedOfflineTotal`,
no `Verify-Alpha.ps1`, no count-reporting doc, and **no `docs/Sync-Protocol.md` change**: the
asymmetry S6S-4 found was recorded as a question in the android repo, not amended into the spec.
`generate.mjs --check` was run once, read-only. **No vector's bytes changed and no vector was
added**; nothing was re-vendored, the android pin stays `679a317`, and all 26 vendored files were
verified byte-identical to it rather than assumed.

**No `relay/` file was touched**, and `npm`, `vitest`, `wrangler` and miniflare were not invoked —
the finding in S6S-3 is about a number the *relay already sends correctly* and the *client* was
discarding, so the relay needs no change. **No `:app` file of any kind** — not a screen, not the
manifest, not a Gradle or version-catalog file. `core/build.gradle.kts` was not touched: the slice
adds no dependency, and `OutboundQueue.kt` adds no import.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages). **The production relay was
contacted zero times, not even `GET /v1/health`** — every relay here is a Ktor `MockEngine` inside
the test JVM. No emulator, no `sdkmanager`, no AVD, and no attempt to route around the
`dl.google.com` denial. No Google, Play, OAuth or Console action; no accounts, no purchases, no Play
Billing code; no email or Gmail anything; no cert-store, MSIX or keystore action — the upload
keystore and its password file were neither read nor referenced beyond their paths. **No secrets
read, written or printed**, and no Android Keystore was created, faked or simulated: the device
signing key enters this code only as a stub function supplied by the caller.

Terra's state was read at iteration start: still R6(b) BLOCKED on draft PR #26, heartbeat unchanged
at 2026-08-07T21:18, **claims no files** — no collision, and `:core` has never been Terra's
territory.

---

## S2R — The relay's read path forgot retention (2026-08-10, eleventh cloud iteration)

Linux cloud sandbox. Main repo only: `relay/src/channel.ts` and `relay/test/relay.test.ts`, two
commits on `claude/s2-relay-retention` (stacked on #32). **No android code was written this
iteration** — the records below are the only android files touched.

### S2R-1 Why this rung, when the prompt nominated S5

The standing prompt nominates S5's spec half every iteration and describes S5 as "NOT STARTED".
`STATE.md`'s fourth correction already answered that: the S5 spec, its two vectors and the phone
applier landed 2026-08-09 (PR #32). The prompt is a stored snapshot and does not re-read itself.
Verifying it took the mandatory fetch plus these records, in that order, and the fetch is also what
found `claude/s5-entitlement-ack-spec` already pushed with four commits on it.

What was actually available was named by the previous iteration, at the end of `STATE.md`: *"`relay/`
(Node + vitest + miniflare, no egress denial, and nobody has re-read it since the size-cap fix)"*.
S2 is the topmost `PARTIAL` rung, `relay/` is its transport half, and it is one of exactly two
modules a cloud session can gate. S2's *other* remainder — the `/pair` page — is C#, so it is not
merely in a different module but behind an absent runtime. This slice took the half that runs here.

**The precedent that shaped the method.** The 2026-08-09 size-cap finding was a relay bug that had
been green on CI the whole time *because the relay's own suite asserted the buggy number*. So the
re-read was done adversarially and empirically: enumerate the spec's MUSTs that bind the relay, then
probe each against the running Worker under miniflare rather than reading the code and reasoning.
Five probes, five answers, four of them things the suite did not know.

### S2R-2 The finding: retention was enforced by a background job and by nothing else

§2 is unambiguous — *"The relay MUST purge any envelope older than the configured TTL"* — and it is
the privacy promise the blind relay is sold on. Collection is driven by `alarm()`, which Cloudflare
**schedules**; it does not fire the instant a row expires. `GET /pull` had no expiry predicate at
all. Measured, by inserting a row with `expires_at = 1` (1970) and pulling:

```
pull -> {"envelopes":[ {…,"expired":true} ], "latest":1}      # served the expired envelope
```

Retention that holds only as fast as a background job happens to run is not the retention §2
describes. The row is not merely stale — it is a row the relay has already promised is gone.

**The second half is the one that bites, and it is not a privacy problem.** `latest` was
`MAX(seq)` over *all* rows while the page filtered none, so once the fix filtered the page the two
would have disagreed. `latest` is the client's loop bound — §2's own route table says pull returns
envelopes `seq > since`, and the client pulls until it has seen `latest`. A `latest` that counts a
row the page will never return is **a bound the client can never reach**: it re-pulls the same page
until the alarm collects the row. So both queries had to take the predicate, and the test that pins
it (`excludes expired rows from latest, so the page and its loop bound agree`) is the one that would
catch a half-fix.

**The opposite rule applies one function up, and that is deliberate.** `POST /push`'s replay guard
still counts expired-but-uncollected rows. The two paths want opposite things from the same rows:
serving one is a retention failure, forgetting one lowers the replay floor. Both are now pinned, so
the pull fix cannot be "tidied" into push by someone who notices the asymmetry and assumes it is an
oversight. `channel.ts` says so above the guard, not just in this log.

### S2R-3 A thing the relay is not, now written down

The same probe run answered a question nobody had asked: **the relay's monotonicity guard is not a
durable replay authority.** It is `MAX(seq)` over live rows, so once the TTL purge empties a
direction the floor is gone:

```
push seq=9 -> 201     purgeAll()     push seq=1 -> 201      # the floor came back to zero
```

That is not a defect and it is not being pinned as acceptable behaviour — §6.2 puts the
authoritative check on the *receiver's* persisted high-water mark, and the relay guard is defence in
depth with a TTL-shaped lifetime. It is written down because a reader who sees `replay_rejected` in
`channel.ts` could easily conclude the relay owns replay protection and relax the receiver's rule,
which is the same class of mistake as the four rung-label over-reaches this file has recorded.

### S2R-4 Two guarantees pinned that the relay already made

Neither is a behaviour change; both were measured against the relay as it stands.

**The 409 body.** `{"error":"replay_rejected","latest":N}`. The suite asserted the status code and
never the body. The android `:core` `RelayClient` began parsing `latest` into `RelayResult.Conflict`
last iteration (S6S-3) — it is §6.1's counter-reconciliation input, and a sender whose persisted
counter has fallen behind has no other way out of a channel that refuses it forever. **A tidy-up
dropping that field would have been green in this repo and would have broken the phone.** That is a
cross-repo contract with a test on neither side until now.

**Unknown top-level fields survive `push` → `pull` verbatim.** §3's *"unknown top-level fields MUST
be rejected"* binds the **receivers**, not the relay. A relay that stripped what it did not
recognise would silently repair envelopes the receivers are required to reject, and the rule would
stop being testable end to end. Pinned now because it is exactly the wire behaviour PQ-A2-3's
`invalid-unknown-field` vector will depend on when B-6 is unblocked — the field has to survive the
trip in order to be rejected at the far end.

### S2R-5 Two findings recorded and deliberately NOT fixed

Both are real; neither was fixed from here, and the reason is the same in both cases and is the
lesson of the size-cap bug: **tightening what the relay refuses is a change whose blast radius is
measured on machines this sandbox is not.**

**PQ-S2-1 — the relay never checks the `pairing` field it declares.** `push` validates seven fields
and skips the eighth. Measured: `"p_x"` → 201, field absent → 201, a *different* valid pairing id →
201. Small today (the key is per-pairing, so a foreign envelope does not decrypt; `pairing` is in the
AAD, so it cannot be edited in flight; only a bearer holder can push at all) — but §3's own words
are "a permissive parser here is how a future version's field silently becomes an injection point",
and this is a field the relay names in a typed interface and routes on. **What stopped the fix was
evidence, not caution:** two callers already in the repo emit ids that would fail a shape check —
`tests/EngineHarness/Program.cs:2268` uses `"p_bridge_test"`, and `relay/test/relay.test.ts`'s own
envelope helper has sent `"p_x"` into every channel for the life of the suite. Neither reaches a
relay today, but they are proof the shape rule is not universally respected here, which is precisely
what to measure *before* the relay starts refusing on it — and the harnesses that would catch an
over-tightening (`SyncLiveSmoke`, `Verify-Alpha.ps1`) need .NET.

**PQ-S2-2 — one out-of-range `seq` wedges a direction permanently.** §3 gives `seq` no maximum.
Measured:

```
push seq = 9007199254740991  -> 201
push seq = 1                 -> 409  {"error":"replay_rejected","latest":9007199254740991}
```

Every later envelope in that direction is refused for as long as the row lives, with no recovery
short of unpair or the TTL — and §6.1 tells a reconciling sender to resume *above* `latest`, which
here is a number it cannot usefully exceed. Not an outsider attack (it needs the bearer) and it does
not need malice: one sender bug that emits a garbage counter bricks the channel, presenting as "sync
stopped" behind a 409 nobody can act on. Capping it relay-side **without a spec amendment first
would be the size-cap bug run again** — a relay refusing what §3 declares legal. Recorded with the
smaller sibling finding in the same field: the engine types `seq` as `long`
(`src/Sync/EnvelopeCodec.cs:7`) while the relay reads it through `JSON.parse` into a double, so the
two diverge silently above 2⁵³. Unreachable in practice, same answer, so they should be settled
together: `2^53 - 1` is the largest integer all three implementations agree on exactly.

### S2R-6 What ran

```
npx vitest run  (baseline, parent 9c05ef7)          ->  36 passed
npx vitest run  (this branch)                       ->  42 passed, 0 failed
npx vitest run  (this branch's TESTS, parent's src) ->  2 failed | 40 passed   <- the defect
npx tsc --noEmit            (after npx wrangler types)  ->  exit 0
node docs/sync-vectors/generate.mjs --check         ->  OK: 28 vector files match the generator. (exit 0)
grep -rniE 'subtle\.(decrypt|importKey)|createDecipher|aes-256-gcm' relay/src/  ->  (nothing) — CI's blindness step
git diff --stat 9c05ef7..HEAD -- docs/ src/ tests/ scripts/  ->  (nothing)
```

**The two-failure run is the evidence, and it was run deliberately.** A regression test that passes
with and without the fix pins nothing, so the fix was reverted (`git checkout 9c05ef7 --
relay/src/channel.ts`) with the new tests kept, and both failed with exactly the symptoms the probes
had found — 1 envelope served instead of 0, `latest` 2 instead of 1. The other four new tests pass
in both states **by design**: they pin behaviour that already existed (S2R-3, S2R-4), and this entry
would be overstating them if it called them regression tests.

**`Verify-Alpha.ps1` did not run and cannot** — no .NET. It also cannot be affected: no file outside
`relay/` was written, so `$ExpectedOfflineTotal` (598) is untouched by construction, and the third
diff command above is the check rather than the assertion. **The android gate did not run and
cannot** (B-7). **Neither did `npx wrangler deploy --dry-run`**, which CI does run — see the
boundary paragraph.

### S2R-7 Ladder effect, stated narrowly

**S2 stays PARTIAL, and this slice does not move it toward DONE.** B-2's remaining gap is the
desktop `/pair` page and nothing here touched it. What changed is that S2's transport half is a
little less wrong than it was, and the reason to say it that way is that the previous relay slice
(the size cap) also fixed a real latent defect without closing anything — two consecutive iterations
have now improved the relay while B-2 stood exactly where it was. **A rung does not advance because
work happened in its neighbourhood.**

The honest summary of this iteration's effect: one live defect fixed (retention was observable, not
latent — every pull between expiry and collection served it), one hang-shaped consequence prevented
before it shipped, four guarantees pinned, two findings handed to a machine that can gate them.

### Boundary — what was not touched

**Nothing was merged, in either repo.** PRs #32 and #33 stay drafts and were neither merged,
retargeted nor force-pushed; android PR #6 stays a draft. The new branch was created and pushed
**forward-only** — no force-push, no history rewrite, no branch deleted, in either repo.

**Two files changed in the main repo, both under `relay/`.** No `.cs` file, no harness, no
`$ExpectedOfflineTotal`, no `Verify-Alpha.ps1`, no count-reporting doc, and **no
`docs/Sync-Protocol.md` change** — the retention fix is conformance to §2 as already written and
needs no amendment, and the two findings that *would* need one were recorded as PQ-S2-1/-2 in this
repo instead. **No vector byte changed and no vector was added**; `generate.mjs --check` was run
once, read-only, and reports 28 on this branch, which is #32's figure and not `main`'s 26. Nothing
was re-vendored and the android pin stays `679a317`.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages), and **`npx wrangler deploy
--dry-run` was deliberately not run** even though CI runs it and it does not deploy: declining every
`wrangler deploy` variant from an unattended sandbox is the conservative reading of a standing
"no deploys" embargo, and `wrangler.jsonc` was not touched, so the step is unaffected by this diff.
`npx wrangler types` **was** run — local codegen into a gitignored file, no network, no account.
**The production relay was contacted zero times, not even `GET /v1/health`** — every relay in this
slice is miniflare inside the test runner.

**No `:core` or `:app` file of any kind**, no Gradle or version-catalog file, no emulator, no
`sdkmanager`, no AVD, and no attempt to route around the `dl.google.com` denial. No Google, Play,
OAuth or Console action; no accounts, no purchases, no Play Billing code; no email or Gmail
anything; no cert-store, MSIX or keystore action — the upload keystore and its password file were
neither read nor referenced beyond their paths. **No secrets read, written or printed.**

Terra's state was read at iteration start: still R6(b) BLOCKED on draft PR #26, heartbeat unchanged
at 2026-08-07T21:18, **claims no files** — no collision. `relay/` has never been Terra's territory,
and it was already claimed by me through #32.

---

## S4P — The pull page was untrusted input parsed as if it were trusted (2026-08-10, twelfth cloud iteration)

**Rung:** S4 (transport half). **Effect on the ladder: none — S4 stays PARTIAL.** Two files, both
in `:core`. `:core` 177 → **185 / 0 / 0**, measured here both before and after.

### S4P-1 Why this rung, and why not the one the prompt nominated

The standing prompt nominates S5's spec half — §4.3's `entitlement_ack` body, the ack vector,
PQ-A2-1/-2/-3 — and describes S5 as "NOT STARTED". **All of it except PQ-A2-3 landed 2026-08-09**
(main-repo PR #32 draft), and the phone applier landed with it. `STATE.md` has carried that
correction since the seventh iteration; this is the sixth iteration in a row to re-derive it. The
prompt is a stored snapshot and does not re-read itself.

Every rung's *forward* remainder needs a machine this session is not:

| Rung | Remainder | Needs |
| --- | --- | --- |
| S2 | the desktop `/pair` page | .NET (absent — `dotnet` is not on PATH) |
| S3 | Keystore key, camera, screens | a device/AVD (B-4) |
| S4 | the `:app` adapter, Room source, Ktor engine | Android SDK (B-4/B-7) |
| S5 | the **C# applier** | .NET |
| S6 | the device key, the `:app` wiring | B-4 + B-7 |

So the choice was among the queued `:core`/`relay/` items. `STATE.md` carried one from the eighth
iteration — *"whether `parsePullPage` should accept the wrapper shape at all… a slice, not a
drive-by"* — and reading that function to answer the wrapper question turned up something larger
sitting next to it, which is what this entry is about. **The wrapper question itself is still open**
and still deliberately not a drive-by (S4P-6).

### S4P-2 The finding: one sibling was hardened and the other was not

`RelayClient` has two functions that read a body the relay controls. One was written to be total,
with the reasoning in its own KDoc:

> *"Deliberately total: a 409 whose body is absent, empty, not JSON, or JSON without the field is a
> conflict with no reconciliation input… **the one thing this client must never do is convert a
> relay decision into an unavailability.**"* — `conflictLatest`

The other, `parsePullPage`, was partial in four separate places (`parseToJsonElement`, `.jsonObject`,
`.jsonArray`, `.jsonPrimitive` — each throws on the wrong shape), **and it is invoked outside the
try/catch that would have caught it**: `pull` ended `.map { body -> parsePullPage(body) }`, and
`map` runs on the *result* of `request`, after its error handling has finished. So a malformed 200
body did not become a `RelayResult` at all — it threw out of `pull` entirely, past the sealed
hierarchy that exists to describe exactly this.

That matters because of who supplies the body. §2 makes the relay a **blind pipe**, and this
client's own class KDoc is written on the assumption that it may be hostile as well as broken. It
controls the page completely.

**Measured before anything was changed** — twelve bodies through the shipped parser:

```
PROBE | non-JSON body               | THREW JsonDecodingException
PROBE | HTML error page             | THREW JsonDecodingException
PROBE | empty body                  | THREW JsonDecodingException
PROBE | JSON array root             | THREW IllegalArgumentException
PROBE | JSON string root            | THREW IllegalArgumentException
PROBE | envelopes not an array      | THREW IllegalArgumentException
PROBE | array element is a primitive| THREW IllegalArgumentException
PROBE | seq is an object            | THREW IllegalArgumentException
PROBE | latest is an object         | THREW IllegalArgumentException
PROBE | latest is a numeric string  | OK -> PullPage(envelopes=[], latest=9)
PROBE | envelopes key absent        | OK -> PullPage(envelopes=[], latest=4)
PROBE | latest key absent           | OK -> PullPage(envelopes=[], latest=0)
```

**Nine of twelve escaped the `RelayResult` contract.** No attacker is required for the realistic
one: an intercepting proxy or a CDN serving its own error page with a 200 status produces line 2.

### S4P-3 The three that did *not* throw are the worse half

The loud failures are a robustness bug. The three quiet ones are a correctness bug, and one of them
is a silent stall an adversary can cause by **deleting a single field**.

`latest` is what drives `moreAvailable` and §6.2's gap check. Omit it and the old parser read `0`,
so `cursor < latest` is false, so the pump believes it is fully caught up — **a sync that stops with
no error, reported as healthy**. `envelopes` absent was the mirror image: a successful *empty* page
carrying a `latest` above the cursor, i.e. "nothing to do" and "the relay is ahead of you" asserted
in the same breath.

**The engine has always refused both.** `src/Sync/RelayClient.cs:72-73` reads
`GetProperty("envelopes")` and `GetProperty("latest").GetInt64()` — absent keys throw, and so does a
quoted number. The phone was the permissive one, which is the wrong direction: the mission's
interpretation rule says match the engine, and *"a phone more correct than the engine is a field
bug"* cuts both ways — more *lenient* is a field bug too. That reading is what this slice shipped,
and the gap that allowed two readings is now **PQ-S4-2**: §2's route table defines the pull request
and never defines its response body, so the relay, the engine and the phone had each invented one.

### S4P-4 The decisions, stated, because each has a wrong version that compiles

1. **Total, not throwing.** Any unreadable page is `RelayResult.Unavailable("malformed pull page:
   <exception class>")`. `pull` now ends `.flatMap`, a new combinator for a transform that can
   itself fail — `map` structurally cannot express "the relay answered and I could not read it".
2. **Both keys required and strictly typed**, matching `GetProperty`/`GetInt64`. A quoted `"9"` is
   refused because the engine refuses it.
3. **One unusable element rejects the whole page — never skip-and-continue.** This is the decision
   with the most dangerous wrong version, because skipping compiles, renders correctly, and loses
   envelopes in silence: the cursor advances past everything *seen*, so dropping element 47 and
   keeping 48 skips 47 permanently. That is the history-truncation attack `SyncPump` already refuses
   in its other form (it reads the authenticated `seq`, never the relay's — C-S4T-4) wearing a
   different hat. **A blind relay that wants an envelope skipped would only have to corrupt it.**
4. **The per-element `seq` stays lenient**, and is the one field here that rejects nothing. Nothing
   authenticated reads it: `SyncPump` takes the `seq` out of the sealed bytes and ignores this one,
   and the engine reads no per-element `seq` at all. Rejecting over it would be *stricter* than the
   engine on a field no trust decision consumes — the wrong direction again, in the other direction.
5. **The failure detail carries a diagnosis and no relay bytes.** It can reach a log line; the body
   it describes is ciphertext plus routing metadata.

`Unavailable` was chosen over a new `RelayResult` variant deliberately, and it is the weakest point
in this slice — see the self-audit in the PR and S4P-7.

### S4P-5 Evidence, and the red run was deliberate

Baseline, measured on the reduced `:core` probe (C-S6A-1's recipe, JDK 21) **before** any edit:
**177 / 0 / 0 across 14 classes**, matching the figure `STATE.md` carried from the tenth iteration.
After: **185 / 0 / 0 across 14 classes**, `RelayClientTest` 17 → **25**.

**All 8 new tests were run against the pre-fix parser and all 8 failed**, with the 17 pre-existing
`RelayClientTest` cases still passing — so the new tests pin the new behaviour, and the fix moved no
existing assertion:

```
a page body that is not JSON is reported, never thrown                        FAILED
every structurally wrong page is an Unavailable, and none of them escapes ...  FAILED
a page missing latest is rejected, because defaulting it to zero fakes ...     FAILED
a page missing envelopes is rejected, not read as an empty queue               FAILED
a quoted latest is refused, because the engine's GetInt64 refuses it           FAILED
one unusable element rejects the whole page, and never just itself             FAILED
an unusable per-element seq does not reject the page, because nothing ...      FAILED
the failure detail carries a diagnosis and no relay bytes                      FAILED
(17 pre-existing cases: PASSED)                          -> BUILD FAILED
```

This is the reduced probe, **not** the verification command of record: `:app`, `lintDebug`,
`assembleDebug` and `checkCoreIsAndroidFree` are all absent from it, and the toolchain is
substituted 17 → 21 because `api.foojay.io` is egress-denied (B-7). **The gate is CI.**

**The android gate did not run and cannot run here** (no SDK, no JBR, `dl.google.com` denied — B-7).
**`Verify-Alpha.ps1` did not run and cannot** — `dotnet` is not on PATH; it is also unaffected, since
no main-repo file was written, so `$ExpectedOfflineTotal` (598) is untouched by construction.

**Vendored vectors: 26/26 byte-identical to pin `679a317`, drift 0** — verified here against the
main-repo checkout, not asserted. No vector was added or edited.

### S4P-6 What this deliberately did *not* do

**The wrapper shape stays.** `{"seq":N,"envelope":…}` is accepted by this client and produced by
nothing — not the relay (it splices bare envelope JSON), not the engine (no branch for it), not any
shared vector, not the spec. The evidence for removing it is now much stronger than when the
question was queued, **and it is still not a drive-by**: `RelayClientTest`'s
`pull returns envelopes unparsed…` asserts that shape directly, so removal rewrites an existing
assertion and belongs in a slice whose title says so. Recorded in **PQ-S4-2** with the evidence.

**The engine's own reader is partial in the same way** and was not touched: `PullAsync` has no
`try`, so a malformed page throws there too. It is a different failure posture (a desktop process,
not a phone's sync coroutine) and it is `.cs`, which cannot be compiled or gated here. Recorded, not
fixed.

### S4P-7 Ladder effect, stated narrowly

**S4 stays PARTIAL and this slice does not move it toward DONE.** Its remainder is the `:app`
wiring — the `ApplyResult` adapter, the Room-backed position source, the Ktor engine — and none of
that was touched. What changed is that S4's transport half stops trusting a party the threat model
already called untrusted. **A rung does not advance because work happened in its neighbourhood** —
the same sentence the eleventh iteration had to write about S2, and it is worth noticing that this
is now two in a row.

**`SyncPump` has no production caller** (`grep -rn SyncPump app/src` prints nothing), so the crash
this prevents is prospective, not observed in the field. The stall of S4P-3 is likewise a property
of the code as written, not an incident anyone reported. Saying otherwise would overstate it.

### Boundary — what was not touched

**Nothing was merged, in either repo.** Android PR #6 stays a draft; main-repo PRs #32 and #33 stay
drafts and were neither merged, retargeted, rebased nor force-pushed. The branch was pushed
**forward-only** — no force-push, no history rewrite, no branch deleted, in either repo.

**Two code files changed, both `:core` Kotlin** (`RelayClient.kt`, `RelayClientTest.kt`), plus this
repo's records. **No file in the main repo was written except the coordination bus** — no `.cs`, no
`relay/` file, no harness, no `$ExpectedOfflineTotal`, no `Verify-Alpha.ps1`, no count-reporting
doc, and **no `docs/Sync-Protocol.md` change**: the amendment PQ-S4-2 asks for is a spec decision,
and taking it unilaterally is the size-cap mistake in reverse. **No vector byte changed and no
vector was added**; nothing was re-vendored; the pin stays `679a317`. `generate.mjs` was not run and
not needed — this slice adds no vector.

**No `:app` file, no Gradle or version-catalog file**, no emulator, no `sdkmanager`, no AVD, and no
attempt to route around the `dl.google.com` denial. **No deploy of any kind** (Cloudflare, Workers,
relay, site, Pages), and no `wrangler` invocation at all. **The production relay was contacted zero
times, not even `GET /v1/health`** — every relay in this slice is a Ktor `MockEngine` inside the
test runner, and no test in it opens a socket.

No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email
or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password
file were neither read nor referenced beyond their paths. **No secrets read, written or printed.**

Terra's state was read at iteration start and again before writing this: still R6(b) BLOCKED on
draft PR #26, heartbeat unchanged at 2026-08-07T21:18, **claims no files** — no collision. This
slice took no main-repo territory at all.

### S4P-8 CI reported green on this head, and it counts

The gate ran and passed on `1867d0c`, this branch's tip — check run `93600690593`, job
*Build and test*, `success`, 21:15:55 → 21:23:37 UTC
([run 31433025825](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31433025825)).
The head was confirmed equal to `git rev-parse HEAD` rather than inferred from the PR's check list,
which follows the head. Single-job workflow, so green covers `checkCoreIsAndroidFree`, the
vendored-vector diff against `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`,
`:app:lintDebug` and the release-classpath tracker check — **every one of which the reduced probe
structurally cannot run**, and two of which (`assembleDebug`, `lintDebug`) are the only check that
the `.flatMap` change compiles under the real toolchain rather than the substituted one.

**It does not corroborate the number.** CI prints no totals and I did not count the per-case
`PASSED` lines, so **185** stays the probe's figure, gate-confirmed as *green* only (C-S4P-11).

**And a process failure worth writing down, because it is this entry's own defect in miniature.**
Two poll loops were spent on `curl` against the Actions REST API before I noticed it answers
**403** to this session's token — as does the Checks endpoint. Only the MCP `get_check_runs` path
reaches them. **`AUDIT-REQUEST.md` C-S6A-1 already recorded this**, from an earlier iteration that
lost the same time the same way, and I read past it. That is precisely the shape of the bug this
slice fixed: *an invariant written down in one place and not applied where it was needed.* Repeated
at C-S4P-12 rather than left where being written once demonstrably did not work.

---

## S4S — The pull page had no definition, so three implementations wrote three (2026-08-11, thirteenth cloud iteration)

Linux cloud sandbox. **Two repos, spec first, deliberately in that order.**

### S4S-1 Why this rung, when the stored prompt nominated another

The scheduled prompt nominates S5's spec half every iteration — §4.3 amendment, ack vector,
PQ-A2-1/-2/-3 — and describes S5 as "NOT STARTED and genuinely NOT blocked". **That has been wrong
since 2026-08-09**, and this is the seventh iteration to re-derive it: §4.3.3, both ack vectors and
the phone applier landed in PR #32 the first time it was nominated. The prompt is a stored snapshot
and does not re-read itself; its own instruction — *"verify it; do not trust this summary"* — is the
operative one. `STATE.md` and this file are the state.

So the choice was again among the items a sandbox can actually reach. Every rung's *forward*
remainder needs a machine this session is not (S2 the `/pair` page → .NET; S3 the Keystore and
screens → B-4; S4's `:app` wiring → SDK/B-7; S5's C# applier → .NET; S6's device key → B-4+B-7).
The queued `:core`/spec items were the field, and **PQ-S4-2's stated close was the topmost**: the
twelfth iteration had opened it, written down exactly what would close it — *"a §2 (or a new §4.2)
amendment defining the pull response body… Spec first, then the wrapper removal on both sides"* —
and deliberately left it, because taking a spec decision inside an implementation slice is the
size-cap mistake in reverse. This slice is that close, both halves.

### S4S-2 The gap, stated

§2's route table defines `GET /v1/{pairing}/pull?since={seq}&dir={dir}` as "Fetch envelopes for
direction `dir` with `seq > since`" **and stops**. No response body: not `envelopes`, not `latest`,
not their types, not whether either is required. `latest` appears in the normative text exactly
once — §6.1 reconciles the engine's counter against "the relay's current `latest`" — **using a field
the document never defines**. §3 pins the envelope to the byte; the page carrying envelopes was
pinned nowhere. Three implementations each filled the gap differently (the table is in PQ-S4-2), and
the phone's was the most permissive, which is the wrong direction under the interpretation rule.

### S4S-3 What §2.1 says, and why each rule is the engine's

The new section pins the strictest reading **already shipping** rather than inventing a fourth:
both fields REQUIRED, `latest` a bare JSON integer, elements bare §3 envelopes in ascending `seq`,
the page explicitly **truncatable** (`PULL_PAGE_SIZE` = 100, `relay/src/protocol.ts:64`) so `latest`
is the only "am I caught up" signal, an unreadable body never a successful empty pull, and the
`{"seq":N,"envelope":…}` wrapper refused.

The load-bearing paragraph is why the fields are required rather than defaulted. **`latest` is the
client's loop bound.** Default it to `0` and `cursor < latest` is false, so the client reports a
healthy, fully-caught-up, permanently empty sync — one deleted field, no error anywhere, and the
relay is the party that controls the body. Rejecting is loud; defaulting is not.

### S4S-4 A clause I wrote too strongly, caught by reading the engine instead of assuming it

The first draft required a receiver to report an unreadable body **"as an unavailability"**. The
phone does. `src/Sync/RelayClient.cs`'s `PullAsync` does not — it has no `try`, and the parse throws
to its caller. **That draft made shipping engine code non-conformant on a question of error-type
style, not safety**, and briefly looked like a new engine-side blocker to file.

It was not the engine's defect; it was mine, two commits old. The safety property both receivers
genuinely hold — an unreadable body must never become a successful pull of zero envelopes — stays a
MUST. The reporting mechanism dropped to SHOULD with both postures named. **A spec tightening ahead
of its implementations is the same defect as an implementation tightening ahead of its spec**, and
the relay's size cap is the precedent for the second direction. Corrected in the same slice
(`10696d2`), not left for a reader to trip over.

### S4S-5 The phone half, and the decision in it

`parsePullPage` no longer unwraps. An element **is** the envelope; `wire` is the whole element; a
wrapper fails the receiver's strict §3 parse, because `envelope` is not in `EnvelopeJson.KNOWN_FIELDS`.

Accepting the wrapper was never free tolerance. It made the meaning of an element depend on whether
it happened to contain a key named `envelope` — **a key the relay controls** — and it read a sequence
number the relay authenticates with nothing from beside one the AAD covers.

**The result is stronger than a fix, and this is the part worth carrying.** `SyncPump`'s rule 4
(prefer `header.seq`, never the page's) was a *defence* against those two numbers disagreeing. With
the wrapper gone they are read off the same field by both parsers, so for any element they either
agree (it parsed) or both fail (it did not): **the disagreement is now structurally unreachable
rather than defended against.** Rule 4 survives as defence in depth, and its test now says so
instead of implying the check is load-bearing.

### S4S-6 Three existing assertions rested on the wrapper, not the one that was predicted

The queued note predicted `RelayClientTest`'s `pull returns envelopes unparsed…`. There were
**three, in two files** — and the second `SyncPumpTest` case is the one worth the entry:

`an envelope that does not parse is discarded and does not stall the cursor` **kept passing** after
the change, while testing something other than its title. It wrapped a malformed envelope to
exercise §3's unknown-field rule; post-change the *wrapper* was what failed to parse, not the
`surprise` field, and the numbers happened to line up so the assertions still held. **A green test
is not evidence that it still tests what it says.** It now uses a bare envelope, which is what it
always meant. Nothing would have flagged this — it was found by reading every `wrappedPage` caller
after the one failure, rather than stopping at the failure.

### S4S-7 Evidence, and the red run was deliberate

Baseline on the reduced probe (C-S6A-1 recipe, JDK 21) **before any edit**: **185 / 0 / 0 across 14
classes**, matching the figure `STATE.md` carried from the twelfth iteration — re-derived, not
copied. After: **187 / 0 / 0 across 14**. `RelayClientTest` 25 → **26**, `SyncPumpTest` 18 → **19**.

The new `RelayClientTest` case was run against the **pre-change** parser, and failed there while all
25 pre-existing cases in the class passed:

```
RelayClientTest > a wrapped envelope is refused end to end, even when the envelope inside it is valid() FAILED
26 tests completed, 1 failed
BUILD FAILED
```

Its inner envelope is deliberately **structurally valid** — that is the whole point. Under the old
client the wrapper was unwrapped into something the receiver could parse, so the shape *worked*, and
that is precisely why nobody noticed no implementation emits it.

`node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the generator.`, exit 0
(28 is the **branch** figure; `main` is 26).

**This is the reduced probe, not the verification command of record.** `:app`, `lintDebug`,
`assembleDebug` and `checkCoreIsAndroidFree` are absent from it and the toolchain is substituted
17 → 21 (`api.foojay.io` egress-denied, B-7). **The gate is CI.** The android gate did not run and
cannot here; **`Verify-Alpha.ps1` did not run and cannot** — no .NET — and is also unaffected: the
main-repo half is one doc file, so `$ExpectedOfflineTotal` (598) is untouched by construction.

### S4S-8 What this deliberately did not do

**No vector was added or changed.** A page is not an envelope, so none applies — and `SyncHarness`
enumerates `docs/sync-vectors/v1/*.json` (`tests/SyncHarness/Program.cs:50`), so adding a file moves
`$ExpectedOfflineTotal`, a number no .NET-less machine can measure. The vendored pin stays `679a317`
and no vendored byte was touched.

**No relay change.** It already emits the conforming shape; §2.1 was written to match it, not to
move it. **No engine change** — `PullAsync` is `.cs` and cannot be compiled or gated here.

**PQ-S4-3 was opened, not fixed.** An element that fails the strict parse still advances the cursor
to its own *claimed* `seq`, so one unparseable element carrying a large number can steer the cursor
past envelopes that are then never re-requested. **It predates this slice and this slice neither
caused nor worsened it** — it merely made it the only remaining path by which a page's own numbers
reach the cursor. Both obvious repairs have a wrong version that compiles (refusing to advance
stalls the direction forever, which §6.2 forbids; advancing by one desynchronises the cursor from
real sequence numbers). Bounding the advance by the page's own `latest` looks best, and **that is a
decision, not a bug fix** — a slice whose title says so, spec first again.

### S4S-9 Ladder effect, stated narrowly

**S4 stays PARTIAL and this slice does not move it toward DONE.** Its remainder is the `:app` wiring
— the `ApplyResult` adapter, the Room-backed position source, the Ktor engine — and none of that was
touched. What changed is that one more thing the phone trusted about an untrusted party is no longer
trusted, and that a contract three implementations were each guessing at is now written down. **A
rung does not advance because work happened in its neighbourhood** — the third iteration in a row
that has had to write this sentence.

**`SyncPump` still has no production caller** (`grep -rn SyncPump app/src` prints nothing), so
nothing here is a field fix; it is all prospective.

### Boundary — what was not touched

**Nothing was merged, in either repo.** Android PR #6 stays a draft; main-repo PRs #32 and #33 stay
drafts and were neither merged, retargeted, rebased nor force-pushed. Both branches were pushed
**forward-only** — no force-push, no history rewrite, no branch deleted, in either repo.

**Main repo: one file written** (`docs/Sync-Protocol.md`, on `claude/s4-pull-request-semantics`),
plus the coordination bus. No `.cs`, no `relay/` file, no harness, no `Verify-Alpha.ps1`, no
`$ExpectedOfflineTotal`, no count-reporting doc, no vector and no `generate.mjs` change.

**Android repo: three `:core` files** (`RelayClient.kt`, `RelayClientTest.kt`, `SyncPumpTest.kt`)
plus these records. **No `:app` file**, no Gradle or version-catalog file, no emulator, no
`sdkmanager`, no AVD, and no attempt to route around the `dl.google.com` denial.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages) and no `wrangler` invocation at
all. **The production relay was contacted zero times, not even `GET /v1/health`** — every relay in
this slice is a Ktor `MockEngine` inside the test runner, and no test opens a socket.

No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email
or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password
file were neither read nor referenced beyond their paths. **No secrets read, written or printed.**

Terra's state was read at iteration start and again before writing this: still R6(b) BLOCKED on
draft PR #26, heartbeat unchanged at 2026-08-07T21:18, **claims no files** — no collision. This
slice's only main-repo territory is `docs/Sync-Protocol.md`, already claimed via #32/#33.

### S4S-10 CI reported green on both heads, and the offline pin was read rather than argued

**Main repo** — run [31448717897](https://github.com/ShivaClaw/careerseeker/actions/runs/31448717897) on
`claude/s4-pull-request-semantics` head `10696d2`. Both jobs `success`. **The job log was read, not
just its conclusion**, and it prints:

```
=== 130 passed, 0 failed ===          (SyncHarness)
=== Offline total: 598 passed, 0 failed ===
CareerSeeker alpha verification complete.
```

**This discharges a caveat that had stood since the tenth iteration.** Previous entries argued the
pin was intact *by construction* (a doc-only change cannot move it) and corroborated that with
"`Verify-Alpha.ps1` exits 0 and the script throws on drift". Both are sound arguments, and neither
is a measurement. **The number was sighted directly this time** — which matters more than usual
here, because this slice is the first to edit `docs/Sync-Protocol.md` in a way that could in
principle have tripped a content assertion, and `CLAUDE.md`'s doc/verifier drift trap is exactly
about that class of failure. It did not: the verifier makes **no** assertion against
`Sync-Protocol.md` (`grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1` → `0`, run here before the
edit), and CI confirms the total is unmoved.

**Android** — run [31448716435](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31448716435)
(run #75, event `push`), job *Build and test* (`93648385242`), **`success`**, 01:15:02 → 01:22:28 UTC.
**`head_sha` `782f9bbe951eb32fe09474c4bd3b04172db205a4`, read from the run's own field and matched
against `git rev-parse HEAD`** rather than taken from the PR's check list, which follows the head.
Single job, so green covers `checkCoreIsAndroidFree`, the vendored-vector diff against `679a317`,
`:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug` and the tracker check — **including
the only check that the wrapper removal compiles under the real toolchain** instead of the probe's
substituted JDK 21, which is the one thing the reduced probe structurally cannot tell anyone.

**What it still does not prove, stated plainly.** CI prints no totals for `:core` and **I did not
count the per-case `PASSED` lines**, so **187** remains the probe's number, gate-corroborated as
*green* and not as a count. That is the same gap C-S4P-11 recorded last iteration and it is
unchanged; the only honest channel for counting is pulling the entire job log, and I judged that
cost not worth paying for a number two independent probe runs already agree on. Recorded rather
than quietly dropped.

**One commit follows the measured head** — this records update — and it is **records-only, no code**,
so the green above still describes the code as it stands.

---

## S4C — The cursor advanced on a number the relay made up (2026-08-11, fourteenth cloud iteration)

### S4C-1 Why this rung, when the stored prompt nominated another

**The stored prompt's slice was already done, and checking that first is the whole of this
section.** It assigned S5's spec half: amend §4.3 to define the `entitlement_ack` body, close
PQ-A6-1/PQ-A2-1/PQ-A2-2, add the ack vector and PQ-A2-3's `invalid-unknown-field`. It also stated
S5 was "NOT STARTED and genuinely NOT blocked".

Measured after the mandatory `git fetch --all --prune` in both checkouts:

```
$ git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
9c05ef7 S5: correct 3.1's relay paragraph -- it reasoned in one direction only
a564c0c S5: the relay refused envelopes the protocol declares legal -- derive its cap
22b028e S5: pin section 4.3.3 with two entitlement_ack vectors, generated not hand-written
8575539 S5: define the entitlement_ack body, and say what the size cap actually measures
```

So §4.3.3 exists, both ack vectors exist, and PQ-A6-1/A2-1/A2-2 are closed — draft PR #32, four
iterations ago. `EntitlementAckApplier` (9 tests) is the phone half. The one piece of that prompt
still open is PQ-A2-3's vector, which is **B-6**: the engine has no inbound wire-JSON parser, so the
vector would assert a rejection the engine cannot perform and would turn the offline gate red for
whoever pushed next. B-6 says parser first, vector second, and the parser is C#.

**Redoing it would have produced a duplicate spec section and a second copy of two vectors** — and
adding B-6's vector would have shipped a known-red pin from a machine with no way to observe it.
S5's remaining surface in this environment is therefore empty: C# or blocked.

Picked instead: **PQ-S4-3**, opened by the previous iteration, explicitly queued there as "a slice
whose title says so, spec first again", and the topmost open item that is genuinely verifiable here
— a spec decision plus `:core` Kotlin, no .NET, no Android SDK, no emulator.

### S4C-2 The gap, stated

`SyncPump.pump` advanced its transport cursor per element:

```kotlin
val seq = header?.seq ?: envelope.seq      // SyncPump.kt:245, before
if (seq > cursorValue) cursorValue = seq
```

When the strict §3 parse fails there is no authenticated `seq`, so it fell back to the one the
element **claims** at its top level — read leniently by `parsePullPage` (`RelayClient.kt:237`,
anything unusable reads `0`) and authenticated by nothing. The KDoc justified this as "safe because
the item is discarded either way".

**The item is discarded; the cursor is not.** A blind relay that appends one unparseable element
carrying `"seq": 1000000` moves the cursor past every envelope below it, and the cursor never moves
backwards, so they are never requested again. That is history truncation performed by a party that
cannot decrypt a byte of what it removed from the receiver's view.

**And the protocol had nothing to say about it.** §6.2 governs `highest_accepted` — the
authenticated, persisted mark that drives replay rejection. The transport cursor is a *different*
number, the `since` the next pull sends, and `docs/Sync-Protocol.md` had never named it. The bug was
downstream of a hole in the spec, which is why the spec moved first.

### S4C-3 What §6.4 says, and why bounded

New **§6.4** (main repo, `claude/s4-pull-request-semantics`): the cursor MUST NOT move backwards;
it advances only to a `seq` recovered from the sealed bytes; and an element failing the §3 parse MAY
advance it by its claimed number but **MUST NOT** advance it past the page's own `latest`.

Both obvious repairs have a wrong version that compiles, which is what PQ-S4-3 recorded:

- **Refuse to advance.** One corrupt byte then stalls the direction forever, which §6.2 forbids by
  name ("a gap MUST NOT stall the stream").
- **Advance by one.** Desynchronises the cursor from real sequence numbers, and nothing says the
  next `seq` is `n+1` — the TTL purge makes gaps legitimate.

The bound settles it on an asymmetry PQ-S4-3 had not stated: **a stall is recoverable and loud** —
the cursor holds, `latest` still exceeds it, `moreAvailable` stays true, and the stream resumes
exactly where it stopped on the next readable page — **while truncation is silent, permanent, and
looks like a healthy caught-up sync**. When a receiver must choose, it stalls.

### S4C-4 A claim in my own source entry that was too strong, corrected in the spec

PQ-S4-3 argued the bound "caps how far one bad element can move the cursor at a value the client was
going to compare against regardless". **Writing the test showed that overstates it**, and the
overstatement is the kind that matters — it would have let a reader believe the attack was closed.

The bound does **not** protect envelopes the relay *currently holds*. `latest` is the relay's own
claim; a relay willing to lie about an element's `seq` can serve a page whose `latest` already
covers the rows it wants withheld — and it never needed a malformed element for that, since it could
simply not serve them.

What the bound removes is the part that **outlives the attack**: unbounded, the claim parks the
cursor in the *future*, past sequence numbers not yet issued, so every envelope the engine publishes
from that moment until the claimed number arrives at a receiver that believes it is already past
them. One malformed element becomes permanent, forward-going, silent data loss against an engine and
a relay that are both behaving. §6.4 states this distinction explicitly rather than letting the
stronger reading stand, and PQ-S4-3's closing note records the correction against itself.

### S4C-5 The phone half, after the spec

`SyncPump.kt:260`, now `minOf(envelope.seq, page.latest)`. Rule 4 in the class KDoc was rewritten to
match — it described only the wrapper hazard, which §2.1 closed last iteration, and said nothing
about the unparsed case that is now the whole of the rule.

**Against an honest relay this is a no-op**, which is the claim the two unchanged tests below
demonstrate rather than assert: its `latest` covers every row it serves, so the ceiling never binds.

### S4C-6 Evidence, and the red run was deliberate

Baseline on the reduced probe (C-S6A-1 recipe, JDK 21) **before any edit**: **187 / 0 / 0 across 14
classes**, re-derived here rather than copied from `STATE.md` — and it matched. After:
**190 / 0 / 0 across 14**. `SyncPumpTest` 19 → **22**. No class added, deleted or renamed.

The three new cases were run against the **pre-change** `SyncPump.kt`:

```
SyncPumpTest > an unparseable element cannot move the cursor past the page's latest() FAILED
SyncPumpTest > after a bounded skip the stream still delivers envelopes issued later() FAILED
SyncPumpTest > an authenticated seq above latest still moves the cursor() PASSED
22 tests completed, 2 failed
BUILD FAILED
```

**Two fail, and the third passing is the point, not a gap.** `an authenticated seq above latest
still moves the cursor` pins existing behaviour: the bound applies to the unauthenticated path only.
Without it, the obvious "simplification" — clamp every `seq` to `latest` — compiles, passes the two
new tests, and hands the relay the opposite lever, letting an understated `latest` hold the cursor
below envelopes the phone has already read. It is labelled a regression guard here and in its own
KDoc rather than counted as evidence of the fix.

**All 19 pre-existing `SyncPumpTest` cases passed in the red run.** Two of them assert cursor
positions on unparseable elements (`a wrapped envelope is never applied…` at claimed 999 / latest
999, and `an envelope that does not parse…` at claimed 6 / latest 6) and both are **unchanged by
this diff**, because on each the ceiling equals the claim. That is the honest demonstration that
§6.4 is a ceiling and not a behaviour change; the stale comment on the first — which called the
unbounded advance "the residual hazard recorded as PQ-S4-3" — was rewritten, since that hazard is
now the thing this commit bounds.

**Main repo:** `node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the
generator.`, exit 0 (28 is the **branch** figure; `main` is 26). `grep -c "Sync-Protocol"
scripts/Verify-Alpha.ps1` → **0**, run before the edit, so the doc/verifier drift trap is not armed
against this file.

**This is the reduced probe, not the verification command of record.** `:app`, `lintDebug`,
`assembleDebug` and `checkCoreIsAndroidFree` are absent from it and the toolchain is substituted
17 → 21 (`api.foojay.io` egress-denied, B-7). **The gate is CI.** The android gate did not run and
cannot here; **`Verify-Alpha.ps1` did not run and cannot** — no .NET — and is also unaffected: the
main-repo half is one Markdown file, so `$ExpectedOfflineTotal` (598) is untouched by construction,
not by assertion.

### S4C-7 What this deliberately did not do

**The engine half is not written.** `src/Sync/RelayClient.cs` reads pages with the same structure
and needs the same ceiling. It is `.cs`, there is no .NET here (`which dotnet` → nothing), and a
parser written against an unrun compiler is the drift these records exist to prevent. It is
**unblocked and merely unwritten** — a local session's, not a phantom blocker.

**No vector was added or changed.** A pull *page* is not an envelope, so no §3 vector can express
this rule at all — and `SyncHarness` enumerates `docs/sync-vectors/v1/*.json`
(`tests/SyncHarness/Program.cs:50`), so adding a file moves `$ExpectedOfflineTotal`, a number no
.NET-less machine can measure. The vendored pin stays `679a317` and **no vendored byte was touched**.

**No relay change.** The relay already publishes `latest` on every page; §6.4 was written to consume
what it emits, not to move it.

### S4C-8 Ladder effect, stated narrowly

**S4 stays PARTIAL and this slice does not move it toward DONE.** Its remainder is the `:app` wiring
— the `ApplyResult` adapter, the Room-backed position source, the Ktor engine — and none of it was
touched. **`SyncPump` still has no production caller**: `grep -rn SyncPump app/src` prints nothing,
so the truncation this prevents is prospective, not a field fix. **A rung does not advance because
work happened in its neighbourhood** — the fourth iteration in a row that has had to write this.

### Boundary — what was not touched

**Nothing was merged, in either repo.** Android PR #6 stays a draft; main-repo PRs #32 and #33 stay
drafts and were neither merged, retargeted, rebased nor force-pushed. Both branches were pushed
**forward-only** — no force-push, no history rewrite, no branch deleted, in either repo.

**Main repo: one file written** (`docs/Sync-Protocol.md`, on `claude/s4-pull-request-semantics`),
plus the coordination bus. No `.cs`, no `relay/` file, no harness, no `Verify-Alpha.ps1`, no
`$ExpectedOfflineTotal`, no count-reporting doc, no vector and no `generate.mjs` change.

**Android repo: two `:core` files** (`SyncPump.kt`, `SyncPumpTest.kt`) plus these records. **No
`:app` file**, no Gradle or version-catalog file, no emulator, no `sdkmanager`, no AVD, and no
attempt to route around the `dl.google.com` denial (re-measured this session: `CONNECT tunnel
failed, response 403`).

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages) and no `wrangler` invocation at
all. **The production relay was contacted zero times, not even `GET /v1/health`** — every relay in
this slice is a Ktor `MockEngine` inside the test runner, and no test opens a socket.

No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email
or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password
file were neither read nor referenced beyond their paths. **No secrets read, written or printed.**

Terra's state was read at iteration start: still R6(b) BLOCKED on draft PR #26, heartbeat unchanged
at 2026-08-07T21:18, **claims no files** — no collision. This slice's only main-repo territory is
`docs/Sync-Protocol.md`, already claimed via #32/#33, so no new claim was taken.

---

## S6C — the relay refuses both senders, and §6.1 asked only one (fifteenth cloud iteration, 2026-08-11)

Linux cloud sandbox. Main repo: **one Markdown file** on `claude/s4-pull-request-semantics`.
Android repo: **records and `docs/protocol-questions.md`** — **zero `:core` files, zero `:app`
files, zero vectors, zero relay code**. Spec-first, as every slice in this window has been.

### S6C-1 Slice choice, and the prompt was a stale snapshot for the fifth time

The stored prompt assigned S5's spec half — amend §4.3 for `entitlement_ack`, close
PQ-A6-1/PQ-A2-1/PQ-A2-2, add the ack vector and PQ-A2-3's `invalid-unknown-field` — and stated S5
was "NOT STARTED and genuinely NOT blocked". Measured after the mandatory
`git fetch --all --prune` in both checkouts:

```
$ git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
9c05ef7 S5: correct 3.1's relay paragraph -- it reasoned in one direction only
a564c0c S5: the relay refused envelopes the protocol declares legal -- derive its cap
22b028e S5: pin section 4.3.3 with two entitlement_ack vectors, generated not hand-written
8575539 S5: define the entitlement_ack body, and say what the size cap actually measures
```

All of it landed 2026-08-09 (draft PR #32), four iterations before this one. The remaining piece,
PQ-A2-3's vector, is **B-6**: the engine has no inbound wire-JSON parser, so the vector would assert
a rejection the engine cannot perform and would turn the offline gate red for whoever pushed next.
Parser first, vector second, and the parser is C#. **S5's remaining surface in this environment is
empty: C# or blocked.** Re-verify: C-S6C-1.

`STATE.md`'s fourth and sixth corrections both already say this. The prompt does not re-read itself;
the records are the state.

**Also checked before picking:** the fourteenth run closed PQ-S4-3, which `STATE.md` listed as the
topmost remaining sandbox-reachable item. That left `relay/` (PQ-S2-1, PQ-S2-2 — both carrying an
explicit *do not close either from a sandbox*) and "whatever `:core` decision layers remain
unenumerated". **Picked PQ-S6-2** instead: opened 2026-08-10, deferred that day for a reason worth
re-reading rather than inheriting — "a third stacked spec edit, made from a sandbox that cannot run
`Verify-Alpha.ps1`, is a poor trade for a paragraph that changes no behaviour". That judgement was
about *cost*, and it undercounted the finding sitting inside the question.

### S6C-2 The gap, and it is two gaps stacked

§6.1's first sentence binds **both** senders to persist their counter. The paragraph then spells out
the *recovery* rule for exactly one:

> The engine MUST therefore resume its e2p counter above `max(persisted_seq, relay_latest_e2p_seq)`

**The relay does not care who is sending.** `POST /push` refuses `seq <= last` per direction
(`relay/src/channel.ts:171`) whichever end pushed it. A phone whose persisted p2e counter has fallen
behind — a restore from backup, a rolled-back store, a counter persisted only after a push whose
response was lost — builds envelopes refused at the door until the counter climbs back, with no rule
in the document telling it what to do about that.

**And the deferral note contained the second gap, stated as an aside.** PQ-S6-2's "to close"
paragraph observed that the 409 body carries `latest`, and that the field "is currently documented
nowhere in `Sync-Protocol.md` despite being implemented and relied upon". **A §6.1 rule that points
at an undocumented field is not closed.** That is PQ-S4-2's defect one level down: normative text
depending on a response body no section defines. So §2.2 was written first.

### S6C-3 What was measured, and the probe trick earned its keep again

The eleventh run's method, reused verbatim: throwaway vitest tests under miniflare asserting
deliberately **wrong** values so the runner prints the measured one in its diff (`console.log` does
not escape the Workers pool). Nine probes:

| probe | measured |
| --- | --- |
| first accepted push | `201 {"ok":true,"seq":1}` |
| replayed seq 7 | `409 {"error":"replay_rejected","latest":7}`, keys exactly `error\|latest` |
| regressed seq 3, high-water 50 | `409 … latest=50` |
| `p2e` seq 4 replayed while `e2p` holds 90 | `409 … latest=4` — **per direction, not per pairing** |
| unparseable body | `400 {"error":"bad_request"}` |
| `seq: 0` (header shape) | `400 {"error":"bad_request"}` |
| oversize ciphertext | `413 {"error":"too_large"}` |
| first push on a direction holding nothing | `201`, even at seq 1 |
| `GET /pull?dir=p2e&since=0`, empty direction | `200 {"envelopes":[],"latest":0}` |

**A practical note that cost two runs:** vitest truncates long diff values mid-string
(`'409 {"error":"replay_rejected","lates…'`), so the first two probe passes measured nothing useful.
Put the value being measured **first and short** — `L=4 K=error|latest S=409`. Written into C-S6C-2
so the next session does not pay it again.

**The probe file was deleted before committing.** The relay suite measured **36 / 0 both before and
after**, so this is a measurement and not a suite member, and `git status --porcelain` was empty
after the delete. **36 is this branch's figure**; `STATE.md`'s 42 belongs to
`claude/s2-relay-retention` and reading one as the other is the count-drift trap one branch over.

**Two results contradicted nothing and were pinned anyway.** `latest` is per-direction — a sender
reading it as a pairing-wide position would resume far too high, skipping seqs and creating gaps the
receiver reads as legitimate under §6.2. And a direction holding nothing accepts seq 1 rather than
answering 409 with `latest: 0`, because the monotonicity check has no prior value to compare
against.

### S6C-4 §2.2, and the namespace collision underneath it

New **§2.2** pins all four push responses, the 409's `latest` as REQUIRED on that status and
explicitly per-direction, 400/413 as carrying no counter evidence at all, and 201 as meaning
*appended* and nothing more — not that any receiver accepted, decrypted or applied anything.

The part that is more than bookkeeping: **§7.2's error codes and the relay's HTTP error codes are
different namespaces, and two names overlap with identical meanings.** §7.2 defines the sealed
`error` *payload*, `{code, detail?, ref_seq?}`, invisible to the relay. The relay answers HTTP with
`{"error": …}`. Measured — `grep -rho "error: '[a-z_]*'" relay/src/*.ts | sort -u` yields **eight**
transport codes, of which `bad_request`, `unauthorized`, `not_found`, `method_not_allowed` and
`upgrade_required` appeared **zero times** anywhere in `Sync-Protocol.md` before this commit.

**The overlap is the dangerous half, not the gap.** An implementer who reads §7.2, sees
`replay_rejected`, then receives `{"error":"replay_rejected","latest":7}` has every reason to parse
it as a payload error — look for `code`, find none, fall through to a generic failure. **That is
exactly how the engine came to discard it** (S6C-6). Recorded as **PQ-S2-3**; v1 pins push's mapping
and no other route's, because inventing five more from a sandbox that cannot run `Verify-Alpha.ps1`
is the size-cap mistake's shape.

### S6C-5 §6.1, generalised — and why this is not the §2.1 mistake

The rule now reads: a sender MUST resume above
`max(persisted_seq, relay_latest_seq_for_that_direction)`, with **both** sources of the relay's
number named (§2.1's pull `latest`, §2.2's 409 `latest`). The engine's e2p case is kept as the
worked example because its consequence is the severe one — the recovery `snapshot` itself rejected,
"a silent, total, one-sided sync death" — and the phone's p2e obligation is stated, with its milder
consequence stated too: **marks and entitlements stall rather than the dashboard dying, and that is
why the asymmetry was easy to write rather than why it is optional.**

**This writes a MUST that neither shipping sender meets, which is normally the thirteenth run's
defect** — §2.1's first draft required an error *type* the engine does not use, and the eighth
correction names "a spec tightening ahead of its implementations" as the same bug as an
implementation tightening ahead of its spec. Three things separate this from that, and they are in
the section rather than only here:

1. **The rule was already normative for one of the two senders.** Generalising it did not invent an
   obligation; it removed an exemption the document never argued for.
2. **Persistence was already required of both** by §6.1's own first sentence. Only the *recovery*
   half was engine-only.
3. **It is a safety property, not error-reporting style.** §2.1's over-reach was about which error
   type to raise, where both behaviours were safe and the two receivers could reasonably differ.
   Nothing here is a matter of taste.

And the section says out loud that neither sender conforms, in a measured conformance note naming
both gaps by ID. **A spec that quietly outruns its implementations is a defect in the spec; one that
states the gap is a work item.**

### S6C-6 The finding, and it inverts the section it came from

Checking the engine against the rule *before* writing it — the method the eighth correction
prescribes — found that **the engine implements half of §6.1 and its own comment states the other
half.** `src/Engine/Program.cs:288`:

```csharp
        startSeq: paired.LastE2pSeq);
```

The persisted term only. No `max(…)`, and `grep -n "PullAsync" src/Engine/Program.cs` prints
**nothing** — the relay is never consulted on the startup path. Ten lines above, at
`src/Engine/Program.cs:239-243`, the comment states the rule verbatim: *"this method MUST construct
the publisher with `startSeq = max(vault.last_e2p_seq, relay latest e2p)` — Sync-Protocol.md §6.1"*.
**The comment and the code disagree, and the comment is right.**

**The second half compounds it.** `RelayClient.PushAsync` (`src/Sync/RelayClient.cs:51-60`) returns
`res.StatusCode is HttpStatusCode.Created` — a bare `bool`. A 409 is indistinguishable from a
timeout, a 400 or a 413, and the `latest` the relay puts in that body is discarded unread. **So the
engine can neither reconcile up front nor recover from the refusal that tells it to** — while the
phone, which §6.1 never asked, reads the number and reconciles through `OutboundQueue`. The section
asked the wrong sender.

**Stated precisely, because it is milder than it first looks and overstating it would be this
window's recurring error.** `SyncPublisher` assigns `seq` with `Interlocked.Increment` *before* the
sink runs (`src/Sync/SyncPublisher.cs:90`) and the vault records the mark only on success
(`Program.cs:285`). So a stale vault does not deadlock: each refused push burns one seq, the next
attempt is one higher, and the counter climbs back on its own. **The cost is one dropped envelope
per burned seq.** If the vault is behind by N, that is N envelopes silently discarded — *including
the recovery `snapshot`* if it falls in the run — each returning `false` to a caller with no retry.
§6.1's named catastrophe is **mitigated into a window rather than prevented**, and nothing reports
the window. Recorded as **PQ-S6-3**, with the two-commit fix written out.

**Not fixed here: it is C#, and `which dotnet` prints nothing.** Unblocked and merely unwritten — a
local session's, not a phantom blocker, and deliberately **not** filed in `BLOCKED.md`.

### S6C-7 Ladder effect, stated narrowly

**S6 stays PARTIAL and this slice does not move it toward DONE.** It closed a *protocol question*
against S6's send path, not the path itself: the remainder is still the Keystore key (**B-4**) and
the `:app` wiring (**B-7**), and neither was touched. `OutcomeMarkPolicy` and `OutboundQueue` still
have no production caller. **A rung does not advance because work happened in its neighbourhood** —
the fifth iteration in a row to write that sentence, which is itself worth noticing.

What did move: **B-8 is now a smaller and better-specified hole.** Its spec half is closed — §6.1
states the rule this sender must satisfy — and the measurement sharpened: `SeqSource`'s only
implementation in the tree is a **test double** (`OutboundQueueTest.kt:30`), with zero `:app`
references, so there is no production counter to persist rather than an in-memory one to replace.

### Boundary — what was not touched

**Nothing was merged, in either repo.** Android PR #6 stays a draft; main-repo PRs #32 and #33 stay
drafts and were neither merged, retargeted, rebased nor force-pushed. Both branches were pushed
**forward-only** — no force-push, no history rewrite, no branch deleted, in either repo.

**Main repo: one file written** (`docs/Sync-Protocol.md`, on `claude/s4-pull-request-semantics`),
plus the coordination bus. **No `.cs`** — the engine finding is recorded, not fixed. **No `relay/`
source file**: the probe was a throwaway test, deleted before committing, and `git status` was clean
after. No harness, no `Verify-Alpha.ps1`, no `$ExpectedOfflineTotal`, no count-reporting doc, **no
vector byte and no `generate.mjs` change** — `node docs/sync-vectors/generate.mjs --check` →
`OK: 28 vector files match the generator.`, exit 0.

**Android repo: `docs/protocol-questions.md` plus these records. Zero `:core` files, zero `:app`
files, zero Kotlin of any kind**, no Gradle or version-catalog file, no emulator, no `sdkmanager`,
no AVD, and no attempt to route around the `dl.google.com` denial. The vendored vector pin stays
`679a317` and **no vendored byte was touched**.

**Neither gate ran and neither could.** `Verify-Alpha.ps1` needs .NET (`which dotnet` → nothing);
the android gate needs an SDK (B-7). **CI is the gate**, and nothing here asserts otherwise.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages) and **no `wrangler` invocation at
all** — the relay ran only under `vitest`/miniflare inside the test runner. **The production relay
was contacted zero times, not even `GET /v1/health`.**

No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email
or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password
file were neither read nor referenced beyond their paths. **No secrets read, written or printed.**

Terra's state was read at iteration start: still R6(b) BLOCKED on draft PR #26, heartbeat unchanged
at 2026-08-07T21:18, **claims no files** — no collision. This slice's only main-repo territory is
`docs/Sync-Protocol.md`, already claimed via #32/#33, so no new claim was taken.

---

## S2Q — The `seq` field had no maximum, and the wedge reached further than the question recorded (sixteenth cloud iteration, 2026-08-11)

Linux cloud sandbox. No .NET, no Android SDK, no emulator, no Windows. **Neither gate ran**; CI is
the gate, and where CI is the evidence the run id is cited.

### S2Q-1 Why this slice, and the deferral that turned out to be inherited rather than derived

`git fetch --all --prune` in both checkouts first, per rule one. Both clean. Counts below are all
post-fetch.

**The standing prompt again nominated S5's spec half** — §4.3 `entitlement_ack`, the ack vector,
PQ-A2-1/-2/-3 — and again described S5 as "NOT STARTED and genuinely NOT blocked". That is the
fourth correction in `STATE.md`, now in its fourth iteration of being right: all of it except
PQ-A2-3 landed 2026-08-09 on `claude/s5-entitlement-ack-spec` (draft PR #32), and PQ-A2-3 is **B-6**
(the engine has no inbound wire-JSON parser, so the vector would assert a rejection nothing can
perform). **Verified rather than inherited** — `git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec`
prints the same four commits it did last iteration. The prompt is a stored snapshot; the records are
the state.

So the pick came from the open-question list. **PQ-S2-2**, and the reason it was still open is the
interesting half.

**It had been carried as "do not close from a sandbox" for two iterations, and that was inherited
from its neighbour.** PQ-S2-1 and PQ-S2-2 were opened in the same iteration and summarised together
ever since. PQ-S2-1's "to close" genuinely does begin *"On a machine with .NET"* — closing it means
changing two engine test fixtures that emit non-conforming pairing ids. **PQ-S2-2's does not.** Read
in full, it says: *"A §3 amendment giving `seq` an explicit maximum, then the relay enforcing it as
a `400`. … Spec first, relay second."* Markdown and TypeScript — both of which run here.

**That is the fifteenth run's ninth correction arriving one iteration later in a different
costume.** That one was about a deferral reason that was an argument about *cost*, made before
anyone checked the substance. This one is about a deferral reason that belonged to a *different
question* and was never about this one at all. **Before inheriting a deferral, check which question
it was written against.**

### S2Q-2 What was measured, before anything was written

The eleventh run's probe method, reused: throwaway vitest tests under miniflare asserting
deliberately **wrong** values so the runner prints the measured one in its diff, with the value
**first and short** per C-S6C-2's lesson about vitest truncating long diffs. Fourteen probes. The
file was deleted before committing and `git status --porcelain` was empty after.

Baseline first, so the count means something: **`Tests 42 passed (42)`** on
`claude/s2-relay-retention`, this slice's base.

| probe | measured |
| --- | --- |
| `seq` = 9007199254740991 (2⁵³−1) | `201 {"ok":true,"seq":9007199254740991}` |
| then `seq` = 1 | `409 {"error":"replay_rejected","latest":9007199254740991}` |
| **`seq` = 1e300** | **`201 {"ok":true,"seq":1e+300}`** |
| pull after 1e300 | `200 … "latest":1e+300` |
| then `seq` = 2 | `409 … latest: 1e+300` |
| `seq` = 2⁵³ then 2⁵³+1 | `201`, then **`409 replay_rejected, latest: 9007199254740992`** |
| `seq` = 1.5 | `400 bad_request` |
| `seq` = 1e400 (→ `Infinity`) | `400 bad_request` |
| `Number.isInteger(1e300 / 2⁵³ / Infinity)` | `true / true / false` |
| `seq` = 2⁶² = 4611686018427387904 | pull reports **`"latest":4611686018427388000`** |
| `seq` = 1e19 | pull reports `"latest":10000000000000000000` |
| `seq` = 1e21 | pull reports `"latest":1e+21` |

**Three findings the question did not have.**

**(a) The reachable ceiling was not `MAX_SAFE_INTEGER`.** PQ-S2-2's measurement stops there and
reads as though that were the boundary. `Number.isInteger` is **not a range check**: it rejects a
fractional value but **cannot reject a large one**, because every double at or above 2⁵³ is
necessarily integral — the format has no bits left for a fraction there — so the predicate is
vacuously true across exactly the range this rule cares about. The accepted range ran to ~1.8e308,
and `Infinity` is refused only as a side effect of `Number.isInteger(Infinity)` being `false`.

**(b) The read path breaks, and it is the severe half.** The question costed the wedge on the write
path only. But `latest` is emitted from the same double, and **both receivers parse it strictly**:
`src/Sync/RelayClient.cs:74` is `GetProperty("latest").GetInt64()` with **no catch on that path**,
and the phone's `strictLong` goes through `toLongOrNull()` (`core/.../RelayClient.kt:258-262`) to a
page rejected as `Unavailable`. Above `Long.MaxValue` the number renders as plain decimal neither
can parse; past 1e21 it renders in exponent notation. **So one garbage counter disables the
`GET /pull` reconciliation §6.1 prescribes for exactly that situation** — it takes out the
instrument used to diagnose it. That is what moved this from a note to a rule.

**(c) "Unreachable in practice" was wrong.** The question dismissed the precision divergence because
"2⁵³ envelopes is not a number this product produces". True and irrelevant: **reaching 2⁵³ does not
require sending 2⁵³ envelopes, only emitting one counter that large.** Measured, 2⁵³ then 2⁵³+1
answered `201` then `409 replay_rejected` — a strictly **larger** integer refused as a **replay**,
because both land on the same double. One buggy sender does that in one step.

### S2Q-3 §3.2, and why the bound is a property of the wire rather than of the relay

New **§3.2**: `seq` MUST NOT exceed **`2^53 - 1`**. Sender MUST NOT emit above it; relay MUST refuse
with `400 bad_request`; receiver **SHOULD** reject as a structural rejection.

**`2^53 - 1` is chosen at the point where the wire stops being unambiguous**, not for the relay's
convenience: both receivers type `seq` as a 64-bit integer (`src/Sync/EnvelopeCodec.cs:7`
`long Seq`; the Kotlin header likewise) and the relay reads it into a double, so this is the largest
integer **all three represent exactly**. A cap picked to suit the relay would be §3.1's size-cap bug
again. `MAX_SEQ` is spelled `Number.MAX_SAFE_INTEGER` — the derivation, not a literal — per the
lesson `relay/src/protocol.ts` already records about round numbers.

**Spec first, relay second, deliberately.** Refusing an envelope the document declares legal is the
one thing §3.1's amendment forbids, so the bound had to be *stated* before `channel.ts` could
*enforce* it. Two commits, in that order.

**`SHOULD` on the receiver, and this is the softest thing in the slice.** A `MUST` would be a spec
tightening ahead of two shipping implementations that do not do it — the eighth correction's defect,
and the §2.1 precedent. The argument for `SHOULD` is that **the relay is the only ingress**, so a
receiver check is defence in depth rather than the property being protected. §3.2 states the
non-conformance in a measured note rather than implying conformance. **If ingress ever stops being
single — a direct peer mode, or a live path that does not transit `push` — the argument fails and
this becomes a `MUST` plus two code changes.** Written into the PR's self-audit as the first thing
to attack.

### S2Q-4 The relay half, and the test that had to be proven rather than trusted

`relay/src/channel.ts` gained `|| seq > MAX_SEQ` inside the existing header-shape `if` — **above**
the `SELECT MAX(seq)` block, so a refused envelope is never appended and the `400` carries no
counter evidence.

Suite **42 → 51**. **Seven of the nine new tests were proven to fail against the previous
`channel.ts`** — the guard was reverted and the suite re-run, giving `Tests 7 failed | 44 passed
(51)`, then restored to `51 passed (51)`. **The other two pass either way**, and they are labelled
as pins rather than regression catchers: the boundary value being accepted, and `latest` staying
inside the range both receivers can parse. **That labelling is the thirteenth run's second lesson
applied forward** — a test that still passes after a behaviour change is the one to read, so rather
than wait to be surprised by one, each new test was checked for which side of that line it sits on.

`node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the generator.`, exit 0.
**28 is the branch figure; `main` is 26.** No vector byte moved: a `seq` **range** rule cannot be
expressed as a §3 vector without the inbound wire-JSON parser **B-6** is waiting on, which is the
same wall PQ-A2-3 sits behind.

### S2Q-5 What CI said, because I could not say it

Draft PR **#35** (`claude/s2-seq-bound`, stacked on #34 → #32). **Two runs, because a wording fix
landed after the code** — and the branch tip is the one that counts. Run **31494720248** on
`0af7012` and run **31495565325** on **`2be00fc`**, the tip: **both jobs `success` in both**. From
the *Build and offline harnesses* job log on the tip (id 93792278316):
`=== 130 passed, 0 failed ===` and **`=== Offline total: 598 passed, 0 failed ===`**, then
`CareerSeeker alpha verification complete.` — so `Verify-Alpha.ps1` ran in full and the pin is
confirmed **on the head being reviewed**, not on an intermediate commit. **So the 598
pin is confirmed by observation, not argued from the diff** — and the *Blind relay (Worker)* job
ran this slice's tests on a machine that is not mine.

**`SyncLiveSmoke` was not re-run and this slice does not claim it passes.** It pushes seqs from 1
and should be unaffected by a bound at 2⁵³−1 — **that is reasoning, not evidence**, and it is
written into the PR that way. `tsc --noEmit` prints 55 unresolved-`Env` errors because the project's
typecheck is `wrangler types && tsc --noEmit` and **no `wrangler` was invoked**; the count was
measured **identical on the base branch and on this one**, which is the only claim it supports.

### S2Q-6 Ladder effect, stated narrowly

**S2 stays PARTIAL and this slice does not move it toward DONE.** **B-2 is still exactly the missing
desktop `/pair` page**, which is C# and unreachable here. This is the **third** hardening of S2's
transport half — size cap (ninth run), retention predicate (eleventh), now the `seq` bound — and
hardening a rung's transport is not the same as advancing the rung. **Sixth iteration in a row that
this sentence has been written**, which by now is less an observation than a property of doing
sandbox-reachable work on a ladder whose remaining rungs need machines.

**PQ-S2-2 is closed in part, not closed.** The bound stops a channel being wedged **out of range**
and does nothing for one wedged **in** range: a sender emitting `9007199254740991`
legitimately-shaped still bricks the direction until TTL or unpair. The question's own last line
asked for the reset question to be settled alongside the cap; **it was not, because it is a product
decision** — a channel reset is a new authenticated destructive route, and inventing one here is the
size-cap mistake's shape. **That is for Brandon.** Recorded as the open half, and deliberately
**not** filed in `BLOCKED.md`: nothing blocks it, it is a decision that has not been made.

### Boundary — what was not touched

**Nothing was merged, in either repo.** PR #35 was opened as a **draft**; #32, #33 and #34 stay
drafts and were neither merged, retargeted, rebased nor force-pushed. Android PR #6 stays a draft.
Both branches pushed **forward-only** — no force-push, no history rewrite, no branch deleted, in
either repo.

**Main repo: four files written** — `docs/Sync-Protocol.md`, `relay/src/protocol.ts`,
`relay/src/channel.ts`, `relay/test/relay.test.ts` — plus the coordination bus. **No `.cs`**: the
engine is described at `RelayClient.cs:74` and not edited. **No harness, no `Verify-Alpha.ps1`, no
`$ExpectedOfflineTotal`, no count-reporting doc, no vector byte and no `generate.mjs` change.**
Verify with `git diff --stat origin/claude/s2-relay-retention..claude/s2-seq-bound` — four files.

**Android repo: `docs/protocol-questions.md` plus these records. Zero `:core` files, zero `:app`
files, zero Kotlin of any kind**, no Gradle or version-catalog file, no emulator, no `sdkmanager`,
no AVD, and no attempt to route around the `dl.google.com` denial. The vendored vector pin stays
`679a317` and **no vendored byte was touched**.

**Neither gate ran and neither could.** `Verify-Alpha.ps1` needs .NET (`which dotnet` → nothing);
the android gate needs an SDK (B-7). **CI is the gate**, and nothing here asserts otherwise.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages) and **no `wrangler` invocation at
all** — the relay ran only under `vitest`/miniflare inside the test runner, and `npm ci` fetched from
the npm registry only. **The production relay was contacted zero times, not even `GET /v1/health`.**

No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email
or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password
file were neither read nor referenced beyond their paths. **No secrets read, written or printed.**

Terra's state was read at iteration start **and again before writing the bus entry**: still R6(b)
BLOCKED on draft PR #26, heartbeat unchanged at 2026-08-07T21:18, **claims no files** — no
collision. `relay/src/protocol.ts` and `relay/src/channel.ts` are now **written to** rather than
merely held, and the bus says so; Terra has right-of-way and I rebase on request.

---

## S2T — `pairing_unknown` never means the pairing is unknown (seventeenth cloud iteration, 2026-08-11)

**Environment:** Linux cloud sandbox. **No .NET** (`which dotnet` → nothing), **no Android SDK**
(B-7). Neither gate could run here, and nothing below claims one did — **CI is the gate**, and it is
cited by run and job id.

### S2T-0 Why this slice, and not the one the iteration prompt asked for

The prompt assigned **S5**, on the stated basis that S5 was "NOT STARTED and genuinely NOT blocked".
**Derived after the mandatory fetch, that is false, and the correction is the first evidence in this
entry.** `origin/claude/s5-entitlement-ack-spec` has carried four commits since **2026-08-09** and is
open as **draft PR #32**:

```
$ git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
9c05ef7 S5: correct 3.1's relay paragraph -- it reasoned in one direction only
a564c0c S5: the relay refused envelopes the protocol declares legal -- derive its cap
22b028e S5: pin section 4.3.3 with two entitlement_ack vectors, generated not hand-written
8575539 S5: define the entitlement_ack body, and say what the size cap actually measures
```

So every named sub-task was already done: §4.3.3 defines `{product_id, acknowledged_at, order_id?}`
per PQ-A6-1; the two `entitlement_ack` vectors exist (28 files, up from `main`'s 26); PQ-A2-1 and
PQ-A2-2 are closed. **The one exception is PQ-A2-3**, whose vector cannot be added: **B-6**, engine
has no inbound wire-JSON parser, so the vector would assert a rejection the engine does not perform
and would turn the offline gate red for whoever pushes next. That is C# and there is no .NET here.
**The prompt's own instruction covers this case** — pick the topmost genuinely verifiable rung and
justify it — so this iteration took **PQ-S2-3**, which the fifteenth run left open with option (a)
recommended, and which is relay + spec work that this sandbox *can* execute.

**A stale iteration summary is worth recording as a finding in itself.** The prompt described the
ladder as of roughly the fourth iteration; thirteen have run since. The mandatory fetch is what
caught it, which is the whole point of rule one.

### S2T-1 The question's own table was short by one, and its re-verification command proved it

PQ-S2-3 said the relay emits **eight** transport codes. Running **its own command on the commit it
cited**:

```
$ mkdir -p /tmp/pqcheck
$ git archive origin/claude/s4-pull-request-semantics relay/src | tar -x -C /tmp/pqcheck
$ grep -rho "error: '[a-z_]*'" /tmp/pqcheck/relay/src/*.ts | sort -u
bad_request  exists  method_not_allowed  not_found  pairing_unknown
replay_rejected  too_large  unauthorized  upgrade_required
```

**Nine.** `exists` was dropped in transcription, not by the grep — and `git grep 'exists' origin/main`
confirms it predates the question, so this was never a later addition. It is emitted by **two**
routes for **two** different conditions (`create` on an existing channel, `pair` on an already-stored
completion), so it was never marginal.

**`AUDIT-REQUEST.md`'s C-S6C-5 inherited the error and became self-contradicting**: its command
returns nine while its claim and its *Expected* line both said eight, so anyone running the
re-verification as written would have seen it fail. Corrected in place, with the correction stated
rather than silently overwritten.

### S2T-2 The measurement, and the finding it turned up

`relay/test/_measure.test.ts` (scratch, deleted before commit) exercised every route under
miniflare. `console.log` is swallowed in the workerd pool, so the matrix was forced out through a
deliberate `throw` — noted because it is the trick the next session will need.

The finding is in the last five rows. **After `DELETE /v1/{pairing}` — the exact condition §7.2 names
`pairing_unknown`, "the relay has no Durable Object for this pairing":**

```
401 {"error":"unauthorized"}   GET  /v1/{p}/pull?dir=e2p&since=0
401 {"error":"unauthorized"}   POST /v1/{p}/push
401 {"error":"unauthorized"}   GET  /v1/{p}/pair
401 {"error":"unauthorized"}   DELETE /v1/{p}
201 {"ok":true}                POST /v1/{p}/create      <- the id re-bootstraps
```

**The transport `pairing_unknown` is never emitted for this.** It fires only on a pairing-id *shape*
failure (`relay/src/index.ts:56`), checked before authentication. A well-formed id that was never
created also answers **401**. So the transport code's name describes a condition it is never emitted
for, and the condition it is named for has **no transport code at all**.

**What it costs the phone — and this half is READ, not executed.** `RelayClient.kt:283-284` maps any
404 → `PairingUnknown`; `OutboundQueue.kt:267-269` maps `PairingUnknown` → `SendHalt.PAIRING_GONE`
(**terminal** — `OutboundQueueTest.kt:269` is literally named *`pairing_unknown` is terminal and no
clearing call revives it*) and `Unauthorised` → `SendHalt.UNAUTHORISED`, which
`OutboundQueue.kt:288-290` clears "when a fresh bearer is in hand". **So a genuinely unpaired phone
halts on the recoverable state and waits for a bearer that cannot exist**, while the terminal state
built for exactly this is never entered. It appears unreachable outright: no route the phone calls
can 404 — the malformed-id path is impossible because `RelayClient.init` requires
`isValidPairingId`, and the one transiently-404ing route (`GET /pair`, answered `not_found` both
before a completion is posted and after the engine's one-shot read) **is never called by the phone**.

**That reachability claim is a hypothesis with file:line support and is unverified by execution.**
`./gradlew … :core:test` did not run and could not (B-7). The relay half is measured; the phone half
is read. Recorded as **PQ-S2-4**, deliberately *not* as a blocker — nothing is blocked, a decision
has not been made.

### S2T-3 §2.3, written descriptively, and why that direction is the load-bearing part

New **§2.3** pins `create`, `pair`, `pull`, `live`, `DELETE` and `health` — statuses and bodies —
plus three rules: key off the HTTP status; `{"error": …}` is transport and `{"code": …}` is payload;
409 is three different answers, and only push's carries a number.

**Every line was read off the running Worker and written down second, so `relay/src/` is
byte-identical on this branch** (`git diff …-- relay/src/` → empty). Nothing new is refused. That is
deliberate and it is the §3.1 size-cap lesson applied: a transport section written from the spec
downwards is how a relay comes to reject what the document declares legal.

**v1 pins the 401 rather than adding a code**, and §2.3 says why: a purged pairing is
indistinguishable from one that never existed, so the relay never answers "did this pairing ever
exist?" to a caller holding a wrong credential — and the measured re-bootstrap proves there is no
tombstone to disclose. **Whether that privacy property is worth more than the phone being able to
tell it was unpaired is Brandon's call, not a spec section's**, and PQ-S2-4 says so.

A second correction the measurement forced: §2.2 says "two names appear in both vocabularies". The
intersection of the nine transport and ten §7.2 payload codes is **three** — `replay_rejected` and
`too_large` agree, **`pairing_unknown` does not**. §2.2's sentence is *true as written* (it says two
collide *with the same meaning*); the third name is the dangerous one and nothing said so.

### S2T-4 The tests, and the honest label on the one that is not proven

**36 → 49.** Because no relay code changed, **all thirteen are pins by construction — none of them CAN
fail against the current source** — so rather than assert they were useful, each was checked against
a deliberately mutated relay. Four mutations, each reverted:

| mutation | caught by |
| --- | --- |
| purged channel answers 404 `pairing_unknown` | `means the id is MALFORMED`, `after unpair every route answers 401` |
| `GET /pair` empty → 204; `DELETE` drops `purged` | `ordinary "nothing waiting" case`, `{ok,purged:N}` |
| create-conflict drops `exists`; a 400 gains a `hint`; rotation drops its flag | `exactly nine codes`, `409 carries three bodies`, `{ok,rotated:true}`, `every error body is exactly {error}` |
| worker-level 405 → 404 and 426 → 400 | `405 is reachable only for…`, `live answers 426` |
| `/pair`'s cap counts bytes instead of characters | `cap counts characters, not bytes` (§S2T-8) |
| `rotate_to` regex relaxed to accept uppercase hex | `rotate_to is LOWERCASE hex` (§S2T-9) |

**Twelve of thirteen proven. The thirteenth — `unpair is not a tombstone` — is NOT proven and is labelled a
pin**, since breaking it needs a future change rather than a mutation of today's code. This is the
thirteenth run's lesson applied forward: a test that still passes after a behaviour change is the one
to read, so each was checked for which side of that line it sits on rather than waiting to be
surprised.

**An audit command that did not reproduce its own expected output was caught and fixed before it
shipped.** The first `sed` recipe written into C-S2T-6 matched **three** call sites, not one, and
produced `5 failed` against the documented `4 failed`. It was run, the mismatch showed, and it was
replaced with a single-site mutation plus a `git diff --numstat` guard so the next reader catches an
over-broad match. **A re-verification command that does not reproduce its own claim is worse than no
command, because it reads as evidence.**

### S2T-5 What CI said, because I could not say it

Draft PR **#36** (`claude/s2-transport-vocabulary`), stacked **#33 → #32 → `main`**. Run
**31516194482** on **`4db3543`**, the branch tip — **both jobs `success`**:

- *Build and offline harnesses* (`windows-latest`, job 93861817135): `=== 130 passed, 0 failed ===`
  for `SyncHarness`, then **`=== Offline total: 598 passed, 0 failed ===`** and
  `CareerSeeker alpha verification complete.` — so `Verify-Alpha.ps1` ran in full on a machine that
  is not mine, and **598 is unchanged**. The PR predicted the total would not move on the grounds
  that no `.cs`, no harness and no `$ExpectedOfflineTotal` was touched; that prediction is now
  **observed rather than reasoned**.
- *Blind relay (Worker)* (`ubuntu-latest`, job 93861817039): green, including *Typecheck*, *Test*,
  *Assert the relay has no decryption path* and *Assert sync vectors match their generator*.

Locally: `node docs/sync-vectors/generate.mjs --check` → `OK: 28 vector files match the generator.`,
exit 0. `npx tsc --noEmit` prints **55** errors here, all unresolved `Env`/`Response`, because the
project typecheck is `wrangler types && tsc --noEmit` and no `wrangler` was invoked — measured
**identical on the base branch and this one (55 = 55)**, which is the only claim it supports. CI's
*Typecheck* step passes because it runs *Generate runtime types* first.

### S2T-6 A stack-topology hazard that predates this slice, and that no PR in the stack mentions

**§2.1 and §2.2 do not exist on the `seq`-bound line.** They landed on `claude/s4-pull-request-semantics`
(#33); `claude/s2-relay-retention` (#34) → `claude/s2-seq-bound` (#35) branch off **#32 as siblings**.
Both lines edit `docs/Sync-Protocol.md` off the same base `9c05ef7`:

```
$ git merge-base origin/claude/s4-pull-request-semantics origin/claude/s2-seq-bound
9c05ef7
$ git diff --stat 9c05ef7..origin/claude/s4-pull-request-semantics   # 1 file, +306
$ git diff --stat 9c05ef7..origin/claude/s2-seq-bound                # 4 files, +311
```

This slice was **re-based onto #33 after starting on #35**, because extending §2.2 from a branch
without §2.2 would have written a section referring to nothing. Measured with `git merge-tree`, the
two lines **merge cleanly — before this PR and after it** (exit 0, no conflict list), because #33's
additions sit in §2 and the other line's in §3. New tests were placed near base line ~90, away from
#35's hunks at ~199 and ~327, so the diamond was not made worse. **Merge order is still a human
decision and nothing in the stack records this**, which is why it is here and in the PR self-audit.

### S2T-7 Ladder effect, stated narrowly

**S2 stays PARTIAL and this slice does not move it toward DONE.** B-2 is still exactly the missing
desktop `/pair` page, which is C# and unreachable here. This is the **fourth** hardening of S2's
transport half — size cap (ninth run), retention predicate (eleventh), `seq` bound (sixteenth), now
the response vocabulary — and hardening a rung's transport is not advancing the rung. **Seventh
iteration in a row that this sentence has been written.**

**PQ-S2-3 is closed. PQ-S2-4 is opened and is not a blocker.** S5's spec half remains closed and its
applier half remains unwritten, which is a local session's slice: two appliers, two languages,
neither compilable here.

### S2T-8 A twelfth test, and the finding that came from auditing my own draft

**The §2.3 table originally said `POST /pair` refuses "body over 16 KiB".** That was copied from how
the constant *looks* (`16 * 1024`) rather than from what it *does*: the check is `raw.length` on the
decoded string, so the unit is **UTF-16 code units**. Measured before the row was corrected:

```
413  16385 ASCII chars (16385 bytes)
400  16384 ASCII chars (16384 bytes)        <- under the cap, fails later on JSON.parse
400  16384 x 3-byte chars (49152 BYTES)     <- under the cap at 3x the bytes
413  16385 x 3-byte chars
```

**The effective byte ceiling is up to 3× what the constant looks like**, and this is the *same*
character-versus-byte conflation §3.1 was amended to fix on 2026-08-09 — in a second place, which
nobody had written down. Had the row shipped as drafted, `Sync-Protocol.md` would have asserted a
byte budget the relay does not enforce: **the §3.1 bug's exact shape, written into the document that
exists to prevent it.**

**v1 pins the measured behaviour rather than correcting it**, on §3.1's own reasoning: tightening the
relay to a byte count refuses bodies this document has never declared illegal, and the completion is
a small pairing document whose worst case is a bounded over-allocation, not a security property. The
cap is stated in the unit it uses, plus the rule for any future amendment — **state the byte budget
and derive the character constant from it, never the other way round**, which is what
`MAX_CIPHERTEXT_B64U_CHARS` already does.

Suite **47 → 48**, and that test is **proven live**: mutating the cap to `new TextEncoder()
.encode(raw).length` fails this test **and only this test**. With §S2T-9 that becomes **twelve of thirteen proven**; the
unproven one is still `unpair is not a tombstone`, still labelled a pin.

**The method is the transferable part.** This was not found by reading `channel.ts` — it was found by
**re-auditing my own table against the source before shipping it**, one row at a time. Three of this
iteration's findings came from the same move: the nine-vs-eight code count, the self-contradicting
`sed` in C-S2T-6, and this. **A draft's own claims are the cheapest place to find a defect and the
last place anyone looks.**

### S2T-9 A thirteenth test, from the same move, and this one is an interop trap

The §2.3 row for `POST /create`'s 400 said "`rotate_to` was not 64 hex chars". The regex is
`/^[0-9a-f]{64}$/` — **case-sensitive**, which the row did not say.

**That is not pedantry, because C#'s `Convert.ToHexString` returns UPPERCASE.** The engine's only
rotation caller (`tests/SyncLiveSmoke/Program.cs:84`) is correct **solely** because it appends an
explicit `.ToLowerInvariant()`. Remove that one call and rotation is refused with a bare `400` —
and `RelayClient.RotateTokenAsync` returns `res.IsSuccessStatusCode`, a bare `bool`
(`src/Sync/RelayClient.cs:30-38`), so the failure is **indistinguishable from a network error**, on
the one call in the protocol that is **one-way** and locks the engine out of the channel if it
half-succeeds. The codebase's own habit is right — `Convert.ToHexString(...).ToLowerInvariant()`
appears throughout `src/` — but **a habit is not a test**, and nothing stated the requirement.

Now both state it: §2.3 says lowercase and says why, and the suite pins it. Suite **48 → 49**,
**proven live** — relaxing the regex to `[0-9a-fA-F]` fails this test and only this test.

**Nothing was changed in the relay to accommodate it.** Current behaviour is correct; the defect was
in the *document*, and the fix is the document plus a test. Relaxing the regex would be the size-cap
mistake pointing the other way — loosening what the relay accepts from a sandbox that cannot run the
engine's gate.

### S2T-10 The android CI job hung on a docs-only commit, measured against its own baseline

Not part of the slice, and recorded because it is the kind of thing that reads as normal slowness
until someone compares it to a number.

The android records push (`c68ef07`, **five Markdown files and nothing else**) triggered run
**31517760672**. Its `Unit tests (:app, Robolectric)` step started **17:32:31** and was **still
`in_progress` 25 minutes later**, with `Assemble debug APK`, `Lint` and the tracker check all still
`pending`.

**The baseline is the same branch four hours earlier**, run **31498538679** on `3bf152c` —
`success`, and the only difference between the two heads is this iteration's Markdown:

| step | `3bf152c` (success) | `c68ef07` |
| --- | --- | --- |
| `Unit tests (:app, Robolectric)` | 13:56:15 → 13:57:48 = **93 s** | 17:32:31 → **still running at 25+ min** |
| whole job | 13:53:14 → 14:00:40 = **7 m 26 s** | not reached |

**So it is ~16× the previous duration on a diff that cannot have caused it.** Everything before that
step passed, including the two that matter most here: **`Assert vendored sync vectors match the
pinned main-repo commit` ✓** (so the `679a317` pin is intact and there is no cross-repo drift) and
`:core:test` ✓.

**What this is not:** it is not evidence about any code in this iteration, which touched no Kotlin.

**It recurred, on a different step, and that is what makes it diagnosable.** The records push that
recorded the first hang (`f49290e`, also Markdown-only) started run **31518284889**, which then hung
on **`Unit tests (:core)`** — baseline **50 s** (13:55:25 → 13:56:15), observed still `in_progress`
past **7 minutes**. So:

| run | head | hung on | that step's baseline |
| --- | --- | --- | --- |
| 31517760672 | `c68ef07` | `Unit tests (:app, Robolectric)` | 93 s |
| 31518284889 | `f49290e` | `Unit tests (:core)` | 50 s |

**Two different steps, two different runners, both docs-only commits** — so this is **not** the
Robolectric fragility **B-5** records (that would not touch `:core`, which has no Android
dependency by construction and is asserted so in the step before it).

**The sharper cut: everything that is not a test task ran at baseline speed.** On run 2, `Set up
Android SDK` took **27 s** against a 23 s baseline, `Assert :core has no Android dependency` **101 s**
against 97 s, and the vendored-vector assertion **5 s** against 5 s. `checkCoreIsAndroidFree` is
itself a Gradle task and it completed normally, so **Gradle is not broadly wedged** — it is the
**test-executing** tasks that hang, which points at forked test JVMs or the runner, not at the build.

**A correction to my own first reading, recorded because it was wrong in the record before it was
wrong anywhere else.** I initially called run 2 "slow at `Set up Android SDK`" — it was not; I read
an in-flight step as a slow one. It finished in 27 s. The conclusion (infrastructure rather than this
iteration's code) survives; the reason I first gave for it does not.

**Still not diagnosed, and deliberately not chased.** Two observations make a pattern, not a root
cause, and nothing here can attach a debugger to a GitHub runner.

**Also worth knowing, because it cost three runs this iteration.** The workflow cancels in-progress
runs on a new push to the same branch, so runs on `b394583`, `10e99c0` and `16f2451` all show
`cancelled` — each superseded by the next records commit. That is expected behaviour and not a
failure, but **a reader scanning conclusions will see three `cancelled` in a row and should not read
them as red**. The pattern was recorded once before (fourteenth run) and is recorded again because it
recurred.

### Boundary — what was not touched

**Nothing was merged, in either repo.** PR #36 was opened as a **draft**; #32, #33, #34 and #35 stay
drafts and were neither merged, retargeted, rebased nor force-pushed. Android PR #6 stays a draft.
Both branches pushed **forward-only** — no force-push, no history rewrite, no branch deleted.

**Main repo: two files written** — `docs/Sync-Protocol.md` and `relay/test/relay.test.ts`. **No
relay source change**: `git diff origin/claude/s4-pull-request-semantics -- relay/src/` is **empty**,
and that is the slice's central property, not an incidental one. **No `.cs`** — the engine is
described at `RelayClient.cs:51-60` and not edited. **No harness, no `Verify-Alpha.ps1`, no
`$ExpectedOfflineTotal`, no count-reporting doc, no vector byte, no `generate.mjs` change.** The
vendored vector pin stays `679a317` and no vendored byte moved.

**Android repo: `docs/protocol-questions.md` plus these records. Zero `:core` files, zero `:app`
files, zero Kotlin of any kind** — `RelayClient.kt`, `OutboundQueue.kt` and `OutboundQueueTest.kt`
were **read and cited, never edited**. No Gradle or version-catalog file, no emulator, no
`sdkmanager`, no AVD, and no attempt to route around the `dl.google.com` denial.

**Neither gate ran and neither could.** `Verify-Alpha.ps1` needs .NET; the android gate needs an SDK
(B-7). **CI is the gate**, cited above by run and job id, and nothing here asserts otherwise.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages) and **no `wrangler` invocation at
all** — the relay ran only under `vitest`/miniflare inside the test runner, and `npm ci` fetched from
the npm registry only. **The production relay was contacted zero times, not even `GET /v1/health`.**

No Google, Play, OAuth or Console action; no accounts, no purchases, no Play Billing code; no email
or Gmail anything; no cert-store, MSIX or keystore action — the upload keystore and its password file
were neither read nor referenced beyond their paths. **No secrets read, written or printed.**

Terra's state was read at iteration start **and again before writing the bus entry**: still R6(b)
BLOCKED on draft PR #26, heartbeat unchanged at **2026-08-07T21:18**, **claims no files** — no
collision. `relay/test/relay.test.ts` is written to on this branch; `relay/src/` is held but **not**
modified. Terra has right-of-way and I rebase on request.

---

## CP — the sandbox could run `:core` all along (eighteenth cloud iteration, 2026-08-11)

**Slice:** not a rung. A **gate**. Every rung this program can still move needs Kotlin verified, and
for seven iterations that was believed impossible here, so seven iterations wrote spec paragraphs
instead. The belief was wrong, and this iteration spent itself proving that rather than adding an
eighth paragraph.

### CP-1 What B-7 actually says, versus what it was read as saying

B-7 records that the cloud sandbox cannot resolve Google-hosted artifacts, and concludes: *"the
android gate is unrunnable here."* **That conclusion is correct.** The gate is
`checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug`, and three of those four need
the Android SDK.

What it was *read* as saying — in `AUDIT-REQUEST.md` C-S2T-7, in the S2T records, in the S4C row's
"engine half unwritten, not blocked" framing — is **"no Kotlin can be executed here"**. That is a
different and false claim. Measured this iteration, before anything was built:

| host | status |
| --- | --- |
| `services.gradle.org/distributions/` | **200** |
| `repo1.maven.org/maven2/` | **200** |
| `plugins.gradle.org/m2/` | **200** |
| `dl.google.com/dl/android/maven2/` | **000** |

**One denial, not four.** And `:core` is a pure-Kotlin/JVM module *by construction* — that is the
whole point of the `checkCoreIsAndroidFree` task in `build.gradle.kts` and the comment in
`settings.gradle.kts`. Its six dependencies (Kotlin JVM plugin, `kotlinx-serialization-json`,
`ktor-client-core`, `kotlin-test`, `ktor-client-mock`, `kotlinx-coroutines-test`) are all on Maven
Central. **`:core` needs nothing from Google, and never did.**

**What actually blocks the repo's build here is the root script, not `:core`.**
`build.gradle.kts` declares `alias(libs.plugins.android.application) apply false`, which resolves AGP
from `google()` at configuration time, and `settings.gradle.kts` includes `:app`. So
`./gradlew :core:test` in the repository fails — and that failure was reasonably, and wrongly, read
as "`:core` cannot be built here".

### CP-2 The lane, and why it is a generated file rather than a checked-in one

`scripts/core-probe.sh` builds a throwaway Gradle build in `mktemp -d` that includes `:core` and
only `:core`, pointed at the repository's own `core/` directory and its own
`gradle/libs.versions.toml`, with **`google()` deliberately absent** from the resolver. The
repository working tree is never modified; Gradle writes to `core/build/`, which `.gitignore`
already covers.

The settings file is **regenerated on every run rather than checked in**. A second, committed
settings file is precisely the thing that drifts from the real one without anyone noticing — the
doc/verifier trap in the main repo's `CLAUDE.md`, in Gradle form. Generating it from the repo's own
catalog and module directory means there is nothing to keep in sync.

**The JDK was the last obstacle and it is not egress.** `:core` pins `jvmToolchain(17)`; only JDK 21
is preinstalled; Gradle's auto-provisioner downloads from `api.foojay.io`, which **is** denied
(measured `000`, consistent with B-7). But the sandbox is Ubuntu and the distro archive is
reachable: `apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless`
installs `17.0.19+10`. **`apt-get install` alone 404s against the stale index — the `update` is not
optional**, which is recorded in the script's own error message because it cost a cycle here.

### CP-3 The measurement, and it is not "close to" CI — it is identical

`scripts/core-probe.sh` from a deleted `core/build/`: **`190 tests, 0 failed, 0 skipped, across 14
classes`**, `BUILD SUCCESSFUL`, exit 0.

CI run **31518619205**, job **93869950639**, on the same commit `34237ea` — its `Unit tests (:core)`
step logs **190 `PASSED` lines and 0 `FAILED`**, across **14** classes. Compared class by class,
not just in total:

| class | CI | probe | | class | CI | probe |
| --- | --- | --- | --- | --- | --- | --- |
| `EntitlementAckTest` | 9 | 9 | | `ProStateTest` | 5 | 5 |
| `EntitlementVectorsTest` | 5 | 5 | | `ProtocolTest` | 11 | 11 |
| `EnvelopeJsonTest` | 8 | 8 | | `ProtocolVectorsTest` | 6 | 6 |
| `OutboundEnvelopesTest` | 10 | 10 | | `PullPolicyTest` | 17 | 17 |
| `OutboundQueueTest` | 20 | 20 | | `RelayClientTest` | 26 | 26 |
| `OutcomeMarkPolicyTest` | 22 | 22 | | `SyncPumpTest` | 22 | 22 |
| `PairingFlowTest` | 21 | 21 | | `PairingSessionTest` | 8 | 8 |

**14/14 classes match exactly; 190 = 190.** The lane is not an approximation of CI's `:core` step,
it is the same suite.

**Proven live, because a green suite that cannot fail is worthless.** Mutating one line of
`RelayClient.kt` — `HttpStatusCode.NotFound -> RelayResult.PairingUnknown` to `RelayResult.Unauthorised`,
a `1 1` numstat — fails **exactly two tests** (`relay answers map to the decision the caller has to
make`, `a 4xx is a decision and is never retried`) and the script **exits 1**. Reverted; the suite
returns to 190/0. The lane detects a real regression in the exact area the previous iteration could
only reason about.

### CP-4 The dividend, claimed narrowly and taken immediately

The seventeenth iteration wrote, in `AUDIT-REQUEST.md` **C-S2T-7**:

> *"**No Kotlin was compiled or run: there is no Android SDK in this sandbox (B-7).** Treat it as a
> hypothesis with file:line support, not as a measurement. … The gate that would confirm the
> consequence is `./gradlew … :core:test`, which did not run."*

**That gate has now run, and it required no new code**, which is the part worth stating precisely.
The claim's two halves were already pinned by tests that were merely never executed here:

- `RelayClientTest.kt:284-286` — `404 → PairingUnknown`, `401 → Unauthorised`, `403 → Unauthorised`.
- `OutboundQueueTest.kt:269-279` — `PairingUnknown → SendHalt.PAIRING_GONE`, and neither
  `reconciled()` nor `reauthorised()` revives it (terminal).
- `OutboundQueueTest.kt:281-293` — `Unauthorised → SendHalt.UNAUTHORISED`, cleared **only** by
  `reauthorised()`, envelope bytes preserved (recoverable).

All five assertions are inside the 190 that just passed. Composed with S2T's miniflare measurement
that a purged pairing answers **401**, PQ-S2-4's consequence is now **executed rather than read**: an
unpaired phone halts `UNAUTHORISED` and waits for a bearer that cannot exist, while `PAIRING_GONE` —
the terminal state built for exactly this — is unreachable on today's wire.

**What this does not do:** it does not change PQ-S2-4's status. The question is still open and its
resolution is still a product decision. It upgrades the *evidence* under one caveat, and C-S2T-7's
"hypothesis, not a measurement" disclaimer is retired.

### CP-5 An independent check falls out of the construction, and it is worth one sentence

Because `google()` is absent from the probe's resolver **entirely**, a successful run proves `:core`
resolves with the Android repository unreachable. `checkCoreIsAndroidFree` asserts the same rule by
scanning imports and the `plugins {}` block — a *source-text* check. This asserts it at the
**dependency-resolution** layer. Two independent mechanisms for one invariant, and the new one
cannot be fooled by a transitive Android dependency that arrives without an `import android.` line.

**Not claimed as a replacement.** The scanning task also runs in CI and catches things this cannot
(a `com.android.library` plugin swap would fail resolution here, but so would a network blip).

### CP-6 The CI hang from the seventeenth iteration resolved on its own, and the record should say so

S2T-10 recorded two docs-only runs hanging on test steps at ~16× baseline, "still not diagnosed,
and deliberately not chased". Checked this iteration rather than left open:

| run | head | outcome |
| --- | --- | --- |
| 31517760672 | `c68ef07` | **cancelled** (superseded by the next records push) |
| 31518284889 | `f49290e` | **cancelled** (superseded) |
| **31518619205** | **`34237ea`** (branch tip) | **success** |

The tip run's step timings are back at baseline: `Unit tests (:core)` **54 s** (baseline 50 s),
`Unit tests (:app, Robolectric)` **108 s** (baseline 93 s), whole job **7 m 50 s** against 7 m 26 s.
**Neither hung run was ever observed to fail** — both were cancelled by the next push while still
in-progress, so nothing was ever red. **Transient runner infrastructure, self-resolved, no action.**
Recorded because an open "undiagnosed hang" left in the records sends the next session hunting for a
fault that is not there — the same failure mode as calling something BLOCKED when nothing blocks it.

### CP-7 Ladder effect, stated narrowly and this time it is not the usual sentence

**No rung moved, and this iteration did not try to move one.** S2 is still PARTIAL with B-2 still
exactly the missing `/pair` page.

What changed is the **shape of what a cloud iteration can do**. Seven consecutive iterations
hardened spec because spec was believed to be the only verifiable surface. It was not. From here a
cloud session can write Kotlin in `:core` and **run it** — which covers `SyncPump`, `OutboundQueue`,
`RelayClient`, `PullPolicy`, `PairingFlow`, `EntitlementAckApplier` and the protocol/vector tests.
**B-8's owner problem is `:app`/Room and stays blocked**; the `:app` half of everything stays
blocked; the engine's C# stays blocked. The unblocked surface is `:core`, and it is large.

**Stated as a limit, not a boast:** this runs **one** of the gate's four tasks. Any future record
citing it must say `:core:test, via scripts/core-probe.sh` and name what did not run. It is not the
android gate and must never be reported as one.

### CP-8 CI on this branch tip, added after the records were written

Run **31537144947**, job **93930962727**, on `d25c615` — **every step `success`**, whole job
**21:17:32 → 21:23:29 = 5 m 57 s**, which is *faster* than the 7 m 26 s baseline. The steps that
matter to this iteration's claims:

| step | result | duration |
| --- | --- | --- |
| `Assert :core has no Android dependency` | ✓ | 70 s |
| `Assert vendored sync vectors match the pinned main-repo commit` | ✓ | 9 s |
| `Unit tests (:core)` | ✓ | **41 s** (baseline 50 s) |
| `Unit tests (:app, Robolectric)` | ✓ | **85 s** (baseline 93 s) |
| `Assemble debug APK` · `Lint` | ✓ · ✓ | 72 s · 32 s |

**Two things this confirms and one it does not.** It confirms the **`679a317` vendored pin is
intact** — no cross-repo drift from anything here — and that **the hang did not recur**, which is
the observation CP-6's closure rests on; a third occurrence would have made that closure premature
and it is the thing to watch. It does **not** re-prove the lane: CI runs the repository's build with
an SDK, so its `:core` step and `scripts/core-probe.sh` are two independent routes to the same
suite, which is the entire point of comparing them.

**No count-reporting doc moved**, because no test was added: the suite is still the repository's own
190.

### Boundary — what was not touched

**Nothing was merged, in either repo.** Android PR #6 stays a draft; main-repo PRs #32, #33, #34,
#35 and #36 were neither merged, retargeted, rebased nor force-pushed, and **were not touched at
all** — this iteration wrote nothing in the main repo except the `autonomy/claude-state` bus entry.
The branch pushed **forward-only** — no force-push, no history rewrite, no branch deleted.

**Android repo: one new file** (`scripts/core-probe.sh`) **plus these records.** `RelayClient.kt`
was mutated to prove the lane detects a regression and **reverted in the same step** — verified with
`git status --porcelain`, which shows no modification to it. **Zero net Kotlin changes: no `:core`
source, no `:core` test, no `:app` file, no Gradle build script, no version catalog, no
`settings.gradle.kts`.** The 190-test suite is the repository's own, unchanged; **no test was added,
so the count did not move** and no count-reporting doc needed the drift sweep.

**Main repo: nothing.** No `docs/Sync-Protocol.md` edit, **no vector byte, no `generate.mjs`
change**, no harness, no `Verify-Alpha.ps1`, no `$ExpectedOfflineTotal`. The vendored vector pin
stays `679a317` and no vendored byte moved — this iteration had no reason to go near them.

**Neither gate ran in full and neither could.** `Verify-Alpha.ps1` needs .NET (`which dotnet` →
nothing). The android gate needs the Android SDK for three of its four tasks; **the fourth,
`:core:test`, ran here — that is the entire finding** and it is not a gate result.

**`apt-get install openjdk-17-jdk-headless` was run**, which modifies the *sandbox*, not the
repository. Recorded because it is the one environment change this iteration made and the next
session needs it.

**No deploy of any kind** (Cloudflare, Workers, relay, site, Pages) and **no `wrangler` invocation**.
**The production relay was contacted zero times, not even `GET /v1/health`** — no relay test ran at
all this iteration. Network egress was: the Gradle distribution, Maven Central, the Ubuntu archive,
and the GitHub API. No Google, Play, OAuth or Console action; no accounts, no purchases; no Gmail;
no cert-store, MSIX or keystore action — the upload keystore was neither read nor referenced.
**No secrets read, written or printed.**

Terra's state was read at iteration start: still **R6(b) BLOCKED** on draft PR #26, heartbeat
unchanged at **2026-08-07T21:18**, **claims no files** — no collision, and this iteration claims no
main-repo file at all.

---

## §ER — The receive state machine's check order, tested for the first time (nineteenth cloud iteration, 2026-08-11)

Linux sandbox, cloud iteration, **nineteenth** run. **`:core` Kotlin written and executed here** —
the second iteration to do so, and the first to add code rather than a probe.

### ER-0 Why this slice, and a correction to the iteration prompt

The prompt assigned **S5**, on the stated basis that S5 is "**NOT STARTED** and genuinely **NOT
blocked**". **Both halves are wrong, and the seventeenth iteration already recorded that they are**
— this is the second consecutive prompt to carry the error, so it is restated here rather than in
passing:

- `origin/claude/s5-entitlement-ack-spec` has carried **four commits since 2026-08-09** as draft
  **PR #32**. §4.3.3's `entitlement_ack` body, both shared vectors, and **PQ-A6-1 / PQ-A2-1 /
  PQ-A2-2** are closed. Verified after the mandatory fetch, not inherited:
  `git log --oneline origin/claude/s5-entitlement-ack-spec -4` → `9c05ef7`, `a564c0c`, `22b028e`,
  `8575539`, all above `origin/main` at `00b3705`.
- The one remaining piece the prompt names — **PQ-A2-3's `invalid-unknown-field` vector** — is
  **blocked by B-6**, and the blocker exists precisely to stop a session doing what the prompt
  asks. `src/Sync` has no inbound wire-JSON parser, so the engine would *accept* the envelope and
  the vector would turn the offline gate red on `windows-latest` for whoever pushes next, while
  proving nothing. **Parser first (C#), vector second.** No cloud session has .NET.

So this iteration took the prompt's own escape clause and picked the topmost rung actually
verifiable here. **The reason that is now a different question than it was two runs ago** is the
eighteenth iteration's finding: `:core` is pure-Kotlin/JVM and its suite **runs in this sandbox**
via `scripts/core-probe.sh`. That iteration closed by naming the honest next question —

> *"the honest next question for a cloud iteration is no longer 'which spec paragraph can I verify'
> but 'which `:core` behaviour is unwritten or untested'."*

This entry answers it with the first candidate found, and **writes Kotlin instead of prose for the
first time in nine iterations.**

### ER-1 The gap: an order called normative, tested by nothing

`EnvelopeReceiver`'s own docstring — carried **verbatim** in both implementations
(`core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt:26-35` and the engine's
`src/Sync/EnvelopeReceiver.cs:16-25`) — says:

> *"The check ORDER is part of the protocol, not an implementation detail: rejecting for the wrong
> reason usually means a check fired earlier than intended and the real one is untested."*

**`EnvelopeReceiver` had no dedicated test file.** It was exercised incidentally by
`ProtocolVectorsTest`, `SyncPumpTest`, `EntitlementVectorsTest` and `OutboundEnvelopesTest`.

**And the incidental coverage cannot reach the order, structurally.** `ProtocolVectorsTest` feeds
every shared envelope vector through the receiver and asserts each one's error code — but **every
vector violates exactly one rule**. A receiver that ran its checks in a completely different order
would classify all of them identically and pass. That is not a criticism of the vectors; a vector
file pins *the wire*, and order is not on the wire. It does mean the claim was load-bearing and
unpinned.

### ER-2 The construction: two violations per envelope

The only construction that can tell orders apart is **one envelope breaking two rules at once**,
asserting the earlier check answers. `core/src/test/kotlin/app/careerseeker/core/EnvelopeReceiverTest.kt`,
**26 tests**, walks the documented chain adjacency by adjacency:

| envelope violates | must answer | pins |
| --- | --- | --- |
| `v=2` **and** a revoked `key_id` | `version_unsupported` | version → key_id |
| revoked `key_id` **and** an unparseable nonce | `key_unknown` | key_id → structural |
| revoked `key_id` **and** a 1 MiB+1 ciphertext | `key_unknown` | key_id → size |
| unparseable nonce **and** oversized ciphertext | `decrypt_failed` | structural → size |
| oversized ciphertext **and** a sig on an `e2p` envelope | `too_large` | size → sig placement |
| sig on `e2p` **and** a replayed seq | `bad_signature` | sig placement → replay |
| replayed seq **and** an undecryptable ciphertext | `replay_rejected` | replay → decrypt |
| wrong sealing key **and** a body whose kind is unknown | `decrypt_failed` | decrypt → kind |
| reserved L2 kind **and** an unparseable sig on `p2e` | `unknown_kind` | kind → signature |

Plus the invariants the order exists to protect: **no rejection advances the sequence cursor**,
asserted **once per error code** rather than in aggregate — an aggregate assertion passes while one
code leaks — and gaps are legitimate, the two directions keep independent counters, a rejected
envelope never hands back a plaintext, and `receiveWire`'s strict parse runs ahead of the machine.

**Measured, `scripts/core-probe.sh --rerun`:**

```
core-probe: 216 tests, 0 failed, 0 skipped, across 15 classes
```

**190 → 216, 14 → 15 classes.** Baseline re-measured this session before the change (190/0/14), so
the delta is this file and nothing else.

### ER-3 Proven live, because a suite that has never failed is not evidence

**No production code changed**, so every one of the 26 assertions is a **pin by construction**. The
seventeenth iteration's rule applies: check each against a deliberately broken receiver. Six
mutations of `EnvelopeReceiver.kt`, each reverted immediately (`git checkout --` in a trap, so the
tree is restored even on failure):

| # | mutation | numstat | caught by |
| --- | --- | --- | --- |
| M1 | replay moved ahead of signature placement | `1 1` | `signature placement is checked before replay` |
| M2 | size moved after signature placement | `1 1` | `size is checked before signature placement` |
| M3 | `key_id` moved after the structural decode | `1 1` | `key_id is checked before …` **×2** |
| M4 | sequence committed before the checks | `1 1` | `no rejection advances the sequence tracker`; **`ProtocolVectorsTest`** |
| M5 | `kindOf` reverted to a substring scan | `11 5` | `untrusted body text …`; `a non-string kind …` |
| M6 | version check deleted | `0 1` | `version is checked before key_id`; +2; **`ProtocolVectorsTest`** |

**Six of six caught, and the build exits 1 on each.**

**The load-bearing row is M1–M3.** Those three are *pure reorderings* — no check removed, no
classification of a single-fault envelope changed — and **the pre-existing 190 tests did not notice
any of them.** Only M4 and M6, which delete or move a check's effect, reach `ProtocolVectorsTest`.
That is the gap in ER-1, measured rather than argued.

### ER-4 A finding from auditing my own draft, which is the habit these records keep rewarding

The first version of `untrusted body text cannot choose the route` **did not discriminate**, and
M5 is what exposed it: the mutation was caught by the *non-string kind* test, not by the test
written for exactly that attack.

The reason is worth the paragraph. §8.6's concern is that the decrypted body carries untrusted job
and recruiter text, so `kindOf` parses JSON rather than scanning for the first `"kind"` substring.
My draft modelled the attack as a **quoted `"kind":"snapshot"` inside a string value** — but JSON
escapes an inner quote as `\"`, so in the raw wire text the scanner's `indexOf("\"kind\"")` never
matches it. **The attack fails against the naive scanner on its own, and a test built on it proves
nothing.**

What actually defeats a scanner is a **nested object** — `{"meta":{"kind":"snapshot"},"kind":"heartbeat"}`
— whose `"kind"` is unescaped, well-formed, and earlier in the byte stream than the real one. Added
that case and an array-of-objects variant, re-ran M5: the test now **fails** against the substring
scanner as intended. All three bodies ship, ordered with the ineffective one first and a comment
saying why it is ineffective, so the next reader does not re-derive this.

### ER-5 The docstring's "structural decode" is one step in prose and two in code — corrected nowhere, recorded here

Reading the code against its own stated order surfaced a mismatch. The docstring says *structural
decode* happens at step 3, before size and signature placement. In the Kotlin, the **`dir` decode is
also a structural decode** and sits at **step 6** (`EnvelopeReceiver.kt:75`), after both.

**This is a documentation imprecision, not a behavioural defect, and the check was made rather than
assumed.** The engine twin **never parses `dir` at all** — it threads the raw string through
`HighestAccepted`, `keyForDir` and the AAD (`src/Sync/EnvelopeReceiver.cs:54-62`). So for an
envelope with an unrecognised `dir`:

- **Phone:** `Direction.fromWire` returns null → `decrypt_failed`, before any crypto.
- **Engine:** proceeds, the bogus `dir` reaches the AAD, the GCM tag fails → `decrypt_failed`.

**Same classification, different path.** And no engine caller can throw on the way: every
`keyForDir` lambda that exists today is total (`tests/SyncHarness/Program.cs:194`,
`tests/SyncLiveSmoke/Program.cs:126,146`, `tests/EngineHarness/Program.cs:2280`), and
`InboundDispatcher` has **no production construction at all** — `src/Engine/Program.cs:247` is a
comment describing the seam B-2 is about. **No divergence, and deliberately no code change**: the
Kotlin's placement is what keeps it agreeing with an engine that does not parse `dir`, so
"correcting" it to match the prose would be the phone being *more correct than the engine*, which
the mission's interpretation rule names as a field bug. **The prose is what is imprecise.** Left for
a session that can gate both repos, since the docstring is shared and a one-word edit to it belongs
in the same change as the C# one.

### ER-6 One observation opened as a question, and it is diagnosability rather than safety

`receiveWire` applies §3's strict parse **before** the version check. So a v2 sender that both bumps
`v` **and** adds a top-level field is told **`decrypt_failed`**, never `version_unsupported`, and
cannot learn the version is the problem. Both halves are pinned by
`the strict parse runs ahead of the version check…`, which also shows the same envelope **without**
the extra field is correctly told `version_unsupported`.

`EnvelopeJson`'s docstring argues for this order — *if the sender speaks a dialect this receiver
does not know, nothing else it says should be interpreted* — and that argument is sound. The cost is
that it lands on **exactly the upgrade path** §3's rule exists to protect. **Behaviour unchanged**;
recorded as **PQ-ER-1** with its severity stated plainly, because a question filed as though it were
a defect is its own kind of drift.

### ER-7 Ladder effect, stated narrowly

**No rung moved, and none was attempted.** S5 is **PARTIAL** exactly as the seventeenth iteration
left it — spec and vectors done in PR #32, the phone applier done, the **C# applier unwritten** (not
blocked, merely impossible here), **PQ-A2-3 still blocked by B-6**. S2's B-2 is still the missing
`/pair` page. S3/S4/S6 are unchanged.

**What this iteration did was make an existing claim checkable**, in the module the eighteenth
iteration proved reachable. The receiver is the piece both `SyncPump` and the vector suite sit on
top of, so the order it applies is load-bearing for S4's transport loop and S6's send path alike —
but **none of that is a rung advancing**, and calling it one would be the failure this file exists
to prevent.

### ER-8 What was NOT touched

**Android repo:** one file added, `core/src/test/kotlin/app/careerseeker/core/EnvelopeReceiverTest.kt`,
plus these records. **No production Kotlin changed** — `EnvelopeReceiver.kt` was mutated six times
and restored six times, and `git diff --stat` against `core/src/main/` is **empty**. No `:app` file,
no Gradle script, no `gradle/libs.versions.toml`, no CI workflow, no `scripts/` change. **No
vendored vector byte** — the pin stays `679a317`.

**Main repo (`careerseeker`): nothing but the coordination bus.** No `docs/Sync-Protocol.md`, no
`relay/` file, **no vector, no `generate.mjs` run that wrote anything**, no `.cs`, no harness, no
`Verify-Alpha.ps1`, no `$ExpectedOfflineTotal`. The C# files named in ER-5 were **read only**. Draft
PRs #32–#36 were not touched: not merged, retargeted, rebased or force-pushed.

**Neither gate ran in full and neither could.** `Verify-Alpha.ps1` needs .NET (none here). The
android gate needs the Android SDK for **three of its four tasks**; the fourth, `:core:test`, ran via
`scripts/core-probe.sh`. **That is one task, not a gate result**, and `checkCoreIsAndroidFree`,
`:app:assembleDebug` and `:app:lintDebug` did not run — CI remains the gate for those.

**No merge in either repo**, no force-push, no history rewrite, no branch deleted. **No deploy** of
any kind (Cloudflare, Workers, relay, site, Pages) and **no `wrangler` invocation`**. **The
production relay was contacted zero times, not even `GET /v1/health`.** Network egress was the
Gradle distribution, Maven Central, the Ubuntu archive and the GitHub API. No Google, Play, OAuth or
Console action; no accounts, no purchases; no Gmail; no cert-store, MSIX or keystore action — the
upload keystore was neither read nor referenced. **No secrets read, written or printed.**

Terra's state was read at iteration start **and again before writing this**: still **R6(b) BLOCKED**
on draft PR #26, heartbeat unchanged at **2026-08-07T21:18**, **claims no files** — no collision, and
this iteration claims no main-repo file at all.

### ER-9 Two of my own audit commands did not reproduce their stated output

Added after the fact, because the check that catches this is the one worth keeping. Every command in
C-ER was run **as written** before the branch was called done. Two failed, and both were in
`AUDIT-REQUEST.md` rather than in the code:

1. **C-ER-3 pointed at the wrong path and tested the wrong type.** It read
   `docs/sync-vectors/v1` — that is the **generator's** path, in the *other* repo. The android
   repo's vendored copy is `core/src/test/resources/sync-vectors/v1`, so the command died
   `ENOENT`. Worse, its predicate was `v.valid === "false"`, and `valid` is a **JSON boolean**: had
   the path been right, the loop would have printed **an empty list**, which reads exactly like
   "there are no invalid vectors" — a silent wrong answer rather than a loud one. Corrected, it
   prints **13** invalid envelope vectors, one `expect_error` each, which is the claim.

2. **C-ER-5 printed a number nobody can evaluate.** `console.log("scanner finds:", s.indexOf(…))`
   emitted a bare `32`. The claim is *"the scanner skips the escaped decoy and lands on the real
   field"* — and `32` demonstrates that only to a reader willing to count characters. Corrected to
   print the matched slice and the boolean `hit === s.lastIndexOf(…)`, so the output states the
   claim instead of encoding it.

**Neither defect touched the tests**, which is the point worth drawing out: the suite was green and
the mutations were caught with both of these sitting broken in the document beside them. **A claim
and the command that re-checks it can drift independently**, and only running the command finds it.
The seventeenth iteration caught the same shape (a `sed` that matched three sites, not one); that
this recurs suggests it should be a standing step rather than a habit — **run every command you just
wrote, from the path you told the reader to stand in.**

### ER-10 CI reported green on the final head, and it is the gate for the three tasks this sandbox cannot run

Checked rather than predicted, and **after** the last push rather than assumed from an earlier one:
run [31553243004](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31553243004)
(run #97, event `push`), **`head_sha` `a58f7d5` read from the run's own field** and equal to the
branch tip, `status: completed`, `conclusion: success`, 01:19:53 → 01:28:26 UTC.

**This is the part that matters for every claim above.** The workflow is a single job on
`ubuntu-latest` with the real SDK and JDK 17, so green covers `checkCoreIsAndroidFree`, the
vendored-vector drift check against `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug` and
`:app:lintDebug` — **the three gate tasks `scripts/core-probe.sh` structurally cannot run**, plus
the only check that this change compiles under the real toolchain rather than the probe's
substituted one.

**It corroborates green, not the count.** CI prints no totals and I did not count the log's per-case
`PASSED` lines, so **216** remains the probe's number, corroborated by CI as *green* and not as a
figure. That distinction is the one C-S6S-12 established and it still holds.

---

## CR — `:core`'s two crypto primitives had no tests of their own (2026-08-12, twentieth cloud iteration)

**Rung: none moved, and none was attempted.** This is the second consecutive iteration to answer the
standing question the eighteenth run replaced "which spec paragraph can I verify?" with — *which
`:core` behaviour is unwritten, untested, or asserted only by reading?* — and the answer this time
was the bottom of the stack.

**A correction to the iteration prompt, for the third consecutive run**, made after the mandatory
fetch and recorded because the recurrence is now the finding. The prompt again described **S5** as
"NOT STARTED and genuinely NOT blocked" and named it the strongest candidate, listing four
sub-tasks. Three have been done since 2026-08-09 on `origin/claude/s5-entitlement-ack-spec`
(draft **PR #32**, four commits, §4.3.3 plus both `entitlement_ack` vectors, closing PQ-A6-1,
PQ-A2-1 and PQ-A2-2); the fourth, PQ-A2-3's `invalid-unknown-field` vector, is **blocked by B-6**,
whose entry exists precisely to stop a session adding it. The seventeenth and nineteenth runs each
recorded this. **The tree outranks the prompt, including on what is already done** — and the
prompt's own escape clause is what this iteration took.

### CR-1 The gap, measured rather than guessed

`core/src/main/.../crypto/` holds three files. `core/src/test/.../crypto/` **did not exist**.

```
git grep -l "Hkdf" 0182d89 -- core/src/test   ->   0 files
```

`Base64Url` was better off only in appearance: **seven** test files call it, none asserts anything
about it. `SyncCrypto` is genuinely exercised by six. So the gap was two primitives — the ones every
key and every wire field passes through — and `Hkdf` was named by nothing at all. Re-verify: C-CR-2.

### CR-2 Why "named by no test" understated it

`Hkdf` is not unexercised: `PairingDerivation` calls it five times and the shared pairing vectors
prove those outputs byte-for-byte against the engine. The real gap is narrower and worse:

**Every production call asks for 4 or 32 bytes, and HKDF-SHA256's block is 32.** So `expand`'s
`while (pos < length)` loop had only ever run **once**, `counter` had never been anything but **1**,
and `mac.update(t)` had never been fed a non-empty `t`. The chaining that makes HKDF an extendable
KDF — the part that is easy to get wrong and impossible to notice — was unreached by the product
**and** by the suite. Re-verify: C-CR-3.

### CR-3 RFC 5869 rather than more generated vectors, and the reason is not style

The pairing vectors come from `docs/sync-vectors/generate.mjs`. They prove **Node ≡ Kotlin ≡ .NET**
at the lengths the product happens to use. They cannot prove any of the three is *RFC-correct*, and
they cannot reach a second block at all. Appendix A's SHA-256 cases are published, independent of
this program, and **A.1 (L=42) needs two blocks, A.2 (L=82) needs three**.

The expected values were **recomputed with `node:crypto` before being written down** rather than
transcribed from the RFC — the seventeenth run's lesson that a transcribed measurement is not a
measurement. Re-verify: C-CR-4.

### CR-4 The Base64Url finding, and the draft of it that was wrong

The docstring makes a protocol claim — refusing alternate spellings is what lets the vectors "pin
one encoding" so Kotlin and C# cannot "disagree about what an envelope even says". Measured, two of
three spelling axes are closed (padding, standard alphabet). The third is open: the JDK's URL
decoder **ignores the final character's unused bits**, so `QQ`, `QR`, `QV` and `QZ` all decode to
`0x41`.

**The first draft of the test asserted this against the nonce, and was wrong.** It tried to build a
second spelling of a 12-byte nonce; there is none. Spare bits exist only when the byte length is
**not a multiple of 3** — the nonce (12) has exactly **one** spelling, a 32-byte key has 4, a
64-byte signature has 16. **The draft's own guard assertion (`test built no second spelling`) is
what caught it**, on the first run, before anything was recorded. That is the whole argument for
writing a guard into a test whose construction you believe.

What survives is narrower and true: a re-spelled **ciphertext** both opens and signs identically,
because `signatureInput` binds the nonce *string* (immune) and the ciphertext by the **hash of its
decoded bytes**. Not a replay bypass — `seq` is in the AAD. The one live constraint: **an envelope's
wire form is not unique**, so it must never be de-duplicated or authenticated by hashing its wire
bytes. Nothing does. Opened as **PQ-B64-1** for the half that cannot be settled here — whether .NET's
decoder agrees, which decides a §3 conformance question and which no vector can express.
Re-verify: C-CR-5, C-CR-6.

### CR-5 The mutation battery, and the two mutations that cannot be caught

Eight mutations, each applied, run and reverted; `git diff --stat -- core/src/main/` is **empty**
afterwards. The headline is M1 — delete `counter++`, so every block after the first is wrong:

| | result |
| --- | --- |
| M1 with the two new files **removed** | **216 tests, 0 failed** — the prior suite is **blind** |
| M1 with them **present** | **3 failed**, all `HkdfTest`, all three RFC cases |

M2 (chaining removed) behaves identically. M4/M5 (bounds) and M6/M8 (base64) are each caught by
their named test.

**Two mutations were caught by nothing, and both are semantically equivalent changes rather than
test gaps** — checked, not excused. **M3**: replacing the empty-salt substitution `ByteArray(32)`
with `ByteArray(1)` changes no behaviour, because HMAC zero-pads any key shorter than its 64-byte
block; verified against `node:crypto`, where salts of 0, 1, 32 and 64 zero bytes all agree and 65
diverges. **M7**: deleting the `'+'`/`'/'` guard changes nothing a test can see, because those
characters are outside the URL alphabet and the JDK decoder throws anyway — so only the `'='` half
of that guard is load-bearing, which M6 proves. Both are now stated in the tests' own docstrings, so
neither reads as stronger than it is.

**A result worth keeping that runs against my own instinct:** the two *structural* tests I wrote —
`output at length N is a prefix of N plus one` and `maximum length is 255 blocks` — **do not catch
M1 or M2**. A stuck counter still chains and still produces distinct blocks. Only the published
vectors catch it. The argument for RFC cases over self-consistency properties is therefore a
measurement here, not a preference. Re-verify: C-CR-7.

### CR-6 One of my own audit commands did not reproduce its stated output

The nineteenth run found two and proposed making the re-run a standing step rather than a habit.
Done as a standing step this time, and it caught one: **C-CR-3 claimed "five call sites" while its
command printed six.** The sixth is `Hkdf.kt`'s own `fun deriveKey` declaration. Corrected to the
qualified pattern `Hkdf\.deriveKey`, which returns exactly five, with the six-line result explained
in place rather than overwritten. Small, and the same shape as the seventeenth run's `sed` and the
nineteenth's `C-ER-3`: **a claim and the command that re-checks it drift independently, and only
running the command finds it.**

### CR-7 Ladder effect, stated narrowly

**No rung moved.** S5 is **PARTIAL** exactly as the seventeenth and nineteenth runs left it — spec
and vectors in PR #32, phone applier done, **C# applier unwritten** (not blocked, merely impossible
here), **PQ-A2-3 still blocked by B-6**. S2's B-2 is still the missing desktop `/pair` page. S3, S4,
S6, S7, S8 are unchanged. `:core` suite **216 → 244** across **15 → 17** classes.

What this iteration did is make two previously-unasserted claims checkable, at the bottom of the
dependency stack: every envelope key comes out of `Hkdf`, and every wire field goes through
`Base64Url`. **That is not a rung advancing, and calling it one would be the failure this file
exists to prevent.**

### CR-8 What was NOT touched

**Android repo:** two files added, `core/src/test/.../crypto/HkdfTest.kt` and
`core/src/test/.../crypto/Base64UrlTest.kt`, plus these records and `docs/protocol-questions.md`.
**No production Kotlin changed** — `Hkdf.kt` and `Base64Url.kt` were mutated eight times and
restored eight times, and `git diff --stat 0182d89..HEAD -- core/src/main/` is **empty**. No `:app`
file, no Gradle script, no `gradle/libs.versions.toml`, no CI workflow, no `scripts/` change. **No
vendored vector byte** — the pin stays `679a317`.

**Main repo (`careerseeker`): nothing but the coordination bus.** No `docs/Sync-Protocol.md`, no
`relay/` file, no vector, no `generate.mjs` run that wrote anything, no `.cs`, no harness, no
`Verify-Alpha.ps1`, no `$ExpectedOfflineTotal`. Draft PRs #32–#36 were not touched: not merged,
retargeted, rebased or force-pushed. **The offline pin stays 598 and could not have moved** — this
iteration wrote no main-repo file other than the bus.

**One machine change, logged as the mission requires:** `apt-get install openjdk-17-jdk-headless`.
`:core` pins `jvmToolchain(17)`, the sandbox shipped only JDK 21, and `api.foojay.io` is denied by
the same egress policy as `dl.google.com` (B-7), so Gradle cannot auto-provision. `core-probe.sh`'s
header already prescribed this exact command.

**Neither gate ran and neither could.** `Verify-Alpha.ps1` needs .NET (none here). The android gate
needs the Android SDK for **three of its four tasks** — `checkCoreIsAndroidFree`,
`:app:assembleDebug`, `:app:lintDebug` — none of which ran. The fourth, `:core:test`, ran via
`scripts/core-probe.sh`: **244 tests, 0 failed, 0 skipped, across 17 classes.** **That is one task,
not a gate result**, and CI remains the gate for the rest.

**No merge in either repo**, no force-push, no history rewrite, no branch deleted. **No deploy** of
any kind (Cloudflare, Workers, relay, site, Pages) and **no `wrangler` invocation**. **The production
relay was contacted zero times, not even `GET /v1/health`.** Network egress was the Gradle
distribution, Maven Central, the Ubuntu archive and the GitHub API. No Google, Play, OAuth or Console
action; no accounts, no purchases; no Gmail; no cert-store, MSIX or keystore action — the upload
keystore was neither read nor referenced. **No secrets read, written or printed.**

Terra's state was read at iteration start: still **R6(b) BLOCKED** on draft PR #26, heartbeat
unchanged at **2026-08-07T21:18**, **claims no files** — no collision, and this iteration claims no
main-repo file at all.

### CR-9 CI reported, and the first attempt was red — on a commit that changed no code

Checked rather than predicted, and the result needed a second measurement before it meant anything.

Run [31566551075](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31566551075) on
**`head_sha` `d8ae5da`**, read from the run's own field and equal to the branch tip:

| attempt | conclusion | ended |
| --- | --- | --- |
| **1** | **`failure`** | 05:31:54 UTC |
| **2** (re-run, **identical commit, no push between**) | **`success`** | 05:42:06 UTC |

Attempt 1's only failure:

```
ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab FAILED
    java.lang.AssertionError at ScreensFromFixtureTest.kt:69
35 tests completed, 1 failed, 3 skipped
```

**That test is in `:app`, and this iteration touched no `:app` file.**
`git diff --stat 0182d89..HEAD -- app/ core/src/main/` is **empty** — the branch added two `:core`
*test* files and Markdown. `:core` test sources are not on `:app`'s compile or runtime classpath, so
there is no mechanism by which this change could reach a Compose UI test. The parent commit
`0182d89` had passed the same job 4 hours earlier (run `31553856359`).

**Attempt 2 is the controlled experiment that settles it:** same tree, same SHA, no intervening
push, opposite outcome. **The test is nondeterministic.** Attempt 2 went green on all thirteen
steps, including the three the probe structurally cannot run — *Assert :core has no Android
dependency*, *Assemble debug APK*, *Lint* — plus the vendored-vector drift check against `679a317`
and *Assert no analytics or tracking SDKs ship*.

**The diagnosis is narrow and is a reading, not a run** (no Android SDK here — B-7). Line 69 is the
**first** assertion, immediately after `setContent` and **before any tab is clicked**:

```kotlin
compose.setContent { DashboardApp(db) }
val label = "Demo data — not a live engine"

compose.onNodeWithText(label).assertIsDisplayed()   // <- line 69
```

So the failure is **initial composition not having settled**, not the navigation loop the test was
written to walk. The same job log carries the matching deprecation warning against
`createComposeRule`: *"The v2 APIs use StandardTestDispatcher instead of UnconfinedTestDispatcher…
Tests relying on immediate execution may require explicit synchronization."* That is exactly this
shape of failure, and the test relies on immediate execution.

### CR-10 Why a flaky test in this program is worth more than a shrug

**CI is the gate.** Nine iterations of records — including every claim in this entry — end with
"the probe runs one of the gate's four tasks; CI is the gate for the rest." A gate that fails
~1 run in N on a timing race weakens every one of those sentences, and it fails in the most
expensive way: **a future session sees red on a commit that is fine, and either misattributes it to
its own slice or spends its iteration on a phantom.** The eighteenth iteration lost most of a run to
an inherited belief; this is the same cost with a different cause.

**Not fixed here, and the reason is B-7 rather than judgement.** The repair is in `:app` — either
`compose.waitForIdle()`/`waitUntil` before the first assertion, or migrating to the v2
`createComposeRule` the warning names. Neither can be compiled, run or gated in this sandbox, and
shipping an `:app` edit whose only verification is CI-roulette is precisely what these records
exist to prevent. **Recorded in `BLOCKED.md` as a standing gate hazard with the smallest human
unblock**, not as a blocker on this slice — nothing here was blocked.

**Also recorded because it will mislead someone otherwise:** run **#99** (`49bbe25`, the code head)
shows `cancelled`. It was superseded by the records-only push that produced `d8ae5da`; it did not
fail. Since `d8ae5da` differs from `49bbe25` only in `BLOCKED.md`, **attempt 2's green covers this
slice's code in full.**

---

## SC — The AEAD codec was every test's tool and no test's subject (2026-08-12, twenty-first cloud iteration)

**Rung: none moved, and none was attempted.**

**First, the correction this record has now made for the fourth consecutive run.** The iteration
prompt again assigned **S5** on the basis that it is "NOT STARTED and genuinely NOT blocked."
**Both halves are still wrong**, and the seventeenth, nineteenth and twentieth runs each said so.
Verified again after the mandatory fetch, not carried from the previous record:
`origin/claude/s5-entitlement-ack-spec` carries **four commits** above `origin/main` (`00b3705`)
as draft **PR #32** — `9c05ef7`, `a564c0c`, `22b028e`, `8575539` — and
`git show origin/claude/s5-entitlement-ack-spec:docs/Sync-Protocol.md` contains **§4.3.3** at line
307 defining the body as `{product_id, acknowledged_at, order_id?}`, with both
`entitlement-ack*.json` vectors present and `generate.mjs` amended. **PQ-A6-1, PQ-A2-1 and PQ-A2-2
are closed.** The one remaining piece the prompt names — **PQ-A2-3's `invalid-unknown-field`
vector — is blocked by B-6**, re-read this session rather than assumed.

The prompt also directs that the Kotlin applier not be written "unless you can compile them — you
cannot." **Two things are wrong with that.** `:core` Kotlin *can* be compiled and run here (B-7's
scope was corrected in the eighteenth iteration), and the applier **already exists**:
`core/…/EntitlementAck.kt` ships `EntitlementAck` + `EntitlementAckApplier`, with
`EntitlementAckTest` beside it. **Four identical corrections is itself the finding**, and it is
now the first line of `STATE.md`: a session handed S5 should verify PR #32 and read B-6 *before
writing anything*.

So this run took the prompt's escape clause and went to the bottom of the stack, continuing the
twentieth iteration's sweep of `core/…/crypto/`.

### SC-1 The gap, and why it is not the same gap as the last two

`Hkdf` and `Base64Url` were *unreferenced* by any test. [SyncCrypto] never was —
`grep -rl SyncCrypto core/src/test` printed **six** files. The gap is narrower and easier to miss:
**referenced everywhere as a tool, asserted about nowhere.** All six (`SyncPumpTest`,
`EnvelopeReceiverTest`, `PairingSessionTest`, `OutboundEnvelopesTest`, `EntitlementVectorsTest`,
`ProtocolVectorsTest`) use it to build or open a fixture on the way to testing something else. A
codec exercised only as scaffolding is tested exactly on the inputs its scaffolding happens to
produce — and two consequences were measured before a line was written.

**One: `verifySignature` has exactly one pre-existing test call** (`ProtocolVectorsTest.kt:146`)
and one production caller (`EnvelopeReceiver.kt:98`). The vendored set carries **eight** distinct
64-byte signatures, and **all eight have a non-zero leading byte in both `r` and `s`** — so
`toDerInteger`'s strip loop had never taken an iteration, in the product or the suite. Same shape
as CR's HKDF finding. **Two: no vector puts a non-ASCII byte in the AAD** — 26 files, 23 with an
`aad` field, **zero**. Re-verify: **C-SC-1**, **C-SC-2**.

### SC-2 The finding: the AAD is not an injective encoding of the header

`EnvelopeHeader.aad()` joins six fields with `|` and `=`; `SyncCrypto.gcm()` encodes the result
`US_ASCII`. `EnvelopeJson.parse` regex-checks `pairing` and types `v`/`seq`/`dir`, but takes
**`ts` and `key_id` as arbitrary JSON strings with no charset or content check** — and those two
are the last, adjacent fields. Two independent collisions follow.

**Half 1, the charset.** Java's `US_ASCII` encoder maps every unmappable character to `?` (0x3F).
Measured: `é`, `è`, `Ж` and `😀` all become the **single** byte 0x3F — a surrogate pair collapses
to one, not two — and all four collide with a literal `?`. An envelope sealed under `ts=…Zé` opens
under `…Zè`, `…Z😀` and `…Z?`. §5.4's signature input uses the same encoder, so command signatures
inherit it.

**Half 2, the framing, and it needs no non-ASCII at all.** `(ts="T", key_id="K|key_id=Z")` and
`(ts="T|key_id=K", key_id="Z")` produce a byte-identical AAD, and each opens the other's envelope.

**Both are latent, and the tests say so at their own sites rather than in a footnote.** A rewrite
only survives authentication if the *original* bytes and the rewritten bytes agree after encoding;
conforming senders emit RFC 3339 timestamps and generated key ids, so every mutation of a real
envelope changes a byte and fails the tag. Half 2 is self-limiting twice over, since `key_id`
selects the key before the AAD is built. **Calling either a bypass would be the phantom these
records exist to prevent.**

**What is genuinely open is the cross-implementation half, and it is unmeasured.** If `src/Sync/`
builds its AAD with UTF-8, the two sides agree on every all-ASCII header and each answers
`decrypt_failed` on the other's traffic for everything else — the shape of **PQ-B64-1**, one field
over. **Deliberately not fixed:** tightening the Kotlin makes the phone stricter than an unmeasured
engine, the field bug the mission's interpretation rule names. Filed as **PQ-AAD-1**; the
resolution is one `grep` on a machine with .NET. Re-verify: **C-SC-5**, **C-SC-6**.

### SC-3 Why the vector suite could never have caught it

`heartbeat-unicode.json` looks like exactly the vector that would — its own note says it "catches
implementations that treat UTF-8 as Latin-1 or mangle surrogate pairs." **It puts its non-ASCII in
the `plaintext_json`, and its `aad` field is plain ASCII.** The body is length-delimited bytes and
round-trips; the AAD is a string that gets re-encoded, and does not. `SyncCryptoTest` ships the
plaintext case as the deliberate contrast to the header case sitting next to it.

### SC-4 The mutation battery, and the split inside the four that survived

Eight mutations applied to `SyncCrypto.kt` and reverted; **`git diff --stat -- core/src/main/` is
empty**. **Four caught** — M1 (by two tests), M3, M4, M5. Four survived, and they do **not** all
survive for the same reason, which is the part worth reading.

**M6 is semantically redundant, not a gap.** The explicit 64-byte gate duplicates an
`IndexOutOfBoundsException` that `rawToDer` throws inside the `try` and the catch converts to
`false`. Checked, not excused.

**M2, M7 and M8 survive because they are only observable under a stricter JCA provider than the
one the tests run on**, measured directly rather than inferred: `SunEC` **accepts** an unpadded
negative DER INTEGER (while rejecting the non-minimal encoding M1 produces); `generateSecret()`
returns a **fixed-width 32-byte** array even when the secret's top byte is `0x00`, so `leftPad`
never fires; and `generatePublic` **returns without throwing** for an off-curve point *and* for
coordinates above the field prime, so `verifySignature`'s entire `catch` is unreached.
Re-verify: **C-SC-7**, **C-SC-8**.

### SC-5 That split is the iteration's second finding, and it bounds this whole lane

`:core` is a pure-JVM module, so `:core:test` runs on the JDK's **`SunEC`** — here *and* on
`windows-latest` in CI. The phone runs on Android, where the provider is **Conscrypt**. The three
lines M2/M7/M8 delete are precisely the ones that matter when a provider differs from `SunEC`, and
they are the three no JVM test can exercise. **A green `:core:test` is not evidence about the
codec's behaviour on a device**, and no record before this one said so.

**The risk is deleting them, not keeping them.** All three are defensive — they make the encoding
conform to DER/§5.2 and convert provider exceptions into the `false` §7.2 expects — so the entry
exists mainly so a future session running a coverage tool does not read them as dead code. Worst
case, stated concretely: if Conscrypt returned the BigInteger-minimal ECDH secret, `leftPad` is the
only thing stopping a 31-byte IKM reaching HKDF — a *different* IKM, so the two ends derive
different directional keys for roughly **1 pairing in 256**. Filed as **PQ-SC-1**, explicitly
**not** as a blocker: nothing was obstructed and no rung depends on it.

### SC-6 Two of this file's own claims were wrong, and the mutation run is what caught them

Both were written before the battery ran, and both shipped corrected.

**The leading-zero characterisation was too coarse.** A `0x00` whose *next* byte has the high bit
set is a strip-then-pad **no-op** — the encoding is identical either way — so the `r` fixture
(`00a3…`) does **not** reach the branch. Only the `s` fixture (`0051…`, high bit clear) does, and
M1 fails exactly the two tests that use it. The test is now named *for the no-op*, and kept
deliberately: a future reader picking a leading-zero fixture at random would most likely pick this
shape and conclude the branch was covered. The frequency claim was corrected with it — a strip is
needed ~1 time in 512 per component, so **~1 signature in 256**, not the "1 in 128" first written.

**The ECDH test was labelled a regression catcher and is a pin.** The first draft said "verified as
a real catcher by mutation M7"; M7 then left the suite green, because `SunEC` never returns a short
secret. The docstring now says that, and says why the fixture is still worth having.

**A third correction, in the audit commands rather than the tests.** Three of the nine C-SC entries
did not reproduce their stated output on the standing re-run step: C-SC-1's expectation named a
declaration line the pattern cannot match (**the same transcription shape as CR-6 and C-CR-3**,
third recurrence), C-SC-3 claimed three `PASSED` lines where the pattern returns **four**, and
C-SC-4's JWK paired one key's `d` with the *other* key's public coordinates. All three were fixed
and re-run before commit.

### SC-7 Counts, and one machine change

`:core:test` via `scripts/core-probe.sh`: **244 → 270, 17 → 18 classes, 0 failed**. The 244/17
baseline was **re-measured this session** before any edit rather than quoted from CR. The +26 is
one new class, `SyncCryptoTest`; **no existing class was added, deleted, renamed or edited**, and
no production file changed at all.

One machine change, logged as such: `apt-get update -qq && apt-get install -y --no-install-recommends
openjdk-17-jdk-headless`, exactly as `core-probe.sh`'s header prescribes (`:core` pins
`jvmToolchain(17)`; `api.foojay.io` is denied by B-7's policy, so Gradle cannot auto-provision).

The scratch Java probes behind C-SC-5 and C-SC-8 were written under the session scratchpad and
**never inside the repository** — `git status --porcelain` showed only the intended files
throughout.

### SC-8 What was NOT touched

**No vector byte.** `git diff 27b28bb..HEAD -- core/src/test/resources/sync-vectors/` is **empty**,
so the vendored pin **`679a317`** is intact by construction and no cross-repo drift event occurred.
**No production Kotlin** — all eight mutations reverted, `git diff --stat -- core/src/main/` empty.
**No `:app` file.** **Nothing in the main repo except the coordination bus** — no
`docs/Sync-Protocol.md`, no `relay/` file, no `generate.mjs` run that wrote anything, no `.cs`, no
harness, no `Verify-Alpha.ps1`, no `$ExpectedOfflineTotal`; **the offline pin stays 598 and could
not have moved.** **Draft PRs #32–#36 were not touched** — not merged, retargeted, rebased or
force-pushed. **No merge in either repo**, no force-push, no history rewrite, no branch deletion,
**no deploy of any kind**, no contact with the production relay (not even `GET /v1/health`), no
Google/Play/OAuth console, no accounts, no purchases, no Gmail, and **no secret printed or read**.
**B-1, B-2, B-4, B-5, B-6, B-8 untouched**; B-6 was re-read and is unchanged, which is why PQ-A2-3
was again not attempted despite the prompt naming it.

**And the standing limit, repeated because every record citing this lane must repeat it:**
`core-probe.sh` runs **one** of the android gate's four tasks. `checkCoreIsAndroidFree`,
`:app:assembleDebug` and `:app:lintDebug` need the Android SDK and **did not run**;
`scripts/Verify-Alpha.ps1` needs .NET and **did not run** (`which dotnet` is empty). **CI is still
the gate**, and citing this entry as "the android gate passed" would be the exact failure these
records exist to prevent. Re-verify: **C-SC-9**.

---

## WP — .NET was in the Ubuntu archive, and B-6 was never about the rule (2026-08-12, twenty-second cloud iteration)

**Rung: S5. The last open piece is closed** — the first rung-slice a cloud iteration has moved in
nine runs, and it moved because a blocker's *stated reason* was re-tested instead of re-read.

**The prompt correction, made for the FIFTH consecutive run, and then made irrelevant.** The
iteration prompt again assigned **S5** as "NOT STARTED and genuinely NOT blocked." Verified again
after the mandatory fetch rather than carried: `origin/claude/s5-entitlement-ack-spec` carries
**four commits** above `origin/main` (`00b3705`) as draft **PR #32**, `§4.3.3` is present, and both
`entitlement-ack*.json` vectors exist — so "not started" is false, as the seventeenth, nineteenth,
twentieth and twenty-first runs each recorded. What *is* different this time: the remaining piece
the prompt names — **PQ-A2-3's `invalid-unknown-field` vector, blocked by B-6** — **is now done.**
The prompt was also right by accident about one thing and wrong about it in detail: it says not to
write the C# applier because ".NET" is unavailable. **That premise is false in this sandbox**, and
that is the whole finding below.

### WP-1 The finding: B-6's reason was a measurement, and it had gone stale

B-6 has read the same way since 2026-08-09: *"No .NET on this machine (`which dotnet` → nothing),
so it could not be compiled, let alone tested."* **That was true and it was never re-tested.**
`which dotnet` is still empty on a fresh sandbox. But the question B-6 answered was *"is dotnet
installed"*, and the question that bounds the work is *"can dotnet be obtained"* — and the
eighteenth iteration had already proved the difference matters, when it found `:core` was never
covered by B-7 and seven runs had read the blocker wider than it was. **The same shape, one
toolchain over:**

```
$ apt-cache policy dotnet-sdk-8.0
  Candidate: 8.0.125-0ubuntu1~24.04.1
     500 http://archive.ubuntu.com/ubuntu noble-updates/main amd64 Packages
```

`src/Sync/SeekerSvc.Sync.csproj` and every other project pin `net8.0` — an **exact** match, not a
near one, and there is no `global.json` to disagree with. The Ubuntu archive is reachable here;
`dl.google.com` and `api.foojay.io` (B-7) are the denied hosts, and .NET needs neither.

**One machine change, logged as such** (the precedent is the twentieth run's `openjdk-17-jdk-headless`):

```
$ apt-get update -qq && apt-get install -y --no-install-recommends dotnet-sdk-8.0
$ dotnet --version
8.0.129
$ dotnet build CareerSeeker.sln -c Release
Build succeeded.  0 Warning(s)  0 Error(s)
```

**The entire engine solution builds in a Linux cloud sandbox.** Nine of the ten offline harnesses
run (WP-6). This is a standing capability for every future cloud iteration, not a one-off.

### WP-2 The gap, measured before anything was written

B-6's *diagnosis* was correct and survived re-checking. `src/Sync` had **no inbound wire-JSON
parser at all**: `ReceivedEnvelope` is a record that callers construct from already-parsed JSON,
and `SyncHarness`'s `ToReceived` read the nine names it wanted and dropped everything else. So an
envelope carrying a tenth top-level field **decrypted and was accepted** by the engine, while the
phone's `EnvelopeJson.parse` rejected it. §3 has required the opposite since P1:

> Other unknown top-level fields MUST be rejected, not ignored.

**The rule was normative, and enforced on one side.** That is also exactly why the vector could
not be added first: a shared vector is only enforceable if *both* consumers can fail it, and the
engine would have gone green by accepting the envelope the vector exists to refuse. B-6's refusal
to add it anyway was right, and its ordering — parser first, vector second — was followed
verbatim.

### WP-3 What was written

`src/Sync/EnvelopeJson.cs`, the C# counterpart of `core/…/EnvelopeJson.kt`, **mirrored field for
field including the parts the phone had already reasoned about**: a present-but-non-string `sig`
is malformed rather than absent (letting it degrade to "unsigned" turns a broken signature into a
missing one and changes which check fires), numbers are not coerced from quoted strings, the
pairing id is shape-checked before it can reach the AAD, and every structural failure reports
`decrypt_failed` per §3/§7.2.

**Check order matches the phone deliberately, cost included.** The unknown-field check precedes the
version check, so a v2 sender that also adds a field is told `decrypt_failed` and cannot learn the
version is the problem — **PQ-ER-1**, opened by the nineteenth run against the phone and now true
of both. Matching the phone is the point: an engine answering a *different* code for the same bytes
would be the drift this parser exists to remove. Two assertions pin the pair, because one could not
distinguish them — the same envelope **without** the extra field still reports `version_unsupported`.

`tests/SyncHarness/Program.cs` now feeds envelope vectors through `EnvelopeJson.Parse` on the
serialized **wire form**. **The compatibility result is the one that matters:** all 24 pre-existing
envelope vectors classify identically through the strict parser — 130 → 130 before the new vector
was added — so the parser refuses nothing the vectors declare legal.

Eleven assertions pin the parser directly, including three cases **no vector file can carry**: a
vector *is* JSON, so it cannot express "the wire bytes were not JSON at all" or "the root was not
an object", and `index.json` gives each vector exactly one `expect_error`, so a case whose whole
interest is *which check fired first* has nowhere to live in the shared suite.

### WP-4 The vector, and the drift that did not happen

`invalid-unknown-field`, generated by `generate.mjs`, never hand-written. **Everything about the
envelope is valid except the extra field** — a well-formed delta at an unused `seq` (12), sealed
with the real e2p key — so a receiver that dropped the rule would **accept** it rather than fail
it some other way. That is what makes it a pin rather than a shape. The field is injected
**post-seal** and is therefore **not covered by the AAD**, which is precisely why a permissive
parser is an injection point: anyone on the path can add it and no authentication step notices.

**The cross-repo hazard was checked, not assumed.** The mission forbids changing any existing
vector's bytes:

```
$ git diff --name-only docs/sync-vectors/v1/ | grep -v index.json | wc -l
0
$ git diff --stat docs/sync-vectors/v1/
 docs/sync-vectors/v1/index.json | 6 ++++++
```

**Additive only.** `index.json` gains exactly one entry. The android repo's vendored copies are
pinned at **`679a317`** and `.github/workflows/ci.yml:70` compares each vendored file against that
**immutable ref** (`?ref=$PIN`), never against main's tip — so a new vector on a branch cannot move
that check, and nothing under `core/src/test/resources/sync-vectors/` was touched. **No cross-repo
drift event.**

### WP-5 Proven live — five mutations, five caught

A suite that has never failed is not evidence, and every new assertion here landed beside the code
it tests, so all of them are pins by construction. Each was checked against a mutated parser;
production tree byte-identical afterwards.

| # | mutation | caught by |
| --- | --- | --- |
| M1 | unknown-field check removed | **`invalid-unknown-field -> decrypt_failed -- got accepted`** |
| M2 | non-string `sig` degrades to unsigned | the `sig` assertion |
| M3 | numbers may be quoted strings | **both** type assertions |
| M4 | pairing-id shape check removed | the pairing-id assertion |
| M5 | root need not be an object | **unhandled `InvalidOperationException` out of `Parse`** |

**M1 is the load-bearing row** and it failed in exactly the shape B-6 predicted — *accepted*, not
"rejected for another reason". **M1 also took two further assertions with it**, and that cascade is
not a second finding: accepting the envelope commits its `seq`, moving the e2p high-water mark to
12, so `invalid-unknown-kind` (seq 8) then reported `replay_rejected` and the tracker assertion
failed. **§10.1 already documents that the suite's `seq` space is packed by design**, so this is
that documented property doing its job, not a new one. Recording it as an independent discovery
would be the phantom these records exist to prevent.

**M5 is worth its own sentence** because its failure mode is not a `FAIL` line: without the guard,
`EnumerateObject()` throws straight out of `Parse`, past the `ParseResult` contract entirely. That
is the **same shape as the twelfth iteration's `parsePullPage` finding**, one layer down — a
function whose contract says total and whose implementation is partial. The guard ships; the
mutation is the argument for it.

### WP-6 The pin moved, and what did not run

`$ExpectedOfflineTotal` **598 → 610**, swept as one unit with every doc the verifier asserts
against (`README.md`, `src/Engine/README.md`, `docs/CareerSeeker-Project-Summary.md`,
`docs/External-Audit-Handoff.md`) and the `Assert-Contains` literals themselves, per CLAUDE.md's
drift trap. SyncHarness **130 → 142** (+11 parser assertions, +1 vector).

**`scripts/Verify-Alpha.ps1` did NOT run, and could not even be parse-checked** — there is no
PowerShell here and none in the Ubuntu archive. **610 is measured, not guessed, and not measured
end-to-end.** Nine of its ten offline harnesses run here and sum to **393**:

```
Slice 28 · ResearcherHarness 57 · HookHarness 16 · StoreParityHarness 28 · GatewayGateHarness 36
DispatcherNoSendHarness 35 · LifecycleHarness 45 · RendererHarness 6 · SyncHarness 142   = 393
```

**`EngineHarness` cannot complete on Linux**, and the reason is benign: `PlanInstalledWorkspace()`
resolves a Windows install path, which on Linux resolves to a volume root, and
`FullDataDeletion.ResolveAllowedWorkspace` **correctly refuses** it (`src/Engine/FullDataDeletion.cs:81`).
Its **217** is therefore quoted from `Verify-Alpha.ps1`'s own comment rather than re-measured.
**393 + 217 = 610.** The drift-trap *pairing* was verified directly — every changed
`Assert-Contains` literal was confirmed to occur in the doc it targets — but that is a string
check, not a verifier run. **CI on `windows-latest` is the gate for this pin.**

### WP-7 PQ-AAD-1 is closed, and the answer is a real divergence

The twenty-first run left **PQ-AAD-1** open — whether `src/Sync` encodes its AAD as UTF-8, which
would make the two ends disagree on every non-ASCII header — and recorded that it needed "one
`grep` on a machine with .NET". **Both halves are now measured in one session**, and the grep alone
would have given the wrong answer.

The engine encodes `Encoding.ASCII.GetBytes(aad)` (`src/Sync/EnvelopeCodec.cs:31,45`;
`DeviceSignature.cs:38`), matching the phone's `US_ASCII`. **So the encodings agree by name.** They
do not agree in behaviour:

| input | Java `US_ASCII` | .NET `Encoding.ASCII` | |
| --- | --- | --- | --- |
| `é`, `è`, `Ж` (BMP) | `543F` | `543F` | agree |
| **`😀` (surrogate pair)** | **`543F` — one byte** | **`543F3F` — two** | **DIVERGE** |
| literal `?` | `543F` | `543F` | agree |

**Java collapses a surrogate pair to one `?`; .NET emits one per surrogate.** So a supplementary-plane
character anywhere in `ts` or `key_id` produces **different AAD bytes on the two sides**, and §5.4's
signature input inherits it.

**Severity, stated precisely: this fails closed and it is not a bypass.** The sender seals under its
own AAD and the receiver opens under its own, so the outcome is a tag mismatch → `decrypt_failed`:
an interop failure, not an authentication one. It is also **unreachable for a conforming sender** —
`ts` is RFC 3339 and `key_id` is an opaque ASCII id — but **nothing enforces that**, because
`EnvelopeJson` on *both* sides takes those two fields as arbitrary strings with no charset check.
That was the twenty-first run's finding; this run supplies the cross-implementation half.

**Deliberately not fixed here.** The clean resolution is to constrain `ts` and `key_id` to ASCII in
§3, which makes the divergence structurally unreachable — but that is a **wire-visible spec change
touching both implementations**, so it is a gate for Brandon, not a unilateral edit. Adding a
charset check to the C# parser alone would make the engine stricter than the phone, which is the
mission's named field bug pointing the other way. Recorded in `docs/protocol-questions.md`.

### WP-8 One question opened: PQ-DUP-1

Measured while probing parser totality: **§3 says nothing about duplicate top-level keys**, and
.NET takes the **last** one — `{…,"seq":12,…,"seq":99,"v":7}` parses as `seq=99, v=7`, while
`EnumerateObject()` still sees all ten properties (so an unknown *duplicate* is still caught by the
unknown-field check). Kotlin's `JsonObject` is a map and very probably behaves the same, **but the
phone half was not measured** and is not claimed. Filed rather than fixed, for the same reason as
WP-7. Not a bypass: a duplicated `seq` changes the AAD and the envelope then fails to decrypt.

### Boundary — what this iteration did not touch

**No merge in either repo**, and PR **#37** is a **draft stacked on #32**, not on `main` — the main
repo's merge policy is conditional on a full local `Verify-Alpha.ps1` gate **I could not run**, so
merging was never eligible. **Draft PRs #32–#36 were not touched** — not merged, retargeted,
rebased or force-pushed. **No existing vector byte changed** (`git diff --name-only` over
`docs/sync-vectors/v1/` excluding `index.json` printed **0**), so the vendored pin `679a317` is
intact by construction. **No android file changed at all this iteration except the house records**
— no Kotlin, no `:app`, no `core/src/`, and **`:core:test` did not run** (the change is engine-side,
and nothing in `:core` moved). **No `relay/` file.** **No force-push, no history rewrite, no branch
deletion, no deploy of any kind**, no contact with the production relay (not even `GET /v1/health`),
no Google/Play/OAuth console, no accounts, no purchases, no Play Billing code, no Gmail, no
`.appdata`, and **no secret printed or read**. **B-1, B-2, B-4, B-5, B-8 untouched**; **B-6 is
RESOLVED** and B-7 is unchanged and re-measured in its own terms.

**The standing limit, restated in this run's terms because it changed shape.** The android gate
(`checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug`) **did not run** — B-7's
three SDK-dependent tasks are still blocked, and `:core:test` was not exercised because nothing in
`:core` changed. `scripts/Verify-Alpha.ps1` **did not run** — no PowerShell. What *did* run is the
full C# solution build and nine of ten offline harnesses. **CI is still the gate**, and citing this
entry as "the engine gate passed" would be the exact failure these records exist to prevent.
Re-verify: **C-WP-1…12**.
