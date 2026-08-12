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
}
