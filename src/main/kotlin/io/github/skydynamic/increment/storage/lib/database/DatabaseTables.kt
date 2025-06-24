package io.github.skydynamic.increment.storage.lib.database

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

enum class DatabaseTables(val cls: Class<out BaseTable>) {
    STORAGE_INFO(StorageInfoTable::class.java),
    FILE_HASH(FileHashTable::class.java)
}

data class StorageInfo(
    val name: String,
    val desc: String,
    val timestamp: Long,
    val useIncrementalStorage: Boolean
)

open class BaseTable(name: String): Table(name) {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 65535)
    val collectionUuid = uuid("collection_uuid")
}

object StorageInfoTable: BaseTable("storage_info") {
    val desc = varchar("desc", 65535)
    val timestamp = long("timestamp")
    val useIncrementalStorage = bool("use_incremental_storage")

    override val primaryKey = PrimaryKey(id, name = "ISL_SI_ID")

    @JvmStatic
    fun isUseIncrementalStorage(result: StorageInfo): Boolean {
        return result.useIncrementalStorage
    }

    @JvmStatic
    fun getStorageInfo(result: ResultRow): StorageInfo {
        return StorageInfo(
            name = result[name],
            desc = result[desc],
            timestamp = result[timestamp],
            useIncrementalStorage = result[useIncrementalStorage]
        )
    }
}

object FileHashTable: BaseTable("file_hash") {
    val fileHashMap = text("file_hash_map")

    override val primaryKey = PrimaryKey(id, name = "ISL_FH_ID")
}

object FileHashReferenceTable : BaseTable("file_hash_reference") {
    val fileHash = varchar("file_hash", 255)

    override val primaryKey = PrimaryKey(id, name = "ISL_FH_REF_ID")
}