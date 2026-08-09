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

*Expected:* no worktree added by this session — the list holds 8 entries, unchanged from its
pre-session state (the main checkout, the pre-existing `.claude/worktrees/*`, and temp review
trees).

**Do not read new commits there as mine.** A parallel session was working in that repo
throughout this window, and a naive `git log --since` will show its work:

```bash
git -C C:/Users/bkirk/Documents/CareerSeeker log --all --since='2026-07-30 20:00' \
    --pretty=format:'%h %an %s'
```

*Expected:* ~11 commits, all on `codex/beta-*` branches (Beta B0–B3: redraft prevention, crash
recovery/`ReconcileAsync`, lexical ranking) plus merges of PRs #9–#11. **None touch
`docs/Sync-Protocol.md`, `docs/sync-vectors/`, or `relay/`.** Confirm the boundary that
actually matters — that the normative contract and vectors were not modified:

```bash
git -C C:/Users/bkirk/Documents/CareerSeeker log --all --since='2026-07-30 20:00' \
    --oneline -- docs/Sync-Protocol.md docs/sync-vectors relay/
```

*Expected:* no output. This session read those paths with `git show` and wrote nothing
anywhere in that repo.

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

---

## A4 — Pairing logic + provenance labelling

### C-A4-1 — The banner was on one screen and is now on all of them

```bash
git show 9992718:app/src/main/kotlin/app/careerseeker/dashboard/ui/HomeScreen.kt | grep -c StatusBanner   # 2 — Home only
for f in ApplicationsScreen JobsScreen EvidenceScreen ApplicationDetailScreen; do
  echo -n "$f: "; git show 9992718:app/src/main/kotlin/app/careerseeker/dashboard/ui/$f.kt | grep -c StatusBanner
done   # all 0 — this is the bug
grep -n 'topBar' app/src/main/kotlin/app/careerseeker/dashboard/ui/DashboardApp.kt   # the fix
```

```powershell
.\gradlew.bat --no-daemon :app:test --tests '*ScreensFromFixtureTest*' --rerun-tasks
```

*Expected:* 8 tests green, including `theProvenanceBannerIsShownOnEveryTab` (walks all four
tabs) and `theBannerFollowsIntoTheApplicationDetailOverlay`.

### C-A4-2 — Pairing agrees with the engine, and refuses to downgrade

```powershell
.\gradlew.bat --no-daemon :core:test --tests '*PairingSessionTest*' --rerun-tasks
```

*Expected:* 8 green. The two worth reading: `the completion this phone builds is the one the
engine opens` (builds the completion, then derives from the wire `phone_pub` and opens it as
the engine would, asserting both sides compute the same confirm code) and `an unrecognised
suite refuses to pair instead of falling back` (§5.2's no-silent-downgrade rule, exercised with
the reserved PQ suite).

### C-A4-3 — No private key material in `:core`

```bash
grep -rn 'PrivateKey\|getPrivate\|KeyStore' core/src/main/kotlin/app/careerseeker/core/PairingSession.kt || echo "none — good"
```

*Expected:* none. The device signing key enters as a public point plus a signing function.

---

## A5 — Live end-to-end

### C-A5-1 — The one live call that was made

```bash
curl -s -i https://relay.careerseeker.app/v1/health
```

*Expected:* `200` with `{"ok":true,"protocol":1,"phase":"p1"}`. This is the whole of this
session's contact with production: a GET on the route §2 defines as returning no pairing
information. **No pairing was created, no envelope pushed, nothing deployed.** Confirm by
absence:

```bash
git log --all --oneline -- relay/ 2>/dev/null | head   # nothing; relay/ is the other repo
grep -rn 'careerseeker.app' core/src/test app/src/test || echo "no test targets production"
```

### C-A5-2 — Why full e2e was not reachable (verify the claim, do not take it)

```bash
grep -n 'sync' HANDOFF.md | grep -i 'no-op'
emulator -list-avds ; adb devices
```

