package com.example.expenses.ui;

import com.example.expenses.domain.AppUser;
import com.example.expenses.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * UC-001: Sign in by picking a user (role-based authorization demo, no password — BR-002).
 */
@Route("login")
@PageTitle("Sign in | Expense Claims")
public class LoginView extends VerticalLayout {

    private final ComboBox<AppUser> userPicker = new ComboBox<>("User");

    public LoginView(UserService userService) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        userPicker.setItems(userService.listUsers());
        userPicker.setItemLabelGenerator(
                user -> user.name() + " (" + user.role().label() + ")");
        userPicker.setWidth("20em");

        Button signIn = new Button("Sign in", click -> signIn());
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new H1("Expense Claims"),
                new Paragraph("Select a user to sign in."),
                userPicker, signIn);
    }

    private void signIn() {
        AppUser user = userPicker.getValue();
        if (user == null) {
            Notification.show("Please pick a user first.", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        CurrentUser.set(user);
        getUI().ifPresent(ui -> ui.navigate(ClaimsListView.class));
    }
}
