package app.careerseeker.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.careerseeker.dashboard.replica.JobRow

/**
 * Jobs (spec §4.1 screen 5): discovered postings with their two honesty badges — repost and
 * prompt-injection-flagged. The flags ride the wire as booleans; the raw posting body never
 * ships to the phone in P2, so there is nothing here to interpolate.
 */
@Composable
fun JobsScreen(jobs: List<JobRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Jobs")

        if (jobs.isEmpty()) {
            EmptyHint("No discovered jobs in the replica yet.")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(jobs, key = { it.id }) { job ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(job.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            job.company,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (job.repost || job.injectionFlag) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (job.repost) FlagBadge("repost", error = false)
                                if (job.injectionFlag) FlagBadge("injection flagged", error = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
