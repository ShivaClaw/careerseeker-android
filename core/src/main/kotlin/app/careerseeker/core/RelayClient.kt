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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
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

    /**
     * HTTP 400 — the relay could not parse the body, or the envelope failed its header-shape
     * check (`relay/src/channel.ts:143-159`).
     *
     * Unlike every other case here this one indicts **this side**: a conforming phone does not
     * compose an envelope the relay refuses to shape-check, so it is a defect in the sender and it
     * is permanent for these bytes. Kept distinct from [TooLarge] because the remedy differs — a
     * malformed envelope is a bug to fix, an oversized one is a payload to split (§4.4). This is
     * the engine's `RelayPushResult.Rejected` decision, matched deliberately (mission §1's
     * engine-compatible interpretation rule).
     *
     * A `data object` rather than the engine's `Rejected(string Detail)`, and the difference is
     * considered rather than accidental: this interface carries a payload only where a *variable*
     * the caller must act on rides along ([Conflict.latest], [Unavailable.detail], which differs
     * per transport failure). A 400 carries no such input — the remedy is "fix whatever composed
     * these bytes" whatever the relay's wording — and the engine's own detail is a constant
     * string. PQ-PSH-1 warns that the phone's mapping needs its own derivation rather than a
     * transcription; this is that derivation, and it matches the engine where it counts: 400 is
     * terminal, and it is not [TooLarge].
     */
    data object Rejected : RelayResult<Nothing>

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

