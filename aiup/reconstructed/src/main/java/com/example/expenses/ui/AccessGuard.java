package com.example.expenses.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Forwards unauthenticated visitors to the sign-in page (BR-001).
 */
@Component
public class AccessGuard implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit ->
                uiInit.getUI().addBeforeEnterListener(enter -> {
                    boolean toLogin = LoginView.class.equals(enter.getNavigationTarget());
                    if (!CurrentUser.isSignedIn() && !toLogin) {
                        enter.forwardTo(LoginView.class);
                    }
                }));
    }
}
