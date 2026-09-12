package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [EntitlementRoutingApplier] — the wiring that makes `entitlement_ack` reach [ProState].
 *
 * These tests exist because the bug they pin **cannot be seen from any single layer**. Before this
 * class, a correctly sealed ack was accepted by [EnvelopeReceiver], handed to the replica applier,
 * matched no branch there, and was reported as [ApplyDisposition.IGNORED] — a disposition that is
 * completely normal for `doc` and `conflict`. Every layer behaved exactly as specified and the
 * user stayed on Free forever. Nothing threw, nothing was rejected, and no counter went wrong.
 *
 * So the first test below is deliberately a **negative control**: it drives the un-decorated
 * arrangement and asserts the phone stays [ProState.Free]. If a later refactor makes the route
 * unnecessary, that test fails and says so, which is the only way a test suite can hold a gap
 * shut that no layer considers a gap.
 *
 * The envelopes here are really sealed with [SyncCrypto] and really opened by [EnvelopeReceiver]
 * through a real [SyncPump], so "the ack unlocked Pro" means the crypto and the whole pull loop
 * agreed — not that a stub returned a plaintext.
 */
class EntitlementRoutingApplierTest {

    private val pairing = "p_7Fq2mXk9LtVbN3wR"
    private val keyId = "k-2026-06-01"
    private val kE2p = hex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff")
    private val kP2e = hex("0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0")

