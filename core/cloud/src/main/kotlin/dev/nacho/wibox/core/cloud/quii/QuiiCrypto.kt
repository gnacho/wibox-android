package dev.nacho.wibox.core.cloud.quii

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object QuiiCrypto {
    val IV: ByteArray = ByteArray(16) { '0'.code.toByte() }

    fun aesCbcEncrypt(key: ByteArray, plain: ByteArray): ByteArray {
        require(key.size == 16 || key.size == 32) { "key must be 16 or 32 bytes" }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(IV))
        return cipher.doFinal(zeroPad(plain))
    }

    fun aesCbcDecrypt(key: ByteArray, enc: ByteArray): ByteArray {
        require(enc.size % 16 == 0) { "ciphertext must be block aligned" }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(IV))
        return cipher.doFinal(enc)
    }

    fun aesCbcDecryptAlignedPrefix(key: ByteArray, data: ByteArray, prefixLen: Int): ByteArray {
        val prefix = aesCbcDecrypt(key, data.copyOfRange(0, prefixLen))
        return prefix + data.copyOfRange(prefixLen, data.size)
    }

    fun zeroPad(data: ByteArray): ByteArray {
        val rem = data.size % 16
        if (rem == 0) return data
        return data + ByteArray(16 - rem)
    }

    fun sha256(vararg chunks: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        for (c in chunks) md.update(c)
        return md.digest()
    }

    fun sha256Hex(value: String): String =
        sha256(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun encodeDevicePassword(value: String): String =
        if (value.length < 64) sha256Hex(value) else value
}
