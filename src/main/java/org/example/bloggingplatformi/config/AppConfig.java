package org.example.bloggingplatformi.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads settings from application.properties. A JVM system property
 * (-Ddb.postgres.password=x) wins first, then an environment variable
 * (DB_POSTGRES_PASSWORD), then the file. This keeps real passwords out of git.
 */
public final class AppConfig {

    private static final String RESOURCE = "/application.properties";

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    private static AppConfig instance;

    /** Reads the file the first time, then returns the same object every time. */
    public static synchronized AppConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AppConfig load() {
        Properties loaded = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new ConfigException(
                        RESOURCE + " not found on the classpath. "
                        + "It must sit in src/main/resources.");
            }
            loaded.load(in);
        } catch (IOException e) {
            throw new ConfigException("Could not read " + RESOURCE, e);
        }
        return new AppConfig(loaded);
    }

    /**
     * Returns a required setting.
     *
     * @throws ConfigException if the key is not set anywhere, so the app
     *         fails early with a clear message
     */
    public String getString(String key) {
        String value = resolve(key);
        if (value == null || value.isBlank()) {
            throw new ConfigException("Missing required configuration key: " + key);
        }
        return value.trim();
    }

    public String getString(String key, String defaultValue) {
        String value = resolve(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    public int getInt(String key, int defaultValue) {
        String value = resolve(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException(
                    "Configuration key '" + key + "' must be an integer, was: " + value, e);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = resolve(key);
        return (value == null || value.isBlank())
                ? defaultValue
                : Boolean.parseBoolean(value.trim());
    }

    private String resolve(String key) {
        String fromSystem = System.getProperty(key);
        if (fromSystem != null) {
            return fromSystem;
        }
        String fromEnv = System.getenv(toEnvKey(key));
        if (fromEnv != null) {
            return fromEnv;
        }
        return properties.getProperty(key);
    }

    /** Turns a key into an environment variable name: db.postgres.url becomes DB_POSTGRES_URL. */
    static String toEnvKey(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase();
    }

    /** Thrown when a setting is missing or has the wrong format. */
    public static class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
