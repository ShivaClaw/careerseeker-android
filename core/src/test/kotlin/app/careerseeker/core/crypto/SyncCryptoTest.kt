package app.careerseeker.core.crypto

import app.careerseeker.core.Direction
import app.careerseeker.core.EnvelopeHeader
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Direct coverage for [SyncCrypto] — the module's AEAD/ECDH/ECDSA codec, and the last of the
 * three files under `core/…/crypto/` to have no test file of its own.
 *
 * `grep -rl SyncCrypto core/src/test` printed six files before this one existed, so unlike
 * [Hkdf] and [Base64Url] this class was never *unreferenced*. It was something narrower and
 * easier to miss: **referenced everywhere as a tool, asserted about nowhere.** Every one of
 * those six uses it to build or open a fixture on the way to testing something else
 * (`SyncPumpTest`, `EnvelopeReceiverTest`, `PairingSessionTest`, `OutboundEnvelopesTest`,
 * `EntitlementVectorsTest`, `ProtocolVectorsTest`). A codec that is only ever exercised as
 * scaffolding is tested exactly on the inputs its scaffolding happens to produce.
 *
 * Two measured consequences motivated this file, both established before it was written:
 *
 * 1. **`verifySignature` is called once in the whole module** (`ProtocolVectorsTest.kt:146`),
 *    and the shared vectors carry **eight** distinct 64-byte signatures. All eight have a
 *    non-zero leading byte in both `r` and `s` — so [SyncCrypto]'s private `toDerInteger`
 *    leading-zero **strip loop has never executed**, in the product or in the suite. Same
 *    shape as `HkdfTest`'s multi-block finding: a branch that exists, is reachable, and has
 *    never run. Section D pins both sides of it with signatures generated for the purpose.
 *
 * 2. **No vector puts a non-ASCII byte in the AAD.** 26 vendored vectors, 23 with an `aad`
 *    field, **zero** non-ASCII among them; the one vector that does carry non-ASCII —
 *    `heartbeat-unicode.json`, whose note says it "catches implementations that treat UTF-8 as
 *    Latin-1 or mangle surrogate pairs" — puts it in the **plaintext**, and its AAD is plain
 *    ASCII. So the suite deliberately tests the body's charset and has never tested the
 *    header's. Section B is that gap, and what it found is in the section's own docs.
 *
 * Re-derive both counts with `AUDIT-REQUEST.md` C-SC-1 and C-SC-2.
 *
 * **A third thing came out of the mutation run rather than out of the plan, and it bounds
 * this whole module.** Eight mutations were applied to `SyncCrypto.kt` and reverted; **four
 * were caught** (M1 by two tests, M3, M4, M5) and **four survived** — but not for one reason,
 * and the split is the point.
 *
 * **M6 survives because it is semantically redundant** — the explicit 64-byte gate duplicates
 * an `IndexOutOfBoundsException` the `try` already converts to `false`. That is not a coverage
 * gap and is not recorded as one.
 *
 * **M2, M7 and M8 survive for the same, sharper reason: they are only observable under a JCA
 * provider stricter than the one the tests run on.** `:core:test` executes on the JDK, where
 * the provider is `SunEC`; the app executes on Android, where it is Conscrypt. Measured here:
 * `SunEC` accepts unpadded negative DER INTEGERs, returns fixed-width ECDH secrets even when
 * the top byte is zero, and validates EC points lazily rather than at `generatePublic`. So the
 * positive pad, the left-pad, and the entire `catch` in `verifySignature` are **unobservable
 * on this provider by construction**, not merely untested — and no test written here, by
 * anyone, can close them. Each of the three says so at its own site rather than claiming a
 * strength it does not have. Recorded as **PQ-SC-1**.
 *
 * **What this file deliberately does not do: change [SyncCrypto].** Section B records two ways
 * the AAD fails to bind what §3 says it binds. Both are cross-implementation questions — the
 * engine builds the same string in `src/Sync/` (ShivaClaw/careerseeker) — and the mission's
 * standing rule is that a phone stricter than an unmeasured engine is a field bug, not a fix.
 * No .NET exists in a cloud sandbox, so the engine half is unmeasured. Filed as **PQ-AAD-1**
 * in `docs/protocol-questions.md`; the tests below pin *current* behaviour so that whoever
 * closes it can see the change in a diff.
 */
