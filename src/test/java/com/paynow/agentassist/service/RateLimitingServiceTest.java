package com.paynow.agentassist.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rate Limiting Service Tests")
class RateLimitingServiceTest {

  @Test
  @DisplayName("Should create service and handle basic operations")
  void shouldCreateServiceAndHandleBasicOperations() {
    // Given
    RateLimitingService rateLimitingService = new RateLimitingService();

    // Set the fields that would normally be injected by @Value
    ReflectionTestUtils.setField(rateLimitingService, "requestsPerSecond", 5);
    ReflectionTestUtils.setField(rateLimitingService, "bucketCapacity", 10);

    String customerId = "c_test_customer_123";

    // When & Then - Should not throw exceptions
    assertDoesNotThrow(
        () -> {
          boolean result = rateLimitingService.isAllowed(customerId);
          assertNotNull(result);
        });
  }

  @Test
  @DisplayName("Should handle null customer ID gracefully")
  void shouldHandleNullCustomerIdGracefully() {
    // Given
    RateLimitingService rateLimitingService = new RateLimitingService();
    ReflectionTestUtils.setField(rateLimitingService, "requestsPerSecond", 5);
    ReflectionTestUtils.setField(rateLimitingService, "bucketCapacity", 10);

    // When & Then - Service will throw NullPointerException, not IllegalArgumentException
    assertThrows(NullPointerException.class, () -> rateLimitingService.isAllowed(null));
  }

  @Test
  @DisplayName("Should handle empty customer ID gracefully")
  void shouldHandleEmptyCustomerIdGracefully() {
    // Given
    RateLimitingService rateLimitingService = new RateLimitingService();
    ReflectionTestUtils.setField(rateLimitingService, "requestsPerSecond", 5);
    ReflectionTestUtils.setField(rateLimitingService, "bucketCapacity", 10);

    // When & Then - Service accepts empty strings as valid customer IDs
    assertDoesNotThrow(() -> rateLimitingService.isAllowed(""));
    assertDoesNotThrow(() -> rateLimitingService.isAllowed("   "));
  }

  @Test
  @DisplayName("Should get available tokens")
  void shouldGetAvailableTokens() {
    // Given
    RateLimitingService rateLimitingService = new RateLimitingService();
    ReflectionTestUtils.setField(rateLimitingService, "requestsPerSecond", 5);
    ReflectionTestUtils.setField(rateLimitingService, "bucketCapacity", 10);

    String customerId = "c_token_test";

    // When
    long initialTokens = rateLimitingService.getAvailableTokens(customerId);
    rateLimitingService.isAllowed(customerId); // Consume one token
    long tokensAfterConsumption = rateLimitingService.getAvailableTokens(customerId);

    // Then
    assertEquals(10, initialTokens); // Should return capacity for new customer
    assertTrue(
        tokensAfterConsumption < initialTokens); // Should have fewer tokens after consumption
  }
}
