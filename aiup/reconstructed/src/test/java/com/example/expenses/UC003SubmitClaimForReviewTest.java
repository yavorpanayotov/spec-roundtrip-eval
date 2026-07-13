package com.example.expenses;

import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.clearNotifications;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.expectNotifications;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.domain.HistoryEntry;
import com.example.expenses.usecase.UseCase;
import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UC003SubmitClaimForReviewTest extends KaribuTest {

    @BeforeEach
    void signInAsEmma() {
        signIn(emma());
    }

    @Test
    @UseCase(id = "UC-003", businessRules = {"BR-011", "BR-012", "BR-016"})
    void submit_claim_with_items() {
        long claimId = createClaimViaUi("Client visit");
        addItemViaUi(ExpenseCategory.TRAVEL, "Train",
                new BigDecimal("30.00"), LocalDate.now(), false);
        clearNotifications();

        _get(Button.class, spec -> spec.withText("Submit")).click();

        expectNotifications("Claim submitted for review.");
        _get(Span.class, spec -> spec.withText("Submitted"));
        var record = db.select(EXPENSE_CLAIM.STATUS, EXPENSE_CLAIM.SUBMITTED_AT)
                .from(EXPENSE_CLAIM).where(EXPENSE_CLAIM.ID.eq(claimId)).fetchSingle();
        assertThat(record.value1()).isEqualTo(ClaimStatus.SUBMITTED.name());
        assertThat(record.value2()).isNotNull();

        Grid<HistoryEntry> history = _get(Grid.class, spec -> spec.withId("history-grid"));
        assertThat(GridKt._size(history)).isEqualTo(1);
        HistoryEntry entry = (HistoryEntry) GridKt._get(history, 0);
        assertThat(entry.actorName()).isEqualTo(emma().name());
        assertThat(entry.action().name()).isEqualTo("SUBMITTED");
    }

    @Test
    @UseCase(id = "UC-003", scenario = "A1: Claim has no items", businessRules = {"BR-012"})
    void claim_without_items_cannot_be_submitted() {
        long claimId = createClaimViaUi("Empty claim");

        _get(Button.class, spec -> spec.withText("Submit")).click();

        expectNotifications("A claim needs at least one expense item before it can be submitted.");
        String status = db.select(EXPENSE_CLAIM.STATUS).from(EXPENSE_CLAIM)
                .where(EXPENSE_CLAIM.ID.eq(claimId)).fetchSingle().value1();
        assertThat(status).isEqualTo(ClaimStatus.DRAFT.name());
    }

    @Test
    @UseCase(id = "UC-003", scenario = "A2: Receipt missing on a large item",
            businessRules = {"BR-013"})
    void large_item_without_receipt_blocks_submission() {
        createClaimViaUi("Hardware");
        addItemViaUi(ExpenseCategory.EQUIPMENT, "Monitor",
                new BigDecimal("60.00"), LocalDate.now(), false);
        clearNotifications();

        _get(Button.class, spec -> spec.withText("Submit")).click();
        expectNotifications("Item 'Monitor' (60.00 EUR) exceeds the receipt threshold "
                + "of 50.00 EUR and needs a receipt.");
        _get(Span.class, spec -> spec.withText("Draft"));

        // attach the receipt, then submission succeeds
        Grid<?> items = _get(Grid.class, spec -> spec.withId("items-grid"));
        ((Button) GridKt._getCellComponent(items, 0, "actions").getChildren()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "Edit".equals(button.getText()))
                .findFirst().orElseThrow()).click();
        _get(Checkbox.class, spec -> spec.withLabel("Receipt attached")).setValue(true);
        _get(Button.class, spec -> spec.withText("Save")).click();
        clearNotifications();

        _get(Button.class, spec -> spec.withText("Submit")).click();
        expectNotifications("Claim submitted for review.");
    }

    @Test
    @UseCase(id = "UC-003", scenario = "A2: Receipt missing on a large item",
            businessRules = {"BR-013"})
    void item_exactly_at_threshold_needs_no_receipt() {
        createClaimViaUi("Small purchase");
        addItemViaUi(ExpenseCategory.OTHER, "Adapter",
                new BigDecimal("50.00"), LocalDate.now(), false);
        clearNotifications();

        _get(Button.class, spec -> spec.withText("Submit")).click();

        expectNotifications("Claim submitted for review.");
    }

    @Test
    @UseCase(id = "UC-003", scenario = "A4: Withdraw a submitted claim",
            businessRules = {"BR-015", "BR-009", "BR-016"})
    void withdraw_returns_claim_to_draft() {
        long claimId = createClaimViaUi("Trip");
        addItemViaUi(ExpenseCategory.TRAVEL, "Bus",
                new BigDecimal("10.00"), LocalDate.now(), false);
        _get(Button.class, spec -> spec.withText("Submit")).click();
        clearNotifications();
        openClaim(claimId);

        _get(Button.class, spec -> spec.withText("Withdraw")).click();

        expectNotifications("Claim withdrawn back to draft.");
        _get(Span.class, spec -> spec.withText("Draft"));
        Grid<HistoryEntry> history = _get(Grid.class, spec -> spec.withId("history-grid"));
        assertThat(GridKt._size(history)).isEqualTo(2);

        // BR-009: a withdrawn claim was submitted once, so it can no longer be deleted
        assertThat(_find(Button.class, spec -> spec.withText("Delete"))).isEmpty();
    }
}
