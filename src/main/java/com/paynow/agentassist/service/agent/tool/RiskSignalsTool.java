package com.paynow.agentassist.service.agent.tool;

import com.paynow.agentassist.dto.RiskSignals;
import com.paynow.agentassist.util.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RiskSignalsTool implements AgentTool<String, RiskSignals> {

  private static final Logger logger = LoggerFactory.getLogger(RiskSignalsTool.class);
  private final ResourceManager resourceManager;

  public RiskSignalsTool(ResourceManager resourceManager) {
    this.resourceManager = resourceManager;
  }

  @Override
  public String getName() {
    return "getRiskSignals";
  }

  @Override
  public String getDescription() {
    return "Retrieves risk signals and fraud indicators for customer";
  }

  @Override
  public CompletableFuture<RiskSignals> execute(String customerId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            logger.debug("Fetching risk signals for customer: {}", customerId);
            Thread.sleep(75); // Simulate ML model call latency

            // Simulate deterministic risk assessment based on customerId
            int hash = customerId.hashCode();
            boolean deviceChange = (hash % 7) == 0;
            int recentDisputes = Math.abs(hash % 4);
            boolean velocityViolation = (hash % 5) == 0;
            int dailyTransactionCount = Math.abs(hash % 20) + 1;

            String riskScore;
            if (recentDisputes >= 2 || velocityViolation) {
              riskScore = "HIGH";
            } else if (deviceChange || dailyTransactionCount > 15) {
              riskScore = "MEDIUM";
            } else {
              riskScore = "LOW";
            }

            return new RiskSignals(
                recentDisputes, deviceChange, velocityViolation, dailyTransactionCount, riskScore);

          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Risk signals fetch interrupted", e);
          } catch (Exception e) {
            logger.error("Failed to fetch risk signals for customer: {}", customerId, e);
            throw new RuntimeException("Risk signals fetch failed", e);
          }
        },
        resourceManager.getAgentToolExecutor());
  }

  @Override
  public Class<String> getInputType() {
    return String.class;
  }

  @Override
  public Class<RiskSignals> getResultType() {
    return RiskSignals.class;
  }
}
