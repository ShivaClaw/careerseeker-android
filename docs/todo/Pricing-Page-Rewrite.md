# TODO — pricing-page rewrite (P6 launch blocker)

**For:** a future session (Sonnet). **Standalone.** **Do not deploy** — this is a content
change to a live public site; land it as a reviewed change and let Brandon flip it live.

## Why this is a blocker, and when

P-MONEY was decided ([Monetization-Decision.md](../Monetization-Decision.md)): Dashboard
**$4.99** one-time, Pro **$2.99** one-time, Cloud **$1.99/mo**, Windows app free forever and
named just **"CareerSeeker"** (not "Basic").

The live pricing page (`careerseeker.app/pricing/`) is now **false in three places**. Spec
success-criterion 3 requires every public promise to be literally true on launch day, so
these must be fixed **before the Dashboard ships (P6)** — not deferred to Cloud:

1. > "The dashboard — **Our only revenue**." → false once Pro ships (a second revenue line).
2. > "It's a one-time purchase, and it's **the only money we ever ask you for**." → false once
   Pro ships (a second ask). **Independent of Cloud** — this is the nearer deadline.
3. > "**No subscription exists, so there's nothing to cancel.**" → false once Cloud ships.

The load-bearing promise to **keep true**: *"free to download and free forever. No
subscription, no trial clock, no locked features."* That survives **only if Pro adds panels
rather than gating existing ones** (it does — spec §6.1). Do not write copy that implies the
free app loses anything.

## Where the file is

**Source:** `C:\Users\bkirk\Desktop\site-v2\pricing\index.html` (this is the deployed
site's source; the site is Cloudflare Pages — see the site-deploy memory / handoff for the
deploy path and the functions-CWD trap). The page is plain HTML; match its existing voice
and structure exactly.

## The rewrite (drafted — refine, don't reinvent)

The honest story survives having more parts. Load-bearing idea: **you never have to
subscribe.** BYOK stays free forever; Cloud is an optional convenience.

Proposed structure:

- **The app — $0.** Unchanged. Free forever, no locked features.
- **The fuel — at cost.** Unchanged. Pay your AI provider directly; we take $0.
- **The dashboard — $4.99 once.** Optional Android companion.
- **Pro — $2.99 once.** Optional. Outcome tracking on phone and desktop.
- **Cloud — $1.99/mo.** Optional, the *only* subscription. For people who'd rather we handle
  the AI than bring their own key. Cancel any time; the free app is unaffected.

Replacement for the two broken sentences, in the page's voice:

> Everything you buy from us once, you own — the dashboard and Pro are one-time purchases,
> not rentals. There is exactly one subscription, Cloud, and it is optional: it exists only
> if you'd rather we handle the AI than connect your own provider. The Windows app is free
> forever either way, and if you cancel Cloud, nothing you've already bought stops working.

## The drift-trap rule (critical)

Per spec §7.3, **app behaviour, the privacy policy, the Play data-safety answers, and this
public copy are ONE artifact** — change them together or they contradict. The privacy /
data-safety wording already drafted for the relay lives in
[Sync-Consent-Copy.md](../Sync-Consent-Copy.md) (Cloudflare is named there per Brandon's
2026-07-23 decision). When the pricing copy changes, re-check that these still agree.

## Also verify

- The `/dashboard/` page still promises "exactly this app" — keep it aligned; it gets real
  screenshots + Play link at listing time (P5).
- Grep the whole site for the three false phrases — they may appear on more than the pricing
  page (home, trust pages).

## Done when

- [ ] The three false statements are gone from every page that carried them.
- [ ] The free-forever / no-locked-features promise is still literally true.
- [ ] Windows app is called "CareerSeeker" everywhere (no "Basic").
- [ ] Pricing copy, privacy policy, and data-safety framing agree (§7.3).
- [ ] Reviewed change, **not** deployed — Brandon flips it live with the P6 launch.
