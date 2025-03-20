package io.github.skydynamic.increment.storage.lib.database

import com.google.gson.Gson
import io.github.skydynamic.increment.storage.lib.Interface.IDataBaseManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

private val gson = Gson()

fun String.toMap(): Map<String, String> {
    val map = gson.fromJson(this, Map::class.java)
    return if (map is Map<*, *>) map.mapKeys { it.key.toString() }.mapValues { it.value.toString() } else emptyMap()
}

class Database(val databaseManager: IDataBaseManager) {
    private val database: Database = Database.connect(
        url = "jdbc:h2:file:./${databaseManager.dataBasePath}/${databaseManager.fileName}",
        driver = "org.h2.Driver"
    )

    init {
        transaction {
            SchemaUtils.create(StorageInfoTable, IndexFileTable, FileHashTable)
        }
    }

    fun insertStorageInfo(
        name: String,
        desc: String,
        timestamp: Long,
        useIncrementalStorage: Boolean,
        indexStorage: List<String>
    ) {
        transaction(database) {
            StorageInfoTable.insert {
                it[collectionUuid] = databaseManager.collectionUuid
                it[StorageInfoTable.name] = name
                it[StorageInfoTable.desc] = desc
                it[StorageInfoTable.timestamp] = timestamp
                it[StorageInfoTable.useIncrementalStorage] = useIncrementalStorage
                it[StorageInfoTable.indexStorage] = indexStorage
            }
        }
    }

    fun insertIndexFile(name: String, indexFileMap: Map<String, String>) {
        transaction(database) {
            IndexFileTable.insert {
                it[collectionUuid] = databaseManager.collectionUuid
                it[IndexFileTable.name] = name
                it[IndexFileTable.indexFileMap] = gson.toJson(indexFileMap)
            }
        }
    }

    fun insertFileHash(name: String, fileHashMap: Map<String, String>) {
        transaction(database) {
            FileHashTable.insert {
                it[collectionUuid] = databaseManager.collectionUuid
                it[FileHashTable.name] = name
                it[FileHashTable.fileHashMap] = gson.toJson(fileHashMap)
            }
        }
    }

    fun updateStorageInfo(
        storageInfo: StorageInfo
    ) {
        transaction(database) {
            StorageInfoTable.update({
                ( StorageInfoTable.name eq storageInfo.name) and (StorageInfoTable.collectionUuid eq databaseManager.collectionUuid)
            }) {
                it[indexStorage] = storageInfo.indexStorage
            }
        }
    }

    fun updateIndexFileValue(name: String, indexFileMap: Map<String, String>) {
        transaction(database) {
            IndexFileTable.update({
                ( IndexFileTable.name eq name) and (IndexFileTable.collectionUuid eq databaseManager.collectionUuid)
            }) {
                it[IndexFileTable.indexFileMap] = gson.toJson(indexFileMap)
            }
        }
    }

    fun <T: BaseTable> selectTable(name: String, table: T): List<ResultRow> {
        return transaction(database) {
            table.selectAll()
                .where { table.name eq name }
                .toList()
        }
    }

    fun <T: BaseTable> deleteTableValue(name: String, table: T) {
        transaction(database) {
            table.deleteWhere(op = { table.name eq name })
        }
    }

    fun deleteTableValue(name: String, tableType: DatabaseTables) {
        when (tableType) {
            DatabaseTables.INDEX_FILE -> deleteTableValue(name, IndexFileTable)
            DatabaseTables.FILE_HASH -> deleteTableValue(name, FileHashTable)
            DatabaseTables.STORAGE_INFO -> deleteTableValue(name, StorageInfoTable)
        }
    }

    fun storageExists(name: String): Boolean {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.collectionUuid }
                .where { StorageInfoTable.name eq name }
                .toList()
                .isNotEmpty()
        }
    }

    fun getStorageInfoWithNameList(nameList: List<String>): List<StorageInfo> {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.collectionUuid }
                .where { StorageInfoTable.name inList nameList }
                .toList()
                .map { StorageInfoTable.getStorageInfo(it) }
        }
    }

    fun getStorageInfoWithIndexStorage(indexStorageInfoName: String): List<StorageInfo> {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.collectionUuid }
                .toList()
                .map { StorageInfoTable.getStorageInfo(it) }
                .filter { it.indexStorage.contains(indexStorageInfoName) }
        }
    }

    fun getAllStorageInfo(): List<ResultRow> {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.collectionUuid }
                .toList()
        }
    }

    fun getIndexFileWithNameList(nameList: List<String>): List<IndexFile> {
        return transaction(database) {
            IndexFileTable.selectAll()
                .where { IndexFileTable.collectionUuid eq databaseManager.collectionUuid }
                .where { IndexFileTable.name inList nameList }
                .toList()
                .map { IndexFileTable.getIndexFile(it) }
        }
    }

    fun getIndexFile(name: String): IndexFile {
        val map =  transaction(database) {
            IndexFileTable.selectAll()
                .where { IndexFileTable.collectionUuid eq databaseManager.collectionUuid }
                .where { IndexFileTable.name eq name }
                .toList()
                .first()
                .let {
                    val map = gson.fromJson(it[IndexFileTable.indexFileMap], Map::class.java)
                    return@let if (map is Map<*, *>) map.mapKeys { it.key.toString() }.mapValues { it.value.toString() } else emptyMap()
                }
        }
        return IndexFile(name, map)
    }

    fun getFileHashMap(name: String): Map<String, String> {
        return transaction(database) {
            FileHashTable.selectAll()
                .where { FileHashTable.collectionUuid eq databaseManager.collectionUuid }
                .where { FileHashTable.name eq name }
                .toList()
                .first()
                .let {
                    val map = gson.fromJson(it[FileHashTable.fileHashMap], Map::class.java)
                    return@let if (map is Map<*, *>) map.mapKeys { it.key.toString() }.mapValues { it.value.toString() } else emptyMap()
                }
        }
    }

    fun getDatabase(): Database {
        return database
    }

    fun closeDatabase() {
        transaction(database) {
            closeExecutedStatements()
        }
    }
}