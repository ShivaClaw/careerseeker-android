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

### RE-OPENED AND RE-CLOSED 2026-08-09 (S2/S5, sixth iteration) — "so there is no gap" was wrong

The paragraph directly above contains a true implication and a false conclusion, and the false one
is mine. *"An envelope the relay agrees to carry can never be one a receiver rejects on size"* is
correct. **"So there is no gap" does not follow from it** — it checks one direction of a
two-directional relationship and declares the other direction closed by silence.

The other direction is the one that matters. **An envelope both receivers accept can be one the
relay refuses to carry**, and it was **256 KiB** wide. Measured against a local relay under
miniflare rather than reasoned about:

```
MAX_ENVELOPE_BYTES         = 1048576          (§3.1: decoded ciphertext)
b64u chars for 1 MiB       = 1398102          (ceil(4/3 × cap) — what a legal envelope weighs)
relay accepted b64u chars <= 1048576          (a CHARACTER count tested against a BYTE budget)
=> max decoded bytes the relay would carry = 786432

push, ciphertext = 1,398,102 chars (exactly 1 MiB decoded, legal by §3.1)
  -> 413 {"error":"too_large"}
push, ciphertext = 1,048,576 chars (786,432 decoded)
  -> 201
```

**Why it was latent and not live.** Nothing sends envelopes near either number today: §4.4 chunking
is unimplemented in both codebases, and snapshots are orders of magnitude smaller. So no field
incident was ever possible from this. What makes it worth fixing before it is reachable is that
**§4.4 instructs a future chunker to split against "the envelope limit"** — §3.1's number — which is
precisely the value that does not fit. The first correctly-implemented chunker would have produced
maximum-size chunks and met a 413, with the relay, the spec and both receivers each individually
defensible.

**Closed by:** `MAX_CIPHERTEXT_B64U_CHARS` derived from `MAX_ENVELOPE_BYTES` in
`relay/src/protocol.ts`, applied at both guards in `relay/src/channel.ts`, and §3.1 amended to make
the conversion **normative** — the relay MUST carry every envelope §3.1 declares legal.
`relay/test/relay.test.ts` grew four cases pinning the derivation, the maximum legal envelope
surviving a push/pull round trip, and the first character past the cap; the case that asserted
`1 MiB + 1 chars → 413` was **pinning the bug** and is gone. Both guards moved strictly *looser*, so
no envelope the relay accepted before is rejected now.

**Nothing on the phone changes.** `EnvelopeReceiver.kt` measures decoded bytes against
`Protocol.MAX_ENVELOPE_BYTES` and was correct throughout; so was the engine's. This was only ever
wrong in the one component that cannot decode.

**Severity of the original miss:** low in effect, higher in kind. The defect was latent; the
*reasoning error* — checking an implication in one direction and reporting the question closed — is
the sort that closes a question while leaving it open, and it survived a PR body and two iterations
of these records before anyone ran the other direction.

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

### CLOSED 2026-08-12 (twenty-second cloud iteration) — the vector exists, and so does the parser it needed

**Closed the only way it could be**, and the order is the point: the vector was **not** the work.

The reason this sat open for three weeks is that the engine had **nowhere to reject one**.
`src/Sync` had no inbound wire-JSON parser at all — `ReceivedEnvelope` was a record built
field-by-field from already-parsed JSON, reading the nine names it wanted and dropping the rest — so
an envelope carrying a tenth field **decrypted and was accepted** by the engine while the phone
refused it. Adding the vector first would have turned the shared suite red on a rule the engine did
not implement, which is what **B-6** existed to prevent.

