package io.github.skydynamic.increment.storage.lib.database.index.type;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import org.bson.types.ObjectId;

import java.util.HashMap;

@Entity(value = "FileHash", useDiscriminator = false)
public class FileHash {
    @Id private ObjectId id;
    private String name;
    private HashMap<String, String> fileHashMap;

    public String getName() {
        return name;
    }

    public HashMap<String, String> getFileHashMap() {
        return fileHashMap;
    }

    @Deprecated // Morphia only!
    public FileHash() {}

    public FileHash(String name, HashMap<String, String> fileHashMap) {
        this.name = name;
        this.fileHashMap = fileHashMap;
    }
}
