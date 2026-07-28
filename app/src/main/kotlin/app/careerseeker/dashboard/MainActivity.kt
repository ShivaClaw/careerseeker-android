package app.careerseeker.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.careerseeker.dashboard.replica.DemoFixture
import app.careerseeker.dashboard.replica.ReplicaDb
import app.careerseeker.dashboard.ui.DashboardApp

/**
 * P2: the read-only dashboard. Screens are pure projections of the Room replica; with no
 * pairing yet (that flow is device-bound, later in P2), the replica is seeded with the demo
 * fixture on first launch so every screen renders honestly labeled demo data. A real
 * snapshot replaces the fixture wholesale and clears the label.
 */
class MainActivity : ComponentActivity() {
    private lateinit var db: ReplicaDb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = ReplicaDb.open(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LaunchedEffect(Unit) {
                        // Seed only an empty replica: never clobber live-synced state.
                        if (db.dao().syncStateNow() == null) DemoFixture.seed(db)
                    }
                    DashboardApp(db)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) db.close()
    }
}
