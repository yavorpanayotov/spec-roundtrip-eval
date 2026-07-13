package com.example.expenses;

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.clearNotifications;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ClaimSummary;
import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.ui.ClaimDetailView;
import com.example.expenses.ui.ClaimsListView;
import com.example.expenses.usecase.UseCase;
import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class UC006TrackClaimsTest extends KaribuTest {

    /** One draft claim and one submitted claim, both owned by Emma. */
    private void emmaHasDraftAndSubmittedClaims() {
        signIn(emma());
        createClaimViaUi("Draft claim");
        long submitted = createClaimViaUi("Submitted claim");
        addItemViaUi(ExpenseCategory.TRAVEL, "Train",
                new BigDecimal("42.00"), LocalDate.now(), false);
        _get(Button.class, spec -> spec.withText("Submit")).click();
        clearNotifications();
    }

    @Test
    @UseCase(id = "UC-006", businessRules = {"BR-024"})
    void owner_sees_own_claims_newest_first() {
        emmaHasDraftAndSubmittedClaims();

        openClaimsList();
        Grid<ClaimSummary> grid = _get(Grid.class, spec -> spec.withId("claims-grid"));

        assertThat(GridKt._size(grid)).isEqualTo(2);
        ClaimSummary newest = (ClaimSummary) GridKt._get(grid, 0);
        assertThat(newest.title()).isEqualTo("Submitted claim");
        assertThat(newest.total()).isEqualByComparingTo(new BigDecimal("42.00"));
        ClaimSummary older = (ClaimSummary) GridKt._get(grid, 1);
        assertThat(older.title()).isEqualTo("Draft claim");
    }

    @Test
    @UseCase(id = "UC-006", businessRules = {"BR-024"})
    void other_employees_see_no_foreign_claims() {
        emmaHasDraftAndSubmittedClaims();

        signIn(erik());
        openClaimsList();
        Grid<ClaimSummary> grid = _get(Grid.class, spec -> spec.withId("claims-grid"));

        assertThat(GridKt._size(grid)).isZero();
    }

    @Test
    @UseCase(id = "UC-006", businessRules = {"BR-024"})
    void manager_sees_only_submitted_claims() {
        emmaHasDraftAndSubmittedClaims();

        signIn(mona());
        openClaimsList();
        Grid<ClaimSummary> grid = _get(Grid.class, spec -> spec.withId("claims-grid"));

        assertThat(GridKt._size(grid)).isEqualTo(1);
        ClaimSummary claim = (ClaimSummary) GridKt._get(grid, 0);
        assertThat(claim.title()).isEqualTo("Submitted claim");
        assertThat(claim.status()).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    @UseCase(id = "UC-006", businessRules = {"BR-024"})
    void finance_sees_submitted_approved_and_reimbursed_claims() {
        emmaHasDraftAndSubmittedClaims();

        signIn(frank());
        openClaimsList();
        Grid<ClaimSummary> grid = _get(Grid.class, spec -> spec.withId("claims-grid"));
        assertThat(GridKt._size(grid)).isEqualTo(1);

        // approve the submitted claim; it stays in the finance queue but leaves the manager's
        ClaimSummary claim = (ClaimSummary) GridKt._get(grid, 0);
        openClaim(claim.id());
        _get(Button.class, spec -> spec.withText("Approve")).click();
        clearNotifications();

        openClaimsList();
        grid = _get(Grid.class, spec -> spec.withId("claims-grid"));
        assertThat(GridKt._size(grid)).isEqualTo(1);
        assertThat(((ClaimSummary) GridKt._get(grid, 0)).status()).isEqualTo(ClaimStatus.APPROVED);

        signIn(mona());
        openClaimsList();
        grid = _get(Grid.class, spec -> spec.withId("claims-grid"));
        assertThat(GridKt._size(grid)).isZero();
    }

    @Test
    @UseCase(id = "UC-006")
    void selecting_a_claim_opens_its_details() {
        emmaHasDraftAndSubmittedClaims();

        openClaimsList();
        Grid<ClaimSummary> grid = _get(Grid.class, spec -> spec.withId("claims-grid"));
        GridKt._clickItem(grid, 0);

        _get(H2.class, spec -> spec.withText("Submitted claim"));
        Grid<?> items = _get(Grid.class, spec -> spec.withId("items-grid"));
        assertThat(GridKt._size(items)).isEqualTo(1);
        Grid<?> history = _get(Grid.class, spec -> spec.withId("history-grid"));
        assertThat(GridKt._size(history)).isEqualTo(1);
    }

    @Test
    @UseCase(id = "UC-006", scenario = "A1: Claim not found")
    void missing_claim_shows_not_found_message() {
        signIn(emma());

        UI.getCurrent().navigate(ClaimDetailView.class, 999_999L);

        _get(H2.class, spec -> spec.withText("Claim not found"));
        _get(Paragraph.class, spec -> spec.withText("The claim was not found."));
    }
}
