# Apple / iOS — enrollment runbook and build strategy

**Written 2026-08-11 (sixteenth cloud iteration), at Brandon's request**, after the S2 `seq` slice
and outside the S-ladder. Two audiences: the human to-do list is §1 (nothing in it needs a Mac or a
line of code), the build strategy is §2.

**Status: proposal, not a decision.** Nothing here has been built, and no Apple account, console,
purchase or enrollment action was taken — §1 is entirely Brandon's to execute.

---

## 0. The measurement the strategy rests on

Taken 2026-08-11 on `claude/android-a0-probe`, re-runnable — see `AUDIT-REQUEST.md` **C-IOS-1**.

`:core` is **18 files / ~3,150 lines**. Split by whether they touch the JVM at all:

| | files | lines | ports to iOS |
| --- | --- | --- | --- |
| **zero `java.*`/`javax.*` imports** | 13 | ~2,720 (**86%**) | **unchanged** |
| JCA-bound crypto/encoding | 4 | ~291 | needs a platform seam |
| Google Play entitlement | 1 | 138 | **does not port — see §2.3** |

The 13 that port unchanged are every protocol decision built this window: `PairingFlow` (355),
`RelayClient` (349), `SyncPump` (336), `OutboundQueue` (302), `OutcomeMarking` (278), `PullPolicy`
(221), `Protocol`, `OutboundEnvelopes`, `PairingSession`, `EnvelopeReceiver`, `EntitlementAck`,
`EnvelopeJson`, `ProState`.

Both `:core` dependencies are **already multiplatform**: `kotlinx-serialization-json` and
`ktor-client-core`. `:app` is small — **12 files / 1,200 lines**, five Compose screens plus the Room
replica.

**This is not luck.** `checkCoreIsAndroidFree` (root `build.gradle.kts:17`) has failed the build on
any `import android.`/`import androidx.` in `:core` since P0, and `core/build.gradle.kts` says in
its own comment that the rule exists to protect "the eventual iOS target". The discipline already
paid for most of this port.

---

## 1. Meatspace to-do — Apple, in dependency order

**Start §1.A now regardless of everything else.** It needs no Mac, no code, and no laptop, and it
is the long pole: D&B propagation and Apple's verification are external latency measured in days.

### 1.A — Apple Developer Program enrollment (organization)

Verified against Apple's live pages 2026-08-11; **re-verify at decision time**, per the standing
house rule about policy pages.

1. **Check the D-U-N-S record for Applied Autonomy is exactly right** — legal entity name, address,
   phone. Apple verifies identity, legal-entity status and address against D&B, and a mismatch is
   the most common enrollment delay. If you change anything at D&B, **allow up to 2 business days**
   for Apple to receive the update before enrolling.
2. **Use the legal entity name, not a trade name.** Apple does not accept DBAs, fictitious business
   names, trade names or branches — and this name becomes the **seller name on the App Store**. It
   should match what you used for Google Play.
3. **Get a work email on a domain associated with the organization.** Apple requires this and
   **will not accept a `gmail.com` address** — your current account email is one, so this is the
   item most likely to bite. No P.O. boxes for the address, and a reachable phone number.
4. **Stand up a real website on that domain.** Apple requires it to be publicly available and
   functional, with the domain associated with the organization; **social-media links or a
   minimal-content stub are explicitly rejected**. Judgement call: `careerseeker.app` is a *product*
   site. Safest is an Applied Autonomy page (own domain, or a clearly-attributed company/about page)
   so the org and the domain visibly match.
5. **Create a dedicated company Apple Account with 2FA** — not your personal one. It becomes the
   Account Holder and is unpleasant to move later.
6. **Enrol as an Organization** at `developer.apple.com/programs/enroll` (web or the Apple Developer
   app). **$99/year.** You must have legal authority to bind the entity; as owner of the LLC you do,
   which also avoids the "provide a reference who can confirm your authority" branch.
7. **Expect a verification call or email** from Apple Developer Support, typically to the number in
   the D&B record. Make sure that number reaches you. You review the license agreement and pay
   **after** Apple verifies.

### 1.B — Once enrolled (App Store Connect)

8. **Choose the bundle ID. It is permanent.** Your Android `applicationId` is still recorded as
   **PROVISIONAL** — decide both together so they are consistent, and do it before either store
   record exists.
9. **Fill the App Privacy label.** You are in an unusually strong position: no accounts, no
   analytics, no ads, no third-party SDKs — and CI *enforces* that last one by failing on a tracker
   anywhere in the resolved release classpath. That should map to **Data Not Collected**. Derive it
   from the same source as the Play Data Safety worksheet so the two stores cannot drift.
10. **Export compliance — do not skip this one.** CareerSeeker does real cryptography (AES-256-GCM,
    ECDH P-256, ECDSA P-256), so you must answer it. You will set `ITSAppUsesNonExemptEncryption` in
    `Info.plist`. Encryption built into the OS is typically **exempt** from uploading documentation
    — but **exempt use can still require a year-end self-classification report to the U.S.
    government (BIS)**. Get that determination once from someone qualified, write it down, and reuse
    it for both stores and the Windows build.
11. **TestFlight is your alpha lane.** **Internal testers: up to 100, no Beta App Review** — that is
    the fast path. **External: up to 10,000, but the first build goes through Beta App Review.**
    Builds expire after **90 days**. Before inviting anyone you need beta app description, what to
    test, and a feedback email.

