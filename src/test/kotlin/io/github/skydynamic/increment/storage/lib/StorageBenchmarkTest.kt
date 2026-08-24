package io.github.skydynamic.increment.storage.lib

import io.github.skydynamic.increment.storage.lib.support.StorageHarness
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Random

@Tag("benchmark")
@EnabledIfSystemProperty(named = "incremental.runBenchmark", matches = "true")
class StorageBenchmarkTest {
    private var harness: StorageHarness? = null

    @AfterEach
    fun tearDown() {
        harness?.close()
        harness = null
    }

    @Test
    fun `measure first and add backup wall time`(@TempDir root: Path) {
        val h = StorageHarness(root)
        harness = h

        var sourceBytes = 0L
        val rng = Random(1)
        repeat(32) { i ->
            val bytes = ByteArray(1024 * 1024)
            rng.nextBytes(bytes)
            h.file("region/r.0.$i.mca", bytes)
            sourceBytes += bytes.size
        }
        repeat(200) { i ->
            val bytes = ByteArray(4 * 1024)
            rng.nextBytes(bytes)
            h.file("data/d$i.dat", bytes)
            sourceBytes += bytes.size
        }
        repeat(800) { i ->
            val bytes = ByteArray(256)
            rng.nextBytes(bytes)
            h.file("datapacks/pkg/data/f$i.json", bytes)
            sourceBytes += bytes.size
        }

        val firstNs = timed {
            h.manager.incrementalStorage("b1", "first", h.sourceDir)
        }
        val addUnchangedNs = timed {
            h.manager.incrementalStorage("b2", "unchanged", h.sourceDir)
        }

        fun mutateRegion(name: String) {
            val file = h.sourceDir.resolve(name)
            val bytes = file.readBytes()
            rng.nextBytes(bytes)
            file.writeBytes(bytes)
        }
        mutateRegion("region/r.0.0.mca")
        mutateRegion("region/r.0.1.mca")
        repeat(50) { i ->
            h.file("data/new$i.dat", ByteArray(512).also { rng.nextBytes(it) })
        }

        val addChangedNs = timed {
            h.manager.incrementalStorage("b3", "changed", h.sourceDir)
        }

        println("firstBackup_ms=${firstNs / 1_000_000}")
        println("addBackupUnchanged_ms=${addUnchangedNs / 1_000_000}")
        println("addBackupChanged_ms=${addChangedNs / 1_000_000}")
        println("sourceBytes=$sourceBytes")
    }

    private fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }
}
