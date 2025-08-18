package com.paynow.agentassist.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.paynow.agentassist.util.ResourceManager;

import static com.paynow.agentassist.util.PiiMaskingUtil.maskCustomerId;

@Service
public class BalanceService {

  private static final Logger logger = LoggerFactory.getLogger(BalanceService.class);

  // In-memory balance storage for simulation
  private final ConcurrentHashMap<String, BigDecimal> balances = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, BigDecimal> reservedAmounts = new ConcurrentHashMap<>();

  private final ResourceManager resourceManager;

  public BalanceService(ResourceManager resourceManager) {
    this.resourceManager = resourceManager;
    // Initialize some demo balances
    balances.put("c_customer_001", new BigDecimal("1000.00"));
    balances.put("c_customer_002", new BigDecimal("1500.00"));
    balances.put("c_api_key_test_001", new BigDecimal("2000.00"));
    balances.put("c_test_001", new BigDecimal("5000.00"));

    reservedAmounts.put("c_customer_001", BigDecimal.ZERO);
    reservedAmounts.put("c_customer_002", BigDecimal.ZERO);
    reservedAmounts.put("c_api_key_test_001", BigDecimal.ZERO);
    reservedAmounts.put("c_test_001", BigDecimal.ZERO);
  }

  public BigDecimal getAvailableBalance(String customerId) {
    BigDecimal balance = balances.getOrDefault(customerId, BigDecimal.ZERO);
    BigDecimal reserved = reservedAmounts.getOrDefault(customerId, BigDecimal.ZERO);
    return balance.subtract(reserved);
  }

  public boolean reserveAmount(String customerId, BigDecimal amount) {
    return resourceManager.executeWithLock(
        "balance:" + customerId,
        () -> {
          BigDecimal currentBalance = balances.getOrDefault(customerId, BigDecimal.ZERO);
          BigDecimal currentReserved = reservedAmounts.getOrDefault(customerId, BigDecimal.ZERO);
          BigDecimal availableBalance = currentBalance.subtract(currentReserved);

          if (availableBalance.compareTo(amount) >= 0) {
            // Sufficient funds - reserve the amount
            reservedAmounts.put(customerId, currentReserved.add(amount));
            logger.info(
                "Reserved {} for customer {}, new reserved total: {}",
                amount,
                maskCustomerId(customerId),
                currentReserved.add(amount));
            return true;
          } else {
            logger.warn(
                "Insufficient funds for customer {}, available: {}, requested: {}",
                maskCustomerId(customerId),
                availableBalance,
                amount);
            return false;
          }
        });
  }

  public void releaseReservedAmount(String customerId, BigDecimal amount) {
    resourceManager.executeWithLock(
        "balance:" + customerId,
        () -> {
          BigDecimal currentReserved = reservedAmounts.getOrDefault(customerId, BigDecimal.ZERO);
          BigDecimal newReserved = currentReserved.subtract(amount).max(BigDecimal.ZERO);
          reservedAmounts.put(customerId, newReserved);
          logger.info(
              "Released {} for customer {}, new reserved total: {}",
              amount,
              maskCustomerId(customerId),
              newReserved);
        });
  }

  public void deductBalance(String customerId, BigDecimal amount) {
    resourceManager.executeWithLock(
        "balance:" + customerId,
        () -> {
          BigDecimal currentBalance = balances.getOrDefault(customerId, BigDecimal.ZERO);
          BigDecimal currentReserved = reservedAmounts.getOrDefault(customerId, BigDecimal.ZERO);

          balances.put(customerId, currentBalance.subtract(amount));
          reservedAmounts.put(customerId, currentReserved.subtract(amount).max(BigDecimal.ZERO));

          logger.info(
              "Deducted {} from customer {}, new balance: {}",
              amount,
              maskCustomerId(customerId),
              currentBalance.subtract(amount));
        });
  }

}
