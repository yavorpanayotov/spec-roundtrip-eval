package com.example.expenses;

import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.clearNotifications;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.expectNotifications;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.expenses.domain.ClaimItem;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.ui.ClaimsListView;
import com.example.expenses.usecase.UseCase;
import com.github.mvysny.kaributesting.v10.GridKt;
import com.github.mvysny.kaributesting.v10.pro.ConfirmDialogKt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UC002ManageExpenseClaimTest extends KaribuTest {

    @BeforeEach
    void signInAsEmma() {
        signIn(emma());
    }

    @Test
    @UseCase(id = "UC-002", businessRules = {"BR-003", "BR-004", "BR-007", "BR-010"})
    void create_claim_and_add_items() {
        long claimId = createClaimViaUi("Conference trip");

        String status = db.select(EXPENSE_CLAIM.STATUS).from(EXPENSE_CLAIM)
                .where(EXPENSE_CLAIM.ID.eq(claimId)).fetchSingle().value1();
        assertThat(status).isEqualTo(ClaimStatus.DRAFT.name());

        addItemViaUi(ExpenseCategory.TRAVEL, "Train ticket",
                new BigDecimal("45.50"), LocalDate.now().minusDays(2), false);
        expectNotifications("Item saved.");
        addItemViaUi(ExpenseCategory.MEALS, "Team dinner",
                new BigDecimal("30.00"), LocalDate.now().minusDays(1), false);
        expectNotifications("Item saved.");

        Grid<ClaimItem> items = _get(Grid.class, spec -> spec.withId("items-grid"));
        assertThat(GridKt._size(items)).isEqualTo(2);
        // BR-010: total is the sum of the item amounts
        _get(Span.class, spec -> spec.withText("Total: 75.50 EUR"));
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A1: Missing or blank title", businessRules = {"BR-003"})
    void blank_title_is_refused() {
        signIn(emma());
        com.vaadin.flow.component.UI.getCurrent().navigate(ClaimsListView.class);
        _get(Button.class, spec -> spec.withText("New claim")).click();
        _get(Button.class, spec -> spec.withText("Create")).click();

        expectNotifications("A claim needs a title.");
        assertThat(db.fetchCount(EXPENSE_CLAIM)).isZero();
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A2: Invalid item", businessRules = {"BR-007"})
    void invalid_items_are_refused() {
        createClaimViaUi("Trip");

        addItemViaUi(null, "Taxi", new BigDecimal("10.00"), LocalDate.now(), false);
        expectNotifications("An expense item needs a category.");
        _get(Button.class, spec -> spec.withText("Cancel")).click();

        addItemViaUi(ExpenseCategory.TRAVEL, null, new BigDecimal("10.00"), LocalDate.now(), false);
        expectNotifications("An expense item needs a description.");
        _get(Button.class, spec -> spec.withText("Cancel")).click();

        addItemViaUi(ExpenseCategory.TRAVEL, "Taxi", BigDecimal.ZERO, LocalDate.now(), false);
        expectNotifications("The amount must be greater than zero.");
        _get(Button.class, spec -> spec.withText("Cancel")).click();

        addItemViaUi(ExpenseCategory.TRAVEL, "Taxi", new BigDecimal("10.00"), null, false);
        expectNotifications("An expense item needs an expense date.");
        _get(Button.class, spec -> spec.withText("Cancel")).click();

        Grid<ClaimItem> items = _get(Grid.class, spec -> spec.withId("items-grid"));
        assertThat(GridKt._size(items)).isZero();
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A3: Expense date in the future", businessRules = {"BR-008"})
    void future_expense_date_is_refused() {
        createClaimViaUi("Trip");

        addItemViaUi(ExpenseCategory.TRAVEL, "Flight",
                new BigDecimal("100.00"), LocalDate.now().plusDays(1), false);

        expectNotifications("The expense date cannot be in the future.");
        Grid<ClaimItem> items = _get(Grid.class, spec -> spec.withId("items-grid"));
        assertThat(GridKt._size(items)).isZero();
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A4: Edit or remove an existing item",
            businessRules = {"BR-006", "BR-010"})
    void edit_and_remove_item() {
        createClaimViaUi("Trip");
        addItemViaUi(ExpenseCategory.MEALS, "Lunch",
                new BigDecimal("20.00"), LocalDate.now(), false);
        clearNotifications();

        Grid<ClaimItem> items = _get(Grid.class, spec -> spec.withId("items-grid"));
        itemAction(items, "Edit").click();
        _get(BigDecimalField.class, spec -> spec.withLabel("Amount (EUR)"))
                .setValue(new BigDecimal("25.00"));
        _get(Button.class, spec -> spec.withText("Save")).click();
        expectNotifications("Item saved.");
        _get(Span.class, spec -> spec.withText("Total: 25.00 EUR"));

        items = _get(Grid.class, spec -> spec.withId("items-grid"));
        itemAction(items, "Remove").click();
        expectNotifications("Item removed.");
        _get(Span.class, spec -> spec.withText("Total: 0.00 EUR"));
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A5: Delete a draft claim", businessRules = {"BR-009"})
    void delete_never_submitted_draft() {
        long claimId = createClaimViaUi("Obsolete claim");

        _get(Button.class, spec -> spec.withText("Delete")).click();
        ConfirmDialogKt._fireConfirm(_get(ConfirmDialog.class));

        expectNotifications("Claim deleted.");
        _get(ClaimsListView.class);
        assertThat(db.fetchCount(EXPENSE_CLAIM, EXPENSE_CLAIM.ID.eq(claimId))).isZero();
    }

    @Test
    @UseCase(id = "UC-002", scenario = "A6: Claim not editable", businessRules = {"BR-006"})
    void submitted_claim_offers_no_item_editing() {
        long claimId = createClaimViaUi("Trip");
        addItemViaUi(ExpenseCategory.TRAVEL, "Train",
                new BigDecimal("30.00"), LocalDate.now(), false);
        _get(Button.class, spec -> spec.withText("Submit")).click();
        clearNotifications();
        openClaim(claimId);

        assertThat(_find(Button.class, spec -> spec.withText("Add item"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Edit"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Remove"))).isEmpty();
    }

    private Button itemAction(Grid<ClaimItem> grid, String caption) {
        HorizontalLayout cell =
                (HorizontalLayout) GridKt._getCellComponent(grid, 0, "actions");
        return cell.getChildren()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> caption.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }
}
