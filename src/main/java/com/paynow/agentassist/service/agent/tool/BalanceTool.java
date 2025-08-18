package com.paynow.agentassist.service.agent.tool;

import com.paynow.agentassist.service.BalanceService;
import com.paynow.agentassist.util.PerformanceLogger;
import com.paynow.agentassist.util.PiiMaskingUtil;
import com.paynow.agentassist.util.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Component
public class BalanceTool implements AgentTool<String, BigDecimal> {

  private static final Logger logger = LoggerFactory.getLogger(BalanceTool.class);

  private final BalanceService balanceService;
  private final ResourceManager resourceManager;
  private final PerformanceLogger performanceLogger;

  public BalanceTool(
      BalanceService balanceService,
      ResourceManager resourceManager,
      PerformanceLogger performanceLogger) {
    this.balanceService = balanceService;
    this.resourceManager = resourceManager;
    this.performanceLogger = performanceLogger;
  }

  @Override
  public String getName() {
    return "getBalance";
  }

  @Override
  public String getDescription() {
    return "Retrieves customer balance information";
  }

  @Override
  public CompletableFuture<BigDecimal> execute(String customerId) {
    return CompletableFuture.supplyAsync(
        () -> {
          return performanceLogger.logExecutionTime(
              "getBalance",
              "BalanceTool",
              () -> {
                try {
                  logger.debug(
                      "Fetching balance for customer: {}",
                      PiiMaskingUtil.maskCustomerId(customerId));
                  Thread.sleep(50); // Simulate DB call latency
                  return balanceService.getAvailableBalance(customerId);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new RuntimeException("Balance fetch interrupted", e);
                } catch (Exception e) {
                  logger.error(
                      "Failed to fetch balance for customer: {}",
                      PiiMaskingUtil.maskCustomerId(customerId),
                      e);
                  throw new RuntimeException("Balance fetch failed", e);
                }
              });
        },
        resourceManager.getAgentToolExecutor());
  }

  @Override
  public Class<String> getInputType() {
    return String.class;
  }

  @Override
  public Class<BigDecimal> getResultType() {
    return BigDecimal.class;
  }
}
