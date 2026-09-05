package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The receive state machine's check ORDER, tested directly.
 *
 * [EnvelopeReceiver]'s own docstring says the order "is part of the protocol, not an
 * implementation detail: rejecting for the wrong reason usually means a check fired earlier
 * than intended and the real one is untested" — and until this file, nothing tested it.
 * `ProtocolVectorsTest` feeds the shared vectors through the receiver, but every vector
 * violates exactly one rule, so it pins *classification* and cannot pin *order*: a receiver
 * that ran its checks in any order at all would pass it.
 *
 * The technique here is one envelope violating TWO rules at once, asserting the earlier check
 * is the one that answers. That is the only construction that can tell the orders apart.
 *
 * Why order is load-bearing rather than cosmetic:
 *
 *  - The cheap header checks must precede crypto, or a 1 MiB forgery costs a GCM open before
 *    it is refused.
 *  - `replay` must precede `decrypt` for the same reason, and `decrypt` must precede `kind`
 *    because a kind read off an unauthenticated body is attacker-chosen (§8.6 — the decrypted
 *    body is where untrusted job and recruiter text lives).
 *  - The sequence number is committed only after every check passes, so garbage cannot burn
 *    sequence numbers. That claim is asserted here once per error code rather than in
 *    aggregate, because an aggregate check passes even if one code leaks.
 *
 * The engine twin is `src/Sync/EnvelopeReceiver.cs` in ShivaClaw/careerseeker and carries the
 * same docstring verbatim. Where this file pins an order, it pins it for both.
 */
class EnvelopeReceiverTest {

    private val pairing = "p_7Fq2mXk9LtVbN3wR"
    private val keyId = "k-2026-06-01"
    private val ts = "2026-06-11T14:02:11Z"

    private val kE2p = hex("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90")
    private val kP2e = hex("0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0")

    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    private fun keyFor(dir: String) = if (dir == "e2p") kE2p else kP2e

    private fun nonce(fill: Byte = 7) = ByteArray(Protocol.NONCE_BYTES) { fill }

    /**
     * AAD is built from the raw `dir` string rather than via [EnvelopeHeader], deliberately:
     * several cases below need a `dir` that [Direction.fromWire] rejects, and the enum cannot
     * express one. The format is [EnvelopeHeader.aad]'s, and `aad matches EnvelopeHeader` below
     * proves this helper has not drifted from it.
     */
    private fun aadOf(v: Int = Protocol.VERSION, dir: String, seq: Long, key: String = keyId) =
        "v=$v|pairing=$pairing|dir=$dir|seq=$seq|ts=$ts|key_id=$key"

    /** A well-formed envelope, sealed for real, that the receiver accepts unless mutated. */
    private fun envelope(
        v: Int = Protocol.VERSION,
        dir: String = "e2p",
        seq: Long = 1L,
        key: String = keyId,
        kind: String = "heartbeat",
        sig: String? = null,
        n: ByteArray = nonce(),
        sealWith: ByteArray? = null,
    ): ReceivedEnvelope {
        val aad = aadOf(v, dir, seq, key)
        val body = """{"kind":"$kind"}""".toByteArray(Charsets.UTF_8)
        val ct = SyncCrypto.seal(sealWith ?: keyFor(dir), n, aad, body)
        return ReceivedEnvelope(
            v, pairing, dir, seq, ts, key,
            Base64Url.encode(n), Base64Url.encode(ct), sig,
        )
    }

    private fun receiver(devicePub: ByteArray? = null) = EnvelopeReceiver(keyId, devicePub)

    /** Base64url for a payload larger than the §3.1 cap, which is measured on the ciphertext. */
    private fun oversized() = Base64Url.encode(ByteArray(Protocol.MAX_ENVELOPE_BYTES + 1))

    // ------------------------------------------------------------------ the helper itself

    @Test
    fun `the aad helper is byte-identical to EnvelopeHeader`() {
        assertEquals(
            EnvelopeHeader(1, pairing, Direction.ENGINE_TO_PHONE, 42L, ts, keyId).aad(),
            aadOf(1, "e2p", 42L),
            "the test helper has drifted from the normative AAD construction",
        )
    }

