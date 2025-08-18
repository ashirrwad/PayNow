package com.paynow.agentassist.event;

import com.paynow.agentassist.domain.PaymentDecision;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentDecisionEvent(
    String eventId,
    String eventType,
    LocalDateTime timestamp,
    String customerId,
    BigDecimal amount,
    String currency,
    String payeeId,
    PaymentDecision decision,
    List<String> reasons,
    String requestId,
    String idempotencyKey) {
  public PaymentDecisionEvent(
      String eventId,
      String customerId,
      BigDecimal amount,
      String currency,
      String payeeId,
      PaymentDecision decision,
      List<String> reasons,
      String requestId,
      String idempotencyKey) {
    this(
        eventId,
        "payment.decided",
        LocalDateTime.now(),
        customerId,
        amount,
        currency,
        payeeId,
        decision,
        reasons,
        requestId,
        idempotencyKey);
  }
}
