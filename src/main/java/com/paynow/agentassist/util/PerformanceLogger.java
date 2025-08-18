package com.paynow.agentassist.util;

import com.paynow.agentassist.constants.ApiConstants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
public class PerformanceLogger {

  private static final Logger logger = LoggerFactory.getLogger(PerformanceLogger.class);
  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

  public PerformanceLogger(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public <T> T logExecutionTime(String operationName, Supplier<T> operation) {
    return logExecutionTime(operationName, "default", operation);
  }

  public <T> T logExecutionTime(String operationName, String component, Supplier<T> operation) {
    Timer timer = getOrCreateTimer(operationName, component);
    Timer.Sample sample = Timer.start(meterRegistry);
    long startTime = System.nanoTime();

    try {
      logger.debug("Starting operation: {}", operationName);
      T result = operation.get();

      long executionTime = System.nanoTime() - startTime;
      double executionTimeMs = executionTime / 1_000_000.0;

      sample.stop(timer);

      if (executionTimeMs > 100) {
        logger.warn("Slow operation detected: {} took {} ms", operationName, executionTimeMs);
      } else {
        logger.debug("Operation {} completed in {} ms", operationName, executionTimeMs);
      }

      return result;
    } catch (Exception e) {
      sample.stop(timer);

      meterRegistry
          .counter(
              ApiConstants.METRIC_OPERATION_FAILURES,
              "operation",
              operationName,
              "component",
              component,
              "exception",
              e.getClass().getSimpleName())
          .increment();

      logger.error("Operation {} failed: {}", operationName, e.getMessage());
      throw e;
    }
  }

  public void logExecutionTime(String operationName, Runnable operation) {
    logExecutionTime(
        operationName,
        () -> {
          operation.run();
          return null;
        });
  }

  private Timer getOrCreateTimer(String operationName, String component) {
    String metricName = component + "." + operationName;
    return timers.computeIfAbsent(
        metricName,
        name ->
            Timer.builder(ApiConstants.METRIC_OPERATION_EXECUTION_TIME)
                .tag("operation", operationName)
                .tag("component", component)
                .description("Operation execution time")
                .register(meterRegistry));
  }
}
