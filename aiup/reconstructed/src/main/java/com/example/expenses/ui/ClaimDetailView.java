package com.example.expenses.ui;

import com.example.expenses.domain.AppUser;
import com.example.expenses.domain.ClaimDetails;
import com.example.expenses.domain.ClaimItem;
import com.example.expenses.domain.ClaimStatus;
import com.example.expenses.domain.HistoryEntry;
import com.example.expenses.domain.Role;
import com.example.expenses.service.BusinessRuleException;
import com.example.expenses.service.ClaimService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;

/**
 * Claim detail: items, actions, and history (UC-002 .. UC-006).
 */
@Route(value = "claims", layout = MainLayout.class)
@PageTitle("Claim | Expense Claims")
public class ClaimDetailView extends VerticalLayout implements HasUrlParameter<Long> {

    private final ClaimService claimService;
    private long claimId;

    public ClaimDetailView(ClaimService claimService) {
        this.claimService = claimService;
        setSizeFull();
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.claimId = parameter == null ? 0 : parameter;
        render();
    }

    private void render() {
        removeAll();
        ClaimDetails claim = claimService.findClaimFor(CurrentUser.get(), claimId).orElse(null);
        if (claim == null) {
            add(new H2("Claim not found"),
                    new Paragraph("The claim was not found."));
            return;
        }

        AppUser user = CurrentUser.get();
        boolean owner = user.id() == claim.ownerId();
        boolean editable = owner && (claim.status() == ClaimStatus.DRAFT
                || claim.status() == ClaimStatus.REJECTED);

        add(header(claim), actions(claim, user, owner, editable));

        if (claim.decisionReason() != null && claim.status() == ClaimStatus.REJECTED) {
            Span reason = new Span("Rejection reason: " + claim.decisionReason());
            reason.getStyle().set("color", "var(--lumo-error-text-color)");
            add(reason);
        }

        add(new H3("Items"), itemsGrid(claim, editable));
        if (editable) {
            Button addItem = new Button("Add item", click -> openItemDialog(null));
            add(addItem);
        }

        add(new H3("History"), historyGrid(claimService.history(claimId)));
    }

    private HorizontalLayout header(ClaimDetails claim) {
        H2 title = new H2(claim.title());
        title.getStyle().set("margin", "0");
        Span total = new Span("Total: " + Formats.money(claim.total()));
        total.getStyle().set("font-weight", "bold");
        Span meta = new Span("Owner: " + claim.ownerName()
                + " · Created: " + Formats.dateTime(claim.createdAt())
                + (claim.submittedAt() != null
                        ? " · Submitted: " + Formats.dateTime(claim.submittedAt()) : "")
                + (claim.reimbursedAt() != null
                        ? " · Reimbursed: " + Formats.dateTime(claim.reimbursedAt()) : ""));
        meta.getStyle().set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout text = new VerticalLayout(title, meta);
        text.setPadding(false);
        text.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(text, Badges.status(claim.status()), total);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.expand(text);
        return header;
    }

    private HorizontalLayout actions(ClaimDetails claim, AppUser user, boolean owner,
            boolean editable) {
        HorizontalLayout actions = new HorizontalLayout();

        if (editable) {
            Button submit = new Button("Submit", click -> run(() -> {
                claimService.submit(user, claimId);
                success("Claim submitted for review.");
            }));
            submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(submit);
        }
        if (owner && claim.status() == ClaimStatus.DRAFT && claim.submittedAt() == null) {
            Button delete = new Button("Delete", click -> confirmDelete(user));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            actions.add(delete);
        }
        if (owner && claim.status() == ClaimStatus.SUBMITTED) {
            Button withdraw = new Button("Withdraw", click -> run(() -> {
                claimService.withdraw(user, claimId);
                success("Claim withdrawn back to draft.");
            }));
            actions.add(withdraw);
        }
        boolean reviewer = user.role() == Role.MANAGER || user.role() == Role.FINANCE;
        if (reviewer && !owner && claim.status() == ClaimStatus.SUBMITTED) {
            Button approve = new Button("Approve", click -> run(() -> {
                claimService.approve(user, claimId);
                success("Claim approved.");
            }));
            approve.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
            Button reject = new Button("Reject", click -> openRejectDialog(user));
            reject.addThemeVariants(ButtonVariant.LUMO_ERROR);
            actions.add(approve, reject);
        }
        if (user.role() == Role.FINANCE && claim.status() == ClaimStatus.APPROVED) {
            Button reimburse = new Button("Reimburse", click -> run(() -> {
                claimService.reimburse(user, claimId);
                success("Claim reimbursed.");
            }));
            reimburse.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
            actions.add(reimburse);
        }
        return actions;
    }

