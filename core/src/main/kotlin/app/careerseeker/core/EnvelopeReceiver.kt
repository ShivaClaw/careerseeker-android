package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto

/** An envelope as parsed off the wire, fields still in their string forms. */
data class ReceivedEnvelope(
    val v: Int,
    val pairing: String,
    val dir: String,
    val seq: Long,
    val ts: String,
    val keyId: String,
    val nonce: String,
    val ciphertext: String,
    val sig: String?,
)

data class ReceiveResult(val error: ErrorCode?, val kind: String?, val plaintext: ByteArray?) {
    val accepted: Boolean get() = error == null
}

/**
 * The v1 receiving state machine (Sync-Protocol.md §3–§5.4), the Kotlin twin of the
 * engine's `EnvelopeReceiver`. The check ORDER is part of the protocol, not an
 * implementation detail: rejecting for the wrong reason usually means a check fired
 * earlier than intended and the real one is untested.
 *
 * Order: version → key_id → structural decode → size → signature placement → replay →
 * decrypt → kind → signature requirement/verification. The sequence number is committed
 * only after every check passes, so garbage cannot burn sequence numbers.
 */
class EnvelopeReceiver(
    private val activeKeyId: String,
    private val deviceSigPub: ByteArray? = null,
) {
    private val seq = SequenceTracker()

    fun highestAccepted(dir: Direction): Long = seq.highestAccepted(dir)

    fun receive(env: ReceivedEnvelope, keyForDir: (String) -> ByteArray): ReceiveResult {
        if (env.v != Protocol.VERSION) return reject(ErrorCode.VERSION_UNSUPPORTED)

        // Revocation is explicit, not a side effect of cryptography: a superseded pairing
        // whose derived key still decrypts is exactly what the tag cannot catch.
        if (env.keyId != activeKeyId) return reject(ErrorCode.KEY_UNKNOWN)

        val nonce = Base64Url.decodeOrNull(env.nonce)
            ?: return reject(ErrorCode.DECRYPT_FAILED)
        if (nonce.size != Protocol.NONCE_BYTES) return reject(ErrorCode.DECRYPT_FAILED)
        val ciphertext = Base64Url.decodeOrNull(env.ciphertext)
            ?: return reject(ErrorCode.DECRYPT_FAILED)
        if (ciphertext.size > Protocol.MAX_ENVELOPE_BYTES) return reject(ErrorCode.TOO_LARGE)

        // The engine holds no signing key, so sig on an e2p envelope is always wrong.
        if (env.dir == "e2p" && env.sig != null) return reject(ErrorCode.BAD_SIGNATURE)

        val direction = Direction.fromWire(env.dir) ?: return reject(ErrorCode.DECRYPT_FAILED)
        if (env.seq <= seq.highestAccepted(direction)) return reject(ErrorCode.REPLAY_REJECTED)

        val header = EnvelopeHeader(env.v, env.pairing, direction, env.seq, env.ts, env.keyId)
        val aad = header.aad()

        val plaintext = try {
            SyncCrypto.open(keyForDir(env.dir), nonce, aad, ciphertext)
        } catch (_: Exception) {
            return reject(ErrorCode.DECRYPT_FAILED)
        }

        val kind = kindOf(plaintext) ?: return reject(ErrorCode.UNKNOWN_KIND)

        // Reserved-before-signature: a reserved L2 kind is rejected as unknown even if
        // beautifully signed — the phone does not get engine control in v1.
        if (PayloadKind.fromWire(kind) == null) return reject(ErrorCode.UNKNOWN_KIND)

        if (env.dir == "p2e" && Protocol.STATE_CHANGING_KINDS.contains(kind)) {
            val sig = env.sig ?: return reject(ErrorCode.BAD_SIGNATURE)
            val pub = deviceSigPub ?: return reject(ErrorCode.BAD_SIGNATURE)
            val sigBytes = Base64Url.decodeOrNull(sig) ?: return reject(ErrorCode.BAD_SIGNATURE)
            val input = PairingDerivation.signatureInput(aad, env.nonce, ciphertext)
            if (!SyncCrypto.verifySignature(pub, input, sigBytes)) return reject(ErrorCode.BAD_SIGNATURE)
        }

        seq.accept(direction, env.seq) // committed only after every check passed
        return ReceiveResult(null, kind, plaintext)
    }

    private fun reject(e: ErrorCode) = ReceiveResult(e, null, null)

    /** Minimal top-level "kind" extraction — enough to route, without a JSON dependency. */
    private fun kindOf(plaintext: ByteArray): String? {
        val text = plaintext.toString(Charsets.UTF_8)
        val marker = "\"kind\""
        val i = text.indexOf(marker)
        if (i < 0) return null
        var j = text.indexOf(':', i + marker.length)
        if (j < 0) return null
        j++
        while (j < text.length && text[j].isWhitespace()) j++
        if (j >= text.length || text[j] != '"') return null
        val end = text.indexOf('"', j + 1)
        if (end < 0) return null
        return text.substring(j + 1, end)
    }
}
