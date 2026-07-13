# AIUP vs Allium: Reverse-Engineering Evaluation

Comparing AIUP's `/reverse-engineer` against Allium's `/distill` by round-tripping the
same unseen codebase through both: **code → spec → reconstructed code**, then measuring
how much survived the journey and what it cost.

## Design

The reference app is built with plain Claude Code — no AIUP, no Allium — so neither
tool has seen the codebase or influenced its shape. Each tool then gets a pristine
copy of the reference code to extract specs from, and each reconstruction happens in a
**clean room**: a fresh directory and a fresh session containing *only* that tool's
extracted artifacts, with no access to the reference code. Fidelity of the
reconstruction is therefore fidelity of the spec — nothing else carries the
information across.

```
reference/              the original app (Vaadin + jOOQ + Flyway, expense claims)
ground-truth/           behaviour inventory (scoring rubric) — hand-maintained
aiup/
  extracted/            copy of reference code → /reverse-engineer → AIUP artifacts
  reconstructed/        clean room: AIUP artifacts only → greenfield construction
allium/
  extracted/            copy of reference code → /distill → .allium spec
  reconstructed/        clean room: .allium only → /propagate → implement → /weed
results/
  logs/                 one intervention log per session
  sessions.md           token/cost/time per session
  scores.md             filled rubrics
  report.md             final comparison
```

## Phases

### Phase 0 — Reference app (control)

Build the expense claims app in `reference/` per `ground-truth/behaviour-inventory.md`,
using plain Claude Code with no spec tooling and without consulting AIUP or Allium
documentation. Stack: Vaadin Flow, jOOQ, Flyway, H2 (file-based), Maven, JUnit 5.
The app is done when every behaviour in the inventory is implemented and manually
verified. The inventory is frozen at that point — it becomes the rubric.

### Phase 1 — Extraction (one dedicated session per tool)

1. Copy `reference/` source into `<tool>/extracted/` (code, migrations, pom — not
   `results/`, not `ground-truth/`).
2. **AIUP**: fresh Claude Code session in `aiup/extracted/`, run `/reverse-engineer`
   (plugin `aiup-core` from the `AI-Unified-Process/marketplace`). Expected artifacts:
   `docs/use_cases.puml`, `docs/use_cases/UC-*.md`, `docs/entity_model.md`.
3. **Allium**: fresh Claude Code session in `allium/extracted/`, run `/distill`,
   repeating passes until a pass finds nothing new (per the Allium README's
   loop-until-dry guidance). Expected artifact: `.allium` spec file(s).
4. Follow each tool's own guidance if it asks for review/input — answering counts as
   human involvement and is logged.

### Phase 2 — Reconstruction (clean room, one dedicated session per tool)

1. Copy **only the spec artifacts** into `<tool>/reconstructed/` (AIUP: `docs/`;
   Allium: `*.allium`). No reference code, no test code, no schema.
2. Same stack constraint given to both as the kickoff prompt (verbatim, both sides):
   > Rebuild this application from the specification in this directory.
   > Stack: Vaadin Flow, jOOQ, Flyway, H2, Maven, JUnit 5.
3. **AIUP**: follow its greenfield construction flow from the recovered artifacts
   (`/flyway-migration` → `/implement` per use case → `/browserless-test` →
   `/playwright-test`, plus the `aiup-vaadin-jooq` coverage pass).
4. **Allium**: `/propagate` → implement against failing tests → `/weed` → loop to
   convergence (or drive via `/allium <goal>`).
5. Stop when the tool's own process says done (tests green / loop converged), not when
   it "looks right" — the stopping decision is part of what we're evaluating.

### Phase 3 — Scoring

Fill `results/scores.md` from the rubric; write up `results/report.md`.

## Metrics

**1. Token usage (ballpark).** Every phase runs in its own dedicated Claude Code
session. At the end of each session run `/cost` and record input tokens, output
tokens, and USD into `results/sessions.md`. Method caveats to state in the report:
figures include cache reads/writes, retries, and any tangents; same model and plugin
versions across all sessions (record them). This gives order-of-magnitude comparison,
not accounting-grade numbers — which is the stated goal.

**2. Spec fidelity (extraction quality).** Score each extracted spec against the
behaviour inventory: per behaviour, **2** = fully captured, **1** = partially/
ambiguously captured, **0** = absent. Also count:
- *invented behaviours* — things the spec asserts that the reference app doesn't do;
- *implementation leakage* — implementation detail presented as requirement
  (more expected in AIUP artifacts by design; still worth counting).

**3. Product fidelity (round-trip quality).** Score each reconstructed app against
the same inventory (2/1/0 per behaviour, verified by manually exercising the app).
Secondary: schema equivalence (compare reconstructed Flyway migrations to the
reference schema — same entities/columns/constraints, modulo naming).

**4. Tests produced.** In each reconstruction, count tests by type: unit,
browserless/UI-server-side, E2E (Playwright), other. Raw counts are gameable, so also
score **behaviour test coverage**: for each inventory behaviour, does at least one
test in the reconstruction exercise it? That's the number that matters.

**5. Human involvement.** Per session, keep `results/logs/<phase>-<tool>.md` and log
every human message after the kickoff prompt, classified as:
- *answer* — the tool asked a question and the human answered;
- *steer* — the human redirected an approach before it went wrong;
- *correct* — the human fixed something after it went wrong;
- *approve* — a rubber-stamp confirmation.
Record wall-clock per session too. Corrections weigh heaviest in the write-up;
answers are arguably a feature (the tool knew what it didn't know).

**6. Convergence and artifact economics.** Number of passes/iterations to done;
size of the spec artifacts (lines and approximate tokens) — the ongoing cost of
maintaining each spec format; time-to-first-question (how quickly each tool surfaced
something it needed a human for).

**7. Stakeholder readability probe (stretch goal).** Give each spec set (no code) to
a fresh session and ask five behaviour questions from the inventory ("can a manager
approve their own claim?"). Score the answers. Proxies for the "readable by all
stakeholders" claim without recruiting actual stakeholders.

## Fairness controls

- Reference app built with neither tool and without reading either tool's docs during
  the build.
- Same model, same Claude Code version, same plugin versions for every session;
  recorded in `results/sessions.md`.
- Pristine copies: neither tool ever sees the other's artifacts, the ground-truth
  inventory, or the results directory.
- Kickoff prompts are fixed and quoted verbatim in the logs.
- Known bias to disclose in the report: the stack is AIUP's home turf (deliberate —
  it's the only way AIUP's construction pipeline can run at all); the reference app
  author (Claude) is also the reconstruction engine for both sides.

## Prerequisites

- Allium plugin (installed: juxt-plugins 3.7.0) + `allium` CLI on PATH
  (`brew tap juxt/allium && brew install allium`).
- AIUP plugins: `/plugin marketplace add AI-Unified-Process/marketplace`, then install
  `aiup-core` and `aiup-vaadin-jooq`.
- Java 21+, Maven; Playwright deps for E2E runs.
