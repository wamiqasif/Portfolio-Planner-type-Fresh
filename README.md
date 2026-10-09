# API Automation Framework

Standalone API test automation framework. Rest Assured + TestNG + Jackson + Log4j2 + Extent Reports.

No Selenium/Appium. No Spring Boot. No Cucumber.

> **Status**: all layers implemented and passing (config, core HTTP, auth, models, logging/reporting,
> retry, base test, sample CRUD tests). See `ARCHITECTURE.md` for the class/dependency diagrams and
> `CI-CD.md` for the full CI/CD reference. Run `./mvnw test` to execute the suite.

## Prerequisites

- JDK 17
- No pre-installed Maven required - use the checked-in wrapper (`./mvnw` / `mvnw.cmd`)

## Project layout

```
src/main/java/com/org/apiautomation/   Framework code (reusable, no test logic)
  config/          Environment resolution + typed config access
  core/            Rest Assured wrapper: spec building, execution, auth + logging attached automatically
  auth/            Pluggable auth strategies (Basic/Bearer/API key/OAuth2) + token cache
  models/          Jackson request/response DTOs
  services/        One class per API resource - the facade tests call
  listeners/        TestNG lifecycle + Rest Assured filter + retry wiring (see below)
  reporting/          Extent Reports lifecycle (single instance, per-thread report node)
  exceptions/           Framework-specific unchecked exceptions
  assertions/             (empty - no caller yet)
  constants/                (empty - no caller yet)
  utils/                      (empty - no caller yet)

src/main/resources/
  config/          dev/qa/stage/prod.properties
  log4j2.xml       Logging configuration (separate files for framework logs vs. HTTP traffic)

src/test/java/com/org/apitests/
  base/            BaseTest - config load + fail-fast, one @BeforeSuite method
  data/               (empty - data providers are inline in test classes)
  users/           Sample CRUD tests (GET/POST/PUT/PATCH/DELETE) against a public demo API
  authentication/ portfolio/ funds/ stocks/ transactions/ notifications/ common/
                   (empty - business-module packages, ready for real tests)

src/test/resources/
  testdata/        (empty - no caller yet)
  schemas/         JSON Schema files for contract validation
  suites/          smoke.xml, sanity.xml, regression.xml, full.xml

testng.xml         Default suite (mirrors suites/full.xml - everything, no group filter)
Jenkinsfile        Declarative Jenkins pipeline (see CI-CD.md)
.github/workflows/api-tests.yml   GitHub Actions workflow (see CI-CD.md)
```

See `ARCHITECTURE.md` for the full class diagram, package dependency diagram, and the
Architecture Review that shaped this layout (what was refactored, what was deliberately left
empty, and why).

## Configuration model

Environment selection follows this precedence (`config.EnvironmentLoader`):

1. `-Denv=<env>` system property
2. `ENV` environment variable
3. default: `qa`

The active environment picks which `src/main/resources/config/<env>.properties` file
`config.ConfigManager` loads. A Maven profile per environment (`dev`/`qa`/`stage`/`prod`) is
just a convenience that sets `-Denv=...` for you - the properties files are the source of
truth, not the pom.

## Running tests

See `CI-CD.md` for the full command reference. The short version:

```bash
./mvnw test                                                              # default: everything, qa
./mvnw test -DsuiteXmlFile=src/test/resources/suites/smoke.xml           # fast tier
./mvnw test -Pstage -DsuiteXmlFile=src/test/resources/suites/regression.xml   # env + tier
```

## Authentication

Four mechanisms, selected by `auth.type` in the active environment's properties file - Basic,
static Bearer token, API key, or OAuth2 (client-credentials, token cached and reused until
near expiry). Applied automatically to every request built via
`core.RequestSpecFactory.defaultRequestSpec()` - a service class never writes auth-handling
code of its own.

## Retry

Every `@Test` method automatically gets a bounded retry (`listeners.RetryAnalyzer`, 2 retries)
via `listeners.RetryAnnotationTransformer` - no per-test opt-in needed. A test that fails and
then passes on retry reports as a clean PASS, not a confusing FAIL-then-PASS, in both the logs
and the Extent report.

## Reports and logs

- **Extent HTML report**: `reports/ExtentReport_<timestamp>.html` - one file per run, never
  overwritten. Request/response/headers/body/execution-time attached automatically per test
  step, with `Authorization`/`Cookie`/`Set-Cookie`/API-key headers redacted before they're ever
  written anywhere.
- **Surefire XML**: `target/surefire-reports/` - what CI parses to gate a build.
- **Logs**: `test-output/logs/automation.log` (framework/test diagnostics) and
  `http-traffic.log` (request/response detail, kept separate on purpose - see `ARCHITECTURE.md`).

## CI/CD

See `CI-CD.md` for the complete reference (GitHub Actions, Jenkins, Maven command cheat sheet,
parallel execution, environment execution, report publishing, best practices). Short version:
PRs run the smoke suite, merges to `main` run sanity, a nightly job runs the full regression
suite, and the full suite is manual/release-gate only.

## Extending the framework

- **New API resource**: add a `*Service` class under `services/`, request/response models
  under `models/`.
- **New auth mechanism**: add an `AuthStrategy` implementation under `auth/`, wire it into
  `AuthStrategyFactory`.
- **New business module**: add tests under the matching (already-scaffolded) package in
  `src/test/java/com/org/apitests/` - it's already included in every suite's `<packages>` list.
- **New environment**: add `<env>.properties` under `src/main/resources/config/` and a
  matching Maven profile in `pom.xml`.
