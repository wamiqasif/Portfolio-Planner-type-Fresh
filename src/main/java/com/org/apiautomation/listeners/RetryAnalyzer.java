package com.org.apiautomation.listeners;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries a failed test method up to a small, fixed number of times before
 * letting it fail for good.
 *
 * <p><b>"Retry only failed tests" is inherent to {@code IRetryAnalyzer}, not
 * something this class has to check for itself:</b> TestNG calls {@link
 * #retry} only after a test method has already thrown - a passing or
 * skipped test never reaches this class at all.
 *
 * <p><b>Fixed constant, not read from config:</b> unlike a base URL or HTTP
 * timeout, "how many times to retry a flaky test" is the same policy
 * regardless of which environment is under test - there's no per-environment
 * value this would need to vary by, so adding a config key for it would be
 * a key with one hardcoded caller in practice.
 *
 * <p><b>Marks a to-be-retried attempt {@code SKIP}, not left as {@code
 * FAILED}:</b> without this, a test that fails once and passes on retry
 * would show as both FAIL and PASS for the same test method in {@code
 * TestLifecycleListener}'s Extent report and logs - confusing, and it misreports a
 * test that ultimately passed as having failed. Marking the attempts that
 * still have retries remaining as SKIP means only the true final outcome -
 * the eventual PASS, or the FAIL once retries are exhausted - is what shows
 * as the test's result.
 *
 * <p>TestNG constructs one instance of this class per test method (verified
 * directly rather than assumed - see the framework's verification notes),
 * so tracking {@code retryCount} as instance state here is safe without any
 * synchronization: no two test methods, and no two invocations of the same
 * method under {@code parallel="classes"}, ever share one instance.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRY_COUNT = 2;

    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount >= MAX_RETRY_COUNT) {
            return false;
        }
        retryCount++;
        result.setStatus(ITestResult.SKIP);
        return true;
    }
}