class SyncCryptoTest {

    // ---------------------------------------------------------------- helpers

    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    private val key = hex("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90")
    private val nonce = ByteArray(12) { it.toByte() }
    private val body = """{"kind":"heartbeat","body":{"cycle":1}}""".toByteArray(Charsets.UTF_8)

    /** A realistic v1 AAD, matching the shape `EnvelopeHeader.aad()` emits. */
    private fun aad(ts: String = "2026-06-11T14:02:11Z", keyId: String = "k-2026-06-01") =
        EnvelopeHeader(1, "p_7Fq2mXk9LtVbN3wR", Direction.ENGINE_TO_PHONE, 3, ts, keyId).aad()

    private fun p256KeyPair() = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun BigInteger.fixed32(): ByteArray {
        val raw = toByteArray()
        val out = ByteArray(32)
        if (raw.size >= 32) raw.copyInto(out, 0, raw.size - 32, raw.size)
        else raw.copyInto(out, 32 - raw.size)
        return out
    }

    private fun scalarOf(kp: java.security.KeyPair) = (kp.private as ECPrivateKey).s.fixed32()

    private fun uncompressedOf(kp: java.security.KeyPair): ByteArray {
        val w = (kp.public as ECPublicKey).w
        return byteArrayOf(0x04) + w.affineX.fixed32() + w.affineY.fixed32()
    }

    // ================================================================ A. AES-256-GCM

    @Test
    fun `seal then open round-trips under the same key nonce and aad`() {
        val sealed = SyncCrypto.seal(key, nonce, aad(), body)
        assertContentEquals(body, SyncCrypto.open(key, nonce, aad(), sealed))
    }

    /**
     * The tag is appended, so the ciphertext is exactly plaintext + [app.careerseeker.core.Protocol.TAG_BYTES].
     * `RelayClient` and the 1 MiB envelope cap both reason about sealed size; this pins the
     * relationship they assume.
     */
    @Test
    fun `sealed length is plaintext length plus the 16-byte tag`() {
        assertEquals(body.size + 16, SyncCrypto.seal(key, nonce, aad(), body).size)
        assertEquals(16, SyncCrypto.seal(key, nonce, aad(), ByteArray(0)).size)
    }

    @Test
    fun `an empty plaintext round-trips rather than being refused`() {
        val sealed = SyncCrypto.seal(key, nonce, aad(), ByteArray(0))
        assertContentEquals(ByteArray(0), SyncCrypto.open(key, nonce, aad(), sealed))
    }

    /**
     * The exception type is pinned deliberately, because [SyncCrypto.open]'s KDoc gets it
     * wrong: it says `AEADBadTagException` is "an IllegalStateException". It is not —
     * `AEADBadTagException` → `BadPaddingException` → `GeneralSecurityException` →
     * `Exception`. Nothing in that chain is `IllegalStateException`.
     *
     * Harmless today and checked rather than assumed: the only production caller is
     * `EnvelopeReceiver.kt:82`, which catches `Exception` and is therefore correct by
     * accident of being broad. It is a live trap for the next caller, who would read the
     * KDoc, catch `IllegalStateException`, and have an authentication failure escape as a
     * crash. This test is what makes that concrete.
     */
    @Test
    fun `a tampered ciphertext fails authentication with AEADBadTagException`() {
        val sealed = SyncCrypto.seal(key, nonce, aad(), body)
        sealed[0] = (sealed[0].toInt() xor 0x01).toByte()
        val thrown = assertFailsWith<AEADBadTagException> { SyncCrypto.open(key, nonce, aad(), sealed) }
        assertFalse(
            IllegalStateException::class.java.isAssignableFrom(thrown.javaClass),
            "SyncCrypto.open's KDoc calls AEADBadTagException an IllegalStateException; it is not",
        )
        assertEquals(
            listOf(
                "javax.crypto.AEADBadTagException",
                "javax.crypto.BadPaddingException",
                "java.security.GeneralSecurityException",
                "java.lang.Exception",
            ),
            generateSequence<Class<*>>(thrown.javaClass) { it.superclass }
                .takeWhile { it.name != "java.lang.Throwable" }
                .map { it.name }
                .toList(),
            "the actual hierarchy, so the KDoc can be corrected against a measurement",
        )
    }

