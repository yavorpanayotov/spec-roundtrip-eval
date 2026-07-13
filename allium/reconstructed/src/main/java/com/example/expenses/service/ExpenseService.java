package com.example.expenses.service;

import com.example.expenses.domain.AppConfig;
import com.example.expenses.domain.Category;
import com.example.expenses.domain.ClaimAction;
import com.example.expenses.domain.ClaimDetailView;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ClaimSummary;
import com.example.expenses.domain.DecisionAction;
import com.example.expenses.domain.DecisionView;
import com.example.expenses.domain.ItemView;
import com.example.expenses.domain.Role;
import com.example.expenses.domain.RuleViolationException;
import com.example.expenses.domain.User;
import com.example.expenses.jooq.tables.records.AppUserRecord;
import com.example.expenses.jooq.tables.records.ExpenseClaimRecord;
import com.example.expenses.jooq.tables.records.ExpenseItemRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.example.expenses.jooq.Tables.APP_USER;
import static com.example.expenses.jooq.Tables.DECISION_LOG_ENTRY;
import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static com.example.expenses.jooq.Tables.EXPENSE_ITEM;

/**
 * The behavioural core of the application: one method per spec rule, plus the
 * query side of the two surfaces (ClaimsList, ClaimDetail).
 *
 * Every mutator validates the rule's {@code requires} clauses and throws
 * {@link RuleViolationException} when one fails; on success it applies the
 * {@code ensures} clauses atomically.
 */
public class ExpenseService {

    private final DSLContext dsl;
    private final Clock clock;

