package com.example.expenses.ui;

import com.example.expenses.domain.Category;
import com.example.expenses.domain.ClaimAction;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.DecisionView;
import com.example.expenses.domain.ItemView;
import com.example.expenses.domain.RuleViolationException;
import com.example.expenses.service.ExpenseService;
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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;

import java.util.Set;

/**
 * ClaimDetail surface: exposes the claim, its items and its decision log;
 * offers exactly the operations whose provides/when guards hold for the
 * current viewer (as computed by the service).
 */
@Route(value = "claims", layout = MainLayout.class)
public class ClaimDetailView extends VerticalLayout implements HasUrlParameter<Long> {

    private final ExpenseService service = AppContext.service();
    private long claimId;

    public ClaimDetailView() {
        setSizeFull();
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.claimId = parameter;
        refresh();
    }

    private void refresh() {
        removeAll();
        if (CurrentUser.get() == null) {
            return;
        }
        long viewer = CurrentUser.get().id();
        getStyle().set("overflow", "auto");
        com.example.expenses.domain.ClaimDetailView claim;
        try {
            claim = service.claimDetail(claimId);
        } catch (RuleViolationException ex) {
            add(new Span("Claim not found."));
            return;
        }
        Set<ClaimAction> actions = service.availableActions(viewer, claimId);

        add(new H2(claim.title()));
        Span status = new Span("Status: " + claim.status().name().toLowerCase());
        Span total = new Span("Total: " + claim.total() + " €");
        HorizontalLayout facts = new HorizontalLayout(status, total);
        if (claim.status() == ClaimStatus.REJECTED && claim.decisionReason() != null) {
            Span reason = new Span("Rejected: " + claim.decisionReason());
            reason.getStyle().set("color", "var(--lumo-error-text-color)");
            facts.add(reason);
        }
        add(facts);
        add(actionBar(actions));

        add(new H3("Items"));
        boolean canManageItems = actions.contains(ClaimAction.MANAGE_ITEMS);
        Grid<ItemView> items = new Grid<>();
        items.addColumn(i -> i.category().name().toLowerCase()).setHeader("Category");
        items.addColumn(ItemView::description).setHeader("Description").setFlexGrow(2);
        items.addColumn(i -> i.amount() + " €").setHeader("Amount");
        items.addColumn(ItemView::expenseDate).setHeader("Date");
        items.addColumn(i -> i.hasReceipt() ? "yes" : i.requiresReceipt() ? "MISSING" : "no")
                .setHeader("Receipt");
        if (canManageItems) {
            items.addComponentColumn(item -> {
                Button edit = new Button("Edit", e -> openItemDialog(item));
                Button remove = new Button("Remove", e -> invoke(() ->
                        service.removeItem(viewer, item.id())));
                remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                return new HorizontalLayout(edit, remove);
            }).setHeader("");
        }
        items.setItems(claim.items());
        items.setAllRowsVisible(true);
        add(items);
        if (canManageItems) {
            add(new Button("Add item", e -> openItemDialog(null)));
        }

        add(new H3("Decision log"));
        Grid<DecisionView> log = new Grid<>();
        log.addColumn(d -> d.action().name().toLowerCase()).setHeader("Action");
        log.addColumn(DecisionView::actorName).setHeader("By");
        log.addColumn(DecisionView::occurredAt).setHeader("At");
        log.addColumn(DecisionView::reason).setHeader("Reason").setFlexGrow(2);
        log.setItems(claim.decisions());
        log.setAllRowsVisible(true);
        add(log);
    }

    private HorizontalLayout actionBar(Set<ClaimAction> actions) {
        long viewer = CurrentUser.get().id();
        HorizontalLayout bar = new HorizontalLayout();
        if (actions.contains(ClaimAction.SUBMIT)) {
            Button b = new Button("Submit", e -> invoke(() -> service.submitClaim(viewer, claimId)));
            b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            bar.add(b);
        }
        if (actions.contains(ClaimAction.WITHDRAW)) {
            bar.add(new Button("Withdraw", e -> invoke(() -> service.withdrawClaim(viewer, claimId))));
        }
        if (actions.contains(ClaimAction.DELETE)) {
            Button b = new Button("Delete", e -> {
                try {
                    service.deleteClaim(viewer, claimId);
                    getUI().ifPresent(ui -> ui.navigate(ClaimsListView.class));
                } catch (RuleViolationException ex) {
                    Notification.show(ex.getMessage());
                }
            });
            b.addThemeVariants(ButtonVariant.LUMO_ERROR);
            bar.add(b);
        }
        if (actions.contains(ClaimAction.APPROVE)) {
            Button b = new Button("Approve", e -> invoke(() -> service.approveClaim(viewer, claimId)));
            b.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
            bar.add(b);
        }
        if (actions.contains(ClaimAction.REJECT)) {
            bar.add(new Button("Reject…", e -> openRejectDialog()));
        }
        if (actions.contains(ClaimAction.REIMBURSE)) {
            Button b = new Button("Reimburse", e -> invoke(() -> service.reimburseClaim(viewer, claimId)));
            b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            bar.add(b);
        }
        return bar;
    }

    private void openRejectDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reject claim");
        TextArea reason = new TextArea("Reason");
        reason.setWidth("24em");
        Button reject = new Button("Reject", e -> {
            try {
                service.rejectClaim(CurrentUser.get().id(), claimId, reason.getValue());
                dialog.close();
                refresh();
            } catch (RuleViolationException ex) {
                Notification.show(ex.getMessage());
            }
        });
        reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        dialog.add(reason);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), reject);
        dialog.open();
    }

    private void openItemDialog(ItemView existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add item" : "Edit item");

        ComboBox<Category> category = new ComboBox<>("Category");
        category.setItems(Category.values());
        category.setItemLabelGenerator(c -> c.name().toLowerCase());
        TextField description = new TextField("Description");
        BigDecimalField amount = new BigDecimalField("Amount (€)");
        DatePicker expenseDate = new DatePicker("Expense date");
        Checkbox hasReceipt = new Checkbox("Receipt attached");

        if (existing != null) {
            category.setValue(existing.category());
            description.setValue(existing.description());
            amount.setValue(existing.amount());
            expenseDate.setValue(existing.expenseDate());
            hasReceipt.setValue(existing.hasReceipt());
        }

        Button save = new Button("Save", e -> {
            try {
                long viewer = CurrentUser.get().id();
                if (existing == null) {
                    service.addItem(viewer, claimId, category.getValue(), description.getValue(),
                            amount.getValue(), expenseDate.getValue(), hasReceipt.getValue());
                } else {
                    service.updateItem(viewer, existing.id(), category.getValue(),
                            description.getValue(), amount.getValue(), expenseDate.getValue(),
                            hasReceipt.getValue());
                }
                dialog.close();
                refresh();
            } catch (RuleViolationException ex) {
                Notification.show(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new VerticalLayout(category, description, amount, expenseDate, hasReceipt));
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    private void invoke(Runnable operation) {
        try {
            operation.run();
        } catch (RuleViolationException ex) {
            Notification.show(ex.getMessage());
        }
        refresh();
    }
}
