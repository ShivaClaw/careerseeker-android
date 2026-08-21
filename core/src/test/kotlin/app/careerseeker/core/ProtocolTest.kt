package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {

    /**
     * §7.2's error table, transcribed by hand from `docs/Sync-Protocol.md`.
     *
     * Literal strings, never derived from [ErrorCode]. The seventy-fourth run's finding was
     * that a derivation compared against itself agrees with itself while both sides disagree
     * with the normative document; a hand copy is the only side of this comparison that can
     * disagree with the enum, which is the whole point of spelling it out. Update it only
     * when §7.2 changes, and in the same commit as the enum.
     */
    private val section72Codes = setOf(
        "version_unsupported", "replay_rejected", "decrypt_failed", "unknown_kind",
        "key_unknown", "bad_signature", "rev_conflict", "pairing_unknown", "too_large",
        "unimplemented",
    )

    /**
     * §4.3's reserved-for-L2 list, transcribed by hand. Same rule as [section72Codes].
     */
    private val section43Reserved = setOf(
        "gate_request", "gate_resolve", "kill", "config_change", "lesson_proposal", "metric",
        "state_change",
    )

    /**
     * [ErrorCode] is exactly §7.2's table — and until the seventy-fifth run it was not.
     *
     * The enum was written 2026-07-22 with nine rows; §7.2 grew a tenth (`unimplemented`) on
     * 2026-07-24, amended into the spec and `src/Sync/Protocol.cs` in one commit. The phone
     * never followed, and **nothing in either repo compares the two**: `ErrorCode.entries` was
     * enumerated by no test at all, so the omission was not merely unnoticed, it was
     * unnoticeable. Deleting `pairing_unknown` from the enum on the pre-fix tree left
     * `:core:test` green at 338/0 — the negative control that shows this assertion had no
     * predecessor rather than a weak one.
     *
     * Set equality in both directions on purpose. A missing row is the drift that actually
     * happened; an extra one would be the phone inventing a rejection reason the engine will
     * never send and no §7.2 reader can interpret.
     */
    @Test
    fun `error codes are exactly section 7-2's table`() {
        assertEquals(
            section72Codes,
            ErrorCode.entries.map { it.wire }.toSet(),
            "ErrorCode is a transcription of Sync-Protocol.md section 7.2 and must equal it. " +
                "A row present in the spec and absent here cannot be represented, let alone " +
                "acted on -- that is how `unimplemented` went missing for 28 days. A row here " +
                "and not in the spec is a rejection reason the engine never sends. If section " +
                "7.2 has genuinely changed, change the enum and section72Codes together.",
        )
    }

    /**
     * The wire strings are the ones §7.2 prints, not merely a set of the right size.
     *
     * Separated from the membership check because the two fail for different reasons and a
     * reader debugging one should not have to disentangle the other. Only the codes the shared
     * vector corpus exercises are pinned anywhere else (`ProtocolVectorsTest` compares
     * `expect_error` against [ErrorCode.wire]); `rev_conflict`, `pairing_unknown` and
     * `unimplemented` have no vector, so this is their only guard.
     */
    @Test
    fun `error code wire strings are lowercase snake case as section 7-2 prints them`() {
        val malformed = ErrorCode.entries.filter { !Regex("^[a-z]+(_[a-z]+)*$").matches(it.wire) }
        assertTrue(malformed.isEmpty(), "wire strings must be lowercase snake_case: $malformed")
        assertEquals(
            ErrorCode.entries.size,
            ErrorCode.entries.map { it.wire }.toSet().size,
            "two ErrorCode constants share a wire string",
        )
    }

    /**
     * [PayloadKind.RESERVED_FOR_L2] is exactly §4.3's reserved list.
     *
     * The same unguarded shape as [ErrorCode], found by the same sweep: five call sites
     * iterate this set and assert a property of each member, so **dropping** a member simply
     * makes those loops test less. Deleting `metric` on the pre-fix tree left `:core:test`
     * green at 338/0. A reserved name that quietly leaves this set is a name a future L2 can
     * collide with — the exact thing §4.3 claims the list prevents.
     */
    @Test
    fun `reserved L2 kinds are exactly section 4-3's list`() {
        assertEquals(
            section43Reserved,
            PayloadKind.RESERVED_FOR_L2,
            "RESERVED_FOR_L2 is a transcription of section 4.3's reserved list. Every other " +
                "test of this set iterates it, so a dropped name weakens them all in silence.",
        )
    }

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
        assertTrue(Protocol.INFO_ENGINE_TO_PHONE != Protocol.INFO_PHONE_TO_ENGINE)
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
