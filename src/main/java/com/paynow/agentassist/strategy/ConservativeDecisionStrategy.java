package com.paynow.agentassist.strategy;

import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.RiskSignals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ConservativeDecisionStrategy implements DecisionStrategy {

  @Override
  public String getName() {
    return "conservative";
  }

  @Override
  public String getDescription() {
    return "Conservative approach with lower risk tolerance";
  }

  @Override
  public PaymentDecision makeDecision(
      PaymentDecisionRequest request,
      BigDecimal balance,
      RiskSignals riskSignals,
      List<String> reasons) {

    if (balance.compareTo(request.amount()) < 0) {
      reasons.add("insufficient_balance");
      return PaymentDecision.BLOCK;
    }

    if (request.amount().compareTo(new BigDecimal("50")) > 0) {
      reasons.add("amount_above_conservative_threshold");
    }

    if (riskSignals.recentDisputes() > 0) {
      reasons.add("recent_disputes");
      return PaymentDecision.BLOCK;
    }

    if (riskSignals.deviceChange()) {
      reasons.add("device_change_detected");
    }

    if (riskSignals.velocityViolation()) {
      reasons.add("velocity_violation");
      return PaymentDecision.BLOCK;
    }

    if (riskSignals.dailyTransactionCount() > 10) {
      reasons.add("high_transaction_frequency");
    }

    if ("HIGH".equals(riskSignals.riskScore()) || "MEDIUM".equals(riskSignals.riskScore())) {
      return PaymentDecision.BLOCK;
    }

    if (reasons.size() >= 1) {
      return PaymentDecision.REVIEW;
    }

    return PaymentDecision.ALLOW;
  }
}
