package app.careerseeker.dashboard.replica

import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** What happened to one decrypted envelope. */
sealed interface ApplyResult {
    /** The payload changed the replica. */
    data class Applied(val kind: String) : ApplyResult

    /** Envelope seq is at or below the persisted high-water mark — already applied. */
    data object SkippedStale : ApplyResult

    /** A valid protocol kind the replica does not project yet (doc, conflict… — see §4.3). */
    data class Ignored(val kind: String) : ApplyResult

    /** The plaintext did not parse as the expected payload shape. Nothing changed. */
    data object Malformed : ApplyResult

    /**
     * A `delta` arrived before any full `snapshot`. Nothing changed, and the caller should ask
     * the engine to re-publish from seq 0 (`pull_request`, §4.3).
     *
     * This is not an error — it is the honest response to arriving mid-stream. See
     * [SyncStateRow.snapshotSeen].
     */
    data object AwaitingSnapshot : ApplyResult
}

/**
 * Projects decrypted engine→phone payloads into the Room replica (P2-Runbook §2.3). The
 * crypto boundary is `:core`'s `EnvelopeReceiver` — by the time bytes reach this class they
 * are authenticated plaintext from the paired engine; this class owns only the projection,
 * which is why it lives in `:app` (it touches Room) while the receiver stays in `:core`.
 *
 * Field names mirror the engine's `SyncPayloads` builders exactly (snapshot/delta/heartbeat,
 * Sync-Protocol.md §4.3); those shapes are pinned by the C# `SyncHarness` on the engine side
 * and by this class's tests on the phone side.
 *
 * Application order: the receiver rejects replays within a process, but its window resets on
 * restart, so the applier re-checks the envelope seq against the replica's PERSISTED
 * high-water mark inside the same transaction that applies the payload. Re-delivery after a
 * relaunch is a no-op, and a crash between decrypt and commit leaves the mark unmoved so the
 * envelope can be re-applied cleanly.
 */
class EnvelopeApplier(private val db: ReplicaDb) {

    /** A fully validated payload, parsed before anything is written. */
    private sealed interface Parsed {
        data class Snapshot(val counters: CountersRow, val apps: List<ApplicationRow>, val jobs: List<JobRow>) : Parsed
        data class Delta(val counters: CountersRow, val apps: List<ApplicationRow>, val jobs: List<JobRow>) : Parsed
        data class Heartbeat(val counters: CountersRow) : Parsed
        data class Evidence(val auditOk: Boolean, val events: List<EvidenceEventRow>) : Parsed
    }

    suspend fun apply(seq: Long, envelopeTs: String, kind: String, plaintext: ByteArray): ApplyResult {
        val body = parseBody(plaintext) ?: return ApplyResult.Malformed

        return db.withTransaction {
            val dao = db.dao()
            val state = dao.syncStateNow()
            if (seq <= (state?.highestAppliedE2pSeq ?: 0L)) return@withTransaction ApplyResult.SkippedStale

            // Parse FULLY before writing anything: a malformed payload must change nothing, and
            // the demo wipe below must never fire for a payload that then fails validation.
            val parsed: Parsed = when (kind) {
                "snapshot" -> parseSnapshot(body, seq)
                "delta" -> parseDelta(body, seq)
                "heartbeat" -> parseHeartbeat(body)
                "evidence" -> parseEvidence(body)
                else -> return@withTransaction ApplyResult.Ignored(kind)
            } ?: return@withTransaction ApplyResult.Malformed

            // A delta is the recent WINDOW, not the pipeline (§4.3.1). Applied to a replica
            // that has never held a snapshot, it would present a handful of rows as the whole
            // truth. The phone genuinely can arrive mid-stream -- it pulls from seq 0 and the
            // relay's TTL may already have purged the snapshot -- so the snapshot is awaited,
            // never reconstructed from deltas. The caller asks for one with `pull_request`.
            //
            // Placed AFTER validation so a malformed delta is still reported as malformed
            // (rejecting for the wrong reason hides the real defect), and BEFORE the demo wipe
            // so a refused delta leaves fixture rows and their honest label exactly as they
            // were -- see the note on the wipe below.
            if (parsed is Parsed.Delta && !(state?.snapshotSeen ?: false)) {
                return@withTransaction ApplyResult.AwaitingSnapshot
            }

            // Demo/real boundary (Codex audit finding, 2026-07-24): fixture data must never mix
            // with or masquerade as engine data. The FIRST applied real payload of any kind wipes
            // every fixture-populated table — a delta must not upsert into demo rows, a heartbeat
            // must not clear the demo label while demo rows remain visible, and a snapshot's own
            // wholesale replace does not cover the evidence/documents tables the fixture also
            // seeded. The fixture's auditOk claim dies here too (it was never engine-reported).
            val wasDemo = state?.demoMode ?: false
            if (wasDemo) {
                dao.clearApplications()
                dao.clearJobs()
                dao.clearEvidenceEvents()
                dao.clearDocuments()
            }

            // The audit verdict is only ever set by an `evidence` payload. A full snapshot is a
            // fresh resync whose verdict has not been re-reported, so it reverts to unknown (null)
            // rather than carrying a stale "intact"; deltas/heartbeats preserve the last REAL verdict.
            var auditOk = if (kind == "snapshot" || wasDemo) null else state?.auditOk

            when (parsed) {
                is Parsed.Snapshot -> {
                    // Snapshot is FULL dashboard state: applications and jobs are replaced wholesale.
                    dao.clearApplications()
                    dao.clearJobs()
                    dao.upsertApplications(parsed.apps)
                    dao.upsertJobs(parsed.jobs)
                    dao.upsertCounters(parsed.counters)
                }
                is Parsed.Delta -> {
                    // Delta is the recent window: listed rows are upserted, everything else retained.
                    dao.upsertApplications(parsed.apps)
                    dao.upsertJobs(parsed.jobs)
                    dao.upsertCounters(parsed.counters)
                }
                is Parsed.Heartbeat -> dao.upsertCounters(parsed.counters)
                is Parsed.Evidence -> {
                    // The trail is the engine's current view, not an accumulating log: replace wholesale.
                    dao.clearEvidenceEvents()
                    dao.upsertEvidenceEvents(parsed.events)
                    auditOk = parsed.auditOk
                }
            }

            dao.upsertSyncState(
                SyncStateRow(
                    highestAppliedE2pSeq = seq,
                    lastSeenTs = envelopeTs,
                    lastCycle = body["counters"]?.jsonObject?.get("cycles")?.jsonPrimitive?.longOrNull
                        ?: (body["cycle"]?.jsonPrimitive?.longOrNull ?: state?.lastCycle),
                    demoMode = false, // real engine data replaces any fixture claim
                    auditOk = auditOk,
                    // Latches on the first snapshot and never clears: once a full state has
                    // been received, later deltas are legitimately incremental. The demo
                    // fixture does NOT set it — fixture rows are not a snapshot.
                    snapshotSeen = (kind == "snapshot") || (state?.snapshotSeen ?: false),
                ),
            )
            ApplyResult.Applied(kind)
        }
    }

