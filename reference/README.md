# Expense Claims

Internal web application for submitting and approving employee expense claims.

Employees create claims with itemised expenses and submit them for review. Managers
approve or reject submitted claims; finance reimburses approved ones. Sign-in is a
simple user picker — the app demonstrates role-based authorization, not
authentication.

## Stack

- Java 21, Spring Boot
- Vaadin Flow (server-side UI)
- jOOQ (type-safe SQL, code generated from the Flyway schema)
- Flyway migrations, H2 database (file-based)

## Run

```
mvn spring-boot:run
```

Then open http://localhost:8080 and sign in as one of the seeded users.

## Test

```
mvn test
```
