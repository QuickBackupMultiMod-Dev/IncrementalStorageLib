package io.github.skydynamic.increment.storage.lib

import io.github.skydynamic.increment.storage.lib.support.StorageHarness
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DatabaseQueryTest {
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
    fun `storageExists finds inserted names only`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.file("b.txt", "B".toByteArray())
        h.manager.incrementalStorage("A", "da", h.sourceDir)
        h.manager.incrementalStorage("B", "db", h.sourceDir)

        assertTrue(h.database.storageExists("A"))
        assertTrue(h.database.storageExists("B"))
        assertFalse(h.database.storageExists("missing"))
    }

    @Test
    fun `getStorageInfoWithName returns only the requested row`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.manager.incrementalStorage("A", "desc-a", h.sourceDir)
        h.manager.incrementalStorage("B", "desc-b", h.sourceDir)

        val info = h.database.getStorageInfoWithName("B")
        assertEquals("B", info?.name)
        assertEquals("desc-b", info?.desc)
        assertNull(h.database.getStorageInfoWithName("missing"))
    }

    @Test
    fun `getStorageInfoWithNameList does not include other names`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.manager.incrementalStorage("A", "da", h.sourceDir)
        h.manager.incrementalStorage("B", "db", h.sourceDir)

        val list = h.database.getStorageInfoWithNameList(listOf("A"))
        assertEquals(listOf("A"), list.map { it.name })
    }

    @Test
    fun `getAllStorageInfo omits restore_temp sentinel desc`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.manager.incrementalStorage("keep", "real", h.sourceDir)
        val acceptAll = object : org.apache.commons.io.filefilter.IOFileFilter {
            override fun accept(file: java.io.File): Boolean = true
            override fun accept(dir: java.io.File, name: String): Boolean = true
        }
        h.manager.incrementalStorageTemp(h.sourceDir, acceptAll, acceptAll)

        val all = h.database.getAllStorageInfo()
        assertEquals(listOf("keep"), all.map { it.name })
        assertTrue(h.database.storageExists("restore_temp"))
    }

    @Test
    fun `getFileHashMap is scoped to the named storage`(@TempDir root: Path) {
        val h = harness(root)
        h.file("a.txt", "A".toByteArray())
        h.manager.incrementalStorage("A", "da", h.sourceDir)

        val sourceB = root.resolve("sourceB").toFile().apply { mkdirs() }
        java.io.File(sourceB, "b.txt").writeBytes("B".toByteArray())
        h.manager.incrementalStorage("B", "db", sourceB)

        val mapA = h.database.getFileHashMap("A")
        val mapB = h.database.getFileHashMap("B")
        assertEquals(setOf(java.io.File("a.txt").path), mapA.values.toSet())
        assertEquals(setOf(java.io.File("b.txt").path), mapB.values.toSet())
        assertTrue(mapA.keys.none { it in mapB.keys } || mapA.values != mapB.values)
    }

    @Test
    fun `lookups remain correct among fifty storages`(@TempDir root: Path) {
        val h = harness(root)
        repeat(50) { i ->
            val source = root.resolve("src$i").toFile().apply { mkdirs() }
            java.io.File(source, "f.txt").writeBytes("payload-$i".toByteArray())
            h.manager.incrementalStorage("n$i", "d$i", source)
        }
        val info = h.database.getStorageInfoWithName("n49")
        assertEquals("n49", info?.name)
        val map = h.database.getFileHashMap("n49")
        assertEquals(setOf(java.io.File("f.txt").path), map.values.toSet())
        h.database.insertStorageInfo(
            "restore_temp",
            "a5ff1c641758cc02744172a50e577bbe06c2a1c5",
            System.currentTimeMillis(),
            true
        )
        assertTrue(h.database.storageExists("restore_temp"))
        assertTrue(h.database.getAllStorageInfo().none { it.name == "restore_temp" })
    }
}
