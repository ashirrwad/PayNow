package com.paynow.agentassist.dto;

import java.math.BigDecimal;

public record CaseCreationRequest(
    String customerId,
    BigDecimal amount,
    String currency,
    String payeeId,
    String reason,
    String priority) {}
