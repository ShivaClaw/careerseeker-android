# P-MONEY — decided 2026-07-23

| Product | Price | Notes |
| --- | --- | --- |
| **CareerSeeker** (.exe) | **Free forever** | The Windows engine — *the product*, not a tier. No locked features. |
| **CareerSeeker Dashboard** (.apk) | **$4.99 one-time** | Paid Play app. |
| **CareerSeeker Pro** (IAP) | **$2.99 one-time** | Outcome tracking + desktop Pro panels. |
| **CareerSeeker Cloud** | **$1.99 / month** | Managed inference. Parked; not in this program. |

Gate closed. The build was identical either way, so nothing in P1–P4 changes; what changes
is the **store listing** and, more urgently, the **public pricing copy** (§1).

---

## 1. The live pricing page is now false in three places

`careerseeker.app/pricing/` (source: `Desktop\site-v2\pricing\index.html`) currently says,
verbatim:

1. > "The dashboard — **Our only revenue**."
2. > "It's a one-time purchase, and it's **the only money we ever ask you for**."
3. > "**No subscription exists, so there's nothing to cancel.**"

Against the decision above:

- **(1) and (2) break the moment Pro ships**, not when Cloud does. Pro is a second ask at
  $2.99 and a second revenue line. This is independent of the subscription question and
  arrives at **P6**, which is the nearer deadline of the two.
- **(3) breaks when Cloud ships.** Cloud is a subscription; the sentence says none exists.

Spec success criterion 3 is *"every public promise on careerseeker.app remains literally
true on launch day."* Under the current copy it fails at Dashboard launch. **The rewrite is
a P6 blocker, not a Cloud-era cleanup.**

One promise survives intact and should be protected: *"free to download and free forever.
No subscription, no trial clock, no locked features."* That stays true **only if Pro adds
new panels rather than gating existing ones** — which is the design (spec §6.1) and is
worth re-checking at P4.

## 2. Draft replacement framing

The honest story is still strong; it just has more parts than when it was written. The
load-bearing idea to keep is: **you never have to subscribe.** BYOK stays free forever, and
Cloud is a convenience for people who would rather not manage a provider key.

Proposed structure for the rewrite (copy to be finalised with the P6 site update):

- **The app — $0.** Unchanged. Free forever, no locked features.
- **The fuel — at cost.** Unchanged. You pay your AI provider directly; we take $0.
- **The dashboard — $4.99 once.** Optional Android companion.
- **Pro — $2.99 once.** Optional. Adds outcome tracking on both phone and desktop.
- **Cloud — $1.99/month.** Optional, and the *only* subscription we offer. It exists for
  people who'd rather we handle the AI than bring their own key. Cancel any time; the free
  app keeps working exactly as before.

Replacement for the two broken sentences, in the same voice as the existing page:

> Everything you buy from us once, you own — the dashboard and Pro are one-time purchases,
> not rentals. There is exactly one subscription, Cloud, and it is optional: it exists only
> if you'd rather we handle the AI than connect your own provider. The Windows app is free
> forever either way, and if you cancel Cloud, nothing you've already bought stops working.

## 3. Naming — decided 2026-07-23

**The Windows app is "CareerSeeker".** Not "CareerSeeker Basic". Dashboard, Pro, and Cloud
are named add-ons to it.

"Basic" was briefly on the table and dropped, because it names a *tier* — and a tier
implies a ladder with things withheld at the bottom, which pulls directly against the
strongest promise on the pricing page ("no locked features"). A user or a store reviewer
reading "Basic" reasonably infers "the crippled one", which is the opposite of true here:
the free Windows app is the whole engine.

Applies to user-facing copy, the store listing, and the review notes. No internal SKU needs
the word.

## 4. Consequences to carry forward

- **P4:** `EntitlementService` configures `pro_unlock` as **INAPP at $2.99** (not SUBS).
  The two-product-id abstraction in spec §6.3 still holds; Cloud, when it arrives, is a
  separate SUBS product and does not touch Pro buyers.
- **P5:** Store listing prices Dashboard at $4.99; Pro appears as a $2.99 in-app product.
  Play requires a merchant profile for both.
- **P6:** Pricing-page rewrite ships **with** the launch, per the drift-trap discipline —
  app behaviour, privacy policy, data-safety answers, and public copy are one artifact.
- **Cloud (parked):** introducing a subscription later means Play subscription surface —
  cancel/pause/resume, price-change consent, grace and hold states — none of which Pro
  needs. That asymmetry is a reason to keep Cloud a genuinely separate product rather than
  a Pro tier.
