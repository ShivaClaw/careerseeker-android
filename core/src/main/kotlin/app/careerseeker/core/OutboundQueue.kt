package app.careerseeker.core

/**
 * One phone→engine envelope whose bytes are already fixed.
 *
 * [wire] is the whole decision this type carries: once built, an envelope's sequence number,
 * nonce, ciphertext and signature are frozen together, and a retry re-sends *these* bytes. The
 * [id] is the caller's key for the thing being sent — for `outcome` that is the `app_id`, which
 * is what makes [OutboundQueue.enqueue]'s collapse possible.
 *
 * @property attempts how many times this exact wire has been handed to the transport. Zero on the
 *   first [OutboundQueue.next] that produced it.
 */
data class OutboundItem(
    val id: String,
    val kind: String,
    val wire: String,
    val attempts: Int,
)

/**
 * Why the queue has stopped, and what has to happen before it can move again.
 *
 * All four are conditions the transport cannot fix by trying again, which is why they are a halt
 * rather than a retry. Two are recoverable in-process and two are not — see [OutboundQueue.next].
 */
enum class SendHalt {
    /**
     * The relay refused a push with 409 `replay_rejected`: its high-water mark for `p2e` is at or
     * above the sequence number this phone just used, so the phone's persisted counter (§6.1) has
     * fallen behind. Recoverable — see [OutboundQueue.reconciled].
     */
    COUNTER_BEHIND,

    /** The bearer was rejected. Recoverable via [RelayTokenLadder] and [OutboundQueue.reauthorised]. */
    UNAUTHORISED,

    /** The relay has no Durable Object for this pairing (§7.2 `pairing_unknown`). Terminal. */
    PAIRING_GONE,

    /**
     * A state-changing kind reached the head with no [DeviceSigner] configured (§5.4). Terminal:
     * the signer is fixed for a queue's lifetime, and a phone without a device key is a phone that
     * is not paired.
     */
    NO_DEVICE_KEY,
}

/** Why an envelope left the queue without ever being accepted. */
enum class DropReason {
    /**
     * The relay answered 413. An `outcome` body is a few dozen bytes and cannot approach §3.1's
     * cap, so this is a defect in whatever built it — and re-pushing it would wedge every later
     * mark behind an envelope that can never fit.
     */
    TOO_LARGE,
}

/** What the transport should do next. */
sealed interface SendStep {
    /** Nothing is waiting. */
    data object Idle : SendStep

    /** Push [item]'s bytes, then report the answer to [OutboundQueue.onPushed]. */
    data class Push(val item: OutboundItem) : SendStep

    /**
     * The queue is stopped.
     *
     * @property relayLatest set only for [SendHalt.COUNTER_BEHIND], and only when the relay
     *   reported a number: the value the phone's `p2e` counter must be lifted above.
     */
    data class Halted(val reason: SendHalt, val relayLatest: Long? = null) : SendStep
}

/** What one reported push did to the queue. */
sealed interface PushOutcome {
    /**
     * The relay stored it and the item has left the queue.
     *
     * **This is not "the mark was applied."** The relay is a blind store-and-forward queue and
     * `outcome` has no ack (PQ-S6-1), so the only evidence of application is the value coming
     * back in a later §4.3.1 payload. [OutcomeMarkPolicy.onSent] is the honest place to report
     * this, and it deliberately changes nothing.
     */
    data object Accepted : PushOutcome

    /** The bytes are kept; call [OutboundQueue.next] again on the next tick. */
    data object Retry : PushOutcome

    /** The item was discarded and the queue has moved on. */
    data class Dropped(val reason: DropReason) : PushOutcome

    /** The queue stopped; [SendHalt] says what has to happen first. */
    data class Halted(val reason: SendHalt, val relayLatest: Long? = null) : PushOutcome
}

