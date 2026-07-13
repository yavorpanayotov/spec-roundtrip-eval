# Use Case: Review Submitted Claim

## Overview

**Use Case ID:** UC-004   
**Use Case Name:** Review Submitted Claim   
**Primary Actor:** Manager (Finance Officer for claims above the manager approval limit)   
**Goal:** The reviewer decides on a submitted claim — approving it for reimbursement or rejecting it with a reason so the owner can correct and resubmit it.   
**Status:** Implemented

## Preconditions

- The reviewer is signed in (UC-001) with the Manager or Finance role.
- The claim is in Submitted status.
- The reviewer is not the owner of the claim.

## Main Success Scenario

1. The reviewer opens a submitted claim from the claims list.
2. The system shows the claim's items, total, and history.
3. The reviewer chooses Approve.
4. The system checks that the reviewer is allowed to decide on this claim and that the claim total is within the reviewer's approval authority.
5. The system moves the claim to Approved status and records who decided and when.
6. The system records the approval in the claim's history.
7. The system shows the claim in Approved status; it now awaits reimbursement (UC-005).

## Alternative Flows

### A1: Reject with a reason

**Trigger:** The reviewer chooses Reject instead of Approve (step 3)
**Flow:**

1. The system asks for a rejection reason.
2. The reviewer enters a non-blank reason and confirms.
3. The system moves the claim to Rejected status, stores the reason on the claim, and records who decided and when.
4. The system records the rejection and its reason in the claim's history.
5. Use case ends; the owner may correct and resubmit the claim (UC-002, UC-003).

### A2: Missing rejection reason

**Trigger:** The rejection reason is empty or blank (A1 step 2)
**Flow:**

1. The system shows a message that rejecting a claim requires a reason and leaves the claim in Submitted status.
2. Use case continues at A1 step 1.

### A3: Claim total exceeds the reviewer's authority

**Trigger:** The claim total exceeds the manager approval limit and the reviewer is a Manager, not a Finance Officer (step 4)
**Flow:**

1. The system refuses the approval and shows a message that claims over the limit can only be approved by finance.
2. Use case ends; a Finance Officer must review the claim instead.

### A4: Reviewer not allowed to decide

**Trigger:** The actor is an Employee, or is the owner of the claim, or the claim is not in Submitted status (step 4)
**Flow:**

1. The system refuses the decision and shows a message explaining why.
2. Use case ends.

## Postconditions

### Success Postconditions

- The claim is in Approved or Rejected status, with the decision time recorded; a rejected claim carries the rejection reason.
- The decision appears in the claim's history with the reviewer, the time, and the reason where one was given (BR-016).

### Failure Postconditions

- The claim remains in Submitted status, unchanged; the reviewer sees a message explaining the refusal.

## Business Rules

### BR-017: Decide on Submitted Claims Only

Only claims in Submitted status can be approved or rejected.

### BR-018: Reviewers Are Managers or Finance

Only users with the Manager or Finance role may approve or reject claims; Employees cannot decide.

### BR-019: No Self-Approval

No one may approve or reject their own claim, regardless of role. Another user with sufficient authority must decide.

### BR-020: Manager Approval Limit

Claims whose total exceeds the manager approval limit (configured, currently 5,000.00 EUR) can only be approved by a Finance Officer. A total exactly at the limit may still be approved by a Manager.

### BR-021: Rejection Requires a Reason

A claim can only be rejected with a non-blank reason, which is stored on the claim and shown to the owner.
