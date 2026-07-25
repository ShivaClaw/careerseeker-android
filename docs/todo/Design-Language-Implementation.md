# TODO — CareerSeeker Design Language on the Android app (build AFTER P4 merges)

**Decision (Brandon, 2026-07-24):** plan now, implement after P4's screen work lands — a full
reskin is not additive, and the parallel P4 session owns the same five screens on `claude/p4-pro`.
This doc is the complete implementation spec so the executing session (any model) needs no
re-derivation. **Do not start while `claude/p4-pro` is unmerged** — rebase onto its merge result.

**Sources (Claude Design, read 2026-07-24 via DesignSync):**
- Project `d57594ba-7192-416a-ad63-4bae8ebccc03` — `CareerSeeker Design Language.dc.html`
  (turn t1, options 1a–1g), `android-frame.jsx` (canvas preview scaffold only — NOT app code),
  `support.js` (canvas runtime).
- Project `e03c8d14-863b-44f5-abd7-a6bd211248d3` — brand logo
  (`uploads/site-v2-backup-2026-07-22/assets/logo.svg`), Brand Kit / Logo pages.

## 0. The two palettes — do not mix them up

| Surface | Palette | Type |
| --- | --- | --- |
| **Marketing / brand** (site, store listing, launcher icon) | near-black `#0A0A0B`, lime `#A3FF12`, yellow `#FFE500` — the radar logo | Space Grotesk · Inter · IBM Plex Mono |
| **Product UI** (Windows dashboard + Android app) | "cockpit instrumentation" — see §1 | IBM Plex Sans · IBM Plex Mono |

The launcher icon wears the **brand** mark; the app's **screens** wear the cockpit palette. This is
the design's own split (the Design Language doc's mockups use trust-blue cockpit for both apps
while the site mock uses lime/yellow).

## 1. Cockpit tokens (Design Language §1a, verbatim hexes)

```
void        #07090D   app background
panel       #0C1017   card/panel background
raised      #131A24   raised surface
trust       #45C4FF   primary accent ("trust blue") — links, chips, selected nav, DRAFTED
teal        #2FD6C3   secondary accent — GATE ✓
go          #3DDC97   GO / verified / RUNNING lamp
caution     #FFB454   CAUTION / PAUSED
block       #FF5D5D   BLOCK / KILLED / errors
text-hi     #D7E2EE   primary text
text-dim    #7B8BA1   secondary text
text-faint  #55637A   section labels (mono, letterspaced)
hairline    rgba(148,197,255,.10–.14)   borders
```

Type: **IBM Plex Sans** for prose/UI, **IBM Plex Mono (tabular)** for every number, timestamp,
hash, state name, and section label (10px, letterspacing ~.14–.16em, uppercase). Radii: 8–12px.
Status lamps: 8–9px dot + glow (`box-shadow 0 0 8-10px`), RUNNING pulses (2s opacity 1→.35).

Key patterns from the mockups:
- **Grade lamps** (1a): 40dp square, mono letter A–F, tinted border+bg+glow per grade
  (A `#3DDC97` glowing, B `#2FD6C3`, C `#FFB454`, D `#FF8A5C`, F `#FF5D5D`).
- **Autonomy chip** (1a): bordered mono chip — `L1 · DRAFTS` in trust-blue + dim "you review ·
  you send". L2 amber / L3 red variants exist but are FUTURE (engine is L1).
- **Status header** (1c): lamp + mono status + L1 chip + `SYNC 12s` right-aligned.
- **Stat tiles** (1c): panel cards, mono 20sp value, 9sp letterspaced dim label.
- **Bottom nav** (1c): mono glyph + 9sp letterspaced label, active = trust-blue.

## 2. Mapping the 1c mockup → the five shipped screens

The mockup is aspirational (approval-queue-first IA). **v1 keeps the current IA and read-only
surface**; adopt the visual language only:

