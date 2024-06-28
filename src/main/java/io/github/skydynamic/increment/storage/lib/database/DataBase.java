package io.github.skydynamic.increment.storage.lib.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.h2.H2Backend;
import dev.morphia.Datastore;
import dev.morphia.DeleteOptions;
import dev.morphia.InsertOneOptions;
import dev.morphia.Morphia;
import dev.morphia.query.filters.Filters;
import io.github.skydynamic.increment.storage.lib.Interface.IConfig;
import io.github.skydynamic.increment.storage.lib.Interface.IDataBaseManager;
import io.github.skydynamic.increment.storage.lib.database.index.type.IndexFile;
import io.github.skydynamic.increment.storage.lib.logging.LogUtil;

import java.io.File;
import java.util.HashMap;

import org.slf4j.Logger;

public class DataBase {
    private MongoServer server;

    private Datastore datastore;

    MongoClient mongoClient;
    IDataBaseManager dataBaseManager;
    IConfig config;

    public Datastore getDatastore() {
        return datastore;
    }

    private static final InsertOneOptions INSERT_OPTIONS = new InsertOneOptions();
    private static final DeleteOptions DELETE_OPTIONS = new DeleteOptions();
    private static final Logger LOGGER = LogUtil.getLogger();

    public DataBase(IDataBaseManager dataBaseManager, IConfig config) {
        File dataBaseDir = dataBaseManager.getDataBasePath().toFile();
        if (!dataBaseDir.exists()) dataBaseDir.mkdirs();

        String connectionString = config.getMongoDBUri();
        if (config.getUserInternalDataBase()) {
            connectionString = startInternalMongoServer();
            LOGGER.info("Started local MongoDB server at " + server.getConnectionString());
        }

        mongoClient = MongoClients.create(connectionString);

        datastore = Morphia.createDatastore(mongoClient, dataBaseManager.getCollectionName());
    }

    public HashMap<String, String> getIndexFileMap(String name) {
        return datastore.find(IndexFile.class).filter(Filters.eq("name", name)).first().getIndexFileMap();
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

    public void stopInternalMongoServer() {
        if (mongoClient != null) mongoClient.close();
        if (server != null) server.shutdownNow();
        server = null;
    }
}
