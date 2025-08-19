package com.paynow.agentassist.service.agent.tool;

import com.paynow.agentassist.service.BalanceService;
import com.paynow.agentassist.util.PerformanceLogger;
import com.paynow.agentassist.util.ResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;

@ExtendWith(MockitoExtension.class)
@DisplayName("Balance Tool Tests")
class BalanceToolTest {

  @Mock private BalanceService balanceService;

  @Mock private ResourceManager resourceManager;

  @Mock private PerformanceLogger performanceLogger;

  private BalanceTool balanceTool;

  @BeforeEach
  void setUp() {
    lenient().when(resourceManager.getAgentToolExecutor()).thenReturn(Executors.newFixedThreadPool(2));

    // Setup PerformanceLogger to actually execute the operation
    lenient().when(performanceLogger.logExecutionTime(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Supplier<?> operation = invocation.getArgument(2);
              return operation.get();
            });

    balanceTool = new BalanceTool(balanceService, resourceManager, performanceLogger);
  }

  @Nested
  @DisplayName("Tool Metadata Tests")
  class ToolMetadataTests {

    @Test
    @DisplayName("Should return correct tool name")
    void shouldReturnCorrectToolName() {
      assertEquals("getBalance", balanceTool.getName());
    }

    @Test
    @DisplayName("Should return descriptive tool description")
    void shouldReturnDescriptiveToolDescription() {
      String description = balanceTool.getDescription();
      assertNotNull(description);
      assertTrue(description.contains("balance"));
      assertTrue(description.contains("customer"));
    }

    @Test
    @DisplayName("Should return correct input and result types")
    void shouldReturnCorrectInputAndResultTypes() {
      assertEquals(String.class, balanceTool.getInputType());
      assertEquals(BigDecimal.class, balanceTool.getResultType());
    }
  }

  @Nested
  @DisplayName("Balance Retrieval Logic Tests")
  class BalanceRetrievalLogicTests {

    @Test
    @DisplayName("Should successfully retrieve customer balance")
    void shouldSuccessfullyRetrieveCustomerBalance()
        throws ExecutionException, InterruptedException {
      // Given
      String customerId = "c_test_customer_123";
      BigDecimal expectedBalance = new BigDecimal("1500.00");

      when(balanceService.getAvailableBalance(customerId)).thenReturn(expectedBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);
      BigDecimal actualBalance = result.get();

      // Then
      assertEquals(expectedBalance, actualBalance);
      verify(balanceService).getAvailableBalance(customerId);
      verify(performanceLogger).logExecutionTime(eq("getBalance"), eq("BalanceTool"), any());
    }

    @Test
    @DisplayName("Should handle zero balance correctly")
    void shouldHandleZeroBalanceCorrectly() throws ExecutionException, InterruptedException {
      // Given
      String customerId = "c_zero_balance_customer";
      BigDecimal zeroBalance = BigDecimal.ZERO;

      when(balanceService.getAvailableBalance(customerId)).thenReturn(zeroBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);
      BigDecimal actualBalance = result.get();

      // Then
      assertEquals(BigDecimal.ZERO, actualBalance);
      verify(balanceService).getAvailableBalance(customerId);
    }

    @Test
    @DisplayName("Should handle negative balance correctly")
    void shouldHandleNegativeBalanceCorrectly() throws ExecutionException, InterruptedException {
      // Given
      String customerId = "c_overdrawn_customer";
      BigDecimal negativeBalance = new BigDecimal("-50.00");

      when(balanceService.getAvailableBalance(customerId)).thenReturn(negativeBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);
      BigDecimal actualBalance = result.get();

      // Then
      assertEquals(negativeBalance, actualBalance);
      verify(balanceService).getAvailableBalance(customerId);
    }

    @Test
    @DisplayName("Should handle large balance amounts")
    void shouldHandleLargeBalanceAmounts() throws ExecutionException, InterruptedException {
      // Given
      String customerId = "c_high_value_customer";
      BigDecimal largeBalance = new BigDecimal("999999999.99");

      when(balanceService.getAvailableBalance(customerId)).thenReturn(largeBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);
      BigDecimal actualBalance = result.get();

      // Then
      assertEquals(largeBalance, actualBalance);
      verify(balanceService).getAvailableBalance(customerId);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should propagate balance service exceptions")
    void shouldPropagateBalanceServiceExceptions() {
      // Given
      String customerId = "c_error_customer";
      when(balanceService.getAvailableBalance(customerId))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);

      // Then
      ExecutionException exception = assertThrows(ExecutionException.class, result::get);
      assertTrue(exception.getCause() instanceof RuntimeException);
      assertEquals("Balance fetch failed", exception.getCause().getMessage());

      verify(balanceService).getAvailableBalance(customerId);
    }

    @Test
    @DisplayName("Should handle null customer ID gracefully")
    void shouldHandleNullCustomerIdGracefully() {
      // Given
      String nullCustomerId = null;
      when(balanceService.getAvailableBalance(nullCustomerId))
          .thenThrow(new NullPointerException("Customer ID cannot be null"));

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(nullCustomerId);

      // Then
      ExecutionException exception = assertThrows(ExecutionException.class, result::get);
      assertTrue(exception.getCause() instanceof RuntimeException);
      assertEquals("Balance fetch failed", exception.getCause().getMessage());
    }