    public ExpenseService(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    // ---- Users (external reference data; read-only) ----

    public List<User> allUsers() {
        return dsl.selectFrom(APP_USER)
                .orderBy(APP_USER.ID)
                .fetch(r -> new User(r.getId(), r.getName(), r.getEmail(),
                        Role.valueOf(r.getRole())));
    }

    public User user(long userId) {
        AppUserRecord r = fetchUser(dsl, userId);
        return new User(r.getId(), r.getName(), r.getEmail(), Role.valueOf(r.getRole()));
    }

    // ---- Claim lifecycle rules ----

    /** Rule UserCreatesClaim. Returns the new claim's id. */
    public long createClaim(long actorId, String title) {
        require(title != null && !title.trim().isEmpty(), "title must not be blank");
        fetchUser(dsl, actorId);
        return dsl.insertInto(EXPENSE_CLAIM,
                        EXPENSE_CLAIM.OWNER_ID, EXPENSE_CLAIM.TITLE,
                        EXPENSE_CLAIM.STATUS, EXPENSE_CLAIM.CREATED_AT)
                .values(actorId, title.trim(), ClaimStatus.DRAFT.name(), now())
                .returning(EXPENSE_CLAIM.ID)
                .fetchOne()
                .get(EXPENSE_CLAIM.ID);
    }

    /** Rule OwnerSubmitsClaim. */
    public void submitClaim(long actorId, long claimId) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseClaimRecord claim = fetchClaim(tx, claimId);
            require(actorId == claim.getOwnerId(), "only the owner may submit a claim");
            ClaimStatus status = status(claim);
            require(status == ClaimStatus.DRAFT || status == ClaimStatus.REJECTED,
                    "only draft or rejected claims can be submitted");
            List<ExpenseItemRecord> items = itemsOf(tx, claimId);
            require(!items.isEmpty(), "a claim needs at least one item to be submitted");
            require(items.stream().allMatch(i -> i.getHasReceipt() || !requiresReceipt(i)),
                    "every item above " + AppConfig.RECEIPT_REQUIRED_OVER + " needs a receipt");

            LocalDateTime now = now();
            tx.update(EXPENSE_CLAIM)
                    .set(EXPENSE_CLAIM.STATUS, ClaimStatus.SUBMITTED.name())
                    .set(EXPENSE_CLAIM.SUBMITTED_AT, now)
                    // decided_at and decision_reason exist only in the
                    // approved/rejected states
                    .setNull(EXPENSE_CLAIM.DECIDED_AT)
                    .setNull(EXPENSE_CLAIM.DECISION_REASON)
                    .where(EXPENSE_CLAIM.ID.eq(claimId))
                    .execute();
            logDecision(tx, claimId, actorId, DecisionAction.SUBMITTED, now, null);
        });
    }

    /** Rule OwnerWithdrawsClaim. */
    public void withdrawClaim(long actorId, long claimId) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseClaimRecord claim = fetchClaim(tx, claimId);
            require(actorId == claim.getOwnerId(), "only the owner may withdraw a claim");
            require(status(claim) == ClaimStatus.SUBMITTED,
                    "only submitted claims can be withdrawn");

            LocalDateTime now = now();
            // submitted_at is retained: the claim has still "ever been submitted"
            tx.update(EXPENSE_CLAIM)
                    .set(EXPENSE_CLAIM.STATUS, ClaimStatus.DRAFT.name())
                    .where(EXPENSE_CLAIM.ID.eq(claimId))
                    .execute();
            logDecision(tx, claimId, actorId, DecisionAction.WITHDRAWN, now, null);
        });
    }

    /** Rule OwnerDeletesClaim. */
    public void deleteClaim(long actorId, long claimId) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseClaimRecord claim = fetchClaim(tx, claimId);
            require(actorId == claim.getOwnerId(), "only the owner may delete a claim");
            require(status(claim) == ClaimStatus.DRAFT, "only draft claims can be deleted");
            require(claim.getSubmittedAt() == null,
                    "a claim that has ever been submitted cannot be deleted");

            tx.deleteFrom(EXPENSE_ITEM).where(EXPENSE_ITEM.CLAIM_ID.eq(claimId)).execute();
            tx.deleteFrom(EXPENSE_CLAIM).where(EXPENSE_CLAIM.ID.eq(claimId)).execute();
        });
    }

    /** Rule ApproverApprovesClaim. */
    public void approveClaim(long actorId, long claimId) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseClaimRecord claim = fetchClaim(tx, claimId);
            AppUserRecord actor = fetchUser(tx, actorId);
            require(status(claim) == ClaimStatus.SUBMITTED,
                    "only submitted claims can be approved");
            require(role(actor) != Role.EMPLOYEE, "employees cannot approve claims");
            require(actorId != claim.getOwnerId(), "approvers cannot decide on their own claim");
            BigDecimal total = totalOf(tx, claimId);
            require(total.compareTo(AppConfig.MANAGER_APPROVAL_LIMIT) <= 0
                            || role(actor) == Role.FINANCE,
                    "claims above " + AppConfig.MANAGER_APPROVAL_LIMIT + " need finance approval");

            LocalDateTime now = now();
            tx.update(EXPENSE_CLAIM)
                    .set(EXPENSE_CLAIM.STATUS, ClaimStatus.APPROVED.name())
                    .set(EXPENSE_CLAIM.DECIDED_AT, now)
                    .where(EXPENSE_CLAIM.ID.eq(claimId))
                    .execute();
            logDecision(tx, claimId, actorId, DecisionAction.APPROVED, now, null);
        });
    }

    /** Rule ApproverRejectsClaim. */
    public void rejectClaim(long actorId, long claimId, String reason) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseClaimRecord claim = fetchClaim(tx, claimId);
            AppUserRecord actor = fetchUser(tx, actorId);
            require(status(claim) == ClaimStatus.SUBMITTED,
                    "only submitted claims can be rejected");
            require(role(actor) != Role.EMPLOYEE, "employees cannot reject claims");
            require(actorId != claim.getOwnerId(), "approvers cannot decide on their own claim");
            require(reason != null && !reason.trim().isEmpty(),
                    "a rejection needs a non-blank reason");

            LocalDateTime now = now();
            String trimmedReason = reason.trim();
            tx.update(EXPENSE_CLAIM)
                    .set(EXPENSE_CLAIM.STATUS, ClaimStatus.REJECTED.name())
                    .set(EXPENSE_CLAIM.DECIDED_AT, now)
                    .set(EXPENSE_CLAIM.DECISION_REASON, trimmedReason)
                    .where(EXPENSE_CLAIM.ID.eq(claimId))
                    .execute();
            logDecision(tx, claimId, actorId, DecisionAction.REJECTED, now, trimmedReason);
        });
    }

    /** Rule FinanceReimbursesClaim. */
    public void reimburseClaim(long actorId, long claimId) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseClaimRecord claim = fetchClaim(tx, claimId);
            AppUserRecord actor = fetchUser(tx, actorId);
            require(role(actor) == Role.FINANCE, "only finance may reimburse claims");
            require(status(claim) == ClaimStatus.APPROVED,
                    "only approved claims can be reimbursed");

            LocalDateTime now = now();
            tx.update(EXPENSE_CLAIM)
                    .set(EXPENSE_CLAIM.STATUS, ClaimStatus.REIMBURSED.name())
                    .set(EXPENSE_CLAIM.REIMBURSED_AT, now)
                    .where(EXPENSE_CLAIM.ID.eq(claimId))
                    .execute();
            logDecision(tx, claimId, actorId, DecisionAction.REIMBURSED, now, null);
        });
    }

    // ---- Item rules ----

    /** Rule OwnerAddsItem. Returns the new item's id. */
    public long addItem(long actorId, long claimId, Category category, String description,
                        BigDecimal amount, LocalDate expenseDate, boolean hasReceipt) {
        return dsl.transactionResult(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseClaimRecord claim = fetchClaim(tx, claimId);
            requireItemPreconditions(actorId, claim, description, amount, expenseDate);
            return tx.insertInto(EXPENSE_ITEM,
                            EXPENSE_ITEM.CLAIM_ID, EXPENSE_ITEM.CATEGORY,
                            EXPENSE_ITEM.DESCRIPTION, EXPENSE_ITEM.AMOUNT,
                            EXPENSE_ITEM.EXPENSE_DATE, EXPENSE_ITEM.HAS_RECEIPT)
                    .values(claimId, category.name(), description.trim(), amount,
                            expenseDate, hasReceipt)
                    .returning(EXPENSE_ITEM.ID)
                    .fetchOne()
                    .get(EXPENSE_ITEM.ID);
        });
    }

    /** Rule OwnerUpdatesItem. */
    public void updateItem(long actorId, long itemId, Category category, String description,
                           BigDecimal amount, LocalDate expenseDate, boolean hasReceipt) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseItemRecord item = fetchItem(tx, itemId);
            ExpenseClaimRecord claim = fetchClaim(tx, item.getClaimId());
            requireItemPreconditions(actorId, claim, description, amount, expenseDate);
            tx.update(EXPENSE_ITEM)
                    .set(EXPENSE_ITEM.CATEGORY, category.name())
                    .set(EXPENSE_ITEM.DESCRIPTION, description.trim())
                    .set(EXPENSE_ITEM.AMOUNT, amount)
                    .set(EXPENSE_ITEM.EXPENSE_DATE, expenseDate)
                    .set(EXPENSE_ITEM.HAS_RECEIPT, hasReceipt)
                    .where(EXPENSE_ITEM.ID.eq(itemId))
                    .execute();
        });
    }

    /** Rule OwnerRemovesItem. */
    public void removeItem(long actorId, long itemId) {
        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ExpenseItemRecord item = fetchItem(tx, itemId);
            ExpenseClaimRecord claim = fetchClaim(tx, item.getClaimId());
            require(actorId == claim.getOwnerId(), "only the owner may change items");
            require(isEditable(claim), "items can only be changed on draft or rejected claims");
            tx.deleteFrom(EXPENSE_ITEM).where(EXPENSE_ITEM.ID.eq(itemId)).execute();
        });
    }

    private void requireItemPreconditions(long actorId, ExpenseClaimRecord claim,
                                          String description, BigDecimal amount,
                                          LocalDate expenseDate) {
        require(actorId == claim.getOwnerId(), "only the owner may change items");
        require(isEditable(claim), "items can only be changed on draft or rejected claims");
        require(description != null && !description.trim().isEmpty(),
                "description must not be blank");
        require(amount != null && amount.compareTo(BigDecimal.ZERO) > 0,
                "amount must be positive");
        require(expenseDate != null && !expenseDate.isAfter(LocalDate.now(clock)),
                "expense date must not be in the future");
    }

    // ---- Surface queries ----

    /**
     * ClaimsList context: the viewer's own claims; plus all submitted claims
     * for managers; plus all submitted, approved and reimbursed claims for
     * finance.
     */
    public List<ClaimSummary> claimsVisibleTo(long viewerId) {
        AppUserRecord viewer = fetchUser(dsl, viewerId);
        Condition visible = EXPENSE_CLAIM.OWNER_ID.eq(viewerId);
        if (role(viewer) == Role.MANAGER) {
            visible = visible.or(EXPENSE_CLAIM.STATUS.eq(ClaimStatus.SUBMITTED.name()));
        } else if (role(viewer) == Role.FINANCE) {
            visible = visible.or(EXPENSE_CLAIM.STATUS.in(
                    ClaimStatus.SUBMITTED.name(),
                    ClaimStatus.APPROVED.name(),
                    ClaimStatus.REIMBURSED.name()));
        }
        Field<BigDecimal> total = totalField();
        return dsl.select(EXPENSE_CLAIM.ID, EXPENSE_CLAIM.TITLE, APP_USER.NAME,
                        EXPENSE_CLAIM.STATUS, total, EXPENSE_CLAIM.SUBMITTED_AT)
                .from(EXPENSE_CLAIM)
                .join(APP_USER).on(APP_USER.ID.eq(EXPENSE_CLAIM.OWNER_ID))
                .where(visible)
                .orderBy(EXPENSE_CLAIM.ID.desc())
                .fetch(r -> new ClaimSummary(
                        r.get(EXPENSE_CLAIM.ID),
                        r.get(EXPENSE_CLAIM.TITLE),
                        r.get(APP_USER.NAME),
                        ClaimStatus.valueOf(r.get(EXPENSE_CLAIM.STATUS)),
                        r.get(total),
                        r.get(EXPENSE_CLAIM.SUBMITTED_AT)));
    }

    /** ClaimDetail surface: reachable for any claim by any signed-in user (as specified). */
    public ClaimDetailView claimDetail(long claimId) {
        ExpenseClaimRecord claim = fetchClaim(dsl, claimId);
        List<ItemView> items = itemsOf(dsl, claimId).stream()
                .map(i -> new ItemView(i.getId(), Category.valueOf(i.getCategory()),
                        i.getDescription(), i.getAmount(), i.getExpenseDate(),
                        i.getHasReceipt()))
                .toList();
        List<DecisionView> decisions = dsl
                .select(DECISION_LOG_ENTRY.ACTION, APP_USER.NAME,
                        DECISION_LOG_ENTRY.OCCURRED_AT, DECISION_LOG_ENTRY.REASON)
                .from(DECISION_LOG_ENTRY)
                .join(APP_USER).on(APP_USER.ID.eq(DECISION_LOG_ENTRY.ACTOR_ID))
                .where(DECISION_LOG_ENTRY.CLAIM_ID.eq(claimId))
                .orderBy(DECISION_LOG_ENTRY.ID)
                .fetch(r -> new DecisionView(
                        DecisionAction.valueOf(r.get(DECISION_LOG_ENTRY.ACTION)),
                        r.get(APP_USER.NAME),
                        r.get(DECISION_LOG_ENTRY.OCCURRED_AT),
                        r.get(DECISION_LOG_ENTRY.REASON)));
        BigDecimal total = items.stream()
                .map(ItemView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClaimDetailView(claim.getId(), claim.getTitle(), status(claim), total,
                claim.getDecisionReason(), items, decisions);
    }

    /**
     * The {@code provides ... when} guards of the ClaimDetail surface for this
     * viewer, evaluated against current claim state.
     */
    public Set<ClaimAction> availableActions(long viewerId, long claimId) {
        AppUserRecord viewer = fetchUser(dsl, viewerId);
        ExpenseClaimRecord claim = fetchClaim(dsl, claimId);
        ClaimStatus status = status(claim);
        boolean isOwner = viewerId == claim.getOwnerId();

        EnumSet<ClaimAction> actions = EnumSet.noneOf(ClaimAction.class);
        if (isOwner && isEditable(claim)) {
            actions.add(ClaimAction.SUBMIT);
            actions.add(ClaimAction.MANAGE_ITEMS);
        }
        if (isOwner && status == ClaimStatus.SUBMITTED) {
            actions.add(ClaimAction.WITHDRAW);
        }
        if (isOwner && status == ClaimStatus.DRAFT && claim.getSubmittedAt() == null) {
            actions.add(ClaimAction.DELETE);
        }
        if (!isOwner && role(viewer) != Role.EMPLOYEE && status == ClaimStatus.SUBMITTED) {
            actions.add(ClaimAction.APPROVE);
            actions.add(ClaimAction.REJECT);
        }
        if (role(viewer) == Role.FINANCE && status == ClaimStatus.APPROVED) {
            actions.add(ClaimAction.REIMBURSE);
        }
        return actions;
    }

    // ---- Internals ----

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuleViolationException(message);
        }
    }

    private static ClaimStatus status(ExpenseClaimRecord claim) {
        return ClaimStatus.valueOf(claim.getStatus());
    }

    private static Role role(AppUserRecord user) {
        return Role.valueOf(user.getRole());
    }

    private static boolean isEditable(ExpenseClaimRecord claim) {
        ClaimStatus status = status(claim);
        return status == ClaimStatus.DRAFT || status == ClaimStatus.REJECTED;
    }

    private static boolean requiresReceipt(ExpenseItemRecord item) {
        return item.getAmount().compareTo(AppConfig.RECEIPT_REQUIRED_OVER) > 0;
    }

    private static AppUserRecord fetchUser(DSLContext ctx, long id) {
        AppUserRecord user = ctx.fetchOne(APP_USER, APP_USER.ID.eq(id));
        require(user != null, "no such user: " + id);
        return user;
    }

    private static ExpenseClaimRecord fetchClaim(DSLContext ctx, long id) {
        ExpenseClaimRecord claim = ctx.fetchOne(EXPENSE_CLAIM, EXPENSE_CLAIM.ID.eq(id));
        require(claim != null, "no such claim: " + id);
        return claim;
    }

    private static ExpenseItemRecord fetchItem(DSLContext ctx, long id) {
        ExpenseItemRecord item = ctx.fetchOne(EXPENSE_ITEM, EXPENSE_ITEM.ID.eq(id));
        require(item != null, "no such item: " + id);
        return item;
    }

    private static List<ExpenseItemRecord> itemsOf(DSLContext ctx, long claimId) {
        return ctx.selectFrom(EXPENSE_ITEM)
                .where(EXPENSE_ITEM.CLAIM_ID.eq(claimId))
                .orderBy(EXPENSE_ITEM.ID)
                .fetch();
    }

    private static BigDecimal totalOf(DSLContext ctx, long claimId) {
        BigDecimal total = ctx.select(DSL.sum(EXPENSE_ITEM.AMOUNT))
                .from(EXPENSE_ITEM)
                .where(EXPENSE_ITEM.CLAIM_ID.eq(claimId))
                .fetchOne(0, BigDecimal.class);
        return total != null ? total : BigDecimal.ZERO;
    }

    private static Field<BigDecimal> totalField() {
        return DSL.coalesce(
                DSL.field(DSL.select(DSL.sum(EXPENSE_ITEM.AMOUNT))
                        .from(EXPENSE_ITEM)
                        .where(EXPENSE_ITEM.CLAIM_ID.eq(EXPENSE_CLAIM.ID))),
                BigDecimal.ZERO
        ).as("total");
    }

    private static void logDecision(DSLContext ctx, long claimId, long actorId,
                                    DecisionAction action, LocalDateTime occurredAt,
                                    String reason) {
        ctx.insertInto(DECISION_LOG_ENTRY,
                        DECISION_LOG_ENTRY.CLAIM_ID, DECISION_LOG_ENTRY.ACTOR_ID,
                        DECISION_LOG_ENTRY.ACTION, DECISION_LOG_ENTRY.OCCURRED_AT,
                        DECISION_LOG_ENTRY.REASON)
                .values(claimId, actorId, action.name(), occurredAt, reason)
                .execute();
    }
}
