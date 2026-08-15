package app.careerseeker.core

import app.careerseeker.core.crypto.Base64Url
import app.careerseeker.core.crypto.Hkdf
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [PairingDerivation] — the second of the two gaps the twentieth iteration named, and the one
 * that was left over.
 *
 * That record predicted two `:core` files with no test file of their own — `SyncCrypto` and
 * this one — and named exactly the three surfaces below: `signatureInput`, `completionAad`,
 * and *"the confirm-code reduction (`% 1_000_000`, which has a modulo-bias question nobody has
 * asked)"*. `SyncCryptoTest.kt` has since been written. Measured this run, `core/src/test`
 * held eighteen files against `core/src/main`'s eighteen, and none was `PairingDerivationTest`.
 *
 * **"Not tested" is the wrong reading, and the right one is narrower.** `ProtocolVectorsTest`
 * and `PairingSessionTest` between them call `derive`, `provisionalRelayToken`,
 * `completionAad` and `signatureInput` and prove every output byte-for-byte against the shared
 * vectors in `docs/sync-vectors/v1/`. What those tests cannot reach is everything the vectors
 * do not happen to contain — and measured, that is three things:
 *
 * 1. **The confirm code's reduction.** Every caller in the tree passes `listOf(oneSecret)`,
 *    and each vector fixes one input, so `% 1_000_000` has only ever been observed at the
 *    handful of points the vectors pin. Whether the assembly is *unsigned* is invisible at a
 *    point where the top byte happens to be below 0x80, and the modulo's bias had, in the
 *    record's own words, never been asked about.
 * 2. **`concat` with more than one shared secret.** `PairingDerivation.kt:18-20` says the
 *    concatenation exists for the post-quantum hybrid suite and must not be collapsed. Every
 *    production and test call site passes a **one**-element list, so the loop in `concat` has
 *    never run twice and the ordering it implies is asserted nowhere.
 * 3. **The two `|`-joined strings' field structure**, which is shared byte-for-byte with the
 *    engine (`src/Sync/PairingCrypto.cs:85-86` and `src/Sync/DeviceSignature.cs:18`) and is
 *    therefore a cross-implementation contract, not a local formatting choice.
 *
 * Every expectation below is either an exact integer identity or an independent recomputation.
 * Nothing here is transcribed from the production expression it checks.
 */
class PairingDerivationTest {

    /** Deterministic bytes; no RNG, so a failure here reproduces exactly. */
    private fun bytes(seed: Int): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("careerseeker/test/$seed".toByteArray(Charsets.US_ASCII))

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private fun confirmBytesFor(sharedSecret: ByteArray, oneTime: ByteArray): ByteArray =
        Hkdf.deriveKey(
            ikm = sharedSecret,
            salt = oneTime,
            info = Protocol.INFO_CONFIRM.toByteArray(Charsets.US_ASCII),
            length = 4,
        )

    // ---- The confirm code -----------------------------------------------------------------

    /**
     * The six digits the user compares against the desktop screen (§5.2). Two properties in
     * one loop, over 256 independent derivations:
     *
     * **It is always six characters and all of them are digits.** `padStart(6, '0')` makes the
     * *length* six for any non-negative value, so the interesting half is the character class.
     * `PairingDerivation.kt:32-35` assembles the four bytes into a **`Long`**, masking each
     * byte with `and 0xFF`. If that were an `Int`, a first byte of 0x80 or above would shift
     * into the sign bit, `confirmValue` would be negative, Kotlin's `%` would return a
     * negative remainder, and the code would render as `-12345` — six characters, so a length
     * check alone passes it, and a digits check does not.
     *
     * **The loop is known to have reached that case.** The final assertion is not about
     * `PairingDerivation` at all; it is about this test. Without it, a change to `bytes()` or
     * to the info string could quietly move every sample below 0x80 and the signed-overflow
     * path would stop being covered while the test kept passing.
     *
     * The engine reduces the same four bytes as a `uint`
     * (`PairingCrypto.cs:65`, `BinaryPrimitives.ReadUInt32BigEndian`), so a signed phone does
     * not merely look wrong — it disagrees with the desktop the user is reading from, which is
     * the one comparison the confirm code exists to make.
     */
    @Test
    fun `confirm code is six decimal digits, including where the high bit is set`() {
        val digits = Regex("^[0-9]{6}$")
        var highBitSamples = 0

        for (seed in 0 until 256) {
            val shared = bytes(seed)
            val oneTime = bytes(seed + 10_000)
            val code = PairingDerivation.derive(listOf(shared), oneTime).confirmCode

            assertTrue(digits.matches(code), "seed $seed produced a non-numeric code: $code")

            if ((confirmBytesFor(shared, oneTime)[0].toInt() and 0xFF) >= 0x80) highBitSamples++
        }

        assertTrue(
            highBitSamples > 0,
            "no sample had the high bit set, so the signed-overflow path was never exercised",
        )
    }

