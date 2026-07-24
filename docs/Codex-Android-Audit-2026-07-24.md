# CareerSeeker Android — Codex Audit Seed (P2 offline)

**Prepared:** 2026-07-24, for an adversarial audit of the **P2 offline** work across both repos.
**Companion:** [Checkpoint-2026-07-24.md](Checkpoint-2026-07-24.md) — the full spec-vs-build
meta-analysis and findings ledger. This seed is the *directed* version: what to check, where,
and what to ignore. Read the checkpoint for context; work from this.

---

## 0. Your job

You are the independent auditor in a program whose rule is **Codex audits, Brandon merges — the
author never self-merges.** This is a correctness / security / privacy audit against stated
invariants, not a style pass. The bar: **find where claimed ≠ actual, where a load-bearing
invariant leaks, or where a fix is insufficient for the failure it names.** The author (Claude)
believes this work is complete and green; your value is in disproving that where it's wrong.

Be concrete. Every finding: `file:line`, the specific input/state that triggers it, the wrong
outcome, and a severity. "Looks risky" without a path is noise. If you can't find a real defect
in an area, say so plainly — a clean bill on a specific invariant is itself useful signal.

---

## 1. Scope

**In scope — audit these:**

- **Engine (public repo `ShivaClaw/careerseeker`), branch `claude/p2-publisher`.**
  Diff to audit: `git log --oneline claude/p1-sync..claude/p2-publisher` (6 commits,
  `51c6bcd`→`dbad69e`). The new sync-publish surface + the normative protocol changes.
- **Android (private repo `ShivaClaw/careerseeker-android`), branch `claude/p2-replica`.**
  Diff to audit: `git log --oneline claude/p1-pairing..claude/p2-replica` (5 commits,
  `44d0abe`→`bd3f71d`). The Room replica, envelope applier, demo fixture, five read-only
  screens, and the doc_kind reconciliation.
- **The normative protocol doc** `docs/Sync-Protocol.md` (engine repo) — §3–§8, especially the
  new §4.3.1 and the §6.1 sequence-persistence rule.

**Out of scope — do NOT audit or flag as incomplete (§7 explains why each is deferred):**

- Anything device-bound: phone pairing UI, desktop `/pair` page, cert pinning, the WSS live
  feed, the airplane-mode exit proof. None of it is built; it's the P2 finale.
- The `doc` payload pipeline (engine emitting resume/cover text). Deliberately not built.
- The inbound phone→engine path (`doc_edit`/`outcome`/`entitlement`/`pull_request` handling) —
  that's P3/P4.
- P1 crypto (pairing, codec, receiver, vectors, relay) — already audited and proven; audit it
  only if the P2 diff *changed* its behavior (it should not have).

---

## 2. What was built (the claims to verify)

**Engine:** `SyncPublisher` seals `SyncPayloads` (snapshot/delta/heartbeat/evidence) with `k_e2p`
into v1 wire envelopes, assigns a monotonic e2p `seq`, pushes through an injected sink;
`EngineSyncBridge` projects live `EngineCounters` + recent application/job/evidence rows into
those payloads; the host publishes after each cycle **behind `--sync`, default OFF**. Proven by
`SyncHarness` (offline) and `SyncLiveSmoke` (live relay, 22/22).

**Android:** a Room replica; an `EnvelopeApplier` that projects decrypted engine→phone payloads
into Room (snapshot=replace, delta=upsert, heartbeat=counters, evidence=trail+verdict) with a
**persisted** seq high-water mark; a demo fixture; five read-only Compose screens as pure
projections of Room. Proven by 21 Robolectric tests + CI.

**Files most worth opening (not exhaustive):**
| Concern | Engine | Android |
| --- | --- | --- |
| Payload builders | `src/Sync/SyncPayloads.cs` | — |
| Seal + sequence + push | `src/Sync/SyncPublisher.cs` | — |
| Projection + drive | `src/Engine/EngineSyncBridge.cs` | — |
| Host wiring / flag / seam | `src/Engine/Host.cs`, `src/Engine/Program.cs` (`BuildSyncBridge`) | — |
| Receiver (crypto boundary) | `src/Sync/EnvelopeReceiver.cs` | `core/.../EnvelopeReceiver.kt` |
| Apply to store | — | `app/.../replica/EnvelopeApplier.kt` |
| Replica schema | — | `app/.../replica/ReplicaEntities.kt`, `ReplicaDao.kt` |
| Fixture / screens | — | `app/.../replica/DemoFixture.kt`, `app/.../ui/*Screen.kt` |
| Conformance tests | `tests/SyncHarness/Program.cs`, `tests/EngineHarness/Program.cs` | `app/src/test/.../*Test.kt` |

