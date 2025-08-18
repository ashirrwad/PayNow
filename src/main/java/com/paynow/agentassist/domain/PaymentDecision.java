package com.paynow.agentassist.domain;

public enum PaymentDecision {
  ALLOW("allow"),
  REVIEW("review"),
  BLOCK("block");

  private final String value;

  PaymentDecision(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
