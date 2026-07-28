# Play Data-Safety dossier — CareerSeeker Dashboard (STAGED, verify on account day)

**Status:** staged for the Play Console Data safety form. **Nothing here is submitted** — there is
no Play Console yet. On account day, the executor pastes these answers into the live form and
**re-verifies every policy citation** (they are dated 2026-07-24 below and drift quarterly).

**One artifact.** The Data safety answers, the privacy policy (`careerseeker.app/privacy/`, staged
in `Privacy-Policy-Delta.md`), and the app's actual behavior must say the **same thing** and ship
in the **same change** (spec §7.3; consent copy §5). Divergence between any two is a launch bug.

> ### ⚠️ RED-BOX RULE — form answers must match the manifest of the exact AAB uploaded
> The Data safety declarations (especially the permissions/data-access implications) must describe
> **the specific AAB you upload**, not this document's snapshot. The manifest **will change** before
> submission (see §6). **Re-diff the uploaded AAB's manifest against §6 on account day** and adjust
> the form before you submit. Google: *"You alone are responsible for making complete and accurate
> declarations."* (answer/10787469, 2026-07-24)

---

## 1. The one fact the whole form turns on

The phone app and the PC engine exchange data **only** as **end-to-end-encrypted ciphertext**
relayed by a Cloudflare Worker + Durable Object that holds the keys to nothing. Google's Data safety
guidance carves this out explicitly:

> "Data that is sent off device, but that is unreadable by you or anyone other than the sender and
> recipient as a result of end-to-end encryption does **not** need to be disclosed."
> — Play Console Help, *Provide information for Google Play's Data safety section*
> (support.google.com/googleplay/android-developer/answer/10787469, verified 2026-07-24)

The relay stores ciphertext keyed by a random pairing id, plus sizes and timestamps, and expires it
within ≤30 days. The keys (`k_e2p`/`k_p2e`) exist only on the paired PC and phone — never on the
relay, never with the developer, never with Cloudflare. Therefore, under Google's own definition,
**the app collects no user data and shares no user data.** Every answer below flows from that, and
each is independently true (no analytics, no ads, no third-party SDK, no accounts).

**Cloudflare is a processor hosting ciphertext, not a recipient of user data.** This distinction is
the single most important thing for a reviewer to understand, so it is stated plainly in the review
notes (§5 here and in `Play-Listing.md`) rather than left to be inferred.

---

## 2. Data collection & sharing — the top-level answers

| Form question (verbatim, answer/10787469, 2026-07-24) | Answer | Ground |
| --- | --- | --- |
| "Does your app collect or share certain types of user data?" | **No** | All off-device data is E2EE ciphertext the developer cannot read → E2EE exception. No analytics/ads/trackers/crash-reporting; no third-party SDKs; no accounts. |
| Data **shared** with third parties? | **No** | Cloudflare is a processor hosting ciphertext, not a data recipient. Nothing readable is shared with anyone. |
| Any data **collected** (even if not shared)? | **No** | The replica lives on the user's own phone; it is not transmitted to the developer. Provider LLM calls happen on the **PC**, not the phone, and never traverse this app. |

Because the top-level answer is "No data collected or shared," Google's per-data-type matrix
(location, personal info, financial info, messages, photos, files, contacts, app activity, etc.)
is **not entered** — there is no collected type to characterize. The walk-through in §3 records why
each plausibly-relevant category is genuinely absent, so a reviewer question has a ready answer.

---

## 3. Per-category walk-through (why "none" is honest, not evasive)

Grounded in the architecture (HANDOFF §2; Checkpoint invariants I1–I7; consent copy §1/§4). For
each category a reviewer might expect a job-seeking app to touch:

