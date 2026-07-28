# Account-day checklist — the mechanical afternoon

**Pre-condition:** the Google Play **organization** account requires **LLC + D-U-N-S in hand**
(D-U-N-S verification in flight as of 2026-07-24). Do not start until both exist.

**Purpose:** when the account lands, listing is a mechanical afternoon of pasting **staged**
artifacts — not a scramble. Every step points at the file that already holds the answer.

> **Re-verify policy on the day.** Every Play fact below is dated 2026-07-24 and drifts quarterly.
> The re-verify URLs live in `docs/P5-Evidence.md` §2 — walk them first and update anything that moved
> before you paste. A stale citation is a finding, not a shortcut.

**Owner legend:** **B** = Brandon only (payments, identity, legal — a session must never do these).
**S** = session/agent can execute. **B+S** = Brandon acts, session prepares/verifies.

---

## Order of operations

| # | Step | Owner | Staged artifact / verified fact |
| --- | --- | --- | --- |
| 1 | **LLC + D-U-N-S in hand** | B | Legal prerequisite for the org account (avoids the 12/14 rule — step 13). |
| 2 | **Register Play Console *organization* account ($25 one-time)** | B | Payment + identity — Brandon only. Choose **Organization**, not Personal. |
| 3 | **Identity / org verification** (D-U-N-S, legal name, address) | B | Google verifies the business entity; can add lead time — expected on the org path. |
| 4 | **Merchant profile** (Payments profile) | B | Required for the paid app **and** the IAP (Monetization-Decision §4). Brandon only. |
| 5 | **Create app** — name "CareerSeeker Dashboard", default lang en-US, app (not game), **paid** | S (B confirms) | `Play-Listing.md` §1. **applicationId `app.careerseeker.dashboard` is permanent** — confirm **P4's applicationId gate is closed** and matches before creating. |
| 6 | **Play App Signing** enrol + upload first **AAB** | B+S | Verify target-API on the day: **API 36+ required for new apps from Aug 31 2026** (Evidence §2.1); app targets **37 → compliant**. Upload format is **AAB** (the APK is only for sideload). |
| 7 | **Store listing** — paste title / short / full description / category / contact / screenshots / feature graphic / icon | S | `Play-Listing.md` §1/§3 (counts 22/72/2,522 — all within limits); `assets/` (feature graphic + **chosen** icon from **Gate P5-ICON**; screenshots from `assets/CAPTURE.md`). |
| 8 | **Data safety form** — paste answers + review notes | S | `Play-Data-Safety.md`. **RED-BOX: the form must match the uploaded AAB's manifest** (§6 there) — re-diff permissions (INTERNET-only vs +CAMERA/+POST_NOTIFICATIONS) against the exact AAB. |
| 9 | **Privacy policy URL** | S | `https://careerseeker.app/privacy/` — already reachable and already covers the relay honestly. The **finalized** delta (Cloudflare named) + full public copy ship at **P6 launch as one artifact** (`Privacy-Policy-Delta.md`); the existing URL suffices for testing/review. |
| 10 | **Content declarations** — IARC questionnaire, ads = none, target audience **18+**, account deletion **N/A (no accounts)**, financial features **no** | S | `Play-Data-Safety.md` §7/§8. State "pairing, not accounts" in the account-deletion field/notes. |
| 11 | **License Key (RSA public)** — copy from Play Console → **hand to P4's config** | B+S | This is the key P4's `GoogleSignedPayloadVerifier` (option C, `Entitlement-Architecture.md`) verifies against. Secrets by name only; do not paste the key into any repo. **Hand-off to P4, not this session's to wire.** |
| 12 | **Create in-app product** `pro_unlock` — **$2.99, INAPP (one-time, not SUBS)** | B+S | Monetization-Decision §4. Needs the merchant profile (step 4). PBL **8+** required by Aug 31 2026 (Evidence §2.3) — P4's billing integration must target it. |
| 13 | **License testers** (Play Console → Setup → License testing) | S | Add the Windows alpha testers' Google accounts so IAP test purchases don't charge. |
| 14 | **Internal testing track** — upload AAB, add testers, verify install + demo mode | B+S | Fastest track; no review wait. Confirms the exact AAB installs and every screen renders (demo mode). |
| 15 | **Closed track + tester recruitment** | B+S | **Verify the 12-tester/14-day rule on the day.** Official page scopes it to "personal accounts created after November 13, 2023" and is **silent on org accounts** (Evidence §2.6). Expectation: the org account is **exempt**. **If the Console nonetheless gates production behind closed testing, the 14-day clock starts here** — recruit ≥12 from the Windows alpha testers immediately; it becomes critical-path to production. Record what the Console actually says. |
| 16 | **Pre-launch report triage** | S | Play runs the AAB on real devices; triage crashes/a11y/security flags. Fold any a11y flags into `Accessibility-Pass.md` §4 (additive fixes only, on `claude/p5-store`). |

---

## Guardrails carried onto the day

- **Draft PRs only; never self-merge; never push code to `main`.** The a11y fixes live on
  `claude/p5-store`; open its draft PR only when Brandon says so.
- **Prohibited-for-agent actions (Brandon only):** the $25 registration and any payment, identity
  verification, merchant/tax profile, and accepting Play Developer Program / any legal agreements.
  A session prepares and verifies; Brandon transacts and consents.
- **One artifact at P6:** public site copy + finalized privacy delta + data-safety answers + app
  behavior go live **together** at production launch, not piecemeal (`Privacy-Policy-Delta.md`).
- **Pricing page rewrite is a P6 launch blocker** (Monetization-Decision §1; Sonnet TODO
  `docs/todo/Pricing-Page-Rewrite.md`) — production launch must not go out while
  `careerseeker.app/pricing/` still says "the only money we ever ask you for" once Pro exists.
- **Nothing cross-scope:** engine-repo work, the Pro screen, P3 editing — **noted for their owner**,
  never done from this lane.

## Two things that must be true before step 6 (upload)

1. **Gate P5-ICON closed** — Brandon picked A/B/C and the chosen 512 icon + adaptive foreground are
   final (`assets/Gate-P5-ICON.md`). Until then the build carries the placeholder.
2. **Screenshots captured** on the Pixel 10 (`assets/CAPTURE.md`) — currently device-gated.

*Cross-refs: all citations `docs/P5-Evidence.md` §2; listing `Play-Listing.md`; data-safety
`Play-Data-Safety.md`; assets `assets/`; a11y `Accessibility-Pass.md`.*
