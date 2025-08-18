package com.paynow.agentassist.strategy;

import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.PaymentDecisionRequestBuilder;
import com.paynow.agentassist.dto.RiskSignals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Decision Strategy Tests")
class DecisionStrategyTest {

  @Nested
  @DisplayName("Default Strategy Tests")
  class DefaultStrategyTests {

    private final DefaultDecisionStrategy strategy = new DefaultDecisionStrategy();

    @Test
    @DisplayName("Should allow low-risk transactions")
    void shouldAllowLowRiskTransactions() {
      // Given
      PaymentDecisionRequest request = createTestRequest("50.00");
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals riskSignals = new RiskSignals(0, false, false, 5, "LOW");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(PaymentDecision.ALLOW, decision);
      assertTrue(reasons.isEmpty());
    }

    @Test
    @DisplayName("Should block insufficient balance")
    void shouldBlockInsufficientBalance() {
      // Given
      PaymentDecisionRequest request = createTestRequest("1000.00");
      BigDecimal balance = new BigDecimal("500.00");
      RiskSignals riskSignals = new RiskSignals(0, false, false, 5, "LOW");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(PaymentDecision.BLOCK, decision);
      assertTrue(reasons.contains("insufficient_balance"));
    }

    @ParameterizedTest
    @CsvSource({
      "HIGH, 0, false, false, 5, BLOCK",
      "MEDIUM, 0, false, false, 5, REVIEW",
      "LOW, 2, false, false, 5, BLOCK",
      "LOW, 1, true, false, 5, REVIEW"
    })
    @DisplayName("Should make decisions based on risk signals")
    void shouldMakeDecisionsBasedOnRiskSignals(
        String riskScore,
        int disputes,
        boolean deviceChange,
        boolean velocityViolation,
        int dailyCount,
        PaymentDecision expected) {
      // Given
      PaymentDecisionRequest request = createTestRequest("50.00");
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals riskSignals =
          new RiskSignals(disputes, deviceChange, velocityViolation, dailyCount, riskScore);
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(expected, decision);
    }
  }

  @Nested
  @DisplayName("Conservative Strategy Tests")
  class ConservativeStrategyTests {

    private final ConservativeDecisionStrategy strategy = new ConservativeDecisionStrategy();

    @Test
    @DisplayName("Should block on any disputes")
    void shouldBlockOnAnyDisputes() {
      // Given
      PaymentDecisionRequest request = createTestRequest("50.00");
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals riskSignals = new RiskSignals(1, false, false, 5, "LOW");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(PaymentDecision.BLOCK, decision);
      assertTrue(reasons.contains("recent_disputes"));
    }

    @Test
    @DisplayName("Should use lower amount threshold")
    void shouldUseLowerAmountThreshold() {
      // Given
      PaymentDecisionRequest request = createTestRequest("75.00"); // Above conservative threshold
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals riskSignals = new RiskSignals(0, false, false, 5, "LOW");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(PaymentDecision.REVIEW, decision);
      assertTrue(reasons.contains("amount_above_conservative_threshold"));
    }

    @Test
    @DisplayName("Should block medium and high risk scores")
    void shouldBlockMediumAndHighRiskScores() {
      // Given
      PaymentDecisionRequest request = createTestRequest("25.00");
      BigDecimal balance = new BigDecimal("1000.00");
      List<String> reasons = new ArrayList<>();

      // Test medium risk
      RiskSignals mediumRisk = new RiskSignals(0, false, false, 5, "MEDIUM");
      PaymentDecision mediumDecision = strategy.makeDecision(request, balance, mediumRisk, reasons);
      assertEquals(PaymentDecision.BLOCK, mediumDecision);

      // Test high risk
      reasons.clear();
      RiskSignals highRisk = new RiskSignals(0, false, false, 5, "HIGH");
      PaymentDecision highDecision = strategy.makeDecision(request, balance, highRisk, reasons);
      assertEquals(PaymentDecision.BLOCK, highDecision);
    }
  }

  @Nested
  @DisplayName("Aggressive Strategy Tests")
  class AggressiveStrategyTests {

    private final AggressiveDecisionStrategy strategy = new AggressiveDecisionStrategy();

    @Test
    @DisplayName("Should allow transactions with single dispute")
    void shouldAllowTransactionsWithSingleDispute() {
      // Given
      PaymentDecisionRequest request = createTestRequest("100.00");
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals riskSignals = new RiskSignals(1, false, false, 5, "MEDIUM");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(PaymentDecision.ALLOW, decision);
    }

