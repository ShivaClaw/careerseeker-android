package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * §4.3 (`pull_request`), §4.3.1 (a delta is a window, not a diff) and §6.2 (a large gap is a
 * signal to request a snapshot), asserted rather than trusted.
 *
 * §6.2's gap rule had no implementation and no test anywhere in either codebase before this
 * file; `ApplyResult.AwaitingSnapshot` was produced by the replica and discarded by every
 * caller. These tests are what make both rules real on the phone side.
 *
 * What they do **not** prove is the loop end to end: `:app`'s applier is Room-backed and needs
 * an emulator this program does not yet have (BLOCKED B-4), so the transport wiring that maps
 * `ApplyResult` onto [ApplyDisposition] is not exercised here.
 */
class PullPolicyTest {

    private val cold = ReplicaPosition(snapshotSeen = false, highestAppliedSeq = 0L)
    private val warm = ReplicaPosition(snapshotSeen = true, highestAppliedSeq = 10L)

    private fun request(decision: PullDecision): PullDecision.Request =
        assertIs<PullDecision.Request>(decision, "expected a pull_request, got $decision")

    // ---- pull on open (S4) ----

    @Test
    fun `opening with no snapshot asks for one`() {
        val r = request(PullPolicy().onOpen(cold))
        assertEquals(PullReason.COLD_START, r.reason)
    }

    @Test
    fun `opening with a snapshot already held asks nothing`() {
        assertEquals(PullDecision.None, PullPolicy().onOpen(warm))
    }

    /**
     * The fabrication-shaped case, and the reason [ReplicaPosition] carries two fields.
     *
     * A `heartbeat` applies on a replica that has never held a snapshot, so the high-water mark
     * can be well ahead of zero while the dashboard is empty. If the policy keyed off the seq it
     * would conclude "I am at 5, I must have state" and stay silent forever, leaving the phone
     * showing demo data. Only `snapshotSeen` can answer this.
     */
    @Test
    fun `a high-water mark advanced by heartbeats does not count as holding a snapshot`() {
        val heartbeatsOnly = ReplicaPosition(snapshotSeen = false, highestAppliedSeq = 5L)
        val r = request(PullPolicy().onOpen(heartbeatsOnly))
        assertEquals(PullReason.COLD_START, r.reason)
        assertEquals(0L, r.sinceSeq, "a phone with no snapshot must not claim to hold up to seq 5")
    }

    // ---- AwaitingSnapshot: the result that used to be dropped on the floor ----

    @Test
    fun `a delta refused for want of a snapshot asks for one`() {
        val r = request(PullPolicy().onEnvelope(7L, ApplyDisposition.AWAITING_SNAPSHOT, cold))
        assertEquals(PullReason.AWAITING_SNAPSHOT, r.reason)
    }

    @Test
    fun `the snapshot that arrives satisfies the request`() {
        val policy = PullPolicy()
        request(policy.onOpen(cold))
        assertTrue(policy.hasPendingRequest)

        assertEquals(
            PullDecision.None,
            policy.onEnvelope(8L, ApplyDisposition.APPLIED_SNAPSHOT, cold),
        )
        assertFalse(policy.hasPendingRequest, "the snapshot should have released the latch")
    }

    /**
     * A snapshot the engine published for its own reasons (it sends one on start, §4.3) releases
     * the latch too. The policy tracks whether the *need* is met, not whether its own ask was
     * answered — otherwise a phone that got what it wanted by luck would never ask again after
     * the next gap.
     */
    @Test
    fun `an unsolicited snapshot also releases the latch`() {
        val policy = PullPolicy()
        request(policy.onEnvelope(3L, ApplyDisposition.AWAITING_SNAPSHOT, cold))
        policy.onEnvelope(4L, ApplyDisposition.APPLIED_SNAPSHOT, cold)

        val afterGap = request(policy.onEnvelope(200L, ApplyDisposition.APPLIED, warm))
        assertEquals(PullReason.SEQUENCE_GAP, afterGap.reason)
    }

    // ---- §6.2 large gap ----

    @Test
    fun `a gap larger than the threshold asks for a fresh snapshot`() {
        val policy = PullPolicy(gapThreshold = 32L)
        val r = request(policy.onEnvelope(100L, ApplyDisposition.APPLIED, warm))
        assertEquals(PullReason.SEQUENCE_GAP, r.reason)
    }

    @Test
    fun `a gap at the threshold is not large enough`() {
        val policy = PullPolicy(gapThreshold = 32L)
        // 42 - 10 == 32, which is "large" only if the comparison is >=. §6.2 says a gap must not
        // stall the stream, so the benefit of the doubt goes to carrying on.
        assertEquals(PullDecision.None, policy.onEnvelope(42L, ApplyDisposition.APPLIED, warm))
    }

