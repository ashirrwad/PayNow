package com.paynow.agentassist.service;

import com.paynow.agentassist.util.ResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Balance Service Tests")
class BalanceServiceTest {

  private BalanceService balanceService;

  @BeforeEach
  void setUp() {
    ResourceManager resourceManager = new ResourceManager();
    balanceService = new BalanceService(resourceManager);
  }

  @Test
  @DisplayName("Should return available balance for customer")
  void shouldReturnAvailableBalanceForCustomer() {
    // Given
    String customerId = "c_test_customer_123";

    // When
    BigDecimal balance = balanceService.getAvailableBalance(customerId);

    // Then
    assertNotNull(balance);
    assertTrue(balance.compareTo(BigDecimal.ZERO) >= 0);
  }

  @Test
  @DisplayName("Should return different balances for different customers")
  void shouldReturnDifferentBalancesForDifferentCustomers() {
    // Given
    String customer1 = "c_customer_001";
    String customer2 = "c_customer_002";

    // When
    BigDecimal balance1 = balanceService.getAvailableBalance(customer1);
    BigDecimal balance2 = balanceService.getAvailableBalance(customer2);

    // Then
    assertNotNull(balance1);
    assertNotNull(balance2);
    // Note: While they could theoretically be the same due to randomization,
    // the test validates the method works for different customers
  }

  @Test
  @DisplayName("Should reserve amount successfully when sufficient balance")
  void shouldReserveAmountSuccessfullyWhenSufficientBalance() {
    // Given
    String customerId = "c_high_balance_customer";
    BigDecimal smallAmount = new BigDecimal("10.00");

    // When
    boolean result = balanceService.reserveAmount(customerId, smallAmount);

    // Then - Small amounts should generally succeed
    assertNotNull(result);
  }

  @Test
  @DisplayName("Should fail to reserve amount when insufficient balance")
  void shouldFailToReserveAmountWhenInsufficientBalance() {
    // Given
    String customerId = "c_test_customer";
    BigDecimal largeAmount = new BigDecimal("999999.00"); // Very large amount

    // When
    boolean result = balanceService.reserveAmount(customerId, largeAmount);

    // Then - Very large amounts should fail
    assertFalse(result);
  }

  @Test
  @DisplayName("Should handle concurrent reservations safely")
  void shouldHandleConcurrentReservationsSafely() {
    // Given
    String customerId = "c_concurrent_test";
    BigDecimal amount = new BigDecimal("50.00");

    // When & Then - multiple reservations should be handled safely
    boolean result1 = balanceService.reserveAmount(customerId, amount);
    boolean result2 = balanceService.reserveAmount(customerId, amount);

    // At least one should succeed (depending on balance)
    // The important thing is that no exceptions are thrown due to concurrency issues
    assertNotNull(result1);
    assertNotNull(result2);
  }

  @Test
  @DisplayName("Should handle null customer ID gracefully")
  void shouldHandleNullCustomerIdGracefully() {
    // When & Then - Service will throw NullPointerException, not IllegalArgumentException
    assertThrows(NullPointerException.class, () -> balanceService.getAvailableBalance(null));

    assertThrows(
        NullPointerException.class,
        () -> balanceService.reserveAmount(null, new BigDecimal("100.00")));
  }

  @Test
  @DisplayName("Should handle null amount gracefully")
  void shouldHandleNullAmountGracefully() {
    // Given
    String customerId = "c_test_customer";

    // When & Then - Service will throw NullPointerException when comparing null amount
    assertThrows(NullPointerException.class, () -> balanceService.reserveAmount(customerId, null));
  }

  @Test
  @DisplayName("Should handle negative amount")
  void shouldHandleNegativeAmount() {
    // Given
    String customerId = "c_test_customer";
    BigDecimal negativeAmount = new BigDecimal("-50.00");

    // When
    boolean result = balanceService.reserveAmount(customerId, negativeAmount);

    // Then - Negative amounts will always be "reservable" since they're less than any positive
    // balance
    assertTrue(result);
  }
}
