package com.example.expenses.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClaimItem(
        long id,
        ExpenseCategory category,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        boolean hasReceipt) {
}
