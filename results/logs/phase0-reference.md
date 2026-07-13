# Phase 0 — Reference app build log

- Built by Claude Code (plain session, no AIUP or Allium skills invoked; neither
  tool's documentation consulted during the build).
- Date: 2026-07-08.
- Stack as planned: Spring Boot 3.5.0, Vaadin 24.7.3, jOOQ (codegen via DDLDatabase
  from `V1__schema.sql`), Flyway, H2 file DB, Java 21, Maven 3.9.16.
- Structure: `ClaimService` enforces every rubric behaviour; UI (login as user
  picker, claims list, claim detail) delegates to it. Thresholds in
  `application.properties` (C02).
- Verification: `mvn test` — 23/23 behaviour tests green (one test per rubric ID
  B01–B23, named accordingly). Boot smoke test: `mvn spring-boot:run` starts in
  ~3.4s, `GET /login` returns 200 with the Vaadin shell.
- UI verification: driven headless with Playwright (`results/rig/drive.js`; the
  original run's screenshots lived in session-scoped tmp and were not preserved —
  rerunning the script regenerates them). Covered end-to-end through the browser: B01, B02 (empty submit blocked), B03, B06 (reject → owner resubmits),
  B08 (manager sees no item controls), B11 (receipt gate on submit), B12 (total),
  B17 (reject requires + displays reason), B18, B19, B20 (Bob cannot see Alice's
  claim), B21, B23 (full history with actors/timestamps). No JS console errors.
  Remaining rubric behaviours are service-enforced and covered by the unit tests.

## Interventions

None (single-session build by the agent; no human corrections needed so far).

## Token caveat

Phase 0 was built in the same session as the evaluation design and an earlier
AIUP/Allium review discussion, so the session `/cost` overstates the build alone.
Recorded as-is with this caveat; phase 0 is the control, not a compared run.

## Issues hit during build

1. H2 declared twice in pom (runtime + test scope); Maven let the test-scope
   declaration win, so `spring-boot:run` failed with "Cannot load driver class:
   org.h2.Driver". Fixed by removing the duplicate — runtime scope is on the test
   classpath anyway.
2. Not a bug, but a quirk found during UI driving: the Vaadin date picker parses
   typed dates with the browser locale (day-first here), so `7/8/2026` became
   7 August 2026 — a future date, which B10 correctly rejected. Kept as-is; C01/B10
   unaffected, but worth remembering when writing E2E tests against reconstructions.
