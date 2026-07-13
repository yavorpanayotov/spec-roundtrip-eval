package com.example.expenses.ui;

import com.example.expenses.domain.BusinessRuleException;
import com.example.expenses.domain.Category;
import com.example.expenses.jooq.tables.records.AppUserRecord;
import com.example.expenses.jooq.tables.records.ExpenseClaimRecord;
import com.example.expenses.jooq.tables.records.ExpenseItemRecord;
import com.example.expenses.service.ClaimService;
import com.example.expenses.service.ClaimService.LogEntry;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;

@Route(value = "claims", layout = MainLayout.class)
@PageTitle("Claim | Expense Claims")
public class ClaimDetailView extends VerticalLayout implements HasUrlParameter<Long> {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final ClaimService claimService;
    private long claimId;

    public ClaimDetailView(ClaimService claimService) {
        this.claimService = claimService;
        setSizeFull();
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        if (!CurrentUser.isSignedIn()) {
            event.forwardTo(LoginView.class);
            return;
        }
        this.claimId = id;
        build();
    }

    private void build() {
        removeAll();
        AppUserRecord user = CurrentUser.get();
        ExpenseClaimRecord claim;
        try {
            claim = claimService.getClaim(claimId);
        } catch (BusinessRuleException ex) {
            add(new Span(ex.getMessage()));
            return;
        }

        add(new H2(claim.getTitle()));
        HorizontalLayout meta = new HorizontalLayout(
                badge("Status: " + claim.getStatus().toLowerCase()),
                badge("Total: € " + claimService.total(claimId)));
        if (claim.getDecisionReason() != null) {
            meta.add(badge("Rejection reason: " + claim.getDecisionReason()));
        }
        add(meta);

        add(actionBar(user, claim));
        add(new H3("Items"));
        add(itemsSection(user, claim));
        add(new H3("History"));
        add(historyGrid());
    }

    // ---- Actions ----

    private HorizontalLayout actionBar(AppUserRecord user, ExpenseClaimRecord claim) {
        HorizontalLayout bar = new HorizontalLayout();
        if (claimService.canSubmit(user, claim)) {
            bar.add(primary("Submit", () -> claimService.submit(user.getId(), claimId)));
        }
        if (claimService.canWithdraw(user, claim)) {
            bar.add(plain("Withdraw", () -> claimService.withdraw(user.getId(), claimId)));
        }
        if (claimService.canDelete(user, claim)) {
            Button delete = plain("Delete", () -> {
                claimService.deleteClaim(user.getId(), claimId);
                getUI().ifPresent(ui -> ui.navigate(ClaimsListView.class));
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            bar.add(delete);
        }
        if (claimService.canDecide(user, claim)) {
            bar.add(primary("Approve", () -> claimService.approve(user.getId(), claimId)));
            bar.add(plain("Reject…", this::openRejectDialog));
        }
        if (claimService.canReimburse(user, claim)) {
            bar.add(primary("Reimburse", () -> claimService.reimburse(user.getId(), claimId)));
        }
        return bar;
    }

    private Button primary(String label, Runnable action) {
        Button b = plain(label, action);
        b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return b;
    }

    private Button plain(String label, Runnable action) {
        return new Button(label, e -> run(action));
    }

    private void run(Runnable action) {
        try {
            action.run();
            build();
        } catch (BusinessRuleException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openRejectDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reject claim");
        TextArea reason = new TextArea("Reason");
        reason.setWidth("25em");
        Button reject = new Button("Reject", e -> {
            try {
                claimService.reject(CurrentUser.get().getId(), claimId, reason.getValue());
                dialog.close();
                build();
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        reject.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.add(reason);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), reject);
        dialog.open();
    }

    // ---- Items ----

    private VerticalLayout itemsSection(AppUserRecord user, ExpenseClaimRecord claim) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        boolean editable = claimService.canEditItems(user, claim);

        Grid<ExpenseItemRecord> grid = new Grid<>();
        grid.addColumn(i -> i.getCategory().toLowerCase()).setHeader("Category");
        grid.addColumn(ExpenseItemRecord::getDescription).setHeader("Description").setFlexGrow(2);
        grid.addColumn(i -> "€ " + i.getAmount()).setHeader("Amount");
        grid.addColumn(ExpenseItemRecord::getExpenseDate).setHeader("Date");
        grid.addColumn(i -> i.getHasReceipt() ? "yes" : "no").setHeader("Receipt");
        if (editable) {
            grid.addComponentColumn(item -> {
                Button edit = new Button("Edit", e -> openItemDialog(item));
                Button remove = new Button("Remove", e -> run(() ->
                        claimService.removeItem(user.getId(), item.getId())));
                remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                return new HorizontalLayout(edit, remove);
            }).setHeader("");
        }
        grid.setItems(claimService.items(claimId));
        grid.setAllRowsVisible(true);
        section.add(grid);

        if (editable) {
            section.add(new Button("Add item…", e -> openItemDialog(null)));
        }
        return section;
    }

    private void openItemDialog(ExpenseItemRecord existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add item" : "Edit item");

        ComboBox<Category> category = new ComboBox<>("Category");
        category.setItems(Category.values());
        TextField description = new TextField("Description");
        BigDecimalField amount = new BigDecimalField("Amount (EUR)");
        DatePicker date = new DatePicker("Expense date");
        Checkbox receipt = new Checkbox("Receipt attached");

        if (existing != null) {
            category.setValue(Category.valueOf(existing.getCategory()));
            description.setValue(existing.getDescription());
            amount.setValue(existing.getAmount());
            date.setValue(existing.getExpenseDate());
            receipt.setValue(existing.getHasReceipt());
        }

        Button save = new Button("Save", e -> {
            try {
                long actorId = CurrentUser.get().getId();
                if (existing == null) {
                    claimService.addItem(actorId, claimId, category.getValue(), description.getValue(),
                            amount.getValue(), date.getValue(), Boolean.TRUE.equals(receipt.getValue()));
                } else {
                    claimService.updateItem(actorId, existing.getId(), category.getValue(), description.getValue(),
                            amount.getValue(), date.getValue(), Boolean.TRUE.equals(receipt.getValue()));
                }
                dialog.close();
                build();
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new VerticalLayout(category, description, amount, date, receipt));
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    // ---- History ----

    private Grid<LogEntry> historyGrid() {
        Grid<LogEntry> grid = new Grid<>();
        grid.addColumn(l -> l.action().name().toLowerCase()).setHeader("Action");
        grid.addColumn(LogEntry::actorName).setHeader("By");
        grid.addColumn(l -> DATE_TIME.format(l.occurredAt())).setHeader("When");
        grid.addColumn(LogEntry::reason).setHeader("Reason").setFlexGrow(2);
        grid.setItems(claimService.decisionLog(claimId));
        grid.setAllRowsVisible(true);
        return grid;
    }

    private Span badge(String text) {
        Span span = new Span(text);
        span.getElement().getThemeList().add("badge");
        return span;
    }
}
