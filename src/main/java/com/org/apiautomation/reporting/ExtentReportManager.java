package com.org.apiautomation.reporting;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.org.apiautomation.config.ConfigManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Owns the single {@link ExtentReports} instance for the whole run: builds
 * it once, hands out per-test report nodes, flushes it once at the end.
 *
 * <p>Singleton (same initialization-on-demand holder idiom as {@code
 * ConfigManager}/{@code TokenManager}): exactly one HTML report must span
 * every test class and every parallel thread in the run - not one per class,
 * which is what would happen without a shared instance.
 *
 * <p>The report filename is timestamped ({@code ExtentReport_<timestamp>.html})
 * so successive local runs and CI builds don't overwrite each other's
 * report - the CI workflow already archives whatever ends up under {@code
 * reports/} as a build artifact.
 *
 * <p>Per-test node tracking uses a {@code ThreadLocal<ExtentTest>}, not a
 * single field: {@code testng.xml} runs with {@code parallel="classes"}, so
 * multiple threads are each "the current test" simultaneously from their
 * own point of view. A single shared field would let one thread's test
 * silently receive another thread's report steps.
 */
public final class ExtentReportManager {

    private static final String REPORT_DIR = "reports";
    private static final DateTimeFormatter FILE_TIMESTAMP_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final class Holder {
        private static final ExtentReportManager INSTANCE = new ExtentReportManager();
    }

    public static ExtentReportManager getInstance() {
        return Holder.INSTANCE;
    }

    private final ExtentReports extentReports;
    private final ThreadLocal<ExtentTest> currentTest = new ThreadLocal<>();

    private ExtentReportManager() {
        this.extentReports = buildExtentReports();
    }

    private ExtentReports buildExtentReports() {
        String reportPath = REPORT_DIR + "/ExtentReport_" + LocalDateTime.now().format(FILE_TIMESTAMP_PATTERN) + ".html";
        ExtentSparkReporter sparkReporter = new ExtentSparkReporter(reportPath);

        ExtentReports extent = new ExtentReports();
        extent.attachReporter(sparkReporter);

        // Makes the report self-describing: opening it answers "which
        // environment did this run hit" without cross-referencing logs.
        ConfigManager config = ConfigManager.getInstance();
        extent.setSystemInfo("Environment", config.getEnvironment());
        extent.setSystemInfo("Base URL", config.getBaseUrl());

        return extent;
    }

    /** Creates a fresh report node for the calling thread's current test and registers it as "current". */
    public ExtentTest startTest(String testName) {
        ExtentTest test = extentReports.createTest(testName);
        currentTest.set(test);
        return test;
    }

    /** The calling thread's current test's report node, or {@code null} if none is active. */
    public ExtentTest getCurrentTest() {
        return currentTest.get();
    }

    /**
     * Clears the calling thread's current-test reference. Not strictly
     * required for correctness (the next test on this thread calls {@link
     * #startTest} again before logging anything), but prevents an HTTP call
     * made between tests (e.g. in an {@code @AfterMethod}) from being
     * attributed to a test that has already finished, and avoids holding a
     * reference to a finished test's node for the rest of the run.
     */
    public void endTest() {
        currentTest.remove();
    }

    /** Writes the report to disk. Must be called exactly once, after every test has finished. */
    public void flush() {
        extentReports.flush();
    }
}
