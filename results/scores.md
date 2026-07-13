# Rubric Scores

Scoring: **2** fully captured · **1** partial/ambiguous · **0** absent.
Scored by the evaluator session against `ground-truth/behaviour-inventory.md` (frozen).

## AIUP extracted spec (phase 1, scored 2026-07-08)

Artifacts: `docs/use_cases.puml`, 6 × `UC-*.md`, `docs/entity_model.md` — 8 files,
581 lines. Behaviours are captured as 24 numbered business rules (BR-001…BR-024)
spread across the use case specs, plus scenario steps.

| ID | Score | Notes |
|----|-------|-------|
| B01 | 2 | BR-004, verbatim including "no submission timestamp" |
| B02 | 2 | BR-012 + UC-003 A1 |
| B03 | 2 | UC-003 main scenario step 3 |
| B04 | 2 | BR-015 (owner-only, submitted-only) |
| B05 | 2 | BR-009 — explicitly covers the withdrawn-claim-not-deletable case |
| B06 | 2 | BR-011 + UC-002 A4 |
| B07 | 2 | BR-023, enumerates every blocked transition |
| B08 | 2 | BR-006 |
| B09 | 2 | BR-007 |
| B10 | 2 | BR-008, including the "today is allowed" boundary |
| B11 | 2 | BR-013, configured threshold + exactly-at-threshold boundary |
| B12 | 2 | BR-010, including empty-claim total = 0 |
| B13 | 2 | BR-017 |
| B14 | 2 | BR-018 |
| B15 | 2 | BR-019, "regardless of role" |
| B16 | 2 | BR-020, configured limit + exactly-at-limit boundary |
| B17 | 2 | BR-021, stored + shown to owner |
| B18 | 2 | UC-004 steps 5–6 |
| B19 | 2 | BR-022 + UC-005 |
| B20 | 2 | BR-024 |
| B21 | 2 | BR-024, "all Submitted claims (their review queue)" |
| B22 | 2 | BR-024 |
| B23 | 2 | BR-016 + UC-006 step 4 (history visible on detail) |
| **Behaviours** | **46/46** | |
| C01 | 2 | Amounts described as EUR throughout |
| C02 | 2 | BR-013/BR-020: "configured, currently …" |
| C03 | 0 | Stack constraint nowhere in artifacts — see note 2 |
| **Constraints** | **4/6** | |
| **Total** | **50/52 (96%)** | |

**Invented behaviours: 2.** (a) Entity model gives `email` a "Format: Email"
validation rule; nothing in schema or code validates email format. (b) BR-024 asserts
"nobody else sees another user's Drafts or Rejected claims" — false: the claim detail
view is reachable by URL for any claim by any signed-in user (KD-1 — "known defect
1", a real reference-app bug found by Allium's distill; defined in the Allium
section below). AIUP's spec states the *intended* visibility as
if enforced, silently absorbing the defect; it raised no questions anywhere.

**Bonus true behaviours beyond the rubric** (correct, not counted): unauthenticated
visitors forwarded to sign-in (UC-001 A2); resubmission clears the previous decision
(BR-014 — true, `submit()` nulls `decided_at`/`decision_reason`); claims list newest
first (UC-006 — true, `created_at desc`); claim requires non-blank title (BR-003).

**Implementation leakage:** the entity model deliberately mirrors the schema (column
types, lengths, PK/sequence, FK + cascade delete) — by AIUP design, but it presents
storage decisions as model facts (~10 instances). Use case prose stays behavioural.

**Notes:**
1. **Scope gap → C03**: `/reverse-engineer` produces use cases + entity model only —
   no requirements catalog, so non-functional requirements and constraints (the
   greenfield `/requirements` output) have no home. Stack constraints went uncaptured.
2. **Threat to validity (applies to BOTH tools):** the reference test suite
   (`ClaimServiceTest`) carries `@DisplayName` strings that restate the rubric almost
   verbatim (B01–B23). Any extractor that reads the tests gets the ground truth in
   prose. Symmetric between the tools, so the comparison stands, but absolute
   fidelity numbers are inflated vs. a typical legacy codebase. Optional harder-mode
   rerun: strip `src/test` from both extraction copies and repeat phase 1.

