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

    /** Envelope hard limit. Larger is rejected before any crypto work. */
    const val MAX_ENVELOPE_BYTES = 1024 * 1024

    /** AES-256-GCM, decided at gate P0-CIPHER. See Sync-Protocol.md section 5.1. */
    const val CIPHER = "AES-256-GCM"
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16

    /**
     * HKDF info strings for the two directional keys. Distinct by direction so a captured
     * envelope cannot be replayed back at its sender.
     */
    const val HKDF_INFO_ENGINE_TO_PHONE = "careerseeker/v1/e2p"
    const val HKDF_INFO_PHONE_TO_ENGINE = "careerseeker/v1/p2e"

    /** Signature domain separator for phone-originated commands. Sync-Protocol.md section 5.4. */
    const val COMMAND_SIG_PREFIX = "careerseeker/v1/cmd"
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
 * Payload kinds shipping in v1.
 *
 * [Reserved] kinds are claimed so a future L2 cannot collide with v1 traffic, and a v1
 * receiver must reject them. That rejection is the guard that stops the phone acquiring
 * engine control before the signing and audit story has been externally audited.
 */
enum class PayloadKind(val wire: String) {
    // engine -> phone
    SNAPSHOT("snapshot"),
    DELTA("delta"),
    DOC("doc"),
    EVIDENCE("evidence"),
    HEARTBEAT("heartbeat"),
    CONFLICT("conflict"),
    ENTITLEMENT_ACK("entitlement_ack"),

    // phone -> engine
    DOC_EDIT("doc_edit"),
    OUTCOME("outcome"),
    ENTITLEMENT("entitlement"),
    PULL_REQUEST("pull_request"),

    // both
    ERROR("error"),
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
    }
}

/** Rejection reasons. Sync-Protocol.md section 7.2. */
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
