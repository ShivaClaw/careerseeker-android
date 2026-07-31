# AUDIT-REQUEST — Android Alpha (P3) ladder

For the adversarial review on ~2026-08-02. Every claim this session makes is listed with the
**exact command** that re-verifies it. A claim with no re-verification command is a bug in
this document.

**Auditor setup** (once per shell):

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
cd C:\Users\bkirk\Documents\careerseeker-android
git checkout claude/android-a0-probe
```

Note `--rerun-tasks` throughout: Gradle will otherwise report `UP-TO-DATE` and you would be
auditing a cache, not an execution.

---

## A0 — Environment probe + lane selection

### C-A0-1 — The target repo exists; the spec's "404" premise is stale

> **Claim.** `ShivaClaw/careerseeker-android` exists with 10 branches and 5 PRs, contradicting
> both `Android_Alpha_roadmap_spec.md` (`# 404 as of 2026-07-30`) and JULY-SUMMARY §S7.

```bash
git ls-remote https://github.com/ShivaClaw/careerseeker-android.git
```

*Expected:* refs including `claude/p0-scaffold`, `claude/p1-pairing`, `claude/p2-replica`,
`claude/p4-pro`, `claude/p5-store`, `main`, and `refs/pull/1..5`.

### C-A0-2 — Reference repo state matches JULY-SUMMARY §S3

```bash
git ls-remote https://github.com/ShivaClaw/careerseeker.git
```

*Expected:* `main` = `14a7dfe…`, `claude/p4-entitlement` = `d5bdb9d…`, `claude/p2-publisher`
= `7158202…`, `claude/p1-sync` = `6c46545…`, `fix/engine-actually-runs` = `40bc9a7…`.

### C-A0-3 — Toolchain supports Lane A

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -version
Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\platforms" | Select-Object -ExpandProperty Name
Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Select-Object -ExpandProperty Name
```

*Expected:* JDK 21.0.10; platforms include `android-37.0`; build-tools `36.0.0`.

### C-A0-4 — Vendored vectors are 5 behind upstream

> **Claim.** Upstream `claude/p4-entitlement` has 25 vectors; the phone vendors 20 at pin
> `fff4bce…`; the 5 `entitlement-*` vectors are absent on the phone.

```bash
# upstream count and names (read-only against the reference clone)
git -C C:/Users/bkirk/Documents/CareerSeeker ls-tree -r --name-only \
    claude/p4-entitlement -- docs/sync-vectors/v1 | grep -c '\.json$'
git -C C:/Users/bkirk/Documents/CareerSeeker ls-tree -r --name-only \
    claude/p4-entitlement -- docs/sync-vectors/v1 | grep entitlement

