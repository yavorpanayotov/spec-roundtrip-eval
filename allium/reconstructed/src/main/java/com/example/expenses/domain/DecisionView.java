package com.example.expenses.domain;

import java.time.LocalDateTime;

/** Entry of the claim's append-only decision log, as exposed by ClaimDetail. */
public record DecisionView(
        DecisionAction action,
        String actorName,
        LocalDateTime occurredAt,
        String reason) {
}
