package com.example.expenses.ui;

import com.example.expenses.domain.BusinessRuleException;
import com.example.expenses.service.ClaimService;
import com.example.expenses.service.ClaimService.ClaimSummary;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Claims | Expense Claims")
public class ClaimsListView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final ClaimService claimService;
    private final Grid<ClaimSummary> grid = new Grid<>();

    public ClaimsListView(ClaimService claimService) {
        this.claimService = claimService;
        setSizeFull();

        Button newClaim = new Button("New claim", e -> openNewClaimDialog());
        newClaim.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        grid.addColumn(ClaimSummary::title).setHeader("Title").setFlexGrow(2);
        grid.addColumn(ClaimSummary::ownerName).setHeader("Owner");
        grid.addColumn(c -> c.status().name().toLowerCase()).setHeader("Status");
        grid.addColumn(c -> "€ " + c.total()).setHeader("Total");
        grid.addColumn(c -> c.submittedAt() != null ? DATE_TIME.format(c.submittedAt()) : "")
                .setHeader("Submitted");
        grid.addItemClickListener(e ->
                getUI().ifPresent(ui -> ui.navigate(ClaimDetailView.class, e.getItem().id())));
        grid.setSizeFull();

        add(newClaim, grid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!CurrentUser.isSignedIn()) {
            event.forwardTo(LoginView.class);
            return;
        }
        refresh();
    }

    private void refresh() {
        grid.setItems(claimService.visibleClaims(CurrentUser.get().getId()));
    }

    private void openNewClaimDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New claim");
        TextField title = new TextField("Title");
        title.setWidth("20em");
        Button create = new Button("Create", e -> {
            try {
                long id = claimService.createClaim(CurrentUser.get().getId(), title.getValue());
                dialog.close();
                getUI().ifPresent(ui -> ui.navigate(ClaimDetailView.class, id));
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(title);
        dialog.getFooter().add(cancel, create);
        dialog.open();
    }
}
