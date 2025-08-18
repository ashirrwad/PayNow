package com.paynow.agentassist.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynow.agentassist.constants.ApiConstants;
import com.paynow.agentassist.util.PiiMaskingUtil;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class PerformanceInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceInterceptor.class);
    private static final String START_TIME_ATTR = "interceptor.startTime";
    private static final String REQUEST_ID_ATTR = "interceptor.requestId";
    
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    
    public PerformanceInterceptor(MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        
        long startTime = System.nanoTime();
        String requestId = generateRequestId();
        
        request.setAttribute(START_TIME_ATTR, startTime);
        request.setAttribute(REQUEST_ID_ATTR, requestId);
        
        // Set up MDC for logging correlation
        MDC.put("requestId", requestId);
        MDC.put("endpoint", request.getRequestURI());
        MDC.put("method", request.getMethod());
        
        // Extract and mask PII from request
        extractAndMaskPii(request);
        
        // Log incoming request
        logger.info("Incoming request: {} {}", request.getMethod(), request.getRequestURI());
        
        // Record request metric
        meterRegistry.counter(ApiConstants.METRIC_PAYMENT_REQUESTS_TOTAL,
                            "endpoint", request.getRequestURI(),
                            "method", request.getMethod())
                    .increment();
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) throws Exception {
        
        try {
            Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
            String requestId = (String) request.getAttribute(REQUEST_ID_ATTR);
            
            if (startTime != null) {
                long duration = System.nanoTime() - startTime;
                double durationMs = duration / 1_000_000.0;
                
                String endpoint = request.getRequestURI();
                String method = request.getMethod();
                String status = String.valueOf(response.getStatus());
                
                meterRegistry.timer(ApiConstants.METRIC_REQUEST_DURATION,
                                  "endpoint", endpoint,
                                  "method", method,
                                  "status", status)
                            .record(duration, TimeUnit.NANOSECONDS);
                
                // Log slow requests
                if (durationMs > 200) {
                    logger.warn("Slow HTTP request detected: {} {} took {:.2f} ms (requestId: {})", 
                              method, endpoint, durationMs, requestId);
                } else {
                    logger.info("Request completed: {} {} in {:.2f} ms (status: {}, requestId: {})", 
                              method, endpoint, durationMs, response.getStatus(), requestId);
                }
                
                if (ex != null) {
                    meterRegistry.counter(ApiConstants.METRIC_OPERATION_FAILURES,
                                        "endpoint", endpoint,
                                        "method", method,
                                        "exception", ex.getClass().getSimpleName())
                                .increment();
                    
                    logger.error("Request failed: {} {} (requestId: {})", method, endpoint, requestId, ex);
                }
            }
        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.clear();
        }
    }
    
    private void extractAndMaskPii(HttpServletRequest request) {
        try {
            // Try to extract customer ID from request body for POST requests
            if ("POST".equals(request.getMethod()) && request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
                byte[] content = wrapper.getContentAsByteArray();
                
                if (content.length > 0) {
                    String body = new String(content);
                    extractCustomerIdFromJson(body);
                }
            }
            
            // Extract from path parameters (e.g., /api/v1/customers/{customerId})
            String uri = request.getRequestURI();
            extractCustomerIdFromPath(uri);
            
            // Extract from query parameters
            String customerId = request.getParameter("customerId");
            if (customerId != null) {
                MDC.put("customerId", PiiMaskingUtil.maskCustomerId(customerId));
            }
            
        } catch (Exception e) {
            logger.debug("Failed to extract PII for masking: {}", e.getMessage());
            // Don't fail the request if PII extraction fails
        }
    }
    
    private void extractCustomerIdFromJson(String jsonBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonBody);
            JsonNode customerIdNode = jsonNode.get("customerId");
            
            if (customerIdNode != null && !customerIdNode.isNull()) {
                String customerId = customerIdNode.asText();
                MDC.put("customerId", PiiMaskingUtil.maskCustomerId(customerId));
            }
        } catch (IOException e) {
            logger.debug("Failed to parse JSON for PII extraction: {}", e.getMessage());
        }
    }
    
    private void extractCustomerIdFromPath(String uri) {
        // Match patterns like /api/v1/customers/c_12345 or /customers/c_12345
        if (uri.contains("/customers/")) {
            String[] parts = uri.split("/customers/");
            if (parts.length > 1) {
                String customerIdPart = parts[1].split("/")[0];
                if (customerIdPart.startsWith("c_")) {
                    MDC.put("customerId", PiiMaskingUtil.maskCustomerId(customerIdPart));
                }
            }
        }
    }
    
    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}