package app.careerseeker.core

/**
 * The replica's position **as of before** the envelope under consideration was applied.
 *
 * Two fields rather than one because they answer different questions and can disagree. A phone
 * that has applied a `heartbeat` at seq 5 but has never received a `snapshot` has
 * `highestAppliedSeq = 5` and `snapshotSeen = false`: it is *ahead* in the stream and *empty* in
 * the dashboard at the same time. Collapsing these into one number is how a receiver ends up
 * believing it holds state it has never been sent.
 *
 * @property snapshotSeen whether a full `snapshot` (§4.3.1) has ever been applied. The replica's
 *   persisted flag, not an in-memory guess — it must survive a process restart.
 * @property highestAppliedSeq the persisted e2p high-water mark. Note this is the highest
 *   **applied** seq, not the highest *accepted* one: [EnvelopeReceiver] commits a seq the moment
 *   an envelope is authentic, which happens even for a `delta` the replica then refuses.
 */
data class ReplicaPosition(
    val snapshotSeen: Boolean,
    val highestAppliedSeq: Long,
)

/**
 * What the replica did with one decrypted envelope, reduced to the cases the pull decision turns
 * on.
 *
 * This is deliberately a `:core` type mirroring `:app`'s `ApplyResult` rather than that type
 * itself. `ApplyResult` lives in `:app` because it is produced by Room-backed code, and `:core`
 * must stay Android-free (`checkCoreIsAndroidFree`). The mapping is one `when` in the transport
 * layer, and the narrowing is the point: the pull decision must not be able to depend on which
 * *kind* was applied beyond the snapshot/not-snapshot distinction it genuinely needs.
 */
enum class ApplyDisposition {
    /** A full `snapshot` landed. The only disposition that can satisfy an outstanding request. */
    APPLIED_SNAPSHOT,

    /** Some other payload changed the replica (`delta`, `heartbeat`, `evidence`). */
    APPLIED,

    /** A `delta` arrived before any snapshot; nothing changed (`ApplyResult.AwaitingSnapshot`). */
    AWAITING_SNAPSHOT,

    /** At or below the persisted high-water mark — already applied. */
    STALE,

    /** A valid kind the replica does not project yet (`doc`, `conflict`…). */
    IGNORED,

    /** The plaintext did not parse as the expected payload shape. Nothing changed. */
    MALFORMED,
}

/** Why the phone is asking the engine to re-publish. Diagnostic; it never rides the wire. */
enum class PullReason {
    /** Opened with no snapshot ever applied — §6.2's "arrived mid-stream". */
    COLD_START,

    /** A `delta` was refused for want of a snapshot (§4.3.1). */
    AWAITING_SNAPSHOT,

    /** The seq gap exceeded [PullPolicy.gapThreshold] — §6.2's "large gap". */
    SEQUENCE_GAP,
}

/** Whether the transport should emit a `pull_request` (§4.3) after handling an envelope. */
sealed interface PullDecision {
    /** Ask nothing. */
    data object None : PullDecision

    /**
     * Emit `pull_request` with this body. Build it with
     * [OutboundEnvelopeFactory.pullRequest] — it is not a state-changing kind (§5.4), so it
     * needs no device signature and therefore works before a Keystore key exists.
     */
    data class Request(val sinceSeq: Long, val reason: PullReason) : PullDecision
}

