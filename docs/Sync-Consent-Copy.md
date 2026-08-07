# Sync consent copy — draft for Gate P0-SYNC-COPY

**Status:** draft, awaiting Brandon's gate decision. This wording is privacy-load-bearing:
the app copy, the privacy-policy delta, and the Play data-safety answers must all say the
same thing (spec §7.3 — one artifact). Nothing here ships until gated.

Voice rules applied: plain sentences, no euphemism, never imply the relay can see less
than it can, never imply an edit reached Gmail when only the relay has it.

---

## 1. Desktop — the "Pair phone" opt-in screen (sync is default OFF)

> **See your dashboard on your phone.**
>
> Pairing lets your phone show what this PC is doing — jobs found, applications drafted,
> your documents — and lets you edit drafts from your phone.
>
> Here is exactly what that means for your data:
>
> - Everything sent between this PC and your phone is **encrypted before it leaves this
>   machine**, with keys that exist only on this PC and your phone.
> - It travels through a relay server we run on **Cloudflare**. The relay stores **only
>   encrypted blobs** — it cannot read them, and neither can we, and neither can
>   Cloudflare. It sees a random pairing ID, message sizes, and timestamps. It never sees
>   your name, email, documents, or job list.
> - Relay copies expire automatically within days, and always within 30.
> - Your Gmail access, API keys, and database **never leave this PC**. Your phone never
>   gets them.
> - Unpairing erases the phone's copy and shuts the channel off. You can unpair from
>   either device, any time.
>
> Sync is off until you choose to turn it on.
>
> **[Pair a phone]** **[Not now]**

## 2. Phone — onboarding, before the QR scan

> **Your PC does the work. This is the window.**
>
> No account. No sign-up. Pairing connects this phone to your own PC, and to nothing
> else.
>
> We need the camera for one thing: scanning the pairing code on your PC's screen. It is
> not used for anything else, ever.
>
> What lives on this phone: an encrypted copy of your dashboard and documents, so you can
> read them offline. Erase it any time in Settings → Unpair, or by deleting the app.

## 3. Phone — the honesty line for edits (shown on first edit save)

> Saved. Your edit is on its way to your PC, which updates the Gmail draft.
>
> If your PC is offline, the edit waits (encrypted) until it wakes. The chips on each
> document tell you the truth: **On desktop ✓ / In Gmail ✓ / Queued…** — an edit is not
> in Gmail until you see "In Gmail ✓".

## 4. Privacy-policy delta (careerseeker.app/privacy/ — ships with the app, not after)

Add a section "The phone dashboard and the relay":

- **Where it runs:** the relay is a Cloudflare Worker with Durable Object storage, operated
  by us on Cloudflare's infrastructure. Named explicitly (gate decision, 2026-07-23) —
  being vague about where a privacy product's data physically sits invites exactly the
  suspicion the product exists to avoid.
- What the relay stores: encrypted envelopes it cannot decrypt, keyed by a random pairing
  ID; sizes and timestamps; nothing else. No accounts, no names, no emails.
- **What Cloudflare can see:** the same ciphertext and metadata we can, plus connection
  IPs, which are transient and which we do not retain or log. Cloudflare cannot read
  envelope contents — the keys exist only on the paired PC and phone, and never reach the
  relay or us.
- Retention: every stored envelope expires, at most 30 days.
- What we can see: that *a* pairing exists and how much ciphertext moved. Not whose, not
  what.
- The app contains no analytics, no ads, no trackers, and no crash reporting.
- Erasure: unpair (either side) purges the relay queue and wipes the phone's replica.
- The engine's Gmail access and keys never leave the PC; the phone has no send path.

## 5. Play data-safety mapping (P5 checklist, kept consistent with the above)

| Form question | Answer | Grounded in |
| --- | --- | --- |
| Data collected? | No data collected by the developer | Relay stores ciphertext it cannot read |
| Data shared? | No | Cloudflare is a **processor hosting ciphertext**, not a recipient of user data — no readable data is shared with anyone, and there are no third-party SDKs in the app |
| Data encrypted in transit? | Yes (E2EE — developer cannot read) | §1 copy |
| Deletion mechanism? | Unpair / uninstall wipes replica; relay TTL ≤30d | §1, §4 |
| Account creation? | None — "pairing, not accounts" (state in review notes) | §2 copy |

**Gate decision, 2026-07-23: name Cloudflare, for transparency.** Applied in §1 (in-app
opt-in copy), §4 (privacy policy), and §5 (data-safety framing). The reasoning worth
keeping: naming the host costs nothing when the host cannot read anything, and a privacy
product that is vague about where data physically sits invites exactly the suspicion it
exists to avoid.

One consequence to carry into P5: the Play data-safety "Data shared?" answer must
distinguish a **processor hosting ciphertext** from a **recipient of user data**. The
honest answer stays "No" — nothing readable is shared with anyone — but the review notes
should state the relay arrangement plainly rather than leaving a reviewer to infer it.
