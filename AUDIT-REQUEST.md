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
