package com.example.expenses;

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.expectNotifications;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.expenses.domain.AppUser;
import com.example.expenses.ui.ClaimsListView;
import com.example.expenses.ui.CurrentUser;
import com.example.expenses.ui.LoginView;
import com.example.expenses.usecase.UseCase;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import org.junit.jupiter.api.Test;

class UC001SignInTest extends KaribuTest {

    @Test
    @UseCase(id = "UC-001", businessRules = {"BR-002"})
    @SuppressWarnings("unchecked")
    void sign_in_with_selected_user_opens_claims_list() {
        UI.getCurrent().navigate(LoginView.class);

        ComboBox<AppUser> picker = _get(ComboBox.class, spec -> spec.withLabel("User"));
        picker.setValue(emma());
        _get(Button.class, spec -> spec.withText("Sign in")).click();

        assertThat(CurrentUser.get()).isEqualTo(emma());
        _get(ClaimsListView.class);
    }

    @Test
    @UseCase(id = "UC-001", scenario = "A1: No user selected")
    void sign_in_without_user_shows_message() {
        UI.getCurrent().navigate(LoginView.class);

        _get(Button.class, spec -> spec.withText("Sign in")).click();

        expectNotifications("Please pick a user first.");
        assertThat(CurrentUser.get()).isNull();
        _get(LoginView.class);
    }

    @Test
    @UseCase(id = "UC-001", scenario = "A2: Unauthenticated access to a protected page",
            businessRules = {"BR-001"})
    void unauthenticated_visitor_is_forwarded_to_login() {
        UI.getCurrent().navigate(ClaimsListView.class);

        _get(LoginView.class);
    }

    @Test
    @UseCase(id = "UC-001", scenario = "A3: Sign out")
    void sign_out_ends_session_and_returns_to_login() {
        signIn(emma());
        UI.getCurrent().navigate(ClaimsListView.class);

        _get(Button.class, spec -> spec.withText("Sign out")).click();

        assertThat(CurrentUser.get()).isNull();
        _get(LoginView.class);

        // protected pages stay unreachable after sign-out (BR-001)
        UI.getCurrent().navigate(ClaimsListView.class);
        _get(LoginView.class);
    }
}
