package app.careerseeker.dashboard.replica

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * At-rest protection is Android platform encryption (gate P2-REPLICA-CRYPTO recommendation:
 * no SQLCipher — its native .so re-opens the 16 KB-page-size compliance work the spec's
 * crypto choices deliberately avoided). The database lives in app-private storage,
 * device-encrypted; it is erased on unpair and fully re-syncable from a snapshot.
 */
@Database(
    entities = [
        ApplicationRow::class,
        JobRow::class,
        CountersRow::class,
        EvidenceEventRow::class,
        DocumentRow::class,
        SyncStateRow::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ReplicaDb : RoomDatabase() {
    abstract fun dao(): ReplicaDao

    companion object {
        private const val NAME = "replica.db"

        /**
         * v1 → v2: records whether a full snapshot has ever been applied (A3).
         *
         * Existing replicas migrate to `0` — "no snapshot seen" — which is the safe direction:
         * deltas are refused until the engine sends a snapshot, which it does on start and on
         * pairing. A migration that defaulted to `1` would assert a snapshot this replica may
         * never have received, which is the fabrication the column exists to prevent.
         *
         * A destructive migration would also have "worked" here (the replica is fully
         * re-syncable and there are no released builds), but a real migration keeps the
         * exported schema meaningful as a reviewable artifact.
         *
         * NOT YET COVERED BY A TEST: there is no `MigrationTestHelper` case opening a v1
         * database and migrating it. The statement is a one-column `ALTER TABLE` and Room
         * validates the result against the exported `2.json` at open time, so the risk is low
         * — but "low risk" is not "verified", and this is the first migration this schema has
         * ever had. Tracked in LOG.md's A3 known-gaps list.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_state ADD COLUMN snapshotSeen INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 → v3: carries the nullable Pro `outcome` on each application (§4.3.1, A6).
         *
         * Nullable with no default, because NULL is the protocol's own "unset" — inventing a
         * default here would turn "the engine never said" into a claim about the application.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE applications ADD COLUMN outcome TEXT")
            }
        }

        fun open(context: Context): ReplicaDb =
            Room.databaseBuilder(context.applicationContext, ReplicaDb::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** Unpair: drop the replica entirely. Everything in it is re-syncable. */
        fun erase(context: Context) {
            context.applicationContext.deleteDatabase(NAME)
        }
    }
}
