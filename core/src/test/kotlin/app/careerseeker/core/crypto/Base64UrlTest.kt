package app.careerseeker.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * [Base64Url]'s strictness contract, tested as a subject rather than used as a helper.
 *
 * Seven test files call `Base64Url.decodeOrNull` to build fixtures; none asserted anything
 * about it. That matters more here than for an ordinary utility, because its docstring makes
 * a **protocol** claim — that rejecting alternate spellings is what lets the shared vectors
 * "pin one encoding" so "the Kotlin and C# sides could [not] disagree about what an envelope
 * even says". Every base64url field on the wire (`nonce`, `ciphertext`, `sig`, `engine_pub`,
 * `secret`) is read through this one function.
 *
 * Two of the three spelling axes are closed, and this file pins them: **padding** (`=`) and
 * the **standard alphabet** (`+`, `/`) are refused explicitly, ahead of the JDK decoder.
 *
 * The third is open, and `non-canonical trailing bits are ACCEPTED` records it as measured
 * fact rather than as a defect, because the reading below says it is fail-safe here:
 *
 *  - `java.util.Base64.getUrlDecoder()` ignores the unused bits of the final character, so
 *    `"QQ"`, `"QR"`, `"QV"` and `"QZ"` all decode to the single byte `0x41`. Measured
 *    directly against the JDK, not inferred.
 *  - Re-spelling a field therefore yields a **different wire envelope that decodes to
 *    identical bytes**. `EnvelopeHeader.aad()` binds `v|pairing|dir|seq|ts|key_id` and
 *    **not** the nonce or ciphertext spelling (`Protocol.kt:143`), so AES-GCM still opens it.
 *  - It is not a replay bypass: `seq` is inside the AAD and the receiver's replay check is on
 *    `seq`, so a re-spelled copy is refused as a replay like any other duplicate.
 *  - **It is field-specific, and the first draft of this file got that wrong.** Spare bits
 *    exist only when the field's byte length is not a multiple of 3, so the 12-byte `nonce`
 *    has exactly **one** spelling and cannot be re-spelled at all; a 32-byte key has 4 and a
 *    64-byte signature has 16. The guard assertion in the draft's own test is what caught it.
 *  - Nor is it a signature bypass, but not for the reason first assumed: `signatureInput`
 *    binds the `nonce` **string** (immune, above) and the ciphertext by the **hash of its
 *    decoded bytes** (`PairingDerivation.kt:62-65`). So a re-spelled ciphertext signs and
 *    opens identically — the envelope's wire form is not unique, while remaining valid.
 *    Pinned below, with the one consequence that survives: an envelope must not be
 *    de-duplicated or authenticated by hashing its wire bytes.
 *
 * **What is genuinely open is cross-implementation, and cannot be settled from this sandbox.**
 * If .NET's decoder *refuses* non-canonical trailing bits where the JDK's accepts them, engine
 * and phone disagree about whether an envelope is well-formed — one opens it, the other
 * answers `decrypt_failed`. Both encoders only ever emit canonical output, so nothing produces
 * such an envelope today and no vector covers it; a hostile or buggy third writer is the only
 * way to reach it. Recorded as **PQ-B64-1** with the exact .NET command, for a session that
 * has one. This file deliberately does **not** tighten the Kotlin: a phone stricter than the
 * engine is the "more correct than the engine" field bug the mission's interpretation rule
 * names, and the direction of the divergence is still unmeasured.
 */
class Base64UrlTest {

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    // ---- Round trip -----------------------------------------------------------------------

    @Test
    fun `round trips the wire's real field sizes`() {
        for (n in intArrayOf(1, 12, 16, 32, 33, 64, 65)) {
            val bytes = ByteArray(n) { (it * 31 + 7).toByte() }
            val encoded = Base64Url.encode(bytes)
            assertContentEquals(bytes, Base64Url.decodeOrNull(encoded), "round trip failed at $n")
        }
    }

    @Test
    fun `encode never pads`() {
        // 1, 2 and 3 mod 3 are the three padding cases; none may produce '='.
        for (n in 1..9) {
            val encoded = Base64Url.encode(ByteArray(n) { it.toByte() })
            assertEquals(-1, encoded.indexOf('='), "padding leaked at length $n: $encoded")
        }
    }