### 1.C — The Mac question (blocks the build, not the account)

12. **Decide how iOS gets built.** Building, signing and uploading iOS requires macOS + Xcode. Two
    routes: **(a)** a Mac (Apple Silicon) for development — simulator, debugging, the thing you
    actually want when a sync bug only reproduces on device; **(b)** **macOS CI runners** (GitHub
    Actions) for build + TestFlight upload, which works headlessly and is the right answer for
    releases either way. **§1.A and §1.B need neither** — run them in parallel now.

### 1.D — Product decisions only you can make

13. **The product name.** Already queued in `STATE.md` as blocking the store listing: `p1-runbook`
    records "the Windows app is **CareerSeeker**, not *Basic*" as decided 2026-07-23, while the
    lineage carrying current work still prints "CareerSeeker **Basic**". **It now blocks two store
    listings.**
14. **Confirm the iOS alpha ships free-tier only, no Pro.** Recommended in §2.3, and it is the
    single largest scope cut available.

---

## 2. Build strategy — two seams, not a rewrite

### 2.1 Seam one: `:core` becomes a Kotlin Multiplatform module

86% of it moves untouched (§0). The work is concentrated in four files, and one of them disappears:

- **`Base64Url.kt` (25 lines) — no platform seam at all.** Replace `java.util.Base64` with the
  Kotlin stdlib's multiplatform `Base64.UrlSafe`. One file leaves the port surface entirely.
- **`SyncCrypto.kt` (137) + `Hkdf.kt` (55) + `PairingDerivation.kt` (74)** — one `expect` interface
  behind which the JVM `actual` is **the existing JCA code, moved verbatim**. That is the important
  property: Android keeps running byte-identical crypto, so this cannot regress the shipping app.
- The iOS `actual` is **CryptoKit**, which covers every primitive v1 uses natively — AES-GCM,
  P-256 key agreement, P-256 signing, HKDF, SHA-256. No third-party crypto dependency, which
  preserves the posture that dropped Tink.

**The shared vectors are what make this safe.** `docs/sync-vectors/v1/` already pins the wire format
for two implementations; an iOS `actual` that passes the same vectors is correct by the same
standard, and a divergence shows up as a failing vector rather than as a field bug. **Port the
vectors first, before any UI.**

### 2.2 Seam two: the platform layer

`:app` is 12 files / 1,200 lines. Only two things are genuinely new on iOS: the **replica store**
(Room is Android-side; iOS needs Room-KMP or SQLDelight — a decision, and it touches only the
replica, never the protocol) and the **UI**. Five Compose screens is small enough that **Compose
Multiplatform** reuses the most, with SwiftUI as the alternative if native feel matters more than
reuse. For an alpha whose job is to prove the sync loop on real hardware, reuse wins.

### 2.3 Cut Pro from the iOS alpha — the sharpest available decision

`Entitlement.kt` verifies a **Google Play** signed purchase record. It has no iOS meaning. Pro on
iOS is StoreKit 2 plus Apple's server-side verification — **and because of your own PQ-A2-4
boundary (the phone is a courier; the engine grants), it also needs an Apple receipt verifier in C#
on the Windows side.** That is a second billing integration across two codebases, to serve an alpha
whose purpose is to prove pairing and sync on a real iPhone.

Shipping free-tier-only also keeps the first Apple review clear of in-app-purchase scrutiny. Do it
after the sync loop is real, not before.

### 2.4 Suggested order

1. `:core` → KMP with the crypto seam; **iOS target passing the shared vectors** (no UI yet).
2. Pairing + pull loop against a **local** relay, on the simulator.
3. Replica store, then the five screens.
4. TestFlight internal build (no Beta App Review) → Brandon and a handful of iPhones.
5. Only then: external TestFlight, and only then: Pro.

**Nothing in steps 1–3 needs the App Store account**, and nothing in §1 needs the code. They are
genuinely parallel, which is the main scheduling point of this document.

---

## 3. The other two goals, and the one human action each needs

- **Windows beta `.exe`** — the artifact comes from `scripts\Verify-Alpha.ps1 -IncludePublish`
  (win-x64 single-file). **The one thing no cloud session can produce**: it needs .NET on your
  machine. Also gated on the merge decisions already queued in `STATE.md` — in particular that the
  full gate is run **on the merged tree**, not on the branches, since #5 and #6 auto-fuse three
  screen files that no gate has ever seen in combination.
- **Android `.apk`** — **you can have this today, without your laptop.** CI builds
  `:app:assembleDebug` and uploads it as artifact **`app-debug`** (`.github/workflows/ci.yml:117-123`,
  14-day retention, `if-no-files-found: error`). The green run on `e6e6dc5` is
  [31495754391](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31495754391) —
  download the artifact and sideload it. **Debug-signed, so it is for you, not for testers**; a
  release build wants the upload keystore and belongs to S7.
- **iOS alpha** — §1 and §2 above.

---

## 4. What this document is not

**No Apple account, console, purchase or enrollment action was taken**, and none will be from a
cloud session — §1 is Brandon's alone. **No iOS code was written**, no KMP conversion was started,
no `:core` file was edited, and no build was attempted: this is a proposal resting on one
measurement (§0) and on Apple's live policy pages read on 2026-08-11.

**The Apple facts carry an expiry.** They were read from `developer.apple.com` on 2026-08-11 and are
policy, which moves. Re-verify at decision time — the same standing rule that governs the Play
target-SDK floor and the AGP/Gradle pins.
