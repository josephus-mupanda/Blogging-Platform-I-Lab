package org.example.bloggingplatformi.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Reads the settings in src/main/resources/application.properties. */
public final class AppConfig {

    private static final Properties SETTINGS = load();

    private AppConfig() {
    }

    /**
     * Returns a setting, for example get("db.postgres.url").
     * A -Ddb.postgres.url=... option or a DB_POSTGRES_URL environment variable
     * wins over the file, so a password does not have to be written in the file.
     */
    public static String get(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key.replace('.', '_').toUpperCase());
        }
        if (value == null) {
            value = SETTINGS.getProperty(key);
        }
        if (value == null) {
            throw new IllegalStateException("Missing setting in application.properties: " + key);
        }
        return value;
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream file = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (file != null) {
                properties.load(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read application.properties", e);
        }
        return properties;
    }
}
