package app.careerseeker.core

/**
 * A mark the user has made that the engine has not confirmed.
 *
 * @property appId the application it applies to, as carried in §4.3.1's summary `id`.
 * @property outcome the phone-settable value (§4.3's p2e five-value subset).
 * @property at the observation time the user is reporting, ISO-8601. Carried straight into the
 *   body's `at` field. Not a security input (§6.3) and never used for ordering here.
 * @property disagreements how many dashboard payloads have arrived carrying a *different* value
 *   for [appId] since this mark was made. The shadow's only bound — see [OutcomeMarkPolicy].
 */
data class PendingMark(
    val appId: String,
    val outcome: Outcome,
    val at: String,
    val disagreements: Int = 0,
)

/** Why a tap did not become a mark. Diagnostic and UI copy; it never rides the wire. */
enum class MarkRefusal {
    /**
     * Outcome tracking is a Pro feature (P4 §2.5). Only [ProState.Unlocked] may mark, and that
     * state has exactly one producer — the engine's `entitlement_ack`. A phone that let a Free
     * user mark would be spending a signed, sequence-burning envelope the engine will honour on
     * behalf of a user who has not paid, which is the phone fabricating entitlement by the back
     * door.
     */
    NOT_PRO,

    /**
     * The application already shows this value, counting an unconfirmed mark. Re-sending is not
     * harmless: every p2e envelope consumes a sequence number the sender must persist (§6.1),
     * and a duplicate is indistinguishable on the engine side from a genuine re-mark.
     */
    UNCHANGED,
}

/** What the phone should do about one tap. */
sealed interface MarkDecision {
    /**
     * Build and send `outcome` for [mark].
     *
     * The envelope itself is built by [OutboundEnvelopeFactory.outcome], which requires a
     * [DeviceSigner] because `outcome` is state-changing (§5.4). That is deliberately *not* this
     * type's job: the decision of what to mark needs no key, so it is testable — and shippable —
     * before the Android Keystore work in S3 exists.
     */
    data class Send(val mark: PendingMark) : MarkDecision

    /** Do nothing, and tell the user why. */
    data class Refused(val reason: MarkRefusal) : MarkDecision
}

/**
 * What the badge should render for one application.
 *
 * @property wire the outcome string to show, or `null` for "no outcome" (§4.3.1: an absent field
 *   is never a malformed value). May be a value outside [Outcome] — `no_reply` is a legitimate
 *   engine-set observation the phone renders but must never offer.
 * @property pending true when [wire] comes from an unconfirmed local mark rather than from the
 *   engine. The UI is expected to distinguish the two; showing a local mark as though the desktop
 *   had confirmed it is the specific dishonesty this flag exists to prevent.
 */
data class DisplayedOutcome(val wire: String?, val pending: Boolean)

