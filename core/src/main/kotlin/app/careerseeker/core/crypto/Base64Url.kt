package app.careerseeker.core.crypto

import java.util.Base64

/**
 * Strict, unpadded base64url. Rejects padded and standard-alphabet input on purpose:
 * accepting both spellings would mean the shared vectors no longer pin one encoding, and
 * the Kotlin and C# sides could disagree about what an envelope even says.
 */
object Base64Url {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decodeOrNull(s: String?): ByteArray? {
        if (s.isNullOrEmpty()) return null
        if (s.contains('=') || s.contains('+') || s.contains('/')) return null
        return try {
            decoder.decode(s)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
