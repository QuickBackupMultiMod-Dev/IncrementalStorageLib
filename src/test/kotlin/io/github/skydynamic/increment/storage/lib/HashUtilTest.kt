package io.github.skydynamic.increment.storage.lib

import io.github.skydynamic.increment.storage.lib.utils.HashUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertTrue

class HashUtilTest {
    @Test
    fun `same file content yields identical md5`() {
        val file = File.createTempFile("hash", ".bin")
        file.writeBytes("hello-incremental".toByteArray())
        try {
            assertEquals(HashUtil.getFileHash(file), HashUtil.getFileHash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `empty file has stable 32-char hex hash`(@TempDir dir: Path) {
        val file = dir.resolve("empty").toFile()
        file.writeBytes(ByteArray(0))
        val hash = HashUtil.getFileHash(file)
        assertEquals(32, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{32}")))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hash)
    }

    @Test
    fun `different content yields different hash`(@TempDir dir: Path) {
        val a = dir.resolve("a").toFile().apply { writeText("a") }
        val b = dir.resolve("b").toFile().apply { writeText("b") }
        assertNotEquals(HashUtil.getFileHash(a), HashUtil.getFileHash(b))
    }

    @Test
    fun `known abc content uses md5`() {
        val file = File.createTempFile("abc", ".txt")
        file.writeText("abc")
        try {
            assertEquals("900150983cd24fb0d6963f7d28e17f72", HashUtil.getFileHash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing file throws`(@TempDir dir: Path) {
        val missing = dir.resolve("no-such-file").toFile()
        assertThrows(Exception::class.java) {
            HashUtil.getFileHash(missing)
        }
    }
}
