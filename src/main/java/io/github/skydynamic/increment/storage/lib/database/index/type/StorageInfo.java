package io.github.skydynamic.increment.storage.lib.database.index.type;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import org.bson.types.ObjectId;

import java.util.List;

@Entity(value = "StorageInfo", useDiscriminator = false)
public class StorageInfo {
    @Id private ObjectId id;
    private String name;
    private String desc;
    private long timestamp;
    private boolean useIncrementalStorage;
    private List<String> indexStorage;

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isUseIncrementalStorage() {
        return useIncrementalStorage;
    }

    public List<String> getIndexStorage() {
        return indexStorage;
    }

    public void setIndexStorage(List<String> indexStorage) {
        this.indexStorage = indexStorage;
    }

    @Deprecated // Morphia only!
    public StorageInfo() {}

    public StorageInfo(String name, String desc, long timestamp, boolean useIncrementalStorage, List<String> indexStorage) {
        this.name = name;
        this.desc = desc;
        this.timestamp = timestamp;
        this.useIncrementalStorage = useIncrementalStorage;
        this.indexStorage = indexStorage;
    }
}
