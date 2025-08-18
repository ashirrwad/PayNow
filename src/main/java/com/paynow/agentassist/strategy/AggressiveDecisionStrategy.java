package com.paynow.agentassist.strategy;

import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.RiskSignals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AggressiveDecisionStrategy implements DecisionStrategy {

  @Override
  public String getName() {
    return "aggressive";
  }

  @Override
  public String getDescription() {
    return "Aggressive approach with higher risk tolerance for better UX";
  }

  @Override
  public PaymentDecision makeDecision(
      PaymentDecisionRequest request,
      BigDecimal balance,
      RiskSignals riskSignals,
      List<String> reasons) {

    // Check balance constraints
    if (balance.compareTo(request.amount()) < 0) {
      reasons.add("insufficient_balance");
      return PaymentDecision.BLOCK;
    }

    // Higher thresholds for aggressive approach
    if (request.amount().compareTo(new BigDecimal("500")) > 0) {
      reasons.add("amount_above_aggressive_threshold");
    }

    // More lenient risk assessment
    if (riskSignals.recentDisputes() >= 3) {
      reasons.add("multiple_recent_disputes");
    }

    if (riskSignals.deviceChange() && riskSignals.recentDisputes() > 0) {
      reasons.add("device_change_with_disputes");
    }

    if (riskSignals.velocityViolation() && riskSignals.dailyTransactionCount() > 20) {
      reasons.add("severe_velocity_violation");
    }

    if (riskSignals.dailyTransactionCount() > 25) {
      reasons.add("excessive_transaction_frequency");
    }

    // Aggressive decision logic - favor allowing transactions
    if ("HIGH".equals(riskSignals.riskScore()) && riskSignals.recentDisputes() >= 3) {
      return PaymentDecision.BLOCK;
    }

    if ("HIGH".equals(riskSignals.riskScore()) && reasons.size() >= 2) {
      return PaymentDecision.REVIEW;
    }

    if (reasons.size() >= 3) {
      return PaymentDecision.REVIEW;
    }

    return PaymentDecision.ALLOW;
  }
}
