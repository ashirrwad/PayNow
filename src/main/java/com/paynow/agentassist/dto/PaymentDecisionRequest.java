package com.paynow.agentassist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentDecisionRequest(
    @NotBlank(message = "Customer ID is required")
        @Pattern(regexp = "^c_[a-zA-Z0-9_]+$", message = "Customer ID must start with 'c_'")
        @JsonProperty("customerId")
        String customerId,
    @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
        @JsonProperty("amount")
        BigDecimal amount,
    @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        @JsonProperty("currency")
        String currency,
    @NotBlank(message = "Payee ID is required")
        @Pattern(regexp = "^p_[a-zA-Z0-9_]+$", message = "Payee ID must start with 'p_'")
        @JsonProperty("payeeId")
        String payeeId,
    @NotBlank(message = "Idempotency key is required")
        @Size(
            min = 10,
            max = 100,
            message = "Idempotency key must be between 10 and 100 characters")
        @JsonProperty("idempotencyKey")
        String idempotencyKey) {}
