package com.example.expenses.service;

import com.example.expenses.domain.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Generated from specs/expense-claims.allium — reachability scenarios walking
 * the transition graph end to end, with the three spec invariants
 * (PositiveItemAmounts, SubmittedClaimsHaveItems, ReceiptsPresentOnceSubmitted)
 * checked after every state-changing step.
 */
class ScenarioInvariantTest extends ServiceTestBase {

    // reachability: draft -> submitted -> approved -> reimbursed
    @Test
    void happyPathLifecycle() {
        long id = service.createClaim(alice, "Client visit");
        assertInvariants();
        service.addItem(alice, id, Category.TRAVEL, "Train", new BigDecimal("75.00"), today(), true);
        service.addItem(alice, id, Category.MEALS, "Lunch", new BigDecimal("18.90"), today(), false);
        assertInvariants();
        service.submitClaim(alice, id);
        assertInvariants();
        service.approveClaim(mara, id);
        assertInvariants();
        service.reimburseClaim(frank, id);
        assertInvariants();
        assertEquals("REIMBURSED", claim(id).getStatus());
        assertAmount("93.90", service.claimDetail(id).total());
        assertEquals(3, decisions(id).size());
    }

    // reachability: draft -> submitted -> rejected -> submitted -> approved -> reimbursed
    @Test
    void rejectionReworkResubmissionLifecycle() {
        long id = service.createClaim(alice, "Offsite");
        service.addItem(alice, id, Category.ACCOMMODATION, "Hotel", new BigDecimal("200.00"), today(), false);
        assertRejected(() -> service.submitClaim(alice, id)); // receipt missing
        assertInvariants();

        long itemId = items(id).get(0).getId();
        service.updateItem(alice, itemId, Category.ACCOMMODATION, "Hotel",
                new BigDecimal("200.00"), today(), true);
        service.submitClaim(alice, id);
        assertInvariants();

        service.rejectClaim(mara, id, "Needs cost centre");
        assertInvariants();

        service.addItem(alice, id, Category.OTHER, "Cost centre fee", new BigDecimal("5.00"), today(), false);
        service.submitClaim(alice, id);
        assertInvariants();
        assertNull(claim(id).getDecisionReason());

        service.approveClaim(frank, id);
        service.reimburseClaim(frank, id);
        assertInvariants();
        assertEquals("REIMBURSED", claim(id).getStatus());
        assertEquals(5, decisions(id).size());
    }

    // reachability: draft -> submitted -> draft (withdrawal) -> submitted -> approved
    @Test
    void withdrawalReworkLifecycle() {
        long id = submittedClaim(alice);
        service.withdrawClaim(alice, id);
        assertInvariants();
        // editable again: owner may change items while in draft
        service.addItem(alice, id, Category.MEALS, "Dinner", new BigDecimal("30.00"), today(), false);
        service.submitClaim(alice, id);
        service.approveClaim(mara, id);
        assertInvariants();
        assertEquals("APPROVED", claim(id).getStatus());
    }

    // order independence: two claims progress independently
    @Test
    void independentClaimsDoNotInterfere() {
        long a = submittedClaim(alice);
        long b = submittedClaim(ben);
        service.approveClaim(mara, b);
        assertEquals("SUBMITTED", claim(a).getStatus());
        assertEquals("APPROVED", claim(b).getStatus());
        service.rejectClaim(mara, a, "duplicate");
        assertEquals("APPROVED", claim(b).getStatus());
        assertInvariants();
    }
}