    /**
     * The reduction itself, recomputed by an independent route and compared.
     *
     * Production assembles the value with four masked shifts. This reads the same four bytes
     * with [ByteBuffer] — big-endian by default — and clears the sign with a single 32-bit
     * mask. Two different mechanisms for one rule: a swapped byte order, a dropped byte or a
     * signed read changes one and not the other.
     *
     * **What this does not prove**, stated so the assertion is not read as wider than it is:
     * both sides here are Kotlin, so this pins the *expression* against the *rule*, not the
     * phone against the engine. The engine agreement is the vectors' job, and
     * `ProtocolVectorsTest` already holds it.
     */
    @Test
    fun `confirm code is the unsigned big-endian reduction of four HKDF bytes`() {
        for (seed in 0 until 32) {
            val shared = bytes(seed)
            val oneTime = bytes(seed + 20_000)

            val unsigned = ByteBuffer.wrap(confirmBytesFor(shared, oneTime)).int.toLong() and 0xFFFFFFFFL
            val expected = (unsigned % 1_000_000L).toString().padStart(6, '0')

            assertEquals(expected, PairingDerivation.derive(listOf(shared), oneTime).confirmCode)
        }
    }

    /**
     * **The modulo-bias question, asked and answered.** The records carried it as open — a
     * `% 1_000_000` over a 32-bit value, noted and never quantified. It is quantified here in
     * exact integers, so the answer is a measurement rather than an intuition.
     *
     * 2³² is not a multiple of 10⁶, so the reduction cannot be uniform. It splits as
     * `2³² = 4294 × 10⁶ + 967296`: the low **967,296** codes have **4295** preimages each and
     * the remaining **32,704** have **4294**. The identity below is what makes that a proof
     * rather than a claim — the two bucket sizes and their counts have to add back to exactly
     * 2³².
     *
     * **So the bias is one preimage wide.** An attacker guessing the single most likely code
     * succeeds with probability `4295 / 2³²` against an ideal `1 / 10⁶ = 4294.967296 / 2³²` —
     * larger by a factor under 1.0000077, i.e. under one part in 130,000. Against a six-digit
     * code whose whole security argument is a ~10⁻⁶ guess and a human comparing two screens,
     * that is not a finding, and **no change is proposed**.
     *
     * **This test is a pin by construction and cannot fail from a production edit** — it reads
     * no production code, only 2³² and 10⁶. Stated plainly because the reverse is easy to
     * assume: what actually holds the modulus and the derived length in place is
     * `confirm code is the unsigned big-endian reduction of four HKDF bytes` above, which
     * recomputes against a literal `1_000_000L` over a literal 4 bytes and compares. A suite
     * that reduced differently would fail *there*, and the arithmetic here would then need
     * recomputing to match. The two are a pair; neither is sufficient alone.
     *
     * A rejection-sampling loop would remove the bias exactly, and is deliberately **not**
     * proposed: it would make the derivation non-total for adversarial inputs, and the engine
     * would have to make the identical choice or the two screens stop matching.
     */
    @Test
    fun `the confirm code's modulo bias is exactly one preimage wide`() {
        val space = 1L shl 32
        val modulus = 1_000_000L

        val small = space / modulus
        val biased = space % modulus

        assertEquals(4294L, small)
        assertEquals(967_296L, biased)

        // The bias is real -- 2^32 is not a multiple of 10^6 -- and it is exactly one wide.
        assertTrue(biased > 0L, "a zero remainder would mean the reduction is uniform")
        assertTrue(biased < modulus, "every code has at least `small` preimages")

        // Every one of the 2^32 inputs lands in exactly one bucket, or the split above is wrong.
        assertEquals(space, biased * (small + 1) + (modulus - biased) * small)

        // p(most likely code) = (small + 1) / 2^32 < 1.0000077 / 10^6, in exact integers.
        assertTrue((small + 1) * modulus * 10_000_000L < 10_000_077L * space)
    }

