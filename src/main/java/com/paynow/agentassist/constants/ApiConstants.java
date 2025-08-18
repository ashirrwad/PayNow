package com.paynow.agentassist.constants;

public final class ApiConstants {

  private ApiConstants() {
    // Utility class
  }

  // Error Codes
  public static final String ERROR_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
  public static final String ERROR_INTERNAL_SERVER = "INTERNAL_SERVER_ERROR";

  // Error Messages
  public static final String MSG_RATE_LIMIT_EXCEEDED = "Rate limit exceeded";
  public static final String MSG_INTERNAL_SERVER_ERROR = "An unexpected error occurred";

  // Success Messages
  public static final String MSG_PAYMENT_PROCESSED = "Payment decision processed successfully";
  public static final String MSG_METRICS_RETRIEVED = "Metrics retrieved successfully";

  // Metric Names
  public static final String METRIC_PAYMENT_REQUESTS_TOTAL = "payment_requests_total";
  public static final String METRIC_PAYMENT_DECISIONS_TOTAL = "payment_decisions_total";
  public static final String METRIC_PAYMENT_REQUEST_DURATION = "payment_request_duration";
  public static final String METRIC_REQUEST_DURATION = "request_duration";
  public static final String METRIC_OPERATION_EXECUTION_TIME = "operation.execution.time";
  public static final String METRIC_OPERATION_FAILURES = "operation.failures";

  // Decision Values
  public static final String DECISION_ALLOW = "allow";
  public static final String DECISION_BLOCK = "block";
  public static final String DECISION_REVIEW = "review";

  // Headers
  public static final String HEADER_API_KEY = "X-API-Key";
  public static final String HEADER_RETRY_AFTER = "Retry-After";

  // Reason Codes
  public static final String REASON_SYSTEM_ERROR = "system_error";
  public static final String REASON_INSUFFICIENT_FUNDS = "insufficient_funds";
}
