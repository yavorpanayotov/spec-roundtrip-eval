package com.example.expenses.service;

import com.example.expenses.domain.Category;
import com.example.expenses.jooq.tables.records.ExpenseItemRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated from specs/expense-claims.allium — rules OwnerAddsItem,
 * OwnerUpdatesItem, OwnerRemovesItem.
 */
class ItemRuleTest extends ServiceTestBase {

    @Nested
    class OwnerAddsItem {

        // rule-success.OwnerAddsItem / rule-entity-creation.OwnerAddsItem.1
        @Test
        void addsItemWithTrimmedDescription() {
            long claimId = service.createClaim(alice, "Trip");
            long itemId = service.addItem(alice, claimId, Category.ACCOMMODATION,
                    "  Hotel night  ", new BigDecimal("89.50"), today().minusDays(2), true);

            ExpenseItemRecord item = dsl.fetchOne(
                    com.example.expenses.jooq.Tables.EXPENSE_ITEM,
                    com.example.expenses.jooq.Tables.EXPENSE_ITEM.ID.eq(itemId));
            assertEquals(claimId, item.getClaimId());
            assertEquals("ACCOMMODATION", item.getCategory());
            assertEquals("Hotel night", item.getDescription());
            assertAmount("89.50", item.getAmount());
            assertEquals(today().minusDays(2), item.getExpenseDate());
            assertTrue(item.getHasReceipt());
        }

        // rejected claims are editable again (is_editable = status in {draft, rejected})
        @Test
        void addsItemToRejectedClaim() {
            long claimId = submittedClaim(alice);
            service.rejectClaim(mara, claimId, "Incomplete");
            service.addItem(alice, claimId, Category.OTHER, "Parking",
                    new BigDecimal("12.00"), today(), false);
            assertEquals(2, items(claimId).size());
        }

        // rule-failure.OwnerAddsItem.1: actor = claim.owner
        @Test
        void rejectsAddByNonOwner() {
            long claimId = service.createClaim(alice, "Trip");
            assertRejected(() -> service.addItem(ben, claimId, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today(), false));
        }

        // rule-failure.OwnerAddsItem.2: claim.is_editable
        @Test
        void rejectsAddWhenClaimNotEditable() {
            long submitted = submittedClaim(alice);
            assertRejected(() -> service.addItem(alice, submitted, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today(), false));

            long approved = approvedClaim(alice, mara);
            assertRejected(() -> service.addItem(alice, approved, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today(), false));
        }

        // rule-failure.OwnerAddsItem.3: trim(description) != ""
        @Test
        void rejectsBlankDescription() {
            long claimId = service.createClaim(alice, "Trip");
            assertRejected(() -> service.addItem(alice, claimId, Category.MEALS, "   ",
                    new BigDecimal("10.00"), today(), false));
        }

        // rule-failure.OwnerAddsItem.4: amount > 0
        @Test
        void rejectsNonPositiveAmount() {
            long claimId = service.createClaim(alice, "Trip");
            assertRejected(() -> service.addItem(alice, claimId, Category.MEALS, "Lunch",
                    BigDecimal.ZERO, today(), false));
            assertRejected(() -> service.addItem(alice, claimId, Category.MEALS, "Lunch",
                    new BigDecimal("-5.00"), today(), false));
        }

        // rule-failure.OwnerAddsItem.5: expense_date <= now (whole days)
        @Test
        void rejectsFutureExpenseDate() {
            long claimId = service.createClaim(alice, "Trip");
            assertRejected(() -> service.addItem(alice, claimId, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today().plusDays(1), false));
        }

        // boundary: the guard compares whole days, so today is allowed
        @Test
        void allowsExpenseDatedToday() {
            long claimId = service.createClaim(alice, "Trip");
            service.addItem(alice, claimId, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today(), false);
            assertEquals(1, items(claimId).size());
        }
    }

    @Nested
    class OwnerUpdatesItem {

        private long claimId;
        private long itemId;

        private void fixture() {
            claimId = service.createClaim(alice, "Trip");
            itemId = service.addItem(alice, claimId, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today().minusDays(1), false);
        }

