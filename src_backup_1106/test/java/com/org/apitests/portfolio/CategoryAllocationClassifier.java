package com.org.apitests.portfolio;

/**
 * Encodes the PRD's Asset Allocation table (Portfolio-Planner-PRD.md, under
 * "Under the Review pages... Asset Allocation") verbatim - the one place in
 * this PRD that gives concrete, numeric equity/debt weights per VR category,
 * rather than a screen-flow description. Used to classify which bucket
 * (Equity/Debt) a recommended fund's category should fall under, so {@code
 * AllocationValidator} can check the Planner's own {@code breakdown_funds_data}
 * grouping against it instead of just trusting the label the API chose.
 *
 * <p>Matched by {@code category_name} substring against the fund's Fund
 * Details record - VR's category names are consistently of the form
 * {@code "<Asset class>: <Sub-category>"} (confirmed in every sample: e.g.
 * {@code "Hybrid: Aggressive Hybrid"}, {@code "Debt: Liquid"}, {@code
 * "Equity: Large Cap"}) or a bare equity/debt sub-category with no colon
 * (confirmed in the Fund Opinion sample funds: {@code "Flexi Cap"}, {@code
 * "Short Duration"}), so this only special-cases the categories the PRD
 * table actually calls out (hybrid variants) and defaults everything else
 * to the table's "All Equity"/"All Debt" rows based on the asset-class
 * prefix.
 */
final class CategoryAllocationClassifier {

    record Weights(double equityPct, double debtPct) {
    }

    private CategoryAllocationClassifier() {
    }

    static Weights classify(String categoryName) {
        if (categoryName == null) {
            return null;
        }
        String normalized = categoryName.toLowerCase();

        if (normalized.contains("arbitrage")) {
            return new Weights(0, 100);
        }
        if (normalized.contains("conservative hybrid")) {
            return new Weights(25, 75);
        }
        if (normalized.contains("equity savings")) {
            return new Weights(35, 65);
        }
        if (normalized.contains("dynamic asset allocation")) {
            return new Weights(50, 50);
        }
        if (normalized.contains("balanced hybrid")) {
            return new Weights(50, 50);
        }
        if (normalized.contains("aggressive hybrid")) {
            return new Weights(75, 25);
        }
        // "Multi asset allocation" is explicitly fund-specific per the PRD
        // (no fixed weights table entry) - deliberately not classified here
        // rather than guessing a number the PRD didn't give.
        if (normalized.contains("multi asset")) {
            return null;
        }
        if (normalized.startsWith("debt") || normalized.contains("duration") || normalized.contains("liquid")
                || normalized.contains("overnight") || normalized.contains("gilt") || normalized.contains("money market")) {
            return new Weights(0, 100);
        }
        // Default: every other category in the sample data (Flexi Cap, Large
        // Cap, Large & MidCap, Small Cap, ELSS, etc.) is a pure-equity
        // category per the PRD table's "All Equity categories -> 100%" row.
        return new Weights(100, 0);
    }

    /** True if the classified weights make this category equity-dominant (>50% equity) - the PRD's own worked example (75/25 aggressive hybrid) treats "which bucket" as majority-driven. */
    static boolean isEquityDominant(Weights weights) {
        return weights != null && weights.equityPct() > 50;
    }
}
