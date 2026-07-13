package com.example.expenses.ui;

import com.example.expenses.jooq.tables.records.AppUserRecord;
import com.example.expenses.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("login")
@PageTitle("Sign in | Expense Claims")
public class LoginView extends VerticalLayout {

    public LoginView(UserService userService) {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        ComboBox<AppUserRecord> userPicker = new ComboBox<>("Sign in as");
        userPicker.setItems(userService.allUsers());
        userPicker.setItemLabelGenerator(u -> u.getName() + " (" + u.getRole().toLowerCase() + ")");
        userPicker.setWidth("20em");

        Button signIn = new Button("Sign in", e -> {
            AppUserRecord user = userPicker.getValue();
            if (user == null) {
                Notification.show("Pick a user first.");
                return;
            }
            CurrentUser.set(user);
            getUI().ifPresent(ui -> ui.navigate(ClaimsListView.class));
        });
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        signIn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        add(new H1("Expense Claims"), userPicker, signIn);
    }
}
