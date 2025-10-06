package io.github.skydynamic.increment.storage.lib.database

import com.google.gson.Gson
import io.github.skydynamic.increment.storage.lib.manager.IDatabaseManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

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
        transaction(database) {
            SchemaUtils.create(StorageInfoTable, FileHashTable, FileHashReferenceTable)
        }

        runMigrations()
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
        }.filter { it.desc != "a5ff1c641758cc02744172a50e577bbe06c2a1c5" }
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

    /**
     * @deprecated Use [getReferenceCountsForHashes] for batch queries to avoid N+1 query problem.
     */
    @Deprecated(
        message = "Use getReferenceCountsForHashes for batch queries to avoid N+1 query problem",
        replaceWith = ReplaceWith("getReferenceCountsForHashes(setOf(hash))[hash] ?: 0L")
    )
    fun getReferenceCountForHash(hash: String): Long {
        return transaction(database) {
            FileHashReferenceTable.selectAll()
                .where {
                    (FileHashReferenceTable.collectionUuid eq databaseManager.getCollectionUuid()) and
                        (FileHashReferenceTable.fileHash eq hash)
                }
                .count()
        }
    }

    fun getReferenceCountsForHashes(hashes: Set<String>): Map<String, Long> {
        if (hashes.isEmpty()) {
            return emptyMap()
        }

        val countColumn = FileHashReferenceTable.id.count()
        val referenceCounts = mutableMapOf<String, Long>()

        transaction(database) {
            hashes.chunked(MAX_HASHES_PER_QUERY).forEach { chunk ->
                FileHashReferenceTable
                    .select(FileHashReferenceTable.fileHash, countColumn)
                    .where {
                        (FileHashReferenceTable.collectionUuid eq databaseManager.getCollectionUuid()) and
                            (FileHashReferenceTable.fileHash inList chunk)
                    }
                    .groupBy(FileHashReferenceTable.fileHash)
                    .forEach { row ->
                        referenceCounts[row[FileHashReferenceTable.fileHash]] = row[countColumn]
                    }
            }
        }

        return hashes.associateWith { hash -> referenceCounts[hash] ?: 0L }
    }

    fun getDatabase(): Database {
        return database
    }

    fun closeDatabase() {
        transaction(database) {
            closeExecutedStatements()
        }
    }

    private fun runMigrations() {
        transaction(database) {
            // Ensure the hash lookup index exists for databases created before the index definition was added.
            exec(
                "CREATE INDEX IF NOT EXISTS $FILE_HASH_REFERENCE_INDEX_NAME ON $FILE_HASH_REFERENCE_TABLE_NAME($FILE_HASH_COLUMN_NAME)"
            )
        }
    }

    private companion object {
        private const val MAX_HASHES_PER_QUERY = 900
        private const val FILE_HASH_REFERENCE_TABLE_NAME = "file_hash_reference"
        private const val FILE_HASH_REFERENCE_INDEX_NAME = "file_hash_reference_file_hash"
        private const val FILE_HASH_COLUMN_NAME = "file_hash"
    }
}
