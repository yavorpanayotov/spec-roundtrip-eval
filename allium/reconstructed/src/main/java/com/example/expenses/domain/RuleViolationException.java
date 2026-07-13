package com.example.expenses.domain;

/**
 * Thrown when a rule's {@code requires} precondition does not hold for the
 * attempted operation. The operation has no effect in that case.
 */
public class RuleViolationException extends RuntimeException {

    public RuleViolationException(String message) {
        super(message);
    }
}
