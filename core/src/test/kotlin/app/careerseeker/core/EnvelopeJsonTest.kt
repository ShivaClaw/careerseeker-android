package app.careerseeker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers Sync-Protocol.md §3's unknown-top-level-field rule, which is a **MUST** with no
 * shared vector behind it (PQ-A2-3) — so an implementation can pass 100% of the published
 * suite while ignoring it entirely. This repo did, until A2.
 */
class EnvelopeJsonTest {

    private val valid = """
        {"v":1,"pairing":"p_7Fq2mXk9LtVbN3wR","dir":"e2p","seq":1,
         "ts":"2026-06-11T14:02:11Z","key_id":"k-2026-06-01",
         "nonce":"3q2-796tvu_erb7v","ciphertext":"AAAA"}
    """.trimIndent()

    @Test
    fun `a well-formed envelope parses into its fields`() {
        val r = EnvelopeJson.parse(valid)
        assertTrue(r.ok, "expected a parse, got ${r.error?.wire}")
        val e = r.envelope!!
        assertEquals(1, e.v)
        assertEquals("p_7Fq2mXk9LtVbN3wR", e.pairing)
        assertEquals("e2p", e.dir)
        assertEquals(1L, e.seq)
        assertEquals("k-2026-06-01", e.keyId)
        assertNull(e.sig, "an absent sig is absent, not empty-string")
    }

    @Test
    fun `an unknown top-level field is rejected, not ignored`() {
        // The rule exists because a permissive parser is how a future version's field
        // silently becomes an injection point (§3).
        val withExtra = valid.dropLast(1) + ""","admin":true}"""
        val r = EnvelopeJson.parse(withExtra)
        assertTrue(!r.ok, "an envelope carrying an unknown field must be rejected")
        assertEquals(ErrorCode.DECRYPT_FAILED, r.error)
    }

    @Test
    fun `every field the spec defines is accepted, so the strictness is not overreach`() {
        // The mirror of the test above: a parser that rejects unknown fields is only correct
        // if it knows ALL the known ones. Missing one here would reject legitimate traffic.
        assertEquals(
            setOf("v", "pairing", "dir", "seq", "ts", "key_id", "nonce", "ciphertext", "sig"),
            EnvelopeJson.KNOWN_FIELDS,
        )
        val signed = valid.dropLast(1) + ""","sig":"QUJD"}"""
        val r = EnvelopeJson.parse(signed)
        assertTrue(r.ok, "sig is a known optional field")
        assertEquals("QUJD", r.envelope!!.sig)
    }

    @Test
    fun `a missing required field is rejected`() {
        for (field in EnvelopeJson.KNOWN_FIELDS - "sig") {
            val without = EnvelopeJson.parse(removeField(valid, field))
            assertTrue(!without.ok, "an envelope missing '$field' must be rejected")
        }
    }

    @Test
    fun `fields of the wrong JSON type are rejected rather than coerced`() {
        // "seq":"1" is the interesting one: a lenient parser coerces the string to a number
        // and the envelope sails through with an attacker-chosen sequence.
        assertTrue(!EnvelopeJson.parse(valid.replace("\"seq\":1", "\"seq\":\"1\"")).ok)
        assertTrue(!EnvelopeJson.parse(valid.replace("\"v\":1", "\"v\":\"1\"")).ok)
        assertTrue(!EnvelopeJson.parse(valid.replace("\"dir\":\"e2p\"", "\"dir\":2")).ok)
        assertTrue(!EnvelopeJson.parse(valid.replace("\"ciphertext\":\"AAAA\"", "\"ciphertext\":null")).ok)
    }

    @Test
    fun `a non-string sig is malformed, and must not degrade into unsigned`() {
        // Silently treating a broken sig as "no sig" would change which check fires: a
        // state-changing p2e envelope would report a MISSING signature instead of a bad one.
        assertTrue(!EnvelopeJson.parse(valid.dropLast(1) + ""","sig":42}""").ok)
        // Explicit JSON null, however, genuinely means absent — the vectors encode it that way.
        val nulled = EnvelopeJson.parse(valid.dropLast(1) + ""","sig":null}""")
        assertTrue(nulled.ok)
        assertNull(nulled.envelope!!.sig)
    }

    @Test
    fun `malformed JSON and non-objects are rejected without throwing`() {
        for (junk in listOf("", "{", "[]", "\"string\"", "null", "{\"v\":1,}")) {
            val r = EnvelopeJson.parse(junk)
            assertTrue(!r.ok, "junk input <$junk> must be rejected")
            assertEquals(ErrorCode.DECRYPT_FAILED, r.error)
        }
    }

    @Test
    fun `a structurally wrong pairing id is rejected before it can reach the AAD`() {
        assertTrue(!EnvelopeJson.parse(valid.replace("p_7Fq2mXk9LtVbN3wR", "not-a-pairing")).ok)
    }

    /** Crude but exact: drops `"field": <value>` from the flat test envelope. */
    private fun removeField(source: String, field: String): String {
        val compact = source.replace(Regex("\\s"), "")
        val start = compact.indexOf("\"$field\":")
        val afterValue = compact.indexOf(',', start).let { if (it < 0) compact.length - 1 else it + 1 }
        return (compact.substring(0, start) + compact.substring(afterValue))
            .replace(",}", "}")
    }
}
