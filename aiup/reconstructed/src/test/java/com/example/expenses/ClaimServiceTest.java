package com.example.expenses;

import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.expenses.domain.AppUser;
import com.example.expenses.domain.ClaimDetails;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.domain.ItemData;
import com.example.expenses.service.BusinessRuleException;
import com.example.expenses.service.ClaimService;
import com.example.expenses.service.UserService;
import com.example.expenses.usecase.UseCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Service-level tests for business rules that the UI never exposes
 * (defense in depth against direct calls).
 */
@SpringBootTest
class ClaimServiceTest {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private UserService userService;

    @Autowired
    private DSLContext db;

    @AfterEach
    void cleanup() {
        db.deleteFrom(EXPENSE_CLAIM).execute();
    }

    private AppUser user(String email) {
        return userService.listUsers().stream()
                .filter(user -> user.email().equals(email))
                .findFirst()
                .orElseThrow();
    }

    private AppUser emma() {
        return user("emma.employee@example.com");
    }

    private AppUser erik() {
        return user("erik.employee@example.com");
    }

    private AppUser mona() {
        return user("mona.manager@example.com");
    }

    private AppUser frank() {
        return user("frank.finance@example.com");
    }

    private ItemData item(String amount) {
        return new ItemData(ExpenseCategory.TRAVEL, "Ticket",
                new BigDecimal(amount), LocalDate.now(), true);
    }

