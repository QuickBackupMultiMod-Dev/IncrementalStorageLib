package io.github.skydynamic.increment.storage.lib.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.h2.H2Backend;
import dev.morphia.Datastore;
import dev.morphia.DeleteOptions;
import dev.morphia.InsertOneOptions;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.query.filters.Filters;
import io.github.skydynamic.increment.storage.lib.Interface.IConfig;
import io.github.skydynamic.increment.storage.lib.Interface.IDataBaseManager;
import io.github.skydynamic.increment.storage.lib.database.index.type.IndexFile;
import io.github.skydynamic.increment.storage.lib.database.index.type.StorageInfo;
import io.github.skydynamic.increment.storage.lib.logging.LogUtil;

import java.io.File;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

@SuppressWarnings("unused")
public class DataBase {
    private static final List<String> DB_NAME_NOT_ALLOW_SYMBOL =
        Arrays.asList(".", "=", ">", "<", "'", "\"", "/", "\\");

    private MongoServer server;

    @Getter
    private Datastore datastore;

    MongoClient mongoClient;
    IDataBaseManager dataBaseManager;
    IConfig config;

    private static final InsertOneOptions INSERT_OPTIONS = new InsertOneOptions();
    private static final DeleteOptions DELETE_OPTIONS = new DeleteOptions();
    private static final Logger LOGGER = LogUtil.getLogger();

    public DataBase(@NotNull IDataBaseManager dataBaseManager, IConfig config) {
        this.dataBaseManager = dataBaseManager;
        this.config = config;

        File dataBaseDir = dataBaseManager.getDataBasePath().toFile();
        if (!dataBaseDir.exists()) dataBaseDir.mkdirs();

        String connectionString = config.getMongoDBUri();
        if (config.getUserInternalDataBase()) {
            connectionString = startInternalMongoServer();
            LOGGER.info("Started local MongoDB server at " + server.getConnectionString());
        }

        mongoClient = MongoClients.create(connectionString);

        MapperOptions mapperOptions = MapperOptions.builder()
            .storeEmpties(true)
            .storeNulls(true)
            .build();

        datastore = Morphia.createDatastore(mongoClient, fixDbName(dataBaseManager.getCollectionName()), mapperOptions);
    }

    public Map<String, String> getIndexFileMap(String name) {
        return datastore.find(IndexFile.class).filter(Filters.eq("name", name)).first().getIndexFileMap();
    }

    public StorageInfo getStorageInfo(String name) {
        return datastore.find(StorageInfo.class).filter(Filters.eq("name", name)).first();
    }

    public <T> void save (T obj) {
        getDatastore().save(obj, INSERT_OPTIONS);
    }

    public <T> void delete (T obj) {
        getDatastore().delete(obj, DELETE_OPTIONS);
    }

    private String startInternalMongoServer() {
        server = new MongoServer(new H2Backend(
                dataBaseManager.getDataBasePath()
                    + "/"
                    + dataBaseManager.getFileName()
                    + ".mv"
            ));
        server.bind();
        return server.getConnectionString();
    }

    private String fixDbName(String s) {
        s = s.replace(" ", "");
        for (String string : DB_NAME_NOT_ALLOW_SYMBOL) {
            s = s.replace(string, "-");
        }
        return s;
    }

    public void stopInternalMongoServer() {
        if (mongoClient != null) mongoClient.close();
        if (server != null) server.shutdownNow();
        server = null;
    }
}
