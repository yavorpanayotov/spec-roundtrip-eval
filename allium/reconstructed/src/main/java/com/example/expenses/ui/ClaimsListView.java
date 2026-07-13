package com.example.expenses.ui;

import com.example.expenses.domain.ClaimSummary;
import com.example.expenses.domain.RuleViolationException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;

/**
 * ClaimsList surface: role-scoped claim visibility, exposing title,
 * owner name, status, total and submitted_at; provides CreateClaim.
 */
@Route(value = "", layout = MainLayout.class)
public class ClaimsListView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ClaimSummary> grid = new Grid<>();

    public ClaimsListView() {
        setSizeFull();

        Button newClaim = new Button("New claim", e -> openCreateDialog());
        newClaim.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        grid.addColumn(ClaimSummary::title).setHeader("Title").setFlexGrow(2);
        grid.addColumn(ClaimSummary::ownerName).setHeader("Owner");
        grid.addColumn(c -> c.status().name().toLowerCase()).setHeader("Status");
        grid.addColumn(c -> c.total() + " €").setHeader("Total");
        grid.addColumn(ClaimSummary::submittedAt).setHeader("Submitted at");
        grid.addItemClickListener(e -> getUI().ifPresent(
                ui -> ui.navigate(ClaimDetailView.class, e.getItem().id())));
        grid.setSizeFull();

        add(newClaim, grid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (CurrentUser.get() != null) {
            grid.setItems(AppContext.service().claimsVisibleTo(CurrentUser.get().id()));
        }
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New claim");
        TextField title = new TextField("Title");
        title.setWidth("20em");
        Button create = new Button("Create", e -> {
            try {
                long id = AppContext.service()
                        .createClaim(CurrentUser.get().id(), title.getValue());
                dialog.close();
                getUI().ifPresent(ui -> ui.navigate(ClaimDetailView.class, id));
            } catch (RuleViolationException ex) {
                Notification.show(ex.getMessage());
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(title);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), create);
        dialog.open();
    }
}