    @Test
    fun `the unmutated envelope is accepted, so every rejection below is caused by its mutation`() {
        val r = receiver().receive(envelope(), ::keyFor)
        assertTrue(r.accepted, "control envelope rejected as ${r.error} — the rest of this file proves nothing")
        assertEquals("heartbeat", r.kind)
    }

    // ------------------------------------------------------------------ pairwise order

    @Test
    fun `version is checked before key_id`() {
        val r = receiver().receive(envelope(v = 2, key = "k-revoked"), ::keyFor)
        assertEquals(ErrorCode.VERSION_UNSUPPORTED, r.error)
    }

    @Test
    fun `key_id is checked before the structural decode`() {
        val bad = envelope(key = "k-revoked").copy(nonce = "!!!not-base64url!!!")
        assertEquals(ErrorCode.KEY_UNKNOWN, receiver().receive(bad, ::keyFor).error)
    }

    @Test
    fun `key_id is checked before size`() {
        val bad = envelope(key = "k-revoked").copy(ciphertext = oversized())
        assertEquals(ErrorCode.KEY_UNKNOWN, receiver().receive(bad, ::keyFor).error)
    }

    @Test
    fun `the structural decode is checked before size`() {
        val bad = envelope().copy(nonce = "!!!not-base64url!!!", ciphertext = oversized())
        assertEquals(ErrorCode.DECRYPT_FAILED, receiver().receive(bad, ::keyFor).error)
    }

    @Test
    fun `a nonce of the wrong length is structural, not a decrypt failure discovered late`() {
        val bad = envelope().copy(nonce = Base64Url.encode(ByteArray(Protocol.NONCE_BYTES + 1)))
        assertEquals(ErrorCode.DECRYPT_FAILED, receiver().receive(bad, ::keyFor).error)
    }

    @Test
    fun `size is checked before signature placement`() {
        val bad = envelope(dir = "e2p", sig = "AAAA").copy(ciphertext = oversized())
        assertEquals(ErrorCode.TOO_LARGE, receiver().receive(bad, ::keyFor).error)
    }

    @Test
    fun `signature placement is checked before replay`() {
        val r = receiver()
        assertTrue(r.receive(envelope(seq = 5L), ::keyFor).accepted)

        // seq 3 is a replay AND carries a sig an e2p envelope may never carry.
        val bad = envelope(seq = 3L, sig = "AAAA")
        assertEquals(ErrorCode.BAD_SIGNATURE, r.receive(bad, ::keyFor).error)
    }

    /**
     * §3's `dir` rule, and the only construction that can observe it.
     *
     * `Direction.fromWire(env.dir) ?: reject(DECRYPT_FAILED)` sits between the signature-placement
     * check and replay. A single-rule envelope cannot see that step at all: delete it and an
     * unknown `dir` still ends in `decrypt_failed`, because the AAD it then feeds no longer matches
     * what the sender sealed. Measured at run 95 — replacing the rejection with a fallback to
     * [Direction.ENGINE_TO_PHONE] left `:core:test` green at **347/0**.
     *
     * Violating a *later* rule in the same envelope is what makes the step observable, which is
     * this file's whole technique. Under that fallback the envelope below is filed against the e2p
     * stream, where seq 3 is a replay, so the receiver answers `replay_rejected` — a different
     * code, from a check §3 says should never have been reached.
     *
     * **The engine twin has no equivalent check at all.** `src/Sync/EnvelopeReceiver.cs` passes the
     * raw string to `keyForDir`, to the AAD and to its sequence tracker, and an unknown `dir` is
     * refused only because the AEAD tag then fails. Both implementations answer `decrypt_failed`
     * today, so this is not a live defect — but two mechanisms that coincide are not one rule, and
     * no vector holds them together (B-26; the corpus gap is C-95-5).
     */
    @Test
    fun `a dir v1 does not define is refused before replay, not by the AEAD`() {
        val r = receiver()
        assertTrue(r.receive(envelope(dir = "e2p", seq = 5L), ::keyFor).accepted)

        // Two rules at once: `dir` is not a direction §3 defines, AND seq 3 would be a replay if
        // the receiver decided this envelope belonged to the e2p stream.
        val bad = envelope(dir = "e2p-x", seq = 3L)
        assertEquals(ErrorCode.DECRYPT_FAILED, r.receive(bad, ::keyFor).error)
    }