    @Test
    fun `a tampered tag fails authentication`() {
        val sealed = SyncCrypto.seal(key, nonce, aad(), body)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<AEADBadTagException> { SyncCrypto.open(key, nonce, aad(), sealed) }
    }

    @Test
    fun `a changed aad fails authentication`() {
        val sealed = SyncCrypto.seal(key, nonce, aad(), body)
        assertFailsWith<AEADBadTagException> { SyncCrypto.open(key, nonce, aad(ts = "2026-06-11T14:02:12Z"), sealed) }
    }

    @Test
    fun `a wrong key fails authentication`() {
        val sealed = SyncCrypto.seal(key, nonce, aad(), body)
        val other = key.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFailsWith<AEADBadTagException> { SyncCrypto.open(other, nonce, aad(), sealed) }
    }

    @Test
    fun `a wrong nonce fails authentication`() {
        val sealed = SyncCrypto.seal(key, nonce, aad(), body)
        val other = nonce.copyOf().also { it[11] = (it[11].toInt() xor 0x01).toByte() }
        assertFailsWith<AEADBadTagException> { SyncCrypto.open(key, other, aad(), sealed) }
    }

    /**
     * The size guards throw `IllegalArgumentException`, which matters at exactly one place:
     * `EnvelopeReceiver` decodes `nonce` from base64url and hands the result straight to
     * [SyncCrypto.open], so a wire nonce of the wrong length reaches this `require`. Its
     * `catch (_: Exception)` turns that into `decrypt_failed`, which is what §7.2 wants —
     * but only because the catch is broad enough to cover a *different* exception family
     * from the authentication failure two lines above it.
     */
    @Test
    fun `key and nonce sizes are enforced on both seal and open`() {
        for (badKey in listOf(ByteArray(0), ByteArray(16), ByteArray(31), ByteArray(33))) {
            assertFailsWith<IllegalArgumentException> { SyncCrypto.seal(badKey, nonce, aad(), body) }
            assertFailsWith<IllegalArgumentException> { SyncCrypto.open(badKey, nonce, aad(), ByteArray(32)) }
        }
        for (badNonce in listOf(ByteArray(0), ByteArray(11), ByteArray(13), ByteArray(16))) {
            assertFailsWith<IllegalArgumentException> { SyncCrypto.seal(key, badNonce, aad(), body) }
            assertFailsWith<IllegalArgumentException> { SyncCrypto.open(key, badNonce, aad(), ByteArray(32)) }
        }
    }

    /**
     * Non-ASCII in the *plaintext* survives exactly, which is what `heartbeat-unicode.json`
     * asserts against the shared vectors. It is included here as the contrast case for
     * section B: the body is length-delimited bytes and round-trips; the AAD is a string that
     * gets re-encoded, and does not.
     */
    @Test
    fun `non-ASCII plaintext round-trips byte-exactly`() {
        val unicode = """{"note":"café — naïve 日本語 🙂"}""".toByteArray(Charsets.UTF_8)
        val sealed = SyncCrypto.seal(key, nonce, aad(), unicode)
        assertContentEquals(unicode, SyncCrypto.open(key, nonce, aad(), sealed))
    }

    // ================================================================ B. AAD canonicalisation
    //
    // Two independent ways `EnvelopeHeader.aad()` + SyncCrypto's US_ASCII encoding fail to be
    // injective — distinct headers that produce byte-identical AAD, and therefore authenticate
    // each other's envelopes. Neither is reachable from a conforming sender today, and both
    // tests say so; they are recorded as PQ-AAD-1 rather than fixed here, because the fix has
    // to be symmetric with the C# engine and no cloud session can measure that side.

