package com.paynow.agentassist.service;

import com.paynow.agentassist.constants.ApiConstants;
import com.paynow.agentassist.domain.PaymentDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class MetricsService {

  private final Counter requestCounter;
  private final Timer requestTimer;
  private final MeterRegistry meterRegistry;

  public MetricsService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.requestCounter =
        Counter.builder(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL)
            .description("Total payment decision requests")
            .register(meterRegistry);
    this.requestTimer =
        Timer.builder(ApiConstants.METRIC_PAYMENT_REQUEST_DURATION)
            .description("Payment request processing time")
            .register(meterRegistry);
  }

  public void incrementRequestCounter() {
    requestCounter.increment();
  }

  public void recordRequestDuration(long startTimeNanos) {
    long duration = System.nanoTime() - startTimeNanos;
    requestTimer.record(duration, TimeUnit.NANOSECONDS);
  }

  public void recordPaymentDecision(PaymentDecision decision) {
    Counter.builder(ApiConstants.METRIC_PAYMENT_DECISIONS_TOTAL)
        .tag("decision", decision.getValue())
        .register(meterRegistry)
        .increment();
  }

  public Map<String, Object> getSystemMetrics() {
    Map<String, Object> metrics = new HashMap<>();

    Counter requestCounter =
        meterRegistry.find(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL).counter();
    if (requestCounter != null) {
      metrics.put(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL, requestCounter.count());
    }

    Counter allowDecisions =
        meterRegistry
            .find(ApiConstants.METRIC_PAYMENT_DECISIONS_TOTAL)
            .tag("decision", ApiConstants.DECISION_ALLOW)
            .counter();
    if (allowDecisions != null) {
      metrics.put("payment_decisions_allow", allowDecisions.count());
    }

    Counter reviewDecisions =
        meterRegistry
            .find(ApiConstants.METRIC_PAYMENT_DECISIONS_TOTAL)
            .tag("decision", ApiConstants.DECISION_REVIEW)
            .counter();
    if (reviewDecisions != null) {
      metrics.put("payment_decisions_review", reviewDecisions.count());
    }

    Counter blockDecisions =
        meterRegistry
            .find(ApiConstants.METRIC_PAYMENT_DECISIONS_TOTAL)
            .tag("decision", ApiConstants.DECISION_BLOCK)
            .counter();
    if (blockDecisions != null) {
      metrics.put("payment_decisions_block", blockDecisions.count());
    }

    Timer requestTimer = meterRegistry.find(ApiConstants.METRIC_PAYMENT_REQUEST_DURATION).timer();
    if (requestTimer != null) {
      metrics.put("payment_request_mean_latency_ms", requestTimer.mean(TimeUnit.MILLISECONDS));
      metrics.put("payment_request_max_latency_ms", requestTimer.max(TimeUnit.MILLISECONDS));
      metrics.put("payment_request_count", requestTimer.count());
    }

    return metrics;
  }
}
