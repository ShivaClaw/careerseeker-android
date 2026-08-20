package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phone→engine envelopes (A6 / P4's phone half).
 *
 * Everything built here is fed back through [EnvelopeReceiver] — the same state machine the
 * engine mirrors — so "we built it correctly" means "the receiving side accepts it", not "the
 * string looks right".
 */
class OutboundEnvelopesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val pairing = "p_7Fq2mXk9LtVbN3wR"
    private val keyId = "k-2026-06-01"
    private val kP2e = hex("0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0")

    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    /** A real P-256 signer, so signatures actually verify rather than being stubbed. */
    private class TestKey {
        private val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        val publicPoint: ByteArray = run {
            val pub = pair.public as java.security.interfaces.ECPublicKey
            fun pad(b: java.math.BigInteger): ByteArray {
                val raw = b.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
                return ByteArray(32 - raw.size) + raw
            }
            byteArrayOf(0x04) + pad(pub.w.affineX) + pad(pub.w.affineY)
        }

        val signer = DeviceSigner { input ->
            val der = java.security.Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private); update(input.toByteArray(Charsets.US_ASCII)); sign()
            }
            derToRaw(der)
        }

        /** JCA emits DER; §5.4 specifies fixed-width r||s. */
        private fun derToRaw(der: ByteArray): ByteArray {
            var i = 2
            if (der[1].toInt() and 0x80 != 0) i = 2 + (der[1].toInt() and 0x7f)
            fun readInt(at: Int): Pair<ByteArray, Int> {
                val len = der[at + 1].toInt()
                val v = der.copyOfRange(at + 2, at + 2 + len).dropWhile { it == 0.toByte() }.toByteArray()
                return (ByteArray(32 - v.size) + v) to (at + 2 + len)
            }
            val (r, next) = readInt(i)
            val (s, _) = readInt(next)
            return r + s
        }
    }

    private fun factory(
        signer: DeviceSigner? = null,
        startSeq: Long = 0,
    ): Pair<OutboundEnvelopeFactory, () -> Long> {
        var seq = startSeq
        val f = OutboundEnvelopeFactory(
            pairing, keyId, kP2e,
            seqSource = { ++seq },
            signer = signer,
            nonces = { ByteArray(Protocol.NONCE_BYTES) { i -> (i + seq).toByte() } },
        )
        return f to { seq }
    }

    // ---------------------------------------------------------------- the signing rule

    @Test
    fun `a state-changing kind cannot be built without a signer`() {
        // Not "builds an unsigned envelope the engine will reject" — refuses to build it. The
        // audit chain's ability to prove which paired device asked for a change depends on it.
        val (f, _) = factory(signer = null)
        for (kind in Protocol.STATE_CHANGING_KINDS) {
            assertFailsWith<OutboundEnvelopeFactory.UnsignableEnvelope>("$kind must refuse") {
                f.build(kind, "{}", "2026-06-11T14:02:11Z")
            }
        }
    }

    @Test
    fun `a non-state-changing kind is built unsigned, and carrying a sig would be wrong`() {
        val (f, _) = factory(signer = TestKey().signer)
        val wire = f.pullRequest(sinceSeq = 12, timestamp = "2026-06-11T14:02:11Z")
        val env = json.parseToJsonElement(wire).jsonObject

        assertNull(env["sig"], "pull_request is not state-changing; a sig here is a protocol error")
        assertEquals("p2e", env["dir"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an outcome envelope is accepted by the receiver, signature and all`() {
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val wire = f.outcome("app_1", Outcome.INTERVIEW, at = "2026-06-11T14:02:11Z")

        val receiver = EnvelopeReceiver(keyId, key.publicPoint)
        val result = receiver.receiveWire(wire) { kP2e }

        assertTrue(result.accepted, "receiver rejected our own envelope: ${result.error?.wire}")
        assertEquals("outcome", result.kind)

        val body = json.parseToJsonElement(result.plaintext!!.toString(Charsets.UTF_8))
            .jsonObject["body"]!!.jsonObject
        assertEquals("app_1", body["app_id"]!!.jsonPrimitive.content)
        assertEquals("interview", body["outcome"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a tampered outcome envelope stops verifying`() {
        // Proves the signature is over the real thing rather than decorative: flip one
        // character of ciphertext and the receiver must reject it.
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val wire = f.outcome("app_1", Outcome.OFFER, at = "2026-06-11T14:02:11Z")
        val env = json.parseToJsonElement(wire).jsonObject
        val ct = env["ciphertext"]!!.jsonPrimitive.content
        val flipped = ct.take(4) + (if (ct[4] == 'A') 'B' else 'A') + ct.drop(5)

        val result = EnvelopeReceiver(keyId, key.publicPoint)
            .receiveWire(wire.replace(ct, flipped)) { kP2e }

        assertTrue(!result.accepted)
        assertTrue(
            result.error == ErrorCode.DECRYPT_FAILED || result.error == ErrorCode.BAD_SIGNATURE,
            "expected a rejection, got ${result.error?.wire}",
        )
    }

    // ---------------------------------------------------------------- sequence discipline

    @Test
    fun `sequence numbers are owned by the factory and never repeat`() {
        // §6.1: a reused p2e seq is silently dropped by the engine as a replay, which looks
        // exactly like "the outcome didn't save". The caller cannot supply one.
        val (f, _) = factory(signer = TestKey().signer)
        val seqs = (1..5).map {
            json.parseToJsonElement(f.outcome("app_$it", Outcome.SENT, "2026-06-11T14:02:1${it}Z"))
                .jsonObject["seq"]!!.jsonPrimitive.content.toLong()
        }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), seqs)
        assertEquals(seqs.distinct().size, seqs.size)
    }

    @Test
    fun `a queue of offline outcomes replays in order and the receiver accepts every one`() {
        // The offline-queue case: envelopes built while unreachable, pushed later in order.
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val queued = listOf(
            f.outcome("app_1", Outcome.SENT, "2026-06-11T14:02:11Z"),
            f.outcome("app_1", Outcome.REPLIED, "2026-06-11T14:03:11Z"),
            f.outcome("app_2", Outcome.REJECTED, "2026-06-11T14:04:11Z"),
        )

        val receiver = EnvelopeReceiver(keyId, key.publicPoint)
        queued.forEach { wire ->
            assertTrue(receiver.receiveWire(wire) { kP2e }.accepted, "queued envelope rejected")
        }
        assertEquals(3L, receiver.highestAccepted(Direction.PHONE_TO_ENGINE))
    }

    // ---------------------------------------------------------------- vocabulary

    @Test
    fun `the phone can only send the five outcomes it is allowed to set`() {
        assertEquals(
            setOf("sent", "replied", "interview", "offer", "rejected"),
            Outcome.entries.map { it.wire }.toSet(),
        )
        // no_reply is a desktop-set observation in the store's superset (§4.3.1). The phone
        // must be able to RENDER it without being able to SEND it.
        assertNull(Outcome.fromWire("no_reply"))
        assertTrue("no_reply" in Outcome.ENGINE_ONLY)
    }

    @Test
    fun `no outbound kind can cause an email to be sent`() {
        // §8.1 stated as a test: the vocabulary is the whole vocabulary.
        assertTrue(
            PayloadKind.entries.none {
                it.wire.contains("send") || it.wire.contains("submit") || it.wire.contains("mail")
            },
        )
        val (f, _) = factory(signer = TestKey().signer)
        assertFailsWith<IllegalArgumentException> { f.build("send_email", "{}", "2026-06-11T14:02:11Z") }
        for (reserved in PayloadKind.RESERVED_FOR_L2) {
            assertFailsWith<IllegalArgumentException>("$reserved must not be buildable") {
                f.build(reserved, "{}", "2026-06-11T14:02:11Z")
            }
        }
    }

    // ---------------------------------------------------------------- outcome body escaping

    /**
     * F-67-1. `outcome()` built its body by raw interpolation while its `entitlement()` sibling
     * routed every field through `jsonString()`. The three tests below are the escaping guard the
     * outcome path never had — the mirror of the quote/backslash fixture at line 215, which exists
     * for exactly this reason on the other path.
     *
     * Severity is defense in depth, not a live defect, and the tests do not claim otherwise:
     * `app_id` is an engine-internal ULID inside an AEAD-sealed snapshot, so no untrusted §8.6 job
     * text reaches this field today. The convention is not an enforced invariant, which is what
     * makes it worth a test rather than a comment.
     */
    @Test
    fun `an app_id containing a quote and a backslash survives the round trip`() {
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val appId = """app_1"quote\backslash"""

        val wire = f.outcome(appId, Outcome.SENT, at = "2026-06-11T14:02:11Z")
        val result = EnvelopeReceiver(keyId, key.publicPoint).receiveWire(wire) { kP2e }

        // Unescaped, the body is malformed JSON and the receiver refuses the whole envelope —
        // a mark the user made, sent, accepted by the relay, and silently never applied.
        assertTrue(result.accepted, "outcome envelope rejected: ${result.error?.wire}")
        val body = json.parseToJsonElement(result.plaintext!!.toString(Charsets.UTF_8))
            .jsonObject["body"]!!.jsonObject
        assertEquals(appId, body["app_id"]!!.jsonPrimitive.content)
        assertEquals("sent", body["outcome"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an at timestamp containing a quote survives the round trip`() {
        // `timestamp` is passed explicitly and left clean: `at` defaults into it, and the header
        // is a different interpolation surface with a different bound (see F-69-1). This test is
        // about the body only.
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val at = """2026-06-11T14:02:11Z","injected":"field"""

        val wire = f.outcome("app_1", Outcome.SENT, at = at, timestamp = "2026-06-11T14:02:11Z")
        val result = EnvelopeReceiver(keyId, key.publicPoint).receiveWire(wire) { kP2e }

        assertTrue(result.accepted, "outcome envelope rejected: ${result.error?.wire}")
        val body = json.parseToJsonElement(result.plaintext!!.toString(Charsets.UTF_8))
            .jsonObject["body"]!!.jsonObject
        assertEquals(at, body["at"]!!.jsonPrimitive.content)
        assertNull(body["injected"], "a crafted `at` must not be able to add a sibling field")
    }

    @Test
    fun `a crafted app_id cannot forge the outcome field`() {
        // The load-bearing half. Unescaped, this appId closes the app_id string and opens a
        // SECOND "outcome" key. Unlike the quote case above the result is still *valid* JSON, so
        // nothing rejects it: the envelope is accepted and applied with a body carrying two
        // outcomes. Which one survives is parser-dependent — kotlinx and the engine's
        // System.Text.Json need not agree — so the two implementations can record different
        // outcomes for one signed envelope. Injected through `at`, the body's LAST field, the
        // forged key lands after the real one, where last-wins makes it decisive.
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val appId = """x","outcome":"offer"""

        val wire = f.outcome(appId, Outcome.SENT, at = "2026-06-11T14:02:11Z")
        val result = EnvelopeReceiver(keyId, key.publicPoint).receiveWire(wire) { kP2e }
        assertTrue(result.accepted, "outcome envelope rejected: ${result.error?.wire}")

        val plaintext = result.plaintext!!.toString(Charsets.UTF_8)
        assertEquals(
            1,
            Regex("\"outcome\":").findAll(plaintext).count(),
            "the body carries a second `outcome` key: $plaintext",
        )
        val body = json.parseToJsonElement(plaintext).jsonObject["body"]!!.jsonObject
        assertEquals("sent", body["outcome"]!!.jsonPrimitive.content)
        assertEquals(appId, body["app_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an ordinary outcome body is byte-for-byte what it always was`() {
        // A guard against over-fixing, not a control: this one passes before the fix as well.
        // Escaping must be a no-op on the ids v1 actually carries — a "fix" that re-encoded or
        // double-escaped ordinary text would change bytes the engine already parses.
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)

        val wire = f.outcome("app_01H8XK", Outcome.INTERVIEW, at = "2026-06-11T14:02:11Z")
        val plaintext = EnvelopeReceiver(keyId, key.publicPoint)
            .receiveWire(wire) { kP2e }.plaintext!!.toString(Charsets.UTF_8)

        assertEquals(
            """{"kind":"outcome","body":{"app_id":"app_01H8XK","outcome":"interview",""" +
                """"at":"2026-06-11T14:02:11Z"}}""",
            plaintext,
        )
    }

    // ---------------------------------------------------------------- entitlement courier

    @Test
    fun `the entitlement courier forwards original_json byte-for-byte`() {
        // §4.3.2: the RSA signature covers these exact bytes, so re-serialising breaks it.
        // The record below contains quotes and a backslash precisely to catch sloppy escaping.
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val originalJson = """{"orderId":"GPA.1","packageName":"app.careerseeker.dashboard",""" +
            """"productId":"pro_unlock","purchaseState":0,"note":"quote\" and backslash\\ here"}"""
        val signature = "abc+/def=="

        val wire = f.entitlement(originalJson, signature, "2026-06-11T14:02:11Z")
        val result = EnvelopeReceiver(keyId, key.publicPoint).receiveWire(wire) { kP2e }
        assertTrue(result.accepted, "entitlement envelope rejected: ${result.error?.wire}")

        val body = json.parseToJsonElement(result.plaintext!!.toString(Charsets.UTF_8))
            .jsonObject["body"]!!.jsonObject
        assertEquals(originalJson, body["original_json"]!!.jsonPrimitive.content, "verbatim or the signature dies")
        assertEquals(signature, body["signature"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the sealed envelope really is opaque to anyone without the key`() {
        // The relay sees this wire text. It must learn nothing but metadata (§1).
        val key = TestKey()
        val (f, _) = factory(signer = key.signer)
        val wire = f.outcome("app_secret_id", Outcome.OFFER, "2026-06-11T14:02:11Z")

        assertTrue(!wire.contains("app_secret_id"))
        assertTrue(!wire.contains("offer"))
        assertTrue(!wire.contains(Base64Url.encode(kP2e)))

        // And it decrypts to the truth under the right key, so the check above is not vacuous.
        val env = json.parseToJsonElement(wire).jsonObject
        val opened = SyncCrypto.open(
            kP2e,
            Base64Url.decodeOrNull(env["nonce"]!!.jsonPrimitive.content)!!,
            EnvelopeHeader(
                1, pairing, Direction.PHONE_TO_ENGINE,
                env["seq"]!!.jsonPrimitive.content.toLong(),
                env["ts"]!!.jsonPrimitive.content, keyId,
            ).aad(),
            Base64Url.decodeOrNull(env["ciphertext"]!!.jsonPrimitive.content)!!,
        ).toString(Charsets.UTF_8)
        assertTrue(opened.contains("app_secret_id"))
    }
}
