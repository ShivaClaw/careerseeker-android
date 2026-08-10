package app.careerseeker.core

/**
 * This device's ephemeral ECDH keypair for one pairing attempt (§5.2).
 *
 * Ephemeral in the strict sense: generated per attempt, used for exactly one completion, and
 * dropped with the attempt. It is **not** the device signing key — that one lives in the Android
 * Keystore, is non-exportable, and only its public half ever appears here.
 */
data class EphemeralKeyPair(val privateScalar: ByteArray, val publicUncompressed: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is EphemeralKeyPair &&
            privateScalar.contentEquals(other.privateScalar) &&
            publicUncompressed.contentEquals(other.publicUncompressed)

    override fun hashCode(): Int = 31 * privateScalar.contentHashCode() + publicUncompressed.contentHashCode()
}

/** Why a pairing attempt ended without a pairing. Each maps to a different thing to tell the user. */
enum class PairingAbort {
    /** The QR itself was refused (§5.2). [PairingStep.Aborted.inviteError] says which way. */
    INVITE_REJECTED,

    /**
     * The relay answered, and the answer forecloses this attempt: the pairing id is unknown
     * (the desktop's invite already expired or was cancelled) or the provisional bearer was
     * rejected. Neither becomes true again by waiting, so this is terminal rather than retryable.
     */
    RELAY_REFUSED,

    /** The relay could not be reached after [RelayClient]'s own retries. Retryable — see [retry]. */
    RELAY_UNAVAILABLE,

    /**
     * The human said the six digits do not match. **This is the MITM signal**, and it is reported
     * distinctly from [CANCELLED] for that reason: a mismatch means somebody else completed this
     * pairing, and the audit trail should be able to tell that apart from a user who changed
     * their mind.
     */
    CODE_MISMATCH,

    /** The human backed out before confirming. Nothing was persisted; nothing to report. */
    CANCELLED,
}

