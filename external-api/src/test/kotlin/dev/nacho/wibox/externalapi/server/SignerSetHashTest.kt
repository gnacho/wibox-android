package dev.nacho.wibox.externalapi.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SignerSetHashTest {
    private val signerA = "3082signer-a-0001".toByteArray(Charsets.UTF_8)
    private val signerB = "3082signer-b-0002".toByteArray(Charsets.UTF_8)
    private val signerC = "3082signer-c-0003".toByteArray(Charsets.UTF_8)

    @Test
    fun `sha256 uses the known test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SignerSetHash.sha256Hex("abc".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun `two different signer sets produce two different digests`() {
        val twoSignatures = SignerSetHash.signerSetSha256(listOf(signerA, signerB))
        val oneSignature = SignerSetHash.signerSetSha256(listOf(signerC))
        assertNotEquals(twoSignatures, oneSignature)
        assertEquals(64, twoSignatures.length)
        assertTrue(twoSignatures.all { it in "0123456789abcdef" })
    }

    @Test
    fun `signer order does not change the set digest`() {
        val first = SignerSetHash.signerSetSha256(listOf(signerA, signerB))
        val second = SignerSetHash.signerSetSha256(listOf(signerB, signerA))
        assertEquals(first, second)
    }

    @Test
    fun `per certificate digests are lowercase hex and sorted`() {
        val digests = SignerSetHash.signerSha256s(listOf(signerB, signerA))
        assertEquals(digests.sorted(), digests)
        digests.forEach { digest ->
            assertEquals(64, digest.length)
            assertTrue(digest.all { it in "0123456789abcdef" })
        }
    }

    @Test
    fun `a single different signer changes the digest`() {
        val base = SignerSetHash.signerSetSha256(listOf(signerA, signerB))
        val swapped = SignerSetHash.signerSetSha256(listOf(signerA, signerC))
        assertNotEquals(base, swapped)
    }
}
