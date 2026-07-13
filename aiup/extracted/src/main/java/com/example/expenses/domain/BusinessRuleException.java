package com.example.expenses.domain;

/**
 * Thrown when an operation violates a business rule (invalid state transition,
 * missing authorization, failed validation). The message is safe to show to users.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
