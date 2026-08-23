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

### RESOLVED — the gate is **GREEN**, and the "slow resolution" theory is withdrawn too

Run [31807155069](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31807155069) on
`head_sha` **`0ffe3b5`** — the commit carrying all of this run's records — **`conclusion: success`,
attempt 1, ALL THIRTEEN STEPS**, 13:57:59 → 14:06:21. **No re-run was needed.**

*Set up Android SDK* ✓ (27 s) · *Set up Gradle* ✓ · *Assert :core has no Android dependency* ✓ (1m45s)
· ***Assert vendored sync vectors match the pinned main-repo commit* ✓** — the `7328a0b` pin resolving
on a third machine, independently confirming this iteration moved no vector byte · *Unit tests (:core)*
✓ · ***Unit tests (:app, Robolectric)* ✓** — **the standing flake did not fire** · *Assemble debug APK*
✓ · *Lint* ✓ · *Assert no analytics or tracking SDKs ship* ✓ · *Upload debug APK* ✓.

**Two corrections this closes.**

**(1) "The gate has NO result for this work" is now superseded** — it was true when written and is not
true now. The gate is green.

**(2) The slow-dependency-resolution theory is WITHDRAWN.** The correction above said the `f28a276`
run "reached step 6 at 13:55:48 and was still there ~30 minutes later," and inferred that *something*
was making resolution crawl. **That was the same stale-snapshot mistake a second time**: that run was
`cancelled` at 13:57:56, about two minutes in, by my own next push. And this green run settles it
positively — steps 4–7 completed in **113 seconds total**, at entirely normal speed. **There is no
slow-resolution problem. There never was.** Step 6 simply takes ~1m45s, which is why a run killed
inside it always looked "stuck in step 6".

**So the whole afternoon reduces to one real event:** attempt 1's **transient 403** from Maven Central,
which cleared by itself. Everything else — three cancelled runs, two "hangs", one crawl — was **my own
churn under `cancel-in-progress: true`, read through a cache**. The comparison table further up is
still worth keeping, because distinguishing a 403 from the `ScreensFromFixtureTest` flake is a real
skill this program needs; but **B-11 was never warranted and is not filed.**

**The lesson, stated once and plainly, because it cost most of an iteration:** *I generated the
symptoms I then investigated.* Batch records into one commit, push once, and read `conclusion` — never
a step's start time against a wall clock.

---

## B-12 — the halt policy's WINDOW, not its shape: a decision this environment cannot source

**Milestone:** S2 (ordered next intent, item 1). **Filed 2026-08-14, thirty-fifth run.**
**A LIMIT, not a blocker** — nothing is stuck, and the rung moved. B-11 was deliberately never
filed (see the entry above); this one is filed because a later session *will* reach for the missing
number and should not have to re-derive why it is missing.

**Symptom.** `RelaySink` now records the halt policy's *shape*, measured rather than argued: a
bounded, self-clearing backoff on `PushDisposition.PairingDead` **alone** needs no product decision,
while the same backoff on `PayloadDead` needs one, because it would suppress the `entitlement_ack`
(**C-HALT-3**). What is still unsourced is the **window** — how many cycles, growing how, capped
where.

**Attempts.** Two, both in this session, both dead ends *by construction* rather than by failure:

1. **Derive it from the harness.** `SyncHarness` drives the real `SyncPushPath` composition and can
   count pushes per cycle, but a "cycle" there is a loop iteration in a test. It carries no wall-clock
   period, so a backoff of "skip 8 cycles" is unitless here and could mean eight seconds or eight
   hours.
2. **Read it off the engine.** The period lives in `EngineSyncBridge`, on the host side of the seam —
   the same side as `BuildSyncBridge`, which **has never executed in any harness or on any CI runner**
   (the standing item 2). Nothing in this sandbox can observe it, and inferring it from source would
   be a guess dressed as a measurement.

**Why it was not guessed anyway.** A backoff window that is too long converts a transient 401 — a
relay deploy blip — into a sync outage, which is precisely argument two *against* halting in
`RelaySink`'s own remarks. Picking that number blind from a cloud session would re-introduce the risk
the whole decision exists to avoid.

**Smallest human unblock.** One number, from a machine that runs the engine: the wall-clock period of
`EngineSyncBridge`'s publish cycle. With it, the backoff is a small, testable change on
`PairingDead` only. **Without it, do not implement the backoff** — the naive version is now caught by
name by `SyncHarness` (mutation **M7**, four `FAIL halt:` lines), which is the guard this run left
behind rather than a suggestion.

---

## B-13 — #36's declared base is not its actual base, and only a history-rewriting session can fix it

**Found 2026-08-14 (thirty-sixth run)** while costing the restack (`docs/Merge-Topology.md` §10.5).
Not a blocker on any rung — it is a **latent defect in the PR stack** that will silently lose a
commit if the wrong session restacks it first.

### Symptom

