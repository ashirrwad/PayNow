package com.paynow.agentassist.service.agent;

import com.paynow.agentassist.dto.PaymentDecisionRequest;

public interface PaymentDecisionProcessor {
  PaymentDecisionAgent.AgentDecisionResult processPayment(PaymentDecisionRequest request);

  PaymentDecisionAgent.AgentDecisionResult processPaymentWithStrategy(
      PaymentDecisionRequest request, String strategyName);
}
