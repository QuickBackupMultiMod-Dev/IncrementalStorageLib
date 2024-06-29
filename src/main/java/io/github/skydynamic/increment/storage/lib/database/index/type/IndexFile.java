package io.github.skydynamic.increment.storage.lib.database.index.type;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Getter;
import org.bson.types.ObjectId;

import java.util.Map;

@SuppressWarnings("unused")
@Entity(value = "IndexFile", useDiscriminator = false)
public class IndexFile {
    @Id private ObjectId id;
    @Getter
    private String name;
    @Getter
    private Map<String, String> indexFileMap;

    @Deprecated // Morphia only!
    public IndexFile() {}

    public IndexFile(String name, Map<String, String> indexFileMap) {
        this.name = name;
        this.indexFileMap = indexFileMap;
    }
}
