package com.example.expenses.ui;

import com.example.expenses.db.Database;
import com.example.expenses.service.ExpenseService;

import java.time.Clock;

/** Lazily wires the single service instance the UI talks to. */
public final class AppContext {

    private static final String JDBC_URL = "jdbc:h2:mem:expenses;DB_CLOSE_DELAY=-1";

    private static volatile ExpenseService service;

    private AppContext() {
    }

    public static ExpenseService service() {
        if (service == null) {
            synchronized (AppContext.class) {
                if (service == null) {
                    service = new ExpenseService(Database.connect(JDBC_URL),
                            Clock.systemDefaultZone());
                }
            }
        }
        return service;
    }
}