| Category | Collected/Shared? | Why not |
| --- | --- | --- |
| **Personal info** (name, email) | No | No accounts, no sign-up ("pairing, not accounts"). The engine's Gmail/OAuth stays on the **PC**; the phone never receives it (consent copy §1). |
| **Financial info** | No | Not a financial-features app. Purchases (Dashboard $4.99 up-front; Pro $2.99 IAP) are handled by **Google Play Billing**, not by the app collecting payment data. |
| **Messages / email** | No | The phone has **no send path** (invariant I1). Draft *bodies* do not ride the wire in v1; only short structured fields do, rendered inert (I4). Nothing is transmitted to the developer. |
| **Photos / files / documents** | No | Documents render from the local encrypted replica. They are never uploaded to developer infrastructure; the relay sees only ciphertext (I2, I5). |
| **App activity / analytics** | No | No analytics, telemetry, or crash reporting. Enforced structurally by a resolved-classpath CI check, not a promise (I7). |
| **Device or other IDs** | No | No advertising ID, no analytics ID. The pairing id is a **random** value the user's own devices mint; it is not a device identifier tied to the person. |
| **Location, contacts, calendar, health, audio, browsing** | No | None accessed. Camera (when it arrives, §6) is used **only** to scan the pairing QR — not to collect images (consent copy §2). |

---

## 4. Security practices & deletion

| Form question (verbatim) | Answer | Ground |
| --- | --- | --- |
| "whether or not all of the user data collected by your app is encrypted in transit" | **Yes — encrypted in transit** (E2EE; the developer cannot read it) | AES-256-GCM v1 envelopes; keys only on PC + phone (Sync-Protocol §5; consent copy §1). Mark the E2EE indicator where the form offers it. |
| "whether or not you provide a way for users to request that their data is deleted" | **Yes — user-initiated, immediate** | There is no developer-held account to delete. **Unpair** (from either device) purges the relay queue and wipes the phone's replica; **uninstall** removes the replica; the relay's TTL expires every stored envelope within ≤30 days regardless (consent copy §1/§4). No deletion *request* to the developer is needed because the developer holds nothing. |
| Account deletion URL (Play's account-deletion policy) | **N/A — no accounts** | State "pairing, not accounts" in the review notes. There is no account to delete and no account-deletion endpoint because none is created. |
| Independent security review / data-encryption details | State the E2EE architecture in review notes | The relay's blindness is testable (storage-schema test asserts ciphertext-only columns; live pull inspection, P1-Evidence §3). |

---

## 5. Review notes text (paste into the Data safety review notes / app-access notes)

> CareerSeeker Dashboard pairs 1:1 with the user's own Windows PC. There are **no accounts** and no
> sign-up — the app pairs by scanning a QR code shown on the user's PC ("pairing, not accounts").
>
> All data between PC and phone is **end-to-end encrypted** before it leaves either device. It is
> relayed by a Cloudflare Worker with Durable Object storage that we operate; the relay stores
> **only encrypted blobs it cannot read**, keyed by a random pairing id, plus message sizes and
> timestamps, expiring within ≤30 days. The encryption keys exist only on the paired PC and phone —
> never on the relay, never with us, never with Cloudflare. **Cloudflare is a processor hosting
> ciphertext, not a recipient of user data.** Under Google's Data safety E2EE exception, this
> off-device data does not need to be disclosed as collected or shared; we have answered "No data
> collected / No data shared" on that basis, and every other basis is independently true (no
> analytics, no ads, no trackers, no crash reporting, no third-party SDKs).
>
> **How a reviewer sees the app:** it launches into **demo mode** with clearly-labeled sample data —
> no pairing, no PC, and no account are required to review every screen. Deletion: unpair (either
> device) or uninstall wipes the phone's local copy; the relay queue is purged and TTL-expires
> regardless.

---

## 6. Permissions truth-matching — TWO states, and which AAB you upload decides

The Data safety form's answers about device access must match the **manifest of the uploaded AAB**.
The manifest changes between now and submission:

| Permission | In the manifest **today** (`claude/p2-replica`) | Arrives with… | Data-safety implication |
| --- | --- | --- | --- |
| `INTERNET` | **Yes** | already present | Enables the E2EE relay connection only. No data collected. |
| `CAMERA` | **No** | the **P2 device-finale pairing UI** (CameraX + ML Kit QR scan) | Used **only** to scan the pairing QR. Declare in-form as QR-only; no image collection. Add the camera usage rationale. |
| `POST_NOTIFICATIONS` (runtime, Android 13+) | **No** | likely with the pairing UI / live transport | Local notifications for liveness only; no data collected. Runtime-requested. |

