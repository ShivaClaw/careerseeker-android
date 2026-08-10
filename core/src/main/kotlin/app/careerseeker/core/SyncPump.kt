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
            RelayResult.Conflict -> CONFLICT
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
 *     → ReplicaApplier.apply        (the projection, :app)
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
 * **4. Sequence numbers come from inside the envelope, never from the relay's page wrapper.**
 * [RelayClient.pull] reports a `seq` per item alongside the sealed bytes. That number is transport
 * metadata: the relay is blind, but blind is not the same as trusted (§2). The envelope's own
 * `seq` is authenticated — it is in the AAD, so the AEAD tag covers it — and it is the one that
 * moves the cursor and measures the gap. The two agree against this relay, which splices the
 * envelope back verbatim; the rule is what keeps that a property of this deployment rather than an
 * assumption baked into the phone.
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

            // Advance first. A rejected or unapplied envelope must not be re-fetched next cycle —
            // see rule 1 on the class. The relay already handed it over; asking again produces the
            // same bytes and, after the receiver's window has seen them, a replay rejection.
            //
            // Rule 4, and it is the reason the parse is hoisted: the seq that drives the cursor is
            // the one inside the envelope, which the AEAD tag authenticates through the AAD — not
            // the one the relay put in the page wrapper. The relay is blind but it is not trusted
            // (§2), and a wrapper seq of 999999 on an envelope carrying 5 would otherwise skip the
            // stream past everything the phone had not read. They agree in practice today because
            // the relay splices the envelope back verbatim; the rule is what keeps that a fact
            // about this relay rather than an assumption about every relay. An envelope that does
            // not parse has no authenticated seq, so it falls back to the wrapper's — the only
            // number available, and safe because the item is discarded either way and the
            // alternative is a permanent stall on one malformed byte.
            val seq = header?.seq ?: envelope.seq
            if (seq > cursorValue) cursorValue = seq

            if (header == null) {
                rejections += parsed.error ?: ErrorCode.DECRYPT_FAILED
                continue
            }

            val received = receiver.receive(header, keyForDir)
            if (!received.accepted) {
                rejections += received.error ?: ErrorCode.DECRYPT_FAILED
                continue
            }

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
