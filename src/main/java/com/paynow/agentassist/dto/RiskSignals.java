package com.paynow.agentassist.dto;

public record RiskSignals(
    int recentDisputes,
    boolean deviceChange,
    boolean velocityViolation,
    int dailyTransactionCount,
    String riskScore) {}
