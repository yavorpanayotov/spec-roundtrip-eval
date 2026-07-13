package com.example.expenses.domain;

import java.time.LocalDateTime;

public record HistoryEntry(
        String actorName,
        ClaimAction action,
        LocalDateTime occurredAt,
        String reason) {
}
