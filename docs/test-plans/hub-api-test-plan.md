# Hub API — Manual/API Test Plan

**Service:** VRO · **Endpoint:** `GET /api/{{api-version}}/hub/` (confirmed live: `GET https://qaappapi.valueresearch.in/api/v0/hub/`) · **Legacy endpoint:** `GET /api/hub/`
**Auth:** `auth-token` header · **Purpose:** returns all Hub navigation links, menu structure, disclosures, metadata, and tracking information for the application.

This document is a manual/API test design artifact only — **no automation code is included**, per the review request. It is organized as: API Analysis → Business Rules (explained before any test case, per instruction) → Test Suite (grouped by the requested lettered categories) → Final Summary.

---

## 1. API Analysis

| # | Aspect | Finding |
|---|---|---|
| 1 | Endpoint | Two live variants: legacy `/api/hub/` and versioned `/api/v0/hub/`. Drift risk between them. |
| 2 | Request Method | `GET` only, no body. Must be read-only; no other verb should be accepted. |
| 3 | Authentication | `auth-token` header only. `Authorization` is not used for user auth (confirmed in a prior investigation). Cookie `auth_token` (underscore) and `user_session` (JWT) are also present in the browser session — a separate, confusingly-named credential channel worth testing explicitly. |
| 4 | Headers | This capture is a **web** session — none of the Android app's `json-api-key`/`app-platform`/`app-type`/`app-version`/`app-build`/`browser-id` headers are present. The API serves ≥2 client types with apparently different header sets. |
| 5 | Cookies | Large analytics/tracking jar (`_ga`, `_fbp`, `_clck`, `cf_clearance`, etc.) plus `user_session` (JWT carrying a `page_access` permission map) and `auth_token` cookie. |
| 6 | Query Parameters | None documented, none present. |
| 7 | Path Parameters | Only the API version segment (`v0`). No resource ID — this is a static, global structure. |
| 8 | Required fields (request) | `auth-token` header only. |
| 9 | Optional fields (request) | None documented. |
| 10 | Response Structure | `status`, `data` (`inpage_title`, `hub[]`, `add_investor`, `is_add_investor_allowed`, `disclosures[]`, `disclosures_data`), `page_info`, `tracking_info`. |
| 11 | Nested Objects | Hub Group → Menu → Product (Product is a narrower shape than Menu: no `id`/`icon`/`action_hint`). `disclosures_data.data[].details` is polymorphic (`{list}` or `{desc}`). |
| 12 | Arrays | `hub[]`, `menus[]`, `products[]` (nullable), `disclosures[]`, `disclosures_data.data[]`, `details.list[]`, `we_user_properties[]`. |
| 13 | Data Types | Mostly consistent; two confirmed exceptions — see Business Rules #8 and #11. |
| 14 | Nullable Fields | `download_url`, `component_code`, `products` at menu level; `ga_uid`, `ga_tracking_url` in tracking_info. The 8th `disclosures_data` entry **omits** `title` rather than nulling it — a third "no value" convention alongside null and empty-string (`meta_description`). |
| 15 | Boolean Fields | `status`, `is_ext_url`, `is_show`, `is_active`, `is_add_investor_allowed`, and (2nd sample) several `ga_user_properties` subscriber flags. All correctly typed; not all correctly **valued** (see BR-4). |
| 16 | URL Fields | Relative internal (`/myaccount/`) and absolute external (`https://qastock.valueresearch.in/...`), plus fragment-based in-page nav (`/settings/#portfolio`). |
| 17 | Component Codes | `UPPER_SNAKE_CASE` when present; ties to the session JWT's `page_access` map (BR-7). |
| 18 | Metadata | `page_info` — plain SEO fields, all strings. |
| 19 | Tracking Information | Structurally unstable across the two real samples provided — the most severe finding in this analysis (BR-8). |
| 20 | Business Rules | Explained in full in Section 2. |

---

## 2. Business Rules (explained before test case generation)

- **BR-1 Mandatory menu fields** — every menu must have non-null `id`, `title`, `icon`, `url`, `action_hint`. Confirmed true for all 36 sample menus. `download_url`/`component_code`/`products` are optional by design — not part of this rule.
- **BR-2 ID uniqueness** — all IDs unique in-sample; nothing structurally *enforces* it, so it must be actively tested.
- **BR-3 Title uniqueness is scoped, not global** — "Stock Advisor" is both a menu title and a product title elsewhere. Uniqueness applies **within one menu list**, not globally.
- **BR-4 `is_ext_url` correctness — three real counter-examples exist in the sample itself**: `stock_screener` (cross-domain URL, flagged `false`), `about_us` (relative URL, flagged `true`), `terms_n_conditions` (relative URL, flagged `true`, and the only top-level menu with a non-null `download_url`). This is a defect-class finding already present in the data, not a hypothetical.
- **BR-5 Icon-to-ID convention** — every icon is `/assets/images/hub_icon/<menu_id>.svg`, confirmed with zero exceptions across all 36 entries.
- **BR-6 `products[]` is for multi-product branching** — every populated array has exactly 2 entries (Fund Advisor + Stock Advisor variants of the same destination). The open question for a single-product user's top-level `url` resolution needs an explicit answer from the team.
- **BR-7 `component_code` is a permission-map key** — matches keys in the session JWT's `page_access` map. Whether the Hub API filters menus by entitlement or returns everything and defers gating to the client is the single most consequential undecided business rule here.
- **BR-8 `tracking_info` is not contract-stable — P0 finding.** `ga_user_properties` is an **empty array** in the documented sample and a **populated object** in the real captured response — a type change on the same field, not a value change. Any strongly-typed client will fail to deserialize one of the two shapes.
- **BR-9 PII in a navigation endpoint** — the real response's `we_user_properties` carries the user's plaintext name/email; `ga_user_properties.sha256_email_address` carries a hashed one alongside it. Needs an explicit access-scoping and necessity decision, not an implicit one.
- **BR-10 `disclosures_data` mixes omission and polymorphism** — the 8th entry omits `title` outright; `details` has two undiscriminated shapes (`{list}` vs `{desc}`).
- **BR-11 `data_fmt` type depends on a sibling field's value** — string for Name/Email, number for Mobile Number. A phone number as a JSON number is itself a latent formatting risk (leading zeros, country codes).

