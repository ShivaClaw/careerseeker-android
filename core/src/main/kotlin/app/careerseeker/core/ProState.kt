package app.careerseeker.core

/**
 * What the phone may honestly say about Pro.
 *
 * The whole point of this type is that **[Unlocked] has exactly one producer**:
 * [ProState.afterEngineAck]. There is no constructor path from a locally-computed
 * [EntitlementVerdict] to [Unlocked], because §4.3.2 makes the engine the verifier and the
 * phone a courier — and because a client that unlocked features on its own verdict would be
 * trusting a check running on the device that benefits from lying.
 *
 * This is the phone-side expression of the project's one rule. The engine cannot fabricate a
 * skill; the phone must not be able to fabricate a status.
 */
sealed interface ProState {

    /** No receipt has been delivered. Pro features are locked and say so plainly. */
    data object Free : ProState

    /**
     * A receipt has been forwarded and the engine has not answered yet.
     *
     * Not "unlocked optimistically". The user is told the truth — the desktop is checking —
     * because an optimistic unlock that later revokes itself is exactly the dishonest state
     * this design exists to avoid.
     */
    data object AwaitingEngine : ProState

    /** The engine verified the purchase and answered `entitlement_ack`. The only unlocked state. */
    data class Unlocked(val productId: String, val acknowledgedAt: String) : ProState

    /**
     * The receipt cannot unlock anything, and the reason is worth showing.
     *
     * Carrying the verdict lets the UI say "this receipt is for a different product" rather
     * than a generic failure — the honest reason is more useful than a shrug, and it is safe to
     * show because it grants nothing.
     */
    data class Rejected(val verdict: EntitlementVerdict) : ProState

    val isPro: Boolean get() = this is Unlocked

    companion object {
        /**
         * The only way to reach [Unlocked].
         *
         * @param ackBody the decrypted body of an `entitlement_ack` envelope that
         *   [EnvelopeReceiver] has already accepted — meaning it decrypted under the active
         *   pairing key and its sequence was fresh. Authentication is the receiver's job; this
         *   function's job is to refuse to invent the state without one.
         */
        fun afterEngineAck(productId: String, acknowledgedAt: String): ProState =
            Unlocked(productId, acknowledgedAt)

        /**
         * What the phone shows after its own pre-screen of a receipt.
         *
         * A local [EntitlementVerdict.ACCEPTED] means only "worth sending to the engine", so it
         * maps to [AwaitingEngine] — never to [Unlocked]. Every other verdict is a reason the
         * user can act on immediately without waiting for a round trip.
         */
        fun afterLocalPrescreen(verdict: EntitlementVerdict): ProState =
            if (verdict == EntitlementVerdict.ACCEPTED) AwaitingEngine else Rejected(verdict)
    }
}
