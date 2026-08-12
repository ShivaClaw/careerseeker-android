package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §4.3.3 of `Sync-Protocol.md`, asserted rather than trusted.
 *
 * ## Where these payloads come from
 *
 * The two grant bodies are **decrypted out of the shared vectors** `entitlement-ack.json` and
 * `entitlement-ack-no-order-id.json`, which the main repo's `generate.mjs` produces. They are
 * read, not copied: [ackWithOrderId] is whatever AES-GCM yields from that vector's own
 * `ciphertext_b64u`, so it is the same artifact the engine's `SyncHarness` asserts against.
 *
 * ## Why this file used to transcribe them, and what that cost
 *
 * Until the vectors were re-vendored, the vendored copy was pinned at main-repo commit
 * `679a317` and both ack files postdated it, so the bodies were pasted into this file as
 * string literals and documented as "transcribed verbatim". They were not verbatim. The
 * generator seals `JSON.stringify(plaintext)` — compact, no whitespace — while the literals
 * were wrapped across two lines for readability, making them **142 and 104 bytes against the
 * vectors' 140 and 102**: a `,\n ` where the sealed bytes have a bare `,`. The tests passed
 * anyway, because JSON parsing ignores whitespace, which is exactly the point — a
 * transcription cannot fail when the vector moves, so it proves agreement with a snapshot
 * rather than with the vector. That is the gap PQ-A2-5 recorded, and this is its closure on
 * the phone side.
 *
 * The formal cross-implementation assertion lives in `ProtocolVectorsTest` alongside the other
 * `type`-filtered sections. This file keeps the *behavioural* half: what the applier does with
 * bodies that are malformed, foreign, or trying to un-grant.
 */
class EntitlementAckTest {

    private val applier = EntitlementAckApplier(knownProductIds = setOf("pro_unlock"))

    /** `entitlement-ack.json`, decrypted — not a copy of it. */
    private val ackWithOrderId = ackPlaintext("entitlement-ack")

    /** `entitlement-ack-no-order-id.json`, decrypted. */
    private val ackNoOrderId = ackPlaintext("entitlement-ack-no-order-id")

    private fun ackPlaintext(name: String): ByteArray {
        val url = requireNotNull(javaClass.classLoader.getResource("sync-vectors/v1/$name.json")) {
            "shared vector $name not on the test classpath — is core/src/test/resources/sync-vectors vendored?"
        }
        val v = Json.parseToJsonElement(File(url.toURI()).readText()).jsonObject
        fun str(key: String) = v[key]!!.jsonPrimitive.content
        val key = str("key_hex").let { h -> ByteArray(h.length / 2) { ((h[it * 2].digitToInt(16) shl 4) or h[it * 2 + 1].digitToInt(16)).toByte() } }
        return SyncCrypto.open(
            key,
            requireNotNull(Base64Url.decodeOrNull(str("nonce_b64u"))),
            str("aad"),
            requireNotNull(Base64Url.decodeOrNull(str("ciphertext_b64u"))),
        )
    }

    @Test
    fun `the grant bodies are the vectors' own bytes and not a re-wrapped copy`() {
        // Pins the defect this file used to have. A literal wrapped across two lines parses
        // identically to the sealed bytes, so every other test here passed while the bodies
        // were 2 bytes longer than the vectors'. Nothing but a byte-level check sees that.
        //
        // No magic lengths: the generator seals `JSON.stringify(plaintext)`, so the sealed
        // bytes must survive a compact re-serialisation unchanged. Whitespace anywhere
        // outside a string breaks this and nothing else.
        for ((name, bytes) in listOf("entitlement-ack" to ackWithOrderId, "no-order-id" to ackNoOrderId)) {
            val text = bytes.toString(Charsets.UTF_8)
            assertEquals(text, Json.parseToJsonElement(text).toString(), "$name is not compact JSON")
        }
    }

    @Test
    fun `the engine ack unlocks Pro and carries the granted product and time`() {
        val state = applier.apply(ProState.Free, ackWithOrderId)

        assertEquals(ProState.Unlocked("pro_unlock", "2026-06-11T14:02:11Z"), state)
        assertTrue(state.isPro)
    }

    @Test
    fun `order_id is genuinely optional and an ack without it is honoured identically`() {
        // The whole reason the vector pair exists. An implementation that requires order_id
        // fails here rather than in a support ticket about an unlock that did not happen.
        assertEquals(
            applier.apply(ProState.Free, ackWithOrderId),
            applier.apply(ProState.Free, ackNoOrderId),
        )
        assertEquals("GPA.3390-8461-2039-11123", applier.parse(ackWithOrderId)?.orderId)
        assertNull(applier.parse(ackNoOrderId)?.orderId)
    }

