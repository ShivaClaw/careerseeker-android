package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phone's pairing attempt ([PairingFlow]) and the relay-token handover
 * ([RelayTokenLadder]), driven against a MockEngine relay and the shared `pairing-basic` vector.
 *
 * These exist for the same reason [SyncPumpTest] does: the rules in [PairingFlow]'s KDoc are
 * ordering rules, and each one's wrong version compiles, renders a plausible six-digit code, and
 * reports nothing. A rebuilt completion, a 409 read as failure or as success, a phone that rotates
 * the relay token, a ladder that falls back after promotion — none of those throw.
 *
 * The completion built here is the vector's, so "the confirm code is right" means the desktop
 * would show the same six digits, not that a stub agreed with itself.
 *
 * What these do **not** prove: anything about CameraX, the Android Keystore, or a screen. The
 * device signing key is supplied as a public point by the test, exactly as `:app` will supply it
 * from a Keystore key — and whether that key is really hardware-backed is a claim only an emulator
 * or a device can settle (B-4). Nothing here asserts it.
 */
class PairingFlowTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): JsonObject = json.parseToJsonElement(
        File(
            File(requireNotNull(javaClass.classLoader.getResource("sync-vectors/v1/index.json")).toURI()).parentFile,
            "$name.json",
        ).readText(),
    ).jsonObject

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private val vec by lazy { load("pairing-basic") }
    private val pairingId by lazy { load("index").str("pairing_id") }
    private val relayBase = "https://relay.careerseeker.app"

    private fun inviteJson(
        v: Int = 1,
        suite: String = Protocol.SUITE,
        relay: String = relayBase,
    ): String = """{"v":$v,"suite":"$suite","pairing":"$pairingId",""" +
        """"engine_pub":"${vec["engine"]!!.jsonObject.str("pub_b64u")}",""" +
        """"relay":"$relay","secret":"${vec.str("secret_b64u")}"}"""

    private fun phoneKeys() = EphemeralKeyPair(
        privateScalar = hex(vec["phone"]!!.jsonObject.str("d_hex")),
        publicUncompressed = Base64Url.decodeOrNull(vec["phone"]!!.jsonObject.str("pub_b64u"))!!,
    )

    private fun deviceSigPub() = Base64Url.decodeOrNull(vec["device_sig"]!!.jsonObject.str("pub_b64u"))!!

    /** One recorded relay call, in the terms the rules above are stated in. */
    private data class Call(val method: String, val path: String, val bearer: String, val body: String)

    /**
     * A relay that answers [statuses] in order (repeating the last) and records every call.
     * `respond` bodies are irrelevant here: [RelayClient.submitPairing] discards them.
     */
    private class FakeRelay(private vararg val statuses: HttpStatusCode) {
        val calls = mutableListOf<Call>()

        fun engine() = MockEngine { request ->
            calls += Call(
                method = request.method.value,
                path = request.url.encodedPath,
                bearer = (request.headers[HttpHeaders.Authorization] ?: "").removePrefix("Bearer "),
                body = runCatching {
                    (request.body as io.ktor.http.content.TextContent).text
                }.getOrDefault(""),
            )
            val status = statuses.getOrElse(calls.size - 1) { statuses.last() }
            respond(ByteReadChannel("""{"ok":true}"""), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private fun flowOver(
        relay: FakeRelay,
        ephemeral: () -> EphemeralKeyPair = ::phoneKeys,
    ): PairingFlow {
        val http = HttpClient(relay.engine())
        return PairingFlow(
            relayFor = { base, pairing, bearer -> RelayClient(http, base, pairing, bearer) },
            ephemeral = ephemeral,
            deviceSigPublic = ::deviceSigPub,
            nonces = { ByteArray(Protocol.NONCE_BYTES) { i -> i.toByte() } },
            clock = { "2026-06-11T14:02:11Z" },
        )
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    fun `a scanned invite is submitted once and waits for the human`() = runTest {
        val relay = FakeRelay(HttpStatusCode.Created)
        val step = flowOver(relay).begin(inviteJson())

        assertTrue(step is PairingStep.AwaitingConfirmation, "got $step")
        assertFalse(step.raced, "a 201 is not a race")
        assertEquals(1, relay.calls.size, "exactly one relay call")
        assertEquals("POST", relay.calls[0].method)
        assertEquals("/v1/$pairingId/pair", relay.calls[0].path)

        // The recorder reads the request body through a cast; assert it really captured something,
        // or the "identical bytes" assertion below could pass on a set of one empty string.
        val body = json.parseToJsonElement(relay.calls[0].body).jsonObject
        assertEquals(Protocol.SUITE, body["suite"]!!.jsonPrimitive.content)
        assertTrue(body["phone_pub"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(body["ciphertext"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun `the six digits shown are the ones the desktop derives`() = runTest {
        val step = flowOver(FakeRelay(HttpStatusCode.Created)).begin(inviteJson())
        assertEquals(
            vec["expected"]!!.jsonObject.str("confirm"),
            (step as PairingStep.AwaitingConfirmation).confirmCode,
            "the human compares this against the engine's screen; a code only this code agrees with is useless",
        )
    }

    @Test
    fun `the completion is submitted with the provisional bearer, not the final one`() = runTest {
        val relay = FakeRelay(HttpStatusCode.Created)
        flowOver(relay).begin(inviteJson())

        val expected = vec["expected"]!!.jsonObject
        assertEquals(expected.str("provisional_token_b64u"), relay.calls[0].bearer, "§5.2.1")
        assertTrue(
            relay.calls[0].bearer != expected.str("relay_token_b64u"),
            "the final token is not active at the relay until the engine rotates (§5.2.3)",
        )
    }

    @Test
    fun `confirming yields the pairing the engine will agree with`() = runTest {
        val flow = flowOver(FakeRelay(HttpStatusCode.Created))
        flow.begin(inviteJson())
        val step = flow.confirm(codesMatch = true)

        assertTrue(step is PairingStep.Paired, "got $step")
        val paired = step.pairing
        val expected = vec["expected"]!!.jsonObject
        assertEquals(pairingId, paired.pairing)
        assertEquals(Protocol.SUITE, paired.suite)
        assertEquals(relayBase, paired.relayBaseUrl)
        assertEquals(expected.str("k_p2e_hex").lowercase(), paired.keys.keyPhoneToEngine.toHex())
        assertEquals(expected.str("relay_token_b64u"), paired.keys.relayToken)
        assertTrue(paired.deviceSigPublicUncompressed.contentEquals(deviceSigPub()))
    }

    // ---------------------------------------------------------------- rule 4: one call, never create

    @Test
    fun `the phone never rotates the relay token`() = runTest {
        val relay = FakeRelay(HttpStatusCode.Created)
        val flow = flowOver(relay)
        flow.begin(inviteJson())
        flow.confirm(codesMatch = true)

        // §5.2.3 gives rotation to the engine. A phone that POSTs /create with a rotate_to while
        // the engine still holds the provisional bearer locks it out of GET /pair — the completion
        // is stored, one-shot and unreadable, and the secret is already spent.
        assertTrue(relay.calls.none { it.path.endsWith("/create") }, "flow called /create: ${relay.calls}")
        assertEquals(1, relay.calls.size, "a pairing attempt is one relay call, and this is it")
    }

    // ---------------------------------------------------------------- rule 1: build once, resend verbatim

    @Test
    fun `a retry re-sends the same bytes and does not rebuild the completion`() = runTest {
        // 503 four times exhausts RelayClient's retries and surfaces as Unavailable; then 201.
        val relay = FakeRelay(
            HttpStatusCode.ServiceUnavailable, HttpStatusCode.ServiceUnavailable,
            HttpStatusCode.ServiceUnavailable, HttpStatusCode.ServiceUnavailable,
            HttpStatusCode.Created,
        )
        val flow = flowOver(relay)

        val first = flow.begin(inviteJson())
        assertEquals(PairingStep.Aborted(PairingAbort.RELAY_UNAVAILABLE), first)

        val second = flow.retry()
        assertTrue(second is PairingStep.AwaitingConfirmation, "got $second")

        assertEquals(1, flow.completionBuilds, "the completion is derived once per invite")
        val bodies = relay.calls.map { it.body }.toSet()
        assertEquals(1, bodies.size, "every attempt sent identical bytes; got ${bodies.size} distinct bodies")
        assertTrue(bodies.single().contains("\"ciphertext\""), "the recorder captured nothing, so the line above proves nothing")
    }

    @Test
    fun `a retry keeps the ephemeral key it already published`() = runTest {
        // The failure this pins: regenerate the keypair on retry and the engine, collecting the
        // FIRST stored body, derives against a phone_pub this device has thrown away. Both sides
        // then show a confirm code the other cannot match, and nothing anywhere says why.
        var handedOut = 0
        val relay = FakeRelay(
            HttpStatusCode.ServiceUnavailable, HttpStatusCode.ServiceUnavailable,
            HttpStatusCode.ServiceUnavailable, HttpStatusCode.ServiceUnavailable,
            HttpStatusCode.Created,
        )
        val flow = flowOver(relay, ephemeral = { handedOut++; phoneKeys() })

        flow.begin(inviteJson())
        flow.retry()

        assertEquals(1, handedOut, "the ephemeral keypair was regenerated for a retry")
    }

    // ---------------------------------------------------------------- rule 2: 409 is a question

    @Test
    fun `a 409 is neither success nor failure -- it goes to the human, flagged`() = runTest {
        val step = flowOver(FakeRelay(HttpStatusCode.Conflict)).begin(inviteJson())

        assertTrue(step is PairingStep.AwaitingConfirmation, "a 409 must not abort: got $step")
        assertTrue(step.raced, "a 409 must be surfaced as a race, not swallowed")
    }

    @Test
    fun `a raced attempt still confirms to the keys this phone derived`() = runTest {
        // If the stored completion was ours (the transport retried and lost a response), the code
        // matches and this pairing is correct. If it was a stranger's, the code differs and the
        // human aborts. Either way the phone's own derivation is what it confirms with.
        val flow = flowOver(FakeRelay(HttpStatusCode.Conflict))
        val awaiting = flow.begin(inviteJson()) as PairingStep.AwaitingConfirmation
        val paired = (flow.confirm(codesMatch = true) as PairingStep.Paired).pairing

        assertEquals(vec["expected"]!!.jsonObject.str("confirm"), awaiting.confirmCode)
        assertEquals(
            vec["expected"]!!.jsonObject.str("k_p2e_hex").lowercase(),
            paired.keys.keyPhoneToEngine.toHex(),
        )
    }

    // ---------------------------------------------------------------- rule 3: the human gate

    @Test
    fun `a code mismatch is terminal and is not a cancel`() = runTest {
        val flow = flowOver(FakeRelay(HttpStatusCode.Created))
        flow.begin(inviteJson())

        assertEquals(PairingStep.Aborted(PairingAbort.CODE_MISMATCH), flow.confirm(codesMatch = false))
        // The engine burned the one-time secret on the completion it accepted (§5.2.2); another
        // attempt against a dead secret is not a recovery, it is a second failure.
        assertFailsWith<IllegalStateException> { flow.confirm(codesMatch = true) }
    }

    @Test
    fun `a cancel is reported as a cancel, because the two mean opposite things`() = runTest {
        val flow = flowOver(FakeRelay(HttpStatusCode.Created))
        flow.begin(inviteJson())
        assertEquals(PairingStep.Aborted(PairingAbort.CANCELLED), flow.cancel())
    }

    @Test
    fun `confirming twice is refused`() = runTest {
        val flow = flowOver(FakeRelay(HttpStatusCode.Created))
        flow.begin(inviteJson())
        flow.confirm(codesMatch = true)
        assertFailsWith<IllegalStateException> { flow.confirm(codesMatch = true) }
    }

    @Test
    fun `begin is once per attempt`() = runTest {
        val flow = flowOver(FakeRelay(HttpStatusCode.Created))
        flow.begin(inviteJson())
        assertFailsWith<IllegalStateException> { flow.begin(inviteJson()) }
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun `a refused QR aborts before anything is derived or sent`() = runTest {
        val relay = FakeRelay(HttpStatusCode.Created)
        val flow = flowOver(relay)
        val step = flow.begin(inviteJson(suite = "rot13"))

        assertEquals(PairingStep.Aborted(PairingAbort.INVITE_REJECTED, PairingError.SUITE_UNSUPPORTED), step)
        assertEquals(0, relay.calls.size, "a refused invite must not reach the relay")
        assertEquals(0, flow.completionBuilds)
        // Nothing was spent, so the next scan is a clean attempt rather than a poisoned one.
        assertTrue(flowOver(relay).begin(inviteJson()) is PairingStep.AwaitingConfirmation)
    }

    @Test
    fun `a cleartext relay in the QR is refused by the parser, not by the client`() = runTest {
        val relay = FakeRelay(HttpStatusCode.Created)
        val step = flowOver(relay).begin(inviteJson(relay = "http://relay.careerseeker.app"))

        assertEquals(PairingStep.Aborted(PairingAbort.INVITE_REJECTED, PairingError.INSECURE_RELAY), step)
        assertEquals(0, relay.calls.size)
    }

    @Test
    fun `relay answers that foreclose the attempt are terminal, not retryable`() = runTest {
        // BadRequest joins the list with PQ-PSH-1: a completion the relay shape-checked and
        // refused is this build's defect, so retrying it cannot succeed.
        for (status in listOf(
            HttpStatusCode.Unauthorized,
            HttpStatusCode.NotFound,
            HttpStatusCode.PayloadTooLarge,
            HttpStatusCode.BadRequest,
        )) {
            val flow = flowOver(FakeRelay(status))
            assertEquals(
                PairingStep.Aborted(PairingAbort.RELAY_REFUSED),
                flow.begin(inviteJson()),
                "status $status",
            )
            assertFailsWith<IllegalStateException>("status $status") { flow.retry() }
        }
    }

    // ---------------------------------------------------------------- secret hygiene

    @Test
    fun `the ephemeral private scalar is zeroised once the completion is built`() = runTest {
        val keyPair = phoneKeys()
        val flow = flowOver(FakeRelay(HttpStatusCode.Created), ephemeral = { keyPair })
        flow.begin(inviteJson())

        assertTrue(keyPair.privateScalar.all { it.toInt() == 0 }, "the scalar outlived its use")
        // And it was zeroised AFTER the derivation, not before: the keys are still the vector's.
        val paired = (flow.confirm(codesMatch = true) as PairingStep.Paired).pairing
        assertEquals(
            vec["expected"]!!.jsonObject.str("k_p2e_hex").lowercase(),
            paired.keys.keyPhoneToEngine.toHex(),
        )
    }

    // ---------------------------------------------------------------- the token ladder (§5.2.3)

    @Test
    fun `the ladder opens on the provisional token`() {
        val ladder = RelayTokenLadder("prov", "final")
        assertEquals("prov", ladder.bearer())
        assertFalse(ladder.promoted)
    }

    @Test
    fun `a 401 on the provisional token says try the final one`() {
        val ladder = RelayTokenLadder("prov", "final")
        assertEquals("final", ladder.unauthorised("prov"))
    }

    @Test
    fun `accepting the final token promotes, and promotion is one-way`() {
        val ladder = RelayTokenLadder("prov", "final")
        ladder.accepted("final")

        assertTrue(ladder.promoted)
        assertEquals("final", ladder.bearer())
        // Rule 3: after rotation there is no state in which the provisional token is right again,
        // so a 401 here is a revoked pairing the user must see -- not a credential to shuffle.
        assertNull(ladder.unauthorised("final"), "a promoted ladder must not fall back")
    }

    @Test
    fun `accepting the provisional token does not promote`() {
        val ladder = RelayTokenLadder("prov", "final")
        ladder.accepted("prov")
        assertFalse(ladder.promoted, "only the final token proves the engine rotated")
        assertEquals("prov", ladder.bearer())
    }
}
