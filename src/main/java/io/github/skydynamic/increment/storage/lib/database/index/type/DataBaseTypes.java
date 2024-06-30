package io.github.skydynamic.increment.storage.lib.database.index.type;

@SuppressWarnings("unused")
public enum DataBaseTypes {
    FILES_HASHES("FileHash", FileHash.class),
    STORAGE_INFO("StorageInfo", StorageInfo.class),
    FILE_INDEX("IndexFile", IndexFile.class);
    public final String type;
    public final Class<?> cls;

    DataBaseTypes(String type, Class<?> cls) {
        this.type = type;
        this.cls = cls;
    }
}
