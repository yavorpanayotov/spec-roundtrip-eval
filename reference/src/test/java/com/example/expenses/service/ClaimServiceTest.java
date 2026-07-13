package com.example.expenses.service;

import com.example.expenses.config.AppLimits;
import com.example.expenses.domain.BusinessRuleException;
import com.example.expenses.domain.Category;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.LogAction;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.example.expenses.jooq.Tables.APP_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests, one section per group in ground-truth/behaviour-inventory.md.
 * Runs against a fresh in-memory H2 database migrated by Flyway, no Spring context.
 */
class ClaimServiceTest {

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

    // ---- Claim lifecycle ----

    @Test
    @DisplayName("B01: a new claim starts in draft, owned by its creator, with zero items")
    void newClaimStartsAsDraft() {
        long id = service.createClaim(alice, "Conference trip");
        var claim = service.getClaim(id);
        assertEquals(ClaimStatus.DRAFT.name(), claim.getStatus());
        assertEquals(alice, claim.getOwnerId());
        assertTrue(service.items(id).isEmpty());
        assertNull(claim.getSubmittedAt());
    }

    @Test
    @DisplayName("B02: a draft without items cannot be submitted")
    void submitWithoutItemsBlocked() {
        long id = service.createClaim(alice, "Empty");
        assertThrows(BusinessRuleException.class, () -> service.submit(alice, id));
    }