*Expected:* `HANDOFF.md` §4 states the engine's `--sync` is honored but no-ops pending a
device-bound pairing; no AVDs and no attached devices. The blocker is engine-side, not
phone-side — see `BLOCKED.md` B-2.

---

## A6 — Outcomes + entitlement

### C-A6-1 — The `outcome` field was being dropped (F-5)

```bash
git show 54b8937:app/src/main/kotlin/app/careerseeker/dashboard/replica/EnvelopeApplier.kt | grep -c outcome  # 0
grep -n 'outcome' app/src/main/kotlin/app/careerseeker/dashboard/replica/EnvelopeApplier.kt
ls app/schemas/app.careerseeker.dashboard.replica.ReplicaDb/   # 1.json 2.json 3.json
```

### C-A6-2 — A state-changing envelope cannot be built unsigned

```powershell
.\gradlew.bat --no-daemon :core:test --tests '*OutboundEnvelopesTest*' --rerun-tasks
```

*Expected:* 9 green. Note these round-trip through `EnvelopeReceiver` rather than asserting on
strings — in particular `a tampered outcome envelope stops verifying` and `the entitlement
courier forwards original_json byte-for-byte`.

### C-A6-3 — **The phone cannot claim Pro.** The central check.

```powershell
.\gradlew.bat --no-daemon :core:test --tests '*ProStateTest*' --rerun-tasks
```

*Expected:* 5 green, including `the unlocked state is unreachable from any entitlement
verdict`, which checks every verdict exhaustively. Then confirm structurally:

```bash
grep -rn 'Unlocked(' core/src/main/kotlin/ app/src/main/kotlin/
```

*Expected:* exactly one construction site, inside `ProState.afterEngineAck`. Any other would
be a path from a device-local opinion to a paid feature.

### C-A6-4 — Nothing unlocks Pro today, and that is deliberate

```bash
grep -rn 'afterEngineAck' app/src/ || echo "no caller — expected"
grep -rn 'entitlement_ack' app/src/main/ || echo "no applier branch — expected"
```

*Expected:* no callers. §4.3 defines `entitlement_ack` with **no body**, so no parser was
written for it (PQ-A6-1). The app is honestly Free with no way to become anything else.

### C-A6-5 — Ktor is pinned below the newest release on purpose

> **Claim.** Ktor 3.2.0 cannot be dexed at minSdk 26; the pin is 3.1.3.

Reproduce the failure if you want to see it (edit `gradle/libs.versions.toml` to `3.2.0`, then):

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug --rerun-tasks
```

*Expected on 3.2.0:* `:app:mergeExtDexDebug FAILED` with `Space characters in SimpleName 'use
streaming syntax' are not allowed prior to DEX version 040`. Restore `3.1.3` afterwards.

Note that **`:core:test` and `:app:test` pass on 3.2.0** — unit tests are not dexed. Only the
APK build catches it, which is the argument for assembling at every milestone.

*Expected fix NOT taken:* `minSdk` is still 26.

```bash
grep -n 'minSdk' app/build.gradle.kts
```

Raising it to 30 would clear the error by dropping Android 8–10 devices — a product decision,
not a build fix.

---

## S0 — re-entry and derivation (2026-08-08)

Every claim in [`docs/S-Ladder.md`](docs/S-Ladder.md) and [`STATE.md`](STATE.md) has its command
here. `$A` is this repo; `$S` is the main-repo clone at
`C:\Users\bkirk\Documents\careerseeker-sync`. **Fetch both first** — every count below is
meaningless against stale refs.

```powershell
$A='C:\Users\bkirk\Documents\careerseeker-android'; $S='C:\Users\bkirk\Documents\careerseeker-sync'
git -C $A fetch --all --prune; git -C $S fetch --all --prune
```

### C-S0-1 — The vendored vectors have not drifted from pin `679a317`

> **Claim.** All 26 vendored vector files are byte-identical to the pinned upstream commit.
> This is B-3's check, run for real against an independent clone rather than a same-machine tree.

