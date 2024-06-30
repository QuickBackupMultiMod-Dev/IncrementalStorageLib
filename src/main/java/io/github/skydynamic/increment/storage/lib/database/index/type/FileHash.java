package io.github.skydynamic.increment.storage.lib.database.index.type;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Getter;
import org.bson.types.ObjectId;

import java.util.Map;

@SuppressWarnings("unused")
@Entity(value = "FileHash", useDiscriminator = false)
public class FileHash {
    @Id
    private ObjectId id;
    @Getter
    private String name;
    @Getter
    private Map<String, String> fileHashMap;

    public FileHash() {
    }

    public FileHash(String name, Map<String, String> fileHashMap) {
        this.name = name;
        this.fileHashMap = fileHashMap;
    }
}