## Allium extracted spec (phase 1, scored 2026-07-08)

Artifacts: `specs/expense-claims.allium` — 1 file, 306 lines (config block, 4 entities
with a status transition table, 10 rules, 3 invariants, 2 surfaces, 4 open questions).
The session also ran `/propagate`, generating `ExpenseClaimsSpecTest.java` (465 lines,
16 tests) and running it against the existing code — a spot-check of the spec, not
full verification (propagate promises no coverage level; 16 tests is what this run
produced), but beyond AIUP's extraction scope, which verifies nothing.

| ID | Score | Notes |
|----|-------|-------|
| B01 | 2 | `UserCreatesClaim` — created as draft, owner = actor |
| B02 | 2 | submit `requires items.count >= 1` + `SubmittedClaimsHaveItems` invariant |
| B03 | 2 | ensures status/submitted_at |
| B04 | 2 | `OwnerWithdrawsClaim`, owner + submitted-only |
| B05 | 2 | `not claim.has_been_submitted`; submitted_at semantics documented ("never cleared") |
| B06 | 2 | `rejected -> submitted` transition + `is_editable` includes rejected |
| B07 | 2 | `terminal: reimbursed` in the transitions table |
| B08 | 2 | `is_editable` gating all three item rules + owner checks |
| B09 | 2 | AddItem/UpdateItem requires (typed category, non-blank description, amount > 0, date) |
| B10 | 2 | `expense_date <= now`, day-granularity noted |
| B11 | 2 | derived `requires_receipt` + submit guard + `ReceiptsPresentOnceSubmitted` invariant |
| B12 | 2 | derived `total: sum(items, i => i.amount)` |
| B13 | 2 | both decision rules require `status = submitted` |
| B14 | 2 | `actor.role != employee` |
| B15 | 2 | `actor != claim.owner` on approve and reject |
| B16 | 2 | `total <= limit or role = finance` — boundary exact |
| B17 | 2 | non-blank reason, stored, exposed on detail `when status = rejected` |
| B18 | 2 | decided_at + log entry with actor |
| B19 | 2 | finance-only, approved → reimbursed, timestamp |
| B20 | 2 | `ClaimsList` context where-clause + `@guarantee RoleScopedVisibility` |
| B21 | 2 | same where-clause, manager arm |
| B22 | 2 | same where-clause, finance arm |
| B23 | 2 | log entry in every transition rule; history exposed on `ClaimDetail`; explicitly notes what is NOT logged |
| **Behaviours** | **46/46** | |
| C01 | 2 | EUR stated on both config values |
| C02 | 2 | `config` block — thresholds are first-class configuration |
| C03 | 1 | Header comment names Spring Boot/Vaadin; jOOQ/Flyway/H2 absent (implementation exclusion is by language design) |
| **Constraints** | **5/6** | |
| **Total** | **51/52 (98%)** | |

**Invented behaviours: 0.**

**Implementation leakage: 0** — persistence/UI/framework explicitly excluded in the
scope header; the one type fudge (expense_date as day-granular Timestamp) is flagged
in a comment.

**Intent findings (the differentiator):** 4 parked open questions, adjudicated by the
reference author:
1. *Claim detail unscoped by URL* — **real defect (KD-1, "known defect 1")**: `ClaimDetailView`/`getClaim`
   never check visibility; the reference app violates the intent of B20–B22 and the
   evaluator's UI tests missed it (they only exercised the list). Confirmed oversight.
2. *No self-reimbursement guard* — **real gap (KD-2, "known defect 2")**: true in code, and the rubric
   itself never specified it (B15 covers approve/reject only). An implicit behaviour
   that was never explicitly decided — surfaced correctly as a question.
3. *Withdrawn claims permanently undeletable* — deliberate (B05); correctly inferred
   as probably-intentional from the tests, asked anyway.
4. *Creation/deletion/item edits unlogged* — deliberate (B23 scopes the log to
   transitions); fair question.

**Experiment decision:** KD-1/KD-2 stay in the reference unfixed — both tools
extracted the same code, so the comparison is symmetric. Phase 2 reconstructs from
each spec as-extracted (open questions left parked) to keep the inputs untouched.

