package com.org.apitests.portfolio;

import java.util.Map;
import java.util.Set;

final class CategoryMatcher {

    /**
     * Test-sheet category names that differ from Value Research category names.
     * "Nifty 50 index funds" -> VR "Large Cap" (confirmed, TC 50); "ELSS" -> "ELSS - Tax Saver";
     * "Value" -> Value Oriented / Contra; regular-income sheet names: "Short Duration" -> "Short Term",
     * "Flexicap/Multicap/Large & Mid cap/Focused" and the dev sheet's "Equity: Flexi Cap" -> any growth
     * category (the test case group covers all of them, e.g. Mirae Asset Large & Midcap for cases 56-84).
     */
    private static final Map<String, Set<String>> ALIASES = Map.of(
            "nifty 50 index funds", Set.of("large cap"),
            "value", Set.of("value", "value oriented", "contra"),
            "elss", Set.of("elss", "elss - tax saver", "elss (tax savings)"),
            "short duration", Set.of("short term", "short duration"),
            "flexicap/multicap/large & mid cap/focused",
            Set.of("flexi cap", "multi cap", "large & midcap", "large & mid cap", "focused"),
            "flexi cap", Set.of("flexi cap", "multi cap", "large & midcap", "large & mid cap", "focused")
    );

    private CategoryMatcher() {
    }

    static boolean matches(String expectedCategory, String actualCategoryName, String actualSecondaryCategoryName) {
        String actual = actualSecondaryCategoryName != null ? actualSecondaryCategoryName : actualCategoryName;
        String expectedShortForm = shortForm(expectedCategory);
        String actualShortForm = shortForm(actual);
        if (expectedShortForm == null || actualShortForm == null) {
            return false;
        }
        if (expectedShortForm.equals(actualShortForm)) {
            return true;
        }
        return ALIASES.getOrDefault(expectedShortForm, Set.of()).contains(actualShortForm);
    }

    private static String shortForm(String category) {
        if (category == null) {
            return null;
        }
        int colonIndex = category.indexOf(':');
        String tail = colonIndex >= 0 ? category.substring(colonIndex + 1) : category;
        return tail.trim().toLowerCase();
    }
}
