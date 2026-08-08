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
