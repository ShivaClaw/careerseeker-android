package app.careerseeker.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The body of an `entitlement_ack` payload (Sync-Protocol.md §4.3.3).
 *
 * @property productId which entitlement the engine granted. A record of *what* was granted,
 *   never a request to grant it — see [EntitlementAckApplier].
 * @property acknowledgedAt RFC 3339 UTC, the engine's clock. **Advisory only** (§6.3).
 * @property orderId Play `orderId`, optional. Support correspondence data, not authorisation.
 */
data class EntitlementAck(
    val productId: String,
    val acknowledgedAt: String,
    val orderId: String?,
)

/**
 * Turns an accepted `entitlement_ack` envelope into [ProState] — the phone's only unlock path.
 *
 * ## Why this is a separate type and not a branch inside the receiver
 *
 * [EnvelopeReceiver] answers one question: is this envelope authentic and fresh? It decrypts
 * under the active pairing key, enforces the replay high-water mark, and hands back
 * `(kind, plaintext)`. It deliberately does not interpret payloads. This class answers the
 * second, independent question — does this authentic payload mean the user has Pro? — and it
 * is the only code in the app that may answer "yes".
 *
 * The split matters because the two questions have different failure modes. An envelope that
 * fails the first is an attack or corruption and is reported as an error code (§7.2). A body
 * that fails the second is very likely a *newer engine* talking to an older phone, and the
 * correct response is to ignore it quietly, not to error — which is why [apply] returns the
 * caller's current state rather than throwing or producing a rejection.
 *
 * ## Ignoring is not the same as rejecting
 *
 * §4.3.3: "A receiver that sees anything else MUST ignore the ack rather than unlock on it:
 * the field records *which* entitlement was granted, and is not a request to grant one."
 * Every failure below therefore returns `current` unchanged. In particular an unrecognised
 * `product_id` must not become [ProState.Rejected] — a rejection is a statement about the
 * user's *receipt*, and nothing here says their receipt was bad. It says this phone build
 * does not know the product, which is the phone's limitation to keep quiet about.
 *
 * @param knownProductIds the configured product-id set (§4.3.2). Configuration, not a
 *   constant: the production ids exist only once the Play app is created.
 */
class EntitlementAckApplier(private val knownProductIds: Set<String>) {

    // Unknown BODY fields are ignored on purpose, and this is the one place where that
    // differs from the envelope rule. §3 requires unknown top-level ENVELOPE fields to be
    // rejected, because the envelope is framing an attacker can reshape. The body is
    // different: §4.3.3 states there is no negative form, and that "a kind whose meaning
    // depends on reading a field inside the body is exactly the parser hazard §4.2 exists to
    // avoid". Ignoring unknown body fields is what makes that guarantee real -- a future
    // engine that adds a field cannot accidentally acquire a way to un-grant an ack, and an
    // attacker who could add one gains nothing by it.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param plaintext the decrypted payload of an envelope [EnvelopeReceiver] has already
     *   **accepted**. Authenticity is the receiver's job; this function's job is to refuse to
     *   invent an entitlement without one. Callers must not pass unverified bytes here.
     * @return the parsed ack, or `null` if the payload is not a well-formed `entitlement_ack`
     *   body for a product this build knows.
     */
    fun parse(plaintext: ByteArray): EntitlementAck? {
        val root = try {
            json.parseToJsonElement(plaintext.decodeToString()) as? JsonObject ?: return null
        } catch (_: IllegalArgumentException) {
            return null
        }

        // The kind is re-checked rather than trusted from the receiver's return value. The
        // receiver reports the kind it found; this class is the thing that acts on it, and a
        // caller that dispatched on the wrong branch must not be able to unlock Pro with a
        // heartbeat body that happens to carry a product_id.
        if (root.stringOrNull("kind") != PayloadKind.ENTITLEMENT_ACK.wire) return null

        val body = root["body"] as? JsonObject ?: return null

        val productId = body.stringOrNull("product_id") ?: return null
        if (productId !in knownProductIds) return null

        // Required even though it is advisory. Advisory governs what a receiver may DO with
        // the value (§6.3: never expire, re-lock or refuse on it), not whether it must be
        // present. An ack with no timestamp is a malformed ack, and guessing one here would
        // put a fabricated time in front of the user on a support screen.
        val acknowledgedAt = body.stringOrNull("acknowledged_at") ?: return null

        // Absent is fine; present-but-not-a-string is not. A non-string here means the
        // sender's shape disagrees with §4.3.3, and silently dropping it would hide that.
        val orderIdField = body["order_id"]
        val orderId = if (orderIdField == null) {
            null
        } else {
            (orderIdField as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        }

        return EntitlementAck(productId, acknowledgedAt, orderId)
    }

    /**
     * Applies an accepted ack to the current state.
     *
     * Returns [current] unchanged when the body cannot be honoured, per §4.3.3's "ignore
     * rather than unlock". Note what is deliberately absent: there is no path here that moves
     * any state *downward*. An ack means granted, full stop — so this never produces
     * [ProState.Free] or [ProState.Rejected], and a phone cannot be talked out of an
     * entitlement by a later message it happens to receive.
     */
    fun apply(current: ProState, plaintext: ByteArray): ProState {
        val ack = parse(plaintext) ?: return current
        return ProState.afterEngineAck(ack.productId, ack.acknowledgedAt)
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
