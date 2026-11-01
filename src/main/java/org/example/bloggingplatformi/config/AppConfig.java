package org.example.bloggingplatformi.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralised, read-only access to {@code application.properties}.
 *
 * <p>Values resolve in order of decreasing specificity:
 * <ol>
 *   <li>JVM system property &mdash; {@code -Ddb.postgres.password=secret}</li>
 *   <li>Environment variable &mdash; {@code DB_POSTGRES_PASSWORD}
 *       (upper-cased, dots replaced by underscores)</li>
 *   <li>The bundled {@code application.properties}</li>
 * </ol>
 *
 * <p>That ordering is what keeps credentials out of version control: the
 * file holds development defaults, and a real deployment overrides them
 * without a rebuild.
 *
 * <p>Loading happens once, in a holder class, so it is both lazy and
 * thread-safe without explicit synchronisation.
 */
public final class AppConfig {

    private static final String RESOURCE = "/application.properties";

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    private static final class Holder {
        private static final AppConfig INSTANCE = load();
    }

    public static AppConfig get() {
        return Holder.INSTANCE;
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
     * @throws ConfigException if the key is absent everywhere. Failing loudly
     *         at startup beats a {@code null} surfacing inside a JDBC URL.
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

    public long getLong(String key, long defaultValue) {
        String value = resolve(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException(
                    "Configuration key '" + key + "' must be a long, was: " + value, e);
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

    /** {@code db.postgres.url} becomes {@code DB_POSTGRES_URL}. */
    static String toEnvKey(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase();
    }

    /** Thrown when configuration is missing or malformed. */
    public static class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