    @Test
    fun `replay is checked before decrypt`() {
        val r = receiver()
        assertTrue(r.receive(envelope(seq = 5L), ::keyFor).accepted)

        // Undecryptable AND a replay: the cheap check must answer, not the GCM open.
        val bad = envelope(seq = 3L).copy(ciphertext = Base64Url.encode(ByteArray(64)))
        assertEquals(ErrorCode.REPLAY_REJECTED, r.receive(bad, ::keyFor).error)
    }

    @Test
    fun `decrypt is checked before kind`() {
        // Sealed under the p2e key but sent as e2p, so the tag fails. The body it would have
        // decrypted to carries a kind no v1 receiver knows; DECRYPT_FAILED must still win,
        // because a kind read from a body whose tag did not verify is attacker-chosen.
        val bad = envelope(dir = "e2p", kind = "nonsense", sealWith = kP2e)
        assertEquals(ErrorCode.DECRYPT_FAILED, receiver().receive(bad, ::keyFor).error)
    }

    @Test
    fun `kind is checked before the signature requirement`() {
        // p2e + a reserved L2 kind + a sig that could never verify. If the signature check ran
        // first this would be BAD_SIGNATURE; the reserved kind must be refused on its own.
        val bad = envelope(dir = "p2e", kind = "state_change", sig = "!!!not-base64url!!!")
        assertEquals(ErrorCode.UNKNOWN_KIND, receiver(devicePub = ByteArray(65)).receive(bad, ::keyFor).error)
    }

    // ------------------------------------------------------------------ the reserved set

    @Test
    fun `every kind reserved for L2 is refused, signed or not`() {
        for (reserved in PayloadKind.RESERVED_FOR_L2) {
            val unsigned = envelope(dir = "p2e", kind = reserved)
            assertEquals(
                ErrorCode.UNKNOWN_KIND,
                receiver().receive(unsigned, ::keyFor).error,
                "$reserved must not be accepted by a v1 receiver",
            )
        }
    }

    /**
     * The receiver accepts an engine→phone `error`, which is the half of PQ-ERR-1 that can be
     * executed here rather than argued.
     *
     * §4.3's engine→phone table lists `error`, and `Protocol.cs` has it in `ShippingKinds`, so
     * an authentic one is a conforming envelope and must not be refused. Asserting that is not
     * the point of this test — the point is what the acceptance *implies*: [SyncPump] hands
     * every accepted payload to the single [ReplicaApplier], and `:app`'s implementation is a
     * `when` over four projected kinds with `else -> Ignored`. So the engine's only channel for
     * reporting a §7.2 rejection of something the phone sent decrypts cleanly, is counted as a
     * healthy envelope, and is dropped — the run-58 shape, one kind along.
     *
     * `:core` cannot assert the drop itself: the `when` is `:app` and needs the Android SDK
     * (**B-7**). What it can assert is that the payload gets that far, which is the step the
     * question turns on, and [PayloadKindCoverageTest] carries the classification half.
     */
    @Test
    fun `an engine to phone error payload is accepted, and therefore reaches the applier`() {
        val r = receiver().receive(envelope(dir = "e2p", kind = "error"), ::keyFor)

        assertTrue(r.accepted, "section 4.3 lists `error` engine->phone; it was rejected as ${r.error}")
        assertEquals("error", r.kind, "the applier is dispatched on this string")
    }

    @Test
    fun `a state-changing p2e kind without a signature is refused`() {
        for (kind in Protocol.STATE_CHANGING_KINDS) {
            val r = receiver().receive(envelope(dir = "p2e", kind = kind), ::keyFor)
            assertEquals(ErrorCode.BAD_SIGNATURE, r.error, "$kind must require a device signature")
        }
    }

    @Test
    fun `a state-changing p2e kind is refused when the receiver holds no device key`() {
        // deviceSigPub is null: the signature is present but there is nothing to check it with,
        // which must fail closed rather than pass for want of a verifier.
        val r = receiver(devicePub = null).receive(envelope(dir = "p2e", kind = "outcome", sig = "AAAA"), ::keyFor)
        assertEquals(ErrorCode.BAD_SIGNATURE, r.error)
    }

    // ------------------------------------------------------------------ sequence hygiene