/**
 * One envelope as the relay hands it back, still sealed.
 *
 * @property wire the envelope's own JSON (§2.1: page elements are bare envelopes), untouched and
 *   unparsed. Every trust decision belongs to the receiver.
 * @property seq the `seq` **claimed** by that JSON, read leniently, `0` when it is absent or
 *   unusable. It is not authenticated — the tag covers the `seq` inside the sealed bytes (§4.1),
 *   and that is the one [SyncPump] acts on. This one exists for exactly one case: an envelope that
 *   fails the strict parse has no authenticated `seq`, and the cursor still has to clear it or one
 *   malformed byte stalls the direction forever.
 */
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
        }.flatMap { body -> parsePullPage(body) }

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

    /**
     * The page a `pull` answered with, or a failure — **never an exception**.
     *
     * The relay is the untrusted party here (§2: it is a blind pipe, and this client is written
     * on the assumption that it may be hostile as well as merely broken). It controls this body
     * completely, so every structural decision below is a decision about what an adversary can
     * make the phone do.
     *
     * **1. Total, for the same reason [conflictLatest] is.** A 200 whose body is not JSON, or is
     * JSON of the wrong shape, is a relay that did not answer usefully — which is a fact the
     * caller can act on. It reaches the caller as [RelayResult.Unavailable]. Previously this
     * function threw out of [pull] altogether: `.map` runs *outside* [request]'s try/catch, so a
     * `JsonDecodingException` escaped the `RelayResult` contract entirely and took the pump's
     * coroutine with it. An HTML error page served with a 200 — an intercepting proxy, a CDN
     * having a bad day — was enough to trigger it, no malice required.
     *
     * **2. Both keys are required, and strictly typed, because the engine requires them.**
     * `src/Sync/RelayClient.cs` reads `GetProperty("envelopes")` and
     * `GetProperty("latest").GetInt64()` — absent keys and quoted numbers both throw there. The
     * spec's §2 route table never defines this response body (PQ-S4-2), so the mission's
     * interpretation rule applies: match the engine. Defaulting a missing `latest` to `0`, as
     * this used to, is worse than a rejection — `latest` is what drives `moreAvailable` and §6.2's
     * gap check, so a relay that simply *omits* the field silently convinces the phone that it is
     * fully caught up. That is a stall the caller cannot see, produced by deleting one field.
     *
     * **3. One unusable element rejects the whole page.** Never skip-and-continue: the cursor
     * advances past every envelope *seen*, so dropping element 7 and keeping 8 advances past 7
     * permanently. That is the history-truncation attack `SyncPump` already refuses in its other
     * form (it takes the authenticated `seq`, never the relay's) wearing a different hat — a blind
     * relay that wants an envelope skipped would only have to corrupt it.
     *
     * **4. The per-element `seq` stays lenient, deliberately**, and is the one field here that
     * does not reject anything. Nothing authenticated depends on it: [SyncPump] reads the
     * envelope's own `seq` out of the sealed bytes and ignores this one, and the engine's reader
     * does not look at a per-element `seq` at all. Rejecting a page over it would be *stricter*
     * than the engine on a field no trust decision reads, which is the wrong direction under the
     * interpretation rule.
     *
     * **5. An element is a bare envelope, and the `{"seq":N,"envelope":…}` wrapper is refused**
     * (§2.1, decided 2026-08-11 — PQ-S4-2). This client used to accept both, and it was the only
     * party anywhere that did: the relay splices bare envelope JSON, the engine's `PullAsync` has
     * no branch for a wrapper, no shared vector contains a page, and until §2.1 the spec described
     * no response body at all. Accepting a shape nothing emits is not free tolerance — it made the
     * meaning of an element depend on whether it happened to contain a key named `envelope`, which
     * the relay controls, and it read a sequence number **the relay authenticates with nothing**
     * from beside one the AAD covers. Cursor arithmetic on the former is how a blind relay
     * truncates history without decrypting a byte (the other half of that attack is C-S4T-4, in
     * [SyncPump]). A wrapper now simply fails the receiver's strict §3 parse, which is where an
     * unrecognised envelope shape belongs.
     */
    private fun parsePullPage(body: String): RelayResult<PullPage> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val latest = root["latest"].strictLong("latest")
        val envelopes = root["envelopes"].requiredArray("envelopes").map { element ->
            // §2.1: an element IS a bare envelope. Not a wrapper around one — see (5) above.
            val o = element.jsonObject
            PulledEnvelope(
                // Lenient by design — see (4) above. Anything unusable reads as 0, which no
                // trust decision consumes.
                seq = (o["seq"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0L,
                // Forwarded verbatim to the receiver's strict parser, which owns every trust
                // decision. This client re-serialises rather than slicing the original text, so
                // the bytes the receiver parses are this object and nothing around it.
                wire = o.toString(),
            )
        }
        PullPage(envelopes, latest)
    }.fold(
        onSuccess = { RelayResult.Ok(it) },
        // The detail is the diagnosis; it never carries relay bytes, because this string can
        // reach a log line and the body it describes is ciphertext plus routing metadata.
        onFailure = { RelayResult.Unavailable("malformed pull page: ${it::class.simpleName}") },
    )

    /**
     * A required JSON **number**, matching `GetInt64()` engine-side: absent, quoted, fractional
     * or non-numeric all fail. Quoted is included on purpose — `"9"` is a string, the engine
     * refuses it, and a phone that accepted it would be the "more correct than the engine" field
     * bug the mission's interpretation rule exists to prevent.
     */
    private fun JsonElement?.strictLong(field: String): Long {
        val primitive = this as? JsonPrimitive ?: error("$field: expected a JSON number")
        require(!primitive.isString) { "$field: expected a JSON number, got a string" }
        return primitive.content.toLongOrNull() ?: error("$field: not an integer")
    }

    /** A required JSON array. Absent is a rejection, not an empty page — see (2) above. */
    private fun JsonElement?.requiredArray(field: String): JsonArray =
        this as? JsonArray ?: error("$field: expected a JSON array")

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

                    // 400 is "not ever" too, and it used to fall to the `else` below — so a
                    // sender-side defect was retried the full budget and then reported as
                    // Unavailable, i.e. presented to the user as being offline while the relay was
                    // answering promptly and saying exactly what was wrong (PQ-PSH-1). Version
                    // skew reaches this with no bug at all: a relay that tightens its shape check
                    // 400s every push from an older phone, which is precisely when "the network is
                    // down" is the most expensive possible misdiagnosis.
                    HttpStatusCode.BadRequest -> return RelayResult.Rejected

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
    is RelayResult.Rejected -> this
    is RelayResult.Conflict -> this
    is RelayResult.Unavailable -> this
}

/**
 * [map] for a transform that can itself fail.
 *
 * The distinction is the whole point of this slice: `map` forces the body-reading step to either
 * succeed or throw, and throwing is exactly what escapes the `RelayResult` contract. A transform
 * that returns a `RelayResult` can report "the relay answered, and I could not read it" in the
 * same vocabulary as every other relay outcome.
 */
private inline fun <T, R> RelayResult<T>.flatMap(transform: (T) -> RelayResult<R>): RelayResult<R> = when (this) {
    is RelayResult.Ok -> transform(value)
    is RelayResult.PairingUnknown -> this
    is RelayResult.Unauthorised -> this
    is RelayResult.TooLarge -> this
    is RelayResult.Rejected -> this
    is RelayResult.Conflict -> this
    is RelayResult.Unavailable -> this
}
