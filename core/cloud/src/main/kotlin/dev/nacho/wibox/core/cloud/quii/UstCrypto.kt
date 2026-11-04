package dev.nacho.wibox.core.cloud.quii

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cripto UST (credenciales MQTT del servicio p2papp) - port de quii-helper
 * quii_helper/protocols/ust/{credentials,key_derivation,key_schedule,crypto_tables}.py.
 *
 * El broker MQTT UST se recibe del cloud (query-hlrv2, servicio p2papp) como URL con
 * params username=/password= cifrados en base64 (AES-256-CBC, IV ASCII "0"*16) y srcid
 * (semilla de derivacion). La clave AES se deriva del srcid con 3 tablas de 4096B
 * extraidas de libqv-p2p-v2.so (shipeadas como assets p2p_table_{t1,t2,t3}.bin) y una
 * expansion AES-256 con SBOX CUSTOM derivada de las tablas.
 */
object UstCrypto {
    const val UST_AES_IV: String = "0000000000000000" // ASCII '0' x16
    private const val TABLE_SIZE = 4096

    /** Carga las 3 tablas cripto desde assets. */
    fun loadTables(t1: ByteArray, t2: ByteArray, t3: ByteArray): Tables {
        require(t1.size == TABLE_SIZE && t2.size == TABLE_SIZE && t3.size == TABLE_SIZE) {
            "ust crypto tables must be $TABLE_SIZE bytes"
        }
        return Tables(t1, t2, t3)
    }

    class Tables(val data1: ByteArray, val data2: ByteArray, val data3: ByteArray)

    /** append_zero_to_32: UTF-8 truncado a 32 o padding con '0' ASCII. */
    fun appendZeroTo32(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        val out = ByteArray(32) { '0'.code.toByte() }
        raw.copyInto(out, 0, 0, minOf(32, raw.size))
        return out
    }

    /**
     * generate_ust_seed_and_sbox(srcid): seed[0]=data2[data1[1]];
     * seed[i]=data2[((seed[i-1] % 0x10)*seed[i-1]) & 0xFFF]; seed[i]+=srcid_padded[i];
     * sbox[0]=data3[seed[0]]; sbox[i]=data3[((sbox[i-1] % 0x10)*sbox[i-1]) & 0xFFF].
     */
    fun generateSeedAndSbox(srcid: String, tables: Tables): Pair<ByteArray, ByteArray> {
        val seed = ByteArray(32)
        val srcidPadded = appendZeroTo32(srcid)
        seed[0] = tables.data2[tables.data1[1].toInt() and 0xFF]
        for (i in 1 until 32) {
            val prev = seed[i - 1].toInt() and 0xFF
            seed[i] = tables.data2[(((prev % 0x10) * prev) and 0xFFF)].toByte()
        }
        for (i in 0 until 32) {
            seed[i] = (((seed[i].toInt() and 0xFF) + (srcidPadded[i].toInt() and 0xFF)) and 0xFF).toByte()
        }
        val sbox = ByteArray(256)
        sbox[0] = tables.data3[seed[0].toInt() and 0xFF]
        for (i in 1 until 256) {
            val prev = sbox[i - 1].toInt() and 0xFF
            sbox[i] = tables.data3[(((prev % 0x10) * prev) and 0xFFF)].toByte()
        }
        return seed to sbox
    }

    private fun rotWord(word: IntArray): IntArray = intArrayOf(word[1], word[2], word[3], word[0])

    private fun subWord(word: IntArray, sbox: ByteArray): IntArray =
        IntArray(4) { sbox[word[it]].toInt() and 0xFF }

    private fun gmult(a: Int, b: Int): Int {
        var x = a
        var y = b
        var p = 0
        for (i in 0 until 8) {
            if (y and 1 != 0) p = p xor x
            val hi = x and 0x80
            x = (x shl 1) and 0xFF
            if (hi != 0) x = x xor 0x1B
            y = (y shr 1) and 0xFF
        }
        return p
    }

    private fun rconWord(i: Int): IntArray {
        if (i == 0) return intArrayOf(0, 0, 0, 0)
        var value = 1
        repeat(i - 1) { value = gmult(value, 2) }
        return intArrayOf(value, 0, 0, 0)
    }

    /**
     * expand_ust_aes_key: key schedule AES-256 (nk=8, nr=14) con sbox custom.
     * Devuelve expanded[8:40] (32 bytes de clave).
     */
    fun expandAesKey(seed: ByteArray, sbox: ByteArray): ByteArray {
        val words = Array(8) { w ->
            intArrayOf(seed[w * 4].toInt() and 0xFF, seed[w * 4 + 1].toInt() and 0xFF,
                seed[w * 4 + 2].toInt() and 0xFF, seed[w * 4 + 3].toInt() and 0xFF)
        }.toMutableList()
        for (i in 8 until 60) {
            var temp = words[i - 1].copyOf()
            if (i % 8 == 0) {
                temp = subWord(rotWord(temp), sbox)
                val rcon = rconWord(i / 8)
                for (k in 0 until 4) temp[k] = temp[k] xor rcon[k]
            } else if (i % 8 == 4) {
                temp = subWord(temp, sbox)
            }
            val prev = words[i - 8]
            words.add(IntArray(4) { k -> prev[k] xor temp[k] })
        }
        val flat = ByteArray(32)
        for (w in 2 until 10) {
            for (k in 0 until 4) flat[(w - 2) * 4 + k] = words[w][k].toByte()
        }
        return flat
    }

    fun deriveAesKey(srcid: String, tables: Tables): ByteArray {
        val (seed, sbox) = generateSeedAndSbox(srcid, tables)
        return expandAesKey(seed, sbox)
    }

    /** decode_ust_ciphertext: base64 -> AES-CBC decrypt IV ceros -> rstrip \0 -> utf8. */
    fun decodeCiphertext(ciphertextB64: String, srcid: String, tables: Tables): String {
        val key = deriveAesKey(srcid, tables)
        val data = java.util.Base64.getDecoder().decode(ciphertextB64)
        require(data.size % 16 == 0) { "ust ciphertext not block aligned" }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(UST_AES_IV.toByteArray(Charsets.US_ASCII)))
        val plain = cipher.doFinal(data)
        return plain.toString(Charsets.UTF_8).trimEnd('\u0000')
    }

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
