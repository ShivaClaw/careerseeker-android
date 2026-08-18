package app.careerseeker.core

/**
 * Why a relay call did not succeed, without [RelayResult]'s type parameter.
 *
 * [PumpReport] needs to carry "the pull failed, and here is which way" as data. `RelayResult<T>`
 * cannot be stored in a field without picking a `T` that has no meaning for a failure, so the
 * non-`Ok` cases are re-expressed here. The mapping is total and lives in [fromRelayResult]; if
 * [RelayResult] grows a case, that `when` stops compiling, which is the point.
 */
enum class RelayFailure {
    PAIRING_UNKNOWN,
    UNAUTHORISED,
    TOO_LARGE,
    CONFLICT,
    UNAVAILABLE,
    ;

    companion object {
        /** Returns null for [RelayResult.Ok] — success is not a failure reason. */
        fun fromRelayResult(result: RelayResult<*>): RelayFailure? = when (result) {
            is RelayResult.Ok -> null
            RelayResult.PairingUnknown -> PAIRING_UNKNOWN
            RelayResult.Unauthorised -> UNAUTHORISED
            RelayResult.TooLarge -> TOO_LARGE
            is RelayResult.Conflict -> CONFLICT
            is RelayResult.Unavailable -> UNAVAILABLE
        }
    }
}

/**
 * What one call to [SyncPump.open] or [SyncPump.pump] did.
 *
 * Every field is an observation, not a verdict: the pump does not decide whether a sync is
 * "healthy". A caller that wants to surface a banner, retry, or back off reads these and decides.
 *
 * @property pullFailure non-null when the relay itself did not answer. Everything below is then
 *   zero — nothing was fetched, so nothing was applied and nothing was asked.
 * @property pulled envelopes the relay handed back on this page.
 * @property applied envelopes that changed the replica ([ApplyDisposition.APPLIED] or
 *   [ApplyDisposition.APPLIED_SNAPSHOT]).
 * @property rejections receive-level rejections (§7.2), in arrival order. A non-empty list is a
 *   fact worth showing an operator: it means the paired engine and this phone disagree about the
 *   keys, the version, or the framing, and no amount of pulling will fix it.
 * @property cursor the transport cursor after this call — the `since` the next pull will use.
 * @property latest the relay's high-water mark for `e2p` as of this page (§6.1).
 * @property requestSent set when a `pull_request` was built **and** the relay accepted it.
 * @property requestFailed set when a `pull_request` was built and the push did not land. The
 *   policy latch has already been released, so the next call may ask again.
 * @property moreAvailable `cursor < latest` — the relay pages, so a true value means call [pump]
 *   again rather than waiting for the next tick.
 */
data class PumpReport(
    val pullFailure: RelayFailure? = null,
    val pulled: Int = 0,
    val applied: Int = 0,
    val rejections: List<ErrorCode> = emptyList(),
    val cursor: Long = 0L,
    val latest: Long = 0L,
    val requestSent: PullReason? = null,
    val requestFailed: PullReason? = null,
    val moreAvailable: Boolean = false,
)

