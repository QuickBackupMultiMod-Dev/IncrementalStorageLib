package io.github.skydynamic.increment.storage.lib.database

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

enum class DatabaseTables(val cls: Class<out BaseTable>) {
    STORAGE_INFO(StorageInfoTable::class.java),
    INDEX_FILE(IndexFileTable::class.java),
    FILE_HASH(FileHashTable::class.java)
}

data class StorageInfo(
    val name: String,
    val desc: String,
    val timestamp: Long,
    val useIncrementalStorage: Boolean,
    val indexStorage: List<String>
)

data class IndexFile(
    val name: String,
    val indexFileMap: Map<String, String>
)

open class BaseTable(name: String): Table(name) {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val collectionUuid = uuid("collection_uuid")
}

object StorageInfoTable: BaseTable("storage_info") {
    val desc = varchar("desc", 255)
    val timestamp = long("timestamp")
    val useIncrementalStorage = bool("use_incremental_storage")
    val indexStorage = array<String>("index_storage", 255)

    override val primaryKey = PrimaryKey(id, name = "ISL_SI_ID")

    @JvmStatic
    fun isUseIncrementalStorage(result: ResultRow): Boolean {
        return result[useIncrementalStorage]
    }

    @JvmStatic
    fun getStorageInfo(result: ResultRow): StorageInfo {
        return StorageInfo(
            name = result[name],
            desc = result[desc],
            timestamp = result[timestamp],
            useIncrementalStorage = result[useIncrementalStorage],
            indexStorage = result[indexStorage].toList()
        )
    }
}

object IndexFileTable: BaseTable("index_file") {
    val indexFileMap = text("index_file_map")

    override val primaryKey = PrimaryKey(id, name = "ISL_IF_ID")

    @JvmStatic
    fun getIndexFile(result: ResultRow): IndexFile {
        return IndexFile(
            name = result[name],
            indexFileMap = result[indexFileMap].toMap()
        )
    }
}

object FileHashTable: BaseTable("file_hash") {
    val fileHashMap = varchar("hash", 255)

    override val primaryKey = PrimaryKey(id, name = "ISL_FH_ID")
}