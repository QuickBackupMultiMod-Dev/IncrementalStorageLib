package io.github.skydynamic.increment.storage.lib.Interface;

import java.nio.file.Path;
import java.util.UUID;

@SuppressWarnings("unused")
public interface IDataBaseManager {
    void setFileName(String name);

    void setDataBasePath(Path path);

    void setCollectionUuid(UUID uuid);

    default String getFileName() {
        return "dataBase";
    }

    default Path getDataBasePath() {
        return Path.of(".");
    }

    default UUID getCollectionUuid() {
        return UUID.randomUUID();
    }
}
