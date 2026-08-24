package io.github.skydynamic.increment.storage.lib.utils

import io.github.skydynamic.increment.storage.lib.database.Database
import io.github.skydynamic.increment.storage.lib.database.DatabaseTables
import io.github.skydynamic.increment.storage.lib.exception.IncrementalStorageException
import io.github.skydynamic.increment.storage.lib.logging.LogUtil
import io.github.skydynamic.increment.storage.lib.manager.IConfig
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.io.filefilter.IOFileFilter
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Suppress("unused")
class StorageManager(private val database: Database, private val config: IConfig) {
    private fun getExist(storageName: String) = database.storageExists(storageName)

    private fun getAllFiles(folder: File, fileFilter: IOFileFilter): List<File> {
        val files = mutableListOf<File>()
        if (!folder.exists()) {
            return files
        }
        try {
            Files.walkFileTree(folder.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir != folder.toPath() && !fileFilter.accept(dir.toFile())) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) {
                        files.add(file.toFile())
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    LogUtil.logger.error("Cannot access file: ${file.toAbsolutePath()}", exc)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: SecurityException) {
            LogUtil.logger.error("Cannot access folder: ${folder.absolutePath}", e)
        }
        return files
    }

    private fun processFiles(files: List<File>, sourcePath: File): Map<String, String> {
        val blogsPath = File(config.getStoragePath()).resolve("blogs")
        prepareBlobDirs(blogsPath)
        val firstIngest = !hasAnyBlobFile(blogsPath)
        val fileHashMap = ConcurrentHashMap<String, String>()
        forEachFileParallel(files) { file ->
            if (!file.exists()) {
                return@forEachFileParallel
            }
            val hash = if (firstIngest) {
                ingestNewFile(file, blogsPath)
            } else {
                ingestExistingStore(file, blogsPath)
            }
            fileHashMap[hash] = file.relativeTo(sourcePath).path
        }
        return fileHashMap
    }

    private fun processTempFiles(files: List<File>, sourcePath: File): Map<String, String> {
        val blogsPath = File(config.getStoragePath()).resolve("blogs")
        val tmpBlogsPath = File(config.getStoragePath()).resolve("blogs_temp")
        prepareBlobDirs(blogsPath)
        tmpBlogsPath.mkdirs()

        val fileHashMap = ConcurrentHashMap<String, String>()
        forEachFileParallel(files) { file ->
            try {
                val relative = file.relativeTo(sourcePath).path
                val size = file.length()
                if (size in 0..SMALL_INGEST_BYTES) {
                    val bytes = file.readBytes()
                    val hash = HashUtil.hashBytes(bytes)
                    val blob = blogsPath.resolve(hash.take(2)).resolve(hash)
                    if (blob.exists()) {
                        fileHashMap[hash] = relative
                    } else {
                        val tmpHashFile = tmpBlogsPath.resolve("blog_temp_$hash.tmp")
                        Files.write(tmpHashFile.toPath(), bytes)
                        fileHashMap[tmpHashFile.name] = relative
                    }
                } else {
                    val hash = HashUtil.getFileHash(file)
                    val blob = blogsPath.resolve(hash.take(2)).resolve(hash)
                    if (blob.exists()) {
                        fileHashMap[hash] = relative
                    } else {
                        val tmpHashFile = tmpBlogsPath.resolve("blog_temp_$hash.tmp")
                        tmpHashFile.outputStream().use { out -> HashUtil.ingestToStream(file, out) }
                        fileHashMap[tmpHashFile.name] = relative
                    }
                }
            } catch (e: Exception) {
                LogUtil.logger.error("Failed to process file: ${file.absolutePath}, error: ${e.message}")
            }
        }
        return fileHashMap
    }

    fun deleteStorage(name: String?) {
        if (name == null) throw IllegalArgumentException("Backup name cannot be null")

        val fileHashes = database.getFileHashMap(name)
        val referenceCounts = database.getReferenceCountsForHashes(fileHashes.keys.toSet())
        val deletableHashes = fileHashes.keys.filter { (referenceCounts[it] ?: 0L) <= 1L }
        val blogsPath = File(config.getStoragePath()).resolve("blogs")
        forEachFileParallel(deletableHashes.map { hash ->
            blogsPath.resolve(hash.take(2)).resolve(hash)
        }) { filePath ->
            if (filePath.exists()) {
                filePath.delete()
                val hashDir = filePath.parentFile
                if (hashDir.list()?.isEmpty() == true) {
                    hashDir.delete()
                }
            }
        }

        for (type in DatabaseTables.entries) {
            database.deleteTableValue(name, type)
        }
    }

    fun deleteTempStorage() {
        val blogsPath = File(config.getStoragePath()).resolve("blogs_temp")
        blogsPath.deleteRecursively()

        for (type in DatabaseTables.entries) {
            database.deleteTableValue("restore_temp", type)
        }
    }

    fun incrementalStorage(storageName: String, desc: String, sourcePath: File) {
        val filter = FileFilterUtils.trueFileFilter()
        incrementalStorage(storageName, desc, sourcePath, filter, filter)
    }

