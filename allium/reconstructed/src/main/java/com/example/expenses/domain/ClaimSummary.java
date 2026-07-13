package com.example.expenses.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row of the ClaimsList surface: exposes exactly title, owner.name, status,
 * total and submitted_at (plus the id needed to navigate to the detail).
 */
public record ClaimSummary(
        long id,
        String title,
        String ownerName,
        ClaimStatus status,
        BigDecimal total,
        LocalDateTime submittedAt) {
}
