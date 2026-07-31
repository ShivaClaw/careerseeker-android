package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one rule this type exists for: the phone cannot unlock Pro on its own say-so.
 *
 * These read like tautologies precisely because the design makes them tautological. That is the
 * point — the guarantee is structural, and if a later change makes any of these fail, it has
 * introduced a path from a device-local opinion to a paid feature.
 */
class ProStateTest {

    @Test
    fun `a locally accepted receipt awaits the engine and does not unlock`() {
        val state = ProState.afterLocalPrescreen(EntitlementVerdict.ACCEPTED)

        assertEquals(ProState.AwaitingEngine, state)
        assertTrue(!state.isPro, "a local verdict must never unlock Pro (§4.3.2 — the engine verifies)")
    }

    @Test
    fun `every rejection verdict is surfaced with its reason and stays locked`() {
        // Showing the reason is safe because it grants nothing, and it is far more useful than
        // a generic failure: "this receipt is for a different product" is actionable.
        for (verdict in EntitlementVerdict.entries - EntitlementVerdict.ACCEPTED) {
            val state = ProState.afterLocalPrescreen(verdict)
            assertEquals(ProState.Rejected(verdict), state)
            assertTrue(!state.isPro, "$verdict must not unlock")
        }
    }

    @Test
    fun `only the engine ack produces the unlocked state`() {
        val unlocked = ProState.afterEngineAck("pro_unlock", "2026-06-11T14:02:11Z")

        assertTrue(unlocked.isPro)
        assertEquals(ProState.Unlocked("pro_unlock", "2026-06-11T14:02:11Z"), unlocked)

        // And nothing else in the type's vocabulary is Pro.
        assertTrue(!ProState.Free.isPro)
        assertTrue(!ProState.AwaitingEngine.isPro)
        assertTrue(!ProState.Rejected(EntitlementVerdict.NOT_PURCHASED).isPro)
    }

    @Test
    fun `the unlocked state is unreachable from any entitlement verdict`() {
        // The structural claim, checked exhaustively: no verdict maps to Unlocked.
        assertTrue(
            EntitlementVerdict.entries.none { ProState.afterLocalPrescreen(it) is ProState.Unlocked },
            "a verdict computed on this device must not be able to produce Unlocked",
        )
    }

    @Test
    fun `an unpaired phone is Free rather than unknown`() {
        // Honest default: no receipt delivered means locked and said plainly, not a hopeful
        // blank state that a screen might render as "checking..." forever.
        assertTrue(!ProState.Free.isPro)
    }
}
