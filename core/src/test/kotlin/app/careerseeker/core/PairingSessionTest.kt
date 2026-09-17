package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone's half of §5.2.2, driven by the shared `pairing-basic` vector so the completion
 * this code builds is the one the engine is proven to open.
 */
class PairingSessionTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): JsonObject = json.parseToJsonElement(
        File(
            File(requireNotNull(javaClass.classLoader.getResource("sync-vectors/v1/index.json")).toURI()).parentFile,
            "$name.json",
        ).readText(),
    ).jsonObject

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    /** A QR payload built from the pairing vector's own values. */
    private fun inviteJson(
        v: Int = 1,
        suite: String = Protocol.SUITE,
        relay: String = "https://relay.careerseeker.app",
    ): String {
        val vec = load("pairing-basic")
        val pairing = load("index").str("pairing_id")
        return """{"v":$v,"suite":"$suite","pairing":"$pairing",""" +
            """"engine_pub":"${vec["engine"]!!.jsonObject.str("pub_b64u")}",""" +
            """"relay":"$relay","secret":"${vec.str("secret_b64u")}"}"""
    }

    // ---------------------------------------------------------------- invite parsing

    @Test
    fun `a well-formed invite parses into its fields`() {
        val parsed = PairingSession.parseInvite(inviteJson())
        assertTrue(parsed is PairingParse.Ok, "got $parsed")
        val invite = (parsed as PairingParse.Ok).invite
        assertEquals(Protocol.SUITE, invite.suite)
        assertEquals(65, invite.enginePub.size)
        assertEquals(32, invite.secret.size)
    }

    @Test
    fun `an unrecognised suite refuses to pair instead of falling back`() {
        // §5.2 is explicit: never silently fall back. A downgrade the user cannot see is worse
        // than a pairing that fails loudly — and the reserved hybrid suite is the realistic
        // way this happens, when a newer desktop meets an older phone.
        val hybrid = PairingSession.parseInvite(inviteJson(suite = Protocol.SUITE_HYBRID_RESERVED))
        assertEquals(PairingError.SUITE_UNSUPPORTED, (hybrid as PairingParse.Rejected).error)

        val nonsense = PairingSession.parseInvite(inviteJson(suite = "rot13"))
        assertEquals(PairingError.SUITE_UNSUPPORTED, (nonsense as PairingParse.Rejected).error)

        assertTrue(Protocol.SUITE_HYBRID_RESERVED !in PairingSession.SUPPORTED_SUITES)
    }

    @Test
    fun `a non-v1 invite is rejected as a version mismatch, not as malformed`() {
        val parsed = PairingSession.parseInvite(inviteJson(v = 2))
        assertEquals(PairingError.VERSION_UNSUPPORTED, (parsed as PairingParse.Rejected).error)
    }

    @Test
    fun `a cleartext relay in the invite is refused`() {
        val parsed = PairingSession.parseInvite(inviteJson(relay = "http://relay.careerseeker.app"))
        assertEquals(PairingError.INSECURE_RELAY, (parsed as PairingParse.Rejected).error)
    }

    @Test
    fun `structurally broken invites are rejected without throwing`() {
        val broken = listOf(
            "", "{", "[]", "null",
            inviteJson().replace(load("index").str("pairing_id"), "not-a-pairing"),
            inviteJson().replace("\"engine_pub\"", "\"engine_public\""),
            // A truncated point is not a P-256 key; catching it here beats a confusing
            // decryption failure three steps later.
            inviteJson().replace(
                load("pairing-basic")["engine"]!!.jsonObject.str("pub_b64u"), "BKxF",
            ),
        )
        for (payload in broken) {
            val parsed = PairingSession.parseInvite(payload)
            assertTrue(parsed is PairingParse.Rejected, "should reject <${payload.take(40)}>")
        }
    }

    // ---------------------------------------------------------------- completion

    @Test
    fun `the completion this phone builds is the one the engine opens`() {
        // End-to-end against the vector: build the completion with the vector's phone key,
        // then open it the way the engine does (deriving from phone_pub) and confirm the
        // device signing key is inside.
        val vec = load("pairing-basic")
        val invite = (PairingSession.parseInvite(inviteJson()) as PairingParse.Ok).invite

        val phoneD = hex(vec["phone"]!!.jsonObject.str("d_hex"))
        val phonePub = Base64Url.decodeOrNull(vec["phone"]!!.jsonObject.str("pub_b64u"))!!
        val deviceSigPub = Base64Url.decodeOrNull(vec["device_sig"]!!.jsonObject.str("pub_b64u"))!!

        val completion = PairingSession.buildCompletion(
            invite = invite,
            phonePrivateScalar = phoneD,
            phonePublicUncompressed = phonePub,
            deviceSigPublicUncompressed = deviceSigPub,
            nonce = ByteArray(Protocol.NONCE_BYTES) { it.toByte() },
            timestamp = "2026-06-11T14:02:11Z",
        )

        // The derived values must equal the vector's, or this phone and that engine disagree.
        val expected = vec["expected"]!!.jsonObject
        assertEquals(expected.str("k_p2e_hex").lowercase(), completion.keys.keyPhoneToEngine.toHex())
        assertEquals(expected.str("confirm"), completion.confirmCode)
        assertEquals(expected.str("provisional_token_b64u"), completion.provisionalRelayToken)
        assertEquals(expected.str("relay_token_b64u"), completion.keys.relayToken)

        // Now play the engine: derive from the phone_pub on the wire and open the ciphertext.
        val body = json.parseToJsonElement(completion.bodyJson).jsonObject
        val engineD = hex(vec["engine"]!!.jsonObject.str("d_hex"))
        val engineSide = PairingDerivation.derive(
            listOf(SyncCrypto.ecdhSharedSecret(engineD, Base64Url.decodeOrNull(body.str("phone_pub"))!!)),
            invite.secret,
        )
        val opened = SyncCrypto.open(
            engineSide.keyPhoneToEngine,
            Base64Url.decodeOrNull(body.str("nonce"))!!,
            PairingDerivation.completionAad(invite.pairing, invite.suite, body.str("phone_pub")),
            Base64Url.decodeOrNull(body.str("ciphertext"))!!,
        ).toString(Charsets.UTF_8)

        assertTrue(opened.contains(Base64Url.encode(deviceSigPub)), "device signing key must be inside")
        assertEquals(expected.str("confirm"), engineSide.confirmCode, "both screens show the same 6 digits")
    }

    @Test
    fun `the device signing key never appears outside the ciphertext`() {
        // §5.2.2: the relay must never learn which signing key belongs to a pairing. The
        // relay sees exactly the body this builds, so the check is on the body itself.
        val vec = load("pairing-basic")
        val invite = (PairingSession.parseInvite(inviteJson()) as PairingParse.Ok).invite
        val deviceSigPub = Base64Url.decodeOrNull(vec["device_sig"]!!.jsonObject.str("pub_b64u"))!!

        val completion = PairingSession.buildCompletion(
            invite = invite,
            phonePrivateScalar = hex(vec["phone"]!!.jsonObject.str("d_hex")),
            phonePublicUncompressed = Base64Url.decodeOrNull(vec["phone"]!!.jsonObject.str("pub_b64u"))!!,
            deviceSigPublicUncompressed = deviceSigPub,
            nonce = ByteArray(Protocol.NONCE_BYTES) { it.toByte() },
            timestamp = "2026-06-11T14:02:11Z",
        )

        val body = json.parseToJsonElement(completion.bodyJson).jsonObject
        val encoded = Base64Url.encode(deviceSigPub)
        assertTrue(!body.str("phone_pub").contains(encoded))
        assertTrue(!completion.bodyJson.replace(body.str("ciphertext"), "").contains(encoded))
        // And no secret material rides along either.
        assertTrue(!completion.bodyJson.contains(Base64Url.encode(invite.secret)))
    }

    @Test
    fun `a swapped phone_pub breaks the handshake rather than hijacking it`() {
        // The MITM the AAD binding exists to stop: a relay substituting its own key changes
        // both the derived key AND the AAD, so decryption fails either way.
        val vec = load("pairing-basic")
        val invite = (PairingSession.parseInvite(inviteJson()) as PairingParse.Ok).invite
        val completion = PairingSession.buildCompletion(
            invite = invite,
            phonePrivateScalar = hex(vec["phone"]!!.jsonObject.str("d_hex")),
            phonePublicUncompressed = Base64Url.decodeOrNull(vec["phone"]!!.jsonObject.str("pub_b64u"))!!,
            deviceSigPublicUncompressed = Base64Url.decodeOrNull(vec["device_sig"]!!.jsonObject.str("pub_b64u"))!!,
            nonce = ByteArray(Protocol.NONCE_BYTES) { it.toByte() },
            timestamp = "2026-06-11T14:02:11Z",
        )
        val body = json.parseToJsonElement(completion.bodyJson).jsonObject

        val attackerPub = Base64Url.decodeOrNull(
            load("pairing-mitm-keyswap")["phone"]!!.jsonObject.str("pub_b64u"),
        )!!
        val engineD = hex(vec["engine"]!!.jsonObject.str("d_hex"))
        val hijacked = PairingDerivation.derive(
            listOf(SyncCrypto.ecdhSharedSecret(engineD, attackerPub)), invite.secret,
        )

        var failed = false
        try {
            SyncCrypto.open(
                hijacked.keyPhoneToEngine,
                Base64Url.decodeOrNull(body.str("nonce"))!!,
                PairingDerivation.completionAad(
                    invite.pairing, invite.suite, Base64Url.encode(attackerPub),
                ),
                Base64Url.decodeOrNull(body.str("ciphertext"))!!,
            )
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed, "a substituted phone_pub must break the tag")
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
