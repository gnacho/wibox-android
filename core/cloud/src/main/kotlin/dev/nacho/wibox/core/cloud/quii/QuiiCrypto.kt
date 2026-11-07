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

    /**
     * Descifra solo el prefijo cifrado de [data] y deja el resto tal cual: en un
     * cpacket de media solo va cifrado el prefijo que anuncia la cabecera y el H264
     * viaja en claro.
     *
     * [prefixLen] puede no ser multiplo de 16 (la cabecera lo anuncia en crudo). Se
     * redondea hacia abajo y se descifran solo los bloques completos, conservando el
     * sobrante tal cual llego: con un valor desalineado, `aesCbcDecrypt` lanzaba y
     * `decodeChained` perdia el resto del blob.
     */
    fun aesCbcDecryptAlignedPrefix(key: ByteArray, data: ByteArray, prefixLen: Int): ByteArray {
        val prefix = prefixLen.coerceIn(0, data.size)
        val aligned = prefix - prefix % 16
        if (aligned <= 0) return data.copyOf()
        return aesCbcDecrypt(key, data.copyOfRange(0, aligned)) + data.copyOfRange(aligned, data.size)
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