/**
 * Decides when the phone asks the engine to re-publish (§4.3 `pull_request`, §6.2).
 *
 * The transport loop this serves is: pull envelopes from the relay → [EnvelopeReceiver.receiveWire]
 * → hand accepted plaintext to the replica's applier → ask this policy what to do about the
 * result → if it says [PullDecision.Request], build one envelope and push it. Everything except
 * the applier step is `:core`, which is why this type can be tested without Room, an emulator, or
 * a device key.
 *
 * ## `since_seq` is always zero, and that is the interesting decision
 *
 * §4.3 describes `pull_request` as "ask the engine to re-publish **from a sequence point**",
 * which reads like resumption. The engine does not implement resumption: `InboundDispatcher`
 * parses `since_seq`, hands it to `ISnapshotRepublisher.RepublishSnapshotAsync(sinceSeq)`, and
 * every implementation of that interface ignores the argument and publishes a **full snapshot**
 * (`LiveRepublisher` calls `PublishSnapshotAsync` unconditionally). So in v1 `pull_request` means
 * exactly one thing: *send me a snapshot*.
 *
 * Given that, the phone sends `0`. The tempting alternative — report the honest high-water mark,
 * `since_seq = highestAppliedSeq` — is *worse*, and worse in a way that would not show up until
 * later. It encodes a request the current engine ignores but a future one might honour, and if it
 * were honoured the gap case would come back as **deltas resuming after N** when §6.2 explicitly
 * requires a fresh snapshot. Zero is the only value that means "I hold nothing usable, send
 * everything" under both the engine that exists and the engine that might.
 *
 * The mission's interpretation rule is the tie-breaker: where the protocol is ambiguous, match the
 * engine and record the question. Recorded as PQ-S4-1 in `docs/protocol-questions.md`.
 * [PullPolicyTest] pins the zero so a later "improvement" has to argue with a failing test.
 *
 * ## One request at a time
 *
 * A phone awaiting a snapshot may be handed many deltas in one pull page, and a large gap is
 * usually followed by more envelopes on the far side of it. Emitting one `pull_request` per
 * envelope would answer a stalled sync with a burst of traffic. The policy therefore latches:
 * once it has asked, it returns [PullDecision.None] until one of three things releases it — an
 * [ApplyDisposition.APPLIED_SNAPSHOT] satisfies the request, the caller reports the push failed
 * via [onRequestFailed], or the transport is reopened via [onOpen].
 *
 * The third exists because the first two only cover asks whose fate the phone can observe. An ask
 * the relay accepted and the engine never collected before the TTL expired is indistinguishable,
 * from here, from one still in flight — so without a release at [onOpen] the latch outlives the
 * thing it was latching for. See [onOpen]'s doc; it is the case that fails silently.
 *
 * ## A gap is what was never received, not what was never projected
 *
 * §6.2's "large gap" is a statement about the *stream*, so it has to be measured against what this
 * phone has **seen** — not against what its replica chose to **keep**. Those two numbers are the
 * same only while every published kind is projected, which is true of the engine that exists today
 * and stops being true the moment a `doc` or `conflict` publisher lands. See [highestHandledSeq];
 * the gap branch of [onEnvelope] measures against the higher of the two marks for that reason.
 *
 * Not thread-safe: drive it from the single coroutine that owns the transport loop.
 *
 * @param gapThreshold how large a seq gap must be before it counts as §6.2's "large gap". The
 *   protocol says a large gap is a signal to request a snapshot but pins **no number**, and the
 *   engine has no opinion because the engine never sends `pull_request` — it only answers one. So
 *   this is phone-side policy, not protocol, which is why it is a parameter with a stated default
 *   rather than a constant in [Protocol]. Also recorded as PQ-S4-1.
 */
class PullPolicy(val gapThreshold: Long = DEFAULT_GAP_THRESHOLD) {

    init {
        require(gapThreshold >= 1) { "gapThreshold must be at least 1, was $gapThreshold" }
    }

    private var pending = false

    /**
     * The highest e2p seq this policy has been **told about**, whatever the replica did with it.
     *
     * Deliberately distinct from [ReplicaPosition.highestAppliedSeq], and the distinction is the
     * whole of the §6.2 rule below. The persisted mark advances only for
     * [ApplyDisposition.APPLIED] and [ApplyDisposition.APPLIED_SNAPSHOT], so on its own it cannot
     * tell an envelope the phone **never received** from one it **received and deliberately did
     * not project** ([ApplyDisposition.IGNORED], [ApplyDisposition.MALFORMED]). §6.2's gap is only
     * the first of those; measuring against the applied mark alone counted the second as well, and
     * a run of `gapThreshold + 1` unprojected envelopes then made the *next* projected one report a
     * `SEQUENCE_GAP` that nothing was missing from — a full snapshot asked for on a healthy
     * pairing.
     *
     * In memory only, and deliberately so: this is a statement about *this process's* receive
     * history, not a replica fact worth persisting. Losing it at process death is safe, because the
     * only thing that can do is lower the baseline back to the persisted mark — where this started.
     * It is not cleared at [onOpen] either; see that method's doc for why.
     */
    private var highestHandledSeq = 0L

