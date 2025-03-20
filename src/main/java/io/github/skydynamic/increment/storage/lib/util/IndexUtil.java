package io.github.skydynamic.increment.storage.lib.util;

import io.github.skydynamic.increment.storage.lib.Interface.IConfig;
import io.github.skydynamic.increment.storage.lib.database.Database;
import io.github.skydynamic.increment.storage.lib.database.IndexFile;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.increment.storage.lib.logging.LogUtil;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
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
    private static Database database;
    @Setter
    private static IConfig config;

    public static void copyIndexFile(String name, Path storagePath, File targetFile) throws IOException {
        IndexFile indexFile = database.getIndexFile(name);
        for (String fileKey : indexFile.getIndexFileMap().keySet()) {
            File indexFilePathFile = storagePath.resolve(indexFile.getIndexFileMap().get(fileKey)).resolve(fileKey).toFile();
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
        Map<String, String> oldMap = reIndexTargetIndexFile.getIndexFileMap();
        Map<String, String> newMap = new HashMap<>(oldMap);

        for (String fileKey : oldMap.keySet()) {
            if (oldMap.get(fileKey).equals(deleteName)) {
                if (isDelete) {
                    newMap.remove(fileKey);
                } else {
                    newMap.replace(fileKey, reIndexTargetName);
                }
            }
        }
        database.updateIndexFileValue(reIndexTargetIndexFile.getName(), newMap);
    }

    public static void reIndex(String name, String resolvePath) throws IOException {
        Path storagePath = Path.of(config.getStoragePath()).resolve(resolvePath);
        List<StorageInfo> query = database.getStorageInfoWithIndexStorage(name);

        Optional<StorageInfo> earliestStorageInfoOptional = query.stream()
            .min(Comparator.comparingLong(StorageInfo::getTimestamp));

        if (earliestStorageInfoOptional.isPresent()) {
            StorageInfo earliestStorageInfo = earliestStorageInfoOptional.get();
            String reIndexTargetName = earliestStorageInfo.getName();
            File reIndexTargetPathFile = storagePath.resolve(reIndexTargetName).toFile();

            copyIndexFile(reIndexTargetName, storagePath, reIndexTargetPathFile);

            IndexFile reIndexTargetFile = database.getIndexFile(reIndexTargetName);
            Map<String, String> reIndexTargetFileMap = reIndexTargetFile.getIndexFileMap();

            reIndexFile(reIndexTargetFile, reIndexTargetName, name, true);

            List<String> reindexStorageList = query.stream()
                .map(StorageInfo::getName)
                .collect(Collectors.toList());

            List<IndexFile> indexQuery = database.getIndexFileWithNameList(reindexStorageList);
            List<StorageInfo> storageQuery = database.getStorageInfoWithNameList(reindexStorageList);

            for (StorageInfo storageInfo : storageQuery) {
                List<String> indexStorageList = storageInfo.getIndexStorage();
                indexStorageList.remove(name);
                if (!indexStorageList.contains(reIndexTargetName) && !reIndexTargetName.equals(storageInfo.getName())) {
                    indexStorageList.add(reIndexTargetName);
                }
                database.updateStorageInfo(storageInfo);
            }

            for (IndexFile indexFile : indexQuery) {
                reIndexFile(indexFile, reIndexTargetName, name, false);
            }
        }
    }
}
