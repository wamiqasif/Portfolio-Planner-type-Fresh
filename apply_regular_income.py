"""Run from ~/SA_APIs:  python3 apply_regular_income.py
Adds 'Invest for monthly income' (REGULAR-INCOME) support and CASE ID based scenario matching.
Payload as captured from the app (14-Oct-2026):
  start_regular_income = "Needed when?" (1 Immediately, 2 Next 1 year, 3 Next 1-5 years)
  annual_income_range  = from sheet (app sends 2)
  needed_annual_amount = null for start 1, amount for start 2/3
Safe to run again; upgrades earlier versions of this patch."""
import sys, shutil
base = "src/test/java/com/org/apitests/portfolio/"
main_req = "src/main/java/com/org/apiautomation/models/request/planner/PlannerRequest.java"

def load(p): return open(p).read()
def save(p, s, o):
    if s != o:
        shutil.copy(p, p + ".bak-ri"); open(p, "w").write(s); print(p.split("/")[-1], ": patched (backup .bak-ri)")
    else:
        print(p.split("/")[-1], ": already up to date")

# 0) PlannerRequest: start_regular_income setter
p = main_req; s = load(p); o = s
if "startRegularIncome(" not in s:
    a = "    public Object get(String key) {"
    if a not in s: sys.exit("PlannerRequest.java: anchor not found - send this file to Claude")
    s = s.replace(a, '''    /** REGULAR-INCOME only: when the income should start (1 Immediately, 2 Next 1 year, 3 Next 1-5 years). */
    public PlannerRequest startRegularIncome(Object value) {
        return set("start_regular_income", value);
    }

''' + a, 1)
save(p, s, o)

# 1) Excel reader
p = base + "ExcelTestDataReader.java"; s = load(p); o = s
ORIG = '''                    parseLongOrNull(r.get("Tax saving amount"))));'''
TAIL = '''                    parseIntOrNull(r.get("Annual income range")),
                    parseIntOrNull(r.get("Start regular income"))));'''
FULL = '''                    parseLongOrNull(r.get("Tax saving amount")),
                    parseIntOrNull(r.get("CASE ID") != null ? r.get("CASE ID") : r.get("Case ID")),
                    parseLongOrNull(r.get("Accumulated amount")),
                    parseLongOrNull(r.get("Needed annual amount")),
                    r.get("Duration type"),
                    parseIntOrNull(r.get("Investment duration")),
''' + TAIL
if TAIL in s:
    pass
elif '''                    parseIntOrNull(r.get("Annual income range"))));''' in s:
    s = s.replace('''                    parseIntOrNull(r.get("Annual income range"))));''', TAIL, 1)
elif '''                    parseIntOrNull(r.get("Investment duration"))));''' in s:
    s = s.replace('''                    parseIntOrNull(r.get("Investment duration"))));''',
                  '''                    parseIntOrNull(r.get("Investment duration")),
''' + TAIL, 1)
elif ORIG in s:
    s = s.replace(ORIG, FULL, 1)
else:
    sys.exit("ExcelTestDataReader.java: anchor not found - send this file to Claude")
save(p, s, o)

# 2) goal mapping
p = base + "PlannerRequestDefaults.java"; s = load(p); o = s
if '"REGULAR-INCOME"' not in s:
    a = "    static String goalTypeFor(String goalLabel) {"
    if a not in s: sys.exit("PlannerRequestDefaults.java: anchor not found - send this file to Claude")
    s = s.replace(a, a + '''
        if (goalLabel != null && goalLabel.toLowerCase().contains("income")) {
            return "REGULAR-INCOME";
        }''', 1)
save(p, s, o)

# 3) payload
p = base + "PortfolioPlannerExecutionDataDrivenTests.java"; s = load(p); o = s
BLOCK = '''            if ("REGULAR-INCOME".equals(goalType)) {
                // Invest for monthly income - shape captured from the app (14-Oct-2026):
                // start_regular_income = "Needed when?" (1 Immediately, 2 Next 1 year, 3 Next 1-5 years),
                // annual_income_range from the sheet (app sends 2),
                // needed_annual_amount = null for start 1, the entered amount for start 2/3.
                request.without("duration_type").without("investment_duration");
                scenario.map(PortfolioAwareScenarioRow::annualIncomeRange).filter(v -> v != null)
                        .ifPresent(request::annualIncomeRange);
                Integer start = scenario.map(PortfolioAwareScenarioRow::startRegularIncome).orElse(null);
                if (start != null) {
                    request.startRegularIncome(start);
                }
                scenario.map(PortfolioAwareScenarioRow::accumulatedAmount).filter(v -> v != null)
                        .ifPresent(request::accumulatedAmount);
                Long needed = scenario.map(PortfolioAwareScenarioRow::neededAnnualAmount).orElse(null);
                if (needed != null) {
                    request.neededAnnualAmount(needed);
                } else if (start != null && start == 1) {
                    request.neededAnnualAmount(null);
                }
            } else if ("TAX-SAVINGS".equals(goalType)) {'''
if BLOCK not in s:
    if '            if ("REGULAR-INCOME".equals(goalType)) {' in s:
        i = s.index('            if ("REGULAR-INCOME".equals(goalType)) {')
        j = s.index('            } else if ("TAX-SAVINGS".equals(goalType)) {', i) + len('            } else if ("TAX-SAVINGS".equals(goalType)) {')
        s = s[:i] + BLOCK + s[j:]
    elif '            if ("TAX-SAVINGS".equals(goalType)) {' in s:
        s = s.replace('            if ("TAX-SAVINGS".equals(goalType)) {', BLOCK, 1)
    else:
        sys.exit("PortfolioPlannerExecutionDataDrivenTests.java: anchor not found - send this file to Claude")
save(p, s, o)

# 4) blank Investor ID (new scenario accounts give only Label ID + User ID): take it from the portfolio API
p = base + "PortfolioPlannerExecutionDataDrivenTests.java"; s = load(p); o = s
A_OLD = "                    .investorId(Long.parseLong(row.investorId()))"
A_NEW = "                    .investorId(row.investorId() == null || row.investorId().isBlank() ? 0L : Long.parseLong(row.investorId()))"
B_ANCHOR = "            failIfPortfolioDataMissing(portfolioHttpResponse);"
B_NEW = B_ANCHOR + r"""
            if (row.investorId() == null || row.investorId().isBlank()) {
                // Test account given without Investor ID: use the investor of this label from label-opinion-data
                java.util.regex.Matcher inv = java.util.regex.Pattern
                        .compile("\"investor_id\"\\s*:\\s*(\\d+)[^}]*\"label_id\"\\s*:\\s*" + row.labelId() + "\\b")
                        .matcher(portfolioHttpResponse.getBody().asString());
                if (!inv.find()) {
                    throw new IllegalStateException("Investor ID not given for label " + row.labelId()
                            + " and not found in label-opinion-data label-data");
                }
                request.investorId(Long.parseLong(inv.group(1)));
                description = request.toJson();
            }"""
if "Test account given without Investor ID" not in s:
    if A_OLD not in s or B_ANCHOR not in s:
        sys.exit("PortfolioPlannerExecutionDataDrivenTests.java: investor-id anchors not found - send this file to Claude")
    s = s.replace(A_OLD, A_NEW, 1).replace(B_ANCHOR, B_NEW, 1)
save(p, s, o)
print("Done. Now: ./mvnw clean test-compile")
