package org.example.bloggingplatformi.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * The connection to MongoDB. One MongoClient is created the first time and
 * then reused, because it keeps its own pool of connections.
 */
public final class Mongo {

    private static MongoClient client;

    private Mongo() {
    }

    public static synchronized MongoDatabase database() {
        if (client == null) {
            client = MongoClients.create(AppConfig.get("db.mongo.uri"));
        }
        return client.getDatabase(AppConfig.get("db.mongo.database"));
    }

    /** Called when the app closes. */
    public static synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
