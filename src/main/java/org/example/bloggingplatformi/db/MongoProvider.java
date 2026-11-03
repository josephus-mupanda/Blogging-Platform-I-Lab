package org.example.bloggingplatformi.db;

import java.util.concurrent.TimeUnit;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;

import org.example.bloggingplatformi.config.AppConfig;
import org.example.bloggingplatformi.exception.DataAccessException;

/**
 * Holds the one MongoClient used by the whole app. A MongoClient has its own
 * connection pool and is thread-safe, so it is created once and reused.
 */
public final class MongoProvider {

    public static final String COMMENTS = "comments";
    public static final String REVIEWS = "reviews";

    private static MongoClient client;
    private static String databaseName;

    private MongoProvider() {
    }

    // Creates the client the first time it is needed.
    public static synchronized MongoClient client() {
        if (client == null) {
            client = build();
        }
        return client;
    }

    private static MongoClient build() {
        AppConfig config = AppConfig.get();
        String uri = config.getString("db.mongo.uri");
        databaseName = config.getString("db.mongo.database");

        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    // Give up after 5 seconds if MongoDB is down. The default is 30 seconds.
                    .applyToClusterSettings(b ->
                            b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                    .applyToSocketSettings(b ->
                            b.connectTimeout(5, TimeUnit.SECONDS))
                    .build();
            return MongoClients.create(settings);
        } catch (RuntimeException e) {
            throw new DataAccessException(
                    "Could not create the MongoDB client. Check db.mongo.uri and that "
                    + "mongod is running.", e);
        }
    }

    public static MongoDatabase database() {
        MongoClient mongo = client();
        return mongo.getDatabase(databaseName);
    }

    public static MongoCollection<Document> collection(String name) {
        return database().getCollection(name);
    }

    public static MongoCollection<Document> comments() {
        return collection(COMMENTS);
    }

    public static MongoCollection<Document> reviews() {
        return collection(REVIEWS);
    }

    /** Sends a ping to check that MongoDB is up before we run real queries. */
    public static boolean isReachable() {
        try {
            database().runCommand(new Document("ping", 1));
            return true;
        } catch (MongoException | DataAccessException e) {
            return false;
        }
    }

    public static synchronized boolean isInitialised() {
        return client != null;
    }

    public static synchronized void shutdown() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
