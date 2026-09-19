# Post-Quantum Posture — standing architectural directive

**Directive (Brandon, 2026-07-23):** build on post-quantum tools, practices, and
implementations. Q-day assumed by **2029**. Treat quantum-vulnerable primitives as a
dangerous design choice, not a neutral one.

**Status of this document:** the standing reference for every cryptographic decision in
this program and its successors. It records what is actually at risk, what is not, what we
ship today, and the trigger conditions for migrating.

---

## 1. One correction, because it changes where the effort goes

The directive named "elliptic curves, ECDSA, and **SHA-256**" together. Two of those three
are correct. **SHA-256 is not quantum-vulnerable in any operational sense, and treating it
as such would send us chasing the wrong migration.**

- Quantum attacks on hash functions come from **Grover's algorithm**, which gives only a
  *quadratic* speedup: SHA-256 preimage resistance drops from 2²⁵⁶ to ~2¹²⁸ quantum work.
  2¹²⁸ is the security level we deliberately choose for symmetric crypto anyway.
- **Shor's algorithm** is the actual break, and it applies only to problems with hidden
  periodic structure — integer factorisation and discrete logs. That is RSA, ECDH, and
  ECDSA. Hash functions have no such structure; there is no known quantum algorithm that
  breaks SHA-2 the way Shor breaks ECC.
- The decisive evidence is NIST's own: **FIPS 205 (SLH-DSA)** is a *post-quantum signature
  standard* whose security rests entirely on hash-function collision resistance, and six of
  its twelve approved parameter sets are **SHA-2 based** (`SLH-DSA-SHA2-128s` through
  `SLH-DSA-SHA2-256f`). NIST standardised a post-quantum signature scheme built on SHA-2.
  It cannot simultaneously be the case that SHA-256 is quantum-broken.
- Same logic covers **AES-256-GCM**: Grover halves it to ~2¹²⁸ effective, which is why
  NIST maps its PQC security categories to AES-128/192/256 equivalents *with Grover already
  accounted for*. **Our P0-CIPHER decision is already post-quantum-appropriate** — that was
  luck rather than foresight, but it holds.

**What this means practically:** no SHA-3 migration is needed, and no AES change. The
budget goes entirely to **asymmetric** primitives — which is exactly where P1's pairing
handshake sits. Spending it on hashing would cost real work and buy nothing.

## 2. What is actually at risk in this program

| Primitive | Used for | Quantum status | Harvest-now-decrypt-later? |
| --- | --- | --- | --- |
| AES-256-GCM | envelope confidentiality | **Safe** (Grover → 2¹²⁸) | n/a |
| SHA-256 / HKDF-SHA256 | AAD, key derivation, confirm code | **Safe** (§1) | n/a |
| **P-256 ECDH** | pairing key agreement | **Broken by Shor** | **YES — the real exposure** |
| **P-256 ECDSA** | device command signatures | **Broken by Shor** | No (see below) |

Two asymmetric uses, and they carry very different urgency:

**Key agreement is the HNDL exposure.** An adversary who records relay traffic today and
holds it until Q-day can recover the pairing shared secret and decrypt everything that
ever crossed the channel. This is the one that justifies acting before Q-day rather than
at it.

**Signatures are not retroactively forgeable.** Breaking ECDSA in 2029 does not let anyone
forge a 2026 signature *that was already accepted and recorded in the audit chain*. It
lets them forge signatures from that point forward — which matters only if a pairing from
before the migration is still live. Re-pairing rotates keys anyway (§5.3 of the protocol),
so signature migration can lag key-agreement migration without creating retroactive risk.

**Mitigating factors specific to this design**, which lower the HNDL stakes but do not
eliminate them: relay retention is capped at 30 days (spec §8.3) and defaults to 7, so the
harvestable window per pairing is small; the plaintext is job applications and draft
correspondence, whose confidentiality value decays; and re-pairing rotates keys. None of
this argues against migrating — it argues that we are not in an emergency, and can migrate
deliberately rather than by bolting on whatever is available.

## 3. What ships in P1 — and why it is still P-256

Gate P1-CURVE was answered **P-256 both sides**, and that stands for P1. The reasoning is
in the P1 runbook; the post-quantum lens does not overturn it, for three reasons:

1. **The spec's alternative was X25519 — also quantum-broken.** The choice was never
   PQ-vs-not; it was which classical curve. Nothing was given up.
2. **The platform is not ready for PQ-only pairing.** .NET's `MLKem` requires Windows 11
   25H2 / Server 2025 (CNG) or OpenSSL 3.5+, exposed through `MLKem.IsSupported`. Our users
   run whatever Windows they have. A PQ-only handshake would hard-fail on a meaningful
   share of alpha testers, and the fallback path would be… P-256.
