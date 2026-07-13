package com.example.expenses.service;

import com.example.expenses.config.AppLimits;
import com.example.expenses.domain.BusinessRuleException;
import com.example.expenses.domain.Category;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.LogAction;
import com.example.expenses.jooq.tables.records.AppUserRecord;
import com.example.expenses.jooq.tables.records.ExpenseItemRecord;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;

import static com.example.expenses.jooq.Tables.APP_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Propagated from specs/expense-claims.allium (allium plan: 81 obligations).
 *
 * Fills the obligations not already discharged by {@link ClaimServiceTest} (B01–B23),
 * which covers: all transition edges, rule success paths, approval/rejection/
 * reimbursement authorization, item validation on add, receipt threshold, totals,
 * role-scoped visibility (ClaimsList context predicate) and the audit trail.
 * Obligation IDs from the plan are noted on each test.
 *
 * Not generated (no test seam): surface-actor sign-in redirects — UI layer,
 * needs browser tests.
 */
class ExpenseClaimsSpecTest {

    private Connection connection;
    private DSLContext dsl;
    private ClaimService service;

    private long alice;   // employee
    private long bob;     // employee
    private long carol;   // manager
    private long erin;    // manager
    private long dave;    // finance

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        connection = DriverManager.getConnection(url, "sa", "");
        dsl = DSL.using(connection, SQLDialect.H2);
        service = new ClaimService(dsl, new AppLimits(new BigDecimal("50.00"), new BigDecimal("5000.00")));

