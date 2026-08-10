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
