package io.github.skydynamic.increment.storage.lib.support

import io.github.skydynamic.increment.storage.lib.database.Database
import io.github.skydynamic.increment.storage.lib.manager.IConfig
import io.github.skydynamic.increment.storage.lib.manager.IDatabaseManager
import io.github.skydynamic.increment.storage.lib.utils.StorageManager
import java.io.File
import java.nio.file.Path
import java.util.UUID

class TestConfig(private val storagePath: String) : IConfig {
    override fun getStoragePath(): String = storagePath
}

class TestDatabaseManager(
    private val fileName: String,
    private val databasePath: String,
    private val collectionUuid: UUID
) : IDatabaseManager {
    override fun setFileName(name: String?) {}
    override fun setDatabasePath(path: String?) {}
    override fun setCollectionUuid(uuid: UUID?) {}
    override fun getFileName(): String = fileName
    override fun getDatabasePath(): String = databasePath
    override fun getCollectionUuid(): UUID = collectionUuid
}

class StorageHarness(root: Path) {
    val storageDir: File = root.resolve("storage").toFile().apply { mkdirs() }
    val dbDir: File = root.resolve("db").toFile().apply { mkdirs() }
    val sourceDir: File = root.resolve("source").toFile().apply { mkdirs() }
    val uuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val database = Database(TestDatabaseManager("dataBase", dbDir.absolutePath, uuid))
    val manager = StorageManager(database, TestConfig(storageDir.absolutePath))

    fun file(relative: String, bytes: ByteArray): File {
        val f = sourceDir.resolve(relative)
        f.parentFile.mkdirs()
        f.writeBytes(bytes)
        return f
    }

    fun blogsDir(): File = storageDir.resolve("blogs")

    fun blobFile(hash: String): File = blogsDir().resolve(hash.take(2)).resolve(hash)

    fun close() {
        database.closeDatabase()
    }
}
