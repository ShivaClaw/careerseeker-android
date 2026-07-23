package app.careerseeker.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869), hand-rolled over the JDK's HmacSHA256.
 *
 * ~20 lines, and it removes the only reason `:core` would need a third-party crypto
 * library: the JCA gives us HMAC, AES-GCM, ECDH, and ECDSA, but not HKDF as a one-call
 * primitive. Writing it here keeps `:core` dependency-free and Android-free (see
 * checkCoreIsAndroidFree), which is what makes it the iOS port's starting point.
 *
 * The engine derives the same values via .NET's `HKDF.DeriveKey`; both are checked
 * against the same Node-generated pairing vectors, so any disagreement fails CI.
 */
object Hkdf {
    private const val HASH_LEN = 32

    fun deriveKey(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "length out of range: $length" }
        return expand(extract(salt, ikm), info, length)
    }

    /** HKDF-Extract: PRK = HMAC(salt, ikm). An all-zero salt is used when salt is empty. */
    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val key = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return hmac(key, ikm)
    }

    /** HKDF-Expand: T(n) = HMAC(PRK, T(n-1) || info || n), concatenated to `length`. */
    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            val mac = macInstance(prk)
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val take = minOf(t.size, length - pos)
            t.copyInto(output, pos, 0, take)
            pos += take
            counter++
        }
        return output
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = macInstance(key).doFinal(data)

    private fun macInstance(key: ByteArray): Mac =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
}