    fun incrementalStorage(
        storageName: String,
        desc: String,
        sourcePath: File,
        fileFilter: IOFileFilter,
        dirFilter: IOFileFilter
    ) {
        if (sourcePath.isFile) {
            throw IncrementalStorageException("Source path must be a directory")
        }

        if (getExist(storageName)) {
            throw IncrementalStorageException("Storage $storageName already exists")
        }

        val sourceFiles = getAllFiles(sourcePath, dirFilter).filter { fileFilter.accept(it) }
        val fileHashMap = processFiles(sourceFiles, sourcePath)
        database.insertFileHash(storageName, fileHashMap)

        database.insertStorageInfo(
            storageName,
            desc,
            System.currentTimeMillis(),
            true
        )
    }

    fun incrementalStorageTemp(sourcePath: File, fileFilter: IOFileFilter, dirFilter: IOFileFilter) {
        if (sourcePath.isFile) {
            throw IncrementalStorageException("Source path must be a directory")
        }

        if (getExist("restore_temp")) {
            deleteStorage("restore_temp")
        }

        val sourceFiles = getAllFiles(sourcePath, dirFilter).filter { fileFilter.accept(it) }
        val fileHashMap = processTempFiles(sourceFiles, sourcePath)
        database.insertFileHash("restore_temp", fileHashMap)
        database.insertStorageInfo(
            "restore_temp",
            "a5ff1c641758cc02744172a50e577bbe06c2a1c5",
            System.currentTimeMillis(),
            true
        )
    }

    private fun copyDirectory(source: File, name: String) {
        val target = File(config.getStoragePath()).resolve("full").resolve(name)
        if (target.exists()) {
            target.deleteRecursively()
        }
        target.mkdirs()
        source.walkTopDown().forEach { file ->
            if (file == source) {
                return@forEach
            }
            val dest = target.resolve(file.relativeTo(source).path)
            if (file.isDirectory) {
                dest.mkdirs()
            } else if (file.isFile) {
                // Minecraft holds an exclusive lock on session.lock while the world is open.
                // Copying it fails on Windows and is not needed to restore the save.
                if (file.name == "session.lock") {
                    return@forEach
                }
                dest.parentFile.mkdirs()
                Files.copy(
                    file.toPath(),
                    dest.toPath(),
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }

    fun fullStorage(storageName: String, desc: String, sourcePath: File) {
        if (sourcePath.isFile) {
            throw IncrementalStorageException("Source path must be a directory")
        }

        if (getExist(storageName)) {
            throw IncrementalStorageException("Storage $storageName already exists")
        }

        val timestamp = System.currentTimeMillis()
        copyDirectory(sourcePath, storageName)

        database.insertStorageInfo(
            storageName,
            desc,
            timestamp,
            false
        )
    }

    private fun forEachFileParallel(files: List<File>, action: (File) -> Unit) {
        if (files.isEmpty()) {
            return
        }
        if (files.size == 1 || parallelism == 1) {
            files.forEach(action)
            return
        }
        val pool = Executors.newFixedThreadPool(parallelism.coerceAtMost(files.size))
        try {
            val futures = files.map { file -> pool.submit { action(file) } }
            futures.forEach { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pool.shutdown()
        }
    }

    private fun prepareBlobDirs(blogsPath: File) {
        blogsPath.mkdirs()
        var i = 0
        while (i < 256) {
            File(blogsPath, "%02x".format(i)).mkdirs()
            i++
        }
    }

    private fun hasAnyBlobFile(blogsPath: File): Boolean {
        val entries = blogsPath.listFiles() ?: return false
        for (entry in entries) {
            if (entry.isFile && !entry.name.startsWith(".part-")) {
                return true
            }
            if (entry.isDirectory) {
                val children = entry.list()
                if (children != null && children.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun ingestNewFile(file: File, blogsPath: File): String {
        val part = File(blogsPath, ".part-${Thread.currentThread().id}-${partSeq.incrementAndGet()}")
        try {
            val hash = part.outputStream().use { out -> HashUtil.ingestToStream(file, out) }
            val dest = blogsPath.resolve(hash.take(2)).resolve(hash)
            if (dest.exists()) {
                part.delete()
                return hash
            }
            try {
                Files.move(part.toPath(), dest.toPath())
            } catch (_: FileAlreadyExistsException) {
                part.delete()
            }
            return hash
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    private fun ingestExistingStore(file: File, blogsPath: File): String {
        val size = file.length()
        if (size in 0..SMALL_INGEST_BYTES) {
            val bytes = file.readBytes()
            val hash = HashUtil.hashBytes(bytes)
            val dest = blogsPath.resolve(hash.take(2)).resolve(hash)
            if (!dest.exists()) {
                Files.write(dest.toPath(), bytes)
            }
            return hash
        }
        val hash = HashUtil.getFileHash(file)
        val dest = blogsPath.resolve(hash.take(2)).resolve(hash)
        if (!dest.exists()) {
            ingestNewFile(file, blogsPath)
        }
        return hash
    }

    private companion object {
        private val parallelism = System.getProperty(
            "incremental.parallelism",
            Runtime.getRuntime().availableProcessors().coerceAtMost(8).toString()
        ).toInt().coerceAtLeast(1)

        private val SMALL_INGEST_BYTES = System.getProperty(
            "incremental.smallIngestBytes",
            (1024 * 1024).toString()
        ).toInt()

        private val partSeq = AtomicLong()
    }
}
