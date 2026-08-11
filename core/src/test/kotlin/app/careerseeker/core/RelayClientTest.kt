package app.careerseeker.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The relay client, exercised entirely against a MockEngine.
 *
 * No test here opens a socket. `relay.careerseeker.app` is production infrastructure and this
 * session is a client of it, not a load generator — a unit suite that dialled it would be both
 * rude and non-deterministic.
 */
class RelayClientTest {

    private val pairing = "p_7Fq2mXk9LtVbN3wR"
    private val bearer = "dGVzdC10b2tlbg"

    private fun clientOver(engine: MockEngine, base: String = "https://relay.careerseeker.app") =
        RelayClient(
            HttpClient(engine), base, pairing, bearer,
            // Fast backoff so the retry tests do not sleep for eight seconds.
            RelayClient.RetryPolicy(attempts = 3, initialDelayMillis = 1, maxDelayMillis = 4),
        )

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine {
        respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    // ---------------------------------------------------------------- transport rules

    @Test
    fun `cleartext is refused at construction, not at request time`() {
        // §2: "Transport is HTTPS/WSS only. Clients MUST reject cleartext." Failing at
        // construction means no retry path can ever skip the check.
        val e = assertFailsWith<IllegalArgumentException> {
            RelayClient(HttpClient(json("{}")), "http://relay.careerseeker.app", pairing, bearer)
        }
        assertTrue(e.message!!.contains("TLS"))
    }

    @Test
    fun `the live route is wss, derived from the same base as the https routes`() {
        val client = clientOver(json("{}"))
        assertEquals("wss://relay.careerseeker.app/v1/$pairing/live", client.liveUrl())
    }

    @Test
    fun `every authenticated route carries the derived bearer`() = runTest {
        val seen = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seen += request.headers[HttpHeaders.Authorization]
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = clientOver(engine)

        client.push("""{"v":1}""")
        client.pull(Direction.ENGINE_TO_PHONE, 0)
        client.unpair()

        assertEquals(3, seen.size)
        assertTrue(seen.all { it == "Bearer $bearer" }, "got $seen")
    }

    @Test
    fun `health needs no bearer, because it reveals no pairing`() = runTest {
        var authorization: String? = "not-null"
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }
        clientOver(engine).health()
        assertEquals(null, authorization)
    }

    @Test
    fun `the bearer never appears in toString`() {
        // Cheap, and the failure it prevents is a token in a crash report or a log line.
        val rendered = clientOver(json("{}")).toString()
        assertTrue(!rendered.contains(bearer), "bearer leaked: $rendered")
        assertTrue(rendered.contains("redacted"))
    }

    // ---------------------------------------------------------------- pull semantics

    @Test
    fun `pull returns envelopes unparsed, plus the relay's high-water mark`() = runTest {
        // §2.1: an element IS a bare envelope. The client is a dumb pipe by design — it hands wire
        // text to the receiver, which owns every trust decision. The payloads below are nonsense
        // on purpose; the client must not care.
        val body = """
            {"latest":48,"envelopes":[
              {"v":1,"pairing":"$pairing","dir":"e2p","seq":47},
              {"v":1,"pairing":"$pairing","dir":"e2p","seq":48}]}
        """.trimIndent()

        val result = clientOver(json(body)).pull(Direction.ENGINE_TO_PHONE, 46)
        val page = (result as RelayResult.Ok).value

        assertEquals(48L, page.latest)
        assertEquals(listOf(47L, 48L), page.envelopes.map { it.seq })
        assertTrue(page.envelopes[0].wire.contains("\"seq\":47"))
        // Forwarded whole, not sliced or re-shaped: the receiver parses this object and nothing
        // around it.
        assertTrue(page.envelopes[0].wire.contains("\"pairing\":\"$pairing\""))
    }