---

## 3. Test Suite

Test Case ID scheme: `HUB-<CATEGORY>-<NN>`. "Automation Candidate" reflects whether the case is realistically automatable in this framework today (a pure JSON/HTTP check) vs. requiring manual/cross-team judgment (e.g., live link-health crawls, UX/product sign-off, real multi-account entitlement fixtures).

### A. Functional

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-FUN-01 | Functional | Valid request returns full hub | Valid `auth-token` | GET `/api/v0/hub/` with valid token | 200, `status:true`, `data.hub` populated | P0 | Critical | Functional | Yes |
| HUB-FUN-02 | Functional | `inpage_title` correctness | Valid token | GET hub | `data.inpage_title == "The Hub"` | P2 | Low | Functional | Yes |
| HUB-FUN-03 | Functional | All expected hub groups present | Valid token | GET hub | `hub[]` contains `profile`, `investor_account`, `mutual_funds`, `stocks`, `more` (baseline set) | P1 | High | Functional | Yes |
| HUB-FUN-04 | Functional | Menu count per group matches baseline | Valid token, known baseline | GET hub, compare menu counts per group to last signed-off baseline | Counts match, or diff is a reviewed/approved content change | P1 | Medium | Regression | Yes |
| HUB-FUN-05 | Functional | `add_investor` block structurally correct | Valid token | GET hub | `add_investor` has `is_show`, `is_active`, `title`, `url`, `component_code` | P1 | Medium | Functional | Yes |
| HUB-FUN-06 | Functional | `is_add_investor_allowed` reflects real eligibility | User at max investor limit | GET hub | `is_add_investor_allowed == false` | P1 | High | Business Logic | Partial (needs fixture) |
| HUB-FUN-07 | Functional | Statutory disclosure present | Valid token | GET hub | `data.disclosures[]` contains a "Statutory Disclosures" entry with non-empty `text` | P2 | Medium | Functional | Yes |
| HUB-FUN-08 | Functional | Legacy vs versioned endpoint parity | Valid token | GET `/api/hub/` and `/api/v0/hub/`, diff results | Structurally equivalent (or divergence is a signed-off, intentional deprecation) | P1 | High | Contract | Yes |
| HUB-FUN-09 | Functional | Response stability across repeated calls | Valid token, unchanged session | GET hub twice in quick succession | Identical structural response (tracking nonces excluded) | P2 | Medium | Regression | Yes |

### B. Response Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-RESP-01 | Response | Top-level `status` on success | Valid token | GET hub | `status == true` (boolean, not string/int) | P0 | Critical | Functional | Yes |
| HUB-RESP-02 | Response | HTTP 200 on success | Valid token | GET hub | HTTP status code is exactly 200 | P0 | Critical | Functional | Yes |
| HUB-RESP-03 | Response | Content-Type correctness | Valid token | GET hub | Response `Content-Type: application/json` (charset tolerated) | P1 | Medium | Functional | Yes |
| HUB-RESP-04 | Response | `page_info` fully populated | Valid token | GET hub | `lang`, `title`, `inpage_title`, `meta_keywords` all non-null strings | P2 | Low | Functional | Yes |
| HUB-RESP-05 | Response | `tracking_info` core fields present | Valid token | GET hub | `canonical_url` and `url_for_sharing` present and non-empty | P2 | Low | Functional | Yes |

