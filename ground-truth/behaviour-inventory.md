# Expense Claims — Behaviour Inventory (Ground Truth)

This is both the build spec for `reference/` and the scoring rubric for both
extractions and reconstructions. Frozen once the reference app passes all of it.

Scoring per behaviour: **2** fully captured/implemented · **1** partial or ambiguous
· **0** absent.

## Actors

- **Employee** — submits and manages their own claims.
- **Manager** — approves/rejects submitted claims, up to a limit.
- **Finance** — approves anything, reimburses approved claims.

One user, one role (kept deliberately simple). Login selects the user; no password
complexity requirements — authentication itself is not under evaluation,
authorization is.

## Entities

- **User**: name, email, role (employee | manager | finance).
- **ExpenseClaim**: owner, title, status (draft | submitted | approved | rejected |
  reimbursed), created/submitted/decided/reimbursed timestamps, decision reason.
- **ExpenseItem**: claim, category (travel | meals | accommodation | equipment |
  other), description, amount (EUR), expense date, receipt flag.
- **DecisionLog**: claim, actor, action (submitted | withdrawn | approved | rejected |
  reimbursed), timestamp, reason (for rejections).

## Behaviours

### Claim lifecycle

| ID | Behaviour |
|----|-----------|
| B01 | A new claim starts in `draft`, owned by its creator, with zero items. |
| B02 | A draft can only be submitted if it has at least one expense item. |
| B03 | Submitting moves `draft → submitted` and records the submission timestamp. |
| B04 | The owner can withdraw a `submitted` claim back to `draft` (only the owner, only from `submitted`). |
| B05 | A draft can be deleted by its owner; a claim that has ever been submitted can never be deleted. |
| B06 | A rejected claim can be edited by its owner and resubmitted (`rejected → submitted`). |
| B07 | `reimbursed` is terminal — no edits, no transitions out. |

### Items and validation

| ID | Behaviour |
|----|-----------|
| B08 | Items can only be added, edited, or removed while the claim is in `draft` or `rejected`. |
| B09 | An item requires a category, a description, an amount > 0, and an expense date. |
| B10 | An item's expense date cannot be in the future. |
| B11 | Any item with amount over €50 must have the receipt flag set; submission is blocked otherwise. |
| B12 | The claim total is the sum of its items, displayed on the claim and in lists (derived, never stored stale). |

### Approval and authorization

| ID | Behaviour |
|----|-----------|
| B13 | Only `submitted` claims can be approved or rejected. |
| B14 | Managers and finance can approve; employees cannot. |
| B15 | Nobody can approve or reject their own claim, regardless of role. |
| B16 | A manager can only approve claims totalling €5,000 or less; above that only finance can approve. |
| B17 | Rejection requires a non-empty reason, stored on the claim and shown to the owner. |
| B18 | Approval moves `submitted → approved` and records who decided and when. |

### Reimbursement

| ID | Behaviour |
|----|-----------|
| B19 | Only finance can reimburse, and only `approved` claims (`approved → reimbursed`), recording the timestamp. |

### Visibility and audit

| ID | Behaviour |
|----|-----------|
| B20 | Employees see only their own claims. |
| B21 | Managers see their own claims plus all `submitted` claims awaiting decision. |
| B22 | Finance sees all claims in `submitted`, `approved`, and `reimbursed` states, plus their own. |
| B23 | Every state transition (submit, withdraw, approve, reject, reimburse) appends a DecisionLog entry with actor and timestamp; the log is visible on the claim detail view. |

### Constraints (non-behavioural, for the constraints-recovery comparison)

| ID | Constraint |
|----|-----------|
| C01 | Single currency: EUR. |
| C02 | Amount threshold values (€50 receipt, €5,000 manager limit) are configuration, not hard-coded in business logic. |
| C03 | Stack: Vaadin Flow UI, jOOQ data access, Flyway migrations, H2 database. |

## Scoring sheet template

Copy into `results/scores.md`, one table per artifact scored (AIUP spec, Allium spec,
AIUP reconstruction, Allium reconstruction): columns `ID | Score (0/1/2) | Notes`.
Then: invented behaviours (list them), implementation leakage count (specs only),
behaviour test coverage (reconstructions only: which IDs have ≥1 test).
