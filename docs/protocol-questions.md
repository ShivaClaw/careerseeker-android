# Protocol questions

Ambiguities and cross-implementation mismatches found while implementing `:core` against
`docs/Sync-Protocol.md` (normative; **this session never edits it**).

Per the roadmap spec's rule: where the contract is ambiguous, take the **engine-compatible**
interpretation, ship that, and record the question here rather than diverging quietly. A
phone that is "more correct" than the engine in a way the engine cannot match is a field bug,
not a fix — the shared vectors exist precisely to keep the two implementations identical.

Each entry states what the spec says, what each side actually does, what this repo chose, and
what decision would close it. **None of these are fixed unilaterally on the phone.**

---

## PQ-A2-1 — The 1 MiB cap is measured against the ciphertext, not the envelope

**Spec (§3.1):** "An envelope MUST NOT exceed **1 MiB** total. The relay MUST reject larger
with HTTP 413."

**Both implementations instead measure the decoded ciphertext:**

- engine — `src/Sync/EnvelopeReceiver.cs`:
  `if (ciphertext.Length > Protocol.MaxEnvelopeBytes) return Reject(SyncError.TooLarge);`
- phone — `core/.../EnvelopeReceiver.kt`:
  `if (ciphertext.size > Protocol.MAX_ENVELOPE_BYTES) return reject(ErrorCode.TOO_LARGE)`

So an envelope whose ciphertext is just under 1 MiB is accepted by both endpoints even though
the JSON envelope carrying it — base64url expansion (×4/3) plus ~200 bytes of header — is
comfortably **over** 1 MiB. The endpoints are consistent with each other and inconsistent with
the document.

**Chosen here:** unchanged. Matching the engine is worth more than matching the prose, and the
relay enforces a real 413 at the transport boundary regardless, so the practical exposure is a
receiver accepting something the relay would not have carried.

**To close (engine + phone + `generate.mjs`, one commit):** decide whether the cap is on the
serialised envelope or on the ciphertext, then either (a) amend §3.1 to say "ciphertext" and
keep both implementations, or (b) change both receivers to measure the serialised envelope and
regenerate `invalid-oversized` so the vector actually pins the chosen meaning. Option (a) is
the smaller change and arguably the more useful check — the ciphertext is what gets decrypted —
but the wording should stop saying "envelope … total" either way.

**Severity:** low. Not exploitable on its own; it is a spec/implementation divergence that
would surface as an interop surprise for a third implementation reading only the document.

---

## PQ-A2-2 — §7.2 has no error code for a structurally malformed envelope

**Spec (§3):** "Other unknown top-level fields MUST be rejected, not ignored." §7.2 then
enumerates the error codes, and none of them means "this did not parse".

Malformed framing therefore has no home. Existing practice on both sides is to fold structural
failures into `decrypt_failed` (bad base64, wrong nonce length, unparseable direction all do
this today), which also happens to satisfy §7.2's rule that a receiver must not let an observer
distinguish `decrypt_failed` from `bad_signature`.

**Chosen here:** `EnvelopeJson.parse` rejects unknown top-level fields and every structural
defect as `decrypt_failed`, following existing practice. Note this makes the phone **stricter
than the engine**, which does not currently reject unknown top-level fields at all — but
strictly-more-rejection is safe in this direction: the engine's publisher emits exactly the nine
fields §3 defines, so no legitimate engine traffic is affected.

**To close:** either add a `malformed` code to §7.2 (and accept that it is a new observable
distinction, so it must be indistinguishable from `decrypt_failed` in timing and size), or state
explicitly in §3 that structural rejection reports `decrypt_failed`. The latter costs nothing
and documents what both implementations already do.

**Severity:** cosmetic for interop, but worth pinning: a third implementation reading §3 will
ask exactly this question, and "whatever the first implementation did" is not a spec.

---

## PQ-A2-3 — No vector covers the unknown-top-level-field rule

§3's rejection requirement is a **MUST** with no vector behind it, so an implementation can pass
100% of the shared suite while ignoring it — which is exactly what this repo did until A2.

**Chosen here:** implemented and covered by a local `:core` test rather than a shared vector,
because vectors are generated upstream and this session does not write to that repo.

**To close:** add `invalid-unknown-field` to `docs/sync-vectors/v1/` via `generate.mjs` so the
rule is enforced cross-language like every other MUST in §3.

**Severity:** low, now that both a phone implementation and a test exist — but it stays open
until the engine is held to the same rule by a shared vector.

---

## PQ-A2-4 — "Verifying" an entitlement on the phone is not the same as granting one

Not a spec defect; a design boundary worth writing down because it is easy to erode.

§4.3.2 makes the phone a **courier**: it forwards `{original_json, signature}` and the *engine*
verifies against its configured Play public key, then answers `entitlement_ack`. The roadmap
spec's A6, though, describes the phone-side behaviour as "`entitlement-valid` unlocks;
`not-purchased`/`tampered`/`wrong-*` show honest locked states", which read literally would put
the unlock decision on the device that benefits from getting it wrong.

**Chosen here:** `EntitlementVerifier` in `:core` classifies the five signed vectors identically
to the engine's `GoogleSignedPayloadVerifier`, and its KDoc states that a local `ACCEPTED`
verdict means only "worth sending to the engine". Pro is unlocked by an `entitlement_ack`
envelope and by nothing else. The local check exists to give the user an immediate honest
reason when a receipt will not work, not to grant anything.

**To close:** nothing required — recorded so a later session does not "simplify" the local
verdict into the entitlement flag and quietly make the phone self-certifying.
