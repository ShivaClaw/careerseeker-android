# Play Listing package — CareerSeeker Dashboard (STAGED)

**Status:** staged for paste into the Play Console store listing on account day. Character limits
verified against Play Console Help (answer/9866151 family) and counted below. **Nothing submitted.**

**Naming canon (enforced):** the Windows app is **"CareerSeeker"** — the product — never "Basic".
**Dashboard** and **Pro** are named add-ons. Do not let "Basic" appear in any user-facing string
(Monetization-Decision §3).

**Honesty fence for v1:** the Dashboard v1 is **read-only** (live view + offline replica + evidence
trail). The phone does **not** edit drafts or manage replies in v1 — that is P3, and "reply
management" is permanently out of Pro's scope (spec §6.1). The copy below claims none of it. If P3
lands **before** submission, add the clearly-marked editing block in §5; otherwise ship as written.

---

## 1. Listing metadata

| Field | Value | Limit / note |
| --- | --- | --- |
| **App name (title)** | `CareerSeeker Dashboard` | 22/30 chars ✓ |
| **Short description** | `See your CareerSeeker PC live: encrypted, read-only, no account, no ads.` | 72/80 chars ✓ |
| **Full description** | §3 below | 2,522/4000 chars ✓ (counted 2026-07-24) |
| **App category** | **Productivity** (primary); Business acceptable alternate | Job-search companion utility |
| **Tags** | productivity, job search, privacy (choose from Play's fixed tag set in-console) | — |
| **Contact email** | `support@careerseeker.app` | Already routed (spec §5.7) |
| **Website** | `https://careerseeker.app` | — |
| **Privacy policy URL** | `https://careerseeker.app/privacy/` | Updated per `Privacy-Policy-Delta.md`, ships same change |
| **Price (app)** | **$4.99** one-time (paid app, up-front) | P-MONEY; requires merchant profile |
| **In-app product** | `pro_unlock` — **$2.99 INAPP** (one-time, not SUBS) | P4 creates it; PBL 8+ (Evidence §2.3) |
| **applicationId** | `app.careerseeker.dashboard` | Permanent once created — confirm P4's gate first |
| **Default language** | English (US) | — |

**Short-description alternates** (all ≤80, in case the chosen one needs a tweak):
- `A private, encrypted window into your CareerSeeker PC. Read-only, no account.` (77)
- `Your job-search PC, on your phone. Encrypted, read-only, no account, no ads.` (76)
- `Your CareerSeeker PC on your phone: read-only, encrypted, no account.` (69)

---

## 2. What the app must *not* claim (v1 truth fence)

- ✗ "Edit your drafts from your phone" — that is P3, not shipped.
- ✗ "Reply management" / "send follow-ups" — permanently out of scope (spec §6.1; no send path, I1).
- ✗ Any price on the free Windows app — it is **free forever** (keep that promise intact).
- ✗ Any claim that contradicts the live `/pricing/` promises still in force (Evidence §3).
- ✓ Demo mode, live view, offline replica, evidence/audit trail, E2EE, no account, optional Pro
  outcome tracking — all true and shipped/decided.

---

## 3. Full description (paste as-is; **2,522/4000 chars**, verified 2026-07-24)

```
CareerSeeker Dashboard is the phone companion to CareerSeeker, the free Windows app that helps you discover, evaluate, and prepare job applications. Your PC does the work. This is the window.

WHAT YOU SEE
• Live view — when your phone is paired to your PC, watch your job search as it happens: postings discovered, applications drafted, fit and legitimacy scores, and interview prep.
• Works offline — an encrypted copy of your dashboard lives on your phone, so every screen still reads when your PC is asleep or you are on a plane.
• The evidence trail — CareerSeeker keeps a tamper-evident, hash-chained log of what the engine did and why. The Dashboard shows it to you, honestly, including when a check hasn't run yet.

PRIVATE BY DESIGN
• No account. No sign-up. You pair the phone by scanning a code on your PC's screen — and to nothing else.
• End-to-end encrypted. Everything between your PC and phone is encrypted before it leaves either device, with keys that exist only on your two devices. It travels through a relay we run on Cloudflare that stores only encrypted blobs it cannot read — and neither can we, and neither can Cloudflare.
• No ads. No trackers. No analytics. No crash reporting. The app watches you as little as it possibly can, which is not at all.
• Your data stays put. Your Gmail access, API keys, and database never leave your PC. The phone holds only an erasable, encrypted copy of what you choose to see.
• Erase any time. Unpair from either device, or uninstall, and the phone's copy is gone; the relay's copy expires within days, always within 30.

TRY IT BEFORE YOU PAIR
The Dashboard opens in demo mode with clearly-labeled sample data, so you can see every screen before you connect anything. Real data always replaces the demo the moment you pair.

READ-ONLY, ON PURPOSE (v1)
This first version is a read-only window: it shows you what your PC is doing. It does not send email and has no way to. Editing from the phone is a planned, separate capability — this version does not claim it.

CAREERSEEKER PRO (optional, one-time)
Pro adds outcome tracking — mark each application sent, replied, interview, or offer, and see your funnel and rates over time, on both phone and desktop. Pro only adds; it never locks anything the free app already does.

REQUIRES THE FREE WINDOWS APP
CareerSeeker Dashboard pairs with the CareerSeeker Windows app. Get it free at careerseeker.app — free to download and free forever, no subscription, no locked features.

Questions: support@careerseeker.app
```

> **Char count:** verified in §7 (must be ≤4000). Re-count if you edit the block.

---

## 4. Reviewer notes (the most load-bearing text in the listing — paste into "App access" / review notes)

> **No login is required to review this app.** CareerSeeker Dashboard launches into **demo mode**
> with clearly-labeled sample data on first launch — every screen (Home, Applications, Application
> detail, Jobs, Evidence) is fully visible with no account, no pairing, and no PC. A demo-mode
> banner is shown; this is intentional and honest.
>
> **What it is:** a read-only phone companion to the user's own Windows PC running the free
> CareerSeeker app. In normal use the user pairs the phone by scanning a QR code shown on their PC.
> Pairing is 1:1 with their own machine — **there are no accounts and no sign-up** ("pairing, not
> accounts").
>
> **Data:** all PC↔phone data is **end-to-end encrypted**; it is relayed by a Cloudflare Worker that
> stores only encrypted blobs it cannot read (a **processor hosting ciphertext**, not a recipient of
> user data). The app contains no ads, trackers, analytics, or crash reporting, and no send path.
>
> **In-app purchase:** one optional item, CareerSeeker Pro ($2.99, one-time), which adds outcome
> tracking; it does not gate any existing functionality.
>
> Contact for review questions: support@careerseeker.app.

---

## 5. Conditional editing block — add ONLY if P3 (phone editing) lands before submission

> *(Do not paste unless the doc-edit capability is actually in the submitted AAB and CI-green.)*
> EDIT ON THE GO — With your PC paired, you can edit your drafts from your phone. Saves sync back to
> your PC over the same encrypted channel; a status chip on each document tells you the truth about
> where the edit is — On desktop, or In Gmail — and an edit is never "in Gmail" until it says so.

If this block ships, the §3 "READ-ONLY, ON PURPOSE" paragraph must be removed in the same edit, and
the Data-safety/privacy copy re-checked for the phone→PC edit path. Until then, v1 stays read-only.

---

## 6. Screenshot & graphic slots (filled from `docs/store/assets/`)

- **Phone screenshots (min 2, max 8):** the five demo-mode screens captured on the Pixel 10
  (`assets/CAPTURE.md`). Order for the listing: Home → Applications → Application detail → Jobs →
  Evidence. The demo-mode banner in the shots is fine and honest — no need to hide it.
- **Feature graphic (1024×500):** `assets/feature-graphic.svg` → PNG.
- **App icon (512×512):** `assets/icon.png` — the **official CareerSeeker radar logo** (Gate P5-ICON
  RESOLVED). In-app adaptive launcher swap is pending the Design-Language decision (`Gate-P5-ICON.md`).

---

## 7. Character-count verification (2026-07-24)

Counted with Python `len()` on the exact strings (Play counts Unicode characters, not bytes):
**title 22/30, short 72/80, full 2,522/4000.** All within limits. Re-count if you edit any string
(the em dash and other non-ASCII glyphs count as one character each but multiple bytes — count
characters, not bytes).

*Cross-refs: prices in `Monetization-Decision.md`; reviewer-notes framing mirrors
`Play-Data-Safety.md` §5; promise-truth check in `P5-Evidence.md` §3 (finding P5-FIND-1).*