    private val product = "pro_unlock"
    private val ackedAt = "2026-08-18T11:00:00Z"

    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    private fun ackBody(
        productId: String = product,
        acknowledgedAt: String = ackedAt,
        orderId: String? = "GPA.3312-5567-1120-88901",
    ) = buildString {
        append("""{"product_id":"$productId","acknowledged_at":"$acknowledgedAt"""")
        if (orderId != null) append(""","order_id":"$orderId"""")
        append("}")
    }

    // ---------------------------------------------------------------- fixtures

    /** One real engine→phone envelope, sealed exactly as the engine seals it. */
    private fun e2p(seq: Long, kind: String, body: String = "{}", ts: String = "2026-08-18T09:00:00Z"): String {
        val header = EnvelopeHeader(Protocol.VERSION, pairing, Direction.ENGINE_TO_PHONE, seq, ts, keyId)
        val nonce = ByteArray(Protocol.NONCE_BYTES) { (seq + it).toByte() }
        val plaintext = """{"kind":"$kind","body":$body}"""
        val ciphertext = SyncCrypto.seal(kE2p, nonce, header.aad(), plaintext.toByteArray(Charsets.UTF_8))
        return """{"v":1,"pairing":"$pairing","dir":"e2p","seq":$seq,"ts":"$ts",""" +
            """"key_id":"$keyId","nonce":"${Base64Url.encode(nonce)}",""" +
            """"ciphertext":"${Base64Url.encode(ciphertext)}"}"""
    }

    private fun page(vararg wires: String, latest: Long) =
        """{"envelopes":[${wires.joinToString(",")}],"latest":$latest}"""

    /**
     * The `:app` replica applier, reduced to the only property that matters here: it projects the
     * four kinds it knows and reports everything else as [ApplyDisposition.IGNORED]. That `else`
     * branch is not a simplification for the test — it is what
     * `app/src/main/kotlin/app/careerseeker/dashboard/replica/EnvelopeApplier.kt` actually does.
     */
    private class FakeReplica(var position: ReplicaPosition) : ReplicaApplier, ReplicaPositionSource {
        val seen = mutableListOf<String>()

        override suspend fun current(): ReplicaPosition = position

        override suspend fun apply(
            seq: Long,
            envelopeTs: String,
            kind: String,
            plaintext: ByteArray,
        ): ApplyDisposition {
            seen += kind
            return when (kind) {
                "snapshot" -> {
                    position = ReplicaPosition(snapshotSeen = true, highestAppliedSeq = seq)
                    ApplyDisposition.APPLIED_SNAPSHOT
                }

                "delta", "heartbeat", "evidence" -> {
                    position = ReplicaPosition(position.snapshotSeen, seq)
                    ApplyDisposition.APPLIED
                }

                else -> ApplyDisposition.IGNORED
            }
        }
    }

    private class FakeProStateStore(initial: ProState = ProState.Free) : ProStateStore {
        var state: ProState = initial
            private set
        var writes = 0
            private set
        var reads = 0
            private set

        override suspend fun current(): ProState = state.also { reads++ }

        override suspend fun store(state: ProState) {
            this.state = state
            writes++
        }
    }

    private fun relayServing(vararg pages: String): Pair<MockEngine, MutableList<String>> {
        val pushes = mutableListOf<String>()
        var index = 0
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/pull") -> {
                    val body = pages.getOrElse(index) { """{"envelopes":[],"latest":0}""" }
                    index++
                    respond(
                        ByteReadChannel(body), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                path.endsWith("/push") -> {
                    pushes += (request.body as io.ktor.http.content.TextContent).text
                    respond(
                        ByteReadChannel("""{"ok":true}"""), HttpStatusCode.Created,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                else -> respond(
                    ByteReadChannel("""{"error":"not_found"}"""), HttpStatusCode.NotFound,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        return engine to pushes
    }

    private fun pumpOver(engine: MockEngine, applier: ReplicaApplier, position: ReplicaPositionSource): SyncPump {
        var seq = 0L
        return SyncPump(
            relay = RelayClient(
                HttpClient(engine), "https://relay.careerseeker.app", pairing, "dGVzdC10b2tlbg",
                RelayClient.RetryPolicy(attempts = 2, initialDelayMillis = 1, maxDelayMillis = 2),
            ),
            receiver = EnvelopeReceiver(keyId),
            keyForDir = { dir -> if (dir == "e2p") kE2p else kP2e },
            applier = applier,
            position = position,
            outbound = OutboundEnvelopeFactory(
                pairing, keyId, kP2e, { ++seq }, null,
                { ByteArray(Protocol.NONCE_BYTES) { i -> i.toByte() } },
            ),
            clock = { "2026-08-18T09:30:00Z" },
        )
    }

    private fun route(replica: ReplicaApplier, store: ProStateStore) =
        EntitlementRoutingApplier(replica, EntitlementAckApplier(setOf(product)), store)

    private fun warm(seq: Long) = ReplicaPosition(snapshotSeen = true, highestAppliedSeq = seq)

    // ------------------------------------------------- the gap, and the closing of it

    /**
     * **Negative control — this is the bug.** Exactly the arrangement that existed before
     * [EntitlementRoutingApplier]: a real ack, a real pump, the replica applier wired straight in.
     * The envelope is accepted, the replica reports the same `IGNORED` it reports for `doc`, and
     * the user is still on Free. Nothing in the report distinguishes this from a healthy cycle.
     */
    @Test
    fun `without the route an accepted ack is dropped and the phone stays Free`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(warm(40))
        val (engine, _) = relayServing(page(e2p(41, "entitlement_ack", ackBody()), latest = 41))

        val report = pumpOver(engine, replica, replica).pump()

        assertEquals(1, report.pulled)
        assertTrue(report.rejections.isEmpty(), "the envelope is authentic; nothing rejected it")
        assertEquals(listOf("entitlement_ack"), replica.seen, "it did reach the replica applier")
        assertEquals(ProState.Free, store.state, "and the replica had nowhere to put it")
        assertEquals(0, store.writes)
    }

    /** The same envelope, the same pump, with the route in place. */
    @Test
    fun `an accepted ack unlocks Pro end to end through the pump`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(warm(40))
        val (engine, _) = relayServing(page(e2p(41, "entitlement_ack", ackBody()), latest = 41))

        val report = pumpOver(engine, route(replica, store), replica).pump()

        assertEquals(1, report.pulled)
        assertTrue(report.rejections.isEmpty())
        assertEquals(ProState.Unlocked(product, ackedAt), store.state)
        assertTrue(store.state.isPro)
        assertEquals(1, store.writes)
    }

    // ------------------------------------------------- the disposition, and why it is IGNORED

    /**
     * The route reports [ApplyDisposition.IGNORED] for an ack it *did* honour, and this test pins
     * the consequence rather than the enum value — an assertion on the name alone would not say
     * why changing it is unsafe.
     *
     * [PullPolicy] is the only consumer of the disposition, and it measures replica progress. An
     * ack never advances the replica's `highestAppliedSeq`, so reporting `APPLIED` invites the
     * policy to measure a gap that the envelope causing it can never close.
     */
    @Test
    fun `reporting an ack as APPLIED would manufacture a sequence-gap request`() {
        val positionBefore = warm(seq = 40)

        val honest = PullPolicy(gapThreshold = 5).onEnvelope(200L, ApplyDisposition.IGNORED, positionBefore)
        assertEquals(PullDecision.None, honest, "an ack is not replica progress and must not ask for traffic")

        val ifApplied = PullPolicy(gapThreshold = 5).onEnvelope(200L, ApplyDisposition.APPLIED, positionBefore)
        assertEquals(PullDecision.Request(0L, PullReason.SEQUENCE_GAP), ifApplied)
        assertNotEquals(honest, ifApplied, "the two dispositions are not interchangeable here")
    }

    /** The end-to-end form of the same rule: an honoured ack pushes nothing back at the engine. */
    @Test
    fun `an honoured ack sends no pull_request`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(warm(40))
        val (engine, pushes) = relayServing(page(e2p(900, "entitlement_ack", ackBody()), latest = 900))

        pumpOver(engine, route(replica, store), replica).pump()

        assertTrue(store.state.isPro, "precondition: the ack was honoured")
        assertTrue(pushes.isEmpty(), "a 860-wide apparent gap, and correctly no request: $pushes")
    }

    // ------------------------------------------------- routing

    @Test
    fun `every other kind reaches the replica untouched and never reads Pro state`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(warm(10))
        val (engine, _) = relayServing(
            page(
                e2p(11, "snapshot", """{"applications":[],"jobs":[],"documents":[],"evidence":[]}"""),
                e2p(12, "heartbeat"),
                e2p(13, "doc"),
                latest = 13,
            ),
        )

        pumpOver(engine, route(replica, store), replica).pump()

        assertEquals(listOf("snapshot", "heartbeat", "doc"), replica.seen)
        assertEquals(0, store.reads, "a non-ack payload must not so much as read the Pro state")
        assertEquals(0, store.writes)
    }

    @Test
    fun `the delegate's disposition is returned unchanged`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(ReplicaPosition(snapshotSeen = false, highestAppliedSeq = 0))
        val routed = route(replica, store)

        assertEquals(
            ApplyDisposition.APPLIED_SNAPSHOT,
            routed.apply(1L, "2026-08-18T09:00:00Z", "snapshot", ByteArray(0)),
            "swallowing APPLIED_SNAPSHOT would leave PullPolicy's latch stuck forever",
        )
        assertEquals(ApplyDisposition.APPLIED, routed.apply(2L, "2026-08-18T09:00:00Z", "delta", ByteArray(0)))
        assertEquals(ApplyDisposition.IGNORED, routed.apply(3L, "2026-08-18T09:00:00Z", "conflict", ByteArray(0)))
    }

    // ------------------------------------------------- §4.3.3: ignore rather than unlock

    @Test
    fun `an unknown product id writes nothing and leaves the state alone`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(warm(40))
        val body = ackBody(productId = "pro_from_a_newer_engine")

        val disposition = route(replica, store)
            .apply(41L, "2026-08-18T09:00:00Z", "entitlement_ack", """{"kind":"entitlement_ack","body":$body}""".encodeToByteArray())

        assertEquals(ApplyDisposition.IGNORED, disposition)
        assertEquals(ProState.Free, store.state)
        assertEquals(0, store.writes, "a newer engine is not a defect and must not write")
    }

