# AIUP `/reverse-engineer` vs Allium `/distill` — Round-Trip Evaluation Report

**Date:** 2026-07-08 · **Design:** one unseen reference app (expense claims, Vaadin +
jOOQ + Flyway + H2, 23 behaviours + 3 constraints, built with neither tool), each
tool extracts a spec from a pristine copy, then rebuilds the app in a clean room
from its spec alone. Full protocol in `EVALUATION.md`; per-behaviour scores in
`scores.md`; session ledger in `sessions.md`.

## Headline numbers

| Metric | AIUP | Allium |
|---|---|---|
| Spec fidelity (extraction) | 50/52 (96%) | 51/52 (98%) |
| Invented behaviours in spec | 2 | 0 |
| Implementation leakage in spec | ~10 (schema-mirroring entity model, by design) | 0 |
| Intent findings (questions raised) | 0 | 4 (2 confirmed real defects) |
| Spec artifact size | 8 files, 581 lines | 1 file, 306 lines |
| Reconstruction rubric score | 52/52 | 52/52 |
| Round-trip behavioural divergence from original | 1 (visibility policy mutated) | 0 |
| Tests in reconstruction | 43 (31 UI/Karibu + 12 service, 0 E2E) | 89 (service-level, derived from the 81 checkable requirements the spec implies; run red-first — confirmed failing before implementation; 0 UI, 0 E2E) |
| Behaviour test coverage | 23/23 (spot-checked) | 23/23 (spot-checked) |
| Human interventions (all phases) | 0 (1 unprompted judgment call) | 0 (4 decisions parked, still human-owned) |
| Cost: extraction | $3.12 · 4m · single pass | $11.35 · 16m · full loop incl. verification tests¹ |
| Cost: reconstruction | $18.67 · 24m | $16.80 · 24m² |
| Cost: total | **$21.79 · ~29m** | **$28.15 · ~39m** |

¹ Not scope-comparable: the Allium extraction session also generated a 465-line
verification suite and ran it against the code — a spot-check of the spec at
whatever coverage that run produced (propagate promises no coverage level), not
full verification. See `pin-suite-proposal.md` for the follow-up this motivates.

² Times are model-activity (API) durations from `/cost` — the time the tool
actually spent working, excluding the session idling on the human terminal. The
two reconstructions were effectively equal; only the extraction difference is
real compute. Raw per-session figures, including wall-clock, are in `sessions.md`.

## What the numbers can't say alone: the KD-1 chain

KD-1 ("known defect 1") is the scoring label for a real bug the reference app
shipped with, by accident: the claim detail page does no visibility check, so any
signed-in user can open any claim by URL, though the list is role-scoped. (KD-2,
its sibling: no self-reimbursement guard. Both were deliberately left unfixed once
found — both tools had already extracted from the buggy code, so fixing it
mid-experiment would have broken symmetry. Details in `scores.md`.) Neither the rubric author nor the reference UI tests
caught it. What each tool's workflow did with it is the experiment's clearest
result:

- **AIUP:** the spec asserted the *intended* visibility as enforced (BR-024) — the
  defect was silently absorbed and the spec over-claims. In reconstruction, the
  rebuilder noticed BR-024 contradicts UC-004's own main scenario and **invented a
  third policy** (reviewers may open non-draft claims; employees never see foreign
  claims), documented in a code comment. Probe confirms: foreign detail HIDDEN.
  The round trip changed observable behaviour without a human decision.
- **Allium:** distill modeled what the code does, flagged the mismatch as an open
  question, and the reconstruction reproduced the original behaviour exactly (probe:
  foreign detail VISIBLE) with the question re-parked. The human still owns the
  decision; nothing changed silently.

Same pattern on the second real finding (no self-reimbursement guard): Allium
surfaced it as a question and reproduced it (with a documenting test); AIUP's spec
and reconstruction carry it invisibly.

## Other observations

- **Both specs recovered all 23 behaviours**, including every boundary condition.
  Caveat: the reference test suite's `@DisplayName`s restate the rubric nearly
  verbatim — both extractors read them, so absolute fidelity is inflated relative to
  a real legacy codebase (symmetric, so the comparison holds). Harder-mode rerun
  (strip `src/test` before extraction) is the obvious follow-up.
- **Constraints need a home.** AIUP's `/reverse-engineer` produces no requirements
  catalog, so stack/NFR constraints went uncaptured (C03: 0). Allium partially
  captured stack in a header comment (C03: 1) but excludes implementation by design.
  Both reconstructions relied on the kickoff prompt for the stack — and diverged on
  the unstated part (Spring Boot vs plain Jetty), both defensibly.
- **Test philosophy differs more than test quality.** AIUP's suite is UI-first
  (Karibu per use case, `@UseCase`-tagged for traceability — genuinely nice).
  Allium's is obligation-first (transition edges, invariants, boundary values), ran
  red-first against a stub, and `/weed` caught a real spec↔code divergence the green
  suite didn't. Neither committed browser E2E tests.
- **Readability trade** (from phase 1, unchanged): AIUP's markdown use cases are
  stakeholder-readable; the `.allium` file is denser but half the size and carried
  more precise semantics (state machine, invariants, surfaces) through the round trip.
- **Adoption shape (observed, not measured).** AIUP is a methodology: phases, named
  roles, traceability rules, and a construction pipeline that exists only for its
  preferred stack — running it at full strength meant building the reference app on
  that stack. Allium is a specification language with a toolchain around it — the
  editor plugin whose skills and agents drive the loop (distill, propagate, weed),
  the checker CLI that validates specs, surfaces contradictions and derives test
  obligations, and LSP integration — but no prescribed process, roles, or stack; it
  attached to this experiment's workflow as-is. Which shape is
  *better* depends on whether a team wants a complete opinion or a composable tool —
  this experiment demonstrates the structural difference, not the adoption cost.
- **Cost:** AIUP's extraction is dramatically cheaper ($3.12 vs $11.35) but bought
  less (no verification, no questions). Reconstructions cost the same within noise.
  Totals: $21.79 vs $28.15 — the ~$6 premium bought the verification loop and the
  intent findings.

## Threats to validity

1. Reference tests leak the rubric to both extractors (symmetric; inflates absolutes).
2. Stack is AIUP's home turf (deliberate, disclosed).
3. Same model family (Fable 5) built the reference, ran both tools, and evaluated —
   monoculture effects unquantified.
4. n=1 app, n=1 run per cell; no variance estimate.
5. Evaluator wrote the reference and the rubric (defects adjudicated by author intent).
6. Allium's phase-2 input differs from its phase-1 artifact by one `/weed`-applied
   spec line (documented; immaterial to scores).

## Bottom line

On this codebase, both workflows round-trip a clean, well-tested app essentially
losslessly against the rubric — the differentiation is entirely at the edges the
spec didn't nail down. AIUP is cheaper, faster, and its artifacts are more readable,
but it resolved ambiguity by silently deciding — once in extraction (over-claiming
enforcement) and once in reconstruction (inventing a policy). Allium cost ~30% more
and its spec is harder on the eye, but its loop was the only workflow that (a) found real
defects, (b) distinguished what the code does from what it should do, and (c) kept
every unresolved decision visibly owned by a human across the full round trip.
