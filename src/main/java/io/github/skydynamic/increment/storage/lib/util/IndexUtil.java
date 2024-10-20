package io.github.skydynamic.increment.storage.lib.util;

import dev.morphia.query.Query;
import dev.morphia.query.filters.Filters;
import io.github.skydynamic.increment.storage.lib.Interface.IConfig;
import io.github.skydynamic.increment.storage.lib.database.DataBase;
import io.github.skydynamic.increment.storage.lib.database.index.type.IndexFile;
import io.github.skydynamic.increment.storage.lib.database.index.type.StorageInfo;
import io.github.skydynamic.increment.storage.lib.logging.LogUtil;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class IndexUtil {
    private static final Logger LOGGER = LogUtil.getLogger();

    @Setter
    private static DataBase dataBase;
    @Setter
    private static IConfig config;

    public static void copyIndexFile(String name, Path storagePath, File targetFile) throws IOException {
        Map<String, String> indexFileMap = dataBase.getIndexFileMap(name);
        for (String fileKey : indexFileMap.keySet()) {
            File indexFilePathFile = storagePath.resolve(indexFileMap.get(fileKey)).resolve(fileKey).toFile();
            File targetFilePathFile = targetFile.toPath().resolve(fileKey).getParent().toFile();
            try {
                FileUtils.copyFileToDirectory(indexFilePathFile, targetFilePathFile);
            } catch (FileNotFoundException e) {
                LOGGER.warn("Skip to copy %s because: ".formatted(fileKey), e);
            }
        }
    }

    public static void reIndexFile(
        IndexFile reIndexTargetIndexFile,
        String reIndexTargetName,
        String deleteName,
        boolean isDelete
    ) {
        Map<String, String> reIndexTargetIndexFileMap = reIndexTargetIndexFile.getIndexFileMap();
        Map<String, String> newMap = new HashMap<>(reIndexTargetIndexFileMap);

        for (String fileKey : reIndexTargetIndexFileMap.keySet()) {
            if (reIndexTargetIndexFileMap.get(fileKey).equals(deleteName)) {
                if (isDelete) {
                    newMap.remove(fileKey);
                } else {
                    newMap.replace(fileKey, reIndexTargetName);
                }
            }
        }
        reIndexTargetIndexFile.setIndexFileMap(newMap);
        dataBase.save(reIndexTargetIndexFile);
    }

    public static void reIndex(String name, String resolvePath) throws IOException {
        Path storagePath = Path.of(config.getStoragePath()).resolve(resolvePath);
        Query<StorageInfo> query = dataBase.getDatastore()
            .find(StorageInfo.class)
            .filter(Filters.in("indexStorage", Collections.singletonList(name)));

        Optional<StorageInfo> earliestStorageInfoOptional = query.stream()
            .min(Comparator.comparingLong(StorageInfo::getTimestamp));

        if (earliestStorageInfoOptional.isPresent()) {
            StorageInfo earliestStorageInfo = earliestStorageInfoOptional.get();
            String reIndexTargetName = earliestStorageInfo.getName();
            File reIndexTargetPathFile = storagePath.resolve(reIndexTargetName).toFile();

            copyIndexFile(reIndexTargetName, storagePath, reIndexTargetPathFile);

            IndexFile reIndexTargetFile = dataBase.getDatastore()
                .find(IndexFile.class)
                .filter(Filters.eq("name", reIndexTargetName))
                .first();
            if (reIndexTargetFile == null) throw new NullPointerException("%s does not exist".formatted(reIndexTargetName));

            reIndexFile(reIndexTargetFile, reIndexTargetName, name, true);

            List<String> reindexStorageList = query.stream()
                .map(StorageInfo::getName)
                .collect(Collectors.toList());

            Query<IndexFile> indexQuery = dataBase.getDatastore()
                .find(IndexFile.class)
                .filter(Filters.in("name", reindexStorageList));
            Query<StorageInfo> storageQuery = dataBase.getDatastore()
                .find(StorageInfo.class)
                .filter(Filters.in("name", reindexStorageList));

            for (StorageInfo storageInfo : storageQuery) {
                List<String> indexStorageList = storageInfo.getIndexStorage();
                indexStorageList.remove(name);
                if (!indexStorageList.contains(reIndexTargetName) && !reIndexTargetName.equals(storageInfo.getName())) {
                    indexStorageList.add(reIndexTargetName);
                }
                storageInfo.setIndexStorage(indexStorageList);
                dataBase.save(storageInfo);
            }

            for (IndexFile indexFile : indexQuery) {
                reIndexFile(indexFile, reIndexTargetName, name, false);
            }
        }
    }
}
