# Expense Claims

An expense claim tracking application reconstructed from the AIUP specification in
[`docs/`](docs/): staff members assemble expense claims, submit them for review, and
managers/finance approve, reject, and reimburse them
(see [`docs/use_cases/`](docs/use_cases) and [`docs/entity_model.md`](docs/entity_model.md)).

## Stack

- **Vaadin Flow 24** — server-side UI (`src/main/java/com/example/expenses/ui`)
- **jOOQ** — type-safe data access; classes are generated from the Flyway DDL at build time
- **Flyway** — schema migrations in `src/main/resources/db/migration`
- **H2** — file database in `./data/expenses.mv.db` (in-memory for tests)
- **Spring Boot / Maven / JUnit 5** — wiring, build, and tests (Karibu server-side UI tests)

## Run

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. Sign-in is a user picker without a password (BR-002);
four users are seeded — two employees, a manager, and a finance officer.

## Test

```bash
mvn test
```

Tests are use-case tests (`UC001…UC006*Test`, annotated with `@UseCase`) plus
service-level business rule tests (`ClaimServiceTest`). They run against an
in-memory H2 database migrated by the same Flyway scripts.

## Business rule configuration

`src/main/resources/application.properties`:

| Property                           | Default   | Rule   |
|------------------------------------|-----------|--------|
| `expenses.receipt-threshold`       | `50.00`   | BR-013 |
| `expenses.manager-approval-limit`  | `5000.00` | BR-020 |
