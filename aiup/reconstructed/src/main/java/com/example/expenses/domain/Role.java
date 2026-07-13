package com.example.expenses.domain;

public enum Role {
    EMPLOYEE("Employee"),
    MANAGER("Manager"),
    FINANCE("Finance");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
