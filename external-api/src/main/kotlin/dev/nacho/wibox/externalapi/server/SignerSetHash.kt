package dev.nacho.wibox.externalapi.server

import java.security.MessageDigest

/**
 * Derives the signer-set digest used by the authorization allowlist.
 *
 * Each signing certificate becomes a lowercase hex SHA-256 without separators.
 * The per-certificate digests are sorted and joined so that one digest
 * identifies the complete current signer set. Two different signer sets
 * therefore always produce two different digests, and the digest is stable
 * regardless of the order in which the certificates are supplied.
 */
object SignerSetHash {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

    fun signerSha256s(certificates: List<ByteArray>): List<String> =
        certificates.map(::sha256Hex).sorted()

    fun signerSetSha256(certificates: List<ByteArray>): String =
        sha256Hex(signerSha256s(certificates).joinToString(separator = "\n").toByteArray(Charsets.UTF_8))
}
