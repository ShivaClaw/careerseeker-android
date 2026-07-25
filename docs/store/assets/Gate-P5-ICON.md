# Gate P5-ICON — RESOLVED: use the official CareerSeeker logo

**Status:** **RESOLVED 2026-07-24.** Brandon supplied the finalized brand in Claude Design
(project "CareerSeeker Site Reskin" → `CareerSeeker Logo.dc.html`; source vector
`uploads/site-v2-backup-2026-07-22/assets/logo.svg`). The earlier three drafted options
(compass / paired / monogram, slate+blue) are **superseded and removed** — they were off-brand.

## The brand (authoritative)

- **Mark:** a radar / targeting compass — concentric rings + tick marks + a sweeping needle.
- **Palette:** near-black field `#0A0A0B`, primary lime `#A3FF12`, accent yellow `#FFE500`,
  text `#EAECF2`, muted `#9A9EB4`.
- **Type:** Space Grotesk (display) · Inter (body) · IBM Plex Mono (labels).

## What was produced (this session, from the official logo)

- `icon.svg` / `icon.png` — **512×512** Play hi-res icon: the radar mark on `#0A0A0B`, inset so
  the adaptive-icon mask (central ~66/108) can't clip the outer ring. Rendered + viewed.
- `feature-graphic.svg` / `feature-graphic.png` — **1024×500**, rebranded (logo + wordmark).
  (The wordmark uses a system-sans fallback in this render; embed Space Grotesk or convert text to
  paths for the final production PNG.)

## Remaining (small, app-side) — needs the scope call on the Design Language

The **in-app adaptive launcher icon** is still the template placeholder
(`app/src/main/res/drawable/ic_launcher_foreground.xml`, `res/values/colors.xml`
`ic_launcher_background = #1B2733`). Replacing it with the official mark is a contained,
additive asset change (new foreground vector + background `#0A0A0B`), run through the full Gradle
ritual. **It is folded into the "CareerSeeker Design Language" implementation decision** (see the
scope question raised 2026-07-24) — because the launcher icon should land with the app's brand
theming, not piecemeal. Until that's decided, the placeholder stays in the build; the **store**
icon/feature-graphic above are done and unblock the listing.

*Gate owner: Brandon. Brand = decided. Only the in-app launcher-drawable swap remains, pending the
Design-Language scope decision.*
