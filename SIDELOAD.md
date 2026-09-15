# Sideloading the Alpha dashboard

A **debug** APK for review. Not a release build, not signed for distribution, and not on Play.

> **`applicationId` is PROVISIONAL** — `app.careerseeker.dashboard`. Play application ids are
> permanent once published, so Brandon confirms it before any upload. Nothing in this repo
> uploads anything.

---

## 1. Build it

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

The build needs a JDK 17+ (Android Studio's bundled JBR works) and an Android SDK with
platform 37. On the machine this was built on:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
```

Reference build from 2026-07-30: **13,180,026 bytes**, sha256
`CD7B8A26A9B0FEE1E1C756159B5BFAC0D256607F6265D4220A4310E2487AB9CE`. Your hash **will differ** —
debug builds are not reproducible (timestamps, debug signing). Size should land within a few KB.

## 2. Install it

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android **8.0** (API 26). If `adb` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a
build signed with a different debug key is already installed — `adb uninstall
app.careerseeker.dashboard` first.

## 3. What you can actually do with it — read this before judging the app

The app opens in **demo mode**, and it is honest about that: every screen carries the banner
**"Demo data — not a live engine"**. Nothing in this build can pair with a real engine yet, and
the reason is not on the phone side — see [`BLOCKED.md`](BLOCKED.md) B-1/B-2.

So the review tour is:

| Screen | What is real | What is fixture |
| --- | --- | --- |
| **Home** | the counter layout and the provenance banner | the seven counter values |
| **Applications** | filter chips derived from states actually present; outcome badges | six applications |
| **Application detail** | read-only rendering of three document kinds, inert text | the documents |
| **Jobs** | the two honesty badges (`repost`, `injection flagged`) | five postings |
| **Evidence** | the audit verdict wording — *engine-verified* / *engine-reported* / *unknown* | seven events |

**Things worth deliberately checking**, because they are the claims this app makes:

1. **Provenance is never absent.** Visit every tab, then open an application. The demo banner
   is on all of them, including the detail overlay. There is no screen that shows data without
   saying where it came from.
2. **No send affordance exists anywhere.** There is no compose, no send, no share-to-mail. The
   protocol has no payload kind that can transmit email, and the UI has no button that implies
   one.
3. **Nothing claims to be connected.** The banner says "demo", or "engine last seen &lt;ts&gt;
   (engine clock)". It never says "connected" or "live", because nothing in this build could
   prove it.
4. **The audit verdict is attributed.** Evidence says "engine-verified" or "not yet reported" —
   never a verdict the phone reached on its own.
5. **Job text is inert.** No links, no WebView, no rich text. Job and recruiter text is
   display-only data.

## 4. What is NOT in this build

- **Pairing** — no QR scan, no device key. Gate `P2-KEYSTORE-FALLBACK` is unanswered and there
  was no device to test Keystore behaviour on (`BLOCKED.md` B-1). The pairing *logic* is built
  and vector-proven in `:core`; the screen is not.
- **Live sync** — nothing opens the relay. `RelayClient` exists and is tested offline.
- **Outcome marking** — outcomes *display*, but marking one sends a device-signed envelope, so
  it is blocked by the same Keystore gate.
- **Pro unlock** — `entitlement_ack` has no body defined in the protocol yet (PQ-A6-1), so the
  app cannot receive one. It is honestly Free with no path to anything else.
- **Document editing** — P3 work, deliberately absent so there is no edit affordance to remove
  later.
- **Play Billing** — no billing code exists. Entitlement is exercised only with signed test
  vectors.

## 5. Uninstall

```bash
adb uninstall app.careerseeker.dashboard
```

The replica database lives in app-private storage and goes with it. Everything in it is
re-syncable from an engine snapshot; nothing is authoritative on the phone.
