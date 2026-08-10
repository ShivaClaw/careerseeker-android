package app.careerseeker.core

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** What the relay answered, in the only terms the caller needs to act on. */
sealed interface RelayResult<out T> {
    data class Ok<T>(val value: T) : RelayResult<T>

    /** The pairing has no Durable Object — unpaired, or purged (§7.2 `pairing_unknown`). */
    data object PairingUnknown : RelayResult<Nothing>

    /** The bearer was rejected. Never retried: a wrong token does not become right. */
    data object Unauthorised : RelayResult<Nothing>

    /** Envelope exceeded the relay's limit (HTTP 413, §3.1). */
    data object TooLarge : RelayResult<Nothing>

    /** Transport or 5xx failure after the configured retries. */
    data class Unavailable(val detail: String) : RelayResult<Nothing>

    /**
     * A conflicting state the caller must interpret (409: pairing exists / already completed /
     * `replay_rejected`).
     *
     * @property latest the relay's high-water mark for the pushed direction, when it reported one.
     *   A `push` refused for `seq <= last` answers `{"error":"replay_rejected","latest":N}`
     *   (`relay/src/channel.ts`), and `N` is precisely the input §6.1's counter reconciliation
     *   needs — without it a sender whose persisted counter has fallen behind can only retry an
     *   envelope the relay will refuse forever. **Null for the pairing conflicts** (§5.2.1,
     *   §5.2.2), which answer `{"error":"exists"}` and carry no number: those mean "already done",
     *   not "your counter is behind", and there is nothing to reconcile against.
     */
    data class Conflict(val latest: Long? = null) : RelayResult<Nothing>
}

/** One envelope as the relay hands it back, still sealed. */
data class PulledEnvelope(val seq: Long, val wire: String)

data class PullPage(val envelopes: List<PulledEnvelope>, val latest: Long)

/**
 * Client for the blind relay (Sync-Protocol.md §2).
 *
 * Three properties this class exists to hold:
 *
 *  1. **The relay is a dumb pipe.** Nothing here interprets an envelope. Bodies go straight to
 *     [EnvelopeReceiver], which owns every trust decision. A transport that "helpfully" parsed
 *     payloads would be a second place where trust decisions live, and the second place is
 *     always the one that gets them wrong.
 *  2. **TLS only.** Cleartext is refused at construction, not at request time — §2 says
 *     clients MUST reject it, and a check that runs per-request is a check that a retry path
 *     can skip.
 *  3. **The bearer authenticates the pairing, not a person.** It is derived (§5.2.3), never
 *     minted here, and never logged — see [toString].
 *
 * The relay at `relay.careerseeker.app` is production. This client is a *client*: it reads,
 * pushes, and unpairs. It never deploys, configures, or administers anything.
 */
