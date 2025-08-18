package com.paynow.agentassist.strategy;

import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.RiskSignals;

import java.math.BigDecimal;
import java.util.List;

public interface DecisionStrategy {
  String getName();

  String getDescription();

  PaymentDecision makeDecision(
      PaymentDecisionRequest request,
      BigDecimal balance,
      RiskSignals riskSignals,
      List<String> reasons);
}