    @Test
    fun `an unknown product_id is ignored rather than unlocking anything`() {
        val foreign = """
            {"kind":"entitlement_ack","body":{"product_id":"pro_unlock_v2",
             "acknowledged_at":"2026-06-11T14:02:11Z"}}
        """.trimIndent().toByteArray()

        val state = applier.apply(ProState.Free, foreign)

        assertEquals(ProState.Free, state, "§4.3.3: ignore the ack, do not unlock on it")
        // Specifically NOT Rejected: nothing here says the user's receipt was bad. It says
        // this build does not know the product, which is the phone's problem to keep quiet
        // about rather than to blame the user for.
        assertTrue(state !is ProState.Rejected)
    }

    @Test
    fun `acknowledged_at is advisory and never expires or re-locks the entitlement`() {
        // §6.3. A receiver that treated the engine's clock as a security input would let two
        // machines disagreeing about the time look exactly like a revocation nobody performed.
        val ancient = """
            {"kind":"entitlement_ack","body":{"product_id":"pro_unlock",
             "acknowledged_at":"1999-01-01T00:00:00Z"}}
        """.trimIndent().toByteArray()

        val state = applier.apply(ProState.Free, ancient)

        assertTrue(state.isPro, "an old acknowledged_at must still grant")
        assertEquals(ProState.Unlocked("pro_unlock", "1999-01-01T00:00:00Z"), state)
    }

    @Test
    fun `there is no negative form - an extra body field cannot un-grant an ack`() {
        // §4.3.3: "An ack means granted, full stop." A kind whose meaning depends on reading a
        // field inside the body is the parser hazard §4.2 exists to avoid, and here it would
        // be a hazard on the one path that turns a paid feature on. So a field that LOOKS like
        // a revocation must be inert.
        val withDecoyFlag = """
            {"kind":"entitlement_ack","body":{"product_id":"pro_unlock",
             "acknowledged_at":"2026-06-11T14:02:11Z","granted":false,"revoked":true}}
        """.trimIndent().toByteArray()

        assertTrue(applier.apply(ProState.Free, withDecoyFlag).isPro)
    }

    @Test
    fun `a malformed or foreign body is ignored and leaves the state exactly as it was`() {
        val unusable = mapOf(
            "not JSON at all" to "this is not json".toByteArray(),
            "a JSON array, not an object" to """[{"kind":"entitlement_ack"}]""".toByteArray(),
            "body missing" to """{"kind":"entitlement_ack"}""".toByteArray(),
            "body is not an object" to """{"kind":"entitlement_ack","body":"pro_unlock"}""".toByteArray(),
            "product_id missing" to
                """{"kind":"entitlement_ack","body":{"acknowledged_at":"2026-06-11T14:02:11Z"}}""".toByteArray(),
            "acknowledged_at missing" to
                """{"kind":"entitlement_ack","body":{"product_id":"pro_unlock"}}""".toByteArray(),
            "product_id is not a string" to
                """{"kind":"entitlement_ack","body":{"product_id":7,"acknowledged_at":"2026-06-11T14:02:11Z"}}"""
                    .toByteArray(),
            "order_id present but not a string" to
                ("""{"kind":"entitlement_ack","body":{"product_id":"pro_unlock",""" +
                    """"acknowledged_at":"2026-06-11T14:02:11Z","order_id":42}}""").toByteArray(),
        )

        for ((why, payload) in unusable) {
            assertNull(applier.parse(payload), "must not parse: $why")
            assertEquals(ProState.Free, applier.apply(ProState.Free, payload), "must not unlock: $why")
        }
    }

    @Test
    fun `a payload of another kind cannot unlock Pro even if it carries a product_id`() {
        // Guards a caller that dispatched on the wrong branch. The receiver reports the kind it
        // found; this class re-checks it, because "the heartbeat happened to contain the right
        // fields" must never be a route to a paid feature.
        val heartbeat = """
            {"kind":"heartbeat","body":{"product_id":"pro_unlock",
             "acknowledged_at":"2026-06-11T14:02:11Z"}}
        """.trimIndent().toByteArray()

        assertNull(applier.parse(heartbeat))
        assertEquals(ProState.Free, applier.apply(ProState.Free, heartbeat))
    }

    @Test
    fun `applying an unusable ack never downgrades an entitlement already granted`() {
        // An ack means granted; there is no negative form. So no message arriving later may
        // talk the phone out of an entitlement it has already been given.
        val unlocked = applier.apply(ProState.Free, ackWithOrderId)
        assertTrue(unlocked.isPro)

        for (junk in listOf("garbage".toByteArray(), """{"kind":"entitlement_ack"}""".toByteArray())) {
            assertEquals(unlocked, applier.apply(unlocked, junk))
        }
    }

    @Test
    fun `the applier is the only route from a payload to Unlocked`() {
        // PQ-A2-4's boundary, restated at the seam where it could actually be lost: a local
        // ACCEPTED prescreen still yields AwaitingEngine, and only an ack moves past it.
        val local = ProState.afterLocalPrescreen(EntitlementVerdict.ACCEPTED)
        assertEquals(ProState.AwaitingEngine, local)
        assertTrue(!local.isPro)

        assertTrue(applier.apply(local, ackWithOrderId).isPro)
    }
}
