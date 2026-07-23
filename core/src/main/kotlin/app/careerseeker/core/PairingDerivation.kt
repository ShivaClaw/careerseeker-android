package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.Hkdf
import java.security.MessageDigest

/** Everything §5.2 derives from one pairing exchange. */
data class PairingKeys(
    val keyEngineToPhone: ByteArray,
    val keyPhoneToEngine: ByteArray,
    val relayToken: String,
    val confirmCode: String,
)

/**
 * Pairing key agreement for suite `p256-hkdf-sha256` (Sync-Protocol.md §5.2).
 *
 * `ikm` always goes through concatenation of the suite's shared secrets, even while there
 * is exactly one — the post-quantum hybrid appends the ML-KEM secret to the same
 * concatenation, so migrating is a suite bump, not a break. Do not collapse the concat.
 *
 * Mirrors the engine's `PairingCrypto` (C#), and both are proven against the same pairing
 * vectors — a byte for byte agreement, or CI fails.
 */
object PairingDerivation {

    fun derive(sharedSecrets: List<ByteArray>, oneTimeSecret: ByteArray): PairingKeys {
        require(sharedSecrets.isNotEmpty()) { "at least one shared secret" }
        val ikm = concat(sharedSecrets)

        val confirmBytes = Hkdf.deriveKey(ikm, oneTimeSecret, Protocol.INFO_CONFIRM.toByteArray(Charsets.US_ASCII), 4)
        val confirmValue = ((confirmBytes[0].toLong() and 0xFF) shl 24) or
            ((confirmBytes[1].toLong() and 0xFF) shl 16) or
            ((confirmBytes[2].toLong() and 0xFF) shl 8) or
            (confirmBytes[3].toLong() and 0xFF)
        val confirm = (confirmValue % 1_000_000L).toString().padStart(6, '0')

        return PairingKeys(
            keyEngineToPhone = Hkdf.deriveKey(ikm, oneTimeSecret, Protocol.INFO_ENGINE_TO_PHONE.toByteArray(Charsets.US_ASCII), Protocol.KEY_BYTES),
            keyPhoneToEngine = Hkdf.deriveKey(ikm, oneTimeSecret, Protocol.INFO_PHONE_TO_ENGINE.toByteArray(Charsets.US_ASCII), Protocol.KEY_BYTES),
            relayToken = Base64Url.encode(Hkdf.deriveKey(ikm, oneTimeSecret, Protocol.INFO_RELAY_TOKEN.toByteArray(Charsets.US_ASCII), Protocol.KEY_BYTES)),
            confirmCode = confirm,
        )
    }

    /** Provisional relay token (§5.2.1): keyed on the one-time secret alone. */
    fun provisionalRelayToken(oneTimeSecret: ByteArray): String =
        Base64Url.encode(
            Hkdf.deriveKey(
                ikm = oneTimeSecret,
                salt = Protocol.BOOTSTRAP_SALT.toByteArray(Charsets.US_ASCII),
                info = Protocol.INFO_RELAY_TOKEN.toByteArray(Charsets.US_ASCII),
                length = Protocol.KEY_BYTES,
            ),
        )

    /** AAD for the pairing completion (§5.2.2). Binds phone_pub so a relay cannot swap keys. */
    fun completionAad(pairing: String, suite: String, phonePubB64u: String): String =
        "${Protocol.PAIR_AAD_PREFIX}|$pairing|$suite|$phonePubB64u"

    /** Signing input for the envelope device signature (§5.4). Exact wire artifacts only. */
    fun signatureInput(aad: String, nonceB64u: String, ciphertext: ByteArray): String {
        val hex = MessageDigest.getInstance("SHA-256").digest(ciphertext)
            .joinToString("") { "%02x".format(it) }
        return "${Protocol.COMMAND_SIG_PREFIX}|$aad|$nonceB64u|$hex"
    }

    private fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (p in parts) { p.copyInto(out, offset); offset += p.size }
        return out
    }
}