/**
 * Decides what the phone may mark, what it shows while a mark is in flight, and when it stops
 * believing its own mark (§4.3 p2e `outcome`, P4 §2.5).
 *
 * The loop this serves is: user taps → [mark] → if [MarkDecision.Send], build one signed envelope
 * with [OutboundEnvelopeFactory.outcome] and push it → report the push with [onSent] or
 * [onSendFailed] → when a `snapshot` or `delta` lands, feed each application's carried outcome to
 * [onEngineOutcome]. Rendering asks [display]. Everything here is `:core`: no Room, no emulator,
 * no device key.
 *
 * ## The finding that shapes all of it: a mark is never acknowledged
 *
 * §4.3's engine→phone table has an ack for exactly two things — `conflict` rejects a `doc_edit`,
 * `entitlement_ack` confirms an entitlement. **There is no `outcome_ack` and no rejection kind for
 * `outcome`.** Worse, the engine's dispatcher returns `InboundOutcome.OutcomeApplied` for every
 * accepted `outcome` envelope *whether or not an applier is wired*
 * (`src/Sync/InboundDispatcher.cs:98-103`), and `IOutcomeApplier` is explicitly nullable — "a null
 * applier means outcome dispatch is a no-op seam for now". So today the engine can accept a mark,
 * report it applied, and drop it on the floor.
 *
 * The phone therefore has exactly one way to learn a mark landed: **the value comes back** in a
 * later §4.3.1 dashboard payload. That is the whole basis of the design below. Recorded as
 * PQ-S6-1 in `docs/protocol-questions.md`.
 *
 * ## Why a mark shadows the engine, and why the shadow is bounded
 *
 * A snapshot that arrives after a mark may have been *generated before* it. The phone cannot tell:
 * §6.1 gives each direction "an independent counter", so the e2p seq carrying the snapshot and the
 * p2e seq carrying the mark are not comparable, and §4.3.1's application summary carries no
 * per-application timestamp to compare either. There is no cross-direction ordering in v1.
 *
 * Given that, letting the engine's value win on arrival would revert the badge under the user's
 * finger for a mark that is perfectly fine and merely in flight — indistinguishable, to them, from
 * "it didn't save". So a pending mark **shadows** whatever the engine reports.
 *
 * But an unbounded shadow is the opposite failure: with no ack, a mark the engine silently dropped
 * would display as the user's truth forever. So the shadow is bounded by
 * [disagreementLimit] — counted in *engine reports that disagree*, not elapsed time, because §6.3
 * makes clocks untrustworthy and a disagreeing report is the only monotone evidence the phone
 * actually has. After that many dashboard payloads have carried a different value for the
 * application, the mark is abandoned and the engine's value shows through. The phone would rather
 * eventually tell the truth than permanently tell the user what they wanted to hear.
 *
 * Not thread-safe: drive it from the single coroutine that owns the transport loop.
 *
 * @param disagreementLimit how many disagreeing dashboard payloads retire an unconfirmed mark.
 *   Chosen, not measured, and small on purpose: one disagreement is expected (the snapshot already
 *   in flight when the mark was made), two is plausible on a busy engine, and beyond that the
 *   likeliest explanation is that the mark is not going to be applied. There is no deployment to
 *   derive a number from yet, which is why it is a parameter rather than a constant.
 */
class OutcomeMarkPolicy(val disagreementLimit: Int = DEFAULT_DISAGREEMENT_LIMIT) {

    init {
        require(disagreementLimit >= 1) {
            "disagreementLimit must be at least 1, was $disagreementLimit"
        }
    }

    /**
     * Unconfirmed marks, keyed by application, in the order they should be sent.
     *
     * A `LinkedHashMap` rather than a list because the collapse below depends on both properties:
     * keyed, so a re-mark replaces rather than queues; ordered, so replay after an offline spell
     * preserves the order the user acted in.
     */
    private val pending = LinkedHashMap<String, PendingMark>()

    /** Unconfirmed marks in send order. */
    fun queued(): List<PendingMark> = pending.values.toList()

    /** The unconfirmed mark for [appId], or null. */
    fun pendingFor(appId: String): PendingMark? = pending[appId]

    /**
     * The outcomes a Pro user may be offered for an application currently showing [engineOutcome].
     *
     * Two things this refuses to return, both of which would otherwise be easy mistakes in a
     * screen:
     *
     *  - **`no_reply`, ever.** It is in §4.3.1's carried superset but not §4.3's phone-settable
     *    subset — a desktop-set observation. [Outcome] cannot represent it at all, so the guard is
     *    the type; this method is where that guarantee becomes visible to the UI layer.
     *  - **The value already displayed.** Offering it invites the duplicate [MarkRefusal.UNCHANGED]
     *    exists to reject, so it is filtered here rather than refused later.
     *
     * Empty for any non-[ProState.Unlocked] state: the control should not be rendered at all, not
     * rendered and then refused.
     */
    fun offerFor(pro: ProState, appId: String, engineOutcome: String?): Set<Outcome> {
        if (!pro.isPro) return emptySet()
        val shown = display(appId, engineOutcome).wire
        return Outcome.entries.filterTo(LinkedHashSet()) { it.wire != shown }
    }

