package com.paynow.agentassist.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "customer_balances",
    indexes = {@Index(name = "idx_customer_balance", columnList = "customerId", unique = true)})
public class CustomerBalance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String customerId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal balance;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal dailyLimit;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal dailySpent;

  @Column(nullable = false)
  private LocalDateTime lastResetDate;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Version private Long version;

  public CustomerBalance() {}

  public CustomerBalance(String customerId, BigDecimal balance, BigDecimal dailyLimit) {
    this.customerId = customerId;
    this.balance = balance;
    this.dailyLimit = dailyLimit;
    this.dailySpent = BigDecimal.ZERO;
    this.lastResetDate = LocalDateTime.now().toLocalDate().atStartOfDay();
    this.updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public BigDecimal getDailyLimit() {
    return dailyLimit;
  }

  public void setDailyLimit(BigDecimal dailyLimit) {
    this.dailyLimit = dailyLimit;
  }

  public BigDecimal getDailySpent() {
    return dailySpent;
  }

  public void setDailySpent(BigDecimal dailySpent) {
    this.dailySpent = dailySpent;
  }

  public LocalDateTime getLastResetDate() {
    return lastResetDate;
  }

  public void setLastResetDate(LocalDateTime lastResetDate) {
    this.lastResetDate = lastResetDate;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
