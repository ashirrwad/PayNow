package com.paynow.agentassist.security;

import com.paynow.agentassist.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collection;
import java.util.Collections;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("API Key Authentication Filter Tests")
class ApiKeyAuthenticationFilterTest {

  private TestApiKeyService testApiKeyService;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;

  private ApiKeyAuthenticationFilter authenticationFilter;
  private StringWriter responseWriter;
  private PrintWriter printWriter;

  @BeforeEach
  void setUp() throws IOException {
    testApiKeyService = new TestApiKeyService();
    authenticationFilter = new ApiKeyAuthenticationFilter(testApiKeyService);

    // Create mock objects for servlet components
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    filterChain = mock(FilterChain.class);

    // Setup response writer for testing error responses
    responseWriter = new StringWriter();
    printWriter = new PrintWriter(responseWriter);
    when(response.getWriter()).thenReturn(printWriter);

    // Clear security context before each test
    SecurityContextHolder.clearContext();
  }

  // Custom test implementation of ApiKeyService
  static class TestApiKeyService extends ApiKeyService {
    private final Map<String, String> validApiKeys = new HashMap<>();
    private boolean shouldThrowExceptionOnValidation = false;
    private boolean shouldThrowExceptionOnUserLookup = false;

    public TestApiKeyService() {
      super(null); // Pass null repository since we won't use it
      // Pre-populate with test data
      validApiKeys.put("valid-api-key-123", "user123");
      validApiKeys.put("context-test-key", "contextUser");
      validApiKeys.put("valid-key", "user");
      validApiKeys.put("valid-but-unknown-user-key", null); // Valid key but no user
    }

    public void addValidApiKey(String apiKey, String userId) {
      validApiKeys.put(apiKey, userId);
    }

    public void setShouldThrowExceptionOnValidation(boolean shouldThrow) {
      this.shouldThrowExceptionOnValidation = shouldThrow;
    }

    public void setShouldThrowExceptionOnUserLookup(boolean shouldThrow) {
      this.shouldThrowExceptionOnUserLookup = shouldThrow;
    }

    @Override
    public boolean validateApiKey(String apiKey) {
      if (shouldThrowExceptionOnValidation) {
        throw new RuntimeException("Database error");
      }
      return validApiKeys.containsKey(apiKey);
    }

    @Override
    public Optional<String> getUserIdByApiKey(String apiKey) {
      if (shouldThrowExceptionOnUserLookup) {
        throw new RuntimeException("User service error");
      }
      String userId = validApiKeys.get(apiKey);
      return userId != null ? Optional.of(userId) : Optional.empty();
    }
  }

  // Simple test implementation of Authentication
  static class TestAuthentication implements Authentication {
    private final String name;
    private boolean authenticated = true;

    public TestAuthentication(String name) {
      this.name = name;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return Collections.emptyList();
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return null;
    }

    @Override
    public Object getPrincipal() {
      return name;
    }

    @Override
    public boolean isAuthenticated() {
      return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
      this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  @Nested
  @DisplayName("Valid Authentication Tests")
  class ValidAuthenticationTests {

    @Test
    @DisplayName("Should authenticate successfully with valid API key")
    void shouldAuthenticateSuccessfullyWithValidApiKey() throws ServletException, IOException {
      // Given
      String validApiKey = "valid-api-key-123";
      String userId = "user123";

      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(validApiKey);
      // testApiKeyService already has this key configured

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain).doFilter(request, response);

      // Verify authentication was set in security context
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      assertNotNull(auth);
      assertEquals(userId, auth.getPrincipal());
      assertNull(auth.getCredentials());
      assertTrue(auth.getAuthorities().isEmpty());

      // Verify no error response was written
      verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should handle unknown user ID gracefully")
    void shouldHandleUnknownUserIdGracefully() throws ServletException, IOException {
      // Given
      String validApiKey = "valid-but-unknown-user-key";

      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(validApiKey);
      // testApiKeyService already has this key configured with no user

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain).doFilter(request, response);

      // Verify authentication was set with "unknown" user
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      assertNotNull(auth);
      assertEquals("unknown", auth.getPrincipal());
    }
  }

  @Nested
  @DisplayName("Authentication Failure Tests")
  class AuthenticationFailureTests {

    @Test
    @DisplayName("Should reject request with missing API key")
    void shouldRejectRequestWithMissingApiKey() throws ServletException, IOException {
      // Given
      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(null);

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain, never()).doFilter(request, response);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      verify(response).setContentType("application/json");

      printWriter.flush();
      String responseBody = responseWriter.toString();
      assertEquals("{\"error\":\"Invalid or missing API key\"}", responseBody);

      // Verify no authentication was set
      assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject request with empty API key")
    void shouldRejectRequestWithEmptyApiKey() throws ServletException, IOException {
      // Given
      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn("");
      // Empty string will return false from testApiKeyService

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain, never()).doFilter(request, response);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

      assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject request with invalid API key")
    void shouldRejectRequestWithInvalidApiKey() throws ServletException, IOException {
      // Given
      String invalidApiKey = "invalid-api-key";

      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(invalidApiKey);
      // Invalid key will return false from testApiKeyService

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain, never()).doFilter(request, response);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      verify(response).setContentType("application/json");

