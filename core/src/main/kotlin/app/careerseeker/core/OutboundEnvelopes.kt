package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto

/**
 * The five outcomes a **phone** may set (§4.3, p2e `outcome`).
 *
 * Deliberately not the same set the wire carries back in a snapshot: §4.3.1's application
 * summary uses the *store's superset*, which additionally has `no_reply` — a desktop-set
 * observation, not something a user taps. Modelling the phone-settable subset as its own type
 * is what stops a future screen offering "no reply" as a button the engine would reject.
 */
enum class Outcome(val wire: String) {
    SENT("sent"),
    REPLIED("replied"),
    INTERVIEW("interview"),
    OFFER("offer"),
    REJECTED("rejected"),
    ;

    companion object {
        fun fromWire(value: String): Outcome? = entries.firstOrNull { it.wire == value }

        /**
         * Values that may arrive from the engine but that the phone must never *send*.
         * Rendering one is fine; offering it as a control is not.
         */
        val ENGINE_ONLY = setOf("no_reply")
    }
}

/** Signs the §5.4 input with the device key. Implemented in `:app` by an Android Keystore key. */
fun interface DeviceSigner {
    /** ECDSA-P256-SHA256 over [input], returned as raw 64-byte `r||s` (not DER). */
    fun sign(input: String): ByteArray
}

/**
 * Builds phone→engine envelopes.
 *
 * Two rules this type exists to make unbreakable:
 *
 *  1. **A state-changing kind is signed, or it is not built.** `doc_edit`, `outcome`, and
 *     `entitlement` require the envelope `sig` (§5.4). Here that is not a caller's
 *     responsibility to remember — [build] refuses to produce an unsigned envelope for those
 *     kinds. The audit chain's ability to prove *which paired device* asked for a change
 *     depends on it.
 *  2. **The sequence number is owned, not passed in.** §6.1 requires the sender's counter to be
 *     monotonic and persisted across restarts. The factory takes a [SeqSource] rather than a
 *     number so a caller cannot reuse one — a reused p2e seq is silently dropped by the engine
 *     as a replay, which looks exactly like "the outcome didn't save".
 *
 * There is no payload kind here that can cause an email to be sent, and there cannot be: §8.1
 * is a protocol property, and [Protocol.STATE_CHANGING_KINDS] plus the v1 kind set is the whole
 * vocabulary.
 */
class OutboundEnvelopeFactory(
    private val pairing: String,
    private val keyId: String,
    private val keyPhoneToEngine: ByteArray,
    private val seqSource: SeqSource,
    private val signer: DeviceSigner?,
    private val nonces: NonceSource = NonceSource.secureRandom(),
) {
    /** Supplies the next p2e sequence number. Must persist across process restarts (§6.1). */
    fun interface SeqSource {
        fun next(): Long
    }

    /** 12 fresh CSPRNG bytes per envelope. Never counter-derived (§5.1). */
    fun interface NonceSource {
        fun next(): ByteArray

        companion object {
            fun secureRandom() = NonceSource {
                ByteArray(Protocol.NONCE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
            }
        }
    }

    class UnsignableEnvelope(kind: String) : IllegalStateException(
        "kind '$kind' changes engine state and requires a device signature (§5.4), " +
            "but no signer is configured — refusing to build an envelope the engine must reject",
    )

    /** Serialised envelope JSON, ready for `RelayClient.push`. */
    fun build(kind: String, bodyJson: String, timestamp: String): String {
        require(PayloadKind.fromWire(kind) != null) { "unknown payload kind '$kind'" }

        val stateChanging = Protocol.STATE_CHANGING_KINDS.contains(kind)
        if (stateChanging && signer == null) throw UnsignableEnvelope(kind)

        val seq = seqSource.next()
        val nonce = nonces.next()
        require(nonce.size == Protocol.NONCE_BYTES) { "nonce must be 12 bytes" }

        val header = EnvelopeHeader(Protocol.VERSION, pairing, Direction.PHONE_TO_ENGINE, seq, timestamp, keyId)
        val aad = header.aad()
        val plaintext = """{"kind":"$kind","body":$bodyJson}"""
        val ciphertext = SyncCrypto.seal(keyPhoneToEngine, nonce, aad, plaintext.toByteArray(Charsets.UTF_8))

        val nonceB64u = Base64Url.encode(nonce)
        val sig = if (stateChanging) {
            Base64Url.encode(signer!!.sign(PairingDerivation.signatureInput(aad, nonceB64u, ciphertext)))
        } else {
            null
        }

        return buildString {
            append("""{"v":${Protocol.VERSION},"pairing":"$pairing","dir":"p2e","seq":$seq,""")
            append(""""ts":"$timestamp","key_id":"$keyId","nonce":"$nonceB64u",""")
            append(""""ciphertext":"${Base64Url.encode(ciphertext)}"""")
            if (sig != null) append(""","sig":"$sig"""")
            append("}")
        }
    }

    /** `outcome` (§4.3): `{app_id, outcome, at}`. Always signed. */
    fun outcome(appId: String, outcome: Outcome, at: String, timestamp: String = at): String =
        build("outcome", """{"app_id":"$appId","outcome":"${outcome.wire}","at":"$at"}""", timestamp)

    /**
     * `entitlement` (§4.3.2): the phone is a **courier**. `original_json` is forwarded verbatim
     * because the RSA signature covers those exact bytes — it is embedded as a JSON string
     * without being re-parsed or re-serialised.
     */
    fun entitlement(originalJson: String, playSignature: String, timestamp: String): String =
        build(
            "entitlement",
            """{"original_json":${jsonString(originalJson)},"signature":${jsonString(playSignature)}}""",
            timestamp,
        )

    /**
     * `pull_request` (§4.3.4): ask the engine to re-publish the whole dashboard as a fresh
     * `snapshot`. Not signed — it changes no engine state (§5.4), so it needs no device key.
     *
     * This KDoc used to say "re-publish from a sequence point", copied from §4.3's old row.
     * §4.3.4 has since made `since_seq` **reserved**: a sender MUST set `0`, a receiver MUST
     * ignore it and answer with a full snapshot, and a receiver MUST NOT reject a non-zero value.
     * The parameter stays on the signature because the field is still on the wire; the only value
     * v1 sends is [PullPolicy.SINCE_SEQ_FULL_REPUBLISH]. See PQ-S4-1 in `docs/protocol-questions.md`.
     */
    fun pullRequest(sinceSeq: Long, timestamp: String): String =
        build("pull_request", """{"since_seq":$sinceSeq}""", timestamp)

    /** Minimal JSON string escaping — enough for the fields v1 carries, and exact. */
    private fun jsonString(raw: String): String = buildString {
        append('"')
        for (c in raw) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
