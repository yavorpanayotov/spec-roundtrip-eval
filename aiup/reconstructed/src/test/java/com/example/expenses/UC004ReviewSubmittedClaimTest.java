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
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UC004ReviewSubmittedClaimTest extends KaribuTest {

    private long submittedClaim(BigDecimal amount) {
        signIn(emma());
        long claimId = createClaimViaUi("Reviewable claim");
        addItemViaUi(ExpenseCategory.EQUIPMENT, "Laptop", amount, LocalDate.now(), true);
        _get(Button.class, spec -> spec.withText("Submit")).click();
        clearNotifications();
        return claimId;
    }

    @Test
    @UseCase(id = "UC-004", businessRules = {"BR-017", "BR-018", "BR-016"})
    void manager_approves_submitted_claim() {
        long claimId = submittedClaim(new BigDecimal("120.00"));

        signIn(mona());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Approve")).click();

        expectNotifications("Claim approved.");
        _get(Span.class, spec -> spec.withText("Approved"));
        Grid<HistoryEntry> history = _get(Grid.class, spec -> spec.withId("history-grid"));
        assertThat(GridKt._size(history)).isEqualTo(2);
        HistoryEntry entry = (HistoryEntry) GridKt._get(history, 1);
        assertThat(entry.actorName()).isEqualTo(mona().name());
        assertThat(entry.action().name()).isEqualTo("APPROVED");
    }

    @Test
    @UseCase(id = "UC-004", scenario = "A1: Reject with a reason",
            businessRules = {"BR-021", "BR-016"})
    void manager_rejects_with_reason() {
        long claimId = submittedClaim(new BigDecimal("120.00"));

        signIn(mona());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Reject")).click();
        Dialog dialog = _get(Dialog.class);
        LocatorJ._get(dialog, TextArea.class, spec -> spec.withLabel("Reason"))
                .setValue("Missing project code");
        LocatorJ._get(dialog, Button.class, spec -> spec.withText("Reject")).click();

        expectNotifications("Claim rejected.");
        _get(Span.class, spec -> spec.withText("Rejected"));
        _get(Span.class, spec -> spec.withText("Rejection reason: Missing project code"));
        Grid<HistoryEntry> history = _get(Grid.class, spec -> spec.withId("history-grid"));
        HistoryEntry entry = (HistoryEntry) GridKt._get(history, 1);
        assertThat(entry.reason()).isEqualTo("Missing project code");
    }

    @Test
    @UseCase(id = "UC-004", scenario = "A2: Missing rejection reason", businessRules = {"BR-021"})
    void rejection_without_reason_is_refused() {
        long claimId = submittedClaim(new BigDecimal("120.00"));

        signIn(mona());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Reject")).click();
        Dialog dialog = _get(Dialog.class);
        LocatorJ._get(dialog, Button.class, spec -> spec.withText("Reject")).click();

        expectNotifications("Rejecting a claim requires a reason.");
        // the claim is still submitted; the dialog stays open for another attempt
        LocatorJ._get(dialog, TextArea.class, spec -> spec.withLabel("Reason"));
    }

    @Test
    @UseCase(id = "UC-004", scenario = "A3: Claim total exceeds the reviewer's authority",
            businessRules = {"BR-020"})
    void manager_cannot_approve_claim_over_limit() {
        long claimId = submittedClaim(new BigDecimal("5000.01"));

        signIn(mona());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Approve")).click();
        expectNotifications("Claims over 5000.00 EUR can only be approved by finance.");
        _get(Span.class, spec -> spec.withText("Submitted"));

        // a finance officer may approve it
        signIn(frank());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Approve")).click();
        expectNotifications("Claim approved.");
        _get(Span.class, spec -> spec.withText("Approved"));
    }

    @Test
    @UseCase(id = "UC-004", scenario = "A3: Claim total exceeds the reviewer's authority",
            businessRules = {"BR-020"})
    void manager_may_approve_claim_exactly_at_limit() {
        long claimId = submittedClaim(new BigDecimal("5000.00"));

        signIn(mona());
        openClaim(claimId);
        _get(Button.class, spec -> spec.withText("Approve")).click();

        expectNotifications("Claim approved.");
    }

    @Test
    @UseCase(id = "UC-004", scenario = "A4: Reviewer not allowed to decide",
            businessRules = {"BR-018", "BR-019"})
    void employees_and_owners_see_no_decision_buttons() {
        long claimId = submittedClaim(new BigDecimal("120.00"));

        // another employee cannot decide (and does not even see the claim)
        signIn(erik());
        openClaim(claimId);
        assertThat(_find(Button.class, spec -> spec.withText("Approve"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Reject"))).isEmpty();

        // the owner sees no decision buttons either, regardless of role (BR-019)
        signIn(emma());
        openClaim(claimId);
        assertThat(_find(Button.class, spec -> spec.withText("Approve"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Reject"))).isEmpty();
    }
}
