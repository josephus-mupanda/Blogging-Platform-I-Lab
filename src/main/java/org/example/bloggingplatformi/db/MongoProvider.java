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
 * Owns the single {@link MongoClient} for the application.
 *
 * <p>{@code MongoClient} is already a thread-safe, pooled object. Creating one
 * per operation is a well-worn mistake: each instance starts its own
 * connection pool and monitoring threads, so the "lightweight" document store
 * ends up slower than the relational one for reasons that have nothing to do
 * with either engine. One client, reused, is the supported pattern.
 */
public final class MongoProvider {

    public static final String COMMENTS = "comments";
    public static final String REVIEWS = "reviews";

    private static volatile MongoClient client;
    private static volatile String databaseName;

    private MongoProvider() {
    }

    public static MongoClient client() {
        MongoClient local = client;
        if (local == null) {
            synchronized (MongoProvider.class) {
                local = client;
                if (local == null) {
                    local = build();
                    client = local;
                }
            }
        }
        return local;
    }

    private static MongoClient build() {
        AppConfig config = AppConfig.get();
        String uri = config.getString("db.mongo.uri");
        databaseName = config.getString("db.mongo.database");

        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    // Without a bound, an unreachable mongod leaves the caller
                    // waiting 30 seconds before it reports anything. On the FX
                    // thread that is indistinguishable from a freeze.
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

    /**
     * Round-trips a {@code ping} so a connection problem surfaces here, with a
     * clear message, rather than inside an unrelated query later.
     */
    public static boolean isReachable() {
        try {
            database().runCommand(new Document("ping", 1));
            return true;
        } catch (MongoException | DataAccessException e) {
            return false;
        }
    }

    public static boolean isInitialised() {
        return client != null;
    }

    public static synchronized void shutdown() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
