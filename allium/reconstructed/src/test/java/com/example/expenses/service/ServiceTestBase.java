package com.example.expenses.service;

import com.example.expenses.db.Database;
import com.example.expenses.domain.Category;
import com.example.expenses.domain.Role;
import com.example.expenses.domain.RuleViolationException;
import com.example.expenses.jooq.tables.records.DecisionLogEntryRecord;
import com.example.expenses.jooq.tables.records.ExpenseClaimRecord;
import com.example.expenses.jooq.tables.records.ExpenseItemRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.example.expenses.jooq.Tables.APP_USER;
import static com.example.expenses.jooq.Tables.DECISION_LOG_ENTRY;
import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static com.example.expenses.jooq.Tables.EXPENSE_ITEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh in-memory H2 (migrated by Flyway) and a fixed, controllable clock per
 * test. Users are external reference data, so tests insert them directly;
 * everything else goes through the service under test.
 */
abstract class ServiceTestBase {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 15, 10, 0, 0);

    DSLContext dsl;
    MutableClock clock;
    ExpenseService service;

    long alice;   // employee
    long ben;     // employee
    long mara;    // manager
    long frank;   // finance

    @BeforeEach
    void setUpDatabase() {
        dsl = Database.connect("jdbc:h2:mem:t" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        clock = new MutableClock(T0.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ExpenseService(dsl, clock);
        alice = insertUser("Alice", Role.EMPLOYEE);
        ben = insertUser("Ben", Role.EMPLOYEE);
        mara = insertUser("Mara", Role.MANAGER);
        frank = insertUser("Frank", Role.FINANCE);
    }

    long insertUser(String name, Role role) {
        return dsl.insertInto(APP_USER, APP_USER.NAME, APP_USER.EMAIL, APP_USER.ROLE)
                .values(name, name.toLowerCase() + "@test.example", role.name())
                .returning(APP_USER.ID)
                .fetchOne()
                .get(APP_USER.ID);
    }

    LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    LocalDate today() {
        return now().toLocalDate();
    }

    void advanceHours(int hours) {
        clock.advance(Duration.ofHours(hours));
    }

    // ---- Fixtures built through the service (the only writer of claims) ----

    /** Draft claim with one receipt-backed item, owned by {@code owner}. */
    long draftClaim(long owner) {
        long claim = service.createClaim(owner, "Trip to Berlin");
        service.addItem(owner, claim, Category.TRAVEL, "Flight", new BigDecimal("120.00"),
                today(), true);
        return claim;
    }

    long submittedClaim(long owner) {
        long claim = draftClaim(owner);
        service.submitClaim(owner, claim);
        return claim;
    }

    long approvedClaim(long owner, long approver) {
        long claim = submittedClaim(owner);
        service.approveClaim(approver, claim);
        return claim;
    }

    // ---- State readers ----

    ExpenseClaimRecord claim(long id) {
        return dsl.fetchOne(EXPENSE_CLAIM, EXPENSE_CLAIM.ID.eq(id));
    }

    List<ExpenseItemRecord> items(long claimId) {
        return dsl.fetch(EXPENSE_ITEM, EXPENSE_ITEM.CLAIM_ID.eq(claimId));
    }

    List<DecisionLogEntryRecord> decisions(long claimId) {
        return dsl.selectFrom(DECISION_LOG_ENTRY)
                .where(DECISION_LOG_ENTRY.CLAIM_ID.eq(claimId))
                .orderBy(DECISION_LOG_ENTRY.ID)
                .fetch();
    }

    DecisionLogEntryRecord lastDecision(long claimId) {
        List<DecisionLogEntryRecord> all = decisions(claimId);
        assertTrue(!all.isEmpty(), "expected at least one decision log entry");
        return all.get(all.size() - 1);
    }

    // ---- Assertions ----

    void assertRejected(Executable operation) {
        assertThrows(RuleViolationException.class, operation);
    }

    void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    /** Spec invariants, checked against the whole database. */
    void assertInvariants() {
        // PositiveItemAmounts
        for (ExpenseItemRecord item : dsl.fetch(EXPENSE_ITEM)) {
            assertTrue(item.getAmount().compareTo(BigDecimal.ZERO) > 0,
                    "invariant PositiveItemAmounts violated by item " + item.getId());
        }
        for (ExpenseClaimRecord c : dsl.fetch(EXPENSE_CLAIM)) {
            boolean pastSubmission = List.of("SUBMITTED", "APPROVED", "REIMBURSED")
                    .contains(c.getStatus());
            if (!pastSubmission) {
                continue;
            }
            List<ExpenseItemRecord> claimItems = items(c.getId());
            // SubmittedClaimsHaveItems
            assertTrue(!claimItems.isEmpty(),
                    "invariant SubmittedClaimsHaveItems violated by claim " + c.getId());
            // ReceiptsPresentOnceSubmitted
            for (ExpenseItemRecord item : claimItems) {
                boolean requiresReceipt = item.getAmount()
                        .compareTo(com.example.expenses.domain.AppConfig.RECEIPT_REQUIRED_OVER) > 0;
                assertTrue(item.getHasReceipt() || !requiresReceipt,
                        "invariant ReceiptsPresentOnceSubmitted violated by item " + item.getId());
            }
        }
    }
}
