package io.github.skydynamic.increment.storage.lib.util;

import com.mongodb.client.MongoCollection;
import dev.morphia.query.filters.Filters;
import io.github.skydynamic.increment.storage.lib.database.DataBase;
import io.github.skydynamic.increment.storage.lib.Interface.IConfig;
import io.github.skydynamic.increment.storage.lib.Interface.IDataBaseManager;
import io.github.skydynamic.increment.storage.lib.database.index.type.DataBaseTypes;
import io.github.skydynamic.increment.storage.lib.database.index.type.FileHash;
import io.github.skydynamic.increment.storage.lib.database.index.type.IndexFile;
import io.github.skydynamic.increment.storage.lib.database.index.type.StorageInfo;
import io.github.skydynamic.increment.storage.lib.exception.IncrementalStorageException;
import io.github.skydynamic.increment.storage.lib.logging.LogUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.bson.Document;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class Storager {
    private static final Logger LOGGER = LogUtil.getLogger();
    private static final HashMap<String, String> EMPTY_HASH_MAP = new HashMap<>();

    private DataBase dataBase;
    private IConfig config;
    private IDataBaseManager dataBaseManager;

    public void startAndSetDataBase() {
        if (dataBase == null) {
            dataBase = new DataBase(dataBaseManager, config);
        }
    }

    public void deleteStorage(String name) {
        for (DataBaseTypes type : DataBaseTypes.values()) {
            MongoCollection<Document> collection = dataBase.getDatastore().getDatabase().getCollection(type.type);
            collection.findOneAndDelete(com.mongodb.client.model.Filters.eq("name", name));
        }
    }

    private void writeStorageInfo(
        String name, String desc,
        boolean useIncrementalStorage, List<String> indexBackupList
    ) {
        dataBase.save(
            new StorageInfo(
                name, desc,
                System.currentTimeMillis(), useIncrementalStorage,
                indexBackupList
            )
        );
    }

    private void writeStorageInfo(StorageInfo storageInfo) {
        dataBase.save(storageInfo);
    }

    private void makeDirAndCopyToDir(File source, File dest) throws IOException {
        if (!dest.exists()) {
            dest.mkdirs();
        }

        FileUtils.copyFileToDirectory(source, dest);
    }

    private Collection<File> getDirectoryFiles(
        File file,
        IOFileFilter fileFilter,
        IOFileFilter dirFilter
    ) {
        return FileUtils.listFiles(file, fileFilter, dirFilter);
    }

    private String getFileHash(File file) {
        try {
            return HashUtil.getFileHash(file.toPath());
        } catch (IOException e) {
            LOGGER.error(e.toString());
            return "";
        }
    }

    private HashMap<String, String> getFileHashMap(
        Collection<File> files,
        IOFileFilter fileFilter,
        IOFileFilter dirFilter
    ) {
        HashMap<String, String> map = new HashMap<>();
        for (File file : files) {
            if (file.isDirectory()) {
                Collection<File> dirFiles = getDirectoryFiles(file, fileFilter, dirFilter);
                HashMap<String, String> appendMap = getFileHashMap(dirFiles, fileFilter, dirFilter);
                map.putAll(appendMap);
            } else {
                String fileHash = getFileHash(file);
                String key = file.getParent().replace(File.separator, ".") + "#" + file.getName();
                map.put(key, fileHash);
            }
        }
        return map;
    }

    private <T extends HashMap<String, String>> HashMap<String, Object> compareGetIndexFileMap(
        String latestStorageName,
        T newStorageHashMap,
        T latestStorageHashMap,
        T latestStorageIndexFileMap,
        List<String> indexStorageList,
        File destCopyDir
    ) throws IOException {
        HashMap<String, String> indexMap = new HashMap<>();
        for (String fileKey : newStorageHashMap.keySet()) {
            String newHashValue = newStorageHashMap.get(fileKey);
            String latestHashValue = latestStorageHashMap.get(fileKey);
            if (newHashValue.equals(latestHashValue)) {
                String index = latestStorageIndexFileMap.get(fileKey);
                if (index.isEmpty()) index = latestStorageName;
                indexMap.put(fileKey, index);
                if (!indexStorageList.contains(index)) indexStorageList.add(index);
            } else {
                File sourceFile =
                    new File(fileKey.replace(".", File.separator).replace("#", File.separator));
                makeDirAndCopyToDir(sourceFile, destCopyDir);
            }
        }
        HashMap<String, Object> returnMap = new HashMap<>();
        returnMap.put("index", indexMap);
        returnMap.put("list", indexStorageList);
        return returnMap;
    }

    private String getLatestStorageName() {
        List<StorageInfo> infoList = dataBase.getDatastore().find(StorageInfo.class)
            .stream()
            .filter(StorageInfo::isUseIncrementalStorage)
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
     * Calculate the file hash value and compare it with the latest storage's files.
     * Then copy the files with differences
     *
     * @param storageDir source directory
     * @param targetDir target directory
     * @throws IncrementalStorageException File target is not a directory
     */
    public void incrementalStorage(
        StorageInfo storageInfo,
        Path storageDir,
        Path targetDir
    ) throws IncrementalStorageException, IOException {
        File storageFile = storageDir.toFile();
        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        incrementalStorage(storageInfo, storageDir, targetDir, null, null);
    }

    /**
     * Calculate the file hash value and compare it with the latest storage's files.
     * Then copy the files with differences
     *
     * @param storageDir source directory
     * @param targetDir target directory
     * @param fileFilter fileFilter
     * @param dirFilter  dirFilter
     * @throws IncrementalStorageException File target is not a directory
     */
    public void incrementalStorage(
        StorageInfo storageInfo,
        Path storageDir,
        Path targetDir,
        IOFileFilter fileFilter,
        IOFileFilter dirFilter
    ) throws IncrementalStorageException, IOException {
        String name = storageInfo.getName();

        if (!dataBase.getDatastore()
                .find(StorageInfo.class)
                .filter(Filters.eq("name", name))
                .stream().toList().isEmpty()
        ) throw new IncrementalStorageException("Storage already exists");

        File storageFile = storageDir.toFile();
        File targetFile = targetDir.toFile();

        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        Collection<File> files = getDirectoryFiles(
            storageDir.toFile(),
            fileFilter,
            dirFilter
        );

        String latestBackupName = getLatestStorageName();
        HashMap<String, String> fileHashMap = getFileHashMap(files, fileFilter, dirFilter);

        boolean isFirstIncrementalStorage = latestBackupName.isEmpty();
        if (isFirstIncrementalStorage) {
            FileUtils.copyDirectory(storageFile, targetFile, fileFilter);
            dataBase.save(new FileHash(name, fileHashMap));
            dataBase.save(new IndexFile(name, EMPTY_HASH_MAP));
            writeStorageInfo(storageInfo);
            return;
        }

        HashMap<String, String> latestFileHashMap = dataBase.getDatastore()
            .find(FileHash.class)
            .filter(Filters.eq("name", latestBackupName))
            .first()
            .getFileHashMap();
        HashMap<String, String> latestIndexFileMap = dataBase.getDatastore()
            .find(IndexFile.class)
            .filter(Filters.eq("name", latestBackupName))
            .first()
            .getIndexFileMap();

        HashMap<String, Object> resultMap = compareGetIndexFileMap(
            latestBackupName,
            fileHashMap,
            latestFileHashMap,
            latestIndexFileMap,
            new ArrayList<>(),
            targetFile
        );
        HashMap<String, String> indexMap = (HashMap<String, String>) resultMap.get("index");
        List<String> indexList = (List<String>) resultMap.get("list");

        dataBase.save(new FileHash(name, fileHashMap));
        dataBase.save(new IndexFile(name, indexMap));
        writeStorageInfo(name, storageInfo.getDesc(), true, indexList);
    }

    /**
     * Storage full file and do not save hash.
     *
     * @param storageDir source directory
     * @param targetDir target directory
     * @throws IncrementalStorageException File target is not a directory
     */
    public void fullStorage(
        StorageInfo storageInfo,
        Path storageDir,
        Path targetDir
    ) throws IncrementalStorageException, IOException {
        File storageFile = storageDir.toFile();
        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        fullStorage(storageInfo, storageDir, targetDir, null);
    }

    /**
     * Storage full file and do not save hash.
     *
     * @param storageDir source directory
     * @param targetDir target directory
     * @param fileFilter fileFilter
     * @throws IncrementalStorageException File target is not a directory
     */
    public void fullStorage(
        StorageInfo storageInfo,
        Path storageDir,
        Path targetDir,
        IOFileFilter fileFilter
    ) throws IncrementalStorageException, IOException {
        File storageFile = storageDir.toFile();
        if (!storageFile.isDirectory()) {
            throw new IncrementalStorageException("Target is not a directory");
        }

        FileUtils.copyDirectory(storageFile, targetDir.toFile(), fileFilter);
        writeStorageInfo(storageInfo.getName(), storageInfo.getDesc(), false, new ArrayList<>());
    }
}