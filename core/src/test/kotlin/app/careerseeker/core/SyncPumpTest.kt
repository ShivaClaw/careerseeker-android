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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine→phone transport loop ([SyncPump]), driven entirely against a MockEngine relay and a
 * fake replica.
 *
 * These tests exist because the four rules in [SyncPump]'s KDoc are **ordering** rules, and every
 * one of them fails silently when it is broken: the phone keeps rendering, keeps pulling, and
 * never reports anything. A stalled cursor, a gap measured against a stale position, a latch that
 * is never released, a sequence number taken from the relay instead of from the AEAD — none of
 * those throw. They are exactly the class of bug that has to be pinned by assertion or not at all.
 *
 * Envelopes here are **really sealed** with `SyncCrypto` and really opened by [EnvelopeReceiver],
 * so "the pump applied it" means the crypto agreed, not that a stub said yes.
 *
 * What these do **not** prove: the loop against a real relay or a real Room replica. The replica
 * is `:app`'s and needs a toolchain and an emulator this program does not have (B-4, B-7); the
 * relay half is proven separately by `relay/test/relay.test.ts` under miniflare. This file covers
 * the decisions in between, which is the whole reason they were moved into `:core`.
 */
class SyncPumpTest {

    private val pairing = "p_7Fq2mXk9LtVbN3wR"
    private val keyId = "k-2026-06-01"
    private val kE2p = hex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff")
    private val kP2e = hex("0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0")
    private val json = Json { ignoreUnknownKeys = true }

    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    // ---------------------------------------------------------------- fixtures

    /** One real engine→phone envelope, sealed exactly as the engine seals it. */
    private fun e2p(
        seq: Long,
        kind: String,
        body: String = "{}",
        ts: String = "2026-08-10T09:00:00Z",
        withKeyId: String = keyId,
    ): String {
        val header = EnvelopeHeader(Protocol.VERSION, pairing, Direction.ENGINE_TO_PHONE, seq, ts, withKeyId)
        val nonce = ByteArray(Protocol.NONCE_BYTES) { (seq + it).toByte() }
        val plaintext = """{"kind":"$kind","body":$body}"""
        val ciphertext = SyncCrypto.seal(kE2p, nonce, header.aad(), plaintext.toByteArray(Charsets.UTF_8))
        return """{"v":1,"pairing":"$pairing","dir":"e2p","seq":$seq,"ts":"$ts",""" +
            """"key_id":"$withKeyId","nonce":"${Base64Url.encode(nonce)}",""" +
            """"ciphertext":"${Base64Url.encode(ciphertext)}"}"""
    }

    /** A page in the shape the relay actually returns: envelopes spliced in verbatim. */
    private fun page(vararg wires: String, latest: Long) =
        """{"envelopes":[${wires.joinToString(",")}],"latest":$latest}"""

    /**
     * The `{seq, envelope}` wrapper — a shape **no implementation emits and §2.1 now forbids**
     * (PQ-S4-2). Kept only so a test can prove the pump refuses it; it used to be the one shape in
     * which the transport's `seq` and the envelope's own could disagree.
     */
    private fun wrappedPage(seq: Long, wire: String, latest: Long) =
        """{"envelopes":[{"seq":$seq,"envelope":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(wire))}}],"latest":$latest}"""

    /** Records what the pump asked the replica to do, and what it told the pump back. */
    private class FakeReplica(
        var position: ReplicaPosition,
        private val script: (Long, String) -> ApplyDisposition,
    ) : ReplicaApplier, ReplicaPositionSource {
        val applied = mutableListOf<Triple<Long, String, String>>() // seq, kind, ts
        /** Positions handed to the pump, in order — one per read. */
        val positionReads = mutableListOf<ReplicaPosition>()

        override suspend fun current(): ReplicaPosition = position.also { positionReads += it }

        override suspend fun apply(
            seq: Long,
            envelopeTs: String,
            kind: String,
            plaintext: ByteArray,
        ): ApplyDisposition {
            applied += Triple(seq, kind, envelopeTs)
            val disposition = script(seq, kind)
            // Mirror a real replica: an applied envelope moves the persisted mark, and a snapshot
            // additionally sets the flag. Anything refused leaves both untouched.
            if (disposition == ApplyDisposition.APPLIED || disposition == ApplyDisposition.APPLIED_SNAPSHOT) {
                position = ReplicaPosition(
                    snapshotSeen = position.snapshotSeen || disposition == ApplyDisposition.APPLIED_SNAPSHOT,
                    highestAppliedSeq = seq,
                )
            }
            return disposition
        }
    }

