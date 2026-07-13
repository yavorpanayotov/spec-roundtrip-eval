# Use Case: Submit Claim for Review

## Overview

**Use Case ID:** UC-003   
**Use Case Name:** Submit Claim for Review   
**Primary Actor:** Employee (claim owner)   
**Goal:** The employee hands a completed claim over for review so it can be approved and reimbursed, and can take it back while it is still awaiting a decision.   
**Status:** Implemented

## Preconditions

- The employee is signed in (UC-001) and owns the claim.
- The claim is in Draft or Rejected status and has at least one expense item (see UC-002).

## Main Success Scenario

1. The employee opens the claim and chooses Submit.
2. The system checks that the claim has at least one item and that every item above the receipt threshold has a receipt attached.
3. The system moves the claim to Submitted status, records the submission time, and clears any previous decision (decision time and rejection reason).
4. The system records the submission in the claim's history with the employee and the time.
5. The system shows the claim in Submitted status; it now awaits a decision (UC-004).

## Alternative Flows

### A1: Claim has no items

**Trigger:** The claim has no expense items (step 2)
**Flow:**

1. The system shows a message that a claim needs at least one expense item before it can be submitted.
2. Use case ends; the claim remains in its previous status.

### A2: Receipt missing on a large item

**Trigger:** An item's amount exceeds the receipt threshold and no receipt is attached (step 2)
**Flow:**

1. The system shows a message naming the offending item and the threshold.
2. The employee attaches the receipt or corrects the item (UC-002).
3. Use case continues at step 1.

### A3: Claim not submittable

**Trigger:** The claim is not in Draft or Rejected status, or the actor is not the owner (step 1)
**Flow:**

1. The system refuses the submission and shows a message explaining why.
2. Use case ends.

### A4: Withdraw a submitted claim

**Trigger:** The owner chooses Withdraw while the claim is in Submitted status (after step 5)
**Flow:**

1. The system moves the claim back to Draft status.
2. The system records the withdrawal in the claim's history with the employee and the time.
3. Use case continues at step 1 when the employee is ready to resubmit.

## Postconditions

### Success Postconditions

- The claim is in Submitted status with a submission timestamp and no leftover decision data.
- The submission (and any withdrawal) appears in the claim's history.

### Failure Postconditions

- The claim's status and content are unchanged; the employee sees a message explaining the refusal.

## Business Rules

### BR-011: Submission From Draft or Rejected Only

Only claims in Draft or Rejected status can be submitted, and only by their owner. Rejected claims may be corrected and resubmitted.

### BR-012: At Least One Item

A claim must contain at least one expense item before it can be submitted.

### BR-013: Receipt Threshold

Every item whose amount exceeds the receipt threshold (configured, currently 50.00 EUR) must have a receipt attached before the claim can be submitted. An item exactly at the threshold needs no receipt.

### BR-014: Resubmission Clears the Previous Decision

Submitting a claim clears the previous decision timestamp and rejection reason.

### BR-015: Withdrawal From Submitted Only

Only claims in Submitted status can be withdrawn, and only by their owner. Withdrawal returns the claim to Draft.

### BR-016: Full Audit Trail

Every state transition (submitted, withdrawn, approved, rejected, reimbursed) is recorded in the claim's history with the acting user, the action, the time, and the reason where one was given. This rule also applies in UC-004 and UC-005.
