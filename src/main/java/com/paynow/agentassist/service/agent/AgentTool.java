package com.paynow.agentassist.service.agent;

import com.paynow.agentassist.domain.AgentStep;
import com.paynow.agentassist.dto.PaymentDecisionRequest;

public interface AgentTool {
  String getName();

  AgentStep execute(PaymentDecisionRequest request, Object... params) throws Exception;

  boolean shouldRetry(Exception exception);
}
