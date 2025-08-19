package com.paynow.agentassist.config;

import com.paynow.agentassist.config.ApiKeyConfig.ApiKey;
import com.paynow.agentassist.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Component
@Profile("local")
public class DataInitializer implements CommandLineRunner {

  private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

  private final ApiKeyService apiKeyService;
  private final ApiKeyConfig apiKeyConfig;

  public DataInitializer(ApiKeyService apiKeyService, @Autowired(required = false) ApiKeyConfig apiKeyConfig) {
    this.apiKeyService = apiKeyService;
    this.apiKeyConfig = apiKeyConfig;
  }

  @Override
  public void run(String... args) throws Exception {
    logger.info("Running DataInitializer for local profile - initializing test API keys");
    initializeDefaultApiKeys();
  }

  private void initializeDefaultApiKeys() {
    try {
      if (apiKeyConfig == null) {
        logger.warn("ApiKeyConfig is null - no API keys configured for initialization");
        return;
      }

      List<ApiKey> apiKeys = apiKeyConfig.getApiKeys();
      if (apiKeys == null || apiKeys.isEmpty()) {
        logger.warn("No API keys found in configuration for initialization");
        return;
      }

      logger.info("Initializing {} API keys from configuration", apiKeys.size());
      
      for (ApiKey apiKey : apiKeys) {
        if (apiKey.getKey() != null && !apiKeyService.apiKeyExists(apiKey.getKey())) {
          apiKeyService.createApiKey(
            apiKey.getKey(), 
            apiKey.getUserId(), 
            apiKey.getName(), 
            apiKey.getDescription()
          );
          logger.info("Created API key: {} (user: {})", apiKey.getName(), apiKey.getUserId());
        } else if (apiKey.getKey() != null) {
          logger.debug("API key already exists: {}", apiKey.getName());
        } else {
          logger.warn("Skipping API key with null key value: {}", apiKey.getName());
        }
      }
      
      logger.info("API key initialization completed successfully");
    } catch (Exception e) {
      logger.error("Failed to initialize default API keys", e);
      throw new RuntimeException("DataInitializer failed", e);
    }
  }
}
