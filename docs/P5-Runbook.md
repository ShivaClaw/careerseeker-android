# P5 Runbook — Store readiness, staged (everything before the account exists)

**Written:** 2026-07-24 (Fable, planning role), for the **Opus session executing P5**.
**Read first, in order:** this file → `HANDOFF.md` (this repo, main) →
`docs/Checkpoint-2026-07-24.md` → `docs/Sync-Consent-Copy.md` (branch `claude/p0-scaffold`)
→ `docs/Monetization-Decision.md` (branch `claude/p1-runbook`) → spec §5 and §7.3
(`Android-Dashboard-Pro-Spec-2026-07-22.md`, Desktop).

---

## 0. Reframing P5 under the constraint

**There is no Google Play account yet** — D-U-N-S and LLC registration are 2–3 weeks out.
So this phase's goal changes shape: **when the account lands, listing day must be a
mechanical afternoon of pasting staged artifacts, not a scramble.** Everything you produce
is staged in this private repo under `docs/store/`; NOTHING is deployed to the live site and
NOTHING touches a console (there isn't one). The two work streams that genuinely need
hardware — screenshots and the accessibility pass — need only the **Pixel 10** (available,
USB debugging) and the app's demo mode, which renders all five screens with honestly-labeled
fixture data on first launch, no engine, no pairing, no account.

**Play policy drifts quarterly.** For every policy fact you state (data-safety form
questions, screenshot pixel specs, target-API requirement, closed-testing rules, PBL
minimums), **verify against the live Play Help/policy pages at execution time** (WebFetch)
and cite what you saw, with the date. The spec's own instruction (§5) is that the executing
model re-verifies; a stale citation in the dossier is a finding, not a shortcut.

## 1. Session setup (parallel-session discipline — read before touching anything)

A **P4 session runs in parallel** with you, building Pro (billing/entitlement/outcomes).
Collision rules:

- **You (P5) own:** android branch **`claude/p5-store`** (create off `claude/p2-replica`),
  everything under `docs/store/**`, and **additive-only** accessibility edits to the five
  existing screens (semantics/contentDescription/touch-target modifiers — no structural
  refactors, no renames, no file moves). You do **NOT** touch the engine repo at all — the
  drift trap is P4's to manage this cycle, and yours to never trigger.
- **P4 owns:** the engine repo; android branch `claude/p4-pro`; new billing/Pro/outcome
  files.
- **Shared-clone hazard:** you both work on the same PC and P4 uses the main clones. **You
  work in a separate git worktree.** First command of the session:
  `git -C C:\Users\bkirk\Documents\careerseeker-android worktree add
  C:\Users\bkirk\Documents\careerseeker-android-p5 -b claude/p5-store claude/p2-replica`
  — then do ALL android work in `careerseeker-android-p5`, and remove the worktree at
  session end. Never switch branches in the main clone.

**Environment facts:** Android builds: `JAVA_HOME=C:\Program Files\Android\Android
Studio\jbr`, `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`; ritual `./gradlew
checkCoreIsAndroidFree :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
(currently 25 unit tests, lint `warningsAsErrors`); AGP 9 has built-in Kotlin — never apply
`org.jetbrains.kotlin.android`; interrupted `--no-daemon` runs corrupt `*/build/kotlin`
caches → delete them. Pixel 10: `adb devices` / `adb install -r
app/build/outputs/apk/debug/app-debug.apk` / `adb exec-out screencap -p > name.png`.

**Non-negotiable rules:** evidence standard ("ran it and saw it" — cite command output and
the policy-page text you verified); draft PRs only, never self-merge, never push code to
main (docs-only exceptions exist but your a11y edits are code — they go on your branch);
secrets by name only; untrusted text stays inert; every public-facing word must keep every
live promise on careerseeker.app literally true (the pricing rewrite itself is P6's, with a
Sonnet TODO — not yours).

## 2. Work items (any order except 2.6 last; 2.1–2.3 are pure staging)

### 2.1 Data-safety dossier — `docs/store/Play-Data-Safety.md`

Walk the **current** data-safety form questions (verify against live Play Help; capture the
question text you answered). Ground every answer in the architecture, extending
`Sync-Consent-Copy.md` §5's mapping: no data collected by the developer; nothing shared —
with the **processor-hosting-ciphertext** framing for Cloudflare stated plainly in the
review notes rather than left for a reviewer to infer; E2EE marked (developer cannot read);
deletion = unpair/uninstall + relay TTL ≤30d; **no accounts — "pairing, not accounts"** in
the review-notes text you draft. Include: IARC questionnaire prep answers (utility, no
objectionable content); target audience **18+** (avoids the Families surface); ads = none;
not a financial-features app.

**Permissions truth-matching:** the manifest today declares only `INTERNET`. `CAMERA`
arrives with the P2 device-finale pairing UI and `POST_NOTIFICATIONS` may arrive with it.
The dossier must state BOTH states and carry a red-boxed rule: **the form answers submitted
must match the manifest of the exact AAB uploaded** — re-check on account day.

### 2.2 Privacy-policy delta + dashboard-page copy — `docs/store/Privacy-Policy-Delta.md`

Finalize `Sync-Consent-Copy.md` §4 into publish-ready copy for `careerseeker.app/privacy/`
("The phone dashboard and the relay" section: Cloudflare named, what the relay stores, what
we can see, retention ≤30d, no analytics/trackers/crash reporting, erasure, no send path).
Also stage the `/dashboard/` page copy refresh (real description; screenshot slots; Play
link placeholder). **Do not deploy anything** — the one-artifact rule (app behavior +
data-safety answers + privacy policy + site copy ship together) means these go live at P6
with the listing. State that rule at the top of the file.

### 2.3 Listing package — `docs/store/Play-Listing.md`

App title (≤30 chars — "CareerSeeker Dashboard"), short description (≤80), full description
(≤4000), category, contact email (`support@careerseeker.app`, already routed), and the
**reviewer notes** — the most load-bearing text in the listing: no account to log into;
the app runs in **demo mode** out of the box (this is how a reviewer sees every screen);
pairing explained in two sentences; E2EE framing. Naming canon applies (the Windows app is
"CareerSeeker", never "Basic"; Dashboard/Pro are add-ons). Prices: Dashboard $4.99 up-front
paid, Pro $2.99 INAPP — per the closed P-MONEY gate. Nothing in the copy may promise what
P2-finale/P3 haven't shipped: describe the read-only dashboard v1 honestly (live view,
offline replica, evidence trail); phone editing is listed only if P3 lands before submission.

### 2.4 Screenshots + graphic assets — `docs/store/assets/`

Sideload the current debug build on the **Pixel 10** (demo mode self-seeds). Capture all
five screens (Home/Applications/Detail/Jobs/Evidence) via `adb exec-out screencap`; verify
the current Play screenshot specs first (count minimums, aspect/pixel bounds) and record
them. The demo-mode banner appearing in shots is **fine and honest** — note it in the
listing doc. Feature graphic (1024×500) and the 512×512 hi-res icon: draft what you can as
clean brand-consistent SVG→PNG; where a real design decision is needed (the launcher icon is
still the template default), file it as **Gate P5-ICON** for Brandon with 2–3 concrete
options rather than silently shipping placeholder art. Keep binaries small; this repo is
private.

### 2.5 Accessibility pass — `docs/store/Accessibility-Pass.md` + additive code fixes

TalkBack on the Pixel across all five screens: reading order, announced labels for every
interactive element, state badges announced meaningfully (not just color), touch targets
≥48dp, contrast on the badge/label colors. Fix in code **additively only** (semantics
blocks, `contentDescription`, `heading()`, minimum touch-target modifiers) so P4's parallel
UI work doesn't conflict; add Robolectric/lint-level a11y assertions where feasible. Every
fix runs the full gradle ritual; CI green before it counts. Document findings → fixes →
retest in the pass doc.

### 2.6 Account-day checklist — `docs/store/Account-Day-Checklist.md` (write last)

The mechanical afternoon, in exact order, each step pointing at its staged artifact:
LLC + D-U-N-S in hand → Play Console **organization** account ($25) → identity verification
→ merchant profile → create app (**applicationId `app.careerseeker.dashboard` — permanent;
confirm P4's gate closed**) → Play App Signing + first **AAB** (verify current target-API
requirement) → paste §2.3 listing + §2.4 assets + §2.1 data-safety → content declarations
(IARC, ads, 18+, account-deletion N/A) → License Key RSA public → hand to P4's config →
create `pro_unlock` $2.99 INAPP → license testers → **internal track** → closed track +
tester recruitment (Windows alpha testers; org account should not carry the personal-account
12-tester/14-day rule — **verify against current policy and record what you find**; if it
somehow applies, the 14-day clock starts here and gates production, plan it) → pre-launch
report triage. Include a "who does what" column (Brandon-only: payments, identity, legal;
session: everything else).

## 3. Explicit non-goals

No console actions (impossible); no live-site deploys (P6, one-artifact rule); no engine-repo
changes (P4's); no pricing-page rewrite (P6 + Sonnet TODO `docs/todo/Pricing-Page-Rewrite.md`);
no CAMERA permission work (P2 finale); no new screens (P4 owns the Pro screen); no icon
bikeshedding beyond Gate P5-ICON's options.

## 4. Exit criteria + evidence

`docs/store/` contains: data-safety dossier with dated live-policy citations; publish-ready
privacy/site copy; complete listing text with reviewer notes; ≥5 Pixel-10 screenshots
meeting verified current specs; the a11y pass doc with fixes landed and CI green; the
account-day checklist executable end-to-end. Evidence file `docs/P5-Evidence.md` in the
house style: commands run, policy pages verified (with dates), device used, CI links. Push
`claude/p5-store`; **draft PR only when Brandon says open it.** Anything discovered that
belongs to another phase (engine change, screen redesign, P3 dependency) gets *noted for its
owner*, never fixed cross-scope from this session.
