"""Run from ~/SA_APIs:  python3 apply_limits_check.py
Adds planTxnDetails() to the FundSelectionValidator.Fetcher used by PortfolioPlannerExecutionDataDrivenTests."""
import sys, shutil
p = "src/test/java/com/org/apitests/portfolio/PortfolioPlannerExecutionDataDrivenTests.java"
s = open(p).read()
if "planTxnDetails" in s:
    sys.exit("Already applied - nothing to do.")
i = s.find("public String fundsByFilter(String encodedFilter) {")
if i < 0:
    sys.exit("fundsByFilter not found - send this file to Claude.")
j = s.find("\n            }\n", i)
if j < 0:
    sys.exit("End of fundsByFilter not found - send this file to Claude.")
j += len("\n            }\n")
add = '''
            @Override
            public String planTxnDetails(List<Long> planIds) {
                String ids = planIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                return new ApiClient().get("/api/v1/gql/component", mainApiRowSpec(rowSession)
                        .queryParam("screen", "fund-portfolio-planner-edit")
                        .queryParam("componentName", "plan-txn-details-alt")
                        .queryParam("params", "{\\"plan_id\\": \\"" + ids + "\\"}")).getBody().asString();
            }
'''
shutil.copy(p, p + ".bak2")
s = s[:j] + add + s[j:]
open(p, "w").write(s)
print("Applied. Backup:", p + ".bak2")
