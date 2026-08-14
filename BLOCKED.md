# BLOCKED

Things this session could not finish, why, and the **smallest** action that unblocks each.

Recorded per the operating protocol ("blocked → record symptom, attempts, smallest human
unblock → advance"). Nothing here is a guess about what *might* be wrong: each entry names a
check that was actually run.

---

## B-1 — Pairing UI cannot be finished: an open gate plus no device

**Milestone:** A4.

**Symptom.** The pairing *logic* is complete and vector-proven (`PairingSession` in `:core`,
7 tests). The pairing *screen* is not built, and building it now would mean pre-empting a
decision that is explicitly Brandon's.

**Why.** Two independent blockers:

1. **Gate `P2-KEYSTORE-FALLBACK` is open.** `docs/P2-Runbook.md` §4 lists three P2 gates as
   Brandon-only, and this one decides what happens when a device has no StrongBox: pair anyway
   with a *logged software-key downgrade*, or refuse. That choice determines what the pairing
   screen shows and what the app promises about key storage — a security-posture claim, not a
   layout detail. Guessing it would mean shipping a promise nobody approved.
   The other two (`P2-PIN-ROTATION`, `P2-REPLICA-CRYPTO`) also touch this surface.
2. **No device and no emulator.** Verified this session, not assumed:
   ```
   $ emulator -list-avds          → (empty)
   $ ls $ANDROID_HOME/system-images → none installed
   $ adb devices                  → List of devices attached   (none)
   ```
   The device signing key is an **Android Keystore** key (§5.4, hardware-backed, StrongBox
   where available). Keystore behaviour is precisely what Robolectric does not model, so a
   pairing screen written now could not be honestly tested here — only compiled.

**Deliberately not worked around.** `PairingSession` takes the device signing key as a
*public point plus a signing function*, so it never touches private key material and needs no
Keystore to be tested. That is why A4's logic could be completed and proven while the screen
could not.

**Smallest unblock:** Brandon answers `P2-KEYSTORE-FALLBACK` (one line: fall back with a
visible, logged downgrade, or refuse to pair). A handset — or `sdkmanager` installing a
system image and an AVD — is then needed to verify, but the gate answer is the part only he
can give.

**Status update 2026-08-08 (S0).** **The gate is answered.** Brandon, 2026-08-07:
`P2-KEYSTORE-FALLBACK` = *fall back, visibly* — no hardware-backed key means pair with a
software key, show a persistent "software-backed key" indicator, and record the downgrade in the
audit trail. Blocker (1) is therefore **cleared**.

Blocker (2) — no device — remains, but is no longer a hard stop: creating an AVD via
`sdkmanager` is now explicitly permitted (mission §3a) and is S3's first task, logged as a
machine change. B-1 moves from *blocked* to **scheduled at S3**. The gate answer was to be
recorded in `docs/P2-Runbook.md` §4; that file does not exist in this repo on any branch
(verified across `main`, `p2-replica`, `p5-store`, `a0-probe`), so it is recorded in
[`docs/S-Ladder.md`](docs/S-Ladder.md) §4 and carried to S3.

**Correction 2026-08-09 (S3 probe).** The sentence above — "creating an AVD via `sdkmanager` is now
explicitly permitted… B-1 moves from *blocked* to **scheduled at S3**" — was written before anyone
checked that `sdkmanager` is installed. **It is not on this machine at all.** See **B-4**. B-1's
device half is therefore still blocked, on a smaller and more specific thing than before: one
checkbox in Android Studio's SDK Tools.

---

## B-2 — Full live end-to-end could not be reached

**Milestone:** A5.

**Symptom.** "Pair phone/emulator ↔ engine through the relay" was not achieved. What *was*
achieved is recorded honestly in `LOG.md` A5 rather than described as more than it is.

**Attempts / findings:**

1. **Device or emulator** — none available (see B-1's probe output). Installing a system image
   is a multi-GB download plus an AVD create, and would still leave B-1's gate unanswered, so
   the emulator path buys a screen that cannot be honestly labelled anyway.
2. **The spec's own fallback** — "drive `:core`'s client against the engine from a JVM test
   harness" — is blocked *engine-side*, not phone-side. `HANDOFF.md` §4 records that the
   engine's `--sync` flag is honored but **no-ops with an explicit note**, because publishing
   requires a completed pairing, and the desktop `/pair` page that would create one is listed
   as still-to-build. `Program.cs::BuildSyncBridge` is a documented seam with no
   `RelayClient`-backed sink behind it yet. So there is currently no way for an engine on this
   machine to publish a real envelope to the relay for the phone to read.
3. **The reference repo is owned by a parallel session this week.** Building the engine is
   permitted (build/run only, no commits), but its working tree currently sits on
   `codex/beta-M0-preflight`, and creating worktrees or running builds in it risks colliding
   with work in flight. The cost/benefit did not justify it given (2) already blocks the
   outcome.

**What was done instead:** the production relay was probed as a *client* on the one route that
carries no pairing information — `GET /v1/health` — which proves TLS reachability and that this
client speaks to the real service. Result in `LOG.md` A5. No pairing was created, no envelope
pushed, nothing deployed or configured.

**Smallest unblock:** the engine-side desktop `/pair` page plus `BuildSyncBridge` wired to a
real `RelayClient` sink (engine work, and the parallel session's territory), **then** a handset.
Until the engine can publish, the phone has nothing to receive, and no amount of phone-side work
changes that.

### B-2 status 2026-08-09 (S2) — most of the way closed, and the rest is one screen

**Engine ↔ relay is now proven end to end on this machine**, which is what B-2 said had never been
reached. The relay ran **locally** under miniflare (no deploy); `SyncLiveSmoke` accepts the relay
URL as an argument, so the production-relay proof ran unchanged against `127.0.0.1:8787`:

```
=== 30 passed, 0 failed ===
```

pairing · snapshot + delta · signed p2e `doc_edit` and its rejection under the wrong device key ·
entitlement → outcome → `pull_request` in order · republished snapshot · duplicate seq refused
(409) · unpair.

The engine side of the gap is also built: `BuildSyncBridge` no longer returns `null`. A DPAPI
pairing vault (`src/Engine/SyncPairingVault.cs`) persists the pairing, both directional keys, the
device signing key, the relay token, the `key_id`, and **both** §6.1 sequence high-water marks; the
seam constructs a `RelayClient`-backed publisher resuming above the persisted mark. PR
[#31](https://github.com/ShivaClaw/careerseeker/pull/31).

**Still open, and it is exactly one thing: the desktop `/pair` page.** Until it exists the vault has
no product path to being populated, so `--sync` publishes nothing for a real user. A harness
creating a pairing is not a person pairing a phone, and this entry will not claim otherwise.

**B-2 status 2026-08-09 (sixth iteration) — unchanged, and deliberately so.** The relay's size cap
was fixed this iteration (PR #32, C-S2R-1…7) and that is **not** progress against B-2: the transport
was never what B-2 was about. Recorded here only so the next session does not read the S2 row's
"transport half hardened" and infer the blocker moved. **It did not.** The `/pair` page still does
not exist, still needs .NET, and is still the whole of the gap. **No new blocker arose this
iteration** — the relay slice was verifiable end to end on this machine and on CI, so filing one
would be inventing a phantom.

**Smallest unblock:** a `/pair` route on the local dashboard that (1) creates a `PairingManager`,
(2) renders the invite — `PairingInvite.ToQrJson()` is the exact payload, so a QR encoder is the
only genuinely new dependency — (3) polls `RelayClient.TakeCompletionAsync`, (4) shows the confirm
code for the human to compare against the phone, and (5) writes `SyncPairing` to the vault. Every
piece except the QR rendering and the route already exists and is vector-proven.

**Correction to this entry's original premise.** It cited the live Worker self-reporting
`phase: "p1"` as evidence the deployment predates P2/P4. That inference does not hold:
`phase: 'p1'` is **hard-coded at `relay/src/index.ts:47`**, so current source reports the same
string — the local instance did too. A redeploy may still be wanted, but this is not the evidence
for it; use the deployed script hash or add a build stamp.

---

**Status update 2026-08-08 (S0).** Still blocked, but the root cause is now measured rather than
inferred, and the engine side is now *this* agent's territory (mission §4) rather than a parallel
session's.

The measurement: the engine sync track does not exist in the main repo's `main` branch **at all**.
A path check on `origin/main` for `relay/`, `src/Sync/`, `docs/Sync-Protocol.md`,
`docs/sync-vectors/` and `tests/SyncHarness` returns **0 matches**; the same check on
`origin/claude/p4-entitlement` returns 45+. The publisher seam, the protocol spec and the shared
vectors all live on the unmerged PR stack (#5⊂#6⊂#7⊂#8, 85 commits behind `main`).

So B-2 cannot be closed by writing publisher code — the code it would extend is not on the
branch anyone would build. The path is **S1 (land the stack) → S2 (publisher + `/pair` + local
relay E2E)**. Note also that reaching E2E no longer requires a handset: an emulator is now
permitted (§3a), and the local relay runs under miniflare/vitest — no deploy.

---

## B-3 — CI's vendored-vector drift check is unverifiable locally

**Milestone:** A1/A2 (minor).

**Symptom.** The workflow step that re-fetches the pinned upstream vectors and diffs them uses
the GitHub contents API with `${{ github.token }}`. It cannot run on this machine.

**Mitigation, not a workaround:** the same comparison was performed locally against the local
clone of the reference repo, **blob-to-blob** (which is what CI's Linux checkout compares) —
all 26 vendored vectors are byte-identical to pin `679a317`. The command is in
`AUDIT-REQUEST.md` C-A2-1.

**Smallest unblock:** push the branch; CI runs the step. This is expected to pass, and is
recorded as *expected*, not as *verified*.

**Status update 2026-08-08 (S0) — locally VERIFIED, and the branch is pushed.**

Two things changed. First, this window provides a *dedicated independent clone* of the main repo
(`C:\Users\bkirk\Documents\careerseeker-sync`), so the comparison no longer has to lean on a
same-machine reference tree that a parallel session might be mutating. Second, the pin commit
`679a3175590dcd021b21c85af9daf12114e131fd` is present and readable in that clone
(`git cat-file -e` → exit 0).

The check was re-run blob-to-blob — comparing git object hashes, which is exactly the identity CI
asserts and is immune to line-ending or checkout differences:

```
vendored vectors compared = 26    mismatches = 0
```

All 26 vendored files under `core/src/test/resources/sync-vectors/v1/` are byte-identical to
`679a317:docs/sync-vectors/v1/`. The command is in `AUDIT-REQUEST.md` (C-S0-1).

One honest caveat that this check *surfaces* rather than resolves: pin `679a317` is reachable in
the main repo but is **not an ancestor of `origin/main`** — it lives on the unmerged sync stack.
Cross-repo vector identity is therefore currently pinned to an unmerged branch. That is not drift,
and nothing is wrong today, but S1 must confirm the pin's content survives the rebase unchanged;
if vector *content* moves, that is a drift event and a hard stop.

**Remaining:** CI's own run of the step, which this rung's push triggers. Downgraded from
*blocked* to **awaiting CI confirmation**.

### B-3 RESOLVED — 2026-08-08, CI confirmed

The branch was pushed and CI ran. Run
[`31278769047`](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31278769047),
6m01s, conclusion **success**. The step that mattered is named in the job list and did not skip:

```
- Assert :core has no Android dependency                            [success]
- Assert vendored sync vectors match the pinned main-repo commit    [success]   <-- B-3
- Unit tests (:core)                                                [success]
- Unit tests (:app, Robolectric)                                    [success]
- Assemble debug APK                                                [success]
- Lint                                                              [success]
- Assert no analytics or tracking SDKs ship                         [success]
```

Checking the step individually rather than trusting the workflow's overall green matters here: a
skipped step also lets a run go green, and "CI passed" would have been a weaker claim than the one
this blocker actually needed.

**B-3 is closed.** The check that could not run on this machine has now run on CI, with the
authoritative GitHub-API fetch, and it agrees with the local blob-to-blob result (26/26).

The caveat above stands and is *not* part of this blocker: the pin remains a non-ancestor of
`origin/main`, and S1 must confirm vector content survives the rebase byte-for-byte.

---

## B-4 — The emulator lane cannot be created: `sdkmanager` is not on this machine

**Milestone:** S3 (and therefore S4, S6).

**Symptom.** Mission §3a explicitly permits "`sdkmanager` system-image install + AVD creation" —
the allowance that was supposed to unblock B-1's device half. The tool it names does not exist here.

**Probed this session, not assumed:**

```powershell
$sdk="$env:LOCALAPPDATA\Android\Sdk"
Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"   -> False
Test-Path "$sdk\cmdline-tools\latest\bin\avdmanager.bat"   -> False
Test-Path "$sdk\emulator\emulator.exe"                     -> True
Get-ChildItem $sdk -Directory                              -> .temp build-tools emulator
                                                              licenses platform-tools platforms sources
Get-ChildItem $sdk -Recurse -Filter "sdkmanager*"          -> (nothing)
Get-ChildItem "C:\Program Files\Android\Android Studio" -Recurse -Filter "sdkmanager*"  -> (nothing)
$sdk\system-images                                         -> does not exist
emulator -list-avds                                        -> (empty)
```

So: the emulator **binary** is installed, but there is **no `cmdline-tools` directory at all**, no
system image, and no AVD. Disk is not the constraint — 765 GB free.

**Why this is a blocker rather than a task.** Creating the lane needs a prior step the mission did
not authorize: downloading and installing the *Android SDK Command-line Tools* package into
Brandon's SDK. §3a authorizes using `sdkmanager`; it does not authorize installing the toolchain
that provides it. That is a new SDK component on his machine, and the house rule is that machine
changes are named and logged, not assumed.

**Deliberately not worked around.** Without an AVD, S3's Keystore behaviour cannot be honestly
verified — Robolectric does not model the Android Keystore, and the mission forbids compile-only
claims for screens ("label exactly what ran where"). Writing the pairing screen now would produce
precisely the unverifiable artifact B-1 already refused to produce once.

**Cascade.** S4 (transport loop) needs S3's device key and an emulator to be an end-to-end claim;
S6 (outcome marking) needs both. All three are blocked on the same one-time setup.

**Its cost has shrunk three times, and the scope statement above is broader than the blocker.**
2026-08-09 found S4's and S6's *decision* layers behind this label needing neither key nor emulator
(`PullPolicy`, `OutcomeMarkPolicy`); 2026-08-10 (eighth run) moved S4's four transport ordering
rules out as `SyncPump`; 2026-08-10 (ninth run) moved **S3's own** attempt ordering out as
`PairingFlow` + `RelayTokenLadder`, 21 test cases, built with no Keystore and no camera.

**What B-4 still owns, in full, and it is not reduced by any of that:** the Android Keystore ECDSA
key and therefore gate P2-KEYSTORE-FALLBACK's StrongBox → TEE → software chain with its persistent
indicator and audit-trail entry; CameraX and the ML Kit QR decode; every screen; S6's device-signed
send; S4's and S2's end-to-end proofs; and **any claim that a key is hardware-backed**. Read the
milestone line above as "S3's device and screen halves", not "S3".

**Smallest unblock — one checkbox, ~2 minutes.** In Android Studio: *Settings → Languages &
Frameworks → Android SDK → SDK Tools →* tick **"Android SDK Command-line Tools (latest)"** → Apply.
That creates `cmdline-tools\latest\bin\sdkmanager.bat`, after which §3a applies exactly as written
and an agent can do the rest unattended:

```powershell
sdkmanager "system-images;android-36;google_apis;x86_64"
avdmanager create avd -n careerseeker-test -k "system-images;android-36;google_apis;x86_64"
emulator -avd careerseeker-test -no-window -no-audio
```

(Alternatively: authorize an agent to install the command-line tools package itself, and the whole
lane becomes unattended.)

---

## B-5 — Room 2.8.4 cannot open a file-backed database under Robolectric

**Milestone:** S8 (migration coverage). Closes off the gap `ReplicaDb` documents against itself.

**Symptom.** Every attempt to open a **file-backed** Room database in a Robolectric unit test fails:

```
java.lang.IllegalArgumentException: This driver is configured to open a database named
'replica-migration-test.db' but 'C:\...\robolectric-...\app.careerseeker.dashboard-dataDir\
databases\replica-migration-test.db' was requested.
    at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:48)
    at androidx.room.BaseRoomConnectionManager$DriverWrapper.openLocked(RoomConnectionManager.kt:68)
```

`SupportSQLiteDriver.open()` compares the requested path against the configured database *name* and
throws when Robolectric supplies its absolute temp path.

**Why it bites here specifically.** In-memory databases are unaffected — which is why the existing
16 replica tests pass and why this was never noticed. A migration test **cannot** use in-memory: it
has to create a v1 file, close it, and reopen it at v3. The file path is the whole point.

**Attempts (four, all the same failure):**

1. `MigrationTestHelper.runMigrationsAndValidate(...)` — the documented path.
2. Exporting the schemas to the **test** source set's assets — wrong asset path under Robolectric;
   fixed by moving them to `debug` (that part now works, and `helper.createDatabase` succeeds).
3. Opening via `Room.databaseBuilder(...).addMigrations(...)` and letting Room validate on open.
4. Forcing the legacy path with `.openHelperFactory(FrameworkSQLiteOpenHelperFactory())` — Room 2.8
   still wraps the support factory in a `SupportSQLiteDriver`, so the comparison still runs.

**What was kept.** The test is written and left in place under `@Ignore` carrying this diagnosis,
rather than deleted: the assertions are the valuable part and they are believed correct. It asserts
what the migrations must guarantee — `snapshotSeen` arriving as **0** (a 1 would claim a snapshot
this replica never received, which is the fabrication the column exists to prevent) and `outcome`
arriving **NULL** ("not recorded" is not "known"). `helper.createDatabase` genuinely builds the old
version from the committed schema export, so only the reopen is blocked.

Gate is unaffected and green: **102 tests, 0 failures, 0 errors, 3 skipped.**

**Smallest unblock:** move the class to `app/src/androidTest` and drop the `@Ignore` — as an
instrumented test it runs on a real Android runtime and never sees Robolectric's path handling. That
needs an emulator, so this is **downstream of B-4**. Failing that, the alternative is upstream: a
Room/Robolectric fix for `SupportSQLiteDriver` path handling. Downgrading Room to dodge it would
trade a test for a runtime dependency regression and is not recommended.

---

## B-6 — PQ-A2-3's `invalid-unknown-field` vector cannot be added yet: the engine has nowhere to reject it

**Milestone:** S5 (first half). This is the one part of the S5 spec/vector slice that did not land.

**Symptom.** Adding `invalid-unknown-field` to `docs/sync-vectors/v1/` — a §3 MUST with no vector
behind it — would make the shared suite **fail** on the engine side, because the engine would
*accept* the envelope. The vector would not enforce the rule; it would turn the offline gate red
while proving nothing.

**Why. Checked in the engine tree this session, not assumed:**

```
src/Sync/EnvelopeReceiver.cs:7   public sealed record ReceivedEnvelope(
                                     int V, string Pairing, string Dir, long Seq, string Ts,
                                     string KeyId, string Nonce, string Ciphertext, string? Sig);

$ grep -n "Deserialize\|JsonNode.Parse\|JsonSerializer" src/Sync/*.cs
src/Sync/PairingManager.cs:9     ToQrJson()          <- outbound
src/Sync/SyncPayloads.cs:19,43   serialise           <- outbound
src/Sync/SyncPublisher.cs:105    serialise           <- outbound
```

There is **no inbound envelope JSON parser in `src/Sync` at all.** `ReceivedEnvelope` is a record
that callers construct from already-parsed JSON, so there is no code path where an unknown
top-level field is even visible, let alone rejectable. `SyncHarness` proves the point: its
`ToReceived` (`tests/SyncHarness/Program.cs:200`) reads the nine fields it wants by name and drops
everything else silently, so a vector with a tenth field would decrypt and be **accepted**.

The phone is the stricter side here, and always was — `EnvelopeJson.parse` rejects unknown top-level
fields today (that is what PQ-A2-3 records). The gap is engine-side.

**Deliberately not worked around.** Three things were considered and rejected:

1. **Add the vector anyway.** It would fail CI on `windows-latest` for whoever pushes next, on a
   branch whose author could not run the gate. Shipping a known-red pin is worse than an open
   blocker.
2. **Add it with a non-`envelope` `type`** so no consumer picks it up. That is worse than not adding
   it: the suite would *look* like it covers the rule while enforcing nothing, which is precisely
   the failure PQ-A2-3 was raised about.
3. **Write the engine parser here.** No .NET on this machine (`which dotnet` → nothing), so it could
   not be compiled, let alone tested. A parser written against an unrun compiler is the drift
   `docs/protocol-questions.md` exists to prevent.

**Smallest unblock — engine work, on a machine with .NET, in this order:**

1. Add an inbound wire-JSON parser to `src/Sync` (the C# counterpart of the phone's
   `EnvelopeJson.parse`) that enumerates top-level properties and rejects any name outside the nine
   §3 defines, reporting `decrypt_failed` per the §3/§7.2 amendment that just landed in
   [careerseeker#32](https://github.com/ShivaClaw/careerseeker/pull/32).
2. Route `SyncHarness`'s invalid-vector loop through it instead of `ToReceived`, so the rule is
   exercised where the vectors are consumed.
3. *Then* add `invalid-unknown-field` via `generate.mjs` and regenerate.
4. Expect the offline assertion count to move, and run the full drift-trap sweep:
   `$ExpectedOfflineTotal` plus every count-reporting doc, in the same commit.

Steps 1–2 are the actual work; step 3 is two lines. Doing 3 first is the trap.

**Not blocking anything else.** S5's other three questions (PQ-A6-1, PQ-A2-1, PQ-A2-2) all closed
without it, and the `entitlement_ack` vectors landed. This is a standalone hardening item.

---

## B-7 — The cloud sandbox cannot resolve Google-hosted artifacts (egress policy)

**Milestone:** S5 second half, 2026-08-09.

**Symptom.** Even with a JDK and Gradle present, the android gate cannot run in the Linux cloud
sandbox, because AGP and every `androidx` artifact live on `dl.google.com`, which the session's
egress policy **denies**:

```
$ curl https://dl.google.com/dl/android/maven2/.../com.android.application.gradle.plugin-9.3.0.pom
curl: (56) CONNECT tunnel failed, response 403

$ curl -sS "$HTTPS_PROXY/__agentproxy/status"
"recentRelayFailures":[{"kind":"connect_rejected",
  "detail":"gateway answered 403 to CONNECT (policy denial or upstream failure)",
  "host":"dl.google.com:443"}]
```

`./gradlew :core:test` in the real repo fails during **plugin resolution**, before any compilation:
`Plugin [id: 'com.android.application', version: '9.3.0'] was not found`. The root
`build.gradle.kts` declares it `apply false`, but Gradle still resolves it.

`api.foojay.io` is denied by the same policy, so the `foojay-resolver-convention` in
`settings.gradle.kts` cannot provision the JDK 17 that `:core`'s `jvmToolchain(17)` pins. Only
JDK 21 exists here.

**Attempts.** Two, then stopped per the two-attempt rule. (1) `./gradlew :core:test` in the repo —
failed on AGP resolution as above. (2) A reduced scratchpad harness including only `:core`, whose
dependencies are all on Maven Central (reachable, `HTTP 200`) — this **works**, with the toolchain
overridden 17 → 21. That is how S5.B was verified, and it is labelled a probe everywhere it is
cited.

**Deliberately not worked around.** `/root/.ccr/README.md` is explicit: a 403 from the proxy is an
organization policy denial — "do not retry or route around it — report the blocked host". No mirror,
no vendored AGP, no `ANDROID_HOME` fabrication was attempted.

**Consequence.** This is **not** the same blocker as B-4. B-4 is "the owner's Windows machine has no
`sdkmanager`". B-7 is "the cloud sandbox cannot fetch the Android toolchain at all". Ticking B-4's
checkbox does nothing for cloud iterations, and vice versa. What a cloud iteration **can** do is now
known and worth stating positively: `:core` (pure Kotlin/JVM, Maven Central only), `relay/`
(Node + vitest + miniflare), `docs/sync-vectors/generate.mjs`, and every doc.

**Smallest human unblock:** none needed for the program — **CI already is the unblock**. Pushing to
`claude/**` runs the real gate on `ubuntu-latest` with JDK 17 and a real SDK, which is why this
slice's authoritative evidence is the CI run on its push rather than the local probe. Only if
someone wants the full gate to run *inside* a cloud session would `dl.google.com` need adding to the
session's egress allowlist.

---

### B-4 / B-7 status 2026-08-09 (fifth iteration, S6) — the send path is the blocked half, and it is now the *only* blocked half

**No new blocker.** Recorded here so the next session does not re-derive it, and because S6's row
in `STATE.md` changes label this iteration without anything about B-4 or B-7 changing.

**Symptom.** S6 (outcome marking) cannot be proven end to end. `OutcomeMarkPolicy` decides what may
be marked and what is displayed while a mark is unconfirmed, and it is tested here — but nothing
sends. `outcome` is state-changing, so §5.4 requires the envelope to carry `sig`, ECDSA-P256 from a
non-exportable **Android Keystore** key. That key is S3's deliverable, S3 needs an AVD, and the AVD
needs `sdkmanager` (**B-4**). The `:app` wiring that would call the policy additionally cannot even
be compiled in a cloud session (**B-7**: `dl.google.com` is an egress policy denial, so AGP and
every `androidx` artifact are unfetchable).

**Attempts this iteration.** None against the blocker itself — deliberately. The mission does not
authorize installing the SDK toolchain, and the proxy's README says to report an egress denial
rather than work around it. What was done instead was to establish, by reading both sides, that the
*decision* layer needs neither: `mark`, `display`, `offerFor` and the reconciliation rule touch no
key and no Android type, which is why 22 tests for them run here at all.

**Smallest human unblock.** Unchanged, and it is still one checkbox: Android Studio → SDK Tools →
*Android SDK Command-line Tools (latest)*. That gives `sdkmanager`/`avdmanager` → an AVD → S3's
Keystore key → S6's send path, in that order. Nothing about S6 needs anything else.

**What is deliberately not claimed.** That the policy is correct against a real engine. It is
correct against the protocol as read and against the engine's source as read; with no `outcome_ack`
in v1 (**PQ-S6-1**) there is no round trip that could confirm it, and there will not be until either
that PQ closes as option (a) or a live pairing exists to observe convergence against.

---

### B-6 status 2026-08-09 (fifth iteration) — unchanged, and it was re-read rather than assumed

The iteration prompt asked for PQ-A2-3's `invalid-unknown-field` vector. **It still cannot be
added**, for the reason already recorded: `EnvelopeReceiver.Receive` takes an *already-parsed*
`ReceivedEnvelope` and the harness's `ToReceived` cherry-picks named keys, so an unknown top-level
field is discarded before any rejection check runs. The engine would **accept** the envelope, and a
vector asserting rejection would turn the offline gate red.

Parser first, vector second. The parser is C# and no cloud session has .NET, so this stays queued
for a local session — and a session that is handed "add the unknown-field vector" as a task should
read this entry before starting, not after.

---

## No new blocker arose 2026-08-10 (S4 spec half, seventh cloud iteration)

Recorded as an entry because its absence is otherwise indistinguishable from an omission, and
because this iteration made a decision that *looks* like a blocker and is not.

**The S4 spec slice completed end to end on this machine.** PQ-S4-1 is closed (§4.3.4 in the main
repo, PR #33 draft), and nothing in it was left unfinished — option (a) required no code on either
side, which is why a sandbox with no .NET and no Android SDK could finish the whole of it.

**The one thing deliberately not done is not blocked: no `pull_request` vector was added.** That was
a judgement, not an obstacle — a static vector cannot test any of §4.3.4's three behavioural MUSTs
(answer a snapshot, ignore the value, do not reject a non-zero), and an `envelope`-typed addition
would enter `SyncHarness`'s enumeration and move `$ExpectedOfflineTotal`, which no .NET-less machine
can measure. Zero value, non-zero risk. Reasoning in `LOG.md` §S4S-3. **Do not file this as a
blocker and do not "finish" it** — there is nothing here for a local session to pick up.

**Not to be confused with B-6**, which is a genuinely blocked vector and a different one:
PQ-A2-3's `invalid-unknown-field` is blocked because the engine has no inbound wire-JSON parser to
reject the field in, so the vector would turn the gate red. B-6 needs a C# parser written first.
This entry needs nothing.

**B-6 and B-7 are unchanged and were not re-tested this iteration** — nothing in the slice touched
either, and re-asserting an untested blocker as current would be the same overclaiming these records
correct elsewhere. **A new engine-side defect was found** (`pull_request` reports
`SnapshotRepublished` with no republisher configured, the same shape as PQ-S6-1's `outcome` case) —
it is recorded as an extension to PQ-S6-1 and as C-S4S-5, **not** here, because it blocks nothing:
it is unwritten C#, not obstructed C#.

---

### B-7 status 2026-08-10 (eighth iteration, S4 transport half) — re-measured, unchanged, and its cost is now smaller

**No new blocker.** Recorded because B-7 was re-measured this session rather than carried forward,
and because the slice deliberately reduced what B-7 actually costs.

**Re-measured, this session:**

```
https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.3.0/gradle-9.3.0.pom
  -> curl: (56) CONNECT tunnel failed, response 403
https://api.foojay.io/disco/v3.0/packages
  -> curl: (56) CONNECT tunnel failed, response 403
https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.10/kotlin-stdlib-2.4.10.pom
  -> 200
dotnet, adb, sdkmanager, pwsh  -> absent      java 21, node 22.22.2  -> present
```

Unchanged from 2026-08-09. Probed once, as a client, and then respected — no mirror, no vendored
AGP, no `ANDROID_HOME` fabrication, per `/root/.ccr/README.md`.

**What changed is the size of the blocked surface, not the blocker.** S4's remainder used to be
described as "`:app` wiring", which put four ordering decisions — the transport cursor, when the
replica position is read, latch release on a failed push, and which `seq` is authenticated — inside
a module no cloud session can compile. Those now live in `:core` as `SyncPump`, tested here
(C-S4T-1…7). What B-7 still blocks in S4 is genuinely mechanical: constructing a `SyncPump` with a
Ktor engine, an `EnvelopeApplier` → `ReplicaApplier` adapter, and a Room-backed
`ReplicaPositionSource`.

**Still true and worth not losing:** `SyncPump` has **no production caller**. `grep -rn SyncPump
app/src` prints nothing, and will keep printing nothing until a machine that can compile `:app`
writes the adapter. That is unblocked-and-unwritten in a cloud session's hands only in the sense
that a local session can do it today; it is not something to mark done from here.

**Smallest human unblock: unchanged — none needed.** CI is the unblock, and it runs the real gate
on the push.

---

## B-4 / B-7 status 2026-08-10 (S6 send path, tenth iteration) — the cost shrank again, and one stated reason was wrong

**Neither blocker is closed, and neither is weakened.** What changed is the size of what sits behind
them, and one sentence in `STATE.md` that described S6's remainder incorrectly.

**The sentence that was wrong.** `STATE.md` recorded S6's remaining half as *"genuinely blocked: it
needs a device-signed envelope (§5.4), which needs S3's Android Keystore key, which needs an AVD that
does not exist (B-4)"*, and singled it out as the one remainder a toolchain could not unblock. **The
repo already contradicted it:** `OutboundEnvelopeFactory` takes the signer as an injected
`fun interface DeviceSigner`, and `OutboundEnvelopesTest` has been building and asserting *signed*
envelopes in this sandbox since A6. "Needs a device signature" and "needs a device" are not the same
statement. The send *decisions* are now `OutboundQueue`, 20 tests, run here.

**What is still B-4's, in full, and is not weakened by any of the above:**

- The Android Keystore key itself, and therefore gate `P2-KEYSTORE-FALLBACK`'s StrongBox → TEE →
  software chain with its persistent indicator and audit-trail entry.
- **Any claim that a signature came from a hardware-backed key.** The signer in this slice is a stub
  returning fixed bytes; the tests assert only *whether a signature could be produced at all*.
- CameraX / ML Kit, every screen, and the E2E proof.

**What is still B-7's:** the `:app` wiring for S6 — the detail-screen controls, the transport loop
that would drive `OutboundQueue`, and the **persisted p2e counter** that `reconciled()` assumes some
caller owns. `grep -rn "OutboundQueue" app/src` prints nothing.

**Smallest unblock, unchanged:** for B-4, one checkbox — Android Studio → SDK Tools → *Android SDK
Command-line Tools (latest)* — then a system image and an AVD. For B-7, a machine that is not this
sandbox; `dl.google.com` and `api.foojay.io` are egress **policy denials** here, which is firmer than
"not installed". CI is the gate, not a checkbox.

---

## B-8 — The persisted p2e counter has no owner, and `OutboundQueue.reconciled()` assumes one

**Milestone:** S6 send path (2026-08-10, tenth iteration).

**Symptom.** §6.1 requires the sender to persist its sequence counter across restarts.
`OutboundEnvelopeFactory` takes a `SeqSource` and documents that requirement; **nothing in the repo
implements one that survives a process restart.** Every construction of it in `:core` and in every
test is an in-memory counter. `OutboundQueue.reconciled()` is written to be called after the caller
has lifted that persisted counter above the relay's reported `latest` — a caller that does not exist
yet.

> **Sharpened 2026-08-11 (fifteenth iteration), and the earlier wording undersold it.** The line
> above says every `SeqSource` is "an in-memory counter", which implies production code holding a
> counter in memory. Measured: `grep -rn "SeqSource" core/src app/src --include=*.kt` returns the
> `fun interface` declaration, one KDoc reference, one constructor parameter — and **exactly one
> implementation, a test double at `OutboundQueueTest.kt:30`**. There is no production counter to
> persist, in memory or otherwise, and **zero `:app` references**. That is a smaller hole to fill
> than "replace the in-memory one" suggests, and a larger one than "add persistence" suggests: the
> owner does not exist yet. The spec half is now closed — §6.1 states the resume rule for **both**
> senders as of 2026-08-11 (PQ-S6-2), so the rule this must satisfy is written down, and §6.1's
> conformance note names this gap by ID.

**Why it is not fixed here.** The counter belongs in Room, which is `:app`, which needs the Android
SDK this sandbox cannot fetch (**B-7**). Writing it as a `:core` interface with no implementation
would add a type without adding a guarantee, and the guarantee is the whole point: an in-memory
counter that resets to 1 on restart produces envelopes the relay refuses at the door, forever.

**What was done instead.** The failure mode is now *detected and reported* rather than silent:
a refused push halts the queue on `SendHalt.COUNTER_BEHIND` and surfaces the relay's `latest`, which
is exactly the number the persisted counter must be lifted above (`AUDIT-REQUEST.md` C-S6S-2,
C-S6S-5). A phone with a resetting counter now stalls visibly instead of dropping marks quietly.

**Smallest unblock:** a machine with an Android SDK. Then: a Room-backed `SeqSource` that persists
**before** the envelope is handed to the transport (persisting after a successful push reintroduces
the same lag on a lost response), and a startup reconciliation against
`GET /pull?dir=p2e&since=0`'s `latest`, per §6.1's own recipe — which, **as of 2026-08-11, is
stated for this sender too** rather than for the engine only (**PQ-S6-2**, closed). §2.2 also now
pins a second, cheaper source of the same number: the 409 body's `latest`, which
`RelayClient.conflictLatest` already reads and `OutboundQueue` already surfaces. So the persisted
counter is the only missing piece, not the reconciliation logic around it.

---

## No new blocker arose 2026-08-10 (S2 relay conformance, eleventh cloud iteration)

**Recorded because the absence is the point.** This slice produced **two new findings and zero new
blockers**, and both findings are the kind that read like blockers if skimmed. Writing them up as
`BLOCKED` would send the next session hunting for an obstruction that does not exist.

**PQ-S2-1** (the relay never validates the `pairing` field it declares) and **PQ-S2-2** (one
out-of-range `seq` wedges a direction permanently) are **not blocked. They are unblocked and
deliberately deferred**, which is a different status with a different next action. Nothing is
missing from any machine: both are a few lines in `relay/src/channel.ts`. What is missing is the
*evidence to change them safely*, and that is a property of where the change lands, not of this
sandbox:

- Both tighten what the relay **refuses**, which is the exact shape of the 2026-08-09 size-cap bug
  — a relay refusing what `docs/Sync-Protocol.md` declares legal, discoverable by a conforming
  sender only as a 413 on a correctly-sized chunk. §3.1's own amendment now says the relay MUST
  carry every envelope the section declares legal.
- PQ-S2-2 needs a **spec amendment first**: §3 states no maximum for `seq`, so capping it relay-side
  would refuse conforming envelopes by definition. Spec, then relay — the reverse is the bug again.
- PQ-S2-1 has direct evidence against a blind fix, found while checking whether one was safe:
  `tests/EngineHarness/Program.cs:2268` constructs a publisher with `"p_bridge_test"` (11 chars
  after `p_`, not 16) and `relay/test/relay.test.ts`'s own envelope helper has sent `"p_x"` into
  every channel for the life of the suite. Neither reaches a relay today; both prove the shape rule
  is not universally respected in this codebase, which is what to measure *before* the relay starts
  refusing on it.

**Smallest unblock: none — there is nothing to unblock.** The next action for both is a session on a
machine with .NET, which can run `Verify-Alpha.ps1` and the engine↔local-relay smoke and therefore
observe an over-tightening instead of arguing about one. Re-verification commands are
`AUDIT-REQUEST.md` **C-S2R-8** and **C-S2R-9**; the full write-ups with the closing decision are
`docs/protocol-questions.md` **PQ-S2-1** and **PQ-S2-2**.

**One thing this iteration chose not to run, which is an embargo and not a blocker.** CI's relay job
runs `npx wrangler deploy --dry-run` as a config validation. It does not deploy, but declining every
`wrangler deploy` variant from an unattended sandbox is the conservative reading of the standing
"no deploys of any kind" embargo, so it was skipped. `relay/wrangler.jsonc` was not touched by this
diff, so the step is unaffected on its face — but that is an argument, and CI is the measurement
(**C-S2R-15**).

**B-7 was not re-measured this iteration** and is carried forward unchanged: this slice needed
neither Gradle nor the Android SDK, so it produced no new evidence about `dl.google.com`. The last
measurement stands (2026-08-10, tenth run).

---

## No new blocker arose 2026-08-11 (S4 pull-page semantics, thirteenth cloud iteration)

Recorded explicitly, because "nothing blocked" is a finding too and the alternative is a future
session hunting for a phantom.

**The slice completed both halves it set out to do.** §2.1 landed in the main repo and the wrapper
removal landed in `:core`, and neither needed a machine this sandbox is not. The spec half is
doc-only; the phone half is `:core` Kotlin, which the reduced probe runs (C-S4S-5).

**One candidate blocker was investigated and dismissed rather than filed.** The first draft of §2.1
required a receiver to report an unreadable body "as an unavailability", which
`src/Sync/RelayClient.cs`'s `PullAsync` does not do — it lets the parse throw. That looked like a
new engine-side conformance gap needing .NET to close, i.e. a B-9. **It was not a gap in the engine;
it was an over-reach in the clause I had just written**, and the fix was to write the clause
correctly (MUST for the safety property both receivers hold, SHOULD for the error type they
reasonably differ on). Filing it would have manufactured a blocker out of my own draft.

**What remains unverified here, and it is the standing pair, not something new:**

- The **android gate** did not run and cannot — no SDK, no JBR, `dl.google.com` egress-denied
  (**B-7**). CI is the gate. Verify with C-S4S-7.
- **`Verify-Alpha.ps1`** did not run and cannot — no .NET (**not** a new blocker; it is also
  unaffected, since the main-repo half of this slice wrote one doc file and no `.cs`, no harness,
  no vector and no count-reporting doc, so `$ExpectedOfflineTotal` (598) is untouched by
  construction).

**PQ-S4-3 is a finding, not a blocker.** Nothing prevents it being fixed — it needs a spec decision
about how far an unparseable element may move a cursor, and both the decision and the `:core` half
are reachable from a sandbox. It is queued as a slice, not filed here, and calling it BLOCKED would
be exactly the mislabel this file's history is full of corrections for.

---

## No new blocker arose 2026-08-11 (S4 cursor bound, fourteenth cloud iteration)

Recorded explicitly, because "nothing blocked" is a finding and its absence is indistinguishable
from an omission.

**The slice completed both halves it set out to do.** §6.4 landed in the main repo and the bound
landed in `:core`, and neither needed a machine this sandbox is not. The spec half is doc-only; the
phone half is `:core` Kotlin, which the reduced probe runs (C-S4C-4).

**The engine half is NOT a blocker and must not be filed as one.** `src/Sync/RelayClient.cs` reads
pull pages with the same structure and needs the same `latest` ceiling. It did not get it because
there is no .NET in this sandbox — which makes it **unwritten**, exactly like B-6's parser is
unwritten, but *unlike* B-6 nothing prevents a local session doing it today. There is no missing
tool, no unanswered gate, and no upstream defect. A session handed "port §6.4 to the engine" should
find no obstruction, and filing this here would send it hunting for one.

**One thing that looks like a blocker and is not: the two pre-existing cursor assertions did not
change.** `a wrapped envelope is never applied…` still asserts `cursor == 999` and `an envelope that
does not parse…` still asserts `cursor == 6`, after a commit whose entire purpose was to bound that
advance. That is correct, not a missed case: on both pages the claimed `seq` equals `latest`, so the
ceiling does not bind. It is the demonstration that §6.4 is a ceiling rather than a behaviour change
(C-S4C-5). Do not "fix" those numbers.

**What remains unverified here, and it is the standing pair, not something new:**

- The **android gate** did not run and cannot — no SDK, no JBR, `dl.google.com` egress-denied
  (**B-7**, re-measured this session: `CONNECT tunnel failed, response 403`). CI is the gate.
- **`Verify-Alpha.ps1`** did not run and cannot — no .NET. It is also unaffected: the main-repo half
  is one Markdown file, so `$ExpectedOfflineTotal` (598) is untouched **by construction**, and
  `grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1` → `0`, run before the edit, so the drift trap is
  not armed against this file at all.

**B-4, B-5, B-6, B-7, B-8 are unchanged and were not re-tested** beyond B-7's egress probe above.
Nothing in this slice touched any of them, and re-asserting an untested blocker as current would be
the overclaiming these records correct elsewhere.

**PQ-S4-3 is now CLOSED** (`docs/protocol-questions.md`), including a correction against its own
original framing — it claimed the `latest` bound capped the attack outright, and it does not: it
confines the damage to envelopes the relay already holds, which it could withhold regardless, and
removes only the forward-going part. That is a smaller claim than the one it opened with, and the
spec states the smaller one.

---

## No new blocker arose 2026-08-11 (S2 `seq` bound, sixteenth cloud iteration)

The slice ran end to end in this sandbox: §3.2 written, `relay/src/channel.ts` enforcing it, nine
tests added, suite **42 → 51**, CI green on both jobs (run `31494720248`). Nothing was attempted
twice and abandoned, so nothing here is a blocker.

**PQ-S2-2's remaining half is deliberately NOT filed as one, and the distinction is the point of
this entry.** The bound closes the *out-of-range* wedge. A channel wedged **in** range — a sender
that emits `9007199254740991` legitimately-shaped — still refuses every later envelope in that
direction until the row expires or the pairing is deleted, and the relay exposes no reset short of
`DELETE /v1/{pairing}`. **Nothing blocks fixing that.** It is a **product decision** Brandon has not
made: a channel-level reset is a new authenticated destructive route, and its shape (who may call
it, whether it purges one direction or both, whether it is distinguishable from unpair) is a
question about what the product promises, not about what this machine can run. Filing it as
`BLOCKED` would send the next session hunting for a toolchain that would not help it.

It is recorded as the open half of **PQ-S2-2** in `docs/protocol-questions.md` and belongs on the
return-day decision list, not here.

**Two blockers were re-read rather than assumed, and both stood.**

- **B-6** (PQ-A2-3's `invalid-unknown-field` vector) is why **no vector expresses §3.2 either.** A
  `seq` **range** rule is a rejection rule, and a rejection vector asserts a rejection the engine has
  nowhere to perform — `EnvelopeReceiver.cs:33` takes an already-parsed record and
  `SyncHarness/Program.cs:696` cherry-picks keys. Parser first, vector second, and the parser is C#.
  **Unchanged.**
- **B-2** is still exactly the missing desktop `/pair` page. This slice hardened S2's transport for
  the third time and moved B-2 not at all. **Unchanged.**

**B-4, B-5, B-7, B-8 were not re-tested this iteration** — nothing in the slice touched an Android
SDK, an emulator, Room, or a persisted counter — and are carried forward unchanged.

---

## No new blocker arose 2026-08-11 (S2 transport vocabulary, seventeenth cloud iteration)

Recorded as an entry because its absence is otherwise indistinguishable from an omission, and
because this iteration produced **two** things that look like blockers and are not.

**The slice completed end to end on this machine.** PQ-S2-3 is closed by §2.3 in the main repo
(draft PR #36), the relay suite went 36 → 47 with ten of eleven new tests proven against a mutated
relay, `generate.mjs --check` passed, and CI was green on the branch tip (run `31516194482`, both
jobs, offline total 598 unchanged). Nothing was attempted and abandoned.

**Not a blocker (1): PQ-S2-4.** The measured 401-vs-404 gap — a purged pairing answers
`unauthorized`, so the phone's terminal `SendHalt.PAIRING_GONE` is never entered and appears
unreachable — is **a decision that has not been made**, not an obstacle. Brandon has to weigh "the
phone can tell it was remotely unpaired" against "a wrong credential cannot learn a pairing id was
ever real". Either answer is implementable; nothing prevents the work. Filing it here would send the
next session hunting for a phantom obstacle, which is the failure mode `BLOCKED.md` is supposed to
prevent. It is in `docs/protocol-questions.md` as **PQ-S2-4** and in `HUMAN-QUEUE` terms it is a
one-question decision.

**Not a blocker (2): the S5 slice this iteration was assigned.** The prompt asked for S5 on the
basis that it was "NOT STARTED". Derived after the mandatory fetch, S5's spec half has been **done
since 2026-08-09** (draft PR #32, four commits, §4.3.3 + two `entitlement_ack` vectors, PQ-A6-1 /
PQ-A2-1 / PQ-A2-2 closed). Its remaining half is the **engine and phone appliers**, which are C# and
Kotlin — uncompilable here — and that is **B-6/B-7 territory already recorded**, not a new blocker.
A stale iteration summary is not an obstacle either; it is a reason to derive before acting.

### B-6 status 2026-08-11 (seventeenth iteration) — unchanged, re-read rather than assumed

PQ-A2-3's `invalid-unknown-field` vector still cannot be added. `src/Sync` still has no inbound
wire-JSON parser, so an unknown top-level field is discarded before any rejection check runs and the
engine would **accept** the envelope; a vector asserting rejection would turn the offline gate red
for whoever pushes next. Parser first, vector second — C#, and no cloud session has .NET. **A
session handed "add the unknown-field vector" as a task should read this entry before starting.**
This iteration was handed exactly that task and did read it first.

### B-7 status 2026-08-11 (seventeenth iteration) — unchanged, and it bounded one claim in this slice

No Android SDK, so `./gradlew … :core:test` did not run. **The specific cost this time:** PQ-S2-4's
phone-side half — that `PairingUnknown`/`PAIRING_GONE` is unreachable on today's wire — is derived
from reading `RelayClient.kt:283-284`, `OutboundQueue.kt:267-269`, `OutboundQueue.kt:288-290` and
`OutboundQueueTest.kt:269`, **not from executing anything**. It is labelled a hypothesis in
`AUDIT-REQUEST.md` **C-S2T-7** rather than a measurement. The relay half of the same question *is*
measured under miniflare. Whoever has an SDK should confirm the Kotlin half before anyone acts on it.

---

## B-7 SCOPE CORRECTED 2026-08-11 (eighteenth cloud iteration) — it never covered `:core`

**B-7 is not closed and its facts were never wrong.** `dl.google.com` and `api.foojay.io` are still
denied, re-measured this iteration (`000` for both). What is corrected is **how far the denial
reaches**, because seven iterations read it as a wider blocker than it is.

**What B-7 says:** the android **gate** — `checkCoreIsAndroidFree :core:test :app:assembleDebug
:app:lintDebug` — cannot run here. **Still true.** Three of those four tasks need the Android SDK.

**What it was read as saying:** that no Kotlin could be compiled or executed in a cloud session.
That produced `AUDIT-REQUEST.md` C-S2T-7's "*no Kotlin was compiled or run … treat it as a
hypothesis, not a measurement*", and it is the reason seven consecutive iterations produced spec
paragraphs. **False, and demonstrated false rather than argued:**

```
services.gradle.org   200      dl.google.com/dl/android/maven2/   000
repo1.maven.org       200      api.foojay.io                      000
plugins.gradle.org    200
```

`:core` is pure-Kotlin/JVM **by construction** — that is what `checkCoreIsAndroidFree` exists to
enforce — and all six of its dependencies are on Maven Central. It needs nothing from Google.

**What actually failed, and why it looked like `:core`.** `./gradlew :core:test` in the repository
fails here, but on the **root** script: `build.gradle.kts` declares
`alias(libs.plugins.android.application) apply false`, which resolves AGP from `google()` at
configuration time, and `settings.gradle.kts` includes `:app`. The failure is real; attributing it
to `:core` was the error.

**Now measured:** `scripts/core-probe.sh` runs `:core:test` here — **190 tests, 0 failed, 14
classes**, identical class-by-class to CI run `31518619205`'s `:core` step on the same commit, and
**proven live** (a one-line `RelayClient.kt` regression fails exactly two tests, exit 1). See
`LOG.md` §CP and `AUDIT-REQUEST.md` C-CP-1…8.

**The JDK was the last obstacle and it is not egress.** `:core` pins `jvmToolchain(17)`; Gradle's
auto-provisioner needs `api.foojay.io`, which is denied. The Ubuntu archive is not:
`apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless`. **The
`update` is not optional** — `install` alone 404s against the stale index.

### What remains blocked by B-7, unchanged

- **`:app` entirely** — AGP, `androidx`, Compose, Room, Robolectric all resolve from `google()`.
  Every `:app` claim stays CI-verified or unverified.
- **`checkCoreIsAndroidFree`, `:app:assembleDebug`, `:app:lintDebug`** — three of the gate's four
  tasks. **The gate remains unrunnable here and CI remains the gate.**
- **B-8's persisted `p2e` counter**, which belongs in Room, which is `:app`. Unchanged.
- **B-4's emulator lane**, which is a different blocker on a different machine and is untouched by
  any of this.

### What this unblocks, stated as a surface rather than a promise

A cloud session can now write Kotlin in `:core` **and run it**: `SyncPump`, `OutboundQueue`,
`RelayClient`, `PullPolicy`, `PairingFlow`, `EntitlementAckApplier`, `OutcomeMarkPolicy`,
`EnvelopeJson`, `ProState` and the protocol/vector suites. That is where most of the protocol logic
lives, so the honest next question for a cloud iteration is no longer "which spec paragraph can I
verify" but "which `:core` behaviour is unwritten or untested".

**The standing caution.** This runs **one** of the gate's four tasks. Any record citing it must say
`:core:test, via scripts/core-probe.sh` and name what did not run. Reporting it as "the android
gate passed" would be exactly the failure this file exists to prevent.

**Smallest human unblock for the rest of B-7:** unchanged — allow `dl.google.com` (and
`api.foojay.io`, though `apt` makes that one unnecessary) through the sandbox egress policy, or
accept CI as the gate for `:app`. Nothing here changes that ask.

---

## No new blocker arose 2026-08-11 (`:core` lane, eighteenth cloud iteration)

**Recorded because the absence is the point, and because one open item was closed by observation.**

The seventeenth iteration left S2T-10 open: two docs-only CI runs hung on test steps at ~16×
baseline, "still not diagnosed, and deliberately not chased". Checked rather than inherited:

| run | head | outcome |
| --- | --- | --- |
| 31517760672 | `c68ef07` | **cancelled** — superseded by the next records push |
| 31518284889 | `f49290e` | **cancelled** — superseded |
| **31518619205** | **`34237ea`** | **success**, steps back at baseline |

**Neither hung run ever failed.** Both were cancelled in-progress by the following push, so nothing
was ever red, and the branch tip is green with `:core` at 54 s (baseline 50 s) and `:app` Robolectric
at 108 s (baseline 93 s). **Transient runner infrastructure, self-resolved.** It is **not** a
blocker and was never one; leaving it phrased as an open anomaly would send the next session hunting
a fault that is not there.

---

## No new blocker arose 2026-08-11 (receive-order tests, nineteenth cloud iteration)

**Recorded because the absence is the point**, and because this iteration was handed a task that a
blocker already covers.

**B-6 was re-read, not assumed, and it is unchanged.** The iteration prompt assigned PQ-A2-3's
`invalid-unknown-field` vector as part of an S5 slice. B-6 exists precisely to stop that: `src/Sync`
still has no inbound wire-JSON parser, so the engine would **accept** an envelope carrying an
unknown top-level field, and a vector asserting rejection would turn the offline gate red on
`windows-latest` for whoever pushes next while proving nothing. **Parser first (C#), vector second.**
No cloud session has .NET. **This is the second consecutive prompt to describe S5 as not-started and
not-blocked; both halves are wrong** — see `LOG.md` §ER-0 and the seventeenth iteration's entry.

**B-7 unchanged, and its corrected scope held up under the first real use.** The eighteenth
iteration narrowed B-7 to "`:app` and three of the gate's four tasks", against seven iterations that
had read it as "no Kotlin runs here". This iteration **wrote and executed new Kotlin** in `:core` —
26 tests, plus six mutate-and-revert cycles of a production file — and needed nothing from
`dl.google.com`. The narrowed scope is now exercised rather than merely measured. **What remains
blocked is exactly what B-7 says:** `:app` entirely, `checkCoreIsAndroidFree`,
`:app:assembleDebug`, `:app:lintDebug`. CI is still the gate for those.

**B-4, B-5, B-8 untouched** — nothing this iteration did bears on the emulator lane, Room under
Robolectric, or the persisted p2e counter.

**One thing that could have become a blocker and did not.** ER-5 found the receiver's shared
docstring describing "structural decode" as one step where the Kotlin splits it across steps 3 and
6. That was chased to a conclusion rather than filed: both implementations still answer
`decrypt_failed` for an unrecognised `dir`, by different routes, and every engine `keyForDir` is
total so none can throw. **No divergence, so no blocker** — the prose is imprecise and the
correction belongs in a change that can gate both repos. Filing it as a blocker would have sent the
next session hunting a fault that is not there.

**One open question was opened, deliberately not as a blocker:** **PQ-ER-1** (a v2 dialect reads as
`decrypt_failed` rather than `version_unsupported`). Nothing is blocked; it is a decision that has
not been made, and it is diagnosability rather than safety.

---

## Twentieth cloud iteration (2026-08-12) — no new blocker, and one deliberate non-blocker

**Nothing this iteration attempted was obstructed.** Both files were written, run and mutated in the
sandbox; the slice completed.

**B-7's narrowed scope exercised a second time, and holding.** The eighteenth iteration corrected
B-7 from "no Kotlin runs here" to its actual measurement; the nineteenth wrote 26 tests on the
strength of it; this one wrote 28 more and ran **eight** mutate-and-revert cycles across two
production files, needing nothing from `dl.google.com`. **What remains blocked is exactly what B-7
says:** `:app` entirely, plus `checkCoreIsAndroidFree`, `:app:assembleDebug` and `:app:lintDebug`.
CI is still the gate for those.

**One machine change, which is not a blocker and is logged as a machine change:**
`apt-get install openjdk-17-jdk-headless`. `:core` pins `jvmToolchain(17)`; the sandbox shipped only
JDK 21; `api.foojay.io` is denied by the same egress policy as `dl.google.com`, so Gradle cannot
auto-provision. `scripts/core-probe.sh`'s header already prescribed this exact command, including
that `apt-get update` is not optional. **A future sandbox without it will fail the probe's
precondition check with the fix in the message**, which is the intended behaviour rather than a
blocker.

**B-4, B-5, B-6, B-8 untouched** — nothing this iteration did bears on the emulator lane, Room under
Robolectric, the inbound wire-JSON parser, or the persisted p2e counter. **B-6 was re-read and is
unchanged**, and it is why PQ-A2-3 was again not attempted despite the prompt naming it.

**One open question was opened, deliberately not as a blocker: PQ-B64-1.** The JDK's base64url
decoder accepts non-canonical trailing bits; whether .NET's does is **unmeasured**, and if it does
not, the two implementations disagree about whether an envelope is well-formed. **Nothing is
blocked** — the engine measurement is one line of C# on any machine with .NET, and the *vector* half
is already B-6's, alongside PQ-A2-3. Filing it as a blocker would send the next session hunting a
fault nobody has established exists.

**Deliberately not filed, and stated so it is not mistaken for an oversight:** two of the eight
mutations (M3, M7) were caught by no test. **Neither is a coverage gap** — both are semantically
equivalent changes, checked rather than assumed (HMAC zero-pads short keys; `+` and `/` are already
outside the JDK's URL alphabet). Recording them as gaps would have put a phantom in these records.

---

## Standing gate hazard (new 2026-08-12, twentieth iteration) — `ScreensFromFixtureTest` is flaky, and CI is the gate

**Not a blocker on any slice.** Nothing was obstructed. Filed because it will cost a future session
an iteration if it is not written down.

**Symptom.** `ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab` fails intermittently in
CI with `java.lang.AssertionError at ScreensFromFixtureTest.kt:69`. Measured on run
[31566551075](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31566551075):
**attempt 1 `failure`, attempt 2 `success`, identical `head_sha` `d8ae5da`, no push between them.**
The parent commit passed the same job hours earlier.

**Why it matters more here than in an ordinary repo.** Every record in this program ends with
"the probe runs one of the gate's four tasks; **CI is the gate** for the rest". A gate that goes red
on a good commit means a session either misattributes the red to its own slice or burns its
iteration chasing a phantom — and the records are explicit that calling something BLOCKED when
nothing blocks it is the failure mode to avoid.

**Attempts.** One re-run, which passed. **Bounded there deliberately** rather than re-running until
a pattern emerged: repeated re-runs would measure a frequency I cannot act on, and `:app` cannot be
built here at all.

**Diagnosis — a reading, not a reproduction (B-7: no Android SDK in a cloud sandbox).** Line 69 is
the **first** assertion, immediately after `compose.setContent { DashboardApp(db) }` and **before
any tab click**, so this is initial composition not having settled rather than the navigation loop
the test exists to walk. The same job log warns that `createComposeRule` is deprecated in favour of
`androidx.compose.ui.test.junit4.v2.createComposeRule`, whose note reads: *"The v2 APIs use
StandardTestDispatcher instead of UnconfinedTestDispatcher… Tests relying on immediate execution may
require explicit synchronization."* This test relies on immediate execution.

**Smallest human unblock — one of two, on a machine with the Android SDK:**

1. Insert `compose.waitForIdle()` (or `compose.waitUntil { … }` on the banner node) between
   `setContent` and the first assertion at line 69. Smallest possible change; leaves the deprecated
   rule in place.
2. Migrate the rule to `androidx.compose.ui.test.junit4.v2.createComposeRule`, which the warning
   names and which changes the dispatcher to `StandardTestDispatcher`. Larger, and it may require
   the same explicit synchronization anyway — **the warning says so**, so (1) is likely needed
   regardless and is the better first move.

Verify with `./gradlew --no-daemon :app:testDebugUnitTest --rerun-tasks` repeated several times, and
by re-reading this entry's attempt-1/attempt-2 evidence before declaring it fixed.

**Deliberately not attempted here.** The repair is an `:app` edit, `:app` cannot be compiled, run or
gated in this sandbox, and shipping an `:app` change whose only verification is another CI roll is
exactly what these records exist to prevent.

---

## Twenty-first cloud iteration (2026-08-12) — no new blocker, and two deliberate non-blockers

**Nothing this iteration attempted was obstructed.** One test file was written, run, and mutated
eight times in the sandbox; the slice completed.

**B-7's narrowed scope exercised a third time, and holding.** The eighteenth iteration corrected
B-7 from "no Kotlin runs here" to its actual measurement; the nineteenth, twentieth and this one
have now written 26, 28 and 26 tests on the strength of it. **What remains blocked is exactly what
B-7 says:** `:app` entirely, plus `checkCoreIsAndroidFree`, `:app:assembleDebug` and
`:app:lintDebug`. CI is still the gate for those.

**One machine change, which is not a blocker and is logged as a machine change:**
`apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless`, exactly
as `scripts/core-probe.sh`'s header prescribes. A sandbox without it fails the probe's precondition
check with the fix in the message, which is the intended behaviour.

**B-1, B-2, B-4, B-5, B-6, B-8 untouched.** **B-6 was re-read and is unchanged**, and it is why
PQ-A2-3 was again not attempted despite the iteration prompt naming it — for the fourth
consecutive run. The engine still has no inbound wire-JSON parser, so the vector would turn the
offline gate red for whoever pushes next while proving nothing.

**Two open questions raised, both deliberately NOT filed as blockers.**

**PQ-AAD-1** — the AAD is not an injective encoding of the header, in two independent ways
(`US_ASCII` is lossy; the `|`/`=` framing is ambiguous across the `ts`/`key_id` boundary). **Nothing
is blocked:** both halves are latent against conforming senders, and the one genuinely open
question — whether the C# engine encodes its AAD as UTF-8 — is settled by a single `grep` on any
machine with .NET. Filing it as a blocker would send the next session hunting a fault nobody has
established exists.

**PQ-SC-1** — `:core:test` runs only on the JDK's `SunEC`, and three of `SyncCrypto`'s defences
(the DER positive pad, the ECDH left-pad, and `verifySignature`'s `catch`) are **unobservable on
that provider**, so no test written here can cover them. **Nothing is blocked and nothing is known
to be wrong:** all three are insurance against a stricter provider, and the record exists chiefly
so a future session running a coverage tool does not read them as dead code and delete them. The
resolution — running the three assertions as an instrumented `androidTest` case against Conscrypt —
needs the emulator lane that **B-4** already covers, and adds no new obstruction of its own.

**Deliberately not filed, and stated so it is not mistaken for an oversight:** four of the eight
mutations were caught by no test. **One (M6) is not a coverage gap** — it is a semantically
redundant guard duplicating a throw the `try` already converts, checked rather than excused. The
other three are PQ-SC-1's subject and are recorded there rather than here, because a question about
the evidence's reach is not the same thing as an obstruction.

**The standing gate hazard from the twentieth iteration is unchanged.**
`ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab` is still flaky and still
unrepairable from a cloud sandbox (`:app` needs the SDK). This iteration touched no `:app` file, so
a red on that test against this branch is still the hazard and not this slice.

**RECURRENCE 2026-08-14 (thirty-third cloud iteration) — the fifth recorded instance, and PROVEN
again rather than assumed.** Run
[31788519473](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31788519473) on
`claude/android-a0-probe`, `head_sha` **`94ed7e6`**. **Attempt 1 `failure`** 09:32:46 → 09:36:55,
sole failing step **9, `Unit tests (:app, Robolectric)`**, signature **byte-identical** to the
symptom above: `ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab FAILED`,
`java.lang.AssertionError at ScreensFromFixtureTest.kt:69`, `35 tests completed, 1 failed,
3 skipped`. Steps 10–13 were **skipped**, not passed. **Attempt 2 `success`, ALL THIRTEEN STEPS**,
09:38:45 → 09:46:11, **identical tree, no push between** — which is what makes this a *proven* flake
instance rather than an assumed one. **This push could not have caused it:**
`git diff --stat e7c78ba..94ed7e6 -- app/ core/` is **empty** — three Markdown files changed
(`LOG.md`, `AUDIT-REQUEST.md`, `STATE.md`) and **no source at all**. The re-run was the recorded
remedy, and it is neither a merge nor a deploy.

**What it cost this time, stated because it is the argument for fixing it.** The failure arrived
*after* the slice was complete and pushed, and closing it out consumed several wait cycles at the end
of an otherwise finished iteration. **Still not a blocker on any slice** — nothing was obstructed —
but it has now cost five sessions the same tax, and the smallest human unblock is unchanged: open
`app/src/test/kotlin/app/careerseeker/dashboard/ui/ScreensFromFixtureTest.kt:69` on a machine with
the SDK and make the provenance-banner assertion wait for composition to settle rather than sampling
it.


---

## B-6 RESOLVED — 2026-08-12 (twenty-second cloud iteration)

**Closed by doing exactly what B-6 prescribed, in its order.** Its four steps were: (1) add an
inbound wire-JSON parser to `src/Sync`; (2) route `SyncHarness`'s vector loop through it; (3) *then*
add `invalid-unknown-field` via `generate.mjs`; (4) sweep the drift trap. All four are done, on
draft PR **#37** (stacked on **#32**). Evidence: `LOG.md` §WP, re-verification `AUDIT-REQUEST.md`
**C-WP-1…12**.

**Its diagnosis was right and survived re-checking.** `src/Sync` genuinely had no inbound wire
parser, an unknown top-level field genuinely was *accepted* by the engine, and the vector genuinely
could not be added first — a shared vector is unenforceable if one consumer goes green by accepting
the envelope the vector exists to refuse. Adding the vector first really was the trap B-6 named.
**Steps 1–2 were the work; step 3 was two lines**, exactly as written.

**What was wrong was one word of its reason, and it is worth naming precisely** so the next session
generalises it correctly. B-6 said:

> **Write the engine parser here.** No .NET on this machine (`which dotnet` → nothing), so it could
> not be compiled, let alone tested.

`which dotnet` is **still empty** on a fresh sandbox — the *measurement* was never wrong. The error
was treating "not installed" as "not obtainable". `dotnet-sdk-8.0` is in the **Ubuntu archive**
(`noble-updates/main`), every project pins `net8.0` exactly, there is no `global.json`, and the
denied hosts under B-7 (`dl.google.com`, `api.foojay.io`) are not involved. One `apt-get install`
and `dotnet build CareerSeeker.sln -c Release` reports **0 warnings / 0 errors**.

**This is the second time this exact shape has cost the program iterations.** The eighteenth
iteration found B-7 had never covered `:core`, after seven runs read it wider than it was. This is
the same failure one toolchain over: a blocker whose *stated symptom* stayed true while the *bound
it implied* had gone stale, and nobody re-tested the bound because the symptom kept reproducing.
**The lesson, stated so it is actionable rather than moral:** when a blocker's reason is "tool X is
absent", the re-test is `apt-cache policy <pkg>`, not `which <tool>`. Both prior blockers would have
been caught by that one command.

**What this unblocks beyond B-6**, stated as a surface rather than a promise: a cloud iteration can
now build and run **the entire C# engine** — `src/Sync`, `src/Engine`, the Gateway, the Verifier —
and **nine of the ten offline harnesses** (`Slice`, `ResearcherHarness`, `HookHarness`,
`StoreParityHarness`, `GatewayGateHarness`, `DispatcherNoSendHarness`, `LifecycleHarness`,
`RendererHarness`, `SyncHarness`). The engine-side halves that previous records filed as "unwritten,
not blocked — no .NET here" are now writable and runnable: the **C# `entitlement_ack` applier**
(S5's last piece), `RelayClient.cs`'s §6.4 cursor bound (S4, fourteenth run), and the engine half of
PQ-S2-4.

**Two limits that did NOT move, and no record may blur them.**

1. **`EngineHarness` cannot complete on Linux.** It dies at
   `FullDataDeletion.ResolveAllowedWorkspace` (`src/Engine/FullDataDeletion.cs:81`) because
   `PlanInstalledWorkspace()` resolves a Windows install path, which becomes `/` here, and the guard
   **correctly refuses a volume root**. That is the safety check working, not a regression — but it
   means its **217 assertions cannot be re-measured in a cloud session**, so the offline pin can be
   corroborated arithmetically (393 + 217 = 610) and never fully measured here.
2. **`scripts/Verify-Alpha.ps1` still cannot run.** There is no PowerShell in this sandbox and
   **none in the Ubuntu archive** — `apt-cache policy powershell` returns nothing, so the trick that
   solved .NET does not solve this one. The verifier could not even be parse-checked. **CI on
   `windows-latest` remains the gate**, and the main repo's merge policy (which requires a full local
   gate) therefore remains out of reach for a cloud iteration. **PR #37 is a draft and was not
   merged.**

**Smallest human unblock for what remains:** none needed for B-6. For the pin, the ask is unchanged
and cheap — let CI run. For PowerShell, if a future cloud session ever needs the real verifier, the
ask is to add the Microsoft package repository to the sandbox image; **it is not needed for the work
now in reach**, and CI already covers it.

---

## Twenty-second cloud iteration (2026-08-12) — no new blocker, and two deliberate non-blockers

**B-6 closed (above). Nothing new blocked.** B-1, B-2, B-4, B-5 and B-8 were not touched. B-7 is
unchanged in its own terms — `dl.google.com` and `api.foojay.io` are still denied, `:app` is still
unbuildable here, and three of the android gate's four tasks still cannot run.

Two findings are filed as **questions, not blockers**, because nothing is blocked by them and
calling either a blocker would send the next session hunting a phantom:

- **PQ-AAD-1 (now answered, not open).** Java's `US_ASCII` and .NET's `Encoding.ASCII` agree on BMP
  non-ASCII but **diverge on surrogate pairs** — Java collapses a pair to one `0x3F`, .NET emits
  two — so a supplementary-plane character in `ts` or `key_id` yields different AAD bytes on the two
  sides. It **fails closed** (tag mismatch → `decrypt_failed`, an interop failure and not an
  authentication one) and is unreachable for a conforming sender. The clean fix is a §3 charset
  constraint, which is **wire-visible and touches both implementations** — a gate for Brandon, not
  a unilateral edit, and a one-sided tightening would be the mission's named field bug. Nothing is
  blocked: no code needs it to proceed.
- **PQ-DUP-1 (new).** §3 says nothing about duplicate top-level keys; .NET takes the **last**. The
  Kotlin half is **not measured** and is not claimed. Not a bypass — a duplicated `seq` changes the
  AAD and the envelope then fails to decrypt.

Both are recorded in `docs/protocol-questions.md` with the commands that reproduce them
(**C-WP-11**).

---

## No new blocker arose 2026-08-12 (S5 entitlement_ack emitter, twenty-third cloud iteration)

Recorded as an entry because its absence is otherwise indistinguishable from an omission, and
because this iteration produced **three** things that look like blockers and are not.

**The slice completed end to end on this machine.** `SyncPayloads.EntitlementAck`, the
`SyncPublisher` method and the `InboundDispatcher` seam were written, built (0 warnings / 0 errors),
and asserted: SyncHarness 142 → **157, 0 failed**, with **5/5 mutations caught** and
`generate.mjs --check` green at 29 files. Nothing was attempted and abandoned. Draft PR **#38**.

**Not a blocker (1): S5's remaining host wiring.** `IEntitlementAckPublisher` has no production
caller — `grep -rn IEntitlementAckPublisher src/` outside `src/Sync/` prints nothing — so the
purchase path is closed **in the library, not in the running engine**. That wiring needs the pairing
vault and device session, which is the *same host work S2 and S4 already await*, and B-2 (`/pair`
page) gates the vault end. It is **unblocked and merely unwritten**. Filing it here would send the
next session hunting for a phantom, which is what this file exists to prevent.

**Not a blocker (2): PQ-A2-5.** The ack vectors are read by the engine and only *transcribed* by the
phone, because the android repo vendors `docs/sync-vectors/` at pin `679a317` and the ack vectors
postdate it. Closing it is a re-vendor plus a test rewrite — both cheap, neither doable without
`:core:test` (B-7, already recorded). It is a **conformance gap with a name**, in
`docs/protocol-questions.md` as PQ-A2-5, not an obstacle.

**Not a blocker (3): the prompt's stated S5 slice.** For the **sixth consecutive run** the iteration
prompt described S5 work that was already done (this time: amend §4.3.3, add the ack vectors, add
`invalid-unknown-field`, close PQ-A2-1/-2/-3 — all landed in PRs #32 and #37) and instructed that
the C# applier "must not be written because you cannot compile it". Both premises were stale and the
records said so **before** anything was touched. A stale prompt is not an obstacle; it is the reason
RULE ONE is a fetch and the reason the ladder is derived rather than trusted.

### B-6 status 2026-08-12 (twenty-third iteration) — still RESOLVED, and re-proved from scratch

The .NET route was re-tested on a **fresh sandbox** rather than assumed from the last run's note:
`which dotnet` → nothing (as always), `apt-cache policy dotnet-sdk-8.0` → candidate in
`noble-updates/main`, one `apt-get install` → SDK **8.0.129**, whole solution builds. The standing
lesson holds: when a blocker's reason is "tool X is absent", the re-test is `apt-cache policy <pkg>`,
not `which <tool>`.

### B-7 status 2026-08-12 (twenty-third iteration) — unchanged, and it bounded nothing this time

No Android SDK, so the android gate did not run — but **nothing in `:core` or `:app` changed this
iteration**, so `:core:test` was not needed and no claim here rests on it. The one android-side
statement made (that `EntitlementAckTest` transcribes rather than reads the vectors) is derived from
**reading** the file's own KDoc, and is labelled as such in C-AK-11 and PQ-A2-5.

### The PowerShell limit is unchanged, and it is the gate for 625

`scripts/Verify-Alpha.ps1` **did not run** — no PowerShell in this sandbox and none in the Ubuntu
archive, so it could not even be parse-checked. The offline pin moved 610 → **625** on a measured
Linux sum of **408** plus `EngineHarness`'s **217 carried from the CI-settled 610 pin, not measured
here** (it throws on Linux: `Refusing full-data deletion for a volume root`,
`src/Engine/FullDataDeletion.cs:81`, which is correct). **CI on `windows-latest` is the gate**, as it
was for 610. If a full local gate measures a different `EngineHarness`, the 625 pin is wrong and the
fix is the standing one: re-run, write the measured number, sweep every count-reporting doc in the
same commit.

---

### B-7 status 2026-08-12 (twenty-fourth run, S5/PQ-A2-5) — reproduced verbatim, and nothing new is blocked

**No new blocker.** Recorded so the next session does not re-derive the measurement, and to say
plainly that this iteration finished what it set out to do.

**B-7 reproduced, not inherited.** Measured again this session:

```
$ curl -sS -o /dev/null https://dl.google.com/dl/android/maven2/.../gradle-9.3.0.pom
curl: (56) CONNECT tunnel failed, response 403

$ curl -sS -o /dev/null https://api.foojay.io/disco/v3.0/packages
curl: (56) CONNECT tunnel failed, response 403
```

Both hosts still denied, so AGP cannot resolve and the JDK 17 that `:core` pins cannot be
auto-provisioned. `/root/.ccr/README.md` is explicit that a 403 from the proxy is a policy denial —
no mirror, no vendored AGP, no `ANDROID_HOME` fabrication was attempted.

**What is NOT blocked, stated positively.** `scripts/core-probe.sh` runs `:core:test` here (272/0
this iteration). The JDK 17 it needs installs from the Ubuntu archive in seconds —
`apt-get update -qq && apt-get install -y --no-install-recommends openjdk-17-jdk-headless` — which
the script's own failure message tells you. The remaining four gate tasks
(`checkCoreIsAndroidFree`, `:app:assembleDebug`, `:app:lintDebug`, `:app:test`) still need the SDK.
**CI is the unblock and always was.**

**PQ-A2-5's main-repo half is NOT recorded here, deliberately.** Amending `Sync-Protocol.md` §10.2
and the question itself is **unblocked and merely undone** — it was left because those statements
stay *true* until this android PR merges, and writing them early would put a claim in the engine
repo whose truth depends on an unmerged PR in another repo. Filing that under BLOCKED would send the
next session hunting a phantom, which is the failure this file exists to prevent.

---

## B-9 — The engine's inbound path cannot run: no Play licence key exists yet

**New 2026-08-13 (twenty-fifth cloud iteration), found by building the thing it blocks.**

**Symptom.** `BuildInboundPump` (engine repo, `src/Engine/Program.cs`, draft PR #39) returns null and
the engine prints *"Inbound is OFF: no Play licence key is configured, so a purchase cannot be
verified."* `GoogleSignedPayloadVerifier` requires the Play Console **"License Key for This
Application"** — an X.509 SPKI in standard base64 — and validates it eagerly at construction. There is
no production source for it anywhere in `src/`: measured, the verifier is constructed only in tests,
each with a locally generated key.

**This is expected, not a surprise, and the spec says so.** `docs/Sync-Protocol.md` §4.3.2: *"the
production licence key only exists once the Play app is created, and slots in then."* It is
configuration, deliberately not a constant. So B-9 is a **configuration gap awaiting an account-day
action**, not a defect — but it is recorded as a blocker because it is the one thing standing between
"the engine has a receive path" (now true, PR #39) and "the engine receives" (still false).

**Attempts.** None, and deliberately none. The two ways to proceed without the key were both refused
rather than tried: a verifier that accepts, and a verifier that rejects while looking like a real
signature check. Both are the hand-waving `CLAUDE.md` forbids by name on this repo's other
verification path, and a fail-closed placeholder is still a placeholder that a later change deletes
the guard from. Creating the Play app is embargoed (no Google/Play console), so there is no third
option a cloud session may take.

**What it blocks, precisely.** The `entitlement` inbound kind, and therefore the whole S5 purchase
loop end-to-end. It does **not** block the pump, the cursor rules, the resumable replay mark, or the
ack emitter — all of those are written and asserted (`AUDIT-REQUEST.md` C-IP-4…8). Collaterally it
also switches off `outcome` and `pull_request`, because the drain is gated as one unit; that is
acceptable only while neither has an engine implementation (S6 owns the outcome applier, S2/S4 the
republisher), and **if either grows one, this gate becomes wrong and must be split**.

**Smallest human unblock.** On account day, after the Play app exists: copy Play Console →
Monetisation setup → *"Licensing"* → the base64 licence key, and set
`CAREERSEEKER_PLAY_LICENSE_KEY` in `secrets/env.secrets` (or pass `--play-key`). Nothing else changes;
the engine picks it up on the next start and prints the pairing line instead of the inbound-off line.
A mistyped key fails **at startup** with a named error rather than silently at the first real
purchase — that eagerness is deliberate.

**Not blocked by this, and worth stating so nobody hunts for a phantom:** B-9 is not why S5's E2E is
unproven. That is the ordinary state of an unmerged stack plus B-4 (no emulator) plus the absence of
a `/pair` page (B-2). B-9 is narrower: it is the one input the engine half needs and cannot obtain.

### No new *android* blocker arose 2026-08-13 (twenty-fifth cloud iteration)

This iteration changed no android source, so B-4, B-5, B-7 and B-8 were neither exercised nor
re-measured, and none of their statuses moved. **B-7 was re-confirmed only in the negative sense that
matters here:** the android gate was not attempted because nothing in `app/` or `core/` changed, not
because it was blocked.

**One standing limit re-proved rather than carried:** `Verify-Alpha.ps1` still cannot run in a cloud
session — `which pwsh` is empty and `apt-cache policy powershell` offers no candidate, so the
`apt-get` route that closed B-6 does not repeat here. **CI on `windows-latest` remains the gate for
the offline pin (now 641).**

---

### B-2 status 2026-08-13 — the `/pair` page exists; the phone-facing half does not

The page landed: draft PR [careerseeker#42](https://github.com/ShivaClaw/careerseeker/pull/42),
`GET /pair` plus begin/complete/unpair controls, gated exactly like every other mutating control
(Host/Origin shape + control token). `Program.cs` holds the `PairingManager` across the two requests,
bootstraps the relay channel, takes the completion and writes `SyncPairing` to the vault — so the
vault the publisher has needed since PR #31 now has a way to be filled from the product rather than
from a harness. Full local gate green, **609 passed, 0 failed** (EngineHarness 217 → 228).

**One of the eleven new assertions found a real defect on its first run.** The confirmation code was
rendered only on the pre-completion screen, so after pairing the human had nothing to compare against
the phone — the entire MITM check, absent, while every other assertion passed and the flow worked.
That is the failure mode worth remembering from this rung: state that exists in the model and is
never rendered.

**Still open, and now precisely two things:**

1. **QR rendering is not implemented**, and the page says so in those words; the payload shown is the
   QR contents verbatim. No encoder was added because none can be *verified* here — no scanner, no
   emulator (**B-4**) — and a QR that cannot be proven to scan is the unverifiable artifact this
   repo's rules forbid. Either a verified encoder, or a manual-entry path on the phone (S3), closes it.
2. **The host half has no test.** The eleven assertions drive the dashboard with a *stub* seam; the
   real `BeginAsync`/`CompleteAsync` — relay bootstrap, `TakeCompletionAsync`, vault write — are
   exercised by nothing. The local-relay rig from S2's earlier slice (miniflare on `127.0.0.1:8787`)
   is the honest way to close this, and it does not need B-4.

**Smallest unblock for (2):** point a `dashboard` run at the local relay
(`--relay http://127.0.0.1:8787 --sync-vault <temp>`), drive begin → simulated phone completion →
complete with the same `src/Sync` primitives `SyncLiveSmoke` already uses, and assert the vault was
written. That is a bounded piece of work on a machine that can run .NET.

## No new blocker arose 2026-08-13 (PQ-CUR-1, twenty-sixth cloud iteration)

The slice was spec + `:core` Kotlin, both of which this sandbox can verify, and nothing in it was
obstructed. Recorded explicitly because "no entry" and "not checked" look identical a week later.

**Existing blockers, re-read rather than assumed:**

- **B-7** (cloud egress) — unchanged and it bounded this slice exactly where the eighteenth run said
  it would. `:core:test` ran here (**276/0 across 18 classes** via `scripts/core-probe.sh`);
  `checkCoreIsAndroidFree`, `:app:assembleDebug` and `:app:lintDebug` did **not**. No `:app` file
  moved this run, so the untested three cover nothing this change touched — but **CI is still the
  gate** and this run is not a gate result.
- **The PowerShell limit** — unchanged. `which pwsh powershell` is empty and `apt-cache policy
  powershell` offers no candidate, so `Verify-Alpha.ps1` **did not run and could not**. It also had
  nothing to say here: `grep -c "Sync-Protocol" scripts/Verify-Alpha.ps1` is **0** and no harness
  assertion moved, so `$ExpectedOfflineTotal` (598 on PR #33's branch) is untouched.
- **B-4** (emulator), **B-5** (Room/Robolectric), **B-8** (persisted p2e counter), **B-9** (Play
  licence key) — all untouched by this slice and all unchanged. B-8 is worth one line of contact:
  this change moves the *transport cursor*, which is in-memory state seeded from the persisted mark;
  it does **not** create the persisted counter B-8 is about, and does not shrink B-8 at all.

**One thing that is NOT a blocker and must not be filed as one.** PR #39's `InboundPump.cs` still
cites a §6.4 that is not on its branch, because PR #33 and PR #39 are **siblings**. That is a merge
*ordering* constraint — the two land together — not an obstruction: both branches are pushed, both
PRs are open, and nothing about it needs a human to unblock. Calling it BLOCKED would send the next
session hunting for a phantom, which is the failure these records exist to prevent.

---

## Blocker status 2026-08-13 (RelayClient pull result, twenty-seventh cloud iteration)

**No new blocker, and nothing was blocked this iteration.** The slice was chosen precisely because it
is verifiable here: C# compiles and runs in this sandbox (B-6's closure), the harness is offline, and
a stub `HttpMessageHandler` needs no relay, no SDK and no device.

- **B-7** (cloud egress / Android SDK) — **untouched and unchanged**, and it did not obstruct this
  slice because nothing in `core/` or `app/` moved. The android gate was **not run** and correctly so;
  `scripts/core-probe.sh` would have proven only that an untouched module still passes.
- **PowerShell is still absent** and this is *not* filed as a blocker, deliberately: it is a standing
  environmental limit recorded in every entry that reports a pin, not an obstruction awaiting a human.
  Re-measured this run — `which pwsh` empty, `apt-cache policy powershell` returns nothing, so **the
  `apt` trick that closed B-6 does not repeat.** CI on `windows-latest` runs `Verify-Alpha.ps1` and is
  the gate.
- **B-9** (Play licence key) — unchanged, and it is why the engine's inbound composition, including
  the new host-side logging added this run, is **compile-checked and never executed**.
- **B-1, B-2, B-4, B-5, B-8** — untouched by this slice, all unchanged. B-2 is worth one line: this
  hardens S2's *transport* for the fourth-plus time while the `/pair` page — the whole of what B-2 is
  actually about — **still has not moved**. That pattern is now long enough to be worth naming here
  as well as in `STATE.md`.

**Two things that are NOT blockers and must not be filed as ones.**

1. **The rebase pin conflict.** `origin/main` is `aac05f3` at pin **611**; this stack reads **662** on
   an older base. That is a *merge-ordering* consequence with a standing resolution already written
   (whoever lands first wins; the other re-runs the verifier and writes the measured number). Nothing
   needs a human to unblock it — it needs a machine that can run `Verify-Alpha.ps1`.
2. **`Misconfigured`/`Unauthorised` having no behavioural consumer.** The host logs each once and
   returns `null`. That is a *deliberately deferred* design step recorded in the LOG and in PR #45's
   self-audit, not an obstruction: nothing prevents the next session from acting on them.

---

## B-2 status 2026-08-14 (thirty-fourth run) — **THE "ONE REMAINING THING" HAS BEEN DONE FOR TWO DAYS**

**Read this before picking S2 again.** Every B-2 entry above this line, including the ones that say
so in bold, is **stale**: *"Still open, and it is exactly one thing: the desktop `/pair` page."*

**The `/pair` page merged into `main` on 2026-08-12 19:57:26 −0600.** PR **#42** from
`claude/s2-pair-page`, merge `d1bc698`, carrying `5a97b0f` — a `GET /pair` route plus three POST
controls in `Host.cs`, the host half (PairingManager, relay channel, DPAPI vault write) in
`Program.cs`, and eleven `EngineHarness` assertions. Verified this run by three independent means,
not by reading the commit message: `git merge-base --is-ancestor d1bc698 origin/main` returns true;
`git show origin/main:src/Engine/Host.cs | grep -c 'path == "/pair"'` returns 1; and **the eleven
`/pair` assertions executed on Linux this session, 11/11 PASS** (they became reachable as a
side-effect of B-10's fix).

**Why five sessions kept reporting it open, and it was NOT a skipped fetch.** The thirty-third run
fetched correctly. But it worked on `claude/s2-push-disposition` — **40 ahead of `main`, 16 behind
it** — and derived its blocker list **from its own stack**.
`git merge-base --is-ancestor d1bc698 origin/claude/s2-push-disposition` is **false**: the merge is
not in the stack. **Rule one says fetch. The rule this adds: re-derive against `main`, because a
long-lived stack is a stale ref that fetches perfectly cleanly.** The repeated "five hardenings, zero
`/pair` progress" note was true when written and became false without anyone noticing.

**What is actually left of B-2.** Not a screen. B-2 asks for "pair phone/emulator ↔ engine through
the relay"; the engine half is **done and merged**, and what remains is **the phone and the device**
— which is **B-4**, already tracked separately. **B-2 is therefore not CLOSED, but it is no longer
blocked on anything of its own**, and the next session must not go looking for a `/pair` page to
build. **Smallest unblock: B-4's** (one Android Studio SDK Tools checkbox), plus the live-relay
re-run already queued for return day.

---

## B-10 — thirteen `EngineHarness` assertions are Windows-only, and until this run they cost 220 more

**New 2026-08-14 (thirty-fourth run). A LIMIT, not a blocker — nothing is stuck, and CI covers it.**
Filed because the code comments in `tests/EngineHarness/Program.cs` reference it by name, and a
reference with no entry is the same bug as a claim with no command.

**Symptom (measured, not assumed).** `EngineHarness` aborted on Linux with an **unhandled**
`InvalidOperationException` at `tests/EngineHarness/Program.cs:221`, process exit **134**. A cloud
session reached **17 of the file's 237 assertion sites**; **220 never ran.**

**Diagnosis.** `FullDataDeletion.ResolveAllowedWorkspace` derives the volume root with
`Path.GetPathRoot` and trims the trailing separator (`FullDataDeletion.cs:79-81`). On Windows `C:\`
trims to `C:`; on Linux `/` trims to `""`, so the `IsNullOrWhiteSpace` arm refuses **every**
workspace as a volume root. **This is not a defect** — it fails closed, which is right for a
deletion guard, and the product is Windows-only.

**Attempts.** (1) Editing `src/Engine/FullDataDeletion.cs` — **considered and refused**: production
deletion-safety code, correct on its shipping platform, and a sandbox that cannot run
`Verify-Alpha.ps1` is not where you loosen a delete-everything guard. (2) Harness-side skip —
**taken** (PR #48): both platform-bound sections announce a skip instead of throwing. Result
**17 → 217 passed, 0 failed**, and the whole offline ladder now runs here at **598**, with
**598 + 13 = 611 = `$ExpectedOfflineTotal`** — confirmed against Windows CI, which measured
**EngineHarness 230** and **`Offline total: 611 passed, 0 failed`** on the same commit.

**What remains genuinely unrunnable off Windows, and why that is honest rather than a gap:**

| Section | Assertions | Reason |
| --- | --- | --- |
| `[ confirmed full-data deletion ]` | 6 | POSIX path root resolves to `""` (above) |
| `[ sync pairing vault ]` | 7 | `SyncPairingVault` is DPAPI-backed; `crypt32` has no POSIX equivalent |

**Smallest human unblock: none needed.** CI on `windows-latest` executes all 230 on every push, and
the 611 reconciliation proves the two skips are the *entire* difference. This entry exists so nobody
later reads "217 on Linux" as the whole harness — **it is 217 of 230, and the missing 13 are named.**

---

## Standing gate hazard #2 (new 2026-08-14, thirty-fourth run) — a Maven **403**, and it is NOT the `ScreensFromFixtureTest` flake

**Filed specifically so the next session does not misread this as the standing flake.** The two look
alike from the run list — android CI red on attempt 1 of a records-only push — and they are nothing
alike underneath. **Checking the signature before claiming the flake is the whole discipline here**,
and this run is the case that proves the discipline earns its keep: the reflex answer would have been
wrong.

| | Standing flake (5 prior instances) | **This** |
| --- | --- | --- |
| Failing step | **9**, `Unit tests (:app, Robolectric)` | **5**, `Set up Gradle` / dependency resolution |
| Duration | ~93 s into the job | **`BUILD FAILED in 48s`** |
| Signature | `ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab FAILED`, `AssertionError at ScreensFromFixtureTest.kt:69` | `Received status code 403 from server: Forbidden` from `repo.maven.apache.org` **and** `plugins.gradle.org` |
| Nature | a real intermittent test | **infrastructure — nothing was even compiled** |

**Symptom.** Run [31806621771](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31806621771),
`head_sha` `370ecfe`, attempt 1 `failure`. Gradle could not resolve the **AGP 9.3.0 buildscript
itself** — `com.android.tools.build:gradle:9.3.0` and its transitive deps
(`bundletool`, `jetifier-processor`, `kotlin-gradle-plugin-api:2.2.10`, `jsr305`, `dagger`, `jose4j`,
`slf4j-api`, …) each **403 Forbidden** from both Maven Central and the Gradle plugin portal, with
`There are 27 more failures with identical causes.` **No project code was compiled and no test ran**,
so no step past dependency resolution has a result at all.

**This push could not have caused it.** `git diff --stat 7009bfe..370ecfe` is **four Markdown files,
377 insertions**; scoped to `app/ core/ gradle/ build.gradle.kts settings.gradle.kts gradle.properties
.github/` the diff is **empty**. A records-only commit cannot make Maven Central return 403.

**Attempts.** Re-ran the failed job (attempt 2) — the recorded remedy, and neither a merge nor a
deploy. **Attempt 2 then sat in `Set up Android SDK` for well over eight minutes**, far longer than
its usual few seconds, which points the same way: **runner-side network trouble reaching Google/Maven
CDNs**, not a repo defect.

**Smallest human unblock: none, and probably nothing to fix.** A 403 from Maven Central to a GitHub
runner is transient infrastructure (rate-limiting or a CDN edge), and it clears on its own. **The
action is to re-run, then read the signature.** If it persists across several hours and multiple
re-runs, it becomes a real blocker and the mitigation is a dependency cache or a mirror — but do not
build that on one bad afternoon.

**What this does NOT license.** It does not make "CI was red, probably infrastructure" an acceptable
reading of any future red. **This entry exists because the signature was checked and found to differ
from the recorded one** — that check is the point, not the conclusion.

**Attempt 2 outcome, recorded rather than rounded up.** The re-run **cleared dependency resolution**
— `Set up Android SDK` ✓ (26 s) and `Set up Gradle` ✓ — which **confirms the 403 was transient**, as
diagnosed. But it then **hung in step 6, `Assert :core has no Android dependency`, for 21+ minutes**
(started 13:53:30, still `in_progress`), against a normal runtime of well under a minute. So the
network trouble did not clear so much as **change shape**: fast 403s became a stall, presumably on the
same CDN reads.

**Therefore: the android gate has NO result for `370ecfe`, and this run does not claim one.** Not
green, not red — **unfinished**. That distinction is the entire reason this paragraph exists; "the
re-run went green" would have been the flattering reading and it is not what happened. The push is
records-only, so nothing about the engine work depends on it. **Next session: re-run and read the
signature. If step 6 stalls again, that is the moment this stops being weather and becomes B-11.**

### CORRECTION (same run, 30 minutes later) — **"attempt 2 hung for 21+ minutes" is FALSE, and I caused what I then reported as a symptom**

The paragraph above is wrong on its central fact and is corrected here rather than edited away, because
the mistake is instructive.

**What actually happened.** Attempt 2 of run
[31806621771](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31806621771) did not hang.
It was **`cancelled` at 13:54:14, seventy-seven seconds after it started**; step 6 ran 13:53:30 →
13:54:12, i.e. **42 seconds**, and steps 7–13 are `skipped`, not pending.

**I cancelled it myself.** `.github/workflows/ci.yml:17-19` sets

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

so each push to this branch kills the previous run. I pushed `b2855cb` at 13:53:58 — **while attempt 2
was live** — which cancelled it; `b2855cb`'s own run was then cancelled by `f28a276` at 13:54:39. Three
runs, two of them killed by my own commits, in under a minute.

**How I got it wrong, which is the part worth keeping.** I polled the job repeatedly and kept receiving
a **stale `in_progress` snapshot** showing step 6 started at 13:53:30. I compared that start time to
wall-clock, got "21+ minutes", and wrote *hung*. **The elapsed time was real; the `in_progress` was
not.** The job had been finished for twenty minutes. **A timestamp inside a cached response is not a
measurement of now** — and "still in progress" is exactly the kind of reading that looks like evidence
while being an artifact of the transport. The rule this earns: **for a terminal state, read
`conclusion`, never infer it from a start time and a clock.**

**What still stands, unchanged.** Attempt 1's failure is solid and was read from the job log, not a
snapshot: **403 Forbidden** from `repo.maven.apache.org` and `plugins.gradle.org`, AGP 9.3.0's
buildscript unresolvable, `BUILD FAILED in 48s`. The comparison table above is still the right way to
tell that apart from the `ScreensFromFixtureTest` flake. And **the gate still has NO result for this
work** — that conclusion was right for the wrong reason.

**Also still true, and now better evidenced:** the run for `f28a276` reached step 6 at 13:55:48 and was
still there ~30 minutes later with its logs 404-ing (consistent with a genuinely running job, unlike
the cached case above). So *something* is making dependency resolution crawl, which fits the same
network trouble as the 403. **That observation now belongs to run
[31806923786](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31806923786), not to the
cancelled one.**

**Note for the next session, learned the hard way: do not push three commits in ninety seconds to a
branch with `cancel-in-progress: true` and then try to read CI.** Batch the records into one commit,
push once, and let the run settle. **This correction is itself another push and will cancel the
in-flight run** — so the run to read is the one for whatever commit is HEAD when you arrive, and the
earlier cancellations are noise I created, not signal.