/**
 * Applies one decrypted engine→phone payload to the replica and says what happened.
 *
 * Implemented in `:app`, because the replica is Room and `:core` must stay Android-free
 * (`checkCoreIsAndroidFree`). The implementation is one `when` over `ApplyResult`:
 *
 * ```
 * when (val r = applier.apply(seq, ts, kind, plaintext)) {
 *     is ApplyResult.Applied ->
 *         if (r.kind == "snapshot") ApplyDisposition.APPLIED_SNAPSHOT else ApplyDisposition.APPLIED
 *     ApplyResult.AwaitingSnapshot -> ApplyDisposition.AWAITING_SNAPSHOT
 *     ApplyResult.SkippedStale     -> ApplyDisposition.STALE
 *     is ApplyResult.Ignored       -> ApplyDisposition.IGNORED
 *     ApplyResult.Malformed        -> ApplyDisposition.MALFORMED
 * }
 * ```
 *
 * The `snapshot` branch is the one that matters and the one a hand-written loop gets wrong:
 * [ApplyDisposition.APPLIED_SNAPSHOT] is the **only** disposition that clears [PullPolicy]'s
 * latch. Map a snapshot to plain [ApplyDisposition.APPLIED] and the phone still renders the
 * dashboard correctly while the latch stays stuck forever — every later gap goes unasked, in
 * silence. That is why the narrowing is an interface here and not a comment in `:app`.
 *
 * ## This interface does not see every accepted payload, and must not be wired as if it does
 *
 * The `when` above is a projection of engine data into the replica, so its `else` branch is
 * correct for `doc` and `conflict` — kinds the dashboard does not render yet. It is **not**
 * correct for `entitlement_ack`, which carries no rows to project and is the phone's only path to
 * [ProState.Unlocked]. An `:app` applier handed one matches no branch, reports
 * [ApplyDisposition.IGNORED] exactly as it does for `doc`, and Pro silently never unlocks —
 * a failure with no error, no rejection and no wrong counter anywhere.
 *
 * So the composition root wires **[EntitlementRoutingApplier] around** the `:app` applier and
 * passes that to [SyncPump], rather than passing the `:app` applier directly:
 *
 * ```
 * applier = EntitlementRoutingApplier(roomApplier, EntitlementAckApplier(productIds), proStateStore)
 * ```
 *
 * `EntitlementRoutingApplierTest` holds this shut from the other side: its first test drives the
 * un-decorated arrangement and asserts the phone stays [ProState.Free].
 */
fun interface ReplicaApplier {
    suspend fun apply(seq: Long, envelopeTs: String, kind: String, plaintext: ByteArray): ApplyDisposition
}

/** Reads the replica's **persisted** position (§6.1). Implemented in `:app` over Room. */
fun interface ReplicaPositionSource {
    suspend fun current(): ReplicaPosition
}

