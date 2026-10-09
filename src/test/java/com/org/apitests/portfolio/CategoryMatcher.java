package com.org.apitests.portfolio;

import java.util.Map;
import java.util.Set;

final class CategoryMatcher {

    /**
     * Excel category names that differ from Value Research category names.
     * "Nifty 50 index funds" -> VR "Equity: Large Cap" (confirmed from QA log, TC 50).
     * "Value" -> Value Oriented / Contra (assumption: dev sheet Value cases include Contra funds).
//     */
//    private static final Map<String, Set<String>> ALIASES = Map.of(
//            "nifty 50 index funds", Set.of("large cap"),
//            "value", Set.of("value", "value oriented", "contra")
//    );

    private static final Map<String, Set<String>> ALIASES = Map.of(
            "nifty 50 index funds", Set.of("large cap"),
            "value", Set.of("value", "value oriented", "contra"),
            "elss", Set.of("elss", "elss - tax saver", "elss (tax savings)")
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