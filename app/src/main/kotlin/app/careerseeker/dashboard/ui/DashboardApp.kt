package app.careerseeker.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.careerseeker.dashboard.replica.ReplicaDb

/** The four top-level destinations. Detail is an overlay on Applications, not a fifth tab. */
internal enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    APPLICATIONS("Applications", Icons.AutoMirrored.Filled.List),
    JOBS("Jobs", Icons.Filled.Search),
    EVIDENCE("Evidence", Icons.Filled.Info),
}

/**
 * The read-only dashboard shell: bottom navigation over the five P2 screens, every one a
 * pure projection of the Room replica's Flows. No screen fetches, mutates, or edits —
 * that is what makes the whole app honestly offline-readable (spec Part 6) and keeps the
 * P3 editing surface out of this phase entirely.
 */
@Composable
fun DashboardApp(db: ReplicaDb) {
    val dao = db.dao()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var openAppId by rememberSaveable { mutableStateOf<String?>(null) }

    val counters by dao.counters().collectAsState(initial = null)
    val syncState by dao.syncState().collectAsState(initial = null)
    val applications by dao.applications().collectAsState(initial = emptyList())
    val jobs by dao.jobs().collectAsState(initial = emptyList())
    val events by dao.evidenceEvents().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The provenance banner lives in the SHELL, not in a screen.
        //
        // It used to be drawn by HomeScreen alone, which meant Applications, Jobs, Evidence
        // and the detail overlay rendered demo rows with nothing anywhere on screen saying so.
        // A user who opened the app on the Jobs tab saw six fabricated postings presented as
        // their pipeline. The honest-UI rule is "labelled on EVERY screen", and hoisting it
        // here is what makes that structural: a screen added later cannot forget it, because
        // no screen draws it.
        topBar = { StatusBanner(syncState) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && openAppId == null,
                        onClick = { openAppId = null; tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        val detailId = openAppId
        if (detailId != null) {
            BackHandler { openAppId = null }
            val application by remember(detailId) { dao.application(detailId) }.collectAsState(initial = null)
            val documents by remember(detailId) { dao.documents(detailId) }.collectAsState(initial = emptyList())
            ApplicationDetailScreen(application, documents, onBack = { openAppId = null }, modifier = content)
        } else {
            when (tab) {
                Tab.HOME -> HomeScreen(counters, syncState, modifier = content)
                Tab.APPLICATIONS -> ApplicationsScreen(applications, onOpen = { openAppId = it }, modifier = content)
                Tab.JOBS -> JobsScreen(jobs, modifier = content)
                Tab.EVIDENCE -> EvidenceScreen(events, auditOk = syncState?.auditOk, modifier = content)
            }
        }
    }
}