---

## 3. Invariants — verify each actually holds in the code

These are load-bearing (from spec §2/§7, `CLAUDE.md`, protocol §8). For each, confirm the code
enforces it or find the path that breaks it.

1. **No raw posting body ever reaches the phone.** `snapshot`/`delta` carry only
   {id,state,company,title,score} and {id,company,title,repost,injection_flag}; `evidence`
   carries only audit *metadata*. **Check:** is there ANY field, in any builder or projection,
   that could carry a job description / recruiter text / event payload body? Trace
   `EngineSyncBridge` back to its sources (`DashboardEvidence`, the store rows). The harnesses
   assert "no raw posting body" by string-absence — is that assertion actually sufficient, or
   is there a field name it doesn't check?
2. **No send path.** Confirm nothing in the P2 diff introduces a payload kind or code path that
   could transmit email. `DispatcherNoSendHarness` must still be green.
3. **Sync is opt-in, default OFF, and OFF is byte-identical to before.** With `--sync` absent,
   is the tick provably unchanged (`Host.cs` constructs the scheduler with exactly
   `cycle.TickAsync`)? Any way the bridge runs when it shouldn't?
4. **The applier never advances its persisted seq on a rejected/malformed/ignored payload**, and
   a stale seq (≤ high-water) is a no-op even across a simulated restart. Trace every early-return
   in `EnvelopeApplier.apply` — does any path commit the sync_state row without applying?
5. **The `auditOk` lifecycle is honest.** evidence sets it; delta/heartbeat preserve it; snapshot
   resets it to null (unknown). **Check for a hole:** can the screen ever show "intact
   (engine-verified)" based on stale or demo data after real data arrived? Is `demoMode` cleared
   on every real apply?
6. **Score unit (F1).** `EngineSyncBridge.ScoreToWire` maps the engine's 0–5 total to a 0–100 int
   (`round(total×20)`, clamped). Confirm the clamp can't overflow/underflow and that the phone
   renders the same field it's sent. Is 0–5 actually the guaranteed engine range? (See
   `src/Scorer/Scorer.cs`: `total = min(fit,legitimacy)·multiplier`, axes clamped 0–5.)
7. **Seq resumes above the phone's high-water mark (F4).** Protocol §6.1 now requires the engine
   resume its e2p counter above `max(persisted, relay latest)`. The *rule* is documented and the
   *mechanism* (`SyncPublisher(startSeq)`) is tested, but the vault that supplies it is
   device-session work. **Check:** is the §6.1 rule actually sufficient to prevent the silent
   sync-death it describes? Is there a gap between "documented MUST" and "enforceable" that
   should be a stronger guard now (e.g., should `BuildSyncBridge` refuse to construct a publisher
   without a resolved startSeq)?
8. **Untrusted text stays inert on the phone.** The screens render document/company/title text.
   Confirm nothing interpolates it, markdown-renders it with active content, or makes it
   actionable (spec §2.4).
9. **Drift trap held.** Every count-bearing change moved the pin + all docs in one commit. Spot
   check: does `$ExpectedOfflineTotal` (435) equal the sum of the per-harness counts in every doc
   and the script's own assert strings?
10. **Vector integrity untouched.** The P2 diff must not have hand-edited shared vectors or
    changed P1 crypto. Confirm `docs/sync-vectors/` is unchanged in the P2 range and the android
    `VECTORS.lock` still matches.

---

## 4. Reproduce the verification (don't trust the numbers — run them)

