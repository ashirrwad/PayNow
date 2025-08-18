package com.paynow.agentassist.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paynow.agentassist.constants.ApiConstants;
import com.paynow.agentassist.domain.AgentStep;
import com.paynow.agentassist.domain.PaymentDecision;
import com.paynow.agentassist.dto.PaymentDecisionRequest;
import com.paynow.agentassist.dto.PaymentDecisionRequestBuilder;
import com.paynow.agentassist.dto.PaymentDecisionResponse;
import com.paynow.agentassist.dto.RateLimitResult;
import com.paynow.agentassist.exception.GlobalExceptionHandler;
import com.paynow.agentassist.service.MetricsService;
import com.paynow.agentassist.service.payment.PaymentDecisionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Payment Controller Integration Tests")
class PaymentControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private TestPaymentDecisionService testService;
    private TestMetricsService testMetricsService;
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testService = new TestPaymentDecisionService();
        testMetricsService = new TestMetricsService();
        paymentController = new PaymentController(testService, testMetricsService);
        
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(new LocalValidatorFactoryBean())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    static class TestPaymentDecisionService implements PaymentDecisionService {
        
        private RateLimitResult rateLimitResult = RateLimitResult.success();
        private PaymentDecisionResponse response = new PaymentDecisionResponse(
            PaymentDecision.ALLOW, 
            List.of("low_risk"), 
            List.of(new AgentStep("test", "Test step")), 
            "req_test123"
        );
        private boolean throwException = false;
        
        public void setRateLimitResult(RateLimitResult result) {
            this.rateLimitResult = result;
        }
        
        public void setResponse(PaymentDecisionResponse response) {
            this.response = response;
        }
        
        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }
        
        @Override
        public RateLimitResult checkRateLimit(PaymentDecisionRequest request) {
            return rateLimitResult;
        }
        
        @Override
        public PaymentDecisionResponse processPaymentDecision(PaymentDecisionRequest request) {
            if (throwException) {
                throw new RuntimeException("Test exception");
            }
            return response;
        }
        
        @Override
        public PaymentDecisionResponse processPaymentDecisionWithStrategy(PaymentDecisionRequest request, String strategyName) {
            return processPaymentDecision(request);
        }
    }

    static class TestMetricsService extends MetricsService {
        
        private final AtomicInteger requestCounter = new AtomicInteger(0);
        private final AtomicInteger decisionCounter = new AtomicInteger(0);
        private final AtomicInteger durationCounter = new AtomicInteger(0);
        
        public TestMetricsService() {
            super(new SimpleMeterRegistry());
        }
        
        @Override
        public void incrementRequestCounter() {
            requestCounter.incrementAndGet();
        }
        
        @Override
        public void recordPaymentDecision(PaymentDecision decision) {
            decisionCounter.incrementAndGet();
        }
        
        @Override
        public void recordRequestDuration(long durationMs) {
            durationCounter.incrementAndGet();
        }
        
        public int getRequestCount() { return requestCounter.get(); }
        public int getDecisionCount() { return decisionCounter.get(); }
        public int getDurationCount() { return durationCounter.get(); }
        public void reset() {
            requestCounter.set(0);
            decisionCounter.set(0);
            durationCounter.set(0);
        }
    }

    @Nested
    @DisplayName("Payment Decision API Tests")
    class PaymentDecisionApiTests {

        @Test
        @DisplayName("Should successfully process valid payment decision request")
        void shouldSuccessfullyProcessValidPaymentDecisionRequest() throws Exception {
            // Given - Valid payment request
            PaymentDecisionRequest request = createValidRequest();
            
            // Reset test state
            testMetricsService.reset();
            testService.setRateLimitResult(RateLimitResult.success());
            testService.setResponse(new PaymentDecisionResponse(
                PaymentDecision.ALLOW, List.of("low_risk"), List.of(), "req_test123456"));

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("ALLOW"))
                .andExpect(jsonPath("$.data.requestId").value("req_test123456"))
                .andExpect(jsonPath("$.data.reasons[0]").value("low_risk"));
        }

        @Test
        @DisplayName("Should return 429 when rate limit exceeded")
        void shouldReturn429WhenRateLimitExceeded() throws Exception {
            // Given - Rate limit exceeded scenario
            PaymentDecisionRequest request = createValidRequest();
            RateLimitResult rateLimitResult = new RateLimitResult(
                false, ApiConstants.MSG_RATE_LIMIT_EXCEEDED, "PT30S");
            
            testService.setRateLimitResult(rateLimitResult);

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(ApiConstants.HEADER_RETRY_AFTER, "PT30S"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ApiConstants.ERROR_RATE_LIMIT_EXCEEDED));
        }

        @Test
        @DisplayName("Should return 500 when service throws unexpected exception")
        void shouldReturn500WhenServiceThrowsUnexpectedException() throws Exception {
            // Given - Service throws exception
            PaymentDecisionRequest request = createValidRequest();
            
            testService.setRateLimitResult(RateLimitResult.success());
            testService.setThrowException(true);

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ApiConstants.ERROR_INTERNAL_SERVER));
                
            // Reset for other tests
            testService.setThrowException(false);
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should return 400 for missing customer ID")
        void shouldReturn400ForMissingCustomerId() throws Exception {
            // Given - Request without customer ID (create JSON directly)
            String invalidJson = "{"
                + "\"amount\": 100.00,"
                + "\"currency\": \"USD\","
                + "\"payeeId\": \"p_test_payee_456\","
                + "\"idempotencyKey\": \"test-key-123\""
                + "}";

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for negative amount")
        void shouldReturn400ForNegativeAmount() throws Exception {
            // Given - Request with negative amount (create JSON directly)
            String invalidJson = "{"
                + "\"customerId\": \"c_test_customer_123\","
                + "\"amount\": -50.00,"
                + "\"currency\": \"USD\","
                + "\"payeeId\": \"p_test_payee_456\","
                + "\"idempotencyKey\": \"test-key-123\""
                + "}";

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for invalid JSON")
        void shouldReturn400ForInvalidJson() throws Exception {
            // Given - Malformed JSON
            String invalidJson = "{ \"customerId\": \"test\", \"amount\": invalid }";

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 415 for unsupported content type")
        void shouldReturn415ForUnsupportedContentType() throws Exception {
            // Given - Valid request but wrong content type
            PaymentDecisionRequest request = createValidRequest();

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.TEXT_PLAIN) // Wrong content type
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("API Contract Tests")
    class ApiContractTests {

        @Test
        @DisplayName("Should return standardized API response format")
        void shouldReturnStandardizedApiResponseFormat() throws Exception {
            // Given
            PaymentDecisionRequest request = createValidRequest();
            
            testService.setRateLimitResult(RateLimitResult.success());
            testService.setResponse(new PaymentDecisionResponse(
                PaymentDecision.REVIEW,
                List.of("high_amount", "new_payee"),
                List.of(),
                "req_review123"));

            // When & Then - Verify standard API response structure
            mockMvc
                .perform(
                    post("/api/v1/payments/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.decision").value("REVIEW"))
                .andExpect(jsonPath("$.data.reasons").isArray())
                .andExpect(jsonPath("$.data.requestId").isString())
                .andExpect(jsonPath("$.data.agentTrace").isArray());
        }

        @Test
        @DisplayName("Should handle missing request body gracefully")
        void shouldHandleMissingRequestBodyGracefully() throws Exception {
            // When & Then
            mockMvc
                .perform(post("/api/v1/payments/decide").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    // Helper method to create valid request
    private PaymentDecisionRequest createValidRequest() {
        return PaymentDecisionRequestBuilder.newBuilder()
            .customerId("c_test_customer_123")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .payeeId("p_test_payee_456")
            .idempotencyKey("test-key-123")
            .build();
    }
}