/**
 * The engine→phone transport loop, with every decision it makes kept in `:core`.
 *
 * This is S4's remaining logic. What is left for `:app` after this class exists is I/O and
 * nothing else: a Ktor engine for [RelayClient], a Room-backed [ReplicaApplier] and
 * [ReplicaPositionSource], and a coroutine that calls [open] once and [pump] on a tick or on a
 * live-socket nudge. Splitting it this way is deliberate — the interesting parts below are all
 * ordering rules, and ordering rules written in `:app` can only be checked by a machine with an
 * Android SDK, which the sessions doing this work do not have.
 *
 * One cycle:
 *
 * ```
 * relay.pull(e2p, cursor) → for each envelope:
 *     EnvelopeReceiver.receiveWire  (every trust decision, §3–§5.4)
 *     → ReplicaApplier.apply        (the projection, :app — behind EntitlementRoutingApplier)
 *     → PullPolicy.onEnvelope       (the ask decision, §4.3/§6.2)
 * → at most one pull_request pushed back
 * ```
 *
 * ## Four rules this class exists to hold
 *
 * **1. The transport cursor advances on every envelope *seen*, not every envelope *applied*.**
 * The two differ constantly: a `delta` that arrives before any snapshot is accepted by the
 * receiver and then refused by the replica ([ApplyDisposition.AWAITING_SNAPSHOT]), and a
 * receive-level rejection never reaches the replica at all. Driving the next `since` from the
 * persisted *applied* mark would re-fetch those envelopes on every cycle — and the second time
 * around [EnvelopeReceiver]'s in-process replay window refuses them, so the phone would pull the
 * same page forever, apply nothing, and report no error. The cursor is therefore in-memory
 * transport state, seeded from the persisted mark on [open].
 *
 * Re-seeding on restart is the other half of that rule and it is not an accident: after a process
 * restart the receiver's replay window is empty, so re-fetching everything above the persisted
 * applied mark is exactly right — anything accepted-but-not-applied last time gets a second, clean
 * attempt.
 *
 * **2. The replica's position is read *before* each apply, once per envelope.** [PullPolicy]
 * measures a gap as `envelopeSeq - positionBefore.highestAppliedSeq`, so reading the position
 * after the apply folds the envelope's own seq into the mark and hides every gap. Reading it once
 * for a whole page is the subtler version of the same bug in the other direction: a contiguous
 * page of 40 envelopes measured against the position at the top of the page reports a 40-wide gap
 * on its last envelope and fires a `pull_request` nothing was wrong with.
 *
 * **3. At most one `pull_request` leaves per cycle**, which is [PullPolicy]'s latch doing its job
 * rather than anything this class adds. The pump's contribution is honouring the other end of the
 * contract: if the push does not land, [PullPolicy.onRequestFailed] is called so the latch does
 * not silence the policy for the life of the process over one dropped packet.
 *
 * **4. Sequence numbers come from inside the envelope; an unauthenticated one is bounded by
 * `latest`.** [RelayClient.pull] reports a `seq` per element alongside the sealed bytes. That
 * number is transport metadata: the relay is blind, but blind is not the same as trusted (§2). The
 * envelope's own `seq` is authenticated — it is in the AAD, so the AEAD tag covers it — and it is
 * the one that moves the cursor and measures the gap. The two agree against this relay, which
 * splices the envelope back verbatim; the rule is what keeps that a property of this deployment
 * rather than an assumption baked into the phone.
 *
 * **Parsing is not authenticating**, and that is where the line falls (§6.4, amended 2026-08-13 —
 * PQ-CUR-1). A `seq` is recovered from the sealed bytes only once the AEAD tag verifies over the
 * AAD that carries it, so the cursor advances *without a bound* only for an envelope the receiver
 * **accepted**. Everything else — an element that fails the §3 parse, and an element that parses
 * cleanly and is then refused for any reason, the tag included — carries a `seq` that is a claim,
 * and §6.4 lets that claim move the cursor only as far as the page's own `latest`.
 *
 * The two failures are one case here on purpose. An envelope can pass the §3 parse completely and
 * still be bytes the relay invented: well-formed JSON, the right fields, a valid pairing id, a
 * 12-byte nonce, a base64url ciphertext, and nothing at all vouching for its `seq`. This class
 * originally advanced the moment the parse succeeded, which handed that element the unbounded path
 * — history truncation performed without decrypting anything, since one such element claiming
 * `seq: 1000000` skips every envelope below it, permanently, because the cursor never moves
 * backwards. Refusing to move at all is the opposite failure and §6.2 forbids it by name. The
 * asymmetry is what decides it: a stall is recoverable and visible, a truncation is silent and is
 * not.
 *
 * ## What this class deliberately does not do
 *
 * No signing: `pull_request` is not state-changing (§5.4), so [outbound] may be built with no
 * [DeviceSigner] at all and this loop still works — which is why S4's pull half never needed S3's
 * Android Keystore key. No retry or backoff scheduling: [RelayClient] owns transport retry, and
 * *when* to pump is a lifecycle decision that belongs to whatever owns the coroutine. No socket:
 * the live route (`wss`) is a nudge to call [pump] sooner, not a second code path.
 *
 * Not thread-safe: drive it from the single coroutine that owns the transport.
 *
 * @param keyForDir supplies the AEAD key for a direction, as [EnvelopeReceiver.receiveWire] wants
 *   it. Only `e2p` is ever asked for here.
 * @param clock produces the envelope `ts` for outbound requests. RFC 3339. Injected rather than
 *   read from a system clock so the ordering rules above can be tested without one, and because
 *   §6.3 makes `ts` advisory anyway — nothing here compares it to anything.
 */