    @Test
    fun `no rejection advances the sequence tracker, one error code at a time`() {
        val cases = listOf(
            ErrorCode.VERSION_UNSUPPORTED to envelope(v = 2, seq = 9L),
            ErrorCode.KEY_UNKNOWN to envelope(key = "k-revoked", seq = 9L),
            ErrorCode.DECRYPT_FAILED to envelope(seq = 9L).copy(nonce = "!!!"),
            ErrorCode.TOO_LARGE to envelope(seq = 9L).copy(ciphertext = oversized()),
            ErrorCode.BAD_SIGNATURE to envelope(seq = 9L, sig = "AAAA"),
            ErrorCode.UNKNOWN_KIND to envelope(seq = 9L, kind = "kill"),
        )

        for ((expected, env) in cases) {
            val r = receiver()
            assertTrue(r.receive(envelope(seq = 4L), ::keyFor).accepted)
            assertEquals(expected, r.receive(env, ::keyFor).error, "wrong classification for $expected")
            assertEquals(
                4L, r.highestAccepted(Direction.ENGINE_TO_PHONE),
                "$expected burned a sequence number — a rejected envelope must not move the cursor",
            )
        }
    }

    @Test
    fun `a replayed seq is refused and the tracker still does not move`() {
        val r = receiver()
        assertTrue(r.receive(envelope(seq = 5L), ::keyFor).accepted)
        assertEquals(ErrorCode.REPLAY_REJECTED, r.receive(envelope(seq = 5L), ::keyFor).error)
        assertEquals(ErrorCode.REPLAY_REJECTED, r.receive(envelope(seq = 1L), ::keyFor).error)
        assertEquals(5L, r.highestAccepted(Direction.ENGINE_TO_PHONE))
    }

    @Test
    fun `gaps are legitimate and must not stall the stream`() {
        // The relay purges on a TTL, so a missing seq is expected traffic, not an attack.
        val r = receiver()
        assertTrue(r.receive(envelope(seq = 1L), ::keyFor).accepted)
        assertTrue(r.receive(envelope(seq = 900L), ::keyFor).accepted, "a gap must not be refused")
        assertEquals(900L, r.highestAccepted(Direction.ENGINE_TO_PHONE))
    }

    @Test
    fun `the two directions keep independent counters`() {
        val r = receiver()
        assertTrue(r.receive(envelope(dir = "e2p", seq = 7L), ::keyFor).accepted)

        // seq 2 is below e2p's cursor but is the first p2e envelope, so it must be accepted.
        assertTrue(
            r.receive(envelope(dir = "p2e", seq = 2L, kind = "pull_request"), ::keyFor).accepted,
            "p2e must not inherit e2p's cursor",
        )
        assertEquals(7L, r.highestAccepted(Direction.ENGINE_TO_PHONE))
        assertEquals(2L, r.highestAccepted(Direction.PHONE_TO_ENGINE))
    }

    // ------------------------------------------------------------------ dispatch safety

    @Test
    fun `untrusted body text cannot choose the route`() {
        // §8.6: the decrypted body is where job and recruiter text lives. A carried string
        // spelling out a kind must not steer dispatch — this is why kindOf parses the JSON
        // instead of scanning for the first "kind" substring.
        //
        // The bodies below are ordered by what actually defeats a scanner, which is not what
        // it first looks like. A quoted "kind" inside a *string value* is escaped as \" in the
        // wire text, so a naive indexOf("\"kind\"") never matches it — that attack fails on its
        // own and proves nothing. A **nested object** is the one that works: its "kind" is
        // unescaped, well-formed, and appears earlier in the byte stream than the real one.
        val bodies = listOf(
            """{"note":"\"kind\":\"snapshot\"","kind":"heartbeat"}""",
            """{"meta":{"kind":"snapshot"},"kind":"heartbeat"}""",
            """{"items":[{"kind":"snapshot"}],"kind":"heartbeat"}""",
        )

        for (body in bodies) {
            val aad = aadOf(dir = "e2p", seq = 1L)
            val ct = SyncCrypto.seal(kE2p, nonce(), aad, body.toByteArray(Charsets.UTF_8))
            val env = ReceivedEnvelope(
                1, pairing, "e2p", 1L, ts, keyId,
                Base64Url.encode(nonce()), Base64Url.encode(ct), null,
            )

            val r = receiver().receive(env, ::keyFor)
            assertTrue(r.accepted, "body $body was rejected outright")
            assertEquals(
                "heartbeat", r.kind,
                "a kind carried inside untrusted body text chose the route: $body",
            )
        }
    }

