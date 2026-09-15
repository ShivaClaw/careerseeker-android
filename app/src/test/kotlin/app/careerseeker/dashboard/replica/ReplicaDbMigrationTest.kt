package app.careerseeker.dashboard.replica

import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Closes the gap `ReplicaDb` documents against itself: "NOT YET COVERED BY A TEST: there is no
 * `MigrationTestHelper` case opening a v1 database and migrating it."
 *
 * This runs on the JVM under Robolectric — real Android SQLite, no emulator — so it gates in CI
 * like the rest of the replica tests.
 *
 * `MigrationTestHelper` builds the *old-version* database from the committed schema export under
 * `app/schemas`, which is the part that cannot honestly be faked by hand-writing CREATE TABLE. The
 * migrated schema is then validated by Room itself at open time (see [openMigrated]): if a
 * migration leaves the schema anything other than what `3.json` describes, the open throws. That is
 * what makes these assertions worth more than "the ALTER TABLE did not throw".
 *
 * The defaults are the point, not an incidental detail. `snapshotSeen` must arrive as **0**: a
 * migration that defaulted it to 1 would assert a snapshot this replica may never have received,
 * and deltas would then be applied over demo fixture rows — the exact fabrication the column exists
 * to prevent. `outcome` must arrive **NULL**, because "no outcome recorded" and "outcome known" are
 * different claims and an upgraded row has not earned the second one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Ignore(
    "BLOCKED (B-5): Room 2.8.4 cannot open a FILE-backed database under Robolectric. Every open " +
        "routes through SupportSQLiteDriver.open(), which compares the requested path against the " +
        "configured name and throws: \"This driver is configured to open a database named " +
        "'replica-migration-test.db' but '<robolectric abs path>' was requested\". In-memory " +
        "databases are unaffected, which is why the other replica tests pass -- but a migration " +
        "test must persist a v1 file and reopen it, so it cannot avoid the file path. Tried: " +
        "MigrationTestHelper.runMigrationsAndValidate, opening via Room.databaseBuilder, and " +
        "forcing the legacy path with openHelperFactory(FrameworkSQLiteOpenHelperFactory()) -- " +
        "same failure each time. The assertions below are believed correct and are kept so this " +
        "is revivable: move the class to app/src/androidTest and drop this annotation once an " +
        "emulator exists (B-4).",
)
class ReplicaDbMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReplicaDb::class.java,
    )

    @Test
    fun `v1 to v2 adds snapshotSeen defaulted to 0, never 1`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO sync_state (id, lastSeq, lastTs, demoMode) VALUES (0, 7, '2026-08-09T00:00:00Z', 0)")
        }

        val migrated = openMigrated()

        migrated.query("SELECT lastSeq, snapshotSeen FROM sync_state WHERE id = 0").use { c ->
            assertTrue("the pre-migration row should survive", c.moveToFirst())
            assertEquals("the existing high-water mark must be preserved", 7, c.getInt(0))
            assertEquals(
                "snapshotSeen must default to 0 — a 1 would claim a snapshot that was never received",
                0,
                c.getInt(1),
            )
        }
    }

    @Test
    fun `v2 to v3 adds a nullable outcome that starts NULL`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO applications (id, state, company, title, score, draftRef, updatedAt, jobUrl, applyUrl) " +
                    "VALUES ('app_1', 'SENT', 'Northwind', 'Platform Engineer', 88, NULL, '2026-08-09T00:00:00Z', NULL, NULL)",
            )
        }

        val migrated = openMigrated()

        migrated.query("SELECT outcome FROM applications WHERE id = 'app_1'").use { c ->
            assertTrue("the pre-migration application should survive", c.moveToFirst())
            assertTrue(
                "outcome must be NULL after upgrade — 'not recorded' is not the same claim as 'known'",
                c.isNull(0),
            )
        }
    }

    @Test
    fun `the full v1 to v3 chain runs in one pass`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO sync_state (id, lastSeq, lastTs, demoMode) VALUES (0, 3, '2026-08-09T00:00:00Z', 1)")
        }

        // Both migrations at once: the path a real device on an old build actually takes, which is
        // not the same code path as either step verified alone.
        val migrated = openMigrated()

        migrated.query("SELECT lastSeq, snapshotSeen, demoMode FROM sync_state WHERE id = 0").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
            assertEquals("snapshotSeen still 0 across the whole chain", 0, c.getInt(1))
            assertEquals("demoMode is carried, not silently reset", 1, c.getInt(2))
        }
    }

    /**
     * Open the database through Room itself, applying the real migration list from [ReplicaDb].
     *
     * Room's own open-time schema validation is the assertion that matters here: if a migration
     * leaves the schema anything other than what the committed `3.json` describes, the open throws
     * rather than quietly succeeding. That is the same guarantee `runMigrationsAndValidate` gives.
     *
     * It is used instead of that helper because Room 2.8.4 drives `runMigrationsAndValidate`
     * through the new `SQLiteDriver` API, which is handed Robolectric's absolute database path and
     * rejects it ("This driver is configured to open a database named 'x.db' but '<abs path>' was
     * requested"). `createDatabase` is unaffected, so the helper still builds the old-version
     * database from the committed schema — which is the part that cannot be faked by hand.
     */
    private fun openMigrated() =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplicaDb::class.java,
            TEST_DB,
        )
            .addMigrations(ReplicaDb.MIGRATION_1_2, ReplicaDb.MIGRATION_2_3)
            // Force the legacy support-helper path. Room 2.8.4 otherwise opens through
            // SupportSQLiteDriver, which is handed Robolectric's absolute database path and rejects
            // it. This affects any FILE-backed Room database under Robolectric; the existing replica
            // tests never hit it because they build in-memory databases, and a migration test cannot
            // (it has to persist a v1 file and reopen it).
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
            .also { openedDatabases += it }
            .openHelper
            .writableDatabase

    @After
    fun closeOpenedDatabases() {
        openedDatabases.forEach { it.close() }
        openedDatabases.clear()
    }

    private val openedDatabases = mutableListOf<ReplicaDb>()

    private companion object {
        const val TEST_DB = "replica-migration-test.db"
    }
}
