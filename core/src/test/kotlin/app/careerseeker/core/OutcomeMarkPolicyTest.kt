package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §4.3 (the p2e `outcome` kind and its five-value subset), §4.3.1 (the carried superset, and an
 * absent outcome meaning "unset"), §6.1 (independent per-direction counters) and P4 §2.5 (outcome
 * tracking is Pro), asserted rather than trusted.
 *
 * The rule none of these can check is the one that matters most in production: **there is no
 * `outcome_ack`**. §4.3's engine→phone table acknowledges `doc_edit` (via `conflict`) and
 * `entitlement` (via `entitlement_ack`) and nothing else, and the engine's dispatcher reports
 * `OutcomeApplied` even when its applier seam is null. So every assertion below about
 * convergence is an assertion about what the phone does with a *later dashboard payload*, which
 * is the only evidence v1 gives it.
 *
 * What these tests do **not** prove: that a mark reaches the engine. Building the envelope needs
 * [DeviceSigner] and an Android Keystore key (§5.4), which needs S3 and therefore an emulator
 * (BLOCKED B-4); pushing it needs the `:app` transport, which is unwritten. This file covers the
 * decision layer only, which is exactly the half that needs neither.
 */
class OutcomeMarkPolicyTest {

    private val pro: ProState = ProState.afterEngineAck("pro_annual", "2026-08-09T10:00:00Z")
    private val free: ProState = ProState.Free

    private fun sent(decision: MarkDecision): PendingMark =
        assertIs<MarkDecision.Send>(decision, "expected a Send, got $decision").mark

    private fun refusal(decision: MarkDecision): MarkRefusal =
        assertIs<MarkDecision.Refused>(decision, "expected a Refused, got $decision").reason

    // ---- who may mark ----

    @Test
    fun `a Free user cannot mark an outcome`() {
        val p = OutcomeMarkPolicy()
        assertEquals(MarkRefusal.NOT_PRO, refusal(p.mark(free, "app_1", Outcome.INTERVIEW, AT, null)))
        assertTrue(p.queued().isEmpty(), "a refused mark must not queue an envelope")
    }

    /**
     * [ProState.AwaitingEngine] is the state a phone is in after forwarding a receipt the engine
     * has not answered. It is deliberately *not* an optimistic unlock, and this pins that the
     * distinction reaches the outcome surface too — otherwise a user could mark during the
     * round trip and the phone would be spending signed envelopes on an unproven entitlement.
     */
    @Test
    fun `awaiting the engine is not Pro enough to mark`() {
        val p = OutcomeMarkPolicy()
        val awaiting = ProState.afterLocalPrescreen(EntitlementVerdict.ACCEPTED)
        assertEquals(ProState.AwaitingEngine, awaiting)
        assertEquals(MarkRefusal.NOT_PRO, refusal(p.mark(awaiting, "app_1", Outcome.OFFER, AT, null)))
    }

    @Test
    fun `an unlocked user marks and gets exactly one envelope to send`() {
        val p = OutcomeMarkPolicy()
        val mark = sent(p.mark(pro, "app_1", Outcome.INTERVIEW, AT, null))
        assertEquals("app_1", mark.appId)
        assertEquals(Outcome.INTERVIEW, mark.outcome)
        assertEquals(AT, mark.at)
        assertEquals(listOf(mark), p.queued())
    }

    // ---- what may be offered ----

    @Test
    fun `nothing is offered to a Free user`() {
        assertTrue(OutcomeMarkPolicy().offerFor(free, "app_1", null).isEmpty())
    }

    /**
     * The `no_reply` trap. §4.3.1's carried superset has six values; §4.3's phone-settable subset
     * has five. A screen that built its buttons from whatever the engine last reported would
     * offer `no_reply`, and the engine would reject the resulting envelope.
     */
    @Test
    fun `no_reply is never offered, even while the engine is reporting it`() {
        val offered = OutcomeMarkPolicy().offerFor(pro, "app_1", engineOutcome = "no_reply")
        assertEquals(Outcome.entries.toSet(), offered)
        assertFalse(offered.any { it.wire in Outcome.ENGINE_ONLY })
        assertTrue("no_reply" in Outcome.ENGINE_ONLY, "guarding the guard")
    }

    @Test
    fun `the value already displayed is not offered again`() {
        val p = OutcomeMarkPolicy()
        assertFalse(Outcome.REPLIED in p.offerFor(pro, "app_1", engineOutcome = "replied"))
        p.mark(pro, "app_2", Outcome.OFFER, AT, null)
        assertFalse(
            Outcome.OFFER in p.offerFor(pro, "app_2", engineOutcome = null),
            "a pending mark is displayed, so it must not be offered either",
        )
    }

