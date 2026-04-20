package com.yutori.ai

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 of a file, as a lowercase hex string. Streaming — never
 * loads the entire model (~2.58 GB) into memory. Pure JVM; safe to
 * call from a unit test against a real temp file.
 */
internal object ModelHashing {

    private const val BUFFER_BYTES = 64 * 1024

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
