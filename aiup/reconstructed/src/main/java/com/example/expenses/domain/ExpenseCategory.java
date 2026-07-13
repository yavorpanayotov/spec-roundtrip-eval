package com.example.expenses.domain;

public enum ExpenseCategory {
    TRAVEL("Travel"),
    MEALS("Meals"),
    ACCOMMODATION("Accommodation"),
    EQUIPMENT("Equipment"),
    OTHER("Other");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
