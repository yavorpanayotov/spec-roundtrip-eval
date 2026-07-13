# Phase 2 — AIUP reconstruction log

## Discarded first attempt (2026-07-08, ~16:42–16:54)

A reconstruction was run in a non-fresh session (prior context, so potentially
contaminated by knowledge of the reference code) — invalidated per protocol. Output
(full app: pom, src, tests run, H2 data) deleted; `docs/` verified byte-identical to
`aiup/extracted/docs/` and retained. Not counted in any metric.

## Valid run (2026-07-08)

- Fresh session in `aiup/reconstructed/` (docs only), kickoff prompt per EVALUATION.md.
- Skills used: aiup-vaadin-jooq (`/flyway-migration`, `/implement`, `/karibu-test`;
  Vaadin + Playwright MCP servers for a manual smoke drive; no Playwright test files
  committed).
- Cost: $18.67 · wall 28m 39s (API 24m 24s) · 2,923 lines added.
- Tokens: fable-5 14.0k input, 104.0k output, 9.6M cache read, 188.4k cache write.
- Output: Spring Boot 3.5 + Vaadin 24.10.7 app, 5 migrations, 43 tests (31 Karibu UI
  + 12 service), all green. Independently verified by evaluator: suite re-run green,
  service code read, Playwright probe passed (see scores.md).
- Interventions: none beyond kickoff reported. One unprompted judgment call by the
  session: detail-page visibility policy (see scores.md, divergence 1) — decided and
  documented in code rather than asked.
- Housekeeping note: a leftover server from the discarded first attempt was still
  serving on :8080 during this run (both phase-2 sessions worked around it assuming
  it was the original app); evaluator identified its cwd as aiup/reconstructed and
  killed it before probing.