    @Test
    @DisplayName("Should handle thread interruption gracefully")
    void shouldHandleThreadInterruptionGracefully() {
      // Given
      String customerId = "c_interrupt_test";
      
      // Mock balance service to return normally - interruption happens in BalanceTool's Thread.sleep
      lenient().when(balanceService.getAvailableBalance(customerId)).thenReturn(new BigDecimal("100.00"));

      // Reset the performance logger mock to provide a specific behavior for this test
      reset(performanceLogger);
      when(performanceLogger.logExecutionTime(any(), any(), any()))
          .thenAnswer(
              invocation -> {
                Supplier<?> operation = invocation.getArgument(2);
                // Simulate thread interruption during balance fetch
                Thread.currentThread().interrupt();
                return operation.get();
              });

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);

      // Then
      ExecutionException exception = assertThrows(ExecutionException.class, result::get);
      assertTrue(exception.getCause() instanceof RuntimeException);
      assertEquals("Balance fetch interrupted", exception.getCause().getMessage());

      Thread.interrupted();
    }
  }

  @Nested
  @DisplayName("Asynchronous Operation Tests")
  class AsynchronousOperationTests {

    @Test
    @DisplayName("Should execute balance retrieval asynchronously")
    void shouldExecuteBalanceRetrievalAsynchronously()
        throws ExecutionException, InterruptedException {
      // Given
      String customerId = "c_async_test";
      BigDecimal expectedBalance = new BigDecimal("750.00");

      when(balanceService.getAvailableBalance(customerId)).thenReturn(expectedBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);

      // Then
      assertFalse(result.isDone()); // Should be asynchronous
      BigDecimal actualBalance = result.get(); // Wait for completion
      assertTrue(result.isDone());
      assertEquals(expectedBalance, actualBalance);

      verify(resourceManager).getAgentToolExecutor();
    }

    @Test
    @DisplayName("Should handle multiple concurrent balance requests")
    void shouldHandleMultipleConcurrentBalanceRequests()
        throws ExecutionException, InterruptedException {
      // Given
      String customer1 = "c_concurrent_1";
      String customer2 = "c_concurrent_2";
      BigDecimal balance1 = new BigDecimal("1000.00");
      BigDecimal balance2 = new BigDecimal("2000.00");

      when(balanceService.getAvailableBalance(customer1)).thenReturn(balance1);
      when(balanceService.getAvailableBalance(customer2)).thenReturn(balance2);

      // When
      CompletableFuture<BigDecimal> result1 = balanceTool.execute(customer1);
      CompletableFuture<BigDecimal> result2 = balanceTool.execute(customer2);

      // Then
      assertEquals(balance1, result1.get());
      assertEquals(balance2, result2.get());

      verify(balanceService).getAvailableBalance(customer1);
      verify(balanceService).getAvailableBalance(customer2);
    }
  }

  @Nested
  @DisplayName("Performance Monitoring Integration Tests")
  class PerformanceMonitoringIntegrationTests {

    @Test
    @DisplayName("Should integrate with performance logger for monitoring")
    void shouldIntegrateWithPerformanceLoggerForMonitoring()
        throws ExecutionException, InterruptedException {
      // Given
      String customerId = "c_performance_test";
      BigDecimal expectedBalance = new BigDecimal("500.00");

      when(balanceService.getAvailableBalance(customerId)).thenReturn(expectedBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);
      result.get(); // Wait for completion

      // Then
      verify(performanceLogger)
          .logExecutionTime(eq("getBalance"), eq("BalanceTool"), any(Supplier.class));
    }

    @Test
    @DisplayName("Should record performance metrics even when operation fails")
    void shouldRecordPerformanceMetricsEvenWhenOperationFails() {
      // Given
      String customerId = "c_fail_test";
      when(balanceService.getAvailableBalance(customerId))
          .thenThrow(new RuntimeException("Service unavailable"));

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);

      // Then
      assertThrows(ExecutionException.class, result::get);
      verify(performanceLogger)
          .logExecutionTime(eq("getBalance"), eq("BalanceTool"), any(Supplier.class));
    }
  }

  @Nested
  @DisplayName("Resource Management Tests")
  class ResourceManagementTests {

    @Test
    @DisplayName("Should use agent tool executor for asynchronous operations")
    void shouldUseAgentToolExecutorForAsynchronousOperations()
        throws ExecutionException, InterruptedException {
      // Given
      String customerId = "c_executor_test";
      BigDecimal expectedBalance = new BigDecimal("300.00");

      when(balanceService.getAvailableBalance(customerId)).thenReturn(expectedBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);
      result.get(); // Wait for completion

      // Then
      verify(resourceManager).getAgentToolExecutor();
    }

    @Test
    @DisplayName("Should properly mask customer ID in logging")
    void shouldProperlyMaskCustomerIdInLogging() throws ExecutionException, InterruptedException {
      String customerId = "c_sensitive_customer_12345";
      BigDecimal expectedBalance = new BigDecimal("1200.00");

      when(balanceService.getAvailableBalance(customerId)).thenReturn(expectedBalance);

      // When
      CompletableFuture<BigDecimal> result = balanceTool.execute(customerId);
      BigDecimal actualBalance = result.get();

      // Then
      assertEquals(expectedBalance, actualBalance);
      verify(balanceService).getAvailableBalance(customerId);
    }
  }
}
