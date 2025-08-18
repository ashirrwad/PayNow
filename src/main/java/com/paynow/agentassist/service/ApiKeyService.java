package com.paynow.agentassist.service;

import com.paynow.agentassist.entity.ApiKeyEntity;
import com.paynow.agentassist.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ApiKeyService {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyService.class);
  private static final String API_KEY_PREFIX = "pn_";
  private static final int API_KEY_LENGTH = 32;

  private final ApiKeyRepository apiKeyRepository;
  private final SecureRandom secureRandom = new SecureRandom();

  public ApiKeyService(ApiKeyRepository apiKeyRepository) {
    this.apiKeyRepository = apiKeyRepository;
  }

  public String createApiKey(String userId, String name, String description) {
    // Generate secure API key
    byte[] keyBytes = new byte[API_KEY_LENGTH];
    secureRandom.nextBytes(keyBytes);
    String apiKey =
        API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);

    // Hash the key for storage
    String keyHash = hashApiKey(apiKey);

    ApiKeyEntity entity = new ApiKeyEntity();
    entity.setUserId(userId);
    entity.setKeyHash(keyHash);
    entity.setName(name);
    entity.setDescription(description);
    entity.setActive(true);

    apiKeyRepository.save(entity);

    logger.info("Created new API key for user: {}, name: {}", userId, name);

    // Return the plain key only once (never stored)
    return apiKey;
  }

  public boolean validateApiKey(String apiKey) {
    if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX)) {
      return false;
    }

    String keyHash = hashApiKey(apiKey);
    Optional<ApiKeyEntity> entity = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);

    if (entity.isPresent()) {
      // Update usage statistics asynchronously
      updateUsageStats(entity.get().getId());
      return true;
    }

    return false;
  }

  public Optional<String> getUserIdByApiKey(String apiKey) {
    if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX)) {
      return Optional.empty();
    }

    String keyHash = hashApiKey(apiKey);
    return apiKeyRepository.findByKeyHashAndActiveTrue(keyHash).map(ApiKeyEntity::getUserId);
  }

  public List<ApiKeyEntity> getUserApiKeys(String userId) {
    return apiKeyRepository.findByUserId(userId);
  }

  public void deactivateApiKey(Long keyId, String userId) {
    ApiKeyEntity entity =
        apiKeyRepository
            .findById(keyId)
            .filter(e -> e.getUserId().equals(userId))
            .orElseThrow(() -> new IllegalArgumentException("API key not found"));

    entity.setActive(false);
    apiKeyRepository.save(entity);

    logger.info("Deactivated API key {} for user: {}", keyId, userId);
  }

  @Transactional
  public void updateUsageStats(Long keyId) {
    try {
      apiKeyRepository.updateUsageStats(keyId, LocalDateTime.now());
    } catch (Exception e) {
      logger.warn("Failed to update usage stats for key: {}", keyId, e);
      // Don't fail the request if usage tracking fails
    }
  }

  private String hashApiKey(String apiKey) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
