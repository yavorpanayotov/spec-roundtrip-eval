package com.example.expenses.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ItemView(
        long id,
        Category category,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        boolean hasReceipt) {

    /** Derived: items above config.receipt_required_over need a receipt. */
    public boolean requiresReceipt() {
        return amount.compareTo(AppConfig.RECEIPT_REQUIRED_OVER) > 0;
    }
}
