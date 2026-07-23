package app.careerseeker.dashboard.replica

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = true,
)
abstract class ReplicaDb : RoomDatabase() {
    abstract fun dao(): ReplicaDao

    companion object {
        private const val NAME = "replica.db"

        fun open(context: Context): ReplicaDb =
            Room.databaseBuilder(context.applicationContext, ReplicaDb::class.java, NAME).build()

        /** Unpair: drop the replica entirely. Everything in it is re-syncable. */
        fun erase(context: Context) {
            context.applicationContext.deleteDatabase(NAME)
        }
    }
}