    @Test
    @DisplayName("Should use higher amount threshold")
    void shouldUseHigherAmountThreshold() {
      // Given
      PaymentDecisionRequest request = createTestRequest("300.00"); // Below aggressive threshold
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals riskSignals = new RiskSignals(0, false, false, 5, "LOW");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(PaymentDecision.ALLOW, decision);
      assertTrue(reasons.isEmpty());
    }

    @Test
    @DisplayName("Should only block on very high risk combinations")
    void shouldOnlyBlockOnVeryHighRiskCombinations() {
      // Given
      PaymentDecisionRequest request = createTestRequest("100.00");
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals highRiskWithDisputes = new RiskSignals(3, true, true, 25, "HIGH");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision =
          strategy.makeDecision(request, balance, highRiskWithDisputes, reasons);

      // Then
      assertEquals(PaymentDecision.BLOCK, decision);
      assertTrue(reasons.contains("multiple_recent_disputes"));
    }

    @Test
    @DisplayName("Should require multiple reasons for review")
    void shouldRequireMultipleReasonsForReview() {
      // Given
      PaymentDecisionRequest request = createTestRequest("600.00"); // Above threshold
      BigDecimal balance = new BigDecimal("1000.00");
      RiskSignals riskSignals = new RiskSignals(1, false, false, 15, "MEDIUM");
      List<String> reasons = new ArrayList<>();

      // When
      PaymentDecision decision = strategy.makeDecision(request, balance, riskSignals, reasons);

      // Then
      assertEquals(PaymentDecision.ALLOW, decision); // Single reason not enough for review
    }
  }

  @Nested
  @DisplayName("Strategy Registry Tests")
  class StrategyRegistryTests {

    @Test
    @DisplayName("Should register and retrieve strategies correctly")
    void shouldRegisterAndRetrieveStrategiesCorrectly() {
      // Given
      List<DecisionStrategy> strategies =
          List.of(
              new DefaultDecisionStrategy(),
              new ConservativeDecisionStrategy(),
              new AggressiveDecisionStrategy());
      DecisionStrategyRegistry registry = new DecisionStrategyRegistry(strategies);

      // When & Then
      assertNotNull(registry.getStrategy("default"));
      assertNotNull(registry.getStrategy("conservative"));
      assertNotNull(registry.getStrategy("aggressive"));

      assertEquals("default", registry.getStrategy("default").getName());
      assertEquals("conservative", registry.getStrategy("conservative").getName());
      assertEquals("aggressive", registry.getStrategy("aggressive").getName());
    }

    @Test
    @DisplayName("Should return default strategy for unknown strategy name")
    void shouldReturnDefaultStrategyForUnknownStrategyName() {
      // Given
      List<DecisionStrategy> strategies = List.of(new DefaultDecisionStrategy());
      DecisionStrategyRegistry registry = new DecisionStrategyRegistry(strategies);

      // When
      DecisionStrategy unknownStrategy = registry.getStrategy("unknown");

      // Then
      assertEquals("default", unknownStrategy.getName());
    }

    @Test
    @DisplayName("Should validate strategy names correctly")
    void shouldValidateStrategyNamesCorrectly() {
      // Given
      List<DecisionStrategy> strategies =
          List.of(new DefaultDecisionStrategy(), new ConservativeDecisionStrategy());
      DecisionStrategyRegistry registry = new DecisionStrategyRegistry(strategies);

      // When & Then
      assertTrue(registry.isValidStrategy("default"));
      assertTrue(registry.isValidStrategy("conservative"));
      assertFalse(registry.isValidStrategy("unknown"));
    }

    @Test
    @DisplayName("Should provide available strategies map")
    void shouldProvideAvailableStrategiesMap() {
      // Given
      List<DecisionStrategy> strategies =
          List.of(new DefaultDecisionStrategy(), new ConservativeDecisionStrategy());
      DecisionStrategyRegistry registry = new DecisionStrategyRegistry(strategies);

      // When
      var availableStrategies = registry.getAvailableStrategies();

      // Then
      assertEquals(2, availableStrategies.size());
      assertTrue(availableStrategies.containsKey("default"));
      assertTrue(availableStrategies.containsKey("conservative"));
    }
  }

  private PaymentDecisionRequest createTestRequest(String amount) {
    return PaymentDecisionRequestBuilder.newBuilder()
        .customerId("c_test_customer_123")
        .amount(new BigDecimal(amount))
        .currency("USD")
        .payeeId("p_test_payee_456")
        .generateIdempotencyKey()
        .build();
  }
}
