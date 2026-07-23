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

    /** A valid protocol kind the replica does not project (doc, conflict… — P3 concerns). */
    data class Ignored(val kind: String) : ApplyResult

    /** The plaintext did not parse as the expected payload shape. Nothing changed. */
    data object Malformed : ApplyResult
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

    suspend fun apply(seq: Long, envelopeTs: String, kind: String, plaintext: ByteArray): ApplyResult {
        val body = parseBody(plaintext) ?: return ApplyResult.Malformed

        return db.withTransaction {
            val dao = db.dao()
            val state = dao.syncStateNow()
            if (seq <= (state?.highestAppliedE2pSeq ?: 0L)) return@withTransaction ApplyResult.SkippedStale

            when (kind) {
                "snapshot" -> applySnapshot(dao, seq, body) ?: return@withTransaction ApplyResult.Malformed
                "delta" -> applyDelta(dao, seq, body) ?: return@withTransaction ApplyResult.Malformed
                "heartbeat" -> applyHeartbeat(dao, body) ?: return@withTransaction ApplyResult.Malformed
                else -> return@withTransaction ApplyResult.Ignored(kind)
            }

            dao.upsertSyncState(
                SyncStateRow(
                    highestAppliedE2pSeq = seq,
                    lastSeenTs = envelopeTs,
                    lastCycle = body["counters"]?.jsonObject?.get("cycles")?.jsonPrimitive?.longOrNull
                        ?: (body["cycle"]?.jsonPrimitive?.longOrNull ?: state?.lastCycle),
                    demoMode = false, // real engine data replaces any fixture claim
                ),
            )
            ApplyResult.Applied(kind)
        }
    }

    /** Snapshot is FULL dashboard state: applications and jobs are replaced wholesale. */
    private suspend fun applySnapshot(dao: ReplicaDao, seq: Long, body: JsonObject): Unit? {
        val counters = countersOf(body) ?: return null
        val apps = body["applications"]?.jsonArray?.map { appOf(it.jsonObject, seq) ?: return null } ?: return null
        val jobs = body["jobs"]?.jsonArray?.map { jobOf(it.jsonObject, seq) ?: return null } ?: return null

        dao.clearApplications()
        dao.clearJobs()
        dao.upsertApplications(apps)
        dao.upsertJobs(jobs)
        dao.upsertCounters(counters)
        return Unit
    }

    /** Delta carries what changed: listed rows are upserted, everything else is retained. */
    private suspend fun applyDelta(dao: ReplicaDao, seq: Long, body: JsonObject): Unit? {
        val counters = countersOf(body) ?: return null
        val apps = body["applications"]?.jsonArray?.map { appOf(it.jsonObject, seq) ?: return null } ?: return null
        val jobs = body["jobs"]?.jsonArray?.map { jobOf(it.jsonObject, seq) ?: return null } ?: return null

        dao.upsertApplications(apps)
        dao.upsertJobs(jobs)
        dao.upsertCounters(counters)
        return Unit
    }

    /** Heartbeat is counters-only liveness; the row tables are untouched. */
    private suspend fun applyHeartbeat(dao: ReplicaDao, body: JsonObject): Unit? {
        dao.upsertCounters(countersOf(body) ?: return null)
        return Unit
    }

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
}
