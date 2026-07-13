package com.example.expenses.service;

/**
 * Thrown when an action violates a business rule; the message is shown to the user.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
