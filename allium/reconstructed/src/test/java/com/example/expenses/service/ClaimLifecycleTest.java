package com.example.expenses.service;

import com.example.expenses.domain.Category;
import com.example.expenses.jooq.tables.records.DecisionLogEntryRecord;
import com.example.expenses.jooq.tables.records.ExpenseClaimRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated from specs/expense-claims.allium — rules UserCreatesClaim,
 * OwnerSubmitsClaim, OwnerWithdrawsClaim, OwnerDeletesClaim.
 */
class ClaimLifecycleTest extends ServiceTestBase {

    @Nested
    class UserCreatesClaim {

        // rule-success.UserCreatesClaim / rule-entity-creation.UserCreatesClaim.1
        @Test
        void createsDraftClaimOwnedByActorWithTrimmedTitle() {
            long id = service.createClaim(alice, "  Conference travel  ");
            ExpenseClaimRecord c = claim(id);
            assertEquals(alice, c.getOwnerId());
            assertEquals("Conference travel", c.getTitle());
            assertEquals("DRAFT", c.getStatus());
            assertEquals(now(), c.getCreatedAt());
            assertNull(c.getSubmittedAt());
            assertNull(c.getDecidedAt());
            assertNull(c.getReimbursedAt());
            assertNull(c.getDecisionReason());
        }

        // rule-failure.UserCreatesClaim.1: trim(title) != ""
        @Test
        void rejectsBlankTitle() {
            assertRejected(() -> service.createClaim(alice, "   "));
            assertRejected(() -> service.createClaim(alice, ""));
        }

        // spec: claim creation is not recorded in the decision log
        @Test
        void leavesNoDecisionLogEntry() {
            long id = service.createClaim(alice, "Trip");
            assertTrue(decisions(id).isEmpty());
        }
    }

    @Nested
    class OwnerSubmitsClaim {

        // rule-success.OwnerSubmitsClaim / rule-entity-creation.OwnerSubmitsClaim.1
        @Test
        void submitsDraftAndLogsSubmission() {
            long id = draftClaim(alice);
            advanceHours(2);
            service.submitClaim(alice, id);

            ExpenseClaimRecord c = claim(id);
            assertEquals("SUBMITTED", c.getStatus());
            assertEquals(now(), c.getSubmittedAt());

            DecisionLogEntryRecord log = lastDecision(id);
            assertEquals("SUBMITTED", log.getAction());
            assertEquals(alice, log.getActorId());
            assertEquals(now(), log.getOccurredAt());
            assertNull(log.getReason());
        }

        // transition-edge rejected -> submitted; when-presence: decided_at and
        // decision_reason cease to exist on resubmission
        @Test
        void resubmissionAfterRejectionClearsDecisionFields() {
            long id = submittedClaim(alice);
            service.rejectClaim(mara, id, "Missing details");
            advanceHours(1);
            service.submitClaim(alice, id);

            ExpenseClaimRecord c = claim(id);
            assertEquals("SUBMITTED", c.getStatus());
            assertEquals(now(), c.getSubmittedAt());
            assertNull(c.getDecidedAt());
            assertNull(c.getDecisionReason());
        }

        // rule-failure.OwnerSubmitsClaim.1: actor = claim.owner
        @Test
        void rejectsSubmissionByNonOwner() {
            long id = draftClaim(alice);
            assertRejected(() -> service.submitClaim(ben, id));
            assertRejected(() -> service.submitClaim(mara, id));
            assertEquals("DRAFT", claim(id).getStatus());
        }

        // rule-failure.OwnerSubmitsClaim.2: status must be draft or rejected
        @Test
        void rejectsSubmissionWhenNotDraftOrRejected() {
            long submitted = submittedClaim(alice);
            assertRejected(() -> service.submitClaim(alice, submitted));

            long approved = approvedClaim(ben, mara);
            assertRejected(() -> service.submitClaim(ben, approved));
        }