    @Test
    fun `re-marking the displayed value is refused rather than re-sent`() {
        val p = OutcomeMarkPolicy()
        assertEquals(
            MarkRefusal.UNCHANGED,
            refusal(p.mark(pro, "app_1", Outcome.SENT, AT, engineOutcome = "sent")),
        )
        p.mark(pro, "app_2", Outcome.SENT, AT, null)
        assertEquals(
            MarkRefusal.UNCHANGED,
            refusal(p.mark(pro, "app_2", Outcome.SENT, AT2, null)),
            "a pending mark counts as displayed, so re-tapping it is also a duplicate",
        )
        assertEquals(1, p.queued().size)
    }

    // ---- the shadow ----

    @Test
    fun `a pending mark is displayed, and is flagged as not yet confirmed`() {
        val p = OutcomeMarkPolicy()
        p.mark(pro, "app_1", Outcome.INTERVIEW, AT, engineOutcome = "sent")
        assertEquals(DisplayedOutcome("interview", pending = true), p.display("app_1", "sent"))
    }

    @Test
    fun `an application with no mark just shows what the engine said`() {
        val p = OutcomeMarkPolicy()
        assertEquals(DisplayedOutcome("no_reply", pending = false), p.display("app_9", "no_reply"))
        assertEquals(DisplayedOutcome(null, pending = false), p.display("app_9", null))
    }

    /**
     * The case the whole design turns on, and the one an auditor should attack first.
     *
     * §6.1 gives each direction an independent counter, and §4.3.1's application summary carries
     * no per-application timestamp, so a snapshot arriving after a mark may have been generated
     * before it. If the engine's value won on arrival the badge would revert under the user's
     * finger for a mark that is merely in flight — indistinguishable from "it didn't save".
     */
    @Test
    fun `a stale snapshot does not revert a mark that is still in flight`() {
        val p = OutcomeMarkPolicy()
        p.mark(pro, "app_1", Outcome.INTERVIEW, AT, engineOutcome = null)
        p.onSent("app_1")

        assertFalse(p.onEngineOutcome("app_1", null), "one disagreement must not retire the mark")
        assertEquals(DisplayedOutcome("interview", pending = true), p.display("app_1", null))
    }

    @Test
    fun `the engine reporting the marked value retires the mark`() {
        val p = OutcomeMarkPolicy()
        p.mark(pro, "app_1", Outcome.INTERVIEW, AT, null)

        assertTrue(p.onEngineOutcome("app_1", "interview"))
        assertNull(p.pendingFor("app_1"))
        assertEquals(DisplayedOutcome("interview", pending = false), p.display("app_1", "interview"))
        assertTrue(p.queued().isEmpty(), "a converged mark must stop being replayed")
    }

    /**
     * The other half of the bound: with no ack, a mark the engine silently dropped would
     * otherwise display as the user's truth forever. The phone would rather eventually tell the
     * truth than permanently tell the user what they wanted to hear.
     */
    @Test
    fun `a mark the engine keeps disagreeing with is eventually abandoned`() {
        val p = OutcomeMarkPolicy(disagreementLimit = 3)
        p.mark(pro, "app_1", Outcome.OFFER, AT, null)

        assertFalse(p.onEngineOutcome("app_1", "sent"))
        assertFalse(p.onEngineOutcome("app_1", "sent"))
        assertEquals(2, p.pendingFor("app_1")?.disagreements)

        assertTrue(p.onEngineOutcome("app_1", "sent"), "the third disagreement retires it")
        assertEquals(DisplayedOutcome("sent", pending = false), p.display("app_1", "sent"))
    }

    @Test
    fun `a disagreement count resets when the user marks again`() {
        val p = OutcomeMarkPolicy(disagreementLimit = 3)
        p.mark(pro, "app_1", Outcome.OFFER, AT, null)
        p.onEngineOutcome("app_1", "sent")
        p.onEngineOutcome("app_1", "sent")

        val remark = sent(p.mark(pro, "app_1", Outcome.REJECTED, AT2, engineOutcome = "sent"))
        assertEquals(0, remark.disagreements, "a fresh intention gets a fresh shadow")
    }

