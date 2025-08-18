package com.paynow.agentassist.config;

import com.paynow.agentassist.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

  private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

  private final ApiKeyService apiKeyService;

  public DataInitializer(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @Override
  public void run(String... args) throws Exception {
    initializeDefaultApiKeys();
  }

  private void initializeDefaultApiKeys() {
    try {
      // Create API keys for demo users
      String apiKey1 =
          apiKeyService.createApiKey(
              "demo_user_001", "Demo API Key", "API key for demonstration and testing");

      String apiKey2 =
          apiKeyService.createApiKey(
              "payment_client_001", "Payment Client Key", "API key for payment processing client");

      logger.info("=== Demo API Keys Created ===");
      logger.info("User: demo_user_001, API Key: {}", apiKey1);
      logger.info("User: payment_client_001, API Key: {}", apiKey2);
      logger.info("=== Use these keys in X-API-Key header ===");

    } catch (Exception e) {
      logger.error("Failed to initialize default API keys", e);
    }
  }
}
