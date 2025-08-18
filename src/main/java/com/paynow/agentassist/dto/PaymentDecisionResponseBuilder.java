package com.paynow.agentassist.dto;

import com.paynow.agentassist.domain.AgentStep;
import com.paynow.agentassist.domain.PaymentDecision;

import java.util.ArrayList;
import java.util.List;

public class PaymentDecisionResponseBuilder {

  private PaymentDecision decision;
  private List<String> reasons;
  private List<AgentStep> agentTrace;
  private String requestId;

  private PaymentDecisionResponseBuilder() {
    this.reasons = new ArrayList<>();
    this.agentTrace = new ArrayList<>();
  }

  public static PaymentDecisionResponseBuilder newBuilder() {
    return new PaymentDecisionResponseBuilder();
  }

  public static PaymentDecisionResponseBuilder from(PaymentDecisionResponse response) {
    return new PaymentDecisionResponseBuilder()
        .decision(response.decision())
        .reasons(response.reasons())
        .agentTrace(response.agentTrace())
        .requestId(response.requestId());
  }

  public PaymentDecisionResponseBuilder decision(PaymentDecision decision) {
    this.decision = decision;
    return this;
  }

  public PaymentDecisionResponseBuilder allow() {
    this.decision = PaymentDecision.ALLOW;
    return this;
  }

  public PaymentDecisionResponseBuilder block() {
    this.decision = PaymentDecision.BLOCK;
    return this;
  }

  public PaymentDecisionResponseBuilder review() {
    this.decision = PaymentDecision.REVIEW;
    return this;
  }

  public PaymentDecisionResponseBuilder reasons(List<String> reasons) {
    this.reasons = new ArrayList<>(reasons);
    return this;
  }

  public PaymentDecisionResponseBuilder addReason(String reason) {
    if (this.reasons == null) {
      this.reasons = new ArrayList<>();
    }
    this.reasons.add(reason);
    return this;
  }

  public PaymentDecisionResponseBuilder agentTrace(List<AgentStep> agentTrace) {
    this.agentTrace = new ArrayList<>(agentTrace);
    return this;
  }

  public PaymentDecisionResponseBuilder addTraceStep(String step, String detail) {
    if (this.agentTrace == null) {
      this.agentTrace = new ArrayList<>();
    }
    this.agentTrace.add(new AgentStep(step, detail));
    return this;
  }

  public PaymentDecisionResponseBuilder addTraceStep(AgentStep step) {
    if (this.agentTrace == null) {
      this.agentTrace = new ArrayList<>();
    }
    this.agentTrace.add(step);
    return this;
  }

  public PaymentDecisionResponseBuilder requestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  public PaymentDecisionResponseBuilder generateRequestId() {
    this.requestId =
        "req_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return this;
  }

  public PaymentDecisionResponse build() {
    if (decision == null) {
      throw new IllegalArgumentException("Decision is required");
    }
    if (requestId == null || requestId.trim().isEmpty()) {
      throw new IllegalArgumentException("Request ID is required");
    }

    return new PaymentDecisionResponse(
        decision,
        reasons != null ? List.copyOf(reasons) : List.of(),
        agentTrace != null ? List.copyOf(agentTrace) : List.of(),
        requestId.trim());
  }

  public boolean isValid() {
    try {
      build();
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