    // ---- parse phase: pure, null = malformed, nothing written ----

    private fun parseSnapshot(body: JsonObject, seq: Long): Parsed.Snapshot? = Parsed.Snapshot(
        counters = countersOf(body) ?: return null,
        apps = body["applications"]?.jsonArray?.map { appOf(it.jsonObject, seq) ?: return null } ?: return null,
        jobs = body["jobs"]?.jsonArray?.map { jobOf(it.jsonObject, seq) ?: return null } ?: return null,
    )

    private fun parseDelta(body: JsonObject, seq: Long): Parsed.Delta? = Parsed.Delta(
        counters = countersOf(body) ?: return null,
        apps = body["applications"]?.jsonArray?.map { appOf(it.jsonObject, seq) ?: return null } ?: return null,
        jobs = body["jobs"]?.jsonArray?.map { jobOf(it.jsonObject, seq) ?: return null } ?: return null,
    )

    private fun parseHeartbeat(body: JsonObject): Parsed.Heartbeat? =
        Parsed.Heartbeat(countersOf(body) ?: return null)

    /**
     * Evidence carries the engine's audit-chain verdict plus recent event metadata (never event
     * payload bodies).
     */
    private fun parseEvidence(body: JsonObject): Parsed.Evidence? = Parsed.Evidence(
        auditOk = body["audit_ok"]?.jsonPrimitive?.booleanOrNull ?: return null,
        events = body["events"]?.jsonArray?.map { evidenceEventOf(it.jsonObject) ?: return null } ?: return null,
    )

    // ---- payload parsing, field names pinned to the engine's SyncPayloads ----

    private fun parseBody(plaintext: ByteArray): JsonObject? = try {
        Json.parseToJsonElement(plaintext.toString(Charsets.UTF_8))
            .jsonObject["body"]?.jsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun countersOf(body: JsonObject): CountersRow? {
        val c = body["counters"]?.jsonObject ?: return null
        fun n(name: String): Long? = c[name]?.jsonPrimitive?.longOrNull
        return CountersRow(
            discovered = n("discovered") ?: return null,
            acted = n("acted") ?: return null,
            drafted = n("drafted") ?: return null,
            blocked = n("blocked") ?: return null,
            rejected = n("rejected") ?: return null,
            errors = n("errors") ?: return null,
            cycles = n("cycles") ?: return null,
        )
    }

    private fun appOf(o: JsonObject, seq: Long): ApplicationRow? = ApplicationRow(
        id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null,
        state = o["state"]?.jsonPrimitive?.contentOrNull ?: return null,
        company = o["company"]?.jsonPrimitive?.contentOrNull ?: return null,
        title = o["title"]?.jsonPrimitive?.contentOrNull ?: return null,
        score = o["score"]?.jsonPrimitive?.intOrNull ?: return null,
        updatedSeq = seq,
    )

    private fun jobOf(o: JsonObject, seq: Long): JobRow? = JobRow(
        id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null,
        company = o["company"]?.jsonPrimitive?.contentOrNull ?: return null,
        title = o["title"]?.jsonPrimitive?.contentOrNull ?: return null,
        repost = o["repost"]?.jsonPrimitive?.booleanOrNull ?: return null,
        injectionFlag = o["injection_flag"]?.jsonPrimitive?.booleanOrNull ?: return null,
        updatedSeq = seq,
    )

    private fun evidenceEventOf(o: JsonObject): EvidenceEventRow? = EvidenceEventRow(
        seq = o["seq"]?.jsonPrimitive?.longOrNull ?: return null,
        ts = o["ts"]?.jsonPrimitive?.contentOrNull ?: return null,
        actor = o["actor"]?.jsonPrimitive?.contentOrNull ?: return null,
        kind = o["kind"]?.jsonPrimitive?.contentOrNull ?: return null,
        entity = o["entity"]?.jsonPrimitive?.contentOrNull ?: return null,
        entityId = o["entity_id"]?.jsonPrimitive?.contentOrNull ?: return null,
    )
}
