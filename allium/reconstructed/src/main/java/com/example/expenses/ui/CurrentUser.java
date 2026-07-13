package com.example.expenses.ui;

import com.example.expenses.domain.User;
import com.vaadin.flow.server.VaadinSession;

/**
 * The signed-in user for this session. Sign-in is a demo user picker by
 * design: the application demonstrates role-based authorization, not
 * authentication (spec scope note).
 */
public final class CurrentUser {

    private static final String KEY = CurrentUser.class.getName();

    private CurrentUser() {
    }

    public static User get() {
        return (User) VaadinSession.getCurrent().getAttribute(KEY);
    }

    public static void set(User user) {
        VaadinSession.getCurrent().setAttribute(KEY, user);
    }

    public static void clear() {
        VaadinSession.getCurrent().setAttribute(KEY, null);
    }
}
