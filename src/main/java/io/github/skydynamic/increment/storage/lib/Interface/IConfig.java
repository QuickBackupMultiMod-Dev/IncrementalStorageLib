package io.github.skydynamic.increment.storage.lib.Interface;

public interface IConfig {
    boolean useInternalDataBase = true;
    String mongoDBUri = "mongodb://localhost:27017";
    String storagePath = "./storage/";

    void setUseInternalDataBase(boolean value);

    void setMongoDBUri(String uri);

    boolean getUserInternalDataBase();

    String getMongoDBUri();

    String getStoragePath();
}
