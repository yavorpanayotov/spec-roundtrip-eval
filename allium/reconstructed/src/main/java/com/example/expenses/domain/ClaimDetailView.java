package com.example.expenses.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * The ClaimDetail surface contract: title, status, total, decision_reason
 * (present only when rejected), items and the decision log.
 */
public record ClaimDetailView(
        long id,
        String title,
        ClaimStatus status,
        BigDecimal total,
        String decisionReason,
        List<ItemView> items,
        List<DecisionView> decisions) {
}
