package io.github.skydynamic.increment.storage.lib.Interface;

@SuppressWarnings("unused")
public interface IConfig {
    default boolean getUseInternalDataBase() {
        return true;
    }

    default String getMongoDBUri() {
        return "mongodb://localhost:27017";
    }

    default String getStoragePath() {
        return "./storage/";
    }
}
