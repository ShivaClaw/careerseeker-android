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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.careerseeker.dashboard.replica.EvidenceEventRow

/**
 * Evidence (spec §4.1 screen 6): the audit-event trail plus the chain-verification badge.
 * The verdict is the ENGINE's, relayed — [auditOk] null means no applied payload has
 * reported one, and the screen says "unknown" rather than implying a verification the
 * phone never performed. Event metadata only; payload bodies stay on the desktop.
 */
@Composable
fun EvidenceScreen(events: List<EvidenceEventRow>, auditOk: Boolean?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Evidence")
        AuditBadge(auditOk)

        if (events.isEmpty()) {
            EmptyHint("No audit events in the replica yet.")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.seq }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(event.kind, style = MaterialTheme.typography.titleSmall)
                            Text("#${event.seq}", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "${event.actor} · ${event.entity} ${event.entityId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(event.ts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditBadge(auditOk: Boolean?) {
    val (text, container) = when (auditOk) {
        true -> "Audit chain intact (engine-verified)" to MaterialTheme.colorScheme.secondaryContainer
        false -> "Audit chain BROKEN (engine-reported)" to MaterialTheme.colorScheme.errorContainer
        null -> "Audit status unknown — not yet reported" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}