/**
 * Orders the phone's `p2e` sends and decides what each relay answer means for the envelope in
 * flight (§6.1, §6.2, §7.2).
 *
 * This is the half of S6 that sits between [OutcomeMarkPolicy] — which decides *what* to mark —
 * and [RelayClient.push], which is an I/O call. It owns no key, no socket and no database: it is
 * driven by the same single coroutine that owns the transport loop, exactly like [SyncPump].
 *
 * ## Why the bytes are built once and retried verbatim
 *
 * [OutboundEnvelopeFactory.build] consumes a sequence number on every call. So "retry" and
 * "rebuild" are not the same operation, and the difference is invisible at the call site:
 * rebuilding burns a second `p2e` seq, and if the first attempt actually landed — a response lost
 * on the way back is indistinguishable from a request that never arrived — the engine receives the
 * same intention twice under two sequence numbers, with two audit rows. So the wire is frozen at
 * first [next] and every retry re-sends it. There is exactly one case where a rebuild is correct
 * ([reconciled]), and it is the one case where the frozen bytes can never be accepted again.
 *
 * ## Why a 409 is never read as success, and never as failure either
 *
 * The relay refuses `seq <= last` at the door with `replay_rejected` and reports its `latest`
 * (`relay/src/channel.ts`). Two different things produce that answer: this phone's own envelope
 * landed and lost its response, or this phone's persisted counter has fallen behind the relay's.
 * They are **not** distinguishable by attempt count, because [RelayClient] retries transport
 * failures *inside* one `push` call — so a lost response is followed by a conflict the queue sees
 * on what it believes is its first attempt. It is the same ambiguity [PairingFlow] found on
 * `POST /pair`, arriving on the push path.
 *
 * The resolution is that the queue does not need to know. Whether the mark landed is already
 * answered elsewhere and only elsewhere: [OutcomeMarkPolicy] holds the mark pending until the
 * engine's value converges, because with no `outcome_ack` that is the only evidence v1 offers
 * (PQ-S6-1). What the queue must do is get *unstuck*, and for that the relay has already sent the
 * one number that helps. So a 409 retires the frozen bytes, halts on [SendHalt.COUNTER_BEHIND],
 * and hands `latest` to the caller that owns the persisted counter.
 *
 * The cost of that choice is a possible duplicate — if the original did land, the rebuilt envelope
 * re-states the same mark. That is the right way to be wrong: §4.3.1's carried outcome is
 * latest-wins state rather than an event log, so a duplicate is idempotent in effect, whereas
 * guessing "delivered" loses the user's mark silently.
 *
 * ## Why there is only ever one envelope in flight
 *
 * [next] will not build a second envelope while the head is unresolved. §6.2 would permit
 * pipelining — gaps are legal and MUST NOT stall the stream — but a queue with several unresolved
 * sequence numbers outstanding cannot attribute a 409 to any of them, and the [reconciled] rebuild
 * would then have to reason about which of its frozen envelopes are dead. Strict single-flight is
 * what keeps every rule above checkable.
 *
 * Not thread-safe.
 */
class OutboundQueue(private val factory: OutboundEnvelopeFactory) {

    private class Entry(val id: String, val kind: String, var bodyJson: String, var timestamp: String) {
        /** Non-null once built. Frozen from that moment until the item leaves or is retired. */
        var wire: String? = null
        var attempts: Int = 0
    }

    private val entries = ArrayDeque<Entry>()
    private var halt: SendHalt? = null
    private var haltLatest: Long? = null

    /** Envelopes waiting, built or not. */
    fun depth(): Int = entries.size

    /** Waiting ids in send order. */
    fun queuedIds(): List<String> = entries.map { it.id }

    /** The current halt, or null if the queue can move. */
    fun halted(): SendHalt? = halt

    /**
     * Queue one payload for [id].
     *
     * **Collapse.** If [id] already has an entry whose envelope has *not* been built, that entry is
     * replaced and moved to the end rather than a second one being appended — the same reasoning
     * [OutcomeMarkPolicy.mark] gives for collapsing in its own map: a user who taps twice before
     * anything leaves the phone expressed one intention, and sending both would burn two sequence
     * numbers to describe it. Moving to the end keeps "newest decision, newest to send".
     *
     * An entry whose envelope is already built is **not** collapsed onto. Those bytes may already
     * be on the wire, and pretending otherwise would silently drop a mark the engine is about to
     * receive; a re-mark then legitimately becomes a second envelope, which the engine resolves by
     * latest-wins (§4.3.1).
     *
     * @return true if this call collapsed onto an existing unbuilt entry rather than appending.
     */
    fun enqueue(id: String, kind: String, bodyJson: String, timestamp: String): Boolean {
        require(PayloadKind.fromWire(kind) != null) { "unknown payload kind '$kind'" }

        val existing = entries.firstOrNull { it.id == id && it.wire == null }
        if (existing != null) {
            existing.bodyJson = bodyJson
            existing.timestamp = timestamp
            entries.remove(existing)
            entries.addLast(existing)
            return true
        }
        entries.addLast(Entry(id, kind, bodyJson, timestamp))
        return false
    }

