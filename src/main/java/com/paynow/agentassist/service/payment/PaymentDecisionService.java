package com.paynow.agentassist.service.payment;

import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.PaymentDecisionResponse;
import com.paynow.agentassist.dto.RateLimitResult;

/**
 * Interface for payment decision processing services.
 * Defines the contract for payment decision operations.
 * 
 * This interface provides methods for:
 * - Rate limiting checks
 * - Payment decision processing with default strategy
 * - Payment decision processing with custom strategies
 */
public interface PaymentDecisionService {
    
    /**
     * Check if the customer has exceeded their rate limit.
     * 
     * @param request the payment decision request containing customer information
     * @return rate limit result indicating if the request is allowed
     */
    RateLimitResult checkRateLimit(PaymentDecisionRequest request);
    
    /**
     * Process a payment decision using the default strategy.
     * This is the main method for payment decision processing.
     * 
     * @param request the payment decision request
     * @return the payment decision response with decision, reasons, and agent trace
     */
    PaymentDecisionResponse processPaymentDecision(PaymentDecisionRequest request);
    
    /**
     * Process a payment decision using a specific strategy.
     * Allows for custom decision strategies (e.g., conservative, aggressive).
     * 
     * @param request the payment decision request
     * @param strategyName the name of the strategy to use (must be registered)
     * @return the payment decision response
     * @throws IllegalArgumentException if the strategy is not found
     */
    PaymentDecisionResponse processPaymentDecisionWithStrategy(PaymentDecisionRequest request, String strategyName);
}