        alice = userId("alice@example.com");
        bob = userId("bob@example.com");
        carol = userId("carol@example.com");
        erin = userId("erin@example.com");
        dave = userId("dave@example.com");
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.createStatement().execute("SHUTDOWN");
        connection.close();
    }

    // ---- Config ----

    @Test
    @DisplayName("S01: application config carries the spec's declared defaults"
            + " [config-default.receipt_required_over, config-default.manager_approval_limit]")
    void configDefaultsMatchSpec() throws Exception {
        Properties props = new Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        assertEquals(0, new BigDecimal("50.00")
                .compareTo(new BigDecimal(props.getProperty("app.limits.receipt-required-over"))));
        assertEquals(0, new BigDecimal("5000.00")
                .compareTo(new BigDecimal(props.getProperty("app.limits.manager-approval-limit"))));
    }

    // ---- Rule failures not covered by B01–B23 ----

    @Test
    @DisplayName("S02: a claim cannot be created without a title [rule-failure.UserCreatesClaim.1]")
    void blankTitleRejected() {
        assertThrows(BusinessRuleException.class, () -> service.createClaim(alice, null));
        assertThrows(BusinessRuleException.class, () -> service.createClaim(alice, "   "));
    }

    @Test
    @DisplayName("S03: only the owner can submit a claim [rule-failure.OwnerSubmitsClaim.1]")
    void nonOwnerCannotSubmit() {
        long id = claimWithItem(alice, "30.00", false);
        assertThrows(BusinessRuleException.class, () -> service.submit(bob, id));
        assertThrows(BusinessRuleException.class, () -> service.submit(carol, id));
        assertEquals(ClaimStatus.DRAFT.name(), service.getClaim(id).getStatus());
    }

    @Test
    @DisplayName("S04: only the owner can delete, and only a draft"
            + " [rule-failure.OwnerDeletesClaim.1, rule-failure.OwnerDeletesClaim.2]")
    void deleteGuards() {
        long draft = service.createClaim(alice, "Someone else's draft");
        assertThrows(BusinessRuleException.class, () -> service.deleteClaim(bob, draft));

        long submitted = claimWithItem(alice, "30.00", false);
        service.submit(alice, submitted);
        assertThrows(BusinessRuleException.class, () -> service.deleteClaim(alice, submitted));
    }

    @Test
    @DisplayName("S05: no undeclared transitions out of approved or rejected"
            + " [transition-rejected.ExpenseClaim.status]")
    void undeclaredTransitionsRejected() {
        // approved: only approved -> reimbursed is declared
        long approved = approvedClaim(alice, "100.00");
        assertThrows(BusinessRuleException.class, () -> service.submit(alice, approved));
        assertThrows(BusinessRuleException.class, () -> service.withdraw(alice, approved));
        assertThrows(BusinessRuleException.class, () -> service.approve(erin, approved));
        assertThrows(BusinessRuleException.class, () -> service.reject(erin, approved, "Too late"));

        // rejected: only rejected -> submitted is declared
        long rejected = claimWithItem(alice, "30.00", false);
        service.submit(alice, rejected);
        service.reject(carol, rejected, "Fix it");
        assertThrows(BusinessRuleException.class, () -> service.withdraw(alice, rejected));
        assertThrows(BusinessRuleException.class, () -> service.approve(carol, rejected));
        assertThrows(BusinessRuleException.class, () -> service.reject(carol, rejected, "Again"));
        assertThrows(BusinessRuleException.class, () -> service.reimburse(dave, rejected));

        // draft: reimbursement is not declared
        long draft = claimWithItem(alice, "30.00", false);
        assertThrows(BusinessRuleException.class, () -> service.reimburse(dave, draft));
    }

    // ---- State-dependent field presence ----

    @Test
    @DisplayName("S06: decision fields exist only in their lifecycle states"
            + " [when-presence.ExpenseClaim.decided_at, when-presence.ExpenseClaim.decision_reason,"
            + " when-presence.ExpenseClaim.reimbursed_at]")
    void decisionFieldPresenceFollowsState() {
        long id = claimWithItem(alice, "30.00", false);
        var claim = service.getClaim(id);
        assertNull(claim.getDecidedAt());
        assertNull(claim.getDecisionReason());
        assertNull(claim.getReimbursedAt());

        service.submit(alice, id);
        claim = service.getClaim(id);
        assertNull(claim.getDecidedAt());
        assertNull(claim.getDecisionReason());

        service.reject(carol, id, "Missing receipt");
        claim = service.getClaim(id);
        assertNotNull(claim.getDecidedAt());
        assertEquals("Missing receipt", claim.getDecisionReason());

        // resubmission leaves the rejected state: both decision fields are cleared
        service.submit(alice, id);
        claim = service.getClaim(id);
        assertNull(claim.getDecidedAt());
        assertNull(claim.getDecisionReason());

        service.approve(carol, id);
        claim = service.getClaim(id);
        assertNotNull(claim.getDecidedAt());
        assertNull(claim.getDecisionReason());
        assertNull(claim.getReimbursedAt());

        service.reimburse(dave, id);
        claim = service.getClaim(id);
        assertNotNull(claim.getDecidedAt());
        assertNotNull(claim.getReimbursedAt());
    }

    @Test
    @DisplayName("S07: submitted_at survives withdrawal — the claim has still ever been submitted"
            + " [entity-optional.ExpenseClaim.submitted_at, derived.ExpenseClaim.has_been_submitted]")
    void submittedAtPersistsAfterWithdrawal() {
        long id = claimWithItem(alice, "30.00", false);
        assertNull(service.getClaim(id).getSubmittedAt());
        service.submit(alice, id);
        service.withdraw(alice, id);
        var claim = service.getClaim(id);
        assertEquals(ClaimStatus.DRAFT.name(), claim.getStatus());
        assertNotNull(claim.getSubmittedAt());
    }

    // ---- Items ----

    @Test
    @DisplayName("S08: added and updated items store exactly the given fields"
            + " [entity-fields.ExpenseItem, rule-entity-creation.OwnerAddsItem.1,"
            + " rule-success.OwnerUpdatesItem]")
    void itemFieldsRoundTrip() {
        long id = service.createClaim(alice, "Round trip");
        LocalDate date = LocalDate.now().minusDays(3);
        long itemId = service.addItem(alice, id, Category.ACCOMMODATION, "Hotel", new BigDecimal("120.50"),
                date, true);

        ExpenseItemRecord item = service.items(id).get(0);
        assertEquals(itemId, item.getId());
        assertEquals(Category.ACCOMMODATION.name(), item.getCategory());
        assertEquals("Hotel", item.getDescription());
        assertEquals(0, new BigDecimal("120.50").compareTo(item.getAmount()));
        assertEquals(date, item.getExpenseDate());
        assertTrue(item.getHasReceipt());

        LocalDate newDate = LocalDate.now().minusDays(1);
        service.updateItem(alice, itemId, Category.MEALS, "Dinner", new BigDecimal("45.00"), newDate, false);
        item = service.items(id).get(0);
        assertEquals(Category.MEALS.name(), item.getCategory());
        assertEquals("Dinner", item.getDescription());
        assertEquals(0, new BigDecimal("45.00").compareTo(item.getAmount()));
        assertEquals(newDate, item.getExpenseDate());
        assertFalse(item.getHasReceipt());
    }

    @Test
    @DisplayName("S09: item updates enforce the same guards and validation as adds"
            + " [rule-failure.OwnerUpdatesItem.1–5]")
    void updateItemGuards() {
        long id = claimWithItem(alice, "30.00", false);
        long itemId = service.items(id).get(0).getId();

        assertThrows(BusinessRuleException.class, () -> service.updateItem(bob, itemId,
                Category.MEALS, "Not yours", new BigDecimal("10.00"), LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.updateItem(alice, itemId,
                Category.MEALS, "  ", new BigDecimal("10.00"), LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.updateItem(alice, itemId,
                Category.MEALS, "Zero", BigDecimal.ZERO, LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.updateItem(alice, itemId,
                Category.MEALS, "No date", new BigDecimal("10.00"), null, false));
        assertThrows(BusinessRuleException.class, () -> service.updateItem(alice, itemId,
                Category.MEALS, "Future", new BigDecimal("10.00"), LocalDate.now().plusDays(1), false));
    }

    @Test
    @DisplayName("S10: deleting a claim removes its items [rule OwnerDeletesClaim ensures]")
    void deleteCascadesToItems() {
        long id = service.createClaim(alice, "To be deleted");
        service.addItem(alice, id, Category.OTHER, "Stationery", new BigDecimal("9.99"),
                LocalDate.now(), false);
        long itemId = service.items(id).get(0).getId();
        service.deleteClaim(alice, id);
        assertThrows(BusinessRuleException.class, () -> service.getClaim(id));
        assertTrue(dsl.fetch(com.example.expenses.jooq.Tables.EXPENSE_ITEM,
                com.example.expenses.jooq.Tables.EXPENSE_ITEM.ID.eq(itemId)).isEmpty());
    }

    // ---- Audit trail ----

    @Test
    @DisplayName("S11: only rejections carry a reason in the log [entity-optional.DecisionLogEntry.reason]")
    void onlyRejectionsCarryReason() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        service.reject(carol, id, "Needs receipt");
        service.submit(alice, id);
        service.approve(carol, id);
        service.reimburse(dave, id);

        for (ClaimService.LogEntry entry : service.decisionLog(id)) {
            if (entry.action() == LogAction.REJECTED) {
                assertEquals("Needs receipt", entry.reason());
            } else {
                assertNull(entry.reason());
            }
        }
    }

    // ---- Surface provides guards (ClaimDetail action availability) ----

    @Test
    @DisplayName("S12: submit/withdraw/delete are offered exactly to the owner in the right states"
            + " [surface-provides.ClaimDetail]")
    void ownerActionAvailability() {
        long id = claimWithItem(alice, "30.00", false);
        var owner = user(alice);
        var other = user(bob);

        var draft = service.getClaim(id);
        assertTrue(service.canSubmit(owner, draft));
        assertTrue(service.canEditItems(owner, draft));
        assertTrue(service.canDelete(owner, draft));
        assertFalse(service.canWithdraw(owner, draft));
        assertFalse(service.canSubmit(other, draft));
        assertFalse(service.canEditItems(other, draft));
        assertFalse(service.canDelete(other, draft));

        service.submit(alice, id);
        var submitted = service.getClaim(id);
        assertFalse(service.canSubmit(owner, submitted));
        assertFalse(service.canEditItems(owner, submitted));
        assertFalse(service.canDelete(owner, submitted));
        assertTrue(service.canWithdraw(owner, submitted));
        assertFalse(service.canWithdraw(other, submitted));

        service.withdraw(alice, id);
        var withdrawn = service.getClaim(id);
        assertTrue(service.canSubmit(owner, withdrawn));
        assertFalse(service.canDelete(owner, withdrawn));   // ever-submitted: no delete

        service.submit(alice, id);
        service.reject(carol, id, "Fix");
        var rejected = service.getClaim(id);
        assertTrue(service.canSubmit(owner, rejected));
        assertTrue(service.canEditItems(owner, rejected));
        assertFalse(service.canDelete(owner, rejected));
    }

    @Test
    @DisplayName("S13: approve/reject offered to non-owner approvers on submitted claims only;"
            + " reimburse to finance on approved claims only [surface-provides.ClaimDetail]")
    void deciderActionAvailability() {
        long id = claimWithItem(carol, "30.00", false);
        var ownerManager = user(carol);
        var otherManager = user(erin);
        var employee = user(alice);
        var finance = user(dave);

        var draft = service.getClaim(id);
        assertFalse(service.canDecide(otherManager, draft));

        service.submit(carol, id);
        var submitted = service.getClaim(id);
        assertTrue(service.canDecide(otherManager, submitted));
        assertTrue(service.canDecide(finance, submitted));
        assertFalse(service.canDecide(ownerManager, submitted));   // no self-decision
        assertFalse(service.canDecide(employee, submitted));
        assertFalse(service.canReimburse(finance, submitted));

        service.approve(erin, id);
        var approved = service.getClaim(id);
        assertTrue(service.canReimburse(finance, approved));
        assertFalse(service.canReimburse(otherManager, approved));
        assertFalse(service.canReimburse(employee, approved));
        assertFalse(service.canDecide(otherManager, approved));
    }

    // ---- Invariants (assertion-based; no PBT framework in this project) ----

    @Test
    @DisplayName("S14: invariants hold at every step of a full lifecycle"
            + " [invariant.PositiveItemAmounts, invariant.SubmittedClaimsHaveItems,"
            + " invariant.ReceiptsPresentOnceSubmitted]")
    void invariantsHoldAcrossLifecycle() {
        long id = service.createClaim(alice, "Lifecycle");
        service.addItem(alice, id, Category.TRAVEL, "Flight", new BigDecimal("320.00"), LocalDate.now(), true);
        service.addItem(alice, id, Category.MEALS, "Lunch", new BigDecimal("14.00"), LocalDate.now(), false);
        assertInvariants(id);

        service.submit(alice, id);
        assertInvariants(id);
        service.withdraw(alice, id);
        assertInvariants(id);
        service.submit(alice, id);
        assertInvariants(id);
        service.reject(carol, id, "Split the meals");
        assertInvariants(id);
        service.submit(alice, id);
        assertInvariants(id);
        service.approve(erin, id);
        assertInvariants(id);
        service.reimburse(dave, id);
        assertInvariants(id);
    }

    // ---- Surface exposure (ClaimsList summary fields) ----

    @Test
    @DisplayName("S15: the claims list exposes title, owner name, status, total and submission time"
            + " [surface-exposure.ClaimsList]")
    void claimsListExposure() {
        long id = service.createClaim(alice, "Team offsite");
        service.addItem(alice, id, Category.TRAVEL, "Bus", new BigDecimal("18.00"), LocalDate.now(), false);
        service.submit(alice, id);

        ClaimService.ClaimSummary summary = service.visibleClaims(alice).stream()
                .filter(c -> c.id() == id).findFirst().orElseThrow();
        assertEquals("Team offsite", summary.title());
        assertEquals("Alice Adams", summary.ownerName());
        assertEquals(ClaimStatus.SUBMITTED, summary.status());
        assertEquals(0, new BigDecimal("18.00").compareTo(summary.total()));
        assertNotNull(summary.submittedAt());
    }

    // ---- String normalisation ----

    @Test
    @DisplayName("S16: stored titles, descriptions and rejection reasons are trimmed"
            + " [rule-entity-creation.UserCreatesClaim.1, rule-entity-creation.OwnerAddsItem.1,"
            + " rule-entity-creation.ApproverRejectsClaim.1]")
    void storedStringsAreTrimmed() {
        long id = service.createClaim(alice, "  Conference trip  ");
        assertEquals("Conference trip", service.getClaim(id).getTitle());

        service.addItem(alice, id, Category.TRAVEL, "  Taxi  ", new BigDecimal("12.00"),
                LocalDate.now(), false);
        assertEquals("Taxi", service.items(id).get(0).getDescription());

        service.submit(alice, id);
        service.reject(carol, id, "  Needs itemisation  ");
        assertEquals("Needs itemisation", service.getClaim(id).getDecisionReason());
        var rejection = service.decisionLog(id).stream()
                .filter(e -> e.action() == LogAction.REJECTED).findFirst().orElseThrow();
        assertEquals("Needs itemisation", rejection.reason());
    }

    // ---- Helpers ----

    private void assertInvariants(long claimId) {
        var claim = service.getClaim(claimId);
        var items = service.items(claimId);
        // PositiveItemAmounts
        assertTrue(items.stream().allMatch(i -> i.getAmount().signum() > 0));
        ClaimStatus status = ClaimStatus.valueOf(claim.getStatus());
        boolean pastSubmission = status == ClaimStatus.SUBMITTED
                || status == ClaimStatus.APPROVED || status == ClaimStatus.REIMBURSED;
        if (pastSubmission) {
            // SubmittedClaimsHaveItems
            assertFalse(items.isEmpty());
            // ReceiptsPresentOnceSubmitted
            assertTrue(items.stream().allMatch(i ->
                    i.getHasReceipt() || i.getAmount().compareTo(new BigDecimal("50.00")) <= 0));
        }
    }

    private long claimWithItem(long ownerId, String amount, boolean receipt) {
        long id = service.createClaim(ownerId, "Claim " + UUID.randomUUID());
        service.addItem(ownerId, id, Category.TRAVEL, "Item", new BigDecimal(amount),
                LocalDate.now(), receipt);
        return id;
    }

    private long approvedClaim(long ownerId, String amount) {
        long id = claimWithItem(ownerId, amount, true);
        service.submit(ownerId, id);
        service.approve(carol, id);
        return id;
    }

    private AppUserRecord user(long id) {
        return dsl.fetchOne(APP_USER, APP_USER.ID.eq(id));
    }

    private long userId(String email) {
        return dsl.fetchOne(APP_USER, APP_USER.EMAIL.eq(email)).getId();
    }
}