### C. Schema Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-SCH-01 | Schema | Full response against JSON Schema | Valid token, schema authored | GET hub, validate against schema covering hub/menu/product/disclosures_data/tracking_info | Validates with zero violations | P0 | Critical | Contract | Yes |
| HUB-SCH-02 | Schema | Product schema ≠ Menu schema | Schema authored | Validate a `products[]` entry against a Product-specific schema (no `id`/`icon`/`action_hint` required) | Passes; also fails if validated against Menu schema (proves the two aren't interchangeable) | P1 | High | Contract | Yes |
| HUB-SCH-03 | Schema | `details` polymorphism (`list` vs `desc`) | Schema authored with `oneOf` | Validate every `disclosures_data.data[].details` entry | Each matches exactly one of the two shapes, none matches neither/both | P1 | High | Contract | Yes |
| HUB-SCH-04 | Schema | `data_fmt` conditional type by `name` | Schema authored with `if/then` | Validate every `details.list[]` entry | `name=="Mobile Number"` → number; else → string | P1 | High | Contract | Yes |
| HUB-SCH-05 | Schema | `ga_user_properties` array-vs-object (BR-8) | Both real sample payloads available | Validate documented sample and live sample against the same schema | **Expected to currently fail on one of the two** — this is the regression test that proves BR-8 is fixed once schema/backend are aligned | P0 | Critical | Contract | Yes |
| HUB-SCH-06 | Schema | `title` optional in `disclosures_data.data[]` | Schema authored | Validate the entry that omits `title` | Schema treats `title` as optional, not required — no false failure | P2 | Medium | Contract | Yes |

### D. Header Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-HDR-01 | Headers | Mandatory response headers present | Valid token | GET hub, inspect response headers | `Content-Type` present and correct | P1 | Medium | Functional | Yes |
| HUB-HDR-02 | Headers | No credential echoed back | Valid token | GET hub, inspect all response headers | `auth-token` value never echoed in any response header | P0 | Critical | Security | Yes |
| HUB-HDR-03 | Headers | CORS headers sane for webview use | Valid token, cross-origin request | GET hub with `Origin` header from an allowed webview origin | Appropriate `Access-Control-Allow-*` headers, no wildcard `*` alongside credentialed requests | P1 | High | Security | Yes |
| HUB-HDR-04 | Headers | Baseline security headers present | Valid token | GET hub, inspect headers | `X-Content-Type-Options: nosniff` (and equivalents) present per platform security baseline | P2 | Medium | Security | Yes |

### E. Authentication

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-AUTH-01 | Auth | Valid `auth-token` succeeds | Valid token | GET hub | 200 success | P0 | Critical | Functional | Yes |
| HUB-AUTH-02 | Auth | Cookie-only auth (no header) | Valid `user_session`/`auth_token` cookies, no `auth-token` header | GET hub with cookies only | Document actual behavior: if 401, cookie auth is not a fallback (expected); if 200, this is an undocumented second auth channel needing its own security review | P0 | Critical | Security | Yes |
| HUB-AUTH-03 | Auth | Header vs cookie precedence when they disagree | Valid header token for User A, cookie session for User B | GET hub with both present, mismatched | Response must reflect the `auth-token` header's user only, never a blend | P0 | Critical | Security | Yes |
| HUB-AUTH-04 | Auth | `Authorization` header alone is insufficient | Valid `Authorization: Basic ...`/`Bearer ...`, no `auth-token` | GET hub with only `Authorization` set | 401 — confirms `Authorization` is not a user-auth channel | P1 | High | Security | Yes |

### F. Authorization

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-AUTHZ-01 | Authorization | Fund-Advisor-only entitlement | User subscribed to Fund Advisor only | GET hub | Fund-Advisor-relevant menus/products resolve correctly; no Stock-Advisor-exclusive action is presented as available | P1 | High | Business Logic | Partial |
| HUB-AUTHZ-02 | Authorization | Stock-Advisor-only entitlement | User subscribed to Stock Advisor only | GET hub | Symmetric to AUTHZ-01 | P1 | High | Business Logic | Partial |
| HUB-AUTHZ-03 | Authorization | Anonymous/guest session | Unauthenticated but app-issued guest token, if supported | GET hub | Entitlement-gated fields (`is_add_investor_allowed`, etc.) reflect guest state correctly, no crash on absent entitlement | P1 | Medium | Business Logic | Partial |
| HUB-AUTHZ-04 | Authorization | `component_code` vs session `page_access` (BR-7) | Session JWT has `is_accessible:false` for a code that also appears as a menu's `component_code` | GET hub, cross-check menu list against JWT's `page_access` map | Document actual behavior against the team's intended design (hide vs. return-and-let-client-gate) — currently undecided, flagged for product sign-off | P0 | Critical | Business Logic | No |
| HUB-AUTHZ-05 | Authorization | Cross-user data isolation | Two distinct valid tokens (User A, User B) | GET hub as A, then as B | Each response's PII/tracking (BR-9) belongs strictly to its own token's user; never blended or cached across users | P0 | Critical | Security | Yes |

### G. Invalid Token

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-TOK-INV-01 | Invalid Token | Random malformed token | — | GET hub, `auth-token: randomgarbage123` | 401, no 500 | P0 | Critical | Negative | Yes |
| HUB-TOK-INV-02 | Invalid Token | SQLi payload as token | — | `auth-token: ' OR '1'='1` | 401, no SQL error text in body | P0 | Critical | Security | Yes |
| HUB-TOK-INV-03 | Invalid Token | XSS payload as token | — | `auth-token: <script>alert(1)</script>` | 401, payload never reflected anywhere in response | P0 | Critical | Security | Yes |
| HUB-TOK-INV-04 | Invalid Token | Truncated valid token | Valid token available | Remove last 8 chars of a valid token, GET hub | 401 | P1 | High | Negative | Yes |
| HUB-TOK-INV-05 | Invalid Token | Valid token with appended junk | Valid token available | Append random chars to a valid token, GET hub | 401 | P1 | High | Negative | Yes |

### H. Missing Token

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-TOK-MISS-01 | Missing Token | No `auth-token` header at all | — | GET hub, omit `auth-token` entirely | 401, not 500/200 | P0 | Critical | Negative | Yes |
| HUB-TOK-MISS-02 | Missing Token | Empty-string token | — | `auth-token:` (empty value) | 401 | P0 | Critical | Negative | Yes |
| HUB-TOK-MISS-03 | Missing Token | Whitespace-only token | — | `auth-token:    ` | 401 | P1 | High | Negative | Yes |

### I. Expired Token

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-TOK-EXP-01 | Expired Token | Genuinely expired token | A token past its server-side TTL | GET hub | 401/403 | P0 | Critical | Negative | Yes |
| HUB-TOK-EXP-02 | Expired Token | Framework auto-refresh-and-retry | Expired cached token, valid credentials for re-login | GET hub via the automation framework's `auth-token` flow | Framework re-logs in once and retries; final result is 200 with a fresh token, transparently to the caller | P1 | High | Functional (framework behavior) | Yes |
| HUB-TOK-EXP-03 | Expired Token | Expiry mid-large-payload fetch | N/A today (single-shot response, no pagination) | Documented as a forward-looking case if pagination/streaming is introduced | Not applicable until AE (Pagination) becomes real | P3 | Low | Edge | No |

### J. Wrong HTTP Method

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-METH-01 | Method | POST rejected | Valid token | POST `/api/v0/hub/` | 405 Method Not Allowed | P1 | High | Negative | Yes |
| HUB-METH-02 | Method | PUT rejected | Valid token | PUT `/api/v0/hub/` | 405 | P1 | High | Negative | Yes |
| HUB-METH-03 | Method | DELETE rejected | Valid token | DELETE `/api/v0/hub/` | 405 | P1 | High | Negative | Yes |
| HUB-METH-04 | Method | PATCH rejected | Valid token | PATCH `/api/v0/hub/` | 405 | P1 | High | Negative | Yes |
| HUB-METH-05 | Method | HEAD behavior | Valid token | HEAD `/api/v0/hub/` | Document actual behavior: either 200 with headers-only/no body, or 405 — must be consistent and documented | P2 | Medium | Negative | Yes |
| HUB-METH-06 | Method | OPTIONS/CORS preflight | Valid token | OPTIONS `/api/v0/hub/` | Sensible preflight response if CORS is in use; no 500 | P2 | Medium | Negative | Yes |

### K. Invalid Headers

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-HDR-INV-01 | Invalid Headers | Unrecognized custom header | Valid token | GET hub with an arbitrary extra header | Ignored, 200 as normal | P3 | Low | Negative | Yes |
| HUB-HDR-INV-02 | Invalid Headers | Oversized header value | Valid token | GET hub with a 16KB+ header value | Graceful 400/431, not a crash/500 | P2 | Medium | Negative | Yes |

### L. Invalid Accept Header

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-HDR-ACC-01 | Accept Header | Non-JSON Accept | Valid token | `Accept: text/xml`, GET hub | Document actual behavior: JSON served regardless (lenient), or 406 (strict) — either is acceptable if consistent and documented | P2 | Medium | Negative | Yes |
| HUB-HDR-ACC-02 | Accept Header | Missing Accept header | Valid token | Omit `Accept` entirely | 200, JSON returned by default | P2 | Low | Negative | Yes |

### M. Invalid Content-Type

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-HDR-CT-01 | Content-Type | Meaningless Content-Type on GET | Valid token | `Content-Type: application/xml` on a bodyless GET | Ignored, 200 as normal | P3 | Low | Negative | Yes |
| HUB-HDR-CT-02 | Content-Type | multipart Content-Type on GET | Valid token | `Content-Type: multipart/form-data` on a bodyless GET | Ignored, 200 as normal | P3 | Low | Negative | Yes |

### N. Missing Mandatory Headers

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-HDR-MISS-01 | Missing Headers | Missing `auth-token` (cross-ref TOK-MISS-01) | — | GET hub, no `auth-token` | 401 | P0 | Critical | Negative | Yes |
| HUB-HDR-MISS-02 | Missing Headers | Missing `User-Agent` | Valid token | GET hub, omit `User-Agent` | 200 — not mandatory | P3 | Low | Negative | Yes |

### O. Empty Response

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-EMPTY-RESP-01 | Empty Response | New/zero-entitlement user | Freshly onboarded user, no products | GET hub | Core navigation (`profile`, `more`, etc.) still present; only entitlement-dependent sections vary | P1 | High | Edge | Partial |
| HUB-EMPTY-RESP-02 | Empty Response | `data: null` with `status:true` | Simulated/contrived server response | GET hub (mocked) | Client fails gracefully with a clear error, never an NPE/crash | P1 | High | Negative | Yes |

### P. Null Values

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-NULL-01 | Null Values | `component_code: null` handling | Menu with null component_code | GET hub | Client treats as "no permission gate," never as an error | P2 | Medium | Functional | Yes |
| HUB-NULL-02 | Null Values | `download_url: null` handling | Menu with null download_url | GET hub | No download affordance offered for that menu | P2 | Medium | Functional | Yes |
| HUB-NULL-03 | Null Values | `products: null` handling | Menu with null products | GET hub | Single default `url`/`is_ext_url` used; no null-array iteration crash | P1 | High | Negative | Yes |
| HUB-NULL-04 | Null Values | `ga_uid: null` (anonymous) | Anonymous/guest session | GET hub | Tracking still functions without a uid; no crash | P2 | Medium | Edge | Yes |
| HUB-NULL-05 | Null Values | `ga_tracking_url: null` | Any session | GET hub | No broken tracking call attempted when null | P3 | Low | Edge | Yes |

### Q. Invalid Data Types

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-TYPE-01 | Data Types | `is_ext_url` as string | Simulated/contract-drift payload | Validate a payload where `is_ext_url: "false"` (string) | Schema validation fails — must be a real boolean | P1 | High | Contract | Yes |
| HUB-TYPE-02 | Data Types | `id` as integer | Simulated/contract-drift payload | Validate a payload where a menu `id` is a number | Schema validation fails — must be a string | P1 | High | Contract | Yes |
| HUB-TYPE-03 | Data Types | `data_fmt` type/name mismatch | Simulated payload | Validate `{"name":"Mobile Number","data_fmt":"123"}` (string instead of number) and `{"name":"Name","data_fmt":123}` (number instead of string) | Both fail the conditional schema (BR-11) | P1 | High | Contract | Yes |
| HUB-TYPE-04 | Data Types | `ga_user_properties` shape regression (BR-8) | Both real sample payloads | Assert the field's declared type against both the documented and the live sample | Currently inconsistent — this is the tracked regression case for the fix | P0 | Critical | Contract | Yes |

### R. Invalid URLs

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-URL-INV-01 | Invalid URLs | Malformed URL syntax | Simulated payload | Validate `"url": "htp:/broken"` | Fails URL-format contract check | P2 | Medium | Contract | Yes |
| HUB-URL-INV-02 | Invalid URLs | `javascript:` scheme URL | Simulated payload | Validate `"url": "javascript:alert(1)"` | Must never be served — security-relevant contract violation | P0 | Critical | Security | Yes |
| HUB-URL-INV-03 | Invalid URLs | Real `is_ext_url` mismatches (BR-4) | Live sample data | Assert `is_ext_url` against each URL's actual origin for `stock_screener`, `about_us`, `terms_n_conditions` | All three currently fail — pinned regression cases until fixed | P0 | Critical | Business Logic | Yes |

### S. Duplicate IDs

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-DUP-ID-01 | Duplicate IDs | Menu ID uniqueness | Valid token | GET hub, collect all menu `id`s across all groups | Zero duplicates across the entire response | P1 | High | Contract | Yes |
| HUB-DUP-ID-02 | Duplicate IDs | Hub group ID uniqueness | Valid token | GET hub, collect all `hub[].id` | Zero duplicates | P1 | High | Contract | Yes |

### T. Duplicate Menus

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-DUP-MENU-01 | Duplicate Menus | Functional duplicate (same title+url, different id) | Valid token | GET hub, group by `(title, url)` within each menu list | No group has >1 entry | P2 | Medium | Contract | Yes |

### U. Empty Arrays

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-EMPTY-ARR-01 | Empty Arrays | `hub: []` | Simulated payload | Render/parse an empty hub array | Client shows a "no navigation available" state, not a crash | P1 | High | Edge | Yes |
| HUB-EMPTY-ARR-02 | Empty Arrays | `menus: []` for a group | Simulated payload | A hub group with an empty menu list | Documented product decision: hide the group or show it empty — must be consistent | P2 | Medium | Edge | No |
| HUB-EMPTY-ARR-03 | Empty Arrays | `disclosures: []` | Simulated payload | Empty disclosures array | No disclosure section rendered, no error | P2 | Low | Edge | Yes |
| HUB-EMPTY-ARR-04 | Empty Arrays | `we_user_properties: []` (as in documented sample) | Documented sample payload | Parse the documented sample | Tracking degrades gracefully with zero properties | P3 | Low | Edge | Yes |

### V. Empty Objects

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-EMPTY-OBJ-01 | Empty Objects | `add_investor: {}` | Simulated payload | Parse an empty `add_investor` object | Client treats as "feature unavailable," does not crash reading `is_show`/`url` | P1 | High | Edge | Yes |
| HUB-EMPTY-OBJ-02 | Empty Objects | `details: {}` (neither `list` nor `desc`) | Simulated payload | Parse a disclosure entry with empty `details` | Graceful "no details" render, no crash | P2 | Medium | Edge | Yes |

### W. Broken Download URLs

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-DL-URL-01 | Download URLs | `download_url` points to a dead resource | Live `download_url` value | Probe `download_url` directly (separate from the Hub API call itself) | Flagged as a periodic link-health check, not a hard gate on this API's own release — cross-team dependency | P2 | Medium | Edge | No |
| HUB-DL-URL-02 | Download URLs | `download_url` cross-domain but `is_ext_url:false` | Live data (mirrors BR-4's pattern) | Check every non-null `download_url`'s domain against its menu's `is_ext_url` | Consistent, or flagged as the same class of defect as BR-4 | P1 | High | Business Logic | Yes |

### X. Broken Navigation URLs

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-NAV-URL-01 | Navigation URLs | Internal `url` resolves to a real route | Live data | Probe each internal `url` against the app's own routing (out of Hub API's direct scope, tracked separately) | No dead internal links; tracked as a periodic regression, not a per-release blocker | P2 | Medium | Edge | No |
| HUB-NAV-URL-02 | Navigation URLs | External `url` returns 2xx/3xx | Live data | Probe each `is_ext_url:true` URL | No dead external links; same tracking note as above | P3 | Low | Edge | No |

### Y. Boolean Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-BOOL-01 | Boolean | `status:false` carries a real explanation | Forced error condition | Trigger a genuine failure (e.g. expired token) | `status:false` accompanied by a meaningful error body, not a bare `false` | P1 | High | Negative | Yes |
| HUB-BOOL-02 | Boolean | `is_add_investor_allowed` correctness | User at the multi-investor limit | GET hub | Value correctly reflects real eligibility, not a hardcoded `true` | P1 | High | Business Logic | Partial |
| HUB-BOOL-03 | Boolean | Full `is_ext_url` correctness sweep | Live data, all menus | Cross-check every menu's `is_ext_url` against its URL's actual origin | Zero mismatches (currently 3 known failures per BR-4 — this case's purpose is catching any *others* beyond those three) | P0 | Critical | Business Logic | Yes |

### Z. Enum Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-ENUM-01 | Enum | `lang` restricted to supported locales | Multiple locale requests | GET hub for each supported locale | `lang` always one of the app's registered locale codes, never an arbitrary string | P2 | Medium | Contract | Yes |
| HUB-ENUM-02 | Enum | `component_code` restricted to the registered set | Live data + app's known code registry | Cross-check every non-null `component_code` against the registry | No unregistered/unknown code ever appears | P1 | High | Contract | Yes |

### AA. Performance

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-PERF-01 | Performance | Single-user response time SLA | Valid token | GET hub, measure elapsed time | Within team-agreed SLA (e.g. <500ms) — baseline must be explicitly agreed, not assumed | P1 | High | Performance | Yes |
| HUB-PERF-02 | Performance | Concurrent load | Valid tokens for N virtual users | Fire 50–100 concurrent GETs | No degradation beyond agreed SLA, no error-rate increase | P1 | High | Performance | Partial |
| HUB-PERF-03 | Performance | Payload size tracked release-over-release | Baseline payload size recorded | GET hub, measure response size, compare to last release | No unexplained bloat; growth is a reviewed, intentional content change | P2 | Medium | Performance | Yes |

### AB. Security

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-SEC-01 | Security | Cross-environment token rejected | Valid token from a different environment (e.g. dev) | GET hub on qa with a dev-issued token | 401 — tokens must not be valid across environments | P0 | Critical | Security | Yes |
| HUB-SEC-02 | Security | PII strictly scoped to the token's own user (BR-9) | Two valid tokens, two users | GET hub as each; compare `we_user_properties`/`sha256_email_address` | Each response's PII belongs only to its own user, under any header manipulation | P0 | Critical | Security | Yes |
| HUB-SEC-03 | Security | No internal error detail leaked | Any forced error path | Trigger a 4xx/5xx | No stack trace, DB error, or server version string in the response | P0 | Critical | Security | Yes |
| HUB-SEC-04 | Security | HTTPS enforced | — | Attempt plain HTTP to the same host/path | Redirected to HTTPS or rejected; never served in plaintext | P1 | High | Security | Yes |
| HUB-SEC-05 | Security | Brute-force protection on invalid tokens | — | Send many rapid invalid-token requests | Rate-limiting/throttling engages; no unbounded retry surface | P2 | High | Security | Partial |

### AC. Caching

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-CACHE-01 | Caching | `Cache-Control` appropriate for personalized, PII-bearing content | Valid token | GET hub, inspect `Cache-Control` | `private`/`no-store` (or equivalent) — given BR-9's PII finding, this must **not** be publicly/CDN-cacheable | P0 | Critical | Security | Yes |
| HUB-CACHE-02 | Caching | No cross-user cache bleed | Two users, shared CDN/proxy layer if any | GET hub as User A, then User B via the same edge cache | User B never receives User A's cached, personalized response | P0 | Critical | Security | Partial |

### AD. Response Time

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-RESPTIME-01 | Response Time | P95/P99 latency under repeated load | Valid token | Fire N sequential/parallel requests, compute percentiles | P95/P99 within agreed SLA, not just the best-case single-shot number | P1 | High | Performance | Yes |

### AE. Pagination (if applicable)

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-PAGN-01 | Pagination | No pagination today; params must not silently alter response | Valid token | GET hub with `?page=2`, `?limit=5`, `?offset=10` appended | Response unchanged (params ignored) — proves pagination isn't silently half-implemented; future introduction must be a deliberate, versioned contract change | P2 | Medium | Contract | Yes |

### AF. Localization

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-I18N-01 | Localization | Supported alternate locale | `Accept-Language` for a supported locale | GET hub with that header | Titles/`action_hint`/disclosures localized, or documented as English-only if that's the real scope | P2 | Medium | Functional | Yes |
| HUB-I18N-02 | Localization | Unsupported/garbage locale | `Accept-Language: xx-ZZ` | GET hub | Graceful fallback to default locale, not an error | P2 | Medium | Negative | Yes |

### AG. HTML Injection

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-HTML-INJ-01 | HTML Injection | Only `desc` fields legitimately carry markup | Live data | Scan every `title`/`action_hint`/menu title for HTML tags | None found outside the sanctioned `disclosures_data...desc` fields | P1 | High | Security | Yes |
| HUB-HTML-INJ-02 | HTML Injection | Any user-influenced value is sanitized | Any field ever populated from user input (e.g. a future display-name field) | Inject `<b>test</b>` style markup at the source, GET hub | Rendered escaped, never raw-injected | P1 | High | Security | Yes |

### AH. XSS Payload Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-XSS-01 | XSS | Script payload in every attacker-reachable input | — | Send `<script>alert(1)</script>` in `auth-token` and any header | Never reflected unescaped in response body or error | P0 | Critical | Security | Yes |
| HUB-XSS-02 | XSS | Disclosure HTML fields don't allow script-executing tags | Live `desc` content | Content-review the sanctioned HTML allowlist (`<p>`, `<br>`, `<a>`) | No `<script>`/`onerror=`/similar ever present, by policy | P1 | High | Security | Partial |

### AI. SQL Injection Payload Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-SQLI-01 | SQL Injection | Classic payloads in `auth-token` | — | `auth-token: ' OR '1'='1`, `auth-token: '; DROP TABLE users;--` | 401, no SQL error text, no behavioral difference | P0 | Critical | Security | Yes |
| HUB-SQLI-02 | SQL Injection | Payloads in other headers | — | Inject the same payloads into `Referer`/`User-Agent`/custom headers | No behavioral difference, no error leakage | P1 | High | Security | Yes |

### AJ. Unicode Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-UNI-01 | Unicode | Existing curly-quote title renders correctly | Live data (`"Analyst's Choice"` uses U+2019) | GET hub, inspect the raw bytes/encoding of that title | Correct UTF-8 round-trip, not mojibake | P2 | Medium | Functional | Yes |
| HUB-UNI-02 | Unicode | Multi-byte Unicode (emoji/CJK/RTL) in a hypothetical title | Simulated content | Author a test menu title with emoji/CJK/Arabic | JSON encodes/decodes correctly, no rendering break | P2 | Medium | Edge | Yes |

### AK. Special Characters

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-SPCHAR-01 | Special Characters | Ampersand in titles | Live data (`"Stories & Videos"`, `"Terms & Conditions"`) | GET hub, inspect JSON encoding | Correctly escaped, not double-encoded/broken | P2 | Low | Functional | Yes |
| HUB-SPCHAR-02 | Special Characters | HTML entities in disclosures (`&copy;`) | Live data | GET hub, inspect the copyright disclosure text | Renders as intended, not double-escaped (`&amp;copy;`) | P2 | Low | Functional | Yes |

### AL. Large Response Handling

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-LARGE-01 | Large Response | 1000-menu hub | Synthetic/staged large dataset | GET hub against a staged environment with 1000 menus | No timeout, no OOM, response parses correctly end-to-end | P2 | Medium | Performance | Partial |
| HUB-LARGE-02 | Large Response | 50+ entry `disclosures_data` | Synthetic/staged large dataset | GET hub with an inflated disclosures_data list | No truncation, no performance cliff | P3 | Low | Performance | Partial |

### AM. Contract Validation

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-CONTRACT-01 | Contract | Documented sample vs. live response diff | Both samples available | Structural diff, field-by-field | Every difference is either expected (e.g. populated vs. empty tracking) or a tracked defect (BR-8's type change) | P0 | Critical | Contract | Yes |
| HUB-CONTRACT-02 | Contract | Product/Menu schema never merge | Schema suite | Run schema validation across a full historical corpus of responses if available | No response ever conflates the two shapes | P2 | Medium | Contract | Yes |

### AN. Backward Compatibility

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-BC-01 | Backward Compatibility | Legacy Fawkes endpoint still serves older clients | Legacy endpoint still deployed | GET `/api/hub/` | Returns a response older app builds can still parse, if the endpoint remains supported | P1 | High | Contract | Yes |
| HUB-BC-02 | Backward Compatibility | No field removed without deprecation | Version history available | Diff current response against N-1 release's captured response | No previously-present field silently disappears | P1 | High | Contract | Partial |

### AO. Future Compatibility

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-FC-01 | Future Compatibility | Client tolerates unknown new fields | Simulated payload with an extra unexpected field | Parse a response with a field the client doesn't know about | No crash, field simply ignored — direct mitigation for BR-8-style drift | P1 | High | Contract | Yes |
| HUB-FC-02 | Future Compatibility | Client doesn't assume fixed group count/order | Simulated payload with a 6th hub group or reordered groups | Parse and render | UI/client handles additional or reordered groups without breaking | P2 | Medium | Contract | Yes |

---

## 4. Business Validation (explicit cross-reference)

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-BIZ-01 | Business Validation | Icon-to-ID naming convention (BR-5) | Live data | For every menu, assert `icon == "/assets/images/hub_icon/" + id + ".svg"` | Holds for all menus; any exception is a content bug | P2 | Medium | Business Logic | Yes |
| HUB-BIZ-02 | Business Validation | `action_hint` is meaningful, not a placeholder | Live data | Assert every `action_hint` is non-empty and not a lorem-ipsum/TBD-style placeholder | All 36 pass | P2 | Low | Business Logic | Partial |
| HUB-BIZ-03 | Business Validation | `products[]` length is 0 or exactly 2 (BR-6) | Live data | Assert every non-null `products` array has length exactly 2 | Holds today; any future 1 or 3+ is flagged for product review, not silently accepted | P1 | Medium | Business Logic | Yes |
| HUB-BIZ-04 | Business Validation | `download_url` distinct from `url` when both present | Live data (`terms_n_conditions`) | Assert `download_url != url` wherever both are non-null | Holds; a menu with identical `url`/`download_url` would be a data-quality smell | P2 | Low | Business Logic | Yes |

---

## 5. Edge Cases

| Test Case ID | Module | Scenario | Precondition | Test Steps | Expected Result | Priority | Severity | Test Type | Automation Candidate |
|---|---|---|---|---|---|---|---|---|---|
| HUB-EDGE-01 | Edge Case | Empty Hub | Simulated payload | `hub: []` | Cross-ref EMPTY-ARR-01 | P1 | High | Edge | Yes |
| HUB-EDGE-02 | Edge Case | Single hub group, single menu | Simulated minimal payload | One group, one menu, all required fields present | Parses correctly — no code assumes ≥N items anywhere | P2 | Medium | Edge | Yes |
| HUB-EDGE-03 | Edge Case | 1000 menus | Cross-ref LARGE-01 | — | — | P2 | Medium | Edge | Partial |
| HUB-EDGE-04 | Edge Case | Null menu entry (literal `null` inside `menus[]`) | Simulated malformed payload | `menus: [null, {...valid...}]` | Server should never send this; if it somehow does, client skips gracefully rather than crashing | P1 | High | Negative | Yes |
| HUB-EDGE-05 | Edge Case | Duplicate menu (same `id` twice in one group) | Cross-ref DUP-ID-01 / DUP-MENU-01 | — | — | P1 | High | Negative | Yes |
| HUB-EDGE-06 | Edge Case | Null `products` | Cross-ref NULL-03 | — | — | P1 | High | Negative | Yes |
| HUB-EDGE-07 | Edge Case | Missing `icon` field (structurally absent, not null) | Simulated malformed payload | Remove the `icon` key entirely from a menu object | Violates BR-1 — schema validation fails | P1 | High | Contract | Yes |
| HUB-EDGE-08 | Edge Case | Invalid `component_code` format | Simulated malformed payload | `component_code: "my invalid code!"` (lowercase, spaces, punctuation) | Fails the `UPPER_SNAKE_CASE` contract check | P2 | Medium | Contract | Yes |
| HUB-EDGE-09 | Edge Case | Relative URL missing leading slash | Simulated payload | `url: "myaccount/"` (no leading `/`) | Document whether this resolves correctly client-side or must be rejected server-side | P2 | Medium | Edge | Yes |
| HUB-EDGE-10 | Edge Case | Absolute URL missing scheme | Simulated payload | `url: "//qastock.valueresearch.in/"` (protocol-relative) | Document expected handling; protocol-relative URLs behave differently across app/webview contexts | P2 | Medium | Edge | Yes |
| HUB-EDGE-11 | Edge Case | `download_url` missing where business expects one | Content-authoring review | Identify any legal/document-style menu with `download_url: null` that should logically have one | Flags a content gap for the content team, not a hard API defect | P3 | Low | Business Logic | No |
| HUB-EDGE-12 | Edge Case | `action_hint` present but empty string | Simulated payload | `action_hint: ""` | Distinct from missing — client must not render an empty tooltip/label | P2 | Medium | Edge | Yes |
| HUB-EDGE-13 | Edge Case | Large metadata (multi-KB `meta_keywords`/`meta_description`) | Simulated payload | Populate with an unusually long string | No truncation issue, no layout break | P3 | Low | Edge | Yes |
| HUB-EDGE-14 | Edge Case | `tracking_info` missing entirely | Simulated payload | Remove the `tracking_info` key | Client tracking degrades without breaking the rest of the page | P1 | High | Negative | Yes |
| HUB-EDGE-15 | Edge Case | `disclosures` key missing entirely (not empty array) | Simulated payload | Remove the `disclosures` key | Distinct from EMPTY-ARR-03 — client must handle absence, not just emptiness | P2 | Medium | Negative | Yes |
| HUB-EDGE-16 | Edge Case | Large `disclosures[]` list (50+ entries) | Simulated payload | Inflate the top-level `disclosures` array | No scroll/render performance cliff | P3 | Low | Performance | Partial |
| HUB-EDGE-17 | Edge Case | RTL/CJK title | Cross-ref UNI-02 | — | — | P2 | Medium | Edge | Yes |
| HUB-EDGE-18 | Edge Case | Long title (200+ characters) | Simulated payload | A menu title of 200+ chars | No truncation corrupts meaning, no layout break | P3 | Low | Edge | Yes |
| HUB-EDGE-19 | Edge Case | Special characters beyond `&` (quotes, backslashes, pipes) | Simulated payload | Title containing `"`, `\`, and a pipe character | Correct JSON escaping, no parse break | P2 | Medium | Edge | Yes |

---

## 6. Final Summary

### 6.1 Totals by category

| Category | Count |
|---|---|
| Total Functional Test Cases (A, B) | 14 |
| Total Negative Test Cases (G, H, J, K, L, M, N, O, P subset, Q, EDGE subset) | 45 |
| Total Edge Test Cases (U, V, W, X, Edge Cases section) | 34 |
| Total Security Test Cases (E subset, F subset, AB, AG, AH, AI) | 27 |
| Total Performance Test Cases (AA, AD, AL, EDGE-16) | 7 |
| Total Contract Validation Cases (C, S, T, Y-03, Z, AM, AN, AO) | 24 |
| **Grand total test cases in this suite** | **145** |

*(Some cases legitimately count toward more than one category above — e.g. HUB-SQLI-01 is both Negative and Security; the grand total reflects unique Test Case IDs, not the sum of the category column.)*

### 6.2 Release Blocker Test Cases (P0)

These must pass (or have an explicit, signed-off exception) before this API ships:

- HUB-FUN-01, HUB-RESP-01, HUB-RESP-02 — basic success path
- HUB-SCH-01, HUB-SCH-05, HUB-TYPE-04 — schema validity, and specifically **BR-8's `ga_user_properties` array-vs-object contract defect**
- HUB-HDR-02 — no credential echo
- HUB-AUTH-02, HUB-AUTH-03 — cookie-vs-header auth precedence and isolation
- HUB-AUTHZ-04 — entitlement/`component_code` gating decision (needs product sign-off, not just a test result)
- HUB-AUTHZ-05, HUB-SEC-02 — cross-user data isolation (PII per BR-9)
- HUB-TOK-INV-01/02/03, HUB-TOK-MISS-01/02, HUB-TOK-EXP-01 — auth failure handling
- HUB-URL-INV-02 — `javascript:` scheme URLs
- HUB-URL-INV-03, HUB-BOOL-03 — the three known `is_ext_url` mismatches (BR-4) and the full-sweep case
- HUB-SEC-01, HUB-SEC-03 — cross-environment tokens, no internal error leakage
- HUB-CACHE-01, HUB-CACHE-02 — no public/cross-user caching of PII-bearing content
- HUB-XSS-01, HUB-SQLI-01 — injection handling
- HUB-CONTRACT-01 — the full documented-vs-live diff

**18 P0 cases**, three of which (BR-4's three known mismatches, BR-8's type instability, and BR-7's entitlement-gating question) are not just "run and see" — they are **known, already-observed defects/open decisions in the provided sample data itself**, not hypothetical risks.

### 6.3 Recommended Smoke Suite

Fast, must-always-pass, run on every deploy:

HUB-FUN-01, HUB-RESP-01, HUB-RESP-02, HUB-RESP-03, HUB-SCH-01, HUB-AUTH-01, HUB-TOK-MISS-01, HUB-TOK-INV-01, HUB-METH-01, HUB-HDR-02

### 6.4 Recommended Regression Suite

Everything in Smoke, plus: all of Sections A–F in full, HUB-SCH-02 through 06, HUB-TYPE-01 through 04, HUB-URL-INV-01 through 03, HUB-DUP-ID-01/02, HUB-DUP-MENU-01, HUB-BOOL-01 through 03, HUB-ENUM-01/02, HUB-CACHE-01/02, HUB-CONTRACT-01/02, HUB-BC-01/02, HUB-FC-01/02, and Section 4 (Business Validation) in full. Security (AB, AG, AH, AI) runs in regression, not smoke — it's too slow/noisy for a fast gate but must run before every release.

### 6.5 Recommended API Automation Order

1. **Functional + Response + Schema (A, B, C)** — cheapest, highest immediate value, catches the BR-8 contract defect on day one.
2. **Authentication + Authorization + token edge cases (E–I)** — this framework already has the `auth-token`/login/retry machinery built; these slot in directly.
3. **Business Validation + Contract (Section 4, AM, S, T, Z)** — the `is_ext_url`/`component_code`/uniqueness sweeps are pure JSON assertions, no live navigation needed.
4. **Negative/Header/Method (G–N)** — mechanical, high automation value, low effort.
5. **Security (AB, AG, AH, AI)** — payload-injection cases are automatable now; brute-force/rate-limit and CDN cache-isolation cases need environment support first.
6. **Performance (AA, AD)** — needs an agreed SLA baseline from the team before automating pass/fail thresholds, not before.
7. **Edge/Large-response/Backward-Future compatibility (U, V, W, X, AL, AN, AO, Edge Cases)** — lowest priority to automate; several (link-health crawls, 1000-menu staged data, entitlement fixtures) need supporting test data/environments that don't exist yet — build those first, or run manually until they do.
