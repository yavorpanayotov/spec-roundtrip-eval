package com.example.expenses.service;

import com.example.expenses.config.ExpensesProperties;
import com.example.expenses.domain.AppUser;
import com.example.expenses.domain.ClaimAction;
import com.example.expenses.domain.ClaimDetails;
import com.example.expenses.domain.ClaimItem;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ClaimSummary;
import com.example.expenses.domain.HistoryEntry;
import com.example.expenses.domain.ItemData;
import com.example.expenses.domain.Role;
import com.example.expenses.repository.ClaimRepository;
import com.example.expenses.repository.ClaimRepository.ClaimState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense claim lifecycle and business rules (UC-002 .. UC-006).
 */
@Service
@Transactional
public class ClaimService {

    private final ClaimRepository claims;
    private final ExpensesProperties properties;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ExpensesProperties properties, Clock clock) {
        this.claims = claims;
        this.properties = properties;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- UC-002

    /** Creates a new claim in Draft status owned by the actor (BR-003, BR-004). */
    public long createClaim(AppUser actor, String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("A claim needs a title.");
        }
        return claims.insertClaim(actor.id(), title.strip(), LocalDateTime.now(clock));
    }

    /** Deletes a never-submitted draft claim together with its items (BR-005, BR-009). */
    public void deleteClaim(AppUser actor, long claimId) {
        ClaimState claim = requireClaim(claimId);
        requireOwner(actor, claim);
        if (claim.status() != ClaimStatus.DRAFT || claim.submittedAt() != null) {
            throw new BusinessRuleException(
                    "Only draft claims that have never been submitted can be deleted.");
        }
        claims.deleteClaim(claimId);
    }

    /** Adds an item to a claim (BR-005, BR-006, BR-007, BR-008). */
    public long addItem(AppUser actor, long claimId, ItemData data) {
        requireEditable(actor, requireClaim(claimId));
        validateItem(data);
        return claims.insertItem(claimId, data);
    }

    /** Updates an existing item (BR-005, BR-006, BR-007, BR-008). */
    public void updateItem(AppUser actor, long claimId, long itemId, ItemData data) {
        requireEditable(actor, requireClaim(claimId));
        requireItem(claimId, itemId);
        validateItem(data);
        claims.updateItem(itemId, data);
    }

    /** Removes an item from a claim (BR-005, BR-006). */
    public void removeItem(AppUser actor, long claimId, long itemId) {
        requireEditable(actor, requireClaim(claimId));
        requireItem(claimId, itemId);
        claims.deleteItem(itemId);
    }

    // ---------------------------------------------------------------- UC-003

    /** Submits a claim for review (BR-011 .. BR-014, BR-016). */
    public void submit(AppUser actor, long claimId) {
        ClaimState claim = requireClaim(claimId);
        requireOwner(actor, claim);
        if (claim.status() != ClaimStatus.DRAFT && claim.status() != ClaimStatus.REJECTED) {
            throw new BusinessRuleException(
                    "Only claims in Draft or Rejected status can be submitted.");
        }
        if (claims.itemCount(claimId) == 0) {
            throw new BusinessRuleException(
                    "A claim needs at least one expense item before it can be submitted.");
        }
        List<ClaimItem> missingReceipts =
                claims.itemsMissingReceipt(claimId, properties.receiptThreshold());
        if (!missingReceipts.isEmpty()) {
            ClaimItem item = missingReceipts.get(0);
            throw new BusinessRuleException(
                    "Item '%s' (%s EUR) exceeds the receipt threshold of %s EUR and needs a receipt."
                            .formatted(item.description(), item.amount(),
                                    properties.receiptThreshold()));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        claims.markSubmitted(claimId, now);
        claims.insertLog(claimId, actor.id(), ClaimAction.SUBMITTED, now, null);
    }

    /** Withdraws a submitted claim back to Draft (BR-015, BR-016). */
    public void withdraw(AppUser actor, long claimId) {
        ClaimState claim = requireClaim(claimId);
        requireOwner(actor, claim);
        if (claim.status() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException("Only submitted claims can be withdrawn.");
        }
        claims.markWithdrawn(claimId);
        claims.insertLog(claimId, actor.id(), ClaimAction.WITHDRAWN, LocalDateTime.now(clock), null);
    }

    // ---------------------------------------------------------------- UC-004

    /** Approves a submitted claim (BR-016 .. BR-020). */
    public void approve(AppUser actor, long claimId) {
        ClaimState claim = requireClaim(claimId);
        requireReviewer(actor, claim);
        BigDecimal total = claims.total(claimId);
        if (total.compareTo(properties.managerApprovalLimit()) > 0
                && actor.role() != Role.FINANCE) {
            throw new BusinessRuleException(
                    "Claims over %s EUR can only be approved by finance."
                            .formatted(properties.managerApprovalLimit()));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        claims.markDecided(claimId, ClaimStatus.APPROVED, now, null);
        claims.insertLog(claimId, actor.id(), ClaimAction.APPROVED, now, null);
    }

    /** Rejects a submitted claim with a reason (BR-016 .. BR-019, BR-021). */
    public void reject(AppUser actor, long claimId, String reason) {
        ClaimState claim = requireClaim(claimId);
        requireReviewer(actor, claim);
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Rejecting a claim requires a reason.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        claims.markDecided(claimId, ClaimStatus.REJECTED, now, reason.strip());
        claims.insertLog(claimId, actor.id(), ClaimAction.REJECTED, now, reason.strip());
    }

    // ---------------------------------------------------------------- UC-005

    /** Marks an approved claim as reimbursed (BR-016, BR-022, BR-023). */
    public void reimburse(AppUser actor, long claimId) {
        ClaimState claim = requireClaim(claimId);
        if (actor.role() != Role.FINANCE) {
            throw new BusinessRuleException("Only finance officers may reimburse claims.");
        }
        if (claim.status() != ClaimStatus.APPROVED) {
            throw new BusinessRuleException("Only approved claims can be reimbursed.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        claims.markReimbursed(claimId, now);
        claims.insertLog(claimId, actor.id(), ClaimAction.REIMBURSED, now, null);
    }

    // ---------------------------------------------------------------- UC-006

    /** Claims visible to the user per BR-024, newest first. */
    @Transactional(readOnly = true)
    public List<ClaimSummary> listClaims(AppUser user) {
        return claims.listVisibleTo(user);
    }

    @Transactional(readOnly = true)
    public Optional<ClaimDetails> findClaim(long claimId) {
        return claims.findDetails(claimId);
    }

    /**
     * The claim, if it exists and the user may open its detail page. Owners always may;
     * reviewers (Manager/Finance) may open any claim that has left Draft — the strict list
     * visibility of BR-024 would otherwise hide a claim from the reviewer the moment they
     * decide on it, while UC-004 step 7 and UC-005 step 5 show the decided claim. Other
     * employees never see foreign claims.
     */
    @Transactional(readOnly = true)
    public Optional<ClaimDetails> findClaimFor(AppUser user, long claimId) {
        return claims.findDetails(claimId).filter(claim -> claim.ownerId() == user.id()
                || (user.role() != Role.EMPLOYEE && claim.status() != ClaimStatus.DRAFT));
    }

    @Transactional(readOnly = true)
    public List<ClaimItem> items(long claimId) {
        return claims.items(claimId);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(long claimId) {
        return claims.history(claimId);
    }

    // ---------------------------------------------------------------- guards

    private ClaimState requireClaim(long claimId) {
        return claims.findState(claimId)
                .orElseThrow(() -> new BusinessRuleException("Claim not found."));
    }

    private void requireOwner(AppUser actor, ClaimState claim) {
        if (claim.ownerId() != actor.id()) {
            throw new BusinessRuleException("Only the claim owner may change the claim.");
        }
    }

    private void requireEditable(AppUser actor, ClaimState claim) {
        requireOwner(actor, claim);
        if (claim.status() != ClaimStatus.DRAFT && claim.status() != ClaimStatus.REJECTED) {
            throw new BusinessRuleException(
                    "Items can only be changed while the claim is in Draft or Rejected status.");
        }
    }

    private void requireReviewer(AppUser actor, ClaimState claim) {
        if (claim.status() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException("Only submitted claims can be approved or rejected.");
        }
        if (actor.role() != Role.MANAGER && actor.role() != Role.FINANCE) {
            throw new BusinessRuleException(
                    "Only managers or finance officers may decide on claims.");
        }
        if (claim.ownerId() == actor.id()) {
            throw new BusinessRuleException("You cannot decide on your own claim.");
        }
    }

    private void requireItem(long claimId, long itemId) {
        if (!claims.itemBelongsToClaim(itemId, claimId)) {
            throw new BusinessRuleException("Expense item not found on this claim.");
        }
    }

    private void validateItem(ItemData data) {
        if (data.category() == null) {
            throw new BusinessRuleException("An expense item needs a category.");
        }
        if (data.description() == null || data.description().isBlank()) {
            throw new BusinessRuleException("An expense item needs a description.");
        }
        if (data.amount() == null || data.amount().signum() <= 0) {
            throw new BusinessRuleException("The amount must be greater than zero.");
        }
        if (data.expenseDate() == null) {
            throw new BusinessRuleException("An expense item needs an expense date.");
        }
        if (data.expenseDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("The expense date cannot be in the future.");
        }
    }
}
