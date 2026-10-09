package com.org.apitests.portfolio;

import com.org.apiautomation.models.response.funddetails.FundDetail;
import com.org.apiautomation.models.response.planner.FundEntry;
import com.org.apiautomation.models.response.planner.PlannerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the {@code PORTFOLIO_AWARE} flow's "already yours" logic.
 *
 * <p>Rule source: PRD section 11.5/11.7 and the acceptance criteria - a
 * recommended fund counts as "picked from the existing portfolio" if the
 * investor already holds the same underlying fund under a different plan
 * option ("if someone has the Regular plan option of a fund which is
 * suitable and we are suggesting its Direct plan, then it should be counted
 * as picked up from the existing portfolio"). The join key for "same
 * underlying fund, different plan option" is {@code fund_id} from the Fund
 * Details API - see {@code FundDetail}'s Javadoc for why {@code
 * reg_plan_id}/{@code variant_plan_id} can't be used for this instead.
 */
final class PortfolioAwareValidator {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private PortfolioAwareValidator() {
    }

    /**
     * For each recommended fund, {@code is_owned} must be {@code true} iff
     * its {@code fund_id} matches the {@code fund_id} of any fund already in
     * the investor's portfolio.
     */
    static List<BusinessRuleResult> assertOwnershipMatchesPortfolio(
            List<FundEntry> recommendedFunds,
            Map<Long, FundDetail> recommendedFundDetailsByPlanId,
            Set<Long> ownedFundIds,
            String scenario) {

        List<BusinessRuleResult> results = new ArrayList<>();
        for (FundEntry fund : recommendedFunds) {
            Long planId = fund.planData().planId();
            FundDetail detail = recommendedFundDetailsByPlanId.get(planId);
            if (detail == null || detail.fundId() == null) {
                results.add(BusinessRuleResult.fail(
                        "is_owned must reflect whether the investor already holds this fund (PRD 11.5/11.7), matched by fund_id",
                        scenario, String.valueOf(planId), "fund_id resolvable via Fund Details API", "fund_id missing/unresolved"));
                continue;
            }
            boolean expectedOwned = ownedFundIds.contains(detail.fundId());
            boolean actualOwned = fund.isOwned();
            results.add(new BusinessRuleResult(
                    "is_owned must reflect whether the investor already holds this fund (PRD 11.5/11.7), matched by fund_id",
                    scenario, String.valueOf(planId), expectedOwned, actualOwned, expectedOwned == actualOwned));
        }
        return results;
    }

    /**
     * PRD 11.5: the "You already own N fund(s)..." note is shown, with the
     * correct count and singular/plural wording, exactly when at least one
     * recommended fund is owned; PRD 11.7 restricts the "Already yours"
     * concept to {@code PORTFOLIO_AWARE} only.
     */
    static BusinessRuleResult assertPlanNoteCountConsistency(PlannerData data, String scenario) {
        long ownedCount = data.fundsData().data().stream().filter(FundEntry::isOwned).count();

        if (ownedCount == 0) {
            // No live sample demonstrates the "zero owned, PORTFOLIO_AWARE"
            // case, so only the note's absence of a false ownership claim is
            // checked here - not asserting is_show must be false, since that
            // isn't demonstrated by any confirmed sample (avoids inventing
            // a rule beyond what the PRD/API evidence actually shows).
            String text = stripHtml(data.planNoteData().textFmt());
            boolean falselyClaimsOwnership = text != null && text.toLowerCase().contains("already own");
            return BusinessRuleResult.of(!falselyClaimsOwnership,
                    "plan_note_data must not claim ownership when no recommended fund is owned (PRD 11.5)",
                    scenario, "no 'already own' claim", text);
        }

        String expectedFragment = ownedCount + (ownedCount == 1 ? " fund" : " funds");
        String actualText = stripHtml(data.planNoteData().textFmt());
        boolean showsNote = data.planNoteData().isShow();
        boolean textMatches = actualText != null && actualText.contains(expectedFragment);

        boolean passed = showsNote && textMatches;
        return BusinessRuleResult.of(passed,
                "plan_note_data must show the correct owned-fund count with correct singular/plural wording (PRD 11.5)",
                scenario, "is_show=true, text contains \"" + expectedFragment + "\"",
                "is_show=" + showsNote + ", text=\"" + actualText + "\"");
    }

    /** PRD 11.4/11.5: none of this applies to {@code FRESH} ("See a New Plan") - no fund can be owned and the note must be hidden. */
    static List<BusinessRuleResult> assertNotShownForFresh(PlannerData data, String scenario) {
        List<BusinessRuleResult> results = new ArrayList<>();

        results.add(BusinessRuleResult.of(!data.planNoteData().isShow(),
                "plan_note_data must be hidden for FRESH/'See a New Plan' (PRD 11.4/11.5)",
                scenario, false, data.planNoteData().isShow()));

        for (FundEntry fund : data.fundsData().data()) {
            results.add(new BusinessRuleResult(
                    "is_owned must always be false for FRESH/'See a New Plan' (PRD 11.4)",
                    scenario, String.valueOf(fund.planData().planId()), false, fund.isOwned(),
                    !fund.isOwned()));
        }
        return results;
    }

    private static String stripHtml(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = HTML_TAG.matcher(text);
        return matcher.replaceAll("");
    }
}
