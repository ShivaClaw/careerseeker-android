package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every engine→phone [PayloadKind] is classified, and the classification is a partition.
 *
 * ## The defect this generalises
 *
 * B-19's finding (fifty-eighth run) was that `entitlement_ack` sat in this enum from
 * 2026-08-09 with a documented applier and **no production caller**. `SyncPump` hands every
 * accepted payload to one [ReplicaApplier]; `:app`'s is a `when` over four kinds with
 * `else -> Ignored(kind)`. An authentic ack decrypted, was accepted, was reported as the same
 * `IGNORED` that `doc` legitimately produces, and was dropped. Pro could not unlock, and no
 * layer reported a failure, because on every layer's own terms nothing failed.
 *
 * The reason it survived twenty-two runs of record-reading is that **nothing asked the
 * question**. A kind could be added to the enum, be spec'd, be vector-covered and be given an
 * applier without anyone ever having to state where a received one lands. The three sets this
 * test checks make that statement mandatory: add an engine→phone constant and this test fails
 * until it is placed.
 *
 * ## What this test does NOT prove, stated first so nobody reads it as more
 *
 * It does **not** prove a production caller exists. That is precisely what was missing before,
 * and `:core` cannot check it — the composition root is `:app` (**B-19**), and `:app` needs the
 * Android SDK this sandbox cannot reach (**B-7**). [PayloadKind.ROUTED_OUTSIDE_REPLICA] would
 * have contained `entitlement_ack` on 2026-08-09 and this test would have passed.
 *
 * The guard that holds the run-58 case shut from the other side is
 * `EntitlementRoutingApplierTest`'s negative control, which drives the un-decorated arrangement
 * and asserts the phone stays [ProState.Free]. **This test is the weaker, wider net**: it cannot
 * catch a route that is classified and unbuilt, and it does catch a kind that was never
 * classified at all — the state `entitlement_ack` was in before anyone looked.
 */
class PayloadKindCoverageTest {

    private val classified: List<Set<PayloadKind>> = listOf(
        PayloadKind.PROJECTED_BY_REPLICA,
        PayloadKind.ROUTED_OUTSIDE_REPLICA,
        PayloadKind.NOT_PROJECTED_IN_V1,
    )

    @Test
    fun `every engine to phone kind is classified exactly once`() {
        val union = classified.flatten()
        val unclassified = PayloadKind.ENGINE_TO_PHONE_KINDS - union.toSet()

        assertTrue(
            unclassified.isEmpty(),
            "engine->phone kinds with no declared destination: ${unclassified.map { it.wire }}. " +
                "Place each in PROJECTED_BY_REPLICA, ROUTED_OUTSIDE_REPLICA or NOT_PROJECTED_IN_V1. " +
                "A kind that is in none of them reaches the replica applier's `else` branch and is " +
                "dropped in silence -- that is B-19's defect, and this assertion is what makes " +
                "adding another one impossible without a decision.",
        )

        // Disjointness matters as much as coverage: a kind in two sets means two destinations
        // claim it, and the reader has no way to know which one runs.
        assertEquals(
            union.size,
            union.toSet().size,
            "a kind appears in more than one classification: " +
                "${union.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { it.wire }}",
        )
    }

    @Test
    fun `no phone to engine kind is classified as something the phone receives`() {
        val misplaced = classified.flatten().filter { it.flow != KindFlow.ENGINE_TO_PHONE }

        assertTrue(
            misplaced.isEmpty(),
            "these are not engine->phone kinds and cannot be received by the replica: " +
                "${misplaced.map { "${it.wire}(${it.flow})" }}",
        )
    }

    /**
     * The `flow` property replaced a `// engine -> phone` comment. This pins the mapping it
     * carries against §4.3's two tables, so the enum cannot be reordered into a wrong direction
     * as silently as a comment could drift above the wrong constant.
     */
    @Test
    fun `flow matches section 4-3's direction tables`() {
        assertEquals(
            setOf("snapshot", "delta", "doc", "evidence", "heartbeat", "conflict", "entitlement_ack"),
            PayloadKind.ENGINE_TO_PHONE_KINDS.map { it.wire }.toSet(),
        )
        assertEquals(
            setOf("doc_edit", "outcome", "entitlement", "pull_request"),
            PayloadKind.entries.filter { it.flow == KindFlow.PHONE_TO_ENGINE }.map { it.wire }.toSet(),
        )
        assertEquals(
            setOf("error"),
            PayloadKind.entries.filter { it.flow == KindFlow.BOTH }.map { it.wire }.toSet(),
        )
    }

    /**
     * Every kind that changes engine state travels phone→engine. §5.4 requires the device
     * signature on exactly those, so a state-changing kind pointed the other way would be a
     * signature rule with nothing to sign.
     */
    @Test
    fun `state-changing kinds are all phone to engine`() {
        val offenders = Protocol.STATE_CHANGING_KINDS
            .mapNotNull { PayloadKind.fromWire(it) }
            .filter { it.flow != KindFlow.PHONE_TO_ENGINE }

        assertTrue(offenders.isEmpty(), "state-changing but not phone->engine: ${offenders.map { it.wire }}")
    }

    /**
     * The four kinds the `:app` replica projects, pinned against `EnvelopeApplier`'s `when`.
     *
     * `:core` is Android-free and cannot read that `when`, so this is a mirror and is labelled
     * one. Its value is that the mirror is in a single place: before this, the same four-kind
     * list was prose in `SyncPump`'s KDoc, in `PullPolicy`'s KDoc and in `EntitlementRoute`'s.
     */
    @Test
    fun `the replica projects exactly the four kinds app's when branches on`() {
        assertEquals(
            setOf("snapshot", "delta", "heartbeat", "evidence"),
            PayloadKind.PROJECTED_BY_REPLICA.map { it.wire }.toSet(),
        )
    }
}
