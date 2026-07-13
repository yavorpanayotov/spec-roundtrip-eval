package com.example.expenses.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business rule configuration.
 *
 * @param receiptThreshold     items above this amount need a receipt before submission (BR-013)
 * @param managerApprovalLimit claims above this total can only be approved by finance (BR-020)
 */
@ConfigurationProperties(prefix = "expenses")
public record ExpensesProperties(BigDecimal receiptThreshold, BigDecimal managerApprovalLimit) {
}