```powershell
$pin='679a3175590dcd021b21c85af9daf12114e131fd'
git -C $S cat-file -e "$pin^{commit}"; "pin present: exit=$LASTEXITCODE"
$files = git -C $A ls-tree -r --name-only HEAD -- core/src/test/resources/sync-vectors/v1
$m=0; $n=0
foreach ($f in $files) {
  $name = Split-Path $f -Leaf
  $h1 = git -C $A rev-parse "HEAD:$f"
  $h2 = git -C $S rev-parse "${pin}:docs/sync-vectors/v1/$name" 2>$null
  $n++; if ($h1 -ne $h2) { $m++; "MISMATCH: $name" }
}
"compared=$n mismatches=$m"
```

*Expected:* `pin present: exit=0`, then `compared=26 mismatches=0`. Blob hashes are compared, not
working-tree bytes, so line-ending and checkout settings cannot produce a false pass or fail.

*Caveat this check surfaces:* the pin is **not** an ancestor of `origin/main` — see C-S0-5.

### C-S0-2 — `p4-pro` is not a separate lineage, and `main` has diverged

> **Claim.** `claude/p4-pro` and `claude/p2-replica` are the same commit; `a0-probe` and
> `p5-store` are siblings off it; `main` is docs-only and is not an ancestor of the code lineage.

```powershell
git -C $A rev-parse origin/claude/p4-pro origin/claude/p2-replica
git -C $A ls-tree --name-only origin/main
git -C $A merge-base --is-ancestor origin/main origin/claude/p2-replica; "main ancestor: exit=$LASTEXITCODE"
git -C $A rev-list --left-right --count origin/main...origin/claude/p2-replica
git -C $A merge-base --is-ancestor origin/claude/p5-store HEAD; "p5 ancestor of a0-probe: exit=$LASTEXITCODE"
git -C $A merge-base origin/claude/p5-store HEAD
```

*Expected:* the two rev-parses print the **same** SHA `d9f95fd…`; `main`'s tree is exactly
`HANDOFF.md README.md docs`; `main ancestor: exit=1` (diverged); the counts are `10  23`;
`p5 ancestor of a0-probe: exit=1`; and the merge-base is `d9f95fd…` — i.e. siblings.

### C-S0-3 — The two-lineage collision is three files, and `ApplicationDetailScreen` is not one

> **Claim.** `a0-probe` ∩ `p5-store` = HomeScreen, ApplicationsScreen, ScreensFromFixtureTest.

```powershell
$x = git -C $A diff --name-only d9f95fd..HEAD
$y = git -C $A diff --name-only d9f95fd..origin/claude/p5-store
Compare-Object $x $y -IncludeEqual -ExcludeDifferent | ForEach-Object { $_.InputObject }
```

*Expected:* exactly three paths. `ApplicationDetailScreen.kt` is **absent** — it is touched only
by `p5-store`, correcting the mission's expectation. Nothing here was resolved: merge policy is
Brandon's.

### C-S0-4 — The engine stack is intact and 85 behind

```powershell
$b = 'claude/android-apk-build-setup-90d9d5','claude/p1-sync','claude/p2-publisher','claude/p4-entitlement'
foreach ($x in $b) { "$x " + (git -C $S rev-list --left-right --count "origin/main...origin/$x") }
git -C $S merge-base --is-ancestor origin/claude/android-apk-build-setup-90d9d5 origin/claude/p1-sync; "5in6=$LASTEXITCODE"
git -C $S merge-base --is-ancestor origin/claude/p1-sync origin/claude/p2-publisher; "6in7=$LASTEXITCODE"
git -C $S merge-base --is-ancestor origin/claude/p2-publisher origin/claude/p4-entitlement; "7in8=$LASTEXITCODE"
```

*Expected:* ahead-counts `3 / 6 / 13 / 21` and behind-count **85** for all four (the mission's
"~58" predates `main` advancing 27); all three ancestry checks `exit=0`.