    @Test
    fun `an application the payload never mentions keeps its mark`() {
        val p = OutcomeMarkPolicy(disagreementLimit = 1)
        p.mark(pro, "app_1", Outcome.INTERVIEW, AT, null)

        // A delta carries a recent window, not every application. Reporting only what the payload
        // actually mentioned is the caller's contract; this pins that an unmentioned application
        // is untouched, so a quiet engine cannot retire marks by omission.
        assertFalse(p.onEngineOutcome("app_2", "sent"))
        assertEquals(Outcome.INTERVIEW, p.pendingFor("app_1")?.outcome)
    }

    // ---- the offline queue ----

    @Test
    fun `a failed push keeps the mark queued and does not count as a disagreement`() {
        val p = OutcomeMarkPolicy(disagreementLimit = 2)
        p.mark(pro, "app_1", Outcome.REPLIED, AT, null)

        repeat(5) { p.onSendFailed("app_1") }

        assertEquals(1, p.queued().size, "an offline mark must survive to be replayed")
        assertEquals(0, p.pendingFor("app_1")?.disagreements)
    }

    @Test
    fun `a reaching-the-relay report does not confirm anything`() {
        val p = OutcomeMarkPolicy()
        p.mark(pro, "app_1", Outcome.OFFER, AT, null)
        p.onSent("app_1")

        // The relay is a blind store-and-forward queue and there is no ack, so delivery is not
        // application. The mark stays pending until a dashboard payload agrees.
        assertEquals(DisplayedOutcome("offer", pending = true), p.display("app_1", null))
        assertEquals(1, p.queued().size)
    }

    @Test
    fun `marks on different applications replay in the order the user made them`() {
        val p = OutcomeMarkPolicy()
        p.mark(pro, "app_1", Outcome.SENT, AT, null)
        p.mark(pro, "app_2", Outcome.REPLIED, AT2, null)
        p.mark(pro, "app_3", Outcome.REJECTED, AT3, null)

        assertEquals(listOf("app_1", "app_2", "app_3"), p.queued().map { it.appId })
    }

    /**
     * §4.3.1 makes the carried outcome latest-wins state, not an event log, so two taps on one
     * application before either leaves the phone are one intention. Sending both would burn two
     * §6.1 sequence numbers to describe it.
     */
    @Test
    fun `re-marking one application collapses to a single latest envelope, moved to the end`() {
        val p = OutcomeMarkPolicy()
        p.mark(pro, "app_1", Outcome.SENT, AT, null)
        p.mark(pro, "app_2", Outcome.REPLIED, AT2, null)
        p.mark(pro, "app_1", Outcome.INTERVIEW, AT3, null)

        assertEquals(listOf("app_2", "app_1"), p.queued().map { it.appId })
        assertEquals(Outcome.INTERVIEW, p.pendingFor("app_1")?.outcome)
        assertEquals(AT3, p.pendingFor("app_1")?.at)
    }

    @Test
    fun `abandoning a mark drops the shadow immediately`() {
        val p = OutcomeMarkPolicy()
        p.mark(pro, "app_1", Outcome.OFFER, AT, null)
        p.abandon("app_1")

        assertTrue(p.queued().isEmpty())
        assertEquals(DisplayedOutcome(null, pending = false), p.display("app_1", null))
    }

    // ---- construction ----

    @Test
    fun `a disagreement limit below one is refused at construction`() {
        for (bad in listOf(0, -1)) {
            val e = kotlin.runCatching { OutcomeMarkPolicy(disagreementLimit = bad) }.exceptionOrNull()
            assertIs<IllegalArgumentException>(e, "limit $bad must be refused")
        }
    }

    /**
     * A limit of 1 means the first disagreeing report wins. Legitimate — it is the "trust the
     * desktop" end of the dial — but it reintroduces the revert-under-the-finger case, so it is
     * pinned as a deliberate behaviour rather than left to be discovered.
     */
    @Test
    fun `a limit of one retires on the first disagreement`() {
        val p = OutcomeMarkPolicy(disagreementLimit = 1)
        p.mark(pro, "app_1", Outcome.INTERVIEW, AT, null)
        assertTrue(p.onEngineOutcome("app_1", null))
        assertEquals(DisplayedOutcome(null, pending = false), p.display("app_1", null))
    }

    @Test
    fun `the default limit is the documented one`() {
        assertEquals(3, OutcomeMarkPolicy.DEFAULT_DISAGREEMENT_LIMIT)
        assertEquals(3, OutcomeMarkPolicy().disagreementLimit)
    }

    private companion object {
        const val AT = "2026-08-09T14:02:11Z"
        const val AT2 = "2026-08-09T14:03:11Z"
        const val AT3 = "2026-08-09T14:04:11Z"
    }
}