    /** True once a request has been issued and not yet satisfied or released. */
    val hasPendingRequest: Boolean get() = pending

    /**
     * Called when the transport opens (app start, resume, reconnect) — S4's "pull on open".
     *
     * A phone that has never applied a snapshot asks for one immediately rather than waiting for
     * a `delta` to be refused. Waiting would mean an idle engine leaves the phone showing demo
     * data indefinitely, since nothing forces a delta to arrive.
     *
     * ## A reopen releases the latch, and that is a different case from [onRequestFailed]
     *
     * [onRequestFailed] covers the ask that never left the phone. This covers the ask that **did**
     * leave, was accepted by the relay, and was never answered — and until this line existed, that
     * case had no release path at all.
     *
     * It is not hypothetical. The relay is a blind store-and-forward queue with a TTL (§6.2's
     * "the relay's TTL purge creates legitimate gaps"), and the engine is a desktop application
     * that is switched off more often than it is on. So *"the relay accepted my `pull_request`"*
     * is not *"the engine will answer it"*: an engine that never polls before the TTL expires
     * leaves the ask nowhere at all. The latch would then suppress every later ask for the life of
     * the process — [onEnvelope] cannot release it either, because on a replica with no snapshot
     * every disposition that would ask routes through the same latched [request]. The phone shows
     * demo data with [hasPendingRequest] true: waiting, and honestly reporting that it is waiting,
     * on a question that no longer exists anywhere. **§6.2 forbids exactly this outcome** — "a gap
     * MUST NOT stall the stream".
     *
     * The engine's start-up snapshot hides it most of the time, which is what makes it worth
     * pinning rather than trusting: it bites precisely when the engine was *already* running as
     * the ask expired, so it never publishes a fresh start-up snapshot and the phone never asks
     * again.
     *
     * Releasing here is safe because the asymmetry runs the same way as everywhere else in this
     * loop. A duplicate ask costs one extra snapshot, is bounded by the **reconnect** rate rather
     * than the envelope rate, and is idempotent engine-side — every `ISnapshotRepublisher`
     * implementation answers `pull_request` by publishing a full snapshot regardless of state. A
     * suppressed ask costs the dashboard indefinitely, in silence. The recoverable failure takes
     * the benefit of the doubt.
     *
     * This is phone-side policy, not protocol: the engine never *sends* `pull_request`, so there
     * is no engine behaviour for the mission's interpretation rule to bind to here. The release is
     * unconditional rather than cold-only on purpose — a warm replica holding a latched
     * [PullReason.SEQUENCE_GAP] ask across a reconnect has lost it the same way, and gets the same
     * second chance the moment the next gap appears.
     *
     * ## What is released is the latch, and only the latch
     *
     * [highestHandledSeq] is deliberately **not** cleared here. Those envelopes really were
     * received; a reopen does not un-receive them, and forgetting them would reinstate the spurious
     * `SEQUENCE_GAP` this policy just stopped emitting, one reconnect later. Nor does keeping it
     * hide a real gap opened *across* the downtime: the envelopes on the far side of that gap were
     * never handled either, so they never raised the mark, and the first envelope past it still
     * measures the full distance. Pinned by its own test and its own mutation.
     */
    fun onOpen(position: ReplicaPosition): PullDecision {
        // Before the decision, not after: the release must happen even on the warm path that
        // returns None, or a gap ask latched before the reconnect survives it.
        pending = false
        return if (position.snapshotSeen) PullDecision.None else request(PullReason.COLD_START)
    }

