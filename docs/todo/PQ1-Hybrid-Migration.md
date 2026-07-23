# TODO — PQ-1: hybrid post-quantum key agreement

**For:** a future session (Sonnet). **Standalone** — everything needed is here or linked.
**Prereq gate:** the engine must be on **.NET 10** (currently .NET 8). Do not start the
crypto until that is true; §1 is the gate check.

## Why this exists

Brandon's standing directive ([Post-Quantum-Posture.md](../Post-Quantum-Posture.md)): build
post-quantum, Q-day assumed 2029. P1 shipped **ECDH P-256** pairing, which is
harvest-now-decrypt-later exposed — a recorded relay transcript can be opened after Q-day.
PQ-1 adds a **hybrid** (classical + ML-KEM) so the session key is safe unless *both* are
broken. Hybrid, never PQ-only — ML-KEM is young and NIST/IETF prescribe hybrid during
transition.

**Do not touch AES-256-GCM, SHA-256, or HKDF.** They are quantum-fine (Grover is only a
quadratic speedup; FIPS 205 is a *post-quantum* standard built on SHA-2). The entire job is
the asymmetric key agreement. See the posture doc §1 if this is surprising.

## 1. Gate check before any code

- [ ] Engine targets **.NET 10+**. `MLKem` is native there (`System.Security.Cryptography`),
      but needs **Windows 11 25H2+ / Server 2025** (CNG) or **OpenSSL 3.5+**. Guard every use
      with `MLKem.IsSupported` and fall back to P-256-only pairing when false — a hybrid that
      hard-fails on a tester's older Windows is worse than classical.
- [ ] Android side is already viable: BouncyCastle 1.79+ has ML-KEM and runs on Android. It
      enters **`:app`**, never `:core` (keep `:core` dependency-free). Alternatively wait for a
      pure-JVM ML-KEM in a maintained lib.

If .NET 10 is not yet the engine's target, **stop and tell Brandon** — this is his call, not
a code change to force.

## 2. The design is already built for this (do not re-architect)

P1 deliberately made the migration a *suite bump*. Verify these still hold, then extend:

- The pairing payload carries `suite`. v1 = `p256-hkdf-sha256`; the reserved name is
  **`p256+mlkem768-hkdf-sha256`** (already in `Protocol.SUITE_HYBRID_RESERVED` on both sides).
- Session keys derive from **`ikm = concat(shared secrets)`** — one element today. The hybrid
  appends the ML-KEM shared secret: `ikm = ecdh_shared || mlkem_shared`. This is why the
  migration is cheap; do not change the HKDF chain, only what feeds `ikm`.
- Wire fields are length-variable base64url; the QR budget was already checked against
  ML-KEM-768's 1184-byte encapsulation key.

## 3. Work items

1. **Protocol** (`docs/Sync-Protocol.md` in the **main** repo, `ShivaClaw/careerseeker`):
   define the hybrid handshake under `p256+mlkem768-hkdf-sha256`. The QR gains the engine's
   ML-KEM **encapsulation key**; the phone encapsulates to it and returns the ciphertext in
   its completion; both sides set `ikm = ecdh_shared || mlkem_shared`. Amend §5.2. Record the
   amendment in the §9 table. **`docs/Sync-Protocol.md` is normative.**
2. **Vectors** (`docs/sync-vectors/generate.mjs`, main repo): add a `pairing-hybrid` vector.
   Node has ML-KEM via `node:crypto` in recent versions — verify, else document the generator
   dependency. Follow the embed-and-verify pattern already used for ECDSA.
3. **Engine** (`src/Sync`, main repo): add `MLKem` to `PairingCrypto.Derive` behind the suite,
   guarded by `MLKem.IsSupported`. `SyncHarness` reads the new vector; **bump
   `$ExpectedOfflineTotal` and every count-bearing doc in the same commit** (drift trap).
4. **Phone** (`:core` + `:app`, this repo): ML-KEM in `:app` (BouncyCastle), the pure logic in
   `:core` via an interface `:app` implements — `:core` stays Android-free *and* library-free.
   `ProtocolVectorsTest` reads the same new vector.
5. **Migration**: keep P-256 in the hybrid (hybrid = both, never drop the classical half).
   Bump the pairing suite, **force re-pairing** for existing devices (the engine records
   `suite` per pairing in the audit chain — find affected pairings there, don't survey users).

## 4. Out of scope for PQ-1

Signature migration (ECDSA→ML-DSA). Signatures are **not** harvest-now-decrypt-later
exposed — a signature can't be forged retroactively — so it lags safely. Its own future TODO.

## 5. Done when

- [ ] `p256+mlkem768` handshake passes the shared vectors in all three implementations.
- [ ] `MLKem.IsSupported == false` cleanly falls back to P-256-only.
- [ ] `Verify-Alpha.ps1` green at the bumped pin; docs moved together.
- [ ] Live smoke (`tests/SyncLiveSmoke`) pairs under the hybrid suite against the real relay.
- [ ] Draft PR, Codex audit, never self-merged.
