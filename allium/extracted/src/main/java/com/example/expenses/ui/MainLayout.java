package com.example.expenses.ui;

import com.example.expenses.jooq.tables.records.AppUserRecord;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.VaadinSession;

public class MainLayout extends AppLayout {

    public MainLayout() {
        H1 title = new H1("Expense Claims");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        HorizontalLayout header = new HorizontalLayout(title);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setPadding(true);
        header.expand(title);

        AppUserRecord user = CurrentUser.get();
        if (user != null) {
            Span who = new Span(user.getName() + " · " + user.getRole().toLowerCase());
            Button signOut = new Button("Sign out", e -> {
                VaadinSession.getCurrent().getSession().invalidate();
                getUI().ifPresent(ui -> ui.getPage().setLocation("login"));
            });
            signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            header.add(who, signOut);
        }

        addToNavbar(header);
    }
}
