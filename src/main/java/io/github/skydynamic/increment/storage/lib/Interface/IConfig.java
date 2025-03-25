package io.github.skydynamic.increment.storage.lib.Interface;

@SuppressWarnings("unused")
public interface IConfig {
    default String getStoragePath() {
        return "./storage/";
    }
}