    /**
     * **Finding 1 — the AAD is encoded `US_ASCII`, which is lossy, so it does not bind any
     * non-ASCII content in `ts` or `key_id`.**
     *
     * Java's `US_ASCII` encoder replaces every unmappable character with `?` (0x3F). Measured
     * here rather than argued: `é`, `è`, `Ж` and `😀` all become the single byte 0x3F, and
     * therefore collide with each other *and* with a literal `?`. A surrogate pair collapses
     * to one `?`, not two.
     *
     * `EnvelopeJson.parse` validates `pairing` against a regex and parses `v`/`seq`/`dir` into
     * types, but `ts` and `key_id` are taken as arbitrary JSON strings with **no charset or
     * content check at all** (`EnvelopeJson.kt:55-56`). So a crafted envelope can put any
     * Unicode into either field and reach this encoder.
     *
     * **Why this is latent rather than exploitable, stated precisely.** Rewriting a header
     * field only survives authentication if the *original* bytes and the *rewritten* bytes
     * agree after encoding. Conforming senders emit RFC 3339 timestamps and generated key ids,
     * both pure ASCII, so every mutation of a real envelope changes a byte and fails the tag.
     * The collision class is only reachable when the genuine sender put a non-ASCII character
     * (or a literal `?`) in `ts`/`key_id` to begin with — which nothing in either
     * implementation does. It is a canonicalisation gap, not a live bypass, and calling it a
     * bypass would be the phantom these records exist to prevent.
     *
     * **The cross-implementation half is the part that actually matters, and it is
     * unmeasured.** If `src/Sync/` builds its AAD with UTF-8 rather than ASCII, the two sides
     * agree on every all-ASCII header and disagree on every other one — the engine and the
     * phone would compute different AAD bytes for the same envelope and each would report
     * `decrypt_failed` on the other's traffic. Identical in shape to PQ-B64-1. No vector can
     * express it either: `generate.mjs` emits canonical values, and all 23 AADs in the
     * vendored set are ASCII.
     */
    @Test
    fun `the aad encoder is lossy so distinct non-ASCII timestamps authenticate each other`() {
        val acute = aad(ts = "2026-06-11T14:02:11Zé")
        val grave = aad(ts = "2026-06-11T14:02:11Zè")
        val cyrillic = aad(ts = "2026-06-11T14:02:11ZЖ")
        val emoji = aad(ts = "2026-06-11T14:02:11Z😀")
        val literalQuestionMark = aad(ts = "2026-06-11T14:02:11Z?")

        // They are different strings...
        assertNotEquals(acute, grave)
        assertNotEquals(acute, literalQuestionMark)

        // ...and identical once the codec encodes them, which is the only form GCM sees.
        val asAscii = { s: String -> s.toByteArray(Charsets.US_ASCII) }
        assertContentEquals(asAscii(acute), asAscii(grave))
        assertContentEquals(asAscii(acute), asAscii(cyrillic))
        assertContentEquals(asAscii(acute), asAscii(emoji), "a surrogate pair also collapses to one 0x3F")
        assertContentEquals(asAscii(acute), asAscii(literalQuestionMark))

        // The consequence, demonstrated end to end: sealed under one, opened under another.
        val sealed = SyncCrypto.seal(key, nonce, acute, body)
        assertContentEquals(body, SyncCrypto.open(key, nonce, grave, sealed))
        assertContentEquals(body, SyncCrypto.open(key, nonce, emoji, sealed))
        assertContentEquals(body, SyncCrypto.open(key, nonce, literalQuestionMark, sealed))

        // And the control: under UTF-8 they would all be distinct, which is what makes the
        // choice of charset — not the delimiter design — the operative cause here.
        assertFalse(
            acute.toByteArray(Charsets.UTF_8).contentEquals(grave.toByteArray(Charsets.UTF_8)),
            "under UTF-8 these AADs differ; the collision is created by the US_ASCII encoding",
        )
    }

