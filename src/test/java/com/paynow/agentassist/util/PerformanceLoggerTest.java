package com.paynow.agentassist.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Performance Logger Tests")
class PerformanceLoggerTest {

  private PerformanceLogger performanceLogger;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    performanceLogger = new PerformanceLogger(meterRegistry);
  }

  @Test
  @DisplayName("Should log execution time for successful operations")
  void shouldLogExecutionTimeForSuccessfulOperations() {
    // When
    String result =
        performanceLogger.logExecutionTime(
            "testOperation",
            () -> {
              try {
                Thread.sleep(10); // Small delay
                return "success";
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
              }
            });

    // Then
    assertEquals("success", result);

    // Verify metrics were recorded
    assertNotNull(meterRegistry.find("operation.execution.time").timer());
    assertTrue(meterRegistry.find("operation.execution.time").timer().count() > 0);
  }

  @Test
  @DisplayName("Should log execution time with component for successful operations")
  void shouldLogExecutionTimeWithComponentForSuccessfulOperations() {
    // When
    String result =
        performanceLogger.logExecutionTime(
            "testOperation",
            "TestComponent",
            () -> {
              return "success";
            });

    // Then
    assertEquals("success", result);

    // Verify metrics were recorded with correct tags
    assertNotNull(
        meterRegistry
            .find("operation.execution.time")
            .tag("operation", "testOperation")
            .tag("component", "TestComponent")
            .timer());
  }

  @Test
  @DisplayName("Should handle exceptions and record failure metrics")
  void shouldHandleExceptionsAndRecordFailureMetrics() {
    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              performanceLogger.logExecutionTime(
                  "failingOperation",
                  () -> {
                    throw new RuntimeException("Test exception");
                  });
            });

    assertEquals("Test exception", exception.getMessage());

    // Verify failure counter was incremented
    assertNotNull(meterRegistry.find("operation.failures").counter());
    assertTrue(meterRegistry.find("operation.failures").counter().count() > 0);
  }

  @Test
  @DisplayName("Should log execution time for void operations")
  void shouldLogExecutionTimeForVoidOperations() {
    // Given
    final boolean[] executed = {false};

    // When
    performanceLogger.logExecutionTime(
        "voidOperation",
        () -> {
          executed[0] = true;
        });

    // Then
    assertTrue(executed[0]);

    // Verify metrics were recorded
    assertNotNull(meterRegistry.find("operation.execution.time").timer());
    assertTrue(meterRegistry.find("operation.execution.time").timer().count() > 0);
  }

  @Test
  @DisplayName("Should return null for void operations wrapped in supplier")
  void shouldReturnNullForVoidOperationsWrappedInSupplier() {
    // When
    Object result =
        performanceLogger.logExecutionTime(
            "voidOperation",
            () -> {
              // Do some work
              return null;
            });

    // Then
    assertNull(result);
  }
}