class SyncPump(
    private val relay: RelayClient,
    private val receiver: EnvelopeReceiver,
    private val keyForDir: (String) -> ByteArray,
    private val applier: ReplicaApplier,
    private val position: ReplicaPositionSource,
    private val outbound: OutboundEnvelopeFactory,
    private val clock: () -> String,
    private val policy: PullPolicy = PullPolicy(),
) {
    private var seeded = false
    private var cursorValue = 0L

    /** The `since` the next [pump] will send. Meaningless until [open] or the first [pump]. */
    val cursor: Long get() = cursorValue

    /** Exposed so a caller can surface "waiting for a snapshot" honestly. */
    val hasPendingRequest: Boolean get() = policy.hasPendingRequest

    /**
     * Called when the transport comes up — app start, resume, reconnect.
     *
     * Seeds the cursor from the persisted position and asks for a snapshot if the replica has
     * never held one. Nothing is pulled here: [pump] does that, and a caller that wants the two
     * together calls them in order.
     */
    suspend fun open(): PumpReport {
        val current = seed()
        return emitIfAsked(policy.onOpen(current), base = PumpReport(cursor = cursorValue))
    }

    /**
     * Pull one page, apply it, and send at most one `pull_request`.
     *
     * Returns as soon as the relay refuses to answer: a page that never arrived cannot be
     * applied, and inventing a decision from a transport failure is how a phone ends up asking
     * the engine to fix a problem the engine does not have.
     */
    suspend fun pump(): PumpReport {
        if (!seeded) seed()

        val page = when (val pulled = relay.pull(Direction.ENGINE_TO_PHONE, cursorValue)) {
            is RelayResult.Ok -> pulled.value
            else -> return PumpReport(
                pullFailure = RelayFailure.fromRelayResult(pulled),
                cursor = cursorValue,
            )
        }

        var applied = 0
        val rejections = mutableListOf<ErrorCode>()
        var decision: PullDecision = PullDecision.None

        for (envelope in page.envelopes) {
            // The SAME strict parse receiveWire performs (§3, including unknown-field rejection),
            // hoisted so the header's own `seq` and `ts` are available below. This is not the
            // lenient second parser receiveWire's KDoc warns about — it is that method's first
            // line, and the receiver still owns every trust decision that follows.
            val parsed = EnvelopeJson.parse(envelope.wire)
            val header = parsed.envelope

            // Rule 4, and it is the reason the parse is hoisted: the seq that drives the cursor is
            // the one inside the envelope, which the AEAD tag authenticates through the AAD — not
            // the one the relay put in the page. The relay is blind but it is not trusted (§2),
            // and a claimed seq of 999999 on an envelope carrying 5 would otherwise skip the
            // stream past everything the phone had not read.
            //
            // PARSING IS NOT AUTHENTICATING (§6.4, amended 2026-08-13 / PQ-CUR-1). A seq is
            // "recovered from the sealed bytes" only once the AEAD tag verifies over the AAD that
            // carries it. So the cursor advances WITHOUT A BOUND only for an envelope the receiver
            // ACCEPTED, and every other element — one that fails the §3 parse, and one that parses
            // and is then refused for any reason, the tag included — advances it only as far as the
            // page's own `latest`. This block used to advance before the receive call, using
            // `header.seq` unbounded the moment the parse succeeded: a well-formed envelope that no
            // key opens could be minted by the relay and walked the cursor anywhere it liked.
            //
            // Bounded, not refused: refusing stalls the direction forever on one corrupt byte,
            // which §6.2 forbids. Bounded, not free: free is history truncation achieved without
            // decrypting anything, and the two failure modes are not symmetric — a stall keeps
            // `latest` above the cursor and resumes the moment a readable page arrives, while
            // truncation is silent, permanent, and looks like a healthy sync.
            //
            // `latest` is the bound because it costs the relay nothing it did not already have:
            // it is the same number that decides `moreAvailable` below. Against an honest relay
            // this is a no-op — its `latest` covers every row it serves.
            //
            // Every path still advances, which is rule 1: a rejected or unapplied envelope must not
            // be re-fetched next cycle. The relay already handed it over; asking again produces the
            // same bytes and, after the receiver's window has seen them, a replay rejection.
            if (header == null) {
                // No authenticated seq exists and no parsed header either, so the only number
                // available is the element's claimed top-level one.
                advanceBounded(envelope.seq, page.latest)
                rejections += parsed.error ?: ErrorCode.DECRYPT_FAILED
                continue
            }

            val received = receiver.receive(header, keyForDir)
            if (!received.accepted) {
                // It parsed, so a header seq exists — but the receiver refused it, so the tag never
                // verified over that seq (or never ran at all). Same rule as an unparseable element.
                advanceBounded(header.seq, page.latest)
                rejections += received.error ?: ErrorCode.DECRYPT_FAILED
                continue
            }

            // Accepted: the tag verified over the AAD, and the AAD carries this seq. It is a fact
            // now, and it is the one number here that may move the cursor without a bound.
            val seq = header.seq
            if (seq > cursorValue) cursorValue = seq

            // Read per envelope, before applying. Rule 2.
            val before = position.current()
            val disposition = applier.apply(
                seq,
                // ts is advisory (§6.3): carried to the replica for display, never compared here.
                // It is authenticated all the same — it is part of the AAD, so a tampered ts fails
                // the tag before this line is reached.
                header.ts,
                received.kind ?: "",
                received.plaintext ?: ByteArray(0),
            )
            if (disposition == ApplyDisposition.APPLIED || disposition == ApplyDisposition.APPLIED_SNAPSHOT) {
                applied++
            }

            // The policy latches, so later envelopes on this page cannot overwrite an earlier
            // Request with None — but a snapshot arriving later on the page DOES clear the latch,
            // and then the request must not still be sent. Re-reading the decision each time and
            // sending after the loop is what makes those two behave.
            val next = policy.onEnvelope(seq, disposition, before)
            if (next is PullDecision.Request) decision = next
            if (disposition == ApplyDisposition.APPLIED_SNAPSHOT) decision = PullDecision.None
        }

        return emitIfAsked(
            decision,
            base = PumpReport(
                pulled = page.envelopes.size,
                applied = applied,
                rejections = rejections,
                cursor = cursorValue,
                latest = page.latest,
                moreAvailable = cursorValue < page.latest,
            ),
        )
    }

    // ---------------------------------------------------------------- internals

    /**
     * Advance the cursor to [claimed], but never past [latest] and never backwards (§6.4).
     *
     * The single place the unauthenticated path is allowed to move the cursor. Both callers reach
     * it for the same reason — no AEAD tag has verified over the number they hold — and they are
     * deliberately not distinguished here: §6.4's rule is about *whether the seq is authenticated*,
     * not about which check refused the element, and giving a parse failure and a tag failure two
     * different bounds is exactly the gap PQ-CUR-1 was filed against.
     *
     * The engine's twin is `InboundPump.AdvanceBounded` (careerseeker `src/Sync/InboundPump.cs`).
     */
    private fun advanceBounded(claimed: Long, latest: Long) {
        val bounded = minOf(claimed, latest)
        if (bounded > cursorValue) cursorValue = bounded
    }

    private suspend fun seed(): ReplicaPosition {
        val current = position.current()
        if (!seeded) {
            cursorValue = current.highestAppliedSeq
            seeded = true
        }
        return current
    }

    private suspend fun emitIfAsked(decision: PullDecision, base: PumpReport): PumpReport {
        if (decision !is PullDecision.Request) return base

        val wire = outbound.pullRequest(decision.sinceSeq, clock())
        return when (relay.push(wire)) {
            is RelayResult.Ok -> base.copy(requestSent = decision.reason)
            else -> {
                // Rule 3: an ask that never reached the relay is not an outstanding ask.
                policy.onRequestFailed()
                base.copy(requestFailed = decision.reason)
            }
        }
    }

}
