package com.example.expenses;

import static com.example.expenses.jooq.Tables.EXPENSE_CLAIM;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;

import com.example.expenses.domain.AppUser;
import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.repository.UserRepository;
import com.example.expenses.ui.ClaimDetailView;
import com.example.expenses.ui.ClaimsListView;
import com.example.expenses.ui.CurrentUser;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import java.math.BigDecimal;
import java.time.LocalDate;
import kotlin.jvm.functions.Function0;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Base class for server-side Karibu UI tests: boots the Spring context (with an
 * in-memory H2 migrated by Flyway) and mocks the Vaadin environment per test.
 */
@SpringBootTest
public abstract class KaribuTest {

    private static final Routes routes =
            new Routes().autoDiscoverViews("com.example.expenses.ui");

    @Autowired
    protected ApplicationContext appContext;

    @Autowired
    protected DSLContext db;

    @Autowired
    protected UserRepository users;

    @BeforeEach
    void mockVaadin() {
        Function0<UI> uiFactory = UI::new;
        MockVaadin.setup(uiFactory, new MockSpringServlet(routes, appContext, uiFactory));
    }

    @AfterEach
    void tearDownVaadin() {
        MockVaadin.tearDown();
        // Remove claims created during the test; items and log entries cascade.
        // Seeded users are left untouched.
        db.deleteFrom(EXPENSE_CLAIM).execute();
    }

    // ------------------------------------------------------------- users

    protected AppUser user(String email) {
        return users.findAll().stream()
                .filter(user -> user.email().equals(email))
                .findFirst()
                .orElseThrow();
    }

    protected AppUser emma() {
        return user("emma.employee@example.com");
    }

    protected AppUser erik() {
        return user("erik.employee@example.com");
    }

    protected AppUser mona() {
        return user("mona.manager@example.com");
    }

    protected AppUser frank() {
        return user("frank.finance@example.com");
    }

    protected void signIn(AppUser user) {
        CurrentUser.set(user);
    }

    // ------------------------------------------------------------- UI flows

    /** Creates a claim through the UI and returns its id; leaves the detail view open. */
    protected long createClaimViaUi(String title) {
        UI.getCurrent().navigate(ClaimsListView.class);
        _get(Button.class, spec -> spec.withText("New claim")).click();
        _get(TextField.class, spec -> spec.withLabel("Title")).setValue(title);
        _get(Button.class, spec -> spec.withText("Create")).click();
        return db.select(org.jooq.impl.DSL.max(EXPENSE_CLAIM.ID))
                .from(EXPENSE_CLAIM)
                .fetchSingle()
                .value1();
    }

    /** Adds an item through the item dialog on the currently open claim detail view. */
    @SuppressWarnings("unchecked")
    protected void addItemViaUi(ExpenseCategory category, String description,
            BigDecimal amount, LocalDate date, boolean receipt) {
        _get(Button.class, spec -> spec.withText("Add item")).click();
        fillItemDialog(category, description, amount, date, receipt);
        _get(Button.class, spec -> spec.withText("Save")).click();
    }

    @SuppressWarnings("unchecked")
    protected void fillItemDialog(ExpenseCategory category, String description,
            BigDecimal amount, LocalDate date, boolean receipt) {
        if (category != null) {
            _get(ComboBox.class, spec -> spec.withLabel("Category")).setValue(category);
        }
        if (description != null) {
            _get(TextField.class, spec -> spec.withLabel("Description")).setValue(description);
        }
        if (amount != null) {
            _get(BigDecimalField.class, spec -> spec.withLabel("Amount (EUR)")).setValue(amount);
        }
        if (date != null) {
            _get(DatePicker.class, spec -> spec.withLabel("Expense date")).setValue(date);
        }
        _get(Checkbox.class, spec -> spec.withLabel("Receipt attached")).setValue(receipt);
    }

    /** Navigates to the claim detail view (via the list, so a re-render is forced). */
    protected void openClaim(long claimId) {
        UI.getCurrent().navigate(ClaimsListView.class);
        UI.getCurrent().navigate(ClaimDetailView.class, claimId);
    }

    /**
     * Opens the claims list with a detour, because navigating to the route the UI is
     * already on is a no-op in Vaadin and would leave a stale view from a previous user.
     */
    protected void openClaimsList() {
        UI.getCurrent().navigate(com.example.expenses.ui.LoginView.class);
        UI.getCurrent().navigate(ClaimsListView.class);
    }
}
