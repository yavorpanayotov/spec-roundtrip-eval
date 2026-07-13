package com.example.expenses.service;

import com.example.expenses.domain.Category;
import com.example.expenses.domain.ClaimAction;
import com.example.expenses.domain.ClaimDetailView;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ClaimSummary;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated from specs/expense-claims.allium — surfaces ClaimsList and
 * ClaimDetail: context scoping (@guarantee RoleScopedVisibility), exposes
 * fields, and provides/when guards.
 */
class SurfaceTest extends ServiceTestBase {

    private boolean sees(long viewer, long claimId) {
        return service.claimsVisibleTo(viewer).stream().anyMatch(c -> c.id() == claimId);
    }

    @Nested
    class ClaimsListVisibility {

        // surface-actor / context: employees see only their own claims
        @Test
        void employeeSeesOwnClaimsInEveryStatus() {
            long draft = draftClaim(alice);
            long submitted = submittedClaim(alice);
            long approved = approvedClaim(alice, mara);
            long rejected = submittedClaim(alice);
            service.rejectClaim(mara, rejected, "no");
            long reimbursed = approvedClaim(alice, mara);
            service.reimburseClaim(frank, reimbursed);

            for (long id : List.of(draft, submitted, approved, rejected, reimbursed)) {
                assertTrue(sees(alice, id), "owner must see claim " + id);
            }
        }

        @Test
        void employeeDoesNotSeeOthersClaims() {
            long othersSubmitted = submittedClaim(ben);
            long othersDraft = draftClaim(ben);
            assertTrue(!sees(alice, othersSubmitted));
            assertTrue(!sees(alice, othersDraft));
        }

        // managers additionally see all submitted claims
        @Test
        void managerSeesSubmittedButNotOtherStatusesOfOthers() {
            long draft = draftClaim(alice);
            long submitted = submittedClaim(alice);
            long approved = approvedClaim(ben, mara);
            long rejected = submittedClaim(ben);
            service.rejectClaim(mara, rejected, "no");
            long reimbursed = approvedClaim(alice, frank);
            service.reimburseClaim(frank, reimbursed);

            assertTrue(sees(mara, submitted));
            assertTrue(!sees(mara, draft));
            assertTrue(!sees(mara, approved));
            assertTrue(!sees(mara, rejected));
            assertTrue(!sees(mara, reimbursed));
        }

        // finance additionally sees submitted, approved and reimbursed claims
        @Test
        void financeSeesSubmittedApprovedReimbursedButNotDraftsOrRejected() {
            long draft = draftClaim(alice);
            long submitted = submittedClaim(alice);
            long approved = approvedClaim(ben, mara);
            long rejected = submittedClaim(ben);
            service.rejectClaim(mara, rejected, "no");
            long reimbursed = approvedClaim(alice, mara);
            service.reimburseClaim(frank, reimbursed);

            assertTrue(sees(frank, submitted));
            assertTrue(sees(frank, approved));
            assertTrue(sees(frank, reimbursed));
            assertTrue(!sees(frank, draft));
            assertTrue(!sees(frank, rejected));
        }

        // managers and finance still see their own claims like any owner
        @Test
        void approverRolesSeeTheirOwnDrafts() {
            long maraDraft = draftClaim(mara);
            long frankDraft = draftClaim(frank);
            assertTrue(sees(mara, maraDraft));
            assertTrue(sees(frank, frankDraft));
        }

        // surface-exposure.ClaimsList: title, owner.name, status, total, submitted_at
        @Test
        void exposesListedFields() {
            long id = submittedClaim(alice);
            ClaimSummary row = service.claimsVisibleTo(alice).stream()
                    .filter(c -> c.id() == id).findFirst().orElseThrow();
            assertEquals("Trip to Berlin", row.title());
            assertEquals("Alice", row.ownerName());
            assertEquals(ClaimStatus.SUBMITTED, row.status());
            assertAmount("120.00", row.total());
            assertEquals(now(), row.submittedAt());
        }

        // surface-provides.ClaimsList: CreateClaim is available to any viewer
        @Test
        void anyViewerMayCreateAClaim() {
            for (long viewer : List.of(alice, mara, frank)) {
                long id = service.createClaim(viewer, "New claim");
                assertNotNull(claim(id));
            }
        }
    }

    @Nested
    class ClaimDetailExposure {

