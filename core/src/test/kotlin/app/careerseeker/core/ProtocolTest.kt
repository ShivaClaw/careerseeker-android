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

    /**
     * **The two §5.2 suite names, pinned against literals transcribed by hand** — run 76's
     * test for the seven domain-separation strings, applied to the two constants that sweep
     * left out.
     *
     * **`SUITE_HYBRID_RESERVED` was guarded by nothing, and it is the last constant in
     * `Protocol.kt` that was.** Measured, one mutation at a time, `scripts/core-probe.sh
     * --rerun` from a **346/0** baseline (C-83-3):
     *
     *  - `VERSION = 1 -> 2` — **RED**.
     *  - `SUITE -> "p256-hkdf-sha512"` — **RED**, two tests: `PairingDerivationTest >
     *    completionAad is the pinned four-field format` and `ProtocolVectorsTest > index
     *    agrees with the shipping constants`.
     *  - `SUITE_HYBRID_RESERVED -> "p256+mlkem1024-hkdf-sha256"` — **GREEN, 346 passed, 0
     *    failed.** Caught by nothing.
     *
     * **Why nothing caught it.** Both of its references move with it. `PairingSessionTest >
     * an unrecognised suite refuses to pair` builds its invite *from* the constant and asserts
     * the parse is rejected — but every unsupported string is rejected identically, so the
     * assertion holds for any value; and `SUITE_HYBRID_RESERVED !in SUPPORTED_SUITES` is
     * satisfied by any wrong string too, more easily than by the right one. That is the
     * seventy-fourth run's trap exactly: a derivation compared against itself agrees with
     * itself and disagrees with the document.
     *
     * **This is not a live drift.** The value here is correct — it matches
     * `docs/Sync-Protocol.md` §5.2 line 306 and the engine's `src/Sync/Protocol.cs:21`, and
     * all 64 occurrences across every ref in both repositories agree (C-83-4). The defect is
     * that nothing keeps it correct.
     *
     * **The string is not a label; it names a parameter set, and a sizing decision rests on
     * it.** §5.2 records that under this suite the QR additionally carries the ML-KEM
     * encapsulation key and `ikm` becomes a concatenation of both shared secrets, and that
     * *"QR payload budget is checked against the hybrid suite's sizes now: ML-KEM-768's
     * 1184-byte key fits comfortably in a version-40 QR"*. The mutation above names
     * ML-KEM-1024, whose key is 1568 bytes — so a slip of two characters silently invalidates
     * the budget the spec says was already checked.
     *
     * **v1 behaviour is unaffected either way, which is the point.** Both sides reject the
     * reserved suite today, and reject a corrupted one identically, so the failure is
     * invisible until the hybrid migration ships — the one moment the two implementations
     * must agree on this string, and the moment no test has ever compared them.
     *
     * **The engine's guard accepts the same wrong value** (read, not executed — no `dotnet`
     * or `pwsh` here, C-ENV-1). `tests/SyncHarness/Program.cs` asserts
     * `Protocol.SuiteHybridReserved.Contains("mlkem") && Protocol.SuiteHybridReserved !=
     * Protocol.Suite`; `"p256+mlkem1024-hkdf-sha256"` satisfies both conjuncts, so the same
     * mutation is green on that side too (C-83-5). Fixing that half needs a gate this sandbox
     * cannot run; it is filed in STATE.md's ordered intent with the mutation that proves it.
     */
    @Test
    fun `the suite names are the ones section 5_2 prints`() {
        assertEquals("p256-hkdf-sha256", Protocol.SUITE)
        assertEquals("p256+mlkem768-hkdf-sha256", Protocol.SUITE_HYBRID_RESERVED)
        assertTrue(Protocol.SUITE != Protocol.SUITE_HYBRID_RESERVED)
    }

    @Test
    fun `directional key derivation uses distinct info strings`() {
        // Identical info strings would derive one key for both directions, letting a
        // captured envelope be replayed back at its sender.
        assertTrue(Protocol.INFO_ENGINE_TO_PHONE != Protocol.INFO_PHONE_TO_ENGINE)
    }

    /**
     * **The seven domain-separation strings of §5.2 and §5.4, pinned against literals
     * transcribed by hand** — run 75's lesson for `ErrorCode` and `PayloadKind`, applied to
     * the strings those two vocabularies sit beside.
     *
     * **This test was written to close a hypothesis, and the hypothesis was wrong.** Run 75
     * filed these constants as a successor target on the reading that `ProtocolTest` asserted
     * only that two of them *differ*, `HkdfTest` uses its own literals, and the envelope
     * vectors carry `key_hex` directly rather than deriving it — so the phone could derive
     * from wrong info strings, stay green here, and disagree with the engine only in the
     * field. **Measured, all seven are already guarded**: each was mutated one at a time and
     * `scripts/core-probe.sh --rerun` went red on every one (C-76-3). The premise was true;
     * the conclusion was not. What catches them is the *pairing* vectors — `pairing-basic`
     * carries `k_e2p_hex`, `k_p2e_hex`, `relay_token_b64u`, `provisional_token_b64u` and
     * `confirm` as **derived** values, and `ProtocolVectorsTest` recomputes all five.
     *
     * **So why add this at all?** Because that guard is the corpus, and `VECTORS.lock` states
     * the corpus's guarantee precisely: *"the phone matches the pin"*, never *"the phone
     * matches the engine"*. Three properties follow, and this test supplies all three.
     *
     * 1. **It is independent of the corpus.** Every existing guard runs through a vector, so
     *    a re-pin, a dropped vector or a skipped enumeration takes the guard with it. This one
     *    reads no file.
     * 2. **`INFO_ENGINE_TO_PHONE` had exactly one guard.** Measured: mutating it reddened
     *    `ProtocolVectorsTest > pairing derivation reproduces every vector value` **and nothing
     *    else**, where `p2e`, `relay-token`, `confirm` and `bootstrap` each reddened three to
     *    five tests across `PairingFlowTest` and `PairingSessionTest`. The e2p direction is the
     *    thin one, because the phone *seals* under `k_p2e` and only *opens* under `k_e2p`.
     * 3. **It states the contract where a reader looks for it.** The literals below are
     *    transcribed from `docs/Sync-Protocol.md` §5.2 lines 414-417 and 444, §5.2.2 line 464
     *    and §5.4 line 522 at vector pin `7328a0b`, and they match the engine's
     *    `src/Sync/Protocol.cs:23-29` at that same commit — verified constant by constant
     *    (C-76-4). Seven on the phone, seven in the engine, no eighth on either side.
     *
     * Nothing here is read from `Protocol.kt`; a copy of the source would assert nothing, which
     * is the defect fixed one file over in `PairingDerivationTest`.
     */
    @Test
    fun `the domain-separation strings are the ones section 5 prints`() {
        assertEquals("careerseeker/v1/e2p", Protocol.INFO_ENGINE_TO_PHONE)
        assertEquals("careerseeker/v1/p2e", Protocol.INFO_PHONE_TO_ENGINE)
        assertEquals("careerseeker/v1/relay-token", Protocol.INFO_RELAY_TOKEN)
        assertEquals("careerseeker/v1/confirm", Protocol.INFO_CONFIRM)
        assertEquals("careerseeker/v1/bootstrap", Protocol.BOOTSTRAP_SALT)
        assertEquals("careerseeker/v1/pair", Protocol.PAIR_AAD_PREFIX)
        assertEquals("careerseeker/v1/cmd", Protocol.COMMAND_SIG_PREFIX)
    }

    /**
     * The seven above are seven *different* strings. A copy-paste that gave two derivations
     * the same label would keep every literal assertion above green — each one still equals
     * what it is compared to — while collapsing two domains into one.
     *
     * `directional key derivation uses distinct info strings` makes this point for the two
     * directional keys only, which is where replay lives; this widens it to the whole set,
     * because `BOOTSTRAP_SALT` colliding with `INFO_RELAY_TOKEN` would make §5.2.1's
     * provisional token equal to §5.2.3's final one and the bootstrap credential would never
     * expire — the ladder `PairingDerivationTest` calls load-bearing.
     */
    @Test
    fun `the domain-separation strings are pairwise distinct`() {
        val all = listOf(
            Protocol.INFO_ENGINE_TO_PHONE,
            Protocol.INFO_PHONE_TO_ENGINE,
            Protocol.INFO_RELAY_TOKEN,
            Protocol.INFO_CONFIRM,
            Protocol.BOOTSTRAP_SALT,
            Protocol.PAIR_AAD_PREFIX,
            Protocol.COMMAND_SIG_PREFIX,
        )
        assertEquals(7, all.size)
        assertEquals(all.size, all.toSet().size, "two domain separators collided")
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

    /**
     * The §3.1 cap is 1 MiB. This pins the **number**; `EnvelopeReceiverTest` pins the **unit**
     * it is measured in and the boundary it sits on, which this assertion cannot see — a
     * receiver comparing this value against base64url characters satisfies it exactly as well
     * as one comparing it against decoded ciphertext bytes.
     */
    @Test
    fun `the section 3-1 cap is 1 MiB`() {
        assertEquals(1024 * 1024, Protocol.MAX_ENVELOPE_BYTES)
    }
}