| Mock element | v1 treatment |
| --- | --- |
| Status header (lamp/L1 chip/SYNC) | Restyle `StatusBanner`: demo → caution amber chip "DEMO DATA"; live → go lamp + "ENGINE LAST SEEN …" mono; not-paired → dim. The L1 chip may ship (it states a true fact about the engine). |
| Stat tiles (FOUND/DRAFTED/BLOCKED/ESCAPES) | Restyle `MetricCard` to the tile pattern (mono values, letterspaced labels, BLOCKED in `#FF5D5D`, zero-state in `#3DDC97`). Keep the existing 7 counters. |
| Job cards with grade lamps | Restyle `ApplicationCard`/Jobs cards: panel bg, hairline border, grade lamp left. **Grade mapping gate (G-DL-1):** wire score is 0–100 int (= engine total×20); propose A ≥88, B ≥72, C ≥56, D ≥40, F <40 — Brandon confirms before ship. |
| `GATE 14/14 CLAIMS VERIFIED ✓` line | v1 has no claims count on the wire — do NOT fabricate. Show state badge (restyled) instead; claims detail arrives with a future wire field (note for protocol owner). |
| OPEN DRAFT ↗ / SKIP buttons | **DO NOT SHIP** — P3 editing surface. v1 rows open the read-only detail. |
| Remote kill switch (1e/1f/1g) | **DO NOT SHIP** — protocol `kill` is reserved-and-rejected (spec §10 non-goal); a remote halt is a new remote-action surface needing its own gate + protocol work. If ever built: option 1f (hold-to-kill ring) was the phone-appropriate variant per the design doc. |
| Bottom nav QUEUE/JOBS/ACTIVITY/CONTROLS | Keep HOME/APPLICATIONS/JOBS/EVIDENCE; restyle to mono-glyph pattern. IA change is a product decision, not a reskin. |
| `SYNC 12s` | Fine — derive from `sync_state.lastSeenTs` ("SYNC 12s" / "OFFLINE 3h"). |

## 3. Implementation order (one session, post-P4-merge)

1. **Fonts:** bundle IBM Plex Sans + IBM Plex Mono as `res/font` (OFL — include license file;
   subset weights 400/500/600/700 to keep APK small). No network fetch (I7: no trackers).
2. **Theme:** `ui/theme/CockpitTheme.kt` — `darkColorScheme` from §1 tokens + `Typography` (Plex)
   + shapes. Wrap `MaterialTheme` in `MainActivity` with it. The M3 container/on-container pairs
   my P5 a11y pass relied on must keep AA contrast — re-verify the badge containers against the
   new scheme (Accessibility Scanner on device + the lint/Robolectric suite).
3. **Shared components** (`ui/cockpit/`): `StatusLamp`, `ModeChip`, `GradeLamp`, `StatTile`,
   `SectionLabel` (mono letterspaced), `HairlineCard`.
4. **Per-screen adoption** (5 small commits, one screen each, full Gradle ritual per commit):
   Home → Applications → Detail → Jobs → Evidence.
5. **Launcher icon swap:** adaptive foreground = brand radar mark vector, bg `#0A0A0B`
   (closes the last limb of Gate P5-ICON; store `icon.png` already done on `claude/p5-store`).
6. **A11y invariants (from P5, must survive):** `heading()` on titles, merged `MetricCard`
   announcement, `Role.Button` + click label on application rows, worded severity on flag badges,
   "Back" label. The 5 Robolectric assertions in `ScreensFromFixtureTest` enforce this — keep
   them green. Grade lamps must announce the letter AND meaning ("Grade A — strong match"), never
   colour alone. RUNNING pulse must respect reduced-motion.
7. **Ritual + CI green; screenshots recaptured** (store listing shots should show the final skin —
   re-run `docs/store/assets/CAPTURE.md` after the reskin lands, before listing day).

## 4. Gates / open items

| Id | Question | Owner |
| --- | --- | --- |
| G-DL-1 | Grade-letter thresholds from the 0–100 wire score | Brandon (rec in §2) |
| G-DL-2 | Ship the L1 · DRAFTS chip in v1? (true fact, but references engine autonomy the phone can't change) | Brandon |
| note | `claims verified n/n` needs a wire field — protocol owner (post-P3) | — |
| note | Windows-dashboard reskin (mock 1b/1d) is the ENGINE repo's — not this repo's TODO | engine/P6 |

*Written by the P5 session, 2026-07-24. Evidence trail: `docs/P5-Evidence.md` §7.*
