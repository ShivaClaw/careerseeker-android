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
        // The client is a dumb pipe by design: it hands wire text to the receiver, which owns
        // every trust decision. Note the payload below is nonsense — the client must not care.
        val body = """
            {"latest":48,"envelopes":[
              {"seq":47,"envelope":{"v":1,"pairing":"$pairing","dir":"e2p","seq":47}},
              {"seq":48,"envelope":{"v":1,"pairing":"$pairing","dir":"e2p","seq":48}}]}
        """.trimIndent()

        val result = clientOver(json(body)).pull(Direction.ENGINE_TO_PHONE, 46)
        val page = (result as RelayResult.Ok).value

        assertEquals(48L, page.latest)
        assertEquals(listOf(47L, 48L), page.envelopes.map { it.seq })
        assertTrue(page.envelopes[0].wire.contains("\"seq\":47"))
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