    /** A MockEngine relay: serves scripted pull pages in order, records every push. */
    private class FakeRelay(
        private val pages: List<String>,
        private val pullStatus: HttpStatusCode = HttpStatusCode.OK,
        private val pushStatus: HttpStatusCode = HttpStatusCode.Created,
    ) {
        val pullUrls = mutableListOf<String>()
        val pushes = mutableListOf<String>()
        private var pageIndex = 0

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/pull") -> {
                    pullUrls += request.url.toString()
                    val body = pages.getOrElse(pageIndex) { """{"envelopes":[],"latest":0}""" }
                    pageIndex++
                    respond(
                        ByteReadChannel(body), pullStatus,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                path.endsWith("/push") -> {
                    pushes += (request.body as io.ktor.http.content.TextContent).text
                    respond(
                        ByteReadChannel("""{"ok":true}"""), pushStatus,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                else -> respond(
                    ByteReadChannel("""{"error":"not_found"}"""), HttpStatusCode.NotFound,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
    }

    private fun pumpOver(
        relay: FakeRelay,
        replica: FakeReplica,
        signer: DeviceSigner? = null,
        policy: PullPolicy = PullPolicy(),
        activeKeyId: String = keyId,
    ): SyncPump {
        var seq = 0L
        return SyncPump(
            relay = RelayClient(
                HttpClient(relay.engine), "https://relay.careerseeker.app", pairing, "dGVzdC10b2tlbg",
                // Fast backoff so the transport-failure test does not sleep.
                RelayClient.RetryPolicy(attempts = 2, initialDelayMillis = 1, maxDelayMillis = 2),
            ),
            receiver = EnvelopeReceiver(activeKeyId),
            keyForDir = { dir -> if (dir == "e2p") kE2p else kP2e },
            applier = replica,
            position = replica,
            outbound = OutboundEnvelopeFactory(
                pairing, keyId, kP2e, { ++seq }, signer,
                { ByteArray(Protocol.NONCE_BYTES) { i -> i.toByte() } },
            ),
            clock = { "2026-08-10T09:30:00Z" },
            policy = policy,
        )
    }

    private fun cold(seq: Long = 0L) = ReplicaPosition(snapshotSeen = false, highestAppliedSeq = seq)
    private fun warm(seq: Long) = ReplicaPosition(snapshotSeen = true, highestAppliedSeq = seq)

    private fun sinceOf(url: String) = Regex("since=(\\d+)").find(url)!!.groupValues[1].toLong()

    private fun kindOfPush(wire: String): String {
        val env = json.parseToJsonElement(wire).jsonObject
        val header = EnvelopeHeader(
            env["v"]!!.jsonPrimitive.content.toInt(),
            env["pairing"]!!.jsonPrimitive.content,
            Direction.PHONE_TO_ENGINE,
            env["seq"]!!.jsonPrimitive.content.toLong(),
            env["ts"]!!.jsonPrimitive.content,
            env["key_id"]!!.jsonPrimitive.content,
        )
        val plain = SyncCrypto.open(
            kP2e,
            Base64Url.decodeOrNull(env["nonce"]!!.jsonPrimitive.content)!!,
            header.aad(),
            Base64Url.decodeOrNull(env["ciphertext"]!!.jsonPrimitive.content)!!,
        )
        return String(plain, Charsets.UTF_8)
    }

    // ---------------------------------------------------------------- open()

    @Test
    fun `opening a cold replica pushes exactly one unsigned pull_request for seq zero`() = runTest {
        val relay = FakeRelay(emptyList())
        val replica = FakeReplica(cold()) { _, _ -> ApplyDisposition.APPLIED }
        // No DeviceSigner at all: pull_request is not state-changing (§5.4), so this whole half of
        // S4 works before S3's Android Keystore key exists. If that ever stops being true, the
        // factory throws UnsignableEnvelope and this test is where it surfaces.
        val report = pumpOver(relay, replica, signer = null).open()

        assertEquals(PullReason.COLD_START, report.requestSent)
        assertNull(report.requestFailed)
        assertEquals(1, relay.pushes.size)
        assertEquals(
            """{"kind":"pull_request","body":{"since_seq":0}}""",
            kindOfPush(relay.pushes.single()),
        )
        assertTrue(
            "sig" !in json.parseToJsonElement(relay.pushes.single()).jsonObject.keys,
            "pull_request must not carry a device signature",
        )
    }

    @Test
    fun `opening a replica that already holds a snapshot asks nothing`() = runTest {
        val relay = FakeRelay(emptyList())
        val report = pumpOver(relay, FakeReplica(warm(10)) { _, _ -> ApplyDisposition.APPLIED }).open()

        assertNull(report.requestSent)
        assertEquals(0, relay.pushes.size)
    }

    @Test
    fun `open seeds the transport cursor from the persisted applied mark`() = runTest {
        val relay = FakeRelay(listOf(page(latest = 42)))
        val pump = pumpOver(relay, FakeReplica(warm(42)) { _, _ -> ApplyDisposition.APPLIED })

        pump.open()
        assertEquals(42L, pump.cursor)
        pump.pump()
        assertEquals(42L, sinceOf(relay.pullUrls.single()))
    }

    @Test
    fun `pump seeds the cursor even when open was never called`() = runTest {
        val relay = FakeRelay(listOf(page(latest = 7)))
        val pump = pumpOver(relay, FakeReplica(warm(7)) { _, _ -> ApplyDisposition.APPLIED })

        pump.pump()
        assertEquals(7L, sinceOf(relay.pullUrls.single()))
    }

    /**
     * Rule 3. Without this, one dropped push silences the policy for the life of the process: it
     * believes a request is outstanding that the engine never received, and the phone waits
     * forever for an answer to a question nobody was asked.
     */
    @Test
    fun `a pull_request the relay refused releases the latch instead of latching forever`() = runTest {
        val relay = FakeRelay(emptyList(), pushStatus = HttpStatusCode.ServiceUnavailable)
        val pump = pumpOver(relay, FakeReplica(cold()) { _, _ -> ApplyDisposition.APPLIED })

        val first = pump.open()
        assertEquals(PullReason.COLD_START, first.requestFailed)
        assertNull(first.requestSent)
        assertTrue(!pump.hasPendingRequest, "a request that never landed is not outstanding")
    }

    // ---------------------------------------------------------------- pump(): transport

    @Test
    fun `a relay that will not answer is reported, not guessed at`() = runTest {
        val relay = FakeRelay(listOf(page(latest = 0)), pullStatus = HttpStatusCode.ServiceUnavailable)
        val replica = FakeReplica(warm(1)) { _, _ -> ApplyDisposition.APPLIED }
        val report = pumpOver(relay, replica).pump()

        assertEquals(RelayFailure.UNAVAILABLE, report.pullFailure)
        assertEquals(0, report.pulled)
        assertEquals(0, replica.applied.size)
        assertEquals(0, relay.pushes.size, "a transport failure is not a reason to ask for a snapshot")
    }

    @Test
    fun `an unauthorised relay is a distinct failure from an unavailable one`() = runTest {
        val relay = FakeRelay(listOf(page(latest = 0)), pullStatus = HttpStatusCode.Unauthorized)
        val report = pumpOver(relay, FakeReplica(warm(1)) { _, _ -> ApplyDisposition.APPLIED }).pump()

        assertEquals(RelayFailure.UNAUTHORISED, report.pullFailure)
    }

    @Test
    fun `moreAvailable says whether the relay is still ahead of the cursor`() = runTest {
        val relay = FakeRelay(listOf(page(e2p(1, "snapshot"), latest = 9)))
        val replica = FakeReplica(cold()) { _, _ -> ApplyDisposition.APPLIED_SNAPSHOT }
        val report = pumpOver(relay, replica).pump()

        assertEquals(1L, report.cursor)
        assertEquals(9L, report.latest)
        assertTrue(report.moreAvailable, "the relay holds envelopes above the cursor; pump again")
    }

    // ---------------------------------------------------------------- rule 1: the cursor

    /**
     * Rule 1, in the form that actually bites. A `delta` before any snapshot is *accepted* by the
     * receiver and *refused* by the replica, so the persisted applied mark does not move. A cursor
     * driven by that mark re-fetches the same delta next cycle — where the receiver's in-process
     * replay window now rejects it — and the phone pulls the same page forever, applies nothing,
     * and reports no error at all.
     */
    @Test
    fun `the cursor advances past a delta the replica refused for want of a snapshot`() = runTest {
        val relay = FakeRelay(
            listOf(page(e2p(4, "delta"), latest = 4), page(latest = 4)),
        )
        val replica = FakeReplica(cold()) { _, _ -> ApplyDisposition.AWAITING_SNAPSHOT }
        val pump = pumpOver(relay, replica)

        val first = pump.pump()
        assertEquals(4L, first.cursor)
        assertEquals(PullReason.AWAITING_SNAPSHOT, first.requestSent)

        pump.pump()
        assertEquals(listOf(0L, 4L), relay.pullUrls.map(::sinceOf))
    }

    @Test
    fun `the cursor advances past an envelope the receiver rejected outright`() = runTest {
        // Sealed under a key_id this receiver does not consider active: rejected at §5's
        // revocation check, never reaches the replica.
        val relay = FakeRelay(
            listOf(page(e2p(3, "snapshot", withKeyId = "k-retired"), latest = 3), page(latest = 3)),
        )
        val replica = FakeReplica(warm(1)) { _, _ -> ApplyDisposition.APPLIED }
        val pump = pumpOver(relay, replica)

        val report = pump.pump()
        assertEquals(listOf(ErrorCode.KEY_UNKNOWN), report.rejections)
        assertEquals(0, replica.applied.size)
        assertEquals(3L, report.cursor)

        pump.pump()
        assertEquals(listOf(1L, 3L), relay.pullUrls.map(::sinceOf))
    }

    // ---------------------------------------------------------------- rule 2: position ordering

    /**
     * Rule 2. The position is read once per envelope, before that envelope is applied.
     *
     * Reading it once per *page* is the failure this pins: measured against the position at the
     * top of the page, the last of a run of contiguous envelopes looks `n` ahead of the mark, and
     * a page longer than the gap threshold fires a `pull_request` for a stream with no gap in it —
     * answered by a full snapshot, on every single sync.
     */
    @Test
    fun `a long contiguous page reports no gap, because the position is re-read per envelope`() = runTest {
        val wires = (2L..40L).map { e2p(it, "delta") }.toTypedArray()
        val relay = FakeRelay(listOf(page(*wires, latest = 40)))
        val replica = FakeReplica(warm(1)) { _, _ -> ApplyDisposition.APPLIED }
        // Threshold 5: far smaller than the page, so a page-scoped read could not fail to trip it.
        val report = pumpOver(relay, replica, policy = PullPolicy(gapThreshold = 5)).pump()

        assertNull(report.requestSent, "39 contiguous envelopes are not a gap")
        assertEquals(0, relay.pushes.size)
        assertEquals(39, replica.applied.size)
        // 39 envelopes + the one lazy seeding read `pump` does when `open` was never called. The
        // count is the assertion that matters: a page-scoped implementation reads once, total.
        assertEquals(40, replica.positionReads.size, "one position read per envelope, not per page")
    }

    @Test
    fun `a real gap larger than the threshold asks for a snapshot exactly once`() = runTest {
        val relay = FakeRelay(listOf(page(e2p(50, "delta"), e2p(51, "delta"), latest = 51)))
        val replica = FakeReplica(warm(1)) { _, _ -> ApplyDisposition.APPLIED }
        val report = pumpOver(relay, replica, policy = PullPolicy(gapThreshold = 5)).pump()

        assertEquals(PullReason.SEQUENCE_GAP, report.requestSent)
        assertEquals(1, relay.pushes.size, "the policy latches; a page is one ask, not one per envelope")
    }

    // ---------------------------------------------------------------- the latch across a page

    /**
     * A snapshot later on the same page satisfies the request an earlier delta triggered. Sending
     * the ask anyway would have the engine re-publish a snapshot the phone is already holding —
     * harmless once, and a permanent extra round trip on every cold start, which is the shape of
     * a bug nobody ever notices.
     */
    @Test
    fun `a snapshot later in the page cancels the request an earlier delta triggered`() = runTest {
        val relay = FakeRelay(listOf(page(e2p(1, "delta"), e2p(2, "snapshot"), latest = 2)))
        val replica = FakeReplica(cold()) { _, kind ->
            if (kind == "snapshot") ApplyDisposition.APPLIED_SNAPSHOT else ApplyDisposition.AWAITING_SNAPSHOT
        }
        val pump = pumpOver(relay, replica)
        val report = pump.pump()

        assertNull(report.requestSent)
        assertEquals(0, relay.pushes.size)
        assertTrue(!pump.hasPendingRequest, "the snapshot cleared the latch")
    }

    @Test
    fun `applied counts only the envelopes that changed the replica`() = runTest {
        val relay = FakeRelay(
            listOf(page(e2p(1, "snapshot"), e2p(2, "doc"), e2p(3, "heartbeat"), latest = 3)),
        )
        val replica = FakeReplica(cold()) { _, kind ->
            when (kind) {
                "snapshot" -> ApplyDisposition.APPLIED_SNAPSHOT
                "doc" -> ApplyDisposition.IGNORED
                else -> ApplyDisposition.APPLIED
            }
        }
        val report = pumpOver(relay, replica).pump()

        assertEquals(3, report.pulled)
        assertEquals(2, report.applied)
        assertEquals(emptyList(), report.rejections)
    }

    // ---------------------------------------------------------------- rule 4: whose seq is it

    /**
     * Rule 4. [RelayClient] accepts a page shape — `{seq, envelope}` — in which the relay's
     * reported sequence number and the envelope's own can disagree. The envelope's is
     * authenticated: it is in the AAD, so the AEAD tag covers it. The relay's is not covered by
     * anything.
     *
     * A cursor driven by the relay's number would jump to 999 here and the phone would never ask
     * for envelopes 6..999 again — a blind relay could silently truncate the stream without ever
     * touching a byte it is able to read.
     */
    @Test
    fun `the cursor follows the envelope's authenticated seq, and nothing else can supply one`() = runTest {
        // Rule 4 still holds — `header?.seq ?: envelope.seq` prefers the authenticated number —
        // but as of §2.1 (PQ-S4-2) it is defence in depth rather than the load-bearing check.
        // The wrapper was the only shape in which a page could carry a *second*, unauthenticated
        // sequence number beside the envelope's own. With it refused at the parser, an element IS
        // the envelope, so the two numbers are read off the same field and cannot disagree.
        val relay = FakeRelay(listOf(page(e2p(5, "snapshot"), latest = 999), page(latest = 999)))
        val replica = FakeReplica(cold()) { _, _ -> ApplyDisposition.APPLIED_SNAPSHOT }
        val pump = pumpOver(relay, replica)

        val report = pump.pump()
        // `latest` is 999 and the envelope says 5. The cursor takes the envelope's, never the
        // relay's high-water mark.
        assertEquals(5L, report.cursor, "999 came from the relay and is authenticated by nothing")
        assertEquals(listOf(5L), replica.applied.map { it.first })

        pump.pump()
        assertEquals(5L, sinceOf(relay.pullUrls[1]))
    }

    @Test
    fun `a wrapped envelope is never applied, because the wrapper is not an envelope`() = runTest {
        // The shape the pump used to unwrap and apply. §2.1 refuses it, so it reaches the receiver
        // as an unrecognised envelope and is discarded like any other garbled item.
        val relay = FakeRelay(
            listOf(wrappedPage(seq = 999, wire = e2p(5, "snapshot"), latest = 999), page(latest = 999)),
        )
        val replica = FakeReplica(cold()) { _, _ -> ApplyDisposition.APPLIED_SNAPSHOT }
        val pump = pumpOver(relay, replica)

        val report = pump.pump()
        assertEquals(0, replica.applied.size, "a wrapper must not reach the replica")
        assertEquals(listOf(ErrorCode.DECRYPT_FAILED), report.rejections)

        // Stated rather than hidden: the discarded item still advances the cursor, and with no
        // authenticated seq to use it advances to the element's *claimed* 999. That is the same
        // don't-stall-on-one-bad-byte rule as the test below, and it is the residual hazard
        // recorded as PQ-S4-3 — this slice narrowed the wrapper hole, it did not close that one.
        assertEquals(999L, report.cursor)
    }

    @Test
    fun `the ts handed to the replica is the envelope's own, and it is covered by the tag`() = runTest {
        val relay = FakeRelay(listOf(page(e2p(1, "snapshot", ts = "2026-08-10T11:22:33Z"), latest = 1)))
        val replica = FakeReplica(cold()) { _, _ -> ApplyDisposition.APPLIED_SNAPSHOT }
        pumpOver(relay, replica).pump()

        assertEquals("2026-08-10T11:22:33Z", replica.applied.single().third)
    }

    /**
     * §3's unknown-top-level-field rule, reaching the pump. A garbled item must not stall the
     * stream: it is discarded, recorded, and the cursor moves past it. The alternative — refusing
     * to advance until it parses — is a permanent stall on one bad byte, which is worse than
     * losing the one envelope.
     */
    @Test
    fun `an envelope that does not parse is discarded and does not stall the cursor`() = runTest {
        val malformed = """{"v":1,"pairing":"$pairing","dir":"e2p","seq":6,"ts":"2026-08-10T09:00:00Z",""" +
            """"key_id":"$keyId","nonce":"AAAAAAAAAAAAAAAA","ciphertext":"AAAA","surprise":"v2"}"""
        // A bare malformed envelope, not a wrapped one: §2.1 elements are bare, and wrapping this
        // would have tested the wrapper's rejection rather than the unknown-field rule it is here
        // for. Its own top-level `seq` (6) is what the cursor falls back to.
        val relay = FakeRelay(listOf(page(malformed, latest = 6), page(latest = 6)))
        val replica = FakeReplica(warm(1)) { _, _ -> ApplyDisposition.APPLIED }
        val pump = pumpOver(relay, replica)

        val report = pump.pump()
        assertEquals(listOf(ErrorCode.DECRYPT_FAILED), report.rejections)
        assertEquals(0, replica.applied.size)
        assertEquals(6L, report.cursor)

        pump.pump()
        assertEquals(6L, sinceOf(relay.pullUrls[1]))
    }

    // ---------------------------------------------------------------- failure mapping

    @Test
    fun `every non-Ok relay answer maps to a distinct failure, and Ok maps to none`() {
        assertNull(RelayFailure.fromRelayResult(RelayResult.Ok(Unit)))
        assertEquals(RelayFailure.PAIRING_UNKNOWN, RelayFailure.fromRelayResult(RelayResult.PairingUnknown))
        assertEquals(RelayFailure.UNAUTHORISED, RelayFailure.fromRelayResult(RelayResult.Unauthorised))
        assertEquals(RelayFailure.TOO_LARGE, RelayFailure.fromRelayResult(RelayResult.TooLarge))
        assertEquals(RelayFailure.CONFLICT, RelayFailure.fromRelayResult(RelayResult.Conflict()))
        assertEquals(RelayFailure.UNAVAILABLE, RelayFailure.fromRelayResult(RelayResult.Unavailable("x")))
    }
}
