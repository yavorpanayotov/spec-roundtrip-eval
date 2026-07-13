# Phase 1 — Allium extraction log

- Fresh Claude Code session in `allium/extracted/`, run by Yavor, 2026-07-08.
- Kickoff: Allium loop ("reverse-engineer the codebase" — `.allium-loop/` state file
  present), which ran `/distill` → `/propagate` → test run against the existing code.
  NOTE: this is broader than AIUP's extraction-only run — the session also generated
  a 465-line, 16-test spec-verification suite (`ExpenseClaimsSpecTest.java`) and
  validated the spec against the code. Cost figures below cover the whole loop.
- Models: claude-fable-5 (main), claude-opus-4-8 (subagents, $0.78), haiku (trivial).
- Wall-clock: 21m 39s (API 15m 49s). Cost: $11.35 total.
- Tokens (from /cost): fable-5 7.8k input, 49.1k output, 5.2M cache read, 144.4k
  cache write; opus-4-8 266 input, 11.8k output, 237.2k cache read, 57.8k cache write.
- Output: `specs/expense-claims.allium` (306 lines) + `ExpenseClaimsSpecTest.java`
  (465 lines, 16 tests). 831 lines added / 18 removed per /cost.
- Interventions: none reported beyond kickoff; the session ended by parking 4 open
  questions for the human rather than blocking on them. _Yavor: correct if you
  answered anything mid-run._
- Convergence: loop ran to a clean stop with open questions parked — the questions
  are deferred human input, counted as pending "answer"-class involvement if resolved.
