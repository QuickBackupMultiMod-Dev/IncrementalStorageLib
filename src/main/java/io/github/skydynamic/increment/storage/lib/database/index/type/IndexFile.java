package io.github.skydynamic.increment.storage.lib.database.index.type;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import org.bson.types.ObjectId;

import java.util.HashMap;

@Entity(value = "IndexFile", useDiscriminator = false)
public class IndexFile {
    @Id private ObjectId id;
    private String name;
    private HashMap<String, String> indexFileMap;

    public String getName() {
        return name;
    }

    public HashMap<String, String> getIndexFileMap() {
        return indexFileMap;
    }

    @Deprecated // Morphia only!
    public IndexFile() {}

    public IndexFile(String name, HashMap<String, String> indexFileMap) {
        this.name = name;
        this.indexFileMap = indexFileMap;
    }
}