    /**
     * What the transport should do now.
     *
     * Builds the head's envelope on the first call that reaches it and returns the identical wire
     * on every call after that, until the head is resolved by [onPushed].
     */
    fun next(): SendStep {
        halt?.let { return SendStep.Halted(it, haltLatest) }

        val head = entries.firstOrNull() ?: return SendStep.Idle

        if (head.wire == null) {
            head.wire = try {
                factory.build(head.kind, head.bodyJson, head.timestamp)
            } catch (_: OutboundEnvelopeFactory.UnsignableEnvelope) {
                // Not a drop. The item is fine and the phone is not: nothing state-changing can
                // ever be sent without the device key, so discarding the user's marks here would
                // destroy data to report a condition the UI should be surfacing instead.
                return haltWith(SendHalt.NO_DEVICE_KEY)
            }
        }

        return SendStep.Push(OutboundItem(head.id, head.kind, head.wire!!, head.attempts))
    }

    /**
     * Report what the relay answered for the envelope [next] last returned.
     *
     * @throws IllegalStateException if nothing was in flight — a push nobody was asked to make
     *   cannot be reported, and silently accepting one would let a caller resolve the wrong item.
     */
    fun onPushed(result: RelayResult<*>): PushOutcome {
        val head = entries.firstOrNull()
            ?: throw IllegalStateException("onPushed with an empty queue — nothing was in flight")
        check(head.wire != null) { "onPushed before next() built an envelope for '${head.id}'" }
        head.attempts++

        return when (result) {
            is RelayResult.Ok -> {
                entries.removeFirst()
                PushOutcome.Accepted
            }

            // Keep the bytes. Offline is not a data-loss event, and losing a mark because the
            // network was down is a loss the user has no way to detect (OutcomeMarkPolicy.onSendFailed).
            is RelayResult.Unavailable -> PushOutcome.Retry

            // These bytes can never be accepted: the relay's `last` is already at or above their
            // seq. Retire them so `reconciled()` rebuilds above the reported mark.
            is RelayResult.Conflict -> {
                head.wire = null
                // The count is per-wire, not per-item: it is the only thing that tells a caller
                // whether the bytes in hand have already been tried, and a rebuilt envelope that
                // reported itself as a retry would be lying about bytes the relay has never seen.
                head.attempts = 0
                haltLatest = result.latest
                halted(SendHalt.COUNTER_BEHIND)
            }

            // 413 on a body of a few dozen bytes is a defect, not a condition. Drop just this one
            // and continue: the envelope was never stored, so the relay's `last` is unmoved, and
            // §6.2 makes the resulting gap legal for the receiver too.
            RelayResult.TooLarge -> {
                entries.removeFirst()
                PushOutcome.Dropped(DropReason.TOO_LARGE)
            }

            RelayResult.Unauthorised -> halted(SendHalt.UNAUTHORISED)

            RelayResult.PairingUnknown -> halted(SendHalt.PAIRING_GONE)
        }
    }

    /**
     * The caller has lifted its persisted `p2e` counter above the relay's high-water mark, per
     * §6.1 — the same reconciliation the spec spells out for the engine's `e2p` counter, which the
     * phone owes symmetrically for `p2e`.
     *
     * Clears **only** [SendHalt.COUNTER_BEHIND]. The head is rebuilt on the next [next], which is
     * the one place in this class where rebuilding is correct.
     */
    fun reconciled() {
        if (halt == SendHalt.COUNTER_BEHIND) {
            halt = null
            haltLatest = null
        }
    }

    /** A fresh bearer is in hand ([RelayTokenLadder]). Clears **only** [SendHalt.UNAUTHORISED]. */
    fun reauthorised() {
        if (halt == SendHalt.UNAUTHORISED) halt = null
    }

    private fun halted(reason: SendHalt): PushOutcome {
        halt = reason
        return PushOutcome.Halted(reason, haltLatest)
    }

    private fun haltWith(reason: SendHalt): SendStep {
        halt = reason
        return SendStep.Halted(reason, haltLatest)
    }
}
