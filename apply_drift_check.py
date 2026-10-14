"""Run from ~/SA_APIs:  python3 apply_drift_check.py
Replaces the hard 'is_analyst_pick must match the Excel' check with TestDataDriftChecker."""
import re, sys, shutil
p = "src/test/java/com/org/apitests/portfolio/PortfolioPlannerExecutionDataDrivenTests.java"
s = open(p).read()
if "TestDataDriftChecker" in s:
    sys.exit("Already applied - nothing to do.")
shutil.copy(p, p + ".bak")

# 1) remember is_direct_plan of the reference fund
a = "                if (!matchedDetails.data().isEmpty()) {\n                    FundDetail detail = matchedDetails.data().get(0);"
if a not in s:
    sys.exit("Anchor 1 not found - send this file to Claude.")
s = s.replace(a, "                Boolean liveDirectPlan = null;\n" + a + "\n                    liveDirectPlan = detail.isDirectPlan();", 1)

# 2) replace the is_analyst_pick block
start = s.find("                Boolean expectedAnalystPick = row.isAnalystPickFlag();")
end_marker = "\"Reference fund's is_analyst_pick must match the Excel's expected is_analyst_pick\","
mid = s.find(end_marker, start)
if start < 0 or mid < 0:
    sys.exit("Anchor 2 not found - send this file to Claude.")
end = s.find("                }\n", mid) + len("                }\n")
new = '''                // Test data drift: compare sheet opinion / star / Analyst's Choice with live QA data
                currentApi[0] = "Fund Opinion (test data drift)";
                String liveOpinionBody = new ApiClient().get(ConfigManager.getInstance().getFundOpinionEndpoint(),
                        mainApiRowSpec(rowSession).queryParam("plan_id", row.matchedPlanId())).getBody().asString();
                results.addAll(TestDataDriftChecker.check(row.caseCovered(), row.opinionName(), row.starRating(),
                        row.isAnalystPickFlag(), row.matchedFund(), matchedPlanId, liveDirectPlan, liveOpinionBody));
'''
s = s[:start] + new + s[end:]
open(p, "w").write(s)
print("Applied. Backup saved as", p + ".bak")
