package com.paynow.agentassist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynow.agentassist.domain.AgentStep;
import com.paynow.agentassist.domain.PaymentDecision;

import java.util.List;

public record PaymentDecisionResponse(
    @JsonProperty("decision") PaymentDecision decision,
    @JsonProperty("reasons") List<String> reasons,
    @JsonProperty("agentTrace") List<AgentStep> agentTrace,
    @JsonProperty("requestId") String requestId) {}