    @Test
    fun `an ordinary contiguous stream never asks`() {
        val policy = PullPolicy()
        var position = ReplicaPosition(snapshotSeen = true, highestAppliedSeq = 0L)
        for (seq in 1L..50L) {
            assertEquals(
                PullDecision.None,
                policy.onEnvelope(seq, ApplyDisposition.APPLIED, position),
                "seq $seq should not have triggered a pull",
            )
            position = position.copy(highestAppliedSeq = seq)
        }
    }

    // ---- dispositions that must not generate traffic ----

    @Test
    fun `stale re-delivery asks nothing`() {
        assertEquals(PullDecision.None, PullPolicy().onEnvelope(2L, ApplyDisposition.STALE, warm))
    }

    @Test
    fun `an unprojected kind asks nothing`() {
        assertEquals(PullDecision.None, PullPolicy().onEnvelope(11L, ApplyDisposition.IGNORED, warm))
    }

    /**
     * A malformed payload would very likely be reproduced byte for byte by a re-publish, so
     * asking again converts a parse defect into an unbounded request loop against the relay.
     */
    @Test
    fun `a malformed payload asks nothing rather than looping`() {
        val policy = PullPolicy()
        repeat(5) { assertEquals(PullDecision.None, policy.onEnvelope(11L + it, ApplyDisposition.MALFORMED, warm)) }
        assertFalse(policy.hasPendingRequest)
    }

    // ---- the latch ----

    @Test
    fun `many refused deltas produce exactly one request`() {
        val policy = PullPolicy()
        val decisions = (1L..10L).map { policy.onEnvelope(it, ApplyDisposition.AWAITING_SNAPSHOT, cold) }
        assertEquals(1, decisions.count { it is PullDecision.Request }, "one gap, one ask")
    }

    @Test
    fun `a failed push re-arms the policy`() {
        val policy = PullPolicy()
        request(policy.onOpen(cold))
        assertEquals(PullDecision.None, policy.onEnvelope(1L, ApplyDisposition.AWAITING_SNAPSHOT, cold))

        policy.onRequestFailed()
        assertFalse(policy.hasPendingRequest)
        request(policy.onEnvelope(2L, ApplyDisposition.AWAITING_SNAPSHOT, cold))
    }

    // ---- the wire value ----

    /**
     * The pin described in [PullPolicy]'s class doc. Every reason sends `since_seq: 0`, because
     * in v1 `pull_request` means "send me a snapshot" — the engine's `ISnapshotRepublisher`
     * implementations ignore the field and publish a full snapshot regardless. A later change
     * that starts reporting the phone's high-water mark has to fail this test first.
     */
    @Test
    fun `every reason sends since_seq zero`() {
        val reasons = listOf(
            PullPolicy().onOpen(cold),
            PullPolicy().onEnvelope(9L, ApplyDisposition.AWAITING_SNAPSHOT, cold),
            PullPolicy(gapThreshold = 1L).onEnvelope(999L, ApplyDisposition.APPLIED, warm),
        ).map { request(it) }

        assertEquals(3, reasons.map { it.reason }.toSet().size, "expected all three reasons")
        reasons.forEach { assertEquals(0L, it.sinceSeq, "${it.reason} sent a non-zero since_seq") }
    }

    /**
     * `pull_request` is not in [Protocol.STATE_CHANGING_KINDS], so §5.4 does not require an
     * envelope signature and the factory builds one with no signer configured. That is what
     * makes the whole pull loop reachable before S3's Android Keystore key exists — worth an
     * assertion, because if `pull_request` ever became state-changing this policy would start
     * throwing `UnsignableEnvelope` on a phone that has not paired a device key yet.
     */
    @Test
    fun `a pull_request needs no device signature`() {
        assertFalse(Protocol.STATE_CHANGING_KINDS.contains(PayloadKind.PULL_REQUEST.wire))

        val factory = OutboundEnvelopeFactory(
            pairing = "p_0123456789abcdef",
            keyId = "k1",
            keyPhoneToEngine = ByteArray(Protocol.KEY_BYTES) { 7 },
            seqSource = { 1L },
            signer = null,
            nonces = { ByteArray(Protocol.NONCE_BYTES) { 3 } },
        )

        val decision = request(PullPolicy().onOpen(cold))
        val wire = factory.pullRequest(decision.sinceSeq, "2026-06-11T14:02:11Z")

        assertTrue(wire.contains(""""dir":"p2e""""))
        assertFalse(wire.contains(""""sig""""), "an unsigned kind must not carry a sig field")
    }

    @Test
    fun `a nonsensical gap threshold is refused at construction`() {
        val failure = runCatching { PullPolicy(gapThreshold = 0L) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(failure)
    }
}
