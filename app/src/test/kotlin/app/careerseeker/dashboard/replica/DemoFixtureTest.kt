package app.careerseeker.dashboard.replica

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

/** The fixture must make every screen renderable AND yield to real data (P2-Runbook §2.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // see EnvelopeApplierTest for why 35
class DemoFixtureTest {
    private lateinit var db: ReplicaDb

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReplicaDb::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedsEveryTableTheScreensProject() = runTest {
        DemoFixture.seed(db)

        val apps = db.dao().applicationsNow()
        assertEquals(6, apps.size)
        // Every lifecycle bucket a screen renders is represented.
        for (state in listOf(
            "DRAFTED", "AWAITING_RESPONSE", "GATE_PENDING",
            "BLOCKED_FABRICATION", "REJECTED_BY_ENGINE", "PAUSED",
        )) {
            assertTrue("fixture must cover state $state", apps.any { it.state == state })
        }

        val jobs = db.dao().jobsNow()
        assertEquals(5, jobs.size)
        assertTrue("fixture must cover a repost badge", jobs.any { it.repost })
        assertTrue("fixture must cover an injection badge", jobs.any { it.injectionFlag })

        assertEquals(12L, db.dao().countersNow()!!.cycles)
        assertEquals(7, db.dao().evidenceEventsNow().size)

        // The detail screen renders all three documents read-only (editing is P3). Canonical
        // doc_kind set (Sync-Protocol.md §4.3), ordered by kind ascending as the DAO returns them.
        assertEquals(
            listOf("cover_letter", "draft_email", "resume_text"),
            db.dao().documentsNow("app_demo_1").map { it.kind },
        )

        val state = db.dao().syncStateNow()!!
        assertTrue("fixture data must be labeled demo", state.demoMode)
        assertEquals(0L, state.highestAppliedE2pSeq)
        assertEquals(true, state.auditOk)
    }

    @Test
    fun seedingTwiceIsIdempotent() = runTest {
        DemoFixture.seed(db)
        DemoFixture.seed(db)
        assertEquals(6, db.dao().applicationsNow().size)
        assertEquals(5, db.dao().jobsNow().size)
        assertEquals(7, db.dao().evidenceEventsNow().size)
    }

    @Test
    fun realSnapshotSupersedesDemoData() = runTest {
        DemoFixture.seed(db)

        val snapshot =
            """{"kind":"snapshot","body":{"counters":{"discovered":1,"acted":0,"drafted":0,"blocked":0,"rejected":0,"errors":0,"cycles":1},"applications":[{"id":"app_real","state":"READY","company":"RealCo","title":"Engineer","score":90}],"jobs":[]}}"""
                .toByteArray()
        val result = EnvelopeApplier(db).apply(1, "2026-07-23T13:00:00Z", "snapshot", snapshot)
        assertEquals(ApplyResult.Applied("snapshot"), result)

        // Wholesale replacement: no demo application survives, and the demo label is gone.
        assertEquals(listOf("app_real"), db.dao().applicationsNow().map { it.id })
        val state = db.dao().syncStateNow()!!
        assertEquals(false, state.demoMode)
        // Real sync has not reported an audit verdict; the screen must say unknown, not guess.
        assertNull(state.auditOk)
    }
}