## AIUP reconstruction (phase 2, scored 2026-07-08)

Verified by: reading `ClaimService`/`ClaimRepository`, running the suite (43/43
green), and an independent Playwright probe of the running app (full lifecycle
across all four roles + KD-1 URL probe). All 23 behaviours **2** — every rule
enforced in the service layer, boundaries included; visibility query matches the
rubric matrix; probe confirmed the happy path and list scoping end-to-end.
Constraints: C01 2, C02 2 (`ExpensesProperties`), C03 2 (Spring Boot + Vaadin 24.10 +
jOOQ/DDLDatabase + Flyway + H2 + JUnit 5). **Total 52/52.**

**Round-trip divergences from the original:**
1. **Detail-access policy mutated (from KD-1).** Original: any signed-in user can
   open any claim by URL. Reconstruction: owner always; Manager/Finance for any
   non-draft claim; employees never see foreign claims (probe: foreign detail
   HIDDEN to employee). Root cause chain: the reference defect → AIUP's spec
   asserted intended visibility as enforced (BR-024) → the rebuilder noticed BR-024
   contradicts UC-004 step 7 and *invented a third policy*, documented in a code
   comment but not surfaced as a question. Arguably an improvement; still a silent
   behavioural mutation through the round trip.
2. Seed data lost: 4 invented users (one manager) vs the original 5 (two managers) —
   seed data was never in the spec artifacts.
3. Schema equivalent modulo mechanics: same 4 tables/columns; 5 migrations vs 2,
   sequences vs identity columns.

**Tests: 43** — 31 browserless UI (Karibu, 6 use-case classes, `@UseCase`-tagged) +
12 service tests. E2E (Playwright): 0 committed. Behaviour test coverage: all 23
rubric behaviours reachable from test names/BR tags (spot-checked, not instrumented).

## Allium reconstruction (phase 2, scored 2026-07-08)

Verified by: reading `ExpenseService`, running the suite (89/89 green), and the same
independent Playwright probe (app on :8090). All 23 behaviours **2**; visibility
query identical in effect to the rubric matrix; probe confirmed the full lifecycle.
Constraints: C01 2, C02 2 (`AppConfig`), C03 2 — notably took the stack list
literally: plain Vaadin + Jetty, **no Spring** (Spring wasn't in the kickoff
constraint; both interpretations are compliant). **Total 52/52.**

**Round-trip divergences from the original: 0 behavioural.** The probe confirmed the
faithful reproduction of KD-1 (foreign claim detail VISIBLE to an employee by URL,
"as specified" per code comment) and the spec's open questions survived to the human
— all four implemented as the original behaves, one with a test documenting the
behaviour. During `/weed` the session found one spec↔code divergence (the UI's
"receipt missing" hint exposes derived `requires_receipt` not listed in the surface)
and fixed the *spec*, not the code. Seed data likewise invented (4 users).

**Tests: 89** — service-level integration tests propagated from 81 spec obligations,
run red-first (88/89 failing before implementation). By class: ItemRuleTest 19,
ClaimLifecycleTest 17, DecisionTest 16, SurfaceTest 18 (visibility/guard matrices),
TransitionGraphTest 8, EntityStateTest 7, ScenarioInvariantTest 4. By type:
rule/acceptance 52, state machine 15, surface/authorization 18, invariant 4.
UI-component tests: 0. Browser E2E: 0. Behaviour test coverage: all 23 rubric
behaviours reachable from obligation-derived test names (spot-checked).

## Round-trip verdict (code → spec → code)

Both reconstructions score 52/52 against the frozen rubric and pass an independent
UI probe. The separation is in *what happened to unspecified/defective behaviour*:
AIUP's pipeline absorbed a defect into an over-claiming spec and the round trip
mutated the behaviour without asking; Allium's pipeline modeled the defect, parked
the question, and reproduced the original exactly — decision still owned by the
human. Fidelity-to-original: Allium 1st. Fidelity-to-intent: AIUP's mutation lands
closer to intent by luck of the rebuilder's judgment, not by process guarantee.
