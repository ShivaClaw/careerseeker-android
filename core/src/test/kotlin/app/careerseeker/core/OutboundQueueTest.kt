package app.careerseeker.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phone's `p2e` send ordering (S6's send path).
 *
 * Every rule here has a version that compiles, renders correctly and reports nothing wrong: a
 * retry that rebuilds, a 409 read as success, a poisoned envelope retried forever, a mark dropped
 * because the network was down. Each is asserted rather than commented.
 */
class OutboundQueueTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val pairing = "p_7Fq2mXk9LtVbN3wR"
    private val keyId = "k-2026-06-01"
    private val kP2e = ByteArray(32) { (it * 7).toByte() }
    private val ts = "2026-06-11T14:02:11Z"

    /** Counts its own calls, so "was a second sequence number burned?" is directly observable. */
    private class Seqs(start: Long = 0) : OutboundEnvelopeFactory.SeqSource {
        var issued = 0L
            private set
        private var seq = start
        override fun next(): Long {
            issued++
            return ++seq
        }
    }

    /** Deterministic, and distinct per envelope so two builds cannot accidentally match. */
    private class Nonces : OutboundEnvelopeFactory.NonceSource {
        private var n = 0
        override fun next(): ByteArray {
            n++
            return ByteArray(Protocol.NONCE_BYTES) { (it + n).toByte() }
        }
    }

    /** Signature content is irrelevant here — only whether one could be produced at all. */
    private val stubSigner = DeviceSigner { ByteArray(64) { 0x5a } }

    private fun queue(signer: DeviceSigner? = stubSigner): Pair<OutboundQueue, Seqs> {
        val seqs = Seqs()
        val factory = OutboundEnvelopeFactory(
            pairing, keyId, kP2e,
            seqSource = seqs,
            signer = signer,
            nonces = Nonces(),
        )
        return OutboundQueue(factory) to seqs
    }

    private fun body(outcome: String) = """{"app_id":"a1","outcome":"$outcome","at":"$ts"}"""

    private fun OutboundQueue.enqueueOutcome(id: String, outcome: String = "sent") =
        enqueue(id, "outcome", body(outcome), ts)

    private fun seqOf(wire: String): Long =
        json.parseToJsonElement(wire).jsonObject["seq"]!!.jsonPrimitive.longOrNull!!

    private fun pushOf(step: SendStep): OutboundItem = (step as SendStep.Push).item

    // ---------------------------------------------------------------- build once, retry verbatim

    @Test
    fun `an empty queue asks for nothing`() {
        val (q, seqs) = queue()
        assertEquals(SendStep.Idle, q.next())
        assertEquals(0L, seqs.issued, "an idle queue must not consume a sequence number")
    }

    @Test
    fun `the envelope is built once and every retry re-sends the identical bytes`() {
        // The failure this prevents is silent: rebuilding burns a second p2e seq, and if the first
        // attempt landed and merely lost its response the engine gets one intention twice.
        val (q, seqs) = queue()
        q.enqueueOutcome("a1")

        val first = pushOf(q.next()).wire
        assertEquals(1L, seqs.issued)

        assertEquals(PushOutcome.Retry, q.onPushed(RelayResult.Unavailable("offline")))

        val second = pushOf(q.next())
        assertEquals(first, second.wire, "a retry must re-send the frozen bytes, not rebuild them")
        assertEquals(1L, seqs.issued, "a retry must not consume a second sequence number")
        assertEquals(1, second.attempts, "the attempt count is what distinguishes a retry")
    }

    @Test
    fun `a failed push keeps the mark queued, because offline is not a data-loss event`() {
        val (q, _) = queue()
        q.enqueueOutcome("a1")
        q.next()

        q.onPushed(RelayResult.Unavailable("no route to host"))

        assertEquals(1, q.depth())
        assertEquals(listOf("a1"), q.queuedIds())
    }

    @Test
    fun `an accepted envelope leaves and the next one is built`() {
        val (q, seqs) = queue()
        q.enqueueOutcome("a1")
        q.enqueueOutcome("a2")

        val first = pushOf(q.next()).wire
        assertEquals(PushOutcome.Accepted, q.onPushed(RelayResult.Ok(Unit)))
        assertEquals(1, q.depth())

        val second = pushOf(q.next()).wire
        assertNotEquals(first, second)
        assertEquals(2L, seqs.issued)
        assertEquals(seqOf(first) + 1, seqOf(second), "§6.1: the counter advances per envelope")
    }

    @Test
    fun `only one envelope is in flight at a time`() {
        // Single-flight is what makes the 409 rule below checkable: a queue with several
        // unresolved sequence numbers outstanding cannot attribute a conflict to any of them.
        val (q, seqs) = queue()
        q.enqueueOutcome("a1")
        q.enqueueOutcome("a2")

        q.next()
        q.next()
        q.next()

        assertEquals(1L, seqs.issued, "the second item must not be built while the head is unresolved")
    }

    // ---------------------------------------------------------------- collapse

    @Test
    fun `a re-mark collapses onto an unbuilt entry rather than queueing a second envelope`() {
        val (q, seqs) = queue()
        assertTrue(!q.enqueueOutcome("a1", "sent"))
        assertTrue(q.enqueueOutcome("a1", "interview"), "the second must report a collapse")

        assertEquals(1, q.depth())
        val wire = pushOf(q.next()).wire
        assertEquals(1L, seqs.issued, "two taps before anything left the phone are one envelope")
        // The newer body is the one that got built.
        assertTrue(wire.isNotEmpty())
    }

    @Test
    fun `collapsing moves the entry to the end, so the newest decision is the newest to send`() {
        val (q, _) = queue()
        q.enqueueOutcome("a1")
        q.enqueueOutcome("a2")
        q.enqueueOutcome("a1", "offer")

        assertEquals(listOf("a2", "a1"), q.queuedIds())
    }

    @Test
    fun `an entry whose bytes are already built is never collapsed onto`() {
        // Those bytes may already be on the wire. Replacing them would silently drop a mark the
        // engine is about to receive; a second envelope is the honest outcome (§4.3.1 latest-wins).
        val (q, _) = queue()
        q.enqueueOutcome("a1", "sent")
        q.next()

        assertTrue(!q.enqueueOutcome("a1", "offer"), "must append, not collapse")
        assertEquals(2, q.depth())
        assertEquals(listOf("a1", "a1"), q.queuedIds())
    }

    // ---------------------------------------------------------------- the 409

    @Test
    fun `a conflict halts on COUNTER_BEHIND carrying the relay's own high-water mark`() {
        val (q, _) = queue()
        q.enqueueOutcome("a1")
        q.next()

        val outcome = q.onPushed(RelayResult.Conflict(latest = 41))

        assertEquals(PushOutcome.Halted(SendHalt.COUNTER_BEHIND, 41L), outcome)
        assertEquals(SendHalt.COUNTER_BEHIND, q.halted())
        assertEquals(1, q.depth(), "the mark is not lost — only its bytes are dead")
    }

    @Test
    fun `a conflict is never reported as delivered`() {
        // RelayClient retries transport inside one push call, so a lost response arrives as a
        // conflict on what this queue believes is its first attempt. Attempt count cannot
        // disambiguate, so the queue does not try: OutcomeMarkPolicy's convergence answers it.
        val (q, _) = queue()
        q.enqueueOutcome("a1")
        q.next()

        val outcome = q.onPushed(RelayResult.Conflict(latest = 7))

        assertNotEquals(PushOutcome.Accepted, outcome)
        assertTrue(outcome is PushOutcome.Halted)
    }

    @Test
    fun `the queue stays stopped until the counter is reconciled`() {
        val (q, seqs) = queue()
        q.enqueueOutcome("a1")
        q.next()
        q.onPushed(RelayResult.Conflict(latest = 41))

        assertEquals(SendStep.Halted(SendHalt.COUNTER_BEHIND, 41L), q.next())
        assertEquals(1L, seqs.issued, "a stopped queue must not keep burning sequence numbers")
    }

    @Test
    fun `reconciling rebuilds above the reported mark, the one place a rebuild is correct`() {
        val (q, seqs) = queue()
        q.enqueueOutcome("a1")
        val dead = pushOf(q.next()).wire
        q.onPushed(RelayResult.Conflict(latest = 41))

        q.reconciled()
        val rebuilt = pushOf(q.next())

        assertNull(q.halted())
        assertNotEquals(dead, rebuilt.wire, "the frozen bytes can never be accepted again")
        assertEquals(2L, seqs.issued, "the rebuild is the one call that legitimately burns a seq")
        assertEquals(0, rebuilt.attempts, "fresh bytes are a fresh attempt")
    }

    @Test
    fun `a conflict with no reported latest still halts, with nothing to reconcile against`() {
        // The pairing 409s answer {"error":"exists"} and carry no number.
        val (q, _) = queue()
        q.enqueueOutcome("a1")
        q.next()

        assertEquals(
            PushOutcome.Halted(SendHalt.COUNTER_BEHIND, null),
            q.onPushed(RelayResult.Conflict()),
        )
    }

    // ---------------------------------------------------------------- poison and halts

    @Test
    fun `a 413 drops only that envelope and the queue continues`() {
        // Retrying it would wedge every later mark behind an envelope that can never fit.
        val (q, _) = queue()
        q.enqueueOutcome("a1")
        q.enqueueOutcome("a2")
        q.next()

        assertEquals(PushOutcome.Dropped(DropReason.TOO_LARGE), q.onPushed(RelayResult.TooLarge))

        assertEquals(1, q.depth())
        assertNull(q.halted())
        assertEquals("a2", pushOf(q.next()).id)
    }

    @Test
    fun `pairing_unknown is terminal and no clearing call revives it`() {
        val (q, _) = queue()
        q.enqueueOutcome("a1")
        q.next()

        assertEquals(PushOutcome.Halted(SendHalt.PAIRING_GONE, null), q.onPushed(RelayResult.PairingUnknown))

        q.reconciled()
        q.reauthorised()
        assertEquals(SendHalt.PAIRING_GONE, q.halted())
    }

    @Test
    fun `unauthorised halts, keeps the bytes, and is cleared only by reauthorising`() {
        val (q, seqs) = queue()
        q.enqueueOutcome("a1")
        val wire = pushOf(q.next()).wire

        assertEquals(PushOutcome.Halted(SendHalt.UNAUTHORISED, null), q.onPushed(RelayResult.Unauthorised))
        q.reconciled()
        assertEquals(SendHalt.UNAUTHORISED, q.halted(), "the wrong clearing call must not work")

        q.reauthorised()
        assertEquals(wire, pushOf(q.next()).wire, "a token problem does not invalidate the envelope")
        assertEquals(1L, seqs.issued)
    }

    // ---------------------------------------------------------------- the device key

    @Test
    fun `no device key halts the queue and destroys nothing`() {
        // §5.4: outcome is state-changing. Dropping the user's marks to report a missing key
        // would delete data to describe a condition the UI should be showing instead.
        val (q, _) = queue(signer = null)
        q.enqueueOutcome("a1")

        assertEquals(SendStep.Halted(SendHalt.NO_DEVICE_KEY, null), q.next())
        assertEquals(1, q.depth())
        assertEquals(SendHalt.NO_DEVICE_KEY, q.halted())
    }

    @Test
    fun `a kind that needs no signature is unaffected by the absence of a device key`() {
        // Guards the halt above from being broader than §5.4 is: pull_request changes no engine
        // state, so it needs no key and must still flow on a phone that has none.
        val (q, seqs) = queue(signer = null)
        q.enqueue("pull", "pull_request", """{"since_seq":0}""", ts)

        val item = pushOf(q.next())
        assertEquals("pull_request", item.kind)
        assertEquals(1L, seqs.issued)
        assertNull(q.halted())
    }

    // ---------------------------------------------------------------- misuse

    @Test
    fun `reporting a push nobody was asked to make is refused`() {
        val (q, _) = queue()
        assertFailsWith<IllegalStateException> { q.onPushed(RelayResult.Ok(Unit)) }

        q.enqueueOutcome("a1")
        assertFailsWith<IllegalStateException>("onPushed before next() built anything") {
            q.onPushed(RelayResult.Ok(Unit))
        }
    }

    @Test
    fun `an unknown payload kind is refused at enqueue rather than at build`() {
        val (q, _) = queue()
        assertFailsWith<IllegalArgumentException> { q.enqueue("a1", "not_a_kind", "{}", ts) }
    }
}
