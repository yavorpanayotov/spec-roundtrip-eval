# Use Case: Track Claims

## Overview

**Use Case ID:** UC-006   
**Use Case Name:** Track Claims   
**Primary Actor:** Employee (any staff member — Manager and Finance Officer see additional claims per their role)   
**Goal:** The staff member reviews the claims relevant to their role — status, totals, and full decision history — to know where each claim stands and what needs their attention.   
**Status:** Implemented

## Preconditions

- The staff member is signed in (UC-001).

## Main Success Scenario

1. The staff member opens the claims list.
2. The system shows the claims visible to the staff member's role, newest first, with title, owner, status, total, and submission time.
3. The staff member selects a claim.
4. The system shows the claim's details: status, total, any rejection reason, its expense items, and the history of every action taken on it (who, what, when, and any reason).

## Alternative Flows

### A1: Claim not found

**Trigger:** The staff member navigates to a claim that does not exist (step 4)
**Flow:**

1. The system shows a message that the claim was not found.
2. Use case ends.

## Postconditions

### Success Postconditions

- The staff member has seen the current status, total, items, and history of the selected claim. No data is changed.

### Failure Postconditions

- No data is changed.

## Business Rules

### BR-024: Role-Based Claim Visibility

Every user sees their own claims. In addition, Managers see all Submitted claims (their review queue), and Finance Officers see all Submitted, Approved, and Reimbursed claims (their approval and payout queues). Employees see only their own claims; nobody else sees another user's Drafts or Rejected claims.
