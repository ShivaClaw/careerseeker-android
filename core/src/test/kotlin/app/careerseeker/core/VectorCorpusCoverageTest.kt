package app.careerseeker.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every vector in the vendored corpus is claimed by a consumer, and the corpus and its
 * manifest describe the same files.
 *
 * ## The defect this generalises
 *
 * Twice now the same shape has been found and fixed one instance at a time:
 *
 *  - `entitlement_ack` sat in [PayloadKind] from 2026-08-09, spec'd and vector-covered, with
 *    no production caller (B-19, fifty-eighth run). `PayloadKindCoverageTest` closed that at
 *    the enum: a kind nobody classifies now fails the build.
 *  - `pairing-high-bit-confirm` was vendored, listed in `index.json`, and **asserted by
 *    nothing**, because the derivation test loaded `pairing-basic` by hand while calling
 *    itself "every vector value". `ProtocolVectorsTest.validPairingVectors()` closed that by
 *    enumerating from the manifest instead of naming one file.
 *
 * Both were *"present, spec'd, shipped — and nothing consumes it"*, and both survived because
 * **nothing asked the question**. The second fix makes each existing type enumerate, but it
 * does not make a *new* type enumerate. There are exactly four `type` filters in the suite —
 * `envelope`, `entitlement`, `entitlement_ack`, `pairing` — and until this test there was no
 * assertion that those four exhaust the manifest.
 *
 * So a vector carrying a fifth `type` — which is precisely what `entitlement_ack` was in
 * August 2026 — could be generated upstream, vendored, listed, and diffed byte-for-byte by
 * CI, while **every test in `:core` skipped it and the suite stayed green**. The corpus is
 * the phone's only evidence that it agrees with the engine; a slice of it that nothing reads
 * is evidence nobody is collecting.
 *
 * ## What this test does NOT prove, stated first so nobody reads it as more
 *
 * It does not prove the consumers *assert anything useful* about the vectors they enumerate —
 * only that some enumerator claims each type. A consumer that loaded its type and asserted
 * nothing would pass here. It also says nothing about whether the pin is current: the corpus
 * being fully consumed is independent of whether it matches upstream `main`, which is
 * `VECTORS.lock`'s and CI's job (B-16 is open on exactly that, and this test does not touch it).
 *
 * ## Why the map is declared rather than derived
 *
 * Deriving the consumed set by reflecting over the test sources would make this test pass
 * automatically for any type someone filtered on, which is the tautology it exists to avoid.
 * The map below is a **statement a human has to make**: adding a type to the corpus fails this
 * test until someone writes down which test consumes it, the same way adding a [PayloadKind]
 * fails until someone places it.
 */
class VectorCorpusCoverageTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun vectorDir(): File {
        val url = requireNotNull(javaClass.classLoader.getResource("sync-vectors/v1/index.json")) {
            "shared vectors not on the test classpath — is core/src/test/resources/sync-vectors vendored?"
        }
        return File(url.toURI()).parentFile
    }

    private fun load(name: String): JsonObject =
        json.parseToJsonElement(File(vectorDir(), "$name.json").readText()).jsonObject

    private fun entries(): List<JsonObject> =
        load("index")["vectors"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
    private fun JsonObject.isValid(): Boolean = this["valid"]!!.jsonPrimitive.boolean

    /** How a type's vectors reach an assertion. */
    private enum class Consumed {
        /** Every vector of the type is enumerated from the manifest, valid and invalid alike. */
        WHOLE_TYPE,

        /**
         * The happy-path enumerator filters to `valid: true`, because running happy-path
         * assertions over a vector that pins a REJECTION is wrong. Each invalid vector of such
         * a type therefore needs a test that loads it by name, and must be listed in
         * [namedInvalidCoverage] below.
         */
        VALID_ENUMERATED_INVALID_NAMED,
    }

    /**
     * Which test consumes each vector type. Comments name the enumerator, so a reader who has
     * to change this map can go straight to the code it is describing.
     */
    private val consumers: Map<String, Consumed> = mapOf(
        // ProtocolVectorsTest.envelopeVectors() — both the valid and the invalid branch of
        // `the receiver classifies every envelope vector exactly as the engine does`.
        "envelope" to Consumed.WHOLE_TYPE,
        // EntitlementVectorsTest.entitlementVectors() — every vector, valid flag included.
        "entitlement" to Consumed.WHOLE_TYPE,
        // ProtocolVectorsTest.ackVectors().
        "entitlement_ack" to Consumed.WHOLE_TYPE,
        // ProtocolVectorsTest.validPairingVectors() — `valid: true` only, by design.
        "pairing" to Consumed.VALID_ENUMERATED_INVALID_NAMED,
    )

    /**
     * Invalid vectors covered by a test that loads them by name instead of by enumeration.
     *
     * This is the one place a named load is legitimate, and it is written down so that an
     * invalid vector added upstream cannot quietly join it.
     */
    private val namedInvalidCoverage: Map<String, Set<String>> = mapOf(
        // ProtocolVectorsTest.`pairing MITM key swap cannot decrypt the completion`.
        "pairing" to setOf("pairing-mitm-keyswap"),
    )

    @Test
    fun `every vector type in the manifest has a declared consumer`() {
        val inManifest = entries().map { it.str("type") }.toSet()
        val unclaimed = inManifest - consumers.keys

        assertTrue(
            unclaimed.isEmpty(),
            "the manifest carries vector type(s) no test consumes: ${unclaimed.sorted()}. " +
                "A vendored vector that nothing reads is not evidence. Write a consumer for it " +
                "(see ProtocolVectorsTest.ackVectors() for the shape) and record it in " +
                "VectorCorpusCoverageTest.consumers.",
        )
    }

    @Test
    fun `no vector spells an absent optional field as an explicit JSON null`() {
        // PQ-A2-6, and this test exists because a comment in the suite cites this corpus as
        // evidence for a behaviour the corpus does not actually witness. EnvelopeJsonTest's
        // `a non-string sig is malformed, and must not degrade into unsigned` justifies
        // accepting `"sig": null` with *"the vectors encode it that way"*. They do not.
        //
        // `sig` is §3's only optional envelope field, and every vector that omits a signature
        // omits the KEY -- absence is spelled by omission across the whole corpus, exactly as
        // `entitlement-ack-no-order-id` spells an absent `order_id`. So the null spelling is
        // unwitnessed on the wire, in either direction, and the two parsers in :core that had
        // to guess what it means guessed differently (EnvelopeJson: absent; the ack body
        // parser: malformed, dropping the whole payload).
        //
        // This pins the fact, not the resolution. If a future vector deliberately introduces a
        // null spelling, this test fails and whoever adds it has to answer PQ-A2-6 first --
        // which is the point, because that vector would silently change what one of the two
        // parsers is claiming to be conformant with.
        // Only the two fields that ARE wire representations are scanned. The vector schema's own
        // metadata (`expect_error`, `expected`, and an absent `plaintext_json` on a vector whose
        // ciphertext never decrypts) legitimately uses null to mean "not applicable", and that is
        // a property of the harness format, not of anything either implementation parses.
        val nulled = mutableListOf<String>()
        fun scan(where: String, element: JsonElement) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    if (value is JsonNull) nulled += "$where.$key" else scan("$where.$key", value)
                }
                is JsonArray -> element.forEachIndexed { i, value -> scan("$where[$i]", value) }
                else -> Unit
            }
        }
        for (entry in entries()) {
            val name = entry.str("name")
            val vector = load(name)
            for (field in listOf("envelope_json", "plaintext_json")) {
                scan("$name.$field", vector[field] ?: continue)
            }
        }

        assertEquals(
            emptyList(), nulled,
            "a vector spells an optional field as an explicit JSON null. The corpus has never " +
                "done this, and :core's two parsers disagree about what it means (PQ-A2-6): " +
                "EnvelopeJson reads a null sig as absent, EntitlementAckApplier reads a null " +
                "order_id as malformed and drops the entire ack. Answer PQ-A2-6 before " +
                "vendoring this vector.",
        )
    }

    @Test
    fun `every declared consumer still has vectors to consume`() {
        val inManifest = entries().map { it.str("type") }.toSet()
        val stale = consumers.keys - inManifest

        // The reverse direction, and it is not symmetry for its own sake: a type that vanishes
        // upstream leaves an enumerator quietly matching nothing, which reads as coverage on a
        // filter that can no longer fire.
        assertTrue(
            stale.isEmpty(),
            "declared consumer(s) for type(s) the manifest no longer carries: ${stale.sorted()}. " +
                "Either the re-pin dropped them upstream — in which case delete the enumerator " +
                "rather than leaving it matching nothing — or the vendoring is incomplete.",
        )
    }

    @Test
    fun `every invalid vector outside a whole-type enumerator is covered by a named test`() {
        for ((type, mode) in consumers) {
            if (mode != Consumed.VALID_ENUMERATED_INVALID_NAMED) continue

            val invalid = entries().filter { it.str("type") == type && !it.isValid() }
                .map { it.str("name") }
                .toSet()

            assertEquals(
                namedInvalidCoverage[type].orEmpty(),
                invalid,
                "the invalid `$type` vectors and the ones a named test actually loads have " +
                    "diverged. $type is enumerated `valid: true` only, so each invalid vector " +
                    "needs its own test; one added upstream would otherwise be vendored, " +
                    "listed, and asserted by nothing.",
            )
        }
    }

    @Test
    fun `the manifest and the vendored directory describe the same payload files`() {
        val onDisk = vectorDir().listFiles { f: File -> f.name.endsWith(".json") }
            .orEmpty()
            .map { it.name.removeSuffix(".json") }
            .filter { it != "index" }
            .toSet()
        val listed = entries().map { it.str("name") }.toSet()

        // CI diffs the vendored directory against the pin file-by-file, which catches a payload
        // that drifted from upstream. It does not catch the manifest and the directory
        // disagreeing with each other, because it never reads the manifest — and every
        // enumerator in this suite starts from the manifest, so a file the manifest omits is
        // invisible to all of them no matter how faithfully it was copied.
        assertEquals(
            listed,
            onDisk,
            "index.json and the vendored directory disagree. Vectors listed but absent: " +
                "${(listed - onDisk).sorted()}; present but unlisted: ${(onDisk - listed).sorted()}. " +
                "Re-vendor from the pin rather than adding either side by hand.",
        )
    }
}
