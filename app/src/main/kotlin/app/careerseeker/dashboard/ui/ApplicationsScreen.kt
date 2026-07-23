package app.careerseeker.dashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.careerseeker.dashboard.replica.ApplicationRow

/**
 * Applications list + state filters (spec §4.1 screen 3). Read-only; a row opens the
 * detail screen. Filter chips are derived from the states actually present, so the filter
 * bar never advertises an empty bucket.
 */
@Composable
fun ApplicationsScreen(
    applications: List<ApplicationRow>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val states = applications.map { it.state }.distinct().sorted()
    val shown = if (filter == null) applications else applications.filter { it.state == filter }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Applications")

        if (applications.isEmpty()) {
            EmptyHint("No applications in the replica yet.")
            return@Column
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf<String?>(null) + states) { state ->
                FilterChip(
                    selected = filter == state,
                    onClick = { filter = if (filter == state) null else state },
                    label = { Text(state ?: "All") },
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { app ->
                ApplicationCard(app, onOpen)
            }
        }
    }
}

@Composable
private fun ApplicationCard(app: ApplicationRow, onOpen: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(app.id) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                StateBadge(app.state)
                Text("score ${app.score}", style = MaterialTheme.typography.labelLarge)
            }
            Text(app.title, style = MaterialTheme.typography.titleMedium)
            Text(app.company, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** State chip; blocked/rejected read as warnings, everything else neutral. */
@Composable
internal fun StateBadge(state: String, modifier: Modifier = Modifier) {
    val container = when (state) {
        "BLOCKED_FABRICATION" -> MaterialTheme.colorScheme.errorContainer
        "REJECTED_BY_ENGINE" -> MaterialTheme.colorScheme.surfaceVariant
        "DRAFTED" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    androidx.compose.material3.Surface(color = container, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            state,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Small labeled badge used by the Jobs screen for repost/injection flags. */
@Composable
internal fun FlagBadge(text: String, error: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