> ### ⚠️ Which state is true depends on the exact AAB.
> If you submit **before** the pairing UI merges, the app declares `INTERNET` only and the form must
> not claim camera use. If you submit **after**, `CAMERA` (and probably `POST_NOTIFICATIONS`) are
> present and the form + listing + privacy policy must all reflect that. **Re-diff the uploaded AAB
> manifest here before submitting.** Neither state changes the "No data collected/shared" answer —
> camera-for-QR collects no data — but the *permissions declared* differ and reviewers cross-check.

Nothing from Play's restricted-permission lists is (or will be) requested. No `QUERY_ALL_PACKAGES`,
no location, no SMS/Call Log, no `MANAGE_EXTERNAL_STORAGE`, no accessibility-service, no
foreground-service special types beyond what live transport may later justify (re-verify then).

---

## 7. Other content declarations (staged answers)

| Declaration | Answer | Ground |
| --- | --- | --- |
| **IARC content rating** | Utility/productivity; **no** objectionable content (no violence, sex, gambling, drugs, profanity, user-generated content shared between users) | It is a personal dashboard for one's own PC. IARC questionnaire prep in §8. |
| **Ads** | **None** — the app contains no ads and no ad SDKs | Consent copy §4; classpath CI check (I7). |
| **Target audience & content** | **18+** (adult job seekers) — avoids the Families policy surface | Spec §5.6. Do not select any child/teen age band. |
| **Financial features** | **Not** a financial-features app | No lending, banking, crypto, trading, or payment-collection features. |
| **Account deletion** | N/A — no accounts | "pairing, not accounts" (§4). |
| **Government / health / other special app types** | None apply | Utility app. |
| **Data access (App access instructions for review)** | Provide the **demo-mode** note: no login needed; every screen renders from labeled sample data on first launch | This is how a reviewer reaches full functionality without a PC or account (see `Play-Listing.md` reviewer notes). |

---

## 8. IARC questionnaire prep (typical question → staged answer)

The IARC questionnaire is completed in-console and issues ratings for multiple boards at once.
Staged answers, all "No/None" for the sensitive axes:

- Violence / realistic violence / fantasy violence: **None**.
- Sexual content or nudity: **None**.
- Profanity / crude humor: **None**.
- Controlled substances (drugs, alcohol, tobacco), gambling (real or simulated): **None**.
- Fear / horror: **None**.
- **User-generated content / user-to-user communication / sharing:** **None** — the app pairs to the
  user's *own* PC; there is no social surface, no chat, no content shared with other users.
- Personal information sharing / location sharing with other users: **None**.
- Digital purchases: **Yes** — a paid app plus one in-app purchase (Pro, $2.99). Declare in-app
  purchases where asked; this drives the "contains in-app purchases" badge, which is accurate.
- Content controlled/created by users, or unmoderated content: **None**.

Expected outcome: a low/everyone-adjacent utility rating. We nonetheless set the store target
audience to **18+** (§7) for policy-surface reasons, independent of the IARC rating.

---

## 9. Exit state for this dossier

- [x] Data-collection / sharing answers grounded in the verified E2EE exception (dated citation).
- [x] Security (encrypted-in-transit) and deletion answers grounded in the architecture.
- [x] Review-notes text drafted with the processor-hosting-ciphertext + pairing-not-accounts framing.
- [x] Permissions truth-matching documents **both** manifest states + the red-box "match the AAB" rule.
- [x] IARC / ads / 18+ / not-financial / account-deletion-N/A staged.
- [ ] **Account day:** re-verify each citation; diff the uploaded AAB manifest against §6; paste.

*Cross-refs: citations in `docs/P5-Evidence.md` §2; privacy alignment in `Privacy-Policy-Delta.md`;
reviewer notes mirrored in `Play-Listing.md`.*
