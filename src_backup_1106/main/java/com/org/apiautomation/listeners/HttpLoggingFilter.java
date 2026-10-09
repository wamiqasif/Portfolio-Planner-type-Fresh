package com.org.apiautomation.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.reporting.ExtentReportManager;
import com.org.apiautomation.utils.SensitiveDataMasker;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Fires around every HTTP call made through {@code ApiClient}: logs
 * request/response/headers/body/execution-time to Log4j2, and attaches the
 * same detail as a step on the current test's Extent report node (if one
 * is active).
 *
 * <p><b>Why this is a separate class from {@link TestLifecycleListener},
 * where an earlier version combined both into one {@code ReportListener}:
 * </b> a Rest Assured {@link Filter} and a TestNG {@code ITestListener} are
 * unrelated extension points from two different libraries, triggered by two
 * different things (an HTTP call vs. a test method's lifecycle). Combining
 * them was originally justified as "both need the same shared state" - but
 * since neither ever held any state of its own (everything lives in {@link
 * ExtentReportManager}), that justification didn't actually require one
 * class. Splitting them costs nothing and means each class has exactly one
 * reason to change, and one framework's API to know about.
 *
 * <p>Holds no state of its own, for the same reason: it's constructed fresh
 * by {@code core.RequestSpecFactory} on every request spec, and reads/writes
 * only through {@link ExtentReportManager}.
 */
public final class HttpLoggingFilter implements Filter {

    private static final Logger LOGGER = LogManager.getLogger(HttpLoggingFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_LOGGED_BODY_LENGTH = 4000;

    // Header values that must never reach a log file or the HTML report -
    // both are routinely archived as CI artifacts, which would otherwise
    // turn every logged call into a leaked credential. "auth-token" is the
    // VR login flow's session token header (auth.DynamicLoginAuthStrategy) -
    // functionally equivalent to Authorization for masking purposes here.
    private static final Set<String> ALWAYS_REDACTED_HEADERS = Set.of("authorization", "cookie", "set-cookie", "auth-token");

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                            FilterableResponseSpecification responseSpec,
                            FilterContext ctx) {
        String method = requestSpec.getMethod();
        String uri = requestSpec.getURI();
        String requestHeaders = formatHeaders(requestSpec.getHeaders());
        String requestBody = describeRequestBody(requestSpec.getBody());

        LOGGER.info("--> {} {}", method, uri);
        LOGGER.info("Request Headers:\n{}", requestHeaders);
        LOGGER.info("Request Body: {}", requestBody);

        Response response = ctx.next(requestSpec, responseSpec);

        // Rest Assured already measures this itself (a TimingFilter runs on
        // every call by default) - reusing response.getTime() instead of
        // hand-rolling a System.currentTimeMillis() stopwatch here avoids
        // duplicating timing logic Rest Assured already gets right.
        long executionTimeMs = response.getTime();
        String responseHeaders = formatHeaders(response.getHeaders());
        String responseBody = truncate(response.getBody().asString());

        LOGGER.info("<-- {} {} ({} ms)", response.getStatusCode(), uri, executionTimeMs);
        LOGGER.info("Response Headers:\n{}", responseHeaders);
        LOGGER.info("Response Body: {}", responseBody);

        reportStep(method, uri, requestHeaders, requestBody,
                response.getStatusCode(), responseHeaders, responseBody, executionTimeMs);

        return response;
    }

    // Shared formatting feeds both Log4j2 and the Extent report, so
    // redaction/truncation logic exists in exactly one place.

    private void reportStep(String method, String uri, String requestHeaders, String requestBody,
                             int statusCode, String responseHeaders, String responseBody, long executionTimeMs) {
        withCurrentTest(test -> test.info(String.format(
                "<b>%s %s</b> &rarr; <b>%d</b> (%d ms)<br>"
                        + "<u>Request Headers</u><pre>%s</pre>"
                        + "<u>Request Body</u><pre>%s</pre>"
                        + "<u>Response Headers</u><pre>%s</pre>"
                        + "<u>Response Body</u><pre>%s</pre>",
                escapeHtml(method), escapeHtml(uri), statusCode, executionTimeMs,
                escapeHtml(requestHeaders), escapeHtml(requestBody),
                escapeHtml(responseHeaders), escapeHtml(responseBody))));
    }

    private void withCurrentTest(Consumer<ExtentTest> action) {
        // No test context (e.g. a call made outside a TestNG test, or
        // OAuth2's own token-fetch call which deliberately never goes
        // through this filter) - the Log4j2 lines above still happened;
        // there's just no report node to attach a step to.
        ExtentTest test = ExtentReportManager.getInstance().getCurrentTest();
        if (test != null) {
            action.accept(test);
        }
    }

    private String formatHeaders(Headers headers) {
        StringBuilder formatted = new StringBuilder();
        for (Header header : headers) {
            String value = isSensitive(header.getName()) ? SensitiveDataMasker.MASKED_VALUE : header.getValue();
            formatted.append(header.getName()).append(": ").append(value).append(System.lineSeparator());
        }
        return formatted.toString().stripTrailing();
    }

    private boolean isSensitive(String headerName) {
        String lower = headerName.toLowerCase();
        if (ALWAYS_REDACTED_HEADERS.contains(lower)) {
            return true;
        }
        // The API key header name is configurable (auth.apikey.header.name),
        // so the fixed set above can't know it in advance.
        String apiKeyHeaderName = ConfigManager.getInstance().getAuthApiKeyHeaderName();
        return apiKeyHeaderName != null && lower.equals(apiKeyHeaderName.toLowerCase());
    }

    /**
     * <b>Known gap, not fixed here:</b> only headers are redacted. A request
     * body containing a sensitive field (e.g. a login payload's password)
     * is logged in full. Redacting inside an arbitrary JSON body needs a
     * field-name allow/deny list that's a policy decision, not something
     * this framework layer can decide generically - see the architecture
     * review's Security findings.
     */
    private String describeRequestBody(Object body) {
        if (body == null) {
            return "<no body>";
        }
        if (body instanceof String stringBody) {
            return truncate(stringBody);
        }
        try {
            // Serializes with the exact same Jackson annotations Rest
            // Assured itself uses to put this object on the wire, so this
            // logs the real JSON that was sent - not just the object's
            // toString().
            return truncate(OBJECT_MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            return truncate(String.valueOf(body));
        }
    }

    private String truncate(String content) {
        if (content == null || content.length() <= MAX_LOGGED_BODY_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_LOGGED_BODY_LENGTH) + "...[truncated, " + content.length() + " chars total]";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
