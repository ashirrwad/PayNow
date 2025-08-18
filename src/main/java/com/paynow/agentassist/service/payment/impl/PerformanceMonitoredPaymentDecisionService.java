package com.paynow.agentassist.service.payment.impl;

import com.paynow.agentassist.constants.ApiConstants;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.PaymentDecisionResponse;
import com.paynow.agentassist.dto.RateLimitResult;
import com.paynow.agentassist.service.payment.PaymentDecisionService;
import com.paynow.agentassist.util.PiiMaskingUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PerformanceMonitoredPaymentDecisionService implements PaymentDecisionService {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoredPaymentDecisionService.class);
    
    private final PaymentDecisionService delegate;
    private final MeterRegistry meterRegistry;
    
    public PerformanceMonitoredPaymentDecisionService(
            @Qualifier("paymentDecisionServiceImpl") PaymentDecisionService delegate,
            MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public RateLimitResult checkRateLimit(PaymentDecisionRequest request) {
        Timer timer = createTimer("checkRateLimit");
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String originalCustomerId = MDC.get("customerId");
            if (originalCustomerId == null) {
                MDC.put("customerId", PiiMaskingUtil.maskCustomerId(request.customerId()));
            }
            
            try {
                logger.debug("Checking rate limit for customer");
                RateLimitResult result = delegate.checkRateLimit(request);
                
                meterRegistry.counter(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL,
                                    "operation", "rate_limit_check",
                                    "allowed", String.valueOf(result.allowed()))
                            .increment();
                
                if (!result.allowed()) {
                    logger.warn("Rate limit exceeded for customer");
                }
                
                return result;
            } finally {
                if (originalCustomerId == null) {
                    MDC.remove("customerId");
                }
            }
        } finally {
            sample.stop(timer);
        }
    }
    
    @Override
    public PaymentDecisionResponse processPaymentDecision(PaymentDecisionRequest request) {
        Timer timer = createTimer("processPaymentDecision");
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String originalCustomerId = MDC.get("customerId");
            if (originalCustomerId == null) {
                MDC.put("customerId", PiiMaskingUtil.maskCustomerId(request.customerId()));
            }
            
            try {
                logger.info("Processing payment decision for amount: {} {}", 
                          request.amount(), request.currency());
                
                long startTime = System.nanoTime();
                PaymentDecisionResponse response = delegate.processPaymentDecision(request);
                long duration = System.nanoTime() - startTime;
                double durationMs = duration / 1_000_000.0;
                
                meterRegistry.counter(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL,
                                    "operation", "payment_decision",
                                    "decision", response.decision().name())
                            .increment();
                
                if (durationMs > 500) {
                    logger.warn("Slow payment decision processing: {:.2f} ms", durationMs);
                }
                
                logger.info("Payment decision completed: {} with {} reasons (requestId: {})", 
                          response.decision(), response.reasons().size(), response.requestId());
                
                return response;
                
            } catch (Exception e) {
                meterRegistry.counter(ApiConstants.METRIC_OPERATION_FAILURES,
                                    "operation", "payment_decision",
                                    "exception", e.getClass().getSimpleName())
                            .increment();
                
                logger.error("Payment decision processing failed", e);
                throw e;
                
            } finally {
                if (originalCustomerId == null) {
                    MDC.remove("customerId");
                }
            }
        } finally {
            sample.stop(timer);
        }
    }
    
    @Override
    public PaymentDecisionResponse processPaymentDecisionWithStrategy(PaymentDecisionRequest request, String strategyName) {
        Timer timer = createTimer("processPaymentDecisionWithStrategy");
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String originalCustomerId = MDC.get("customerId");
            if (originalCustomerId == null) {
                MDC.put("customerId", PiiMaskingUtil.maskCustomerId(request.customerId()));
            }
            
            try {
                logger.info("Processing payment decision with strategy: {} for amount: {} {}", 
                          strategyName, request.amount(), request.currency());
                
                PaymentDecisionResponse response = delegate.processPaymentDecisionWithStrategy(request, strategyName);
                
                meterRegistry.counter(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL,
                                    "operation", "payment_decision_strategy",
                                    "strategy", strategyName,
                                    "decision", response.decision().name())
                            .increment();
                
                logger.info("Payment decision with strategy completed: {} (strategy: {}, requestId: {})", 
                          response.decision(), strategyName, response.requestId());
                
                return response;
                
            } catch (Exception e) {
                meterRegistry.counter(ApiConstants.METRIC_OPERATION_FAILURES,
                                    "operation", "payment_decision_strategy",
                                    "strategy", strategyName,
                                    "exception", e.getClass().getSimpleName())
                            .increment();
                
                logger.error("Payment decision processing with strategy failed: {}", strategyName, e);
                throw e;
                
            } finally {
                if (originalCustomerId == null) {
                    MDC.remove("customerId");
                }
            }
        } finally {
            sample.stop(timer);
        }
    }
    
    private Timer createTimer(String operation) {
        return Timer.builder(ApiConstants.METRIC_REQUEST_DURATION)
                   .tag("service", "PaymentDecisionService")
                   .tag("operation", operation)
                   .register(meterRegistry);
    }
}