    /**
     * **This test is the falsifier for a sentence in §3, and it has been green the whole time.**
     *
     * §3 says: *"Every structural rejection — an unknown top-level field, padded base64, a nonce
     * that is not 12 bytes, a `dir` that is neither `e2p` nor `p2e`, **a body that is not parseable
     * JSON** — is reported as `decrypt_failed`."* §7.2's own `decrypt_failed` row, in the same
     * document, lists four of those five and names the fifth **"unparseable framing"** — which is
     * the envelope, not the body, and the two are separated by an AEAD open.
     *
     * The assertion below says what this receiver actually does, and the engine agrees:
     * `src/Sync/EnvelopeReceiver.cs` catches `JsonException` from `JsonDocument.Parse` and returns
     * `SyncError.UnknownKind`, with a comment saying the agreement is deliberate. So **both
     * implementations conform to §7.2 and contradict §3's sentence**, and nothing is wrong on the
     * wire — the document disagrees with itself.
     *
     * It survived because **no vector covers the rule** (C-95-5), and a vector is the only artifact
     * that forces the document, the phone and the engine to be read against one another. Filed as
     * PQ-STR-1, undecided here: striking the body clause from §3 needs no code change on either
     * side, but a spec sentence is normative for two codebases and one cannot be compiled in a
     * cloud sandbox.
     *
     * **Do not "fix" this test to expect `decrypt_failed` without changing both receivers and the
     * document in the same change.**
     */
    @Test
    fun `a body that is not a JSON object is unknown_kind, not a crash`() {
        for (body in listOf("[]", "\"snapshot\"", "17", "not json at all", "")) {
            val aad = aadOf(dir = "e2p", seq = 1L)
            val ct = SyncCrypto.seal(kE2p, nonce(), aad, body.toByteArray(Charsets.UTF_8))
            val env = ReceivedEnvelope(
                1, pairing, "e2p", 1L, ts, keyId,
                Base64Url.encode(nonce()), Base64Url.encode(ct), null,
            )
            assertEquals(
                ErrorCode.UNKNOWN_KIND, receiver().receive(env, ::keyFor).error,
                "body <$body> should classify as unknown_kind",
            )
        }
    }

    @Test
    fun `a non-string kind is unknown_kind rather than a coerced route`() {
        for (body in listOf("""{"kind":7}""", """{"kind":null}""", """{"kind":["snapshot"]}""", """{"kind":true}""")) {
            val aad = aadOf(dir = "e2p", seq = 1L)
            val ct = SyncCrypto.seal(kE2p, nonce(), aad, body.toByteArray(Charsets.UTF_8))
            val env = ReceivedEnvelope(
                1, pairing, "e2p", 1L, ts, keyId,
                Base64Url.encode(nonce()), Base64Url.encode(ct), null,
            )
            assertEquals(ErrorCode.UNKNOWN_KIND, receiver().receive(env, ::keyFor).error, "body $body")
        }
    }

    @Test
    fun `an accepted envelope returns its plaintext and a rejected one never does`() {
        val ok = receiver().receive(envelope(), ::keyFor)
        assertEquals("""{"kind":"heartbeat"}""", ok.plaintext!!.toString(Charsets.UTF_8))

        val bad = receiver().receive(envelope(key = "k-revoked"), ::keyFor)
        assertNull(bad.plaintext, "a rejected envelope must not hand its body to the caller")
        assertNull(bad.kind)
    }

    // ------------------------------------------------------------------ receiveWire

    @Test
    fun `receiveWire applies the strict parse before the state machine`() {
        val env = envelope()
        val wire = """{"v":1,"pairing":"$pairing","dir":"e2p","seq":1,"ts":"$ts",""" +
            """"key_id":"$keyId","nonce":"${env.nonce}","ciphertext":"${env.ciphertext}"}"""
        assertTrue(receiver().receiveWire(wire) { keyFor(it) }.accepted, "control wire rejected")

        val withUnknownField = wire.dropLast(1) + ""","future_field":"x"}"""
        assertEquals(
            ErrorCode.DECRYPT_FAILED,
            receiver().receiveWire(withUnknownField) { keyFor(it) }.error,
            "§3 requires unknown top-level fields to be rejected, not ignored",
        )
    }

