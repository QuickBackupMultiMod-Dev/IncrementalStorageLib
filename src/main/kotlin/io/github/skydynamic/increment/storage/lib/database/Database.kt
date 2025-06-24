package io.github.skydynamic.increment.storage.lib.database

import com.google.gson.Gson
import io.github.skydynamic.increment.storage.lib.manager.IDatabaseManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

private val gson = Gson()

fun String.toMap(): Map<String, String> {
    val map = gson.fromJson(this, Map::class.java)
    return if (map is Map<*, *>) map.mapKeys { it.key.toString() }.mapValues { it.value.toString() } else emptyMap()
}

class Database(private val databaseManager: IDatabaseManager) {
    private val database: Database = Database.connect(
        url = "jdbc:h2:file:${databaseManager.getDatabasePath()}/${databaseManager.getFileName()}",
        driver = "org.h2.Driver"
    )

    init {
        transaction {
            SchemaUtils.create(StorageInfoTable, FileHashTable, FileHashReferenceTable)
        }
    }

    fun insertStorageInfo(
        name: String,
        desc: String,
        timestamp: Long,
        useIncrementalStorage: Boolean
    ) {
        transaction(database) {
            StorageInfoTable.insert {
                it[collectionUuid] = databaseManager.getCollectionUuid()
                it[StorageInfoTable.name] = name
                it[StorageInfoTable.desc] = desc
                it[StorageInfoTable.timestamp] = timestamp
                it[StorageInfoTable.useIncrementalStorage] = useIncrementalStorage
            }
        }
    }

    fun insertFileHash(name: String, fileHashMap: Map<String, String>) {
        transaction(database) {
            FileHashTable.insert {
                it[collectionUuid] = databaseManager.getCollectionUuid()
                it[FileHashTable.name] = name
                it[FileHashTable.fileHashMap] = gson.toJson(fileHashMap)
            }

            fileHashMap.keys.forEach { hash ->
                FileHashReferenceTable.insert {
                    it[collectionUuid] = databaseManager.getCollectionUuid()
                    it[FileHashReferenceTable.name] = name
                    it[fileHash] = hash
                }
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

    private fun <T: BaseTable> deleteTableValue(name: String, table: T) {
        transaction(database) {
            table.deleteWhere(op = { (table.name eq name) and (table.collectionUuid eq databaseManager.getCollectionUuid()) })
        }
    }

    fun deleteTableValue(name: String, tableType: DatabaseTables) {
        when (tableType) {
            DatabaseTables.FILE_HASH -> deleteTableValue(name, FileHashTable)
            DatabaseTables.STORAGE_INFO -> deleteTableValue(name, StorageInfoTable)
        }
    }

    fun storageExists(name: String): Boolean {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.getCollectionUuid() }
                .toList()
                .any { it[StorageInfoTable.name] == name }
        }
    }

    fun getStorageInfoWithName(name: String): StorageInfo? {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.getCollectionUuid() }
                .toList()
                .firstOrNull { it[StorageInfoTable.name] == name }
                ?.let { StorageInfoTable.getStorageInfo(it) }
        }
    }

    fun getStorageInfoWithNameList(nameList: List<String>): List<StorageInfo> {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.getCollectionUuid() }
                .toList()
                .filter { it[StorageInfoTable.name] in nameList }
                .map { StorageInfoTable.getStorageInfo(it) }
        }
    }

    fun getAllStorageInfo(): List<StorageInfo> {
        return transaction(database) {
            StorageInfoTable.selectAll()
                .where { StorageInfoTable.collectionUuid eq databaseManager.getCollectionUuid() }
                .toList()
                .map { StorageInfoTable.getStorageInfo(it) }
        }
    }

    fun getFileHashMap(name: String): Map<String, String> {
        return transaction(database) {
            FileHashTable.selectAll()
                .where { FileHashTable.collectionUuid eq databaseManager.getCollectionUuid() }
                .toList()
                .first { it[FileHashTable.name] == name }
                .let {
                    val map = gson.fromJson(it[FileHashTable.fileHashMap], Map::class.java)
                    return@let if (map is Map<*, *>) map.mapKeys { it.key.toString() }.mapValues { it.value.toString() } else emptyMap()
                }
        }
    }

    fun getReferenceCountForHash(hash: String): Long {
    return transaction(database) {
        FileHashReferenceTable.selectAll()
            .where{ (FileHashReferenceTable.collectionUuid eq databaseManager.getCollectionUuid()) and (FileHashReferenceTable.fileHash eq hash) }
            .count()
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