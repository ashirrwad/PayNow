package com.paynow.agentassist.controller;

import com.paynow.agentassist.constants.ApiConstants;
import com.paynow.agentassist.dto.ApiResponse;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.PaymentDecisionResponse;
import com.paynow.agentassist.dto.RateLimitResult;
import com.paynow.agentassist.service.MetricsService;
import com.paynow.agentassist.service.payment.PaymentDecisionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

  private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

  private final PaymentDecisionService paymentDecisionService;
  private final MetricsService metricsService;

  public PaymentController(
      PaymentDecisionService paymentDecisionService, MetricsService metricsService) {
    this.paymentDecisionService = paymentDecisionService;
    this.metricsService = metricsService;
  }

  @PostMapping("/decide")
  public ResponseEntity<ApiResponse<PaymentDecisionResponse>> decidePayment(
      @Valid @RequestBody PaymentDecisionRequest request) {
    long startTime = System.nanoTime();

    try {
      metricsService.incrementRequestCounter();

      RateLimitResult rateLimitResult = paymentDecisionService.checkRateLimit(request);
      if (!rateLimitResult.allowed()) {
        ApiResponse<PaymentDecisionResponse> errorResponse =
            ApiResponse.error(
                ApiConstants.ERROR_RATE_LIMIT_EXCEEDED,
                ApiConstants.MSG_RATE_LIMIT_EXCEEDED,
                rateLimitResult.errorMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(ApiConstants.HEADER_RETRY_AFTER, rateLimitResult.retryAfter().toString())
            .body(errorResponse);
      }

      PaymentDecisionResponse response = paymentDecisionService.processPaymentDecision(request);
      metricsService.recordPaymentDecision(response.decision());

      return ResponseEntity.ok(ApiResponse.success(response, ApiConstants.MSG_PAYMENT_PROCESSED));

    } catch (Exception e) {
      logger.error("Unexpected error processing payment decision for request:", e);
      ApiResponse<PaymentDecisionResponse> errorResponse =
          ApiResponse.error(
              ApiConstants.ERROR_INTERNAL_SERVER,
              ApiConstants.MSG_INTERNAL_SERVER_ERROR,
              e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    } finally {
      metricsService.recordRequestDuration(startTime);
    }
  }
}
