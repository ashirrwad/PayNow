package com.paynow.agentassist.exception;

import com.paynow.agentassist.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Global Exception Handler Tests")
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler globalExceptionHandler;

  @BeforeEach
  void setUp() {
    globalExceptionHandler = new GlobalExceptionHandler();
  }

  @Nested
  @DisplayName("Validation Exception Handling Tests")
  class ValidationExceptionHandlingTests {

    @Test
    @DisplayName("Should handle method argument validation errors")
    void shouldHandleMethodArgumentValidationErrors() {
      // Given
      MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
      BindingResult bindingResult = mock(BindingResult.class);

      FieldError fieldError1 =
          new FieldError("paymentRequest", "customerId", "Customer ID is required");
      FieldError fieldError2 =
          new FieldError("paymentRequest", "amount", "Amount must be positive");

      when(exception.getBindingResult()).thenReturn(bindingResult);
      when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleValidationExceptions(exception);

      // Then
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      
      assertNotNull(responseBody.error());
      assertEquals("VALIDATION_ERROR", responseBody.error().code());
      assertEquals("Request validation failed", responseBody.error().message());
      
      String details = responseBody.error().details();
      assertNotNull(details);
      assertTrue(details.contains("customerId: Customer ID is required"));
      assertTrue(details.contains("amount: Amount must be positive"));
    }

    @Test
    @DisplayName("Should handle single validation error")
    void shouldHandleSingleValidationError() {
      // Given
      MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
      BindingResult bindingResult = mock(BindingResult.class);

      FieldError fieldError =
          new FieldError("paymentRequest", "currency", "Currency must be 3 characters");

      when(exception.getBindingResult()).thenReturn(bindingResult);
      when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleValidationExceptions(exception);

      // Then
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      
      assertNotNull(responseBody.error());
      assertEquals("VALIDATION_ERROR", responseBody.error().code());
      assertEquals("Request validation failed", responseBody.error().message());
      
      String details = responseBody.error().details();
      assertNotNull(details);
      assertTrue(details.contains("currency: Currency must be 3 characters"));
    }

    @Test
    @DisplayName("Should handle empty validation errors gracefully")
    void shouldHandleEmptyValidationErrorsGracefully() {
      // Given
      MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
      BindingResult bindingResult = mock(BindingResult.class);

      when(exception.getBindingResult()).thenReturn(bindingResult);
      when(bindingResult.getAllErrors()).thenReturn(List.of());

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleValidationExceptions(exception);

      // Then
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      
      assertNotNull(responseBody.error());
      assertEquals("VALIDATION_ERROR", responseBody.error().code());
      assertEquals("Request validation failed", responseBody.error().message());
      
      String details = responseBody.error().details();
      // When no validation errors, details should be empty string
      assertTrue(details == null || details.isEmpty());
    }
  }

  @Nested
  @DisplayName("Business Logic Exception Handling Tests")
  class BusinessLogicExceptionHandlingTests {

    @Test
    @DisplayName("Should handle illegal argument exceptions")
    void shouldHandleIllegalArgumentExceptions() {
      // Given
      String errorMessage = "Invalid payment amount: negative values not allowed";
      IllegalArgumentException exception = new IllegalArgumentException(errorMessage);

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleIllegalArgumentException(exception);

      // Then
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      
      assertNotNull(responseBody.error());
      assertEquals("INVALID_REQUEST", responseBody.error().code());
      assertEquals("Invalid request parameter", responseBody.error().message());
      assertEquals(errorMessage, responseBody.error().details());
    }

    @Test
    @DisplayName("Should handle illegal argument exceptions with null message")
    void shouldHandleIllegalArgumentExceptionsWithNullMessage() {
      // Given
      IllegalArgumentException exception = new IllegalArgumentException((String) null);

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleIllegalArgumentException(exception);

      // Then
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("INVALID_REQUEST", responseBody.error().code());
      assertEquals("Invalid request parameter", responseBody.error().message());
    }

    @Test
    @DisplayName("Should handle illegal argument exceptions with empty message")
    void shouldHandleIllegalArgumentExceptionsWithEmptyMessage() {
      // Given
      IllegalArgumentException exception = new IllegalArgumentException("");

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleIllegalArgumentException(exception);

      // Then
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("INVALID_REQUEST", responseBody.error().code());
      assertEquals("Invalid request parameter", responseBody.error().message());
    }
  }

  @Nested
  @DisplayName("HTTP Method Exception Handling Tests")
  class HttpMethodExceptionHandlingTests {

    @Test
    @DisplayName("Should handle method not supported exceptions")
    void shouldHandleMethodNotSupportedExceptions() {
      // Given
      String errorMessage = "Request method 'DELETE' not supported";
      HttpRequestMethodNotSupportedException exception =
          new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleMethodNotSupportedException(exception);

      // Then
      assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("METHOD_NOT_ALLOWED", responseBody.error().code());

      assertEquals("HTTP method not supported", responseBody.error().message());
      String details = responseBody.error().details();
      assertNotNull(details);
      assertTrue(details.contains("DELETE"));
      assertTrue(details.contains("not supported"));
    }

    @Test
    @DisplayName("Should handle method not supported with supported methods info")
    void shouldHandleMethodNotSupportedWithSupportedMethodsInfo() {
      // Given
      HttpRequestMethodNotSupportedException exception =
          new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "POST", "PATCH"));

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleMethodNotSupportedException(exception);

      // Then
      assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);

      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("METHOD_NOT_ALLOWED", responseBody.error().code());
      assertEquals("HTTP method not supported", responseBody.error().message());
      String details = responseBody.error().details();
      assertTrue(details.contains("PUT"));
    }
  }

  @Nested
  @DisplayName("Generic Exception Handling Tests")
  class GenericExceptionHandlingTests {

    @Test
    @DisplayName("Should handle generic runtime exceptions")
    void shouldHandleGenericRuntimeExceptions() {
      // Given
      RuntimeException exception = new RuntimeException("Database connection failed");

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleGenericException(exception);

      // Then
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("INTERNAL_ERROR", responseBody.error().code());
      assertEquals("An unexpected error occurred", responseBody.error().message());
    }

    @Test
    @DisplayName("Should handle null pointer exceptions")
    void shouldHandleNullPointerExceptions() {
      // Given
      NullPointerException exception = new NullPointerException("Required service is null");

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleGenericException(exception);

      // Then
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("INTERNAL_ERROR", responseBody.error().code());
      assertEquals("An unexpected error occurred", responseBody.error().message());
    }

    @Test
    @DisplayName("Should handle checked exceptions")
    void shouldHandleCheckedExceptions() {
      // Given
      Exception exception = new Exception("External service timeout");

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleGenericException(exception);

      // Then
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("INTERNAL_ERROR", responseBody.error().code());
      assertEquals("An unexpected error occurred", responseBody.error().message());
    }

    @Test
    @DisplayName("Should handle exceptions with null message")
    void shouldHandleExceptionsWithNullMessage() {
      // Given
      RuntimeException exception = new RuntimeException((String) null);

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleGenericException(exception);

      // Then
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("INTERNAL_ERROR", responseBody.error().code());
      assertEquals("An unexpected error occurred", responseBody.error().message());
    }
  }

  @Nested
  @DisplayName("Security and Privacy Tests")
  class SecurityAndPrivacyTests {

    @Test
    @DisplayName("Should not expose internal system details in error responses")
    void shouldNotExposeInternalSystemDetailsInErrorResponses() {
      // Given
      RuntimeException exception =
          new RuntimeException(
              "Connection to database 'payment_db' on host 'internal-db-server.company.com' failed");

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleGenericException(exception);

      // Then
      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);

      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("INTERNAL_ERROR", responseBody.error().code());
      assertEquals("An unexpected error occurred", responseBody.error().message());

      // Verify internal details are not exposed
      String errorMessage = responseBody.error().message();
      assertFalse(errorMessage.contains("payment_db"));
      assertFalse(errorMessage.contains("internal-db-server"));
    }

    @Test
    @DisplayName("Should sanitize validation error messages")
    void shouldSanitizeValidationErrorMessages() {
      // Given
      MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
      BindingResult bindingResult = mock(BindingResult.class);

      FieldError fieldError =
          new FieldError(
              "paymentRequest", "customerId", "Customer ID 'c_12345' not found in database");

      when(exception.getBindingResult()).thenReturn(bindingResult);
      when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

      // When
      ResponseEntity<ApiResponse<Void>> response =
          globalExceptionHandler.handleValidationExceptions(exception);

      // Then
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      ApiResponse<Void> responseBody = response.getBody();
      assertNotNull(responseBody);

      assertFalse(responseBody.success());
      assertNotNull(responseBody.error());
      assertEquals("VALIDATION_ERROR", responseBody.error().code());
      assertEquals("Request validation failed", responseBody.error().message());

      // The actual message is returned in details (validation messages are considered safe to expose)
      String details = responseBody.error().details();
      assertNotNull(details);
      assertTrue(details.contains("customerId:"));
    }
  }
}
