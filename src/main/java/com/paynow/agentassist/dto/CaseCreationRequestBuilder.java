package com.paynow.agentassist.dto;

import java.math.BigDecimal;

public class CaseCreationRequestBuilder {

  private String customerId;
  private BigDecimal amount;
  private String currency;
  private String payeeId;
  private String reason;
  private String priority;

  private CaseCreationRequestBuilder() {}

  public static CaseCreationRequestBuilder newBuilder() {
    return new CaseCreationRequestBuilder();
  }

  public static CaseCreationRequestBuilder from(CaseCreationRequest request) {
    return new CaseCreationRequestBuilder()
        .customerId(request.customerId())
        .amount(request.amount())
        .currency(request.currency())
        .payeeId(request.payeeId())
        .reason(request.reason())
        .priority(request.priority());
  }

  public static CaseCreationRequestBuilder fromPaymentRequest(
      PaymentDecisionRequest paymentRequest) {
    return new CaseCreationRequestBuilder()
        .customerId(paymentRequest.customerId())
        .amount(paymentRequest.amount())
        .currency(paymentRequest.currency())
        .payeeId(paymentRequest.payeeId());
  }

  public CaseCreationRequestBuilder customerId(String customerId) {
    this.customerId = customerId;
    return this;
  }

  public CaseCreationRequestBuilder amount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  public CaseCreationRequestBuilder amount(String amount) {
    this.amount = new BigDecimal(amount);
    return this;
  }

  public CaseCreationRequestBuilder amount(double amount) {
    this.amount = BigDecimal.valueOf(amount);
    return this;
  }

  public CaseCreationRequestBuilder currency(String currency) {
    this.currency = currency;
    return this;
  }

  public CaseCreationRequestBuilder payeeId(String payeeId) {
    this.payeeId = payeeId;
    return this;
  }

  public CaseCreationRequestBuilder reason(String reason) {
    this.reason = reason;
    return this;
  }

  public CaseCreationRequestBuilder priority(String priority) {
    this.priority = priority;
    return this;
  }

  public CaseCreationRequestBuilder highPriority() {
    this.priority = "HIGH";
    return this;
  }

  public CaseCreationRequestBuilder mediumPriority() {
    this.priority = "MEDIUM";
    return this;
  }

  public CaseCreationRequestBuilder lowPriority() {
    this.priority = "LOW";
    return this;
  }

  public CaseCreationRequest build() {
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
    if (reason == null || reason.trim().isEmpty()) {
      throw new IllegalArgumentException("Reason is required");
    }
    if (priority == null || priority.trim().isEmpty()) {
      this.priority = "MEDIUM"; // Default priority
    }

    return new CaseCreationRequest(
        customerId.trim(),
        amount,
        currency.trim().toUpperCase(),
        payeeId.trim(),
        reason.trim(),
        priority.trim().toUpperCase());
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
