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

**P0 — scaffold.** No product features yet. `:core` holds the Sync Protocol v1 constants
and rules; `:app` renders a placeholder that proves the toolchain assembles and that
`:core` is reachable. Pairing starts in P1.

Phase plan, gate decisions, and exit criteria: [`docs/P0-Runbook.md`](docs/P0-Runbook.md).

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
| `targetSdk` | **36** (Android 16) — Play's floor for new apps from 2026-08-31 |
| `minSdk` | 26 |

`targetSdk` is 36, not the 35 the program spec assumed: Play requires new apps and updates
to target API 36 from **2026-08-31**. The spec flagged its own number for re-verification,
and re-verification changed it.

The two SDK levels differ on purpose. `compileSdk` controls which APIs the code may
reference; `targetSdk` controls which runtime behavior changes the app opts into. Current
AndroidX refuses to be consumed below 37, while Play only requires 36 — so the app compiles
against 37 without opting into Android 17 behavior nothing here has been tested against.

### Building

**There is no Gradle wrapper in this repo yet.** It was scaffolded on a machine with no
JDK, and committing a `gradle-wrapper.jar` that could not be generated or inspected there
would have been worse than leaving it out. CI installs a pinned Gradle 9.6.1 instead.

From a machine with JDK 17 and the Android SDK:

```bash
gradle wrapper --gradle-version 9.6.1
gradle checkCoreIsAndroidFree :core:test :app:assembleDebug :app:lintDebug
```

Commit the generated wrapper when you do; the CI step that installs Gradle by version then
becomes redundant.

## Working rules

Inherited from the main repo's `CLAUDE.md` and unchanged here: derive state before acting,
secrets by name only, evidence standard ("ran it and saw it" or it didn't happen), small
reviewable commits, draft PRs, **never self-merge**, external audit before merges, gates
are Brandon's alone.
