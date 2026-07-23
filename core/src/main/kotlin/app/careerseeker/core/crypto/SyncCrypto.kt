package app.careerseeker.core.crypto

import app.careerseeker.core.Protocol
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec

/**
 * The Sync Protocol v1 codec for `:core`, built entirely on the JVM's own JCA:
 * AES-256-GCM, ECDH P-256, ECDSA P-256, plus [Hkdf]. No Tink, no BouncyCastle.
 *
 * Tink was in the original spec (§4.2) and was dropped here deliberately: the one thing it
 * gave us that the JCA does not is HKDF, and [Hkdf] is twenty lines. Everything else —
 * AEAD, key agreement, signatures — is in every Android runtime from API 26. Dropping the
 * dependency keeps `:core` Android-free and shrinks the app's attack surface by one
 * third-party crypto library. If the post-quantum hybrid (ML-KEM) lands, *that* is when a
 * vetted library re-enters, and it enters `:app`, not `:core`.
 *
 * Correctness is not argued, it is proven: ProtocolVectorsTest runs every value in this
 * file against the Node-generated shared vectors that the C# engine also consumes.
 */
object SyncCrypto {

    // ---------------------------------------------------------------- AES-256-GCM

    fun seal(key: ByteArray, nonce: ByteArray, aad: String, plaintext: ByteArray): ByteArray {
        val cipher = gcm(Cipher.ENCRYPT_MODE, key, nonce, aad)
        return cipher.doFinal(plaintext)
    }

    /** Throws [javax.crypto.AEADBadTagException] (an IllegalStateException) on auth failure. */
    fun open(key: ByteArray, nonce: ByteArray, aad: String, sealedBytes: ByteArray): ByteArray {
        val cipher = gcm(Cipher.DECRYPT_MODE, key, nonce, aad)
        return cipher.doFinal(sealedBytes)
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: String): Cipher {
        require(key.size == Protocol.KEY_BYTES) { "key must be 32 bytes" }
        require(nonce.size == Protocol.NONCE_BYTES) { "nonce must be 12 bytes" }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(Protocol.TAG_BYTES * 8, nonce))
            updateAAD(aad.toByteArray(Charsets.US_ASCII))
        }
    }

    // ---------------------------------------------------------------- ECDH P-256

    /** Raw ECDH shared secret: the 32-byte X coordinate, per Sync-Protocol.md §5.2. */
    fun ecdhSharedSecret(ownPrivateScalar: ByteArray, peerUncompressed: ByteArray): ByteArray {
        val priv = privateKeyFromScalar(ownPrivateScalar)
        val pub = publicKeyFromUncompressed(peerUncompressed)
        val ka = KeyAgreement.getInstance("ECDH").apply { init(priv); doPhase(pub, true) }
        // JCA returns the raw X coordinate for ECDH; left-pad to 32 bytes in case of a
        // leading-zero secret (rare, but a fixed-width secret is what HKDF expects).
        return leftPad(ka.generateSecret(), 32)
    }

    // ---------------------------------------------------------------- ECDSA P-256

    /**
     * Verify an envelope device signature (§5.4): ECDSA P-256 / SHA-256 over the ASCII
     * [sigInput], signature in raw 64-byte r||s form. The JCA speaks DER, so r||s is
     * transcoded to a DER SEQUENCE before verification.
     */
    fun verifySignature(deviceSigPubUncompressed: ByteArray, sigInput: String, rawSignature: ByteArray): Boolean {
        if (rawSignature.size != 64) return false
        if (deviceSigPubUncompressed.size != 65 || deviceSigPubUncompressed[0].toInt() != 0x04) return false
        return try {
            val pub = publicKeyFromUncompressed(deviceSigPubUncompressed)
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(pub)
                update(sigInput.toByteArray(Charsets.US_ASCII))
                verify(rawToDer(rawSignature))
            }
        } catch (_: Exception) {
            false // malformed key/point/signature — not a valid signature either way
        }
    }

    // ---------------------------------------------------------------- key import helpers

    private val p256Params: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
    }

    private fun publicKeyFromUncompressed(uncompressed: ByteArray): ECPublicKey {
        require(uncompressed.size == 65 && uncompressed[0].toInt() == 0x04) { "expected 65-byte uncompressed P-256 point" }
        val x = BigInteger(1, uncompressed.copyOfRange(1, 33))
        val y = BigInteger(1, uncompressed.copyOfRange(33, 65))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), p256Params)) as ECPublicKey
    }

    private fun privateKeyFromScalar(scalar: ByteArray): ECPrivateKey {
        val d = BigInteger(1, scalar)
        val spec = java.security.spec.ECPrivateKeySpec(d, p256Params)
        return KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
    }

    // ---------------------------------------------------------------- encoding utils

    private fun leftPad(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size == size) return bytes
        if (bytes.size > size) return bytes.copyOfRange(bytes.size - size, bytes.size)
        return ByteArray(size - bytes.size) + bytes
    }

    /** Raw r||s (64 bytes) -> DER SEQUENCE(INTEGER r, INTEGER s), for the JCA verifier. */
    private fun rawToDer(raw: ByteArray): ByteArray {
        val r = toDerInteger(raw.copyOfRange(0, 32))
        val s = toDerInteger(raw.copyOfRange(32, 64))
        val body = r + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun toDerInteger(value: ByteArray): ByteArray {
        var v = value
        var i = 0
        while (i < v.size - 1 && v[i].toInt() == 0) i++ // strip leading zeros
        v = v.copyOfRange(i, v.size)
        if (v[0].toInt() and 0x80 != 0) v = byteArrayOf(0) + v // keep it positive
        return byteArrayOf(0x02, v.size.toByte()) + v
    }
}
