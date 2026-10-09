package com.org.apitests.portfolio;

import org.testng.annotations.DataProvider;

import java.util.List;

/**
 * TestNG {@code @DataProvider} over the {@code Execution_Data} sheet - the
 * execution dataset per the automation brief. Each yielded {@link
 * ExecutionDataRow} already exposes exactly the fields the brief asks for
 * (labelId, investorId, userId, caseCovered, fundCategory, matchedPlanId,
 * matchStatus) as named accessors, so no separate row-to-test-args DTO is
 * introduced on top of it (RULE-004: never hardcode these values in a test
 * class - every one of them always comes from here).
 */
final class ExecutionDataProvider {

    private ExecutionDataProvider() {
    }

    @DataProvider(name = "executionData")
    static Object[][] executionData() {
        List<ExecutionDataRow> rows = ExcelTestDataReader.readExecutionData();
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }

    @DataProvider(name = "freshTestData")
    static Object[][] freshTestData() {
        List<FreshTestDataRow> rows = ExcelTestDataReader.readFreshTestData();
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }
}
