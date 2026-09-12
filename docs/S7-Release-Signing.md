# S7 — release signing, version scheme, and the live Play floor

Partial rung. What is recorded here was executed; what was not done is named as not done.

Deliberately **not** placed under `docs/store/`: that directory exists on `claude/p5-store`, a
sibling branch of this one, and duplicating it here would manufacture a merge conflict on top of the
two-lineage hazard S0 already flagged. The listing copy, data-safety worksheet, privacy delta and
account-day checklist live there and were **not** re-created.

---

## 1. Play target-API floor — verified live, not copied

House rule: version and policy facts are verified against the live source at decision time.

**Checked 2026-08-09** against
<https://developer.android.com/google/play/requirements/target-sdk>:

| Category | Requirement | Deadline |
| --- | --- | --- |
| New apps and app updates | **API 36** | **2026-08-31** (extension to 2026-11-01 on request) |
| Existing apps (to stay available to new users on newer OS) | API 35 | 2026-08-31 |
| Wear OS / Automotive (new) | API 35 | 2026-08-31 |
| Android TV / XR (new) | API 34 | 2026-08-31 |

**This app targets SDK 37**, so it clears the floor with a margin of one API level. The gate record
in the mission (§2.4, "targetSdk 36 from 2026-08-31") is **confirmed correct** by the live page.

Worth noting for planning: the deadline is **23 days** from this check. It does not bite us — but
anything that would tempt a targetSdk *downgrade* between now and then is a launch blocker, not a
build preference.

---

## 2. Upload keystore — generated

Created under mission §3(b), which explicitly permits it.

- **Location:** `%USERPROFILE%\.careerseeker-signing\upload-keystore.jks` — **outside every git
  repository**, not merely gitignored.
- **Alias:** `careerseeker-upload`
- **Algorithm:** RSA 4096, SHA384withRSA self-signed
- **Validity:** 10,000 days from 2026-08-09 — well past Play's requirement that the certificate stay
  valid beyond 2033.
- **Passwords:** generated in-process and written straight to
  `upload-keystore-passwords.txt` in the same directory. **Never printed** — not to a terminal, not
  to a log, not into a commit message. That file's ACL had inheritance removed and is granted to
  `LEGION\bkirk` only.
- **README:** written alongside, covering what the files are, how to print the certificate
  fingerprint without echoing a secret, and why loss is recoverable.

**Why generating this unattended was low-risk.** It is an *upload* key, not the app signing key.
With Play App Signing enrolled, Google holds the app signing key and re-signs every upload; the
upload key only proves who uploaded. A lost or compromised upload key can be reset by Play support.
The irreplaceable key is created by Google at enrolment — do not opt out of Play App Signing.

---

## 3. `versionCode` scheme — proposed, not yet applied

Recorded so the first upload does not improvise. Play requires `versionCode` to increase
monotonically **forever**; it can never be reused or lowered, even across a rollback.

**Proposal — a plain monotonic integer, starting at 1**, incremented once per artifact *uploaded to
Play* (not per build, not per commit).

Rejected alternative: date-derived codes (e.g. `20260809`). They look self-documenting and then
force an awkward jump the first time two builds ship in one day, and they permanently consume the
integer space. A boring counter with the human-readable version in `versionName` has neither
problem.

`versionName` tracks the marketing version (`0.1.0` for the first internal-testing artifact) and is
free to repeat or regress; only `versionCode` is load-bearing.

**Not applied yet** — applying it belongs with the first real release build, and there is no signed
artifact to attach it to (see below).

---

## 4. What was NOT done

- **No release `.aab` was built or signed.** The keystore exists; nothing consumes it. No Gradle
  signing config was added, because wiring one without producing and verifying an artifact would
  leave a config claiming a capability nobody has exercised.
- **No R8/minification pass.** Untouched.
- **No Play Console action whatsoever** — no app created, no Play App Signing enrolment, no listing,
  no internal testing track. Console work is human-only and embargoed for the agent.
- **No screenshots.** They require a device or emulator, which is blocked by **B-4**
  (`sdkmanager` is not installed on this machine).
- **No listing/data-safety/privacy work** — that lives on `claude/p5-store` and was deliberately not
  duplicated.

---

## 5. Re-verification

```powershell
# the keystore exists and is outside any repo (prints no secret)
Test-Path "$env:USERPROFILE\.careerseeker-signing\upload-keystore.jks"
git -C C:\Users\bkirk\Documents\careerseeker-android check-ignore -v -- "$env:USERPROFILE\.careerseeker-signing\upload-keystore.jks"

# certificate details — prompts for the store password; read it from the sibling file, do not echo
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.careerseeker-signing\upload-keystore.jks" -alias careerseeker-upload

# the app's target SDK, against the live floor above
Select-String -Path app\build.gradle.kts -Pattern 'targetSdk'
```

*Expected:* keystore present; `targetSdk = 37` against a live floor of 36.
