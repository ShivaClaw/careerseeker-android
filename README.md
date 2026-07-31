# careerseeker-android

The Android companion app for [CareerSeeker](https://github.com/ShivaClaw/careerseeker) —
**CareerSeeker Dashboard**, a paid Kotlin/Compose client that pairs with the free Windows
engine over an end-to-end-encrypted blind relay.

**This repository is private, always.** It stays private regardless of the main repo's
visibility (`ShivaClaw/careerseeker` is public, so that the alpha ZIP can be served).
Nothing here is intended for public consumption.

## What this app is, and is not

The Windows engine does all the work and holds all the credentials. This app is a
**window** into it, plus a document editor.

- It shows the user's own pipeline: jobs found, applications drafted, scores, evidence.
- It lets the user view and edit their draft emails, resumes, and cover letters from the
  phone. Edits travel to the PC as **signed commands**; the engine is authoritative and
  applies them.
- **It has no send path and never holds Gmail credentials.** The only exit is a deep link
  that opens the Gmail app on the draft. The user presses send in Gmail, never here.
- No accounts. No analytics. No trackers. Pairing, not sign-up.

## Repo split

| Lives here (private) | Lives in `ShivaClaw/careerseeker` (public) |
| --- | --- |
| Kotlin/Compose app, `:core` protocol module | `relay/` — the blind relay Worker |
| Program strategy, runbooks, gate records | `docs/Sync-Protocol.md` + shared test vectors |
| Play Console / listing / billing planning | `src/Sync/` engine publisher, `SyncHarness` |

The relay is public deliberately: its entire value proposition is that it cannot read
your data, and that claim is worth more when anyone can audit it. The protocol spec and
its test vectors are public for the same reason — and because both sides consume the same
vectors, which is how cross-repo drift gets caught in CI.

## Status

**Read-only dashboard, offline-complete.** `:core` implements the Sync Protocol v1 envelope
codec (AES-256-GCM, ECDH/ECDSA P-256, HKDF) and the receiving state machine, proven against
the shared cross-language test vectors. `:app` holds a Room replica, the envelope applier,
and five read-only Compose screens rendering it.

Not built yet: the pairing UI and the live relay transport (both device-bound), outcome
marking, and the entitlement surface.

Phase plan, gate decisions, and exit criteria: [`docs/P0-Runbook.md`](docs/P0-Runbook.md).
Session-by-session executed evidence: [`LOG.md`](LOG.md); the re-verification commands
behind every claim: [`AUDIT-REQUEST.md`](AUDIT-REQUEST.md).

> **`applicationId` is PROVISIONAL.** It is currently `app.careerseeker.dashboard`. A Play
> application id is **permanent once published** and cannot be changed afterwards, so it is
> Brandon's call to confirm before any upload. Nothing in this repo uploads anything, and the
> package namespace may keep matching the id either way.

### Toolchain

Versions verified against the artifact repositories and Play policy on 2026-07-22, not
copied from the spec:

| | |
| --- | --- |
| AGP | 9.3.0 (built-in Kotlin — do **not** apply `org.jetbrains.kotlin.android`) |
| Gradle | 9.6.1 (AGP 9.3 requires 9.5.0+) |
| JDK | 17 |
| Kotlin | 2.4.10 |
| Compose BOM | 2026.06.01 |
| `compileSdk` | **37** — forced by AndroidX (core-ktx 1.19.0, lifecycle 2.11.0) |
| `targetSdk` | **37** — clears Play's floor of 36 |
| `minSdk` | 26 |

**Play's floor is `targetSdk` 36** for new apps and updates from **2026-08-31**, verified
2026-07-22. The program spec's "assume 35+" would be rejected outright; it flagged its own
number for re-verification, and re-verification changed it.

The two levels answer different questions — `compileSdk` is which APIs the code may
*reference*, `targetSdk` is which runtime behaviors the app *opts into* — so they can
legitimately differ. Both are 37 here: AndroidX forces the first, and lint's `OldTargetApi`
treats a lagging `targetSdk` as an error. Lagging is defensible once there are features
whose behavior could regress under a newer Android; there are none yet, and suppressing the
check would silence the prompt to re-test when that changes.

### Building

The Gradle wrapper is committed and is the single source of the Gradle version
(9.6.1). It was generated 2026-07-22 from the official distribution, with both the
distribution zip and the resulting `gradle-wrapper.jar` verified against the sha256
checksums published by services.gradle.org; `distributionSha256Sum` is pinned in
`gradle-wrapper.properties` so every future download is verified too.

```bash
./gradlew checkCoreIsAndroidFree :core:test :app:test :app:assembleDebug :app:lintDebug
```

Add `--rerun-tasks` when you are verifying a claim rather than iterating: Gradle otherwise
reports `UP-TO-DATE` and you are reading a cache, not an execution.

`:core` targets JVM 17 via `jvmToolchain(17)` — have a JDK 17 available (Android
Studio's bundled JBR works for running Gradle itself).

## Working rules

Inherited from the main repo's `CLAUDE.md` and unchanged here: derive state before acting,
secrets by name only, evidence standard ("ran it and saw it" or it didn't happen), small
reviewable commits, draft PRs, **never self-merge**, external audit before merges, gates
are Brandon's alone.
