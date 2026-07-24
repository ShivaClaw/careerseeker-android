package app.careerseeker.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.careerseeker.dashboard.replica.CountersRow
import app.careerseeker.dashboard.replica.SyncStateRow

/**
 * Home/Live (spec §4.1 screen 2): the engine's tallies, plus an HONEST liveness line — the
 * replica renders what it holds whether or not the engine is reachable, and says so. All
 * five screens are pure projections of Room; nothing here fetches.
 */
@Composable
fun HomeScreen(counters: CountersRow?, syncState: SyncStateRow?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("CareerSeeker")
        StatusBanner(syncState)

        if (counters == null) {
            EmptyHint("No engine data yet. Pair a phone or enable demo mode.")
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                listOf(
                    "Cycles" to counters.cycles,
                    "Discovered" to counters.discovered,
                    "Acted" to counters.acted,
                    "Drafted" to counters.drafted,
                    "Blocked" to counters.blocked,
                    "Rejected" to counters.rejected,
                    "Errors" to counters.errors,
                ),
            ) { (label, value) ->
                MetricCard(label, value)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Long) {
    // A11y (P5): the label and value are two Texts, so TalkBack would land on each
    // separately ("Cycles", then "12"). Merge them into one node that speaks "Cycles: 12".
    // The visible layout is unchanged; onNodeWithText still finds "Cycles"/"12" in the
    // merged node's text list.
    Card(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$value", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** Shared honesty banner: demo label and engine-last-seen, never an implied "live". */
@Composable
internal fun StatusBanner(syncState: SyncStateRow?, modifier: Modifier = Modifier) {
    val (text, container) = when {
        syncState == null -> "Not paired — no data yet" to MaterialTheme.colorScheme.surfaceVariant
        syncState.demoMode -> "Demo data — not a live engine" to MaterialTheme.colorScheme.tertiaryContainer
        else -> "Engine last seen ${syncState.lastSeenTs ?: "unknown"} (engine clock)" to
            MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun ScreenTitle(text: String) {
    // A11y (P5): mark every screen's title as a heading so TalkBack users can jump
    // between screens' headers with the heading-navigation gesture. Shared by all five screens.
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
