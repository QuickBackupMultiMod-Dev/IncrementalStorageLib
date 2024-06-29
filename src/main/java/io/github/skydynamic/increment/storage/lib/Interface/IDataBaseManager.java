package io.github.skydynamic.increment.storage.lib.Interface;

import java.nio.file.Path;

@SuppressWarnings("unused")
public interface IDataBaseManager {
    String fileName = "dataBase";
    String collectionName = "";
    Path dataBasePath = Path.of(".");

    void setFileName(String name);

    void setCollectionName(String name);

    void setDataBasePath(Path path);

    String getFileName();

    String getCollectionName();

    Path getDataBasePath();
}
