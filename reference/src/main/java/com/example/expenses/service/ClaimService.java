package com.example.expenses.service;

import com.example.expenses.config.AppLimits;
import com.example.expenses.domain.BusinessRuleException;
import com.example.expenses.domain.Category;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.LogAction;
import com.example.expenses.domain.Role;
import com.example.expenses.jooq.tables.records.AppUserRecord;
import com.example.expenses.jooq.tables.records.ExpenseClaimRecord;
import com.example.expenses.jooq.tables.records.ExpenseItemRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.example.expenses.jooq.Tables.APP_USER;
import static com.example.expenses.jooq.Tables.DECISION_LOG;
import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static com.example.expenses.jooq.Tables.EXPENSE_ITEM;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

@Service
@Transactional
public class ClaimService {

    public record ClaimSummary(long id, String title, String ownerName, ClaimStatus status,
                               BigDecimal total, LocalDateTime submittedAt) {
    }

    public record LogEntry(LogAction action, String actorName, LocalDateTime occurredAt, String reason) {
    }

    private final DSLContext dsl;
    private final AppLimits limits;

    public ClaimService(DSLContext dsl, AppLimits limits) {
        this.dsl = dsl;
        this.limits = limits;
    }

    // ---- Claim lifecycle ----

    public long createClaim(long actorId, String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("A claim needs a title.");
        }
        return dsl.insertInto(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.OWNER_ID, actorId)
                .set(EXPENSE_CLAIM.TITLE, title.trim())
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.DRAFT.name())
                .set(EXPENSE_CLAIM.CREATED_AT, LocalDateTime.now())
                .returning(EXPENSE_CLAIM.ID)
                .fetchOne()
                .getId();
    }

    public void submit(long actorId, long claimId) {
        ExpenseClaimRecord claim = getClaim(claimId);
        requireOwner(actorId, claim, "Only the claim owner can submit it.");
        ClaimStatus status = status(claim);
        if (status != ClaimStatus.DRAFT && status != ClaimStatus.REJECTED) {
            throw new BusinessRuleException("Only draft or rejected claims can be submitted.");
        }
        List<ExpenseItemRecord> items = items(claimId);
        if (items.isEmpty()) {
            throw new BusinessRuleException("A claim needs at least one expense item before it can be submitted.");
        }
        for (ExpenseItemRecord item : items) {
            if (item.getAmount().compareTo(limits.receiptRequiredOver()) > 0 && !item.getHasReceipt()) {
                throw new BusinessRuleException("Items over " + limits.receiptRequiredOver()
                        + " EUR require a receipt: \"" + item.getDescription() + "\".");
            }
        }
        dsl.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.SUBMITTED.name())
                .set(EXPENSE_CLAIM.SUBMITTED_AT, LocalDateTime.now())
                .setNull(EXPENSE_CLAIM.DECIDED_AT)
                .setNull(EXPENSE_CLAIM.DECISION_REASON)
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
        log(claimId, actorId, LogAction.SUBMITTED, null);
    }

    public void withdraw(long actorId, long claimId) {
        ExpenseClaimRecord claim = getClaim(claimId);
        requireOwner(actorId, claim, "Only the claim owner can withdraw it.");
        requireStatus(claim, ClaimStatus.SUBMITTED, "Only submitted claims can be withdrawn.");
        dsl.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.DRAFT.name())
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
        log(claimId, actorId, LogAction.WITHDRAWN, null);
    }

    public void deleteClaim(long actorId, long claimId) {
        ExpenseClaimRecord claim = getClaim(claimId);
        requireOwner(actorId, claim, "Only the claim owner can delete it.");
        requireStatus(claim, ClaimStatus.DRAFT, "Only draft claims can be deleted.");
        if (claim.getSubmittedAt() != null) {
            throw new BusinessRuleException("A claim that has been submitted cannot be deleted.");
        }
        dsl.deleteFrom(EXPENSE_CLAIM).where(EXPENSE_CLAIM.ID.eq(claimId)).execute();
    }

    public void approve(long actorId, long claimId) {
        ExpenseClaimRecord claim = getClaim(claimId);
        AppUserRecord actor = getUser(actorId);
        requireStatus(claim, ClaimStatus.SUBMITTED, "Only submitted claims can be approved.");
        requireApprover(actor, claim);
        BigDecimal total = total(claimId);
        if (total.compareTo(limits.managerApprovalLimit()) > 0 && role(actor) != Role.FINANCE) {
            throw new BusinessRuleException("Claims over " + limits.managerApprovalLimit()
                    + " EUR can only be approved by finance.");
        }
        dsl.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.APPROVED.name())
                .set(EXPENSE_CLAIM.DECIDED_AT, LocalDateTime.now())
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
        log(claimId, actorId, LogAction.APPROVED, null);
    }

    public void reject(long actorId, long claimId, String reason) {
        ExpenseClaimRecord claim = getClaim(claimId);
        AppUserRecord actor = getUser(actorId);
        requireStatus(claim, ClaimStatus.SUBMITTED, "Only submitted claims can be rejected.");
        requireApprover(actor, claim);
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Rejecting a claim requires a reason.");
        }
        dsl.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.REJECTED.name())
                .set(EXPENSE_CLAIM.DECIDED_AT, LocalDateTime.now())
                .set(EXPENSE_CLAIM.DECISION_REASON, reason.trim())
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
        log(claimId, actorId, LogAction.REJECTED, reason.trim());
    }

    public void reimburse(long actorId, long claimId) {
        ExpenseClaimRecord claim = getClaim(claimId);
        AppUserRecord actor = getUser(actorId);
        if (role(actor) != Role.FINANCE) {
            throw new BusinessRuleException("Only finance can reimburse claims.");
        }
        requireStatus(claim, ClaimStatus.APPROVED, "Only approved claims can be reimbursed.");
        dsl.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.REIMBURSED.name())
                .set(EXPENSE_CLAIM.REIMBURSED_AT, LocalDateTime.now())
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
        log(claimId, actorId, LogAction.REIMBURSED, null);
    }

    // ---- Items ----

    public long addItem(long actorId, long claimId, Category category, String description,
                        BigDecimal amount, LocalDate expenseDate, boolean hasReceipt) {
        ExpenseClaimRecord claim = getClaim(claimId);
        requireOwner(actorId, claim, "Only the claim owner can change its items.");
        requireEditable(claim);
        validateItem(category, description, amount, expenseDate);
        return dsl.insertInto(EXPENSE_ITEM)
                .set(EXPENSE_ITEM.CLAIM_ID, claimId)
                .set(EXPENSE_ITEM.CATEGORY, category.name())
                .set(EXPENSE_ITEM.DESCRIPTION, description.trim())
                .set(EXPENSE_ITEM.AMOUNT, amount)
                .set(EXPENSE_ITEM.EXPENSE_DATE, expenseDate)
                .set(EXPENSE_ITEM.HAS_RECEIPT, hasReceipt)
                .returning(EXPENSE_ITEM.ID)
                .fetchOne()
                .getId();
    }

    public void updateItem(long actorId, long itemId, Category category, String description,
                           BigDecimal amount, LocalDate expenseDate, boolean hasReceipt) {
        ExpenseItemRecord item = getItem(itemId);
        ExpenseClaimRecord claim = getClaim(item.getClaimId());
        requireOwner(actorId, claim, "Only the claim owner can change its items.");
        requireEditable(claim);
        validateItem(category, description, amount, expenseDate);
        dsl.update(EXPENSE_ITEM)
                .set(EXPENSE_ITEM.CATEGORY, category.name())
                .set(EXPENSE_ITEM.DESCRIPTION, description.trim())
                .set(EXPENSE_ITEM.AMOUNT, amount)
                .set(EXPENSE_ITEM.EXPENSE_DATE, expenseDate)
                .set(EXPENSE_ITEM.HAS_RECEIPT, hasReceipt)
                .where(EXPENSE_ITEM.ID.eq(itemId))
                .execute();
    }

    public void removeItem(long actorId, long itemId) {
        ExpenseItemRecord item = getItem(itemId);
        ExpenseClaimRecord claim = getClaim(item.getClaimId());
        requireOwner(actorId, claim, "Only the claim owner can change its items.");
        requireEditable(claim);
        dsl.deleteFrom(EXPENSE_ITEM).where(EXPENSE_ITEM.ID.eq(itemId)).execute();
    }

    // ---- Queries ----

    public List<ClaimSummary> visibleClaims(long userId) {
        AppUserRecord user = getUser(userId);
        var ownClaims = EXPENSE_CLAIM.OWNER_ID.eq(userId);
        var condition = switch (role(user)) {
            case EMPLOYEE -> ownClaims;
            case MANAGER -> ownClaims.or(EXPENSE_CLAIM.STATUS.eq(ClaimStatus.SUBMITTED.name()));
            case FINANCE -> ownClaims.or(EXPENSE_CLAIM.STATUS.in(
                    ClaimStatus.SUBMITTED.name(), ClaimStatus.APPROVED.name(), ClaimStatus.REIMBURSED.name()));
        };
        var totalField = coalesce(
                field(select(sum(EXPENSE_ITEM.AMOUNT)).from(EXPENSE_ITEM)
                        .where(EXPENSE_ITEM.CLAIM_ID.eq(EXPENSE_CLAIM.ID))),
                BigDecimal.ZERO).as("total");
        return dsl.select(EXPENSE_CLAIM.ID, EXPENSE_CLAIM.TITLE, APP_USER.NAME, EXPENSE_CLAIM.STATUS,
                        totalField, EXPENSE_CLAIM.SUBMITTED_AT)
                .from(EXPENSE_CLAIM)
                .join(APP_USER).on(APP_USER.ID.eq(EXPENSE_CLAIM.OWNER_ID))
                .where(condition)
                .orderBy(EXPENSE_CLAIM.CREATED_AT.desc())
                .fetch(r -> new ClaimSummary(r.value1(), r.value2(), r.value3(),
                        ClaimStatus.valueOf(r.value4()), r.value5(), r.value6()));
    }

    public BigDecimal total(long claimId) {
        BigDecimal total = dsl.select(sum(EXPENSE_ITEM.AMOUNT))
                .from(EXPENSE_ITEM)
                .where(EXPENSE_ITEM.CLAIM_ID.eq(claimId))
                .fetchOne()
                .value1();
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<LogEntry> decisionLog(long claimId) {
        return dsl.select(DECISION_LOG.ACTION, APP_USER.NAME, DECISION_LOG.OCCURRED_AT, DECISION_LOG.REASON)
                .from(DECISION_LOG)
                .join(APP_USER).on(APP_USER.ID.eq(DECISION_LOG.ACTOR_ID))
                .where(DECISION_LOG.CLAIM_ID.eq(claimId))
                .orderBy(DECISION_LOG.OCCURRED_AT, DECISION_LOG.ID)
                .fetch(r -> new LogEntry(LogAction.valueOf(r.value1()), r.value2(), r.value3(), r.value4()));
    }

    public ExpenseClaimRecord getClaim(long claimId) {
        ExpenseClaimRecord claim = dsl.fetchOne(EXPENSE_CLAIM, EXPENSE_CLAIM.ID.eq(claimId));
        if (claim == null) {
            throw new BusinessRuleException("Claim not found.");
        }
        return claim;
    }

    public List<ExpenseItemRecord> items(long claimId) {
        return dsl.fetch(EXPENSE_ITEM, EXPENSE_ITEM.CLAIM_ID.eq(claimId));
    }

    // ---- UI predicates (service methods above remain authoritative) ----

    public boolean canEditItems(AppUserRecord user, ExpenseClaimRecord claim) {
        ClaimStatus status = status(claim);
        return claim.getOwnerId().equals(user.getId())
                && (status == ClaimStatus.DRAFT || status == ClaimStatus.REJECTED);
    }

    public boolean canSubmit(AppUserRecord user, ExpenseClaimRecord claim) {
        return canEditItems(user, claim);
    }

    public boolean canWithdraw(AppUserRecord user, ExpenseClaimRecord claim) {
        return claim.getOwnerId().equals(user.getId()) && status(claim) == ClaimStatus.SUBMITTED;
    }

    public boolean canDelete(AppUserRecord user, ExpenseClaimRecord claim) {
        return claim.getOwnerId().equals(user.getId())
                && status(claim) == ClaimStatus.DRAFT
                && claim.getSubmittedAt() == null;
    }

    public boolean canDecide(AppUserRecord user, ExpenseClaimRecord claim) {
        return status(claim) == ClaimStatus.SUBMITTED
                && role(user) != Role.EMPLOYEE
                && !claim.getOwnerId().equals(user.getId());
    }

    public boolean canReimburse(AppUserRecord user, ExpenseClaimRecord claim) {
        return status(claim) == ClaimStatus.APPROVED && role(user) == Role.FINANCE;
    }

    // ---- Internals ----

    private void validateItem(Category category, String description, BigDecimal amount, LocalDate expenseDate) {
        if (category == null) {
            throw new BusinessRuleException("An item needs a category.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleException("An item needs a description.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("An item needs an amount greater than zero.");
        }
        if (expenseDate == null) {
            throw new BusinessRuleException("An item needs an expense date.");
        }
        if (expenseDate.isAfter(LocalDate.now())) {
            throw new BusinessRuleException("The expense date cannot be in the future.");
        }
    }

    private void requireOwner(long actorId, ExpenseClaimRecord claim, String message) {
        if (!claim.getOwnerId().equals(actorId)) {
            throw new BusinessRuleException(message);
        }
    }

    private void requireStatus(ExpenseClaimRecord claim, ClaimStatus expected, String message) {
        if (status(claim) != expected) {
            throw new BusinessRuleException(message);
        }
    }

    private void requireEditable(ExpenseClaimRecord claim) {
        ClaimStatus status = status(claim);
        if (status != ClaimStatus.DRAFT && status != ClaimStatus.REJECTED) {
            throw new BusinessRuleException("Items can only be changed while the claim is a draft or rejected.");
        }
    }

    private void requireApprover(AppUserRecord actor, ExpenseClaimRecord claim) {
        if (role(actor) == Role.EMPLOYEE) {
            throw new BusinessRuleException("Only managers or finance can decide on claims.");
        }
        if (claim.getOwnerId().equals(actor.getId())) {
            throw new BusinessRuleException("You cannot decide on your own claim.");
        }
    }

    private void log(long claimId, long actorId, LogAction action, String reason) {
        dsl.insertInto(DECISION_LOG)
                .set(DECISION_LOG.CLAIM_ID, claimId)
                .set(DECISION_LOG.ACTOR_ID, actorId)
                .set(DECISION_LOG.ACTION, action.name())
                .set(DECISION_LOG.OCCURRED_AT, LocalDateTime.now())
                .set(DECISION_LOG.REASON, reason)
                .execute();
    }

    private AppUserRecord getUser(long userId) {
        AppUserRecord user = dsl.fetchOne(APP_USER, APP_USER.ID.eq(userId));
        if (user == null) {
            throw new BusinessRuleException("User not found.");
        }
        return user;
    }

    private static ClaimStatus status(ExpenseClaimRecord claim) {
        return ClaimStatus.valueOf(claim.getStatus());
    }

    private static Role role(AppUserRecord user) {
        return Role.valueOf(user.getRole());
    }

    private ExpenseItemRecord getItem(long itemId) {
        ExpenseItemRecord item = dsl.fetchOne(EXPENSE_ITEM, EXPENSE_ITEM.ID.eq(itemId));
        if (item == null) {
            throw new BusinessRuleException("Item not found.");
        }
        return item;
    }
}