3. **PQ-only is not the recommended posture anyway.** NIST and IETF both prescribe
   **hybrid** during transition — classical ECDH *and* ML-KEM combined, so the session is
   safe unless *both* are broken. Shipping ML-KEM alone would be worse practice than
   shipping P-256 alone, because ML-KEM is young and hybrid protects against a classical
   break in the lattice assumption.

So P1 ships P-256, and P1's real post-quantum obligation is **making the migration cheap**
(§4) rather than performing it early and badly.

## 4. What P1 must do now, at near-zero cost

These are protocol-design obligations, folded into P1's existing work items. Skipping them
is what makes a later migration expensive.

1. **Version the handshake independently of the envelope.** The envelope already carries
   `v`; the *pairing* payload gets its own `suite` identifier — `p256-hkdf-sha256` for v1,
   with `p256+mlkem768-hkdf-sha256` reserved. A pairing suite is negotiated once, at
   pairing time, and never mid-session (§7.1's downgrade argument applies here too).
2. **Derive session keys through HKDF from a *concatenated* secret**, not directly from
   the ECDH output. Today: `ikm = ecdh_shared`. Hybrid tomorrow:
   `ikm = ecdh_shared || mlkem_shared`. If P1 derives straight from the raw ECDH result,
   adding ML-KEM later changes the derivation shape and breaks every paired device; if P1
   derives through HKDF over a concatenation of one element, adding a second element is a
   suite bump and nothing else. **This is the single highest-leverage line in this
   document** and it costs one extra function today.
3. **Do not let key sizes leak into the wire format.** Fixed-width fields sized for a
   32-byte P-256 public key will not hold an ML-KEM-768 encapsulation key (1184 bytes).
   Pairing payload fields stay length-prefixed / base64url-variable, and the QR payload
   budget is checked against ML-KEM-768 sizes now — a QR code that cannot physically carry
   the hybrid key would force a pairing-UX redesign at migration time.
4. **Record `suite` in the engine's audit chain** at pairing, so "which cryptography was
   this pairing built on" is answerable later without guessing. When P-256 is deprecated we
   need to find affected pairings, not survey users.

## 5. Trigger conditions for PQ-1 (the hybrid migration)

Not calendar-driven. Migrate when the **first** of these becomes true:

- **`MLKem.IsSupported` is true on the Windows builds our users actually run.** Requires
  .NET 10+ on the engine (currently .NET 8) *and* Windows 11 25H2+. Re-check at each phase
  boundary; this is the gating item today.
- Google Tink or a comparably maintained Android library ships ML-KEM in a pure-JVM
  artifact. BouncyCastle 1.79+ already has ML-KEM/ML-DSA/SLH-DSA and works on Android, so
  the Android side is arguably ready now — **the engine is the blocker, not the phone.**
- NIST IR 8547's schedule advances: it deprecates ECDH/ECDSA P-256 by **2030** and
  disallows them by **2035**. Brandon's 2029 Q-day estimate is more aggressive than NIST's
  own timeline, which is a reasonable posture for a security-sensitive product; either way,
  2030 is the outer bound for having hybrid shipped.

**When triggered:** add `p256+mlkem768-hkdf-sha256`, keep P-256 in the hybrid (do not
remove it — hybrid means both), bump the pairing suite, force re-pairing for existing
devices, and amend the protocol doc + vectors + both implementations in one change, per
the drift-trap rule. Signature migration to ML-DSA follows separately and later, per §2.

## 6. Rules for future systems under this directive

1. **Never ship a new protocol whose key agreement is classical-only unless the platform
   genuinely cannot do hybrid** — and if it cannot, record why, in a document like this one.
2. **Hybrid, not replacement.** Classical + PQ combined until the PQ primitive has the
   track record the classical one has. Do not drop the classical half to look modern.
3. **Symmetric and hash choices are already fine** at 256-bit / SHA-256+. Do not spend
   migration budget there (§1).
4. **Design for crypto-agility over algorithm choice.** Suite identifiers, HKDF-mediated
   derivation, variable-width key fields, and recorded suite provenance are what make the
   next migration cheap. The specific algorithm chosen today matters less than whether it
   can be replaced without a flag day.
5. **Prefer platform-native primitives; make PQ the exception that justifies a dependency.**
   The reasoning that settled P0-CIPHER still holds — but "no new dependency" must not
   become the reason a system stays classical forever. When the trigger conditions in §5
   fire, a vetted PQ library is the right call even though it is a new dependency.
