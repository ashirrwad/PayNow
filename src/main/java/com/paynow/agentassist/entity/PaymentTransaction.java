package com.paynow.agentassist.entity;

import com.paynow.agentassist.domain.PaymentDecision;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "payment_transactions",
    indexes = {
      @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
      @Index(name = "idx_customer_id", columnList = "customerId"),
      @Index(name = "idx_request_id", columnList = "requestId")
    })
public class PaymentTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String idempotencyKey;

  @Column(nullable = false, length = 50)
  private String customerId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 50)
  private String payeeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentDecision decision;

  @Column(nullable = false, unique = true, length = 50)
  private String requestId;

  @Column(columnDefinition = "TEXT")
  private String agentTrace;

  @Column(columnDefinition = "TEXT")
  private String reasons;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  public PaymentTransaction() {}

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
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

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getPayeeId() {
    return payeeId;
  }

  public void setPayeeId(String payeeId) {
    this.payeeId = payeeId;
  }

  public PaymentDecision getDecision() {
    return decision;
  }

  public void setDecision(PaymentDecision decision) {
    this.decision = decision;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getAgentTrace() {
    return agentTrace;
  }

  public void setAgentTrace(String agentTrace) {
    this.agentTrace = agentTrace;
  }

  public String getReasons() {
    return reasons;
  }

  public void setReasons(String reasons) {
    this.reasons = reasons;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
