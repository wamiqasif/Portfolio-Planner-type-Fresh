"""Run from ~/SA_APIs after unzipping report_columns_v22.zip.
'Business Detail' sheet - final layout (14 columns):
  TC ID | Payload | Response | Investor Holdings | Recommended Funds - Tags & AC Priority | label_id | investor_id |
  Case Covered | Fund Category | Actual Plan IDs | Allocation Result | PASS/FAIL | Failure Reason | API responsible
Removed (empty, constant, duplicate or unreadable): Funds in Portfolio, Planner Mode, Expected Business Rule,
Expected Plan IDs, Expected Category, Actual Category, Fund Details Response.
Safe to run again (works on the original file and on a file already patched by the earlier v22 script)."""
import re, shutil, sys
p = "src/test/java/com/org/apitests/portfolio/BusinessValidationReportWriter.java"
s = open(p).read()
if "/* report layout v22b */" in s:
    print("BusinessValidationReportWriter.java : already on the v22b layout"); sys.exit(0)

rows_new = '''static List<List<String>> detailRows() { /* report layout v22b */
        List<List<String>> rows = new ArrayList<>();
        for (BusinessValidationReportRow row : ROWS) {
            rows.add(List.of(
                    nullToEmpty(row.tcId()), nullToEmpty(row.description()), nullToEmpty(row.plannerGenerateResponse()),
                    extrasHoldings(row), extrasTags(row),
                    nullToEmpty(row.labelId()), nullToEmpty(row.investorId()),
                    nullToEmpty(row.caseCovered()), nullToEmpty(row.fundCategory()),
                    nullToEmpty(row.actualPlanIds()), nullToEmpty(row.allocationResult()),
                    nullToEmpty(row.result()), nullToEmpty(row.failureReason()), nullToEmpty(row.apiResponsible())));
        }
        return rows;
    }'''
header_new = '''static List<String> header() {
        return List.of("TC ID", "Payload (planner/funds-buy/generate)", "Response (planner/funds-buy/generate)",
                "Investor Holdings (plan, units, %, opinion, rating, AC)", "Recommended Funds - Tags & AC Priority",
                "label_id", "investor_id", "Case Covered", "Fund Category", "Actual Plan IDs", "Allocation Result",
                "PASS/FAIL", "Failure Reason", "API responsible");
    }'''
helpers = '''    private static String extrasHoldings(BusinessValidationReportRow row) {
        ReportExtras.Extras e = ReportExtras.get(row.plannerGenerateResponse());
        return e == null ? "" : nullToEmpty(e.holdings());
    }

    private static String extrasTags(BusinessValidationReportRow row) {
        ReportExtras.Extras e = ReportExtras.get(row.plannerGenerateResponse());
        return e == null ? "" : nullToEmpty(e.recommendedTags());
    }

'''
m1 = re.search(r"static List<List<String>> detailRows\(\) \{.*?return rows;\n    \}", s, re.S)
m2 = re.search(r"static List<String> header\(\) \{.*?\);\n    \}", s, re.S)
if not m1 or not m2 or "private static String nullToEmpty(String value) {" not in s:
    sys.exit("anchor not found in " + p + " - send this file to Claude")
shutil.copy(p, p + ".bak-v22b")
s = s[:m1.start()] + rows_new + s[m1.end():]
m2 = re.search(r"static List<String> header\(\) \{.*?\);\n    \}", s, re.S)
s = s[:m2.start()] + header_new + s[m2.end():]
if "extrasHoldings(BusinessValidationReportRow row)" not in s:
    s = s.replace("    private static String nullToEmpty(String value) {", helpers + "    private static String nullToEmpty(String value) {", 1)
open(p, "w").write(s)
print("BusinessValidationReportWriter.java : patched - 14 columns (2 added, 7 removed)")
