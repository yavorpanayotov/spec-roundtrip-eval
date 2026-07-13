package com.example.expenses.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Generated from specs/expense-claims.allium — transitions block of
 * ExpenseClaim.status. Every declared edge is reachable via its witnessing
 * rule; undeclared transitions are rejected; the terminal state has no exit.
 */
class TransitionGraphTest extends ServiceTestBase {

    // transition-edge.ExpenseClaim.draft.submitted (OwnerSubmitsClaim)
    @Test
    void draftToSubmitted() {
        long id = draftClaim(alice);
        service.submitClaim(alice, id);
        assertEquals("SUBMITTED", claim(id).getStatus());
    }

    // transition-edge.ExpenseClaim.rejected.submitted (OwnerSubmitsClaim)
    @Test
    void rejectedToSubmitted() {
        long id = submittedClaim(alice);
        service.rejectClaim(mara, id, "Fix it");
        service.submitClaim(alice, id);
        assertEquals("SUBMITTED", claim(id).getStatus());
    }

    // transition-edge.ExpenseClaim.submitted.draft (OwnerWithdrawsClaim)
    @Test
    void submittedToDraft() {
        long id = submittedClaim(alice);
        service.withdrawClaim(alice, id);
        assertEquals("DRAFT", claim(id).getStatus());
    }

    // transition-edge.ExpenseClaim.submitted.approved (ApproverApprovesClaim)
    @Test
    void submittedToApproved() {
        long id = submittedClaim(alice);
        service.approveClaim(mara, id);
        assertEquals("APPROVED", claim(id).getStatus());
    }

    // transition-edge.ExpenseClaim.submitted.rejected (ApproverRejectsClaim)
    @Test
    void submittedToRejected() {
        long id = submittedClaim(alice);
        service.rejectClaim(mara, id, "No");
        assertEquals("REJECTED", claim(id).getStatus());
    }

    // transition-edge.ExpenseClaim.approved.reimbursed (FinanceReimbursesClaim)
    @Test
    void approvedToReimbursed() {
        long id = approvedClaim(alice, mara);
        service.reimburseClaim(frank, id);
        assertEquals("REIMBURSED", claim(id).getStatus());
    }

    // transition-rejected.ExpenseClaim.status: undeclared edges are rejected
    @Test
    void undeclaredTransitionsAreRejected() {
        long draft = draftClaim(alice);
        // draft -> approved / rejected / reimbursed: no witnessing rule
        assertRejected(() -> service.approveClaim(mara, draft));
        assertRejected(() -> service.rejectClaim(mara, draft, "reason"));
        assertRejected(() -> service.reimburseClaim(frank, draft));
        assertEquals("DRAFT", claim(draft).getStatus());

        long submitted = submittedClaim(ben);
        // submitted -> reimbursed skips approval
        assertRejected(() -> service.reimburseClaim(frank, submitted));

        long approved = approvedClaim(ben, mara);
        // approved -> draft / submitted / rejected
        assertRejected(() -> service.withdrawClaim(ben, approved));
        assertRejected(() -> service.submitClaim(ben, approved));
        assertRejected(() -> service.rejectClaim(mara, approved, "late"));
        assertEquals("APPROVED", claim(approved).getStatus());
    }

    // transition-terminal.ExpenseClaim.status: reimbursed has no outbound moves
    @Test
    void reimbursedIsTerminal() {
        long id = approvedClaim(alice, mara);
        service.reimburseClaim(frank, id);

        assertRejected(() -> service.submitClaim(alice, id));
        assertRejected(() -> service.withdrawClaim(alice, id));
        assertRejected(() -> service.deleteClaim(alice, id));
        assertRejected(() -> service.approveClaim(mara, id));
        assertRejected(() -> service.rejectClaim(mara, id, "reason"));
        assertRejected(() -> service.reimburseClaim(frank, id));
        assertEquals("REIMBURSED", claim(id).getStatus());
    }
}
