package com.paynow.agentassist.dto;

import java.math.BigDecimal;

public class PaymentDecisionRequestBuilder {

  private String customerId;
  private BigDecimal amount;
  private String currency;
  private String payeeId;
  private String idempotencyKey;

  private PaymentDecisionRequestBuilder() {}

  public static PaymentDecisionRequestBuilder newBuilder() {
    return new PaymentDecisionRequestBuilder();
  }

  public static PaymentDecisionRequestBuilder from(PaymentDecisionRequest request) {
    return new PaymentDecisionRequestBuilder()
        .customerId(request.customerId())
        .amount(request.amount())
        .currency(request.currency())
        .payeeId(request.payeeId())
        .idempotencyKey(request.idempotencyKey());
  }

  public PaymentDecisionRequestBuilder customerId(String customerId) {
    this.customerId = customerId;
    return this;
  }

  public PaymentDecisionRequestBuilder amount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  public PaymentDecisionRequestBuilder amount(String amount) {
    this.amount = new BigDecimal(amount);
    return this;
  }

  public PaymentDecisionRequestBuilder amount(double amount) {
    this.amount = BigDecimal.valueOf(amount);
    return this;
  }

  public PaymentDecisionRequestBuilder currency(String currency) {
    this.currency = currency;
    return this;
  }

  public PaymentDecisionRequestBuilder payeeId(String payeeId) {
    this.payeeId = payeeId;
    return this;
  }

  public PaymentDecisionRequestBuilder idempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    return this;
  }

  public PaymentDecisionRequestBuilder generateIdempotencyKey() {
    this.idempotencyKey = "req_" + java.util.UUID.randomUUID().toString().replace("-", "");
    return this;
  }

  public PaymentDecisionRequest build() {
    if (customerId == null || customerId.trim().isEmpty()) {
      throw new IllegalArgumentException("Customer ID is required");
    }
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
    if (currency == null || currency.trim().isEmpty()) {
      throw new IllegalArgumentException("Currency is required");
    }
    if (payeeId == null || payeeId.trim().isEmpty()) {
      throw new IllegalArgumentException("Payee ID is required");
    }
    if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
      throw new IllegalArgumentException("Idempotency key is required");
    }

    return new PaymentDecisionRequest(
        customerId.trim(),
        amount,
        currency.trim().toUpperCase(),
        payeeId.trim(),
        idempotencyKey.trim());
  }

  public boolean isValid() {
    try {
      build();
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
