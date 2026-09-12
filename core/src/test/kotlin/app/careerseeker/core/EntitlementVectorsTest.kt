package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.SyncCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Conformance for the five Play-signed entitlement vectors added upstream in P4
 * (`679a317`), vendored here in A2.
 *
 * Two independent layers are asserted, because they fail for different reasons and a suite
 * that conflated them could pass while one was broken:
 *
 *  1. **Envelope layer** — each entitlement vector is a well-formed, device-signed `p2e`
 *     envelope. The receiver must ACCEPT all five, *including the four whose purchase is
 *     bad*. A rejected envelope would mean the phone could not even deliver a receipt it
 *     ought to forward, and would mask the real verdict behind a transport error.
 *  2. **Payload layer** — the purchase record inside must classify exactly as the vector
 *     says, with the specific reason it names. This is the phone half of the cross-language
 *     agreement with the engine's `GoogleSignedPayloadVerifier`.
 *
 * Nothing here grants Pro. See `EntitlementVerifier`'s KDoc and PQ-A2-4.
 */
class EntitlementVectorsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun vectorDir(): File = File(
        requireNotNull(javaClass.classLoader.getResource("sync-vectors/v1/index.json")) {
            "shared vectors not on the test classpath"
        }.toURI(),
    ).parentFile

    private fun load(name: String): JsonObject =
        json.parseToJsonElement(File(vectorDir(), "$name.json").readText()).jsonObject

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content

    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    /** Every vector the index marks `type: "entitlement"`, in seq order. */
    private fun entitlementVectors(): List<JsonObject> =
        load("index")["vectors"]!!.jsonArray.map { it.jsonObject }
            .filter { it.str("type") == "entitlement" }
            .map { load(it.str("name")) }
            .sortedBy { it["envelope_json"]!!.jsonObject["seq"]!!.jsonPrimitive.long }

    private fun verifierFor(v: JsonObject): EntitlementVerifier {
        val cfg = v["entitlement"]!!.jsonObject
        return EntitlementVerifier(
            playPublicKeySpkiBase64 = cfg.str("rsa_pub_spki_b64"),
            expectedPackageName = cfg.str("package_name_expected"),
            expectedProductIds = cfg["product_ids_expected"]!!.jsonArray
                .map { it.jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun `the index actually carries the five entitlement vectors`() {
        // Guards the vendoring itself: if VECTORS.lock is bumped but the files are not
        // re-copied, every assertion below would vacuously pass over an empty list.
        val names = entitlementVectors().map { it.str("name") }.toSet()
        assertEquals(
            setOf(
                "entitlement-valid",
                "entitlement-tampered-json",
                "entitlement-wrong-product",
                "entitlement-wrong-package",
                "entitlement-not-purchased",
            ),
            names,
        )
    }

    @Test
    fun `every entitlement envelope is accepted at the envelope layer`() {
        val vectors = entitlementVectors()
        val devicePub = Base64Url.decodeOrNull(vectors.first().str("device_sig_pub_b64u"))!!
        val receiver = EnvelopeReceiver(load("index").str("active_key_id"), devicePub)

        for (v in vectors) {
            val env = v["envelope_json"]!!.jsonObject
            val result = receiver.receive(
                ReceivedEnvelope(
                    env["v"]!!.jsonPrimitive.content.toInt(), env.str("pairing"), env.str("dir"),
                    env["seq"]!!.jsonPrimitive.long, env.str("ts"), env.str("key_id"),
                    env.str("nonce"), env.str("ciphertext"), env.str("sig"),
                ),
            ) { dir -> keyFor(dir) }

            assertTrue(
                result.accepted,
                "${v.str("name")}: a bad purchase must still be a GOOD envelope " +
                    "(got ${result.error?.wire}) — the verdict belongs to the payload layer",
            )
            assertEquals("entitlement", result.kind, "${v.str("name")} kind")
        }
    }

    @Test
    fun `every entitlement payload classifies with the exact reason the vector names`() {
        for (v in entitlementVectors()) {
            val name = v.str("name")
            val body = decryptBody(v)

            val verdict = verifierFor(v).verify(
                originalJson = body.str("original_json"),
                signatureBase64 = body.str("signature"),
            )

            val expected = v["entitlement"]!!.jsonObject.str("expect")
            assertEquals(expected, verdict.wire, "$name must reject as $expected, not ${verdict.wire}")

            // And the boolean the vector states independently, so a verdict enum renamed
            // out from under this test cannot quietly invert the meaning.
            assertEquals(
                v["valid"]!!.jsonPrimitive.content == "true",
                verdict == EntitlementVerdict.ACCEPTED,
                "$name valid-flag agrees with the verdict",
            )
        }
    }

    @Test
    fun `a valid receipt is rejected once the configured package or product moves`() {
        // The vectors pin one negative per reason, but only against a fixed configuration.
        // These two prove the checks read CONFIGURATION rather than constants baked in --
        // which matters because the real applicationId and product id arrive from Play
        // Console later (§4.3.2) and must be swappable without touching the verifier.
        val v = load("entitlement-valid")
        val cfg = v["entitlement"]!!.jsonObject
        val body = decryptBody(v)
        val originalJson = body.str("original_json")
        val signature = body.str("signature")

        val wrongPackage = EntitlementVerifier(
            cfg.str("rsa_pub_spki_b64"), "app.someone.else", setOf("pro_unlock"),
        )
        assertEquals(EntitlementVerdict.WRONG_PACKAGE, wrongPackage.verify(originalJson, signature))

        val wrongProduct = EntitlementVerifier(
            cfg.str("rsa_pub_spki_b64"), cfg.str("package_name_expected"), setOf("something_else"),
        )
        assertEquals(EntitlementVerdict.WRONG_PRODUCT, wrongProduct.verify(originalJson, signature))
    }

    @Test
    fun `the signature is checked over the exact bytes, so re-serialising breaks it`() {
        // §4.3.2: "The engine MUST verify over the exact bytes of this string and MUST NOT
        // re-serialise it." This pins that requirement as a test rather than a comment: a
        // semantically identical re-encoding must NOT verify.
        val v = load("entitlement-valid")
        val body = decryptBody(v)
        val signature = body.str("signature")
        val verifier = verifierFor(v)

        val original = body.str("original_json")
        assertEquals(EntitlementVerdict.ACCEPTED, verifier.verify(original, signature))

        // A pretty-printed re-encoding: same JSON document by every semantic measure, and
        // different bytes. (Re-serialising compactly can reproduce the input exactly, which
        // would make this test vacuous — so the difference is forced explicitly.)
        val reEncoded = Json { prettyPrint = true }
            .encodeToString(kotlinx.serialization.json.JsonElement.serializer(), Json.parseToJsonElement(original))
        assertTrue(reEncoded != original, "test is meaningless unless re-encoding changes the bytes")
        assertEquals(
            Json.parseToJsonElement(reEncoded), Json.parseToJsonElement(original),
            "the re-encoding must still be the SAME document — otherwise this proves nothing about bytes",
        )
        assertEquals(
            EntitlementVerdict.SIGNATURE_INVALID,
            verifier.verify(reEncoded, signature),
            "a re-serialised record must not verify — that is why original_json travels verbatim",
        )
    }

    /** Opens the vector's envelope with its own key and returns the payload `body` object. */
    private fun decryptBody(v: JsonObject): JsonObject {
        val plaintext = SyncCrypto.open(
            hex(v.str("key_hex")),
            Base64Url.decodeOrNull(v.str("nonce_b64u"))!!,
            v.str("aad"),
            Base64Url.decodeOrNull(v.str("ciphertext_b64u"))!!,
        ).toString(Charsets.UTF_8)
        return json.parseToJsonElement(plaintext).jsonObject["body"]!!.jsonObject
    }

    private fun keyFor(dir: String): ByteArray = hex(
        if (dir == "e2p") "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        else "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0",
    )
}