**Engine (Windows, PowerShell — `pwsh` is not installed, use `powershell.exe`):**
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Alpha.ps1
```
Expect `Offline total: 435 passed, 0 failed`. Note: `EngineHarness` binds **port 7777** for its
dashboard test; if a real dashboard is running the count silently drops ~39 and the pin assert
fails — free the port first.

Live relay round-trip (optional, hits `relay.careerseeker.app`, out of the hermetic suite):
```
dotnet run --project tests/SyncLiveSmoke -c Release -- https://relay.careerseeker.app
```
Expect `22 passed`.

**Android** (`JAVA_HOME` = Android Studio jbr, `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`):
```
./gradlew checkCoreIsAndroidFree :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```
Expect BUILD SUCCESSFUL, 21 unit tests, lint clean with `warningsAsErrors`. CI on the branch is
the same pipeline in a clean container.

---

## 5. Audit hotspots — where a fresh adversarial eye is worth the most

Ranked by where the author is least certain the analysis is complete:

- **A. The untrusted-text guarantee, end to end.** The strongest claim in the program is "no raw
  posting body reaches the phone." It's enforced by *what the builders choose to carry*, not by a
  filter. Trace every field from the SQLite source rows through `EngineSyncBridge` to the wire.
  Is there a store column whose contents are attacker-influenced (a scraped title? an
  injection-signal string?) that rides to the phone and is rendered? `JobSummaryRow` has
  `InjectionSignals` and `Title` — where do those originate, and are they inert on the phone?
- **B. The `evidence` payload's honesty.** It carries `actor/kind/entity/entity_id`. Are those
  ever free-text or attacker-influenced (e.g., an `entity_id` derived from a job posting)? The
  claim is "engine-internal structured identifiers." Verify against how audit events are actually
  written in the engine.
- **C. The applier's transaction boundaries.** Everything runs in `db.withTransaction`. Confirm a
  malformed payload or an exception mid-apply leaves the seq mark *and* the tables exactly as
  before — no partial write, no advanced seq. The `applyEvidence` returns `Boolean?` where null =
  malformed; is there any valid `audit_ok=false` that gets confused with malformed?
- **D. F4's sufficiency.** The seq-death failure mode is real and the fix is currently a
  *documented rule* + a *tested mechanism*, but the enforcing vault doesn't exist yet. Is
  documenting-the-MUST the right call for now, or is this a latent trap that should have a
  runtime guard today? Argue it either way with the failure path.
- **E. Score-scale edge cases (F1).** `round(total×20)`: what does a `total` of exactly 2.5 give
  (banker's vs away-from-zero rounding)? Is `AwayFromZero` the intended behavior, and does the
  phone's integer display match? Any locale/culture issue in the int formatting on the wire?
- **F. The demo/real boundary.** Demo fixture seeds at seq 0 with `demoMode=true`. Prove no real
  envelope can be shadowed by demo data, and no demo value (score, auditOk) can survive visibly
  into a real session mislabeled.

---

## 6. Working rules the work must satisfy (hold it to these)

- **Evidence standard:** "ran it and saw it" or it didn't happen. Claims in commit messages must
  be reproducible by §4.
- **Drift trap:** counts in `Verify-Alpha.ps1` (pin + assert strings) + `README.md` +
  `src/Engine/README.md` + `docs/CareerSeeker-Project-Summary.md` +
  `docs/External-Audit-Handoff.md` + `docs/repo-audit-2026-07-13.md` move as ONE commit. Flag any
  count that disagrees.
- **No speculative parsers:** the phone deliberately has **no** `doc` applier branch because the
  engine doesn't emit `doc`. That's correct discipline, not a gap — do not flag it. (It's F3's
  deferred half; see §7.)
- **Structural over substring:** checks should parse structure, not grep tokens (substring greps
  caused false positives historically). If you find an invariant "enforced" only by a fragile
  string search, that's a finding.

---

## 7. Known-deferred — do NOT report these as defects

Each is deferred deliberately, with a recorded reason. Flag only if you think the *reason* is
wrong or the deferral creates a hazard that isn't contained.

| Item | Why deferred | Contained by |
| --- | --- | --- |
| `doc` payload + editor (F3 pipeline) | Engine persists only PDF paths, not tailored text + rev; needs a store migration | P3's opening commit; no `doc` branch anywhere, so no half-built parser |
| Device-bound finale | Needs a physical handset to verify honestly | P2-Runbook §2.1/§3; three open gates |
| Pairing vault (persists `last_e2p_seq`) | Device-session deliverable | F4 rule in §6.1 documents what it must satisfy |
| Heartbeat timer (F5) | Only matters with a live phone watching | Host publishes per-cycle today; one-line add in the device session |
| `event_count` unused on phone (F7) | No column; low value alone | Carried on the wire; add with a future migration |
| Inbound p2e handling | P3/P4 | Receiver built + vector-proven; no host pull loop yet |
| Pricing-page rewrite | P6 launch blocker | Tracked in `Monetization-Decision.md`; Sonnet TODO exists |

---

## 8. Deliverable

A findings list, most-severe first. For each: **severity** (blocker / high / medium / low),
`file:line`, the **triggering input or state**, the **wrong outcome**, and a one-line **fix
direction**. Separate genuine defects from "deferred, reason sound" confirmations. If an invariant
in §3 holds, say so explicitly — the author wants the clean bills as much as the breaks. End with
your overall judgment: is the offline P2 half safe to open as draft PRs for merge review?
