package io.github.skydynamic.increment.storage.lib.Interface;

import java.nio.file.Path;

@SuppressWarnings("unused")
public interface IDataBaseManager {
    void setFileName(String name);

    void setDataBasePath(Path path);

    default String getFileName() {
        return "dataBase";
    }

    default Path getDataBasePath() {
        return Path.of(".");
    }
}