class RelayClient(
    private val http: HttpClient,
    baseUrl: String,
    private val pairing: String,
    /** Derived per §5.2.3 — provisional during bootstrap, final once paired. */
    private val bearer: String,
    private val retry: RetryPolicy = RetryPolicy(),
) {
    /** Backoff for transport/5xx only. 4xx answers are decisions, and decisions are not retried. */
    data class RetryPolicy(
        val attempts: Int = 4,
        val initialDelayMillis: Long = 250,
        val maxDelayMillis: Long = 8_000,
        val multiplier: Double = 3.0,
    )

    private val base: String

    init {
        require(baseUrl.startsWith("https://")) {
            // wss:// is derived from this same base for the live route, so one check covers both.
            "the relay must be reached over TLS; refusing '$baseUrl' (Sync-Protocol.md §2)"
        }
        require(isValidPairingId(pairing)) { "malformed pairing id" }
        base = baseUrl.trimEnd('/')
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** §5.2.1 — engine-only in practice; present for completeness and relay conformance tests. */
    suspend fun create(rotateToSha256Hex: String? = null): RelayResult<Unit> =
        request {
            http.post("$base/v1/$pairing/create") {
                authorised()
                if (rotateToSha256Hex != null) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"rotate_to":"$rotateToSha256Hex"}""")
                }
            }
        }.map { }

    /** §5.2.2 — the phone submits its pairing completion. */
    suspend fun submitPairing(completionJson: String): RelayResult<Unit> =
        request {
            http.post("$base/v1/$pairing/pair") {
                authorised()
                contentType(ContentType.Application.Json)
                setBody(completionJson)
            }
        }.map { }

    /** Append one sealed envelope to the recipient's queue. */
    suspend fun push(envelopeWire: String): RelayResult<Unit> =
        request {
            http.post("$base/v1/$pairing/push") {
                authorised()
                contentType(ContentType.Application.Json)
                setBody(envelopeWire)
            }
        }.map { }

    /**
     * Fetch envelopes for [dir] with `seq > since`.
     *
     * Returns them **unparsed**. `latest` is the relay's current high-water mark for the
     * direction, which §6.1 uses to reconcile a lagging local counter — and a large gap
     * between it and what came back is the signal to ask for a fresh snapshot (§6.2), never
     * to reconstruct the missing envelopes.
     */
    suspend fun pull(dir: Direction, since: Long): RelayResult<PullPage> =
        request {
            http.get("$base/v1/$pairing/pull?since=$since&dir=${dir.wire}") { authorised() }
        }.map { body -> parsePullPage(body) }

    /** Unpair: purge the Durable Object and every queued envelope. */
    suspend fun unpair(): RelayResult<Unit> =
        request { http.delete("$base/v1/$pairing") { authorised() } }.map { }

    /** Liveness. Returns no pairing information, and needs no bearer (§2). */
    suspend fun health(): RelayResult<Unit> =
        request(authorise = false) { http.get("$base/v1/health") }.map { }

    /** `wss://…/live` for the live fan-out route; the caller owns the socket lifecycle. */
    fun liveUrl(): String = "wss://" + base.removePrefix("https://") + "/v1/$pairing/live"

    // ---------------------------------------------------------------- plumbing

    private fun io.ktor.client.request.HttpRequestBuilder.authorised() {
        header("Authorization", "Bearer $bearer")
    }

    private fun parsePullPage(body: String): PullPage {
        val root = json.parseToJsonElement(body).jsonObject
        val envelopes = root["envelopes"]?.jsonArray.orEmpty().map { element ->
            val o = element.jsonObject
            PulledEnvelope(
                seq = o["seq"]?.jsonPrimitive?.longOrNull ?: 0L,
                // The relay may hand back the envelope as a nested object or as an opaque
                // string; either way it is forwarded verbatim to the receiver's strict parser.
                wire = o["envelope"]?.let {
                    if (it is kotlinx.serialization.json.JsonPrimitive && it.isString) it.content else it.toString()
                } ?: o.toString(),
            )
        }
        return PullPage(envelopes, root["latest"]?.jsonPrimitive?.longOrNull ?: 0L)
    }

    private suspend fun request(
        authorise: Boolean = true,
        call: suspend () -> HttpResponse,
    ): RelayResult<String> {
        var delayMillis = retry.initialDelayMillis
        var last = "no attempt made"

        repeat(retry.attempts) { attempt ->
            val outcome = try {
                val response = call()
                when (response.status) {
                    HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.Accepted,
                    HttpStatusCode.NoContent,
                    -> return RelayResult.Ok(response.bodyAsTextOrEmpty())

                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> return RelayResult.Unauthorised
                    HttpStatusCode.NotFound -> return RelayResult.PairingUnknown
                    HttpStatusCode.Conflict -> return RelayResult.Conflict(conflictLatest(response.bodyAsText()))
                    HttpStatusCode.PayloadTooLarge -> return RelayResult.TooLarge

                    // 5xx and 429 are the only retryable answers: they say "not now", whereas
                    // every 4xx above says "not ever", and retrying those just burns battery
                    // and adds load to a relay that already told us the answer.
                    else -> "relay answered ${response.status.value}"
                }
            } catch (e: Exception) {
                "transport failure: ${e::class.simpleName}"
            }

            last = outcome
            if (attempt < retry.attempts - 1) {
                delay(delayMillis)
                delayMillis = (delayMillis * retry.multiplier).toLong().coerceAtMost(retry.maxDelayMillis)
            }
        }
        return RelayResult.Unavailable(last)
    }

    /**
     * The `latest` a 409 body carries, or null when it carries none.
     *
     * Deliberately total: a 409 whose body is absent, empty, not JSON, or JSON without the field
     * is a conflict with no reconciliation input, which is a fact the caller can act on. Throwing
     * here would turn a recoverable "your counter is behind" into a transport exception, and the
     * one thing this client must never do is convert a relay decision into an unavailability.
     */
    private fun conflictLatest(body: String): Long? = runCatching {
        json.parseToJsonElement(body).jsonObject["latest"]?.jsonPrimitive?.longOrNull
    }.getOrNull()

    private suspend fun HttpResponse.bodyAsTextOrEmpty(): String =
        if (status == HttpStatusCode.NoContent) "" else bodyAsText()

    /** Never let a bearer reach a log line, a crash report, or a debugger's toString. */
    override fun toString(): String = "RelayClient(pairing=$pairing, bearer=<redacted>)"
}

private inline fun <T, R> RelayResult<T>.map(transform: (T) -> R): RelayResult<R> = when (this) {
    is RelayResult.Ok -> RelayResult.Ok(transform(value))
    is RelayResult.PairingUnknown -> this
    is RelayResult.Unauthorised -> this
    is RelayResult.TooLarge -> this
    is RelayResult.Conflict -> this
    is RelayResult.Unavailable -> this
}