    // ---- The IKM concatenation ------------------------------------------------------------

    /**
     * `require(sharedSecrets.isNotEmpty())`. Deriving from an empty concatenation would give
     * every caller with no ECDH result the *same* keys, which is worse than failing.
     */
    @Test
    fun `an empty shared-secret list is refused`() {
        assertFailsWith<IllegalArgumentException> {
            PairingDerivation.derive(emptyList(), bytes(1))
        }
    }

    /**
     * **The multi-secret path, reached here for the first time in the module's history.**
     *
     * `PairingDerivation.kt:18-20` keeps the concatenation for the hybrid suite, which appends
     * the ML-KEM secret to the same IKM. That makes the *order* of the list normative — the
     * engine's `Concat` (`PairingCrypto.cs:88-94`) walks its list in the same direction, and
     * two implementations that disagree about which secret comes first derive different keys
     * and fail every subsequent tag check.
     *
     * Nothing in the tree passed a two-element list before this test, so `concat`'s loop body
     * had run exactly once, forever, and the ordering was a property of the source only.
     *
     * **The third assertion is here because a mutation proved the first two insufficient.**
     * Replacing `concat(sharedSecrets)` with `sharedSecrets[0]` — the exact "collapse" the
     * source comment warns against — leaves `[a, b]` and `[b, a]` deriving from `a` and `b`
     * respectively, so they still differ and an order-only test still passes. What that
     * mutation actually destroys is the *second* element's contribution, so that is what has
     * to be asserted: `[a, b]` must not derive what `[a]` alone derives.
     */
    @Test
    fun `shared secrets concatenate in list order, and every element reaches the ikm`() {
        val a = bytes(41)
        val b = bytes(42)
        val oneTime = bytes(43)

        val ab = PairingDerivation.derive(listOf(a, b), oneTime)
        val ba = PairingDerivation.derive(listOf(b, a), oneTime)
        val aOnly = PairingDerivation.derive(listOf(a), oneTime)

        assertNotEquals(ab.relayToken, ba.relayToken)
        assertFalse(ab.keyEngineToPhone.contentEquals(ba.keyEngineToPhone))
        assertFalse(ab.keyPhoneToEngine.contentEquals(ba.keyPhoneToEngine))

        assertNotEquals(ab.relayToken, aOnly.relayToken, "the second secret never reached the ikm")
    }

    /**
     * The concatenation is **unframed** — no lengths, no separators — so `[a, b]` and `[a || b]`
     * are the same IKM and derive the same keys.
     *
     * This is asserted rather than corrected, because for a *fixed* suite it is unambiguous:
     * a suite name pins how many secrets there are and how long each one is, so no two legal
     * inputs of one suite can collide. It is written down because that argument lives entirely
     * in the suite identifier, and the next person to add a suite with a variable-length
     * secret needs to meet this test rather than discover the property afterwards.
     */
    @Test
    fun `the concatenation is unframed, so a split is indistinguishable from its join`() {
        val a = bytes(51)
        val b = bytes(52)
        val oneTime = bytes(53)

        val split = PairingDerivation.derive(listOf(a, b), oneTime)
        val joined = PairingDerivation.derive(listOf(a + b), oneTime)

        assertEquals(split.relayToken, joined.relayToken)
        assertEquals(split.confirmCode, joined.confirmCode)
        assertTrue(split.keyEngineToPhone.contentEquals(joined.keyEngineToPhone))
    }

