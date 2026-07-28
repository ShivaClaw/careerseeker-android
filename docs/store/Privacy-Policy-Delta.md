# Privacy-policy delta + `/dashboard/` copy — CareerSeeker.app (STAGED, do NOT deploy)

> ### ⚠️ ONE ARTIFACT — nothing on this page goes live before P6
> App behavior + Play Data-safety answers + this privacy policy + this site copy are **one
> artifact**; they ship in the **same change** at launch (P6), never piecemeal (spec §7.3;
> Monetization-Decision §4). This file **stages** publish-ready copy. **Do not deploy it.** Deploying
> the relay/privacy language before the app is live would make a promise the store can't yet see.

**Anchor = the LIVE site, not the repo draft.** Ground truth is the deployed
`careerseeker.app/privacy/` and `/dashboard/` (captured verbatim in `P5-Evidence.md` §3 on
2026-07-24), **not** the engine repo's `docs-site/privacy.md`, which is an older, divergent draft
("L1 Drafts alpha v0.1"). Reconciling repo-source ↔ live is the site owner's job at P6 (noted, not
fixed here — P5 does not touch the engine repo).

---

## 1. Privacy-policy delta — new section for `careerseeker.app/privacy/`

The live policy already anticipates the relay ("mobile sync relay … end-to-end encrypted payloads
that we cannot read"; "We operate no servers that store user data"; footer "no trackers, no cookies,
no analytics"). This delta **finalizes** that anticipation into a shipping section, names Cloudflare
(gate decision 2026-07-23), and states retention and erasure precisely. It replaces/absorbs the live
policy's "planned cloud component" language at launch.

### Publish-ready copy (drop-in section)

> ## The phone dashboard and the relay
>
> CareerSeeker Dashboard is an optional Android app that shows you what your CareerSeeker PC is
> doing. It pairs directly to your own PC. **There is no account and no sign-up** — you pair by
> scanning a code shown on your PC's screen.
>
> **Everything between your PC and your phone is end-to-end encrypted** before it leaves either
> device, with keys that exist only on those two devices.
>
> - **Where it runs.** Encrypted messages travel through a relay we operate — a Cloudflare Worker
>   with Durable Object storage. We name our host deliberately: being vague about where a privacy
>   product's data physically sits invites exactly the suspicion the product exists to avoid.
> - **What the relay stores.** Only encrypted blobs it cannot read, keyed by a random pairing ID,
>   along with message sizes and timestamps. No accounts, no names, no emails, no job data.
> - **What Cloudflare can see.** The same ciphertext and metadata we can, plus connection IP
>   addresses, which are transient and which we do not retain or log. Cloudflare cannot read the
>   contents of any message: the keys exist only on your paired PC and phone, and never reach the
>   relay, us, or Cloudflare.
> - **What we can see.** That *a* pairing exists and how much ciphertext moved — not whose, and not
>   what.
> - **Retention.** Every stored envelope expires automatically, at most 30 days.
> - **No analytics, no ads, no trackers, no crash reporting** in the app.
> - **Erasure.** Unpair from either device, or uninstall the app, and the phone's encrypted copy is
>   removed and the relay's queue for that pairing is purged. Copies expire on their own regardless.
> - **Your keys stay put.** Your Gmail access, API keys, and database never leave your PC. The phone
>   never receives them, and the phone has no way to send email.

### Notes for whoever publishes it (P6)

- Update the policy's **Last updated** date and, if the versioning line is kept, the product-version
  string, in the same commit that ships the app.
- The live policy's existing "Planned Cloud Component" / "future relay" wording should be replaced by
  the section above (present tense) — don't leave both, or the page contradicts itself.
- Keep the existing "no trackers, no cookies, no analytics" footer — it stays true.
- The Play **Data-safety** answers (`Play-Data-Safety.md`) and this section must match word-for-fact:
  E2EE, ciphertext-only relay, ≤30-day TTL, no accounts, erase-by-unpair. Re-diff both on the day.

---

## 2. `/dashboard/` page copy refresh (STAGED)

The live `/dashboard/` page carries the slot we fill — "Screenshots and the Google Play link will
appear here when the app enters review" — and the promise "One-time purchase, and the only money we
ever ask you for" and the "no trackers, no cookies, no analytics" line.

> ### ⚠️ P5-FIND-1 — the live `/dashboard/` copy currently over-promises v1
> The live page says the Android dashboard does "application drafting, reply management, and
> interview scheduling — all accessible live from a mobile device." Two problems for a truthful
> launch: (a) v1 is **read-only** — the phone does not draft or edit (that's P3); (b) "reply
> management" points at the reply/send surface that is **permanently out of scope** (spec §6.1; no
> send path). The refreshed copy below describes the shipping read-only v1 honestly. **Correcting
> the live page is a P6 one-artifact edit** for the site owner — staged here, not deployed.

### Publish-ready `/dashboard/` body (read-only v1, honest)

> # The dashboard
>
> Your CareerSeeker PC does the work. **CareerSeeker Dashboard** is the window — a private Android
> app that shows you what your PC is doing, live, and keeps working offline.
>
> - **See it live.** When your phone is paired to your PC, watch jobs get discovered, applications
>   get drafted, and scores land — as it happens.
> - **Read it anywhere.** An encrypted copy lives on your phone, so every screen reads even when your
>   PC is asleep.
> - **Trust what you see.** The dashboard shows CareerSeeker's tamper-evident evidence trail —
>   including, honestly, when a check hasn't run yet.
> - **Private by design.** No account. End-to-end encrypted through a relay we run on Cloudflare that
>   stores only encrypted blobs it cannot read. No ads, no trackers, no analytics.
>
> This first version is **read-only** — it shows you your PC; it doesn't send email and has no way
> to. Editing from the phone is a planned, separate capability.
>
> **CareerSeeker Pro** (optional, one-time) adds outcome tracking — sent, replied, interview, offer —
> on both phone and desktop. It only adds; it never locks anything the free app already does.
>
> *[Screenshots]* · *[Get it on Google Play — link goes live at launch]*
>
> The Windows app stays free to download and free forever. *(This line must match `/pricing/`; the
> pricing-page rewrite is P6's, per Monetization-Decision §1 — do not restate the soon-to-change
> "only money we ever ask you for" sentence here.)*

### Slots to fill at listing time

- **Screenshots:** the five Pixel-10 demo-mode captures (`assets/CAPTURE.md`), same order as the
  Play listing.
- **Play link:** replace the placeholder with the real store URL once the app is in review/live.
- **Pricing line:** align to whatever the P6 pricing rewrite lands; until then keep only the
  still-true "free forever" promise, not the sentences Monetization-Decision §1 flags as breaking.

---

## 3. Do-not-deploy checklist (for the P6 launch change)

- [ ] App is live/in-review in Play **before** this privacy section and `/dashboard/` copy go public.
- [ ] Privacy section, Data-safety answers, and app behavior are word-for-fact aligned (re-diff).
- [ ] Live `/pricing/` reconciled by the P6 rewrite (Sonnet TODO `docs/todo/Pricing-Page-Rewrite.md`)
      so no page still says "only money we ever ask you for" once Pro ships.
- [ ] Repo `docs-site/privacy.md` ↔ live reconciled by the site owner (engine/P6 — not P5).
- [ ] "Last updated" date bumped; placeholder Play link and screenshots filled.

*Cross-refs: source copy in `Sync-Consent-Copy.md` §4; live snapshot + P5-FIND-1 in
`P5-Evidence.md` §3; data-safety alignment in `Play-Data-Safety.md`.*
