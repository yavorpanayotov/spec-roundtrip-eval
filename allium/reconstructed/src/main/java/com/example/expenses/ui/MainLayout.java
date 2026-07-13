package com.example.expenses.ui;

import com.example.expenses.domain.User;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

/** Shell for all signed-in views; forwards to the user picker when signed out. */
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private final Span userLabel = new Span();

    public MainLayout() {
        H1 title = new H1("Expense Claims");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        Button signOut = new Button("Sign out", e -> {
            CurrentUser.clear();
            getUI().ifPresent(ui -> ui.navigate(LoginView.class));
        });
        signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(title, userLabel, signOut);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.expand(title);
        header.getStyle().set("padding", "0 var(--lumo-space-m)");
        addToNavbar(header);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        User user = CurrentUser.get();
        if (user == null) {
            event.forwardTo(LoginView.class);
            return;
        }
        userLabel.setText(user.name() + " · " + user.role().name().toLowerCase());
    }
}
