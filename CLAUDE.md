# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone API test automation framework: Java 17, Maven, Rest Assured, TestNG, Jackson,
Log4j2, Extent Reports, Apache POI (test-scope only, for Excel-driven data). No Selenium/Appium,
no Spring, no Cucumber, no DI container — wiring between layers is explicit and traceable.
`README.md` (usage) and `ARCHITECTURE.md` (class/package diagrams, folder structure, review
findings) describe the framework's original scaffold state and are now behind the code — they
predate the Portfolio Planner and Hub business modules, the real `vrlogin` auth internals
(RSA-encrypted login, `GuidSessionAuthenticator`), and `utils.SensitiveDataMasker`. `CI-CD.md`
(pipeline reference) is still the right place for CI/CD detail. Trust the code and this file over
README/ARCHITECTURE for current module/auth architecture until those two are refreshed. This file
is about what isn't written down elsewhere: commands and the non-obvious things that were learned
by trial and error.

## Commands

Always use the checked-in wrapper (`./mvnw`), never a system `mvn` — it pins the exact Maven
version. There is no linter/static-analysis plugin configured in `pom.xml` (no checkstyle,
spotbugs, or PMD) — don't invent one.

```bash
./mvnw clean compile test-compile          # compile only, fails fast before any HTTP call

./mvnw test                                 # whatever testng.xml currently points at - NOT "everything", see caveat below
./mvnw test -DsuiteXmlFile=src/test/resources/suites/smoke.xml       # smoke / sanity / regression / full

./mvnw test -Pstage                         # or -Denv=dev|qa|stage|prod
./mvnw test -Dauth.password=...             # override any single config key without editing a file

./mvnw test -Dtest=GetPostTests                                          # single class
./mvnw test -Dtest=GetPostTests#getPostById_withValidId_returns200WithMatchingPost   # single method
./mvnw test -Dgroups=smoke                  # by TestNG group, across all classes
```

`-Dtest`/`-Dgroups` work as expected even though Surefire is configured with an explicit
`suiteXmlFiles` element (verified, not assumed) — they filter within whatever the suite XML
already selected.

**Root `testng.xml` is not "everything" despite what `README.md`/`ARCHITECTURE.md` claim.** It's
a working scratch suite that gets repointed at whatever module is being iterated on day to day —
right now it lists nine Portfolio Planner classes only, not the whole codebase. `suites/full.xml`
(no group filter, every `apitests.*` package) is the actual "run everything" suite:
`./mvnw test -DsuiteXmlFile=src/test/resources/suites/full.xml`. Don't assume plain `./mvnw test`
covers the whole codebase. Two more root-level files exist for the same reason and are **not**
part of the smoke/sanity/regression/full tier system in `src/test/resources/suites/`:
`testngHub.xml` (Hub module classes) and `portfolioPlanner.xml` (Portfolio Planner module,
currently identical to `testng.xml`) — both are manual-run scratch suites, edit/replace them
freely.

