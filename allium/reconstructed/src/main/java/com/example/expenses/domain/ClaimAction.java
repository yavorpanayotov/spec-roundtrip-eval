package com.example.expenses.domain;

/**
 * Operations a surface can offer on a claim; mirrors the {@code provides}
 * clauses of the ClaimDetail surface. Item-level operations share one guard
 * (owner + editable), represented by MANAGE_ITEMS.
 */
public enum ClaimAction {
    SUBMIT, WITHDRAW, DELETE, APPROVE, REJECT, REIMBURSE, MANAGE_ITEMS
}