### C-S0-5 — The whole sync track is missing from `main` (the finding that gates S2–S6)

```powershell
$pat = '^relay/|^src/Sync/|Sync-Protocol|sync-vectors/|SyncHarness'
"main:  " + (git -C $S ls-tree -r --name-only origin/main | Select-String $pat | Measure-Object).Count
"PR#8:  " + (git -C $S ls-tree -r --name-only origin/claude/p4-entitlement | Select-String $pat | Measure-Object).Count
```

*Expected:* `main: 0` and `PR#8:` a number in the mid-40s. The protocol spec, the 26 shared
vectors, the blind relay and the C# sync sources exist **only** on the unmerged stack — which is
why S1 gates S2, S4, S5 and S6, and why B-2 cannot be closed by writing publisher code first.

### C-S0-6 — Gate `P0-BASE` is superseded

```powershell
gh pr view 4 --repo ShivaClaw/careerseeker --json state,headRefName,mergedAt
git -C $S rev-parse origin/main
```

*Expected:* PR #4 (`claude/alpha-finish`) is `MERGED`. The alpha train landed, so "target
`claude/alpha-finish`" is dead; the base of record is `origin/main` =
`3a89fb58673712ac46aff82b35d7d269cb15793c`.

### C-S0-7 — The stale `p5` worktree is gone, and nothing was lost with it

```powershell
git -C $A worktree list
Test-Path C:\Users\bkirk\Documents\careerseeker-android-p5
git -C $A rev-parse claude/p5-store origin/claude/p5-store
```

*Expected:* one worktree (the main tree); `False`; and both p5 refs still resolve to `bb7f4d0`.
The tree was verified clean before removal. `git worktree remove` itself **failed** with
`Filename too long` after de-registering the worktree, leaving orphaned Gradle `build/` output on
disk; that residue was cleared with a robocopy mirror-empty (the standard Windows long-path
removal). No commit, branch or PR was affected — `p5-store` is intact on the remote and PR #5
remains an untouched draft.

### C-S0-8 — No collision with Terra this iteration

```powershell
git -C $S show origin/autonomy/codex-state:STATE.md | Select-String -Pattern 'Files claimed' -Context 0,2
```

*Expected:* "Files claimed: **none** for the next iteration." Terra is BLOCKED on R6(b)/PR #26.
Terra's worktree `C:\Users\bkirk\Documents\CareerSeeker-r6-sbom` and the tree
`C:\Users\bkirk\Documents\CareerSeeker` were not read or written this rung.

*Also note:* Terra's measured `$ExpectedOfflineTotal` is **412** (was 407). S1 must re-derive it
rather than copy it, and sweep every count-reporting doc in the same commit.

### C-S0-9 — CI actually ran the vector step (B-3 closed)

> **Claim.** The vendored-vector step executed and passed on CI — not merely that the workflow
> went green.

```powershell
gh run view 31278769047 --repo ShivaClaw/careerseeker-android `
  --json jobs --jq '.jobs[].steps[] | "\(.conclusion)  \(.name)"'
```

*Expected:* every step `success`, and specifically the line
`success  Assert vendored sync vectors match the pinned main-repo commit`.

**Check the step, not the run.** A skipped step still yields a green workflow, and B-3's entire
content was "this specific step has never executed here" — so an overall-green reading would not
have closed it. This run also exercised the full gate on a clean Linux checkout (`:core` tests,
`:app` Robolectric tests, debug APK, lint, no-analytics assertion), which is **CI's** result and
not a local re-run; S0 touched no source and the gate was deliberately not re-run on this machine.

---

## S1 — the engine sync track lands in the main repo (2026-08-09)

S1 ran in `careerseeker`, not here, but it is what unblocks this repo's roadmap, and the
cross-repo vector check below is the one an android auditor should care about most.
`$S` is the main-repo clone.

### C-S1-1 — The four PRs were re-cut, not force-pushed, and the originals are closed as superseded

```powershell
gh pr list --repo ShivaClaw/careerseeker --state all --limit 12 `
  --json number,title,state,headRefName --jq '.[] | "\(.number) \(.state) \(.headRefName)"'
```

