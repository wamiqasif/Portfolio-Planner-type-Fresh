package com.org.apiautomation.exceptions;

/**
 * Thrown when configuration cannot be resolved or loaded - an unsupported
 * environment name, a missing classpath properties file, or a missing/
 * malformed required key. Kept distinct from a generic {@link RuntimeException}
 * so that a broken environment setup is visibly different, in logs and in the
 * report, from an actual API test assertion failure.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
