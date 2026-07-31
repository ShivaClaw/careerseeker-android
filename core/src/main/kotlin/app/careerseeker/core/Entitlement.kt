package app.careerseeker.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * The result of checking a Google Play signed purchase record (Sync-Protocol.md §4.3.2).
 *
 * Each value corresponds to one distinct rejection reason, and the shared vectors pin one
 * negative vector per reason. Collapsing two of these into one would pass the vectors' *valid*
 * cases while hiding which check actually fired — and §10 is explicit that rejecting for the
 * wrong reason is a failure, because it usually means a check fired early and the real one is
 * untested.
 */
enum class EntitlementVerdict(val wire: String) {
    ACCEPTED("accepted"),
    SIGNATURE_INVALID("signature_invalid"),
    WRONG_PACKAGE("wrong_package"),
    WRONG_PRODUCT("wrong_product"),
    NOT_PURCHASED("not_purchased"),

    /** `original_json` was not a JSON object, or a required field was missing/mistyped. */
    MALFORMED("malformed"),
    ;

    companion object {
        fun fromWire(value: String): EntitlementVerdict? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Checks a Play-signed purchase record.
 *
 * ## This does not grant Pro, and must never be wired up as though it does
 *
 * The protocol makes the **phone a courier and the engine the verifier** (§4.3.2, gate
 * P0-WORKER option C). The phone forwards `{original_json, signature}` to the engine inside a
 * device-signed `entitlement` envelope; the engine verifies it against its configured Play
 * public key and answers with `entitlement_ack`. **Only that ack unlocks Pro.**
 *
 * So why run the check here at all? Two honest reasons, neither of them entitlement:
 *
 *  1. It lets the phone tell the user *immediately* and *truthfully* why a purchase will not
 *     unlock anything ("this receipt is for a different product") instead of showing a
 *     hopeful spinner until the engine gets around to answering.
 *  2. It is the phone half of the cross-language conformance proof: the same five signed
 *     vectors classify identically here and in the C# `GoogleSignedPayloadVerifier`.
 *
 * A locally-computed [EntitlementVerdict.ACCEPTED] means "worth sending to the engine",
 * never "the user has Pro". A client that unlocked features on its own verdict would be
 * trusting a check running on the very device that benefits from lying — which is the whole
 * reason the engine is authoritative.
 *
 * The Play public key, the package name, and the product-id set are **configuration**, not
 * constants: the production license key only exists once the Play app is created (§4.3.2).
 */
class EntitlementVerifier(
    playPublicKeySpkiBase64: String,
    private val expectedPackageName: String,
    private val expectedProductIds: Set<String>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Decoded once; an unparseable configured key is a configuration bug, not a runtime verdict. */
    private val playPublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(playPublicKeySpkiBase64)))

    /**
     * @param originalJson `Purchase.getOriginalJson()` **verbatim**. The signature covers these
     *   exact bytes, so it is verified as-is and never re-serialised (§4.3.2).
     * @param signatureBase64 `Purchase.getSignature()` — **standard** base64 with `+`, `/` and
     *   `=` padding, because that is what Play emits. The unpadded-base64url rule that governs
     *   envelope framing (§3) deliberately does not apply to it; this is payload content.
     */
    fun verify(originalJson: String, signatureBase64: String): EntitlementVerdict {
        // Order is normative (§4.3.2): signature, then packageName, then productId, then
        // purchaseState. Signature first is not a style choice -- every field below is read
        // out of a string that is only trustworthy once the signature over it has verified.
        if (!signatureVerifies(originalJson, signatureBase64)) return EntitlementVerdict.SIGNATURE_INVALID

        val record = try {
            json.parseToJsonElement(originalJson) as? JsonObject ?: return EntitlementVerdict.MALFORMED
        } catch (_: IllegalArgumentException) {
            return EntitlementVerdict.MALFORMED
        }

        val packageName = record.stringOrNull("packageName") ?: return EntitlementVerdict.MALFORMED
        if (packageName != expectedPackageName) return EntitlementVerdict.WRONG_PACKAGE

        val productId = record.stringOrNull("productId") ?: return EntitlementVerdict.MALFORMED
        if (productId !in expectedProductIds) return EntitlementVerdict.WRONG_PRODUCT

        // PURCHASED is 0 in the RAW JSON. Purchase.getPurchaseState() remaps it to 1; reading
        // that value here instead would accept pending purchases and reject real ones.
        val purchaseState = record.intOrNull("purchaseState") ?: return EntitlementVerdict.MALFORMED
        if (purchaseState != PURCHASE_STATE_PURCHASED) return EntitlementVerdict.NOT_PURCHASED

        return EntitlementVerdict.ACCEPTED
    }

    private fun signatureVerifies(originalJson: String, signatureBase64: String): Boolean = try {
        val signatureBytes = Base64.getDecoder().decode(signatureBase64)
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(playPublicKey)
            update(originalJson.toByteArray(Charsets.UTF_8))
            verify(signatureBytes)
        }
    } catch (_: Exception) {
        // A malformed signature encoding is not a valid signature. Reported as
        // SIGNATURE_INVALID rather than MALFORMED: the record itself may be perfectly
        // well-formed, and it is the signature that failed.
        false
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    companion object {
        /**
         * RSASSA-PKCS1-v1_5 over SHA-1. Google's fixed IAB format, not a choice made here;
         * the assessment lives in the android repo's `Entitlement-Architecture.md` and is
         * not re-litigated in code.
         */
        const val SIGNATURE_ALGORITHM = "SHA1withRSA"

        /** `purchaseState` value meaning PURCHASED **in the raw JSON** (§4.3.2). */
        const val PURCHASE_STATE_PURCHASED = 0
    }
}
