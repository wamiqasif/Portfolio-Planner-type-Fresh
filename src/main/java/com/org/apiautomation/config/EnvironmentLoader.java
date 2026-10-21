package com.org.apiautomation.config;

import com.org.apiautomation.exceptions.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

/**
 * Resolves which environment is active for the current run. Nothing here
 * knows how to load a properties file - it only decides the environment
 * name, in this order:
 *
 * <ol>
 *     <li>{@code -Denv=<environment>} system property</li>
 *     <li>{@code ENV} operating-system environment variable</li>
 *     <li>default: {@code qa}</li>
 * </ol>
 *
 * Kept separate from {@link PropertyReader} so the precedence rule above is
 * independently readable and testable, without dragging in file I/O.
 */
public final class EnvironmentLoader {

    private static final Logger LOGGER = LogManager.getLogger(EnvironmentLoader.class);

    public static final String ENV_SYSTEM_PROPERTY = "env";
    public static final String ENV_ENVIRONMENT_VARIABLE = "ENV";
    public static final String DEFAULT_ENVIRONMENT = "qa";

    private static final Set<String> SUPPORTED_ENVIRONMENTS = Set.of("dev", "qa", "stage", "prod", "uat");

    private EnvironmentLoader() {
    }

    /**
     * Resolves and validates the active environment name.
     *
     * @throws ConfigurationException if the resolved name is not one of the
     *                                 supported environments
     */
    public static String resolveEnvironment() {
        String value = System.getProperty(ENV_SYSTEM_PROPERTY);
        String source = "system property '-D" + ENV_SYSTEM_PROPERTY + "'";

        if (isBlank(value)) {
            value = System.getenv(ENV_ENVIRONMENT_VARIABLE);
            source = "environment variable '" + ENV_ENVIRONMENT_VARIABLE + "'";
        }

        if (isBlank(value)) {
            value = DEFAULT_ENVIRONMENT;
            source = "default";
        }

        String environment = value.trim().toLowerCase();

        if (!SUPPORTED_ENVIRONMENTS.contains(environment)) {
            throw new ConfigurationException(
                    "Unsupported environment '" + environment + "' (resolved via " + source + "). "
                            + "Supported environments are: " + SUPPORTED_ENVIRONMENTS);
        }

        LOGGER.info("Resolved active environment '{}' via {}", environment, source);
        return environment;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
