package com.example.expenses.domain;

import java.math.BigDecimal;

/** Spec config block: the domain's tunable parameters. Amounts are EUR. */
public final class AppConfig {

    /** Items above this amount need a receipt (spec: receipt_required_over). */
    public static final BigDecimal RECEIPT_REQUIRED_OVER = new BigDecimal("50.00");

    /** Claims above this total need finance to approve (spec: manager_approval_limit). */
    public static final BigDecimal MANAGER_APPROVAL_LIMIT = new BigDecimal("5000.00");

    private AppConfig() {
    }
}