    @Test
    fun `the strict parse runs ahead of the version check, so a v2 dialect reads as malformed`() {
        // Pinning observed behaviour, not endorsing it. A v2 sender that adds a top-level field
        // AND bumps `v` is told decrypt_failed, never version_unsupported, so it cannot learn
        // the version is the problem. EnvelopeJson's docstring argues for this order — if the
        // sender speaks an unknown dialect, nothing it says should be interpreted — but the
        // cost lands on exactly the upgrade path §3's rule exists to protect.
        // Recorded as an observation in docs/protocol-questions.md; behaviour unchanged here.
        val env = envelope(v = 2)
        val wire = """{"v":2,"pairing":"$pairing","dir":"e2p","seq":1,"ts":"$ts",""" +
            """"key_id":"$keyId","nonce":"${env.nonce}","ciphertext":"${env.ciphertext}","future_field":"x"}"""

        assertEquals(ErrorCode.DECRYPT_FAILED, receiver().receiveWire(wire) { keyFor(it) }.error)

        // Without the extra field the same envelope is correctly told its version is unsupported.
        val plain = wire.replace(""","future_field":"x"""", "")
        assertEquals(ErrorCode.VERSION_UNSUPPORTED, receiver().receiveWire(plain) { keyFor(it) }.error)
    }

    @Test
    fun `receiveWire never advances the cursor on a malformed wire`() {
        val r = receiver()
        assertTrue(r.receive(envelope(seq = 3L), ::keyFor).accepted)
        assertEquals(ErrorCode.DECRYPT_FAILED, r.receiveWire("{ not json") { keyFor(it) }.error)
        assertEquals(ErrorCode.DECRYPT_FAILED, r.receiveWire("""{"v":1}""") { keyFor(it) }.error)
        assertEquals(3L, r.highestAccepted(Direction.ENGINE_TO_PHONE))
    }

    // ------------------------------------------------------------------ §3.1's size boundary

    /**
     * A real, sealed envelope whose **decoded** ciphertext is exactly [ciphertextBytes] long.
     *
     * GCM output is the plaintext plus [Protocol.TAG_BYTES], so the body is padded to land on
     * the target exactly. The padding is ASCII, so its character count is its byte count, and
     * `kind` stays readable because [EnvelopeReceiver] reads that one field and ignores the
     * rest of the body.
     *
     * Unlike [oversized], this is a genuine envelope: it decrypts. That is the whole point —
     * the case below has to reach the crypto it would be refused before.
     */
    private fun sealedWithCiphertextOf(ciphertextBytes: Int, seq: Long = 1L): ReceivedEnvelope {
        val prefix = "{\"kind\":\"heartbeat\",\"pad\":\""
        val suffix = "\"}"
        val padLen = (ciphertextBytes - Protocol.TAG_BYTES) - (prefix.length + suffix.length)
        require(padLen >= 0) { "target ciphertext too small to carry a kind" }

        val body = (prefix + "A".repeat(padLen) + suffix).toByteArray(Charsets.UTF_8)
        val n = nonce()
        val ct = SyncCrypto.seal(kE2p, n, aadOf(dir = "e2p", seq = seq), body)
        return ReceivedEnvelope(
            Protocol.VERSION, pairing, "e2p", seq, ts, keyId,
            Base64Url.encode(n), Base64Url.encode(ct), null,
        )
    }

    /**
     * **An envelope at exactly the cap is legal, and until this test nothing said so.**
     *
     * §3.1: *"The **decoded ciphertext** — the AEAD output including its 16-byte tag, after
     * base64url decoding — MUST NOT exceed 1 MiB. A receiver measures those decoded bytes, not
     * the length of the JSON envelope and not the length of the base64url text."* `MUST NOT
     * exceed` makes exactly 1 MiB the largest **legal** ciphertext, not the first illegal one.
     *
     * ## Why the existing cases could not pin this
     *
     * Every oversized case in the suite and in the shared corpus is `MAX_ENVELOPE_BYTES + 1`
     * **decoded** — [oversized] here, and `invalid-oversized`'s `synth_ciphertext_len` of
     * 1048577 upstream. A value one byte over the cap in decoded bytes is also over it in
     * base64url characters (~1.4 MB) and in JSON envelope length, so **all three candidate
     * readings reject it** and no assertion can tell them apart. Measured, not argued: two
     * mutations of the size check left `:core:test` green at **343/0** — measuring
     * `env.ciphertext.length` (the base64url text, the unit §3.1 forbids by name), and capping
     * at `MAX_ENVELOPE_BYTES * 3 / 4`. Removing the check outright reddens three tests, so the
     * gate was tested for *existence* and never for *unit* or *number*.
     *
     * ## Why the distinction is load-bearing rather than pedantic
     *
     * §3.1 records this exact bug on the relay: its guard *"compared a character count to a
     * byte budget, which capped the decoded payload at 786,432 bytes and left the top 256 KiB
     * of the declared range untransmittable"*. That is `MAX * 3 / 4` — mutation M2 above, green
     * on the phone. The engine side pins the boundary (`relay/test/relay.test.ts` covers *"the
     * maximum legal envelope surviving a push/pull round trip, and the first character beyond
     * it"*); the phone did not, and §4.4 instructs a future chunker to size against exactly
     * this number.
     *
     * A `too_large` here would refuse a payload the protocol declares legal, and the sender
     * could not discover why — the failure it produces is the silent one §3.1 calls out.
     */
    @Test
    fun `a ciphertext of exactly the cap is legal and is accepted`() {
        val env = sealedWithCiphertextOf(Protocol.MAX_ENVELOPE_BYTES)

        // The fixture is what it claims to be, asserted before it is relied on.
        assertEquals(
            Protocol.MAX_ENVELOPE_BYTES,
            Base64Url.decodeOrNull(env.ciphertext)!!.size,
            "fixture does not decode to exactly the cap, so the case below proves nothing",
        )

        val r = receiver().receive(env, ::keyFor)
        assertTrue(
            r.accepted,
            "a ciphertext of exactly MAX_ENVELOPE_BYTES decoded bytes is legal under §3.1 " +
                "(`MUST NOT exceed`), and was rejected as ${r.error}. A receiver measuring the " +
                "base64url text or the JSON envelope instead of the decoded bytes fails here " +
                "and passes every other size case in this suite.",
        )
        assertEquals("heartbeat", r.kind)
    }

    /**
     * One byte past the cap is `too_large` — the same verdict [oversized] gets, reached by a
     * genuinely sealed envelope rather than by noise that could never have decrypted.
     *
     * Paired with the case above on purpose: together they place the boundary between two
     * adjacent values, which is the only construction that pins *where* it is rather than
     * merely that it exists.
     */
    @Test
    fun `a ciphertext one byte past the cap is too_large`() {
        val env = sealedWithCiphertextOf(Protocol.MAX_ENVELOPE_BYTES + 1)
        assertEquals(
            Protocol.MAX_ENVELOPE_BYTES + 1,
            Base64Url.decodeOrNull(env.ciphertext)!!.size,
        )
        assertEquals(ErrorCode.TOO_LARGE, receiver().receive(env, ::keyFor).error)
    }

    /**
     * The maximum legal ciphertext is **longer than the cap** once base64url-encoded, and that
     * inequality is what gives the acceptance case above its discriminating power.
     *
     * 1,398,102 is not an incidental number: §3.1 declares the relay's character cap as
     * `ceil(4/3 × 1 MiB) = 1,398,102` and calls the conversion *"normative, not incidental:
     * **the relay MUST carry every envelope this section declares legal.**"* Pinning it here
     * ties the phone's fixture to the relay's `MAX_CIPHERTEXT_B64U_CHARS` without the phone
     * importing a relay constant, so a change to either side has to answer for the other.
     */
    @Test
    fun `the maximum legal ciphertext exceeds the cap when measured as base64url text`() {
        val env = sealedWithCiphertextOf(Protocol.MAX_ENVELOPE_BYTES)

        assertEquals(
            1_398_102, env.ciphertext.length,
            "§3.1 derives the relay's character cap as ceil(4/3 x 1 MiB) = 1,398,102; the " +
                "maximum legal envelope must encode to exactly that many base64url characters",
        )
        assertTrue(
            env.ciphertext.length > Protocol.MAX_ENVELOPE_BYTES,
            "if the encoded text were NOT longer than the cap, measuring the wrong unit would " +
                "be undetectable and the acceptance case above would prove nothing",
        )
    }
}
