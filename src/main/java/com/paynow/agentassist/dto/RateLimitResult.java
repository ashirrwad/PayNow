package com.paynow.agentassist.dto;

public record RateLimitResult(boolean allowed, String errorMessage, String retryAfter) {

  public static RateLimitResult success() {
    return new RateLimitResult(true, null, null);
  }

  public static RateLimitResult rateLimited() {
    return new RateLimitResult(false, "Too many requests for this customer", "1 second");
  }
}
