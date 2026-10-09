package com.org.apitests.base;

import com.org.apiautomation.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeSuite;

/**
 * The one class every test class in the suite extends. Deliberately holds a
 * single lifecycle method and nothing else - no fields, no HTTP client, no
 * service objects.
 *
 * <p><b>Why inheritance, and why this doesn't grow into a deeper hierarchy
 * (e.g. a {@code BaseApiTest} on top of this):</b> TestNG runs annotated
 * lifecycle methods on a base class automatically for every subclass, with
 * zero effort from the subclass. That's the one thing composition can't
 * give you here without every test class remembering to call an init method
 * itself - exactly the kind of repeated boilerplate the rest of this
 * framework (auth applied automatically in {@code RequestSpecFactory},
 * response logging applied automatically in {@code ApiClient}) was built to
 * eliminate. A second inheritance level would only be justified once there's
 * shared state actually worth caching per test class - e.g. pre-built
 * service objects once a services layer exists. Nothing in the framework
 * needs that yet, so this stays a single, flat level of inheritance.
 */
public abstract class BaseTest {

    private static final Logger LOGGER = LogManager.getLogger(BaseTest.class);

    /**
     * Test initialization and configuration loading, in one method.
     *
     * <p><b>Why {@code @BeforeSuite}, not {@code @BeforeClass} or
     * {@code @BeforeMethod}:</b> configuration is a one-time, suite-wide
     * concern - it should run exactly once regardless of how many test
     * classes exist or how many run in parallel (see {@code testng.xml}'s
     * {@code parallel="classes"}). {@code @BeforeClass} would repeat this
     * once per class; {@code @BeforeMethod} once per test method - both
     * wrong for something that isn't per-class or per-method state.
     *
     * <p><b>Why it exists at all, given {@code ConfigManager} already
     * lazy-loads itself on first access:</b> without this, configuration
     * would load lazily whenever the first test happens to make its first
     * config call - which could be several classes and several minutes into
     * a parallel run. A bad environment value or missing required config key
     * would then surface as a {@code ConfigurationException} deep inside an
     * arbitrary test, misread as that test's own failure. Calling {@code
     * ConfigManager.getInstance()} here forces that failure to happen at
     * suite start, loudly, before any test runs at all - fail fast instead of
     * fail confusing.
     *
     * <p>{@code alwaysRun = true}: a defensive default for a method other
     * classes inherit - it keeps this running even if some other
     * {@code @BeforeSuite} method (in a class that also extends this one)
     * threw before it. There is only one {@code @BeforeSuite} method in the
     * codebase today, so this has no visible effect yet; it costs nothing to
     * set correctly now rather than discover the gap later.
     */
    @BeforeSuite(alwaysRun = true)
    public void initializeTestSuite() {
        ConfigManager config = ConfigManager.getInstance();
        LOGGER.info("Test suite starting against environment '{}' (base URL: {})",
                config.getEnvironment(), config.getBaseUrl());
    }
}
