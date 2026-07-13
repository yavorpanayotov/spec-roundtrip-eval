package com.example.expenses.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input data for creating or updating an expense item.
 */
public record ItemData(
        ExpenseCategory category,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        boolean hasReceipt) {
}
