package com.example.expenses.service;

import com.example.expenses.domain.Category;
import com.example.expenses.jooq.tables.records.DecisionLogEntryRecord;
import com.example.expenses.jooq.tables.records.ExpenseClaimRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Generated from specs/expense-claims.allium — rules ApproverApprovesClaim,
 * ApproverRejectsClaim, FinanceReimbursesClaim.
 */
class DecisionTest extends ServiceTestBase {

    private long submittedClaimTotalling(long owner, String amount) {
        long id = service.createClaim(owner, "Big claim");
        service.addItem(owner, id, Category.EQUIPMENT, "Hardware", new BigDecimal(amount),
                today(), true);
        service.submitClaim(owner, id);
        return id;
    }

    @Nested
    class ApproverApprovesClaim {

        // rule-success.ApproverApprovesClaim / rule-entity-creation.ApproverApprovesClaim.1
        @Test
        void managerApprovesAndDecisionIsLogged() {
            long id = submittedClaim(alice);
            advanceHours(1);
            service.approveClaim(mara, id);

            ExpenseClaimRecord c = claim(id);
            assertEquals("APPROVED", c.getStatus());
            assertEquals(now(), c.getDecidedAt());

            DecisionLogEntryRecord log = lastDecision(id);
            assertEquals("APPROVED", log.getAction());
            assertEquals(mara, log.getActorId());
            assertEquals(now(), log.getOccurredAt());
            assertNull(log.getReason());
        }

        // rule-failure.ApproverApprovesClaim.1: status = submitted
        @Test
        void rejectsApprovalWhenNotSubmitted() {
            long draft = draftClaim(alice);
            assertRejected(() -> service.approveClaim(mara, draft));

            long approved = approvedClaim(alice, mara);
            assertRejected(() -> service.approveClaim(frank, approved));
        }

        // rule-failure.ApproverApprovesClaim.2: actor.role != employee
        @Test
        void rejectsApprovalByEmployee() {
            long id = submittedClaim(alice);
            assertRejected(() -> service.approveClaim(ben, id));
        }

        // rule-failure.ApproverApprovesClaim.3: actor != claim.owner
        @Test
        void rejectsApprovalOfOwnClaim() {
            long id = submittedClaim(mara);
            assertRejected(() -> service.approveClaim(mara, id));
        }

        // rule-failure.ApproverApprovesClaim.4: total <= limit or finance
        @Test
        void rejectsManagerApprovalAboveLimit() {
            long id = submittedClaimTotalling(alice, "5000.01");
            assertRejected(() -> service.approveClaim(mara, id));
        }

        // boundary: exactly at the limit a manager may approve
        @Test
        void allowsManagerApprovalAtLimit() {
            long id = submittedClaimTotalling(alice, "5000.00");
            service.approveClaim(mara, id);
            assertEquals("APPROVED", claim(id).getStatus());
        }

        // config.manager_approval_limit gates managers, not finance
        @Test
        void allowsFinanceApprovalAboveLimit() {
            long id = submittedClaimTotalling(alice, "5000.01");
            service.approveClaim(frank, id);
            assertEquals("APPROVED", claim(id).getStatus());
        }
    }

    @Nested
    class ApproverRejectsClaim {

        // rule-success.ApproverRejectsClaim / rule-entity-creation.ApproverRejectsClaim.1
        @Test
        void rejectsWithTrimmedReasonAndLogsIt() {
            long id = submittedClaim(alice);
            advanceHours(1);
            service.rejectClaim(mara, id, "  No receipts attached  ");

            ExpenseClaimRecord c = claim(id);
            assertEquals("REJECTED", c.getStatus());
            assertEquals(now(), c.getDecidedAt());
            assertEquals("No receipts attached", c.getDecisionReason());

            DecisionLogEntryRecord log = lastDecision(id);
            assertEquals("REJECTED", log.getAction());
            assertEquals(mara, log.getActorId());
            assertEquals(now(), log.getOccurredAt());
            assertEquals("No receipts attached", log.getReason());
        }

        // rule-failure.ApproverRejectsClaim.1: status = submitted
        @Test
        void rejectsRejectionWhenNotSubmitted() {
            long draft = draftClaim(alice);
            assertRejected(() -> service.rejectClaim(mara, draft, "reason"));
        }

        // rule-failure.ApproverRejectsClaim.2: actor.role != employee
        @Test
        void rejectsRejectionByEmployee() {
            long id = submittedClaim(alice);
            assertRejected(() -> service.rejectClaim(ben, id, "reason"));
        }

        // rule-failure.ApproverRejectsClaim.3: actor != claim.owner
        @Test
        void rejectsRejectionOfOwnClaim() {
            long id = submittedClaim(frank);
            assertRejected(() -> service.rejectClaim(frank, id, "reason"));
        }

        // rule-failure.ApproverRejectsClaim.4: trim(reason) != ""
        @Test
        void rejectsBlankReason() {
            long id = submittedClaim(alice);
            assertRejected(() -> service.rejectClaim(mara, id, "   "));
            assertEquals("SUBMITTED", claim(id).getStatus());
        }
    }

    @Nested
    class FinanceReimbursesClaim {

        // rule-success.FinanceReimbursesClaim / rule-entity-creation.FinanceReimbursesClaim.1
        @Test
        void financeReimbursesApprovedClaim() {
            long id = approvedClaim(alice, mara);
            advanceHours(1);
            service.reimburseClaim(frank, id);

            ExpenseClaimRecord c = claim(id);
            assertEquals("REIMBURSED", c.getStatus());
            assertEquals(now(), c.getReimbursedAt());

            DecisionLogEntryRecord log = lastDecision(id);
            assertEquals("REIMBURSED", log.getAction());
            assertEquals(frank, log.getActorId());
            assertEquals(now(), log.getOccurredAt());
        }

        // rule-failure.FinanceReimbursesClaim.1: actor.role = finance
        @Test
        void rejectsReimbursementByManagerOrEmployee() {
            long id = approvedClaim(alice, mara);
            assertRejected(() -> service.reimburseClaim(mara, id));
            assertRejected(() -> service.reimburseClaim(alice, id));
        }

        // rule-failure.FinanceReimbursesClaim.2: status = approved
        @Test
        void rejectsReimbursementWhenNotApproved() {
            long submitted = submittedClaim(alice);
            assertRejected(() -> service.reimburseClaim(frank, submitted));
        }

        // spec has no self-decision guard on reimbursement (parked open
        // question): finance may reimburse their own claim once another
        // approver has approved it
        @Test
        void financeMayReimburseOwnApprovedClaim() {
            long id = approvedClaim(frank, mara);
            service.reimburseClaim(frank, id);
            assertEquals("REIMBURSED", claim(id).getStatus());
        }
    }
}
