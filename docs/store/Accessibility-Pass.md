# Accessibility pass — the five read-only screens

**Scope (P5 §2.5):** TalkBack/reading-order/labels/touch-targets/contrast across Home, Applications,
Application detail, Jobs, Evidence. **Fixes are additive-only** — semantics / `contentDescription` /
`heading()` / touch-target modifiers, no structural refactors, renames, or file moves — so P4's
parallel UI work on `claude/p4-pro` doesn't collide.

**Two halves, honestly labeled:**
1. **Code-level audit + additive fixes + CI — DONE this session** (below), full Gradle ritual green.
2. **On-device TalkBack sweep — DEVICE-GATED** (Pixel 10 not connected 2026-07-24). The retest
   checklist is staged in §4; run it in the same device session as the screenshots (`assets/CAPTURE.md`).

**Method:** read all five screens + the shell (`DashboardApp.kt`) and the shared helpers
(`ScreenTitle`, `StateBadge`, `FlagBadge`, `MetricCard`); reasoned about the Compose semantics tree
(merged vs unmerged, clickable merging boundaries); applied additive fixes; added Robolectric
semantics assertions; ran the ritual. Evidence: `docs/P5-Evidence.md` §4/§6.

---

## 1. Findings → fixes (landed, CI-green)

| # | Screen / component | Finding | Additive fix | Where |
| --- | --- | --- | --- | --- |
| A1 | **All five** (`ScreenTitle`) | Screen titles were plain text — TalkBack couldn't navigate by heading | `Modifier.semantics { heading() }` on the shared `ScreenTitle` | `HomeScreen.kt` |
| A2 | **Home** (`MetricCard`) | Label + value were two separate focus stops ("Cycles", then "12") | Merge into one node announcing **"Cycles: 12"** (`semantics(mergeDescendants=true){contentDescription=…}`) | `HomeScreen.kt` |
| A3 | **Applications** (`ApplicationCard`) | Row was clickable but announced with no role and no action label | `clickable(onClickLabel = "Open application details", role = Role.Button)` | `ApplicationsScreen.kt` |
| A4 | **Jobs** (`FlagBadge`) | Repost/injection severity was carried by **colour** (neutral vs error) | Severity in words: **"Warning: injection flagged"** / **"Flag: repost"** | `ApplicationsScreen.kt` |
| A5 | **Application detail** (back) | Visible "< Back" reads as "less-than, Back" to TalkBack | `contentDescription = "Back"` (non-clearing → click action preserved) | `ApplicationDetailScreen.kt` |
| A6 | **Application detail** (section) | "Documents (read-only)" wasn't a heading | `Modifier.semantics { heading() }` | `ApplicationDetailScreen.kt` |

Each fix is a semantics-only modifier; **no visual layout changed**. `git diff --stat` vs
`claude/p2-replica`: 3 screen files + the test, +94/−6.

## 2. Considered and deliberately NOT changed (with reasons)

- **`StateBadge` raw token ("BLOCKED_FABRICATION" etc.) left as visible text.** Humanizing it via a
  `contentDescription` would **backfire** on the Applications screen: the badge sits inside the
  **clickable card**, which is a semantics *merging boundary*. A `contentDescription` on any child of
  a merged clickable **suppresses the sibling text** (title/company/score) in TalkBack's spoken
  output — you'd hear "Status: drafted" and lose the job title. The badge already announces its
  **text** (meaning is in words, not only colour), so the baseline is acceptable. The right place to
  humanize the state is the **display layer** (a P3/P4 UI decision), not an additive P5 semantics
  hack. Documented so it isn't "missed."
- **Dark mode.** The app wraps `MaterialTheme {}` (M3 **baseline light** scheme; no dynamic-color or
  dark handling in `MainActivity`), so dark-mode users see a light UI. This is a UI-owner decision
  (P4), out of additive-a11y scope — **noted for its owner**, not fixed here.
- **`StatusBanner` / `AuditBadge`.** Already state status in words ("Demo data — not a live engine",
  "Audit chain BROKEN (engine-reported)", "Audit status unknown — not yet reported") — meaning does
  not depend on colour. No change needed; confirm announcement quality on device.

## 3. Touch targets & contrast (analysis; confirm on device)

**Touch targets ≥48dp.** The interactive elements are M3 components — `NavigationBarItem`,
`FilterChip`, `TextButton` — which apply `minimumInteractiveComponentSize()` (a 48dp minimum touch
target) automatically even when the visual is smaller (e.g., a 32dp chip). The only custom clickable,
`ApplicationCard`, is a full-width, multi-line `Card` far exceeding 48dp. **No target is below 48dp in
code.** Verify on device with Google's **Accessibility Scanner** (it flags sub-48dp targets).

**Contrast.** Screens render under `MaterialTheme {}` (baseline scheme). Badges/banners use
`Surface(color = <container>)`, and M3 automatically sets the text colour to
`contentColorFor(container)` — the **paired on-container token** (e.g., `onErrorContainer` on
`errorContainer`). The baseline M3 container/on-container pairs are designed to meet **WCAG AA**, so
badge/label contrast is compliant by construction. Verify the actual rendering (secondary/tertiary
containers especially) with Accessibility Scanner's contrast check on device.

## 4. On-device TalkBack retest checklist (Pixel 10 — run with `assets/CAPTURE.md`)

Enable TalkBack (Settings → Accessibility → TalkBack) and install the demo build, then per screen:

- [ ] **Reading order** is top-to-bottom, logical (title → banner/badge → content). No focus traps.
- [ ] **Headings**: swipe-by-heading (TalkBack rotor) lands on each screen title and "Documents".
- [ ] **Home**: each metric announces as one item, "Cycles: 12" etc. (A2). Banner announced.
- [ ] **Applications**: each row announces its title/company/score/state and "double tap to open
      application details" (A3). Filter chips announce label + selected state.
- [ ] **Detail**: "Back, button" (A5). Documents read in order; untrusted doc text is read as plain
      text (no actionable/interpreted content).
- [ ] **Jobs**: injection badge announces "Warning: injection flagged"; repost announces
      "Flag: repost" (A4).
- [ ] **Evidence**: audit badge announces its worded status; each event reads coherently.
- [ ] **Touch targets**: run **Accessibility Scanner** — zero sub-48dp target flags.
- [ ] **Contrast**: Accessibility Scanner contrast check — zero AA failures on badges/labels.
- [ ] Record results (pass/fail per item + any new finding) back into this §4 and `P5-Evidence.md` §5.

Any device finding that needs more than an additive tweak (e.g., a display-layer state rename) is
**noted for its owner** (P3/P4), not fixed from this P5 session.

## 5. Verification (this session)

- **Robolectric a11y assertions added (5):** heading on the screen title; merged metric-card
  description; application row Role.Button; flag-badge severity descriptions; back-button label.
  (`ScreensFromFixtureTest.kt`.)
- **Full ritual green (exit 0, 2026-07-24):** `checkCoreIsAndroidFree :core:test :app:testDebugUnitTest
  :app:assembleDebug :app:lintDebug`. App unit tests **25 → 30** (ScreensFromFixtureTest 6 → 11),
  0 failures; `:core` 17; lint clean with `warningsAsErrors`. Detail in `P5-Evidence.md` §6.

## 6. Exit state

- [x] Code-level audit of all five screens; 6 additive fixes landed; 5 a11y assertions; CI green.
- [x] Touch-target and contrast reasoned to compliant-by-construction; on-device confirmation staged.
- [ ] **Device:** TalkBack sweep + Accessibility Scanner on the Pixel 10 (§4) — turns this from
      "analyzed" to "ran it and saw it."
