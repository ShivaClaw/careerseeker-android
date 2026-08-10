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