    private Grid<ClaimItem> itemsGrid(ClaimDetails claim, boolean editable) {
        Grid<ClaimItem> grid = new Grid<>();
        grid.setId("items-grid");
        grid.addColumn(item -> item.category().label()).setHeader("Category");
        grid.addColumn(ClaimItem::description).setHeader("Description").setFlexGrow(2);
        grid.addColumn(item -> Formats.money(item.amount())).setHeader("Amount")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(item -> Formats.date(item.expenseDate())).setHeader("Date");
        grid.addColumn(item -> item.hasReceipt() ? "Yes" : "No").setHeader("Receipt");
        if (editable) {
            grid.addComponentColumn(item -> {
                Button edit = new Button("Edit", click -> openItemDialog(item));
                edit.addThemeVariants(ButtonVariant.LUMO_SMALL);
                Button remove = new Button("Remove", click -> run(() -> {
                    claimService.removeItem(CurrentUser.get(), claimId, item.id());
                    success("Item removed.");
                }));
                remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                return new HorizontalLayout(edit, remove);
            }).setHeader("Actions").setKey("actions");
        }
        List<ClaimItem> items = claimService.items(claimId);
        grid.setItems(items);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private Grid<HistoryEntry> historyGrid(List<HistoryEntry> entries) {
        Grid<HistoryEntry> grid = new Grid<>();
        grid.setId("history-grid");
        grid.addColumn(entry -> Formats.dateTime(entry.occurredAt())).setHeader("When");
        grid.addColumn(HistoryEntry::actorName).setHeader("Who");
        grid.addColumn(entry -> entry.action().label()).setHeader("Action");
        grid.addColumn(HistoryEntry::reason).setHeader("Reason").setFlexGrow(2);
        grid.setItems(entries);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private void openItemDialog(ClaimItem existing) {
        ItemDialog dialog = new ItemDialog(existing, data -> {
            AppUser user = CurrentUser.get();
            try {
                if (existing == null) {
                    claimService.addItem(user, claimId, data);
                } else {
                    claimService.updateItem(user, claimId, existing.id(), data);
                }
            } catch (BusinessRuleException e) {
                error(e.getMessage());
                return;
            }
            closeItemDialogAndRefresh();
            success("Item saved.");
        });
        dialog.open();
    }

    private void closeItemDialogAndRefresh() {
        getUI().ifPresent(ui -> ui.getChildren()
                .filter(ItemDialog.class::isInstance)
                .map(ItemDialog.class::cast)
                .forEach(Dialog::close));
        render();
    }

    private void openRejectDialog(AppUser user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reject claim");
        TextArea reason = new TextArea("Reason");
        reason.setWidth("24em");
        dialog.add(reason);

        Button confirm = new Button("Reject", click -> {
            try {
                claimService.reject(user, claimId, reason.getValue());
            } catch (BusinessRuleException e) {
                error(e.getMessage());
                return;
            }
            dialog.close();
            render();
            successNotify("Claim rejected.");
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancel = new Button("Cancel", click -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void confirmDelete(AppUser user) {
        ConfirmDialog dialog = new ConfirmDialog(
                "Delete claim",
                "Delete this claim together with its items?",
                "Delete",
                confirm -> run(() -> {
                    claimService.deleteClaim(user, claimId);
                    successNotify("Claim deleted.");
                    getUI().ifPresent(ui -> ui.navigate(ClaimsListView.class));
                }),
                "Cancel",
                cancel -> {
                });
        dialog.setConfirmButtonTheme("error primary");
        dialog.open();
    }

    /** Runs an action, shows business rule violations as error notifications, re-renders on success. */
    private void run(Runnable action) {
        try {
            action.run();
        } catch (BusinessRuleException e) {
            error(e.getMessage());
        }
    }

    private void success(String message) {
        render();
        successNotify(message);
    }

    private void successNotify(String message) {
        Notification.show(message, 2500, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void error(String message) {
        Notification.show(message, 4000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