    @Test
    @DisplayName("B03: submitting moves draft to submitted and records the timestamp")
    void submitMovesToSubmitted() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        var claim = service.getClaim(id);
        assertEquals(ClaimStatus.SUBMITTED.name(), claim.getStatus());
        assertNotNull(claim.getSubmittedAt());
    }

    @Test
    @DisplayName("B04: the owner can withdraw a submitted claim back to draft; others cannot")
    void withdrawRules() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        assertThrows(BusinessRuleException.class, () -> service.withdraw(bob, id));
        service.withdraw(alice, id);
        assertEquals(ClaimStatus.DRAFT.name(), service.getClaim(id).getStatus());
        // only from submitted
        assertThrows(BusinessRuleException.class, () -> service.withdraw(alice, id));
    }

    @Test
    @DisplayName("B05: a never-submitted draft can be deleted; an ever-submitted claim cannot")
    void deleteRules() {
        long fresh = service.createClaim(alice, "Fresh draft");
        service.deleteClaim(alice, fresh);
        assertThrows(BusinessRuleException.class, () -> service.getClaim(fresh));

        long submitted = claimWithItem(alice, "30.00", false);
        service.submit(alice, submitted);
        service.withdraw(alice, submitted); // back in draft, but has been submitted
        assertThrows(BusinessRuleException.class, () -> service.deleteClaim(alice, submitted));
    }

    @Test
    @DisplayName("B06: a rejected claim can be edited by its owner and resubmitted")
    void rejectedCanBeEditedAndResubmitted() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        service.reject(carol, id, "Wrong category");
        service.addItem(alice, id, Category.MEALS, "Dinner", new BigDecimal("20.00"),
                LocalDate.now(), false);
        service.submit(alice, id);
        var claim = service.getClaim(id);
        assertEquals(ClaimStatus.SUBMITTED.name(), claim.getStatus());
        assertNull(claim.getDecisionReason());
    }

    @Test
    @DisplayName("B07: reimbursed is terminal")
    void reimbursedIsTerminal() {
        long id = approvedClaim(alice, "100.00");
        service.reimburse(dave, id);
        assertThrows(BusinessRuleException.class, () -> service.submit(alice, id));
        assertThrows(BusinessRuleException.class, () -> service.withdraw(alice, id));
        assertThrows(BusinessRuleException.class, () -> service.approve(carol, id));
        assertThrows(BusinessRuleException.class, () -> service.reimburse(dave, id));
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, Category.OTHER,
                "Late item", new BigDecimal("5.00"), LocalDate.now(), false));
    }

    // ---- Items and validation ----

    @Test
    @DisplayName("B08: items can only be changed in draft or rejected, and only by the owner")
    void itemEditingRules() {
        long id = claimWithItem(alice, "30.00", false);
        long itemId = service.items(id).get(0).getId();
        assertThrows(BusinessRuleException.class, () -> service.removeItem(bob, itemId));

        service.submit(alice, id);
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, Category.OTHER,
                "Extra", new BigDecimal("5.00"), LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.updateItem(alice, itemId, Category.OTHER,
                "Changed", new BigDecimal("5.00"), LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.removeItem(alice, itemId));

        service.reject(carol, id, "Fix it");
        service.updateItem(alice, itemId, Category.MEALS, "Corrected", new BigDecimal("25.00"),
                LocalDate.now(), false);
    }

    @Test
    @DisplayName("B09: an item requires category, description, positive amount and a date")
    void itemValidation() {
        long id = service.createClaim(alice, "Validation");
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, null,
                "No category", new BigDecimal("10.00"), LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, Category.MEALS,
                "  ", new BigDecimal("10.00"), LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, Category.MEALS,
                "Zero", BigDecimal.ZERO, LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, Category.MEALS,
                "Negative", new BigDecimal("-1.00"), LocalDate.now(), false));
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, Category.MEALS,
                "No date", new BigDecimal("10.00"), null, false));
    }

    @Test
    @DisplayName("B10: the expense date cannot be in the future")
    void futureDateRejected() {
        long id = service.createClaim(alice, "Time traveller");
        assertThrows(BusinessRuleException.class, () -> service.addItem(alice, id, Category.TRAVEL,
                "Tomorrow's taxi", new BigDecimal("10.00"), LocalDate.now().plusDays(1), false));
        service.addItem(alice, id, Category.TRAVEL, "Today's taxi", new BigDecimal("10.00"),
                LocalDate.now(), false);
    }

    @Test
    @DisplayName("B11: items over the receipt threshold need a receipt before submission")
    void receiptRequiredOverThreshold() {
        long over = claimWithItem(alice, "50.01", false);
        assertThrows(BusinessRuleException.class, () -> service.submit(alice, over));

        long exactly = claimWithItem(alice, "50.00", false);
        service.submit(alice, exactly); // exactly the threshold: no receipt needed

        long withReceipt = claimWithItem(alice, "50.01", true);
        service.submit(alice, withReceipt);
    }

    @Test
    @DisplayName("B12: the claim total is the sum of its items")
    void totalIsSumOfItems() {
        long id = service.createClaim(alice, "Totals");
        assertEquals(0, BigDecimal.ZERO.compareTo(service.total(id)));
        service.addItem(alice, id, Category.MEALS, "Lunch", new BigDecimal("12.50"), LocalDate.now(), false);
        service.addItem(alice, id, Category.TRAVEL, "Train", new BigDecimal("30.00"), LocalDate.now(), false);
        assertEquals(0, new BigDecimal("42.50").compareTo(service.total(id)));
    }

    // ---- Approval and authorization ----

    @Test
    @DisplayName("B13: only submitted claims can be approved or rejected")
    void decideOnlySubmitted() {
        long draft = claimWithItem(alice, "30.00", false);
        assertThrows(BusinessRuleException.class, () -> service.approve(carol, draft));
        assertThrows(BusinessRuleException.class, () -> service.reject(carol, draft, "No"));
    }

    @Test
    @DisplayName("B14: employees cannot approve or reject")
    void employeesCannotDecide() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        assertThrows(BusinessRuleException.class, () -> service.approve(bob, id));
        assertThrows(BusinessRuleException.class, () -> service.reject(bob, id, "Nope"));
    }

    @Test
    @DisplayName("B15: nobody can decide on their own claim, regardless of role")
    void noSelfApproval() {
        long managerClaim = claimWithItem(carol, "30.00", false);
        service.submit(carol, managerClaim);
        assertThrows(BusinessRuleException.class, () -> service.approve(carol, managerClaim));
        assertThrows(BusinessRuleException.class, () -> service.reject(carol, managerClaim, "Mine"));
        service.approve(erin, managerClaim); // another manager can

        long financeClaim = claimWithItem(dave, "30.00", false);
        service.submit(dave, financeClaim);
        assertThrows(BusinessRuleException.class, () -> service.approve(dave, financeClaim));
    }

    @Test
    @DisplayName("B16: managers approve up to the limit; above it only finance")
    void managerApprovalLimit() {
        long atLimit = claimWithItem(alice, "5000.00", true);
        service.submit(alice, atLimit);
        service.approve(carol, atLimit); // exactly the limit is fine for a manager

        long overLimit = claimWithItem(alice, "5000.01", true);
        service.submit(alice, overLimit);
        assertThrows(BusinessRuleException.class, () -> service.approve(carol, overLimit));
        service.approve(dave, overLimit); // finance can
    }

    @Test
    @DisplayName("B17: rejection requires a non-empty reason, stored on the claim")
    void rejectionNeedsReason() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        assertThrows(BusinessRuleException.class, () -> service.reject(carol, id, null));
        assertThrows(BusinessRuleException.class, () -> service.reject(carol, id, "   "));
        service.reject(carol, id, "Missing receipt scan");
        assertEquals("Missing receipt scan", service.getClaim(id).getDecisionReason());
    }

    @Test
    @DisplayName("B18: approval records who decided and when")
    void approvalRecordsDecision() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        service.approve(carol, id);
        var claim = service.getClaim(id);
        assertEquals(ClaimStatus.APPROVED.name(), claim.getStatus());
        assertNotNull(claim.getDecidedAt());
        var entries = service.decisionLog(id);
        var approval = entries.get(entries.size() - 1);
        assertEquals(LogAction.APPROVED, approval.action());
        assertEquals("Carol Clark", approval.actorName());
    }

    // ---- Reimbursement ----

    @Test
    @DisplayName("B19: only finance reimburses, and only approved claims")
    void reimbursementRules() {
        long id = approvedClaim(alice, "100.00");
        assertThrows(BusinessRuleException.class, () -> service.reimburse(carol, id));
        assertThrows(BusinessRuleException.class, () -> service.reimburse(alice, id));
        service.reimburse(dave, id);
        var claim = service.getClaim(id);
        assertEquals(ClaimStatus.REIMBURSED.name(), claim.getStatus());
        assertNotNull(claim.getReimbursedAt());

        long submitted = claimWithItem(alice, "20.00", false);
        service.submit(alice, submitted);
        assertThrows(BusinessRuleException.class, () -> service.reimburse(dave, submitted));
    }

    // ---- Visibility ----

    @Test
    @DisplayName("B20: employees see only their own claims")
    void employeeVisibility() {
        long own = claimWithItem(alice, "10.00", false);
        long other = claimWithItem(bob, "10.00", false);
        service.submit(bob, other);
        var visible = service.visibleClaims(alice);
        assertTrue(visible.stream().anyMatch(c -> c.id() == own));
        assertFalse(visible.stream().anyMatch(c -> c.id() == other));
    }

    @Test
    @DisplayName("B21: managers see their own claims plus all submitted claims")
    void managerVisibility() {
        long ownDraft = service.createClaim(carol, "Manager's own draft");
        long submitted = claimWithItem(alice, "10.00", false);
        service.submit(alice, submitted);
        long draft = claimWithItem(bob, "10.00", false);
        long approved = approvedClaim(bob, "10.00");

        var visible = service.visibleClaims(carol);
        assertTrue(visible.stream().anyMatch(c -> c.id() == ownDraft));
        assertTrue(visible.stream().anyMatch(c -> c.id() == submitted));
        assertFalse(visible.stream().anyMatch(c -> c.id() == draft));
        assertFalse(visible.stream().anyMatch(c -> c.id() == approved));
    }

    @Test
    @DisplayName("B22: finance sees submitted, approved and reimbursed claims plus their own")
    void financeVisibility() {
        long ownDraft = service.createClaim(dave, "Finance's own draft");
        long submitted = claimWithItem(alice, "10.00", false);
        service.submit(alice, submitted);
        long approved = approvedClaim(alice, "20.00");
        long reimbursed = approvedClaim(bob, "30.00");
        service.reimburse(dave, reimbursed);
        long draft = claimWithItem(bob, "10.00", false);
        long rejected = claimWithItem(bob, "15.00", false);
        service.submit(bob, rejected);
        service.reject(carol, rejected, "No");

        var visible = service.visibleClaims(dave);
        assertTrue(visible.stream().anyMatch(c -> c.id() == ownDraft));
        assertTrue(visible.stream().anyMatch(c -> c.id() == submitted));
        assertTrue(visible.stream().anyMatch(c -> c.id() == approved));
        assertTrue(visible.stream().anyMatch(c -> c.id() == reimbursed));
        assertFalse(visible.stream().anyMatch(c -> c.id() == draft));
        assertFalse(visible.stream().anyMatch(c -> c.id() == rejected));
    }

    // ---- Audit ----

    @Test
    @DisplayName("B23: every state transition appends a log entry with actor and timestamp")
    void fullLifecycleIsLogged() {
        long id = claimWithItem(alice, "30.00", false);
        service.submit(alice, id);
        service.withdraw(alice, id);
        service.submit(alice, id);
        service.reject(carol, id, "Add the hotel invoice");
        service.submit(alice, id);
        service.approve(erin, id);
        service.reimburse(dave, id);

        List<ClaimService.LogEntry> log = service.decisionLog(id);
        assertEquals(List.of(LogAction.SUBMITTED, LogAction.WITHDRAWN, LogAction.SUBMITTED,
                        LogAction.REJECTED, LogAction.SUBMITTED, LogAction.APPROVED, LogAction.REIMBURSED),
                log.stream().map(ClaimService.LogEntry::action).toList());
        assertTrue(log.stream().allMatch(e -> e.occurredAt() != null && e.actorName() != null));
        assertEquals("Add the hotel invoice",
                log.stream().filter(e -> e.action() == LogAction.REJECTED).findFirst().orElseThrow().reason());
    }

    // ---- Helpers ----

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

    private long userId(String email) {
        return dsl.fetchOne(APP_USER, APP_USER.EMAIL.eq(email)).getId();
    }
}
