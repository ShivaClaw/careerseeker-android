package app.careerseeker.dashboard.replica

import androidx.room.withTransaction

/**
 * Demo-mode fixture (P2-Runbook §2.4): populates the replica with representative data so
 * every screen is developable and CI-testable with no live engine, no pairing, and no relay.
 * This is what keeps the screen work off the critical path behind the device-bound pairing
 * UI.
 *
 * The rows deliberately cover every state a screen must render: an application in each
 * lifecycle bucket the dashboard distinguishes, a prompt-injection-flagged job and a repost
 * (the two honesty badges), and an audit-event trail for the Evidence screen. `sync_state`
 * is written with `demoMode = true` so screens can label the data honestly; applying any
 * real envelope clears the flag (and a real snapshot replaces the rows wholesale).
 *
 * Seeded at seq 0 so even the very first real envelope (seq 1) is newer than everything
 * here — demo data can never shadow live data.
 */
object DemoFixture {
    suspend fun seed(db: ReplicaDb) {
        db.withTransaction {
            val dao = db.dao()
            dao.clearApplications()
            dao.clearJobs()
            dao.clearEvidenceEvents()
            dao.clearDocuments()

            dao.upsertApplications(
                listOf(
                    ApplicationRow("app_demo_1", "DRAFTED", "Northwind Labs", "Senior Platform Engineer", 82, 0),
                    ApplicationRow("app_demo_2", "AWAITING_RESPONSE", "Fabrikam", "Staff Engineer, Infrastructure", 77, 0),
                    ApplicationRow("app_demo_3", "GATE_PENDING", "Contoso", "Principal SRE", 71, 0),
                    ApplicationRow("app_demo_4", "BLOCKED_FABRICATION", "Initech", "Platform Architect", 64, 0),
                    ApplicationRow("app_demo_5", "REJECTED_BY_ENGINE", "Globex", "DevOps Lead", 38, 0),
                    ApplicationRow("app_demo_6", "PAUSED", "Umbrella Research", "Site Reliability Engineer", 69, 0),
                ),
            )
            dao.upsertJobs(
                listOf(
                    JobRow("job_demo_1", "Northwind Labs", "Senior Platform Engineer", repost = false, injectionFlag = false, updatedSeq = 0),
                    JobRow("job_demo_2", "Fabrikam", "Staff Engineer, Infrastructure", repost = false, injectionFlag = false, updatedSeq = 0),
                    JobRow("job_demo_3", "Contoso", "Principal SRE", repost = true, injectionFlag = false, updatedSeq = 0),
                    JobRow("job_demo_4", "Wayne Industries", "Kubernetes Engineer", repost = false, injectionFlag = true, updatedSeq = 0),
                    JobRow("job_demo_5", "Globex", "DevOps Lead", repost = false, injectionFlag = false, updatedSeq = 0),
                ),
            )
            dao.upsertCounters(
                CountersRow(
                    discovered = 23,
                    acted = 6,
                    drafted = 4,
                    blocked = 1,
                    rejected = 9,
                    errors = 0,
                    cycles = 12,
                ),
            )
            dao.upsertEvidenceEvents(
                listOf(
                    EvidenceEventRow(1, "2026-07-23T09:00:11Z", "engine", "scout_ingest", "scout", "b41f"),
                    EvidenceEventRow(2, "2026-07-23T09:00:12Z", "engine", "job_scored", "job", "job_demo_1"),
                    EvidenceEventRow(3, "2026-07-23T09:00:14Z", "engine", "application_created", "application", "app_demo_1"),
                    EvidenceEventRow(4, "2026-07-23T09:00:19Z", "engine", "gate_passed", "application", "app_demo_1"),
                    EvidenceEventRow(5, "2026-07-23T09:00:21Z", "engine", "draft_created", "application", "app_demo_1"),
                    EvidenceEventRow(6, "2026-07-23T09:05:02Z", "engine", "gate_blocked_fabrication", "application", "app_demo_4"),
                    EvidenceEventRow(7, "2026-07-23T10:12:40Z", "user", "application_paused", "application", "app_demo_6"),
                ),
            )
            dao.upsertDocuments(
                listOf(
                    DocumentRow(
                        "app_demo_1", "resume",
                        "JORDAN LEE\nSenior Platform Engineer\n\nEXPERIENCE\nNorthwind Labs adjacent: 8 years building Kubernetes platforms, " +
                            "cut deploy lead time 40%, ran the on-call program for a 200-service fleet.\n\nSKILLS\nKubernetes, Go, Terraform, SLO design.",
                        rev = 1,
                    ),
                    DocumentRow(
                        "app_demo_1", "cover_letter",
                        "Dear Northwind Labs team,\n\nYour posting asks for someone who has run platform migrations without " +
                            "stopping the release train. I led exactly that at my current role and would bring the same discipline here.\n\nJordan",
                        rev = 1,
                    ),
                    DocumentRow(
                        "app_demo_1", "answers",
                        "Q: Are you authorized to work in the US?\nA: Yes.\n\nQ: Notice period?\nA: Two weeks.",
                        rev = 1,
                    ),
                ),
            )
            dao.upsertSyncState(
                SyncStateRow(
                    highestAppliedE2pSeq = 0,
                    lastSeenTs = "2026-07-23T10:12:40Z",
                    lastCycle = 12,
                    demoMode = true,
                    auditOk = true,
                ),
            )
        }
    }
}