    /**
     * Dispatch is not authorisation. The receiver reports the `kind` from the envelope's own
     * top-level field; [EntitlementAckApplier] re-checks it inside the body. A heartbeat body
     * routed here — by a future dispatch bug, or by a caller that guessed — unlocks nothing.
     */
    @Test
    fun `a non-ack body delivered under the ack kind unlocks nothing`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(warm(40))
        val heartbeat = """{"kind":"heartbeat","body":{"product_id":"$product","acknowledged_at":"$ackedAt"}}"""

        val disposition = route(replica, store)
            .apply(41L, "2026-08-18T09:00:00Z", "entitlement_ack", heartbeat.encodeToByteArray())

        assertEquals(ApplyDisposition.IGNORED, disposition)
        assertEquals(ProState.Free, store.state)
        assertEquals(0, store.writes)
    }

    @Test
    fun `a re-delivered ack does not write again`() = runTest {
        val store = FakeProStateStore()
        val replica = FakeReplica(warm(40))
        val routed = route(replica, store)
        val wire = """{"kind":"entitlement_ack","body":${ackBody()}}""".encodeToByteArray()

        routed.apply(41L, "2026-08-18T09:00:00Z", "entitlement_ack", wire)
        routed.apply(41L, "2026-08-18T09:00:00Z", "entitlement_ack", wire)
        routed.apply(42L, "2026-08-18T09:05:00Z", "entitlement_ack", wire)

        assertEquals(ProState.Unlocked(product, ackedAt), store.state)
        assertEquals(1, store.writes, "re-delivery is normal after a restart clears the replay window")
    }

    /**
     * Nothing arriving on this route moves state downward. The applier has no path to
     * [ProState.Free] or [ProState.Rejected], and the router adds none: an unlocked phone handed
     * an ack it cannot honour stays unlocked.
     */
    @Test
    fun `an unhonourable ack cannot un-unlock an already unlocked phone`() = runTest {
        val unlocked = ProState.Unlocked(product, ackedAt)
        val store = FakeProStateStore(unlocked)
        val replica = FakeReplica(warm(40))
        val routed = route(replica, store)

        val foreign = """{"kind":"entitlement_ack","body":${ackBody(productId = "something_else")}}"""
        routed.apply(50L, "2026-08-18T09:00:00Z", "entitlement_ack", foreign.encodeToByteArray())
        routed.apply(51L, "2026-08-18T09:00:00Z", "entitlement_ack", "not json at all".encodeToByteArray())
        routed.apply(52L, "2026-08-18T09:00:00Z", "entitlement_ack", ByteArray(0))

        assertEquals(unlocked, store.state)
        assertEquals(0, store.writes)
    }

    /**
     * PQ-A2-4's boundary, restated where it is now reachable: the *only* input that produces
     * [ProState.Unlocked] is an engine ack that arrived through this route. A locally-accepted
     * receipt reaches [ProState.AwaitingEngine] and stops there.
     */
    @Test
    fun `a local ACCEPTED verdict still unlocks nothing`() = runTest {
        val store = FakeProStateStore(ProState.afterLocalPrescreen(EntitlementVerdict.ACCEPTED))
        val replica = FakeReplica(warm(40))

        assertEquals(ProState.AwaitingEngine, store.state)
        assertTrue(!store.state.isPro)

        val (engine, _) = relayServing(page(e2p(41, "entitlement_ack", ackBody()), latest = 41))
        pumpOver(engine, route(replica, store), replica).pump()

        assertEquals(ProState.Unlocked(product, ackedAt), store.state, "and the engine's ack is what moves it")
    }
}
