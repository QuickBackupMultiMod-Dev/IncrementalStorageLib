package io.github.skydynamic.increment.storage.lib

import io.github.skydynamic.increment.storage.lib.exception.IncrementalStorageException
import io.github.skydynamic.increment.storage.lib.support.StorageHarness
import io.github.skydynamic.increment.storage.lib.utils.HashUtil
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.NameFileFilter
import org.apache.commons.io.filefilter.NotFileFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class StorageManagerTest {
    private var harness: StorageHarness? = null

    @AfterEach
    fun tearDown() {
        harness?.close()
        harness = null
    }

    private fun harness(root: Path): StorageHarness {
        val h = StorageHarness(root)
        harness = h
        return h
    }

    @Test
    fun `incrementalStorage copies file into content addressed blob`(@TempDir root: Path) {
        val h = harness(root)
        val payload = "world-region".toByteArray()
        h.file("region/r.0.0.mca", payload)

        h.manager.incrementalStorage("slot1", "desc", h.sourceDir)

        val hash = HashUtil.getFileHash(h.sourceDir.resolve("region/r.0.0.mca"))
        val blob = h.blobFile(hash)
        assertTrue(blob.isFile)
        assertEquals(payload.toList(), blob.readBytes().toList())

        val map = h.database.getFileHashMap("slot1")
        assertEquals(hash, map.keys.single())
        assertEquals(File("region", "r.0.0.mca").path, map.values.single())
    }

    @Test
    fun `second storage with same bytes reuses blob and increments references`(@TempDir root: Path) {
        val h = harness(root)
        val payload = "shared-bytes".toByteArray()
        h.file("a.txt", payload)
        h.manager.incrementalStorage("s1", "d", h.sourceDir)

        val source2 = root.resolve("source2").toFile().apply { mkdirs() }
        File(source2, "b.txt").writeBytes(payload)
        h.manager.incrementalStorage("s2", "d", source2)

        val hash = HashUtil.getFileHash(h.sourceDir.resolve("a.txt"))
        val blobs = h.blogsDir().walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, blobs.size)
        assertEquals(hash, blobs.single().name)

        val counts = h.database.getReferenceCountsForHashes(setOf(hash))
        assertEquals(2L, counts[hash])
    }

    @Test
    fun `deleteStorage removes blob when it is the only reference`(@TempDir root: Path) {
        val h = harness(root)
        h.file("only.txt", "unique-payload".toByteArray())
        h.manager.incrementalStorage("only", "d", h.sourceDir)
        val hash = HashUtil.getFileHash(h.sourceDir.resolve("only.txt"))
        assertTrue(h.blobFile(hash).isFile)

        h.manager.deleteStorage("only")

        assertFalse(h.blobFile(hash).exists())
        assertFalse(h.database.storageExists("only"))
    }

    @Test
    fun `deleteStorage keeps blob when another storage still references it`(@TempDir root: Path) {
        val h = harness(root)
        val payload = "kept".toByteArray()
        h.file("a.txt", payload)
        h.manager.incrementalStorage("s1", "d", h.sourceDir)

        val source2 = root.resolve("source2").toFile().apply { mkdirs() }
        File(source2, "b.txt").writeBytes(payload)
        h.manager.incrementalStorage("s2", "d", source2)

        val hash = HashUtil.getFileHash(h.sourceDir.resolve("a.txt"))
        h.manager.deleteStorage("s1")

        assertTrue(h.blobFile(hash).isFile)
        assertTrue(h.database.storageExists("s2"))
    }

    @Test
    fun `incrementalStorage rejects duplicate name`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "x".toByteArray())
        h.manager.incrementalStorage("dup", "d", h.sourceDir)
        assertThrows(IncrementalStorageException::class.java) {
            h.manager.incrementalStorage("dup", "d", h.sourceDir)
        }
    }

    @Test
    fun `incrementalStorageTemp uses hash key for existing blob and temp key otherwise`(@TempDir root: Path) {
        val h = harness(root)
        h.file("old.txt", "already-backed-up".toByteArray())
        h.manager.incrementalStorage("base", "d", h.sourceDir)

        val existingHash = HashUtil.getFileHash(h.sourceDir.resolve("old.txt"))
        h.file("new.txt", "not-yet-in-store".toByteArray())

        val acceptAll = object : IOFileFilter {
            override fun accept(file: File): Boolean = true
            override fun accept(dir: File, name: String): Boolean = true
        }
        h.manager.incrementalStorageTemp(h.sourceDir, acceptAll, acceptAll)

        val map = h.database.getFileHashMap("restore_temp")
        assertEquals(File("old.txt").path, map[existingHash])
        val tempEntries = map.filterKeys { it.startsWith("blog_temp_") }
        assertEquals(1, tempEntries.size)
        val tempName = tempEntries.keys.single()
        assertEquals(File("new.txt").path, tempEntries.values.single())
        assertTrue(h.storageDir.resolve("blogs_temp").resolve(tempName).isFile)
    }

    @Test
    fun `file and directory filters skip ignored paths`(@TempDir root: Path) {
        val h = harness(root)
        h.file("keep/a.txt", "keep".toByteArray())
        h.file("skip_dir/b.txt", "skip".toByteArray())
        h.file("keep/ignore.dat", "ignore".toByteArray())

        val dirFilter = NotFileFilter(NameFileFilter("skip_dir"))
        val fileFilter = NotFileFilter(NameFileFilter("ignore.dat"))
        h.manager.incrementalStorage("filtered", "d", h.sourceDir, fileFilter, dirFilter)

        val map = h.database.getFileHashMap("filtered")
        assertEquals(1, map.size)
        assertEquals(File("keep", "a.txt").path, map.values.single())
    }

    @Test
    fun `deleteStorage rejects null name`(@TempDir root: Path) {
        val h = harness(root)
        assertThrows(IllegalArgumentException::class.java) {
            h.manager.deleteStorage(null)
        }
    }

    @Test
    fun `two identical files in one backup share a blob`(@TempDir root: Path) {
        val h = harness(root)
        val payload = "dup-content".toByteArray()
        h.file("one.txt", payload)
        h.file("two.txt", payload)
        h.manager.incrementalStorage("dup", "d", h.sourceDir)

        val blobs = h.blogsDir().walkTopDown().filter { it.isFile && !it.name.startsWith(".part-") }.toList()
        assertEquals(1, blobs.size)
        val map = h.database.getFileHashMap("dup")
        assertEquals(1, map.size)
        assertEquals(payload.toList(), blobs.single().readBytes().toList())
    }

    @Test
    fun `two hundred unique files are all stored`(@TempDir root: Path) {
        val h = harness(root)
        repeat(200) { i ->
            h.file("f$i.txt", "payload-$i".toByteArray())
        }
        h.manager.incrementalStorage("many", "d", h.sourceDir)
        val map = h.database.getFileHashMap("many")
        assertEquals(200, map.size)
        val blobs = h.blogsDir().walkTopDown().filter { it.isFile && !it.name.startsWith(".part-") }.toList()
        assertEquals(200, blobs.size)
        map.forEach { (hash, relative) ->
            assertEquals(
                h.sourceDir.resolve(relative).readBytes().toList(),
                h.blobFile(hash).readBytes().toList()
            )
        }
    }

    @Test
    fun `second backup of unchanged tree does not rewrite blobs`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "stable".toByteArray())
        h.manager.incrementalStorage("first", "d", h.sourceDir)
        val stamps = h.blogsDir().walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".part-") }
            .associate { it.absolutePath to it.lastModified() }

        Thread.sleep(20)
        h.manager.incrementalStorage("second", "d", h.sourceDir)

        stamps.forEach { (path, modified) ->
            assertEquals(modified, File(path).lastModified())
        }
    }

    @Test
    fun `deleteStorage drops reference rows so the last copy can be collected`(@TempDir root: Path) {
        val h = harness(root)
        val payload = "gc-me".toByteArray()
        h.file("a.txt", payload)
        h.manager.incrementalStorage("s1", "d", h.sourceDir)

        val source2 = root.resolve("source2").toFile().apply { mkdirs() }
        File(source2, "b.txt").writeBytes(payload)
        h.manager.incrementalStorage("s2", "d", source2)

        val hash = HashUtil.getFileHash(h.sourceDir.resolve("a.txt"))
        assertEquals(2L, h.database.getReferenceCountsForHashes(setOf(hash))[hash])

        h.manager.deleteStorage("s1")
        assertTrue(h.blobFile(hash).isFile)
        assertEquals(1L, h.database.getReferenceCountsForHashes(setOf(hash))[hash])

        h.manager.deleteStorage("s2")
        assertFalse(h.blobFile(hash).exists())
        assertEquals(0L, h.database.getReferenceCountsForHashes(setOf(hash))[hash])
    }

    @Test
    fun `fullStorage copies each source file once`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.file("dir/b.txt", "B".toByteArray())

        h.manager.fullStorage("full1", "d", h.sourceDir)

        val fullRoot = h.storageDir.resolve("full")
        val copies = fullRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        assertEquals(1, copies.size)
        assertEquals("full1", copies.single().name)
        assertTrue(copies.single().resolve("a.txt").isFile)
        assertTrue(copies.single().resolve("dir").resolve("b.txt").isFile)
        assertFalse(copies.single().resolve("a.txt").resolve("a.txt").exists())
        assertEquals(false, h.database.getStorageInfoWithName("full1")?.useIncrementalStorage)
        assertEquals("full1", h.database.getStorageInfoWithName("full1")?.name)
    }

    @Test
    fun `fullStorage skips session lock files`(@TempDir root: Path) {
        val h = harness(root)
        h.file("level.dat", "L".toByteArray())
        h.file("session.lock", "locked".toByteArray())

        h.manager.fullStorage("full1", "d", h.sourceDir)

        val copy = h.storageDir.resolve("full").resolve("full1")
        assertTrue(copy.resolve("level.dat").isFile)
        assertFalse(copy.resolve("session.lock").exists())
    }

    @Test
    fun `fullStorage uses the caller name as the directory name`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.manager.fullStorage("full1", "d", h.sourceDir)

        val copies = h.storageDir.resolve("full").listFiles()?.filter { it.isDirectory } ?: emptyList()
        assertEquals(listOf("full1"), copies.map { it.name })
        assertEquals("full1", h.database.getStorageInfoWithName("full1")?.name)
    }

    @Test
    fun `fullStorage rejects a duplicate name`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.manager.fullStorage("full1", "d", h.sourceDir)
        assertThrows(IncrementalStorageException::class.java) {
            h.manager.fullStorage("full1", "d", h.sourceDir)
        }
        h.manager.fullStorage("full2", "d", h.sourceDir)
        val copies = h.storageDir.resolve("full").listFiles()?.filter { it.isDirectory } ?: emptyList()
        assertEquals(setOf("full1", "full2"), copies.map { it.name }.toSet())
    }
}