    /**
     * Called once per envelope, after the replica has applied it.
     *
     * @param envelopeSeq the e2p seq of the envelope just handled.
     * @param disposition what the replica did with it.
     * @param positionBefore the replica's position **before** this envelope was applied. Reading
     *   it after would fold this envelope's own seq into the high-water mark and hide every gap.
     */
    fun onEnvelope(
        envelopeSeq: Long,
        disposition: ApplyDisposition,
        positionBefore: ReplicaPosition,
    ): PullDecision {
        val decision = decide(envelopeSeq, disposition, positionBefore)

        // After the decision, never before. Folding this envelope's own seq into the baseline it
        // is about to be measured against would make every gap measure zero -- the same trap the
        // positionBefore parameter's doc warns about, one field along. Kotlin will not flag the
        // reordering, so it is pinned by its own mutation.
        if (envelopeSeq > highestHandledSeq) highestHandledSeq = envelopeSeq

        return decision
    }

    private fun decide(
        envelopeSeq: Long,
        disposition: ApplyDisposition,
        positionBefore: ReplicaPosition,
    ): PullDecision = when (disposition) {
        // The request is satisfied by the snapshot itself, not by having sent the ask. Clearing
        // here (rather than when the push succeeds) is what makes the latch self-healing: a
        // snapshot the engine sent for its own reasons also releases it.
        ApplyDisposition.APPLIED_SNAPSHOT -> {
            pending = false
            PullDecision.None
        }

        ApplyDisposition.AWAITING_SNAPSHOT -> request(PullReason.AWAITING_SNAPSHOT)

        ApplyDisposition.APPLIED ->
            when {
                // Applied something without ever holding a snapshot -- a heartbeat or evidence
                // payload on a fresh phone. The stream is moving but the dashboard is empty, so
                // ask. This also makes the policy correct for a caller that never called onOpen.
                !positionBefore.snapshotSeen -> request(PullReason.COLD_START)

                // Against whichever mark is higher: the replica's persisted *applied* seq, or the
                // highest seq this policy has *handled*. See [highestHandledSeq] -- the two
                // disagree exactly when the phone received something it chose not to project, and
                // that is a receipt, not a gap.
                envelopeSeq - maxOf(positionBefore.highestAppliedSeq, highestHandledSeq) >
                    gapThreshold -> request(PullReason.SEQUENCE_GAP)

                else -> PullDecision.None
            }

        // None of these justify traffic. STALE means re-delivery, which is normal. IGNORED means
        // a kind this build does not project -- re-sending it would produce the same result.
        // MALFORMED is the one worth stating: a re-publish would very likely reproduce the same
        // bytes, so asking again turns a parse bug into a request loop. A malformed payload is a
        // defect to report, not a gap to fill.
        ApplyDisposition.STALE, ApplyDisposition.IGNORED, ApplyDisposition.MALFORMED ->
            PullDecision.None
    }

    /**
     * Release the latch after a `pull_request` failed to reach the relay.
     *
     * Without this a single failed push would silence the policy for the life of the process:
     * it would believe a request was outstanding that the engine never received.
     */
    fun onRequestFailed() {
        pending = false
    }

    private fun request(reason: PullReason): PullDecision {
        if (pending) return PullDecision.None
        pending = true
        return PullDecision.Request(SINCE_SEQ_FULL_REPUBLISH, reason)
    }

    companion object {
        /**
         * The only `since_seq` v1 sends. See the class doc — `pull_request` means "send me a
         * snapshot", and zero is the value that says so to any engine.
         */
        const val SINCE_SEQ_FULL_REPUBLISH = 0L

        /**
         * Default for [gapThreshold]. Chosen, not derived: the engine publishes roughly one
         * envelope per cycle plus heartbeats, so a gap this size means the phone was away long
         * enough that the relay's TTL has plausibly purged what it missed — the situation §6.2
         * describes. Tune it with evidence from a real deployment; there is none yet, and a
         * number presented as measured when it was picked would be worse than an honest default.
         */
        const val DEFAULT_GAP_THRESHOLD = 32L
    }
}
