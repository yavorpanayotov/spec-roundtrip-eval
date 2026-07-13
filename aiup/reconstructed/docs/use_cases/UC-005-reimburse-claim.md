# Use Case: Reimburse Claim

## Overview

**Use Case ID:** UC-005   
**Use Case Name:** Reimburse Claim   
**Primary Actor:** Finance Officer   
**Goal:** The finance officer marks an approved claim as reimbursed so the claim lifecycle is closed and the payout is on record.   
**Status:** Implemented

## Preconditions

- The finance officer is signed in (UC-001) with the Finance role.
- The claim is in Approved status (UC-004).

## Main Success Scenario

1. The finance officer opens an approved claim from the claims list.
2. The finance officer chooses Reimburse.
3. The system moves the claim to Reimbursed status and records the reimbursement time.
4. The system records the reimbursement in the claim's history.
5. The system shows the claim in Reimbursed status; the claim is closed.

## Alternative Flows

### A1: Actor is not a Finance Officer

**Trigger:** A Manager or Employee attempts to reimburse a claim (step 2)
**Flow:**

1. The system refuses and shows a message that only finance can reimburse claims.
2. Use case ends.

### A2: Claim not approved

**Trigger:** The claim is not in Approved status (step 3)
**Flow:**

1. The system refuses and shows a message that only approved claims can be reimbursed.
2. Use case ends.

## Postconditions

### Success Postconditions

- The claim is in Reimbursed status with a reimbursement timestamp and can no longer be changed, resubmitted, or decided on.
- The reimbursement appears in the claim's history with the finance officer and the time (BR-016).

### Failure Postconditions

- The claim's status is unchanged; the actor sees a message explaining the refusal.

## Business Rules

### BR-022: Finance Reimburses Approved Claims Only

Only users with the Finance role may reimburse a claim, and only claims in Approved status can be reimbursed.

### BR-023: Reimbursed Is Terminal

A reimbursed claim is final: it cannot be resubmitted, withdrawn, approved, rejected, reimbursed again, or have its items changed.