    @Test
    fun `a wrapped envelope is refused end to end, even when the envelope inside it is valid`() =
        runTest {
            // PQ-S4-2, and the inner envelope below is deliberately *structurally valid* — that is
            // the whole point. Under the old client this element was unwrapped and the receiver
            // got a page it could parse, so the shape worked and nobody could see that no
            // implementation emits it. It now fails at the receiver's strict §3 parse, where an
            // unrecognised envelope shape belongs.
            val inner = """
                {"v":1,"pairing":"$pairing","dir":"e2p","seq":47,"ts":"2026-06-11T14:02:11Z",
                 "key_id":"k-2026-06-01","nonce":"AAAAAAAAAAAAAAAA","ciphertext":"AAAA"}
            """.trimIndent().replace("\n", "").replace(" ", "")
            // The inner object on its own is what a conforming relay would have sent, and it parses.
            assertTrue(EnvelopeJson.parse(inner).ok)

            val page = (clientOver(json("""{"latest":48,"envelopes":[{"seq":999,"envelope":$inner}]}"""))
                .pull(Direction.ENGINE_TO_PHONE, 46) as RelayResult.Ok).value

            // The whole element is the wire, wrapper and all — the inner object is NOT extracted.
            assertTrue(page.envelopes[0].wire.contains("\"envelope\""), page.envelopes[0].wire)
            assertEquals(null, EnvelopeJson.parse(page.envelopes[0].wire).envelope)

            // `seq` is read off the element's own top level, always — there is no second number
            // to disagree with it now. Stated exactly: this 999 is unauthenticated and no trust
            // decision consumes it, but it IS what SyncPump falls back to for an envelope that
            // does not parse, and that fallback is its own open question (PQ-S4-3), not this
            // test's subject.
            assertEquals(999L, page.envelopes[0].seq)
        }

    @Test
    fun `pull sends the direction and cursor the caller asked for`() = runTest {
        var url = ""
        val engine = MockEngine { request ->
            url = request.url.toString()
            respond(ByteReadChannel("""{"latest":0,"envelopes":[]}"""), HttpStatusCode.OK)
        }
        clientOver(engine).pull(Direction.PHONE_TO_ENGINE, 12)
        assertTrue(url.contains("since=12"), url)
        assertTrue(url.contains("dir=p2e"), url)
    }

    @Test
    fun `an empty queue is a successful empty page, not an error`() = runTest {
        val result = clientOver(json("""{"latest":9,"envelopes":[]}""")).pull(Direction.ENGINE_TO_PHONE, 9)
        val page = (result as RelayResult.Ok).value
        assertTrue(page.envelopes.isEmpty())
        assertEquals(9L, page.latest)
    }

    // ------------------------------------------------- the page body is untrusted input

    // §2 makes the relay a blind pipe, which means it is also an untrusted one: it controls this
    // body completely. Every test below feeds a 200 with a body the relay should never send, and
    // asserts the client reports rather than throws. Before this slice all of them threw out of
    // `pull` entirely -- `.map` runs outside `request`'s try/catch -- so the exception escaped the
    // RelayResult contract and reached whatever coroutine called the pump.

    @Test
    fun `a page body that is not JSON is reported, never thrown`() = runTest {
        // The realistic trigger needs no attacker at all: an intercepting proxy or a CDN serving
        // its own error page with a 200 status.
        val result = clientOver(json("<html>502 Bad Gateway</html>")).pull(Direction.ENGINE_TO_PHONE, 0)
        assertTrue(result is RelayResult.Unavailable, "got $result")
        assertTrue(result.detail.contains("malformed pull page"), result.detail)
    }

    @Test
    fun `every structurally wrong page is an Unavailable, and none of them escapes as an exception`() = runTest {
        // One assertion per shape, because "it throws somewhere" was the old behaviour for all of
        // them and a single example would not have shown how wide the hole was.
        val bodies = listOf(
            "" to "empty body",
            "[]" to "array at the root",
            "\"page\"" to "string at the root",
            """{"envelopes":{},"latest":3}""" to "envelopes is not an array",
            """{"envelopes":["abc"],"latest":3}""" to "an element is not an object",
            """{"envelopes":[],"latest":{}}""" to "latest is not a number",
        )
        for ((body, label) in bodies) {
            // runCatching, not a bare call: the point of the assertion is that nothing throws,
            // and a bare call would fail the test with a stack trace instead of a verdict.
            val outcome = runCatching { clientOver(json(body)).pull(Direction.ENGINE_TO_PHONE, 0) }
            val result = outcome.getOrElse { fail("$label threw ${it::class.simpleName}: ${it.message}") }
            assertTrue(result is RelayResult.Unavailable, "$label produced $result")
        }
    }

