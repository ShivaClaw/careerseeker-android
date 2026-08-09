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