*Expected:* #27/#28/#29/#30 `MERGED` on `claude/s1-*` branches, and #5/#6/#7/#8 `CLOSED`.
Force-push and history rewrite are embargoed this window, so a rebase of a live PR branch was not
available; each PR was re-cut onto fresh `main` and the original closed with a comment saying what
superseded it. **No branch was deleted** — which also keeps pin `679a317` reachable (see C-S1-3).

### C-S1-2 — Every pin was measured, never carried over

> **Claim.** The offline total moved 418 → 457 → 486 → 528 → 591, each figure produced by running
> the verifier rather than by arithmetic on a commit message.

```powershell
git -C $S log --oneline -30 -- scripts/Verify-Alpha.ps1
git -C $S show origin/main:scripts/Verify-Alpha.ps1 | Select-String 'ExpectedOfflineTotal = '
```

*Expected:* final pin **591**. The per-rung deltas are recorded in the pin's own comment block.

**Why this is worth checking rather than trusting:** at P1 the commit subject said
*"harness 39->60"*, which implies 478. The real total was **486** — the branch's later commits took
SyncHarness to 68. A plausible-looking number computed from a commit message would have been wrong
and would still have looked right in review.

### C-S1-3 — Cross-repo: `main` now carries vectors byte-identical to this repo's pin

> **Claim.** The vendored vectors here (`VECTORS.lock` pin `679a317`) are byte-identical to what
> now sits in the main repo's `main`. Landing the stack did not desynchronise the two repos.

```powershell
$pin='679a3175590dcd021b21c85af9daf12114e131fd'
$n=0; $m=0
foreach ($f in (git -C $S ls-tree -r --name-only $pin -- docs/sync-vectors/v1)) {
  $a = git -C $S rev-parse "${pin}:$f"
  $b = git -C $S rev-parse "origin/main:$f"
  $n++; if ($a -ne $b) { $m++; "DIFFERS: $f" }
}
"compared=$n differences=$m"
```

*Expected:* `compared=26 differences=0`.

**The state this changes.** Before S1, pin `679a317` was reachable **only through an unmerged
branch** — C-S0-1 recorded that as a caveat. It is now content-identical to `main`. The pin itself
still names a non-`main` commit, so re-pinning to a `main` commit is queued as follow-up work; it
is a tidiness fix, not a correctness one, and it must be done by comparing content, not by assuming.

### C-S1-4 — The rebase moved commits, not bytes

```powershell
# per rung, comparing each re-cut branch against its pre-rebase original
git -C $S diff --stat origin/claude/p4-entitlement origin/claude/s1-p4-entitlement -- docs/sync-vectors
```

*Expected:* no differences under `docs/sync-vectors`. Measured per rung: 16, 22, 22, 27 files
compared, **0 drift** every time. This was S1's hard stop — changed vector *content* would have been
a cross-repo drift event and a full stop, not a merge conflict to resolve.

### C-S1-5 — The three breakages the gate caught that reading would not have

> **Claim.** Rebasing an 85-commit-stale stack broke three things, and each was caught by running
> the gate rather than by inspecting the diff.

```powershell
git -C $S show origin/main --stat | Select-String 'Host.cs|Program.cs|EngineHarness'
git -C $S log origin/main --oneline --grep='Re-derive the offline pin'
```

*Expected:* four "Re-derive the offline pin" commits, whose messages record:

1. **`CS1503` in `src/Engine/Program.cs`** (twice, P2 and P4) — a constructor argument passed
   positionally that is no longer that parameter, because `main` grew parameters ahead of it. After
   the second occurrence all 11 `LocalDashboard`/`EngineHost` call sites were audited, not just the
   one the compiler named.
