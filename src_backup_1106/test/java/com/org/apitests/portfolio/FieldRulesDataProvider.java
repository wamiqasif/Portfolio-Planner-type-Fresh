package com.org.apitests.portfolio;

import org.testng.annotations.DataProvider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TestNG {@code @DataProvider} over {@code planner_generate_test_data.xlsx}
 * - the API-1 field-presence/boundary test-data workbook. Mirrors {@link
 * ExecutionDataProvider}'s shape for the same reason: no row-to-test-args
 * DTO on top of {@link PositiveCombinationRow}/{@link BoundaryNegativeRow},
 * which already expose exactly what the test classes need.
 */
final class FieldRulesDataProvider {

    /**
     * Optional pilot scoping, same convention as {@code
     * ExcelTestDataReader}'s {@code portfolio.execution.labelIds}: a
     * comma-separated {@code TC_ID} allowlist via {@code
     * -Dportfolio.fieldRules.tcIds=...}, for running a small pilot batch
     * before committing to the full 150+42-row dataset (per the automation
     * brief's execution strategy). Absent (the default), every row runs.
     */
    private static final String TC_ID_FILTER_PROPERTY = "portfolio.fieldRules.tcIds";

    private FieldRulesDataProvider() {
    }

    @DataProvider(name = "positiveCombinations")
    static Object[][] positiveCombinations() {
        List<PositiveCombinationRow> rows = applyTcIdFilter(ExcelTestDataReader.readPositiveCombinations(), PositiveCombinationRow::tcId);
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }

    @DataProvider(name = "boundaryNegativeTests")
    static Object[][] boundaryNegativeTests() {
        List<BoundaryNegativeRow> rows = applyTcIdFilter(ExcelTestDataReader.readBoundaryNegativeCombinations(), BoundaryNegativeRow::tcId);
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }

    private static <T> List<T> applyTcIdFilter(List<T> rows, java.util.function.Function<T, String> tcIdOf) {
        String allowlist = System.getProperty(TC_ID_FILTER_PROPERTY);
        if (allowlist == null || allowlist.isBlank()) {
            return rows;
        }
        Set<String> allowedTcIds = new HashSet<>(Arrays.asList(allowlist.split(",")));
        return rows.stream().filter(r -> allowedTcIds.contains(tcIdOf.apply(r))).toList();
    }
}
