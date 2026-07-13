package com.example.expenses.domain;

public enum ClaimAction {
    SUBMITTED("Submitted"),
    WITHDRAWN("Withdrawn"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    REIMBURSED("Reimbursed");

    private final String label;

    ClaimAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
