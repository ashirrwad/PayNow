package com.paynow.agentassist.service.payment.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynow.agentassist.domain.AgentStep;
import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.PaymentDecisionResponse;
import com.paynow.agentassist.dto.RateLimitResult;
import com.paynow.agentassist.entity.PaymentTransaction;
import com.paynow.agentassist.event.EventPublisher;
import com.paynow.agentassist.event.PaymentDecisionEvent;
import com.paynow.agentassist.repository.PaymentTransactionRepository;
import com.paynow.agentassist.service.BalanceService;
import com.paynow.agentassist.service.RateLimitingService;
import com.paynow.agentassist.service.agent.PaymentDecisionAgent;
import com.paynow.agentassist.service.agent.PaymentDecisionProcessor;
import com.paynow.agentassist.service.payment.PaymentDecisionService;
import com.paynow.agentassist.strategy.DecisionStrategyRegistry;
import com.paynow.agentassist.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core implementation of PaymentDecisionService.
 * Contains the pure business logic for payment decision processing.
 * 
 * This implementation handles:
 * - Rate limiting checks
 * - Payment decision processing with agent tools
 * - Transaction persistence
 * - Event publishing
 * - Idempotency handling
 */
@Service("paymentDecisionServiceImpl")
@Transactional
public class PaymentDecisionServiceImpl implements PaymentDecisionService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentDecisionServiceImpl.class);

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentDecisionProcessor decisionAgent;
    private final BalanceService balanceService;
    private final RateLimitingService rateLimitingService;
    private final DecisionStrategyRegistry strategyRegistry;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PaymentDecisionServiceImpl(
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

    @Override
    public RateLimitResult checkRateLimit(PaymentDecisionRequest request) {
        if (!rateLimitingService.isAllowed(request.customerId())) {
            logger.warn("Rate limit exceeded for customer: {}",
                    PiiMaskingUtil.maskCustomerId(request.customerId()));
            return RateLimitResult.rateLimited();
        }
        return RateLimitResult.success();
    }

    @Override
    public PaymentDecisionResponse processPaymentDecisionWithStrategy(
            PaymentDecisionRequest request, String strategyName) {
        return processPaymentDecisionInternal(request, strategyName);
    }

    @Override
    public PaymentDecisionResponse processPaymentDecision(PaymentDecisionRequest request) {
        return processPaymentDecisionInternal(request, "default");
    }

    /**
     * Internal method that handles the core payment decision processing logic.
     * This method is responsible for:
     * - Setting up MDC context for logging
     * - Checking for existing transactions (idempotency)
     * - Processing the payment decision through the agent
     * - Handling balance reservations for ALLOW decisions
     * - Persisting the transaction
     * - Publishing events
     */
    private PaymentDecisionResponse processPaymentDecisionInternal(
            PaymentDecisionRequest request, String strategyName) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);
        MDC.put("customerId", PiiMaskingUtil.maskCustomerId(request.customerId()));

        try {
            logger.info("Processing payment decision for amount: {} {}", 
                       request.amount(), request.currency());

            // Check for existing transaction (idempotency)
            Optional<PaymentTransaction> existingTransaction =
                    transactionRepository.findByIdempotencyKey(request.idempotencyKey());

            if (existingTransaction.isPresent()) {
                logger.info("Returning cached decision for idempotency key: {}", request.idempotencyKey());
                return buildResponseFromTransaction(existingTransaction.get());
            }

            // Process payment decision through agent
            PaymentDecisionAgent.AgentDecisionResult result =
                    strategyName.equals("default")
                            ? decisionAgent.processPayment(request)
                            : decisionAgent.processPaymentWithStrategy(request, strategyName);

            // Handle balance reservation for ALLOW decisions
            if (result.decision() == PaymentDecision.ALLOW) {
                boolean reservationSuccessful =
                        balanceService.reserveAmount(request.customerId(), request.amount());
                if (!reservationSuccessful) {
                    logger.warn("Failed to reserve amount, changing decision to BLOCK due to insufficient funds");
                    result = new PaymentDecisionAgent.AgentDecisionResult(
                            PaymentDecision.BLOCK, 
                            List.of("insufficient_funds"), 
                            result.trace());
                } else {
                    logger.info("Amount reserved successfully for customer");
                }
            }

            // Persist transaction
            PaymentTransaction transaction = createTransaction(request, result, requestId);
            transactionRepository.save(transaction);

            // Build response
            PaymentDecisionResponse response = new PaymentDecisionResponse(
                    result.decision(), result.reasons(), result.trace(), requestId);

            logger.info("Payment decision completed: {} with {} reasons",
                       result.decision(), result.reasons().size());

            // Publish event
            publishPaymentDecisionEvent(request, result, requestId);

            return response;

        } catch (Exception e) {
            logger.error("Error processing payment decision", e);
            return createErrorResponse(requestId);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Creates a PaymentTransaction entity from the request and decision result.
     */
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

    /**
     * Builds a PaymentDecisionResponse from an existing transaction (for idempotency).
     */
    private PaymentDecisionResponse buildResponseFromTransaction(PaymentTransaction transaction) {
        try {
            return new PaymentDecisionResponse(
                    transaction.getDecision(),
                    objectMapper.readValue(transaction.getReasons(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                    objectMapper.readValue(transaction.getAgentTrace(),
                            objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, AgentStep.class)),
                    transaction.getRequestId());
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize transaction data", e);
            return createErrorResponse(transaction.getRequestId());
        }
    }

    /**
     * Creates an error response when processing fails.
     */
    private PaymentDecisionResponse createErrorResponse(String requestId) {
        return new PaymentDecisionResponse(
                PaymentDecision.BLOCK,
                List.of("system_error"),
                List.of(new AgentStep("error", "System error occurred")),
                requestId);
    }

    /**
     * Generates a unique request ID for correlation.
     */
    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Publishes a payment decision event for downstream processing.
     */
    private void publishPaymentDecisionEvent(
            PaymentDecisionRequest request,
            PaymentDecisionAgent.AgentDecisionResult result,
            String requestId) {
        try {
            PaymentDecisionEvent event = new PaymentDecisionEvent(
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