2. **An unhandled `TaskCanceledException` in `EngineHarness`** — P4's Pro assertions hit a
   hard-coded `localhost:7777`, but `main` had moved that section to a free port because HTTP.sys
   keeps 7777 reserved. **The compiler cannot see this**, and the symptom was the whole harness
   dying on a 3-second timeout, not a failed assertion. `main`'s own fix exists because the same
   hazard had previously left *"19 assertions quietly not running"*.
3. **A stale instruction in `docs/Scoring-Calibration.md`** — it told readers EngineHarness "must
   report 170 passed". The verifier only asserts the doc *contains* that string, so it would have
   kept passing while misleading every reader. Moved 170 → 186 → 210 with the measurements.

*Expected conclusion:* a green build proves nothing about (2). Only running the harnesses does.

### C-S1-6 — The load-bearing invariant survived verbatim

```powershell
git -C $S show origin/main:src/Engine/EngineSyncBridge.cs | Select-String -Context 3 '_snapshotSent, 1'
```

*Expected:* the flag flips **only** after a successful push (`if (ok) Volatile.Write(...)`), with the
2026-07-24 audit-finding comment intact. A failed first snapshot is retried and never demoted to a
delta — which is what stops a fresh phone merging deltas into demo fixture rows.

---

## S5 (first half) — entitlement_ack spec + vectors (2026-08-09)

