package com.example.expenses.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClaimSummary(
        long id,
        String title,
        String ownerName,
        ClaimStatus status,
        BigDecimal total,
        LocalDateTime submittedAt,
        LocalDateTime createdAt) {
}
