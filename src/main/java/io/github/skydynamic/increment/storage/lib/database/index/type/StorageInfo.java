package io.github.skydynamic.increment.storage.lib.database.index.type;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.List;

@Entity(value = "StorageInfo", useDiscriminator = false)
@SuppressWarnings("unused")
public class StorageInfo {
    @Id private ObjectId id;
    @Getter
    private String name;
    @Getter
    private String desc;
    @Getter
    private long timestamp;
    @Getter
    private boolean useIncrementalStorage;
    @Setter
    @Getter
    private List<String> indexStorage;

    @Deprecated // Morphia only!
    public StorageInfo() {}

    public StorageInfo(String name, String desc, long timestamp, boolean useIncrementalStorage, List<String> indexStorage) {
        this.name = name;
        this.desc = desc;
        this.timestamp = timestamp;
        this.useIncrementalStorage = useIncrementalStorage;
        this.indexStorage = indexStorage;
    }

    public StorageInfo(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }
}
