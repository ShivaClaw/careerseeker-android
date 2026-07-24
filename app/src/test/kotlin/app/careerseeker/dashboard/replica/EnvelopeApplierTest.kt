package app.careerseeker.dashboard.replica

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.careerseeker.core.EnvelopeReceiver
import app.careerseeker.core.ReceivedEnvelope
import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The P2-Runbook §2.3 acceptance: apply a snapshot then a delta and assert the projected
 * state, against REAL Room + SQLite (Robolectric supplies both on the JVM, no emulator).
 * Payload JSON here is written to the exact field names the engine's SyncPayloads builders
 * emit — these strings are the phone-side pin of that wire shape.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35, not the app's 37: Robolectric does not model 37 yet, and its SDK-36 image
// demands a Java 21 test JVM while this module pins jvmToolchain(17). The replica
// exercises nothing version-specific — this is Room+SQLite behavior, stable across SDKs.
@Config(sdk = [35])
class EnvelopeApplierTest {
    private lateinit var db: ReplicaDb
    private lateinit var applier: EnvelopeApplier

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReplicaDb::class.java).build()
        applier = EnvelopeApplier(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapshotJson(
        drafted: Long = 1,
        cycles: Long = 7,
        apps: String = """[{"id":"app_1","state":"READY","company":"Northwind Labs","title":"Senior Platform Engineer","score":82}]""",
        jobs: String = """[{"id":"job_1","company":"Northwind Labs","title":"Senior Platform Engineer","repost":false,"injection_flag":false}]""",
    ): ByteArray =
        """{"kind":"snapshot","body":{"counters":{"discovered":3,"acted":1,"drafted":$drafted,"blocked":0,"rejected":1,"errors":0,"cycles":$cycles},"applications":$apps,"jobs":$jobs}}"""
            .toByteArray()

    private fun deltaJson(): ByteArray =
        """{"kind":"delta","body":{"since_seq":1,"counters":{"discovered":4,"acted":2,"drafted":2,"blocked":0,"rejected":1,"errors":0,"cycles":8},"applications":[{"id":"app_1","state":"DRAFTED","company":"Northwind Labs","title":"Senior Platform Engineer","score":82},{"id":"app_2","state":"READY","company":"Fabrikam","title":"Staff Engineer","score":74}],"jobs":[{"id":"job_2","company":"Fabrikam","title":"Staff Engineer","repost":true,"injection_flag":true}]}}"""
            .toByteArray()

    private fun heartbeatJson(cycles: Long = 9): ByteArray =
        """{"kind":"heartbeat","body":{"ts":"2026-07-23T12:05:00Z","cycle":$cycles,"counters":{"discovered":4,"acted":2,"drafted":2,"blocked":0,"rejected":1,"errors":0,"cycles":$cycles}}}"""
            .toByteArray()

    private fun evidenceJson(auditOk: Boolean = true): ByteArray =
        """{"kind":"evidence","body":{"audit_ok":$auditOk,"event_count":42,"events":[{"seq":1,"ts":"2026-07-23T09:00:11Z","actor":"engine","kind":"scout_ingest","entity":"scout","entity_id":"b41f"},{"seq":2,"ts":"2026-07-23T09:00:14Z","actor":"engine","kind":"application_created","entity":"application","entity_id":"app_1"}]}}"""
            .toByteArray()

    @Test
    fun snapshotProjectsFullState() = runTest {
        val result = applier.apply(seq = 1, envelopeTs = "2026-07-23T12:00:00Z", kind = "snapshot", plaintext = snapshotJson())
        assertEquals(ApplyResult.Applied("snapshot"), result)

        val apps = db.dao().applicationsNow()
        assertEquals(1, apps.size)
        assertEquals("READY", apps[0].state)
        assertEquals("Northwind Labs", apps[0].company)
        assertEquals(82, apps[0].score)

        val jobs = db.dao().jobsNow()
        assertEquals(1, jobs.size)
        assertEquals(false, jobs[0].injectionFlag)

        val counters = db.dao().countersNow()!!
        assertEquals(1L, counters.drafted)
        assertEquals(7L, counters.cycles)

        val state = db.dao().syncStateNow()!!
        assertEquals(1L, state.highestAppliedE2pSeq)
        assertEquals("2026-07-23T12:00:00Z", state.lastSeenTs)
        assertEquals(false, state.demoMode)
    }

    @Test
    fun deltaUpsertsAndRetains() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "snapshot", snapshotJson())
        val result = applier.apply(2, "2026-07-23T12:01:00Z", "delta", deltaJson())
        assertEquals(ApplyResult.Applied("delta"), result)

        val apps = db.dao().applicationsNow()
        assertEquals(2, apps.size) // app_1 updated in place, app_2 added
        assertEquals("DRAFTED", apps.first { it.id == "app_1" }.state)
        assertEquals("READY", apps.first { it.id == "app_2" }.state)

        val jobs = db.dao().jobsNow()
        assertEquals(2, jobs.size) // job_1 retained, job_2 added
        assertTrue(jobs.first { it.id == "job_2" }.injectionFlag)

        assertEquals(8L, db.dao().countersNow()!!.cycles)
        assertEquals(2L, db.dao().syncStateNow()!!.highestAppliedE2pSeq)
    }

    @Test
    fun heartbeatTouchesOnlyCountersAndLiveness() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "snapshot", snapshotJson())
        val result = applier.apply(3, "2026-07-23T12:05:00Z", "heartbeat", heartbeatJson())
        assertEquals(ApplyResult.Applied("heartbeat"), result)

        assertEquals(1, db.dao().applicationsNow().size) // untouched
        assertEquals(9L, db.dao().countersNow()!!.cycles)
        val state = db.dao().syncStateNow()!!
        assertEquals(3L, state.highestAppliedE2pSeq)
        assertEquals("2026-07-23T12:05:00Z", state.lastSeenTs)
        assertEquals(9L, state.lastCycle)
    }

    @Test
    fun staleSeqIsSkippedEvenAfterRestart() = runTest {
        applier.apply(5, "2026-07-23T12:00:00Z", "snapshot", snapshotJson())

        // A fresh applier (fresh process; the receiver's in-memory window is gone) must
        // still refuse seq 5 because the mark is persisted in the replica itself.
        val restarted = EnvelopeApplier(db)
        val result = restarted.apply(5, "2026-07-23T12:09:00Z", "delta", deltaJson())
        assertEquals(ApplyResult.SkippedStale, result)
        assertEquals(1, db.dao().applicationsNow().size) // nothing changed
        assertEquals("2026-07-23T12:00:00Z", db.dao().syncStateNow()!!.lastSeenTs)
    }

    @Test
    fun secondSnapshotReplacesWholesale() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "snapshot", snapshotJson())
        applier.apply(2, "2026-07-23T12:01:00Z", "delta", deltaJson())
        // New snapshot names ONLY app_9/job_9: everything older must vanish.
        val result = applier.apply(
            3, "2026-07-23T12:02:00Z", "snapshot",
            snapshotJson(
                apps = """[{"id":"app_9","state":"READY","company":"Initech","title":"SRE","score":66}]""",
                jobs = """[{"id":"job_9","company":"Initech","title":"SRE","repost":false,"injection_flag":false}]""",
            ),
        )
        assertEquals(ApplyResult.Applied("snapshot"), result)
        assertEquals(listOf("app_9"), db.dao().applicationsNow().map { it.id })
        assertEquals(listOf("job_9"), db.dao().jobsNow().map { it.id })
    }

    @Test
    fun malformedPayloadChangesNothing() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "snapshot", snapshotJson())
        val garbled = applier.apply(2, "2026-07-23T12:01:00Z", "snapshot", "not json".toByteArray())
        assertEquals(ApplyResult.Malformed, garbled)
        val missingField = applier.apply(
            2, "2026-07-23T12:01:00Z", "snapshot",
            """{"kind":"snapshot","body":{"counters":{"discovered":1}}}""".toByteArray(),
        )
        assertEquals(ApplyResult.Malformed, missingField)
        assertEquals(1L, db.dao().syncStateNow()!!.highestAppliedE2pSeq) // mark unmoved
        assertEquals(1, db.dao().applicationsNow().size)
    }

    @Test
    fun nonReplicaKindIsIgnored() = runTest {
        val result = applier.apply(
            1, "2026-07-23T12:00:00Z", "doc",
            """{"kind":"doc","body":{"app_id":"app_1"}}""".toByteArray(),
        )
        assertEquals(ApplyResult.Ignored("doc"), result)
        assertNull(db.dao().syncStateNow()) // ignored payloads do not advance the mark
    }

    @Test
    fun evidenceProjectsAuditTrailAndVerdict() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "snapshot", snapshotJson())
        val result = applier.apply(2, "2026-07-23T12:01:00Z", "evidence", evidenceJson(auditOk = true))
        assertEquals(ApplyResult.Applied("evidence"), result)

        val events = db.dao().evidenceEventsNow()
        assertEquals(2, events.size)
        assertEquals("scout_ingest", events.first { it.seq == 1L }.kind)
        assertEquals("b41f", events.first { it.seq == 1L }.entityId)

        val state = db.dao().syncStateNow()!!
        assertEquals(true, state.auditOk)
        assertEquals(2L, state.highestAppliedE2pSeq)
        assertEquals(1, db.dao().applicationsNow().size) // dashboard rows untouched by evidence
    }

    @Test
    fun evidenceReportsBrokenChainAndReplacesTrail() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "evidence", evidenceJson(auditOk = true))
        // A later evidence payload with a broken verdict and a different trail replaces wholesale.
        val broken =
            """{"kind":"evidence","body":{"audit_ok":false,"first_broken_seq":7,"event_count":9,"events":[{"seq":9,"ts":"2026-07-23T11:00:00Z","actor":"engine","kind":"audit_break","entity":"event","entity_id":"e9"}]}}"""
                .toByteArray()
        applier.apply(2, "2026-07-23T12:05:00Z", "evidence", broken)

        assertEquals(listOf(9L), db.dao().evidenceEventsNow().map { it.seq })
        assertEquals(false, db.dao().syncStateNow()!!.auditOk)
    }

    @Test
    fun snapshotResetsAuditToUnknownButDeltaPreservesIt() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "evidence", evidenceJson(auditOk = true))
        // A delta does not re-report the audit verdict, so it must be preserved.
        applier.apply(2, "2026-07-23T12:01:00Z", "delta", deltaJson())
        assertEquals(true, db.dao().syncStateNow()!!.auditOk)
        // A full snapshot is a fresh resync with no verdict reported -> unknown, not stale "intact".
        applier.apply(3, "2026-07-23T12:02:00Z", "snapshot", snapshotJson())
        assertNull(db.dao().syncStateNow()!!.auditOk)
    }

    @Test
    fun malformedEvidenceChangesNothing() = runTest {
        applier.apply(1, "2026-07-23T12:00:00Z", "evidence", evidenceJson(auditOk = true))
        // Missing audit_ok, and an event missing entity_id: either makes the whole apply a no-op.
        val garbled = applier.apply(
            2, "2026-07-23T12:01:00Z", "evidence",
            """{"kind":"evidence","body":{"events":[{"seq":1,"ts":"t","actor":"engine","kind":"k","entity":"e"}]}}""".toByteArray(),
        )
        assertEquals(ApplyResult.Malformed, garbled)
        assertEquals(1L, db.dao().syncStateNow()!!.highestAppliedE2pSeq) // mark unmoved
        assertEquals(2, db.dao().evidenceEventsNow().size) // prior trail intact
        assertEquals(true, db.dao().syncStateNow()!!.auditOk)
    }

    /**
     * The seam test: bytes travel exactly as they would in production — sealed with k_e2p,
     * opened and replay-checked by `:core`'s receiver, then projected by the applier.
     */
    @Test
    fun sealedEnvelopeFlowsReceiverToReplica() = runTest {
        val kE2p = ByteArray(32) { (it + 1).toByte() }
        val pairing = "p_AAAABBBBCCCCDDDD"
        val ts = "2026-07-23T12:00:00Z"
        val plaintext = snapshotJson()

        val header = app.careerseeker.core.EnvelopeHeader(
            1, pairing, app.careerseeker.core.Direction.ENGINE_TO_PHONE, 1, ts, "k-test",
        )
        val nonce = ByteArray(12) { (it * 3).toByte() }
        val ciphertext = SyncCrypto.seal(kE2p, nonce, header.aad(), plaintext)

        val receiver = EnvelopeReceiver(activeKeyId = "k-test")
        val received = receiver.receive(
            ReceivedEnvelope(
                v = 1, pairing = pairing, dir = "e2p", seq = 1, ts = ts, keyId = "k-test",
                nonce = Base64Url.encode(nonce), ciphertext = Base64Url.encode(ciphertext), sig = null,
            ),
        ) { kE2p }

        assertTrue(received.accepted)
        assertEquals("snapshot", received.kind)

        val result = applier.apply(1, ts, received.kind!!, received.plaintext!!)
        assertEquals(ApplyResult.Applied("snapshot"), result)
        assertEquals("Northwind Labs", db.dao().applicationsNow()[0].company)
    }
}
