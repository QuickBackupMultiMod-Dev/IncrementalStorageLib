package io.github.skydynamic.increment.storage.lib.utils

import io.github.skydynamic.increment.storage.lib.database.Database
import io.github.skydynamic.increment.storage.lib.database.DatabaseTables
import io.github.skydynamic.increment.storage.lib.exception.IncrementalStorageException
import io.github.skydynamic.increment.storage.lib.manager.IConfig
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.io.filefilter.IOFileFilter
import java.io.File

@Suppress("unused")
class StorageManager(private val database: Database, private val config: IConfig) {
    private fun getExist(storageName: String) = database.storageExists(storageName)

    private fun getAllFiles(folder: File, fileFilter: IOFileFilter): List<File> {
        val files = mutableListOf<File>()
        folder.listFiles()?.forEach { file ->
            if (file.isFile) {
                files.add(file)
            } else if (file.isDirectory) {
                if (fileFilter.accept(file)) {
                    files.addAll(getAllFiles(file, fileFilter))
                }
            }
        }
        return files
    }

    private fun processFiles(files: List<File>, sourcePath: File) : Map<String, String> {
        val blogsPath = File(config.getStoragePath()).resolve("blogs")
        if (!blogsPath.exists()) {
            blogsPath.mkdirs()
        }

        val fileHashMap: MutableMap<String, String> = mutableMapOf()
        files.forEach { file ->
            if (file.exists()) {
                val fileHash = HashUtil.getFileHash(file)
                val hashStart = fileHash.substring(0, 2)
                val filePath = blogsPath.resolve(hashStart).resolve(fileHash)
                if (blogsPath.resolve(hashStart).exists()) {
                    if (!filePath.exists()) {
                        FileUtils.copyFile(file, filePath)
                    }
                } else {
                    blogsPath.resolve(hashStart).mkdirs()
                    FileUtils.copyFile(file, filePath)
                }
                fileHashMap[fileHash] = file.relativeTo(sourcePath).path
            }
        }
        return fileHashMap
    }

    private fun processTempFiles(files: List<File>, sourcePath: File) : Map<String, String> {
        val blogsPath = File(config.getStoragePath()).resolve("blogs")
        val tmpBlogsPath = File(config.getStoragePath()).resolve("blogs_temp")

        if (!blogsPath.exists()) {
            blogsPath.mkdirs()
        }
        if (!tmpBlogsPath.exists()) {
            tmpBlogsPath.mkdirs()
        }

        val fileHashMap: MutableMap<String, String> = mutableMapOf()
        files.forEach { file ->
            val fileHash = HashUtil.getFileHash(file)
            val hashStart = fileHash.substring(0, 2)
            if (blogsPath.resolve(hashStart).resolve(fileHash).exists()) {
                fileHashMap[fileHash] = file.relativeTo(sourcePath).path
            } else {
                val tmpHashFile = tmpBlogsPath.resolve("blog_temp_$fileHash.tmp")
                fileHashMap[tmpHashFile.name] = file.relativeTo(sourcePath).path
                FileUtils.copyFile(file, tmpHashFile)
            }
        }

        return fileHashMap
    }

    fun deleteStorage(name: String?) {
        if (name == null) throw IllegalArgumentException("Backup name cannot be null")

        val fileHashes = database.getFileHashMap(name)
        val hashRefCount = mutableMapOf<String, Int>()
        fileHashes.forEach { hash ->
            val count = database.getReferenceCountForHash(hash.key)
            hashRefCount[hash.key] = count.toInt()
        }

        val deletableHashes = hashRefCount.filter { it.value <= 1 }.keys
        val blogsPath = File(config.getStoragePath()).resolve("blogs")
        deletableHashes.forEach { hash ->
            val hashStart = hash.substring(0, 2)
            val filePath = blogsPath.resolve(hashStart).resolve(hash)
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
}