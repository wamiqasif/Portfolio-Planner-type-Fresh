package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Focused, API-free unit tests for {@link CategoryMatcher} - covers the
 * compound-vs-short VR category equivalence confirmed against the PRD and
 * existing {@code CategoryAllocationClassifier}/{@code PortfolioPlannerCrossApiTests}
 * evidence.
 */
public class CategoryMatcherTest {

    @Test
    public void compoundDebtCategoryMatchesItsShortForm() {
        Assert.assertTrue(CategoryMatcher.matches("Debt: Short Term", "Debt: Short Term", "Short Term"));
    }

    @Test
    public void compoundEquityCategoryMatchesItsShortForm() {
        Assert.assertTrue(CategoryMatcher.matches("Equity: Mid Cap", "Equity: Mid Cap", "Mid Cap"));
    }

    @Test
    public void exactSameCategoryMatches() {
        Assert.assertTrue(CategoryMatcher.matches("Liquid", "Debt: Liquid", "Liquid"));
    }

    @Test
    public void clearlyDifferentCategoriesDoNotMatch() {
        Assert.assertFalse(CategoryMatcher.matches("Liquid", "Equity: Mid Cap", "Mid Cap"));
    }
}
