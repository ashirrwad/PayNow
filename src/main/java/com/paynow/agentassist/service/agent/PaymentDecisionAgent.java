package com.paynow.agentassist.service.agent;

import com.paynow.agentassist.domain.AgentStep;
import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.*;
import com.paynow.agentassist.service.agent.tool.BalanceTool;
import com.paynow.agentassist.service.agent.tool.CaseCreationTool;
import com.paynow.agentassist.service.agent.tool.RiskSignalsTool;
import com.paynow.agentassist.strategy.DecisionStrategy;
import com.paynow.agentassist.strategy.DecisionStrategyRegistry;
import com.paynow.agentassist.factory.AgentToolFactory;
import com.paynow.agentassist.util.PerformanceLogger;
import com.paynow.agentassist.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentDecisionAgent implements PaymentDecisionProcessor {

  private static final Logger logger = LoggerFactory.getLogger(PaymentDecisionAgent.class);

  private final BalanceTool balanceTool;
  private final RiskSignalsTool riskSignalsTool;
  private final CaseCreationTool caseCreationTool;
  private final DecisionStrategyRegistry strategyRegistry;
  private final AgentToolFactory toolFactory;
  private final PerformanceLogger performanceLogger;

  @Value("${paynow.agent.max-retries:2}")
  private int maxRetries;

  @Value("${paynow.agent.timeout-seconds:30}")
  private long timeoutSeconds;

  public PaymentDecisionAgent(
      BalanceTool balanceTool,
      RiskSignalsTool riskSignalsTool,
      CaseCreationTool caseCreationTool,
      DecisionStrategyRegistry strategyRegistry,
      AgentToolFactory toolFactory,
      PerformanceLogger performanceLogger) {
    this.balanceTool = balanceTool;
    this.riskSignalsTool = riskSignalsTool;
    this.caseCreationTool = caseCreationTool;
    this.strategyRegistry = strategyRegistry;
    this.toolFactory = toolFactory;
    this.performanceLogger = performanceLogger;
  }

  public AgentDecisionResult processPaymentWithStrategy(
      PaymentDecisionRequest request, String strategyName) {
    return processPaymentInternal(request, strategyName);
  }

  public AgentDecisionResult processPayment(PaymentDecisionRequest request) {
    return processPaymentInternal(request, "default");
  }

  private AgentDecisionResult processPaymentInternal(
      PaymentDecisionRequest request, String strategyName) {
    return performanceLogger.logExecutionTime(
        "processPayment",
        "PaymentDecisionAgent",
        () -> {
          List<AgentStep> trace = new ArrayList<>();
          List<String> reasons = new ArrayList<>();

          logger.info(
              "Processing payment decision for customer: {}",
              PiiMaskingUtil.maskCustomerId(request.customerId()));

          try {
            trace.add(new AgentStep("plan", "Check balance, risk, and limits"));

            // Execute tools asynchronously for better performance
            CompletableFuture<BigDecimal> balanceFuture =
                executeToolWithRetry(() -> balanceTool.execute(request.customerId()), "getBalance");

            CompletableFuture<RiskSignals> riskFuture =
                executeToolWithRetry(
                    () -> riskSignalsTool.execute(request.customerId()), "getRiskSignals");

            // Wait for both tools to complete
            CompletableFuture<Void> allTools = CompletableFuture.allOf(balanceFuture, riskFuture);
            allTools.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();

            BigDecimal balance = balanceFuture.join();
            RiskSignals riskSignals = riskFuture.join();

            trace.add(new AgentStep("tool:getBalance", "balance=" + balance));
            trace.add(
                new AgentStep(
                    "tool:getRiskSignals",
                    String.format(
                        "recent_disputes=%d, device_change=%s, velocity_violation=%s, risk_score=%s",
                        riskSignals.recentDisputes(),
                        riskSignals.deviceChange(),
                        riskSignals.velocityViolation(),
                        riskSignals.riskScore())));

            DecisionStrategy strategy = strategyRegistry.getStrategy(strategyName);
            trace.add(new AgentStep("strategy", "Using decision strategy: " + strategy.getName()));

            PaymentDecision decision =
                strategy.makeDecision(request, balance, riskSignals, reasons);

            // Create case if needed
            if (decision == PaymentDecision.REVIEW || decision == PaymentDecision.BLOCK) {
              String priority = "HIGH".equals(riskSignals.riskScore()) ? "HIGH" : "MEDIUM";
              String reason = String.join(", ", reasons);

              CaseCreationRequest caseRequest =
                  new CaseCreationRequest(
                      request.customerId(),
                      request.amount(),
                      request.currency(),
                      request.payeeId(),
                      reason,
                      priority);

              CompletableFuture<CaseCreationResult> caseFuture =
                  executeToolWithRetry(() -> caseCreationTool.execute(caseRequest), "createCase");

              CaseCreationResult caseResult =
                  caseFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
              trace.add(
                  new AgentStep(
                      "tool:createCase",
                      String.format(
                          "case_id=%s, status=%s, assigned_to=%s",
                          caseResult.caseId(), caseResult.status(), caseResult.assignedTo())));
            }

            trace.add(
                new AgentStep("tool:recommend", "route to " + decision.getValue().toLowerCase()));

            return new AgentDecisionResult(decision, reasons, trace);

          } catch (CompletionException e) {
            logger.error("Agent processing failed for request: {}", request.idempotencyKey(), e);
            trace.add(new AgentStep("error", "Processing failed: " + e.getCause().getMessage()));
            reasons.add("system_error");
            return new AgentDecisionResult(PaymentDecision.BLOCK, reasons, trace);
          } catch (Exception e) {
            logger.error("Agent processing failed for request: {}", request.idempotencyKey(), e);
            trace.add(new AgentStep("error", "Processing failed: " + e.getMessage()));
            reasons.add("system_error");
            return new AgentDecisionResult(PaymentDecision.BLOCK, reasons, trace);
          }
        });
  }

  private <T> CompletableFuture<T> executeToolWithRetry(
      java.util.function.Supplier<CompletableFuture<T>> toolExecution, String toolName) {

    return CompletableFuture.supplyAsync(
        () -> {
          Exception lastException = null;

          for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
              return toolExecution.get().join();
            } catch (Exception e) {
              lastException = e;

              if (attempt == maxRetries) {
                break;
              }

              logger.warn(
                  "Tool {} failed on attempt {}, retrying: {}",
                  toolName,
                  attempt + 1,
                  e.getMessage());

              try {
                Thread.sleep(100 * (attempt + 1)); // Exponential backoff
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Tool execution interrupted", ie);
              }
            }
          }

          throw new RuntimeException(
              "Tool " + toolName + " failed after " + (maxRetries + 1) + " attempts",
              lastException);
        });
  }

  public record AgentDecisionResult(
      PaymentDecision decision, List<String> reasons, List<AgentStep> trace) {}
}