        // rule-success.OwnerUpdatesItem
        @Test
        void updatesAllFieldsWithTrimmedDescription() {
            fixture();
            service.updateItem(alice, itemId, Category.TRAVEL, "  Taxi  ",
                    new BigDecimal("23.40"), today(), true);

            ExpenseItemRecord item = items(claimId).get(0);
            assertEquals("TRAVEL", item.getCategory());
            assertEquals("Taxi", item.getDescription());
            assertAmount("23.40", item.getAmount());
            assertEquals(today(), item.getExpenseDate());
            assertTrue(item.getHasReceipt());
        }

        // rule-failure.OwnerUpdatesItem.1: actor = item.claim.owner
        @Test
        void rejectsUpdateByNonOwner() {
            fixture();
            assertRejected(() -> service.updateItem(mara, itemId, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today(), false));
        }

        // rule-failure.OwnerUpdatesItem.2: item.claim.is_editable
        @Test
        void rejectsUpdateWhenClaimNotEditable() {
            fixture();
            service.updateItem(alice, itemId, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today(), true);
            service.submitClaim(alice, claimId);
            assertRejected(() -> service.updateItem(alice, itemId, Category.MEALS, "Lunch",
                    new BigDecimal("11.00"), today(), true));
        }

        // rule-failure.OwnerUpdatesItem.3: trim(description) != ""
        @Test
        void rejectsBlankDescription() {
            fixture();
            assertRejected(() -> service.updateItem(alice, itemId, Category.MEALS, "  ",
                    new BigDecimal("10.00"), today(), false));
        }

        // rule-failure.OwnerUpdatesItem.4: amount > 0
        @Test
        void rejectsNonPositiveAmount() {
            fixture();
            assertRejected(() -> service.updateItem(alice, itemId, Category.MEALS, "Lunch",
                    BigDecimal.ZERO, today(), false));
        }

        // rule-failure.OwnerUpdatesItem.5: expense_date <= now
        @Test
        void rejectsFutureExpenseDate() {
            fixture();
            assertRejected(() -> service.updateItem(alice, itemId, Category.MEALS, "Lunch",
                    new BigDecimal("10.00"), today().plusDays(1), false));
        }
    }

    @Nested
    class OwnerRemovesItem {

        // rule-success.OwnerRemovesItem: item ceases to exist
        @Test
        void removesItemFromEditableClaim() {
            long claimId = draftClaim(alice);
            long itemId = items(claimId).get(0).getId();
            service.removeItem(alice, itemId);
            assertTrue(items(claimId).isEmpty());
        }

        // rule-failure.OwnerRemovesItem.1: actor = item.claim.owner
        @Test
        void rejectsRemovalByNonOwner() {
            long claimId = draftClaim(alice);
            long itemId = items(claimId).get(0).getId();
            assertRejected(() -> service.removeItem(frank, itemId));
            assertFalse(items(claimId).isEmpty());
        }

        // rule-failure.OwnerRemovesItem.2: item.claim.is_editable
        @Test
        void rejectsRemovalWhenClaimNotEditable() {
            long claimId = submittedClaim(alice);
            long itemId = items(claimId).get(0).getId();
            assertRejected(() -> service.removeItem(alice, itemId));
        }
    }

    // derived.ExpenseItem.requires_receipt boundary via the surface DTO
    @Test
    void requiresReceiptIsStrictlyAboveThreshold() {
        long claimId = service.createClaim(alice, "Trip");
        service.addItem(alice, claimId, Category.MEALS, "At threshold",
                new BigDecimal("50.00"), today(), false);
        service.addItem(alice, claimId, Category.MEALS, "Above threshold",
                new BigDecimal("50.01"), today(), false);

        var detail = service.claimDetail(claimId);
        assertFalse(detail.items().stream()
                .filter(i -> i.description().equals("At threshold"))
                .findFirst().orElseThrow().requiresReceipt());
        assertTrue(detail.items().stream()
                .filter(i -> i.description().equals("Above threshold"))
                .findFirst().orElseThrow().requiresReceipt());
    }

    // spec: item changes leave no audit trail
    @Test
    void itemChangesLeaveNoDecisionLogEntries() {
        long claimId = service.createClaim(alice, "Trip");
        long itemId = service.addItem(alice, claimId, Category.MEALS, "Lunch",
                new BigDecimal("10.00"), today(), false);
        service.updateItem(alice, itemId, Category.MEALS, "Lunch", new BigDecimal("12.00"),
                today(), false);
        service.removeItem(alice, itemId);
        assertTrue(decisions(claimId).isEmpty());
        assertNull(claim(claimId).getSubmittedAt());
    }
}
