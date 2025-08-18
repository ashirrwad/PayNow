package com.paynow.agentassist.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.PaymentDecisionResponse;
import com.paynow.agentassist.dto.RateLimitResult;
import com.paynow.agentassist.entity.PaymentTransaction;
import com.paynow.agentassist.event.EventPublisher;
import com.paynow.agentassist.event.PaymentDecisionEvent;
import com.paynow.agentassist.repository.PaymentTransactionRepository;
import com.paynow.agentassist.service.agent.PaymentDecisionAgent;
import com.paynow.agentassist.service.agent.PaymentDecisionProcessor;
import com.paynow.agentassist.strategy.DecisionStrategyRegistry;
import com.paynow.agentassist.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PaymentDecisionService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentDecisionService.class);

  private final PaymentTransactionRepository transactionRepository;
  private final PaymentDecisionProcessor decisionAgent;
  private final BalanceService balanceService;
  private final RateLimitingService rateLimitingService;
  private final DecisionStrategyRegistry strategyRegistry;
  private final EventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public PaymentDecisionService(
      PaymentTransactionRepository transactionRepository,
      PaymentDecisionProcessor decisionAgent,
      BalanceService balanceService,
      RateLimitingService rateLimitingService,
      DecisionStrategyRegistry strategyRegistry,
      EventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.transactionRepository = transactionRepository;
    this.decisionAgent = decisionAgent;
    this.balanceService = balanceService;
    this.rateLimitingService = rateLimitingService;
    this.strategyRegistry = strategyRegistry;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  public RateLimitResult checkRateLimit(PaymentDecisionRequest request) {
    if (!rateLimitingService.isAllowed(request.customerId())) {
      logger.warn(
          "Rate limit exceeded for customer: {}",
          PiiMaskingUtil.maskCustomerId(request.customerId()));
      return RateLimitResult.rateLimited();
    }
    return RateLimitResult.success();
  }

  public PaymentDecisionResponse processPaymentDecisionWithStrategy(
      PaymentDecisionRequest request, String strategyName) {
    return processPaymentDecisionInternal(request, strategyName);
  }

  public PaymentDecisionResponse processPaymentDecision(PaymentDecisionRequest request) {
    return processPaymentDecisionInternal(request, "default");
  }

  private PaymentDecisionResponse processPaymentDecisionInternal(
      PaymentDecisionRequest request, String strategyName) {
    String requestId = generateRequestId();
    MDC.put("requestId", requestId);
    MDC.put("customerId", PiiMaskingUtil.maskCustomerId(request.customerId()));

    try {
      logger.info(
          "Processing payment decision for amount: {} {}", request.amount(), request.currency());

      Optional<PaymentTransaction> existingTransaction =
          transactionRepository.findByIdempotencyKey(request.idempotencyKey());

      if (existingTransaction.isPresent()) {
        logger.info("Returning cached decision for idempotency key: {}", request.idempotencyKey());
        return buildResponseFromTransaction(existingTransaction.get());
      }

      PaymentDecisionAgent.AgentDecisionResult result =
          strategyName.equals("default")
              ? decisionAgent.processPayment(request)
              : decisionAgent.processPaymentWithStrategy(request, strategyName);

      if (result.decision() == PaymentDecision.ALLOW) {
        boolean reservationSuccessful =
            balanceService.reserveAmount(request.customerId(), request.amount());
        if (!reservationSuccessful) {
          logger.warn(
              "Failed to reserve amount, changing decision to BLOCK due to insufficient funds");
          result =
              new PaymentDecisionAgent.AgentDecisionResult(
                  PaymentDecision.BLOCK, java.util.List.of("insufficient_funds"), result.trace());
        } else {
          logger.info("Amount reserved successfully for customer");
        }
      }

      PaymentTransaction transaction = createTransaction(request, result, requestId);
      transactionRepository.save(transaction);

      PaymentDecisionResponse response =
          new PaymentDecisionResponse(
              result.decision(), result.reasons(), result.trace(), requestId);

      logger.info(
          "Payment decision completed: {} with {} reasons",
          result.decision(),
          result.reasons().size());

      publishPaymentDecisionEvent(request, result, requestId);

      return response;

    } catch (Exception e) {
      logger.error("Error processing payment decision", e);
      return createErrorResponse(requestId);
    } finally {
      MDC.clear();
    }
  }

  private PaymentTransaction createTransaction(
      PaymentDecisionRequest request,
      PaymentDecisionAgent.AgentDecisionResult result,
      String requestId) {
    PaymentTransaction transaction = new PaymentTransaction();
    transaction.setIdempotencyKey(request.idempotencyKey());
    transaction.setCustomerId(request.customerId());
    transaction.setAmount(request.amount());
    transaction.setCurrency(request.currency());
    transaction.setPayeeId(request.payeeId());
    transaction.setDecision(result.decision());
    transaction.setRequestId(requestId);

    try {
      transaction.setAgentTrace(objectMapper.writeValueAsString(result.trace()));
      transaction.setReasons(objectMapper.writeValueAsString(result.reasons()));
    } catch (JsonProcessingException e) {
      logger.error("Failed to serialize agent trace or reasons", e);
      transaction.setAgentTrace("[]");
      transaction.setReasons("[]");
    }

    return transaction;
  }

  private PaymentDecisionResponse buildResponseFromTransaction(PaymentTransaction transaction) {
    try {
      return new PaymentDecisionResponse(
          transaction.getDecision(),
          objectMapper.readValue(
              transaction.getReasons(),
              objectMapper
                  .getTypeFactory()
                  .constructCollectionType(java.util.List.class, String.class)),
          objectMapper.readValue(
              transaction.getAgentTrace(),
              objectMapper
                  .getTypeFactory()
                  .constructCollectionType(
                      java.util.List.class, com.paynow.agentassist.domain.AgentStep.class)),
          transaction.getRequestId());
    } catch (JsonProcessingException e) {
      logger.error("Failed to deserialize transaction data", e);
      return createErrorResponse(transaction.getRequestId());
    }
  }

  private PaymentDecisionResponse createErrorResponse(String requestId) {
    return new PaymentDecisionResponse(
        PaymentDecision.BLOCK,
        java.util.List.of("system_error"),
        java.util.List.of(
            new com.paynow.agentassist.domain.AgentStep("error", "System error occurred")),
        requestId);
  }

  private String generateRequestId() {
    return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private void publishPaymentDecisionEvent(
      PaymentDecisionRequest request,
      PaymentDecisionAgent.AgentDecisionResult result,
      String requestId) {
    try {
      PaymentDecisionEvent event =
          new PaymentDecisionEvent(
              "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
              request.customerId(),
              request.amount(),
              request.currency(),
              request.payeeId(),
              result.decision(),
              result.reasons(),
              requestId,
              request.idempotencyKey());

      eventPublisher.publishPaymentDecision(event);
      logger.debug("Payment decision event published: {}", event.eventId());

    } catch (Exception e) {
      logger.error("Failed to publish payment decision event for request: {}", requestId, e);
    }
  }
}
