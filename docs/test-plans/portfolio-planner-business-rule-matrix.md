# Portfolio Planner — Business Rule Matrix

Source of truth priority (per the automation brief): **PRD rule > explicit API contract >
actual API response > test data.** Every row below traces to `Portfolio-Planner-PRD.md` (PRD
section quoted) or to a field confirmed real via a live curl/sample in
`portfolio-planner-apis.md` / `src/test/resources/testdata/*.json`. Rows are **not** invented —
where the PRD gives a screen-flow description but no computable number (e.g. exact SIP/duration
boundaries, goal→risk-profile mapping, the config-master risk/income table), no test asserts a
value for it; see "Known gaps" at the end instead.

| Rule ID | PRD Rule | Input | API Source | Response Field | Expected Calculation | Validation | Test Case |
|---|---|---|---|---|---|---|---|
| R1 | "'See a New Plan' will show recommendations WITHOUT considering existing portfolio. Funds will be straightaway selected from Analyst's Choice as per the model." (§11.4) | `aware_type` omitted (FRESH is the confirmed default — see R7) | Planner Generate → Fund Opinion (`is_analyst_pick`) | `funds_data.data[].plan_data.plan_id` vs Fund Opinion's `is_analyst_pick` | Every FRESH plan_id must have `is_analyst_pick=true` | `FundEligibilityValidator.assertFreshFundsAreAnalystChoice` | `PortfolioPlannerCrossApiTests.crossApi_freshRecommendationsAreAnalystChoice` |
| R2 | "If someone has the Regular plan option of a fund which is suitable and we are suggesting its Direct plan, then it should be counted as picked up from the existing portfolio." (§11.5/§11.7) | `aware_type=PORTFOLIO_AWARE` | Planner Generate → User Portfolio → Fund Details (`fund_id`) | `funds_data.data[].is_owned` | `is_owned` = true iff recommended fund's `fund_id` (Fund Details) matches any portfolio-held fund's `fund_id` | `PortfolioAwareValidator.assertOwnershipMatchesPortfolio` | `PortfolioPlannerCrossApiTests.crossApi_portfolioAwareOwnershipMatchesActualHoldings` |
| R3 | "Show a sentence about... how many [funds picked from existing portfolio]... Replace 'funds' with 'fund' if only one." (§11.5) | `aware_type=PORTFOLIO_AWARE` | Planner Generate (self-contained) | `plan_note_data.is_show`, `plan_note_data.text_fmt` | Count of `is_owned=true` funds must appear in the note text with correct singular/plural, `is_show=true` when count > 0 | `PortfolioAwareValidator.assertPlanNoteCountConsistency` | `PortfolioPlannerPortfolioAwareTests.portfolioAware_planNoteCountMatchesOwnedFunds` |
| R4 | Not to be shown for "See a New Plan" (§11.4/§11.5); confirmed in the live FRESH sample: `is_owned=false` on every fund, `plan_note_data.is_show=false` | `aware_type` omitted (FRESH) | Planner Generate (self-contained) | `funds_data.data[].is_owned`, `plan_note_data.is_show` | Both always `false`/`false` for FRESH | `PortfolioAwareValidator.assertNotShownForFresh` | `PortfolioPlannerFreshTests.fresh_ownershipNeverAppliedAndNoteHidden` |
| R5 | Asset Allocation table (equity/debt % by VR category) under "Review pages... Asset Allocation" | any | Planner Generate → Fund Details (`category_name`) | `breakdown_funds_data.data[].name` (Equity/Debt bucket) | A fund's category must classify (via the PRD's fixed weights table) as equity-dominant iff placed in the "Equity" bucket | `CategoryAllocationClassifier` + `AllocationValidator.assertCategoryBucketConsistency` | `PortfolioPlannerCrossApiTests.crossApi_allocationBucketsMatchCategoryClassification` |
| R6 | General portfolio-construction invariant (brief §5): Equity % + Debt % = 100% | any | Planner Generate (self-contained) | `breakdown_funds_data.data[].perc_fmt` | Sum of all bucket percentages = 100 | `AllocationValidator.assertBreakdownPercentagesSumTo100` | `*Fresh/PortfolioAwareTests.*_allocationArithmeticIsConsistent` |
| R7 | "Total of lumpsum investment suggested to be invested in one shot across all funds + Total of first month SIP instalments across all funds..." (§11.6) | any | Planner Generate (self-contained) | `funds_data.total_investment_data.amount` | = Σ `funds_data.data[].first_month_amount` | `AllocationValidator.assertFirstMonthTotalMatchesSumOfFunds` | `*Fresh/PortfolioAwareTests.*_allocationArithmeticIsConsistent` |
| R8 | "The below line is missing in the Advisor App version of disclaimer... add it... make it uniform." (§11.10) | any | Planner Generate (self-contained) | `disclaimer_data.data[]` | Must contain the exact sentence: *"The Operator, its directors, employees, and agents may have holding or position in the securities advised herein."* | `DisclaimerValidator.assertRequiredSentencePresent` | `*Fresh/PortfolioAwareTests.*_disclaimerIncludesRequiredSentence` |
| R9 | Request/response consistency (API contract, not a screen rule, but required by brief §3/§6) | any | Planner Generate (self-contained) | `data.aware_type`, `data.investor.investor_id`, `data.input_summary_data.text_fmt` | Echoed values match the request; amounts appear correctly Indian-digit-grouped in the summary text | `InvestmentInputValidator.*` | `*Fresh/PortfolioAwareTests.*_inputsAreEchoedCorrectly` |
| R10 | "Alert message on Your Investment plan screen: 'Monthly amount must be greater than or equal to 1000.' / 'One-time amount must be greater than or equal to 5000.'" (validation-message list) | boundary `monthly_amount`/`lumpsum_amount` | Planner Generate | HTTP status / `status` | Below threshold → rejected; at threshold → accepted. **Caveat:** this threshold is documented for the Edit Investment Plan flow, applied here to Generate as the closest verified source (no separate Generate threshold is documented) — see gaps. | `PlannerNegativeAssertions.wasRejected` | `PortfolioPlannerBoundaryTests.boundary_*` |
| R11 | Missing/invalid business inputs must not silently succeed (brief §6) | missing `goal_type`/`investor_id`/`risk_profile_id`/`txn_options`; invalid `goal_type`/`txn_options`/`aware_type`/`investor_id`/`risk_profile_id` | Planner Generate | HTTP status / `status` | Rejected (non-2xx, or 200 with `status:false`) | `PlannerNegativeAssertions.wasRejected` | `PortfolioPlannerNegativeTests.negative_*` |
| R12 (contract) | Cross-API data consistency (brief §9) | any | Planner Generate → Fund Details | `funds_data.data[].category_data.category_id`/`category_fmt` | Must equal Fund Details' `category_id`/`secondary_category_name` for the same plan_id | inline in test | `PortfolioPlannerCrossApiTests.crossApi_fundCategoryConsistentWithFundDetails` |

