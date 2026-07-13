package com.example.expenses.service;

import com.example.expenses.domain.AppConfig;
import com.example.expenses.domain.Category;
import com.example.expenses.domain.Role;
import com.example.expenses.domain.User;
import com.example.expenses.jooq.tables.records.ExpenseClaimRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated from specs/expense-claims.allium — entity shape, state-dependent
 * field presence, optional fields, derived values, relationships, config.
 */
class EntityStateTest extends ServiceTestBase {

    // config-default.receipt_required_over / config-default.manager_approval_limit
    @Test
    void configDefaultsMatchSpec() {
        assertAmount("50.00", AppConfig.RECEIPT_REQUIRED_OVER);
        assertAmount("5000.00", AppConfig.MANAGER_APPROVAL_LIMIT);
    }

    // entity-fields.User: seeded reference data carries name, email, role
    @Test
    void userFieldsAreExposed() {
        User u = service.user(alice);
        assertEquals(alice, u.id());
        assertEquals("Alice", u.name());
        assertEquals("alice@test.example", u.email());
        assertEquals(Role.EMPLOYEE, u.role());
        assertTrue(service.allUsers().stream().anyMatch(x -> x.id() == frank
                && x.role() == Role.FINANCE));
    }

    // when-presence.ExpenseClaim.decided_at, .decision_reason, .reimbursed_at
    // and entity-optional.ExpenseClaim.submitted_at across the lifecycle
    @Test
    void stateDependentFieldsTrackStatus() {
        long id = draftClaim(alice);
        ExpenseClaimRecord c = claim(id);
        assertNull(c.getSubmittedAt());
        assertNull(c.getDecidedAt());
        assertNull(c.getReimbursedAt());
        assertNull(c.getDecisionReason());

        service.submitClaim(alice, id);
        c = claim(id);
        assertNotNull(c.getSubmittedAt());
        assertNull(c.getDecidedAt());
        assertNull(c.getDecisionReason());

        service.rejectClaim(mara, id, "why");
        c = claim(id);
        assertNotNull(c.getDecidedAt());
        assertEquals("why", c.getDecisionReason());
        assertNull(c.getReimbursedAt());

        service.submitClaim(alice, id);
        service.approveClaim(mara, id);
        c = claim(id);
        assertNotNull(c.getDecidedAt());
        assertNull(c.getDecisionReason(), "decision_reason exists only when rejected");
        assertNull(c.getReimbursedAt());

        service.reimburseClaim(frank, id);
        c = claim(id);
        assertNotNull(c.getDecidedAt());
        assertNotNull(c.getReimbursedAt());
    }

    // derived.ExpenseClaim.has_been_submitted: observable through the delete
    // guard — false on a fresh draft, true forever after first submission
    @Test
    void hasBeenSubmittedPersistsThroughWithdrawal() {
        long neverSubmitted = draftClaim(alice);
        service.deleteClaim(alice, neverSubmitted); // allowed: never submitted
        assertNull(claim(neverSubmitted));

        long onceSubmitted = submittedClaim(alice);
        service.withdrawClaim(alice, onceSubmitted);
        assertNotNull(claim(onceSubmitted).getSubmittedAt());
        assertRejected(() -> service.deleteClaim(alice, onceSubmitted));
    }

    // entity-relationship.ExpenseClaim.items / derived total = sum of amounts
    @Test
    void itemsBelongToTheirClaimAndTotalSumsAmounts() {
        long claimA = service.createClaim(alice, "A");
        long claimB = service.createClaim(alice, "B");
        service.addItem(alice, claimA, Category.MEALS, "Lunch", new BigDecimal("10.50"), today(), false);
        service.addItem(alice, claimA, Category.TRAVEL, "Train", new BigDecimal("31.25"), today(), false);
        service.addItem(alice, claimB, Category.OTHER, "Stamps", new BigDecimal("4.00"), today(), false);

        assertEquals(2, items(claimA).size());
        assertEquals(1, items(claimB).size());
        assertAmount("41.75", service.claimDetail(claimA).total());
        assertAmount("4.00", service.claimDetail(claimB).total());
    }

    // derived total of an itemless claim is zero, not null
    @Test
    void totalOfEmptyClaimIsZero() {
        long id = service.createClaim(alice, "Empty");
        assertAmount("0.00", service.claimDetail(id).total());
    }

    // entity-relationship.ExpenseClaim.decisions: log entries navigate to their claim
    @Test
    void decisionLogIsAppendOnlyPerClaim() {
        long id = submittedClaim(alice);
        service.withdrawClaim(alice, id);
        service.submitClaim(alice, id);
        service.approveClaim(mara, id);
        service.reimburseClaim(frank, id);

        var log = decisions(id);
        assertEquals(5, log.size());
        assertEquals("SUBMITTED", log.get(0).getAction());
        assertEquals("WITHDRAWN", log.get(1).getAction());
        assertEquals("SUBMITTED", log.get(2).getAction());
        assertEquals("APPROVED", log.get(3).getAction());
        assertEquals("REIMBURSED", log.get(4).getAction());
        // entity-optional.DecisionLogEntry.reason: null except for rejections
        assertTrue(log.stream().allMatch(e -> e.getReason() == null));
    }
}
