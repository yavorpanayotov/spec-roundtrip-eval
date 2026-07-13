package com.example.expenses.ui;

import com.example.expenses.domain.AppUser;
import com.vaadin.flow.server.VaadinSession;

/**
 * Holds the signed-in user in the Vaadin session (UC-001).
 */
public final class CurrentUser {

    private static final String ATTRIBUTE = AppUser.class.getName();

    private CurrentUser() {
    }

    public static AppUser get() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (AppUser) session.getAttribute(ATTRIBUTE);
    }

    public static void set(AppUser user) {
        VaadinSession.getCurrent().setAttribute(ATTRIBUTE, user);
    }

    public static void clear() {
        VaadinSession.getCurrent().setAttribute(ATTRIBUTE, null);
    }

    public static boolean isSignedIn() {
        return get() != null;
    }
}