    private long submittedClaim(AppUser owner, String amount) {
        long claimId = claimService.createClaim(owner, "Claim of " + owner.name());
        claimService.addItem(owner, claimId, item(amount));
        claimService.submit(owner, claimId);
        return claimId;
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A6: Claim not editable", businessRules = {"BR-005"})
    void only_the_owner_may_change_a_claim() {
        long claimId = claimService.createClaim(emma(), "Emma's claim");

        assertThatThrownBy(() -> claimService.addItem(erik(), claimId, item("10.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only the claim owner may change the claim.");
        assertThatThrownBy(() -> claimService.deleteClaim(erik(), claimId))
                .hasMessage("Only the claim owner may change the claim.");
        assertThatThrownBy(() -> claimService.submit(erik(), claimId))
                .hasMessage("Only the claim owner may change the claim.");
    }

    @Test
    @UseCase(id = "UC-004", scenario = "A4: Reviewer not allowed to decide",
            businessRules = {"BR-018"})
    void employees_cannot_decide() {
        long claimId = submittedClaim(emma(), "20.00");

        assertThatThrownBy(() -> claimService.approve(erik(), claimId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only managers or finance officers may decide on claims.");
        assertThatThrownBy(() -> claimService.reject(erik(), claimId, "reason"))
                .hasMessage("Only managers or finance officers may decide on claims.");
    }

    @Test
    @UseCase(id = "UC-004", scenario = "A4: Reviewer not allowed to decide",
            businessRules = {"BR-019"})
    void nobody_may_decide_on_their_own_claim() {
        long claimId = submittedClaim(mona(), "20.00");

        assertThatThrownBy(() -> claimService.approve(mona(), claimId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You cannot decide on your own claim.");

        // another user with sufficient authority may decide
        claimService.approve(frank(), claimId);
        assertThat(status(claimId)).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    @UseCase(id = "UC-004", businessRules = {"BR-017"})
    void only_submitted_claims_can_be_decided() {
        long claimId = claimService.createClaim(emma(), "Draft");

        assertThatThrownBy(() -> claimService.approve(mona(), claimId))
                .hasMessage("Only submitted claims can be approved or rejected.");
        assertThatThrownBy(() -> claimService.reject(mona(), claimId, "reason"))
                .hasMessage("Only submitted claims can be approved or rejected.");
    }

    @Test
    @UseCase(id = "UC-003", scenario = "A3: Claim not submittable", businessRules = {"BR-011"})
    void approved_claim_cannot_be_resubmitted() {
        long claimId = submittedClaim(emma(), "20.00");
        claimService.approve(mona(), claimId);

        assertThatThrownBy(() -> claimService.submit(emma(), claimId))
                .hasMessage("Only claims in Draft or Rejected status can be submitted.");
    }

    @Test
    @UseCase(id = "UC-003", scenario = "A4: Withdraw a submitted claim",
            businessRules = {"BR-015"})
    void only_submitted_claims_can_be_withdrawn() {
        long claimId = claimService.createClaim(emma(), "Draft");

        assertThatThrownBy(() -> claimService.withdraw(emma(), claimId))
                .hasMessage("Only submitted claims can be withdrawn.");
    }

    @Test
    @UseCase(id = "UC-003", businessRules = {"BR-014"})
    void resubmission_clears_the_previous_decision() {
        long claimId = submittedClaim(emma(), "20.00");
        claimService.reject(mona(), claimId, "Wrong cost centre");

        ClaimDetails rejected = claimService.findClaim(claimId).orElseThrow();
        assertThat(rejected.decisionReason()).isEqualTo("Wrong cost centre");
        assertThat(rejected.decidedAt()).isNotNull();

        claimService.submit(emma(), claimId);

        ClaimDetails resubmitted = claimService.findClaim(claimId).orElseThrow();
        assertThat(resubmitted.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(resubmitted.decisionReason()).isNull();
        assertThat(resubmitted.decidedAt()).isNull();
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A5: Delete a draft claim", businessRules = {"BR-009"})
    void withdrawn_claim_cannot_be_deleted() {
        long claimId = submittedClaim(emma(), "20.00");
        claimService.withdraw(emma(), claimId);

        assertThatThrownBy(() -> claimService.deleteClaim(emma(), claimId))
                .hasMessage("Only draft claims that have never been submitted can be deleted.");
    }

    @Test
    @UseCase(id = "UC-005", scenario = "A1: Actor is not a Finance Officer",
            businessRules = {"BR-022"})
    void only_finance_may_reimburse() {
        long claimId = submittedClaim(emma(), "20.00");
        claimService.approve(mona(), claimId);

        assertThatThrownBy(() -> claimService.reimburse(mona(), claimId))
                .hasMessage("Only finance officers may reimburse claims.");
        assertThatThrownBy(() -> claimService.reimburse(emma(), claimId))
                .hasMessage("Only finance officers may reimburse claims.");
    }

    @Test
    @UseCase(id = "UC-005", scenario = "A2: Claim not approved", businessRules = {"BR-022"})
    void only_approved_claims_can_be_reimbursed() {
        long claimId = submittedClaim(emma(), "20.00");

        assertThatThrownBy(() -> claimService.reimburse(frank(), claimId))
                .hasMessage("Only approved claims can be reimbursed.");
    }

    @Test
    @UseCase(id = "UC-005", businessRules = {"BR-023"})
    void reimbursed_claim_is_terminal() {
        long claimId = submittedClaim(emma(), "20.00");
        claimService.approve(mona(), claimId);
        claimService.reimburse(frank(), claimId);

        assertThatThrownBy(() -> claimService.submit(emma(), claimId))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> claimService.withdraw(emma(), claimId))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> claimService.approve(mona(), claimId))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> claimService.reject(mona(), claimId, "reason"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> claimService.reimburse(frank(), claimId))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> claimService.addItem(emma(), claimId, item("10.00")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(status(claimId)).isEqualTo(ClaimStatus.REIMBURSED);
    }

    @Test
    @UseCase(id = "UC-003", businessRules = {"BR-016"})
    void every_transition_is_recorded_in_the_history() {
        long claimId = submittedClaim(emma(), "20.00");
        claimService.withdraw(emma(), claimId);
        claimService.submit(emma(), claimId);
        claimService.reject(mona(), claimId, "Fix the date");
        claimService.submit(emma(), claimId);
        claimService.approve(frank(), claimId);
        claimService.reimburse(frank(), claimId);

        var history = claimService.history(claimId);
        assertThat(history).extracting(entry -> entry.action().name())
                .containsExactly("SUBMITTED", "WITHDRAWN", "SUBMITTED", "REJECTED",
                        "SUBMITTED", "APPROVED", "REIMBURSED");
        assertThat(history.get(3).reason()).isEqualTo("Fix the date");
        assertThat(history.get(3).actorName()).isEqualTo(mona().name());
    }

    private ClaimStatus status(long claimId) {
        return claimService.findClaim(claimId).orElseThrow().status();
    }
}