**What landed** (draft PR [careerseeker#37](https://github.com/ShivaClaw/careerseeker/pull/37),
stacked on #32):

1. `src/Sync/EnvelopeJson.cs` — the C# counterpart of this repo's `EnvelopeJson.kt`, mirrored field
   for field, reporting `decrypt_failed` per PQ-A2-2's closure.
2. `tests/SyncHarness/Program.cs` — envelope vectors now go through it as **wire text**. All 24
   pre-existing vectors classify identically (130 → 130 before the new vector), so the strict parser
   refuses nothing the suite declares legal.
3. `invalid-unknown-field`, generated by `generate.mjs`. Everything about the envelope is valid
   except the extra field, so a receiver that dropped the rule **accepts** it rather than failing it
   some other way — a pin, not a shape. The field is injected post-seal and is therefore **not
   covered by the AAD**, which is exactly why a permissive parser is an injection point.
4. `docs/Sync-Protocol.md` §10 now **requires** the vector; new §10.3 records why it could not exist
   before.

**Proven by mutation:** deleting the unknown-field check makes the vector report
`-- got accepted`. The engine's laxness was real, and it is now pinned cross-language like every
other MUST in §3.

**One consequence inherited deliberately:** the check precedes the version check on both sides, so a
v2 sender that also adds a field is told `decrypt_failed` rather than `version_unsupported` —
**PQ-ER-1**, now true of both implementations by design rather than by accident. Re-verify:
`AUDIT-REQUEST.md` **C-WP-3…8**.

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

**What is still open** *(revised 2026-08-09, later the same day)*: the paragraph here previously
said the applier was "not written, in either language". The **phone half is now written** —
`EntitlementAckApplier` calls `ProState.afterEngineAck(...)`, 9 tests, measured in a reduced `:core`
probe and green on CI run
[31305289509](https://github.com/ShivaClaw/careerseeker-android/actions/runs/31305289509). The
**engine half is still unwritten**: no C# applier answers `entitlement_ack` after
`GoogleSignedPayloadVerifier` accepts. That remains unblocked, merely unwritten — the unattended
sessions doing this work have no .NET toolchain, and a parser written against a compiler nobody ran
is exactly the drift this file exists to prevent. `docs/Sync-Protocol.md` §10.2 still holds: the
vectors are a fixed target for the appliers, **not** evidence that either side implements §4.3.3.

PQ-A2-4's boundary is untouched and stays load-bearing: a local `ACCEPTED` verdict still unlocks
nothing.

---

## PQ-S4-1 — `pull_request` is described as resumable, but the engine only ever re-publishes everything

**Spec (§4.3, phone → engine):** `pull_request` | `{since_seq}` — "ask the engine to re-publish
**from a sequence point**." §6.2 separately says a receiver should "treat a large gap as a signal
to request a fresh `snapshot`, not as an error", and pins **no threshold** for "large".

**What the engine actually does.** It parses the field and then discards its meaning:

- `src/Sync/InboundDispatcher.cs:105-111` — `case "pull_request"` reads `since_seq`
  (`ReadSinceSeq`, defaulting to `0` on any parse failure) and passes it to
  `ISnapshotRepublisher.RepublishSnapshotAsync(since, ct)`.
- **Every implementation of that interface ignores the argument.**
  `LiveRepublisher.RepublishSnapshotAsync` (`tests/SyncLiveSmoke/Program.cs:311-312`) calls
  `publisher.PublishSnapshotAsync(...)` unconditionally; `RecordingRepublisher`
  (`tests/SyncHarness/Program.cs:756-759`) only records the value so the harness can assert it
  round-tripped. There is no shipping code path in which `since_seq` changes what is sent.

So in v1 `pull_request` has exactly one meaning — *send me a full snapshot* — and `since_seq` is
carried but inert. The engine's own live smoke sends `since_seq = 0`
(`tests/SyncLiveSmoke/Program.cs:191`); the harness sends `7` purely to prove the plumbing
(`tests/SyncHarness/Program.cs:639`).

**Chosen here:** the phone always sends `since_seq: 0`
(`PullPolicy.SINCE_SEQ_FULL_REPUBLISH`), for every reason it asks — cold start, a `delta` refused
for want of a snapshot, or a §6.2 gap.

The reasoning is worth stating because the alternative looks more honest and is in fact more
dangerous. Reporting the phone's real high-water mark (`since_seq = highestAppliedSeq`) would encode
a request the current engine ignores but a future one might honour — and if it were honoured, the
gap case would come back as *deltas resuming after N*, when §6.2 explicitly wants a fresh snapshot.
`0` is the only value that means "I hold nothing usable, send everything" under both the engine that
exists and the engine that might. `PullPolicyTest.every reason sends since_seq zero` pins it so a
later "improvement" has to argue with a failing test first.

**The unpinned threshold is a second, smaller question.** Since §6.2 gives no number and the engine
has no opinion (it never *sends* a `pull_request`, it only answers one), "large gap" is phone-side
policy. It is therefore a constructor parameter, `PullPolicy(gapThreshold = ...)`, defaulting to
**32** — chosen, not measured, and labelled as such in the source. There is no deployment to derive
it from yet.

**To close (engine + phone + spec, one commit):** decide which `pull_request` is.

- **(a) It is a snapshot request.** Amend §4.3 to drop "from a sequence point" and either remove
  `since_seq` or document it as reserved-and-ignored. Smallest change; matches every line of
  shipping code today.
- **(b) It is genuinely resumable.** Then `ISnapshotRepublisher` needs an implementation that
  honours `sinceSeq`, §6.2 needs to say how a receiver asks for a *snapshot* specifically (a
  resumable pull cannot express it), and the phone's zero becomes wrong.

(a) is recommended. Whichever is chosen, §6.2's "large gap" should either name a number or say
explicitly that the threshold is receiver policy.

**Severity:** low today, medium if unaddressed. Nothing is broken — both sides agree in practice
because the field is inert. The risk is a third implementation reading §4.3, implementing
resumption, and finding that the phone's `0` asks for the full history on every reconnect while the
engine's answer never depended on the field at all.

### CLOSED 2026-08-10 (S4 spec half, seventh cloud iteration) — option (a), and it cost no code

Amended in the main repo on `claude/s4-pull-request-semantics` (stacked on PR #32), commit
`9399d11`. §4.3's row now reads "re-publish the whole dashboard as a fresh `snapshot`", a new
**§4.3.4** pins the body, and §6.2 states that the gap threshold is receiver policy.

The three rules §4.3.4 adds:

- a sender MUST set `since_seq` to `0`;
- a receiver MUST ignore it and answer with a full `snapshot`, never a `delta`;
- a receiver **MUST NOT reject** a non-zero `since_seq`.

The third is the one that was not in the question's option (a) and is worth the extra line.
"Reserved" invites a reader to validate it, and this is a **reserved field**, not one of §4.3's
reserved **kinds** — those MUST be rejected as `unknown_kind`. Rejecting an unknown kind refuses
traffic v1 cannot understand; rejecting this field would refuse a request v1 understands perfectly,
and would stall the stream on a forward-compatible sender. §4.3.4 states the asymmetry so a third
implementation does not read one rule and apply the other.

**Neither implementation had to change, and that is the evidence the option was right.** Checked
against both, this session, before the spec was written rather than after:

| §4.3.4 rule | Engine | Phone |
| --- | --- | --- |
| sender sends `0` | never sends `pull_request` — n/a | `PullPolicy.SINCE_SEQ_FULL_REPUBLISH = 0L` |
| receiver ignores the value | `ReadSinceSeq` → `ISnapshotRepublisher`; **every** impl ignores the argument | n/a |
| answers a full snapshot | `LiveRepublisher` calls `PublishSnapshotAsync` unconditionally | n/a |
| non-zero is not a rejection | no rejection path reads the field | n/a |

Re-verification commands: `AUDIT-REQUEST.md` **C-S4S-1 … C-S4S-5**.

**Deliberately no vector.** A `pull_request` vector was considered and rejected, for a reason that
generalises: §4.3.4's content is three *behavioural* MUSTs, and a static vector cannot test any of
them — it cannot observe that a receiver answered a snapshot, nor that it declined to reject. Worse,
`SyncHarness` enumerates every `type: "envelope"` vector on disk
(`tests/SyncHarness/Program.cs:59-62`, and the "classifies every vector correctly" loop), so an
envelope-typed addition would move `$ExpectedOfflineTotal` — a number this sandbox cannot measure,
having no .NET. That is the CLAUDE.md drift trap, and the S5 ack vectors escaped it only by carrying
a new `type` the enumeration skips. A vector here would have bought nothing and risked a red gate
for the next session.

### A finding this slice turned up, recorded against PQ-S6-1 rather than here

Verifying the engine's `pull_request` path surfaced the **same over-reporting shape** PQ-S6-1
describes for `outcome`, on a second kind. See the note appended to PQ-S6-1 below.

---

## PQ-S6-1 — An `outcome` is never acknowledged, and the engine reports it applied either way

**Spec (§4.3).** The phone → engine table has `outcome` | Pro: `{app_id, outcome, at}`. The
engine → phone table acknowledges exactly two phone-originated kinds: `conflict` rejects a
`doc_edit`, and `entitlement_ack` confirms an `entitlement`. **There is no `outcome_ack`, and no
rejection kind for `outcome`.** §7.2's `error` covers envelope-level faults (bad kind, replay, size),
not "the mark was received and then not applied".

**What the engine actually does**, which is worse than silence:

- `src/Sync/InboundDispatcher.cs:98-103` — `case "outcome"` calls `_outcomeApplier.ApplyAsync(...)`
  **only if the applier is non-null**, then returns `InboundResult(InboundOutcome.OutcomeApplied, ...)`
  unconditionally.
- `IOutcomeApplier` is nullable by design and documented as such
  (`src/Sync/InboundDispatcher.cs:30-31`): "a null applier means outcome dispatch is a no-op seam
  for now."

So the engine can accept a signed `outcome`, do nothing with it, and report `OutcomeApplied`. That
is an internal dispatch result rather than a wire claim — nothing is sent to the phone saying so —
but it means even an engine-side caller cannot distinguish applied from dropped.

**The second half: the phone cannot order its own mark against an arriving snapshot.** §6.1 gives
each direction "an independent counter, starting at 1" — the e2p seq carrying a `snapshot` and the
p2e seq carrying the mark are not comparable — and §4.3.1's application summary
(`{id, state, company, title, score, outcome?}`) carries no per-application timestamp. A payload
that arrives after a mark may have been generated before it, and there is nothing in v1 that says
which.

**Chosen here** (`OutcomeMarkPolicy`, `core/.../OutcomeMarking.kt`): a pending mark **shadows**
whatever the engine reports for that application, and the shadow is retired by **value
convergence** — the engine carrying the marked value in a later §4.3.1 payload — because that is
the only evidence v1 offers. The shadow is **bounded** by a count of disagreeing dashboard payloads
(`disagreementLimit`, default **3**, chosen not measured), after which the mark is abandoned and
the engine's value shows through.

Both halves of that are forced. Letting the engine win on arrival reverts the badge under the
user's finger for a mark that is merely in flight, which is indistinguishable from "it didn't
save". Letting the mark win forever means a silently dropped mark displays as the user's truth
permanently — the exact fabrication shape this project exists to refuse, pointed at the user
instead of at the engine. The bound counts *reports*, not seconds, because §6.3 makes clocks
untrustworthy and a disagreeing report is the only monotone evidence the phone has.

`DisplayedOutcome.pending` is what keeps the compromise honest: the UI is expected to render an
unconfirmed mark differently from one the desktop has confirmed.

**To close (engine + phone + spec).** Two options, and unlike PQ-S4-1 the cheap one is not clearly
right:

- **(a) Add `outcome_ack`** to §4.3's engine → phone table — minimally `{app_id, outcome, applied}`
  — and have `InboundDispatcher` emit it from the applier's real result rather than from reaching
  the `case`. Then the phone retires a mark on evidence instead of on inference, the bound becomes
  a fallback rather than the mechanism, and `disagreementLimit` stops being a guess. This also
  makes `IOutcomeApplier`-is-null observable instead of silently successful.
- **(b) Declare marks fire-and-forget** and say so in §4.3: the phone MUST NOT display an
  unconfirmed mark as confirmed, and convergence-with-a-bound is the specified behaviour. Cheaper,
  and it at least stops a third implementation inventing a different reconciliation rule — but it
  leaves the engine's dispatch result over-reporting, which is a separate small fix worth making
  either way.

**(a) is recommended**, with the `InboundDispatcher` result fix regardless of which is chosen.

**Severity:** medium. Nothing is broken today because no shipping caller marks outcomes yet — the
`:app` wiring is unwritten and the send path needs S3's device key. It becomes a user-visible
correctness problem the moment the first mark is sent, and a third implementation reading §4.3
today has no way to know reconciliation is even required.

### Extension 2026-08-10 (S4 spec half) — the over-reporting is not unique to `outcome`

Found while verifying the engine's `pull_request` path for PQ-S4-1, so it is measured rather than
inferred. `InboundDispatcher` has the **same shape on a second kind**:

```
src/Sync/InboundDispatcher.cs:105-111
    case "pull_request":
        var since = ReadSinceSeq(received.Plaintext!);
        if (_republisher is not null)
            await _republisher.RepublishSnapshotAsync(since, ct);
        return new InboundResult(InboundOutcome.SnapshotRepublished, null, received.Kind);
```

The `return` is outside the null check, exactly as `case "outcome"` returns `OutcomeApplied`
outside its own. So an engine with `_republisher == null` — the documented inert seam, "null means
the seam is inert (no vault yet)" — accepts a `pull_request`, republishes **nothing**, and reports
`SnapshotRepublished`.

**This one is milder than the `outcome` case and the difference is worth stating**, because a
future fix should not treat them as one bug. A dropped `outcome` loses a user's mark and the phone
displays it as truth (which is why `OutcomeMarkPolicy` bounds the shadow). A dropped
`pull_request` loses nothing: the phone asked for a snapshot, none arrives, and `PullPolicy`'s
latch means it will ask again on the next open. The consequence is a silently unanswered request,
not a false display.

What they share is the defect: **an `InboundOutcome` that reports reaching a `case` rather than
completing an action.** PQ-S6-1's option (a) proposes fixing this for `outcome` by deriving the
result from the applier; the same derivation is available here — return a disposition that
distinguishes "republished" from "no republisher configured". Worth doing in the same commit as
PQ-S6-1's fix, since it is the same three lines of reasoning twice.

**Not fixed here: it is C#, and this sandbox has no .NET.** Unblocked, merely unwritten — a local
session can do it. Re-verify the symptom with `AUDIT-REQUEST.md` **C-S4S-5**.

---

## PQ-S6-2 — §6.1 spells out counter reconciliation for the engine only, and the relay enforces it symmetrically

**Opened 2026-08-10 (S6 send path, tenth cloud iteration).** Not a blocker; the phone-side
behaviour is already correct. Recorded because the spec asks one sender for something it does not
ask the other, and the omission is the kind a second implementer will read as permission.

**Spec (§6.1).** One sentence covers both directions — each has "an independent counter starting at
1, incremented per envelope, and **persisted by the sender across restarts**". The paragraph then
spells out the *recovery* rule for exactly one side:

> The engine MUST therefore resume its e2p counter above `max(persisted_seq, relay_latest_e2p_seq)`
> — the value from its pairing store, reconciled on startup against the relay's current `latest`
> for the direction (`GET /pull?dir=e2p&since=0` returns it) as a belt-and-suspenders should the
> store lag.

**The phone owes the identical obligation on `p2e`, and §6.1 never says so.** The relay does not
care who is sending: `POST /push` refuses `seq <= last` **per direction**
(`relay/src/channel.ts:167`) and answers 409 `replay_rejected` with its `latest`. A phone whose
persisted p2e counter has fallen behind — a restore, a rolled-back store, a counter persisted only
after a successful push whose response was lost — therefore builds envelopes the relay refuses at
the door, forever, with no rule in the spec telling it what to do about that.

**Why the asymmetry is easy to write and easy to misread.** §6.1's engine paragraph justifies itself
with a consequence that is genuinely one-sided ("a silent, total, one-sided sync death" when the
recovery `snapshot` itself is rejected). The p2e consequence is smaller — marks and entitlements
stall rather than the dashboard dying — so it never got written down. But "smaller" is not "absent",
and a reader implementing a second phone client has no rule to follow.

**Chosen here, and it needed no amendment to be correct.** `OutboundQueue` (`core/.../OutboundQueue.kt`)
implements the symmetric rule: a 409 retires the envelope's frozen bytes, halts on
`SendHalt.COUNTER_BEHIND`, and surfaces the relay's `latest` to the caller that owns the persisted
counter; `reconciled()` then rebuilds above it. `RelayClient` had to be changed to make the number
reachable at all — it was mapping every 409 to a bare `Conflict` and returning before reading the
body (see `LOG.md` §S6S-3, `AUDIT-REQUEST.md` C-S6S-2).

**To close.** A §6.1 amendment stating the rule for both senders — the cheapest form is to
generalise the existing sentence to "the sender MUST resume its counter above
`max(persisted_seq, relay_latest_<dir>_seq)`" and keep the engine's e2p consequence as the worked
example. Optionally note that `POST /push`'s 409 body carries `latest`, which lets a sender
reconcile without the extra `GET /pull?since=0` round trip; that field is currently documented
nowhere in `Sync-Protocol.md` despite being implemented and relied upon.

**Deliberately not amended in this iteration.** `docs/Sync-Protocol.md` lives in the main repo and
is already claimed by draft PRs **#32** and **#33** (#33 stacked on #32); a third stacked spec edit,
made from a sandbox that cannot run `Verify-Alpha.ps1`, is a poor trade for a paragraph that changes
no behaviour. Engine-compatible interpretation rule applies: match the engine, ship it, record the
question.

**Re-verify:** `AUDIT-REQUEST.md` **C-S6S-2** and **C-S6S-5**.

### CLOSED 2026-08-11 (fifteenth cloud iteration) — the cheap form, plus the field it depends on

Closed the way this question recommended: §6.1's resume rule now reads "a sender MUST resume its
counter above `max(persisted_seq, relay_latest_seq_for_that_direction)`", with the engine's e2p case
kept as the worked example and the phone's p2e obligation stated explicitly. Main repo,
`claude/s4-pull-request-semantics`.

**But the recommendation had a hole, and closing it needed a second section.** This question's "to
close" paragraph noted, almost as an aside, that the 409 body carries `latest` and that the field
"is currently documented nowhere in `Sync-Protocol.md` despite being implemented and relied upon".
**A §6.1 rule that points at an undocumented field is not closed** — it is the PQ-S4-2 defect
one level down, where normative text depends on a body no section defines. So the slice wrote
**§2.2** first, pinning the push route's four responses, and only then amended §6.1 to name both
sources of the relay's number.

**Measured, not read off the source** — the eleventh run's probe trick, throwaway tests under
miniflare asserting deliberately wrong values so vitest prints the measured one:

| probe | measured |
| --- | --- |
| first accepted push | `201 {"ok":true,"seq":1}` |
| replayed seq 7 | `409 {"error":"replay_rejected","latest":7}`, keys exactly `error\|latest` |
| regressed seq 3 with high-water 50 | `409 … latest=50` |
| `p2e` seq 4 replayed while `e2p` holds 90 | `409 … latest=4` — **per direction, not per pairing** |
| unparseable body / `seq: 0` | `400 {"error":"bad_request"}` — no `latest` |
| oversize ciphertext | `413 {"error":"too_large"}` — no `latest` |
| first push on a direction holding nothing | `201` even at seq 1 |
| `GET /pull?dir=p2e&since=0` on an empty direction | `200 {"envelopes":[],"latest":0}` |

The probe file was **deleted before committing** and the relay suite measured **36 / 0 both before
and after** on this branch — it is a measurement, not a suite member. (36 is the
`claude/s4-pull-request-semantics` figure; the 42 in `STATE.md` is `claude/s2-relay-retention`'s,
and reading one as the other is the count-drift trap one branch over.)

**The close is honest about non-conformance rather than implying conformance.** §6.1 now carries a
measured conformance note: neither shipping sender fully satisfies the rule. The engine reconciles
not at all (**PQ-S6-3**, opened here); the phone reconciles but persists nothing (**B-8**). Writing
a MUST that both implementations miss is normally the §2.1 defect — a spec tightening ahead of its
implementations — and the reason it is not that here is stated in the section itself: the rule was
**already normative for one of the two senders**, the first sentence of §6.1 **already** bound both
to persist, and the property is safety rather than error-reporting style. What generalising changed
is that a second phone implementation now has a rule to follow; it did not invent an obligation.

**Two probe results contradicted nothing but were worth pinning anyway**: `latest` is per-direction
(a sender that read it as a pairing-wide position would resume far too high, skipping seqs and
creating gaps the receiver reads as legitimate), and a direction holding nothing accepts seq 1
rather than answering 409 with `latest: 0`.

**Re-verify:** `AUDIT-REQUEST.md` **C-S6C-1…6**.

---

## PQ-S2-1 — The relay declares a `pairing` field it never checks

**Opened 2026-08-10 (S2 relay conformance, eleventh cloud iteration).** Not a blocker, and
deliberately **not fixed** in the slice that found it — the reason is the interesting half.

**Spec (§3).** `pairing` is a required envelope field: "Pairing id, `p_` + 16 base64url chars."
`relay/src/protocol.ts` declares it in `EnvelopeHeader`, and §4.1 puts it in the AAD, so both
receivers authenticate it.

**What the relay actually does.** `POST /push` validates `v`, `dir`, `seq`, `ts`, `key_id`,
`nonce`, `ciphertext` and the optional `sig` — and never looks at `pairing`. Measured in this
sandbox against the real Worker under miniflare, all three accepted with **201**:

| pushed to a channel with | `pairing` in the body | relay answered |
| --- | --- | --- |
| a valid bootstrapped channel | `"p_x"` (not 16 chars, not this channel) | 201 |
| the same | field absent entirely | 201 |
| the same | `"p_AAAAAAAAAAAAAAAA"` — a *different* valid pairing id | 201 |

**Why this is small.** It is not a confidentiality hole. The key is derived per pairing, so an
envelope sealed for pairing X does not decrypt at Y regardless of what its header claims, and
`pairing` sits in the AAD so it cannot be edited in flight. Only a bearer holder can push at all,
and the bearer *is* the pairing. The cost is that the relay stores junk and burns a `seq` slot —
which is PQ-S2-2's problem, not this one's.

**Why it is worth recording anyway.** §3's own words are "a permissive parser here is how a future
version's field silently becomes an injection point", and this is a field the relay names in a
typed interface, routes on, and then declines to check. The blast radius today is small because
other mechanisms happen to cover it; that is a different statement from "the check is unnecessary".

**Why it was not fixed here, and this is the load-bearing part.** Tightening what the relay refuses
is the exact shape of the 2026-08-09 size-cap bug (§3.1's amendment: "the relay MUST carry every
envelope this section declares legal"), and the harnesses that would catch an over-tightening are
`SyncLiveSmoke` and `Verify-Alpha.ps1`, neither of which runs without .NET. Two callers in the repo
already emit ids that would **fail** a shape check: `tests/EngineHarness/Program.cs:2268` constructs
a publisher with `"p_bridge_test"` (11 chars after `p_`, not 16), and `relay/test/relay.test.ts`'s
own envelope helper has been sending `"p_x"` into every channel for the life of the suite. Neither
reaches a relay today — but they are evidence that the shape rule is not universally respected
inside this codebase, which is precisely the thing to measure *before* a relay starts refusing on it.

**To close.** On a machine with .NET: add `typeof env.pairing !== 'string' || env.pairing !== <the
URL's pairing segment>` to `push`'s header check, fix the two non-conforming ids above, then run
`Verify-Alpha.ps1` and the engine↔local-relay smoke. Equality with the URL segment is the stronger
check and is free: the relay already routes by that segment, so a disagreement is malformed by
construction.

**Re-verify:** `AUDIT-REQUEST.md` **C-S2R-8**.

---

## PQ-S2-2 — `seq` has no upper bound, and one out-of-range value wedges a direction permanently

**Opened 2026-08-10 (S2 relay conformance, eleventh cloud iteration).** Recorded, not fixed:
capping `seq` in the relay would refuse what §3 declares legal, which is the one thing §3.1's
amendment forbids.

**Spec (§3).** `seq` is "int, per-direction monotonic counter, starts at 1". **No maximum is
stated**, anywhere.

**What that costs.** The relay's guard is `seq <= MAX(seq)` → 409 `replay_rejected`. Measured here:

```
push seq = Number.MAX_SAFE_INTEGER (9007199254740991)  -> 201 accepted
push seq = 1            (the next legitimate envelope)  -> 409
   body: {"error":"replay_rejected","latest":9007199254740991}
```

Every subsequent envelope in that direction is refused for as long as the row lives. There is no
recovery path short of `DELETE /v1/{pairing}` (unpair) or waiting out the TTL — and §6.1 tells a
reconciling sender to resume *above* `latest`, which here is a number it can never usefully exceed.
This is not an outsider attack (it needs the bearer), but it does not need malice either: one
sender bug that emits a garbage counter bricks the channel, and the failure presents as "sync
stopped working" with a 409 nobody can act on.

**A second, smaller mismatch in the same field.** The two receivers type `seq` as a 64-bit integer
(`src/Sync/EnvelopeCodec.cs:7` `long Seq`; the Kotlin header likewise), while the relay is
JavaScript and reads it through `JSON.parse` into a double. Integers above 2⁵³ round silently there,
so the relay's notion of `seq` and the receivers' diverge past that point. Unreachable in practice —
2⁵³ envelopes is not a number this product produces — but the *cap* question and the *precision*
question have the same answer, so they should be settled together.

**To close.** A §3 amendment giving `seq` an explicit maximum, then the relay enforcing it as a
`400`. `2^53 - 1` is the natural bound: it is the largest integer all three implementations agree
on exactly, and stating it turns a silent precision divergence into a documented limit. Spec first,
relay second — the reverse is the size-cap bug again, a relay refusing what the spec permits.
Worth deciding alongside whether the relay should expose any channel-level reset short of unpair.

**Re-verify:** `AUDIT-REQUEST.md` **C-S2R-9**.

### CLOSED IN PART 2026-08-11 (sixteenth cloud iteration) — the bound, and the half it does not reach

Closed exactly as the "to close" paragraph above prescribes, in that order: §3 amendment first
(**§3.2**), relay enforcement second (draft PR **#35**, `claude/s2-seq-bound`, stacked on #34 →
#32). **The open half is named at the bottom of this note and is not closed.**

**This question was deferred on 2026-08-10 and again on 2026-08-11 as "do not close from a
sandbox", and that inheritance was wrong.** It came from PQ-S2-1, whose "to close" genuinely does
begin *"On a machine with .NET"* — because fixing it means changing two engine test fixtures. This
one's does not: it says *spec first, relay second*, and both halves are Markdown and TypeScript.
The two questions were opened in the same iteration and got summarised together, and the summary
was what carried forward. **A deferral reason attached to a neighbouring question is not a finding
about this one** — the fifteenth run's ninth correction, arriving one iteration later in a
different costume.

**Two things measured before writing the rule, neither of which this question had.**

**1. The reachable ceiling was not `MAX_SAFE_INTEGER`.** The measurement above stops at
`Number.MAX_SAFE_INTEGER`, which reads as though that were the boundary. `Number.isInteger` is
**not a range check**: it rejects a fractional value but cannot reject a large one, since every
double at or above 2⁵³ is necessarily integral — so `1e300` pushes just as well, and
only `Infinity` was refused (it answers `400`, since `Number.isInteger(Infinity)` is `false`). The
reachable range was ~1.8e308.

**2. The read path breaks too, and it is the severe half.** This question costed the wedge on the
*write* path only: "every subsequent envelope in that direction is refused". But `latest` is
emitted from the same double, so measured under miniflare against the real Worker:

| pushed `seq` | relay reported `latest` as | consequence |
| --- | --- | --- |
| `4611686018427387904` (2⁶²) | `4611686018427388000` | **silently rounded** — off by 96; both receivers parse it happily and are now 96 ahead of the sender |
| `10000000000000000000` (1e19) | `10000000000000000000` | above `Long.MaxValue`; **neither receiver can parse it** |
| `1e300` | `1e+300` | exponent notation; **neither receiver can parse it** |

Both receivers read `latest` strictly: `src/Sync/RelayClient.cs:74` is
`GetProperty("latest").GetInt64()` with **no catch on that path** (it throws out of `PullAsync`),
and the phone's `strictLong` goes through `toLongOrNull()` to a rejected page reported as
`Unavailable` (`core/.../RelayClient.kt:258-262`). **So one out-of-range envelope disables the
`GET /pull` reconciliation §6.1 prescribes for exactly this situation.** The wedge takes out the
instrument used to diagnose it — which is why this earned a rule rather than a note.

**The "unreachable in practice" clause was also wrong, and that is worth reading carefully.** This
question dismissed the precision divergence because "2⁵³ envelopes is not a number this product
produces". True, and irrelevant: reaching 2⁵³ does not require *sending* 2⁵³ envelopes, only
emitting one counter that large. Measured, `9007199254740992` answered `201` and then
`9007199254740993` answered `409 replay_rejected` — **a strictly larger integer refused as a
replay**, because both land on the same double. One buggy sender reaches that in one step.

**What was decided.** `seq` MUST NOT exceed **`2^53 - 1`**. Sender MUST NOT emit above it; relay
MUST refuse with `400 bad_request`; receiver **SHOULD** reject. The bound is the derivation the
question itself proposed — the largest integer the two 64-bit receivers and the relay's double all
represent **exactly** — and `MAX_SEQ` is spelled `Number.MAX_SAFE_INTEGER` rather than as a
literal, per §3.1's lesson about round numbers.

**`SHOULD` on the receiver, deliberately.** A `MUST` would be a spec tightening ahead of two
shipping implementations that do not do it, which the eighth correction names as the same defect as
an implementation tightening ahead of its spec. The relay is the only ingress, so the receiver check
is defence in depth rather than the property being protected. **§3.2 states the non-conformance in a
measured note rather than implying conformance.** If ingress ever stops being single, this becomes a
`MUST` plus two code changes.

**STILL OPEN, and it is the half a reader should not assume closed.** The bound stops a channel
being wedged **out of range**; it does nothing for one wedged **in** range. A sender that emits
`9007199254740991` legitimately-shaped still refuses every later envelope in that direction until
the row expires or the pairing is deleted, and **the relay still exposes no channel-level reset
short of `DELETE /v1/{pairing}`**. This question's own last line asked for that to be settled
alongside the cap; it was not, because **it is a product decision** — a reset endpoint is a new
authenticated destructive route, and inventing one from a sandbox that cannot run
`Verify-Alpha.ps1` is the size-cap mistake's shape. **For Brandon, not for a cloud iteration.**

**Not verified here:** `Verify-Alpha.ps1` and `SyncLiveSmoke` did not run (no .NET). `SyncLiveSmoke`
pushes seqs from 1 and should be unaffected — **reasoning, not evidence**.

**Re-verify:** `AUDIT-REQUEST.md` **C-S2Q-1…7**.

---

## PQ-S4-2 — The `pull` response body is not defined anywhere, and the three implementations disagree about it

**Opened 2026-08-10 (S4 pull-page hardening, twelfth cloud iteration).** The phone was moved to the
engine-compatible reading in the same slice; this entry records the gap that made two readings
possible.

**Spec.** §2's route table defines `GET /v1/{pairing}/pull?since={seq}&dir={dir}` as "Fetch
envelopes for direction `dir` with `seq > since`" — **and stops there**. No response body is
specified: not the `envelopes` array, not `latest`, not their types, not whether either is
required. `latest` appears in the normative text exactly once, in §6.1's counter reconciliation
("reconciled on startup against the relay's current `latest` for the direction"), which uses the
field without ever defining where it comes from. §3 pins the *envelope* framing precisely; the
*page* that carries envelopes is pinned nowhere.

**What the three implementations actually do.**

| | `envelopes` | `latest` | element shape |
| --- | --- | --- | --- |
| Relay (`relay/src/channel.ts`, `pull`) | always emitted | always emitted, integer, `?? 0` | bare envelope JSON, spliced verbatim |
| Engine (`src/Sync/RelayClient.cs`, `PullAsync`) | **required** — `GetProperty` throws if absent | **required, strict** — `GetProperty("latest").GetInt64()` throws on a string | bare envelope only; no per-element `seq` is read |
| Phone, before this slice | optional, defaulted to `[]` | optional, defaulted to `0`, and a quoted `"9"` accepted | bare **or** a `{"seq":N,"envelope":…}` wrapper |

Three readings of one undefined contract. The relay's is the strictest producer, the engine's is the
strictest consumer, and the phone's was the most permissive of all — which is the wrong direction
under the interpretation rule, and was not a deliberate choice so much as an absent one.

**Why the phone's leniency was the dangerous end.** Defaulting an absent `latest` to `0` is not a
tolerant reading, it is a **silent** one: `latest` is what drives `moreAvailable` and §6.2's gap
check, so a relay that merely *omits* the field convinces the phone it is fully caught up. The
phone then reports a healthy, empty sync forever. One deleted field, no error anywhere, and the
blind relay is the party that controls the body. The engine has always refused this shape.

**Chosen here (engine-compatible, per the mission's interpretation rule):** `envelopes` and `latest`
are both **required and strictly typed**, matching `GetProperty`/`GetInt64` exactly — a quoted
number is refused on the phone because it is refused on the engine. Anything else is
`RelayResult.Unavailable`, never an exception and never a silently-empty page. The
`{"seq":N,"envelope":…}` wrapper shape is **still accepted**, deliberately unchanged in this slice —
see the finding below.

**A finding worth carrying, and it is the reason the wrapper shape was left alone rather than
removed.** Nothing produces it. The relay splices bare envelope JSON
(`relay/src/channel.ts`: `rows.map((r) => r.ciphertext).join(',')`); the engine's reader has no
branch for it; no shared vector contains a page at all; and the spec does not mention it. It is
accepted by exactly one implementation and emitted by none — and the `seq` it carries is the
relay's unauthenticated number, the one `SyncPump` already refuses to use (C-S4T-4). Removing it is
the obvious next step and is still **deliberately not a drive-by**: `RelayClientTest`'s
`pull returns envelopes unparsed…` asserts that shape directly, so removing it rewrites an existing
assertion, and that belongs in a slice that says so.

**To close.** A §2 (or a new §4.2) amendment defining the pull response body: both fields required,
`latest` an integer, elements bare envelopes, and one sentence on whether a client may accept the
wrapper shape — which, on the evidence above, should be *no*. Spec first, then the wrapper removal
on both sides. Doing it in the other order is the size-cap mistake again: an implementation
tightening ahead of the document it claims to implement.

**Re-verify:** `AUDIT-REQUEST.md` **C-S4P-6** and **C-S4P-7**.

### CLOSED 2026-08-11 (S4 pull-page semantics, thirteenth cloud iteration) — spec first, then the phone

**Spec half.** `docs/Sync-Protocol.md` gained **§2.1 Pull response body** on
`claude/s4-pull-request-semantics` (main repo, draft PR #33). It pins the strictest reading already
shipping — the engine's — rather than inventing a fourth: `envelopes` and `latest` both **REQUIRED**,
`latest` a bare JSON integer, elements **bare §3 envelopes** in ascending `seq`, the page explicitly
**truncatable** (so `latest` is the only "am I caught up" signal and a short page is not one), and an
unreadable body something a receiver **MUST NOT** turn into a successful pull of zero envelopes. The
`{"seq":N,"envelope":…}` wrapper is refused in as many words. §2's route-table row and §6.1's use of
`latest` now point at it.

**One clause was written too strongly and corrected in the same slice**, which is worth recording
because it is the size-cap mistake pointing the other way. The first draft said a receiver MUST
report an unreadable body "as an unavailability" — the phone does, and the engine's `PullAsync` lets
the parse throw instead. That would have made shipping engine code non-conformant on a question of
**error-type style**, not safety. The safety property both receivers already hold — never a silent
empty page — stays a MUST; the reporting mechanism is a SHOULD with both postures named. **A spec
tightening ahead of its implementations is the same defect as an implementation tightening ahead of
its spec**, and this one was caught only by going and reading `PullAsync` rather than assuming the
engine matched the phone.

**Phone half, in the same slice and deliberately second.** `RelayClient.parsePullPage` no longer
unwraps: an element is the envelope, `wire` is the whole element, and a wrapper simply fails the
receiver's strict §3 parse (`envelope` is not in `KNOWN_FIELDS`). The order matters — doing the
implementation first is the size-cap mistake, an implementation tightening ahead of the document it
claims to implement.

**Two existing `SyncPumpTest` cases were built on the wrapper, not one.** The queued note only
predicted `RelayClientTest`'s. Both were rewritten rather than deleted:

- `the cursor follows the envelope's authenticated seq, not the relay's page wrapper` is now
  `…, and nothing else can supply one`. **The property it defended is now structurally unreachable
  rather than defended**, which is the strongest form of this result: `parsePullPage` reads `seq`
  off the same element `EnvelopeJson.parse` reads it off, so for any element the two either agree
  (it parsed) or both fail (it did not). `SyncPump`'s rule 4 survives as defence in depth, and the
  test now says so instead of implying the check is load-bearing.
- `an envelope that does not parse is discarded and does not stall the cursor` was **passing for the
  wrong reason** after the change — it wrapped a malformed envelope to test §3's unknown-field rule,
  and post-change the wrapper was what failed, not the `surprise` field. It now uses a bare
  envelope, which is what it always meant.

**What did not change:** no vector (a page is not an envelope, and `SyncHarness` enumerates
`docs/sync-vectors/v1/*.json`, so a new file would move `$ExpectedOfflineTotal` — a number no
.NET-less machine can measure); no relay code (it already emits the conforming shape); no engine code
(see B-9).

**Measured:** `:core` **185 → 187 / 0 / 0** across 14 classes on the reduced probe; the new
`RelayClientTest` case was run against the pre-change parser and **failed** there while all 25
pre-existing cases passed.

**Re-verify:** `AUDIT-REQUEST.md` **C-S4S-1…7**.

---

## PQ-S4-3 — An envelope that fails to parse still advances the cursor to a number it made up

**Opened 2026-08-11 (S4 pull-page semantics, thirteenth cloud iteration).** Found while removing the
wrapper shape, and **it predates that change** — the wrapper removal neither caused it nor worsened
it, it merely made it the only remaining path by which a page's own numbers reach the cursor.

**The rule.** `SyncPump` (`core/…/SyncPump.kt`) advances its transport cursor per element:

```
val seq = header?.seq ?: envelope.seq
if (seq > cursorValue) cursorValue = seq
```

When the strict §3 parse fails there is no authenticated `seq`, so it falls back to the one the
element **claims** at its top level, read leniently by `parsePullPage`. The KDoc justifies this as
"safe because the item is discarded either way and the alternative is a permanent stall on one
malformed byte".

**Why the justification is narrower than the rule.** The *item* is discarded — that part is true and
safe. The *cursor* is not: it advances to an unauthenticated number of the relay's choosing. A blind
relay that appends one unparseable element carrying `"seq": 1000000` moves the phone's cursor past
every envelope between its current position and that value. Those envelopes are never re-requested,
because the cursor only ever moves forward. **That is history truncation, achieved without
decrypting anything** — the same attack C-S4T-4 and §2.1's wrapper refusal each closed one door on,
through the one door still open.

**Why it is not fixed here.** Both obvious repairs have a wrong version that compiles:

- *Do not advance on an unparseable element.* One corrupt byte then stalls the direction forever,
  which is the failure §6.2 explicitly forbids ("a gap MUST NOT stall the stream").
- *Advance by one instead.* Cheap, and it converts a truncation into a slow crawl — but it also
  desynchronises the cursor from real sequence numbers, and nothing in the spec says the next `seq`
  is `n+1` (the relay's TTL purge makes gaps legitimate).

A third option — bound the advance by the page's own `latest`, which the relay must report anyway —
looks best on the evidence, because it caps how far one bad element can move the cursor at a value
the client was going to compare against regardless. **It is a decision, not a bug fix**, and it
belongs in a slice whose title says so.

**To close.** A §2.1 or §6.2 sentence stating how far an unparseable element may move a receiver's
cursor, then the same rule in `SyncPump` and in the engine's reader. Spec first, again.

**Re-verify:** `AUDIT-REQUEST.md` **C-S4S-6**.

### CLOSED 2026-08-11 (fourteenth cloud iteration) — bounded by `latest`, spec first

**Decided: the third option**, as the entry above predicted it would be. New **§6.4** in
`docs/Sync-Protocol.md` (main repo, `claude/s4-pull-request-semantics`, PR #33) defines the
**transport cursor** — a thing this protocol had never named, because §6.2 governs
`highest_accepted` and stops there — and caps how far an element with no authenticated `seq` may
move it: it MAY advance by the element's claimed number, and MUST NOT advance past the page's own
`latest`. `SyncPump` then implements exactly that (`minOf(envelope.seq, page.latest)`), **in that
order**.

**Bounded rather than refused,** because refusing stalls the direction permanently on one corrupt
byte and §6.2 forbids that in as many words. The tie is broken by an asymmetry the original entry
did not state: a stall is **recoverable and loud** — `latest` still exceeds the cursor, the receiver
still reports more available, and the stream resumes on the next readable page — while truncation is
silent, permanent, and presents as a healthy fully-caught-up sync. When a receiver must choose
between them, it stalls.

**One correction to this entry's own framing, found while writing the test.** The original text said
bounding by `latest` "caps how far one bad element can move the cursor at a value the client was
going to compare against regardless", which overstates the protection. The bound does **not** stop a
hostile relay from skipping a receiver past envelopes it *currently holds* — `latest` is the relay's
own claim, and it could withhold those rows without any malformed element. What the bound stops is
the part that outlives the attack: an **unbounded** claim parks the cursor in the *future*, past
sequence numbers not yet issued, so every envelope the engine publishes from now until that number
arrives at a receiver that believes it is already past them. That is the forward-going, permanent
data loss, and it is what §6.4 removes. The spec states the distinction explicitly rather than
letting the stronger reading stand.

**Evidence.** `:core` 187 → **190 / 0 / 0** across 14 classes, both ends measured on the reduced
probe this session; `SyncPumpTest` 19 → **22**, no class added or renamed. **Two of the three new
cases fail against the pre-change source** while all 19 pre-existing cases pass. The third
(`an authenticated seq above latest still moves the cursor`) **passes on both sides by design** — it
pins that the bound applies to the unauthenticated path only, so that nobody "simplifies" the fix
into clamping every `seq` to `latest`, which would hand the relay the opposite lever.

**The engine half is NOT done.** `src/Sync/RelayClient.cs` has the same structure and no .NET exists
in this sandbox, so it is unwritten, not verified-absent. It is **unblocked and merely unwritten** —
see `BLOCKED.md`'s note; do not file it as a blocker.

**Re-verify:** `AUDIT-REQUEST.md` **C-S4C-1…6**.

---

## PQ-S2-3 — The relay's transport error vocabulary is documented nowhere, and two of its names collide with §7.2's payload codes

**Opened 2026-08-11 (S6 counter symmetry, fifteenth cloud iteration).** Not a blocker. Opened by
the slice that wrote §2.2, which pinned the **push** route's bodies and deliberately left the rest
observed-but-not-normative rather than inventing them from a sandbox.

**The two vocabularies.** §7.2 defines the sealed `error` *payload*: `{code, detail?, ref_seq?}`,
exchanged between engine and phone, invisible to the relay. The relay separately answers HTTP with
`{"error": …}` — a different key, a different namespace, and a different party. Nothing in
`Sync-Protocol.md` said so until §2.2.

**Measured.** `grep -rho "error: '[a-z_]*'" relay/src/*.ts | sort -u` yields eight distinct
transport codes. Against the document (`grep -c` on `docs/Sync-Protocol.md`, run 2026-08-11 on
`claude/s4-pull-request-semantics` before §2.2 was written):

| transport code | in §7.2? | in the doc at all, before §2.2? |
| --- | --- | --- |
| `replay_rejected` | yes | yes — but as a *payload* code |
| `too_large` | yes | yes — but as a *payload* code |
| `pairing_unknown` | yes | yes — but as a *payload* code |
| `bad_request` | **no** | **no — zero occurrences** |
| `unauthorized` | **no** | **no — zero occurrences** |
| `not_found` | **no** | **no — zero occurrences** |
| `method_not_allowed` | **no** | **no** |
| `upgrade_required` | **no** | **no** |

**Why the overlap is the dangerous half rather than the gap.** A third implementer who reads §7.2,
sees `replay_rejected`, and then receives `{"error":"replay_rejected","latest":7}` from the relay
has every reason to parse it as a payload error — looking for `code`, finding none, and falling
through to a generic failure. That is precisely how the 409's `latest` came to be discarded by the
engine (PQ-S6-3). The names matching is what makes the mistake feel correct.

**To close.** Either (a) extend §2.2 into a full transport-response section covering `create`,
`pair`, `pull`, `live` and `DELETE`, measured the same way push's was; or (b) state that transport
bodies are relay implementation detail beyond the fields §2.1/§2.2 pin, and that clients MUST key
off the HTTP **status**, treating `{"error": …}` as advisory. **(a) is recommended** — the 409's
`latest` is the proof that a transport body can carry a field the protocol depends on, so
"implementation detail" is already false once.

**Not done in this slice deliberately.** Pinning five more routes' bodies from a sandbox that cannot
run `Verify-Alpha.ps1` is the size-cap mistake's shape: a document tightening around behaviour whose
regression tests live somewhere this machine cannot reach. Push was pinned because §6.1 *depends* on
its 409; nothing depends on the others yet.

**Re-verify:** `AUDIT-REQUEST.md` **C-S6C-5**.

### CLOSED 2026-08-11 (seventeenth cloud iteration) — option (a), and the question's own table was short

Closed the recommended way: **§2.3** in the main repo
([careerseeker#36](https://github.com/ShivaClaw/careerseeker/pull/36), stacked on #33 → #32) pins
`create`, `pair`, `pull`, `live`, `DELETE` and `health`, measured under miniflare and written down
second. **`relay/src/` is byte-identical on that branch** — the section is descriptive, so nothing
new is refused, which is the direction §3.1's amendment makes load-bearing.

**This question's own table was wrong, and so was the §2.2 prose derived from it.** It listed
**eight** transport codes. Re-running **its own command on the commit it cited** yields **nine** —
`exists` was dropped in transcription, not by the grep:

```
$ git archive origin/claude/s4-pull-request-semantics relay/src | tar -x -C /tmp/x
$ cd /tmp/x && grep -rho "error: '[a-z_]*'" relay/src/*.ts | sort -u
bad_request  exists  method_not_allowed  not_found  pairing_unknown
replay_rejected  too_large  unauthorized  upgrade_required
```

`exists` is emitted by **two** routes for **two** different conditions (`create` on an existing
channel, `pair` on an already-stored completion), so it was never a marginal code.

**A second correction the measurement forced.** §2.2 says "two names appear in both vocabularies".
Measured, the intersection of the nine transport codes and §7.2's ten payload codes is **three**:
`replay_rejected` and `too_large` mean the same thing in each, and **`pairing_unknown` means
something different in each**. §2.2's sentence is *true as written* — it says two names collide
*with the same meaning* — but the third name is the dangerous one, and nothing said so.

**What the measurement turned up is bigger than the gap it was opened for**, and is recorded
separately as **PQ-S2-4**.

**Re-verify:** `AUDIT-REQUEST.md` **C-S2T-1** … **C-S2T-6**.

---

## PQ-S2-4 — A purged pairing answers `unauthorized`, so the phone's terminal "unpaired" state is unreachable

**Opened 2026-08-11 (S2 transport vocabulary, seventeenth cloud iteration).** Recorded, **not
fixed**: the fix touches the relay, the phone and a product decision, and none of the three can be
gated from this sandbox.

**Spec (§7.2).** The payload code `pairing_unknown` means "the relay has no Durable Object for this
pairing."

**What the relay actually answers for exactly that condition.** Measured under miniflare after
`DELETE /v1/{pairing}`, on every route:

```
401 {"error":"unauthorized"}   GET  /v1/{p}/pull?dir=e2p&since=0
401 {"error":"unauthorized"}   POST /v1/{p}/push
401 {"error":"unauthorized"}   GET  /v1/{p}/pair
401 {"error":"unauthorized"}   DELETE /v1/{p}
201 {"ok":true}                POST /v1/{p}/create      <- the id re-bootstraps
```

**The transport `pairing_unknown` is never emitted for this.** It fires only when the pairing id
fails the `p_` + 16-base64url-char shape check, which the Worker applies *before* it authenticates
(`relay/src/index.ts:56`). A well-formed id that was never created also answers **401**.

**What that costs the phone. Read, not executed — there is no Android SDK here (B-7).**

- `RelayClient.kt:283-284` maps `Unauthorized`/`Forbidden` → `RelayResult.Unauthorised` and **any
  404** → `RelayResult.PairingUnknown`.
- `OutboundQueue.kt:267-269` maps `Unauthorised` → `halted(SendHalt.UNAUTHORISED)` and
  `PairingUnknown` → `halted(SendHalt.PAIRING_GONE)`.
- `UNAUTHORISED` is the **recoverable** halt: `OutboundQueue.kt:288-290` clears it when "a fresh
  bearer is in hand (`RelayTokenLadder`)". `PAIRING_GONE` is **terminal** — `OutboundQueueTest.kt:269`
  is named `pairing_unknown is terminal and no clearing call revives it`.

So when the user is actually unpaired, the phone halts on the **recoverable** state and waits for a
fresher bearer that cannot exist, because the Durable Object holding the token hash is gone and
every bearer now fails. **The terminal state built for precisely this condition is never entered.**

**And it appears to be unreachable outright.** Of the routes the phone calls — `create`, `POST /pair`,
`push`, `pull`, `live`, `DELETE` — none can 404 under any measured condition: the malformed-id 404
is impossible because `RelayClient.init` requires `isValidPairingId(pairing)`, and the one route
that 404s transiently (`GET /pair`, answered `not_found` both before a completion is posted and
after the engine's one-shot read) **is never called by the phone**. That makes `SendHalt.PAIRING_GONE`
dead code on today's wire. **This half is derived by reading and is unverified by execution** — the
relay half above is measured, this one is not.

**Why nothing was changed here, and the reasoning is the size-cap lesson.** Three options were
considered:

1. **Make the relay answer 404 `pairing_unknown` on a purged channel.** This is the intuitive fix and
   it is the one to be most careful with: it changes what two shipping clients receive, and neither
   can be compiled or gated from this sandbox. It also **gives up a privacy property** — measured,
   the same pairing id re-bootstraps after `DELETE`, so a purged pairing is currently
   indistinguishable from one that never existed, and the relay never answers "did this pairing ever
   exist?" to a caller holding a wrong token. §2.3 pins the 401 for that reason.
2. **Change the phone's mapping** so `Unauthorised` becomes terminal after N failed token
   acquisitions. Kotlin, uncompilable here (B-7), and it converts a genuine transient — a bearer
   mid-rotation (§5.2.3) — into a terminal state, which is its own bug.
3. **Add a distinguishing field to the 401 body.** Cheapest on the wire, but it is a new normative
   field, and §2.3 has just finished saying every transport error body is exactly `{"error": …}`.

**Smallest human unblock — a decision, then two small changes on a machine with both gates:**
Brandon decides whether "the phone can tell it was remotely unpaired" is worth more than "a wrong
credential cannot learn a pairing id was ever real". If yes, option (3) plus a phone-side branch, and
§2.3's "every body is exactly `{error}`" rule is amended in the same change. If no, the phone's
`PAIRING_GONE` should be **deleted** rather than left as unreachable code that reads like coverage.

**Not filed in `BLOCKED.md`:** nothing is blocked. This is a decision that has not been made.

**Re-verify:** `AUDIT-REQUEST.md` **C-S2T-3**, **C-S2T-4**, **C-S2T-7**.

---

## PQ-S6-3 — The engine implements half of §6.1's resume rule, and its own comment states the other half

**Opened 2026-08-11 (S6 counter symmetry, fifteenth cloud iteration).** Engine-side, C#, **unblocked
and merely unwritten** — there is no .NET in this sandbox. Do not file it as a blocker.

**Spec (§6.1).** A sender MUST resume its counter above
`max(persisted_seq, relay_latest_seq_for_that_direction)`. This was already normative for the engine
before this iteration; the 2026-08-11 amendment generalised it to both senders, it did not create
the engine's obligation.

**What the engine does.** `src/Engine/Program.cs:288`:

```csharp
        startSeq: paired.LastE2pSeq);
```

The persisted term only. **The `max(…)` is not computed and the relay is never consulted** — no
`PullAsync(…, since: 0)` call exists on the startup path. The comment block at
`src/Engine/Program.cs:239-243` states the rule the code below it does not implement:

> The vault MUST persist the last e2p seq and this method MUST construct the publisher with
> `startSeq = max(vault.last_e2p_seq, relay latest e2p)` — Sync-Protocol.md §6.1.

**And the second half compounds it.** `RelayClient.PushAsync` (`src/Sync/RelayClient.cs:51-60`)
returns `res.StatusCode is HttpStatusCode.Created` — a bare `bool`. A 409 `replay_rejected` is
therefore indistinguishable from a timeout, a 400 or a 413, and the `latest` the relay puts in that
body (§2.2) is **discarded unread**. So the engine can neither reconcile up front nor recover from
the refusal that tells it to.

**What actually happens today, stated precisely, because it is milder than it first looks.**
`SyncPublisher` assigns `seq` with `Interlocked.Increment` *before* the sink runs
(`src/Sync/SyncPublisher.cs:90`) and the vault records the mark only on success
(`src/Engine/Program.cs:285`). So a stale vault does not deadlock: each refused push burns one seq
and the next attempt is one higher, and the counter climbs back to the relay's mark on its own.
**The cost is one dropped envelope per burned seq**, and if the vault is behind by N — a restore
from backup, a rolled-back store, a run of pushes whose responses were lost — that is N envelopes
silently discarded, *including the recovery `snapshot`* if it falls in the run. Every one of them
returns `false` to a caller that has no retry. So §6.1's named catastrophe is **mitigated into a
window rather than prevented**, and nothing reports the window.

**To close (engine only; the spec half is done).** Two commits, in either order:

- Make `PushAsync` return a result that distinguishes 409 from every other failure and carries the
  body's `latest` — the phone's `RelayResult.Conflict(latest)` is the shape to mirror, and the field
  is now pinned in §2.2 rather than being a relay implementation detail.
- On the startup path, take `max(paired.LastE2pSeq, RelayClient.PullAsync(dir: "e2p", since: 0).Latest)`
  before constructing the publisher, per §6.1. `PullAsync` already returns `Latest`, so this is a
  read the client can already do.

Both need `Verify-Alpha.ps1`, and `SyncHarness` is where the regression test belongs (it already
covers the resumed-publisher case at `tests/SyncHarness/Program.cs:419-425`, with `startSeq: 41`).
**Adding a harness assertion moves `$ExpectedOfflineTotal`** (598) and every doc that reports it —
the drift trap in `CLAUDE.md`. That is a local session's slice.

**Re-verify:** `AUDIT-REQUEST.md` **C-S6C-3** and **C-S6C-4**.

---

## PQ-ER-1 — The strict parse runs ahead of the version check, so a v2 dialect reads as malformed

**Opened 2026-08-11 (receive-order tests, nineteenth cloud iteration).** **Severity: diagnosability,
not safety.** Stated first because a question filed as though it were a defect is its own kind of
drift, and this one is small.

**Behaviour, executed rather than read** (`:core:test` via `scripts/core-probe.sh`, test
`the strict parse runs ahead of the version check, so a v2 dialect reads as malformed`):

| envelope | answer |
| --- | --- |
| `v=2`, plus an unknown top-level field | **`decrypt_failed`** |
| `v=2`, no unknown field | `version_unsupported` |

`EnvelopeReceiver.receiveWire` calls `EnvelopeJson.parse` before the state machine sees anything,
and §3's unknown-top-level-field rule is the parser's first check. So a **v2 sender that both bumps
`v` and adds a field** — the ordinary shape of a protocol upgrade — is told its envelope is
malformed, and cannot learn that the version is the problem.

**The rejection itself is correct in both rows, and that is why this is not a defect.** §3 requires
unknown fields to be rejected rather than ignored, and `EnvelopeJson`'s docstring gives the reason
the parse comes first: *if the sender is speaking a dialect this receiver does not know, nothing
else it says should be interpreted.* That argument is sound. The cost is only that the **error code
is less informative than it could be**, and it lands on exactly the upgrade path §3's rule exists to
protect.

**Why nothing was changed.** Reordering — version before the unknown-field check — means reading `v`
out of a document this receiver has already decided it does not understand, which is the reasoning
`EnvelopeJson` rejects. Adding a distinct `version_unsupported` pre-check is a **normative change to
§3's parse order**, it would need the same edit in the C# engine to avoid drift, and **neither gate
is runnable from a cloud sandbox** (no .NET; three of the android gate's four tasks need the SDK).
A phone that reports differently from the engine here is the field bug the mission's
engine-compatible interpretation rule names.

**Also worth knowing before anyone acts on this:** the engine's inbound path **cannot reach this
question today**. `src/Sync` has no inbound wire-JSON parser at all — that is **B-6**, the same gap
that blocks PQ-A2-3's `invalid-unknown-field` vector. So there is currently **no C# counterpart to
`receiveWire`** to keep in step, and whoever closes B-6 should decide this at the same time rather
than building the parser and then reordering it.

**Smallest resolution — a decision, then one edit per implementation, on a machine with both gates:**
does a v2 sender need to distinguish "your version is unsupported" from "your envelope is
malformed"? If yes, both receivers check `v` before the unknown-field rule and §3 says so. If no,
this entry closes as **working as intended** and the test that pins it stays as documentation of a
deliberate choice.

**Not filed in `BLOCKED.md`:** nothing is blocked.

**Re-verify:** `AUDIT-REQUEST.md` **C-ER-7**.

---

## PQ-B64-1 — Non-canonical base64url trailing bits are accepted, and whether .NET agrees is unmeasured

**Opened 2026-08-12 (crypto-primitive tests, twentieth cloud iteration).** **Severity:
conformance/interoperability, not safety on today's code paths.** Stated first, because this one
reads worse than it is and the narrow version is the true one.

**Behaviour, executed rather than read** (`:core:test` via `scripts/core-probe.sh`, tests
`non-canonical trailing bits are ACCEPTED` and
`spare bits and therefore spellings are decided by length mod three`):

`Base64Url.decodeOrNull` delegates to `java.util.Base64.getUrlDecoder()`, which **ignores the unused
low bits of the final character** rather than requiring them to be zero. So several distinct strings
decode to identical bytes:

| input | decodes to |
| --- | --- |
| `QQ` | `0x41` |
| `QR`, `QV`, `QZ` | `0x41` — same byte, three other spellings |

**How many alternate spellings exist is decided by the field's length mod 3**, measured against the
JDK directly:

| field | bytes | `len % 3` | spare bits | spellings |
| --- | --- | --- | --- | --- |
| `nonce` | 12 | 0 | 0 | **1 — cannot be re-spelled** |
| key / `secret` | 32 | 2 | 2 | 4 |
| `engine_pub` (uncompressed P-256) | 65 | 2 | 2 | 4 |
| `sig` (raw P-256) | 64 | 1 | 4 | 16 |

**This is materially narrower than "base64url fields are malleable", which is what the first draft
of the test file assumed.** It tried to build a second spelling of a 12-byte nonce; there is none,
and the draft's own guard assertion is what caught it.

**Why it is not a defect today, each half checked rather than argued:**

- **Not a replay bypass.** `seq` is inside the AAD (`Protocol.kt:143`) and the receiver's replay
  check is on `seq`, so a re-spelled copy is refused as a duplicate like any byte-wise one.
- **Not a signature bypass.** `signatureInput` binds the `nonce` **string** — which is immune, per
  the table — and the ciphertext by the **hash of its decoded bytes** (`PairingDerivation.kt:62-65`).
  A re-spelled ciphertext therefore signs *identically* and opens identically.
- **Both encoders only ever emit canonical output.** Nothing in either implementation produces an
  alternate spelling; reaching this needs a hostile or buggy third writer on the wire.

**The one consequence that survives, stated because it is a real constraint on future code:** an
envelope's **wire form is not uniquely determined**. Two distinct byte strings can be the same
envelope. So an envelope must never be de-duplicated, cached, or authenticated by hashing its wire
bytes — that would see two envelopes where the protocol sees one. Nothing does this today.

**What is genuinely open, and what cannot be settled from a cloud sandbox.** If .NET's decoder
**refuses** non-canonical trailing bits where the JDK's accepts them, the engine and the phone
disagree about whether a given envelope is *well-formed*: one opens it, the other answers
`decrypt_failed`. That is a conformance divergence in `docs/Sync-Protocol.md` §3's terms, and **no
vector covers it** — the generator emits canonical output, so no existing vector can express the
question. This iteration had no .NET and did **not** measure the engine side.

**Why nothing was changed.** Tightening the Kotlin to require canonical trailing bits would make the
phone stricter than an engine whose behaviour is unmeasured — the "more correct than the engine"
field bug the mission's engine-compatible interpretation rule names. The direction of the divergence
has to be measured before either side moves.

**Smallest resolution — one command on a machine with .NET, then a decision:**

```csharp
// Does the engine's decoder accept a non-canonical final character?
// "QQ" and "QR" both carry the single byte 0x41 under a permissive decoder.
Console.WriteLine(Convert.ToHexString(Base64Url.DecodeFromChars("QQ")));
Console.WriteLine(Convert.ToHexString(Base64Url.DecodeFromChars("QR"))); // throws, or 41?
```

If .NET **throws**, the two implementations already disagree and §3 must say which is normative —
almost certainly "reject", with the Kotlin tightened and an `invalid-noncanonical-b64u` vector added
alongside PQ-A2-3's (**both blocked by B-6** for the same reason: the engine has no inbound wire-JSON
parser to feed a vector through). If .NET **accepts**, the two agree, §3 records the permissive
reading as deliberate, and this closes as working as intended with the tests above as documentation.

**Not filed in `BLOCKED.md`:** nothing is blocked. The vector half would be, and it is already B-6's.

**Re-verify:** `AUDIT-REQUEST.md` **C-CR-5**, **C-CR-6**.

---

## PQ-AAD-1 — The AAD is not an injective encoding of the header, in two independent ways

**Raised:** 2026-08-12, twenty-first cloud iteration, while writing `SyncCryptoTest`.
**Severity:** latent. Neither half is reachable from a conforming sender today, and the tests
that pin them say so at their own sites. Filed because the AAD's *whole job* is to bind the
header, and both halves rest on validation that `EnvelopeJson` does not perform.

**Where.** `EnvelopeHeader.aad()` (`core/…/Protocol.kt:143-144`) builds

```
v=$v|pairing=$pairing|dir=${dir.wire}|seq=$seq|ts=$ts|key_id=$keyId
```

and `SyncCrypto.gcm()` (`core/…/crypto/SyncCrypto.kt:53`) feeds it to GCM as
`aad.toByteArray(Charsets.US_ASCII)`. The engine builds the same string in `src/Sync/`
(ShivaClaw/careerseeker); its charset is **unmeasured** — no .NET in a cloud sandbox.

`EnvelopeJson.parse` regex-checks `pairing` and type-checks `v`/`seq`/`dir`, but takes **`ts`
and `key_id` as arbitrary JSON strings with no charset or content check at all**
(`EnvelopeJson.kt:55-56`). They are also the last two fields of the AAD, and adjacent.

**Half 1 — `US_ASCII` is lossy, so the AAD does not bind non-ASCII content.** Java's encoder
replaces every unmappable character with `?` (0x3F). Measured: `é`, `è`, `Ж` and `😀` all
become the *single* byte 0x3F, so they collide with each other and with a literal `?` (a
surrogate pair collapses to one byte, not two). An envelope sealed under `ts=…Zé` opens under
`ts=…Zè`, `ts=…Z😀` and `ts=…Z?`. §5.4's signature input is encoded the same way, so command
signatures inherit it.

**Half 2 — the `|`/`=` framing is ambiguous, with no non-ASCII needed.** Content can move
across the `|key_id=` boundary without changing a byte:

```
ts = "T"           key_id = "K|key_id=Z"   ->  …|ts=T|key_id=K|key_id=Z
ts = "T|key_id=K"  key_id = "Z"            ->  …|ts=T|key_id=K|key_id=Z
```

Two distinct header tuples, one AAD, and each opens the other's envelope.

**Why neither is a live bypass, checked rather than waved through.** A header rewrite only
survives authentication if the *original* bytes and the rewritten bytes agree after encoding.
Conforming senders emit RFC 3339 timestamps and generated key ids — pure ASCII, no delimiters
— so every mutation of a real envelope changes a byte and fails the tag. The collision classes
are reachable only if the genuine sender put a non-ASCII character or a `|`/`=` into `ts` or
`key_id` to begin with, which neither implementation does. Half 2 is self-limiting a second
way: `key_id` selects the decryption key *before* the AAD is built, so a rewritten form must
still name a key the receiver holds.

**What is genuinely open, and it is the cross-implementation half.** If `src/Sync/` builds its
AAD with **UTF-8** rather than ASCII, the two sides agree on every all-ASCII header and
disagree on every other one — each computing different AAD bytes for the same envelope and
each answering `decrypt_failed` on the other's traffic. Identical in shape to **PQ-B64-1**.

**No vector can express this, and the vector set shows why it was missed.** 26 vendored
vectors, 23 with an `aad` field, and **zero** carry a non-ASCII byte in the AAD. The one
vector that does carry non-ASCII — `heartbeat-unicode.json`, whose note says it "catches
implementations that treat UTF-8 as Latin-1 or mangle surrogate pairs" — puts it in the
**plaintext**, and its AAD is plain ASCII. The suite deliberately tests the body's charset and
has never tested the header's.

**Why nothing was changed.** Tightening the Kotlin — validating `ts`/`key_id`, or switching to
UTF-8 — would make the phone stricter than an engine whose behaviour is unmeasured, the
"more correct than the engine" field bug the mission's interpretation rule names. Either fix
must be symmetric and land with the spec.

**Smallest resolution — read one line of C# on a machine with .NET, then decide:**

```
grep -rn "ASCII\|UTF8\|GetBytes" src/Sync/EnvelopeReceiver.cs src/Sync/SyncPublisher.cs
```

If the engine uses UTF-8 and the phone ASCII, that is a live conformance divergence and §3
must name one normative. If both use ASCII they agree today, and the remaining work is §3
constraining `ts` and `key_id` to a delimiter-free ASCII subset, so the AAD is injective by
construction rather than by the accident of what senders happen to emit.

**Not filed in `BLOCKED.md`:** nothing is blocked. The measurement is one grep.

**Re-verify:** `AUDIT-REQUEST.md` **C-SC-2**, **C-SC-5**, **C-SC-6**.

---

## PQ-SC-1 — `:core`'s crypto is tested only on `SunEC`, and three of its defences are unobservable there

**Raised:** 2026-08-12, twenty-first cloud iteration, out of `SyncCryptoTest`'s mutation run
rather than out of its plan.
**Severity:** a bound on the evidence, not a defect. Nothing is known to be wrong.

**The measurement.** Eight mutations were applied to `SyncCrypto.kt` and reverted. Four were
caught. **M6** survived because it is semantically redundant (the explicit 64-byte gate
duplicates an `IndexOutOfBoundsException` the `try` already converts to `false`) — not a gap.
The other three survived for one shared reason:

| Mutation | What it deletes | Why no test on this JVM can see it |
| --- | --- | --- |
| **M2** | the `0x00` positive pad in `toDerInteger` | `SunEC` **accepts** an unpadded negative DER INTEGER (while rejecting the non-minimal encoding M1 produces) |
| **M7** | `leftPad` on the ECDH secret | `SunEC`'s `generateSecret()` returns a **fixed-width 32-byte** array even when the X coordinate's top byte is zero, so the pad never fires |
| **M8** | the `catch` in `verifySignature`, rethrowing instead | `SunEC`'s `KeyFactory.generatePublic` returns normally for an off-curve point **and** for coordinates above the field prime; invalidity surfaces as `verify() == false`, never as an exception |

**The gap.** `:core` is a pure-JVM module, so `:core:test` runs on the JDK's `SunEC` — here,
and on `windows-latest` in CI. The phone runs on Android, where the provider is **Conscrypt**.
These three lines are precisely the ones that matter when a provider behaves differently from
`SunEC`, and they are the three no JVM test can exercise. `:core:test` green is therefore not
evidence about the codec's behaviour on a device, and this is the first record to say so.

**Why this is not alarmism.** All three are *defensive*: the pad and left-pad make the encoding
conform to DER/§5.2 regardless of provider, and the catch converts provider exceptions into the
`false` that §7.2 expects. They are insurance against a stricter provider, and the fact that
they cannot be observed on `SunEC` is the reason they should stay rather than a reason to
delete them as dead code. **The risk is deleting them, not keeping them** — and this entry
exists so that a future session running a coverage tool does not read them as unreachable.

**Where it would bite, worst case.** If Conscrypt returned the BigInteger-minimal ECDH secret,
`leftPad` is the only thing stopping a 31-byte IKM reaching HKDF — a *different* IKM, so the
two ends would derive different directional keys for roughly **1 pairing in 256**: far too
rare to find by hand, far too common never to happen in the field.

**Smallest resolution — needs a device or emulator, which B-4/B-7 put out of reach here:**
run the three assertions as an instrumented (`androidTest`) case on a real Android runtime and
compare against the JVM results recorded above. That is the only way to learn whether
Conscrypt agrees, and it is the same lane the emulator work already needs.

**Not filed in `BLOCKED.md`:** nothing this iteration attempted was obstructed, and no rung
depends on the answer. Filing it as a blocker would send the next session hunting a fault
nobody has established exists.

**Re-verify:** `AUDIT-REQUEST.md` **C-SC-7**, **C-SC-8**.

---

### PQ-AAD-1 ANSWERED 2026-08-12 (twenty-second cloud iteration) — they agree by name and diverge on surrogate pairs

The twenty-first run opened this and said it needed "one `grep` on a machine with .NET". **The grep
alone would have given the wrong answer**, which is worth recording as much as the result.

**The grep half.** The engine encodes its AAD — and §5.4's signature input — with
`Encoding.ASCII.GetBytes` (`src/Sync/EnvelopeCodec.cs:31,45`, `src/Sync/DeviceSignature.cs:38`),
matching the phone's `US_ASCII`. **Not UTF-8.** So the feared "engine encodes UTF-8, phone encodes
ASCII" divergence does **not** exist, and by name the two agree.

**The half a grep cannot reach.** Both were then *run*, in the same session (`C-WP-11`):

| input | Java `US_ASCII` | .NET `Encoding.ASCII` | |
| --- | --- | --- | --- |
| `é`, `è`, `Ж` (BMP) | `543F` | `543F` | agree |
| **`😀` (surrogate pair)** | **`543F` — one byte** | **`543F3F` — two** | **DIVERGE** |
| literal `?` | `543F` | `543F` | agree |

**Java collapses a surrogate pair to a single `?`; .NET emits one replacement per surrogate.** So
any supplementary-plane character in `ts` or `key_id` produces **different AAD bytes on the two
sides**, and the §5.4 signature input inherits it.

**Severity, stated precisely so it is not over-read.** This **fails closed**. The sender seals under
its own AAD and the receiver opens under its own, so the outcome is a tag mismatch →
`decrypt_failed`: an **interop** failure, not an authentication one, and not a forgery path. It is
also **unreachable for a conforming sender** — `ts` is RFC 3339 and `key_id` is an opaque ASCII id.
But **nothing enforces that**: `EnvelopeJson` on *both* sides takes those two fields as arbitrary
strings with no charset check. The twenty-first run found the phone-side lossiness; this supplies
the cross-implementation half.

**Deliberately not fixed, and the reason is the mission's own rule.** The clean resolution is to
constrain `ts` and `key_id` to ASCII in §3, which makes the divergence structurally unreachable and
can be added to both parsers in one coordinated change. That is **wire-visible and touches both
implementations**, so it is a gate for Brandon. Adding a charset check to the C# parser alone would
make the engine stricter than the phone — the named field bug pointing the other way.

**Recommended resolution when gated:** §3 gains "`ts` and `key_id` MUST be ASCII; a receiver rejects
otherwise with `decrypt_failed`", plus one shared vector. **Do not** silently switch either side to
UTF-8: that changes the AAD for every existing envelope and would break every paired device.

---

## PQ-DUP-1 — §3 says nothing about duplicate top-level fields, and .NET takes the last one

**Opened 2026-08-12 (twenty-second cloud iteration)**, found while probing the new parser's totality.

§3 enumerates nine fields and requires unknown ones be rejected. It says **nothing about a field
appearing twice**. Measured on the engine side:

```
{…,"seq":12,…,"seq":99,"v":7}  ->  parses OK as seq=99, v=7
EnumerateObject() still sees all 10 properties
```

`JsonElement.TryGetProperty` returns the **last** duplicate. The unknown-field check enumerates
every property, so a duplicated **unknown** field is still caught — the gap is only about duplicated
**known** fields.

**The phone half is NOT measured and is not claimed here.** `kotlinx.serialization`'s `JsonObject`
is a map, so last-wins is the likely behaviour and the two probably agree — but "probably" is not
evidence, and a cloud session that can run `:core` should settle it with one test rather than
inherit this sentence.

**Severity: low, and it is not a bypass.** `seq`, `v`, `pairing`, `dir`, `ts` and `key_id` are all
in the AAD, so a duplicate that changes any of them changes the AAD and the envelope fails to
decrypt. The reachable case is a duplicate of `nonce`, `ciphertext` or `sig`, none of which lets an
attacker produce a valid tag they could not already produce.

**To close:** (a) measure the Kotlin side; (b) if the two agree, add one sentence to §3 pinning
last-wins (or first-wins) so it is a decision rather than an accident, with a shared vector; (c) if
they disagree, that is a real divergence and it takes priority over (b).

**Do not** tighten one side alone before (a).

---

## PQ-A2-5 — The `entitlement_ack` vectors are enforced on one side only

Not a spec defect. A **conformance** gap, filed because §10 states a property that does not
currently hold for this kind, and the shortfall is invisible from either codebase alone.

**What §10 promises.** "**Both** the C# `SyncHarness` and the Kotlin `:core` tests read these same
files, so a divergence between the two implementations fails CI instead of surfacing as a pairing
bug in the field." That is the whole reason the generator is Node: a generator written in the same
language as its verifier proves only that the language agrees with itself.

**What is actually true for `entitlement_ack`, as of 2026-08-12.**

| side | how it consumes the two ack vectors |
| --- | --- |
| engine (`tests/SyncHarness/Program.cs`) | **reads the files**, and asserts the built body is **byte-identical** to each vector's plaintext, and that re-sealing reproduces `ciphertext_b64u` exactly |
| phone (`core/.../EntitlementAckTest.kt`) | **transcribes** the two bodies verbatim into the test source; the vector files are never opened |

The Kotlin file states this itself and does not claim otherwise. The reason is structural, not
sloppiness: the android repo vendors `docs/sync-vectors/` **pinned at main-repo commit `679a317`**,
and both ack vectors postdate that pin, so they are not present in
`core/src/test/resources/sync-vectors/` to be read.

**Why it matters despite both sides currently agreeing.** A transcription is a snapshot of the
vector at the moment someone copied it. It cannot fail when the vector changes, because it is not
reading the vector — so the divergence §10 exists to catch (engine and phone drifting apart on
field order, on optionality, on an omitted-vs-null `order_id`) would be caught on the engine side
and **silently pass on the phone side**. The two implementations agree today; nothing enforces that
they still will.

**To close:** re-vendor `docs/sync-vectors/` into the android repo at a commit that includes the ack
vectors, then convert `EntitlementAckTest`'s transcribed constants into a vector-driven assertion in
`ProtocolVectorsTest` alongside the other kinds. That is a re-vendor plus a test rewrite — both
cheap, and neither doable in a session that cannot run `:core:test` (B-7). Nothing prevents the work;
this is not a blocker.

**Until it closes**, `docs/Sync-Protocol.md` §10.2 says in the document itself that these vectors are
evidence about **one** implementation. Do not cite the ack vectors as cross-implementation evidence.

---

## PQ-CUR-1 — §6.4's carve-out is drawn for parse failures, and a failed AEAD tag falls through it

**Opened 2026-08-13** (twenty-fifth cloud iteration) while writing the engine's inbound pump
(`src/Sync/InboundPump.cs`, careerseeker draft PR #39). **Not a blocker**, and it is a spec defect
first and an implementation gap second.

**The rule as written.** §6.4 (careerseeker PR #33) says the transport cursor:

- MUST NOT move backwards;
- MUST advance only to a `seq` **recovered from the sealed bytes** (§4.1), except under the bound;
- when an element **fails the §3 parse**, MAY advance by the element's claimed top-level `seq`, but
  MUST NOT advance beyond the page's `latest`.

**The case it does not cover.** A `seq` is recovered from the sealed bytes only when the **AEAD tag
verifies** — the seq lives in the AAD, and the tag is what makes it a fact rather than a claim.
**Parsing is not authenticating.** An envelope can pass the §3 parse completely — well-formed JSON,
exactly the nine known fields, a valid `p_`-prefixed pairing id, a typed `seq`, a 12-byte base64url
nonce, a base64url ciphertext — and still be bytes the relay invented. It parses. Its header `seq` is
authenticated by nothing at all.

That element did **not** fail the §3 parse, so §6.4's carve-out does not apply to it; and its seq was
**not** recovered from the sealed bytes, so the MUST forbids advancing to it. Read literally, §6.4
therefore says the cursor may not move at all for a parseable envelope whose tag fails — which is the
permanent stall §6.2 forbids in as many words, reachable by serving one crafted element.

**What the two implementations do today.**

| | behaviour on a parseable envelope whose tag fails |
| --- | --- |
| engine (`InboundPump`, PR #39) | advances **bounded by the page's `latest`**, same as a parse failure |
| phone (`SyncPump.kt:260`) | advances by the header `seq`, **unbounded** |

The phone's line is `val seq = header?.seq ?: minOf(envelope.seq, page.latest)` — the bound is applied
only on the `null` branch, i.e. only when the parse failed. So on the phone, one well-formed
undecryptable element claiming `seq: 1000000` walks the cursor past every envelope below it, and since
the cursor never moves backwards those envelopes are never requested again. **That is the history
truncation §6.4 exists to prevent, performed without decrypting anything, through the door §6.4 did
not think to close.**

**Severity, stated precisely.** No interop risk: the transport cursor is local state and never appears
on the wire, so an engine stricter than the phone merely re-requests what the phone would skip. This
is **not** the mission's "a phone more correct than the engine is a field bug" case in either
direction. The exposure is availability and silent data loss on the phone, against a relay willing to
serve a page it made up — which §2 explicitly says to assume it may.

**To close, in this order** (the order matters, and is why PR #39 did not just fix the Kotlin):

1. **Amend §6.4 on careerseeker PR #33**, the branch that owns the section — generalise the carve-out
   from "fails the §3 parse" to "has no authenticated `seq`, whether because the parse failed or
   because the AEAD tag did not verify". PR #39 already implements this reading and **cites a section
   its own branch does not contain** (§6.4 is on a sibling branch; PR #39's `Sync-Protocol.md` has
   §6.1–§6.3 only), so the amendment also removes a dangling citation from shipped code.
2. **Then** bound the phone's advance the same way and add the case to `SyncPumpTest`. Verifiable in a
   cloud session: `:core` compiles and runs via `scripts/core-probe.sh` (B-7 never covered `:core`).

Doing (2) before (1) would write a rule into the phone that the normative document does not state —
the thirteenth run's §2.1 defect, in reverse.

**Re-verification:** `AUDIT-REQUEST.md` C-IP-13 (the section is absent from the branch that cites it)
and C-IP-14 (the phone's unbounded advance, read off the shipping line).

**CLOSED 2026-08-13 (twenty-sixth cloud iteration), in the prescribed order and on both sides.**

1. **§6.4 amended** — careerseeker `claude/s4-pull-request-semantics` commit `3a8dfdd` (draft PR #33).
   The carve-out now covers "every other element — one that fails the §3 parse, *and* one that parses
   and is then rejected for any reason, **the AEAD tag included**", with a *Parsing is not
   authenticating* paragraph stating that the boundary is **accepted vs. not accepted**. Three later
   sentences saying "malformed element" were widened to "unauthenticated element", since the rule now
   covers well-formed elements that no key opens.
2. **Phone bounded to match** — `SyncPump.kt`: the advance moved *below* `receiver.receive` and split
   three ways, with one `advanceBounded` helper shared by both unauthenticated paths and the accepted
   path left deliberately unbounded. `:core` **272 → 276, 0 failed**; **M1 proves the three new tests
   fail against the pre-change source and the other 272 do not**, so the pre-existing suite could not
   see this bug.

**One claim in this question turned out to be wrong and is corrected here.** Step 1 above said the
amendment "also removes a dangling citation from shipped code". **It does not.**
`claude/s4-pull-request-semantics` and `claude/s5-inbound-pump` are **siblings**
(`git merge-base --is-ancestor` exits 1), so `InboundPump.cs` still cites a §6.4 its own branch does
not contain. What the amendment fixes is the section's *content*; the citation resolves **on merge of
both PRs**, and not before. The two must still land together.

**A gap the closure found, which the question did not predict.** Mutating away the
`bounded > cursorValue` guard left the whole suite green at 275/0 — §6.4's **first** bullet ("MUST NOT
move backwards") was a normative MUST that no test on this side asserted, and the new bound is what
makes it reachable, because `minOf(claimed, latest)` takes the relay's `latest` whenever it is
smaller. Closed with a fourth test; the mutation now fails exactly that test.

**Re-verification:** `AUDIT-REQUEST.md` **C-CUR-1…13**.

---

## PQ-LAT-1 — §3.2 caps `seq`, and never says the cap reaches `latest`

**Opened 2026-08-13** (twenty-eighth cloud iteration) while giving `RelayClient.PullAsync` a range
check on `latest` (careerseeker draft PR #45). **Not a blocker.** The engine now enforces the cap on
`latest`; this question asks the spec to *say* what the engine derived.

**The rule as written.** §3.2 (careerseeker PR #35, `claude/s2-seq-bound`) caps `seq` at
`2^53 - 1` and states three obligations:

- a **sender** MUST NOT emit a larger value;
- the **relay** MUST reject one with HTTP 400 `bad_request`;
- a **receiver** SHOULD treat a larger value as a structural rejection (`decrypt_failed`, §3).

All three are about the `seq` *inside an envelope*. §2.1 defines the pull page's `latest` as "a bare
integer" and says nothing about its range.

**Why that leaves a hole.** `latest` is not an independent number: it is `MAX(seq)` over the rows the
relay holds for the direction (`relay/src/channel.ts:206`), and every one of those rows passed the
relay's own seq check. So `latest` *does* inherit `seq`'s domain — but **by derivation, not by
statement**, and the derivation runs through the relay's implementation rather than through the
document. A receiver reading only §2.1 and §3.2 is entitled to accept any integer it can represent.

**What both implementations did, measured.** Neither range-checked it. The engine's
`TryGetInt64` and the phone's `strictLong` (`RelayClient.kt:258`, `toLongOrNull()`) fix the type and
the width and nothing else, so both accepted the whole of `Int64`:

```
latest = -1                     -> Ok, carried through
latest = 9007199254740992       -> Ok   (2^53, one past §3.2's cap)
latest = 9223372036854775807    -> Ok   (Int64.MaxValue)
latest = 10000000000000000000   -> refused (overflows Int64, not by any bound)
```

The last line is the trap: the two values that *are* refused are refused by the width of the integer
type, which looks like a range check and is not one. The engine now refuses `latest < 0` and
`latest > Protocol.MaxSeq`; **the phone still does not**, and is left alone deliberately — an engine
stricter than the phone costs a conforming relay nothing, while changing the phone here would need
the android gate this sandbox cannot run.

**To close.** One sentence in §2.1 or §3.2: `latest` is a `seq` and carries `seq`'s range, `0`
meaning the direction holds nothing. Then bound the phone's `strictLong` call site to match and add
the case to `RelayClientTest`.

**Re-verification:** `AUDIT-REQUEST.md` **C-LAT-1…4**.

---

## PQ-LAT-2 — §6.4's bound on an unauthenticated `seq` is supplied by the party it defends against

**Opened 2026-08-13** (twenty-eighth cloud iteration), by measuring what the range check above does
**not** fix. **Not a blocker, and it is the more serious of the two.** Recorded rather than fixed:
the fix is a protocol change affecting both receivers.

**The rule as written.** §6.4 says an element with no authenticated `seq` MAY advance the transport
cursor by the number it *claims*, but MUST NOT advance past **the page's own `latest`**. Both
implementations do exactly that, and `InboundPump`'s docstring argued the bound "denies a hostile
relay a second, independent lever, because `latest` is already the number it must publish to say
there is more".

**That argument is wrong, and this iteration measured it wrong.** `latest` and the crafted element
arrive in the **same response, from the same party**, and nothing authenticates either one. The
"independence" is an assumption about an honest relay, inside a rule written for a dishonest one.
Measured against the shipping `InboundPump`, one unreadable element claiming `seq: 1000000`:

| page's `latest` | resulting cursor |
| --- | --- |
| `5` (honest) | **5** — bounded, as §6.4 intends |
| `Int64.MaxValue` (inflated) | **1000000** — the bound is a no-op |

So the history truncation §6.4 exists to prevent is reachable in full: inflate `latest`, serve one
crafted element, and the cursor parks past every genuine envelope below it. Because the cursor never
moves backwards, those envelopes are never requested again, and the direction presents as a healthy
fully-caught-up sync.

**What PR #45's range check does and does not do.** It lowers the reachable ceiling from `2^63 - 1`
to `2^53 - 1`. That is a real narrowing against a garbage or rounded counter, and it is **not** a fix
for this: `2^53 - 1` is still astronomically past any counter a real deployment reaches, so the
attack survives the change unchanged. The two harness assertions that pin the table above are
labelled as pinning an **open weakness** — if a later slice closes it, they SHOULD fail.

**Why it was not fixed here.** Every in-band bound is relay-supplied, so closing it needs a bound
that is not — and the obvious candidate does not survive one check.

> **Corrected while drafting this entry, before it shipped.** The first draft proposed
> `min(page.latest, cursor + elements_served)`, reasoning that a page cannot legitimately advance the
> cursor past the number of elements it actually served. **§6 says otherwise in as many words:** a
> receiver MUST accept `seq > highest_accepted` **"including gaps — the relay's TTL purge creates
> them"** (`docs/Sync-Protocol.md:568`). After a purge a page can legitimately serve one element at
> `seq: 500` with everything below it gone, and a cursor bounded to `cursor + 1` would refuse to
> reach it — the direction stalls forever on a retention event that the protocol requires the relay
> to perform. That is §6.2's permanent stall, arrived at by the same route §6.4's own "bounded, not
> refused" reasoning rejected. So the element count is **not** a safe bound, and the fact that it
> looks like one is why this needs a decision rather than a patch.

What is left is a bound derived from something the relay does not choose — elapsed time, a
receiver-side maximum advance per tick, or an accepted-envelope watermark — each of which trades a
different property, and all of which are normative changes to §6.4 binding **both** receivers.
Inventing one in a single engine PR is precisely the divergence this ledger exists to prevent.

**To close.** Decide what bounds an unauthenticated advance when the relay supplies every in-band
number, amend §6.4 on the branch that owns it, then move both pumps and both test suites together.

**Re-verification:** `AUDIT-REQUEST.md` **C-LAT-5…6**.