## Explicitly out of scope (per the automation brief)
`port_builder_id`, `recommend_reason_data`, `recommend_reason`, and any HTML↔structured
recommendation-reason matching are not modeled, parsed, or asserted on anywhere in this module.

## Known gaps (not automated, and why)
- **Planner Generate is currently flaky in QA, not uniformly broken.** The endpoint/contract
  itself is confirmed correct: `PlannerService` calls `POST /api/v1/planner/funds-buy/generate`
  on the advisor app gateway (`advisor.api.base.url`, changed 2026-09-08 from the main app host
  and the old `/api/v1/planner/generate` path) with a JSON body matching a real, directly-supplied
  curl byte-for-byte, and at least one isolated live call returned a real 200 with a full,
  well-formed recommendation (confirmed on 2026-09-08, ~22s response time - real fund-selection
  computation, not instant). But repeated isolated calls with the identical request most often
  return an instant, empty HTTP 500 instead - both outcomes were observed within the same short
  session, with no pattern tied to concurrency (single-class, single-threaded runs fail exactly
  like full-suite parallel runs do). Every other Portfolio Planner API (User Portfolio, Fund
  Opinion, Analyst Pick, Fund Details, Fund Plan Options) succeeds consistently through the same
  authenticated session, so this is isolated to Generate itself - a live backend
  reliability/environment issue, not a request-shape or framework defect. `read.timeout.ms` was
  raised to 45000 in `qa.properties` so a genuine slow success is no longer misreported as a
  client-side timeout. The 16 business-rule tests that depend on a successful Generate call
  report this cleanly (`PlannerTestData.parseSuccessful`: "did not return 200") rather than
  failing individually or throwing an opaque parser exception; expect them to pass whenever a
  given run happens to land on a healthy response, and to keep intermittently failing until the
  backend's reliability issue is fixed - no test changes needed either way.
- **KYC/Risk Assessment/Select-Investor screens, Edit Investment Plan, Review page deviation
  coloring, cart-order journey** — these are UI-flow/session-state screens with no corresponding
  API in the given API list, or require an API not exercised here (no "review"/"edit" endpoint
  was provided). Not automatable without that endpoint.
- **Financial-year question window (1 Jan–31 Mar)** — a UI-only condition on the tax-saving
  input screen; no `financial_year`-shaped parameter appears in the Planner Generate contract.
- **Config Master API (`risk_assessment.annual_income`)** — a service method exists
  (`FundService.getConfigMaster`) but no business-rule test reads it: the PRD text provided does
  not give a concrete risk/income mapping table to assert against. Automating this needs a
  follow-up with the actual config values.
- **Fund Filter / GQL plan-txn-details** — service methods exist (`FundService.getFundsByFilter`,
  `getPlanTxnDetails`) so the framework can execute these calls, but the filter's encoding and the
  txn-details component's business meaning aren't documented anywhere available to this session,
  so no rule is asserted against them.
- **Transaction-status/"suspended for sale"/"Exit"/"Avoidable" fund blocking** — `FundDetail`
  exposes `transactionStatus`/`analystTypeCode`, but the PRD doesn't enumerate which values mean
  "blocked" vs "notify-only," so no hard assertion is made — would need product confirmation of
  the exact value set first (see `CLAUDE.md`'s own "Known gaps" section, which flags the same
  absence for app-identity headers).
