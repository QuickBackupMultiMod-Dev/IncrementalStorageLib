package io.github.skydynamic.increment.storage.lib.Interface;

import java.nio.file.Path;

public interface IDataBaseManager {
    String name = "dataBase";
    Path dataBasePath = Path.of(".");

    void setName(String name);

    void setDataBasePath(Path path);

    String getName();

    Path getDataBasePath();
}