    /**
     * **Finding 2 — the AAD's `|`/`=` framing is ambiguous, and needs no non-ASCII at all.**
     *
     * `aad()` joins fields with `|` and `=` and neither `ts` nor `key_id` is checked for
     * either character, so content can be moved across the `|key_id=` boundary without
     * changing a single byte of the result:
     *
     * ```
     * ts = "T"          key_id = "K|key_id=Z"   ->  …|ts=T|key_id=K|key_id=Z
     * ts = "T|key_id=K" key_id = "Z"            ->  …|ts=T|key_id=K|key_id=Z
     * ```
     *
     * Two distinct header tuples, one AAD. `ts` and `key_id` are the last two fields and are
     * the only two free-form ones, which is exactly why they are the pair that collides —
     * `pairing` is regex-checked, and `v`/`seq`/`dir` are typed.
     *
     * **Why this is latent too, and self-limiting in a second way.** As above, a rewrite only
     * survives if the genuine sender's bytes already contained the delimiters. On top of that,
     * `key_id` selects the decryption key before the AAD is ever built, so the rewritten form
     * has to name a key the receiver actually holds — a real key id containing `|key_id=` is
     * not something either implementation generates.
     *
     * It is recorded because the AAD's whole job is to be an injective encoding of the header,
     * its KDoc says field order "is normative" and that a fixed ASCII string was chosen
     * *because* "two independent implementations have to agree exactly" — and injectivity is
     * the unstated assumption underneath that, resting on validation `EnvelopeJson` does not
     * perform.
     */
    @Test
    fun `the aad framing is ambiguous across the ts and key_id boundary`() {
        val a = aad(ts = "T", keyId = "K|key_id=Z")
        val b = aad(ts = "T|key_id=K", keyId = "Z")

        assertEquals(a, b, "two distinct (ts, key_id) pairs produce one AAD string")

        val sealed = SyncCrypto.seal(key, nonce, a, body)
        assertContentEquals(
            body,
            SyncCrypto.open(key, nonce, b, sealed),
            "so an envelope sealed under one header opens under the other",
        )
    }

    /**
     * The reason findings 1 and 2 are latent, pinned as a fact rather than left as a claim:
     * a conforming header produces an all-ASCII AAD containing no `|` or `=` inside any
     * field value. If a future change lets a non-ASCII or delimiter-bearing value into `ts`
     * or `key_id`, this test is what fails.
     */
    @Test
    fun `a conforming header produces an unambiguous all-ASCII aad`() {
        val s = aad()
        assertTrue(s.all { it.code in 0x20..0x7E }, "conforming AAD is printable ASCII: $s")
        assertContentEquals(s.toByteArray(Charsets.US_ASCII), s.toByteArray(Charsets.UTF_8))
        assertEquals(5, s.count { it == '|' }, "exactly five separators for six fields")
        assertEquals(6, s.count { it == '=' }, "exactly one '=' per field")
    }

    // ================================================================ C. ECDH P-256

    @Test
    fun `ecdh is symmetric between the two parties`() {
        val engine = p256KeyPair()
        val phone = p256KeyPair()
        val fromEngine = SyncCrypto.ecdhSharedSecret(scalarOf(engine), uncompressedOf(phone))
        val fromPhone = SyncCrypto.ecdhSharedSecret(scalarOf(phone), uncompressedOf(engine))
        assertContentEquals(fromEngine, fromPhone)
    }

    /**
     * §5.2 requires the raw 32-byte X coordinate, and [SyncCrypto] left-pads what the JCA
     * hands back "in case of a leading-zero secret".
     *
     * This fixture is a pair found by search (C-SC-4) whose shared secret genuinely begins
     * `0x00` — the case the padding exists for, which a random pair reaches only ~1 time in
     * 256.
     *
     * **It is still a pin rather than a regression catcher, and the mutation run is why.**
     * Deleting `leftPad` (**M7**) leaves the whole suite green. Measured directly rather than
     * inferred: `SunEC`'s `KeyAgreement.generateSecret()` returns a **fixed-width 32-byte**
     * array for P-256 even when the X coordinate's top byte is zero — so `leftPad` never
     * fires on this provider and cannot be observed by any test running on it. The initial
     * version of this comment claimed M7 proved the test was a catcher; that was written
     * before the mutation was run, and the run disproved it.
     *
     * The padding is therefore not dead code so much as **provider insurance**, and the
     * provider it insures against is not the one the suite runs on. `:core:test` runs on the
     * JDK (`SunEC`); the phone runs on Android (Conscrypt). A provider that returns the
     * BigInteger-minimal form would hand HKDF a 31-byte IKM — a *different* IKM — and the two
     * ends would derive different directional keys for roughly 1 pairing in 256. That is the
     * worst possible frequency for a bug: far too rare to find by hand, far too common to
     * never happen in the field.
     */
    @Test
    fun `an ecdh shared secret with a leading zero byte is left-padded to 32 bytes`() {
        val aD = hex("ef05145101f1f7ac0c32401997d46a1fa98c43f7a740ef097c5563a66a783e0c")
        val aPub = hex(
            "04c140b3d8632fe4b65f954fd528787a8d49cc3edaedb4d178ca8b0ca9effcde83" +
                "f30bc7c87cd732dcae18040e339391c177cd966c86ec3956ad91cd45f37d11bb",
        )
        val bD = hex("fcc34ccbc1058d3e6d0f931a80897879c2084defbd5de2a88604d176969041b8")
        val bPub = hex(
            "04f49624aba444bc99079d23b15a0a4bae6f117bc2056131a71e74861a21fbf72b" +
                "67416a7d1dd5a1c5d4b7b66db43a1bfdeba7f997f5943cf43d8aa52ff15845dd",
        )
        val expected = hex("00e34c6ffb3bbdcde790ef53a42850107a3005b88f6fd9dc3c602225153ea250")

        val fromA = SyncCrypto.ecdhSharedSecret(aD, bPub)
        val fromB = SyncCrypto.ecdhSharedSecret(bD, aPub)

        assertEquals(32, fromA.size, "a leading-zero secret must still be 32 bytes wide")
        assertContentEquals(expected, fromA)
        assertContentEquals(expected, fromB)
    }

