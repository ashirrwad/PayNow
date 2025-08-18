package com.paynow.agentassist.strategy;

import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.RiskSignals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DefaultDecisionStrategy implements DecisionStrategy {

  @Override
  public String getName() {
    return "default";
  }

  @Override
  public String getDescription() {
    return "Standard decision algorithm with balanced risk assessment";
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

    if (request.amount().compareTo(new BigDecimal("100")) > 0) {
      reasons.add("amount_above_daily_threshold");
    }

    if (riskSignals.recentDisputes() > 0) {
      reasons.add("recent_disputes");
    }

    if (riskSignals.deviceChange()) {
      reasons.add("device_change_detected");
    }

    if (riskSignals.velocityViolation()) {
      reasons.add("velocity_violation");
    }

    if (riskSignals.dailyTransactionCount() > 15) {
      reasons.add("high_transaction_frequency");
    }

    if ("HIGH".equals(riskSignals.riskScore()) || riskSignals.recentDisputes() >= 2) {
      return PaymentDecision.BLOCK;
    }

    if ("MEDIUM".equals(riskSignals.riskScore()) || reasons.size() >= 2) {
      return PaymentDecision.REVIEW;
    }

    if (reasons.size() == 1) {
      return PaymentDecision.REVIEW;
    }

    return PaymentDecision.ALLOW;
  }
}