        // surface-exposure.ClaimDetail incl. for-iterations over items and decisions
        @Test
        void exposesClaimItemsAndDecisions() {
            long id = service.createClaim(alice, "Trip");
            service.addItem(alice, id, Category.TRAVEL, "Flight", new BigDecimal("300.00"),
                    today().minusDays(3), true);
            service.submitClaim(alice, id);
            service.rejectClaim(mara, id, "Wrong project");

            ClaimDetailView detail = service.claimDetail(id);
            assertEquals("Trip", detail.title());
            assertEquals(ClaimStatus.REJECTED, detail.status());
            assertAmount("300.00", detail.total());
            assertEquals("Wrong project", detail.decisionReason());

            assertEquals(1, detail.items().size());
            var item = detail.items().get(0);
            assertEquals(Category.TRAVEL, item.category());
            assertEquals("Flight", item.description());
            assertAmount("300.00", item.amount());
            assertEquals(today().minusDays(3), item.expenseDate());
            assertTrue(item.hasReceipt());

            assertEquals(2, detail.decisions().size());
            var rejection = detail.decisions().get(1);
            assertEquals(com.example.expenses.domain.DecisionAction.REJECTED, rejection.action());
            assertEquals("Mara", rejection.actorName());
            assertNotNull(rejection.occurredAt());
            assertEquals("Wrong project", rejection.reason());
        }

        // decision_reason is exposed only when status = rejected
        @Test
        void decisionReasonAbsentUnlessRejected() {
            long id = submittedClaim(alice);
            assertNull(service.claimDetail(id).decisionReason());
        }

        // parked open question: detail is reachable for any claim by any
        // signed-in user, unlike the role-filtered list — as specified
        @Test
        void detailIsReachableForNonVisibleClaims() {
            long othersDraft = draftClaim(ben);
            assertNotNull(service.claimDetail(othersDraft));
        }
    }

    @Nested
    class ClaimDetailProvides {

        private Set<ClaimAction> actions(long viewer, long claimId) {
            return service.availableActions(viewer, claimId);
        }

        @Test
        void ownerOfFreshDraftMaySubmitDeleteAndManageItems() {
            long id = draftClaim(alice);
            assertEquals(Set.of(ClaimAction.SUBMIT, ClaimAction.DELETE, ClaimAction.MANAGE_ITEMS),
                    actions(alice, id));
            assertEquals(Set.of(), actions(ben, id));
            assertEquals(Set.of(), actions(mara, id));
        }

        // SubmitClaim guard is ownership + editability only; the rule's other
        // preconditions (items, receipts) reject on invocation
        @Test
        void submitIsOfferedOnEmptyDraftButRuleRejectsIt() {
            long id = service.createClaim(alice, "Empty");
            assertTrue(actions(alice, id).contains(ClaimAction.SUBMIT));
            assertRejected(() -> service.submitClaim(alice, id));
        }

        @Test
        void ownerOfWithdrawnDraftMayNotDelete() {
            long id = submittedClaim(alice);
            service.withdrawClaim(alice, id);
            assertEquals(Set.of(ClaimAction.SUBMIT, ClaimAction.MANAGE_ITEMS),
                    actions(alice, id));
        }

        @Test
        void submittedClaimOffersWithdrawToOwnerAndDecisionsToApprovers() {
            long id = submittedClaim(alice);
            assertEquals(Set.of(ClaimAction.WITHDRAW), actions(alice, id));
            assertEquals(Set.of(ClaimAction.APPROVE, ClaimAction.REJECT), actions(mara, id));
            assertEquals(Set.of(ClaimAction.APPROVE, ClaimAction.REJECT), actions(frank, id));
            assertEquals(Set.of(), actions(ben, id), "other employees get nothing");
        }

        // approve/reject guard excludes the owner even for approver roles
        @Test
        void approverSeesNoDecisionActionsOnOwnSubmittedClaim() {
            long id = submittedClaim(mara);
            assertEquals(Set.of(ClaimAction.WITHDRAW), actions(mara, id));
        }

        @Test
        void approvedClaimOffersReimburseToFinanceOnly() {
            long id = approvedClaim(alice, mara);
            assertEquals(Set.of(ClaimAction.REIMBURSE), actions(frank, id));
            assertEquals(Set.of(), actions(mara, id));
            assertEquals(Set.of(), actions(alice, id));
        }

        @Test
        void rejectedClaimIsEditableByOwnerButNotDeletable() {
            long id = submittedClaim(alice);
            service.rejectClaim(mara, id, "no");
            assertEquals(Set.of(ClaimAction.SUBMIT, ClaimAction.MANAGE_ITEMS),
                    actions(alice, id));
        }

        @Test
        void reimbursedClaimOffersNothingToAnyone() {
            long id = approvedClaim(alice, mara);
            service.reimburseClaim(frank, id);
            for (long viewer : List.of(alice, ben, mara, frank)) {
                assertEquals(Set.of(), actions(viewer, id));
            }
        }
    }
}
