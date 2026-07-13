package com.example.expenses.ui;

import com.example.expenses.jooq.tables.records.AppUserRecord;
import com.vaadin.flow.server.VaadinSession;

/**
 * Session-scoped holder for the signed-in user. Login is a user picker by design;
 * authorization (not authentication) is what this application demonstrates.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AppUserRecord get() {
        return (AppUserRecord) VaadinSession.getCurrent().getAttribute(AppUserRecord.class.getName());
    }

    public static void set(AppUserRecord user) {
        VaadinSession.getCurrent().setAttribute(AppUserRecord.class.getName(), user);
    }

    public static boolean isSignedIn() {
        return get() != null;
    }
}
