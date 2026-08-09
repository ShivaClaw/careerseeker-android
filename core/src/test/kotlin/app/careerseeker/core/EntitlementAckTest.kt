package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §4.3.3 of `Sync-Protocol.md`, asserted rather than trusted.
 *
 * ## Where these payloads come from
 *
 * The two grant bodies below are **transcribed verbatim** from the shared vectors
 * `entitlement-ack.json` and `entitlement-ack-no-order-id.json` (`plaintext_json`), which the
 * main repo's `generate.mjs` produces. They are transcribed rather than read out of
 * `core/src/test/resources/sync-vectors/` because the vendored copy is pinned at main-repo
 * commit `679a317` and those two files postdate the pin — they live on the **unmerged** PR #32.
 * Re-vendoring to pick them up would move the pin to an unmerged branch commit, which is
 * exactly the cross-repo drift the `VECTORS.lock` pin exists to prevent.
 *
 * So this file proves the applier obeys §4.3.3 against the real bytes, and the formal
 * vector-driven assertion — the one that belongs in `ProtocolVectorsTest` alongside the other
 * `type`-filtered sections — is deliberately deferred to the re-vendor slice that follows
 * PR #32 merging. `Sync-Protocol.md` §10.2 already says no consumer asserts against these
 * vectors yet; this file does not change that, and does not claim to.
 */
class EntitlementAckTest {

    private val applier = EntitlementAckApplier(knownProductIds = setOf("pro_unlock"))

    /** `entitlement-ack.json` → `plaintext_json`, verbatim. */
    private val ackWithOrderId = """
        {"kind":"entitlement_ack","body":{"product_id":"pro_unlock",
         "acknowledged_at":"2026-06-11T14:02:11Z","order_id":"GPA.3390-8461-2039-11123"}}
    """.trimIndent().toByteArray()

    /** `entitlement-ack-no-order-id.json` → `plaintext_json`, verbatim. */
    private val ackNoOrderId = """
        {"kind":"entitlement_ack","body":{"product_id":"pro_unlock",
         "acknowledged_at":"2026-06-11T14:02:11Z"}}
    """.trimIndent().toByteArray()

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
