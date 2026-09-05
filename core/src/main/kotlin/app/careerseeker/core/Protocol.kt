package app.careerseeker.core

/**
 * Sync Protocol v1 constants and envelope types.
 *
 * Normative source: `docs/Sync-Protocol.md` in ShivaClaw/careerseeker. Anything here that
 * disagrees with that document is a bug in this file.
 *
 * P0 SCAFFOLD. This module holds the shape of the protocol and the rules that do not
 * require crypto. The AEAD codec and the shared-vector conformance tests land in P1,
 * reading `docs/sync-vectors/v1/` -- the same files the C# `SyncHarness` already consumes,
 * which is how the two implementations are kept from drifting apart.
 *
 * No Android imports belong in this file, or anywhere in `:core`. See
 * `checkCoreIsAndroidFree` in the root build script.
 */
object Protocol {
    const val VERSION = 1

    /**
     * The §3.1 size cap, measured on the **decoded ciphertext** — the AEAD output including
     * its 16-byte tag, after base64url decoding. Larger is rejected before any crypto work.
     *
     * **Not the envelope, despite the name.** §3.1 is explicit that a receiver measures those
     * decoded bytes, *"not the length of the JSON envelope and not the length of the base64url
     * text"*, and `MUST NOT exceed` makes exactly this value the largest **legal** ciphertext
     * rather than the first illegal one. The name is kept because it is the shared corpus's:
     * `index.json` carries `max_envelope_bytes`, `invalid-oversized` is specified as *"a
     * ciphertext of `max_envelope_bytes + 1` **decoded** bytes"*, and renaming it here would
     * split the phone from the manifest it is pinned against for no wire-visible gain.
     *
     * The KDoc that stood here until the seventy-ninth run read *"Envelope hard limit"* — the
     * P0 wording S5 retired. §3.1 records why: that sentence *"described something neither
     * implementation ever measured"*, both having always tested the decoded ciphertext, so the
     * prose moved to the code rather than the reverse. The stale copy mattered because §4.4
     * instructs a future chunker to size against exactly this number, and a chunker sized
     * against the envelope would be sized against the wrong quantity by ~33%.
     *
     * The boundary itself is pinned in `EnvelopeReceiverTest` — a ciphertext of exactly this
     * many decoded bytes is accepted, one more is `too_large`, and the acceptance case is what
     * distinguishes this unit from the two §3.1 forbids.
     */
    const val MAX_ENVELOPE_BYTES = 1024 * 1024

    /** AES-256-GCM, decided at gate P0-CIPHER. See Sync-Protocol.md section 5.1. */
    const val CIPHER = "AES-256-GCM"
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16

    /** Pairing suite for v1 (gate P1-CURVE). The hybrid PQ suite is a reserved bump. */
    const val SUITE = "p256-hkdf-sha256"
    const val SUITE_HYBRID_RESERVED = "p256+mlkem768-hkdf-sha256"

    /**
     * HKDF info strings. The two directional keys are distinct by direction so a captured
     * envelope cannot be replayed back at its sender; the relay token and confirm code are
     * derived rather than invented so both endpoints agree and the relay only recognises.
     */
    const val INFO_ENGINE_TO_PHONE = "careerseeker/v1/e2p"
    const val INFO_PHONE_TO_ENGINE = "careerseeker/v1/p2e"
    const val INFO_RELAY_TOKEN = "careerseeker/v1/relay-token"
    const val INFO_CONFIRM = "careerseeker/v1/confirm"
    const val BOOTSTRAP_SALT = "careerseeker/v1/bootstrap"
    const val PAIR_AAD_PREFIX = "careerseeker/v1/pair"

    /** Signature domain separator for phone-originated commands. Sync-Protocol.md section 5.4. */
    const val COMMAND_SIG_PREFIX = "careerseeker/v1/cmd"

    /**
     * Phone-originated kinds that change engine state and therefore REQUIRE the
     * envelope-level device signature (Sync-Protocol.md §5.4).
     */
    val STATE_CHANGING_KINDS = setOf("doc_edit", "outcome", "entitlement")
}

enum class Direction(val wire: String) {
    ENGINE_TO_PHONE("e2p"),
    PHONE_TO_ENGINE("p2e"),
    ;

