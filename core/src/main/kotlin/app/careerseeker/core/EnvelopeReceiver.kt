package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

    /**
     * Receive an envelope straight off the wire.
     *
     * This is the entry point transport code should use: it applies §3's strict parse —
     * including the unknown-top-level-field rejection — before the state machine sees
     * anything. Taking a pre-built [ReceivedEnvelope] instead lets a caller construct one
     * from JSON it parsed leniently, which is exactly the gap §3 warns about.
     */
    fun receiveWire(wire: String, keyForDir: (String) -> ByteArray): ReceiveResult {
        val parsed = EnvelopeJson.parse(wire)
        val envelope = parsed.envelope ?: return reject(parsed.error ?: ErrorCode.DECRYPT_FAILED)
        return receive(envelope, keyForDir)
    }

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

    /**
     * Top-level `kind` extraction, by actually parsing the JSON.
     *
     * This used to scan the decrypted text for the first `"kind"` substring to avoid a JSON
     * dependency. That is unsafe *here specifically*: the decrypted body is where untrusted
     * job and recruiter text lives (§8.6), so a carried string containing `"kind":"snapshot"`
     * ahead of the real field would have chosen the route. Untrusted text must not be able to
     * influence dispatch — parsing is the fix, not a longer scanner.
     *
     * Returns null for malformed JSON, a missing `kind`, or a non-string `kind`; the caller
     * maps that to `unknown_kind`, which matches the engine's `JsonDocument.Parse` behaviour
     * so both implementations classify a garbage body identically.
     */
    private fun kindOf(plaintext: ByteArray): String? = try {
        val root = Json.parseToJsonElement(plaintext.toString(Charsets.UTF_8)) as? JsonObject
        (root?.get("kind") as? JsonPrimitive)?.takeIf { it.isString }?.content
    } catch (_: IllegalArgumentException) {
        null
    }
}
