package com.paynow.agentassist.service;

import com.paynow.agentassist.entity.CustomerBalance;
import com.paynow.agentassist.repository.CustomerBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Transactional
public class CustomerBalanceService {

  private final CustomerBalanceRepository balanceRepository;

  public CustomerBalanceService(CustomerBalanceRepository balanceRepository) {
    this.balanceRepository = balanceRepository;
  }

  public BigDecimal getBalance(String customerId) {
    CustomerBalance balance = getOrCreateCustomerBalance(customerId);
    return balance.getBalance();
  }

  public BigDecimal getRemainingDailyLimit(String customerId) {
    CustomerBalance balance = getOrCreateCustomerBalance(customerId);
    resetDailyLimitIfNeeded(balance);
    return balance.getDailyLimit().subtract(balance.getDailySpent());
  }

  public void reserveAmount(String customerId, BigDecimal amount) {
    CustomerBalance balance = getOrCreateCustomerBalance(customerId);
    resetDailyLimitIfNeeded(balance);

    if (balance.getBalance().compareTo(amount) < 0) {
      throw new IllegalArgumentException("Insufficient balance");
    }

    BigDecimal remainingDaily = balance.getDailyLimit().subtract(balance.getDailySpent());
    if (remainingDaily.compareTo(amount) < 0) {
      throw new IllegalArgumentException("Daily limit exceeded");
    }

    balance.setBalance(balance.getBalance().subtract(amount));
    balance.setDailySpent(balance.getDailySpent().add(amount));
    balanceRepository.save(balance);
  }

  private CustomerBalance getOrCreateCustomerBalance(String customerId) {
    return balanceRepository
        .findByCustomerId(customerId)
        .orElseGet(
            () -> {
              CustomerBalance newBalance =
                  new CustomerBalance(
                      customerId, new BigDecimal("1000.00"), new BigDecimal("500.00"));
              return balanceRepository.save(newBalance);
            });
  }

  private void resetDailyLimitIfNeeded(CustomerBalance balance) {
    LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
    if (balance.getLastResetDate().isBefore(today)) {
      balance.setDailySpent(BigDecimal.ZERO);
      balance.setLastResetDate(today);
      balanceRepository.save(balance);
    }
  }
}
