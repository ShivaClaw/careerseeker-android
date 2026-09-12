package app.careerseeker.core

/**
 * Reads and writes the phone's persisted [ProState].
 *
 * Implemented in `:app`, wherever Pro state actually lives (DataStore or the Room replica) —
 * `:core` must stay Android-free, so the seam is here and the storage is not.
 *
 * Both halves are needed, and the read half is the one worth explaining. [EntitlementAckApplier]
 * is deliberately a *transition* — `(current, plaintext) -> next` — rather than a producer, so
 * that "an ack never moves state downward" is a property of the function rather than a rule the
 * caller has to remember. A sink that could only write would force the router to invent a
 * `current` to hand it, and the obvious invention ([ProState.Free]) discards exactly the
 * information that makes the transition safe.
 */
interface ProStateStore {
    suspend fun current(): ProState

    /** Called only when the state actually changed; a re-delivered ack writes nothing. */
    suspend fun store(state: ProState)
}

/**
 * Routes `entitlement_ack` to [EntitlementAckApplier] and everything else to the replica.
 *
 * ## Why this type exists
 *
 * [EntitlementAckApplier] is documented as "the phone's only unlock path", and until this class
 * existed **nothing in either module called it**. [SyncPump] hands every accepted payload to one
 * [ReplicaApplier]; the `:app` implementation of that interface is a `when` over the four kinds
 * it projects (`snapshot`, `delta`, `heartbeat`, `evidence`) and returns
 * [ApplyDisposition.IGNORED] for the rest. `entitlement_ack` is in the rest.
 * So a correctly sealed, correctly sequenced ack from the engine would decrypt, be accepted by
 * [EnvelopeReceiver], be counted as a clean envelope — and be dropped. Pro would never unlock,
 * and nothing anywhere would report a failure, because on every layer's own terms nothing failed.
 *
 * Decorating the replica applier rather than branching inside [SyncPump] keeps the pump's four
 * ordering rules untouched and keeps the replica's applier ignorant of entitlements, which is
 * where that knowledge should not be: the replica is a projection of engine data, and Pro state
 * is not engine data the user can see rows of.
 *
 * ## The disposition is always [ApplyDisposition.IGNORED], including on success
 *
 * This looks wrong and is the load-bearing decision in the file. [ApplyDisposition] is the
 * **replica's** vocabulary — [PullPolicy] is its only consumer, and every branch it has measures
 * the replica's projection against the stream. An ack changes [ProState]; it projects no row, and
 * it never advances the replica's persisted `highestAppliedSeq`.
 *
 * Report a successful ack as [ApplyDisposition.APPLIED] and [PullPolicy] reads it as replica
 * progress that did not happen. Two of its branches then misfire on data that is perfectly
 * healthy: an ack on a phone that has not yet taken a snapshot requests a
 * [PullReason.COLD_START] it is already going to request for better reasons, and an ack whose
 * seq sits far above the replica's applied mark reports a [PullReason.SEQUENCE_GAP] that no
 * amount of correct syncing can close, because the envelope that "caused" it can never move the
 * mark it is measured against. Both produce traffic on a healthy pairing, which is precisely what
 * [PullPolicy]'s latch exists to prevent.
 *
 * [ApplyDisposition.IGNORED] is what the disposition means from where it is read: *the replica
 * did not project this*. That is true of an ack, and it stays true whether or not the ack
 * unlocked anything.
 *
 * ## An ack this build cannot honour is also IGNORED, not MALFORMED
 *
 * §4.3.3's rule is "ignore rather than unlock", and [EntitlementAckApplier] implements it by
 * returning the caller's state unchanged for an unparseable body, an unknown `product_id`, or a
 * missing `acknowledged_at`. The overwhelmingly likely cause of all three is a **newer engine
 * talking to an older phone** — a product this build has never heard of is the expected steady
 * state right up until the Play listing is created. Calling that [ApplyDisposition.MALFORMED]
 * would file a defect report against a correct engine, so the two cases are not distinguished
 * here. What separates them is whether the store was written, which is observable and tested.
 */
class EntitlementRoutingApplier(
    private val delegate: ReplicaApplier,
    private val ack: EntitlementAckApplier,
    private val proState: ProStateStore,
) : ReplicaApplier {

    override suspend fun apply(
        seq: Long,
        envelopeTs: String,
        kind: String,
        plaintext: ByteArray,
    ): ApplyDisposition {
        if (kind != PayloadKind.ENTITLEMENT_ACK.wire) {
            return delegate.apply(seq, envelopeTs, kind, plaintext)
        }

        // The envelope ts is not consulted, and that is §6.3 rather than an omission: the
        // acknowledged_at the user is shown comes from inside the authenticated body, not from
        // the header field a relay could have reshaped had the AEAD tag not covered it.
        val before = proState.current()
        val after = ack.apply(before, plaintext)

        // Re-delivery is normal — the relay may serve the same envelope again after a restart
        // clears the receiver's in-process replay window — so an unchanged state must not
        // produce a write. Storing unconditionally would rewrite the same row on every cycle.
        if (after != before) proState.store(after)

        return ApplyDisposition.IGNORED
    }
}
