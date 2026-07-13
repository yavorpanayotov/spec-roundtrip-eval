package com.example.expenses.ui;

import com.example.expenses.domain.AppUser;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;

public class MainLayout extends AppLayout {

    public MainLayout() {
        H1 title = new H1("Expense Claims");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        RouterLink claims = new RouterLink("Claims", ClaimsListView.class);

        HorizontalLayout header = new HorizontalLayout(title, claims);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setPadding(true);
        header.expand(claims);

        AppUser user = CurrentUser.get();
        if (user != null) {
            Span userInfo = new Span(user.name() + " · " + user.role().label());
            userInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");
            Button signOut = new Button("Sign out", click -> signOut());
            signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            header.add(userInfo, signOut);
        }

        addToNavbar(header);
    }

    private void signOut() {
        CurrentUser.clear();
        getUI().ifPresent(ui -> ui.navigate(LoginView.class));
    }
}