    @Test
    fun `encode uses the url alphabet`() {
        // 0xfb 0xff encodes to "-_8" in base64url and "+/8" in the standard alphabet.
        val encoded = Base64Url.encode(byteArrayOf(0xfb.toByte(), 0xff.toByte()))
        assertEquals("-_8", encoded)
    }

    // ---- The two closed spelling axes -----------------------------------------------------

    @Test
    fun `padded input is refused`() {
        // "QQ==" is the padded spelling of the same byte "QQ" carries.
        assertNull(Base64Url.decodeOrNull("QQ=="))
        assertContentEquals(byteArrayOf(0x41), Base64Url.decodeOrNull("QQ"))
    }

    /**
     * **Measured note: only half of the explicit guard is load-bearing.** Deleting
     * `s.contains('=')` from `decodeOrNull` fails `padded input is refused`, because the JDK's
     * URL decoder *accepts* padding. Deleting the `'+'`/`'/'` half changes nothing any test
     * can see — those characters are outside the URL alphabet, so the decoder throws and the
     * `catch` returns null anyway. The guard is defence in depth there, not the mechanism.
     * Recorded rather than removed: it states the intent at the top of the function, and the
     * cost is two `contains` calls.
     */
    @Test
    fun `standard alphabet is refused`() {
        assertNull(Base64Url.decodeOrNull("+/8"))
        assertContentEquals(
            byteArrayOf(0xfb.toByte(), 0xff.toByte()),
            Base64Url.decodeOrNull("-_8"),
        )
    }

    // ---- The open one, recorded as measured ------------------------------------------------

    /**
     * **Measured, not desired.** Four distinct strings decode to the same byte, because the
     * JDK drops the final character's unused bits rather than requiring them to be zero.
     * This is the assertion that will fail the day someone tightens either side, which is
     * exactly when PQ-B64-1 needs re-reading.
     */
    @Test
    fun `non-canonical trailing bits are ACCEPTED`() {
        for (spelling in listOf("QQ", "QR", "QV", "QZ")) {
            val decoded = Base64Url.decodeOrNull(spelling)
                ?: error("$spelling was refused; PQ-B64-1 has moved")
            assertEquals("41", decoded.hex(), "$spelling decoded to something else")
        }
    }

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Every last-character substitution that still decodes to exactly [bytes]. */
    private fun spellingsOf(bytes: ByteArray): List<String> {
        val canonical = Base64Url.encode(bytes)
        return alphabet.mapNotNull { c ->
            val alt = canonical.dropLast(1) + c
            if (Base64Url.decodeOrNull(alt)?.contentEquals(bytes) == true) alt else null
        }
    }

    /**
     * **How many spare bits a field has is decided by its length mod 3**, and this is the
     * assertion the first draft of this file got wrong — it tried to build a second spelling
     * of a 12-byte nonce, and there is none.
     *
     *  - `len % 3 == 0` → the final unit is 4 characters carrying 24 bits exactly. **No spare
     *    bits, one spelling.** The 12-byte nonce lands here.
     *  - `len % 3 == 2` → 3 characters carry 16 bits, 2 spare → **4 spellings**. A 32-byte key
     *    and a 65-byte uncompressed P-256 point land here.
     *  - `len % 3 == 1` → 2 characters carry 8 bits, 4 spare → **16 spellings**. A 64-byte raw
     *    P-256 signature lands here.
     *
     * So the conflation is real but **field-specific**, which is a materially narrower claim
     * than "base64url fields are malleable" — and the narrower claim is the true one.
     */
    @Test
    fun `spare bits and therefore spellings are decided by length mod three`() {
        assertEquals(1, spellingsOf(ByteArray(12) { 0x07 }).size, "nonce (12) must be unique")
        assertEquals(1, spellingsOf(ByteArray(48) { 0x07 }).size)
        assertEquals(4, spellingsOf(ByteArray(32) { 0x07 }).size, "32-byte key")
        assertEquals(4, spellingsOf(ByteArray(65) { 0x07 }).size, "uncompressed P-256 point")
        assertEquals(16, spellingsOf(ByteArray(64) { 0x07 }).size, "raw P-256 signature")
    }

