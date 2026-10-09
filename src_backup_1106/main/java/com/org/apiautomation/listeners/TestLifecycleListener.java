package com.org.apiautomation.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.org.apiautomation.reporting.ExtentReportManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.function.Consumer;

/**
 * Drives the Extent report's per-test lifecycle: opens a report node when a
 * test starts, marks it pass/fail/skip when the test ends, flushes the
 * report once the run finishes. The HTTP-level detail (request/response/
 * headers/body/time) is a different concern, handled by {@link
 * HttpLoggingFilter} - see that class's Javadoc for why these were split
 * out of what was originally one combined class.
 *
 * <p>Holds no state of its own; all of it (the {@code ExtentReports}
 * instance, which {@code ExtentTest} node is "current" per thread) lives in
 * {@link ExtentReportManager}, which both this class and {@link
 * HttpLoggingFilter} read/write independently.
 */
public final class TestLifecycleListener implements ITestListener {

    private static final Logger LOGGER = LogManager.getLogger(TestLifecycleListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        ExtentReportManager.getInstance().startTest(testName);
        LOGGER.info("=== Starting test: {} ===", testName);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        long elapsedMs = result.getEndMillis() - result.getStartMillis();
        LOGGER.info("=== Test passed: {} ({} ms) ===", result.getMethod().getMethodName(), elapsedMs);
        withCurrentTest(test -> test.pass("Test passed in " + elapsedMs + " ms"));
        ExtentReportManager.getInstance().endTest();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.error("=== Test failed: {} ===", result.getMethod().getMethodName(), result.getThrowable());
        withCurrentTest(test -> test.fail(result.getThrowable()));
        ExtentReportManager.getInstance().endTest();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOGGER.warn("=== Test skipped: {} ===", result.getMethod().getMethodName());
        withCurrentTest(test -> test.skip("Test skipped"));
        ExtentReportManager.getInstance().endTest();
    }

    @Override
    public void onFinish(ITestContext context) {
        // testng.xml/suites have exactly one <test> block per run, so this
        // firing once per <test> tag is once per run - no ISuiteListener
        // needed on top of this for that guarantee.
        ExtentReportManager.getInstance().flush();
    }

    private void withCurrentTest(Consumer<ExtentTest> action) {
        ExtentTest test = ExtentReportManager.getInstance().getCurrentTest();
        if (test != null) {
            action.accept(test);
        }
    }
}
