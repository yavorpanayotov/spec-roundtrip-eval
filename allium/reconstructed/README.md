# Expense Claims

Rebuilt from the Allium specification in [`specs/expense-claims.allium`](specs/expense-claims.allium).

Scope: the expense claim lifecycle — creation, itemisation, submission,
approval/rejection, reimbursement, and the audit trail of decisions.
Sign-in is a demo user picker by design; the application demonstrates
role-based authorization, not authentication.

## Stack

- **Vaadin Flow 24** — UI (`src/main/java/com/example/expenses/ui`)
- **jOOQ** — typed SQL; classes generated from the Flyway DDL at build time
- **Flyway** — schema migrations (`src/main/resources/db/migration`)
- **H2** — in-memory database (data resets on restart; users are seeded)
- **Maven**, **JUnit 5**

## Layout

| Path | Role |
|------|------|
| `specs/expense-claims.allium` | the behavioural specification (source of truth) |
| `src/main/java/.../domain` | enums, DTOs matching the spec's surfaces, config, rule-violation exception |
| `src/main/java/.../service/ExpenseService.java` | one method per spec rule + surface queries |
| `src/main/java/.../ui` | Vaadin views for the ClaimsList and ClaimDetail surfaces |
| `src/test/java/.../service` | tests propagated from the spec (do not weaken to make them pass — fix spec or code) |

## Run

```bash
mvn jetty:run          # dev mode, http://localhost:8080
mvn test               # spec-propagated test suite
mvn package -Pproduction   # production frontend build
```

Demo users (seeded): Alice and Ben (employees), Mara (manager), Frank (finance).

## Domain rules in one paragraph

Employees create draft claims and itemise them; items above €50 need a
receipt. A claim with at least one item (receipts in order) can be submitted;
the owner may withdraw it back to draft, but once submitted it can never be
deleted. Managers and finance approve or reject submitted claims (never their
own); claims above €5000 can only be approved by finance. Finance reimburses
approved claims — a terminal state. Submissions, withdrawals, approvals,
rejections and reimbursements are recorded in an append-only decision log.
