package com.paynow.agentassist.service;

import com.paynow.agentassist.dto.PaymentDecisionRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class RiskAssessmentService {

  private final Random random = new Random();

  public Map<String, Object> assessRisk(PaymentDecisionRequest request) {
    Map<String, Object> riskSignals = new HashMap<>();

    int recentDisputes = calculateRecentDisputes(request.customerId());
    boolean deviceChange = checkDeviceChange(request.customerId());
    boolean suspiciousPattern = detectSuspiciousPattern(request);
    double riskScore = calculateRiskScore(request, recentDisputes, deviceChange, suspiciousPattern);

    riskSignals.put("recent_disputes", recentDisputes);
    riskSignals.put("device_change", deviceChange);
    riskSignals.put("suspicious_pattern", suspiciousPattern);
    riskSignals.put("risk_score", String.format("%.2f", riskScore));

    return riskSignals;
  }

  private int calculateRecentDisputes(String customerId) {
    int hash = Math.abs(customerId.hashCode());
    return hash % 3;
  }

  private boolean checkDeviceChange(String customerId) {
    int hash = Math.abs(customerId.hashCode());
    return (hash % 5) == 0;
  }

  private boolean detectSuspiciousPattern(PaymentDecisionRequest request) {
    if (request.amount().compareTo(new BigDecimal("999.99")) == 0) {
      return true;
    }

    if (request.payeeId().contains("suspicious")) {
      return true;
    }

    return random.nextDouble() < 0.1;
  }

  private double calculateRiskScore(
      PaymentDecisionRequest request,
      int recentDisputes,
      boolean deviceChange,
      boolean suspiciousPattern) {
    double score = 0.0;

    score += recentDisputes * 0.3;

    if (deviceChange) {
      score += 0.25;
    }

    if (suspiciousPattern) {
      score += 0.4;
    }

    if (request.amount().compareTo(new BigDecimal("500")) > 0) {
      score += 0.15;
    }

    if (request.amount().compareTo(new BigDecimal("1000")) > 0) {
      score += 0.25;
    }

    return Math.min(score, 1.0);
  }
}
