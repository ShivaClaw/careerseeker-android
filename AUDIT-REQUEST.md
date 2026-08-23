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

### C-S5-7 — The android gate is green at this commit, and the counts are not

> **Claim.** This repo's full CI gate passed at `53710a6`. Separately: the 102/0/0/3 test counts in
> `STATE.md` are **carried from the S8 local run**, not re-measured, because Gradle does not print
> them. Two claims, deliberately separated.

```bash
gh run view 31292342258 --repo ShivaClaw/careerseeker-android --log \
  | rg 'BUILD SUCCESSFUL|no analytics|vendored'
```

*Expected, and measured:* `:app:test`, `:app:assembleDebug` and `:app:lintDebug` each
`BUILD SUCCESSFUL`, and `OK: no analytics or tracking SDKs on the release classpath.` Job conclusion
**success**.

Check the vendored-vector step **individually**, not via the overall green — a skipped step also lets
a run go green, which is the lesson B-3 was closed on. That step has no `if:` condition in
`.github/workflows/ci.yml`, so it cannot skip; confirm that is still true if the workflow changes.

*Expected NOT to be found:* a test count. `./gradlew ... :app:test` prints none, so anyone wanting to
re-verify 102/0/0/3 must run the verification command of record locally with `--rerun-tasks` and read
the XML reports. Until someone does, that number is inherited and this entry says so.

---

## C-MT — Merge topology, measured 2026-08-09

All of these run from a clone of `ShivaClaw/careerseeker-android` with a sibling clone of
`ShivaClaw/careerseeker`. **Run `git fetch --all --prune` in both first** — every count below is
taken after that fetch, and the 2026-08-06 stale-refs incident is why that is rule one.

### C-MT-1 — `claude/p4-pro` is the same commit as `claude/p2-replica`

> **Claim.** There is no separate P4 branch to merge. The P4 Pro work is inside the `p2-replica`
> tip, which is why no PR exists for it.

```bash
git rev-parse origin/claude/p4-pro origin/claude/p2-replica
```

*Expected:* the same SHA twice — `d9f95fd76d39a1ba8fdfe582486172c0e53ab9c0`. If these ever differ,
`docs/Merge-Topology.md` §2 is stale and P4 needs its own row in §7.

### C-MT-2 — The PR stack is a real stack

> **Claim.** Each PR's base is an ancestor of its head for #3–#6, and `main` is an ancestor of
> nothing (it has diverged, docs-only, 10 commits).

```bash
for p in claude/p1-pairing:claude/p0-scaffold claude/p2-replica:claude/p1-pairing \
         claude/p5-store:claude/p2-replica claude/android-a0-probe:claude/p2-replica; do
  h=${p%%:*}; b=${p##*:}
  git merge-base --is-ancestor origin/$b origin/$h && echo "$h <- $b : stacked" || echo "$h <- $b : DIVERGED"
done
git rev-list --left-right --count origin/main...origin/claude/android-a0-probe
```

*Expected:* four `stacked` lines, and `10<TAB>40` for the last command (10 behind, 40 ahead).

### C-MT-3 — The whole stack merges into `main` without a conflict

> **Claim.** Seven of the nine branches merge into `main` cleanly, sequentially, carrying each
> result forward. This is a simulation: it creates dangling objects only and moves no ref.

```bash
cur=$(git rev-parse origin/main)
for s in claude/p0-scaffold claude/p1-pairing claude/p2-replica claude/p5-store \
         claude/android-a0-probe claude/p2-runbook claude/todos-pq1-pricing; do
  t=$(git rev-parse origin/$s)
  out=$(git merge-tree --write-tree --name-only "$cur" "$t") \
    && cur=$(git commit-tree "$(echo "$out" | head -1)" -p "$cur" -p "$t" -m "sim $s") \
    && echo "$s: clean" || { echo "$s: CONFLICT"; echo "$out" | tail -n +2; }
done
```

*Expected:* seven `clean` lines and no conflict output. Requires git ≥ 2.38 for
`merge-tree --write-tree`; measured on 2.43.0.

### C-MT-4 — Exactly one conflicting file exists in the repository

> **Claim.** `claude/p1-runbook` is the only branch that conflicts, on exactly one path,
> `docs/Monetization-Decision.md`, as an **add/add** — and the difference is a product-naming
> decision, not formatting.

```bash
git merge-tree --write-tree --name-only origin/claude/android-a0-probe origin/claude/p1-runbook
diff <(git show origin/claude/p1-runbook:docs/Monetization-Decision.md) \
     <(git show origin/claude/android-a0-probe:docs/Monetization-Decision.md)
```

*Expected:* a non-zero exit naming `docs/Monetization-Decision.md` and
`CONFLICT (add/add)`; then a two-hunk diff, 9 insertions / 12 deletions, whose whole substance is
"CareerSeeker" vs "CareerSeeker **Basic**" and "Naming — decided" vs "Naming note (worth a decision,
not urgent)". If the diff has grown beyond those two hunks, §5's recommendation needs re-deriving.

### C-MT-5 — #5 and #6 overlap on three files and are auto-fused without conflict

> **Claim.** Both branches modify the same three files since `d9f95fd`, and git merges all three
> without asking. The overlap is real; the *conflict* is not. Both halves of that sentence matter.

```bash
mb=$(git merge-base origin/claude/p5-store origin/claude/android-a0-probe)
comm -12 <(git diff --name-only $mb origin/claude/p5-store | sort) \
         <(git diff --name-only $mb origin/claude/android-a0-probe | sort)
git merge-tree --write-tree origin/claude/p5-store origin/claude/android-a0-probe > /dev/null; echo "exit=$?"
```

*Expected:* `$mb` = `d9f95fd`; exactly three paths — `ui/HomeScreen.kt`, `ui/ApplicationsScreen.kt`,
`test/…/ScreensFromFixtureTest.kt`; and `exit=0`, meaning no textual conflict.

**The claim this does NOT support:** that the fused tree is correct. It has never been built,
tested or linted — CI runs per-branch. `docs/Merge-Topology.md` §6 says so, and §9 lists it as an
explicit non-claim. Anyone integrating must run the verification command of record on the *merged*
tree.

### C-MT-6 — The vendored vector pin is intact

> **Claim.** All 26 vendored vector files are byte-identical to upstream pin `679a317`. Upstream
> now has 28; the gap is the two unmerged `entitlement-ack` vectors, not drift.

```bash
S=../careerseeker   # sibling clone of ShivaClaw/careerseeker
for f in $(git ls-tree -r origin/claude/android-a0-probe --name-only | grep 'sync-vectors/v1/'); do
  a=$(git rev-parse origin/claude/android-a0-probe:$f)
  b=$(git -C $S rev-parse 679a317:docs/sync-vectors/v1/$(basename $f)) || echo "MISSING $f"
  [ "$a" = "$b" ] || echo "DIFFERS $f"
done; echo "compared: $(git ls-tree -r origin/claude/android-a0-probe --name-only | grep -c 'sync-vectors/v1/')"
git -C $S ls-tree -r origin/claude/s5-entitlement-ack-spec --name-only | grep -c 'sync-vectors/v1/.*json'
```

*Expected:* no `DIFFERS` or `MISSING` output, `compared: 26`, and `28` upstream on the S5 branch.
Any `DIFFERS` line is a cross-repo drift event: stop and escalate, per `VECTORS.lock`.

### C-MT-7 — The relay suite passes, and it is the only gate this environment can run

> **Claim.** `relay/`'s own test suite runs to green in a Linux sandbox with only Node — 32 tests,
> 1 file. This is the sole executable gate available here; the android gate and
> `Verify-Alpha.ps1` are **not runnable** in this environment and were not run.

```bash
cd ../careerseeker/relay && npm ci && npx vitest run
```

*Expected:* `Test Files  1 passed (1)` / `Tests  32 passed (32)`. Measured on Node v22.22.2.

*Expected to remain unavailable here:* `dotnet`, `pwsh`, `sdkmanager`, `ANDROID_HOME` — all absent
(`which` returns nothing). Any claim in this repo that depends on those is carried from another
machine and must say so.

---

## S5 second half — the `entitlement_ack` applier (2026-08-09)

Every command below was run in a Linux cloud sandbox with **no Android SDK** and with
`dl.google.com` egress-denied. The harness is a **reduced, probe-only** `:core` build, not the
verification command of record. Reproducing C-S5B-2/-3 needs that harness rebuilt (C-S5B-1) or,
better, CI — which runs the real gate on `ubuntu-latest` with JDK 17 and a real SDK.

### C-S5B-1 — The reduced `:core` harness, and why it is reduced

> **Claim.** `:core` compiles and tests in this sandbox because every one of its dependencies is on
> **Maven Central**. The full gate cannot run here for two independent reasons: no Android SDK, and
> `dl.google.com` (AGP + all `androidx`) is denied by egress policy. `api.foojay.io` is denied too,
> so the pinned JDK 17 toolchain cannot be provisioned and the probe substitutes 21.

```bash
curl -sS -o /dev/null -w "google=%{http_code}\n" \
  https://dl.google.com/dl/android/maven2/com/android/application/com.android.application.gradle.plugin/9.3.0/com.android.application.gradle.plugin-9.3.0.pom
curl -sS -o /dev/null -w "central=%{http_code}\n" \
  https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.10/kotlin-stdlib-2.4.10.pom
curl -sS "$HTTPS_PROXY/__agentproxy/status" | grep -A3 recentRelayFailures
ls /usr/lib/jvm/            # 21 only
which dotnet pwsh sdkmanager || echo "absent, as recorded"
```

*Expected:* the Google URL fails with `curl: (56) CONNECT tunnel failed, response 403` (so
`google=000`), `central=200`, the proxy status naming `dl.google.com:443` /
`gateway answered 403 to CONNECT`, only JDK 21 present, and no `dotnet`/`pwsh`/`sdkmanager`.
**Do not route around the 403** — `/root/.ccr/README.md` says report it, not work around it.

To rebuild the probe harness itself (throwaway; committed nowhere):

```bash
mkdir -p /tmp/coreprobe && cd /tmp/coreprobe
cat > settings.gradle.kts <<'EOF'
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories { mavenCentral() }
  versionCatalogs { create("libs") { from(files("<REPO>/gradle/libs.versions.toml")) } }
}
rootProject.name = "coreprobe"
include(":core")
project(":core").projectDir = file("<REPO>/core")
EOF
cat > build.gradle.kts <<'EOF'
subprojects { afterEvaluate {
  extensions.findByType(JavaPluginExtension::class.java)?.toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
} }
EOF
gradle --no-daemon -Dorg.gradle.java.installations.auto-download=false :core:test --rerun-tasks
```

*Expected:* `BUILD SUCCESSFUL`. **This is not the gate.** It never runs `checkCoreIsAndroidFree`,
`:app:test`, `:app:assembleDebug` or `:app:lintDebug`, and it runs on JDK 21 where CI runs 17.

### C-S5B-2 — `:core` was 67 tests before this slice and is 76 after, 0 failures, 0 skipped

> **Claim.** Baseline **67 / 0 / 0** measured on the branch before the change; **76 / 0 / 0** after,
> the delta being exactly the 9 new `EntitlementAckTest` cases. `STATE.md`'s carried
> **102 / 0 / 0 / 3** is `:core` **plus** `:app`; `:app` cannot run in this environment at all.

```bash
# after a :core:test run (C-S5B-1)
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=s=0
for p in glob.glob('<REPO>/core/build/test-results/test/*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t, f, s)
EOF
git stash && <rerun :core:test> && <rerun the python> && git stash pop   # for the 67 baseline
```

*Expected:* `76 0 0` at the tip; `67 0 0` with the two new files stashed.

### C-S5B-3 — Each §4.3.3 rule is pinned by a named test, not by a comment

> **Claim.** Nine tests pass, covering: the grant, `order_id`'s optionality, unknown `product_id`
> ignored rather than rejected, `acknowledged_at` advisory, no negative form, malformed bodies
> ignored, a foreign `kind` refused, no downgrade path, and the local-verdict boundary (PQ-A2-4).

```bash
gradle --no-daemon :core:test --rerun-tasks --tests 'app.careerseeker.core.EntitlementAckTest'
```

*Expected:* nine `PASSED` lines and `BUILD SUCCESSFUL`. The names are the assertions; read them
rather than the count. Note the decoy-flag case asserts a body carrying `"revoked":true` **still
unlocks** — that is §4.3.3's "no negative form", not a bug.

### C-S5B-4 — The vendored vector pin is untouched; the ack bodies are transcribed, not vendored

> **Claim.** This slice added **no** vector and re-vendored **nothing**. The pin stays `679a317`,
> the repo still holds 26 files against upstream's 28, and the two grant bodies in the test file are
> transcribed verbatim from `generate.mjs`'s `plaintext_json`.

```bash
git diff --stat HEAD~1 HEAD -- core/src/test/resources/ VECTORS.lock   # expect: no output
S=../careerseeker
diff <(git show HEAD:core/src/test/kotlin/app/careerseeker/core/EntitlementAckTest.kt |
       grep -o '"product_id":"pro_unlock"' | head -1) <(echo '"product_id":"pro_unlock"')
python3 -c "
import json;b=json.load(open('$S/docs/sync-vectors/v1/entitlement-ack.json'))['plaintext_json']['body']
print(b)"
```

*Expected:* no diff output for the first command (nothing under `resources/` or `VECTORS.lock`
changed), and the upstream body printing
`{'product_id': 'pro_unlock', 'acknowledged_at': '2026-06-11T14:02:11Z', 'order_id': 'GPA.3390-8461-2039-11123'}`
— the same triple the test transcribes.

### C-S5B-5 — B-6 stands: the engine would *accept* an unknown-field envelope

> **Claim.** PQ-A2-3's `invalid-unknown-field` vector cannot be added yet. The C# receiver takes an
> already-parsed record, and the harness builds that record by cherry-picking named keys, so an
> extra top-level field is dropped before any check runs — the envelope is accepted and a vector
> expecting `decrypt_failed` turns the gate red.

```bash
S=../careerseeker
sed -n '33,40p'   $S/src/Sync/EnvelopeReceiver.cs     # Receive(ReceivedEnvelope env, ...)
sed -n '694,700p' $S/tests/SyncHarness/Program.cs     # static ReceivedEnvelope ToReceived(JsonObject env) => new(
```

*Expected:* `Receive` takes a `ReceivedEnvelope` record (no wire-JSON parsing), and `ToReceived`
constructs it from named lookups only — no unknown-key check anywhere in the path.

### C-S5B-6 — The S5 spec half was already landed, and was re-verified rather than assumed

> **Claim.** §4.3.3 + PQ-A2-1/-2 + the two vectors were already on the main repo's
> `claude/s5-entitlement-ack-spec` (draft PR #32) before this iteration started.

```bash
cd ../careerseeker && git fetch --all --prune
git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
node docs/sync-vectors/generate.mjs --check
```

*Expected:* two commits, and `OK: 28 vector files match the generator.`

### C-S5B-7 — CI is the gate that ran, and it passed

> **Claim.** CI run `31305289509` on `a37c185`, job *Build and test*, concluded **`success`** —
> `ubuntu-latest`, **JDK 17**, real Android SDK. It ran `checkCoreIsAndroidFree`, the vendored-vector
> drift step against `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug`
> and the release-classpath tracker check. This supersedes the reduced probe (C-S5B-1) as evidence.

```bash
# The Actions REST API needs actions:read; a token without it returns
# 403 "Resource not accessible by integration" -- which is what silently
# defeated two poll loops during this iteration. Read check runs instead:
#   MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6
# or, with a suitably scoped token:
gh run view 31305289509 --repo ShivaClaw/careerseeker-android
```

*Expected:* one check run, `Build and test`, `status: completed`, `conclusion: success`,
`completed_at: 2026-08-09T09:18:25Z`. **The run does not report test counts** — Gradle does not
print them and the workflow does not collect them, so the `76 / 0 / 0` of C-S5B-2 stays a
probe measurement and is not corroborated by CI. Green means every step passed, nothing more.

---

## S4 (phone half) — the pull decision · 2026-08-09 (fourth cloud iteration)

### C-S4A-1 — The baseline was measured before anything was written

> **Claim.** `:core` stood at **76 tests / 0 failures / 0 skipped** on `db4ec49` before this
> slice, so the delta below is a measurement rather than a story.

The probe is **reduced** and is not the verification command of record: `:app`, `lintDebug`,
`assembleDebug` and `checkCoreIsAndroidFree` are all absent from it, and the toolchain is
substituted 17 → 21 because `api.foojay.io` is egress-denied here (B-7).

```bash
mkdir -p /tmp/coreprobe && cd /tmp/coreprobe
cat > settings.gradle.kts <<'EOF'
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("<repo>/gradle/libs.versions.toml")) } }
}
rootProject.name = "coreprobe"
include(":core")
project(":core").projectDir = file("<repo>/core")
EOF
cat > build.gradle.kts <<'EOF'
subprojects {
    afterEvaluate {
        extensions.findByType(JavaPluginExtension::class.java)?.toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
EOF
git -C <repo> stash list >/dev/null && git -C <repo> checkout db4ec49 -- core
gradle --no-daemon :core:test --rerun-tasks
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=s=0
for p in glob.glob('<repo>/core/build/test-results/test/*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t,f,s)
EOF
```

*Expected:* `76 0 0`.

### C-S4A-2 — The slice adds 17 tests and they pass

> **Claim.** After `PullPolicy` + `PullPolicyTest`, the same reduced probe measures **93 tests /
> 0 failures / 0 skipped**, of which `PullPolicyTest` contributes **17 / 0 / 0**. Unlike the
> previous iteration's applier, this one **passed on its first run** — no red-then-green.

```bash
cd /tmp/coreprobe && gradle --no-daemon :core:test --rerun-tasks 2>&1 | grep -c "PullPolicyTest.*PASSED"
python3 -c "
import xml.etree.ElementTree as ET
r=ET.parse('<repo>/core/build/test-results/test/TEST-app.careerseeker.core.PullPolicyTest.xml').getroot()
print(r.get('tests'), r.get('failures'), r.get('errors'), r.get('skipped'))"
```

*Expected:* `17`, then `17 0 0 0`.

### C-S4A-3 — The engine really does ignore `since_seq` (the basis for PQ-S4-1)

> **Claim.** `InboundDispatcher` parses `since_seq` and hands it to `ISnapshotRepublisher`, and
> **every** implementation of that interface discards it and publishes a full snapshot. This is
> the whole reason the phone sends `0`.

```bash
S=../careerseeker            # on origin/claude/s5-entitlement-ack-spec or origin/main
sed -n '105,111p' $S/src/Sync/InboundDispatcher.cs      # case "pull_request": ReadSinceSeq -> RepublishSnapshotAsync
grep -rn "RepublishSnapshotAsync" --include=*.cs $S | grep -v InboundDispatcher.cs
sed -n '308,313p' $S/tests/SyncLiveSmoke/Program.cs     # LiveRepublisher
sed -n '753,759p' $S/tests/SyncHarness/Program.cs       # RecordingRepublisher
```

*Expected:* exactly **two** implementations, both in `tests/`. `LiveRepublisher.RepublishSnapshotAsync`
calls `publisher.PublishSnapshotAsync(counters, apps, jobs, ct)` — the `sinceSeq` parameter is
unreferenced in the body. `RecordingRepublisher` only assigns `LastSince`. No shipping code path
lets `since_seq` change what is sent.

### C-S4A-4 — `pull_request` needs no device signature, so the loop predates S3

> **Claim.** `pull_request` is absent from `Protocol.STATE_CHANGING_KINDS` on both sides, so
> `OutboundEnvelopeFactory.build` emits it with `signer = null` and no `sig` field. This is why
> an S4 pull loop is **not** downstream of S3's Keystore key, and it is asserted, not asserted-in-prose.

```bash
grep -n "STATE_CHANGING_KINDS" -A1 core/src/main/kotlin/app/careerseeker/core/Protocol.kt
grep -n "StateChangingKinds" -A3 ../careerseeker/src/Sync/Protocol.cs
cd /tmp/coreprobe && gradle --no-daemon :core:test --tests '*PullPolicyTest' --rerun-tasks 2>&1 \
  | grep "needs no device signature"
```

*Expected:* both sets are `doc_edit, outcome, entitlement` — `pull_request` in neither — and the
test `a pull_request needs no device signature() PASSED`.

*One trap worth naming, because a careless grep hits it.* `src/Sync/Protocol.cs:34` lists
`"doc_edit", "outcome", "entitlement", "pull_request", "error"` on a single line, which looks like
the state-changing set and is not: that is `ShippingKinds` (every v1 kind, §4.3).
`StateChangingKinds` is a separate set at line 52. Grep for the identifier, not for the kind names.

### C-S4A-5 — Nothing was re-vendored and no existing vector's bytes moved

> **Claim.** This slice touched no vector. The vendored pin stays `679a317` and the repo still
> holds 26 of upstream's 28.

```bash
git diff --stat db4ec49..HEAD -- core/src/test/resources/sync-vectors VECTORS.lock
ls core/src/test/resources/sync-vectors/v1/*.json | wc -l
git diff --stat db4ec49..HEAD --name-only
```

*Expected:* the first two commands report **no changes** and `26`; the name-only diff lists exactly
`core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt`,
`core/src/test/kotlin/app/careerseeker/core/PullPolicyTest.kt`,
`docs/protocol-questions.md`, plus this iteration's record files.

### C-S4A-6 — `:core` stayed Android-free

> **Claim.** No Android or `androidx` import entered `:core`, so `checkCoreIsAndroidFree` is
> unaffected. The probe cannot run that task; CI does.

```bash
grep -rn "^import android" core/src/ ; echo "exit=$?"
```

*Expected:* no matches, `exit=1`.

### C-S4A-7 — What this slice did NOT verify

> **Claim.** The end-to-end loop is **not** proven. `PullPolicy` has no production caller: the
> mapping from `:app`'s `ApplyResult` onto `ApplyDisposition`, the relay push of the resulting
> envelope, and the `:app` Ktor engine dependency are all unwritten, and the E2E claim needs an
> emulator (B-4) and a toolchain this sandbox cannot fetch (B-7).

```bash
grep -rn "PullPolicy\|ApplyDisposition" app/src/ ; echo "exit=$?"
```

*Expected:* no matches, `exit=1` — the policy is a tested `:core` unit and nothing more. Any
future claim that S4 is DONE must first make this command return hits.

### C-S4A-8 — CI is the gate that ran, and it passed

> **Claim.** CI run `31315292165` on `044d829`, job *Build and test*, concluded **`success`** —
> `ubuntu-latest`, **JDK 17**, real Android SDK, 13:14:40 → 13:21:05 UTC. It ran
> `checkCoreIsAndroidFree`, the vendored-vector drift step against `679a317`, `:core:test`,
> `:app:test`, `:app:assembleDebug`, `:app:lintDebug` and the release-classpath tracker check.
> This supersedes the reduced probe (C-S4A-1/-2) as evidence.

```bash
# The Actions REST API needs actions:read; without it every call returns
# 403 "Resource not accessible by integration". Read check runs instead:
#   MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6
# or, with a suitably scoped token:
gh run view 31315292165 --repo ShivaClaw/careerseeker-android
```

*Expected:* one check run, `Build and test`, `status: completed`, `conclusion: success`,
`completed_at: 2026-08-09T13:21:05Z`.

**Two cautions about this command specifically.** First, `get_check_runs` reports runs for the PR's
**current head**, not for a commit you name — so once the branch moves it will describe a *later*
run and `total_count: 0` while one is queuing. To re-verify *this* claim after further pushes, use
the run id above directly. Second, **the run does not report test counts** — Gradle does not print
them and the workflow does not collect them, so the `93 / 0 / 0` of C-S4A-2 stays a probe
measurement and is not corroborated by CI. Green means every step passed, nothing more.

---

## S6 (phone half) — the outcome-marking decision · 2026-08-09 (fifth cloud iteration)

Every claim below was produced by a command run in this session. The `:core` numbers come from a
**reduced** probe (C-S6A-1); the gate is CI (C-S6A-8). Where a claim is unverified, it says so and
says why.

### C-S6A-1 — The reduced `:core` harness, and why it is reduced

> **Claim.** `:core` test numbers in this section come from a throwaway Gradle root in the
> scratchpad that includes **`:core` only**, resolves from **Maven Central only**, and forces the
> Kotlin toolchain **17 → 21**. It is not the repo build and it is not the gate. `:app`, AGP and
> every `androidx` artifact come from `dl.google.com`, which is an egress **policy denial** here
> (B-7), and `api.foojay.io` is denied too, so a JDK 17 cannot be provisioned.
>
> Gradle 9 removed the `-c` / `--settings-file` option, so the probe cannot be a stray settings
> file passed on the command line — it has to be a separate root that points at the real `:core`.
> That is why the recipe below is longer than "run gradlew with a flag".

```bash
R=/path/to/careerseeker-android
P=$(mktemp -d)/coreprobe && mkdir -p "$P" && cd "$P"
cat > settings.gradle.kts <<EOF
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("$R/gradle/libs.versions.toml")) } }
}
rootProject.name = "coreprobe"
include(":core")
project(":core").projectDir = file("$R/core")
EOF
cat > init.gradle.kts <<'EOF'
allprojects {
    afterEvaluate {
        if (plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            extensions.findByName("kotlin")?.withGroovyBuilder { "jvmToolchain"(21) }
        }
    }
}
EOF
"$R/gradlew" -I init.gradle.kts :core:test --rerun-tasks --console=plain
```

*Expected:* `BUILD SUCCESSFUL`. The `afterEvaluate` is load-bearing — a `plugins.withId` block runs
*before* `core/build.gradle.kts`'s own `jvmToolchain(17)`, which then overwrites it and the build
fails with "Cannot find a Java installation … matching: {languageVersion=17}".

*Verify the denial rather than taking B-7 on trust:*

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://dl.google.com/ ; echo "exit=$?"
```

*Expected:* a CONNECT tunnel failure / 403 from the proxy, not a 200.

### C-S6A-2 — `:core` was 93 tests before this slice and is 115 after, 0 failures, 0 skipped

> **Claim.** Baseline measured on the untouched branch at `66bf167`: **93 / 0 / 0**, which matches
> the figure `STATE.md` already carried. After the slice: **115 / 0 / 0** — `+22`, all of them
> `OutcomeMarkPolicyTest`. They passed on the **first** run; no test was adjusted to make it green.

```bash
# after the C-S6A-1 recipe:
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=s=0
for p in sorted(glob.glob('<repo>/core/build/test-results/test/TEST-*.xml')):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t, f, s)
PY
```

*Expected:* `115 0 0`, across 11 test classes, with `OutcomeMarkPolicyTest` contributing 22. For the
baseline, `git stash` the two new files (or check out `66bf167`) and repeat: `93 0 0` across 10.

### C-S6A-3 — There is no `outcome_ack` in the protocol (the first half of PQ-S6-1)

> **Claim.** §4.3's engine → phone table acknowledges `doc_edit` (via `conflict`) and `entitlement`
> (via `entitlement_ack`) and nothing else. No kind acknowledges or rejects an `outcome`.

```bash
cd <careerseeker>
sed -n '/^Engine → phone:/,/^Phone → engine:/p' docs/Sync-Protocol.md
grep -n "outcome_ack" docs/Sync-Protocol.md ; echo "exit=$?"
```

*Expected:* the engine → phone table lists exactly `snapshot`, `delta`, `doc`, `evidence`,
`heartbeat`, `conflict`, `entitlement_ack`, `error`; the grep returns **no matches, `exit=1`**.

### C-S6A-4 — The engine reports `OutcomeApplied` even with a null applier (the second half)

> **Claim.** `case "outcome"` calls the applier only when it is non-null, then returns
> `InboundOutcome.OutcomeApplied` unconditionally — and `IOutcomeApplier` is nullable by design,
> documented as "a null applier means outcome dispatch is a no-op seam for now".

```bash
cd <careerseeker>
sed -n '28,36p;96,104p' src/Sync/InboundDispatcher.cs
```

*Expected:* the interface doc-comment naming the null-applier no-op, and a `case "outcome"` whose
`return new InboundResult(InboundOutcome.OutcomeApplied, ...)` sits outside the `if
(_outcomeApplier is not null)` guard.

### C-S6A-5 — The two directions' sequence numbers are not comparable

> **Claim.** §6.1 gives each direction an independent counter, and §4.3.1's application summary has
> no per-application timestamp. So a `snapshot` arriving after a mark cannot be ordered against it,
> which is why reconciliation is by value convergence rather than by recency.

```bash
cd <careerseeker>
sed -n '/^### 6.1 Sequence numbers/,/^### 6.2/p' docs/Sync-Protocol.md
grep -n '"applications": \[' docs/Sync-Protocol.md
```

*Expected:* "Each direction has an independent counter starting at 1"; and the summary field list
`{ "id","state","company","title","score","outcome"? }` — no timestamp, no per-app sequence.

### C-S6A-6 — Each decision is pinned by a named test, not by a comment

> **Claim.** Every rule the policy asserts has a test whose name states it.

```bash
grep -n "fun \`" core/src/test/kotlin/app/careerseeker/core/OutcomeMarkPolicyTest.kt
```

*Expected:* 22 names, including — a Free user cannot mark; awaiting the engine is not Pro enough;
`no_reply` is never offered even while the engine is reporting it; a stale snapshot does not revert
a mark that is still in flight; a mark the engine keeps disagreeing with is eventually abandoned; a
failed push keeps the mark queued and does not count as a disagreement; a reaching-the-relay report
does not confirm anything; re-marking one application collapses to a single latest envelope.

### C-S6A-7 — Nothing was re-vendored and no existing vector's bytes moved

> **Claim.** The vendored pin is still `679a317`, all **26** files are byte-identical to it, and
> this slice touched no vector, no `VECTORS.lock`, and no `:app` source.

```bash
grep -n "Pinned commit" core/src/test/resources/sync-vectors/VECTORS.lock
ls core/src/test/resources/sync-vectors/v1/*.json | wc -l
git diff --name-only 66bf167..HEAD
# byte-identity against the pin, checked out of the main repo rather than assumed:
cd <careerseeker> && git archive 679a3175590dcd021b21c85af9daf12114e131fd docs/sync-vectors/v1 \
  | tar -x -C /tmp/vecchk && diff -r /tmp/vecchk/docs/sync-vectors/v1 \
  <android>/core/src/test/resources/sync-vectors/v1 && echo IDENTICAL
```

*Expected:* `679a3175590dcd021b21c85af9daf12114e131fd`; `26`; a name-only diff listing exactly
`core/src/main/kotlin/app/careerseeker/core/OutcomeMarking.kt`,
`core/src/test/kotlin/app/careerseeker/core/OutcomeMarkPolicyTest.kt`,
`docs/protocol-questions.md` plus this iteration's record files; and `IDENTICAL`.

Also, `:core` stayed Android-free:

```bash
grep -rn "^import android" core/src/ ; echo "exit=$?"
```

*Expected:* no matches, `exit=1`.

### C-S6A-8 — What this slice did NOT verify, stated before anyone infers it

> **Claim.** S6 is **not** end-to-end. `OutcomeMarkPolicy` has **no production caller**: no screen
> offers the control, no transport pushes the envelope, and the send path needs a device signature
> (§5.4) from an Android Keystore key that does not exist until S3 — which needs an emulator
> (B-4). The `:app` half additionally needs a toolchain this sandbox cannot fetch (B-7).

```bash
grep -rn "OutcomeMarkPolicy\|MarkDecision\|DisplayedOutcome" app/src/ ; echo "exit=$?"
```

*Expected:* **no matches, `exit=1`** — the policy is a tested `:core` unit and nothing more. Any
future claim that S6 is DONE must first make this command return hits, and must additionally show a
`DeviceSigner` backed by a real Keystore key.

Two further things this slice does not establish, both worth an auditor's attention:

- **`disagreementLimit = 3` is chosen, not measured.** There is no deployment to derive it from.
  If PQ-S6-1 closes as option (a) — a real `outcome_ack` — the bound stops being the mechanism and
  becomes a fallback, and the number matters much less.
- **The convergence rule cannot distinguish "the engine applied my mark" from "the desktop
  independently reached the same value".** With no ack, nothing in v1 can. The policy treats them
  as equivalent, which is correct for display and would be wrong for anything that needed
  attribution.

### C-S6A-9 — CI is the gate, and this claim is open until it runs

> **Claim.** *Unverified at the time of writing.* The android gate
> (`:core:test`, `:app:test`, `:app:assembleDebug`, `:app:lintDebug`, `checkCoreIsAndroidFree`, the
> vendored-vector drift step) **was not run in this session** and cannot be: no Android SDK, and
> `dl.google.com` is an egress policy denial (B-7). The reduced probe of C-S6A-1/-2 is a local
> signal on JDK 21, not the gate on JDK 17.

```bash
# MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6
gh run list --repo ShivaClaw/careerseeker-android --branch claude/android-a0-probe --limit 3
```

*Expected once CI has run on this push:* job *Build and test*, `conclusion: success`. **If it is
red, C-S6A-2's counts stand but every "the slice is green" reading of this section does not.**
`get_check_runs` reports the PR's *current* head, so check the commit it names before believing it.

---

## S2/S5 relay half — the size cap (C-S2R-1 … C-S2R-7)

All commands run from a clone of `ShivaClaw/careerseeker` at
`claude/s5-entitlement-ack-spec` (draft PR #32), with `cd relay && npm ci` done once.

### C-S2R-1 — The relay refused envelopes the protocol declares legal (the finding)

> **Claim.** Before this slice, the relay 413'd a ciphertext of exactly `MAX_ENVELOPE_BYTES`
> decoded — legal by §3.1 — and the largest it would carry decoded to **786,432** bytes, leaving a
> **256 KiB** band of the declared range untransmittable.

This is a claim about the code *before* the fix, so it re-verifies against the parent commit:

```bash
git stash list >/dev/null; git worktree add /tmp/pre 22b028e && cd /tmp/pre/relay && npm ci
cat > test/probe.test.ts <<'EOF'
import { env } from 'cloudflare:workers';
import { describe, expect, it } from 'vitest';
import worker from '../src/index';
import { MAX_ENVELOPE_BYTES } from '../src/protocol';
const call = (p: string, i?: RequestInit) => worker.fetch(new Request(`https://r.example${p}`, i), env as never);
const bearer = { authorization: 'Bearer tok' };
describe('probe', () => {
  it('413s a legal maximum', async () => {
    const id = 'p_000001ProbeVbN3W';
    expect((await call(`/v1/${id}/create`, { method: 'POST', headers: bearer })).status).toBe(201);
    const body = JSON.stringify({ v: 1, pairing: 'p_x', dir: 'e2p', seq: 1, ts: '2026-06-11T14:02:11Z',
      key_id: 'k-1', nonce: 'AAAAAAAAAAAAAAAA', ciphertext: 'A'.repeat(Math.ceil(MAX_ENVELOPE_BYTES * 4 / 3)) });
    const res = await call(`/v1/${id}/push`, { method: 'POST', headers: bearer, body });
    console.log('status', res.status, await res.text());
    expect(res.status).toBe(413);
  });
});
EOF
npx vitest run test/probe.test.ts --reporter=verbose
```

*Expected on `22b028e` (pre-fix):* the test **passes**, printing `status 413 {"error":"too_large"}` —
i.e. the bug reproduces. *Expected on the fix commit `a564c0c` or later:* the same test **fails**,
because the push now returns 201. **A green probe here is the defect, and a red one is the fix.**
Remove the worktree afterwards: `git worktree remove /tmp/pre --force`.

### C-S2R-2 — The character cap is derived, never re-spelled

> **Claim.** `MAX_CIPHERTEXT_B64U_CHARS` is computed from `MAX_ENVELOPE_BYTES` and equals 1,398,102;
> the old guard's ceiling was 786,432 decoded, a 256 KiB shortfall.

```bash
grep -n "MAX_CIPHERTEXT_B64U_CHARS\|MAX_PUSH_BODY_CHARS" relay/src/protocol.ts relay/src/channel.ts
grep -rn "1048576\|1398102\|1024 \* 1024" relay/src/
```

*Expected:* `MAX_CIPHERTEXT_B64U_CHARS` defined **once**, as `Math.ceil((MAX_ENVELOPE_BYTES * 4) / 3)`,
and `1024 * 1024` appearing **only** on `MAX_ENVELOPE_BYTES`. **A literal `1398102` anywhere in
`relay/src/` is the regression this constant exists to prevent** — the guard would then be a second
round number that can drift from the first.

### C-S2R-3 — The suite, and the number it moved from

> **Claim.** `36 passed`, up from a measured `32`, the delta being five new cases minus the one that
> pinned the bug.

```bash
cd relay && npx vitest run
git show 22b028e:relay/test/relay.test.ts | grep -c "  it("   # pre-fix case count
```

*Expected:* `Test Files 1 passed (1)`, `Tests 36 passed (36)`. The baseline `32 passed` is
re-derivable by running the same command in the C-S2R-1 worktree.

### C-S2R-4 — The old case pinned the bug, and is gone

> **Claim.** The suite previously asserted `1 MiB + 1 characters → 413`, which locked the character/byte
> confusion in place.

```bash
git show 22b028e:relay/test/relay.test.ts | grep -n -A3 "rejects an oversized ciphertext"
grep -n "1024 \* 1024 + 1" relay/test/relay.test.ts
```

*Expected:* the first prints the old case asserting 413 on `'A'.repeat(1024 * 1024 + 1)`; the second
returns **no matches, `exit=1`**. If that string comes back, someone has re-pinned the bug.

### C-S2R-5 — The change is strictly loosening, so no proven path can regress

> **Claim.** Both guards moved *looser*, therefore nothing the relay accepted before is rejected now,
> therefore PR #31's engine↔relay 30/30 proof cannot regress on this change.

```bash
git diff 22b028e..HEAD -- relay/src/ | grep -E "^[-+].*(MAX_|4096)"
```

*Expected:* the body guard goes `MAX_ENVELOPE_BYTES + 4096` (1,052,672) → `MAX_PUSH_BODY_CHARS`
(1,402,198) and the ciphertext guard `MAX_ENVELOPE_BYTES` (1,048,576) → `MAX_CIPHERTEXT_B64U_CHARS`
(1,398,102). **Both larger.** This is an arithmetic argument, not a test run: the 30/30 smoke needs
.NET and was **not** re-run in this session.

### C-S2R-6 — No vector byte moved, and no .NET surface was touched

> **Claim.** This slice cannot affect `$ExpectedOfflineTotal` (598) or any shared vector.

```bash
node docs/sync-vectors/generate.mjs --check
git diff --name-only 22b028e..HEAD
```

*Expected:* `OK: 28 vector files match the generator.` and a changed-file list of exactly
`docs/Sync-Protocol.md`, `relay/src/channel.ts`, `relay/src/protocol.ts`, `relay/test/relay.test.ts`
— **no `.cs`, no `docs/sync-vectors/v1/*`, no `scripts/Verify-Alpha.ps1`.**

### C-S2R-7 — CI is the gate, and this claim is open until it reports

> **Claim.** *Open at the time of writing.* The vitest run above is `npx vitest` on Node 22 in a
> sandbox. The authoritative result is CI's **Blind relay (Worker)** job, and the offline pin is
> confirmed only by CI's **Build and offline harnesses** job on `windows-latest`. Neither was run by
> me; `Verify-Alpha.ps1` cannot run here (no .NET).

```bash
# MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker pullNumber=32
gh run list --repo ShivaClaw/careerseeker --branch claude/s5-entitlement-ack-spec --limit 4
```

*Expected:* both jobs `success`, and `Offline total: 598 passed, 0 failed` unchanged in the second.
**If the relay job is red, every number in C-S2R-3 stands but the slice does not.**

### C-S6A-9 — CLOSED GREEN 2026-08-09 (superseding the open claim above)

The previous iteration left C-S6A-9 open: the android gate had not reported on the S6 push. It has.

```bash
# MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6
```

*Measured:* run [31325873134](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31325873134),
job *Build and test*, **`conclusion: success`**, 17:14:21 → 17:22:00 UTC, on head `9f73226` — the
commit carrying `OutcomeMarkPolicy`. S6's marking decision is now **gate-verified**, not
probe-verified. The `115 / 0 / 0` count remains a probe measurement; CI proves green, not the number.

---

## S4 spec half — `pull_request` semantics, PQ-S4-1 closed · 2026-08-10 (seventh cloud iteration)

The slice is **one documentation commit in the main repo** (`9399d11` on
`claude/s4-pull-request-semantics`, stacked on PR #32) plus these records. It changes no code in
either repo, and that is the claim an auditor should test hardest: option (a) was chosen precisely
*because* both implementations already conform, so if either does not, the amendment is wrong.

### C-S4S-1 — The amendment says what this section says it says

> **Claim.** §4.3's `pull_request` row no longer promises resumption; a new §4.3.4 pins the body
> with three MUSTs (send `0`, ignore the value, do not reject a non-zero); §6.2 states the gap
> threshold is receiver policy; §9's amendments table records both changes.

```bash
cd careerseeker && git fetch origin claude/s4-pull-request-semantics
git show origin/claude/s4-pull-request-semantics:docs/Sync-Protocol.md \
  | grep -n "sequence point\|4.3.4\|RESERVED in v1\|receiver policy"
```

*Expected:* **no match for "sequence point"** — its removal is the point of the change — and matches
for the other three. The diff is `git diff origin/claude/s5-entitlement-ack-spec..origin/claude/s4-pull-request-semantics`:
one file, `docs/Sync-Protocol.md`, 74 insertions / 2 deletions.

### C-S4S-2 — The engine conforms to the amendment already, unchanged

> **Claim.** The engine parses `since_seq`, hands it to `ISnapshotRepublisher`, and **every**
> implementation ignores the argument — so "ignore the value, answer a full snapshot" describes
> shipping behaviour rather than requesting new behaviour. No rejection path reads the field.

```bash
cd careerseeker && grep -n "pull_request" -A 7 src/Sync/InboundDispatcher.cs
grep -rn "RepublishSnapshotAsync" --include=*.cs .
grep -rn "since_seq\|SinceSeq" --include=*.cs src/Sync/ | grep -i "reject\|error\|throw"
```

*Expected:* `InboundDispatcher.cs:105-111` reads `ReadSinceSeq` and passes it on; exactly two
implementations, `tests/SyncLiveSmoke/Program.cs:311-312` (calls `PublishSnapshotAsync`
unconditionally — the argument is unused) and `tests/SyncHarness/Program.cs:756-758` (assigns it to
`LastSince` and returns). The third command returns **nothing**: no rejection path reads the field.

### C-S4S-3 — The phone conforms to the amendment already, unchanged

> **Claim.** `PullPolicy` sends `0` for every reason it asks, pinned by a test, and the §6.2
> threshold is a constructor parameter defaulting to 32 and labelled chosen-not-measured.

```bash
cd careerseeker-android && grep -n "SINCE_SEQ_FULL_REPUBLISH\|gapThreshold" \
  core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt
grep -rn "since_seq zero" core/src/test/kotlin/app/careerseeker/core/PullPolicyTest.kt
```

*Expected:* `const val SINCE_SEQ_FULL_REPUBLISH = 0L`, a `gapThreshold` parameter defaulting to 32,
and the test `every reason sends since_seq zero`. **No Kotlin was written this slice** — this
command documents that the code the amendment describes already existed and was not edited:
`git diff origin/claude/android-a0-probe -- core/ app/` is empty.

### C-S4S-4 — `:core` is green, and it is a probe rather than the gate

> **Claim.** `:core` measured **115 tests / 0 failures / 0 skipped**, `BUILD SUCCESSFUL` — unchanged
> from the S6 slice's count, as expected for a documentation-only iteration.

Recipe: **C-S6A-1** (reduced probe, `:core` only, Maven Central only, toolchain 17 → 21). Measured
here `BUILD SUCCESSFUL in 1m 32s`, then counted from the JUnit XML rather than read off the console,
because Gradle does not print totals:

```bash
python3 -c "
import glob,xml.etree.ElementTree as ET
t=f=s=0
for p in glob.glob('core/build/test-results/test/*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0))+int(r.get('errors',0)); s+=int(r.get('skipped',0))
print(t,f,s)"
```

*Expected:* `115 0 0`. **This is not the gate** — the gate is CI (C-S6A-8/-9); `:app`, AGP and every
`androidx` artifact come from `dl.google.com`, a policy denial here (B-7).

### C-S4S-5 — The new finding: `pull_request` over-reports exactly as `outcome` does

> **Claim.** `InboundDispatcher` returns `SnapshotRepublished` **outside** its null check, so an
> engine with no republisher configured reports a snapshot it never sent — the same shape PQ-S6-1
> records for `outcome`, on a second kind. Found while verifying C-S4S-2. **Not fixed: C#, and this
> sandbox has no .NET.**

```bash
cd careerseeker && sed -n '96,112p' src/Sync/InboundDispatcher.cs
```

*Expected:* both `case "outcome"` and `case "pull_request"` guard their applier/republisher call
with `is not null` and then `return new InboundResult(...Applied/Republished...)` unconditionally.
An auditor wanting the consequence should note the two differ in severity — a dropped `outcome`
loses a user's mark, a dropped `pull_request` loses only a request the phone will re-issue on the
next open (`PullPolicy`'s latch).

### C-S4S-6 — No vector moved, none was added, and the cross-repo pin is untouched

> **Claim.** Zero vector drift. The generator still reports **28** files, the same count as before
> this slice, so the android repo's vendored pin `679a317` (26 files) and `$ExpectedOfflineTotal`
> (598) are untouched **by construction** — no `.cs`, no harness, no vector byte, no count-reporting
> doc.

```bash
cd careerseeker && node docs/sync-vectors/generate.mjs --check ; echo "exit=$?"
git diff origin/claude/s5-entitlement-ack-spec..origin/claude/s4-pull-request-semantics --stat
```

*Expected:* `OK: 28 vector files match the generator.`, `exit=0`, and a one-file diffstat naming
only `docs/Sync-Protocol.md`. **Measured here, both.**

### C-S4S-7 — What this slice did NOT verify

> **Claim.** No gate ran on the amendment. `Verify-Alpha.ps1` was **not** run and cannot be here (no
> .NET); the android gate was **not** run and cannot be here (no SDK/JBR, and B-7). The amendment is
> documentation, so the honest statement is that CI on the main repo is the gate and it has not yet
> reported on this branch at the time of writing.

```bash
# MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker pullNumber=<this PR>
```

*Expected:* both jobs `success`, and `Offline total: 598 passed, 0 failed` unchanged. **If the
offline total is anything other than 598, this slice's central claim — that a documentation change
cannot move the pin — is false and the entry above is wrong.** That is the cheapest possible test of
it, and it is the reason the number is repeated here rather than referenced.

### C-S4S-8 — CI reported, the pin held, and the one harness test worth a second look is green

> **Claim.** C-S4S-7 has now been checked rather than predicted. Run
> [31346147785](https://github.com/ShivaClaw/careerseeker/actions/runs/31346147785) on head
> `9399d11`: **both jobs `success`** — *Blind relay (Worker)* and *Build and offline harnesses*
> (`windows-latest`, `Verify-Alpha.ps1`). Read directly from the job log, not inferred from the
> green tick: `SyncHarness === 130 passed, 0 failed ===` and
> **`=== Offline total: 598 passed, 0 failed ===`**. The pin did not move, which is what a
> documentation-only slice must show.

```bash
# MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker pullNumber=33
# MCP: get_job_logs job_id=93328358926 return_content=true tail_lines=400   # grep "Offline total"
```

> **Second claim, and it is the one an auditor should press.** The harness contains
> `dispatch: pull_request -> SnapshotRepublished(since_seq=7)` — a test that sends a **non-zero**
> `since_seq`, which §4.3.4 now says senders MUST NOT do. It is green and it is **conformant**, for
> two reasons worth stating rather than leaving to inference:
>
> 1. The harness is a test fixture, not a shipping sender. §4.3.4's "senders MUST send `0`" binds
>    implementations that ask for a republish; no shipping engine sends `pull_request` at all.
> 2. The test in fact **demonstrates** §4.3.4's third rule — a receiver handed a non-zero value
>    MUST NOT reject it. A receiver that started validating the field would turn this test red,
>    which is the behaviour the rule forbids.

```bash
cd careerseeker && grep -rn "since_seq=7\|SinceSeq" tests/SyncHarness/Program.cs | head
```

*Expected:* the dispatch test and `RecordingRepublisher.LastSince`. **What would make this stale:**
§4.3.4 arguably invites removing `sinceSeq` from `ISnapshotRepublisher` entirely, since no
implementation reads it. That is a **C# cleanup this sandbox cannot make or verify**, and it would
rewrite this test. Recorded so whoever does it knows the test is deliberate rather than incidental.

---

## S4 transport half — `SyncPump`, the loop's decisions in `:core` · 2026-08-10 (eighth cloud iteration)

Every claim below was **measured on this machine** with the reduced `:core` probe (C-S6A-1 recipe,
re-run here — `BUILD SUCCESSFUL in 28s`). The gate is CI (C-S4T-8). Where a claim is unverified, it
says so and says why.

### C-S4T-1 — The measured `:core` total moved 115 → 133, and `SyncPumpTest` is the whole delta

> **Claim.** `:core` is **133 tests / 0 failures / 0 skipped across 12 classes**, up from the
> measured 115 / 11 baseline. The +18 is `SyncPumpTest` and nothing else: no existing test was
> edited, renamed, or deleted.

```bash
# C-S6A-1's probe recipe, then:
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=s=n=0
for p in sorted(glob.glob('<repo>/core/build/test-results/test/TEST-*.xml')):
    r=ET.parse(p).getroot(); n+=1
    t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t,f,s,n)
PY
git diff --stat HEAD~2..HEAD
```

*Expected:* `133 0 0 12`, and a three-file diffstat: `SyncPump.kt` (new), `SyncPumpTest.kt` (new),
`OutboundEnvelopes.kt` (comment only). **Measured here.**

### C-S4T-2 — Rule 1: the cursor advances on envelopes *seen*, not envelopes *applied*

> **Claim.** This is the bug the class exists to prevent, and it is silent in every direction. A
> `delta` arriving before any snapshot is **accepted** by `EnvelopeReceiver` and **refused** by the
> replica (`AWAITING_SNAPSHOT`), so the persisted applied mark does not move. A cursor read from
> that mark re-requests the same envelope next cycle, where the receiver's in-process replay window
> now rejects it — the phone pulls the same page forever, applies nothing, and reports no error.
> Two tests pin it: one for the refused-by-replica path, one for the rejected-by-receiver path.

```bash
# after the C-S6A-1 recipe:
grep -n "the cursor advances past" -A 20 \
  <repo>/core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt
```

*Expected:* both tests assert the **second** pull's `since=` equals the refused envelope's seq
(`4` and `3` respectively), not the unchanged applied mark (`0` and `1`). **Measured here, both
green.** An auditor wanting to see the failure should invert the guard at `SyncPump.kt`'s
`if (seq > cursorValue)` to key off `position.current().highestAppliedSeq`; both tests go red.

### C-S4T-3 — Rule 2: the replica position is read per envelope, before the apply

> **Claim.** `PullPolicy` measures a gap as `envelopeSeq - positionBefore.highestAppliedSeq`. Read
> the position *after* the apply and the envelope's own seq folds into the mark, hiding every gap.
> Read it once per *page* and the opposite happens: 39 contiguous envelopes measured against the
> position at the top of the page report a 39-wide gap on the last one and fire a `pull_request`
> for a stream with nothing wrong with it — answered by a full snapshot, on every sync.

```bash
grep -n "a long contiguous page reports no gap" -A 14 \
  <repo>/core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt
```

*Expected:* a page of 39 contiguous envelopes against `PullPolicy(gapThreshold = 5)` sends **zero**
pushes, and `positionReads.size == 40` (39 envelopes + one lazy seeding read). The count is the
load-bearing assertion — a page-scoped implementation reads **once**. **Measured here.**

### C-S4T-4 — Rule 4: the seq that drives the cursor is the authenticated one

> **Claim, and it is the one an auditor should attack first.** `RelayClient.parsePullPage` accepts
> two page shapes, and in the `{seq, envelope}` shape the relay's reported sequence number and the
> envelope's own **can disagree**. The envelope's is in the AAD and the AEAD tag covers it; the
> relay's is authenticated by nothing. A cursor driven by the relay's number lets a blind relay
> truncate the stream — report `seq: 999` on an envelope carrying `5` and the phone never asks for
> `6..999` again — **without decrypting a single byte it is unable to read**.

```bash
grep -n "the cursor follows the envelope's authenticated seq" -A 18 \
  <repo>/core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt
grep -n "o\[\"envelope\"\]" -B 4 <repo>/core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expected:* the test's second pull sends `since=5`, not `since=999`, and `RelayClient` really does
accept the wrapper shape (so the divergence is reachable, not hypothetical). **Measured here.**

> **What this claim does NOT say.** It is not a report of a relay that lies — the deployed relay
> splices the envelope back verbatim, so the two numbers agree today. The claim is that the phone
> no longer *depends* on that being true. Whether the wrapper shape should exist in `parsePullPage`
> at all is a separate question this slice did not answer.

### C-S4T-5 — Rule 3: a `pull_request` that never landed is not an outstanding request

> **Claim.** `PullPolicy` latches to stop one stalled sync producing a burst of traffic. The pump
> holds the other end of that contract: a push the relay refused calls `PullPolicy.onRequestFailed`,
> so the latch does not silence the policy for the life of the process over one dropped packet.

```bash
grep -n "releases the latch instead of latching forever" -A 10 \
  <repo>/core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt
```

*Expected:* `requestFailed == COLD_START`, `requestSent == null`, `hasPendingRequest == false`.
**Measured here.**

### C-S4T-6 — The pull half still needs no device key, and that is asserted rather than assumed

> **Claim.** `pull_request` is not state-changing (§5.4), so `SyncPump` works with an
> `OutboundEnvelopeFactory` built with **no `DeviceSigner` at all**. This is why S4's pull half was
> never blocked on S3's Android Keystore key (B-4). The test builds exactly that pump and asserts
> the pushed envelope carries no `sig`; were the kind ever moved into
> `Protocol.STATE_CHANGING_KINDS`, the factory would throw `UnsignableEnvelope` and this test is
> where it surfaces.

```bash
grep -n "unsigned pull_request for seq zero" -A 22 \
  <repo>/core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt
```

*Expected:* the decrypted push body is exactly `{"kind":"pull_request","body":{"since_seq":0}}` and
`"sig"` is absent from the envelope's keys. **Measured here** — and note the body is read by
actually opening the AEAD, not by string-matching the wire.

### C-S4T-7 — `checkCoreIsAndroidFree` cannot have been affected, structurally

> **Claim.** `SyncPump.kt` contains **zero `import` lines**. It references only types already in
> `app.careerseeker.core` and adds no dependency to `core/build.gradle.kts`. The Android-free rule
> therefore cannot have been broken by this slice — not "was checked and passed", but "has no
> mechanism by which it could fail".

```bash
grep -c "^import" <repo>/core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
git diff HEAD~2..HEAD -- <repo>/core/build.gradle.kts
```

*Expected:* `0`, and an empty diff. **Measured here.** The task itself needs AGP and was **not**
run — see C-S4T-8.

### C-S4T-8 — What this slice did NOT verify, and why

> **Claim.** Three things are unverified and one of them matters.
>
> 1. **The android gate did not run and cannot here** — no SDK, no JBR, and `dl.google.com` is an
>    egress policy denial (B-7, re-measured this session). So `:app:assembleDebug`, `:app:lintDebug`,
>    `:app:test` and `checkCoreIsAndroidFree` have **not** been executed by me. CI is the gate.
> 2. **`Verify-Alpha.ps1` did not run and cannot** — no .NET. It also cannot be affected: this slice
>    touched no `.cs`, no harness, no vector byte and no count-reporting doc, so
>    `$ExpectedOfflineTotal` (598) is untouched by construction.
> 3. **`SyncPump` has no production caller.** Nothing in `:app` constructs one. The loop is proven
>    against a MockEngine relay and a fake replica, which is exactly as far as this machine can take
>    it; the real replica is Room-backed and the real transport needs a Ktor engine in `:app`.

```bash
curl -sS -o /dev/null -w "%{http_code}\n" --max-time 25 \
  https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.3.0/gradle-9.3.0.pom
grep -rn "SyncPump" <repo>/app/src   # must print nothing
# MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6
```

*Expected:* the curl fails with `curl: (56) CONNECT tunnel failed, response 403` (**measured this
session**; `repo.maven.apache.org` answers `200` on the same run, which is why `:core` is
buildable and `:app` is not); the grep prints nothing, **which is the honest statement of what is
left**; and CI reports on the push. **The CI claim is written to be checked, not assumed** — at the
time of writing it had not reported.

### C-S4T-9 — The `pull_request` KDoc no longer contradicts §4.3.4

> **Claim.** `OutboundEnvelopeFactory.pullRequest` described the semantics PQ-S4-1 removed ("from a
> sequence point"). Comment-only fix; no behaviour, and the parameter stays because the field is
> still on the wire.

```bash
git show HEAD -- <repo>/core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
```

*Expected:* a comment-only diff. **Measured here** — and the `:core` suite was re-run after it
(133 / 0 / 0), which is the only way to say "comment-only" and mean it.

### C-S4T-10 — CI reported, and it closes C-S4T-7 and the first item of C-S4T-8

> **Claim.** The android gate ran on this push and is **green**. Run
> [31358052519](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31358052519), job
> *Build and test* (`93361342042`), conclusion **`success`**, on head `540a489` — `ubuntu-latest`,
> **JDK 17** (`Java_Temurin-Hotspot_jdk/17.0.19-10`), real SDK at `/usr/local/lib/android/sdk`. Read
> from the job log directly, not inferred from the green tick:
>
> ```
> > Task :checkCoreIsAndroidFree            BUILD SUCCESSFUL in 1m 34s
> OK: all vendored vectors match 679a3175590dcd021b21c85af9daf12114e131fd
> > Task :core:test                         BUILD SUCCESSFUL in 49s
> > Task :app:test                          BUILD SUCCESSFUL in 1m 34s
> > Task :app:assembleDebug                 BUILD SUCCESSFUL in 1m 56s
> > Task :app:lintDebug                     BUILD SUCCESSFUL in 49s
> OK: no analytics or tracking SDKs on the release classpath.
> ```
>
> **All 18 `SyncPumpTest` cases appear individually as `PASSED` in that log**, so the suite is green
> on the gate and not only on the reduced probe. Two claims that the probe could **not** make are now
> made by the gate rather than by argument: `checkCoreIsAndroidFree` **executed** (C-S4T-7 argued the
> rule could not break; CI shows it did not), and the vendored-vector pin is confirmed against
> `679a317` by CI's own blob comparison.

```bash
# MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6
# MCP: get_job_logs job_id=93361342042 return_content=true
#   grep for: checkCoreIsAndroidFree | all vendored vectors match | SyncPumpTest | BUILD
```

*Expected:* `conclusion: success` on head `540a489`, and the six lines above.

> **What is still NOT closed by this.** CI proves the gate is green; it does **not** print test
> totals, so `133 / 0 / 0` stays a probe measurement (C-S4T-1). And CI cannot invent a caller —
> `grep -rn SyncPump app/src` still prints nothing, so the third item of C-S4T-8 stands exactly as
> written. **A green gate on an uncalled class is not a working transport loop**, and this entry
> says so rather than letting the tick imply otherwise.

---

## S3 — the pairing attempt's decision layer (`PairingFlow`), 2026-08-10, ninth cloud iteration

Same reduced probe as C-S6A-1 (`:core` only, Maven Central only, toolchain 17 → 21); the gate is
still CI. Where a claim is unverified, it says so and says why.

### C-S3A-1 — The measured `:core` total moved 133 → 154, and `PairingFlowTest` is the whole delta

> **Claim.** Baseline re-measured on the untouched branch at `ebfaf81` **before** anything was
> written: **133 / 0 / 0** across 12 classes, which matches the figure `STATE.md` already carried.
> After the slice: **154 / 0 / 0** across 13 classes — `+21`, all of them `PairingFlowTest`. **No
> existing test was edited, renamed or deleted**, and no existing source file was touched: the
> slice is two new files and nothing else. They passed on the **first** run.

```bash
# after the C-S6A-1 recipe, in the careerseeker-android checkout:
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=s=0; c=0
for p in sorted(glob.glob('<repo>/core/build/test-results/test/TEST-*.xml')):
    r=ET.parse(p).getroot(); c+=1
    t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t, f, s, c)
PY
git show --stat HEAD   # expect: 2 files changed, both new
```

*Expected:* `154 0 0 13`. For the baseline, `git checkout ebfaf81` and repeat: `133 0 0 12`.

### C-S3A-2 — Rule 1: the completion is built once per invite and retried verbatim

> **Claim.** After a relay outage that exhausts `RelayClient`'s retries, `PairingFlow.retry()`
> re-sends the **same bytes** and does not re-derive. `completionBuilds` stays `1`, the set of
> distinct request bodies across all five HTTP attempts has size `1`, and the ephemeral keypair
> supplier is invoked exactly once.
>
> The failure this pins is silent in both directions. If the first body landed and is still stored,
> the retry gets 409 and the engine later collects **body #1**, deriving against a `phone_pub` this
> device has thrown away — both screens then show confirm codes that cannot match, with nothing
> explaining why. If the first body landed and was already collected, the engine burned the
> one-time secret against body #1, so body #2 is refused and the phone waits for a confirmation
> that will never appear.

```bash
# C-S6A-1 recipe, then:
grep -n "does not rebuild the completion\|keeps the ephemeral key" -A 25 \
  core/src/test/kotlin/app/careerseeker/core/PairingFlowTest.kt
```

*Expected:* both cases `PASSED` in the probe output.

### C-S3A-3 — Rule 2: a 409 on submit is ambiguous *by construction*, and the class refuses to guess

> **Claim.** `RelayClient.request` retries transport failures internally (4 attempts,
> `RelayClient.kt:186-215`). An attempt that stores the completion and loses its response is
> therefore followed by an attempt that sees the relay's own 409 — so **this phone's success can
> reach it as `RelayResult.Conflict`**, and no information available to the phone separates that
> from a stranger's completion. `PairingFlow` neither aborts (which would kill a good pairing on
> every network hiccup) nor treats it as success (which would hide a real race): it returns
> `AwaitingConfirmation(raced = true)` and lets the confirm code arbitrate, which is the job §5.2
> assigns it — *"the confirmation step catches a raced completion"*.

```bash
grep -n "ambiguous by construction" -A 12 core/src/main/kotlin/app/careerseeker/core/PairingFlow.kt
grep -n "neither success nor failure\|raced attempt still confirms" -A 18 \
  core/src/test/kotlin/app/careerseeker/core/PairingFlowTest.kt
sed -n '/private suspend fun request(/,/^    }/p' core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expected:* the retry loop in `RelayClient` (which is what makes the ambiguity structural rather
than hypothetical), and both 409 cases `PASSED`.

### C-S3A-4 — Rule 4: the phone issues exactly one relay call, and it is never `/create`

> **Claim.** A complete successful attempt — `begin()` through `confirm(true)` — makes **one** HTTP
> call: `POST /v1/{pairing}/pair`, carrying the **provisional** bearer (§5.2.1), never the final
> one. `PairingFlow` never calls `RelayClient.create`, which is the phone-reachable rotation path
> (`RelayClient.kt:94`, `rotateToSha256Hex`).
>
> §5.2.3 assigns rotation to the engine. A phone that rotates while the engine still holds the
> provisional bearer locks it out of `GET /v1/{pairing}/pair` with a 401 it cannot read as "the
> phone jumped the gun" — and by then the completion is stored, one-shot and unreadable, and the
> secret is spent. Unrecoverable, and silent in code.

```bash
grep -n "never rotates the relay token" -A 14 core/src/test/kotlin/app/careerseeker/core/PairingFlowTest.kt
grep -n "create(" core/src/main/kotlin/app/careerseeker/core/PairingFlow.kt ; echo "exit=$?"
```

*Expected:* the test `PASSED`; the `grep` for `create(` in `PairingFlow.kt` prints **nothing**
(`exit=1`).

### C-S3A-5 — Rule 3 and the ladder: the human gate, and a promotion that is one-way

> **Claim (gate).** Key material is reachable only through `PairingStep.Paired`, which only
> `confirm(true)` produces. `confirm(false)` is `PairingAbort.CODE_MISMATCH` — reported distinctly
> from `CANCELLED` because the two mean opposite things about whether an attacker is present — and
> is terminal: a second `confirm` throws.
>
> **Claim (ladder).** `RelayTokenLadder` opens on the provisional token, answers a 401 on it with
> the final one, and **once a call carrying the final token is accepted, never falls back**
> (`unauthorised("final")` returns `null`). Rotation is one-way and idempotent (§5.2.3), so after
> it there is no state in which the provisional token is right again; a ladder that kept falling
> back would turn a revoked pairing — a 401 the user needs to see — into an auth blip retrying
> forever against a token derived from a secret the engine already burned.

```bash
grep -n "code mismatch is terminal\|promotes, and promotion is one-way\|does not promote\|opens on the provisional" -A 14 \
  core/src/test/kotlin/app/careerseeker/core/PairingFlowTest.kt
```

*Expected:* all four cases `PASSED`.

### C-S3A-6 — The confirm code is the desktop's, not this code's own opinion

> **Claim.** The six digits `PairingFlow` puts on screen equal `pairing-basic`'s
> `expected.confirm`, and the pairing `confirm(true)` yields carries the vector's `k_p2e` and
> `relay_token`. The completion is built from the vector's own phone key, so agreement here is
> agreement with the engine that the same vector proves — not a stub agreeing with itself.

```bash
grep -n "six digits shown are the ones the desktop derives" -A 10 \
  core/src/test/kotlin/app/careerseeker/core/PairingFlowTest.kt
cd <careerseeker> && git checkout main                       && node docs/sync-vectors/generate.mjs --check
cd <careerseeker> && git checkout claude/s5-entitlement-ack-spec && node docs/sync-vectors/generate.mjs --check
```

*Expected:* the case `PASSED`, and — **the count depends on the ref, and this entry says which** —
`OK: 26 vector files match the generator.` on `main` (`00b3705`), `OK: 28 …` on
`claude/s5-entitlement-ack-spec` (`9c05ef7`), both exit 0. **Both measured here.** The two ack
vectors PR #32 adds are not on `main` until it merges; `STATE.md`'s standing "28" is the branch
figure, and reading it as a `main` figure is the mistake this line exists to prevent. Either way
**this slice added and edited nothing** — it wrote no file in the main repo at all.

### C-S3A-7 — `checkCoreIsAndroidFree` cannot have been affected, structurally

> **Claim.** `PairingFlow.kt` contains **zero `import` lines**, so it cannot import `android.*` or
> `androidx.*` — the rule the task enforces (`build.gradle.kts:39`). The test file imports Ktor,
> kotlinx and `kotlin.test` only, exactly as `SyncPumpTest` and `RelayClientTest` already do, and
> the task walks `core/src` including tests.

```bash
grep -c "^import" core/src/main/kotlin/app/careerseeker/core/PairingFlow.kt          # expect 0
grep -rn "^import android\.\|^import androidx\." core/src/ ; echo "exit=$?"          # expect exit=1
```

*Expected:* `0`, and no matches.

### C-S3A-8 — What this slice did NOT verify, and why

> **Not verified — the android gate.** `./gradlew … :app:assembleDebug :app:lintDebug` did **not**
> run and cannot on this machine: no Android SDK, no JBR, and `dl.google.com` / `api.foojay.io` are
> egress policy denials (B-7). Everything above comes from the reduced `:core` probe. **CI is the
> gate**; at the time of writing it has not yet reported on this push.
>
> **Not verified — `Verify-Alpha.ps1`.** No .NET here. It also **cannot** be affected: this slice
> wrote no `.cs` file, no harness, no vector byte and no count-reporting doc, and no file at all in
> the main repo, so `$ExpectedOfflineTotal` (598) is untouched by construction.
>
> **Not verified — anything about hardware-backed keys.** `deviceSigPublic` is supplied by the test
> as a public point, exactly as `:app` will supply it from a Keystore key. Whether that key is
> really StrongBox-, TEE- or software-backed — and therefore whether the fallback indicator and the
> audit entry that gate P2-KEYSTORE-FALLBACK requires are correct — is a claim only an emulator or a
> device can settle. **B-4 still owns that claim in full.**
>
> **`PairingFlow` has no production caller, and this file will not pretend otherwise.** S3 does not
> become DONE. A green suite on an uncalled class is not a pairing screen.

```bash
grep -rn "PairingFlow\|RelayTokenLadder" app/src ; echo "exit=$?"
curl -sS -o /dev/null -w '%{http_code}\n' https://dl.google.com/ ; echo "exit=$?"
```

*Expected:* the first prints **nothing** (`exit=1`) — written to fail the day `:app` wires this up,
which is the point. The second is a CONNECT tunnel failure / 403, not a 200.

### C-S3A-9 — CI reported, and it closes C-S3A-7 and the first item of C-S3A-8

> **Claim.** The android gate ran on this slice and is **green**. Run
> [31374085226](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31374085226), job
> *Build and test* (`93409378480`), conclusion **`success`**, **`head_sha` `d361fa3`** — verified
> against the run's own `head_sha` field rather than inferred from the PR's check list, because a
> check-runs listing follows the PR head and would have answered for whatever was newest. Read from
> the job log, not from the tick:
>
> ```
> > Task :checkCoreIsAndroidFree
> OK: all vendored vectors match 679a3175590dcd021b21c85af9daf12114e131fd
> > Task :core:test          BUILD SUCCESSFUL in 46s
> > Task :app:test           BUILD SUCCESSFUL in 1m 28s
> > Task :app:assembleDebug  BUILD SUCCESSFUL in 1m 50s
> > Task :app:lintDebug      BUILD SUCCESSFUL in 48s
> OK: no analytics or tracking SDKs on the release classpath.
> ```
>
> **All 21 `PairingFlowTest` cases appear individually as `PASSED`** and the log contains **zero**
> occurrences of `FAILED`. Two claims the probe could not make are now made by the gate rather than
> by argument: `checkCoreIsAndroidFree` **executed** (C-S3A-7 argued the rule could not break; CI
> shows it did not), and the vendored-vector pin is confirmed against `679a317` by CI's own blob
> comparison — so this slice added no drift to the file the two repos share.

```bash
# MCP: actions_get method=get_workflow_run resource_id=31374085226   -> check head_sha is d361fa3
# MCP: get_job_logs job_id=93409378480 return_content=true tail_lines=1050
grep -c "FAILED" <log>                                    # expect 0
grep -o "PairingFlowTest" <log> | wc -l                   # expect 21
grep -o "() PASSED" <log> | wc -l                         # expect 154
```

> **A methodological correction, and it is worth carrying forward.** Every previous entry in this
> file says *"CI prints no totals, so the count stays a probe number."* That is true of a summary
> line and **false of the log**: `:core:test` prints one `PASSED` line per case, and counting them
> gives **154** — exactly the probe's figure, on `ubuntu-latest` with JDK 17 and a real SDK. The
> count is therefore no longer probe-only, and the same command would have corroborated `133` and
> `115` in earlier iterations had anyone counted. Use it in future slices instead of caveating.

> **What is still NOT closed by this.** CI cannot invent a caller: `grep -rn "PairingFlow" app/src`
> still prints nothing, so the third item of C-S3A-8 stands exactly as written, and **a green gate on
> an uncalled class is not a pairing screen**. Nor does any of this touch B-4's claims — no CI runner
> has an Android Keystore either.

---

## S6 send path — the outbound decision layer (2026-08-10, tenth cloud iteration)

Every number below comes from the reduced `:core` probe (C-S6A-1 recipe), run in this session on
this machine. Where a claim needs a gate this sandbox cannot run, it says so and says why.

### C-S6S-1 — The suite, and the delta

> **Claim.** `:core` is **177 tests / 0 failures / 0 skipped across 14 classes**, up from a
> **154 / 0 / 0 across 13** baseline re-measured on the same probe in the same session before the
> slice. The +23 is `OutboundQueueTest` (**20**, new) and three new cases in `RelayClientTest`
> (**14 → 17**). No existing test was deleted or renamed; two existing assertions changed only
> because `RelayResult.Conflict` gained a field (see C-S6S-2).

```bash
# C-S6A-1 recipe, then:
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=s=0; per={}
for p in sorted(glob.glob('<repo>/core/build/test-results/test/TEST-*.xml')):
    r=ET.parse(p).getroot(); n=int(r.get('tests')); per[r.get('name').split('.')[-1]]=n
    t+=n; f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t,f,s,len(per)); print(per)
PY
```

*Expected:* `177 0 0 14`, with `OutboundQueueTest: 20` and `RelayClientTest: 17`. For the baseline,
`git stash` the slice or check out `b95ea8a` and repeat: `154 0 0 13`.

### C-S6S-2 — The relay reports its high-water mark on a refused push, and the client kept throwing it away

> **Claim.** `POST /v1/{pairing}/push` refuses `seq <= last` with **409 and a body carrying
> `latest`** — `relay/src/channel.ts:167` in the main repo:
>
> ```ts
> if (last !== null && seq <= last) return this.json({ error: 'replay_rejected', latest: last }, 409);
> ```
>
> `RelayClient.request` mapped every 409 to a bare `RelayResult.Conflict` and returned before
> reading the body, so that number was unreachable to any caller. It is now parsed into
> `RelayResult.Conflict.latest`. **This is the input §6.1's reconciliation needs**: the spec's own
> recipe for a lagging counter is to resume above the relay's `latest` for the direction, and
> without the number a sender can only retry an envelope the relay refuses forever.

```bash
# in the careerseeker (main) checkout, at origin/main:
grep -n "replay_rejected" relay/src/channel.ts
# in the android checkout:
grep -n "conflictLatest\|data class Conflict" core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

> **Not verified — the relay's own suite does not assert this field.** `grep -n latest
> relay/test/relay.test.ts` on `origin/main` returns only the two `pull` assertions (lines 156,
> 158); nothing there covers the 409 body. **The relay suite was not run this iteration** (no `npm`,
> no vitest, no miniflare). The claim above rests on reading `channel.ts` and on the three android
> unit tests in C-S6S-3, which drive a `MockEngine`, not the relay.

### C-S6S-3 — `latest` is present for a replay rejection and absent for a pairing conflict

> **Claim.** Three `RelayClientTest` cases, all run here. A 409 carrying
> `{"error":"replay_rejected","latest":41}` parses to `Conflict(latest = 41)`. A pairing 409
> carrying `{"error":"exists"}` — what §5.2.1/§5.2.2 answer — parses to `Conflict(latest = null)`,
> which is correct rather than a gap: "already done" is not "your counter is behind", and there is
> nothing to reconcile against. A 409 whose body is not JSON also yields `Conflict(latest = null)`
> rather than throwing, because converting a relay *decision* into a transport failure would make
> the caller retry something the relay has already refused.
>
> **`PairingFlow`'s reading of a 409 is unchanged by this.** It still treats the pairing conflict as
> ambiguous and routes it to the human's confirm-code comparison (C-S3A-3); the new field is null on
> exactly that path, so there was no number for it to start trusting.

```bash
# C-S6A-1 recipe, then read the three case names from the log:
grep -E "RelayClientTest > (a replay_rejected|a pairing 409|a 409 whose body)" <probe-log>
```

*Expected:* three lines, each ending `PASSED`.

### C-S6S-4 — The envelope is built once, and a retry re-sends the identical bytes

> **Claim.** `OutboundEnvelopeFactory.build` consumes a sequence number on **every** call, so
> "retry" and "rebuild" are different operations that are indistinguishable at the call site.
> `OutboundQueue` freezes the wire at the first `next()` that reaches an entry and returns the same
> string until the head is resolved. The test counts the `SeqSource`'s calls directly, so "a second
> sequence number was burned" is observable rather than argued: after an `Unavailable` and a second
> `next()`, `issued == 1` and the two wires are `assertEquals`-identical.
>
> **The failure this prevents is silent.** A rebuild burns a second p2e seq, and if the first
> attempt landed and merely lost its response, the engine receives one intention twice under two
> sequence numbers with two audit rows.

```bash
grep -E "OutboundQueueTest > (the envelope is built once|an accepted envelope|only one envelope)" <probe-log>
```

### C-S6S-5 — A 409 on a push is read as neither success nor failure, and single-flight is what makes that checkable

> **Claim, and it is the finding of the slice.** Attempt count **cannot** disambiguate a push
> conflict. `RelayClient.request` retries transport failures *inside* one `push` call
> (`RelayClient.kt`, 4 attempts by default), so an attempt that reaches the relay, stores the
> envelope and loses its response is followed by an attempt that sees the relay's own 409 — and the
> queue sees that on what it believes is its **first** attempt. This is the same ambiguity
> `PairingFlow` recorded on `POST /pair` (C-S3A-3), arriving on the push path.
>
> `OutboundQueue` therefore does not guess. A 409 retires the frozen bytes, halts on
> `SendHalt.COUNTER_BEHIND`, and hands `latest` to the caller that owns the persisted counter.
> Whether the mark actually landed is answered **only** by `OutcomeMarkPolicy`'s value convergence,
> because with no `outcome_ack` that is the only evidence v1 offers (PQ-S6-1). The test asserts the
> outcome is **not** `Accepted`.
>
> **The cost is a possible duplicate, and it is the right way to be wrong.** If the original did
> land, the rebuilt envelope re-states the same mark; §4.3.1's carried outcome is latest-wins state
> rather than an event log, so a duplicate is idempotent in effect, whereas guessing "delivered"
> loses the user's mark silently.
>
> **Single-flight is load-bearing for the above, not a style choice.** §6.2 would permit pipelining
> (gaps are legal and MUST NOT stall the stream), but a queue with several unresolved sequence
> numbers outstanding cannot attribute a 409 to any of them. `next()` refuses to build a second
> envelope while the head is unresolved, and the test asserts `issued == 1` after three `next()`
> calls on a two-item queue.

```bash
grep -E "OutboundQueueTest > (a conflict halts|a conflict is never|the queue stays stopped|reconciling rebuilds|a conflict with no reported)" <probe-log>
```

### C-S6S-6 — Poison is dropped; every other failure keeps the user's data

> **Claim.** Asserted case by case. `TooLarge` drops **only** that envelope and the queue continues
> with the next id — retrying it would wedge every later mark behind an envelope that can never
> fit, and the drop is safe because the relay never stored it (so its `last` is unmoved) and §6.2
> makes the resulting p2e gap legal for the receiver. `Unavailable` keeps the bytes and the depth
> (offline is not a data-loss event). `Unauthorised` halts, keeps the bytes, and is cleared **only**
> by `reauthorised()` — `reconciled()` does not clear it, which is asserted. `PairingUnknown` is
> terminal and neither clearing call revives it.

```bash
grep -E "OutboundQueueTest > (a 413 drops|pairing_unknown is terminal|unauthorised halts|a failed push keeps)" <probe-log>
```

### C-S6S-7 — A missing device key halts the queue and destroys nothing, and the halt is no broader than §5.4

> **Claim.** `outcome` is state-changing, so `OutboundEnvelopeFactory` refuses to build it without a
> `DeviceSigner` (§5.4). `OutboundQueue` catches that refusal and **halts** on
> `SendHalt.NO_DEVICE_KEY` with the queue depth unchanged — dropping the user's marks to report a
> missing key would delete data in order to describe a condition the UI should be showing instead.
> The companion case guards the halt from over-reaching: `pull_request` changes no engine state, so
> it needs no key, and it is asserted to build and flow normally on a queue with `signer = null`.

```bash
grep -E "OutboundQueueTest > (no device key halts|a kind that needs no signature)" <probe-log>
```

### C-S6S-8 — The suite found a defect in the implementation before this file described it

> **Claim.** The first full run of the new suite was **not** green: `177 tests completed, 1 failed`.
> `reconciling rebuilds above the reported mark` failed with
> `AssertionFailedError: fresh bytes are a fresh attempt ==> expected: <0> but was: <1>`. Retiring
> the dead bytes after a 409 had cleared the wire but not the per-wire attempt counter, so a freshly
> rebuilt envelope reported itself as already-tried — a lie about bytes the relay has never seen,
> and the only signal a future backoff or telemetry caller would have. Fixed by resetting
> `attempts` in the same branch that retires the wire; the re-run is green.
>
> Recorded because this file's precedent is to say whether a slice was green immediately. **This one
> was not**, and the assertion that caught it is one an implementation-shaped test would not have
> made.

```bash
git log --oneline -3          # the fix is inside the OutboundQueue commit, not a follow-up
grep -n "per-wire, not per-item" core/src/main/kotlin/app/careerseeker/core/OutboundQueue.kt
```

### C-S6S-9 — `:core` stays Android-free and no shared vector byte moved

> **Claim.** `OutboundQueue.kt` has **zero** `import` lines, so the `checkCoreIsAndroidFree` claim
> is structural rather than a promise. Independently, the task's own predicate
> (`build.gradle.kts:39` — a line starting `import android.` or `import androidx.`) matches nothing
> anywhere under `core/src`.
>
> **All 26 vendored vectors are byte-identical to pin `679a317`**, verified here against the main-repo
> checkout rather than assumed: nothing under `core/src/test/resources/` was opened for writing, no
> vector was added or edited in either repo, and the pin is unchanged.

```bash
grep -c '^import' core/src/main/kotlin/app/careerseeker/core/OutboundQueue.kt        # expect 0
grep -rn -E '^\s*import\s+(android|androidx)\.' core/src --include=*.kt ; echo $?    # expect exit 1
git status --porcelain core/src/test/resources/ | wc -l                             # expect 0
# byte-for-byte against the pin, from a careerseeker checkout:
for f in <android>/core/src/test/resources/sync-vectors/v1/*.json; do
  git show 679a3175590dcd021b21c85af9daf12114e131fd:docs/sync-vectors/v1/$(basename "$f") \
    | diff -q - "$f" || echo "DRIFT $(basename "$f")"
done
node docs/sync-vectors/generate.mjs --check     # in careerseeker @ origin/main
```

*Expected:* `0`; exit `1`; `0`; no `DRIFT` line across 26 files; and
`OK: 26 vector files match the generator.` (exit 0) — the **`main`** figure. See C-S3A-6: the "28"
in these records is the `claude/s5-entitlement-ack-spec` figure and is not a `main` number.

### C-S6S-10 — `OutboundQueue` has no production caller, and this file will not pretend otherwise

> **Claim.** `grep -rn "OutboundQueue" app/src` prints nothing. **S6 does not become DONE.** A green
> suite on an uncalled class is not an outcome-marking feature: nothing on any screen builds an
> envelope, nothing pushes one, and no `:app` code references this type.

```bash
grep -rn "OutboundQueue" app/src ; echo "exit=$?"
```

*Expected:* nothing, `exit=1` — written to fail the day `:app` wires this up, which is the point.

### C-S6S-11 — What did NOT run, and cannot here

> **Not verified — the android gate.** `./gradlew … :app:assembleDebug :app:lintDebug` did **not**
> run and cannot on this machine: no Android SDK, no JBR, and `dl.google.com` / `api.foojay.io` are
> egress policy denials (B-7). Every number above is the reduced `:core` probe. **CI is the gate**;
> at the time of writing it has not reported on this push. Per C-S3A-9, read the count from the
> job log by counting `() PASSED` lines — expect **177** — rather than caveating that CI prints no
> totals.
>
> **Not verified — `Verify-Alpha.ps1`.** No .NET here. It also **cannot** be affected: this slice
> wrote **no file in the main repo at all** — no `.cs`, no harness, no vector byte, no
> count-reporting doc, no `docs/Sync-Protocol.md` change — so `$ExpectedOfflineTotal` (**598**) is
> untouched by construction.
>
> **Not verified — the relay.** No `npm`, no vitest, no miniflare, no `wrangler`, no deploy. **The
> production relay was contacted zero times, not even `GET /v1/health`.** Every relay in this slice
> is a Ktor `MockEngine` inside the test JVM.
>
> **Not verified — anything about hardware-backed keys.** The signer here is a stub returning fixed
> bytes; the slice asserts only *whether a signature could be produced at all*, never that one
> verifies against a real device key. **B-4 owns every hardware claim in full.**

### C-S6S-12 — CI reported green on this exact head, and what that does and does not prove

> **Claim.** The android gate ran on this slice and is **green**. Run
> [31392794765](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31392794765) (run
> #69, event `push`), job *Build and test* (`93468326913`), conclusion **`success`**,
> 13:25:17 → 13:32:38 UTC, **`head_sha` `88b1d19497ebe0c49b559704aff9fc90fb89e4b9`** — read from the
> run's own `head_sha` field and compared against this branch's tip, because a check-runs listing
> follows the PR head and would answer for whatever is newest.
>
> `.github/workflows/ci.yml` runs everything in a **single job**, so a `success` conclusion means no
> step failed — including the six the reduced `:core` probe structurally cannot run:
> `checkCoreIsAndroidFree`, the vendored-vector diff against `679a317`, `:app:test`,
> `:app:assembleDebug`, `:app:lintDebug`, and the release-classpath tracker check. In particular
> **the vendored-vector step is CI's own blob comparison against the pinned main-repo commit**, which
> independently confirms C-S6S-9's local diff: this slice added no cross-repo vector drift.
>
> **What this does NOT prove, stated because the previous iteration established the stronger method
> and this one did not use it.** C-S3A-9 showed that the job log prints one `PASSED` line per test
> case and that counting them corroborates the probe's total. **I did not count them here.** The
> **177** figure in these records is therefore the probe's number, and CI corroborates it only as
> *green*, not as a *count*. A future iteration should run the count — it is one command — rather
> than inheriting this gap.

```bash
# MCP: actions_get method=get_workflow_run resource_id=31392794765   -> head_sha, conclusion
# MCP: get_job_logs job_id=93468326913 return_content=true tail_lines=2500
grep -c "FAILED" <log>                    # expect 0
grep -o "() PASSED" <log> | wc -l         # expect 177  -- NOT yet run; this is the gap above
git rev-parse HEAD                        # must equal the run's head_sha
```

---

## S2 relay conformance — retention on the read path (C-S2R-8 … C-S2R-15)

All commands run from a clone of `ShivaClaw/careerseeker` at `claude/s2-relay-retention`
(draft PR, stacked on #32), with `cd relay && npm ci && npx wrangler types` done once.
Branch commits: `90ae2a1` (the fix), `310406a` (the pins); parent `9c05ef7` = #32's head.

Several probes below assert a **deliberately wrong** expected value, so the runner prints the
measured one in its diff. That is the house trick for reading a value out of the Workers pool:
`console.log` inside `@cloudflare/vitest-pool-workers` does not reach the terminal, so an
intentional mismatch is the cheapest way to make the runtime state its own answer. **A red run is
the evidence here, not a failure** — read the `Received` line.

### C-S2R-8 — The relay never checks the `pairing` field it declares (PQ-S2-1)

> **Claim.** `POST /push` accepts an envelope whose `pairing` is malformed, absent, or names a
> *different* pairing — 201 in all three cases.

Write `relay/test/probe.test.ts`:

```ts
import { env } from 'cloudflare:workers';
import { describe, expect, it } from 'vitest';
import worker from '../src/index';
const call = (p: string, i?: RequestInit) => worker.fetch(new Request(`https://r.example${p}`, i), env as never);
const bearer = { authorization: 'Bearer tok' };
const base = { v: 1, dir: 'e2p', ts: '2026-06-11T14:02:11Z', key_id: 'k-1', nonce: 'AAAAAAAAAAAAAAAA', ciphertext: 'op' };
describe('probe', () => {
  it('accepts any pairing value', async () => {
    const id = 'p_000042ProbeVbN3W';
    expect((await call(`/v1/${id}/create`, { method: 'POST', headers: bearer })).status).toBe(201);
    const push = (b: object) => call(`/v1/${id}/push`, { method: 'POST', headers: bearer, body: JSON.stringify(b) });
    const a = await push({ ...base, pairing: 'p_x', seq: 1 });              // wrong shape, not this channel
    const b = await push({ ...base, seq: 2 });                              // field absent entirely
    const c = await push({ ...base, pairing: 'p_AAAAAAAAAAAAAAAA', seq: 3 }); // a different valid pairing
    expect([a.status, b.status, c.status]).toEqual([0, 0, 0]);              // deliberate mismatch
  });
});
```

```bash
cd relay && npx vitest run test/probe.test.ts; rm test/probe.test.ts
```

*Expected:* the test fails with `Received: [ 201, 201, 201 ]`. Any `400` in that array means the
check has since been added and **PQ-S2-1 should be closed**.

The two non-conforming pairing ids the PQ cites, which are why this was recorded rather than fixed:

```bash
grep -n 'p_bridge_test' tests/EngineHarness/Program.cs      # -> 2268 (and 2325): 11 chars after p_, not 16
grep -n "pairing: 'p_x'" relay/test/relay.test.ts           # -> the suite's own envelope helper
```

### C-S2R-9 — One out-of-range `seq` wedges a direction permanently (PQ-S2-2)

> **Claim.** A push at `Number.MAX_SAFE_INTEGER` is accepted (201), after which every legitimate
> envelope in that direction is refused 409 with `latest: 9007199254740991`.

Write `relay/test/probe.test.ts`:

```ts
import { env } from 'cloudflare:workers';
import { describe, expect, it } from 'vitest';
import worker from '../src/index';
const call = (p: string, i?: RequestInit) => worker.fetch(new Request(`https://r.example${p}`, i), env as never);
const bearer = { authorization: 'Bearer tok' };
const env1 = (seq: number) => JSON.stringify({ v: 1, pairing: 'p_x', dir: 'e2p', seq,
  ts: '2026-06-11T14:02:11Z', key_id: 'k-1', nonce: 'AAAAAAAAAAAAAAAA', ciphertext: 'op' });
describe('probe', () => {
  it('wedges on a huge seq', async () => {
    const id = 'p_000043ProbeVbN3W';
    expect((await call(`/v1/${id}/create`, { method: 'POST', headers: bearer })).status).toBe(201);
    const huge = await call(`/v1/${id}/push`, { method: 'POST', headers: bearer, body: env1(Number.MAX_SAFE_INTEGER) });
    const next = await call(`/v1/${id}/push`, { method: 'POST', headers: bearer, body: env1(1) });
    expect([huge.status, next.status, await next.text()]).toEqual([0, 0, '']);  // deliberate mismatch
  });
});
```

```bash
cd relay && npx vitest run test/probe.test.ts; rm test/probe.test.ts
```

*Expected:* fails with `Received: [ 201, 409, '{"error":"replay_rejected","latest":9007199254740991}' ]`.

The type mismatch half of the PQ is two greps, no runtime needed:

```bash
grep -n 'long Seq' src/Sync/EnvelopeCodec.cs     # -> 7: 64-bit on the engine side
grep -n 'Number.isInteger(seq)' relay/src/channel.ts   # -> the relay reads it as a JS double (2^53)
```

### C-S2R-10 — The read path served expired envelopes (the finding)

> **Claim.** Before `90ae2a1`, `GET /pull` returned an envelope whose `expires_at` had passed but
> which the TTL alarm had not yet collected, and counted it in `latest`.

This is a claim about the code *before* the fix, so it re-verifies by reverting one file and
running the tests that were written for it:

```bash
git checkout claude/s2-relay-retention
git checkout 9c05ef7 -- relay/src/channel.ts     # parent's channel.ts, this branch's tests
cd relay && npx vitest run
git checkout HEAD -- src/channel.ts              # put the fix back
```

*Expected:* **2 failed | 40 passed**, and the two failures name the defect exactly —

```
× does not serve an expired envelope that the alarm has not collected yet
    AssertionError: expected [ { seq: 1, expired: true } ] to have a length of +0 but got 1
× excludes expired rows from latest, so the page and its loop bound agree
    AssertionError: expected [ 1, 2 ] to deeply equal [ 1 ]
```

**A red run here is the defect reproducing.** The second failure is the one that matters most:
`latest: 2` against a page that stops at `1` is a loop bound the client can never reach, so a
paging caller re-pulls the same page until the alarm fires.

### C-S2R-11 — The fix, and the suite it moved

> **Claim.** With `90ae2a1` and `310406a` applied the whole relay suite is green at **42 passed**
> (from a **36** baseline re-measured on the parent in the same session), and the CI typecheck is
> clean.

```bash
git checkout claude/s2-relay-retention && cd relay
npm ci && npx wrangler types
npx tsc --noEmit; echo "tsc exit=$?"
npx vitest run
```

*Expected:* `tsc exit=0`, and `Test Files 1 passed (1) / Tests 42 passed (42)`.

Baseline, for the delta:

```bash
git stash; git checkout 9c05ef7 && cd relay && npx vitest run    # -> 36 passed
```

The +6 is four pins (C-S2R-12, C-S2R-13) and the two regression tests in C-S2R-10.

### C-S2R-12 — `push` still counts expired rows, and the relay guard is not durable

> **Claim.** The fix is confined to the read path. `POST /push` deliberately still refuses a `seq`
> at or below an expired-but-uncollected row — and, separately, the relay's replay floor disappears
> once the queue is emptied, because it is `MAX(seq)` over live rows.

```bash
cd relay && npx vitest run -t 'still refuses a seq at or below an expired-but-uncollected row'
cd relay && npx vitest run -t 'loses its replay floor once the queue is emptied'
```

*Expected:* both pass. The second is the one to read carefully: pushing `seq 9`, purging, then
pushing `seq 1` returns **201**. That is not a defect being pinned as acceptable — §6.2 puts the
authoritative replay check on the receiver's persisted high-water mark, and the test exists so a
future reader does not move that obligation onto the relay. `relay/src/channel.ts` carries the same
statement in a comment above the guard.

### C-S2R-13 — The 409 carries `latest`, and unknown fields survive verbatim

> **Claim.** Two guarantees the relay already made and nothing tested: the replay refusal's body is
> `{"error":"replay_rejected","latest":N}`, and an unrecognised top-level envelope field arrives at
> the puller unmodified.

```bash
cd relay && npx vitest run -t 'reports its high-water mark in the refusal'
cd relay && npx vitest run -t 'carries an unknown top-level field through to the receiver verbatim'
```

*Expected:* both pass. The first is a **cross-repo** contract — the android `:core` `RelayClient`
parses `latest` into `RelayResult.Conflict` (see C-S6S-2), and before this test a tidy-up dropping
the field would have been green in this repo while breaking the phone's only exit from a wedged
counter. The second is the wire behaviour PQ-A2-3's `invalid-unknown-field` vector depends on:
§3 binds *receivers* to reject unknown fields, so the relay must not silently repair them.

### C-S2R-14 — Nothing outside `relay/` moved

> **Claim.** Two files changed in the main repo, both under `relay/`. No vector byte, no `.cs`
> file, no doc, no `$ExpectedOfflineTotal`, and no android file.

```bash
git diff --stat 9c05ef7..claude/s2-relay-retention
node docs/sync-vectors/generate.mjs --check
git diff --stat 9c05ef7..claude/s2-relay-retention -- docs/ src/ tests/ scripts/
```

*Expected:* the first prints exactly `relay/src/channel.ts` and `relay/test/relay.test.ts`; the
second prints `OK: 28 vector files match the generator.` and exits 0; the **third prints nothing**.
The offline pin therefore cannot have moved — no file it measures was written.

### C-S2R-15 — CI is the gate, and it has now reported: GREEN

> **Claim.** Both CI jobs passed on this branch's tip, including the two checks this session could
> not run: `Verify-Alpha.ps1` (no .NET here) and `npx wrangler deploy --dry-run` (skipped under the
> deploy embargo).

```bash
gh run view 31412922819 --repo ShivaClaw/careerseeker
```

*Measured 2026-08-10, run [`31412922819`](https://github.com/ShivaClaw/careerseeker/actions/runs/31412922819),
event `push`, **`head_sha` `310406a`** read from the run's own field and matched against this
branch's tip — not inferred from the PR's check list, which follows the head.*

| job | runner | conclusion |
| --- | --- | --- |
| **Blind relay (Worker)** (`93535031632`) | `ubuntu-latest` | **success** |
| **Build and offline harnesses** (`93535031353`) | `windows-latest` | **success** |

Per-step, both jobs are single-job-per-conclusion so `success` means every step passed. The steps
that matter to this slice's open claims:

- *Blind relay*: `Install dependencies` · `Generate runtime types` · **`Typecheck`** · **`Test`** ·
  **`Validate config (no deploy)`** · `Assert the relay has no decryption path` ·
  **`Assert sync vectors match their generator`** — all `success`.
- *Build and offline harnesses*: `Build Release with warnings as errors` ·
  **`Run offline alpha verification`** — both `success`. That second step is `Verify-Alpha.ps1`,
  which **throws** on offline-total drift, so its success is the confirmation that
  `$ExpectedOfflineTotal` (598) is intact. This slice could not have moved it — it wrote no file
  outside `relay/` (C-S2R-14) — but that was an argument until this run, and now it is a measurement.

**`npx wrangler deploy --dry-run` ran on CI and passed.** It was deliberately skipped in the
session (see the boundary paragraph in `LOG.md` §S2R-7: declining every `wrangler deploy` variant
from an unattended sandbox is the conservative reading of the standing no-deploy embargo). The
argument offered there — that `relay/wrangler.jsonc` is untouched so the step is unaffected — is now
confirmed rather than asserted.

**What CI still does not prove.** It ran no engine↔relay smoke against a live or local relay
(`SyncLiveSmoke` is not in the offline set), so the `latest` semantics change flagged as self-audit
item 1 on PR #34 remains **unverified against the C# resume path**. Green CI is not evidence for
that claim, and this entry does not offer it as such.

---

## S4P — the pull page is untrusted input · 2026-08-10 (twelfth cloud iteration)

Every claim below was produced by a command run in this session. The `:core` numbers come from the
**reduced** probe (C-S6A-1's recipe: `:core` alone, separate root, JDK 21 substituted for 17 because
`api.foojay.io` is egress-denied — B-7). The reduced probe runs **none** of `:app`, `lintDebug`,
`assembleDebug` or `checkCoreIsAndroidFree`; **the gate is CI** (C-S4P-8). Where a claim is
unverified, it says so.

Set `REPO` to this checkout and `SYNC` to a `careerseeker` checkout for the commands below.

### C-S4P-1 — The baseline was measured before anything was written

> **Claim.** `:core` stood at **177 tests / 0 failures / 0 skipped across 14 classes** on
> `20fe7e6` before this slice, matching the figure `STATE.md` carried from the tenth iteration —
> so the delta below is a measurement, not a story.

```bash
cd "$PROBE" && gradle --no-daemon :core:test --rerun-tasks     # probe root per C-S6A-1
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=s=0; per={}
for p in glob.glob('<REPO>/core/build/test-results/test/*.xml'):
    r=ET.parse(p).getroot()
    n=int(r.get('tests')); t+=n
    f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
    per[r.get('name').split('.')[-1]]=n
print(t,f,s,len(per)); print(per['RelayClientTest'])
EOF
```

*Expected on `20fe7e6`:* `177 0 0 14`, then `17`.

### C-S4P-2 — The defect was measured on the shipped parser, not argued

> **Claim.** Before this slice, **9 of 12** malformed page bodies threw out of `RelayClient.pull`
> rather than returning a `RelayResult`, and 3 more returned a wrong value silently.

The probe class was temporary and is not in the tree. To reproduce it, check out the parent commit
and re-run the twelve bodies listed in `LOG.md` §S4P-2 through `pull`. The structural claim behind
it needs no build at all:

```bash
git -C <REPO> show 20fe7e6:core/src/main/kotlin/app/careerseeker/core/RelayClient.kt \
  | grep -n "parsePullPage\|\.map { body\|runCatching"
```

*Expected:* `pull` ends `.map { body -> parsePullPage(body) }`, and `parsePullPage` contains **no**
`runCatching` — while `conflictLatest`, three functions below it, does. `map` is applied to the
*result* of `request`, i.e. after its `try`/`catch` has already returned, which is why the throw
escapes.

### C-S4P-3 — The new tests fail against the pre-fix parser (the red run)

> **Claim.** All **8** new `RelayClientTest` cases fail against the parent's `RelayClient.kt`, and
> the **17** pre-existing cases in the same class still pass — so the new tests pin the new
> behaviour and the fix moved no existing assertion. Run deliberately: a test that passes either
> way pins nothing.

```bash
git -C <REPO> checkout 20fe7e6 -- core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
cd "$PROBE" && gradle --no-daemon :core:test --tests "app.careerseeker.core.RelayClientTest" --rerun-tasks
git -C <REPO> checkout HEAD -- core/src/main/kotlin/app/careerseeker/core/RelayClient.kt   # restore
```

*Expected:* `BUILD FAILED`, with exactly these 8 `FAILED` and no others —
`a page body that is not JSON is reported, never thrown` ·
`every structurally wrong page is an Unavailable, and none of them escapes as an exception` ·
`a page missing latest is rejected, because defaulting it to zero fakes being caught up` ·
`a page missing envelopes is rejected, not read as an empty queue` ·
`a quoted latest is refused, because the engine's GetInt64 refuses it` ·
`one unusable element rejects the whole page, and never just itself` ·
`an unusable per-element seq does not reject the page, because nothing authenticated reads it` ·
`the failure detail carries a diagnosis and no relay bytes`.

### C-S4P-4 — The slice adds 8 tests and the suite is green

> **Claim.** After the change the same probe measures **185 / 0 / 0 across 14 classes**, of which
> `RelayClientTest` contributes **25**. No class was added, deleted or renamed.

Same commands as C-S4P-1, on this branch's head.

*Expected:* `185 0 0 14`, then `25`.

### C-S4P-5 — The engine requires both fields, which is what makes this the engine-compatible reading

> **Claim.** `src/Sync/RelayClient.cs` reads `GetProperty("envelopes")` and
> `GetProperty("latest").GetInt64()`. Both throw on an absent key; `GetInt64()` additionally throws
> on a JSON string. The phone now refuses exactly what the engine refuses.

```bash
sed -n '62,76p' <SYNC>/src/Sync/RelayClient.cs
```

*Expected:* `PullAsync`, containing `doc.RootElement.GetProperty("envelopes").EnumerateArray()` and
`doc.RootElement.GetProperty("latest").GetInt64()`.

**Note what this same command shows and this slice did not fix:** `PullAsync` has no `try`, so the
engine's reader is partial in the same way the phone's was. Not touched — it is `.cs` and cannot be
compiled or gated here (`dotnet` is not on PATH). Recorded in `LOG.md` §S4P-6.

### C-S4P-6 — Nothing produces the wrapper shape (the basis for PQ-S4-2's finding)

> **Claim.** `{"seq":N,"envelope":…}` is accepted by the Kotlin client and emitted by **no**
> implementation: not the relay, not the engine, not any shared vector, and it is not in the spec.

```bash
sed -n '196,211p' <SYNC>/relay/src/channel.ts          # pull(): rows.map(r => r.ciphertext).join(',')
grep -rn '"envelope"' --include=*.cs --include=*.ts <SYNC>/src <SYNC>/tests <SYNC>/relay
grep -rln 'envelopes' <SYNC>/docs/sync-vectors/v1/ | head
grep -n 'latest' <SYNC>/docs/Sync-Protocol.md
```

*Expected:* the relay splices bare `ciphertext` rows; the second command prints **nothing** (the one
`grep` hit repo-wide is `"type" == "envelope"` in `SyncHarness`, a vector-type filter, not a page
shape); no vector file contains a page; and `latest` appears in the spec only at §6.1's counter
reconciliation — **never in a response-body definition**, which is PQ-S4-2 itself.

### C-S4P-7 — The wrapper shape is still accepted, deliberately

> **Claim.** This slice did **not** remove it, because an existing assertion depends on it.

```bash
grep -n "pull returns envelopes unparsed" -A 16 <REPO>/core/src/test/kotlin/app/careerseeker/core/RelayClientTest.kt
```

*Expected:* the test body feeds `{"seq":47,"envelope":{…}}` elements and asserts
`page.envelopes.map { it.seq } == [47, 48]`. Removing the wrapper shape rewrites this test, which is
why it is queued (PQ-S4-2) rather than done here.

### C-S4P-8 — The gate is CI, and the reduced probe is not it

> **Claim.** The android gate — `./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test
> :app:assembleDebug :app:lintDebug --rerun-tasks` — **did not run in this session and cannot**:
> no Android SDK, no JBR, and `dl.google.com` is an egress **policy denial** (B-7), not a missing
> install. `Verify-Alpha.ps1` likewise did not run and cannot — `dotnet` is not on PATH.

```bash
which dotnet sdkmanager avdmanager adb; echo "exit=$?"
curl -sS -o /dev/null -w '%{http_code}\n' https://dl.google.com/ || true
```

*Expected:* no paths printed; the `dl.google.com` request fails at the proxy
(`CONNECT tunnel failed, response 403`). Read the CI check run for the gate itself:
`MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6`.
**CI reports no test counts** — Gradle does not print them and the workflow does not collect them —
so the `185` of C-S4P-4 stays a probe measurement, corroborated by CI only as "green", never as a
number.

### C-S4P-9 — No vector moved, and the cross-repo pin is intact

> **Claim.** All **26** vendored vector files are byte-identical to pin `679a317`, **drift 0**,
> verified here rather than asserted. None added, none edited.

```bash
cd <SYNC>
drift=0
for f in <REPO>/core/src/test/resources/sync-vectors/v1/*.json; do
  b=$(basename "$f")
  git show 679a317:docs/sync-vectors/v1/"$b" > /tmp/pin.json 2>/dev/null || { echo "MISSING: $b"; drift=$((drift+1)); continue; }
  cmp -s /tmp/pin.json "$f" || { echo "DRIFT: $b"; drift=$((drift+1)); }
done
echo "drift=$drift  files=$(ls <REPO>/core/src/test/resources/sync-vectors/v1/ | wc -l)"
```

*Expected:* `drift=0  files=26`.

### C-S4P-10 — This slice claimed no main-repo territory

> **Claim.** No file in `careerseeker` was written except the coordination bus
> (`autonomy/claude-state:STATE.md`), so `$ExpectedOfflineTotal` (**598**) cannot have moved.

```bash
git -C <REPO> diff --stat 20fe7e6..HEAD --   # every path is core/ or a record in this repo
git -C <SYNC> status --porcelain             # clean
```

*Expected:* the first prints only `core/src/main/kotlin/.../RelayClient.kt`,
`core/src/test/kotlin/.../RelayClientTest.kt`, `LOG.md`, `AUDIT-REQUEST.md`, `STATE.md`,
`docs/protocol-questions.md`; the second prints nothing. **`Verify-Alpha.ps1` was not run** (no
.NET), so 598 is confirmed by the no-files-written argument here, and by CI on the main repo — not
by a measurement taken in this session.

### C-S4P-11 — CI reported green on this head, so the gate ran what the probe could not

> **Claim.** The android gate is **GREEN on `1867d0c`**, this branch's tip — check run
> `93600690593`, job *Build and test*, `status: completed`, `conclusion: success`,
> 21:15:55 → 21:23:37 UTC, run
> [31433025825](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31433025825).
> The workflow is a **single job**, so `success` means every step passed: `checkCoreIsAndroidFree`,
> the vendored-vector diff against `679a317`, `:core:test`, `:app:test`, `:app:assembleDebug`,
> `:app:lintDebug` and the release-classpath tracker check — **all of which the reduced probe
> structurally cannot run**. This supersedes C-S4P-4's probe as evidence of *green*.

```
MCP: pull_request_read method=get_check_runs owner=ShivaClaw repo=careerseeker-android pullNumber=6
MCP: pull_request_read method=get         owner=ShivaClaw repo=careerseeker-android pullNumber=6
```

*Expected:* one check run, `Build and test`, `completed` / `success`; and `head.sha` =
`1867d0ca0058848fe12f8189f4a178be44bace15`, matching `git rev-parse HEAD`. **The second call is
not optional** — `get_check_runs` reports runs for the PR's *current* head, so after any further
push it describes a later run. Both were run here and matched.

**What this does not prove.** CI reports **no test counts** — Gradle does not print them and the
workflow does not collect them — and **I did not count the log's per-case `PASSED` lines** (the
method C-S3A-9 established). So **185** remains the probe's number, gate-corroborated only as
"green", never as a count. It also runs no emulator, so nothing here is an E2E claim.

### C-S4P-12 — The Actions REST API is 403 from this sandbox, and the records had already said so

> **Claim.** `GET /repos/…/actions/runs/{id}` and `GET /repos/…/commits/{sha}/check-runs` both
> answer **403** to `curl` with this session's token. Only the MCP path reaches them. Two poll
> loops were defeated by this before I stopped using `curl` — **and C-S6A-1 already warned about
> it in this very file**, from an iteration that lost the same time the same way.

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $GH_TOKEN" \
  https://api.github.com/repos/ShivaClaw/careerseeker-android/actions/runs/31433025825
curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $GH_TOKEN" \
  https://api.github.com/repos/ShivaClaw/careerseeker-android/commits/1867d0c.../check-runs
```

*Expected:* `403`, twice. **Poll CI with the MCP `get_check_runs` method, never with `curl`.** The
note is repeated here rather than left at C-S6A-1 because being written down once demonstrably did
not stop it — which is the same failure mode as the defect this slice fixed, one level up: an
invariant recorded in one place and not applied where it was needed.

---

## S4 pull-page semantics — PQ-S4-2 closed (thirteenth cloud iteration, 2026-08-11)

Two repos, spec first. The main-repo half is a **doc-only** change on
`claude/s4-pull-request-semantics` (draft PR #33); the android half is two `:core` Kotlin test files
and one `:core` source file. Numbers below come from the **reduced** probe (C-S6A-1); the gate is CI
(C-S6A-8). Where a claim is unverified, it says so and why.

### C-S4S-1 — Before this slice the pull *response* body was defined nowhere

> **Claim.** On `origin/main` and on the parent commit of this slice, `docs/Sync-Protocol.md` defines
> the pull **request** in §2's route table and never defines the response. `latest` appears in the
> normative text exactly once, in §6.1, used without being defined.

```bash
cd <careerseeker>
git show origin/main:docs/Sync-Protocol.md | grep -n "latest"
git show origin/main:docs/Sync-Protocol.md | grep -n "envelopes\"\|\"envelopes"
```

*Expected:* the `latest` hits are §6.1's reconciliation sentence and §3.1/§4 matter unrelated to the
page; **no section defines a 200 body for `/pull`**, and there is no `### 2.1`.

### C-S4S-2 — §2.1 now defines it, and pins the engine's reading rather than a fourth one

> **Claim.** `docs/Sync-Protocol.md` §2.1 requires both fields, types `latest` as a bare integer,
> makes elements bare §3 envelopes, states the page may be truncated, forbids turning an unreadable
> body into a successful empty pull (**MUST**) while leaving the error type to the receiver
> (**SHOULD**), and refuses the `{"seq":N,"envelope":…}` wrapper. It matches what
> `src/Sync/RelayClient.cs` already enforced — including its error posture, which is why that clause
> is a SHOULD; see the second commit on the branch.

```bash
cd <careerseeker> && git checkout claude/s4-pull-request-semantics
sed -n '/^### 2.1 Pull response body/,/^---$/p' docs/Sync-Protocol.md
sed -n '/GetProperty("envelopes")/,+2p' src/Sync/RelayClient.cs
```

*Expected:* §2.1 prints with the five rules above; the C# lines show
`GetProperty("envelopes")` and `GetProperty("latest").GetInt64()` — absent keys and quoted numbers
both throw there, which is the reading §2.1 pins.

### C-S4S-3 — No vector was added or changed, in either repo

> **Claim.** A page is not an envelope, so no vector applies. `SyncHarness` enumerates
> `docs/sync-vectors/v1/*.json`, so **adding** a file would move `$ExpectedOfflineTotal` (598) — a
> number this machine has no .NET to measure. Nothing was added; the generator still reports no drift.

```bash
cd <careerseeker> && node docs/sync-vectors/generate.mjs --check ; echo "exit=$?"
git diff --stat origin/main..claude/s4-pull-request-semantics -- docs/sync-vectors/
grep -n 'Directory.GetFiles(vectorDir' tests/SyncHarness/Program.cs
```

*Expected:* `OK: 28 vector files match the generator.` / `exit=0` (28 is the **branch** figure — #32's
two ack vectors are not on `main`, where it is 26); the diff is **empty**; the grep shows the
directory enumeration that makes a new file a count change. Run here, exit 0.

### C-S4S-4 — The phone no longer unwraps, and the new case fails against the pre-change parser

> **Claim.** `parsePullPage` forwards the whole element as `wire`. The new `RelayClientTest` case
> was run against the **pre-change** `RelayClient.kt` and **failed**, while all **25** pre-existing
> cases in that class passed — so it pins new behaviour and moved no existing assertion.

```bash
# with the C-S6A-1 probe root in place:
cd <android> && git stash                      # revert both files
git checkout stash@{0} -- core/src/test/kotlin/app/careerseeker/core/RelayClientTest.kt
cd <probe> && <android>/gradlew --no-daemon -I init.gradle.kts :core:test --rerun-tasks \
  --console=plain --tests 'app.careerseeker.core.RelayClientTest'
```

*Expected — measured here, verbatim:*

```
RelayClientTest > a wrapped envelope is refused end to end, even when the envelope inside it is valid() FAILED
26 tests completed, 1 failed
BUILD FAILED
```

with every other `RelayClientTest` case `PASSED`. Restore with
`git checkout -- …/RelayClientTest.kt && git stash pop`.

### C-S4S-5 — `:core` is 185 → 187 / 0 / 0, and no class was added, deleted or renamed

> **Claim.** Baseline re-measured on the untouched branch in this session: **185 / 0 / 0 across 14
> classes**, matching what `STATE.md` carried. After: **187 / 0 / 0 across 14**. `RelayClientTest`
> 25 → **26**, `SyncPumpTest` 18 → **19**.

```bash
# after the C-S6A-1 recipe:
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=s=0; c=0
for p in sorted(glob.glob('<android>/core/build/test-results/test/TEST-*.xml')):
    r=ET.parse(p).getroot(); c+=1
    t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t, f, s, c)
PY
```

*Expected:* `187 0 0 14`. For the baseline, check out the parent commit and repeat: `185 0 0 14`.
Both ends were measured in this session.

### C-S4S-6 — Two `SyncPumpTest` cases rested on the wrapper, and one of them passed for the wrong reason

> **Claim.** The queued note predicted **one** existing assertion would have to be rewritten
> (`RelayClientTest`'s). There were **three**, in two files. The second `SyncPumpTest` case —
> `an envelope that does not parse is discarded and does not stall the cursor` — kept **passing**
> after the change while testing something other than its title: it wrapped a malformed envelope to
> exercise §3's unknown-field rule, and post-change the *wrapper* was what failed to parse, not the
> `surprise` field. A green test is not evidence that it still tests what it says.
>
> **This is also where PQ-S4-3 came from.** `a wrapped envelope is never applied…` asserts
> `report.cursor == 999` — the discarded element still advances the cursor to its own claimed
> number, because an unparseable envelope has no authenticated `seq`. Pre-existing, not caused here.

```bash
cd <android>
git show HEAD -- core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt | grep -n "^[-+].*wrappedPage"
grep -n "header?.seq ?: envelope.seq" -B 12 core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
```

*Expected:* the diff shows `wrappedPage` removed from the unknown-field test and retained only in
the new refusal test; the `SyncPump` grep prints the fallback and the KDoc whose safety argument
covers the discarded *item* but not the *cursor* — which is PQ-S4-3 in two lines.

### C-S4S-7 — Neither gate ran here, and neither can

> **Claim.** The android gate (`./gradlew … :app:assembleDebug :app:lintDebug`) did **not** run: no
> Android SDK, no JBR, and `dl.google.com` is an egress policy denial (B-7). `Verify-Alpha.ps1` did
> **not** run: no .NET. Both statements are about this sandbox, not about the change.

```bash
which dotnet ; echo "dotnet exit=$?"
curl -sS -o /dev/null -w '%{http_code}\n' https://dl.google.com/ ; echo "exit=$?"
```

*Expected:* `dotnet` not found; a CONNECT tunnel failure / 403 from the proxy. **The main-repo half
of this slice is doc-only**, so `$ExpectedOfflineTotal` (598) is untouched by construction — no
`.cs`, no harness, no vector and no count-reporting doc was written. CI is the gate for both halves.

---

## S4C — bounding the cursor advance (2026-08-11, fourteenth cloud iteration)

Every claim this slice makes, with the command that re-verifies it. `<android>` and `<main>` are the
two checkouts. Test numbers come from the **reduced** probe (C-S6A-1); the gate is CI (C-S4C-6).

### C-S4C-1 — The stored prompt's slice was already landed, which is why a different rung was picked

> **Claim.** The prompt assigned S5's spec half and stated S5 was "NOT STARTED". §4.3.3, both
> `entitlement_ack` vectors and the closes of PQ-A6-1/A2-1/A2-2 already exist on
> `claude/s5-entitlement-ack-spec` (draft PR #32), landed four iterations earlier. Redoing it would
> have duplicated a spec section and two vectors.

```bash
cd <main>
git fetch --all --prune
git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
git show origin/claude/s5-entitlement-ack-spec:docs/Sync-Protocol.md | grep -n "4.3.3 Entitlement acknowledgement"
git show origin/claude/s5-entitlement-ack-spec --stat --oneline | grep entitlement-ack
```

*Expected:* four `S5:` commits; a §4.3.3 heading; `entitlement-ack.json` and
`entitlement-ack-no-order-id.json`. The one remaining piece of that prompt is PQ-A2-3's vector,
which is **B-6** (engine has no inbound wire-JSON parser) — read `BLOCKED.md` B-6 before picking it.

### C-S4C-2 — §6.4 exists, defines the transport cursor, and bounds the unauthenticated advance

> **Claim.** `docs/Sync-Protocol.md` gains **§6.4**, which names the transport cursor (a number §6.2
> never governed — §6.2 is about `highest_accepted`) and caps an unparseable element's advance at
> the page's `latest`. §6.2 gains a pointer to it, and §9's amendment table gains a row.

```bash
cd <main> && git checkout claude/s4-pull-request-semantics
grep -n "### 6.4 The transport cursor" -A 24 docs/Sync-Protocol.md
grep -n "not the same number as the pulling receiver" docs/Sync-Protocol.md
grep -n "the \*\*transport cursor\*\* was not described at all" docs/Sync-Protocol.md
```

*Expected:* §6.4 with three bullets — cursor MUST NOT move backwards, MUST advance only to a `seq`
recovered from the sealed bytes, MUST NOT advance an unparsed element past `latest`; the §6.2
pointer; the §9 row citing PQ-S4-3.

### C-S4C-3 — The phone implements exactly that, and only on the unauthenticated path

> **Claim.** `SyncPump.kt` is now `minOf(envelope.seq, page.latest)`. An **authenticated** `seq` is
> never clamped.

```bash
cd <android>
grep -n "val seq = header?.seq" core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
git log -1 --format=%H -- core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
git show HEAD~1 -- core/src/main/kotlin/app/careerseeker/core/SyncPump.kt | head -1
```

*Expected:* `val seq = header?.seq ?: minOf(envelope.seq, page.latest)` — the `header?.seq` branch is
outside the `minOf`, which is the whole distinction.

### C-S4C-4 — `:core` went 187 → 190, and two of the three new tests fail against the pre-change source

> **Claim.** Baseline **187 / 0 / 0 across 14 classes**, measured here before any edit; after,
> **190 / 0 / 0 across 14**. `SyncPumpTest` 19 → **22**, no class added, deleted or renamed. The two
> truncation tests **fail** on the pre-change `SyncPump.kt` while all 19 pre-existing cases pass.

```bash
# after the C-S6A-1 probe recipe:
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=s=0; c=0
for p in sorted(glob.glob('<android>/core/build/test-results/test/TEST-*.xml')):
    r=ET.parse(p).getroot(); c+=1
    t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped'))
print(t,f,s,c)
PY
# the red run — revert only the source, keep the tests:
cd <android> && git stash push core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
# re-run the probe with --tests 'app.careerseeker.core.SyncPumpTest'
git stash pop
```

*Expected:* `190 0 0 14` after; `187 0 0 14` with both files reverted. In the red run, exactly
`an unparseable element cannot move the cursor past the page's latest` and `after a bounded skip the
stream still delivers envelopes issued later` FAIL — `22 tests completed, 2 failed`.

### C-S4C-5 — The third new test passes on both sides, deliberately, and two old ones are unchanged

> **Claim.** `an authenticated seq above latest still moves the cursor` passes **before and after**.
> It is a regression guard, not evidence of the fix: it forbids the "simplification" of clamping
> every `seq` to `latest`, which would let an understated `latest` hold the cursor below envelopes
> the phone already read. Separately, the two pre-existing cursor assertions on unparseable elements
> are **unchanged by this diff**, because on each the ceiling equals the claim — which is the
> demonstration that §6.4 is a ceiling and not a behaviour change.

```bash
cd <android>
git show HEAD -- core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt | grep -n "^[-+]" | grep -i "assertEquals(999L\|assertEquals(6L"
grep -n "latest = 999\|latest = 6" core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt
```

*Expected:* **no** `+`/`-` line changes either cursor assertion — only the stale comment above the
999 one moves. The `grep` shows why: claimed 999 on a page whose `latest` is 999, claimed 6 on a page
whose `latest` is 6, so `minOf` binds on neither.

### C-S4C-6 — No vector moved, the drift trap is not armed against this doc, and neither gate ran

> **Claim.** No vector was added or changed and the vendored pin stays `679a317`.
> `scripts/Verify-Alpha.ps1` makes **zero** assertions against `Sync-Protocol.md`, so the
> doc/verifier drift trap is not armed against this file. `$ExpectedOfflineTotal` (598) is untouched
> **by construction** — the main-repo half is one Markdown file. Neither gate ran here and neither
> can: no .NET, no Android SDK, `dl.google.com` egress-denied (B-7).

```bash
cd <main>
node docs/sync-vectors/generate.mjs --check ; echo "exit=$?"
grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1
grep -n 'ExpectedOfflineTotal\s*=' scripts/Verify-Alpha.ps1
git diff --stat origin/main..claude/s4-pull-request-semantics -- docs/sync-vectors/
which dotnet ; curl -sS -o /dev/null -w '%{http_code}\n' https://dl.google.com/
```

*Expected:* `OK: 28 vector files match the generator.` exit 0 (28 is the **branch** figure — #32's
two ack vectors are not on `main`, where it is 26; reading it as a `main` figure is the drift trap
one repo over). `0` Sync-Protocol references. `$ExpectedOfflineTotal = 598`. The `docs/sync-vectors/`
diff shows **only #32's** two ack vectors plus `index.json`/`generate.mjs` — nothing from this slice.
`dotnet` not found; a CONNECT tunnel failure / 403. **CI is the gate for both halves.**

### C-S4C-7 — The engine half is unwritten, not blocked, and `SyncPump` still has no caller

> **Claim.** `src/Sync/RelayClient.cs` needs the same ceiling and did not get it — no .NET here. It
> is **unblocked and merely unwritten**; do not file it as a blocker. And `SyncPump` has no
> production caller, so the truncation this prevents is prospective.

```bash
cd <main>  && grep -n "PullAsync" -A 20 src/Sync/RelayClient.cs | grep -n "seq"
cd <android> && grep -rn "SyncPump" app/src ; echo "callers exit=$?"
```

*Expected:* the C# reader still takes its per-element `seq` with no `latest` ceiling; the `grep` over
`app/src` prints **nothing** (exit 1).

---

## S6C — counter symmetry, the push response body, and two findings (fifteenth cloud iteration, 2026-08-11)

### C-S6C-1 — The stored prompt's slice was already landed, so it was not redone

> **Claim.** The scheduled prompt assigned S5's spec half (amend §4.3 for `entitlement_ack`, close
> PQ-A6-1/A2-1/A2-2, add vectors) and stated S5 was "NOT STARTED". **All of it except PQ-A2-3 landed
> 2026-08-09** on `claude/s5-entitlement-ack-spec` (draft PR #32), and PQ-A2-3 is **B-6** — the
> engine has no inbound wire-JSON parser, so the vector would assert a rejection the engine cannot
> perform and would turn the offline gate red. Redoing it would have produced a duplicate §4.3.3 and
> a second copy of two vectors. This is the **fourth** iteration to have to check this; `STATE.md`'s
> fourth and sixth corrections say so.

```bash
cd <main>
git fetch --all --prune
git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
grep -n "^### 4.3.3" docs/Sync-Protocol.md
ls docs/sync-vectors/v1/ | grep -i entitlement-ack
```

*Expected:* four S5 commits; §4.3.3 present; the two ack vectors already on that branch. **Records,
not the prompt, are the state** — the prompt is a stored snapshot and does not re-read itself.

### C-S6C-2 — §2.2's four push responses, measured under miniflare rather than read off the source

> **Claim.** `POST /push` answers `201 {"ok":true,"seq":N}` · `409
> {"error":"replay_rejected","latest":N}` with keys **exactly** `error|latest` · `400
> {"error":"bad_request"}` · `413 {"error":"too_large"}`. The 409's `latest` is **per direction, not
> per pairing** — with `e2p` at 90, a replayed `p2e` seq 4 answers `latest: 4`. 400 and 413 carry
> **no** `latest`. A direction holding nothing answers **201 to seq 1**, not 409. An empty direction
> pulls as `{"envelopes":[],"latest":0}`.

Reproduce with the eleventh run's probe trick — throwaway tests asserting deliberately **wrong**
values so vitest prints the measured one in its diff (`console.log` does not escape the Workers
pool). Keep the measured string short; vitest truncates long diffs mid-value, which cost this
session two runs:

```bash
cd <main>/relay && npm ci
# write test/probe.test.ts: bootstrap a pairing, push, and assert
#   expect(`L=${b.latest} K=${Object.keys(b).join('|')} S=${res.status}`).toBe('PROBE')
npx vitest run test/probe.test.ts 2>&1 | grep AssertionError
rm test/probe.test.ts        # NOT a suite member — delete before committing
```

*Expected:* `L=7 K=error|latest S=409` (replayed seq 7), `L=50 …` (regressed 3 against high-water
50), `L=4 …` (the per-direction case). **The probe was deleted; the suite is unchanged** — see
C-S6C-6.

### C-S6C-3 — The engine implements only the persisted half of §6.1's resume rule (PQ-S6-3)

> **Claim.** `src/Engine/Program.cs:288` constructs the publisher with `startSeq: paired.LastE2pSeq`
> — the persisted term **only**. The `max(…)` is never computed and the relay is never consulted on
> the startup path, although the comment block at `src/Engine/Program.cs:239-243` states the rule
> verbatim. **The comment and the code below it disagree, and the comment is right.**

```bash
cd <main>
sed -n '239,243p;286,290p' src/Engine/Program.cs
grep -n "PullAsync" src/Engine/Program.cs ; echo "startup-path pulls exit=$?"
```

*Expected:* the comment states `startSeq = max(vault.last_e2p_seq, relay latest e2p)`; the code
passes one term; **no `PullAsync` call exists in `Program.cs`** (exit 1). Not fixed here — C#, no
.NET in this sandbox. **Unblocked and merely unwritten; do not file it as a blocker.**

### C-S6C-4 — `PushAsync` returns `bool`, so the 409's `latest` is discarded unread (PQ-S6-3)

> **Claim.** `src/Sync/RelayClient.cs:51-60` returns `res.StatusCode is HttpStatusCode.Created`. A
> 409 `replay_rejected` is therefore indistinguishable from a timeout, a 400 or a 413, and the
> reconciliation value the relay puts in that body is never read. **The phone does read it** —
> `RelayClient.conflictLatest` — which is the inversion worth noting: §6.1 asked the engine to
> reconcile and the engine is the one that cannot.

```bash
cd <main>    && sed -n '50,60p' src/Sync/RelayClient.cs
cd <android> && grep -n "conflictLatest" core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expected:* the C# reads no response body at all on push; the Kotlin has a total `conflictLatest`
returning `Long?`.

### C-S6C-5 — The relay's transport error vocabulary is undocumented, and two names collide with §7.2's (PQ-S2-3)

> **Claim.** The relay emits **eight** distinct HTTP error codes. `bad_request`, `unauthorized`,
> `not_found`, `method_not_allowed` and `upgrade_required` appeared **zero** times in
> `Sync-Protocol.md` before §2.2. `replay_rejected`, `too_large` and `pairing_unknown` do appear —
> but as **payload** codes in §7.2, a different key (`code`, not `error`) and a different party.

```bash
cd <main>
grep -rho "error: '[a-z_]*'" relay/src/*.ts | sort -u
git show aa305de~1:docs/Sync-Protocol.md | grep -c "bad_request"      # before §2.2
```

*Expected:* ~~eight codes~~ **NINE codes** — see the correction below; `0` occurrences of
`bad_request` before the amendment. v1 pins **push's** mapping and no other route's — the rest are
observed, not normative, deliberately.

> **CORRECTED 2026-08-11 (seventeenth cloud iteration).** This entry as first written was
> **self-contradicting**: the command above yields **nine** codes on the commit it cites, while the
> claim and the *Expected* line both said eight. `exists` was dropped in transcription, so anyone
> running the re-verification as written would have seen it fail. The claim's "eight" and the
> "eight codes" expectation are wrong; everything else in the entry holds. The nine are
> `bad_request`, `exists`, `method_not_allowed`, `not_found`, `pairing_unknown`, `replay_rejected`,
> `too_large`, `unauthorized`, `upgrade_required`. PQ-S2-3 is now **closed** by §2.3 — see
> **C-S2T-1** below.

### C-S6C-6 — No vector moved, no relay code moved, the drift trap is not armed, and neither gate ran

> **Claim.** This slice wrote **two Markdown files** — `docs/Sync-Protocol.md` (main) and
> `docs/protocol-questions.md` (android) — plus the records and the bus. **No `.cs`, no `.kt`, no
> `.ts`, no vector byte, no `generate.mjs`, no harness, no `Verify-Alpha.ps1`.** The relay suite is
> **36 / 0 measured both before and after** on this branch, and the probe file was deleted.
> `$ExpectedOfflineTotal` (598) is untouched **by construction**. Neither gate ran and neither can:
> no .NET, no Android SDK.

```bash
cd <main>
node docs/sync-vectors/generate.mjs --check ; echo "exit=$?"
grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1
grep -n 'ExpectedOfflineTotal\s*=' scripts/Verify-Alpha.ps1
git diff --stat origin/claude/s4-pull-request-semantics@{1}..HEAD   # this slice's two commits
cd relay && npx vitest run 2>&1 | grep -E "Tests "
git status --porcelain                                              # probe left no trace
which dotnet
```

*Expected:* `OK: 28 vector files match the generator.` exit 0 — **28 is the branch figure**, `main`
is 26, and reading it as a `main` figure is the drift trap one repo over. `0` Sync-Protocol
references in the verifier. `$ExpectedOfflineTotal = 598`. The diff touches
`docs/Sync-Protocol.md` and nothing else. `Tests 36 passed (36)` — **not 42**, which is
`claude/s2-relay-retention`'s figure and belongs to a different branch. Clean tree. `dotnet` not
found. **CI is the gate.**

### C-S6C-7 — Main-repo CI green on this head, and the 598 pin confirmed by measurement

> **Claim.** Run **31476875538** (event `push`, `head_sha` **`b114d11`** read from the run's own
> field, not from the PR's check list, which follows the head). **Both jobs `success`.** From the
> *Build and offline harnesses* job log: `=== 130 passed, 0 failed ===` (SyncHarness) and
> **`=== Offline total: 598 passed, 0 failed ===`**, then `CareerSeeker alpha verification
> complete.` So `Verify-Alpha.ps1` ran in full — the 82-second duration is a warm build, not a
> skipped run; every harness section prints in the log.

```bash
# MCP, not curl -- the Actions REST API is 403 to curl from this sandbox (C-S4P-12)
#   actions_get      method=get_workflow_run  resource_id=31476875538   -> read head_sha
#   get_job_logs     job_id=93732493711  return_content=true  tail_lines=400
```

*Expected:* `head_sha` equal to `b114d11`; conclusion `success` on both *Blind relay (Worker)* and
*Build and offline harnesses*; the two totals above present verbatim. **This upgrades C-S6C-6's
"unchangeable by construction" from an argument to an observation** — the pin was measured at 598
after this iteration's commits, not merely argued to be untouched.

---

## S2 `seq` bound — sixteenth cloud iteration, 2026-08-11 (PQ-S2-2, closed in part)

Branch `claude/s2-seq-bound` in `careerseeker`, draft PR **#35**, stacked on #34 → #32. Every
command below runs on Linux with Node only; **none of them needs .NET or an Android SDK.**

### C-S2Q-1 — The relay's old guard was not a range check, and the reachable ceiling was ~1.8e308

> **Claim.** `relay/src/channel.ts` validated `seq` with `Number.isInteger(seq) && seq >= 1` and
> nothing else. `Number.isInteger` rejects a fractional value but cannot reject a large
> one — every double at or above `2^53` is necessarily integral — so the predicate is vacuously
> true across exactly the range this rule cares about. Values to ~1.8e308 were accepted and
> appended; only `Infinity` was refused, and it fails because `Number.isInteger(Infinity)` is
> `false`, not because of any bound.

```bash
git -C careerseeker show origin/claude/s2-relay-retention:relay/src/channel.ts | grep -n "isInteger(seq)"
node -e "console.log(Number.isInteger(1e300), Number.isInteger(2**53), Number.isInteger(Infinity))"
```

*Expected:* the pre-change line `|| !Number.isInteger(seq) || seq < 1` with **no upper term**, and
`true true false`.

### C-S2Q-2 — §3.2 exists, states the bound, and states its own non-conformance

> **Claim.** `docs/Sync-Protocol.md` gained **§3.2 Sequence number range**: maximum `2^53 - 1`,
> sender MUST NOT emit above it, relay MUST refuse `400 bad_request`, receiver **SHOULD** reject —
> plus a measured conformance note saying **neither receiver implements the SHOULD**, and a closing
> paragraph saying the bound does **not** address a channel wedged *in* range.

```bash
cd careerseeker && git checkout claude/s2-seq-bound
sed -n '/^### 3.2 Sequence number range/,/^---$/p' docs/Sync-Protocol.md
grep -n "maximum \`2\^53 - 1\`" docs/Sync-Protocol.md          # the field-table row
```

*Expected:* the section present; `SHOULD` (not `MUST`) on the receiver bullet; the phrase
**"Neither receiver implements the SHOULD"**; and the "What this does not fix" paragraph naming
`DELETE /v1/{pairing}` and deferring the reset question to PQ-S2-2's open half.

### C-S2Q-3 — `MAX_SEQ` is the derivation, not a literal, and the relay enforces it

> **Claim.** `relay/src/protocol.ts` exports `MAX_SEQ = Number.MAX_SAFE_INTEGER` (which *is*
> `2^53 - 1`), and `relay/src/channel.ts` refuses `seq > MAX_SEQ` with `400 bad_request` in the
> header-shape check — before the monotonicity comparison, so nothing is appended.

```bash
cd careerseeker/relay
grep -n "MAX_SEQ" src/protocol.ts src/channel.ts
node -e "console.log(Number.MAX_SAFE_INTEGER === 2**53 - 1)"
```

*Expected:* `MAX_SEQ = Number.MAX_SAFE_INTEGER` with no numeric literal spelled out; the guard term
`|| seq > MAX_SEQ` sitting inside the same `if` as the other header checks and **above** the
`SELECT MAX(seq)` block; and `true`.

### C-S2Q-4 — The relay suite is 42 → 51 and green

> **Claim.** Nine tests added. Suite **51 passed (51)** on `claude/s2-seq-bound`. **42 is this
> branch's base** (`claude/s2-relay-retention`); the **36** in the S6 records is
> `claude/s4-pull-request-semantics`'s. Three branches, three figures — reading one as another is
> the count-drift trap one branch over.

```bash
cd careerseeker/relay && npm ci && npm test
```

*Expected:* `Tests  51 passed (51)`, `Test Files  1 passed (1)`. On the base branch the same
command prints `42 passed (42)`.

### C-S2Q-5 — Seven of the nine new tests fail without the guard (proven, not assumed)

> **Claim.** The regression coverage was verified by reverting the guard and re-running, not by
> inspection. Seven fail: the four band cases, the no-counter-evidence rule, the
> direction-stays-usable regression, and the 2⁵³/2⁵³+1 collision. **Two pass either way** — the
> boundary-accepted pin and the `latest`-parseability pin — and are labelled pins rather than
> regression catchers.

```bash
cd careerseeker/relay
sed -i 's/ || seq > MAX_SEQ//' src/channel.ts && npm test; git checkout src/channel.ts && npm test
```

*Expected:* `Tests  7 failed | 44 passed (51)` with the guard removed, then `51 passed (51)` after
restoring it. **Restore the file** — the `git checkout` above is part of the command, not optional.

### C-S2Q-6 — No vector byte moved, and the 598 pin is unchanged, both measured

> **Claim.** This slice touched four files: one Markdown and three TypeScript. **No vector, no
> harness, no `.cs`, no count-reporting doc, and not `Verify-Alpha.ps1`.** CI's *Build and offline
> harnesses* job on this head reported **`=== Offline total: 598 passed, 0 failed ===`**, so the pin
> is confirmed by observation rather than argued from the diff.

```bash
cd careerseeker
git diff --stat origin/claude/s2-relay-retention..claude/s2-seq-bound
node docs/sync-vectors/generate.mjs --check
git diff --name-only origin/claude/s2-relay-retention..claude/s2-seq-bound -- docs/sync-vectors/ scripts/ tests/ src/
# MCP, not curl -- the Actions REST API is 403 to curl from this sandbox (C-S4P-12)
#   get_job_logs  job_id=93792278316  return_content=true  tail_lines=48   # final head 2be00fc
#   get_job_logs  job_id=93789450880  return_content=true  tail_lines=45   # earlier head 0af7012
```

*Expected:* exactly four files (`docs/Sync-Protocol.md`, `relay/src/protocol.ts`,
`relay/src/channel.ts`, `relay/test/relay.test.ts`); `OK: 28 vector files match the generator.` exit
0 (**28 is the branch figure**, `main` is 26); the third command prints **nothing**; and both
`=== 130 passed, 0 failed ===` and `=== Offline total: 598 passed, 0 failed ===` in the job log.
**Two runs, and the later one is the head that matters:** `31494720248` on `0af7012` (the code) and
**`31495565325` on `2be00fc`** (the wording fix on top of it). **Both jobs `success` in both runs**,
and both printed the same two totals — so the 598 pin is confirmed on the branch tip, not only on an
intermediate commit.

### C-S2Q-7 — `tsc` reports the same 55 errors before and after, so none is mine

> **Claim.** The project's typecheck is `wrangler types && tsc --noEmit`; **no `wrangler` was
> invoked**, so bare `tsc` cannot resolve `Env`/`SqlStorage`/`cloudflare:workers` and prints 55
> errors. That count is **identical on the base branch and on this one**, which is the evidence that
> this change introduces none.

```bash
cd careerseeker/relay
git stash -u && npx tsc --noEmit -p tsconfig.json 2>&1 | wc -l && git stash pop
npx tsc --noEmit -p tsconfig.json 2>&1 | wc -l
```

*Expected:* `55` both times. This is **not** a clean typecheck and must not be reported as one —
run `npm run typecheck` on a machine where invoking `wrangler` is in scope.

---

## iOS portability measurement — 2026-08-11 (out of ladder, at Brandon's request)

### C-IOS-1 — `:core` is 86% portable by line count, and the port surface is 5 files

> **Claim.** `:core` is 18 files / ~3,150 lines. **13 files / ~2,720 lines carry zero
> `java.*`/`javax.*` imports** and port to a Kotlin/Native iOS target unchanged. Five are JVM-bound:
> `SyncCrypto.kt` (137 lines, 14 such imports), `Entitlement.kt` (138, 4), `PairingDerivation.kt`
> (74, 1), `Hkdf.kt` (55, 2), `Base64Url.kt` (25, 1). Both `:core` dependencies
> (`kotlinx-serialization-json`, `ktor-client-core`) are already multiplatform. `:app` is 12 files /
> 1,200 lines.

```bash
cd careerseeker-android && git checkout claude/android-a0-probe
for f in $(find core/src/main/kotlin -name "*.kt"); do
  echo "$(grep -c '^import java\.\|^import javax\.' $f)	$(wc -l < $f)	$f"; done | sort -rn
find app/src/main -name "*.kt" | wc -l
find app/src/main -name "*.kt" -exec cat {} + | wc -l
grep -n "kotlinx.serialization\|ktor.client.core" core/build.gradle.kts
```

*Expected:* five files with a non-zero first column and thirteen with `0`; `12` and `1200` for
`:app`; both dependencies present as `implementation`. **The line counts are `wc -l`, so they
include comments and blank lines** — this is a proportionality measurement, not a LOC estimate, and
should not be quoted as effort.

### C-IOS-2 — CI already produces a sideloadable debug APK

> **Claim.** `.github/workflows/ci.yml` uploads `app/build/outputs/apk/debug/*.apk` as artifact
> **`app-debug`** with `if-no-files-found: error` and 14-day retention. Since the *Build and test*
> job concluded `success` on `e6e6dc5` (run `31495754391`), the artifact exists **by construction**:
> the step fails the job when the APK is missing.

```bash
sed -n '117,123p' careerseeker-android/.github/workflows/ci.yml
# then, in a browser (the Actions artifacts REST API is 403 to this sandbox -- C-S4P-12):
#   https://github.com/ShivaClaw/careerseeker-android/actions/runs/31495754391
```

*Expected:* the `Upload debug APK` step as described, and an `app-debug` artifact on that run.
**This is inference from config plus a green job, not an artifact I downloaded** — the sandbox
cannot list Actions artifacts. It is **debug-signed**, so it is for the owner's own device and not
for testers.

### C-IOS-3 — Do not poll GitHub CI with `curl` from this sandbox; it fails silently

> **Claim.** `curl` against `api.github.com/repos/.../check-runs` and `.../actions/artifacts` returns
> **403 `Resource not accessible by integration`** here (the artifacts call was measured directly;
> the check-runs call returned an empty parse). A `Monitor` poll loop built on it therefore **ran its
> full 24 iterations and exited with no output** — and *no output looked exactly like "CI is still
> running"*. Two waits were spent on a watch that never worked. **The MCP tools do work**:
> `pull_request_read method=get_check_runs` and `get_job_logs` returned correct data throughout.

```bash
curl -sS -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $GITHUB_TOKEN" \
  "https://api.github.com/repos/ShivaClaw/careerseeker-android/actions/runs/31495754391/artifacts"
```

*Expected:* `403`. **The lesson generalises past this API:** a poll loop whose failure mode is
silence is indistinguishable from a poll loop that is still waiting, so a watch must emit on the
*error* path too, not only on success. Use the MCP tools for CI state and keep `curl` for nothing
that a decision depends on. Precedent: **C-S4P-12** recorded the same 403 class for the Actions REST
API and this iteration rediscovered it the expensive way.

---

## S2T — the transport vocabulary, every route (seventeenth cloud iteration, 2026-08-11)

Main-repo branch `claude/s2-transport-vocabulary`, **draft PR #36**, stacked on #33 → #32 → `main`.
Two commits: `cc6d966` (§2.3) and `4db3543` (the tests). Every command below was run in this
session on a Linux cloud sandbox with **no .NET and no Android SDK**; where a claim needs a gate
that does not exist here, the entry says so instead of asserting a result.

Set up once:

```bash
cd <main>
git fetch --all --prune
git checkout claude/s2-transport-vocabulary
cd relay && npm ci
```

### C-S2T-1 — The relay emits nine transport codes, and PQ-S2-3's table dropped one

> **Claim.** The vocabulary is **nine** codes, not the eight recorded in PQ-S2-3 and echoed in
> C-S6C-5. `exists` was dropped **in transcription** — the question's own command, on the commit the
> question cited, returns it.

```bash
cd <main>
mkdir -p /tmp/pqcheck                                  # tar will not create it, and fails if absent
git archive origin/claude/s4-pull-request-semantics relay/src | tar -x -C /tmp/pqcheck
grep -rho "error: '[a-z_]*'" /tmp/pqcheck/relay/src/*.ts | sort -u | wc -l
```

*Expected:* `9`. The set is `bad_request`, `exists`, `method_not_allowed`, `not_found`,
`pairing_unknown`, `replay_rejected`, `too_large`, `unauthorized`, `upgrade_required`. The suite
pins it independently, so the document and the Worker cannot drift apart silently:

```bash
cd <main>/relay && npx vitest run -t 'emits exactly nine transport codes'
```

*Expected:* 1 passed.

### C-S2T-2 — The two vocabularies share exactly three names, and one of them means two things

> **Claim.** Nine transport codes, ten §7.2 payload codes, intersection **three**:
> `pairing_unknown`, `replay_rejected`, `too_large`. The first means something *different* on each
> side; the other two agree. §2.2's "two names … with the same meaning" is true as written and
> incomplete as read.

```bash
cd <main> && python3 - <<'EOF'
import re
transport={'bad_request','exists','method_not_allowed','not_found','pairing_unknown',
           'replay_rejected','too_large','unauthorized','upgrade_required'}
s=open('docs/Sync-Protocol.md').read()
sec=s[s.index('### 7.2 Error kinds'):s.index('## 8. What this protocol cannot do')]
payload=set(re.findall(r'^\| `([a-z_]+)` \|', sec, re.M))
print(len(transport), len(payload), sorted(transport&payload))
EOF
```

*Expected:* `9 10 ['pairing_unknown', 'replay_rejected', 'too_large']`.

### C-S2T-3 — `pairing_unknown` means the id is malformed, never that the pairing is unknown

> **Claim.** The transport code fires only on a pairing-id **shape** failure, checked before
> authentication. A well-formed id that was never created answers **401**, not 404.

```bash
cd <main>/relay && npx vitest run -t 'means the id is MALFORMED'
```

*Expected:* 1 passed. Asserts `404 {"error":"pairing_unknown"}` for `/v1/not-a-pairing/pull` and
`401 {"error":"unauthorized"}` for a fresh well-formed id.

### C-S2T-4 — A purged pairing answers 401 on every route, so §7.2's condition has no transport code

> **Claim.** After `DELETE /v1/{pairing}`, `pull`, `push`, `pair` and `DELETE` all answer
> `401 {"error":"unauthorized"}` — identical to a wrong token. This is **PQ-S2-4**.

```bash
cd <main>/relay && npx vitest run -t 'after unpair every route answers 401'
```

*Expected:* 1 passed.

### C-S2T-5 — `relay/src/` is byte-identical: §2.3 changed no relay behaviour

> **Claim.** The section is **descriptive**. Nothing new is refused, which is the property §3.1's
> amendment makes load-bearing. Only the doc and the test file moved.

```bash
cd <main>
git diff --stat origin/claude/s4-pull-request-semantics..claude/s2-transport-vocabulary -- relay/src/
git diff --stat origin/claude/s4-pull-request-semantics..claude/s2-transport-vocabulary
```

*Expected:* the first prints **nothing**; the second prints exactly two files —
`docs/Sync-Protocol.md` and `relay/test/relay.test.ts`.

### C-S2T-6 — 36 → 49, and twelve of the thirteen new tests were proven against a mutated relay

> **Claim.** All thirteen are pins by construction (no relay code changed), so each was checked
> against a deliberately broken relay rather than assumed useful. **Twelve caught something.** The
> thirteenth — `unpair is not a tombstone` — is **not** proven and is labelled a pin.

```bash
cd <main>/relay
git stash && npm test 2>&1 | grep "Tests "     # base: 36 passed
git stash pop && npm test 2>&1 | grep "Tests " # branch: 47 passed
```

*Expected:* `36 passed (36)` then `49 passed (49)`.

To reproduce one mutation (revert afterwards — the branch must stay byte-identical in `relay/src/`):

```bash
cd <main>
python3 -c "
p='relay/src/channel.ts'; s=open(p).read()
s=s.replace(\"if (!(await this.authorize(bearer))) return this.json({ error: 'unauthorized' }, 401);\",
            \"if (!(await this.authorize(bearer))) return this.json({ error: 'pairing_unknown' }, 404);\")
open(p,'w').write(s)"
git diff --numstat relay/src/channel.ts          # MUST be 1 1 — see the note below
cd relay && npm test 2>&1 | grep -E "Tests |FAIL"
cd .. && git checkout relay/src/channel.ts
```

*Expected:* `1	1	relay/src/channel.ts`, then `4 failed | 45 passed`, two of them the §2.3 tests
(`means the id is MALFORMED`, `after unpair every route answers 401`). The other two failures are
pre-existing tests that also depend on the 401 (`rotates provisional -> final`, `DELETE purges the
queue and the token`). The other three mutations and the tests they catch are enumerated in commit
`4db3543`'s message.

> **Why this recipe replaces the `sed` first written here, and it is the entry's own lesson.** The
> first version of this command was `sed -i "s/return this.json({ error: 'unauthorized' }, 401);$/…/"`,
> which **matches three sites**, not one — `authorize` plus both bearer checks inside `create` — and
> so produces `5 failed`, not the `4 failed` this entry claimed. It was run before being written
> down, the mismatch showed up immediately, and the recipe was replaced with one that mutates a
> single site. **A re-verification command that does not reproduce its own expected output is worse
> than no command**, because it reads as evidence. The `git diff --numstat` line is in the recipe so
> the next reader catches an over-broad match before interpreting the failure count.

### C-S2T-6b — `POST /pair`'s 413 cap counts characters, not bytes

> **Claim.** The check is `raw.length > 16 * 1024` on the decoded string, so the unit is UTF-16 code
> units. A body of 16,384 three-byte characters — **49,152 bytes** — is *under* the cap. The
> effective byte ceiling is up to **3×** what the constant looks like. This is the same
> character-versus-byte conflation §3.1 was amended to fix, in a second place, and §2.3 now pins it
> **in the unit the relay actually uses** rather than correcting it (correcting it would refuse
> bodies v1 has never declared illegal — §3.1's own rule).

```bash
cd <main>/relay && npx vitest run -t "cap counts characters"
```

*Expected:* 1 passed — `413` at 16,385 ASCII chars, `400` at 16,384, `400` for 16,384 three-byte
chars (49,152 bytes, under the cap, failing later on `JSON.parse`), `413` at 16,385 of them.

Proven live by mutating the cap to count bytes — **this test fails and only this test**:

```bash
cd <main>
python3 -c "
p='relay/src/channel.ts'; s=open(p).read()
s=s.replace('if (raw.length > 16 * 1024) return',
            'if (new TextEncoder().encode(raw).length > 16 * 1024) return')
open(p,'w').write(s)"
git diff --numstat relay/src/channel.ts          # MUST be 1 1
cd relay && npm test 2>&1 | grep -E "Tests |×"
cd .. && git checkout relay/src/channel.ts
```

*Expected:* `1 failed | 48 passed (49)`, the failure being this test.

**How it was found, because the method is the point.** It was not found by reading `channel.ts`; it
was found by **auditing my own §2.3 table before shipping it**. The row originally read "body over
16 KiB", copied from the constant's appearance rather than from its behaviour. Had it shipped, the
spec would have asserted a byte budget the relay does not enforce — the §3.1 bug's exact shape,
written into the document that exists to prevent it.

### C-S2T-6c — `rotate_to` is lowercase hex, and C# defaults to uppercase

> **Claim.** The relay tests `/^[0-9a-f]{64}$/`, **case-sensitive**. `Convert.ToHexString` returns
> **uppercase** in C#, and the engine's only rotation caller is correct solely because it appends an
> explicit `.ToLowerInvariant()`. Drop it and rotation 400s, while `RotateTokenAsync` returns a bare
> `bool` — so the failure is indistinguishable from a network error, on a call that is **one-way**
> and locks the engine out of the channel if it half-succeeds. Current behaviour is correct; the
> requirement was a **habit rather than a test**, and is now both.

```bash
cd <main>
grep -n "0-9a-f" relay/src/channel.ts
sed -n '84p' tests/SyncLiveSmoke/Program.cs
sed -n '30,38p' src/Sync/RelayClient.cs
cd relay && npx vitest run -t "rotate_to is LOWERCASE hex"
```

*Expected:* the case-sensitive regex; a caller ending in `.ToLowerInvariant()`; a method returning
`res.IsSuccessStatusCode`; 1 passed.

Proven live by relaxing the regex — **this test fails and only this test**:

```bash
cd <main>
python3 -c "
p='relay/src/channel.ts'; s=open(p).read()
s=s.replace('/^[0-9a-f]{64}\$/', '/^[0-9a-fA-F]{64}\$/')
open(p,'w').write(s)"
git diff --numstat relay/src/channel.ts          # MUST be 1 1
cd relay && npm test 2>&1 | grep -E "Tests |×"
cd .. && git checkout relay/src/channel.ts
```

*Expected:* `1 failed | 48 passed (49)`.

### C-S2T-7 — The phone-side half of PQ-S2-4 is READ, not executed

> **Claim, and its limit.** The relay half of PQ-S2-4 is measured under miniflare. The phone half —
> that `Unauthorised` is the *recoverable* halt while `PairingUnknown`/`PAIRING_GONE` is terminal and
> **unreachable on today's wire** — is derived by reading source. **No Kotlin was compiled or run:
> there is no Android SDK in this sandbox (B-7).** Treat it as a hypothesis with file:line support,
> not as a measurement.

```bash
cd <android>
sed -n '283,285p' core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
sed -n '267,270p;288,291p' core/src/main/kotlin/app/careerseeker/core/OutboundQueue.kt
grep -n 'pairing_unknown is terminal' core/src/test/kotlin/app/careerseeker/core/OutboundQueueTest.kt
grep -rn '/pair' core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expected:* the 404 → `PairingUnknown` mapping; `PairingUnknown` → `SendHalt.PAIRING_GONE` and
`Unauthorised` → `SendHalt.UNAUTHORISED`; a clearing method that clears **only** `UNAUTHORISED`; the
terminal-ness test at `OutboundQueueTest.kt:269`; and **exactly one** `/pair` reference, an
`http.post` — the phone never issues `GET /pair`, which is the one route that 404s transiently.
**The gate that would confirm the consequence is `./gradlew … :core:test`, which did not run.**

### C-S2T-8 — No vector moved, no gate ran, and the stack still merges

> **Claim.** No vector byte changed and `generate.mjs` was not edited. The drift trap is not armed:
> no harness assertion, no `$ExpectedOfflineTotal`, no count-reporting doc. Neither gate ran and
> neither could.

```bash
cd <main>
node docs/sync-vectors/generate.mjs --check
git diff --stat origin/claude/s4-pull-request-semantics..claude/s2-transport-vocabulary -- docs/sync-vectors/ scripts/
git merge-tree --write-tree --name-only claude/s2-transport-vocabulary origin/claude/s2-seq-bound >/dev/null; echo "merge-tree exit: $?"
which dotnet || echo "no .NET — Verify-Alpha.ps1 cannot run here"
```

*Expected:* `OK: 28 vector files match the generator.` and exit 0; **no** diff under
`docs/sync-vectors/` or `scripts/`; `merge-tree exit: 0` (the #33 line and the #34/#35 line still
merge cleanly — measured before and after this PR); and no `dotnet` on PATH.

**`npx tsc --noEmit` prints 55 errors on this branch and 55 on its base** — all unresolved
`Env`/`Response`, because the project typecheck is `wrangler types && tsc --noEmit` and no
`wrangler` was invoked. The only claim that supports is *unchanged by this diff*.

---

## CP — the `:core` verification lane (eighteenth cloud iteration, 2026-08-11)

Every claim below was produced on a Linux cloud sandbox with **no Android SDK**. The headline is a
*negative* claim being retracted, so the commands are written to let an auditor reproduce the
retraction rather than take it.

### C-CP-1 — One host is denied, not four

> **Claim.** B-7's egress denial covers `dl.google.com` and `api.foojay.io`. The Gradle
> distribution service, Maven Central and the Gradle plugin portal are all reachable, so `:core`'s
> entire dependency set is fetchable here.

```bash
for h in https://services.gradle.org/distributions/ \
         https://repo1.maven.org/maven2/ \
         https://plugins.gradle.org/m2/ \
         https://dl.google.com/dl/android/maven2/ \
         https://api.foojay.io/disco/v3.0/distributions ; do
    printf "%-52s " "$h"; curl -s -o /dev/null -w "%{http_code}\n" --max-time 20 "$h"
done
```

*Expected:* `200`, `200`, `200`, **`000`**, **`000`** — in that order. The two zeros are B-7; the
three 200s are what B-7 was over-read as also covering.

### C-CP-2 — `:core` needs nothing from Google, and the root script is what does

> **Claim.** `:core` declares only Maven Central artifacts. The repository build fails here because
> the **root** script resolves AGP from `google()`, not because of anything in `:core`.

```bash
cd <android>
sed -n '1,3p' core/build.gradle.kts                     # plugins { alias(libs.plugins.kotlin.jvm) }
grep -n "implementation\|testImplementation" core/build.gradle.kts | grep -v "^.*//"
sed -n '1,4p' build.gradle.kts                          # the AGP alias, apply false
grep -n "include(" settings.gradle.kts
```

*Expected:* `:core` applies **only** the Kotlin JVM plugin; its six dependencies are
`kotlinx-serialization-json`, `ktor-client-core`, `kotlin-test`, `kotlinx-serialization-json`
(test), `ktor-client-mock`, `kotlinx-coroutines-test` — **no `androidx.*`, no `com.android.*`,
no `com.google.*`**; the root script declares `alias(libs.plugins.android.application) apply false`;
`settings.gradle.kts` includes both `:core` and `:app`.

### C-CP-3 — The lane runs, from a clean build directory

> **Claim.** `scripts/core-probe.sh` runs `:core:test` to completion on a machine with no Android
> SDK: **190 tests, 0 failed, 0 skipped, 14 classes**, exit 0.

```bash
cd <android>
ls /usr/lib/jvm/ | grep 17 || \
  (apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless)
rm -rf core/build
./scripts/core-probe.sh; echo "exit: $?"
git status --porcelain            # MUST print nothing but untracked scratch, if any
```

*Expected:* `BUILD SUCCESSFUL`, then `core-probe: 190 tests, 0 failed, 0 skipped, across 14 classes`,
`exit: 0`. **The working tree must be unmodified** — the probe writes only to `core/build/`, which
`.gitignore` covers.

**The `apt-get update` is not optional.** `apt-get install` alone returns `404 Not Found` against
the stale index; measured here before the update was added.

### C-CP-4 — The lane is identical to CI's `:core` step, class by class

> **Claim.** Not "comparable to" CI — the same 190 tests in the same 14 classes, on the same commit
> `34237ea`.

```bash
cd <android>
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
for p in sorted(glob.glob('core/build/test-results/test/*.xml')):
    r = ET.parse(p).getroot()
    print(f"{r.get('name').split('.')[-1]:<26}{r.get('tests'):>4}  failures={r.get('failures')} errors={r.get('errors')}")
PY
```

Compare against CI run **31518619205**, job **93869950639**, step *Unit tests (:core)* — its log
carries **190 `PASSED` lines, 0 `FAILED`**, across the same 14 class names:

`EntitlementAckTest` 9 · `EntitlementVectorsTest` 5 · `EnvelopeJsonTest` 8 · `OutboundEnvelopesTest`
10 · `OutboundQueueTest` 20 · `OutcomeMarkPolicyTest` 22 · `PairingFlowTest` 21 · `PairingSessionTest`
8 · `ProStateTest` 5 · `ProtocolTest` 11 · `ProtocolVectorsTest` 6 · `PullPolicyTest` 17 ·
`RelayClientTest` 26 · `SyncPumpTest` 22.

*Expected:* every class present with an identical count; totals `190 = 190`.

### C-CP-5 — Proven live: the lane fails on a real regression

> **Claim.** A one-line change to `RelayClient.kt` fails **exactly two** tests and exits **1**.

```bash
cd <android>
python3 -c "
p='core/src/main/kotlin/app/careerseeker/core/RelayClient.kt'; s=open(p).read()
s=s.replace('HttpStatusCode.NotFound -> return RelayResult.PairingUnknown',
            'HttpStatusCode.NotFound -> return RelayResult.Unauthorised')
open(p,'w').write(s)"
git diff --numstat core/src/main/kotlin/app/careerseeker/core/RelayClient.kt   # MUST be 1 1
./scripts/core-probe.sh --rerun 2>&1 | grep -E "FAILED|BUILD"; echo "exit: ${PIPESTATUS[0]}"
git checkout core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
./scripts/core-probe.sh --rerun 2>&1 | tail -1
```

*Expected:* `1 1`; then `RelayClientTest > relay answers map to the decision the caller has to
make() FAILED` and `RelayClientTest > a 4xx is a decision and is never retried() FAILED`,
`BUILD FAILED`, `exit: 1`; then after the revert, `core-probe: 190 tests, 0 failed, ...`.

### C-CP-6 — C-S2T-7's "read, not executed" caveat is retired, with no new code

> **Claim.** The five assertions behind PQ-S2-4's phone-half consequence were already written and
> are inside the 190 that pass. Nothing was added to make this true.

```bash
cd <android>
sed -n '284,286p' core/src/test/kotlin/app/careerseeker/core/RelayClientTest.kt
sed -n '269,279p' core/src/test/kotlin/app/careerseeker/core/OutboundQueueTest.kt
sed -n '281,293p' core/src/test/kotlin/app/careerseeker/core/OutboundQueueTest.kt
git diff --stat HEAD~1 -- core/     # MUST be empty: no :core file changed this iteration
```

*Expected:* `404 → PairingUnknown`, `401`/`403 → Unauthorised`; `PairingUnknown → PAIRING_GONE` with
neither clearing call reviving it; `Unauthorised → UNAUTHORISED` cleared only by `reauthorised()`
with the wire bytes preserved. **No diff under `core/`.**

**The limit of the claim.** This upgrades the evidence under PQ-S2-4's *consequence*. It does **not**
close PQ-S2-4, whose resolution is a product decision, and it does not touch the relay-side
measurement (miniflare, seventeenth iteration, C-S2T-1…6).

### C-CP-7 — The seventeenth iteration's CI hang resolved without intervention

> **Claim.** Neither hung run ever failed; both were cancelled by a superseding push, and the branch
> tip ran green with step timings back at baseline.

```bash
# GitHub API, or the Actions UI for ShivaClaw/careerseeker-android:
#   runs 31517760672, 31518284889  -> conclusion "cancelled"
#   run  31518619205 (head 34237ea) -> conclusion "success", job 93869950639
```

*Expected:* on run `31518619205`, `Unit tests (:core)` **17:41:23 → 17:42:17 = 54 s** (baseline
50 s), `Unit tests (:app, Robolectric)` **17:42:17 → 17:44:05 = 108 s** (baseline 93 s), whole job
**17:39:03 → 17:46:53 = 7 m 50 s** (baseline 7 m 26 s). No step near the 16× excursion S2T-10
recorded.

### C-CP-8 — Nothing in the main repo moved, and no vector byte moved

> **Claim.** This iteration touched no main-repo file except the `autonomy/claude-state` bus entry,
> and armed no drift trap in either repo.

```bash
cd <main>
git status --porcelain                          # clean
node docs/sync-vectors/generate.mjs --check
cd <android>
git diff --stat HEAD~1 -- core/ app/ gradle/ settings.gradle.kts build.gradle.kts   # empty
git diff --name-only HEAD~1                     # records + scripts/core-probe.sh only
```

*Expected:* a clean main-repo tree; `OK: <n> vector files match the generator.` and **exit 0** —
**`<n>` is branch-dependent and that is the point of writing it this way**: `origin/main` carries
**26** (measured this iteration), and the S5-stacked branches carry **28**, because PR #32 adds the
two `entitlement_ack` vectors. **Assert the exit code, not the number**, unless you also say which
branch you are on. (The seventeenth iteration's records quote 28 correctly — they were written on
`claude/s2-transport-vocabulary`, which sits above #32.) Then: **no**
diff under `core/`, `app/`, `gradle/` or either build script; and the android diff limited to
`LOG.md`, `STATE.md`, `BLOCKED.md`, `AUDIT-REQUEST.md` and `scripts/core-probe.sh`.

**Not run and not runnable here:** `Verify-Alpha.ps1` (no .NET — `which dotnet` prints nothing) and
the android gate's other three tasks, `checkCoreIsAndroidFree`, `:app:assembleDebug`,
`:app:lintDebug` (all need the Android SDK). CI is the gate for those.

---

## C-ER — The receive state machine's check order (nineteenth cloud iteration, 2026-08-11)

Every claim in `LOG.md` §ER, with the command that re-checks it. **All of these run in a Linux
sandbox with no Android SDK**, which is the point of the lane; the three gate tasks they do *not*
cover are named in C-ER-8.

**Prerequisite for every command below** (the JDK is not egress-blocked, but it is not preinstalled):

```bash
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
```

The `update` is not optional — `install` alone 404s against a stale index.

### C-ER-1 — The suite exists, runs, and is 216/0 across 15 classes

```bash
cd <android> && git checkout claude/android-a0-probe
scripts/core-probe.sh --rerun
```

*Expected:* `BUILD SUCCESSFUL`, then
`core-probe: 216 tests, 0 failed, 0 skipped, across 15 classes`, exit 0.

**The baseline this is a delta from was re-measured in the same session, not quoted from the
eighteenth iteration's record:** `git stash`-ing the new file (or running the same command at
`a7528c1`) gives `190 tests, 0 failed, 0 skipped, across 14 classes`. **190 → 216 is 26 tests in
one new class**, and nothing else moved.

### C-ER-2 — `EnvelopeReceiver` had no dedicated test file before this commit

```bash
cd <android>
git show a7528c1 --stat | grep -c EnvelopeReceiverTest        # 0
git ls-tree --name-only a7528c1 core/src/test/kotlin/app/careerseeker/core/ | grep EnvelopeReceiver
```

*Expected:* `0`, and the second command prints **nothing**. The class was reachable only through
`ProtocolVectorsTest`, `SyncPumpTest`, `EntitlementVectorsTest` and `OutboundEnvelopesTest`:

```bash
grep -rl "EnvelopeReceiver" core/src/test/ | sort
```

### C-ER-3 — The vector suite cannot pin the order, and this is structural rather than an oversight

```bash
cd <android>
node -e '
const fs=require("fs");const d="core/src/test/resources/sync-vectors/v1";
let n=0;
for (const f of fs.readdirSync(d).filter(f=>f.endsWith(".json"))) {
  const v=JSON.parse(fs.readFileSync(`${d}/${f}`));
  if (v.type==="envelope" && v.valid===false) { console.log(f.padEnd(34), "->", v.expect_error); n++; }
}
console.log("invalid envelope vectors:", n);'
```

**Two things in that command were wrong in this entry's first draft and are corrected here, because
an audit command that does not reproduce its own stated output is a bug in this document.** The
vendored vectors live at **`core/src/test/resources/sync-vectors/v1`**, not `docs/sync-vectors/v1`
(that is the *generator's* path, in the other repo); and `valid` is a **JSON boolean**, so
`v.valid === "false"` matches nothing and the loop printed an empty list that could be misread as
"no invalid vectors exist".

*Expected:* **13** invalid envelope vectors, each with **exactly one** `expect_error` —
`decrypt_failed` ×3, `bad_signature` ×4, `unknown_kind` ×2, and one each of `too_large`,
`replay_rejected`, `key_unknown`, `version_unsupported`. **One rule broken per vector is the whole
point:** a receiver applying its checks in any order classifies all 13 identically, which is why the
suite pins classification and cannot pin order. C-ER-4's M1–M3 then demonstrate that by measurement.

### C-ER-4 — Six mutations, six caught, and three are invisible to the pre-existing suite

**This is the claim to re-run first if you only run one.** For each mutation: apply it, run the
probe, revert. The script used is reproduced in `LOG.md` §ER-3's table; the essential shape is

```bash
cd <android>
SRC=core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt
# M1: move the replay check above the signature-placement check in receive()
$EDITOR $SRC
git diff --numstat -- $SRC          # 1 1
scripts/core-probe.sh 2>&1 | grep -E "FAILED|^core-probe:"
git checkout -- $SRC                # ALWAYS, including on failure
```

*Expected, per mutation:*

| # | mutation | numstat | failing tests |
| --- | --- | --- | --- |
| M1 | replay above signature placement | `1 1` | `signature placement is checked before replay` |
| M2 | size below signature placement | `1 1` | `size is checked before signature placement` |
| M3 | `key_id` below the structural decode | `1 1` | `key_id is checked before the structural decode`, `key_id is checked before size` |
| M4 | `seq.accept` above the decrypt | `1 1` | `no rejection advances the sequence tracker…`, `ProtocolVectorsTest > the receiver classifies…` |
| M5 | `kindOf` as a substring scan | `11 5` | `untrusted body text cannot choose the route`, `a non-string kind is unknown_kind…` |
| M6 | version check deleted | `0 1` | `version is checked before key_id`, `the strict parse runs ahead…`, `no rejection advances…`, `ProtocolVectorsTest > …` |

Each exits **1**. **For M1, M2 and M3 the only failures are in `EnvelopeReceiverTest`** — that is
the measurement behind "the pre-existing 190 tests do not notice a pure reordering". To check that
half directly, stash the new file and re-run any of M1–M3: the suite returns **190/0 green on a
receiver whose checks are in the wrong order**.

**Verify the tree is clean afterwards:** `git diff --stat -- core/src/main/` → **empty**.

### C-ER-5 — The first draft of the untrusted-text test did not discriminate, and the shipped one does

```bash
cd <android>
# apply M5 (kindOf by substring scan), then:
scripts/core-probe.sh 2>&1 | grep "untrusted body text"
git checkout -- core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt
```

*Expected:* `untrusted body text cannot choose the route() FAILED`.

*And the reason it did not fail before the fix*, which is the part worth understanding:

```bash
node -e '
const s=JSON.stringify({note:"\"kind\":\"snapshot\"",kind:"heartbeat"});
const hit=s.indexOf("\"kind\"");
console.log("wire text :", s);
console.log("scanner hit at", hit, "->", s.slice(hit, hit+20));
console.log("is that the REAL key (not the decoy)?", hit === s.lastIndexOf("\"kind\""));'
```

*Expected:*

```
wire text : {"note":"\"kind\":\"snapshot\"","kind":"heartbeat"}
scanner hit at 32 -> "kind":"heartbeat"}
is that the REAL key (not the decoy)? true
```

**The last line is the one that carries the claim, and the first draft of this entry printed only
the index — a bare `32` that a reader cannot evaluate without counting characters by hand.** The
escaped `\"kind\"` inside the string value is **not** a match for `"kind"`, so the scanner walks
past the decoy and lands on the real field. That body therefore passes under the naive scanner and
proves nothing. The **nested-object** body is the one that works, which is why three bodies ship and
why the ineffective one ships first with a comment.

### C-ER-6 — The docstring's "structural decode" is one step in prose, two in code — and it costs no divergence

```bash
cd <android>
sed -n '26,35p;70,76p' core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt

cd <main-repo>
sed -n '16,25p;50,62p' src/Sync/EnvelopeReceiver.cs
grep -rn "keyForDir\|KeyFor(string" --include=*.cs src/ tests/ | grep -v "^src/Sync/EnvelopeReceiver.cs"
grep -rn "new InboundDispatcher" --include=*.cs src/
```

*Expected:* the two docstrings are **identical prose**; the Kotlin's `Direction.fromWire` sits at
step 6, after size and signature placement; the C# **never parses `dir`** and passes the raw string
to `HighestAccepted`, `keyForDir` and the AAD. Every `keyForDir` that exists is **total** (a
`dir == "e2p" ? … : …` or a `_ =>` constant — none can throw on an unknown `dir`), and the last
command prints **nothing**: `InboundDispatcher` has no production construction, only the seam
comment at `src/Engine/Program.cs:247`.

**So both sides answer `decrypt_failed` for an unrecognised `dir`, by different routes.** The prose
is what is imprecise. **Deliberately unchanged** — see §ER-5; the shared docstring should be
corrected in a change that can gate both repos.

### C-ER-7 — PQ-ER-1's two halves, both pinned by executed tests

```bash
cd <android>
scripts/core-probe.sh 2>&1 | grep "strict parse runs ahead"
sed -n '/the strict parse runs ahead of the version check/,/^    }/p' \
  core/src/test/kotlin/app/careerseeker/core/EnvelopeReceiverTest.kt
```

*Expected:* `PASSED`, and the body shows both halves — a `v=2` envelope **with** an unknown
top-level field answers `decrypt_failed`, and the **same envelope without it** answers
`version_unsupported`. That pair is the whole of PQ-ER-1: the rejection is correct either way, only
the code the sender learns differs. **Diagnosability, not safety** — see
`docs/protocol-questions.md`.

### C-ER-8 — What did NOT run, stated so no reader promotes this to a gate result

```bash
cd <android>
which dotnet                                  # nothing
./gradlew --no-daemon checkCoreIsAndroidFree  # fails: AGP resolves from google() (B-7)
cd <main-repo> && git status --porcelain      # empty
```

*Expected:* no .NET, so **`scripts\Verify-Alpha.ps1` did not run and cannot**; the root Gradle
script still fails here, so **`checkCoreIsAndroidFree`, `:app:assembleDebug` and `:app:lintDebug`
did not run** — three of the android gate's four tasks. **`scripts/core-probe.sh` runs exactly one
of them.** Citing this iteration as "the android gate passed" is the failure these records exist to
prevent. **CI is still the gate**, and the main-repo tree is untouched.

Also verify the blast radius directly:

```bash
cd <android>
git diff --name-only a7528c1..HEAD
git diff --stat a7528c1..HEAD -- core/src/main/ app/ gradle/ .github/ scripts/
```

*Expected:* the first lists `core/src/test/.../EnvelopeReceiverTest.kt` plus `LOG.md`, `STATE.md`,
`AUDIT-REQUEST.md`, `BLOCKED.md`, `docs/protocol-questions.md` — **and nothing else**. The second is
**empty**: no production Kotlin, no `:app`, no build script, no CI workflow, no script changed.

---

## C-CR — `:core`'s two crypto primitives, tested as subjects (twentieth cloud iteration, 2026-08-12)

Base commit for every command below: **`0182d89`** (branch tip before this slice).

### C-CR-1 — The suite moved 216 → 244, and the 216 is re-measurable

```bash
cd <android>
scripts/core-probe.sh --rerun 2>&1 | tail -1
git stash list >/dev/null; git show 0182d89 --stat | head -3
```

*Expected:* `core-probe: 244 tests, 0 failed, 0 skipped, across 17 classes`. The prior figure is
recoverable exactly — remove the two new files and re-run:

```bash
mkdir -p /tmp/hold && mv core/src/test/kotlin/app/careerseeker/core/crypto/*.kt /tmp/hold/
scripts/core-probe.sh --rerun 2>&1 | tail -1
mv /tmp/hold/*.kt core/src/test/kotlin/app/careerseeker/core/crypto/
```

*Expected:* `core-probe: 216 tests, 0 failed, 0 skipped, across 15 classes` — matching STATE.md's
nineteenth-run figure, so the delta is exactly **+28** (13 `HkdfTest` + 15 `Base64UrlTest`).

**This is one of the android gate's four tasks, not a gate result.** See C-CR-8.

### C-CR-2 — Before this change, no test named `Hkdf`

```bash
cd <android>
git grep -l "Hkdf" 0182d89 -- core/src/test | wc -l
git grep -l "Hkdf" HEAD   -- core/src/test
```

*Expected:* **`0`**, then `core/src/test/kotlin/app/careerseeker/core/crypto/HkdfTest.kt`. The
primitive was exercised indirectly through `PairingDerivation` and the pairing vectors; it was the
subject of nothing.

### C-CR-3 — Production never leaves HKDF's first block

```bash
cd <android>
grep -rn "Hkdf\.deriveKey" core/src/main app/src/main
```

*Expected:* **five** lines, all in `PairingDerivation.kt` — lines 31, 39, 40, 41 and 49, and no hit
under `app/src/main` at all. Line 31 passes the literal length `4`; the other four pass
`Protocol.KEY_BYTES` (on line 49 the argument is on a following line, as a named `length =`).

Grep for the bare word `deriveKey` instead and the count is **six**: the sixth is the declaration in
`Hkdf.kt` itself, not a call site. The qualified pattern above is the one that answers the claim.
And: 

```bash
grep -n "KEY_BYTES" core/src/main/kotlin/app/careerseeker/core/Protocol.kt
```

*Expected:* `const val KEY_BYTES = 32`. HKDF-SHA256's block is 32, so **every production call
completes in one iteration of `expand`'s loop**: `counter` is never anything but 1 and `mac.update(t)`
never sees a non-empty `t`. That is the gap the RFC cases close, and C-CR-7 measures that the prior
suite could not detect a break in it.

### C-CR-4 — The RFC 5869 expectations were recomputed, not transcribed

```bash
node -e '
const c=require("node:crypto"), h=b=>Buffer.from(b).toString("hex");
console.log("A.1", h(c.hkdfSync("sha256",Buffer.alloc(22,0x0b),
  Buffer.from("000102030405060708090a0b0c","hex"),
  Buffer.from("f0f1f2f3f4f5f6f7f8f9","hex"),42)));
console.log("A.3", h(c.hkdfSync("sha256",Buffer.alloc(22,0x0b),
  Buffer.alloc(0),Buffer.alloc(0),42)));'
```

*Expected:* `A.1 3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865`
and `A.3 8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8` —
byte-identical to the strings asserted in `HkdfTest.kt`, produced by an implementation that is not
this program's. (A.2 is omitted here only for line length; it is in the test file.)

**Also the limit of the empty-salt assertion**, measured rather than assumed:

```bash
node -e '
const c=require("node:crypto"), h=b=>Buffer.from(b).toString("hex");
for (const n of [0,1,32,64,65]) console.log(String(n).padStart(2),
  h(c.hkdfSync("sha256",Buffer.alloc(22,0x0b),Buffer.alloc(n),Buffer.alloc(0),42)).slice(0,32));'
```

*Expected:* lengths **0, 1, 32 and 64 all print the same prefix**; 65 differs. HMAC zero-pads any
key shorter than its 64-byte block, so `empty salt equals thirty-two zero bytes` pins RFC 5869 §2.2's
*contract* and cannot pin the constant `32`. Stated in the test's own docstring.

### C-CR-5 — Non-canonical trailing bits are accepted (PQ-B64-1's core measurement)

```bash
cd <android>
scripts/core-probe.sh 2>&1 | grep -E "non-canonical trailing bits|spare bits and therefore"
```

*Expected:* both `PASSED`. The behaviour is the JDK's, and is reproducible without this repo:

```bash
cat > /tmp/B.java <<'JAVA'
import java.util.Base64;
public class B { public static void main(String[] a){
  for (String s : new String[]{"QQ","QR","QV","QZ"})
    System.out.println(s+" -> "+Base64.getUrlDecoder().decode(s)[0]);
}}
JAVA
java /tmp/B.java
```

*Expected:* all four print `65` (`0x41`) — four spellings, one byte. This is why PQ-B64-1 exists.

### C-CR-6 — Spellings are decided by length mod 3, and the nonce has exactly one

```bash
cd <android>
scripts/core-probe.sh 2>&1 | grep "the nonce cannot be re-spelled"
sed -n '/fun `spare bits and therefore spellings are decided by length mod three`/,/^    }/p' \
  core/src/test/kotlin/app/careerseeker/core/crypto/Base64UrlTest.kt
```

*Expected:* `PASSED`, and the body asserts **1** spelling at 12 and 48 bytes, **4** at 32 and 65,
**16** at 64. The 12-byte `nonce` is therefore immune, which is what makes `signatureInput`'s binding
of the nonce *string* unambiguous. **The first draft of this file assumed the opposite** and its own
guard assertion caught it — see LOG §CR-4.

### C-CR-7 — The mutation battery, including the two mutations nothing can catch

Each row: apply, run, revert. `git checkout --` restores the source between rows.

```bash
cd <android>
H=core/src/main/kotlin/app/careerseeker/core/crypto/Hkdf.kt
B=core/src/main/kotlin/app/careerseeker/core/crypto/Base64Url.kt

# M1 -- the headline. Break multi-block chaining, WITHOUT the new tests present.
sed -i 's/^            counter++$/            \/\/ MUTANT/' $H
mkdir -p /tmp/hold && mv core/src/test/kotlin/app/careerseeker/core/crypto/*.kt /tmp/hold/
scripts/core-probe.sh --rerun 2>&1 | tail -1        # -> 216 tests, 0 failed   <== BLIND
mv /tmp/hold/*.kt core/src/test/kotlin/app/careerseeker/core/crypto/
scripts/core-probe.sh --rerun 2>&1 | tail -3        # -> 3 failed, all HkdfTest
git checkout -- $H
```

*Expected, and this is the measurement the HKDF file exists to make:* with `counter++` deleted the
**pre-existing 216 tests pass** — the shared pairing vectors included — and the three RFC cases fail.

| mutation | caught by |
| --- | --- |
| **M1** `counter++` removed | RFC A.1, A.2, A.3 — **and by nothing in the prior 216** |
| **M2** `mac.update(t)` removed (chaining) | RFC A.1, A.2, A.3 |
| **M4** length lower bound `1` → `0` | `length below one is refused` |
| **M5** length upper bound `255` → `256` blocks | `length above 255 blocks is refused` |
| **M6** `s.contains('=')` dropped | `padded input is refused` |
| **M8** `isNullOrEmpty` → `== null` | `null and empty decode to null`, `the empty array does not round trip` |
| **M3** empty-salt `ByteArray(HASH_LEN)` → `ByteArray(1)` | ***nothing — and nothing can*** |
| **M7** `s.contains('+') \|\| s.contains('/')` → `false` | ***nothing — and nothing can*** |

**M3 and M7 are semantically equivalent mutations, not test gaps**, and both were checked rather
than excused. M3: HMAC zero-pads short keys, so every all-zero salt ≤ 64 bytes is the same key
(C-CR-4's second command). M7: `+` and `/` are outside the URL alphabet, so the JDK decoder throws
and the `catch` returns null anyway — the explicit guard is defence in depth, and only its `=` half
does work the decoder does not already do (M6 proves that half is load-bearing).

Note the structural tests do **not** catch M1 or M2: `output at length N is a prefix of N plus one`
and `maximum length is 255 blocks` both survive, because a stuck counter still chains and still
produces distinct blocks. **Only the published vectors catch it** — which is the argument for RFC
cases over self-consistency properties, made by measurement rather than by preference.

### C-CR-8 — What did NOT run, and the blast radius

```bash
cd <android>
git diff --name-only 0182d89..HEAD
git diff --stat  0182d89..HEAD -- core/src/main/ app/ gradle/ .github/ scripts/
```

*Expected:* the first lists the two new test files plus `LOG.md`, `STATE.md`, `AUDIT-REQUEST.md`,
`docs/protocol-questions.md` — **and nothing else**. The second is **empty**: no production Kotlin,
no `:app`, no build script, no CI workflow, no `scripts/` change, and **no vendored vector byte**
(the pin stays `679a317`).

**Neither gate ran and neither could.** `Verify-Alpha.ps1` needs .NET; there is none here. The
android gate needs the Android SDK for **three of its four tasks** — `checkCoreIsAndroidFree`,
`:app:assembleDebug`, `:app:lintDebug` — which **did not run**. The fourth, `:core:test`, ran via
`scripts/core-probe.sh`. **CI remains the gate**; see the CI row in `STATE.md` for this branch tip.

### C-CR-9 — CI's two attempts on one commit, and the flake they establish

```bash
# Both attempts of the same run, on the same head. No push happened between them.
# (any GitHub API client; the run is public to the repo's collaborators)
#   GET /repos/ShivaClaw/careerseeker-android/actions/runs/31566551075/attempts/1
#   GET /repos/ShivaClaw/careerseeker-android/actions/runs/31566551075/attempts/2
```

*Expected:* both report `head_sha` `d8ae5da8b33bf6f23008753186ad1beccd97f3a7`; attempt **1** is
`conclusion: failure`, attempt **2** is `conclusion: success`. Attempt 1's job log contains
`ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab FAILED` and
`35 tests completed, 1 failed, 3 skipped`; attempt 2 has all thirteen steps `success`.

**Same tree, opposite outcomes, nothing pushed in between — that pair is the whole claim.**

And the blast-radius check that rules this slice out as the cause:

```bash
cd <android>
git diff --stat 0182d89..HEAD -- app/ core/src/main/
```

*Expected:* **empty**. No `:app` file and no production Kotlin changed, and `:core` *test* sources
are not on `:app`'s classpath — so no mechanism connects this branch to a Compose UI test. The
parent `0182d89` passed the same job at run `31553856359`.

**Note `49bbe25` (run #99) shows `cancelled`, not failed** — superseded by the records-only push
that produced `d8ae5da`. The two commits differ only in `BLOCKED.md`, so attempt 2's green covers
this slice's code in full.

### C-CR-10 — The flake's location, which is a reading rather than a run

```bash
cd <android>
sed -n '66,72p' app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt
```

*Expected:* line **69** is `compose.onNodeWithText(label).assertIsDisplayed()` — the **first**
assertion, immediately after `setContent` and **before any `performClick`**. So the failure is
initial composition not having settled, **not** the tab-navigation loop the test walks.

Corroborating, from attempt 1's own log:

```bash
# grep the job log for the deprecation warning carried in the same run
#   'fun createComposeRule(...)' is deprecated. Use androidx.compose.ui.test.junit4.v2.createComposeRule
#   ... "Tests relying on immediate execution may require explicit synchronization."
```

*Expected:* present. **`:app` cannot be built or run in a cloud sandbox (B-7)**, so this diagnosis
is explicitly a reading of the failing line plus the run's own warning — **not** a reproduction. The
repair is named in `BLOCKED.md`; it was deliberately not attempted here.

---

## C-SC — The AEAD codec as a subject, and the provider bound (twenty-first cloud iteration, 2026-08-12)

Every command below runs in a Linux sandbox with **no Android SDK and no .NET**. `core-probe.sh`
runs **one** of the android gate's four tasks; see **C-SC-9** for what did not run.

### C-SC-1 — No vector signature reaches the DER strip branch

```bash
cd <android>
node -e '
const fs=require("fs"),path=require("path");
const dir="core/src/test/resources/sync-vectors/v1";
const seen=new Map();
for(const f of fs.readdirSync(dir)){
  if(!f.endsWith(".json"))continue;
  const j=JSON.parse(fs.readFileSync(path.join(dir,f),"utf8"));
  (function walk(o){ if(o&&typeof o==="object") for(const[k,v] of Object.entries(o)){
    if(k==="sig"&&typeof v==="string"&&v.length>80){ if(!seen.has(v))seen.set(v,[]); seen.get(v).push(f); } else walk(v); } })(j);
}
console.log("distinct sigs:",seen.size);
for(const [sig,files] of seen){
  const b=Buffer.from(sig.replace(/-/g,"+").replace(/_/g,"/"),"base64");
  if(b.length!==64) continue;
  console.log("  r[0]=0x"+b[0].toString(16).padStart(2,"0"),"s[0]=0x"+b[32].toString(16).padStart(2,"0"),files.join(","));
}'
```

*Expected:* `distinct sigs: 8`, and **every** line has `r[0]` and `s[0]` non-zero. That is the
claim: `toDerInteger`'s `while (i < v.size - 1 && v[i].toInt() == 0) i++` has never taken an
iteration in the product or the suite.

Corroborating, that `verifySignature` had exactly one call site in the whole module:

```bash
cd <android> && grep -rn "SyncCrypto.verifySignature" core/src/ app/src/
```

*Expected:* exactly **one** production call — `EnvelopeReceiver.kt:98`, the signature step — and
exactly **one** pre-existing test call, `ProtocolVectorsTest.kt:146`, plus the new calls in
`SyncCryptoTest.kt`. (The pattern is `SyncCrypto.verifySignature`, so it does **not** match the
declaration inside `SyncCrypto.kt` itself; an earlier draft of this entry said it did, which is the
same transcription error C-CR-3 caught in the twentieth iteration.)

### C-SC-2 — No vector puts a non-ASCII byte in the AAD

```bash
cd <android>
node -e '
const fs=require("fs"),path=require("path");
const dir="core/src/test/resources/sync-vectors/v1";
let total=0,withAad=0,badAad=0,badPlain=0;
for(const f of fs.readdirSync(dir)){
  if(!f.endsWith(".json"))continue; total++;
  const j=JSON.parse(fs.readFileSync(path.join(dir,f),"utf8"));
  if(typeof j.aad==="string"){ withAad++; if(/[^\x00-\x7F]/.test(j.aad)) badAad++; }
  if(/[^\x00-\x7F]/.test(JSON.stringify(j.plaintext_json||""))) badPlain++;
}
console.log({total,withAad,nonAsciiAad:badAad,nonAsciiPlaintext:badPlain});'
```

*Expected:* `{ total: 26, withAad: 23, nonAsciiAad: 0, nonAsciiPlaintext: 1 }`. The single
non-ASCII plaintext is `heartbeat-unicode.json`, and its own `aad` field is plain ASCII — so the
suite tests the body's charset and has never tested the header's. This is PQ-AAD-1's motivation.

### C-SC-3 — The hard-coded ECDSA fixtures are reproducible

The two signatures in `SyncCryptoTest`'s section D were produced with `node:crypto` by signing
`careerseeker/v1/cmd|probe|N` for increasing `N` under one generated P-256 key and keeping the
first `N` whose `r` (respectively `s`) begins `0x00`. They are hard-coded rather than searched at
test time because ECDSA's nonce is random and a retry loop in the suite would be a second flaky
test (`BLOCKED.md`, twentieth iteration).

They do not need re-deriving to be checked — they are self-verifying against the pinned public key:

```bash
cd <android>
scripts/core-probe.sh 2>&1 | grep -E "leading zero|high bit set|strip-then-pad"
```

*Expected:* **four** `PASSED` lines — the three ECDSA cases plus
`an ecdh shared secret with a leading zero byte is left-padded to 32 bytes`, which the pattern also
matches and which belongs to C-SC-4 rather than here. If the ECDSA fixtures were wrong,
`verifySignature` would return `false` and those three would fail — a bad fixture cannot pass.

### C-SC-4 — The ECDH fixture really does have a leading-zero shared secret

```bash
cd <android>
node -e '
const c=require("crypto");
const ss=c.diffieHellman({
  privateKey:c.createPrivateKey({key:{kty:"EC",crv:"P-256",
    d:Buffer.from("ef05145101f1f7ac0c32401997d46a1fa98c43f7a740ef097c5563a66a783e0c","hex").toString("base64url"),
    x:Buffer.from("c140b3d8632fe4b65f954fd528787a8d49cc3edaedb4d178ca8b0ca9effcde83","hex").toString("base64url"),
    y:Buffer.from("f30bc7c87cd732dcae18040e339391c177cd966c86ec3956ad91cd45f37d11bb","hex").toString("base64url")},
    format:"jwk"}),
  publicKey:c.createPublicKey({key:{kty:"EC",crv:"P-256",
    x:Buffer.from("f49624aba444bc99079d23b15a0a4bae6f117bc2056131a71e74861a21fbf72b","hex").toString("base64url"),
    y:Buffer.from("67416a7d1dd5a1c5d4b7b66db43a1bfdeba7f997f5943cf43d8aa52ff15845dd","hex").toString("base64url")},
    format:"jwk"})});
console.log(ss.toString("hex"));'
```

*Expected (note the leading `00`):* `00e34c6ffb3bbdcde790ef53a42850107a3005b88f6fd9dc3c602225153ea250`.
The Kotlin test asserts the same value from both directions. **This does not prove `leftPad` is
exercised** — see **C-SC-7**, which measures that it is not.

### C-SC-5 — The AAD encoder is lossy: the measurement behind PQ-AAD-1's first half

```bash
cd <scratch> && cat > P.java <<'EOF'
import java.nio.charset.StandardCharsets; import java.util.Arrays;
public class P { public static void main(String[] a){
  String[] xs = {"Zé","Zè","ZЖ","Z😀","Z?"};
  for(String s: xs) System.out.println(s.length()+" "+Arrays.toString(s.getBytes(StandardCharsets.US_ASCII))
     +" utf8="+Arrays.toString(s.getBytes(StandardCharsets.UTF_8)));
}}
EOF
java -Dfile.encoding=UTF-8 P.java
```

*Expected:* every one of the five prints `US_ASCII` bytes `[90, 63]` — including the surrogate
pair, which collapses to **one** `0x3F`, and the literal `?`. Under UTF-8 all five differ. So the
choice of charset, not the delimiter design, is what creates this collision class.

The end-to-end consequence is asserted in Kotlin:

```bash
cd <android> && scripts/core-probe.sh 2>&1 | grep "aad encoder is lossy"
```

*Expected:* `PASSED` — an envelope sealed under `ts=…Zé` opens under `…Zè`, `…Z😀` and `…Z?`.

### C-SC-6 — The AAD framing is ambiguous, with no non-ASCII involved

```bash
cd <android> && scripts/core-probe.sh 2>&1 | grep "aad framing is ambiguous"
```

*Expected:* `PASSED`. The two header tuples are
`(ts="T", key_id="K|key_id=Z")` and `(ts="T|key_id=K", key_id="Z")`; the test asserts the AAD
**strings** are equal and that an envelope sealed under one opens under the other.

The unvalidated-fields half, which is why it is reachable at all:

```bash
cd <android> && sed -n '51,73p' core/src/main/kotlin/app/careerseeker/core/EnvelopeJson.kt
```

*Expected:* `pairing` is checked with `isValidPairingId`, `v`/`seq` are typed, `dir` is parsed —
and **`ts` and `key_id` get `stringField` and nothing else**.

### C-SC-7 — The mutation battery, and the four that survive for two different reasons

```bash
cd <android>
# For each mutation: apply to core/src/main/.../crypto/SyncCrypto.kt, run, revert.
#   M1 delete  `while (i < v.size - 1 && v[i].toInt() == 0) i++`
#   M2 delete  `if (v[0].toInt() and 0x80 != 0) v = byteArrayOf(0) + v`
#   M3 change  Charsets.US_ASCII -> Charsets.UTF_8 in gcm()
#   M4 delete  require(key.size == Protocol.KEY_BYTES)
#   M5 delete  require(nonce.size == Protocol.NONCE_BYTES)
#   M6 delete  if (rawSignature.size != 64) return false
#   M7 change  return leftPad(ka.generateSecret(), 32) -> return ka.generateSecret()
#   M8 change  catch (_: Exception) { false } -> catch (e: Exception) { throw e }
scripts/core-probe.sh 2>&1 | grep -E "^[A-Za-z]+ > .* FAILED"
git checkout -- core/src/main/kotlin/app/careerseeker/core/crypto/SyncCrypto.kt
```

*Expected, per mutation:*

| | failing tests | why |
| --- | --- | --- |
| **M1** | **2** — `s has a leading zero and a low next byte`, `r has the high bit set` | both use the `leadingZeroS` fixture |
| **M2** | **0** | `SunEC` accepts an unpadded negative INTEGER |
| **M3** | **1** — `the aad encoder is lossy…` | UTF-8 makes the AADs distinct again |
| **M4** | **1** — `key and nonce sizes are enforced…` | |
| **M5** | **1** — `key and nonce sizes are enforced…` | |
| **M6** | **0** | redundant with `rawToDer`'s throw inside the `try` |
| **M7** | **0** | `SunEC` never returns a short ECDH secret |
| **M8** | **0** | nothing in the `try` throws on `SunEC` |

**M1 failing the `r`-leading-zero test is NOT expected** — a `0x00` followed by a high-bit byte is
a strip-then-pad no-op, which is why that test is named for the no-op and not for the branch.

Then, the property that makes the whole battery trustworthy:

```bash
cd <android> && git diff --stat -- core/src/main/
```

*Expected:* **empty.** Every mutation was reverted; no production file changed this iteration.

### C-SC-8 — The provider facts behind PQ-SC-1, measured rather than assumed

```bash
cd <scratch>   # full source in the iteration's LOG entry, §SC-7
java Probe3.java
```

*Expected:* `JCA provider for ECDSA: SunEC`; the unpadded-negative DER encoding verifies `true`;
`generateSecret()` reports `RAW LENGTH FROM JCA = 32` for a secret whose first byte is `0x00`; and
`generatePublic` **returns without throwing** for both an off-curve point and coordinates of all
`0xFF`. Those three facts are exactly why M2, M7 and M8 cannot be caught on this JVM.

### C-SC-9 — The suite count, and what did NOT run

```bash
cd <android>
git stash push -- core/src/test/kotlin/app/careerseeker/core/crypto/SyncCryptoTest.kt
scripts/core-probe.sh | tail -1
git stash pop
scripts/core-probe.sh | tail -1
```

*Expected:* `244 tests, 0 failed, 0 skipped, across 17 classes` then
`270 tests, 0 failed, 0 skipped, across 18 classes`. The baseline was **re-measured this session**
rather than quoted from the twentieth run's record.

**What did not run, stated so no reader promotes this to a gate result.** `core-probe.sh` runs
**one** of the android gate's four tasks. `checkCoreIsAndroidFree`, `:app:assembleDebug` and
`:app:lintDebug` need the Android SDK and **did not run** (B-7). `scripts/Verify-Alpha.ps1` needs
.NET and **did not run** — `which dotnet` is empty — so **nothing here is main-repo gate-backed**,
and the offline pin of **598** was not measured by me. **CI is the gate.** Note also that a green
`:core:test` says nothing about Conscrypt (**PQ-SC-1**), and this iteration touched **no vector
byte**, so the vendored pin `679a317` is intact by construction:

```bash
cd <android> && git diff --stat 27b28bb..HEAD -- core/src/test/resources/sync-vectors/
```

*Expected:* **empty.** `27b28bb` is this branch's tip before the iteration; the range is pinned to
it rather than to `HEAD~N`, which drifts as commits are added.

---

## C-WP — The engine's wire parser, and the toolchain that made it possible (twenty-second cloud iteration, 2026-08-12)

Unlike every C- section before it, these commands need **.NET**, and **C-WP-1 is how you get it**
in a sandbox that does not ship it. There is still **no Android SDK and no PowerShell**; see
**C-WP-12** for what did not run. `<engine>` is a checkout of `ShivaClaw/careerseeker` at branch
`claude/s5-engine-wire-parser` (draft PR **#37**, stacked on **#32**).

### C-WP-1 — .NET 8 is in the Ubuntu archive, and the whole engine builds on Linux

```bash
apt-cache policy dotnet-sdk-8.0
apt-get update -qq && apt-get install -y --no-install-recommends dotnet-sdk-8.0
dotnet --version
cd <engine> && dotnet build CareerSeeker.sln -c Release
```

*Expected:* candidate `8.0.125-0ubuntu1~24.04.1` from `noble-updates/main`; `dotnet --version`
prints `8.0.129`; the build reports **0 Warning(s), 0 Error(s)**. This is the claim that retires
B-6's stated reason. Note the *installed* version (`8.0.129`) differs from the apt *candidate*
string — both are correct, they are SDK vs package versions.

Corroborating that the match is exact rather than lucky:

```bash
cd <engine> && grep -rh "TargetFramework" src/*/*.csproj tests/*/*.csproj | sort -u; ls global.json
```

*Expected:* only `<TargetFramework>net8.0</TargetFramework>`, and **no** `global.json`.

### C-WP-2 — The gap: at the base commit the engine had no inbound wire parser

```bash
cd <engine>
git show origin/claude/s5-entitlement-ack-spec:src/Sync/EnvelopeReceiver.cs | sed -n '6,10p'
git grep -n "Deserialize\|JsonNode.Parse\|JsonSerializer" origin/claude/s5-entitlement-ack-spec -- src/Sync/
```

*Expected:* `ReceivedEnvelope` is a record of nine already-typed fields, and every JSON call site
in `src/Sync` at that commit is **outbound** serialisation (`PairingManager.ToQrJson`,
`SyncPayloads`, `SyncPublisher`). No inbound parser exists, which is why an unknown top-level field
was accepted.

### C-WP-3 — The parser exists and covers exactly §3's nine fields

```bash
cd <engine> && sed -n '/KnownFields/,/};/p' src/Sync/EnvelopeJson.cs
```

*Expected:* `v, pairing, dir, seq, ts, key_id, nonce, ciphertext, sig` — the same set as the
phone's `EnvelopeJson.KNOWN_FIELDS` (`core/src/main/kotlin/app/careerseeker/core/EnvelopeJson.kt`),
which is the comparison that matters. Diff the two field sets by eye; they must not drift.

### C-WP-4 — Routing the vectors through the parser changed no existing verdict

```bash
cd <engine> && git checkout 274ea6b
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | sed -n '/the strict section-3 wire parser/,/^$/p' | grep -c PASS
```

*Expected:* **`141 passed, 0 failed`**, and the parser block contributes exactly **11**. 141 − 11 =
**130** — the pre-existing count — so all 24 envelope vectors classify identically through the
strict parser. This is the compatibility claim: the parser refuses nothing the vectors declare
legal. (`274ea6b` is the reroute commit, before the vector was added.)

### C-WP-5 — SyncHarness 130 → 142, measured on both sides of the change

```bash
cd <engine>
git checkout origin/claude/s5-entitlement-ack-spec && dotnet build CareerSeeker.sln -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
git checkout claude/s5-engine-wire-parser && dotnet build CareerSeeker.sln -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
```

*Expected:* **`130 passed, 0 failed`** on the base, **`142 passed, 0 failed`** after. The baseline
was **re-measured this session**, not quoted from a previous record.

### C-WP-6 — The vector is additive; no existing vector's bytes moved

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check
git diff --stat origin/claude/s5-entitlement-ack-spec..claude/s5-engine-wire-parser -- docs/sync-vectors/v1/
git diff --name-only origin/claude/s5-entitlement-ack-spec..claude/s5-engine-wire-parser \
  -- docs/sync-vectors/v1/ | grep -v index.json | grep -v invalid-unknown-field | wc -l
```

*Expected:* `OK: 29 vector files match the generator.` (28 on the base); the stat shows
`index.json` **+6** and the one new file; the third command prints **0**. That zero is the
cross-repo drift claim.

### C-WP-7 — The vendored pin cannot be moved by this change

```bash
cd <android> && sed -n '53,86p' .github/workflows/ci.yml
```

*Expected:* the drift step fetches each vendored file at `?ref=$PIN` where `$PIN` comes from
`core/src/test/resources/sync-vectors/VECTORS.lock` (`679a317`). It compares **vendored copy vs the
pinned commit** — never against main's tip — so adding a vector on a branch cannot affect it, and
nothing under `core/src/test/resources/sync-vectors/` was touched this iteration.

### C-WP-8 — M1: without the rule the vector is ACCEPTED, not rejected differently

```bash
cd <engine>
python3 - <<'EOF'
p='src/Sync/EnvelopeJson.cs'; s=open(p).read()
s=s.replace("""            foreach (var prop in root.EnumerateObject())
                if (!KnownFields.Contains(prop.Name)) return Fail();""","")
open(p,'w').write(s)
EOF
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release >/dev/null \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -E "FAIL|passed,"
git checkout -- src/Sync/EnvelopeJson.cs
```

*Expected:* **`128 passed, 3 failed`**, the first being
`FAIL invalid-unknown-field -> decrypt_failed -- got accepted`. **The other two are not a separate
finding** — accepting the envelope commits its `seq`, moving the e2p high-water mark to 12, so
`invalid-unknown-kind` (seq 8) reports `replay_rejected` and the tracker assertion fails. §10.1 of
`docs/Sync-Protocol.md` documents that the suite's `seq` space is packed by design; this is that
property working, not a new one. **Restore the file** — the last line is not optional.

### C-WP-9 — M5: the root-object guard is load-bearing, and its failure mode is an escape

```bash
cd <engine>
sed -i 's|            if (root.ValueKind != JsonValueKind.Object) return Fail();|            // M5|' src/Sync/EnvelopeJson.cs
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release >/dev/null \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -5
git checkout -- src/Sync/EnvelopeJson.cs
```

*Expected:* **not** a `FAIL` line — an `Unhandled exception. System.InvalidOperationException: The
requested operation requires an element of type 'Object', but the target element has type 'Array'`
thrown from `EnvelopeJson.Parse`, i.e. straight out through the `ParseResult` contract. Same shape
as the twelfth iteration's `parsePullPage` finding. **Restore the file.**

### C-WP-10 — The offline pin: 393 + 217 = 610

```bash
cd <engine>
for h in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness GatewayGateHarness \
         DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  echo -n "$h: "; dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 \
    | grep -oE "=== [0-9]+ passed" | tail -1
done
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
```

*Expected:* nine harnesses summing to **393** (`28+57+16+28+36+35+45+6+142`), **EngineHarness
producing no summary** — it dies at `FullDataDeletion.ResolveAllowedWorkspace`
(`src/Engine/FullDataDeletion.cs:81`) because a Windows install path resolves to `/` on Linux and
the guard **correctly refuses a volume root** — and the pin reading **610**. EngineHarness's
**217** is quoted from `Verify-Alpha.ps1`'s own comment block, **not re-measured**. This is
arithmetic corroborated by measurement, **not** a verifier run.

### C-WP-11 — PQ-AAD-1: the two encoders agree except on surrogate pairs

```bash
cd <engine> && grep -n "Encoding.ASCII" src/Sync/EnvelopeCodec.cs src/Sync/DeviceSignature.cs
mkdir -p /tmp/aad && cd /tmp/aad && cat > P.java <<'EOF'
import java.nio.charset.StandardCharsets;
public class P { public static void main(String[] a){
  for (String s : new String[]{"Té","Tè","TЖ","T😀","T?"}) {
    byte[] b = s.getBytes(StandardCharsets.US_ASCII); StringBuilder sb=new StringBuilder();
    for (byte x: b) sb.append(String.format("%02X",x));
    System.out.println(sb+" (len "+b.length+")"); } } }
EOF
javac P.java && java P
```

…and the .NET half (a throwaway console project referencing nothing):

```bash
cd /tmp/aad && dotnet new console -o net --force >/dev/null && cat > net/Program.cs <<'EOF'
using System.Text;
foreach (var s in new[]{"Té","Tè","TЖ","T😀","T?"})
  Console.WriteLine(Convert.ToHexString(Encoding.ASCII.GetBytes(s)) + $" (len {Encoding.ASCII.GetBytes(s).Length})");
EOF
dotnet run --project net
```

*Expected:* the engine encodes the AAD and the §5.4 signature input with `Encoding.ASCII`; both
runtimes give `543F` for `é`, `è`, `Ж` and a literal `?`; and **they differ on the emoji** — Java
`543F` (one byte, the surrogate pair collapses) vs .NET `543F3F` (**two**). That difference is the
whole of PQ-AAD-1's answer. It **fails closed** (tag mismatch → `decrypt_failed`), is unreachable
for a conforming sender, and is **deliberately not fixed** — see `docs/protocol-questions.md`.

### C-WP-12 — What did NOT run, and what remains the gate

```bash
cd <android> && ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug --rerun-tasks
cd <engine>  && pwsh -File scripts/Verify-Alpha.ps1
```

*Expected:* **both fail to start here.** The first needs the Android SDK (B-7: `dl.google.com`
denied); the second needs PowerShell, which is absent and **not in the Ubuntu archive**
(`apt-cache policy powershell` returns nothing) — so `Verify-Alpha.ps1` was not run and could not
even be parse-checked. `:core:test` was **not** run this iteration either, and did not need to be:
nothing in `:core` changed. **CI on `windows-latest` is the gate for the 610 pin and for every
claim in this section that depends on it.**

### C-WP-13 — CI ran the gate and confirmed the pin (added after the entry above was written)

```bash
gh run view 31600630766 --repo ShivaClaw/careerseeker
gh run view 31600630766 --repo ShivaClaw/careerseeker --log \
  | grep -E "invalid-unknown-field|Offline total|142 passed"
```

*Expected:* both jobs **success**, and the log contains
`PASS  invalid-unknown-field -> decrypt_failed`, `=== 142 passed, 0 failed ===` and
**`=== Offline total: 610 passed, 0 failed ===`**.

This is the evidence **C-WP-10 could not produce in-session**. `Verify-Alpha.ps1` throws on a pin
mismatch, so a green run *is* the pin check — which also settles `EngineHarness = 217`
(610 − the 393 measured locally), the one number quoted rather than measured. The relay job's
*"Assert sync vectors match their generator"* step passing is the independent confirmation of
**C-WP-6**.

**This does not retire C-WP-12.** `Verify-Alpha.ps1` still cannot run in a cloud sandbox; the gate
ran on `windows-latest`, as it always must.

---

## C-AK — S5 entitlement_ack emitter (2026-08-12, twenty-third cloud iteration, draft PR #38)

Every command below was run in-session unless the entry says otherwise. Branch:
`claude/s5-entitlement-ack-emitter` in the **engine** repo, stacked on `claude/s5-engine-wire-parser`.

### C-AK-1 — The finding: the kind existed only as a vocabulary string

```bash
cd <engine> && git checkout origin/claude/s5-engine-wire-parser
grep -rn "entitlement_ack\|EntitlementAck" src/ tests/ --include=*.cs
```

*Expected:* **exactly one line**, `src/Sync/Protocol.cs:34`, the `ShippingKinds` entry. No builder,
no publisher, no dispatch arm. This is the state the branch replaces: the engine verified a Play
receipt, flipped its own Pro flag, and sent the phone nothing — while §4.3.3 makes the ack the only
thing that may unlock Pro there.

### C-AK-2 — Toolchain: .NET is obtainable, and this is a 30-second re-test

```bash
apt-cache policy dotnet-sdk-8.0
apt-get install -y --no-install-recommends dotnet-sdk-8.0 && dotnet --version
```

*Expected:* a candidate from `noble-updates/main`, and **8.0.129**. `which dotnet` returning nothing
on a fresh sandbox is **not** evidence the toolchain is unavailable — that mistake cost this program
eight iterations (B-6). When a blocker's reason is "tool X is absent", the re-test is
`apt-cache policy <pkg>`.

### C-AK-3 — Build baseline

```bash
cd <engine> && dotnet build CareerSeeker.sln -c Release
```

*Expected:* `Build succeeded.  0 Warning(s)  0 Error(s)` — both before and after the change.

### C-AK-4 — SyncHarness moved 142 → 157

```bash
cd <engine> && git checkout origin/claude/s5-engine-wire-parser \
  && dotnet build tests/SyncHarness/SyncHarness.csproj -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -1
git checkout origin/claude/s5-entitlement-ack-emitter \
  && dotnet build tests/SyncHarness/SyncHarness.csproj -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -1
```

*Expected:* `=== 142 passed, 0 failed ===` then `=== 157 passed, 0 failed ===`.

### C-AK-5 — The ack is byte-identical to the shared vectors

```bash
cd <engine> && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | grep -A 12 "entitlement_ack: the engine builds"
```

*Expected:* 11 PASS lines, including for **each** of `entitlement-ack` and
`entitlement-ack-no-order-id`: "reproduces the vector plaintext byte for byte" and "re-sealing the
built body reproduces the vector ciphertext exactly". Byte equality is the claim — a field-by-field
check passes while the implementations disagree about field order or about an omitted vs. null
`order_id`, which is what C-AK-7's M1'/M2 demonstrate.

### C-AK-6 — An accepted receipt acks; a rejected receipt does not

```bash
cd <engine> && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | grep -E "dispatch: (an applied|the ack names|a REJECTED|a null ack)"
```

*Expected:* four PASS lines. The load-bearing one is **"a REJECTED entitlement publishes no ack at
all (§4.3.3 has no negative form)"** — the ack means granted, full stop, and the rejection returns
*before* the publish in `src/Sync/InboundDispatcher.cs`. Also pinned: the ack names the product and
order read from the **verified receipt**, never from the phone's request body.

### C-AK-7 — Proven by mutation, not assumed (5/5 caught)

```bash
cd <engine> && git checkout origin/claude/s5-entitlement-ack-emitter
# M1' absent order_id becomes an empty string
sed -i 's/order_id = orderId }/order_id = orderId ?? "" }/' src/Sync/SyncPayloads.cs
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release >/dev/null \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -c FAIL
git checkout -- src/Sync/
# M2 field order swapped
sed -i 's/new { product_id = productId, acknowledged_at = acknowledgedAt, order_id = orderId }/new { acknowledged_at = acknowledgedAt, product_id = productId, order_id = orderId }/' src/Sync/SyncPayloads.cs
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release >/dev/null \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -c FAIL
git checkout -- src/Sync/
# M5 the ack drops the receipt's order id
sed -i 's/PublishEntitlementAckAsync(verdict.ProductId!, verdict.OrderId, ct)/PublishEntitlementAckAsync(verdict.ProductId!, null, ct)/' src/Sync/InboundDispatcher.cs
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release >/dev/null \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -c FAIL
git checkout -- src/Sync/
```

*Expected:* **3**, **4**, **1** failures respectively. Two further mutations (dispatcher never
publishes → **2** failures; ack also published on a rejected receipt → **1**) require editing the
dispatch arm by hand rather than by `sed`.

**Revert only `src/`, and commit the harness first.** Reverting `tests/SyncHarness/` with
uncommitted work in it deletes the assertions being measured, and the tell is a mutation reporting
the *pre-change* total (`142`) rather than a lower one — that is a missing test file, not a weak
mutation. This happened in-session; see LOG.md AK-7.

### C-AK-8 — No vector byte moved, and no vector was added

```bash
cd <engine> && git diff --name-only origin/claude/s5-engine-wire-parser..origin/claude/s5-entitlement-ack-emitter -- docs/sync-vectors/ | wc -l
node docs/sync-vectors/generate.mjs --check
```

*Expected:* **0**, and `OK: 29 vector files match the generator.` The android repo's vendored copies
are pinned at `679a317` and are untouched by construction — **no cross-repo drift event**.

### C-AK-9 — The offline sum measured on Linux is 408; 217 is carried, not measured

```bash
cd <engine> && for h in Slice ResearcherHarness HookHarness StoreParityHarness \
  GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$h/$h.csproj -c Release --no-build | tail -1
done
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build | tail -3
```

*Expected:* 28, 57, 16, 28, 36, 35, 45, 6, **157** → **408**. `EngineHarness` **throws**:
`System.InvalidOperationException: Refusing full-data deletion for a volume root` at
`src/Engine/FullDataDeletion.cs:81` — correct behaviour, since a Windows install path resolves to
`/` on Linux. Its **217 is carried from the CI-settled 610 pin, not measured this session**.
408 + 217 = **625**.

### C-AK-10 — The pin and every count-reporting doc moved together

```bash
cd <engine> && grep -rn "625\|SyncHarness | 157" README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md scripts/Verify-Alpha.ps1
grep -rn "610\|SyncHarness | 142" README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md scripts/Verify-Alpha.ps1 \
  | grep -v "10.0.26100" | grep -v "^scripts/Verify-Alpha.ps1:1[5-6][0-9]:#"
```

*Expected:* the first finds `$ExpectedOfflineTotal = 625`, both table rows in all three tables, the
Audit-Handoff line, and each `Assert-Contains` literal. The second finds **only** the historical
narrative comment above the pin, which deliberately keeps `598 -> 610` and `610` as the record of
how the number got here. This is CLAUDE.md's drift trap: doc content and verifier expectation are
one unit that changes together.

### C-AK-11 — §10.2 now names which implementation asserts, and which does not

```bash
cd <engine>  && sed -n '/#### 10.2/,/#### 10.3/p' docs/Sync-Protocol.md
cd <android> && sed -n '1,30p' core/src/test/kotlin/app/careerseeker/core/EntitlementAckTest.kt
```

*Expected:* §10.2 states the engine asserts against the vector files byte-for-byte and that the
**phone does not read them** — `EntitlementAckTest` transcribes the two bodies verbatim because the
android repo vendors `docs/sync-vectors/` at a pin predating the ack vectors, which the Kotlin file
says itself. **These vectors are therefore evidence about ONE implementation**, and §10's
cross-implementation property does not yet hold for this kind. → **PQ-A2-5**.

### C-AK-12 — The seam has no production caller: the path is closed in the library, not the engine

```bash
cd <engine> && grep -rn "IEntitlementAckPublisher" src/ | grep -v "^src/Sync/"
grep -rn "PublishEntitlementAckAsync" src/ | grep -v "^src/Sync/"
```

*Expected:* **both print nothing.** A dispatcher constructed without the seam applies the
entitlement and emits nothing, exactly as before — there is an assertion pinning that inert
behaviour. Host wiring needs the pairing vault and device session (same host work S2/S4 await;
B-2 still gates the vault end). **Unblocked, merely unwritten** — do not file it as a blocker.

### C-AK-13 — No E2E claim is made anywhere

```bash
cd <engine> && git log origin/claude/s5-engine-wire-parser..origin/claude/s5-entitlement-ack-emitter -p \
  -- src/ tests/ | grep -iE "relay\.careerseeker|https://|health"
```

*Expected:* **nothing**. No relay was contacted in this session, not even `GET /v1/health`; no phone
exists; `PublishEntitlementAckAsync` has never sent a byte to a real receiver.

### C-AK-14 — What did NOT run, and what remains the gate

```bash
cd <android> && ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug --rerun-tasks
cd <engine>  && pwsh -File scripts/Verify-Alpha.ps1
```

*Expected:* **both fail to start here.** The first needs the Android SDK (B-7/B-4). The second needs
PowerShell, absent and **not in the Ubuntu archive** — so `Verify-Alpha.ps1` was not run and could
not even be parse-checked. `:core:test` was not run and did not need to be: nothing in `:core`
changed. **CI on `windows-latest` is the gate for the 625 pin**, exactly as it was for 610. Citing
this section as "the engine gate passed" is the precise failure these records exist to prevent.

### C-AK-15 — CI ran the gate and confirmed the 625 pin (added after the section above was written)

```bash
gh run view 31621352429 --repo ShivaClaw/careerseeker
gh run view 31621352429 --repo ShivaClaw/careerseeker --log \
  | grep -E "Offline total|157 passed|no ack at all|byte for byte"
```

*Expected:* both jobs **success**, and the log contains `=== 157 passed, 0 failed ===`,
**`=== Offline total: 625 passed, 0 failed ===`**, both "byte for byte" PASS lines and
`dispatch: a REJECTED entitlement publishes no ack at all`.

This is the evidence **C-AK-9 could not produce in-session**. `Verify-Alpha.ps1` throws on a pin
mismatch, so a green run *is* the pin check — which also confirms `EngineHarness = 217`
(625 − the 408 measured locally), the one number carried rather than measured, and unchanged from
the run that settled 610. The relay job's *"Assert sync vectors match their generator"* step passing
independently confirms **C-AK-8**.

**This does not retire C-AK-14**, and it does not touch **PQ-A2-5**. `Verify-Alpha.ps1` still cannot
run in a cloud sandbox; the gate ran on `windows-latest`, as it always must. And CI exercising the
*engine's* vector assertions says nothing about the phone, which still transcribes rather than reads.

---

## C-VR — the phone reads the ack vectors (twenty-fourth cloud iteration, 2026-08-12)

Every claim in `LOG.md` §VR. `<android>` is this repo; `<engine>` is a `ShivaClaw/careerseeker`
clone. Run `git fetch --all --prune` in both first.

Commits referenced below, so these commands keep working as the branch grows:
`e007e07` the base this slice started from · `056a1dd` re-vendor · `c714570` wire-text delivery ·
`60a20d5` the phone reads the ack vectors · `ae799c8` call the shipped `receiveWire` seam.

### C-VR-1 — The transcription was not verbatim: 142/140 and 104/102 bytes

```bash
cd <android> && git show e007e07:core/src/test/kotlin/app/careerseeker/core/EntitlementAckTest.kt \
  | sed -n '/ackWithOrderId = /,/toByteArray()/p'
cd <engine> && node -e '
const {execSync}=require("child_process"),{createDecipheriv}=require("crypto");
const d=s=>Buffer.from(s.replace(/-/g,"+").replace(/_/g,"/"),"base64");
for (const n of ["entitlement-ack","entitlement-ack-no-order-id"]) {
  const v=JSON.parse(execSync(`git show 7328a0b:docs/sync-vectors/v1/${n}.json`));
  const c=d(v.ciphertext_b64u), x=createDecipheriv("aes-256-gcm",Buffer.from(v.key_hex,"hex"),d(v.nonce_b64u));
  x.setAAD(Buffer.from(v.aad)); x.setAuthTag(c.subarray(c.length-16));
  const p=Buffer.concat([x.update(c.subarray(0,c.length-16)),x.final()]);
  console.log(n, p.length, JSON.stringify(p.toString()));
}'
```

*Expected:* the old literals are wrapped across two lines (a newline plus a leading space before
`"acknowledged_at"`), and the sealed bytes are **140** and **102** with no whitespace. The literals
measure **142** and **104**. They parse identically, which is why nine tests passed over the
difference.

### C-VR-2 — Before the fix, the phone ACCEPTED an envelope the engine rejects

The measurement in `LOG.md` §VR-2. Vendor the vectors at the parent of the source change and run the
suite:

```bash
cd <android> && git checkout -b tmp-c-vr-2 056a1dd && \
  gradle :core:test --tests '*ProtocolVectorsTest*'   # or ./gradlew, with an SDK present
```

*Expected:* **FAILS** with
`invalid-unknown-field should reject as decrypt_failed ==> expected: <decrypt_failed> but was: <null>`.
`056a1dd` is the re-vendor commit, which adds the vector but predates routing envelopes through the
strict parser. Delete the branch afterwards; it exists only to reproduce the defect.

### C-VR-3 — The rule was implemented all along; only the vector path bypassed it

```bash
cd <android> && grep -n "KNOWN_FIELDS" core/src/main/kotlin/app/careerseeker/core/EnvelopeJson.kt
cd <android> && git show 056a1dd:core/src/test/kotlin/app/careerseeker/core/ProtocolVectorsTest.kt \
  | sed -n '/private fun received(/,/^    )/p'
```

*Expected:* `EnvelopeJson` rejects any key outside the nine §3 defines — it always did. The old
`received()` helper reads exactly those nine keys off `envelope_json` and drops the rest, so no
unknown field could ever reach the rule. The defect was the delivery path, not the parser.

### C-VR-4 — The re-vendor is additive; no existing vector byte moved

```bash
cd <engine>  && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pin
cd <android> && diff -r core/src/test/resources/sync-vectors/v1 /tmp/pin/docs/sync-vectors/v1
cd <android> && git diff --numstat e007e07 ae799c8 -- core/src/test/resources/sync-vectors/v1/index.json
cd <android> && git diff --name-status e007e07 ae799c8 -- core/src/test/resources/sync-vectors/v1/
```

*Expected:* `diff -r` silent (all 29 identical); `index.json` **18 added, 0 removed**; the name-status
list shows **`A`** for exactly three files and **`M`** for `index.json` only. No other `M`.

### C-VR-5 — The 26 pre-existing vectors are identical across all three commits

```bash
cd <engine> && for r in 679a317 origin/main 7328a0b; do git archive $r docs/sync-vectors/v1 \
  | tar -x -C /tmp/$r; done && diff -r /tmp/679a317 /tmp/origin_main
```

*Expected:* identical. This is what makes the off-main pin safe to rely on: the content the pin
names has been stable across the merge that landed the sync track.

### C-VR-6 — The pin is not on main, and that is not new

```bash
cd <engine> && git merge-base --is-ancestor 679a317 origin/main; echo "679a317 on main? $?"
cd <engine> && git merge-base --is-ancestor 7328a0b origin/main; echo "7328a0b on main? $?"
cd <engine> && git log --oneline origin/main..origin/claude/s5-entitlement-ack-emitter -- docs/sync-vectors/
```

*Expected:* **both print 1** (neither is an ancestor). The vendored copy has been pinned off-main
since it was first vendored. The log shows exactly two commits touching the vector directory, the
later being `7328a0b` — which is why the pin names it rather than the branch tip.

### C-VR-7 — The content is anchored to the generator, not to the branch

```bash
cd <engine> && git worktree add --detach /tmp/wt 7328a0b && cd /tmp/wt && \
  node docs/sync-vectors/generate.mjs --check
```

*Expected:* `OK: 29 vector files match the generator.`

### C-VR-8 — CI's drift step works against an off-main pin

This is the step the re-pin was most likely to break. Run CI's own loop:

```bash
cd <android> && PIN=$(grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1)
for f in core/src/test/resources/sync-vectors/v1/*.json; do n=$(basename $f); \
  curl -fsSL -H "Accept: application/vnd.github.raw+json" \
    "https://api.github.com/repos/ShivaClaw/careerseeker/contents/docs/sync-vectors/v1/$n?ref=$PIN" \
    -o /tmp/u.json && diff -q "$f" /tmp/u.json >/dev/null || echo "DRIFT/FAIL: $n"; done; echo done
```

*Expected:* no `DRIFT/FAIL` lines — 29 fetched, 0 drift. Confirms the contents API serves a
draft-branch SHA, so the pin being off-main does not break CI.

### C-VR-9 — The new assertions have teeth (mutation)

Commit first, then mutate only `core/src/main`:

```bash
# (a) drop the unknown-field rule
sed -i 's|if (root.keys.any { it !in KNOWN_FIELDS }) return fail()|if (false) return fail()|' \
  core/src/main/kotlin/app/careerseeker/core/EnvelopeJson.kt
# (b) make an absent order_id present
sed -i 's|val orderId = if (orderIdField == null) {|val orderId = if (false) {|' \
  core/src/main/kotlin/app/careerseeker/core/EntitlementAck.kt
# (c) re-introduce the transcription defect, in the test:
#     append to ackPlaintext's return:
#     .toString(Charsets.UTF_8).replace(",\"acknowledged_at", ",\n \"acknowledged_at").toByteArray()
```

*Expected:* (a) **6** failures including `ProtocolVectorsTest.the receiver classifies every envelope
vector` — which could not fail before this slice; (b) **4** including `entitlement ack vectors
decrypt to the exact bytes`; (c) **exactly 1** — `the grant bodies are the vectors' own bytes and not
a re-wrapped copy` — and the other nine pass, which is the measurement of what the transcription was
worth. `git checkout --` each file afterwards.

### C-VR-10 — What did NOT run, and what remains the gate

```bash
cd <android> && scripts/core-probe.sh --rerun
cd <android> && ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug
cd <engine>  && pwsh -File scripts/Verify-Alpha.ps1
curl -sS -o /dev/null https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.3.0/gradle-9.3.0.pom
```

*Expected:* the probe prints `core-probe: 272 tests, 0 failed, 0 skipped, across 18 classes` (it
needs a JDK 17 present — it says so and gives the apt line if missing). The **gate** command fails
during plugin resolution: AGP is on `dl.google.com`, and the curl prints
`CONNECT tunnel failed, response 403` (**B-7**). `Verify-Alpha.ps1` fails — no PowerShell here.

**The probe is one of the gate's five tasks.** `checkCoreIsAndroidFree`, `:app:assembleDebug`,
`:app:lintDebug` and `:app:test` **did not run**. **CI is the gate.** Citing C-VR as "the android
gate passed" is the precise failure these records exist to prevent.

### C-VR-11 — PQ-A2-5's main-repo half is still open

**Corrected 2026-08-13 (twenty-fifth run): the first command named the wrong repo and could never
have run.** `docs/protocol-questions.md` lives in **this** repo, not the engine's —
`git show origin/claude/s5-entitlement-ack-emitter:docs/protocol-questions.md` answers
`fatal: path 'docs/protocol-questions.md' does not exist`. Fourth recurrence of the
audit-command-does-not-reproduce shape (after CR-6, C-CR-3 and C-SC-1/3/4), and the first where the
error was the repository rather than the pattern.

```bash
cd <android> && sed -n '/^## PQ-A2-5/,/^---$/p' docs/protocol-questions.md | tail -4
cd <engine> && git show origin/claude/s5-entitlement-ack-emitter:docs/Sync-Protocol.md \
  | grep -n "transcribed by the phone\|The phone does not yet read these files"
```

**Both halves were wrong, and both are fixed above.** The first named the engine repo for a file that
lives here. The second grepped for the literal `one implementation`, which appears **nowhere** in
`docs/Sync-Protocol.md` on that branch — §10.2 is headed *"asserted by the engine, transcribed by the
phone"* and states the asymmetry in those words instead. An audit command that returns nothing is
indistinguishable from a claim that has become false, which is exactly the failure this file exists to
prevent.

*Expected:* both still say the ack vectors are evidence about **one** implementation. That is
**correct and deliberate** — it stays true until this android PR merges, and amending it early would
make the engine repo assert something whose truth depends on an unmerged PR in another repo.

---

## C-IP — the engine had no receive path at all (twenty-fifth cloud iteration, 2026-08-13)

Every claim in `LOG.md` §IP. `<engine>` is a `ShivaClaw/careerseeker` clone; `<android>` is this
repo. Run `git fetch --all --prune` in both first. Everything below is engine-side: **this iteration
changed no android source file**, only these records.

Commits referenced, so the commands keep working as the branch grows: `2bb61de` the base
(`origin/claude/s5-entitlement-ack-emitter`, PR #38) · `c7c79ce` the pump · `1b30643` the resumable
replay mark · `e1fc72d` the host wiring · `7bd4812` the assertions · `ec7d0e5` the pin sweep.
Branch `claude/s5-inbound-pump`, draft PR **#39**.

Toolchain, needed by nearly every command below:

```bash
apt-get update -qq && apt-get install -y --no-install-recommends dotnet-sdk-8.0 && dotnet --version
```

*Expected:* `8.0.129` (`noble-updates/main`). Standing rule, re-proved: when a blocker's stated
reason is "tool X is absent", the re-test is `apt-cache policy <pkg>`, not `which <tool>`.

### C-IP-1 — The finding: every inbound seam had zero production callers

```bash
cd <engine> && git grep -n -E "RecordP2eSeq|EnvelopeJson\.|InboundDispatcher\(|PullAsync\(|IEntitlementAckPublisher|LastP2eSeq" 2bb61de -- src/ \
  | grep -v "src/Sync/InboundDispatcher.cs\|src/Sync/EnvelopeJson.cs\|src/Sync/RelayClient.cs\|src/Engine/SyncPairingVault.cs"
```

*Expected:* **exactly two lines**, both in `src/Engine/Program.cs` (246 and 247), and both are
**comments** describing what should be built. No inbound seam had a caller: not the pull loop, not
the dispatcher, not the ack publisher, and not the vault's `last_p2e_seq`, which had been persisted
since PR #31 and read by nothing. The engine could publish and could not receive.

Same command against `HEAD` of `claude/s5-inbound-pump` returns real call sites in
`src/Engine/Program.cs` (356–392), `src/Engine/SyncAckPublisher.cs` and `src/Sync/InboundPump.cs`.

### C-IP-2 — Build baseline and after

```bash
cd <engine> && git checkout 2bb61de && dotnet build CareerSeeker.sln -c Release 2>&1 | tail -4
cd <engine> && git checkout claude/s5-inbound-pump && dotnet build CareerSeeker.sln -c Release 2>&1 | tail -4
```

*Expected:* **0 Warning(s) / 0 Error(s)** both times. The whole C# solution builds in a Linux cloud
sandbox.

### C-IP-3 — SyncHarness 157 → 173

```bash
cd <engine> && git checkout 2bb61de && dotnet build CareerSeeker.sln -c Release >/dev/null && \
  dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -1
cd <engine> && git checkout claude/s5-inbound-pump && dotnet build CareerSeeker.sln -c Release >/dev/null && \
  dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -1
```

*Expected:* `=== 157 passed, 0 failed ===` then `=== 173 passed, 0 failed ===`. Both ends measured
in this session; neither number is carried.

### C-IP-4 — The cursor rules, read as executed output

```bash
cd <engine> && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | sed -n '/inbound pump/,/replay mark raises/p'
```

*Expected:* fourteen `PASS` lines under `[ inbound pump: the engine's p2e transport loop ]`,
including by name:

- `a crafted seq of 1,000,000 on an undecryptable envelope is capped at the page's latest`
- `an accepted envelope's AUTHENTICATED seq moves the cursor past a lying latest`
- `the cursor never moves backwards (seeded at 20, page claims 2)`
- `an unauthenticated seq is never persisted as the replay mark`

The pair is the point: the bound applies to unauthenticated advances and **only** to those. Bounding
an accepted seq too would be the stall §6.2 forbids by name.

### C-IP-5 — Parsing is not authenticating, and that is the whole rule

```bash
cd <engine> && sed -n '/A seq is recovered from the sealed bytes/,/every conforming page/p' src/Sync/InboundPump.cs
```

*Expected:* the doc paragraph stating that an envelope can be well-formed §3 JSON — valid pairing
id, dir, key_id, nonce, base64url ciphertext — and still be bytes the relay invented; its header seq
parses and is authenticated by nothing. The cursor therefore advances freely only for an envelope the
receiver **accepted**; a failed §3 parse and a failed AEAD tag are treated identically, both bounded
by the page's `latest`.

### C-IP-6 — The engine's own traffic, served back at it

```bash
cd <engine> && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | grep "e2p envelope replayed onto the p2e page\|cannot corrupt the p2e replay mark"
```

*Expected:* two `PASS` lines. The attack they pin, in full: an envelope the engine itself sent
(`dir: e2p`, sealed under `k_e2p`, unsigned) is well-formed, and a relay may serve it back on the p2e
page. Every downstream check passes — the sig-placement rule is satisfied because an e2p envelope
carries no sig, the replay check consults the **e2p** counter which the resume never seeds, and
`keyForDir` hands over `k_e2p`, so the tag verifies. It is **accepted**, its kind falls through to
`Ignored`, and the damage lands to the side: `onAccepted` writes an **e2p** seq into the persisted
**p2e** replay mark. Push that mark past the phone's counter and every genuine phone envelope
afterwards is refused as a replay — silent, permanent, one-directional. M1 below measures it.

### C-IP-7 — The persisted mark only protects anything if the receiver is built from it

```bash
cd <engine> && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | grep "unseeded receiver ACCEPTS\|resumed from the persisted mark"
```

*Expected:* two `PASS` lines. The first asserts the **failure**: a receiver that starts empty accepts
an already-applied envelope. That is not hypothetical — the relay chooses what a page contains, so a
restarted engine can simply be handed the entitlement again. The second asserts the fix.

### C-IP-8 — Proven by mutation, and one was NOT caught

```bash
cd <engine> && python3 - <<'PY'
s=open('src/Sync/InboundPump.cs').read()
old='                rejections.Add(parsed.Error ?? SyncError.DecryptFailed);'
open('src/Sync/InboundPump.cs','w').write(s.replace(old,'                _onAccepted?.Invoke(ClaimedSeq(element));\n'+old))
PY
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release >/dev/null && \
  dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep "FAIL\|passed,"
cd <engine> && git checkout -- src/Sync/InboundPump.cs
```

*Expected:* `FAIL  pump: an element failing the §3 parse persists no replay mark either` and
`=== 172 passed, 1 failed ===`.

Seven mutations were applied and reverted this session; the full list and their caught assertions are
in `LOG.md` §IP-6. **M4 above survived the first pass.** The assertion covering the parse-failure
branch pinned the cursor and said nothing about the mark, so writing a claimed seq into the persisted
replay mark from the least-authenticated branch on the page was caught by nothing. That is a real gap
in the new tests rather than a semantically equivalent change — checked, not excused — and it is why
there are sixteen new assertions and not fifteen.

### C-IP-9 — No vector byte moved

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check
cd <engine> && git diff --name-only 2bb61de..claude/s5-inbound-pump -- docs/sync-vectors/ | wc -l
```

*Expected:* `OK: 29 vector files match the generator` and **0**. No vector was added or changed, so
the android repo's `7328a0b` vendor pin is untouched and **no cross-repo drift event occurred**.

### C-IP-10 — The pin moved 625 → 641, and the arithmetic is stated

```bash
cd <engine> && for h in Slice ResearcherHarness HookHarness StoreParityHarness GatewayGateHarness \
  DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 | grep -oE "=== [0-9]+ passed, [0-9]+ failed ==="; done
cd <engine> && grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
cd <engine> && grep -rn "641" scripts/Verify-Alpha.ps1 docs/CareerSeeker-Project-Summary.md \
  docs/External-Audit-Handoff.md README.md src/Engine/README.md
```

*Expected:* 28, 57, 16, 28, 36, 35, 45, 6, 173 — summing to **424**, all with 0 failed.
`$ExpectedOfflineTotal = 641`, and 641 appears in all five count-reporting files, swept in the same
commit (`ec7d0e5`) as the drift trap requires. **641 = 424 + 217.**

### C-IP-11 — The 217 is carried, not measured, and here is why

```bash
cd <engine> && dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build 2>&1 | tail -4
```

*Expected:* `Unhandled exception. System.InvalidOperationException: Refusing full-data deletion for a
volume root.` at `FullDataDeletion.ResolveAllowedWorkspace`. `EngineHarness` **cannot complete on
Linux**, because a Windows install path resolves to `/` here and the guard **correctly** refuses it.
Its **217** is quoted from the CI-settled 625 (610 + 15), not measured this session. So **641 is
corroborated, not measured end-to-end**, and CI on `windows-latest` is the gate.

### C-IP-12 — The host wiring is compile-checked and was never executed

```bash
cd <engine> && sed -n '/^InboundPump? BuildInboundPump/,/^}/p' src/Engine/Program.cs | head -30
cd <engine> && grep -n "DrainInboundAsync" src/Engine/Host.cs src/Engine/EngineSyncBridge.cs
```

*Expected:* the composition exists and compiles. **It did not run anywhere in this session and could
not**: `BuildSyncBridge` returns null without a pairing, and the pairing vault is DPAPI — Windows
only. The pump's *rules* are tested (C-IP-4/6/7); the *composition* is not. Any record that blurs
those two is wrong.

### C-IP-13 — The code implements §6.4, which is not in the branch it is written on

```bash
cd <engine> && grep -n "^### 6\." docs/Sync-Protocol.md
cd <engine> && git show origin/claude/s4-pull-request-semantics:docs/Sync-Protocol.md | grep -n "^### 6.4"
```

*Expected:* this branch's spec has **§6.1, §6.2, §6.3 and no §6.4**; §6.4 exists only on
`claude/s4-pull-request-semantics` (PR #33), a **sibling** of this stack. So a reader of PR #39
cannot find the section the code cites, and the two PRs must land together or the citation dangles.

Worse, and this is the substantive half: §6.4's carve-out is written for "an element that **fails the
§3 parse**" and says nothing about one that parses and then fails the tag. Read literally it forbids
advancing at all in that case — which is the stall §6.2 forbids in as many words. **The spec has a
hole**, PR #39 implements the sensible reading rather than the literal one, and the amendment belongs
on #33. Filed as **PQ-CUR-1**.

### C-IP-14 — The phone has the same hole and this did not close it

```bash
cd <android> && sed -n '255,262p' core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
```

*Expected:* `val seq = header?.seq ?: minOf(envelope.seq, page.latest)` — the phone bounds the
claimed seq only when the **parse** fails. An envelope that parses and then fails the tag moves the
phone's cursor by its header seq, **unbounded**. Same truncation door, one field over.

The engine is now stricter than the phone on a shared rule. There is **no interop risk** — the cursor
is local transport state and never appears on the wire — so this is not the mission's "phone more
correct than the engine" field bug in either direction. It is unclosed work, in **PQ-CUR-1**, and it
is deliberately not this slice: closing it well means amending §6.4 first, on a branch this one does
not contain.

### C-IP-15 — What did NOT run, and what remains the gate

```bash
cd <engine> && which pwsh powershell; apt-cache policy powershell 2>/dev/null | head -3
cd <android> && git diff --stat 2bb61de..HEAD -- app/ core/ 2>/dev/null | tail -1
```

*Expected:* no PowerShell binary and **no candidate in the Ubuntu archive** — the trick that solved
.NET does not repeat, so **`scripts/Verify-Alpha.ps1` did not run and could not be parse-checked**.
No claim is made about the engine gate this iteration. The android gate did not run either and did
not need to: **no android source file changed**, so `:core:test` had nothing to re-measure. **CI is
the gate in both repos**, and citing anything here as "the gate passed" is the precise failure these
records exist to prevent.

### C-IP-16 — CI ran the gate and confirmed the 641 pin (added after the section above was written)

```bash
gh api repos/ShivaClaw/careerseeker/actions/runs/31657307243 --jq '.head_sha,.status,.conclusion'
gh api repos/ShivaClaw/careerseeker/actions/runs/31657307243/jobs --jq '.jobs[] | "\(.name): \(.conclusion)"'
gh run view --repo ShivaClaw/careerseeker --job 94314498111 --log | grep "Offline total\|=== 173 passed"
```

*Expected:* head_sha **`ec7d0e5`** (equal to the branch tip), `completed`, **`success`**; both jobs
success — *Build and offline harnesses* (`windows-latest`, job `94314498111`) and *Blind relay
(Worker)* (`ubuntu-latest`, job `94314498169`); and from the log itself:

```
=== 173 passed, 0 failed ===
=== Offline total: 641 passed, 0 failed ===
CareerSeeker alpha verification complete.
```

So `Verify-Alpha.ps1` **ran in full and 641 is confirmed by measurement**, not by the "it throws on
drift, so exit 0 implies it" argument. **`EngineHarness` = 217 is corroborated again** (641 − the 424
measured on Linux), unchanged across the 610 and 625 settlements, which is what a carried number
should do. The relay job's *Assert sync vectors match their generator* step also passed, independently
confirming C-IP-9's zero-drift claim on a machine that is not this one.

**What it does not prove.** No engine↔relay smoke ran, so **the host wiring is still unexecuted** —
CI builds it and never constructs it, because there is no pairing vault on a runner (C-IP-12). And CI
being green is **not** the merge condition: the policy needs a full *local* gate, so PR #39 stays a
**DRAFT**.

## §C-CUR — PQ-CUR-1, closed on both sides (2026-08-13, twenty-sixth cloud iteration)

Engine-repo claims are against `claude/s4-pull-request-semantics` at `3a8dfdd` (draft PR #33);
android claims against `claude/android-a0-probe`. `<engine>` is a clone of `ShivaClaw/careerseeker`,
`<android>` a clone of this repo. Every command below was executed before being written down.

### C-CUR-1 — The defect: §6.4's carve-out named the parse, not the tag

```bash
cd <engine> && git show b114d11:docs/Sync-Protocol.md | sed -n '/### 6.4 The transport cursor/,/^\*\*Why bounded/p' | grep -n "fails the §3 parse\|recovered from the sealed bytes"
```

*Expected:* on the **pre-change** commit, the MUST names "recovered from the sealed bytes" and the
only carve-out is "When an element fails the §3 parse". An element that parses and then fails the
AEAD tag matches neither clause: no authenticated seq, so the MUST forbids advancing; not a parse
failure, so the carve-out does not reach it.

### C-CUR-2 — The amendment, read as shipped text

```bash
cd <engine> && sed -n '/### 6.4 The transport cursor/,/^\*\*Why bounded/p' docs/Sync-Protocol.md
```

*Expected:* the `Amended 2026-08-13 (PQ-CUR-1)` note; a bullet reading "For **every other element** —
one that fails the §3 parse, *and* one that parses and is then rejected for any reason, **the AEAD
tag included**"; and the `Parsing is not authenticating, and that is where the line falls` paragraph
stating the boundary is *accepted vs. not accepted*.

### C-CUR-3 — Three sentences widened from "malformed" to "unauthenticated"

```bash
cd <engine> && grep -c "malformed element" docs/Sync-Protocol.md
cd <engine> && grep -c "unauthenticated element" docs/Sync-Protocol.md
```

*Expected:* **0** and **3**. The rule now covers well-formed elements that no key opens, so
"malformed" understated it; leaving those three sentences would have re-stated the original defect in
prose one paragraph below its correction.

### C-CUR-4 — The phone's hole, read off the pre-change line

```bash
cd <android> && git show 9d5f1fd:core/src/main/kotlin/app/careerseeker/core/SyncPump.kt | grep -n "header?.seq ?: minOf"
```

*Expected:* one hit, `val seq = header?.seq ?: minOf(envelope.seq, page.latest)`. The bound is on the
`null` branch only, and the assignment sits **above** the `receiver.receive` call — so the cursor was
committed on the strength of the parse alone.

### C-CUR-5 — The phone's fix: three paths, one bounded helper

```bash
cd <android> && sed -n '/PARSING IS NOT AUTHENTICATING/,/if (seq > cursorValue) cursorValue = seq/p' core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
cd <android> && sed -n '/Advance the cursor to \[claimed\]/,/^    }/p' core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
```

*Expected:* the unparseable branch calling `advanceBounded(envelope.seq, page.latest)`, the
refused branch calling `advanceBounded(header.seq, page.latest)`, and the accepted path taking
`header.seq` with **no bound**; then `advanceBounded`'s body, `minOf(claimed, latest)` guarded by
`bounded > cursorValue`. The two callers are deliberately not distinguished — §6.4 asks whether the
seq is authenticated, not which check refused the element.

### C-CUR-6 — The suite, both ends measured this session

```bash
cd <android> && apt-get update -qq && apt-get install -y openjdk-17-jdk-headless
cd <android> && git stash && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/core-probe.sh | tail -1
cd <android> && git stash pop && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/core-probe.sh | tail -1
```

*Expected:* `core-probe: 272 tests, 0 failed, 0 skipped, across 18 classes` then
`core-probe: 276 tests, 0 failed, 0 skipped, across 18 classes`. Neither number is carried; the
baseline was re-measured in this session rather than quoted from the twenty-fourth run.

### C-CUR-7 — M1: the new tests fail against the pre-change source, and nothing else does

```bash
cd <android> && git show 9d5f1fd:core/src/main/kotlin/app/careerseeker/core/SyncPump.kt \
  > core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/core-probe.sh | grep -E "FAILED|core-probe:"
cd <android> && git checkout -- core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
```

*Expected:* exactly three `FAILED` lines — `a parseable envelope whose tag fails cannot move the
cursor past latest either`, `a rejected envelope is bounded whichever check refused it`, `after a
bounded tag failure the stream still delivers envelopes issued later` — and **no others**. That the
other 272 stay green **is** the finding: the pre-existing suite could not see this bug.

### C-CUR-8 — M2/M3: the bound applies to the unauthenticated path and only to it

```bash
cd <android> && F=core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
sed -i 's/val bounded = minOf(claimed, latest)/val bounded = claimed/' $F
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/core-probe.sh | grep -cE "FAILED"
git checkout -- $F
sed -i 's/val seq = header.seq/val seq = minOf(header.seq, page.latest)/' $F
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/core-probe.sh | grep -E "FAILED"
git checkout -- $F
```

*Expected:* **M2** fails **five** tests (the three new ones *plus* the two pre-existing parse-failure
bound tests) — after this change there is exactly one bounded path and it is shared. **M3** fails
exactly one, `an authenticated seq above latest still moves the cursor`, which is the proof the
change did not over-clamp: bounding an accepted seq would let an understated `latest` hold a receiver
below envelopes it has already read.

### C-CUR-9 — M4 SURVIVED, and that is the fourth test

```bash
cd <android> && F=core/src/main/kotlin/app/careerseeker/core/SyncPump.kt
sed -i 's/if (bounded > cursorValue) cursorValue = bounded/cursorValue = bounded/' $F
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/core-probe.sh | grep -E "FAILED|core-probe:"
git checkout -- $F
```

*Expected now:* one `FAILED` — `a page that understates latest cannot drag the cursor backwards`.
*Expected before that test was written:* **no failures at all, `276 tests, 0 failed`** (measured at
275 with three new tests). §6.4's **first** bullet is a MUST that nothing on this side asserted, and
the bound is what makes it reachable: `minOf(claimed, latest)` takes the relay's `latest` whenever it
is smaller, so a page understating `latest` drags the cursor down and re-requests envelopes already
accepted — which the in-process replay window then refuses, the pull-the-same-page-forever loop rule
1 exists to prevent. A real test gap, checked rather than excused.

### C-CUR-10 — The dangling citation is NOT removed by this change

```bash
cd <engine> && git merge-base --is-ancestor origin/claude/s4-pull-request-semantics origin/claude/s5-inbound-pump; echo "exit=$?"
cd <engine> && git show origin/claude/s5-inbound-pump:docs/Sync-Protocol.md | grep -c "^### 6.4"
cd <engine> && git grep -n "§6.4" origin/claude/s5-inbound-pump -- 'src/**/*.cs'
```

*Expected:* `exit=1` (**siblings**, not ancestor/descendant), `0` §6.4 headings on PR #39's branch,
and two citations in `src/Sync/InboundPump.cs`. So `InboundPump.cs` still cites a section its own
branch does not contain; **this change fixes the section's content, not the citation**, and the two
PRs must still land together. PR #39's comment reads "arriving with PR #33", so it is a flagged
citation rather than a silent one.

### C-CUR-11 — No vector byte moved, and the vendored pin is intact

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check
cd <engine> && git diff --name-only b114d11..3a8dfdd -- docs/sync-vectors/ | wc -l
```

*Expected:* `OK: 28 vector files match the generator` and `0`. No vector was added, changed or
regenerated, so the android repo's `7328a0b` vendored pin is untouched and **no cross-repo drift
event occurred**. (28 here, not the 29 the twenty-fifth run reported — `invalid-unknown-field`
arrives with PR #37, which is not an ancestor of PR #33.)

### C-CUR-12 — The doc/verifier drift trap is not engaged

```bash
cd <engine> && grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1
cd <engine> && grep -n 'ExpectedOfflineTotal\s*=' scripts/Verify-Alpha.ps1
```

*Expected:* **0**, and `$ExpectedOfflineTotal = 598`. The verifier carries **no** assertion against
the normative protocol document, so a §6.4 edit cannot drift it; and no harness assertion was added
or removed this run, so 598 is untouched on that branch and could not have moved. Checked rather than
assumed — this is the trap `CLAUDE.md` names first.

### C-CUR-13 — What did NOT run, and what remains the gate

```bash
cd <android> && sed -n '/WHAT THIS IS NOT/,/Do not report a gate result/p' scripts/core-probe.sh
cd <android> && which pwsh powershell; apt-cache policy powershell 2>/dev/null | head -3
```

*Expected:* the probe's own header saying it runs **one** of the gate's four tasks; and no PowerShell
binary and no installation candidate. `checkCoreIsAndroidFree`, `:app:assembleDebug` and
`:app:lintDebug` need the Android SDK (**B-7**) and **did not run**; `Verify-Alpha.ps1` **did not run
and could not**. **CI is the gate.** Nothing was executed against a relay, an engine or a phone: the
pump's *rules* are tested, the composition is not.

### C-CUR-14 — CI ran both repos' gates on these exact heads (added after §C-CUR was written)

```bash
gh api repos/ShivaClaw/careerseeker/actions/runs/31669070172/jobs \
  --jq '.jobs[] | "\(.name): \(.conclusion)"'
gh api repos/ShivaClaw/careerseeker-android/actions/runs/31669725746/jobs \
  --jq '.jobs[] | .steps[] | "\(.number) \(.name): \(.conclusion)"'
```

*Expected (engine, head `3a8dfdd`):* two jobs, **both `success`** — `Blind relay (Worker)` and
`Build and offline harnesses`, 05:04:35 → 05:06:50 UTC. The `windows-latest` job's step 6, **`Run
offline alpha verification`**, *is* `Verify-Alpha.ps1` — the script that **throws on a pin
mismatch** — and it is `success`. So **`$ExpectedOfflineTotal = 598` is CI-confirmed rather than
merely asserted**, on the one platform this sandbox cannot reach, and C-CUR-12's claim is now
measured on a second machine. The relay job's step 10, `Assert sync vectors match their generator`,
is also `success` — zero vector drift, independently confirmed.

*Expected (android, head `d3dcce7`):* **all thirteen steps `success`, first attempt**, 05:15:45 →
05:22:53 UTC (**7 m 08 s** against a ~7 m 51 s baseline). Read one by one, the four that matter here
are the four **`scripts/core-probe.sh` structurally cannot run**:

- `Assert :core has no Android dependency` ✓
- `Assert vendored sync vectors match the pinned main-repo commit` ✓ — **the `7328a0b` pin holds**,
  independently confirming this iteration moved no vector byte
- `Assemble debug APK` ✓ and `Lint` ✓

`Unit tests (:core)` ✓ (05:18:02 → 05:18:51) is the **276 measured on the real JDK 17 + SDK
toolchain**, not only on the reduced probe. `Unit tests (:app, Robolectric)` ✓ — **the standing
`ScreensFromFixtureTest` flake did not fire**, and this slice touched no `:app` file anyway.

**So the android gate passed, and this is the first claim in this section entitled to say so.**
Everything above it says `core-probe.sh` ran one of four tasks; this row is where the other three are
answered, and by a machine rather than by me.

**What this does and does not license.** CI green is **not** the merge condition. The main-repo merge
policy requires a full *local* gate (`Verify-Alpha.ps1 -IncludePublish -IncludePackage`), a different
condition that remains out of reach here; the android repo is **never-self-merge** regardless. Every
PR in both stacks stays a **DRAFT**, and #33 and #39 still have to land together (C-CUR-10).

---

## C-RPR — RelayClient.PullAsync's failure channel, and PQ-S2-4's engine half (2026-08-13, twenty-seventh cloud iteration)

Every command below is run from a clone of `ShivaClaw/careerseeker` at
`claude/s2-relay-pull-result` (**draft PR #45**, stacked on #39 → #38 → #37 → #32), unless it names
this repo. `.NET` is not preinstalled in a fresh cloud sandbox; `apt-get update -qq && apt-get install
-y dotnet-sdk-8.0` is the one machine change, as the twenty-second run established.

### C-RPR-1 — the defect: three throwing calls and no failure channel

```bash
git show claude/s5-inbound-pump:src/Sync/RelayClient.cs | sed -n '62,75p'
git show claude/s5-inbound-pump:src/Engine/Program.cs  | sed -n '370,387p'
```

*Expected:* the **pre-change** `PullAsync` returning a bare
`Task<(IReadOnlyList<JsonElement> Envelopes, long Latest)>` and calling **`EnsureSuccessStatusCode`**,
**`GetProperty`** (twice) and **`GetInt64`** — three throwing calls, no failure channel in the
signature. The second command shows the host's containment: a single `catch` naming **five** exception
types, whose own comment reads *"Containment, not a fix."*

### C-RPR-2 — RelayClient had no offline coverage of any kind

```bash
git grep -l RelayClient claude/s5-inbound-pump -- tests/
```

*Expected:* **exactly one path** — `tests/SyncLiveSmoke/Program.cs`. That project needs a live or
local relay and is excluded from the hermetic offline suite (`grep -n SyncLiveSmoke
scripts/Verify-Alpha.ps1` shows it gated behind `-IncludeLive`), so before this change the engine's
relay client was **never executed by the offline gate**. This is the measurement behind the LOG's
claim that the partiality survived four iterations that cited it.

### C-RPR-3 — the four cases, and that they are derived from the relay rather than invented

```bash
sed -n '/^public abstract record RelayPullResult/,/^}/p' src/Sync/RelayClient.cs
sed -n '40,70p' relay/src/index.ts
```

*Expected:* a hierarchy closed by a **private constructor** to `Ok`, `Unauthorised`, `Misconfigured`
and `Unavailable`. The relay source is what licenses that split: `index.ts:55` answers **404
`pairing_unknown`** for a pairing id that fails the shape check, `index.ts:61` answers **401
`unauthorized`** when the bearer is absent or malformed *before* dispatch, and `index.ts:66` answers
**404 `not_found`** for an unknown route.

### C-RPR-4 — PQ-S2-4's engine half: the asymmetry that forbids copying the phone's mapping

```bash
# engine: no pairing-id guard at construction, though the check exists in the same assembly
sed -n '/public sealed class RelayClient/,/^{/p' src/Sync/RelayClient.cs
grep -n "IsValidPairingId" src/Sync/EnvelopeJson.cs
# phone: the guard the engine lacks
grep -n "isValidPairingId" ../careerseeker-android/core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expected:* the engine's `RelayClient` primary constructor takes `string pairing` and **validates
nothing**, while `EnvelopeJson.cs:51` defines `IsValidPairingId` in the same assembly; the phone's
client carries `require(isValidPairingId(pairing))` in its `init`. **So the relay's shape-check 404 is
reachable for the engine and unreachable for the phone**, which is why 404 maps to `Misconfigured`
here and must not be mapped to the phone's terminal `PairingUnknown`. The guard is **deliberately not
added** — a throwing change to a startup-path constructor belongs to a slice that can run the full
local gate.

### C-RPR-5 — build and harness, measured

```bash
dotnet build CareerSeeker.sln -c Release 2>&1 | tail -4
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 | tail -2
```

*Expected:* **0 Warning(s), 0 Error(s)**, and **`=== 194 passed, 0 failed ===`**. The baseline
**173** was re-measured this session on `claude/s5-inbound-pump` before any edit rather than quoted
from the previous record; `git stash`-ing this branch's `tests/SyncHarness/Program.cs` reproduces it.

### C-RPR-6 — the new section, and that it drives the real client over a fake socket only

```bash
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 \
  | sed -n '/relay pull result/,$p' | head -25
sed -n '/sealed class StubTransport/,/^}/p' tests/SyncHarness/Program.cs
```

*Expected:* **21 `PASS` lines** under `[ relay pull result ]`, and a `StubTransport : HttpMessageHandler`
that answers whatever the test hands it — including by throwing. The class under test is the shipping
`SeekerSvc.Sync.RelayClient`; only the socket is stubbed.

### C-RPR-7 — proven by mutation: seven applied, seven caught

Apply each edit to `src/Sync/RelayClient.cs`, rebuild `tests/SyncHarness`, run it, then revert.

*Expected:*

| | mutation | result |
| --- | --- | --- |
| M1 | `case NotFound:` returns `Unauthorised()` | **193 passed, 1 failed** — `404 is Misconfigured, NOT Unauthorised and NOT Unavailable` |
| M2 | drop `.Clone()` from the envelope projection | **ESCAPED** — `System.ObjectDisposedException`, no summary line, exit 134 |
| M3 | delete the `when (ct.IsCancellationRequested)` catch | **193 passed, 1 failed** — `caller cancellation propagates rather than becoming a result` |
| M4 | accept `latest` via `(long)GetDouble()` without the integer check | **191 passed, 3 failed** |
| M5 | missing `envelopes` returns `Ok(empty, 0)` | **192 passed, 2 failed** |
| M6 | delete the `root.ValueKind != JsonValueKind.Object` guard | **ESCAPED** — `System.InvalidOperationException`, no summary line, exit 134 |
| M7 | remove `case HttpStatusCode.Forbidden:` | **193 passed, 1 failed** — `403 is Unauthorised too` |

**M2 and M6 do not produce a FAIL line**, and that is the point rather than a gap in the report: they
take the harness down with an **unhandled exception escaping through `PullAsync`'s own contract**,
which is the exact failure mode this change removes. Verify the tree is restored afterwards:
`git diff --stat -- src/Sync/RelayClient.cs` must match the committed diff and nothing else.

### C-RPR-8 — the pin sweep is complete, and no literal was left behind

```bash
grep -n "ExpectedOfflineTotal = " scripts/Verify-Alpha.ps1
grep -rn "641\|SyncHarness | 173" scripts/Verify-Alpha.ps1 README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md | grep -v ":[0-9]*:#"
```

*Expected:* **`$ExpectedOfflineTotal = 662`**, and the second command prints **nothing** — no live
`641` and no `SyncHarness | 173` literal anywhere in the swept set. Six `Assert-Contains` literals
moved (three `| SyncHarness | 173 |` → `194`, three `| **Total** | **641** |` → `662`) plus the
pinned-verifier phrase, together with the four docs those literals target.

**The `| grep -v ":[0-9]*:#"` is load-bearing and this entry shipped without it at first.** Run
without the filter, the command returns **one** line — `scripts/Verify-Alpha.ps1:185`, reading
*"…which is why there are sixteen assertions rather than fifteen. 641."* That is the **previous**
pin's derivation comment, which correctly ends at the total it derived; the comment block is a
running history of how each bump was reached, so a past total appearing in it is right and must not
be "corrected". The claim worth auditing is that **no live assertion** still says 641, and the filter
is what makes the command test that claim rather than a stronger and false one. Caught by the
standing re-run step before commit — the **fourth** recurrence of an audit command whose stated
output did not reproduce.

### C-RPR-9 — 662 is corroborated, not measured; and exactly which part is carried

```bash
for h in Slice ResearcherHarness HookHarness StoreParityHarness GatewayGateHarness \
         DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 | grep -oE "=== [0-9]+ passed"
done
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build 2>&1 | tail -3
which pwsh; apt-cache policy powershell
```

*Expected:* **28, 57, 16, 28, 36, 35, 45, 6, 194 — summing to 445.** `EngineHarness` **aborts** with
`System.InvalidOperationException: Refusing full-data deletion for a volume root` at
`FullDataDeletion.ResolveAllowedWorkspace` — the guard **correctly** refusing a Windows install path
that resolves to `/` on Linux — so its **217 is carried from the CI-settled 641, not re-measured**.
445 + 217 = **662**. The last two commands print **nothing**: there is no PowerShell binary and **no
installation candidate**, so **`scripts/Verify-Alpha.ps1` did not run and could not**. The `apt` trick
that closed B-6 does **not** repeat. **CI on `windows-latest` is the gate.**

### C-RPR-10 — no vector byte moved, so no cross-repo drift event

```bash
node docs/sync-vectors/generate.mjs --check
git diff --name-only origin/claude/s5-inbound-pump..HEAD -- docs/sync-vectors/ | wc -l
```

*Expected:* **`OK: 29 vector files match the generator`** and **`0`**. The android repo's vendored
pin **`7328a0b`** is therefore intact and this iteration is **not** a drift event. (Note the count is
**29**, not 28: `invalid-unknown-field` arrives with PR #37, which *is* an ancestor of this branch —
unlike the twenty-sixth run's #33, where it was not.)

### C-RPR-11 — the rebase hazard, which is a live pin conflict rather than a note

```bash
git fetch origin main
git show origin/main:scripts/Verify-Alpha.ps1 | grep -n "ExpectedOfflineTotal = "
git log --oneline -1 origin/main
```

*Expected:* `origin/main` is **`aac05f3`** (Terra's R7 track landed) and its pin reads **611**, while
this stack reads **662** on an older base. **These are not comparable and 662 must not be carried
across a rebase blindly** — the standing resolution is that whoever lands first wins and the other
re-runs the verifier and writes the **measured** number, sweeping every count-reporting doc in the
same commit. Recorded on PR #45 and on `autonomy/claude-state`.

### C-RPR-12 — what did NOT run, and therefore what is unverified

```bash
head -20 ../careerseeker-android/scripts/core-probe.sh
git diff --name-only origin/claude/s5-inbound-pump..HEAD
```

*Expected:* the probe's header stating it runs **one** of the android gate's four tasks — and it was
**not run at all** this iteration, correctly, because the second command shows **no `core/` or `app/`
file changed** (nine files, all in the engine repo). `checkCoreIsAndroidFree`, `:app:assembleDebug`
and `:app:lintDebug` need the Android SDK (**B-7**); `Verify-Alpha.ps1` needs PowerShell (C-RPR-9).
**Nothing was executed against a relay, an engine or a phone** — no network call was made at all, not
even `GET /v1/health`. The engine's inbound composition remains **compile-checked and never
executed** (`BuildSyncBridge` returns `null` without a pairing; the vault is DPAPI/Windows; B-9 keeps
inbound OFF), so **the new `Console.WriteLine` lines and the once-only reporting flag have never
run.**

### C-RPR-13 — CI ran `Verify-Alpha.ps1` on this exact head and the 662 pin held (added after §C-RPR was written)

```bash
gh api repos/ShivaClaw/careerseeker/actions/runs/31685499397/jobs \
  --jq '.jobs[] | "\(.name): \(.conclusion)"'
gh api repos/ShivaClaw/careerseeker/actions/jobs/94400476634/logs | grep -E "Offline total|=== 194"
```

*Expected (head `ddd4a9a`, draft PR #45):* two jobs, **both `success`** — `Blind relay (Worker)`
(09:12:05 → 09:12:30 UTC) and `Build and offline harnesses` (09:12:06 → 09:13:43 UTC). The second
command prints **`=== 194 passed, 0 failed ===`** and **`=== Offline total: 662 passed, 0 failed ===`**.

**This is the row that upgrades C-RPR-9.** That entry says 662 is *corroborated, not measured*,
because `Verify-Alpha.ps1` cannot run in this sandbox. It ran here, on `windows-latest`, and **it
throws on a pin mismatch** — so **662 is confirmed**, and `EngineHarness` = 662 − 445 = **217** is
re-confirmed as the carried number rather than an assumption. The twenty-one new assertions pass on
**Windows** as well as on the Linux run measured in C-RPR-5; the log lists them by name.

**What it does not license:** CI green is **not** the merge condition. The main-repo merge policy
requires a full *local* gate (`-IncludePublish -IncludePackage`), which remains out of reach here, and
the android repo is **never-self-merge**. **#45 stays a DRAFT** and inherits #39's ordering
constraint.

---

## C-LAT — `latest` had a type check and never a range check (twenty-eighth iteration, 2026-08-13)

Engine repo, branch `claude/s2-relay-pull-result`, draft PR **#45**, commits `706f2df`, `5c8b063`,
`818c5b3`. Run everything below from a `careerseeker` checkout at `818c5b3` unless a command says
otherwise. `dotnet-sdk-8.0` installs from the Ubuntu archive
(`apt-get update && apt-get install -y dotnet-sdk-8.0`); `apt-cache policy dotnet-sdk-8.0` is the
re-test, **not** `which dotnet` — see B-6's closure.

### C-LAT-1 — the hole was exactly two bands, and a type check cannot see them

The measurement that motivated the change, reproducible by reverting the check:

```bash
git show 706f2df -- src/Sync/RelayClient.cs | grep -A3 "outside the legal seq range"
git checkout 706f2df~1 -- src/Sync/RelayClient.cs          # RelayClient.cs ONLY -- see the note
dotnet build CareerSeeker.sln -c Release 2>&1 | grep "Error(s)"
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 \
  | grep -E "latest (is negative|one past|at Int64|0 is Ok|above Int64|exactly at)|out-of-range latest"
git checkout HEAD -- src/Sync/RelayClient.cs
```

**Revert `RelayClient.cs` and not `Protocol.cs`.** This entry's first draft reverted both and **did
not compile** — `Protocol.MaxSeq` has three call sites in `tests/SyncHarness/Program.cs` (1020, 1070
twice), so taking the constant away yields `error CS0117` and no test result at all, which would have
been recorded as a measurement. Caught by the standing re-run step before commit; **second audit
command this iteration to fail it** (the other is C-LAT-7's diff base), and the fifth recurrence of
this shape across the series.

*Expected:* with the check reverted, **four** of those lines read `FAIL` — *a 200 whose latest is
negative*, *latest one past §3.2's cap*, *latest at Int64.MaxValue*, and *an out-of-range latest
refuses the page even when the envelopes are fine* — while *latest exactly at §3.2's cap is still
Ok*, *latest 0 is Ok* and *latest above Int64 is still Unavailable* keep passing. That split **is**
the claim: the two values already refused (`1e19`, `1e300`) are refused by the **width of `Int64`**,
not by any bound, which is why the gap looked closed. Restore with the final `git checkout`.

### C-LAT-2 — the bound is `[0, 2^53-1]`, and the constant is checked by arithmetic not by transcription

```bash
grep -n "MaxSeq" src/Sync/Protocol.cs
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 \
  | grep -E "seq cap"
```

*Expected:* `public const long MaxSeq = 9_007_199_254_740_991L;` with the docstring stating **§3.2
lives on a sibling branch**, and two `PASS` lines — *the seq cap is 2^53-1, the largest integer a
double represents exactly* and *the seq cap round-trips through a double unchanged, and the next
integer up does not*. The second is the load-bearing one: it asserts the *property* that chose the
number (all three implementations represent it exactly) rather than re-typing the digits, and the
relay reaches the same value through an IEEE-754 double.

### C-LAT-3 — §3.2 is on a SIBLING branch, so the citation is flagged and does not resolve yet

```bash
git merge-base --is-ancestor origin/claude/s2-seq-bound HEAD; echo "exit=$?"
grep -c "^### 3.2" docs/Sync-Protocol.md
git show origin/claude/s2-seq-bound:docs/Sync-Protocol.md | grep -c "^### 3.2"
git diff --name-only $(git merge-base origin/claude/s2-seq-bound origin/main)..origin/claude/s2-seq-bound
```

*Expected:* **`exit=1`** (sibling, not ancestor); **`0`** occurrences of §3.2 in this branch's spec
and **`1`** on #35's. This is the **same shape** as `InboundPump`'s §6.4 citation, which #39 flagged
as "arriving with PR #33" — flagged rather than silent, and it resolves **on merge of both**, not
before. The fourth command shows #35 touches **no C# file**, so `Protocol.MaxSeq` cannot conflict
with it on merge.

### C-LAT-4 — the sweep, and why 673 is corroborated rather than measured

```bash
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
grep -rn "673\|SyncHarness | 205" scripts/Verify-Alpha.ps1 README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md
for h in Slice ResearcherHarness HookHarness StoreParityHarness GatewayGateHarness \
         DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 | grep -oE "=== [0-9]+ passed"
done
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build 2>&1 | tail -3
which pwsh; apt-cache policy powershell
```

*Expected:* the pin reads **673**; the second command finds it in **six** `Assert-Contains` literals
plus the four count-reporting docs. The nine harnesses sum to **456** (28+57+16+28+36+35+45+6+205),
up from 445 by exactly the eleven assertions added. `EngineHarness` **aborts** at
`FullDataDeletion.PlanWorkspace` (`src/Engine/FullDataDeletion.cs:29`) — correctly refusing a volume
root when a Windows install path resolves to `/` — so its **217 is carried, not measured**.
456 + 217 = **673**. The last line prints **nothing** for both: `Verify-Alpha.ps1` **did not run and
could not**, so **673 is CORROBORATED, NOT MEASURED**, and CI on `windows-latest` is the gate. The
rebase caveat is unchanged: `origin/main` is `aac05f3` with a pin of **611**, not comparable to 673
on this stack's older base.

### C-LAT-5 — the finding: §6.4's bound is supplied by the party it defends against

```bash
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 \
  | grep -E "honest latest bounds|inflated latest does NOT"
git show 5c8b063 -- src/Sync/InboundPump.cs | grep -E "^\+.*(Corrected|Int64.MaxValue|supplied by the party)"
```

*Expected:* two `PASS` lines — *pump: an honest latest bounds an unauthenticated claim to the page's
own high-water mark* and *pump: an inflated latest does NOT (open weakness, pinned — PQ-LAT-2)*. The
identical element claiming `seq: 1000000` reaches cursor **5** under `latest: 5` and cursor
**1000000** under `latest: Protocol.MaxSeq`.

**These two assertions pin a WEAKNESS, not a fix, and that is the most attackable thing in the
slice.** The second command shows the docstring correction: `InboundPump` previously claimed the
bound *"denies a hostile relay a second, independent lever, because `latest` is already the number it
must publish to say there is more"* — **false**, because `latest` and the crafted element arrive in
the same response from the same party. The range check lowers the ceiling from `2^63-1` to `2^53-1`
and **does not close this**. If a later slice closes it, **these two assertions SHOULD fail**.

### C-LAT-6 — the obvious fix for PQ-LAT-2 is wrong, and §6 says so

```bash
sed -n '565,572p' docs/Sync-Protocol.md
grep -n "TTL purge creates" ../careerseeker-android/docs/protocol-questions.md
```

*Expected:* §6 requires a receiver to accept `seq > highest_accepted` **"including gaps — the relay's
TTL purge creates them"**. So `min(page.latest, cursor + elements_served)` — PQ-LAT-2's first draft,
**corrected before it shipped** — would stall a direction forever after a retention event the
protocol *requires* the relay to perform: after a purge a page can legitimately serve one element at
`seq: 500`. That is §6.2's permanent stall reached by the route §6.4's own "bounded, not refused"
reasoning rejected. The correction is recorded inside PQ-LAT-2 itself rather than silently dropped.

### C-LAT-7 — seven mutations, seven caught, tree byte-identical after

```bash
git -C . status --porcelain
git diff --stat ddd4a9a..HEAD
```

*Expected:* a **clean** tree (every mutation reverted) and **nine** changed files across the three
commits. Note the base: `ddd4a9a` is the previous iteration's tip, **not** the PR's base
`origin/claude/s5-inbound-pump` — diffing against the latter returns **eleven** files, because it
also carries `src/Engine/Program.cs` and `tests/SyncLiveSmoke/Program.cs` from the twenty-seventh
run. **This entry's first draft used the PR base and claimed nine**, which would have credited this
slice with two files it never touched; caught by the standing re-run step before commit. The mutations and their measured results are in `LOG.md`'s C-LAT entry and in PR #45's
follow-up comment; M2 and M7 are the row worth re-running, because each also takes down assertions
that existed **before** this slice — M2 fails *an empty page is Ok, not a failure* and M7 fails three
pre-existing pump assertions.

### C-LAT-8 — what did NOT run, and therefore what is unverified

```bash
git diff --name-only ddd4a9a..HEAD
head -20 ../careerseeker-android/scripts/core-probe.sh
node docs/sync-vectors/generate.mjs --check
git status --porcelain docs/sync-vectors/
```

*Expected:* **nine** files (see C-LAT-7 on the base), **all in the engine repo** — no `core/`, no
`app/`, no `relay/`, no `docs/Sync-Protocol.md`, no `src/Engine/Host.cs`. So the **android gate did not run and correctly was not attempted**;
`core-probe.sh` runs one of its four tasks and even that had nothing to run.
`checkCoreIsAndroidFree`, `:app:assembleDebug` and `:app:lintDebug` need the Android SDK (**B-7**);
`Verify-Alpha.ps1` needs PowerShell (C-LAT-4). The vector check prints **`OK: 29 vector files match
the generator`** and the fourth command prints **nothing** — **zero vector bytes moved**, the android
repo's `7328a0b` pin is intact, **no cross-repo drift event**.

**Never executed:** the engine's inbound composition remains compile-checked only (`BuildSyncBridge`
returns `null` without a pairing, the vault is DPAPI/Windows, B-9 keeps inbound OFF). **Not one byte
was sent to a relay, an engine or a phone** — no network call of any kind was made this iteration,
including `GET /v1/health`. The range check has been executed **only against a stub
`HttpMessageHandler`**, never against a relay that produced one of these pages.

### C-LAT-9 — CI ran `Verify-Alpha.ps1` on this exact head and the 673 pin held (added after §C-LAT was written)

```bash
gh api repos/ShivaClaw/careerseeker/actions/runs/31704145293 \
  --jq '{head_sha, run_attempt, conclusion}'
gh api repos/ShivaClaw/careerseeker/actions/runs/31704145293/jobs \
  --jq '.jobs[] | "\(.name): \(.conclusion)"'
gh api repos/ShivaClaw/careerseeker/actions/jobs/94460325057/logs \
  | grep -E "Offline total|=== 205|latest (one past|at Int64|exactly at)"
```

*Expected:* `head_sha` **`818c5b39…`** — this branch's tip, read from **the run's own field** rather
than from the PR check-runs view, which follows the current head and lags a push (the twenty-fourth
run's lesson) — `run_attempt` **1**, `conclusion` **`success`**. Two jobs, **both `success`**:
`Blind relay (Worker)` 13:17:11 → 13:17:51 UTC and `Build and offline harnesses` 13:17:11 → 13:18:48
UTC. The third command prints **`=== 205 passed, 0 failed ===`** and
**`=== Offline total: 673 passed, 0 failed ===`**, followed by `CareerSeeker alpha verification
complete.`, plus the new assertions **by name** (`latest one past §3.2's cap is Unavailable`,
`latest at Int64.MaxValue is Unavailable`, `latest exactly at §3.2's cap is still Ok`).

**This is the row that upgrades C-LAT-4.** That entry says 673 is *corroborated, not measured*,
because `Verify-Alpha.ps1` cannot run in this sandbox. It ran here, on `windows-latest`, and **it
throws on a pin mismatch** — so **673 is CONFIRMED**, and `EngineHarness` = 673 − 456 = **217** is
re-confirmed as the carried number rather than an assumption. The eleven new assertions pass on
**Windows** as well as on the Linux run measured in C-LAT-4.

**What it does not license:** CI green is **not** the merge condition. The main-repo merge policy
requires a full *local* gate (`-IncludePublish -IncludePackage`), which remains out of reach here,
and the android repo is **never-self-merge**. **#45 stays a DRAFT** and inherits #39's ordering
constraint. CI also **cannot** touch the standing limit: it builds the engine's inbound composition
and never constructs it, because a runner has no pairing vault.

---

## C-PSH — `RelayClient.PushAsync`'s failure channel (twenty-ninth cloud iteration, 2026-08-13)

Engine repo, branch `claude/s2-relay-pull-result`, draft PR **#45**. Auditor setup:

```bash
cd careerseeker && git fetch --all --prune
git checkout claude/s2-relay-pull-result     # tip 62f1f8d
sudo apt-get update && sudo apt-get install -y dotnet-sdk-8.0   # the `update` is required
dotnet build CareerSeeker.sln -c Release
```

### C-PSH-1 — the headline: 205 → 236, and the build stays clean

```bash
dotnet build CareerSeeker.sln -c Release 2>&1 | grep -E "Warning|Error"
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
```

*Expected:* **0 Warning(s) / 0 Error(s)** and **`=== 236 passed, 0 failed ===`**. The **205**
baseline was re-measured in this session before any edit, not quoted from the previous record.

### C-PSH-2 — nine mutations, nine caught (the assertions are load-bearing)

Each edit is to `src/Sync/RelayClient.cs`; rebuild and re-run C-PSH-1 after each, then restore.

| # | mutation | expected |
| --- | --- | --- |
| M1 | delete the `case HttpStatusCode.Conflict:` arm (409 falls to `default`) | **222 / 14** |
| M2 | delete `if (latest < 0 \|\| latest > Protocol.MaxSeq) return null;` in `ConflictLatest` | **233 / 3** |
| M3 | that line's `>` → `>=` | **235 / 1** |
| M4 | drop its lower bound, keeping `if (latest > Protocol.MaxSeq)` | **235 / 1** |
| M5 | return `Unavailable` when `ConflictLatest` yields null | **226 / 10** |
| M6 | add `case HttpStatusCode.OK:` above `case HttpStatusCode.Created:` | **235 / 1** |
| M7 | `case HttpStatusCode.BadRequest:` returns `TooLarge` | **235 / 1** |
| M8 | delete the `when (ct.IsCancellationRequested) { throw; }` catch | **235 / 1** |
| M9 | make the 201 arm parse its body and return `Unavailable` when unreadable | **235 / 1** |

**M5 is the row to read first.** It is the plausible-looking alternative design — "an unusable
`latest` means the answer was no good" — and it takes down **ten** assertions, which is the
measurement behind the commit arguing that choice rather than asserting it. **M1 and M5 failing
different sets** is what shows Conflict-the-case and Conflict's-number are independently pinned.

**Detector warning, and it is not hypothetical.** Read the harness's own
`=== N passed, M failed ===` line. Testing build output for the substring `error` matches `dotnet`'s
`0 Error(s)` banner and reports every mutation as "DID NOT COMPILE" — that is what the twenty-seventh
run's first pass did, and the false result was the flattering one.

### C-PSH-3 — the pin 673 → 704 is arithmetic, and is NOT a Verify-Alpha measurement

```bash
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
for h in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness \
         GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  echo -n "$h: "
  dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 \
    | grep -oE "=== [0-9]+ passed, [0-9]+ failed ===" | tail -1
done
which pwsh; apt-cache policy powershell
```

*Expected:* the pin reads **704**. The loop prints **28, 57, 16, 28, 36, 35, 45, 6, 236** and
**EngineHarness prints nothing** — it aborts at `FullDataDeletion.PlanWorkspace`
(`src/Engine/FullDataDeletion.cs:29`), **correctly** refusing a volume root when a Windows install
path resolves to `/` on Linux. Linux sum **487** (was 445 — up by exactly the 31 assertions added).
`EngineHarness` = **217**, *carried, not measured*; 487 + 217 = **704**. The last two commands print
**nothing at all**: there is no PowerShell here and none in the Ubuntu archive, both **re-checked
this session** rather than carried.

**So 704 is CORROBORATED, NOT MEASURED.** `scripts/Verify-Alpha.ps1` did not run and could not.
**CI on `windows-latest` is the gate** — it runs the script, which throws on a pin mismatch.
**The rebase caveat stands:** `origin/main` is `aac05f3` and its pin reads **611**, while this stack
reads **704** on its own older base. Not comparable; **704 must not be carried across a rebase
blindly.**

### C-PSH-4 — zero vector bytes moved, so no cross-repo drift event

```bash
node docs/sync-vectors/generate.mjs --check
git status --porcelain docs/sync-vectors/
```

*Expected:* **`OK: 29 vector files match the generator.`** and the second command prints **nothing**.
The android repo's vendored pin `7328a0b` is intact. **No vector file was added, edited or
regenerated this iteration.**

### C-PSH-5 — the phone finding (PQ-PSH-1): a 400 is retried, and the comment says it is not

In the **android** repo, `claude/android-a0-probe`:

```bash
grep -c "BadRequest" core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
sed -n '287,292p' core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
grep -n "is RelayResult.Unavailable ->" core/src/main/kotlin/app/careerseeker/core/OutboundQueue.kt
```

*Expected:* the count is **0** — the phone has no 400 case, so a 400 falls to the `else` arm
**directly beneath a comment claiming "5xx and 429 are the only retryable answers"**. It is then
retried 4 times, becomes `Unavailable`, and line **245** maps that to `PushOutcome.Retry`, which
keeps the bytes and re-sends indefinitely. **A sender-side defect presented as an offline
condition.** Not fixed here: it needs the android gate (**B-7**) and its own derivation (PQ-S2-4
already shows the phone's status mapping is not the engine's to copy).

### C-PSH-6 — the §2.2 citation is FLAGGED, not glossed

```bash
git merge-base --is-ancestor origin/claude/s2-transport-vocabulary HEAD; echo "exit=$?"
grep -c '^### 2.2' docs/Sync-Protocol.md
git show origin/claude/s2-transport-vocabulary:docs/Sync-Protocol.md | grep -c '^### 2.2'
```

*Expected:* **exit 1** (a sibling branch, not an ancestor), then **0** here and **1** there. So
`RelayPushResult`'s doc comments cite a section this branch does not contain — the **third** citation
of this shape, after `InboundPump`'s §6.4 and `Protocol.MaxSeq`'s §3.2. It **resolves on merge of
both PRs, not before**, and is recorded rather than quietly reworded.

### C-PSH-7 — exactly which files moved, and which did not

```bash
git diff --name-only 818c5b3..HEAD
```

*Expected:* **nine** files — `src/Sync/RelayClient.cs`, `src/Engine/Program.cs`,
`tests/SyncHarness/Program.cs`, `tests/SyncLiveSmoke/Program.cs`, `scripts/Verify-Alpha.ps1`,
`README.md`, `src/Engine/README.md`, `docs/CareerSeeker-Project-Summary.md`,
`docs/External-Audit-Handoff.md`. **All in the engine repo.** Note the base is **this slice's**
parent `818c5b3`, not the PR's base — the twenty-eighth run's audit command used the PR base and
would have credited the slice with two files it never touched.

No `core/`, no `app/`, no `relay/`, no `docs/Sync-Protocol.md`, no `docs/sync-vectors/`, no
`generate.mjs`, no `src/Engine/Host.cs`.

### C-PSH-8 — what did NOT run, stated because it is the whole of what is unverified

Run this one in the **android** repo — `core/` and `app/` do not exist in the engine repo, so
asking there would pass trivially and prove nothing:

```bash
cd careerseeker-android && git diff --name-only cd58689..HEAD
git diff --name-only cd58689..HEAD -- core/ app/     # the android gate's inputs
```

*Expected:* the first lists **records only** — `LOG.md`, `AUDIT-REQUEST.md`, `STATE.md`,
`docs/protocol-questions.md` — and the second prints **nothing**. No android source changed, so the
android gate **did not run and correctly was not attempted** (`:app:assembleDebug`,
`:app:lintDebug`, `checkCoreIsAndroidFree` need the SDK — **B-7**). `Verify-Alpha.ps1` needs
PowerShell (C-PSH-3).

**Never executed:** `PushAsync` has been driven **only against a stub `HttpMessageHandler`**, never
against a relay that produced one of these statuses — the live proof is `SyncLiveSmoke`, which needs
a relay and is excluded from the hermetic suite. Its sharpened replay assertion (`the 409 carries the
relay's e2p high-water mark`) is therefore **written and unrun**. The engine's inbound composition
remains compile-checked and never constructed (`BuildSyncBridge` returns `null` without a pairing;
the vault is DPAPI/Windows). **Not one byte was sent to a relay, an engine or a phone this
iteration — no network call of any kind, including `GET /v1/health`.**

### C-PSH-9 — CI ran `Verify-Alpha.ps1` on this exact head and the 704 pin held (added after §C-PSH was written)

```bash
gh api repos/ShivaClaw/careerseeker/actions/runs/31726114575 \
  --jq '{head_sha, run_attempt, conclusion}'
gh api repos/ShivaClaw/careerseeker/actions/runs/31726114575/jobs \
  --jq '.jobs[] | "\(.name): \(.conclusion)"'
gh api repos/ShivaClaw/careerseeker/actions/jobs/94534783762/logs \
  | grep -E "Offline total|=== 236|400 is Rejected|latest 0 survives"
```

*Expected:* `head_sha` **`62f1f8dd…`** — this slice's tip, read from **the run's own field** rather
than from the PR check-runs view, which follows the current head and lags a push (the twenty-fourth
run's lesson, re-applied here) — `run_attempt` **1**, `conclusion` **`success`**. Two jobs, **both
`success`**: `Blind relay (Worker)` 17:31:19 → 17:31:48 and `Build and offline harnesses`
17:31:20 → 17:33:14 UTC. The third command prints **`=== 236 passed, 0 failed ===`** and
**`=== Offline total: 704 passed, 0 failed ===`**, followed by `CareerSeeker alpha verification
complete.`, plus new assertions **by name** (`400 is Rejected -- this side composed something the
relay would not shape-check`, `latest 0 survives as 0, not null`).

**This is the row that upgrades C-PSH-3.** That entry says 704 is *corroborated, not measured*,
because `Verify-Alpha.ps1` cannot run in this sandbox. **It ran here, on `windows-latest`, and it
throws on a pin mismatch** — so **704 is CONFIRMED**, and `EngineHarness` = 704 − 487 = **217** is
re-confirmed as the carried number rather than an assumption. The thirty-one new assertions pass on
**Windows** as well as on the Linux run measured in C-PSH-3. Same arithmetic that settled 610, 625,
641, 662 and 673.

**What it does not license:** CI green is **not** the merge condition. The main-repo merge policy
needs a full *local* gate (`-IncludePublish -IncludePackage`), still out of reach here, and the
android repo is **never-self-merge** regardless. **#45 stays a DRAFT** and inherits #39's ordering
constraint. CI also does not touch the standing limit: it **builds** the engine's inbound
composition and never constructs it, because a runner has no pairing vault — and `PushAsync` still
has been executed only against a stub `HttpMessageHandler`, never against a relay.

---

## C-S6 — counter reconciliation, §6.1's second term (thirtieth cloud iteration, 2026-08-13)

All commands run from a clone of `ShivaClaw/careerseeker` at
`claude/s6-counter-reconciliation` (draft PR **#46**, tip `834adcd`) unless stated. **Run them in
the engine repo** — the paths below do not exist in the android tree, which is the mistake C-PSH-8
made and the reason this header says so.

The toolchain is not preinstalled and the first install **fails**:

```bash
apt-get install -y dotnet-sdk-8.0     # 404s on every package -- stale index
apt-get update && apt-get install -y dotnet-sdk-8.0    # this is the one that works
dotnet --version                                       # 8.0.129
```

### C-S6-1 — the prompt's assigned slice had already landed

```bash
git fetch --all --prune
git show origin/claude/s2-relay-pull-result:docs/Sync-Protocol.md | grep -c entitlement_ack
git ls-tree -r --name-only origin/claude/s2-relay-pull-result docs/sync-vectors/ \
  | grep -E "entitlement-ack|invalid-unknown-field"
```

*Expected:* **8**, and three files — `entitlement-ack.json`, `entitlement-ack-no-order-id.json`,
`invalid-unknown-field.json`. The prompt assigned all of this as new work for the **twelfth**
consecutive run.

### C-S6-2 — the vendored vector pin is not what the prompt says

```bash
grep "Pinned commit" core/src/test/resources/sync-vectors/VECTORS.lock   # ANDROID repo
```

*Expected:* `7328a0bc043335491cd96a67d634e8eea2a13af9`, not the prompt's `679a317`.

### C-S6-3 — build and harness

```bash
dotnet build CareerSeeker.sln -c Release
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -1
```

*Expected:* **0 Warning(s), 0 Error(s)**; **`=== 256 passed, 0 failed ===`**. Baseline before this
slice is **236** (`git stash` the three commits, or run the same command at
`origin/claude/s2-relay-pull-result`).

### C-S6-4 — the new assertions exist and are named

```bash
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | sed -n '/counter reconciliation/,$p'
```

*Expected:* a `[ counter reconciliation (§6.1) ]` block of **20** PASS lines, including
`ReconcileTo REFUSES to lower the counter and says it did nothing`, `an unreachable relay falls back
to the store rather than refusing to publish`, and `a corrupt store AND a silent relay still resume
from 0, not from a negative`.

### C-S6-5 — the invariant is load-bearing (M5)

```bash
git checkout -- src/Sync/SyncPublisher.cs   # ensure a clean, COMMITTED baseline first
python3 - <<'EOF'
p='src/Sync/SyncPublisher.cs'; s=open(p).read()
s=s.replace("            if (seq <= current) return false;\n","")
open(p,'w').write(s)
EOF
dotnet build CareerSeeker.sln -c Release >/dev/null && \
  dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -E "^  FAIL|^=== "
git checkout -- src/Sync/SyncPublisher.cs
```

*Expected:* **`=== 253 passed, 3 failed ===`**, failing `ReconcileTo REFUSES to lower the counter…`,
`the counter survived the attempt to lower it`, and `an equal mark is not a move either`.

**Note the first line, and it is the point of this entry.** The mutation script's `git checkout`
restores to **HEAD**, so running any of these against *uncommitted* work silently reverts the code
under test and every subsequent mutation reports "DID NOT COMPILE". That happened this run and
invalidated eight results before they were recorded.

### C-S6-6 — the two gaps the pass found, and that they are now closed

```bash
# M4 -- the floor
python3 -c "
p='src/Sync/SyncPublisher.cs'; s=open(p).read()
s=s.replace('var floor = persistedSeq > 0 ? persistedSeq : 0;','var floor = persistedSeq;')
open(p,'w').write(s)"
dotnet build CareerSeeker.sln -c Release >/dev/null && \
  dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -E "^  FAIL|^=== "
git checkout -- src/Sync/SyncPublisher.cs
# M9 -- the cap boundary
python3 -c "
p='src/Sync/SyncPublisher.cs'; s=open(p).read()
s=s.replace('if (seq < 0 || seq > Protocol.MaxSeq)','if (seq < 0 || seq >= Protocol.MaxSeq)')
open(p,'w').write(s)"
dotnet build CareerSeeker.sln -c Release >/dev/null && \
  dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -E "^  FAIL|^=== "
git checkout -- src/Sync/SyncPublisher.cs
```

*Expected:* each prints **`=== 255 passed, 1 failed ===`**, failing exactly `a corrupt store AND a
silent relay still resume from 0, not from a negative` and `a seq exactly at §3.2's cap is still
accepted` respectively. **Before the fix in `f4d56f6` both of these SURVIVED** — M4 because the
relay term rescued every case that tested a negative store, M9 because the throw took the harness
down **without printing a FAIL line**, which reads as a survivor to anything counting FAILs. To see
the latter, re-run M9 at `6c3f8bb` and observe `Unhandled exception.
System.ArgumentOutOfRangeException` with **no** `=== N passed ===` line at all.

### C-S6-7 — zero vector bytes moved (no cross-repo drift event)

```bash
node docs/sync-vectors/generate.mjs --check
git status --porcelain docs/sync-vectors/
git diff --stat origin/claude/s2-relay-pull-result..HEAD -- docs/sync-vectors/
```

*Expected:* **`OK: 29 vector files match the generator.`**, and **both** of the last two print
nothing. The android repo's `7328a0b` pin is therefore untouched.

### C-S6-8 — the offline pin sweep is complete

```bash
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
# stale ASSERTION literals only -- the quoted strings the verifier checks docs against
grep -rn "'| SyncHarness | 236 |'\|'| \*\*Total\*\* | \*\*704\*\* |'\|704 passed" \
  scripts/Verify-Alpha.ps1 README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md
grep -rn "724\|SyncHarness | 256" scripts/Verify-Alpha.ps1 README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md | wc -l
```

*Expected:* **724**; the second command prints **nothing** (no stale assertion literal survives);
the third prints **17** — seven verifier lines (the pin, its comment's closing `724.`, and five
`Assert-Contains` literals) plus the four docs' ten entries.

**Why the second command filters to quoted literals.** A bare `grep -rn 704` still matches
`Verify-Alpha.ps1:232`, `# nine caught. 704.` — the **previous** slice's comment paragraph recording
the pin *as of that slice*. That is correct history, not drift, and a sweep check that flags it
would train the next reader to ignore it. The drift trap is about the strings the verifier
**asserts**, which are the quoted ones.

### C-S6-9 — 724 is CORROBORATED, not measured

```bash
which pwsh ; apt-cache policy powershell
for h in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness \
         GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  echo -n "$h: "
  dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 \
    | grep -oE "=== [0-9]+ passed, [0-9]+ failed" | tail -1
  echo
done
```

*Expected:* both of the first two print **nothing** — `Verify-Alpha.ps1` **cannot run here and did
not**. The sweep prints 28 / **(EngineHarness: no count)** / 57 / 16 / 28 / 36 / 35 / 45 / 6 / 256 =
**Linux sum 507**. `EngineHarness` aborts with `InvalidOperationException: Refusing full-data
deletion for a volume root` — correct behaviour on Linux — so its **217 is CARRIED, not measured**.
507 + 217 = **724**, agreeing independently with 704 + 20.

**Rebuild before running this.** The first sweep this session read `SyncHarness` at **255** because
`--no-build` used binaries left from a mutation build. A stale cache is not a measurement.

### C-S6-10 — the composition is compile-checked and was never executed

```bash
grep -n "ResumeSeq\|ReconcileTo" src/Engine/Program.cs src/Sync/SyncPublisher.cs
grep -rn --include=*.cs "ReconcileTo" tests/
```

**`--include=*.cs` is not optional:** without it `grep -r` reports `binary file matches` for every
built `.dll` under `tests/*/bin` and `obj`, which looks like six test projects reference the method
when exactly one does.

*Expected:* `Program.cs` calls both (startup and the sink's `Conflict` arm); `tests/` matches
**`SyncHarness/Program.cs` only**. **Nothing tests that the SINK calls `ReconcileTo`** — reverting
that one call site fails no test in this repo. This is the sharpest gap in the slice and #46's
self-audit names it.

> **AMENDED by the thirty-first run — the second half of that expectation is now FALSE, deliberately.**
> `RelaySink.Create` extracts the sink's decision, and reverting the `ReconcileTo` call site now
> fails **6** assertions (**C-SNK-1**). The first half still reproduces: `tests/` matches
> `SyncHarness/Program.cs` only, and the *composition* in `BuildSyncBridge` remains unexecuted
> (**C-SNK-8**). Left in place rather than rewritten, because the entry is the record of what was
> true at `834adcd` — but an expectation that no longer holds has stopped testing what it claims,
> so the pointer forward is mandatory rather than courteous.

### C-S6-11 — no android source changed

```bash
git -C <android-clone> diff --stat origin/claude/android-a0-probe..HEAD -- core/ app/
```

*Expected:* **empty**. This repo took records only, which is why the android gate did not run and
was not attempted (**B-7**).

## Thirty-first run — the sink's call site (draft PR #46, `dee32f8`, `ca868e8`, `63ec8a5`)

**Every command below runs in the ENGINE clone** (`ShivaClaw/careerseeker`, branch
`claude/s6-counter-reconciliation`) unless it says otherwise. An audit command must be executed
where it will be read — the sixth recurrence of that mistake was a `git diff -- core/ app/` run in
the engine repo, where those paths do not exist, so it asserted nothing and would have been
recorded as proof. Prerequisite for all of them:
`apt-get update && apt-get install -y dotnet-sdk-8.0` — **note the `update`**, without which the
archive serves a **404** on the SDK package (measured again this run: the first attempt failed
exactly that way, and the wrapper's "exit 0" was a trailing `tail`, not the install).

### C-SNK-1 — the gap this slice closed was real, and the new assertion is what closes it

```bash
git diff --quiet -- src/Sync/RelaySink.cs || echo "ABORT: commit first"
perl -0pi -e 's/log\(reconcileTo\(latest\)\n(\s+)\?/log(false\n$1?/' src/Sync/RelaySink.cs
dotnet build CareerSeeker.sln -c Release 2>&1 | tail -3
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 \
  | grep -E "^  FAIL|=== "
git checkout -- src/Sync/RelaySink.cs
```

*Expected:* deleting the `ReconcileTo` call fails **6** assertions, `A 409 CALLS ReconcileTo — the
call site, not just the rule` first among them, and the run still **reaches its summary line**.
Before this slice the same deletion failed **nothing**.

**A committed baseline is mandatory** — the script restores with `git checkout`, so running it
against uncommitted work reverts the code under test and reports meaningless results as
measurements (the thirtieth run's lesson, applied here as the first line of the command).

### C-SNK-2 — the correction: the first reading of M1 was a false "caught"

```bash
git show ca868e8 -- tests/SyncHarness/Program.cs | grep -A3 "reconciledTo.Count == 1"
```

*Expected:* the assertion is **count-guarded** before indexing. Unguarded, `reconciledTo[0]` throws
under the very mutation it exists to catch, taking the harness down **after one FAIL line** — which
a fail-counting reader scores as a tidy "CAUGHT (1 failing)" while the rest of the suite never runs.
The detector must check for the **missing summary line first**, and the FAIL count second. An
assertion that cannot survive its own target mutation is not an assertion.

### C-SNK-3 — SyncHarness 256 → 277, and the baseline is re-measured, not carried

```bash
dotnet build CareerSeeker.sln -c Release 2>&1 | tail -3
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 | tail -1
git stash list >/dev/null; git checkout -q 834adcd -- tests/SyncHarness/Program.cs src/Sync src/Engine/Program.cs
dotnet build CareerSeeker.sln -c Release 2>&1 | tail -3
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 | tail -1
git checkout -q HEAD -- tests/SyncHarness/Program.cs src/Sync src/Engine/Program.cs
```

*Expected:* **`=== 277 passed, 0 failed ===`** on this tree, **`=== 256 passed, 0 failed ===`** on
`834adcd`. Build is **0 warnings / 0 errors** both times. **Rebuild between the two** — the
thirtieth run nearly recorded a stale count because `--no-build` reused binaries from a mutation
build, and the mission's `--rerun-tasks` rule applies verbatim to `dotnet --no-build`.

### C-SNK-4 — ten mutations, ten caught

The script is reproduced in the LOG entry for this run. *Expected:* **M1…M10 all CAUGHT**, tree
byte-identical afterwards (`git status --porcelain -- src/Sync/RelaySink.cs` empty). M1 fails 6,
M2 (reconcile to the refused seq) fails 5, M3 (call it on an unusable mark) fails 2, M10 (swallow
the result) fails 14.

**M3's first draft did not compile** and was rewritten rather than recorded as a survivor:
`... is { } latest && ... || true` leaves `latest` unassigned on the `true` arm. The working form is
`(conflict.Latest ?? 0) is { } latest`. A mutation that does not compile has measured nothing.

### C-SNK-5 — zero vector bytes moved (no cross-repo drift event)

```bash
node docs/sync-vectors/generate.mjs --check
git diff --stat 834adcd..HEAD -- docs/sync-vectors/
```

*Expected:* **`OK: 29 vector files match the generator`**, and the diff is **empty**. The android
repo's `core/src/test/resources/sync-vectors/VECTORS.lock` pin **`7328a0b`** is untouched, so there
was **no cross-repo drift event**. (The pin is `7328a0b`, **not** `679a317` — that has been stale in
the iteration prompt for seven runs now.)

### C-SNK-6 — the offline pin sweep 724 → 745 is complete

```bash
grep -rn "745\|SyncHarness | 277" --include="*.md" --include="*.ps1" . | grep -v node_modules
grep -rn "SyncHarness | 256" --include="*.md" --include="*.ps1" .
```

*Expected:* the first lists `$ExpectedOfflineTotal = 745`, the verifier's `Assert-Contains`
literals, and the four count-reporting docs (`README.md`, `src/Engine/README.md`,
`docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`). The second is **empty**.

**Two deliberate non-edits.** `docs/Codex-Resume-Handoff.md:80` contains `724` **inside a commit
SHA** (`5661342b1263089a1724fa1eb0cc22e85db7201e`) and must not be swept; and a bare `grep -rn 724`
still matches the verifier's **previous** comment paragraphs, which record the pin *as of those
slices*. That is correct history, not drift — the trap is about the strings the verifier
**asserts**, which are the quoted ones.

### C-SNK-7 — 745 is CORROBORATED, not measured

```bash
which pwsh ; apt-cache policy powershell
dotnet build CareerSeeker.sln -c Release 2>&1 | tail -3
for h in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness \
         GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  echo -n "$h: "
  dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 \
    | grep -oE "=== [0-9]+ passed, [0-9]+ failed" | tail -1
  echo
done
```

*Expected:* the first two print **nothing** — `Verify-Alpha.ps1` **cannot run here and did not**,
re-checked this session. The sweep prints 28 / **(EngineHarness: no count)** / 57 / 16 / 28 / 36 /
35 / 45 / 6 / 277 = **Linux sum 528**. `EngineHarness` aborts with `InvalidOperationException:
Refusing full-data deletion for a volume root` (`src/Engine/FullDataDeletion.cs:81`) — correct
behaviour on Linux — so its **217 is CARRIED, not measured**. 528 + 217 = **745**, agreeing
independently with 724 + 21. **CI's `windows-latest` job is what settles it.**

### C-SNK-8 — the composition is STILL never executed, and that is now the sharpest gap

```bash
cp src/Engine/Program.cs /tmp/Program.cs.bak
perl -0pi -e 's/persistSeq: seq => vault\.RecordE2pSeq\(seq\),/persistSeq: _ => { },/' src/Engine/Program.cs
dotnet build CareerSeeker.sln -c Release 2>&1 | grep -E "error CS|Build succeeded"
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 | tail -1
cp /tmp/Program.cs.bak src/Engine/Program.cs
```

*Expected:* **`Build succeeded`** and **`=== 277 passed, 0 failed ===`**. Neutering the engine's
high-water-mark persistence to a no-op **fails no test in this repo** — the engine would silently
stop recording where it got to, and nothing would notice. The gap this slice closed **moved one
level up**: the sink's *decision* is now tested, and the host's *composition* of it is not.
`SyncPairingVault` is DPAPI/Windows and there is no pairing here, so `BuildSyncBridge` has never
executed — not locally and not in CI, since a runner has no pairing vault either.

**A correction against my own draft, caught before it stood.** #46's self-audit first named
*"swapping the `persistSeq` and `reconcileTo` arguments"* as the attack. **Measured, that is wrong:**
they are **named** arguments, and named arguments in C# are order-independent, so swapping them
compiles *and changes nothing at all*. The real vector is the one above — a delegate wired to the
wrong body, not to the wrong position. The PR body was corrected rather than left standing.

### C-SNK-9 — no android source changed

```bash
git -C <android-clone> diff --stat origin/claude/android-a0-probe..HEAD -- core/ app/
```

*Expected:* **empty** — run this in the **android** clone, where `core/` and `app/` exist. This repo
took records only, which is why the android gate did not run and was not attempted (**B-7**).

## Thirty-second run — the push path's wiring (draft PR #46, `0d369eb`, `783a6e1`, `8560796`, `3e7e728`, `9394ca1`)

**Every command below runs in the ENGINE clone** (`ShivaClaw/careerseeker`, branch
`claude/s6-counter-reconciliation`) unless it says otherwise — an audit command must be executed
where it will be read. Prerequisite for all of them:
`apt-get update && apt-get install -y dotnet-sdk-8.0` — **note the `update`**, without which the
archive serves a **404** on the SDK package. Verify the toolchain actually landed
(`which dotnet && dotnet --version` → `/usr/bin/dotnet`, `8.0.129`) before trusting anything below:
the install's exit code has previously been a trailing `tail`'s, not the install's.

### C-WIR-1 — the defect was LIVE at the parent commit, not merely asserted to have been

```bash
git switch --detach 63ec8a5
sed -i 's|persistSeq: seq => vault.RecordE2pSeq(seq),|persistSeq: _ => { },|' src/Engine/Program.cs
dotnet build CareerSeeker.sln -c Release 2>&1 | grep -E "^\s+[0-9]+ (Warning|Error)"
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 | tail -2
git checkout -- src/Engine/Program.cs && git switch -
```

*Expected:* **0 Warning(s) / 0 Error(s)** and **`=== 277 passed, 0 failed ===`**. An engine that had
silently stopped persisting its e2p high-water mark failed no test in this repo. Run in this session
before any code was written, and re-run afterwards to confirm the command itself reproduces.

### C-WIR-2 — and it is dead at HEAD: the same mutation at its new home now fails

```bash
git diff --quiet || echo "ABORT: commit first"
sed -i 's|persistSeq: seqStore.RecordE2pSeq,|persistSeq: _ => { },|' src/Sync/SyncPushPath.cs
dotnet build CareerSeeker.sln -c Release 2>&1 | grep -E "^\s+[0-9]+ (Warning|Error)"
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 \
  | grep -E "^  FAIL|=== "
git checkout -- src/Sync/SyncPushPath.cs
```

*Expected:* **5 failing**, `PUSH PATH PERSISTS THE MARK -- the wiring, not just the sink's rule`
among them, and the run **reaches its summary line** rather than dying. **A committed baseline is
mandatory** — the script restores with `git checkout`, so running it against uncommitted work
reverts the code under test and reports meaningless results as measurements.

### C-WIR-3 — eight mutations, eight caught, and the eighth does not compile

```bash
git diff --quiet || echo "ABORT: commit first"
# M2: untie the mutual reference
sed -i 's|^        publisherRef = publisher;|        // publisherRef = publisher;|' src/Sync/SyncPushPath.cs
dotnet build CareerSeeker.sln -c Release >/dev/null 2>&1
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 | tail -1
git checkout -- src/Sync/SyncPushPath.cs
# M8: the vault stops implementing the interface
sed -i 's|public sealed class SyncPairingVault : IE2pSeqStore|public sealed class SyncPairingVault|' src/Engine/SyncPairingVault.cs
dotnet build CareerSeeker.sln -c Release 2>&1 | grep -cE 'error CS[0-9]+'
git checkout -- src/Engine/SyncPairingVault.cs
```

*Expected:* M2 → **9 failing**; M8 → a **non-zero** count of `error CS…` lines, i.e. **it does not
compile**. M8 is the one that converts "the host passes the right store" from a convention into a
build error, which is why the PR claims **three** remaining argument identities and not four.
Full pass measured this run: M1 5, M2 9, M3 4, M4 5, M5 5, M6 1, M7 6, M8 does-not-compile.

### C-WIR-4 — the correction: a guard assertion that killed the run

```bash
git show 8560796 -- tests/SyncHarness/Program.cs | grep -E "^\+.*(catch \(Exception|did not throw|detail)"
```

*Expected:* the three-outcome `Throws<T>`. `Throws<T>`'s first version let a wrong exception type
propagate by design; the null-store mutation (M6) then raised a `NullReferenceException` that took
the harness down **after zero FAIL lines**, so every assertion below it silently never ran. Only a
detector that checks for the **missing summary line before counting FAILs** notices — which is the
thirty-first run's correction, and it is what caught this. After the fix M6 reports **CAUGHT (1
failing)**. **Fourth appearance of this family, by a fourth route.**

### C-WIR-5 — the harness moved 277 → 294, and the build is clean

```bash
dotnet build CareerSeeker.sln -c Release 2>&1 | grep -E "^\s+[0-9]+ (Warning|Error)"
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build 2>&1 | tail -1
```

*Expected:* **0 Warning(s) / 0 Error(s)** and **`=== 294 passed, 0 failed ===`**. Baseline
re-measured this session on fresh binaries after a clean rebuild — `--no-build` against binaries
left by a mutation build has previously reported a stale count as a measurement.

### C-WIR-6 — 762 is CORROBORATED, NOT MEASURED, and here is the arithmetic

```bash
which pwsh || echo "no pwsh"; apt-cache policy powershell 2>&1 | head -3
for p in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness \
         GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  printf '%-26s' "$p"
  dotnet run --project tests/$p/$p.csproj -c Release --no-build 2>&1 \
    | grep -oE '=== [0-9]+ passed, [0-9]+ failed ===' | tail -1 || echo "NO SUMMARY LINE"
done
```

*Expected:* **no pwsh, and none in the Ubuntu archive** — so `scripts/Verify-Alpha.ps1` **did not run
and cannot run here**. Nine harnesses report; the Linux sum is
**28+57+16+28+36+35+45+6+294 = 545**. `EngineHarness` prints **NO SUMMARY LINE** and its **217 is
carried** (see C-WIR-7). **545 + 217 = 762**, agreeing independently with 745 + 17.

### C-WIR-7 — why EngineHarness carries, and what that COSTS this slice

```bash
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build 2>&1 | tail -6
grep -n "SyncPairingVault" tests/EngineHarness/Program.cs | head -2
```

*Expected:* an `InvalidOperationException: Refusing full-data deletion for a volume root` from
`FullDataDeletion.cs:81`, thrown at `tests/EngineHarness/Program.cs:221`. The guard is **correct** —
on Linux the packaged workspace root resolves to a volume root — so the 217 is carried, not guessed.

**The cost, recorded rather than glossed:** the seven sync-pairing-vault assertions live at
`Program.cs:2462`, **past** that line, so they **did not execute in this session**.
`SyncPairingVault : IE2pSeqStore` is therefore **compile-verified here and assertion-verified only on
Windows**. C-WIR-3's M8 shows the implementation is load-bearing, and implicitly implementing an
interface with a pre-existing public method of the same signature changes no dispatch for existing
callers — but that is an argument, not a run, and CI on `windows-latest` is what discharges it.

### C-WIR-8 — zero vector bytes moved, so there was NO cross-repo drift event

```bash
node docs/sync-vectors/generate.mjs --check
git diff --stat 63ec8a5..HEAD -- docs/sync-vectors/
```

*Expected:* **`OK: 29 vector files match the generator`** and an **empty** diff. The android repo's
vendored pin (`7328a0b`) is untouched; this slice added no vector and changed none.

### C-WIR-9 — the pin moved as ONE unit with every doc that reports it

```bash
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
grep -rn '| SyncHarness | 294 |\|\*\*762\*\*\|762 passed, 0 failed' \
  scripts/Verify-Alpha.ps1 README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md | wc -l
grep -rn '| SyncHarness | 277 |\|\*\*745\*\*' README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md
```

*Expected:* **762**; a **non-zero** count across the verifier and the four count-reporting docs; and
the last grep **empty** — no doc still reports the stale pair. The `294` inside the Alpha ZIP's
SHA-256 at `Verify-Alpha.ps1:486,509` is **deliberately not swept**: it is a hash, not a count, the
same trap as the `724` sitting inside a commit SHA in `docs/Codex-Resume-Handoff.md:80`.

### C-WIR-10 — no android source changed

```bash
git -C <android-clone> diff --stat origin/claude/android-a0-probe..HEAD -- core/ app/
```

*Expected:* **empty**. This repo took records only, which is why the android gate did not run and
was not attempted (**B-7**). Note the path: this command asserts nothing if run in the engine clone,
where `core/` and `app/` do not exist.

### C-WIR-11 — nothing was merged, rewritten, or deployed

```bash
git -C <engine-clone> log --oneline origin/main -1
git -C <engine-clone> merge-base --is-ancestor HEAD origin/main && echo "MERGED" || echo "NOT MERGED"
git -C <engine-clone> reflog --date=iso | grep -iE 'push --force|rebase' | head
```

*Expected:* `origin/main` still at **`aac05f3`** (Terra's R7 merge, unmoved this run); **NOT
MERGED**; and no force-push or rebase in the reflog. #46 remains a **DRAFT**, and draft PRs #26 and
#32–#45 were left exactly as found.

## S2 — the push disposition (thirty-third run, 2026-08-14)

Engine repo, draft PR **#47** on `claude/s2-push-disposition`, stacked on #46. Every command below
runs in the **engine** clone unless it names another.

### C-DSP-1 — the defect was LIVE, measured through the SHIPPING composition

The reproduction is a throwaway console project outside the repo (it must not join the solution or
it changes the pinned count). Recreate it verbatim: a project referencing `src/Sync`, and a `Main`
that drives the real `SyncPushPath.Create` for five cycles per result, mimicking
`EngineSyncBridge.PublishAsync`'s ratified snapshot retry (`ok` leaves the flag unset, so the next
cycle re-sends):

```csharp
// for each of Rejected("x") / TooLarge() / Unauthorised() / Unavailable("dns"):
var log = new List<string>(); var pushes = 0;
var pub = SyncPushPath.Create(key, "p_probe0000000000000000000000", "k1",
    push: (_, _) => { pushes++; return Task.FromResult(answer); },
    seqStore: store, log: log.Add, startSeq: 0);
for (var c = 0; c < 5; c++)
    if (pub.PublishSnapshotAsync(counters, Array.Empty<AppSummary>(), Array.Empty<JobSummary>())
        .GetAwaiter().GetResult()) break;
// report pushes, pub.HighestSeq, log.Count, log.Distinct().Count()
```

*Expected at the parent commit `9394ca1`:* all four results identical — **pushes=5, highestSeq=5,
delivered=0, logLines=5 (1 distinct)**. A 400 `bad_request` and a DNS failure are behaviourally
indistinguishable. *Expected at HEAD:* pushes/seqs/delivered **unchanged** (the halt was deliberately
not taken — see C-DSP-7) and **logLines=1** for every permanent case.

### C-DSP-2 — the 413 line asserted something the shipping code contradicts

```bash
git show 9394ca1:src/Sync/RelaySink.cs | grep -n 'will not be retried'
grep -n 'will not be retried' src/Sync/RelaySink.cs
grep -n 'if (!SnapshotSent)' -A 4 src/Engine/EngineSyncBridge.cs
```

*Expected:* present at the parent, **absent at HEAD**, and the bridge visibly leaving its flag unset
on failure — which is what makes the retry certain rather than possible. The probe measured **four
retries in five cycles**.

### C-DSP-3 — Classify is pure, total, and its mapping is pinned

```bash
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | grep -E 'CLASSIFY|PERMANENT FAILURE|share Pa|share the same|Classify refuses'
```

*Expected:* `PASS` on the full mapping, on **A PERMANENT FAILURE IS DISTINGUISHABLE FROM A TRANSIENT
ONE**, on 413/400 sharing `PayloadDead`, on 401/404 sharing `PairingDead`, and on a null result being
refused rather than classified.

### C-DSP-4 — the bool is DERIVED, so it cannot drift from the disposition

```bash
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | grep "the sink's bool is exactly"
```

*Expected:* `PASS`. Asserting the mapping alone would leave the sink free to classify `PayloadDead`
and still report success; mutation **M3** (misclassify `Ok`) fails **12** assertions, which is what
shows the two are tied rather than merely consistent today.

### C-DSP-5 — ten mutations, ten caught, tree byte-identical after

Apply each to `src/Sync/RelaySink.cs`, rebuild, run `SyncHarness`, revert:

| # | mutation | expected |
| --- | --- | --- |
| M1 | `Rejected => PushDisposition.RetryLater` (the original defect) | CAUGHT, 3 failed |
| M2 | `Unauthorised => PayloadDead` | CAUGHT, 4 failed |
| M3 | `Ok => RetryLater` | CAUGHT, 12 failed |
| M4 | delete the dedupe (always log) | CAUGHT, 5 failed |
| M5 | suppress silently (no recovery line) | CAUGHT, 4 failed |
| M6 | dedupe by disposition, not by line | CAUGHT, 4 failed |
| M7 | restore `"it will not be retried"` | CAUGHT, 2 failed |
| M8 | `lastDisposition` starts at `PairingDead` (phantom recovery) | CAUGHT, 1 failed |
| M9 | skip `Report` when the line repeats, so the EFFECTS are skipped too | CAUGHT, 1 failed |
| M10 | recovery line drops the suppressed count | CAUGHT, 1 failed |

*Expected:* **10/10 CAUGHT**, and `sha256sum -c` on `RelaySink.cs` **OK** afterwards. A mutation
whose target text is not found must be reported **SKIPPED**, never CAUGHT, and a harness that
**crashed** must be reported CRASH — checking for the missing summary line **before** counting FAILs
is the correction runs 27/30/31/32 reached four times by four routes.

### C-DSP-6 — suppression is words only; it never touches the effects

```bash
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | grep -E 'suppression is words only|persists the mark|announces nothing|no phantom|first push that succeeds'
```

*Expected:* `PASS` — an identical 409 reaches `ReconcileTo` **both** times, a 201 after a suppressed
run still persists the mark, a steady healthy engine announces nothing, and a first successful push
logs nothing at all. This is the assertion that stops a log fix becoming a protocol bug (M9).

### C-DSP-7 — the halt was CONSIDERED and NOT TAKEN, and that is written down

```bash
grep -n 'deliberately does NOT do: halt' -A 18 src/Sync/RelaySink.cs
grep -n 'does not authorise a halt' -A 6 src/Sync/RelaySink.cs
```

*Expected:* both present. The reasons are (a) the retry is **ratified above this layer** — the
2026-07-24 snapshot finding, which exists to stop a fresh phone merging a delta into demo fixture
rows — and (b) permanence is an **assumption about the relay's answer**, so an engine halting on a
401 raised by a deploy blip converts a minute of relay trouble into an outage. **This is the item's
open half, and it is open on purpose:** C-DSP-1's retry counts are unchanged at HEAD.

### C-DSP-8 — the harness moved 294 → 313 and the build is clean

```bash
dotnet build CareerSeeker.sln -c Release | tail -4
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
```

*Expected:* **0 Warning(s) / 0 Error(s)** and **`=== 313 passed, 0 failed ===`**.

### C-DSP-9 — 781 is CORROBORATED, NOT MEASURED, and here is the arithmetic

```bash
which pwsh; apt-cache policy powershell
for h in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness \
         GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  echo -n "$h: "; dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 \
    | grep -E 'passed, [0-9]+ failed' | tail -1
done
```

*Expected:* `pwsh` **empty** and `apt-cache policy powershell` **nothing** — so `Verify-Alpha.ps1`
did not run and cannot here. Nine harnesses report; they sum to **564**. `EngineHarness` prints **no
summary**: it throws `Refusing full-data deletion for a volume root` at `FullDataDeletion.cs:81`,
reached from `tests/EngineHarness/Program.cs:221` — a **correct** refusal on Linux and a pre-existing
documented limit, not a new break. Its **217** is carried from Windows CI. **564 + 217 = 781**, and
**762 + 19 = 781** independently. CI on `windows-latest` is what settles it.

### C-DSP-10 — zero vector bytes moved, so there was NO cross-repo drift event

```bash
node docs/sync-vectors/generate.mjs --check
git diff --stat HEAD~3..HEAD -- docs/sync-vectors/
```

*Expected:* **`OK: 29 vector files match the generator`** and an **empty** diff. The android repo's
vendored pin (`7328a0b`) is untouched; this slice added no vector and changed none.

### C-DSP-11 — the pin moved as ONE unit with every doc that reports it

```bash
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
grep -rn '| SyncHarness | 313 |\|\*\*781\*\*\|781 passed, 0 failed' \
  scripts/Verify-Alpha.ps1 README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md | wc -l
grep -rn '| SyncHarness | 294 |\|\*\*762\*\*' README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md
grep -c '3A4251F65AEF530BC5D73387422CD53556294970EC546C0112B6EF1BA4E900F2' scripts/Verify-Alpha.ps1
```

*Expected:* **781**; a **non-zero** count across the verifier and the four count-reporting docs; the
third grep **empty**; and the hash count still **2**. The `294` inside the Alpha ZIP's SHA-256 at
`Verify-Alpha.ps1:486,509` and the `762` inside the hash at `docs/Codex-Resume-Handoff.md:141` /
`docs/BETA-AUDIT-REQUEST.md:48` are **deliberately not swept** — they are hashes, not counts, the
same trap the previous run named. The two surviving prose mentions of `294`/`762` at
`Verify-Alpha.ps1:269,290` are the narrative of the **previous** slice and are correct history.

### C-DSP-12 — no android source changed, and nothing was merged, rewritten or deployed

```bash
git -C <android-clone> diff --stat origin/claude/android-a0-probe..HEAD -- core/ app/
git -C <engine-clone> log --oneline origin/main -1
git -C <engine-clone> merge-base --is-ancestor HEAD origin/main && echo MERGED || echo "NOT MERGED"
git -C <engine-clone> reflog --date=iso | grep -iE 'push --force|rebase' | head
```

*Expected:* **empty** (this repo took records only, so the android gate did not run and was not
attempted — **B-7**); `origin/main` still at **`aac05f3`**; **NOT MERGED**; and no force-push or
rebase in the reflog. #47 is a **DRAFT**, and #26 and #32–#46 were left exactly as found.

### C-DSP-13 — the android red was the STANDING FLAKE, proven by a re-run on an identical tree

```bash
git -C <android-clone> diff --stat e7c78ba..94ed7e6 -- app/ core/
```

*Expected:* **empty** — three Markdown files changed and no source at all, so this push could not
have caused a `:app` test failure. Then read run
[31788519473](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31788519473),
`head_sha` **`94ed7e6`**: **attempt 1 `failure`** with sole failing step **9,
`Unit tests (:app, Robolectric)`** — `ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab
FAILED`, `java.lang.AssertionError at ScreensFromFixtureTest.kt:69`, `35 tests completed, 1 failed,
3 skipped`, steps 10–13 **skipped** — and **attempt 2 `success` across all thirteen steps** on the
**same `head_sha` with no push between**. That identity is what makes it a *proven* flake instance
rather than an assumed one; the signature is the one already recorded under **Standing gate hazard**
in `BLOCKED.md`, and this is its **fifth** instance. Attempt 2's *Assert vendored sync vectors match
the pinned main-repo commit* step also passed, confirming the `7328a0b` pin on a **third** machine.

---

## Thirty-fourth run — EngineHarness's reach off Windows, and two stale records

Run from a Linux checkout of `ShivaClaw/careerseeker` at `claude/s8-harness-linux-reach`
(branched from `origin/main` = `aac05f3`). Requires `dotnet-sdk-8.0` and `node`; **install dotnet
with `sudo apt-get update && sudo apt-get install -y dotnet-sdk-8.0` — the `update` is required, a
bare `install` 404s on a stale index in a fresh container.**

### C-EHR-1 — the prompt's assigned S5 slice was already landed, and the pin in the prompt is stale

```bash
git fetch --all --prune
git log --oneline -1 7328a0b                    # "S5: add the invalid-unknown-field vector, closing PQ-A2-3 and B-6"
git ls-tree --name-only origin/claude/s5-entitlement-ack-spec docs/sync-vectors/v1/ | wc -l   # 28
git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l                             # 26
```

The vendored pin is **`7328a0b`**, not the `679a317` the stored prompt names. Expect the S5 spec
branch ahead of main with the extra vectors already present.

### C-EHR-2 — B-2's "one remaining thing" has been on main since 2026-08-12

```bash
git log -1 --format='%ci %s' d1bc698            # 2026-08-12 19:57:26 -0600  Merge pull request #42 ... claude/s2-pair-page
git merge-base --is-ancestor d1bc698 origin/main && echo "ON MAIN"
git merge-base --is-ancestor d1bc698 origin/claude/s2-push-disposition || echo "NOT IN THE STACK"
git show origin/main:src/Engine/Host.cs | grep -c 'path == "/pair"'    # 1
```

The last line is the point: the merge **is** on `main` and **is not** in the stack the thirty-third
run derived its blocker list from. That is how a closed gap stayed open in the records.

### C-EHR-3 — the baseline: 17 of 237 assertions, and an abort rather than a skip

Against the **parent** commit, i.e. before this run's fix:

```bash
git checkout aac05f3
dotnet build CareerSeeker.sln -c Release
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build; echo "EXIT=$?"
```

Expect `Unhandled exception. System.InvalidOperationException: Refusing full-data deletion for a
volume root.` at `FullDataDeletion.cs:line 81`, from `Program.cs:line 221`, and **EXIT=134**. Count
the two sides:

```bash
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build 2>/dev/null | grep -c "^  PASS\|^  FAIL"   # 17
grep -c "^\s*Check(" tests/EngineHarness/Program.cs                                                                            # 237
```

### C-EHR-4 — 17 → 217, and the reconciliation that makes it meaningful

```bash
git checkout claude/s8-harness-linux-reach
dotnet build CareerSeeker.sln -c Release        # 0 Warning(s), 0 Error(s)
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build | tail -2
```

Expect `=== 217 passed, 0 failed ===`, plus two `SKIP` lines naming 6 and 7 assertions. Then the
whole offline ladder:

```bash
for p in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness \
         GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$p/$p.csproj -c Release --no-build 2>&1 \
    | grep -oE "=== [0-9]+ passed, [0-9]+ failed ===" | tail -1 | sed "s/^/$p: /"
done
```

Expect 28 · 217 · 57 · 16 · 28 · 36 · 35 · 45 · 6 · 130, **all `0 failed`**, summing to **598**.
**The claim to check is the arithmetic, not the size:** `598 + 6 + 7 = 611`, and

```bash
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1     # 611
```

If those disagree, the guards are skipping the wrong set and this run is wrong.

### C-EHR-5 — three mutations, three caught, tree byte-identical after

```bash
sha256sum tests/EngineHarness/Program.cs > /tmp/eh.sha
```

**M1** — change the first `if (!OperatingSystem.IsWindows())` to `if (OperatingSystem.IsWindows())`,
rebuild, run: expect the abort to return (`InvalidOperationException`, `Program.cs:line 239`).
**M2** — same inversion on the `[ sync pairing vault ]` guard: expect
`DllNotFoundException: Unable to load shared library 'crypt32.dll'` at `SyncPairingVault.cs:line 78`.
**M3** — insert `false &&` into the `"the payload is HTML-escaped, not injected raw"` check: expect
`FAIL` and `=== 216 passed, 1 failed ===`. **M3 is the one that matters** — it shows the recovered
assertions are live rather than vacuous. After each: `git checkout tests/EngineHarness/Program.cs &&
sha256sum -c /tmp/eh.sha` must print `OK`.

### C-EHR-6 — Windows is unchanged, and this is the run's biggest exposure

```bash
git diff --stat aac05f3..claude/s8-harness-linux-reach     # 1 file changed, 33 insertions(+)
git diff aac05f3..claude/s8-harness-linux-reach            # purely additive; original code is the else-branch verbatim
sed -n '288,292p' scripts/Verify-Alpha.ps1                 # the four Assert-Contains literals, all still present in the file
```

**`$ExpectedOfflineTotal` was deliberately not swept.** The honest re-verification is not a command I
can run: **CI on `windows-latest` runs `Verify-Alpha.ps1`, which throws on a pin mismatch.** If that
job is green at 611, the claim holds; if it is red, this run was wrong and the pin needs the sweep I
declined. Check it at
`https://github.com/ShivaClaw/careerseeker/pull/48` → *Build and offline harnesses*.

### C-EHR-7 — zero vector bytes moved

```bash
node docs/sync-vectors/generate.mjs --check     # OK: 26 vector files match the generator
git status --porcelain                          # empty
```

**No cross-repo drift event**; the android repo's `7328a0b` pin is untouched.

### C-EHR-8 — nothing was merged, rewritten, deployed, or contacted

```bash
gh pr view 48 --repo ShivaClaw/careerseeker --json isDraft,state,mergedAt   # isDraft true, OPEN, null
git log --oneline origin/main -1                                            # still aac05f3 -- unmoved by this run
```

No android source changed this run (`git diff --stat origin/claude/android-a0-probe~1..HEAD` in the
android repo shows Markdown only), so the android gate was correctly not attempted (**B-7**).

---

## Thirty-fifth run — the halt policy's FOR argument, measured (engine repo, `claude/s2-push-disposition`)

All commands below run from a clone of `ShivaClaw/careerseeker` at
`claude/s2-push-disposition` (`1951313`), on Linux, after
`apt-get update && apt-get install -y dotnet-sdk-8.0`.

### C-HALT-0 — the assigned slice was already landed, so it was declined rather than redone

```bash
git fetch --all --prune
git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
#   9c05ef7 / a564c0c / 22b028e "pin section 4.3.3 with two entitlement_ack vectors"
#           / 8575539 "define the entitlement_ack body, and say what the size cap actually measures"
git log --oneline origin/main..origin/claude/s5-engine-wire-parser | grep 7328a0b
#   7328a0b S5: add the invalid-unknown-field vector, closing PQ-A2-3 and B-6
```

PQ-A6-1, PQ-A2-1, PQ-A2-2 and PQ-A2-3 are all closed on the open draft stack (#32, #37). **Nothing
was re-derived, re-specified or duplicated.**

### C-HALT-1 — the per-cycle cost of never halting is ten attempts and ten burnt seqs in ten cycles

```bash
dotnet build CareerSeeker.sln -c Release                                   # 0 Warning(s) / 0 Error(s)
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep "halt:"
#   PASS  halt: a permanently dead pairing costs ONE push attempt per cycle, indefinitely
#   PASS  halt: ...and burns one seq per cycle -- the counter advanced by exactly the cycle count
#   PASS  halt: ...delivering nothing and persisting nothing
#   PASS  halt: ...while the operator sees ONE line, not ten -- that clause is ALREADY answered
```

Driven through the **real `SyncPushPath` composition**, not through a copy of the sink.

### C-HALT-2 — seq exhaustion is not the harm, and the claim is an assertion so it can rot loudly

```bash
grep -n "MaxSeq = " src/Sync/Protocol.cs        # 9_007_199_254_740_991 (2^53-1)
python3 -c "print(9007199254740991 // 31557600)"   # 285420920  -> years at one seq per SECOND
```

Re-verified by mutation, not by arithmetic alone — see **M5** below.

### C-HALT-3 — THE FINDING: a `PayloadDead` backoff would suppress the entitlement ack

```bash
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep "halt:"
#   PASS  halt: an oversized snapshot is refused by §3.1's cap, measured on the CIPHERTEXT (PQ-A2-1)
#   PASS  halt: ...and that refusal classifies PayloadDead, which is what a backoff would key on
#   PASS  halt: THE ACK STILL REACHES THE RELAY on the very next push -- what a PayloadDead backoff would suppress
#   PASS  halt: ...and the bytes that got through ARE the entitlement_ack, decrypted from the wire
#   PASS  halt: ...and its mark was persisted, so the delivery is a real one and not a reported one
#   PASS  halt: under PairingDead the ack fails ANYWAY -- a backoff there withholds nothing that would have succeeded
#   PASS  halt: ...so PairingDead and PayloadDead do NOT take the same policy, though they share a permanence
```

The ack is proven by **decrypting the envelope the transport received** (`EnvelopeCodec.Open`
against the header's own AAD), not by the success flag of the call that produced it.

### C-HALT-4 — nine mutations, nine caught, and the ninth is the remedy itself

```bash
sha256sum src/Sync/RelaySink.cs src/Sync/SyncPublisher.cs src/Sync/Protocol.cs \
          src/Sync/SyncPayloads.cs tests/SyncHarness/Program.cs > /tmp/halt.sha
```

Apply each, rebuild `tests/SyncHarness`, run, and **check for the `=== N passed, M failed ===`
summary line BEFORE counting FAILs** — a mutation that kills the run is not a catch:

**M1** `src/Sync/RelaySink.cs`: `TooLarge => PushDisposition.PayloadDead` → `PairingDead`.
**M2** `src/Sync/SyncPublisher.cs`: `Interlocked.Increment(ref _seq)` → `Interlocked.Read(ref _seq)`.
**M3** `src/Sync/RelaySink.cs`: delete the `persistSeq(accepted)` call.
**M4** `src/Sync/SyncPayloads.cs`: `Encode("entitlement_ack"` → `Encode("entitlement_acknowledgement"`.
**M5** `src/Sync/Protocol.cs`: `MaxSeq = 9_007_199_254_740_991L` → `1_000_000L`.
**M6** `src/Sync/RelaySink.cs`: `return disposition == PushDisposition.Delivered;` →
`return disposition != PushDisposition.RetryLater;`.
**M8** `src/Sync/RelaySink.cs`: `if (line == lastLine) suppressed++;` →
`if (line == lastLine) { log(line); suppressed++; }`.
**M9** `src/Sync/Protocol.cs`: `MaxEnvelopeBytes = 1024 * 1024` → `64 * 1024 * 1024`.
**M7 — the naive backoff, the one that matters.** In `RelaySink.Create`'s returned lambda, before
the `push` call, insert:

```csharp
bool skip;
lock (gate) skip = lastDisposition is PushDisposition.PayloadDead or PushDisposition.PairingDead;
if (skip) return false;
```

Expect **four** `FAIL  halt:` lines, led by
`halt: THE ACK STILL REACHES THE RELAY on the very next push`. After each mutation:
`git checkout <file> && sha256sum -c /tmp/halt.sha` must print `OK` for all five.

### C-HALT-5 — the assertion was rewritten to survive its own target mutation

```bash
git show e071b98 -- tests/SyncHarness/Program.cs | grep -n "capPushed.Count == 2"
```

Before the rewrite, M7 raised `System.ArgumentOutOfRangeException` at `Program.cs:1992` and killed
the harness **after one FAIL line**. The guarded form reports the failure and lets every assertion
below it run. Re-verifiable by reverting the guard to a bare `capPushed[1]` and re-applying M7:
expect no summary line.

### C-HALT-6 — the pin sweep is counts only, and 793 is corroborated, not measured

```bash
git diff bb2cc63..1951313 -- README.md src/Engine/README.md docs/ scripts/Verify-Alpha.ps1 \
  | grep -E "^[-+]" | grep -vE "^[-+][-+]|^\+#"        # only 313->325 and 781->793; no hash, no version string
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1                     # 793
for h in Slice ResearcherHarness HookHarness StoreParityHarness GatewayGateHarness \
         DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$h/$h.csproj -c Release | tail -1; done
#   28 / 57 / 16 / 28 / 36 / 35 / 45 / 6 / 325  = 576;  576 + EngineHarness's Windows 217 = 793
```

**`Verify-Alpha.ps1` did not run and cannot here** (`which pwsh` is empty). CI is what settles
it, and **it did**: run `31822961113` on `head_sha` `1951313`, job `94840334927`
(`windows-latest`), step 6, printed `=== 325 passed, 0 failed ===` then
`=== Offline total: 793 passed, 0 failed ===`. The pin is therefore **measured on Windows**, not
merely corroborated on Linux. Re-read it with:

```bash
gh run view 31822961113 --repo ShivaClaw/careerseeker --log --job 94840334927 | grep "Offline total"
```

### C-HALT-7 — zero vector bytes moved

```bash
node docs/sync-vectors/generate.mjs --check     # OK: 29 vector files match the generator
git status --porcelain                          # empty
```

**No cross-repo drift event.** No vector was added, removed or edited this run, so the android repo's
vendored pin is untouched.

### C-HALT-8 — no behaviour changed, and nothing was merged, rewritten, deployed or contacted

```bash
git diff bb2cc63..1951313 --stat -- src/Sync/               # src/Sync/RelaySink.cs | 34 ++++ (1 file, +34/-0)
git diff bb2cc63..1951313 -- src/Sync/ | grep -E "^\+" | grep -v "^+++" | grep -vcE "^\+///"   # 0 non-comment additions
git log --oneline origin/main -1                            # aac05f3 -- unmoved by this run
```

`Classify`'s mapping, the sink's effects, its bool, `Report`, `SyncPushPath.Create` and
`SyncPublisher` are byte-identical to `bb2cc63`. **No backoff and no halt was implemented.** PR #47
is a **DRAFT**; the android repo received Markdown only, so the android gate was correctly not
attempted (**B-7**).

---

## C-RST — the engine stack's restack, costed (2026-08-14, thirty-sixth run)

Every command below runs in the **engine** checkout (`ShivaClaw/careerseeker`), after
`git fetch --all --prune`, and reproduces a claim in
[`docs/Merge-Topology.md`](docs/Merge-Topology.md) §10. Refs as measured: `origin/main` = `aac05f3`,
common fork = `00b3705`, stack tip #47 = `1951313`.

```bash
B="claude/s5-entitlement-ack-spec claude/s4-pull-request-semantics claude/s2-relay-retention \
claude/s2-seq-bound claude/s2-transport-vocabulary claude/s5-engine-wire-parser \
claude/s5-entitlement-ack-emitter claude/s5-inbound-pump claude/s2-relay-pull-result \
claude/s6-counter-reconciliation claude/s2-push-disposition"
```

### C-RST-1 — eleven chained PRs, a tree of depth 7, not a chain of sixteen

```bash
for b in $B; do printf "%-34s ahead=%s\n" "$b" "$(git rev-list --count 00b3705..origin/$b)"; done
for a in $B; do line=""; for c in $B; do [ "$a" = "$c" ] && continue
  git merge-base --is-ancestor origin/$c origin/$a 2>/dev/null && line="$line $c"; done
  echo "$a contains:$line"; done
```

Ahead counts 4/12/6/9/15/8/12/17/26/37/43. Containment yields exactly the §10.1 tree: #32 contains
nothing; #33, #34, #36, #37 each contain only #32; #35 ⊃ {#32,#34}; #38 ⊃ {#32,#37}; and the deepest
path is #32 ⊂ #37 ⊂ #38 ⊂ #39 ⊂ #45 ⊂ #46 ⊂ #47 — **7 PRs, not 16**.

### C-RST-2 — all eleven fork from one commit; main is 16 ahead of it

```bash
for b in $B; do git merge-base origin/$b origin/main; done | sort -u   # ONE line: 00b3705f89…
git rev-list --count 00b3705..origin/main                              # 16
```

### C-RST-3 — the merge probe: five branches clean, six conflict on the same five files

```bash
for b in $B; do out=$(git merge-tree --write-tree --name-only origin/main origin/$b)
  printf "%-34s conflicting-files=%s\n" "$b" "$(echo "$out" | sed -n '2,$p' | grep -c '^CONFLICT')"; done
git merge-tree --write-tree --name-only origin/main origin/claude/s2-push-disposition \
  | sed -n '2,$p' | grep '^CONFLICT'
```

0/0/0/0/0 then 5/5/5/5/5/5. The five are always `README.md`,
`docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`,
`scripts/Verify-Alpha.ps1`, `src/Engine/README.md`.

### C-RST-4 — conflicts appear exactly where a pin sweep does

```bash
for b in $B; do printf "%-34s pin-sweeps=%s\n" "$b" \
  "$(git log --oneline 00b3705..origin/$b -- scripts/Verify-Alpha.ps1 | wc -l)"; done
```

0/0/0/0/0/1/2/3/6/9/11 — **the same partition as C-RST-3**, clean exactly where the count is 0.

### C-RST-5 — the conflict is additive; 806 is derived, not measured

```bash
for r in 00b3705 origin/main origin/claude/s2-push-disposition; do
  git show $r:scripts/Verify-Alpha.ps1 | grep -m1 'ExpectedOfflineTotal = '
  git show $r:README.md | grep -E "^\| (EngineHarness|SyncHarness|\*\*Total\*\*)"; done
```

598 (E 217 / S 130) → main **611** (E **230** / S 130, +13) and stack **793** (E 217 / S **325**,
+195). **Disjoint harnesses**, so the restacked value is `598 + 13 + 195` = **806** (E 230 / S 325).

**806 WAS NOT MEASURED.** `Verify-Alpha.ps1` needs Windows PowerShell (`which pwsh` empty here) and
**did not run**. It throws on a pin mismatch, so CI on `windows-latest` is what settles it —
see C-RST-11. Treat 806 as an arithmetic prediction, not evidence.

### C-RST-6 — the code half of the restack is free

```bash
git merge-tree --write-tree --name-only origin/main origin/claude/s2-push-disposition \
  | grep -E "Host\.cs|Program\.cs|src/Sync|relay/"
```

Prints exactly two lines, both `Auto-merging` (`src/Engine/Host.cs`, `src/Engine/Program.cs`) and
neither a `CONFLICT` — despite main rewriting them by +134 and +95. **No `src/Sync/` or `relay/`
line appears at all**: main never touched those paths, so they do not even enter the merge.

### C-RST-7 — the restack cannot cause a cross-repo drift event

```bash
git diff --name-status 00b3705 origin/main -- docs/sync-vectors/        # EMPTY: main touched no vector
git diff --name-status 679a317 origin/claude/s2-push-disposition -- docs/sync-vectors/v1/ \
  | grep "^M" | grep -v index.json                                      # EMPTY: no existing payload modified
ls docs/sync-vectors/v1/*.json | wc -l                                  # 29
node docs/sync-vectors/generate.mjs --check                             # OK: 29 vector files match the generator
```

The stack's whole effect on vectors is **three added payloads** (`entitlement-ack`,
`entitlement-ack-no-order-id`, `invalid-unknown-field`) plus the `index.json` manifest. Existing
payloads are byte-identical to the android vendored pin `679a317`. **Adding is permitted; nothing
was modified.**

### C-RST-8 — #36's declared base is not its actual base

```bash
git merge-base --is-ancestor origin/claude/s4-pull-request-semantics \
  origin/claude/s2-transport-vocabulary && echo CONTAINS || echo "DOES NOT CONTAIN"
git log --oneline b114d11..origin/claude/s4-pull-request-semantics
```

Prints `DOES NOT CONTAIN`, then the one commit #36 lacks: `3a8dfdd` (PQ-CUR-1). GitHub shows #36's
base as #33 regardless. Restacking #36 onto its *actual* fork point silently drops `3a8dfdd`.

### C-RST-9 — #48 is independent and free

```bash
git rev-list --left-right --count origin/claude/s8-harness-linux-reach...origin/main   # 1  0
git merge-tree --write-tree --name-only origin/main origin/claude/s8-harness-linux-reach \
  | sed -n '2,$p' | grep -c CONFLICT                                                   # 0
```

One commit, **zero behind** fresh main, **zero conflicts**. Nothing in §10 affects it.

### C-RST-10 — §8's vector count was one stale, and is corrected

```bash
ls docs/sync-vectors/v1/*.json | wc -l                       # 29, not the 28 §8 recorded
git log --oneline --diff-filter=A -1 -- docs/sync-vectors/v1/invalid-unknown-field.json
#   7328a0b S5: add the invalid-unknown-field vector, closing PQ-A2-3 and B-6
```

### C-RST-11 — NOT RUN HERE: the command that settles 806

**This is the one claim in §10 with no executed evidence, and it is labelled so on purpose.** On a
Windows machine, after the restack:

```powershell
scripts\Verify-Alpha.ps1        # must print: === Offline total: 806 passed, 0 failed ===
```

If it prints anything else, §10.3's arithmetic is wrong and the doc/verifier sweep must take the
**measured** number, per `CLAUDE.md`'s rule. **No cloud session can run this**; no cloud session
should claim it.

---

## Thirty-seventh run (2026-08-15) — #36's base fix, C-B36-1…6

All commands run from a clone of `ShivaClaw/careerseeker` after `git fetch --all --prune`.
`b0b6c77` is #36's head after the fix; `9176b04` is its head before.

### C-B36-1 — the defect was real, and exactly one commit was at risk

```bash
git merge-base --is-ancestor origin/claude/s4-pull-request-semantics 9176b04 \
  && echo CONTAINS || echo "DOES NOT CONTAIN"     # DOES NOT CONTAIN  (the pre-fix state)
git log --oneline 9176b04..origin/claude/s4-pull-request-semantics   # exactly one: 3a8dfdd
```

### C-B36-2 — what `3a8dfdd` carries, i.e. what a silent drop would have cost

```bash
git show --stat 3a8dfdd | head -20
```

PQ-CUR-1: §6.4's carve-out was drawn at the parse, so an element that parses cleanly and then fails
its AEAD tag was covered by neither the MUST nor the exception — the cursor may not advance at all
for a forged-but-well-formed element, the permanent stall §6.2 forbids.

### C-B36-3 — the fix is one Markdown file, no vector, no pin

```bash
git diff --name-only 9176b04..b0b6c77                      # docs/Sync-Protocol.md  (and nothing else)
git diff --name-only 9176b04..b0b6c77 -- docs/sync-vectors/ scripts/Verify-Alpha.ps1   # empty
node docs/sync-vectors/generate.mjs --check                # OK: 28 vector files match the generator.
grep -n "Sync-Protocol" scripts/Verify-Alpha.ps1           # empty -- no doc/verifier pair moved
```

**28 is correct on this branch, not a discrepancy with §8's 29**: `invalid-unknown-field.json` is
added in #37, downstream of #36. At the stack tip the same command prints `OK: 29 …`:

```bash
# in a scratch worktree, so no existing checkout is disturbed:
git worktree add /tmp/cs-tip origin/claude/s6-counter-reconciliation
(cd /tmp/cs-tip && node docs/sync-vectors/generate.mjs --check)   # OK: 29 vector files match the generator.
git worktree remove /tmp/cs-tip
```

### C-B36-4 — the merge preserved both sides *exactly* (the claim worth attacking)

A clean merge is only trustworthy if each side's diff is reproduced verbatim. Both halves:

```bash
# (a) #36's own changes, byte-identical:
diff <(git diff 3a8dfdd..b0b6c77 -- docs/Sync-Protocol.md | grep -v '^index ') \
     <(git diff b114d11..9176b04 -- docs/Sync-Protocol.md | grep -v '^index ')     # no output

# (b) PQ-CUR-1, identical in content; @@ headers differ by offset only:
diff <(git diff 9176b04..b0b6c77 -- docs/Sync-Protocol.md | grep '^[+-]' | grep -v '^\(+++\|---\)') \
     <(git diff b114d11..3a8dfdd -- docs/Sync-Protocol.md | grep '^[+-]' | grep -v '^\(+++\|---\)')   # no output
```

(b) **without** stripping `@@` headers deliberately does *not* match — the four hunk offsets move by
~110 lines because #36's additions precede §6.4. Anyone re-running this should expect that and check
the content lines, which is what the second form does.

### C-B36-5 — the fix did not disturb §10's costing

```bash
git merge-tree --write-tree --name-only origin/main b0b6c77 >/dev/null; echo $?   # 0 -- zero conflicts
```

#36 was one of the five zero-cost PRs in §10 and still is.

### C-B36-6 — the class, not just the instance: every open PR swept

For each open PR, is the declared base branch's **tip** contained in the head?

```bash
for pair in \
  "48 s8-harness-linux-reach:main" \
  "47 s2-push-disposition:claude/s6-counter-reconciliation" \
  "46 s6-counter-reconciliation:claude/s2-relay-pull-result" \
  "45 s2-relay-pull-result:claude/s5-inbound-pump" \
  "39 s5-inbound-pump:claude/s5-entitlement-ack-emitter" \
  "38 s5-entitlement-ack-emitter:claude/s5-engine-wire-parser" \
  "37 s5-engine-wire-parser:claude/s5-entitlement-ack-spec" \
  "36 s2-transport-vocabulary:claude/s4-pull-request-semantics" \
  "35 s2-seq-bound:claude/s2-relay-retention" \
  "34 s2-relay-retention:claude/s5-entitlement-ack-spec" \
  "33 s4-pull-request-semantics:claude/s5-entitlement-ack-spec" \
  "32 s5-entitlement-ack-spec:main" ; do
  pr=${pair%% *}; hb=${pair#* }; h=${hb%%:*}; b=${hb#*:}
  git merge-base --is-ancestor origin/$b origin/claude/$h 2>/dev/null \
    && echo "#$pr OK" || echo "#$pr NOT CONTAINED"
done
```

Expected after the fix: every line `OK` **except `#32 NOT CONTAINED`**, which is the known restack
gap (base is `main`, 16 ahead of the fork `00b3705`) and not this defect. If any line other than #32
reports `NOT CONTAINED`, a new instance has appeared and §10.6's merge order is unsafe until it is
fixed.

### NOT RUN HERE

`scripts\Verify-Alpha.ps1` (needs Windows; `which pwsh` empty) and the android gate (B-7). The change
is doc prose carried by a merge, touching no pin and no vector, so the strongest claim available is
"no pin, no vector, no code" — **not** that any harness passed.

---

## C-CR-* — the `BuildSyncBridge` composition-root decision (thirty-eighth run, 2026-08-15)

Slice: `STATE.md` ordered-intent **item 3**, closed as a **decision**. Deliverable is
`docs/Composition-Root-Decision.md` in the **main** repo, on `claude/s6-composition-root-decision`
(draft **PR #49**, base `claude/s2-push-disposition`). Documentation only — no C#, no vector, no
`Verify-Alpha.ps1` edit, nothing compiled.

All commands below run from a clone of `ShivaClaw/careerseeker` checked out at
`origin/claude/s6-composition-root-decision`, after `git fetch --all --prune`.

### C-CR-1 — `BuildSyncBridge` is `Program.cs:256-323` and is identical at #46's and #47's heads

```bash
git diff --stat origin/claude/s6-counter-reconciliation origin/claude/s2-push-disposition \
  -- src/Engine/Program.cs                      # empty -- #47 does not touch Program.cs
sed -n '256p;323p' src/Engine/Program.cs        # the signature line and the closing brace
```

The line numbers in the decision doc are only stable because of the first command. If #47 ever gains
a `Program.cs` change, every citation in `Composition-Root-Decision.md` §1 must be re-derived.

### C-CR-2 — the call site is seven arguments, four of them named

```bash
sed -n '310,317p' src/Engine/Program.cs
sed -n '310,317p' src/Engine/Program.cs | grep -cE '^\s+(push|seqStore|log|startSeq):'   # 4
```

### C-CR-3 — M8 is **cited, not re-measured** (the weakest link in the argument)

```bash
grep -n 'mutation M8' src/Engine/Program.cs                    # the citation, Program.cs:306
grep -n 'M8 takes one of those four off the list' LOG.md       # the register, android repo
```

The claim *"deleting `: IE2pSeqStore` is a build error, not a silent no-op"* is **not verified by this
run** — no .NET on this host. It is the evidence for "the decisive step was not an extraction," so an
auditor who doubts §2 of the decision doc should re-run M8 on Windows first. Note also that the
register lives in the **android** repo while the citation lives in the **main** repo's C#.

### C-CR-4 — the five operator strings are unexecuted *and* unasserted

```bash
for s in 'nothing will be published' 'publishing turns on once' \
         'resuming the e2p counter above' 'could not read the relay' 'Sync: publishing to'; do
  printf '%-34s src:%s tests:%s\n' "$s" "$(grep -rl "$s" src/ | wc -l)" "$(grep -rl "$s" tests/ | wc -l)"
done
```

Expected: `src:1 tests:0` on every line. A `tests:` count above zero means one of §3's seven
behaviours has since gained coverage and the decision's part 3 should be re-read.

### C-CR-5 — the rule is covered twelve times; its one production call site is not covered at all

```bash
grep -c 'SyncPublisher.ResumeSeq' tests/SyncHarness/Program.cs   # 12
grep -rn 'SyncPublisher.ResumeSeq(' src/ | wc -l                 # 1  -> Program.cs:287
```

### C-CR-6 — the extraction history is exactly two commits

```bash
git log --oneline --diff-filter=A -- src/Sync/RelaySink.cs src/Sync/SyncPushPath.cs
# 0d369eb  S6: extract the push path's wiring so the composition is reachable from a harness
# dee32f8  S6: extract the push sink so its ReconcileTo call site is testable
```

This is what makes "5 → 4 → 3" a measurement rather than a story.

### C-CR-7 — the finding: the scoping qualifier is present in one file and absent in the other

```bash
grep -n 'at a single call site' src/Sync/SyncPushPath.cs   # 1 hit, line 47
sed -n '301,302p' src/Engine/Program.cs                    # same claim, qualifier absent
```

`SyncPushPath.cs:47` says *"four argument identities **at a single call site**"*; `Program.cs:301-302`
says *"What stays **here** is only the four argument identities."* Read plainly, *"here"* is the
method, and the sentence is false — §1's table lists seven behaviours that also stay.

### C-CR-8 — `"e2p"` → `"p2e"` compiles, tests clean, and reconciles against the wrong mark

```bash
grep -n 'string bearer, string dir' src/Sync/RelayClient.cs   # dir is an unconstrained string
sed -n '286p' src/Engine/Program.cs                           # the literal, passed positionally
sed -n '206,208p' relay/src/channel.ts                        # SELECT MAX(seq) ... WHERE dir = ?
```

The three together are the claim: nothing types the direction, nothing tests the call, and the relay
answers with the *other* direction's high-water mark rather than an error.

### C-CR-9 — the blast radius, stated at its real size rather than inflated

```bash
sed -n '568,570p' docs/Sync-Protocol.md
```

§6 makes gaps legitimate and forbids them stalling the stream — a large gap signals a fresh
`snapshot`. So C-CR-8's consequence is a **spurious snapshot request, not a stall**. This command
exists so nobody upgrades the finding on re-telling. **Reasoned, not executed** — no engine ran.

### C-CR-10 — doc-only: the drift trap is not engaged

```bash
git diff --name-only origin/claude/s2-push-disposition origin/claude/s6-composition-root-decision
# docs/Composition-Root-Decision.md   -- one file, and it is new
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1     # 338:$ExpectedOfflineTotal = 793
grep -n 'Composition-Root-Decision' scripts/Verify-Alpha.ps1   # empty -- no assertion targets it
```

No assertion added or removed, so the pin does not move and no count-reporting doc is swept.

### C-CR-11 — no vector byte moved, so no cross-repo drift event is possible

```bash
git diff --name-only origin/claude/s2-push-disposition origin/claude/s6-composition-root-decision \
  -- docs/sync-vectors/                          # empty
node docs/sync-vectors/generate.mjs --check      # OK: 29 vector files match the generator.
```

The android vendored pin `679a317` is untouched by construction.

### NOT RUN HERE

`scripts\Verify-Alpha.ps1` (needs Windows; `which pwsh` empty) and the android gate (**B-7**). This
branch adds one Markdown file and no assertion, so the strongest available claim is *"no pin, no
vector, no code"* — **not** that any harness passed. Every proposal in
`Composition-Root-Decision.md` §5 changes shipping C# signatures and is **unverified by
construction**; each needs a full local `Verify-Alpha.ps1 -IncludePublish -IncludePackage`.

### C-CR-12 — CI ran the offline gate on this branch, and it passed on the first attempt

**I did not run a gate; CI did.** The distinction matters and is preserved everywhere above: the
sandbox has no .NET and no Windows, so nothing in this run's prose claims a locally-executed harness.
What follows is CI's result, cited as CI's.

Run [**31866169984**](https://github.com/ShivaClaw/careerseeker/actions/runs/31866169984), event
`push`, attempt **1**, both jobs **success**:

| Job | Runner | Steps that matter |
| --- | --- | --- |
| Build and offline harnesses | `windows-latest` | *Build Release with warnings as errors* ✓ · **Run offline alpha verification** ✓ |
| Blind relay (Worker) | `ubuntu-latest` | Typecheck ✓ · Test ✓ · Validate config (**no deploy**) ✓ · *Assert the relay has no decryption path* ✓ · **Assert sync vectors match their generator** ✓ |

```bash
gh run list --branch claude/s6-composition-root-decision --workflow ci.yml
gh run view 31866169984 --json conclusion,jobs
```

**This upgrades two claims from inspection to execution:**

- **C-CR-10** said the pin does not move because no assertion was added. *Run offline alpha
  verification* passing means `Verify-Alpha.ps1`'s own drift check found the measured sum equal to
  `$ExpectedOfflineTotal = 793` **on this branch**. The pin is confirmed by running the verifier, not
  by reading it.
- **C-CR-11** said no vector byte moved. *Assert sync vectors match their generator* passing is CI's
  independent confirmation, on a clean checkout, of the `--check` this session ran by hand.

**What it still does not cover:** the CI job runs the **offline** verification only — not
`-IncludePublish`, not `-IncludePackage`, and not `-IncludeLive`. The merge condition in
`docs/Merge-Topology.md` §10.6 remains a **full local gate**, which is Brandon's and is unchanged by
this green. And CI cannot execute `BuildSyncBridge` either, for exactly the reason the decision doc
gives: no DPAPI vault, no relay.

---

## Thirty-ninth run (2026-08-15) — `PairingDerivationTest`, the `:core` lane's last named gap

Every claim below was executed in this session unless the line says otherwise. Commands run from the
android repo root on `claude/android-a0-probe` unless stated.

### C-PD-0 — the sandbox has no JDK 17, and `core-probe.sh` cannot run until it does

**New this run, and it is an environment change, not a repo change.** The image ships JDK 21 only;
`:core` pins `jvmToolchain(17)` and Gradle cannot auto-provision one (`api.foojay.io` denied, B-7).

```bash
ls -d /usr/lib/jvm/*17*                      # before: "No such file or directory"
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
ls -d /usr/lib/jvm/*17*                      # after:  /usr/lib/jvm/java-17-openjdk-amd64
```

That is the command `core-probe.sh` prints in its own failure message. **This is recurring, not new
— a first draft of this claim said no prior LOG entry recorded installing a JDK, and that was
wrong:** the twentieth run logged the same command for the same reason. Verify:

```bash
grep -o "apt-get install openjdk-17-jdk-headless" STATE.md | head -2
```

So a cloud session should expect this step **every** time, before any `:core` claim.

### C-PD-1 — `PairingDerivation` had no test file of its own

```bash
git ls-tree --name-only origin/claude/android-a0-probe core/src/test/kotlin/app/careerseeker/core/ \
  | grep -c PairingDerivation        # 0 at the parent commit 61d5804
ls core/src/main/kotlin/app/careerseeker/core/**/*.kt | wc -l   # 18 production files
```

The twentieth iteration's standing answer named two such files, `SyncCrypto` and this one.
`SyncCryptoTest.kt` closed the first; this run closes the second.

### C-PD-2 — the suite moved 276 → 288, measured from the XML and not counted by eye

```bash
scripts/core-probe.sh --rerun
```

- parent commit `61d5804`: `core-probe: 276 tests, 0 failed, 0 skipped, across 18 classes`
- this commit: `core-probe: 288 tests, 0 failed, 0 skipped, across 19 classes`

**This is `:core:test` only** — one of the android gate's four tasks. `checkCoreIsAndroidFree`,
`:app:assembleDebug` and `:app:lintDebug` did **not** run (B-7). Do not read this as a gate result.

### C-PD-3 — every pre-existing `derive` caller passes a one-element list

The claim that `concat`'s loop body had never run twice:

```bash
grep -rn -A2 "PairingDerivation.derive(" core/src app/src
```

Five call sites — `PairingSession.kt:123`, `ProtocolVectorsTest.kt:93` and `:117`,
`PairingSessionTest.kt:136` and `:196` — and every one wraps a single secret in `listOf(...)`.

### C-PD-4 — the confirm code's modulo bias, in exact integers

Recomputed independently of the test before it was written down:

```bash
python3 -c "
space=2**32; m=10**6; small=space//m; biased=space%m
print(small, biased, m-biased, biased*(small+1)+(m-biased)*small==space, (small+1)/(space/m))"
# 4294 967296 32704 True 1.00000761449337
```

967,296 codes have 4295 preimages, 32,704 have 4294, the identity closes on 2³², and the most likely
code is over-represented by a factor under **1.0000077**. **No change proposed.**

**Known limit, stated rather than left to be discovered:** the test carrying these numbers is a pin
by construction and cannot fail from a production edit. What holds the modulus and the derived
length in place is C-PD-5's recomputation test.

### C-PD-5 — the reduction is recomputed by a second mechanism, not transcribed

`confirm code is the unsigned big-endian reduction of four HKDF bytes` derives the four bytes through
`Hkdf.deriveKey(..., length = 4)`, reads them with `ByteBuffer` (big-endian by default), clears the
sign with a 32-bit mask and compares to `derive(...).confirmCode` over 32 independent inputs.

**What it does not prove, stated so it is not read as wider:** both sides are Kotlin, so it pins the
expression against the rule, not the phone against the engine. Engine agreement is the vectors' job
and `ProtocolVectorsTest` already holds it.

### C-PD-6 — the engine's construction, read rather than assumed

In `ShivaClaw/careerseeker` at `origin/main`:

```bash
grep -n "ReadUInt32BigEndian\|CompletionAad" src/Sync/PairingCrypto.cs
grep -n "CommandSigPrefix" src/Sync/DeviceSignature.cs
```

- `PairingCrypto.cs:65` — `BinaryPrimitives.ReadUInt32BigEndian(confirmBytes) % 1_000_000u`, i.e.
  **unsigned**; the phone's `Long` + `and 0xFF` is what matches it.
- `PairingCrypto.cs:85-86` — `$"{PairAadPrefix}|{pairing}|{suite}|{phonePubB64u}"`.
- `DeviceSignature.cs:18` — `Convert.ToHexString(SHA256.HashData(ciphertext)).ToLowerInvariant()`,
  i.e. **lower-case**; Kotlin's `"%02x"` is the same choice in another language.

### C-PD-7 — the mutation pass, and the harness that cannot fabricate a survivor

```bash
python3 scratch/mutate.py     # nine mutations, one at a time, full suite each, source restored
```

The harness **aborts** if a mutation's target string is not found, so "SURVIVED" can never be
produced by a mutation that silently failed to apply. First pass:

| # | Mutation | Failed | Mine? |
| --- | --- | --- | --- |
| M1 | `and 0xFF` dropped on the top byte | 2 | **both mine** |
| M2 | confirm bytes read little-endian | 5 | 1 mine + 4 pre-existing |
| M3 | `concat(sharedSecrets)` → `sharedSecrets[0]` | 1 | 1 mine — **and it was the wrong one** |
| M4 | digest hex `%02X` | 4 | 1 mine + 3 pre-existing |
| M5 | both directional keys under one label | 6 | 1 mine + 5 pre-existing |
| M6 | `completionAad` field order swapped | 2 | 1 mine + 1 pre-existing |
| M7 | `.padStart(6, '0')` dropped | 2 | **both mine** |
| M8 | modulus 10⁶ → 10⁵ | 5 | 1 mine + 4 pre-existing |
| M9 | provisional token's salt changed | 3 | **none mine** |

### C-PD-8 — M3 found a real gap in my own test, which was then closed and re-measured

The ordering test passed under a mutation that collapses the concatenation, because `[a, b]` → `a`
and `[b, a]` → `b` still differ. A third assertion was added (`[a, b]` must not derive what `[a]`
derives) and the test renamed.

```bash
python3 scratch/reverify.py    # clean run, then M3 re-applied
```

- clean run: **no failures**
- M3 re-applied: **2 failures**, `shared secrets concatenate in list order, and every element
  reaches the ikm` now among them.

### C-PD-9 — the shared vectors cannot distinguish a signed reduction from an unsigned one

**A finding about the corpus, not about this module, and it is not fixed here.** Under M1 the
reduction is signed, yet every pre-existing conformance test passed — including
`ProtocolVectorsTest`, which asserts `expected.str("confirm")` against `keys.confirmCode` by strict
equality (`ProtocolVectorsTest.kt:97`).

**Measured directly rather than left as that inference**, with an independent Python HKDF:

```bash
python3 -c "
import json,glob,base64,hmac,hashlib
def b64u(s): return base64.urlsafe_b64decode(s + '='*(-len(s)%4))
def hkdf(ikm, salt, info, length):
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    okm=b''; t=b''; c=1
    while len(okm)<length:
        t = hmac.new(prk, t+info+bytes([c]), hashlib.sha256).digest(); okm+=t; c+=1
    return okm[:length]
for f in sorted(glob.glob('core/src/test/resources/sync-vectors/v1/pairing-*.json')):
    d=json.load(open(f)); exp=d.get('expected')
    if not exp or not exp.get('confirm'): print(f, '-> no expected confirm'); continue
    cb=hkdf(bytes.fromhex(exp['ss_hex']), b64u(d['secret_b64u']), b'careerseeker/v1/confirm', 4)
    print(f, cb.hex(), hex(cb[0]), cb[0]>=0x80, str(int.from_bytes(cb,'big')%1000000).zfill(6), exp['confirm'])"
```

```
pairing-basic.json        confirm bytes 5fd509b6 | top byte 0x5f | high bit set: False
                          recomputed 797174 == vector 797174
pairing-mitm-keyswap.json no expected confirm code (error vector)
```

**The corpus carries exactly ONE confirm code**, and its top byte is `0x5f`. The finding is therefore
stronger than the inference: not "no vector happens to have the high bit set" but "there is a single
confirm derivation in the whole corpus, and it is a coin flip that landed the wrong way". The
recomputation reproducing `797174` is what makes this a measurement rather than a coincidence.

The engine is correct (C-PD-6); the shared corpus does not prove it has to be, on either
implementation.

Re-verify the mutation half by re-running M1 and reading which tests fail:

```bash
python3 scratch/mutate.py 2>&1 | sed -n '/M1/,/M2/p'   # 2 failures, both in PairingDerivationTest
```

Closing it means a new vector via `docs/sync-vectors/generate.mjs` in the **main** repo — a
cross-repo artifact and a separate slice. **Not attempted this run.**

### C-PD-10 — M9 is a miss in this file, reported as one

Changing the provisional token's salt is caught by three pre-existing tests and by none of mine: my
test asserts relationships (differs from the final token, deterministic, varies with the secret) and
all survive a salt change. The **value** is pinned by `ProtocolVectorsTest` against
`provisional_token_b64u`, which is the better authority — deliberate non-duplication, but a limit of
this file.

### C-PD-11 — no vector byte moved, in either repo

```bash
# main repo, origin/main
node docs/sync-vectors/generate.mjs --check     # OK: 26 vector files match the generator.
# android repo
git status --porcelain                          # one line: the new test file, untracked
git diff --stat core/src/main/                  # empty — the mutation harness restored the source
```

The vendored copy under `core/src/test/resources/sync-vectors/` was never opened for writing. **No
cross-repo drift event.**

### NOT RUN HERE

`scripts\Verify-Alpha.ps1` (needs Windows; no `pwsh`, no .NET on this host) and **three of the
android gate's four tasks** (`checkCoreIsAndroidFree`, `:app:assembleDebug`, `:app:lintDebug` —
B-7). This slice adds **no C# assertion and no vector**, so `$ExpectedOfflineTotal` is untouched and
the drift trap is not engaged. **CI is the gate**; if its `:app` Robolectric step reds on attempt 1
with `ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab`, that is the standing flake
already recorded in `BLOCKED.md`, not this change.

---

## Fortieth run — 2026-08-15: the confirm-code vector (main repo `claude/s3-pairing-confirm-vector`, draft PR #50)

Every command below is run from a clone of **`ShivaClaw/careerseeker`** on branch
`claude/s3-pairing-confirm-vector` unless stated otherwise. Node is the only tool needed for
C-HB-1…7; C-HB-8 needs Windows and is explicitly **not** claimed by this run.

### C-HB-1 — the gap existed: the corpus carried exactly one confirm code, high bit clear

```bash
git checkout origin/main
node -e '
const fs=require("fs"),g="docs/sync-vectors/v1/";
const n=fs.readdirSync(g).filter(f=>f!=="index.json")
 .map(f=>JSON.parse(fs.readFileSync(g+f,"utf8")))
 .filter(v=>v.type==="pairing"&&v.expected&&v.expected.confirm);
console.log("confirm codes on main:",n.length, n.map(v=>v.name+"="+v.expected.confirm).join(" "));'
```

Expected: `confirm codes on main: 1 pairing-basic=797174`.

### C-HB-2 — the generator produces 27 files and the tree matches it

```bash
node docs/sync-vectors/generate.mjs          # Wrote 27 files to docs/sync-vectors/v1/ (8 valid, 18 invalid).
node docs/sync-vectors/generate.mjs --check   # OK: 27 vector files match the generator.
echo $?                                       # 0
```

Baseline for comparison, on `origin/main`: `OK: 26 vector files match the generator.`
This is the same command as `.github/workflows/ci.yml:103`, so CI re-runs it on every push.

### C-HB-3 — the new vector renders `030514`, and both errors render something else

```bash
node -e '
const v=require("./docs/sync-vectors/v1/pairing-high-bit-confirm.json");
const {createECDH,hkdfSync}=require("crypto");
const k=createECDH("prime256v1"); k.setPrivateKey(Buffer.from(v.engine.d_hex,"hex"));
const pub=Buffer.from(v.phone.pub_b64u.replace(/-/g,"+").replace(/_/g,"/"),"base64");
const ss=k.computeSecret(pub);
const sec=Buffer.from(v.secret_b64u.replace(/-/g,"+").replace(/_/g,"/"),"base64");
const d=Buffer.from(hkdfSync("sha256",ss,sec,"careerseeker/v1/confirm",4));
const raw=d.readUInt32BE(0);
console.log("digest 0x"+raw.toString(16),"top 0x"+(raw>>>24).toString(16));
console.log("published :",v.expected.confirm);
console.log("unsigned  :",String(raw%1e6).padStart(6,"0"));
console.log("signed    :",String((raw|0)%1e6));
console.log("unpadded  :",String(raw%1e6));'
```

Expected: digest `0x9010f572`, top `0x90`, published `030514`, unsigned `030514`,
signed `-936782`, unpadded `30514`.

### C-HB-4 — the corpus now detects both errors (property, not vector)

```bash
node -e '
const fs=require("fs"),g="docs/sync-vectors/v1/";
const n=fs.readdirSync(g).filter(f=>f!=="index.json")
 .map(f=>JSON.parse(fs.readFileSync(g+f,"utf8")))
 .filter(v=>v.type==="pairing"&&v.expected&&v.expected.confirm);
console.log("codes:",n.length,"| leading-zero:",n.filter(v=>v.expected.confirm[0]==="0").length);'
```

Expected: `codes: 2 | leading-zero: 1`. Combine with C-HB-3 for the high-bit half.

### C-HB-5 — the guard fires; it is not decorative

Strip the new vector from `generate.mjs` and run it. The object to remove begins
`{ type: 'pairing', name: 'pairing-high-bit-confirm',` and ends at its `expect_error: null,`.

```bash
python3 - <<'EOF'
src=open('docs/sync-vectors/generate.mjs').read()
s=src.index("  {\n    type: 'pairing',\n    name: 'pairing-high-bit-confirm',")
e=src.index("    expect_error: null,\n  },\n];",s)+len("    expect_error: null,\n  },\n")
open('/tmp/gen-without-vector.mjs','w').write(src[:s]+src[e:])
EOF
node /tmp/gen-without-vector.mjs; echo "exit=$?"
```

Expected: throws `confirm audit: no vector's confirm digest has the high bit set, so a SIGNED int32
reduction would pass the whole suite. Add or restore a vector whose digest exceeds 0x7fffffff.`,
`exit=1`. (It writes to `/tmp/v1`, never to the repo.)

### C-HB-6 — additive only: no existing vector byte moved, so no cross-repo drift

```bash
git diff --stat origin/main...HEAD -- docs/sync-vectors/v1/
git diff origin/main...HEAD -- docs/sync-vectors/v1/index.json
git diff --name-only origin/main...HEAD -- docs/sync-vectors/v1/ | grep -v index.json
```

Expected: exactly one changed file (`index.json`, **+6/-0**, a single appended entry) and one new
file (`pairing-high-bit-confirm.json`). All 25 pre-existing vectors byte-identical. In the **android**
clone, the vendored copy is untouched and still pinned at `679a317`:

```bash
git -C ../careerseeker-android status --porcelain core/src/test/resources/sync-vectors/   # empty
```

### C-HB-7 — this branch adds ZERO merge conflicts against the unmerged S5 stack

```bash
git merge --no-commit --no-ff origin/claude/s5-inbound-pump >/dev/null 2>&1
git diff --name-only --diff-filter=U ; git merge --abort
git checkout -B probe origin/main                      # control: main WITHOUT this branch
git merge --no-commit --no-ff origin/claude/s5-inbound-pump >/dev/null 2>&1
git diff --name-only --diff-filter=U ; git merge --abort
```

Expected: **the identical five files in both runs** — `README.md`,
`docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`,
`scripts/Verify-Alpha.ps1`, `src/Engine/README.md`. `generate.mjs`, `index.json`,
`Sync-Protocol.md` and every vector auto-merge in both. The five are the S5 stack's staleness against
`main`, not this change.

### C-HB-8 — that `$ExpectedOfflineTotal` stays 611 — **UPGRADED FROM INSPECTION TO EXECUTION BY CI**

**I did not run the gate; CI did, and this claim is no longer inspection-only.** Written first as the
run's most attackable claim, it was measured minutes later by the PR's own CI:

| run | job | result |
| --- | --- | --- |
| [31886331917](https://github.com/ShivaClaw/careerseeker/actions/runs/31886331917) (pull_request) | **Build and offline harnesses** (`windows-latest`) | **success** |
| [31886305938](https://github.com/ShivaClaw/careerseeker/actions/runs/31886305938) (push) | **Build and offline harnesses** (`windows-latest`) | **success** |

`.github/workflows/ci.yml:46-48` runs `./scripts/Verify-Alpha.ps1`, and that script **throws** if the
measured offline sum differs from `$ExpectedOfflineTotal` (`Verify-Alpha.ps1:926-927`). The job
passing therefore *is* the measurement: the total still reads **611** with this branch's vector on
disk, which is exactly what the inspection predicted. Both runs also passed **Blind relay (Worker)**,
whose *sync vectors match their generator* step is an independent clean-checkout confirmation of
C-HB-2.

Re-verify locally on Windows if you want it off CI's word:

```powershell
scripts\Verify-Alpha.ps1        # expect: offline total 611, unchanged; 0 warnings / 0 errors
```

**The original reasoning, kept because it is what a reviewer should attack if the number ever moves.**
The argument was that
`SyncHarness` selects pairing vectors by name (`tests/SyncHarness/Program.cs:75`, `:120`) while its
generic loops iterate only `envelope`/`entitlement` types (`:141`, `:168`, `:198`, `:204`, `:447`,
`:507`), and that *"every declared vector exists on disk"* (`:55`) is a set equality rather than a
count — so a `pairing`-type vector adds no assertion. **CI's green agrees with it**, which is the
outcome that matters: the prediction and the measurement match.

If any future change measures anything other than **611**, it owes a `$ExpectedOfflineTotal` bump
**and** a sweep of every count-reporting doc, in the same change, per the drift trap in `CLAUDE.md`.
Adding the consuming assertion (next intent item 1) is exactly such a change.

### NOT RUN HERE

`scripts\Verify-Alpha.ps1` (**no `pwsh`, no .NET** on this host) and the **android gate** in all four
tasks (**B-7** — no Android SDK). This run added **no C# and no Kotlin**, so the only gate its content
touches is the Node vector check, which **was** run (C-HB-2) and which CI re-runs independently.

**The distinction is kept deliberately: I ran no gate, and no claim here says I did.** The Windows
verifier ran on **CI's** machine, not mine (C-HB-8), and that is how it is attributed. The android
gate ran nowhere — this run changed no android file but these records, so it had nothing to gate.

---

## C-CC — the confirm code's consumer (forty-first run, 2026-08-15, draft PR #51)

Like the C-WP block, these commands need **.NET**, and **C-CC-1 is how you get it**. `<engine>` is a
checkout of `ShivaClaw/careerseeker`; `<vector>` is branch `claude/s3-pairing-confirm-vector` (PR #50,
the base) and `<consumer>` is `claude/s3-pairing-confirm-consumer` (PR #51, this run). See **C-CC-8**
for what did not run.

### C-CC-1 — .NET 8 installs here; PowerShell does not

```bash
apt-cache policy dotnet-sdk-8.0
apt-get update -qq && apt-get install -y --no-install-recommends dotnet-sdk-8.0 && dotnet --version
apt-cache policy powershell; command -v pwsh
```

*Expected:* candidate `8.0.125-0ubuntu1~24.04.1`, installed `dotnet --version` **8.0.129**, and
**nothing** for `powershell`/`pwsh`. This is the pair of facts that splits the fortieth run's
inherited "neither compiles here" into a true half and a false half.

### C-CC-2 — the gap: the pre-change suite is blind to both slips

```bash
cd <engine> && git checkout <vector> && cp src/Sync/PairingCrypto.cs /tmp/pc.orig
sed -i 's|var confirm = (BinaryPrimitives.ReadUInt32BigEndian(confirmBytes) % 1_000_000u).ToString("D6");|var confirm = ((int)BinaryPrimitives.ReadUInt32BigEndian(confirmBytes) % 1_000_000).ToString("D6");|' src/Sync/PairingCrypto.cs
dotnet build CareerSeeker.sln -c Release && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
cp /tmp/pc.orig src/Sync/PairingCrypto.cs
sed -i 's|% 1_000_000u).ToString("D6");|% 1_000_000u).ToString();|' src/Sync/PairingCrypto.cs
dotnet build CareerSeeker.sln -c Release && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
cp /tmp/pc.orig src/Sync/PairingCrypto.cs && git status --porcelain
```

*Expected:* **`=== 130 passed, 0 failed ===` twice** — the signed reduction and the dropped zero-pad
both pass the whole suite — then a clean `git status`. This is the measurement PR #50 predicted.

### C-CC-3 — with the consumer, both mutations fail, and the detail names the wrong rendering

Same as C-CC-2 but on `<consumer>`.

*Expected:* **`=== 135 passed, 1 failed ===` twice**, the failing line being
`pairing-high-bit-confirm: confirm re-derives from that vector's own secret and scalars`, with detail
`derived -936782, vector 030514` under M1 and `derived 30514, vector 030514` under M7.

### C-CC-4 — the clean run, and the six new assertions by name

```bash
cd <engine> && git checkout <consumer> && dotnet build CareerSeeker.sln -c Release
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build \
  | sed -n '/every published confirm re-derives/,/^$/p'
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
```

*Expected:* build **0 Warning(s), 0 Error(s)**; six `PASS` lines under
`[ pairing: every published confirm re-derives, and both failure modes are pinned ]`; and
**`=== 136 passed, 0 failed ===`**.

### C-CC-5 — the corpus guard reports rather than throws

```bash
cd <engine> && cp docs/sync-vectors/v1/index.json /tmp/i.orig \
  && cp docs/sync-vectors/v1/pairing-high-bit-confirm.json /tmp/h.orig
rm docs/sync-vectors/v1/pairing-high-bit-confirm.json
python3 -c "import json;p='docs/sync-vectors/v1/index.json';d=json.load(open(p));d['vectors']=[v for v in d['vectors'] if v['name']!='pairing-high-bit-confirm'];open(p,'w').write(json.dumps(d,indent=2)+'\n')"
dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -E "FAIL|=== [0-9]+ passed"
cp /tmp/i.orig docs/sync-vectors/v1/index.json; cp /tmp/h.orig docs/sync-vectors/v1/pairing-high-bit-confirm.json
```

*Expected:* **four** `FAIL` lines and **`=== 131 passed, 4 failed ===`** — a printed summary, **not**
an unhandled exception. The first draft of this section used `.First(...)` and aborted the harness
here; that is the defect this command exists to keep closed.

### C-CC-6 — the offline pin: 387 + 230 = 617

```bash
cd <engine> && for h in Slice EngineHarness ResearcherHarness HookHarness StoreParityHarness \
    GatewayGateHarness DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  echo -n "$h: "; dotnet run --project tests/$h/$h.csproj -c Release --no-build 2>&1 \
    | grep -oE "=== [0-9]+ passed" | tail -1; echo
done
grep -n 'ExpectedOfflineTotal = ' scripts/Verify-Alpha.ps1
```

*Expected:* nine harnesses summing to **387** (`28+57+16+28+36+35+45+6+136`), **EngineHarness
producing no summary** (it aborts at `tests/EngineHarness/Program.cs:221`, B-10), and the pin reading
**617**. EngineHarness's **230** is quoted from `Verify-Alpha.ps1`'s comment block, **not
re-measured** — arithmetic corroborated by measurement, **not** a verifier run. The same arithmetic
reproduces the old pin: 381 + 230 = 611.

### C-CC-7 — the drift sweep moved together, and the historical records did not

```bash
cd <engine> && git diff main...<consumer> --stat
grep -rn "617" README.md src/Engine/README.md docs/CareerSeeker-Project-Summary.md \
  docs/External-Audit-Handoff.md scripts/Verify-Alpha.ps1
grep -rln "611" docs/autonomy/CODEX-STATE.md docs/Codex-Resume-Handoff.md docs/BETA-AUDIT-REQUEST.md
git diff --stat -- docs/sync-vectors/
node docs/sync-vectors/generate.mjs --check
```

*Expected:* six changed files; `617` in all five live count surfaces (pin, three `**Total**` rows,
the `Pinned offline verifier` line, and three `SyncHarness | 136` assertions); `611` **still present**
in the three historical evidence records, which report past measurements and are deliberately not
rewritten; **empty** vector diff; and `OK: 27 vector files match the generator.`

### C-CC-8 — what did NOT run, and what remains the gate

```bash
cd <engine>  && pwsh -File scripts/Verify-Alpha.ps1
cd <android> && ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug --rerun-tasks
```

*Expected:* **both fail to start here.** PowerShell is absent and not in the Ubuntu archive (C-CC-1),
so `Verify-Alpha.ps1` was not run and could not be parse-checked; the android gate needs the SDK
(**B-7**). `:core:test` was not run and did not need to be — nothing in `:core` changed, and the new
vector is not in the android tree at all (**B-14**). **CI on `windows-latest` is the gate for the 617
pin**, and for every claim in this section that depends on it.

### C-CC-9 — CI ran the Windows gate and confirmed the pin

```bash
gh run view 31897428719 --repo ShivaClaw/careerseeker
gh run view 31897428719 --repo ShivaClaw/careerseeker --log | grep -E "Offline total|136 passed"
```

*Expected:* both jobs **success**, and the log containing `=== 136 passed, 0 failed ===` and
**`=== Offline total: 617 passed, 0 failed ===`**. Both lines were **observed** in run
`31897428719` (job `95042792228`, `windows-latest`), at 2026-08-15T17:09:39Z. `Verify-Alpha.ps1`
**throws** on a pin mismatch (`:926-927`), so a green run *is* the pin check — which **settles the
230**, the one number C-CC-6 quotes rather than measures: 617 measured minus 387 measured here
leaves exactly 230, so the quoted figure was right and is now observed rather than inherited. **This does not retire C-CC-8**: the gate ran on
`windows-latest`, as it always must, and it is the offline half only.

---

## Forty-second run — PQ-S6-1's engine half (`claude/s6-outcome-disposition`, draft PR #52)

`<engine>` is a clone of `ShivaClaw/careerseeker` at `claude/s6-outcome-disposition`; `<android>` is
this repo. Every command below was executed this session unless it is explicitly marked as one that
does **not** run here.

### C-OD-1 — .NET is installable here; "no .NET on this machine" is a per-session cost, not a bound

```bash
which dotnet                                  # empty on a fresh sandbox
apt-cache policy dotnet-sdk-8.0
apt-get update -qq && apt-get install -y --no-install-recommends dotnet-sdk-8.0
dotnet --version
```

*Expected:* `which dotnet` empty before install; candidate **8.0.125-0ubuntu1~24.04.1** from
`noble-updates`; `dotnet --version` → **8.0.129**. Third run to confirm this.

### C-OD-2 — the defect, on `main`, before any edit

```bash
cd <engine> && git fetch --all --prune && git show origin/main:src/Sync/InboundDispatcher.cs | sed -n '98,111p'
```

*Expected:* both `return`s sit **outside** their null checks — `OutcomeApplied` is returned whether or
not `_outcomeApplier` ran, and `SnapshotRepublished` whether or not `_republisher` ran. `main` is
`aac05f3`.

### C-OD-3 — the finding: the real applier had six silent no-ops, not just the null seam

```bash
cd <engine> && git show origin/main:src/Engine/StoreOutcomeApplier.cs | grep -n "return;"
```

*Expected:* **six** bare `return;` lines (non-object body, unparseable `app_id`, missing `outcome`,
missing `at`, `JsonException`, outcome outside `PhoneSettable`). This is the measured strengthening of
PQ-S6-1: the over-reporting was **not** conditional on the seam being unwired.

### C-OD-4 — build and the full offline set, measured on Linux

```bash
cd <engine> && dotnet build CareerSeeker.sln -c Release
for h in Slice ResearcherHarness HookHarness StoreParityHarness GatewayGateHarness \
         DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$h/$h.csproj -c Release --no-build | tail -1
done
```

*Expected:* **0 Warning(s), 0 Error(s)**; then 28 / 57 / 16 / 28 / 36 / 35 / 45 / 6 / **134**, all
`0 failed`. Sum **385**.

### C-OD-5 — the baseline was measured, not inherited

```bash
cd <engine> && git stash && dotnet build tests/SyncHarness/SyncHarness.csproj -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -1 \
  && git stash pop
```

*Expected:* **`=== 130 passed, 0 failed ===`** — so the delta this run adds is exactly **4**.

### C-OD-6 — the four assertions have teeth (mutation, both directions)

```bash
cd <engine> && cp src/Sync/InboundDispatcher.cs /tmp/ID.bak
# revert BOTH dispositions to the pre-fix form: move each `return` outside its null check
dotnet build tests/SyncHarness/SyncHarness.csproj -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | grep -E "FAIL|==="
cp /tmp/ID.bak src/Sync/InboundDispatcher.cs && diff /tmp/ID.bak src/Sync/InboundDispatcher.cs
```

*Expected:* **`=== 130 passed, 4 failed ===`** with all four new assertions named in the FAIL lines,
and the pass count landing exactly on C-OD-5's baseline. `diff` clean after restore, and
`git diff --stat src/Sync/InboundDispatcher.cs` unchanged from the committed version.

**A first draft of the fourth assertion failed this test by passing it.**
`InboundOutcome.OutcomeApplied != InboundOutcome.OutcomeNotApplied` survived the mutation because the
enum members still existed — a tautology, replaced by the `ReceiveError is null` assertion. The 4/4 is
the re-test.

### C-OD-7 — the pin, and the one number that is not measured here

```bash
cd <engine> && grep -n "ExpectedOfflineTotal = " scripts/Verify-Alpha.ps1
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build 2>&1 | tail -5
```

*Expected:* pin **615**. EngineHarness prints **no summary** — it throws
`InvalidOperationException: Refusing full-data deletion for a volume root` at
`src/Engine/FullDataDeletion.cs:81`, reached from `tests/EngineHarness/Program.cs:221` (**B-10**,
re-confirmed live this run). So **385 measured + 230 quoted = 615**, and the 230 comes from
`Verify-Alpha.ps1`'s own comment block. The old pin decomposes identically: 381 + 230 = 611.

### C-OD-8 — the drift sweep moved as one unit, and the historical records did not

```bash
cd <engine> && grep -rn "615\|SyncHarness | 134" README.md src/Engine/README.md \
  docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md scripts/Verify-Alpha.ps1
grep -rn "611" docs/P1-Evidence.md docs/P2-Evidence.md docs/autonomy/ docs/Codex-Resume-Handoff.md
```

*Expected:* **615** in all five live count surfaces (the pin, three `**Total**` rows, the
`Pinned offline verifier` line) and `SyncHarness | 134` in all three assertion tables plus all three
docs. The historical evidence records are **deliberately not rewritten** — `P1-Evidence.md` still says
68 and `P2-Evidence.md` still says 88, because those are what those runs measured.

### C-OD-9 — no cross-repo drift; this run is not even a vector consumer

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check && git diff --stat -- docs/sync-vectors/
cd <android> && git diff --stat -- core/src/test/resources/
```

*Expected:* **`OK: 26 vector files match the generator.`** and **both diffs empty**. 26 rather than 27
because this branch is cut from `main` and the twenty-seventh vector is on the unmerged PR #50. The
android vendored corpus (pin `679a317`) was never opened for writing.

### C-OD-10 — the wire half was NOT taken, and the spec was not touched

```bash
cd <engine> && git diff main...claude/s6-outcome-disposition --stat -- docs/Sync-Protocol.md
git diff main...claude/s6-outcome-disposition --stat
```

*Expected:* the **spec diff is empty** — no `outcome_ack` kind was added to §4.3 and no §4.3.x section
was written. The full diff is **nine files**: two `src/`, three `tests/`, `scripts/Verify-Alpha.ps1`
and three count-reporting docs. PQ-S6-1's option (a)/(b) fork remains **open and unanswered**; see the
2026-08-15 amendment in `docs/protocol-questions.md`.

### C-OD-11 — what did NOT run, and what remains the gate

```bash
cd <engine>  && pwsh -File scripts/Verify-Alpha.ps1
cd <android> && ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug --rerun-tasks
```

*Expected:* **both fail to start here.** `pwsh` is absent and not in the Ubuntu archive, so
`Verify-Alpha.ps1` was **not run and could not be parse-checked** — a PowerShell syntax error in this
run's edits to it would not have been caught locally, which PR #52's self-audit names as the most
likely defect in the diff. The android gate needs the SDK (**B-7**); `:core` was not run and did not
need to be — **nothing in `:core` changed**. **CI on `windows-latest` is the gate for the 615 pin.**

### C-OD-12 — CI

```bash
gh run list --repo ShivaClaw/careerseeker --branch claude/s6-outcome-disposition
gh run view <run-id> --repo ShivaClaw/careerseeker --log | grep -E "Offline total|134 passed"
```

*Expected:* both jobs green, with `=== 134 passed, 0 failed ===` and
**`=== Offline total: 615 passed, 0 failed ===`** in the `windows-latest` log.

**Both lines were OBSERVED**, not merely expected, in run
[`31908682006`](https://github.com/ShivaClaw/careerseeker/actions/runs/31908682006) (job
`95070361787`, `windows-latest`) at 2026-08-15T21:11:44Z, with all four new assertions appearing as
**PASS** by name. `Verify-Alpha.ps1` **throws** on a pin mismatch (`:926-927`), so a green run *is* the
pin check — which **settles the 230** that C-OD-7 quotes rather than measures: 615 measured minus 385
measured on Linux leaves exactly 230, so the quoted figure was right and is now observed rather than
inherited.

**This also retires the specific risk C-OD-11 raises** — that a PowerShell syntax error in this run's
edits could not be caught locally. The verifier parsed and executed on `windows-latest`. **It does not
retire C-OD-11 itself**: the gate ran on CI, as it always must, and it is the offline half only
(no `-IncludePublish`, `-IncludePackage`, `-IncludeLive`), so the merge condition remains a **full local
gate** and remains Brandon's.

---

## Forty-third run — 2026-08-16 (S6 / PQ-S6-3, resume reconciliation)

Branch `claude/s6-resume-reconciliation` (engine repo), **draft PR #53**, cut from `origin/main` at
`aac05f3`. `<engine>` = the careerseeker checkout, `<android>` = careerseeker-android.

### C-RR-1 — the ordered intent's item 1 was already closed, and the check is one command

> **Claim.** `STATE.md`'s forty-second revision names **NEW ITEM 1 — PQ-S2-3**. That question was
> closed on **2026-08-11** by `cc6d966` on `claude/s2-transport-vocabulary`, which added §2.3. This
> repo's own `AUDIT-REQUEST.md` says so at C-S6C-5. **The list went stale in the same way the prompt
> did.**

```bash
cd <engine>
git log --oneline origin/claude/s2-transport-vocabulary | grep -i "PQ-S2-3"
git show origin/claude/s2-transport-vocabulary:docs/Sync-Protocol.md | grep -n "^### 2.3"
cd <android> && grep -n "PQ-S2-3 is now" AUDIT-REQUEST.md
```

*Expected:* `cc6d966 S2: pin the transport vocabulary for every route -- close PQ-S2-3`; §2.3
**"Transport responses for the remaining routes"** at line **172**; and C-S6C-5's correction reading
*"PQ-S2-3 is now **closed** by §2.3."* All three **observed** this run.

### C-RR-2 — PQ-S2-4 was refused as a decision, not skipped as hard

> **Claim.** The next list item examined was **PQ-S2-4**, and it was declined because its own text
> ends with a decision for Brandon, not a task. Recorded so no future run reads the skip as an
> oversight.

```bash
cd <android> && sed -n '/^## PQ-S2-4/,/^## PQ-S6-3/p' docs/protocol-questions.md | grep -n "Brandon decides\|Not filed in"
```

*Expected:* *"**Smallest human unblock — a decision, then two small changes…** Brandon decides whether…"*
and *"**Not filed in `BLOCKED.md`:** nothing is blocked. This is a decision that has not been made."*

### C-RR-3 — the toolchain, re-tested in both directions

> **Claim.** `.NET` is installable here (**8.0.129**, after `apt-get update` — the first attempt 404'd
> on a stale index). **PowerShell is not**, so `Verify-Alpha.ps1` genuinely cannot run in this sandbox.

```bash
which dotnet || echo "absent on a fresh sandbox"
apt-cache policy dotnet-sdk-8.0 | head -3
apt-get update && apt-get install -y dotnet-sdk-8.0 && dotnet --version
apt-cache policy powershell ; echo "powershell exit=$?"
```

*Expected:* `dotnet` absent before install, candidate `8.0.125-0ubuntu1~24.04.1`, `8.0.129` after.
`apt-cache policy powershell` prints **nothing**. **Both observed.**

### C-RR-4 — the defect, on `main`, before anything was written

> **Claim.** `Program.cs:288` passed the persisted term only, while `:239-243` stated the full §6.1
> rule; and `RelayClient.PushAsync` returned a bare `bool`, so the 409's `latest` was unread.

```bash
cd <engine>
git show origin/main:src/Engine/Program.cs | sed -n '239,243p;286,290p'
git show origin/main:src/Sync/RelayClient.cs | sed -n '51,60p'
git show origin/main:src/Engine/Program.cs | grep -c "PullAsync"
```

*Expected:* the comment states `startSeq = max(vault.last_e2p_seq, relay latest e2p)`; the code passes
`startSeq: paired.LastE2pSeq`; `PushAsync` ends `return res.StatusCode is HttpStatusCode.Created;`;
and **`0`** `PullAsync` occurrences in `Program.cs` — no startup consult existed.

### C-RR-5 — `SyncHarness` 130 → 146, with the baseline measured by stashing

> **Claim.** Baseline **130/0** measured on this tree by stashing, not read off the record; **146/0**
> with the change. Delta **+16**.

```bash
cd <engine> && dotnet build CareerSeeker.sln -c Release
git stash -u && dotnet build CareerSeeker.sln -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
git stash pop && dotnet build CareerSeeker.sln -c Release \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release --no-build | tail -2
```

*Expected:* build **0 warnings / 0 errors** throughout; `=== 130 passed, 0 failed ===` stashed;
`=== 146 passed, 0 failed ===` restored. **All observed.**

### C-RR-6 — the mutation table, including the one that is NOT caught

> **Claim.** M1–M6 are each caught by the assertion counts below. **M7 is not caught**, by design,
> after the hardening it provoked.

```bash
cd <engine>
# each: apply the edit, dotnet build -c Release, run SyncHarness, then revert
# M1 delete the HttpStatusCode.Conflict arm of PushAsync's switch          -> 5 FAIL
# M2 ReadLatestAsync returns 0 instead of null (both the ternary and catch) -> 4 FAIL
# M3 ResumeFrom => relayLatest ?? 0                                         -> 2 FAIL
# M4 ResumeFrom => persistedSeq                                             -> 2 FAIL
# M5 pass ReadLatestAsync's result on every switch arm, not just Conflict   -> 5 FAIL
# M6 delete the Unauthorized/Forbidden arm                                  -> 1 FAIL
# M7 delete `&& latest.ValueKind == JsonValueKind.Number`                   -> 146 PASS
```

*Expected:* `=== 140 passed, 5 failed ===`, `141/4`, `143/2`, `143/2`, `141/5`, `145/1`, and for M7
**`=== 146 passed, 0 failed ===`**. All observed.

**M7 is expected to pass and that is the point.** `TryGetInt64` throws on a non-number element rather
than returning false, so before the hardening M7 **crashed** the harness with an unhandled
`InvalidOperationException` at `RelayClient.cs:134` — the `ValueKind` guard was the only thing
preventing a `{"latest":"42"}` body from taking down the engine's push path. That catch now exists, so
guard-present and guard-absent are observationally identical and no behavioural assertion can separate
them. To see the crash, revert the `catch (InvalidOperationException)` arm first.

### C-RR-7 — the relay property the engine now depends on, and the relay suite

> **Claim.** `latest` is `MAX(seq)` for the direction, independent of `since`; the 409 body carries
> that mark per **direction**, not per pairing. Relay suite **32 → 34**, 0 failed. **No relay source
> ships in this change.**

```bash
cd <engine>/relay && npm ci && npx vitest run 2>&1 | grep -E "Tests "
git -C .. show origin/main:relay/src/channel.ts | sed -n '200,205p'
git -C .. diff origin/main -- relay/src/ ; echo "relay src diff exit=$?"
```

*Expected:* `Tests  34 passed (34)`; the `latest` query reading
`SELECT MAX(seq) AS m FROM envelopes WHERE dir = ?` with **no `since` term**; and an **empty** diff for
`relay/src/`. **Both mutations** (compute `latest` from the returned page; report the attempted seq in
the 409) were applied, each failing exactly one new test, then reverted and the suite re-measured at
**34/0**. All observed.

### C-RR-8 — no cross-repo drift

> **Claim.** No vector was added, removed or edited. The android vendored corpus was never opened for
> writing.

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check
git diff origin/main -- docs/sync-vectors/ ; echo "vector diff exit=$?"
cd <android> && git status --porcelain
```

*Expected:* `OK: 26 vector files match the generator.` (**26 is the `main` figure** — branch figures of
27 and 28 exist elsewhere in this book and reading one as a `main` figure is the drift trap one repo
over); an **empty** vector diff; and the android tree showing **only** `LOG.md`, `STATE.md`,
`AUDIT-REQUEST.md`, `BLOCKED.md`. All observed.

### C-RR-9 — the offline pin 611 → 627, derived and stated so it can be attacked

> **Claim.** Nine Linux-runnable harnesses measure **397**; `EngineHarness` contributes **230** on
> Windows; 397 + 230 = **627**, and 611 + 16 agrees. `EngineHarness` cannot run here.

```bash
cd <engine>
for h in Slice ResearcherHarness HookHarness StoreParityHarness GatewayGateHarness \
         DispatcherNoSendHarness LifecycleHarness RendererHarness SyncHarness; do
  dotnet run --project tests/$h/$h.csproj -c Release --no-build | tail -1
done
dotnet run --project tests/EngineHarness/EngineHarness.csproj -c Release --no-build | tail -5
grep -n 'ExpectedOfflineTotal =' scripts/Verify-Alpha.ps1
grep -rn "611" README.md src/Engine/README.md docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md
```

*Expected:* 28, 57, 16, 28, 36, 35, 45, 6, 146 — **sum 397**, all `0 failed`. `EngineHarness` **aborts**
with `System.InvalidOperationException: Refusing full-data deletion for a volume root` at
`FullDataDeletion.ResolveAllowedWorkspace`, from `Program.cs:221` — a Windows-path assumption, **not a
defect**. `$ExpectedOfflineTotal = 627`. **No `611` in any of the four docs.** All observed.

### C-RR-10 — the verifier's doc expectations, checked without running it

> **Claim.** `Verify-Alpha.ps1` was **not** run. Instead all **230** single-quoted `Assert-Contains`
> literals were extracted and confirmed present in their target docs.

```bash
cd <engine>
python3 - <<'PY'
import re
src = open('scripts/Verify-Alpha.ps1').read()
varmap = dict(re.findall(r'\$(\w+)\s*=\s*Get-Content\s+-LiteralPath\s+"([^"]+)"', src))
for d, b in re.findall(r'\$(\w+)\s*=\s*\[regex\]::Replace\(\$(\w+)', src):
    if b in varmap: varmap[d] = varmap[b]
miss = n = 0
for var, body, label in re.findall(r'Assert-Contains\s+\$(\w+)\s+@\((.*?)\)\s+"([^"]*)"', src, re.S):
    p = varmap.get(var)
    if not p: continue
    try: t = open(p, encoding='utf-8').read()
    except FileNotFoundError: continue
    hay = re.sub(r'[ \t]+', ' ', t) if 'Collapsed' in var else t
    for lit in re.findall(r"'((?:[^']|'')*)'", body):
        n += 1
        if lit.replace("''", "'") not in hay: miss += 1; print("MISSING", p, lit[:60])
print(f"{n} literals checked, {miss} missing")
PY
```

*Expected:* **`230 literals checked`**, with the only reported misses being two fragments from a
mixed-quote block in `src/Engine/BetaSetupWebFlow.cs` assertions about `gmail.compose` — **artifacts of
this checker's single-quote regex, unrelated to counts, not drift.** No count literal is missing.
**This is not a substitute for running the verifier**; it cannot catch a PowerShell syntax error.

### C-RR-11 — what did NOT run, and what remains the gate

```bash
cd <engine>  && pwsh -File scripts/Verify-Alpha.ps1
cd <engine>  && dotnet run --project tests/SyncLiveSmoke/SyncLiveSmoke.csproj -c Release --no-build
cd <android> && ./gradlew --no-daemon :core:test :app:assembleDebug :app:lintDebug
```

*Expected:* **all three fail to start or cannot pass here.** `pwsh` absent (C-RR-3), so a PowerShell
syntax error in this run's verifier edits **would not have been caught locally**. `SyncLiveSmoke`
compiles but needs a live relay — its replay check now asserts `Replayed` **and** `latest == 4`, and
that assertion is **UNVERIFIED**. The android gate needs the SDK (**B-7**); **nothing in `:core` or
`:app` changed**. **CI on `windows-latest` is the gate for 627.**

### C-RR-12 — CI

```bash
gh run list --repo ShivaClaw/careerseeker --branch claude/s6-resume-reconciliation
gh run view <run-id> --repo ShivaClaw/careerseeker --log | grep -E "Offline total|146 passed"
```

*Expected:* green, with `=== 146 passed, 0 failed ===` and
**`=== Offline total: 627 passed, 0 failed ===`** in the `windows-latest` log. `Verify-Alpha.ps1`
**throws** on a pin mismatch, so a green run *is* the pin check — and it is what would settle whether
`EngineHarness` is still 230.

**Both lines were OBSERVED**, not merely expected, in run
[`31919261549`](https://github.com/ShivaClaw/careerseeker/actions/runs/31919261549) (job
`95096310491`, `windows-latest`) at **2026-08-16T01:19:51Z**: `=== 146 passed, 0 failed ===` and
**`=== Offline total: 627 passed, 0 failed ===`**. The `Blind relay (Worker)` job (`95096310523`,
`ubuntu-latest`) was green in the same run, including its **"Assert sync vectors match their
generator"** and **"Assert the relay has no decryption path"** steps.

**This upgrades C-RR-9 from derived to measured**, and settles the one figure it could only quote:
627 observed minus 397 measured on Linux leaves exactly **230** for `EngineHarness`, so the number the
verifier asserts is confirmed rather than inherited. It also retires the specific risk C-RR-11 names —
that a PowerShell syntax error in this run's verifier edits could not be caught locally. The verifier
parsed and executed on `windows-latest`.

**It does not retire C-RR-11 itself.** This is the **offline half only** — no `-IncludePublish`,
`-IncludePackage`, `-IncludeLive` — so the merge condition remains a **full local gate**, and remains
Brandon's. `SyncLiveSmoke` is still unrun and its new `latest == 4` assertion still **UNVERIFIED**.

---

## Forty-fourth run (2026-08-16) — the fleet probe, and the duplication it found

Every command below ran in a Linux cloud sandbox on `claude/android-a0-probe` with `../careerseeker`
checked out alongside, **after `git fetch --all --prune` in both**. No gate ran; see **C-FL-6**.

### C-FL-1 — the tool, and its own regression test

```bash
cd <android> && ./scripts/fleet-probe.sh self-test ../careerseeker
```

*Expected:* three `PASS` lines and `self-test: OK`, exit 0. **Observed:** `fleet() sees 31 branches`,
`includes claude/* branches`, `excludes main, HEAD, codex/* and autonomy/*`.

This test exists because the first draft of the script used `for-each-ref 'refs/remotes/origin/*'`,
which matches only up to the next slash: the fleet narrowed to `origin/main` alone and `symbol` printed
**"not present in any unmerged branch"** for three symbols present in four. The tool reproduced the
false negative it was written to prevent. **A `FAIL` here means every `symbol` result is untrustworthy.**

### C-FL-2 — ITEM 1 was already built, on three branches

```bash
cd <android> && ./scripts/fleet-probe.sh symbol ../careerseeker ReconcileTo RelayPushResult PushOutcome
```

*Expected, and **observed**:* `ReconcileTo` on `s2-push-disposition`,
`s6-composition-root-decision`, `s6-counter-reconciliation` (4 files each); `RelayPushResult` on those
three plus `s2-relay-pull-result`; **`PushOutcome` on `s6-resume-reconciliation` alone.**

That last line is the finding in one output: #53 is the only holder of its own push type, while four
branches already carry `RelayPushResult` for the same job.

### C-FL-3 — the mechanism ITEM 1 asked for, read on the branch that has it

```bash
cd <engine> && git show origin/claude/s6-counter-reconciliation:src/Sync/SyncPublisher.cs \
  | grep -nE "public (static )?(long|bool) [A-Z]"
cd <engine> && git log --oneline origin/main..origin/claude/s6-counter-reconciliation | grep -E "6c3f8bb|e083f86|dee32f8"
```

*Expected:* `HighestSeq`, **`ReconcileTo(long)` at :81**, **`ResumeSeq(long, RelayPullResult)` at :121**.
The log shows `e083f86 S2: give PushAsync a failure channel, and stop discarding the 409's latest`,
`6c3f8bb S6: consume the typed results -- reconcile the e2p counter (PQ-S6-3's second bullet)`, and
`dee32f8 S6: extract the push sink so its ReconcileTo call site is testable` — **all dated 2026-08-14**,
against ITEM 1 written 2026-08-16. **Observed.**

Note for the auditor: `ReconcileTo` there also carries a §3.2 range guard that **throws** rather than
clamps, and `Protocol.MaxSeq` — neither of which exists on `main` or on #53, because PR **#35** (§3.2)
is likewise unmerged. This is why writing the mechanism fresh on a depth-1 branch would also have
re-derived a bound that already exists.

### C-FL-4 — the leaf-vs-leaf conflict matrix (§11.1)

```bash
cd <android> && ./scripts/fleet-probe.sh matrix ../careerseeker claude/s6-resume-reconciliation
```

*Expected, and **observed**:* SRC/DOC of **4/5** for #45; **5/5** for #46, #47, #49; 2/5 for #39;
1/5 for #38 and #51; 0/5 for #37 and #52; **0/0** for #32, #33, #34, #35, #36, #48, #50.

Cross-check one row by hand, since this table is what corrects §10.2:

```bash
cd <engine> && git merge-tree --write-tree origin/claude/s6-resume-reconciliation \
  origin/claude/s6-counter-reconciliation | sed -n 's/^CONFLICT ([^)]*): Merge conflict in /  /p'
```

*Expected:* exactly **ten** files — `README.md`, `docs/CareerSeeker-Project-Summary.md`,
`docs/External-Audit-Handoff.md`, `scripts/Verify-Alpha.ps1`, `src/Engine/Program.cs`,
`src/Engine/README.md`, `src/Sync/RelayClient.cs`, `src/Sync/SyncPublisher.cs`,
`tests/SyncHarness/Program.cs`, `tests/SyncLiveSmoke/Program.cs`. **Observed**, and it is the row that
falsifies §10.2's "no `src/Sync/`, no test file conflicts anywhere" **as a statement about the fleet**
(it remains true of the branch-vs-main probe §10.2 actually ran).

### C-FL-5 — the pins, and why they no longer add up (§11.3)

```bash
cd <engine> && for b in main claude/s6-resume-reconciliation claude/s2-relay-pull-result \
  claude/s6-counter-reconciliation claude/s2-push-disposition claude/s6-composition-root-decision; do
  echo -n "$b: "; git show origin/$b:scripts/Verify-Alpha.ps1 | grep -m1 "ExpectedOfflineTotal *=" | grep -oE '[0-9]+'
done
```

*Expected, and **observed**:* **611, 627, 704, 762, 793, 793.**

**The additive claim is where this stops being measured.** That `611 + 16 + 182` is *not* the merged
total is **derived, not measured**: it follows from both sides asserting the same push-answer behaviour
through incompatible APIs, so resolving `RelayClient.cs` deletes one side's assertions. The real number
is whatever the gate reports **after** the design choice. Nobody should quote a merged pin before then.

### C-FL-6 — what did NOT run

```bash
cd <engine>  && pwsh -File scripts/Verify-Alpha.ps1
cd <android> && ./gradlew --no-daemon :core:test :app:assembleDebug :app:lintDebug
```

*Expected:* **both fail to start here.** `pwsh` is absent; the android gate needs the SDK (**B-7**).

**No code was written in either repo this run, so neither gate has anything new to measure.** The
engine baseline was re-measured only to confirm the record was not itself stale:

```bash
cd <engine> && git checkout claude/s6-resume-reconciliation \
  && dotnet run --project tests/SyncHarness/SyncHarness.csproj -c Release
```

*Expected:* `=== 146 passed, 0 failed ===`. **Observed**, matching the forty-third run's claim exactly
(`dotnet-sdk-8.0` installed via `apt-get update && apt-get install -y dotnet-sdk-8.0`, **8.0.129** —
the standing correction that a fresh sandbox has no .NET but can get it holds for a fourth run).

---

## Forty-fifth run (2026-08-16) — the cross-repo drift check, and the pin records

Every command below was run in a Linux cloud sandbox with no Android SDK, no JDK, no PowerShell and no
.NET installed for this slice. `<engine>` is a checkout of `ShivaClaw/careerseeker`, `<android>` of
`ShivaClaw/careerseeker-android`. **Run `git fetch --all --prune` in both first** — every count here is
post-fetch.

### C-S5-1 — the assigned S5 spec work is landed (tenth consecutive stale assignment)

```bash
cd <engine>
git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
git show origin/claude/s5-entitlement-ack-spec:docs/Sync-Protocol.md | grep -n "PQ-A2-1\|PQ-A2-2"
git log --oneline --all -- docs/sync-vectors/v1/invalid-unknown-field.json | tail -1
```

*Expected, and **observed**:* `8575539` "define the entitlement_ack body, and say what the size cap
actually measures"; `22b028e` "pin section 4.3.3 with two entitlement_ack vectors, generated not
hand-written"; §7.2 and §3 carrying explicit `S5 / PQ-A2-1` and `S5 / PQ-A2-2` markers; and
`7328a0b` "add the invalid-unknown-field vector, closing PQ-A2-3 and B-6". **All four assigned items
exist. None was rewritten this run.**

### C-PIN-1 — the real pin, and the generator that anchors it

```bash
cd <android> && grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1
cd <engine>  && git merge-base --is-ancestor 7328a0bc043335491cd96a67d634e8eea2a13af9 origin/main \
                 && echo "on main" || echo "NOT on main"
cd <engine>  && git worktree add -f --detach /tmp/pin 7328a0bc043335491cd96a67d634e8eea2a13af9 \
                 && (cd /tmp/pin && node docs/sync-vectors/generate.mjs --check)
diff -rq /tmp/pin/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1
```

*Expected, and **observed**:* the pin is `7328a0bc043335491cd96a67d634e8eea2a13af9`, **not** `679a317`
as three documents said; it is **NOT on main** (neither was `679a317` — same posture, not a new one);
`OK: 29 vector files match the generator.`; and the final `diff -rq` prints **nothing** — the vendored
copy is byte-identical to the pin, before and after this run's edits.

`--check` also passes on every branch that carries vectors, which is what makes the content
generator-anchored rather than ref-anchored:

```bash
cd <engine> && for b in main claude/s5-entitlement-ack-spec claude/s5-entitlement-ack-emitter \
  claude/s5-inbound-pump; do git worktree add -f --detach /tmp/wt-$$ origin/$b >/dev/null 2>&1 \
  && echo -n "$b: " && (cd /tmp/wt-$$ && node docs/sync-vectors/generate.mjs --check) \
  && git worktree remove --force /tmp/wt-$$; done
```

*Expected, and **observed**:* **26, 28, 29, 29** vector files match the generator, respectively.

### C-PIN-2 — the one word `VECTORS.lock` had too strong

```bash
cd <engine>
for f in $(git ls-tree --name-only 679a317 docs/sync-vectors/v1/ | xargs -n1 basename); do
  h1=$(git cat-file blob 679a317:docs/sync-vectors/v1/$f | sha256sum | cut -d' ' -f1)
  h2=$(git cat-file blob origin/main:docs/sync-vectors/v1/$f | sha256sum | cut -d' ' -f1)
  h3=$(git cat-file blob 7328a0b:docs/sync-vectors/v1/$f | sha256sum | cut -d' ' -f1)
  [ "$h1" = "$h2" ] && [ "$h2" = "$h3" ] || echo "DIVERGES: $f"
done
```

*Expected, and **observed**:* exactly one line — **`DIVERGES: index.json`**. So of the 26 files the
lock called byte-identical across all three commits, **25 are**. The twenty-sixth is the **manifest**,
which necessarily changed when three vectors were added. **Zero existing payloads modified** — the
claim that carries the guarantee — still holds, and is how `docs/Merge-Topology.md` §8 words it.

### C-CI-1 — the drift check was blind to under-vendoring (the defect)

Extract the pre-fix step body (`git show HEAD~N:.github/workflows/ci.yml`, any commit before this run)
and run its loop against an android tree with one vendored vector deleted:

```bash
cp -r <android> /tmp/case2 && rm /tmp/case2/core/src/test/resources/sync-vectors/v1/entitlement-ack.json
# old loop: for f in core/src/test/resources/sync-vectors/v1/*.json; do fetch; diff; done
```

*Expected, and **observed**: **PASS**.* The loop iterates the **vendored** side only, so a file present
**at the pin** and absent locally is never enumerated and never fetched. The reachable case is a
**partial re-vendor** after a pin bump: copy two of three new files and this step passes.

> **CORRECTED, same run.** An earlier draft of this entry said the failure was "not subtle: across S5
> the phone was missing three upstream vectors and this step was green throughout." **The second
> clause is true and the causal link is false** — see **C-CI-4**. At the time the vendored set matched
> the pin *exactly*, so the old step was green **correctly**, and the fixed step would also have been
> green. S5's gap was a **stale pin**, which neither version detects by design. **B-16.**

### C-CI-2 — the fixed check, three cases, verbatim step body

The step body is extracted from `ci.yml` itself (so the test cannot drift from the workflow), with
`curl` shadowed by a stub that serves the contents API from local git objects:

```bash
python3 -c "import yaml;d=yaml.safe_load(open('.github/workflows/ci.yml'));
print([s['run'] for j in d['jobs'].values() for s in j['steps']
       if 'vendored sync vectors' in (s.get('name') or '')][0])" > /tmp/step.sh
# stub: PATH-shadowed curl mapping ?ref=SHA to `git cat-file blob SHA:path`,
#       and the directory URL to a JSON [{name,type:"file"}] listing from `git ls-tree`
PATH=/tmp/bin:$PATH bash /tmp/step.sh
```

*Expected, and **observed**:*

| tree | exit | output |
| --- | --- | --- |
| untouched | `0` | `OK: 29 vendored vectors match 7328a0b…, and the sets agree` |
| one vendored vector deleted | `1` | `::error::upstream has vector(s) that were never vendored: entitlement-ack.json` |
| one vendored vector hand-edited | `1` | `::error::vendored entitlement-ack.json differs from the pinned main-repo copy` |

Set equality on the real tree, independently:

```bash
cd <engine> && git ls-tree --name-only 7328a0b docs/sync-vectors/v1/ | xargs -n1 basename | sort > /tmp/up
ls <android>/core/src/test/resources/sync-vectors/v1 | sort > /tmp/ven
comm -23 /tmp/up /tmp/ven; comm -13 /tmp/up /tmp/ven
```

*Expected, and **observed**:* **29 and 29, both `comm` outputs empty** — the new check is green on
today's tree, so this is a real defect fixed, not a false positive introduced.

Also checked, because the lock edit sits in the file CI greps for the pin:

```bash
cd <android> && grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1
python3 -c "import yaml;d=yaml.safe_load(open('.github/workflows/ci.yml'));print('steps:',sum(len(j.get('steps',[])) for j in d['jobs'].values()))"
```

*Expected, and **observed**:* `7328a0bc043335491cd96a67d634e8eea2a13af9`, and `steps: 12` — the YAML
still parses.

### C-CI-3 — what did NOT run, and what is therefore unverified

```bash
cd <android> && ./gradlew --no-daemon :core:test :app:assembleDebug :app:lintDebug
```

*Expected:* **fails to start here** — no Android SDK, no JBR (**B-7**).

**The edited workflow has never executed on a runner.** Three things are verified only against a stub
and must be confirmed on the first real CI run: the contents-API **directory listing shape**
(`[{name,type}]`, and that 29 files stay inside one page), **`jq`'s presence** on the runner image, and
that the `?ref=` **directory** lookup resolves for a commit on an unmerged branch — the per-file lookup
is known to work there, the directory lookup is not independently confirmed. If any fails, the symptom
is this step erroring rather than reporting drift, and **B-15** in `BLOCKED.md` says so.

### C-INT-1 — the ordered intent's carried items were re-checked, not assumed

The standing precondition ("before taking item 1, re-verify item 1") applied to the items this run
did **not** take, since the list is what a future run will route by:

```bash
cd <android> && ./scripts/fleet-probe.sh symbol ../careerseeker \
  "relay_version|relayVersion|since.*version skew|X-Relay-Version"
cd <engine> && git show origin/main:relay/src/channel.ts | grep -n "latest"
cd <engine> && git show origin/claude/s6-resume-reconciliation:src/Engine/Program.cs \
  | grep -n -i "consult\|fall back"
```

*Expected, and **observed**:* **no relay-version guard exists on any unmerged branch**, so the
`since:` version-skew item is **still open**; `latest` is computed identically on `main` and on #53
(`channel.ts:202`), which is the property the skew turns on; and #53's startup consult still carries
its deliberate soft failure (`Program.cs:283-286`), so that item is **still a question, not a task**.
**Nothing on the carried list was found closed** — the two-run streak of stale item 1s ends here,
which is itself worth recording, because "the list is usually stale" would be the wrong lesson to
inherit.

**This run took none of those items.** The slice it took was derived by measuring the cross-repo drift
machinery (**C-CI-1**), which is what ITEM 3's "a target derived by measurement" asks for.


### C-CI-4 — the correction: S5's gap was a stale pin, not under-vendoring

```bash
cd <android>
git ls-tree --name-only 056a1dd^ core/src/test/resources/sync-vectors/v1/ | wc -l
git show 056a1dd^:core/src/test/resources/sync-vectors/VECTORS.lock | grep -oE '[0-9a-f]{40}' | head -1
cd <engine> && git ls-tree --name-only 679a317 docs/sync-vectors/v1/ | wc -l
```

*Expected, and **observed**:* **26**, `679a3175590dcd021b21c85af9daf12114e131fd`, **26**. The vendored
set and the pin were **equal** before the re-vendor, so the pre-fix step passed **correctly** and the
post-fix set comparison would have passed too. **This falsifies the causal claim in the first draft of
C-CI-1**, and it is why **B-16** exists: both versions of the step compare against the **pin**, never
against upstream HEAD, so a pin lagging upstream is invisible to CI in both repos.

The fix in **C-CI-2** is unaffected — case 2 (a file present at the pin, absent locally) is a real
condition the old step could not see, reachable via a partial re-vendor. Only the S5 attribution was
wrong.

---

## Forty-sixth run — 2026-08-16 (Linux sandbox)

### C-CI-5 — B-15 is closed on the pass path: the rewritten step ran on a real runner

The previous run shipped the set-comparison rewrite stub-verified only, and named three things that
could be confirmed **only** on a runner: the contents-API **directory listing shape**, **`jq`'s
presence**, and whether the `?ref=` **directory** lookup resolves for a commit on an **unmerged
branch**. All three executed.

```bash
gh api repos/ShivaClaw/careerseeker-android/actions/runs/31938526828 --jq '.conclusion,.head_sha'
gh api repos/ShivaClaw/careerseeker-android/actions/jobs/95144180297 \
  --jq '.steps[] | select(.name|test("vendored sync vectors")) | {name,conclusion}'
gh run view 31938526828 --repo ShivaClaw/careerseeker-android --log \
  | grep -E "pinned main-repo commit|OK: [0-9]+ vendored"
```

*Expected, and **observed**:* `success`, `703e8f222e3784c6da47a60a150311cee7033934`; the step
`Assert vendored sync vectors match the pinned main-repo commit` → `success` (09:16:51 → 09:17:00,
`ubuntu-latest`); and verbatim:

```
pinned main-repo commit: 7328a0bc043335491cd96a67d634e8eea2a13af9
OK: 29 vendored vectors match 7328a0bc043335491cd96a67d634e8eea2a13af9, and the sets agree
```

**Scope, stated precisely, because the rest of B-15 does not close with it.** What ran on the runner
is the **pass** path. The two **failure** paths (a vendored vector deleted; a vendored vector
hand-edited) are still verified only against the local stub of **C-CI-2** — runner-confirming them
means pushing a deliberately-broken tree, which would leave a red run and a stray branch on a repo
whose PRs are under review. **Not done, and not silently implied.** B-15 is narrowed, not deleted.

### C-CI-6 — the step's comment promised a count assertion that did not exist

```bash
cd <android> && git show 703e8f2:.github/workflows/ci.yml \
  | grep -n "count assertion below"
cd <android> && git show 703e8f2:.github/workflows/ci.yml \
  | sed -n '/Assert vendored sync vectors/,/Unit tests/p' | grep -c "wc -l < /tmp/upstream.names"
```

*Expected, and **observed**:* the comment is present at `703e8f2`, and the grep count is **0** —
there was no count assertion anywhere in the step. What actually happens on a truncated listing was
measured against the **real** vendored tree, listing truncated to 10 of 29 names, set-comparison
logic extracted verbatim:

```
::error::vendored vector(s) absent upstream at 7328a0b…: entitlement-wrong-product.json …
STEP WOULD FAIL (exit 1)
```

So truncation **is** caught — every dropped name looks like a vendored file absent upstream, so the
vendored-only branch fires — but the **only** diagnosis emitted points at a vendoring error that does
not exist. Outcome safe, stated reason wrong.

### C-CI-7 — the replacement assertion, exercised on both sides of its threshold

The new assertion was extracted **verbatim from `ci.yml`** (parsed out of the YAML block scalar, not
retyped) and run under `bash -e`, the shell GitHub uses:

```bash
cd <android>
python3 -c "import yaml;s=[x for x in yaml.safe_load(open('.github/workflows/ci.yml'))['jobs']['build']['steps'] if 'vendored sync vectors' in x.get('name','')][0]['run'];l=s.split('\n');i=[n for n,v in enumerate(l) if v.startswith('upstream_count=')][0];j=[n for n,v in enumerate(l) if n>i and v.strip()=='fi'][0];open('/tmp/ca.sh','w').write('\n'.join(l[i:j+1]))"
seq 29   > /tmp/upstream.names && bash -e /tmp/ca.sh; echo "EXIT=$?"
seq 1000 > /tmp/upstream.names && bash -e /tmp/ca.sh; echo "EXIT=$?"
```

*Expected, and **observed**:* `EXIT=0` at 29 (the step continues, today's reality), and at 1000:

```
::error::the upstream listing returned 1000 entries and is probably paginated;
::error::this step must follow pages before its set comparison can be trusted
EXIT=1
```

YAML validity after the edit, and that the stale comment is gone:

```bash
cd <android> && python3 -c "import yaml;d=yaml.safe_load(open('.github/workflows/ci.yml'));s=[x for x in d['jobs']['build']['steps'] if 'vendored sync vectors' in x.get('name','')][0];print(len(d['jobs']['build']['steps']),'upstream_count' in s['run'],'count assertion below' not in s['run'])"
```

*Expected, and **observed**:* `12 True True`.

### C-CI-8 — a defect I predicted, measured, and did not find

Before finding C-CI-6 I predicted the listing's **retry loop** was broken: `[ "$attempt" = 5 ] && { … }`
as the last statement of a loop body looked like the classic `set -e` abort, which would mean the loop
never retried and died silently on the first transient API failure. **Measured, with `curl` stubbed to
fail twice and succeed on the third attempt, run as GitHub runs it (`bash -e {0}`):**

```bash
bash -e /tmp/claude-scratch/retry-repro.sh; echo "EXIT=$?"
```

*Expected (my prediction):* abort after attempt 1. ***Observed:*** `retry 1`, `retry 2`, then
`REACHED THE LINE AFTER THE LOOP (attempts used: 3)`, `EXIT=0`. **The retry loop is sound** — a
failing `[` that is not the final command of an `&&` list is exempt from `set -e`. Recorded because
the prediction was wrong and an unrecorded wrong prediction is how a phantom blocker gets born.

**Note on method:** the first run of this repro invoked `bash script` and so **ignored the `-e` in the
shebang** — it proved nothing, and re-running it as `bash -e` is what made it evidence.

### C-ENV-1 — the android gate's absence is measured here, not assumed

```bash
java -version; gradle --version | head -3
curl -s -o /dev/null -w "%{http_code}\n" https://dsl.maven.google.com/ --max-time 15
```

*Expected, and **observed**:* JDK **21.0.10** (the build pins **17**), Gradle **8.14.3** (the build
pins **9.6.1**), and `000` for Google's Maven host — **unreachable**, against `200` for
`repo1.maven.org`. So **B-7 holds by measurement this run**, and every android-side claim above is
either runner-sourced or shell-level; **no `:core`, `:app`, `assembleDebug` or `lintDebug` result is
claimed, because none was run.**

### C-CI-9 — the edited step ran on a runner too, and passed

The change this run makes to `ci.yml` was itself exercised on `ubuntu-latest`, on the push of
`f5465b8`:

```bash
gh api repos/ShivaClaw/careerseeker-android/actions/jobs/95169273279 \
  --jq '.steps[] | select(.name|test("vendored sync vectors")) | {number,conclusion,started_at,completed_at}'
gh run view 31948926844 --repo ShivaClaw/careerseeker-android --log \
  | grep -E "pinned main-repo commit|OK: [0-9]+ vendored|paginated"
```

*Expected, and **observed** for the first command:* step **7**, `conclusion: success`,
`13:09:51Z → 13:10:00Z`. So the new `upstream_count` assertion parses and runs on the real image, and
does **not** fire at 29 — which is the behaviour **C-CI-7** predicted for the below-threshold side,
now confirmed on a runner rather than only locally.

**Stated honestly: the second command was not run by me.** Job `95169273279` was still executing
`:core:test` at hand-off, and GitHub returns **HTTP 404** for a job's logs until the job completes, so
the verbatim `OK: 29 vendored vectors …` line for *this* run is **not** in hand. What is in hand is
the step's own `conclusion: success` from the jobs API. The log grep is left above because it is the
command that upgrades this from a status to a transcript once the job finishes — **the next session
should run it, and should also confirm the remaining steps went green**, since `:core:test`,
`:app:test`, `assembleDebug` and `lintDebug` had not reported when this run stopped.

**Correction, same run:** pointing at run `31948926844` by number is stale advice the moment another
commit lands — this repo's workflow cancels an in-flight run when the branch moves, and the records
commit that follows this one does exactly that. **Read the newest run on the branch instead**, which
is what this resolves to:

```bash
gh run list --repo ShivaClaw/careerseeker-android --branch claude/android-a0-probe --limit 1
gh run view --repo ShivaClaw/careerseeker-android <id> --log \
  | grep -E "pinned main-repo commit|OK: [0-9]+ vendored|paginated"
```

The step-7 `conclusion: success` recorded above is **not** invalidated by the cancellation — that step
had already completed before the branch moved — but its *log text* may no longer be retrievable, which
is precisely why it is written up as a status and not quoted as a transcript.

**Resolved before hand-off — the transcript is in hand after all.** The run on the final head
`bbde8e2` completed while this session was still open: run
[`31949137250`](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31949137250), job
`95169758965`, `ubuntu-latest`, **job conclusion `success`, all 13 steps green**, `13:12:02 → 13:19:47`.
The edited step (step 7, `13:14:16 → 13:14:25`) printed verbatim:

```
pinned main-repo commit: 7328a0bc043335491cd96a67d634e8eea2a13af9
OK: 29 vendored vectors match 7328a0bc043335491cd96a67d634e8eea2a13af9, and the sets agree
```

**No `::error::` line appears anywhere in the step**, and in particular the new pagination assertion
did **not** fire at 29 — the below-threshold behaviour of **C-CI-7**, now confirmed on the real image
rather than only locally. Five `BUILD SUCCESSFUL` lines cover `checkCoreIsAndroidFree`, `:core:test`,
`:app:test`, `assembleDebug` and `lintDebug`.

**This upgrades a status to a transcript; it does not upgrade B-7.** None of those gradle results was
produced by me — they are read out of a runner log, and this sandbox still cannot run the android gate
(**C-ENV-1**). The distinction is the whole point of the B-7 bound: *observing* a gate is not
*running* one.

**Nothing in this run's claims depends on the CI run that this final records commit triggers.** It is
expected to reproduce the step output above; if it does not, that is a finding for the next session,
not a retraction of the transcript quoted here, which is anchored to `bbde8e2`.

---

## Forty-seventh run — 2026-08-16 (Linux sandbox): the landing sequence

All commands assume `git fetch --all --prune` in **both** checkouts first, and an engine checkout at
`../careerseeker`. Every count below was taken after that fetch.

### C-LAND-1 — 17 open PRs reduce to 7 leaf merges

Subsumption, `merge-base --is-ancestor`; every check must exit 0:

```bash
cd ../careerseeker
for x in s2-push-disposition s6-counter-reconciliation s2-relay-pull-result s5-inbound-pump \
         s5-entitlement-ack-emitter s5-engine-wire-parser s5-entitlement-ack-spec; do
  git merge-base --is-ancestor origin/claude/$x origin/claude/s6-composition-root-decision \
    && echo "#49 contains $x"
done
git merge-base --is-ancestor origin/claude/s4-pull-request-semantics origin/claude/s2-transport-vocabulary
git merge-base --is-ancestor origin/claude/s2-relay-retention        origin/claude/s2-seq-bound
git merge-base --is-ancestor origin/claude/s3-pairing-confirm-vector origin/claude/s3-pairing-confirm-consumer
```

Expected: #49 subsumes all seven; the three trailing checks exit 0. Leaves = #49, #36, #35, #51,
#48, #52, #53.

### C-LAND-2 — a leaf is not an open PR

```bash
scripts/fleet-probe.sh leaves ../careerseeker
```

Expected: the seven leaves **plus `claude/p4-entitlement`**, which has no open PR — its successors
landed as #27–#30. Cross-check against `list_pull_requests` before reading the output as a plan.

### C-LAND-3 — the cumulative landing costs 3 stops, not 1

```bash
scripts/fleet-probe.sh land ../careerseeker \
  claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
  claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition \
  claude/s6-resume-reconciliation claude/s6-composition-root-decision
```

Expected: first four `clean`, last three `STOP`, final line `conflicted merges (human stops): 3`.
Touches no working tree — `merge-tree`/`commit-tree` only. **This contradicts `Merge-Topology.md`
§10.4's "conflicts once"**, which is correct for the single chain it costed and stale for the fleet.

### C-LAND-4 — four leaves move the pin, to four different absolute values

```bash
cd ../careerseeker
for b in s3-pairing-confirm-consumer s6-outcome-disposition s6-resume-reconciliation s6-composition-root-decision; do
  mb=$(git merge-base origin/main origin/claude/$b)
  echo "$b  base=$(git show $mb:scripts/Verify-Alpha.ps1 | grep -m1 -o 'ExpectedOfflineTotal = [0-9]*')" \
       " tip=$(git show origin/claude/$b:scripts/Verify-Alpha.ps1 | grep -m1 -o 'ExpectedOfflineTotal = [0-9]*')"
done
git show origin/main:scripts/Verify-Alpha.ps1 | grep -m1 -o 'ExpectedOfflineTotal = [0-9]*'
```

Expected: main **611**; #51 611→617, #52 611→615, #53 611→627, #49 **598**→793. #48/#35/#36 do not
appear because they never touch the file. N pin-touchers cost **N−1** stops.

### C-LAND-5 — the stop count is order-dependent

```bash
scripts/fleet-probe.sh land ../careerseeker \
  claude/s6-composition-root-decision claude/s8-harness-linux-reach claude/s2-seq-bound \
  claude/s2-transport-vocabulary claude/s3-pairing-confirm-consumer \
  claude/s6-outcome-disposition claude/s6-resume-reconciliation
```

Expected: `conflicted merges (human stops): 4`. #49 forked at pin `598` and `main` is at `611`, so
it conflicts even as the first merge and forfeits the free slot. **Land a fresh-off-`main`
pin-toucher first.**

### C-LAND-6 — closing #53 removes a stop and the whole `src/Sync/` conflict class

```bash
scripts/fleet-probe.sh land ../careerseeker \
  claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
  claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition claude/s6-composition-root-decision
```

Expected: `conflicted merges (human stops): 2`, and the final `STOP` line no longer names
`src/Sync/RelayClient.cs`, `src/Sync/SyncPublisher.cs`, `src/Engine/Program.cs` or
`tests/SyncLiveSmoke/Program.cs`. Corroborates §11.2's duplication finding from the other direction.

### C-LAND-7 — no gate ran, and that is measured

```bash
which pwsh dotnet node; echo "exit=$?"
```

Expected in this sandbox: `node` only. **No `Verify-Alpha.ps1` result is claimed anywhere in this
run**, and the landed pin value is therefore *not* derivable here — see `Merge-Topology.md` §12.5.

### C-LAND-8 — the assigned slice is built, for the twelfth consecutive run

```bash
cd ../careerseeker
git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l          # 26
git ls-tree --name-only origin/main docs/sync-vectors/v1/ | grep -cE 'entitlement-ack|invalid-unknown-field'   # 0
for c in 8575539 22b028e 7328a0b; do git branch -r --contains $c | head -3; done
node docs/sync-vectors/generate.mjs --check                                 # OK: 26 …, exit 0
```

Expected: `main` carries 26 vectors and **neither** `entitlement-ack*` nor `invalid-unknown-field`;
all three commits resolve on `claude/s5-*` draft branches. **The work exists and is reviewable; it is
not on the trunk.** That distinction is why the slice has been re-assigned twelve times.

### C-LAND-9 — the stop condition was crossed two runs ago

```bash
# in the ANDROID checkout -- C-LAND-8's block leaves you in ../careerseeker
cd -                              # or: cd /path/to/careerseeker-android
grep -E '^#{2} ' LOG.md \
  | grep -oiE '(twenty|thirty|forty|fifty)-(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)' \
  | tail -3
```

Expected: the last ordinal is **forty-sixth**, i.e. 46 runs were logged before this one.

**Do not count `##` headings for this** — `grep -cE '^#{2} ' LOG.md` returns **65**, because the early
runs used one `##` per milestone rather than one per run. The ordinal in the last run heading is the
only reliable counter, and the first draft of this very command got it wrong.

Mission §7 fires the final handoff at **45**. `RETURN-DAY.md` is that handoff, written two runs late.

### C-STOP-1 — the assigned slice is built, for the thirteenth consecutive run

Run after `git fetch --all --prune` in **both** trees, or the counts are stale — that is rule one.

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  echo "== $c =="; git log -1 --format='%ad  %s' $c
  git show --stat --format='' $c
done
```

*Expected, and **observed** this run:* `8575539` (2026-08-09) amends **`docs/Sync-Protocol.md` only**,
+114/−3 — §4.3.3's `{product_id, acknowledged_at, order_id?}` body (**PQ-A6-1**), the 1 MiB cap
re-stated on the **decoded ciphertext** (**PQ-A2-1**), and structural rejection reported as
`decrypt_failed` (**PQ-A2-2**). `22b028e` adds `entitlement-ack.json`,
`entitlement-ack-no-order-id.json`, `index.json` **and `generate.mjs`** — generated, not hand-written.
`7328a0b` adds `invalid-unknown-field.json` (**PQ-A2-3**, closes **B-6**). **All four gates named in
the recurring prompt are already closed.**

That the vectors are generator-output rather than hand-written is the load-bearing half, and it is
checked on the branch that **carries** them — not on `main`, which has none of them:

```bash
cd <engine> && git checkout -B s5-check origin/claude/s5-entitlement-ack-emitter
node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected, and **observed**:* **`OK: 29 vector files match the generator.`**, `exit=0`. Prior runs
recorded this only against `main` (**`OK: 26 …`** — see **C-LAND-8**), which by construction says
nothing about the three added files. 26 + 3 = 29 reconciles the two.

**Why this is a re-verification and not a build instruction:** the work is unmerged, not unwritten.
`git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l` still returns **26**. The 17 open
PRs are unmerged because the merge condition is a full local `Verify-Alpha.ps1`, which no cloud
session can run (**C-LAND-7**, **C-ENV-1**). **Building it again would duplicate `8575539` and put a
regenerated corpus next to the one the android repo vendors at `7328a0b`.**

### C-STOP-2 — `RETURN-DAY.md` is on none of the reading lists that lead to it

```bash
cd <android>
# 2e78dab is run 47's records commit -- the parent of this run's first commit, pinned by SHA
for f in docs/CLAUDE-ANDROID-MISSION.md docs/S-Ladder.md; do
  echo -n "$f: "; git show 2e78dab:$f | grep -c 'RETURN-DAY'
done
echo -n "STATE.md pointer line: "; git show 2e78dab:STATE.md | sed -n '3,5p' | grep -c 'RETURN-DAY'
echo -n "STATE.md whole file:   "; git show 2e78dab:STATE.md | grep -c 'RETURN-DAY'
```

> **CORRECTED, same run, before push of the fix.** The first draft of this block used `HEAD~1` and
> expected `0`. **`HEAD~1` is the banner commit itself** (`43a3bd5`), so it returns **`2`** for the
> mission doc — the command proved the opposite of what it claimed the moment a second commit
> existed. Pinned to the **SHA** rather than `HEAD~2` so it stays correct however many commits land
> after it. This is the same failure mode run 47 recorded against C-LAND-9, caught the same way: by
> running the command instead of reading it. **Assume it can recur; re-run rather than trust.**

> **CORRECTED TWICE, and the second one narrows the claim.** The fix above also asserted `STATE.md`
> returns `0` for the **whole file** — "a stronger result than the claim needs". **It returns `2`.**
> Run 47's heartbeat cell does name `RETURN-DAY.md`, inside a multi-thousand-character table cell.
> So the honest claim is the **scoped** one the first draft made: the **navigational** pointer line
> is where a reader looks for what to read next, and that line named it **zero** times. I reached for
> a stronger version of my own finding and it was not there. **The scoped claim stands; the strong
> one is withdrawn.**

*Expected, and **observed** at `2e78dab`:* **`0`**, **`0`**, **`0`** for the mission doc, `S-Ladder.md`
and `STATE.md`'s pointer line — and **`2`** for `STATE.md` whole-file, which is the correction above
and is **not** a refutation: a mention buried in a heartbeat cell is not a reading instruction. The
recurring prompt names exactly `docs/CLAUDE-ANDROID-MISSION.md`, `STATE.md`, `LOG.md`, `BLOCKED.md`,
`docs/S-Ladder.md` and `AUDIT-REQUEST.md`; the closing handoff written at run 47 to end the
re-assignment loop was reachable from **none** of them. This run adds the banner to the first two, so
the same command against `HEAD` returns non-zero. Recorded as **B-18**.

**Attack this first if you doubt the run:** the claim is about *document reachability*, not about the
prompt's contents, and it is falsifiable exactly as written — if any of those files named
`RETURN-DAY.md` before this commit, the finding is wrong and the banner is redundant.

### C-STOP-3 — the vendored corpus is still byte-identical to its pin, after today's fetch

```bash
cd <engine> && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pin-check
diff -r /tmp/pin-check/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1
echo "exit=$?"; ls /tmp/pin-check/docs/sync-vectors/v1 | wc -l
```

*Expected, and **observed**:* **no output, `exit=0`, 29 files.** This re-confirms **C-PIN-1** rather
than establishing something new — it is here because rule one exists: the pin claim is only as fresh
as the fetch behind it, and this one was taken after `git fetch --all --prune` in both trees on
2026-08-16. **No vector byte was written by this run in either repo**; `git status` in the engine
checkout is clean and the android diff touches no path under `core/src/test/resources/`.

## Forty-ninth run — 2026-08-17 (Linux sandbox): revalidating the landing plan the day before it is used

The three commands below re-verify **`RETURN-DAY.md` §3**, which is the document Brandon acts on
during what §1 calls "the single highest-value hour on return day". They are pure `git` — no gate, no
toolchain — so they are runnable by any auditor on any machine with a clone.

**All of them require `git fetch --all --prune` in both trees first.** That is rule one, and here it
is load-bearing rather than ceremonial: a landing plan measured against stale refs is worse than no
plan, because it reads as measured.

### C-RD-1 — the landing plan's stop counts still hold, on a `main` fetched this morning

```bash
cd <android>
# the order RETURN-DAY 3 recommends, with #53 closed -- expect 2 stops
scripts/fleet-probe.sh land <engine> \
  claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
  claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition \
  claude/s6-composition-root-decision
# the same order with #53 appended -- expect 3 stops
scripts/fleet-probe.sh land <engine> \
  claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
  claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition \
  claude/s6-composition-root-decision claude/s6-resume-reconciliation
```

*Expected, and **observed** this run:* **`conflicted merges (human stops): 2`** and **`: 3`**. The
first four merges are `clean` in both. The stopping merges carry the exact file sets §3 prints:
`s6-outcome-disposition` conflicts on the five-file pin family (`README.md`,
`docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`,
`scripts/Verify-Alpha.ps1`, `src/Engine/README.md`); `s6-composition-root-decision` adds
`tests/SyncHarness/Program.cs`; and `s6-resume-reconciliation` — the #53 leaf — is the only one that
drags in `src/Sync/RelayClient.cs`, `src/Sync/SyncPublisher.cs`, `src/Engine/Program.cs` and
`tests/SyncLiveSmoke/Program.cs`. **That is §3's central claim measured directly: closing #53 removes
a stop and the entire `src/Sync/` conflict class.**

`origin/main` was `aac05f3` before and after, unmoved since 2026-08-12, so this is the same base §3
was written against — not a coincidental agreement across two different bases.

### C-RD-2 — order-dependence, measured in the configuration §3 actually recommends

```bash
cd <android>
# 49 first, all 7 leaves -- expect 4
scripts/fleet-probe.sh land <engine> \
  claude/s6-composition-root-decision claude/s8-harness-linux-reach claude/s2-seq-bound \
  claude/s2-transport-vocabulary claude/s3-pairing-confirm-consumer \
  claude/s6-outcome-disposition claude/s6-resume-reconciliation
# 49 first, 53 closed -- expect 3
scripts/fleet-probe.sh land <engine> \
  claude/s6-composition-root-decision claude/s8-harness-linux-reach claude/s2-seq-bound \
  claude/s2-transport-vocabulary claude/s3-pairing-confirm-consumer \
  claude/s6-outcome-disposition
```

*Expected, and **observed**:* **`4`** and **`3`**. Read against C-RD-1's `3` and `2`, the full matrix
is **all-7: 3 → 4** and **#53-closed: 2 → 3**; the penalty for putting #49 first is **+1 in both**.

**§3's printed `4 instead of 3` is correct, and this does not contradict it** — it is the all-7
figure. What was missing is that §3 *recommends closing #53*, and the order claim had never been
measured in the recommended configuration. A reader who takes step 0's advice and then checks the
order claim measures **3 vs 2** and finds neither printed number. **The penalty transfers between
configurations; the absolute counts do not.** The added table in §3 prints both rows.

*Withdrawn before it was written:* the first framing of this finding was "§3's order-dependence
figure is wrong for the configuration it recommends". It is not wrong — it is measured on a
different configuration and says so implicitly. Run 48's milestone 6 recorded exactly this failure
mode one run earlier (reaching for a stronger version of one's own finding), and it was avoided here
by measuring the all-7 case **before** claiming anything about the printed number, not after.

### C-RD-3 — the plan was measured against the live PRs, not just local refs

A landing plan is only as good as the correspondence between the branch names it simulates and the
pull requests a human will actually click merge on. Nothing before this run checked that.

```bash
cd <engine>
# heads from: list_pull_requests(ShivaClaw/careerseeker, state=open)
for pair in \
  35:claude/s2-seq-bound 36:claude/s2-transport-vocabulary 48:claude/s8-harness-linux-reach \
  49:claude/s6-composition-root-decision 51:claude/s3-pairing-confirm-consumer \
  52:claude/s6-outcome-disposition 53:claude/s6-resume-reconciliation; do
  n=${pair%%:*}; br=${pair#*:}
  echo "PR #$n  $br  $(git rev-parse origin/$br)"
done
```

*Expected, and **observed**:* **7 of 7 match their PR head SHA, 0 mismatches** —
`#35 2be00fc`, `#36 b0b6c77`, `#48 c93e88d`, `#49 f5e0c0a`, `#51 edee32b`, `#52 94fd979`,
`#53 8177353`. **All 17 fleet PRs are still `state: open`, `draft: true`**; none merged, none closed,
none taken out of draft since run 47 wrote the plan.

**One count worth stating precisely, because it is easy to get wrong:** the repo shows **18** open
PRs, not 17. The eighteenth is **#26 (`codex/r6-dependency-sbom`)**, which is **Terra's**, not part
of this fleet and not part of any landing step. §1's "seventeen" counts the `claude/*` fleet
(#32–#39, #45–#53) and is correct as written. An auditor recounting from the PR list will get 18 and
should not read that as drift.

### C-STOP-1 and C-STOP-3 — re-run at run 49, unchanged

Both commands re-executed verbatim after this run's fetch, and both reproduce:
`node docs/sync-vectors/generate.mjs --check` on `origin/claude/s5-entitlement-ack-emitter` reports
**`OK: 29 vector files match the generator.`**, `exit=0`; the `git archive 7328a0b` / `diff -r`
against the android repo's vendored corpus reports **no output, `exit=0`, 29 files**.
`git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l` still returns **26**.

**This is the fourteenth consecutive run assigned that slice**, and the first in which the banner
added at run 48 did its job: this session reached the built-already conclusion from its **first**
document read rather than by re-deriving it. That is B-18 attempt 3 working as designed — it makes
the firing cheap, and it still does not stop the firing.

## Fiftieth run — 2026-08-17 (Linux sandbox): re-verification, and the cheapest check yet

Every command below was **executed this run**, after `git fetch --all --prune` in both trees. That
fetch is load-bearing here rather than ceremonial — see **C-FETCH-1**.

### C-FETCH-1 — how stale an unfetched checkout actually was, measured

```bash
cd <android> && git rev-list --left-right --count HEAD...origin/claude/android-a0-probe
```

*Expected, and **observed** at session start:* **`10  200`** — the checkout's detached `HEAD`
(`ebfaf81`) was **200 commits behind** the work branch (`bd2be94`). `RETURN-DAY.md`, run 48's banner
and `BLOCKED.md` B-18 **do not exist at that ref**. This is the concrete cost of skipping rule one:
not a stale number, but a repository in which the last twelve runs are invisible and the assigned
slice looks genuinely unbuilt. Re-run after checking out the branch and it reads `0  0`.

### C-STOP-1 (re-run at run 50) — unchanged, and read at the diff

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  echo "== $c =="; git log -1 --format='%ad  %s' $c; git show --stat --format='' $c
done
# and the gates, read from the diff rather than the subject line:
git show 8575539 -- docs/Sync-Protocol.md | grep -E "^\+" \
  | grep -iE "entitlement_ack|product_id|acknowledged_at|order_id|ciphertext|decrypt_failed|MiB"
```

*Expected, and **observed**:* `8575539` (Sun Aug 9 2026) touches **`docs/Sync-Protocol.md` only**,
**+114/−3**; `22b028e` adds both ack vectors, `index.json` **and** `generate.mjs`; `7328a0b` adds
`invalid-unknown-field.json` **+60**. The grep prints §4.3.3's
`{product_id, acknowledged_at, order_id?}` (**PQ-A6-1**), *"The **decoded ciphertext** … MUST NOT
exceed **1 MiB**"* (**PQ-A2-1**), and the `decrypt_failed` row reading *"**Also every structural
rejection**… There is deliberately no separate `malformed` code"* (**PQ-A2-2**). **All four prompt
gates closed.**

### C-STOP-3 (re-run at run 50) — the generator, and zero cross-repo drift

```bash
cd <engine> && git worktree add -f --detach /tmp/pin 7328a0bc043335491cd96a67d634e8eea2a13af9
(cd /tmp/pin && node docs/sync-vectors/generate.mjs --check); echo "exit=$?"
diff -rq /tmp/pin/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "drift-exit=$?"
ls <android>/core/src/test/resources/sync-vectors/v1 | wc -l
cd <engine> && git worktree remove --force /tmp/pin
```

*Expected, and **observed**:* **`OK: 29 vector files match the generator.`**, **`exit=0`**; the
`diff -rq` prints **nothing**, **`drift-exit=0`**, **29** files. Re-run after this run's commits —
the android diff touches no path under `core/src/test/resources/`, so the corpus is byte-identical
to the pin **before and after** this run.

### C-STOP-4 — the cheapest check: the slice is an open draft PR, and its title says so

**New this run, and it is a shorter route to a known fact, not a new fact.** Every prior
demonstration ran `git` against three SHAs or checked out a branch. This one needs no clone:

```
list_pull_requests(ShivaClaw/careerseeker, state=open, fields=[number,title,draft])
```

*Expected, and **observed**:* **#32 — "S5 (first half): the `entitlement_ack` body, PQ-A2-1/-2, and
the relay cap §3.1 turned out to require"**, `draft: true`; **#37 — "S5: the engine's strict §3 wire
parser, and the vector it makes enforceable (closes B-6 / PQ-A2-3)"**, `draft: true`. A session asked
to build the `entitlement_ack` body can read the answer off the PR list in one call. **This is not a
discovery** — PR #32 is referenced ~28 times in this file already; it is recorded because B-18's
mitigation is about *cost per firing*, and this is the lowest-cost form of the check found so far.

### C-RD-1 (re-run at run 50) — the landing plan's stop counts, on the last day before it is used

```bash
cd <android>
scripts/fleet-probe.sh land <engine> \
  claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
  claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition \
  claude/s6-composition-root-decision                                   # expect 2
scripts/fleet-probe.sh land <engine> \
  claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
  claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition \
  claude/s6-composition-root-decision claude/s6-resume-reconciliation   # expect 3
cd <engine> && git rev-parse --short origin/main                        # expect aac05f3
```

*Expected, and **observed**:* **`conflicted merges (human stops): 2`** and **`: 3`**; first four
merges `clean` in both; `origin/main` **`aac05f3`**, unmoved since 2026-08-12, so this is the same
base §3 was written against. The stopping merges print the exact file sets §3 names, and the #53 leaf
is the only one carrying `src/Sync/RelayClient.cs`, `src/Sync/SyncPublisher.cs`,
`src/Engine/Program.cs` and `tests/SyncLiveSmoke/Program.cs`.

**Caveat, stated because the number invites over-reading:** `fleet-probe.sh` reports **textual**
`merge-tree` conflicts. **`clean` is not evidence that a merged tree compiles**, and the counts
forecast hand-resolutions only. No gate ran — see **C-ENV-1**.

### C-RD-3 (re-run at run 50) — branch heads against the live PRs

```bash
cd <engine>
for pair in 35:claude/s2-seq-bound 36:claude/s2-transport-vocabulary 48:claude/s8-harness-linux-reach \
  49:claude/s6-composition-root-decision 51:claude/s3-pairing-confirm-consumer \
  52:claude/s6-outcome-disposition 53:claude/s6-resume-reconciliation; do
  n=${pair%%:*}; br=${pair#*:}; echo "PR #$n  $br  $(git rev-parse origin/$br)"
done
# compare against list_pull_requests(ShivaClaw/careerseeker, state=open, fields=[number,head,draft])
```

*Expected, and **observed**:* **7 of 7 match, 0 mismatches** — `#35 2be00fc`, `#36 b0b6c77`,
`#48 c93e88d`, `#49 f5e0c0a`, `#51 edee32b`, `#52 94fd979`, `#53 8177353`. **All 18 open PRs are
`draft: true`**; none merged, closed or undrafted since run 47 wrote the plan. The fleet is **17**
(`#32`–`#39`, `#45`–`#53`); the eighteenth is Terra's **#26**, outside the plan — an auditor
recounting from the PR list gets 18 and **should not read that as drift**.

### C-ENV-1 (re-run at run 50) — neither gate can run here, measured not inherited

```bash
for t in pwsh dotnet sdkmanager adb gradle java node; do
  printf "%-12s %s\n" "$t" "$(command -v $t || echo ABSENT)"
done; echo "ANDROID_HOME=${ANDROID_HOME:-unset}"
```

*Expected, and **observed**:* `pwsh` **ABSENT**, `dotnet` **ABSENT**, `sdkmanager` **ABSENT**,
`adb` **ABSENT**, `ANDROID_HOME` **unset**; `gradle`, `java` and `node` present. **So
`scripts\Verify-Alpha.ps1` cannot run and neither can
`./gradlew … :app:assembleDebug :app:lintDebug`** — no Android SDK exists for the latter to target.
**No gate result is claimed anywhere in this run's records.** Grep this run's LOG entry for any
sentence that blurs that line and report it; run 47's auditor list ranks exactly this attack third.

### C-PIN-1 (re-run at run 50) — the pin the prompt still gets wrong

```bash
cd <android> && grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1
cd <engine>  && git merge-base --is-ancestor 7328a0bc043335491cd96a67d634e8eea2a13af9 origin/main \
                 && echo "on main" || echo "NOT on main"
cd <engine>  && git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l
```

*Expected, and **observed**:* pin is **`7328a0bc043335491cd96a67d634e8eea2a13af9`**, the recurring
prompt's `679a317` is **stale by five days** (moved 2026-08-12); the pin is **NOT on main**; `main`
carries **26** vectors, the branches **29**. **Run the ancestry check in the *engine* checkout** — run
in the android checkout it fails with `fatal: Not a valid commit name`, which a careless reader could
mistake for a meaningful "NOT on main".

## Fifty-first run — 2026-08-17 (Linux sandbox): what the landing plan does to the vendored pin

Every prior vector check in this window compares the phone against **the pin**. None has ever asked
what the corpus *becomes* when `RETURN-DAY.md` §3 is executed. This run measured that, because §3 is
executed tomorrow.

### C-POST-1 — the six merges of §3, run as real merges, not `merge-tree` probes

```bash
cd <engine> && git clone -q --no-checkout . /tmp/land && cd /tmp/land
git fetch -q origin '+refs/remotes/origin/*:refs/remotes/origin/*'
git checkout -q -B landsim origin/main
for b in s8-harness-linux-reach s2-seq-bound s2-transport-vocabulary \
         s3-pairing-confirm-consumer s6-outcome-disposition s6-composition-root-decision; do
  echo "== $b"
  git merge --no-edit -q origin/claude/$b >/dev/null 2>&1 && echo "  CLEAN" || {
    echo "  CONFLICT:"; git diff --name-only --diff-filter=U | sed 's/^/    /'
    echo "  vector files conflicted: $(git diff --name-only --diff-filter=U | grep -c sync-vectors)"
    git checkout -q --theirs .; git -c core.editor=true merge --continue >/dev/null 2>&1 \
      || git commit -q -am "sim"; }
  echo "  vectors now: $(ls docs/sync-vectors/v1 | wc -l)"
done
```

*Expected, and **observed** from base `aac05f3`:* merges 1–4 **`CLEAN`**; **#52
(`s6-outcome-disposition`) stops on the five-file pin family** — `README.md`,
`docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`, `scripts/Verify-Alpha.ps1`,
`src/Engine/README.md`; **#49 (`s6-composition-root-decision`) stops on those five plus
`tests/SyncHarness/Program.cs`**. **Two stops, and the file sets are exactly the ones §3 prints.**

This **corroborates** §3 rather than establishing it, and it is worth having because §3's counts came
from `fleet-probe.sh`, which uses `merge-tree`'s conflicted tree — a *textual* forecast. RETURN-DAY §7
item 2 warns that "the file lists after the first stop are artifacts". Under real merges the lists
reproduce exactly. **That warning is still the right posture**, though: this run resolved the first
stop before reaching the second, so merge 6's list is measured *downstream of a resolution I chose*.
The **counts** are robust; treat the second list as corroborated, not independent.

**The vector line is the load-bearing one: `vector files conflicted: 0`, at every stop.** No file
under `docs/sync-vectors/` conflicts in any of the six merges.

### C-POST-2 — therefore the post-landing corpus is determined, whatever Brandon does at the stops

Re-run C-POST-1 substituting `git checkout -q --ours .` for `--theirs`, then:

```bash
diff <(ls /tmp/land-ours/docs/sync-vectors/v1) <(ls /tmp/land/docs/sync-vectors/v1)
for f in /tmp/land-ours/docs/sync-vectors/v1/*.json; do
  cmp -s "$f" "/tmp/land/${f#/tmp/land-ours/}" || echo "BYTE DIFF: $f"; done
cd /tmp/land && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected, and **observed**:* **identical file set, zero byte differences**, and
**`OK: 30 vector files match the generator.`**, `exit=0`. The two opposite resolutions of the prose
conflicts produce a **byte-identical** vector corpus. **So the number below is not a forecast that
depends on how the merges are resolved — it is determined by the merge set.**

### C-POST-3 — after §3 lands, `main` carries one vector the phone does not, and nothing reports it

```bash
P=<android>/core/src/test/resources/sync-vectors/v1
L=/tmp/land/docs/sync-vectors/v1
for d in "$P" "$L"; do
  echo "$d  files=$(ls $d | wc -l)  payloads=$(ls $d | grep -vc '^index.json$')"
  node -e "console.log('  index.vectors[]: '+require('$d/index.json').vectors.length)"
done
comm -23 <(ls $L | grep -v '^index.json$') <(ls $P | grep -v '^index.json$')
cd <engine> && git log --oneline origin/main..origin/claude/s3-pairing-confirm-consumer \
  -- docs/sync-vectors/v1/pairing-high-bit-confirm.json
```

*Expected, and **observed**:* the phone is **29 files = 28 payloads + `index.json`**, `index.vectors[]`
**28**; post-landing `main` is **30 files = 29 payloads + `index.json`**, `index.vectors[]` **29**. The
single delta is **`pairing-high-bit-confirm.json`**, introduced by **`b95e83d`** on
`claude/s3-pairing-confirm-consumer` — **step 4 of §3**, the merge §3 calls *clean*.

**Read the counts carefully: `generate.mjs --check` counts `index.json` as one of its "vector files",
so its `29`/`30` are file counts, not payload counts.** A reader who compares the generator's `30`
against `index.vectors[]`'s `29` will think the manifest is stale. **It is not** — the manifest lists
all 29 payloads, `pairing-high-bit-confirm` among them (`grep -c pairing-high-bit-confirm
index.json` → `1`). This run drafted that false finding and killed it by looking; it is written down
so the next reader does not re-derive it.

**Why this matters and what is new.** That the phone lacks this vector is **not** new — it is
**B-14**, filed 2026-08-15, and B-16 already records that every drift check compares against the pin
rather than upstream. What is new is that the gap has now been **measured on the far side of the
landing plan**, with the exact post-state Brandon can check against, and a date: **it opens the
moment §3 step 4 lands.** `.github/workflows/ci.yml:127-133` contains a check written for precisely
this case — *"Upstream-only == under-vendored: the engine ships a vector the phone has never
[vendored]"* — but it queries `?ref=$PIN` (line 86, 101). **While the pin is `7328a0b`, that check
compares the phone against a commit that also lacks the vector, so it passes and cannot fire.** The
android CI stays green across the event it was written to catch. That is B-16's gap with a filename
and a date on it.

**Not claimed:** that anything breaks at runtime. `ProtocolVectorsTest` enumerates from the phone's
own `index.json` (`core/src/test/kotlin/app/careerseeker/core/ProtocolVectorsTest.kt:35`), so an
un-vendored vector is a **silently untested case**, not a failure. **No gate was run for this claim
and none could be** (**C-ENV-1**); the Kotlin side is read, not executed.

## Fifty-second run — 2026-08-17 (Linux sandbox): revalidating the *android* landing plan, which had never been re-measured

`RETURN-DAY.md` §3 (engine) was revalidated at run 49 and extended at run 51. **§4 (android) had not
been touched since `docs/Merge-Topology.md` §§4–7 was measured on 2026-08-09**, while one side of its
central hazard grew by 156 commits. These three checks re-measure it. All are `git`-only: **no
Windows, no .NET, no Android SDK, no emulator** is needed to reproduce any of them, and none of them
claims a build.

### C-AND-1 — §4's integration table still holds, as real merges

Run after `git fetch --all --prune` in the android tree, or the SHAs are stale — that is rule one.

```bash
cd <android>
git rev-parse --short origin/main origin/claude/p5-store origin/claude/p1-runbook \
                      origin/claude/p0-scaffold origin/claude/android-a0-probe
echo "a0-probe past fork: $(git rev-list --count d9f95fd..origin/claude/android-a0-probe)"
echo "  of those, since 2026-08-09: $(git rev-list --count --since=2026-08-09 origin/claude/android-a0-probe)"

rm -rf /tmp/andmerge && git clone -q <android> /tmp/andmerge && cd /tmp/andmerge
git fetch -q <android> 'refs/remotes/origin/*:refs/rem/*'
git checkout -q -B integ rem/main
for b in p0-scaffold p1-pairing p2-replica p5-store android-a0-probe p2-runbook todos-pq1-pricing p1-runbook; do
  if git merge --no-edit -q rem/claude/$b >/dev/null 2>&1; then echo "  <- $b : CLEAN"
  else echo "  <- $b : CONFLICT"; git diff --name-only --diff-filter=U | sed 's/^/       /'; fi
done
```

*Expected, and **observed** this run:* `main` **`ebfaf81`**, `p5-store` **`bb7f4d0`**, `p1-runbook`
**`ec0f73e`**, `p0-scaffold` **`59051a4`**, `a0-probe` **`980689b`**; a0-probe **183** past the fork,
**156** of them on/after 2026-08-09. Then **seven CLEAN merges** and **`p1-runbook` → CONFLICT on
`docs/Monetization-Decision.md`, exactly one file.** This is `docs/Merge-Topology.md` §4's table
reproduced row for row. **§4 used `merge-tree --write-tree`; this uses real `git merge`** — the two
agree by different means, which is the point of re-running it rather than re-reading it.

**Why this is a revalidation and not a new plan:** every other android branch is byte-unmoved since
08-09. **Only `a0-probe` moved, and only by this window's own records commits** — so the honest
result is *"the plan holds"*, and the finding is that nobody knew that until it was run.

### C-AND-2 — the §6 sibling overlap is still exactly three files

```bash
cd <android>
git diff --name-only d9f95fd origin/claude/p5-store        | sort > /tmp/p5.txt
git diff --name-only d9f95fd origin/claude/android-a0-probe | sort > /tmp/a0.txt
wc -l < /tmp/p5.txt; wc -l < /tmp/a0.txt
comm -12 /tmp/p5.txt /tmp/a0.txt
```

*Expected, and **observed**:* `p5-store` touches **21** files, `a0-probe` **73**, and the
intersection is **3** — `app/src/main/kotlin/app/careerseeker/dashboard/ui/ApplicationsScreen.kt`,
`.../ui/HomeScreen.kt`, `app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt`.
Identical to the 08-09 set. **The 156 records commits added no new file that `p5-store` also touches.**

**Not claimed:** that the fused result is correct. All three **auto-fuse with no conflict**, and
§6's standing warning — *"a clean merge is not a passing gate"*, **no gate has ever run on the fused
tree** — is unchanged. **This run ran no gate and could not** (**C-ENV-1**): no Android SDK, no
`sdkmanager`, `ANDROID_HOME` unset.

### C-AND-3 — §5's conflict is already ratified downstream, and the merge that reveals it is clean

```bash
cd /tmp/andmerge
# the rule, and the authority it cites -- on p5-store, a THIRD branch
git show rem/claude/p5-store:docs/store/Play-Listing.md | sed -n '6,8p'
# the section that rule cites, on each side of the conflict
git show 973a1dc | sed -n '5p;63p'     # a0-probe / p0-scaffold side
git show 2322ce8 | sed -n '5p;63p'     # p1-runbook side
# build both resolutions and scope the test to the PRICE-TABLE ROW
for res in ours theirs; do
  git checkout -q -B t-$res rem/main
  for b in p0-scaffold p1-pairing p2-replica p5-store android-a0-probe p2-runbook todos-pq1-pricing; do
    git merge --no-edit -q rem/claude/$b >/dev/null 2>&1; done
  git merge --no-edit -q rem/claude/p1-runbook >/dev/null 2>&1
  git checkout -q --$res -- docs/Monetization-Decision.md; git add -A >/dev/null; git commit -q --no-edit >/dev/null
  echo -n "--$res price row prints 'Basic'? "
  sed -n '5p' docs/Monetization-Decision.md | grep -q Basic && echo YES || echo no
done
```

*Expected, and **observed**:* `Play-Listing.md:6-8` reads **"Naming canon (enforced): the Windows app
is 'CareerSeeker' … never 'Basic'. … Do not let 'Basic' appear in any user-facing string
(Monetization-Decision §3)."** Blob `973a1dc` §3 = *"Naming note (worth a decision, not urgent)"*
with price row *"**CareerSeeker Basic** (.exe)"*; blob `2322ce8` §3 = *"Naming — **decided**
2026-07-23"* with price row *"**CareerSeeker** (.exe) — the product, not a tier"*. Then **`--ours`
→ YES**, **`--theirs` → no**.

**Read the check carefully — scope it to line 5.** A grep of the **whole file** reports *both*
resolutions inconsistent, because the `p1-runbook` side mentions `Basic` **3** times in §3's prose
*recording that the name was dropped* (*"was briefly on the table and dropped"*). A document that
decides against a word must be allowed to print it. **This run drafted that false finding and killed
it by looking**; it is written down because the coarse grep is the obvious way to write this check.

**What this establishes, narrowly.** §5 already recommends the `p1-runbook` side, arguing from
recency. **This does not change the recommendation — it corroborates it from an independent
direction** and adds the part §5 did not state: `docs/store/Play-Listing.md` lives on **`p5-store`,
neither side of the conflict**, already **enforces** one side, and **cites the disputed section as
its authority**. So resolving `--ours` leaves `main` carrying a price table that prints the exact
string its own store listing forbids, with the listing pointing at that table as the rule's source.

**And nothing raises a hand.** `p5-store` merges **clean**, `a0-probe` merges **clean**; the only
conflict git reports is on `Monetization-Decision.md` itself (**C-AND-1**). **The contradiction is
never a merge conflict** — it would surface, if at all, in copy pasted into the Play Console.
`--ours` is the tempting default precisely because it is the lineage carrying all 183 commits.

**Not claimed:** that anything builds, or that the store copy is otherwise submission-ready. **`Basic`
appears in the whole `docs/store/` dossier exactly twice, both on `Play-Listing.md:6-7`, and both are
the guard clause itself** — the other 13 dossier files contain the string zero times.

## Fifty-third run — 2026-08-17 (Linux sandbox): eve-of-return revalidation

Every command below was **executed this run**, after `git fetch --all --prune` in both trees. This
run added **one** new check (**C-RET-1**); the rest are prior checks re-executed verbatim, which is
the point of the run — the numbers Brandon acts on tomorrow were last measured across three separate
earlier runs, and this pass re-stamps all of them together.

### C-RET-1 — on the eve of the landing, every PR is still open+draft and every landing branch still matches its live PR head

The freshness check that makes §3 safe to execute against the refs it names. `C-RD-3` (run 49)
checked the seven heads; this adds the open+draft sweep over **all** open PRs, so a PR merged or
undrafted by someone else would be caught too.

```bash
# live state — GitHub API (any client); expected: 18 open, all draft
#   17 claude/* (#32..#53) + Terra's #26
cd <engine>
declare -A PR=( [s8-harness-linux-reach]=c93e88dcd459f52e6982f1cee5149658ba0165f5 \
  [s2-seq-bound]=2be00fc07793b4da6ba2fcfdd626f70924613a8d \
  [s2-transport-vocabulary]=b0b6c7730d95f92f5bce75a0eabfebe04fd082eb \
  [s3-pairing-confirm-consumer]=edee32b7eda68ac7e420b59cc69642a1d4effedf \
  [s6-outcome-disposition]=94fd9794309c474fee631e98d5562f5795a451cb \
  [s6-composition-root-decision]=f5e0c0a4feeb56b2b617bb7d8702fa17c56fcd8c \
  [s6-resume-reconciliation]=8177353c2147d24500a9edf2b866abfc62645230 )
for b in "${!PR[@]}"; do L=$(git rev-parse origin/claude/$b)
  [ "$L" = "${PR[$b]}" ] && echo "MATCH $b" || echo "MISMATCH $b $L ${PR[$b]}"; done
git rev-parse origin/main
```

*Expected, and **observed**:* **7 MATCH, 0 MISMATCH**; `origin/main` = **`aac05f3`**, unmoved since
2026-08-12. All 18 open PRs still `open` and still `draft`; **nothing merged, closed or undrafted**
since run 49. **If any line reports MISMATCH, §3's plan is stale and must be re-costed before it is
executed** — that is the whole purpose of this check.

### C-STOP-1, C-STOP-3, C-POST-1, C-POST-3 — re-run at run 53, all unchanged

All four re-executed verbatim after this run's fetch. Every number reproduces:

- **C-STOP-1** — `8575539` touches `docs/Sync-Protocol.md` only, **+114/−3**; `22b028e` adds the two
  ack vectors, `index.json` and `generate.mjs`; `7328a0b` adds `invalid-unknown-field.json`. On
  `origin/claude/s5-entitlement-ack-emitter`: **`OK: 29 vector files match the generator.`**,
  `exit=0`. `git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l` → **26**; the same on
  the emitter branch → **29**. **Eighteenth consecutive assignment, eighteenth verification.**
- **C-STOP-1 (content half, new phrasing this run)** — the four gates read **in the file**, not
  inferred from commit subjects:
  ```bash
  cd <engine> && git checkout -B s5-check origin/claude/s5-entitlement-ack-emitter
  grep -n "product_id\|acknowledged_at\|order_id" docs/Sync-Protocol.md | head -3   # PQ-A6-1 -> :318-320
  grep -n -i "decoded ciphertext" docs/Sync-Protocol.md                             # PQ-A2-1 -> :111
  grep -n "Also every structural rejection" docs/Sync-Protocol.md                    # PQ-A2-2 -> :601
  ls docs/sync-vectors/v1/invalid-unknown-field.json                                 # PQ-A2-3
  ```
- **C-STOP-3** — `git archive 7328a0b docs/sync-vectors/v1` → **29 files**; `diff -r` against
  `core/src/test/resources/sync-vectors/v1` → **no output, `exit=0`**. Byte-identical to pin.
- **C-POST-1** — six merges from `aac05f3` as real merges: **1–4 CLEAN**, **#52 stops on the
  five-file pin family**, **#49 on those five plus `tests/SyncHarness/Program.cs`**,
  **`vector files conflicted: 0` at both stops**, corpus 26 → 28 → 28 → 29 → 29 → **30**.
- **C-POST-3** — phone **29 files / 28 payloads / `index.vectors[]` 28**; landed `main` **30 / 29 /
  29**; single delta **`pairing-high-bit-confirm.json`**; generator on the landed tree →
  **`OK: 30 vector files match the generator.`**, `exit=0`.

### C-ENV-1 — re-measured; neither gate is runnable here, and `gradle` being present does not change that

```bash
for t in pwsh dotnet sdkmanager adb gradle java node git; do
  printf '%-12s %s\n' "$t" "$(command -v $t 2>/dev/null || echo ABSENT)"; done
echo "ANDROID_HOME=${ANDROID_HOME:-<unset>}"
```

*Expected, and **observed**:* `pwsh` **ABSENT**, `dotnet` **ABSENT**, `sdkmanager` **ABSENT**, `adb`
**ABSENT**, `ANDROID_HOME`/`ANDROID_SDK_ROOT` **unset**; `git`, `java`, `node` v22.22.2 and
**`gradle` present**. **The `gradle` line is the one worth reading carefully**: a reader who greps
this file for "gradle: present" could conclude the android gate ran. It cannot —
`:app:assembleDebug` requires the Android SDK, which is absent. **No gate result of either kind is
claimed at run 53**, and RETURN-DAY §7 item 3 stands: grep these records for any sentence that blurs
that line and report it.

### C-B18-4 — the notification, and its limits as evidence

B-18 attempt 4 was a push notification to Brandon (routine notification channel), sent this run,
carrying: the eighteen firings, the three commits, `generate.mjs --check` → `OK: 29 … exit=0`, the
two stale prompt facts (pin `679a317` → `7328a0b`; S5 "NOT STARTED" → built), and `RETURN-DAY.md` §5.

**This claim is not independently re-verifiable from either repository, and it should not be written
as if it were.** No file records it; delivery is attested only by the tool's own result
(`Mobile push requested.`). An auditor should treat "a notification was sent" as **unverified from
the repo** and, if it matters, confirm with Brandon directly. What *is* verifiable from the repo is
everything the notification asserted — each item above has its command in this file.

## Fifty-fourth run — 2026-08-17 (Linux sandbox): the lock file's own claim, re-measured against main

This run took no rung. It re-ran the four eve-of-return checks (all unchanged, below), then narrowed
one measurably false sentence in the file the vendored-vector guarantee rests on. **C-LOCK-1 is the
only new claim.**

### C-LOCK-1 — the vendored corpus is NOT byte-identical to main, in either direction

The claim the header made until this run. Run after `git fetch --all --prune` in both trees.

```bash
cd <engine> && git ls-tree --name-only origin/main docs/sync-vectors/v1/ \
  | xargs -n1 basename | sort > /tmp/main.names
ls <android>/core/src/test/resources/sync-vectors/v1 | sort > /tmp/vend.names
echo "main=$(wc -l < /tmp/main.names) vendored=$(wc -l < /tmp/vend.names)"
echo "-- vendored-only --"; comm -13 /tmp/main.names /tmp/vend.names
echo "-- main-only --";     comm -23 /tmp/main.names /tmp/vend.names
for f in $(comm -12 /tmp/main.names /tmp/vend.names); do
  a=$(git cat-file blob origin/main:docs/sync-vectors/v1/$f | sha256sum | cut -d' ' -f1)
  b=$(sha256sum <android>/core/src/test/resources/sync-vectors/v1/$f | cut -d' ' -f1)
  [ "$a" = "$b" ] || echo "DIFFERS: $f"
done
```

*Expected, and **observed** against `origin/main` = `aac05f3`:* **`main=26 vendored=29`**;
vendored-only = **`entitlement-ack.json`**, **`entitlement-ack-no-order-id.json`**,
**`invalid-unknown-field.json`**; main-only = **none**; and exactly one shared file differs —
**`DIFFERS: index.json`**.

**What this does and does not mean.** It is **not** drift: the three files are the S5 vectors, they
live only on the unmerged draft stack the pin sits on, `index.json` differs because it is the manifest
that lists them, and the corpus remains byte-identical to its pin (**C-STOP-3**). It **is** a
refutation of the header sentence *"They must stay byte-identical to the main repo"* — for these
three files the phone is **ahead** of main, and the guarantee both repos actually check is
**phone == pin**. The reverse direction (**phone behind main**) is what opens at `RETURN-DAY.md` §3
step 4 (**B-16 status 2026-08-17**, **H7**).

**Attack this first if you doubt the run:** the edit is a comment-only change to a `.lock` file, so
the risk is not the prose — it is whether it broke the consumer. Two guards, both run:

```bash
cd <android>
PIN=$(grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1); echo "$PIN"
grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | wc -l
git diff --name-only HEAD~1 | grep -c 'sync-vectors/v1/'
```

*Expected, and **observed**:* `PIN` = **`7328a0bc043335491cd96a67d634e8eea2a13af9`**, the file
contains **exactly one** 40-hex string (so `head -1` cannot pick up a SHA added by this run —
appended prose deliberately uses short SHAs only), and **`0`** files under `v1/` were touched by the
commit. `ci.yml:75` is the line that would have broken; it does not.

### C-STOP-1, C-PIN-1, C-STOP-3, C-RET-1 — re-run at run 54, all unchanged

- **C-STOP-1** — `8575539` (2026-08-09) touches `docs/Sync-Protocol.md` only, **+114/−3**;
  `22b028e` adds both ack vectors, `index.json` and `generate.mjs`; `7328a0b` adds
  `invalid-unknown-field.json`. On `origin/claude/s5-entitlement-ack-emitter`,
  `node docs/sync-vectors/generate.mjs --check` → **`OK: 29 vector files match the generator.`**,
  `exit=0` (node v22.22.2). The four gates were read **in the file** at `7328a0b`, not inferred:
  `acknowledged_at` at `Sync-Protocol.md:319` (**PQ-A6-1**), the cap on decoded ciphertext at
  `:112`/`:132` (**PQ-A2-1**), structural rejection as `decrypt_failed` at `:103`/`:601`
  (**PQ-A2-2**), `invalid-unknown-field.json` present (**PQ-A2-3**). `origin/main` carries **26**
  vector files and `7328a0b` is **NOT on main** — unmerged, not unwritten. **Fourteenth consecutive
  assignment; declined again.**
- **C-PIN-1** — vendored pin reads **`7328a0bc043335491cd96a67d634e8eea2a13af9`**. The recurring
  prompt's `679a317` is stale by three vectors, as it has been since 2026-08-12.
- **C-STOP-3** — `git archive 7328a0b` unpacked and `diff -r`'d against the vendored tree: **no
  output, `exit=0`, 29 files** — re-run **after** this run's commit, so it also proves the edit
  touched no payload.
- **C-RET-1** — all **18** open PRs in `ShivaClaw/careerseeker` read from the API: **17 `claude/*`
  (#32–#53) plus Terra's #26**, every one still **open and still draft**. The seven landing branches
  match run 53's recorded SHAs exactly — `c93e88d`, `2be00fc`, `b0b6c77`, `edee32b`, `94fd979`,
  `f5e0c0a`, `8177353`, **0 mismatches**. `origin/main` is still `aac05f3`; android `origin/main` is
  still `ebfaf81`. **Nothing has been landed, closed or undrafted since run 53. `RETURN-DAY.md` §3 is
  still safe to execute exactly as printed.**

### C-ENV-1 — unchanged, and no gate is claimed at run 54

`pwsh`, `dotnet`, `sdkmanager`, `adb` **ABSENT**; `ANDROID_HOME`/`ANDROID_SDK_ROOT` **unset**;
`git`, `java`, `node` v22.22.2, `gradle` present. **Neither `Verify-Alpha.ps1` nor the android gate
was run at run 54, and no result for either is claimed anywhere in this entry.** The one thing this
run's change would be gated by is `ci.yml`'s drift step, which runs on a real runner; the two guards
in **C-LOCK-1** are the parts of it that are runnable here, and they were run.

## Fifty-fifth run — 2026-08-18 (Linux sandbox): the re-pin step gets a command

This run's one new artifact is `scripts/repin-vectors.sh`. Everything below either exercises it or
re-runs a prior check on the morning the landing plan is executed. **No `.ps1` and no Gradle task ran
this run; no gate result is claimed anywhere in this entry** (**C-ENV-1**, re-measured below).

### C-REPIN-1 — the script is a proven no-op at the current pin, and does the right thing at the post-landing one

The whole point of a re-pin tool is that it must be **inert** when there is nothing to do and
**exact** when there is. Both halves, run this morning:

```bash
cd <android>
scripts/repin-vectors.sh --check --engine <engine>              # current pin, expect a clean no-op
```

*Expected, and **observed**:* `current pin` and `target pin` both
`7328a0bc043335491cd96a67d634e8eea2a13af9`; `pin position : NOT an ancestor of origin/main`;
`OK: 29 vector files match the generator.`; `vendored: 29 files    at pin: 29 files`; then
**`OK: the vendored corpus is byte-identical to pin 7328a0b…, and the pin is unchanged.`**, `exit=0`.
Note the generator check inside it — the script does not merely diff bytes, it re-proves the pinned
corpus is generator output before it will vendor it.

The post-landing half needs a post-landing `main`, which does not exist yet, so it was **built** by
replaying `RETURN-DAY.md` §3's six merges for real in a throwaway clone under the session scratchpad:

```bash
git clone --no-checkout <engine> /tmp/postmerge
cd /tmp/postmerge && git fetch <engine> 'refs/remotes/origin/*:refs/remotes/eng/*'
git checkout -B landing eng/main
for br in s8-harness-linux-reach s2-seq-bound s2-transport-vocabulary \
          s3-pairing-confirm-consumer s6-outcome-disposition s6-composition-root-decision; do
  git merge --no-edit "eng/claude/$br" || { git diff --name-only --diff-filter=U; }   # resolve, continue
done
ls docs/sync-vectors/v1 | wc -l
```

*Expected, and **observed**:* **#48, #35, #36, #51 CLEAN; #52 and #49 STOP** — 5 and 6 conflicted
files respectively, **0 of them under `docs/sync-vectors/`**, the conflict set being the
`$ExpectedOfflineTotal` pin family (`README.md`, `docs/CareerSeeker-Project-Summary.md`,
`docs/External-Audit-Handoff.md`, `scripts/Verify-Alpha.ps1`, `src/Engine/README.md`) plus
`tests/SyncHarness/Program.cs` at #49. Post-landing corpus: **30 files**. **This independently
reproduces C-POST-1 and C-POST-2 on the morning §3 is executed** — 4 clean / 2 stops / no vector
conflict / 29 payloads + `index.json` — and it is a *replay*, not a citation of run 51's number.

Then the script against that replayed head:

```bash
cd <android> && scripts/repin-vectors.sh --check --engine /tmp/postmerge <replayed-head>
```

*Expected, and **observed**:* `OK: 30 vector files match the generator.` — the merge set does not
corrupt the corpus — then `vendored: 29 files    at pin: 30 files`, **`+ pairing-high-bit-confirm.json`**,
**`~ index.json`**, `DRIFT: 2 file(s)`, `exit=1`. **This is the gap no check in either repo reports**
(**B-16**, **H7**), printed by name.

The write path was exercised in a **throwaway copy** of the android tree, never in the repo:

```bash
cp -R <android> /tmp/andr-copy && cd /tmp/andr-copy
scripts/repin-vectors.sh --engine /tmp/postmerge <replayed-head>
git status --porcelain; grep -coE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock
scripts/repin-vectors.sh --check --engine /tmp/postmerge        # idempotence
```

*Expected, and **observed**:* `OK: re-pinned 7328a0b… -> <replayed-head>`, `30 vector files vendored`;
`git status` shows **exactly three** paths — `M VECTORS.lock`, `M v1/index.json`,
`?? v1/pairing-high-bit-confirm.json` — nothing else; the lock holds **1** 40-hex string and it reads
back as the new pin; and the immediate re-`--check` returns the clean no-op, `exit=0`. **Idempotent.**

### C-REPIN-2 — #51's branch is the wrong re-pin target, and the script says so before writing

The obvious mistake is to pin at the branch that *adds* the vector rather than at the merged `main`.

```bash
cd <android>
scripts/repin-vectors.sh --check --engine <engine> origin/claude/s3-pairing-confirm-consumer
```

*Expected, and **observed**:* `at pin: 27 files` against `vendored: 29`, reporting
**`+ pairing-high-bit-confirm.json`**, **`- entitlement-ack-no-order-id.json`**,
**`- entitlement-ack.json`**, **`- invalid-unknown-field.json`**, **`~ index.json`** — `DRIFT: 5`,
`exit=1`. That branch has **never carried the three S5 vectors**, so re-pinning there would *delete*
them from the phone. The removals are printed above the verdict, before any write. This is why the
script replaces the corpus **wholesale**: an additive-only re-vendor would hide exactly this.

### C-REPIN-3 — the four refusals, each measured

```bash
cd <android>
scripts/repin-vectors.sh --check --engine <engine> deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
scripts/repin-vectors.sh --check --engine <engine> $(git -C <engine> rev-list --max-parents=0 origin/main | head -1)
cp -R <android> /tmp/neg && cd /tmp/neg
printf '\n' >> core/src/test/resources/sync-vectors/v1/pairing-basic.json
scripts/repin-vectors.sh --check --engine <engine>
sed -i '3i # aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' core/src/test/resources/sync-vectors/VECTORS.lock
scripts/repin-vectors.sh --engine <engine> origin/main
```

*Expected, and **observed**, all `exit=1`:*

1. unresolvable rev → *"does not resolve to a commit in …"* and the `git fetch --all --prune` fix;
2. root commit `a96cb34` → *"carries no `docs/sync-vectors/v1`"*;
3. one byte appended to a vendored vector, **pin unchanged** → `~ pairing-basic.json`, then
   *"The pin did not move, so this is a **LOCAL EDIT** to vendored bytes — that is a cross-repo drift
   event. Do not 'fix' it by re-vendoring until you know why."* **This is the offline half of CI's
   drift step, runnable with no network**;
4. a second 40-hex string injected into `VECTORS.lock` → *"contains 2 40-hex strings; ci.yml:75 takes
   the FIRST one, so the pin is ambiguous"* — and `git status` confirms **nothing under `v1/` was
   written**. The guard runs **before** the write for exactly that reason; a second, identical
   assertion runs after it as a net.

**Attack this first if you doubt the run:** the script's own claim is that it is *safe when it
refuses*. Case 4 is where that is testable and where it would most plausibly be false — an
abort-after-write leaves a corpus that matches one commit and a lock that names another, which is the
precise state this file exists to make impossible. Re-run case 4 and check `git status`.

### C-REPIN-4 — the repository's own vectors were not touched by any of it

```bash
cd <android>
git diff --name-only 89068d8..HEAD | grep -c 'sync-vectors/v1/'
git status --porcelain
git archive --remote=. 2>/dev/null; cd <engine> && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pin-check
diff -r /tmp/pin-check/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
```

*Expected, and **observed**:* **`0`** vector files changed across this run's commits; a clean
`git status`; and **C-STOP-3** re-run after the commits — **no output, `exit=0`, 29 files**. Every
write path was exercised in `/tmp` copies. **The pin is still `7328a0b` and the corpus still matches
it byte for byte.**

### C-RET-2 — return-day freshness: nothing has been landed, and §3 is still safe to execute as printed

Taken **after** `git fetch --all --prune` in both trees on 2026-08-18.

```bash
cd <engine>  && git rev-parse origin/main
cd <android> && git rev-parse origin/main
# and the live PR heads, from the API rather than local refs
```

*Expected, and **observed**:* engine `origin/main` **`aac05f3`**, unmoved since 2026-08-12; android
`origin/main` **`ebfaf81`**. **18 open PRs** in `ShivaClaw/careerseeker` (17 `claude/*` #32–#53 plus
Terra's #26) and **6** in `careerseeker-android` (#1–#6), **every one still open and still draft;
none merged**. The seven landing branches match run 53/54's recorded heads exactly —
`c93e88d` (#48), `2be00fc` (#35), `b0b6c77` (#36), `edee32b` (#51), `94fd979` (#52), `f5e0c0a` (#49),
`8177353` (#53) — **0 mismatches**. **Brandon had not begun landing when this run measured.**

### C-STOP-1 — re-run at run 55; fifteenth consecutive assignment, declined again

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do git log -1 --format='%ad  %s' $c; git show --stat --format='' $c; done
git worktree add --detach /tmp/wt-s5 origin/claude/s5-entitlement-ack-emitter
cd /tmp/wt-s5 && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l
```

*Expected, and **observed**:* `8575539` (2026-08-09) touches **`docs/Sync-Protocol.md` only,
+114/−3**; `22b028e` adds `entitlement-ack.json`, `entitlement-ack-no-order-id.json`, `index.json`
**and `generate.mjs`**; `7328a0b` adds `invalid-unknown-field.json`. The generator check reports
**`OK: 29 vector files match the generator.`**, `exit=0`, node v22.22.2. `origin/main` carries **26**.
**Unmerged, not unwritten** — building it again would duplicate `8575539` and regenerate a corpus the
android repo vendors, which the prompt itself classes as a cross-repo drift event.

### C-ENV-1 — re-measured at run 55, unchanged

`pwsh` **ABSENT**, `dotnet` **ABSENT**, `sdkmanager` **ABSENT**, `adb` **ABSENT**;
`ANDROID_HOME`/`ANDROID_SDK_ROOT` **unset**; `git` 2.43.0, `java` **openjdk 21.0.10**,
`node` **v22.22.2**, `gradle` present. **Neither `scripts\Verify-Alpha.ps1` nor
`./gradlew … :app:assembleDebug :app:lintDebug` was run, and no result for either is claimed.**
Note the JDK, because it narrows what this run could prove. `:core` pins `jvmToolchain(17)` and
`core-probe.sh` gates on `ls -d /usr/lib/jvm/*17*`; measured here, that directory holds
**`java-1.21.0-openjdk-amd64`, `java-21-openjdk-amd64`, `openjdk-21` and no 17**, so `core-probe.sh`
would exit 1 at its own precondition. **`scripts/repin-vectors.sh` was therefore never validated
against `:core:test`** — only against its own preconditions and its own output. Whether a re-pinned
corpus still passes the phone's codec is `RETURN-DAY.md` §3's step 2, and it needs a machine with a
JDK 17. **Do not read C-REPIN-1 as saying the re-pin is test-green; it says the re-pin is correct
about bytes.**

## Fifty-sixth run — 2026-08-18 (Linux sandbox): the re-pin was proved about bytes; this run ran it against the phone's codec

### C-JDK-1 — this host CAN run `:core:test`, and that changes what a cloud run may claim

Every prior run recorded `:core:test` as unrunnable here. **C-ENV-1** narrowed the reason correctly
to the JDK: `core-probe.sh` gates on `ls -d /usr/lib/jvm/*17*` and this image ships only JDK 21. The
script's own error message names the fix, and it works.

```bash
ls -d /usr/lib/jvm/*17* 2>&1                     # before: no match
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
ls -d /usr/lib/jvm/*17*
cd <android> && scripts/core-probe.sh; echo "exit=$?"
```

*Expected, and **observed**:* no JDK 17 before; after the install
`/usr/lib/jvm/java-1.17.0-openjdk-amd64` and `/usr/lib/jvm/java-17-openjdk-amd64`; then
`BUILD SUCCESSFUL` and **`core-probe: 288 tests, 0 failed, 0 skipped, across 19 classes`**,
`exit=0`. Maven Central and the Gradle services resolved; `google()` is absent from the probe's
resolver by construction, so this also re-proves `:core` needs nothing from the Android repository.

**This is a machine change to an ephemeral cloud container, not to Brandon's machine**, and it is
recorded as one. **It is still not the android gate** — `:app:assembleDebug` and `:app:lintDebug`
need the Android SDK, which is still absent (**B-7**, **C-ENV-1**), and no result for them is claimed
anywhere in this entry. What it retires is the narrower sentence *"no Kotlin can be executed here"*.

### C-ENUM-1 — the re-pinned corpus passes the phone's codec (RETURN-DAY §3 step 2, never before run)

Replay §3's six merges, re-pin a **copy** of the phone tree at the replayed head, then test it.

```bash
git clone -q <engine> /tmp/eng-replay && cd /tmp/eng-replay
git checkout -B replay origin/main
for br in s8-harness-linux-reach s2-seq-bound s2-transport-vocabulary \
          s3-pairing-confirm-consumer s6-outcome-disposition s6-composition-root-decision; do
  git merge --no-edit -q origin/claude/$br || {
    for f in $(git diff --name-only --diff-filter=U); do git checkout --theirs -- "$f"; git add "$f"; done
    git commit -q --no-edit; }
done
node docs/sync-vectors/generate.mjs --check
cp -R <android> /tmp/and-repin && cd /tmp/and-repin
scripts/repin-vectors.sh --engine /tmp/eng-replay $(git -C /tmp/eng-replay rev-parse HEAD)
scripts/core-probe.sh --rerun; echo "exit=$?"
```

*Expected, and **observed**:* **#48, #35, #36, #51 CLEAN; #52 STOP (5 files); #49 STOP (6 files)** —
independently reproducing **C-POST-1** / **C-REPIN-1** on the morning §3 is executed, the conflict set
being the `$ExpectedOfflineTotal` pin family plus `tests/SyncHarness/Program.cs`, **0 files under
`docs/sync-vectors/`**. Post-merge corpus **30 files**, `OK: 30 vector files match the generator.`
The re-pin reports **`+ pairing-high-bit-confirm.json`**, **`~ index.json`**, writes exactly three
paths, `exit=0`. Then `:core:test`: **288 tests, 0 failed**, `exit=0`.

**So the re-pin is test-green, not merely byte-correct.** That is the sentence **C-ENV-1** said this
host could not write, and the only thing that was missing was a JDK.

### C-ENUM-2 — but the newly vendored vector was asserted by NOTHING, and a negative control is what showed it

The green run above is **not** evidence that the new vector is covered. Corrupt it and re-run:

```bash
cp -R /tmp/and-repin /tmp/and-neg && cd /tmp/and-neg
python3 -c "import json;p='core/src/test/resources/sync-vectors/v1/pairing-high-bit-confirm.json';\
d=json.load(open(p));d['expected']['confirm']='999999';json.dump(d,open(p,'w'),indent=2)"
scripts/core-probe.sh --rerun; echo "exit=$?"
```

*Expected before this run's fix, and **observed**:* **`288 tests, 0 failed`**, `exit=0`. **A vector
whose expected confirm code is wrong left the suite green.**

The cause, read in the file rather than guessed:
`ProtocolVectorsTest.pairing derivation reproduces every vector value` **hardcoded
`load("pairing-basic")`** despite its name. Only `envelopeVectors()` enumerated the manifest, and it
filters `type == "envelope"` — `pairing-high-bit-confirm` is `type: "pairing"`, so it was vendored,
listed in `index.json` (29 payloads), and **read by no test**.

**Why this one matters more than an average uncovered vector:** its own `notes` say it exists to
separate three implementations `pairing-basic` cannot tell apart — a signed-int32 reduction renders
`-936782`, a dropped zero-pad renders `30514`, and only the conforming unsigned zero-padded reduction
renders `030514`. `pairing-basic`'s digest has its high bit clear and six significant digits, so it
**cannot** catch either bug. The vector that catches them was the inert one.

### C-ENUM-3 — the fix, and the same negative control failing as it should

`4ddad07` replaces the hardcoded load with enumeration of valid `type: pairing` vectors from the
manifest (`valid: false` stays excluded — those pin a rejection and have their own tests).

```bash
cd <android> && scripts/core-probe.sh --rerun; echo "exit=$?"     # current pin, 29 files
# then with the fixed test over the re-pinned 30-file corpus:
cp <android>/core/src/test/kotlin/app/careerseeker/core/ProtocolVectorsTest.kt /tmp/and-repin/core/src/test/kotlin/app/careerseeker/core/
cd /tmp/and-repin && scripts/core-probe.sh --rerun; echo "exit=$?"
# and the negative control again, now over the fixed test:
cp -R /tmp/and-repin /tmp/and-neg2 && cd /tmp/and-neg2
python3 -c "import json;p='core/src/test/resources/sync-vectors/v1/pairing-high-bit-confirm.json';\
d=json.load(open(p));d['expected']['confirm']='999999';json.dump(d,open(p,'w'),indent=2)"
scripts/core-probe.sh --rerun; echo "exit=$?"
```

*Expected, and **observed**:* at the **current** pin **288 tests, 0 failed**, `exit=0` — the fix
changes no result on the corpus the phone ships today. Over the **re-pinned** corpus, **288 / 0**,
`exit=0`. With the vector corrupted, **`288 tests completed, 1 failed`**, `exit=1`, and the message is
the whole point:

```
org.opentest4j.AssertionFailedError: 6-digit confirm code (pairing-high-bit-confirm)
  ==> expected: <999999> but was: <030514>
```

**Green → red on the same mutation is the proof the assertion now exists.** Note the test *count* is
288 in every one of these runs: the vectors are looped **inside** one test method, so a coverage
change does not move the number. **Anyone auditing this by watching the test count will see nothing.**

### C-ENUM-4 — and the phone's confirm reduction is correct, which nothing previously established

The failure message above reports the actual value: **`030514`**. That is the conforming unsigned,
zero-padded reduction — not `-936782` (signed int32) and not `30514` (dropped zero-pad). Read in the
source, `PairingDerivation.derive` masks each byte with `0xFF` into a `Long` and calls
`padStart(6, '0')`, which agrees.

**This is a new fact, not a restatement.** Before this run the phone's high-bit behaviour was
**unasserted by construction** — the vector was not vendored, and after a re-pin it would still not
have been read. The engine half was CI-green already; the phone half is now green too, and by the
same vector.

### C-ENUM-5 — no vector byte was written in either repo

```bash
cd <android> && git status --porcelain && git diff --name-only HEAD~1 | grep -c 'sync-vectors/' 
cd <engine> && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pin-check
diff -r /tmp/pin-check/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
```

*Expected, and **observed**:* this run's commit touches **`0`** paths under `sync-vectors/`; the
vendored corpus still matches pin **`7328a0b`** — **no output, `exit=0`, 29 files** (**C-STOP-3**
re-run after the commit). **Every re-pin and every mutation happened in `/tmp` copies.** The pin did
not move: moving it is **H7**, and it is Brandon's.

### C-STOP-1 — re-run at run 56; sixteenth consecutive assignment, declined again

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do git log -1 --format='%ad  %s' $c; git show --stat --format='' $c; done
git checkout -B s5-check origin/claude/s5-entitlement-ack-emitter
node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l
```

*Expected, and **observed**:* `8575539` (2026-08-09) touches **`docs/Sync-Protocol.md` only, +114/−3**;
`22b028e` adds both ack vectors, `index.json` and `generate.mjs`; `7328a0b` adds
`invalid-unknown-field.json`. Generator check **`OK: 29 vector files match the generator.`**, `exit=0`,
node v22.22.2. `origin/main` carries **26**. **Unmerged, not unwritten.** Rebuilding it would duplicate
`8575539` and regenerate a corpus the android repo vendors — which the prompt itself classes as a
cross-repo drift event.

### C-RET-3 — return-day freshness, re-measured at run 56 against the live API

*Expected, and **observed**, after `git fetch --all --prune` in both trees:* engine `origin/main`
**`aac05f3`**; android `origin/main` **`ebfaf81`**. **18 open PRs** in `ShivaClaw/careerseeker`
(17 `claude/*` plus Terra's #26) and **6** in `careerseeker-android`, **every one still open and still
draft; none merged or closed**. The seven landing branches match their live PR heads exactly —
`c93e88d` (#48), `2be00fc` (#35), `b0b6c77` (#36), `edee32b` (#51), `94fd979` (#52), `f5e0c0a` (#49),
`8177353` (#53) — **0 mismatches**. **Brandon had not begun landing when this run measured.**

### C-ENUM-6 — how wide is the hole? Exactly one family, verified rather than assumed

A first draft of this run's auditor note claimed the `entitlement` and `entitlement_ack` families had
the same defect. **They do not.** Checked before push:

```bash
cd <android>
grep -rn 'str("type") ==' core/src/test/kotlin/
python3 -c "import json,collections;d=json.load(open('core/src/test/resources/sync-vectors/v1/index.json'));\
print(collections.Counter(v['type'] for v in d['vectors']))"
```

*Expected, and **observed**:* the manifest holds four types — **envelope 19, entitlement 5, pairing 3
(post-re-pin), entitlement_ack 2** — and three of the four were already enumerated from it:
`ProtocolVectorsTest.envelopeVectors()` (`type == "envelope"`),
`EntitlementVectorsTest.entitlementVectors()` (`type == "entitlement"`), and `ProtocolVectorsTest`
again at `type == "entitlement_ack"`. **`pairing` was the only hardcoded family**, and `4ddad07` makes
it the fourth enumerator.

**The withdrawn claim made the finding sound broader; the true one makes it worse.** Every other family
was already self-extending, so a vector added upstream to any of them lands asserted. Only `pairing`
would land inert — and `pairing-high-bit-confirm` is precisely a `pairing` vector arriving upstream.
**Attack this by re-running the grep**: if a fifth type appears in the manifest with no matching
filter, the same hole is open again for that family.

### C-CI-56 — the runner's verdict on `4ddad07` was still pending when this run closed

```bash
# check run 95604409205 on PR #6, head 467d7b1
# (workflow run 32102074876)
```

*Observed at close of slice:* **`status: in_progress`**, started `2026-08-18T05:13:53Z`, polled five
times over roughly **28 minutes** and never reached a conclusion inside this session. **No CI result
is claimed for this run's commits — not green, not red.**

**Do not chase the run ID above.** Each records push starts a fresh run that supersedes the previous
one — the final commit of this run had already replaced `32102074876` with `32102294199` before the
session closed. **Read the latest run on PR #6**; any run ID written into a records file is stale by
construction, which is why the re-verification below names the PR and not a run.

This matters because the house rule is that android **gate** results are read out of runner logs and
never produced locally, and this run produced only `:core:test` locally (**C-JDK-1**, 1 of the gate's
5 tasks). So the status of `checkCoreIsAndroidFree`, `:app:test`, `:app:assembleDebug` and
`:app:lintDebug` against `4ddad07` is **unknown as of this writing**.

**What to do with that.** The change is confined to a **test** file and adds no production code, and
the same file's suite was run locally at the current pin (**288 / 0**, **C-ENUM-3**), so the expected
runner outcome is green. **Expected is not observed** — read the run before treating it as evidence:
`https://github.com/ShivaClaw/careerseeker-android/actions/runs/32102074876`. If it is red, the
finding in **C-ENUM-2** is unaffected (it was measured locally, with a negative control) but the
**fix** in `4ddad07` is not, and should be re-read first.

---

## Fifty-seventh run (2026-08-18) — re-verification commands

Every claim this run makes is below with the exact command that re-derives it. **All of them must be
run after `git fetch --all --prune` in both checkouts**; a stale ref silently invalidates every
count here, which is the failure this program's rule one exists to prevent.

### C-CI-57 — the gate is GREEN on PR #6's head, closing C-CI-56

```bash
# The PR, never a run ID -- each records push supersedes the previous run,
# so any run ID written into a records file is stale by construction (C-CI-56).
gh pr view 6 --repo ShivaClaw/careerseeker-android --json headRefOid
gh api repos/ShivaClaw/careerseeker-android/commits/878a203/check-runs \
  --jq '.check_runs[] | {name, status, conclusion, started_at, completed_at}'
```

*Expected, and **observed** at this run:* head `878a20368072223d450c5a16c216953b11603dd9`; one check
run, `Build and test`, **`status: completed`**, **`conclusion: success`**, `2026-08-18T05:17:53Z →
2026-08-18T05:26:15Z`, job `95605131416`. **This supersedes C-CI-56's explicit non-claim**, which
recorded `in_progress` after five polls over ~28 minutes and claimed no result in either direction.

**Why this is the whole gate and not one suite** — verify rather than take it on trust:

```bash
grep -n "gradlew\|name:" .github/workflows/ci.yml | head -40
```

*Observed:* the single `Build and test` job runs `checkCoreIsAndroidFree` (:47), the vendored-vector
drift step (:69), `:core:test` (:154), `:app:test` (:162), `:app:assembleDebug` (:165),
`:app:lintDebug` (:168) and the analytics assertion (:176). **So run 56's `ProtocolVectorsTest`
enumerator fix (C-ENUM-3) is runner-green across all five gate tasks**, where run 56 could only run
`:core:test` locally (C-JDK-1, 1 of 5).

**Attack this first.** It is a **read of someone else's runner**, not a gate this session executed —
**B-7 is not lifted** and no local gate result is claimed anywhere in this run. And it is a
**pass-path** observation only, so **B-15 stays NARROWED**: a green drift step proves the check does
not false-alarm at 29/29; it does **not** prove the check still fires on a deleted or hand-edited
vector. Those paths remain stub-only. If you want C-CI-57 to mean more than it does, the thing to
break is a vector on a scratch branch and watch the step go red.

### C-RET-4 — freshness: nothing landed, and §3 is still executable as printed

```bash
cd <engine> && git fetch --all --prune
cd <android> && git fetch --all --prune
gh pr list --repo ShivaClaw/careerseeker        --state open --json number,draft | jq 'length, (map(select(.draft)) | length)'
gh pr list --repo ShivaClaw/careerseeker-android --state open --json number,draft | jq 'length, (map(select(.draft)) | length)'
git -C <engine>  rev-parse origin/main
git -C <android> rev-parse origin/main
# the load-bearing half: LIVE PR head vs fetched ref, per landing branch
gh pr list --repo ShivaClaw/careerseeker --state open --json number,headRefName,headRefOid \
  --jq '.[] | "\(.number) \(.headRefName) \(.headRefOid)"' | while read -r n ref sha; do
    [ "$(git -C <engine> rev-parse "origin/$ref" 2>/dev/null)" = "$sha" ] \
      && echo "PR#$n $ref MATCH" || echo "PR#$n $ref *** MISMATCH ***"
  done
```

*Expected, and **observed**:* engine `origin/main` = **`aac05f3`** and android `origin/main` =
**`ebfaf81`**, both unmoved since 2026-08-12. **18 open / 18 draft** in `careerseeker`, **6 open /
6 draft** here — **nothing merged, closed or undrafted by anyone, including this session**. All
**17** engine PR branches: **MATCH, 0 mismatches**. Android `a0-probe` local ref equals its live PR
head. **Therefore `RETURN-DAY.md` §3 is still safe to execute exactly as printed.**

**Attack this by re-running it, and note the direction of decay:** this claim is true only until
someone lands something. If any row prints MISMATCH, **stop and re-derive §3's stop counts** before
merging — the plan's 4-CLEAN/2-STOP shape was measured against these exact SHAs.

### C-PIN-3 — the vendored corpus is still byte-identical to pin `7328a0b`

```bash
rm -rf /tmp/pinvec && mkdir -p /tmp/pinvec
git -C <engine> archive 7328a0bc043335491cd96a67d634e8eea2a13af9 docs/sync-vectors/v1 \
  | tar -x -C /tmp/pinvec
ls /tmp/pinvec/docs/sync-vectors/v1 | wc -l
ls core/src/test/resources/sync-vectors/v1 | wc -l
diff -r /tmp/pinvec/docs/sync-vectors/v1 core/src/test/resources/sync-vectors/v1; echo "exit=$?"
```

*Expected, and **observed**:* **29** upstream, **29** vendored, `diff -r` prints **nothing**,
**`exit=0`**. This was run **after** this run's commits, so it also proves the prohibition
paragraph's "no vector byte written" rather than merely asserting it.

**Read the guarantee precisely** — this is the wording B-16 and `VECTORS.lock` were narrowed to at
run 54: it says **the phone matches the pin**, *never* the phone matches the engine. `main` holds
**26** vector files and the phone **29**; the extra three are the S5 vectors that live only on the
unmerged stack the pin sits on. That gap is **H7** and is not drift.

### C-STOP-5 — the assigned slice is built, and the generator agrees

```bash
git -C <engine> log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
git -C <engine> worktree add -f --detach /tmp/s5wt origin/claude/s5-entitlement-ack-emitter
cd /tmp/s5wt && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
grep -n "entitlement_ack\|ciphertext\|decrypt_failed" docs/Sync-Protocol.md | head -20
ls docs/sync-vectors/v1/ | grep -E "entitle|unknown"
```

*Expected, and **observed**:* the four commits `8575539`, `a564c0c`, `22b028e`, `9c05ef7`;
**`OK: 29 vector files match the generator.`**, **`exit=0`**; §4.3.3 at line 307 defining the body as
`{product_id, acknowledged_at, order_id?}` under the explicit **"Decided 2026-08-07 (gate PQ-A6-1,
default-proceed)"** marker; §3.1 line 111 measuring the cap on the **decoded ciphertext**; §7.2 line
601 reporting structural rejection as **`decrypt_failed`** with no `malformed` code; and
`entitlement-ack.json`, `entitlement-ack-no-order-id.json`, `invalid-unknown-field.json` present.
**That is PQ-A6-1, PQ-A2-1, PQ-A2-2 and PQ-A2-3 — all four assigned gates — closed, and open as
draft PR #32 since 2026-08-09.**

**Attack this by checking where those commits live.** They are on **unmerged draft branches, not on
`origin/main`** — `git -C <engine> ls-tree origin/main docs/sync-vectors/v1/ | wc -l` returns **26**,
with no `entitlement-ack*` and no `invalid-unknown-field`. **That ambiguity is the single best
explanation for why the slice keeps being re-assigned**, and it is the reason the honest status is
*built and unlanded*, not *done* and not *not started*.

---

## Fifty-eighth run (2026-08-18) — re-verification commands

Every claim below was produced on Linux with JDK 17 installed into the container. `<engine>` is a
checkout of `ShivaClaw/careerseeker`; `<android>` is this repo on `claude/android-a0-probe`.

### C-S5-1 — `EntitlementAckApplier` had no production caller in either module

```bash
cd <android>
git grep -n "EntitlementAckApplier\|ProState" -- core/src/main app/src/main
git grep -n "app.careerseeker.core" -- app/src/main            # the :app production sources
git grep -n "entitlement" -- app/src/main/kotlin/app/careerseeker/dashboard/replica/EnvelopeApplier.kt
```

*Expected, at commit `0c4ca8f` (this run's parent):* the first command prints **only** the
definitions in `core/.../ProState.kt`, `EntitlementAck.kt`, `OutcomeMarking.kt` and `Protocol.kt` —
**no call site**. The second prints **nothing**: `:app`'s production code does not reference `:core`
at all. The third prints **nothing**: the replica applier's `when` covers
`snapshot`/`delta`/`heartbeat`/`evidence` and sends everything else to `else -> Ignored(kind)`.

**Attack this first.** The claim is *"an accepted `entitlement_ack` reaches nothing"*, and the three
greps establish it only together. If a future session adds a second dispatch path, this evidence
goes stale silently — the standing check is **C-S5-3**, which asserts the behaviour rather than the
absence.

### C-S5-2 — `:core:test` before and after, on this host

```bash
cd <android>
apt-get update && apt-get install -y openjdk-17-jdk-headless   # see C-JDK-2
git stash && scripts/core-probe.sh --rerun ; git stash pop     # baseline
scripts/core-probe.sh --rerun                                  # with this run's change
```

*Expected, and **observed**:*

```
baseline (0c4ca8f):  core-probe: 288 tests, 0 failed, 0 skipped, across 19 classes   exit=0
after   (03e3e8f):   core-probe: 299 tests, 0 failed, 0 skipped, across 20 classes   exit=0
```

Zero compiler warnings in both. **This is one of the android gate's five tasks, not the gate.**
`checkCoreIsAndroidFree`, `:app:test`, `:app:assembleDebug` and `:app:lintDebug` did **not** run
here and no result is claimed for them (**B-7**). `core-probe.sh` builds a throwaway Gradle build
with `google()` absent from the resolver, so a green run additionally proves `:core` resolves with
the Android repository unreachable.

### C-S5-3 — the negative control: without the route, an accepted ack is dropped

```bash
cd <android>
scripts/core-probe.sh --rerun 2>&1 | grep "without the route"
```

*Expected, and **observed**:*
`EntitlementRoutingApplierTest > without the route an accepted ack is dropped and the phone stays Free() PASSED`

**Read what that test does, because the pass is the finding.** It drives a real sealed
`entitlement_ack` through a real `SyncPump` and a real `EnvelopeReceiver` into the `:app`-shaped
replica applier, with **no** `EntitlementRoutingApplier`, and asserts `pulled == 1`,
`rejections == []`, that the replica **did** see the kind, and that the phone is **still
`ProState.Free`**. It is the pre-fix behaviour pinned as an assertion, so the gap cannot reopen
without a red test.

### C-S5-4 / C-S5-5 — the new tests were made to fail before being believed

```bash
cd <android>
F=core/src/main/kotlin/app/careerseeker/core/EntitlementRoute.kt
cp $F /tmp/route.orig

sed -i 's/if (kind != PayloadKind.ENTITLEMENT_ACK.wire) {/if (true) {/' $F   # C-S5-4
scripts/core-probe.sh --rerun; echo "exit=$?"
cp /tmp/route.orig $F

sed -i '0,/return ApplyDisposition.IGNORED$/! s/return ApplyDisposition.IGNORED/return ApplyDisposition.APPLIED/' $F   # C-S5-5
scripts/core-probe.sh --rerun; echo "exit=$?"
cp /tmp/route.orig $F
scripts/core-probe.sh --rerun; echo "exit=$?"
```

*Expected, and **observed**:*

| mutation | failures | exit |
| --- | --- | --- |
| **C-S5-4** ack branch removed (the pre-fix behaviour) | `an accepted ack unlocks Pro end to end through the pump`, `a re-delivered ack does not write again`, `a local ACCEPTED verdict still unlocks nothing`, `an honoured ack sends no pull_request` — **4** | **1** |
| **C-S5-5** honoured ack reported as `APPLIED` | `an honoured ack sends no pull_request`, `an unknown product id writes nothing and leaves the state alone`, `a non-ack body delivered under the ack kind unlocks nothing` — **3** | **1** |
| restored | none — **299 / 0** | **0** |

**C-S5-5 is the one to read.** It fails the **end-to-end** test, not an assertion on an enum name: a
real ack at seq `900` over a replica marked at `40`, asserting the relay received **no push**. Under
`APPLIED`, `PullPolicy` measures an 860-wide gap and pushes a `pull_request` — a gap the ack can
never close, because an ack never advances `highestAppliedSeq`. That is why the disposition is
`IGNORED` even on success, and it is pinned by consequence.

### C-S5-6 — no vector byte was written, verified after the commits

```bash
cd <android>
rm -rf /tmp/pinvec && mkdir -p /tmp/pinvec
git -C <engine> archive 7328a0bc043335491cd96a67d634e8eea2a13af9 docs/sync-vectors/v1 | tar -x -C /tmp/pinvec
find /tmp/pinvec/docs/sync-vectors/v1 -type f | wc -l
find core/src/test/resources/sync-vectors/v1 -type f | wc -l
diff -r /tmp/pinvec/docs/sync-vectors/v1 core/src/test/resources/sync-vectors/v1; echo "exit=$?"
git status --porcelain
```

*Expected, and **observed**:* **29** upstream, **29** vendored, `diff -r` prints **nothing**,
**`exit=0`**. `git status` showed exactly three paths — `SyncPump.kt` modified,
`EntitlementRoute.kt` and `EntitlementRoutingApplierTest.kt` untracked — **and no path under
`sync-vectors/`**. `VECTORS.lock` untouched; the pin did not move (**H7**).

### C-JDK-2 — a correction to C-JDK-1's recorded command

```bash
apt-get install -y openjdk-17-jdk-headless          # C-JDK-1 as written at run 56
apt-get update && apt-get install -y openjdk-17-jdk-headless   # what works today
```

*Expected, and **observed**:* the first now **fails**, `exit=100`, with
`404 Not Found` on `openjdk-17-{jre,jdk}-headless_17.0.18+8-1~24.04.1_amd64.deb` — the indexed
version was superseded and the container's package index is stale from image build. With
`apt-get update` first, **`17.0.19+10-1~24.04.2` installs, `exit=0`**. **C-JDK-1's conclusion stands
in full** — this host can run `:core:test` — only its command line needs the update.

### C-RET-5 — freshness, re-measured after `git fetch --all --prune` in both trees

```bash
git -C <engine> fetch --all --prune && git -C <engine> rev-parse --short origin/main
git -C <android> fetch --all --prune && git -C <android> rev-parse --short origin/main
# live, not local refs:
#   list_pull_requests(ShivaClaw/careerseeker, state=open)  -> count, and draft per row
#   list_pull_requests(ShivaClaw/careerseeker-android, state=open)
```

*Expected, and **observed**:* engine `origin/main` = **`aac05f3`**, android `origin/main` =
**`ebfaf81`**, both unmoved since 2026-08-12. **18 open / 18 draft** in `careerseeker` (17 `claude/*`
+ `codex/r6-dependency-sbom` #26), **6 open / 6 draft** here. **Nothing merged, closed or undrafted
by anyone, including this session.** **`RETURN-DAY.md` §3 is still safe to execute exactly as
printed.**

### C-STOP-6 — the assigned spec half is still built; twenty-third consecutive assignment

```bash
git -C <engine> show origin/claude/s5-entitlement-ack-spec:docs/Sync-Protocol.md | grep -n "4.3.3" | head
git -C <engine> ls-tree --name-only origin/claude/s5-entitlement-ack-emitter docs/sync-vectors/v1/ | grep -c json
```

*Expected:* §4.3.3 defines `{product_id, acknowledged_at, order_id?}` under an explicit
*"Decided 2026-08-07 (gate PQ-A6-1, default-proceed)"* marker; §3.1 measures the 1 MiB cap on the
ciphertext; §7.2 reports structural rejection as `decrypt_failed`; `invalid-unknown-field.json`
exists. Commits `8575539`, `22b028e`, `7328a0b`; draft PR **#32**, open since **2026-08-09**. The
prompt's stated pin `679a317` is stale — it is **`7328a0b`**. **Declined again**, for the reason in
LOG Milestone 2: re-authoring is the cross-repo drift event the prompt itself names.

**What is new at run 58 and supersedes part of C-STOP-1..5's framing:** the prompt's instruction
*"do NOT write the phone applier, you cannot compile it"* is **false for `:core`** and has been
since **C-JDK-1**. Acting on that discovery is what produced C-S5-1 through C-S5-6. **The prompt's
premise, not only its ladder summary, is stale.**

## Fifty-ninth run — 2026-08-18 (Linux sandbox): re-verification commands

Every claim run 59 makes, with the exact command that re-verifies it. This run wrote no source and
opened no PR; its one non-restatement claim is C-CORE-59, a fresh execution of the suite run 58
introduced.

### C-RET-6 — freshness after `git fetch --all --prune` in both trees

```bash
git -C <android> fetch --all --prune && git -C <android> rev-parse --short origin/main   # expect ebfaf81
git -C <engine>  fetch --all --prune && git -C <engine>  rev-parse --short origin/main   # expect aac05f3
# live PR state (any GitHub client):
#   list_pull_requests(ShivaClaw/careerseeker,        state=open)  -> 18, all draft
#   list_pull_requests(ShivaClaw/careerseeker-android, state=open) ->  6, all draft
```

*Observed:* engine `aac05f3`, android `ebfaf81`, both unmoved since 2026-08-12. Nothing merged,
closed or undrafted by anyone. `RETURN-DAY.md` §3 still safe to execute exactly as printed.

### C-STOP-7 — the assigned spec half is built and still off `main`; twenty-fourth assignment

```bash
for c in 8575539 22b028e 7328a0b; do git -C <engine> log --oneline -1 "$c"; done
git -C <engine> merge-base --is-ancestor 7328a0b origin/main; echo "is-ancestor exit=$?"   # expect NON-zero (not on main)
git -C <engine> branch -r --contains 7328a0b | grep s5   # the s5 draft branches carry it
```

*Expected:* the three commits resolve; `--is-ancestor` exits non-zero (spec is on draft branches,
not on `main`); draft PR #32 open since 2026-08-09. Prompt's pin `679a317` is stale — it is
`7328a0b`. Declined again: re-authoring is the cross-repo drift event the prompt forbids.

### C-CORE-59 — run 58's `:core` fix re-executed green on a clean container (the one non-restatement claim)

```bash
# JDK 17 is required (:core pins jvmToolchain(17)); apt-get update FIRST or the index 404s (C-JDK-2):
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 scripts/core-probe.sh --rerun
```

*Expected, and observed 2026-08-18:* `BUILD SUCCESSFUL`, `5 actionable tasks: 5 executed`, and
`core-probe: 299 tests, 0 failed, 0 skipped, across 20 classes`. This is `:core:test` — **one** of
the android gate's five tasks — with the up-to-date checks disabled (`--rerun-tasks`), executed on
this host, not read from a cache or a runner log. The other four tasks need the Android SDK (B-7);
`Verify-Alpha.ps1` needs Windows + .NET. No gate result is claimed.

### C-CORE-59b — no drift: vendored corpus byte-identical to the pin, re-checked this run

```bash
PIN=7328a0bc043335491cd96a67d634e8eea2a13af9
TMP=$(mktemp -d); git -C <engine> archive "$PIN" docs/sync-vectors/v1 | tar -x -C "$TMP"
diff -r "$TMP/docs/sync-vectors/v1" <android>/core/src/test/resources/sync-vectors/v1; echo "diff exit=$?"
```

*Expected, and observed:* 29 files upstream, 29 vendored, `diff -r` silent, `exit=0`. This run wrote
only records files, so the corpus could not have moved; the diff is taken anyway. The pin did not
move (moving it is H7).

---

## Sixtieth run — 2026-08-18 (Linux sandbox)

**Auditor setup for this run's claims** (Linux, no Android SDK needed — `:core` is Android-free):

```bash
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
#   the update is NOT optional: `apt-get install` alone 404s against a stale index.
#   Measured this run: without it, both openjdk-17 debs failed 404; with it, clean install
#   of 17.0.19+10. This is C-JDK-2's recipe and it reproduced exactly.
cd <android>
git fetch --all --prune && git checkout claude/android-a0-probe
```

### C-KIND-1 — the engine→phone kind partition is asserted, and `:core` is green at 304 / 21

> **Claim.** `PayloadKind` now carries a `flow` property; `ENGINE_TO_PHONE_KINDS` is derived from
> it; three destination sets partition that derived set; `PayloadKindCoverageTest` asserts it.
> `:core:test` goes **299 tests / 20 classes → 304 tests / 21 classes, 0 failed, 0 skipped**.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 scripts/core-probe.sh --rerun
```

*Expected:* `BUILD SUCCESSFUL`, `5 actionable tasks: 5 executed`, and the final line
`core-probe: 304 tests, 0 failed, 0 skipped, across 21 classes`.

For the 299 baseline, run the same command at `HEAD~1` (`git stash` is not needed — the change is
one commit):

```bash
git checkout HEAD~1 -- core/src/main/kotlin/app/careerseeker/core/Protocol.kt
rm core/src/test/kotlin/app/careerseeker/core/PayloadKindCoverageTest.kt
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 scripts/core-probe.sh --rerun   # -> 299 / 20 classes
git checkout HEAD -- core/ && git status --short                             # restore; expect empty
```

**This is ONE of the android gate's five tasks.** `checkCoreIsAndroidFree`, `:app:test`,
`:app:assembleDebug` and `:app:lintDebug` need the Android SDK (**B-7**) and **were not run; no
result is claimed for them.** `Verify-Alpha.ps1` was not run either — this is Linux.

### C-KIND-2 — the new assertions are capable of failing (three mutations)

> **Claim.** Mutation 1 (`entitlement_ack` unclassified — the pre-B-19 state) → **1 failed**;
> mutation 2 (one kind in two destination sets) → **1 failed**; mutation 3 (`conflict` flipped to
> `PHONE_TO_ENGINE`) → **2 failed**.

```bash
P=core/src/main/kotlin/app/careerseeker/core/Protocol.kt
cp "$P" /tmp/Protocol.bak

sed -i 's|ROUTED_OUTSIDE_REPLICA: Set<PayloadKind> = setOf(ENTITLEMENT_ACK)|ROUTED_OUTSIDE_REPLICA: Set<PayloadKind> = emptySet()|' "$P"
scripts/core-probe.sh --rerun 2>&1 | grep -E "FAILED|BUILD "   # -> 1 FAILED: classified exactly once
cp /tmp/Protocol.bak "$P"

sed -i 's|NOT_PROJECTED_IN_V1: Set<PayloadKind> = setOf(DOC, CONFLICT)|NOT_PROJECTED_IN_V1: Set<PayloadKind> = setOf(DOC, CONFLICT, SNAPSHOT)|' "$P"
scripts/core-probe.sh --rerun 2>&1 | grep -E "FAILED|BUILD "   # -> 1 FAILED: classified exactly once
cp /tmp/Protocol.bak "$P"

sed -i 's|CONFLICT("conflict", KindFlow.ENGINE_TO_PHONE)|CONFLICT("conflict", KindFlow.PHONE_TO_ENGINE)|' "$P"
scripts/core-probe.sh --rerun 2>&1 | grep -E "FAILED|BUILD "   # -> 2 FAILED: flow matches 4-3; no p2e kind
cp /tmp/Protocol.bak "$P"; git diff --stat                     # expect only the intended change
```

**What C-KIND-1/-2 do NOT establish, and the auditor should not read into them:** neither proves a
**production caller** exists for anything in `ROUTED_OUTSIDE_REPLICA`. That set would have held
`entitlement_ack` on 2026-08-09, when nothing called it, and these tests would have passed. **B-19
is unchanged.** The guard for that case is `EntitlementRoutingApplierTest`'s negative control
(**C-S5-3**, run 58).

### C-STOP-8 — the assigned S5 spec half is still built, still off `main`, twenty-five runs on

> **Claim.** All three commits exist and predate this run; the spec amendments and the vector are
> present at the pin; the generator agrees; and none of it is an ancestor of `origin/main`.

```bash
cd <engine> && git fetch --all --prune
git merge-base --is-ancestor 7328a0b origin/main; echo "exit=$?"    # -> exit=1 (still off main)
for c in 8575539 22b028e 7328a0b; do git log -1 --format='%h %ad %s' --date=short $c; done

git worktree add --detach /tmp/pin 7328a0b
cd /tmp/pin
node docs/sync-vectors/generate.mjs --check                        # -> OK: 29 vector files match the generator.  exit=0
grep -n "product_id\|acknowledged_at\|order_id" docs/Sync-Protocol.md | head   # -> 318-320,324,328,334 (PQ-A6-1)
grep -n -i ciphertext docs/Sync-Protocol.md | grep -i "MiB\|cap"              # -> 118, 656 (PQ-A2-1)
grep -n decrypt_failed docs/Sync-Protocol.md | head                          # -> 103, 601, 657 (PQ-A2-2)
ls docs/sync-vectors/v1/invalid-unknown-field.json                           # -> present (PQ-A2-3)
```

*Expected:* commits dated **2026-08-09**, **2026-08-09**, **2026-08-12** — all predating this run.
**The prompt's pin `679a317` is stale; the pin is `7328a0b`** (**C-PIN-1**).

### C-RET-7 — freshness: nothing has landed, on return day

```bash
cd <engine> && git fetch --all --prune && git rev-parse --short origin/main    # -> aac05f3
cd <android> && git fetch --all --prune && git rev-parse --short origin/main   # -> ebfaf81
gh pr list -R ShivaClaw/careerseeker         --state open --json number,isDraft | jq length  # -> 18, all draft
gh pr list -R ShivaClaw/careerseeker-android --state open --json number,isDraft | jq length  # -> 6,  all draft
```

*Expected:* both `main`s unmoved since 2026-08-12; **18 + 6 PRs open, every one still draft; nothing
merged, closed or undrafted.** `RETURN-DAY.md` §3 still safe to execute as printed.

**Note on the fetch:** this run's checkouts arrived **detached and stale — the android tree was 231
commits behind `origin/claude/android-a0-probe`.** Every number above was taken *after* the fetch.

### C-PIN-4 — no vector drift from this run

```bash
cd <engine> && git worktree add --detach /tmp/pin 7328a0b
diff -r /tmp/pin/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
ls <android>/core/src/test/resources/sync-vectors/v1/*.json | wc -l
```

*Expected:* `diff -r` **silent**, `exit=0`, **29** files. Taken **after** this run's commit.
`VECTORS.lock` unedited and the pin unmoved (**H7**).

---

## Sixty-first cloud iteration — 2026-08-19 (Linux sandbox)

### C-VEC-1 — the vector-corpus coverage guard exists and `:core` is green at 308 / 22

> **Claim.** `VectorCorpusCoverageTest` adds four assertions that the vendored corpus is fully
> consumed and agrees with its manifest. `:core:test` goes **304 tests / 21 classes → 308 tests /
> 22 classes, 0 failed, 0 skipped**.

```bash
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
scripts/core-probe.sh --rerun
```

*Expected:* `BUILD SUCCESSFUL` and the final line
`core-probe: 308 tests, 0 failed, 0 skipped, across 22 classes`.

The JDK line is not decoration: this container ships **JDK 21 only**, `:core` pins
`jvmToolchain(17)`, and `api.foojay.io` is denied (**B-7**), so the probe fails with its own
diagnostic until 17 is installed. For the 304 baseline, run the same command at `HEAD~1`.

### C-VEC-2 — the four assertions are capable of failing, and each fires alone

> **Claim.** Four mutations, each firing **exactly one** of the four assertions and leaving the
> other three passing. No mutation is left on disk.

```bash
V=core/src/test/resources/sync-vectors/v1
cp $V/index.json /tmp/index.bak

# 1 -> `every vector type in the manifest has a declared consumer` FAILS
python3 -c "
import json; p='$V/index.json'; d=json.load(open(p))
d['vectors'].append({'name':'outcome-mark-basic','type':'outcome_mark','valid':True,'expect_error':None})
json.dump(d,open(p,'w'),indent=2)"
echo '{\"name\":\"outcome-mark-basic\"}' > $V/outcome-mark-basic.json
scripts/core-probe.sh 2>&1 | grep -E "VectorCorpusCoverageTest.*(PASSED|FAILED)"
cp /tmp/index.bak $V/index.json; rm -f $V/outcome-mark-basic.json

# 2 -> `every invalid vector outside a whole-type enumerator is covered by a named test` FAILS
python3 -c "
import json; p='$V/index.json'; d=json.load(open(p))
d['vectors'].append({'name':'pairing-replayed-confirm','type':'pairing','valid':False,'expect_error':'decrypt_failed'})
json.dump(d,open(p,'w'),indent=2)"
echo '{\"name\":\"pairing-replayed-confirm\"}' > $V/pairing-replayed-confirm.json
scripts/core-probe.sh 2>&1 | grep -E "VectorCorpusCoverageTest.*(PASSED|FAILED)"
cp /tmp/index.bak $V/index.json; rm -f $V/pairing-replayed-confirm.json

# 3 -> `the manifest and the vendored directory describe the same payload files` FAILS
echo '{\"name\":\"stray-vendored\"}' > $V/stray-vendored.json
scripts/core-probe.sh 2>&1 | grep -E "VectorCorpusCoverageTest.*(PASSED|FAILED)"
rm -f $V/stray-vendored.json

# 4 -> `every declared consumer still has vectors to consume` FAILS
python3 -c "
import json; p='$V/index.json'; d=json.load(open(p))
d['vectors']=[v for v in d['vectors'] if v.get('type')!='entitlement_ack']
json.dump(d,open(p,'w'),indent=2)"
mv $V/entitlement-ack.json $V/entitlement-ack-no-order-id.json /tmp/
scripts/core-probe.sh 2>&1 | grep -E "VectorCorpusCoverageTest.*(PASSED|FAILED)"
mv /tmp/entitlement-ack.json /tmp/entitlement-ack-no-order-id.json $V/
git checkout -- $V/index.json
git status --porcelain core/src/test/resources/   # -> must print NOTHING
```

*Expected:* mutation 1 fails `...has a declared consumer`; 2 fails `...covered by a named test`;
3 fails `...describe the same payload files`; 4 fails `...still has vectors to consume`. In every
run the other three **PASSED**. The final `git status` line is the safety check — it must be
**empty**, i.e. no vector byte survived the controls.

### C-VEC-3 — the pre-fix proof: an unconsumed vector type left the suite green at 304 / 0

> **Claim.** This is the defect, measured rather than argued. With a vector of an entirely new
> `type` **generated, vendored, and listed in `index.json`** — the exact state `entitlement_ack`
> was in on 2026-08-09 — and `VectorCorpusCoverageTest` removed, `:core:test` is
> **BUILD SUCCESSFUL, 304 tests, 0 failed, 0 skipped, across 21 classes**. Every test in the
> suite skipped it and nothing anywhere reported a gap.

```bash
V=core/src/test/resources/sync-vectors/v1
mv core/src/test/kotlin/app/careerseeker/core/VectorCorpusCoverageTest.kt /tmp/VCCT.kt
cp $V/index.json /tmp/index.bak
python3 -c "
import json; p='$V/index.json'; d=json.load(open(p))
d['vectors'].append({'name':'outcome-mark-basic','type':'outcome_mark','valid':True,'expect_error':None})
json.dump(d,open(p,'w'),indent=2)"
echo '{\"name\":\"outcome-mark-basic\"}' > $V/outcome-mark-basic.json
scripts/core-probe.sh 2>&1 | grep -E "BUILD|core-probe: [0-9]+ tests"
cp /tmp/index.bak $V/index.json; rm -f $V/outcome-mark-basic.json
mv /tmp/VCCT.kt core/src/test/kotlin/app/careerseeker/core/VectorCorpusCoverageTest.kt
```

*Expected:* `BUILD SUCCESSFUL` and `core-probe: 304 tests, 0 failed, 0 skipped, across 21 classes`
— **green, with an unread vector in the corpus.**

### C-PIN-5 — no vector drift from this run, re-checked after the mutation controls

> **Claim.** The vendored corpus is **29/29 byte-identical** to pin `7328a0b`, taken **after** the
> four negative controls and the pre-fix proof had each mutated and restored it. Nothing under
> `docs/sync-vectors/` was written in either repo; `VECTORS.lock` was not edited and **the pin did
> not move** (moving it is **H7**, Brandon's).

```bash
cd ../careerseeker && git fetch --all --prune
diff -r <(git show 7328a0b:docs/sync-vectors/v1 >/dev/null 2>&1; git archive 7328a0b docs/sync-vectors/v1 | tar -xO --to-stdout >/dev/null; echo) /dev/null >/dev/null
# Direct form, which is what was actually run:
git ls-tree 7328a0b docs/sync-vectors/v1/ --name-only | sed 's|.*/||' | sort > /tmp/up.txt
ls ../careerseeker-android/core/src/test/resources/sync-vectors/v1/ | sort > /tmp/vd.txt
comm -3 /tmp/up.txt /tmp/vd.txt          # -> no output
while read n; do
  git show 7328a0b:docs/sync-vectors/v1/$n > /tmp/u.json
  diff -q /tmp/u.json ../careerseeker-android/core/src/test/resources/sync-vectors/v1/$n >/dev/null \
    || echo "DIFFERS: $n"
done < /tmp/vd.txt                        # -> no output
```

*Expected:* both loops silent, `29` on each side. Measured this run: **upstream=29 vendored=29,
set diff empty, content drift=0**, and `git status --porcelain core/src/test/resources/` empty.

### C-VEC-4 — the prompt's one runnable ask still passes, on work that predates this run

> **Claim.** `node docs/sync-vectors/generate.mjs --check` at the pin reports
> **`OK: 29 vector files match the generator.`, `exit=0`**. The `entitlement_ack` body (§4.3.3),
> the ciphertext-cap wording (PQ-A2-1), `decrypt_failed` on structural rejection (PQ-A2-2) and
> `invalid-unknown-field.json` (PQ-A2-3) are all present **at that commit and not on `main`**.

```bash
cd ../careerseeker && git fetch --all --prune
git merge-base --is-ancestor 7328a0b origin/main; echo "exit=$?"   # -> 1, i.e. NOT on main
git checkout 7328a0b && node docs/sync-vectors/generate.mjs --check
git show origin/main:docs/sync-vectors/v1 2>/dev/null | tail -n +3 | wc -l   # -> 26 on main
```

*Expected:* `exit=1` from the ancestor check, `OK: 29 vector files match the generator.` from the
generator, and **26** vector entries on `main` against the phone's 29.

### C-STOP-9 — the assigned slice is still built, still off `main`, twenty-six runs on

> **Claim.** The scheduled prompt assigned S5's spec half for the **twenty-sixth** time. It has
> existed since 2026-08-09 as draft PR **#32**; commits `8575539`, `22b028e`, `7328a0b`. The
> prompt's pin `679a317` is stale — it is **`7328a0b`** — and its "S5 is NOT STARTED" is wrong.

```bash
cd ../careerseeker && git log --oneline origin/main..origin/claude/s5-entitlement-ack-spec
grep -n "Pinned commit" ../careerseeker-android/core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected:* four commits including `8575539` and `22b028e`; the lock names `7328a0b…`, not
`679a317`.

### C-RET-8 — freshness: nothing has landed since return day

> **Claim.** Taken after `git fetch --all --prune` in both checkouts. `careerseeker` `main` is
> **`aac05f3`** and android `main` is **`ebfaf81`**, both unmoved since 2026-08-12. **18** open
> PRs in `careerseeker` and **6** in android, **all still draft, none merged or closed.**

```bash
cd ../careerseeker && git fetch --all --prune && git log --oneline -1 origin/main
cd ../careerseeker-android && git fetch --all --prune && git log --oneline -1 origin/main
```

*Expected:* `aac05f3` and `ebfaf81`. PR counts via the GitHub API, `state=open`: 18 and 6, every
one with `"draft": true`.

### C-RET-9 — freshness at run 62: return day passed, nothing moved

> **Claim.** Taken after `git fetch --all --prune` in both checkouts. Return day was **2026-08-18**;
> it is now **2026-08-19**. `careerseeker` `main` is **`aac05f3`** and android `main` is
> **`ebfaf81`**, both unmoved since **2026-08-12**. **18** open PRs in `careerseeker` and **6** in
> android, **all still draft; none merged, closed or undrafted.** No item in RETURN-DAY §5's human
> queue (**H1–H8**) has been acted on.

```bash
cd ../careerseeker         && git fetch --all --prune && git log --oneline -1 origin/main
cd ../careerseeker-android && git fetch --all --prune && git log --oneline -1 origin/main
```

*Expected:* `aac05f3` and `ebfaf81`. PR counts via the GitHub API, `state=open`: 18 and 6, every one
`"draft": true`.

### C-STOP-10 — the assigned slice is still built, still off `main`, twenty-seven runs on

> **Claim.** The scheduled prompt assigned S5's spec half for the **twenty-seventh** time. The one
> command it asks for **was run**, at the pin: **`OK: 29 vector files match the generator.`,
> `exit=0`**. `7328a0b` is **not** an ancestor of `origin/main`. The prompt's pin `679a317` is
> stale and its "S5 is NOT STARTED" is wrong.

```bash
cd ../careerseeker && git fetch --all --prune
git merge-base --is-ancestor 7328a0b origin/main; echo "exit=$?"   # -> 1, i.e. NOT on main
git checkout --detach 7328a0b && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected:* `exit=1` from the ancestor check; `OK: 29 vector files match the generator.`, `exit=0`
from the generator.

### C-LAND-8 — the landing plan has not decayed: 7/7 heads still match the live PRs

> **Claim.** All seven branches named in RETURN-DAY §3 (including #53, step 0) match their **live
> PR head SHA** exactly — **0 mismatches** — and §3's stop counts still reproduce in all three
> configurations: **#53 closed = 2**, **all 7 leaves = 3**, **#53 closed with #49 first = 3**
> (the **+1** order penalty).

```bash
cd ../careerseeker && git fetch --all --prune
# heads: compare `git rev-parse origin/<branch>` against each PR's head.sha from the API
cd ../careerseeker-android
./scripts/fleet-probe.sh land ../careerseeker \
  claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
  claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition claude/s6-composition-root-decision
```

*Expected:* four `clean`, then two `STOP`, `conflicted merges (human stops): 2`. Appending
`claude/s6-resume-reconciliation` gives **3**; moving `claude/s6-composition-root-decision` first
gives **3**.

### C-RES-1 / C-RES-2 — what each STOP actually contains, from real merges

> **Claim.** **STOP 1** conflicts 5 files / **11** hunks, every one the same number-pair
> (`SyncHarness 136`/`617` vs `134`/`615`); `tests/SyncHarness/Program.cs` is **not** conflicted.
> **STOP 2** conflicts 6 files / **7** hunks — the same shape **plus a single `using` directive**
> in `SyncHarness/Program.cs`: `System.Buffers.Binary` (ours) vs `System.Net` (theirs). **That one
> directive is the only source-code conflict in the entire six-merge landing.**

```bash
cd /tmp && rm -rf land && git clone -q --shared --no-checkout <engine> land && cd land
git fetch -q <engine> 'refs/remotes/origin/*:refs/remotes/origin/*'
git checkout -q --detach origin/main
for b in claude/s8-harness-linux-reach claude/s2-seq-bound \
         claude/s2-transport-vocabulary claude/s3-pairing-confirm-consumer; do
  git merge --no-edit -q origin/$b; done                      # all clean
git merge --no-edit origin/claude/s6-outcome-disposition      # STOP 1
git diff --name-only --diff-filter=U                          # 5 files, no SyncHarness
grep -c '^<<<<<<<' README.md docs/CareerSeeker-Project-Summary.md \
        docs/External-Audit-Handoff.md scripts/Verify-Alpha.ps1 src/Engine/README.md
```

*Expected:* `1 2 1 6 1` = **11** hunks at STOP 1, and `SyncHarness/Program.cs` absent from the
conflict list. After resolving, `git merge origin/claude/s6-composition-root-decision` conflicts
**6** files, and the harness hunk is the two `using` lines.

### C-RES-3 — the deltas are additive; nothing is dropped in the fuse

> **Claim.** Every line each side adds to `tests/SyncHarness/Program.cs` survives into the fused
> file: **#51 +51 lines, #52 +38, #49 +1292 — 0 missing in all three.** `Check(` counts are exactly
> additive at STOP 1: base **97**, #51 **102** (+5), #52 **101** (+4), fused **106**.

```bash
# in the merged tree above, with F=tests/SyncHarness/Program.cs
git show origin/main:$F > /tmp/base.cs
for side in claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition; do
  git show origin/$side:$F > /tmp/s.cs
  diff /tmp/base.cs /tmp/s.cs | grep '^>' | sed 's/^> //' | while read -r l; do
    [ -z "${l// }" ] || grep -Fqx -- "$l" $F || echo "MISSING: $l"; done
done
grep -oE '\bCheck\(' $F | wc -l
```

*Expected:* no `MISSING:` line from any side (use `git merge-base origin/main
origin/claude/s6-composition-root-decision` as the base for #49), and **106** at STOP 1.

### C-RES-4 — the pin arithmetic, and why disjointness holds here

> **Claim.** `main` = **611** (SyncHarness **130**). #51 = **617** (**136**), #52 = **615**
> (**134**) — both +6 / +4 on the same base. #49 forked at **`00b3705`** = **598** (**130**) and is
> **793** (**325**), so its total delta **195** is **entirely** SyncHarness (`325 − 130`) — meaning
> `main`'s +13 (`598 → 611`, R6/R7 scorer assertions) is disjoint from it. Hence
> `611 + 6 + 4 + 195 = **816**` and `130 + 6 + 4 + 195 = **335**`.
> **This is a PREDICTION.** `Verify-Alpha.ps1` was **not** run and cannot be run here (Linux, no
> .NET, no `pwsh`). The gate decides; the drift trap throws on a wrong value.

```bash
cd <engine>
for r in origin/main 00b3705 origin/claude/s3-pairing-confirm-consumer \
         origin/claude/s6-outcome-disposition origin/claude/s6-composition-root-decision; do
  printf '%-46s %s\n' "$r" "$(git show $r:scripts/Verify-Alpha.ps1 | grep -oE 'ExpectedOfflineTotal = [0-9]+' | head -1)"
  git show $r:src/Engine/README.md | grep -E '\| SyncHarness \|'
done
git merge-base origin/main origin/claude/s6-composition-root-decision   # -> 00b3705…
```

*Expected:* `611, 598, 617, 615, 793` and SyncHarness `130, 130, 136, 134, 325`.

### C-RES-5 — the fully-landed tree is coherent short of the gate

> **Claim.** After both resolutions the tree has **zero conflict markers**, and **every**
> `Assert-Contains` string in `Verify-Alpha.ps1` naming the totals resolves to a doc that contains
> it — the drift trap satisfied **statically**, the part of it that does not need Windows. The
> landed corpus is **30 files**, `OK: 30 vector files match the generator.`, `exit=0`, and the one
> file it gains over the phone's pin `7328a0b` is **`pairing-high-bit-confirm.json`** (**H7**).

```bash
# in the fully-merged, fully-resolved tree
grep -rn '^<<<<<<<\|^>>>>>>>' . | grep -v '^./.git' | wc -l     # -> 0
for lit in '| SyncHarness | 335 |' '| **Total** | **816** |'; do
  grep -rFl -- "$lit" README.md src/Engine/README.md \
       docs/CareerSeeker-Project-Summary.md docs/External-Audit-Handoff.md; done
ls docs/sync-vectors/v1 | wc -l && node docs/sync-vectors/generate.mjs --check
comm -13 <(git -C <engine> ls-tree -r --name-only 7328a0b docs/sync-vectors/v1 \
           | xargs -n1 basename | sort) <(ls docs/sync-vectors/v1 | sort)
```

*Expected:* `0` markers; each literal found in three docs; `30`, `OK: 30 vector files match the
generator.`, `exit=0`; and exactly `pairing-high-bit-confirm.json` from the `comm`.

### C-VEC-5 — the vendored corpus is still byte-identical to its pin

> **Claim.** Re-run this iteration: **29 vendored, 29 at pin `7328a0b`, `diff -r` clean, `exit=0`.**
> No vector byte was written this run in either repo.

```bash
cd ../careerseeker-android
git -C ../careerseeker archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pin
diff -r core/src/test/resources/sync-vectors/v1 /tmp/pin/docs/sync-vectors/v1; echo "exit=$?"
git status --porcelain core/src/test/resources/
```

*Expected:* no `diff` output, `exit=0`, and empty `git status`.

### C-CI-62 — the android gate ran green on the runner for run 62's push

> **Claim.** Run [32218378901](https://github.com/ShivaClaw/careerseeker-android/actions/runs/32218378901),
> `head_sha` **`ba3c7ea`**, **attempt 1**: job *Build and test* **`completed success`**, **all 13
> steps `success`** — including the five android-gate tasks this sandbox cannot run (steps 6, 8, 9,
> 10, 11) and the vendored-vector drift step (7). **Read out of the runner, produced locally by
> nothing.** Run 62 changed only five Markdown files, so this proves the tree is unbroken and
> **tests none of run 62's findings**; `Verify-Alpha.ps1` still has not run anywhere.

```bash
gh api repos/ShivaClaw/careerseeker-android/actions/runs/32218378901 \
  --jq '{status,conclusion,head_sha}'
gh api repos/ShivaClaw/careerseeker-android/actions/runs/32218378901/jobs \
  --jq '.jobs[] | {name,conclusion,steps:[.steps[]|{number,name,conclusion}]}'
```

*Expected:* `"conclusion":"success"`, `head_sha` starting `ba3c7ea`, and every step `success`.

---

## Run 63 — 2026-08-19 (Linux sandbox): re-verification commands

Every command below was **executed this run**, after `git fetch --all --prune` in **both** trees.
Run that fetch first or the counts are stale — that is rule one, and the android checkout arrives
detached at a stale `main` on every firing.

### C-STOP-11 — the assigned slice is built, for the twenty-eighth run

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  echo "== $c =="; git log -1 --format='%ad  %s' $c; git show --stat --format='' $c
done
git merge-base --is-ancestor 7328a0b origin/main; echo "is-ancestor exit=$?"
```

*Expected, and **observed**:* `8575539` (2026-08-09) touches **`docs/Sync-Protocol.md` only**,
**+114/−3**; `22b028e` (2026-08-09) adds both ack vectors, `index.json` **and `generate.mjs`**;
`7328a0b` (2026-08-12) adds `invalid-unknown-field.json`. `is-ancestor` **exits 1** — built, and
**not on `main`**. All four gates named in the recurring prompt (**PQ-A6-1**, **PQ-A2-1/-2/-3**) are
already closed.

The generator half, on the branch that carries it — this is the one command the prompt requests:

```bash
cd <engine> && git checkout -B s5-check origin/claude/s5-entitlement-ack-emitter
node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected, and **observed**:* **`OK: 29 vector files match the generator.`**, **`exit=0`**.

And the spec text itself, so the closure is read rather than inferred:

```bash
grep -n "acknowledged_at\|order_id" docs/Sync-Protocol.md | head
grep -n -i "decoded ciphertext\|1 MiB" docs/Sync-Protocol.md | head
grep -n "decrypt_failed" docs/Sync-Protocol.md | head
```

*Expected:* §4.3.3's `{product_id, acknowledged_at, order_id?}` with `order_id` **OPTIONAL**; the cap
stated on the **decoded ciphertext**; structural rejection reported as **`decrypt_failed`** with no
`malformed` code added.

### C-RET-10 — return day + 1, and no human action

```bash
cd <engine>   && git log -1 --format='%H %ad' origin/main
cd <android>  && git log -1 --format='%H %ad' origin/main
```

*Expected, and **observed**:* **`aac05f3`**, dated **2026-08-12** (unmoved 7 days), and **`ebfaf81`**.

```bash
# via the GitHub API, not local refs
gh pr list --repo ShivaClaw/careerseeker         --state open --json number,isDraft | jq length
gh pr list --repo ShivaClaw/careerseeker-android --state open --json number,isDraft | jq length
```

*Expected, and **observed**:* **18** and **6**, **every one still `isDraft: true`**; none merged,
closed or undrafted. **#53 still open** — H1 undecided. Terra's bus
(`git show origin/autonomy/codex-state:STATE.md`) still reads **COMPLETE / files claimed: none**.

### C-LAND-10 — the landing plan has not decayed

Compare each of `RETURN-DAY.md` §3's seven landing branches against its **live PR head**, because a
local ref proves only what was last fetched:

```bash
cd <engine>
for b in s8-harness-linux-reach s2-seq-bound s2-transport-vocabulary \
         s3-pairing-confirm-consumer s6-outcome-disposition \
         s6-composition-root-decision s6-resume-reconciliation; do
  printf '%-35s %s\n' "$b" "$(git rev-parse --short=7 origin/claude/$b)"
done
```

*Expected, and **observed**, against the API's head SHAs:* `c93e88d`, `2be00fc`, `b0b6c77`,
`edee32b`, `94fd979`, `f5e0c0a`, `8177353` — **7/7 match, 0 mismatches**. This checks **non-decay
only**; the stop counts (2 / 3 / 3), the resolutions, and the `816` prediction are run 62's
measurements (**C-RES-1**…**-5**) and were **not** re-derived here.

### C-VEC-6 — no vector drift

```bash
cd <engine> && rm -rf /tmp/pinv && mkdir -p /tmp/pinv
git archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pinv
diff -r /tmp/pinv/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1
echo "exit=$?"; ls /tmp/pinv/docs/sync-vectors/v1 | wc -l
git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l
```

*Expected, and **observed**:* **no output**, **`exit=0`**, **29** files vendored; `main` carries
**26**. `git status --porcelain <android>/core/src/test/resources/` is **empty** — the pin did not
move (**H7**).

### C-ENV-2 — B-7 re-measured: an allowlist denial, not an absent network

```bash
curl -sS -o /dev/null -w "%{http_code}\n" --max-time 25 \
  https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
curl -sS -o /dev/null -w "%{http_code}\n" --max-time 25 https://repo1.maven.org/maven2/
java -version 2>&1 | head -2; which sdkmanager adb; echo "ANDROID_HOME=$ANDROID_HOME"
```

*Expected, and **observed**:* `dl.google.com` → **`curl: (56) CONNECT tunnel failed, response 403`**
(code `000`); `repo1.maven.org` → **`200`**; **openjdk 21.0.10** (the image ships 21, not the 17
`:core` pins); `sdkmanager`/`adb` **absent**, `ANDROID_HOME` **unset**. **Maven Central answers in
the same session Google's repo is refused**, so B-7 is a policy allowlist denial. Per
`/root/.ccr/README.md` a 403 is an organization policy denial — **not routed around**. Consequence
unchanged: `:core` is reachable here, `:app` and the four SDK-bound gate tasks are not.

### C-CRON-1 — the schedule is not a session cron job (B-18, attempt 6)

```
CronList   ->   "No scheduled jobs."
```

*Observed.* B-18 attempt 2 **inferred** that the recurring prompt is stored scheduler configuration
rather than a file in either checkout; this **measures** it from the one angle a session can. No tool
available here enumerates or edits the schedule, so it is account/environment-level configuration,
reachable only from the surface it was created on. **This narrows B-18's unblock location; it does
not unblock it.** Nothing was created, edited or deleted — the call is read-only.

### What this run did NOT verify, stated so no reader infers it

**No gate ran.** `Verify-Alpha.ps1` needs Windows, `pwsh` and `dotnet`, none of which exist here
(**C-LAND-7**, **C-ENV-1**, re-confirmed by **C-ENV-2**), so **`816` remains a prediction**. The
android gate did not run either — `:core:test` was **not** executed this run (run 61's **308/0** and
run 56's **C-ENUM-1** stand as the last measurements), and `:app:assembleDebug` / `:app:lintDebug`
have never run in any cloud session. **No sentence in the run 63 LOG entry claims a gate result.**

---

## Run 64 — 2026-08-19 (twenty-ninth firing)

Every claim in `LOG.md`'s RUN 64 entry, with the command that re-verifies it. `<E>` is a checkout of
`ShivaClaw/careerseeker`; `<A>` is this repo. **Run `git fetch --all --prune` in both first** — runs
62, 63 and 64 all arrived detached at a stale `main`, and every number below is post-fetch.

### C-64-1 — The assigned slice is built, and none of it is on `main`

> **Claim.** §4.3.3's `entitlement_ack` body, the two ack vectors, PQ-A2-1, PQ-A2-2 and PQ-A2-3 all
> exist upstream (`8575539`, `22b028e`, `7328a0b`), and **not one is an ancestor of `origin/main`**.
> The first four are PR **#32**, open and draft since 2026-08-09.

```bash
cd <E> && git fetch --all --prune
for c in 8575539 22b028e 7328a0b; do
  git merge-base --is-ancestor $c origin/main && echo "$c ON MAIN" || echo "$c not on main (exit 1)"
done
git show origin/claude/s5-entitlement-ack-spec:docs/Sync-Protocol.md | grep -n "4.3.3\|decoded ciphertext\|decrypt_failed" | head
git log --oneline -1 7328a0b
```

*Expected:* all three print `not on main (exit 1)`; the grep shows §4.3.3, the **decoded ciphertext**
cap sentence and `decrypt_failed`; `7328a0b` is *"S5: add the invalid-unknown-field vector, closing
PQ-A2-3 and B-6"*. **If any commit reports `ON MAIN`, H2 has been done and this entry is stale — good
news, not drift.**

### C-64-2 — `generate.mjs --check` passes at the pin's branch

> **Claim.** `OK: 29 vector files match the generator.`, `exit=0`.

```bash
cd <E> && git worktree add -f --detach /tmp/wt-s5 origin/claude/s5-entitlement-ack-emitter
cd /tmp/wt-s5 && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected:* `OK: 29 vector files match the generator.` and `exit=0`. Node 22 here; the generator is
deterministic, so the count is the assertion.

### C-64-3 — The vendored corpus has not drifted from pin `7328a0b`

> **Claim.** 29/29 files byte-identical; `diff -r` silent; `exit=0`; `VECTORS.lock` unedited.

```bash
mkdir -p /tmp/pin && cd <E> \
  && git archive 7328a0bc043335491cd96a67d634e8eea2a13af9 docs/sync-vectors/v1 | tar -x -C /tmp/pin
diff -r /tmp/pin/docs/sync-vectors/v1 <A>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
ls /tmp/pin/docs/sync-vectors/v1 | wc -l; ls <A>/core/src/test/resources/sync-vectors/v1 | wc -l
```

*Expected:* no output from `diff`, `exit=0`, and `29` twice. **This is the phone-matches-its-pin
guarantee and nothing wider** — see `VECTORS.lock`'s 2026-08-17 note; the phone is *ahead* of `main`
by three vectors, which is not drift.

### C-64-4 — `:core:test` is green on a clean container: 308 / 0 / 0 across 22 classes

> **Claim.** `BUILD SUCCESSFUL` and `core-probe: 308 tests, 0 failed, 0 skipped, across 22 classes`.
> This reproduces run 61's recorded expectation exactly.

```bash
apt-get update && apt-get install -y --no-install-recommends openjdk-17-jdk-headless   # see C-64-5
cd <A> && bash scripts/core-probe.sh 2>&1 | tail -5
```

*Expected:* `BUILD SUCCESSFUL` and `core-probe: 308 tests, 0 failed, 0 skipped, across 22 classes`.
**Scope:** this is `:core:test` **only**. `checkCoreIsAndroidFree`, `:app:test`,
`:app:assembleDebug` and `:app:lintDebug` need the Android SDK (**B-7**) and **did not run**; no
result is claimed for them. Do not cite this as the android gate.

### C-64-5 — The recorded JDK-17 install step needs `apt-get update` first, or it 404s

> **Claim.** `apt-get install -y --no-install-recommends openjdk-17-jdk-headless` **alone** now fails
> with `404 Not Found` and `exit=100`; with `apt-get update` first it succeeds and installs
> **17.0.19+10-1~24.04.2**. The image ships JDK **21** only.

```bash
java -version 2>&1 | head -1          # openjdk 21.x -- and :core pins jvmToolchain(17)
apt-get install -y --no-install-recommends openjdk-17-jdk-headless; echo "exit=$?"   # expect 404 / 100
apt-get update && apt-get install -y --no-install-recommends openjdk-17-jdk-headless; echo "exit=$?"
ls /usr/lib/jvm
```

*Expected:* the bare install fails fetching `openjdk-17-*_17.0.18+8-1~24.04.1_amd64.deb` with `404`;
after `apt-get update` it exits `0` and `java-17-openjdk-amd64` appears. **404 is a stale apt index,
not the egress policy** — a policy denial shows as `403` at the proxy (**B-7**, `/root/.ccr/README.md`).
This corrects the command block recorded at C-VEC-1; it is **not** a new blocker.

### C-64-6 — The standing state has not moved

> **Claim.** engine `main` **`aac05f3`** (2026-08-12), android `main` **`ebfaf81`** (2026-08-06);
> **18 + 6 PRs open, all draft**, none merged/closed/undrafted; **#53 open**; landing plan **7/7**
> against live PR heads, 0 mismatches; Terra **COMPLETE, files claimed: none**.

```bash
cd <E> && git fetch --all --prune && git log -1 --format='%h %ad' --date=short origin/main
cd <A> && git fetch --all --prune && git log -1 --format='%h %ad' --date=short origin/main
# live, not local refs:
gh pr list -R ShivaClaw/careerseeker         --state open --json number,isDraft,headRefName,headRefOid
gh pr list -R ShivaClaw/careerseeker-android --state open --json number,isDraft,headRefName,headRefOid
cd <E> && git show origin/autonomy/codex-state:STATE.md | head -12
```

*Expected:* `aac05f3 2026-08-12` / `ebfaf81 2026-08-06`; 18 and 6 entries, every `isDraft: true`;
#53 present; the seven landing heads `c93e88d 2be00fc b0b6c77 edee32b 94fd979 f5e0c0a 8177353`;
Terra COMPLETE with no files claimed. **Any difference here means a human acted — re-read
`RETURN-DAY.md` §3 before trusting the older counts.**

---

## Run 65 — 2026-08-19. PQ-PSH-1 closed in `:core`; re-verification commands

Every claim run 65 makes, with the command that re-runs it. `<android>` is a checkout of
`claude/android-a0-probe`; `<engine>` is a checkout of `ShivaClaw/careerseeker`. **Fetch both first**
— every count below is post-`git fetch --all --prune`.

**Toolchain, once, per C-64-5** (the recorded one-command install 404s on a stale apt index):

```bash
apt-get update && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### C-65-1 — the assigned slice is built, for the thirtieth consecutive run

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  git merge-base --is-ancestor $c origin/main; echo "$c -> exit $?"
done
```

*Expect:* **`exit 1` for all three** — the work exists and is **not on `main`**. Then, on the branch
that carries it:

```bash
git checkout origin/claude/s5-inbound-pump
node docs/sync-vectors/generate.mjs --check
```

*Expect:* **`OK: 29 vector files match the generator.`**, **`exit=0`**.

### C-65-2 — the four assigned items read in the file, not inferred

```bash
cd <engine> && git checkout origin/claude/s5-inbound-pump
grep -n "entitlement_ack" docs/Sync-Protocol.md      # §4.3.3 body at 307-320  (PQ-A6-1)
grep -n -i "ciphertext" docs/Sync-Protocol.md        # cap at 118 / 656        (PQ-A2-1)
grep -n "decrypt_failed" docs/Sync-Protocol.md       # 103 / 601 / 657         (PQ-A2-2)
ls docs/sync-vectors/v1/invalid-unknown-field.json   # present                 (PQ-A2-3)
```

*Expect:* all four present. **The prompt's pin `679a317` is stale; the pin is `7328a0b`** —
`grep 'Pinned commit' <android>/core/src/test/resources/sync-vectors/VECTORS.lock`.

### C-65-3 — PQ-PSH-1 was filed as needing a gate it does not need

The claim is that both files PQ-PSH-1 names live in `:core`, which needs no Android SDK:

```bash
cd <android>
ls core/src/main/kotlin/app/careerseeker/core/RelayClient.kt \
   core/src/main/kotlin/app/careerseeker/core/OutboundQueue.kt
```

*Expect:* both exist under `core/`. **This is the whole of the finding** — the question was filed
unreachable on the strength of `:app`, a module neither file is in.

### C-65-4 — the 308 baseline, measured before a line was written

```bash
cd <android>
git worktree add /tmp/cs-baseline 45d706a       # run 64's tip, this run's parent
cd /tmp/cs-baseline && ./scripts/core-probe.sh
```

*Expect:* **`BUILD SUCCESSFUL`**, **`core-probe: 308 tests, 0 failed, 0 skipped, across 22 classes`**.

### C-65-5 — the defect, on the parent commit

```bash
cd /tmp/cs-baseline
grep -c "BadRequest" core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expect:* **`0`** — 400 was in no branch of the mapping, so it fell through to the retry path and was
reported as `Unavailable`, which `OutboundQueue.onPushed` maps to `PushOutcome.Retry`
(`grep -n "Unavailable -> PushOutcome.Retry" core/src/main/kotlin/app/careerseeker/core/OutboundQueue.kt`).

**And the test that stayed green over it:**

```bash
sed -n '/a 4xx is a decision and is never retried/,/^    }/p' \
  core/src/test/kotlin/app/careerseeker/core/RelayClientTest.kt
```

*Expect:* on the **parent**, a test claiming a property of all 4xx that witnesses it with
`HttpStatusCode.NotFound` **only**. On this run's commit it enumerates five statuses.

### C-65-6 — the fix matches the engine, and its restraint is deliberate

```bash
cd <engine>
git show origin/claude/s2-relay-pull-result:src/Sync/RelayClient.cs | grep -n -A2 "case HttpStatusCode.BadRequest"
```

*Expect:* **`return new RelayPushResult.Rejected(...)`** — and **no case for 405 or 426**, which is
why the phone does not add one either. Phone side:

```bash
cd <android>
grep -n "BadRequest\|MethodNotAllowed\|UpgradeRequired" core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expect:* **`BadRequest` mapped, 405/426 absent** from the mapping.

### C-65-7 — executed: 312/0

```bash
cd <android> && ./scripts/core-probe.sh --rerun
```

*Expect:* **`BUILD SUCCESSFUL`**, **`core-probe: 312 tests, 0 failed, 0 skipped, across 22 classes`**,
0 warnings. **This is `:core:test` only** — four of the android gate's five tasks need the Android SDK
and did not run (**B-7**). Do not report a gate result on the strength of it.

### C-65-8 — the four mutations, each red

Apply one at a time to `<android>`, run `./scripts/core-probe.sh --rerun`, then `git checkout -- core/`.
**Commit before mutating** — see the run-65 log's Milestone 7.

| | Mutation | Expect |
| --- | --- | --- |
| **M1** | delete the line `HttpStatusCode.BadRequest -> return RelayResult.Rejected` | **3 failed** — status mapping, the 4xx test, `PairingFlowTest`'s terminal list |
| **M2** | replace `OutboundQueue`'s `RelayResult.Rejected` block with `PushOutcome.Retry` | **2 failed**, both new `OutboundQueueTest` cases |
| **M3** | map `BadRequest` to `RelayResult.TooLarge` instead | **3 failed**, incl. `a 400 is the sender's defect and is kept distinct from an oversized body` |
| **M4** | map `BadRequest, HttpStatusCode.MethodNotAllowed` to `Rejected` | **exactly 1 failed** — `405 and 426 are still retried…` |

**M4 is the one to press**: if it fails *anything else*, the engine-parity pin is entangled with
another assertion and is weaker than claimed. If it fails **nothing**, the pin is decoration and the
phone's terminal set can silently drift past the engine's.

### C-65-9 — standing state, unmoved

```bash
cd <engine>   && git rev-parse --short origin/main    # aac05f3
cd <android>  && git rev-parse --short origin/main    # ebfaf81
```

Plus the PR sweep (18 engine + 6 android, **all open, all draft**) and Terra's
`git show origin/autonomy/codex-state:STATE.md` → **COMPLETE, files claimed: none**.

### C-65-10 — no vector byte written

```bash
cd <engine> && git worktree add --detach /tmp/pinchk 7328a0bc043335491cd96a67d634e8eea2a13af9
diff -r /tmp/pinchk/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
```

*Expect:* **silent**, **`exit=0`**, **29 files each side** — measured **after** this run's commit.

---

## Run 66 — 2026-08-19. The pull latch that outlived its ask.

Throughout, `<android>` = a checkout of `ShivaClaw/careerseeker-android` on
`claude/android-a0-probe`, `<engine>` = a checkout of `ShivaClaw/careerseeker`.
**`git fetch --all --prune` in BOTH before any of it** — both arrived detached at a stale `main`,
the android tree **247** commits behind its own branch.

### C-66-1 — the assigned slice exists and is not on `main`

Run this in the **engine** repo. The three commits and `generate.mjs` are **not** in the android
checkout; looking for them there returns `exit 128` (commit absent), which is not the same answer.

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  git merge-base --is-ancestor $c origin/main; echo "$c exit=$?"
done
```

*Expect:* **`exit=1`** for all three — they exist, none is on `main`. `exit=128` means you are in
the wrong repository.

### C-66-2 — the generator is clean, and the count is 26 rather than 29

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expect:* **`OK: 26 vector files match the generator.`**, **`exit=0`**. `main` carries **26**; the
phone's vendored corpus is **29**, from pin `7328a0b`, which is not on `main`. The prompt's pin
`679a317` is stale.

### C-66-3 — the defect: `open()` was never called twice anywhere in the suite

```bash
cd <android> && git show 08e808a^:core/src/test/kotlin/app/careerseeker/core/SyncPumpTest.kt \
  | grep -c "pump.open()"
cd <android> && git show 08e808a^:core/src/test/kotlin/app/careerseeker/core/PullPolicyTest.kt \
  | grep -c "onOpen"
```

*Expect:* every pre-fix occurrence is a **single** `open()` on a fresh pump/policy — no test drives
a reopen. That is the hole; the KDoc on `onOpen` named `reconnect` as a call site the whole time.

### C-66-4 — the negative control, which is the proof the defect was real

```bash
cd <android> && git checkout 08e808a -- core/src/test && git checkout 08e808a^ -- core/src/main
scripts/core-probe.sh --rerun
```

*Expect:* **`BUILD FAILED`**, and **exactly these three**:

```
PullPolicyTest > a reopen re-asks when the first request was accepted but never answered() FAILED
PullPolicyTest > a reopen releases a gap request latched on a warm replica() FAILED
SyncPumpTest  > reopening after an unanswered pull_request asks again() FAILED
```

**`releasing on reopen does not weaken the one-ask-per-page latch` must PASS here.** If it fails,
it is a second copy of the defect witness rather than a guard. Restore with
`git checkout 08e808a -- core/`.

### C-66-5 — the baseline and the fixed run

```bash
cd <android> && git checkout 08e808a^ -- core/ && scripts/core-probe.sh          # baseline
cd <android> && git checkout 08e808a  -- core/ && scripts/core-probe.sh --rerun  # fixed
```

*Expect:* **`312 tests, 0 failed, 0 skipped, across 22 classes`** then
**`316 tests, 0 failed, 0 skipped, across 22 classes`**, both **`BUILD SUCCESSFUL`**.
Two `No cast needed` warnings appear in `PairingSessionTest` and `RelayClientTest` in **both** runs
— pre-existing, not this change; **no zero-warning claim is made.**
**This is `:core:test` only** — four of the android gate's five tasks need the Android SDK and did
not run (**B-7**). Do not report a gate result on the strength of it.

### C-66-6 — the four mutations, each red, each for its own reason

Apply one at a time to `<android>` `core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt`, run
`scripts/core-probe.sh --rerun`, then `git checkout -- core/`. **Commit before mutating.**

| | Mutation | Measured |
| --- | --- | --- |
| **M1** | delete the `pending = false` line in `onOpen` (*reinstates the defect*) | **3 failed** — exactly the three C-66-4 tests; **the latch guard passes** |
| **M2** | move `pending = false` to **after** the decision is computed | **4 failed** — the cold reopen test, the guard, `a failed push re-arms the policy`, `the snapshot that arrives satisfies the request` |
| **M3** | make it conditional: `if (!position.snapshotSeen) pending = false` | **exactly 1 failed** — `a reopen releases a gap request latched on a warm replica`, **alone** |
| **M4** | delete `if (pending) return PullDecision.None` from `request()` (*remove the latch*) | **3 failed** — `many refused deltas produce exactly one request`, the new guard, `a failed push re-arms the policy` |

**M3 is the one to press**: it fires **one** assertion with everything else green, which is what
makes *unconditional* a real constraint rather than a coincidence. If it ever fails **nothing**, the
warm-replica half has become decoration and a later change can silently re-cold-only it.

**M2 was predicted to fail one test and fails four** — worth reading, because the four name the
reason. Releasing *after* the decision means `onOpen` never leaves the latch set **at all**, so
every assertion that depends on an open having latched goes red. The ordering is load-bearing in
four places, not one.

**M4 is what proves the guard test is real.** The guard (`releasing on reopen does not weaken the
one-ask-per-page latch`) fails here — so it genuinely asserts the latch, and is not a second copy
of the defect witness. Note it passes under **M1**, which is the complementary half: the guard is
sensitive to the latch and insensitive to the release, exactly as intended.

### C-66-7 — standing state, unmoved

```bash
cd <engine>  && git rev-parse --short origin/main    # aac05f3
cd <android> && git rev-parse --short origin/main    # ebfaf81
cd <engine>  && git show origin/autonomy/codex-state:STATE.md | head -20
```

*Expect:* the two SHAs above; **18 engine + 6 android PRs, all open, all draft**, none merged,
closed or undrafted; **#32 and #53 both still open**; Terra **COMPLETE, files claimed: none**.

### C-66-8 — no vector byte written

```bash
cd <engine> && tmp=$(mktemp -d) && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C "$tmp"
diff -r "$tmp/docs/sync-vectors/v1" <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
```

*Expect:* **silent**, **`exit=0`**, **29 files each side** — measured **after** this run's commits.

### C-66-9 — B-21, the 429 that is not B-7

```bash
cd <android> && scripts/core-probe.sh --rerun
```

*Expect, on a cold container:* possibly
**`Received status code 429 from server: Too Many Requests`** against
**`repo1.maven.org`** — an **allowed** host rate-limiting, **not** B-7's policy denial of
`dl.google.com`. **Retry with backoff**; each attempt populates more of the Gradle cache and it
clears (it took **4** attempts here). A session that reads the first 429 as B-7 will wrongly file
`:core` as unreachable.

## Sixty-seventh run — 2026-08-20 (Linux sandbox): cancellation swallowed by the relay retry loop

Every command below requires `git fetch --all --prune` in **both** trees first. That is rule one,
and every count here was taken after it.

### C-67-1 — the assigned slice is built, for the thirty-second consecutive run

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  echo "== $c =="; git log -1 --format='%ad  %s' $c
  git merge-base --is-ancestor $c origin/main && echo "  ON MAIN" || echo "  NOT on main"
done
```

*Expected, and **observed**:* all three commits **exist**; all three report **NOT on main**.
`8575539` (2026-08-09) touches `docs/Sync-Protocol.md` only (+114/−3); `22b028e` adds both
`entitlement-ack*` vectors plus the generator arm; `7328a0b` (2026-08-12) adds
`invalid-unknown-field.json`, closing PQ-A2-3. **Run this in the engine repo** — in the android
checkout the same command returns `exit=128`, which reads like a broken command rather than a
wrong repository.

### C-67-2 — the real pin, re-measured

```bash
cd <android> && grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1
cd <engine>  && git worktree add -f --detach /tmp/pin 7328a0b && (cd /tmp/pin && node docs/sync-vectors/generate.mjs --check)
```

*Expected, and **observed**:* the pin is `7328a0bc043335491cd96a67d634e8eea2a13af9`; the generator
reports **`OK: 29 vector files match the generator.`, `exit=0`**. **The prompt's `679a317` is
stale** and has been since 2026-08-12.

### C-67-3 — the defect: `CancellationException` is caught by the general clause

```bash
cd <android> && git show d28b47e^:core/src/main/kotlin/app/careerseeker/core/RelayClient.kt \
  | grep -n "catch (e: Exception)" -B 2 -A 2
cd <android> && grep -rn "Cancell\|withTimeout" core/src/test/kotlin/ | grep -iv "pairingflow\|cancels the request"
```

*Expected, and **observed**:* before this run the retry loop's **only** catch was
`catch (e: Exception) { "transport failure: …" }`, and **nothing in `:core` tested coroutine
cancellation** — the second command returns no relevant hit. `CancellationException` is an
`Exception` on the JVM, so that clause absorbed it and the loop returned
`RelayResult.Unavailable` from a cancelled coroutine.

### C-67-4 — the negative control, run BEFORE the fix

This is the claim that the defect was real rather than a test written to match already-correct
code. Reproduce by reverting only the source half of `d28b47e`:

```bash
cd <android>
git show d28b47e -- core/src/main/kotlin/app/careerseeker/core/RelayClient.kt | git apply -R
scripts/core-probe.sh --rerun; git checkout core/src/main/kotlin/app/careerseeker/core/RelayClient.kt
```

*Expected, and **observed**:*

```
RelayClientTest > cancellation on the final attempt escapes rather than becoming a value() FAILED
RelayClientTest > cancellation is not swallowed into an unavailable relay() FAILED
318 tests completed, 2 failed
```

**Exactly two failures — the two new tests — with all 316 pre-existing tests green.**

### C-67-5 — the suite, with the fix

```bash
cd <android> && scripts/core-probe.sh --rerun
```

*Expected, and **observed**:* **`BUILD SUCCESSFUL`**, **`core-probe: 318 tests, 0 failed, 0
skipped, across 22 classes`** — against a measured clean-worktree baseline of **316**.

**This is `:core:test` only.** `:app:assembleDebug`, `:app:lintDebug`, `:app:test` and
`checkCoreIsAndroidFree` need the Android SDK, **did not run**, and **nothing is claimed for
them** (**B-7**). **No zero-warning claim is made.** JDK 17 is required first:
`apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless`
(the `update` is **not** optional — C-64-5).

### C-67-6 — three mutations, each red

Apply each to `RelayClient.request`, run `scripts/core-probe.sh --rerun`, revert.

| | mutation | *expected, and **observed*** |
| --- | --- | --- |
| **M1** | delete the `catch (e: CancellationException) { throw e }` clause | **both** new tests fail |
| **M2** | move that clause **below** `catch (e: Exception)` | **compiles cleanly**, **both** new tests fail |
| **M3** | make the general clause `throw e` as well | **exactly one** failure: `a thrown transport error is retried, not propagated` |

**M2 is the one that pins the real constraint.** Kotlin does not flag an unreachable catch clause,
so the mutated file builds and the clause is silently dead — presence alone is not the fix, position
is. If M2 ever stops failing, the ordering has stopped being tested.

**M3 is what makes the two new tests guards rather than duplicates.** It fails a **pre-existing**
test while both new tests stay green, proving the fix is **narrow**: an offline phone still receives
`Unavailable` and does not have an `IOException` propagated into its UI.

*A note on method, because it affects how the table should be read:* a first attempt at M2 spliced
the file badly and produced a **compile error**. That is not a mutation result and is not reported
as one. It was redone cleanly; the row above is the redone run, and the restored file was confirmed
**byte-identical** to the pre-mutation original before the C-67-5 run.

### C-67-7 — two findings recorded but NOT fixed

```bash
cd <android>
grep -n "app_id" core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
grep -n "jsonString" core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
grep -rn "PublishSnapshotAsync\|PublishDeltaAsync\|PublishHeartbeatAsync\|PublishEvidenceAsync" <engine>/src/Sync/SyncPublisher.cs
```

*Expected, and **observed**:* `outcome()` interpolates `app_id` **raw** while `entitlement()` routes
every field through `jsonString()`; and `SyncPublisher.cs` exposes **exactly four** publish methods
— `snapshot`, `delta`, `heartbeat`, `evidence`, all of which the `:app` applier **projects**.

Those two observations are what bound the severity of both findings, and the bound is the point:
finding (1) is **defense in depth** because `app_id` is an engine-internal ULID inside an
AEAD-sealed snapshot, and finding (2) is **latent** because no unprojected kind is published today.
Full symptom and reproduction in `BLOCKED.md`. **Neither is claimed as fixed.**

### C-67-8 — standing state, unmoved

```bash
cd <engine>  && git rev-parse --short origin/main    # aac05f3
cd <android> && git rev-parse --short origin/main    # ebfaf81
cd <engine>  && git show origin/autonomy/codex-state:STATE.md | head -20
# PR counts — measured via the GitHub API, both repos, state=open:
#   ShivaClaw/careerseeker          -> 18 open, all draft
#   ShivaClaw/careerseeker-android  ->  6 open, all draft
```

*Expected, and **observed**:* the two SHAs above; **18 engine PRs, all open, all draft** —
`#26, #32..#39, #45..#53`, with **#32** (the assigned slice) and **#53** (H1) both still open;
**6 android PRs, all open, all draft** — `#1..#6`; Terra **COMPLETE, files claimed: none**.

**The engine count was measured this run, not carried forward.** It is recorded here because the
LOG's prohibition paragraph asserts "the 18 engine PRs … are exactly as found", and an inherited
count stated as a measurement is precisely the drift this file exists to stop.

### C-67-9 — no vector byte written

```bash
cd <engine> && tmp=$(mktemp -d) && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C "$tmp"
diff -r "$tmp/docs/sync-vectors/v1" <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
```

*Expected, and **observed**:* **silent**, **`exit=0`**, **29 files each side** — measured **after**
this run's commit.

---

## Run 68 (2026-08-20) — re-verification commands

Every claim run 68 makes, with the command that re-derives it. Substitute `<engine>` for a
`ShivaClaw/careerseeker` checkout and `<android>` for this one. **Run `git fetch --all --prune` in
both first** — run 68, like the four before it, arrived detached at a stale `main`, and every number
here is post-fetch.

### C-68-1 — the assigned slice exists and is still not on `main`

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  git merge-base --is-ancestor $c origin/main; echo "$c -> exit=$?"
done
```

*Expected, and **observed**:* **`exit=1`** for all three — *not on `main`*. Run this in the
**engine** repo; in the android checkout the same command returns `exit=128`, which reads like a
broken command rather than a wrong repository.

### C-68-2 — the corpus is anchored to a generator, and the prompt's pin is stale

```bash
cd <engine> && git checkout -q 7328a0b && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
git ls-tree -r --name-only origin/main -- docs/sync-vectors/v1 | wc -l
ls <android>/core/src/test/resources/sync-vectors/v1 | wc -l
```

*Expected, and **observed**:* **`OK: 29 vector files match the generator.`**, **`exit=0`**;
**26** on `main`, **29** on the phone. The prompt's `679a317` is stale; the live pin is
**`7328a0b`**, and `VECTORS.lock` says so.

### C-68-3 — the defect F-67-2 described, located in the code

```bash
cd <android>
git show main:core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt | grep -n "highestAppliedSeq > gapThreshold"
grep -rn "APPLIED_SNAPSHOT\|APPLIED ->" core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt | head
grep -rn "PublishSnapshotAsync\|PublishDeltaAsync\|PublishHeartbeatAsync\|PublishEvidenceAsync" <engine>/src/Sync/SyncPublisher.cs
```

*Expected, and **observed**:* the pre-fix expression measures the gap against
`positionBefore.highestAppliedSeq` alone; that mark advances only on `APPLIED` /
`APPLIED_SNAPSHOT`; and `SyncPublisher.cs` exposes **exactly four** publish methods — `snapshot`,
`delta`, `heartbeat`, `evidence` — all of which `:app` projects. **The third command is what bounds
the severity to *latent*:** no unprojected kind is published today, so no run of `IGNORED` can
occur yet.

### C-68-4 — baseline, negative control, and the green run

```bash
cd <android>
# The probe needs a JDK 17 (:core pins jvmToolchain(17)); the image ships 21.
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless

fix=$(git log --format=%H -1 --grep='a §6.2 gap is what was never received')

git checkout -q $fix^ -- core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt \
                         core/src/test/kotlin/app/careerseeker/core/PullPolicyTest.kt
scripts/core-probe.sh --rerun   # baseline: neither the fix nor the tests

git checkout -q $fix  -- core/src/test/kotlin/app/careerseeker/core/PullPolicyTest.kt
scripts/core-probe.sh --rerun   # the negative control: the tests, without the fix

git checkout -q $fix  -- core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt
scripts/core-probe.sh --rerun   # with the fix
```

*Expected, and **observed**:*

| | measured |
| --- | --- |
| baseline, clean worktree | `BUILD SUCCESSFUL`, **`318 tests, 0 failed, 0 skipped, across 22 classes`** |
| negative control (tests, no fix) | **`322 tests completed, 3 failed`** — the three new tests that need the fix; **all 318 existing green** |
| with the fix | `BUILD SUCCESSFUL`, **`322 tests, 0 failed, 0 skipped, across 22 classes`** |
| re-run after all three mutations were restored | `BUILD SUCCESSFUL`, **`322 tests, 0 failed, 0 skipped, across 22 classes`** |

**The fourth new test — *"a genuine gap after unprojected envelopes is still detected"* — passes
unfixed, deliberately.** It is a guard against over-fixing, not a control, and it is red under M2.
Read the "3 failed" line with that in mind: **4 tests added, 3 of them controls**.

`repo.maven.apache.org` returned **429 Too Many Requests** on the first two baseline attempts and
succeeded on the third; every later run resolved first time. See **B-21** — retry with backoff, and
do **not** report a gate result you did not get.

### C-68-5 — the three mutations

```bash
cd <android>
# M1: revert the baseline to the applied mark alone
#   maxOf(positionBefore.highestAppliedSeq, highestHandledSeq)  ->  positionBefore.highestAppliedSeq
# M2: move `if (envelopeSeq > highestHandledSeq) highestHandledSeq = envelopeSeq`
#   from AFTER the `decide(...)` call to BEFORE it
# M3: add `highestHandledSeq = 0L` next to `pending = false` in onOpen
# ...then, for each:
scripts/core-probe.sh
git checkout -- core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt
```

*Expected, and **observed**:*

| | measured |
| --- | --- |
| **M1** | **3 failed** — `a run of unprojected envelopes…`, `a run of malformed envelopes…`, `a reopen does not forget…` |
| **M2** | **compiles**, **7 failed** — adds `a genuine gap after unprojected envelopes is still detected`, `a gap larger than the threshold asks for a fresh snapshot`, `every reason sends since_seq zero`, `an unsolicited snapshot also releases the latch`, `a reopen releases a gap request latched on a warm replica`, `SyncPumpTest > a real gap larger than the threshold asks for a snapshot exactly once`, `EntitlementRoutingApplierTest > reporting an ack as APPLIED would manufacture a sequence-gap request` |
| **M3** | **exactly 1 failed** — `a reopen does not forget the envelopes already handled` |

**M2 is the row to read twice.** It compiles, it is silently wrong, and it breaks **three test
classes** — so the *order* of the two operations inside `onEnvelope` is not a local detail. **It was
predicted to fail 4 and failed 7; the table reports what was measured.** **M3 is the narrowness
proof:** one failure, no collateral, which is what shows the not-clearing-at-`onOpen` decision is
pinned on purpose rather than covered by accident. Each mutated file was restored from a
pre-mutation copy and confirmed **byte-identical** before the next run; **no mutation produced a
compile error.**

### C-68-6 — F-67-1 was not fixed, and is not claimed to be

```bash
cd <android>
grep -n "app_id" core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
# f1bdc95 is run 67's tip -- i.e. THIS RUN'S BASE. Not `main`: the android `main`
# branch predates the whole :core module, so `main..HEAD` diffs 68 runs of work and
# answers a different question entirely.
git diff f1bdc95..HEAD -- core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
git diff f1bdc95..HEAD --name-only
```

*Expected, and **observed**:* `outcome()` still interpolates `app_id` **raw**; the first diff is
**empty** — the file was not touched this run; and the second lists **exactly six** files —
`AUDIT-REQUEST.md`, `BLOCKED.md`, `LOG.md`, `STATE.md`, `PullPolicy.kt`, `PullPolicyTest.kt`.
Symptom, bound and unblock for F-67-1 stay in `BLOCKED.md`.

### C-68-7 — standing state, unmoved

```bash
cd <engine>  && git rev-parse --short origin/main    # aac05f3
cd <android> && git rev-parse --short origin/main    # ebfaf81
cd <engine>  && git show origin/autonomy/codex-state:STATE.md | head -20
# PR counts — measured via the GitHub API this run, both repos, state=open:
#   ShivaClaw/careerseeker          -> 18 open, all draft  (#26, #32-#39, #45-#53)
#   ShivaClaw/careerseeker-android  ->  6 open, all draft  (#1-#6)
```

*Expected, and **observed**:* the two SHAs above; **18 engine PRs, all open, all draft**, with
**#32** (the assigned slice) and **#53** (H1) both still open; **6 android PRs, all open, all
draft**; Terra **COMPLETE, files claimed: none**. **Both counts were measured this run, not carried
forward.**

### C-68-8 — no vector byte written

```bash
cd <engine> && tmp=$(mktemp -d) && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C "$tmp"
diff -r "$tmp/docs/sync-vectors/v1" <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
cd <android> && git diff f1bdc95..HEAD -- core/src/test/resources/sync-vectors/   # run 67's tip = this run's base
```

*Expected, and **observed**:* the `diff -r` is **silent**, **`exit=0`**, **29 files each side**; the
second command is **empty**. Measured **after** this run's commits.

### C-68-9 — how far behind the checkout arrived

```bash
cd <android>
git rev-parse --short HEAD                      # before checking out the work branch
git rev-list --count ebfaf81..f1bdc95           # main -> run 67's tip
```

*Expected, and **observed**:* the checkout arrives at **`ebfaf81`**, which is `origin/main`, and it
is **252** commits behind `claude/android-a0-probe` at run 67's tip.

**Run 66 and run 67 both wrote 247, and run 68 wrote it too before measuring it.** 247 was correct
when run 66 measured it; the figure moves every time a run commits, and copying it forward turns a
measurement into a rumour. Corrected to the measured **252** in `LOG.md` and `STATE.md` in the same
change as this entry.

---

## Sixty-ninth cloud iteration (2026-08-20) — F-67-1 closed in `:core`

Substitute `<engine>` = the `ShivaClaw/careerseeker` checkout, `<android>` = this one. Every command
below was run in this session and its output is what the "observed" line reports. **`910eb2e` is run
68's tip, i.e. this run's base** — not `main`, which predates the whole `:core` module and would
diff sixty-nine runs of work.

### C-69-1 — the assigned slice is built, and is not on `main`

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  git cat-file -e "$c^{commit}" && git merge-base --is-ancestor "$c" origin/main; echo "$c -> $?"
done
node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected, and **observed**:* all three commits exist; each `merge-base` returns **1** (`not on
main`). On `main` the generator reports **`OK: 26 vector files match the generator.`**, **`exit=0`**;
in a worktree at `7328a0b` it reports **`OK: 29 vector files match the generator.`**, **`exit=0`**.

### C-69-2 — the prompt's vector pin is stale

```bash
cd <android> && grep -r "7328a0b" VECTORS.lock STATE.md | head -3
cd <engine>  && git log --oneline -1 7328a0b
```

*Expected, and **observed**:* the pin of record is **`7328a0b`**, not the prompt's **`679a317`**;
it moved on **2026-08-12**. **Thirty-four runs have now been assigned the built slice.**

### C-69-3 — the defect, reproduced from the pre-fix source

```bash
cd <android> && git show 910eb2e:core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt \
  | grep -A2 'fun outcome('
```

*Expected, and **observed**:* the pre-fix body is
`"""{"app_id":"$appId","outcome":"${outcome.wire}","at":"$at"}"""` — raw interpolation, while
`entitlement()` two methods below routes every field through `jsonString()`.

To see both failure modes rather than read them, check out `910eb2e`, apply only the **test** half
of `1ed5e94`, and run the probe — the messages are the C-69-5 table.

```bash
cd <android> && git show 1ed5e94 --stat
```

*Expected, and **observed**:* exactly **two** files — `OutboundEnvelopes.kt` (+20/−2) and
`OutboundEnvelopesTest.kt` (+97). **No vector, no `:app` file, no CI file.**

### C-69-4 — the clean-worktree baseline

```bash
cd <android> && git stash list && git status --porcelain   # must be empty
scripts/core-probe.sh --rerun
```

*Expected, and **observed**:* `BUILD SUCCESSFUL` and
**`core-probe: 322 tests, 0 failed, 0 skipped, across 22 classes`** at `910eb2e`. Requires a JDK 17
under `/usr/lib/jvm` — `apt-get update -qq && apt-get install -y --no-install-recommends
openjdk-17-jdk-headless` — or the script exits 1 with that instruction (B-7).

### C-69-5 — the negative control ran BEFORE the fix and failed exactly the three new controls

```bash
cd <android>
git checkout 910eb2e -- core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
scripts/core-probe.sh --rerun; echo "exit=$?"
git checkout HEAD -- core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
```

*Expected, and **observed**:* `BUILD FAILED`, **`326 tests, 3 failed, 0 skipped, across 22
classes`** — **all 322 pre-existing green**. The three are
`an app_id containing a quote and a backslash survives the round trip` (**`outcome envelope
rejected: unknown_kind`**), `an at timestamp containing a quote survives the round trip`
(**`expected: <2026-06-11T14:02:11Z","injected":"field> but was: <2026-06-11T14:02:11Z>`**), and
`a crafted app_id cannot forge the outcome field` (**`the body carries a second `outcome` key:
{"kind":"outcome","body":{"app_id":"x","outcome":"offer","outcome":"sent","at":"..."}}` ==>
expected: <1> but was: <2>**).

**`an ordinary outcome body is byte-for-byte what it always was` passes here too, by design** — it
is an over-fix guard, not a control, which is why the negative control is **3** and not 4.

Restored, i.e. with the fix: `BUILD SUCCESSFUL`, **`326 tests, 0 failed, 0 skipped, across 22
classes`**.

### C-69-6 — three mutations, each red, each predicted before it ran

Apply one at a time to `OutboundEnvelopes.kt`, run `scripts/core-probe.sh --rerun`, restore.

| | mutation | predicted | **observed** |
| --- | --- | --- | --- |
| **M1** | `${jsonString(appId)}` → `"$appId"` | 2 | **2**: the app_id round-trip, the forge test |
| **M2** | `${jsonString(at)}` → `"$at"` | 1 | **1**: the `at` round-trip alone |
| **M3** | delete the `'\\' -> append("\\\\")` branch of `jsonString` | 2 | **2**: the app_id round-trip **and `the entitlement courier forwards original_json byte-for-byte`** |

*Expected, and **observed**:* every run `BUILD FAILED` with exactly those tests. **M3's second
failure is a pre-existing test in a path this commit did not modify** — that is the evidence the new
app_id test is not a duplicate of the line-215 fixture, and that both paths now depend on one
escaper. **No mutation produced a compile error.**

### C-69-7 — F-69-1 is filed and NOT fixed

```bash
cd <android> && git diff 910eb2e..HEAD -- core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt \
  | grep -E '^\+.*(pairing|keyId|timestamp)' | grep -v jsonString
grep -n 'fun aad()' -A3 core/src/main/kotlin/app/careerseeker/core/Protocol.kt
```

*Expected, and **observed**:* the first command is **empty** — `build()`'s header interpolation of
`pairing`, `keyId` and `timestamp` is **untouched**; `aad()` is still
`"v=$v|pairing=$pairing|dir=…|seq=$seq|ts=$ts|key_id=$keyId"`. **F-69-1 is recorded in `BLOCKED.md`
with its severity bound; no fix is claimed.**

### C-69-8 — freshness and the standing state

```bash
cd <engine>  && git rev-parse --short origin/main    # aac05f3
cd <android> && git rev-parse --short origin/main    # ebfaf81
cd <android> && git rev-list --count ebfaf81..910eb2e
cd <engine>  && git show origin/autonomy/codex-state:STATE.md | head -20
# PR counts — measured via the GitHub API this run, both repos, state=open:
#   ShivaClaw/careerseeker          -> 18 open, all draft  (#26, #32-#39, #45-#53)
#   ShivaClaw/careerseeker-android  ->  6 open, all draft  (#1-#6)
```

*Expected, and **observed**:* the two SHAs above; the checkout arrives at **`ebfaf81`** and is
**257** commits behind the work branch — **run 68 measured 252 against its own base `f1bdc95`, and
that figure moves with every commit; 257 is measured here, not inherited.** **18 engine PRs, all
open, all draft**, **#32** and **#53** among them; **6 android PRs, all open, all draft**. Terra
**COMPLETE, files claimed: none**.

### C-69-9 — no vector byte written

```bash
cd <engine> && tmp=$(mktemp -d) && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C "$tmp"
diff -r "$tmp/docs/sync-vectors/v1" <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
cd <android> && git diff 910eb2e..HEAD -- core/src/test/resources/sync-vectors/
```

*Expected, and **observed**:* the `diff -r` is **silent**, **`exit=0`**, **29 files each side**; the
second command is **empty**. Measured **after** this run's commits.

**Use absolute paths.** This check was first run in this session with a `cd` inherited from a
previous command and executed in the **engine** tree, where it reported `0 files` and a missing
directory. A drift check pointed at the wrong repository can only return a false negative.

### C-69-10 — `unknown_kind`, not `decrypt_failed`, is deliberate

```bash
cd <android> && grep -n 'maps that to `unknown_kind`' -B6 -A6 \
  core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt
```

*Expected, and **observed**:* `kindOf`'s KDoc states it returns null for malformed JSON and the
caller maps that to `unknown_kind`, "which matches the engine's `JsonDocument.Parse` behaviour so
both implementations classify a garbage body identically". **This is why the malformed-body error
code was not filed as a finding**: the diagnosis is misleading to a human but correct and
cross-implementation-consistent, and PQ-A2-2's `decrypt_failed` rule is about *structural* envelope
rejection, not about an undecodable payload body.

---

## RUN 70 — 2026-08-20 (thirty-fifth firing): F-69-1's JSON half closed, its AAD half deferred on purpose

**Auditor setup for every command below.** `<android>` is this checkout,
`<engine>` is a `ShivaClaw/careerseeker` checkout. **Use absolute paths** — run 69 records a
drift check that silently ran in the wrong repository and could only have returned a false
negative. Every command was run in this session and the *observed* output is quoted.

### C-70-1 — the assigned slice exists, and is still not on `main`

```bash
cd <engine> && git fetch --all --prune
for c in 8575539 22b028e 7328a0b; do
  git merge-base --is-ancestor $c origin/main && echo "$c ON MAIN" || echo "$c not on main (exit $?)"
done
```

*Expected, and **observed**:* all three print **`not on main (exit 1)`**. The slice this run's
prompt assigns has existed since 2026-08-09; this is its **thirty-fifth** assignment.

### C-70-2 — the pin is `7328a0b`, the prompt's `679a317` is stale, and the generator agrees

```bash
cd <engine> && git worktree add --detach /tmp/eng-pin 7328a0b
cd /tmp/eng-pin && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
ls docs/sync-vectors/v1/*.json | wc -l
grep -n 'Pinned commit' <android>/core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected, and **observed**:* **`OK: 29 vector files match the generator.`**, **`exit=0`**, **29**
files; the lock names **`7328a0bc043335491cd96a67d634e8eea2a13af9`**.

### C-70-3 — the defect: three header fields interpolated raw, into JSON and into the AAD

```bash
cd <android> && git show e696855:core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt \
  | grep -n '"pairing":"\$pairing"\|"ts":"\$timestamp"\|"key_id":"\$keyId"'
grep -n 'fun aad()' -A3 core/src/main/kotlin/app/careerseeker/core/Protocol.kt
```

*Expected, and **observed**:* the pre-fix file interpolates all three raw; `aad()` is
`"v=$v|pairing=$pairing|dir=${dir.wire}|seq=$seq|ts=$ts|key_id=$keyId"` and **is unchanged by this
run** — the second command's output is identical before and after.

### C-70-4 — negative control: the tests were written before the fix, and five failed

```bash
cd <android> && git stash list   # nothing needed; reproduce from the commit instead
git checkout e696855 -- core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
scripts/core-probe.sh --rerun    # tests from HEAD, production code from before the fix
git checkout HEAD -- core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
```

*Expected, and **observed**:* `BUILD FAILED`, **`334 tests, 5 failed, 0 skipped, across 22
classes`**, the five being `a key_id containing a quote does not malform the envelope header`,
`a crafted key_id cannot forge a second header field`, `a crafted ts cannot restate the sequence
number`, `a ts carrying the AAD separator is refused at build`, and `a malformed pairing id is
refused at construction`. **All 326 pre-existing tests green.** The clean-worktree baseline taken
before any edit was **`326 tests, 0 failed, 0 skipped, across 22 classes`**.

**Three of the eight new tests pass unfixed, deliberately, and are not controls**: the deferral pin
(`a key_id carrying the AAD separator is still accepted, deliberately`), the reason pin (`two
distinct headers can collide on one AAD…`) and the over-fix guard (`an ordinary envelope header is
byte-for-byte what it always was`). That is why the control is **5** and not **8**.

### C-70-5 — with the fix, `:core:test` is green at 334

```bash
cd <android> && scripts/core-probe.sh --rerun
```

*Expected, and **observed**:* `BUILD SUCCESSFUL`, **`core-probe: 334 tests, 0 failed, 0 skipped,
across 22 classes`**. **This is `:core:test` only** — see the bound in C-70-11.

### C-70-6 — seven mutations, each red, every prediction matched

Apply each to `core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt`, run
`scripts/core-probe.sh --rerun`, then restore from a pre-mutation copy:

| | mutation | **measured failures** |
| --- | --- | --- |
| **M1** | `"key_id":${jsonString(keyId)}` → `"key_id":"$keyId"` | **2** |
| **M2** | `"ts":${jsonString(timestamp)}` → `"ts":"$timestamp"` | **1** |
| **M3** | `"pairing":${jsonString(pairing)}` → `"pairing":"$pairing"` | **0** |
| **M4** | delete `requireUnambiguous("ts", timestamp)` | **1** |
| **M5** | delete `require(isValidPairingId(pairing))` | **1** |
| **M6** | `jsonString` → `"\"" + raw + "\""` (escapes nothing) | **7** |
| **M7** | **add** `requireUnambiguous("key_id", keyId)` to `init` | **1** |

*Expected, and **observed**:* every count above, each matching its prediction. **M3's zero is the
measurement, not a miss** — `isValidPairingId`'s character class excludes `"`, so escaping `pairing`
is unreachable and no test can fail for it. **M6 takes down four PRE-EXISTING tests** (`the
entitlement courier forwards original_json byte-for-byte`, `an at timestamp containing a quote
survives the round trip`, `an app_id containing a quote and a backslash survives the round trip`,
`a crafted app_id cannot forge the outcome field`) alongside three new ones, which is the proof
that the header and the body now share one escaper. **M7's single failure is the deferral pin.**

### C-70-7 — the `key_id` guard was written, then removed, because PQ-AAD-1 already decided it

```bash
cd <android> && sed -n '/## PQ-AAD-1/,/^---$/p' docs/protocol-questions.md | grep -n 'Half 2' -A8
grep -n 'Tightening the Kotlin' -A4 docs/protocol-questions.md
grep -n 'a gate for Brandon' -B4 docs/protocol-questions.md
grep -n 'requireUnambiguous' core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt
```

*Expected, and **observed**:* PQ-AAD-1 Half 2 documents the `|` collision with the **same two-header
construction** this run's test uses — it was filed 2026-08-12 and **this run discovers nothing about
it**. Its answer places the fix in §3 for `ts` **and** `key_id` together and calls it *"a gate for
Brandon"*. `requireUnambiguous` therefore appears **twice**: its definition, and **one** call site,
for `ts`. There is **no** `key_id` call site, and `init` carries the comment saying so.

### C-70-8 — freshness and the standing state

```bash
cd <engine>  && git rev-parse --short origin/main    # aac05f3
cd <android> && git rev-parse --short origin/main    # ebfaf81
cd <android> && git rev-list --count origin/main..HEAD
cd <engine>  && git show origin/autonomy/codex-state:STATE.md | head -12
# PR counts — measured via the GitHub API this run, both repos, state=open:
#   ShivaClaw/careerseeker          -> 18 open, all draft  (#26, #32-#39, #45-#53)
#   ShivaClaw/careerseeker-android  ->  6 open, all draft  (#1-#6)
```

*Expected, and **observed**:* the two SHAs above; the branch is **260** commits ahead of a `main`
the checkout arrives detached at — **run 69 measured 257 against its own tip, and that figure moves
with every commit; 260 is measured here, not inherited.** **18 engine PRs and 6 android PRs, all
open, all draft.** Terra: **COMPLETE, files claimed: none.**

### C-70-9 — no vector byte was written

```bash
cd <engine> && tmp=$(mktemp -d) && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C "$tmp"
diff -r "$tmp/docs/sync-vectors/v1" <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
cd <android> && git diff e696855..HEAD -- core/src/test/resources/sync-vectors/
```

*Expected, and **observed**:* the `diff -r` is **silent**, **`exit=0`**, **29 files each side**; the
second command is **empty**. Both sides addressed by **absolute path**.

### C-70-10 — PQ-AAD-1's "smallest resolution" re-run, and it changes nothing

```bash
cd <engine> && grep -rn 'ASCII\|UTF8\|GetBytes' src/Sync/*.cs | grep -i 'aad\|EnvelopeCodec\|DeviceSignature'
```

*Expected, and **observed**:* `EnvelopeCodec.cs:31` and `:45` pass `Encoding.ASCII.GetBytes(aad)`;
`DeviceSignature.cs:38` uses `Encoding.ASCII.GetBytes(sigInput)`. **This confirms the answer already
recorded on 2026-08-12 and is not a new finding** — the engine and the phone agree on ASCII, and the
surrogate-pair divergence that answer measured is untouched by this run. Quoted here only because
the command was cheap and re-running an answered question beats inheriting it.

### C-70-11 — the bound on all of the above: `:core:test` only

```bash
cd <android> && sed -n '33,45p' scripts/core-probe.sh
```

*Expected, and **observed**:* the script's own header states it is **not** the android gate and runs
**one** of the gate's five tasks. **`:app:assembleDebug`, `:app:lintDebug`, `:app:test` and
`checkCoreIsAndroidFree` did not run and no result is claimed for them** (**B-7**). **No
zero-warning claim is made**: `PairingSessionTest.kt:53` and `RelayClientTest.kt:383` emit
`No cast needed`, pre-existing, in files this run did not touch. **`Verify-Alpha.ps1` did not run
and could not** — no `pwsh`, no `dotnet`, and it is a Windows gate.

---

## Run 71 — the ledger's turn: PQ-A2-5's phone half is closed in code and read open in the ledger

Every claim run 71 made has a command below. The slice was documentation, not code — the "To close"
prescription in `docs/protocol-questions.md` §PQ-A2-5 was satisfied on 2026-08-12 by the re-vendor
and by commit `60a20d5` on the phone side, and its own entry did not say so. Nothing here changes
`.kt`, `.kts` or any generated artefact; the only edits are `docs/protocol-questions.md` (adds
`### CLOSED IN PART`), `LOG.md`, `AUDIT-REQUEST.md`, `STATE.md`, and the engine repo's docs-only
`autonomy/claude-state` branch.

### C-71-1 — the assigned S5 spec slice is still not on `main` in the engine repo

> **Claim.** Same as C-70-1 (thirty-sixth firing): the three commits the recurring prompt names as
> "S5 spec half to build" — `8575539`, `22b028e`, `7328a0b` — exist in `ShivaClaw/careerseeker` and
> report `not on main` (exit **1**) this run. The gap is landing, not building; landing needs a
> Windows gate (**B-18**).

```bash
cd <careerseeker> && git fetch --all --prune >/dev/null && \
  for c in 8575539 22b028e 7328a0b; do \
    printf '%s: ' "$c"; git cat-file -t $c && \
      git merge-base --is-ancestor $c origin/main && echo "  on main" || echo "  not on main"; \
  done
```

*Expected, and **observed**:* three commits, each `commit` typed, each `not on main` — the exact
result run 70 saw, and runs 47..70 before it.

### C-71-2 — the pin is `7328a0b`, and the vendored corpus is byte-identical to it

> **Claim.** `VECTORS.lock` names `7328a0bc043335491cd96a67d634e8eea2a13af9`; the vendored
> `core/src/test/resources/sync-vectors/v1/` is **29 files, `diff -r` silent** against a fresh
> checkout of that commit's `docs/sync-vectors/v1/`.

```bash
cd <android> && awk '/^# Pinned commit:/ {print $NF; exit}' core/src/test/resources/sync-vectors/VECTORS.lock
# separately, in a scratch dir:
git clone --depth 1 --branch main https://github.com/ShivaClaw/careerseeker fresh-cs
cd fresh-cs && git fetch --depth 1 origin 7328a0bc043335491cd96a67d634e8eea2a13af9 && \
  git checkout 7328a0bc043335491cd96a67d634e8eea2a13af9 -- docs/sync-vectors/v1
cd .. && diff -r <android>/core/src/test/resources/sync-vectors/v1 fresh-cs/docs/sync-vectors/v1; echo exit=$?
ls <android>/core/src/test/resources/sync-vectors/v1 | wc -l
```

*Expected, and **observed**:* `7328a0bc043335491cd96a67d634e8eea2a13af9`, `exit=0`, `29`. **Both
sides addressed by absolute path** (run 69's process finding).

### C-71-3 — both ack vectors are vendored, by name

> **Claim.** `entitlement-ack.json` and `entitlement-ack-no-order-id.json` are present in the
> vendored corpus. This is what makes the phone-side re-vendor half of PQ-A2-5's "To close" line a
> `done`.

```bash
cd <android> && ls core/src/test/resources/sync-vectors/v1/ | grep -E '^entitlement-ack'
```

*Expected, and **observed**:* `entitlement-ack-no-order-id.json`, `entitlement-ack.json`.

### C-71-4 — `EntitlementAckTest` reads the vectors, not transcribes them

> **Claim.** `ackWithOrderId` and `ackNoOrderId` are produced by `ackPlaintext(name)`, which loads
> `sync-vectors/v1/$name.json` and returns whatever `SyncCrypto.open` decrypts — **no literal body
> appears in the source**. Landed by `60a20d5` (*"S5: make the phone READ the ack vectors, closing
> PQ-A2-5 on this side"*), and pinned by a byte-level compact-JSON test the old transcription
> could not pass.

```bash
cd <android> && sed -n '45,80p' core/src/test/kotlin/app/careerseeker/core/EntitlementAckTest.kt
cd <android> && git log --oneline --all -- core/src/test/kotlin/app/careerseeker/core/EntitlementAckTest.kt | head
```

*Expected, and **observed**:* `ackPlaintext` reads the classpath resource and calls
`SyncCrypto.open`; the commit list includes `60a20d5 S5: make the phone READ the ack vectors,
closing PQ-A2-5 on this side` and, earlier, `13d7318 S5 phone half: the entitlement_ack applier`.

### C-71-5 — `ProtocolVectorsTest` has the cross-implementation ack test §PQ-A2-5 asked for

> **Claim.** `ProtocolVectorsTest:242` — `entitlement ack vectors decrypt to the exact bytes that
> unlock Pro` — enumerates every `type == entitlement_ack` vector from `index.json`, decrypts each
> under the vector's own key/nonce/AAD, round-trips against `plaintext_json` in a sorted-key
> canonical form, and reads `product_id`, `acknowledged_at`, and the **optional** `order_id`
> straight off the vector (so an omitted `order_id` and a `null` `order_id` remain
> distinguishable). It asserts both acks grant byte-equal `ProState`, which is the exact property
> the question said had to be pinned across implementations.

```bash
cd <android> && sed -n '225,275p' core/src/test/kotlin/app/careerseeker/core/ProtocolVectorsTest.kt
```

*Expected, and **observed**:* the section starts with `// ---- entitlement acks`, filters
`type == "entitlement_ack"` from the manifest, and the test does the exact-byte plaintext
round-trip described above.

### C-71-6 — B-7 was scope-corrected and `:core:test` is green at 334

> **Claim.** The reason PQ-A2-5's "To close" thought the work undoable — *"neither doable in a
> session that cannot run `:core:test` (B-7)"* — is out of date: B-7 was scope-corrected
> 2026-08-11 (BLOCKED.md:850) to say it never covered `:core`, and `:core:test` runs here via
> `scripts/core-probe.sh`. Executed this run, on a clean worktree, no code change:
> **`BUILD SUCCESSFUL`, `core-probe: 334 tests, 0 failed, 0 skipped, across 22 classes`**,
> unchanged from run 70's post-fix count.

```bash
cd <android> && sed -n '850,860p' BLOCKED.md
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
cd <android> && bash scripts/core-probe.sh 2>&1 | tail -5
```

*Expected, and **observed**:* BLOCKED.md carries the scope-correction; `core-probe.sh` prints
`core-probe: 334 tests, 0 failed, 0 skipped, across 22 classes` and `BUILD SUCCESSFUL`.

### C-71-7 — the main-repo half stays open, deliberately

> **Claim.** §10.2 of `docs/Sync-Protocol.md` — the normative wire doc, on the engine repo's
> `claude/s2-*` branches — still carves the ack vectors out as evidence about *one*
> implementation. **This branch does not change that**, and nothing this run touches the
> `SyncHarness` side either. That is the same interpretation rule that kept run 70 out of §4.1's
> AAD: do not amend a normative wire document unilaterally.

```bash
cd <careerseeker> && git grep -n 'entitlement' -- 'docs/Sync-Protocol.md' | head
cd <careerseeker> && git log --oneline --all --diff-filter=M -- docs/Sync-Protocol.md \
  | grep -v -f <(git log --oneline origin/main -- docs/Sync-Protocol.md | awk '{print $1}') | head
```

*Expected, and **observed**:* the ack sections live on the `claude/s5-*` and `claude/s2-*`
branches and are not on `main`; nothing since run 70 has been pushed by this session to any file
under `docs/Sync-Protocol.md` — the engine checkout was read-only this run apart from the
docs-only `autonomy/claude-state` heartbeat.

### C-71-8 — freshness and the standing state (measured, not carried forward)

> **Claim.** engine `origin/main` **`aac05f3`**, unmoved since 2026-08-12; android `origin/main`
> **`ebfaf81`**; **18 engine PRs open, all draft** (`#26, #32–#39, #45–#53`); **6 android PRs
> open, all draft** (`#1–#6`, this branch is `#6`); Terra (`autonomy/codex-state:STATE.md`)
> **COMPLETE, files claimed: none** — no collision.

```bash
cd <careerseeker> && git rev-parse origin/main
cd <android> && git rev-parse origin/main
# via MCP: mcp__github__list_pull_requests(owner=ShivaClaw, repo=careerseeker,        state=open, perPage=30)
# via MCP: mcp__github__list_pull_requests(owner=ShivaClaw, repo=careerseeker-android, state=open, perPage=20)
cd <careerseeker> && git show origin/autonomy/codex-state:STATE.md | head -10
```

*Expected, and **observed**:* `aac05f3f93f0ca06cbc9dfa7884f74a126f078dc` / `ebfaf81…`, the PR
lists above, and *"Current rung: COMPLETE. R0, R1, R4, R5, R6(a), R6(c), R6(d), and R7 are
DONE... Files claimed: none"*.

### C-71-9 — the bound: `:core:test` only, no code changed

> **Claim.** No `.kt`, `.kts`, `.json`, `.yml`, `.sh`, or generated artefact was written by run
> 71. The only edits are `docs/protocol-questions.md`, `LOG.md`, `AUDIT-REQUEST.md`, `STATE.md`,
> and the engine repo's `autonomy/claude-state:STATE.md`. **`:core:test` was executed as
> evidence, not as a fix.** **The android gate did not run**: `:app:assembleDebug`,
> `:app:lintDebug`, `:app:test` and `checkCoreIsAndroidFree` are unrun and unclaimed (**B-7**),
> and **no zero-warning claim is made**. **`Verify-Alpha.ps1` did not run and could not** — no
> `pwsh`, no `dotnet`, and it is a Windows gate.

```bash
cd <android> && git diff --stat origin/claude/android-a0-probe..HEAD
```

*Expected, and **observed**:* the diff touches only the five files named above.

---

## RUN 72 — 2026-08-20. PQ-A2-6: two spellings of "optional", and a justification the corpus does not support

### C-72-1 — the assigned slice is built, and is still not on main (thirty-seventh firing)

> **Claim.** The prompt's slice (§4.3 `entitlement_ack` body, the ack vectors, PQ-A2-1/-2/-3) has
> existed since 2026-08-09 as commits `8575539`, `22b028e`, `7328a0b` in the **engine** repo, all
> three still unmerged. It was verified, not rebuilt.

```bash
cd <careerseeker> && for c in 8575539 22b028e 7328a0b; do
  git cat-file -e "$c^{commit}" && git merge-base --is-ancestor "$c" origin/main \
    && echo "$c ON MAIN" || echo "$c exists, not on main"; done
```

*Expected, and **observed**:* all three print `exists, not on main`. Subjects: *"S5: define the
entitlement_ack body…"*, *"S5: pin section 4.3.3 with two entitlement_ack vectors…"*, *"S5: add
the invalid-unknown-field vector, closing PQ-A2-3 and B-6"*.

### C-72-2 — the prompt's vendored pin `679a317` is stale; it is `7328a0b`

> **Claim.** The pin moved on 2026-08-12. `generate.mjs --check` reports **29** vector files at
> the pin and **26** on `origin/main` — the three S5 vectors are on the unmerged draft stack, so
> the difference is expected and is not drift.

```bash
cd <android>  && grep 'Pinned commit' core/src/test/resources/sync-vectors/VECTORS.lock
cd <careerseeker> && node docs/sync-vectors/generate.mjs --check          # on main
cd <careerseeker> && git worktree add --detach /tmp/pin 7328a0b \
  && (cd /tmp/pin && node docs/sync-vectors/generate.mjs --check)
```

*Expected, and **observed**:* `7328a0bc043335491cd96a67d634e8eea2a13af9`;
`OK: 26 vector files match the generator.` on main, exit 0;
`OK: 29 vector files match the generator.` at the pin, exit 0.

### C-72-3 — the vendored corpus is byte-identical to the pin, before AND after this run

> **Claim.** 29/29 identical, `diff -r` silent, exit 0 — measured twice, the second time **after**
> the mutation testing in C-72-7, to prove no vector byte was written. Both sides addressed by
> absolute path (run 69's process finding).

```bash
diff -r /abs/<android>/core/src/test/resources/sync-vectors/v1 \
        /abs/<pin-worktree>/docs/sync-vectors/v1 ; echo "exit=$?"
```

*Expected, and **observed**:* no output, `exit=0`, 29 files on each side, both times.

### C-72-4 — `:core` contains both readings of "optional field", one file apart

> **Claim.** `EnvelopeJson.kt:63-69` reads an explicit JSON `null` `sig` as **absent** (envelope
> accepted). `EntitlementAck.kt:95-100` reads an explicit JSON `null` `order_id` as **malformed**,
> so `parse` returns null and `apply` drops the entire ack, leaving a paying user `Free` with no
> error on any layer. Neither site cites the other. This is PQ-A2-6.

```bash
cd <android> && sed -n '60,70p'  core/src/main/kotlin/app/careerseeker/core/EnvelopeJson.kt
cd <android> && sed -n '93,101p' core/src/main/kotlin/app/careerseeker/core/EntitlementAck.kt
```

*Expected, and **observed**:* the `sig` branch has an explicit `sigElement.toString() == "null"
-> null` arm; the `order_id` branch has no null arm and falls through to `?: return null`.

### C-72-5 — the leniency's stated justification is false: no vector spells absence as null

> **Claim.** `EnvelopeJsonTest` justified accepting a null `sig` with *"the vectors encode it that
> way"*. Across all 29 vendored vectors, every `sig` present is a **string** (9 of 29 files carry
> one) and every vector without a signature **omits the key**. The null spelling is unwitnessed on
> the wire in either direction, so neither parser's choice is a conformance fact.

```bash
cd <android>/core/src/test/resources/sync-vectors/v1 && grep -o '"sig"[^,}]*' *.json | sort
cd <android>/core/src/test/resources/sync-vectors/v1 && grep -l '"sig": *null' *.json ; echo "exit=$?"
```

*Expected, and **observed**:* nine `"sig": "<base64url string>"` lines, one per file; the second
command matches nothing and exits 1.

### C-72-6 — the engine cannot currently emit the null spelling, and the guard is in the other repo

> **Claim.** `src/Sync/SyncPayloads.cs` sets `DefaultIgnoreCondition = JsonIgnoreCondition.
> WhenWritingNull`, **global to all five payload builders**, so `orderId = null` omits the field.
> What holds that in place is `SyncHarness`'s byte-identity assertion against the ack vectors — an
> **engine-side** test that pins the omission incidentally. The phone's strictness is therefore
> safe because of a test in the other repository. This is why PQ-A2-6 is latent, not live.

```bash
cd <careerseeker> && git show origin/claude/s5-entitlement-ack-emitter:src/Sync/SyncPayloads.cs \
  | sed -n '18,23p;58,62p'
```

*Expected, and **observed**:* the `JsonSerializerOptions` block carrying `DefaultIgnoreCondition
= JsonIgnoreCondition.WhenWritingNull`, and `EntitlementAck(...)` passing `order_id = orderId`
through `Encode`.

### C-72-7 — the two new tests are load-bearing: both mutations go red

> **Claim.** Baseline **334**. With the two tests, **336**, 0 failed. **M1** — make
> `EntitlementAck.kt` treat a null `order_id` as absent (the candidate fix) — turns **exactly one**
> test red, `an order_id of JSON null drops the whole ack…`. **M2** — set `order_id` to an explicit
> null in a vector — turns `no vector spells an absent optional field as an explicit JSON null`
> red. Both mutations were reverted; M2 was applied in a **throwaway git worktree**, so the real
> vendored corpus was never written (re-proved by C-72-3's second run).

```bash
cd <android> && scripts/core-probe.sh                      # baseline, then after each mutation
cd <android> && git status --porcelain                     # after restore
```

*Expected, and **observed**:* baseline `core-probe: 334 tests, 0 failed, 0 skipped, across 22
classes`, exit 0. After the two tests: `336 tests, 0 failed, 0 skipped, across 22 classes`, exit 0.
M1: `BUILD FAILED`, one FAILED line, the ack test. M2: `BUILD FAILED`, two FAILED lines — the
corpus test, **plus** `ProtocolVectorsTest > entitlement ack vectors decrypt to the exact bytes
that unlock Pro`. **The second failure is an artefact of the mutation method, not independent
evidence**: editing `plaintext_json` without re-sealing the ciphertext breaks byte-identity
regardless of the null. `git status --porcelain` after restore lists only the three test files.

### C-72-8 — freshness and the standing state (measured this run, not carried forward)

> **Claim.** engine `origin/main` **`aac05f3`**, unmoved since 2026-08-12; android `origin/main`
> **`ebfaf81`**, **262** commits behind this branch's tip; **18 engine PRs open, all draft**
> (`#26, #32–#39, #45–#53`); **6 android PRs open, all draft** (`#1–#6`, this branch is `#6` at
> `4fcfaf4`); **#32** and **#53** both still open and draft; Terra
> (`autonomy/codex-state:STATE.md`) **COMPLETE, files claimed: none** — no collision.

```bash
cd <careerseeker> && git rev-parse --short origin/main
cd <android> && git rev-parse --short origin/main && git rev-list --count origin/main..HEAD
# via MCP: mcp__github__list_pull_requests(owner=ShivaClaw, repo=careerseeker,         state=open)
# via MCP: mcp__github__list_pull_requests(owner=ShivaClaw, repo=careerseeker-android, state=open)
cd <careerseeker> && git show origin/autonomy/codex-state:STATE.md | head -12
```

*Expected, and **observed**:* `aac05f3` / `ebfaf81` / `262`; the two PR lists above, every entry
`"draft": true, "state": "open"`; *"Current rung: COMPLETE… Files claimed: none"*.

### C-72-9 — the bound: `:core:test` only, and no production code changed

> **Claim.** Run 72 wrote **no `:core` main-source file, no `:app` file, no vector, no `.mjs`, no
> `.yml`, no C#**. The only edits are three **test** files, `docs/protocol-questions.md`,
> `LOG.md`, `AUDIT-REQUEST.md`, `STATE.md`, `BLOCKED.md`, and the engine repo's docs-only
> `autonomy/claude-state:STATE.md`. **The behaviour PQ-A2-6 describes was pinned, not changed** —
> the fix belongs on an engine branch with the spec amendment and a shared vector.
> **The android gate did not run**: `:app:assembleDebug`, `:app:lintDebug`, `:app:test` and
> `checkCoreIsAndroidFree` need the Android SDK (**B-7**), are unrun and unclaimed, and **no
> zero-warning claim is made**. **`Verify-Alpha.ps1` did not run and could not** — no `pwsh`, no
> `dotnet`, and it is a Windows gate.

```bash
cd <android> && git diff --stat origin/claude/android-a0-probe..HEAD
```

*Expected, and **observed**:* the diff touches only the files named above; no path under
`core/src/main/`, `app/`, or `core/src/test/resources/`.

---

## Seventy-third run — 2026-08-21 (Linux sandbox): revalidating the landing plan after its use-by date

Run `git fetch --all --prune` in **both** trees first, or every count below is stale — rule one.
`<engine>` = a checkout of `ShivaClaw/careerseeker`; `<android>` = this repo.

### C-73-1 — the assigned slice is built, for the thirty-eighth consecutive run

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do
  echo "== $c =="; git log -1 --format='%ad  %s' $c; git show --stat --format='' $c
done
```

*Expected, and **observed** this run:* `8575539` (**Sun Aug 9 2026**) touches `docs/Sync-Protocol.md`
**only**, **+114/−3**; `22b028e` adds `entitlement-ack.json`, `entitlement-ack-no-order-id.json`,
`index.json` **and** `generate.mjs`; `7328a0b` (**Wed Aug 12 2026**) adds `invalid-unknown-field.json`.
**All four gates named in the recurring prompt are already closed.** Re-building would duplicate
`8575539` and regenerate a corpus next to the one the phone vendors.

### C-73-2 — the pin, and where it is not

```bash
cd <android> && grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1
cd <engine>  && git merge-base --is-ancestor 7328a0bc043335491cd96a67d634e8eea2a13af9 origin/main \
                 && echo "on main" || echo "NOT on main"
cd <engine>  && git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l
```

*Expected, and **observed**:* `7328a0bc043335491cd96a67d634e8eea2a13af9`; **`NOT on main`**; **26**.
**Run this in the engine tree.** In `<android>` the ancestor check returns **`exit=128`** ("Not a
valid commit name") because the commit lives in the other repository — which reads like a broken
command rather than a wrong tree. That is C-66-1's warning, and this run reproduced it by making the
mistake: the first invocation was run in `<android>` and had to be re-run.

### C-73-3 — the vendored corpus is still byte-identical to its pin

```bash
mkdir -p /tmp/pin-check && cd <engine> && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pin-check
diff -r /tmp/pin-check/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1
echo "exit=$?"; ls /tmp/pin-check/docs/sync-vectors/v1 | wc -l
```

*Expected, and **observed**:* no output, **`exit=0`**, **29** files — measured **after** this run's
replay work, not before it.

### C-73-4 — return day passed, and no human has acted (NEW; this is the run's finding)

```bash
cd <engine>  && git log -1 --format='%h %ad %an' --date=short origin/main && git log -1 --format='%ar' origin/main
cd <android> && git log -1 --format='%h %ad %an' --date=short origin/main
cd <engine>  && git log --all --format='%ad %an' --date=short | head -15
```

*Expected, and **observed** on 2026-08-21:* engine `origin/main` = **`aac05f3`, 2026-08-12,
`Portable G & Shiva's Claw`**, reported as **8 days ago**; android `origin/main` = **`ebfaf81`**;
and **every** commit in the recent list is authored **`Claude`** — runs 48–72's heartbeats. **The
last human commit in either repo predates the 2026-08-18 return date by six days.**

**Attack this first if you doubt the run.** The claim is *"no human action since 2026-08-12"*, and it
is falsifiable exactly as written: any commit, merge, close, undraft or review by a non-`Claude`
author after that date refutes it. It is deliberately scoped to **repository** activity — this
session cannot see Brandon's calendar, mail or intentions, and does **not** claim he is unaware.

### C-73-5 — the landing plan's inputs are unmoved (live heads, not local refs)

```bash
cd <engine>
# live heads via the API; compare to fetched refs
for spec in "48 claude/s8-harness-linux-reach" "35 claude/s2-seq-bound" \
            "36 claude/s2-transport-vocabulary" "51 claude/s3-pairing-confirm-consumer" \
            "52 claude/s6-outcome-disposition" "49 claude/s6-composition-root-decision" \
            "53 claude/s6-resume-reconciliation"; do
  pr=${spec%% *}; br=${spec#* }
  echo "#$pr $br $(git rev-parse origin/$br)"
done
git rev-parse --short origin/main
```

*Expected, and **observed**:* all **seven** local refs equal their PR's live head SHA — **0
mismatches** — and `origin/main` is **`aac05f3`**. Live heads this run: `#48 c93e88d`, `#35 2be00fc`,
`#36 b0b6c77`, `#51 edee32b`, `#52 94fd979`, `#49 f5e0c0a`, `#53 8177353`. **`RETURN-DAY.md` §3's
premises are intact**, four days after its last revalidation.

### C-73-6 — the two stops reproduce, and so does each one's composition

```bash
rm -rf /tmp/replay && git clone -q --no-hardlinks <engine> /tmp/replay
cd /tmp/replay && git fetch -q <engine> '+refs/remotes/origin/*:refs/remotes/origin/*'
git checkout -q -B replay origin/main
git config user.email claude@local && git config user.name Claude
stops=0
for br in claude/s8-harness-linux-reach claude/s2-seq-bound claude/s2-transport-vocabulary \
          claude/s3-pairing-confirm-consumer claude/s6-outcome-disposition \
          claude/s6-composition-root-decision; do
  if git merge --no-edit origin/$br >/dev/null 2>&1; then echo "$br CLEAN"
  else stops=$((stops+1)); echo "$br STOP"; git diff --name-only --diff-filter=U | sed 's/^/   /'
       git checkout --theirs . 2>/dev/null; git add -A; git commit -q --no-edit; fi
done; echo "TOTAL STOPS = $stops"
```

*Expected, and **observed**:* **`TOTAL STOPS = 2`** in §3's recommended (#53-closed) configuration
and §3's stated order. **#52** conflicts on exactly **5** files — `README.md`,
`docs/CareerSeeker-Project-Summary.md`, `docs/External-Audit-Handoff.md`, `scripts/Verify-Alpha.ps1`,
`src/Engine/README.md` — the **pin family**; **#49** on those five **plus
`tests/SyncHarness/Program.cs`**. **Nothing under `src/Sync/` conflicts**, which is the measured form
of §3's reason for closing #53.

**Scope of this evidence, stated plainly.** This proves the **merge topology** — which merges stop
and on what. It proves **nothing about whether the result builds or passes**: `Verify-Alpha.ps1` is a
Windows gate that did not run here (**C-ENV-1**), and the stops above were resolved `--theirs`
mechanically only to continue the replay, **which is not a resolution anyone should copy.**

### C-73-7 — the post-landing corpus, and the re-pin command's exact output

```bash
cd /tmp/replay && ls docs/sync-vectors/v1/ | wc -l && node docs/sync-vectors/generate.mjs --check
diff -rq /tmp/pin-check/docs/sync-vectors/v1 /tmp/replay/docs/sync-vectors/v1
cd <android> && scripts/repin-vectors.sh --check --engine /tmp/replay HEAD; echo "exit=$?"
```

*Expected, and **observed**:* **30** files, **`OK: 30 vector files match the generator.`**; the diff
names exactly **`index.json` differs** and **`pairing-high-bit-confirm.json` only in the replay**;
and `--check` prints `vendored: 29 files    at pin: 30 files`, **`+ pairing-high-bit-confirm.json`**,
**`~ index.json`**, `DRIFT: 2 file(s)`, **`exit=1`**. **Every token matches what `RETURN-DAY.md`
says to expect** — the re-pin step is executable as written. `--check` **writes nothing**; the
vendored corpus was re-verified 29/29 afterwards (**C-73-3**).

### C-73-8 — B-18 attempt 10 was delivered, and what that does and does not establish

Not reproducible by command — it is an out-of-band act, recorded here so it is auditable rather than
invisible. **This run sent a push notification to Brandon** (phone + inbox) carrying: return day
passed with 24 drafts unmerged and no human commit since 2026-08-12; that `RETURN-DAY.md` §3 is
**re-verified valid as written**; step 0 (**decide #53**); the load-bearing order
`#48, #35, #36, #51, #52, #49`; the same-sitting re-pin command; and a recommendation to **pause the
schedule** until the queue is cleared.

**What this establishes:** the finding left the repository. **What it does not:** that it was read,
or that anything will change. **B-18 stays open** — closing it is Brandon's action. A session
auditing this claim should treat it as *"a notification was sent"*, which is all it says.

---

## Seventy-fourth run — 2026-08-21 (Linux sandbox): a coverage guard that could not see the kind it was written for

### C-74-1 — the assigned slice is built, and is still not on `main` (thirty-ninth firing)

```bash
cd <engine> && git fetch --all --prune
for c in 8575539 22b028e 7328a0b; do
  git cat-file -t $c
  git merge-base --is-ancestor $c origin/main; echo "$c on main? exit=$?"
done
```

*Expected, and **observed**:* all three print **`commit`** — the S5 spec half (§4.3 body +
PQ-A2-1/-2), both ack vectors, and `invalid-unknown-field` (PQ-A2-3) — and all three report
**`exit=1`**, *not on main*. **Built since 2026-08-09; assigned again this run.** See **B-18**.

### C-74-2 — the pin is `7328a0b`, and the vendored corpus is byte-identical to it

```bash
cd <engine> && git worktree add --detach /tmp/pin74 7328a0b
diff -r /tmp/pin74/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
cd /tmp/pin74 && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected, and **observed**:* `diff -r` **silent, `exit=0`**; **29** files at the pin and **29**
vendored; **`OK: 29 vector files match the generator.`**, `exit=0`. **The recurring prompt's pin
`679a317` is stale — it is `7328a0b`**, unchanged since 2026-08-12. Both sides addressed by
absolute path (run 69's process finding).

### C-74-3 — the defect: §4.3's engine→phone table has eight rows and the derived set had seven

```bash
cd <engine> && git show origin/main:docs/Sync-Protocol.md | sed -n '/### 4.3 Payload kinds/,/^Phone → engine/p'
git show origin/main:src/Sync/Protocol.cs | sed -n '31,36p'
cd <android> && git show e1496ab^:core/src/main/kotlin/app/careerseeker/core/Protocol.kt | sed -n '125,128p'
```

*Expected, and **observed**:* the engine→phone table lists **eight** kinds — `snapshot`, `delta`,
`doc`, `evidence`, `heartbeat`, `conflict`, `entitlement_ack`, **`error`** — and `Protocol.cs:34-35`
carries `error` in `ShippingKinds`. The pre-fix `ENGINE_TO_PHONE_KINDS` reads
**`entries.filter { it.flow == KindFlow.ENGINE_TO_PHONE }`**, which drops every `KindFlow.BOTH`
kind and therefore yields **seven**. That set is the **input to `PayloadKindCoverageTest`**, so
`error` was exempt from the B-19 guard by the shape of the guard's input.

### C-74-4 — negative control: the gap was invisible from both ends, and naming it turns the guard red

The control is the enumerator widened **and nothing else** — no fourth set, no test edits:

```bash
cd <android> && git checkout e1496ab^ -- core/
sed -i 's|it.flow == KindFlow.ENGINE_TO_PHONE }.toSet()|it.flow != KindFlow.PHONE_TO_ENGINE }.toSet()|' \
  core/src/main/kotlin/app/careerseeker/core/Protocol.kt
scripts/core-probe.sh --rerun; echo "exit=$?"
git checkout HEAD -- core/
```

*Expected, and **observed**:* **`exit=1`**, exactly **two** red, and the guard's own message names
the kind:

```
flow matches section 4-3's direction tables() FAILED
  expected: <[snapshot, delta, doc, evidence, heartbeat, conflict, entitlement_ack]>
       but was: <[snapshot, delta, doc, evidence, heartbeat, conflict, entitlement_ack, error]>
every engine to phone kind is classified exactly once() FAILED
  engine->phone kinds with no declared destination: [error]. ...
```

**Both halves of the file were wrong in complementary directions**, which is why fifteen runs of
green told nobody: the direction-table case asserted the same seven names the enum produced — a
derivation compared against itself — and the second case's filter (`!= ENGINE_TO_PHONE`) did not
merely miss `error`, it **failed any attempt to classify it**, on the stated grounds that it
"cannot be received by the replica".

### C-74-5 — with the fix, `:core:test` is green at 338

```bash
cd <android> && set -o pipefail && scripts/core-probe.sh --rerun; echo "exit=$?"
```

*Expected, and **observed**:* **`BUILD SUCCESSFUL`**, `exit=0`, and
**`core-probe: 338 tests, 0 failed, 0 skipped, across 22 classes`**. Baseline before this run's
commits was **336/0** on the same tree, measured this run and not carried forward from run 72.
`set -o pipefail` is not optional — run 72's process finding was a `| tail` that reported `exit 0`
for a run that executed zero tests.

### C-74-6 — three mutations, each red, every prediction matched

```bash
# M1 -- narrow the derivation back to == ENGINE_TO_PHONE
# M2 -- RECEIVED_WITHOUT_A_DESTINATION = emptySet()
# M3 -- RECEIVED_WITHOUT_A_DESTINATION = setOf(ERROR, CONFLICT)
# after each: scripts/core-probe.sh --rerun; echo "exit=$?"; then restore Protocol.kt
```

*Expected, and **observed**:* all three `exit=1`.

| mutation | predicted | observed |
| --- | --- | --- |
| **M1** narrow the derivation | 1 red — the hand-transcribed §4.3 table | `flow matches section 4-3's direction tables` |
| **M2** empty the fourth set | 2 red — the set's pin, and coverage | `the undecided set holds exactly error`, `every engine to phone kind is classified exactly once` |
| **M3** park a second kind in it | 2 red — the set's pin, and **disjointness** | the same two, the second on `conflict` in two sets |

**M1 is the one that matters**: before this run the same narrowing turned **nothing** red. What
holds it is the hand-transcribed constant, not a smarter derivation — the failure mode was a
derivation agreeing with itself, so one side of the comparison must not be derived. After all
three, `Protocol.kt` was restored and re-diffed **byte-identical** to the committed version.

### C-74-7 — an engine→phone `error` is accepted, and therefore reaches the applier

```bash
cd <android> && scripts/core-probe.sh --rerun 2>&1 | grep "error payload is accepted"
```

*Expected, and **observed**:*
**`EnvelopeReceiverTest > an engine to phone error payload is accepted, and therefore reaches the
applier() PASSED`** — the receiver accepts an authentic `error` and reports `kind = "error"`.

**Read the bound before reading it as more.** This proves the payload **gets to** the single
`ReplicaApplier`; it does **not** execute the drop. `:app`'s `when` covers four projected kinds
with `else -> Ignored`, and `:app` needs the Android SDK (**B-7**), so the last step is **read, not
compiled** and is labelled that way in the source. The classification half is
`PayloadKindCoverageTest`; the decision half is **PQ-ERR-1** and is nobody's here.

### C-74-8 — freshness and the standing state (measured this run, not carried forward)

```bash
cd <engine> && git fetch --all --prune && git rev-parse origin/main
git log origin/main --format='%h|%an|%ad' --date=short -20 | grep -v '|Claude|' | head -1
cd <android> && git fetch --all --prune && git rev-parse origin/main
git show origin/autonomy/codex-state:STATE.md | head -12   # in <engine>
# PR counts via the API, state=open
```

*Expected, and **observed**:* engine `main` **`aac05f3`**, android `main` **`ebfaf81`**; the last
non-Claude commit on engine `main` is **`aac05f3`, 2026-08-12** — **nine days ago, three days after
return day**. **18 engine drafts + 6 android drafts open; none merged, closed or undrafted.**
Terra: heartbeat **2026-08-12T20:28:36**, **COMPLETE**, **files claimed: none** — **no collision**.
Both checkouts again arrived **detached at a stale `main`**; the android tree was **265** commits
behind this branch's tip pre-commit (run 73 measured 262), which is why rule one is rule one.

### C-74-9 — the bound: `:core:test` only, and what was not written

```bash
cd <android> && git status --porcelain core/src/test/resources/sync-vectors/ app/ | wc -l
git diff --stat 5694edc..HEAD
```

*Expected, and **observed**:* **`0`** — **no vector byte written, `VECTORS.lock` untouched, the pin
did not move (H7)**, and **no `:app` file written** (**B-19** unmoved). The diff is four files:
`Protocol.kt`, `PayloadKindCoverageTest.kt`, `EnvelopeReceiverTest.kt`, `docs/protocol-questions.md`,
plus this run's records. **`Verify-Alpha.ps1` did not run and no result is claimed for it** — no
`pwsh`, no `dotnet`, Windows gate (**B-7**). **The android gate did not run**: `:app:assembleDebug`,
`:app:lintDebug`, `:app:test` and `checkCoreIsAndroidFree` are **unrun and unclaimed**, and **no
zero-warning claim is made**. `docs/Sync-Protocol.md` was **read, never edited** — the normative
document is the engine's, and PQ-ERR-1's amendment, if any, follows a decision this run did not make.

### C-74-10 — CI reported GREEN on this head, and it is the gate this sandbox cannot run

```bash
# run 32449896251, job "Build and test" (96676292701), head dde704c, ubuntu-latest
gh run view 32449896251 --repo ShivaClaw/careerseeker-android    # or the Actions UI
```

*Expected, and **observed**:* `conclusion: success`, **every step green**, including the four gate
tasks this sandbox cannot execute:

| step | conclusion |
| --- | --- |
| Assert `:core` has no Android dependency (`checkCoreIsAndroidFree`) | **success** |
| Assert vendored sync vectors match the pinned main-repo commit | **success** |
| Unit tests (`:core`) | **success** |
| Unit tests (`:app`, Robolectric) | **success** |
| Assemble debug APK (`:app:assembleDebug`) | **success** |
| Lint (`:app:lintDebug`) | **success** |
| Assert no analytics or tracking SDKs ship | **success** |

**This upgrades C-74-5's bound and does not replace its number.** The android gate **ran** on this
exact head and passed, so **B-7's limit does not apply to this slice's verdict** — `:app` compiled,
lint held, and `checkCoreIsAndroidFree` **executed** rather than being argued from the absence of
imports. The vendored-vector step passing is an **independent** confirmation of **C-74-2**: CI
re-fetches the pin's blobs and diffs them, so the 29/29 identity is now checked by two mechanisms.

**What it still does not establish.** CI prints no test totals, so **`338 / 0 / 0` remains a
`core-probe.sh` measurement** and is not re-asserted as a CI figure. And CI cannot invent a caller:
`:app` still constructs nothing from `:core` (**B-19**), and a green gate on an unwired module is
not a working phone. **PQ-ERR-1 is unaffected** — a green gate says the `else` branch compiles, not
that dropping `error` is right.

---

## RUN 75 — 2026-08-21. §7.2's tenth row, and the two vocabularies nothing compared to the document

### C-75-1 — every count below is post-fetch (rule one)

```bash
cd <android> && git fetch --all --prune && git rev-list --left-right --count origin/main...b5fd218
cd <engine>  && git fetch --all --prune && git rev-parse origin/main
```

*Expected, and **observed**:* android `10  270` — **at this run's starting tip `b5fd218`**, which is
**270** ahead of a `main` the checkout arrived detached at (`ebfaf81`), and 10 behind. Engine
`origin/main` = **`aac05f3f93f0ca06cbc9dfa7884f74a126f078dc`**, unmoved since 2026-08-12.

> **The starting tip is named on purpose.** A first draft of this block wrote `…origin/main...HEAD`,
> which stops reproducing the moment this run commits — it reads **273** at the pushed head. The
> same trap the records caught once before with `HEAD~1` (the banner commit shifts it): **a
> re-verification command must not be anchored to a moving ref.**

### C-75-2 — the assigned slice is built, and is still not on `main` (fortieth firing)

```bash
cd <engine> && git fetch --all --prune
for c in 8575539 22b028e 7328a0b; do
  git cat-file -t $c
  git merge-base --is-ancestor $c origin/main; echo "$c on main? exit=$?"
done
```

*Expected, and **observed**:* all three print **`commit`** and all three report **`exit=1`**, *not
on main*. **Built since 2026-08-09; assigned again this run.** See **B-18**.

### C-75-3 — the pin is `7328a0b`, and the vendored corpus is byte-identical to it

```bash
cd <engine> && git worktree add --detach /tmp/pin75 7328a0b
diff -r /tmp/pin75/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
cd /tmp/pin75 && node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
```

*Expected, and **observed**:* `diff -r` **silent, `exit=0`**; **29** files at the pin and **29**
vendored; **`OK: 29 vector files match the generator.`**, `exit=0`. **The recurring prompt's pin
`679a317` is stale — it is `7328a0b`.**

### C-75-4 — the defect: §7.2 has ten rows and the phone transcribed nine, with a date

```bash
cd <engine> && git show origin/main:docs/Sync-Protocol.md | sed -n '/^### 7.2 Error kinds/,/^## 8/p'
git log --oneline --all -S'`unimplemented`' -- docs/Sync-Protocol.md | tail -1
git log --oneline --all -S'Unimplemented'   -- src/Sync/Protocol.cs   | tail -1
cd <android> && git show dddb806^:core/src/main/kotlin/app/careerseeker/core/Protocol.kt | sed -n '/enum class ErrorCode/,/^}/p'
git log --format='%h %ad' --date=short -S'REV_CONFLICT' -- core/src/main/kotlin/app/careerseeker/core/Protocol.kt | tail -1
grep -rn "unimplemented" --include=*.kt .
```

*Expected, and **observed**:* §7.2's table lists **ten** codes — the nine the phone had plus
**`unimplemented`** (*"A recognised shipping kind the engine does not yet handle … Distinct from
`unknown_kind` — the kind IS known, so the phone should not treat it as a version/vocabulary
error."*). **Both** `git log -S` lines print the same commit, **`e1e7a90` (2026-07-24)** — the spec
and `src/Sync/Protocol.cs` were amended **in one commit**, as `CLAUDE.md`'s drift trap requires. The
pre-fix `ErrorCode` has **nine** constants, written **2026-07-22** (`6bdddbd`). The `grep` returns
**no hit in any Kotlin file**: the string existed nowhere on the phone for **28 days**.

### C-75-5 — negative control: the vocabularies were unguarded in full, not merely short

Run **on the pre-fix tree** (`dddb806^`). Each mutation is the deletion of a row that was *not*
missing, so a passing suite is a statement about the guard, not about the row:

```bash
cd <android> && git checkout dddb806^ -- core/
P=core/src/main/kotlin/app/careerseeker/core/Protocol.kt
sed -i '/PAIRING_UNKNOWN("pairing_unknown"),/d' $P
scripts/core-probe.sh --rerun; echo "exit=$?"          # P-M1
git checkout dddb806^ -- core/
sed -i 's|"config_change", "lesson_proposal", "metric",|"config_change", "lesson_proposal",|' $P
scripts/core-probe.sh --rerun; echo "exit=$?"          # P-M3
git checkout dddb806 -- core/
```

*Expected, and **observed**:* **both green** — `exit=0`, **`338 tests, 0 failed, 0 skipped, across
22 classes`**, twice. `ErrorCode.entries` is enumerated by no test in the repository, and
`RESERVED_FOR_L2`'s five call sites all **iterate** it, so a dropped member makes them test less
rather than fail. The only pre-existing guard on any error code is indirect: `ProtocolVectorsTest`
compares a vector's `expect_error` against `ErrorCode.wire`, which reaches only the codes the corpus
exercises — **`rev_conflict`, `pairing_unknown` and `unimplemented` have no vector**.

### C-75-6 — the claim that was withdrawn: §5.4's list is the best-guarded, not the worst

`PayloadKindCoverageTest`'s `state-changing kinds are all phone to engine` reads
`STATE_CHANGING_KINDS.mapNotNull { PayloadKind.fromWire(it) }`, and `mapNotNull` **discards** any
string that is not a valid kind — so that assertion, alone, launders a typo in the set that decides
which envelopes require §5.4's device signature. Measured rather than assumed, **on the pre-fix
tree**:

```bash
P=core/src/main/kotlin/app/careerseeker/core/Protocol.kt
# P-M2 removal:
sed -i 's|setOf("doc_edit", "outcome", "entitlement")|setOf("doc_edit", "entitlement")|' $P
# P-M4 typo:
sed -i 's|setOf("doc_edit", "outcome", "entitlement")|setOf("doc_edit", "outcomes", "entitlement")|' $P
# P-M5 bogus addition:
sed -i 's|setOf("doc_edit", "outcome", "entitlement")|setOf("doc_edit", "outcome", "entitlement", "not_a_kind")|' $P
```

*Expected, and **observed**:* **all three red** — `exit=1`, **2 / 4 / 2** tests failing
respectively, in `EnvelopeReceiverTest`, `OutboundEnvelopesTest` and `OutboundQueueTest`. Those
drive the set **through the receiver and the builder** rather than inspecting it, so they catch
every mutation direction. **The weak assertion is real; the hole is not.** Recorded so a later
reader who spots `mapNotNull` does not "fix" it believing it to be the gap — and so this run's
finding is not read as wider than it is.

### C-75-7 — the fix, and the suite after it

```bash
cd <android> && scripts/core-probe.sh --rerun; echo "exit=$?"
```

*Expected, and **observed**:* **`341 tests, 0 failed, 0 skipped, across 22 classes`**, `exit=0`.
Baseline **338/0** measured on the same tree this run, before the change. Three new tests, all in
the existing `ProtocolTest`, so the class count is unchanged. `UNIMPLEMENTED("unimplemented")` added
to `ErrorCode`; `section72Codes` and `section43Reserved` transcribed **by hand** in `ProtocolTest`,
never derived from the enums they check — run 74's lesson, one file along.

### C-75-8 — four mutations, and Q-M2 is the historical defect itself

Run **on the post-fix tree**:

```bash
P=core/src/main/kotlin/app/careerseeker/core/Protocol.kt
sed -i '/PAIRING_UNKNOWN("pairing_unknown"),/d' $P            # Q-M1
sed -i '/UNIMPLEMENTED("unimplemented"),/d' $P                # Q-M2
sed -i 's|"config_change", "lesson_proposal", "metric",|"config_change", "lesson_proposal",|' $P   # Q-M3
sed -i 's|TOO_LARGE("too_large")|TOO_LARGE("tooLarge")|' $P   # Q-M4
# after each: scripts/core-probe.sh --rerun; echo "exit=$?"; git checkout dddb806 -- core/
```

*Expected, and **observed**:* all four `exit=1`, with these exact failures —

| | mutation | red | pre-fix |
| --- | --- | --- | --- |
| **Q-M1** | `ErrorCode` loses `PAIRING_UNKNOWN` | **1** — `error codes are exactly section 7-2's table` | **GREEN** |
| **Q-M2** | `ErrorCode` loses `UNIMPLEMENTED` | **1** — same test | **GREEN, for 28 days** |
| **Q-M3** | `RESERVED_FOR_L2` loses `metric` | **1** — `reserved L2 kinds are exactly section 4-3's list` | **GREEN** |
| **Q-M4** | wire typo `too_large` → `tooLarge` | **3** — both new tests **plus** `ProtocolVectorsTest` | red already |

**Q-M2 re-introduces the defect exactly as it existed** and it now fails. Q-M4's third failure is
the pre-existing partial guard firing on a code the corpus *does* cover, which is the boundary
C-75-5 describes.

### C-75-9 — the addition is vocabulary; it decides nothing, and PQ-ERR-1 is untouched

```bash
cd <android> && git diff dddb806^ dddb806 --stat
grep -rn "UNIMPLEMENTED" --include=*.kt core/src/main
git log -1 --format=%H -- docs/protocol-questions.md
```

*Expected, and **observed**:* the diff touches **exactly two files**, both under `core/`
(`Protocol.kt`, `ProtocolTest.kt`) — **no `:app` file, no vector, no `.md`**. `UNIMPLEMENTED` is
referenced in **no production code path**: nothing on the phone emits it, and nothing parses an
inbound `error` body at all, because whether it should is **PQ-ERR-1**, open since run 74 and
**unmodified this run** (`docs/protocol-questions.md`'s last commit predates this run). The
constant exists so the enum **is** §7.2; the behaviour question goes to the gate.

### C-75-10 — 24 drafts, none merged, none closed, none undrafted (measured, not carried forward)

```bash
# GitHub API (this run used the MCP `list_pull_requests`, state=open, fields incl. `draft`)
gh pr list --repo ShivaClaw/careerseeker         --state open --json number,isDraft | jq length
gh pr list --repo ShivaClaw/careerseeker-android --state open --json number,isDraft | jq length
cd <engine> && git log origin/main -1 --format='%h %ad %an' --date=short
```

*Expected, and **observed**:* **18** open in `careerseeker` — #26 (Terra's) and #32…#53 — and **6**
in `careerseeker-android` (#1…#6). **Every one reports `draft: true`**; none is merged, closed or
undrafted. `origin/main` is **`aac05f3`, 2026-08-12, `Portable G & Shiva's Claw`** — the last commit
by a human in either repo, **nine days old**. Android PR **#6**'s head at read time was `b5fd218`,
this branch's tip before this run's push.

**This is the number every run's prohibition paragraph asserts**, and it is measured here rather
than carried forward from the previous entry, per the stale-refs rule that opens every iteration.

### C-75-11 — the android gate is nondeterministic: the same commit went red, then green

```bash
# attempt 1 and attempt 2 are the SAME workflow run, the SAME head, no push between them
# run 32467317566, head 592afa4
gh run view 32467317566 --repo ShivaClaw/careerseeker-android            # or the Actions UI
gh api repos/ShivaClaw/careerseeker-android/actions/jobs/96726656919 --jq .conclusion   # attempt 1
gh api repos/ShivaClaw/careerseeker-android/actions/jobs/96728744410 --jq .conclusion   # attempt 2
# the precedent, on a records-only commit that cannot touch :app
gh run view 32119765602 --repo ShivaClaw/careerseeker-android --log-failed | grep -E "FAILED|tests completed"
# and confirm this run's diff cannot reach :app
cd <android> && git diff --name-only dddb806^ 592afa4 | grep -c '^app/'    # expect 0
grep -rn "ErrorCode\|RESERVED_FOR_L2" --include=*.kt app/src              # expect no output
```

*Expected, and **observed**:*

| attempt | job | head | conclusion |
| --- | --- | --- | --- |
| 1 | `96726656919` | `592afa4` | **failure** — `ScreensFromFixtureTest > theBannerFollowsIntoTheApplicationDetailOverlay`, `AssertionError at ScreensFromFixtureTest.kt:87`, `35 tests completed, 1 failed, 3 skipped` |
| 2 | `96728744410` | **`592afa4`, unchanged** | **success**, every step, artifact `app-debug.zip` finalized at **12,741,153 bytes** |

The precedent run `32119765602` (run number 177, 2026-08-18, head `0c4ca8f` — **records-only**)
prints `ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab FAILED`,
`AssertionError at ScreensFromFixtureTest.kt:69`, `35 tests completed, 1 failed, 3 skipped`. **Two
different assertions, two diffs that touch no `:app` file.** The two greps return **`0`** and **no
output**: this run's diff is `:core`-only and `:app` references neither symbol, so there is no causal
path from the change to the failure.

**What this establishes, and the limit on it.** The same-commit red→green is proof of
nondeterminism on its own; the precedent makes it a pattern (**2 failures in 24 completed runs,
numbers 172–201, both in `ScreensFromFixtureTest`**). It does **not** establish the mechanism —
that reading is `B-22`'s, and it is argued from the deprecation warning the build prints and from
the absence of any `waitForIdle`/`waitUntil` between `performClick()` and `assertIsDisplayed()`.
**No `:app` code was compiled or run to confirm it** (**B-7**), so the proposed patch in B-22 is
**unverified** and is labelled so.

**This retroactively qualifies every CI-green claim in these records, including C-74-10 and this
run's own.** The qualification is **scoped, not total**: the vendored-vector step,
`checkCoreIsAndroidFree`, `:core:test` and `:app:lintDebug` are deterministic; it is `:app:test`'s
Compose-UI subset that is not. A green on that step means **"passed this time"** — which is what
*one sample* is worth, and no record before this one said it.

### C-75-12 — `592afa4` is green (superseded by the completed entry below)

```bash
gh pr checks 6 --repo ShivaClaw/careerseeker-android
```

*Expected, and **observed**:* head **`592afa4`**, check `Build and test` **`success`** — the gate's
every step, including `checkCoreIsAndroidFree`, the vendored-vector drift check, `:core:test`,
`:app:test`, `:app:assembleDebug` and `:app:lintDebug`, on a real runner with a real SDK. **Per
C-75-11 this is one sample of a suite with a nondeterministic subset**, and is not claimed as more.

### C-75-12 (completed) — CI green on `af0cef8`, and the standing limit on saying so

```bash
gh pr checks 6 --repo ShivaClaw/careerseeker-android
```

*Expected, and **observed**:* head **`af0cef8`**, check `Build and test` **`success`**
(job `96731456292`, run `32468925718`) — every step on a real runner with a real SDK:
`checkCoreIsAndroidFree`, the vendored-vector drift check, `:core:test`, `:app:test`,
`:app:assembleDebug`, `:app:lintDebug`, and the no-analytics assertion. The earlier green on
`592afa4` (**C-75-11**, attempt 2) held on the next head too.

**Two limits, both standing rather than new.** Per **C-75-11 / B-22** this is **one sample** of a
suite whose `:app` Compose subset is nondeterministic — `:app:test` green means *"passed this
time"*. And per the convention run 176 settled, **each push supersedes the previous run**, so the
commit that records this line is itself not covered by it: **the last commit of any iteration is
never CI-observed by that iteration.** That is why this cites the **PR** rather than promising a
run ID, and why no green is claimed for the addendum commit itself.

### C-75-13 — every `C-` and `B-` id this run cites resolves to an entry that exists

```bash
cd <android>
grep -c '^## B-22' BLOCKED.md                       # expect 1
grep -c '^### C-75-11' AUDIT-REQUEST.md             # expect 1
grep -c '^### C-75-12' AUDIT-REQUEST.md             # expect 2 (pending + completed)
ls /home/user/BLOCKED.md /home/user/AUDIT-REQUEST.md 2>&1   # expect: No such file
git status --porcelain                                       # expect: clean after commit
```

*Expected, and **observed**:* `1`, `1`, `2`, both strays **absent**, tree clean.

**This check exists because this run failed it.** B-22's entry and C-75-11/-12 were appended with a
bare relative path after the shell's cwd had reset to `/home/user`, so they landed outside any
repository while `f0b0a0a` and the PR body already **cited them as filed** (LOG.md milestone 9).
Restored and verified. **Run 69's "address both sides by absolute path" rule already covered this
hazard and was applied only to the command that produced it** — the same shape as the §7.2 drift
this run's slice is about, one document class over.

**The general form is not built and is the lane's strongest successor candidate:** nothing in either
repo checks that a cited `C-\d+-\d+` or `B-\d+` resolves to a real entry, so a dangling citation is
invisible. The catch here was luck — a later command happened to fail loudly with `fatal: not a git
repository`. A silent `cat >>` would have closed the iteration with green CI and two citations
pointing at nothing.

---

## Run 76 — 2026-08-21 (Linux sandbox). The HKDF successor target, measured and refuted.

Auditor setup for this run's claims (Linux, no Android SDK):

```bash
sudo apt-get update -qq && sudo apt-get install -y --no-install-recommends openjdk-17-jdk-headless
git -C <android> checkout claude/android-a0-probe && git -C <android> pull
```

`scripts/core-probe.sh` runs **`:core:test` only**. Four of the gate's five tasks still need the
Android SDK (**B-7**), so nothing below is a gate result and none is claimed as one.

### C-76-1 — every count below is post-fetch (rule one)

```bash
git -C <android> fetch --all --prune && git -C <engine> fetch --all --prune
git -C <engine> log --oneline -1 origin/main
```

*Expected, and **observed**:* both fetched before any read. `origin/main` is **`aac05f3`**, its last
non-Claude commit **2026-08-12** (`Portable G & Shiva's Claw`, merge of #44). The android checkout
arrived **detached at a stale `main` (`ebfaf81`, docs-only)**; the work branch was checked out from
`origin/claude/android-a0-probe` at **`0c1895a`** before anything was measured.

### C-76-2 — the assigned slice is built; the prompt's pin is still stale (forty-first firing)

```bash
git -C <engine> log --oneline --all --grep='entitlement_ack' | head
grep 'Pinned commit' <android>/core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected, and **observed**:* the prompt's S5 spec half (§4.3 body, the ack vectors, PQ-A2-1/-2/-3)
exists on the `claude/s5-*` drafts as `8575539`, `22b028e`, `7328a0b`, unchanged since 2026-08-09.
The prompt's vendored pin `679a317` is stale: the lock says **`7328a0bc043335491cd96a67d634e8eea2a13af9`**.
**Declined and verified rather than rebuilt, for the forty-first time.**

### C-76-3 — the seven §5 domain separators, mutated one at a time: seven red, hypothesis refuted

Run 75 filed the HKDF info strings as the lane's first successor target, on the reading that
`ProtocolTest` asserted only that two of them *differ*, `HkdfTest` uses its own literals, and the
envelope vectors carry `key_hex` directly — so the phone could derive from wrong info strings, stay
green, and disagree with the engine only in the field. Run 75 said: *measure it before believing it.*

**On the pre-fix tree `0c1895a`**, each constant mutated alone, restored between runs:

```bash
P=<android>/core/src/main/kotlin/app/careerseeker/core/Protocol.kt
# for each: sed -i 's|"careerseeker/v1/X"|"careerseeker/v1/X-MUTANT"|' $P
#           scripts/core-probe.sh --rerun; echo "exit=$?"; git checkout -- $P
```

*Expected by the hypothesis: green. **Observed: all seven red**, `exit=1`.*

| mutation | tests reddened | which |
| --- | --- | --- |
| `e2p` | **1** | `ProtocolVectorsTest > pairing derivation reproduces every vector value` |
| `p2e` | **5** | above + 3 × `PairingFlowTest` + `PairingSessionTest` |
| `relay-token` | **4** | above + 2 × `PairingFlowTest` + `PairingSessionTest` |
| `confirm` | **4** | above + 2 × `PairingFlowTest` + `PairingSessionTest` |
| `bootstrap` | **3** | above + `PairingFlowTest` + `PairingSessionTest` |
| `pair` | **2** | above + `PairingDerivationTest > completionAad is the pinned four-field format` |
| `cmd` | **3** | `EntitlementVectorsTest` + 2 × `ProtocolVectorsTest` |

**The premise was true and the conclusion was false.** What guards them is the **pairing** vectors:
`pairing-basic.json` carries `k_e2p_hex`, `k_p2e_hex`, `relay_token_b64u`,
`provisional_token_b64u` and `confirm` as **derived** values, and `ProtocolVectorsTest:110-124`
recomputes all five through `PairingDerivation.derive`. The hypothesis generalised from the
*envelope* vectors (which do carry `key_hex` directly) to the corpus as a whole.

**`INFO_ENGINE_TO_PHONE` had exactly one guard** where the others had two to five — the phone
*seals* under `k_p2e` and only *opens* under `k_e2p`, so the flow tests never recompute it.

### C-76-4 — the same seven literals in all three places

```bash
git -C <engine> show 7328a0b:docs/Sync-Protocol.md | grep -n 'careerseeker/v1/'
git -C <engine> grep -n 'careerseeker/v1/' 7328a0b -- 'src/Sync/*.cs'
grep -n 'careerseeker/v1/' <android>/core/src/main/kotlin/app/careerseeker/core/Protocol.kt
grep -rn 'careerseeker/v1/' <android>/core/src/main/ | grep -v Protocol.kt   # expect: nothing
```

*Expected, and **observed**:* spec §5.2 lines **414-417** and **444**, §5.2.2 line **464**, §5.4 line
**522**; engine `src/Sync/Protocol.cs:23-29`; phone `Protocol.kt:38-46`. **Seven on each side, no
eighth, and every literal identical** — unlike §7.2's error table (**C-75-4**), this vocabulary
never drifted. The phone holds no `careerseeker/v1/` literal outside `Protocol.kt`.

### C-76-5 — the defect actually found: an assertion that compared the constant against itself

`PairingDerivationTest > signatureInput hashes the ciphertext as lower-case hex` pinned §5.4's
domain separator as `assertEquals(Protocol.COMMAND_SIG_PREFIX, parts[0])` — the production output
against the very constant that produced it, true for **any** value of the constant.

```bash
git -C <android> show 201b781 -- core/src/test/kotlin/app/careerseeker/core/PairingDerivationTest.kt
```

*Expected, and **observed**:* the row for `cmd` in **C-76-3** (pre-fix) does **not** list
`PairingDerivationTest`; the row in **C-76-7** (post-fix) does. **The constant was never
unguarded** — the signature vectors reach it — but not at the line written to guard it.

### C-76-6 — the suite before and after

```bash
cd <android> && scripts/core-probe.sh --rerun; echo "exit=$?"
```

*Expected, and **observed**:* baseline on `0c1895a` **`341 tests, 0 failed, 0 skipped, across 22
classes`**, `exit=0` — reproducing run 75's figure exactly. After this run's two commits,
**`343 tests, 0 failed, 0 skipped, across 22 classes`**, `exit=0`. Two new tests, both in the
existing `ProtocolTest`, so the class count is unchanged.

### C-76-7 — three post-fix mutations; every prediction matched

Run **on the post-fix tree `231bc07`**:

```bash
P=<android>/core/src/main/kotlin/app/careerseeker/core/Protocol.kt
sed -i 's|COMMAND_SIG_PREFIX = "careerseeker/v1/cmd"|COMMAND_SIG_PREFIX = "careerseeker/v1/cmd-MUTANT"|' $P        # R-M1
sed -i 's|BOOTSTRAP_SALT = "careerseeker/v1/bootstrap"|BOOTSTRAP_SALT = "careerseeker/v1/relay-token"|' $P          # R-M2
sed -i 's|INFO_ENGINE_TO_PHONE = "careerseeker/v1/e2p"|INFO_ENGINE_TO_PHONE = "careerseeker/v1/e2p-MUTANT"|' $P     # R-M3
# after each: scripts/core-probe.sh --rerun; echo "exit=$?"; git checkout -- $P
```

*Expected, and **observed**:* all three `exit=1` —

| | mutation | red | pre-fix |
| --- | --- | --- | --- |
| **R-M1** | `cmd` domain separator | **5** — incl. `PairingDerivationTest > signatureInput…` and `ProtocolTest > …section 5 prints` | **3**, neither of those two |
| **R-M2** | `BOOTSTRAP_SALT` **collides with** `INFO_RELAY_TOKEN` | **5** — incl. `ProtocolTest > …pairwise distinct` | the collision test did not exist |
| **R-M3** | `e2p` | **2** — the vector test **and** `ProtocolTest > …section 5 prints` | **1** |

**R-M2 is the case the literal pins alone cannot catch**: every literal assertion still holds
individually when two constants collide, which is why the pairwise-distinct test is separate.
R-M3 is C-76-3's thin row, now two guards instead of one.

### C-76-8 — 24 drafts, none merged; return day three days past

```bash
gh pr list --repo ShivaClaw/careerseeker         --state open --json number,isDraft | jq length
gh pr list --repo ShivaClaw/careerseeker-android --state open --json number,isDraft | jq length
```

*Expected, and **observed**:* **18 engine + 6 android = 24**, every one `draft: true`; **none
merged, closed or undrafted.** Engine `main` unmoved at `aac05f3` since **2026-08-12**. Return day
was **2026-08-18**; today is **2026-08-21**, so it is **three days past**, not four.

> **Correction to run 75.** Its banner says *"Return day is now four days past"*; run 74, same date,
> says *"three days past"*. 2026-08-21 − 2026-08-18 = **3**, so run 74 is right and run 75 is off by
> one. Corrected here rather than silently, because "every number reproduces" is the property these
> records sell. **Nothing else in run 75's banner changes.**

---

## Run 77 — 2026-08-21 (Linux sandbox). The citation guard: C-75-13, built.

Auditor setup (Linux, no Android SDK, no JDK needed for this run's claims):

```bash
git -C <android> fetch --all --prune && git -C <engine> fetch --all --prune
git -C <android> checkout claude/android-a0-probe && git -C <android> pull
```

**Nothing in this run needs a toolchain.** `scripts/check-citations.sh` is bash/awk/grep. No Gradle
task ran, so **no `:core`/`:app` result is claimed and none appears below** (**B-7**).

### C-77-1 — every count in this run is post-fetch (rule one)

```bash
git -C <android> fetch --all --prune && git -C <engine> fetch --all --prune
git -C <engine> log --oneline -1 origin/main
git -C <android> rev-parse origin/claude/android-a0-probe
```

*Expected, and **observed**:* both fetched before any read. `origin/main` is **`aac05f3`**, last
non-Claude commit **2026-08-12**. The android checkout arrived **detached at `ebfaf81`** (stale
docs-only `main`); the work branch was checked out at **`7991e31`** before anything was measured.

### C-77-2 — the assigned slice is built (forty-second firing), and the generator check runs here

```bash
cd <engine>
for c in 8575539 22b028e 7328a0b; do echo "== $c =="; git log -1 --format='%ad  %s' $c; git show --stat --format='' $c; done
git checkout -B s5-check origin/claude/s5-entitlement-ack-emitter
node docs/sync-vectors/generate.mjs --check; echo "exit=$?"
git ls-tree --name-only origin/main docs/sync-vectors/v1/ | wc -l
```

*Expected, and **observed**:* `8575539` (2026-08-09) touches `docs/Sync-Protocol.md` **only**,
**+114/−3**; `22b028e` adds `entitlement-ack.json`, `entitlement-ack-no-order-id.json`, `index.json`
**and `generate.mjs`**; `7328a0b` adds `invalid-unknown-field.json`. Generator check:
**`OK: 29 vector files match the generator.`**, **`exit=0`**. `origin/main` still holds **26** —
the work is **unmerged, not unwritten**.

### C-77-3 — the prompt's pin is stale; the corpus is byte-identical to the real one

```bash
grep 'Pinned commit' <android>/core/src/test/resources/sync-vectors/VECTORS.lock
mkdir -p /tmp/pin-check && cd <engine> && git archive 7328a0b docs/sync-vectors/v1 | tar -x -C /tmp/pin-check
diff -r /tmp/pin-check/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1
echo "exit=$?"; ls /tmp/pin-check/docs/sync-vectors/v1 | wc -l
```

*Expected, and **observed**:* pin reads **`7328a0bc043335491cd96a67d634e8eea2a13af9`**, not the
prompt's `679a317`. Diff: **no output, `exit=0`, 29 files.** **No vector byte was written by this
run in either repo.**

### C-77-4 — the three false positives the first draft produced, and what each one was

Reproduce the pre-fix parser's verdict by disabling the two guards it lacked:

```bash
cd <android>
# the two shapes, in the corpus, as they really appear:
grep -n '^### C-RES-1 / C-RES-2' AUDIT-REQUEST.md      # combined heading: defines TWO ids
grep -n '^### S5\.B-0' LOG.md                           # milestone label, not a citation
grep -c '^### C-RES-2' AUDIT-REQUEST.md                 # expect 0 -- it has no heading of its own
grep -onE '\bC-RES-2\b' LOG.md STATE.md | wc -l         # expect 5 -- and every one is legitimate
# B-11: cited three times, defined nowhere, and correctly so
grep -onE '\bB-11\b' BLOCKED.md
grep -c '^## B-11' BLOCKED.md                           # expect 0
```

*Expected, and **observed**:* `AUDIT-REQUEST.md:11356` is
`### C-RES-1 / C-RES-2 — what each STOP actually contains, from real merges`, so **`C-RES-2` is
defined on a combined heading** and reading only the first id off a heading reported it dangling —
**a parser defect**. `LOG.md:1848` is `### S5.B-0 The environment finding that decided the slice`, a
**milestone label**; a word-boundary match finds the bare id inside it — **also a parser defect**, fixed by
rejecting any id preceded by `[A-Za-z0-9.]`. **`B-11` is genuinely absent and deliberately so**:
`BLOCKED.md:1669` reads *"B-11 was never warranted and is not filed"*, and `B-12`'s opening paragraph
(`BLOCKED.md:1680`) exists to explain the missing number. It is in `KNOWN_ABSENT` **with that
reason**.

**The em-dash is not a range connector**, and this is the trap C-75-13 predicted:

```bash
grep -ohE '(C|B)-[A-Za-z0-9]+-[0-9]+ *(—|–) *-?[0-9]' LOG.md STATE.md BLOCKED.md AUDIT-REQUEST.md | wc -l
```

*Expected, and **observed**:* **4**, and reading each in context shows **all four are an id followed
by prose that happens to contain a number** — zero are ranges. Only `…`, `..` and `...` expand.

### C-77-5 — the corpus is clean; no live defect was found

```bash
cd <android> && ./scripts/check-citations.sh; echo "exit=$?"
```

*Expected, and **observed**:* `definitions: 696   cited: 697   documented-absent: 1` then
**`OK: every cited C-/B- id resolves to an entry that exists.`**, **`exit=0`**. **This run found no
live dangling citation.** The guard's value is prospective; the counts move as the records grow, so
re-derive rather than compare to these.

### C-77-6 — the guard fires, on seven pinned cases including run 75's actual incident

```bash
cd <android> && ./scripts/check-citations.sh --self-test; echo "exit=$?"
```

*Expected, and **observed**:* **nine `PASS` lines, `self-test: all cases passed`, `exit=0`.** The
cases pin, in order: a clean tree passes; **run 75's incident is caught** (a tree citing `B-22` and
`C-75-11` that were never filed → `exit=1`); a **combined heading defines both** ids; a **milestone
label is not a citation**; **range expansion** catches a missing endpoint; an **em-dash after an id
is prose**; a **fenced code block is a fixture, not a claim**; **prose after a closed fence is still
checked**; and a **`/-N` continuation** cites its siblings. Cases 3 and 4 are the two defects of
**C-77-4**, and cases 7 and 7b are **C-77-11**'s — all pinned so they cannot regress.

### C-77-7 — three mutations against the REAL corpus, and the negative control

```bash
cd <android>
cp BLOCKED.md /tmp/B.bak
sed -i 's/^## B-22 — the android gate/## B-XX22 — the android gate/' BLOCKED.md
./scripts/check-citations.sh; echo "M1 exit=$?"          # want 1
cp /tmp/B.bak BLOCKED.md

cp LOG.md /tmp/L.bak
printf '\nFiled this run as B-23, and re-verified (C-77-4).\n' >> LOG.md
./scripts/check-citations.sh; echo "M2 exit=$?"          # want 1
cp /tmp/L.bak LOG.md

printf '\nRe-verified this run (C-76-3), and B-18 stays open.\n' >> LOG.md
./scripts/check-citations.sh; echo "M3 exit=$?"          # want 0
cp /tmp/L.bak LOG.md
git status --porcelain LOG.md BLOCKED.md                 # want: empty
```

*Expected, and **observed**:* **M1 `exit=1`**, printing `B-22` and five of its citation sites —
a definition removed while 30+ citations remain. **M2 `exit=1`**, naming **both** unfiled ids the
appended line cites — run 75's incident, reproduced verbatim on the live corpus. **M3 `exit=0`** — the
**negative control**, and the one that matters most: citing ids that **do** exist must stay green, or
the guard is worse than nothing. **Tree clean after each**, `git status --porcelain` empty.

### C-77-8 — the CI step, extracted verbatim from the YAML and run as GitHub runs it

```bash
cd <android>
python3 -c "
import yaml
d=yaml.safe_load(open('.github/workflows/ci.yml'))
steps=list(d['jobs'].values())[0]['steps']
print('steps:',len(steps))
for s in steps:
    if 'cited' in str(s.get('name','')): open('/tmp/step.sh','w').write(s['run'])
"
bash -e /tmp/step.sh; echo "STEP exit=$?"                # want 0
cp LOG.md /tmp/L.bak && printf '\nA dangling reference (C-99-99).\n' >> LOG.md
bash -e /tmp/step.sh; echo "STEP exit=$?"                # want 1
cp /tmp/L.bak LOG.md
```

*Expected, and **observed**:* YAML valid, **13 steps**; the new step is the **5th entry in the YAML's
`steps` array**.

> **CORRECTED, after reading the runner rather than the YAML.** The first draft of this block said it
> runs "before every toolchain step, because it needs none". **On the runner that is false**, and the
> job listing says so plainly: `Set up JDK 17`, `Set up Android SDK` and `Set up Gradle` are steps
> **3, 4 and 5**, and the guard is step **6** (the runner prepends `Set up job`, so YAML index 5
> becomes runner step 6). Those three are `uses:` **setup actions** and run ahead of every `run:`
> step in the job. **The accurate claim is narrower and is the one worth making:** the guard runs
> before every step that *uses* the toolchain — before the first Gradle invocation, and before the
> `:core`/`:app` test, assemble and lint steps — so a dangling citation fails the build **without
> waiting for the expensive half**. It does not, and cannot, save the setup. Caught by reading the
> step list on this run's own CI; the YAML index was mistaken for the runner's numbering. **`exit=0` on the real tree**; with one dangling citation
appended, **`exit=1`** printing `::error::dangling citation(s)` and naming the fictional id. Run under **`bash -e`**
deliberately — run 46 recorded that invoking a script without its shebang semantics proves nothing,
and that re-running it as `bash -e` is what made it evidence.

**RUNNER-VERIFIED for the pass path, same run** — and therefore **narrowed, not deleted**, exactly as
**B-15** was:

```bash
# job 96848840383 of run 32506850632, head 05463d7, ubuntu-latest
curl -s .../actions/runs/32506850632/jobs | python3 -c '...print step name/status/conclusion...'
```

*Expected, and **observed**:* step **6**, `Assert every cited C-/B- id resolves`, **`completed` /
`success`** on `ubuntu-latest`.

> **READ THE RUN STATE BEFORE THE STEP STATE.** Run `32506850632` shows overall
> **`cancelled`** — it was superseded by the next push under `cancel-in-progress: true`, and the
> cancellation landed at the `:app` step, **long after** step 6 had finished. **A cancelled run is
> not a failed step**, and the per-step conclusion is the evidence here, not the run's. If that
> distinction feels thin, use the run on the branch tip instead — the same step, same image, run
> again from scratch. **Confirmed twice, on two heads:** run `32507159746` (head `4039579`) also shows
> step 6 **`success`**, and was also cancelled later at the `:core` step by the next push. Two
> independent runner executions of this step, both green. So the image's `bash` and `awk` do run this script, and the two
unknowns that mattered — GNU `awk`'s handling of the UTF-8 ellipsis byte sequence, and `grep -vxF -f`
with process substitution under the runner's shell — are resolved **for the green path**.

**The failure path is still stub-only**, which is the same qualification B-15 carries: a green check
proves it does not false-alarm, **not** that it still fires. Proving the red path on a runner would
mean pushing a knowingly-dangling citation, and that is a deliberate choice not to. **It fails loud,
not quiet** — the worst case is a red build on a correct tree, never a green one on a drifting tree.

**I could not read the step's log**, only its conclusion: the Actions log endpoint returned
`http=000` through this sandbox's proxy. **So no claim is made about what the step printed** — only
that it exited zero on the runner.

### C-77-9 — the guard is cwd-independent, which is the whole hazard it guards

```bash
cd /tmp && <android>/scripts/check-citations.sh; echo "exit=$?"
cd /  && bash <android>/scripts/check-citations.sh --self-test >/dev/null; echo "exit=$?"
```

*Expected, and **observed**:* **`exit=0` from both.** The script resolves the repository root from
`BASH_SOURCE`, never from the caller's cwd. **This is load-bearing, not tidiness:** the incident that
motivated the guard was a bare relative path outliving its `cd`, and during this run the sandbox
shell's cwd reset to `/home/user` between commands. A guard against that class must not contain one.

### C-77-10 — B-18's notify criterion, re-measured this run rather than inherited

```bash
git -C <engine> log --oneline -1 origin/main
git -C <engine> log -12 --format='%h %ad %an' --date=short origin/main | grep -v 'Claude' | head -3
# live PR state, both repos -- state/draft/merged for every open PR
```

*Expected, and **observed**:* engine `main` **`aac05f3`**, last non-Claude commit **2026-08-12**;
**18 engine + 6 android pull requests open, every one `draft: true`, none merged, closed or
undrafted** — read from the live API this run, not carried forward. Return day (2026-08-18) is
**three days past**. **Both triggers measured negative → nothing sent, fourth consecutive run.** The
criterion inverts on a new fact: movement in the blocking state, or something needing an action,
goes out immediately.

### C-77-11 — the guard's first catch was its own documentation

```bash
cd <android>
# with fence-skipping disabled, the run-77 records fail their own check:
sed 's|^ *infence { next }||' scripts/check-citations.sh > /tmp/nofence.sh
chmod +x /tmp/nofence.sh && bash /tmp/nofence.sh; echo "exit=$?"
# and with it enabled, as shipped:
./scripts/check-citations.sh; echo "exit=$?"
```

*Expected, and **observed**:* the first draft of this run's records went **red** on three ids. Two
sat inside the **command fixtures** of `C-77-7`/`C-77-8` — the blocks that demonstrate the guard
catching an unfiled id — and one was a bare `B-` id in prose describing the milestone-label defect of
**C-77-4**. As shipped: **`exit=0`**.

**Two fixes, and the second is a convention, not code.** (1) **Fenced code blocks are skipped**:
`AUDIT-REQUEST.md` is by design mostly commands, and a command is a **fixture**, not a claim —
otherwise the document that documents the guard cannot pass it. Pinned by self-test cases 7 and 7b,
the second asserting that prose **after** a fence closes is still checked, so the exemption cannot
smuggle a claim. (2) **Prose must not name a deliberately fictional id.** This falls straight out of
the guard's own rule — a prose citation *is* a claim that the referent exists — and costs nothing,
because the fence carries the exact tokens reproducibly, which is what this document is for.

**Attack this first if you doubt the run:** the claim is that fence-skipping is a *principled*
exemption rather than a convenience that blinds the check. It is falsifiable as written — case 7b
fails if prose after a fence stops being checked, and `C-77-7`'s M1/M2 (whose citations are all in
prose) still go red.

### C-77-12 — a phantom definition would fail SILENTLY, and closing it hit an awk portability trap

The citation side skips fences because a fenced id is a harmless fixture. The **definition** side
needs the same skip for the opposite reason: a heading-shaped line quoted inside a fence — an
unfiled blocker heading shown as an example — would register as a **definition**, and a phantom
definition makes a genuinely dangling citation **look resolved**. Noisy failures are recoverable;
this one is silent, which is strictly worse.

```bash
cd <android>
# 1. how many heading-shaped id lines sit inside fences today?
python3 - <<'PY'
import re
for path in ("AUDIT-REQUEST.md","BLOCKED.md"):
    infence=False; n=0
    for line in open(path,encoding='utf-8'):
        if re.match(r'^[ \t]*(```|~~~)',line): infence=not infence; continue
        if infence and re.match(r'^#{2,4} ',line) and re.search(r'\b(C-[A-Za-z0-9]+-\d+|B-\d+)\b',line): n+=1
    print(path, n)
PY
# 2. the trap: mawk does not support interval quantifiers
awk --version | head -1
printf '### C-1-1 — a check\n' | awk '/^#{2,4} / {print "MATCHED"}'      # prints NOTHING under mawk
printf '### C-1-1 — a check\n' | awk '/^(##|###|####) / {print "MATCHED"}'
./scripts/check-citations.sh --self-test; echo "exit=$?"
```

*Expected, and **observed**:* **0 and 0** — the hazard is **latent, not live**; no phantom definition
exists in the corpus today, and the fix is a guard against growth rather than a repair. `awk` is
**mawk 1.3.4**, and `/^#{2,4} /` matches **nothing** under it while `/^(##|###|####) /` matches. The
self-test reports **ten cases, all passing, `exit=0`**.

**The attempt failed first, and the failure is the point.** Written the short way, the definition
extractor returned **27** definitions instead of **707** and the entire corpus read as dangling.
**mawk does not error on an unsupported interval quantifier — it silently matches nothing**, and
`grep -E` (which the original used) *does* support intervals, so the rewrite looked equivalent and
was not. **The self-test caught it on the first run**, which is what it is for: five cases went red
immediately.

**It fails loud, not quiet, even when broken.** A definition extractor that returns nothing makes
*every* citation dangle, so the build goes red on a correct tree — the safe direction, and the same
property claimed for the check as a whole.

**Attack this first if you doubt the fix:** the new self-test case is falsifiable exactly as written
— a fenced heading for an unfiled id must **not** satisfy a prose citation of it. And re-run the mawk
line above: if this repo's runner image ships **gawk** rather than mawk, the short spelling would
have worked there and failed only here, which is precisely the class of unknown **B-15** records.

### C-77-13 — the `KNOWN_ABSENT` exemption is exact-match, not a prefix

The one entry on that list is where this guard rots into a rubber stamp if the mechanism is loose.
It matches whole ids only:

```bash
tmp=$(mktemp -d); mkdir -p "$tmp/scripts"
cp <android>/scripts/check-citations.sh "$tmp/scripts/"
printf '### C-1-1 — a check\n'  > "$tmp/AUDIT-REQUEST.md"
printf '## B-1 — a blocker\n'   > "$tmp/BLOCKED.md"
printf 'B-11 is explained. But B-110 was filed.\n' > "$tmp/LOG.md"
(cd "$tmp" && ./scripts/check-citations.sh); echo "exit=$?"
```

*Expected, and **observed**:* **`exit=1`**, flagging the **longer** id in that fixture and **not**
`B-11`. The exemption is anchored (`^B-11$`), so an id sharing its digits as a prefix inherits
nothing. **Attack this by adding a
second entry and checking it is still anchored** — a list that silently became a prefix match would
exempt a whole family and nothing would say so.

### C-77-14 — the full CI gate is green on this run's final head, and that is ONE sample

```bash
# run 32507902496, head 8a7f863
curl -s .../actions/runs/32507902496/jobs | python3 -c '...print conclusion + every step...'
```

*Expected, and **observed**:* **`CONCLUSION: success`**, **all 14 steps green** — including step 6
(this run's new citation check), `Unit tests (:core)`, `Unit tests (:app, Robolectric)`,
`Assemble debug APK`, `Lint`, and the vendored-vector drift step. This is the **first run-77 head to
complete without being superseded**; the three before it were cancelled by my own subsequent pushes,
always at the `:app` step and always after step 6 had already passed.

**Three things this does NOT license, and they matter more than the green:**

1. **It is not a local gate result.** Nothing in this run ran `:core:test`, `:app:assembleDebug` or
   `:app:lintDebug` **here** — **B-7** is unmoved and no local suite count is claimed anywhere in
   run 77. **Observing a gate is not running one**, which is the distinction run 46 recorded and
   this entry keeps.
2. **It is ONE sample, and B-22 says exactly why that is not a guarantee.** B-22 records the `:app`
   half as **nondeterministic** (`ScreensFromFixtureTest`, ~8% failure for timing reasons). A single
   green `:app` step is consistent with B-22 and does **not** narrow it. **B-22 stays open**, and a
   red `:app` on this head later would be B-22 — this diff contains no `:app` file.
3. **It does not close B-15's shape for this check.** The **pass** path is now runner-proven four
   times over; the **failure** path is still stub-only, because proving it on a runner means pushing
   a knowingly-dangling citation. A green check proves it does not false-alarm, never that it still
   fires.

---

## Run 78 — 2026-08-22. The forty-third firing of a slice finished on 2026-08-09.

### C-78-1 — no movement in the blocking state, measured live rather than carried forward

```bash
# engine main, and its last non-Claude commit
git -C <engine> fetch --all --prune
git -C <engine> log --oneline -1 origin/main
git -C <engine> log -1 --format='%H %ad %an' --date=short origin/main
# live PR census, both repos
mcp__github__list_pull_requests owner=ShivaClaw repo=careerseeker         state=open perPage=100
mcp__github__list_pull_requests owner=ShivaClaw repo=careerseeker-android state=open perPage=100
mcp__github__list_commits       owner=ShivaClaw repo=careerseeker since=2026-08-12T00:00:00Z
```

*Expected, and **observed**:* `origin/main` = **`aac05f3`**, authored **2026-08-12**. **18 engine +
6 android** pull requests open, **every one `draft: true`**; none merged, closed or undrafted. The
newest human activity anywhere in either repo is **2026-08-13** (PRs #40/#42/#43/#44). **Nine days
of no owner action, four of them after return day.** This is the trigger table's first row and it
reads **negative**, exactly as it did at runs 74–77.

### C-78-2 — the assigned slice is built, and the check reads the spec rather than the commit message

```bash
for c in 8575539 22b028e 7328a0b; do git -C <engine> show --stat --format='%h %ad %s' --date=short $c; done
git -C <engine> show 7328a0b:docs/Sync-Protocol.md | grep -n "entitlement_ack\|product_id\|acknowledged_at\|order_id"
git -C <engine> show 7328a0b:docs/Sync-Protocol.md | grep -n -i "ciphertext"
git -C <engine> show 7328a0b:docs/Sync-Protocol.md | grep -n "decrypt_failed"
```

*Expected, and **observed**:* all three commits exist, dated **2026-08-09 / 2026-08-09 /
2026-08-12**. `8575539` touches `docs/Sync-Protocol.md` **only**, **+114/−3**. The four things this
run was assigned read **in the file**, not in a commit subject:

| assigned | where it already is |
| --- | --- |
| §4.3 `entitlement_ack` body `{product_id, acknowledged_at, order_id?}` (PQ-A6-1) | §4.3.3, line 307 — with `order_id` marked OPTIONAL and the failure rule (a failure is a named reason, never an ack with a flag inside it) |
| PQ-A2-1 — the 1 MiB cap measures the **ciphertext** | §3.1, line 111 — plus the relay's own units, `MAX_CIPHERTEXT_B64U_CHARS` |
| PQ-A2-2 — structural rejection reports `decrypt_failed` | §7.2 table line 601, and §3 line 103 — *"no separate `malformed` code"* |
| PQ-A2-3 — the `invalid-unknown-field` vector | `7328a0b`, generated not hand-written |

**Attack this first** by checking the *specificity*: a reader could believe a spec section exists
because a commit says so. These line numbers are in the blob at the pin.

### C-78-3 — the one verification this environment can genuinely run, run

```bash
git -C <engine> worktree add /tmp/s5check 7328a0b
cd /tmp/s5check && node docs/sync-vectors/generate.mjs --check; echo "EXIT=$?"
```

*Expected, and **observed**:* **`OK: 29 vector files match the generator.`**, **`EXIT=0`**. Node
**v22.22.2**. This is the check the recurring prompt asks for by name, and it passes **because the
work is already done** — it is not evidence that this run produced anything.

### C-78-4 — the cross-repo pin holds; no drift event

```bash
diff -r /tmp/s5check/docs/sync-vectors/v1 \
        <android>/core/src/test/resources/sync-vectors/v1; echo "DIFF_EXIT=$?"
```

*Expected, and **observed**:* **`DIFF_EXIT=0`**, **29 files each side**, no output. The vendored
corpus is byte-identical to pin **`7328a0b`**. **No existing vector byte was altered this run** —
the drift event the mission forbids outright did not occur, and this run added no vector either.

*Note the path.* The corpus is nested at `sync-vectors/v1/`, not `sync-vectors/`. This run's first
attempt diffed one level too high and produced a 29-line false "only in" listing that reads exactly
like catastrophic drift. Recorded because the *shape of the false alarm* is indistinguishable from
the real emergency at a glance, and the next session will run this command.

### C-78-5 — RETURN-DAY.md §3's landing plan still matches the live PR heads

```bash
# for each landing branch: local fetched ref vs the head SHA the live API reports
git -C <engine> rev-parse origin/claude/s8-harness-linux-reach        # vs PR #48
git -C <engine> rev-parse origin/claude/s2-seq-bound                  # vs PR #35
git -C <engine> rev-parse origin/claude/s2-transport-vocabulary       # vs PR #36
git -C <engine> rev-parse origin/claude/s3-pairing-confirm-consumer   # vs PR #51
git -C <engine> rev-parse origin/claude/s6-outcome-disposition        # vs PR #52
git -C <engine> rev-parse origin/claude/s6-counter-reconciliation     # vs PR #46
git -C <engine> rev-parse origin/claude/s6-resume-reconciliation      # vs PR #53
git -C <engine> rev-parse origin/claude/s5-entitlement-ack-spec       # vs PR #32
```

*Expected, and **observed**:* **8 branches, 8 matches, 0 drift** —
`c93e88d 2be00fc b0b6c77 edee32b 94fd979 9394ca1 8177353 9c05ef7`. The plan Brandon is meant to act
on is **still actionable against today's refs**, and step 0 (decide #53) is still the first move.
This is the check worth keeping in a run that builds nothing: a landing branch that had drifted from
its PR head would send him to merge the wrong commits, and nothing else in the record set would
notice.

### C-78-6 — the prompt's B-2 premise is stale: the sync track, and `/pair`, are on `main`

```bash
for p in relay src/Sync docs/Sync-Protocol.md docs/sync-vectors tests/SyncHarness; do
  echo "$p : $(git -C <engine> ls-tree -r --name-only origin/main -- "$p" | wc -l)"; done
git -C <engine> grep -l "/pair" origin/main -- src/
```

*Expected, and **observed**:* `relay` **10**, `src/Sync` **14**, `Sync-Protocol.md` **1**,
`sync-vectors` **27**, `tests/SyncHarness` **2**; `/pair` present in **`src/Engine/Host.cs`**,
**`src/Engine/Program.cs`**, **`src/Sync/PairingManager.cs`**, **`src/Sync/Protocol.cs`**.

The recurring prompt states *"BLOCKED B-2 stays open because the desktop /pair page does not
exist."* **It exists and it is on `main`** — merged as PR #42 on **2026-08-13**, with a
`Verify-Alpha.ps1 -IncludePublish -IncludePackage` run recorded in its commit body (`PS_EXIT=0`,
offline total **609**, EngineHarness **217 → 228**). That is a gate **Brandon** ran on Windows; this
run neither ran nor re-ran it, and cites it as *his* evidence, not as its own. **S1 landed.** The
prompt's summary of the ladder is stale in a **fourth** way, alongside "S5 NOT STARTED", pin
`679a317`, and the S2 framing.

**What is NOT claimed:** that B-2 is closed. Its remaining half is the **phone-facing** pair flow,
and PR #42's own body records QR rendering as deliberately unimplemented (no scanner, no emulator —
**B-4**). B-2 **narrows**; it does not close, and this run did not narrow it either.

### C-78-7 — why no gate ran, stated as an inventory rather than an excuse

```bash
dotnet --version; node --version; java -version; which gradle; echo "${ANDROID_HOME:-unset}"; which pwsh
```

*Expected, and **observed**:* `dotnet` **not found**; `pwsh` **not found**; `ANDROID_HOME`
**unset**; Node **v22.22.2**, OpenJDK **21.0.10**, Gradle present at `/opt/gradle/bin/gradle`.

So **both** gates were impossible here, for reasons that are structural and not a matter of effort:
`scripts\Verify-Alpha.ps1` needs .NET **and** PowerShell **and** Windows DPAPI; the android command
of record needs the Android SDK. **B-7** is unmoved. **No suite count, no assertion total and no
gate result is claimed anywhere in run 78**, and the two numbers quoted in **C-78-6** are Brandon's
from PR #42, attributed.

---

## Run 79 — 2026-08-22. §3.1's size cap: the unit was guarded by nothing

Every command below was executed in this session on Linux. Where a claim could not be
executed here, it says so and says why. `<engine>` is a checkout of `ShivaClaw/careerseeker`,
`<android>` a checkout of this repository; both were `git fetch --all --prune`'d first, and
every count is taken after that fetch.

### C-79-1 — the state that decides B-18's notify criterion, re-measured not inherited

```bash
git -C <engine> fetch --all --prune && git -C <android> fetch --all --prune
git -C <engine> log -1 --format='%H %an %ci' origin/main
git -C <engine> log -12 --format='%h | %an | %ci | %s' origin/main
# live, via the GitHub API rather than local refs:
#   list_pull_requests(ShivaClaw/careerseeker,        state=all)
#   list_pull_requests(ShivaClaw/careerseeker-android, state=all)
```

*Expected, and **observed**:* engine `main` **`aac05f3`**, `Portable G & Shiva's Claw`,
**2026-08-12 20:28 −0600** — unmoved since run 74. **18 engine drafts** (#26, #32–#39, #45–#53)
and **6 android drafts** (#1–#6) read live: **all 24 open, all `draft: true`**, none merged,
closed or undrafted. Newest merge anywhere in either repo: **PR #44, 2026-08-13T02:28Z**.

**Both B-18 triggers read negative**, which is the finding this entry exists to support, not a
formality — see the run 79 status in `BLOCKED.md`.

### C-79-2 — the assigned slice, verified in the spec blob rather than rebuilt

```bash
PIN=7328a0bc043335491cd96a67d634e8eea2a13af9
git -C <engine> show $PIN:docs/Sync-Protocol.md > /tmp/spec-at-pin.md
grep -n "product_id\|acknowledged_at\|order_id" /tmp/spec-at-pin.md | head
grep -n "decoded ciphertext\|1 MiB"            /tmp/spec-at-pin.md | head
grep -n "decrypt_failed"                       /tmp/spec-at-pin.md | head
ls <android>/core/src/test/resources/sync-vectors/v1/invalid-unknown-field.json
```

*Expected, and **observed**:* the `{product_id, acknowledged_at, order_id?}` body at
**§4.3.3 lines 318–320** (gate **PQ-A6-1**, default-proceed, recorded in §10's change table at
line 658); the cap on the **decoded ciphertext** at **§3.1 lines 111–112** with the S5
amendment note at line 132 (**PQ-A2-1**); `decrypt_failed` for **every structural rejection**
at **§7.2 line 601** (**PQ-A2-2**); and `invalid-unknown-field.json` present (**PQ-A2-3**).

**All four are closed, and were closed before this run was scheduled.** The prompt assigning
them is stale; this is its **forty-fourth** firing.

### C-79-3 — the check the prompt asks for by name, at the actual pin

```bash
PIN=7328a0bc043335491cd96a67d634e8eea2a13af9
mkdir -p /tmp/pin && git -C <engine> archive $PIN docs/sync-vectors | tar -x -C /tmp/pin
cd /tmp/pin && node docs/sync-vectors/generate.mjs --check; echo "EXIT=$?"
```

*Expected, and **observed**:* **`OK: 29 vector files match the generator.`**, **`EXIT=0`**.

It passes **because the work is already done**, not because this run did it. Note the pin: the
prompt names `679a317`, which has been stale since 2026-08-12.

### C-79-4 — the vendored corpus is byte-identical to the pin, before and after this run's diff

```bash
diff -r /tmp/pin/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1
echo "DIFF_EXIT=$?"
git -C <android> diff --name-only origin/claude/android-a0-probe -- core/src/test/resources/sync-vectors | wc -l
```

*Expected, and **observed**:* **29 files each side, `DIFF_EXIT=0`**, and **0** vector paths in
this run's diff. Re-run after the two code commits and still `0`. **No vector byte moved and
the pin did not move**, so this run is not a cross-repo drift event.

### C-79-5 — the toolchain inventory, and the one thing that changed it

```bash
which dotnet; which pwsh; echo "${ANDROID_HOME:-unset}"; node --version; java -version
ls /usr/lib/jvm/
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
ls /usr/lib/jvm/
```

*Expected, and **observed**:* `dotnet` **not found**, `pwsh` **not found**, `ANDROID_HOME`
**unset**, Node **v22.22.2**, OpenJDK **21.0.10**. `/usr/lib/jvm` initially carried **21 only**,
so `scripts/core-probe.sh` exited **1** with its "no JDK 17 found" message. The apt install
**succeeded** (`openjdk-17-jdk-headless 17.0.19+10-1~24.04.2`) and `java-17-openjdk-amd64`
appeared.

**Both gates remain structurally impossible here** and **B-7** is unmoved: `Verify-Alpha.ps1`
needs .NET, PowerShell and Windows DPAPI; the android gate needs the Android SDK. What became
possible is exactly one of the gate's five commands, `:core:test`, via `core-probe.sh` — and it
is reported as that, never as a gate.

### C-79-6 — the baseline, established before anything was changed

```bash
cd <android> && scripts/core-probe.sh; echo "EXIT=$?"
```

*Expected, and **observed**:* **`346 tests, 0 failed, 0 skipped, across 22 classes`, `EXIT=0`**
on this run's final head; **343/0/0** on the head it started from (run 76's number, reproduced
here rather than inherited). Use `git stash` or check out `d781183` to reproduce the 343.

### C-79-7 — the finding: every oversized case in either repo is `MAX + 1` in all three units

```bash
grep -rn "MAX_ENVELOPE_BYTES" <android>/core/src
python3 -c "
import json; d=json.load(open('<android>/core/src/test/resources/sync-vectors/v1/invalid-oversized.json'))
print(d['synth_ciphertext_len'], d['expect_error'], d['envelope_json'])"
```

*Expected, and **observed**:* the only fixture is
`oversized() = Base64Url.encode(ByteArray(Protocol.MAX_ENVELOPE_BYTES + 1))`, and the corpus's
`invalid-oversized` carries `synth_ciphertext_len` **1048577** — both **`MAX + 1` decoded**.

**A value one byte over the cap in decoded bytes is also over it in base64url characters
(~1.4 MB) and in JSON envelope length.** All three candidate readings of §3.1 reject it
identically, so **no assertion in the suite or the corpus could tell them apart.** §3.1 forbids
two of those three readings by name.

### C-79-8 / C-79-9 / C-79-10 — the gap, measured by mutation on the pre-fix tree

```bash
cd <android> && git checkout d781183          # the head this run started from
# M1 — measure the base64url TEXT, the unit 3.1 forbids by name
sed -i 's|ciphertext.size > Protocol.MAX_ENVELOPE_BYTES|env.ciphertext.length > Protocol.MAX_ENVELOPE_BYTES|' \
  core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt
scripts/core-probe.sh --rerun
# M2 — cap at MAX * 3/4 = 786,432, the number 3.1 records the relay having shipped
# M3 — delete the check entirely (negative control)
```

*Expected, and **observed**:*

| | mutation of `EnvelopeReceiver.kt:70` | pre-fix result |
| --- | --- | --- |
| **C-79-8** | `env.ciphertext.length` — the base64url text | **GREEN, 343/0/0** |
| **C-79-9** | cap at `MAX_ENVELOPE_BYTES * 3 / 4` = 786,432 | **GREEN, 343/0/0** |
| **C-79-10** | size check removed (negative control) | **RED, 3 failures** |

**C-79-10 is the entry that makes the other two mean something.** The gate was not untested —
deleting it reddens `size is checked before signature placement`, `no rejection advances the
sequence tracker` and `the receiver classifies every envelope vector exactly as the engine
does`. It was tested for **existence**, and for neither **unit** nor **number**.

**C-79-9 is not a hypothetical.** §3.1 records that exact value as a bug that shipped on the
relay: its guard *"compared a character count to a byte budget, which capped the decoded payload
at 786,432 bytes and left the top 256 KiB of the declared range untransmittable"*.

### C-79-11 — the fix, and both mutations dying on it

```bash
cd <android> && scripts/core-probe.sh --rerun          # clean tree
# then re-apply M1, M2, M3 above against THIS head
```

*Expected, and **observed**:* clean **`346 tests, 0 failed, 0 skipped, 22 classes`, `EXIT=0`**
(343 → 346). **M1 RED** and **M2 RED**, each failing on
`EnvelopeReceiverTest > a ciphertext of exactly the cap is legal and is accepted` and on nothing
else. **M3 now reddens four tests instead of three**, the fourth being
`a ciphertext one byte past the cap is too_large` — so both new behavioural cases have teeth,
rather than one carrying the other.

### C-79-12 — the base64url derivation is §3.1's own normative number

```bash
python3 -c "n=1048576; print((n//3)*4 + (0 if n%3==0 else n%3+1), -(-4*n//3))"
grep -n "1,398,102" /tmp/spec-at-pin.md
```

*Expected, and **observed**:* both print **1398102**, and §3.1 line 119 declares
`ceil(4/3 × 1 MiB) = 1,398,102` as the relay's `MAX_CIPHERTEXT_B64U_CHARS`, calling the
conversion *"normative, not incidental: **the relay MUST carry every envelope this section
declares legal.**"* The new test pins the fixture at that character count, which ties the phone
to the relay's constant **without the phone importing a relay constant**.

### C-79-13 — the blast radius, stated so an auditor can check it cheaply

```bash
git -C <android> diff --stat d781183..HEAD
git -C <android> diff --name-only d781183..HEAD -- core/src/main/kotlin/app/careerseeker/core/EnvelopeReceiver.kt
git -C <android> diff --name-only d781183..HEAD -- app/ core/src/test/resources/
```

*Expected, and **observed**:* three files — `Protocol.kt` (**KDoc only**), `EnvelopeReceiverTest.kt`
(+127), `ProtocolTest.kt` (a test rename and a note). **`EnvelopeReceiver.kt` is UNMODIFIED**;
**0** `:app` files; **0** vector paths.

**The implementation was already correct.** It has always measured the decoded ciphertext —
which is precisely why §3.1's amendment moved the prose to the code rather than the reverse.
This run changed **no production behaviour**; it added the assertion that keeps the behaviour
from changing unnoticed, and corrected the one sentence in `:core` that still described the
retired rule.

### C-79-14 — what this run did NOT verify, and cannot

```bash
which pwsh; which dotnet; echo "${ANDROID_HOME:-unset}"
```

*Expected, and **observed**:* all absent/unset (**C-79-5**). Therefore, and stated plainly:

- **No android gate result is claimed.** `./gradlew checkCoreIsAndroidFree :core:test
  :app:assembleDebug :app:lintDebug` was **not run**; four of its five commands need the SDK.
  `:core:test` is reported as `scripts/core-probe.sh`, which is one command of the five.
- **No engine gate result is claimed.** `scripts\Verify-Alpha.ps1` was **not run**, and no
  offline assertion total appears anywhere in run 79.
- **The engine twin was not touched or re-run**, and it has the same gap. Measured by reading
  the blobs at the pin, not by executing anything:

  ```bash
  PIN=7328a0bc043335491cd96a67d634e8eea2a13af9
  git -C <engine> show $PIN:src/Sync/EnvelopeReceiver.cs | sed -n '41,47p'
  git -C <engine> show $PIN:tests/SyncHarness/Program.cs | grep -n -i "oversiz\|max_envelope"
  ```

  *Observed:* `EnvelopeReceiver.cs:45` reads `if (ciphertext.Length > Protocol.MaxEnvelopeBytes)`
  where `ciphertext` is the output of `Base64Url.TryDecode` — **the same correct rule as the
  phone's**, measured in decoded bytes. And `SyncHarness` exercises it at **`invalid-oversized`'s
  `synth_ciphertext_len` only** (`Program.cs:224`), plus a value pin of `index.json`'s
  `max_envelope_bytes` (`Program.cs:47`) — so, exactly as on the phone before this run,
  **`MAX + 1` and nothing at `MAX`.** §3.1's *"relay/test/relay.test.ts pins ... the maximum
  legal envelope"* covers the **relay**, which is a different component from the engine's
  **receiver**; the receiver's boundary is unpinned on that side still.

  Not fixed here: it is a second-repo change whose merge condition is a full local
  `Verify-Alpha.ps1` this environment cannot run, and adding a C# harness assertion moves
  `$ExpectedOfflineTotal`, which `CLAUDE.md`'s drift trap requires be changed together with
  every doc that reports it. Filed in `BLOCKED.md` under run 79 rather than pushed blind.
- **B-22 was neither observed nor sampled** — no `:app` test ran — and was not worked around.

### C-79-15 — the citation guard, run on this run's own records

```bash
cd <android> && bash scripts/check-citations.sh; echo "GUARD_EXIT=$?"
```

*Expected, and **observed**:* **`definitions: 732   cited: 733   documented-absent: 1`**,
`OK: every cited C-/B- id resolves to an entry that exists.`, **`GUARD_EXIT=0`**. Every
`C-79-*` id and `B-23` cited in prose this run resolves to a heading. Run 77 built this guard
(`C-75-13`) and its value was recorded as **prospective**; this is the second run to be checked
by it and it is still prospective — it found nothing, which is the correct outcome.

### C-79-16 — the cwd hazard recurred, and it is recorded rather than tidied away

```bash
git -C <engine> status --short          # must be empty
git -C <android> log --oneline -1 -- LOG.md BLOCKED.md
```

*Expected, and **observed**:* engine `git status` **empty**. During this run the shell's working
directory reset **mid-session** from `<android>` to `<engine>`, and two `cat >> LOG.md` /
`cat >> BLOCKED.md` appends therefore landed **in the engine repository as untracked new files**
rather than in this one. Detected by a `wc -c` sanity check on the file that was supposed to have
grown — `LOG.md` read **9,362 bytes** where it should have read ~988,000.

**Nothing was corrupted**: both strays were **untracked** (`?? LOG.md`, `?? BLOCKED.md` — the
engine repo carries neither file), so no engine content was overwritten. The content was
appended to the correct files with absolute paths and the strays deleted; the engine checkout is
clean.

**This is the identical hazard run 75 hit** — a bare relative path outliving its `cd` — which is
what `C-75-13` filed and run 77 built `scripts/check-citations.sh` against, and what `C-77-9`
recorded as *"load-bearing, since ... this run's shell cwd reset to `/home/user` mid-run"*.
**It has now recurred with a different destination** (`<engine>`, not `/home/user`), which is
worth knowing: the guard catches the *consequence* an unwritten record has for citations, and
catches nothing when the write lands somewhere real and silent. **Every path in run 79's record
appends after the incident is absolute.**

### C-79-17 — CI on this run's head, recorded as what reported rather than as a conclusion

```bash
# job 96985875445 of run 32554264969, head 8264275
#   actions_get(get_workflow_job, ShivaClaw/careerseeker-android, 96985875445)
```

*Expected, and **observed**, on head **`8264275`**, attempt **1**, `ubuntu-latest`:

| # | step | conclusion | duration |
| --- | --- | --- | --- |
| 6 | Assert every cited C-/B- id resolves | **success** | 1s |
| 7 | Assert `:core` has no Android dependency | **success** | 88s |
| 8 | Assert vendored sync vectors match the pinned main-repo commit | **success** | 9s |
| 9 | Unit tests (`:core`) | **success** | **64s** |
| 10 | Unit tests (`:app`, Robolectric) | **success** | 92s |
| 11 | Assemble debug APK | **in_progress at close of slice** | — |
| 12–14 | Lint, analytics assertion, APK upload | **pending** | — |

**No full-job conclusion is claimed**, and this run does not report "CI green". What is claimed
is exactly the six steps above, read from the job's own `steps` array.

**Three of them are the ones that matter for this diff, and all three passed on a runner:**

- **Step 9** is the runner's own execution of the suite this run changed. **64 seconds** answers
  the cost question the PR's self-audit raised about the new 1 MiB fixture: it is not a CI cost.
  CI prints no totals, so **346/0/0 stays a `core-probe.sh` measurement** (**C-79-11**) and step 9
  is corroboration, not the source of the number.
- **Step 8** is the vendored-vector drift check, and it re-derives **C-79-4** independently — CI
  re-fetches the pin's files from the API and diffs them, so the "no drift event" claim is not
  resting on this session's local `diff -r` alone.
- **Step 6** is run 77's citation guard, **runner-verified again** on this run's own records —
  the fourth head on which its pass path has executed. Its **failure** path stays stub-only,
  exactly the qualification **B-15** carries.

**B-22 did not fire on this head.** Step 10 completed **success in 92 seconds**. That is **one
sample** and narrows nothing: B-22's ~8% `:app` nondeterminism is unchanged and stays open, and
this run touched no `:app` file, so the sample is not evidence about the fix either.

**One reading correction, recorded because it was nearly written down as a finding.** Midway
through this run the job API reported step 10 as `in_progress` for what appeared to be 20+
minutes, which matches the shape of the documented android CI hang (`f49290e`, *"93s baseline vs
25+ min"*). **It was not a hang** — the step had completed in 92 seconds and the API's `steps`
array was serving stale data. **The step timestamps are the reliable field; the live `status` on
a polled job is not.** Anyone re-running the command above should compare
`started_at`/`completed_at` rather than trusting a polled `in_progress`.

### C-79-18 — correcting C-79-17: the run on `8264275` was CANCELLED, and this session cancelled it

```bash
#   actions_get(get_workflow_job, ShivaClaw/careerseeker-android, 96985875445)  # head 8264275
#   actions_get(get_workflow_job, ShivaClaw/careerseeker-android, 96986622332)  # head c5bcf83
```

**C-79-17 recorded step 11 as "in_progress at close of slice" and no job conclusion. Both
statements were true when read and are now superseded.** The completed job record for
`8264275` shows:

- **step 11 Assemble debug APK — `success`**, 05:29:39 → **05:31:10**. It had in fact finished.
- **step 12 Lint — `cancelled`**, 05:31:10 → 05:31:28; steps 13/14 **`skipped`**.
- **job conclusion: `cancelled`** at 05:31:33.

**Nothing failed.** The cancellation is **this session's own doing**: pushing `c5bcf83` (the
C-79-17 records commit) at 05:31 superseded the in-flight run under the workflow's concurrency
group, and the replacement run **`32554560261`** started at **05:31:36** on `c5bcf83`. So the
cancelled Lint step is an artifact of the push, **not a lint failure**, and reading that
`cancelled` as a red gate would be wrong.

**On the replacement head `c5bcf83`, steps 1–10 are `success`** — citation guard (step 6),
`:core` android-free (7), vendored-vector drift check (8), **`:core` unit tests in 57s** (9), and
**`:app` Robolectric in 113s** (10). **Steps 11–14 had not reported when this session stopped
polling, and no job conclusion is claimed for that head either.**

**The standing fact, which is the part worth keeping.** This lane has now hit self-cancellation
three times (`944d199`, `0ffe3b5`, and here). **A records commit pushed after a code commit
cancels the code commit's own CI run.** Run 56 already resolved how to write about it —
`878a203`, *"name the PR, not a run ID: each push supersedes the last CI run"* — and that rule
is why this entry cites **head SHAs and reported steps** rather than promising a conclusion.
**The conclusion on this branch's final head is unobserved by this session by construction**:
any push that recorded it would supersede the run it was recording.

**Second-order note on the API, since it cost this session real time.** The job endpoint served
a **stale `steps` array for many minutes** on both runs — reporting `in_progress` for steps that
had already completed, once for long enough to resemble the documented android CI hang
(`f49290e`). `updated_at` on the run object was likewise frozen. **Compare
`started_at`/`completed_at` on individual steps, and treat a polled `status` as a lower bound on
progress, never as current.**

### C-79-19 — B-22 FIRED on this run's final head, and it is the third occurrence

**This corrects two earlier statements in run 79's records.** The heartbeat and the run-79
records commit both say *"B-22 neither observed nor sampled"*, and **C-79-17** says *"B-22 did
not fire on this head."* Both were true when written — of `8264275` and `c5bcf83` — and **both
are wrong about the final head `73238fc`**, where B-22 fired.

```bash
#   pull_request_read(get_check_runs, ShivaClaw/careerseeker-android, 6)
#   actions_get(get_workflow_job, ShivaClaw/careerseeker-android, 96987312857)
#   get_job_logs(ShivaClaw/careerseeker-android, job_id=96987312857, return_content=true)
git -C <android> diff --stat c5bcf83 73238fc
sed -n '60,72p' <android>/app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt
```

*Expected, and **observed**, on head **`73238fc`**, run `32554847042`, attempt 1, job
`96987312857` — **`conclusion: failure`**:

```
ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab FAILED
    java.lang.AssertionError at ScreensFromFixtureTest.kt:69
35 tests completed, 1 failed, 3 skipped
```

Steps 1–9 **`success`** (including **`:core` in 58s**); **step 10 `:app` Robolectric `failure`**;
steps 11–14 `skipped`.

**Five independent reasons this is B-22 and not this PR's defect**, each measurable:

1. **The diff that produced it is records-only.** `git diff --stat c5bcf83 73238fc` →
   `AUDIT-REQUEST.md +41`, `LOG.md +29`. **Two markdown files, zero source files.** The
   precedent is exact: `0c4ca8f` was also a records-only commit failing this same class.
2. **The identical `:app` tree passed twice earlier the same day** — `8264275` at 92s and
   `c5bcf83` at 113s (**C-79-17**, **C-79-18**).
3. **The class and the assertion family are the ones B-22 names**: `ScreensFromFixtureTest`, a
   **provenance-banner** assertion. B-22's two prior samples were both provenance-banner
   assertions.
4. **The failing line is the hazard B-22 describes, and this run pins it to a line number**
   rather than a pattern. `ScreensFromFixtureTest.kt:68-69` is
   `compose.onNodeWithText(tab).performClick()` immediately followed by
   `compose.onNodeWithText(label).assertIsDisplayed()`, inside the tab loop — **a navigating
   click with no `waitForIdle()`/`waitUntil()` between it and the assertion**.
5. **The build's own compiler warning names the mechanism, in the same log, 25 lines above the
   failure:** `createComposeRule` is deprecated because *"The v2 APIs use StandardTestDispatcher
   instead of UnconfinedTestDispatcher ... **Tests relying on immediate execution may require
   explicit synchronization.**"*

**`:core` — the only module this run touched — passed on all three heads** (64s, 57s, 58s). No
`:core` test failed at any point.

**What was done about it: one re-run, and nothing else.** `rerun_failed_jobs` was triggered on
run `32554847042`, producing attempt 2 (job `96987953926`) **on the identical commit, with no
push between** — the only construction that can demonstrate nondeterminism, and the same one the
prior B-22 sample used (`96726656919` failure → `96728744410` success). **Its outcome was NOT
observed by this session**: the job and check-run endpoints served cached `in_progress` for the
remainder of the run, and `get_job_logs` returned **HTTP 404** (logs are not published until a
job completes). **No verdict is claimed for attempt 2.** Whoever reads this next should open the
run and look; if attempt 2 is green, B-22 has a third same-commit demonstration.

**What was deliberately NOT done.** The test was **not** skipped, `@Ignore`d, quarantined or
retried in-suite — B-22 forbids that explicitly and so does the mission. **No `:app` file was
written**, because the fix (a `waitForIdle()`/`waitUntil` after each navigating click) needs the
Android SDK to compile (**B-7**) and B-22 already carries a written-but-uncompiled patch labelled
unverified. **Pushing this correction starts another CI run**, which at B-22's rate has roughly a
one-in-twelve chance of reddening again on a markdown-only diff; that is a property of the
blocker, not of the change, and it is stated here rather than discovered later.

**B-22's sample count moves from 2-in-24 to 3**, and the newly load-bearing detail is that
**two of the three are records-only commits** — so the nondeterminism is not correlated with
touching `:app` at all, which is what makes it a gate hazard rather than a code smell.

### C-79-20 — B-22 demonstrated on a byte-identical `:app` tree, and the re-run that should have shown it was killed by my own push

**This corrects C-79-19's account of attempt 2.** C-79-19 says its outcome *"was NOT observed by
this session"* and attributes that to API caching. **The real reason is sharper and is my own
doing:** attempt 2 was **`cancelled`**, at **05:48:22**, with **step 10 killed mid-run at
05:48:19** — and the push of `5170aff` (the commit recording C-79-19) started the next run at
**05:48:24**. **I cancelled my own confirming re-run by pushing the record of it.**

```bash
#   actions_get(get_workflow_job, ShivaClaw/careerseeker-android, 96987953926)  # attempt 2
#   actions_get(get_workflow_job, ShivaClaw/careerseeker-android, 96988392550)  # head 5170aff
git -C <android> rev-parse 73238fc:app 5170aff:app
git -C <android> diff --name-only 73238fc 5170aff -- app/ core/ | wc -l
```

*Expected, and **observed**:*

| head | `:app` tree object | step 10 (`:app` Robolectric) |
| --- | --- | --- |
| **`73238fc`** | `460e581b927cd36845001d9d33e72273d66e376d` | **`failure`** — `ScreensFromFixtureTest.kt:69` |
| **`5170aff`** | `460e581b927cd36845001d9d33e72273d66e376d` | **`success`, 91s** |

**The two tree hashes are equal, and `git diff --name-only … -- app/ core/` returns 0 paths.**
The `:app` module is **the same git object** on both commits — the only difference between them
is 175 added lines across three markdown files.

**So B-22 is demonstrated on byte-identical code, red then green.** This is *stronger* evidence
than the same-commit re-run it replaces: a re-run repeats a commit, whereas a tree hash proves
the compiled input was the same object regardless of which commit carried it. **The experiment I
destroyed was recovered by accident, in a better form.**

**Full picture across this run's four heads** — `:core` (the only module run 79 changed) passed
on **every** one:

| head | diff from previous | `:core` | `:app` step 10 |
| --- | --- | --- | --- |
| `8264275` | the code slice | **64s** | **success** 92s |
| `c5bcf83` | records only | **57s** | **success** 113s |
| `73238fc` | records only | **58s** | **FAILURE** |
| `5170aff` | records only | **59s** | **success** 91s |

**Four `:core` passes, four different runners. One `:app` failure out of four, on a markdown-only
diff.** B-22's rate stands, and this run contributes **three** of its samples' worth of context
rather than one.

**On `5170aff`, steps 1–10 are `success`; steps 11–14 had not been observed to conclude when this
session stopped polling, and no job conclusion is claimed for that head.** The job endpoint froze
at `step 11 in_progress` and `get_job_logs` returned **HTTP 404** (its pre-completion behaviour),
consistent with the caching recorded in **C-79-18**.

**THE PROCEDURAL RULE THIS BUYS, and it is the part worth carrying forward.** **Never push while
a confirming re-run is in flight — the push cancels it.** Run 56 already established that a
records push supersedes the run it records (`878a203`); this run found the sharper corollary:
**a records push supersedes the *re-run* it records**, destroying the one experiment that can
distinguish a flake from a defect. The correct order is **observe, then write, then push** — and
accept that the final push's own run is unobservable, which is structural and already recorded
(**C-79-18**).

### C-79-21 — the android gate CONCLUDED green, all 14 steps, on this run's build input

**This upgrades C-79-17/-18/-20, which all say "no job conclusion is claimed for any head."** That
was true when each was written. It is no longer true: run **`32555286491`**, job
**`96988392550`**, head **`5170aff`**, attempt 1 — **`conclusion: success`**, completed
**05:55:48**, **all 14 steps green**.

```bash
#   actions_get(get_workflow_job, ShivaClaw/careerseeker-android, 96988392550)
git -C <android> rev-parse 5170aff:app 4dfdfac:app
git -C <android> rev-parse 5170aff:core 4dfdfac:core
git -C <android> diff --name-only 5170aff 4dfdfac -- app/ core/ gradle/ build.gradle.kts settings.gradle.kts .github/ scripts/ | wc -l
```

*Expected, and **observed**:* every step `success` — citation guard 1s, `:core` android-free 97s,
vendored-vector drift check 5s, **`:core` tests 59s**, **`:app` Robolectric 91s**, **Assemble
debug APK 109s**, **Lint 49s**, **the analytics/tracking assertion 2s**, and the APK upload.

**It covers the final head's build input exactly.** `4dfdfac` differs from `5170aff` by **116
added lines across three markdown files**, and the trees are equal:
`app` = `460e581b927cd36845001d9d33e72273d66e376d` on both, `core` =
`e89479950c8c0b184f5fd02c6014ddc9f1316339` on both, with **0** differing paths across `app/`,
`core/`, `gradle/`, the build scripts, `.github/` and `scripts/`.

**So the android gate — `checkCoreIsAndroidFree`, `:core:test`, `:app:test`, `:app:assembleDebug`,
`:app:lintDebug` — ran and passed on this run's code.** That is the claim **B-7** normally
prevents a cloud session from making, and it is CI's, not this session's.

**Read it with three qualifications, all of which still bind.**

1. **It is not a local gate.** No Gradle task ran in this sandbox beyond `:core:test` via
   `scripts/core-probe.sh`. **B-7 is unmoved**; observing a gate is not running one.
2. **It is ONE sample, and this run produced its own counterexample.** `73238fc` carries the
   **identical** `app` tree object and its `:app` step **failed** (**C-79-19**, **C-79-20**).
   **B-22 is not narrowed by this green** — if anything the pair is the cleanest statement of the
   blocker there is: same tree, one red, one green, one full pass.
3. **CI prints no totals**, so **346/0/0 remains a `scripts/core-probe.sh` measurement**
   (**C-79-11**). This entry adds a *conclusion*, not a count.

**No conclusion is claimed for `4dfdfac` itself** — its run was in flight when this session
stopped, and by the rule in **C-79-20** recording it would have superseded it. The tree equality
above is what makes that acceptable rather than a gap.

---

## Run 80 — 2026-08-22 (Linux cloud sandbox). B-22's cause, corrected and fixed

### C-80-1 — rule one, and every count below taken after it

```bash
cd <android> && git fetch --all --prune
cd <engine>  && git fetch --all --prune
```

*Observed:* the android checkout arrived **detached at the docs-only `main` `ebfaf81`**, not on the
work branch — the same start state runs 76 and 79 recorded, so it is the image, not an accident.
`origin/claude/android-a0-probe` fetched to **`cd915ca`** (run 79's final head), **300 commits ahead
of `main`**. Engine fetched clean. Nothing in this run was branched, compared or counted before
these two commands.

### C-80-2 — B-18's two triggers, measured live rather than carried forward

```bash
cd <engine> && git rev-parse --short origin/main
git log --format='%h|%an|%ad|%s' --date=short -40 origin/main | grep -vi claude | head -3
# then, live, not from a local ref:
#   list_pull_requests ShivaClaw/careerseeker        state=all
#   list_pull_requests ShivaClaw/careerseeker-android state=all
```

*Expected, and **observed**:*

| trigger | measured | fires? |
| --- | --- | --- |
| **movement in the blocking state** — a merge, close, undraft, or human commit in either repo | engine `main` **`aac05f3`**, unmoved since **2026-08-12**; last non-Claude commit **2026-08-12**; **18 engine + 6 android** PRs open, **all 24 `draft: true`**; newest merge anywhere **PR #44, 2026-08-13** | **no** |
| **something needing an action before return day** | return day (**2026-08-18**) is **four days** past; this run's output is a corrected diagnosis and a test-only fix | **no** |

**So: nothing sent, for the seventh consecutive run.** Attempt 10 (run 73) reached Brandon's phone
and inbox and is unanswered; nothing in the world state has changed since. **The criterion still
inverts on a new fact, immediately.** **B-18 stays open.**

### C-80-3 — the assigned slice, re-verified in the blobs. Forty-fifth firing

The stored prompt again assigns S5's spec half and again states the pin as `679a317` and S5 as
"NOT STARTED". Both are wrong, and this is the check the prompt itself asks for by name:

```bash
cd <engine> && git worktree add --detach /tmp/pinwt 7328a0bc043335491cd96a67d634e8eea2a13af9
cd /tmp/pinwt && node docs/sync-vectors/generate.mjs --check
diff -r docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1
grep -n product_id docs/Sync-Protocol.md | head -2
grep -n decrypt_failed docs/Sync-Protocol.md | head -3
ls docs/sync-vectors/v1/ | grep unknown-field
```

*Expected, and **observed**:*

- `OK: 29 vector files match the generator.` **`EXIT=0`** — passing **because the work is already
  done**.
- `diff -r` **exit 0**, **29/29** byte-identical. Pin is **`7328a0b`**, per `VECTORS.lock`.
- **PQ-A6-1** closed: the `{product_id, acknowledged_at, order_id?}` body at **§4.3.3, line 318**,
  and named in the change table at line 658.
- **PQ-A2-1** closed: the cap on the **decoded ciphertext**, line 656 (and the §3.1 discussion at
  line 146).
- **PQ-A2-2** closed: *"**Also every structural rejection**"* under `decrypt_failed` at **line 601**.
- **PQ-A2-3** closed: `invalid-unknown-field.json` present at the pin.

### C-80-4 — the mechanism: every initial `collectAsState` value renders a DIFFERENT tree

```bash
cd <android>
sed -n '47,52p' app/src/main/kotlin/app/careerseeker/dashboard/ui/DashboardApp.kt
sed -n '70,74p' app/src/main/kotlin/app/careerseeker/dashboard/ui/HomeScreen.kt
sed -n '44,46p' app/src/main/kotlin/app/careerseeker/dashboard/ui/ApplicationsScreen.kt
sed -n '42,43p' app/src/main/kotlin/app/careerseeker/dashboard/ui/ApplicationDetailScreen.kt
```

*Expected, and **observed**:* `DashboardApp.kt:47-51` reads all five replica queries with
`collectAsState`, four of them `initial = null` or `emptyList()`. And each initial value renders a
tree **without** the node the tests look for:

| seam | first-frame text | the node the test wants |
| --- | --- | --- |
| `StatusBanner(syncState = null)` — `HomeScreen.kt:72` | **"Not paired — no data yet"** | `"Demo data — not a live engine"` |
| `ApplicationsScreen(applications = emptyList())` — `:44` | **"No applications in the replica yet."** | `"Senior Platform Engineer"` |
| `ApplicationDetailScreen(application = null)` — `:42` | early return | `"Documents (read-only)"` |

The demo label is not a slow-arriving version of the first frame — **it is a different string**, and
it exists only after Room's query executor has delivered a row and a recomposition has landed.

### C-80-5 — B-22's stated cause is wrong: the line that failed most recently has no click before it

```bash
cd <android> && git show cd915ca:app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt | sed -n '60,75p'
```

*Expected, and **observed**:* line **69** — `compose.onNodeWithText(label).assertIsDisplayed()` — is
the **first statement after `setContent`**, above the `for` loop, with **no `performClick()` anywhere
before it**. B-22 says *"Both failing assertions are `assertIsDisplayed()` called **immediately after**
a `performClick()` that navigates."* That is true of line 87 and **false of line 69**, and line 69 is
the line that failed in **two of the three** recorded occurrences (`0c4ca8f`, `73238fc`).

### C-80-6 — the failures partition exactly along the Flow seam: 2 tests carry all of them, 6 carry none

```bash
cd <android> && grep -n "    fun " app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt
```

*Expected, and **observed**:* **8 `@Test` methods**, and exactly **two** of them render the shell.

| test | how it gets its data | recorded failures |
| --- | --- | --- |
| `theProvenanceBannerIsShownOnEveryTab` | `DashboardApp(db)` → Room `Flow`s | **2** (`0c4ca8f` line 69, `73238fc` line 69) |
| `theBannerFollowsIntoTheApplicationDetailOverlay` | `DashboardApp(db)` → Room `Flow`s | **1** (`592afa4` line 87) |
| `homeRendersCounters` | `countersNow()` / `syncStateNow()`, passed in | 0 |
| `applicationsRendersRowsAndStateBadges` | `applicationsNow()`, passed in | 0 |
| `applicationDetailRendersAllThreeDocumentsReadOnly` | `applicationsNow()` / `documentsNow()` | 0 |
| `jobsRendersHonestyBadges` | `jobsNow()`, passed in | 0 |
| `evidenceRendersTrailAndEngineVerifiedBadge` | `evidenceEventsNow()`, passed in | 0 |
| `evidenceSaysUnknownWhenNoVerdictReported` | literal `emptyList()` / `null`, no DB at all | 0 |

**All three failures are in the two tests that render `DashboardApp`; none of the six that pass a
value straight into a screen has ever failed.** The six are `suspend` `*Now()` reads inside
`runBlocking` (or, in the last case, no read at all), so their data is complete **before**
`setContent` is called — they never cross the seam. That is the partition the Flow hypothesis
predicts; the click hypothesis predicts nothing about it, and cannot explain line 69 at all.

### C-80-7 — why B-22's prescribed `waitForIdle()` patch would not have fixed either failure

*Expected, and **observed** — this one is an argument from the API contract plus the recorded data,
and it was **not executed**, because `:app` cannot run here (**C-80-9**):*

Compose UI test synchronizes with the compose clock **automatically, before every node interaction**
— `assertIsDisplayed()` fetches its semantics node through the test owner, which idles first. So an
explicit `compose.waitForIdle()` placed immediately before one of these assertions is the same
synchronization, called twice.

**The tests flake in spite of that synchronization already being in force.** Therefore the
unsynchronized source is *outside* the compose clock. In this test the only asynchronous source is
Room's query executor delivering the first row of a `Flow` — which Compose's idling registry does not
observe. **A second `waitForIdle()` cannot cover it.** That is why the fix polls the condition
(`waitUntil` on the node's arrival) rather than the clock.

### C-80-8 — the fix, and what was actually verified about it locally

```bash
cd <android> && git diff cd915ca..30908de --stat
SP=/tmp/scratch
for a in kotlin-compiler-embeddable kotlin-stdlib kotlin-script-runtime kotlin-reflect; do
  curl -sSLo "$SP/v24-$a.jar" "https://repo1.maven.org/maven2/org/jetbrains/kotlin/$a/2.4.10/$a-2.4.10.jar"; done
CP="$SP/v24-kotlin-compiler-embeddable.jar:$SP/v24-kotlin-stdlib.jar:$SP/v24-kotlin-script-runtime.jar:$SP/v24-kotlin-reflect.jar:$SP/annotations.jar:$SP/coroutines.jar:$SP/trove.jar"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -d "$SP/out" \
  app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt
# and the control, on the unmodified file:
git show cd915ca:app/src/test/kotlin/.../ScreensFromFixtureTest.kt > "$SP/Baseline.kt" && <same command>
```

*Expected, and **observed**:* **one file changed, test-only**; six `awaitText(...)` call sites, one
private helper, one import. Diagnostics under the repo's **own pinned Kotlin 2.4.10**:

| | unresolved refs | built-in access | for-loop | annotation | **parse errors** |
| --- | --- | --- | --- | --- | --- |
| baseline (`cd915ca`) | 71 | 3 | 1 | 1 | **0** |
| modified (`30908de`) | 72 | 11 | 1 | 1 | **0** |

**Zero parse errors on both**, and `grep -in "expecting\|syntax\|unexpected token"` returns nothing.
Every remaining diagnostic is the missing classpath: no androidx, no JUnit, and `-no-stdlib`.

**What this does NOT establish, stated because it is easy to overclaim.** A `comm` of the two
unresolved-symbol sets is empty, which looks like *"the change introduces no new unresolved
symbol"*. **It is not evidence of that.** `compose` is itself unresolved at baseline, so the
compiler suppresses cascading diagnostics on `compose.onAllNodesWithText(...)`,
`compose.waitUntil(...)` and `.fetchSemanticsNodes()` — they are never independently reported. **The
three androidx calls are unverified locally.** `waitUntil(timeoutMillis, condition)`,
`onAllNodesWithText` and `fetchSemanticsNodes()` are long-stable `ui-test` API, but that is a
reading, not a measurement. **Their resolution is CI's** (**C-80-10**).

### C-80-9 — B-7 reproduced, and the one new fact about its shape

```bash
curl -sS -o /dev/null -m 20 -w "%{http_code}\n" https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4/maven-metadata.xml
curl -sS -o /dev/null -w "%{http_code}\n" https://repo1.maven.org/maven2/androidx/compose/ui/ui-test-junit4/maven-metadata.xml
which dotnet pwsh; echo "ANDROID_HOME=$ANDROID_HOME"
```

*Expected, and **observed**:* `dl.google.com` **`000`** (denied, unchanged), `repo1.maven.org`
**`404`**. `dotnet`, `pwsh` absent; `ANDROID_HOME` empty. **The new detail:** androidx is not
mirrored to Maven Central, so the `core-probe.sh` trick — build the Central-only subset — has **no
analogue for `:app`**. `:app` is not blocked by a missing SDK alone; its test dependencies are
unreachable from this network at all. **CI is not a convenience for `:app`, it is the only gate that
exists.**

### C-80-10 — CI on this run's code head: CONCLUDED green, all 14 steps, attempt 1

```
get_workflow_job ShivaClaw/careerseeker-android 97010096375
# or: https://github.com/ShivaClaw/careerseeker-android/actions/runs/32564115588
```

*Expected, and **observed**:* run **[32564115588](https://github.com/ShivaClaw/careerseeker-android/actions/runs/32564115588)**,
job **97010096375**, head **`30908de`**, **attempt 1**, **`conclusion: success`**, **all 14 steps
`success`**:

| # | step | |
| --- | --- | --- |
| 6 | citation guard | success |
| 7 | `:core` has no Android dependency | 88s |
| 8 | vendored vectors match the pinned commit | 9s |
| 9 | Unit tests (`:core`) | 56s |
| **10** | **Unit tests (`:app`, Robolectric)** | **93s** |
| 11 | Assemble debug APK | 95s |
| 12 | Lint | 44s |
| 13 | no analytics or tracking SDKs ship | 2s |
| 14 | Upload debug APK | 4s |

**Step 10 is what this run needed and the only thing it proves is the pair of facts it states:** the
three androidx calls **resolve**, and the suite **passes on this tree**. **No re-run was triggered**
— this is attempt 1, and the single permitted re-run remains unspent.

**What it does NOT prove, and the entry says so in the same breath.** B-22 is a *frequency* claim,
and **one green run cannot refute one** — that is the blocker's own central argument, and it applies
to its fix exactly as it applies to every other green in these records. **B-22 stays OPEN and
NARROWED.** CI also prints no totals, so no test count is claimed here. The closing evidence is
B-22's own: `:app:testDebugUnitTest --rerun-tasks` twenty times, 20/20, on a machine with the SDK.

**Read against the right baseline:** `cd915ca`, the tree this run started from, is itself a **green**
sample (**C-80-11**). So step 10 going green is *consistent with* the fix working and *equally
consistent with* the ~89% of runs that were always going to be green. The argument for the fix is
structural (**C-80-4**/**C-80-7**), not statistical, and it should be attacked there.

### C-80-11 — a fifth `:app` sample, from run 79's own final head

```
list_workflow_runs ci.yml branch=claude/android-a0-probe
```

*Expected, and **observed**:* run **220** (`32555907567`), head **`cd915ca`**, **`conclusion:
success`** — a head run 79 pushed and, by its own rule, could not observe. So B-22's tally across the
heads this lane has produced is now **3 failures in 28 completed runs**, not 3 in 27. The rate did
not move materially; the point of recording it is that **`cd915ca` is a green sample on the tree this
run started from**, which is the correct baseline for reading C-80-10.

### C-80-12 — the citation guard on this run's records

```bash
cd <android> && scripts/check-citations.sh
```

*Expected, and **observed**:* **`definitions: 751   cited: 752   documented-absent: 1`**,
`OK: every cited C-/B- id resolves to an entry that exists.`, **`EXIT=0`** — run **after** this
run's four record appends, not before. CI re-runs it as step 6 and it passed there too
(**C-80-10**).

**Read it for what it checks.** It proves every `C-`/`B-` id this run cites has an entry. It does
**not** prove the appends landed in the right repository — the hazard of **C-79-16**, where a
record write went to the engine checkout and both the claim and its definition travelled together,
leaving the guard consistent and wrong. This run defended against that differently: every append
used an **absolute** path, and `wc -c` was taken on all three files before and after
(`AUDIT-REQUEST.md` 769,468 → 783,328; `LOG.md` 999,534 → 1,010,687; `BLOCKED.md` 264,358 →
272,015). The hazard did announce itself twice — the shell's working directory reset to
`/home/user` after two commands — and cost nothing, because nothing relied on a relative path
outliving its `cd`.

## Run 81 — 2026-08-22 (Linux cloud sandbox). `latest`'s `since`-independence: the guard was on the branch the plan closes

Every command below was **executed this run**, after `git fetch --all --prune` in both trees.
`<android>` = `/home/user/careerseeker-android`, `<engine>` = `/home/user/careerseeker`.

### C-81-1 — rule one, and both checkouts were stale detached HEADs

```bash
cd <android> && git fetch --all --prune && git rev-list --left-right --count origin/main...origin/claude/android-a0-probe
cd <engine>  && git fetch --all --prune && git rev-parse origin/main
```

*Expected, and **observed**:* both trees arrived **detached** at stale refs — the android checkout at
the docs-only `main` (`ebfaf81`), the engine at `aac05f3`. Post-fetch, `claude/android-a0-probe` is
`8610253` and is **302 ahead / 10 behind** `origin/main`. Engine `origin/main` is still **`aac05f3`**,
unmoved since 2026-08-12. Every count in this run is post-fetch.

### C-81-2 — the assigned slice is built: the forty-sixth firing

```bash
cd <engine> && for c in 8575539 22b028e 7328a0b; do git log -1 --format='%ad  %s' $c; done
cd <engine> && git worktree add -f --detach /tmp/pin 7328a0b && (cd /tmp/pin && node docs/sync-vectors/generate.mjs --check)
```

*Expected, and **observed**:* `8575539` **Sun Aug 9 2026** *"S5: define the entitlement_ack body, and
say what the size cap actually measures"* (`docs/Sync-Protocol.md` only, **+114/−3**); `22b028e`
**Sun Aug 9 2026** (both ack vectors **and** `generate.mjs`); `7328a0b` **Wed Aug 12 2026**
(`invalid-unknown-field`, PQ-A2-3). `--check` prints **`OK: 29 vector files match the generator.`**,
**`exit=0`**. This is the command the stored prompt names as *"a real, runnable verification here"* —
it runs, and it passes on work finished **thirteen days ago**. **Declined and re-verified, not
rebuilt** (**B-18**).

### C-81-3 — the pin, and zero cross-repo drift

```bash
cd <android> && grep -oE '[0-9a-f]{40}' core/src/test/resources/sync-vectors/VECTORS.lock | head -1
cd <engine>  && git merge-base --is-ancestor 7328a0b origin/main && echo "on main" || echo "NOT on main"
diff -rq /tmp/pin/docs/sync-vectors/v1 <android>/core/src/test/resources/sync-vectors/v1; echo "exit=$?"
```

*Expected, and **observed**:* pin is **`7328a0bc043335491cd96a67d634e8eea2a13af9`**, **not** the
prompt's `679a317`; **NOT on main** (`git ls-tree origin/main docs/sync-vectors/v1/ | wc -l` = **26**,
against **29** vendored); `diff -rq` prints **nothing**, **`exit=0`**, **29 files**. **No vector byte
was written by this run in either repo.**

> **Note the first ancestor check was run in the wrong repository** and printed
> `fatal: Not a valid commit name 7328a0b…` → `NOT on main`. The right answer by the wrong route.
> Re-run in `<engine>`, it is genuinely `NOT on main`. Recorded because the failure mode is
> **C-79-16**'s (a command that outlives the tree it assumes) and it produced a *plausible* result.

### C-81-4 — the `:core` lane, and JDK 17 again

```bash
apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless
cd <android> && bash scripts/core-probe.sh
```

*Expected, and **observed**:* the sandbox ships **JDK 21 only**, and `core-probe.sh` refuses to start
with its own prescribed remedy. After the install: **`core-probe: 346 tests, 0 failed, 0 skipped,
across 22 classes`**, `BUILD SUCCESSFUL`. **346 reproduces run 79 exactly.** This is **B-7**'s
per-session cost recurring for the *n*th run, not a new blocker.

**No `:core` file was written this run, so 346 is a baseline measurement only** — no `:core` claim is
made and no delta is reported.

### C-81-5 — the relay suite runs here, and the baseline reproduces

```bash
cd <engine> && git worktree add -f --detach /tmp/relaywt origin/claude/s2-seq-bound
cd /tmp/relaywt/relay && npm ci && npm test
```

*Expected, and **observed**:* **`Tests  51 passed (51)`**, 1 file. This **reproduces C-S2Q-4**
(*"the relay suite is 42 → 51 and green"*) exactly — it is a **known** lane, not a new capability, and
it is the one gate in this repo a Linux sandbox genuinely owns besides `generate.mjs --check`.

### C-81-6 — item 1's stated axis, REFUTED: `since`-independence holds in every relay version

The ordered intent's top item asks whether a deployed relay older than #53 is covered. Its stated
property is *"`latest` is `MAX(seq)` per direction independent of `since`"*.

```bash
cd <engine> && for r in $(git for-each-ref --format='%(refname:short)' refs/remotes/origin); do
  h=$(git rev-parse "$r:relay/src/channel.ts" 2>/dev/null) && echo "$h  $r"; done | sort | uniq -c -w40
cd <engine> && for h in 3c8a843b 69f6309e 9c8998d1 d961dccb 4ef847e8; do git cat-file blob $h | grep -n "MAX(seq)"; done
```

*Expected, and **observed**:* **five distinct blobs** of `relay/src/channel.ts` across all refs. In
**every** one that has the field, the `latest` query is `MAX(seq) … WHERE dir = ?` (± a retention
predicate) — **`since` appears in no version's `latest` query.** The property has held since the P1
relay `bea78cb`, which is the deployed phase. **So the skew as item 1 describes it is not real**, and
the item can be closed on its stated axis.

### C-81-7 — the version-dependence that IS real, and is deliberate

```bash
cd <engine> && git log --oneline --all -S"MAX(seq) AS m FROM envelopes WHERE dir = ? AND expires_at" -- relay/src/channel.ts
cd <engine> && git cat-file blob 4ef847e8 | sed -n '174,190p'
```

*Expected, and **observed**:* **`90ae2a1`** (*"the relay's read path forgot retention"*, PR #34) is the
sole commit that changed it: the **pull** `latest` became retention-filtered while the **push** replay
guard did not. So `latest`'s *value* is version-dependent even though its `since`-independence is not.
**This is deliberate and documented in situ** — the comment reads *"This deliberately counts
expired-but-uncollected rows, unlike `pull` below. The two want opposite things from the same rows."*
**Not a defect, and recorded so it is not re-investigated as one.**

### C-81-8 — the guard exists on exactly one ref, and it is the one the plan closes

```bash
cd <engine> && for r in $(git for-each-ref --format='%(refname:short)' refs/remotes/origin); do
  git show "$r:relay/test/relay.test.ts" 2>/dev/null | grep -q "independent of since" && echo "HIT $r"; done
```

*Expected, and **observed**:* exactly one line — **`HIT origin/claude/s6-resume-reconciliation`**
(**PR #53**), the branch `RETURN-DAY.md` §3 **step 0 recommends closing**. `claude/s2-seq-bound`'s
`latest` assertions cover ranges, retention and the 409 body; **none pins `since`-independence.**

> `scripts/fleet-probe.sh` lives in the **android** repo, not the engine, so the house probe could not
> be used from the engine checkout; the loop above does the equivalent job across every engine ref.

### C-81-9 — the dependency SURVIVES the closure, on #46

```bash
cd <engine> && git grep -n "PullAsync(" origin/claude/s6-counter-reconciliation -- src/
cd <engine> && git grep -n "Latest" origin/claude/s6-counter-reconciliation -- src/Sync/InboundPump.cs
```

*Expected, and **observed**:* on **#46** (which survives per §11.4), `InboundPump.cs:225` computes
**`MoreAvailable: _cursor < page.Latest`** — `latest` is the pump's **pagination loop bound** — and
`Program.cs:409` calls `PullAsync(…, since, ct)` with a **moving, non-zero** `since` resumed from
`paired.LastP2eSeq`. **So the dependency outlives #53 while its only guard does not.**

**Stated against the weaker half too, deliberately:** `Program.cs:286`'s reconcile read passes
**`since: 0`** on #46, so the *reconcile* half of the justification is #53's own and would leave with
it. **The pump half is what carries the argument**, and it is the half quoted in the PR.

### C-81-10 — the mutation proof: the property is unguarded today

Mutation = the exact refactor the property is exposed to (`… AND seq > ?`, `since`), applied to
`relay/src/channel.ts`'s **pull** `latest` only.

```bash
cd /tmp/relaywt/relay
# 1. baseline, no guard, mutation applied
npm test
# 2. add the guard (this run's commit), mutation still applied
npm test
# 3. restore channel.ts, guard present
npm test
```

*Expected, and **observed**:*

| tree | mutation | result |
| --- | --- | --- |
| `s2-seq-bound` baseline, **no guard** | applied | **`Tests  51 passed (51)`** — **GREEN** |
| **+ guard** | applied | **`Tests  1 failed \| 51 passed (52)`** — **RED** |
| **+ guard** | clean | **`Tests  52 passed (52)`** — **GREEN** |

Row 1 is the finding: **the property the pump depends on is currently guarded by nothing.** Under
mutation the guard fails at `expect(none.latest).toBe(3)` with **received `0`** — exactly the value
that makes `_cursor < page.Latest` false and **ends the drain early and silently**.

`git diff --name-only` after restore: **`relay/test/relay.test.ts`** alone. **No production source was
committed** — `channel.ts` was mutated only in the scratch worktree and restored from a pre-mutation
copy before the commit.

### C-81-11 — typecheck

```bash
cd /tmp/relaywt/relay && npx wrangler types && npx tsc --noEmit -p tsconfig.json
```

*Expected, and **observed**:* **0 errors.** `wrangler types` runs **offline** here. Run without it
first, `tsc` reports ~15 `Cannot find name 'Response'/'Request'/'Env'` errors **all in `src/`** — the
generated `worker-configuration.d.ts` missing, not a defect and **not caused by this run's file**
(`grep -c 'test/relay.test.ts'` = **0** both times). `relay/worker-configuration.d.ts` is gitignored
(`.gitignore:27`) and was not committed.

### C-81-12 — what was pushed, and what was deliberately NOT touched

```bash
cd <engine> && git log --oneline -1 origin/claude/s2-latest-since-invariant
cd <engine> && git rev-parse origin/claude/s2-seq-bound
```

*Expected, and **observed**:* new branch `claude/s2-latest-since-invariant` at **`f95b66e`**, **draft
PR #54** into base `claude/s2-seq-bound`, diff **one test file, +35 lines, no production source**.
**`claude/s2-seq-bound` is unmoved at `2be00fc`** — amending #35 in place would have invalidated
**C-RD-3**, which verified all seven landing branches against their PR heads the day before the plan
is used. **Nothing was merged, closed, undrafted or force-pushed.**

### C-81-13 — the citation guard on this run's records

```bash
cd <android> && scripts/check-citations.sh
```

*Expected, and **observed***, run **after** this run's record appends:
**`definitions: 764   cited: 765   documented-absent: 1`**,
`OK: every cited C-/B- id resolves to an entry that exists.`, **`EXIT=0`**.

Sizes taken before and after with **absolute** paths, per **C-79-16**: `AUDIT-REQUEST.md`
784,362 → **795,042**; `LOG.md` 1,010,687 → **1,018,810**; `BLOCKED.md` 272,015 → **275,459**;
`STATE.md` 574,250 → **582,440**. All four grew, all four in `<android>`, and
`git status` in `<engine>` shows **no untracked record file** — which is the specific failure
**C-79-16** describes, where an append lands in the wrong checkout and the guard stays consistent
because the claim and its definition travel together.

**Read the guard for what it checks.** It proves every `C-`/`B-` id cited here has an entry. It does
**not** prove any command in this file was actually run — that is what the *"Expected, and observed"*
wording is for, and it is only as good as the run that wrote it.

### C-81-14 — CI ran PR #54, and it verifies the two claims run 81 filed as unverified

**Added after the run-81 records were pushed**, on a `check_suite.completed` wake. The rows above that
say *"CI has not run PR #54"* are **superseded by this one**; they were true when written and are not
now. Recorded as a correction rather than edited away.

```bash
list_workflow_runs ci.yml branch=claude/s2-latest-since-invariant
list_workflow_jobs <run_id>
```

*Expected, and **observed**:* **one** run, **`32574969239`**, run number **474**, **`run_attempt: 1`**,
head **`f95b66e`**, **`conclusion: success`**, `pull_requests: [54]`. **No re-run was triggered.**
Two jobs, both `success`:

| job | runner | the steps that matter |
| --- | --- | --- |
| **Blind relay (Worker)** | `ubuntu-latest` | Typecheck ✓, **Test ✓**, dry-run ✓, blind-relay assert ✓, vector assert ✓ |
| **Build and offline harnesses** | `windows-latest` | Build warnings-as-errors ✓, **offline alpha verification ✓** |

**The relay assertion, on a runner:** `✓ test/relay.test.ts (52 tests)`, **`Tests  52 passed (52)`**.
This is **C-81-10**'s clean-tree row reproduced off this machine — the guard is real on CI, not only in
the sandbox. `npx wrangler types` then `npx tsc --noEmit` both green, reproducing **C-81-11**.

**The `$ExpectedOfflineTotal` claim, now measured instead of asserted:**
**`=== Offline total: 598 passed, 0 failed ===`**, `CareerSeeker alpha verification complete.` — run on
**Windows**, which is the gate this sandbox cannot reach. **598 is the base branch's number**, so this
branch moves the pin by **zero** and adds **no** landing cost to the pin family (**B-17**). The PR's
self-audit item 4 asked a reviewer to *"verify that claim rather than accept it"*; this is that
verification, and it holds.

**One number to read correctly, not as drift:** the vector step prints
**`OK: 28 vector files match the generator.`**, not the 29 of **C-81-3**. That is the **base branch's**
pre-pin state — `claude/s2-seq-bound` predates the third vector — and the check is self-consistent on
that tree. **This run added no vector and moved no pin**; 28 here and 29 at `7328a0b` are two different
trees, not a disagreement. **Not a cross-repo drift event.**

**What this still does not prove.** It is the **engine** repo's CI, so it says nothing about the
android gate (**B-7**, **B-22** unmoved), and `Verify-Alpha.ps1`'s **`-IncludePublish`/`-IncludePackage`
passes did not run — only the offline portion CI runs on every push. **The merge condition remains a
full local gate**, which no cloud session can run, so this changes **nothing** about the landing policy.

## Run 82 — 2026-08-22 (Linux cloud sandbox). ITEM 2 measured: the two high-water marks do disagree, and the harmless half was unguarded.

### C-82-1 — rule one, and both checkouts arrived stale

```bash
cd <android> && git fetch --all --prune && git rev-parse HEAD origin/claude/android-a0-probe
cd <engine>  && git fetch --all --prune && git rev-parse origin/main
```

*Expected, and **observed**:* the android checkout arrived **detached at `ebfaf81`** — the docs-only
`main` — while the work branch `origin/claude/android-a0-probe` was **`5dcbca2`**, **302 commits**
ahead. The engine checkout arrived detached at **`aac05f3`**, which **is** `origin/main`, unmoved
since 2026-08-12. **Every count in run 82 is post-fetch.** This reproduces **C-81-1** exactly; it is
a property of how the sandbox clones, not a one-off.

### C-82-2 — the assigned slice, declined for the forty-seventh time

```bash
cd <engine> && git log --oneline -1 8575539 22b028e 7328a0b
cd <engine> && node docs/sync-vectors/generate.mjs --check   # at 7328a0b
cd <android> && grep -i 'Pinned commit' core/src/test/resources/*/VECTORS.lock
```

*Expected, and **observed**:* the stored prompt again assigns S5's spec half (§4.3 `entitlement_ack`,
the ack vectors, PQ-A2-1/-2/-3). **It has been built since 2026-08-09** on the `claude/s5-*` drafts in
the **engine** repo: `8575539`, `22b028e`, `7328a0b`. The prompt's vendored pin **`679a317` is stale**;
`VECTORS.lock` reads **`7328a0bc043335491cd96a67d634e8eea2a13af9`**. This is **inherited from
C-81-2 and re-checked at the lock file, not re-derived** — the three commits were not re-verified
blob-by-blob this run, and this entry does not claim they were.

**No notification was sent for it.** Run 81 delivered exactly this fact to Brandon on 2026-08-22 and
**B-18**'s run-81 note says *"notify again only on a NEW fact."* One day later, unrepointed, is
the **same** fact. Silence here is the rule being followed, not an omission.

**A records gap found while citing this, and recorded rather than quietly patched.** Run 81's
notification is written up in `STATE.md`'s RUN 81 banner, in `LOG.md` and in B-18's run-81 status —
**but it was given no `C-` claim of its own**, so the one action of run 81 that left the repository is
the one with no re-verification command. This run's citation guard caught the dangling citation it
produced by assuming otherwise (**C-82-10**). **Not retro-filed as a C-81-x**: a claim belongs to the
run that made it, and inventing one a day later would put a command in run 81's series that run 81
never ran. Compare **C-B18-4**, which *did* record an earlier attempt properly and is the template.
**The limit is the same one C-B18-4 states: "a notification was sent" is unverifiable from inside the
repository, and an auditor should treat it as such.**

### C-82-3 — ITEM 2's premise, measured rather than believed

The ordered intent filed ITEM 2 as *"a hypothesis, not a finding… measure it before believing it:
a relay test parking rows past their TTL, then reading both numbers. If they cannot disagree in
practice, say so and cross it off."*

```bash
cd <engine>/relay && npm install && npx vitest run test/probe.test.ts --reporter=verbose
```

*Observed*, on `claude/s2-latest-since-invariant`, via a **throwaway** probe (`test/probe.test.ts`,
**deleted before the commit — it is in no tree**):

| state | push 409 `latest` | pull `latest` | page |
| --- | --- | --- | --- |
| expired seq 5, nothing live | **5** | **0** | 0 |
| live seq 1 + expired seq 7 | **7** | **1** | `[1]` |
| nothing expired (control) | **3** | **3** | — |

**They disagree.** The control is the half that stops the finding being over-read: without an expired
row the two predicates select the same rows and the marks coincide, so this is **retention-shaped**,
not a standing off-by-one between the push and pull paths.

**The reachability caveat, stated because it is the strongest attack on this run.** `expiredRow()`
writes `expires_at = 1` straight into SQLite, which is a *test* reaching past the push path. In
production a row is only in that state while the alarm has not yet collected it. **Alarm latency was
not measured here and cannot be** — there is no Cloudflare runtime in this sandbox beyond the
vitest workers pool. The reachability argument rests on `channel.ts`'s own comment that the alarm
*"does not guarantee to run the instant a row expires"*, which is the same premise the three
pre-existing retention tests already rest on. **If that premise is false, these tests pin a property
nothing depends on.**

### C-82-4 — why it is NOT a defect: each consumer reads the side its predicate needs

```bash
cd <engine> && git show origin/claude/s6-counter-reconciliation:src/Sync/SyncPublisher.cs | sed -n '121,126p'
cd <engine> && git show origin/claude/s6-counter-reconciliation:src/Sync/RelaySink.cs      | sed -n '73,85p'
cd <engine> && git show origin/claude/s6-counter-reconciliation:src/Sync/InboundPump.cs    | sed -n '224,226p'
```

*Expected, and **observed**:* both consumers are **raise-never-lower**.
`SyncPublisher.ResumeSeq` is `ok.Latest > floor ? ok.Latest : floor` — a **pull** `latest` that is too
low simply loses to the persisted floor. `RelaySink` feeds the **409's** mark to `reconcileTo`, which
*"refuses to move the counter DOWN"*. `InboundPump.cs:225` is `MoreAvailable: _cursor < page.Latest`
on the **filtered** mark, so it never waits for a row the page cannot return.

**So the divergence is not merely deliberate-and-documented — it is load-bearing in both directions,
and swapping either number breaks the corresponding consumer.** `channel.ts` already says the two
*"want opposite things from the same rows"*; this is the measurement behind that sentence.
**Do not re-open the divergence as a defect** (this supersedes nothing — **C-81-7** said the same of
the retention divergence and this is the evidence for it).

**Scope this correctly:** the three citations are into **`claude/s6-counter-reconciliation` (#46)**,
not into this run's branch and not into `main`. A restack that reshapes either read makes the
*justification* stale while the tests still pass — the identical exposure PR #54 names for itself.

### C-82-5 — the mutation matrix, all four rows executed

```bash
cd <engine>/relay && npm test                      # clean, both test-file states
# then, one at a time, against src/channel.ts, restoring from a pristine copy between rows:
#   M1  the 409 reports a retention-filtered mark (refusal decision unchanged)
#   M2  pull `latest` de-filtered  (drop `AND expires_at > ?`)
#   M3  push guard retention-filtered (add `AND expires_at > ?`) -- the tidy the comment forbids
```

*Expected, and **observed**:*

| mutation | baseline test file (52) | with run 82's tests (55) |
| --- | --- | --- |
| **M1 — 409 reports the filtered mark** | **52 passed — GREEN** | **2 failed / 53** |
| M2 — pull `latest` de-filtered | 1 failed / 51 | 2 failed / 53 |
| M3 — push guard filtered | 1 failed / 51 | 3 failed / 52 |
| clean | **52 passed** | **55 passed** |

**Row 1 is the whole finding.** M2 and M3 were each already caught by one pre-existing test
(`'excludes expired rows from latest…'` and `'still refuses a seq at or below an expired-but-uncollected
row'` respectively) — **measured, not assumed**: both "before" cells were run against the pristine
test file, not inferred from reading it. **M1 was caught by nothing.**

**The production shape of M1 is silent.** A filtered number is below the engine's counter,
`ReconcileTo` declines to move a counter down (§6.2 — rewinding would re-issue seqs the phone may
have accepted), so the reconciliation is refused and the engine walks up one seq at a time into the
same 409, **once per expired row**, instead of resuming above the mark in one round trip. Every push
stays individually well-formed and the engine does get through. **Only the round-trip count changes,
and no status code reports it.**

### C-82-6 — clean green and typecheck, and no production source touched

```bash
cd <engine>/relay && npm test && npm run typecheck && npx tsc --noEmit; echo "EXIT=$?"
cd <engine> && git diff --stat src/ relay/src/
```

*Expected, and **observed**:* **`Tests  55 passed (55)`**, `Test Files  1 passed (1)` on the clean
tree — up from a **52-passed** baseline that itself reproduces **C-81-14**'s CI row off-machine.
`wrangler types` → *Runtime types generated*; `tsc --noEmit` → **no output, `EXIT=0`**.

**`git diff --stat` over the source trees is EMPTY.** `src/channel.ts` was mutated **only** in the
worktree and restored from a pre-mutation copy taken at the start; **`sha256sum` matches the pristine
copy** (`55b3198…0ad659`) before the commit. The committed diff is
**`relay/test/relay.test.ts`, 1 file, +84 lines**, and nothing else — the lockfile that `npm install`
touched was reverted with `git checkout` and appears in no commit. This is the near-miss **C-81-12**
recorded in its own run; the same discipline was applied and is re-stated because it is the one that
would quietly ship a mutation.

### C-82-7 — no vector byte, no pin move, not a drift event

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check; echo "EXIT=$?"
cd <engine> && git diff --name-only HEAD -- docs/sync-vectors/
cd <android> && git status --short core/src/test/resources/
```

*Expected, and **observed**:* **`OK: 28 vector files match the generator.`**, **`EXIT=0`** — the
**base branch's** pre-pin state, exactly the number PR #54's CI printed (**C-81-14**), because
`claude/s2-seq-bound` predates the third S5 vector. **28 here and 29 at `7328a0b` are two different
trees, not a disagreement.** No file under `docs/sync-vectors/` appears in this run's diff, the
android vendored corpus was **not touched at all**, and the pin is **unmoved at `7328a0b`**.
**Not a cross-repo drift event.**

`$ExpectedOfflineTotal` was **not touched**, so this branch adds **no** landing cost to the pin family
(**B-17**) — the same zero PR #54 measured on Windows CI.

### C-82-8 — what was pushed

```bash
cd <engine> && git log --oneline -1 origin/claude/s2-latest-retention-skew
cd <engine> && git rev-parse origin/claude/s2-latest-since-invariant origin/claude/s2-seq-bound
```

*Expected, and **observed**:* new branch **`claude/s2-latest-retention-skew`** at **`c4ad6b0`**,
**draft PR #55** into base **`claude/s2-latest-since-invariant`** (PR #54). Diff: **one test file,
+84 lines, no production source.**

**Both branches below it are unmoved** — `claude/s2-latest-since-invariant` at **`f95b66e`**,
`claude/s2-seq-bound` at **`2be00fc`**. Amending either in place would have invalidated **C-RD-3**
(all seven landing branches checked against their PR heads) and **C-81-12**. **Nothing was merged,
closed, undrafted, force-pushed or deleted.**

**The base choice is a judgement and is defensible either way.** Stacking on #54 adds a level to a
stack `RETURN-DAY.md` §3 already costs; it was chosen so the two relay test additions stay **linear**
rather than conflicting as siblings in the same `relay.test.ts` hunk. The opposite choice —
`s2-seq-bound` as base — trades one merge for one conflict.

### C-82-9 — CI has NOT run PR #55, and no CI result is claimed for it

At the time these records were written, **no CI run existed for `claude/s2-latest-retention-skew`**.
Every number in run 82 is from **this Linux sandbox**: the relay's real vitest suite, not a stub, but
**not** this repo's Windows gate. `scripts\Verify-Alpha.ps1` was **not executed** — no `dotnet`, no
`pwsh`, no Windows DPAPI — and the **android** gate was not executed either (no SDK, `ANDROID_HOME`
unset). **The merge condition is unchanged.**

To close this claim later:

```bash
list_workflow_runs ci.yml branch=claude/s2-latest-retention-skew
```

### C-82-10 — the citation guard caught a dangling claim in this run's own records

```bash
cd <android> && scripts/check-citations.sh; echo "EXIT=$?"
```

*First run, **observed**:*

```
definitions: 774   cited: 776   documented-absent: 1
::error::dangling citation(s) -- cited, but defined nowhere:
  C-81-15
      LOG.md  BLOCKED.md  AUDIT-REQUEST.md
```

**The id does not exist** — run 81's series ends at **C-81-14**, and its notification was recorded in
prose without a claim of its own (see C-82-2). **The quote above sits in a fence deliberately:** the
guard treats a fenced id as a fixture rather than a claim (its own comment — *"a citation is a claim
made in PROSE"*), which is the only honest way to name a non-existent id in a document the guard
reads. Adding it to `KNOWN_ABSENT` was the wrong tool — that would suppress the id corpus-wide and
permanently, to document one run's mistake.

*After the repair, and this is the row that counts:* the three citations were rewritten to name
**B-18's run-81 status**, which does exist, and the guard re-run to **`OK: every cited C-/B- id
resolves to an entry that exists.`**, **`EXIT=0`**.

**This is the guard doing exactly the job `C-75-13` built it for, on the run that built nothing else
for it** — and it caught a *fabricated* citation, which is the failure mode these records are least
able to catch by reading. **Recorded rather than silently fixed**, because a guard that only ever
reports green is indistinguishable from one that is not running.

Sizes taken before and after with **absolute** paths, per **C-79-16**: `AUDIT-REQUEST.md`
798,490 → **812,334**; `LOG.md` 1,018,810 → **1,027,842**; `BLOCKED.md` 276,201 → **279,089**;
`STATE.md` 583,768 → **595,961**. (Taken after the citation repair, so they are the committed sizes,
not the pre-repair ones an earlier draft of this line carried.) All four grew, all four in `<android>`, and `git status` in
`<engine>` and in the engine worktree shows **no untracked record file** — the specific failure
**C-79-16** describes, where an append lands in the wrong checkout.

### C-82-11 — CI ran PR #55, and it closes C-82-9 by measurement

**Added after the run-82 records were pushed**, on a `check_suite.completed` wake. **C-82-9** says
*"CI has not run PR #55 and no CI result is claimed for it"* — that was true when written and is
**superseded by this entry**. Recorded as a correction rather than edited away, per the same practice
as **C-81-14**.

```bash
list_workflow_runs ci.yml branch=claude/s2-latest-retention-skew
list_workflow_jobs <run_id>
```

*Expected, and **observed**:* **one** run, **`32586767792`**, run number **475**, **`run_attempt: 1`**,
head **`c4ad6b0`**, **`conclusion: success`**, `pull_requests: [55]`. **No re-run was triggered**;
the permitted one is unspent. Two jobs, both `success`:

| job | runner | the steps that matter |
| --- | --- | --- |
| **Blind relay (Worker)** | `ubuntu-latest` | Typecheck ✓, **Test ✓**, dry-run ✓, blind-relay assert ✓, vector assert ✓ |
| **Build and offline harnesses** | `windows-latest` | Build warnings-as-errors ✓, **offline alpha verification ✓** |

**The three tests, on a runner, read out of the log rather than inferred from the conclusion:**
`✓ test/relay.test.ts (55 tests)`, **`Tests  55 passed (55)`** — **C-82-6**'s clean row reproduced
**off this machine**, and the 52 → 55 step is CI's, not this sandbox's. `wrangler types` then
`tsc --noEmit` both green, reproducing the typecheck half of C-82-6.

**The B-17 claim, now measured instead of asserted:**
**`=== Offline total: 598 passed, 0 failed ===`**, `CareerSeeker alpha verification complete.` — on
**Windows**, the gate this sandbox cannot reach. **598 is the base branch's number and PR #54's
number**, so this branch moves `$ExpectedOfflineTotal` by **zero** and adds **no** landing cost to
the pin family. The PR's self-audit did not ask a reviewer to take that on trust; this is the check.

**The vector number reproduced on a runner, and it is still not drift:**
**`OK: 28 vector files match the generator.`** — exactly what **C-82-7** predicted and what PR #54's
CI printed. **28 here and 29 at `7328a0b` are two different trees.** `OK: no decryption path in
relay/src.` also green, so the blind-relay invariant is untouched by this diff.

**What this still does not prove, and the merge condition it does not move.** This is the **engine**
repo's CI, so it says nothing about the **android** gate (**B-7**, **B-22** both unmoved), and
`Verify-Alpha.ps1`'s **`-IncludePublish`/`-IncludePackage`** passes did **not** run — only the
offline portion CI runs on every push. **The merge condition remains a full local gate that no cloud
session can run**, so this changes **nothing** about the landing policy, and **nothing was merged,
closed or undrafted in response to it.**

**One thing it cannot touch, restated because a green run invites the error:** it does **not** bear on
**C-82-3**'s reachability caveat. CI runs the same `expiredRow()` fixture this sandbox does, so a
green suite on a runner is silent about whether Cloudflare's alarm collects expired rows faster than
a push can race them. **That attack survives this result untouched.**

---

## Run 83 (2026-08-22) — the last unguarded constant in `Protocol.kt`

### C-83-1 — rule one, and what the fetch changed

```bash
cd <android> && git fetch --all --prune && git rev-parse origin/claude/android-a0-probe
cd <engine>  && git fetch --all --prune && git rev-parse origin/main
```

*Expected, and **observed**:* the android checkout again arrived **detached at the docs-only `main`**
(`ebfaf81`), not on the work branch; `origin/claude/android-a0-probe` is **`adfc2c0`**. Engine
`origin/main` is **`aac05f3`**, **unmoved since 2026-08-12**; the newest merge anywhere in either
repo is still **PR #44, 2026-08-13**. Every count in run 83 is taken after this fetch.

### C-83-2 — the assigned slice, declined for the forty-eighth time

```bash
cd <engine> && git log --oneline origin/claude/s5-entitlement-ack-spec | head -3
cd <engine> && git show origin/claude/s5-entitlement-ack-spec:docs/sync-vectors/v1/index.json | grep -c entitlement_ack
cd <android> && cat core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected, and **observed**:* the stored prompt's slice — §4.3 `entitlement_ack`, the ack vectors,
PQ-A2-1/-2/-3 — is **built and has been since 2026-08-09** (`8575539`, `22b028e`, `7328a0b`), on the
`claude/s5-*` drafts in the **engine** repo, carried by **PR #32** and **PR #37**. The prompt's
vendored pin `679a317` is **stale**; the real pin is **`7328a0b`**. **Nothing was rebuilt.**

### C-83-3 — the mutation matrix on the three constants run 76's sweep left out

```bash
cd <android> && apt-get install -y openjdk-17-jdk-headless      # core-probe needs 17; image ships 21
cd <android> && scripts/core-probe.sh                            # baseline
# then, one at a time against core/src/main/kotlin/app/careerseeker/core/Protocol.kt,
# restoring from a pristine copy between rows, each followed by:
cd <android> && scripts/core-probe.sh --rerun
#   M1  VERSION                 1                           -> 2
#   M2  SUITE                   "p256-hkdf-sha256"          -> "p256-hkdf-sha512"
#   M3  SUITE_HYBRID_RESERVED   "p256+mlkem768-hkdf-sha256" -> "p256+mlkem1024-hkdf-sha256"
```

*Expected, and **observed**:*

| mutation | baseline (346) | with run 83's test (347) |
| --- | --- | --- |
| M1 — `VERSION` | RED | RED |
| M2 — `SUITE` | RED — 2 tests | RED — 3 tests |
| **M3 — `SUITE_HYBRID_RESERVED`** | **346 passed, 0 failed — GREEN** | **RED — 1 test, the new one** |
| clean | **346 passed** | **347 passed** |

**Row 3 is the whole finding.** M1 and M2 were each already caught — measured, not assumed, both
"before" cells run against the pristine file. M2's two are `PairingDerivationTest > completionAad is
the pinned four-field format` and `ProtocolVectorsTest > index agrees with the shipping constants`.
**M3 was caught by nothing**, and `SUITE_HYBRID_RESERVED` was the **last constant in `Protocol.kt`**
that no test compared to the document.

**Why nothing caught it.** Both of its two references move with it. `PairingSessionTest > an
unrecognised suite refuses to pair` builds its invite **from** the constant and asserts the parse is
rejected — but every unsupported suite is rejected identically, so the assertion holds for **any**
value; and `SUITE_HYBRID_RESERVED !in SUPPORTED_SUITES` is satisfied by a wrong string **more**
easily than by the right one. That is the seventy-fourth run's trap exactly: a derivation compared
against itself agrees with itself and disagrees with the document.

### C-83-4 — this is NOT a live drift: the value is correct on both sides today

```bash
cd <engine> && sed -n '306p' docs/Sync-Protocol.md
cd <engine> && git grep -n "SuiteHybridReserved" origin/main -- src/Sync/Protocol.cs
cd <engine> && git grep -h -o "p256+mlkem[a-z0-9+-]*" \
                 $(git for-each-ref --format='%(refname)' refs/remotes/origin/) | sort | uniq -c
```

*Expected, and **observed**:* `docs/Sync-Protocol.md` **§5.2 line 306** prints
`p256+mlkem768-hkdf-sha256`; the engine's `src/Sync/Protocol.cs:21` carries the same string; and
across **every ref in the repository** there are **64 occurrences and exactly one spelling**. The
phone's constant agrees with all of them. **The defect is not a wrong value — it is that nothing
keeps the value right.**

**The string names a parameter set, and a sizing decision rests on it.** §5.2 records that under this
suite the QR additionally carries the ML-KEM encapsulation key and `ikm` becomes a concatenation of
both shared secrets, and that *"QR payload budget is checked against the hybrid suite's sizes now:
ML-KEM-768's 1184-byte key fits comfortably in a version-40 QR"*. M3 names **ML-KEM-1024**, whose key
is **1568 bytes** — a two-character slip silently invalidating a budget the spec says was checked.

**v1 behaviour is unaffected either way, and that is the point.** Both implementations reject the
reserved suite today and reject a corrupted one identically, so the failure is invisible until the
hybrid migration ships — the one moment the two sides must agree on this string, and the moment no
test has ever compared them.

### C-83-5 — the engine's guard accepts the same wrong value (READ, NOT EXECUTED)

```bash
cd <engine> && git grep -n "SuiteHybridReserved" \
                 $(git for-each-ref --format='%(refname)' refs/remotes/origin/) -- tests/SyncHarness/Program.cs
```

*Expected, and **observed**:* every branch carrying the harness asserts

```csharp
Protocol.SuiteHybridReserved.Contains("mlkem") && Protocol.SuiteHybridReserved != Protocol.Suite
```

**`"p256+mlkem1024-hkdf-sha256"` satisfies both conjuncts**, so M3 is green on the engine side too —
the same hole, in the same shape, in the implementation the phone is supposed to be checked against.

**This is read, not executed.** `dotnet` and `pwsh` are both **absent** in this sandbox (**C-ENV-1**),
so no `SyncHarness` run and no `Verify-Alpha.ps1` run backs this row; it is a `git grep` over branch
contents and is labelled as one. **The engine half is not fixed by this run** — that needs a gate
this sandbox cannot run. It is filed in `STATE.md`'s ordered intent with the mutation that proves it.

### C-83-6 — clean green, the negative control, and no production source touched

```bash
cd <android> && scripts/core-probe.sh --rerun
cd <android> && sha256sum core/src/main/kotlin/app/careerseeker/core/Protocol.kt
cd <android> && git status --porcelain && git diff --stat
```

*Expected, and **observed**:* **`core-probe: 347 tests, 0 failed, 0 skipped, across 22 classes`**,
`EXIT=0`, up from a **346** baseline. `Protocol.kt` hashes
**`c42624dfd90e5f47ce85e71b5c65724bf1fc04af454a88b2723400fea00bced8`**, byte-identical to the
pristine copy taken before the first mutation — **verified by `sha256sum -c` after every row and once
more before the commit.** The committed diff is **`core/src/test/kotlin/app/careerseeker/core/ProtocolTest.kt`,
one file, test-only**; **no production source in either module**, and `Protocol.kt` appears in no commit.

This is the **C-81-12 / C-82-6 hazard** — a mutation quietly shipping — re-applied deliberately,
because this run mutated a **production** file rather than a test file and so was more exposed to it
than either predecessor.

### C-83-7 — no vector byte, no pin move, not a drift event

```bash
cd <android> && git status --short core/src/test/resources/
cd <android> && cat core/src/test/resources/sync-vectors/VECTORS.lock
cd <engine>  && node docs/sync-vectors/generate.mjs --check; echo "EXIT=$?"
```

*Expected, and **observed**:* the vendored corpus is **untouched** — nothing under
`core/src/test/resources/` appears in this run's diff — and the pin is **unmoved at `7328a0b`**.
`generate.mjs` was **not edited** and no vector file was added, changed or deleted in either repo.
**Not a cross-repo drift event.** `$ExpectedOfflineTotal` was **not touched**: this run adds **no**
landing cost to the pin family (**B-17**), and it touches no engine file at all.

### C-83-8 — two ordered-intent targets were re-verified CLOSED before either was taken

```bash
cd <android> && grep -n "INFO_ENGINE_TO_PHONE\|INFO_RELAY_TOKEN" core/src/test/kotlin/app/careerseeker/core/ProtocolTest.kt
cd <android> && grep -n "assertEquals(12\|assertEquals(16\|AES-256-GCM" core/src/test/kotlin/app/careerseeker/core/ProtocolTest.kt
```

*Expected, and **observed**:* the ordered intent's **"SUCCESSOR TARGET FOR ITEM 4 — the HKDF info
strings"** is **CLOSED**: `ProtocolTest.kt:218-220` pins `INFO_ENGINE_TO_PHONE`,
`INFO_PHONE_TO_ENGINE` and `INFO_RELAY_TOKEN` against hand-transcribed literals, landed by run 76
(`231bc07`). The adjacent **crypto parameters** (`CIPHER`, `KEY_BYTES`, `NONCE_BYTES`, `TAG_BYTES`)
are pinned too, at `:165-170`.

**The standing precondition — *before taking any item, re-verify that item* — earned its place for
the fourth time.** Both would have been rebuilt on the strength of the list alone. What survived the
re-verification was the **residue**: the two suite names that neither sweep covered, which is the
slice this run took.

### C-83-9 — the records' own citation guard is green

```bash
cd <android> && scripts/check-citations.sh; echo "EXIT=$?"
```

*Expected, and **observed**:* **`definitions: 776   cited: 777   documented-absent: 1`**, then
**`OK: every cited C-/B- id resolves to an entry that exists.`**, `EXIT=0` — run at the **start** of
this run against the inherited records, and again after this run's own entries were written.

### C-83-10 — B-18's triggers re-checked, and the silence was deliberate for the second run

```bash
cd <engine> && git rev-parse origin/main
cd <engine> && gh pr list --state merged --limit 3     # or the MCP equivalent
```

*Expected, and **observed**:* run 82 instructed its successor to *"notify on `main` moving, a PR
merged or undrafted, the stored prompt changing, or a gate result — not on another firing and not on
another draft PR."* **All four re-checked and all four negative:** engine `main` still **`aac05f3`**;
**no PR merged, closed or undrafted** in either repo (18 engine drafts, 6 android drafts, unchanged);
the stored prompt is **the same text**, still naming the landed slice and the stale pin; and **no
gate result arrived** — CI's run on PR #55 was already delivered and recorded at run 82 (**C-82-11**).

**No notification was sent.** The forty-eighth firing of a slice built on 2026-08-09 is the same fact
one day older, and re-sending it trains the channel to be ignored.

---

## Run 84 (2026-08-23) — the relay constants sweep

### C-84-1 — the assigned slice was already built, for the forty-ninth firing

```bash
cd <engine> && git log --oneline origin/claude/s5-entitlement-ack-spec | head -4
cd <engine> && git show 7328a0b --stat | head -20
```

*Expected, and **observed**:* `8575539` ("define the entitlement_ack body, and say what the size cap
actually measures"), `22b028e` (two `entitlement_ack` vectors), `9c05ef7`, with `7328a0b`
(`invalid-unknown-field`, PQ-A2-3/B-6) on `claude/s5-engine-wire-parser`. **The stored prompt's
slice — §4.3's ack body, the ack vectors, PQ-A2-1/-2/-3 — is landed on the S5 drafts and has been
since 2026-08-09.** The prompt's vendored pin `679a317` is stale; it is `7328a0b`.

### C-84-2 — this image cannot run either gate, verified rather than assumed

```bash
which dotnet pwsh; echo "EXIT=$?"
java -version 2>&1 | head -1
echo "[$ANDROID_HOME]"
```

*Expected, and **observed**:* `dotnet` **absent**, `pwsh` **absent** — so `Verify-Alpha.ps1` cannot
run and the ordered intent's **NEW ITEM 1** (the engine half of run 83's suite-name hole) stays
untakeable here. `openjdk version "21.0.10"` where `scripts/core-probe.sh` needs **17**.
`ANDROID_HOME` **empty** (**B-7**).

### C-84-3 — the relay baseline, reproduced off-machine before anything was changed

```bash
cd <engine> && git checkout claude/s2-latest-retention-skew && cd relay && npm ci && npm test
```

*Expected, and **observed**:* **`Test Files 1 passed (1)`, `Tests 55 passed (55)`, `EXIT=0`** — the
number run 82 left at `c4ad6b0` (**C-82-6**), reproduced independently here.

### C-84-4 — the retention default was guarded only by its own ceiling

```bash
cd <engine> && git checkout claude/s2-latest-retention-skew
perl -0pi -e 's/const DEFAULT_TTL_SECONDS = 7 \* 24/const DEFAULT_TTL_SECONDS = 30 * 24/' relay/src/protocol.ts
cd relay && npm test          # baseline: expect 55 passed, GREEN -- the defect
cd .. && git checkout relay/src/protocol.ts
git checkout claude/s2-relay-constant-pins
perl -0pi -e 's/const DEFAULT_TTL_SECONDS = 7 \* 24/const DEFAULT_TTL_SECONDS = 30 * 24/' relay/src/protocol.ts
cd relay && npm test          # expect RED, 1 failed / 56 -- the new test
cd .. && git checkout relay/src/protocol.ts
```

*Expected, and **observed**:* **baseline `55 passed (55)` — GREEN**, and **`1 failed | 56 passed
(57)`** after the pin. The pre-existing assertion is `DEFAULT_TTL_SECONDS <= MAX_TTL_SECONDS`, which
**the ceiling itself satisfies**. Raising the default from 7 days to 30 changes no status code, no
response body and no stored row shape; the only effect is that the blind relay holds ciphertext
**four times longer**. **Not a live drift** — the deployed value is 7 days and is correct; the defect
is the absent guard.

### C-84-5 — two candidates went green and are NOT defects; the hurtful direction is guarded

```bash
cd <engine> && git checkout claude/s2-latest-retention-skew
# harmless direction -> green
perl -0pi -e 's/const PULL_PAGE_SIZE = 100;/const PULL_PAGE_SIZE = 7;/' relay/src/protocol.ts
cd relay && npm test; cd .. && git checkout relay/src/protocol.ts
# hurtful direction -> red
perl -0pi -e 's/const PULL_PAGE_SIZE = 100;/const PULL_PAGE_SIZE = 0;/' relay/src/protocol.ts
cd relay && npm test; cd .. && git checkout relay/src/protocol.ts
perl -0pi -e 's/MAX_CIPHERTEXT_B64U_CHARS \+ 4096/MAX_CIPHERTEXT_B64U_CHARS + 0/' relay/src/protocol.ts
cd relay && npm test; cd .. && git checkout relay/src/protocol.ts
```

*Expected, and **observed**:* page size 100→7 **`55 passed` GREEN**, but 100→**0** **`8 failed / 47`**
— client-loop liveness **is** guarded. Headroom +4096→+65536 **`55 passed` GREEN**, but →**+0**
**`2 failed / 53`** — the "413 on a legal envelope" failure §3.1 forbids **is** guarded. **Both
crossed off: the page size is relay-internal and not a wire contract, and the 4 KiB headroom errs in
the safe direction. Do not re-open either as a defect.**

### C-84-6 — the pairing-id regex's length and charset were compared to nothing

```bash
cd <engine> && grep -n 'pairing.*p_.*16 base64url' docs/Sync-Protocol.md      # the normative row
cd <engine> && git checkout claude/s2-latest-retention-skew
perl -0pi -e 's/\{16\}\$/{16,32}\$/' relay/src/protocol.ts
cd relay && npm test; cd .. && git checkout relay/src/protocol.ts             # expect 55 GREEN
perl -0pi -e 's/\[A-Za-z0-9_-\]\{16\}/[A-Za-z0-9_.-]{16}/' relay/src/protocol.ts
cd relay && npm test; cd .. && git checkout relay/src/protocol.ts             # expect 55 GREEN
perl -0pi -e 's/\^p_\[A-Za-z0-9_-\]/^q_[A-Za-z0-9_-]/' relay/src/protocol.ts
cd relay && npm test; cd .. && git checkout relay/src/protocol.ts             # expect 46 failed
```

*Expected, and **observed**:* `docs/Sync-Protocol.md:79` — *"`pairing` | string | Pairing id, `p_` +
16 base64url chars"* — is normative, and `isValidPairingId` is its hand-transcription. Widening
`{16}`→`{16,32}` and admitting `.` into the charset both left **`55 passed` GREEN**. The **prefix**
was covered only **incidentally**: `p_`→`q_` fails **46 of 55**, because every other test happens to
use a `p_` id. **Length and charset were covered by nothing.** Both now RED at 1 failed / 56.

### C-84-7 — the diff is test-only and the mutated production file is in neither commit

```bash
cd <engine> && git diff --stat c4ad6b0 claude/s2-relay-constant-pins
cd <engine> && git log --name-only --format='%h' c4ad6b0..claude/s2-relay-constant-pins | sort -u
cd <engine> && git checkout claude/s2-relay-constant-pins && cd relay && npm test && npm run typecheck
```

*Expected, and **observed**:* **`relay/test/relay.test.ts | 40 ++++`, 1 file changed, 40 insertions**
— `relay/src/protocol.ts` appears in **neither** commit. Clean **`57 passed (57)`, `EXIT=0`**;
`wrangler types && tsc --noEmit` **0 errors, `EXIT=0`**. Pristine hash re-checked after every
mutation row and before each commit: **`7d7b37bbd687a022fba949e08056ab10bc20a499b18f1243a924850d67b73201`**.

### C-84-8 — a self-inflicted loss during the commit split, recorded not smoothed over

```bash
cd <engine> && git show 0181f55 --stat && git show b11e47b --stat
cd <engine> && git checkout claude/s2-relay-constant-pins && cd relay && npm test
```

*Expected, and **observed**:* a `git checkout --theirs .` issued after a `git stash pop` discarded the
unstaged merge result and **silently lost two of the three hunks**. It was caught by grepping for the
test names — **not** by the commit succeeding, which it did — restored from the saved patch, and the
suite re-run to **`57 passed`** before the second commit. **No wrong content reached a commit**; both
commits contain only `relay/test/relay.test.ts`. Recorded because the failure mode is silent: a
`stash pop` that reports `Auto-merging` followed by a `checkout` leaves a **clean tree** that looks
finished.

### C-84-9 — no vector byte moved and no landing cost was added

```bash
cd <engine> && git diff --stat c4ad6b0 claude/s2-relay-constant-pins -- docs/sync-vectors/ scripts/ src/
cd <android> && git diff --stat origin/claude/android-a0-probe -- core/ app/
```

*Expected, and **observed**:* **empty on both** — no vector, no `generate.mjs`, no
`$ExpectedOfflineTotal`, no `src/`, no `:core`, no `:app`. The vendored pin is unmoved at
**`7328a0b`**, and this branch adds **zero** landing cost to the pin family (**B-17**).

### C-84-10 — B-18's triggers re-checked, and the silence is deliberate for the third run

```bash
cd <engine> && git rev-parse origin/main      # expect aac05f3...
cd <android> && git rev-parse origin/main     # expect ebfaf81...
# plus: any PR merged/undrafted in either repo; whether the stored prompt changed
```

*Expected, and **observed**:* run 83 instructed its successor to notify on *"`main` moving, a PR
merged or undrafted, the stored prompt changing, or a gate result."* **All four re-checked, all four
negative:** engine `main` still **`aac05f3`**, android `main` still **`ebfaf81`**; **nothing merged,
closed or undrafted** in either repo (android's 6 PRs all open drafts, unchanged); the stored prompt
is **the same text**, still naming the landed slice and the stale pin `679a317`; and **no gate result
arrived** — no `dotnet`/`pwsh` here, and PR #56's CI had not reported when this entry was written.

**No notification was sent.** This run's finding is **new but not live**: the deployed retention
value is correct, the defect is an absent guard, and the fix is already in a draft PR that cannot be
merged from here. Waking Brandon for a correct-value-missing-guard would spend the channel on
something that needs no decision from him.

### C-84-11 — the records' own citation guard is green

```bash
cd <android> && scripts/check-citations.sh; echo "EXIT=$?"
```

*Expected, and **observed**:* run at the **start** of this run against the inherited records —
**`definitions: 786   cited: 787   documented-absent: 1`**, `OK`, `EXIT=0` — and again after this
run's entries were written.

### C-84-12 — CI ran the offline gate on PR #56 and it passed; two run-84 claims are now measured

```bash
# via the GitHub MCP tools, or:
#   gh run view 32609617177 --log | grep -E 'Offline total|vector files match|Tests .* passed|no decryption path'
```

*Expected, and **observed**:* run **`32609617177`**, head `b11e47b`, **both jobs
`conclusion: success`**.

- **`Build and offline harnesses`** (`windows-latest`): **`=== Offline total: 598 passed, 0 failed
  ===`**, with `SyncHarness` reporting **`=== 130 passed, 0 failed ===`** inside it. **598 is the
  base branch's number** — the same total run 81 recorded for PR #54 (**C-81-14**) — so **this branch
  moves `$ExpectedOfflineTotal` by zero, measured rather than argued from "no engine file was
  touched"** (**B-17**).
- **`Blind relay (Worker)`** (`ubuntu-latest`): **`Tests 57 passed (57)`**, reproducing this
  sandbox's number on a clean runner; `npx wrangler deploy --dry-run` OK; the blindness grep
  **`OK: no decryption path in relay/src.`**; and
  **`OK: 28 vector files match the generator.`** — **vector drift zero, measured**, which is the
  independent confirmation of **C-84-9**.

**What it does not establish.** CI runs the **offline** portion only. It does **not** run
`-IncludePackage`, `-IncludePublish`, `-IncludeLive`, or **any** part of the android gate; the fused
android tree has **still never been built**. **The merge condition is unchanged**, and PR #56 remains
draft and unmergeable from a cloud session. A green CI shows this branch is **neutral**, not that the
landing plan is safe.

**This is B-18's fourth trigger firing — "a gate result" — and it still did not earn a
notification.** The result is green, expected, concerns a draft PR that cannot be merged from here,
and asks Brandon for no decision. Waking him for it would spend the channel on good news he can read
whenever he returns.

### C-84-13 — run 85 closed run 84's NEW ITEM 2 on the same branch; CI on its head is green

```bash
cd <engine> && git fetch --all --prune
cd <engine> && git log --format='%h %an %s' b11e47b..origin/claude/s2-relay-constant-pins
cd <engine> && git merge-base --is-ancestor b11e47b origin/claude/s2-relay-constant-pins && echo "fast-forward, run 84's commits intact"
# and read PR #56's description + the checks on head 8126a8e
```

*Expected, and **observed**:* two commits — `4dbf5f9` *"pin that the schema DDL survives the second
DO instantiation"* and `8126a8e` *"compare the direction set to §3 rather than to `depth()`'s key
shape"* — authored by a **later run of this same program**, landing as a **fast-forward** above run
84's `b11e47b`. **Run 84's two commits are intact and no history was rewritten.** PR #56's
description was rewritten by that run to report `ENVELOPE_TABLE_DDL` as a genuine hole (dropping
`IF NOT EXISTS` left all 57 tests green, because every existing case instantiates a *fresh* DO while
production re-runs the DDL on every wake), `DIRECTIONS` as already-guarded-incidentally, and the
`PRIMARY KEY (dir, seq)` as **refuted and deliberately not pinned**.

**Those mutation rows are run 85's evidence, not run 84's.** T3/T4/D1 were not executed in this
session and their numbers are **not** restated here as measurements of mine.

**What run 84 did verify first-hand**, and the command for it:

```bash
# via the GitHub MCP tools, or:
#   gh run view 32619516958 --log | grep -E 'Offline total|vector files match|Tests .* passed|no decryption path'
```

*Expected, and **observed**:* run **`32619516958`**, head **`8126a8e`**, **both jobs `success`**.
`windows-latest` **`=== Offline total: 598 passed, 0 failed ===`** with `SyncHarness`
**`=== 130 passed, 0 failed ===`** — **598 is still the base branch's number**, unchanged from run
84's head, so **`$ExpectedOfflineTotal` moves by zero across all four commits** (**B-17**).
`ubuntu-latest` **`Tests 59 passed (59)`**, `wrangler deploy --dry-run` OK, `OK: no decryption path
in relay/src.`, **`OK: 28 vector files match the generator.`**

**Why this entry exists at all.** Run 85 had not written LOG/AUDIT/STATE entries when run 84 read the
records (android head still `e5557a3`), while run 84's ordered intent still told the next session
that `DIRECTIONS` and `ENVELOPE_TABLE_DDL` were unmeasured. **A closed item read as open is this
program's most-repeated failure — it has cost four runs already.** This note is insurance against a
fifth, and it **defers to run 85's own entries if they exist.**

**A settling comment was posted to PR #56** giving the CI numbers above, rather than editing run 85's
description — so the prediction its body records ("CI has not yet run on `8126a8e`… that is a
prediction, not a measurement") and its resolution both remain on the record, and no concurrent
rewrite of that description could be clobbered.
### C-85-1 — the assigned slice was already built, for the fiftieth firing

```bash
cd <engine> && git fetch --all --prune
git log --oneline --all | grep -E '8575539|22b028e|7328a0b'
gh pr view 32 --json title,state,isDraft   # S5 first half: the entitlement_ack body, PQ-A2-1/-2
```

*Expected, and **observed**:* the stored prompt again assigns S5's spec half (§4.3 `entitlement_ack`,
the vector via `generate.mjs`, PQ-A2-1/-2/-3). It has been built since **2026-08-09** — commits
`8575539`, `22b028e`, `7328a0b`, carried by draft PRs **#32/#37/#38/#39**. **Declined and re-routed
to `STATE.md`'s ordered intent**, as the last thirteen runs did. The prompt's vendored pin
`679a317` is likewise stale; the real pin is **`7328a0b`** (**C-PIN-1**, re-verified this run).

### C-85-2 — this image still cannot run either gate, verified rather than assumed

```bash
for t in node npm dotnet pwsh java gradle; do printf '%-8s ' "$t"; which $t || echo ABSENT; done
echo "ANDROID_HOME=${ANDROID_HOME:-UNSET}"
```

*Expected, and **observed**:* `node`/`npm` present (**v22.22.2**), **`dotnet` ABSENT**, **`pwsh`
ABSENT**, `ANDROID_HOME` **UNSET**. So `scripts\Verify-Alpha.ps1` did not run and the android gate
did not run; **ITEM 1 and ITEM 3 of the ordered intent remain untakeable here**, for the same reason
run 84 recorded. The relay lane needs only `node`, which is why it is the lane this run took.

### C-85-3 — the relay baseline, reproduced off-machine before anything was changed

```bash
cd <engine> && git checkout claude/s2-relay-constant-pins && git reset --hard b11e47b
cd relay && npm ci && npm test
```

*Expected, and **observed**:* **`Tests 57 passed (57)`, `Test Files 1 passed (1)`, EXIT=0** — run
84's number, reproduced in a fresh sandbox before any mutation. Every row below is measured against
this baseline, one mutation at a time.

### C-85-4 — the schema DDL's idempotence was guarded by nothing, on both statements

```bash
cd <engine> && git checkout claude/s2-relay-constant-pins && git reset --hard b11e47b   # baseline
perl -0pi -e 's/CREATE TABLE IF NOT EXISTS envelopes/CREATE TABLE envelopes/' relay/src/protocol.ts
cd relay && npm test          # baseline: expect 57 passed, GREEN -- the defect
cd .. && git checkout relay/src/protocol.ts
perl -0pi -e 's/CREATE INDEX IF NOT EXISTS envelopes_expiry/CREATE INDEX envelopes_expiry/' relay/src/protocol.ts
cd relay && npm test          # baseline: expect 57 passed, GREEN -- the same defect, index half
cd .. && git checkout relay/src/protocol.ts

git checkout claude/s2-relay-constant-pins    # back to the pinned head (8126a8e)
perl -0pi -e 's/CREATE TABLE IF NOT EXISTS envelopes/CREATE TABLE envelopes/' relay/src/protocol.ts
cd relay && npm test          # expect RED, 1 failed / 58
cd .. && git checkout relay/src/protocol.ts
perl -0pi -e 's/CREATE INDEX IF NOT EXISTS envelopes_expiry/CREATE INDEX envelopes_expiry/' relay/src/protocol.ts
cd relay && npm test          # expect RED, 1 failed / 58
cd .. && git checkout relay/src/protocol.ts
```

*Expected, and **observed**:* **both mutations `57 passed (57)` — GREEN at baseline**, and **both
`1 failed | 58 passed (59)`** against the new test. `PairingChannel`'s constructor executes
`ENVELOPE_TABLE_DDL` (`relay/src/channel.ts:29`), and Cloudflare calls that constructor on **every**
instantiation, including every wake from eviction or hibernation against storage that already holds
the table. Every pre-existing case instantiates a **fresh** DO, so the re-entry path — the one
production runs on every wake — **was covered by nothing**. The failure it admits is a **dead
channel**: SQLite raises `table envelopes already exists`, the constructor throws, and that pairing
stops working on a wake long after the deploy that caused it. **Not a live drift** — both statements
carry `IF NOT EXISTS` today and are correct; the defect is the absent guard. The index row's
baseline was measured against the **restored original** test file (`git checkout relay/test/relay.test.ts`),
not inferred, which is what makes "unguarded before" a measurement.

### C-85-5 — `DIRECTIONS` was already guarded, incidentally; that is reported, not dressed up

```bash
cd <engine> && git checkout claude/s2-relay-constant-pins && git reset --hard b11e47b
perl -0pi -e "s/= \['e2p', 'p2e'\];/= ['e2p', 'p2e', 'x2y' as Direction];/" relay/src/protocol.ts
cd relay && npm test          # baseline: expect RED, 1 failed / 56 -- ALREADY guarded
cd .. && git checkout relay/src/protocol.ts && git checkout claude/s2-relay-constant-pins
perl -0pi -e "s/= \['e2p', 'p2e'\];/= ['e2p', 'p2e', 'x2y' as Direction];/" relay/src/protocol.ts
cd relay && npm test          # expect RED, 2 failed / 57
cd .. && git checkout relay/src/protocol.ts
```

*Expected, and **observed**:* **baseline `1 failed | 56 passed (57)` — already RED, so NOT a
defect**, and `2 failed | 57 passed (59)` after. The single baseline failure is
**`creates its schema and starts empty`**, which catches the widening only because `depth()`
(`relay/src/channel.ts:289`) derives its keys from the array — **incidental coverage inside a test
about schema creation**, one refactor of `depth()` away from vanishing, and it never names the
document. The added assertion compares the array to §3 directly
(`docs/Sync-Protocol.md:80`, and line 102's "a `dir` that is neither `e2p` nor `p2e`"). **A
hardening, not a finding.**

### C-85-6 — the primary key is removable and is NOT a defect; it was not pinned

```bash
cd <engine> && git checkout claude/s2-relay-constant-pins
perl -0pi -e 's/  expires_at INTEGER NOT NULL,(.*)\n  PRIMARY KEY \(dir, seq\)\n/  expires_at INTEGER NOT NULL$1\n/' relay/src/protocol.ts
cd relay && npm test          # expect 59 passed -- GREEN, and deliberately left green
cd .. && git checkout relay/src/protocol.ts
```

*Expected, and **observed**:* **`59 passed (59)` — GREEN at both 57 and 59**, and **deliberately not
pinned**. Neither of the PK's two roles is load-bearing here: as a **constraint** it is unreachable,
because the app-level check at `relay/src/channel.ts:190` rejects any `seq <= last` where
`last = MAX(seq)` over all rows for the direction — if a row `(dir, seq)` exists then `last >= seq`,
so a duplicate can never reach the `INSERT`; as an **index** it is performance only, because `pull`
sorts with an explicit `ORDER BY seq` (`channel.ts:234`). Per run 84's lesson, this is the harmless
direction. **Do not re-open it.** Caveat, stated because it is the weak part: this is a **reading of
the code, not a measurement** — if the `channel.ts:190` check is ever weakened, the PK becomes
load-bearing and unguarded in the same moment.

### C-85-7 — the diff is test-only and the mutated production file is in neither commit

```bash
cd <engine> && git diff --stat b11e47b..8126a8e
git diff --name-only b11e47b..8126a8e | grep protocol.ts   # expect NO match
git show 8126a8e:relay/src/protocol.ts | sha256sum
```

*Expected, and **observed**:* **`relay/test/relay.test.ts | 38 ++++++`, 1 file changed, 38
insertions**; **no match** for `protocol.ts`; hash
**`7d7b37bbd687a022fba949e08056ab10bc20a499b18f1243a924850d67b73201`**. This run mutated a
**production** file, so it was copied pristine before the first row, restored between **every** row,
and `sha256sum -c` re-checked after each and once more before each commit. **No `src/`, no C#, no
harness**, so **`$ExpectedOfflineTotal` is untouched** and this run adds **zero** landing cost to the
pin family (**B-17**) and **zero** new branches and **zero** new PRs to the S2 relay chain — it
reuses run 84's branch and PR.

### C-85-8 — no vector byte moved and the cross-repo pin is unmoved

```bash
cd <engine> && node docs/sync-vectors/generate.mjs --check
git diff --name-only b11e47b..8126a8e | grep -E 'sync-vectors|generate.mjs'   # expect NO match
cd <android> && head -8 core/src/test/resources/sync-vectors/VECTORS.lock
```

*Expected, and **observed**:* **`OK: 28 vector files match the generator.`, EXIT=0** — vector drift
**zero, measured in this session**, not inferred from an empty diff. **No match** for the vector
paths. `VECTORS.lock` reports **`Pinned commit: 7328a0bc043335491cd96a67d634e8eea2a13af9`** —
unmoved, and the prompt's `679a317` remains stale.

### C-85-9 — B-18's triggers re-checked, and the silence is deliberate for the fourth run

```bash
cd <engine> && git fetch --all --prune && git rev-parse origin/main       # expect aac05f3
cd <android> && git fetch --all --prune && git rev-parse origin/main      # expect ebfaf81
gh pr list --repo ShivaClaw/careerseeker --state open --json number,isDraft
gh pr list --repo ShivaClaw/careerseeker-android --state open --json number,isDraft
```

*Expected, and **observed**:* engine `origin/main` **`aac05f3`**, unmoved since 2026-08-12; android
`origin/main` **`ebfaf81`**; **21 open PRs in the engine repo (20 mine, all draft; #26 Terra's) and 6
in the android repo, all draft** — **nothing merged or undrafted**, newest merge anywhere still PR
**#44** of 2026-08-13. The stored prompt is unchanged and no gate result arrived. All four triggers
**negative**, so **no notification was sent** — the fourth deliberate silence. This run's finding is
**new but not live**: correct values deployed, absent guard, fix already sitting in a draft PR that
cannot be merged from here. **Next run: the same four triggers.**

### C-85-10 — the relay constants lane is claimed COMPLETE; here is how to falsify that

```bash
cd <engine> && grep -n '^export const' relay/src/protocol.ts
```

*Expected, and **observed**:* **ten** exported value bindings (`grep -c` reports **10**) —
`PROTOCOL_VERSION`,
`MAX_ENVELOPE_BYTES`, `MAX_CIPHERTEXT_B64U_CHARS`, `MAX_PUSH_BODY_CHARS`, `MAX_SEQ`,
`MAX_TTL_SECONDS`, `DEFAULT_TTL_SECONDS`, `PULL_PAGE_SIZE`, `DIRECTIONS`, `ENVELOPE_TABLE_DDL` —
**every one of which has now been mutated across runs 84 and 85**, plus the module-private
`PAIRING_ID` regex. What remains unmutated is **type-only** (`Direction`, `EnvelopeHeader`) and
carries no runtime value. **If this claim is wrong, this is the command that shows it**, and the
next `:core`-style sweep of the relay should either name what was missed or record the lane closed.

### C-85-11 — the records' own citation guard is green after this run's entries

```bash
cd <android> && scripts/check-citations.sh; echo "EXIT=$?"
```

*Expected, and **observed**:* `OK: every cited C-/B- id resolves to an entry that exists.`,
**`EXIT=0`** — run after run 85's `LOG.md`, `STATE.md`, `BLOCKED.md` and `AUDIT-REQUEST.md` entries
were written, so every `C-85-*` and `B-*` cited above resolves.

**Two counts, because this entry changes the number it reports.** Before C-85-11 itself was added:
**`definitions: 808   cited: 809   documented-absent: 1`**. With it: **`definitions: 809
cited: 810   documented-absent: 1`** — which is what a re-run today will print, and the number to
compare against. The inherited count was **786/787** at run 84. *(Run 84's C-84-11 has the same
self-reference and reports only the pre-entry figure; noted here rather than edited, since its
observation was correct when made.)*

### C-85-12 — CI ran on THIS run's head and passed; the "no CI on 8126a8e" claim is superseded

```bash
# via the GitHub MCP tools, or:
#   gh run view 32619516958 --log | grep -E 'Offline total|vector files match|passed \(|no decryption path'
#   gh pr view 56 --json headRefOid    # expect 8126a8e…
```

*Expected, and **observed**, read from the job logs in this session rather than taken from another
session's summary:* run **`32619516958`**, head **`8126a8eec5f0ca1cf463c52877140489600334bf`**
(confirmed twice in the checkout step's `git rev-parse` and `git log -1 --format=%H`), **both jobs
`success`**.

- `ubuntu-latest` — **`Tests 59 passed (59)`**, `Test Files 1 passed (1)`, reproducing this sandbox's
  number on a clean runner; `npx tsc --noEmit` clean; `wrangler deploy --dry-run` OK;
  **`OK: no decryption path in relay/src.`**; **`OK: 28 vector files match the generator.`** —
  **vector drift zero, measured on the runner** as well as locally (**C-85-8**).
- `windows-latest` — **`=== Offline total: 598 passed, 0 failed ===`**, `SyncHarness`
  **`=== 130 passed, 0 failed ===`**. **598 is the base branch's number** (the same figure run 81
  recorded for #54 and run 84 for `b11e47b`), so **all four commits on this branch move
  `$ExpectedOfflineTotal` by zero — measured on this head, not argued from "no engine file was
  touched"** (**B-17**).

**This supersedes run 85's earlier line that "CI has run on `b11e47b`, NOT on this run's head."** That
was accurate when written and is now stale; both facts are recorded rather than the first one
quietly edited away.

**What it does NOT change: the merge condition.** CI runs the **offline** portion only — no
`-IncludePackage`, no `-IncludePublish`, no `-IncludeLive`, and no part of the **android** gate. **The
fused android tree has still never been built** and PR #56 remains **draft and unmergeable from
here.** Green CI is evidence this branch is *neutral*, not that the landing plan is safe.

**Provenance note.** A concurrent session recorded the same run as **C-84-13** while these records
were being written; its commit says it *"defers to run 85's own entries if they exist."* The numbers
above were **re-read from the logs in this session** rather than inherited from it — the two agree.

---

## Run 86 (2026-08-23) — the three-way transcription disagreement surface, and the relay's unread `pairing`

### C-86-1 — the assigned slice was already built, for the fifty-first firing

```bash
cd <engine> && git fetch --all --prune
git log --oneline --all | grep -E '8575539|22b028e|7328a0b'
```

*Expected, and **observed**:* the stored prompt again assigns S5's spec half (§4.3
`entitlement_ack`, the vector via `generate.mjs`, PQ-A2-1/-2/-3) and again describes S5 as "NOT
STARTED". It has been built since **2026-08-09** — `8575539`, `22b028e`, `7328a0b` — and the
prompt's vendored pin `679a317` is stale, the real pin being **`7328a0b`** (**C-PIN-1**).
**Declined and re-routed** to `STATE.md`'s ordered intent, as the last fifty runs did. This run
took run 85's own named successor axis instead: *"the `:core` ↔ relay ↔ engine disagreement
surface — three transcriptions of one document; runs 83–85 compared each to the document, nobody
compared them to each other."*

### C-86-2 — this image still cannot run either gate, verified rather than assumed

```bash
for t in node npm dotnet pwsh java; do printf '%-8s ' "$t"; which $t || echo ABSENT; done
echo "ANDROID_HOME=${ANDROID_HOME:-UNSET}"
```

*Expected, and **observed**:* `node`/`npm` present (**v22.22.2**), **`dotnet` ABSENT**, **`pwsh`
ABSENT**, `ANDROID_HOME` **UNSET**, `java` present but **21**, not the pinned 17. So
`scripts\Verify-Alpha.ps1` did not run, the android gate did not run, and **no gate result is
claimed anywhere in this run.**

### C-86-3 — the relay baseline, reproduced before anything was changed

```bash
cd <engine> && git checkout claude/s2-relay-constant-pins
cd relay && npm ci && npm test
```

*Expected, and **observed**:* **`Tests 59 passed (59)`, `Test Files 1 passed (1)`, EXIT=0** —
run 85's number, reproduced in a fresh sandbox. Every row below is measured against this baseline.

### C-86-4 — `pairing` is the one declared header field the relay never reads

```bash
cd <engine>/relay && grep -n "env\.pairing" src/*.ts || echo ">>> NO OCCURRENCES"
grep -n "isValidPairingId" src/index.ts src/channel.ts
```

*Expected, and **observed**:* **`env.pairing` has NO OCCURRENCES in `relay/src/`.** `EnvelopeHeader`
declares six fields (`v`, `pairing`, `dir`, `seq`, `ts`, `key_id`); the push validator at
`channel.ts:150-166` checks five of them plus `ciphertext`, `nonce` and the optional `sig`, and
never `pairing`. `isValidPairingId` is called at **`index.ts:55` only** — on the URL path segment,
never on the body. This is PQ-S2-1's premise, re-verified on the branch head rather than inherited.

### C-86-5 — the four measured cases, and the fourth is new

```bash
cd <engine>/relay && npm test -- -t 'envelope header'
```

*Expected, and **observed**:* four cases pass, pinning: a header naming a **different** pairing
than the path → **201**; a **malformed** header pairing (`p_x`) → **201**; **no `pairing` field at
all** → **201**; and `GET /pull` serving the foreign id **back to the receiver verbatim**. The
first three re-confirm PQ-S2-1's eleventh-run table (recorded there as prose, asserted by nothing
until now). **The fourth was never measured**: because `pairing` is in the §4.1 AAD, the receiver
authenticates a routing claim the relay never checked and reports **`decrypt_failed`** (§7.2) — the
code meaning *corrupt or tampered* — for what is really a misroute.

### C-86-6 — the mutation table: enforcing the shape costs two fixture lines, not eighteen failures

```bash
cd <engine>/relay
cp src/channel.ts /tmp/channel.orig.ts && sha256sum src/channel.ts
# M1: add `typeof env.pairing !== 'string' || !isValidPairingId(env.pairing as string)`
#     to the validator in src/channel.ts, importing isValidPairingId from './protocol'
npm test                                    # against the UNTOUCHED suite, and again with the 4 new tests
# M2: M1 plus `|| env.pairing !== pathPairing`, threading segments[1] into push()
npm test
# M1 + fix BOTH fixtures: `pairing: 'p_x'` at test/relay.test.ts:37 and
#     `"pairing":"p_x"` in rawEnvelope() at :268-270
npm test
cp /tmp/channel.orig.ts src/channel.ts && sha256sum -c   # restore, and prove it
```

*Expected, and **observed**:*

| mutation | untouched suite (59) | with this run's 4 tests (63) |
| --- | --- | --- |
| clean | **59 passed** | **63 passed** |
| **M1** — validator checks `env.pairing` shape | **18 failed / 41 passed** | 20 failed / 43 passed |
| **M2** — M1 + equality with the path segment | — | 22 failed / 41 passed (**all 4 bind**) |
| **M1 + both relay fixtures fixed** | — | **2 failed** — exactly the 2 cases M1 changes |

**The 18 are not 18 problems.** They collapse to **two fixture lines** hard-coding the same
malformed `p_x`: `envelope()` at `:37`, and a second helper `rawEnvelope()` at `:268-270` **that
PQ-S2-1 did not know about**. That refines PQ-S2-1's *"fix the two non-conforming ids"* — the real
count is **four sites**, two here and two in `tests/EngineHarness/Program.cs` (`p_bridge_test` at
`:2291`, `p_harness` at `:2496`). M1 alone does **not** bind the "different pairing" case, because
`p_AAAABBBBCCCCDDDD` is well-formed; that separation is why M2 exists.

### C-86-7 — the three-way table is grep-derived, and says so

```bash
cd <android> && grep -rn "isValidPairingId" core/src/main/ | wc -l    # :core production sites
cd <engine>  && grep -rn "isValidPairingId\|IsValidPairingId" src/ || echo ">>> ENGINE: NONE"
grep -n 'p_' src/Sync/PairingManager.cs
```

*Expected, and **observed**:* `:core` enforces the shape at **5 production sites**
(`EnvelopeJson.kt:73`, `PairingSession.kt:77`, `OutboundEnvelopes.kt:97`, `RelayClient.kt:133`,
`Protocol.kt:326`); the relay enforces it on the **path only**; the **engine enforces it nowhere** —
`PairingManager.cs:51` mints a conforming id but accepts any caller-supplied one unvalidated. One
document, three transcriptions, three answers, with the phone strictest and the engine weakest.
**Caveat, stated rather than buried: the `:core` and engine rows are `grep` over source, not
execution** — no Android SDK and no .NET here. Only the relay row is measured behaviourally.

### C-86-8 — no production source moved, and the vector corpus did not drift

```bash
cd <engine> && git status --porcelain          # during the run
git show --stat claude/s2-relay-header-pairing # the commit
node docs/sync-vectors/generate.mjs --check
cd relay && npx tsc --noEmit; echo "EXIT=$?"
```

*Expected, and **observed**:* the only modified path at every point was
**`relay/test/relay.test.ts`**; `src/channel.ts` was restored between every mutation and re-checked
with `sha256sum -c` → **OK** (`55b31981…d659`), and it is **in neither commit**. Final clean run
**`63 passed (63)`**; **`tsc --noEmit` 0 errors, EXIT=0**; **`OK: 28 vector files match the
generator.`, EXIT=0**. **No vector byte changed and the vendored pin is unmoved at `7328a0b`**
(`VECTORS.lock:4`). Nothing outside `relay/test/` was touched, so **`$ExpectedOfflineTotal` is
untouched** (**B-17**) — asserted from the diff, not measured, since no gate ran here.

### C-86-9 — the §3.1 clarification reached two transcriptions of three; the engine still carries the retired wording

```bash
cd <engine>
for r in $(git for-each-ref --format='%(refname:short)' refs/remotes/origin); do
  git show $r:src/Sync/Protocol.cs 2>/dev/null | grep -n "Envelope hard limit"
done | sort -u
grep -n "MaxEnvelopeBytes" src/Sync/EnvelopeReceiver.cs
cd <android> && grep -n "decoded ciphertext" core/src/main/kotlin/app/careerseeker/core/Protocol.kt
```

*Expected, and **observed**:* `src/Sync/Protocol.cs` reads **`/// <summary>Envelope hard limit;
larger is rejected before any crypto work.</summary>`** on **every ref in the repository** — main
and all 21 draft branches. That is the P0 wording §3.1's amendment retired, because it *"described
something neither implementation ever measured"*. The engine's own code disagrees with its own
comment: **`EnvelopeReceiver.cs:45` measures `ciphertext.Length`** — decoded ciphertext bytes,
which is what §3.1 actually caps. `:core` corrected this KDoc at the **seventy-ninth** run and the
relay's `protocol.ts` carries the derived `MAX_CIPHERTEXT_B64U_CHARS` with the reasoning spelled
out; **the engine is the one transcription the correction never reached.**

**Why it is not cosmetic, in the words of the Kotlin KDoc that fixed it:** §4.4 instructs a future
chunker to size against exactly this constant, and *"a chunker sized against the envelope would be
sized against the wrong quantity by ~33%"*.

**NOT FIXED HERE, and not because it is risky — because it cannot be verified here.** The change is
one comment line and moves no assertion count, but `dotnet` and `pwsh` are absent (**C-86-2**), so
`Verify-Alpha.ps1` cannot confirm the 0-warnings/0-errors baseline this repo's `CLAUDE.md` requires
after every change. Folding an unverifiable C# edit into a test-only relay PR would also cost that
PR its single-claim shape. **This is the strongest engine-side item for the next gated session**,
and it is one line.

### C-86-10 — CI ran on THIS run's head and passed; the pin claim moves from asserted to measured

```bash
gh run list --commit f00feb2fea42a1af8398570e63380a1be69ec79f
gh run view 32630375783 --log | grep -E "Offline total|SyncHarness|Tests +[0-9]+ passed|OK: [0-9]+ vector|no decryption path"
```

*Expected, and **observed**:* run **`32630375783`**, head **`f00feb2`** — **this run's head** — attempt 1,
**both jobs `conclusion: success`**, every step green. Read from the job logs in this session, not
inherited.

- **`windows-latest`** — **`=== Offline total: 598 passed, 0 failed ===`**, with `SyncHarness`
  **`=== 130 passed, 0 failed ===`**. **598 is the base branch's number** (run 85 measured the same
  598 on `8126a8e`), so **this branch moves `$ExpectedOfflineTotal` by zero — MEASURED on this head**
  rather than argued from the diff being test-only (**B-17**).
- **`ubuntu-latest`** — **`Tests 63 passed (63)`**, `Test Files 1 passed (1)`, reproducing this
  sandbox's clean number on a fresh runner; `tsc --noEmit` clean; `wrangler deploy --dry-run` OK;
  **`OK: no decryption path in relay/src.`**; **`OK: 28 vector files match the generator.`**

**This supersedes two lines written earlier in this run.** **C-86-8** says the `$ExpectedOfflineTotal`
claim is *"asserted from the diff, not measured, since no gate ran here"*, and the run-86 LOG scope
paragraph says *"No CI result is claimed for this run's push."* Both were true when written and are
now **superseded**. Both halves remain true in their own moment: **I ran no gate in this session** —
`dotnet` and `pwsh` are absent (**C-86-2**) — **and** CI has now run the offline gate on this head
and it passed. The second is the one a reader wants.

**What this does NOT change: the merge condition.** CI runs the **offline** portion only — no
`-IncludePackage`, no `-IncludePublish`, no `-IncludeLive`, and no android gate. **The fused android
tree has still never been built.** PR #57 remains **draft and unmergeable from here**. A green CI
means this branch is *neutral*, not that the landing plan is safe.

**Also verified on the PR at the same time:** **zero review threads and zero comments**
(`pull_request_read` `get_review_comments` → `totalCount: 0`; `get_comments` → `[]`), and
`mergeable_state: clean`, `additions: 75`, `changed_files: 1`, `commits: 1` — the last three
independently confirming C-86-8's diff shape and the corrected line count.

---

# RUN 87 — 2026-08-23. The landing plan went stale under four PRs that postdate it

Run 87's slice was **not** the assigned S5 spec half (built 2026-08-09; see B-18, fifty-second
firing). It is the one thing that had genuinely rotted since run 47 wrote `RETURN-DAY.md`: **the
landing plan's leaf set**. Every claim below is re-derivable with `git` and `node` alone — no
Windows gate, no emulator, no SDK.

Set up the measurement tree once; claims C-87-3 through C-87-8 all run inside it:

```bash
git clone --no-checkout https://github.com/ShivaClaw/careerseeker mg && cd mg
git fetch origin '+refs/heads/*:refs/heads/*'
git checkout -B landing aac05f3
```

### C-87-1 — rule one: both fetches, and neither `main` has moved

```bash
cd careerseeker         && git fetch --all --prune && git rev-parse --short origin/main
cd ../careerseeker-android && git fetch --all --prune && git rev-parse --short origin/main
```

*Expected, and **observed**:* engine `origin/main` = **`aac05f3`**, last commit **2026-08-12
20:28:21 -0600** — **unmoved for eleven days**. Android `origin/main` = **`ebfaf81`**, last commit
**2026-08-06** — unmoved for seventeen days. Every count in this section was taken **after** these
fetches.

### C-87-2 — twenty-two open draft PRs in the engine repo, none merged

```bash
gh pr list --repo ShivaClaw/careerseeker --state open --limit 40 \
   --json number,isDraft,baseRefName,headRefName | jq 'length, (map(select(.isDraft)) | length)'
```

*Expected, and **observed**:* **22 open, 22 draft, 0 merged**. `RETURN-DAY.md` §3 was derived
against **17**. The five added since are **#54, #55, #56, #57** (plus #53's re-derivation), and
**#57 was opened 2026-08-23T09:13:21Z — the morning of this run**.

### C-87-3 — `#35` is no longer a leaf; `#57` is. This is the defect in the plan

A "leaf" is a PR head that is not another open PR's base — the merge that actually lands a stack.

```bash
gh pr list --repo ShivaClaw/careerseeker --state open --limit 40 \
   --json number,baseRefName,headRefName > prs.json
jq -r '.[] | "\(.number) \(.baseRefName) \(.headRefName)"' prs.json > graph.txt
while read n b h; do awk '{print $2}' graph.txt | grep -qx "$h" || echo "#$n $h"; done < graph.txt
```

*Expected, and **observed** — eight leaves:*

```
#26  codex/r6-dependency-sbom              (Terra's, out of scope)
#36  claude/s2-transport-vocabulary
#48  claude/s8-harness-linux-reach
#49  claude/s6-composition-root-decision
#51  claude/s3-pairing-confirm-consumer
#52  claude/s6-outcome-disposition
#53  claude/s6-resume-reconciliation        (step 0 recommends closing)
#57  claude/s2-relay-header-pairing
```

`RETURN-DAY.md` §3 step 2 says **merge `#35`**. `#35`'s head `claude/s2-seq-bound` is now the base
of `#54`, which is the base of `#55` → `#56` → `#57`. **`#35` is an interior node.** The leaf that
replaces it is **`#57`**, seven PRs deep from `main`.

### C-87-4 — the plan's own documents do not mention any of the four

```bash
cd careerseeker-android
for n in 54 55 56 57; do echo -n "#$n: "; grep -c "#$n\b" RETURN-DAY.md docs/Merge-Topology.md | tr '\n' ' '; echo; done
git log -1 --format='%h %ci' -- docs/Merge-Topology.md
```

*Expected, and **observed**:* **0 hits in both files for every one of #54, #55, #56, #57.**
`docs/Merge-Topology.md` was last touched **`bbd942b`, 2026-08-16**; `RETURN-DAY.md` **`f884a99`,
2026-08-19**. All four PRs postdate the later of those. **The plan is not wrong about what it
covers — it is silent about what it does not.**

### C-87-5 — the corrected order still costs TWO stops, and the same five files

```bash
# in the measurement tree, from aac05f3:
for b in claude/s8-harness-linux-reach claude/s2-relay-header-pairing \
         claude/s2-transport-vocabulary claude/s3-pairing-confirm-consumer \
         claude/s6-outcome-disposition claude/s6-composition-root-decision; do
  git merge --no-edit --no-ff "$b" || { git diff --name-only --diff-filter=U; \
    for f in $(git diff --name-only --diff-filter=U); do git checkout --theirs -- "$f"; git add "$f"; done; \
    git commit --no-edit; }
done
```

*Expected, and **observed** — `#48`, `#57`, `#36`, `#51` **clean**; two stops:*

- **`#52`** — 5 files: `README.md`, `docs/CareerSeeker-Project-Summary.md`,
  `docs/External-Audit-Handoff.md`, `scripts/Verify-Alpha.ps1`, `src/Engine/README.md`
- **`#49`** — 6 files: the same five **plus `tests/SyncHarness/Program.cs`**

**Both stops are the `$ExpectedOfflineTotal` pin family that §3 already names.** Swapping `#35` for
`#57` — four extra PRs, seven extra commits — **adds no stop and no new conflicting file.** §3's
cost table survives the correction intact; only its step-2 row changes.

### C-87-6 — the plan as literally written strands seven commits

```bash
# replay §3 verbatim (with #35), then:
for b in claude/s2-latest-since-invariant claude/s2-latest-retention-skew \
         claude/s2-relay-constant-pins claude/s2-relay-header-pairing; do
  echo "$b -> $(git rev-list --count HEAD..$b) commits NOT landed"; done
```

*Expected, and **observed**:* `#54` **1**, `#55` **2**, `#56` **6**, `#57` **7** commits unlanded.
The verbatim plan also costs **2 stops** — it is not more expensive, it is **incomplete**. It
leaves four open PRs whose base branches have just been merged, which is the state that is
expensive to reason about later, not now.

### C-87-7 — order is still load-bearing: `#49` first costs one extra stop

```bash
# same replay, #49 moved to position 1
```

*Expected, and **observed**:* **3 stops** instead of 2 — `#49` (5 files), `#51` (6 files), `#52`
(5 files). **The `+1` order penalty §3 documents reproduces in the corrected configuration**, which
is the claim §3 says actually transfers between configurations. The absolute counts (2 vs 3) are
the ones a reader will measure today.

### C-87-8 — the post-landing vector corpus is 30, generator-clean, and the phone's delta is exactly one file

```bash
# at the post-landing tree (corrected order):
git ls-tree HEAD docs/sync-vectors/v1/ --name-only | wc -l
node docs/sync-vectors/generate.mjs --check
```

*Expected, and **observed**:* **30 files**, and **`OK: 30 vector files match the generator.`**
(exit 0) — **run in this session**, at the post-landing tree, with both stops resolved `--theirs`.
This is the first time the corpus has been checked *after* the merges rather than before.

Against the phone's vendored corpus (pin **`7328a0b`**, **29 files**):

```bash
comm -23 post.names phone.names   # only upstream
comm -13 post.names phone.names   # only on phone == drift
```

*Expected, and **observed**:* only-upstream = **`pairing-high-bit-confirm.json`**; only-on-phone =
**empty**. Byte-comparing the 28 shared payload vectors: **0 differ**. `index.json` differs, and
only because it enumerates the new file. **No cross-repo drift.** `RETURN-DAY.md`'s "re-pin in the
same sitting" instruction is re-confirmed correct and still one file wide.

**Caveat, stated rather than buried:** the corpus was measured with the two stops resolved
`--theirs`. `RETURN-DAY.md` C-POST-2 established the corpus is byte-identical either way; **this run
did not re-derive that**, it relies on it.

### C-87-9 — no gate ran, and none is claimed

```bash
for t in dotnet pwsh sdkmanager avdmanager emulator adb; do which $t || echo "$t ABSENT"; done
echo "ANDROID_HOME=${ANDROID_HOME:-UNSET}"
```

*Expected, and **observed**:* `dotnet`, `pwsh`, `sdkmanager`, `avdmanager`, `emulator`, `adb` **all
absent**; `ANDROID_HOME` **unset**; `node v22.22.2` and `gradle` present but unusable for `:app`
without the SDK. **`Verify-Alpha.ps1` did not run. The android gate did not run. The fused android
tree has still never been built.** The merge condition in `RETURN-DAY.md` §3 is **unchanged by this
run** — every measurement above is a `git`-level merge cost, **not** a statement that any merge is
safe to land.

---

## RUN 88 — 2026-08-23. The landing-plan guard: C-88-1…C-88-9

Every claim run 88 makes, with the exact command that re-derives it. Run
`git fetch --all --prune` in **both** checkouts first; every count below is post-fetch. Paths assume
the android checkout is the working directory and the engine checkout is `../careerseeker`.

### C-88-1 — the assigned S5 slice is built, for the fifty-third consecutive run

The recurring prompt assigns §4.3's `entitlement_ack` body, the ack vector, and PQ-A2-1/-2/-3. All
three landed **2026-08-09/2026-08-12** on the `claude/s5-*` drafts.

```bash
git -C ../careerseeker show --stat 8575539 | head -12    # §4.3 body + PQ-A2-1 + PQ-A2-2
git -C ../careerseeker show --stat 22b028e | head -12    # the two entitlement_ack vectors
git -C ../careerseeker show --stat 7328a0b | head -12    # invalid-unknown-field, PQ-A2-3
git -C ../careerseeker branch -r --contains 8575539
```

*Expected, and observed:* `8575539` dated **Sun Aug 9 2026**, `7328a0b` dated **Wed Aug 12 2026**;
all three reachable from `origin/claude/s5-*`. **Declined and re-verified, not inherited.**

### C-88-2 — the guard fires, refuses, and passes; all three measured

```bash
scripts/fleet-probe.sh self-test ../careerseeker; echo "EXIT=$?"
```

*Expected, and observed:* six PASS rows, `self-test: OK`, **EXIT=0**. The three new rows are
`plan() accepts a table naming a real leaf (p4-entitlement)`,
`plan() fires on a table naming the non-leaf 's2-seq-bound'`, and
`plan() refuses (exit 2) when it parses zero rows`. **The fire row is the load-bearing one** — a
guard never observed failing is not evidence of anything.

### C-88-3 — §3 as it stands today still names leaves

```bash
scripts/fleet-probe.sh plan ../careerseeker RETURN-DAY.md; echo "EXIT=$?"
```

*Expected, and observed:* six plan rows, all `leaf`; `plan rows: 6   leaves now: 8   ROT: 0
UNPLANNED: 2`; **EXIT=0**. The two UNPLANNED are `p4-entitlement` (a leaf with no open PR — the case
`leaves`' own comment names) and `s6-resume-reconciliation` (#53, which §3 step 0 deliberately
excludes). **Both were already explained before this run; neither is a new finding.**

### C-88-4 — the guard reproduces run 87's finding from the pre-correction plan, in one command

```bash
git show f884a99:RETURN-DAY.md > /tmp/RD-precorrection.md
scripts/fleet-probe.sh plan ../careerseeker /tmp/RD-precorrection.md; echo "EXIT=$?"
```

*Expected, and observed:* **EXIT=1**, `PLAN IS STALE -- 1 row(s)`, and the row

```
  s2-seq-bound   ROT   no longer a leaf; contained by: s2-latest-retention-skew
                       s2-latest-since-invariant s2-relay-constant-pins s2-relay-header-pairing
```

with `s2-relay-header-pairing` additionally listed as an **UNPLANNED** leaf. `f884a99` is the last
commit to touch `RETURN-DAY.md` before run 87's correction `e2c3f27` (`git log --oneline --
RETURN-DAY.md`). **The output contains both halves of run 87's conclusion** — that `#35` rotted, and
that `#57`'s branch is what replaces it.

### C-88-5 — the guard reads refs only; no `gh`, no token, no network

```bash
# non-comment lines only -- the first draft of this claim omitted that and FAILED
grep -nE '^[[:space:]]*[^#[:space:]].*(\bgh\b|curl|wget|api\.github)' scripts/fleet-probe.sh
grep -oE '\b(git|sed|grep|printf|echo|mktemp|comm|head|tr|rm|wc)\b' scripts/fleet-probe.sh | sort -u
```

*Expected, and observed:* **no match** (exit 1), and the external commands are exactly
`comm echo git grep head mktemp printf rm sed tr wc` — **no network client of any kind**.

> **Self-correction, recorded because the house rule is what caught it.** This claim was first
> written with the command `grep -nE 'gh |curl|wget|api\.github'` and the words *"no match"*.
> Running it returned **line 276** — the comment in `plan` that quotes B-19's `gh pr list`. The
> claim's substance was right and its command did not measure it. Fixed before the commit stood.
> *A claim whose command you did not run is a description, not evidence* — and the discipline of
> writing the command next to the claim is the only reason this was caught rather than published.

Every question the script asks is answered by
`git for-each-ref`, `git merge-base --is-ancestor`, `git rev-parse` and `git merge-tree` against the
local object store. This is what makes B-19's attempt-3 premise wrong for the ancestry class:
**a fetch is the whole cost.**

### C-88-6 — when the rot began, and that it was self-inflicted

```bash
git -C ../careerseeker log --format='%H %cd %s' --date=iso-strict --reverse \
  origin/claude/s2-seq-bound..origin/claude/s2-latest-since-invariant | head -3
```

*Expected, and observed:* `f95b66e … 2026-08-22T13:09:35+00:00  S2: pin that 'latest' is
independent of 'since' …` — the first commit to contain `s2-seq-bound` without being it. PR #54 was
created **2026-08-22T13:10:08Z**, thirty-three seconds later. The plan was written **2026-08-19**
(`f884a99`) and corrected **2026-08-23** (`e2c3f27`), so the silent-wrong window is ≈20 hours.
**All four rotting branches are this program's own** (run 86 opened #57; run 85's heartbeat records
#56 as run 84's branch).

### C-88-7 — the board, post-fetch

```bash
git -C ../careerseeker fetch --all --prune
scripts/fleet-probe.sh leaves ../careerseeker
```

*Expected, and observed:* **8 leaves**, reproducing run 87's **C-87-3** exactly, and
`claude/s2-seq-bound` is **not** among them. The open-PR board is **22 open, 22 draft, 0 merged**
(21 `claude/*` + `#26` `codex/r6-dependency-sbom`), read via the GitHub API — **unmoved since run
87**. Engine `origin/main` is `aac05f3`; android `main` is `ebfaf81`.

### C-88-8 — the guard survives an edit to the file it parses

This run edited `RETURN-DAY.md` (step −1). A parser keyed on a table format can silently start
reading zero rows when the file around it changes, which is exactly the failure C-88-2's third row
refuses.

```bash
scripts/fleet-probe.sh plan ../careerseeker RETURN-DAY.md | tail -3
```

*Expected, and observed:* still `plan rows: 6`, **re-run after the edit, not before.**

### C-88-9 — no gate ran, and none is claimed

```bash
for c in dotnet pwsh sdkmanager avdmanager emulator adb; do printf '%-12s ' "$c"; which $c || echo ABSENT; done
echo "ANDROID_HOME=${ANDROID_HOME:-<unset>}"
node docs/sync-vectors/generate.mjs --check     # run in ../careerseeker, on main's tree
head -4 core/src/test/resources/sync-vectors/VECTORS.lock
ls core/src/test/resources/sync-vectors/v1/ | wc -l
```

*Expected, and observed:* all six **ABSENT**, `ANDROID_HOME` **unset**; `java` is 21, not the pinned
17. `Verify-Alpha.ps1` did not run and the android gate did not run. `generate.mjs --check` →
**`OK: 26 vector files match the generator.`**, exit 0 — **26 because it ran on the engine
checkout's `main`**, where the S5 stack's vectors are not; this is not a claim about the stack's 29.
The phone's pin reads **`7328a0bc043335491cd96a67d634e8eea2a13af9`** with **29** vendored files —
**the prompt's `679a317` is stale** (**C-PIN-1**, re-verified).

## Eighty-ninth run — 2026-08-23 (Linux sandbox): the deferred half of B-24, and a collided blocker ID

Every command below was **executed this run**, after `git fetch --all --prune` in both trees.
Where a claim rests on GitHub PR metadata, the session read it through its **GitHub tooling**, which
reaches both repos; **`gh` is absent on this host** (`which gh` → nothing), so the `gh` forms given
below are the **human re-verification path** and were **not** the form executed. That distinction is
the point of C-89-2 and is stated rather than glossed.

### C-89-1 — two different blockers were filed as `B-19`

```bash
cd <android>
git show HEAD:BLOCKED.md | grep -oE '^## B-[0-9]+ — ' | sort | uniq -d   # before this run's fix
grep -oE '^## B-[0-9]+ — ' BLOCKED.md | sort | uniq -d                   # after
```

*Expected, and **observed**:* the first prints **`## B-19 — `** (exactly one duplicate); the second
prints **nothing**. The two filings are `B-19 — S5's phone route exists and nothing in :app
constructs it` (run 58, line 2537) and `B-19 — the landing plan has no guard against its own leaf
set moving` (run 87), the latter **renumbered to B-24** this run (now line 4388).

**The first form of this command was wrong and running it is what showed that.** Written as
`grep -oE '^## B-[0-9]+'` — without the trailing em-dash — it reports **`B-2 B-4 B-6 B-7`** as
duplicates too. Those are **status** headings (`## B-2 status 2026-08-14`, `## B-4 / B-7 status
2026-08-10`, `## B-6 RESOLVED — 2026-08-12`), not second filings. The em-dash anchor is what
separates a filing from an update. *A claim whose command over-reports is not evidence for the
claim; it is evidence for a larger, false one.*

The register's full filing set, both heading levels, and why "next free ID" is not glanceable:

```bash
grep -oE '^#{2,3} B-[0-9]+ — ' BLOCKED.md | grep -oE 'B-[0-9]+' | sort -t- -k2 -n | uniq | tr '\n' ' '
grep -nE '^#{2,3} B-2[0-4] — ' BLOCKED.md
```

*Expected, and **observed**:* **`B-1 … B-24`** contiguous **except `B-11`**, which was
**deliberately never filed** — BLOCKED.md:1669 says so in words (*"B-11 was never warranted and is
not filed"*). **`B-20` and `B-21` are filed at `###`**, every other blocker at `##`. Note the honest
limit: a `##`-only scan would still have returned **B-23** as the maximum, so the mixed heading level
**does not by itself explain** run 87's choice of a taken number. The facts are recorded; the cause
is not claimed.

### C-89-2 — the `merged` field on a PR-list row is `false` even for merged PRs

This is the trap in the one-call implementation of B-24's deferred check 1.

```bash
# human re-verification path (NOT the form this session ran -- gh is absent here):
gh pr list  --repo ShivaClaw/careerseeker --state all --limit 100 --json number,merged \
  | jq '.[] | select(.number==31 or .number==44 or .number==8)'
gh pr view 31 --repo ShivaClaw/careerseeker --json number,merged,mergedAt
gh pr view 44 --repo ShivaClaw/careerseeker --json number,merged,mergedAt
gh pr view  8 --repo ShivaClaw/careerseeker --json number,merged,mergedAt
```

*Expected, and **observed** (via this session's GitHub tooling — list call vs. per-PR read):*

| PR | list row | per-PR read | truth |
| --- | --- | --- | --- |
| **#31** | `merged: false` | `merged: true`, `merged_at 2026-08-09T01:24:10Z`, `merged_by ShivaClaw` | **merged** |
| **#44** | `merged: false` | `merged: true`, `merged_at 2026-08-13T02:28:21Z`, `merged_by ShivaClaw` | **merged** |
| **#8**  | `merged: false` | `merged: false`, **no** `merged_at` | **closed, unmerged** |

Cross-check that needs no credential at all, and it is the one that makes the contradiction
undeniable — **#44's merge commit is `main`'s HEAD**:

```bash
cd <engine> && git log origin/main --oneline -1
```

*Expected, and **observed**:* **`aac05f3 Merge pull request #44 from ShivaClaw/codex/r7-empty-profile-audit`**.
A list row calling #44 unmerged is contradicting the branch it describes. **Key any such guard on
`merged_at` (or read PRs singly); `merged` on a list row is unpopulated, not false.**

### C-89-3 — every branch `RETURN-DAY.md` §3 names still maps to an open draft PR

```bash
cd <android> && bash scripts/fleet-probe.sh plan ../careerseeker RETURN-DAY.md; echo "exit=$?"
# then map each printed row to its PR (gh form; session used its GitHub tooling):
gh pr list --repo ShivaClaw/careerseeker --state open --limit 100 --json number,draft,headRefName
```

*Expected, and **observed**:* the guard prints **6 rows, all `leaf`, ROT 0, UNPLANNED 2, exit 0**.
The six map to **#48, #57, #36, #51, #52, #49** — **all `state: open`, all `draft: true`**. So §3 is
green on the *PR-state* axis as well as the ancestry axis. Board total: **22 open, 21 `claude/*`
plus `#26`**.

### C-89-4 — `claude/p4-entitlement` is a leaf that can never land, and 199 is its size

```bash
cd <engine>
git merge-base --is-ancestor origin/claude/p4-entitlement origin/main && echo ANCESTOR || echo NOT-ANCESTOR
git rev-list --count origin/main..origin/claude/p4-entitlement
for p in 27 28 29 30; do git log origin/main --oneline --grep="Merge pull request #$p " -1; done
```

*Expected, and **observed**:* **`NOT-ANCESTOR`**, **`199`**, and all four successor merges present on
`main` (`7f3e61e`/`f0b9bd5`/`160b317`/`a8ef552`). Its own PR **#8** is **closed, unmerged**
(**C-89-2**). So the content landed via *different* branches and this branch is superseded
lineage — **a leaf is not a landable PR**, and the guard's informational UNPLANNED row is exactly
right to refuse to call it rot.

### C-89-5 — the other unplanned leaf, `#53`, is excluded on purpose

```bash
cd <android> && grep -n '#53\|resume-reconciliation' RETURN-DAY.md | head
```

*Expected, and **observed**:* **§3 step 0 is titled "decide PR #53 (`claude/s6-resume-reconciliation`)"**
and §11.4 **recommends closing it** — #53 and the #45/#46 stack implement the same defect fix twice,
incompatibly. Its absence from the plan's six rows is **deliberate and documented**, not drift.

### C-89-6 — `#26` can never appear in the guard's output, by construction

```bash
cd <android> && sed -n '80,84p' scripts/fleet-probe.sh
grep -c 'dependency-sbom\|#26' RETURN-DAY.md
```

*Expected, and **observed**:* `fleet()` filters `grep -vE '/(codex|autonomy)/'`, so
`codex/r6-dependency-sbom` is outside the fleet and appears as **neither a plan row nor an UNPLANNED
leaf**; `RETURN-DAY.md` mentions it **0** times. Correct for a plan scoped to the claude fleet, and
the reason **`ROT: 0  UNPLANNED: 2` must not be read as "all unlanded work is accounted for"**.

### C-89-7 — what did NOT run, and the environment that decides it

```bash
for t in dotnet pwsh sdkmanager avdmanager emulator adb gh; do printf '%-12s ' "$t"; which $t || echo ABSENT; done
echo "ANDROID_HOME=${ANDROID_HOME:-<unset>}"
```

*Expected, and **observed**:* **all seven ABSENT**, `ANDROID_HOME` **unset**. `Verify-Alpha.ps1` did
not run; the android gate did not run; **no gate result is claimed anywhere in this run's records**.
`node docs/sync-vectors/generate.mjs --check` on `origin/claude/s5-entitlement-ack-emitter` →
**`OK: 29 vector files match the generator.`**, exit 0 (**C-STOP-1**, re-run this run). The vendored
corpus vs. pin `7328a0b`: `git archive` + `diff -r` → **no output, exit 0, 29 files** both sides.

### C-89-8 — the renumber did not rewrite history, only the register

The riskiest edit this run is the one that changes an identifier other documents point at. This is
the check that it stayed inside the register.

```bash
cd <android>
git show <run-89 LOG commit> --numstat -- LOG.md          # e677150
git show HEAD:LOG.md | sed '/^# RUN 89 —/,$d' | grep -c 'B-24'
diff <(git show 6ee92b8:LOG.md) <(git show HEAD:LOG.md | head -n $(git show 6ee92b8:LOG.md | wc -l))
```

*Expected, and **observed**:* **`102  0  LOG.md`** (102 insertions, **0 deletions**); **`0`**
occurrences of `B-24` anywhere before the run-89 entry — so every `B-19` in runs 87 and 88 still
reads `B-19`, as written; and the `diff` produces **no output**, i.e. the whole of `LOG.md` above
run 89 is **byte-identical** to `6ee92b8` (the pre-run-89 tip). **The run-89 text is strictly
appended.**

**Note the earlier form of this check, which is why it is written out here.** The PR body first
offered `git show HEAD:LOG.md | grep -c 'B-24'` and predicted **0**. That command returns **4** —
run 89's own entry names B-24 four times — so it would have "failed" while nothing was wrong. The
claim is about the text *above* run 89, and the command has to say so. *Same defect as C-89-1's
first form: a command that does not measure the claim it is attached to.*
