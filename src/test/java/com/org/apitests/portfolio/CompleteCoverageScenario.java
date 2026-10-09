package com.org.apitests.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.apiautomation.exceptions.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * One line of {@code testdata/generated/complete-coverage-manifest.json} - the execution mapping produced
 * (read-only on every source workbook) by {@code scripts/build_complete_coverage_manifest.py}. A source
 * Excel row can yield several lines (the "Migration to new code" sheet is a set of value lists, one
 * line per non-blank cell); every line keeps its {@code sourceSheet}/{@code sourceRow}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CompleteCoverageScenario(
        String scenarioId,
        String sourceSheet,
        int sourceRow,
        String tcId,
        String kind,
        Map<String, String> investorAccount,
        String fundCategory,
        String caseCovered,
        String goal,
        String riskProfile,
        String timeText,
        String lumpsumText,
        String monthlyText,
        String taxText,
        String auth,
        Map<String, Object> payload,
        Boolean expectedValid,
        String expectedInvalidWhy,
        String notes,
        String mappingStatus,
        String mappingReason,
        String workbookCaseType) {

    private static final String MANIFEST_RESOURCE = "testdata/generated/complete-coverage-manifest.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Manifest(List<CompleteCoverageScenario> scenarios) {
    }

    boolean executable() {
        return "EXECUTABLE".equals(mappingStatus);
    }

    static List<CompleteCoverageScenario> loadAll() {
        try (InputStream in = CompleteCoverageScenario.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE)) {
            if (in == null) {
                throw new ConfigurationException("Missing " + MANIFEST_RESOURCE
                        + " - run: python3 scripts/build_complete_coverage_manifest.py");
            }
            return new ObjectMapper().readValue(in, Manifest.class).scenarios();
        } catch (IOException e) {
            throw new ConfigurationException("Unreadable " + MANIFEST_RESOURCE + ": " + e.getMessage());
        }
    }
}
