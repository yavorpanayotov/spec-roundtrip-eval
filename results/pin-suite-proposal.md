# Proposal: Pin Suites — Keep the Old System's Tests and Make the Rebuild Pass Them

**Author:** Yavor Panayotov
**Date:** 2026-07-09
**Status:** Draft — frozen here as an output of the round-trip experiment. Once
submitted to [Allium's proposal process](https://github.com/juxt/allium), the
version there is canonical and this copy is historical.
**Scope:** Skills and workflow only (`distill`, `propagate`, the `allium` loop,
`recommended-loops.md`). **No language change.** Applies to the **code-first flow
only** — and fully only when the distillation feeds a rewrite. Greenfield
(elicit → propagate) is untouched: pins presuppose an existing system to pin.
Documentation-only distillation gets just the coverage requirement (a fully
verified spec), not the pin contract; in-place brownfield evolution needs neither —
there is no old-vs-new gap for pins to close.

## The idea in one paragraph

When Allium distils a spec from an existing system, it already generates tests from
that spec and runs them against the existing code — to check the spec is accurate.
Then it throws those tests away. This proposal says: don't throw them away. Keep
them, call them **pins** (they pin down what the old system does), ship them
alongside the spec, and require any rebuild to pass them. The spec is the
description of the old system; the pins are the measurements. A rebuild should
match both.

## The problem

Today, a rebuild is finished when it satisfies the **spec**. But nobody rewrites a
system to get "something that matches our description of it." They want "something
that behaves like the old one (except where we chose to change it)."

Those are not the same thing. The spec is a summary. If it describes a detail
loosely, or misreads one, the new system can differ from the old one — and every
test will still pass, because all the tests were generated from the same
(imperfect) spec. Nothing in the loop ever compares the new system to the old
system directly.

This bites hardest on legacy code with no tests. Example: the old code says
`amount > 50` (over €50 needs a receipt). Distill misreads it as `>=` (€50 or more).
Today, nothing catches this:

1. The wrong rule goes into the spec.
2. The rebuild's tests are generated from the wrong rule.
3. The rebuild implements the wrong rule.
4. All tests pass. The loop says "converged." The behaviour has silently changed.

A pin generated from that wrong rule and run **against the old system** would fail
immediately — the old app lets a €50.00 item through without a receipt, the pin
expects it blocked. That failure is the alarm bell. Today the loop rings that bell
once, during distillation, and then discards it.

## What happens now (the code-first loop today)

1. `/distill` reads the old code and writes the spec.
2. `/propagate` generates tests from the spec. How many, and how much of the spec
   they cover, is not defined — it's whatever the run happens to produce; there is
   no coverage level the loop promises. (In our experiment: 16 tests.)
3. Those tests run against the **old** code. If they pass, the spec is considered
   accurate — but only for whatever the run happened to cover.
4. The tests are left behind. Only the `.allium` file moves to the rewrite.
5. In the rewrite, `/propagate` runs again and generates a **fresh, independent**
   set of tests from the spec. Because propagate is generative, the fresh suite is
   not a superset of the first one — it's a second, different realization of the
   same spec (different tests, different granularity, its own judgment calls
   wherever the spec is loose). (In our experiment: 89 tests, none shared with
   the 16.)
6. Implement until the fresh tests pass, `/weed` is clean → converged.

Net result: old system matched the spec, as the first suite interpreted it. Newf
system matches the spec, as the second suite interpreted it. Those interpretations
don't have to agree, and the new system was **never compared to the old system**.
In our run the imbalance made it worse: the thorough check (89) pointed at the new
code; the lighter check (16) pointed at the system of record.

## What we propose (the loop with pins)

1. `/distill` reads the old code and writes the spec. *(unchanged)*
2. `/propagate` generates tests from the spec — at a **defined coverage level: the
   full set of obligations** (`allium plan` already enumerates them), instead of
   whatever the run happens to produce — and saves them as a **pin suite** (e.g.
   in `specs/pins/`), each test labelled with the obligation it checks.
3. The pins run against the **old** code. Distillation is not "done" until they
   pass. Two allowed exceptions:
   - a pin on behaviour that has an `open question` is kept but tagged
     *"documents current behaviour"* — it asserts what the system does today while
     the question of what it *should* do stays open;
   - a pin that fails because `/weed` already flagged a real spec↔code divergence
     is linked to that divergence and waits for the human's call.
4. The pins **travel with the spec**. A rewrite receives `spec + pins`, not spec
   alone.
5. In the rewrite, `/propagate` generates fresh tests as before, **and** the pin
   suite runs against the new code.
6. Converged = fresh tests pass **and pins pass** and `/weed` is clean. A pin may
   only be dropped deliberately — together with the spec change (`/tend`) that
   explains why the behaviour is meant to differ.

Net result: the new system is proven to behave like the old one, for everything
pinned. Silent behaviour drift through a loose spec is no longer possible at pin
granularity.

## "Isn't this just running propagate after distill and keeping the tests?"

Almost — that is the mechanism: generate tests from the spec once distillation
settles, prove them against the old system, and make the same suite pass against
the rebuild. But "just propagate" undersells two additions.

The first is a **coverage requirement that doesn't exist today**. There is no
"full" or "partial" propagate — propagate has no coverage modes at all; a run
produces whatever it produces (ours produced 16 tests covering an undefined slice
of the spec). Pins add the requirement: the suite must realize the complete
obligation list that `allium plan` derives from the spec, and distillation isn't
done until that suite passes. Without this, the guarantee is "the rebuild behaves
like the old system, for whatever the run happened to check" — which is no
guarantee.

The second is four rules that ordinary test files can't provide:

1. **Protected from the agent that must satisfy them.** The rebuild is driven by an
   agent with write access to test files, which generates and regenerates tests as
   part of its normal work. If the carried suite is just tests, a failing one
   invites "fixing the test." Pins are read-only to the rebuild: a red pin routes to
   `/weed` as a divergence; removal requires a linked `/tend` spec change.
2. **They can carry an open question.** A plain test asserts "correct." A
   documents-current-behaviour pin asserts "what the old system does, rightly or
   wrongly" — and is retired *with* its question, not silently.
3. **They expire.** Ordinary tests live forever; keeping a duplicate suite past
   convergence creates permanent maintenance drag and breaks on every legitimate
   change (see "How long do pins live?"). Pins have a defined end of life.
4. **They record provenance.** Their authority is the fact "proven green against
   the old system at commit X." A generic test directory loses that meaning; the
   label preserves why these tests are the ones that count.

If the panel prefers to frame the whole thing as "propagate output you keep" rather
than a new noun, nothing of substance is lost — provided the coverage requirement
and these four rules land somewhere enforceable.

## Why we believe the problem is real

From our controlled round-trip experiment (2026-07-08; full data in this repo —
`results/report.md`, `results/scores.md`):

- The pins genuinely didn't travel. Phase 1 verified the spec with 16 tests and
  left them behind; phase 2 regenerated 89 and converged against those. No test the
  old app had passed ever ran against the new app.
- The failure mode is real, not theoretical. The AIUP arm of the same experiment
  (no verification step at all) rebuilt an app whose visibility behaviour differs
  observably from the original — with every test green. Allium avoided this because
  its spec happened to be precise enough, not because anything guaranteed it.
- The planned no-tests rerun is exactly the condition where pins matter most: with
  no existing tests to read, pin verification is the loop's only defence against
  its own misreadings.

## Alternatives we considered

- **Do nothing.** Keeps the promise at "rebuild matches spec." Rejected: the gap
  showed up in a carefully-run controlled experiment following the recommended
  workflow, and the no-tests brownfield case (Allium's strongest pitch) is where
  it's worst.
- **Skip the spec; write tests straight against the old code** (classic
  characterization testing). Rejected as a replacement: tests can't hold an open
  question. They would have pinned our experiment's authorization bug as *correct*,
  frozen forever. The spec stays primary; pins are its portable, executable
  projection. (Also: writing tests directly needs a runnable, testable system;
  distill works on code you can't even start up.)
- **Snapshot/golden-master testing at the HTTP boundary.** Useful where there are
  no seams at all. Complementary, not competing; possible later addition to
  `recommended-loops.md`.
- **Keep regenerating, but make phase 1 generate the full set.** Fixes the 16-vs-89
  imbalance but still only proves both systems match the spec separately. The
  loose-spec drift channel stays open. Strictly weaker than carrying the pins.

## How long do pins live?

Until the rewrite has converged — not forever. A pin's authority comes from one
fact: "the old system demonstrably did this." That authority expires when
"behave like the old system" stops being the goal.

- **During the rewrite:** pins are load-bearing. A red pin blocks convergence (or
  demands an explicit, spec-linked retirement).
- **"Converged" means the old system is no longer the reference** — not just "the
  loop went green once." In a phased migration where the old system stays live for
  months, pins live exactly as long as it does.
- **After convergence:** pins are retired. Keeping them longer is actively harmful:
  from then on the project evolves through the normal loop (`/tend` →
  `/propagate`), every legitimate behaviour change would break a pin, and the team
  would learn to delete failing pins — exactly the habit this proposal forbids
  while pins matter. They're also redundant by then (the new system's own
  propagated suite covers the same obligations) and preserved in version control
  regardless.

Two exceptions:

1. **Open-question pins outlive convergence.** A documents-current-behaviour pin is
   the executable record of a disputed behaviour; it lives until its question is
   answered via `/tend`, then is either promoted to a normal test (keep the
   behaviour) or retired with the spec change (change it). Pin and question share
   a lifespan.
2. **Promotion instead of deletion.** A pin that captures a quirk more precisely
   than the spec expresses it (exact rounding, an ordering guarantee) moves into
   the regular test suite as an ordinary test. Rule of thumb: after convergence the
   pin directory is empty — anything still valuable has been relabelled and moved.

## One honest limit

Pins are real test code, written against the old system's interfaces (its service
layer or API — the skill guidance should target the most stable boundary available,
never UI internals). If the rebuild keeps that boundary — same kind of service API
— the pins run as-is. If the rewrite deliberately changes the boundary (a full
stack switch), the pins can't run directly; they become a precise, executable
reference for writing their replacements, and the spec remains the carrier. This
limit should be stated in the docs, not hidden. It doesn't reduce the value in the
common same-boundary case.

## What changes where

- `/propagate`: gains a pin mode — full obligation coverage, pin labels, "run
  against the old system" as an acceptance step.
- `/weed`: a failing pin is treated as a spec↔code divergence and enters weed's
  existing reconciliation flow.
- `/tend`: answering an open question surfaces the linked
  documents-current-behaviour pins for explicit retirement or update.
- `/allium` loop + `recommended-loops.md`: the convergence definition and the
  "what travels to a rewrite" contract, as in the steps above.
- Language, CLI, existing specs: untouched.

## Cost

Generating and running the full pin set moves real work into distillation. In our
experiment, this kind of verification was most of the gap between the cheap
extraction ($3.12, no verification) and Allium's ($11.35). That's the honest price
of the equivalence guarantee. To avoid paying it when it isn't needed, pins should
be on by default only when the distillation is *rewrite-bound* — which makes "will
this spec drive a rewrite?" an explicit question the loop asks at the start.

## Reversibility

Fully reversible. Pins are ordinary test files plus documentation conventions.
Dropping the idea means deleting a directory convention and reverting docs. No spec
written under this proposal becomes invalid.

## How we'll prove it works (falsifiable, on the existing rig)

1. **No-tests rerun:** strip `src/test` from the reference app and re-distill.
   Prediction: with pins, boundary rules (exactly €50, exactly €5,000,
   "ever-submitted claims can't be deleted") survive; without pins, some get
   misread or lost.
2. **Seeded mutation:** change one `>` to `>=` in a copy of the reference, distill
   without pins, confirm the error ships silently; repeat with pins, confirm a
   failing pin catches it before convergence.
3. **Round trip with pins:** rerun the clean-room rebuild consuming spec + pins;
   the pin suite must pass unchanged against the new app — upgrading the
   experiment's proven property from "rebuild matches spec" to "rebuild behaves
   like the original."

## Open questions

1. Where do pins live (`specs/pins/`?) and what do we call them — "pin suite," or
   "characterization suite" for external familiarity?
2. On by default for rewrite-bound distillation, opt-in otherwise — agreed?
3. How is a pin marked in code: naming convention, annotation, or a manifest
   mapping test → spec rule?
4. Should a failing documents-current-behaviour pin block convergence while its
   open question is unanswered, or only warn?
5. Who or what declares "the old system is no longer the reference" in a phased
   migration — a human statement the loop records, or something detectable?

## Evidence

All figures cited above come from the AIUP-vs-Allium round-trip evaluation in this
repository: `results/report.md` (headline numbers and the visibility-bug chain),
`results/scores.md` (per-behaviour scoring, incl. the 16-vs-89 imbalance),
`results/sessions.md` (cost ledger), `results/logs/` (per-session detail).
