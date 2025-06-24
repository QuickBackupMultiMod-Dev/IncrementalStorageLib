package io.github.skydynamic.increment.storage.lib.manager

import java.util.*

interface IDatabaseManager {
    fun setFileName(name: String?)

    fun setDatabasePath(path: String?)

    fun setCollectionUuid(uuid: UUID?)

    fun getFileName(): String {
        return "dataBase"
    }

    fun getDatabasePath(): String {
        return "."
    }

    fun getCollectionUuid(): UUID {
        return UUID.randomUUID()
    }
}