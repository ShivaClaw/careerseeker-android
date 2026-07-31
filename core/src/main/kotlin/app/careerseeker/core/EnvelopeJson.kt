package app.careerseeker.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses an envelope off the wire into a [ReceivedEnvelope], strictly.
 *
 * Sync-Protocol.md §3 ends with a requirement that is easy to skip because no test vector
 * exercises it:
 *
 * > Other unknown top-level fields MUST be rejected, not ignored. A permissive parser here is
 * > how a future version's field silently becomes an injection point.
 *
 * Nothing in `:core` enforced that — envelopes were assembled field-by-field by callers, so an
 * extra field was simply dropped. This closes it. The rule is cheap to honour and the failure
 * mode it prevents is the expensive kind: a v2 field that a v1 receiver quietly ignores is a
 * downgrade the sender cannot detect.
 *
 * Everything here happens **before** any crypto. Structural failures are reported as
 * [ErrorCode.DECRYPT_FAILED], which is the convention the receiver and the C# engine already
 * use for malformed framing (bad base64, wrong nonce length, unparseable direction) — §7.2
 * defines no generic "malformed envelope" code, and inventing one on the phone alone would
 * put the two implementations into disagreement. Recorded as PQ-A2-2 in
 * `docs/protocol-questions.md`.
 */
object EnvelopeJson {

    /** Exactly the fields §3 defines. `sig` is the only optional one. */
    val KNOWN_FIELDS = setOf("v", "pairing", "dir", "seq", "ts", "key_id", "nonce", "ciphertext", "sig")

    private val json = Json

    data class ParseResult(val envelope: ReceivedEnvelope?, val error: ErrorCode?) {
        val ok: Boolean get() = envelope != null
    }

    fun parse(wire: String): ParseResult {
        val root = try {
            json.parseToJsonElement(wire) as? JsonObject ?: return fail()
        } catch (_: IllegalArgumentException) {
            return fail()
        }

        // Unknown-field rejection comes first: if the sender is speaking a dialect this
        // receiver does not know, nothing else it says should be interpreted.
        if (root.keys.any { it !in KNOWN_FIELDS }) return fail()

        val v = root.intField("v") ?: return fail()
        val pairing = root.stringField("pairing") ?: return fail()
        val dir = root.stringField("dir") ?: return fail()
        val seq = root.longField("seq") ?: return fail()
        val ts = root.stringField("ts") ?: return fail()
        val keyId = root.stringField("key_id") ?: return fail()
        val nonce = root.stringField("nonce") ?: return fail()
        val ciphertext = root.stringField("ciphertext") ?: return fail()

        // Absent and JSON null are both "no signature"; a present-but-non-string sig is
        // malformed rather than absent, so it must not silently degrade into "unsigned"
        // (that would turn a broken signature into a missing one and change which check fires).
        val sigElement = root["sig"]
        val sig = when {
            sigElement == null -> null
            sigElement is JsonPrimitive && sigElement.isString -> sigElement.content
            sigElement.toString() == "null" -> null
            else -> return fail()
        }

        // Shape check only -- it costs nothing and keeps an obviously-wrong pairing id from
        // reaching the AAD, where it would fail as a confusing decrypt error instead.
        if (!isValidPairingId(pairing)) return fail()

        return ParseResult(
            ReceivedEnvelope(v, pairing, dir, seq, ts, keyId, nonce, ciphertext, sig),
            null,
        )
    }

    private fun fail() = ParseResult(null, ErrorCode.DECRYPT_FAILED)

    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.longField(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun JsonObject.intField(key: String): Int? = longField(key)?.let {
        if (it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) it.toInt() else null
    }
}
