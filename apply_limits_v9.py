"""Run from ~/SA_APIs:  python3 apply_limits_v9.py
Makes the validator's Fetcher use plan-txn-details (the planner's source) for limits, and
plan-txn-details-alt only as a cross-check. Works whether or not apply_limits_check.py was run before."""
import sys, shutil
p = "src/test/java/com/org/apitests/portfolio/PortfolioPlannerExecutionDataDrivenTests.java"
s = open(p).read()
if "planTxnDetailsAlt" in s:
    sys.exit("Already applied - nothing to do.")

def method(name, component):
    return f'''
            @Override
            public String {name}(List<Long> planIds) {{
                String ids = planIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                return new ApiClient().get("/api/v1/gql/component", mainApiRowSpec(rowSession)
                        .queryParam("screen", "fund-portfolio-planner-edit")
                        .queryParam("componentName", "{component}")
                        .queryParam("params", "{{\\"plan_id\\": \\"" + ids + "\\"}}")).getBody().asString();
            }}
'''

shutil.copy(p, p + ".bak3")
if "public String planTxnDetails(" in s:
    i = s.find("public String planTxnDetails(")
    j = s.find("\n            }\n", i) + len("\n            }\n")
    block = s[i:j].replace('"plan-txn-details-alt"', '"plan-txn-details"')
    s = s[:i] + block + method("planTxnDetailsAlt", "plan-txn-details-alt") + s[j:]
else:
    i = s.find("public String fundsByFilter(String encodedFilter) {")
    if i < 0:
        sys.exit("fundsByFilter not found - send this file to Claude.")
    j = s.find("\n            }\n", i) + len("\n            }\n")
    s = s[:j] + method("planTxnDetails", "plan-txn-details") + method("planTxnDetailsAlt", "plan-txn-details-alt") + s[j:]
open(p, "w").write(s)
print("Applied. Backup:", p + ".bak3")
