package com.example.expenses.ui;

import com.example.expenses.domain.User;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("login")
public class LoginView extends VerticalLayout {

    public LoginView() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        ComboBox<User> userPicker = new ComboBox<>("Sign in as");
        userPicker.setItems(AppContext.service().allUsers());
        userPicker.setItemLabelGenerator(u -> u.name() + " (" + u.role().name().toLowerCase() + ")");
        userPicker.setWidth("20em");

        Button signIn = new Button("Sign in", e -> {
            User selected = userPicker.getValue();
            if (selected != null) {
                CurrentUser.set(selected);
                getUI().ifPresent(ui -> ui.navigate(ClaimsListView.class));
            }
        });
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new H1("Expense Claims"), userPicker, signIn);
    }
}
