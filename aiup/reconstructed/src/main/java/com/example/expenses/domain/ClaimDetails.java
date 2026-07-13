package com.example.expenses.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClaimDetails(
        long id,
        String title,
        long ownerId,
        String ownerName,
        ClaimStatus status,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        LocalDateTime reimbursedAt,
        String decisionReason) {
}