/** Everything the phone keeps when a pairing succeeds. Produced only after the human confirms. */
data class PairedPairing(
    val pairing: String,
    val suite: String,
    val relayBaseUrl: String,
    val keys: PairingKeys,
    val deviceSigPublicUncompressed: ByteArray,
    /** Bearer selection for the provisional → final handover (§5.2.3). */
    val tokens: RelayTokenLadder,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** One step of the pairing state machine. */
sealed interface PairingStep {
    /**
     * The completion is on the relay (or was already there — see [raced]) and the six digits are
     * on screen. **Nothing is persisted in this state**: the pairing does not exist until
     * [PairingFlow.confirm] is called with the human's answer.
     *
     * @property raced the relay answered 409 rather than 201, so a completion was already stored
     *   for this pairing. That is *ambiguous by construction* — see rule 2 on [PairingFlow] — and
     *   the confirm code is what resolves it. A caller should say so more loudly in this state,
     *   and must not auto-confirm.
     */
    data class AwaitingConfirmation(val confirmCode: String, val raced: Boolean) : PairingStep

    /** The human confirmed the codes match. This is the only value carrying key material out. */
    data class Paired(val pairing: PairedPairing) : PairingStep

    data class Aborted(val reason: PairingAbort, val inviteError: PairingError? = null) : PairingStep
}

/**
 * Which bearer to present to the relay across the provisional → final handover (§5.2.3).
 *
 * §5.2.3 gives rotation to the **engine**: it calls `POST /create` again with the old bearer and
 * `{"rotate_to": …}`. The phone therefore cannot observe the moment the channel's stored hash
 * changes — it can only discover it by being refused. This class is that discovery, written down
 * once instead of re-improvised at each call site.
 *
 * Three rules, and each has a silent wrong version:
 *
 * 1. **Start on the provisional token.** It is what the channel was bootstrapped with (§5.2.1),
 *    and it is what the engine is still using while it collects the completion. A phone that
 *    opens on the final token gets a 401 that is indistinguishable from an unpaired channel.
 * 2. **A 401 means try the other one**, because a 401 during the handover window is a *timing*
 *    answer, not a credential answer.
 * 3. **Once a call authenticated with the final token succeeds, never fall back.** Rotation is
 *    one-way and idempotent (§5.2.3), so after it there is no state in which the provisional
 *    token is correct again. A ladder that keeps falling back would turn "this pairing was
 *    revoked" — a 401 the user needs to see — into an auth blip that retries forever against a
 *    token derived from a secret the engine burned. Losing a pairing silently is the failure this
 *    rule exists to prevent.
 *
 * Not thread-safe: it belongs to whatever coroutine owns the transport, exactly like [SyncPump].
 */
class RelayTokenLadder(
    private val provisional: String,
    private val final: String,
) {
    private var promotedValue = false

    /** True once a call carrying the final token has been accepted. One-way, per rule 3. */
    val promoted: Boolean get() = promotedValue

    /** The bearer to present on the next call. */
    fun bearer(): String = if (promotedValue) final else provisional

    /**
     * Record that a call carrying [bearer] was accepted. Promotes on the first accepted final
     * token and is a no-op afterwards.
     */
    fun accepted(bearer: String) {
        if (bearer == final) promotedValue = true
    }

    /**
     * Record a 401 on a call carrying [bearer], and return the bearer to try next — or `null`
     * when there is nothing left to try and the caller must surface the refusal.
     *
     * Returns `null` after promotion (rule 3): a refused final token is a revoked pairing, and
     * the caller is meant to say so rather than shuffle credentials.
     */
    fun unauthorised(bearer: String): String? = when {
        promotedValue -> null
        bearer == provisional -> final
        else -> null
    }
}

/**
 * The phone's pairing attempt (§5.2.2), with every decision it makes kept in `:core`.
 *
 * This is S3's logic half. What is left for `:app` after this class exists is I/O and rendering:
 * CameraX + ML Kit to turn a camera frame into the QR string, an Android Keystore ECDSA P-256 key
 * to supply [deviceSigPublic], a `java.security` keypair generator for [ephemeral], and three
 * screens (scan → compare codes → done). The split is deliberate and matches [SyncPump]'s: the
 * interesting parts below are ordering rules, and ordering rules written in `:app` can only be
 * exercised by a machine with an Android SDK and an emulator, which the sessions doing this work
 * do not have (B-4, B-7).
 *
 * One attempt:
 *
 * ```
 * begin(qr) → PairingSession.parseInvite     (§5.2: refuse loudly, never downgrade)
 *           → PairingSession.buildCompletion (ECDH + HKDF + seal, exactly once)
 *           → POST /v1/{pairing}/pair        (provisional bearer, §5.2.1)
 *           → AwaitingConfirmation(code)     ← the human compares against the desktop
 * confirm(matched) → Paired | Aborted(CODE_MISMATCH)
 * ```
 *
 * ## Four rules this class exists to hold
 *
 * **1. The completion is derived exactly once per invite, and a retry re-sends it verbatim.**
 * [retry] posts the same bytes; it does not rebuild. Regenerating the ephemeral key or the nonce
 * for a retry breaks pairing in two different silent ways depending on what the relay did with
 * the first attempt. If the first body landed and is still stored, the retry gets 409, the engine
 * later collects **body #1**, and derives keys against an ephemeral public key the phone has
 * already discarded — the phone shows a confirm code that cannot match, with nothing on either
 * screen explaining why. If the first body landed and was already collected, the engine has burned
 * the one-time secret against body #1, so body #2 is refused (`pairing_unknown`, §5.2.2) and the
 * phone waits for a confirmation the engine will never display. Neither throws; neither logs.
 *
 * **2. A 409 on submit is ambiguous by construction, and this class refuses to guess.** The
 * obvious reading — "a completion is already stored, so somebody else beat us" — is wrong often
 * enough to matter, because [RelayClient] retries transport failures internally: attempt 1 can
 * store the completion and lose the response, and attempt 2 then sees the relay's own 409. So a
 * 409 is *either* our own body or a stranger's, and no information available to the phone
 * separates them. Both plausible resolutions are wrong: treating it as failure aborts a perfectly
 * good pairing every time the network hiccups, and treating it as success hides a genuine race.
 * The resolution already exists in the protocol — the confirm code is derived from `ikm`, so it
 * matches the desktop **iff** the stored completion is ours — so the flow proceeds to
 * [PairingStep.AwaitingConfirmation] with [PairingStep.AwaitingConfirmation.raced] set and lets
 * the human arbitrate. This is what §5.2's "the confirmation step catches a raced completion" is
 * for; it is load-bearing here rather than decorative.
 *
 * **3. Nothing leaves this class until the human confirms, and a mismatch is terminal.** The keys
 * and the device key are reachable only through [PairingStep.Paired], which only [confirm] can
 * produce. A mismatch cannot be retried — the engine burned the one-time secret on the completion
 * it accepted (§5.2.2), so the honest next step is a fresh invite on the desktop, not another
 * attempt against a dead secret. It is also reported as its own [PairingAbort.CODE_MISMATCH]
 * rather than folded into a cancel, because the two mean opposite things about whether an attacker
 * is present.
 *
 * **4. The phone never rotates the relay token.** §5.2.3 assigns rotation to the engine, and this
 * class issues exactly one relay call — `POST /pair`. [RelayClient.create] is reachable from the
 * phone (it takes a `rotate_to`), and calling it here would be a one-line, one-way way to destroy
 * a pairing: the engine still holds the provisional bearer while it collects the completion, so a
 * phone that rotates first locks the engine out of `GET /pair` with a 401 it has no way to read as
 * "the phone jumped the gun". The completion is stored, one-shot, and unreadable; the secret is
 * spent; nothing on either screen says so. The handover the phone *does* participate in is
 * [RelayTokenLadder], which is discovery, not rotation.
 *
 * ## Secret hygiene
 *
 * The invite's one-time secret and the ephemeral private scalar are zeroised as soon as the
 * completion is built — before any network call, because everything derived from them
 * ([PairingKeys], the provisional token) is already in hand by then and nothing later needs the
 * originals. The arrays are the caller's too, which is the point: the buffer the QR decoder handed
 * over comes back blank.
 *
 * @param relayFor builds a [RelayClient] for the invite's relay, pairing id and bearer. Injected
 *   because none of those three are known until the QR is parsed.
 * @param ephemeral supplies this attempt's ECDH keypair.
 * @param deviceSigPublic supplies the **public** half of the Android Keystore signing key (§5.4).
 *   A function rather than a value so `:app` can generate the key lazily, and public-only so this
 *   API cannot tempt anyone into making a non-exportable key exportable.
 * @param nonces supplies the 12-byte completion nonce (§5.1: CSPRNG, never counter-derived).
 * @param clock produces the completion's RFC 3339 `ts`.
 */
class PairingFlow(
    private val relayFor: (baseUrl: String, pairing: String, bearer: String) -> RelayClient,
    private val ephemeral: () -> EphemeralKeyPair,
    private val deviceSigPublic: () -> ByteArray,
    private val nonces: () -> ByteArray,
    private val clock: () -> String,
) {
    private var invite: PairingInvite? = null
    private var completion: PairingSession.Completion? = null
    private var deviceSigPub: ByteArray? = null
    private var relay: RelayClient? = null
    private var raced = false
    private var settled = false

    /** How many times the completion has been built. Exactly one, for the life of an attempt. */
    private var buildCount = 0

    /** Exposed so a test — and an auditor — can assert rule 1 structurally. */
    val completionBuilds: Int get() = buildCount

    /**
     * Scan a QR, build the completion, and submit it.
     *
     * Returns [PairingStep.AwaitingConfirmation] on success *and* on a 409 (rule 2); every other
     * relay answer aborts. On [PairingAbort.RELAY_UNAVAILABLE] the attempt is still alive and
     * [retry] may be called.
     */
    suspend fun begin(qrPayload: String): PairingStep {
        check(invite == null) { "begin() is once per attempt; use retry() to re-send" }

        val parsed = PairingSession.parseInvite(qrPayload)
        if (parsed is PairingParse.Rejected) {
            // Deliberately not `settled`: nothing was derived, nothing was sent, and no secret was
            // spent. A refused QR leaves the attempt untouched so the user can simply scan again —
            // marking it settled here would poison the next scan's confirm() with a stale flag.
            return PairingStep.Aborted(PairingAbort.INVITE_REJECTED, parsed.error)
        }
        val accepted = (parsed as PairingParse.Ok).invite
        invite = accepted

        val keyPair = ephemeral()
        val sigPub = deviceSigPublic()
        deviceSigPub = sigPub

        val built = PairingSession.buildCompletion(
            invite = accepted,
            phonePrivateScalar = keyPair.privateScalar,
            phonePublicUncompressed = keyPair.publicUncompressed,
            deviceSigPublicUncompressed = sigPub,
            nonce = nonces(),
            timestamp = clock(),
        )
        buildCount++
        completion = built

        // Everything derived from them is already held; the originals are dead weight from here on.
        keyPair.privateScalar.fill(0)
        accepted.secret.fill(0)

        relay = relayFor(accepted.relay, accepted.pairing, built.provisionalRelayToken)
        return submit()
    }

    /**
     * Re-send the completion built by [begin], byte for byte (rule 1).
     *
     * Only meaningful after [PairingAbort.RELAY_UNAVAILABLE]: the other aborts are terminal, and
     * [PairingStep.AwaitingConfirmation] has nothing left to send.
     */
    suspend fun retry(): PairingStep {
        check(completion != null) { "retry() before begin()" }
        check(!settled) { "this attempt is already settled" }
        return submit()
    }

    /**
     * The human's answer to "do these six digits match the desktop?".
     *
     * The only path that yields key material. `false` is [PairingAbort.CODE_MISMATCH] and is
     * terminal (rule 3).
     */
    fun confirm(codesMatch: Boolean): PairingStep {
        val built = completion ?: error("confirm() before begin()")
        val accepted = invite ?: error("confirm() before begin()")
        check(!settled) { "this attempt is already settled" }
        settled = true

        if (!codesMatch) return PairingStep.Aborted(PairingAbort.CODE_MISMATCH)

        return PairingStep.Paired(
            PairedPairing(
                pairing = accepted.pairing,
                suite = accepted.suite,
                relayBaseUrl = accepted.relay,
                keys = built.keys,
                deviceSigPublicUncompressed = deviceSigPub!!,
                tokens = RelayTokenLadder(built.provisionalRelayToken, built.keys.relayToken),
            ),
        )
    }

    /** The human backed out. Distinct from [PairingAbort.CODE_MISMATCH] on purpose (rule 3). */
    fun cancel(): PairingStep {
        settled = true
        return PairingStep.Aborted(PairingAbort.CANCELLED)
    }

    // ---------------------------------------------------------------- internals

    private suspend fun submit(): PairingStep {
        val built = completion!!
        return when (relay!!.submitPairing(built.bodyJson)) {
            is RelayResult.Ok -> PairingStep.AwaitingConfirmation(built.confirmCode, raced)

            // Rule 2. Not a verdict — a question the confirm code is about to answer. The relay's
            // pairing 409 carries no `latest` (it is `{"error":"exists"}`), so unlike a push
            // conflict there is no number here to reconcile against — the human is the tiebreak.
            is RelayResult.Conflict -> {
                raced = true
                PairingStep.AwaitingConfirmation(built.confirmCode, raced = true)
            }

            RelayResult.Unauthorised, RelayResult.PairingUnknown -> {
                settled = true
                PairingStep.Aborted(PairingAbort.RELAY_REFUSED)
            }

            // A completion the relay called too large is a bug in this build, not a race: the
            // body is a fixed handful of base64url fields and cannot approach any limit. Terminal.
            RelayResult.TooLarge -> {
                settled = true
                PairingStep.Aborted(PairingAbort.RELAY_REFUSED)
            }

            is RelayResult.Unavailable -> PairingStep.Aborted(PairingAbort.RELAY_UNAVAILABLE)
        }
    }
}