    @Test
    fun `the ecdh shared secret is always exactly 32 bytes`() {
        repeat(32) {
            val a = p256KeyPair()
            val b = p256KeyPair()
            assertEquals(32, SyncCrypto.ecdhSharedSecret(scalarOf(a), uncompressedOf(b)).size)
        }
    }

    @Test
    fun `distinct peers give distinct shared secrets`() {
        val own = p256KeyPair()
        val first = SyncCrypto.ecdhSharedSecret(scalarOf(own), uncompressedOf(p256KeyPair()))
        val second = SyncCrypto.ecdhSharedSecret(scalarOf(own), uncompressedOf(p256KeyPair()))
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `a malformed peer point is refused rather than silently agreed with`() {
        val own = scalarOf(p256KeyPair())
        val good = uncompressedOf(p256KeyPair())

        // Wrong length, and the wrong leading tag byte (compressed form is not accepted).
        assertFailsWith<IllegalArgumentException> { SyncCrypto.ecdhSharedSecret(own, ByteArray(64)) }
        assertFailsWith<IllegalArgumentException> { SyncCrypto.ecdhSharedSecret(own, ByteArray(65)) }
        assertFailsWith<IllegalArgumentException> {
            SyncCrypto.ecdhSharedSecret(own, good.copyOf().also { it[0] = 0x02 })
        }

        // Right shape, but the point is not on the curve. This must not produce a secret.
        val offCurve = good.copyOf().also { it[64] = (it[64].toInt() xor 0x01).toByte() }
        assertFailsWith<Exception> { SyncCrypto.ecdhSharedSecret(own, offCurve) }
    }

    // ================================================================ D. ECDSA and the DER transcoder

    // A P-256 key pair generated once for this file, with two signatures found by search.
    // Hard-coded rather than searched at test time on purpose: ECDSA's nonce is random, so
    // "sign until r has a leading zero" would put a ~1-in-256 retry loop in the suite, and
    // this repo already carries one flaky test (BLOCKED.md, twentieth iteration). These
    // values were produced with node:crypto and are reproducible from AUDIT-REQUEST.md C-SC-3.
    private val sigPub = hex(
        "045347d7b6dfbcd49934647fec0196104283f76522544d568b5be291a656f66087" +
            "119f4cb7c7741be2d2cd214595c8e187ddd325e7787c7cbd4073ac2f9158a2a0",
    )

    /** `r` begins 0x00 — the strip branch no shared vector reaches. */
    private val leadingZeroR = "careerseeker/v1/cmd|probe|581" to hex(
        "00a3ea4ea49123fe478aea1baf987840bfd34024b2c94a357663e745d80ebc7f" +
            "c26858b83579ffc5edc0c2403faa1a5a60a2cc2276624914fb3dfbc583d620a0",
    )

    /** `s` begins 0x00, and its next byte has the high bit clear — strip, then no pad. */
    private val leadingZeroS = "careerseeker/v1/cmd|probe|1290" to hex(
        "fa3057cf108fb53040f9665cebb7aa6de6697e88ae731ef63418e8e3bad5fa29" +
            "0051cc29f584912f7fd2cb2acde29e9a5fd66c14f58846c612ccb01541b7f51d",
    )

    /**
     * The finding this file was opened for, **restated after the mutation run corrected it.**
     *
     * All eight signatures in the vendored vector set have `r[0] != 0` and `s[0] != 0`
     * (C-SC-1), so `toDerInteger`'s `while (i < v.size - 1 && v[i].toInt() == 0) i++` had
     * never taken a single iteration. That much held. What did *not* hold is the obvious
     * reading of it — that any leading-zero signature exercises the branch.
     *
     * **A leading zero is not sufficient.** Stripping a `0x00` whose *following* byte has the
     * high bit set immediately re-adds it as the positive pad, so strip-then-pad is a
     * byte-for-byte no-op and the encoding is identical either way. The branch is only
     * load-bearing when the byte after the zero has its high bit **clear**, which is the
     * `leadingZeroS` fixture and not this one.
     *
     * Measured, not reasoned: mutation **M1** (strip loop deleted) leaves this test green and
     * fails the two that use `leadingZeroS`. This case is therefore kept and named for what it
     * is — the complementary no-op — because a future reader picking a leading-zero fixture at
     * random would very likely pick this shape and conclude the branch was covered.
     *
     * Corrected frequency: a strip is needed when a component starts `0x00` *and* its next
     * byte is `< 0x80` — about 1 in 512 per component, so roughly **1 signature in 256**, not
     * the 1 in 128 this comment claimed before the mutation run.
     */
    @Test
    fun `a leading zero followed by a high-bit byte is a strip-then-pad no-op`() {
        val (msg, sig) = leadingZeroR
        assertEquals(0, sig[0].toInt(), "fixture precondition: r starts with 0x00")
        assertTrue((sig[1].toInt() and 0x80) != 0, "fixture precondition: r's next byte has the high bit set")
        assertTrue(SyncCrypto.verifySignature(sigPub, msg, sig))
    }

    /**
     * The case that actually reaches the strip loop: `s` begins `0x00` and its next byte
     * (`0x51`) has the high bit clear, so the minimal DER INTEGER is 31 bytes and a
     * transcoder that skipped the strip would emit a non-minimal one. **SunEC rejects that**,
     * which is what makes this the test M1 fails against — and the only one in the module
     * that has ever driven `toDerInteger`'s loop body.
     */
    @Test
    fun `a signature whose s has a leading zero and a low next byte verifies`() {
        val (msg, sig) = leadingZeroS
        assertEquals(0, sig[32].toInt(), "fixture precondition: s starts with 0x00")
        assertTrue((sig[33].toInt() and 0x80) == 0, "fixture precondition: s's next byte has the high bit clear")
        assertTrue(SyncCrypto.verifySignature(sigPub, msg, sig))
    }

    /**
     * The other side of the branch — a top-bit-set value must gain a `0x00` pad or DER reads
     * it as negative. Six of the eight vector signatures have `r` high-bit set, so this shape
     * is well covered.
     *
     * **Labelled a pin, not a regression catcher, because the mutation run says so.** Deleting
     * the pad (**M2**) leaves the whole suite green: measured directly, `SunEC` *accepts* an
     * unpadded negative INTEGER, while rejecting the non-minimal encoding M1 produces. So this
     * assertion cannot fail on this JVM no matter what the pad does.
     *
     * That asymmetry is worth more than the test is. It means the transcoder's conformance to
     * DER is **provider-dependent**, and `:core:test` only ever runs on the JDK's `SunEC` —
     * here and in CI. The phone runs on Android, where the provider is Conscrypt, and nothing
     * in this module exercises it. See PQ-AAD-1's sibling note in `docs/protocol-questions.md`.
     */
    @Test
    fun `a signature whose r has the high bit set verifies`() {
        val (msg, sig) = leadingZeroS // r = 0xfa30… , high bit set
        assertTrue((sig[0].toInt() and 0x80) != 0, "fixture precondition: r has the high bit set")
        assertTrue(SyncCrypto.verifySignature(sigPub, msg, sig))
    }

    @Test
    fun `a valid signature fails against a different message`() {
        val (_, sig) = leadingZeroR
        assertFalse(SyncCrypto.verifySignature(sigPub, "careerseeker/v1/cmd|probe|582", sig))
    }

    @Test
    fun `a valid signature fails against a different key`() {
        val (msg, sig) = leadingZeroR
        assertFalse(SyncCrypto.verifySignature(uncompressedOf(p256KeyPair()), msg, sig))
    }

    @Test
    fun `a tampered signature is refused`() {
        val (msg, sig) = leadingZeroR
        val tampered = sig.copyOf().also { it[40] = (it[40].toInt() xor 0x01).toByte() }
        assertFalse(SyncCrypto.verifySignature(sigPub, msg, tampered))
    }

    /**
     * Malformed inputs return `false` rather than throwing. That matters because the caller in
     * `EnvelopeReceiver` treats `false` as `bad_signature` (§7.2), and an exception escaping
     * here would leave the receiver by a different path than the protocol defines.
     *
     * **Two honest limits on what this test proves, both measured in the mutation run.**
     *
     * Deleting the explicit `rawSignature.size != 64` gate (**M6**) leaves the suite green:
     * `rawToDer`'s `copyOfRange` throws inside the `try` for any other length and the catch
     * turns it into `false` anyway. The gate is defence in depth and reads better than the
     * accident it duplicates; it is not a coverage gap.
     *
     * More importantly, making the catch **rethrow** (**M8**) *also* leaves the suite green —
     * meaning none of the inputs below actually reach the catch. Measured rather than assumed:
     * on `SunEC`, `KeyFactory.generatePublic` returns normally for an off-curve point **and**
     * for coordinates larger than the field prime; the invalidity surfaces as `verify()`
     * returning `false`, not as an exception. So the `catch (_: Exception)` is unreached on
     * this provider, and this test pins the *return value* contract without proving the catch
     * works. Closing that would need a provider that validates points eagerly — Conscrypt, on
     * a device, which B-7 puts out of reach here.
     */
    @Test
    fun `malformed signatures and keys return false rather than throwing`() {
        val (msg, sig) = leadingZeroR

        // Wrong raw signature length — the explicit size gate.
        for (bad in listOf(ByteArray(0), ByteArray(63), ByteArray(65), ByteArray(70))) {
            assertFalse(SyncCrypto.verifySignature(sigPub, msg, bad))
        }

        // Wrong key shape — length, and the uncompressed-form tag byte.
        assertFalse(SyncCrypto.verifySignature(ByteArray(0), msg, sig))
        assertFalse(SyncCrypto.verifySignature(ByteArray(65), msg, sig))
        assertFalse(SyncCrypto.verifySignature(sigPub.copyOf().also { it[0] = 0x02 }, msg, sig))

        // Right shape, off-curve point: caught by the internal catch, not propagated.
        assertFalse(
            SyncCrypto.verifySignature(sigPub.copyOf().also { it[64] = (it[64].toInt() xor 0x01).toByte() }, msg, sig),
        )

        // All-zero r and s: DER-encodable as INTEGER 0, and not a valid signature.
        assertFalse(SyncCrypto.verifySignature(sigPub, msg, ByteArray(64)))
    }

    /**
     * §5.4 signs an ASCII domain-separated string. This pins that the *signature* input is
     * encoded with the same `US_ASCII` codec as the AAD — so finding 1 applies to command
     * signatures as well, and for the same reason. `COMMAND_SIG_PREFIX` and the fields around
     * it are ASCII in every conforming caller, so this is latent in the same way.
     */
    @Test
    fun `the signature input is US_ASCII encoded like the aad`() {
        val (msg, sig) = leadingZeroR
        assertTrue(SyncCrypto.verifySignature(sigPub, msg, sig))
        // A non-ASCII character in the signed string collapses to '?' before hashing, so a
        // string differing only there verifies against the same signature. Demonstrated by
        // signing the '?' form and verifying the non-ASCII form.
        val withQuestionMark = "careerseeker/v1/cmd|probe|581?"
        val withNonAscii = "careerseeker/v1/cmd|probe|581é"
        assertNotEquals(withQuestionMark, withNonAscii)
        assertContentEquals(
            withQuestionMark.toByteArray(Charsets.US_ASCII),
            withNonAscii.toByteArray(Charsets.US_ASCII),
        )
    }
}
