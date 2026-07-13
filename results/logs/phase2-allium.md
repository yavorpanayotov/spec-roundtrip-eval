# Phase 2 — Allium reconstruction log

- Fresh session in `allium/reconstructed/` (spec only), kickoff per EVALUATION.md,
  run by Yavor, 2026-07-08.
- Flow: Allium loop — `/propagate` (81 obligations → 89 JUnit tests) → red-first
  check (88/89 failing against a stub) → implement → `/weed` → converged at tick 2.
- Cost: $16.80 · wall 34m 34s (API 23m 40s) · 2,876 lines added.
- Tokens: fable-5 8.3k input, 93.5k output, 6.0M cache read, 245.8k cache write;
  opus-4-8 (subagents) 268 input, 15.5k output, 388.0k cache read, 93.7k cache write.
- Output: plain Vaadin Flow + Jetty (no Spring — stack list taken literally), jOOQ
  via DDLDatabase, 2 Flyway migrations, 89 tests green. Independently verified by
  evaluator: suite re-run green, service code read, Playwright probe passed
  (see scores.md).
- Interventions: none beyond kickoff. The 4 open questions from the spec were
  implemented as-specified (matching the original app) and re-parked for the human —
  still pending decisions, not silent resolutions.
- Spec change during the run: `/weed` added derived `requires_receipt` to the
  ClaimDetail surface's exposes (spec fixed, not code) — legitimate loop behaviour,
  noted because phase-2 input therefore differs from the phase-1 artifact by one line.