    // ---- What one derivation yields ---------------------------------------------------------

    /**
     * The four outputs of one `derive` are separated by `info` alone. The directional keys
     * differing is the load-bearing one: `k_e2p == k_p2e` means an envelope captured in one
     * direction opens and re-seals in the other, which is exactly what §5.2's two labels exist
     * to prevent.
     *
     * `HkdfTest` asserts the same separation one layer down, against the raw labels. This
     * asserts it on the product surface, where a future edit is more likely to reach — passing
     * the same `info` twice in `derive` leaves `HkdfTest` entirely green.
     */
    @Test
    fun `one derivation yields distinct, correctly sized directional keys`() {
        val keys = PairingDerivation.derive(listOf(bytes(61)), bytes(62))

        assertEquals(Protocol.KEY_BYTES, keys.keyEngineToPhone.size)
        assertEquals(Protocol.KEY_BYTES, keys.keyPhoneToEngine.size)
        assertFalse(
            keys.keyEngineToPhone.contentEquals(keys.keyPhoneToEngine),
            "the two directional keys must not coincide",
        )
        assertNotEquals(Base64Url.encode(keys.keyEngineToPhone), keys.relayToken)
        assertNotEquals(Base64Url.encode(keys.keyPhoneToEngine), keys.relayToken)
    }

    /**
     * §5.2.1 vs §5.2.3: the provisional token is keyed on the one-time secret **alone**, under
     * `BOOTSTRAP_SALT`, because the phone needs a bearer before it has completed the exchange.
     * The final token is keyed on the IKM. They must differ, or the token ladder in
     * `PairingFlow` rotates onto the value it is rotating away from and the bootstrap
     * credential — which travelled in the QR's blast radius — never actually expires.
     *
     * Also pinned: the provisional token does not depend on the shared secret. That is what
     * makes it derivable at QR-scan time, and it is the reason the two values are different
     * in the first place.
     */
    @Test
    fun `the provisional relay token is not the post-completion token`() {
        val oneTime = bytes(71)
        val provisional = PairingDerivation.provisionalRelayToken(oneTime)

        assertNotEquals(provisional, PairingDerivation.derive(listOf(bytes(72)), oneTime).relayToken)
        assertEquals(provisional, PairingDerivation.provisionalRelayToken(oneTime))
        assertNotEquals(provisional, PairingDerivation.provisionalRelayToken(bytes(73)))
    }

    // ---- The two `|`-joined strings ---------------------------------------------------------

    /**
     * `completionAad`'s exact wire form, pinned against a literal. The engine builds the same
     * string at `PairingCrypto.cs:85-86`; this is an AAD, so the two constructions are
     * compared by an AEAD tag check and any difference at all — a reordered field, a changed
     * separator, a trailing space — presents as a decryption failure with no diagnostic.
     */
    @Test
    fun `completionAad is the pinned four-field format`() {
        assertEquals(
            "careerseeker/v1/pair|p_AAAAAAAAAAAAAAAA|p256-hkdf-sha256|BPhonePub",
            PairingDerivation.completionAad("p_AAAAAAAAAAAAAAAA", Protocol.SUITE, "BPhonePub"),
        )
    }