        // rule-failure.OwnerSubmitsClaim.3: items.count >= 1
        @Test
        void rejectsSubmissionOfEmptyClaim() {
            long id = service.createClaim(alice, "Empty");
            assertRejected(() -> service.submitClaim(alice, id));
            assertEquals("DRAFT", claim(id).getStatus());
            assertNull(claim(id).getSubmittedAt());
        }

        // rule-failure.OwnerSubmitsClaim.4: every item needs a receipt unless exempt
        @Test
        void rejectsSubmissionWhenReceiptRequiredItemHasNone() {
            long id = service.createClaim(alice, "Trip");
            service.addItem(alice, id, Category.MEALS, "Dinner", new BigDecimal("50.01"),
                    today(), false);
            assertRejected(() -> service.submitClaim(alice, id));
        }

        // derived boundary: requires_receipt is amount > 50.00, not >=
        @Test
        void allowsSubmissionWhenItemAtReceiptThresholdHasNoReceipt() {
            long id = service.createClaim(alice, "Trip");
            service.addItem(alice, id, Category.MEALS, "Lunch", new BigDecimal("50.00"),
                    today(), false);
            service.submitClaim(alice, id);
            assertEquals("SUBMITTED", claim(id).getStatus());
        }
    }

    @Nested
    class OwnerWithdrawsClaim {

        // rule-success.OwnerWithdrawsClaim / rule-entity-creation.OwnerWithdrawsClaim.1
        @Test
        void withdrawsToDraftKeepingSubmittedAtAndLogsWithdrawal() {
            long id = submittedClaim(alice);
            var submittedAt = claim(id).getSubmittedAt();
            advanceHours(3);
            service.withdrawClaim(alice, id);

            ExpenseClaimRecord c = claim(id);
            assertEquals("DRAFT", c.getStatus());
            assertEquals(submittedAt, c.getSubmittedAt(), "submitted_at is retained");

            DecisionLogEntryRecord log = lastDecision(id);
            assertEquals("WITHDRAWN", log.getAction());
            assertEquals(alice, log.getActorId());
            assertEquals(now(), log.getOccurredAt());
        }

        // rule-failure.OwnerWithdrawsClaim.1: actor = claim.owner
        @Test
        void rejectsWithdrawalByNonOwner() {
            long id = submittedClaim(alice);
            assertRejected(() -> service.withdrawClaim(mara, id));
        }

        // rule-failure.OwnerWithdrawsClaim.2: status = submitted
        @Test
        void rejectsWithdrawalWhenNotSubmitted() {
            long draft = draftClaim(alice);
            assertRejected(() -> service.withdrawClaim(alice, draft));

            long approved = approvedClaim(alice, mara);
            assertRejected(() -> service.withdrawClaim(alice, approved));
        }
    }

    @Nested
    class OwnerDeletesClaim {

        // rule-success.OwnerDeletesClaim: claim and its items cease to exist
        @Test
        void deletesNeverSubmittedDraftWithItems() {
            long id = draftClaim(alice);
            service.deleteClaim(alice, id);
            assertNull(claim(id));
            assertTrue(items(id).isEmpty());
        }

        // rule-failure.OwnerDeletesClaim.1: actor = claim.owner
        @Test
        void rejectsDeletionByNonOwner() {
            long id = draftClaim(alice);
            assertRejected(() -> service.deleteClaim(ben, id));
            assertNotNull(claim(id));
        }

        // rule-failure.OwnerDeletesClaim.2: status = draft
        @Test
        void rejectsDeletionOfSubmittedClaim() {
            long id = submittedClaim(alice);
            assertRejected(() -> service.deleteClaim(alice, id));
        }

        // rule-failure.OwnerDeletesClaim.3: not has_been_submitted —
        // a withdrawn claim is draft again but has ever been submitted
        @Test
        void rejectsDeletionOfWithdrawnClaim() {
            long id = submittedClaim(alice);
            service.withdrawClaim(alice, id);
            assertEquals("DRAFT", claim(id).getStatus());
            assertRejected(() -> service.deleteClaim(alice, id));
        }
    }
}
