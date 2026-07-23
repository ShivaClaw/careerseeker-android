package app.careerseeker.dashboard.replica

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The Room replica (P2-Runbook §2.3): the phone-side mirror of dashboard state. The UI is a
 * pure projection of these tables, which is what makes every screen offline-readable — the
 * replica answers whether or not the engine is reachable (spec Part 6).
 *
 * Untrusted-text rule: string columns here (company, title, state…) are display-only. They
 * arrive as short structured fields — the engine never publishes a raw posting body in P2 —
 * and the screens render them as inert text, never interpolate them into anything.
 *
 * Erasable on unpair: the whole database is dropped when the pairing ends; everything in it
 * is re-syncable from an engine snapshot.
 */
@Entity(tableName = "applications")
data class ApplicationRow(
    @PrimaryKey val id: String,
    val state: String,
    val company: String,
    val title: String,
    val score: Int,
    /** The e2p envelope seq that last touched this row — recency for list ordering. */
    val updatedSeq: Long,
)

/** One discovered job as the Jobs screen renders it. Flags are display-only booleans. */
@Entity(tableName = "jobs")
data class JobRow(
    @PrimaryKey val id: String,
    val company: String,
    val title: String,
    val repost: Boolean,
    val injectionFlag: Boolean,
    val updatedSeq: Long,
)

/** Single-row (id = 1) mirror of the engine's live tallies. */
@Entity(tableName = "counters")
data class CountersRow(
    @PrimaryKey val id: Int = 1,
    val discovered: Long,
    val acted: Long,
    val drafted: Long,
    val blocked: Long,
    val rejected: Long,
    val errors: Long,
    val cycles: Long,
)

/**
 * Audit-event metadata for the Evidence screen: seq/ts/actor/kind/entity only, never event
 * payload bodies. Populated by the demo fixture today; the engine's `evidence` payload
 * builder is not defined yet, so the applier deliberately has no branch for it — a
 * speculative parser for an unshipped shape would just be a silent-drift generator.
 */
@Entity(tableName = "evidence_events")
data class EvidenceEventRow(
    @PrimaryKey val seq: Long,
    val ts: String,
    val actor: String,
    val kind: String,
    val entity: String,
    val entityId: String,
)

/**
 * One tailored document as the read-only detail screen renders it (resume, cover letter,
 * answers). P2 renders; editing is P3's invariant-sensitive work. Populated by the demo
 * fixture today — the engine's `doc` payload publisher is not built yet, so the applier has
 * no branch for it and [text] can only come from fixtures until it is. Display-only text,
 * rendered inert, never interpolated.
 */
@Entity(tableName = "documents", primaryKeys = ["appId", "kind"])
data class DocumentRow(
    val appId: String,
    /** `resume` | `cover_letter` | `answers`, mirroring the protocol's doc_kind. */
    val kind: String,
    val text: String,
    val rev: Long,
)

/**
 * Single-row (id = 1) sync bookkeeping. [highestAppliedE2pSeq] persists across process
 * restarts — the in-memory receiver's replay window resets on restart, so this row is what
 * makes re-applying an old envelope after a relaunch a no-op rather than a regression.
 * [lastSeenTs] is the engine's own envelope timestamp (advisory, display-only — never a
 * security decision), which is what an honest "engine last seen" label wants.
 */
@Entity(tableName = "sync_state")
data class SyncStateRow(
    @PrimaryKey val id: Int = 1,
    val highestAppliedE2pSeq: Long,
    val lastSeenTs: String?,
    val lastCycle: Long?,
    /** True while the replica holds fixture data; any applied real envelope clears it. */
    val demoMode: Boolean,
    /**
     * The engine's audit-chain verification verdict as last reported. Null = not reported
     * by any applied payload — the Evidence screen says "unknown" rather than guessing.
     * Only the fixture sets it today; the wire `evidence` payload is not defined yet.
     */
    val auditOk: Boolean? = null,
)
