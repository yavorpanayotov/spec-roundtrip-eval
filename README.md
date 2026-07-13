# AIUP vs Allium: a code → spec → code round-trip evaluation

Two AI spec tools were made to reverse-engineer a specification from the same
unseen codebase, then rebuild the application in a clean room from their own spec
alone. This repository is the complete experiment — the test rig (reference app,
scoring rubric, protocol, probe scripts) and the complete results (both extracted
specs, both rebuilds, per-behaviour scorecards, and the cost ledger).

## The reference app

The subject of the experiment is a working expense-claims application: employees
create claims and add itemised expenses, submit them for review; managers approve
or reject (rejection requires a reason; nobody can decide on their own claim, and
managers only up to a limit); finance reimburses approved claims. Five lifecycle
states, role-scoped visibility, monetary thresholds held in configuration, and an
audit trail of every transition.

It was built with **neither tool**, against a frozen inventory of 23 behaviours
plus 3 constraints ([`ground-truth/behaviour-inventory.md`](ground-truth/behaviour-inventory.md))
— each behaviour enforced in the service layer, covered by a test, and verified
through a real browser before the inventory was frozen as the scoring rubric.

The stack — Vaadin + jOOQ + Flyway + H2 — was chosen deliberately: it is
[AIUP](https://unifiedprocess.ai)'s native stack, the only one its full
construction pipeline supports. That matters because of the disclosure: this
evaluation was run by an [Allium](https://github.com/juxt/allium) contributor, so
the match was played on the other tool's home ground.

## The experiment

Each tool got a pristine copy of the source in a fresh session and extracted a
specification — AIUP's `/reverse-engineer` producing markdown use cases and an
entity model, Allium's loop distilling a formal behavioural spec. Then each spec
went alone into a clean room: a directory containing nothing else, a fresh
session, and the instruction to rebuild the application. Whatever survives the
round trip is what the spec actually captured. Every behaviour was then scored
2/1/0 against the frozen rubric — on the extracted specs and again on the rebuilt
apps — with costs recorded per session.

## Headline results

Both rebuilds scored 52/52 against the rubric. The separation was at the edges:
despite three layers of verification, the reference app shipped with a real
authorization bug — the claim *list* is role-scoped, but the claim *detail* page
does no visibility check, so any signed-in user could open any claim by URL.
Allium's loop found it, parked it as an open question, and reproduced the original
exactly; AIUP's pipeline absorbed it into an over-claiming spec, and its rebuild
silently invented a third behaviour. Full numbers, the defect chain, and every
caveat (including the ones that favour each side):
[`results/report.md`](results/report.md).

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