    @Test
    fun `a page missing latest is rejected, because defaulting it to zero fakes being caught up`() = runTest {
        // This is the one whose old behaviour was silent rather than loud, and it is the worst of
        // the set. `latest` drives moreAvailable and §6.2's gap check, so a relay that simply
        // omits the field used to convince the phone it had everything -- a stall with no error,
        // caused by deleting one field. The engine has always refused this (GetProperty throws).
        val result = clientOver(json("""{"envelopes":[]}""")).pull(Direction.ENGINE_TO_PHONE, 0)
        assertTrue(result is RelayResult.Unavailable, "got $result")
    }

    @Test
    fun `a page missing envelopes is rejected, not read as an empty queue`() = runTest {
        // Silent again, and worse than it looks: it used to report a successful EMPTY page while
        // carrying a latest above the cursor, so the caller saw "nothing to do" and "the relay is
        // ahead of you" at the same time.
        val result = clientOver(json("""{"latest":4}""")).pull(Direction.ENGINE_TO_PHONE, 0)
        assertTrue(result is RelayResult.Unavailable, "got $result")
    }

    @Test
    fun `a quoted latest is refused, because the engine's GetInt64 refuses it`() = runTest {
        // Engine-compatible interpretation rule: src/Sync/RelayClient.cs reads
        // GetProperty("latest").GetInt64(), which throws on a JSON string. A phone that accepted
        // "9" would be more permissive than the engine, and a phone more correct than the engine
        // is a field bug.
        val result = clientOver(json("""{"envelopes":[],"latest":"9"}""")).pull(Direction.ENGINE_TO_PHONE, 0)
        assertTrue(result is RelayResult.Unavailable, "got $result")
    }

    @Test
    fun `one unusable element rejects the whole page, and never just itself`() = runTest {
        // The skip-and-continue version of this function compiles, renders correctly, and loses
        // envelopes silently: the cursor advances past everything SEEN, so dropping seq 47 and
        // keeping 48 skips 47 forever. That is the truncation attack SyncPump already refuses in
        // its other form (it reads the authenticated seq, never the relay's) -- a blind relay that
        // wants an envelope skipped would only have to corrupt it.
        val body = """{"latest":48,"envelopes":[
            {"v":1,"dir":"e2p","seq":47},
            "corrupted",
            {"v":1,"dir":"e2p","seq":48}]}"""

        val result = clientOver(json(body)).pull(Direction.ENGINE_TO_PHONE, 46)

        assertTrue(result is RelayResult.Unavailable, "a partial page was accepted: $result")
    }

    @Test
    fun `an unusable per-element seq does not reject the page, because nothing authenticated reads it`() = runTest {
        // The deliberate asymmetry with the test above. SyncPump takes the seq out of the sealed
        // bytes and ignores this one (it is covered by no tag), and the engine's reader does not
        // look at a per-element seq at all -- so rejecting over it would be stricter than the
        // engine on a field no trust decision consumes. It reads as 0 and the wire still flows.
        val body = """{"latest":48,"envelopes":[{"seq":"47","envelope":{"v":1,"dir":"e2p"}}]}"""

        val page = (clientOver(json(body)).pull(Direction.ENGINE_TO_PHONE, 46) as RelayResult.Ok).value

        assertEquals(1, page.envelopes.size)
        assertEquals(0L, page.envelopes[0].seq)
        assertTrue(page.envelopes[0].wire.contains("\"dir\":\"e2p\""), page.envelopes[0].wire)
    }

    @Test
    fun `the failure detail carries a diagnosis and no relay bytes`() = runTest {
        // The detail can reach a log line. The body it describes is ciphertext plus routing
        // metadata, so it must not be echoed into one.
        val secretish = """{"envelopes":[],"latest":"k_2f8a1c-not-a-number"}"""
        val result = clientOver(json(secretish)).pull(Direction.ENGINE_TO_PHONE, 0) as RelayResult.Unavailable
        assertTrue(!result.detail.contains("k_2f8a1c"), "body echoed into the detail: ${result.detail}")
    }

