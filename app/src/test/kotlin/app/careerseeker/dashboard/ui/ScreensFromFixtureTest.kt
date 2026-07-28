package app.careerseeker.dashboard.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.careerseeker.dashboard.replica.DemoFixture
import app.careerseeker.dashboard.replica.ReplicaDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The P2-Runbook §2.4/§2.5 acceptance: every screen renders FROM THE FIXTURE, in CI, with
 * no relay and no device. Data flows fixture → Room → the same one-shot reads the shell's
 * Flows wrap — so these are projections of the real replica, not hand-built view state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // see EnvelopeApplierTest for why 35
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensFromFixtureTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: ReplicaDb

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReplicaDb::class.java).build()
        runBlocking { DemoFixture.seed(db) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun homeRendersCountersAndDemoBanner() = runBlocking<Unit> {
        val counters = db.dao().countersNow()
        val syncState = db.dao().syncStateNow()
        compose.setContent { HomeScreen(counters, syncState) }

        compose.onNodeWithText("Demo data — not a live engine").assertIsDisplayed()
        compose.onNodeWithText("Drafted").assertIsDisplayed()
        compose.onNodeWithText("23").assertIsDisplayed() // discovered
        compose.onNodeWithText("12").assertIsDisplayed() // cycles
    }

    @Test
    fun applicationsRendersRowsAndStateBadges() = runBlocking<Unit> {
        val apps = db.dao().applicationsNow()
        compose.setContent { ApplicationsScreen(apps, onOpen = {}) }

        compose.onNodeWithText("Senior Platform Engineer").assertIsDisplayed()
        compose.onNodeWithText("Northwind Labs").assertIsDisplayed()
        compose.onNodeWithText("score 82").assertIsDisplayed()
        compose.onNodeWithText("All").assertIsDisplayed() // filter bar present
    }

    @Test
    fun applicationDetailRendersAllThreeDocumentsReadOnly() = runBlocking<Unit> {
        val app = db.dao().applicationsNow().first { it.id == "app_demo_1" }
        val docs = db.dao().documentsNow("app_demo_1")
        compose.setContent { ApplicationDetailScreen(app, docs, onBack = {}) }

        compose.onNodeWithText("Documents (read-only)").assertIsDisplayed()
        // The three documents live below the fold of the test viewport; scroll each into view.
        compose.onNodeWithText("Draft email").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cover letter").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Resume").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun jobsRendersHonestyBadges() = runBlocking<Unit> {
        val jobs = db.dao().jobsNow()
        compose.setContent { JobsScreen(jobs) }

        compose.onNodeWithText("Kubernetes Engineer").assertIsDisplayed()
        compose.onNodeWithText("injection flagged").assertIsDisplayed()
        compose.onNodeWithText("repost").assertIsDisplayed()
    }

    @Test
    fun evidenceRendersTrailAndEngineVerifiedBadge() = runBlocking<Unit> {
        // Same newest-first ordering the screen's Flow query uses.
        val events = db.dao().evidenceEventsNow().sortedByDescending { it.seq }
        val auditOk = db.dao().syncStateNow()!!.auditOk
        compose.setContent { EvidenceScreen(events, auditOk) }

        compose.onNodeWithText("Audit chain intact (engine-verified)").assertIsDisplayed()
        compose.onNodeWithText("gate_blocked_fabrication").assertIsDisplayed()
    }

    @Test
    fun evidenceSaysUnknownWhenNoVerdictReported() {
        compose.setContent { EvidenceScreen(events = emptyList(), auditOk = null) }
        compose.onNodeWithText("Audit status unknown — not yet reported").assertIsDisplayed()
    }
}