Reports/logs land in `reports/` (Extent HTML, timestamped, never overwritten),
`target/surefire-reports/` (JUnit XML, what CI parses), and `test-output/logs/` (`automation.log`
for framework/test diagnostics, `http-traffic.log` for request/response detail — kept in
separate files on purpose). The Portfolio Planner module also writes **one** timestamped
`reports/portfolio-planner-report-<ts>.xlsx` per run (`portfolio.PortfolioPlannerReportWriter`;
`BusinessValidationReportWriter`/`PlannerFieldRulesReportWriter` only accumulate rows into
separate sheets, and every `@AfterSuite` calls the one writer, which writes only on the first call
— don't reintroduce per-writer files). Both Detail sheets lead with `Funds in Portfolio`,
`Payload (planner/funds-buy/generate)`, `Response (planner/funds-buy/generate)` — a second, unrelated kind of report living in
the same folder as the Extent HTML.

The `apitests.users` module calls a real public API (`jsonplaceholder.typicode.com`) over the
network — a sample resource chosen because it's stable and needs no auth, not a real Value
Research endpoint. Every other populated module (`hub`, `portfolio`) calls real Value Research QA
endpoints and needs a working `vrlogin` session. If tests fail in an offline/sandboxed
environment, check network access before assuming the framework code is broken.

## Architecture: the big picture

Dependency direction is one-way and load-bearing: `apitests.* → services → core → {auth,
listeners} → {config, reporting} → exceptions`. Nothing goes backward. Two places deliberately
break the "obvious" path to keep it that way — see the Rest Assured gotchas below.

**The whole framework is built around "auto-apply, never ask the caller."** A test class calls
`someService.doThing()`; the service calls `RequestSpecFactory.defaultRequestSpec(baseUri)`.
That one call already has authentication attached (`auth.type` in the active environment's
`.properties` file picks the strategy), HTTP logging/redaction attached, and — if the response
comes back 401/403 — an automatic invalidate-and-retry-once already wired in. No test or
service class ever touches an `AuthStrategy`, a token, or retry logic directly. When extending
the framework, preserve this: don't make a new service pass a token or auth flag around.

**Auth is a Strategy behind a Factory** (`auth.AuthStrategyFactory`, switches on
`ConfigManager.getAuthType()`): `basic`, `bearer` (static token), `apikey`, `oauth2`
(client-credentials, token cached), `vrlogin` (dynamic login against VR's real API gateway
`/api/v0/login/` endpoint, via `auth.LoginService` → `auth.AuthenticationManager` → cached in
`auth.TokenManager`). Adding a 6th auth mechanism = one new class implementing `AuthStrategy` +
one line in the factory switch + new config keys. Nothing else changes.

`vrlogin` is fully real, not a placeholder: `LoginService` POSTs a JSEncrypt-compatible
RSA-encrypted password (`auth.EncryptionService`, key from `auth.vrlogin.rsa.public.key`) as
`application/x-www-form-urlencoded` to `<env>appapi.valueresearch.in/api/v0/login/`. Two
backend quirks this login flow specifically works around, both confirmed against real calls —
don't "simplify" either back out:
- **HTTP 200 is not login success on this backend.** A rejected password still comes back 200;
  the real outcome is the JSON body's own `"status"` field, with `session-alerts[].message`
  carrying the server's reason (`AuthenticationManager.failFastOnExplicitStatusFalse`). Only an
  explicit `status: false` fails fast — a missing/unparseable `status` falls through to normal
  token extraction unchanged.
- **A leading UTF-8 BOM on the response body** (an observed PHP-backend artifact) makes
  `JsonPath` reject an otherwise-valid JSON document outright; `AuthenticationManager` strips it
  before parsing (`stripLeadingBom`). Token extraction itself tries a JSON field
  (`auth.vrlogin.token.jsonpath`) first, then falls back to a cookie
  (`auth.vrlogin.token.cookie.name`) — the real response shape wasn't confirmed when this was
  built, so both are config-driven on purpose.

Extra headers for the login call (`json-api-key`, etc.) are configured as
`"Name:Value||Name2:Value2"` in `auth.vrlogin.headers` — `||`, not `;`, because a real required
header here (`User-Agent`) contains a literal semicolon that would otherwise corrupt the parse
(`auth.LoginService.parseExtraHeaders`).

**A second, unrelated auth path exists only for the Portfolio Planner Excel-driven suite:**
`auth.GuidSessionAuthenticator` calls a per-Excel-row `login-by-guid` URL — the workbook's own
"Login URL" column, one distinct value per `Execution_Data` row — and returns the session cookies
it sets (redirects deliberately not followed; confirmed the redirect target is just the frontend
homepage and cookies are already set before it). This is **not** an `AuthStrategy` and is never
selected via `auth.type` — every real `AuthStrategy` is stateless and resolved once per
environment, while this is a fresh, uncached session per Excel row keyed by that row's own GUID.
A caller attaches the returned `Cookies` directly onto a row-scoped spec (e.g.
`RequestSpecFactory.unauthenticatedRequestSpec(baseUri).cookies(cookies)`) instead. Confirmed
sufficient for Planner Generate, User Portfolio, Fund Details, and Fund Opinion, across both the
advisor API gateway and the main app API host. Don't unify this with `vrlogin` or try to force it
into the `AuthStrategy` shape — it was kept separate on purpose.

**Two base hosts, not one**: `ConfigManager.getBaseUrl()` (main app API host) and
`ConfigManager.getAdvisorApiBaseUrl()` (advisor API gateway — confirmed via live curls to be a
distinct host for the User Portfolio and, since 2026-09-08, Planner Generate APIs; falls back to
`getBaseUrl()` for any environment that hasn't defined a distinct advisor host). `services.
PlannerService`/`PortfolioService` use the advisor host; most other services use the plain one —
check which one a new service actually needs rather than assuming `getBaseUrl()`.

**Config precedence** (`config.EnvironmentLoader`): `-Denv=<x>` system property → `ENV` env var
→ default `qa`. Every config value then comes from `src/main/resources/config/<env>.properties`
via `config.ConfigManager` (the sole entry point — nothing else reads a properties file
directly), with any individual key overridable via a same-named `-D` system property
(`config.PropertyReader`'s job). Real secrets are supposed to be `CHANGE_ME`/blank here and
overridden from a CI secret store — **`qa.properties` currently has a real, live
`auth.vrlogin.username`/`auth.vrlogin.password` committed instead of `CHANGE_ME`; flag this
before pushing the repo anywhere shared.**

**TestNG group taxonomy**: `smoke` ⊂ `sanity` ⊂ `regression` are additive tags on `@Test`
methods (a smoke test also carries `sanity` and `regression`). `full` is **not** a tag at all —
`suites/full.xml` has no group filter, so "everything" is free; don't add a `"full"` group
anywhere. Module tags (`users`, `hub`, `portfolio`, etc.) mirror the business-module test packages.

**Retry, two unrelated kinds, don't confuse them**: `listeners.RetryAnalyzer` +
`RetryAnnotationTransformer` retry a *failed test method* (TestNG-level, 2 retries, wired onto
every `@Test` automatically). It marks a to-be-retried attempt `SKIP` rather than `FAILED`, so a
flaky-then-passing test reports as a clean PASS, not FAIL-then-PASS. Separately, `ApiClient`
retries a *single HTTP call* once when the response is 401/403 on an authenticated request
(HTTP-level, unrelated mechanism, see gotchas below). `retry.max.attempts`/`retry.delay.ms` in
the properties files are reserved for a third thing (HTTP-level retry on connection failure)
that doesn't exist yet — don't assume they're wired to anything currently.

## Portfolio Planner and Hub modules

Besides `apitests.users` (the original scaffold sample against the public demo API),
`apitests.portfolio` (Portfolio Planner) and `apitests.hub` (Hub) are now real, populated
business modules. `authentication`, `funds`, `stocks`, `transactions`, `notifications`, `common`,
and `data` are still empty (`.gitkeep` only) — scaffolded, no tests written yet.

The Portfolio Planner suite is **Excel-data-driven**: `portfolio.ExcelTestDataReader` (Apache
POI, the framework's only test-scope-only main dependency) reads workbooks from
`src/test/resources/testdata/` — `Portfolio Planner Test CasesQA .xlsx` (execution data +
portfolio-aware scenarios) and `planner_generate_test_data.xlsx` (field-rules matrix). Results
get written back out as a single timestamped `.xlsx` report under `reports/` via
`PortfolioPlannerReportWriter`, separate from the Extent HTML
report and from Surefire's XML.

Not every class under `portfolio/` is a suite-wired `@Test` class — don't assume one is just
because it's in that package:
- `LabelResolutionDiagnostic` is a one-off, read-only diagnostic, deliberately not referenced by
  any suite XML — run it explicitly via `-Dtest=LabelResolutionDiagnostic`.
- `CategoryMatcherTest` is a plain, API-free unit test (no `BaseTest`, no HTTP call at all) —
  the one example in the codebase of a persisted unit test for the framework's own logic.
- `apitests.tools.LoginVerifier` is a standalone `main()` class, not a TestNG test (no `@Test`,
  never picked up by Surefire) — a manual, isolated check of whether the RSA-encrypted login
  request shape is currently accepted, run directly as `java -cp <test-classpath>
  com.org.apitests.tools.LoginVerifier` with real credentials passed via `-D` system properties.

`docs/test-plans/` holds the confirmed-real evidence backing this module — read it before
assuming an endpoint shape from code alone: `portfolio-planner-apis.md` (live curl captures;
check its CURRENT vs. STALE split before trusting anything in it — stale captures include real
session cookies from a since-expired session, kept only for history), `portfolio-planner-
business-rule-matrix.md`, `Portfolio-Planner-PRD.md`, and `hub-api-test-plan.md`.

**Known current backend issue, not a framework bug**: per `portfolio-planner-apis.md`, the
confirmed-current Planner Generate request shape was observed returning a bare HTTP 500 with an
empty body from the QA backend itself (as of 2026-09-07). If a Planner Generate test fails this
way, check whether that's still the backend's state before assuming automation code broke it.

## Rest Assured behaviors that cost real time to discover — don't rediscover them

These are non-obvious library quirks, verified empirically during development. If something
here looks like it could be "simplified," check the Javadoc at the referenced class first —
it's almost certainly there because the simpler version was tried and broke.

- **Never call `someRequestSpec.request(method, path)` directly** on a spec fresh off
  `RequestSpecBuilder.build()` — it throws an internal Rest Assured/Groovy NPE
  (`assertionClosure on null object`). Always route through `RestAssured.given().spec(spec)
  .request(...)` (see `core.ApiClient.execute`).
- **A `Filter` cannot retry by calling `FilterContext.next()` twice** — the second call returns
  `null`. `ctx.send(...)` isn't the answer either — it re-enters the whole filter chain
  including the calling filter itself, causing infinite recursion. This is why the 401/403 retry
  lives in `core.ApiClient.execute()` as two independent top-level requests, not in a Filter —
  don't move it back into one.
- **The default JSON content-type conflicts with `.multiPart(...)`** (throws
  `IllegalArgumentException`) and **silently mislabels form-encoded bodies** (`.formParam(...)`
  on a JSON-content-typed spec sends real form-encoded bytes under a `Content-Type:
  application/json` header — no error, just wrong). Use `RequestSpecFactory.multipartRequestSpec()`
  for multipart, and explicit `.contentType(ContentType.URLENC)` for any form POST (see
  `auth.LoginService`, `auth.OAuth2AuthStrategy`).
- **`.auth().preemptive().basic()/.oauth2()` replaces the `Authorization` header cleanly on a
  second call; plain `.header(name, value)` duplicates it** on a second call with the same name.
  Basic/static-Bearer/OAuth2 use `.auth()`, so re-invoking `AuthStrategy.apply()` on the same spec
  (the auth-retry path) is safe for them without any extra care. `vrlogin`
  (`DynamicLoginAuthStrategy`) uses plain `.header("auth-token", ...)` instead — confirmed against
  a real authenticated call that this backend uses a custom `auth-token` header, not
  `Authorization`, for the user's session token — so it explicitly calls `removeHeader` before
  re-setting it on every `apply()`, or a retry would send two conflicting `auth-token` values.
  `ApiKeyAuthStrategy` also uses plain `.header()` and would have the same duplication risk, but
  never reaches the retry path at all: its `invalidate()` is a no-op and it sets neither
  `Authorization` nor `auth-token`.
- **`RequestSpecFactory.unauthenticatedRequestSpec()` vs `defaultRequestSpec()` is the signal**
  `ApiClient` uses to decide whether a 401/403 should trigger auto-retry: the presence of an
  `Authorization` header *or* an `auth-token` header on the spec. This is what stops a negative
  test that deliberately builds an unauthenticated request (expecting 401) from having its result
  silently "fixed" by an unwanted retry. Don't add auth-retry logic that doesn't check this.
- **`Authorization` is not used for user authentication on this backend at all** — confirmed via
  a real authenticated request. A separate, static `Authorization: Basic ...` app-identity header
  showed up in that same request, unrelated to the per-user token. If a future auth mechanism
  needs that static credential too, it's a distinct concern from `vrlogin`'s per-user `auth-token`
  - don't conflate them into one strategy.
- **`core` depends on `auth` and `listeners`, never the reverse** — `auth.OAuth2AuthStrategy`,
  `auth.LoginService`, and `auth.GuidSessionAuthenticator` all deliberately talk to Rest Assured
  directly instead of calling `core.RequestSpecFactory`, specifically to avoid a circular
  dependency (fetching a token/session would otherwise try to attach the very auth it's trying to
  obtain). Don't "clean this up" by routing them through `core` — it was done this way on purpose.

## Known gaps

No persisted unit tests exist for most of the framework's own classes (`ConfigManager`,
`PropertyReader`, `RetryAnalyzer`, redaction logic, etc.) — every verification during development
was a real, one-off check against a live server, not a repeatable automated test.
`portfolio.CategoryMatcherTest` is the one exception so far — a real, persisted unit test for a
piece of framework/business logic; use it as the pattern if adding more, rather than another
one-off manual check.

Header redaction (`Authorization`/`Cookie`/`Set-Cookie`/`auth-token`) is solid via
`listeners.HttpLoggingFilter`'s `ALWAYS_REDACTED_HEADERS`, sharing one placeholder constant
(`utils.SensitiveDataMasker.MASKED_VALUE`) with the auth layer's own token/password log masking.
Request **body** redaction is still not implemented — a password field in a JSON body would be
logged in full outside of `auth.LoginService`'s own deliberately-minimal, field-specific masking
(which only covers its own login form body, not bodies in general).

The app-identity headers real API evidence showed alongside auth (`json-api-key`, `app-platform`,
`origin`, `referer`, `user-agent`) are now sent, but only as part of `vrlogin`'s own
`auth.vrlogin.headers` config — there's still no general per-call header baseline independent of
`auth.type`. A future auth mechanism needing the same app-identity headers would have to
duplicate that config, not reuse `vrlogin`'s — this would need its own design decision before
being shared.