      printWriter.flush();
      String responseBody = responseWriter.toString();
      assertEquals("{\"error\":\"Invalid or missing API key\"}", responseBody);

      assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject request with malformed API key")
    void shouldRejectRequestWithMalformedApiKey() throws ServletException, IOException {
      // Given
      String malformedApiKey = "malformed key with spaces!@#";

      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(malformedApiKey);
      // Malformed key will return false from testApiKeyService

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain, never()).doFilter(request, response);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

      assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
  }

  @Nested
  @DisplayName("Bypass Rules Tests")
  class BypassRulesTests {

    @Test
    @DisplayName("Should bypass authentication for health endpoint")
    void shouldBypassAuthenticationForHealthEndpoint() throws ServletException, IOException {
      // Given
      when(request.getRequestURI()).thenReturn("/actuator/health");

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain).doFilter(request, response);
      verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

      // No authentication should be set for bypassed endpoints
      assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should bypass authentication for info endpoint")
    void shouldBypassAuthenticationForInfoEndpoint() throws ServletException, IOException {
      // Given
      when(request.getRequestURI()).thenReturn("/actuator/info");

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain).doFilter(request, response);
      verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should bypass authentication for H2 console")
    void shouldBypassAuthenticationForH2Console() throws ServletException, IOException {
      // Given
      when(request.getRequestURI()).thenReturn("/h2-console/login.do");

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain).doFilter(request, response);
      verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void shouldRequireAuthenticationForProtectedEndpoints() throws ServletException, IOException {
      // Given
      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(null);

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain, never()).doFilter(request, response);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Security Context Management Tests")
  class SecurityContextManagementTests {

    @Test
    @DisplayName("Should set authentication in security context")
    void shouldSetAuthenticationInSecurityContext() throws ServletException, IOException {
      // Given
      String validApiKey = "context-test-key";
      String userId = "contextUser";

      when(request.getRequestURI()).thenReturn("/api/v1/metrics");
      when(request.getHeader("X-API-Key")).thenReturn(validApiKey);
      // testApiKeyService already has this key configured

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      assertNotNull(auth);
      assertEquals(userId, auth.getPrincipal());
      assertTrue(auth.isAuthenticated());

      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not set authentication context on failure")
    void shouldNotSetAuthenticationContextOnFailure() throws ServletException, IOException {
      // Given
      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn("invalid-key");
      // Invalid key will return false from testApiKeyService

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      assertNull(SecurityContextHolder.getContext().getAuthentication());
      verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should preserve existing authentication if bypass endpoint")
    void shouldPreserveExistingAuthenticationIfBypassEndpoint()
        throws ServletException, IOException {
      // Given - Set existing authentication (create simple test implementation)
      Authentication existingAuth = new TestAuthentication("existingUser");
      SecurityContextHolder.getContext().setAuthentication(existingAuth);

      when(request.getRequestURI()).thenReturn("/actuator/health");

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      assertEquals(existingAuth, SecurityContextHolder.getContext().getAuthentication());
      verify(filterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle API key service exceptions gracefully")
    void shouldHandleApiKeyServiceExceptionsGracefully() throws ServletException, IOException {
      // Given
      String apiKey = "exception-test-key";

      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(apiKey);
      testApiKeyService.setShouldThrowExceptionOnValidation(true);

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain, never()).doFilter(request, response);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

      printWriter.flush();
      String responseBody = responseWriter.toString();
      assertEquals("{\"error\":\"Invalid or missing API key\"}", responseBody);

      assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should handle user lookup exceptions gracefully")
    void shouldHandleUserLookupExceptionsGracefully() throws ServletException, IOException {
      // Given
      String validApiKey = "user-lookup-error-key";

      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(validApiKey);
      testApiKeyService.addValidApiKey(validApiKey, "someUser");
      testApiKeyService.setShouldThrowExceptionOnUserLookup(true);

      // When & Then - Should not throw exception but handle gracefully
      assertDoesNotThrow(
          () -> {
            authenticationFilter.doFilterInternal(request, response, filterChain);
          });

      // Should still proceed with "unknown" user on user lookup failure
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      assertNotNull(auth);
      assertEquals("unknown", auth.getPrincipal());

      verify(filterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("Header Processing Tests")
  class HeaderProcessingTests {

    @Test
    @DisplayName("Should handle case-sensitive API key header")
    void shouldHandleCaseSensitiveApiKeyHeader() throws ServletException, IOException {
      // Given - Header name is case-sensitive
      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn("valid-key");
      when(request.getHeader("x-api-key")).thenReturn(null); // Different case
      // testApiKeyService already has "valid-key" configured

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain).doFilter(request, response);
      assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should handle whitespace in API key header")
    void shouldHandleWhitespaceInApiKeyHeader() throws ServletException, IOException {
      // Given - API key with leading/trailing whitespace
      String keyWithWhitespace = "  valid-key-with-spaces  ";

      when(request.getRequestURI()).thenReturn("/api/v1/payments/decide");
      when(request.getHeader("X-API-Key")).thenReturn(keyWithWhitespace);
      // Key with whitespace will return false from testApiKeyService

      // When
      authenticationFilter.doFilterInternal(request, response, filterChain);

      // Then
      verify(filterChain, never()).doFilter(request, response);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

      // Validation failed as expected for key with whitespace
    }
  }
}
