package app.careerseeker.dashboard.replica

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Reads are Flows so every screen recomposes when an envelope (or the fixture) lands;
 * one-shot suspend variants exist for tests and the applier's stale-check. Writes are
 * upserts — the applier's delta semantics are "latest wins by envelope seq", and the
 * receiver has already rejected replays before anything reaches this layer.
 */
@Dao
interface ReplicaDao {
    // ---- projections the screens observe ----
    @Query("SELECT * FROM applications ORDER BY updatedSeq DESC, id")
    fun applications(): Flow<List<ApplicationRow>>

    @Query("SELECT * FROM jobs ORDER BY updatedSeq DESC, id")
    fun jobs(): Flow<List<JobRow>>

    @Query("SELECT * FROM counters WHERE id = 1")
    fun counters(): Flow<CountersRow?>

    @Query("SELECT * FROM evidence_events ORDER BY seq DESC")
    fun evidenceEvents(): Flow<List<EvidenceEventRow>>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun syncState(): Flow<SyncStateRow?>

    @Query("SELECT * FROM applications WHERE id = :id")
    fun application(id: String): Flow<ApplicationRow?>

    @Query("SELECT * FROM documents WHERE appId = :appId ORDER BY kind")
    fun documents(appId: String): Flow<List<DocumentRow>>

    // ---- one-shot reads (tests, applier bookkeeping) ----
    @Query("SELECT * FROM applications ORDER BY id") suspend fun applicationsNow(): List<ApplicationRow>
    @Query("SELECT * FROM jobs ORDER BY id") suspend fun jobsNow(): List<JobRow>
    @Query("SELECT * FROM counters WHERE id = 1") suspend fun countersNow(): CountersRow?
    @Query("SELECT * FROM evidence_events ORDER BY seq") suspend fun evidenceEventsNow(): List<EvidenceEventRow>
    @Query("SELECT * FROM sync_state WHERE id = 1") suspend fun syncStateNow(): SyncStateRow?
    @Query("SELECT * FROM documents WHERE appId = :appId ORDER BY kind") suspend fun documentsNow(appId: String): List<DocumentRow>

    // ---- writes (applier + fixture) ----
    @Upsert suspend fun upsertApplications(rows: List<ApplicationRow>)
    @Upsert suspend fun upsertJobs(rows: List<JobRow>)
    @Upsert suspend fun upsertCounters(row: CountersRow)
    @Upsert suspend fun upsertEvidenceEvents(rows: List<EvidenceEventRow>)
    @Upsert suspend fun upsertSyncState(row: SyncStateRow)
    @Upsert suspend fun upsertDocuments(rows: List<DocumentRow>)

    @Query("DELETE FROM applications") suspend fun clearApplications()
    @Query("DELETE FROM jobs") suspend fun clearJobs()
    @Query("DELETE FROM evidence_events") suspend fun clearEvidenceEvents()
    @Query("DELETE FROM documents") suspend fun clearDocuments()
}