    companion object {
        fun fromWire(value: String): Direction? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Which way a [PayloadKind] travels. Sync-Protocol.md section 4.3's two tables.
 *
 * This was a `// engine -> phone` comment until the fifty-ninth run. A comment cannot be
 * read by [PayloadKind.ENGINE_TO_PHONE_KINDS], so the set that decides what the phone must
 * be prepared to receive had to be written out a second time — and two lists of the same
 * thing, in one file, is the drift this program keeps finding in other files.
 */
enum class KindFlow { ENGINE_TO_PHONE, PHONE_TO_ENGINE, BOTH }

/**
 * Payload kinds shipping in v1.
 *
 * [Reserved] kinds are claimed so a future L2 cannot collide with v1 traffic, and a v1
 * receiver must reject them. That rejection is the guard that stops the phone acquiring
 * engine control before the signing and audit story has been externally audited.
 *
 * ## Every constant declares its [flow], and every engine→phone constant is classified
 *
 * A new kind cannot be added here without stating which way it travels, and — if it travels
 * to the phone — without landing in exactly one of the three sets below. `PayloadKindCoverageTest`
 * fails until it does. That is deliberate, and it is B-19's neighbourhood: `entitlement_ack`
 * spent nine days in this enum with a documented applier and no production caller, because
 * nothing anywhere required the question *"and who receives it?"* to be answered when a kind
 * was added. The partition does not answer it — see the warning on [ROUTED_OUTSIDE_REPLICA] —
 * but it does force it to be asked.
 */
enum class PayloadKind(val wire: String, val flow: KindFlow) {
    // engine -> phone
    SNAPSHOT("snapshot", KindFlow.ENGINE_TO_PHONE),
    DELTA("delta", KindFlow.ENGINE_TO_PHONE),
    DOC("doc", KindFlow.ENGINE_TO_PHONE),
    EVIDENCE("evidence", KindFlow.ENGINE_TO_PHONE),
    HEARTBEAT("heartbeat", KindFlow.ENGINE_TO_PHONE),
    CONFLICT("conflict", KindFlow.ENGINE_TO_PHONE),
    ENTITLEMENT_ACK("entitlement_ack", KindFlow.ENGINE_TO_PHONE),

    // phone -> engine
    DOC_EDIT("doc_edit", KindFlow.PHONE_TO_ENGINE),
    OUTCOME("outcome", KindFlow.PHONE_TO_ENGINE),
    ENTITLEMENT("entitlement", KindFlow.PHONE_TO_ENGINE),
    PULL_REQUEST("pull_request", KindFlow.PHONE_TO_ENGINE),

    // both
    ERROR("error", KindFlow.BOTH),
    ;

    companion object {
        fun fromWire(value: String): PayloadKind? = entries.firstOrNull { it.wire == value }

        /**
         * Reserved for a future L2 and rejected in v1. `kill` in particular is reserved
         * rather than shipped: a remote stop command is a control-plane action, and the
         * product stays L1 until that is audited.
         */
        val RESERVED_FOR_L2 = setOf(
            "state_change", "gate_request", "gate_resolve", "kill",
            "config_change", "lesson_proposal", "metric",
        )

        /**
         * Every kind that can arrive **at the phone**. Derived from [flow], never listed a
         * second time.
         *
         * ## `!= PHONE_TO_ENGINE`, not `== ENGINE_TO_PHONE`, and the difference was a live gap
         *
         * Until the seventy-fourth run this read `it.flow == KindFlow.ENGINE_TO_PHONE`, which
         * silently excluded every [KindFlow.BOTH] kind — and §4.3's engine→phone table has
         * **eight** rows, not seven: `error` is in it, by name. So the set that decides "what
         * the phone must be prepared to receive" could not see the one kind that carries the
         * engine's reason for rejecting what the phone sent.
         *
         * The consequence was not that the set was merely short. `PayloadKindCoverageTest`
         * enumerates *this* set to require that every receivable kind declare a destination —
         * the guard written for B-19 — so `error` was exempt from the guard **because of how
         * the guard's input was derived**, and the exemption was invisible from either end:
         * the set looked complete, and the test that pins it against §4.3 asserted the same
         * seven names, so doc and code agreed with each other and both disagreed with the
         * normative table. Widening it here is what makes the omission fail a test instead of
         * reading as a decision.
         */
        val ENGINE_TO_PHONE_KINDS: Set<PayloadKind> =
            entries.filter { it.flow != KindFlow.PHONE_TO_ENGINE }.toSet()

        /**
         * Kinds the `:app` [ReplicaApplier] projects into the Room replica.
         *
         * This mirrors the `when` in `:app`'s `EnvelopeApplier`, and mirroring is the honest
         * word: `:core` is Android-free, so nothing here can read that `when`. What this set
         * buys is that the mirror has one location instead of three KDoc paragraphs, and that
         * a kind added to the enum has to be placed against it.
         */
        val PROJECTED_BY_REPLICA: Set<PayloadKind> = setOf(SNAPSHOT, DELTA, HEARTBEAT, EVIDENCE)

        /**
         * Engine→phone kinds that a correct build acts on **somewhere other than the replica**.
         *
         * **This set is not a guarantee that anything constructs that route.** `entitlement_ack`
         * was in exactly this position for nine days: [EntitlementAckApplier] existed, was
         * documented as the phone's only unlock path, and had no production caller in either
         * module. [EntitlementRoutingApplier] is the route now; **the composition root that
         * builds it is `:app` and does not exist yet (B-19)**. Membership here means "the
         * replica's `else` branch is the wrong destination for this kind" — a statement about
         * where it must not go, which is what was missing, not a claim about where it does go.
         */
        val ROUTED_OUTSIDE_REPLICA: Set<PayloadKind> = setOf(ENTITLEMENT_ACK)

        /**
         * Engine→phone kinds v1 accepts and deliberately drops.
         *
         * `doc` is not emitted in v1 at all (§4.3's table says so), and `conflict` rejects a
         * `doc_edit` the phone cannot send until the P3 editing surface exists. Both reaching
         * the replica's `else` branch is correct, and saying so here is what stops the next
         * reader from "fixing" it.
         */
        val NOT_PROJECTED_IN_V1: Set<PayloadKind> = setOf(DOC, CONFLICT)

        /**
         * Receivable kinds that reach the replica's `else` branch because **nobody decided**
         * where they should go. A defect marker, not a destination.
         *
         * This set exists so the partition can be complete without the completeness being a
         * lie. The other three sets each assert a decision: `snapshot` is projected, an
         * `entitlement_ack` is routed past the replica, `doc` and `conflict` are dropped **for
         * stated reasons** — `doc` is not emitted in v1 at all and `conflict` answers a
         * `doc_edit` the phone cannot yet send. Placing `error` beside `doc` would have
         * borrowed that sentence for a case that has never had one, and a later reader would
         * have had no way to tell the two apart. That laundering is precisely how
         * `entitlement_ack` spent nine days looking handled (B-19), so it is worth a fourth
         * set to avoid repeating it one kind along.
         *
         * **What is true of `error` today**, and all of it is executed rather than reasoned
         * (`PayloadKindCoverageTest`, `EnvelopeReceiverTest`): §4.3 lists it engine→phone;
         * `Protocol.cs` has it in `ShippingKinds`; [EnvelopeReceiver] therefore **accepts** an
         * authentic one and reports `kind = "error"`; [SyncPump] hands it to the single
         * [ReplicaApplier]; `:app`'s `when` covers four kinds and returns
         * [ApplyDisposition.IGNORED] for the rest. The engine's only channel for saying *"I
         * rejected what you sent, and here is the §7.2 code"* is consumed by nothing and
         * surfaced nowhere.
         *
         * **What should happen instead is not decided here.** Whether the phone retries,
         * surfaces the code, or goes on dropping it is product behaviour with a user-visible
         * failure mode — an `outcome` the engine refused reads on the phone as an `outcome`
         * that landed — and this program's standing rule sends that to a gate rather than to a
         * unilateral fix. Filed as **PQ-ERR-1** in `docs/protocol-questions.md`.
         *
         * **Do not park a new kind here to make the build go green.** Its membership is pinned
         * to exactly `{error}` by its own test, so adding a second occupant means editing an
         * assertion that says, in its failure message, that you are doing so.
         */
        val RECEIVED_WITHOUT_A_DESTINATION: Set<PayloadKind> = setOf(ERROR)
    }
}

/**
 * Rejection reasons. Sync-Protocol.md section 7.2.
 *
 * ## This enum is a transcription, and it was short by a row for twenty-eight days
 *
 * §7.2's table is normative and this is a hand copy of it. The copy was written on
 * 2026-07-22 (`6bdddbd`) with nine rows. Two days later, on 2026-07-24, the engine added a
 * tenth — `unimplemented` — amending the spec and `src/Sync/Protocol.cs` **in one commit**
 * (`e1e7a90`), exactly as the drift rule requires. Nothing carried it across the repo
 * boundary, and nothing here could notice: `ErrorCode.entries` was never enumerated by any
 * test, so deleting a genuine §7.2 row left `:core:test` green at 338/0 (measured, run 75).
 * The vocabulary was unguarded in full, not merely incomplete.
 *
 * [ProtocolTest.section72Codes] now pins the whole table by hand. Hand-transcribed on
 * purpose: the seventy-fourth run's finding was that a derivation compared against itself
 * agrees with itself and disagrees with the document, so one side of this comparison must
 * not be derived from the other.
 */
enum class ErrorCode(val wire: String) {
    VERSION_UNSUPPORTED("version_unsupported"),
    REPLAY_REJECTED("replay_rejected"),
    DECRYPT_FAILED("decrypt_failed"),
    UNKNOWN_KIND("unknown_kind"),
    KEY_UNKNOWN("key_unknown"),
    BAD_SIGNATURE("bad_signature"),
    REV_CONFLICT("rev_conflict"),
    PAIRING_UNKNOWN("pairing_unknown"),
    TOO_LARGE("too_large"),

    /**
     * A recognised **shipping** kind the receiver does not yet handle — §7.2's tenth row.
     *
     * Engine-emitted. The phone has no case that produces it today: every kind it refuses is
     * refused as [UNKNOWN_KIND] (unrecognised or reserved-for-L2), and the shipping kinds it
     * accepts but drops — `doc`, `conflict`, `error` — are dropped without a reply at all.
     * Present anyway, because this enum's job is to be §7.2, not to be the subset the phone
     * currently reaches. Two consequences follow from that job, and both are why the absence
     * mattered rather than being cosmetic:
     *
     *  - §7.2 states the rule **about the phone**: `unimplemented` is *"distinct from
     *    `unknown_kind` — the kind IS known, so the phone should not treat it as a
     *    version/vocabulary error."* An enum that cannot represent the code cannot honour a
     *    rule about how to read it.
     *  - Whether the phone reads inbound `error` codes at all is **PQ-ERR-1**, open. Today
     *    `error` is accepted and dropped ([PayloadKind.RECEIVED_WITHOUT_A_DESTINATION]), so
     *    nothing parses this string yet. If PQ-ERR-1 is answered "surface the code", a
     *    `code: "unimplemented"` would have resolved to `null` and been indistinguishable
     *    from an unrecognised one — the precise confusion §7.2's row exists to forbid.
     *
     * So this is added as **vocabulary**, and it changes no behaviour. It is not a decision
     * about PQ-ERR-1, and adding it must not be read as one.
     */
    UNIMPLEMENTED("unimplemented"),
}

/**
 * The envelope header: authenticated but not encrypted.
 *
 * [ts] is advisory and must never drive a security decision -- freshness comes from
 * sequence numbers and the pairing lifetime, never from comparing clocks.
 */
data class EnvelopeHeader(
    val v: Int,
    val pairing: String,
    val dir: Direction,
    val seq: Long,
    val ts: String,
    val keyId: String,
) {
    /**
     * Additional authenticated data, byte-identical to the engine's construction.
     *
     * A fixed ASCII string rather than canonical JSON, deliberately: two independent
     * implementations have to agree exactly, and JSON canonicalization (key order, number
     * formatting, Unicode escaping) is a well-known source of cross-language mismatch.
     *
     * Field order is normative. Changing it silently breaks every paired device.
     */
    fun aad(): String =
        "v=$v|pairing=$pairing|dir=${dir.wire}|seq=$seq|ts=$ts|key_id=$keyId"
}

/** Pairing ids are `p_` + 16 base64url chars. Opaque, never derived from anything personal. */
private val PAIRING_ID = Regex("^p_[A-Za-z0-9_-]{16}$")

fun isValidPairingId(value: String): Boolean = PAIRING_ID.matches(value)

/**
 * Tracks the highest accepted sequence number per direction.
 *
 * Gaps are legitimate -- the relay purges on a TTL -- so a gap must not stall the stream.
 * Only regression is a replay.
 */
class SequenceTracker {
    private val highest = mutableMapOf<Direction, Long>()

    fun highestAccepted(dir: Direction): Long = highest[dir] ?: 0L

    /** Returns true and records the seq if acceptable; false if it is a replay. */
    fun accept(dir: Direction, seq: Long): Boolean {
        if (seq <= highestAccepted(dir)) return false
        highest[dir] = seq
        return true
    }
}
