# AIUP vs Allium: a code → spec → code round-trip evaluation

Two AI spec tools were made to reverse-engineer a specification from the same
unseen codebase, then rebuild the application in a clean room from their own spec
alone. This repository is the complete experiment — the test rig (reference app,
scoring rubric, protocol, probe scripts) and the complete results (both extracted
specs, both rebuilds, per-behaviour scorecards, and the cost ledger).

**The contenders:** [AIUP](https://unifiedprocess.ai)'s `/reverse-engineer`
(markdown use cases + entity model) vs [Allium](https://github.com/juxt/allium)'s
`/distill` (a formal behavioural spec). Disclosure: the evaluation was run by an
Allium contributor — which is why the reference app was deliberately built on
**AIUP's native stack** (Vaadin + jOOQ + Flyway), the only setup where AIUP's full
construction pipeline runs.

## Headline results

Both rebuilds scored 52/52 against the frozen behaviour rubric. The separation was
at the edges: the reference app accidentally shipped a real authorization bug that
its author, its tests, and its browser drive all missed. Allium's loop found it,
parked it as an open question, and reproduced the original exactly; AIUP's
pipeline absorbed it into an over-claiming spec, and its rebuild silently invented
a third behaviour. Full numbers, the defect chain, and every caveat (including the
ones that favour each side): [`results/report.md`](results/report.md).

## Layout

| Path | What it is |
|---|---|
| `EVALUATION.md` | The protocol: phases, clean-room rules, metrics, fairness controls |
| `ground-truth/` | The frozen behaviour inventory — build spec and scoring rubric |
| `reference/` | The original app, built with neither tool |
| `aiup/extracted/` | Pristine source copy + AIUP's extracted artifacts (`docs/`) |
| `aiup/reconstructed/` | Clean-room rebuild from AIUP's docs alone |
| `allium/extracted/` | Pristine source copy + Allium's extracted spec (`specs/`) |
| `allium/reconstructed/` | Clean-room rebuild from the `.allium` spec alone |
| `results/report.md` | The match report |
| `results/scores.md` | Per-behaviour scorecards (2/1/0 against the rubric) |
| `results/sessions.md` | Token/cost/time ledger, one row per session |
| `results/logs/` | Per-phase session logs, including one discarded run |
| `results/rig/` | The independent Playwright probes used to verify both rebuilds |
| `results/pin-suite-proposal.md` | The follow-up this experiment motivated for Allium |

## Reproduce

Each app builds and tests standalone (Java 21+, Maven): `mvn test` in
`reference/`, `aiup/reconstructed/`, or `allium/reconstructed/`. jOOQ classes
generate from the Flyway DDL at build time; no database setup needed. Run an app
with `mvn spring-boot:run` (reference, AIUP rebuild) or `mvn jetty:run` (Allium
rebuild), then drive it with `results/rig/probe.js` (Playwright).

To rerun the experiment itself, follow `EVALUATION.md`: the protocol is
tool-agnostic — bring your own contenders.

## Threats to validity

Read them before quoting the numbers: `results/report.md` § "Threats to
validity". The two biggest: the reference test suite's names restate the rubric
(inflates both tools' extraction scores symmetrically), and the stack is AIUP's
home turf (deliberate, disclosed).
