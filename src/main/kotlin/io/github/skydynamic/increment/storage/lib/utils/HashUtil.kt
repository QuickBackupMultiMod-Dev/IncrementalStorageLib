package io.github.skydynamic.increment.storage.lib.utils

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

object HashUtil {
    private val HASH_BLOCK_SIZE = System.getProperty(
        "incremental.hashBlockSize",
        (64 * 1024).toString()
    ).toInt().coerceAtLeast(1024)

    private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }
    private val buffer = ThreadLocal.withInitial { ByteArray(HASH_BLOCK_SIZE) }
    private val nioBuffer = ThreadLocal.withInitial { ByteBuffer.wrap(buffer.get()) }

    fun getFileHash(file: File): String = hashWithOptionalSink(file, null)

    internal fun ingestToStream(source: File, output: OutputStream): String =
        hashWithOptionalSink(source, output)

    internal fun hashBytes(bytes: ByteArray): String {
        val md = digest.get()
        md.reset()
        md.update(bytes)
        return md.digest().toHexString()
    }

    private fun hashWithOptionalSink(file: File, output: OutputStream?): String {
        val md = digest.get()
        md.reset()
        val raw = buffer.get()
        val bb = nioBuffer.get()
        FileInputStream(file).channel.use { channel ->
            while (true) {
                bb.clear()
                val n = channel.read(bb)
                if (n < 0) {
                    break
                }
                md.update(raw, 0, n)
                output?.write(raw, 0, n)
            }
        }
        return md.digest().toHexString()
    }
}
