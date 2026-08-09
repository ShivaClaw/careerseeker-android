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
