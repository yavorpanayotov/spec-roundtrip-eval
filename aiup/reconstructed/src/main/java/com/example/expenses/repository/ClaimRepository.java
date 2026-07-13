package com.example.expenses.repository;

import static com.example.expenses.jooq.Tables.APP_USER;
import static com.example.expenses.jooq.Tables.DECISION_LOG;
import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static com.example.expenses.jooq.Tables.EXPENSE_ITEM;

import com.example.expenses.domain.AppUser;
import com.example.expenses.domain.ClaimAction;
import com.example.expenses.domain.ClaimDetails;
import com.example.expenses.domain.ClaimItem;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ClaimSummary;
import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.domain.HistoryEntry;
import com.example.expenses.domain.ItemData;
import com.example.expenses.domain.Role;
import com.example.expenses.jooq.Sequences;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimRepository {

    /** Claim total as the sum of its item amounts, zero when there are none (BR-010). */
    private static final Field<BigDecimal> TOTAL = DSL
            .field(DSL.select(DSL.coalesce(DSL.sum(EXPENSE_ITEM.AMOUNT), BigDecimal.ZERO))
                    .from(EXPENSE_ITEM)
                    .where(EXPENSE_ITEM.CLAIM_ID.eq(EXPENSE_CLAIM.ID)))
            .as("total");

    private final DSLContext ctx;

    public ClaimRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    /** Minimal state needed for business rule checks. */
    public record ClaimState(long id, long ownerId, ClaimStatus status, LocalDateTime submittedAt) {
    }

    public Optional<ClaimState> findState(long claimId) {
        return ctx
                .select(EXPENSE_CLAIM.ID, EXPENSE_CLAIM.OWNER_ID,
                        EXPENSE_CLAIM.STATUS.convertFrom(ClaimStatus::valueOf),
                        EXPENSE_CLAIM.SUBMITTED_AT)
                .from(EXPENSE_CLAIM)
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .fetchOptional(Records.mapping(ClaimState::new));
    }

    public long insertClaim(long ownerId, String title, LocalDateTime createdAt) {
        long id = ctx.nextval(Sequences.EXPENSE_CLAIM_SEQ);
        ctx.insertInto(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.ID, id)
                .set(EXPENSE_CLAIM.OWNER_ID, ownerId)
                .set(EXPENSE_CLAIM.TITLE, title)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.DRAFT.name())
                .set(EXPENSE_CLAIM.CREATED_AT, createdAt)
                .execute();
        return id;
    }

    public void deleteClaim(long claimId) {
        ctx.deleteFrom(EXPENSE_CLAIM)
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
    }

    public void markSubmitted(long claimId, LocalDateTime submittedAt) {
        ctx.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.SUBMITTED.name())
                .set(EXPENSE_CLAIM.SUBMITTED_AT, submittedAt)
                .setNull(EXPENSE_CLAIM.DECIDED_AT)
                .setNull(EXPENSE_CLAIM.DECISION_REASON)
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
    }

    public void markWithdrawn(long claimId) {
        ctx.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.DRAFT.name())
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
    }

    public void markDecided(long claimId, ClaimStatus status, LocalDateTime decidedAt, String reason) {
        ctx.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, status.name())
                .set(EXPENSE_CLAIM.DECIDED_AT, decidedAt)
                .set(EXPENSE_CLAIM.DECISION_REASON, reason)
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
    }

    public void markReimbursed(long claimId, LocalDateTime reimbursedAt) {
        ctx.update(EXPENSE_CLAIM)
                .set(EXPENSE_CLAIM.STATUS, ClaimStatus.REIMBURSED.name())
                .set(EXPENSE_CLAIM.REIMBURSED_AT, reimbursedAt)
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .execute();
    }

    public long insertItem(long claimId, ItemData data) {
        long id = ctx.nextval(Sequences.EXPENSE_ITEM_SEQ);
        ctx.insertInto(EXPENSE_ITEM)
                .set(EXPENSE_ITEM.ID, id)
                .set(EXPENSE_ITEM.CLAIM_ID, claimId)
                .set(EXPENSE_ITEM.CATEGORY, data.category().name())
                .set(EXPENSE_ITEM.DESCRIPTION, data.description())
                .set(EXPENSE_ITEM.AMOUNT, data.amount())
                .set(EXPENSE_ITEM.EXPENSE_DATE, data.expenseDate())
                .set(EXPENSE_ITEM.HAS_RECEIPT, data.hasReceipt())
                .execute();
        return id;
    }

    public void updateItem(long itemId, ItemData data) {
        ctx.update(EXPENSE_ITEM)
                .set(EXPENSE_ITEM.CATEGORY, data.category().name())
                .set(EXPENSE_ITEM.DESCRIPTION, data.description())
                .set(EXPENSE_ITEM.AMOUNT, data.amount())
                .set(EXPENSE_ITEM.EXPENSE_DATE, data.expenseDate())
                .set(EXPENSE_ITEM.HAS_RECEIPT, data.hasReceipt())
                .where(EXPENSE_ITEM.ID.eq(itemId))
                .execute();
    }

    public void deleteItem(long itemId) {
        ctx.deleteFrom(EXPENSE_ITEM)
                .where(EXPENSE_ITEM.ID.eq(itemId))
                .execute();
    }

    public boolean itemBelongsToClaim(long itemId, long claimId) {
        return ctx.fetchExists(EXPENSE_ITEM,
                EXPENSE_ITEM.ID.eq(itemId).and(EXPENSE_ITEM.CLAIM_ID.eq(claimId)));
    }

    public int itemCount(long claimId) {
        return ctx.fetchCount(EXPENSE_ITEM, EXPENSE_ITEM.CLAIM_ID.eq(claimId));
    }

    /** Items whose amount exceeds the threshold but have no receipt attached (BR-013). */
    public List<ClaimItem> itemsMissingReceipt(long claimId, BigDecimal threshold) {
        return ctx
                .select(EXPENSE_ITEM.ID,
                        EXPENSE_ITEM.CATEGORY.convertFrom(ExpenseCategory::valueOf),
                        EXPENSE_ITEM.DESCRIPTION, EXPENSE_ITEM.AMOUNT,
                        EXPENSE_ITEM.EXPENSE_DATE, EXPENSE_ITEM.HAS_RECEIPT)
                .from(EXPENSE_ITEM)
                .where(EXPENSE_ITEM.CLAIM_ID.eq(claimId))
                .and(EXPENSE_ITEM.AMOUNT.gt(threshold))
                .and(EXPENSE_ITEM.HAS_RECEIPT.isFalse())
                .orderBy(EXPENSE_ITEM.ID)
                .fetch(Records.mapping(ClaimItem::new));
    }

    public BigDecimal total(long claimId) {
        return ctx
                .select(DSL.coalesce(DSL.sum(EXPENSE_ITEM.AMOUNT), BigDecimal.ZERO))
                .from(EXPENSE_ITEM)
                .where(EXPENSE_ITEM.CLAIM_ID.eq(claimId))
                .fetchSingle()
                .value1();
    }

    public void insertLog(long claimId, long actorId, ClaimAction action,
            LocalDateTime occurredAt, String reason) {
        ctx.insertInto(DECISION_LOG)
                .set(DECISION_LOG.ID, ctx.nextval(Sequences.DECISION_LOG_SEQ))
                .set(DECISION_LOG.CLAIM_ID, claimId)
                .set(DECISION_LOG.ACTOR_ID, actorId)
                .set(DECISION_LOG.ACTION, action.name())
                .set(DECISION_LOG.OCCURRED_AT, occurredAt)
                .set(DECISION_LOG.REASON, reason)
                .execute();
    }

    /** Claims visible to the given user per BR-024, newest first. */
    public List<ClaimSummary> listVisibleTo(AppUser user) {
        Condition visible = EXPENSE_CLAIM.OWNER_ID.eq(user.id());
        if (user.role() == Role.MANAGER) {
            visible = visible.or(EXPENSE_CLAIM.STATUS.eq(ClaimStatus.SUBMITTED.name()));
        } else if (user.role() == Role.FINANCE) {
            visible = visible.or(EXPENSE_CLAIM.STATUS.in(
                    ClaimStatus.SUBMITTED.name(),
                    ClaimStatus.APPROVED.name(),
                    ClaimStatus.REIMBURSED.name()));
        }
        return ctx
                .select(EXPENSE_CLAIM.ID, EXPENSE_CLAIM.TITLE, APP_USER.NAME,
                        EXPENSE_CLAIM.STATUS.convertFrom(ClaimStatus::valueOf),
                        TOTAL, EXPENSE_CLAIM.SUBMITTED_AT, EXPENSE_CLAIM.CREATED_AT)
                .from(EXPENSE_CLAIM)
                .join(APP_USER).on(APP_USER.ID.eq(EXPENSE_CLAIM.OWNER_ID))
                .where(visible)
                .orderBy(EXPENSE_CLAIM.CREATED_AT.desc(), EXPENSE_CLAIM.ID.desc())
                .fetch(Records.mapping(ClaimSummary::new));
    }

    public Optional<ClaimDetails> findDetails(long claimId) {
        return ctx
                .select(EXPENSE_CLAIM.ID, EXPENSE_CLAIM.TITLE, EXPENSE_CLAIM.OWNER_ID, APP_USER.NAME,
                        EXPENSE_CLAIM.STATUS.convertFrom(ClaimStatus::valueOf),
                        TOTAL, EXPENSE_CLAIM.CREATED_AT, EXPENSE_CLAIM.SUBMITTED_AT,
                        EXPENSE_CLAIM.DECIDED_AT, EXPENSE_CLAIM.REIMBURSED_AT,
                        EXPENSE_CLAIM.DECISION_REASON)
                .from(EXPENSE_CLAIM)
                .join(APP_USER).on(APP_USER.ID.eq(EXPENSE_CLAIM.OWNER_ID))
                .where(EXPENSE_CLAIM.ID.eq(claimId))
                .fetchOptional(Records.mapping(ClaimDetails::new));
    }

    public List<ClaimItem> items(long claimId) {
        return ctx
                .select(EXPENSE_ITEM.ID,
                        EXPENSE_ITEM.CATEGORY.convertFrom(ExpenseCategory::valueOf),
                        EXPENSE_ITEM.DESCRIPTION, EXPENSE_ITEM.AMOUNT,
                        EXPENSE_ITEM.EXPENSE_DATE, EXPENSE_ITEM.HAS_RECEIPT)
                .from(EXPENSE_ITEM)
                .where(EXPENSE_ITEM.CLAIM_ID.eq(claimId))
                .orderBy(EXPENSE_ITEM.ID)
                .fetch(Records.mapping(ClaimItem::new));
    }

    /** Full audit trail of a claim, oldest first (BR-016). */
    public List<HistoryEntry> history(long claimId) {
        return ctx
                .select(APP_USER.NAME,
                        DECISION_LOG.ACTION.convertFrom(ClaimAction::valueOf),
                        DECISION_LOG.OCCURRED_AT, DECISION_LOG.REASON)
                .from(DECISION_LOG)
                .join(APP_USER).on(APP_USER.ID.eq(DECISION_LOG.ACTOR_ID))
                .where(DECISION_LOG.CLAIM_ID.eq(claimId))
                .orderBy(DECISION_LOG.OCCURRED_AT, DECISION_LOG.ID)
                .fetch(Records.mapping(HistoryEntry::new));
    }
}