    /**
     * Decide one tap.
     *
     * @param engineOutcome the value the engine last carried for [appId] in a §4.3.1 payload, or
     *   null for "no outcome". Passed in rather than held because the replica owns dashboard
     *   state; this policy owns only what is unconfirmed.
     */
    fun mark(
        pro: ProState,
        appId: String,
        outcome: Outcome,
        at: String,
        engineOutcome: String?,
    ): MarkDecision {
        if (!pro.isPro) return MarkDecision.Refused(MarkRefusal.NOT_PRO)
        if (display(appId, engineOutcome).wire == outcome.wire) {
            return MarkDecision.Refused(MarkRefusal.UNCHANGED)
        }

        // Collapse rather than append. §4.3.1 makes the carried outcome latest-wins state, not an
        // event log, so a user who taps SENT then INTERVIEW before either leaves the phone wants
        // one envelope saying INTERVIEW -- sending both would burn two sequence numbers to
        // describe one intention. Removing first moves the entry to the end: the newest decision
        // is the newest thing to send, which is also the order the engine should see it in.
        pending.remove(appId)
        val mark = PendingMark(appId, outcome, at)
        pending[appId] = mark
        return MarkDecision.Send(mark)
    }

    /**
     * What to render for [appId], given the engine's carried value.
     *
     * An unconfirmed mark wins — see the class doc. The [DisplayedOutcome.pending] flag is how the
     * UI keeps that honest.
     */
    fun display(appId: String, engineOutcome: String?): DisplayedOutcome {
        val mark = pending[appId]
        return if (mark != null) DisplayedOutcome(mark.outcome.wire, pending = true)
        else DisplayedOutcome(engineOutcome, pending = false)
    }

    /**
     * Feed one application's carried outcome from an applied `snapshot` or `delta`.
     *
     * Call it for every application in the payload, including those carrying no outcome: an
     * absent field is a legitimate report of "no outcome" (§4.3.1) and disagreeing with a pending
     * mark is exactly the case the bound is for. Do **not** call it for applications the payload
     * omits — a `delta` carries a recent window, and treating "not mentioned" as "reported
     * different" would retire marks on the strength of an engine that simply said nothing.
     *
     * @return true if a pending mark for [appId] was retired by this report, either because the
     *   engine confirmed it or because it ran out of shadow.
     */
    fun onEngineOutcome(appId: String, engineOutcome: String?): Boolean {
        val mark = pending[appId] ?: return false

        // Convergence. The engine now reports what the user asked for, so the mark has served its
        // purpose -- whether it was this phone's envelope that caused it or the desktop arriving
        // at the same value independently. With no ack (see the class doc) the two are
        // indistinguishable, and for display purposes they are also equivalent.
        if (engineOutcome == mark.outcome.wire) {
            pending.remove(appId)
            return true
        }

        val seen = mark.disagreements + 1
        if (seen >= disagreementLimit) {
            pending.remove(appId)
            return true
        }
        pending[appId] = mark.copy(disagreements = seen)
        return false
    }

    /**
     * Report that the envelope for [appId] reached the relay.
     *
     * The mark deliberately stays pending. Reaching the relay is not the engine applying it — the
     * relay is a blind store-and-forward queue, and with no ack the only evidence of application
     * is the value coming back. Retiring here would drop the shadow while the mark was still
     * unconfirmed, which is the revert-under-the-finger this design exists to avoid.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onSent(appId: String) {
        // Intentionally empty. Present so the transport has somewhere honest to report success,
        // and so the absence of a state change here is a documented decision rather than an
        // omission someone later "fixes".
    }

    /**
     * Report that the envelope for [appId] could not be pushed.
     *
     * The mark stays queued so [queued] replays it — this is the offline case, and losing a mark
     * because the network was down is a data loss the user has no way to detect. The failed
     * attempt is **not** counted against [disagreementLimit]: that bound measures the engine
     * disagreeing, and an envelope that never arrived is not the engine's opinion about anything.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onSendFailed(appId: String) {
        // Intentionally empty, for the same reason as onSent: the decision not to change state is
        // the point, and it is worth a name so it can be tested and cited.
    }

    /** Drop the unconfirmed mark for [appId] without waiting for the bound. */
    fun abandon(appId: String) {
        pending.remove(appId)
    }

    companion object {
        /**
         * Default for [disagreementLimit]. See the class doc for why it is small and why it
         * counts reports rather than seconds.
         */
        const val DEFAULT_DISAGREEMENT_LIMIT = 3
    }
}
