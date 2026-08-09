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

### CLOSED 2026-08-09 (S5) — option (a), and a third measurement nobody had written down

§3.1 is amended in [careerseeker#32](https://github.com/ShivaClaw/careerseeker/pull/32): the cap
is on the **decoded ciphertext**, so **both implementations stand unchanged** and no wire-visible
change was made for a sentence's sake.

Writing it down surfaced something this entry had missed. There are not two measurements, there are
**three**: the relay tests the `ciphertext` **string** length against 1 MiB, and the raw request body
against 1 MiB + 4 KiB (`relay/src/channel.ts:160` and `:139`). Since base64url expands by 4/3, the
relay's test is the **stricter** of the two — an envelope the relay agrees to carry can never be one
a receiver rejects on size, so there is no gap, and the original worry ("a receiver accepting
something the relay would not have carried") is inverted from the real relationship. §3.1 now states
all three limits rather than implying one number.

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

### CLOSED 2026-08-09 (S5) — the second option, as recommended

§3 and §7.2 now say structural rejection reports `decrypt_failed`, and say explicitly that v1 adds
**no** `malformed` code — because a distinct code is a new observable, and §7.2 already requires that
`decrypt_failed` and `bad_signature` be indistinguishable. Landed in
[careerseeker#32](https://github.com/ShivaClaw/careerseeker/pull/32). The phone's existing behaviour
(`EnvelopeJson.parse`) is now the documented behaviour rather than a local convention, and the
engine being laxer is now a *spec* gap with a name — see PQ-A2-3 below, which is where that gap has
to be closed.

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

### STILL OPEN 2026-08-09 (S5) — and now with a measured reason, recorded as B-6

S5 was supposed to close this by adding `invalid-unknown-field` via `generate.mjs`. It did not, and
the reason is worth having written down rather than retried blindly next iteration: **the engine has
no wire-JSON envelope parser to reject the field in.** `ReceivedEnvelope`
(`src/Sync/EnvelopeReceiver.cs:7`) is a record constructed *by callers* from already-parsed JSON, and
`SyncHarness`'s `ToReceived` (`tests/SyncHarness/Program.cs:200`) reads the nine fields it wants and
drops anything else silently. A vector added today would therefore be **accepted** by the engine and
would turn the offline gate red.

So the vector is not the first step; the engine-side parser is. See [`BLOCKED.md`](../BLOCKED.md)
**B-6** for the symptom, what was checked, and the smallest unblock.

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

---

## PQ-A6-1 — `entitlement_ack` has no defined body, so the unlock path cannot be completed

**Spec (§4.3):** the e2p kind table lists

> `entitlement_ack` | Engine confirms a verified Pro entitlement was applied.

…and that is the entire definition. Every other shipping kind has its body specified (§4.3.1
for `snapshot`/`delta`, inline shapes for `evidence`, `heartbeat`, `conflict`, `error`,
§4.3.2 for `entitlement`). This one has no fields at all.

**Why it blocks A6.** `entitlement_ack` is the **only** thing that may unlock Pro on the phone
(§4.3.2 and PQ-A2-4: the engine verifies, the phone couriers). To act on it the applier needs to
know what it carries — at minimum the `product_id` that was granted, and plausibly the
acknowledging timestamp and the order id for support. Guessing those field names would be
inventing wire format, which this session does not do; and writing a parser for an unshipped
shape is the drift generator this repo's own rule already forbids (the `doc` kind is
deliberately unparsed for exactly that reason).

**Chosen here:** `ProState` is implemented as the contract — `Unlocked` is reachable only
through `ProState.afterEngineAck(...)` — and **nothing calls it yet**. The applier has no
`entitlement_ack` branch. The app is therefore honestly Free, with no way to become anything
else, which is the correct behaviour for a build that cannot yet receive an ack.

**To close:** specify the `entitlement_ack` body in §4.3 alongside the others. Suggested
minimum, matching what the engine's `StoreEntitlementStateStore` already tracks:
`{product_id, acknowledged_at, order_id?}`. Then add an applier branch, a shared vector, and
the C# side in the same commit, per the drift trap.

**Severity:** blocks the phone half of Pro. Not urgent — Pro ships complete on the desktop, and
the phone's outcome *display* works without it — but the phone cannot show an unlocked state
until it exists.

### SPEC HALF CLOSED 2026-08-09 (S5) — the body exists; the appliers do not

Gate PQ-A6-1 was answered **default-proceed** (Brandon, 2026-08-07) with exactly the suggested
minimum. `docs/Sync-Protocol.md` §4.3.3 now defines it in
[careerseeker#32](https://github.com/ShivaClaw/careerseeker/pull/32):

```
entitlement_ack body = { product_id, acknowledged_at, order_id? }
```

Three things the spec pins that an implementer would otherwise have to guess, and that this repo
should hold itself to when the applier is written:

1. **`acknowledged_at` is advisory** (§6.3). A receiver MUST NOT expire or re-lock on it. An
   entitlement that lapsed because two clocks disagreed would be indistinguishable from a
   revocation nobody performed.
2. **`order_id` is optional and carries no authorisation weight.** An ack without it is complete.
   Two vectors pin this rather than prose: `entitlement-ack` carries it,
   `entitlement-ack-no-order-id` does not, both valid.
3. **There is no negative form.** A rejected receipt produces an `error`, never an ack with a
   failure flag inside it. This matters here more than anywhere: it is the one payload that turns a
   paid feature on, and a kind whose meaning depends on a field inside the body is the parser
   mistake §4.2 exists to avoid.

**What is still open:** `ProState.afterEngineAck(...)` still has **no caller**. The applier branch is
not written, in either language. That is not an oversight — the unattended session that specified
this had no Kotlin or .NET toolchain, and a parser written against a compiler nobody ran is exactly
the drift this file exists to prevent. `docs/Sync-Protocol.md` §10.2 says so out loud: the vectors
are a fixed target for the appliers, **not** evidence that either side implements §4.3.3.

PQ-A2-4's boundary is untouched and stays load-bearing: a local `ACCEPTED` verdict still unlocks
nothing.
