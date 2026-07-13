package com.example.expenses.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Monetary thresholds used by the business rules. Values come from configuration
 * so they can be changed without touching business logic.
 */
@ConfigurationProperties(prefix = "app.limits")
public record AppLimits(BigDecimal receiptRequiredOver, BigDecimal managerApprovalLimit) {
}
