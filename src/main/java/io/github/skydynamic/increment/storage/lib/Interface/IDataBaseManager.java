package io.github.skydynamic.increment.storage.lib.Interface;

import java.nio.file.Path;

@SuppressWarnings("unused")
public interface IDataBaseManager {
    String fileName = "dataBase";
    Path dataBasePath = Path.of(".");

    void setFileName(String name);

    void setDataBasePath(Path path);

    String getFileName();

    Path getDataBasePath();
}
