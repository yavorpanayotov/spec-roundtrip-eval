# Use Case: Manage Expense Claim

## Overview

**Use Case ID:** UC-002   
**Use Case Name:** Manage Expense Claim   
**Primary Actor:** Employee (any staff member acting as claim owner)   
**Goal:** The employee assembles an expense claim — creating it and adding, correcting, or removing itemised expenses — so it is ready to submit for reimbursement.   
**Status:** Implemented

## Preconditions

- The employee is signed in (UC-001).
- For editing or deleting: the employee owns the claim and the claim is in Draft or Rejected status.

## Main Success Scenario

1. The employee chooses to create a new claim.
2. The employee enters a title for the claim.
3. The system creates the claim in Draft status, owned by the employee, and opens its detail page.
4. The employee adds an expense item, providing a category, description, amount, expense date, and whether a receipt is attached.
5. The system validates the item and adds it to the claim.
6. The system recalculates and shows the claim total as the sum of its items.
7. The employee repeats steps 4–6 until all expenses are captured.

## Alternative Flows

### A1: Missing or blank title

**Trigger:** The employee confirms creation without a title (step 2)
**Flow:**

1. The system shows a message that a claim needs a title and does not create the claim.
2. Use case continues at step 2.

### A2: Invalid item

**Trigger:** The item is missing a category, description, or expense date, or the amount is not greater than zero (step 5)
**Flow:**

1. The system shows a message naming the missing or invalid field and does not save the item.
2. Use case continues at step 4.

### A3: Expense date in the future

**Trigger:** The expense date is after today (step 5)
**Flow:**

1. The system shows a message that the expense date cannot be in the future and does not save the item.
2. Use case continues at step 4.

### A4: Edit or remove an existing item

**Trigger:** The employee chooses to edit or remove an item on a Draft or Rejected claim (step 4)
**Flow:**

1. For an edit, the employee changes the item's details and saves; the system validates as in steps 5, A2, and A3.
2. For a removal, the system removes the item from the claim.
3. Use case continues at step 6.

### A5: Delete a draft claim

**Trigger:** The employee chooses to delete a claim that is in Draft status and has never been submitted (step 3 or later)
**Flow:**

1. The system deletes the claim together with its items and returns the employee to the claims list.
2. Use case ends.

### A6: Claim not editable

**Trigger:** The employee attempts to change items on a claim that is not in Draft or Rejected status, or on a claim they do not own (step 4)
**Flow:**

1. The system refuses the change and shows a message explaining why.
2. Use case ends.

## Postconditions

### Success Postconditions

- The claim exists in Draft (or Rejected) status with the employee as owner and the entered items attached.
- The claim total equals the sum of its item amounts.

### Failure Postconditions

- The claim and its items are unchanged; the employee sees a message explaining the refused action.

## Business Rules

### BR-003: Claim Requires a Title

A claim cannot be created without a non-blank title.

### BR-004: New Claims Start as Draft

A newly created claim is in Draft status, owned by its creator, with no items and no submission timestamp.

### BR-005: Owner-Only Changes

Only the claim owner may change the claim or its items (submit, withdraw, delete, add/edit/remove items).

### BR-006: Items Editable Only in Draft or Rejected

Expense items can be added, changed, or removed only while the claim is in Draft or Rejected status.

### BR-007: Item Completeness

An expense item requires a category (Travel, Meals, Accommodation, Equipment, or Other), a non-blank description, an amount greater than zero, and an expense date.

### BR-008: No Future Expenses

The expense date cannot be in the future; today is allowed.

### BR-009: Only Never-Submitted Drafts Can Be Deleted

A claim can be deleted only while it is in Draft status and has never been submitted. A withdrawn claim (back in Draft after a submission) cannot be deleted.

### BR-010: Claim Total

The claim total is the sum of its item amounts; a claim without items has a total of zero.