    // ---------------------------------------------------------------- status mapping

    @Test
    fun `relay answers map to the decision the caller has to make`() = runTest {
        suspend fun pushWith(status: HttpStatusCode): RelayResult<Unit> =
            clientOver(MockEngine { respondError(status) }).push("{}")

        assertEquals(RelayResult.PairingUnknown, pushWith(HttpStatusCode.NotFound))
        assertEquals(RelayResult.Unauthorised, pushWith(HttpStatusCode.Unauthorized))
        assertEquals(RelayResult.Unauthorised, pushWith(HttpStatusCode.Forbidden))
        assertEquals(RelayResult.TooLarge, pushWith(HttpStatusCode.PayloadTooLarge))
        assertEquals(RelayResult.Conflict(), pushWith(HttpStatusCode.Conflict))
    }

    @Test
    fun `a replay_rejected 409 carries the relay's high-water mark, which is the reconciliation input`() = runTest {
        // Without this number a sender whose persisted p2e counter (§6.1) has fallen behind can
        // only retry an envelope the relay refuses forever. The relay already sends it; this
        // client used to throw it away with the rest of the body.
        val engine = json("""{"error":"replay_rejected","latest":41}""", HttpStatusCode.Conflict)
        assertEquals(RelayResult.Conflict(latest = 41L), clientOver(engine).push("{}"))
    }

    @Test
    fun `a pairing 409 reports no mark, because there is nothing to reconcile against`() = runTest {
        // §5.2.1 / §5.2.2 answer {"error":"exists"} — "already done", not "your counter is behind".
        val engine = json("""{"error":"exists"}""", HttpStatusCode.Conflict)
        assertEquals(RelayResult.Conflict(latest = null), clientOver(engine).submitPairing("{}"))
    }

    @Test
    fun `a 409 whose body is not JSON is still a conflict, never an unavailability`() = runTest {
        // Turning a relay decision into a transport exception would make the caller retry
        // something the relay has already refused.
        val engine = json("<html>gateway</html>", HttpStatusCode.Conflict)
        assertEquals(RelayResult.Conflict(latest = null), clientOver(engine).push("{}"))
    }

    @Test
    fun `a 4xx is a decision and is never retried`() = runTest {
        // Retrying "not ever" burns battery and adds load to a relay that already answered.
        var calls = 0
        val engine = MockEngine { calls++; respondError(HttpStatusCode.NotFound) }
        val result = clientOver(engine).push("{}")

        assertEquals(RelayResult.PairingUnknown, result)
        assertEquals(1, calls)
    }

    @Test
    fun `a 5xx is retried with backoff and then reported unavailable`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respondError(HttpStatusCode.BadGateway) }
        val result = clientOver(engine).push("{}")

        assertEquals(3, calls) // the configured attempt budget, not one try
        assertTrue(result is RelayResult.Unavailable)
        assertTrue((result as RelayResult.Unavailable).detail.contains("502"))
    }

    @Test
    fun `a transient failure that recovers within the budget succeeds`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls < 3) respondError(HttpStatusCode.ServiceUnavailable)
            else respond(ByteReadChannel("""{"latest":1,"envelopes":[]}"""), HttpStatusCode.OK)
        }
        val result = clientOver(engine).pull(Direction.ENGINE_TO_PHONE, 0)

        assertTrue(result is RelayResult.Ok, "expected recovery, got $result")
        assertEquals(3, calls)
    }

    @Test
    fun `a thrown transport error is retried, not propagated`() = runTest {
        // An offline phone is the normal case, not an exception the UI should crash on.
        var calls = 0
        val engine = MockEngine { calls++; throw java.io.IOException("network is unreachable") }
        val result = clientOver(engine).push("{}")

        assertEquals(3, calls)
        assertTrue(result is RelayResult.Unavailable)
    }

    @Test
    fun `a malformed pairing id is refused before any request`() {
        assertFailsWith<IllegalArgumentException> {
            RelayClient(HttpClient(json("{}")), "https://relay.careerseeker.app", "nope", bearer)
        }
    }
}