GitHub declares [careerseeker#36](https://github.com/ShivaClaw/careerseeker/pull/36)
(`claude/s2-transport-vocabulary`) as based on
[#33](https://github.com/ShivaClaw/careerseeker/pull/33) (`claude/s4-pull-request-semantics`).
**#36 does not contain #33's tip.** It forked at `b114d11`; #33 has since gained exactly one commit,
`3a8dfdd` ("S4/S5: 6.4's carve-out was drawn at the parse, and a failed tag fell through it —
PQ-CUR-1").

```bash
git merge-base --is-ancestor origin/claude/s4-pull-request-semantics \
  origin/claude/s2-transport-vocabulary && echo CONTAINS || echo "DOES NOT CONTAIN"   # DOES NOT CONTAIN
git log --oneline b114d11..origin/claude/s4-pull-request-semantics                    # 3a8dfdd
```

**The PR page shows no sign of this.** GitHub renders #36's diff from the merge-base, so the view is
self-consistent and the missing commit is invisible in it. Re-verify: **C-RST-8**.

### Why it matters

Restacking #36 onto the **rebased #33** includes `3a8dfdd`. Restacking it onto its **actual fork
point** drops `3a8dfdd` from #36's line. Both look correct locally and produce different trees, and
`3a8dfdd` is a **parse carve-out fix** (PQ-CUR-1) — a silent revert of it would not be caught by
#36's own tests, because #36 is about the transport vocabulary, not the parse.

### Attempts

**None, deliberately — this session is forbidden from the only fixes there are.** Correcting it means
either retargeting the PR base or rebasing/force-pushing `claude/s2-transport-vocabulary`, and the
standing rules for cloud sessions are **no force-push, no history rewrite, no retargeting, no merge**.
Measuring it and writing it down is the whole of what this environment may do, so the two-attempt
limit does not apply and no attempt was made.

### Smallest human unblock

One of, in a local session (either is ~1 minute):

1. **Retarget** #36's base on GitHub to `claude/s5-entitlement-ack-spec` (#32), which **is** a true
   ancestor of #36 — making the declared base match the actual one, and leaving `3a8dfdd` to arrive
   with #33 on its own line; or
2. **Rebase** `claude/s2-transport-vocabulary` onto the current tip of
   `claude/s4-pull-request-semantics`, so the declared base becomes true by moving the branch.

**(2) is the one that matches the record's intent** — #36 was written as a child of #33 — but it is a
force-push, so it is a human's call and not a cloud session's. Either way, verify afterwards with
**C-RST-8**: the check must flip to `CONTAINS`.

---

### B-7 status 2026-08-15 (thirty-eighth run, S6 composition-root decision) — unchanged, and it bounded exactly two claims

**No new blocker this run, and none is invented.** The slice was a *decision*, and a decision is
precisely the kind of work this environment can finish — the deliverable
(`docs/Composition-Root-Decision.md`, draft PR #49) is complete and needs no gate to be true.

What B-7 did bound, named so the next session does not have to re-derive it:

1. **Mutation M8 was cited, not re-measured** (**C-CR-3**). The claim *"deleting `: IE2pSeqStore` is a
   build error, not a silent no-op"* is the evidence for the decision's alternative — *retire
   identities with types, not seams* — and no .NET exists on this host to re-run it. If M8 is wrong,
   §2 of the decision doc loses its support. **Smallest human unblock:** on Windows, delete
   `: IE2pSeqStore` from `SyncPairingVault` and run `dotnet build CareerSeeker.sln -c Release`;
   expect a compile error, not 0/0.
2. **Both §5 proposals are unverified by construction.** A `ResumeSeq` wrapper struct and a typed
   pull direction change shipping C# signatures on the engine's startup path. Writing them from a
   cloud session would be a compile-only claim, which the house rules forbid. **Smallest human
   unblock:** a local session with `scripts\Verify-Alpha.ps1 -IncludePublish -IncludePackage`.

**Neither is a new blocker** — both are B-7 in its ordinary form (no toolchain here). They are
recorded because the decision doc's value depends on the first, and because the second is now queued
work with a named gate rather than an open question.

**Still true and worth restating:** `BuildSyncBridge` has never executed anywhere — not in a harness,
not on a CI runner, not in this run. That is **B-2's** territory (a real pairing vault and a relay on
the owner's machine), it is unchanged by this decision, and the decision does not claim to move it.

---

## No new blocker arose 2026-08-15 (shared-vector confirm code, fortieth cloud iteration)

The slice completed. `pairing-high-bit-confirm` is generated, verified by the generator's own
`--check`, re-derived independently in Python, and pushed as draft PR
[#50](https://github.com/ShivaClaw/careerseeker/pull/50) on `claude/s3-pairing-confirm-vector`.
Nothing was left half-done that a human must unblock.

### The consumer half is NOT blocked, and is deliberately not filed as a blocker

Neither `SyncHarness` (C#) nor the `:core` tests (Kotlin) yet assert against the new vector. That
work was **not attempted**, because writing it means compiling C# or running Gradle and this host has
**no .NET and no Android SDK** — so it could only be pushed unverified, which the mission forbids.

**That is an environment bound on *this* session, not a blocker on the *work*.** A local Windows
session can write both assertions today: nothing external prevents it, no human decision is owed, no
credential is missing, no upstream answer is pending. Filing it as `B-14` would send the next session
looking for a phantom obstacle. It is recorded as **item 1 of the next intent** in `STATE.md`
instead, which is where next slices belong.

The one caveat that *does* travel with it: adding a `Check()` to `SyncHarness` **will** move
`$ExpectedOfflineTotal` off 611 and therefore engages the drift trap — the pin and every
count-reporting doc must move in the same change. This branch adds no assertion precisely so that it
does not owe that sweep from a host that cannot measure the new total.

## B-14 — the phone cannot assert the confirm vector it does not have

**New 2026-08-15 (forty-first run).** Found by closing the C# half of the same item and checking
what the Kotlin half would actually need.

**Symptom.** `:core` has no test asserting `pairing-high-bit-confirm`, and one cannot be written.
The vendored corpus under `core/src/test/resources/sync-vectors/` is pinned at `679a317`, which
**predates the vector** — the file is not in this repo at all. A test naming it fails to find it,
and a test that silently skips when it is absent asserts nothing.

**Why the obvious diagnosis is wrong.** The inherited note said this half needs an Android SDK
(B-7). It does not: `:core` is Android-free by construction (`checkCoreIsAndroidFree`), its tests
run on the JVM, and this sandbox has run them before after installing JDK 17. **The blocker is the
pin, not the toolchain** — which matters, because B-7 has no human unblock inside this window and
this does.

**Attempts.** One, deliberately not two. Vendoring the vector by hand was considered and
**refused**: hand-copying a file into a corpus whose whole purpose is byte-identity with an
upstream pin is the drift event `CLAUDE.md` and the mission both forbid, and it would move the pin
without the decision that moving a pin represents.

**Smallest human unblock.** Merge PR #50 (`claude/s3-pairing-confirm-vector`) into `careerseeker`
`main`, then re-pin this repo's vendored corpus to the merge commit and re-run the vendored-vector
drift check. Both steps are Brandon's: the main-repo merge policy is conditional on a full local
`Verify-Alpha.ps1 -IncludePublish -IncludePackage`, which no cloud session can run, and a re-pin is
a cross-repo decision rather than a coding task. **PR #51 (the C# consumer) does not depend on
this** and stands on its own.

**Not blocked by this:** the engine-side assertion, which is done and CI-green (run `31897428719`,
offline total 617/0).

Re-verify: **C-CC-8**, and `git -C . log -1 --format=%H -- core/src/test/resources/sync-vectors/`
against `679a317`.

**Status 2026-08-17 (fifty-first run) — the unblock is now scheduled, and it needs one more word.**
B-14's unblock says *merge #50, then re-pin*. `RETURN-DAY.md` §3 **step 4 does exactly that**: #51
lands #50, and `pairing-high-bit-confirm.json` reaches `main` (`b95e83d`) in a merge §3 calls clean.
Measured post-landing corpus: `main` **29 payloads + `index.json`**, phone **28 + `index.json`**
(**C-POST-3**). So B-14 is unblocked by tomorrow's merges **only if the re-pin happens with them** —
otherwise the vector exists upstream, the phone still cannot assert it, and **no check in either repo
reports the gap** (see **B-16 status 2026-08-17**: the CI step written for this queries `?ref=$PIN`
and stays green). **The missing word is "same sitting".** Expected after re-pin:
`OK: 30 vector files match the generator.` and **30** vendored files.

---

### B-7 status 2026-08-15 (fortieth run) — unchanged, and it bounded exactly one claim

No Android SDK, so the android gate did not run and was not attempted. It bounded **one** claim this
run: nothing, in fact, on the android side — **this run changed no android file** beyond these
records, so there was no android claim for it to bound. It is recorded here only to keep the status
line unbroken.

The Windows bound (no `pwsh`, no .NET) is the one that bit: it makes **C-HB-8** — that
`$ExpectedOfflineTotal` stays **611** — an inspection claim rather than a measured one. It is
labelled as such in `AUDIT-REQUEST.md`, in `LOG.md` Milestone 5, and first in PR #50's self-audit.
Smallest human unblock, if anyone wants it measured before the PR moves: run `scripts\Verify-Alpha.ps1`
on the Windows machine and confirm the offline total reads 611.

### B-2, B-9, B-10, B-12, B-13 — untouched this run

This slice went nowhere near the `/pair` page, the Play licence key, the Windows-only `EngineHarness`
assertions, the halt-policy window, or #36's declared base. None of their statuses changed, and none
was re-derived, so the last recorded status for each stands.

---

## No new blocker arose 2026-08-16 (S6 / PQ-S6-3 resume reconciliation, forty-third cloud iteration)

**Nothing in this slice is blocked, and nothing new is filed.** PQ-S6-3 was engine-side C#, and C#
compiles and runs here. Both halves shipped, `SyncHarness` 130 → 146, relay 32 → 34, and CI measured
the pin at 627. Recorded here only so the next run does not go looking for a blocker behind an
un-merged PR: **#53 is unmerged because the merge condition is Brandon's full local gate, which is a
policy, not a blocker.**

### Three things are UNVERIFIED rather than blocked, and the distinction is the point

1. **`SyncLiveSmoke`'s new assertion.** Its replay check now asserts the 409 reads as `Replayed` **and**
   that `latest == 4`. It **compiles** but was **not run** — it needs a live relay, and the standing
   prohibition allows at most `GET /v1/health` against production, which was not exercised either.
   **Unverified, not blocked**: any machine with a local relay closes it (`C-RR-11`).
2. **`Verify-Alpha.ps1` did not run**, so a PowerShell syntax error in this run's edits to it would not
   have been caught locally. **CI retired this specific risk** — the verifier parsed and executed on
   `windows-latest` in run `31919261549` — but the *full* gate (`-IncludePublish -IncludePackage
   -IncludeLive`) still has not run anywhere.
3. **`EngineHarness` did not run here.** It aborts at `tests/EngineHarness/Program.cs:221` —
   `FullDataDeletion.PlanInstalledWorkspace` resolves `%LOCALAPPDATA%` to a volume root on Linux and
   the deletion guard refuses. **A Windows-path assumption in a test's setup, not a defect, and not a
   blocker**: CI runs it on Windows every push, and its 230 is now confirmed by subtraction
   (627 measured − 397 measured on Linux).

### The PowerShell limit, re-tested rather than inherited — and it HELD

`apt-cache policy powershell` finds **nothing**; there is no `snap` either. Unlike `.NET` and the two
other inherited impossibilities this book has overturned, **this one survived re-testing.** Recorded as
loudly as the ones that fell, because "an inherited limit is usually false" is itself becoming an
inherited claim, and it is not always true. **`Verify-Alpha.ps1` genuinely cannot run in this sandbox.**

### B-7 status 2026-08-16 (forty-third run) — unchanged, and it bounded nothing this time

Re-read, not assumed. Nothing in this slice is Kotlin: **no `:core`, no `:app`, no Gradle invocation,
and no android source file was changed at all.** B-7 therefore constrained no claim here.

### Two items this run declined, and neither is blocked

- **PQ-S2-4** — the relay answers `401` for a purged pairing, so the phone's terminal `PAIRING_GONE` is
  unreachable. Its own text ends *"Brandon decides"* and *"nothing is blocked. This is a decision that
  has not been made."* **Still true. Still not a blocker.**
- **PQ-S6-1's wire fork** — `outcome_ack` (a) vs fire-and-forget (b). Left standing by #52 and left
  standing again here. **A question with no default-proceed, absent from mission §2's gate list.**
  Minting a payload kind that binds a second implementation is not an agent's call.

---

## B-15 — the rewritten vector drift check has never run on a runner (forty-fifth run, 2026-08-16)

**Symptom.** `.github/workflows/ci.yml`'s "Assert vendored sync vectors match the pinned main-repo
commit" step was rewritten this run to compare the vendored and upstream vector sets in **both**
directions (it previously iterated the vendored side only and was structurally blind to a vector added
upstream and never vendored — see **C-CI-1**). The new step is YAML-valid and behaviourally verified,
but **only against a local stub** that serves the GitHub contents API out of git objects. No Android
SDK, no JBR and no runner exists in this sandbox, so the workflow has not executed once.

**Three specific things are unverified**, all in the part the stub had to fake:

1. the contents-API **directory** listing shape — the step assumes a JSON array of
   `{name, type}` and filters `select(.type == "file")`;
2. **`jq` on the runner image** — used to parse that listing (it is present on
   `ubuntu-latest` today, but nothing in this repo pins it);
3. that the **directory** `?ref=<sha>` lookup resolves for a commit on an **unmerged branch**. The
   per-file lookup at `7328a0b` is proven — the old step did it every run — but the directory form
   is not independently confirmed against an off-`main` SHA.

**Failure mode if any of the three is wrong**: the step **errors** rather than silently passing, so it
fails loud, not quiet. It cannot regress the guarantee below what the old one gave; the worst case is
a red build on a correct tree.

**Attempts.** Extracted the step body verbatim from `ci.yml` (so the test cannot drift from the
workflow) and ran it against three trees — untouched, under-vendored, hand-edited — with `curl`
PATH-shadowed by a git-backed stub. All three behaved as specified (**C-CI-2**). That is as far as this
environment reaches. `./gradlew` is unavailable for the same reason as **B-7**.

**Smallest human unblock.** **Push the branch and read the first CI run** — nothing local is needed.
The step prints `pinned main-repo commit: <sha>` then either
`OK: 29 vendored vectors match <sha>, and the sets agree` or a `::error::` naming the drifting file.
If it errors instead, item 1 or 3 above is the cause; adding `sudo apt-get install -y jq` or swapping
the listing parse for `grep -oE '"name": *"[^"]+"'` resolves it without touching the logic. **This is
a draft PR precisely so that read happens before anyone relies on it.**

### B-15 NARROWED — 2026-08-16 (forty-sixth run): the pass path ran on a runner; the failure paths did not

**The read this blocker asked for has happened.** Run
[`31938526828`](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31938526828), job
`95144180297`, `ubuntu-latest`, head `703e8f2` — the step succeeded (09:16:51 → 09:17:00) and printed
verbatim (**C-CI-5**):

```
pinned main-repo commit: 7328a0bc043335491cd96a67d634e8eea2a13af9
OK: 29 vendored vectors match 7328a0bc043335491cd96a67d634e8eea2a13af9, and the sets agree
```

**All three named unknowns are resolved, and resolved together** — the step could not have printed
that line otherwise. (1) The contents-API **directory listing shape** parsed: `jq`'s
`select(.type == "file")` produced 29 names, and the set comparison agreed with `ls`. (2) **`jq` is
present** on the runner image. (3) The **directory** `?ref=` lookup **does** resolve for `7328a0b`, a
commit on an unmerged branch. The "29 files stay inside one page" assumption also held.

**What is still open, and it is the reason this is NARROWED rather than CLOSED.** Only the **pass**
path executed. The two failure paths — a vendored vector deleted, a vendored vector hand-edited —
remain verified **only** against the local stub of **C-CI-2**. A green step proves it does not
false-alarm; it does not prove it still fires. That distinction is the whole point of a drift check,
so it is not rounded off here.

**Smallest human unblock for the remainder.** Runner-confirming a failure path means pushing a
deliberately-broken tree. That is cheap but not free: it leaves a red run and a stray branch on a repo
whose PRs are under review, and the house rule against deleting branches means the branch stays. **A
cloud session should not do that unilaterally.** Either (a) Brandon pushes a throwaway branch with one
vendored vector deleted and confirms the step reports
`::error::upstream has vector(s) that were never vendored`, or (b) accept the stub evidence for the
failure paths and say so here — the paths are three lines of `comm` and `diff -q`, and the pass path
now proves the surrounding plumbing is real.

---
## B-16 — nothing in either repo notices that the vendored pin has fallen behind upstream (forty-fifth run, 2026-08-16)

**Symptom.** The android CI step compares the vendored vector set against **the pin recorded in
`VECTORS.lock`**, never against upstream `HEAD`. That is correct and deliberate — the pin is what
makes the corpus reproducible, and comparing against a moving head would make CI fail on the other
repo's schedule. **But it means a pin that has fallen behind upstream is invisible to every automated
check in both repos.**

This is not theoretical, and it is the real history behind this run's slice (**C-CI-4**): from the
addition of the two `entitlement-ack` vectors until `056a1dd` re-vendored on 2026-08-12, the phone
lacked **three** upstream vectors. The vendored set matched the pin **exactly** the whole time, so the
android step was green, the engine's `generate.mjs --check` was green, and both repos' documents
correctly said "no drift". **Every check was right and the phone was still behind**, because no check
was watching the thing that had moved. It was closed by a human noticing, not by a signal.

**Why this run did not fix it.** A check for "is the pin behind upstream?" has to decide *which*
upstream ref to compare against, and the honest answer today is **not `main`** — the entire sync track
including every vector lives on **unmerged draft branches**, and both `679a317` and `7328a0b` are
off-`main` by necessity, not by accident. So the check would have to name a branch, and naming a draft
branch in CI makes the android build depend on a ref someone may rebase or delete. **That is a design
decision about the two repos' release coupling, not a bug fix, and it is not an agent's to make
unilaterally.**

**Attempts.** Scoped only. The one-directional gap in the step (**C-CI-1**/**C-CI-2**) was fixed
because it is unambiguous; this one was measured, attributed, and left. Recorded rather than quietly
absorbed into the fix, because a reader seeing "drift check hardened" would reasonably assume this
case was covered, and it is not.

**Smallest human unblock — a decision, then a one-line check.** Brandon picks one:
1. **advisory, non-blocking** — a scheduled job comparing the pin against a named engine branch and
   opening an issue (or printing a warning) when it lags. Cheapest; no build depends on a draft ref.
2. **blocking, after the stack merges** — once the sync track is on `main`, compare against `main` and
   fail. Clean, but gated on the restack (§10.6/§11.4), which is itself waiting on the #53 decision.
3. **accept it** — the pin moves when a human re-vendors, and that is the intended workflow. Then say
   so in `VECTORS.lock`, so the next reader does not mistake the guarantee for something wider.

Until one is chosen, **the guarantee is exactly "the phone matches the pin", never "the phone matches
the engine"** — and `VECTORS.lock`'s own wording is close to implying the latter.

### B-16 status 2026-08-17 (fifty-first run) — the abstract gap now has a filename and a date

This blocker was filed with a *past* instance (the three `entitlement-ack` vectors, closed by a human
noticing). It now has a **future** one, measured rather than predicted: **executing `RETURN-DAY.md`
§3 opens it again, at step 4.**

Measured post-landing (**C-POST-1/-2/-3**), from base `aac05f3`: after §3's six merges, `main` carries
**29 payloads + `index.json`**; the phone vendors **28 + `index.json`**. The delta is
**`pairing-high-bit-confirm.json`** (`b95e83d`), arriving with **#51 — the merge §3 calls clean**.
**No file under `docs/sync-vectors/` conflicts in any of the six merges**, and the resulting corpus is
**byte-identical** whether every hand-resolution is taken `--ours` or `--theirs`, so this is
**determined by the merge set, not by how Brandon resolves the two stops.**

**And the check that exists for this cannot fire.** `.github/workflows/ci.yml:127-133` already
implements *"upstream has vector(s) that were never vendored"* — written for exactly this case — but
it queries `?ref=$PIN` (lines 86, 101). While the pin is `7328a0b`, which **also** lacks the vector,
the comparison is phone-against-a-commit-that-agrees-with-it: **green, across the very event it was
written to catch.** This is not a defect in that step; it is B-16's design gap deciding its behaviour.

**This sharpens option 2 into the cheap one.** After §3 lands, the sync track *is* on `main`, so
"compare against `main` and fail" stops being gated on the restack — and it would fire immediately,
correctly, on this vector. **The decision (H3) is unchanged and still Brandon's**; what changed is
that option 2's precondition is satisfied by the very merges he is about to run.

**Smallest human unblock, unchanged in substance, now with a number:** re-pin `VECTORS.lock` to the
post-landing merge commit and re-vendor **in the same sitting as the merges**. Verify with
`node docs/sync-vectors/generate.mjs --check` → **`OK: 30 vector files match the generator.`** on
`main`, and `ls core/src/test/resources/sync-vectors/v1 | wc -l` → **30** on the phone. Both
`VECTORS.lock`'s *"ACTION WHEN PR #38's STACK MERGES"* note and **B-14**'s unblock already say
*re-pin afterwards*; **neither says same-sitting, and neither gave a number to check it against.**

**Not claimed:** that anything breaks at runtime. `ProtocolVectorsTest` enumerates from the phone's
own `index.json`, so an un-vendored vector is a **silently untested case**, not a failure. No gate ran
for any of this (**C-ENV-1**); the Kotlin and the workflow YAML were **read, not executed**.

---

## B-17 — the landing cost compounds by one hand-resolution per pin-touching branch (forty-seventh run, 2026-08-16)

**Symptom.** `$ExpectedOfflineTotal` in `scripts/Verify-Alpha.ps1` is an **absolute** number, and the
drift trap requires every branch that adds an assertion to update it *and* the four docs that report
it. So any two unmerged branches that add assertions conflict with each other **by construction**,
even when their code is disjoint and both merge cleanly into `main` in isolation.

Measured this run (**C-LAND-3**, **C-LAND-4**): four of the seven leaves move the pin — to `617`,
`615`, `627` and `793`, from bases `611`/`611`/`611`/`598`. The first to land is free; the other
three each stop the merge on the same five files. **N pin-touchers cost N−1 hand-resolutions.**

**Why this is a blocker entry and not just a costing.** The number is not static. The fleet went from
one pin-touching leaf to four over this window, and **each future run that adds harness assertions on
a new branch adds one more stop** to whatever a human eventually merges. The cost grows in *runs*,
not in PRs (§10.4 measured that growth rate for the chain; §12.3 shows it now applies across leaves
too). Nothing in the sandbox can discharge it: resolving a pin conflict correctly means writing **the
number the gate measures**, and the gate is `Verify-Alpha.ps1` — PowerShell and .NET, neither of
which is on this machine (**C-LAND-7**, measured, not assumed).

**Attempts.**
1. **Measure it properly rather than estimate.** Done — `scripts/fleet-probe.sh land` (new this run)
   probes the cumulative sequence in the object store, touching no working tree. It reproduces 3
   stops, 4 if the order is wrong, 2 if #53 is closed.
2. **Find an ordering that costs zero.** There is none. Landing a fresh-off-`main` pin-toucher first
   claims the one free slot; every other pin-toucher conflicts regardless of order. The best case is
   N−1 and this run's ordering achieves it.
3. **Compute the landed value so a human could pre-fill it.** Refused as unsound, not merely
   unavailable: §11.3 already showed the deltas are not disjoint once #53 is in the set, so the
   arithmetic §10.3 relied on does not close. A pre-filled wrong pin is a hard gate failure, which is
   the drift trap working — but it would waste a return-day cycle.

**Smallest human unblock — land the fleet, and prefer sooner to later.** One Windows session:
decide #53 (**H1**, `RETURN-DAY.md` §3 step 0), then merge the six leaves in the measured order with
a full local `Verify-Alpha.ps1 -IncludePublish -IncludePackage` between merges, writing the measured
pin at each stop. That is 2 hand-resolutions today. It is 3 if #53 stays open, and one more for every
assertion-adding branch opened before it happens.

**A cheaper structural fix exists but is a design decision, not a bug fix:** if the pin were expressed
as a per-harness count summed at runtime rather than one hand-maintained absolute total, disjoint
branches would stop colliding. That changes what the drift trap guarantees, and `CLAUDE.md` names the
single pinned total as the thing that makes a dropped assertion a hard failure. **Not an agent's call.**

## B-18 — the recurring prompt re-issues a completed slice, and its reading list omits the handoff (forty-eighth run, 2026-08-16)

**Symptom.** The scheduled prompt that starts each cloud iteration assigns S5's spec half — amend
`docs/Sync-Protocol.md` §4.3 with the `entitlement_ack` body, add the vector via `generate.mjs`,
close PQ-A2-1/-2/-3. **That work has existed since 2026-08-09** (`8575539`, `22b028e`, `7328a0b`,
**C-STOP-1**). This is the **thirteenth** consecutive run to be assigned it. **UPDATED 2026-08-17
(fifty-second run): the count is now SEVENTEEN.** Runs 49, 50, 51 and 52 were each assigned it again
and each declined it again, re-verifying rather than rebuilding. Attempt 3's banner is working as
designed — run 52 reached "already built" from its **first document read** — but a cheap wrong
assignment is still a wrong assignment, and the banner cannot retire the schedule. `RETURN-DAY.md` §7.5
already names the loop as the defect that cost this window the most iterations; run 47 could describe
it but could not stop it, and neither can this run.

**The mechanism, which is new here and is the actionable part.** The prompt tells a fresh session to
read `docs/CLAUDE-ANDROID-MISSION.md`, `STATE.md`, `LOG.md`'s tail, `BLOCKED.md`, `docs/S-Ladder.md`
and `AUDIT-REQUEST.md`. **`RETURN-DAY.md` is on none of those lists** (**C-STOP-2**), and until this
run neither `STATE.md`'s pointer line nor the mission doc named it. So the closing handoff — the one
document written specifically to stop this — was **invisible to the reading path that needs it**. A
session could follow its instructions exactly and still spend the run re-deriving a conclusion that
was written down two runs earlier.

Two further prompt details are **stale against the repo**, both cheap to check: the vendored pin is
`7328a0b`, not `679a317` (moved 2026-08-12, **C-PIN-1**), and S5 is described as "NOT STARTED" when
its spec half and its emitter are both built. The prompt's own rule — *"verify it; do not trust this
summary"* — is correct and is what caught all three.

**Attempts.**
1. **Do the assigned slice anyway.** Refused, and this is the substantive judgement of the run: it
   would produce a duplicate §4.3 amendment competing with `8575539`, and re-running the generator to
   "add" existing vectors risks touching the corpus the android repo vendors at `7328a0b` — the
   prompt itself classes any change to an existing vector's content as a **cross-repo drift event**.
   The correct response to "build the thing that is built" is to prove it is built and stop.
2. **Fix the prompt.** Not possible from here. It is stored scheduler configuration, not a file in
   either repo; nothing in either checkout can edit it, and the sandbox has no access to the schedule.
3. **Put the pointer where the reader actually looks.** Done, and it is this run's deliverable — a
   banner at the top of `docs/CLAUDE-ANDROID-MISSION.md` (first file on the list) and of `STATE.md`
   (second), each naming `RETURN-DAY.md`, the three commits, and the one-command check. This makes
   the next firing **cheap** rather than preventing it: the session still starts, but it should reach
   the truth in its first read instead of its fifth derivation.

**Smallest human unblock — turn the routine off, or repoint it.** Mission §7's terminal instruction
is *"clear the goal"*, and the stop condition was crossed at run 45 and executed at run 47. The
schedule is the only part of this program still running, and everything left on the board needs a
Windows gate, an emulator (**B-4**), a relay deploy, or a decision only Brandon can make
(`RETURN-DAY.md` §5). **If the routine is meant to keep running**, the prompt's "YOUR SLICE THIS
ITERATION" section should be replaced with: *read `RETURN-DAY.md` §5 and pick from the human queue
what a Linux sandbox can actually advance* — which today is very little, and that is the honest state,
not a failure.

### B-18 status 2026-08-17 (forty-ninth run) — the banner worked; the loop fired anyway, for the fourteenth time

**Unchanged as a blocker, and now with one run of evidence about the mitigation.**

Attempt 3 of B-18 (put the pointer where the reader actually looks) was landed at run 48 as a banner
on `docs/CLAUDE-ANDROID-MISSION.md` and `STATE.md`. **It did what it was built to do.** This session
was assigned the same S5 spec half — the **fourteenth** consecutive assignment — and reached "it is
already built" from its **first** document read, not its fifth derivation. It then spent one command
verifying rather than re-deriving (**C-STOP-1**: `OK: 29 vector files match the generator.`,
`exit=0`) and moved on to work that was actually available.

**The blocker itself has not moved, and the distinction matters.** The banner reduces the *cost* of
each firing; it cannot reduce the *count*, because the prompt is stored scheduler configuration that
no file in either repo can edit. Run 48 said this explicitly and it is worth restating rather than
quietly re-discovering: **a cheap wrong assignment is still a wrong assignment.** The evidence that
it is cheap is that this run had time to do something else; the evidence that it is still wrong is
that the something else had to be chosen by the session rather than by the prompt.

**Smallest human unblock — unchanged, and now due today.** Mission §7's terminal instruction is
*"clear the goal"*. The stop condition was crossed at run 45, executed at run 47, and this is run
49. Brandon returns **2026-08-18**, tomorrow. Either turn the routine off, or replace its "YOUR SLICE
THIS ITERATION" section with *read `RETURN-DAY.md` §5 and pick from the human queue what a Linux
sandbox can actually advance*. **Do not leave it pointed at S5's spec half**; that instruction has
now been wrong for eight days.

**A note for whoever reads this next.** Nothing in this entry is a complaint about the schedule. The
routine is doing exactly what it was configured to do, and the configuration was correct when it was
written. It has simply been overtaken by the work, and the only actor who can retire it is the one
who created it.

### B-18 status 2026-08-17 (fiftieth run) — fifteenth firing, and the first with a measured cost of skipping rule one

**Unchanged as a blocker.** The stored prompt assigned S5's spec half for the **fifteenth**
consecutive run. It is built (`8575539`, `22b028e`, `7328a0b`), it is an **open draft PR** (#32, with
#37 for PQ-A2-3), and this session verified all of it after a fresh fetch rather than inheriting it
(**C-STOP-1**, **C-STOP-3**: `OK: 29 vector files match the generator.`, `exit=0`; vendored corpus
byte-identical to the pin, 29 files, `exit=0`).

**The mitigation keeps working, and its ceiling is now visible.** Run 48's banner did its job for the
second consecutive run: this session reached "already built" from its **first** document read. The
cost of a firing is now roughly four commands. **The count is unchanged, because the prompt is stored
scheduler configuration that no file in either repo can edit** — attempt 2 in the original entry, and
still true.

**One thing this run adds, and it is the sharpest evidence yet for why the reading list matters.**
This session's android checkout started on a **detached HEAD 200 commits behind** the work branch
(**C-FETCH-1**). At that ref, `RETURN-DAY.md`, run 48's banner and this B-18 entry **do not exist**.
The banner mitigation is therefore **conditional on rule one**: a session that skips the fetch reads
a repository where the last twelve runs never happened, finds no banner, finds the assigned slice
genuinely absent from `main`, and builds it — producing the duplicate §4.3 amendment and the
cross-repo drift risk that the prompt itself warns about. **The two mitigations are not independent;
the fetch is what makes the banner reachable.** That is worth knowing before anyone concludes the
banner alone has the problem contained.

**A third route to the truth, added this run as C-STOP-4 — the cheapest so far.** The slice is
visible as an **open draft PR title**: #32 reads *"S5 (first half): the `entitlement_ack` body,
PQ-A2-1/-2, and the relay cap §3.1 turned out to require"*. One PR-list call, no clone, no fetch, no
`git`. This does not close B-18 either; it lowers the floor on the cost of each firing once more.

**Smallest human unblock — unchanged, and now due today.** Turn the routine off, or repoint it.
Mission §7's terminal instruction is *"clear the goal"*; the stop condition was crossed at run 45 and
executed at run 47. **Brandon returns 2026-08-18 — tomorrow.** If the routine is meant to keep
running past that, the prompt's "YOUR SLICE THIS ITERATION" section should be replaced with: *read
`RETURN-DAY.md` §5 and pick from the human queue what a Linux sandbox can actually advance* — and
the honest answer today is **very little**, because everything left needs a Windows gate (**H2**), an
emulator (**B-4**/**H4**), a relay deploy (**H5**), or a decision only Brandon can make (**H1**,
**H3**). That is the state, not a failure.

### B-18 status 2026-08-17 (fifty-third run) — the eighteenth firing, and the first attempt that leaves the repository

**Unchanged as a blocker.** The scheduled prompt assigned S5's spec half again. It has been built
since 2026-08-09 (`8575539`, `22b028e`, `7328a0b`); verified again this run in one command
(**C-STOP-1**: `OK: 29 vector files match the generator.`, `exit=0`) and declined again. **Eighteen
consecutive assignments across nine days.**

**Attempt 4 — notify the human out of band. Executed this run.** Attempts 1–3 all lived inside the
repository: do the slice anyway (refused, correct), edit the prompt (impossible — it is scheduler
configuration no file in either checkout can reach), and move the pointer to where the reader looks
(run 48's banner, which worked: sessions now reach "already built" on their first read). **All three
share one defect, and it is the reason five recorded requests never produced a fix: the actor who can
retire the routine does not read this file.** B-18's smallest human unblock has been correct since
run 48 and undelivered since run 48.

This run had a notification channel earlier runs did not, and used it: a push to Brandon carrying the
eighteen firings, the three commits, the one-command check, the two measurably stale prompt facts
(pin `679a317` → `7328a0b`; S5 described as "NOT STARTED" when its spec half and emitter are both
built), and `RETURN-DAY.md` §5 as the queue of what actually remains.

**What attempt 4 is not.** It is not a fix, and it does not close B-18. It cannot turn the routine
off; only Brandon can. It is also **the one claim in this run's records that the repository cannot
verify** (**C-B18-4**) — no file attests it, and an auditor should treat delivery as unconfirmed
until Brandon says otherwise. If run 54 fires on the same slice, attempt 4 did not land either, and
the next session should say so plainly rather than re-recording the request a seventh time.

**Smallest human unblock — unchanged, and now due today rather than tomorrow.** Mission §7's terminal
instruction is *"clear the goal"*. The stop condition was crossed at run 45, executed at run 47, and
this is run 53. Brandon returns **2026-08-18**. Either turn the routine off, or replace its
"YOUR SLICE THIS ITERATION" section with *read `RETURN-DAY.md` §5 and pick from the human queue what
a Linux sandbox can actually advance*. **Do not leave it pointed at S5's spec half**; that
instruction has now been wrong for nine days.

### B-18 status 2026-08-17 (fifty-fourth run) — run 53 named the test; this run is its result, and it failed

Run 53's entry closed with a falsifiable prediction: *"If run 54 fires on the same slice, attempt 4
did not land either, and the next session should say so plainly rather than re-recording the request a
seventh time."*

**Run 54 fired on the same slice.** The prompt again assigned §4.3's `entitlement_ack` body, the
`generate.mjs` vector, and PQ-A2-1/-2/-3 — the **nineteenth firing** and the **fourteenth consecutive
assignment of work built on 2026-08-09**. It again described the vendored pin as `679a317` (stale
since 2026-08-12) and S5 as "NOT STARTED" (its spec half and emitter are both built). So, plainly, as
instructed: **attempt 4 did not change the schedule.** Whether the notification was delivered and not
acted on, or never surfaced, is not decidable from here — that is exactly the limit **C-B18-4**
records.

**Attempt 5 — executed this run, deliberately different in content.** Re-sending attempt 4's message
would be the seventh recording of the same request, which run 53 ruled out. So this notification does
not re-argue that the slice is built; it reports the **state that changed**: the routine is now firing
on the **eve of return** with **no ladder work left that this environment can advance**, every
remaining item being in `RETURN-DAY.md` §5 and needing Brandon. The ask is one action, unchanged in
substance since run 48: **retire the schedule, or repoint it.**

**What attempt 5 is not.** Not a fix. Not repo-verifiable (**C-B18-4** applies unchanged). It does not
close B-18, and **B-18 cannot be closed by any agent** — the obstacle is scheduler configuration that
no file in either checkout can reach, which has been the finding since run 48 and is now confirmed by
five failed attempts rather than argued.

**Cost accounting, so the next reader can weigh it.** Nineteen firings have produced **zero** duplicate
S5 commits — every session correctly derived the state and declined — so the loop is **wasteful, not
destructive**. That is worth stating precisely: the guard that keeps holding is the mission's
"derive state before acting" rule plus run 48's banner, and it has held **six** times running. A
session that skipped rule one would be the failure mode, and **C-FETCH-1** measures why: the arriving
checkout is detached at docs-only `main`, where `RETURN-DAY.md` and this entry **do not exist**.

**Smallest human unblock — unchanged, and now due today.** Mission §7's terminal instruction is
*"clear the goal"*. The stop condition was crossed at run 45 and executed at run 47; this is run 54;
Brandon returns **2026-08-18**. Either turn the routine off, or replace its "YOUR SLICE THIS ITERATION"
section with *read `RETURN-DAY.md` §5 and pick from the human queue what a Linux sandbox can actually
advance*. **Do not leave it pointed at S5's spec half** — that instruction has been wrong for nine
days.

### B-16 status 2026-08-17 (fifty-fourth run) — the wording half is closed; the decision half is untouched

B-16's entry ended by noting that **`VECTORS.lock`'s own wording is close to implying** the phone
matches the engine, when the guarantee is only that it matches the pin. **Measured this run
(C-LOCK-1), the wording was not merely close to implying it — it asserted it, and it was false in both
directions**: `main` carries 26 vector files, the phone 29, the three extra being the S5 vectors that
live only on the unmerged stack the pin sits on, with `index.json` differing because it is the manifest
that lists them.

Commit `89068d8` narrows the header to **"the phone matches the pin, never the phone matches the
engine"** and records the measurement in the file, with the coming reverse-direction gap
(`pairing-high-bit-confirm.json` at `RETURN-DAY.md` §3 step 4, **H7**) named in place.

**This closes only the documentation half, and it is not option 3.** B-16's three options — advisory
job / compare against `main` once the stack lands / accept and document — are a decision about the two
repos' release coupling. **H3 remains open and Brandon's**, and the note in `VECTORS.lock` says so
explicitly. The CI step at `.github/workflows/ci.yml:127-133` was **not** modified: making it fire
requires naming an upstream ref, which is the decision itself. **B-16 stays open.**

### B-14 / B-16 status 2026-08-18 (fifty-fifth run) — the re-pin now has a command; the *detection* still does not

**Symptom, restated so the two halves stay separate.** When `RETURN-DAY.md` §3's six merges land,
`main` gains `pairing-high-bit-confirm.json`, the phone is behind by one vector, and **no check in
either repo reports it** — CI fetches upstream at `?ref=$PIN` (`ci.yml:100-101`) so a stale pin is
invisible by construction, and `ProtocolVectorsTest` enumerates from the phone's own `index.json` so
the missing vector is never asserted (**B-14**). Two things were missing: something that **does** the
re-pin, and something that **notices** it is needed.

**What this run closed — the doing half.** `scripts/repin-vectors.sh` (`423cade`), wired into
`RETURN-DAY.md` §3 (`d89e833`). Verified against a replay of the actual six merges: at the current pin
it is a proven no-op (`exit=0`), at the replayed post-landing head it reports
**`+ pairing-high-bit-confirm.json`** and **`~ index.json`** and refuses to call it clean (`exit=1`),
and its write path produces exactly three changed paths and is idempotent (**C-REPIN-1**). Pointed at
#51's branch instead of the merged `main` it reports **`+1 / −3 / ~1`** — that branch never carried the
three S5 vectors, so re-pinning there would delete them — and it prints the removals before writing
(**C-REPIN-2**). Four refusal paths measured (**C-REPIN-3**). **`--check` with no rev is the offline
half of CI's drift step and needs no network.**

**What is still blocked — the noticing half, and it is not a tooling gap.** Nothing invokes the script
on its own. Making CI fire on a stale pin requires naming an **upstream ref** to compare against, and
that names the two repos' release coupling. That is **H3**, it is Brandon's, and B-16's three options
(advisory job / compare against `main` once the stack lands / accept and document) are untouched.
**`.github/workflows/ci.yml` was deliberately not edited this run.** Writing a tool that performs a
re-pin the plan already calls for is not a choice among those three.

**Attempts, so the next session does not repeat them.** Two ways to close the noticing half were
considered and both were rejected as out of authority, not as too hard: (a) add a second CI step
comparing against `origin/main` — that *is* option 2 of H3; (b) make the existing step compare against
a ref read from a new field in `VECTORS.lock` — same decision, wearing a config file.

**Smallest human unblock.** Answer **H3** — one line — and the noticing half is a ten-minute CI edit
on top of a script that already does the work. Until then the honest state is: the re-pin is one
command, and remembering to run it is still a human's job.

**Not verified here, and this bounds the claim above.** `/usr/lib/jvm` on this host holds **only JDK
21**, so `core-probe.sh` exits at its own `jvmToolchain(17)` precondition and **`repin-vectors.sh` was
never run against `:core:test`** (**C-ENV-1**). It is proved correct **about bytes** — that the
vendored tree matches the pin it names — not proved to leave the phone's tests green. That is
`RETURN-DAY.md` §3 step 2 and it needs the Windows box. **B-14 stays open until a re-pin has actually
been run and `:core:test` has passed after it. B-16 stays open on H3.**

### B-18 status 2026-08-18 (fifty-fifth run) — sixteenth firing, on the morning the window closes

**Symptom, unchanged.** The routine fired again and again assigned S5's spec half — built
2026-08-09, re-verified this morning for the fifteenth time (**C-STOP-1**). Its two other stated facts
remain stale in the same direction: the pin is `7328a0b`, not `679a317`, since 2026-08-12; and S5 is
**PARTIAL**, not "NOT STARTED".

**What is new, and it is the reason this entry exists rather than a pointer to the last one.** This is
the first firing **on or after Brandon's stated return date**. Mission §7's stop condition was crossed
at run 45, executed at run 47, and has now been re-confirmed at every run since. The routine is no
longer running *during* an unattended window; it is running *past* one. **The guard that keeps holding
is still only rule one plus run 48's banner** — a session that skipped the fetch would arrive detached
at docs-only `main`, where neither `RETURN-DAY.md` nor this file exists (**C-FETCH-1**).

**Attempt 6 — a notification, sent this morning**, leading with the return-day freshness stamp rather
than with the stale-slice complaint, because that is what is actionable today. **Recorded as
unverifiable from the repository, per C-B18-4's standard: no file attests delivery.** In-repo attempts
1–3 cannot reach scheduler configuration and that has not changed.

**Smallest human unblock — unchanged, and now overdue rather than due.** Turn the routine off, or
replace its "YOUR SLICE THIS ITERATION" section with *read `RETURN-DAY.md` §5 and pick from the human
queue what a Linux sandbox can actually advance*. **Do not leave it pointed at S5's spec half.** Ten
days now.

### B-14 status 2026-08-18 (fifty-sixth run) — the unblock was necessary and **not sufficient**, and that is now fixed

**B-14's unblock reads *merge #50, then re-pin*. Both steps could have completed and B-14 would still
have been open** — measured, not argued (**C-ENUM-2**).

**Symptom.** With §3's six merges replayed and a **copy** of this tree re-pinned at the result,
`pairing-high-bit-confirm.json` is vendored and listed in `index.json` — and corrupting its expected
confirm code to `999999` left `:core:test` **green at 288 / 0, `exit=0`**.

**Cause, read in the file.** `ProtocolVectorsTest.pairing derivation reproduces every vector value`
hardcoded `load("pairing-basic")` despite its name. The only enumerator, `envelopeVectors()`, filters
`type == "envelope"`; the new vector is `type: "pairing"`. **Vendored, manifested, read by nothing.**

**Why it mattered here specifically.** The vector exists to separate a signed-int32 reduction
(`-936782`) and a dropped zero-pad (`30514`) from the conforming `030514`. `pairing-basic` cannot
catch either — its digest has the high bit clear and six significant digits. The inert vector was the
only one that could.

**Fixed this run.** `4ddad07` enumerates valid `type: pairing` vectors from the manifest. The same
mutation now fails: `6-digit confirm code (pairing-high-bit-confirm) ==> expected: <999999> but was:
<030514>`, `exit=1`. **Test count is 288 before and after — coverage changed, the number did not**, so
this is invisible to anyone auditing by test count.

**Still open, and still Brandon's:** the **re-pin itself** (**H7**). This run moved no pin and wrote no
vector byte; the corpus still matches `7328a0b` (**C-ENUM-5**). B-14 closes when H7 happens — and the
assertion that makes H7 mean something is now in place, which it was not this morning.

**Not a B-16 fix.** B-16 is about *detection* of a stale pin, and **H3** — which upstream ref CI should
compare against — is untouched. This changes what happens **after** a re-pin, not whether anything
notices one is needed.

Re-verify: **C-ENUM-2**, **C-ENUM-3**, **C-ENUM-4**.

### B-7 status 2026-08-18 (fifty-sixth run) — narrowed, materially

**`:core:test` runs in this sandbox.** `apt-get install -y --no-install-recommends
openjdk-17-jdk-headless` — the fix `core-probe.sh`'s own error message prints — satisfied its
`jvmToolchain(17)` precondition, and the probe returned **288 tests, 0 failed, across 19 classes**,
`exit=0` (**C-JDK-1**). Every prior run recorded this as unrunnable; the blocker was the JDK, and
`core-probe.sh` had said so since 2026-08-11.

**Unchanged:** `dl.google.com` is still denied, `sdkmanager`/`adb` are still absent, and
`:app:assembleDebug` / `:app:lintDebug` still cannot run here. **B-7 is narrowed, not closed**, and the
android gate is still Windows/SDK work. **The install was inside a disposable container**; a future
session on a fresh image must repeat it, which is why the command is in **C-JDK-1** rather than in prose.

---

### B-18 status 2026-08-18 (fifty-seventh run) — return day arrived and passed; attempt 5 leaves the repository

**Unchanged as a blocker. The count is now TWENTY-TWO consecutive firings.**

**Symptom, restated with the one detail that is new.** The scheduled prompt again assigned S5's spec
half — §4.3's `entitlement_ack` body, the ack vector via `generate.mjs`, PQ-A2-1/-2/-3. That work has
existed since **2026-08-09** and is open as draft PR **#32**. Re-verified this run rather than
assumed (**C-STOP-5**): all four assigned gates are closed in `docs/Sync-Protocol.md`, the three
vectors exist, and `node docs/sync-vectors/generate.mjs --check` on
`claude/s5-entitlement-ack-emitter` returns **`OK: 29 vector files match the generator.`**, `exit=0`.
The prompt's stated pin `679a317` is still stale (it is `7328a0b`), and its "S5 is NOT STARTED" is
still wrong.

**What is new: the deadline the earlier statuses were written against has passed.** Runs 49–53 each
framed the unblock as *"Brandon returns 2026-08-18"*. That date **is today**, it has passed, and the
schedule fired again with the same completed slice attached. **Nothing has been merged, closed or
undrafted in either repo** (**C-RET-4**) — engine `main` still `aac05f3` and android `main` still
`ebfaf81`, both unmoved since 2026-08-12, with 18 and 6 PRs still open and still draft. So the loop
is not merely re-issuing finished work; it is doing so **while the queue it feeds has not moved in
six days**, which is the part that makes each additional firing net-negative rather than merely
wasteful.

**A note on the count, because two records disagree and the discrepancy should not be silently
smoothed over.** B-18's own history anchors run 52 at the **seventeenth** assignment, which makes
this run the **twenty-second**; `autonomy/claude-state`'s run-56 heartbeat says **"sixteenth"**. The
B-18 anchor is used here because it is the document being updated and its per-run increments are
traceable. **The exact integer is not load-bearing; that it is far past one is.**

**Attempts — 1 through 4 unchanged, and why attempt 5 is shaped differently.**

1. **Do the assigned slice anyway.** Refused again, on the same reasoning and it has strengthened
   with each copy that exists: it would author a **fourth** divergent §4.3 amendment competing with
   `8575539`, and re-running the generator to "add" vectors that exist risks the corpus the android
   repo vendors at `7328a0b`. The prompt itself classes that as a **cross-repo drift event**.
2. **Fix the prompt.** Still not possible from here. It is stored scheduler configuration, not a
   file in either checkout; the sandbox has no access to the schedule.
3. **Banner on `docs/CLAUDE-ANDROID-MISSION.md` and `STATE.md`.** Landed at run 48, working as
   designed — this run reached "already built" from its first document read. **It reduces the cost
   of each firing and cannot reduce the count.**
4. **A notification-shaped request recorded in-repo** (run 53). No effect, and in hindsight the
   reason is structural: **it was written into a file, and the loop does not read files to decide
   whether to fire.**
5. **NEW THIS RUN — escalate out of the repository, to the one channel that reaches a human.** The
   finding was sent to Brandon **by push notification**: that the routine is re-issuing nine-day-old
   completed work, that the real bottleneck is a merge decision, and that the queue has not moved in
   six days. Attempts 1–4 all terminated inside the repository, which is exactly where a scheduler
   cannot see them. **This is the first attempt whose delivery mechanism does not depend on someone
   opening a file.**

**Smallest human unblock — unchanged, and now overdue rather than pending.** Mission §7's terminal
instruction is *"clear the goal"*; the stop condition was crossed at run 45 and executed at run 47,
and this is run 57. **Either turn the routine off, or replace its "YOUR SLICE THIS ITERATION"
section with:** *read `RETURN-DAY.md` §5 and pick from the human queue what a Linux sandbox can
actually advance.* Today that is very little — everything left needs a Windows gate
(`Verify-Alpha.ps1`), an emulator (**B-4**), a relay deploy, the `/pair` page (**B-2**), or a
decision only Brandon can make (**#53**, `RETURN-DAY.md` §5). **That is the honest state, not a
failure, and it is why more authoring is the wrong thing to ask for:** 18 draft PRs are already
queued behind a merge step that costs 3 stops, and **adding a 19th makes return day harder, not
easier** (**B-17**).

**No new blocker arose this run.** The one thing that could have become one — run 56's unresolved CI
— resolved **green** instead (**C-CI-57**), and is recorded as evidence rather than as a blocker.

---

**STATUS 2026-08-22 (eighty-third run) — the count is FORTY-EIGHT, and the notification channel is
now being rationed on purpose.** The stored prompt was re-issued unchanged: same landed slice, same
stale vendored pin `679a317` (real pin `7328a0b`), same ladder summary describing S2 as PARTIAL and
PR #31 as the tip. **Declined again, re-verified rather than rebuilt (C-83-2).**

**What is new is not the firing; it is the restraint.** Run 81 escalated B-18 to Brandon by push
notification. Run 82 declined to re-send and wrote its successor a test: *notify on `main` moving, a
PR merged or undrafted, the stored prompt changing, or a gate result — not on another firing and not
on another draft PR.* **This run applied that test, all four triggers came back negative, and no
notification was sent (C-83-10).** That is the second deliberate silence, and it is recorded here so
the next session inherits the rule rather than re-deriving it: **the routine firing again is not
news; a notification per firing would train the channel to be ignored, and the one thing B-18 needs
from Brandon is that he still reads it.**

**Smallest human unblock — unchanged.** Turn the routine off, or replace its "YOUR SLICE THIS
ITERATION" section with *read `RETURN-DAY.md` §5 and pick from the human queue what a Linux sandbox
can actually advance.*

**No new blocker arose this run, and one candidate was deliberately NOT filed.** The engine half of
run 83's finding — `SyncHarness`'s `Contains("mlkem")` assertion accepting the same wrong value the
phone's guard did (**C-83-5**) — needs `dotnet` and the gate, which is **H2**, already filed. Filing
it a second time as a B-* would send the next session hunting a phantom; it is carried as the ordered
intent's **NEW ITEM 1** with the exact mutation that proves it.

---

## B-19 — S5's phone route exists and nothing in `:app` constructs it (fifty-eighth run, 2026-08-18)

**Symptom.** `EntitlementRoutingApplier` and `ProStateStore` landed this run in `:core`, test-green
at **299 / 0** (**C-S5-2**), with a negative control pinning the pre-fix behaviour (**C-S5-3**).
**None of it runs on a phone yet**, and it cannot until three `:app`-side pieces exist:

1. a `ProStateStore` implementation — Room table or DataStore, and **which one is a decision, not a
   detail**: Pro state must survive a replica wipe, and the replica is wiped wholesale by the first
   real snapshot (`EnvelopeApplier`'s fixture-clearing path). Storing it in the replica DB is
   therefore the *wrong* choice unless it is explicitly excluded from that wipe;
2. the configured `knownProductIds` set — `pro_unlock` per `docs/Monetization-Decision.md`, but the
   **production ids exist only once the Play app is created**, which is a Console step (S7, human);
3. the composition root itself, which is **S4's** and does not exist: nothing in `:app` constructs
   `SyncPump`, `RelayClient` or any `:core` type today (**C-S5-1**, second grep).

**Why this is BLOCKED here rather than merely unfinished.** All three are `:app`, `:app` needs the
Android SDK and AGP from `dl.google.com`, and this sandbox's egress policy denies it (**B-7**).
`scripts/core-probe.sh` reaches `:core` *only*. Writing the wiring blind would be the exact thing
this program's standing rule forbids: source that no one has compiled, claimed as done.

**Attempts.** None beyond confirming the boundary — the boundary is the finding. Item 1 was
scoped rather than decided, deliberately: it is a persistence decision with a user-visible failure
mode (an unlock that a later snapshot silently revokes), and it is Brandon's.

**Smallest human unblock.** On the Windows box, with the SDK present: implement `ProStateStore`
against DataStore (the recommendation — it is outside the replica DB and therefore outside the
snapshot wipe, which resolves item 1 by construction), wire

```kotlin
applier = EntitlementRoutingApplier(roomApplier, EntitlementAckApplier(setOf("pro_unlock")), proStateStore)
```

into whatever constructs `SyncPump`, and run the full gate
`./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug --rerun-tasks`.
`SyncPump`'s KDoc now carries that snippet at the point where the wiring is chosen (`03e3e8f`), so
the composition root does not have to find this file to get it right.

**What is NOT blocked, and should not be re-derived.** The route's own behaviour — routing,
idempotence, the `IGNORED` disposition and its consequence, §4.3.3's ignore-rather-than-unlock, and
PQ-A2-4's boundary — is settled and test-pinned here (**C-S5-3** … **C-S5-5**). This blocker is
about **construction**, not about semantics.

---

### B-18 status 2026-08-18 (fifty-eighth run) — the twenty-third firing, and its premise is now measurably false too

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half. That work has
existed since **2026-08-09** as draft PR **#32**; re-verified rather than assumed (**C-STOP-6**).
The prompt's pin `679a317` is still stale (**`7328a0b`**), and its "S5 is NOT STARTED" is still
wrong. **Nothing has been merged, closed or undrafted in either repo** — engine `main` still
`aac05f3`, android `main` still `ebfaf81`, unmoved since 2026-08-12, 18 and 6 PRs still open and
still draft (**C-RET-5**). Return day was 2026-08-18; it has now passed twice over.

**What is new, and it is a correction to this entry's own five previous statuses.** Every prior
status treated the prompt as *stale about state* — right about the world, wrong about the date. It
is also **wrong about this environment**, and that error was costing real work. The prompt says:

> *Do NOT write the C# applier or the Kotlin applier unless you can compile them — you cannot.*

**Half of that is false, and has been since run 56 (C-JDK-1).** `:core` is Android-free by
construction and `scripts/core-probe.sh` runs `:core:test` on this host. Twenty-two runs took the
prohibition at face value and produced records; **run 58 checked it, and found a silent product
defect behind it** — `EntitlementAckApplier`, the phone's only unlock path, with no caller anywhere
(**C-S5-1**). Pro could not have unlocked on any phone built from this branch, and no test, counter
or error anywhere would have said so.

**So the lesson generalises past this prompt.** *Verify the summary* was already house law and is
why the ladder table is re-derived every run. **Verify the constraints too** — a stale
"you cannot do X" is more expensive than a stale "X is not started", because the second gets
checked on arrival and the first is never tested at all.

**Attempts — 1 through 5 unchanged; 6 is this run.**

6. **NEW — stop treating the prompt's prohibitions as boundaries and re-derive them, then do the
   work the prohibition was hiding.** This is the first firing that produced product code rather
   than records, and it did so *because* it disbelieved the prompt in a second place. It **does not
   close B-18**: the routine will fire again with the same text, and the next slice found this way
   is not guaranteed to exist. The escalation still went out by push notification, since attempts
   1–4 all terminated inside files the scheduler cannot read.

**Smallest human unblock — unchanged, and now with one line added.** Turn the routine off, **or**
replace its "YOUR SLICE THIS ITERATION" section with: *read `RETURN-DAY.md` §5, and re-derive what
this environment can run before assuming what it cannot — `:core` compiles and tests here
(`scripts/core-probe.sh`); `:app`, .NET and the emulator do not.* The real bottleneck is unchanged
and is a merge decision (**#53**) plus a Windows gate, not authoring capacity.

---

## No new blocker arose 2026-08-18 (S5 defect-class guard, sixtieth cloud iteration)

This run wrote `:core` Kotlin and hit nothing it could not finish. Recorded here anyway, because
a run that files nothing should say so explicitly rather than leave the reader wondering whether
it forgot.

### B-19 status 2026-08-18 (sixtieth run) — **unchanged, and deliberately not narrowed**

**Still open, still exactly as run 58 scoped it.** The three `:app` pieces — a `ProStateStore`
implementation, the configured `knownProductIds` set, and the composition root that constructs
`EntitlementRoutingApplier` — are untouched. **No `:app` file was written this run.**

**Read the new test's bounds before treating it as progress on this blocker.**
`PayloadKindCoverageTest` (**C-KIND-1**) asserts that every engine→phone `PayloadKind` is
classified into exactly one destination. It **does not** assert that anything constructs that
destination. `PayloadKind.ROUTED_OUTSIDE_REPLICA` would have contained `entitlement_ack` on
2026-08-09 — the day the applier landed with no caller — and the test would have **passed**. The
set's own KDoc says this in the source, and the test's KDoc says it again, because a set named
"routed" is exactly the kind of name a later reader trusts too far.

So: the guard added this run catches a kind **nobody classified at all**; the guard that catches a
kind **classified but unbuilt** is `EntitlementRoutingApplierTest`'s negative control (**C-S5-3**,
run 58); and the thing that catches a **composition root that does not exist** is the android gate,
which needs the Android SDK (**B-7**) and did not run. B-19 is the third of those and is unmoved.

**Smallest human unblock — unchanged from run 58, repeated so it is not chased across files.** On
the Windows box with the SDK present: implement `ProStateStore` against DataStore (outside the
replica DB, therefore outside the snapshot wipe), wire

```kotlin
applier = EntitlementRoutingApplier(roomApplier, EntitlementAckApplier(setOf("pro_unlock")), proStateStore)
```

into whatever constructs `SyncPump`, and run the full gate.

### B-7 status 2026-08-18 (sixtieth run) — unchanged, and it bounded exactly one claim

`:app` still cannot be built here. It bounded the `:app` half of this run's work to nothing: the
classification sets are `:core`, and the `when` they mirror is `:app` and was **read, not compiled**.
That mirroring is labelled as mirroring in both the source and the test rather than presented as a
check. **Four of the android gate's five tasks did not run and no result is claimed for them.**

### B-2, B-4, B-5, B-9, B-12, B-13, B-14, B-16, B-18 — untouched this run

None was worked, none moved, and none is re-derived here. **B-18** in particular: the prompt
assigned the built S5 spec half for the **twenty-fifth** time (**C-STOP-8**). Its premise is stale
in the same three ways runs 48–59 measured, and this run escalated the one item that had **never**
reached Brandon — B-19's product consequence — rather than re-sending the standing banner.

---

## No new blocker arose 2026-08-19 (vector-corpus coverage guard, sixty-first cloud iteration)

This run wrote `:core` Kotlin and hit nothing it could not finish. Recorded explicitly, because a
run that files nothing should say so rather than leave the reader wondering whether it forgot.

### B-18 status 2026-08-19 (sixty-first run) — the twenty-sixth firing, and the first to cost nothing

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half, built since
2026-08-09 as draft PR **#32** and re-verified this run rather than assumed (**C-STOP-9**). Its pin
`679a317` is still stale (**`7328a0b`**), its "S5 is NOT STARTED" is still wrong, and **nothing has
been merged, closed or undrafted in either repo** — engine `main` still `aac05f3`, android `main`
still `ebfaf81`, 18 and 6 PRs still open and still draft (**C-RET-8**).

**What is new is a smaller claim than run 58's, and it is worth separating.** Run 58's lesson was
*verify the constraints, not just the state* — it disbelieved the prompt's "you cannot compile
Kotlin" and found a silent product defect behind it. This run applied that lesson **as method
rather than as discovery**: the prohibition was re-derived (it is still half-false, `:core` still
compiles here), and the run then went looking for the *next* instance of run 58's defect class
instead of the next item on the prompt's list. That is what produced **C-VEC-3**.

**So the twenty-sixth firing produced product value, and that is not evidence the loop is fine.**
It is evidence that the *records* are now good enough to redirect a mis-aimed prompt in one pass —
which is a property of `RETURN-DAY.md`, `STATE.md`'s banner and this file, not of the routine. The
routine is still firing a stale slice at a program whose stop condition was met at run 47.

**Smallest human unblock — unchanged.** Turn the routine off, **or** replace its "YOUR SLICE THIS
ITERATION" section with: *read `RETURN-DAY.md` §5, and re-derive what this environment can run
before assuming what it cannot — `:core` compiles and tests here (`scripts/core-probe.sh`); `:app`,
.NET and the emulator do not.* The real bottleneck is unchanged and is a merge decision (**#53**)
plus a Windows gate, not authoring capacity. **B-18 cannot be closed by any agent** — the obstacle
is scheduler configuration.

### B-7 status 2026-08-19 (sixty-first run) — unchanged, and one new fact about the host

`:app` still cannot be built here, and **four of the android gate's five tasks did not run; no
result is claimed for them.** One detail is new and is recorded so the next container does not
rediscover it: **this image ships JDK 21 only**, while `:core` pins `jvmToolchain(17)`. Toolchain
auto-provisioning is denied by the same egress policy (`api.foojay.io`), so `scripts/core-probe.sh`
fails with its own diagnostic until `apt-get install -y --no-install-recommends
openjdk-17-jdk-headless` has run. That install is in **C-VEC-1**'s command block. It is a
one-command setup step inside a disposable container, **not** a new blocker.

### B-19 status 2026-08-19 (sixty-first run) — unchanged, and deliberately not narrowed

**Still open, still exactly as run 58 scoped it.** The three `:app` pieces — a `ProStateStore`
implementation, the configured `knownProductIds` set, and the composition root that constructs
`EntitlementRoutingApplier` — are untouched. **No `:app` file was written this run.**

**Read this run's guard's bounds before mistaking it for progress here.**
`VectorCorpusCoverageTest` asserts that nothing in the vendored corpus goes unread. It does **not**
assert that any consumer checks something useful, and it has **no bearing on whether a production
caller exists** — the same limit `PayloadKindCoverageTest` carries. B-19 is a composition-root
blocker and only the android gate can see it.

### B-16 status 2026-08-19 — untouched, and explicitly not what this run's guard addresses

Worth stating because the two are easy to confuse. **B-16 is about the pin being stale** — the phone
falling behind upstream `main`, with no check firing. **This run's guard is about the corpus being
unread** — vectors the phone *has* that nothing exercises. They are orthogonal: the guard passes on
a corpus that is a year out of date, and B-16 would fire on a corpus every test consumes. **H3 is
still open and still Brandon's**, and nothing here chooses among its three options.

### B-1, B-2, B-4, B-5, B-8, B-9, B-12, B-13, B-14, B-17 — untouched this run

None was worked, none moved, and none is re-derived here.

### B-18 status 2026-08-19 (run 62) — attempt 6, and the only new fact is the date

**Symptom, unchanged.** The stored scheduler prompt assigns S5's spec half, which has been built
since 2026-08-09. Run 62 is the **twenty-seventh** firing. The prompt is scheduler configuration,
not a file in either checkout, so **no session can fix this from inside the repos**.

**Attempts.** 1–4 were a refusal, a mission-doc banner, a `STATE.md` banner and a
notification-shaped request; **attempt 5 (run 57) sent the escalation to Brandon by push
notification**, naming the merge decision as the bottleneck. Attempt 6 is this entry.

**What is new, and it is only this: return day (2026-08-18) has passed** and **no H1–H8 item has
been acted on** (**C-RET-9**) — `main` unmoved since 2026-08-12 in both repos, 18 + 6 PRs still
open and draft.

**No notification was sent this run, deliberately.** Run 57 already escalated this exact standing
state and run 59 escalated **B-19**; nothing has changed since, and re-sending the same banner on
every firing is the fatigue that would make the next real escalation ignorable. **Silence here is a
decision, not an omission.**

**Smallest human unblock.** Edit or disable the scheduled prompt. Failing that, act on **H1** and
**H2** — the queue that every remaining rung is waiting behind.

### The `816` prediction is NOT a new blocker — it is H2, already queued

Recorded here only so nobody opens one for it. Run 62 derived `$ExpectedOfflineTotal = **816**`
(SyncHarness **335**) for the post-landing tree and **could not measure it**: `Verify-Alpha.ps1`
needs Windows + .NET and this is Linux (**B-7**'s engine-side twin). That is **not** a blocker —
it is exactly what **H2** exists to do, and the drift trap makes a wrong prediction a **hard
failure on the number**, never silent drift. **If the gate reports something other than 816, the
gate is right**; write what it measured and sweep every count-reporting doc in the same commit.

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-14, B-16, B-17, B-19 — untouched this run

None was worked, none moved, and none is re-derived here. **B-19 in particular is unmoved: no
`:app` file was written this run.**

---

### B-18 status 2026-08-19 (run 63) — a new attempt: the schedule located, from the one angle a session has

**The twenty-eighth firing.** Unchanged in substance: the prompt still assigns S5's spec half, still
names the stale pin `679a317`, still calls S5 "NOT STARTED". All three are false against the repo
(**C-STOP-11**), and the banner did its job again — the built-already conclusion came from the
**first** document read, not the fifth derivation.

**What is new.** Attempt 2 recorded *"fix the prompt — not possible from here; it is stored scheduler
configuration"* as an **inference**. This run had session cron tooling and checked it:
**`CronList` → `No scheduled jobs.`** (**C-CRON-1**). So the recurring prompt is **not** a
session-level cron job, and nothing in this session enumerates or edits it. It is account- or
environment-level scheduled-task configuration, reachable only from the surface Brandon created it
from — not from either checkout, and not from any tool a firing session holds.

**This does not unblock B-18.** It converts "somewhere in the configuration" into a bounded negative:
**not in either repo, and not in this session's job list.** The blocker stands exactly as written.

**Smallest human unblock — unchanged, and now precisely located.** Retire or repoint the scheduled
task from the Claude Code surface it was created on (the web/app scheduled-tasks configuration for
this account). Mission §7's terminal instruction is *"clear the goal"*; the stop condition was
crossed at run 45 and executed at run 47 as `RETURN-DAY.md`. **If the routine is meant to keep
running**, replace its "YOUR SLICE THIS ITERATION" section with: *read `RETURN-DAY.md` §5 and pick
from the human queue, or verify and stop.* Everything the ladder has left needs a Windows gate, an
emulator (**B-4**), a relay deploy, or a decision (**H1**, **H3**, **H8**) — none of which a cloud
session can supply.

**Cost to date.** Twenty-eight firings on a completed slice. The per-run cost is now small by design
(attempts 3–5's banners), but a cheap wrong assignment is still a wrong assignment, and **only
Brandon can stop it.**

---

### B-18 status 2026-08-19 (run 64) — attempt 7, and the first firing that produced an executed gate

**The twenty-ninth firing.** Substance unchanged: the stored prompt still assigns S5's spec half
(built since 2026-08-09), still names the stale pin `679a317` (it is **`7328a0b`**), and still calls
S5 "NOT STARTED" (it is PARTIAL; the emitter landed). All three are false against the repo
(**C-64-1**), and the `STATE.md` banner did its job for the fourth run running — the built-already
conclusion came from the **first** document read.

**What is new, and it is genuinely small.** Nothing about the blocker moved. Two facts were added:

1. **The gate that *is* reachable here was executed**, not merely described: `:core:test` via
   `scripts/core-probe.sh`, **308 / 0 / 0 across 22 classes**, reproducing run 61's recorded
   expectation on a container built today (**C-64-4**). Twenty-eight prior firings recorded that
   number; this one re-earned it. That makes a future *red* `:core` detectable by this routine,
   which is the only standing value the loop still has while H1/H2 wait.
2. **A recorded command has rotted and is corrected** (**C-64-5**): the documented one-command JDK-17
   install now 404s, because the image's apt index went stale (17.0.18 → 17.0.19). `apt-get update`
   first, then install. Without that fix the next container would read `:core` as unreachable and
   silently lose fact 1.

**Neither narrows the unblock.** B-18 is scheduler configuration; run 63 bounded it further with
`CronList` → `No scheduled jobs.`, so it is **not** in either repo and **not** in this session's job
list. Attempts 1–4 were a refusal and three banners; 5 and 6 were push notifications (runs 57, 59/60,
2026-08-18). **Attempt 7 sends nothing**, deliberately: runs 61–63 declined for the same reason and
were right. Everything this run measured is green and unchanged, and a fourth escalation inside two
days is the fatigue that would make a real one ignorable.

**Smallest human unblock — unchanged.** Retire or repoint the scheduled task from the Claude Code
surface it was created on. **If the routine is meant to keep running**, replace its "YOUR SLICE THIS
ITERATION" section with: *read `RETURN-DAY.md` §5; then run `scripts/core-probe.sh` and report it,
and stop.* That is an honest standing job for a cloud session — a real regression check on the one
module this environment can build — instead of a rebuild order for finished work. **The bottleneck
remains H1 (decide #53) and H2 (run the Windows gate, land §3's six merges), and neither is authoring
capacity.**

**Cost to date.** Twenty-nine firings on a completed slice. **Only Brandon can stop it.**

### B-1, B-2, B-4, B-5, B-6, B-7, B-8, B-9, B-12, B-13, B-14, B-16, B-17, B-19 — untouched this run

None was worked, none moved, and none is re-derived here. **B-19 in particular is unmoved: no `:app`
file was written this run.** **B-7** was not re-measured — run 63 did that hours earlier; this run
only confirmed `:core` still builds under it, which is B-7's known-reachable side, not a change to
its scope.

---

## Run 65 — 2026-08-19. B-18 attempt 8, and a new narrow one (B-20)

### B-18 — the schedule keeps assigning a slice finished on 2026-08-09

**Attempt 8. Status: unchanged, and the cost is now thirty firings.** This run declined the
assignment for the thirtieth consecutive time (**C-65-1**) and spent itself elsewhere. Nothing about
the unblock has moved: run 63 bounded it with `CronList` → `No scheduled jobs.`, so it is **not** in
either repo and **not** in this session's job list — it is account-level scheduled-task
configuration, which no tool available here can read or edit.

**Attempt 8 DID send a push notification, and the reason is a change in the facts, not in the
judgement.** Attempts 5 and 6 (runs 57, 59/60) escalated; attempt 7 (runs 61–64) deliberately sent
nothing, because everything measured was green and unchanged and a fourth "everything waits on you"
inside two days is the fatigue that makes a real signal ignorable. **That reasoning still stands and
this run did not re-send the standing banner.** What it sent instead is a **defect discovered this
run** — a relay 400 retried forever and shown to the user as "waiting for network", reachable by
version skew with no bug on the phone — which had never reached Brandon in any prior message.

**Smallest human unblock — unchanged, and now with one line more evidence.** Retire or repoint the
scheduled task. **If the routine is meant to keep running**, replace its "YOUR SLICE THIS ITERATION"
section with: *read `RETURN-DAY.md` §5; then run `scripts/core-probe.sh` and report it, and stop.*
Run 65 is the argument for that wording: the one genuinely useful thing a cloud session found this
week came from **reading the open-question ledger and testing its stated constraints**, not from the
assignment. **The bottleneck remains H1 (decide #53) and H2 (run the Windows gate, land §3's six
merges). Authoring capacity has never been the bottleneck.**

### B-20 — PQ-PSH-1's fix is `:core`-verified and gate-unverified (NEW, narrow)

**Symptom.** Run 65's change (`6bddded`) is proven by `:core:test` — **312 tests, 0 failed**, four
mutations each red — but **`:core:test` is one of the android gate's five tasks**. The change touches
`RelayClient.kt`, `OutboundQueue.kt`, `SyncPump.kt` and `PairingFlow.kt`, all `:core`; **nothing in
`:app` was written**. Still, no cloud session can run `:app:assembleDebug`, `:app:lintDebug`,
`:app:test` or `checkCoreIsAndroidFree` locally.

**Attempts.** None beyond `:core`, and deliberately so — this is **B-7**, measured and unchanged
(`dl.google.com` → **403 at the proxy**, no `sdkmanager`, no `ANDROID_HOME`). B-20 is filed **not**
as a new obstacle but so that a reader of PR #6 cannot mistake `312/0` for a gate result.

**Why it is narrow.** `:app` has no reference to `RelayResult` (`grep -rn RelayResult app/src` →
nothing), so the exhaustive-`when` breakages that a new sealed case causes are **confined to the four
`:core` files already fixed**. The residual risk is lint, not compilation.

**Smallest human unblock.** Read CI on PR #6 — the runner **does** execute the whole gate (run 57
recorded a green one, job `95605131416`) — or run the command of record locally:
`./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug --rerun-tasks`.
**Closes itself** on the first green CI run for this commit; no decision is owed.

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-14, B-16, B-17, B-19 — untouched this run

None was worked, none moved, none re-derived. **B-19 in particular is unmoved: no `:app` file was
written.** **B-7** was not re-measured — run 63 did that; this run only used its known-reachable
side, which is not a change to its scope.

---

## Run 66 — 2026-08-19. B-18 attempt 9, and a new narrow one (B-21)

### B-18 — the schedule assigned a slice finished on 2026-08-09, for the thirty-first time

**Unchanged as a blocker.** Re-verified rather than assumed (**C-66-1**): all three slice commits
exist in the **engine** repo and none is on `main` (`exit=1` each). The prompt's pin `679a317` is
still stale (**`7328a0b`**), and its "S5 is NOT STARTED" is still wrong. **Nothing has been merged,
closed or undrafted in either repo** — engine `main` still `aac05f3`, android `main` still
`ebfaf81`; **18 + 6 PRs open and draft** (**C-66-7**).

**Attempt 9, and it is a small correction to this entry's own guidance.** Run 58 established
*verify the constraints, not just the summary*. This run found a **third** thing the prompt is
wrong about, and it costs a session real time rather than real work: the prompt says *"add the
matching vector via `docs/sync-vectors/generate.mjs`"* as though that file were in the android
tree. **It is not** — the generator, the vector corpus and the three slice commits all live in
`careerseeker`. Looking for them here does not report "absent", it reports **`exit=128`** from
`git merge-base`, which reads like a broken command rather than a wrong repository. Recorded in
**C-66-1** with the distinction spelled out.

**Smallest human unblock — unchanged.** Turn the routine off, **or** replace its "YOUR SLICE THIS
ITERATION" section with: *read `RETURN-DAY.md` §5; re-derive what this environment can run before
assuming what it cannot (`:core` compiles and tests here via `scripts/core-probe.sh`; `:app`, .NET
and the emulator do not); and note that the sync spec, generator and vectors live in the **engine**
repo.* The real bottleneck is unchanged: a merge decision (**#53**) plus a Windows gate, not
authoring capacity.

---

### B-21 — Maven Central rate-limits this sandbox, and the first 429 looks like B-7 (NEW, narrow)

**Symptom.** `scripts/core-probe.sh` failed four consecutive times with

```
Could not GET 'https://repo.maven.apache.org/maven2/...'. Received status code 429 from server: Too Many Requests
```

naming a **different artifact each time** — `ktor-client-core`, then
`kotlin-scripting-compiler-impl-embeddable`, then `kotlin-test`, then `junit-platform-commons`.

**Why it is worth a number rather than a shrug.** It is **not B-7**, and mistaking it for B-7 is the
expensive outcome. B-7 is an egress **policy denial** of `dl.google.com` — permanent within a
container, and correctly read as "`:app` is unreachable here". A **429 from `repo1.maven.org`** is
an **allowed** host applying a rate limit: transient, self-clearing, and it says nothing about what
this environment can build. A session that reads the first one as B-7 will file **`:core` as
unreachable** and skip the one lane that actually executes here — which is precisely the class of
error run 58 and run 65 each paid for once.

**Attempts.** Retry with linear backoff, up to six. **Attempt 4 succeeded.** The advancing artifact
name is the tell: each attempt populates more of `~/.gradle/caches/modules-2` before being cut off,
so the failures march forward through the dependency graph rather than repeating. Once warm, every
later run in the same container resolved without incident (baseline, negative control, fixed run
and four mutations all ran clean afterwards).

**Smallest human unblock.** None needed — it self-clears. **This entry exists to stop the
misdiagnosis, not to request an action.** If it ever stops clearing, the fix is a Maven mirror or a
pre-warmed Gradle cache in the container image; neither is worth doing on today's evidence.

**Status:** not blocking. Recorded so the next container does not re-derive it as B-7.

---

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-14, B-16, B-17, B-19, B-20 — untouched this run

Not re-derived and not re-stated. **B-19 in particular is unmoved: no `:app` file was written**, and
the fix in this run is `:core`-only, so it changes nothing about whether a production caller exists.

---

## Sixty-seventh run (2026-08-20) — no new blocker, and two deliberate non-blockers

Nothing blocked this run's slice. Two real findings were located in `:core` and **deliberately
left unfixed**: a slice is one coherent change, and neither belongs to the cancellation fix. They
are recorded here — with reproduction and an honest severity bound — so the next session does not
have to re-find them, and so nobody mistakes "not fixed" for "not known".

**Neither is a blocker.** Both are fixable in `:core`, which this environment compiles and tests.
They are queued work, not obstructions.

### F-67-1 — `outcome()` interpolates `app_id` into JSON unescaped, while its sibling escapes

**Symptom.** `OutboundEnvelopeFactory` builds the `outcome` body by raw string interpolation:

```kotlin
fun outcome(appId: String, outcome: Outcome, at: String, timestamp: String = at): String =
    build("outcome", """{"app_id":"$appId","outcome":"${outcome.wire}","at":"$at"}""", timestamp)
```

The same class has a `jsonString()` escaper and uses it for **every** field of `entitlement()`.
`OutboundEnvelopesTest:215` exists *specifically* to catch sloppy escaping on that sibling — its
fixture "contains quotes and a backslash precisely to catch sloppy escaping". The `outcome` path
has **no equivalent test and no escaping**. An `app_id` containing `"` or `\` yields a malformed
plaintext body, which the engine would refuse to parse — a mark the user made, sent, accepted by
the relay, and silently never applied.

**Severity, bounded honestly: defense in depth, not a live defect.** Three independent facts hold
it shut today, and all three were checked rather than assumed:
1. `app_id` is an engine-internal ULID — `"app_01H8XK"` in every one of the 29 vectors.
2. The engine's `src/Sync/SyncPayloads.cs` states entity ids are "engine-internal structured
   identifiers, **not untrusted job text**".
3. The snapshot carrying the id is **AEAD-sealed by the engine**, so the blind relay — the party
   §2 says may be hostile — cannot reach or reshape it.

So there is no path from untrusted job/recruiter text (§8.6) to this field today. It is worth
closing because it is three lines, because the twin path already does it, and because fact (1) is
a convention rather than an enforced invariant.

**Attempts.** None — not attempted, by choice. This is not a failed fix.

**Smallest unblock.** None needed; no human input required. Route `appId` (and `at`) through the
existing `jsonString()`, and extend `OutboundEnvelopesTest` with the quote/backslash fixture the
entitlement test already uses.

### F-67-2 — a §6.2 gap is measured across envelopes the phone deliberately did not project

**Symptom.** `PullPolicy.onEnvelope` decides a large gap with

```kotlin
envelopeSeq - positionBefore.highestAppliedSeq > gapThreshold
```

`highestAppliedSeq` advances **only** for `APPLIED` / `APPLIED_SNAPSHOT`. So the measurement
conflates two different things: envelopes the phone **never received** (a genuine §6.2 gap) and
envelopes it **received and deliberately did not project** (`doc`, `conflict`, `entitlement_ack`
→ `IGNORED`). A run of `gapThreshold + 1` consecutive ignored envelopes makes the **next**
projected envelope report a `SEQUENCE_GAP`, and the phone pushes a `pull_request` asking for a
full snapshot that nothing was missing from — traffic on a healthy pairing, which is precisely
what `EntitlementRoutingApplier`'s KDoc says the design is trying to avoid.

That KDoc names this exact hazard **for the ack itself** and closes it by returning `IGNORED`. The
hazard survives for the envelope **after** it, which nothing addresses.

**Severity, bounded honestly: latent, not live.** `src/Sync/SyncPublisher.cs` exposes exactly four
publish methods — `snapshot`, `delta`, `heartbeat`, `evidence` — and the `:app` applier projects
all four. No unprojected kind is published today, so no run of `IGNORED` can occur. It becomes
reachable the moment a `doc` or `conflict` publisher lands, or if S5's `entitlement_ack` emitter
ever emits in volume. The cold-replica cases are **not** affected: `!snapshotSeen` fires
`COLD_START` first, and `AWAITING_SNAPSHOT` has its own branch.

**Existing coverage does not reach it.** `PullPolicyTest`'s *"an unprojected kind asks nothing"*
asserts a **single** `IGNORED` envelope. Nothing exercises a *run* of them followed by an applied
one.

**Attempts.** None — not attempted, by choice. The fix is a design decision about what the policy
should measure (most likely: track the highest seq *handled*, whatever the disposition, and
measure the gap against `max(highestAppliedSeq, lastHandledSeq)`), and it deserves its own slice
with its own mutations rather than being smuggled into a transport-hygiene fix.

**Smallest unblock.** None needed; no human input required. It is phone-side policy, not protocol
— the engine never *sends* `pull_request`, so there is no engine behaviour to match and no vector
moves.

### B-21 status (sixty-seventh run) — unchanged, and one clean run does not close it

`repo1.maven.org` returned **no 429 this run**: the baseline resolved on the **first** attempt,
where run 66 needed four. That is consistent with a **transient** rate limit on an **allowed**
host and does **not** close B-21 — a session that hits it must still retry with backoff rather
than filing `:core` as unreachable. **B-7 is unchanged and still bounds this run**: four of the
android gate's five tasks need the Android SDK and did not run.

---

## Sixty-eighth cloud iteration (2026-08-20) — no new blocker, one finding closed, one deliberately left

### F-67-2 RESOLVED — 2026-08-20 (sixty-eighth run), in `:core`, executed

The finding above is **fixed**. `PullPolicy` now measures §6.2's gap against
`maxOf(positionBefore.highestAppliedSeq, highestHandledSeq)`, where `highestHandledSeq` is the
highest e2p seq the policy has been told about **whatever the replica did with it**
(`core/src/main/kotlin/app/careerseeker/core/PullPolicy.kt`). The applied mark alone could not tell
an envelope the phone **never received** from one it **received and deliberately did not project**;
the new mark can, and the gap is measured against whichever is higher.

**The prediction F-67-2 wrote down was right, and one detail of it was not.** It proposed
"`max(highestAppliedSeq, lastHandledSeq)`" — that is exactly the shape landed. What it did not say
is that the **order** of the two operations inside `onEnvelope` is load-bearing: the handled mark
must advance **after** the decision, never before, or the envelope's own seq folds into the baseline
it is being measured against and **every** gap measures zero. That is the same trap the
`positionBefore` parameter's own KDoc warns about, one field along, and Kotlin flags none of it —
the reordered version compiles and passes nothing. It is pinned by mutation **M2** below.

**Executed, negative control first.** Clean-worktree baseline **`318 tests, 0 failed, 0 skipped,
across 22 classes`**. The four new tests written **before** the fix failed **exactly three** of
themselves — the fourth (`a genuine gap after unprojected envelopes is still detected`) passes
unfixed **by design**: it is a guard against over-fixing, not a control. With the fix: **`BUILD
SUCCESSFUL`**, **`322 tests, 0 failed, 0 skipped, across 22 classes`**. Three mutations, each red:

| | mutation | measured |
| --- | --- | --- |
| **M1** | the baseline reverts to the applied mark alone | the same **3** tests fail; all 318 existing green |
| **M2** | the handled mark advances **before** the decision | **compiles**; **7** fail — 6 of them **pre-existing** |
| **M3** | `onOpen` clears the handled mark with the latch | **exactly 1** fails — the reopen test |

**M3 is the narrowness proof**, and M2 is the one worth reading twice: it takes down
`SyncPumpTest` and `EntitlementRoutingApplierTest` as well as `PullPolicyTest`, which is the
measurement that the ordering is not a local detail of one file.

Re-verify: `AUDIT-REQUEST.md` **C-68-4**, **C-68-5**.

### F-67-1 — still OPEN, deliberately, and nothing about it changed

`OutboundEnvelopeFactory.outcome()` still interpolates `app_id` unescaped. **It was not fixed and
is not claimed to be.** It is a different file, a different defect, and a different slice; folding
it in would have made this run two changes wearing one commit. Its severity bound is unchanged
(defense in depth: engine-internal ULID, AEAD-sealed, unreachable by the blind relay), and its
smallest unblock is still *none needed*. Re-verify it is untouched: `git diff f1bdc95..HEAD --
core/src/main/kotlin/app/careerseeker/core/OutboundEnvelopes.kt` returns **nothing** — `f1bdc95` is
run 67's tip, i.e. this run's base. **Not `main`**: the android `main` branch predates the whole
`:core` module, so `main..HEAD` diffs sixty-eight runs of work and answers a different question.

### B-21 status (sixty-eighth run) — REPRODUCED, and the host name in the original entry is narrower than the fact

**429s occurred this run**, so B-21 is not merely still open — it is **re-measured live**. The
clean-worktree baseline needed **three** attempts: attempts 1 and 2 died on `429 Too Many Requests`
and attempt 3 succeeded. **Every later run this session — the negative control, the fix, and all
three mutations — resolved on its first attempt**, so the burst was at the start of the session and
did not recur. That asymmetry is itself the evidence for "transient": nothing about the build
changed between the run that needed three attempts and the five that needed one.

**One correction to the original entry.** It names the host as `repo1.maven.org`. The 429s measured
this run came back from **`repo.maven.apache.org`** — the same Maven Central service under its other
name, which is what the Gradle `mavenCentral()` resolver actually contacts. A session grepping its
log for the literal string `repo1.maven.org` would conclude it had hit something new. It has not:
**same allowed host, same transient rate limit, same remedy** — retry with backoff, do not file
`:core` as unreachable, and do not report a gate result you never got.

Notably, each failed attempt still got **further** than the last (attempt 1 died resolving
`kotlin-gradle-plugin`, attempt 2 resolving `kotlin-daemon-embeddable`), because what does resolve
is cached. That is why backoff works here and why a single retry is not enough.

### B-7 is unchanged and still bounds this run

Only `:core:test` ran, via `scripts/core-probe.sh`. **`:app:assembleDebug`, `:app:lintDebug`,
`:app:test` and `checkCoreIsAndroidFree` did not run and no result is claimed for them.** Two
`No cast needed` warnings persist in `PairingSessionTest.kt:53` and `RelayClientTest.kt:383` —
**pre-existing, in files this run did not touch**; no zero-warning claim is made.

---

## Sixty-ninth cloud iteration (2026-08-20) — one finding closed, one filed, no new blocker

### F-67-1 RESOLVED — 2026-08-20 (sixty-ninth run), in `:core`, executed

`OutboundEnvelopeFactory.outcome()` now routes `appId` and `at` through the same `jsonString()`
escaper its `entitlement()` sibling has always used. Commit **`1ed5e94`**.

**The unblock recorded at filing was "none needed; no human input required" and that held** — this
needed no gate, no emulator, no Windows machine and no decision from Brandon. It was three lines
and four tests.

**Executed, not reasoned.** Clean-worktree baseline **`322 tests, 0 failed, 0 skipped, across 22
classes`**; negative control (tests written first, run against unfixed source) **`326 tests, 3
failed`** — the three new controls, all 322 pre-existing green; with the fix **`326 tests, 0
failed, 0 skipped, across 22 classes`**. Three mutations, each red, **each matching its prediction**
(2 / 1 / 2). Re-verification: **C-69-3** through **C-69-6** in `AUDIT-REQUEST.md`.

**Two facts found while closing it that the original entry did not have.** (1) The malformed-body
case is refused as **`unknown_kind`**, not `decrypt_failed` — deliberate, per `EnvelopeReceiver`'s
KDoc, and matching the engine's `JsonDocument.Parse` classification, so it is **not** a finding
(**C-69-10**). (2) A crafted value that stays *valid* JSON opens a **second `outcome` key**, which
nothing rejects; duplicate-key resolution is parser-dependent, so the phone and the engine can
record **different outcomes for one signed envelope**. That second mode is worse than the "engine
refuses to parse" the original entry predicted, and it is the one the forge test pins.

**The severity bound is unchanged and was not upgraded**: defense in depth, not a live defect.
`app_id` is an engine-internal ULID inside an AEAD-sealed snapshot, unreachable by the blind relay,
with no path from untrusted §8.6 text today.

### F-69-1 — `build()` interpolates three header fields raw, into JSON *and* into the AAD (NEW, not fixed)

**Symptom.** `OutboundEnvelopeFactory.build()` interpolates `pairing`, `keyId` and `timestamp` raw
into the envelope header JSON:

```kotlin
append("""{"v":${Protocol.VERSION},"pairing":"$pairing","dir":"p2e","seq":$seq,""")
append(""""ts":"$timestamp","key_id":"$keyId","nonce":"$nonceB64u",""")
```

and the same three values reach `EnvelopeHeader.aad()`, which builds
`v=$v|pairing=$pairing|dir=$dir|seq=$seq|ts=$ts|key_id=$keyId`. **These are two different failure
modes and that is why this is its own finding, not a second half of F-67-1.** In the JSON, a `"`
malforms the envelope. In the AAD there is no JSON at all: the failure mode is **delimiter
ambiguity** — a `|` or `=` inside a field makes two different header tuples produce the **same**
AAD string. The AAD's KDoc calls field order normative and warns that changing it "silently breaks
every paired device"; it does not say what a field containing a delimiter does. **Escaping is
probably the wrong fix for that half** — the engine builds the identical string in C#, so any
change is a coordinated cross-implementation change to a normative wire input, which is exactly the
class of edit `CLAUDE.md`'s drift trap governs.

**Severity, bounded honestly and narrowed by measurement: defense in depth, not a live defect.**

1. **`pairing` is validated** — `isValidPairingId` (`^p_[A-Za-z0-9_-]{16}$`) is enforced at
   `RelayClient:133` with a `require` on the **send** path, and at `EnvelopeJson:73` and
   `PairingSession:77` inbound. The character class excludes `"`, `\`, `|` and `=`, so this field
   cannot carry any of them off-device. **The factory itself does not call the validator** — the
   protection is real but sits one layer out, which is a fragility, not a hole.
2. **`keyId` and `timestamp` are neither escaped nor validated** anywhere on the outbound path.
   Both are locally sourced today: `keyId` from the pairing exchange, `timestamp` from the phone's
   own clock at call time. Neither is untrusted §8.6 text.

So there is **no reachable exploit today**, exactly as with F-67-1 — and exactly as with F-67-1, the
protection is a convention plus a validator in another class rather than an invariant this
constructor enforces.

**Attempts.** None — not attempted, by choice. This is not a failed fix. Run 68 declined to fold
F-67-1 into an unrelated `:core` commit for the same reason, and that judgement was right.

**Smallest unblock.** None needed for the JSON half; no human input required. Validate `keyId` at
construction (or escape it, with `pairing`, at use) and give `build()` the quote/backslash fixture
`OutboundEnvelopesTest` now has for the body. **The AAD half genuinely is a design question and
should not be taken unilaterally**: it is a normative, cross-implementation wire input, so it wants
a `docs/protocol-questions.md` entry and an engine-side change in the same window — the S5 pattern,
not a `:core` slice.

### B-21 status (sixty-ninth run) — did NOT reproduce, and that does not close it

**No 429 at any point this run.** The clean-worktree baseline and all five subsequent probe runs
(negative control, fix, three mutations) resolved on the **first** attempt, against
`repo.maven.apache.org`. Run 68 needed **three** attempts for its baseline alone.

**B-21 stays OPEN.** One clean session is not evidence that a transient rate limit is gone — it is
evidence of what a transient limit looks like when it is not currently firing, which is the same
conclusion run 67 drew from the same observation before run 68 reproduced it. Closing it on a quiet
run would mean the next session that hits two 429s files it again as something new.

### B-7 is unchanged and still bounds this run

Only `:core:test` ran, via `scripts/core-probe.sh`. **`:app:assembleDebug`, `:app:lintDebug`,
`:app:test` and `checkCoreIsAndroidFree` did not run and no result is claimed for them.** The two
`No cast needed` warnings in `PairingSessionTest.kt:53` and `RelayClientTest.kt:383` are
**pre-existing, in files this run did not touch**; **no zero-warning claim is made.**

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-14, B-16, B-17, B-18, B-19, B-20 — untouched this run

None was acted on, narrowed or re-attempted. **B-18** fired for the **thirty-fourth** time (the
schedule assigning a slice finished on 2026-08-09) and its premise is unchanged: the work exists,
the landing needs a Windows gate. **B-19** is unmoved — **no `:app` file was written this run.**

---

## F-69-1 PARTLY RESOLVED — 2026-08-20 (seventieth run), in `:core`, executed

**The JSON half is closed.** `OutboundEnvelopeFactory.build()` routes `pairing`, `ts` and `key_id`
through the class's own `jsonString()`, and `isValidPairingId` is enforced by the type that puts
the value into the AAD rather than only by `RelayClient` on the way out. Two live cases were
measured, both **valid JSON** carrying only fields §3 knows — so neither the strict parser nor its
unknown-field rejection fires: a crafted `key_id` opens a **`sig` on a deliberately unsigned
`pull_request`**, and a crafted `ts` writes a **second `seq`**, the replay defence itself. Evidence:
**C-70-3** through **C-70-6**; `:core:test` **326 → 334, 0 failed**, five red before the fix, seven
mutations each red.

**The AAD half is NOT closed, and it is not blocked either — it is decided, elsewhere, already.**
The `|` collision this run tested is **PQ-AAD-1 Half 2**, filed 2026-08-12 with the same two-header
construction and **answered** the same week. Its answer places the resolution in §3, constraining
`ts` **and** `key_id` together as one coordinated wire-visible change, and calls that *"a gate for
Brandon"*. F-69-1's entry above proposed *"validate `keyId` at construction"* as the smallest
unblock; **that proposal is wrong and is retracted here**, because PQ-AAD-1 had already ruled it
out in terms:

> Tightening the Kotlin — validating `ts`/`key_id` … would make the phone stricter than an engine
> whose behaviour is unmeasured, the "more correct than the engine" field bug the mission's
> interpretation rule names.

**Only `ts` is guarded, and the asymmetry is deliberate.** `timestamp` is minted by the phone at
call time, so refusing an ambiguous one is a sender-side decision with no second party in it.
**`key_id` is issued by the engine** and §5.3 constrains its charset nowhere, so a refusal would let
an engine-issued key id **brick this phone's send path**. It stays buildable, pinned by
`a key_id carrying the AAD separator is still accepted, deliberately`, and mutation **M7** — which
adds the forbidden guard — turns exactly that test red. **A deferral that fails when broken.**

**What is left, and who it belongs to.** §3 gaining *"`ts` and `key_id` MUST be ASCII and
delimiter-free"*, plus one shared vector, applied to both parsers in one change. That is PQ-AAD-1's
own recommended resolution and it is unchanged by this run. **Nothing is blocked**: no measurement
is missing, no tool is absent. It is a gate, and gates are Brandon's.

**Also unchanged: `EnvelopeHeader.aad()` itself.** Not touched, not reordered, not re-encoded. No
receive path was modified either — `EnvelopeJson`, `EnvelopeReceiver` and `SyncCrypto` are
byte-identical to run 69's tree, so nothing here can make the phone reject an envelope it used to
accept.

### One process finding, and it is the same shape as run 69's

**A fix can be complete, green, and still wrong, and the tests will not tell you.** The first
version of this slice guarded all three header fields, passed 334/0 with seven red mutations, and
was **already ruled out by an answered question in this repository's own
`docs/protocol-questions.md`**. Nothing in the code, the tests or the gate could have caught it —
only reading the ledger before writing, rather than after. The commit was reset and the slice
re-derived. **`BLOCKED.md` and `protocol-questions.md` are inputs to a slice, not just outputs of
one**, and F-69-1's own "smallest unblock" line is proof that a finding can carry a proposal its
neighbouring file already refuted.

### B-21 status (seventieth run) — did NOT reproduce, and it stays open

**No 429 at any point.** The clean baseline and all ten subsequent probe runs resolved on the first
attempt against `repo.maven.apache.org`. **Two quiet runs in a row are not evidence a transient rate
limit is gone** — that is what one looks like when it is not firing — and closing it would mean the
next session to hit two 429s files it again as something new.

### B-7 is unchanged and still bounds this run

Only `:core:test` ran, via `scripts/core-probe.sh`. **`:app:assembleDebug`, `:app:lintDebug`,
`:app:test` and `checkCoreIsAndroidFree` did not run and no result is claimed for them.** The two
`No cast needed` warnings in `PairingSessionTest.kt:53` and `RelayClientTest.kt:383` are
**pre-existing, in files this run did not touch**; **no zero-warning claim is made.**

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12–B-20 — untouched this run

None was acted on, narrowed or re-attempted. **B-18** fired for the **thirty-fifth** time and its
premise is unchanged: the work exists, the landing needs a Windows gate. **B-19** is unmoved — **no
`:app` file was written this run.**

---

## No new blocker arose 2026-08-20 (PQ-A2-6, seventy-second cloud iteration)

**Nothing this run is blocked, and PQ-A2-6 is deliberately NOT filed here.** It needs a
**decision** — whether an absent optional field may be spelled as an explicit JSON `null` — and a
decision with an owner is a gate in `docs/protocol-questions.md`, not a blocker. Calling it
BLOCKED would send the next session hunting for a missing tool or a denied permission, when what
is missing is one sentence in §3 and §4.3.3 that only Brandon can authorise. Everything this run
set out to do, it did.

**What was reachable and was done:** `node docs/sync-vectors/generate.mjs --check` on both
`origin/main` and pin `7328a0b`; the 29/29 corpus diff, twice; `:core:test` four times
(baseline, post-change, and once per mutation); the two-mutation negative control. **All of it
executed here.**

### B-7 status 2026-08-20 (seventy-second run) — reproduced, and one detail is new

Unchanged in substance and it bounded exactly the usual claims: `:app:assembleDebug`,
`:app:lintDebug`, `:app:test` and `checkCoreIsAndroidFree` did not run and are unclaimed; no
zero-warning claim is made; `Verify-Alpha.ps1` did not run and could not (no `pwsh`, no `dotnet`,
Windows gate).

**New detail worth carrying:** this sandbox shipped **JDK 21**, not 17, and `:core` pins
`jvmToolchain(17)`, so `scripts/core-probe.sh` refused to start until
`openjdk-17-jdk-headless` was installed — the same per-session `apt-get` the twentieth run logged.
A JDK being *present* is not the same as the *pinned* JDK being present, and a session that checks
`which java` and stops there will conclude it can run `:core:test` when it cannot.

### B-19, B-21 — untouched this run

**B-19** unmoved: **no `:app` file was written**, so the `ProStateStore` implementation, the
`knownProductIds` set and the composition root are exactly as they were. **B-21** was not
exercised — no repeated Maven fetches beyond the four ordinary probe runs, no 429 observed — and
**stays open**, the same posture runs 67 through 71 reached from the same evidence.

---

## Run 73 — 2026-08-21. No new blocker; B-18 attempt 10, and the first that leaves the repository

**Nothing this run set out to do was blocked.** The slice was a revalidation of `RETURN-DAY.md` §3
against live refs, and every part of it is runnable here: git, `node`, and `repin-vectors.sh
--check`. All of it executed.

### B-18 — the schedule assigned a slice finished on 2026-08-09, for the thirty-eighth time

**Unchanged as a blocker, and its premise re-verified rather than assumed** (**C-73-1**,
**C-73-2**): all three slice commits exist in the **engine** repo, none is on `main`, and the
prompt's pin `679a317` is still stale (**`7328a0b`**).

**What is new is the date, and it is the reason this entry matters more than it did yesterday**
(**C-73-4**). **Return day was 2026-08-18; it is now 2026-08-21.** The last commit by a human in
either repository is **2026-08-12** — six days *before* the return date, nine days ago. **18 engine
drafts + 6 android drafts, none merged, closed or undrafted.** The queue this program has been
filling since run 47 has not been touched, and the schedule has kept firing into it: **26 runs since
the stop condition was met.**

**Attempt 10, and it is the first one that is not a document.** Attempts 1–9 wrote the unblock
sentence into `BLOCKED.md`, `STATE.md` and `RETURN-DAY.md` — files reachable only by someone who
opens this repository, which is the same person the entry is waiting on. Runs 62–64 located the
schedule from the inside but could not reach a person from there. **This session had a notification
channel to Brandon's phone and inbox, and used it** (**C-73-8**), carrying the state, the
re-verified landing plan, step 0 (**decide #53**), the merge order, the re-pin command, and an
explicit recommendation to **pause the schedule**.

**It stays OPEN.** A sent notification is not a read one, and stopping the routine is Brandon's
action. But the failure mode that kept attempts 1–9 inert — *the unblock was written where only the
blocked party would find it* — no longer applies.

**Smallest human unblock — unchanged, and now also delivered out of band.** Turn the routine off,
**or** replace its "YOUR SLICE THIS ITERATION" section with: *read `RETURN-DAY.md` §5; re-derive
what this environment can run before assuming what it cannot (`:core` builds and tests here via
`scripts/core-probe.sh` once a JDK 17 is installed; `:app`, .NET and the emulator do not); and note
that the sync spec, generator and vectors live in the **engine** repo.* The real bottleneck is
unchanged and is **not authoring capacity**: one merge decision (**#53**) plus a Windows gate.

### B-7 status 2026-08-21 (seventy-third run) — reproduced, and it bounded this run's claims

Unchanged: `:app:assembleDebug`, `:app:lintDebug`, `:app:test` and `checkCoreIsAndroidFree` **did
not run and are unclaimed**; **no zero-warning claim is made**; `Verify-Alpha.ps1` **did not run and
could not** (no `pwsh`, no `dotnet`, Windows gate). **New this run:** the same bound applies to the
merge replay — **C-73-6** proves merge *topology* only. That the six merges produce a tree which
**builds** or **passes the gate** is **unproven**, and the `--theirs` resolutions used to continue
the replay are a replay mechanism, **not** a recommended resolution.

**Also unclaimed this run: `:core:test`.** It was **not run** — this slice touched no Kotlin. Run
72's **336/0** stands as run 72's measurement and is **not** re-asserted here as if re-measured.

### B-14, B-16 — re-measured, both still open, and both now have a fresher number

**B-14** (the phone never asserts `pairing-high-bit-confirm.json`) and **B-16** (android CI stays
green straight through an un-vendored upstream vector) both turn on the post-landing corpus being
**30** where the phone vendors **29**. That gap was re-measured this run against a real replay of
the six merges (**C-73-7**): **`+ pairing-high-bit-confirm.json`, `~ index.json`, `exit=1`** — the
exact output `RETURN-DAY.md` predicts. **Neither is closed, and neither can be from here**: both
close when the merges land and the re-pin runs, which needs the Windows gate first.

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-15, B-17, B-19, B-20, B-21 — untouched this run

None was acted on, narrowed or re-attempted. **B-19** is unmoved — **no `:app` file was written**.
**B-21** was **not exercised**: this run fetched no Maven artefact at all (no Gradle invocation),
so it stays open on runs 67–72's evidence, not on new evidence.

---

## Run 74 — 2026-08-21. No new blocker; one new question, and B-18's thirty-ninth firing

This run wrote `:core` Kotlin and hit nothing it could not finish. Recorded explicitly, because a
run that files nothing should say so rather than leave the reader wondering whether it forgot.

**What it filed is a question, not a blocker, and the distinction is deliberate.** **PQ-ERR-1** —
the phone accepts an engine→phone `error` and drops it, because `:app`'s applier `when` covers four
kinds and returns `Ignored` for the rest — is **not blocked**. Nothing prevents it being answered;
it needs a *decision* about what the user should see, and the three defensible answers differ in
exactly that. Filing it as BLOCKED would send the next session hunting for an obstacle that does not
exist. Its `:app` half **would** be blocked by **B-7** once the decision is made, and PQ-ERR-1 says
so in the entry rather than here.

### B-18 — the schedule assigned a slice finished on 2026-08-09, for the thirty-ninth time

**Unchanged as a blocker, and its premise is stale in the same three measured ways** (**C-74-1**,
**C-74-2**): the S5 spec half exists as commits `8575539`, `22b028e`, `7328a0b` on the engine repo's
`claude/s5-*` drafts, all reporting `not on main` (`exit=1`); the prompt's vendored pin `679a317` is
**`7328a0b`**; and "S5 is NOT STARTED" is wrong — its emitter landed too.

**No out-of-band attempt was made this run, and that is a decision rather than an omission.**
Attempt 10 (run 73) pushed a notification to Brandon's phone and inbox **the same day**, carrying
the state, the re-verified landing plan, step 0, the merge order, the re-pin command, and the
recommendation to pause the schedule. **Nothing has changed since**: `main` is still `aac05f3`, the
last human commit is still 2026-08-12, and the draft counts are unmoved at 18 + 6 (**C-74-8**). A
second notification carrying the same facts on the same day spends attention and delivers no new
one. **Attempt 11 should carry a new fact or a new channel, not a repeat.**

**Smallest human unblock — unchanged since run 55:** turn the routine off, or replace its slice
section. The one-command check that the assigned slice is already built is **C-STOP-1**.

### B-19 status 2026-08-21 (run 74) — unchanged, and this run's finding is NOT progress on it

**Still open, still exactly as run 58 scoped it.** The three `:app` pieces — a `ProStateStore`
implementation, the configured `knownProductIds` set, and the composition root that constructs
`EntitlementRoutingApplier` — are untouched. **No `:app` file was written this run.**

**Read the boundary before reading the `error` work as movement here.** Widening
`ENGINE_TO_PHONE_KINDS` and adding a fourth classification set makes the guard **see** a kind it was
blind to. It still cannot prove that **anything constructs** a destination — that is the warning
`ROUTED_OUTSIDE_REPLICA`'s own KDoc carries, and it now applies to a second set. The thing that
catches a composition root that does not exist is the android gate, which needs the Android SDK
(**B-7**) and did not run.

### B-7 status 2026-08-21 (run 74) — reproduced, and it bounded exactly two claims

`:app` still cannot be built here; `scripts/core-probe.sh` reaches `:core` only. It bounded (1) the
drop half of **C-74-7** — the `when` that returns `Ignored` for `error` was **read, not compiled**,
and is labelled as read in the source and the test — and (2) any claim about PQ-ERR-1's eventual
`:app` surface. **Four of the android gate's five tasks did not run and no result is claimed for
them.** JDK 17 was installed from apt this run, as runs 56 onward have done; the image ships 21 and
`:core` pins `jvmToolchain(17)`.

### B-21 — not exercised this run, and stays open

No Maven 429 was seen across four `core-probe.sh` runs (baseline, control, three mutations, final).
**One clean session does not close it** — same posture as runs 67–73.

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-14, B-15, B-16, B-17, B-20 — untouched this run

None was worked, none moved, and none is re-derived here.

---

## No new blocker arose 2026-08-21 (§7.2 vocabulary drift, seventy-fifth cloud iteration)

This run wrote `:core` Kotlin and hit nothing it could not finish. Recorded explicitly, because a
run that files nothing should say so rather than leave the reader wondering whether it forgot.

**One near-miss is worth naming, because it is a blocker that did not need to be filed.** The sweep
that found this run's defect began on a third suspect — `PayloadKindCoverageTest`'s `mapNotNull`,
which launders a typo in the set governing §5.4's device-signature requirement. That reads like a
security hole worth a blocker entry. **Three mutations showed it is not one** (**C-75-6**): the
behavioural tests catch every direction. It was withdrawn before it was written down anywhere but
here. The precedent is run 56's withdrawn `entitlement`/`entitlement_ack` claim — *grep instead of
assuming, and narrow the finding before publishing it, not after*.

### B-18 status 2026-08-21 (seventy-fifth run) — the fortieth firing, and the twelfth attempt is silence

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half, which has existed
since **2026-08-09** and is re-verified rather than assumed (**C-75-2**). The prompt's pin
`679a317` is still stale (**`7328a0b`**, **C-75-3**), its "S5 is NOT STARTED" is still wrong, and
its *"you cannot compile the Kotlin applier"* is still half-false — this run compiled and tested
`:core` four times over. **Nothing has been merged, closed or undrafted in either repo**: engine
`main` still `aac05f3`, last non-Claude commit **2026-08-12**; **18 engine + 6 android drafts**,
all open, all draft. **Return day was 2026-08-18 — four days past.**

**Attempt 12 is deliberately to send nothing.** Run 73 pushed the escalation to Brandon's phone and
inbox (attempt 10) carrying the state, the re-verified landing plan, step 0 and the re-pin command.
Run 74 declined to repeat it the same day. **This run declines again, and the reason is now a
measurement rather than a courtesy:** nothing in the world state has changed since attempt 10 —
same `main`, same 24 drafts, same human queue, same four-day-old return date — so a second
notification would carry **no new fact** and would spend attention that the first one still needs.
A notification whose content is identical to one already sent trains its recipient to ignore the
channel, which would damage the one escalation path this program has that does not require someone
to open a 950 KB `LOG.md`.

**This is a judgement, not a rule, and it inverts the moment a fact changes.** If a later run
measures **movement in the blocking state** — a merge, a close, an undraft, a human commit in either
repo — or finds something that **needs an action before return day**, that is a new fact and it
should go out immediately. **B-18 stays open**: whether the routine stops is Brandon's action, and a
sent notification is still not a read one.

> **AMENDED LATER THE SAME RUN, because the first wording did not survive its own test.** This
> paragraph originally said the trigger included *"a genuine new blocker"*. Then this run filed one
> — **B-22** — and did **not** send, which would have left a written rule and the run's own conduct
> disagreeing: the exact defect class this run spent its slice on, one document over. The criterion
> is corrected to what was actually applied and is defensible: **notify on movement, or on something
> needing action before return day.** B-22 is neither. It is a finding about how to *read* the
> evidence base, it requires nothing of Brandon today, and it sits in `STATE.md`'s open-blocker
> table and this file — both of which must be read before the landing plan is acted on anyway.
> **Notifying a third time in one day, about something needing no action, would spend the attention
> that attempt 10's still-unanswered escalation needs.** If B-22 had shown the gate producing false
> **greens** rather than false reds, that would need an action before return day and the call would
> invert.

### B-19 status 2026-08-21 (seventy-fifth run) — unchanged, and this run is not progress on it

**Still open, still exactly as run 58 scoped it.** The three `:app` pieces — a `ProStateStore`
implementation, the configured `knownProductIds` set, and the composition root that constructs
`EntitlementRoutingApplier` — are untouched. **No `:app` file was written this run.**

**Read this run's tests for what they are.** `ProtocolTest`'s two new pins prove a **transcription
is faithful to a document**. That is strictly weaker than proving a **caller exists**, which is the
whole of B-19 and which `:core` structurally cannot check (**B-7**: `:app` needs the Android SDK
this sandbox cannot reach). A vocabulary can be complete, spec-exact and fully mutation-covered on
a phone where nothing ever reads it — which is, precisely, `unimplemented`'s situation today, and
`entitlement_ack`'s for nine days before it.

---

## B-22 — the android gate is nondeterministic in its `:app` half, and every "CI green" in these records is one sample (seventy-fifth run, 2026-08-21)

**Symptom.** CI failed on head `592afa4`, then **passed on the identical commit** with no change to
the tree (**C-75-11**):

| attempt | job | head | result |
| --- | --- | --- | --- |
| 1 | `96726656919` | `592afa4` | **FAILURE** — `ScreensFromFixtureTest > theBannerFollowsIntoTheApplicationDetailOverlay`, `AssertionError at ScreensFromFixtureTest.kt:87`; `35 tests completed, 1 failed, 3 skipped` |
| 2 (`rerun_failed_jobs`) | `96728744410` | **`592afa4` — same commit** | **SUCCESS**, every step, `app-debug.zip` uploaded (12,741,153 bytes) |

**Same tree, red then green.** That alone is proof; the precedent makes it a pattern rather than an
incident. Workflow run **`32119765602`** (run number 177, 2026-08-18, head **`0c4ca8f`**) failed the
**same test class on a different assertion** — `theProvenanceBannerIsShownOnEveryTab`,
`AssertionError at ScreensFromFixtureTest.kt:69`, also `35 tests completed, 1 failed, 3 skipped` —
and `0c4ca8f` is a **records-only commit** which cannot affect `:app` by any causal path. **Two
different assertions failing on two diffs, neither of which touches `:app`, is timing, not
behaviour.** Measured frequency across run numbers 172–201: **2 failures in 24 completed runs (~8%)**,
both in `ScreensFromFixtureTest`, both provenance-banner assertions.

**Why it matters more than a flaky test usually would.** **B-7** means no cloud session can run the
android gate locally, so this program's *entire* body of `:app` evidence — `:app:assembleDebug`,
`:app:lintDebug`, `:app:test`, `checkCoreIsAndroidFree` — is **read out of CI runner logs**. A
nondeterministic gate makes every one of those greens a **single sample**, including run 74's
**C-74-10** and this run's own. It does not make them worthless, and the qualification is scoped:
the vector-drift step, `checkCoreIsAndroidFree`, `:core:test` and lint are deterministic. **It is
`:app:test`'s Compose-UI subset that is not**, so a green there means *"passed this time"*, and
nothing in the records has ever said so.

**Root cause, as far as `:core`-only tooling can establish it.** `ScreensFromFixtureTest` uses the
**deprecated** `createComposeRule()`, whose own compiler warning names the hazard verbatim in every
build log:

> *The v2 APIs use `StandardTestDispatcher` instead of `UnconfinedTestDispatcher` … **Tests relying
> on immediate execution may require explicit synchronization.***

Both failing assertions are `assertIsDisplayed()` called **immediately after** a `performClick()`
that navigates, in a tree whose data arrives through Room `Flow`s seeded in `@Before`. There is no
`waitForIdle`, no `waitUntil`, and no idling resource between the click and the assertion. Under
`UnconfinedTestDispatcher` the recomposition usually lands first; **usually** is the defect.

**Attempts.** One, and it was the diagnosis rather than a fix: the failed job was re-run **once** —
the permitted single re-run — which produced the same-commit green that proves the nondeterminism.
**No further re-run should be spent on this**; the question is settled and a second one would only
re-sample.

**Why this run did not fix it.** The fix is an **`:app`** file, and `:app` needs the Android SDK and
AGP from `dl.google.com`, which this sandbox's egress policy denies (**B-7**); `scripts/core-probe.sh`
reaches `:core` only. Pushing a synchronization change no one has compiled — into the very suite
whose reliability is in question, on a slice that was about `:core` vocabulary — is the thing this
program's standing rule forbids and would also have widened the slice. **The failure is unrelated to
this run's diff** (`:core`-only; `:app` references neither `ErrorCode` nor `RESERVED_FOR_L2`), so the
house rule is to state it with a proposed patch rather than widen the PR. **The head is green.**

**Smallest human unblock.** On the Windows box (or any machine with the SDK), in `:app`:

```kotlin
// ScreensFromFixtureTest.kt — after each navigating performClick(), before the assertion:
compose.onNodeWithText("Applications").performClick()
compose.waitForIdle()                                    // <- the missing synchronization
compose.onNodeWithText("Senior Platform Engineer").performClick()
compose.waitForIdle()
```

and preferably, for the assertions that wait on `Flow`-backed content, the stronger form:

```kotlin
compose.waitUntil(timeoutMillis = 5_000) {
    compose.onAllNodesWithText("Demo data — not a live engine").fetchSemanticsNodes().isNotEmpty()
}
```

Then run the full gate with `--rerun-tasks`, and — because a flake is only proved fixed by
repetition — `./gradlew :app:testDebugUnitTest --rerun-tasks` **twenty times**, expecting 20/20.
Migrating to `androidx.compose.ui.test.junit4.v2.createComposeRule` (what the deprecation warning
asks for) is the larger, better fix and is a separate decision, because the v2 dispatcher **queues**
rather than executing immediately and would likely require synchronization in more places than the
two that have failed so far.

**Do not fix this by skipping, disabling, `@Ignore`-ing or retrying the test.** The provenance banner
is the honest-UI rule — *"Demo data — not a live engine"* on every screen including the detail
overlay — and it is exactly the assertion this program would least like to see quarantined. The bug
is in the test's synchronization, not in what it asserts.

---

## Run 76 — 2026-08-21. No new blocker; B-18's forty-first firing, and the criterion applied as written

### B-18 status 2026-08-21 (seventy-sixth run) — the forty-first firing, and the thirteenth attempt is silence

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half, built since
**2026-08-09** and re-verified rather than assumed (**C-76-2**). The prompt's pin `679a317` is still
stale (**`7328a0b`**, corpus **29/29** byte-identical, **C-76-2**), its "S5 is NOT STARTED" is still
wrong, and its *"you cannot compile the Kotlin applier"* is still half-false — this run compiled and
tested `:core` **twelve** times (one baseline, ten mutations, one post-fix).

**The criterion run 75 wrote was applied to this run without amendment, and it says send nothing.**
It has two triggers, and both were measured rather than assumed (**C-76-1**, **C-76-8**):

| trigger | measured this run | fires? |
| --- | --- | --- |
| **movement in the blocking state** — a merge, close, undraft, or human commit in either repo | engine `main` still **`aac05f3`**, last non-Claude commit **2026-08-12**; **18 engine + 6 android drafts**, all open, all draft; **none** merged, closed or undrafted | **no** |
| **something needing an action before return day** | return day (**2026-08-18**) is **three days past**; this run's finding is a **refuted hypothesis** and a repaired test assertion | **no** |

**So: nothing sent, for the third consecutive run.** Attempt 10 (run 73) reached Brandon's phone and
inbox with the state, the re-verified landing plan, step 0 and the re-pin command, and remains
unanswered. Nothing in the world state has changed since. A fourth message carrying the same facts
would train its recipient to ignore the one escalation path this program has that does not require
opening a ~1 MB `LOG.md`.

**Two things about this run in particular, since a refuted target is exactly where a session might
reach for the notification channel to have something to show.** First, the finding is *negative* —
run 75's suspicion that the phone might derive its HKDF keys from wrong info strings is **wrong**
(**C-76-3**), which is good news and good news needs no interrupt. Second, the one real defect found
(**C-76-5**) was a test assertion comparing a constant against itself: a reader's signal, already
fixed, requiring nothing of anyone. **Neither is movement and neither needs an action before a
return day that has already passed.**

**The criterion still inverts on a new fact**, unchanged: movement in the blocking state, or
something needing action, goes out immediately. **B-18 stays open** — whether the routine stops is
Brandon's action, and a sent notification is still not a read one.

### Nothing else moved

No new blocker was filed this run. **B-22 was re-read and not re-attempted**: its patch needs an
`:app` compile (**B-7**), and it was **not** worked around by skipping or `@Ignore`-ing a test. Its
qualification stands and applies to this run's own head — the diff contains **no `:app` file**, so a
red `ScreensFromFixtureTest` on `f7e6586` is B-22, not this change. **B-4**, **B-5**, **B-19** and
the rest were neither acted on, narrowed nor re-attempted. The dangling-citation guard
(**C-75-13**) is **still not built**; its hazard recurred this run and failed loudly rather than
silently, and every `C-`/`B-` id cited in this run's records and PR body was checked to resolve
before pushing.

---

## Run 77 — 2026-08-21. No new blocker; B-18's forty-second firing, and the criterion re-measured

### B-18 status 2026-08-21 (seventy-seventh run) — the forty-second firing, and the fourteenth attempt is silence

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half, built since
**2026-08-09** and re-verified rather than assumed (**C-77-2**). The prompt's pin `679a317` is still
stale (**`7328a0b`**, corpus **29/29** byte-identical, **C-77-3**), and its "S5 is NOT STARTED" is
still wrong.

**The criterion was re-measured this run, not inherited**, and both triggers read negative
(**C-77-10**):

| trigger | measured this run | fires? |
| --- | --- | --- |
| **movement in the blocking state** — a merge, close, undraft, or human commit in either repo | engine `main` still **`aac05f3`**, last non-Claude commit **2026-08-12**; **18 engine + 6 android drafts**, read live, all open, all draft; **none** merged, closed or undrafted | **no** |
| **something needing an action before return day** | return day (**2026-08-18**) is **three days past**; this run's output is a records-side guard that is **green**, needing nothing from anyone | **no** |

**So: nothing sent, for the fourth consecutive run.** Attempt 10 (run 73) reached Brandon's phone and
inbox with the state, the re-verified landing plan, step 0 and the re-pin command, and remains
unanswered. Nothing in the world state has changed since. **A fifth message carrying the same facts
would train its recipient to ignore the one escalation path this program has that does not require
opening a ~1 MB `LOG.md`** — and this run's finding is the weakest case yet for spending it: a guard
that found **no live defect** in the records it checks.

**The criterion still inverts on a new fact**, unchanged: movement in the blocking state, or
something needing an action, goes out immediately. **B-18 stays open** — whether the routine stops is
Brandon's action, and a sent notification is still not a read one.

### C-75-13 is CLOSED — built, and it is the first successor target to survive measurement and ship

Filed by run 75 as the lane's strongest records-side candidate, left unbuilt by run 76, built this
run as `scripts/check-citations.sh` and wired into CI. **Its predicted difficulty was real but
smaller than predicted** (three false positives across 698 cited ids, **C-77-4**), and **the defect
it actually found was in its own documentation** (**C-77-11**). No entry is filed against it.

### Nothing else moved

No new blocker was filed this run. **B-22 was not re-attempted** — its patch needs an `:app` compile
(**B-7**) — and was **not** worked around by skipping or `@Ignore`-ing a test; no Gradle task ran at
all this iteration, so B-22's nondeterminism was neither observed nor sampled. **B-15's remaining
half was deliberately not taken:** its failure paths need a knowingly-broken vendored corpus pushed
to a runner, and writing a wrong vector byte is the cross-repo drift event the mission forbids
outright. **B-4**, **B-5**, **B-16**, **B-19**, **B-20**, **B-21** were neither acted on, narrowed
nor re-attempted. Every `C-`/`B-` id cited in this run's records and PR body resolves — checked by
this run's own new guard, which is the first iteration where that claim is machine-checked rather
than asserted (**C-77-5**).

### B-18 status 2026-08-22 (seventy-eighth run) — the forty-third firing, and the fifteenth attempt is silence

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half — the §4.3
`entitlement_ack` body, the generated vector, PQ-A2-1/-2/-3. It has been built since **2026-08-09**,
and this run **re-verified it in the spec blob rather than assuming it** (**C-78-2**): the body at
§4.3.3, the ciphertext cap at §3.1, `decrypt_failed` in the §7.2 table, and the
`invalid-unknown-field` vector at `7328a0b`. The prompt's pin `679a317` is still stale
(**`7328a0b`**; corpus **29/29** byte-identical, **C-78-4**), its "S5 is NOT STARTED" is still
wrong, and this run found a **fourth** stale premise: the prompt says B-2 is open *because the
desktop `/pair` page does not exist*, and that page has been **on `main` since 2026-08-13**
(**C-78-6**).

**The criterion was re-measured this run, not inherited** (**C-78-1**), and both triggers read
negative:

| trigger | measured this run | fires? |
| --- | --- | --- |
| **movement in the blocking state** — a merge, close, undraft, or human commit in either repo | engine `main` still **`aac05f3`** (2026-08-12); **18 engine + 6 android** drafts read live, all open, all draft; newest human activity anywhere **2026-08-13** | **no** |
| **something needing an action** | this run built nothing, changed no vector byte and moved no rung; its output is a verification and these records | **no** |

**So: nothing sent, for the fifth consecutive run.** Attempt 10 (run 73) reached Brandon's phone and
inbox carrying the state, the landing plan and step 0, and remains unanswered. **Every fact a
fifteenth message could carry is already in that unanswered one.** Return day being four days past
rather than three is not a new fact; it is the same fact one day older, and runs 74–77 declined on
exactly that reasoning. **The one escalation path this program has that does not require opening a
~1.4 MB `LOG.md` is worth more held than spent restating an unread message.**

**The criterion still inverts on a new fact**, unchanged and stated so the next session can apply it
without re-deriving it: **movement in the blocking state, or something genuinely needing an action,
goes out immediately.** **B-18 stays open** — whether the routine stops is Brandon's action, and a
sent notification is still not a read one.

**One thing this run will not do, and the reason is worth writing down.** The obvious "fix" for a
loop firing on finished work is to stop the loop. This session can enumerate and delete scheduled
tasks. **It did neither, and did not look.** The schedule is the owner's automation; silently
deleting it would destroy the one signal that is still reliably reaching him — that a run happened
at all — and would do it on an agent's judgment, four days into a silence whose cause is unknown.
A stalled routine is not consent to dismantle it.

### B-7 status 2026-08-22 (seventy-eighth run) — reproduced, and it bounded every claim in this run

Measured as an inventory rather than asserted (**C-78-7**): `dotnet` **not found**, `pwsh` **not
found**, `ANDROID_HOME` **unset**; Node **v22.22.2**, OpenJDK **21.0.10**, Gradle present. Both
gates structurally impossible here. **No Gradle task ran, no suite count is claimed, no assertion
total is claimed, and no gate result is claimed** anywhere in run 78. The two engine numbers that
appear in these records (offline **609**, EngineHarness **217 → 228**) are **Brandon's**, from PR
#42's commit body, and are attributed there rather than absorbed.

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-14, B-15, B-16, B-17, B-19, B-20, B-21, B-22 — untouched this run

None was acted on, narrowed, re-attempted or worked around. **B-22 was not fixed and was not worked
around by skipping or `@Ignore`-ing a test** — no `:app` file was touched and no Gradle task ran, so
its nondeterminism was neither observed nor sampled. **B-15's remaining half was again declined:**
proving the citation guard's failure path on a runner means pushing a knowingly-dangling citation,
and proving the vector step's means pushing a knowingly-wrong vector byte — the cross-repo drift
event the mission forbids outright. **B-2 narrowed for the record but not by this run** — C-78-6
records that its `/pair` half landed on 2026-08-13 by Brandon's merge, not by anything done here.

---

## Run 79 — 2026-08-22. One new blocker; B-18's forty-fourth firing, and the sixteenth attempt is silence

## B-23 — the engine's receiver has the same §3.1 boundary gap, and closing it needs a Windows gate (seventy-ninth run, 2026-08-22)

### Symptom

Run 79 found that `:core`'s §3.1 size cap was guarded against **deletion** and against neither
its **unit** nor its **number**: every oversized fixture in the phone suite and in the shared
corpus is `MAX_ENVELOPE_BYTES + 1` **decoded** bytes, which is over the cap in all three
candidate units at once, so no assertion could distinguish the rule §3.1 mandates from the two
it forbids by name. Two mutations proved it — measuring the base64url text, and capping at
`MAX * 3 / 4` — both **green at 343/0** (**C-79-8**, **C-79-9**), with deletion of the check as
the negative control going **red** (**C-79-10**). Fixed on the phone; **346/0/0**, both
mutations now red (**C-79-11**).

**The engine has the identical gap, and it was measured rather than assumed** (**C-79-14**).
`src/Sync/EnvelopeReceiver.cs:45` applies the same correct rule — `ciphertext.Length >
Protocol.MaxEnvelopeBytes` on the output of `Base64Url.TryDecode`, so decoded bytes, matching
the phone. Its guard is `tests/SyncHarness/Program.cs`, which exercises the cap at
`invalid-oversized`'s `synth_ciphertext_len` only (line 224) plus a value pin of `index.json`'s
`max_envelope_bytes` (line 47). That is `MAX + 1` and nothing at `MAX` — the state the phone was
in until this run.

Note what does **not** cover it: §3.1 says *"`relay/test/relay.test.ts` pins the derivation, the
maximum legal envelope surviving a push/pull round trip, and the first character beyond it."*
That is the **relay**, and the relay is not the engine's receiver. The boundary is pinned for the
transport and unpinned for the party that decrypts.

### Why it matters

`MUST NOT exceed` makes exactly 1 MiB the largest **legal** ciphertext. An engine receiver that
drifted to the wrong unit would refuse a payload the protocol declares legal, and the sender
could not discover why — §3.1 records precisely this failure having shipped once already, on the
relay, where a character count compared to a byte budget *"left the top 256 KiB of the declared
range untransmittable"*. §4.4 then instructs a future chunker to size against exactly this
number. The phone is now guarded; the engine is not, and the two are supposed to move together.

### Attempts

1. **Fix it in this session.** Rejected, not attempted. `dotnet` and `pwsh` are absent
   (**C-79-5**, **B-7**), so a C# change could not be compiled, let alone gated.
2. **Push it unverified to an engine branch anyway.** Rejected on the mission's own terms: the
   engine repo's merge condition is a full local `Verify-Alpha.ps1`, and this program's standing
   rule is that a claim needing a gate this environment cannot run is written down as unverified
   rather than pushed as done.
3. **Write the assertion and leave it uncompiled**, as **B-22** does for its `:app` patch.
   Rejected here, and the reason is specific rather than general: adding a `SyncHarness`
   assertion moves the offline total, and `CLAUDE.md` pins that total in
   `$ExpectedOfflineTotal` **and** in every doc that reports it, all of which must change in one
   commit. A written-but-uncompiled patch that is guaranteed to fail the pinned-total check is a
   worse artifact than a precise description of what to write.

### Smallest human unblock

On a Windows machine with the engine checkout, add to `tests/SyncHarness/Program.cs` the twin of
the three cases this run added to `EnvelopeReceiverTest`: a **real sealed** envelope whose
decoded ciphertext is exactly `Protocol.MaxEnvelopeBytes` must be **accepted**; one byte more
must be `too_large`; and the maximum legal ciphertext must encode to **1,398,102** base64url
characters, which is §3.1's own `ceil(4/3 × 1 MiB)` (**C-79-12**). Then bump
`$ExpectedOfflineTotal` by the number of assertions added **and** every doc that reports the
total, in the same commit, and run `scripts\Verify-Alpha.ps1`.

The phone-side change is `f78edaf` on `claude/android-a0-probe` and is a direct template,
including the padding arithmetic (`plaintext = target − TAG_BYTES`, body padded with ASCII so
character count equals byte count).

### B-18 status 2026-08-22 (seventy-ninth run) — the forty-fourth firing, and the sixteenth attempt is silence

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half — §4.3's
`entitlement_ack` body, the generated vector, PQ-A2-1/-2/-3. It has been built since
**2026-08-09**, and this run re-verified it **in the spec blob rather than assuming it**
(**C-79-2**): the body at §4.3.3 lines 318–320, the ciphertext cap at §3.1 lines 111–112,
`decrypt_failed` at §7.2 line 601, and `invalid-unknown-field.json` present at the pin.
`node docs/sync-vectors/generate.mjs --check` → **`OK: 29 vector files match the generator.`,
`EXIT=0`** (**C-79-3**) — the command the prompt asks for by name, passing because the work is
done. The prompt's pin `679a317` is still stale (**`7328a0b`**, corpus **29/29** byte-identical,
**C-79-4**), its "S5 is NOT STARTED" is still wrong, and run 78's fourth stale premise — that
B-2 is open because the desktop `/pair` page does not exist — is unchanged: that page has been
on `main` since 2026-08-13.

**The criterion was re-measured this run, not inherited** (**C-79-1**), and both triggers read
negative:

| trigger | measured this run | fires? |
| --- | --- | --- |
| **movement in the blocking state** — a merge, close, undraft, or human commit in either repo | engine `main` still **`aac05f3`** (2026-08-12); **18 engine + 6 android** drafts read live via the API, all open, all draft; newest merge anywhere **PR #44, 2026-08-13** | **no** |
| **something genuinely needing an action** | this run's output is a `:core` test change that is **already green and already pushed**; the one thing it could not do is filed above as **B-23**, and B-23 waits on the same Windows gate that eight other items already wait on | **no** |

**So: nothing sent, for the sixth consecutive run.** Attempt 10 (run 73) reached Brandon's phone
and inbox carrying the state, the landing plan and step 0, and remains unanswered. **B-23 is a
new finding but not a new *fact about the blocking state*** — it joins a queue that is already
blocked on exactly the action the unanswered message asks for, and a notification saying "there
is now a ninth thing waiting on the Windows gate" tells him nothing the eighth did not.

**The criterion still inverts on a new fact**, unchanged and restated so the next session can
apply it without re-deriving it: **movement in the blocking state, or something genuinely
needing an action, goes out immediately.** **B-18 stays open** — whether the routine stops is
Brandon's action, and a sent notification is still not a read one.

**Unchanged from run 78, and worth keeping written down:** this session can enumerate and delete
scheduled tasks. **It did neither, and did not look.** Silently deleting the owner's automation
would destroy the one signal still reliably reaching him — that a run happened at all — on an
agent's judgment, five days into a silence whose cause is unknown. **A stalled routine is not
consent to dismantle it.**

### B-7 status 2026-08-22 (seventy-ninth run) — reproduced, and it bounded this run's claims more narrowly than usual

Measured as an inventory (**C-79-5**): `dotnet` **not found**, `pwsh` **not found**,
`ANDROID_HOME` **unset**; Node **v22.22.2**, OpenJDK **21.0.10**, Gradle present.

**One thing was different, and it is a cost rather than a change to B-7.** `/usr/lib/jvm` carried
**21 only**, so `scripts/core-probe.sh` exited 1 with its "no JDK 17 found" message; the apt
install the script itself prescribes **succeeded**, and `:core:test` then ran. This is the
recurring provisioning cost the twentieth run first logged, paid again — **not** a narrowing of
B-7, which is about `dl.google.com` and the Android SDK. **Both gates remain structurally
impossible here**, and `:core:test` is reported throughout run 79 as `scripts/core-probe.sh` —
**one of the android gate's five commands** — never as a gate result.

**No android gate result and no engine gate result is claimed anywhere in run 79**, and no
offline assertion total appears in it.

### B-1, B-2, B-4, B-5, B-6, B-8, B-9, B-12, B-13, B-14, B-15, B-16, B-17, B-19, B-20, B-21, B-22 — untouched this run

None was acted on, narrowed, re-attempted or worked around. **B-22 was neither observed nor
sampled** — no `:app` test ran and no `:app` file was touched, so its ~8% nondeterminism was not
exercised — and it was **not** worked around by skipping or `@Ignore`-ing a test. **B-15's
remaining half was again declined**, for the reason it has been declined every run: proving the
vector step's failure path means pushing a knowingly-wrong vector byte, which is the cross-repo
drift event the mission forbids outright.

### B-22 status 2026-08-22 (seventy-ninth run) — it FIRED, third occurrence, and the failing line is now pinned

**Correcting this run's own earlier records.** Run 79's records commit and **C-79-17** both say
B-22 was "neither observed nor sampled" / "did not fire on this head". True of `8264275` and
`c5bcf83`; **false of the final head `73238fc`**, where it fired (**C-79-19**).

**The sample.** Run `32554847042` attempt 1, job `96987312857`, head `73238fc`:
`ScreensFromFixtureTest > theProvenanceBannerIsShownOnEveryTab FAILED`,
`java.lang.AssertionError at ScreensFromFixtureTest.kt:69`, `35 tests completed, 1 failed, 3
skipped`. Steps 1–9 green including **`:core` in 58s**; step 10 red; 11–14 skipped.

**It is B-22 and not this PR's defect, for five measured reasons** (**C-79-19**): the diff that
produced it is **records-only** (two markdown files, zero source — the same shape as the `0c4ca8f`
precedent); the identical `:app` tree **passed twice the same day** (92s, 113s); the class and the
assertion family are the ones B-22 already names; and the build's own `UnconfinedTestDispatcher`
deprecation warning names the mechanism 25 lines above the failure in the same log.

**What is genuinely new, and it is why this entry exists rather than a one-line tally.** B-22
described the hazard as a *pattern* — *"missing any `waitForIdle`/`waitUntil` after a navigating
`performClick()`"*. **This run pins it to a line.** `ScreensFromFixtureTest.kt:68-69`, inside the
tab loop:

```
for (tab in listOf("Applications", "Jobs", "Evidence", "Home")) {
    compose.onNodeWithText(tab).performClick()
    compose.onNodeWithText(label).assertIsDisplayed()
}
```

The click navigates; the assertion runs with nothing between them. **Four tabs, so the loop gives
the hazard four chances per test run** — which is a plausible reason this class dominates the
sample set rather than the failure being spread across the suite.

**And the count now says something the earlier framing did not.** Sample moves from **2-in-24** to
**3**, and **two of the three are records-only commits**. So the nondeterminism is **not
correlated with touching `:app` at all**. That is what makes B-22 a *gate* hazard rather than a
code smell: any commit in this repository, including a pure documentation commit, carries the
risk, and therefore **every "CI green" in these records remains one sample** — the retroactive
qualification B-22 already carried, now with a third data point behind it.

**Attempts this run.**
1. **Re-run the failed job, once.** Executed: `rerun_failed_jobs` on `32554847042` produced
   attempt 2 (job `96987953926`) **on the identical commit with no push between** — the only
   construction that demonstrates nondeterminism, and the one the prior sample used
   (`96726656919` → `96728744410`). **Its outcome was not observed by this session** — the job
   and check-run endpoints served cached `in_progress`, and `get_job_logs` returned **HTTP 404**,
   which is what that endpoint does before a job completes. **No verdict is claimed for attempt
   2**; the next reader should open the run and look.
2. **Fix it.** Not attempted, and correctly so: the fix is an `:app` file and needs the Android
   SDK to compile (**B-7**). B-22 already carries a written-but-uncompiled patch, labelled
   unverified, and adding a second unverified patch would not improve it.
3. **Skip, `@Ignore`, quarantine or in-suite retry.** **Refused**, as B-22 and the mission both
   require. A green obtained by not running the assertion is worth less than a red that is
   understood.

**Smallest human unblock — unchanged, and now cheaper to act on.** On a machine with the Android
SDK, add a `waitForIdle()` (or a `waitUntil` on the banner node) after the `performClick()` at
`ScreensFromFixtureTest.kt:68`, and audit the other provenance-banner test for the same shape.
Then run `./gradlew :app:testDebugUnitTest` repeatedly — B-22's rate means a single green proves
nothing, which is the property that has kept this open.

**B-22 stays OPEN**, and this run neither narrowed nor worked around it.

### B-22 status 2026-08-22 (seventy-ninth run, second entry) — demonstrated on a byte-identical `:app` tree

**The strongest sample this blocker has.** The `:app` module is **the same git tree object** —
`460e581b927cd36845001d9d33e72273d66e376d` — on `73238fc` and on `5170aff`, which differ only by
175 added lines across three markdown files. Step 10 was **`failure`** on the first and
**`success` in 91s** on the second (**C-79-20**).

**That is better evidence than the same-commit re-run it replaces**, and the reason is worth
stating: a re-run repeats a *commit*, whereas an equal tree hash proves the *compiled input was
the same object*. B-22's prior sample was a same-commit re-run (`96726656919` → `96728744410`);
this one shows the same thing at the level of the artifact.

**`:core` passed on all four of this run's heads** — 64s, 57s, 58s, 59s, on four different
runners — while `:app` failed once in four, on a **markdown-only** diff. **Three of the four
heads carried records-only diffs**, which is what makes the point: **the nondeterminism is
independent of what the commit changes.**

**A correction to this run's own previous B-22 entry.** It says attempt 2's outcome *"was not
observed by this session"* and blames API caching. **The real cause was my own push**: attempt 2
was `cancelled` at 05:48:22 with step 10 killed at 05:48:19, and the push of `5170aff` — the
commit recording the B-22 finding — started the replacement run at 05:48:24. **The record of the
experiment destroyed the experiment.**

**Smallest human unblock — unchanged**, and the target is now a specific line:
`ScreensFromFixtureTest.kt:68` needs a `waitForIdle()` (or a `waitUntil` on the banner node)
between the navigating `performClick()` and the assertion on line 69, with the sibling
provenance-banner test audited for the same shape. **B-22 stays OPEN**; this run neither narrowed
nor worked around it, wrote no `:app` file, and skipped, `@Ignore`d and quarantined nothing.

---

## Run 80 — 2026-08-22. B-22 CORRECTED and fixed; B-18's forty-fifth firing

### B-22 DIAGNOSIS CORRECTED and FIX PUSHED — 2026-08-22 (eightieth run)

**The blocker's facts were right. Its cause and its prescribed patch were both wrong, and its
"smallest human unblock" asked for a machine the program already has.**

**What was wrong — three things, each measured.**

1. **The stated cause does not fit the data** (**C-80-5**). B-22 says *"Both failing assertions are
   `assertIsDisplayed()` called **immediately after** a `performClick()` that navigates."*
   `ScreensFromFixtureTest.kt:69` is the **first statement after `setContent`**, with no click
   anywhere before it — and it is the line that failed in **two of the three** recorded occurrences.

2. **The prescribed patch would not have fixed either failure** (**C-80-7**). Compose UI test
   synchronizes with the compose clock automatically before **every** node interaction, so an
   explicit `waitForIdle()` in those positions is the same synchronization called twice. **The tests
   flake in spite of it already being in force**, which is precisely the proof that the
   unsynchronized source lies outside the compose clock.

3. **The unblock did not need a human.** B-22 asked for *"the Windows box (or any machine with the
   SDK)"*. CI **is** such a machine and runs on every push; **B-7**'s own text names CI as `:app`'s
   gate of record. Four runs declined this blocker on the strength of "the fix is an `:app` file",
   which is a statement about compiling, not about verifying.

**The actual cause.** `DashboardApp` reads all five replica queries with `collectAsState`, and each
initial value renders a **different tree** than the one the tests look for (**C-80-4**):
`StatusBanner(null)` prints *"Not paired — no data yet"* rather than the demo label
(`HomeScreen.kt:72`); `ApplicationsScreen` prints *"No applications in the replica yet."* while its
list is empty (`:44`); `ApplicationDetailScreen` returns early while its application is null
(`:42`). The nodes exist only after Room's **query executor** — which Compose's idling registry does
not observe — has delivered a row and a recomposition has landed.

**The partition confirms it** (**C-80-6**): of the class's **8** tests, the **2** that render
`DashboardApp` carry **all three** failures; the **6** that pass a `suspend` `*Now()` read straight
into a screen, complete before `setContent`, have **never** failed.

**The fix** (**C-80-8**), pushed as `30908de`: `awaitText(text)` polls the node's arrival with
`waitUntil(timeoutMillis = 5_000)` at the **six** sites in those two tests that depend on
Room-delivered content — the three that have failed and the three that have not yet. Test-only diff,
one file, **no production file touched**. **No assertion was weakened, skipped, `@Ignore`d,
quarantined or retried**, per this entry's own standing prohibition; every `assertIsDisplayed()`
still runs and still decides.

**Why this stays OPEN rather than CLOSED, and what would close it.** The change removes the race
**by construction**, and that argument does not depend on a sample. But B-22 is a *frequency* claim
(3 in 28 completed runs, ~11%), and **a frequency claim is not refuted by one green run** — that is
the blocker's own central point, and it applies to its fix. **Status: NARROWED — cause corrected,
fix landed and CI-verified once.** The closing evidence is the one B-22 already names and no cloud
session can produce: `./gradlew :app:testDebugUnitTest --rerun-tasks` **twenty times, 20/20**, on a
machine with the SDK. Until then every `:app` green in these records stays one sample.

**Two qualifications on the fix itself.**

- The three androidx calls (`waitUntil`, `onAllNodesWithText`, `fetchSemanticsNodes`) were **not
  resolved locally**. The local check is a **parse** at the repo's pinned Kotlin 2.4.10 — 0 parse
  errors against a 0-parse-error control — and an empty `comm` of unresolved-symbol sets is **not**
  evidence of anything, because cascading diagnostics on an already-unresolved receiver are
  suppressed (**C-80-8**). Their resolution is CI's.
- `waitUntil` changes the failure mode as well as the frequency: a future occurrence fails with
  `ComposeTimeoutException` naming the string it waited for, instead of `AssertionError` on a node
  that "isn't displayed". **If B-22 recurs, the next log will say what it was waiting for.**

**One thing this run could not narrow.** The migration to
`androidx.compose.ui.test.junit4.v2.createComposeRule`, which the build's own deprecation warning
asks for, is untouched and remains the larger separate decision B-22 describes. The v2 dispatcher
**queues** rather than executing immediately, so it would likely require synchronization in more
places than the six fixed here — and `awaitText` is the shape those would take.

### B-18 status 2026-08-22 (eightieth run) — the forty-fifth firing, and the seventeenth attempt is silence

**Unchanged as a blocker.** The scheduled prompt again assigned S5's spec half, built since
**2026-08-09** and re-verified in the blobs rather than assumed (**C-80-3**): `generate.mjs --check`
→ `OK: 29 vector files match the generator.`, `EXIT=0`. The prompt's pin `679a317` is still stale
(**`7328a0b`**, corpus 29/29, `diff -r` exit 0), and its *"S5 is NOT STARTED"* is still wrong.

**Both triggers measured, not carried forward** (**C-80-2**):

| trigger | measured this run | fires? |
| --- | --- | --- |
| **movement in the blocking state** — a merge, close, undraft, or human commit in either repo | engine `main` still **`aac05f3`**; last non-Claude commit **2026-08-12**; **18 engine + 6 android** drafts read live, **all 24 open and draft**; newest merge anywhere **PR #44, 2026-08-13** | **no** |
| **something needing an action before return day** | return day (**2026-08-18**) is **four days** past; this run's output is a corrected diagnosis and a test-only fix, both already in the records a reader must open anyway | **no** |

**So: nothing sent, for the seventh consecutive run.** Attempt 10 (run 73) reached Brandon's phone
and inbox with the state, the landing plan, step 0 and the re-pin command, and remains unanswered.
Nothing in the world state has changed since.

**This run is exactly the case the criterion was amended for, and the amendment holds.** Run 75
narrowed the trigger from *"a genuine new blocker"* to *"movement, or something needing action"*
after filing B-22 and not sending. This run **corrected** B-22 and fixed it — a larger movement in
the *records* than filing it was — and it is still **not movement in the blocking state** and still
requires nothing of Brandon today. It changes what an auditor reads, not what anyone must do. **The
criterion inverts on a new fact, immediately.** **B-18 stays open**: whether the routine stops is
Brandon's action, and a sent notification is still not a read one.

### Nothing else moved

**B-23** was filed by run 79 and is **untouched**: fixing it means a `SyncHarness` assertion, which
moves `$ExpectedOfflineTotal` and every doc reporting it in one commit, and there is no `dotnet` or
`pwsh` here to measure the new total with. **This run added no landing cost to the pin family**
(**B-17**) — `Verify-Alpha.ps1` was not opened. **B-1**, **B-2**, **B-4**, **B-5**, **B-8**, **B-9**,
**B-10**, **B-12**, **B-13**, **B-14**, **B-15**, **B-16**, **B-19** were neither acted on, narrowed
nor re-attempted. **B-7** was reproduced and gained one detail (**C-80-9**): androidx is not on Maven
Central either, so `:app` has no `core-probe.sh` analogue and never will on this network.

### B-18 status 2026-08-22 (eighty-first run) — the forty-sixth firing, and the first one that reached Brandon

**Unchanged as a blocker. One thing about it changed, and it is the only thing that ever could from
here: the message left the repository.**

Forty-five previous runs recorded *"turn the routine off, or repoint it"* into `BLOCKED.md`,
`STATE.md` and `RETURN-DAY.md` — **documents whose whole problem is that the person who can act on
them is not reading them.** Run 48's banner made the firing *cheap*; it could not make it *stop*, and
said so. **This run had a channel those runs did not: a push notification to the owner.** It was sent,
carrying the three commits (`8575539`, `22b028e`, `7328a0b`), the one-command check
(`generate.mjs --check` → `OK: 29 vector files match the generator.`), the stale pin
(`679a317` → `7328a0b`), the 18 open engine drafts with nothing merged since **PR #44, 2026-08-13**,
and the fact that **return day is four days past**.

**Why that is a status change and not a fix.** The unblock has always been an action only Brandon can
take — editing stored scheduler configuration that **nothing in either checkout can reach**
(attempt 2, unchanged). Notifying him does not perform it. **If the routine fires again, this entry
should be read as: the message was delivered, and the schedule still stands.** The next run should
**not** re-derive B-18 from scratch, and should **not** send a second notification for the same fact —
one delivery is information, a daily repeat is noise. **Notify again only on a genuinely new fact**
(the schedule changes, a PR merges, `main` moves, or something breaks).

**The slice itself was declined and verified, per attempt 1's standing judgement** (**C-81-2**):
rebuilding it would produce a duplicate §4.3 amendment competing with `8575539`, and re-running the
generator to "add" existing vectors risks the corpus the android repo vendors at `7328a0b` — which the
prompt's own text classes as a **cross-repo drift event**. **Corpus verified 29/29 byte-identical
before and after this run** (**C-81-3**).

---

### Not blocked, and recorded here so it is not mistaken for a blocker — run 81's finding

**`latest`'s `since`-independence had exactly one guard, on the branch the landing plan closes**
(**C-81-8**). This is **not** a BLOCKED item: it needed no human, no gate and no decision, and it was
**fixed this run** — draft **PR #54**, `claude/s2-latest-since-invariant`, one test file, proven by
mutation (**C-81-10**: unguarded tree **51 GREEN** under the mutation; with the guard, **RED**; clean,
**52 GREEN**).

**What remains of it is a decision that already existed**, not a new one: `RETURN-DAY.md` §3 step 0
asks whether #53 is closed. This run **takes no position on that** and does not need one — the guard
now lives on a branch that survives either answer. **The one thing a future session should not do is
file this as blocked on the #53 decision.** It is not.

**The genuinely unverified part, stated plainly — and then resolved before the run ended.** As
written, this read: *"CI has not run PR #54, and this run claims no CI result for it"*, the evidence
being `npm test` in a Linux sandbox, with the pin-family claim (**B-17**) flagged **"verify that
rather than accept it."**

> **RESOLVED in-run on a `check_suite.completed` wake** (**C-81-14**). Run `32574969239`, head
> `f95b66e`, **attempt 1**, **`conclusion: success`**, no re-run. **Relay job on `ubuntu-latest`:
> `✓ test/relay.test.ts (52 tests)`, `Tests  52 passed (52)`**, typecheck green — the sandbox result
> reproduced **off this machine**. **Offline job on `windows-latest`: `=== Offline total: 598 passed,
> 0 failed ===`** — **598 is the base branch's number, so the branch moves the pin by zero.** The
> B-17 claim is now **measured, not asserted**, which is what the PR's self-audit asked for.
>
> **Still unproven, and not claimed:** the **android** gate (**B-7**, **B-22** both unmoved — this is
> the *engine* repo's CI) and `Verify-Alpha.ps1`'s `-IncludePublish`/`-IncludePackage` passes.
> **The merge condition — a full local gate no cloud session can run — is unchanged.**

### B-18 status 2026-08-22 (eighty-second run) — the forty-seventh firing, and the first that deliberately stayed silent

**No notification was sent this run, and that is the entry.** Run 81 sent attempt 5 — the first
delivery that left the repository — carrying the three commits, the one-command check, the stale pin
`679a317` → `7328a0b`, the unmerged draft census and the fact that return day had passed.
**Recorded in prose only — run 81 gave that delivery no `C-` claim of its own, so there is no
re-verification command for it; see run 82's note in C-82-2.** Its own instruction to the next session was explicit: *"do not re-derive B-18, and do
not re-notify the same fact. One delivery is information; a daily repeat is noise. Notify again only
on a NEW fact."*

**Both triggers were checked and both are negative** (**C-82-2**). The routine fired again today,
**unrepointed**, still assigning S5's spec half; engine `origin/main` is still **`aac05f3`**; the
newest merge anywhere is still **PR #44, 2026-08-13**. **That the routine fired again one day later
is not a new fact** — it is the same fact observed one day on, and a human who received yesterday's
notification may simply not have acted yet. **Re-sending it would train the channel to be ignored,
which is the one way to make attempt 5 worse than useless.**

**So B-18 stays OPEN and its status is unchanged.** The smallest human unblock is still *turn the
routine off or repoint it* — editing stored scheduler config that **nothing in either checkout can
reach**. What changed this run is only the count: forty-seven firings, one delivered.

**The criterion for the next run, stated so it does not have to be re-derived.** Notify on a
**genuinely new** fact — `origin/main` moving, any PR merged or undrafted, the routine's stored prompt
changing, or a gate result that changes the merge condition. **Do not** notify on: another firing of
the same prompt, another draft PR opened by a cloud session (this run opened **#55** and that is
routine progress, not news), or the passage of another day past return day.

---

**No new blocker was filed by run 82, deliberately.** The run's strongest self-criticism —
that `expiredRow()` writes `expires_at = 1` straight into SQLite, so **M1's failure mode is real but
possibly unreachable** if Cloudflare's alarm collects expired rows faster than an engine push can race
them — **is a limit on the evidence, not a blocker.** Nothing human-shaped unblocks it: alarm latency
is unmeasurable in this sandbox and would need a deployed Worker under load to characterise, which is
embargoed for agents (**H5**). It is recorded in **C-82-3** and as the first item of PR #55's
self-audit, where a reviewer will actually meet it. **Filing it here as a blocker would send the next
session hunting a phantom**, which is the failure mode this file exists to prevent.

---

## Run 84 (2026-08-23) — no new blocker, and three deliberate non-blockers

**Nothing new is blocked.** This run's slice was chosen *because* it is executable here, and it
completed: the relay lane needs only `node`, which this image has.

**Deliberate non-blocker 1 — the engine half of run 83's suite-name hole (ordered intent NEW ITEM 1)
is still not takeable here, and it is still NOT a blocker.** `dotnet` and `pwsh` are absent
(**C-84-2**, verified with `which`), so the mutation run 83 specified cannot be executed and a C#
edit that cannot be compiled is what this program's rules forbid. Nothing *human-shaped* is missing
except the gate itself, which is already **H2** — filing it as a `B-` would send the next session
hunting a phantom. It stays in the ordered intent with the exact mutation that proves it.

**Deliberate non-blocker 2 — `core-probe.sh` needs JDK 17 and this image ships 21.** Run 83 hit the
same wall and cleared it with `apt-get update && apt-get install` (its install 404'd against a stale
apt index first). This run did not need the `:core` lane, so the JDK was **not installed** and the
lane was **not opened**. That is a choice, not an obstacle: the next session that wants `:core`
should expect JDK 21 again and budget one `apt-get update` for it.

**Deliberate non-blocker 3 — the retention finding's evidence has a ceiling, and it is stated in
PR #56's self-audit first.** These tests read `DEFAULT_TTL_SECONDS` and `MAX_TTL_SECONDS` as
constants; **nothing here demonstrates that Cloudflare's alarm actually purges on schedule.** Alarm
latency is unmeasurable in this sandbox and would need a deployed Worker under load (**H5**,
embargoed) — the same limit PR #55's self-audit names for its own `expires_at = 1` writes. **It is a
limit on the evidence, not a blocker**, and filing it as one would cost the next session a hunt for
something no local change can fix.

**B-1, B-2, B-4, B-5, B-7, B-8 untouched and not re-tested** — nothing in this slice bears on the
pairing UI, the live end-to-end, the emulator lane, Room under Robolectric, the egress policy or the
p2e counter's owner. **B-7 was not re-measured**; its unset `ANDROID_HOME` was observed in passing
(**C-84-2**) and is consistent with every prior reading. **B-15 and B-16 unchanged** — no vector byte
moved and the vendored pin is unmoved at `7328a0b` (**C-84-9**), so neither the CI drift check nor
the pin-staleness decision was exercised.

**One in-run error is recorded in the log and the audit rather than here, because it was recovered
and blocks nothing** (**C-84-8**): a `git checkout --theirs .` after a `git stash pop` discarded two
of three hunks during the commit split. Caught by grepping for the test names, restored from the
saved patch, suite re-run to 57 before committing. **The reason it is worth a line at all** is that
the failure is silent — `stash pop` reports `Auto-merging`, the subsequent `checkout` leaves a
**clean tree**, and a clean tree reads as *finished* rather than as *reverted*.

---

## Run 85 note — nothing new is blocked, and that is the honest answer

**The slice completed.** The ordered intent's NEW ITEM 2 was takeable in this sandbox, was taken, and
closed on measurement (**C-85-4**, **C-85-5**, **C-85-6**). **No new B-* entry is filed**, because
nothing human-shaped is missing for anything this run touched.

**What this run could NOT do, and why it is not a blocker.** The ordered intent's **ITEM 1** (the
engine half of run 83's suite-name hole) and **ITEM 3** (sweeping `src/Sync/Protocol.cs`) were both
re-verified as untakeable here and skipped: **`dotnet` and `pwsh` are absent**, checked with `which`
rather than assumed (**C-85-2**). That is the **gate**, which is already **H2** — and run 83's
reasoning stands unchanged: **filing it as a blocker would send the next session hunting a phantom.**
Both items stay in the ordered intent with the mutations that prove them, ready for the first
session that has a Windows machine.

**One limit on this run's own evidence, recorded as a limit rather than a blocker.** The DDL pin
asserts *"re-executing the DDL against storage that already ran it does not throw"*, which is a
slightly weaker oracle than *"the Durable Object survives a real second instantiation."*
`runInDurableObject` hands over the **instance**, not the constructor, so the constructor path itself
is not directly re-entered by any test. I believe the two coincide — the constructor's only storage
work *is* this `exec` (`relay/src/channel.ts:29`) — but **I did not prove that**, and it is named
first in PR #56's self-audit for exactly that reason. **No local change fixes it**; closing it
properly wants a deployed Worker observed across an eviction (**H5**, embargoed), the same wall PR
#55's and #56's self-audits already name.

**A second limit, on the refuted row.** The argument that `PRIMARY KEY (dir, seq)` is safely
removable is **a reading of `relay/src/channel.ts:190`, not a measurement** (**C-85-6**). It holds
only while that app-level check does: **if `channel.ts:190` is ever weakened, the PK becomes
load-bearing and unguarded in the same moment.** Recorded here so that a future change to the push
path finds this note rather than rediscovering it.

**B-1, B-2, B-4, B-5, B-7, B-8 untouched and not re-tested** — nothing in this slice bears on the
pairing UI, the live end-to-end, the emulator lane, Room under Robolectric, the egress policy or the
p2e counter's owner. **B-7 was not re-measured**; its unset `ANDROID_HOME` was observed in passing
(**C-85-2**) and is consistent with every prior reading. **B-15 and B-16 unchanged** — no vector byte
moved and the vendored pin is unmoved at `7328a0b` (**C-85-8**), so neither the CI drift check nor
the pin-staleness decision was exercised. **B-17 unchanged** — this run added **zero** landing cost
and **zero** new branches. **B-18 fired for the fiftieth time and was answered with silence**
(**C-85-9**); its four triggers are unchanged for the next run.

---

### B-18 status 2026-08-23 (eighty-sixth run) — the fifty-first firing, and the first run to send a notification

**Symptom, unchanged.** The stored prompt re-issues S5's spec half (§4.3 `entitlement_ack`, the
vector via `generate.mjs`, PQ-A2-1/-2/-3), describes S5 as "NOT STARTED", and carries the stale
vendored pin `679a317`. All three are false and have been since 2026-08-09 / 2026-08-12
(**C-86-1**). **B-18 cannot be closed by any agent** — the obstacle is scheduler configuration
outside both repositories — so this entry records firings and cost, nothing more.

**What changed this run: a fifth notification trigger, and it fired.** Runs 82–85 each re-checked
four triggers (engine `main` moved; android `main` moved; anything merged or undrafted; the prompt
changed) plus "a gate result exists", found all negative, and **deliberately stayed silent** — the
right call each time, because a run that reports "no change" spends the owner's attention for
nothing.

**All four are negative again this run.** Engine `main` still `aac05f3`, android `main` still
`ebfaf81`, nothing merged or undrafted (**22** engine PRs open counting this run's #57, **6**
android, all draft), prompt unchanged, no gate result.

**A fifth trigger is added, and this run meets it: a measured protocol finding with a
field-visible failure mode.** The four existing triggers are all *state* triggers — they ask
whether the world moved. None of them can ever fire on *what a run discovers*, which means a run
could measure a genuine defect in the wire protocol and the silence policy would suppress it
indefinitely. That is the wrong shape for a policy whose purpose is to protect attention rather
than to withhold findings.

The threshold is deliberately high, so this does not become a per-run summary: **a finding is
notifiable only if it is (a) measured in-session rather than reasoned, (b) visible to a user or
operator in the field rather than only to a reader of the code, and (c) not already reported.**
This run's finding meets all three: the relay serves a foreign `pairing` back to a receiver
verbatim, and because that field is in the §4.1 AAD the receiver reports **`decrypt_failed`** — the
code meaning *corrupt or tampered* — for what is really a misroute (**C-86-5**). A field diagnosis
that starts from "the crypto is broken" when the truth is "the envelope went to the wrong channel"
is expensive in exactly the situation where the owner has least context.

**It is a latent defect, not a live outage, and the notification says so.** Nothing in production
sends a mismatched `pairing` today; the values in the corpus are well-formed. What is absent is any
guard that keeps it that way — the same shape as run 85's DDL finding.

**Smallest human unblock (unchanged, and not what the notification asks for):** edit the stored
scheduled prompt to replace the S5 assignment with *"read `RETURN-DAY.md`, then `STATE.md`'s
ordered intent, and take its top item."* Everything else on the ladder needs the Windows gate, an
emulator (**B-4**), or a relay deploy — see `RETURN-DAY.md` §5.

---

### B-18 status 2026-08-23 (eighty-seventh run) — the fifty-second firing, and the first with a measured cost paid by the *records*

**Unchanged and still true:** the prompt assigns S5's spec half; that work has existed since
2026-08-09 (`8575539`, `22b028e`, `7328a0b`); the vendored pin is `7328a0b`, not the prompt's
`679a317`. Declined again, on re-derived evidence, for the reason attempt 1 gives: rebuilding it
would fork §4.3 and risk the corpus the android repo vendors.

**What is new, and it is why this firing was not free.** Every previous firing cost an *iteration*.
This one exposed a cost paid by the **records themselves**: while forty runs were being handed a
completed slice, **`RETURN-DAY.md` §3 — the landing plan, the first thing Brandon reads — went
stale and nobody noticed for four days.** Four PRs (#54–#57) were opened *by these very runs*, each
stacked on the previous, and each one moved the plan's step-2 leaf further out of date. Step 2 now
names an **interior node** (**C-87-3**). The loop is no longer only wasting runs; it is **generating
the drift that invalidates the handoff**, because a run with no admissible slice writes another
stacked draft PR, and the plan is not re-derived when it does.

**Smallest human unblock — unchanged, and now cheaper to justify.** Turn the routine off, or repoint
its "YOUR SLICE THIS ITERATION" section at `RETURN-DAY.md` §5's human queue. **A run that cannot land
anything should not be able to deepen the stack.** If the schedule keeps running as written, the
minimum mitigation is a standing instruction to **re-derive §3's leaf set every run** — that is a
`gh pr list` and eight lines of shell (**C-87-3**), and it is the check that would have caught this
on 2026-08-22 instead of 2026-08-23.

---

## B-19 — the landing plan has no guard against its own leaf set moving (eighty-seventh run, 2026-08-23)

**Milestone:** the return-day landing sequence (`RETURN-DAY.md` §3, `docs/Merge-Topology.md` §12).

**Symptom.** §3 names six merges by PR number. **PR numbers are not stable descriptions of a merge
graph.** When a new PR bases on a branch §3 lists, that branch stops being a leaf and §3's row
becomes an instruction to merge an interior node — landing part of a stack and stranding the rest.
This happened between 2026-08-19 and 2026-08-23 and **nothing detected it**: no CI check, no
harness, no assertion. It was found only because run 87 recomputed the leaf set by hand.

**Attempts.**
1. **Look for an existing guard.** None exists. `.github/workflows/ci.yml`'s vector check
   (lines 91–133) guards the *vendored corpus*, not the *PR graph*, and it queries `?ref=$PIN` — it
   cannot see PR topology at all. `docs/Merge-Topology.md` is a derivation, not a test.
2. **Correct the plan in place.** Done, and it is this run's deliverable: §3 step 2 now reads
   **#57** with a correction banner, and §1's count is **22** (**C-87-3**, **C-87-5**). This fixes
   *today's* instance; it does not prevent the next one.
3. **Add an automated guard.** Not attempted here, deliberately. The check is easy — recompute leaves
   from `gh pr list --json number,baseRefName,headRefName` and diff against the six numbers §3
   names — but it belongs in the android repo's CI, which **cannot run `gh` against the engine repo
   without a cross-repo token**, and provisioning that token is a decision with a credential
   attached. That is Brandon's, not this session's.

**Smallest human unblock.** Either (a) accept the manual check and require every run to re-derive
§3's leaf set (**C-87-3**'s eight lines), or (b) if the routine is being retired anyway, **land the
six merges** — a merged stack has no leaf set left to rot. Option (b) is strictly better and is
already the recommendation in `RETURN-DAY.md` §3.

**Why this is BLOCKED and not merely open.** The durable fix is a CI job needing a cross-repo
credential this session must not create. The manual fix is landed. **Nothing further is verifiable
from here.**
