package io.github.skydynamic.increment.storage.lib.utils

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object HashUtil {
    private val HASH_BLOCK_SIZE = System.getProperty(
        "incremental.hashBlockSize",
        (1024 * 1024 * 5).toString()
    ).toInt()

    fun getFileHash(file: File) : String {
        val inputStream = file.inputStream()
        val hash = getHash(inputStream)
        inputStream.close()
        return hash
    }

    private fun getHash(inputStream: InputStream) : String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(HASH_BLOCK_SIZE)
        var len = inputStream.read(buffer)
        while (len != -1) {
            digest.update(buffer, 0, len)
            len = inputStream.read(buffer)
        }
        val value = digest.digest()
        return value.toHexString()
    }
}