# Use Case: Sign In

## Overview

**Use Case ID:** UC-001   
**Use Case Name:** Sign In   
**Primary Actor:** Employee (any staff member — Employee, Manager, or Finance Officer)   
**Goal:** The staff member identifies themselves to the system so they can work with expense claims under their role's permissions.   
**Status:** Implemented

## Preconditions

- The staff member has a user account in the system (users are provisioned in advance).

## Main Success Scenario

1. The staff member opens the application.
2. The system displays the sign-in page with a list of known users and their roles.
3. The staff member selects their name from the list and signs in.
4. The system starts a session for the selected user and shows the claims list.

## Alternative Flows

### A1: No user selected

**Trigger:** The staff member clicks Sign in without selecting a user (step 3)
**Flow:**

1. The system shows a message asking the staff member to pick a user first.
2. Use case continues at step 3.

### A2: Unauthenticated access to a protected page

**Trigger:** A visitor who is not signed in navigates directly to the claims list or a claim detail page (before step 1)
**Flow:**

1. The system forwards the visitor to the sign-in page.
2. Use case continues at step 2.

### A3: Sign out

**Trigger:** A signed-in staff member chooses Sign out (any time after step 4)
**Flow:**

1. The system ends the session and returns the staff member to the sign-in page.
2. Use case ends.

## Postconditions

### Success Postconditions

- A session is associated with the selected user and their role.
- The staff member sees the claims visible to their role.

### Failure Postconditions

- No session exists; the visitor remains on the sign-in page.

## Business Rules

### BR-001: Sign-in Required

Every page except the sign-in page requires a signed-in user; unauthenticated visitors are forwarded to the sign-in page.

### BR-002: Role-Based User Selection

Sign-in is a user picker without a password. The application demonstrates role-based authorization (Employee, Manager, Finance), not authentication.
