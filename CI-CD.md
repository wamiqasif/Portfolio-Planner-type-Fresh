# CI/CD Guide

## GitHub Actions

`.github/workflows/api-tests.yml`. One workflow, one job — a second workflow file wasn't
justified; everything below is a variation of "checkout, build, run one suite, publish
reports," which one parameterized job already covers.

The tier is picked from *why* the build is running, not hardcoded:

| Trigger | Suite | Rationale |
|---|---|---|
| `pull_request` | `smoke.xml` | Fast feedback, gates the merge |
| `push` to `main` | `sanity.xml` | Broader check right after merge, still fast |
| `schedule` (nightly, 02:00 UTC) | `regression.xml` | Full breadth, doesn't block anyone |
| `workflow_dispatch` | whatever's picked | Manual — including `full.xml`, which has no automatic trigger by design (release-gate tier) |

Every run publishes: PR check annotations (`dorny/test-reporter`, reads the Surefire XML —
failures show up inline on the PR, no artifact download needed), the Extent HTML report, and
the raw Surefire XML, all via `actions/upload-artifact`, `if: always()` so a failed run still
leaves evidence behind.

## Jenkins Pipeline

`Jenkinsfile` at the repo root — declarative, one job, mirrors the GitHub Actions logic so
either CI system produces the same result:

- **Parameters**: `ENVIRONMENT` (maps to a Maven profile), `SUITE` (which tier).
- **Triggers**: nightly cron on `main`, same schedule as the GitHub Actions nightly regression, only relevant on a non-parameterized scheduled run.
- **Stages**: `Checkout` → `Build` (compile only, fails fast on a compile error before spending time on HTTP calls) → `Test`.
- **Post**: `junit` step (trend graph from Surefire XML), `publishHTML` (Extent report — copied to a stable filename first, since the plugin needs one and the report's own filename is timestamped), `archiveArtifacts` for reports + logs. All under `post { always { ... } }` so a failed build still gets everything published.
- `options { disableConcurrentBuilds() }` — two concurrent runs against the same environment would race on shared server-side state (e.g. records created by one run's tests visible to another's).

No Jenkins-managed Maven tool installation is configured — the pipeline calls `./mvnw`
directly, the same wrapper GitHub Actions uses, so both CI systems and every contributor's
machine run the identical pinned Maven version.

## Maven Commands

```bash
# Default (root testng.xml - everything, qa environment)
./mvnw test

# Pick a suite tier
./mvnw test -DsuiteXmlFile=src/test/resources/suites/smoke.xml
./mvnw test -DsuiteXmlFile=src/test/resources/suites/sanity.xml
./mvnw test -DsuiteXmlFile=src/test/resources/suites/regression.xml
./mvnw test -DsuiteXmlFile=src/test/resources/suites/full.xml

# Pick an environment
./mvnw test -Pdev
./mvnw test -Pprod
# or equivalently, bypassing the profile:
./mvnw test -Denv=stage

# Combine
./mvnw test -Pstage -DsuiteXmlFile=src/test/resources/suites/regression.xml

# Override a single secret without touching a checked-in file (see PropertyReader)
./mvnw test -Dauth.password=$CI_SECRET_PASSWORD

# Compile only - fails fast on a real compile error before any HTTP call runs
./mvnw clean compile test-compile

# Skip tests (dependency/build verification only)
./mvnw clean install -DskipTests
```

`suiteXmlFile` and `env` are the two properties every other command here is built from
(`pom.xml`) — every suite/environment combination is one of these two flags, never a
separate script.

## Parallel Execution

Each suite's `<suite parallel="classes" thread-count="N">` runs test *classes* concurrently
(not individual methods within a class) — chosen because state within one test class (its
`PostService`/model instances) isn't shared across classes, so there's no risk of one class's
test corrupting another's, without needing method-level thread-safety review for every test
author. `thread-count` scales with suite size: 3 for smoke/sanity (small, fast tiers where
more threads than tests would just add overhead), 5 for regression/full (larger, benefits more
from concurrency).

This is a fixed value per suite file, not a runtime override — TestNG's suite XML doesn't
support property substitution (e.g. `${threadCount}`), so a fully dynamic thread count would
mean generating the XML at build time instead of checking it in. Not done: no current need has
justified that complexity. See Future Enhancements.

Thread-safety this relies on, already verified live during development:
`ConfigManager`/`TokenManager`/`ExtentReportManager` are all safe (Singleton + immutable state
or `ThreadLocal`); `ApiClient`/`PostService`/auth strategies are stateless; `RetryAnalyzer` gets
one instance per test method from TestNG, so retry counts never leak across methods or threads.

## Environment Execution

One Maven profile per environment (`dev`/`qa`/`stage`/`prod`), each setting nothing but the
`env` system property forwarded to the test JVM. The environment's actual values (base URL,
timeouts, auth config) live in `src/main/resources/config/<env>.properties` — the pom never
hardcodes an environment-specific value, so adding environment 5 is "add a properties file +
a four-line profile," not a code change.

Precedence (enforced by `EnvironmentLoader`): `-Denv=<x>` system property → `ENV` OS
environment variable → default `qa`. CI always sets it explicitly (via the Maven profile
flag); the OS-variable fallback exists for a non-Maven invocation (e.g. running a compiled jar
directly) that a Maven profile can't reach.

## Report Publishing

Two independent outputs, for two audiences:

- **Surefire XML** (`target/surefire-reports/`) — machine-readable, what CI parses. GitHub
  Actions turns it into inline PR check annotations; Jenkins turns it into the `junit` trend
  graph. Neither needs the HTML report to gate a build.
- **Extent HTML report** (`reports/ExtentReport_<timestamp>.html`) — human-readable, what a
  person opens after a failure. Timestamped so successive CI runs never overwrite each other;
  both CI systems archive it as a build artifact regardless of pass/fail (`if: always()` /
  `post { always { ... } }`).
- **Log files** (`test-output/logs/automation.log`, `http-traffic.log`) — the third artifact,
  less visible but often what actually explains *why* something failed at the HTTP level.
  Archived alongside the reports in both pipelines.

## Best Practices Applied Here

- **Pin the build tool.** `./mvnw`, not `mvn` — every CI run and every contributor gets the
  exact same Maven version, not whatever the machine happens to have.
- **Let the trigger decide the tier**, not a hardcoded default — a PR shouldn't wait on a full
  regression run, and a nightly run shouldn't stop at smoke-level coverage.
- **Publish evidence on failure, not just on success** — every report/log upload step runs
  unconditionally (`always()`), because a failed run's evidence is the one you actually need.
- **No secrets in the repo.** Every environment's `.properties` file ships with a blank or
  `CHANGE_ME` placeholder for anything sensitive; real values come from `-D` system properties
  sourced from the CI platform's own secret store (GitHub Actions secrets / Jenkins
  credentials), never committed.
- **One parameterized pipeline per CI system, not one per suite/environment combination** — 4
  suites × 4 environments is 16 possible combinations; encoding each as its own job/workflow
  would be the "unnecessary scripts" this was explicitly built to avoid.