    /**
     * **Where the delimiter guard actually lives.** `completionAad` joins three caller-supplied
     * strings with `|` and validates none of them, so read locally it is ambiguous: a pairing
     * id containing `|` could produce the same AAD as a different (pairing, suite, key) triple.
     *
     * Measured, that is unreachable on the production path, and this test pins the three
     * reasons rather than adding a fourth check inside `completionAad` — which would be the
     * "phone more correct than the engine" mistake the mission's interpretation rule forbids,
     * since `PairingCrypto.cs` validates nothing here either.
     *
     * Each field is constrained upstream: `parseInvite` refuses a pairing id that is not
     * `p_` + 16 base64url characters (`PairingSession.kt:75-76`) and a suite outside
     * `SUPPORTED_SUITES` (`:71-73`), and the public key is base64url-**encoded** by
     * `buildCompletion` rather than passed through. None of those three alphabets contains
     * `|`. If a later change lets an unvalidated string reach this function, this test is the
     * one that should be read again.
     */
    @Test
    fun `no production path can put the delimiter into an AAD field`() {
        assertFalse(isValidPairingId("p_AAAAAAAA|AAAAAAA"))
        assertFalse(isValidPairingId("p_AAAAAAAAAAAAAAAA|"))
        assertTrue(PairingSession.SUPPORTED_SUITES.none { "|" in it })
        assertTrue(PairingSession.SUPPORTED_SUITES.isNotEmpty())

        // Base64Url's alphabet is A-Za-z0-9-_ , so an encoded key cannot carry a delimiter.
        for (seed in 0 until 16) {
            assertFalse("|" in Base64Url.encode(bytes(seed)))
        }
    }

    /**
     * `signatureInput` (§5.4) hashes the ciphertext instead of embedding it, and renders the
     * digest as **lower-case** hex. The engine spells that
     * `Convert.ToHexString(...).ToLowerInvariant()` (`DeviceSignature.cs:18`); Kotlin's
     * `"%02x"` is the same choice made in a different language, and `%02X` would be a silent
     * divergence that fails every device signature the engine checks.
     *
     * The digest is also where a sign bug would hide: `"%02x".format(byte)` is only two
     * characters because Java's formatter treats a negative `Byte` as its unsigned value. The
     * length assertion plus the coverage counter below is what proves a byte above 0x7f was
     * actually rendered, rather than assumed to be.
     */
    @Test
    fun `signatureInput hashes the ciphertext as lower-case hex`() {
        val lowerHex64 = Regex("^[0-9a-f]{64}$")
        var highByteSamples = 0

        for (seed in 0 until 32) {
            val ciphertext = bytes(seed + 30_000)
            val digest = MessageDigest.getInstance("SHA-256").digest(ciphertext)
            if (digest.any { (it.toInt() and 0xFF) >= 0x80 }) highByteSamples++

            val input = PairingDerivation.signatureInput("aad-here", "nonce-here", ciphertext)
            val parts = input.split("|")

            assertEquals(4, parts.size)
            assertEquals(Protocol.COMMAND_SIG_PREFIX, parts[0])
            assertEquals("aad-here", parts[1])
            assertEquals("nonce-here", parts[2])
            assertTrue(lowerHex64.matches(parts[3]), "digest was not 64 lower-case hex: ${parts[3]}")
            assertEquals(digest.hex(), parts[3])
        }

        assertTrue(highByteSamples > 0, "no digest carried a byte above 0x7f; sign handling was never reached")
    }

    /**
     * All three inputs reach the output. The ciphertext arm is the one worth stating: it is
     * bound by its digest, so two different ciphertexts under one (aad, nonce) must not share
     * a signing input — otherwise a signature over one authorises the other, and §5.4's whole
     * purpose is that the engine can attribute a state change to the device that made it.
     */
    @Test
    fun `signatureInput distinguishes aad, nonce and ciphertext`() {
        val base = PairingDerivation.signatureInput("a", "n", bytes(81))

        assertNotEquals(base, PairingDerivation.signatureInput("b", "n", bytes(81)))
        assertNotEquals(base, PairingDerivation.signatureInput("a", "m", bytes(81)))
        assertNotEquals(base, PairingDerivation.signatureInput("a", "n", bytes(82)))
    }
}
