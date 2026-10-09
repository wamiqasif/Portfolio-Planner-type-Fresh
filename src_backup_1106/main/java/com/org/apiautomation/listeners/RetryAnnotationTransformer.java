package com.org.apiautomation.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Attaches {@link RetryAnalyzer} to every {@code @Test} method automatically,
 * so "a failed test gets retried" is a framework-wide default rather than
 * something each test method opts into with {@code @Test(retryAnalyzer =
 * RetryAnalyzer.class)} on every single method - exactly the kind of
 * per-test boilerplate the rest of this framework (auth applied
 * automatically, response logging applied automatically) was built to
 * eliminate. Without this listener, {@code RetryAnalyzer} would exist but
 * retry nothing, because nothing would be wired to use it.
 *
 * <p>Kept as its own class rather than a method on {@link TestLifecycleListener}
 * or {@link HttpLoggingFilter}: {@code IAnnotationTransformer} runs once per
 * test method, before any method has executed, to rewrite its metadata - a
 * fundamentally different extension point from {@code ITestListener} (test
 * lifecycle events) and {@code Filter} (around each HTTP call). Each of the
 * three listener classes in this package now has exactly one third-party
 * extension point to know about.
 */
public class RetryAnnotationTransformer implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
