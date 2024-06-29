package io.github.skydynamic.increment.storage.lib.database.index.type;

@SuppressWarnings("unused")
public enum DataBaseTypes {
    FILES_HASHES("FileHash"),
    BACKUP_INFO("StorageInfo"),
    FILE_INDEX("IndexFile");
    public final String type;

    DataBaseTypes(String type) {
        this.type = type;
    }
}
