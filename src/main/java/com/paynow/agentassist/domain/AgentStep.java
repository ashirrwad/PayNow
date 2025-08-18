package com.paynow.agentassist.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentStep(@JsonProperty("step") String step, @JsonProperty("detail") String detail) {}
