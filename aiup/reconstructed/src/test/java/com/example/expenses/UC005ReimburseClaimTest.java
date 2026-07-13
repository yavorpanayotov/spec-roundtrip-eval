package com.example.expenses;

import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.clearNotifications;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.expectNotifications;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.domain.HistoryEntry;
import com.example.expenses.usecase.UseCase;
import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UC005ReimburseClaimTest extends KaribuTest {

    private long approvedClaim() {
        signIn(emma());
        long claimId = createClaimViaUi("Payout claim");
        addItemViaUi(ExpenseCategory.TRAVEL, "Flight",
                new BigDecimal("300.00"), LocalDate.now(), true);
        _get(Button.class, spec -> spec.withText("Submit")).click();
        signIn(mona());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Approve")).click();
        clearNotifications();
        return claimId;
    }

    @Test
    @UseCase(id = "UC-005", businessRules = {"BR-022", "BR-023", "BR-016"})
    void finance_reimburses_approved_claim() {
        long claimId = approvedClaim();

        signIn(frank());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Reimburse")).click();

        expectNotifications("Claim reimbursed.");
        _get(Span.class, spec -> spec.withText("Reimbursed"));
        Grid<HistoryEntry> history = _get(Grid.class, spec -> spec.withId("history-grid"));
        assertThat(GridKt._size(history)).isEqualTo(3);
        HistoryEntry entry = (HistoryEntry) GridKt._get(history, 2);
        assertThat(entry.actorName()).isEqualTo(frank().name());
        assertThat(entry.action().name()).isEqualTo("REIMBURSED");

        // BR-023: a reimbursed claim is terminal — no further actions are offered
        assertThat(_find(Button.class, spec -> spec.withText("Submit"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Withdraw"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Approve"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Reject"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Reimburse"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Add item"))).isEmpty();
    }

    @Test
    @UseCase(id = "UC-005", scenario = "A1: Actor is not a Finance Officer",
            businessRules = {"BR-022"})
    void manager_sees_no_reimburse_button() {
        long claimId = approvedClaim();

        signIn(mona());
        openClaim(claimId);

        // the approved claim is no longer in the manager's queue at all (BR-024)
        assertThat(_find(Button.class, spec -> spec.withText("Reimburse"))).isEmpty();

        // the owner sees the claim but no reimburse action either
        signIn(emma());
        openClaim(claimId);
        _get(Span.class, spec -> spec.withText("Approved"));
        assertThat(_find(Button.class, spec -> spec.withText("Reimburse"))).isEmpty();
    }

    @Test
    @UseCase(id = "UC-005", scenario = "A2: Claim not approved", businessRules = {"BR-022"})
    void submitted_claim_offers_no_reimburse_button() {
        signIn(emma());
        long claimId = createClaimViaUi("Not yet approved");
        addItemViaUi(ExpenseCategory.MEALS, "Dinner",
                new BigDecimal("20.00"), LocalDate.now(), false);
        _get(Button.class, spec -> spec.withText("Submit")).click();
        clearNotifications();

        signIn(frank());
        openClaim(claimId);

        _get(Span.class, spec -> spec.withText("Submitted"));
        assertThat(_find(Button.class, spec -> spec.withText("Reimburse"))).isEmpty();
    }
}
