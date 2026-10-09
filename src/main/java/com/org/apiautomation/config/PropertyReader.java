package com.org.apiautomation.config;

import com.org.apiautomation.exceptions.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads configuration values. Owns two things: loading the classpath
 * properties file for a given environment, and resolving an individual key
 * out of it.
 *
 * <p>Key resolution always checks a JVM system property of the same name
 * first, before falling back to the properties file. This lets CI override
 * a single value (typically a secret, e.g. {@code -Dauth.password=...}
 * sourced from a pipeline secret store) without editing or committing the
 * checked-in {@code .properties} file.
 */
public final class PropertyReader {

    private static final Logger LOGGER = LogManager.getLogger(PropertyReader.class);
    private static final String CONFIG_PATH_TEMPLATE = "config/%s.properties";

    private PropertyReader() {
    }

    /**
     * Loads {@code config/<environment>.properties} from the classpath.
     *
     * @throws ConfigurationException if the file does not exist or cannot be read
     */
    public static Properties load(String environment) {
        String resourcePath = CONFIG_PATH_TEMPLATE.formatted(environment);

        try (InputStream inputStream = PropertyReader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new ConfigurationException("Configuration file not found on classpath: " + resourcePath);
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            LOGGER.info("Loaded {} propert{} from '{}'",
                    properties.size(), properties.size() == 1 ? "y" : "ies", resourcePath);
            return properties;

        } catch (IOException e) {
            throw new ConfigurationException("Failed to read configuration file: " + resourcePath, e);
        }
    }

    /**
     * Returns the value for {@code key}.
     *
     * @throws ConfigurationException if the key is not set as a system property
     *                                 and not present in {@code properties}
     */
    public static String get(Properties properties, String key) {
        String value = resolve(properties, key);
        if (value == null) {
            throw new ConfigurationException("Missing required configuration key: '" + key + "'");
        }
        return value;
    }

    /**
     * Returns the value for {@code key}, or {@code defaultValue} if it is set
     * nowhere (neither system property nor properties file).
     */
    public static String get(Properties properties, String key, String defaultValue) {
        String value = resolve(properties, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the value for {@code key} parsed as an {@code int}.
     *
     * @throws ConfigurationException if the key is missing, or its value is not a valid integer
     */
    public static int getInt(Properties properties, String key) {
        String value = get(properties, key);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "Configuration key '" + key + "' must be an integer, but was: '" + value + "'", e);
        }
    }

    /**
     * Returns the value for {@code key} parsed as an {@code int}, or
     * {@code defaultValue} if it is set nowhere.
     */
    public static int getInt(Properties properties, String key, int defaultValue) {
        String value = resolve(properties, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "Configuration key '" + key + "' must be an integer, but was: '" + value + "'", e);
        }
    }

    private static String resolve(Properties properties, String key) {
        String systemPropertyOverride = System.getProperty(key);
        if (!isBlank(systemPropertyOverride)) {
            return systemPropertyOverride.trim();
        }

        String value = properties.getProperty(key);
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