    /**
     * The nonce is immune, and that is worth its own name because the envelope's signature
     * binds the nonce as a **string** (`PairingDerivation.signatureInput`). Had the nonce been
     * re-spellable, one envelope would have had several signature inputs; because 12 is a
     * multiple of 3, it cannot.
     */
    @Test
    fun `the nonce cannot be re-spelled`() {
        val nonce = ByteArray(app.careerseeker.core.Protocol.NONCE_BYTES) { 0x07 }
        assertEquals(16, Base64Url.encode(nonce).length)
        assertContentEquals(listOf(Base64Url.encode(nonce)), spellingsOf(nonce))
    }

    /**
     * **The envelope's wire form is not uniquely determined, and it stays valid re-spelled.**
     * The signature binds the ciphertext by the **hash of its decoded bytes**, not by its
     * spelling (`PairingDerivation.kt:62-65`), and GCM opens the decoded bytes too — so an
     * intermediary that re-spells a ciphertext whose length is not a multiple of 3 produces a
     * *different* wire envelope that decrypts identically **and still verifies**.
     *
     * Stated plainly because it sounds worse than it is: replay is keyed on `seq`, which is
     * inside the AAD, so a re-spelled copy is refused as a duplicate exactly like a byte-wise
     * one. What it does rule out is authenticating or de-duplicating an envelope by hashing
     * its wire bytes — that would see two distinct envelopes where the protocol sees one.
     */
    @Test
    fun `a re-spelled ciphertext leaves the signature input unchanged`() {
        val ct = ByteArray(32) { 0x11 } // 32 % 3 == 2, so alternates exist
        val spellings = spellingsOf(ct)
        assertEquals(4, spellings.size)
        val canonical = Base64Url.encode(ct)
        val respelled = spellings.first { it != canonical }

        assertNotEquals(canonical, respelled)
        assertContentEquals(ct, Base64Url.decodeOrNull(respelled))

        val aad = "v=1|pairing=p_7Fq2mXk9LtVbN3wR|dir=p2e|seq=1|ts=2026-06-11T14:02:11Z|key_id=k"
        val nonceB64u = Base64Url.encode(ByteArray(12) { 0x07 })
        assertEquals(
            app.careerseeker.core.PairingDerivation.signatureInput(
                aad, nonceB64u, Base64Url.decodeOrNull(canonical)!!,
            ),
            app.careerseeker.core.PairingDerivation.signatureInput(
                aad, nonceB64u, Base64Url.decodeOrNull(respelled)!!,
            ),
            "the signature binds decoded ciphertext bytes, so both spellings sign the same",
        )
    }

    // ---- Refusals ---------------------------------------------------------------------------

    @Test
    fun `null and empty decode to null`() {
        assertNull(Base64Url.decodeOrNull(null))
        assertNull(Base64Url.decodeOrNull(""))
    }

    /**
     * The empty array is the one input where encode and decode are **not** inverse:
     * `encode(ByteArray(0))` is `""`, and `""` decodes to `null`, not to an empty array.
     * Unreachable on the wire — a GCM ciphertext carries a 16-byte tag, a nonce is 12 bytes,
     * a key 32 — but pinned so it is a known asymmetry rather than a surprise.
     */
    @Test
    fun `the empty array does not round trip`() {
        assertEquals("", Base64Url.encode(ByteArray(0)))
        assertNull(Base64Url.decodeOrNull(Base64Url.encode(ByteArray(0))))
    }

    @Test
    fun `whitespace is refused rather than skipped`() {
        // MIME decoders skip these; a URL decoder must not. Newline is the one that would
        // otherwise let a pretty-printed field through.
        assertNull(Base64Url.decodeOrNull("QQ QQ"))
        assertNull(Base64Url.decodeOrNull("QUJD\n"))
        assertNull(Base64Url.decodeOrNull("\tQUJD"))
    }

    @Test
    fun `truncated final unit is refused`() {
        // Length % 4 == 1 can never be a valid encoding: one character is 6 bits.
        assertNull(Base64Url.decodeOrNull("QQQQQ"))
    }

    @Test
    fun `characters outside the alphabet are refused`() {
        for (bad in listOf("QQ.", "QQ*", "QQ%2B", "QQé", "QQ ")) {
            assertNull(Base64Url.decodeOrNull(bad), "$bad was accepted")
        }
    }

    /** Refusal is a null return, never a throw — every caller uses the elvis operator. */
    @Test
    fun `refusal never throws`() {
        for (bad in listOf("=", "+", "/", "!!!!", "QQQQQ", " ")) {
            assertNull(Base64Url.decodeOrNull(bad))
        }
    }
}
