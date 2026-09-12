package app.careerseeker.dashboard.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    /**
     * B-22's synchronization seam, and the reason it is a `waitUntil` on the node rather than a
     * `waitForIdle`.
     *
     * Every assertion in the two tests that render [DashboardApp] waits on a Room `Flow`. The
     * shell reads all five replica queries with `collectAsState`, and each initial value renders a
     * DIFFERENT tree than the one the test is looking for: `StatusBanner(null)` prints
     * "Not paired — no data yet" rather than the demo label (`HomeScreen.kt:72`),
     * `ApplicationsScreen` prints "No applications in the replica yet." while its list is empty
     * (`ApplicationsScreen.kt:44`), and `ApplicationDetailScreen` returns early while its
     * application is null (`ApplicationDetailScreen.kt:42`). Until Room's query executor delivers
     * the first row and a recomposition lands, the node does not exist.
     *
     * Compose synchronizes on the compose clock, and every node interaction already does that
     * before it looks — which is why these tests flake anyway, and why an explicit `waitForIdle()`
     * in these positions would add nothing: Room's executor is not the compose clock. The
     * condition worth waiting on is the node's arrival.
     *
     * The four tests that pass one-shot `*Now()` reads straight into a screen never go through
     * this seam, which is why none of them has ever failed.
     */
    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeRendersCounters() = runBlocking<Unit> {
        val counters = db.dao().countersNow()
        val syncState = db.dao().syncStateNow()
        compose.setContent { HomeScreen(counters, syncState) }

        compose.onNodeWithText("Drafted").assertIsDisplayed()
        compose.onNodeWithText("23").assertIsDisplayed() // discovered
        compose.onNodeWithText("12").assertIsDisplayed() // cycles
    }

    @Test
    fun theProvenanceBannerIsShownOnEveryTab() {
        // A4: the banner used to be drawn by HomeScreen alone, so Applications, Jobs and
        // Evidence rendered fixture rows with nothing on screen saying they were fixture rows.
        // It now lives in the shell. This walks the whole navigation surface rather than
        // trusting that, because "labelled on every screen" is the actual honest-UI rule.
        compose.setContent { DashboardApp(db) }
        val label = "Demo data — not a live engine"

        awaitText(label)
        compose.onNodeWithText(label).assertIsDisplayed()

        for (tab in listOf("Applications", "Jobs", "Evidence", "Home")) {
            compose.onNodeWithText(tab).performClick()
            awaitText(label)
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun theBannerFollowsIntoTheApplicationDetailOverlay() {
        // The detail view is an overlay rather than a tab, which is exactly the kind of screen
        // a per-screen banner gets forgotten on.
        compose.setContent { DashboardApp(db) }

        compose.onNodeWithText("Applications").performClick()
        awaitText("Senior Platform Engineer")
        compose.onNodeWithText("Senior Platform Engineer").performClick()

        awaitText("Documents (read-only)")
        compose.onNodeWithText("Documents (read-only)").assertIsDisplayed()
        awaitText("Demo data — not a live engine")
        compose.onNodeWithText("Demo data — not a live engine").assertIsDisplayed()
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
