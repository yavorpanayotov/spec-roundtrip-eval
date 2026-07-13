package com.example.expenses.ui;

import com.example.expenses.domain.ClaimSummary;
import com.example.expenses.service.BusinessRuleException;
import com.example.expenses.service.ClaimService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * UC-006: Track claims — the claims visible to the signed-in user's role (BR-024).
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Claims | Expense Claims")
public class ClaimsListView extends VerticalLayout {

    private final ClaimService claimService;
    private final Grid<ClaimSummary> grid = new Grid<>();

    public ClaimsListView(ClaimService claimService) {
        this.claimService = claimService;
        setSizeFull();

        H2 heading = new H2("Claims");
        heading.getStyle().set("margin", "0");
        Button newClaim = new Button("New claim", click -> openCreateDialog());
        newClaim.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(heading, newClaim);
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        grid.setId("claims-grid");
        grid.addColumn(ClaimSummary::title).setHeader("Title").setFlexGrow(2);
        grid.addColumn(ClaimSummary::ownerName).setHeader("Owner");
        grid.addComponentColumn(claim -> Badges.status(claim.status())).setHeader("Status");
        grid.addColumn(claim -> Formats.money(claim.total())).setHeader("Total")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(claim -> Formats.dateTime(claim.submittedAt())).setHeader("Submitted");
        grid.setSizeFull();
        grid.addItemClickListener(event -> getUI().ifPresent(
                ui -> ui.navigate(ClaimDetailView.class, event.getItem().id())));

        add(toolbar, grid);
        refresh();
    }

    private void refresh() {
        grid.setItems(claimService.listClaims(CurrentUser.get()));
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New claim");

        TextField title = new TextField("Title");
        title.setWidth("20em");
        dialog.add(title);

        Button create = new Button("Create", click -> {
            try {
                long id = claimService.createClaim(CurrentUser.get(), title.getValue());
                dialog.close();
                getUI().ifPresent(ui -> ui.navigate(ClaimDetailView.class, id));
            } catch (BusinessRuleException e) {
                Notification.show(e.getMessage(), 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", click -> dialog.close());

        dialog.getFooter().add(cancel, create);
        dialog.open();
    }
}
