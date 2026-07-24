package app.careerseeker.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.careerseeker.dashboard.replica.ApplicationRow
import app.careerseeker.dashboard.replica.DocumentRow

/**
 * Application detail (spec §4.1 screen 4): state, score, and the three tailored documents —
 * READ-ONLY in P2; editing is P3's invariant-sensitive work and there is deliberately no
 * edit affordance to remove later. Document text is untrusted display-only content: it is
 * rendered as inert text, never interpolated, never actionable.
 */
@Composable
fun ApplicationDetailScreen(
    application: ApplicationRow?,
    documents: List<DocumentRow>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back") }
        }

        if (application == null) {
            EmptyHint("This application is no longer in the replica.")
            return@Column
        }

        ScreenTitle(application.title)
        Text(application.company, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StateBadge(application.state)
            Text("score ${application.score}", style = MaterialTheme.typography.labelLarge)
        }

        Text("Documents (read-only)", style = MaterialTheme.typography.titleMedium)
        if (documents.isEmpty()) {
            EmptyHint("No documents for this application in the replica.")
        } else {
            documents.forEach { doc -> DocumentCard(doc) }
        }
    }
}

@Composable
private fun DocumentCard(doc: DocumentRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    // doc_kind vocabulary is pinned in Sync-Protocol.md §4.3: draft_email | cover_letter | resume_text.
                    when (doc.kind) {
                        "draft_email" -> "Draft email"
                        "cover_letter" -> "Cover letter"
                        "resume_text" -> "Resume"
                        else -> doc.kind
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("rev ${doc.rev}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(doc.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
