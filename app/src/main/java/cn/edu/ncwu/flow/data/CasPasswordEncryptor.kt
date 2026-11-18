package cn.edu.ncwu.flow.data

import java.math.BigInteger

internal object CasPasswordEncryptor {
    private const val MODULUS = "008aed7e057fe8f14c73550b0e6467b023616ddc8fa91846d2613cdb7f7621e3cada4cd5d812d627af6b87727ade4e26d26208b7326815941492b2204c3167ab2d53df1e3a2c9153bdb7c8c2e968df97a5e7e01cc410f92c4c2c2fba529b3ee988ebc1fca99ff5119e036d732c368acf8beba01aa2fdafa45b21e4de4928d0d403"
    private val exponent = BigInteger("010001", 16)
    private const val CHUNK_SIZE = 126

    fun encrypt(password: String, modulusHex: String = MODULUS): String {
        val modulus = BigInteger(modulusHex, 16)
        val source = password.map { (it.code and 0xff).toByte() }.toMutableList()
        while (source.size % CHUNK_SIZE != 0) source += 0
        return source.chunked(CHUNK_SIZE).joinToString(" ") { chunk ->
            val littleEndian = chunk.toByteArray()
            val encrypted = BigInteger(1, littleEndian.reversedArray()).modPow(exponent, modulus)
            val hex = encrypted.toString(16)
            hex.padStart(((hex.length + 3) / 4) * 4, '0')
        }
    }
}
