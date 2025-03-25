package io.github.skydynamic.increment.storage.lib.util;

import io.github.skydynamic.increment.storage.lib.database.*;
import io.github.skydynamic.increment.storage.lib.exception.IncrementalStorageException;
import io.github.skydynamic.increment.storage.lib.logging.LogUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class Storager {
    private static final Logger LOGGER = LogUtil.getLogger();

    private final Database database;

    public Storager(Database dataBase) {
        this.database = dataBase;
    }

    public void deleteStorage(String name) {
        for (DatabaseTables type : DatabaseTables.getEntries()) {
            database.deleteTableValue(name, type);
        }
    }

    private void writeStorageInfo(
        String name,
        String desc,
        boolean useIncrementalStorage,
        List<String> indexBackupList
    ) {
        database.insertStorageInfo(name, desc, System.currentTimeMillis(), useIncrementalStorage, indexBackupList);
    }

    private void writeStorageInfo(
        StorageInfo storageInfo
    ) {
        database.insertStorageInfo(
            storageInfo.getName(),
            storageInfo.getDesc(),
            storageInfo.getTimestamp(),
            storageInfo.getUseIncrementalStorage(),
            storageInfo.getIndexStorage()
        );
    }

    private void makeDirAndCopyToDir(File source, @NotNull File dest) throws IOException {
        FileUtils.copyFileToDirectory(source, dest);
    }

    private Collection<File> getDirectoryFiles(
        File file,
        IOFileFilter fileFilter,
        IOFileFilter dirFilter
    ) {
        return Arrays.stream(file.listFiles((FileFilter) fileFilter)).filter(dirFilter::accept).toList();
    }

    private @NotNull String getFileHash(@NotNull File file) {
        try {
            return HashUtil.getFileHash(file.toPath());
        } catch (IOException e) {
            LOGGER.error(e.toString());
            return "";
        }
    }

    private @NotNull HashMap<String, String> getFileHashMap(
        @NotNull Collection<File> files,
        @NotNull String parentPathString,
        boolean isRoot,
        IOFileFilter fileFilter,
        IOFileFilter dirFilter
    ) {
        HashMap<String, String> map = new HashMap<>();
        for (File file : files) {
            if (file.isDirectory()) {
                Collection<File> dirFiles = getDirectoryFiles(file, fileFilter, dirFilter);
                HashMap<String, String> appendMap = getFileHashMap(
                    dirFiles, parentPathString + file.getName() + "/",
                    false, fileFilter, dirFilter
                );
                map.putAll(appendMap);
            } else {
                String fileHash = getFileHash(file);
                String key = parentPathString + file.getName();
                map.put(key, fileHash);
            }
        }
        return map;
    }

    private <T extends Map<String, String>> @NotNull Map<String, Object> compareGetIndexFileMap(
        String latestStorageName,
        @NotNull T newStorageHashMap,
        T latestStorageHashMap,
        T latestStorageIndexFileMap,
        List<String> indexStorageList,
        Path sourceDir,
        File destCopyDir
    ) throws IOException {
        Map<String, String> indexMap = new HashMap<>();
        for (String fileKey : newStorageHashMap.keySet()) {
            String newHashValue = newStorageHashMap.get(fileKey);
            String latestHashValue = latestStorageHashMap.get(fileKey);
            if (newHashValue.equals(latestHashValue)) {
                String index = latestStorageIndexFileMap.get(fileKey);
                if (index == null || index.isEmpty()) index = latestStorageName;
                indexMap.put(fileKey, index);
                if (!indexStorageList.contains(index)) indexStorageList.add(index);
            } else {
                Path indexFilePath = Path.of(fileKey);
                File sourceFile = sourceDir.resolve(indexFilePath).toFile();
                makeDirAndCopyToDir(sourceFile, destCopyDir.toPath().resolve(indexFilePath.getParent()).toFile());
            }
        }
        Map<String, Object> returnMap = new HashMap<>();
        returnMap.put("index", indexMap);
        returnMap.put("list", indexStorageList);
        return returnMap;
    }

    private String getLatestStorageName() {
        List<StorageInfo> infoList = database.getAllStorageInfo()
            .stream()
            .filter(StorageInfoTable::isUseIncrementalStorage)
            .toList();
        String latestBackupName = "";
        if (!infoList.isEmpty()) {
            long time = 0;
            for (StorageInfo info : infoList) {
                long timestamp = info.getTimestamp();
                if (Math.max(time, timestamp) == timestamp) {
                    latestBackupName = info.getName();
                    time = timestamp;
                }
            }
        }
        return latestBackupName;
    }

    /**
     * Get the storage exists
     *
     * @param name Check target storage name
     * @return Boolean
     */
    public boolean storageExists(String name) {
        return database.storageExists(name);
    }

    /**
     * Calculate the file hash value and compare it with the latest storage's files.
     * Then copy the files with differences
     *
     * @param storageDir source directory
     * @param targetDir  target directory
     * @throws IncrementalStorageException File target is not a directory
     */
    public void incrementalStorage(
        StorageInfo storageInfo,
        @NotNull Path storageDir,
        Path targetDir
    ) throws IncrementalStorageException, IOException {
        File storageFile = storageDir.toFile();
        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        IOFileFilter filter = FileFilterUtils.trueFileFilter();

        incrementalStorage(storageInfo, storageDir, targetDir, filter, filter);
    }

    /**
     * Calculate the file hash value and compare it with the latest storage's files.
     * Then copy the files with differences
     *
     * @param storageDir source directory
     * @param targetDir  target directory
     * @param fileFilter fileFilter
     * @param dirFilter  dirFilter
     * @throws IncrementalStorageException File target is not a directory
     */
    public void incrementalStorage(
        @NotNull StorageInfo storageInfo,
        Path storageDir,
        Path targetDir,
        IOFileFilter fileFilter,
        IOFileFilter dirFilter
    ) throws IncrementalStorageException, IOException {
        String name = storageInfo.getName();

        if (storageExists(name)) throw new IncrementalStorageException("Storage already exists");

        File storageFile = storageDir.toFile();
        File targetFile = targetDir.toFile();

        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        Collection<File> files = getDirectoryFiles(
            storageDir.toFile(),
            fileFilter, dirFilter
        );

        String latestBackupName = getLatestStorageName();
        Map<String, String> fileHashMap = getFileHashMap(files, "./", true, fileFilter, dirFilter);

        boolean isFirstIncrementalStorage = latestBackupName.isEmpty();
        if (isFirstIncrementalStorage) {
            FileUtils.copyDirectory(storageFile, targetFile, new AndFileFilter(fileFilter, dirFilter));
            database.insertFileHash(name, fileHashMap);
            database.insertIndexFile(name, new HashMap<>());
            writeStorageInfo(storageInfo);
            return;
        }

        Map<String, String> latestFileHashMap = database.getFileHashMap(latestBackupName);
        if (latestFileHashMap.isEmpty()) throw new NullPointerException("%s does not exist".formatted(latestBackupName));

        IndexFile latestIndexFile = database.getIndexFile(latestBackupName);
        Map<String, String> latestIndexFileMap = latestIndexFile.getIndexFileMap();

        Map<String, Object> resultMap = compareGetIndexFileMap(
            latestBackupName,
            fileHashMap,
            latestFileHashMap,
            latestIndexFileMap,
            new ArrayList<>(),
            storageDir,
            targetFile
        );

        Map<String, String> indexMap = new HashMap<>();
        if (resultMap.get("index") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String value) indexMap.put(key, value);
            });
        }

        List<String> indexList = new ArrayList<>();
        if (resultMap.get("list") instanceof List<?> list) {
            list.forEach(v -> {
                if (v instanceof String value) indexList.add(value);
            });
        }

        database.insertFileHash(name, fileHashMap);
        database.insertIndexFile(name, indexMap);
        writeStorageInfo(name, storageInfo.getDesc(), true, indexList);
    }

    /**
     * Storage full file and do not save hash.
     *
     * @param storageDir source directory
     * @param targetDir  target directory
     * @throws IncrementalStorageException File target is not a directory
     */
    public void fullStorage(
        StorageInfo storageInfo,
        @NotNull Path storageDir,
        Path targetDir
    ) throws IncrementalStorageException, IOException {
        File storageFile = storageDir.toFile();
        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        IOFileFilter filter = FileFilterUtils.trueFileFilter();

        fullStorage(storageInfo, storageDir, targetDir, filter);
    }

    /**
     * Storage full file and do not save hash.
     *
     * @param storageDir source directory
     * @param targetDir  target directory
     * @param fileFilter fileFilter
     * @throws IncrementalStorageException File target is not a directory
     */
    public void fullStorage(
        StorageInfo storageInfo,
        @NotNull Path storageDir,
        Path targetDir,
        IOFileFilter fileFilter
    ) throws IncrementalStorageException, IOException {
        String name = storageInfo.getName();

        if (storageExists(name)) throw new IncrementalStorageException("Storage already exists");

        File storageFile = storageDir.toFile();
        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        FileUtils.copyDirectory(storageFile, targetDir.toFile(), fileFilter);
        writeStorageInfo(storageInfo.getName(), storageInfo.getDesc(), false, new ArrayList<>());
    }
}
