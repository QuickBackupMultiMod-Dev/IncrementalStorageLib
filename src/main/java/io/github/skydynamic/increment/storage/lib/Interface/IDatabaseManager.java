package io.github.skydynamic.increment.storage.lib.Interface;

import java.util.UUID;

@SuppressWarnings("unused")
public interface IDatabaseManager {
    void setFileName(String name);

    void setDatabasePath(String path);

    void setCollectionUuid(UUID uuid);

    default String getFileName() {
        return "dataBase";
    }

    default String getDatabasePath() {
        return ".";
    }

    default UUID getCollectionUuid() {
        return UUID.randomUUID();
    }
}