`$S` is a clone of `ShivaClaw/careerseeker`; the branch under test is
`origin/claude/s5-entitlement-ack-spec` (draft PR #32). Every command below was executed this
session unless the entry says otherwise, and where it says otherwise it says so in the *Expected*
line.

### C-S5-1 — The vectors are reproducible, and not hand-written

> **Claim.** `entitlement-ack` and `entitlement-ack-no-order-id` are generator output, byte-for-byte
> reproducible by anyone.

```bash
git -C $S checkout origin/claude/s5-entitlement-ack-spec
node docs/sync-vectors/generate.mjs --check
```

*Expected:* `OK: 28 vector files match the generator.`, exit 0. A regenerate-then-check
(`node docs/sync-vectors/generate.mjs && node docs/sync-vectors/generate.mjs --check`) must print
`Wrote 28 files to docs/sync-vectors/v1/ (9 valid, 18 invalid).` and leave the working tree clean —
if it does not, a vector was hand-edited.

### C-S5-2 — The change is additive: no existing vector's bytes moved

> **Claim.** All 25 pre-existing vector files are byte-identical to `origin/main`. `index.json` is
> the only existing file that changed, and only by two appended entries. This is the check that
> would catch a cross-repo drift event.

```bash
cd $S && git checkout origin/claude/s5-entitlement-ack-spec
for f in docs/sync-vectors/v1/*.json; do
  git cat-file -e origin/main:$f 2>/dev/null || { echo "NEW: $f"; continue; }
  [ "$(git rev-parse origin/main:$f)" = "$(git hash-object $f)" ] || echo "CHANGED: $f"
done
git diff origin/main...HEAD -- docs/sync-vectors/v1/index.json
```

*Expected:* exactly two `NEW:` lines (`entitlement-ack.json`, `entitlement-ack-no-order-id.json`),
**zero `CHANGED:` lines**, and an `index.json` diff that is purely two appended array entries with
no deletions. Measured: 25 unchanged, 0 changed. **Any `CHANGED:` line other than `index.json` is a
drift event and a hard stop** — this repo vendors those bytes.

### C-S5-3 — This repo's vendored vectors are untouched, and its pin still holds

> **Claim.** Adding vectors upstream did not disturb the vendored copies pinned at `679a317`.

```powershell
git -C . diff --stat origin/main -- core/src/test/resources/sync-vectors/
git -C . show origin/claude/android-a0-probe:core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected:* no diff under `core/src/test/resources/sync-vectors/` from this iteration, and the lock
still naming `679a317`. This repo's CI step compares vendored files against **that pin**, not
against upstream `main`, so upstream growing by two files cannot fail it. Seeing the new vectors
here requires a deliberate re-vendor, which this iteration did not do.

### C-S5-4 — The vectors decrypt, independently of the generator that made them

> **Claim.** Both ciphertexts open to the stated plaintext under the published key/nonce/AAD, carry
> no `sig` (they are `e2p`), and reject a tampered AAD.

```bash
node - <<'JS'
const { createDecipheriv } = require('node:crypto'), { readFileSync } = require('node:fs');
const unb64u = s => Buffer.from(s.replace(/-/g,'+').replace(/_/g,'/'), 'base64');
for (const n of ['entitlement-ack','entitlement-ack-no-order-id']) {
  const v = JSON.parse(readFileSync(`docs/sync-vectors/v1/${n}.json`,'utf8'));
  const c = unb64u(v.ciphertext_b64u);
  const d = createDecipheriv('aes-256-gcm', Buffer.from(v.key_hex,'hex'), unb64u(v.nonce_b64u));
  d.setAAD(Buffer.from(v.aad,'ascii')); d.setAuthTag(c.subarray(c.length-16));
  const pt = Buffer.concat([d.update(c.subarray(0,c.length-16)), d.final()]).toString('utf8');
  console.log(n, pt === JSON.stringify(v.plaintext_json), v.envelope_json.sig === undefined);
}
JS
```

*Expected:* `true true` for both. Measured: both open, `kind` is `entitlement_ack`, `order_id`
present in the first and absent in the second, neither envelope carries `sig`, the AAD reconstructs
from the envelope header, and a `dir=e2p`→`dir=p2e` AAD tamper breaks the tag on both.

### C-S5-5 — The engine gate ran, and the offline pin did not need to move

> **Claim.** SyncHarness stayed at 130 assertions and `$ExpectedOfflineTotal` held at 598, so no
> count-reporting doc needed the drift-trap sweep. **I did not run this** — there is no .NET on the
> machine this iteration ran on. CI on `windows-latest` is the gate of record.

```bash
gh run view 31292158471 --repo ShivaClaw/careerseeker --log | rg 'Offline total|=== 130 passed'
# or, without gh:
#   open https://github.com/ShivaClaw/careerseeker/actions/runs/31292158471
```

*Expected, and measured from the job log:*

```
=== 130 passed, 0 failed ===
=== Offline total: 598 passed, 0 failed ===
CareerSeeker alpha verification complete.
```

Job conclusion **success**. The reason it holds is checkable independently of the run: both
consumers filter vectors on `type == "envelope"`, and that partition is unchanged at 18 envelope /
2 pairing / 5 entitlement before and after —

```bash
node -e 'const i=require("./docs/sync-vectors/v1/index.json");
  const c={}; i.vectors.forEach(v=>c[v.type]=(c[v.type]||0)+1); console.log(c)'
```

*Expected:* `{ envelope: 18, pairing: 2, entitlement: 5, entitlement_ack: 2 }`, with the first three
matching `origin/main`'s index. The two new vectors are invisible to every harness loop, which is
why the count did not move.

### C-S5-6 — The new vectors are specified, not implemented (the claim NOT to over-read)

> **Claim.** Nothing asserts against `entitlement-ack` yet, in either language, and the phone still
> cannot reach an unlocked state.

```powershell
git -C $S grep -n 'entitlement_ack' -- src tests        # engine: no applier branch
git -C . grep -rn 'afterEngineAck'                      # phone: contract exists, no caller
```

*Expected:* in the engine, `entitlement_ack` appears in `Protocol.cs`'s `ShippingKinds` and nowhere
else that acts on it. On the phone, `ProState.afterEngineAck` is defined and **called only from its
own unit test** — no applier branch calls it. `Sync-Protocol.md` §10.2 states this in the spec
itself. If a future session finds a caller without a corresponding gate run, that is the drift this
entry exists to catch.
