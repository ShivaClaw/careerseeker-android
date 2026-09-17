package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The QR the desktop renders (Sync-Protocol.md §5.2), after parsing and validation. */
data class PairingInvite(
    val v: Int,
    val suite: String,
    val pairing: String,
    val enginePub: ByteArray,
    val relay: String,
    val secret: ByteArray,
)

/** Why an invite could not be used. Each maps to a distinct thing to tell the user. */
enum class PairingError(val wire: String) {
    MALFORMED("malformed"),

    /**
     * The QR names a suite this build does not implement. §5.2: a phone that does not
     * recognise `suite` MUST refuse to pair and show the mismatch — **never** silently fall
     * back. A downgrade the user cannot see is worse than a pairing that fails loudly.
     */
    SUITE_UNSUPPORTED("suite_unsupported"),

    VERSION_UNSUPPORTED("version_unsupported"),

    /** The relay URL was not TLS, so the invite cannot be honoured (§2). */
    INSECURE_RELAY("insecure_relay"),
}

sealed interface PairingParse {
    data class Ok(val invite: PairingInvite) : PairingParse
    data class Rejected(val error: PairingError) : PairingParse
}

/**
 * The phone's half of pairing (§5.2.2), as pure logic.
 *
 * Deliberately holds **no** Android types: the Keystore-backed device key and the camera live
 * in `:app`, while everything that has to agree with the engine byte-for-byte lives here and is
 * proven against the shared `pairing-basic` / `pairing-mitm-keyswap` vectors.
 *
 * The device signing key is supplied as a *public* point plus a signing function, so this class
 * never sees private key material — the Android Keystore key is non-exportable by construction
 * and this API does not tempt anyone to change that.
 */
object PairingSession {

    private val json = Json { ignoreUnknownKeys = true }

    /** Suites this build implements. The hybrid PQ suite is reserved, not shipped. */
    val SUPPORTED_SUITES = setOf(Protocol.SUITE)

    fun parseInvite(qrPayload: String): PairingParse {
        val root = try {
            json.parseToJsonElement(qrPayload) as? JsonObject ?: return reject(PairingError.MALFORMED)
        } catch (_: IllegalArgumentException) {
            return reject(PairingError.MALFORMED)
        }

        fun str(key: String): String? = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        val v = (root["v"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
            ?: return reject(PairingError.MALFORMED)
        if (v != Protocol.VERSION) return reject(PairingError.VERSION_UNSUPPORTED)

        val suite = str("suite") ?: return reject(PairingError.MALFORMED)
        // Checked before anything is derived: refusing loudly is the point.
        if (suite !in SUPPORTED_SUITES) return reject(PairingError.SUITE_UNSUPPORTED)

        val pairing = str("pairing") ?: return reject(PairingError.MALFORMED)
        if (!isValidPairingId(pairing)) return reject(PairingError.MALFORMED)

        val enginePub = str("engine_pub")?.let { Base64Url.decodeOrNull(it) }
            ?: return reject(PairingError.MALFORMED)
        if (enginePub.size != 65 || enginePub[0].toInt() != 0x04) return reject(PairingError.MALFORMED)

        val secret = str("secret")?.let { Base64Url.decodeOrNull(it) }
            ?: return reject(PairingError.MALFORMED)
        if (secret.size != 32) return reject(PairingError.MALFORMED)

        val relay = str("relay") ?: return reject(PairingError.MALFORMED)
        if (!relay.startsWith("https://")) return reject(PairingError.INSECURE_RELAY)

        return PairingParse.Ok(PairingInvite(v, suite, pairing, enginePub, relay, secret))
    }

    /** Everything derived from one invite plus this device's ephemeral ECDH key. */
    data class Completion(
        val keys: PairingKeys,
        /** Body for `POST /v1/{pairing}/pair`, already sealed. */
        val bodyJson: String,
        /** The six digits the user compares against the desktop screen. */
        val confirmCode: String,
        /** Bearer for the bootstrap phase, before the final token takes over (§5.2.3). */
        val provisionalRelayToken: String,
    )

    /**
     * Build the pairing completion.
     *
     * `phone_pub` travels in clear because the engine cannot derive `k_p2e` without it, but it
     * is bound into the AAD — so a relay that substitutes its own key breaks the tag rather
     * than hijacking the session. The **device signing key travels only inside the
     * ciphertext**: the relay must never learn which signing key belongs to a pairing.
     */
    fun buildCompletion(
        invite: PairingInvite,
        phonePrivateScalar: ByteArray,
        phonePublicUncompressed: ByteArray,
        deviceSigPublicUncompressed: ByteArray,
        nonce: ByteArray,
        timestamp: String,
    ): Completion {
        require(nonce.size == Protocol.NONCE_BYTES) { "nonce must be 12 bytes" }

        val sharedSecret = SyncCrypto.ecdhSharedSecret(phonePrivateScalar, invite.enginePub)
        val keys = PairingDerivation.derive(listOf(sharedSecret), invite.secret)

        val phonePubB64u = Base64Url.encode(phonePublicUncompressed)
        val aad = PairingDerivation.completionAad(invite.pairing, invite.suite, phonePubB64u)
        val payload = """{"device_sig_pub":"${Base64Url.encode(deviceSigPublicUncompressed)}","ts":"$timestamp"}"""

        val ciphertext = SyncCrypto.seal(keys.keyPhoneToEngine, nonce, aad, payload.toByteArray(Charsets.UTF_8))

        val body = """{"suite":"${invite.suite}","phone_pub":"$phonePubB64u",""" +
            """"nonce":"${Base64Url.encode(nonce)}","ciphertext":"${Base64Url.encode(ciphertext)}"}"""

        return Completion(
            keys = keys,
            bodyJson = body,
            confirmCode = keys.confirmCode,
            provisionalRelayToken = PairingDerivation.provisionalRelayToken(invite.secret),
        )
    }

    private fun reject(e: PairingError) = PairingParse.Rejected(e)
}
