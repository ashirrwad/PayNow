package com.paynow.agentassist.service;

import com.paynow.agentassist.constants.ApiConstants;
import com.paynow.agentassist.domain.PaymentDecision;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Metrics Service Tests")
class MetricsServiceTest {

  private MetricsService metricsService;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metricsService = new MetricsService(meterRegistry);
  }

  @Test
  @DisplayName("Should increment request counter")
  void shouldIncrementRequestCounter() {
    // When
    metricsService.incrementRequestCounter();
    metricsService.incrementRequestCounter();

    // Then
    assertEquals(
        2.0, meterRegistry.find(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL).counter().count());
  }

  @Test
  @DisplayName("Should record payment decisions")
  void shouldRecordPaymentDecisions() {
    // When
    metricsService.recordPaymentDecision(PaymentDecision.ALLOW);
    metricsService.recordPaymentDecision(PaymentDecision.BLOCK);
    metricsService.recordPaymentDecision(PaymentDecision.ALLOW);

    // Then
    assertEquals(
        2.0,
        meterRegistry
            .find(ApiConstants.METRIC_PAYMENT_DECISIONS_TOTAL)
            .tag("decision", ApiConstants.DECISION_ALLOW)
            .counter()
            .count());
    assertEquals(
        1.0,
        meterRegistry
            .find(ApiConstants.METRIC_PAYMENT_DECISIONS_TOTAL)
            .tag("decision", ApiConstants.DECISION_BLOCK)
            .counter()
            .count());
  }

  @Test
  @DisplayName("Should record request duration")
  void shouldRecordRequestDuration() {
    // Given
    long startTime = System.nanoTime() - 1_000_000; // 1ms ago

    // When
    metricsService.recordRequestDuration(startTime);

    // Then
    assertTrue(
        meterRegistry.find(ApiConstants.METRIC_PAYMENT_REQUEST_DURATION).timer().count() > 0);
    assertTrue(
        meterRegistry
                .find(ApiConstants.METRIC_PAYMENT_REQUEST_DURATION)
                .timer()
                .totalTime(TimeUnit.NANOSECONDS)
            > 0);
  }

  @Test
  @DisplayName("Should get system metrics")
  void shouldGetSystemMetrics() {
    // Given
    metricsService.incrementRequestCounter();
    metricsService.recordPaymentDecision(PaymentDecision.ALLOW);
    metricsService.recordRequestDuration(System.nanoTime() - 1_000_000);

    // When
    Map<String, Object> metrics = metricsService.getSystemMetrics();

    // Then
    assertNotNull(metrics);
    assertEquals(1.0, metrics.get(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL));
    assertEquals(1.0, metrics.get("payment_decisions_allow"));
    assertNotNull(metrics.get("payment_request_mean_latency_ms"));
    assertNotNull(metrics.get("payment_request_max_latency_ms"));
    assertNotNull(metrics.get("payment_request_count"));
  }

  @Test
  @DisplayName("Should handle empty metrics gracefully")
  void shouldHandleEmptyMetricsGracefully() {
    // When
    Map<String, Object> metrics = metricsService.getSystemMetrics();

    // Then
    assertNotNull(metrics);
    // Metrics should be empty or have default values since no operations were performed
  }

  @Test
  @DisplayName("Should record multiple decision types")
  void shouldRecordMultipleDecisionTypes() {
    // When
    metricsService.recordPaymentDecision(PaymentDecision.ALLOW);
    metricsService.recordPaymentDecision(PaymentDecision.BLOCK);
    metricsService.recordPaymentDecision(PaymentDecision.REVIEW);

    // When
    Map<String, Object> metrics = metricsService.getSystemMetrics();

    // Then
    assertEquals(1.0, metrics.get("payment_decisions_allow"));
    assertEquals(1.0, metrics.get("payment_decisions_block"));
    assertEquals(1.0, metrics.get("payment_decisions_review"));
  }
}
