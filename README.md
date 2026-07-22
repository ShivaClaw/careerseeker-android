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

Pre-P0. No app code yet. The phase plan and its blocking gates are in
[`docs/P0-Runbook.md`](docs/P0-Runbook.md); scaffolding starts once that is reviewed and
the gates are answered.

## Working rules

Inherited from the main repo's `CLAUDE.md` and unchanged here: derive state before acting,
secrets by name only, evidence standard ("ran it and saw it" or it didn't happen), small
reviewable commits, draft PRs, **never self-merge**, external audit before merges, gates
are Brandon's alone.
