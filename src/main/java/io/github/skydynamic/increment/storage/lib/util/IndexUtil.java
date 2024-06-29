package io.github.skydynamic.increment.storage.lib.util;

import dev.morphia.query.Query;
import dev.morphia.query.filters.Filters;
import io.github.skydynamic.increment.storage.lib.Interface.IConfig;
import io.github.skydynamic.increment.storage.lib.database.DataBase;
import io.github.skydynamic.increment.storage.lib.database.index.type.IndexFile;
import io.github.skydynamic.increment.storage.lib.database.index.type.StorageInfo;
import lombok.Setter;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class IndexUtil {
    @Setter
    private static DataBase dataBase;
    @Setter
    private static IConfig config;

    public static void copyIndexFile(String name, File targetFile) throws IOException {
        Map<String, String> indexFileMap = dataBase.getIndexFileMap(name);
        Path storagePath = Path.of(config.getStoragePath());
        for (String fileKey : indexFileMap.keySet()) {
            File indexFilePathFile = storagePath.resolve(indexFileMap
                    .get(fileKey))
                .resolve(fileKey.replace(".", File.separator).replace("#", File.separator))
                .toFile();
            File targetFilePathFile = targetFile.toPath()
                .resolve(fileKey.replace(".", File.separator).replace("#", File.separator))
                .toFile();
            FileUtils.copyFileToDirectory(indexFilePathFile, targetFilePathFile);
        }
    }

    public static void reIndexFile(
        Map<String, String> sourceIndexFileMap,
        String sourceName,
        String reIndexName,
        boolean isDelete
    ) {
        Map<String, String> newMap = new HashMap<>(sourceIndexFileMap);
        for (String fileKey : sourceIndexFileMap.keySet()) {
            if (sourceIndexFileMap.get(fileKey).equals(sourceName)) {
                newMap.replace(fileKey, reIndexName);
                if (isDelete) newMap.remove(fileKey);
            }
        }
        dataBase.save(new IndexFile(sourceName, newMap));
    }

    public static void reIndex(String name) throws IOException {
        Path storagePath = Path.of(config.getStoragePath());
        Query<StorageInfo> query = dataBase.getDatastore()
            .find(StorageInfo.class)
            .filter(Filters.in("indexBackup", Collections.singletonList(name)));

        List<String> reindexStorageList = new ArrayList<>();

        String reIndexTargetName = null;
        long timestamp = 9999999999999L;
        for (StorageInfo backupInfo : query) {
            reindexStorageList.add(backupInfo.getName());
            if (backupInfo.getTimestamp() < timestamp) {
                timestamp = backupInfo.getTimestamp();
                reIndexTargetName = backupInfo.getName();
            }
        }
        if (reIndexTargetName != null) {
            File reIndexTarget = storagePath.resolve(reIndexTargetName).toFile();

            copyIndexFile(reIndexTargetName, reIndexTarget);

            IndexFile sourceIndexFile = dataBase.getDatastore()
                .find(IndexFile.class)
                .filter(Filters.eq("name", reIndexTargetName))
                .first();
            if (sourceIndexFile == null) throw new NullPointerException("%s does not exist".formatted(reIndexTargetName));
            Map<String, String> sourceIndexFileMap = sourceIndexFile.getIndexFileMap();

            reIndexFile(sourceIndexFileMap, name, reIndexTargetName, true);

            Query<IndexFile> indexQuery = dataBase.getDatastore()
                .find(IndexFile.class)
                .filter(Filters.in("name", reindexStorageList));
            Query<StorageInfo> storageQuery = dataBase.getDatastore()
                .find(StorageInfo.class)
                .filter(Filters.in("name", reindexStorageList));

            for (StorageInfo storageInfo : storageQuery) {
                List<String> indexStorageList = storageInfo.getIndexStorage();
                indexStorageList.remove(name);
                if (!indexStorageList.contains(reIndexTargetName) && !reIndexTargetName.equals(storageInfo.getName()))
                    indexStorageList.add(reIndexTargetName);
                storageInfo.setIndexStorage(indexStorageList);
                dataBase.save(storageInfo);
            }

            for (IndexFile indexFile : indexQuery) {
                reIndexFile(indexFile.getIndexFileMap(), name, reIndexTargetName, false);
            }
        }
    }
}
