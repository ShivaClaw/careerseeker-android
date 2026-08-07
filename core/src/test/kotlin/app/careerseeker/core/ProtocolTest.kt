package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {

    @Test
    fun `aad matches the normative field order`() {
        // This exact string is what the engine authenticates. If it changes, every paired
        // device breaks, so it is pinned here as a literal rather than rebuilt from parts.
        val header = EnvelopeHeader(
            v = 1,
            pairing = "p_7Fq2mXk9LtVbN3wR",
            dir = Direction.ENGINE_TO_PHONE,
            seq = 48211,
            ts = "2026-06-11T14:02:11Z",
            keyId = "k-2026-06-01",
        )
        assertEquals(
            "v=1|pairing=p_7Fq2mXk9LtVbN3wR|dir=e2p|seq=48211|ts=2026-06-11T14:02:11Z|key_id=k-2026-06-01",
            header.aad(),
        )
    }

    @Test
    fun `directions round-trip through their wire form`() {
        assertEquals(Direction.ENGINE_TO_PHONE, Direction.fromWire("e2p"))
        assertEquals(Direction.PHONE_TO_ENGINE, Direction.fromWire("p2e"))
        assertNull(Direction.fromWire("engine"))
        assertNull(Direction.fromWire(""))
    }

    @Test
    fun `pairing ids are opaque and strictly shaped`() {
        assertTrue(isValidPairingId("p_7Fq2mXk9LtVbN3wR"))
        assertFalse(isValidPairingId("p_short"))
        assertFalse(isValidPairingId("7Fq2mXk9LtVbN3wR"))
        // A pairing id must never be able to carry an identity.
        assertFalse(isValidPairingId("brandon@example.com"))
    }

    @Test
    fun `reserved L2 kinds are not shippable in v1`() {
        for (reserved in PayloadKind.RESERVED_FOR_L2) {
            assertNull(
                PayloadKind.fromWire(reserved),
                "$reserved is reserved for L2 and must not resolve to a v1 payload kind",
            )
        }
    }

    @Test
    fun `kill is reserved rather than shipped`() {
        // Called out on its own because this is the one whose absence is a product
        // decision, not an oversight: the phone does not get engine control in v1.
        assertTrue(PayloadKind.RESERVED_FOR_L2.contains("kill"))
        assertNull(PayloadKind.fromWire("kill"))
    }

    @Test
    fun `no payload kind implies a send path`() {
        // The invariant the entire program is built around. The phone never sends email;
        // it edits drafts that the engine owns.
        val offenders = PayloadKind.entries.filter {
            it.wire.contains("send") || it.wire.contains("submit") || it.wire.contains("transmit")
        }
        assertTrue(offenders.isEmpty(), "payload kinds implying transmission: $offenders")
    }

    @Test
    fun `crypto parameters match the AES-256-GCM decision`() {
        assertEquals("AES-256-GCM", Protocol.CIPHER)
        assertEquals(32, Protocol.KEY_BYTES)
        assertEquals(12, Protocol.NONCE_BYTES)
        assertEquals(16, Protocol.TAG_BYTES)
    }

    @Test
    fun `directional key derivation uses distinct info strings`() {
        // Identical info strings would derive one key for both directions, letting a
        // captured envelope be replayed back at its sender.
        assertTrue(Protocol.HKDF_INFO_ENGINE_TO_PHONE != Protocol.HKDF_INFO_PHONE_TO_ENGINE)
    }

    @Test
    fun `sequence tracker rejects regressions but tolerates gaps`() {
        val tracker = SequenceTracker()
        assertTrue(tracker.accept(Direction.ENGINE_TO_PHONE, 1))
        assertTrue(tracker.accept(Direction.ENGINE_TO_PHONE, 2))

        // Replay.
        assertFalse(tracker.accept(Direction.ENGINE_TO_PHONE, 2))
        assertFalse(tracker.accept(Direction.ENGINE_TO_PHONE, 1))

        // A gap is legitimate: the relay purges on a TTL, and a gap must not stall the
        // stream. This is the case a naive "seq must equal last + 1" check gets wrong.
        assertTrue(tracker.accept(Direction.ENGINE_TO_PHONE, 99))
        assertEquals(99, tracker.highestAccepted(Direction.ENGINE_TO_PHONE))
    }

    @Test
    fun `directions track sequences independently`() {
        val tracker = SequenceTracker()
        assertTrue(tracker.accept(Direction.ENGINE_TO_PHONE, 5))
        // Same number, other direction: independent counters, so this is not a replay.
        assertTrue(tracker.accept(Direction.PHONE_TO_ENGINE, 5))
        assertEquals(5, tracker.highestAccepted(Direction.PHONE_TO_ENGINE))
    }

    @Test
    fun `envelope size limit matches the spec`() {
        assertEquals(1024 * 1024, Protocol.MAX_ENVELOPE_BYTES)
    }
}
