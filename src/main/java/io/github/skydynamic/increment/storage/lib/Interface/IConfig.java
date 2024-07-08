package io.github.skydynamic.increment.storage.lib.Interface;

@SuppressWarnings("unused")
public interface IConfig {
    boolean useInternalDataBase = true;
    String mongoDBUri = "mongodb://localhost:27017";
    String storagePath = "./storage/";

    void setUseInternalDataBase(boolean value);

    void setMongoDBUri(String uri);

    boolean getUseInternalDataBase();

    String getMongoDBUri();

    String getStoragePath();
}