# vendored count and pin
ls core/src/test/resources/sync-vectors/v1/*.json | wc -l
grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected:* upstream 26 json files (25 vectors + `index.json`), 5 matching `entitlement`;
vendored 21 json files (20 vectors + `index.json`), 0 matching `entitlement`; pin =
`fff4bce9790788217d72be882f776b882993d640`.

### C-A0-5 — 42 tests pass, actually executed

> **Claim.** `:core` 17 + `:app` 25 = 42 tests, 0 failures, 0 errors, forced to re-run.

```powershell
.\gradlew.bat --no-daemon :core:test :app:test --rerun-tasks
```

*Expected:* `BUILD SUCCESSFUL`, `35 actionable tasks: 35 executed`. Then parse the XML rather
than trusting the console:

```powershell
$t=0;$f=0;$e=0
Get-ChildItem core\build\test-results\test\*.xml,app\build\test-results\testDebugUnitTest\*.xml |
  ForEach-Object { $x=[xml](Get-Content $_.FullName)
    $t+=[int]$x.testsuite.tests; $f+=[int]$x.testsuite.failures; $e+=[int]$x.testsuite.errors }
"tests=$t failures=$f errors=$e"
```

*Expected:* `tests=42 failures=0 errors=0`.

### C-A0-6 — `:core` is Android-free

```powershell
.\gradlew.bat --no-daemon checkCoreIsAndroidFree --rerun-tasks
```

*Expected:* prints `:core is Android-free.`

### C-A0-7 — Lane A: a debug APK assembles and lint is clean

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:lintDebug --rerun-tasks
Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

*Expected:* `BUILD SUCCESSFUL`, `49 actionable tasks: 49 executed`. The APK **hash will
differ** from the logged `7CF785A9…` — debug builds are not reproducible (timestamps, signing
salt). Audit the *existence and size class* (~12 MB), not hash equality.

### C-A0-8 — CI does not gate `:app` tests (finding F-1)

```bash
grep -n 'gradlew' .github/workflows/ci.yml
```

*Expected:* invocations of `checkCoreIsAndroidFree`, `:core:test`, `:app:assembleDebug`,
`:app:lintDebug`, `:app:dependencies` — and **no** `:app:test`. The 25 Robolectric tests are
ungated in CI.

### C-A0-9 — `main` has diverged from the code branches (finding F-3)

```bash
git ls-tree --name-only main            # docs lineage
git ls-tree --name-only claude/p4-pro   # code lineage
git log --oneline main --not claude/p4-pro | head
```

*Expected:* `main` contains `HANDOFF.md` + `docs/` and **no** `app/` or `core/`;
`claude/p4-pro` contains `app/`, `core/` and **no** `HANDOFF.md`; the log lists docs-only
commits present on `main` alone.

### C-A0-10 — Nothing was written to the reference repo

```bash
git -C C:/Users/bkirk/Documents/CareerSeeker status --short
git -C C:/Users/bkirk/Documents/CareerSeeker worktree list
git -C C:/Users/bkirk/Documents/CareerSeeker reflog -n 20
```

*Expected:* no worktree added by this session (the list is unchanged from its pre-session
state: the main checkout plus the pre-existing `.claude/worktrees/*` and temp review trees),
and no new commits authored during this session's window.

---

## A1 — Scaffold + CI reconciliation

### C-A1-1 — CI now gates `:app:test` (F-1 closed)

```bash
grep -n 'gradlew' .github/workflows/ci.yml
```

*Expected:* a `./gradlew :app:test` invocation now present (≈line 96), between `:core:test`
and `:app:assembleDebug`. Compare against the parent commit to see it was genuinely absent:

```bash
git show dd64160:.github/workflows/ci.yml | grep -c ':app:test'   # expect 0
git show HEAD:.github/workflows/ci.yml    | grep -c ':app:test'   # expect 1
```

### C-A1-2 — The newly-gated step actually passes

```powershell
.\gradlew.bat --no-daemon :app:test --rerun-tasks
```

*Expected:* `BUILD SUCCESSFUL`; XML shows 25 tests / 0 failures across `DemoFixtureTest` (3),
`EnvelopeApplierTest` (16), `ScreensFromFixtureTest` (6).

### C-A1-3 — Analytics promise holds on the resolved release classpath

```powershell
.\gradlew.bat --no-daemon -q :app:dependencies --configuration releaseRuntimeClasspath > deps.txt
Select-String deps.txt -Pattern 'firebase|crashlytics|gms:play-services-ads|appsflyer|com\.adjust|amplitude|mixpanel|segment\.analytics'
```

*Expected:* no matches, over ~710 resolved lines.

### C-A1-4 — README states the real status and the PROVISIONAL id

```bash
git diff dd64160..HEAD -- README.md
```

*Expected:* the "P0 — scaffold. No product features yet" claim is gone, replaced by a status
naming both what is built and what is not; a PROVISIONAL `applicationId` note is present.

### C-A1-5 — Not verified locally, and not claimed

The workflow's **vendored-vector drift step** requires the GitHub contents API and was not
executed in this session. It is asserted only to be *unchanged*, not *passing*:

```bash
git diff dd64160..HEAD -- .github/workflows/ci.yml | grep -E '^[-+].*(VECTORS.lock|api.github.com)'
```

*Expected:* no output — that step was not modified. Its pass/fail is CI's to report on push.

---

## A2 — Vector conformance

### C-A2-1 — Vendored vectors are byte-identical to the new pin

> **Claim.** All 26 vendored files match upstream `679a3175590dcd021b21c85af9daf12114e131fd`
> exactly, and the pin in `VECTORS.lock` was moved to that commit.

Compare **blobs, not working-tree bytes** — `core.autocrlf=true` on this machine gives the
working tree CRLF while git blobs and upstream are LF, so a byte compare of files on disk
reports 20 false "drift" hits. This is the check CI actually performs (it runs on Linux):

```powershell
$e='C:\Users\bkirk\Documents\CareerSeeker'; $pin='679a3175590dcd021b21c85af9daf12114e131fd'
git ls-files core/src/test/resources/sync-vectors/v1 | ForEach-Object {
  $name=[IO.Path]::GetFileName($_)
  $mine=(git rev-parse ":$_"); $theirs=(git -C $e rev-parse "${pin}:docs/sync-vectors/v1/$name")
  if ($mine -ne $theirs) { "DRIFT: $name" } }
```

*Expected:* no output (26 files compared). And:

```bash
grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected:* `679a3175590dcd021b21c85af9daf12114e131fd`.

### C-A2-2 — The added vectors were genuinely absent before

```bash
git show dd64160:core/src/test/resources/sync-vectors/v1/index.json | grep -c entitlement  # 0
ls core/src/test/resources/sync-vectors/v1/entitlement-*.json | wc -l                      # 5
```

### C-A2-3 — Only one upstream commit touched vectors, and the other 20 did not change

> This is why re-vendoring was additive rather than a full resync.

```bash
git -C C:/Users/bkirk/Documents/CareerSeeker log --oneline \
    fff4bce9790788217d72be882f776b882993d640..claude/p4-entitlement -- docs/sync-vectors
git -C C:/Users/bkirk/Documents/CareerSeeker diff --name-status \
    fff4bce9790788217d72be882f776b882993d640 claude/p4-entitlement -- docs/sync-vectors
```

*Expected:* one commit `679a317`; the diff shows 5 additions plus modifications to
`index.json` and `generate.mjs` only — no `M` on any pre-existing vector.

### C-A2-4 — 100% conformance, including every invalid vector

```powershell
.\gradlew.bat --no-daemon :core:test --rerun-tasks
```

*Expected:* `BUILD SUCCESSFUL`. Per-suite, from the XML:

```powershell
Get-ChildItem core\build\test-results\test\*.xml | ForEach-Object {
  $x=[xml](Get-Content $_.FullName)
  "{0,-24} tests={1} fail={2} err={3}" -f $x.testsuite.name.Split('.')[-1],
    $x.testsuite.tests,$x.testsuite.failures,$x.testsuite.errors }
```

*Expected:* `ProtocolTest` 11, `ProtocolVectorsTest` 6, `EntitlementVectorsTest` 5,
`EnvelopeJsonTest` 8 — **30 total, 0 failures, 0 errors**.

The conformance assertions worth reading rather than just running:

- `ProtocolVectorsTest.the receiver classifies every envelope vector exactly as the engine
  does` — walks `index.json`, accepts every valid envelope and asserts each invalid one
  rejects with its **stated** `expect_error`, then asserts the rejections did not advance the
  sequence tracker.
- `EntitlementVectorsTest.every entitlement payload classifies with the exact reason the
  vector names` — the five signed vectors, each compared against its own `entitlement.expect`
  field rather than a hardcoded list.
- `EntitlementVectorsTest.every entitlement envelope is accepted at the envelope layer` — a
  bad *purchase* must still be a good *envelope*.

### C-A2-5 — The receiver no longer routes on untrusted text (F-8)

```bash
git show dd64160:core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt | grep -n 'indexOf'
grep -n 'indexOf' core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt
```

*Expected:* the old version shows a hand-rolled substring scan for `"kind"`; the current one
shows none — `kindOf` parses JSON and returns null (→ `unknown_kind`) on malformed input,
matching the engine's `JsonDocument.Parse` behaviour.

### C-A2-6 — §3's unknown-field rule is now enforced (F-6)

```powershell
.\gradlew.bat --no-daemon :core:test --tests '*EnvelopeJsonTest*' --rerun-tasks
```

*Expected:* 8 tests pass, including `an unknown top-level field is rejected, not ignored` and
`fields of the wrong JSON type are rejected rather than coerced`.

### C-A2-7 — Claims deliberately **not** made

- **F-7 (size cap) was not fixed.** The engine measures the ciphertext exactly as the phone
  does; changing one side alone would break cross-implementation agreement. Recorded as
  PQ-A2-1 instead. Verify the engine's behaviour for yourself:
  ```bash
  git -C C:/Users/bkirk/Documents/CareerSeeker show \
      claude/p4-entitlement:src/Sync/EnvelopeReceiver.cs | grep -n 'MaxEnvelopeBytes'
  ```
  *Expected:* `if (ciphertext.Length > Protocol.MaxEnvelopeBytes)` — i.e. same as the phone.
- **The phone does not grant Pro.** `EntitlementVerifier` returning `ACCEPTED` means "worth
  forwarding to the engine". Nothing in `:core` sets an entitlement flag; the unlock path is
  A6 and is driven by the engine's `entitlement_ack`. See PQ-A2-4.
- **The CI vector-drift step still was not run locally** (GitHub API required). Unchanged
  from C-A1-5.

---

## A3 — Protocol client + persistence

### C-A3-1 — A delta before any snapshot is refused, and nothing is invented

> **Claim.** The applier had no such rule; a delta arriving first would have rendered a
> recent window as the user's whole pipeline.

Confirm the rule was genuinely absent before:

```bash
git show 7bc5667:app/src/main/kotlin/app/careerseeker/dashboard/replica/EnvelopeApplier.kt | grep -c snapshotSeen   # 0
grep -c snapshotSeen app/src/main/kotlin/app/careerseeker/dashboard/replica/EnvelopeApplier.kt                     # >0
```

Then run the rule's tests:

```powershell
.\gradlew.bat --no-daemon :app:test --tests '*EnvelopeApplierTest*' --rerun-tasks
```

*Expected:* green, including `deltaBeforeAnySnapshotIsAwaitedNotApplied`,
`aHeartbeatDoesNotCountAsASnapshot`, `deltaIsAppliedOnceASnapshotHasBeenSeen`,
`snapshotSeenLatchesAcrossLaterPayloads`.

### C-A3-2 — **An audit-derived test was amended. Read this one properly.**

`firstRealDeltaWipesDemoDataInsteadOfMergingIntoIt` (from the Codex audit of 2026-07-24, and
the subject of this branch's tip commit) is now
`firstRealDeltaIsRefusedOutrightRatherThanMergedIntoDemoData`.

```bash
git diff 7bc5667..HEAD -- app/src/test/kotlin/app/careerseeker/dashboard/replica/EnvelopeApplierTest.kt
```

Judge it against the invariant, not the assertions: *fixture data must never mix with, or
masquerade as, engine data*. The claim is that refusing the delta holds that invariant **more**
strictly than wiping-and-applying did, and that the wipe defense survives untouched for the
kinds that legitimately arrive first. Verify that last part directly — these two must still
pass **unmodified**:

```bash
git diff 7bc5667..HEAD -- app/src/test/kotlin/... | grep -A2 'firstRealSnapshotAlsoClears\|firstRealHeartbeatWipes'
```

*Expected:* no changes to either test body.

If you disagree with the amendment, the change is small and self-contained: the gate is one
`if` in `EnvelopeApplier.apply` plus one persisted column.

### C-A3-3 — Room schema v2 migration exists and is not destructive

```bash
ls app/schemas/app.careerseeker.dashboard.replica.ReplicaDb/     # expect 1.json AND 2.json
grep -n 'MIGRATION_1_2' -A4 app/src/main/kotlin/app/careerseeker/dashboard/replica/ReplicaDb.kt
```

*Expected:* an `ALTER TABLE sync_state ADD COLUMN snapshotSeen INTEGER NOT NULL DEFAULT 0`,
registered via `addMigrations`, with **no** `fallbackToDestructiveMigration` anywhere:

```bash
grep -rn 'fallbackToDestructive' app/src/ || echo "none — good"
```

### C-A3-4 — The relay client never dials production in tests

```powershell
.\gradlew.bat --no-daemon :core:test --tests '*RelayClientTest*' --rerun-tasks
```

*Expected:* 14 tests green. Confirm no test can reach the network — every one builds its
client over a `MockEngine`:

```bash
grep -c 'MockEngine' core/src/test/kotlin/app/careerseeker/core/RelayClientTest.kt
grep -rn 'CIO\|OkHttp\|HttpClient()' core/src/test/kotlin/app/careerseeker/core/RelayClientTest.kt || echo "no real engine — good"
```

### C-A3-5 — Transport invariants

```bash
grep -n 'startsWith("https://")' core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
grep -n 'redacted' core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expected:* TLS enforced in `init` (construction-time, so no retry path skips it), and
`toString` redacting the bearer. Both are asserted by
`cleartext is refused at construction, not at request time` and `the bearer never appears in
toString`.

### C-A3-6 — `:core` is still Android-free after gaining Ktor

```powershell
.\gradlew.bat --no-daemon checkCoreIsAndroidFree --rerun-tasks
```

*Expected:* `:core is Android-free.` Ktor 3.2.0's `ktor-client-core` carries no engine and no
Android dependency; the platform engine is `:app`'s choice.

### C-A3-7 — Ktor version was verified, not guessed

```powershell
(Invoke-RestMethod 'https://search.maven.org/solrsearch/select?q=g:io.ktor+AND+a:ktor-client-core&core=gav&rows=5&wt=json').response.docs | ForEach-Object { $_.v }
```

*Expected:* `3.2.0` is the newest listed (as of 2026-07-30).
